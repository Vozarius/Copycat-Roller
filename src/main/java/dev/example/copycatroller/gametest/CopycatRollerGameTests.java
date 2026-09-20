package dev.example.copycatroller.gametest;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.half_layer.CopycatHalfLayerBlock;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.actors.roller.PaveTask;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlock;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.crate.CreativeCrateMountedStorage;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.CopycatRollerConfig;
import dev.example.copycatroller.paving.CopycatPlacement;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.CopycatLayerPavingService.PlacementResult;
import dev.example.copycatroller.paving.CopycatMaterialFillingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.FillPassResult;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.FillResult;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.MaterialFillPlan;
import dev.example.copycatroller.paving.CopycatPavingMaterial;
import dev.example.copycatroller.paving.CopycatWideFillPavingService;
import dev.example.copycatroller.paving.LayerMath;
import dev.example.copycatroller.paving.PreciseTrackHeightSampler;
import dev.example.copycatroller.paving.RollerMaterialPlacementCapture;
import dev.example.copycatroller.paving.RollerEdgeSelection;
import dev.example.copycatroller.paving.RollerModeGate;
import dev.example.copycatroller.paving.SurfacePlacement;
import dev.example.copycatroller.paving.TrackSurfaceSample;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;

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
            if (material == CopycatPavingMaterial.BYTE) {
                continue;
            }
            check(
                helper,
                invokeMaterialPredicate(roller, new ItemStack(material.itemBlock().asItem())),
                "Mechanical Roller rejected " + material
            );
        }
        ItemStack zinc = new ItemStack(AllItems.ZINC_INGOT.get());
        check(
            helper,
            invokeMaterialPredicate(roller, zinc),
            "Mechanical Roller rejected the automatic zinc filter"
        );
        check(
            helper,
            BuiltInRegistries.ITEM.getKey(zinc.getItem()).equals(
                ResourceLocation.fromNamespaceAndPath("create", "zinc_ingot")
            ),
            "Create zinc registry id is not create:zinc_ingot"
        );
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
    public static void rollerMaterialFillsEmptyCopycat(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.STONE, 3);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.SUCCESS, "Roller material fill did not succeed");
        assertLayer(helper, position, 4);
        assertSingleMaterial(helper, position, Blocks.STONE.defaultBlockState());
        check(helper, count(materialInventory) == 2, "single-state Copycat did not consume one block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillSupportsOtherCopycatShapes(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        BlockState sliceState = CCBlocks.COPYCAT_SLICE.getDefaultState();
        helper.getLevel().setBlockAndUpdate(position, sliceState);
        ItemStackHandler materialInventory = blockInventory(Blocks.STONE, 2);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.SUCCESS, "another Copycat shape was not material-filled");
        check(helper, helper.getLevel().getBlockState(position).equals(sliceState), "Copycat shape state changed");
        assertSingleMaterial(helper, position, Blocks.STONE.defaultBlockState());
        check(helper, count(materialInventory) == 1, "another Copycat shape did not consume one block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillProtectsExistingMaterial(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            4,
            inventory(4)
        );
        ICopycatBlockEntity copycat =
            (ICopycatBlockEntity) helper.getLevel().getBlockEntity(position);
        copycat.setMaterial(Blocks.STONE.defaultBlockState());
        copycat.setConsumedItem(new ItemStack(Blocks.STONE));
        ItemStackHandler materialInventory = blockInventory(Blocks.DIRT, 2);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.DIRT),
            materialInventory
        );

        check(helper, result == FillResult.PASS, "existing material was not treated as complete");
        assertSingleMaterial(helper, position, Blocks.STONE.defaultBlockState());
        check(helper, count(materialInventory) == 2, "existing material consumed another block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillUsesOneBlockPerExistingHalf(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 3, 5),
            inventory(CopycatPavingMaterial.HALF_LAYER, 8)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.STONE, 3);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.SUCCESS, "Half Layer material fill failed");
        assertHalfLayer(helper, position, Direction.Axis.X, 3, 5);
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        check(helper, count(materialInventory) == 1, "two existing halves did not consume two blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillSkipsMissingHalf(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 0, 5),
            inventory(CopycatPavingMaterial.HALF_LAYER, 5)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.STONE, 2);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.SUCCESS, "single existing half was not filled");
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        IMultiStateCopycatBlockEntity copycat =
            (IMultiStateCopycatBlockEntity) helper.getLevel().getBlockEntity(position);
        var negative = copycat.getMaterialItemStorage().getMaterialItem(
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName()
        );
        check(helper, !negative.hasCustomMaterial(), "missing half received a material");
        check(helper, negative.consumedItem().isEmpty(), "missing half consumed a block");
        check(helper, count(materialInventory) == 1, "single existing half did not consume one block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillIsAtomicForMultistateCopycat(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.STONE, 1);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.FAIL, "underfunded multistate fill did not fail");
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.HALF_LAYER);
        check(helper, count(materialInventory) == 1, "failed multistate fill consumed a block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillCompletesPrepaidMultistateCost(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );
        ItemStackHandler mountedInventory = blockInventory(Blocks.STONE, 1);

        FillResult result = CopycatMaterialFillingService.tryFillWithPrepaid(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            new ItemStack(Blocks.STONE),
            mountedInventory
        );

        check(helper, result == FillResult.SUCCESS, "prepaid Half Layer fill failed");
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        check(
            helper,
            count(mountedInventory) == 0,
            "prepaid Half Layer did not consume exactly one additional block"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillRefundsPrepaidItemWhenUnderfunded(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );
        ItemStackHandler mountedInventory = new ItemStackHandler(1);

        FillResult result = CopycatMaterialFillingService.tryFillWithPrepaid(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            new ItemStack(Blocks.STONE),
            mountedInventory
        );

        check(helper, result == FillResult.FAIL, "underfunded prepaid fill did not fail");
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.HALF_LAYER);
        check(
            helper,
            count(mountedInventory) == 1,
            "underfunded prepaid fill did not return the selected block"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void randomizeFilterUsesItsSelectedBlockForCopycat(GameTestHelper helper) {
        if (!ModList.get().isLoaded("createrandomizefilters")) {
            helper.succeed();
            return;
        }

        try {
            BlockPos position = resetTarget(helper);
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.HALF_LAYER,
                CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 2, 2),
                inventory(CopycatPavingMaterial.HALF_LAYER, 4)
            );

            ItemStack randomFilter = randomizeFilterWith(
                Blocks.GRAVEL.defaultBlockState()
            );
            CompoundTag rollerData = new CompoundTag();
            rollerData.put(
                "Filter",
                randomFilter.save(helper.getLevel().registryAccess())
            );

            BearingContraption contraption = new BearingContraption();
            contraption.getStorage().initialize();
            ItemStackHandler mountedInventory = blockInventory(Blocks.GRAVEL, 3);
            contraption.getStorage().attachExternal(mountedInventory);
            MovementContext context = new MovementContext(
                helper.getLevel(),
                new StructureBlockInfo(
                    BlockPos.ZERO,
                    AllBlocks.MECHANICAL_ROLLER.getDefaultState(),
                    rollerData
                ),
                contraption
            );

            boolean changed = RollerMaterialPlacementCapture.probe(
                new RollerMovementBehaviour(),
                context,
                position,
                Blocks.STONE.defaultBlockState(),
                contraption.getStorage().getAllItems()
            );

            check(helper, changed, "Randomize Filter selection did not fill the Copycat");
            assertPartMaterial(
                helper,
                position,
                CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
                Blocks.GRAVEL.defaultBlockState()
            );
            assertPartMaterial(
                helper,
                position,
                CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
                Blocks.GRAVEL.defaultBlockState()
            );
            check(
                helper,
                count(mountedInventory) == 1,
                "Randomize Filter Half Layer did not consume exactly two selected blocks"
            );
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not configure Create Randomize Filters: " + exception);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryFilterUsesRealRollerTransactionForCopycat(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );

        ItemStack filter = new ItemStack(Blocks.STONE);
        CompoundTag rollerData = new CompoundTag();
        rollerData.put(
            "Filter",
            filter.save(helper.getLevel().registryAccess())
        );

        BearingContraption contraption = new BearingContraption();
        contraption.getStorage().initialize();
        ItemStackHandler mountedInventory = blockInventory(Blocks.STONE, 3);
        contraption.getStorage().attachExternal(mountedInventory);
        MovementContext context = new MovementContext(
            helper.getLevel(),
            new StructureBlockInfo(
                BlockPos.ZERO,
                AllBlocks.MECHANICAL_ROLLER.getDefaultState(),
                rollerData
            ),
            contraption
        );

        boolean changed = RollerMaterialPlacementCapture.probe(
            new RollerMovementBehaviour(),
            context,
            position,
            Blocks.STONE.defaultBlockState(),
            contraption.getStorage().getAllItems()
        );

        check(helper, changed, "ordinary Roller transaction did not fill the Copycat");
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        check(
            helper,
            count(mountedInventory) == 1,
            "ordinary Roller transaction did not consume exactly two blocks"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeCrateFundsCopycatShapesAndZinc(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CreativeCrateMountedStorage layerSupply = new CreativeCrateMountedStorage(
            new ItemStack(CopycatPavingMaterial.LAYER.itemBlock())
        );
        check(
            helper,
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                position,
                8,
                layerSupply
            ) == PlacementResult.SUCCESS,
            "Creative Crate did not fund a full Copycat Layer"
        );
        assertLayer(helper, position, 8);
        check(
            helper,
            CopycatPavingMaterial.LAYER.matches(layerSupply.getStackInSlot(0)),
            "Creative Crate lost its Copycat Layer supply"
        );

        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        CreativeCrateMountedStorage zincSupply = new CreativeCrateMountedStorage(
            new ItemStack(AllItems.ZINC_INGOT.get())
        );
        BlockState oneByte = CopycatLayerPavingService.byteStateFor(Set.of(
            CopycatByteBlock.bite(false, false, false)
        ));
        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.BYTE,
                oneByte,
                zincSupply
            ) == PlacementResult.SUCCESS,
            "Creative Crate did not fund a zinc Copycat Byte"
        );
        check(
            helper,
            CopycatLayerPavingService.isZincIngot(zincSupply.getStackInSlot(0)),
            "Creative Crate lost its zinc supply"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeCrateMaterialUsesRealRollerTransaction(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 2, 2),
            inventory(CopycatPavingMaterial.HALF_LAYER, 4)
        );

        ItemStack filter = new ItemStack(Blocks.STONE);
        CompoundTag rollerData = new CompoundTag();
        rollerData.put(
            "Filter",
            filter.save(helper.getLevel().registryAccess())
        );

        BearingContraption contraption = new BearingContraption();
        contraption.getStorage().initialize();
        CreativeCrateMountedStorage creativeSupply =
            new CreativeCrateMountedStorage(new ItemStack(Blocks.STONE));
        contraption.getStorage().attachExternal(creativeSupply);
        MovementContext context = new MovementContext(
            helper.getLevel(),
            new StructureBlockInfo(
                BlockPos.ZERO,
                AllBlocks.MECHANICAL_ROLLER.getDefaultState(),
                rollerData
            ),
            contraption
        );

        boolean changed = RollerMaterialPlacementCapture.probe(
            new RollerMovementBehaviour(),
            context,
            position,
            Blocks.STONE.defaultBlockState(),
            contraption.getStorage().getAllItems()
        );

        check(helper, changed, "Creative Crate material did not fill the Copycat");
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.STONE.defaultBlockState()
        );
        check(
            helper,
            creativeSupply.getStackInSlot(0).is(Blocks.STONE.asItem()),
            "Creative Crate material supply changed"
        );
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void rollerMaterialFillRejectsUnsupportedMaterial(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.CHEST, 2);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.CHEST),
            materialInventory
        );

        check(helper, result == FillResult.PASS, "unsupported material was not safely skipped");
        assertEmptyMaterial(helper, position);
        check(helper, count(materialInventory) == 2, "unsupported material consumed an item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialFillRequiresExactInventoryItem(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.DIRT, 2);

        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(),
            position,
            new ItemStack(Blocks.STONE),
            materialInventory
        );

        check(helper, result == FillResult.FAIL, "different inventory block funded the material");
        assertEmptyMaterial(helper, position);
        check(helper, count(materialInventory) == 2, "different inventory block was consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialPassFindsFractionalSurfaceCell(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.GRAVEL, 2);
        TrackSurfaceSample sample = new TrackSurfaceSample(
            position.getX(),
            position.getZ(),
            position.getY() - 0.5
        );

        FillPassResult result = CopycatMaterialFillingService.fillSamples(
            helper.getLevel(),
            List.of(sample),
            new ItemStack(Blocks.GRAVEL),
            materialInventory
        );

        check(helper, result.foundSurfaceCopycat(), "fractional surface Copycat was not found");
        check(helper, result.changed(), "fractional surface Copycat was not filled");
        assertSingleMaterial(helper, position, Blocks.GRAVEL.defaultBlockState());
        check(helper, count(materialInventory) == 1, "fractional surface used the wrong material cost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialPassLeavesEmptyColumnsUntouched(GameTestHelper helper) {
        BlockPos copycatPosition = resetTarget(helper);
        BlockPos emptyPosition = copycatPosition.east();
        helper.getLevel().setBlockAndUpdate(emptyPosition, Blocks.AIR.defaultBlockState());
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            copycatPosition,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.GRAVEL, 2);

        FillPassResult result = CopycatMaterialFillingService.fillSamples(
            helper.getLevel(),
            List.of(
                new TrackSurfaceSample(
                    copycatPosition.getX(),
                    copycatPosition.getZ(),
                    copycatPosition.getY() - 0.5
                ),
                new TrackSurfaceSample(
                    emptyPosition.getX(),
                    emptyPosition.getZ(),
                    emptyPosition.getY() - 0.5
                )
            ),
            new ItemStack(Blocks.GRAVEL),
            materialInventory
        );

        check(helper, result.foundSurfaceCopycat(), "material pass did not find its Copycat");
        check(helper, result.changed(), "material pass did not fill its Copycat");
        check(helper, helper.getLevel().getBlockState(emptyPosition).isAir(), "empty column received gravel");
        check(helper, count(materialInventory) == 1, "empty column consumed gravel");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialPassChoosesClosestSurface(GameTestHelper helper) {
        BlockPos upperPosition = resetTarget(helper);
        BlockPos lowerPosition = upperPosition.below();
        helper.getLevel().setBlockAndUpdate(lowerPosition, Blocks.AIR.defaultBlockState());
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            lowerPosition,
            8,
            inventory(8)
        );
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            upperPosition,
            4,
            inventory(4)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.GRAVEL, 2);

        FillPassResult result = CopycatMaterialFillingService.fillSamples(
            helper.getLevel(),
            List.of(new TrackSurfaceSample(
                upperPosition.getX(),
                upperPosition.getZ(),
                upperPosition.getY() - 0.5
            )),
            new ItemStack(Blocks.GRAVEL),
            materialInventory
        );

        check(helper, result.changed(), "closest Copycat surface was not filled");
        assertSingleMaterial(helper, upperPosition, Blocks.GRAVEL.defaultBlockState());
        assertEmptyMaterial(helper, lowerPosition);
        check(helper, count(materialInventory) == 1, "more than one vertical Copycat was filled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialPassFindsSlopedHalfLayer(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            position,
            CopycatPavingMaterial.HALF_LAYER,
            CopycatLayerPavingService.halfLayerStateFor(
                Direction.Axis.X,
                3,
                5
            ),
            inventory(CopycatPavingMaterial.HALF_LAYER, 8)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.GRAVEL, 2);

        FillPassResult result = CopycatMaterialFillingService.fillSamples(
            helper.getLevel(),
            List.of(new TrackSurfaceSample(
                position.getX(),
                position.getZ(),
                position.getY() - 0.5,
                1,
                0,
                0.25,
                0
            )),
            new ItemStack(Blocks.GRAVEL),
            materialInventory
        );

        check(helper, result.foundSurfaceCopycat(), "sloped Half Layer was not found");
        check(helper, result.changed(), "sloped Half Layer was not material-filled");
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.NEGATIVE_LAYERS.getName(),
            Blocks.GRAVEL.defaultBlockState()
        );
        assertPartMaterial(
            helper,
            position,
            CopycatHalfLayerBlock.POSITIVE_LAYERS.getName(),
            Blocks.GRAVEL.defaultBlockState()
        );
        check(helper, count(materialInventory) == 0, "sloped Half Layer used the wrong material cost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollerMaterialPassRejectsDistantDecorativeCopycat(GameTestHelper helper) {
        BlockPos surfacePosition = resetTarget(helper).above();
        BlockPos decorativePosition = surfacePosition.below(2);
        helper.getLevel().setBlockAndUpdate(decorativePosition, Blocks.AIR.defaultBlockState());
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            decorativePosition,
            8,
            inventory(8)
        );
        ItemStackHandler materialInventory = blockInventory(Blocks.GRAVEL, 1);

        FillPassResult result = CopycatMaterialFillingService.fillSamples(
            helper.getLevel(),
            List.of(new TrackSurfaceSample(
                surfacePosition.getX(),
                surfacePosition.getZ(),
                surfacePosition.getY() - 0.5
            )),
            new ItemStack(Blocks.GRAVEL),
            materialInventory
        );

        check(helper, !result.foundSurfaceCopycat(), "distant decorative Copycat activated material mode");
        check(helper, !result.changed(), "distant decorative Copycat was filled");
        assertEmptyMaterial(helper, decorativePosition);
        check(helper, count(materialInventory) == 1, "distant decorative Copycat consumed gravel");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialPlanRedirectsFullCopycatBaseBelow(GameTestHelper helper) {
        BlockPos copycatPosition = resetTarget(helper);
        BlockPos emptyNeighbor = copycatPosition.east();
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            copycatPosition,
            8,
            inventory(8)
        );

        MaterialFillPlan plan = CopycatMaterialFillingService.planSamples(
            helper.getLevel(),
            List.of(
                new TrackSurfaceSample(
                    copycatPosition.getX(),
                    copycatPosition.getZ(),
                    copycatPosition.getY()
                ),
                new TrackSurfaceSample(
                    emptyNeighbor.getX(),
                    emptyNeighbor.getZ(),
                    emptyNeighbor.getY()
                )
            ),
            new ItemStack(Blocks.GRAVEL)
        );

        check(helper, plan.protects(copycatPosition), "full Copycat was not protected");
        check(
            helper,
            plan.redirectCreateBase(copycatPosition).equals(copycatPosition.below()),
            "full Copycat base was not redirected to its support cell"
        );
        check(
            helper,
            plan.redirectCreateBase(emptyNeighbor).equals(emptyNeighbor),
            "empty neighboring column was redirected away from Create"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialPlanLeavesPartialSurfaceBaseToCreate(GameTestHelper helper) {
        BlockPos copycatPosition = resetTarget(helper);
        CopycatLayerPavingService.tryPlace(
            helper.getLevel(),
            copycatPosition,
            4,
            inventory(4)
        );
        BlockPos createBase = copycatPosition.below();

        MaterialFillPlan plan = CopycatMaterialFillingService.planSamples(
            helper.getLevel(),
            List.of(new TrackSurfaceSample(
                copycatPosition.getX(),
                copycatPosition.getZ(),
                copycatPosition.getY() - 0.5
            )),
            new ItemStack(Blocks.GRAVEL)
        );

        check(helper, plan.protects(copycatPosition), "partial Copycat was not protected");
        check(
            helper,
            plan.redirectCreateBase(createBase).equals(createBase),
            "partial Copycat incorrectly redirected Create's base support"
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
    public static void configurationDefaultsToSafeSurfaceOnlyMode(GameTestHelper helper) {
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
            CopycatRollerConfig.SURFACE_ONLY.get(),
            "surface-only paving is not enabled by default"
        );
        check(
            helper,
            Math.abs(CopycatRollerConfig.SLOPE_MAX_VERTICAL_ERROR.get() - 0.25)
                < 1.0e-9,
            "default slope error is not one quarter block"
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
    public static void halfLayerUsesSafeMinimumOfBothHalves(GameTestHelper helper) {
        TrackSurfaceSample risingEast = new TrackSurfaceSample(
            10,
            20,
            64.5,
            1,
            0,
            0.5,
            0
        );
        SurfacePlacement placement = CopycatLayerPavingService.surfacePlacementFor(
            CopycatPavingMaterial.HALF_LAYER,
            risingEast,
            LayerMath.RoundingDirection.UP,
            0.25
        ).orElseThrow();
        BlockState state = placement.state();
        check(helper, placement.pos().getY() == 65, "half-layer selected the wrong surface cell");
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.AXIS) == Direction.Axis.X,
            "half-layer gradient selected the wrong axis"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS) == 2,
            "negative half crossed its lowest track edge"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS) == 4,
            "positive half crossed its lowest track edge"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincSelectsLayerForEqualHalfHeights(GameTestHelper helper) {
        CopycatPlacement placement = CopycatLayerPavingService.zincSurfacePlacementFor(
            new TrackSurfaceSample(10, 20, 64.5, 1, 0, 0, 0),
            LayerMath.RoundingDirection.DOWN
        ).orElseThrow();

        check(
            helper,
            placement.material() == CopycatPavingMaterial.LAYER,
            "equal half heights did not collapse to Copycat Layer"
        );
        check(
            helper,
            placement.state().getValue(CopycatLayerBlock.LAYERS) == 3,
            "equal half heights produced the wrong DOWN-rounded layer count"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincSelectsHalfLayerForUnequalHalfHeights(GameTestHelper helper) {
        CopycatPlacement placement = CopycatLayerPavingService.zincSurfacePlacementFor(
            new TrackSurfaceSample(10, 20, 64.5, 1, 0, 0.5, 0),
            LayerMath.RoundingDirection.DOWN
        ).orElseThrow();

        check(
            helper,
            placement.material() == CopycatPavingMaterial.HALF_LAYER,
            "unequal half heights incorrectly collapsed to Copycat Layer"
        );
        BlockState state = placement.state();
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.AXIS) == Direction.Axis.X,
            "automatic Half Layer selected the wrong axis"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS) == 1,
            "automatic Half Layer selected the wrong negative height"
        );
        check(
            helper,
            state.getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS) == 3,
            "automatic Half Layer selected the wrong positive height"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincLayerPlacementStoresExactHalfLayerChange(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = zincInventory(1, 1);

        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.LAYER,
                CopycatLayerPavingService.stateFor(3),
                inventory
            ) == PlacementResult.SUCCESS,
            "partial automatic Layer placement failed"
        );
        assertLayer(helper, position, 3);
        check(helper, countZinc(inventory) == 0, "partial Layer did not consume one zinc ingot");
        check(
            helper,
            countMaterial(inventory, CopycatPavingMaterial.HALF_LAYER) == 10,
            "six credits did not leave ten Half Layer items as change"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincChangePaysForFollowingHalfLayer(GameTestHelper helper) {
        BlockPos first = resetTarget(helper);
        BlockPos second = first.above();
        helper.getLevel().setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        ItemStackHandler inventory = zincInventory(2, 1);

        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                first,
                CopycatPavingMaterial.LAYER,
                CopycatLayerPavingService.stateFor(3),
                inventory
            ) == PlacementResult.SUCCESS,
            "first zinc-funded Layer placement failed"
        );
        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                second,
                CopycatPavingMaterial.HALF_LAYER,
                CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.X, 2, 5),
                inventory
            ) == PlacementResult.SUCCESS,
            "Half Layer could not spend stored conversion change"
        );
        assertHalfLayer(helper, second, Direction.Axis.X, 2, 5);
        check(helper, countZinc(inventory) == 0, "following placement consumed an extra zinc ingot");
        check(
            helper,
            countMaterial(inventory, CopycatPavingMaterial.HALF_LAYER) == 3,
            "following seven-credit placement left the wrong change"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullZincPlacementsConsumeExactlyOneIngot(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler layerInventory = zincInventory(1, 1);
        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.LAYER,
                CopycatLayerPavingService.stateFor(8),
                layerInventory
            ) == PlacementResult.SUCCESS,
            "one zinc did not create eight ordinary layers"
        );
        check(helper, countZinc(layerInventory) == 0, "full Layer left zinc behind");
        check(
            helper,
            countMaterial(layerInventory, CopycatPavingMaterial.HALF_LAYER) == 0,
            "full Layer incorrectly created change"
        );

        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        ItemStackHandler halfInventory = zincInventory(1, 1);
        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.HALF_LAYER,
                CopycatLayerPavingService.halfLayerStateFor(Direction.Axis.Z, 8, 8),
                halfInventory
            ) == PlacementResult.SUCCESS,
            "one zinc did not create sixteen half layers"
        );
        check(helper, countZinc(halfInventory) == 0, "full Half Layer left zinc behind");
        check(
            helper,
            countMaterial(halfInventory, CopycatPavingMaterial.HALF_LAYER) == 0,
            "full Half Layer incorrectly created change"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincPlacementWithoutChangeSpaceIsAtomic(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = zincInventory(1, 2);

        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.LAYER,
                CopycatLayerPavingService.stateFor(3),
                inventory
            ) == PlacementResult.FAIL,
            "zinc placement succeeded without space for conversion change"
        );
        check(helper, helper.getLevel().getBlockState(position).isAir(), "failed zinc placement changed the world");
        check(helper, countZinc(inventory) == 2, "failed zinc placement lost or duplicated zinc");
        check(
            helper,
            countMaterial(inventory, CopycatPavingMaterial.HALF_LAYER) == 0,
            "failed zinc placement left conversion change behind"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincBytePlacementUsesEightToOneRecipe(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        Set<CopycatByteBlock.Byte> threeBytes = Set.of(
            CopycatByteBlock.bite(false, false, false),
            CopycatByteBlock.bite(true, false, false),
            CopycatByteBlock.bite(false, true, false)
        );
        ItemStackHandler partialInventory = zincInventory(2, 1);

        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.BYTE,
                CopycatLayerPavingService.byteStateFor(threeBytes),
                partialInventory
            ) == PlacementResult.SUCCESS,
            "zinc-funded partial Copycat Byte placement failed"
        );
        BlockState partial = helper.getLevel().getBlockState(position);
        check(helper, partial.is(CCBlocks.COPYCAT_BYTE.get()), "wrong Byte block was placed");
        check(helper, countByteParts(partial) == 3, "partial state did not contain exactly three Bytes");
        check(helper, countZinc(partialInventory) == 0, "partial Byte placement did not consume zinc");
        check(
            helper,
            countMaterial(partialInventory, CopycatPavingMaterial.BYTE) == 5,
            "three Bytes did not leave five Byte items as change"
        );
        assertEmptyMaterial(helper, position, CopycatPavingMaterial.BYTE);

        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        ItemStackHandler fullInventory = zincInventory(1, 1);
        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.BYTE,
                CopycatLayerPavingService.byteStateFor(CopycatByteBlock.allBytes),
                fullInventory
            ) == PlacementResult.SUCCESS,
            "one zinc did not create eight Copycat Bytes"
        );
        check(
            helper,
            countByteParts(helper.getLevel().getBlockState(position)) == 8,
            "full Copycat Byte state did not contain eight parts"
        );
        check(helper, countZinc(fullInventory) == 0, "full Byte placement left zinc behind");
        check(
            helper,
            countMaterial(fullInventory, CopycatPavingMaterial.BYTE) == 0,
            "full Byte placement incorrectly created change"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zincBytePlacementWithoutChangeSpaceIsAtomic(GameTestHelper helper) {
        BlockPos position = resetTarget(helper);
        ItemStackHandler inventory = zincInventory(1, 2);
        BlockState oneByte = CopycatLayerPavingService.byteStateFor(Set.of(
            CopycatByteBlock.bite(false, false, false)
        ));

        check(
            helper,
            CopycatLayerPavingService.tryPlaceWithZinc(
                helper.getLevel(),
                position,
                CopycatPavingMaterial.BYTE,
                oneByte,
                inventory
            ) == PlacementResult.FAIL,
            "Byte placement succeeded without space for conversion change"
        );
        check(helper, helper.getLevel().getBlockState(position).isAir(), "failed Byte placement changed the world");
        check(helper, countZinc(inventory) == 2, "failed Byte placement lost or duplicated zinc");
        check(
            helper,
            countMaterial(inventory, CopycatPavingMaterial.BYTE) == 0,
            "failed Byte placement left conversion change behind"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void diagonalHalfLayerDoesNotProtrudeAcrossEitherHalf(GameTestHelper helper) {
        TrackSurfaceSample diagonal = new TrackSurfaceSample(
            10,
            20,
            64.5,
            1,
            1,
            0.5,
            0.5
        );
        SurfacePlacement placement = CopycatLayerPavingService.surfacePlacementFor(
            CopycatPavingMaterial.HALF_LAYER,
            diagonal,
            LayerMath.RoundingDirection.UP,
            0.25
        ).orElseThrow();

        check(helper, placement.pos().getY() == 65, "diagonal half layer selected the wrong cell");
        check(
            helper,
            placement.state().getValue(CopycatHalfLayerBlock.NEGATIVE_LAYERS) == 0,
            "negative diagonal half protrudes above the track plane"
        );
        check(
            helper,
            placement.state().getValue(CopycatHalfLayerBlock.POSITIVE_LAYERS) == 2,
            "positive diagonal half is not capped at its safe height"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void surfaceOnlyPlanPlacesNoFullBaseBelowPartialSurface(GameTestHelper helper) {
        BlockPos basePosition = resetTarget(helper);
        TrackSurfaceSample sample = new TrackSurfaceSample(
            basePosition.getX(),
            basePosition.getZ(),
            basePosition.getY() + 0.5,
            1,
            0,
            0.5,
            0
        );
        SurfacePlacement placement = CopycatLayerPavingService.surfacePlacementFor(
            CopycatPavingMaterial.LAYER,
            sample,
            LayerMath.RoundingDirection.DOWN,
            0.25
        ).orElseThrow();
        ItemStackHandler inventory = inventory(8);

        check(
            helper,
            CopycatLayerPavingService.tryPlace(
                helper.getLevel(),
                placement.pos(),
                CopycatPavingMaterial.LAYER,
                placement.state(),
                inventory
            ) == PlacementResult.SUCCESS,
            "surface shell placement failed"
        );
        assertLayer(helper, placement.pos(), 1);
        check(
            helper,
            helper.getLevel().getBlockState(placement.pos().below()).isAir(),
            "surface-only placement created a full support block"
        );
        check(helper, count(inventory) == 7, "surface shell consumed more than its one layer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelIntegerTrackSurfaceIsSkipped(GameTestHelper helper) {
        TrackSurfaceSample level = new TrackSurfaceSample(10, 20, 64, 1, 0, 0, 0);
        for (CopycatPavingMaterial material : CopycatPavingMaterial.values()) {
            if (material == CopycatPavingMaterial.BYTE) {
                continue;
            }
            check(
                helper,
                CopycatLayerPavingService.surfacePlacementFor(
                    material,
                    level,
                    LayerMath.RoundingDirection.DOWN,
                    0.25
                ).isEmpty(),
                "level integer track created a needless full " + material
            );
        }
        check(
            helper,
            CopycatLayerPavingService.surfacePlacementFor(
                CopycatPavingMaterial.SLOPE_LAYER,
                new TrackSurfaceSample(10, 20, 64.5, 1, 0, 0, 0),
                LayerMath.RoundingDirection.UP,
                0.25
            ).isEmpty(),
            "level fractional track created an artificial Slope Layer wedge"
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
    public static void slopeQualityGateSkipsSawtoothState(GameTestHelper helper) {
        TrackSurfaceSample betweenRepresentableShapes = new TrackSurfaceSample(
            10,
            20,
            64.5,
            1,
            0,
            0.25,
            0
        );
        check(
            helper,
            CopycatLayerPavingService.surfacePlacementFor(
                CopycatPavingMaterial.SLOPE_LAYER,
                betweenRepresentableShapes,
                LayerMath.RoundingDirection.DOWN,
                0.25
            ).isEmpty(),
            "slope quality gate accepted a sawtooth state"
        );

        TrackSurfaceSample representable = new TrackSurfaceSample(
            10,
            20,
            64.25,
            1,
            0,
            0.25,
            0
        );
        SurfacePlacement placement = CopycatLayerPavingService.surfacePlacementFor(
            CopycatPavingMaterial.SLOPE_LAYER,
            representable,
            LayerMath.RoundingDirection.DOWN,
            0.25
        ).orElseThrow();
        check(
            helper,
            placement.state().getValue(CopycatSlopeLayerBlock.LAYERS) == 1,
            "representable quarter slope selected the wrong state"
        );
        check(
            helper,
            placement.state().getValue(CopycatSlopeLayerBlock.FACING) == Direction.EAST,
            "representable quarter slope faces downhill"
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
    public static void wideFillBytesRespectCombinedRollerFootprint(
        GameTestHelper helper
    ) {
        Level level = helper.getLevel();
        BearingContraption contraption = new BearingContraption();
        contraption.getStorage().initialize();
        CreativeCrateMountedStorage zincSupply =
            new CreativeCrateMountedStorage(AllItems.ZINC_INGOT.asStack());
        contraption.getStorage().attachExternal(zincSupply);

        CompoundTag rollerData = new CompoundTag();
        rollerData.putInt("ScrollValue", 2);
        BlockState rollerState = AllBlocks.MECHANICAL_ROLLER
            .getDefaultState()
            .setValue(RollerBlock.FACING, Direction.NORTH);
        List<MovementContext> rollers = List.of(
            rollerContext(level, contraption, rollerState, rollerData, -1),
            rollerContext(level, contraption, rollerState, rollerData, 0),
            rollerContext(level, contraption, rollerState, rollerData, 1)
        );

        BlockPos start = helper.absolutePos(new BlockPos(4, 8, 4));
        Vec3 first = Vec3.atCenterOf(start);
        Vec3 second = Vec3.atCenterOf(start.offset(0, 0, 5));
        TrackGraph graph = new TrackGraph();
        TrackNode node1 = node(level, first, 101);
        TrackNode node2 = node(level, second, 102);
        TrackEdge edge = new TrackEdge(
            node1,
            node2,
            null,
            TrackMaterial.ANDESITE
        );

        Map<MovementContext, PaveTask> profiles = new HashMap<>();
        Set<Long> centralColumns = new java.util.HashSet<>();
        for (MovementContext roller : rollers) {
            // For a +Z edge, TrackPaver's positive interval points toward
            // world -X, opposite the local east coordinate used by the row.
            double offset = -roller.localPos.getX();
            PaveTask task = new PaveTask(offset, offset);
            TrackPaverV2.pave(task, graph, edge, 0, edge.getLength());
            profiles.put(roller, task);
            for (Couple<Integer> key : task.keys()) {
                centralColumns.add(BlockPos.asLong(
                    key.getFirst(),
                    0,
                    key.getSecond()
                ));
            }
        }

        int[] neighbourProfiles = {0};
        MovementContext edgeRoller = rollers.getFirst();
        boolean changed = CopycatWideFillPavingService.pave(
            edgeRoller,
            start,
            profiles.get(edgeRoller),
            roller -> {
                neighbourProfiles[0]++;
                return profiles.get(roller);
            }
        );
        check(helper, changed, "combined Roller footprint placed no Bytes");
        check(
            helper,
            neighbourProfiles[0] == 2,
            "the edge Roller did not read both neighbouring profiles"
        );

        edgeRoller.rotation = vector -> vector.yRot((float) Math.PI);
        boolean changedAfterCarriageTurn = CopycatWideFillPavingService.pave(
            edgeRoller,
            start,
            profiles.get(edgeRoller),
            profiles::get
        );
        check(
            helper,
            !changedAfterCarriageTurn,
            "carriage yaw created a second slope at another track radius"
        );

        int minimumX = centralColumns.stream()
            .mapToInt(value -> BlockPos.getX(value))
            .min()
            .orElseThrow();
        int maximumX = centralColumns.stream()
            .mapToInt(value -> BlockPos.getX(value))
            .max()
            .orElseThrow();
        int minimumZ = centralColumns.stream()
            .mapToInt(value -> BlockPos.getZ(value))
            .min()
            .orElseThrow();
        int maximumZ = centralColumns.stream()
            .mapToInt(value -> BlockPos.getZ(value))
            .max()
            .orElseThrow();

        boolean foundByte = false;
        for (int x = minimumX - 8; x <= maximumX + 8; x++) {
            for (int z = minimumZ - 8; z <= maximumZ + 8; z++) {
                for (int y = start.getY() - 10; y <= start.getY() + 3; y++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (!level.getBlockState(position).is(CCBlocks.COPYCAT_BYTE.get())) {
                        continue;
                    }
                    foundByte = true;
                    check(
                        helper,
                        !centralColumns.contains(BlockPos.asLong(x, 0, z)),
                        "Copycat Byte entered the combined central footprint"
                    );
                }
            }
        }
        check(helper, foundByte, "placed Byte surface was not found");
        check(
            helper,
            CopycatLayerPavingService.isZincIngot(zincSupply.getStackInSlot(0)),
            "Creative Crate lost its zinc supply"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyEndsOfRollerRowOwnWideFillSides(GameTestHelper helper) {
        List<BlockPos> row = List.of(
            new BlockPos(-2, 4, 7),
            new BlockPos(-1, 4, 7),
            new BlockPos(0, 4, 7),
            new BlockPos(1, 4, 7),
            new BlockPos(2, 4, 7),
            new BlockPos(-20, 4, 8),
            new BlockPos(20, 5, 7)
        );
        var west = RollerEdgeSelection.select(
            new BlockPos(-2, 4, 7), Direction.NORTH, row
        );
        var center = RollerEdgeSelection.select(
            new BlockPos(0, 4, 7), Direction.NORTH, row
        );
        var east = RollerEdgeSelection.select(
            new BlockPos(2, 4, 7), Direction.NORTH, row
        );
        check(helper, west.counterClockwiseOuter(), "west Roller lost its outward side");
        check(helper, !west.clockwiseOuter(), "west Roller received an inward side");
        check(helper, !center.hasOuterSide(), "interior Roller received a Byte slope");
        check(helper, !east.counterClockwiseOuter(), "east Roller received an inward side");
        check(helper, east.clockwiseOuter(), "east Roller lost its outward side");

        var single = RollerEdgeSelection.select(
            BlockPos.ZERO, Direction.SOUTH, List.of(BlockPos.ZERO)
        );
        check(helper, single.counterClockwiseOuter(), "single Roller lost its left side");
        check(helper, single.clockwiseOuter(), "single Roller lost its right side");
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
        check(helper, !RollerModeGate.isWideFill(rollerData), "TUNNEL_PAVE entered zinc Wide Fill");
        rollerData.putInt("ScrollValue", 1);
        check(helper, RollerModeGate.isStraightFill(rollerData), "STRAIGHT_FILL missed the compatibility branch");
        check(helper, !RollerModeGate.isWideFill(rollerData), "STRAIGHT_FILL entered zinc Wide Fill");
        rollerData.putInt("ScrollValue", 2);
        check(helper, !RollerModeGate.isStraightFill(rollerData), "WIDE_FILL entered the straight compatibility branch");
        check(helper, RollerModeGate.isWideFill(rollerData), "WIDE_FILL missed the zinc compatibility branch");
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
            Class.forName(
                "dev.example.copycatroller.paving.CopycatMaterialFillingService",
                false,
                CopycatRoller.class.getClassLoader()
            );
            Class.forName(
                "dev.example.copycatroller.paving.CopycatWideFillPavingService",
                false,
                CopycatRoller.class.getClassLoader()
            );
            Class.forName(
                "dev.example.copycatroller.paving.WideFillBytePlanner",
                false,
                CopycatRoller.class.getClassLoader()
            );            Class.forName(
                "dev.example.copycatroller.paving.RollerEdgeSelection",
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

    private static ItemStack randomizeFilterWith(BlockState material)
        throws ReflectiveOperationException {
        ResourceLocation filterId = ResourceLocation.fromNamespaceAndPath(
            "createrandomizefilters",
            "randomize_filter"
        );
        ItemStack filter = new ItemStack(BuiltInRegistries.ITEM.get(filterId));
        Class<?> listClass = Class.forName(
            "com.createrandomizefilters.component.FilterBlockList"
        );
        Object list = listClass
            .getConstructor(List.class, int.class)
            .newInstance(
                List.of(BuiltInRegistries.BLOCK.getKey(material.getBlock())),
                0
            );
        DataComponentType<?> component =
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath(
                    "createrandomizefilters",
                    "filter_block_list"
                )
            );
        setComponent(filter, component, list);
        return filter;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setComponent(
        ItemStack stack,
        DataComponentType<?> component,
        Object value
    ) {
        stack.set((DataComponentType) component, value);
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

    private static MovementContext rollerContext(
        Level level,
        BearingContraption contraption,
        BlockState rollerState,
        CompoundTag rollerData,
        int localX
    ) {
        BlockPos localPosition = new BlockPos(localX, 0, 0);
        StructureBlockInfo info = new StructureBlockInfo(
            localPosition,
            rollerState,
            rollerData.copy()
        );
        MovementContext context = new MovementContext(
            level,
            info,
            contraption
        );
        contraption.getActors().add(MutablePair.of(info, context));
        return context;
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

    private static ItemStackHandler blockInventory(
        net.minecraft.world.level.block.Block block,
        int count
    ) {
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(block, count));
        return inventory;
    }

    private static ItemStackHandler zincInventory(int slots, int count) {
        ItemStackHandler inventory = new ItemStackHandler(slots);
        inventory.setStackInSlot(0, new ItemStack(AllItems.ZINC_INGOT.get(), count));
        return inventory;
    }

    private static int count(ItemStackHandler inventory) {
        return inventory.getStackInSlot(0).getCount();
    }

    private static int countZinc(ItemStackHandler inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (CopycatLayerPavingService.isZincIngot(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countMaterial(
        ItemStackHandler inventory,
        CopycatPavingMaterial material
    ) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (material.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
    private static int countByteParts(BlockState state) {
        int count = 0;
        for (CopycatByteBlock.Byte bite : CopycatByteBlock.allBytes) {
            count += state.getValue(CopycatByteBlock.byByte(bite)) ? 1 : 0;
        }
        return count;
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

    private static void assertSingleMaterial(
        GameTestHelper helper,
        BlockPos position,
        BlockState expectedMaterial
    ) {
        Object blockEntity = helper.getLevel().getBlockEntity(position);
        check(helper, blockEntity instanceof ICopycatBlockEntity, "expected Copycats+ block entity is missing");
        ICopycatBlockEntity copycat = (ICopycatBlockEntity) blockEntity;
        check(helper, copycat.getMaterial().is(expectedMaterial.getBlock()), "unexpected Copycat material");
        check(
            helper,
            ItemStack.isSameItemSameComponents(
                copycat.getConsumedItem(),
                new ItemStack(expectedMaterial.getBlock())
            ),
            "Copycat stored the wrong consumed material item"
        );
        check(helper, copycat.getConsumedItem().getCount() == 1, "Copycat stored an invalid material item count");
    }

    private static void assertPartMaterial(
        GameTestHelper helper,
        BlockPos position,
        String property,
        BlockState expectedMaterial
    ) {
        Object blockEntity = helper.getLevel().getBlockEntity(position);
        check(
            helper,
            blockEntity instanceof IMultiStateCopycatBlockEntity,
            "expected multistate Copycats+ block entity is missing"
        );
        IMultiStateCopycatBlockEntity copycat =
            (IMultiStateCopycatBlockEntity) blockEntity;
        var stored = copycat.getMaterialItemStorage().getMaterialItem(property);
        check(helper, stored != null, "Copycat material property is missing: " + property);
        check(helper, stored.material().is(expectedMaterial.getBlock()), "unexpected material for " + property);
        check(
            helper,
            ItemStack.isSameItemSameComponents(
                stored.consumedItem(),
                new ItemStack(expectedMaterial.getBlock())
            ),
            "wrong consumed material item for " + property
        );
        check(helper, stored.consumedItem().getCount() == 1, "invalid consumed item count for " + property);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
