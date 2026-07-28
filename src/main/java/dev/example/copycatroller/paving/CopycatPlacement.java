package dev.example.copycatroller.paving;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A planned state together with the exact Copycats+ item family it belongs to.
 */
public record CopycatPlacement(
    BlockPos pos,
    CopycatPavingMaterial material,
    BlockState state
) {
    public CopycatPlacement {
        if (pos == null || material == null || state == null) {
            throw new IllegalArgumentException("copycat placement values must not be null");
        }
        if (!state.is(material.itemBlock())) {
            throw new IllegalArgumentException("state does not match paving material");
        }
    }
}
