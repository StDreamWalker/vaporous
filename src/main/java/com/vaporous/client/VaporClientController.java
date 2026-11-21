package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.net.VaporPayloads;
import com.vaporous.power.VaporAttributes;
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

        boolean active = ClientVaporState.powered && ClientVaporState.form && !p.isSpectator();
        if (active) {
            // Speed this tick, in blocks per second (prevX/Z hold where we started the tick).
            double dx = p.getX() - p.prevX;
            double dz = p.getZ() - p.prevZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;

            float v = ClientVaporState.vapor;
            if (!p.isOnGround()) {
                // Airborne or scaling a wall: momentum (and therefore vapor) is held, fading only slowly.
                v -= 0.002f;
            } else {
                float target = (float) MathHelper.clamp((bps - 6.0) / (c.fullVaporSpeed - 6.0), 0.0, 1.0);
                if (p.isSneaking()) target = 0f;                             // sneak to re-condense on purpose
                else if (!p.isSprinting()) target = Math.min(target, 0.1f);  // real thinning needs a full sprint
                v += (float) ((target - v) * (target > v ? c.vaporBuildRate : c.vaporDecayRate));
            }
            ClientVaporState.vapor = MathHelper.clamp(v, 0f, 1f);

            VaporAttributes.applyClient(p, true, ClientVaporState.vapor);
            VaporMovement.tick(p, ClientVaporState.vapor);
        } else {
            ClientVaporState.vapor = 0f;
            VaporAttributes.clearClient(p);
        }

        // Keep the server informed (it uses vapor for momentum damage and to show others the trail).
        if (ClientVaporState.powered) {
            ticksSinceReport++;
            float v = ClientVaporState.vapor;
            if (Math.abs(v - lastReported) > 0.01f || ticksSinceReport >= 20) {
                if (ClientPlayNetworking.canSend(VaporPayloads.VaporReport.ID)) {
                    ClientPlayNetworking.send(new VaporPayloads.VaporReport(v));
                }
                lastReported = v;
                ticksSinceReport = 0;
            }
        }

        VaporTrail.tick(mc);
    }
}
