package com.faktocraft.gametest.parity;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.generators.combustion_generator.BlockEntityCombustionGenerator;
import com.faktocraft.common.block.impl.logistics.BlockEntityChassis;
import com.faktocraft.common.block.impl.logistics.LogisticsRegistry;
import com.faktocraft.common.block.impl.logistics.ModuleSettings;
import com.faktocraft.common.energy.provider.EnergyCore;
import com.faktocraft.common.energy.provider.EnergyNetwork;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.registries.ModBlocks;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M1Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class GoldenParityGameTest {

  private static final String TEMPLATE = "gametest_platform";

  private static final class TestUtil {
    static BlockEntity blockEntity(GameTestHelper helper, BlockPos rel) {
      return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
    }
  }

  private static final String ENERGY_EXPECTED = "gen=5000 mfe=1200 cesu=300000 near=300000 nets=[2, 1, 1:MEDIUM:128:4:3:1, 4, 1, 1:HIGH:512:2:1:1]";

  private static final BlockPos GEN = new BlockPos(1, 1, 1);
  private static final BlockPos MFE = new BlockPos(6, 1, 1);
  private static final BlockPos CESU = new BlockPos(3, 1, 4);
  private static final BlockPos CESU_NEAR = new BlockPos(2, 1, 2);

  private static void placeMachine(GameTestHelper helper, BlockPos rel, Block block, Direction facing) {
    BlockState state = block.defaultBlockState();
    if (block instanceof IStateFacing stateFacing) {
      state = stateFacing.setDirection(state, facing);
    }
    helper.setBlock(rel, state);
    block.setPlacedBy(helper.getLevel(), helper.absolutePos(rel), state, null, ItemStack.EMPTY);
  }

  private static void placeCable(GameTestHelper helper, BlockPos rel, Block block) {
    helper.setBlock(rel, block.defaultBlockState());
    BlockPos abs = helper.absolutePos(rel);
    block.setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), null, ItemStack.EMPTY);
  }

  private static void setEnergy(GameTestHelper helper, BlockPos rel, int energy) {
    if (TestUtil.blockEntity(helper, rel) instanceof FaktocraftBlockEntity be) {
      be.getEnergyStorage().setEnergy(Math.min(energy, be.getEnergyStorage().maxEnergy()));
    } else {
      helper.fail("no block entity at " + rel);
    }
  }

  private static void setEnergyBelowMax(GameTestHelper helper, BlockPos rel, int missing) {
    if (TestUtil.blockEntity(helper, rel) instanceof FaktocraftBlockEntity be) {
      be.getEnergyStorage().setEnergy(Math.max(0, be.getEnergyStorage().maxEnergy() - missing));
    } else {
      helper.fail("no block entity at " + rel);
    }
  }

  private static int energy(GameTestHelper helper, BlockPos rel) {
    return TestUtil.blockEntity(helper, rel) instanceof FaktocraftBlockEntity be
        ? be.getEnergyStorage().energyStored()
        : -1;
  }

  private static String energySignature(GameTestHelper helper) {
    StringBuilder sb = new StringBuilder();
    sb.append("gen=").append(energy(helper, GEN));
    sb.append(" mfe=").append(energy(helper, MFE));
    sb.append(" cesu=").append(energy(helper, CESU));
    sb.append(" near=").append(energy(helper, CESU_NEAR));
    BlockPos origin = helper.absolutePos(BlockPos.ZERO);
    List<String> nets = new ArrayList<>();
    for (EnergyNetwork network : EnergyCore.get(helper.getLevel()).getNetworks().getNetworks()) {
      BlockPos first = null;
      for (BlockPos pos : network.getConnections()) {
        if (first == null || pos.compareTo(first) < 0) {
          first = pos;
        }
      }
      if (first == null) {
        continue;
      }
      BlockPos rel = first.subtract(origin);
      if (rel.getX() < 0 || rel.getX() >= 12 || rel.getY() < 0 || rel.getY() >= 4 || rel.getZ() < 0
          || rel.getZ() >= 12) {
        continue;
      }
      nets.add(rel.toShortString() + ":" + network.getEnergyTier() + ":"
          + network.energyStored() + ":" + network.getConnections().size() + ":" + network.getElectrics().size()
          + ":" + network.getTransmitters().size());
    }
    nets.sort(String::compareTo);
    sb.append(" nets=").append(nets);
    return sb.toString();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 260)
  public static void goldenEnergy1201(GameTestHelper helper) {
    placeMachine(helper, GEN, M1Registry.COMBUSTION_GENERATOR, Direction.WEST);
    placeCable(helper, new BlockPos(2, 1, 1), ModBlocks.COPPER_CABLE);
    placeCable(helper, new BlockPos(3, 1, 1), ModBlocks.COPPER_CABLE);
    placeCable(helper, new BlockPos(4, 1, 1), ModBlocks.GOLD_CABLE_INSULATED);
    placeCable(helper, new BlockPos(5, 1, 1), ModBlocks.GOLD_CABLE_INSULATED);
    placeMachine(helper, MFE, M1Registry.MFE, Direction.EAST);
    placeCable(helper, new BlockPos(3, 1, 2), ModBlocks.COPPER_CABLE);
    placeCable(helper, new BlockPos(3, 1, 3), ModBlocks.COPPER_CABLE);
    placeMachine(helper, CESU, M1Registry.CESU, Direction.SOUTH);
    placeMachine(helper, CESU_NEAR, M1Registry.CESU, Direction.SOUTH);

    setEnergy(helper, GEN, Integer.MAX_VALUE);
    setEnergy(helper, MFE, 1200);
    setEnergyBelowMax(helper, CESU, 700);
    setEnergyBelowMax(helper, CESU_NEAR, 300);
    if (TestUtil.blockEntity(helper, GEN) instanceof BlockEntityCombustionGenerator gen) {
      gen.fluidStorage.fillFluid(new FluidStack(ModFluids.BIOGAS.still(), 8000), 8000, false);
    }

    helper.runAfterDelay(200, () -> {
      String actual = energySignature(helper);
      if (!ENERGY_EXPECTED.equals(actual)) {
        helper.fail("golden mismatch: " + actual);
        return;
      }
      helper.succeed();
    });
  }

  private static final String LOGISTICS_EXPECTED = "S{}[] B{cobblestone=8}[0=cobblestonex8] C{iron_ingot=27}[0=iron_ingotx27]"
      + " D{}[] E{cobblestone=12, oak_planks=10}[0=cobblestonex12, 1=oak_planksx10]";

  private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
  private static final BlockPos CHASSIS_A = new BlockPos(2, 1, 1);
  private static final BlockPos CHASSIS_B = new BlockPos(4, 1, 1);
  private static final BlockPos CHEST_B = new BlockPos(4, 1, 2);
  private static final BlockPos CHASSIS_C = new BlockPos(6, 1, 1);
  private static final BlockPos CHEST_C = new BlockPos(6, 1, 2);
  private static final BlockPos CHASSIS_D = new BlockPos(7, 1, 1);
  private static final BlockPos CHEST_D = new BlockPos(7, 1, 2);
  private static final BlockPos CHASSIS_E = new BlockPos(8, 1, 1);
  private static final BlockPos CHEST_E = new BlockPos(8, 1, 2);
  private static final BlockPos CORE = new BlockPos(2, 2, 1);

  private static void placeChest(GameTestHelper helper, BlockPos rel, ItemStack... stacks) {
    helper.setBlock(rel, Blocks.CHEST.defaultBlockState());
    if (TestUtil.blockEntity(helper, rel) instanceof ChestBlockEntity chest) {
      for (int i = 0; i < stacks.length; i++) {
        chest.setItem(i, stacks[i]);
      }
    }
  }

  private static void putModule(GameTestHelper helper, BlockPos rel, int slot, ItemStack module) {
    if (TestUtil.blockEntity(helper, rel) instanceof BlockEntityChassis chassis) {
      chassis.getModules().setStackInSlot(slot, module);
    } else {
      helper.fail("no chassis at " + rel);
    }
  }

  private static ItemStack sink(ItemStack filter, int priority, boolean overflow) {
    ItemStack module = new ItemStack(LogisticsRegistry.MODULE_SINK);
    if (filter != null) {
      ModuleSettings.setLine(module, 0, new ModuleSettings.FilterLine(ModuleSettings.LineMode.ITEM, filter, "",
          false, false, 0));
    }
    ModuleSettings.setPriority(module, priority);
    if (overflow) {
      ModuleSettings.setFlag(module, ModuleSettings.FLAG_OVERFLOW, true);
    }
    return module;
  }

  private static String chestSignature(GameTestHelper helper, String name, BlockPos rel) {
    Map<String, Integer> counts = new TreeMap<>();
    List<String> slots = new ArrayList<>();
    if (TestUtil.blockEntity(helper, rel) instanceof ChestBlockEntity chest) {
      for (int i = 0; i < chest.getContainerSize(); i++) {
        ItemStack stack = chest.getItem(i);
        if (!stack.isEmpty()) {
          String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
          counts.merge(id, stack.getCount(), Integer::sum);
          slots.add(i + "=" + id + "x" + stack.getCount());
        }
      }
    }
    return name + counts + slots;
  }

  private static String logisticsSignature(GameTestHelper helper) {
    return chestSignature(helper, "S", SOURCE) + " " + chestSignature(helper, "B", CHEST_B) + " "
        + chestSignature(helper, "C", CHEST_C) + " " + chestSignature(helper, "D", CHEST_D) + " "
        + chestSignature(helper, "E", CHEST_E);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 500)
  public static void goldenLogistics1201(GameTestHelper helper) {
    placeChest(helper, SOURCE, new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.COBBLESTONE, 20),
        new ItemStack(Items.OAK_PLANKS, 10), new ItemStack(Items.IRON_INGOT, 7));
    helper.setBlock(CHASSIS_A, LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(new BlockPos(3, 1, 1), LogisticsRegistry.STONE_PIPE.defaultBlockState());
    helper.setBlock(CHASSIS_B, LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(new BlockPos(5, 1, 1), LogisticsRegistry.STONE_PIPE.defaultBlockState());
    helper.setBlock(CHASSIS_C, LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(CHASSIS_D, LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(CHASSIS_E, LogisticsRegistry.CHASSIS_1.defaultBlockState());
    helper.setBlock(CORE, LogisticsRegistry.LOGISTICS_CONTROLLER.defaultBlockState());
    placeChest(helper, CHEST_B);
    placeChest(helper, CHEST_C);
    placeChest(helper, CHEST_D);
    placeChest(helper, CHEST_E);

    if (TestUtil.blockEntity(helper, CORE) instanceof FaktocraftBlockEntity core) {
      core.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
      core.getEnergyStorage().setEnergy(core.getEnergyStorage().maxEnergy());
    }

    putModule(helper, CHASSIS_A, 0, new ItemStack(LogisticsRegistry.MODULE_EXTRACTOR));
    putModule(helper, CHASSIS_A, 1, new ItemStack(LogisticsRegistry.MODULE_PROVIDER));

    ItemStack supplier = new ItemStack(LogisticsRegistry.MODULE_SUPPLIER);
    ModuleSettings.setLine(supplier, 0, new ModuleSettings.FilterLine(ModuleSettings.LineMode.ITEM,
        new ItemStack(Items.COBBLESTONE), "", false, false, 8));
    putModule(helper, CHASSIS_B, 0, supplier);

    putModule(helper, CHASSIS_C, 0, sink(new ItemStack(Items.IRON_INGOT), 1, false));
    putModule(helper, CHASSIS_D, 0, sink(new ItemStack(Items.IRON_INGOT), 0, false));
    putModule(helper, CHASSIS_E, 0, sink(null, 0, true));
    putModule(helper, CHASSIS_E, 1, new ItemStack(LogisticsRegistry.MODULE_PROVIDER));

    helper.runAfterDelay(400, () -> {
      String actual = logisticsSignature(helper);
      if (!LOGISTICS_EXPECTED.equals(actual)) {
        helper.fail("golden mismatch: " + actual);
        return;
      }
      helper.succeed();
    });
  }
}
