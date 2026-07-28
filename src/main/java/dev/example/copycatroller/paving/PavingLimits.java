package dev.example.copycatroller.paving;

public final class PavingLimits {
    private PavingLimits() {
    }

    public static int effectiveFillLevels(int configuredFillLevels, int createRollerFillDepth) {
        if (configuredFillLevels < 1) {
            throw new IllegalArgumentException("configuredFillLevels must be at least 1");
        }
        if (createRollerFillDepth < 0) {
            throw new IllegalArgumentException("createRollerFillDepth must not be negative");
        }
        return Math.min(configuredFillLevels, createRollerFillDepth + 1);
    }
}
