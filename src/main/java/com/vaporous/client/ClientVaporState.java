package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.net.VaporPayloads;
import com.vaporous.power.Variant;
import com.vaporous.time.TimeDilation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/** Everything the client knows about speedsters, including itself. */
public final class ClientVaporState {

    public record Remote(int variant, boolean form, boolean time, float vapor) {}

    private static final Int2ObjectMap<Remote> REMOTE = new Int2ObjectOpenHashMap<>();

    // Local player, as confirmed by the server.
    /** Which phial (see Variant); 0 = not a speedster. */
    public static int variant;
    public static boolean powered;
    public static boolean form;
    public static boolean time;
    // Local player's vapor level, measured on this client from its own speed.
    public static float vapor;
    // Local player's movement, for the speedometer (blocks per second).
    public static double realSpeed;
    public static double perceivedSpeed;
    public static boolean flying;

    private static long lastTickNanos = System.nanoTime();

    private ClientVaporState() {}

    public static void onState(VaporPayloads.VaporState p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isPowered = Variant.isValid(p.variant());
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            variant = isPowered ? p.variant() : Variant.NONE;
            powered = isPowered;
            form = isPowered && p.form();
            time = isPowered && p.time();
            if (!form) vapor = 0f;
        } else if (!isPowered) {
            REMOTE.remove(p.entityId());
        } else {
            REMOTE.put(p.entityId(), new Remote(p.variant(), p.form(), p.time(), p.vapor()));
        }
    }

    /** Speed/time stats for the phial the local player drank. */
    public static VaporConfig.FormStats stats() {
        return VaporConfig.get().stats(variant);
    }

    @Nullable
    public static Remote remote(int entityId) {
        return REMOTE.get(entityId);
    }

    public static void markTick() {
        lastTickNanos = System.nanoTime();
    }

    /** How far we are between the last tick and the next one (0..1), measured independently of vanilla. */
    public static float partialTick() {
        return MathHelper.clamp((System.nanoTime() - lastTickNanos) / 50_000_000f, 0f, 1f);
    }

    /**
     * Partial tick for things living in slowed time: one world tick is stretched across
     * {@code factor} real ticks, so slowed entities glide instead of stuttering.
     */
    public static float slowedDelta() {
        TimeDilation.Clock c = TimeDilation.CLIENT;
        return MathHelper.clamp((c.phase() + partialTick()) / c.factor(), 0f, 1f);
    }

    public static void reset() {
        REMOTE.clear();
        variant = Variant.NONE;
        powered = false;
        form = false;
        time = false;
        vapor = 0f;
        realSpeed = 0.0;
        perceivedSpeed = 0.0;
        flying = false;
        TimeDilation.CLIENT.reset();
    }
}
