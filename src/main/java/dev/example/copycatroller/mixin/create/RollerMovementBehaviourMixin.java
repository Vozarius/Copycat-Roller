package dev.example.copycatroller.mixin.create;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.RollerModeGate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RollerMovementBehaviour.class)
public abstract class RollerMovementBehaviourMixin {
    @Shadow
    @Nullable
    protected abstract PaveTask createHeightProfileForTracks(MovementContext context);

    @Inject(
        method = "triggerPaver(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void copycatRoller$paveLayer(
        MovementContext context,
        BlockPos position,
        CallbackInfo callback
    ) {
        ItemStack filter = ItemStack.parseOptional(
            context.world.registryAccess(),
            context.blockEntityData.getCompound("Filter")
        );
        if (!CopycatLayerPavingService.isCopycatLayer(filter)
            || !RollerModeGate.isStraightFill(context.blockEntityData)) {
            return;
        }

        callback.cancel();
        if (context.world.isClientSide) {
            return;
        }

        PaveTask trackProfile = createHeightProfileForTracks(context);
        if (!CopycatLayerPavingService.pave(context, position, trackProfile)) {
            return;
        }

        context.data.putInt("WaitingTicks", 2);
        context.data.put("LastPos", NbtUtils.writeBlockPos(position));
        context.stall = true;
    }
}
