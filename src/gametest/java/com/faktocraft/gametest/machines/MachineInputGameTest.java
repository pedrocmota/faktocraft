package com.faktocraft.gametest.machines;

import com.faktocraft.common.block.impl.machines.alloy_smelter.AbstractBlockEntityAlloySmelter;
import com.faktocraft.common.block.impl.machines.alloy_smelter.BlockEntityCoalAlloySmelter;
import com.faktocraft.common.block.impl.machines.circuit_assembler.BlockEntityCircuitAssembler;
import com.faktocraft.common.block.impl.machines.fluid_enricher.BlockEntityFluidEnricher;
import com.faktocraft.common.block.impl.machines.metal_former.BlockEntityMetalFormer;
import com.faktocraft.common.block.impl.machines.ore_washing_plant.BlockEntityOreWashingPlant;
import com.faktocraft.common.block.impl.machines.polymerizer.BlockEntityPolymerizer;
import com.faktocraft.common.block.impl.machines.thermal_centrifuge.BlockEntityThermalCentrifuge;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.MetalFormerMode;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class MachineInputGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos POS = new BlockPos(1, 1, 1);
  private static final int MODES = 3;

  private static FaktocraftBlockEntity place(GameTestHelper helper, Block block) {
    helper.setBlock(POS, block.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, POS) instanceof FaktocraftBlockEntity machine)) {
      throw TestUtil.assertion(helper, "no machine block entity for " + block);
    }
    return machine;
  }

  private static void accepts(GameTestHelper helper, FaktocraftBlockEntity machine, int slot, Item item) {
    if (!machine.isItemValidForSlot(slot, new ItemStack(item))) {
      helper.fail(machine.getClass().getSimpleName() + " refused " + item + " in slot " + slot);
    }
  }

  private static void refuses(GameTestHelper helper, FaktocraftBlockEntity machine, int slot, Item item) {
    if (machine.isItemValidForSlot(slot, new ItemStack(item))) {
      helper.fail(machine.getClass().getSimpleName() + " accepted " + item + " in slot " + slot);
    }
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void circuitAssemblerRefusesCoalInEverySlot(GameTestHelper helper) {
    FaktocraftBlockEntity machine = place(helper, M3Registry.CIRCUIT_ASSEMBLER);
    for (int slot = BlockEntityCircuitAssembler.INPUT_SLOT_0; slot
        <= BlockEntityCircuitAssembler.INPUT_SLOT_2; slot++) {
      refuses(helper, machine, slot, Items.COAL);
      accepts(helper, machine, slot, ModItems.ELECTRONIC_CIRCUIT);
      accepts(helper, machine, slot, ModItems.PLASTIC_PLATE);
      accepts(helper, machine, slot, ModItems.PURIFIED_GOLD);
    }
    refuses(helper, machine, BlockEntityCircuitAssembler.OUTPUT_SLOT, ModItems.ELECTRONIC_CIRCUIT);
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void alloySmeltersRefuseCoalInEveryInputSlot(GameTestHelper helper) {
    for (Block block : new Block[] { M3Registry.ALLOY_SMELTER, M3Registry.COAL_ALLOY_SMELTER,
        M3Registry.COMBUSTION_ALLOY_SMELTER }) {
      FaktocraftBlockEntity machine = place(helper, block);
      for (int slot = AbstractBlockEntityAlloySmelter.INPUT_SLOT_0; slot
          <= AbstractBlockEntityAlloySmelter.INPUT_SLOT_2; slot++) {
        refuses(helper, machine, slot, Items.COAL);
        accepts(helper, machine, slot, Items.COPPER_INGOT);
        accepts(helper, machine, slot, ModItems.TIN_INGOT);
      }
      if (block == M3Registry.COAL_ALLOY_SMELTER) {
        accepts(helper, machine, BlockEntityCoalAlloySmelter.FUEL_SLOT, Items.COAL);
        refuses(helper, machine, BlockEntityCoalAlloySmelter.FUEL_SLOT, Items.COPPER_INGOT);
      }
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void fluidEnricherRefusesCoal(GameTestHelper helper) {
    FaktocraftBlockEntity machine = place(helper, M3Registry.FLUID_ENRICHER);
    for (int slot : new int[] { BlockEntityFluidEnricher.INPUT_SLOT, BlockEntityFluidEnricher.INPUT_SLOT_2 }) {
      refuses(helper, machine, slot, Items.COAL);
      accepts(helper, machine, slot, ModItems.BIO_CHAFF);
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void metalFormerRefusesCoalInEveryMode(GameTestHelper helper) {
    if (!(place(helper, M3Registry.METAL_FORMER) instanceof BlockEntityMetalFormer former)) {
      throw TestUtil.assertion(helper, "no metal former block entity");
    }
    for (int i = 0; i < MODES; i++) {
      MetalFormerMode mode = former.getMode();
      Item valid = mode == MetalFormerMode.CUTTING ? ModItems.COPPER_PLATE
          : mode == MetalFormerMode.ROLLING ? ModItems.BRONZE_INGOT : Items.COPPER_INGOT;
      refuses(helper, former, BlockEntityMetalFormer.INPUT_SLOT, Items.COAL);
      accepts(helper, former, BlockEntityMetalFormer.INPUT_SLOT, valid);
      former.changeMode();
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void oreWashingPlantRefusesCoal(GameTestHelper helper) {
    FaktocraftBlockEntity machine = place(helper, M3Registry.ORE_WASHING_PLANT);
    refuses(helper, machine, BlockEntityOreWashingPlant.INPUT_SLOT, Items.COAL);
    accepts(helper, machine, BlockEntityOreWashingPlant.INPUT_SLOT, ModItems.COPPER_CHUNK);
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void polymerizerRefusesCoal(GameTestHelper helper) {
    FaktocraftBlockEntity machine = place(helper, M3Registry.POLYMERIZER);
    refuses(helper, machine, BlockEntityPolymerizer.INPUT_SLOT, Items.COAL);
    accepts(helper, machine, BlockEntityPolymerizer.INPUT_SLOT, ModItems.COAL_DUST);
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void thermalCentrifugeRefusesCoal(GameTestHelper helper) {
    FaktocraftBlockEntity machine = place(helper, M3Registry.THERMAL_CENTRIFUGE);
    refuses(helper, machine, BlockEntityThermalCentrifuge.INPUT_SLOT, Items.COAL);
    accepts(helper, machine, BlockEntityThermalCentrifuge.INPUT_SLOT, ModItems.BRONZE_INGOT);
    helper.succeed();
  }
}
