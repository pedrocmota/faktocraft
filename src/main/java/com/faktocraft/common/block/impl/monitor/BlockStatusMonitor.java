package com.faktocraft.common.block.impl.monitor;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.util.PlayerMessages;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.common.block.FaktocraftBlock;
import com.faktocraft.common.interfaces.block.IStateFacing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BlockStatusMonitor extends FaktocraftBlock implements EntityBlock, IStateFacing {

  public static final int WIDTH = 3;
  public static final int HEIGHT = 2;
  public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, WIDTH - 1);
  public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, HEIGHT - 1);

  private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 14, 16, 16, 16);
  private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 2);
  private static final VoxelShape SHAPE_WEST = Block.box(14, 0, 0, 16, 16, 16);
  private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 0, 2, 16, 16);

  public BlockStatusMonitor(Properties properties) {
    super(properties);
    registerDefaultState(defaultBlockState().setValue(PART_X, 0).setValue(PART_Y, 0));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(PART_X, PART_Y);
  }

  public static Direction facingOf(BlockState state) {
    return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
  }

  public static Direction rightOf(Direction facing) {
    return facing.getCounterClockWise();
  }

  public static boolean isMaster(BlockState state) {
    return state.getValue(PART_X) == 0 && state.getValue(PART_Y) == 0;
  }

  public static BlockPos masterPos(BlockState state, BlockPos pos) {
    return pos.relative(rightOf(facingOf(state)), -state.getValue(PART_X)).below(state.getValue(PART_Y));
  }

  public static BlockPos partPos(BlockPos master, Direction facing, int x, int y) {
    return master.relative(rightOf(facing), x).above(y);
  }

  public static boolean canPlacePanel(Level level, BlockPos master, Direction facing,
      @Nullable BlockPos alreadyChecked) {
    for (int x = 0; x < WIDTH; x++) {
      for (int y = 0; y < HEIGHT; y++) {
        BlockPos pos = partPos(master, facing, x, y);
        if (pos.equals(alreadyChecked)) {
          continue;
        }
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced()) {
          return false;
        }
      }
    }
    return true;
  }

  public void placePanel(Level level, BlockPos master, Direction facing) {
    BlockState base = defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    for (int x = 0; x < WIDTH; x++) {
      for (int y = 0; y < HEIGHT; y++) {
        level.setBlock(partPos(master, facing, x, y), base.setValue(PART_X, x).setValue(PART_Y, y),
            Block.UPDATE_ALL);
      }
    }
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return switch (facingOf(state)) {
      case SOUTH -> SHAPE_SOUTH;
      case WEST -> SHAPE_WEST;
      case EAST -> SHAPE_EAST;
      default -> SHAPE_NORTH;
    };
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public boolean propagatesSkylightDown(BlockState state) {
    return true;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return isMaster(state) ? new BlockEntityStatusMonitor(pos, state) : null;
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (level.isClientSide() || !isMaster(state)) {
      return null;
    }
    return (tickLevel, pos, tickState, blockEntity) -> {
      if (blockEntity instanceof BlockEntityStatusMonitor monitor) {
        monitor.serverTick();
      }
    };
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide() && player.isCreative() && !isMaster(state)) {
      BlockPos master = masterPos(state, pos);
      if (level.getBlockState(master).is(this)) {
        level.setBlock(master, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
      }
    }
    return super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
      boolean movedByPiston) {
    BlockPos master = masterPos(state, pos);
    Direction facing = facingOf(state);
    if (!isMaster(state) && level.getBlockState(master).is(this)) {
      level.destroyBlock(master, true);
    }
    for (int x = 0; x < WIDTH; x++) {
      for (int y = 0; y < HEIGHT; y++) {
        BlockPos part = partPos(master, facing, x, y);
        if (!part.equals(pos) && level.getBlockState(part).is(this)) {
          level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
      }
    }
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
      BlockHitResult hitResult) {
    if (player.getItemInHand(hand).getItem() instanceof MonitorCardItem) {
      return InteractionResult.PASS;
    }
    if (!level.isClientSide()
        && level.getBlockEntity(masterPos(state, pos)) instanceof BlockEntityStatusMonitor monitor) {
      PlayerMessages.display(player, monitor.describeTarget(), true);
    }
    return InteractionResults.sided(level);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("tooltip.faktocraft.status_monitor").withStyle(ChatFormatting.GRAY));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }
}
