package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import com.vaporous.time.TimeDilation;
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
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
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
 * <p>
 * All speeds here are <b>relative to the world</b>: in 1/20 time the world sees you cover ground 20x
 * faster, so a punch that reads Mach 2 on the speedometer lands like a Mach 2 punch.
 * <ul>
 *   <li><b>Kinetic strikes</b>: hitting something while moving forward adds damage that grows with the
 *   square of your speed, like kinetic energy, and hands it your momentum: it's flung off in your
 *   direction of travel on a long arc, trailing vapor (or plasma), like the far ball of a Newton's cradle.</li>
 *   <li><b>Wall impact</b>: a flung creature that slams into a block hard enough takes impact damage.</li>
 *   <li><b>Cradle chains</b>: a flung creature that hits another creature hands the momentum on.</li>
 *   <li><b>Projectiles</b>: anything the speedster shoots or throws gains their forward speed, arrows and
 *   tridents hit harder with it, and they hand that momentum to whatever they hit.</li>
 * </ul>
 */
public final class MomentumCombat {

    private record PendingLaunch(Entity target, Vec3d velocity, @Nullable UUID attacker, float kineticDamage,
                                 @Nullable SimpleParticleType trail) {}

    private static final class Launched {
        final Entity entity;
        @Nullable final UUID attacker;
        @Nullable final SimpleParticleType trail;
        Vec3d lastVelocity;
        Vec3d lastPos;
        int life = 120;
        int age;

        Launched(Entity entity, @Nullable UUID attacker, Vec3d velocity, @Nullable SimpleParticleType trail) {
            this.entity = entity;
            this.attacker = attacker;
            this.lastVelocity = velocity;
            this.trail = trail;
            this.lastPos = center(entity);
        }
    }

    /** What a momentum-charged projectile carries. */
    private record Charge(double addedSpeed, @Nullable SimpleParticleType trail) {}

    private static final List<PendingLaunch> PENDING = new ArrayList<>();
    private static final Map<UUID, Launched> LAUNCHED = new HashMap<>();
    private static final Map<Entity, Charge> CHARGED = new WeakHashMap<>();

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

    /** How many times faster than real time the world perceives the speedster (1 in normal time). */
    static double relativeFactor() {
        return Math.max(1, TimeDilation.SERVER.factor());
    }

