package com.vaporous.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A light, deterministic model of wind and thermals so every client sees the same currents. */
public final class AirCurrents {
    private AirCurrents() {}

    /** Prevailing wind in blocks/tick. Shifts direction over a couple of minutes, gusts, and grows with altitude and storms. */
    public static Vec3d wind(World world, Vec3d pos) {
        long time = world.getTime();
        double t = time / 2400.0;
        double angle = (Math.sin(t * 1.3) + Math.sin(t * 0.7 + 1.7)) * Math.PI;
        double altitude = MathHelper.clamp((pos.y - 62.0) / 120.0, 0.15, 1.0);
        double gust = 0.6 + 0.4 * Math.sin(time / 37.0);
        double strength = 0.035 * altitude * gust;
        if (world.isRaining()) strength *= 1.6;
        if (world.isThundering()) strength *= 1.4;
        return new Vec3d(Math.cos(angle) * strength, 0.0, Math.sin(angle) * strength);
    }

    /** 0 = no updraft, up to 1 = strong. Heat sources up to 24 blocks below you lift a vaporous body. */
    public static double thermalStrength(World world, BlockPos start) {
        BlockPos.Mutable m = start.mutableCopy();
        for (int i = 0; i < 24; i++) {
            m.move(Direction.DOWN);
            BlockState st = world.getBlockState(m);
            if (isHot(st)) return 1.0 - (i / 24.0) * 0.6;
            if (st.isSolidBlock(world, m)) return 0.0;
        }
        return 0.0;
    }

    private static boolean isHot(BlockState st) {
        if (st.isIn(BlockTags.FIRE)) return true;
        if (st.isOf(Blocks.LAVA) || st.isOf(Blocks.MAGMA_BLOCK) || st.isOf(Blocks.LAVA_CAULDRON)) return true;
        return st.getBlock() instanceof CampfireBlock && st.get(CampfireBlock.LIT);
    }
}
