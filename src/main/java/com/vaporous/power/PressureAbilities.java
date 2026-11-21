package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The vapor speedster's answer to "throwing lightning": he compresses air.
 * <ul>
 *   <li><b>Pressure Jet</b> (positive pressure) — a beam of compressed air that slams, damages and
 *   snuffs flames. Hold to compress harder. Sneak while firing, looking down, to jet yourself upward.</li>
 *   <li><b>Vacuum Well</b> (negative pressure) — a pocket of near-nothing that drags creatures and items
 *   in, starves them of air, then collapses with a thunderclap.</li>
 * </ul>
 */
public final class PressureAbilities {

    private static final List<VacuumZone> ZONES = new ArrayList<>();

    private static final class VacuumZone {
        final RegistryKey<World> dimension;
        final Vec3d center;
        final double radius;
        final int duration;
        final UUID owner;
        final float power;
        final float vapor;
        int age;

        VacuumZone(RegistryKey<World> dimension, Vec3d center, double radius, int duration, UUID owner, float power, float vapor) {
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
            this.duration = duration;
            this.owner = owner;
            this.power = power;
            this.vapor = vapor;
        }
    }

    private PressureAbilities() {}

    static void init() {
        ServerTickEvents.END_WORLD_TICK.register(PressureAbilities::tickZones);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
    }

    // ================================================================== Pressure Jet

    public static void blast(ServerPlayerEntity p, SpeedsterState s, float charge) {
        if (s.blastCooldown > 0) return;
        VaporConfig c = VaporConfig.get();
        s.blastCooldown = c.blastCooldownTicks;

        ServerWorld world = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        double range = c.blastRange * (0.45 + 0.55 * charge);

        BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(dir.multiply(range)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        double len = hit.getType() == HitResult.Type.MISS ? range : hit.getPos().distanceTo(eye);
        float power = 0.35f + 0.65f * charge;
        DamageSource source = VaporRegistry.damage(world, VaporRegistry.PRESSURE_JET, p);

        Box sweep = new Box(eye, eye.add(dir.multiply(len))).expand(2.5);
        for (Entity e : world.getOtherEntities(p, sweep, t -> t.isAlive() && !t.isSpectator())) {
            Vec3d rel = e.getBoundingBox().getCenter().subtract(eye);
            double along = rel.dotProduct(dir);
            if (along < 0 || along > len + 0.5) continue;
            double perpendicular = rel.subtract(dir.multiply(along)).length();
            double beamRadius = 1.1 + 0.9 * charge + e.getWidth() * 0.5;
            if (perpendicular > beamRadius) continue;

            double falloff = 1.0 - 0.45 * (along / Math.max(1.0, len));
            if (e instanceof LivingEntity living) {
                living.damage(source, (float) ((c.blastDamage * power + c.blastVaporBonus * s.vapor) * falloff));
            }
            double push = (0.9 + 2.2 * charge) * falloff;
            e.addVelocity(dir.x * push, dir.y * push * 0.6 + 0.2, dir.z * push);
            e.velocityModified = true;
            e.extinguish(); // a wall of compressed air snuffs flames
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            snuffFires(world, hit.getBlockPos(), 1 + Math.round(charge * 2));
        }

        // Visuals: a cone of vapor along the jet, a dense core streak, and a burst on impact.
        for (double d = 0.8; d < len; d += 0.6) {
            Vec3d pt = eye.add(dir.multiply(d)).add(0, -0.15, 0);
            double spread = 0.06 + d * 0.025 * (0.5 + charge);
            world.spawnParticles(VaporRegistry.VAPOR, pt.x, pt.y, pt.z, 2 + (int) (charge * 3), spread, spread, spread, 0.015);
        }
        Vec3d muzzle = eye.add(dir.multiply(0.9)).add(0, -0.15, 0);
        int streaks = 8 + (int) (16 * charge);
        for (int i = 0; i < streaks; i++) {
            // count = 0 -> the dx/dy/dz values become a direction and "speed" becomes velocity
            world.spawnParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 0, dir.x, dir.y, dir.z, 0.6 + charge * 0.8);
        }
        Vec3d end = eye.add(dir.multiply(len));
        world.spawnParticles(ParticleTypes.POOF, end.x, end.y, end.z, 10 + (int) (20 * charge), 0.4, 0.4, 0.4, 0.08);
        world.spawnParticles(VaporRegistry.VAPOR, end.x, end.y, end.z, 15 + (int) (25 * charge), 0.6, 0.6, 0.6, 0.06);

        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BREEZE_SHOOT,
                SoundCategory.PLAYERS, 1.0f, 1.25f - 0.45f * charge);
        world.playSound(null, end.x, end.y, end.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 0.7f + 0.5f * charge, 0.6f);

