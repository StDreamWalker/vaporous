package com.vaporous.power;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Live (non-saved) server-side state of one powered player. */
public final class SpeedsterState {
    /** The speed form is switched on. */
    public boolean form;
    /** Time dilation is switched on. */
    public boolean time;
    /** 0 = fully dense, 1 = fully vaporous. Reported by the owning client. */
    public float vapor;
    /** Client said the power is holding the body up (flight, wall-run, air currents). */
    public boolean floating;

    /** Smoothed movement per tick, measured on the server from the player's position. */
    public Vec3d motion = Vec3d.ZERO;
    @Nullable
    Vec3d lastPos;

    public int blastCooldown;
    /** Real ticks left during which mobs can't hurt you (from contact in slowed time). */
    public int mobImmuneTicks;

    float lastSentVapor = -1f;
    int ticksSinceSync;
    boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }
}
