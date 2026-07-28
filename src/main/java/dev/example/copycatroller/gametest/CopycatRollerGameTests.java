package dev.example.copycatroller.gametest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.CopycatRollerConfig;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.CopycatLayerPavingService.PlacementResult;
import dev.example.copycatroller.paving.CopycatPavingMaterial;
import dev.example.copycatroller.paving.LayerMath;
import dev.example.copycatroller.paving.PreciseTrackHeightSampler;
import dev.example.copycatroller.paving.RollerModeGate;
import dev.example.copycatroller.paving.TrackSurfaceSample;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(CopycatRoller.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CopycatRollerGameTests {
    private static final BlockPos TARGET = new BlockPos(1, 2, 1);

    private CopycatRollerGameTests() {
    }

    @GameTest(template = "empty")
    public static void rollerAcceptsSupportedLayerFamilies(GameTestHelper helper) {
        RollerBlockEntity roller = createRoller(helper);
        for (CopycatPavingMaterial material : CopycatPavingMaterial.values()) {
            check(
                helper,
                invokeMaterialPredicate(roller, new ItemStack(material.itemBlock().asItem())),
                "Mechanical Roller rejected " + material
            );
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerRejectsAnotherIncompleteCopycat(GameTestHelper helper) {
        RollerBlockEntity roller = createRoller(helper);
        check(
            helper,
            !invokeMaterialPredicate(roller, new ItemStack(CCBlocks.COPYCAT_SLICE.asItem())),
            "Mechanical Roller accepted a different incomplete copycat"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void horizontalProfileCreatesFullLayer(GameTestHelper helper) {
        Level level = helper.getLevel();
        TrackGraph graph = new TrackGraph();
        TrackEdge edge = new TrackEdge(
            node(level, new Vec3(0.5, 64.5, 0.5), 10),
            node(level, new Vec3(8.5, 64.5, 0.5), 11),
            null,
            TrackMaterial.ANDESITE
        );
        PaveTask task = new PaveTask(0, 0);
        TrackPaverV2.pave(task, graph, edge, 0, edge.getLength());
        check(helper, !task.keys().isEmpty(), "horizontal track produced no paving columns");
        check(
            helper,
            PreciseTrackHeightSampler.samples(task, 0).stream()
                .allMatch(sample -> LayerMath.layersAboveBase(sample.surfaceY()) == 0),
            "horizontal track produced a partial upper layer"
        );

        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(8);
        check(
            helper,
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 8, inventory)
                == PlacementResult.SUCCESS,
            "full layer placement failed"
        );
        assertLayer(helper, position, 8);
        assertEmptyMaterial(helper, position);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfBlockSlopeCreatesFourUpperLayers(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(12);
        placeProfileColumn(
            helper,
            position,
            position.getY() + 0.5,
            inventory,
            LayerMath.RoundingDirection.UP
        );
        assertLayer(helper, position, 8);
        assertLayer(helper, position.above(), 4);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configurationDefaultsToDownAndOneBlock(GameTestHelper helper) {
        check(
            helper,
            CopycatRollerConfig.ROUNDING_DIRECTION.get() == LayerMath.RoundingDirection.DOWN,
            "default rounding direction is not DOWN"
        );
        check(
            helper,
            CopycatRollerConfig.FILL_DEPTH_BLOCKS.get() == 1,
            "default fill depth is not one block"
        );
        check(
            helper,
            LayerMath.layersAboveBase(0.125, CopycatRollerConfig.ROUNDING_DIRECTION.get()) == 0,
            "default DOWN rounding did not map 1/8 to zero upper layers"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allEighthSlopesCreateMatchingLayers(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        for (int layers = 1; layers <= 7; layers++) {
            helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            ItemStackHandler inventory = inventory(layers);
            check(
                helper,
                CopycatLayerPavingService.tryPlace(helper.getLevel(), position, layers, inventory)
                    == PlacementResult.SUCCESS,
                "failed to place " + layers + " layers"
            );
            assertLayer(helper, position, layers);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfLayerUsesIndependentAxisSidesAndExactCost(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        BlockState target = CopycatLayerPavingService.halfLayerStateFor(
            Direction.Axis.X,
            3,
            5
        );
        ItemStackHandler inventory = inventory(CopycatPavingMaterial.HALF_LAYER, 12);
        check(
            helper,
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.HALF_LAYER,
                target,
                inventory
            ) == PlacementResult.SUCCESS,
            "Copycat Half Layer placement failed"
        );
        assertHalfLayer(helper, position, Direction.Axis.X, 3, 5);
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.HALF_LAYER);
        check(helper, count(inventory) == 4, "half layer did not consume 3 + 5 items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfLayerSamplesBothHalvesOfTrackGradient(GameTestHelper helper) {
        TrackSurfaceSample risingEast = new TrackSurfaceSample(
            10,
            20,
            64.5,
            1,
            0,
            0.5,
            0
        );
        Optional<BlockState> target = CopycatLayerPavingService.upperStateFor(
            CopycatPavingMaterial.HALF_LAYER,
            risingEast,
            LayerMath.RoundingDirection.UP
        );
        check(helper, target.isPresent(), "half-layer gradient produced no upper state");
        BlockState state = target.orElseThrow();
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.AXIS) == Direction.Axis.X,
            "half-layer gradient selected the wrong axis"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS) == 3,
            "negative half did not sample x - 1/4"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS) == 5,
            "positive half did not sample x + 1/4"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfLayerGrowthIsIncrementalAndAtomic(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler initial = inventory(CopycatPavingMaterial.HALF_LAYER, 5);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 2, 3),
            initial
        );

        ItemStackHandler insufficient = inventory(CopycatPavingMaterial.HALF_LAYER, 4);
        PlacementResult failed = CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 5, 5),
            insufficient
        );
        check(helper, failed == PlacementResult.FAIL, "underfunded half-layer growth succeeded");
        assertHalfLayer(helper, position, Direction.Axis.Z, 2, 3);
        check(helper, count(insufficient) == 4, "failed half-layer growth consumed items");

        ItemStackHandler growth = inventory(CopycatPavingMaterial.HALF_LAYER, 7);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 5, 5),
            growth
        );
        assertHalfLayer(helper, position, Direction.Axis.Z, 5, 5);
        check(helper, count(growth) == 2, "half-layer growth did not consume (5-2) + (5-3)");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slopeLayerFacesUphillAndConsumesLayers(GameTestHelper helper) {
        TrackSurfaceSample risingEast = new TrackSurfaceSample(
            10,
            20,
            64.5,
            1,
            0,
            0.5,
            0
        );
        BlockState target = CopycatLayerPavingService.upperStateFor(
            CopycatPavingMaterial.SLOPE_LAYER,
            risingEast,
            LayerMath.RoundingDirection.UP
        ).orElseThrow();
        check(
            helper,
            target.getValue(CopycatSlopeLayerBlock.FACING) == Direction.EAST,
            "rising-east slope did not face east"
        );
        check(
            helper,
            target.getValue(CopycatSlopeLayerBlock.LAYERS) == 4,
            "half-block slope did not use layers=4"
        );

        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(CopycatPavingMaterial.SLOPE_LAYER, 9);
        check(
            helper,
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.SLOPE_LAYER,
                target,
                inventory
            ) == PlacementResult.SUCCESS,
            "Copycat Slope Layer placement failed"
        );
        assertSlopeLayer(helper, position, Direction.EAST, 4);
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.SLOPE_LAYER);
        check(helper, count(inventory) == 5, "slope layers=4 did not consume four items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void slopeLayerReversesFacingOnDescendingTrack(GameTestHelper helper) {
        TrackSurfaceSample risingWest = new TrackSurfaceSample(
            10,
            20,
            64.75,
            1,
            0,
            -0.5,
            0
        );
        BlockState target = CopycatLayerPavingService.upperStateFor(
            CopycatPavingMaterial.SLOPE_LAYER,
            risingWest,
            LayerMath.RoundingDirection.UP
        ).orElseThrow();
        check(
            helper,
            target.getValue(CopycatSlopeLayerBlock.FACING) == Direction.WEST,
            "descending-east track did not face its higher west side"
        );
        check(
            helper,
            target.getValue(CopycatSlopeLayerBlock.LAYERS) == 6,
            "three-quarter slope did not use layers=6"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullHalfAndSlopeLayersUseCopycatsRequirementCost(GameTestHelper helper) {
        BlockPos halfPosition = resetTarget(helper);
        ItemStackHandler halfInventory = inventory(CopycatPavingMaterial.HALF_LAYER, 20);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            halfPosition,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 8, 8),
            halfInventory
        );
        check(helper, count(halfInventory) == 4, "full half layer did not consume 16 items");

        BlockPos slopePosition = halfPosition.above();
        helper.getLevel().setBlockAndUpdate(slopePosition, Blocks.AIR.defaultBlockState());
        ItemStackHandler slopeInventory = inventory(CopycatPavingMaterial.SLOPE_LAYER, 12);
        check(
            helper,
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                slopePosition,
                CopycatPavingMaterial.SLOPE_LAYER,
                CopycatLayerPavingService.slopeLayerStateFor(Direction.SOUTH, 8),
                slopeInventory
            ) == PlacementResult.SUCCESS,
            "full slope layer placement failed"
        );
        check(helper, count(slopeInventory) == 4, "full slope layer did not consume eight items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void halfLayerCustomMaterialIsProtected(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );
        Object blockEntity = helper.getLevel().getBlockEntity(position);
        check(
            helper,
            blockEntity instanceof IMultiStateCopycatBlockEntity,
            "half layer did not create its multistate block entity"
        );
        IMultiStateCopycatBlockEntity copycat = (IMultiStateCopycatBlockEntity) blockEntity;
        copycat.setMaterial(
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );

        ItemStackHandler inventory = inventory(CopycatPavingMaterial.HALF_LAYER, 12);
        PlacementResult result = CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 5, 5),
            inventory
        );
        check(helper, result == PlacementResult.FAIL, "custom half-layer material was overwritten");
        assertHalfLayer(helper, position, Direction.Axis.X, 2, 2);
        check(helper, count(inventory) == 12, "custom half layer consumed items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialLayerConsumesExactlyNItems(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(11);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 6, inventory);
        check(helper, count(inventory) == 5, "layers=6 did not consume exactly six items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullLayerConsumesEightItems(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(10);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 8, inventory);
        check(helper, count(inventory) == 2, "layers=8 did not consume exactly eight items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insufficientStockIsAtomic(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(5);
        PlacementResult result =
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 6, inventory);
        check(helper, result == PlacementResult.FAIL, "underfunded placement did not fail");
        check(helper, helper.getLevel().getBlockState(position).isAir(), "world changed without enough items");
        check(helper, count(inventory) == 5, "inventory changed without enough items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void growingThreeToSixConsumesThree(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler initial = inventory(3);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 3, initial);

        ItemStackHandler growth = inventory(7);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 6, growth);
        assertLayer(helper, position, 6);
        check(helper, count(growth) == 4, "growing 3 -> 6 did not consume three items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customMaterialIsNeverOverwritten(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 3, inventory(3));
        ICopycatBlockEntity copycat =
            (ICopycatBlockEntity) helper.getLevel().getBlockEntity(position);
        copycat.setMaterial(Blocks.STONE.defaultBlockState());

        ItemStackHandler inventory = inventory(8);
        PlacementResult result =
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 6, inventory);
        check(helper, result == PlacementResult.FAIL, "custom material was not treated as blocking");
        assertLayer(helper, position, 3);
        check(helper, copycat.getMaterial().is(Blocks.STONE), "custom material changed");
        check(helper, count(inventory) == 8, "items were consumed for a custom-material layer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedPassConsumesNothing(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 4, inventory(4));
        ItemStackHandler secondPass = inventory(8);
        PlacementResult result =
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 4, secondPass);
        check(helper, result == PlacementResult.PASS, "identical second pass did not return PASS");
        check(helper, count(secondPass) == 8, "identical second pass consumed items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedChunkIsUntouched(GameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos farAway = new BlockPos(29_000_000, level.getMinBuildHeight() + 32, 29_000_000);
        ItemStackHandler inventory = inventory(8);
        check(helper, !level.isLoaded(farAway), "chosen test chunk is unexpectedly loaded");
        PlacementResult result =
            CopycatLayerPavingService.tryPlace(level, farAway, 8, inventory);
        check(helper, result == PlacementResult.FAIL, "unloaded placement did not fail");
        check(helper, count(inventory) == 8, "unloaded placement consumed items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void solidBlocksAndPortalsAreNotReplaced(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = inventory(16);
        helper.getLevel().setBlockAndUpdate(position, Blocks.OBSIDIAN.defaultBlockState());
        check(
            helper,
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 8, inventory)
                == PlacementResult.FAIL,
            "solid block was replaced"
        );

        helper.getLevel().setBlockAndUpdate(position, Blocks.NETHER_PORTAL.defaultBlockState());
        check(
            helper,
            CopycatLayerPavingService.tryPlace(helper.getLevel(), position, 8, inventory)
                == PlacementResult.FAIL,
            "portal was replaced"
        );
        check(helper, count(inventory) == 16, "blocked positions consumed items");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preciseSamplerKeepsCreateXZCoverage(GameTestHelper helper) {
        Level level = helper.getLevel();
        assertStraightCoverage(helper, level, new Vec3(0, 64, 0), new Vec3(8, 66, 8));
        assertCurveCoverage(helper, level);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compatBranchIsStrictlyScoped(GameTestHelper helper) {
        check(
            helper,
            !CopycatLayerPavingService.isCopycatLayer(new ItemStack(Blocks.STONE)),
            "ordinary block filter entered the compatibility branch"
        );
        CompoundTag rollerData = new CompoundTag();
        rollerData.putInt("ScrollValue", 0);
        check(helper, !RollerModeGate.isStraightFill(rollerData), "TUNNEL_PAVE entered the compatibility branch");
        rollerData.putInt("ScrollValue", 1);
        check(helper, RollerModeGate.isStraightFill(rollerData), "STRAIGHT_FILL missed the compatibility branch");
        rollerData.putInt("ScrollValue", 2);
        check(helper, !RollerModeGate.isStraightFill(rollerData), "WIDE_FILL entered the compatibility branch");
        try {
            Class<?> modeClass = Class.forName(
                "com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity$RollingMode"
            );
            Object[] values = modeClass.getEnumConstants();
            check(helper, values.length == 3, "Create RollingMode count changed");
            check(helper, values[0].toString().equals("TUNNEL_PAVE"), "TUNNEL_PAVE ordinal changed");
            check(helper, values[1].toString().equals("STRAIGHT_FILL"), "STRAIGHT_FILL ordinal changed");
            check(helper, values[2].toString().equals("WIDE_FILL"), "WIDE_FILL ordinal changed");
        } catch (ClassNotFoundException exception) {
            helper.fail("Create RollingMode was not loadable: " + exception);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commonCodeLoadsOnGameTestServer(GameTestHelper helper) {
        try {
            Class.forName("dev.example.copycatroller.CopycatRoller", false, CopycatRoller.class.getClassLoader());
            Class.forName(
                "dev.example.copycatroller.paving.CopycatLayerPavingService",
                false,
                CopycatRoller.class.getClassLoader()
            );
            Class.forName(
                "dev.example.copycatroller.paving.PreciseTrackHeightSampler",
                false,
                CopycatRoller.class.getClassLoader()
            );
        } catch (ClassNotFoundException | LinkageError exception) {
            helper.fail("common code failed server classloading: " + exception);
        }
        helper.succeed();
    }

    private static RollerBlockEntity createRoller(GameTestHelper helper) {
        helper.setBlock(TARGET, AllBlocks.MECHANICAL_ROLLER.getDefaultState());
        Object blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET));
        check(helper, blockEntity instanceof RollerBlockEntity, "roller block entity was not created");
        return (RollerBlockEntity) blockEntity;
    }

    private static boolean invokeMaterialPredicate(RollerBlockEntity roller, ItemStack stack) {
        try {
            Method method = RollerBlockEntity.class.getDeclaredMethod("isValidMaterial", ItemStack.class);
            method.setAccessible(true);
            return (boolean) method.invoke(roller, stack);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not invoke Roller material predicate", exception);
        }
    }

    private static void placeProfileColumn(
        GameTestHelper helper,
        BlockPos basePosition,
        double profileY,
        ItemStackHandler inventory,
        LayerMath.RoundingDirection roundingDirection
    ) {
        LayerMath.SurfaceBreakdown breakdown = LayerMath.breakDown(profileY, roundingDirection);
        if (breakdown.upperLayers() > 0) {
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                new BlockPos(basePosition.getX(), breakdown.baseY() + 1, basePosition.getZ()),
                breakdown.upperLayers(),
                inventory
            );
        }
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            new BlockPos(basePosition.getX(), breakdown.baseY(), basePosition.getZ()),
            breakdown.baseLayers(),
            inventory
        );
    }

    private static void assertStraightCoverage(
        GameTestHelper helper,
        Level level,
        Vec3 first,
        Vec3 second
    ) {
        TrackGraph graph = new TrackGraph();
        TrackNode node1 = node(level, first, 1);
        TrackNode node2 = node(level, second, 2);
        TrackEdge edge = new TrackEdge(node1, node2, null, TrackMaterial.ANDESITE);
        PaveTask task = new PaveTask(0, 0);
        TrackPaverV2.pave(task, graph, edge, 0, edge.getLength());
        assertCapturedCoverage(helper, task);

        List<TrackSurfaceSample> samples = PreciseTrackHeightSampler.samples(task, 0);
        check(
            helper,
            samples.stream().allMatch(sample ->
                sample.longitudinalAxis() == Direction.Axis.X
                    && sample.uphillDirection() == Direction.EAST
                    && sample.gradientAlongAxis() > 0
            ),
            "straight sampler did not preserve its dominant axis and uphill gradient"
        );
    }

    private static void assertCurveCoverage(GameTestHelper helper, Level level) {
        BlockPos firstPosition = new BlockPos(0, 64, 0);
        BlockPos secondPosition = new BlockPos(8, 66, 8);
        Vec3 first = Vec3.atCenterOf(firstPosition);
        Vec3 second = Vec3.atCenterOf(secondPosition);
        BezierConnection curve = new BezierConnection(
            Couple.create(firstPosition, secondPosition),
            Couple.create(first, second),
            Couple.create(new Vec3(1, 0.25, 0), new Vec3(0, -0.25, -1)),
            Couple.create(new Vec3(0, 1, 0), new Vec3(0, 1, 0)),
            true,
            false,
            TrackMaterial.ANDESITE
        );
        TrackEdge edge = new TrackEdge(node(level, first, 3), node(level, second, 4), curve, TrackMaterial.ANDESITE);
        PaveTask task = new PaveTask(0, 0);
        TrackPaverV2.pave(task, new TrackGraph(), edge, 0, edge.getLength());
        assertCapturedCoverage(helper, task);

        boolean hasEighthPrecision = PreciseTrackHeightSampler.samples(task, 0).stream()
            .mapToDouble(sample -> sample.surfaceY() - Math.floor(sample.surfaceY()))
            .anyMatch(fraction -> Math.abs(fraction) > 1.0e-6
                && Math.abs(fraction - 0.5) > 1.0e-6);
        check(helper, hasEighthPrecision, "curve sampler retained only integer/half-block heights");
    }

    private static TrackNode node(Level level, Vec3 position, int id) {
        return new TrackNode(
            new TrackNodeLocation(position).in(level),
            id,
            new Vec3(0, 1, 0)
        );
    }

    private static void assertCapturedCoverage(GameTestHelper helper, PaveTask task) {
        Set<String> createCoverage = task.keys().stream()
            .map(coordinates -> coordinates.getFirst() + "," + coordinates.getSecond())
            .collect(Collectors.toSet());
        Set<String> preciseCoverage = PreciseTrackHeightSampler.samples(task, 0).stream()
            .map(sample -> sample.x() + "," + sample.z())
            .collect(Collectors.toSet());
        check(helper, !createCoverage.isEmpty(), "Create generated an empty X/Z profile");
        check(helper, createCoverage.equals(preciseCoverage), "precise sampler changed Create X/Z coverage");
    }

    private static BlockPos resetTarget(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(TARGET);
        helper.getLevel().setBlockAndUpdate(absolute, Blocks.AIR.defaultBlockState());
        return absolute;
    }

    private static ItemStackHandler inventory(int count) {
        return inventory(CopycatPavingMaterial.LAYER, count);
    }

    private static ItemStackHandler inventory(CopycatPavingMaterial material, int count) {
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(material.itemBlock().asItem(), count));
        return inventory;
    }

    private static int count(ItemStackHandler inventory) {
        return inventory.getStackInSlot(0).getCount();
    }

    private static void assertLayer(GameTestHelper helper, BlockPos position, int layers) {
        check(
            helper,
            helper.getLevel().getBlockState(position).is(CCBlocks.COPYCAT_LAYER.get()),
            "expected Copycat Layer at " + position
        );
        check(
            helper,
            helper.getLevel().getBlockState(position).getValue(CopycatLayerBlock.FACING) == Direction.UP,
            "Copycat Layer did not face up at " + position
        );
        check(
            helper,
            helper.getLevel().getBlockState(position).getValue(CopycatLayerBlock.LAYERS) == layers,
            "unexpected layer count at " + position
        );
    }

    private static void assertHalfLayer(
        GameTestHelper helper,
        BlockPos position,
        Direction.Axis axis,
        int negativeLayers,
        int positiveLayers
    ) {
        BlockState state = helper.getLevel().getBlockState(position);
        check(helper, state.is(CCBlocks.COPYCAT_HALF_LAYER.get()), "expected Copycat Half Layer at " + position);
        check(helper, state.getValue(CopycatHalfLayerBlock.AXIS) == axis, "unexpected half-layer axis");
        check(helper, state.getValue(CopycatHalfLayerBlock.HALF) == Half.BOTTOM, "half layer is not bottom");
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS) == negativeLayers,
            "unexpected negative half layers"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS) == positiveLayers,
            "unexpected positive half layers"
        );
        check(helper, !state.getValue(BlockStateProperties.WATERLOGGED), "half layer is waterlogged");
    }

    private static void assertSlopeLayer(
        GameTestHelper helper,
        BlockPos position,
        Direction facing,
        int layers
    ) {
        BlockState state = helper.getLevel().getBlockState(position);
        check(helper, state.is(CCBlocks.COPYCAT_SLOPE_LAYER.get()), "expected Copycat Slope Layer at " + position);
        check(helper, state.getValue(CopycatSlopeLayerBlock.FACING) == facing, "unexpected slope facing");
        check(helper, state.getValue(CopycatSlopeLayerBlock.HALF) == Half.BOTTOM, "slope layer is not bottom");
        check(helper, state.getValue(CopycatSlopeLayerBlock.LAYERS) == layers, "unexpected slope layers");
        check(helper, !state.getValue(BlockStateProperties.WATERLOGGED), "slope layer is waterlogged");
    }

    private static void assertEmptyMaterial(GameTestHelper helper, BlockPos position) {
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.LAYER);
    }

    private static void assertEmptyMaterial(
        GameTestHelper helper,
        BlockPos position,
        CopycatPavingMaterial material
    ) {
        Object blockEntity = helper.getLevel().getBlockEntity(position);
        check(helper, blockEntity instanceof ICopycatBlockEntity, "expected Copycats+ block entity is missing");
        ICopycatBlockEntity copycat = (ICopycatBlockEntity) blockEntity;
        check(helper, material.hasExpectedBlockEntity(copycat), "unexpected Copycats+ block entity type");
        check(helper, !copycat.hasCustomMaterial(), "new Copycat Layer has a custom material");
        if (copycat instanceof IMultiStateCopycatBlockEntity multiState) {
            check(
                helper,
                multiState.getMaterialItemStorage().getAllConsumedItems().isEmpty(),
                "new multistate layer has consumed material items"
            );
        } else {
            check(helper, copycat.getConsumedItem().isEmpty(), "new Copycat Layer has a consumed material item");
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
