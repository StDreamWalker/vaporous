package com.vaporous.client;

import com.vaporous.VaporConfig;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * "As he becomes more vaporous he can ... be carried on currents of air, and scale walls."
 * At density 20% or lower he's light enough to ride his own momentum through the air:
 * Slipstream flight.
 * Runs on the client that controls the player; the velocity set here is used on the next tick.
 */
public final class VaporMovement {
    private static boolean flying;
    private static double flightMomentum;
    private static Vec3d flightDir = Vec3d.ZERO;

    private VaporMovement() {}

    public static boolean isFlying() {
        return flying;
    }

    public static void stop() {
        flying = false;
    }

    /** @return true if the power is holding the body up this tick (flying, wall-running or riding currents). */
    public static boolean tick(ClientPlayerEntity p, float vapor) {
        VaporConfig c = VaporConfig.get();
        if (p.getAbilities().flying || p.isFallFlying() || p.hasVehicle()
                || p.isTouchingWater() || p.isInLava() || p.isClimbing()) {
            flying = false;
            return false;
        }
        Input in = p.input;
        Vec3d vel = p.getVelocity();
        Vec3d look = p.getRotationVec(1.0f);

        // ---- Slipstream flight: density <= 20%, hold forward, and you go where you look.
        // Take off by looking up while running (or by being airborne). Look down at the ground to land,
        // release forward to coast on your momentum, or sneak to drop.
        boolean wantsFlight = vapor >= c.flightVapor && in.movementForward > 0.1f && !in.sneaking;
        // Minecraft pitch is negative when looking up.
        boolean offGround = !p.isOnGround() || p.getPitch() < (flying ? 5f : -c.takeoffPitch);
        if (wantsFlight && offGround) {
            if (!flying) {
                flying = true;
                flightMomentum = vel.length();
                flightDir = flightMomentum > 1.0E-3 ? vel.normalize() : look;
            }
            double t = MathHelper.clamp((vapor - c.flightVapor) / Math.max(0.01, 1.0 - c.flightVapor), 0.0, 1.0);
            double cruise = c.flightSpeed * (0.6 + 0.4 * t);
            // Momentum: speed builds and bleeds off gradually instead of snapping.
            flightMomentum += (cruise - flightMomentum) * (cruise > flightMomentum ? 0.04 : 0.02);
            // Turning inertia: the flight path bends toward your view rather than jumping to it.
            Vec3d turned = flightDir.lerp(look, c.flightTurnRate);
            flightDir = turned.lengthSquared() > 1.0E-4 ? turned.normalize() : look;

            p.setVelocity(flightDir.multiply(flightMomentum));
            p.fallDistance = 0f;
            return true;
        }
        flying = false;

        // ---- Wall scaling: run straight up anything you push into. Sneak to cling in place.
        if (vapor >= c.wallClimbVapor && p.horizontalCollision && in.movementForward > 0.05f) {
            double vy = in.sneaking ? 0.0 : Math.max(vel.y, 0.22 + 0.5 * vapor);
            p.setVelocity(vel.x, vy, vel.z);
            p.fallDistance = 0f;
            return true;
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
            return true;
        }
        return false;
    }
}
