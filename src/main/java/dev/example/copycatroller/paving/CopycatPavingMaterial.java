package dev.example.copycatroller.paving;

import java.util.Optional;
import java.util.function.Predicate;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public enum CopycatPavingMaterial {
    LAYER,
    HALF_LAYER,
    SLOPE_LAYER,
    BYTE;

    public static Optional<CopycatPavingMaterial> fromFilter(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (CopycatPavingMaterial material : values()) {
            if (material == BYTE) {
                continue;
            }
            if (material.matches(stack)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }

    public boolean matches(ItemStack stack) {
        return stack.is(itemBlock().asItem());
    }

    public Predicate<ItemStack> itemPredicate() {
        return this::matches;
    }

    public Block itemBlock() {
        return switch (this) {
            case LAYER -> CCBlocks.COPYCAT_LAYER.get();
            case HALF_LAYER -> CCBlocks.COPYCAT_HALF_LAYER.get();
            case SLOPE_LAYER -> CCBlocks.COPYCAT_SLOPE_LAYER.get();
            case BYTE -> CCBlocks.COPYCAT_BYTE.get();
        };
    }

    public boolean hasExpectedBlockEntity(ICopycatBlockEntity blockEntity) {
        return switch (this) {
            case HALF_LAYER, BYTE -> blockEntity instanceof IMultiStateCopycatBlockEntity;
            case LAYER, SLOPE_LAYER -> blockEntity instanceof CCCopycatBlockEntity;
        };
    }
}
