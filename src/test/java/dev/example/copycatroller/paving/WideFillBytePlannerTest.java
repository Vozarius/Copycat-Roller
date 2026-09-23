package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
    void allRollerFootprintsProtectTheCentralPavingMask() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(
                new WideFillBytePlanner.Seed(
                    0, 0, 0.0, 1, 0, false, true
                ),
                new WideFillBytePlanner.Seed(
                    0, 1, 0.0, 1, 0, false, false
                ),
                new WideFillBytePlanner.Seed(
                    0, 2, 0.0, 1, 0, false, false
                )
            ),
            3
        );

        assertTrue(
            cells.isEmpty(),
            "an edge must not emit into the footprints of the other Rollers"
        );
    }

    @Test
    void collectiveFootprintLeavesTheSelectedOuterSideConnected() {
        List<WideFillBytePlanner.Seed> seeds = List.of(
            new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 0, true, false
            ),
            new WideFillBytePlanner.Seed(
                0, 1, 0.0, 1, 0, false, false
            ),
            new WideFillBytePlanner.Seed(
                0, 2, 0.0, 1, 0, false, false
            )
        );
        List<WideFillBytePlanner.ByteCell> cells =
            WideFillBytePlanner.plan(seeds, 3);

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().allMatch(cell -> cell.halfZ() < 0));
        for (int distance = 1; distance <= 6; distance++) {
            int expectedDistance = distance;
            assertTrue(cells.stream().anyMatch(cell ->
                cell.distance() == expectedDistance
            ));
        }
        assertEveryCellHasReachableParent(seeds, cells);
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
    void explicitWorldNormalOverridesTangentHandedness() {
        List<WideFillBytePlanner.ByteCell> forward = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, 1, 0, 0, -1, false, true
            )),
            1
        );
        List<WideFillBytePlanner.ByteCell> reversed = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(
                0, 0, 0.0, -1, 0, 0, -1, false, true
            )),
            1
        );

        assertEquals(forward, reversed);
        assertFalse(forward.isEmpty());
        assertTrue(forward.stream().allMatch(cell -> cell.halfZ() < 0));
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
    void haloShapesTheEndsWithoutOwningWorldOutput() {
        List<WideFillBytePlanner.Seed> seeds = List.of(
            new WideFillBytePlanner.Seed(-2, 0, 0, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(-1, 0, 0, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(0, 0, 0, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(1, 0, 0, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(2, 0, 0, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(3, 0, 0, 1, 0, false, true, false)
        );

        List<WideFillBytePlanner.ByteCell> cells =
            WideFillBytePlanner.plan(seeds, 3);

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfX() >= 0 && cell.halfX() <= 3
        ));
        for (int distance = 1; distance <= 6; distance++) {
            int expectedDistance = distance;
            assertTrue(cells.stream().anyMatch(cell ->
                cell.distance() == expectedDistance
            ));
        }
        assertEveryCellHasReachableParent(seeds, cells);
    }

    @Test
    void heightChangesCannotPunchHolesInWritableBands() {
        List<WideFillBytePlanner.Seed> seeds = List.of(
            new WideFillBytePlanner.Seed(-2, 0, -0.5, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(-1, 0, 0.0, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(0, 0, 0.0, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(1, 0, 0.5, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(2, 0, 0.5, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(3, 0, 1.0, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(4, 0, 1.5, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(5, 0, 1.5, 1, 0, false, true, true),
            new WideFillBytePlanner.Seed(6, 0, 2.0, 1, 0, false, true, false),
            new WideFillBytePlanner.Seed(7, 0, 2.0, 1, 0, false, true, false)
        );
        List<WideFillBytePlanner.ByteCell> cells =
            WideFillBytePlanner.plan(seeds, 3);

        for (int distance = 1; distance <= 6; distance++) {
            int expectedDistance = distance;
            List<WideFillBytePlanner.ByteCell> band = cells.stream()
                .filter(cell -> cell.distance() == expectedDistance)
                .toList();
            for (int halfX = 0; halfX <= 11; halfX++) {
                int expectedHalfX = halfX;
                assertTrue(
                    band.stream().anyMatch(cell ->
                        cell.halfX() == expectedHalfX
                    ),
                    "height transition left a hole at band "
                        + distance + ", halfX " + halfX
                );
            }
            assertEightConnected(
                band,
                "height-changing writable band is disconnected " + distance
            );
        }
        assertEveryCellHasReachableParent(seeds, cells);
    }
    @Test
    void movingCoreWindowsCoverOneMonolithicCurvedContour() {
        List<WideFillBytePlanner.Seed> curve = List.of(
            new WideFillBytePlanner.Seed(0, 8, 0, 8, 0, 0, 8, false, true),
            new WideFillBytePlanner.Seed(1, 8, 0, 8, -1, 1, 8, false, true),
            new WideFillBytePlanner.Seed(2, 8, 0, 8, -2, 2, 8, false, true),
            new WideFillBytePlanner.Seed(3, 7, 0, 7, -3, 3, 7, false, true),
            new WideFillBytePlanner.Seed(4, 7, 0, 7, -4, 4, 7, false, true),
            new WideFillBytePlanner.Seed(5, 6, 0, 6, -5, 5, 6, false, true),
            new WideFillBytePlanner.Seed(6, 5, 0, 5, -6, 6, 5, false, true),
            new WideFillBytePlanner.Seed(7, 4, 0, 4, -7, 7, 4, false, true),
            new WideFillBytePlanner.Seed(7, 3, 0, 3, -7, 7, 3, false, true),
            new WideFillBytePlanner.Seed(8, 2, 0, 2, -8, 8, 2, false, true),
            new WideFillBytePlanner.Seed(8, 1, 0, 1, -8, 8, 1, false, true),
            new WideFillBytePlanner.Seed(8, 0, 0, 0, -8, 8, 0, false, true)
        );
        List<WideFillBytePlanner.ByteCell> monolithic =
            WideFillBytePlanner.plan(curve, 6);
        Set<WideFillBytePlanner.ByteCell> traversed = new HashSet<>();

        for (int owner = 0; owner < curve.size(); owner++) {
            List<WideFillBytePlanner.Seed> window = new ArrayList<>();
            int first = Math.max(0, owner - 3);
            int last = Math.min(curve.size() - 1, owner + 3);
            for (int index = first; index <= last; index++) {
                WideFillBytePlanner.Seed seed = curve.get(index);
                window.add(new WideFillBytePlanner.Seed(
                    seed.blockX(),
                    seed.blockZ(),
                    seed.surfaceY(),
                    seed.tangentX(),
                    seed.tangentZ(),
                    seed.normalX(),
                    seed.normalZ(),
                    seed.allowNegativeLateral(),
                    seed.allowPositiveLateral(),
                    index == owner
                ));
            }
            traversed.addAll(WideFillBytePlanner.plan(window, 6));
        }

        assertFalse(monolithic.isEmpty());
        assertEveryCellHasReachableParent(curve, monolithic);
        assertEquals(
            new HashSet<>(monolithic),
            traversed,
            "moving profile windows left radial gaps or overlapping rays"
        );
        for (int distance = 1; distance <= 12; distance++) {
            int expectedDistance = distance;
            List<WideFillBytePlanner.ByteCell> band = traversed.stream()
                .filter(cell -> cell.distance() == expectedDistance)
                .toList();
            assertFalse(band.isEmpty(), "missing curved band " + distance);
            assertEightConnected(
                band,
                "moving curved band is not monolithic at " + distance
            );
            assertFourConnected(
                band,
                "moving curved band contains a diagonal visual gap at "
                    + distance
            );
        }
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
            boolean parentFound = false;
            for (int offsetX = -1; offsetX <= 1 && !parentFound; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1 && !parentFound; offsetZ++) {
                    for (int parentY = cell.lowerHalfY();
                         parentY <= cell.lowerHalfY() + 1;
                         parentY++) {
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
            }
            assertTrue(parentFound, "unreachable planned cell " + cell);
            reached.add(cell.voxel());
        }
    }

    private static void assertFourConnected(
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
                    if (Math.abs(offsetX) + Math.abs(offsetZ) != 1) {
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
