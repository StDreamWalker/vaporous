package com.vaporous.client;

import com.vaporous.VaporConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * A single wisp of vapor: vanilla's soft dust/smoke sprites, drawn translucent, slowly billowing
 * outward, rising and curling as it thins away. Thousands of these overlap into a thick but
 * see-through smoke trail.
 */
public class VaporParticle extends SpriteBillboardParticle {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final SpriteProvider sprites;
    private final float baseAlpha;
    private final float baseScale;
    private final float curlPhase;
    private final float curlSpeed;
    private final float spin;

    protected VaporParticle(ClientWorld world, double x, double y, double z,
                            double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;

        VaporConfig c = VaporConfig.get();
        int rgb = c.trailRgb();
        float shade = 0.86f + this.random.nextFloat() * 0.14f;
        this.setColor(((rgb >> 16) & 0xFF) / 255f * shade,
                ((rgb >> 8) & 0xFF) / 255f * shade,
                (rgb & 0xFF) / 255f * shade);

        this.baseAlpha = c.trailAlpha * (0.7f + this.random.nextFloat() * 0.5f);
        this.alpha = this.baseAlpha * 0.35f;
        this.baseScale = 0.12f + this.random.nextFloat() * 0.14f;
        this.scale = this.baseScale;
        this.maxAge = 22 + this.random.nextInt(22);
        this.gravityStrength = -0.12f;      // drifts gently upward
        this.velocityMultiplier = 0.88f;    // air drag
        this.collidesWithWorld = false;
        this.curlPhase = this.random.nextFloat() * TWO_PI;
        this.curlSpeed = 0.15f + this.random.nextFloat() * 0.2f;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.06f;
        this.angle = this.random.nextFloat() * TWO_PI;
        this.prevAngle = this.angle;
        this.setSpriteForAge(sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.dead) return;
        this.setSpriteForAge(this.sprites);

        // wispy curl
        float a = this.curlPhase + this.age * this.curlSpeed;
        this.velocityX += MathHelper.cos(a) * 0.0035;
        this.velocityZ += MathHelper.sin(a) * 0.0035;

        this.prevAngle = this.angle;
        this.angle += this.spin;

        float life = (float) this.age / (float) this.maxAge;
        float fadeIn = Math.min(1f, 0.35f + this.age / 3f);
        this.alpha = this.baseAlpha * (1f - life) * fadeIn;
    }

    @Override
    public float getSize(float tickDelta) {
        float life = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0f, 1f);
        return this.baseScale * (0.7f + 2.1f * life); // billows outward as it thins
    }

    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new VaporParticle(world, x, y, z, vx, vy, vz, this.sprites);
        }
    }
}