        // The thrust itself is applied on the client for responsiveness; just forgive the fall here.
        if (p.isSneaking()) p.fallDistance = 0f;
    }

    private static void snuffFires(ServerWorld world, BlockPos center, int r) {
        for (BlockPos bp : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
            BlockState st = world.getBlockState(bp);
            if (st.isIn(BlockTags.FIRE)) {
                world.removeBlock(bp, false);
            } else if (st.getBlock() instanceof CampfireBlock && st.get(CampfireBlock.LIT)) {
                world.setBlockState(bp, st.with(CampfireBlock.LIT, false));
            }
        }
        world.playSound(null, center, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.4f);
    }

    // ================================================================== Vacuum Well

    public static void vacuum(ServerPlayerEntity p, SpeedsterState s, float charge) {
        if (s.vacuumCooldown > 0) return;
        VaporConfig c = VaporConfig.get();
        s.vacuumCooldown = c.vacuumCooldownTicks;

        ServerWorld world = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        Vec3d center;
        if (p.isSneaking()) {
            // Sneak: open the well right in front of you to reel things in.
            center = eye.add(dir.multiply(2.0));
        } else {
            double range = c.vacuumRange;
            BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(dir.multiply(range)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            center = hit.getType() == HitResult.Type.MISS
                    ? eye.add(dir.multiply(range))
                    : hit.getPos().subtract(dir.multiply(1.0)); // step back out of the wall
        }

        double radius = c.vacuumRadius * (0.6 + 0.4 * charge);
        int duration = 30 + (int) (30 * charge);
        ZONES.add(new VacuumZone(world.getRegistryKey(), center, radius, duration, p.getUuid(), 0.5f + 0.5f * charge, s.vapor));

        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_BREEZE_INHALE,
                SoundCategory.PLAYERS, 1.2f, 0.6f);
    }

    private static void tickZones(ServerWorld world) {
        if (ZONES.isEmpty()) return;
        // Respect time dilation: the vacuum only progresses when the world does.
        if (!world.getTickManager().shouldTick()) return;

        Iterator<VacuumZone> it = ZONES.iterator();
        while (it.hasNext()) {
            VacuumZone z = it.next();
            if (!z.dimension.equals(world.getRegistryKey())) continue;
            z.age++;

            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
            Box box = new Box(z.center, z.center).expand(z.radius);
            for (Entity e : world.getOtherEntities(owner, box, t -> t.isAlive() && !t.isSpectator())) {
                Vec3d to = z.center.subtract(e.getBoundingBox().getCenter());
                double dist = to.length();
                if (dist > z.radius || dist < 0.05) continue;
                double strength = 0.08 + 0.14 * z.power * (1.0 - dist / z.radius * 0.6);
                e.setVelocity(e.getVelocity().multiply(0.85).add(to.normalize().multiply(strength)));
                e.velocityModified = true;
                if (dist < 1.6 && z.age % 10 == 0 && e instanceof LivingEntity living) {
                    // the air is being pulled out of their lungs
                    living.damage(VaporRegistry.damage(world, VaporRegistry.VACUUM_COLLAPSE, owner), 1.0f + z.power);
                }
            }

            // Vapor streaming inward from all sides, and a dense churning core.
            Random r = world.random;
            for (int i = 0; i < 7; i++) {
                Vec3d off = randomUnit(r).multiply(z.radius * (0.55 + 0.45 * r.nextDouble()));
                Vec3d pos = z.center.add(off);
                Vec3d vel = off.normalize().multiply(-1.0);
                world.spawnParticles(VaporRegistry.VAPOR, pos.x, pos.y, pos.z, 0, vel.x, vel.y, vel.z, 0.32 + 0.1 * z.power);
            }
            world.spawnParticles(VaporRegistry.VAPOR, z.center.x, z.center.y, z.center.z, 3, 0.2, 0.2, 0.2, 0.01);
            if (z.age % 12 == 0) {
                world.playSound(null, z.center.x, z.center.y, z.center.z, SoundEvents.ENTITY_BREEZE_WHIRL,
                        SoundCategory.PLAYERS, 0.6f, 0.5f);
            }

            if (z.age >= z.duration) {
                implode(world, z, owner);
                it.remove();
            }
        }
    }

    private static void implode(ServerWorld world, VacuumZone z, ServerPlayerEntity owner) {
        VaporConfig c = VaporConfig.get();
        float dmg = c.vacuumDamage * (0.5f + 0.5f * z.power) + 3.0f * z.vapor;
        double coreRadius = Math.max(2.0, z.radius * 0.55);
        DamageSource source = VaporRegistry.damage(world, VaporRegistry.VACUUM_COLLAPSE, owner);

        Box box = new Box(z.center, z.center).expand(coreRadius);
        for (Entity e : world.getOtherEntities(owner, box, t -> t.isAlive() && !t.isSpectator())) {
            Vec3d from = e.getBoundingBox().getCenter().subtract(z.center);
            double dist = from.length();
            if (dist > coreRadius) continue;
            if (e instanceof LivingEntity living) {
                living.damage(source, (float) (dmg * (1.0 - 0.5 * dist / coreRadius)));
            }
            // air rushing back in throws everything outward
            Vec3d out = dist > 0.05 ? from.normalize() : new Vec3d(0, 1, 0);
            e.addVelocity(out.x * 0.7, 0.35, out.z * 0.7);
            e.velocityModified = true;
        }

        world.spawnParticles(ParticleTypes.SONIC_BOOM, z.center.x, z.center.y, z.center.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.POOF, z.center.x, z.center.y, z.center.z, 40, z.radius * 0.25, z.radius * 0.25, z.radius * 0.25, 0.12);
        world.spawnParticles(VaporRegistry.VAPOR, z.center.x, z.center.y, z.center.z, 60, z.radius * 0.3, z.radius * 0.3, z.radius * 0.3, 0.1);
        world.playSound(null, z.center.x, z.center.y, z.center.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST,
                SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    private static Vec3d randomUnit(Random r) {
        double u = r.nextDouble() * 2.0 - 1.0;
        double theta = r.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(1.0 - u * u);
        return new Vec3d(s * Math.cos(theta), u, s * Math.sin(theta));
    }
}
