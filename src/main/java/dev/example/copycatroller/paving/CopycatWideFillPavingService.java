package dev.example.copycatroller.paving;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.example.copycatroller.paving.CopycatLayerPavingService.PlacementResult;
import dev.example.copycatroller.paving.PreciseTrackHeightSampler.ProfileWindow;
import dev.example.copycatroller.paving.RollerEdgeSelection.EdgeSides;
import dev.example.copycatroller.paving.WideFillBytePlanner.ByteCell;
import dev.example.copycatroller.paving.WideFillBytePlanner.HalfVoxel;
import dev.example.copycatroller.paving.WideFillBytePlanner.Seed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Zinc-only Wide Fill compatibility. Every enabled Wide Fill Roller in the
 * lateral row contributes its exact track footprint to the protected central
 * mask. Only the two edge Rollers emit a one-Byte-thick slope, outward only.
 */
public final class CopycatWideFillPavingService {
    private static final double DIRECTION_EPSILON = 1.0e-7;

    private CopycatWideFillPavingService() {
    }

    public static boolean pave(
        MovementContext context,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile,
        TrackProfileProvider profileProvider
    ) {
        if (context.world.isClientSide || context.contraption == null) {
            return false;
        }

        Direction localFacing = context.state.getValue(RollerBlock.FACING);
        // Build the complete row footprint synchronously. No MovementContext,
        // Level, or contraption reference escapes this invocation.
        List<MovementContext> rowRollers = new ArrayList<>(
            context.contraption.getActors().stream()
                .map(pair -> pair.getRight())
                .filter(actor -> actor.state.getBlock() instanceof RollerBlock)
                .filter(actor -> !actor.disabled)
                .filter(actor ->
                    actor.state.getValue(RollerBlock.FACING) == localFacing
                )
                .filter(actor ->
                    RollerModeGate.isWideFill(actor.blockEntityData)
                )
                .filter(actor -> sameRow(context, actor, localFacing))
                .toList()
        );
        if (rowRollers.stream().noneMatch(actor -> actor == context)) {
            rowRollers.add(context);
        }
        List<BlockPos> rowPositions = rowRollers.stream()
            .map(actor -> actor.localPos)
            .toList();
        EdgeSides edges = RollerEdgeSelection.select(
            context.localPos,
            localFacing,
            rowPositions
        );
        if (!edges.hasOuterSide()) {
            return false;
        }

        Direction clockwise = localFacing.getClockWise();
        int reach = WideFillBytePlanner.reachBlocksForCreateDepth(
            AllConfigs.server().kinetics.rollerFillDepth.get()
        );
        double profileHalo = reach + 2.0;
        List<RollerProfile> profiles = new ArrayList<>();
        for (MovementContext roller : rowRollers) {
            PaveTask profile = roller == context
                ? trackProfile
                : profileProvider.create(roller);
            ProfileWindow window;
            if (profile != null) {
                window = PreciseTrackHeightSampler.samplesWithHalo(
                    profile,
                    roller.localPos.getY(),
                    profileHalo
                );
            } else if (roller == context) {
                window = ProfileWindow.core(List.of(
                    fallbackSample(context, fallbackPosition)
                ));
            } else {
                continue;
            }
            if (!window.samples().isEmpty()) {
                profiles.add(new RollerProfile(
                    roller == context,
                    projection(roller.localPos, clockwise),
                    window
                ));
            }
        }

        RollerProfile currentProfile = profiles.stream()
            .filter(RollerProfile::current)
            .findFirst()
            .orElse(null);
        if (currentProfile == null) {
            return false;
        }
        RollerProfile inwardProfile = inwardProfile(
            currentProfile,
            profiles,
            edges
        );
        List<Seed> seeds = new ArrayList<>();
        for (RollerProfile profile : profiles) {
            for (TrackSurfaceSample sample : profile.window().samples()) {
                boolean outputOwner = profile.current()
                    && profile.window().isCore(sample);
                if (!profile.current()) {
                    seeds.add(new Seed(
                        sample.x(),
                        sample.z(),
                        sample.minimumCellSurfaceY(),
                        sample.tangentX(),
                        sample.tangentZ(),
                        false,
                        false,
                        false
                    ));
                } else if (edges.counterClockwiseOuter()
                    && edges.clockwiseOuter()) {
                    seeds.add(new Seed(
                        sample.x(),
                        sample.z(),
                        sample.minimumCellSurfaceY(),
                        sample.tangentX(),
                        sample.tangentZ(),
                        -sample.tangentZ(),
                        sample.tangentX(),
                        true,
                        true,
                        outputOwner
                    ));
                } else if (inwardProfile != null) {
                    seeds.add(stableOutwardSeed(
                        sample,
                        inwardProfile.window().samples(),
                        outputOwner
                    ));
                } else if (trackProfile == null) {
                    // Non-track contraptions have no neighbouring PaveTask from
                    // which a stable world side can be reconstructed.
                    Vec3 fallbackClockwiseWorld = context.rotation.apply(
                        Vec3.atLowerCornerOf(clockwise.getNormal())
                    );
                    seeds.add(seedFor(
                        sample,
                        edges,
                        fallbackClockwiseWorld,
                        outputOwner
                    ));
                } else {
                    // Never guess a train side from carriage yaw. If the
                    // neighbouring profile is unavailable, skipping this
                    // sample is safer than creating a second-radius slope.
                    seeds.add(new Seed(
                        sample.x(),
                        sample.z(),
                        sample.minimumCellSurfaceY(),
                        sample.tangentX(),
                        sample.tangentZ(),
                        false,
                        false,
                        false
                    ));
                }
            }
        }
        if (seeds.isEmpty()) {
            return false;
        }
        List<ByteCell> cells = WideFillBytePlanner.plan(seeds, reach);
        if (cells.isEmpty()) {
            return false;
        }

        IItemHandler inventory = context.contraption.getStorage().getAllItems();
        Set<HalfVoxel> reached = new HashSet<>(
            WideFillBytePlanner.seedVoxels(seeds)
        );
        boolean changed = false;
        for (ByteCell cell : cells) {
            if (!hasReachableParent(cell, reached)) {
                continue;
            }

            BlockPos position = new BlockPos(
                Math.floorDiv(cell.halfX(), 2),
                Math.floorDiv(cell.lowerHalfY(), 2),
                Math.floorDiv(cell.halfZ(), 2)
            );
            CopycatByteBlock.Byte bite = CopycatByteBlock.bite(
                Math.floorMod(cell.halfX(), 2) == 1,
                Math.floorMod(cell.lowerHalfY(), 2) == 1,
                Math.floorMod(cell.halfZ(), 2) == 1
            );
            PlacementResult result = CopycatLayerPavingService.tryPlaceWithZinc(
                context.world,
                position,
                CopycatPavingMaterial.BYTE,
                CopycatLayerPavingService.byteStateFor(Set.of(bite)),
                inventory
            );
            if (result != PlacementResult.FAIL) {
                reached.add(cell.voxel());
            }
            changed |= result == PlacementResult.SUCCESS;
        }
        return changed;
    }

