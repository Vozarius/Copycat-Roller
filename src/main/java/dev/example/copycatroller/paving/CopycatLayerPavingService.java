package dev.example.copycatroller.paving;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
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
import org.jetbrains.annotations.Nullable;

/**
 * Runtime paving for the three Copycats+ horizontal layer families supported
 * by Mechanical Roller.
 */
public final class CopycatLayerPavingService {
    private static final double SLOPE_EPSILON = 1.0e-7;

    private CopycatLayerPavingService() {
    }

    public static boolean isCopycatLayer(ItemStack stack) {
        return CopycatPavingMaterial.fromFilter(stack).isPresent();
    }

    public static boolean isZincIngot(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AllItems.ZINC_INGOT.get());
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

        if (preciseTrackProfile && CopycatRollerConfig.SURFACE_ONLY.get()) {
            boolean anyPlacement = false;
            double slopeMaxVerticalError =
                CopycatRollerConfig.SLOPE_MAX_VERTICAL_ERROR.get();
            for (TrackSurfaceSample sample : samples) {
                Optional<SurfacePlacement> planned = surfacePlacementFor(
                    material,
                    sample,
                    roundingDirection,
                    slopeMaxVerticalError
                );
                if (planned.isEmpty()) {
                    continue;
                }
                SurfacePlacement placement = planned.orElseThrow();
                PlacementResult result = tryPlace(
                    context.world,
                    placement.pos(),
                    material,
                    placement.state(),
                    inventory
                );
                anyPlacement |= result == PlacementResult.SUCCESS;
            }
            return anyPlacement;
        }

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
     * Automatic zinc mode. Equal half heights collapse to an ordinary Layer;
     * unequal heights remain a Half Layer.
     */
    public static boolean paveWithZinc(
        MovementContext context,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile
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

        LayerMath.RoundingDirection roundingDirection =
            CopycatRollerConfig.ROUNDING_DIRECTION.get();
        if (preciseTrackProfile && CopycatRollerConfig.SURFACE_ONLY.get()) {
            boolean anyPlacement = false;
            for (TrackSurfaceSample sample : samples) {
                Optional<CopycatPlacement> planned = zincSurfacePlacementFor(
                    sample,
                    roundingDirection
                );
                if (planned.isEmpty()) {
                    continue;
                }
                CopycatPlacement placement = planned.orElseThrow();
                PlacementResult result = tryPlaceWithZinc(
                    context.world,
                    placement.pos(),
                    placement.material(),
                    placement.state(),
                    inventory
                );
                anyPlacement |= result == PlacementResult.SUCCESS;
            }
            return anyPlacement;
        }

        int fillLevels = PavingLimits.effectiveFillLevels(
            CopycatRollerConfig.FILL_DEPTH_BLOCKS.get(),
            AllConfigs.server().kinetics.rollerFillDepth.get()
        );
        for (int depth = 0; depth < fillLevels; depth++) {
            boolean completelyBlocked = true;
            boolean anyPlacement = false;
            for (TrackSurfaceSample sample : samples) {
                int baseY = preciseTrackProfile
                    ? LayerMath.baseY(sample.surfaceY())
                    : fallbackPosition.getY();

                if (preciseTrackProfile && depth == 0) {
                    Optional<CopycatPlacement> upper = zincLegacyUpperPlacementFor(
                        sample,
                        roundingDirection
                    );
                    if (upper.isPresent()) {
                        CopycatPlacement placement = upper.orElseThrow();
                        PlacementResult result = tryPlaceWithZinc(
                            context.world,
                            placement.pos(),
                            placement.material(),
                            placement.state(),
                            inventory
                        );
                        completelyBlocked &= result == PlacementResult.FAIL;
                        anyPlacement |= result == PlacementResult.SUCCESS;
                    }
                }

                PlacementResult baseResult = tryPlaceWithZinc(
                    context.world,
                    new BlockPos(sample.x(), baseY - depth, sample.z()),
                    CopycatPavingMaterial.LAYER,
                    stateFor(8),
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
     * Plans only the highest occupied cell of a precise track column.
     *
     * <p>The Create profile denotes the base full block, so the physical top
     * surface is {@code profileY + 1}. The planner moves the target cell across
     * integer boundaries instead of unconditionally creating a full base
     * block. A level integer surface therefore produces no placement.</p>
     */
    public static Optional<SurfacePlacement> surfacePlacementFor(
        CopycatPavingMaterial material,
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection,
        double slopeMaxVerticalError
    ) {
        return switch (material) {
            case LAYER -> planSimpleSurface(sample, roundingDirection);
            case HALF_LAYER -> planHalfSurface(sample, roundingDirection);
            case SLOPE_LAYER -> planSlopeSurface(
                sample,
                roundingDirection,
                slopeMaxVerticalError
            );
            case BYTE -> throw new IllegalArgumentException(
                "Copycat Byte is reserved for zinc Wide Fill"
            );
        };
    }

    public static Optional<CopycatPlacement> zincSurfacePlacementFor(
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection
    ) {
        return planHalfSurface(sample, roundingDirection)
            .map(CopycatLayerPavingService::collapseEqualHalfLayers);
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
        return tryPlaceInternal(
            level,
            position,
            material,
            requestedState,
            inventory,
            PaymentSource.DIRECT
        );
    }

    public static PlacementResult tryPlaceWithZinc(
        Level level,
        BlockPos position,
        CopycatPavingMaterial material,
        BlockState requestedState,
        IItemHandler inventory
    ) {
        if (material == CopycatPavingMaterial.SLOPE_LAYER) {
            throw new IllegalArgumentException(
                "automatic zinc paving supports Layer, Half Layer, and internal Byte placement"
            );
        }
        return tryPlaceInternal(
            level,
            position,
            material,
            requestedState,
            inventory,
            PaymentSource.ZINC
        );
    }

    private static PlacementResult tryPlaceInternal(
        Level level,
        BlockPos position,
        CopycatPavingMaterial material,
        BlockState requestedState,
        IItemHandler inventory,
        PaymentSource paymentSource
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
            Optional<BlockState> merged = mergeExisting(material, oldState, requestedState);
            if (merged.isEmpty()) {
                return PlacementResult.FAIL;
            }
            targetState = merged.orElseThrow();
            oldUnits = itemUnits(material, oldState);
            if (targetState.equals(oldState)) {
                return PlacementResult.PASS;
            }
            // A filled Byte may be resumed later after resources are replenished.
            // Copycats+ stores every bite independently, so adding an empty bite
            // preserves the already filled parts. Single-state shapes cannot be
            // enlarged safely after their one material assignment.
            if (!isEmptyCopycat(copycat)
                && material != CopycatPavingMaterial.BYTE) {
                return PlacementResult.FAIL;
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

        Optional<PaymentReceipt> payment = paymentSource == PaymentSource.DIRECT
            ? payDirect(level, position, inventory, material, itemCost)
            : payWithZinc(level, position, inventory, material, itemCost);
        if (payment.isEmpty()) {
            return PlacementResult.FAIL;
        }

        boolean extendingFilledByte = material == CopycatPavingMaterial.BYTE
            && oldState.is(material.itemBlock())
            && level.getBlockEntity(position) instanceof ICopycatBlockEntity oldCopycat
            && !isEmptyCopycat(oldCopycat);
        try {
            boolean stateChanged = level.setBlockAndUpdate(position, targetState);
            if (!stateChanged
                || !level.getBlockState(position).equals(targetState)
                || !(level.getBlockEntity(position) instanceof ICopycatBlockEntity copycat)
                || !material.hasExpectedBlockEntity(copycat)
                || (!extendingFilledByte && !isEmptyCopycat(copycat))) {
                level.setBlockAndUpdate(position, oldState);
                payment.orElseThrow().rollback();
                logFailure("Rolled back invalid {} placement at {}", material, position);
                return PlacementResult.FAIL;
            }

            copycat.notifyUpdate();
            return PlacementResult.SUCCESS;
        } catch (RuntimeException exception) {
            try {
                level.setBlockAndUpdate(position, oldState);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            payment.orElseThrow().rollback();
            CopycatRoller.LOGGER.error(
                "Rolled back failed {} placement at {}",
                material,
                position,
                exception
            );
            return PlacementResult.FAIL;
        }
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
                    sample.minimumHalfSurfaceY(false) - baseY,
                    roundingDirection
                );
                int positiveLayers = LayerMath.layersForHeight(
                    sample.minimumHalfSurfaceY(true) - baseY,
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
            case BYTE -> throw new IllegalArgumentException(
                "Copycat Byte has no track-column upper state"
            );
        };
    }

    private static Optional<SurfacePlacement> planSimpleSurface(
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection
    ) {
        double topY = sample.minimumCellSurfaceY() + 1;
        int cellY = LayerMath.baseY(topY);
        int layers = LayerMath.layersForHeight(topY - cellY, roundingDirection);
        if (layers == 0) {
            return Optional.empty();
        }
        return Optional.of(new SurfacePlacement(
            new BlockPos(sample.x(), cellY, sample.z()),
            stateFor(layers)
        ));
    }

    private static Optional<SurfacePlacement> planHalfSurface(
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection
    ) {
        double negativeTopY = sample.minimumHalfSurfaceY(false) + 1;
        double positiveTopY = sample.minimumHalfSurfaceY(true) + 1;
        int cellY = LayerMath.baseY(Math.min(negativeTopY, positiveTopY));
        int negativeLayers = safeFlatLayers(
            negativeTopY - cellY,
            roundingDirection
        );
        int positiveLayers = safeFlatLayers(
            positiveTopY - cellY,
            roundingDirection
        );
        if (negativeLayers == 0 && positiveLayers == 0) {
            return Optional.empty();
        }
        return Optional.of(new SurfacePlacement(
            new BlockPos(sample.x(), cellY, sample.z()),
            halfLayerStateFor(
                sample.longitudinalAxis(),
                negativeLayers,
                positiveLayers
            )
        ));
    }

    private static Optional<SurfacePlacement> planSlopeSurface(
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection,
        double slopeMaxVerticalError
    ) {
        if (Math.abs(sample.gradientAlongAxis()) < SLOPE_EPSILON) {
            return Optional.empty();
        }
        double lowTopY = sample.lowSlopeEdgeSurfaceY() + 1;
        double highTopY = sample.highSlopeEdgeSurfaceY() + 1;
        int cellY = LayerMath.baseY(Math.min(lowTopY, highTopY));
        double desiredLow = lowTopY - cellY;
        double desiredHigh = highTopY - cellY;
        int maximumLayers = LayerMath.layersForHeight(
            (desiredLow + desiredHigh) * 0.5,
            roundingDirection
        );
        var selected = SlopeLayerGeometry.selectBestState(
            desiredLow,
            desiredHigh,
            maximumLayers,
            slopeMaxVerticalError
        );
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SurfacePlacement(
            new BlockPos(sample.x(), cellY, sample.z()),
            slopeLayerStateFor(sample.uphillDirection(), selected.getAsInt())
        ));
    }

    private static Optional<CopycatPlacement> zincLegacyUpperPlacementFor(
        TrackSurfaceSample sample,
        LayerMath.RoundingDirection roundingDirection
    ) {
        int baseY = LayerMath.baseY(sample.surfaceY());
        return upperStateFor(
            CopycatPavingMaterial.HALF_LAYER,
            sample,
            roundingDirection
        ).map(state -> collapseEqualHalfLayers(new SurfacePlacement(
            new BlockPos(sample.x(), baseY + 1, sample.z()),
            state
        )));
    }

    private static CopycatPlacement collapseEqualHalfLayers(
        SurfacePlacement halfPlacement
    ) {
        BlockState halfState = halfPlacement.state();
        int negativeLayers =
            halfState.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS);
        int positiveLayers =
            halfState.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS);
        if (negativeLayers == positiveLayers) {
            return new CopycatPlacement(
                halfPlacement.pos(),
                CopycatPavingMaterial.LAYER,
                stateFor(negativeLayers)
            );
        }
        return new CopycatPlacement(
            halfPlacement.pos(),
            CopycatPavingMaterial.HALF_LAYER,
            halfState
        );
    }

    /**
     * UP may request a state above a conservative footprint sample. The safety
     * cap keeps rectangular states below that sample; DOWN retains its strict
     * previous-eighth semantics.
     */
    private static int safeFlatLayers(
        double height,
        LayerMath.RoundingDirection roundingDirection
    ) {
        int requested = LayerMath.layersForHeight(height, roundingDirection);
        int safeMaximum = (int) Math.floor(
            height * 8.0 + LayerMath.BOUNDARY_EPSILON
        );
        safeMaximum = Math.max(0, Math.min(8, safeMaximum));
        return Math.min(requested, safeMaximum);
    }

    public static BlockState fullStateFor(
        CopycatPavingMaterial material,
        TrackSurfaceSample sample
    ) {
        return switch (material) {
            case LAYER -> stateFor(8);
            case HALF_LAYER -> halfLayerStateFor(sample.longitudinalAxis(), 8, 8);
            case SLOPE_LAYER -> slopeLayerStateFor(sample.uphillDirection(), 8);
            case BYTE -> throw new IllegalArgumentException(
                "Copycat Byte has no full track-column state"
            );
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

    public static BlockState byteStateFor(
        Collection<CopycatByteBlock.Byte> bytes
    ) {
        if (bytes.isEmpty()) {
            throw new IllegalArgumentException("at least one Copycat Byte must be present");
        }
        BlockState state = CCBlocks.COPYCAT_BYTE.getDefaultState();
        for (CopycatByteBlock.Byte bite : CopycatByteBlock.allBytes) {
            state = state.setValue(
                CopycatByteBlock.byByte(bite),
                bytes.contains(bite)
            );
        }
        return state.setValue(BlockStateProperties.WATERLOGGED, false);
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
            case BYTE -> {
                BlockState merged = existing;
                for (CopycatByteBlock.Byte bite : CopycatByteBlock.allBytes) {
                    merged = merged.setValue(
                        CopycatByteBlock.byByte(bite),
                        existing.getValue(CopycatByteBlock.byByte(bite))
                            || requested.getValue(CopycatByteBlock.byByte(bite))
                    );
                }
                yield Optional.of(merged.setValue(BlockStateProperties.WATERLOGGED, false));
            }
        };
    }

    private static Optional<PaymentReceipt> payDirect(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        CopycatPavingMaterial material,
        int itemCost
    ) {
        Optional<MountedItemTransactions.Extraction> extraction =
            MountedItemTransactions.extractExact(
                inventory,
                material.itemPredicate(),
                itemCost
            );
        if (extraction.isEmpty()) {
            return Optional.empty();
        }
        MountedItemTransactions.Extraction paid = extraction.orElseThrow();
        return Optional.of(() -> refundExtraction(
            level,
            position,
            inventory,
            paid,
            material.toString()
        ));
    }

    private static Optional<PaymentReceipt> payWithZinc(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        CopycatPavingMaterial material,
        int itemUnits
    ) {
        if (material == CopycatPavingMaterial.BYTE) {
            return payBytesWithZinc(level, position, inventory, itemUnits);
        }
        int requiredCredits = switch (material) {
            case LAYER -> ZincCreditMath.creditsForLayers(itemUnits);
            case HALF_LAYER -> itemUnits;
            case SLOPE_LAYER -> throw new IllegalArgumentException(
                "Slope Layer cannot be paid from automatic zinc mode"
            );
            case BYTE -> throw new AssertionError("handled above");
        };
        int availableHalfLayers = countMatching(
            inventory,
            CopycatPavingMaterial.HALF_LAYER,
            requiredCredits
        );
        ZincCreditMath.PaymentPlan plan =
            ZincCreditMath.plan(requiredCredits, availableHalfLayers);

        Optional<MountedItemTransactions.Extraction> halfLayerExtraction =
            MountedItemTransactions.extractExact(
                inventory,
                CopycatPavingMaterial.HALF_LAYER.itemPredicate(),
                plan.halfLayersToConsume()
            );
        if (halfLayerExtraction.isEmpty()) {
            return Optional.empty();
        }
        MountedItemTransactions.Extraction extractedHalfLayers =
            halfLayerExtraction.orElseThrow();

        Optional<MountedItemTransactions.Extraction> zincExtraction =
            MountedItemTransactions.extractExact(
                inventory,
                CopycatLayerPavingService::isZincIngot,
                plan.zincIngotsToConsume()
            );
        if (zincExtraction.isEmpty()) {
            refundExtraction(
                level,
                position,
                inventory,
                extractedHalfLayers,
                "zinc paving half-layer credits"
            );
            return Optional.empty();
        }
        MountedItemTransactions.Extraction extractedZinc =
            zincExtraction.orElseThrow();

        // Infinite sources do not lose an ingot and therefore need no stored
        // conversion remainder. Finite zinc must preserve every unused item.
        ItemStack change = extractedZinc.cameFromFiniteStorage()
            ? halfLayerStack(plan.halfLayerChange())
            : ItemStack.EMPTY;
        MountedItemTransactions.Insertion storedChange =
            MountedItemTransactions.insertDurably(inventory, change);
        if (!storedChange.complete()) {
            if (!storedChange.rollback(inventory)) {
                logFailure("Could not roll back half-layer change at {}", position);
            }
            refundExtraction(
                level,
                position,
                inventory,
                extractedHalfLayers,
                "zinc paving half-layer credits"
            );
            refundExtraction(
                level,
                position,
                inventory,
                extractedZinc,
                "zinc paving ingots"
            );
            logFailure("Could not store zinc conversion remainder at {}", position);
            return Optional.empty();
        }

        return Optional.of(() -> {
            if (!storedChange.rollback(inventory)) {
                logFailure("Could not remove rolled-back half-layer change at {}", position);
            }
            refundExtraction(
                level,
                position,
                inventory,
                extractedHalfLayers,
                "zinc paving half-layer credits"
            );
            refundExtraction(
                level,
                position,
                inventory,
                extractedZinc,
                "zinc paving ingots"
            );
        });
    }

    private static Optional<PaymentReceipt> payBytesWithZinc(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        int requiredBytes
    ) {
        int availableBytes = countMatching(
            inventory,
            CopycatPavingMaterial.BYTE,
            requiredBytes
        );
        ZincByteMath.PaymentPlan plan =
            ZincByteMath.plan(requiredBytes, availableBytes);

        Optional<MountedItemTransactions.Extraction> byteExtraction =
            MountedItemTransactions.extractExact(
                inventory,
                CopycatPavingMaterial.BYTE.itemPredicate(),
                plan.bytesToConsume()
            );
        if (byteExtraction.isEmpty()) {
            return Optional.empty();
        }
        MountedItemTransactions.Extraction extractedBytes =
            byteExtraction.orElseThrow();

        Optional<MountedItemTransactions.Extraction> zincExtraction =
            MountedItemTransactions.extractExact(
                inventory,
                CopycatLayerPavingService::isZincIngot,
                plan.zincIngotsToConsume()
            );
        if (zincExtraction.isEmpty()) {
            refundExtraction(
                level,
                position,
                inventory,
                extractedBytes,
                "zinc paving Byte items"
            );
            return Optional.empty();
        }
        MountedItemTransactions.Extraction extractedZinc =
            zincExtraction.orElseThrow();

        ItemStack change = extractedZinc.cameFromFiniteStorage()
            ? byteStack(plan.byteChange())
            : ItemStack.EMPTY;
        MountedItemTransactions.Insertion storedChange =
            MountedItemTransactions.insertDurably(inventory, change);
        if (!storedChange.complete()) {
            if (!storedChange.rollback(inventory)) {
                logFailure("Could not roll back Copycat Byte change at {}", position);
            }
            refundExtraction(level, position, inventory, extractedBytes, "zinc paving Byte items");
            refundExtraction(level, position, inventory, extractedZinc, "zinc paving ingots");
            logFailure("Could not store Copycat Byte conversion remainder at {}", position);
            return Optional.empty();
        }

        return Optional.of(() -> {
            if (!storedChange.rollback(inventory)) {
                logFailure("Could not remove rolled-back Copycat Byte change at {}", position);
            }
            refundExtraction(level, position, inventory, extractedBytes, "zinc paving Byte items");
            refundExtraction(level, position, inventory, extractedZinc, "zinc paving ingots");
        });
    }

    private static int countMatching(
        IItemHandler inventory,
        CopycatPavingMaterial material,
        int limit
    ) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots() && count < limit; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (material.matches(stack)) {
                count += Math.min(stack.getCount(), limit - count);
            }
        }
        return count;
    }

    private static ItemStack halfLayerStack(int count) {
        return count == 0
            ? ItemStack.EMPTY
            : new ItemStack(
                CopycatPavingMaterial.HALF_LAYER.itemBlock().asItem(),
                count
            );
    }

    private static ItemStack byteStack(int count) {
        return count == 0
            ? ItemStack.EMPTY
            : new ItemStack(
                CopycatPavingMaterial.BYTE.itemBlock().asItem(),
                count
            );
    }

    private static int itemUnits(
        CopycatPavingMaterial material,
        BlockState state
    ) {
        return switch (material) {
            case LAYER -> state.getValue(CopycatLayerBlock.LAYERS);
            case HALF_LAYER ->
                state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS)
                    + state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS);
            case SLOPE_LAYER -> state.getValue(CopycatSlopeLayerBlock.LAYERS);
            case BYTE -> {
                int count = 0;
                for (CopycatByteBlock.Byte bite : CopycatByteBlock.allBytes) {
                    count += state.getValue(CopycatByteBlock.byByte(bite)) ? 1 : 0;
                }
                yield count;
            }
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

    private static void refundExtraction(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        MountedItemTransactions.Extraction extraction,
        String description
    ) {
        ItemStack remainder = extraction.rollback(inventory);
        if (remainder.isEmpty()) {
            return;
        }
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
            description,
            position
        );
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

    private enum PaymentSource {
        DIRECT,
        ZINC
    }

    @FunctionalInterface
    private interface PaymentReceipt {
        void rollback();
    }
}
