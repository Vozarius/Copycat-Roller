package dev.example.copycatroller.paving;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Selects the outward sides of the two edge Rollers in one lateral row. */
public final class RollerEdgeSelection {
    private RollerEdgeSelection() {
    }

    public static EdgeSides select(
        BlockPos current,
        Direction facing,
        Collection<BlockPos> rollerPositions
    ) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Roller facing must be horizontal");
        }

        Direction clockwise = facing.getClockWise();
        int currentLongitudinal = projection(current, facing);
        int currentLateral = projection(current, clockwise);
        int minimum = currentLateral;
        int maximum = currentLateral;

        for (BlockPos position : rollerPositions) {
            if (position.getY() != current.getY()
                || projection(position, facing) != currentLongitudinal) {
                continue;
            }
            int lateral = projection(position, clockwise);
            minimum = Math.min(minimum, lateral);
            maximum = Math.max(maximum, lateral);
        }

        return new EdgeSides(
            currentLateral == minimum,
            currentLateral == maximum
        );
    }

    private static int projection(BlockPos position, Direction direction) {
        return position.getX() * direction.getStepX()
            + position.getZ() * direction.getStepZ();
    }

    public record EdgeSides(
        boolean counterClockwiseOuter,
        boolean clockwiseOuter
    ) {
        public boolean hasOuterSide() {
            return counterClockwiseOuter || clockwiseOuter;
        }
    }
}