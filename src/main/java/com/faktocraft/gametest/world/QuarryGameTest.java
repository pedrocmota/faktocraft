package com.faktocraft.gametest.world;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.quarry.BlockEntityQuarry;
import com.faktocraft.common.block.impl.quarry.QuarryRegistry;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class QuarryGameTest {

  private static final String TEMPLATE = "gametest_platform";

  private static final BlockPos QUARRY = new BlockPos(0, 2, 1);
  private static final BlockPos MARK_CORNER = new BlockPos(1, 2, 1);
  private static final BlockPos MARK_X = new BlockPos(10, 2, 1);
  private static final BlockPos MARK_Z = new BlockPos(1, 2, 10);

  private static void place(GameTestHelper helper, BlockPos rel, Block block) {
    helper.setBlock(rel, block.defaultBlockState());
    BlockPos abs = helper.absolutePos(rel);
    block.setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), null, ItemStack.EMPTY);
  }

  private static BlockEntityQuarry quarry(GameTestHelper helper) {
    if (helper.getBlockEntity(QUARRY) instanceof BlockEntityQuarry be) {
      return be;
    }
    throw new IllegalStateException("no quarry block entity");
  }

  private static void fillEnergy(GameTestHelper helper) {
    if (helper.getBlockEntity(QUARRY) instanceof FaktocraftBlockEntity be) {

      if (be.getBatteryStackHandler().getStackInSlot(0).isEmpty()) {
        be.getBatteryStackHandler().setStackInSlot(0,
            new ItemStack(com.faktocraft.common.registries.ModItems.INTERMEDIATE_CAPACITOR));
      }
      be.getEnergyStorage().setEnergy(be.getEnergyStorage().maxEnergy());
    }
  }

  private static boolean inventoryContains(BlockEntityQuarry quarry, net.minecraft.world.item.Item item) {
    for (int i = 0; i < quarry.getInventory().getSlots(); i++) {
      if (quarry.getInventory().getStackInSlot(i).is(item)) {
        return true;
      }
    }
    return false;
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 4000)
  public static void landmarkAreaMinesOreButSparesSpawner(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(new BlockPos(4, 1, 4), Blocks.DIAMOND_ORE.defaultBlockState());
    helper.setBlock(new BlockPos(3, 1, 3), Blocks.SPAWNER.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          BlockEntityQuarry quarry = quarry(helper);
          helper.assertTrue(quarry.hasArea(), "area was not resolved from the landmarks");
          helper.assertTrue(quarry.areaMinX() == helper.absolutePos(MARK_CORNER).getX()
              && quarry.areaMaxX() == helper.absolutePos(MARK_X).getX()
              && quarry.areaMinZ() == helper.absolutePos(MARK_CORNER).getZ()
              && quarry.areaMaxZ() == helper.absolutePos(MARK_Z).getZ(), "area rectangle mismatch");
        })
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 2, 1));
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 6, 1));
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(1, 4, 1));
        })
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertTrue(inventoryContains(quarry(helper), Items.DIAMOND),
              "diamond ore was not mined into the internal inventory");
        })
        .thenExecute(() -> {
          helper.assertBlockPresent(Blocks.SPAWNER, new BlockPos(3, 1, 3));
          helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, new BlockPos(4, 1, 4));
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 4000)
  public static void landmarkAreaMinesUntaggedBlockButSparesChestAndModBlock(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    BlockPos bricks = new BlockPos(4, 1, 4);
    BlockPos chest = new BlockPos(3, 1, 3);
    BlockPos casing = new BlockPos(5, 1, 5);
    helper.setBlock(bricks, Blocks.BRICKS.defaultBlockState());
    helper.setBlock(chest, Blocks.CHEST.defaultBlockState());
    helper.setBlock(casing, com.faktocraft.common.registries.ModBlocks.BASIC_MACHINE_CASING.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertTrue(quarry(helper).hasArea(), "area was not resolved from the landmarks");
        })
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertTrue(inventoryContains(quarry(helper), Items.BRICKS),
              "untagged bricks were not mined into the internal inventory");
        })
        .thenExecute(() -> {
          helper.assertBlockPresent(Blocks.CHEST, chest);
          helper.assertBlockPresent(com.faktocraft.common.registries.ModBlocks.BASIC_MACHINE_CASING, casing);
          helper.assertBlockNotPresent(Blocks.BRICKS, bricks);
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 600)
  public static void breakingQuarryRemovesFrame(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 2, 1));
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 6, 1));
        })
        .thenExecute(() -> helper.destroyBlock(QUARRY))
        .thenWaitUntil(() -> {
          helper.assertBlockNotPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 2, 1));
          helper.assertBlockNotPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(4, 6, 1));
          helper.assertBlockNotPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(1, 4, 1));
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void quarryNextToArmEndResolvesSameArea(GameTestHelper helper) {
    BlockPos quarryPos = new BlockPos(11, 2, 1);
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    place(helper, quarryPos, QuarryRegistry.QUARRY);

    helper.succeedWhen(() -> {
      fillEnergy(helper);
      if (!(helper.getBlockEntity(quarryPos) instanceof BlockEntityQuarry quarry)) {
        helper.fail("no quarry block entity");
        return;
      }
      helper.assertTrue(quarry.hasArea(), "area not resolved from the arm-end landmark");
      helper.assertTrue(quarry.areaMinX() == helper.absolutePos(MARK_CORNER).getX()
          && quarry.areaMaxX() == helper.absolutePos(MARK_X).getX()
          && quarry.areaMinZ() == helper.absolutePos(MARK_CORNER).getZ()
          && quarry.areaMaxZ() == helper.absolutePos(MARK_Z).getZ(), "area rectangle mismatch");
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void quarryBesideEdgeResolvesSameArea(GameTestHelper helper) {
    BlockPos quarryPos = new BlockPos(5, 2, 0);
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    place(helper, quarryPos, QuarryRegistry.QUARRY);

    helper.succeedWhen(() -> {
      fillEnergy(helper);
      if (!(helper.getBlockEntity(quarryPos) instanceof BlockEntityQuarry quarry)) {
        helper.fail("no quarry block entity");
        return;
      }
      helper.assertTrue(quarry.hasArea(), "area not resolved from the edge between landmarks");
      helper.assertTrue(quarry.areaMinX() == helper.absolutePos(MARK_CORNER).getX()
          && quarry.areaMaxX() == helper.absolutePos(MARK_X).getX()
          && quarry.areaMinZ() == helper.absolutePos(MARK_CORNER).getZ()
          && quarry.areaMaxZ() == helper.absolutePos(MARK_Z).getZ(), "area rectangle mismatch");
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void incompleteLandmarksReportInvalidArea(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          BlockEntityQuarry quarry = quarry(helper);
          helper.assertTrue(!quarry.hasArea()
              && quarry.getStatus() == BlockEntityQuarry.STATUS_INVALID_AREA,
              "expected INVALID_AREA while the L is incomplete");
        })
        .thenExecute(() -> {
          helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
          helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
        })
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertTrue(quarry(helper).hasArea(), "area not resolved after completing the L");
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 4000)
  public static void unpoweredQuarryClearsFlowersAndBuildsItsFrame(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    BlockPos ringFlower = new BlockPos(5, 2, 1);
    BlockPos insideFlower = new BlockPos(5, 2, 5);
    helper.setBlock(ringFlower.below(), Blocks.GRASS_BLOCK.defaultBlockState());
    helper.setBlock(insideFlower.below(), Blocks.GRASS_BLOCK.defaultBlockState());
    helper.setBlock(ringFlower, Blocks.POPPY.defaultBlockState());
    helper.setBlock(insideFlower, Blocks.DANDELION.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> helper.assertTrue(quarry(helper).hasArea(), "area was not resolved from the landmarks"))
        .thenWaitUntil(() -> {
          BlockEntityQuarry quarry = quarry(helper);
          helper.assertTrue(quarry.stage() >= BlockEntityQuarry.STAGE_WORK,
              "unpowered quarry did not reach the work stage, status " + quarry.getStatus() + " stage " + quarry.stage()
                  + " ring:" + ringReport(helper));
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, ringFlower);
          helper.assertBlockNotPresent(Blocks.DANDELION, insideFlower);
        })
        .thenExecute(() -> {
          helper.assertTrue(quarry(helper).getEnergyStorage().energyStored() == 0, "quarry spent energy it never had");
          helper.assertTrue(inventoryContains(quarry(helper), Items.POPPY), "ring poppy was not collected");
          helper.assertTrue(inventoryContains(quarry(helper), Items.DANDELION), "inside dandelion was not collected");
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void blockOnTheRingRefusesPlacementWithAMessage(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    BlockPos dirt = new BlockPos(5, 2, 1);
    helper.setBlock(dirt, Blocks.DIRT.defaultBlockState());
    helper.setBlock(QUARRY.below(), Blocks.STONE.defaultBlockState());
    BlockPos quarryAbs = helper.absolutePos(QUARRY);
    MessagePlayer player = new MessagePlayer(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)));
    BlockState refused = QuarryRegistry.QUARRY.getStateForPlacement(placeContext(helper, player, quarryAbs));
    boolean warned = false;
    StringBuilder seen = new StringBuilder();
    for (Component message : player.messages) {
      seen.append(' ').append(message.getString());
      if (message.getContents() instanceof TranslatableContents contents
          && contents.getKey().equals("chat.faktocraft.frame_obstructed")) {
        warned = true;
      }
    }
    helper.setBlock(dirt, Blocks.AIR.defaultBlockState());
    BlockState allowed = QuarryRegistry.QUARRY.getStateForPlacement(placeContext(helper, player, quarryAbs));
    helper.setBlock(MARK_CORNER, Blocks.AIR.defaultBlockState());
    helper.setBlock(MARK_X, Blocks.AIR.defaultBlockState());
    helper.setBlock(MARK_Z, Blocks.AIR.defaultBlockState());
    helper.assertTrue(refused == null, "quarry was placeable with dirt on the ring");
    helper.assertTrue(warned, "no obstruction message reached the player, chat seen:" + seen);
    helper.assertTrue(allowed != null, "quarry was not placeable once the dirt was gone, ring:" + ringReport(helper));
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 4000)
  public static void unpoweredQuarryBuildsItsFrameBeforeClearingTheInside(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    BlockPos lower = new BlockPos(5, 2, 5);
    BlockPos upper = new BlockPos(5, 3, 5);
    helper.setBlock(lower, Blocks.DIRT.defaultBlockState());
    helper.setBlock(upper, Blocks.DIRT.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> helper.assertTrue(quarry(helper).hasArea(), "area was not resolved from the landmarks"))
        .thenWaitUntil(() -> {
          BlockEntityQuarry quarry = quarry(helper);
          helper.assertTrue(quarry.stage() >= BlockEntityQuarry.STAGE_WORK,
              "unpowered quarry did not build its frame, status " + quarry.getStatus());
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(5, 2, 1));
          helper.assertBlockPresent(QuarryRegistry.QUARRY_FRAME, new BlockPos(1, 6, 1));
          helper.assertTrue(quarry.getStatus() == BlockEntityQuarry.STATUS_NO_ENERGY,
              "expected the quarry to wait for energy with its frame built, status " + quarry.getStatus());
        })
        .thenExecute(() -> {
          helper.assertBlockPresent(Blocks.DIRT, upper);
          helper.assertBlockPresent(Blocks.DIRT, lower);
        })
        .thenWaitUntil(() -> {
          fillEnergy(helper);
          helper.assertBlockNotPresent(Blocks.DIRT, upper);
          helper.assertBlockNotPresent(Blocks.DIRT, lower);
        })
        .thenSucceed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "landmarkNeighbours")
  public static void neighbouringLandmarkSetDoesNotHijackTheArea(GameTestHelper helper) {
    helper.setBlock(MARK_CORNER, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_X, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(MARK_Z, QuarryRegistry.LANDMARK.defaultBlockState());
    BlockPos otherCorner = new BlockPos(1, 2, -18);
    BlockPos otherX = new BlockPos(10, 2, -18);
    BlockPos otherZ = new BlockPos(1, 2, -9);
    helper.setBlock(otherCorner, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(otherX, QuarryRegistry.LANDMARK.defaultBlockState());
    helper.setBlock(otherZ, QuarryRegistry.LANDMARK.defaultBlockState());
    place(helper, QUARRY, QuarryRegistry.QUARRY);

    helper.startSequence()
        .thenWaitUntil(() -> {
          BlockEntityQuarry quarry = quarry(helper);
          helper.assertTrue(quarry.hasArea(), "area was not resolved, status " + quarry.getStatus());
        })
        .thenExecute(() -> {
          BlockEntityQuarry quarry = quarry(helper);
          boolean own = quarry.areaMinZ() == helper.absolutePos(MARK_CORNER).getZ()
              && quarry.areaMaxZ() == helper.absolutePos(MARK_Z).getZ()
              && quarry.areaMinX() == helper.absolutePos(MARK_CORNER).getX()
              && quarry.areaMaxX() == helper.absolutePos(MARK_X).getX();
          helper.setBlock(otherCorner, Blocks.AIR.defaultBlockState());
          helper.setBlock(otherX, Blocks.AIR.defaultBlockState());
          helper.setBlock(otherZ, Blocks.AIR.defaultBlockState());
          helper.assertTrue(own, "quarry adopted a rectangle mixed with the neighbouring landmark set");
        })
        .thenSucceed();
  }

  private static String ringReport(GameTestHelper helper) {
    StringBuilder sb = new StringBuilder();
    for (int y = 2; y <= 6; y++) {
      for (int x = 1; x <= 10; x++) {
        for (int z = 1; z <= 10; z++) {
          if (x != 1 && x != 10 && z != 1 && z != 10) {
            continue;
          }
          BlockState state = helper.getBlockState(new BlockPos(x, y, z));
          if (!state.isAir() && !state.is(QuarryRegistry.QUARRY_FRAME)) {
            sb.append(' ').append(x).append(',').append(y).append(',').append(z).append('=')
                .append(state.getBlock().getName().getString());
          }
        }
      }
    }
    return sb.toString();
  }

  private static BlockPlaceContext placeContext(GameTestHelper helper, Player player, BlockPos target) {
    BlockPos floor = target.below();
    BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, floor, false);
    return new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
        new ItemStack(QuarryRegistry.QUARRY), hit);
  }

  private static final class MessagePlayer extends Player {

    final List<Component> messages = new ArrayList<>();

    MessagePlayer(Level level, BlockPos pos) {
      super(level, pos, 0.0F, new GameProfile(UUID.randomUUID(), "faktocraft-placer"));
    }

    @Override
    public void displayClientMessage(Component message, boolean overlay) {
      messages.add(message);
    }

    @Override
    public boolean isSpectator() {
      return false;
    }

    @Override
    public boolean isCreative() {
      return false;
    }
  }
}
