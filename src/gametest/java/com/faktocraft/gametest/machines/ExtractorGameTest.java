package com.faktocraft.gametest.machines;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.machines.extractor.BlockEntityExtractor;
import com.faktocraft.common.container.FaktocraftMenuProvider;
import com.faktocraft.common.entity.block.BlockEntityStandardMachine;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.MockPlayers;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ExtractorGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos POS = new BlockPos(1, 1, 1);
  private static final int LOGS = 32;
  private static final int MAX_WORK_TICKS = 200000;
  private static final int WORK_PER_TICK = 200;
  private static final int VIEW_TICKS = 40;

  private static BlockEntityExtractor place(GameTestHelper helper) {
    helper.setBlock(POS, M2Registry.EXTRACTOR.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, POS) instanceof BlockEntityExtractor extractor)) {
      throw TestUtil.assertion(helper, "no extractor block entity");
    }
    extractor.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
    extractor.getEnergyStorage().setEnergy(extractor.getEnergyStorage().maxEnergy());
    return extractor;
  }

  private static ItemStack bonus(BlockEntityExtractor extractor) {
    return extractor.getItemStackHandler().getStackInSlot(BlockEntityStandardMachine.BONUS_SLOT);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 40)
  public static void rubberLogsKeepYieldingShards(GameTestHelper helper) {
    BlockEntityExtractor extractor = place(helper);
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
    ItemStack bonus = bonus(extractor);
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

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void bonusSlotCountReachesTheMenuViewer(GameTestHelper helper) {
    BlockEntityExtractor extractor = place(helper);
    extractor.getItemStackHandler().setStackInSlot(BlockEntityStandardMachine.INPUT_SLOT,
        new ItemStack(ModItems.RUBBER_LOG, 64));
    BlockState state = helper.getBlockState(POS);
    BlockPos abs = helper.absolutePos(POS);
    BlockPos playerAbs = helper.absolutePos(POS.east(2));
    MockPlayers.Mock mock = MockPlayers.survival(helper, playerAbs.getX() + 0.5, playerAbs.getY(),
        playerAbs.getZ() + 0.5);
    mock.allowAllPayloads();
    int[] seen = { -1 };
    int[] bonusSlot = { -1 };

    helper.startSequence()
        .thenIdle(2)
        .thenExecute(() -> {
          mock.drainOutbound();
          mock.player().openMenu(new FaktocraftMenuProvider((IHasMenu) M2Registry.EXTRACTOR, helper.getLevel(), abs,
              M2Registry.EXTRACTOR.getName()));
          for (int i = 0; i < mock.player().containerMenu.slots.size(); i++) {
            var slot = mock.player().containerMenu.slots.get(i);
            if (slot.container == extractor.getItemStackHandler()
                && slot.getContainerSlot() == BlockEntityStandardMachine.BONUS_SLOT) {
              bonusSlot[0] = i;
            }
          }
        })
        .thenExecuteFor(VIEW_TICKS, () -> {
          for (int i = 0; i < WORK_PER_TICK; i++) {
            extractor.getEnergyStorage().setEnergy(extractor.getEnergyStorage().maxEnergy());
            extractor.tickWork(state);
          }
          int containerId = mock.player().containerMenu.containerId;
          for (Object packet : mock.flushAndDrainOutbound()) {
            if (packet instanceof ClientboundContainerSetContentPacket content
                && content.containerId() == containerId && bonusSlot[0] >= 0
                && bonusSlot[0] < content.items().size()) {
              seen[0] = content.items().get(bonusSlot[0]).getCount();
            } else if (packet instanceof ClientboundContainerSetSlotPacket set
                && set.getContainerId() == containerId && set.getSlot() == bonusSlot[0]) {
              seen[0] = set.getItem().getCount();
            }
          }
        })
        .thenExecute(() -> {
          int server = bonus(extractor).getCount();
          boolean menuOpen = mock.player().containerMenu != mock.player().inventoryMenu;
          mock.remove();
          Faktocraft.LOGGER.info("[Gametest] extractor viewer: server bonus {} shards, client saw {} (slot {})",
              server, seen[0], bonusSlot[0]);
          if (!menuOpen || bonusSlot[0] < 0) {
            helper.fail("menu not open or bonus slot not found, slot=" + bonusSlot[0]);
            return;
          }
          if (server < 5) {
            helper.fail("the extractor made only " + server + " shards while the menu was open");
            return;
          }
          if (seen[0] != server) {
            helper.fail("menu viewer last saw " + seen[0] + " shards in the bonus slot, server has " + server);
            return;
          }
          helper.succeed();
        });
  }
}
