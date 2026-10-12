package com.faktocraft.gametest.world;

import com.faktocraft.common.block.impl.forester.BlockEntityForester;
import com.faktocraft.common.block.impl.forester.ForesterRegistry;
import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.block.impl.quarry.BlockEntityQuarry;
import com.faktocraft.common.block.impl.quarry.QuarryRegistry;
import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class GantrySpeedGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos QUARRY = new BlockPos(1, 2, 1);
  private static final BlockPos FORESTER = new BlockPos(4, 2, 4);
  private static final float GLOBAL = 1.1F;
  private static final float EPSILON = 1.0E-4F;

  private static BlockEntityGantry place(GameTestHelper helper, BlockPos rel, Block block) {
    helper.setBlock(rel, block.defaultBlockState());
    BlockPos abs = helper.absolutePos(rel);
    block.setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), null, ItemStack.EMPTY);
    return TestUtil.blockEntity(helper, rel) instanceof BlockEntityGantry gantry ? gantry : null;
  }

  private static void maxOverclock(BlockEntityGantry gantry) {
    gantry.getUpgrades().setStackInSlot(0, new ItemStack(ModItems.ADVANCED_OVERCLOCKER_UPGRADE));
    gantry.getUpgrades().setStackInSlot(1, new ItemStack(ModItems.ADVANCED_OVERCLOCKER_UPGRADE));
    gantry.getUpgrades().setStackInSlot(2, new ItemStack(ModItems.OVERCLOCKER_UPGRADE));
    gantry.getUpgrades().setStackInSlot(3, new ItemStack(ModItems.OVERCLOCKER_UPGRADE));
  }

  private static int legacyDraw(int config, int boost) {
    return (int) Math.ceil(Math.max(1, config) * GLOBAL * Math.pow(1.6 / 0.65, boost));
  }

  private static float legacyArm(int boost) {
    return GLOBAL * Math.min(1.2F, 0.25F * (1.0F + 0.35F * boost));
  }

  private static boolean check(GameTestHelper helper, String what, double got, double expected) {
    if (Math.abs(got - expected) > EPSILON) {
      helper.fail(what + " = " + got + ", expected " + expected);
      return false;
    }
    return true;
  }

  @GameTest(template = TEMPLATE)
  public static void quarryBaseSpeedDoubledMaxUnchanged(GameTestHelper helper) {
    BlockEntityGantry quarry = place(helper, QUARRY, QuarryRegistry.QUARRY);
    if (!(quarry instanceof BlockEntityQuarry)) {
      helper.fail("no quarry block entity");
      return;
    }
    int config = ModConfig.server().quarry_max_draw_per_tick;
    int doubledDraw = (int) Math.ceil(Math.max(1, config) * GLOBAL * 2);
    if (!check(helper, "quarry base draw", quarry.maxDrawPerTick(), doubledDraw)
        || !check(helper, "quarry base arm speed", quarry.armSpeed(), 2 * legacyArm(0))) {
      return;
    }
    maxOverclock(quarry);
    if (!check(helper, "quarry max draw", quarry.maxDrawPerTick(), legacyDraw(config, 8))
        || !check(helper, "quarry max arm speed", quarry.armSpeed(), legacyArm(8))) {
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void foresterSpeedUnchanged(GameTestHelper helper) {
    BlockEntityGantry forester = place(helper, FORESTER, ForesterRegistry.FORESTER);
    if (!(forester instanceof BlockEntityForester)) {
      helper.fail("no forester block entity");
      return;
    }
    int config = ModConfig.server().forester_max_draw_per_tick;
    if (!check(helper, "forester base draw", forester.maxDrawPerTick(), legacyDraw(config, 0))
        || !check(helper, "forester base arm speed", forester.armSpeed(), legacyArm(0))) {
      return;
    }
    maxOverclock(forester);
    if (!check(helper, "forester max draw", forester.maxDrawPerTick(), legacyDraw(config, 8))
        || !check(helper, "forester max arm speed", forester.armSpeed(), legacyArm(8))) {
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void headPredictionNeverStepsBackwards(GameTestHelper helper) {
    double speed = 0.5;
    double[] goal = { 10.5, 7.0, 0.5 };
    double[] synced = { 0.5, 7.0, 0.5 };
    double[] client = { 0.5, 7.0, 0.5 };
    double[] predicted = new double[3];
    double lastX = client[0];
    for (int tick = 1; tick <= 40; tick++) {
      int syncTick = tick - tick % 5;
      synced[0] = Math.min(goal[0], 0.5 + speed * syncTick);
      for (int frame = 0; frame < 4; frame++) {
        double now = tick + frame * 0.25;
        com.faktocraft.common.block.impl.quarry.GantryHeadMotion.predict(synced, goal, speed, now - syncTick,
            predicted);
        com.faktocraft.common.block.impl.quarry.GantryHeadMotion.follow(client, predicted, speed, 0.25);
        helper.assertTrue(client[0] + 1.0E-6 >= lastX, "head moved backwards at tick " + now
            + ": " + lastX + " -> " + client[0]);
        helper.assertTrue(Math.abs(client[0] - Math.min(goal[0], 0.5 + speed * now)) < 0.75,
            "head drifted from the server path at tick " + now + ": " + client[0]);
        lastX = client[0];
      }
    }
    helper.assertTrue(Math.abs(client[0] - goal[0]) < 1.0E-6, "head did not settle on the goal: " + client[0]);
    helper.succeed();
  }
}
