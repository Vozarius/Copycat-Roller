package dev.example.copycatroller.paving;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproduces the set of block cells visited by Create's {@code WIDE_FILL}
 * depth loop without reading or changing the world.
 */
public final class WideFillTargetPlanner {
    private WideFillTargetPlanner() {
    }

    public static List<TrackSurfaceSample> expand(
        List<TrackSurfaceSample> centerline,
        int maximumDepth
    ) {
        if (maximumDepth < 0) {
            throw new IllegalArgumentException("maximumDepth must not be negative");
        }

        int boundedDepth = PavingLimits.boundedWideFillDepth(maximumDepth);
        Map<TargetCell, TrackSurfaceSample> targets = new LinkedHashMap<>();
        for (int depth = 0; depth <= boundedDepth; depth++) {
            int radius = (depth + 1) / 2;
            for (TrackSurfaceSample sample : centerline) {
                int baseY = (int) Math.floor(sample.surfaceY());
                for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                    for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                        if (Math.abs(offsetX) + Math.abs(offsetZ) > radius) {
                            continue;
                        }
                        TargetCell cell = new TargetCell(
                            sample.x() + offsetX,
                            baseY - depth,
                            sample.z() + offsetZ
                        );
                        TrackSurfaceSample target = depth == 0
                            && offsetX == 0
                            && offsetZ == 0
                                ? sample
                                : new TrackSurfaceSample(cell.x(), cell.z(), cell.y());
                        targets.putIfAbsent(cell, target);
                    }
                }
            }
        }
        return List.copyOf(targets.values());
    }

    private record TargetCell(int x, int y, int z) {
    }
}
