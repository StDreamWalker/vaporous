package com.vaporous.mixin.client;

import com.vaporous.VaporConfig;
import com.vaporous.client.ClientVaporState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A 10x speed boost would otherwise yank the field of view to its maximum. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true, require = 0)
    private void vaporous$capFov(CallbackInfoReturnable<Float> cir) {
        if (!VaporConfig.get().capFov) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;
        if (!ClientVaporState.powered || !ClientVaporState.form) return;
        float cap = 1.12f + 0.18f * ClientVaporState.vapor;
        if (cir.getReturnValueF() > cap) cir.setReturnValue(cap);
    }
}
