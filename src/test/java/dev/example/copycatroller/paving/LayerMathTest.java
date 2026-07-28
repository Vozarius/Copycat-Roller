package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.example.copycatroller.paving.LayerMath.RoundingDirection.DOWN;
import static dev.example.copycatroller.paving.LayerMath.RoundingDirection.UP;

import org.junit.jupiter.api.Test;

class LayerMathTest {
    @Test
    void roundsUpToTheRequestedCeiling() {
        assertLayers(0.0, UP, 0);
        assertLayers(0.000001, UP, 1);
        assertLayers(0.125, UP, 1);
        assertLayers(0.125001, UP, 2);
        assertLayers(0.25, UP, 2);
        assertLayers(0.5, UP, 4);
        assertLayers(0.875, UP, 7);
        assertLayers(0.999999, UP, 8);
    }

    @Test
    void roundsDownToThePreviousEighthBucket() {
        assertLayers(0.0, DOWN, 0);
        assertLayers(0.000001, DOWN, 0);
        assertLayers(0.125, DOWN, 0);
        assertLayers(0.125001, DOWN, 1);
        assertLayers(0.25, DOWN, 1);
        assertLayers(0.5, DOWN, 3);
        assertLayers(0.875, DOWN, 6);
        assertLayers(0.999999, DOWN, 7);
    }

    @Test
    void absorbsTinyFloatingPointNoiseAtEighthBoundaries() {
        assertLayers(0.125 - 1.0e-12, UP, 1);
        assertLayers(0.125 + 1.0e-12, UP, 1);
        assertLayers(0.25 - 1.0e-12, UP, 2);
        assertLayers(0.25 + 1.0e-12, UP, 2);
        assertLayers(0.5 - 1.0e-12, UP, 4);
        assertLayers(0.5 + 1.0e-12, UP, 4);
        assertLayers(1.0e-9, UP, 0);

        assertLayers(0.125 - 1.0e-12, DOWN, 0);
        assertLayers(0.125 + 1.0e-12, DOWN, 0);
        assertLayers(0.25 - 1.0e-12, DOWN, 1);
        assertLayers(0.25 + 1.0e-12, DOWN, 1);
    }

    @Test
    void handlesNegativeWorldCoordinates() {
        assertBreakdown(-10.0, UP, -10, 0);
        assertBreakdown(-9.875, UP, -10, 1);
        assertBreakdown(-9.5, UP, -10, 4);
        assertBreakdown(-9.000001, UP, -10, 8);
        assertBreakdown(-9.875, DOWN, -10, 0);
        assertBreakdown(-9.5, DOWN, -10, 3);
        assertBreakdown(-9.000001, DOWN, -10, 7);
    }

    @Test
    void crossesAnIntegerWithoutMovingTheLowerColumnEarly() {
        assertBreakdown(9.999999, UP, 9, 8);
        assertBreakdown(10.0, UP, 10, 0);
        assertBreakdown(10.000001, UP, 10, 1);
        assertBreakdown(9.999999, DOWN, 9, 7);
        assertBreakdown(10.0, DOWN, 10, 0);
        assertBreakdown(10.000001, DOWN, 10, 0);
    }

    @Test
    void halfBlockMatchesCreateSlabGeometry() {
        LayerMath.SurfaceBreakdown result = LayerMath.breakDown(42.5, UP);
        assertEquals(42, result.baseY());
        assertEquals(8, result.baseLayers());
        assertEquals(4, result.upperLayers());
    }

    @Test
    void noArgumentOverloadDefaultsToDown() {
        assertEquals(3, LayerMath.layersAboveBase(42.5));
        assertEquals(3, LayerMath.breakDown(42.5).upperLayers());
    }

    @Test
    void quantizesSubCellHeightsForHalfLayers() {
        assertEquals(0, LayerMath.layersForHeight(-0.25, UP));
        assertEquals(3, LayerMath.layersForHeight(0.375, UP));
        assertEquals(5, LayerMath.layersForHeight(0.625, UP));
        assertEquals(8, LayerMath.layersForHeight(1.25, UP));

        assertEquals(0, LayerMath.layersForHeight(0.125, DOWN));
        assertEquals(2, LayerMath.layersForHeight(0.375, DOWN));
        assertEquals(4, LayerMath.layersForHeight(0.625, DOWN));
        assertEquals(8, LayerMath.layersForHeight(1.25, DOWN));
    }

    private static void assertLayers(
        double value,
        LayerMath.RoundingDirection roundingDirection,
        int expected
    ) {
        assertEquals(
            expected,
            LayerMath.layersAboveBase(value, roundingDirection),
            () -> "value=" + value + ", rounding=" + roundingDirection
        );
    }

    private static void assertBreakdown(
        double value,
        LayerMath.RoundingDirection roundingDirection,
        int baseY,
        int layers
    ) {
        LayerMath.SurfaceBreakdown result = LayerMath.breakDown(value, roundingDirection);
        assertEquals(baseY, result.baseY(), () -> "base for " + value);
        assertEquals(layers, result.upperLayers(), () -> "layers for " + value);
    }
}
