package dev.example.copycatroller.mixin.create;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.FillPassResult;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.MaterialFillPlan;
import dev.example.copycatroller.paving.CopycatPavingMaterial;
import dev.example.copycatroller.paving.RollerModeGate;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

@Mixin(RollerMovementBehaviour.class)
public abstract class RollerMovementBehaviourMixin {
    @Unique
    private static final ThreadLocal<MaterialPassState>
        copycatRoller$materialPass = new ThreadLocal<>();

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
        copycatRoller$materialPass.remove();

        ItemStack filter = ItemStack.parseOptional(
            context.world.registryAccess(),
            context.blockEntityData.getCompound("Filter")
        );
        Optional<CopycatPavingMaterial> material =
            CopycatLayerPavingService.materialFor(filter);
        boolean zincMode = CopycatLayerPavingService.isZincIngot(filter);
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

        if (!CopycatMaterialFillingService.isMaterialFilter(filter)
            || context.contraption == null) {
            return;
        }

        PaveTask trackProfile = createHeightProfileForTracks(context);
        MaterialFillPlan plan = CopycatMaterialFillingService.planPass(
            context.world,
            position,
            trackProfile,
            context.localPos.getY(),
            filter
        );
        if (plan.isEmpty()) {
            return;
        }

        FillPassResult result = CopycatMaterialFillingService.fillPlan(
            context.world,
            plan,
            filter,
            context.contraption.getStorage().getAllItems()
        );
        copycatRoller$materialPass.set(new MaterialPassState(
            context,
            plan,
            result.changed()
        ));
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
     * A partial Copycat normally occupies Create's optional slab cell. Make
     * that one cell look like the requested block to Create's unchanged
     * tryFill implementation, which returns PASS without extraction or world
     * mutation.
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
        MaterialPassState pass = copycatRoller$materialPass.get();
        if (pass != null
            && pass.context() == context
            && pass.plan().protects(targetPosition)) {
            return toPlace;
        }
        return level.getBlockState(queriedPosition);
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
