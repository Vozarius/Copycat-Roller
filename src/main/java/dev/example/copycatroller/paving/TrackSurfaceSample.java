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

    /**
     * Minimum profile Y under one longitudinal half of this X/Z cell.
     *
     * <p>Using the minimum instead of the half's centre prevents a rectangular
     * Half Layer from crossing the rail plane at diagonal and curved track.</p>
     */
    public double minimumHalfSurfaceY(boolean positiveHalf) {
        if (longitudinalAxis() == Direction.Axis.X) {
            return minimumSurfaceY(
                positiveHalf ? 0 : -0.5,
                positiveHalf ? 0.5 : 0,
                -0.5,
                0.5
            );
        }
        return minimumSurfaceY(
            -0.5,
            0.5,
            positiveHalf ? 0 : -0.5,
            positiveHalf ? 0.5 : 0
        );
    }

    public double minimumCellSurfaceY() {
        return minimumSurfaceY(-0.5, 0.5, -0.5, 0.5);
    }

    public double maximumCellSurfaceY() {
        return maximumSurfaceY(-0.5, 0.5, -0.5, 0.5);
    }

    public double lowSlopeEdgeSurfaceY() {
        Direction uphill = uphillDirection();
        return slopeEdgeSurfaceY(uphill.getOpposite());
    }

    public double highSlopeEdgeSurfaceY() {
        return slopeEdgeSurfaceY(uphillDirection());
    }

    private double slopeEdgeSurfaceY(Direction edge) {
        if (edge.getAxis() != longitudinalAxis()) {
            throw new IllegalArgumentException("slope edge must use the longitudinal axis");
        }
        if (longitudinalAxis() == Direction.Axis.X) {
            return surfaceY
                + gradientX * edge.getStepX() * 0.5
                - Math.abs(gradientZ) * 0.5;
        }
        return surfaceY
            + gradientZ * edge.getStepZ() * 0.5
            - Math.abs(gradientX) * 0.5;
    }

    private double minimumSurfaceY(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ
    ) {
        return surfaceY
            + minimumContribution(gradientX, minimumX, maximumX)
            + minimumContribution(gradientZ, minimumZ, maximumZ);
    }

    private static double minimumContribution(
        double gradient,
        double minimumOffset,
        double maximumOffset
    ) {
        return gradient * (gradient >= 0 ? minimumOffset : maximumOffset);
    }

    private double maximumSurfaceY(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ
    ) {
        return surfaceY
            + maximumContribution(gradientX, minimumX, maximumX)
            + maximumContribution(gradientZ, minimumZ, maximumZ);
    }

    private static double maximumContribution(
        double gradient,
        double minimumOffset,
        double maximumOffset
    ) {
        return gradient * (gradient >= 0 ? maximumOffset : minimumOffset);
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
