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
 * One wisp of the trail, drawn with vanilla's soft dust/smoke sprites.
 * <ul>
 *   <li><b>Vapor</b>: translucent, slowly billowing outward, rising and curling as it thins away.</li>
 *   <li><b>Plasma</b>: the same sprites, but self-lit at full brightness (it glows in the dark), denser
 *   and hotter: it flashes white, cools through electric cyan to a violet fringe, flickers, and burns
 *   out faster.</li>
 * </ul>
 */
public class VaporParticle extends SpriteBillboardParticle {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    /** Full block light + full sky light: ignores the world's lighting, so it glows. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final SpriteProvider sprites;
    private final boolean plasma;
    private final float baseAlpha;
    private final float baseScale;
    private final float growth;
    private final float curlPhase;
    private final float curlSpeed;
    private final float spin;
    private final float flickerPhase;

    // plasma colour ramp
    private final int coreRgb;
    private final int glowRgb;
    private final int fringeRgb;

    protected VaporParticle(ClientWorld world, double x, double y, double z,
                            double vx, double vy, double vz, SpriteProvider sprites, boolean plasma) {
        super(world, x, y, z);
        this.sprites = sprites;
        this.plasma = plasma;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;

        VaporConfig c = VaporConfig.get();
        this.coreRgb = VaporConfig.rgb(c.plasmaCoreColor, 0xFFFFFF);
        this.glowRgb = VaporConfig.rgb(c.plasmaGlowColor, 0x7FE7FF);
        this.fringeRgb = VaporConfig.rgb(c.plasmaFringeColor, 0xA45CFF);

        if (plasma) {
            this.baseAlpha = c.plasmaAlpha * (0.75f + this.random.nextFloat() * 0.4f);
            this.baseScale = 0.07f + this.random.nextFloat() * 0.1f;
            this.growth = 1.3f;
            this.maxAge = 32 + this.random.nextInt(26);  // lingers: a long glowing ribbon
            this.gravityStrength = -0.1f;     // hot, rises gently (lives longer, so slower drift)
            this.velocityMultiplier = 0.84f;
            applyRamp(0f);
        } else {
            int rgb = c.trailRgb();
            float shade = 0.86f + this.random.nextFloat() * 0.14f;
            this.setColor(((rgb >> 16) & 0xFF) / 255f * shade,
                    ((rgb >> 8) & 0xFF) / 255f * shade,
                    (rgb & 0xFF) / 255f * shade);
            this.baseAlpha = c.trailAlpha * (0.7f + this.random.nextFloat() * 0.5f);
            this.baseScale = 0.12f + this.random.nextFloat() * 0.14f;
            this.growth = 2.1f;
            this.maxAge = 22 + this.random.nextInt(22);
            this.gravityStrength = -0.12f;    // drifts gently upward
            this.velocityMultiplier = 0.88f;  // air drag
        }
        this.alpha = this.baseAlpha * 0.35f;
        this.scale = this.baseScale;
        this.collidesWithWorld = false;
        this.curlPhase = this.random.nextFloat() * TWO_PI;
        this.curlSpeed = (plasma ? 0.35f : 0.15f) + this.random.nextFloat() * 0.2f;
        this.spin = (this.random.nextFloat() - 0.5f) * (plasma ? 0.15f : 0.06f);
        this.flickerPhase = this.random.nextFloat() * TWO_PI;
        this.angle = this.random.nextFloat() * TWO_PI;
        this.prevAngle = this.angle;
        this.setSpriteForAge(sprites);
    }

    public boolean isPlasma() {
        return plasma;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return plasma ? FULL_BRIGHT : super.getBrightness(tint);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.dead) return;
        this.setSpriteForAge(this.sprites);

        // wispy curl (plasma writhes faster)
        float a = this.curlPhase + this.age * this.curlSpeed;
        double swirl = plasma ? 0.006 : 0.0035;
        this.velocityX += MathHelper.cos(a) * swirl;
        this.velocityZ += MathHelper.sin(a) * swirl;

        this.prevAngle = this.angle;
        this.angle += this.spin;

        float life = (float) this.age / (float) this.maxAge;
        float fadeIn = Math.min(1f, 0.35f + this.age / 3f);
        // Plasma holds its glow longer before fading out.
        float fade = plasma ? 1f - life * life : 1f - life;
        float alphaNow = this.baseAlpha * fade * fadeIn;
        if (plasma) {
            applyRamp(life);
            float flicker = 0.75f + 0.25f * MathHelper.sin(this.age * 1.9f + this.flickerPhase);
            alphaNow *= flicker;
        }
        this.alpha = alphaNow;
    }

    /** White-hot core -> electric glow -> violet, reached early and held for the rest of the wisp's life. */
    private void applyRamp(float life) {
        int from;
        int to;
        float t;
        if (life < 0.08f) {
            from = coreRgb;
            to = glowRgb;
            t = life / 0.08f;
        } else if (life < 0.35f) {
            from = glowRgb;
            to = fringeRgb;
            t = (life - 0.08f) / 0.27f;
        } else {
            from = fringeRgb;
            to = fringeRgb;
            t = 0f;
        }
        float r = MathHelper.lerp(t, ((from >> 16) & 0xFF) / 255f, ((to >> 16) & 0xFF) / 255f);
        float g = MathHelper.lerp(t, ((from >> 8) & 0xFF) / 255f, ((to >> 8) & 0xFF) / 255f);
        float b = MathHelper.lerp(t, (from & 0xFF) / 255f, (to & 0xFF) / 255f);
        this.setColor(r, g, b);
    }

    @Override
    public float getSize(float tickDelta) {
        float life = MathHelper.clamp((this.age + tickDelta) / this.maxAge, 0f, 1f);
        return this.baseScale * (0.7f + this.growth * life); // billows outward as it thins
    }

    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;
        private final boolean plasma;

        public Factory(SpriteProvider sprites, boolean plasma) {
            this.sprites = sprites;
            this.plasma = plasma;
        }

        public static Factory vapor(SpriteProvider sprites) {
            return new Factory(sprites, false);
        }

        public static Factory plasma(SpriteProvider sprites) {
            return new Factory(sprites, true);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new VaporParticle(world, x, y, z, vx, vy, vz, this.sprites, this.plasma);
        }
    }
}
