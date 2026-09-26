package dev.example.copycatroller.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.paving.PreciseTrackHeightSampler;
import dev.example.copycatroller.paving.TrackProfileSideResolver;
import dev.example.copycatroller.paving.WideFillBytePlanner;
import dev.example.copycatroller.paving.WideFillBytePlanner.ByteCell;
import dev.example.copycatroller.paving.WideFillBytePlanner.HalfVoxel;
import dev.example.copycatroller.paving.WideFillBytePlanner.Seed;
import dev.example.copycatroller.paving.WideFillTraversal;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CopycatRoller.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WideFillContinuityGameTests {
    private WideFillContinuityGameTests() {
    }

    @GameTest(template = "empty")
    public static void realMovingCurveProfilesPreserveEveryPlannedByte(GameTestHelper helper) {
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos end = new BlockPos(12, 64, 12);
        Vec3 first = Vec3.atCenterOf(start);
        Vec3 last = Vec3.atCenterOf(end);
        var curve = new BezierConnection(
            Couple.create(start, end), Couple.create(first, last),
            Couple.create(new Vec3(1, 0, 0), new Vec3(0, 0, -1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
            true, false, TrackMaterial.ANDESITE
        );
        var graph = new TrackGraph();
        var edge = new TrackEdge(
            new TrackNode(new TrackNodeLocation(first).in(helper.getLevel()), 901, new Vec3(0, 1, 0)),
            new TrackNode(new TrackNodeLocation(last).in(helper.getLevel()), 902, new Vec3(0, 1, 0)),
            curve, TrackMaterial.ANDESITE
        );
        for (int lateral : new int[] {-2, 2}) {
            List<Seed> allSeeds = seeds(graph, edge, lateral, 0, edge.getLength());
            Set<ByteCell> whole = new HashSet<>(WideFillBytePlanner.plan(allSeeds, 6));
            for (int distance = 1; distance <= 12; distance++) {
                Set<HalfVoxel> band = new HashSet<>();
                for (ByteCell cell : whole) {
                    if (cell.distance() == distance) band.add(cell.voxel());
                }
                if (band.isEmpty()) helper.fail("missing band " + distance + " on side " + lateral);
                var pending = new ArrayList<HalfVoxel>();
                var firstCell = band.iterator().next();
                pending.add(firstCell);
                band.remove(firstCell);
                for (int i = 0; i < pending.size(); i++) {
                    HalfVoxel cell = pending.get(i);
                    for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        var next = new HalfVoxel(cell.halfX() + step[0], cell.halfY(), cell.halfZ() + step[1]);
                        if (band.remove(next)) pending.add(next);
                    }
                }
                if (!band.isEmpty()) helper.fail("real curve has a gap on side " + lateral
                    + " at distance " + distance + ": " + band.stream().limit(8).toList());
            }
            List<List<Seed>> windows = new ArrayList<>();
            for (double from = 0; from < edge.getLength(); from += 0.5) {
                windows.add(seeds(graph, edge, lateral, from, Math.min(from + 2, edge.getLength())));
            }
            Set<ByteCell> forward = traverse(windows);
            Collections.reverse(windows);
            for (List<Seed> window : windows) Collections.reverse(window);
            Set<ByteCell> backward = traverse(windows);
            if (!forward.equals(backward)) {
                helper.fail("reversing windows or sample order changed the curve on side " + lateral);
            }
            Set<ByteCell> missing = new HashSet<>(whole);
            missing.removeAll(forward);
            if (!missing.isEmpty()) helper.fail("moving real profiles omitted " + missing.size()
                + " Byte cells on side " + lateral + ": " + missing.stream().limit(8).toList());
            Set<ByteCell> extra = new HashSet<>(forward);
            extra.removeAll(whole);
            if (!extra.isEmpty()) helper.fail("moving real profiles added " + extra.size()
                + " extra Byte cells on side " + lateral + ": " + extra.stream().limit(8).toList());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void straightHaloRetainsEveryCoreColumn(GameTestHelper helper) {
        var graph = new TrackGraph();
        for (Vec3 direction : List.of(new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
            new Vec3(0, 0, 1), new Vec3(0, 0, -1))) {
            for (double length : new double[] {8, 8.5, 31}) {
                Vec3 first = new Vec3(0.5, 64.5, 0.5);
                Vec3 last = first.add(direction.scale(length));
                var edge = new TrackEdge(
                    new TrackNode(new TrackNodeLocation(first).in(helper.getLevel()), 911, new Vec3(0, 1, 0)),
                    new TrackNode(new TrackNodeLocation(last).in(helper.getLevel()), 912, new Vec3(0, 1, 0)),
                    null, TrackMaterial.ANDESITE
                );
                for (double from = 0; from < length; from += 0.125) {
                    PaveTask task = new PaveTask(2, 2);
                    TrackPaverV2.pave(task, graph, edge, from, Math.min(from + 2, length));
                    var core = PreciseTrackHeightSampler.samples(task, 0);
                    var window = PreciseTrackHeightSampler.samplesWithHalo(task, 0, 8);
                    for (var sample : core) {
                        if (window.samples().stream().noneMatch(candidate -> candidate.x() == sample.x()
                            && candidate.z() == sample.z())) {
                            helper.fail("straight halo lost core column " + sample.x() + "," + sample.z()
                                + " direction=" + direction + " length=" + length + " from=" + from);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    private static Set<ByteCell> traverse(List<List<Seed>> windows) {
        Set<ByteCell> traversed = new HashSet<>();
        for (List<Seed> window : windows) {
            WideFillTraversal.visitReachable(
                WideFillBytePlanner.seedVoxels(window), WideFillBytePlanner.plan(window, 6),
                cell -> { traversed.add(cell); return true; }
            );
        }
        return traversed;
    }

    private static List<Seed> seeds(TrackGraph graph, TrackEdge edge, int lateral, double from, double to) {
        PaveTask outer = new PaveTask(lateral, lateral);
        PaveTask inner = new PaveTask(lateral - Integer.signum(lateral), lateral - Integer.signum(lateral));
        TrackPaverV2.pave(outer, graph, edge, from, to);
        TrackPaverV2.pave(inner, graph, edge, from, to);
        var outerWindow = PreciseTrackHeightSampler.samplesWithHalo(outer, 0, 8);
        var innerWindow = PreciseTrackHeightSampler.samplesWithHalo(inner, 0, 8);
        List<Seed> result = new ArrayList<>();
        for (var sample : innerWindow.samples()) {
            result.add(new Seed(sample.x(), sample.z(), sample.minimumCellSurfaceY(),
                sample.tangentX(), sample.tangentZ(), false, false, false));
        }
        for (var sample : outerWindow.samples()) {
            var normal = TrackProfileSideResolver.outwardNormal(sample, innerWindow.samples());
            if (normal.isPresent()) {
                result.add(new Seed(sample.x(), sample.z(), sample.minimumCellSurfaceY(),
                    sample.tangentX(), sample.tangentZ(), normal.get().x(), normal.get().z(),
                    false, true, outerWindow.isCore(sample)));
            } else {
                result.add(new Seed(sample.x(), sample.z(), sample.minimumCellSurfaceY(),
                    sample.tangentX(), sample.tangentZ(), false, false, false));
            }
        }
        return result;
    }

}
