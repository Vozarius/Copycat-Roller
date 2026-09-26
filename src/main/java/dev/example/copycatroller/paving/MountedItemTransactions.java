package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.simibubi.create.foundation.item.ItemHelper;
import dev.example.copycatroller.CopycatRoller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Small synchronous transactions for mounted item handlers.
 *
 * <p>Create's Creative Crate deliberately reports successful insertion while
 * discarding the inserted stack. Consequently an empty insertion remainder is
 * not proof that conversion change or a rollback was stored. These helpers
 * account only for item-count changes that are observable in the handler.</p>
 */
final class MountedItemTransactions {
    private MountedItemTransactions() {
    }

    static Optional<Extraction> extractExact(
        IItemHandler inventory,
        Predicate<ItemStack> predicate,
        int count,
        Consumer<ItemStack> returnRemainder
    ) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count == 0) {
            return Optional.of(Extraction.EMPTY);
        }

        ItemStack simulated = ItemHelper.extract(inventory, predicate, count, true);
        if (simulated.getCount() != count) {
            return Optional.empty();
        }

        List<ItemStack> before = snapshot(inventory);
        int extractedCount = 0;
        boolean complete = true;
        try {
            for (int slot = 0; slot < inventory.getSlots() && extractedCount < count; slot++) {
                int remaining = count - extractedCount;
                ItemStack offer = inventory.extractItem(slot, remaining, true);
                if (offer.isEmpty() || !ItemStack.isSameItemSameComponents(offer, simulated)) {
                    continue;
                }
                int requested = Math.min(remaining, offer.getCount());
                // ItemHelper's real extraction ignores this return value. A
                // successful simulation is not proof of a successful payment.
                ItemStack actual = inventory.extractItem(slot, requested, false);
                if (actual.getCount() != requested
                    || !ItemStack.isSameItemSameComponents(actual, simulated)) {
                    complete = false;
                    break;
                }
                extractedCount += actual.getCount();
            }
        } catch (RuntimeException exception) {
            complete = false;
            CopycatRoller.LOGGER.error("Mounted item extraction failed; restoring observed losses", exception);
        }
        if (!complete || extractedCount != count) {
            restoreObservedLosses(before, inventory, returnRemainder);
            return Optional.empty();
        }
        ItemStack extracted = simulated.copyWithCount(extractedCount);
        return Optional.of(new Extraction(extracted, observedLoss(before, inventory, extracted)));
    }

    static Insertion insertDurably(IItemHandler inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return Insertion.EMPTY;
        }

        ItemStack remaining = stack.copy();
        List<InsertedSlot> inserted = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
            ItemStack before = inventory.getStackInSlot(slot).copy();
            inventory.insertItem(slot, remaining.copy(), false);
            ItemStack after = inventory.getStackInSlot(slot);
            int added = observedIncrease(before, after, remaining);
            if (added <= 0) {
                continue;
            }
            inserted.add(new InsertedSlot(slot, remaining.copyWithCount(added)));
            remaining.shrink(added);
        }
        return new Insertion(List.copyOf(inserted), remaining);
    }

    private static void restoreObservedLosses(
        List<ItemStack> before,
        IItemHandler inventory,
        Consumer<ItemStack> returnRemainder
    ) {
        List<ItemStack> restored = new ArrayList<>();
        for (ItemStack stack : before) {
            if (stack.isEmpty() || restored.stream().anyMatch(
                other -> ItemStack.isSameItemSameComponents(other, stack)
            )) {
                continue;
            }
            restored.add(stack);
            // Only finite losses are refunded. Creative Crates return real
            // extracted items without reducing their visible stock.
            int lost = observedLoss(before, inventory, stack.copyWithCount(Integer.MAX_VALUE));
            if (lost > 0) {
                ItemStack remainder = insertDurably(inventory, stack.copyWithCount(lost)).remainder();
                if (!remainder.isEmpty()) returnRemainder.accept(remainder);
            }
        }
    }

    private static List<ItemStack> snapshot(IItemHandler inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            result.add(inventory.getStackInSlot(slot).copy());
        }
        return result;
    }

    private static int observedLoss(
        List<ItemStack> before,
        IItemHandler inventory,
        ItemStack extracted
    ) {
        if (extracted.isEmpty()) {
            return 0;
        }
        long beforeCount = matchingCount(before, extracted);
        long afterCount = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, extracted)) {
                afterCount += stack.getCount();
            }
        }
        return (int) Math.min(extracted.getCount(), Math.max(0, beforeCount - afterCount));
    }

    private static long matchingCount(List<ItemStack> stacks, ItemStack target) {
        long count = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int observedIncrease(
        ItemStack before,
        ItemStack after,
        ItemStack inserted
    ) {
        int beforeCount = ItemStack.isSameItemSameComponents(before, inserted)
            ? before.getCount()
            : 0;
        int afterCount = ItemStack.isSameItemSameComponents(after, inserted)
            ? after.getCount()
            : 0;
        return Math.min(inserted.getCount(), Math.max(0, afterCount - beforeCount));
    }

    record Extraction(ItemStack extracted, int finiteCount) {
        private static final Extraction EMPTY = new Extraction(ItemStack.EMPTY, 0);

        Extraction {
            extracted = extracted.copy();
            if (finiteCount < 0 || finiteCount > extracted.getCount()) {
                throw new IllegalArgumentException("invalid finite extraction count");
            }
        }

        boolean cameFromFiniteStorage() {
            return finiteCount > 0;
        }

        ItemStack rollback(IItemHandler inventory) {
            return finiteCount == 0
                ? ItemStack.EMPTY
                : insertDurably(inventory, extracted.copyWithCount(finiteCount)).remainder();
        }
    }

    record Insertion(List<InsertedSlot> inserted, ItemStack remainder) {
        private static final Insertion EMPTY = new Insertion(List.of(), ItemStack.EMPTY);

        Insertion {
            inserted = List.copyOf(inserted);
            remainder = remainder.copy();
        }

        boolean complete() {
            return remainder.isEmpty();
        }

        boolean rollback(IItemHandler inventory) {
            boolean complete = true;
            for (int index = inserted.size() - 1; index >= 0; index--) {
                InsertedSlot entry = inserted.get(index);
                ItemStack removed = inventory.extractItem(
                    entry.slot(),
                    entry.stack().getCount(),
                    false
                );
                complete &= removed.getCount() == entry.stack().getCount()
                    && ItemStack.isSameItemSameComponents(removed, entry.stack());
            }
            return complete;
        }
    }

    record InsertedSlot(int slot, ItemStack stack) {
        InsertedSlot {
            stack = stack.copy();
        }
    }
}
