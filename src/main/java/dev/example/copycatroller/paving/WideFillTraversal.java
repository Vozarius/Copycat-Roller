package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

import dev.example.copycatroller.paving.WideFillBytePlanner.ByteCell;
import dev.example.copycatroller.paving.WideFillBytePlanner.HalfVoxel;

/** Visits only planned cells reachable through successfully placed or existing parts. */
public final class WideFillTraversal {
    private static final Comparator<ByteCell> ORDER = Comparator
        .comparingInt(ByteCell::distance)
        .thenComparingInt(ByteCell::halfX)
        .thenComparingInt(ByteCell::halfZ)
        .thenComparingInt(ByteCell::lowerHalfY);

    private WideFillTraversal() {
    }

    /**
     * Add only the ancestors needed to connect owned cells to the profile.
     * Geometry from the halo may supply an ancestor, but never starts a new
     * output branch. Each cell has at most one earlier, acyclic predecessor.
     */
    public static List<ByteCell> includeRequiredSupports(
        List<HalfVoxel> roots,
        List<ByteCell> geometry,
        Set<ByteCell> owned
    ) {
        Map<HalfVoxel, ByteCell> waiting = new HashMap<>();
        for (ByteCell cell : geometry) {
            waiting.merge(cell.voxel(), cell,
                (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        Map<HalfVoxel, ByteCell> parents = new HashMap<>();
        Map<HalfVoxel, ByteCell> reachable = new HashMap<>();
        // Prefer an equally near core parent before borrowing halo support.
        // Straight windows therefore keep their exact longitudinal bounds.
        Set<HalfVoxel> ownedVoxels = new HashSet<>();
        for (ByteCell cell : owned) ownedVoxels.add(cell.voxel());
        PriorityQueue<ByteCell> ready = new PriorityQueue<>(Comparator
            .comparingInt(ByteCell::distance)
            .thenComparing(cell -> !ownedVoxels.contains(cell.voxel()))
            .thenComparing(ORDER));
        for (HalfVoxel root : roots) {
            enqueueChildren(root, waiting, ready, null, parents);
        }
        while (!ready.isEmpty()) {
            ByteCell cell = ready.remove();
            reachable.put(cell.voxel(), cell);
            enqueueChildren(cell.voxel(), waiting, ready, cell, parents);
        }
        Set<ByteCell> output = new HashSet<>();
        for (ByteCell owner : owned) {
            ByteCell cell = reachable.get(owner.voxel());
            while (cell != null && output.add(cell)) {
                cell = parents.get(cell.voxel());
            }
        }
        List<ByteCell> ordered = new ArrayList<>(output);
        ordered.sort(ORDER);
        return List.copyOf(ordered);
    }

    public static void visitReachable(
        List<HalfVoxel> roots,
        List<ByteCell> cells,
        Predicate<ByteCell> visitor
    ) {
        Map<HalfVoxel, ByteCell> waiting = new HashMap<>();
        for (ByteCell cell : cells) {
            waiting.merge(cell.voxel(), cell,
                (first, second) -> ORDER.compare(first, second) <= 0 ? first : second);
        }
        PriorityQueue<ByteCell> ready = new PriorityQueue<>(ORDER);
        for (HalfVoxel root : roots) {
            enqueueChildren(root, waiting, ready, null, null);
        }
        while (!ready.isEmpty()) {
            ByteCell cell = ready.remove();
            if (visitor.test(cell)) {
                enqueueChildren(cell.voxel(), waiting, ready, null, null);
            }
        }
    }

    private static void enqueueChildren(
        HalfVoxel parent,
        Map<HalfVoxel, ByteCell> waiting,
        PriorityQueue<ByteCell> ready,
        ByteCell predecessor,
        Map<HalfVoxel, ByteCell> parents
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int drop = 0; drop <= 1; drop++) {
                    ByteCell child = waiting.remove(new HalfVoxel(
                        parent.halfX() + dx, parent.halfY() - drop, parent.halfZ() + dz
                    ));
                    // Removing on enqueue bounds both work and memory by the
                    // original plan. Failed world writes never propagate reach.
                    if (child != null) {
                        if (parents != null && predecessor != null) {
                            parents.put(child.voxel(), predecessor);
                        }
                        ready.add(child);
                    }
                }
            }
        }
    }
}
