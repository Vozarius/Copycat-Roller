package dev.example.copycatroller.paving;

import net.minecraft.core.Direction;

/**
 * One Create paving column together with the local, unquantized track gradient.
 *
 * <p>The gradient is expressed in world coordinates: {@code gradientX} is the
 * Y change per one world-space X block and {@code gradientZ} is the equivalent
 * value for Z. The tangent is kept separately so level track still has a
 * deterministic longitudinal axis.</p>
 */
public record TrackSurfaceSample(
    int x,
    int z,
    double surfaceY,
    double tangentX,
    double tangentZ,
    double gradientX,
    double gradientZ
) {
    private static final double DIRECTION_EPSILON = 1.0e-9;

    public TrackSurfaceSample(int x, int z, double surfaceY) {
        this(x, z, surfaceY, 1, 0, 0, 0);
    }

    public TrackSurfaceSample {
        if (!Double.isFinite(surfaceY)
            || !Double.isFinite(tangentX)
            || !Double.isFinite(tangentZ)
            || !Double.isFinite(gradientX)
            || !Double.isFinite(gradientZ)) {
            throw new IllegalArgumentException("track surface sample values must be finite");
        }
        if (Math.abs(tangentX) < DIRECTION_EPSILON
            && Math.abs(tangentZ) < DIRECTION_EPSILON) {
            throw new IllegalArgumentException("horizontal tangent must not be zero");
        }
    }

    public Direction.Axis longitudinalAxis() {
        return Math.abs(tangentX) >= Math.abs(tangentZ)
            ? Direction.Axis.X
            : Direction.Axis.Z;
    }

    public double gradientAlongAxis() {
        return longitudinalAxis() == Direction.Axis.X ? gradientX : gradientZ;
    }

    public double negativeHalfSurfaceY() {
        return surfaceY - gradientAlongAxis() * 0.25;
    }

    public double positiveHalfSurfaceY() {
        return surfaceY + gradientAlongAxis() * 0.25;
    }

    /**
     * Copycat Slope Layer's facing points toward its higher horizontal side.
     */
    public Direction uphillDirection() {
        double gradient = gradientAlongAxis();
        boolean positive;
        if (Math.abs(gradient) >= DIRECTION_EPSILON) {
            positive = gradient > 0;
        } else {
            positive = longitudinalAxis() == Direction.Axis.X
                ? tangentX >= 0
                : tangentZ >= 0;
        }

        return switch (longitudinalAxis()) {
            case X -> positive ? Direction.EAST : Direction.WEST;
            case Z -> positive ? Direction.SOUTH : Direction.NORTH;
            case Y -> throw new IllegalStateException("longitudinal axis cannot be vertical");
        };
    }
}
