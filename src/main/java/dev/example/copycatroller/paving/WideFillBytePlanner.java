package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rasterizes selected lateral Wide Fill surfaces on the Copycat Byte grid.
 * Each output column is owned by its nearest track sample, so curved normals
 * cannot overlap into the central paving footprint or erase lower bands.
 */
public final class WideFillBytePlanner {
    private static final double HALF_GRID_EPSILON = 1.0e-7;
    private static final double SIDE_EPSILON = 1.0e-7;

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

        List<SourceVoxel> sources = sources(seeds);
        Set<HalfColumn> footprint = new HashSet<>();
        for (SourceVoxel source : sources) {
            footprint.add(source.column());
        }

        int maximumHalfSteps = maximumReachBlocks * 2;
        Map<HalfColumn, NearestSource> nearest = new HashMap<>();
        for (SourceVoxel source : sources) {
            for (int offsetX = -maximumHalfSteps;
                 offsetX <= maximumHalfSteps;
                 offsetX++) {
                for (int offsetZ = -maximumHalfSteps;
                     offsetZ <= maximumHalfSteps;
                     offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }

                    double squaredDistance = offsetX * offsetX
                        + offsetZ * offsetZ;
                    int distance = radialDistance(squaredDistance);
                    if (distance < 1 || distance > maximumHalfSteps) {
                        continue;
                    }

                    double side = offsetX * source.normalX()
                        + offsetZ * source.normalZ();
                    double along = offsetX * -source.normalZ()
                        + offsetZ * source.normalX();
                    double lateralMargin = Math.abs(side) - Math.abs(along);

                    HalfColumn column = new HalfColumn(
                        source.column().x() + offsetX,
                        source.column().z() + offsetZ
                    );
                    if (footprint.contains(column)) {
                        continue;
                    }

                    NearestSource candidate = new NearestSource(
                        source,
                        squaredDistance,
                        lateralMargin,
                        side,
                        distance
                    );
                    nearest.merge(
                        column,
                        candidate,
                        WideFillBytePlanner::nearer
                    );
                }
            }
        }

        List<ByteCell> ordered = new ArrayList<>(nearest.size());
        for (Map.Entry<HalfColumn, NearestSource> entry : nearest.entrySet()) {
            HalfColumn column = entry.getKey();
            NearestSource owner = entry.getValue();
            if (Math.abs(owner.side()) <= SIDE_EPSILON
                || (owner.side() > 0
                    ? !owner.source().allowPositiveLateral()
                    : !owner.source().allowNegativeLateral())) {
                continue;
            }
            ordered.add(new ByteCell(
                column.x(),
                owner.source().lowerHalfY() - owner.distance() + 1,
                column.z(),
                owner.distance()
            ));
        }
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
        for (SourceVoxel source : sources(seeds)) {
            result.add(new HalfVoxel(
                source.column().x(),
                source.lowerHalfY(),
                source.column().z()
            ));
        }
        return List.copyOf(result);
    }

    private static List<SourceVoxel> sources(List<Seed> seeds) {
        List<SourceVoxel> result = new ArrayList<>(seeds.size() * 4);
        for (Seed seed : seeds) {
            int lowerHalfY = lowerHalfY(seed.surfaceY());
            double length = Math.hypot(seed.tangentX(), seed.tangentZ());
            double normalX = -seed.tangentZ() / length;
            double normalZ = seed.tangentX() / length;
            for (int localX = 0; localX < 2; localX++) {
                for (int localZ = 0; localZ < 2; localZ++) {
                    result.add(new SourceVoxel(
                        new HalfColumn(
                            seed.blockX() * 2 + localX,
                            seed.blockZ() * 2 + localZ
                        ),
                        lowerHalfY,
                        normalX,
                        normalZ,
                        seed.allowNegativeLateral(),
                        seed.allowPositiveLateral()
                    ));
                }
            }
        }
        return result;
    }

    private static NearestSource nearer(
        NearestSource current,
        NearestSource candidate
    ) {
        if (Math.abs(candidate.squaredDistance() - current.squaredDistance())
            > HALF_GRID_EPSILON) {
            return candidate.squaredDistance() < current.squaredDistance()
                ? candidate
                : current;
        }
        if (Math.abs(candidate.lateralMargin() - current.lateralMargin())
            > HALF_GRID_EPSILON) {
            return candidate.lateralMargin() > current.lateralMargin()
                ? candidate
                : current;
        }
        return candidate.source().lowerHalfY() > current.source().lowerHalfY()
            ? candidate
            : current;
    }

    private static int radialDistance(double squaredDistance) {
        return (int) Math.floor(
            Math.sqrt(squaredDistance) + 0.5 + HALF_GRID_EPSILON
        );
    }

    private static int lowerHalfY(double surfaceY) {
        return (int) Math.floor(
            (surfaceY + 1.0) * 2.0 + HALF_GRID_EPSILON
        ) - 1;
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

    private record SourceVoxel(
        HalfColumn column,
        int lowerHalfY,
        double normalX,
        double normalZ,
        boolean allowNegativeLateral,
        boolean allowPositiveLateral
    ) {
    }

    private record NearestSource(
        SourceVoxel source,
        double squaredDistance,
        double lateralMargin,
        double side,
        int distance
    ) {
    }
}
