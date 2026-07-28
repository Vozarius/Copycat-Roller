package dev.example.copycatroller.paving;

/**
 * Lossless conversion math derived from the Copycats+ stonecutting recipes.
 *
 * <p>One zinc ingot yields sixteen Half Layer items. Two Half Layer items
 * craft into one ordinary Layer item, so Half Layer items are the smallest
 * exact unit and can safely represent every remainder.</p>
 */
public final class ZincCreditMath {
    public static final int CREDITS_PER_ZINC = 16;
    public static final int CREDITS_PER_LAYER = 2;
    public static final int CREDITS_PER_HALF_LAYER = 1;

    private ZincCreditMath() {
    }

    public static int creditsForLayers(int layers) {
        requireRange(layers, 0, 8, "layers");
        return layers * CREDITS_PER_LAYER;
    }

    public static int creditsForHalfLayers(
        int negativeLayers,
        int positiveLayers
    ) {
        requireRange(negativeLayers, 0, 8, "negativeLayers");
        requireRange(positiveLayers, 0, 8, "positiveLayers");
        return (negativeLayers + positiveLayers) * CREDITS_PER_HALF_LAYER;
    }

    public static PaymentPlan plan(int requiredCredits, int availableHalfLayers) {
        if (requiredCredits < 1) {
            throw new IllegalArgumentException("requiredCredits must be positive");
        }
        if (availableHalfLayers < 0) {
            throw new IllegalArgumentException("availableHalfLayers must not be negative");
        }

        int halfLayersToConsume = Math.min(requiredCredits, availableHalfLayers);
        int uncoveredCredits = requiredCredits - halfLayersToConsume;
        int zincIngots = uncoveredCredits == 0
            ? 0
            : (uncoveredCredits + CREDITS_PER_ZINC - 1) / CREDITS_PER_ZINC;
        int halfLayerChange = zincIngots * CREDITS_PER_ZINC - uncoveredCredits;
        return new PaymentPlan(
            halfLayersToConsume,
            zincIngots,
            halfLayerChange
        );
    }

    private static void requireRange(
        int value,
        int minimum,
        int maximum,
        String name
    ) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                name + " must be in [" + minimum + ", " + maximum + "]"
            );
        }
    }

    public record PaymentPlan(
        int halfLayersToConsume,
        int zincIngotsToConsume,
        int halfLayerChange
    ) {
        public PaymentPlan {
            if (halfLayersToConsume < 0
                || zincIngotsToConsume < 0
                || halfLayerChange < 0
                || halfLayerChange >= CREDITS_PER_ZINC) {
                throw new IllegalArgumentException("invalid zinc payment plan");
            }
        }
    }
}
