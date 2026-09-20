package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrackProfileSideResolverTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void outwardWorldSideDoesNotFlipWhenTrackTangentReverses() {
        TrackSurfaceSample inward = sample(10, 11, 1, 0);

        var forward = TrackProfileSideResolver.outwardNormal(
            sample(10, 10, 1, 0),
            List.of(inward)
        ).orElseThrow();
        var reversed = TrackProfileSideResolver.outwardNormal(
            sample(10, 10, -1, 0),
            List.of(inward)
        ).orElseThrow();

        assertEquals(0, forward.x(), EPSILON);
        assertEquals(-1, forward.z(), EPSILON);
        assertEquals(forward.x(), reversed.x(), EPSILON);
        assertEquals(forward.z(), reversed.z(), EPSILON);
    }

    @Test
    void longitudinalCandidatesCannotChooseTheSide() {
        var normal = TrackProfileSideResolver.outwardNormal(
            sample(10, 10, 1, 0),
            List.of(
                sample(9, 10, 1, 0),
                sample(10, 12, 1, 0)
            )
        ).orElseThrow();

        assertEquals(0, normal.x(), EPSILON);
        assertEquals(-1, normal.z(), EPSILON);
    }

    @Test
    void overlappingQuantizedProfilesRemainAmbiguous() {
        assertTrue(TrackProfileSideResolver.outwardNormal(
            sample(4, 4, 1, 0),
            List.of(sample(4, 4, 1, 0), sample(5, 4, 1, 0))
        ).isEmpty());
    }

    @Test
    void diagonalCurveUsesItsSmoothTrackNormal() {
        double inverseRootTwo = 1 / Math.sqrt(2);
        var normal = TrackProfileSideResolver.outwardNormal(
            sample(4, 4, 1, 1),
            List.of(sample(3, 5, 1, 1))
        ).orElseThrow();

        assertEquals(inverseRootTwo, normal.x(), EPSILON);
        assertEquals(-inverseRootTwo, normal.z(), EPSILON);
    }

    @Test
    void stationCorrespondenceWinsOverNearestQuantizedCell() {
        var normal = TrackProfileSideResolver.outwardNormal(
            sample(10, 10, 1, 0, 10),
            List.of(
                sample(10, 11, 1, 0, 20),
                sample(10, 8, 1, 0, 10)
            )
        ).orElseThrow();

        assertEquals(0, normal.x(), EPSILON);
        assertEquals(1, normal.z(), EPSILON);
    }


    private static TrackSurfaceSample sample(
        int x,
        int z,
        double tangentX,
        double tangentZ
    ) {
        return sample(x, z, tangentX, tangentZ, 0);
    }

    @Test
    void adjacentEdgeStationsCannotStealSideCorrespondence() {
        TrackSurfaceSample edge = sample(10, 10, 1, 0, 0, 7);
        var normal = TrackProfileSideResolver.outwardNormal(
            edge,
            List.of(
                sample(10, 9, 1, 0, 0, 8),
                sample(10, 12, 1, 0, 1, 7)
            )
        ).orElseThrow();

        assertEquals(0, normal.x(), EPSILON);
        assertEquals(-1, normal.z(), EPSILON);
    }
    private static TrackSurfaceSample sample(
        int x,
        int z,
        double tangentX,
        double tangentZ,
        double station
    ) {
        return sample(x, z, tangentX, tangentZ, station, 0);
    }

    private static TrackSurfaceSample sample(
        int x,
        int z,
        double tangentX,
        double tangentZ,
        double station,
        long sectionKey
    ) {
        return new TrackSurfaceSample(
            x,
            z,
            0,
            tangentX,
            tangentZ,
            0,
            0,
            station,
            sectionKey
        );
    }
}
