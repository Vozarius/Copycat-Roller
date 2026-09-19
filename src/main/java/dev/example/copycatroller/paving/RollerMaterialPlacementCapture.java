package dev.example.copycatroller.paving;

import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.example.copycatroller.CopycatRoller;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Runs Create's real Roller placement transaction against a protected
 * Copycat. The world write is intercepted only while this thread-local probe
 * is active, after third-party Mixins have selected and extracted their
 * position-specific material.
 */
public final class RollerMaterialPlacementCapture {
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private RollerMaterialPlacementCapture() {
    }

    public static boolean probe(
        Object rollerBehaviour,
        MovementContext context,
        BlockPos position,
        BlockState provisionalState,
        IItemHandler inventory
    ) {
        if (context.world.isClientSide
            || provisionalState == null
            || provisionalState.isAir()
            || !context.world.isLoaded(position)) {
            return false;
        }
        if (ACTIVE.get() != null) {
            CopycatRoller.LOGGER.error(
                "Skipped a nested Roller material probe at {}",
                position
            );
            return false;
        }

        Capture capture = new Capture(
            context,
            position.immutable(),
            inventory,
            InventorySnapshot.capture(inventory)
        );
        ACTIVE.set(capture);
        try {
            Bridge.TRY_FILL.invoke(
                rollerBehaviour,
                context,
                position,
                provisionalState
            );
        } catch (Throwable throwable) {
            CopycatRoller.LOGGER.error(
                "Third-party Roller material selection failed at {}",
                position,
                throwable
            );
        } finally {
            ACTIVE.remove();
            capture.refundUnresolvedExtraction();
        }
        return capture.outcome == Outcome.SUCCESS;
    }

    public static boolean shouldExposeReplaceable(
        MovementContext context,
        BlockPos position
    ) {
        Capture capture = ACTIVE.get();
        return capture != null
            && capture.context == context
            && capture.position.equals(position)
            && !capture.resolved;
    }

    /**
     * Called from the Level Mixin at the last common point before Create or a
     * third-party Roller Mixin replaces the Copycat block.
     */
    public static Optional<Boolean> interceptPlacement(
        Level level,
        BlockPos position,
        BlockState plannedState
    ) {
        Capture capture = ACTIVE.get();
        if (capture == null
            || capture.context.world != level
            || !capture.position.equals(position)
            || capture.resolved) {
            return Optional.empty();
        }

        capture.resolved = true;
        List<ItemStack> deductions =
            capture.inventoryBefore.deductions(capture.inventory);
        Optional<ItemStack> selected = selectMaterial(
            level,
            position,
            plannedState,
            deductions
        ).or(() -> selectUndeductedMaterial(
            capture.inventory,
            level,
            position,
            plannedState
        ));
        if (selected.isEmpty()) {
            for (ItemStack deduction : deductions) {
                CopycatMaterialFillingService.refundObservedExtraction(
                    level,
                    position,
                    capture.inventory,
                    deduction
                );
            }
            capture.outcome = Outcome.FAIL;
            return Optional.of(false);
        }

        ItemStack material = selected.orElseThrow();
        try {
            CopycatMaterialFillingService.FillResult result =
                CopycatMaterialFillingService.tryFillWithPrepaid(
                    level,
                    position,
                    material.copyWithCount(1),
                    material,
                    capture.inventory
                );
            capture.outcome = switch (result) {
                case SUCCESS -> Outcome.SUCCESS;
                case PASS -> Outcome.PASS;
                case FAIL, NOT_APPLICABLE -> Outcome.FAIL;
            };
            return Optional.of(
                result == CopycatMaterialFillingService.FillResult.SUCCESS
            );
        } catch (RuntimeException exception) {
            for (ItemStack deduction
                : capture.inventoryBefore.deductions(capture.inventory)) {
                CopycatMaterialFillingService.refundObservedExtraction(
                    level,
                    position,
                    capture.inventory,
                    deduction
                );
            }
            capture.outcome = Outcome.FAIL;
            CopycatRoller.LOGGER.error(
                "Rolled back a failed Roller material capture at {}",
                position,
                exception
            );
            return Optional.of(false);
        }
    }

    /**
     * Replaces Create's nominal SUCCESS when the intercepted placement was
     * intentionally skipped or could not be fully funded.
     */
    public static Object adjustedTryFillResult(
        MovementContext context,
        BlockPos position,
        Object original
    ) {
        Capture capture = ACTIVE.get();
        if (capture == null
            || capture.context != context
            || !capture.position.equals(position)
            || capture.outcome == null) {
            return original;
        }
        return capture.outcome == Outcome.PASS ? Bridge.PASS
            : capture.outcome == Outcome.FAIL ? Bridge.FAIL
            : original;
    }

    public static Object passResult() {
        return Bridge.PASS;
    }

