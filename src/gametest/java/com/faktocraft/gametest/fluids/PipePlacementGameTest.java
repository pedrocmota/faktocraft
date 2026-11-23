package com.faktocraft.gametest.fluids;

import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.MockPlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

public class PipePlacementGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos A = new BlockPos(1, 1, 1);
  private static final BlockPos B = new BlockPos(2, 1, 1);
  private static final BlockPos PLAYER = new BlockPos(2, 1, 3);
  private static final int SETTLE_TICKS = 40;

  private static Map<BlockPos, BlockState> sentStates(MockPlayers.Mock mock) {
    Map<BlockPos, BlockState> out = new HashMap<>();
    for (Object message : mock.drainOutbound()) {
      if (message instanceof ClientboundBlockUpdatePacket packet) {
        out.put(packet.getPos(), packet.getBlockState());
      } else if (message instanceof ClientboundSectionBlocksUpdatePacket packet) {
        packet.runUpdates((pos, state) -> out.put(pos.immutable(), state));
      }
    }
    return out;
  }

  private static boolean connected(BlockState state, Direction direction) {
    return state.hasProperty(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))
        && state.getValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction));
  }

  private static MockPlayers.Mock player(GameTestHelper helper) {
    BlockPos playerAbs = helper.absolutePos(PLAYER);
    return MockPlayers.survival(helper, playerAbs.getX() + 0.5, playerAbs.getY(), playerAbs.getZ() + 0.5);
  }

  private static void placeAgainstA(GameTestHelper helper, MockPlayers.Mock mock) {
    mock.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PipeRegistry.FLUID_STONE_PIPE));
    BlockPos absA = helper.absolutePos(A);
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absA).add(0.5, 0, 0), Direction.EAST, absA, false);
    mock.player().gameMode.useItemOn(mock.player(), helper.getLevel(), mock.player().getMainHandItem(),
        InteractionHand.MAIN_HAND, hit);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void playerPlacedPipeConnectsAndSyncs(GameTestHelper helper) {
    helper.setBlock(A, PipeRegistry.FLUID_STONE_PIPE.defaultBlockState());
    MockPlayers.Mock mock = player(helper);
    BlockPos absA = helper.absolutePos(A);
    BlockPos absB = helper.absolutePos(B);
    helper.runAfterDelay(SETTLE_TICKS, () -> {
      mock.drainOutbound();
      placeAgainstA(helper, mock);
    });
    helper.runAfterDelay(SETTLE_TICKS + 20, () -> {
      Map<BlockPos, BlockState> sent = sentStates(mock);
      BlockState levelA = helper.getLevel().getBlockState(absA);
      BlockState levelB = helper.getLevel().getBlockState(absB);
      mock.remove();
      if (!connected(levelA, Direction.EAST) || !connected(levelB, Direction.WEST)) {
        helper.fail("pipes placed by a player did not connect: A=" + levelA + " B=" + levelB);
        return;
      }
      BlockState sentA = sent.get(absA);
      BlockState sentB = sent.get(absB);
      if (sentA == null || sentB == null || !connected(sentA, Direction.EAST) || !connected(sentB, Direction.WEST)) {
        helper.fail("client was not sent connected states: A=" + sentA + " B=" + sentB);
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void playerBreaksPlacedPipe(GameTestHelper helper) {
    helper.setBlock(A, PipeRegistry.FLUID_STONE_PIPE.defaultBlockState());
    MockPlayers.Mock mock = player(helper);
    BlockPos absB = helper.absolutePos(B);
    helper.runAfterDelay(SETTLE_TICKS, () -> placeAgainstA(helper, mock));
    helper.runAfterDelay(SETTLE_TICKS + 20, () -> {
      mock.drainOutbound();
      mock.player().gameMode.destroyBlock(absB);
    });
    helper.runAfterDelay(SETTLE_TICKS + 40, () -> {
      Map<BlockPos, BlockState> sent = sentStates(mock);
      BlockState levelB = helper.getLevel().getBlockState(absB);
      mock.remove();
      if (!levelB.isAir()) {
        helper.fail("pipe survived destroyBlock: " + levelB);
        return;
      }
      if (sent.get(absB) == null || !sent.get(absB).isAir()) {
        helper.fail("client was not sent the air state after the break: " + sent.get(absB));
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void laterChangesStillReachTheClientAfterPipeNextToTank(GameTestHelper helper) {
    helper.setBlock(A, PipeRegistry.TANK.defaultBlockState());
    MockPlayers.Mock mock = player(helper);
    BlockPos absB = helper.absolutePos(B);
    BlockPos stoneRel = B.above();
    BlockPos absStone = helper.absolutePos(stoneRel);
    helper.runAfterDelay(SETTLE_TICKS, () -> placeAgainstA(helper, mock));
    helper.runAfterDelay(SETTLE_TICKS + 20, () -> {
      mock.drainOutbound();
      helper.setBlock(stoneRel, Blocks.STONE.defaultBlockState());
    });
    helper.runAfterDelay(SETTLE_TICKS + 40, () -> {
      Map<BlockPos, BlockState> sent = sentStates(mock);
      BlockState levelB = helper.getLevel().getBlockState(absB);
      mock.remove();
      if (!levelB.is(PipeRegistry.FLUID_STONE_PIPE)) {
        helper.fail("pipe was not placed next to the tank: " + levelB);
        return;
      }
      BlockState stone = sent.get(absStone);
      if (stone == null || !stone.is(Blocks.STONE)) {
        helper.fail("block change in the same section was never broadcast to the client: " + stone);
        return;
      }
      helper.succeed();
    });
  }
}
