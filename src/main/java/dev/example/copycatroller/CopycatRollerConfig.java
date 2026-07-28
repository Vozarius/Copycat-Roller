package dev.example.copycatroller;

import dev.example.copycatroller.paving.LayerMath.RoundingDirection;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CopycatRollerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<RoundingDirection> ROUNDING_DIRECTION;
    public static final ModConfigSpec.IntValue FILL_DEPTH_BLOCKS;
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
        FILL_DEPTH_BLOCKS = builder
            .comment(
                "Number of full block positions to fill downward, including the nearest base block.",
                "Create's rollerFillDepth remains an upper safety limit."
            )
            .defineInRange("fillDepthBlocks", 1, 1, 512);
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
