package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.trains.graph.TrackEdge;
import dev.example.copycatroller.CopycatRoller;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.track.BezierConnection;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Side-channel for the unquantized Y values that {@link TrackPaverV2} otherwise
 * reduces to an integer (straight track) or half block (Bezier track).
 */
public final class PreciseTrackHeightSampler {
    private static final Map<PaveTask, CaptureData> CAPTURES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private PreciseTrackHeightSampler() {
    }

    public static void capture(
        PaveTask task,
        TrackGraph graph,
        TrackEdge edge,
        double from,
        double to
    ) {
        long sectionKey = sectionKey(edge);
        synchronized (CAPTURES) {
            CaptureData data = CAPTURES.computeIfAbsent(
                task,
                ignored -> new CaptureData(
                    new ArrayList<>(),
                    new HashMap<>()
                )
            );
            data.segments().add(new CaptureSegment(
                graph,
                edge,
                from,
                to
            ));
        }
        if (edge.isTurn()) {
            captureCurve(task, edge.getTurn(), from, to, sectionKey);
        } else {
            captureStraight(task, graph, edge, from, to, sectionKey);
        }
    }

    public static List<TrackSurfaceSample> samples(PaveTask task, int rollerLocalY) {
        Map<ColumnKey, HeightCapture> captured;
        synchronized (CAPTURES) {
            CaptureData data = CAPTURES.get(task);
            captured = data == null ? null : Map.copyOf(data.heights());
        }

        if (task.keys().isEmpty()) {
            return List.of();
        }
        if (captured == null) {
            throw new IllegalStateException("Create returned a track paving profile without precise Y samples");
        }

        List<TrackSurfaceSample> result = new ArrayList<>(task.keys().size());
        int recoveredSamples = 0;
        for (Couple<Integer> coordinates : task.keys()) {
            ColumnKey key = new ColumnKey(coordinates.getFirst(), coordinates.getSecond());
            HeightCapture capture = captured.get(key);
            if (capture == null) {
                capture = recoverMissingHeight(task, coordinates, captured);
                recoveredSamples++;
            }
            result.add(new TrackSurfaceSample(
                key.x,
                key.z,
                capture.y + rollerLocalY,
                capture.tangentX,
                capture.tangentZ,
                capture.gradientX,
                capture.gradientZ,
                capture.station,
                capture.sectionKey
            ));
        }
        if (recoveredSamples > 0) {
            CopycatRoller.LOGGER.warn(
                "Recovered {} precise track sample(s) from Create's quantized profile",
                recoveredSamples
            );
        }
        result.sort(Comparator.comparingInt(TrackSurfaceSample::x).thenComparingInt(TrackSurfaceSample::z));
        return List.copyOf(result);
    }

    public static ProfileWindow samplesWithHalo(
        PaveTask task,
        int rollerLocalY,
        double haloDistance
    ) {
        if (haloDistance < 0 || !Double.isFinite(haloDistance)) {
            throw new IllegalArgumentException("haloDistance must be finite and non-negative");
        }
        List<CaptureSegment> segments;
        synchronized (CAPTURES) {
            CaptureData data = CAPTURES.get(task);
            segments = data == null ? null : List.copyOf(data.segments());
        }
        if (segments == null) {
            throw new IllegalStateException("Create returned a track paving profile without capture metadata");
        }

        List<TrackSurfaceSample> coreSamples = samples(task, rollerLocalY);
        if (coreSamples.isEmpty() || haloDistance == 0) {
            return ProfileWindow.core(coreSamples);
        }
        Set<Long> coreColumns = new HashSet<>();
        for (TrackSurfaceSample sample : coreSamples) {
            coreColumns.add(columnId(sample.x(), sample.z()));
        }

        PaveTask expanded = new PaveTask(
            task.getHorizontalInterval().getFirst(),
            task.getHorizontalInterval().getSecond()
        );
        for (CaptureSegment segment : segments) {
            double edgeLength = segment.edge().getLength();
            double expandedFrom = Math.max(
                0,
                Math.min(segment.from(), segment.to()) - haloDistance
            );
            double expandedTo = Math.min(
                edgeLength,
                Math.max(segment.from(), segment.to()) + haloDistance
            );
            int capturesBefore = captureCount(expanded);
            TrackPaverV2.pave(
                expanded,
                segment.graph(),
                segment.edge(),
                expandedFrom,
                expandedTo
            );
            // Production reaches capture through the required TrackPaver
            // Mixin. Keep a no-Mixin fallback for isolated tests without doing
            // the expensive geometry pass twice on a running server.
            if (captureCount(expanded) == capturesBefore) {
                capture(
                    expanded,
                    segment.graph(),
                    segment.edge(),
                    expandedFrom,
                    expandedTo
                );
            }
        }
        return new ProfileWindow(
            samples(expanded, rollerLocalY),
            Set.copyOf(coreColumns)
        );
    }

