package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.trains.graph.TrackEdge;
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
    private static final Map<PaveTask, Map<ColumnKey, Double>> CAPTURES =
        Collections.synchronizedMap(new WeakHashMap<>());

    private PreciseTrackHeightSampler() {
    }

    public static void capture(PaveTask task, TrackGraph graph, TrackEdge edge, double from, double to) {
        if (edge.isTurn()) {
            captureCurve(task, edge.getTurn(), from, to);
        } else {
            captureStraight(task, graph, edge, from, to);
        }
    }

    public static List<TrackSurfaceSample> samples(PaveTask task, int rollerLocalY) {
        Map<ColumnKey, Double> captured;
        synchronized (CAPTURES) {
            captured = CAPTURES.get(task);
            if (captured != null) {
                captured = Map.copyOf(captured);
            }
        }

        if (task.keys().isEmpty()) {
            return List.of();
        }
        if (captured == null) {
            throw new IllegalStateException("Create returned a track paving profile without precise Y samples");
        }

        List<TrackSurfaceSample> result = new ArrayList<>(task.keys().size());
        for (Couple<Integer> coordinates : task.keys()) {
            ColumnKey key = new ColumnKey(coordinates.getFirst(), coordinates.getSecond());
            Double y = captured.get(key);
            if (y == null) {
                throw new IllegalStateException("Missing precise track height for " + key.x + ", " + key.z);
            }
            result.add(new TrackSurfaceSample(key.x, key.z, y + rollerLocalY));
        }
        result.sort(Comparator.comparingInt(TrackSurfaceSample::x).thenComparingInt(TrackSurfaceSample::z));
        return List.copyOf(result);
    }

    private static void captureStraight(PaveTask task, TrackGraph graph, TrackEdge edge, double from, double to) {
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
            putMinimum(task, coordinates.getFirst(), coordinates.getSecond(), preciseY);
        }
    }

    private static void captureCurve(PaveTask task, BezierConnection curve, double from, double to) {
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
                putMinimum(task, coordinates.getFirst(), coordinates.getSecond(), y);
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

    private static void putMinimum(PaveTask task, int x, int z, double y) {
        synchronized (CAPTURES) {
            CAPTURES
                .computeIfAbsent(task, ignored -> new HashMap<>())
                .merge(new ColumnKey(x, z), y, Math::min);
        }
    }

    private record ColumnKey(int x, int z) {
    }
}
