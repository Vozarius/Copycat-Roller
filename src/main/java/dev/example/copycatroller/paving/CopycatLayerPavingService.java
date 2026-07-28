package dev.example.copycatroller.paving;

import java.util.List;
import java.util.function.Predicate;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.CopycatRollerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

public final class CopycatLayerPavingService {
    private static final Predicate<ItemStack> COPYCAT_LAYER_ITEM =
        stack -> stack.is(CCBlocks.COPYCAT_LAYER.asItem());

    private CopycatLayerPavingService() {
    }

    public static boolean isCopycatLayer(ItemStack stack) {
        return !stack.isEmpty() && COPYCAT_LAYER_ITEM.test(stack);
    }

    public static boolean pave(
        MovementContext context,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile
    ) {
        if (context.world.isClientSide || context.contraption == null) {
            return false;
        }

        IItemHandler inventory = context.contraption.getStorage().getAllItems();
        List<TrackSurfaceSample> samples = trackProfile == null
            ? List.of(new TrackSurfaceSample(
                fallbackPosition.getX(),
                fallbackPosition.getZ(),
                fallbackPosition.getY()
            ))
            : PreciseTrackHeightSampler.samples(trackProfile, context.localPos.getY());
        if (samples.isEmpty()) {
            return false;
        }

        int fillLevels = PavingLimits.effectiveFillLevels(
            CopycatRollerConfig.FILL_DEPTH_BLOCKS.get(),
            AllConfigs.server().kinetics.rollerFillDepth.get()
        );
        LayerMath.RoundingDirection roundingDirection =
            CopycatRollerConfig.ROUNDING_DIRECTION.get();

        for (int depth = 0; depth < fillLevels; depth++) {
            boolean completelyBlocked = true;
            boolean anyPlacement = false;

            for (TrackSurfaceSample sample : samples) {
                LayerMath.SurfaceBreakdown breakdown =
                    LayerMath.breakDown(sample.surfaceY(), roundingDirection);

                if (depth == 0 && breakdown.upperLayers() > 0) {
                    PlacementResult upperResult = tryPlace(
                        context.world,
                        new BlockPos(sample.x(), breakdown.baseY() + 1, sample.z()),
                        breakdown.upperLayers(),
                        inventory
                    );
                    completelyBlocked &= upperResult == PlacementResult.FAIL;
                    anyPlacement |= upperResult == PlacementResult.SUCCESS;
                }

                PlacementResult baseResult = tryPlace(
                    context.world,
                    new BlockPos(sample.x(), breakdown.baseY() - depth, sample.z()),
                    breakdown.baseLayers(),
                    inventory
                );
                completelyBlocked &= baseResult == PlacementResult.FAIL;
                anyPlacement |= baseResult == PlacementResult.SUCCESS;
            }

            if (anyPlacement) {
                return true;
            }
            if (completelyBlocked && depth > 0) {
                return false;
            }
        }
        return false;
    }

    public static PlacementResult tryPlace(
        Level level,
        BlockPos position,
        int targetLayers,
        IItemHandler inventory
    ) {
        if (targetLayers < 1 || targetLayers > 8) {
            throw new IllegalArgumentException("targetLayers must be in [1, 8]");
        }
        if (level.isClientSide || !level.isLoaded(position)) {
            return PlacementResult.FAIL;
        }

        BlockState oldState = level.getBlockState(position);
        BlockState targetState;
        int itemCost;

        if (oldState.is(CCBlocks.COPYCAT_LAYER.get())) {
            if (oldState.getValue(CopycatLayerBlock.FACING) != Direction.UP) {
                return PlacementResult.FAIL;
            }
            if (!(level.getBlockEntity(position) instanceof CCCopycatBlockEntity copycat)) {
                logFailure("Copycat Layer at {} has no compatible block entity", position);
                return PlacementResult.FAIL;
            }
            if (copycat.hasCustomMaterial()) {
                return PlacementResult.FAIL;
            }

            int existingLayers = oldState.getValue(CopycatLayerBlock.LAYERS);
            if (existingLayers >= targetLayers) {
                return PlacementResult.PASS;
            }

            itemCost = targetLayers - existingLayers;
            targetState = oldState
                .setValue(CopycatLayerBlock.LAYERS, targetLayers)
                .setValue(BlockStateProperties.WATERLOGGED, false);
        } else {
            if (level.getBlockEntity(position) != null) {
                return PlacementResult.FAIL;
            }
            if (oldState.is(BlockTags.PORTALS)) {
                return PlacementResult.FAIL;
            }
            if (!oldState.is(BlockTags.LEAVES)
                && !oldState.canBeReplaced()
                && !oldState.getCollisionShape(level, position).isEmpty()) {
                return PlacementResult.FAIL;
            }

            itemCost = targetLayers;
            targetState = stateFor(targetLayers);
        }

        LayerPlacement placement = new LayerPlacement(position.immutable(), targetLayers, itemCost);
        ItemStack simulated = ItemHelper.extract(
            inventory,
            COPYCAT_LAYER_ITEM,
            placement.itemCost(),
            true
        );
        if (simulated.getCount() != placement.itemCost()) {
            return PlacementResult.FAIL;
        }

        /*
         * Create's exact ItemHelper extraction performs its own complete preflight
         * before mutating the synchronous mounted handler. No other server task can
         * interleave between that preflight and its slot mutations.
         */
        ItemStack extracted = ItemHelper.extract(
            inventory,
            COPYCAT_LAYER_ITEM,
            placement.itemCost(),
            false
        );
        if (extracted.getCount() != placement.itemCost()) {
            refund(level, position, inventory, extracted);
            logFailure("Mounted storage changed during extraction at {}", position);
            return PlacementResult.FAIL;
        }

        boolean stateChanged = level.setBlockAndUpdate(position, targetState);
        if (!stateChanged
            || !level.getBlockState(position).equals(targetState)
            || !(level.getBlockEntity(position) instanceof CCCopycatBlockEntity copycat)
            || copycat.hasCustomMaterial()
            || !copycat.getConsumedItem().isEmpty()) {
            level.setBlockAndUpdate(position, oldState);
            refund(level, position, inventory, extracted);
            logFailure("Rolled back invalid Copycat Layer placement at {}", position);
            return PlacementResult.FAIL;
        }

        copycat.notifyUpdate();
        return PlacementResult.SUCCESS;
    }

    public static BlockState stateFor(int layers) {
        if (layers < 1 || layers > 8) {
            throw new IllegalArgumentException("layers must be in [1, 8]");
        }
        return CCBlocks.COPYCAT_LAYER.getDefaultState()
            .setValue(CopycatLayerBlock.FACING, Direction.UP)
            .setValue(CopycatLayerBlock.LAYERS, layers)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    private static void refund(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        ItemStack extracted
    ) {
        if (extracted.isEmpty()) {
            return;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, extracted.copy(), false);
        if (!remainder.isEmpty()) {
            Containers.dropItemStack(
                level,
                position.getX() + 0.5,
                position.getY() + 0.5,
                position.getZ() + 0.5,
                remainder
            );
            CopycatRoller.LOGGER.error(
                "Could not return {} Copycat Layer items to mounted storage; dropped them at {}",
                remainder.getCount(),
                position
            );
        }
    }

    private static void logFailure(String message, Object argument) {
        if (CopycatRollerConfig.LOG_PLACEMENT_FAILURES.get()) {
            CopycatRoller.LOGGER.warn(message, argument);
        }
    }

    public enum PlacementResult {
        FAIL,
        PASS,
        SUCCESS
    }
}
