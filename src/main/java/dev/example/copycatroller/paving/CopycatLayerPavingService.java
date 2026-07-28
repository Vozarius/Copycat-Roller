package dev.example.copycatroller.paving;

import java.util.List;
import java.util.Optional;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
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
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime paving for the three Copycats+ horizontal layer families supported
 * by Mechanical Roller.
 */
public final class CopycatLayerPavingService {
    private CopycatLayerPavingService() {
    }

    public static boolean isCopycatLayer(ItemStack stack) {
        return CopycatPavingMaterial.fromFilter(stack).isPresent();
    }

    public static Optional<CopycatPavingMaterial> materialFor(ItemStack stack) {
        return CopycatPavingMaterial.fromFilter(stack);
    }

    public static boolean pave(
        MovementContext context,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        CopycatPavingMaterial material
    ) {
        if (context.world.isClientSide || context.contraption == null) {
            return false;
        }

        IItemHandler inventory = context.contraption.getStorage().getAllItems();
        boolean preciseTrackProfile = trackProfile != null;
        List<TrackSurfaceSample> samples = !preciseTrackProfile
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
                int baseY = preciseTrackProfile
                    ? LayerMath.baseY(sample.surfaceY())
                    : fallbackPosition.getY();

                if (preciseTrackProfile && depth == 0) {
                    Optional<BlockState> upperState =
                        upperStateFor(material, sample, roundingDirection);
                    if (upperState.isPresent()) {
                        PlacementResult upperResult = tryPlace(
                            context.world,
                            new BlockPos(sample.x(), baseY + 1, sample.z()),
                            material,
                            upperState.orElseThrow(),
                            inventory
                        );
                        completelyBlocked &= upperResult == PlacementResult.FAIL;
                        anyPlacement |= upperResult == PlacementResult.SUCCESS;
                    }
                }

                BlockState fullState = fullStateFor(material, sample);
                PlacementResult baseResult = tryPlace(
                    context.world,
                    new BlockPos(sample.x(), baseY - depth, sample.z()),
                    material,
                    fullState,
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

    /**
     * Backwards-compatible entry point used by the original Copycat Layer
     * tests and by integrations that only need the simple layer.
     */
    public static PlacementResult tryPlace(
        Level level,
        BlockPos position,
        int targetLayers,
        IItemHandler inventory
    ) {
        return tryPlace(
            level,
            position,
            CopycatPavingMaterial.LAYER,
            stateFor(targetLayers),
            inventory
        );
    }

    public static PlacementResult tryPlace(
        Level level,
        BlockPos position,
        CopycatPavingMaterial material,
        BlockState requestedState,
        IItemHandler inventory
    ) {
        requireTargetState(material, requestedState);
        if (level.isClientSide || !level.isLoaded(position)) {
            return PlacementResult.FAIL;
        }

        BlockState oldState = level.getBlockState(position);
        BlockState targetState = requestedState;
        int oldUnits = 0;

        if (oldState.is(material.itemBlock())) {
            if (!(level.getBlockEntity(position) instanceof ICopycatBlockEntity copycat)
                || !material.hasExpectedBlockEntity(copycat)) {
                logFailure("{} at {} has no compatible Copycats+ block entity", material, position);
                return PlacementResult.FAIL;
            }
            if (!isEmptyCopycat(copycat)) {
                return PlacementResult.FAIL;
            }

            Optional<BlockState> merged = mergeExisting(material, oldState, requestedState);
            if (merged.isEmpty()) {
                return PlacementResult.FAIL;
            }
            targetState = merged.orElseThrow();
            oldUnits = itemUnits(material, oldState);
            if (targetState.equals(oldState)) {
                return PlacementResult.PASS;
            }
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
        }

        int itemCost = itemUnits(material, targetState) - oldUnits;
        if (itemCost <= 0) {
            return PlacementResult.PASS;
        }

        ItemStack simulated = ItemHelper.extract(
            inventory,
            material.itemPredicate(),
            itemCost,
            true
        );
        if (simulated.getCount() != itemCost) {
            return PlacementResult.FAIL;
        }

        /*
         * Create's exact ItemHelper extraction performs its own complete
         * preflight before mutating the synchronous mounted handler. No other
         * server task can interleave between that preflight and its slot
         * mutations.
         */
        ItemStack extracted = ItemHelper.extract(
            inventory,
            material.itemPredicate(),
            itemCost,
            false
        );
        if (extracted.getCount() != itemCost) {
            refund(level, position, inventory, extracted, material);
            logFailure("Mounted storage changed during extraction at {}", position);
            return PlacementResult.FAIL;
        }

        boolean stateChanged = level.setBlockAndUpdate(position, targetState);
        if (!stateChanged
            || !level.getBlockState(position).equals(targetState)
            || !(level.getBlockEntity(position) instanceof ICopycatBlockEntity copycat)
            || !material.hasExpectedBlockEntity(copycat)
            || !isEmptyCopycat(copycat)) {
            level.setBlockAndUpdate(position, oldState);
            refund(level, position, inventory, extracted, material);
            logFailure("Rolled back invalid {} placement at {}", material, position);
            return PlacementResult.FAIL;
        }

        copycat.notifyUpdate();
        return PlacementResult.SUCCESS;
    }

    public static Optional<BlockState> upperStateFor(
        CopycatPavingMaterial material,
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection
    ) {
        int baseY = LayerMath.baseY(sample.surfaceY());
        return switch (material) {
            case LAYER -> {
                int layers = LayerMath.layersAboveBase(sample.surfaceY(), roundingDirection);
                yield layers == 0 ? Optional.empty() : Optional.of(stateFor(layers));
            }
            case HALF_LAYER -> {
                int negativeLayers = LayerMath.layersForHeight(
                    sample.negativeHalfSurfaceY() - baseY,
                    roundingDirection
                );
                int positiveLayers = LayerMath.layersForHeight(
                    sample.positiveHalfSurfaceY() - baseY,
                    roundingDirection
                );
                yield negativeLayers == 0 && positiveLayers == 0
                    ? Optional.empty()
                    : Optional.of(halfLayerStateFor(
                        sample.longitudinalAxis(),
                        negativeLayers,
                        positiveLayers
                    ));
            }
            case SLOPE_LAYER -> {
                int layers = LayerMath.layersAboveBase(sample.surfaceY(), roundingDirection);
                yield layers == 0
                    ? Optional.empty()
                    : Optional.of(slopeLayerStateFor(sample.uphillDirection(), layers));
            }
        };
    }

    public static BlockState fullStateFor(
        CopycatPavingMaterial material,
        TrackSurfaceSample sample
    ) {
        return switch (material) {
            case LAYER -> stateFor(8);
            case HALF_LAYER -> halfLayerStateFor(sample.longitudinalAxis(), 8, 8);
            case SLOPE_LAYER -> slopeLayerStateFor(sample.uphillDirection(), 8);
        };
    }

    public static BlockState stateFor(int layers) {
        requireLayers(layers);
        return CCBlocks.COPYCAT_LAYER.getDefaultState()
            .setValue(CopycatLayerBlock.FACING, Direction.UP)
            .setValue(CopycatLayerBlock.LAYERS, layers)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    public static BlockState halfLayerStateFor(
        Direction.Axis axis,
        int negativeLayers,
        int positiveLayers
    ) {
        if (axis == Direction.Axis.Y) {
            throw new IllegalArgumentException("Copycat Half Layer axis must be horizontal");
        }
        requireHalfLayers(negativeLayers);
        requireHalfLayers(positiveLayers);
        if (negativeLayers == 0 && positiveLayers == 0) {
            throw new IllegalArgumentException("at least one half layer must be present");
        }
        return CCBlocks.COPYCAT_HALF_LAYER.getDefaultState()
            .setValue(CopycatHalfLayerBlock.AXIS, axis)
            .setValue(CopycatHalfLayerBlock.HALF, Half.BOTTOM)
            .setValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS, negativeLayers)
            .setValue(CopycatHalfLayerBlock.POSITIVE_LAYERS, positiveLayers)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    public static BlockState slopeLayerStateFor(Direction facing, int layers) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Copycat Slope Layer facing must be horizontal");
        }
        requireLayers(layers);
        return CCBlocks.COPYCAT_SLOPE_LAYER.getDefaultState()
            .setValue(CopycatSlopeLayerBlock.FACING, facing)
            .setValue(CopycatSlopeLayerBlock.HALF, Half.BOTTOM)
            .setValue(CopycatSlopeLayerBlock.LAYERS, layers)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    }

    private static Optional<BlockState> mergeExisting(
        CopycatPavingMaterial material,
        BlockState existing,
        BlockState requested
    ) {
        return switch (material) {
            case LAYER -> {
                if (existing.getValue(CopycatLayerBlock.FACING) != Direction.UP) {
                    yield Optional.empty();
                }
                int layers = Math.max(
                    existing.getValue(CopycatLayerBlock.LAYERS),
                    requested.getValue(CopycatLayerBlock.LAYERS)
                );
                yield Optional.of(existing
                    .setValue(CopycatLayerBlock.LAYERS, layers)
                    .setValue(BlockStateProperties.WATERLOGGED, false));
            }
            case HALF_LAYER -> {
                if (existing.getValue(CopycatHalfLayerBlock.HALF) != Half.BOTTOM
                    || existing.getValue(CopycatHalfLayerBlock.AXIS)
                    != requested.getValue(CopycatHalfLayerBlock.AXIS)) {
                    yield Optional.empty();
                }
                int negativeLayers = Math.max(
                    existing.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS),
                    requested.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS)
                );
                int positiveLayers = Math.max(
                    existing.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS),
                    requested.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS)
                );
                yield Optional.of(existing
                    .setValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS, negativeLayers)
                    .setValue(CopycatHalfLayerBlock.POSITIVE_LAYERS, positiveLayers)
                    .setValue(BlockStateProperties.WATERLOGGED, false));
            }
            case SLOPE_LAYER -> {
                if (existing.getValue(CopycatSlopeLayerBlock.HALF) != Half.BOTTOM
                    || existing.getValue(CopycatSlopeLayerBlock.FACING)
                    != requested.getValue(CopycatSlopeLayerBlock.FACING)) {
                    yield Optional.empty();
                }
                int layers = Math.max(
                    existing.getValue(CopycatSlopeLayerBlock.LAYERS),
                    requested.getValue(CopycatSlopeLayerBlock.LAYERS)
                );
                yield Optional.of(existing
                    .setValue(CopycatSlopeLayerBlock.LAYERS, layers)
                    .setValue(BlockStateProperties.WATERLOGGED, false));
            }
        };
    }

    private static int itemUnits(CopycatPavingMaterial material, BlockState state) {
        return switch (material) {
            case LAYER -> state.getValue(CopycatLayerBlock.LAYERS);
            case HALF_LAYER ->
                state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS)
                    + state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS);
            case SLOPE_LAYER -> state.getValue(CopycatSlopeLayerBlock.LAYERS);
        };
    }

    private static boolean isEmptyCopycat(ICopycatBlockEntity copycat) {
        if (copycat.hasCustomMaterial()) {
            return false;
        }
        if (copycat instanceof IMultiStateCopycatBlockEntity multiState) {
            return multiState.getMaterialItemStorage().getAllConsumedItems().isEmpty();
        }
        return copycat.getConsumedItem().isEmpty();
    }

    private static void requireTargetState(
        CopycatPavingMaterial material,
        BlockState targetState
    ) {
        if (!targetState.is(material.itemBlock())) {
            throw new IllegalArgumentException("target state does not match " + material);
        }
        if (itemUnits(material, targetState) < 1) {
            throw new IllegalArgumentException("target state contains no layer items");
        }
    }

    private static void requireLayers(int layers) {
        if (layers < 1 || layers > 8) {
            throw new IllegalArgumentException("layers must be in [1, 8]");
        }
    }

    private static void requireHalfLayers(int layers) {
        if (layers < 0 || layers > 8) {
            throw new IllegalArgumentException("half layers must be in [0, 8]");
        }
    }

    private static void refund(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        ItemStack extracted,
        CopycatPavingMaterial material
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
                "Could not return {} {} items to mounted storage; dropped them at {}",
                remainder.getCount(),
                material,
                position
            );
        }
    }

    private static void logFailure(String message, Object... arguments) {
        if (CopycatRollerConfig.LOG_PLACEMENT_FAILURES.get()) {
            CopycatRoller.LOGGER.warn(message, arguments);
        }
    }

    public enum PlacementResult {
        FAIL,
        PASS,
        SUCCESS
    }
}
