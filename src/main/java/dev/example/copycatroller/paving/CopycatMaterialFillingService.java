package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage;
import com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage.MaterialItem;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.CopycatRollerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Applies an ordinary Roller filter block as the material of an existing
 * Copycats+ block without replacing the Copycat shape.
 */
public final class CopycatMaterialFillingService {
    private static final double SURFACE_EPSILON = 1.0e-7;
    private static final double MAX_SURFACE_GAP = 1.0;

    private CopycatMaterialFillingService() {
    }

    public static boolean isMaterialFilter(ItemStack filter) {
        if (filter.isEmpty() || !(filter.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return !(blockItem.getBlock() instanceof ICopycatBlock);
    }

    /**
     * Resolves and fills the matching Copycat surfaces before Create starts
     * filling individual blocks. The returned plan is also used by the Mixin
     * to protect those Copycat cells while leaving every other target to
     * Create.
     */
    public static FillPassResult fillPass(
        Level level,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        int rollerLocalY,
        ItemStack filter,
        IItemHandler inventory
    ) {
        if (!isMaterialFilter(filter)) {
            return FillPassResult.NONE;
        }

        MaterialFillPlan plan = planPass(
            level,
            fallbackPosition,
            trackProfile,
            rollerLocalY,
            filter
        );
        return fillPlan(level, plan, filter, inventory);
    }

    public static MaterialFillPlan planPass(
        Level level,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        int rollerLocalY,
        ItemStack filter
    ) {
        if (!isMaterialFilter(filter)) {
            return MaterialFillPlan.EMPTY;
        }

        return planPassForAnyFilter(
            level,
            fallbackPosition,
            trackProfile,
            rollerLocalY
        );
    }

    /**
     * Builds only the geometric part of a material-fill pass. The selected
     * material is deliberately resolved later by running Create's own
     * {@code tryFill} transaction, allowing third-party Roller filters to
     * choose a different item for every position.
     */
    public static MaterialFillPlan planPassForAnyFilter(
        Level level,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        int rollerLocalY
    ) {
        List<TrackSurfaceSample> samples = trackProfile == null
            ? List.of(new TrackSurfaceSample(
                fallbackPosition.getX(),
                fallbackPosition.getZ(),
                fallbackPosition.getY()
            ))
            : PreciseTrackHeightSampler.samples(trackProfile, rollerLocalY);
        return planSamplesForAnyFilter(level, samples);
    }

    /**
     * Builds the material plan for the complete block-cell raster visited by
     * Create's Wide Fill depth loop. Unlike the straight-fill search, every
     * generated sample already represents one exact Create target, so a Byte
     * needs to be at most one block below that target.
     */
    public static MaterialFillPlan planWidePassForAnyFilter(
        Level level,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        int rollerLocalY
    ) {
        List<TrackSurfaceSample> centerline = trackProfile == null
            ? List.of(new TrackSurfaceSample(
                fallbackPosition.getX(),
                fallbackPosition.getZ(),
                fallbackPosition.getY()
            ))
            : PreciseTrackHeightSampler.samples(trackProfile, rollerLocalY);
        List<TrackSurfaceSample> targets = WideFillTargetPlanner.expand(
            centerline,
            AllConfigs.server().kinetics.rollerFillDepth.get()
        );
        return planSamplesForAnyFilter(level, targets, MAX_SURFACE_GAP);
    }

    /**
     * Finds every Copycat Byte in the real vertical work column of this
     * Roller. Create's active position is two blocks below the Roller, so the
     * cell immediately above it is still physically below the actor and must
     * be included. This scan deliberately does not apply track-surface
     * tolerances: a stepped Byte slope may sit above or below the quantized
     * rail profile while still being directly under the Roller.
     */
    public static MaterialFillPlan planBytesUnderRoller(
        Level level,
        BlockPos activePosition
    ) {
        int minimumY = activePosition.getY()
            - AllConfigs.server().kinetics.rollerFillDepth.get();
        int maximumY = activePosition.getY() + 1;
        List<SurfaceTarget> targets = new ArrayList<>();
        for (int y = maximumY; y >= minimumY; y--) {
            BlockPos position = new BlockPos(
                activePosition.getX(),
                y,
                activePosition.getZ()
            );
            if (!level.isLoaded(position)
                || !(level.getBlockState(position).getBlock()
                    instanceof CopycatByteBlock)) {
                continue;
            }
            targets.add(new SurfaceTarget(position, activePosition));
        }
        return targets.isEmpty()
            ? MaterialFillPlan.EMPTY
            : new MaterialFillPlan(targets);
    }

    /**
     * Applies one ordinary material filter to the closest Copycat surface in
     * every sampled X/Z column.
     */
    public static FillPassResult fillSamples(
        Level level,
        List<TrackSurfaceSample> samples,
        ItemStack filter,
        IItemHandler inventory
    ) {
        return fillPlan(level, planSamples(level, samples, filter), filter, inventory);
    }

    public static MaterialFillPlan planSamples(
        Level level,
        List<TrackSurfaceSample> samples,
        ItemStack filter
    ) {
        if (!isMaterialFilter(filter) || samples.isEmpty()) {
            return MaterialFillPlan.EMPTY;
        }

        return planSamplesForAnyFilter(level, samples);
    }

    public static MaterialFillPlan planSamplesForAnyFilter(
        Level level,
        List<TrackSurfaceSample> samples
    ) {
        return planSamplesForAnyFilter(
            level,
            samples,
            AllConfigs.server().kinetics.rollerFillDepth.get() + 1.0
        );
    }

    private static MaterialFillPlan planSamplesForAnyFilter(
        Level level,
        List<TrackSurfaceSample> samples,
        double maximumByteGap
    ) {
        if (samples.isEmpty()) {
            return MaterialFillPlan.EMPTY;
        }

        Map<BlockPos, SurfaceTarget> targetsByCopycat = new LinkedHashMap<>();
        samples.stream()
            .sorted(Comparator
                .comparingInt(TrackSurfaceSample::x)
                .thenComparingInt(TrackSurfaceSample::z)
                .thenComparing(
                    TrackSurfaceSample::surfaceY,
                    Comparator.reverseOrder()
                ))
            .forEach(sample -> closestSurfaceCopycat(
                level,
                sample,
                maximumByteGap
            )
                .ifPresent(copycatPosition -> targetsByCopycat.putIfAbsent(
                    copycatPosition,
                    new SurfaceTarget(
                        copycatPosition,
                        new BlockPos(
                            sample.x(),
                            Mth.floor(sample.surfaceY()),
                            sample.z()
                        )
                    )
                )));
        return targetsByCopycat.isEmpty()
            ? MaterialFillPlan.EMPTY
            : new MaterialFillPlan(List.copyOf(targetsByCopycat.values()));
    }

    public static FillPassResult fillPlan(
        Level level,
        MaterialFillPlan plan,
        ItemStack filter,
        IItemHandler inventory
    ) {
        if (plan.isEmpty() || !isMaterialFilter(filter)) {
            return FillPassResult.NONE;
        }
        if (level.isClientSide) {
            return new FillPassResult(true, false);
        }

        boolean changed = false;
        for (SurfaceTarget target : plan.targets()) {
            FillResult result = tryFill(
                level,
                target.copycatPosition(),
                filter,
                inventory
            );
            changed |= result == FillResult.SUCCESS;
        }
        return new FillPassResult(true, changed);
    }

    private static Optional<BlockPos> closestSurfaceCopycat(
        Level level,
        TrackSurfaceSample sample,
        double maximumByteGap
    ) {
        double expectedMinimumTop = sample.minimumCellSurfaceY() + 1.0;
        double expectedMaximumTop = sample.maximumCellSurfaceY() + 1.0;
        double expectedCenterTop = sample.surfaceY() + 1.0;
        int centerY = Mth.floor(expectedCenterTop);
        SurfaceCandidate best = null;

        /*
         * The precise profile describes Create's base block while surface-only
         * Copycats normally occupy the cell above it. Ordinary Copycats keep
         * the narrow surface tolerance, while slope Bytes may descend through
         * Create's configured fill depth and still belong to this column.
         */
        int fillDepth = AllConfigs.server().kinetics.rollerFillDepth.get();
        int searchDepth = Math.min(
            fillDepth + 1,
            (int) Math.ceil(maximumByteGap) + 1
        );
        for (int y = centerY - searchDepth; y <= centerY; y++) {
            BlockPos position = new BlockPos(sample.x(), y, sample.z());
            if (!level.isLoaded(position)) {
                continue;
            }
            BlockState state = level.getBlockState(position);
            if (!(state.getBlock() instanceof ICopycatBlock)) {
                continue;
            }

            Optional<Double> candidateTop = copycatTop(level, position, state);
            if (candidateTop.isEmpty()) {
                continue;
            }
            double top = candidateTop.orElseThrow();
            double maximumGap = state.getBlock() instanceof CopycatByteBlock
                ? maximumByteGap
                : MAX_SURFACE_GAP;
            if (top > expectedMaximumTop + SURFACE_EPSILON
                || top < expectedMinimumTop - maximumGap - SURFACE_EPSILON) {
                continue;
            }

            SurfaceCandidate candidate = new SurfaceCandidate(
                position,
                top,
                Math.abs(expectedCenterTop - top)
            );
            if (best == null
                || candidate.distance() < best.distance() - SURFACE_EPSILON
                || Math.abs(candidate.distance() - best.distance()) <= SURFACE_EPSILON
                    && candidate.top() > best.top()) {
                best = candidate;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.position());
    }

    private static Optional<Double> copycatTop(
        Level level,
        BlockPos position,
        BlockState state
    ) {
        VoxelShape shape = state.getCollisionShape(level, position);
        if (shape.isEmpty()) {
            shape = state.getShape(level, position);
        }
        if (shape.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(position.getY() + shape.max(Direction.Axis.Y));
    }

    public static FillResult tryFill(
        Level level,
        BlockPos position,
        ItemStack filter,
        IItemHandler inventory
    ) {
        return tryFillWithPrepaid(
            level,
            position,
            filter,
            ItemStack.EMPTY,
            inventory
        );
    }

    /**
     * Assigns a material after the normal Roller transaction has already
     * removed {@code prepaid} items. Any remaining multistate cost is
     * extracted atomically. Every early exit returns the prepaid items.
     */
    public static FillResult tryFillWithPrepaid(
        Level level,
        BlockPos position,
        ItemStack filter,
        ItemStack prepaid,
        IItemHandler inventory
    ) {
        if (level.isClientSide || !level.isLoaded(position) || !isMaterialFilter(filter)) {
            refund(level, position, inventory, prepaid);
            return FillResult.NOT_APPLICABLE;
        }

        BlockState copycatState = level.getBlockState(position);
        if (!(copycatState.getBlock() instanceof ICopycatBlock copycatBlock)) {
            refund(level, position, inventory, prepaid);
            return FillResult.NOT_APPLICABLE;
        }
        if (!(level.getBlockEntity(position) instanceof ICopycatBlockEntity copycat)) {
            refund(level, position, inventory, prepaid);
            logFailure("Copycat block at {} has no Copycats+ block entity", position);
            return FillResult.FAIL;
        }

        boolean multistateBlock = copycatBlock instanceof IMultiStateCopycatBlock;
        boolean multistateEntity = copycat instanceof IMultiStateCopycatBlockEntity;
        if (multistateBlock != multistateEntity) {
            refund(level, position, inventory, prepaid);
            logFailure("Copycat block and block entity disagree on multistate storage at {}", position);
            return FillResult.FAIL;
        }

        if (multistateBlock) {
            return fillMultistate(
                level,
                position,
                copycatState,
                (IMultiStateCopycatBlock) copycatBlock,
                (IMultiStateCopycatBlockEntity) copycat,
                filter,
                prepaid,
                inventory
            );
        }
        return fillSingle(
            level,
            position,
            copycatState,
            copycatBlock,
            copycat,
            filter,
            prepaid,
            inventory
        );
    }

    static boolean canAcceptMaterial(
        Level level,
        BlockPos position,
        ItemStack material
    ) {
        if (!level.isLoaded(position) || !isMaterialFilter(material)) {
            return false;
        }

        BlockState copycatState = level.getBlockState(position);
        if (!(copycatState.getBlock() instanceof ICopycatBlock copycatBlock)
            || !(level.getBlockEntity(position) instanceof ICopycatBlockEntity copycat)) {
            return false;
        }

        if (copycatBlock instanceof IMultiStateCopycatBlock multistateBlock
            && copycat instanceof IMultiStateCopycatBlockEntity multistateCopycat) {
            MaterialItemStorage storage = multistateCopycat.getMaterialItemStorage();
            for (String property : multistateBlock.storageProperties()) {
                if (!multistateBlock.partExists(copycatState, property)) {
                    continue;
                }
                MaterialItem current = storage.getMaterialItem(property);
                if (current == null
                    || current.hasCustomMaterial()
                    || !current.consumedItem().isEmpty()) {
                    continue;
                }
                if (multistateBlock.getAcceptedBlockState(
                    property,
                    level,
                    position,
                    material,
                    Direction.UP
                ) != null) {
                    return true;
                }
            }
            return false;
        }

        return !(copycatBlock instanceof IMultiStateCopycatBlock)
            && !(copycat instanceof IMultiStateCopycatBlockEntity)
            && !copycat.hasCustomMaterial()
            && copycat.getConsumedItem().isEmpty()
            && copycatBlock.getAcceptedBlockState(
                level,
                position,
                material,
                Direction.UP
            ) != null;
    }

    private static FillResult fillSingle(
        Level level,
        BlockPos position,
        BlockState copycatState,
        ICopycatBlock copycatBlock,
        ICopycatBlockEntity copycat,
        ItemStack filter,
        ItemStack prepaid,
        IItemHandler inventory
    ) {
        if (copycat.hasCustomMaterial() || !copycat.getConsumedItem().isEmpty()) {
            refund(level, position, inventory, prepaid);
            return FillResult.PASS;
        }

        BlockState acceptedMaterial = copycatBlock.getAcceptedBlockState(
            level,
            position,
            filter,
            Direction.UP
        );
        if (acceptedMaterial == null) {
            refund(level, position, inventory, prepaid);
            return FillResult.PASS;
        }

        Optional<ItemStack> payment = acquirePayment(
            level,
            position,
            inventory,
            filter,
            prepaid,
            1
        );
        if (payment.isEmpty()) {
            return FillResult.FAIL;
        }

        SingleSnapshot snapshot = new SingleSnapshot(
            copycat.getMaterial(),
            copycat.getConsumedItem().copy(),
            copycat.isCTEnabled()
        );
        ItemStack consumedItem = payment.orElseThrow().copyWithCount(1);

        try {
            copycat.setMaterial(acceptedMaterial);
            copycat.setConsumedItem(consumedItem);
            if (!singleAssignmentIsValid(
                level,
                position,
                copycatState,
                copycat,
                acceptedMaterial,
                filter
            )) {
                restoreSingle(copycat, snapshot);
                refund(level, position, inventory, payment.orElseThrow());
                logFailure("Rolled back an invalid Copycat material assignment at {}", position);
                return FillResult.FAIL;
            }
        } catch (RuntimeException exception) {
            restoreSingle(copycat, snapshot);
            refund(level, position, inventory, payment.orElseThrow());
            CopycatRoller.LOGGER.error(
                "Rolled back a failed Copycat material assignment at {}",
                position,
                exception
            );
            return FillResult.FAIL;
        }

        copycat.notifyUpdate();
        return FillResult.SUCCESS;
    }

    private static FillResult fillMultistate(
        Level level,
        BlockPos position,
        BlockState copycatState,
        IMultiStateCopycatBlock copycatBlock,
        IMultiStateCopycatBlockEntity copycat,
        ItemStack filter,
        ItemStack prepaid,
        IItemHandler inventory
    ) {
        MaterialItemStorage storage = copycat.getMaterialItemStorage();
        List<PartAssignment> assignments = new ArrayList<>();

        for (String property : copycatBlock.storageProperties().stream().sorted().toList()) {
            if (!copycatBlock.partExists(copycatState, property)) {
                continue;
            }
            MaterialItem current = storage.getMaterialItem(property);
            if (current == null) {
                refund(level, position, inventory, prepaid);
                logFailure("Copycat material property {} is missing at {}", property, position);
                return FillResult.FAIL;
            }
            if (current.hasCustomMaterial() || !current.consumedItem().isEmpty()) {
                continue;
            }

            BlockState acceptedMaterial = copycatBlock.getAcceptedBlockState(
                property,
                level,
                position,
                filter,
                Direction.UP
            );
            if (acceptedMaterial != null) {
                assignments.add(new PartAssignment(
                    property,
                    acceptedMaterial,
                    snapshot(current)
                ));
            }
        }

        if (assignments.isEmpty()) {
            refund(level, position, inventory, prepaid);
            return FillResult.PASS;
        }

        Optional<ItemStack> payment = acquirePayment(
            level,
            position,
            inventory,
            filter,
            prepaid,
            assignments.size()
        );
        if (payment.isEmpty()) {
            return FillResult.FAIL;
        }

        ItemStack consumedItem = payment.orElseThrow().copyWithCount(1);
        try {
            for (PartAssignment assignment : assignments) {
                copycat.setMaterial(assignment.property(), assignment.material());
                copycat.setConsumedItem(assignment.property(), consumedItem);
            }
            if (!multistateAssignmentIsValid(
                level,
                position,
                copycatState,
                copycat,
                assignments,
                filter
            )) {
                restoreMultistate(copycat, assignments);
                refund(level, position, inventory, payment.orElseThrow());
                logFailure("Rolled back an invalid multistate material assignment at {}", position);
                return FillResult.FAIL;
            }
        } catch (RuntimeException exception) {
            restoreMultistate(copycat, assignments);
            refund(level, position, inventory, payment.orElseThrow());
            CopycatRoller.LOGGER.error(
                "Rolled back a failed multistate material assignment at {}",
                position,
                exception
            );
            return FillResult.FAIL;
        }

        copycat.notifyUpdate();
        return FillResult.SUCCESS;
    }

    private static boolean singleAssignmentIsValid(
        Level level,
        BlockPos position,
        BlockState copycatState,
        ICopycatBlockEntity copycat,
        BlockState acceptedMaterial,
        ItemStack filter
    ) {
        return level.getBlockState(position).equals(copycatState)
            && level.getBlockEntity(position) == copycat
            && copycat.hasCustomMaterial()
            && copycat.getMaterial().is(acceptedMaterial.getBlock())
            && isStoredFilter(copycat.getConsumedItem(), filter);
    }

    private static boolean multistateAssignmentIsValid(
        Level level,
        BlockPos position,
        BlockState copycatState,
        IMultiStateCopycatBlockEntity copycat,
        List<PartAssignment> assignments,
        ItemStack filter
    ) {
        if (!level.getBlockState(position).equals(copycatState)
            || level.getBlockEntity(position) != copycat) {
            return false;
        }

        MaterialItemStorage storage = copycat.getMaterialItemStorage();
        for (PartAssignment assignment : assignments) {
            MaterialItem stored = storage.getMaterialItem(assignment.property());
            if (stored == null
                || !stored.hasCustomMaterial()
                || !stored.material().is(assignment.material().getBlock())
                || !isStoredFilter(stored.consumedItem(), filter)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStoredFilter(ItemStack stored, ItemStack filter) {
        return stored.getCount() == 1
            && ItemStack.isSameItemSameComponents(stored, filter);
    }

    private static Optional<ItemStack> acquirePayment(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        ItemStack filter,
        ItemStack prepaid,
        int count
    ) {
        if (!prepaid.isEmpty()
            && !ItemStack.isSameItemSameComponents(prepaid, filter)) {
            refund(level, position, inventory, prepaid);
            logFailure("Roller prepaid a different material item at {}", position);
            return Optional.empty();
        }

        int prepaidCount = prepaid.getCount();
        int remaining = Math.max(0, count - prepaidCount);
        if (remaining == 0) {
            if (prepaidCount > count) {
                refund(
                    level,
                    position,
                    inventory,
                    prepaid.copyWithCount(prepaidCount - count)
                );
            }
            return Optional.of(filter.copyWithCount(count));
        }

        ItemStack simulated = ItemHelper.extract(
            inventory,
            stack -> ItemStack.isSameItemSameComponents(stack, filter),
            remaining,
            true
        );
        if (simulated.getCount() != remaining) {
            refund(level, position, inventory, prepaid);
            return Optional.empty();
        }

        ItemStack extracted = ItemHelper.extract(
            inventory,
            stack -> ItemStack.isSameItemSameComponents(stack, filter),
            remaining,
            false
        );
        if (extracted.getCount() != remaining) {
            refund(level, position, inventory, extracted);
            refund(level, position, inventory, prepaid);
            logFailure("Mounted storage changed during material extraction at {}", position);
            return Optional.empty();
        }
        return Optional.of(filter.copyWithCount(count));
    }

    private static SingleSnapshot snapshot(MaterialItem materialItem) {
        return new SingleSnapshot(
            materialItem.material(),
            materialItem.consumedItem().copy(),
            materialItem.enableCT()
        );
    }

    private static void restoreSingle(
        ICopycatBlockEntity copycat,
        SingleSnapshot snapshot
    ) {
        copycat.setMaterialInternal(snapshot.material());
        copycat.setConsumedItemInternal(snapshot.consumedItem().copy());
        copycat.setCTEnabledInternal(snapshot.enableCT());
        copycat.notifyUpdate();
    }

    private static void restoreMultistate(
        IMultiStateCopycatBlockEntity copycat,
        List<PartAssignment> assignments
    ) {
        MaterialItemStorage storage = copycat.getMaterialItemStorage();
        for (PartAssignment assignment : assignments) {
            SingleSnapshot snapshot = assignment.snapshot();
            storage.storeMaterialItem(
                assignment.property(),
                new MaterialItem(
                    snapshot.material(),
                    snapshot.consumedItem().copy(),
                    snapshot.enableCT()
                )
            );
        }
        copycat.notifyUpdate();
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
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
            inventory,
            extracted.copy(),
            false
        );
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
            "Could not return {} Copycat material items to mounted storage; dropped them at {}",
            remainder.getCount(),
            position
        );
    }

    static void refundObservedExtraction(
        Level level,
        BlockPos position,
        IItemHandler inventory,
        ItemStack extracted
    ) {
        refund(level, position, inventory, extracted);
    }

    private static void logFailure(String message, Object... arguments) {
        if (CopycatRollerConfig.LOG_PLACEMENT_FAILURES.get()) {
            CopycatRoller.LOGGER.warn(message, arguments);
        }
    }

    public enum FillResult {
        NOT_APPLICABLE,
        FAIL,
        PASS,
        SUCCESS
    }

    public record FillPassResult(boolean foundSurfaceCopycat, boolean changed) {
        public static final FillPassResult NONE = new FillPassResult(false, false);
    }

    /**
     * One Copycat selected for an X/Z profile column and the full-block base
     * position that Create would normally try first in that column.
     */
    public record SurfaceTarget(
        BlockPos copycatPosition,
        BlockPos createBasePosition
    ) {
        public SurfaceTarget {
            copycatPosition = copycatPosition.immutable();
            createBasePosition = createBasePosition.immutable();
        }

        public Optional<BlockPos> supportRedirect() {
            return copycatPosition.getY() <= createBasePosition.getY()
                ? Optional.of(copycatPosition.below())
                : Optional.empty();
        }
    }

    public static final class MaterialFillPlan {
        public static final MaterialFillPlan EMPTY = new MaterialFillPlan(
            List.of(),
            Set.of()
        );
        private final List<SurfaceTarget> targets;
        private final Set<BlockPos> protectedPositions;
        private final Map<BlockPos, BlockPos> baseRedirects;

        public MaterialFillPlan(List<SurfaceTarget> targets) {
            this(targets, Set.of());
        }

        private MaterialFillPlan(
            List<SurfaceTarget> targets,
            Collection<BlockPos> additionallyProtected
        ) {
            this.targets = List.copyOf(targets);

            Set<BlockPos> protectedPositions = new LinkedHashSet<>(
                additionallyProtected
            );
            Map<BlockPos, BlockPos> baseRedirects = new LinkedHashMap<>();
            for (SurfaceTarget target : this.targets) {
                BlockPos copycat = target.copycatPosition();
                BlockPos base = target.createBasePosition();
                for (int y = copycat.getY(); y <= base.getY() + 1; y++) {
                    protectedPositions.add(new BlockPos(
                        copycat.getX(),
                        y,
                        copycat.getZ()
                    ));
                }
                target.supportRedirect().ifPresent(redirect ->
                    baseRedirects.merge(
                        target.createBasePosition(),
                        redirect,
                        (first, second) -> first.getY() <= second.getY()
                            ? first
                            : second
                    )
                );
            }
            this.protectedPositions = Set.copyOf(protectedPositions);
            this.baseRedirects = Map.copyOf(baseRedirects);
        }

        public List<SurfaceTarget> targets() {
            return targets;
        }

        public boolean isEmpty() {
            return targets.isEmpty() && protectedPositions.isEmpty();
        }

        public MaterialFillPlan protecting(Collection<BlockPos> positions) {
            if (positions.isEmpty()) {
                return this;
            }
            Set<BlockPos> combined = new LinkedHashSet<>(protectedPositions);
            combined.addAll(positions);
            return new MaterialFillPlan(targets, combined);
        }

        public static MaterialFillPlan combine(MaterialFillPlan... plans) {
            List<SurfaceTarget> targets = new ArrayList<>();
            Set<BlockPos> protectedPositions = new LinkedHashSet<>();
            Arrays.stream(plans).forEach(plan -> {
                targets.addAll(plan.targets);
                protectedPositions.addAll(plan.protectedPositions);
            });
            if (targets.isEmpty() && protectedPositions.isEmpty()) {
                return EMPTY;
            }
            Map<BlockPos, SurfaceTarget> uniqueTargets = new LinkedHashMap<>();
            for (SurfaceTarget target : targets) {
                uniqueTargets.putIfAbsent(target.copycatPosition(), target);
            }
            return new MaterialFillPlan(
                List.copyOf(uniqueTargets.values()),
                protectedPositions
            );
        }

        public boolean protects(BlockPos position) {
            return protectedPositions.contains(position);
        }

        /**
         * A selected Copycat at or below Create's base target owns the whole
         * paving column, so the base attempt is redirected directly beneath
         * it. A partial Copycat in the upper cell leaves the base unchanged.
         */
        public BlockPos redirectCreateBase(BlockPos position) {
            return baseRedirects.getOrDefault(position, position);
        }
    }
    private record SingleSnapshot(
        BlockState material,
        ItemStack consumedItem,
        boolean enableCT
    ) {
    }

    private record PartAssignment(
        String property,
        BlockState material,
        SingleSnapshot snapshot
    ) {
    }

    private record SurfaceCandidate(
        BlockPos position,
        double top,
        double distance
    ) {
    }
}
