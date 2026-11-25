package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Momentum carried into everything the speedster hits.
 * <ul>
 *   <li><b>Cradle strikes</b>: punch something while moving forward and it takes your momentum, flying off
 *   in your direction of travel like the far ball of a Newton's cradle.</li>
 *   <li><b>Kinetic impact</b>: a launched creature that slams into a block hard enough takes damage
 *   from the impact.</li>
 *   <li><b>Cradle chains</b>: a launched creature that hits another creature hands the momentum on.</li>
 *   <li><b>Projectiles</b>: anything the speedster shoots or throws gains their forward speed (arrow
 *   damage scales with speed, so faster arrows hit harder), and passes that momentum on to whatever it hits.</li>
 * </ul>
 */
public final class MomentumCombat {

    private record PendingLaunch(Entity target, Vec3d velocity, @Nullable UUID attacker) {}

    private static final class Launched {
        final Entity entity;
        @Nullable final UUID attacker;
        Vec3d lastVelocity;
        int life = 80;
        int age;

        Launched(Entity entity, @Nullable UUID attacker, Vec3d velocity) {
            this.entity = entity;
            this.attacker = attacker;
            this.lastVelocity = velocity;
        }
    }

    private static final List<PendingLaunch> PENDING = new ArrayList<>();
    private static final Map<UUID, Launched> LAUNCHED = new HashMap<>();
    /** Projectiles fired with momentum -> the extra speed (blocks/tick) they carry. */
    private static final Map<Entity, Double> CHARGED = new WeakHashMap<>();

    private MomentumCombat() {}

