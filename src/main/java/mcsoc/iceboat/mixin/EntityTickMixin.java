package mcsoc.iceboat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;


@Mixin(Entity.class)
public class EntityTickMixin {
    @Inject(at = @At("TAIL"), method = "tick")
    protected void freezeDuringCountdown(CallbackInfo ci) {}
}
