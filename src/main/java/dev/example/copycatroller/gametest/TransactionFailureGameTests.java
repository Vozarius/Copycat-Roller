package dev.example.copycatroller.gametest;

import java.util.Set;
import java.util.List;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.bytes.CopycatByteBlock;
import com.copycatsplus.copycats.content.copycat.layer.CopycatLayerBlock;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlockEntity;
import com.simibubi.create.AllItems;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.example.copycatroller.CopycatRoller;
import dev.example.copycatroller.paving.CopycatLayerPavingService;
import dev.example.copycatroller.paving.CopycatLayerPavingService.PlacementResult;
import dev.example.copycatroller.paving.CopycatMaterialFillingService;
import dev.example.copycatroller.paving.CopycatMaterialFillingService.FillResult;
import dev.example.copycatroller.paving.CopycatPavingMaterial;
import dev.example.copycatroller.paving.TrackSurfaceSample;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(CopycatRoller.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TransactionFailureGameTests {
    private static final BlockPos TARGET = new BlockPos(1, 2, 1);

    private TransactionFailureGameTests() {
    }

    @GameTest(template = "empty")
    public static void refusedExtractionCannotFundShapesOrZinc(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(TARGET);
        helper.setBlock(TARGET, Blocks.AIR);
        var shapes = new ShortExtraction(new ItemStack(CCBlocks.COPYCAT_LAYER.asItem(), 2), 0);
        var state = CCBlocks.COPYCAT_LAYER.getDefaultState().setValue(CopycatLayerBlock.LAYERS, 2);
        var result = CopycatLayerPavingService.tryPlace(
            helper.getLevel(), position, CopycatPavingMaterial.LAYER, state, shapes
        );
        check(helper, result == PlacementResult.FAIL, "refused extraction funded a shape");
        check(helper, shapes.getStackInSlot(0).getCount() == 2, "shape stock changed");
        check(helper, helper.getLevel().getBlockState(position).isAir(), "unpaid shape was placed");

        var zinc = new ShortExtraction(new ItemStack(AllItems.ZINC_INGOT.get(), 2), 0);
        result = CopycatLayerPavingService.tryPlaceWithZinc(
            helper.getLevel(), position, CopycatPavingMaterial.LAYER, state, zinc
        );
        check(helper, result == PlacementResult.FAIL, "refused extraction funded zinc conversion");
        check(helper, zinc.getStackInSlot(0).getCount() == 2, "zinc stock changed");
        check(helper, helper.getLevel().getBlockState(position).isAir(), "unpaid zinc shape was placed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialExtractionRestoresStockWithoutPlacing(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.AIR);
        BlockPos position = helper.absolutePos(TARGET);
        var stock = new ShortExtraction(new ItemStack(CCBlocks.COPYCAT_LAYER.asItem(), 2), 1);
        var state = CCBlocks.COPYCAT_LAYER.getDefaultState().setValue(CopycatLayerBlock.LAYERS, 2);
        var result = CopycatLayerPavingService.tryPlace(
            helper.getLevel(), position, CopycatPavingMaterial.LAYER, state, stock
        );
        check(helper, result == PlacementResult.FAIL, "partial extraction funded a complete shape");
        check(helper, stock.getStackInSlot(0).getCount() == 2, "partial extraction was not restored");
        check(helper, helper.getLevel().getBlockState(position).isAir(), "partial payment changed the world");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refusedExtractionCannotFillMaterial(GameTestHelper helper) {
        helper.setBlock(TARGET, CCBlocks.COPYCAT_LAYER.getDefaultState());
        BlockPos position = helper.absolutePos(TARGET);
        var stock = new ShortExtraction(new ItemStack(Blocks.STONE, 2), 0);
        FillResult result = CopycatMaterialFillingService.tryFill(
            helper.getLevel(), position, new ItemStack(Blocks.STONE), stock
        );
        check(helper, result == FillResult.FAIL, "refused extraction funded Copycat material");
        var copycat = (ICopycatBlockEntity) helper.getLevel().getBlockEntity(position);
        check(helper, !copycat.hasCustomMaterial() && copycat.getConsumedItem().isEmpty(), "unpaid material remains");
        check(helper, stock.getStackInSlot(0).getCount() == 2, "material stock changed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalSingleNotificationFailureRestoresMaterialAndPayment(GameTestHelper helper) {
        helper.setBlock(TARGET, CCBlocks.COPYCAT_LAYER.getDefaultState());
        BlockPos position = helper.absolutePos(TARGET);
        var original = helper.getLevel().getBlockEntity(position);
        var failing = new CCCopycatBlockEntity(original.getType(), position, original.getBlockState()) {
            private boolean assigned;

            @Override
            public void setConsumedItem(ItemStack stack) {
                super.setConsumedItem(stack);
                assigned = true;
            }

            @Override
            public void notifyUpdate() {
                if (assigned) throw new IllegalStateException("injected final single notification failure");
                super.notifyUpdate();
            }
        };
        helper.getLevel().removeBlockEntity(position);
        helper.getLevel().setBlockEntity(failing);
        var stock = new ItemStackHandler(1);
        // One finite item was already extracted by Create's placement probe.
        FillResult result = CopycatMaterialFillingService.tryFillWithPrepaid(
            helper.getLevel(), position, new ItemStack(Blocks.STONE), new ItemStack(Blocks.STONE), stock
        );
        check(helper, result == FillResult.FAIL, "notification failure reported success");
        check(helper, !failing.hasCustomMaterial() && failing.getConsumedItem().isEmpty(), "single material survived rollback");
        check(helper, stock.getStackInSlot(0).getCount() == 1, "prepayment was not refunded exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finalMultistateNotificationFailureRestoresEveryPart(GameTestHelper helper) {
        var state = CopycatLayerPavingService.byteStateFor(Set.of(
            CopycatByteBlock.bite(false, false, false), CopycatByteBlock.bite(true, false, false)
        ));
        helper.setBlock(TARGET, state);
        BlockPos position = helper.absolutePos(TARGET);
        var original = helper.getLevel().getBlockEntity(position);
        var failing = new MultiStateCopycatBlockEntity(original.getType(), position, state) {
            private int assignments;

            @Override
            public void setConsumedItem(String property, ItemStack stack) {
                super.setConsumedItem(property, stack);
                assignments++;
            }

            @Override
            public void notifyUpdate() {
                if (assignments == 2) throw new IllegalStateException("injected final multistate notification failure");
                super.notifyUpdate();
            }
        };
        helper.getLevel().removeBlockEntity(position);
        helper.getLevel().setBlockEntity(failing);
        var stock = new ItemStackHandler(1);
        stock.setStackInSlot(0, new ItemStack(Blocks.STONE));
        FillResult result = CopycatMaterialFillingService.tryFillWithPrepaid(
            helper.getLevel(), position, new ItemStack(Blocks.STONE), new ItemStack(Blocks.STONE), stock
        );
        check(helper, result == FillResult.FAIL, "multistate notification failure reported success");
        for (var bite : Set.of(CopycatByteBlock.bite(false, false, false), CopycatByteBlock.bite(true, false, false))) {
            var part = failing.getMaterialItemStorage().getMaterialItem(CopycatByteBlock.byByte(bite).getName());
            check(helper, !part.hasCustomMaterial() && part.consumedItem().isEmpty(), "Byte material survived rollback");
        }
        check(helper, stock.getStackInSlot(0).getCount() == 2, "multistate payment was not refunded exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extremeDepthStillFindsCopycatsAtPositiveAndNegativeHeights(GameTestHelper helper) {
        var depth = AllConfigs.server().kinetics.rollerFillDepth;
        int previous = depth.get();
        BlockPos column = helper.absolutePos(TARGET);
        try {
            for (int configured : new int[] {Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                depth.set(configured);
                for (int y : new int[] {-32, 64}) {
                    BlockPos position = new BlockPos(column.getX(), y, column.getZ());
                    var state = CCBlocks.COPYCAT_LAYER.getDefaultState();
                    helper.getLevel().setBlockAndUpdate(position, state);
                    try {
                        var plan = CopycatMaterialFillingService.planSamplesForAnyFilter(
                            helper.getLevel(), List.of(new TrackSurfaceSample(position.getX(), position.getZ(), y))
                        );
                        check(helper, plan.targets().stream().anyMatch(
                            target -> target.copycatPosition().equals(position)
                        ), "extreme depth skipped a Copycat at y=" + y);
                    } finally {
                        helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        } finally {
            depth.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialMaterialPaymentRefundsBothPrepaidAndExtractedItems(GameTestHelper helper) {
        var state = CopycatLayerPavingService.byteStateFor(Set.of(
            CopycatByteBlock.bite(false, false, false),
            CopycatByteBlock.bite(true, false, false),
            CopycatByteBlock.bite(false, false, true)
        ));
        helper.setBlock(TARGET, state);
        BlockPos position = helper.absolutePos(TARGET);
        var stock = new ShortExtraction(new ItemStack(Blocks.STONE, 2), 1);
        FillResult result = CopycatMaterialFillingService.tryFillWithPrepaid(
            helper.getLevel(), position, new ItemStack(Blocks.STONE), new ItemStack(Blocks.STONE), stock
        );
        check(helper, result == FillResult.FAIL, "partial payment filled multistate material");
        var copycat = (MultiStateCopycatBlockEntity) helper.getLevel().getBlockEntity(position);
        check(helper, copycat.getMaterialItemStorage().getAllConsumedItems().stream().allMatch(ItemStack::isEmpty),
            "partially paid Byte retained consumed items");
        check(helper, stock.getStackInSlot(0).getCount() == 3, "partial extraction or prepayment was lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extractionExceptionReturnsItemsEvenWhenInventoryRejectsRefund(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.AIR);
        BlockPos position = helper.absolutePos(TARGET);
        var stock = new ItemStackHandler(1) {
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (simulate) return super.extractItem(slot, amount, true);
                super.extractItem(slot, 1, false);
                throw new IllegalStateException("injected extraction failure after debit");
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
        stock.setStackInSlot(0, new ItemStack(CCBlocks.COPYCAT_LAYER.asItem(), 2));
        var state = CCBlocks.COPYCAT_LAYER.getDefaultState().setValue(CopycatLayerBlock.LAYERS, 2);
        var result = CopycatLayerPavingService.tryPlace(
            helper.getLevel(), position, CopycatPavingMaterial.LAYER, state, stock
        );
        check(helper, result == PlacementResult.FAIL, "throwing handler funded a shape");
        check(helper, helper.getLevel().getBlockState(position).isAir(), "exception placed a shape");
        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1));
        int refunded = dropped.stream().filter(entity -> entity.getItem().is(CCBlocks.COPYCAT_LAYER.asItem()))
            .mapToInt(entity -> entity.getItem().getCount()).sum();
        check(helper, refunded + stock.getStackInSlot(0).getCount() == 2, "rejected rollback lost or duplicated items");
        helper.succeed();
    }

    private static final class ShortExtraction extends ItemStackHandler {
        private final int actualLimit;

        private ShortExtraction(ItemStack stack, int actualLimit) {
            super(1);
            setStackInSlot(0, stack);
            this.actualLimit = actualLimit;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return super.extractItem(slot, simulate ? amount : Math.min(amount, actualLimit), simulate);
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