    private static boolean sameRow(
        MovementContext reference,
        MovementContext candidate,
        Direction facing
    ) {
        return candidate.localPos.getY() == reference.localPos.getY()
            && projection(candidate.localPos, facing)
                == projection(reference.localPos, facing);
    }

    private static int projection(BlockPos position, Direction direction) {
        return position.getX() * direction.getStepX()
            + position.getZ() * direction.getStepZ();
    }

    private static RollerProfile inwardProfile(
        RollerProfile current,
        List<RollerProfile> profiles,
        EdgeSides edges
    ) {
        RollerProfile nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (RollerProfile candidate : profiles) {
            if (candidate.current()) {
                continue;
            }
            int delta = candidate.localLateral() - current.localLateral();
            boolean isInward = edges.counterClockwiseOuter()
                ? delta > 0
                : delta < 0;
            if (!isInward || Math.abs(delta) >= nearestDistance) {
                continue;
            }
            nearest = candidate;
            nearestDistance = Math.abs(delta);
        }
        return nearest;
    }

    private static Seed stableOutwardSeed(
        TrackSurfaceSample edge,
        List<TrackSurfaceSample> inwardSamples,
        boolean outputOwner
    ) {
        var outward = TrackProfileSideResolver.outwardNormal(
            edge,
            inwardSamples
        );
        if (outward.isEmpty()) {
            // Quantized adjacent profiles can occasionally occupy the same
            // X/Z cell. Suppress this ambiguous sample instead of guessing a
            // side and creating a second-radius branch.
            return new Seed(
                edge.x(),
                edge.z(),
                edge.minimumCellSurfaceY(),
                edge.tangentX(),
                edge.tangentZ(),
                false,
                false,
                false
            );
        }
        var normal = outward.orElseThrow();
        return new Seed(
            edge.x(),
            edge.z(),
            edge.minimumCellSurfaceY(),
            edge.tangentX(),
            edge.tangentZ(),
            normal.x(),
            normal.z(),
            false,
            true,
            outputOwner
        );
    }

