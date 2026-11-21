package com.vaporous.mixin.client;

import com.vaporous.VaporConfig;
import com.vaporous.time.TimeDilation;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sounds drop in pitch while the world is slowed, like hearing it from outside time. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    @Inject(method = "getAdjustedPitch", at = @At("RETURN"), cancellable = true, require = 0)
    private void vaporous$slowPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!TimeDilation.CLIENT.active()) return;
        float mul = VaporConfig.get().slowSoundPitch;
        if (mul > 0f && mul < 1f) {
            cir.setReturnValue(cir.getReturnValueF() * mul);
        }
    }
}
