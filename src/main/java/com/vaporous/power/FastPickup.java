package com.vaporous.power;

import com.vaporous.VaporConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Speedsters scoop up items instantly. Fixes two slow-pickup problems:
 * <ul>
 *   <li>In slowed time, a fresh drop's pickup delay only counts down on world ticks, so a mined block
 *   could take seconds to become collectible. Fresh drops are made collectible straight away.</li>
 *   <li>At high speed you blow past items between ticks. The pickup area covers the whole path you
 *   ran since last tick, not just where you ended up.</li>
 * </ul>
 * Items you deliberately dropped keep their normal delay, so you don't instantly re-grab them.
 */
final class FastPickup {
    /** Only fresh drops get their delay skipped (age counts world ticks, so this also holds in slowed time). */
    private static final int FRESH_DROP_AGE = 40;

    private FastPickup() {}

    static void collect(ServerPlayerEntity p, SpeedsterState s, VaporConfig c) {
        ServerWorld world = p.getServerWorld();
        double r = c.pickupRange;
        Box area = p.getBoundingBox().expand(r, 0.75, r).stretch(s.motion.multiply(-1.0));

        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, area,
                i -> i.isAlive() && !i.getStack().isEmpty())) {
            if (item.cannotPickup()) {
                boolean droppedByMe = item.getOwner() == p;
                if (droppedByMe || item.getItemAge() > FRESH_DROP_AGE) continue;
                item.resetPickupDelay();
            }
            item.onPlayerCollision(p);
        }
        for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, area, Entity::isAlive)) {
            orb.onPlayerCollision(p);
        }
    }
}
