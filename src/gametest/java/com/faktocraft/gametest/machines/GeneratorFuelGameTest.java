package com.faktocraft.gametest.machines;

import com.faktocraft.common.block.impl.generators.combustion_generator.BlockEntityCombustionGenerator;
import com.faktocraft.common.block.impl.generators.combustion_generator.MenuCombustionGenerator;
import com.faktocraft.common.block.impl.generators.generator.BlockEntityGenerator;
import com.faktocraft.common.block.impl.generators.generator.MenuGenerator;
import com.faktocraft.common.block.impl.generators.geo_generator.BlockEntityGeoGenerator;
import com.faktocraft.common.block.impl.generators.geo_generator.MenuGeoGenerator;
import com.faktocraft.common.block.impl.machines.alloy_smelter.BlockEntityCoalAlloySmelter;
import com.faktocraft.common.block.impl.machines.iron_furnace.BlockEntityIronFurnace;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.item.base.FluidItem;
import com.faktocraft.common.item.impl.FluidCell;
import com.faktocraft.common.network.PacketContext;
import com.faktocraft.common.network.packet.PacketCellFill;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M1Registry;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.FuelUtil;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.MockPlayers;
import com.faktocraft.gametest.TestUtil;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class GeneratorFuelGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
  private static final BlockPos PLAYER = new BlockPos(3, 1, 1);

  private static int menuIndexOfPlayerSlot(java.util.List<Slot> slots, Player player, int invSlot) {
    for (int i = 0; i < slots.size(); i++) {
      Slot slot = slots.get(i);
      if (slot.container == player.getInventory() && slot.getSlotIndex() == invSlot) {
        return i;
      }
    }
    return -1;
  }

  private static <T> T machine(GameTestHelper helper, Class<T> type) {
    if (!(type.isInstance(TestUtil.blockEntity(helper, MACHINE)))) {
      throw TestUtil.assertion(helper, "no " + type.getSimpleName() + " at the machine position");
    }
    return type.cast(TestUtil.blockEntity(helper, MACHINE));
  }

  private static PacketContext serverContext(ServerPlayer player) {
    IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
        new Class<?>[] { IPayloadContext.class }, (proxy, method, args) -> {
          switch (method.getName()) {
            case "player":
              return player;
            case "enqueueWork":
              if (args[0] instanceof Runnable runnable) {
                runnable.run();
                return CompletableFuture.completedFuture(null);
              }
              return CompletableFuture.completedFuture(((Supplier<?>) args[0]).get());
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        });
    return new PacketContext(context);
  }

  private static ItemStack fuelCells(int count) {
    ItemStack cells = new ItemStack(ModItems.FLUID_CELL, count);
    FluidItem.setFluid(cells, ModFluids.FUEL.still(), FluidCell.getCapacity());
    return cells;
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void generatorAcceptsAndBurnsCoalFromTheMenu(GameTestHelper helper) {
    helper.setBlock(MACHINE, M1Registry.GENERATOR.defaultBlockState());
    BlockEntityGenerator generator = machine(helper, BlockEntityGenerator.class);
    int coalBurn = FuelUtil.burnTime(helper.getLevel(), new ItemStack(Items.COAL), generator);
    if (coalBurn != 1600) {
      helper.fail("coal burn time in the generator is " + coalBurn + ", expected 1600");
      return;
    }
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MenuGenerator menu = new MenuGenerator(1, helper.getLevel(), helper.absolutePos(MACHINE),
        player.getInventory(), player);
    Slot input = menu.slots.get(BlockEntityGenerator.INPUT_SLOT);
    for (Item fuel : new Item[] { Items.COAL, Items.CHARCOAL, Items.OAK_LOG, Items.COAL_BLOCK, Items.BLAZE_ROD }) {
      if (!input.mayPlace(new ItemStack(fuel))) {
        helper.fail("the generator slot refuses " + fuel);
        return;
      }
    }
    if (input.mayPlace(new ItemStack(Items.LAVA_BUCKET)) || input.mayPlace(new ItemStack(Items.COBBLESTONE))) {
      helper.fail("the generator slot accepts a lava bucket or a non fuel");
      return;
    }
    player.getInventory().setItem(9, new ItemStack(Items.COAL, 8));
    menu.quickMoveStack(player, menuIndexOfPlayerSlot(menu.slots, player, 9));
    if (!generator.getItemStackHandler().getStackInSlot(BlockEntityGenerator.INPUT_SLOT).is(Items.COAL)) {
      helper.fail("shift click did not move the coal into the generator");
      return;
    }
    helper.succeedWhen(() -> {
      ItemStack left = generator.getItemStackHandler().getStackInSlot(BlockEntityGenerator.INPUT_SLOT);
      if (left.getCount() != 7) {
        throw TestUtil.assertion(helper, "coal in the generator: " + left.getCount() + ", expected 7");
      }
      if (generator.progressBurn.getProgressMax() != (int) (1600 * 0.60)) {
        throw TestUtil.assertion(helper, "burn length " + generator.progressBurn.getProgressMax());
      }
      if (generator.getEnergyStorage().energyStored() <= 0) {
        throw TestUtil.assertion(helper, "the generator made no energy");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void rubberWoodBurnsLikeIn1201(GameTestHelper helper) {
    helper.setBlock(MACHINE, M1Registry.GENERATOR.defaultBlockState());
    BlockEntityGenerator generator = machine(helper, BlockEntityGenerator.class);
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    MenuGenerator menu = new MenuGenerator(1, helper.getLevel(), helper.absolutePos(MACHINE),
        player.getInventory(), player);
    Slot input = menu.slots.get(BlockEntityGenerator.INPUT_SLOT);
    Item[] items = { ModItems.RUBBER_LOG, ModItems.DRIED_RUBBER_LOG, ModItems.RUBBER_PLANKS, ModItems.RUBBER_SAPLING };
    int[] expected = { 300, 300, 300, 100 };
    for (int i = 0; i < items.length; i++) {
      int burn = FuelUtil.burnTime(helper.getLevel(), new ItemStack(items[i]), generator);
      if (burn != expected[i]) {
        helper.fail(items[i] + " burns for " + burn + " ticks, expected " + expected[i]);
        return;
      }
      if (!input.mayPlace(new ItemStack(items[i]))) {
        helper.fail("the generator slot refuses " + items[i]);
        return;
      }
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void ironFurnaceBurnsCoal(GameTestHelper helper) {
    helper.setBlock(MACHINE, M2Registry.IRON_FURNACE.defaultBlockState());
    BlockEntityIronFurnace furnace = machine(helper, BlockEntityIronFurnace.class);
    if (!furnace.isItemValidForSlot(BlockEntityIronFurnace.FUEL_SLOT, new ItemStack(Items.COAL))) {
      helper.fail("the iron furnace fuel slot refuses coal");
      return;
    }
    furnace.getItemStackHandler().setStackInSlot(BlockEntityIronFurnace.INPUT_SLOT, new ItemStack(Items.RAW_IRON, 4));
    furnace.getItemStackHandler().setStackInSlot(BlockEntityIronFurnace.FUEL_SLOT, new ItemStack(Items.COAL, 4));
    helper.succeedWhen(() -> {
      int coal = furnace.getItemStackHandler().getStackInSlot(BlockEntityIronFurnace.FUEL_SLOT).getCount();
      if (coal != 3) {
        throw TestUtil.assertion(helper, "coal in the iron furnace: " + coal + ", expected 3");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void coalAlloySmelterAcceptsCoal(GameTestHelper helper) {
    helper.setBlock(MACHINE, M3Registry.COAL_ALLOY_SMELTER.defaultBlockState());
    BlockEntityCoalAlloySmelter smelter = machine(helper, BlockEntityCoalAlloySmelter.class);
    if (!smelter.isItemValidForSlot(BlockEntityCoalAlloySmelter.FUEL_SLOT, new ItemStack(Items.COAL))) {
      helper.fail("the coal alloy smelter fuel slot refuses coal");
      return;
    }
    int burn = FuelUtil.burnTime(helper.getLevel(), new ItemStack(Items.CHARCOAL), smelter);
    if (burn != 1600) {
      helper.fail("charcoal burn time in the coal alloy smelter is " + burn + ", expected 1600");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void geoGeneratorTankTakesALavaBucket(GameTestHelper helper) {
    helper.setBlock(MACHINE, M1Registry.GEO_GENERATOR.defaultBlockState());
    BlockEntityGeoGenerator geo = machine(helper, BlockEntityGeoGenerator.class);
    BlockPos playerAbs = helper.absolutePos(PLAYER);
    MockPlayers.Mock mock = MockPlayers.survival(helper, playerAbs.getX() + 0.5, playerAbs.getY(),
        playerAbs.getZ() + 0.5);
    ServerPlayer player = mock.player();
    try {
      player.containerMenu = new MenuGeoGenerator(1, helper.getLevel(), helper.absolutePos(MACHINE),
          player.getInventory(), player);
      player.containerMenu.setCarried(new ItemStack(Items.LAVA_BUCKET));
      PacketCellFill.handle(new PacketCellFill(helper.absolutePos(MACHINE), 0, false), serverContext(player));
      if (geo.fluidStorage.getFluidAmount() != 1000 || !geo.fluidStorage.getFluid().isSame(Fluids.LAVA)) {
        helper.fail("geo generator tank after a lava bucket: " + geo.fluidStorage.getFluidAmount() + " mB");
        return;
      }
      if (!player.containerMenu.getCarried().is(Items.BUCKET)) {
        helper.fail("the cursor should hold an empty bucket, holds " + player.containerMenu.getCarried());
        return;
      }
      player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
      PacketCellFill.handle(new PacketCellFill(helper.absolutePos(MACHINE), 0, false), serverContext(player));
      if (geo.fluidStorage.getFluidAmount() != 1000 || !player.containerMenu.getCarried().is(Items.WATER_BUCKET)) {
        helper.fail("the geo generator took water");
        return;
      }
    } finally {
      player.containerMenu = player.inventoryMenu;
      mock.remove();
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void combustionGeneratorTankTakesFuelCells(GameTestHelper helper) {
    helper.setBlock(MACHINE, M1Registry.COMBUSTION_GENERATOR.defaultBlockState());
    BlockEntityCombustionGenerator combustion = machine(helper, BlockEntityCombustionGenerator.class);
    BlockPos playerAbs = helper.absolutePos(PLAYER);
    MockPlayers.Mock mock = MockPlayers.survival(helper, playerAbs.getX() + 0.5, playerAbs.getY(),
        playerAbs.getZ() + 0.5);
    ServerPlayer player = mock.player();
    int cell = FluidCell.getCapacity();
    try {
      player.containerMenu = new MenuCombustionGenerator(1, helper.getLevel(), helper.absolutePos(MACHINE),
          player.getInventory(), player);
      player.containerMenu.setCarried(fuelCells(1));
      PacketCellFill.handle(new PacketCellFill(helper.absolutePos(MACHINE), 0, false), serverContext(player));
      if (combustion.fluidStorage.getFluidAmount() != cell
          || !combustion.fluidStorage.getFluid().isSame(ModFluids.FUEL.still())) {
        helper.fail("combustion tank after one fuel cell: " + combustion.fluidStorage.getFluidAmount() + " mB");
        return;
      }
      ItemStack carried = player.containerMenu.getCarried();
      if (!carried.is(ModItems.FLUID_CELL) || FluidItem.getFluid(carried) != Fluids.EMPTY) {
        helper.fail("the cursor should hold an empty cell, holds " + carried);
        return;
      }
      player.containerMenu.setCarried(fuelCells(3));
      PacketCellFill.handle(new PacketCellFill(helper.absolutePos(MACHINE), 0, true), serverContext(player));
      if (combustion.fluidStorage.getFluidAmount() != cell * 4) {
        helper.fail("combustion tank after three more cells: " + combustion.fluidStorage.getFluidAmount() + " mB");
        return;
      }
      carried = player.containerMenu.getCarried();
      if (carried.getCount() != 3 || FluidItem.getFluid(carried) != Fluids.EMPTY) {
        helper.fail("the cursor should hold three empty cells, holds " + carried);
        return;
      }
    } finally {
      player.containerMenu = player.inventoryMenu;
      mock.remove();
    }
    helper.succeed();
  }
}
