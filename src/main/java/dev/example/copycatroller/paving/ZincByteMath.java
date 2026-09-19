package dev.example.copycatroller.paving;

/**
 * Lossless conversion math for the Copycats+ Copycat Byte stonecutting recipe.
 * One zinc ingot produces eight Byte items.
 */
public final class ZincByteMath {
    public static final int BYTES_PER_ZINC = 8;

    private ZincByteMath() {
    }

    public static PaymentPlan plan(int requiredBytes, int availableBytes) {
        if (requiredBytes < 1) {
            throw new IllegalArgumentException("requiredBytes must be positive");
        }
        if (availableBytes < 0) {
            throw new IllegalArgumentException("availableBytes must not be negative");
        }

        int bytesToConsume = Math.min(requiredBytes, availableBytes);
        int uncoveredBytes = requiredBytes - bytesToConsume;
        int zincIngots = uncoveredBytes == 0
            ? 0
            : (uncoveredBytes + BYTES_PER_ZINC - 1) / BYTES_PER_ZINC;
        int byteChange = zincIngots * BYTES_PER_ZINC - uncoveredBytes;
        return new PaymentPlan(bytesToConsume, zincIngots, byteChange);
    }

    public record PaymentPlan(
        int bytesToConsume,
        int zincIngotsToConsume,
        int byteChange
    ) {
        public PaymentPlan {
            if (bytesToConsume < 0
                || zincIngotsToConsume < 0
                || byteChange < 0
                || byteChange >= BYTES_PER_ZINC) {
                throw new IllegalArgumentException("invalid zinc Byte payment plan");
            }
        }
    }
}