    private static Seed seedFor(
        TrackSurfaceSample sample,
        EdgeSides edges,
        Vec3 clockwiseWorld,
        boolean outputOwner
    ) {
        double tangentLength = Math.hypot(
            sample.tangentX(),
            sample.tangentZ()
        );
        double positiveNormalX = -sample.tangentZ() / tangentLength;
        double positiveNormalZ = sample.tangentX() / tangentLength;
        double clockwiseDot = clockwiseWorld.x * positiveNormalX
            + clockwiseWorld.z * positiveNormalZ;
        if (Math.abs(clockwiseDot) < DIRECTION_EPSILON) {
            clockwiseDot = 1;
        }

        boolean positiveIsClockwise = clockwiseDot > 0;
        boolean allowNegative = positiveIsClockwise
            ? edges.counterClockwiseOuter()
            : edges.clockwiseOuter();
        boolean allowPositive = positiveIsClockwise
            ? edges.clockwiseOuter()
            : edges.counterClockwiseOuter();
        return new Seed(
            sample.x(),
            sample.z(),
            sample.minimumCellSurfaceY(),
            sample.tangentX(),
            sample.tangentZ(),
            positiveNormalX,
            positiveNormalZ,
            allowNegative,
            allowPositive,
            outputOwner
        );
    }

    private static TrackSurfaceSample fallbackSample(
        MovementContext context,
        BlockPos position
    ) {
        double tangentX = context.relativeMotion.x;
        double tangentZ = context.relativeMotion.z;
        if (Math.abs(tangentX) < 1.0e-9
            && Math.abs(tangentZ) < 1.0e-9) {
            tangentX = 1;
        }
        return new TrackSurfaceSample(
            position.getX(),
            position.getZ(),
            position.getY(),
            tangentX,
            tangentZ,
            0,
            0
        );
    }

    private static boolean hasReachableParent(
        ByteCell cell,
        Set<HalfVoxel> reached
    ) {
        int parentY = cell.distance() == 1
            ? cell.lowerHalfY()
            : cell.lowerHalfY() + 1;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (reached.contains(new HalfVoxel(
                    cell.halfX() + offsetX,
                    parentY,
                    cell.halfZ() + offsetZ
                ))) {
                    return true;
                }
            }
        }
        return false;
    }

    private record RollerProfile(
        boolean current,
        int localLateral,
        ProfileWindow window
    ) {
    }
    @FunctionalInterface
    public interface TrackProfileProvider {
        @Nullable
        PaveTask create(MovementContext context);
    }
}
