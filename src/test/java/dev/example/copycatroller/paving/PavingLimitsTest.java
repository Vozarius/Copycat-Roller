package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PavingLimitsTest {
    @Test
    void oneConfiguredLevelMeansOnlyTheNearestBaseBlock() {
        assertEquals(1, PavingLimits.effectiveFillLevels(1, 20));
    }

    @Test
    void configuredDepthControlsTheNumberOfLevels() {
        assertEquals(5, PavingLimits.effectiveFillLevels(5, 20));
    }

    @Test
    void createRollerFillDepthRemainsTheSafetyCap() {
        assertEquals(3, PavingLimits.effectiveFillLevels(512, 2));
    }

    @Test
    void arithmeticDoesNotOverflowAtCreateMaximum() {
        assertEquals(512, PavingLimits.effectiveFillLevels(512, Integer.MAX_VALUE));
        assertEquals(
            PavingLimits.MAX_WIDE_FILL_DEPTH,
            PavingLimits.boundedWideFillDepth(Integer.MAX_VALUE)
        );
    }

    @Test
    void surfaceSearchRemainsBoundedForExtremeGapsAndWorldHeights() {
        for (int configured : new int[] {12, 32, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            int depth = PavingLimits.surfaceSearchDepth(configured, configured + 1.0);
            assertEquals(Math.min(configured, 32) + 1, depth);
            for (int centerY : new int[] {-63, -32, 0, 65, 319}) {
                long firstY = (long) centerY - depth;
                assertEquals(depth + 1L, centerY - firstY + 1);
            }
            assertEquals(2, PavingLimits.surfaceSearchDepth(configured, 1.0));
        }
        assertEquals(33, PavingLimits.surfaceSearchDepth(Integer.MAX_VALUE, Double.MAX_VALUE));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
            IllegalArgumentException.class,
            () -> PavingLimits.effectiveFillLevels(0, 20)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> PavingLimits.effectiveFillLevels(1, -1)
        );
    }
}
