package com.vaporous.time;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Selective slow motion.
 * <p>
 * When the factor is N, the world only advances on one tick out of every N. On the other
 * ("off") ticks, {@code TickManagerMixin} tells vanilla that the world is frozen, so world
 * time, weather, block/fluid ticks, random ticks, block entities, mob spawning and every
 * non-player entity skip the tick. Players (and anything a player is riding) keep ticking
 * at full speed, so your walking, gravity, mining, placing and attacking are untouched.
 * <p>
 * On the client the same thing happens, and the renderer stretches each world tick across
 * N real ticks so slowed entities glide smoothly instead of stuttering.
 */
public final class TimeDilation {

    public static final class Clock {
        private volatile int factor = 1;
        private volatile long counter;
        private volatile boolean offTick;

        public int factor() {
            return factor;
        }

        public boolean active() {
            return factor > 1;
        }

        /** True during a tick in which the (non-player) world should not advance. */
        public boolean offTick() {
            return offTick;
        }

        /** How many real ticks have passed since the world last advanced (0 .. factor-1). */
        public int phase() {
            int f = factor;
            return f > 1 ? (int) (counter % f) : 0;
        }

        public void setFactor(int f) {
            f = Math.max(1, f);
            if (f != factor) {
                factor = f;
                if (f <= 1) offTick = false;
            }
        }

        /** Called once at the very start of every tick. */
        public void advance() {
            counter++;
            int f = factor;
            offTick = f > 1 && (counter % f) != 0;
        }

        public void reset() {
            factor = 1;
            offTick = false;
        }
    }

    public static final Clock SERVER = new Clock();
    public static final Clock CLIENT = new Clock();

    private TimeDilation() {}

    /** Players, and anything carrying a player, ignore the slowdown. */
    public static boolean isExempt(Entity e) {
        if (e instanceof PlayerEntity) return true;
        for (Entity passenger : e.getPassengerList()) {
            if (passenger instanceof PlayerEntity) return true;
        }
        return false;
    }
}
