package dev.example.copycatroller.paving;

import net.minecraft.nbt.CompoundTag;

/**
 * Create 6 keeps RollerBlockEntity.RollingMode package-private. A mixin in
 * Create's package would create a forbidden JPMS split-package, so the ordinal
 * is isolated here and guarded against the runtime enum order by a server
 * GameTest.
 */
public final class RollerModeGate {
    public static final int WIDE_FILL_ORDINAL = 2;
    public static final int STRAIGHT_FILL_ORDINAL = 1;

    private RollerModeGate() {
    }

    public static boolean isStraightFill(CompoundTag rollerBlockEntityData) {
        return rollerBlockEntityData.getInt("ScrollValue") == STRAIGHT_FILL_ORDINAL;
    }

    public static boolean isWideFill(CompoundTag rollerBlockEntityData) {
        return rollerBlockEntityData.getInt("ScrollValue") == WIDE_FILL_ORDINAL;
    }
}
