package com.faktocraft.gametest.machines;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.machines.extractor.BlockEntityExtractor;
import com.faktocraft.common.entity.block.BlockEntityStandardMachine;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M2Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class ExtractorGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos POS = new BlockPos(1, 1, 1);
  private static final int LOGS = 32;
  private static final int MAX_WORK_TICKS = 200000;

  @GameTest(template = TEMPLATE, timeoutTicks = 40)
  public static void rubberLogsKeepYieldingShards(GameTestHelper helper) {
    helper.setBlock(POS, M2Registry.EXTRACTOR.defaultBlockState());
    if (!(helper.getBlockEntity(POS) instanceof BlockEntityExtractor extractor)) {
      helper.fail("no extractor block entity");
      return;
    }
    extractor.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
    extractor.getEnergyStorage().setEnergy(extractor.getEnergyStorage().maxEnergy());
    extractor.getItemStackHandler().setStackInSlot(BlockEntityStandardMachine.INPUT_SLOT,
        new ItemStack(ModItems.RUBBER_LOG, LOGS));
    BlockState state = helper.getBlockState(POS);
    int worked = 0;
    while (!extractor.getItemStackHandler().getStackInSlot(BlockEntityStandardMachine.INPUT_SLOT).isEmpty()
        && worked < MAX_WORK_TICKS) {
      extractor.getEnergyStorage().setEnergy(extractor.getEnergyStorage().maxEnergy());
      extractor.tickWork(state);
      worked++;
    }
    ItemStack output = extractor.getItemStackHandler().getStackInSlot(BlockEntityStandardMachine.OUTPUT_SLOT);
    ItemStack bonus = extractor.getItemStackHandler().getStackInSlot(BlockEntityStandardMachine.BONUS_SLOT);
    Faktocraft.LOGGER.info("[Gametest] extractor: {} logs -> {} and {} after {} work ticks", LOGS, output, bonus,
        worked);
    if (worked >= MAX_WORK_TICKS) {
      helper.fail("the extractor never finished " + LOGS + " logs: output=" + output + " bonus=" + bonus);
      return;
    }
    if (!output.is(ModItems.DRIED_RUBBER_LOG) || output.getCount() != LOGS) {
      helper.fail("expected " + LOGS + " dried rubber logs, found " + output);
      return;
    }
    if (!bonus.is(ModItems.RUBBER_SHARD) || bonus.getCount() < 5) {
      helper.fail("expected several rubber shards from " + LOGS + " logs at 40%, found " + bonus);
      return;
    }
    helper.succeed();
  }
}
