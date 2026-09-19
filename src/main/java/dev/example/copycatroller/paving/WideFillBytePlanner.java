package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rasterizes selected lateral sides of Wide Fill on a half-block grid.
 * The first cell beside the track keeps the track surface height; every
 * following horizontal half-step lowers the surface by one half-block.
 */
public final class WideFillBytePlanner {
    private static final double HALF_GRID_EPSILON = 1.0e-7;

    private WideFillBytePlanner() {
    }

    public static int reachBlocksForCreateDepth(int createRollerFillDepth) {
        if (createRollerFillDepth < 0) {
            throw new IllegalArgumentException("createRollerFillDepth must not be negative");
        }
        return (createRollerFillDepth + 1) / 2;
    }

    public static List<ByteCell> plan(
        List<Seed> seeds,
        int maximumReachBlocks
    ) {
        if (seeds.isEmpty() || maximumReachBlocks == 0) {
            return List.of();
        }
        if (maximumReachBlocks < 0) {
            throw new IllegalArgumentException("maximumReachBlocks must not be negative");
        }

        Map<HalfColumn, Integer> seedLowerY = new HashMap<>();
        List<SeedVoxel> seedVoxels = new ArrayList<>();
        for (Seed seed : seeds) {
            int lowerHalfY = lowerHalfY(seed.surfaceY());
            boolean lateralAlongX = Math.abs(seed.tangentZ())
                > Math.abs(seed.tangentX());
            for (int localX = 0; localX < 2; localX++) {
                for (int localZ = 0; localZ < 2; localZ++) {
                    HalfColumn column = new HalfColumn(
                        seed.blockX() * 2 + localX,
                        seed.blockZ() * 2 + localZ
                    );
                    seedLowerY.merge(column, lowerHalfY, Math::max);
                    seedVoxels.add(new SeedVoxel(
                        column,
                        lowerHalfY,
                        lateralAlongX,
                        seed.allowNegativeLateral(),
                        seed.allowPositiveLateral()
                    ));
                }
            }
        }

        int maximumHalfSteps = maximumReachBlocks * 2;
        Map<HalfColumn, ByteCell> result = new HashMap<>();
        Set<HalfColumn> footprint = Set.copyOf(seedLowerY.keySet());
        for (SeedVoxel seed : seedVoxels) {
            for (int sign : new int[] {-1, 1}) {
                if (sign < 0 && !seed.allowNegativeLateral()
                    || sign > 0 && !seed.allowPositiveLateral()) {
                    continue;
                }
                for (int step = 1; step <= maximumHalfSteps; step++) {
                    int halfX = seed.column().x()
                        + (seed.lateralAlongX() ? sign * step : 0);
                    int halfZ = seed.column().z()
                        + (seed.lateralAlongX() ? 0 : sign * step);
                    HalfColumn column = new HalfColumn(halfX, halfZ);
                    if (footprint.contains(column)) {
                        continue;
                    }

                    ByteCell candidate = new ByteCell(
                        halfX,
                        seed.lowerHalfY() - step + 1,
                        halfZ,
                        step
                    );
                    result.merge(column, candidate, WideFillBytePlanner::higher);
                }
            }
        }

        List<ByteCell> ordered = new ArrayList<>(result.values());
        ordered.sort((left, right) -> {
            int comparison = Integer.compare(left.distance(), right.distance());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(left.halfX(), right.halfX());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(left.halfZ(), right.halfZ());
            if (comparison != 0) return comparison;
            return Integer.compare(left.lowerHalfY(), right.lowerHalfY());
        });
        return List.copyOf(ordered);
    }

    public static List<HalfVoxel> seedVoxels(List<Seed> seeds) {
        Set<HalfVoxel> result = new HashSet<>();
        for (Seed seed : seeds) {
            int lowerHalfY = lowerHalfY(seed.surfaceY());
            for (int localX = 0; localX < 2; localX++) {
                for (int localZ = 0; localZ < 2; localZ++) {
                    result.add(new HalfVoxel(
                        seed.blockX() * 2 + localX,
                        lowerHalfY,
                        seed.blockZ() * 2 + localZ
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    private static int lowerHalfY(double surfaceY) {
        return (int) Math.floor(
            (surfaceY + 1.0) * 2.0 + HALF_GRID_EPSILON
        ) - 1;
    }

    private static ByteCell higher(ByteCell current, ByteCell candidate) {
        if (candidate.lowerHalfY() != current.lowerHalfY()) {
            return candidate.lowerHalfY() > current.lowerHalfY()
                ? candidate
                : current;
        }
        return candidate.distance() < current.distance() ? candidate : current;
    }

    public record Seed(
        int blockX,
        int blockZ,
        double surfaceY,
        double tangentX,
        double tangentZ,
        boolean allowNegativeLateral,
        boolean allowPositiveLateral
    ) {
        public Seed(
            int blockX,
            int blockZ,
            double surfaceY,
            double tangentX,
            double tangentZ
        ) {
            this(blockX, blockZ, surfaceY, tangentX, tangentZ, true, true);
        }

        public Seed(int blockX, int blockZ, double surfaceY) {
            this(blockX, blockZ, surfaceY, 1, 0, true, true);
        }

        public Seed {
            if (!Double.isFinite(surfaceY)
                || !Double.isFinite(tangentX)
                || !Double.isFinite(tangentZ)) {
                throw new IllegalArgumentException("seed values must be finite");
            }
            if (Math.abs(tangentX) < 1.0e-9
                && Math.abs(tangentZ) < 1.0e-9) {
                throw new IllegalArgumentException("horizontal tangent must not be zero");
            }
        }
    }

    public record ByteCell(
        int halfX,
        int lowerHalfY,
        int halfZ,
        int distance
    ) {
        public ByteCell {
            if (distance < 1) {
                throw new IllegalArgumentException("distance must be positive");
            }
        }

        public HalfVoxel voxel() {
            return new HalfVoxel(halfX, lowerHalfY, halfZ);
        }
    }

    public record HalfVoxel(int halfX, int halfY, int halfZ) {
    }

    private record HalfColumn(int x, int z) {
    }

    private record SeedVoxel(
        HalfColumn column,
        int lowerHalfY,
        boolean lateralAlongX,
        boolean allowNegativeLateral,
        boolean allowPositiveLateral
    ) {
    }
}