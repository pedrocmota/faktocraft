package com.faktocraft.gametest.logistics;

import com.faktocraft.common.block.impl.logistics.BlockEntityChassis;
import com.faktocraft.common.block.impl.logistics.BlockEntityLogisticsController;
import com.faktocraft.common.block.impl.logistics.BlockEntityRecipePipe;
import com.faktocraft.common.block.impl.logistics.BlockEntityRequestTable;
import com.faktocraft.common.block.impl.logistics.IoMode;
import com.faktocraft.common.block.impl.logistics.LogisticsRegistry;
import com.faktocraft.common.block.impl.logistics.TaskLedger;
import com.faktocraft.common.block.impl.machines.fueling_station.BlockEntityFuelingStation;
import com.faktocraft.common.block.impl.machines.fueling_station.FuelingStationRegistry;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.item.base.FluidItem;
import com.faktocraft.common.registries.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public class RecipePipeBatchGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final String BREWING_STAND_ID = "minecraft:brewing_stand";
  private static final int BREWING_SLOTS = 5;

  static final BlockPos STOCK = new BlockPos(1, 1, 1);
  private static final BlockPos CORE = new BlockPos(3, 2, 1);
  static final BlockPos TABLE = new BlockPos(5, 1, 1);
  static final BlockPos PIPE = new BlockPos(4, 1, 2);
  static final BlockPos MACHINE = new BlockPos(4, 1, 3);

  static ItemStack waterBottle() {
    return PotionContents.createItemStack(Items.POTION, Potions.WATER);
  }

  static ItemStack awkwardPotion() {
    return PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
  }

  private static boolean isPotion(ItemStack stack, Holder<Potion> potion) {
    return stack.is(Items.POTION)
        && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(potion);
  }

  private static void fillCore(GameTestHelper helper, BlockPos rel) {
    if (TestUtil.blockEntity(helper, rel) instanceof FaktocraftBlockEntity be) {
      be.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
      be.getEnergyStorage().setEnergy(be.getEnergyStorage().maxEnergy());
    }
  }

  private static void putModule(GameTestHelper helper, BlockPos rel, int slot, ItemStack module) {
    if (TestUtil.blockEntity(helper, rel) instanceof BlockEntityChassis chassis) {
      chassis.getModules().setStackInSlot(slot, module);
    } else {
      helper.fail("no chassis at " + rel);
    }
  }

  private static void placeChest(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
    helper.setBlock(rel, Blocks.CHEST.defaultBlockState());
    if (TestUtil.blockEntity(helper, rel) instanceof ChestBlockEntity chest) {
      for (int i = 0; i < stacks.length; i++) {
        chest.setItem(i, stacks[i]);
      }
    }
  }

  static int countInChest(GameTestHelper helper, BlockPos rel, java.util.function.Predicate<ItemStack> what) {
    if (!(TestUtil.blockEntity(helper, rel) instanceof ChestBlockEntity chest)) {
      return -1;
    }
    int count = 0;
    for (int i = 0; i < chest.getContainerSize(); i++) {
      ItemStack stack = chest.getItem(i);
      if (what.test(stack)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  static int countIn(Container container, java.util.function.Predicate<ItemStack> what) {
    int count = 0;
    for (int i = 0; i < container.getContainerSize(); i++) {
      ItemStack stack = container.getItem(i);
      if (what.test(stack)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  static void buildNetwork(GameTestHelper helper, ItemStack... provided) {
    placeChest(helper, STOCK, provided);
    helper.setBlock(new BlockPos(2, 1, 1), LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(new BlockPos(3, 1, 1), LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(new BlockPos(4, 1, 1), LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(TABLE, LogisticsRegistry.REQUEST_TABLE.defaultBlockState());
    helper.setBlock(CORE, LogisticsRegistry.LOGISTICS_CONTROLLER.defaultBlockState());
    fillCore(helper, CORE);
    putModule(helper, new BlockPos(2, 1, 1), 0, new ItemStack(LogisticsRegistry.MODULE_PROVIDER));
    helper.setBlock(PIPE, LogisticsRegistry.RECIPE_PIPE.defaultBlockState());
  }

  static ItemStack[] brewingStock(int bottles, int wart, int blaze) {
    ItemStack[] stacks = new ItemStack[bottles + 2];
    for (int i = 0; i < bottles; i++) {
      stacks[i] = waterBottle();
    }
    stacks[bottles] = new ItemStack(Items.NETHER_WART, wart);
    stacks[bottles + 1] = new ItemStack(Items.BLAZE_POWDER, blaze);
    return stacks;
  }

  private static boolean configureBrewingRecipe(GameTestHelper helper) {
    helper.setBlock(MACHINE, Blocks.BREWING_STAND.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, PIPE) instanceof BlockEntityRecipePipe pipe)) {
      helper.fail("no recipe pipe");
      return false;
    }
    int recipe = pipe.addRecipe();
    pipe.setIo(recipe, 0, waterBottle());
    pipe.bind(recipe, 0, 0, 2, BREWING_STAND_ID, BREWING_SLOTS);
    pipe.setIo(recipe, 1, new ItemStack(Items.NETHER_WART));
    pipe.setMode(recipe, 1, IoMode.PER_BATCH);
    pipe.bind(recipe, 1, 3, 3, BREWING_STAND_ID, BREWING_SLOTS);
    pipe.setIo(recipe, 2, new ItemStack(Items.BLAZE_POWDER));
    pipe.setMode(recipe, 2, IoMode.MAINTAIN);
    pipe.bind(recipe, 2, 4, 4, BREWING_STAND_ID, BREWING_SLOTS);
    pipe.setBatch(recipe, 3);
    pipe.setIo(recipe, BlockEntityRecipePipe.outputId(0), awkwardPotion());
    pipe.bind(recipe, BlockEntityRecipePipe.outputId(0), 0, 2, BREWING_STAND_ID, BREWING_SLOTS);
    return true;
  }

  static void requestPotions(GameTestHelper helper, int quantity) {
    helper.runAfterDelay(20, () -> {
      if (TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table) {
        table.setGhostTarget(awkwardPotion());
        table.request(null, quantity);
      } else {
        helper.fail("no request table");
      }
    });
  }

  static String ledgerState(GameTestHelper helper) {
    StringBuilder out = new StringBuilder();
    if (TestUtil.blockEntity(helper, MACHINE) instanceof BrewingStandBlockEntity stand) {
      out.append("stand=[");
      for (int i = 0; i < stand.getContainerSize(); i++) {
        ItemStack stack = stand.getItem(i);
        out.append(i).append(':').append(stack.getItem()).append('x').append(stack.getCount()).append(' ');
      }
      out.append("] ");
    }
    if (TestUtil.blockEntity(helper, CORE) instanceof BlockEntityLogisticsController core) {
      for (TaskLedger.TaskSummary task : core.getLedger().summaries()) {
        out.append("task{").append(task.stateKey()).append(' ').append(task.detail()).append(" x")
            .append(task.count());
        for (TaskLedger.SubRecord sub : task.subs()) {
          out.append(" |").append(sub.kind()).append(':').append(sub.stateKey()).append(" x").append(sub.count());
        }
        out.append("} ");
      }
      out.append("cfgErrors=").append(core.getConfigErrors());
      for (TaskLedger.HistoryRecord record : core.getLedger().userHistory()) {
        out.append(" [").append(record.stateKey()).append(' ').append(record.detail()).append(']');
      }
    }
    return out.toString();
  }

  static void expectPotions(GameTestHelper helper, int quantity, int wartUsed, int stockBottles,
      int stockWart) {
    helper.succeedWhen(() -> {
      if (!(TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table)) {
        helper.fail("no request table");
        return;
      }
      int delivered = countIn(table.getItemStackHandler(), stack -> isPotion(stack, Potions.AWKWARD));
      if (delivered != quantity) {
        helper.fail("expected " + quantity + " awkward potions at the table, found " + delivered + "; "
            + ledgerState(helper));
        return;
      }
      if (!(TestUtil.blockEntity(helper, MACHINE) instanceof BrewingStandBlockEntity stand)) {
        helper.fail("no brewing stand");
        return;
      }
      if (!stand.getItem(3).isEmpty()) {
        helper.fail("nether wart left in the stand: " + stand.getItem(3) + "; " + ledgerState(helper));
        return;
      }
      for (int i = 0; i < 3; i++) {
        if (!stand.getItem(i).isEmpty()) {
          helper.fail("bottle slot " + i + " still holds " + stand.getItem(i) + "; " + ledgerState(helper));
          return;
        }
      }
      if (!stand.getItem(4).is(Items.BLAZE_POWDER)) {
        helper.fail("the fuel slot was not kept stocked: " + stand.getItem(4) + "; " + ledgerState(helper));
        return;
      }
      int bottles = countInChest(helper, STOCK, stack -> isPotion(stack, Potions.WATER));
      int wart = countInChest(helper, STOCK, stack -> stack.is(Items.NETHER_WART));
      if (bottles != stockBottles - quantity) {
        helper.fail("expected " + (stockBottles - quantity) + " water bottles left in stock, found " + bottles);
        return;
      }
      if (wart != stockWart - wartUsed) {
        helper.fail("expected " + (stockWart - wartUsed) + " nether wart left in stock, found " + wart);
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1400)
  public void brewingStandBatchesFourPotions(GameTestHelper helper) {
    buildNetwork(helper, brewingStock(8, 8, 4));
    if (!configureBrewingRecipe(helper)) {
      return;
    }
    requestPotions(helper, 4);
    expectPotions(helper, 4, 2, 8, 8);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1900)
  public void brewingStandBatchesSevenPotions(GameTestHelper helper) {
    buildNetwork(helper, brewingStock(8, 8, 4));
    if (!configureBrewingRecipe(helper)) {
      return;
    }
    requestPotions(helper, 7);
    expectPotions(helper, 7, 3, 8, 8);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1400)
  public void brewingStandLeftoverBottlesReturnToStock(GameTestHelper helper) {
    buildNetwork(helper, brewingStock(8, 8, 4));
    if (!configureBrewingRecipe(helper)) {
      return;
    }
    ItemStack sink = new ItemStack(LogisticsRegistry.MODULE_SINK);
    java.util.Map<String, Boolean> everything = new java.util.LinkedHashMap<>();
    everything.put(com.faktocraft.common.block.impl.logistics.LogisticsItemTree.NODE_ALL, true);
    com.faktocraft.common.block.impl.logistics.ModuleSettings.putTreeOverrides(sink, everything);
    putModule(helper, new BlockPos(2, 1, 1), 1, sink);
    if (TestUtil.blockEntity(helper, MACHINE) instanceof BrewingStandBlockEntity stand) {
      stand.setItem(1, waterBottle());
    }
    requestPotions(helper, 1);
    helper.succeedWhen(() -> {
      if (!(TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table)) {
        helper.fail("no request table");
        return;
      }
      int delivered = countIn(table.getItemStackHandler(), stack -> isPotion(stack, Potions.AWKWARD));
      if (delivered != 1) {
        helper.fail("expected 1 awkward potion at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!(TestUtil.blockEntity(helper, MACHINE) instanceof BrewingStandBlockEntity stand)) {
        helper.fail("no brewing stand");
        return;
      }
      int extra = countIn(stand, stack -> isPotion(stack, Potions.AWKWARD));
      if (extra != 0) {
        helper.fail("the surplus potion was not collected from the stand; " + ledgerState(helper));
        return;
      }
      int stocked = countInChest(helper, STOCK, stack -> isPotion(stack, Potions.AWKWARD));
      if (stocked != 1) {
        helper.fail("the surplus potion should have gone to the sink chest, found " + stocked + "; "
            + ledgerState(helper));
      }
    });
  }

  private static ItemStack cell(int milliBuckets) {
    ItemStack stack = new ItemStack(ModItems.FLUID_CELL);
    if (milliBuckets > 0) {
      FluidItem.setFluid(stack, Fluids.WATER, milliBuckets);
    }
    return stack;
  }

  private static int fluidInTable(GameTestHelper helper, BlockPos rel) {
    if (!(TestUtil.blockEntity(helper, rel) instanceof BlockEntityRequestTable table)) {
      helper.fail("no request table");
      return -1;
    }
    for (int i = 0; i < table.getItemStackHandler().getSlots(); i++) {
      ItemStack stack = table.getItemStackHandler().getStackInSlot(i);
      if (stack.is(ModItems.FLUID_CELL)) {
        return FluidItem.getFluidAmount(stack);
      }
    }
    return -1;
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 900)
  public void legacyRecipeStillRunsOnTheFuelingStation(GameTestHelper helper) {
    buildNetwork(helper, cell(0));
    BlockState stationState = FuelingStationRegistry.FUELING_STATION.defaultBlockState();
    if (FuelingStationRegistry.FUELING_STATION instanceof IStateFacing facing) {
      stationState = facing.setDirection(stationState, Direction.SOUTH);
    }
    helper.setBlock(MACHINE, stationState);
    fillCore(helper, MACHINE);
    if (TestUtil.blockEntity(helper, MACHINE) instanceof BlockEntityFuelingStation station) {
      station.tank.fillFluid(new FluidStack(Fluids.WATER, 16000), 16000, false);
    } else {
      helper.fail("no fueling station");
      return;
    }
    if (TestUtil.blockEntity(helper, PIPE) instanceof BlockEntityRecipePipe pipe) {
      int recipe = pipe.addRecipe();
      pipe.setIo(recipe, 0, cell(0));
      pipe.setIo(recipe, BlockEntityRecipePipe.outputId(0), cell(1000));
    } else {
      helper.fail("no recipe pipe");
      return;
    }
    helper.runAfterDelay(20, () -> {
      if (TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table) {
        table.setGhostTarget(cell(1000));
        table.request(null, 1);
      } else {
        helper.fail("no request table");
      }
    });
    helper.succeedWhen(() -> {
      int fluid = fluidInTable(helper, TABLE);
      if (fluid != 1000) {
        helper.fail("the filled cell never came back: table=" + fluid + " mB; " + ledgerState(helper));
      }
    });
  }
}
