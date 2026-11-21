package com.vaporous.mixin;

import com.vaporous.time.TimeDilation;
import net.minecraft.entity.Entity;
import net.minecraft.world.tick.TickManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks vanilla's tick-freeze machinery (the system behind /tick freeze). On "off" ticks the
 * world reports itself frozen, which vanilla already uses to pause world time, block ticks,
 * block entities, random ticks, spawning and non-player entities on both client and server.
 */
@Mixin(TickManager.class)
public abstract class TickManagerMixin {

    @Unique
    private TimeDilation.Clock vaporous$clock() {
        // The client world uses a plain TickManager; the server uses the ServerTickManager subclass.
        return ((Object) this).getClass() == TickManager.class ? TimeDilation.CLIENT : TimeDilation.SERVER;
    }

    @Inject(method = "shouldTick", at = @At("RETURN"), cancellable = true)
    private void vaporous$shouldTick(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && vaporous$clock().offTick()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldSkipTick", at = @At("HEAD"), cancellable = true)
    private void vaporous$shouldSkipTick(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (vaporous$clock().offTick()) {
            cir.setReturnValue(!TimeDilation.isExempt(entity));
        }
    }
}
