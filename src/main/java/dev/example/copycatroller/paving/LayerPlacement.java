package dev.example.copycatroller.paving;

import net.minecraft.core.BlockPos;

public record LayerPlacement(BlockPos pos, int targetLayers, int itemCost) {
    public LayerPlacement {
        if (targetLayers < 1 || targetLayers > 8) {
            throw new IllegalArgumentException("targetLayers must be in [1, 8]");
        }
        if (itemCost < 0 || itemCost > targetLayers) {
            throw new IllegalArgumentException("itemCost must be in [0, targetLayers]");
        }
    }
}
