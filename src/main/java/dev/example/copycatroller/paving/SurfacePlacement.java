package dev.example.copycatroller.paving;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One already-quantized state in the highest cell occupied by a track surface.
 */
public record SurfacePlacement(BlockPos pos, BlockState state) {
    public SurfacePlacement {
        if (pos == null || state == null) {
            throw new IllegalArgumentException("surface placement values must not be null");
        }
    }
}
