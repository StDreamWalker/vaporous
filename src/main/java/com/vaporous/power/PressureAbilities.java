package com.vaporous.power;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
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
import net.minecraft.world.RaycastContext;


/**
 * The vapor speedster's answer to "throwing lightning": he compresses air.
 * <b>Pressure Jet</b>: a beam of compressed air that slams, damages and snuffs flames.
 * Hold to compress harder. Sneak while firing, looking down, to jet yourself upward.
 */
public final class PressureAbilities {

    private PressureAbilities() {}

    static void init() {
    }

    // ================================================================== Pressure Jet

    public static void blast(ServerPlayerEntity p, SpeedsterState s, float charge, int variant) {
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
        SimpleParticleType wisp = VaporRegistry.particleFor(variant);

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
            MomentumCombat.trackLaunch(e, e.getVelocity(), p.getUuid(), wisp); // trails, and slamming into a wall still hurts
        }

        if (hit.getType() == HitResult.Type.BLOCK) {
            snuffFires(world, hit.getBlockPos(), 1 + Math.round(charge * 2));
        }

        // Visuals: a cone of vapor along the jet, a dense core streak, and a burst on impact.
        for (double d = 0.8; d < len; d += 0.6) {
            Vec3d pt = eye.add(dir.multiply(d)).add(0, -0.15, 0);
            double spread = 0.06 + d * 0.025 * (0.5 + charge);
            world.spawnParticles(wisp, pt.x, pt.y, pt.z, 2 + (int) (charge * 3), spread, spread, spread, 0.015);
        }
        Vec3d muzzle = eye.add(dir.multiply(0.9)).add(0, -0.15, 0);
        int streaks = 8 + (int) (16 * charge);
        for (int i = 0; i < streaks; i++) {
            // count = 0 -> the dx/dy/dz values become a direction and "speed" becomes velocity
            world.spawnParticles(ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 0, dir.x, dir.y, dir.z, 0.6 + charge * 0.8);
        }
        Vec3d end = eye.add(dir.multiply(len));
        world.spawnParticles(ParticleTypes.POOF, end.x, end.y, end.z, 10 + (int) (20 * charge), 0.4, 0.4, 0.4, 0.08);
        world.spawnParticles(wisp, end.x, end.y, end.z, 15 + (int) (25 * charge), 0.6, 0.6, 0.6, 0.06);

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
}
