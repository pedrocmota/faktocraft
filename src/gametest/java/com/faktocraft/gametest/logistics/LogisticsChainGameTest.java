package com.faktocraft.gametest.logistics;

import com.faktocraft.common.block.impl.logistics.BlockEntityAssemblyTable;
import com.faktocraft.common.block.impl.logistics.BlockEntityChassis;
import com.faktocraft.common.block.impl.logistics.BlockEntityCraftPipe;
import com.faktocraft.common.block.impl.logistics.BlockEntityLogisticsController;
import com.faktocraft.common.block.impl.logistics.BlockEntityRecipePipe;
import com.faktocraft.common.block.impl.logistics.BlockEntityRequestTable;
import com.faktocraft.common.block.impl.logistics.Endpoint;
import com.faktocraft.common.block.impl.logistics.IoMode;
import com.faktocraft.common.block.impl.logistics.LogisticsItemTree;
import com.faktocraft.common.block.impl.logistics.LogisticsRegistry;
import com.faktocraft.common.block.impl.logistics.ModuleSettings;
import com.faktocraft.common.block.impl.logistics.TaskLedger;
import com.faktocraft.common.block.impl.machines.metal_former.BlockEntityMetalFormer;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.registries.machines.M3Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.IItemHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class LogisticsChainGameTest {

  static final String TEMPLATE = "gametest_platform";
  static final BlockPos STOCK = new BlockPos(1, 1, 1);
  static final BlockPos PROVIDER = new BlockPos(2, 1, 1);
  static final BlockPos CORE = new BlockPos(3, 2, 1);
  static final BlockPos TABLE = new BlockPos(10, 1, 1);
  static final int STATIONS = 8;
  static final int SETUP_DELAY = 20;

  static BlockPos pipeAt(int station) {
    return new BlockPos(2 + station, 1, 2);
  }

  static BlockPos machineAt(int station) {
    return new BlockPos(2 + station, 1, 3);
  }

  static void placeChest(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
    helper.setBlock(rel, Blocks.CHEST.defaultBlockState());
    if (TestUtil.blockEntity(helper, rel) instanceof ChestBlockEntity chest) {
      for (int i = 0; i < stacks.length && i < chest.getContainerSize(); i++) {
        chest.setItem(i, stacks[i]);
      }
    }
  }

  static void fillEnergy(GameTestHelper helper, BlockPos rel) {
    if (TestUtil.blockEntity(helper, rel) instanceof FaktocraftBlockEntity be) {
      be.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.ADVANCED_CAPACITOR));
      be.getEnergyStorage().setEnergy(be.getEnergyStorage().maxEnergy());
    }
  }

  static void putModule(GameTestHelper helper, BlockPos rel, int slot, ItemStack module) {
    if (TestUtil.blockEntity(helper, rel) instanceof BlockEntityChassis chassis) {
      chassis.getModules().setStackInSlot(slot, module);
    } else {
      helper.fail("no chassis at " + rel);
    }
  }

  static ItemStack[] stacks(ItemStack... stacks) {
    return stacks;
  }

  static ItemStack[] repeat(Item item, int stacksOf64) {
    ItemStack[] result = new ItemStack[stacksOf64];
    for (int i = 0; i < stacksOf64; i++) {
      result[i] = new ItemStack(item, 64);
    }
    return result;
  }

  static ItemStack[] concat(ItemStack[] first, ItemStack... more) {
    ItemStack[] result = new ItemStack[first.length + more.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(more, 0, result, first.length, more.length);
    return result;
  }

  static void buildBus(GameTestHelper helper, ItemStack... stock) {
    buildBus(helper, true, new boolean[] { true }, stock);
  }

  static ItemStack allSink() {
    ItemStack sink = new ItemStack(LogisticsRegistry.MODULE_SINK);
    Map<String, Boolean> everything = new LinkedHashMap<>();
    everything.put(LogisticsItemTree.NODE_ALL, true);
    ModuleSettings.putTreeOverrides(sink, everything);
    return sink;
  }

  static void buildBus(GameTestHelper helper, boolean sinkAtStock, boolean[] powered, ItemStack... stock) {
    placeChest(helper, STOCK, stock);
    for (int x = 2; x <= 9; x++) {
      helper.setBlock(new BlockPos(x, 1, 1), LogisticsRegistry.CHASSIS_1.defaultBlockState());
    }
    helper.setBlock(TABLE, LogisticsRegistry.REQUEST_TABLE.defaultBlockState());
    helper.setBlock(CORE, LogisticsRegistry.LOGISTICS_CONTROLLER.defaultBlockState());
    fillEnergy(helper, CORE);
    putModule(helper, PROVIDER, 0, new ItemStack(LogisticsRegistry.MODULE_PROVIDER));
    if (sinkAtStock) {
      putModule(helper, PROVIDER, 1, allSink());
    }
    helper.onEachTick(() -> {
      if (!powered[0]) {
        return;
      }
      fillEnergy(helper, CORE);
      for (int station = 0; station < STATIONS; station++) {
        if (TestUtil.blockEntity(helper, machineAt(station)) instanceof FaktocraftBlockEntity be) {
          be.getEnergyStorage().setEnergy(be.getEnergyStorage().maxEnergy());
        }
      }
    });
  }

  static BlockState facingSouth(Block block) {
    BlockState state = block.defaultBlockState();
    if (block instanceof IStateFacing facing) {
      state = facing.setDirection(state, Direction.SOUTH);
    }
    return state;
  }

  static BlockEntityRecipePipe machineStation(GameTestHelper helper, int station, Block machine) {
    helper.setBlock(pipeAt(station), LogisticsRegistry.RECIPE_PIPE.defaultBlockState());
    helper.setBlock(machineAt(station), facingSouth(machine));
    fillEnergy(helper, machineAt(station));
    if (TestUtil.blockEntity(helper, pipeAt(station)) instanceof BlockEntityRecipePipe pipe) {
      return pipe;
    }
    helper.fail("no recipe pipe at station " + station);
    return null;
  }

  static BlockEntityCraftPipe benchStation(GameTestHelper helper, int station) {
    helper.setBlock(pipeAt(station), LogisticsRegistry.CRAFT_PIPE.defaultBlockState());
    helper.setBlock(machineAt(station), LogisticsRegistry.ASSEMBLY_TABLE.defaultBlockState());
    fillEnergy(helper, machineAt(station));
    if (TestUtil.blockEntity(helper, pipeAt(station)) instanceof BlockEntityCraftPipe pipe) {
      return pipe;
    }
    helper.fail("no craft pipe at station " + station);
    return null;
  }

  static void pattern(GameTestHelper helper, int station, ItemStack... cells) {
    BlockEntityCraftPipe pipe = benchStation(helper, station);
    if (pipe != null) {
      addPattern(pipe, cells);
    }
  }

  static void addPattern(BlockEntityCraftPipe pipe, ItemStack... cells) {
    int recipe = pipe.addRecipe();
    for (int i = 0; i < cells.length; i++) {
      if (cells[i] != null && !cells[i].isEmpty()) {
        pipe.setPatternSlot(recipe, i, cells[i]);
      }
    }
  }

  static int simpleRecipe(BlockEntityRecipePipe pipe, ItemStack input, ItemStack output) {
    int recipe = pipe.addRecipe();
    pipe.setIo(recipe, 0, input.copyWithCount(1), input.getCount());
    pipe.setIo(recipe, BlockEntityRecipePipe.outputId(0), output.copyWithCount(1), output.getCount());
    return recipe;
  }

  static int slotCount(GameTestHelper helper, BlockPos machine) {
    IItemHandler handler = Endpoint.resolveHandler(helper.getLevel(), helper.absolutePos(machine), null);
    return handler != null ? handler.getSlots() : 0;
  }

  static void furnaceStation(GameTestHelper helper, int station, ItemStack input, ItemStack output) {
    helper.setBlock(pipeAt(station), LogisticsRegistry.RECIPE_PIPE.defaultBlockState());
    helper.setBlock(machineAt(station), Blocks.FURNACE.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, pipeAt(station)) instanceof BlockEntityRecipePipe pipe)) {
      helper.fail("no recipe pipe at station " + station);
      return;
    }
    int recipe = simpleRecipe(pipe, input, output);
    pipe.setIo(recipe, 1, new ItemStack(Items.COAL), 2);
    pipe.setMode(recipe, 1, IoMode.MAINTAIN);
    pipe.bind(recipe, 1, 1, "minecraft:furnace", 3);
  }

  static void request(GameTestHelper helper, ItemStack target, int quantity) {
    request(helper, target, quantity, 1);
  }

  static void request(GameTestHelper helper, ItemStack target, int quantity, int times) {
    helper.runAfterDelay(SETUP_DELAY, () -> {
      if (TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table) {
        table.setGhostTarget(target);
        for (int i = 0; i < times; i++) {
          table.request(null, quantity);
        }
      } else {
        helper.fail("no request table");
      }
    });
  }

  static int countInTable(GameTestHelper helper, Predicate<ItemStack> what) {
    if (!(TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table)) {
      return -1;
    }
    int count = 0;
    for (int i = 0; i < table.getItemStackHandler().getSlots(); i++) {
      ItemStack stack = table.getItemStackHandler().getStackInSlot(i);
      if (what.test(stack)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  static int countInChest(GameTestHelper helper, BlockPos rel, Predicate<ItemStack> what) {
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

  static BlockEntityLogisticsController core(GameTestHelper helper) {
    return TestUtil.blockEntity(helper, CORE) instanceof BlockEntityLogisticsController core ? core : null;
  }

  static long holder(GameTestHelper helper, int station) {
    BlockEntityLogisticsController core = core(helper);
    return core != null ? core.getLedger().stationHolder(helper.absolutePos(machineAt(station))) : 0;
  }

  static String ledgerState(GameTestHelper helper) {
    BlockEntityLogisticsController core = core(helper);
    if (core == null) {
      return "no core";
    }
    StringBuilder out = new StringBuilder();
    for (TaskLedger.TaskSummary task : core.getLedger().summaries()) {
      out.append("task{").append(task.stateKey()).append(' ').append(task.detail()).append(" x")
          .append(task.count()).append(' ').append(task.stack().getItem());
      for (TaskLedger.SubRecord sub : task.subs()) {
        out.append(" |").append(sub.kind()).append(':').append(sub.stateKey()).append(' ')
            .append(sub.item().getItem()).append(" x").append(sub.count());
      }
      out.append("} ");
    }
    out.append("cfgErrors=").append(core.getConfigErrors());
    for (TaskLedger.HistoryRecord record : core.getLedger().userHistory()) {
      out.append(" [").append(record.stateKey()).append(' ').append(record.detail()).append(" x")
          .append(record.count()).append(']');
    }
    if (core.graph() != null) {
      out.append(" stock=").append(BlockEntityChassis.stockSnapshot(helper.getLevel(), core.graph(),
          core.getLedger()));
    }
    return out.toString();
  }

  static long historyCount(GameTestHelper helper, String stateKey) {
    BlockEntityLogisticsController core = core(helper);
    if (core == null) {
      return -1;
    }
    return core.getLedger().userHistory().stream().filter(record -> stateKey.equals(record.stateKey())).count();
  }

  static int activeJobs(GameTestHelper helper) {
    BlockEntityLogisticsController core = core(helper);
    return core != null ? core.getLedger().activeJobCount() : -1;
  }

  static void expectDelivered(GameTestHelper helper, Item item, int quantity, int doneRecords) {
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(item));
      if (delivered != quantity) {
        helper.fail("expected " + quantity + " " + item + " at the table, found " + delivered + "; "
            + ledgerState(helper));
        return;
      }
      BlockEntityLogisticsController core = core(helper);
      if (core == null) {
        helper.fail("no core");
        return;
      }
      if (core.getLedger().userHistory().stream().anyMatch(record -> !"done".equals(record.stateKey()))) {
        helper.fail("some request failed; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != doneRecords) {
        helper.fail("expected " + doneRecords + " completed records; " + ledgerState(helper));
        return;
      }
      if (activeJobs(helper) != 0) {
        helper.fail("jobs still active; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 900)
  public void stockRequestDeliversAThousandItems(GameTestHelper helper) {
    buildBus(helper, repeat(Items.COBBLESTONE, 16));
    request(helper, new ItemStack(Items.COBBLESTONE), 1000);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.COBBLESTONE));
      if (delivered != 1000) {
        helper.fail("expected 1000 cobblestone at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      int left = countInChest(helper, STOCK, stack -> stack.is(Items.COBBLESTONE));
      if (left != 24) {
        helper.fail("expected 24 cobblestone left in stock, found " + left);
        return;
      }
      if (historyCount(helper, "done") != 1) {
        helper.fail("expected one completed record; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void manyConcurrentRequestsAllComplete(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    request(helper, new ItemStack(Items.OAK_PLANKS), 4, 12);
    expectDelivered(helper, Items.OAK_PLANKS, 48, 12);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 3600)
  public void largeCraftSplitsAcrossTwoTables(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    pattern(helper, 1, new ItemStack(Items.OAK_LOG));
    boolean[] bothBusy = new boolean[1];
    helper.onEachTick(() -> {
      if (holder(helper, 0) != 0 && holder(helper, 1) != 0) {
        bothBusy[0] = true;
      }
    });
    request(helper, new ItemStack(Items.OAK_PLANKS), 256);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.OAK_PLANKS));
      if (delivered != 256) {
        helper.fail("expected 256 planks at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!bothBusy[0]) {
        helper.fail("the two assembly tables never worked at the same time; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 2) {
        helper.fail("expected the request to be split into two completed parts; " + ledgerState(helper));
        return;
      }
      int logs = countInChest(helper, STOCK, stack -> stack.is(Items.OAK_LOG));
      if (logs != 0) {
        helper.fail("expected all 64 logs to be used, " + logs + " left");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void vanillaFurnaceWithFuelKeptStocked(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL, 8));
    furnaceStation(helper, 0, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    request(helper, new ItemStack(Items.IRON_INGOT), 4);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (delivered != 4) {
        helper.fail("expected 4 iron ingots at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!(TestUtil.blockEntity(helper, machineAt(0)) instanceof AbstractFurnaceBlockEntity furnace)) {
        helper.fail("no furnace");
        return;
      }
      if (!furnace.getItem(0).isEmpty() || !furnace.getItem(2).isEmpty()) {
        helper.fail("furnace not emptied: in=" + furnace.getItem(0) + " out=" + furnace.getItem(2));
        return;
      }
      if (!furnace.getItem(1).is(Items.COAL)) {
        helper.fail("the fuel slot was not kept stocked: " + furnace.getItem(1));
        return;
      }
      int rawLeft = countInChest(helper, STOCK, stack -> stack.is(Items.RAW_IRON));
      if (rawLeft != 4) {
        helper.fail("expected 4 raw iron left in stock, found " + rawLeft);
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void deliveriesStreamWhileStillProducing(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL, 8));
    furnaceStation(helper, 0, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    boolean[] partial = new boolean[1];
    helper.onEachTick(() -> {
      int atTable = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (atTable > 0 && atTable < 4 && activeJobs(helper) > 0 && holder(helper, 0) != 0) {
        partial[0] = true;
      }
    });
    request(helper, new ItemStack(Items.IRON_INGOT), 4);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (delivered != 4) {
        helper.fail("expected 4 iron ingots at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!partial[0]) {
        helper.fail("ingots only arrived after the whole job finished; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 2400)
  public void downstreamStepStartsBeforeUpstreamFinishes(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    pattern(helper, 1, planks, planks, planks, planks, null, planks, planks, planks, planks);
    boolean[] overlap = new boolean[1];
    helper.onEachTick(() -> {
      if (holder(helper, 0) != 0 && holder(helper, 1) != 0) {
        overlap[0] = true;
      }
    });
    request(helper, new ItemStack(Items.CHEST), 4);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.CHEST));
      if (delivered != 4) {
        helper.fail("expected 4 chests at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!overlap[0]) {
        helper.fail("the chest bench never ran while planks were still being made; " + ledgerState(helper));
        return;
      }
      int logs = countInChest(helper, STOCK, stack -> stack.is(Items.OAK_LOG));
      if (logs != 56) {
        helper.fail("expected 56 logs left, found " + logs);
      }
    });
  }

  static void sameBenchChestChain(GameTestHelper helper, int chests, boolean chestFirst) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    BlockEntityCraftPipe pipe = benchStation(helper, 0);
    if (pipe == null) {
      return;
    }
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    ItemStack[] chest = { planks, planks, planks, planks, null, planks, planks, planks, planks };
    if (chestFirst) {
      addPattern(pipe, chest);
    }
    addPattern(pipe, new ItemStack(Items.OAK_LOG));
    if (!chestFirst) {
      addPattern(pipe, chest);
    }
    request(helper, new ItemStack(Items.CHEST), chests);
    expectDelivered(helper, Items.CHEST, chests, 1);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void sameBenchChainMakesOneChest(GameTestHelper helper) {
    sameBenchChestChain(helper, 1, false);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void sameBenchChainMakesTwoChests(GameTestHelper helper) {
    sameBenchChestChain(helper, 2, false);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void sameBenchChainMakesThreeChestsWithTheChestRecipeFirst(GameTestHelper helper) {
    sameBenchChestChain(helper, 3, true);
  }

  static void expectOnly(GameTestHelper helper, Item target, int quantity, Item stray) {
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(target));
      int strays = countInChest(helper, STOCK, stack -> stack.is(stray))
          + countInTable(helper, stack -> stack.is(stray));
      if (TestUtil.blockEntity(helper, machineAt(0)) instanceof BlockEntityAssemblyTable table) {
        for (int slot = 0; slot < table.getOutput().getSlots(); slot++) {
          if (table.getOutput().getStackInSlot(slot).is(stray)) {
            strays += table.getOutput().getStackInSlot(slot).getCount();
          }
        }
      }
      if (strays > 0) {
        helper.fail("the bench crafted " + strays + " " + stray + " from ingredients sent for " + target + "; "
            + ledgerState(helper));
        return;
      }
      if (delivered != quantity || historyCount(helper, "done") != 1 || activeJobs(helper) != 0) {
        helper.fail("expected " + quantity + " " + target + " at the table, found " + delivered + "; "
            + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void sharedBenchCraftsOnlyTheRequestedRecipe(GameTestHelper helper) {
    ItemStack tin = new ItemStack(ModItems.TIN_PLATE);
    ItemStack rubber = new ItemStack(ModItems.RUBBER);
    ItemStack lithium = new ItemStack(ModItems.LITHIUM_DUST);
    ItemStack redstone = new ItemStack(Items.REDSTONE);
    buildBus(helper, tin.copyWithCount(16), rubber.copyWithCount(16), lithium.copyWithCount(16),
        redstone.copyWithCount(16));
    BlockEntityCraftPipe pipe = benchStation(helper, 0);
    if (pipe == null) {
      return;
    }
    addPattern(pipe, null, null, null, tin, rubber, tin, rubber, redstone, rubber);
    addPattern(pipe, tin, rubber, tin, lithium, redstone, lithium, tin, rubber, tin);
    request(helper, new ItemStack(ModItems.BASIC_CAPACITOR), 2);
    expectOnly(helper, ModItems.BASIC_CAPACITOR, 2, ModItems.CRUDE_CAPACITOR);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void sharedBenchChainIgnoresTheStickRecipe(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    BlockEntityCraftPipe pipe = benchStation(helper, 0);
    if (pipe == null) {
      return;
    }
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    addPattern(pipe, null, null, null, planks, null, null, planks);
    addPattern(pipe, new ItemStack(Items.OAK_LOG));
    addPattern(pipe, planks, planks, planks, planks, null, planks, planks, planks, planks);
    request(helper, new ItemStack(Items.CHEST), 2);
    expectOnly(helper, Items.CHEST, 2, Items.STICK);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1200)
  public void modCrusherCraftsThroughRecipePipe(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_COPPER, 6));
    BlockEntityRecipePipe pipe = machineStation(helper, 0, M2Registry.CRUSHER);
    if (pipe == null) {
      return;
    }
    simpleRecipe(pipe, new ItemStack(Items.RAW_COPPER, 3), new ItemStack(ModItems.COPPER_DUST, 4));
    request(helper, new ItemStack(ModItems.COPPER_DUST), 8);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(ModItems.COPPER_DUST));
      if (delivered != 8) {
        helper.fail("expected 8 copper dust at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      int raw = countInChest(helper, STOCK, stack -> stack.is(Items.RAW_COPPER));
      if (raw != 0) {
        helper.fail("expected all raw copper to be used, found " + raw);
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1600)
  public void electricAndVanillaFurnacesShareOneRequest(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL, 8));
    BlockEntityRecipePipe electric = machineStation(helper, 0, M2Registry.ELECTRIC_FURNACE);
    if (electric == null) {
      return;
    }
    simpleRecipe(electric, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    furnaceStation(helper, 1, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    request(helper, new ItemStack(Items.IRON_INGOT), 8);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (delivered != 8) {
        helper.fail("expected 8 iron ingots at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (countInChest(helper, STOCK, stack -> stack.is(Items.COAL)) == 8) {
        helper.fail("the vanilla furnace never received coal, so it did not share the work");
        return;
      }
      if (!(TestUtil.blockEntity(helper, machineAt(1)) instanceof AbstractFurnaceBlockEntity furnace)
          || furnace.getItem(1).isEmpty()) {
        helper.fail("coal was not kept stocked in the vanilla furnace");
        return;
      }
      if (activeJobs(helper) != 0) {
        helper.fail("jobs still active; " + ledgerState(helper));
      }
    });
  }

  static void copperChain(GameTestHelper helper, boolean secondFurnace) {
    BlockEntityRecipePipe crusher = machineStation(helper, 0, M2Registry.CRUSHER);
    if (crusher == null) {
      return;
    }
    simpleRecipe(crusher, new ItemStack(Items.RAW_COPPER, 3), new ItemStack(ModItems.COPPER_DUST, 4));
    furnaceStation(helper, 1, new ItemStack(ModItems.COPPER_DUST), new ItemStack(Items.COPPER_INGOT));
    if (secondFurnace) {
      furnaceStation(helper, 6, new ItemStack(ModItems.COPPER_DUST), new ItemStack(Items.COPPER_INGOT));
    }
    BlockEntityRecipePipe rolling = machineStation(helper, 2, M3Registry.METAL_FORMER);
    if (rolling == null) {
      return;
    }
    if (TestUtil.blockEntity(helper, machineAt(2)) instanceof BlockEntityMetalFormer former) {
      former.changeMode();
    }
    simpleRecipe(rolling, new ItemStack(Items.COPPER_INGOT), new ItemStack(ModItems.COPPER_PLATE));
    BlockEntityRecipePipe cutting = machineStation(helper, 3, M3Registry.METAL_FORMER);
    if (cutting == null) {
      return;
    }
    simpleRecipe(cutting, new ItemStack(ModItems.COPPER_PLATE), new ItemStack(ModItems.COPPER_CABLE, 2));
    pattern(helper, 4, new ItemStack(ModItems.RUBBER), new ItemStack(ModItems.COPPER_CABLE));
    BlockEntityRecipePipe assembler = machineStation(helper, 5, M3Registry.CIRCUIT_ASSEMBLER);
    if (assembler == null) {
      return;
    }
    int recipe = assembler.addRecipe();
    String assemblerId = "faktocraft:circuit_assembler";
    int slots = slotCount(helper, machineAt(5));
    assembler.setIo(recipe, 0, new ItemStack(ModItems.COPPER_CABLE_INSULATED), 4);
    assembler.bind(recipe, 0, 0, assemblerId, slots);
    assembler.setIo(recipe, 1, new ItemStack(ModItems.IRON_PLATE), 1);
    assembler.bind(recipe, 1, 1, assemblerId, slots);
    assembler.setIo(recipe, 2, new ItemStack(Items.REDSTONE), 2);
    assembler.bind(recipe, 2, 2, assemblerId, slots);
    assembler.setIo(recipe, BlockEntityRecipePipe.outputId(0), new ItemStack(ModItems.ELECTRONIC_CIRCUIT), 1);
    assembler.bind(recipe, BlockEntityRecipePipe.outputId(0), 3, assemblerId, slots);
  }

  static ItemStack[] copperStock(int rawCopper) {
    return stacks(new ItemStack(Items.RAW_COPPER, rawCopper), new ItemStack(Items.COAL, 8),
        new ItemStack(ModItems.RUBBER, 16), new ItemStack(ModItems.IRON_PLATE, 4), new ItemStack(Items.REDSTONE, 8));
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 4800)
  public void sixStageChainMixesMachinesAndBenches(GameTestHelper helper) {
    buildBus(helper, copperStock(6));
    copperChain(helper, false);
    request(helper, new ItemStack(ModItems.ELECTRONIC_CIRCUIT), 1);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(ModItems.ELECTRONIC_CIRCUIT));
      if (delivered != 1) {
        helper.fail("expected 1 electronic circuit at the table, found " + delivered + "; "
            + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 1 || activeJobs(helper) != 0) {
        helper.fail("unexpected ledger state; " + ledgerState(helper));
        return;
      }
      int raw = countInChest(helper, STOCK, stack -> stack.is(Items.RAW_COPPER));
      if (raw != 3) {
        helper.fail("expected 3 raw copper left, found " + raw + "; " + ledgerState(helper));
        return;
      }
      int dust = countInChest(helper, STOCK, stack -> stack.is(ModItems.COPPER_DUST));
      if (dust != 2) {
        helper.fail("expected the 2 surplus copper dust back in stock, found " + dust + "; "
            + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 7200)
  public void sixStageChainTwiceWithTwoFurnaces(GameTestHelper helper) {
    buildBus(helper, copperStock(12));
    copperChain(helper, true);
    boolean[] bothFurnaces = new boolean[1];
    helper.onEachTick(() -> {
      if (holder(helper, 1) != 0 && holder(helper, 6) != 0) {
        bothFurnaces[0] = true;
      }
    });
    request(helper, new ItemStack(ModItems.ELECTRONIC_CIRCUIT), 2);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(ModItems.ELECTRONIC_CIRCUIT));
      if (delivered != 2) {
        helper.fail("expected 2 electronic circuits at the table, found " + delivered + "; "
            + ledgerState(helper));
        return;
      }
      if (activeJobs(helper) != 0) {
        helper.fail("jobs still active; " + ledgerState(helper));
        return;
      }
      if (!bothFurnaces[0]) {
        helper.fail("the two furnaces never smelted at the same time; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1200)
  public void machineRemovedMidJobFailsAndFreesTheRequest(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.COAL, 8));
    furnaceStation(helper, 0, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    request(helper, new ItemStack(Items.IRON_INGOT), 4);
    helper.runAfterDelay(SETUP_DELAY + 120, () -> {
      if (holder(helper, 0) == 0) {
        helper.fail("the furnace was never claimed; " + ledgerState(helper));
        return;
      }
      helper.setBlock(machineAt(0), Blocks.AIR.defaultBlockState());
    });
    helper.succeedWhen(() -> {
      if (historyCount(helper, "error.lost") != 1) {
        helper.fail("expected the job to fail because the furnace is gone; " + ledgerState(helper));
        return;
      }
      if (activeJobs(helper) != 0) {
        helper.fail("job still active after losing its machine; " + ledgerState(helper));
        return;
      }
      BlockEntityLogisticsController core = core(helper);
      if (core != null && core.getLedger().reservedFor(
          com.faktocraft.common.block.impl.logistics.ItemKey.of(Items.RAW_IRON)) != 0) {
        helper.fail("raw iron still reserved after the failure");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public void cancelMidChainRecoversIntermediates(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 64));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    pattern(helper, 1, planks, planks, planks, planks, null, planks, planks, planks, planks);
    request(helper, new ItemStack(Items.CHEST), 8);
    long[] jobId = new long[1];
    helper.runAfterDelay(SETUP_DELAY + 100, () -> {
      BlockEntityLogisticsController core = core(helper);
      if (core == null || core.getLedger().summaries().isEmpty()) {
        helper.fail("no active job to cancel; " + ledgerState(helper));
        return;
      }
      jobId[0] = core.getLedger().summaries().get(0).id();
      if (!core.getLedger().cancelUserJob(helper.getLevel(), jobId[0], null)) {
        helper.fail("cancel refused; " + ledgerState(helper));
      }
    });
    helper.succeedWhen(() -> {
      if (historyCount(helper, "error.cancelled") != 1 || activeJobs(helper) != 0) {
        helper.fail("expected one cancelled record and no active job; " + ledgerState(helper));
        return;
      }
      BlockEntityLogisticsController core = core(helper);
      if (core != null && core.getLedger().reservedFor(
          com.faktocraft.common.block.impl.logistics.ItemKey.of(Items.OAK_LOG)) != 0) {
        helper.fail("logs still reserved after the cancel");
        return;
      }
      for (int station = 0; station < 2; station++) {
        if (TestUtil.blockEntity(helper, machineAt(station)) instanceof BlockEntityAssemblyTable table) {
          for (int i = 0; i < table.getIngredients().getSlots(); i++) {
            if (!table.getIngredients().getStackInSlot(i).isEmpty()) {
              helper.fail("ingredients left in the assembly table at station " + station);
              return;
            }
          }
          for (int i = 0; i < table.getOutput().getSlots(); i++) {
            if (!table.getOutput().getStackInSlot(i).isEmpty()) {
              helper.fail("output left in the assembly table at station " + station);
              return;
            }
          }
        }
      }
      int logs = countInChest(helper, STOCK, stack -> stack.is(Items.OAK_LOG));
      int planksBack = countInChest(helper, STOCK, stack -> stack.is(Items.OAK_PLANKS));
      int chests = countInChest(helper, STOCK, stack -> stack.is(Items.CHEST))
          + countInTable(helper, stack -> stack.is(Items.CHEST));
      if (logs * 4 + planksBack + chests * 8 != 256) {
        helper.fail("wood was lost: logs=" + logs + " planks=" + planksBack + " chests=" + chests + "; "
            + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 2400)
  public void sharedPipesSplitTheRequest(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_COPPER, 12));
    for (int station = 0; station < 2; station++) {
      BlockEntityRecipePipe pipe = machineStation(helper, station, M2Registry.CRUSHER);
      if (pipe == null) {
        return;
      }
      simpleRecipe(pipe, new ItemStack(Items.RAW_COPPER, 3), new ItemStack(ModItems.COPPER_DUST, 4));
    }
    boolean[] bothBusy = new boolean[1];
    helper.onEachTick(() -> {
      if (holder(helper, 0) != 0 && holder(helper, 1) != 0) {
        bothBusy[0] = true;
      }
    });
    request(helper, new ItemStack(ModItems.COPPER_DUST), 16);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(ModItems.COPPER_DUST));
      if (delivered != 16) {
        helper.fail("expected 16 copper dust at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 2) {
        helper.fail("expected the request split into two parts; " + ledgerState(helper));
        return;
      }
      if (!bothBusy[0]) {
        helper.fail("the two crushers never ran at the same time; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 2400)
  public void unsharedPipeKeepsTheWholeRequest(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.RAW_COPPER, 12));
    for (int station = 0; station < 2; station++) {
      BlockEntityRecipePipe pipe = machineStation(helper, station, M2Registry.CRUSHER);
      if (pipe == null) {
        return;
      }
      simpleRecipe(pipe, new ItemStack(Items.RAW_COPPER, 3), new ItemStack(ModItems.COPPER_DUST, 4));
      if (station == 1) {
        pipe.setShared(false);
      }
    }
    request(helper, new ItemStack(ModItems.COPPER_DUST), 16);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(ModItems.COPPER_DUST));
      if (delivered != 16) {
        helper.fail("expected 16 copper dust at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 1) {
        helper.fail("expected a single unsplit request; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 2400)
  public void mixedRequestsQueueOnSharedStations(GameTestHelper helper) {
    buildBus(helper, concat(repeat(Items.OAK_LOG, 1), new ItemStack(Items.RAW_IRON, 8),
        new ItemStack(Items.COAL, 8)));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    pattern(helper, 1, planks, planks, null, planks, planks);
    furnaceStation(helper, 2, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    helper.runAfterDelay(SETUP_DELAY, () -> {
      if (!(TestUtil.blockEntity(helper, TABLE) instanceof BlockEntityRequestTable table)) {
        helper.fail("no request table");
        return;
      }
      table.setGhostTarget(new ItemStack(Items.CRAFTING_TABLE));
      table.request(null, 3);
      table.setGhostTarget(new ItemStack(Items.IRON_INGOT));
      table.request(null, 2);
      table.setGhostTarget(new ItemStack(Items.OAK_PLANKS));
      table.request(null, 10);
      table.setGhostTarget(new ItemStack(Items.CRAFTING_TABLE));
      table.request(null, 1);
    });
    helper.succeedWhen(() -> {
      int tables = countInTable(helper, stack -> stack.is(Items.CRAFTING_TABLE));
      int ingots = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      int planksDelivered = countInTable(helper, stack -> stack.is(Items.OAK_PLANKS));
      if (tables != 4 || ingots != 2 || planksDelivered != 10) {
        helper.fail("expected 4 crafting tables, 2 ingots and 10 planks, found " + tables + "/" + ingots + "/"
            + planksDelivered + "; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 4 || activeJobs(helper) != 0) {
        helper.fail("expected four completed requests; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 3600)
  public void fourBenchChainWithSurplusReturnsLeftovers(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 16),
        new ItemStack(Items.CARVED_PUMPKIN, 4));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    pattern(helper, 1, planks, null, null, planks);
    pattern(helper, 2, new ItemStack(Items.COAL), null, null, new ItemStack(Items.STICK));
    pattern(helper, 3, new ItemStack(Items.CARVED_PUMPKIN), null, null, new ItemStack(Items.TORCH));
    request(helper, new ItemStack(Items.JACK_O_LANTERN), 3);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.JACK_O_LANTERN));
      if (delivered != 3) {
        helper.fail("expected 3 jack o'lanterns at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 1 || activeJobs(helper) != 0) {
        helper.fail("unexpected ledger state; " + ledgerState(helper));
        return;
      }
      int torches = countInChest(helper, STOCK, stack -> stack.is(Items.TORCH));
      int sticks = countInChest(helper, STOCK, stack -> stack.is(Items.STICK));
      int planksBack = countInChest(helper, STOCK, stack -> stack.is(Items.OAK_PLANKS));
      if (torches != 1 || sticks != 3 || planksBack != 2) {
        helper.fail("expected surplus 1 torch, 3 sticks and 2 planks back in stock, found " + torches + "/"
            + sticks + "/" + planksBack + "; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 3600)
  public void furnacePairSmeltsSixteenWithStreamingOutput(GameTestHelper helper) {
    buildBus(helper, concat(repeat(Items.RAW_IRON, 1), new ItemStack(Items.COAL, 16)));
    furnaceStation(helper, 0, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    furnaceStation(helper, 1, new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
    List<Integer> seen = new ArrayList<>();
    helper.onEachTick(() -> {
      int atTable = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (seen.isEmpty() || seen.get(seen.size() - 1) != atTable) {
        seen.add(atTable);
      }
    });
    request(helper, new ItemStack(Items.IRON_INGOT), 16);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.IRON_INGOT));
      if (delivered != 16) {
        helper.fail("expected 16 iron ingots at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (seen.size() < 4) {
        helper.fail("ingots arrived in too few steps: " + seen + "; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 2 || activeJobs(helper) != 0) {
        helper.fail("expected two completed parts; " + ledgerState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 600)
  public void requestBeyondStockFailsCleanly(GameTestHelper helper) {
    buildBus(helper, new ItemStack(Items.OAK_LOG, 2));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    request(helper, new ItemStack(Items.OAK_PLANKS), 64);
    helper.succeedWhen(() -> {
      if (historyCount(helper, "error.missing") != 1) {
        helper.fail("expected a missing-ingredient failure; " + ledgerState(helper));
        return;
      }
      if (activeJobs(helper) != 0) {
        helper.fail("no job should be running; " + ledgerState(helper));
        return;
      }
      BlockEntityLogisticsController core = core(helper);
      if (core != null && core.getLedger().reservedFor(
          com.faktocraft.common.block.impl.logistics.ItemKey.of(Items.OAK_LOG)) != 0) {
        helper.fail("logs stayed reserved after the failed plan");
        return;
      }
      if (countInChest(helper, STOCK, stack -> stack.is(Items.OAK_LOG)) != 2) {
        helper.fail("stock was touched by a failed plan");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 7200)
  public void longChainStressBuildsSixtyFourCraftingTables(GameTestHelper helper) {
    buildBus(helper, repeat(Items.OAK_LOG, 1));
    pattern(helper, 0, new ItemStack(Items.OAK_LOG));
    ItemStack planks = new ItemStack(Items.OAK_PLANKS);
    pattern(helper, 1, planks, planks, null, planks, planks);
    boolean[] overlap = new boolean[1];
    helper.onEachTick(() -> {
      if (holder(helper, 0) != 0 && holder(helper, 1) != 0) {
        overlap[0] = true;
      }
    });
    request(helper, new ItemStack(Items.CRAFTING_TABLE), 64);
    helper.succeedWhen(() -> {
      int delivered = countInTable(helper, stack -> stack.is(Items.CRAFTING_TABLE));
      if (delivered != 64) {
        helper.fail("expected 64 crafting tables at the table, found " + delivered + "; " + ledgerState(helper));
        return;
      }
      if (!overlap[0]) {
        helper.fail("the two benches never overlapped; " + ledgerState(helper));
        return;
      }
      if (historyCount(helper, "done") != 1 || activeJobs(helper) != 0) {
        helper.fail("unexpected ledger state; " + ledgerState(helper));
        return;
      }
      if (countInChest(helper, STOCK, stack -> stack.is(Items.OAK_LOG)) != 0) {
        helper.fail("all 64 logs should have been used");
      }
    });
  }
}
