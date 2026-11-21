package com.vaporous.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.vaporous.client.ClientVaporState;
import com.vaporous.time.TimeDilation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Slowed entities only tick once every N ticks. To keep them gliding smoothly, their render
 * interpolation is stretched across those N ticks instead of snapping every N ticks.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float vaporous$stretchEntityDelta(float tickDelta, @Local(argsOnly = true) Entity entity) {
        if (!TimeDilation.CLIENT.active() || TimeDilation.isExempt(entity)) return tickDelta;
        return ClientVaporState.slowedDelta();
    }
}
