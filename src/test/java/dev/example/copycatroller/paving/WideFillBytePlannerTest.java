package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WideFillBytePlannerTest {
    @Test
    void oneBlockSeedCreatesOnlyLateralHalfBlockSteps() {
        List<WideFillBytePlanner.ByteCell> cells = WideFillBytePlanner.plan(
            List.of(new WideFillBytePlanner.Seed(0, 0, 0.0, 1, 0)),
            1
        );

        assertEquals(8, cells.size());
        assertEquals(4, cells.stream().filter(cell -> cell.distance() == 1).count());
        assertEquals(4, cells.stream().filter(cell -> cell.distance() == 2).count());
        assertTrue(cells.stream().allMatch(cell ->
            cell.halfX() == 0 || cell.halfX() == 1
        ));
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

        assertFalse(cells.stream().anyMatch(cell ->
            cell.halfX() < 0 || cell.halfX() > 3
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
}
