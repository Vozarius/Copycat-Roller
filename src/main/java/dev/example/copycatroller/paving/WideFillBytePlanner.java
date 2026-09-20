package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rasterizes selected lateral Wide Fill surfaces on the Copycat Byte grid.
 * The union of every Roller profile defines the protected centre and only its
 * real exterior boundary may emit. Each output column is owned by its nearest
 * track sample; read-only halo sources shape the field but never own output.
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
        sources = sources.stream()
            .map(source -> isOutwardBoundary(source, footprint)
                ? source
                : source.withoutEmission())
            .toList();

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
                        along,
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

        Map<HalfColumn, NearestSource> allowed = new HashMap<>();
        Map<HalfColumn, NearestSource> selected = new HashMap<>();
        for (Map.Entry<HalfColumn, NearestSource> entry : nearest.entrySet()) {
            NearestSource owner = entry.getValue();
            if (Math.abs(owner.side()) <= SIDE_EPSILON
                || (owner.side() > 0
                    ? !owner.source().allowPositiveLateral()
                    : !owner.source().allowNegativeLateral())) {
                continue;
            }
            allowed.put(entry.getKey(), owner);
            if (!extendsPastOpenEnd(owner, sources)) {
                selected.put(entry.getKey(), owner);
            }
        }

        List<Map.Entry<HalfColumn, NearestSource>> initial =
            new ArrayList<>(selected.entrySet());
        initial.sort(Comparator.comparingInt(
            entry -> entry.getValue().distance()
        ));
        for (Map.Entry<HalfColumn, NearestSource> entry : initial) {
            if (!ensureSupported(
                entry.getKey(),
                entry.getValue(),
                selected,
                allowed,
                sources
            )) {
                selected.remove(entry.getKey());
            }
        }

        List<ByteCell> ordered = new ArrayList<>(selected.size());
        for (Map.Entry<HalfColumn, NearestSource> entry : selected.entrySet()) {
            HalfColumn column = entry.getKey();
            NearestSource owner = entry.getValue();
            if (!owner.source().outputOwner()) {
                continue;
            }
            ordered.add(new ByteCell(
                column.x(),
                outputLowerHalfY(owner),
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

    private static boolean ensureSupported(
        HalfColumn column,
        NearestSource owner,
        Map<HalfColumn, NearestSource> selected,
        Map<HalfColumn, NearestSource> allowed,
        List<SourceVoxel> sources
    ) {
        if (owner.distance() == 1) {
            return true;
        }

        int requiredY = outputLowerHalfY(owner) + 1;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                NearestSource parent = selected.get(new HalfColumn(
                    column.x() + offsetX,
                    column.z() + offsetZ
                ));
                if (isParent(parent, owner.distance() - 1, requiredY)) {
                    return true;
                }
            }
        }

        HalfColumn bestColumn = null;
        NearestSource best = null;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                HalfColumn candidateColumn = new HalfColumn(
                    column.x() + offsetX,
                    column.z() + offsetZ
                );
                NearestSource candidate = allowed.get(candidateColumn);
                if (!isParent(candidate, owner.distance() - 1, requiredY)
                    || !betterSupport(candidate, best, sources)) {
                    continue;
                }
                bestColumn = candidateColumn;
                best = candidate;
            }
        }
        if (best == null
            || !ensureSupported(
                bestColumn,
                best,
                selected,
                allowed,
                sources
            )) {
            return false;
        }
        selected.put(bestColumn, best);
        return true;
    }

    private static boolean isParent(
        NearestSource candidate,
        int distance,
        int lowerHalfY
    ) {
        return candidate != null
            && candidate.distance() == distance
            && outputLowerHalfY(candidate) == lowerHalfY;
    }

    private static boolean betterSupport(
        NearestSource candidate,
        NearestSource current,
        List<SourceVoxel> sources
    ) {
        if (current == null) {
            return true;
        }
        boolean candidateClipped = extendsPastOpenEnd(candidate, sources);
        boolean currentClipped = extendsPastOpenEnd(current, sources);
        if (candidateClipped != currentClipped) {
            return !candidateClipped;
        }
        if (Math.abs(
            candidate.lateralMargin() - current.lateralMargin()
        ) > HALF_GRID_EPSILON) {
            return candidate.lateralMargin() > current.lateralMargin();
        }
        return candidate.squaredDistance() < current.squaredDistance();
    }

    private static int outputLowerHalfY(NearestSource owner) {
        return owner.source().lowerHalfY() - owner.distance() + 1;
    }

    private static boolean extendsPastOpenEnd(
        NearestSource owner,
        List<SourceVoxel> sources
    ) {
        // A PaveTask contains only a short moving window. Clip its unsupported
        // caps, not the interior distance contour, or curves become separate
        // rays emitted by every sampled track cell.
        if (Math.abs(owner.along()) <= 0.75 + HALF_GRID_EPSILON) {
            return false;
        }
        return !hasLongitudinalSupport(
            owner.source(),
            sources,
            Math.signum(owner.along())
        );
    }

    private static boolean hasLongitudinalSupport(
        SourceVoxel source,
        List<SourceVoxel> sources,
        double direction
    ) {
        for (SourceVoxel candidate : sources) {
            if (candidate.column().equals(source.column())) {
                continue;
            }
            if (!sharesEmittingSide(source, candidate)) {
                continue;
            }
            int offsetX = candidate.column().x() - source.column().x();
            int offsetZ = candidate.column().z() - source.column().z();
            double along = (
                offsetX * -source.normalZ()
                    + offsetZ * source.normalX()
            ) * direction;
            double side = offsetX * source.normalX()
                + offsetZ * source.normalZ();
            if (along > 0.5 - HALF_GRID_EPSILON
                && along <= 2.5 + HALF_GRID_EPSILON
                && Math.abs(side) <= 1.5 + HALF_GRID_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesEmittingSide(
        SourceVoxel first,
        SourceVoxel second
    ) {
        return first.allowNegativeLateral() && second.allowNegativeLateral()
            || first.allowPositiveLateral() && second.allowPositiveLateral();
    }

    private static boolean isOutwardBoundary(
        SourceVoxel source,
        Set<HalfColumn> footprint
    ) {
        if (!source.allowNegativeLateral()
            && !source.allowPositiveLateral()) {
            return false;
        }
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                HalfColumn neighbour = new HalfColumn(
                    source.column().x() + offsetX,
                    source.column().z() + offsetZ
                );
                if (footprint.contains(neighbour)) {
                    continue;
                }
                double side = offsetX * source.normalX()
                    + offsetZ * source.normalZ();
                double along = offsetX * -source.normalZ()
                    + offsetZ * source.normalX();
                boolean allowedSide = side > SIDE_EPSILON
                    ? source.allowPositiveLateral()
                    : side < -SIDE_EPSILON
                        && source.allowNegativeLateral();
                if (allowedSide
                    && Math.abs(side) + SIDE_EPSILON >= Math.abs(along)) {
                    return true;
                }
            }
        }
        return false;
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
            double normalLength = Math.hypot(seed.normalX(), seed.normalZ());
            double normalX = seed.normalX() / normalLength;
            double normalZ = seed.normalZ() / normalLength;
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
                        seed.allowPositiveLateral(),
                        seed.outputOwner()
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
        double normalX,
        double normalZ,
        boolean allowNegativeLateral,
        boolean allowPositiveLateral,
        boolean outputOwner
    ) {
        public Seed(
            int blockX,
            int blockZ,
            double surfaceY,
            double tangentX,
            double tangentZ,
            double normalX,
            double normalZ,
            boolean allowNegativeLateral,
            boolean allowPositiveLateral
        ) {
            this(
                blockX,
                blockZ,
                surfaceY,
                tangentX,
                tangentZ,
                normalX,
                normalZ,
                allowNegativeLateral,
                allowPositiveLateral,
                true
            );
        }

        public Seed(
            int blockX,
            int blockZ,
            double surfaceY,
            double tangentX,
            double tangentZ,
            boolean allowNegativeLateral,
            boolean allowPositiveLateral,
            boolean outputOwner
        ) {
            this(
                blockX,
                blockZ,
                surfaceY,
                tangentX,
                tangentZ,
                -tangentZ,
                tangentX,
                allowNegativeLateral,
                allowPositiveLateral,
                outputOwner
            );
        }

        public Seed(
            int blockX,
            int blockZ,
            double surfaceY,
            double tangentX,
            double tangentZ,
            boolean allowNegativeLateral,
            boolean allowPositiveLateral
        ) {
            this(
                blockX,
                blockZ,
                surfaceY,
                tangentX,
                tangentZ,
                allowNegativeLateral,
                allowPositiveLateral,
                true
            );
        }

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
                || !Double.isFinite(tangentZ)
                || !Double.isFinite(normalX)
                || !Double.isFinite(normalZ)) {
                throw new IllegalArgumentException("seed values must be finite");
            }
            if (Math.abs(tangentX) < 1.0e-9
                && Math.abs(tangentZ) < 1.0e-9) {
                throw new IllegalArgumentException("horizontal tangent must not be zero");
            }
            if (Math.abs(normalX) < 1.0e-9
                && Math.abs(normalZ) < 1.0e-9) {
                throw new IllegalArgumentException("horizontal normal must not be zero");
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
        boolean allowPositiveLateral,
        boolean outputOwner
    ) {
        private SourceVoxel withoutEmission() {
            return new SourceVoxel(
                column,
                lowerHalfY,
                normalX,
                normalZ,
                false,
                false,
                outputOwner
            );
        }
    }

    private record NearestSource(
        SourceVoxel source,
        double squaredDistance,
        double lateralMargin,
        double side,
        double along,
        int distance
    ) {
    }
}
