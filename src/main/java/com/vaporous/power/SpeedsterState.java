package com.vaporous.power;

/** Live (non-saved) server-side state of one powered player. */
public final class SpeedsterState {
    /** Vapor form: the speed power is switched on. */
    public boolean form;
    /** Time dilation is switched on. */
    public boolean time;
    /** 0 = fully dense, 1 = fully vaporous. Reported by the owning client. */
    public float vapor;

    public int blastCooldown;
    public int vacuumCooldown;

    float lastSentVapor = -1f;
    int ticksSinceSync;
    boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }
}
