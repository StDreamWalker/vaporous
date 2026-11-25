package com.vaporous.client;

import com.vaporous.VaporConfig;
import com.vaporous.VaporRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import com.vaporous.power.Variant;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * "Emits a wispy vapor from his body as he runs instead of electricity or a blur."
 * Each tick, wisps are seeded along the whole path the body covered since the last tick, so even
 * at 60+ blocks a second the trail stays one thick, continuous ribbon of smoke rather than dotted puffs.
 */
public final class VaporTrail {
    private VaporTrail() {}

    static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) return;
        VaporConfig c = VaporConfig.get();
        if (c.trailDensity <= 0f) return;

        for (AbstractClientPlayerEntity pl : world.getPlayers()) {
            boolean self = pl == mc.player;
            boolean form;
            float vapor;
            int variant;
            if (self) {
                form = ClientVaporState.powered && ClientVaporState.form;
                vapor = ClientVaporState.vapor;
                variant = ClientVaporState.variant;
            } else {
                ClientVaporState.Remote r = ClientVaporState.remote(pl.getId());
                if (r == null) continue;
                form = r.form();
                vapor = r.vapor();
                variant = r.variant();
            }
            if (!form || pl.isSpectator() || pl.isInvisible()) continue;
            emit(mc, world, pl, self, vapor, variant == Variant.PLASMA, c);
        }
    }

    private static void emit(MinecraftClient mc, ClientWorld world, AbstractClientPlayerEntity pl,
                             boolean self, float vapor, boolean plasma, VaporConfig c) {
        SimpleParticleType type = plasma ? VaporRegistry.PLASMA : VaporRegistry.VAPOR;
        Vec3d cur = pl.getPos();
        Vec3d prev = new Vec3d(pl.prevX, pl.prevY, pl.prevZ);
        Vec3d motion = cur.subtract(prev);
        double dist = motion.length();
        if (dist > 12.0) return; // teleported, not ran

        double speedFactor = MathHelper.clamp(dist * 4.0, 0.0, 6.0);
        // Plasma burns out faster, so it's laid down denser to keep a solid, glowing ribbon.
        double density = c.trailDensity * (plasma ? 1.5 : 1.0);
        int count = (int) Math.round(density * (1.2 + speedFactor * 5.0 + vapor * 6.0));
        count = Math.min(count, plasma ? 100 : 70);

        Vec3d dir = dist > 1.0E-4 ? motion.multiply(1.0 / dist) : Vec3d.ZERO;
        boolean firstPerson = self && mc.options.getPerspective().isFirstPerson();
        float w = pl.getWidth();
        float h = pl.getHeight();
        Random r = world.random;

        for (int i = 0; i < count; i++) {
            Vec3d base = prev.lerp(cur, r.nextDouble());
            if (firstPerson) {
                // keep the wisps out of your own camera
                base = base.subtract(dir.multiply(0.9 + r.nextDouble() * 0.8));
            }
            // Bias wisps toward the torso and legs so the body reads as "smoking".
            double ox = (r.nextDouble() - 0.5) * w * 1.15;
            double oz = (r.nextDouble() - 0.5) * w * 1.15;
            double oy = Math.pow(r.nextDouble(), 1.3) * h * 0.92;
            if (firstPerson && oy > h * 0.7) oy *= 0.6;

            double vx = -dir.x * 0.03 * speedFactor + (r.nextDouble() - 0.5) * 0.02;
            double vy = 0.004 + r.nextDouble() * 0.014;
            double vz = -dir.z * 0.03 * speedFactor + (r.nextDouble() - 0.5) * 0.02;
            world.addParticle(type, base.x + ox, base.y + oy, base.z + oz, vx, vy, vz);
        }
        // Plasma crackles: a few stray sparks thrown off the body.
        if (plasma && r.nextFloat() < 0.25f + 0.5f * vapor) {
            Vec3d at = prev.lerp(cur, r.nextDouble());
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    at.x + (r.nextDouble() - 0.5) * w * 1.4, at.y + r.nextDouble() * h, at.z + (r.nextDouble() - 0.5) * w * 1.4,
                    (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
        }
    }
}