    static void init() {
        AttackEntityCallback.EVENT.register(MomentumCombat::onAttack);
        ServerEntityEvents.ENTITY_LOAD.register(MomentumCombat::onEntityLoad);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MomentumCombat::onDamage);
        // Applied at the start of the next tick, after vanilla's own knockback, so ours wins.
        ServerTickEvents.START_SERVER_TICK.register(server -> applyPending());
        ServerTickEvents.END_WORLD_TICK.register(MomentumCombat::tickLaunched);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            LAUNCHED.clear();
            CHARGED.clear();
        });
    }

    // ================================================================== melee

    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target, @Nullable EntityHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.isSpectator()) return ActionResult.PASS;
        SpeedsterState s = SpeedsterManager.stateIfPowered(sp);
        if (s == null || !s.form) return ActionResult.PASS;
        VaporConfig c = VaporConfig.get();

        // A speedster's blows land faster than the target can recover.
        if (c.flurryIgnoresIFrames && target instanceof LivingEntity) {
            target.timeUntilRegen = 0;
        }
        if (!target.isAttackable() || !c.cradleStrikes) return ActionResult.PASS;

        Vec3d forward = forwardMomentum(sp, s, c);
        if (forward == null) return ActionResult.PASS;

        queueLaunch(target, forward.multiply(c.cradleTransfer), sp.getUuid());

        ServerWorld sw = sp.getServerWorld();
        double speed = forward.length();
        sw.spawnParticles(VaporRegistry.particleFor(SpeedsterManager.variant(sp)), target.getX(), target.getBodyY(0.5), target.getZ(),
                8 + (int) Math.min(30, speed * 10), 0.25, 0.35, 0.25, 0.08);
        sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                SoundCategory.PLAYERS, 1.0f, (float) MathHelper.clamp(1.2 - speed * 0.15, 0.5, 1.2));
        return ActionResult.PASS;
    }

    /** The player's movement if they're moving forward (roughly where they face) fast enough, otherwise null. */
    @Nullable
    static Vec3d forwardMomentum(ServerPlayerEntity p, SpeedsterState s, VaporConfig c) {
        Vec3d m = s.motion;
        double speed = m.length();
        if (speed < c.cradleMinSpeed) return null;
        if (m.dotProduct(p.getRotationVector()) < 0.4 * speed) return null; // within ~65 degrees of where you face
        return m;
    }

    // ================================================================== projectiles

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof ProjectileEntity projectile) || entity instanceof FishingBobberEntity) return;
        if (!(projectile.getOwner() instanceof ServerPlayerEntity owner)) return;
        SpeedsterState s = SpeedsterManager.stateIfPowered(owner);
        if (s == null || !s.form) return;
        // Only projectiles that have just left the speedster's hands (not ones reloading from disk).
        if (projectile.squaredDistanceTo(owner) > 16.0) return;

        VaporConfig c = VaporConfig.get();
        Vec3d vel = projectile.getVelocity();
        double speed = vel.length();
        if (speed < 0.1) return;
        Vec3d dir = vel.multiply(1.0 / speed);
        double along = s.motion.dotProduct(dir); // only forward motion adds: no skewing your aim sideways
        if (along <= 0.02) return;
        double extra = Math.min(along * c.projectileMomentum, c.maxLaunchSpeed);

        projectile.setVelocity(vel.add(dir.multiply(extra)));
        projectile.velocityModified = true;
        CHARGED.put(projectile, extra);
    }

    private static boolean onDamage(LivingEntity victim, DamageSource source, float amount) {
        Entity direct = source.getSource();
        if (direct == null || CHARGED.isEmpty()) return true;
        Double extra = CHARGED.remove(direct);
        if (extra == null || extra < 0.2) return true;

        VaporConfig c = VaporConfig.get();
        if (!c.cradleStrikes) return true;
        Vec3d v = direct.getVelocity();
        if (v.lengthSquared() < 1.0E-4) return true;
        Entity attacker = source.getAttacker();
        queueLaunch(victim, v.normalize().multiply(extra * c.cradleTransfer), attacker == null ? null : attacker.getUuid());
        return true;
    }

    // ================================================================== launching

    /** Launch from outside, e.g. the Pressure Jet, so slamming into walls still hurts. */
    public static void trackLaunch(Entity target, Vec3d velocity, @Nullable UUID attacker) {
        if (target instanceof LivingEntity) {
            LAUNCHED.put(target.getUuid(), new Launched(target, attacker, velocity));
        }
    }

    private static void queueLaunch(Entity target, Vec3d momentum, @Nullable UUID attacker) {
        VaporConfig c = VaporConfig.get();
        double resist = 0.0;
        if (target instanceof LivingEntity living) {
            resist = MathHelper.clamp(living.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 0.0, 1.0);
        }
        Vec3d v = momentum.multiply(1.0 - resist * 0.8).add(0, c.cradleLift, 0);
        double len = v.length();
        if (len > c.maxLaunchSpeed) v = v.multiply(c.maxLaunchSpeed / len);
        PENDING.add(new PendingLaunch(target, v, attacker));
    }

    private static void applyPending() {
        if (PENDING.isEmpty()) return;
        for (PendingLaunch pl : PENDING) {
            Entity e = pl.target();
            if (e.isRemoved()) continue;
            e.setVelocity(pl.velocity());
            e.velocityModified = true;
            trackLaunch(e, pl.velocity(), pl.attacker());
        }
        PENDING.clear();
    }

    private static void tickLaunched(ServerWorld world) {
        if (LAUNCHED.isEmpty()) return;
        // Respect time dilation: launched things only move when the world does.
        if (!world.getTickManager().shouldTick()) return;
        VaporConfig c = VaporConfig.get();
        List<Launched> passedOn = new ArrayList<>();

        Iterator<Launched> it = LAUNCHED.values().iterator();
        while (it.hasNext()) {
            Launched l = it.next();
            Entity e = l.entity;
            if (e.getWorld() != world) continue;
            if (e.isRemoved() || --l.life <= 0) {
                it.remove();
                continue;
            }
            l.age++;
            Vec3d before = l.lastVelocity;
            double horizontal = Math.sqrt(before.x * before.x + before.z * before.z);

            // Slammed into a wall?
            if (e.horizontalCollision && horizontal > c.impactMinSpeed) {
                impact(world, l, before, horizontal, c);
                it.remove();
                continue;
            }

            // Ran into another creature? Hand the momentum on, like a Newton's cradle.
            if (c.cradleChains && before.length() > c.cradleMinSpeed) {
                Entity next = null;
                for (Entity o : world.getOtherEntities(e, e.getBoundingBox().expand(0.25),
                        o -> o instanceof LivingEntity && o.isAlive() && !o.isSpectator()
                                && !o.getUuid().equals(l.attacker) && !LAUNCHED.containsKey(o.getUuid()))) {
                    next = o;
                    break;
                }
                if (next != null) {
                    Vec3d handed = before.multiply(0.85).add(0, c.cradleLift * 0.5, 0);
                    next.setVelocity(handed);
                    next.velocityModified = true;
                    e.setVelocity(before.multiply(0.12));
                    e.velocityModified = true;
                    float bump = (float) Math.max(1.0, (before.length() - c.impactMinSpeed) * c.impactDamagePerSpeed * 0.35);
                    DamageSource src = VaporRegistry.damage(world, VaporRegistry.KINETIC_IMPACT, attackerOf(world, l));
                    if (next instanceof LivingEntity nl) nl.damage(src, bump);
                    world.spawnParticles(ParticleTypes.CRIT, next.getX(), next.getBodyY(0.5), next.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
                    world.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                            SoundCategory.PLAYERS, 1.0f, 0.8f);
                    passedOn.add(new Launched(next, l.attacker, handed));
                }
            }

            // Settled down: stop watching.
            if (l.age > 4 && e.isOnGround() && e.getVelocity().horizontalLengthSquared() < 0.01) {
                it.remove();
                continue;
            }
            l.lastVelocity = e.getVelocity();
        }
        for (Launched l : passedOn) LAUNCHED.put(l.entity.getUuid(), l);
    }

    private static void impact(ServerWorld world, Launched l, Vec3d before, double horizontal, VaporConfig c) {
        Entity e = l.entity;
        float damage = (float) ((horizontal - c.impactMinSpeed) * c.impactDamagePerSpeed);
        if (e instanceof LivingEntity living && damage > 0.5f) {
            living.timeUntilRegen = 0;
            living.damage(VaporRegistry.damage(world, VaporRegistry.KINETIC_IMPACT, attackerOf(world, l)), damage);
        }

        // Find the block they hit, for debris and sound.
        Vec3d dir = new Vec3d(before.x, 0, before.z).normalize();
        Vec3d probe = e.getBoundingBox().getCenter().add(dir.multiply(e.getWidth() * 0.5 + 0.4));
        BlockPos hitPos = BlockPos.ofFloored(probe);
        BlockState state = world.getBlockState(hitPos);
        if (state.isAir()) state = world.getBlockState(hitPos.down());
        if (!state.isAir()) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), probe.x, probe.y, probe.z,
                    12 + (int) Math.min(40, horizontal * 12), 0.3, 0.4, 0.3, 0.15);
            world.playSound(null, probe.x, probe.y, probe.z, state.getSoundGroup().getBreakSound(),
                    SoundCategory.BLOCKS, 1.0f, 0.7f);
        }
        world.spawnParticles(ParticleTypes.POOF, probe.x, probe.y, probe.z, 8, 0.2, 0.3, 0.2, 0.05);
        world.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_GENERIC_BIG_FALL,
                SoundCategory.PLAYERS, 1.0f, 0.8f);

        // Bounce off a little.
        e.setVelocity(-before.x * 0.2, Math.max(e.getVelocity().y, 0.1), -before.z * 0.2);
        e.velocityModified = true;
    }

    @Nullable
    private static Entity attackerOf(ServerWorld world, Launched l) {
        return l.attacker == null ? null : world.getEntity(l.attacker);
    }
}