    private static Optional<ItemStack> selectMaterial(
        Level level,
        BlockPos position,
        BlockState plannedState,
        List<ItemStack> deductions
    ) {
        List<ItemStack> blockItems = deductions.stream()
            .filter(stack -> stack.getItem() instanceof BlockItem)
            .toList();
        if (blockItems.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> directMatches = blockItems.stream()
            .filter(stack -> Block.byItem(stack.getItem()) == plannedState.getBlock())
            .toList();
        if (directMatches.size() == 1) {
            return Optional.of(directMatches.getFirst());
        }

        List<ItemStack> accepted = blockItems.stream()
            .filter(stack -> CopycatMaterialFillingService.canAcceptMaterial(
                level,
                position,
                stack
            ))
            .toList();
        if (accepted.size() == 1) {
            return Optional.of(accepted.getFirst());
        }
        if (blockItems.size() == 1) {
            return Optional.of(blockItems.getFirst());
        }
        return Optional.empty();
    }

    /**
     * Bottomless mounted inventories, including Create's Creative Crate, do
     * not change when Create extracts its selected block. In that case the
     * planned state is the authoritative result of Create's own filter logic.
     */
    private static Optional<ItemStack> selectUndeductedMaterial(
        IItemHandler inventory,
        Level level,
        BlockPos position,
        BlockState plannedState
    ) {
        ItemStack selected = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack candidate = inventory.getStackInSlot(slot);
            if (!(candidate.getItem() instanceof BlockItem)
                || Block.byItem(candidate.getItem()) != plannedState.getBlock()
                || !CopycatMaterialFillingService.canAcceptMaterial(
                    level,
                    position,
                    candidate
                )) {
                continue;
            }
            if (selected.isEmpty()) {
                selected = candidate.copyWithCount(1);
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(selected, candidate)) {
                return Optional.empty();
            }
        }
        return selected.isEmpty()
            ? Optional.empty()
            : Optional.of(selected);
    }
    private static Class<?> findPaveResultType() {
        try {
            return Class.forName(
                "com.simibubi.create.content.contraptions.actors.roller."
                    + "RollerMovementBehaviour$PaveResult",
                false,
                RollerMovementBehaviour.class.getClassLoader()
            );
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static MethodHandle findTryFill() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RollerMovementBehaviour.class,
                MethodHandles.lookup()
            );
            return lookup.findVirtual(
                RollerMovementBehaviour.class,
                "tryFill",
                MethodType.methodType(
                    Bridge.PAVE_RESULT_TYPE,
                    MovementContext.class,
                    BlockPos.class,
                    BlockState.class
                )
            );
        } catch (IllegalAccessException | NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Object findPaveResult(String name) {
        Object[] constants = Bridge.PAVE_RESULT_TYPE.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
        }
        throw new ExceptionInInitializerError(
            "Create Roller PaveResult." + name + " is unavailable"
        );
    }

    private static final class Bridge {
        private static final Class<?> PAVE_RESULT_TYPE = findPaveResultType();
        private static final MethodHandle TRY_FILL = findTryFill();
        private static final Object PASS = findPaveResult("PASS");
        private static final Object FAIL = findPaveResult("FAIL");

        private Bridge() {
        }
    }

    private enum Outcome {
        SUCCESS,
        PASS,
        FAIL
    }

    private static final class Capture {
        private final MovementContext context;
        private final BlockPos position;
        private final IItemHandler inventory;
        private final InventorySnapshot inventoryBefore;
        private boolean resolved;
        private Outcome outcome;

        private Capture(
            MovementContext context,
            BlockPos position,
            IItemHandler inventory,
            InventorySnapshot inventoryBefore
        ) {
            this.context = context;
            this.position = position;
            this.inventory = inventory;
            this.inventoryBefore = inventoryBefore;
        }

        private void refundUnresolvedExtraction() {
            if (resolved) {
                return;
            }
            for (ItemStack deduction : inventoryBefore.deductions(inventory)) {
                CopycatMaterialFillingService.refundObservedExtraction(
                    context.world,
                    position,
                    inventory,
                    deduction
                );
            }
        }
    }

    static final class InventorySnapshot {
        private final List<ItemStack> totals;

        private InventorySnapshot(List<ItemStack> totals) {
            this.totals = totals;
        }

        static InventorySnapshot capture(IItemHandler inventory) {
            List<ItemStack> totals = new ArrayList<>();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                add(totals, inventory.getStackInSlot(slot), 1);
            }
            return new InventorySnapshot(List.copyOf(totals));
        }

        List<ItemStack> deductions(IItemHandler inventory) {
            List<ItemStack> remaining = new ArrayList<>();
            for (ItemStack total : totals) {
                remaining.add(total.copy());
            }
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                add(remaining, inventory.getStackInSlot(slot), -1);
            }
            return remaining.stream()
                .filter(stack -> !stack.isEmpty() && stack.getCount() > 0)
                .map(ItemStack::copy)
                .toList();
        }

        private static void add(
            List<ItemStack> totals,
            ItemStack stack,
            int multiplier
        ) {
            if (stack.isEmpty()) {
                return;
            }
            int delta = stack.getCount() * multiplier;
            for (int index = 0; index < totals.size(); index++) {
                ItemStack existing = totals.get(index);
                if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                    continue;
                }
                existing.setCount(existing.getCount() + delta);
                return;
            }
            if (delta > 0) {
                totals.add(stack.copyWithCount(delta));
            }
        }
    }
}
