package dev.example.copycatroller.paving;

public final class LayerMath {
    public static final double BOUNDARY_EPSILON = 1.0e-7;

    private LayerMath() {
    }

    public static int baseY(double surfaceY) {
        requireFinite(surfaceY);
        return (int) Math.floor(surfaceY);
    }

    public static int layersAboveBase(double surfaceY) {
        return layersAboveBase(surfaceY, RoundingDirection.DOWN);
    }

    public static int layersAboveBase(double surfaceY, RoundingDirection roundingDirection) {
        int baseY = baseY(surfaceY);
        double fraction = surfaceY - baseY;
        double scaled = fraction * 8.0 - BOUNDARY_EPSILON;
        int layers = switch (roundingDirection) {
            case DOWN -> (int) Math.floor(scaled);
            case UP -> (int) Math.ceil(scaled);
        };
        return Math.max(0, Math.min(8, layers));
    }

    public static SurfaceBreakdown breakDown(double surfaceY) {
        return breakDown(surfaceY, RoundingDirection.DOWN);
    }

    public static SurfaceBreakdown breakDown(double surfaceY, RoundingDirection roundingDirection) {
        return new SurfaceBreakdown(
            baseY(surfaceY),
            8,
            layersAboveBase(surfaceY, roundingDirection)
        );
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("surfaceY must be finite");
        }
    }

    public record SurfaceBreakdown(int baseY, int baseLayers, int upperLayers) {
        public SurfaceBreakdown {
            if (baseLayers != 8) {
                throw new IllegalArgumentException("baseLayers must be 8");
            }
            if (upperLayers < 0 || upperLayers > 8) {
                throw new IllegalArgumentException("upperLayers must be in [0, 8]");
            }
        }
    }

    public enum RoundingDirection {
        DOWN,
        UP
    }
}
