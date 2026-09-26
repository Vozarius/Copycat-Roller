package dev.example.copycatroller.paving;

public final class PavingLimits {
    /**
     * Create accepts arbitrarily large configuration values, but the add-on's
     * two-dimensional rasterizers run synchronously on the server thread.
     * Thirty-two levels retain practical Wide Fill ranges without permitting
     * pathological allocations or multi-second ticks.
     */
    public static final int MAX_WIDE_FILL_DEPTH = 32;

    private PavingLimits() {
    }

    public static int effectiveFillLevels(int configuredFillLevels, int createRollerFillDepth) {
        if (configuredFillLevels < 1) {
            throw new IllegalArgumentException("configuredFillLevels must be at least 1");
        }
        if (createRollerFillDepth < 0) {
            throw new IllegalArgumentException("createRollerFillDepth must not be negative");
        }
        return (int) Math.min(configuredFillLevels, (long) createRollerFillDepth + 1L);
    }

    public static int boundedWideFillDepth(int createRollerFillDepth) {
        if (createRollerFillDepth < 0) {
            throw new IllegalArgumentException("createRollerFillDepth must not be negative");
        }
        return Math.min(createRollerFillDepth, MAX_WIDE_FILL_DEPTH);
    }
}
