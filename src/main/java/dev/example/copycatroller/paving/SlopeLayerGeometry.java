package dev.example.copycatroller.paving;

import java.util.OptionalInt;

/**
 * Exact endpoint geometry of Copycats+ 3.0.4 bottom Slope Layer states.
 *
 * <p>Layers 1..4 grow the facing (high) edge by quarters. Layers 5..8
 * then grow the opposite (low) edge until the state becomes a full block.</p>
 */
public final class SlopeLayerGeometry {
    private static final double EPSILON = 1.0e-7;

    private SlopeLayerGeometry() {
    }

    public static Endpoints endpoints(int layers) {
        if (layers < 1 || layers > 8) {
            throw new IllegalArgumentException("layers must be in [1, 8]");
        }
        if (layers <= 4) {
            return new Endpoints(0, layers / 4.0);
        }
        return new Endpoints((layers - 4) / 4.0, 1);
    }

    /**
     * Finds the closest state that remains entirely below both sampled track
     * edges. {@code maxLayers} preserves the configured volume-rounding bias.
     */
    public static OptionalInt selectBestState(
        double desiredLow,
        double desiredHigh,
        int maxLayers,
        double maxVerticalError
    ) {
        requireFinite(desiredLow);
        requireFinite(desiredHigh);
        requireFinite(maxVerticalError);
        if (maxLayers < 0 || maxLayers > 8) {
            throw new IllegalArgumentException("maxLayers must be in [0, 8]");
        }
        if (maxVerticalError < 0) {
            throw new IllegalArgumentException("maxVerticalError must not be negative");
        }

        int selected = 0;
        double selectedError = Double.POSITIVE_INFINITY;
        for (int layers = 1; layers <= maxLayers; layers++) {
            Endpoints endpoints = endpoints(layers);
            double lowGap = desiredLow - endpoints.low();
            double highGap = desiredHigh - endpoints.high();
            if (lowGap < -EPSILON || highGap < -EPSILON) {
                continue;
            }

            double error = Math.max(lowGap, highGap);
            if (error > maxVerticalError + EPSILON) {
                continue;
            }
            if (error < selectedError - EPSILON
                || Math.abs(error - selectedError) <= EPSILON && layers > selected) {
                selected = layers;
                selectedError = error;
            }
        }
        return selected == 0 ? OptionalInt.empty() : OptionalInt.of(selected);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("slope geometry values must be finite");
        }
    }

    public record Endpoints(double low, double high) {
        public Endpoints {
            if (low < 0 || high > 1 || low > high) {
                throw new IllegalArgumentException("invalid slope endpoints");
            }
        }
    }
}
