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
