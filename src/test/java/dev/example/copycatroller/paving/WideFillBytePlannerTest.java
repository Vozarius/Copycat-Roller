package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WideFillBytePlannerTest {
    @Test
    void oneBlockSeedCreatesOnlyLateralHalfBlockSteps() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.0, 1, 0)),
            1
        );

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfZ() < 0 || cell.halfZ() > 1
        ));
        assertTrue(cells.stream().allMatch(cell ->
            distanceOutside(cell.halfX(), 0, 1)
                <= distanceOutside(cell.halfZ(), 0, 1)
        ));
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfX() == 0 || cell.halfX() == 1
        ));
        assertTrue(cells.stream().anyMatch(cell -> cell.distance() == 1));
        assertTrue(cells.stream().anyMatch(cell -> cell.distance() == 2));
        assertTrue(cells.stream()
            .filter(cell -> cell.distance() == 1)
            .allMatch(cell -> cell.lowerHalfY() == 1));
        assertTrue(cells.stream()
            .filter(cell -> cell.distance() == 2)
            .allMatch(cell -> cell.lowerHalfY() == 0));
    }

    @Test
    void tangentAlongZMovesTheSidesToX() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.0, 0, 1)),
            1
        );

        assertTrue(cells.stream().allMatch(cell ->
            cell.halfX() < 0 || cell.halfX() > 1
        ));
        assertTrue(cells.stream().allMatch(cell ->
            distanceOutside(cell.halfZ(), 0, 1)
                <= distanceOutside(cell.halfX(), 0, 1)
        ));
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfZ() == 0 || cell.halfZ() == 1
        ));
        assertTrue(cells.stream().anyMatch(cell -> cell.halfX() == -1));
        assertTrue(cells.stream().anyMatch(cell -> cell.halfX() == 2));
    }

    @Test
    void anchorFootprintIsNeverReplacedByBytes() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(-1, -1, -4.0)),
            1
        );

        for (int halfX = -2; halfX <= -1; halfX++) {
            for (int halfZ = -2; halfZ <= -1; halfZ++) {
                int expectedX = halfX;
                int expectedZ = halfZ;
                assertFalse(cells.stream().anyMatch(cell ->
                    cell.halfX() == expectedX && cell.halfZ() == expectedZ
                ));
            }
        }
    }

    @Test
    void adjacentLongitudinalTrackCellsFormOneLateralShell() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(
                new WideFillBytePlanner.Seed(0, 0, 0.0, 1, 0),
                new WideFillBytePlanner.Seed(1, 0, 0.0, 1, 0)
            ),
            1
        );

        assertTrue(cells.stream().allMatch(cell ->
            cell.halfZ() < 0 || cell.halfZ() > 1
        ));
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfX() >= 0 && cell.halfX() <= 3
        ));
        assertTrue(cells.stream().anyMatch(cell ->
            cell.halfX() == 0 && cell.halfZ() == -1
                && cell.lowerHalfY() == 1
        ));
        assertTrue(cells.stream().anyMatch(cell ->
            cell.halfX() == 3 && cell.halfZ() == 2
                && cell.lowerHalfY() == 1
        ));
    }

    @Test
    void halfBlockTrackHeightRaisesTheWholeShellByOneHalfCell() {
        List<WideFillBytePlanner.ByteCell> level = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.0)),
            1
        );
        List<WideFillBytePlanner.ByteCell> raised = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.5)),
            1
        );

        assertEquals(level.size(), raised.size());
        for (int index = 0; index < level.size(); index++) {
            assertEquals(
                level.get(index).lowerHalfY() + 1,
                raised.get(index).lowerHalfY()
            );
        }
    }

    @Test
    void edgeSeedCreatesOnlyItsSelectedOutwardSide() {
        List<WideFillBytePlanner.ByteCell> negative = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 0, true, false
            )),
            1
        );
        List<WideFillBytePlanner.ByteCell> positive = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 0, false, true
            )),
            1
        );

        assertEquals(4, negative.size());
        assertTrue(negative.stream().allMatch(cell -> cell.halfZ() < 0));
        assertEquals(4, positive.size());
        assertTrue(positive.stream().allMatch(cell -> cell.halfZ() > 1));
    }

    @Test
    void interiorSeedCreatesNoLateralSlope() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 0, false, false
            )),
            2
        );

        assertTrue(cells.isEmpty());
    }

    @Test
    void diagonalTangentProducesARealDiagonalNormal() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 1, false, true
            )),
            2
        );

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().allMatch(cell ->
            -cell.halfX() + cell.halfZ() > 0
        ));
        assertTrue(cells.stream().anyMatch(cell ->
            cell.halfX() < 0 && cell.halfZ() > 1
        ));
    }

    @Test
    void everyOffsetBandStaysClosedAroundAQuarterTurn() {
        List<WideFillBytePlanner.Seed> seeds = List.of(
            new WideFillBytePlanner.Seed(0, 0, 0.0, 1, 0, false, true),
            new WideFillBytePlanner.Seed(1, 0, 0.0, 1, 0.5, false, true),
            new WideFillBytePlanner.Seed(1, 1, 0.0, 1, 1, false, true),
            new WideFillBytePlanner.Seed(2, 1, 0.0, 0.5, 1, false, true),
            new WideFillBytePlanner.Seed(2, 2, 0.0, 0, 1, false, true)
        );
        List<WideFillBytePlanner.ByteCell> cells =
            WideFillBytePlanner.plan(seeds, 3);

        for (int distance = 1; distance <= 6; distance++) {
            int expectedDistance = distance;
            List<WideFillBytePlanner.ByteCell> band = cells.stream()
                .filter(cell -> cell.distance() == expectedDistance)
                .toList();
            assertFalse(band.isEmpty(), "missing offset band " + distance);
            assertTrue(band.stream().allMatch(cell ->
                cell.lowerHalfY() == 2 - expectedDistance
            ));
            assertEightConnected(
                band,
                "disconnected quarter-turn offset band " + distance
            );
        }
        assertEightConnected(cells, "quarter-turn surface is not closed");
        assertEveryCellHasReachableParent(seeds, cells);
    }

    @Test
    void reachMatchesCreatesWideFillRadius() {
        assertEquals(0, WideFillBytePlanner.reachBlocksForCreateDepth(0));
        assertEquals(1, WideFillBytePlanner.reachBlocksForCreateDepth(1));
        assertEquals(1, WideFillBytePlanner.reachBlocksForCreateDepth(2));
        assertEquals(2, WideFillBytePlanner.reachBlocksForCreateDepth(3));
        assertEquals(6, WideFillBytePlanner.reachBlocksForCreateDepth(12));
        assertThrows(
            IllegalArgumentException.class,
            () -> WideFillBytePlanner.reachBlocksForCreateDepth(-1)
        );

        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.0)),
            3
        );
        assertEquals(6, cells.stream().mapToInt(
            WideFillBytePlanner.ByteCell::distance
        ).max().orElseThrow());
    }

    private static int distanceOutside(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return Math.max(0, value - maximum);
    }

    private static void assertEveryCellHasReachableParent(
        List<WideFillBytePlanner.Seed> seeds,
        List<WideFillBytePlanner.ByteCell> cells
    ) {
        Set<WideFillBytePlanner.HalfVoxel> reached = new HashSet<>(
            WideFillBytePlanner.seedVoxels(seeds)
        );
        for (WideFillBytePlanner.ByteCell cell : cells) {
            int parentY = cell.distance() == 1
                ? cell.lowerHalfY()
                : cell.lowerHalfY() + 1;
            boolean parentFound = false;
            for (int offsetX = -1; offsetX <= 1 && !parentFound; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (reached.contains(new WideFillBytePlanner.HalfVoxel(
                        cell.halfX() + offsetX,
                        parentY,
                        cell.halfZ() + offsetZ
                    ))) {
                        parentFound = true;
                        break;
                    }
                }
            }
            assertTrue(parentFound, "unreachable planned cell " + cell);
            reached.add(cell.voxel());
        }
    }

    private static void assertEightConnected(
        List<WideFillBytePlanner.ByteCell> cells,
        String message
    ) {
        Set<String> remaining = new HashSet<>();
        for (WideFillBytePlanner.ByteCell cell : cells) {
            remaining.add(cell.halfX() + "," + cell.halfZ());
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        String first = remaining.iterator().next();
        remaining.remove(first);
        queue.add(first);
        while (!queue.isEmpty()) {
            String[] coordinates = queue.removeFirst().split(",");
            int x = Integer.parseInt(coordinates[0]);
            int z = Integer.parseInt(coordinates[1]);
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if ((offsetX == 0 && offsetZ == 0)
                        || Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != 1) {
                        continue;
                    }
                    String neighbour = (x + offsetX) + "," + (z + offsetZ);
                    if (remaining.remove(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
        }
        assertTrue(remaining.isEmpty(), message + ": " + remaining);
    }
}