    private static int captureCount(PaveTask task) {
        synchronized (CAPTURES) {
            CaptureData data = CAPTURES.get(task);
            return data == null ? 0 : data.segments().size();
        }
    }

    private static void captureStraight(
        PaveTask task,
        TrackGraph graph,
        TrackEdge edge,
        double from,
        double to,
        long sectionKey
    ) {
        Vec3 location1 = edge.node1.getLocation().getLocation();
        Vec3 location2 = edge.node2.getLocation().getLocation();
        Vec3 difference = location2.subtract(location1);
        Vec3 direction = VecHelper.clampComponentWise(difference, 1);
        int extent = (int) Math.round((to - from) / direction.length());
        double length = edge.getLength();
        boolean levelEdge = Math.abs(difference.y) < 1.0e-12;
        double verticalOffset = levelEdge ? 0.5 : 1.0;

        Vec3 rawStart = edge
            .getPosition(graph, Mth.clamp(from, 1 / 16f, length - 1 / 16f) / length)
            .subtract(0, verticalOffset, 0);
        BlockPos startPos = BlockPos.containing(rawStart);

        PaveTask coverage = new PaveTask(
            task.getHorizontalInterval().getFirst(),
            task.getHorizontalInterval().getSecond()
        );
        TrackPaverV2.paveStraight(coverage, startPos, direction, extent);

        double horizontalLengthSquared =
            difference.x * difference.x + difference.z * difference.z;
        double gradientX = horizontalLengthSquared < 1.0e-12
            ? 0
            : difference.y * difference.x / horizontalLengthSquared;
        double gradientZ = horizontalLengthSquared < 1.0e-12
            ? 0
            : difference.y * difference.z / horizontalLengthSquared;
        for (Couple<Integer> coordinates : coverage.keys()) {
            double t;
            if (horizontalLengthSquared < 1.0e-12) {
                t = Mth.clamp(from / length, 0, 1);
            } else {
                double centerX = coordinates.getFirst() + 0.5;
                double centerZ = coordinates.getSecond() + 0.5;
                t = ((centerX - location1.x) * difference.x
                    + (centerZ - location1.z) * difference.z) / horizontalLengthSquared;
                t = Mth.clamp(t, 0, 1);
            }
            double preciseY = edge.getPosition(graph, t).y - verticalOffset;
            /*
             * On a level straight, Create's 0.5 offset is a BlockPos selection
             * bias, not a request for a slab above the paved base. Preserve
             * Create's integer base there; only a real vertical edge delta
             * contributes fractional surface height.
             */
            if (levelEdge) {
                preciseY = Math.floor(preciseY);
            }
            putMinimum(
                task,
                coordinates.getFirst(),
                coordinates.getSecond(),
                preciseY,
                difference.x,
                difference.z,
                levelEdge ? 0 : gradientX,
                levelEdge ? 0 : gradientZ,
                t * length,
                sectionKey
            );
        }
    }

