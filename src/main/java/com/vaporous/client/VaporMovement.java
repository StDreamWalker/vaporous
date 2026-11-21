package com.vaporous.client;

import com.vaporous.VaporConfig;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * "As he becomes more vaporous he can ... be carried on currents of air, and scale walls."
 * Runs on the client that controls the player; the velocity set here is used on the next tick.
 */
public final class VaporMovement {
    private VaporMovement() {}

    public static void tick(ClientPlayerEntity p, float vapor) {
        VaporConfig c = VaporConfig.get();
        if (p.getAbilities().flying || p.isFallFlying() || p.hasVehicle()
                || p.isTouchingWater() || p.isInLava() || p.isClimbing()) {
            return;
        }
        Input in = p.input;
        Vec3d vel = p.getVelocity();

        // ---- Wall scaling: run straight up anything you push into. Sneak to cling in place.
        if (vapor >= c.wallClimbVapor && p.horizontalCollision && in.movementForward > 0.05f) {
            double vy;
            if (in.sneaking) {
                vy = 0.0;
            } else {
                vy = Math.max(vel.y, 0.22 + 0.5 * vapor);
            }
            p.setVelocity(vel.x, vy, vel.z);
            p.fallDistance = 0f;
            return;
        }

        // ---- Riding air currents: hold jump while airborne.
        if (!p.isOnGround() && vapor >= c.airRideVapor && in.jumping) {
            double minFall = -0.16 + 0.14 * vapor;      // -0.02 blocks/tick at full vapor
            double vy = Math.max(vel.y, minFall);

            double thermal = AirCurrents.thermalStrength(p.getWorld(), p.getBlockPos());
            if (thermal > 0) vy = Math.min(vy + 0.10 * thermal, 0.55);   // rise on heat

            Vec3d wind = AirCurrents.wind(p.getWorld(), p.getPos()).multiply(vapor);
            Vec3d steer = Vec3d.fromPolar(0f, p.getYaw()).multiply(0.015 * in.movementForward);

            double keep = 1.04;                          // partly cancel air drag so momentum carries
            double hx = vel.x * keep + wind.x + steer.x;
            double hz = vel.z * keep + wind.z + steer.z;
            double h = Math.sqrt(hx * hx + hz * hz);
            double cap = 0.6 + 2.4 * vapor;
            if (h > cap) {
                hx *= cap / h;
                hz *= cap / h;
            }
            p.setVelocity(hx, vy, hz);
            p.fallDistance = 0f;
        }
    }
}
