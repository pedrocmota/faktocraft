package com.faktocraft.gametest.fluids;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.pipe.BlockEntityPump;
import com.faktocraft.common.block.impl.pipe.MenuPump;
import com.faktocraft.common.registries.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.faktocraft.common.registries.PipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;

@net.minecraftforge.gametest.GameTestHolder(Faktocraft.MODID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class PumpGameTest {

  private static final String TEMPLATE = "gametest_shaft";

  private static final BlockPos BASIN = new BlockPos(2, 1, 2);
  private static final BlockPos PUMP_POS = new BlockPos(2, 45, 2);
  private static final int DEPTH = PUMP_POS.getY() - BASIN.getY();

  private static void clearShaft(GameTestHelper helper) {
    for (int y = PUMP_POS.getY(); y >= BASIN.getY(); y--) {
      helper.setBlock(new BlockPos(PUMP_POS.getX(), y, PUMP_POS.getZ()), Blocks.AIR.defaultBlockState());
    }

    for (Direction direction : Direction.values()) {
      helper.setBlock(PUMP_POS.relative(direction), Blocks.AIR.defaultBlockState());
    }
  }

  private static String neighbourReport(GameTestHelper helper) {
    StringBuilder out = new StringBuilder();
    for (Direction direction : Direction.values()) {
      BlockPos neighbour = helper.absolutePos(PUMP_POS.relative(direction));
      net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(neighbour);
      if (state.isAir()) {
        continue;
      }
      out.append(out.length() == 0 ? "" : " ").append(direction).append("=").append(state.getBlock());
      BlockEntity be = helper.getLevel().getBlockEntity(neighbour);
      if (be != null && (be.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent()
          || be.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).isPresent())) {
        out.append("(FLUID)");
      }
    }
    return out.length() == 0 ? "all air" : out.toString();
  }

  private static boolean neighbourhoodIsClean(GameTestHelper helper) {
    for (Direction direction : Direction.values()) {
      BlockPos neighbour = helper.absolutePos(PUMP_POS.relative(direction));
      BlockEntity be = helper.getLevel().getBlockEntity(neighbour);
      if (be != null && be.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent()) {
        helper.fail("a fluid acceptor sits " + direction + " of the pump ("
            + helper.getLevel().getBlockState(neighbour).getBlock() + "): it would take the bucket");
        return false;
      }
    }
    return true;
  }

  private static String columnReport(GameTestHelper helper) {
    for (int y = PUMP_POS.getY() - 1; y >= 1; y--) {
      BlockPos pos = new BlockPos(PUMP_POS.getX(), y, PUMP_POS.getZ());
      net.minecraft.world.level.material.FluidState fluid = helper.getLevel().getFluidState(helper.absolutePos(pos));
      if (!fluid.isEmpty()) {
        return "fluid at y=" + y + " source=" + fluid.isSource();
      }
      net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
      if (!state.isAir() && !state.is(PipeRegistry.PUMP_TUBE_BLOCK)) {
        return "blocked at y=" + y + " by " + state.getBlock();
      }
    }
    return "empty to the bottom";
  }

  private static BlockEntityPump setupShaft(GameTestHelper helper) {

    for (int x = 1; x <= 3; x++) {
      for (int z = 1; z <= 3; z++) {
        if (x != BASIN.getX() || z != BASIN.getZ()) {
          helper.setBlock(new BlockPos(x, 1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        helper.setBlock(new BlockPos(x, 0, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
      }
    }
    helper.setBlock(BASIN, Blocks.WATER.defaultBlockState());
    helper.setBlock(PUMP_POS, PipeRegistry.PUMP.defaultBlockState());
    if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump)) {
      helper.fail("pump block entity missing");
      return null;
    }

    pump.getBatteryStackHandler().setStackInSlot(0,
        new net.minecraft.world.item.ItemStack(com.faktocraft.common.registries.ModItems.INTERMEDIATE_CAPACITOR));
    helper.onEachTick(() -> {
      if (helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump p) {
        p.getEnergyStorage().setEnergy(p.getEnergyStorage().maxEnergy());
      }
    });

    String column = columnReport(helper);
    if (!column.equals("fluid at y=" + BASIN.getY() + " source=true")) {
      helper.fail("the shaft does not reach the basin: " + column);
      return null;
    }
    if (!neighbourhoodIsClean(helper)) {
      return null;
    }
    return pump;
  }

  private static String pumpState(GameTestHelper helper) {
    if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump)) {
      return "pump block entity missing";
    }
    return "tank=" + pump.tank.getFluidAmount()
        + " column[" + columnReport(helper) + "]"
        + " tube=" + pump.tubeDepth + "/" + pump.tubeTarget()
        + " queue=" + pump.queuedSources()
        + " progress=" + pump.pumpProgress() + "/" + pump.energyPerBucket()
        + " energy=" + pump.getEnergyStorage().energyStored()
        + "/" + pump.getEnergyStorage().maxEnergy()
        + " runMode=" + pump.getRunMode()
        + " neighbours[" + neighbourReport(helper) + "]"
        + " tick=" + helper.getTick();
  }

  private static void expectTubeStart(GameTestHelper helper, long byTick) {
    helper.runAtTickTime(byTick, () -> {
      if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump) || pump.tubeDepth <= 0.0F) {
        helper.fail("tube has not started descending by tick " + byTick + "; " + pumpState(helper));
      }
    });
  }

  private static void succeedWhenPumped(GameTestHelper helper) {
    helper.succeedWhen(() -> {
      if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump)) {
        helper.fail("pump block entity missing");
        return;
      }
      if (pump.tank.getFluidAmount() < 1000) {
        helper.fail("nothing pumped yet: " + pumpState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public static void pumpDeepShaft(GameTestHelper helper) {
    clearShaft(helper);
    if (setupShaft(helper) == null) {
      return;
    }
    expectTubeStart(helper, 100);
    succeedWhenPumped(helper);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public static void pumpDeepShaftStaleTube(GameTestHelper helper) {
    clearShaft(helper);

    for (int y = PUMP_POS.getY() - 1; y >= PUMP_POS.getY() - 20; y--) {
      helper.setBlock(new BlockPos(PUMP_POS.getX(), y, PUMP_POS.getZ()),
          PipeRegistry.PUMP_TUBE_BLOCK.defaultBlockState());
    }
    if (setupShaft(helper) == null) {
      return;
    }
    expectTubeStart(helper, 100);
    succeedWhenPumped(helper);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public static void pumpHandsTheBucketToTheNeighbour(GameTestHelper helper) {
    clearShaft(helper);
    BlockPos tank = PUMP_POS.relative(Direction.NORTH);
    if (setupShaft(helper) == null) {
      return;
    }
    helper.setBlock(tank, PipeRegistry.TANK.defaultBlockState());

    helper.succeedWhen(() -> {
      if (!(helper.getBlockEntity(tank) instanceof com.faktocraft.common.block.impl.pipe.BlockEntityTank neighbour)) {
        helper.fail("no tank next to the pump");
        return;
      }
      if (neighbour.tank.getFluidAmount() < 1000) {
        helper.fail("the neighbour has not been handed a bucket yet: "
            + neighbour.tank.getFluidAmount() + " mB; " + pumpState(helper));
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public static void pumpRecoversWhenShaftIsUnblocked(GameTestHelper helper) {
    clearShaft(helper);
    if (setupShaft(helper) == null) {
      return;
    }
    BlockPos obstruction = new BlockPos(PUMP_POS.getX(), 20, PUMP_POS.getZ());
    helper.setBlock(obstruction, Blocks.POLISHED_ANDESITE.defaultBlockState());

    helper.runAtTickTime(100, () -> {
      if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump)) {
        helper.fail("pump block entity missing");
        return;
      }
      if (pump.tubeDepth > 0.0F || pump.tank.getFluidAmount() > 0) {
        helper.fail("the tube must not go down a blocked shaft: " + pumpState(helper));
        return;
      }
      helper.setBlock(obstruction, Blocks.AIR.defaultBlockState());
    });
    succeedWhenPumped(helper);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1800)
  public static void pumpResumesAfterReload(GameTestHelper helper) {
    clearShaft(helper);
    BlockEntityPump pump = setupShaft(helper);
    if (pump == null) {
      return;
    }
    for (int y = BASIN.getY() + 1; y < PUMP_POS.getY(); y++) {
      helper.setBlock(new BlockPos(PUMP_POS.getX(), y, PUMP_POS.getZ()),
          PipeRegistry.PUMP_TUBE_BLOCK.defaultBlockState());
    }
    CompoundTag tag = pump.saveWithoutMetadata();
    tag.putFloat("tubeDepth", DEPTH - 1 + 0.4F);
    tag.putFloat("tubeTarget", DEPTH - 1 + 0.4F);
    pump.load(tag);

    helper.runAtTickTime(560, () -> {
      if (helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump p && p.tank.getFluidAmount() < 1000) {

        helper.fail("resume too slow: " + pumpState(helper));
      }
    });
    succeedWhenPumped(helper);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void pumpAcceptsEfficiencyUpgradesAndSpendsLessPerBucket(GameTestHelper helper) {
    clearShaft(helper);
    helper.setBlock(PUMP_POS, PipeRegistry.PUMP.defaultBlockState());
    if (!(helper.getBlockEntity(PUMP_POS) instanceof BlockEntityPump pump)) {
      helper.fail("no pump block entity");
      return;
    }
    int plain = pump.energyPerBucket();
    pump.getUpgrades().setStackInSlot(0, new ItemStack(ModItems.EFFICIENCY_UPGRADE));
    int efficient = pump.energyPerBucket();
    pump.getUpgrades().setStackInSlot(1, new ItemStack(ModItems.ADVANCED_EFFICIENCY_UPGRADE));
    int advanced = pump.energyPerBucket();
    pump.getUpgrades().setStackInSlot(0, ItemStack.EMPTY);
    pump.getUpgrades().setStackInSlot(1, ItemStack.EMPTY);
    Player player = helper.makeMockPlayer();
    MenuPump menu = new MenuPump(1, helper.getLevel(), helper.absolutePos(PUMP_POS), player.getInventory(), player);
    boolean accepted = false;
    boolean overclockStillAccepted = false;
    for (int i = 0; i < menu.slots.size(); i++) {
      if (menu.slots.get(i).container == pump.getUpgrades()) {
        accepted |= menu.slots.get(i).mayPlace(new ItemStack(ModItems.EFFICIENCY_UPGRADE));
        overclockStillAccepted |= menu.slots.get(i).mayPlace(new ItemStack(ModItems.OVERCLOCKER_UPGRADE));
      }
    }
    helper.assertTrue(efficient < plain, "efficiency upgrade did not lower the cost: " + plain + " -> " + efficient);
    helper.assertTrue(advanced < efficient, "advanced efficiency upgrade did not lower the cost further: "
        + efficient + " -> " + advanced);
    helper.assertTrue(accepted, "pump upgrade slots refuse the efficiency upgrade");
    helper.assertTrue(overclockStillAccepted, "pump upgrade slots refuse the overclocker upgrade");
    helper.succeed();
  }
}