    private static void captureCurve(
        PaveTask task,
        BezierConnection curve,
        double from,
        double to,
        long sectionKey
    ) {
        PaveTask coverage = new PaveTask(
            task.getHorizontalInterval().getFirst(),
            task.getHorizontalInterval().getSecond()
        );
        TrackPaverV2.paveCurve(coverage, curve, from, to);
        if (coverage.keys().isEmpty()) {
            return;
        }

        BlockPos blockEntityPosition = curve.bePositions.getFirst();
        double radius = -task.getHorizontalInterval().getFirst();
        double radiusInner = radius - 0.575;
        double radiusOuter = radius + 0.575;

        double handleLength = curve.getHandleLength();
        Vec3 start = curve.starts.getFirst()
            .subtract(Vec3.atLowerCornerOf(blockEntityPosition))
            .add(0, 3 / 16f, 0);
        Vec3 end = curve.starts.getSecond()
            .subtract(Vec3.atLowerCornerOf(blockEntityPosition))
            .add(0, 3 / 16f, 0);
        Vec3 startHandle = curve.axes.getFirst().scale(handleLength).add(start);
        Vec3 endHandle = curve.axes.getSecond().scale(handleLength).add(end);
        Vec3 startNormal = curve.normals.getFirst();
        Vec3 endNormal = curve.normals.getSecond();

        int segmentCount = curve.getSegmentCount();
        float[] lut = curve.getStepLUT();
        double localFrom = from / curve.getLength();
        double localTo = to / curve.getLength();

        for (int segment = 0; segment < segmentCount; segment++) {
            float t = segment * lut[segment] / segmentCount;
            float t1 = segment + 1 == segmentCount
                ? 1
                : (segment + 1) * lut[segment + 1] / segmentCount;
            if (t1 < localFrom || t > localTo) {
                continue;
            }

            Vec3 first = curvePoint(
                start, end, startHandle, endHandle, startNormal, endNormal, t
            );
            Vec3 second = curvePoint(
                start, end, startHandle, endHandle, startNormal, endNormal, t1
            );
            Vec3 firstHorizontalNormal = horizontalNormal(
                start, end, startHandle, endHandle, startNormal, endNormal, t
            );
            Vec3 secondHorizontalNormal = horizontalNormal(
                start, end, startHandle, endHandle, startNormal, endNormal, t1
            );
            Vec3 tangent = second.subtract(first);
            double horizontalLengthSquared =
                tangent.x * tangent.x + tangent.z * tangent.z;
            if (horizontalLengthSquared < 1.0e-12) {
                continue;
            }
            double gradientX = tangent.y * tangent.x / horizontalLengthSquared;
            double gradientZ = tangent.y * tangent.z / horizontalLengthSquared;

            Vec2 a = vec2(first.add(firstHorizontalNormal.scale(radiusOuter)));
            Vec2 b = vec2(second.add(secondHorizontalNormal.scale(radiusOuter)));
            Vec2 c = vec2(second.add(secondHorizontalNormal.scale(radiusInner)));
            Vec2 d = vec2(first.add(firstHorizontalNormal.scale(radiusInner)));
            double y = (first.y + second.y) / 2.0 + blockEntityPosition.getY();

            for (Couple<Integer> coordinates : coverage.keys()) {
                int localX = coordinates.getFirst() - blockEntityPosition.getX();
                int localZ = coordinates.getSecond() - blockEntityPosition.getZ();
                Vec2 center = new Vec2(localX + 0.5f, localZ + 0.5f);
                if (!isInTriangle(a, b, c, center) && !isInTriangle(a, c, d, center)) {
                    continue;
                }
                putMinimum(
                    task,
                    coordinates.getFirst(),
                    coordinates.getSecond(),
                    y,
                    tangent.x,
                    tangent.z,
                    gradientX,
                    gradientZ,
                    (t + t1) * 0.5 * curve.getLength(),
                    sectionKey
                );
            }
        }
    }

    private static Vec3 curvePoint(
        Vec3 start,
        Vec3 end,
        Vec3 startHandle,
        Vec3 endHandle,
        Vec3 startNormal,
        Vec3 endNormal,
        float t
    ) {
        Vec3 point = VecHelper.bezier(start, end, startHandle, endHandle, t);
        Vec3 normal = startNormal.equals(endNormal)
            ? startNormal
            : VecHelper.slerp(t, startNormal, endNormal);
        return point.add(normal.scale(-1.175f));
    }

    private static Vec3 horizontalNormal(
        Vec3 start,
        Vec3 end,
        Vec3 startHandle,
        Vec3 endHandle,
        Vec3 startNormal,
        Vec3 endNormal,
        float t
    ) {
        Vec3 normal = startNormal.equals(endNormal)
            ? startNormal
            : VecHelper.slerp(t, startNormal, endNormal);
        return normal.cross(
            VecHelper.bezierDerivative(start, end, startHandle, endHandle, t).normalize()
        ).normalize();
    }

    private static Vec2 vec2(Vec3 vector) {
        return new Vec2((float) vector.x, (float) vector.z);
    }

    private static boolean isInTriangle(Vec2 a, Vec2 b, Vec2 c, Vec2 point) {
        float pcx = point.x - c.x;
        float pcy = point.y - c.y;
        float cbx = c.x - b.x;
        float bcy = b.y - c.y;
        float determinant = bcy * (a.x - c.x) + cbx * (a.y - c.y);
        float s = bcy * pcx + cbx * pcy;
        float t = (c.y - a.y) * pcx + (a.x - c.x) * pcy;
        return determinant < 0
            ? s <= 0 && t <= 0 && s + t >= determinant
            : s >= 0 && t >= 0 && s + t <= determinant;
    }

