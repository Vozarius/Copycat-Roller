package dev.example.copycatroller;

import dev.example.copycatroller.paving.LayerMath.RoundingDirection;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CopycatRollerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<RoundingDirection> ROUNDING_DIRECTION;
    public static final ModConfigSpec.BooleanValue SURFACE_ONLY;
    public static final ModConfigSpec.IntValue FILL_DEPTH_BLOCKS;
    public static final ModConfigSpec.DoubleValue SLOPE_MAX_VERTICAL_ERROR;
    public static final ModConfigSpec.BooleanValue LOG_PLACEMENT_FAILURES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("paving");
        ROUNDING_DIRECTION = builder
            .comment(
                "How fractional track heights are quantized to eighths.",
                "DOWN: (0, 1/8] -> 0 and (7/8, 1] -> 7.",
                "UP:   (0, 1/8] -> 1 and (7/8, 1] -> 8."
            )
            .defineEnum("roundingDirection", RoundingDirection.DOWN);
        SURFACE_ONLY = builder
            .comment(
                "Place only the highest Copycats+ surface cell for a track profile.",
                "When true, no full support blocks are filled below a partial surface.",
                "A level straight track whose surface ends exactly on a block boundary is skipped."
            )
            .define("surfaceOnly", true);
        FILL_DEPTH_BLOCKS = builder
            .comment(
                "Number of full block positions to fill downward, including the nearest base block.",
                "Used only when surfaceOnly is false.",
                "Create's rollerFillDepth remains an upper safety limit."
            )
            .defineInRange("fillDepthBlocks", 1, 1, 512);
        SLOPE_MAX_VERTICAL_ERROR = builder
            .comment(
                "Maximum vertical gap, in blocks, allowed at either edge of a Slope Layer.",
                "States that cross the track surface or exceed this gap are skipped to avoid a sawtooth.",
                "0.25 is one Copycats+ slope-layer growth step."
            )
            .defineInRange("slopeMaxVerticalError", 0.25, 0.0, 1.0);
        builder.pop();

        builder.push("diagnostics");
        LOG_PLACEMENT_FAILURES = builder
            .comment("Log blocked or rolled-back Copycat Layer placements.")
            .define("logPlacementFailures", false);
        builder.pop();
        SPEC = builder.build();
    }

    private CopycatRollerConfig() {
    }
}
