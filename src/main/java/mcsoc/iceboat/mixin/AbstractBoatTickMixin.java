package mcsoc.iceboat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import mcsoc.iceboat.datatrackers.LevelData;


@Mixin(AbstractBoat.class)
public class AbstractBoatTickMixin extends EntityTickMixin {
    @Override
    protected void freezeDuringCountdown(CallbackInfo ci) {
        var this_boat = (AbstractBoat)(Object)this;
        if (!(this_boat.level() instanceof ServerLevel level)) return;

        Boolean gameRunning = !LevelData.getRaceData(level).getActiveMapKey().isEmpty();

        if (!gameRunning) {
            this_boat.setDeltaMovement(this_boat.getDeltaMovement().multiply(0, 1, 0));
            this_boat.teleportTo(this_boat.xo, this_boat.yo, this_boat.zo);
        }
    }
}