    private static HeightCapture recoverMissingHeight(
        PaveTask task,
        Couple<Integer> coordinates,
        Map<ColumnKey, HeightCapture> captured
    ) {
        int x = coordinates.getFirst();
        int z = coordinates.getSecond();
        Map.Entry<ColumnKey, HeightCapture> nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (Map.Entry<ColumnKey, HeightCapture> entry : captured.entrySet()) {
            long deltaX = (long) x - entry.getKey().x();
            long deltaZ = (long) z - entry.getKey().z();
            long distance = deltaX * deltaX + deltaZ * deltaZ;
            if (distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }

        double createY = task.get(coordinates);
        if (nearest == null) {
            return new HeightCapture(createY, 1, 0, 0, 0, 0, 0);
        }

        ColumnKey sourcePosition = nearest.getKey();
        HeightCapture source = nearest.getValue();
        double deltaX = x - sourcePosition.x();
        double deltaZ = z - sourcePosition.z();
        double predictedY = source.y()
            + source.gradientX() * deltaX
            + source.gradientZ() * deltaZ;
        predictedY += createY - quantizeLikeCreate(predictedY);

        double tangentLength = Math.hypot(source.tangentX(), source.tangentZ());
        double station = source.station();
        if (tangentLength > 1.0e-12) {
            station += (
                deltaX * source.tangentX() + deltaZ * source.tangentZ()
            ) / tangentLength;
        }
        return new HeightCapture(
            predictedY,
            source.tangentX(),
            source.tangentZ(),
            source.gradientX(),
            source.gradientZ(),
            station,
            source.sectionKey()
        );
    }

    private static double quantizeLikeCreate(double y) {
        double base = Math.floor(y);
        return base + (y - base >= 0.5 ? 0.5 : 0);
    }

    private static long sectionKey(TrackEdge edge) {
        long first = vectorKey(edge.node1.getLocation().getLocation());
        long second = vectorKey(edge.node2.getLocation().getLocation());
        if (Long.compareUnsigned(first, second) > 0) {
            long swap = first;
            first = second;
            second = swap;
        }
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ first) * 0x100000001b3L;
        hash = (hash ^ second) * 0x100000001b3L;
        return (hash ^ (edge.isTurn() ? 1 : 0)) * 0x100000001b3L;
    }

    private static long vectorKey(Vec3 position) {
        long hash = 0xcbf29ce484222325L;
        hash = (hash ^ Double.doubleToLongBits(position.x)) * 0x100000001b3L;
        hash = (hash ^ Double.doubleToLongBits(position.y)) * 0x100000001b3L;
        return (hash ^ Double.doubleToLongBits(position.z)) * 0x100000001b3L;
    }

    private static long columnId(int x, int z) {
        return (long) x << 32 ^ z & 0xffffffffL;
    }

    private static void putMinimum(
        PaveTask task,
        int x,
        int z,
        double y,
        double tangentX,
        double tangentZ,
        double gradientX,
        double gradientZ,
        double station,
        long sectionKey
    ) {
        HeightCapture next = new HeightCapture(
            y,
            tangentX,
            tangentZ,
            gradientX,
            gradientZ,
            station,
            sectionKey
        );
        synchronized (CAPTURES) {
            CaptureData data = CAPTURES.get(task);
            if (data == null) {
                throw new IllegalStateException("capture metadata disappeared during track sampling");
            }
            data.heights().merge(
                new ColumnKey(x, z),
                next,
                (current, candidate) -> candidate.y < current.y ? candidate : current
            );
        }
    }

    private record ColumnKey(int x, int z) {
    }

    private record HeightCapture(
        double y,
        double tangentX,
        double tangentZ,
        double gradientX,
        double gradientZ,
        double station,
        long sectionKey
    ) {
    }

    private record CaptureSegment(
        TrackGraph graph,
        TrackEdge edge,
        double from,
        double to
    ) {
    }

    private record CaptureData(
        List<CaptureSegment> segments,
        Map<ColumnKey, HeightCapture> heights
    ) {
    }

    public record ProfileWindow(
        List<TrackSurfaceSample> samples,
        Set<Long> coreColumns
    ) {
        public ProfileWindow {
            samples = List.copyOf(samples);
            coreColumns = Set.copyOf(coreColumns);
        }

        public static ProfileWindow core(List<TrackSurfaceSample> samples) {
            Set<Long> columns = new HashSet<>();
            for (TrackSurfaceSample sample : samples) {
                columns.add(columnId(sample.x(), sample.z()));
            }
            return new ProfileWindow(samples, columns);
        }

        public boolean isCore(TrackSurfaceSample sample) {
            return coreColumns.contains(columnId(sample.x(), sample.z()));
        }
    }
}