    // ================================================================== melee

    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity target, @Nullable EntityHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.isSpectator()) return ActionResult.PASS;
        SpeedsterState s = SpeedsterManager.stateIfPowered(sp);
        if (s == null) return ActionResult.PASS;
        VaporConfig c = VaporConfig.get();

        // Nothing in slowed time can react to you after you hit it.
        if (TimeDilation.SERVER.active()) s.mobImmuneTicks = Math.max(s.mobImmuneTicks, c.dilationContactImmunityTicks);

        if (!s.form) return ActionResult.PASS;
        // A speedster's blows land faster than the target can recover.
        if (c.flurryIgnoresIFrames && target instanceof LivingEntity) {
            target.timeUntilRegen = 0;
        }
        if (!target.isAttackable() || !c.cradleStrikes) return ActionResult.PASS;

        Vec3d relative = forwardMomentum(sp, s, c);
        if (relative == null) return ActionResult.PASS;

        double v = relative.length();
        float kinetic = (float) Math.min(c.maxKineticDamage, c.kineticDamageScale * v * v);
        SimpleParticleType trail = VaporRegistry.particleFor(SpeedsterManager.variant(sp));
        queueLaunch(target, relative.multiply(c.cradleTransfer), sp.getUuid(), kinetic, trail);

        ServerWorld sw = sp.getServerWorld();
        sw.spawnParticles(trail, target.getX(), target.getBodyY(0.5), target.getZ(),
                12 + (int) Math.min(40, v * 3), 0.3, 0.4, 0.3, 0.1);
        sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.4);
        sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                SoundCategory.PLAYERS, 1.0f, (float) MathHelper.clamp(1.2 - v * 0.05, 0.5, 1.2));
        if (v > 8.0) {
            // a sonic crack for the really fast ones
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
                    SoundCategory.PLAYERS, 1.2f, 0.6f);
        }
        return ActionResult.PASS;
    }

    /**
     * The player's movement relative to the world (real movement x time factor), if they're moving
     * forward (roughly where they face) fast enough; otherwise null.
     */
    @Nullable
    static Vec3d forwardMomentum(ServerPlayerEntity p, SpeedsterState s, VaporConfig c) {
        Vec3d m = s.motion;
        double real = m.length();
        if (real < 1.0E-3) return null;
        if (m.dotProduct(p.getRotationVector()) < 0.4 * real) return null; // within ~65 degrees of where you face
        Vec3d relative = m.multiply(relativeFactor());
        return relative.length() >= c.cradleMinSpeed ? relative : null;
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
        // Only forward motion adds (no skewing your aim sideways), measured relative to the world.
        double along = s.motion.dotProduct(dir) * relativeFactor();
        if (along <= 0.02) return;
        double added = Math.min(along * c.projectileMomentum, Math.max(0.0, c.maxProjectileSpeed - speed));
        if (added <= 0.0) return;

        projectile.setVelocity(vel.add(dir.multiply(added)));
        projectile.velocityModified = true;

        // Arrows and tridents already hit harder the faster they fly; momentum adds a kinetic-energy bonus on top.
        if (projectile instanceof PersistentProjectileEntity arrowLike) {
            double multiplier = Math.min(c.maxProjectileDamageMultiplier, 1.0 + c.projectileKineticScale * added * added);
            arrowLike.setDamage(arrowLike.getDamage() * multiplier);
        }
        CHARGED.put(projectile, new Charge(added, VaporRegistry.particleFor(SpeedsterManager.variant(owner))));
    }

    private static boolean onDamage(LivingEntity victim, DamageSource source, float amount) {
        Entity direct = source.getSource();
        if (direct == null || CHARGED.isEmpty()) return true;
        Charge charge = CHARGED.remove(direct);
        if (charge == null || charge.addedSpeed() < 0.2) return true;

        VaporConfig c = VaporConfig.get();
        if (!c.cradleStrikes) return true;
        Vec3d v = direct.getVelocity();
        double speed = v.length();
        if (speed < 1.0E-2) return true;
        Entity attacker = source.getAttacker();
        queueLaunch(victim, v.multiply(c.projectileTransfer), attacker == null ? null : attacker.getUuid(), 0f, charge.trail());
        return true;
    }

    // ================================================================== launching

    /** Launch from outside, e.g. the Pressure Jet, so slamming into walls still hurts and leaves a trail. */
    public static void trackLaunch(Entity target, Vec3d velocity, @Nullable UUID attacker, @Nullable SimpleParticleType trail) {
        if (target instanceof LivingEntity) {
            LAUNCHED.put(target.getUuid(), new Launched(target, attacker, velocity, trail));
        }
    }

    private static void queueLaunch(Entity target, Vec3d momentum, @Nullable UUID attacker, float kineticDamage,
                                    @Nullable SimpleParticleType trail) {
        VaporConfig c = VaporConfig.get();
        double resist = 0.0;
        if (target instanceof LivingEntity living) {
            resist = MathHelper.clamp(living.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 0.0, 1.0);
        }
        Vec3d v = momentum.multiply(1.0 - resist * 0.8);
        double len = v.length();
        if (len > c.maxLaunchSpeed) {
            v = v.multiply(c.maxLaunchSpeed / len);
            len = c.maxLaunchSpeed;
        }
        // Harder hits throw higher, so the target sails a long way instead of skidding to a stop.
        double lift = Math.min(c.maxLaunchLift, c.cradleLift + c.launchLiftPerSpeed * len);
        v = v.add(0, lift, 0);
        PENDING.add(new PendingLaunch(target, v, attacker, kineticDamage, trail));
    }

    private static void applyPending() {
        if (PENDING.isEmpty()) return;
        List<PendingLaunch> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (PendingLaunch pl : batch) {
            Entity e = pl.target();
            if (e.isRemoved()) continue;
            // Damage first (it applies its own small knockback), then the launch overrides it.
            if (pl.kineticDamage() > 0.5f && e instanceof LivingEntity living && e.getWorld() instanceof ServerWorld sw) {
                Entity attacker = pl.attacker() == null ? null : sw.getEntity(pl.attacker());
                living.timeUntilRegen = 0;
                living.damage(VaporRegistry.damage(sw, VaporRegistry.KINETIC_IMPACT, attacker), pl.kineticDamage());
            }
            if (e.isRemoved()) continue;
            e.setVelocity(pl.velocity());
            e.velocityModified = true;
            trackLaunch(e, pl.velocity(), pl.attacker(), pl.trail());
        }
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

            drawTrail(world, l);

            // Slammed into a wall?
            if (e.horizontalCollision && horizontal > c.impactMinSpeed) {
                impact(world, l, before, horizontal, c);
                it.remove();
                continue;
            }

            // Ran into another creature? Hand the momentum on, like a Newton's cradle.
            if (c.cradleChains && before.length() > c.cradleMinSpeed) {
                Entity next = null;
                for (Entity o : world.getOtherEntities(e, e.getBoundingBox().expand(0.25).stretch(before),
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
                    if (next instanceof LivingEntity nl) {
                        nl.timeUntilRegen = 0;
                        nl.damage(src, bump);
                    }
                    world.spawnParticles(ParticleTypes.CRIT, next.getX(), next.getBodyY(0.5), next.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
                    world.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                            SoundCategory.PLAYERS, 1.0f, 0.8f);
                    passedOn.add(new Launched(next, l.attacker, handed, l.trail));
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

    /** A streak of vapor (or plasma) along the path the flung creature covered since last world tick. */
    private static void drawTrail(ServerWorld world, Launched l) {
        Vec3d now = center(l.entity);
        if (l.trail != null) {
            double seg = now.distanceTo(l.lastPos);
            if (seg > 0.25 && seg < 40.0) {
                int points = (int) Math.min(24, Math.ceil(seg * 1.5));
                double spread = 0.12 + l.entity.getWidth() * 0.25;
                for (int i = 0; i < points; i++) {
                    Vec3d p = l.lastPos.lerp(now, (i + 0.5) / points);
                    world.spawnParticles(l.trail, p.x, p.y, p.z, 2, spread, spread, spread, 0.01);
                }
            }
        }
        l.lastPos = now;
    }

    private static Vec3d center(Entity e) {
        return e.getPos().add(0, e.getHeight() * 0.5, 0);
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
                    12 + (int) Math.min(40, horizontal * 6), 0.3, 0.4, 0.3, 0.15);
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
