package dev.example.copycatroller.paving;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.example.copycatroller.paving.CopycatLayerPavingService.PlacementResult;
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
 * Zinc-only Wide Fill compatibility. Only the two edge Rollers in a lateral
 * row create a one-Byte-thick slope, and each edge Roller works outward only.
 */
public final class CopycatWideFillPavingService {
    private static final double DIRECTION_EPSILON = 1.0e-7;

    private CopycatWideFillPavingService() {
    }

    public static boolean pave(
        MovementContext context,
        BlockPos fallbackPosition,
        @Nullable PaveTask trackProfile
    ) {
        if (context.world.isClientSide || context.contraption == null) {
            return false;
        }

        Direction localFacing = context.state.getValue(RollerBlock.FACING);
        List<BlockPos> matchingRollers = context.contraption.getBlocks()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().state().getBlock() instanceof RollerBlock)
            .filter(entry -> entry.getValue().state().getValue(RollerBlock.FACING) == localFacing)
            .map(java.util.Map.Entry::getKey)
            .toList();
        EdgeSides edges = RollerEdgeSelection.select(
            context.localPos,
            localFacing,
            matchingRollers
        );
        if (!edges.hasOuterSide()) {
            return false;
        }

        List<TrackSurfaceSample> samples = trackProfile == null
            ? List.of(fallbackSample(context, fallbackPosition))
            : PreciseTrackHeightSampler.samples(
                trackProfile,
                context.localPos.getY()
            );
        if (samples.isEmpty()) {
            return false;
        }

        Vec3 clockwiseWorld = context.rotation.apply(
            Vec3.atLowerCornerOf(localFacing.getClockWise().getNormal())
        );
        List<Seed> seeds = samples.stream()
            .map(sample -> seedFor(sample, edges, clockwiseWorld))
            .toList();
        int reach = WideFillBytePlanner.reachBlocksForCreateDepth(
            AllConfigs.server().kinetics.rollerFillDepth.get()
        );
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

    private static Seed seedFor(
        TrackSurfaceSample sample,
        EdgeSides edges,
        Vec3 clockwiseWorld
    ) {
        boolean lateralAlongX = Math.abs(sample.tangentZ())
            > Math.abs(sample.tangentX());
        double clockwiseComponent = lateralAlongX
            ? clockwiseWorld.x
            : clockwiseWorld.z;
        if (Math.abs(clockwiseComponent) < DIRECTION_EPSILON) {
            clockwiseComponent = lateralAlongX
                ? sample.tangentZ()
                : -sample.tangentX();
        }

        boolean positiveIsClockwise = clockwiseComponent > 0;
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
            allowNegative,
            allowPositive
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
        return reached.contains(new HalfVoxel(
            cell.halfX() - 1, parentY, cell.halfZ()
        )) || reached.contains(new HalfVoxel(
            cell.halfX() + 1, parentY, cell.halfZ()
        )) || reached.contains(new HalfVoxel(
            cell.halfX(), parentY, cell.halfZ() - 1
        )) || reached.contains(new HalfVoxel(
            cell.halfX(), parentY, cell.halfZ() + 1
        ));
    }
}