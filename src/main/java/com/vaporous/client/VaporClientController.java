package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.net.VaporPayloads;
import com.vaporous.power.VaporAttributes;
import com.vaporous.time.TimeDilation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/** Runs at the end of every client tick: measures speed, thins the body, moves it, emits vapor. */
public final class VaporClientController {
    private static float lastReported = -1f;
    private static int ticksSinceReport;

    private VaporClientController() {}

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        VaporConfig c = VaporConfig.get();

        VaporKeys.handle(mc, p);

        // Real speed this tick (prevX/Y/Z hold where the player started the tick).
        double dx = p.getX() - p.prevX;
        double dy = p.getY() - p.prevY;
        double dz = p.getZ() - p.prevZ;
        double groundBps = Math.sqrt(dx * dx + dz * dz) * 20.0;
        double totalBps = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
        if (totalBps > 400.0) totalBps = 0.0; // a teleport, not running
        updateSpeedometer(totalBps);

        boolean active = ClientVaporState.powered && ClientVaporState.form && !p.isSpectator();
        boolean floating = false;
        if (active) {
            float v = ClientVaporState.vapor;
            if (VaporMovement.isFlying()) {
                // In flight, airspeed keeps the body thin, the same way ground speed does.
                float target = speedToVapor(totalBps, c);
                v += (float) ((target - v) * (target > v ? c.vaporBuildRate : c.vaporDecayRate * 0.5));
            } else if (!p.isOnGround()) {
                // Airborne or scaling a wall: momentum (and therefore vapor) is held, fading only slowly.
                v -= 0.002f;
            } else {
                float target = speedToVapor(groundBps, c);
                if (p.isSneaking()) target = 0f;                             // sneak to re-condense on purpose
                else if (!p.isSprinting()) target = Math.min(target, 0.1f);  // real thinning needs a full sprint
                v += (float) ((target - v) * (target > v ? c.vaporBuildRate : c.vaporDecayRate));
            }
            ClientVaporState.vapor = MathHelper.clamp(v, 0f, 1f);

            VaporAttributes.applyClient(p, true, ClientVaporState.vapor);
            floating = VaporMovement.tick(p, ClientVaporState.vapor);
        } else {
            ClientVaporState.vapor = 0f;
            VaporMovement.stop();
            VaporAttributes.clearClient(p);
        }
        ClientVaporState.flying = VaporMovement.isFlying();

        // Keep the server informed (momentum damage, others' view of the trail, and fall forgiveness).
        if (ClientVaporState.powered) {
            ticksSinceReport++;
            float v = ClientVaporState.vapor;
            if (floating || Math.abs(v - lastReported) > 0.01f || ticksSinceReport >= 20) {
                if (ClientPlayNetworking.canSend(VaporPayloads.VaporReport.ID)) {
                    ClientPlayNetworking.send(new VaporPayloads.VaporReport(v, floating));
                }
                lastReported = v;
                ticksSinceReport = 0;
            }
        }

        VaporTrail.tick(mc);
    }

    private static float speedToVapor(double bps, VaporConfig c) {
        return (float) MathHelper.clamp((bps - 6.0) / (c.fullVaporSpeed - 6.0), 0.0, 1.0);
    }

    /**
     * Your legs move at the same real speed whether time is slowed or not; what changes is how fast
     * the world sees you go. In 1/12 time, every block you cover takes the world 1/12 as long to
     * notice, so your perceived speed is real speed x 12.
     */
    private static void updateSpeedometer(double realBps) {
        int factor = TimeDilation.CLIENT.factor();
        double perceived = realBps * factor;
        ClientVaporState.realSpeed = realBps;
        ClientVaporState.perceivedSpeed += (perceived - ClientVaporState.perceivedSpeed) * 0.3;
        if (ClientVaporState.perceivedSpeed < 0.05) ClientVaporState.perceivedSpeed = 0.0;
    }
}
