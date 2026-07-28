package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class SlopeLayerGeometryTest {
    @Test
    void matchesAllEightCopycatsEndpointShapes() {
        assertEndpoints(1, 0, 0.25);
        assertEndpoints(2, 0, 0.5);
        assertEndpoints(3, 0, 0.75);
        assertEndpoints(4, 0, 1);
        assertEndpoints(5, 0.25, 1);
        assertEndpoints(6, 0.5, 1);
        assertEndpoints(7, 0.75, 1);
        assertEndpoints(8, 1, 1);
    }

    @Test
    void acceptsARepresentableQuarterSlopeBelowTheTrack() {
        assertEquals(
            1,
            SlopeLayerGeometry.selectBestState(0.125, 0.375, 1, 0.25)
                .orElseThrow()
        );
        assertEquals(
            7,
            SlopeLayerGeometry.selectBestState(0.875, 1.125, 7, 0.25)
                .orElseThrow()
        );
    }

    @Test
    void skipsTheMidCellStateThatWouldCreateASawtooth() {
        OptionalInt selected =
            SlopeLayerGeometry.selectBestState(0.375, 0.625, 3, 0.25);
        assertTrue(selected.isEmpty());
    }

    @Test
    void acceptsARepresentableHalfSlopeAtQuarterBlockTolerance() {
        assertEquals(
            3,
            SlopeLayerGeometry.selectBestState(0.25, 0.75, 3, 0.25)
                .orElseThrow()
        );
    }

    @Test
    void neverChoosesAStateThatCrossesEitherTrackEdge() {
        OptionalInt selected =
            SlopeLayerGeometry.selectBestState(0.5, 0.75, 8, 1);
        int layers = selected.orElseThrow();
        SlopeLayerGeometry.Endpoints endpoints =
            SlopeLayerGeometry.endpoints(layers);
        assertTrue(endpoints.low() <= 0.5);
        assertTrue(endpoints.high() <= 0.75);
    }

    private static void assertEndpoints(int layers, double low, double high) {
        SlopeLayerGeometry.Endpoints endpoints =
            SlopeLayerGeometry.endpoints(layers);
        assertEquals(low, endpoints.low(), 1.0e-9);
        assertEquals(high, endpoints.high(), 1.0e-9);
    }
}
