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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side brain: who is a speedster (and which kind), what is switched on, and the global time factor. */
public final class SpeedsterManager {

    /** Which phial the player drank (see {@link Variant}). Saved on the player and kept through death. */
    public static AttachmentType<Integer> VARIANT;
    /** Older saves stored a simple yes/no; still read so existing speedsters keep their powers. */
    private static AttachmentType<Boolean> LEGACY_POWERED;

    private static final Map<UUID, SpeedsterState> STATES = new HashMap<>();
    private static int factorChangeCooldown;

    private SpeedsterManager() {}

    public static void init() {
        VARIANT = AttachmentRegistry.<Integer>create(Vaporous.id("variant"), builder -> builder
                .persistent(Codec.INT)
                .copyOnDeath()
                .initializer(() -> Variant.NONE));
        LEGACY_POWERED = AttachmentRegistry.<Boolean>create(Vaporous.id("powered"), builder -> builder
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
                s.lastPos = null;
                s.motion = Vec3d.ZERO;
                s.markDirty();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            TimeDilation.SERVER.reset();
        });

        // In slowed time, a mob you've just touched or hit can't land a blow on you.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                SpeedsterState s = STATES.get(sp.getUuid());
                if (s != null && s.mobImmuneTicks > 0 && source.getAttacker() instanceof MobEntity) return false;
            }
            return true;
        });

        PressureAbilities.init();
        MomentumCombat.init();
    }

    // ------------------------------------------------------------------ queries

    public static int variant(ServerPlayerEntity player) {
        Integer v = player.getAttached(VARIANT);
        int variant = v == null ? Variant.NONE : v;
        if (variant == Variant.NONE) {
            Boolean legacy = player.getAttached(LEGACY_POWERED);
            if (legacy != null && legacy) {
                player.setAttached(VARIANT, Variant.VAPOR);
                player.removeAttached(LEGACY_POWERED);
                return Variant.VAPOR;
            }
        }
        return Variant.isValid(variant) ? variant : Variant.NONE;
    }

    public static boolean isPowered(ServerPlayerEntity player) {
        return variant(player) != Variant.NONE;
    }

    public static SpeedsterState state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), u -> new SpeedsterState());
    }

    /** The live state, or null if this player isn't a speedster. */
    @Nullable
    public static SpeedsterState stateIfPowered(ServerPlayerEntity player) {
        return isPowered(player) ? state(player) : null;
    }

    public static VaporConfig.FormStats stats(ServerPlayerEntity player) {
        return VaporConfig.get().stats(variant(player));
    }

    // ------------------------------------------------------------------ mutation

    /** Make a player a speedster of the given variant, or {@link Variant#NONE} to take the powers away. */
    public static void setVariant(ServerPlayerEntity player, int variant) {
        if (!Variant.isValid(variant)) variant = Variant.NONE;
        player.setAttached(VARIANT, variant);
        player.removeAttached(LEGACY_POWERED);
        SpeedsterState s = state(player);
        if (variant == Variant.NONE) {
            s.form = false;
            s.time = false;
            s.vapor = 0f;
            VaporAttributes.clearServer(player);
        }
        s.markDirty();
        broadcast(player, s);
        if (variant == Variant.NONE) STATES.remove(player.getUuid());
    }

    public static void handleAbility(ServerPlayerEntity player, int ability, float charge) {
        int variant = variant(player);
        if (variant == Variant.NONE || player.isSpectator() || !player.isAlive()) return;
        if (Float.isNaN(charge)) charge = 0f;
        charge = MathHelper.clamp(charge, 0f, 1f);
        SpeedsterState s = state(player);
        ServerWorld world = player.getServerWorld();
        VaporConfig.FormStats stats = VaporConfig.get().stats(variant);

        switch (ability) {
            case VaporPayloads.Ability.TOGGLE_FORM -> {
                s.form = !s.form;
                if (!s.form) s.vapor = 0f;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        s.form ? SoundEvents.ENTITY_BREEZE_CHARGE : SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        SoundCategory.PLAYERS, 0.8f, s.form ? (variant == Variant.PLASMA ? 1.9f : 1.4f) : 1.2f);
                world.spawnParticles(VaporRegistry.particleFor(variant), player.getX(), player.getBodyY(0.5), player.getZ(),
                        s.form ? 40 : 15, 0.35, 0.7, 0.35, 0.03);
                s.markDirty();
            }
            case VaporPayloads.Ability.TOGGLE_TIME -> {
                boolean costsHunger = stats.timeHungerCost > 0f;
                if (!s.time && costsHunger && !player.getAbilities().creativeMode
                        && player.getHungerManager().getFoodLevel() <= 2) {
                    player.sendMessage(Text.translatable("message.vaporous.too_hungry").formatted(Formatting.GRAY), true);
                    return;
                }
                s.time = !s.time;
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        s.time ? SoundEvents.BLOCK_BEACON_DEACTIVATE : SoundEvents.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.PLAYERS, 1.0f, s.time ? (variant == Variant.PLASMA ? 0.35f : 0.5f) : 1.6f);
                s.markDirty();
            }
            case VaporPayloads.Ability.PRESSURE_BLAST -> {
                if (pressureAllowed(s, player)) PressureAbilities.blast(player, s, charge, variant);
            }
            default -> {
            }
        }
    }

    /** Compressing air takes either slowed time (then even standing still is fine) or real speed. */
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
            int variant = variant(p);
            if (variant == Variant.NONE) continue;
            VaporConfig.FormStats stats = c.stats(variant);
            SpeedsterState s = state(p);
            if (!s.form) s.vapor = 0f;

            trackMotion(p, s);

            if (s.blastCooldown > 0) s.blastCooldown--;
            if (s.mobImmuneTicks > 0) s.mobImmuneTicks--;
            if (TimeDilation.SERVER.active() && p.isAlive() && !p.isSpectator()) {
                // Running into a mob in slowed time: no mob could react that fast.
                Box reach = p.getBoundingBox().expand(0.6);
                if (!p.getServerWorld().getEntitiesByClass(MobEntity.class, reach, Entity::isAlive).isEmpty()) {
                    s.mobImmuneTicks = Math.max(s.mobImmuneTicks, c.dilationContactImmunityTicks);
                }
            }

            VaporAttributes.applyServer(p, s.form, s.vapor);

            // Flying, wall-running and riding currents aren't falling: don't build up fall damage.
            if (s.floating || (s.form && s.vapor >= c.flightVapor - 0.05)) {
                p.fallDistance = 0f;
            }
            s.floating = false;

            if (c.fastPickup && p.isAlive() && !p.isSpectator()) {
                FastPickup.collect(p, s, c);
            }

            boolean creative = p.getAbilities().creativeMode || p.isSpectator();
            if (!creative) {
                if (s.form && stats.speedHungerCost > 0f) p.addExhaustion(stats.speedHungerCost * (0.3f + s.vapor));
                if (s.time && stats.timeHungerCost > 0f) {
                    p.addExhaustion(stats.timeHungerCost);
                    if (p.getHungerManager().getFoodLevel() <= 0) {
                        s.time = false;
                        s.markDirty();
                        p.sendMessage(Text.translatable("message.vaporous.time_exhausted").formatted(Formatting.GRAY), true);
                    }
                }
            }

            if (s.time && p.isAlive()) {
                int f = stats.baseTimeFactor;
                if (c.timeScalesWithSpeed) {
                    f += Math.round(s.vapor * (stats.maxTimeFactor - stats.baseTimeFactor));
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

    /** Measures how the player actually moved this tick (the server doesn't otherwise know a player's velocity). */
    private static void trackMotion(ServerPlayerEntity p, SpeedsterState s) {
        Vec3d pos = p.getPos();
        if (s.lastPos != null) {
            Vec3d delta = pos.subtract(s.lastPos);
            if (delta.lengthSquared() > 15.0 * 15.0) delta = Vec3d.ZERO; // a teleport, not running
            s.motion = s.motion.lerp(delta, 0.6);
        }
        s.lastPos = pos;
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
        return new VaporPayloads.VaporState(p.getId(), variant(p), s.form, s.time, s.vapor);
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
}
