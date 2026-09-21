package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class WideFillTargetPlannerTest {
    @Test
    void matchesCreatesDiamondAtEveryDepth() {
        List<TrackSurfaceSample> targets = WideFillTargetPlanner.expand(
            List.of(new TrackSurfaceSample(10, 20, 30.75)),
            4
        );

        assertEquals(37, targets.size());
        assertEquals(
            Set.of("10,30,20"),
            cellsAtY(targets, 30)
        );
        assertEquals(
            Set.of(
                "10,29,20",
                "9,29,20",
                "11,29,20",
                "10,29,19",
                "10,29,21"
            ),
            cellsAtY(targets, 29)
        );
        assertEquals(5, cellsAtY(targets, 28).size());
        assertEquals(13, cellsAtY(targets, 27).size());
        assertEquals(13, cellsAtY(targets, 26).size());
        assertTrue(cellsAtY(targets, 26).contains("12,26,20"));
        assertTrue(cellsAtY(targets, 26).contains("10,26,18"));
    }

    @Test
    void keepsFractionalSurfaceOnlyForTopCenterCell() {
        List<TrackSurfaceSample> targets = WideFillTargetPlanner.expand(
            List.of(new TrackSurfaceSample(3, 5, -2.25)),
            1
        );

        assertEquals(-2.25, targets.getFirst().surfaceY());
        assertTrue(targets.stream()
            .skip(1)
            .allMatch(sample -> sample.surfaceY() == -4.0));
    }

    @Test
    void rejectsNegativeDepth() {
        assertThrows(
            IllegalArgumentException.class,
            () -> WideFillTargetPlanner.expand(List.of(), -1)
        );
    }

    private static Set<String> cellsAtY(
        List<TrackSurfaceSample> targets,
        int y
    ) {
        return targets.stream()
            .filter(sample -> Math.floor(sample.surfaceY()) == y)
            .map(sample -> sample.x() + "," + y + "," + sample.z())
            .collect(Collectors.toSet());
    }
}
