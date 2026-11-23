package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.registries.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public abstract class BlockDockingPipe extends VoxelBlock implements EntityBlock, IHasMenu {

  protected BlockDockingPipe(Properties properties) {
    super(properties, 0.25f);
  }

  public boolean connects(LevelReader level, BlockPos pos, Direction direction) {
    return canConnect(level, pos, direction);
  }

  @Override
  protected boolean canConnect(LevelReader level, BlockPos pos, Direction direction) {
    BlockState state = level.getBlockState(pos.relative(direction));
    if (LogisticsGraph.isNetworkMember(state) && !(state.getBlock() instanceof BlockAssemblyTable)) {
      return true;
    }
    return direction == BlockChassis.selectedInventoryDirection(level, pos);
  }

  @Override
  protected BlockState computeShapeUpdate(BlockState state, LevelReader level, ScheduledTickAccess ticks,
      BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    state = super.computeShapeUpdate(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    return withConnections(state, level, pos);
  }

  @Override
  protected boolean connectionExtensions() {
    return true;
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
      BlockHitResult hit) {
    if (!player.getMainHandItem().is(ModTags.WRENCHES)) {
      return InteractionResult.PASS;
    }
    return super.use(state, level, pos, player, hand, hit);
  }

  @Override
  public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    super.onPlace(state, level, pos, oldState, isMoving);
    if (!level.isClientSide()) {
      LogisticsCores.markDirtyNear(level, pos);
    }
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
      boolean movedByPiston) {
    LogisticsCores.markDirtyNear(level, pos);
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }
}
