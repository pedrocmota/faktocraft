package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.util.TransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BlockChassis extends VoxelBlock implements EntityBlock, IHasMenu {

  private final int tier;

  public BlockChassis(int tier, Properties properties) {
    super(properties, 0.25f);
    this.tier = tier;
  }

  public int getTier() {
    return tier;
  }

  public int moduleSlots() {
    return switch (tier) {
      case 1 -> 2;
      default -> 3;
    };
  }

  public int upgradeSlots() {
    return tier;
  }

  static java.util.List<Direction> inventoryDirections(LevelReader level, BlockPos pos) {
    java.util.List<Direction> result = new java.util.ArrayList<>();
    for (Direction direction : Direction.values()) {
      BlockPos relative = pos.relative(direction);
      BlockState state = level.getBlockState(relative);
      if (state.getBlock() instanceof BlockAssemblyTable) {
        result.add(direction);
        continue;
      }
      if (LogisticsGraph.isNetworkMember(state)) {
        continue;
      }
      if (level instanceof Level realLevel
          && TransferUtil.findItemHandler(realLevel, relative, direction.getOpposite()) != null) {
        result.add(direction);
      }
    }
    return result;
  }

  @Nullable
  static Direction selectedInventoryDirection(LevelReader level, BlockPos pos) {
    if (level.getBlockEntity(pos) instanceof BlockEntityChassis chassis) {
      return chassis.selectedInventoryDirection();
    }
    if (level.getBlockEntity(pos) instanceof BlockEntityDockingPipe pipe) {
      return pipe.selectedInventoryDirection();
    }

    boolean craftPipe = level.getBlockState(pos).getBlock() instanceof BlockCraftPipe;
    for (Direction candidate : inventoryDirections(level, pos)) {
      boolean assembly = level.getBlockState(pos.relative(candidate)).getBlock() instanceof BlockAssemblyTable;
      if (level.getBlockState(pos).getBlock() instanceof BlockDockingPipe && assembly != craftPipe) {
        continue;
      }
      return candidate;
    }
    return null;
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
    return direction == selectedInventoryDirection(level, pos);
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
  public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
      net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
    if (!player.getMainHandItem().is(com.faktocraft.common.registries.ModTags.WRENCHES)) {
      return net.minecraft.world.InteractionResult.PASS;
    }
    return super.use(state, level, pos, player, hand, hit);
  }

  @Override
  public AbstractContainerMenu getMenu(int windowId, Level level, BlockPos pos, Inventory playerInventory,
      Player player) {
    return new MenuChassis(windowId, level, pos, playerInventory, player);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityChassis(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (level.isClientSide()) {
      return null;
    }
    return (tickLevel, pos, tickState, blockEntity) -> {
      if (blockEntity instanceof BlockEntityChassis chassis) {
        chassis.tickServer();
      }
    };
  }

  @Override
  public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    super.onPlace(state, level, pos, oldState, isMoving);
    if (!level.isClientSide()) {
      LogisticsCores.markDirtyNear(level, pos);
    }
  }

  @Override
  public void preRemoveSideEffects(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity) {
    if (blockEntity instanceof BlockEntityChassis chassis) {
      chassis.dropContents();
    }
    super.preRemoveSideEffects(state, level, pos, blockEntity);
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
      boolean movedByPiston) {
    LogisticsCores.markDirtyNear(level, pos);
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }
}
