package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.net.VaporPayloads;
import com.vaporous.time.TimeDilation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Right-click handling for speedsters.
 * <ul>
 *   <li>In slowed time (either phial), right-clicking a projectile in flight snatches it.</li>
 *   <li>With arrows in hand, hold right-click to wind up and release to throw, like a trident.
 *   Arrows in the off hand work when your main hand is empty.</li>
 * </ul>
 */
public final class ProjectileHands {
    /** Minimum wind-up before a release counts as a throw (a trident needs 10). */
    private static final int MIN_THROW_TICKS = 5;

    public static int throwHold;
    private static boolean throwOffhand;
    private static boolean useWasDown;
    /** After a catch, ignore the held button until it's released so you don't instantly throw what you caught. */
    private static boolean suppressUntilRelease;

    private ProjectileHands() {}

    static void tick(MinecraftClient mc, ClientPlayerEntity p) {
        boolean down = mc.currentScreen == null && mc.options.useKey.isPressed();
        boolean pressedNow = down && !useWasDown;
        useWasDown = down;
        if (!down) suppressUntilRelease = false;

        if (!ClientVaporState.powered || p.isSpectator() || !p.isAlive()) {
            throwHold = 0;
            return;
        }

        // ---- Catch
        if (pressedNow && TimeDilation.CLIENT.active()) {
            Entity target = findCatchTarget(mc, p);
            if (target != null) {
                send(new VaporPayloads.Catch(target.getId()));
                throwHold = 0;
                suppressUntilRelease = true;
                return;
            }
        }

        // ---- Throw
        VaporConfig c = VaporConfig.get();
        Hand hand = throwHand(p);
        if (down && hand != null && !suppressUntilRelease) {
            if (throwHold == 0) throwOffhand = hand == Hand.OFF_HAND;
            throwHold = Math.min(throwHold + 1, c.throwChargeTicks);
        } else if (throwHold > 0) {
            if (!down && throwHold >= MIN_THROW_TICKS) {
                float charge = Math.min(1f, throwHold / (float) c.throwChargeTicks);
                send(new VaporPayloads.Throw(charge, throwOffhand));
            }
            throwHold = 0;
        }
    }

    public static float throwCharge() {
        return Math.min(1f, throwHold / (float) Math.max(1, VaporConfig.get().throwChargeTicks));
    }

    @Nullable
    private static Hand throwHand(ClientPlayerEntity p) {
        if (p.getMainHandStack().getItem() instanceof ArrowItem) return Hand.MAIN_HAND;
        if (p.getMainHandStack().isEmpty() && p.getOffHandStack().getItem() instanceof ArrowItem) return Hand.OFF_HAND;
        return null;
    }

    /** The in-flight projectile nearest your crosshair, within catch range. */
    @Nullable
    private static Entity findCatchTarget(MinecraftClient mc, ClientPlayerEntity p) {
        if (mc.world == null) return null;
        double range = VaporConfig.get().catchRange;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(p, p.getBoundingBox().expand(range),
                t -> t instanceof ProjectileEntity && !(t instanceof FishingBobberEntity) && t.isAlive())) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double along = to.dotProduct(look);
            if (along < 0.3 || along > range) continue;
            double off = to.subtract(look.multiply(along)).length();
            double tolerance = 0.6 + along * 0.12; // a little more forgiving further out
            if (off > tolerance) continue;
            double score = off + along * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static void send(net.minecraft.network.packet.CustomPayload payload) {
        if (ClientPlayNetworking.canSend(payload.getId())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
