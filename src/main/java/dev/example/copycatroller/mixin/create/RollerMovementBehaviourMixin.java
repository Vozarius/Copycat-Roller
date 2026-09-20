package dev.example.copycatroller.mixin.create;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.CopycatWideFillPavingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.MaterialFillPlan;
import dev.example.copycatroller.paving.CopycatPavingMaterial;
import dev.example.copycatroller.paving.RollerMaterialPlacementCapture;
import dev.example.copycatroller.paving.RollerModeGate;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RollerMovementBehaviour.class)
public abstract class RollerMovementBehaviourMixin {
    @Unique
    private static final ThreadLocal<MaterialPassState>
        copycatRoller$materialPass = new ThreadLocal<>();

    @Shadow
    @Nullable
    protected abstract PaveTask createHeightProfileForTracks(MovementContext context);

    @Shadow
    protected abstract BlockState getStateToPaveWith(MovementContext context);

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
        copycatRoller$materialPass.remove();

        ItemStack filter = ItemStack.parseOptional(
            context.world.registryAccess(),
            context.blockEntityData.getCompound("Filter")
        );
        Optional<CopycatPavingMaterial> material =
            CopycatLayerPavingService.materialFor(filter);
        boolean zincMode = CopycatLayerPavingService.isZincIngot(filter);
        if (zincMode && RollerModeGate.isWideFill(context.blockEntityData)) {
            callback.cancel();
            if (context.world.isClientSide) {
                return;
            }

            PaveTask trackProfile = createHeightProfileForTracks(context);
            boolean paved = CopycatLayerPavingService.paveWithZinc(
                context,
                position,
                trackProfile
            );
            paved |= CopycatWideFillPavingService.pave(
                context,
                position,
                trackProfile,
                this::createHeightProfileForTracks
            );
            if (paved) {
                copycatRoller$markPaved(context, position);
            }
            return;
        }

        if (!RollerModeGate.isStraightFill(context.blockEntityData)) {
            return;
        }

        if (material.isPresent() || zincMode) {
            callback.cancel();
            if (context.world.isClientSide) {
                return;
            }

            PaveTask trackProfile = createHeightProfileForTracks(context);
            boolean paved = zincMode
                ? CopycatLayerPavingService.paveWithZinc(
                    context,
                    position,
                    trackProfile
                )
                : CopycatLayerPavingService.pave(
                    context,
                    position,
                    trackProfile,
                    material.orElseThrow()
                );
            if (paved) {
                copycatRoller$markPaved(context, position);
            }
            return;
        }

        if (filter.isEmpty() || context.contraption == null) {
            return;
        }

        PaveTask trackProfile = createHeightProfileForTracks(context);
        MaterialFillPlan plan =
            CopycatMaterialFillingService.planPassForAnyFilter(
                context.world,
                position,
                trackProfile,
                context.localPos.getY()
            );
        if (plan.isEmpty()) {
            return;
        }

        boolean changed = false;
        if (!context.world.isClientSide) {
            BlockState provisionalState = getStateToPaveWith(context);
            for (CopycatMaterialFillingService.SurfaceTarget target
                : plan.targets()) {
                changed |= RollerMaterialPlacementCapture.probe(
                    this,
                    context,
                    target.copycatPosition(),
                    provisionalState,
                    context.contraption.getStorage().getAllItems()
                );
            }
        }
        copycatRoller$materialPass.set(new MaterialPassState(
            context,
            plan,
            changed
        ));
    }

    /**
     * A protected surface Copycat has already gone through its own real
     * Roller transaction at triggerPaver HEAD. The later vanilla pass must
     * treat that cell as complete without asking a third-party filter to
     * resolve the same position again.
     */
    @Inject(
        method = "tryFill(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)"
            + "Lcom/simibubi/create/content/contraptions/actors/roller/"
            + "RollerMovementBehaviour$PaveResult;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void copycatRoller$protectResolvedCopycat(
        MovementContext context,
        BlockPos targetPosition,
        BlockState toPlace,
        CallbackInfoReturnable<Object> callback
    ) {
        if (RollerMaterialPlacementCapture.shouldExposeReplaceable(
            context,
            targetPosition
        )) {
            return;
        }

        MaterialPassState pass = copycatRoller$materialPass.get();
        if (pass != null
            && pass.context() == context
            && pass.plan().protects(targetPosition)) {
            callback.setReturnValue(RollerMaterialPlacementCapture.passResult());
        }
    }

    /**
     * The second tryFill invocation is Create's full-block base attempt. When
     * that cell is occupied by the selected full Copycat, move only this
     * attempt one block down so the Roller can create its support.
     */
    @ModifyArg(
        method = "triggerPaver(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/actors/roller/"
                + "RollerMovementBehaviour;tryFill("
                + "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
                + "Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)"
                + "Lcom/simibubi/create/content/contraptions/actors/roller/"
                + "RollerMovementBehaviour$PaveResult;",
            ordinal = 1
        ),
        index = 1,
        require = 1
    )
    private BlockPos copycatRoller$redirectFullCopycatSupport(BlockPos position) {
        MaterialPassState pass = copycatRoller$materialPass.get();
        return pass == null ? position : pass.plan().redirectCreateBase(position);
    }

    /**
     * During the position-specific probe only, make the Copycat look
     * replaceable so Create and other Roller Mixins reach their normal
     * extraction and placement hooks. The actual placement is intercepted at
     * Level.setBlockAndUpdate.
     */
    @Redirect(
        method = "tryFill(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)"
            + "Lcom/simibubi/create/content/contraptions/actors/roller/"
            + "RollerMovementBehaviour$PaveResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState("
                + "Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        require = 1
    )
    private BlockState copycatRoller$protectCopycatCell(
        Level level,
        BlockPos queriedPosition,
        MovementContext context,
        BlockPos targetPosition,
        BlockState toPlace
    ) {
        if (RollerMaterialPlacementCapture.shouldExposeReplaceable(
            context,
            targetPosition
        )) {
            return Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(queriedPosition);
    }

    @Inject(
        method = "tryFill(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)"
            + "Lcom/simibubi/create/content/contraptions/actors/roller/"
            + "RollerMovementBehaviour$PaveResult;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
    )
    private void copycatRoller$reportCapturedPlacement(
        MovementContext context,
        BlockPos targetPosition,
        BlockState toPlace,
        CallbackInfoReturnable<Object> callback
    ) {
        Object original = callback.getReturnValue();
        Object adjusted = RollerMaterialPlacementCapture.adjustedTryFillResult(
            context,
            targetPosition,
            original
        );
        if (adjusted != original) {
            callback.setReturnValue(adjusted);
        }
    }

    @Inject(
        method = "triggerPaver(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;"
            + "Lnet/minecraft/core/BlockPos;)V",
        at = @At("RETURN"),
        require = 1
    )
    private void copycatRoller$finishMaterialPass(
        MovementContext context,
        BlockPos position,
        CallbackInfo callback
    ) {
        MaterialPassState pass = copycatRoller$materialPass.get();
        copycatRoller$materialPass.remove();
        if (pass != null
            && pass.context() == context
            && pass.materialChanged()
            && !context.world.isClientSide) {
            copycatRoller$markPaved(context, position);
        }
    }

    @Unique
    private static void copycatRoller$markPaved(
        MovementContext context,
        BlockPos position
    ) {
        context.data.putInt("WaitingTicks", 2);
        context.data.put("LastPos", NbtUtils.writeBlockPos(position));
        context.stall = true;
    }

    @Unique
    private record MaterialPassState(
        MovementContext context,
        MaterialFillPlan plan,
        boolean materialChanged
    ) {
    }
}
