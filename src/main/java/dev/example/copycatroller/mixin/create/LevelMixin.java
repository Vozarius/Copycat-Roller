package dev.example.copycatroller.mixin.create;

import dev.example.copycatroller.paving.RollerMaterialPlacementCapture;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
        method = "setBlockAndUpdate("
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void copycatRoller$captureRollerMaterial(
        BlockPos position,
        BlockState state,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Optional<Boolean> result =
            RollerMaterialPlacementCapture.interceptPlacement(
                (Level) (Object) this,
                position,
                state
            );
        result.ifPresent(callback::setReturnValue);
    }
}
