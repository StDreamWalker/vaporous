package com.vaporous.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vaporous.client.ClientVaporState;
import com.vaporous.client.VaporParticle;
import com.vaporous.time.TimeDilation;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World particles (rain splashes, smoke, crits...) slow down with time. The speedster's own
 * vapor keeps moving at his speed, so his trail curls and fades naturally around him.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void vaporous$slowParticle(Particle particle, CallbackInfo ci) {
        if (TimeDilation.CLIENT.offTick() && !(particle instanceof VaporParticle)) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "renderParticles",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"),
            require = 0)
    private void vaporous$particleDelta(Particle particle, VertexConsumer consumer, Camera camera, float tickDelta,
                                        Operation<Void> original) {
        float delta = tickDelta;
        if (TimeDilation.CLIENT.active()) {
            delta = particle instanceof VaporParticle ? ClientVaporState.partialTick() : ClientVaporState.slowedDelta();
        }
        original.call(particle, consumer, camera, delta);
    }
}
