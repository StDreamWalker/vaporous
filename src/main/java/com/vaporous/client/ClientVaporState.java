package com.vaporous.client;

import com.vaporous.net.VaporPayloads;
import com.vaporous.time.TimeDilation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/** Everything the client knows about speedsters, including itself. */
public final class ClientVaporState {

    public record Remote(boolean form, boolean time, float vapor) {}

    private static final Int2ObjectMap<Remote> REMOTE = new Int2ObjectOpenHashMap<>();

    // Local player, as confirmed by the server.
    public static boolean powered;
    public static boolean form;
    public static boolean time;
    // Local player's vapor level, measured on this client from its own speed.
    public static float vapor;

    private static long lastTickNanos = System.nanoTime();

    private ClientVaporState() {}

    public static void onState(VaporPayloads.VaporState p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            powered = p.powered();
            form = p.powered() && p.form();
            time = p.powered() && p.time();
            if (!form) vapor = 0f;
        } else if (!p.powered()) {
            REMOTE.remove(p.entityId());
        } else {
            REMOTE.put(p.entityId(), new Remote(p.form(), p.time(), p.vapor()));
        }
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
        powered = false;
        form = false;
        time = false;
        vapor = 0f;
        TimeDilation.CLIENT.reset();
    }
}
