package com.vaporous.power;

/** Live (non-saved) server-side state of one powered player. */
public final class SpeedsterState {
    /** Vapor form: the speed power is switched on. */
    public boolean form;
    /** Time dilation is switched on. */
    public boolean time;
    /** 0 = fully dense, 1 = fully vaporous. Reported by the owning client. */
    public float vapor;

    /** Client said the power is holding the body up (flight, wall-run, air currents). */
    public boolean floating;

    public int blastCooldown;
    public int vacuumCooldown;

    float lastSentVapor = -1f;
    int ticksSinceSync;
    boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }
}
