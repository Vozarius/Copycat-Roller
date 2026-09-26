package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.example.copycatroller.paving.WideFillBytePlanner.ByteCell;
import dev.example.copycatroller.paving.WideFillBytePlanner.HalfVoxel;

class WideFillTraversalTest {
    private final HalfVoxel root = new HalfVoxel(0, 2, 0);
    private final ByteCell first = new ByteCell(1, 1, 0, 1);
    private final ByteCell second = new ByteCell(0, 0, 0, 2);
    private final ByteCell bridge = new ByteCell(-1, 0, 0, 2);

    @Test
    void bridgeWaitsForItsSameBandParent() {
        var visited = new ArrayList<ByteCell>();
        WideFillTraversal.visitReachable(List.of(root), List.of(first, bridge, second), cell -> {
            visited.add(cell);
            return true;
        });
        assertEquals(List.of(first, second, bridge), visited);
    }

    @Test
    void blockedParentDoesNotBuildThroughAnObstacle() {
        var visited = new ArrayList<ByteCell>();
        WideFillTraversal.visitReachable(List.of(root), List.of(first, bridge, second), cell -> {
            visited.add(cell);
            return false;
        });
        assertEquals(List.of(first), visited);
    }

    @Test
    void duplicateCellsAndReversedInputAreVisitedOnceInStableOrder() {
        var visited = new ArrayList<ByteCell>();
        WideFillTraversal.visitReachable(List.of(root, root), List.of(second, bridge, first, bridge), cell -> {
            visited.add(cell);
            return true;
        });
        assertEquals(List.of(first, second, bridge), visited);
    }

    @Test
    void ownedCellIncludesItsHaloSupportButNotUnrelatedBranches() {
        ByteCell unrelated = new ByteCell(1, 1, 1, 1);
        assertEquals(List.of(first, bridge, second), WideFillTraversal.includeRequiredSupports(
            List.of(root), List.of(first, second, bridge, unrelated), Set.of(bridge)
        ));
    }

    @Test
    void haloWithoutOwnedCellsCannotStartOutput() {
        assertEquals(List.of(), WideFillTraversal.includeRequiredSupports(
            List.of(root), List.of(first, second, bridge), Set.of()
        ));
    }
}
