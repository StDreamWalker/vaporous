package com.vaporous.power;

import com.mojang.serialization.Codec;
import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import com.vaporous.Vaporous;
import com.vaporous.net.VaporNetworking;
import com.vaporous.net.VaporPayloads;
import com.vaporous.time.TimeDilation;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side brain: who is a speedster, what is switched on, and the global time factor. */
public final class SpeedsterManager {

    /** Saved on the player and kept through death. */
    public static AttachmentType<Boolean> POWERED;

    private static final Map<UUID, SpeedsterState> STATES = new HashMap<>();
    private static int factorChangeCooldown;

    private SpeedsterManager() {}

    public static void init() {
        POWERED = AttachmentRegistry.<Boolean>create(Vaporous.id("powered"), builder -> builder
                .persistent(Codec.BOOL)
                .copyOnDeath()
                .initializer(() -> Boolean.FALSE));

        ServerTickEvents.START_SERVER_TICK.register(server -> TimeDilation.SERVER.advance());
        ServerTickEvents.END_SERVER_TICK.register(SpeedsterManager::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUuid()));

        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayerEntity sp && isPowered(sp)) {
                VaporNetworking.send(viewer, payload(sp, state(sp)));
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            SpeedsterState s = STATES.get(newPlayer.getUuid());
            if (s != null) {
                s.form = false;
                s.time = false;
                s.vapor = 0f;
                s.markDirty();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            TimeDilation.SERVER.reset();
        });

        AttackEntityCallback.EVENT.register(SpeedsterManager::onAttack);
        PressureAbilities.init();
    }

    // ------------------------------------------------------------------ queries

    public static boolean isPowered(ServerPlayerEntity player) {
        Boolean b = player.getAttached(POWERED);
        return b != null && b;
    }

    public static SpeedsterState state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), u -> new SpeedsterState());
    }

    @Nullable
    public static SpeedsterState stateIfPowered(ServerPlayerEntity player) {
        return isPowered(player) ? state(player) : null;
    }

    // ------------------------------------------------------------------ mutation

    public static void setPowered(ServerPlayerEntity player, boolean powered) {
        player.setAttached(POWERED, powered);
        SpeedsterState s = state(player);
        if (!powered) {
            s.form = false;
            s.time = false;
            s.vapor = 0f;
            VaporAttributes.clearServer(player);
        }
        broadcast(player, s);
        if (!powered) STATES.remove(player.getUuid());
    }

    public static void handleAbility(ServerPlayerEntity player, int ability, float charge) {
        if (!isPowered(player) || player.isSpectator() || !player.isAlive()) return;
        if (Float.isNaN(charge)) charge = 0f;
        charge = MathHelper.clamp(charge, 0f, 1f);
        SpeedsterState s = state(player);
        ServerWorld world = player.getServerWorld();

        switch (ability) {
            case VaporPayloads.Ability.TOGGLE_FORM -> {
                s.form = !s.form;
                if (!s.form) s.vapor = 0f;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        s.form ? SoundEvents.ENTITY_BREEZE_CHARGE : SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        SoundCategory.PLAYERS, 0.8f, s.form ? 1.4f : 1.2f);
                world.spawnParticles(VaporRegistry.VAPOR, player.getX(), player.getBodyY(0.5), player.getZ(),
                        s.form ? 40 : 15, 0.35, 0.7, 0.35, 0.03);
                s.markDirty();
            }
            case VaporPayloads.Ability.TOGGLE_TIME -> {
                if (!s.time && !player.getAbilities().creativeMode && player.getHungerManager().getFoodLevel() <= 2) {
                    player.sendMessage(Text.translatable("message.vaporous.too_hungry").formatted(Formatting.GRAY), true);
                    return;
                }
                s.time = !s.time;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        s.time ? SoundEvents.BLOCK_BEACON_DEACTIVATE : SoundEvents.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.PLAYERS, 1.0f, s.time ? 0.5f : 1.6f);
                s.markDirty();
            }
            case VaporPayloads.Ability.PRESSURE_BLAST -> {
                if (pressureAllowed(s, player)) PressureAbilities.blast(player, s, charge);
            }
            case VaporPayloads.Ability.VACUUM -> {
                if (pressureAllowed(s, player)) PressureAbilities.vacuum(player, s, charge);
            }
            default -> {
            }
        }
    }

    /**
     * Compressing air takes either slowed time (then even standing still is fine) or real speed.
     */
    private static boolean pressureAllowed(SpeedsterState s, ServerPlayerEntity player) {
        if (TimeDilation.SERVER.active()) return true;
        // small tolerance for network lag between the client's check and ours
        if (s.form && s.vapor >= VaporConfig.get().pressureMinVapor - 0.05f) return true;
        player.sendMessage(Text.translatable("message.vaporous.need_speed").formatted(Formatting.GRAY), true);
        return false;
    }

    public static void handleVaporReport(ServerPlayerEntity player, float vapor, boolean floating) {
        SpeedsterState s = STATES.get(player.getUuid());
        if (s == null || !isPowered(player)) return;
        if (Float.isNaN(vapor)) vapor = 0f;
        s.vapor = s.form ? MathHelper.clamp(vapor, 0f, 1f) : 0f;
        if (floating && s.form) s.floating = true;
    }

    // ------------------------------------------------------------------ ticking

    private static void tick(MinecraftServer server) {
        VaporConfig c = VaporConfig.get();
        int desiredFactor = 1;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!isPowered(p)) continue;
            SpeedsterState s = state(p);
            if (!s.form) s.vapor = 0f;

            if (s.blastCooldown > 0) s.blastCooldown--;
            if (s.vacuumCooldown > 0) s.vacuumCooldown--;

            VaporAttributes.applyServer(p, s.form, s.vapor);

            // Flying, wall-running and riding currents aren't falling: don't build up fall damage.
            if (s.floating || (s.form && s.vapor >= c.flightVapor - 0.05)) {
                p.fallDistance = 0f;
            }
            s.floating = false;

            boolean creative = p.getAbilities().creativeMode || p.isSpectator();
            if (!creative) {
                if (s.form) p.addExhaustion(c.speedHungerCost * (0.3f + s.vapor));
                if (s.time) {
                    p.addExhaustion(c.timeHungerCost);
                    if (p.getHungerManager().getFoodLevel() <= 0) {
                        s.time = false;
                        s.markDirty();
                        p.sendMessage(Text.translatable("message.vaporous.time_exhausted").formatted(Formatting.GRAY), true);
                    }
                }
            }

            if (s.time && p.isAlive()) {
                int f = c.baseTimeFactor;
                if (c.timeScalesWithSpeed) {
                    f += Math.round(s.vapor * (c.maxTimeFactor - c.baseTimeFactor));
                }
                desiredFactor = Math.max(desiredFactor, f);
            }

            s.ticksSinceSync++;
            if (s.dirty || Math.abs(s.vapor - s.lastSentVapor) > 0.03f || s.ticksSinceSync >= 40) {
                broadcast(p, s);
            }
        }

        updateTimeFactor(server, desiredFactor);
    }

    private static void updateTimeFactor(MinecraftServer server, int desired) {
        if (factorChangeCooldown > 0) factorChangeCooldown--;
        int current = TimeDilation.SERVER.factor();
        if (desired == current) return;
        boolean switchingOnOrOff = desired == 1 || current == 1;
        // Smooth out small speed-driven changes so the slow motion doesn't wobble.
        if (!switchingOnOrOff && factorChangeCooldown > 0) return;
        TimeDilation.SERVER.setFactor(desired);
        factorChangeCooldown = 10;
        VaporPayloads.TimeFactor payload = new VaporPayloads.TimeFactor(desired);
        for (ServerPlayerEntity p : PlayerLookup.all(server)) {
            VaporNetworking.send(p, payload);
        }
    }

    private static void onJoin(ServerPlayerEntity player) {
        VaporNetworking.send(player, new VaporPayloads.TimeFactor(TimeDilation.SERVER.factor()));
        if (isPowered(player)) {
            SpeedsterState s = state(player);
            s.form = false;
            s.time = false;
            s.vapor = 0f;
            s.markDirty();
        }
    }

    // ------------------------------------------------------------------ sync

    private static VaporPayloads.VaporState payload(ServerPlayerEntity p, SpeedsterState s) {
        return new VaporPayloads.VaporState(p.getId(), isPowered(p), s.form, s.time, s.vapor);
    }

    private static void broadcast(ServerPlayerEntity p, SpeedsterState s) {
        VaporPayloads.VaporState payload = payload(p, s);
        VaporNetworking.send(p, payload);
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(p)) {
            if (viewer != p) VaporNetworking.send(viewer, payload);
        }
        s.lastSentVapor = s.vapor;
        s.ticksSinceSync = 0;
        s.dirty = false;
    }

    // ------------------------------------------------------------------ momentum combat

    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target, @Nullable EntityHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp) || player.isSpectator()) return ActionResult.PASS;
        SpeedsterState s = STATES.get(sp.getUuid());
        if (s == null || !s.form || !isPowered(sp)) return ActionResult.PASS;

        VaporConfig c = VaporConfig.get();
        // A speedster's blows land faster than the target can recover.
        if (c.flurryIgnoresIFrames && target instanceof LivingEntity) {
            target.timeUntilRegen = 0;
        }

        // Carry momentum into the strike: the faster you're moving, the harder you throw them.
        if (s.vapor > 0.15f && target.isAttackable()) {
            Vec3d dir = sp.getRotationVector().multiply(1.0, 0.0, 1.0);
            if (dir.lengthSquared() > 1.0E-4) {
                dir = dir.normalize();
                double k = s.vapor * c.momentumKnockback * 0.55;
                target.addVelocity(dir.x * k, 0.12 + 0.25 * s.vapor, dir.z * k);
                target.velocityModified = true;
            }
            ServerWorld sw = sp.getServerWorld();
            sw.spawnParticles(VaporRegistry.VAPOR, target.getX(), target.getBodyY(0.5), target.getZ(),
                    6 + (int) (14 * s.vapor), 0.25, 0.35, 0.25, 0.06);
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 0.5f + 0.5f * s.vapor, 1.5f - 0.5f * s.vapor);
        }
        return ActionResult.PASS;
    }
}
