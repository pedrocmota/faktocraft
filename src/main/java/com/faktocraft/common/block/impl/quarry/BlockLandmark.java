package com.faktocraft.common.block.impl.quarry;

import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.common.block.IBlockHoverText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BlockLandmark extends Block implements EntityBlock, IBlockHoverText {

  public static final int MAX_SPAN = BlockEntityQuarry.MAX_FRAME_SPAN;
  public static final int MIN_SPAN = BlockEntityQuarry.MIN_FRAME_SPAN;

  private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 10, 10);

  public BlockLandmark(Properties properties) {
    super(properties);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityLandmark(pos, state);
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @Override
  public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
  }

  @Override
  public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
      Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
      return Blocks.AIR.defaultBlockState();
    }
    return state;
  }

  @Override
  protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
      InteractionHand hand, BlockHitResult hitResult) {
    if (level.isClientSide()) {
      return InteractionResult.SUCCESS;
    }
    BlockPos alongX = partnerOnAxis(level, pos, Direction.EAST, Direction.WEST);
    BlockPos alongZ = partnerOnAxis(level, pos, Direction.SOUTH, Direction.NORTH);
    if (alongX != null && alongZ != null) {
      int width = Math.abs(alongX.getX() - pos.getX()) + 1;
      int depth = Math.abs(alongZ.getZ() - pos.getZ()) + 1;
      player.sendSystemMessage(Component.translatable("chat.faktocraft.landmark_area", width, depth)
          .withStyle(ChatFormatting.GREEN));
    } else if (alongX != null || alongZ != null) {
      player.sendSystemMessage(Component.translatable("chat.faktocraft.landmark_partial")
          .withStyle(ChatFormatting.YELLOW));
    } else {
      player.sendSystemMessage(Component.translatable("chat.faktocraft.landmark_alone")
          .withStyle(ChatFormatting.GRAY));
    }
    return InteractionResult.SUCCESS;
  }

  @Nullable
  public static BlockPos findPartner(BlockGetter level, BlockPos from, Direction direction) {
    BlockPos.MutableBlockPos cursor = from.mutable();
    for (int i = 1; i <= MAX_SPAN - 1; i++) {
      cursor.move(direction);
      if (level.getBlockState(cursor).is(QuarryRegistry.LANDMARK)) {
        return i >= MIN_SPAN - 1 ? cursor.immutable() : null;
      }
    }
    return null;
  }

  @Nullable
  public static int[] rectAround(BlockGetter level, BlockPos pos) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (dx == 0 && dz == 0) {
          continue;
        }
        BlockPos neighbor = pos.offset(dx, 0, dz);
        int[] rect = rectThrough(level, neighbor);
        if (rect != null) {
          return rect;
        }
      }
    }
    return null;
  }

  public static boolean touchesLandmarks(BlockGetter level, BlockPos pos) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        if (dx == 0 && dz == 0) {
          continue;
        }
        BlockPos neighbor = pos.offset(dx, 0, dz);
        if (level.getBlockState(neighbor).is(QuarryRegistry.LANDMARK)) {
          return true;
        }
        for (Direction.Axis axis : new Direction.Axis[] { Direction.Axis.X, Direction.Axis.Z }) {
          Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
          if (nearestLandmark(level, neighbor, negative) != null
              && nearestLandmark(level, neighbor, negative.getOpposite()) != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private static int[] rectThrough(BlockGetter level, BlockPos spot) {
    if (level.getBlockState(spot).is(QuarryRegistry.LANDMARK)) {
      return resolveRect(level, spot);
    }
    for (Direction.Axis axis : new Direction.Axis[] { Direction.Axis.X, Direction.Axis.Z }) {
      Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
      BlockPos before = nearestLandmark(level, spot, negative);
      BlockPos after = nearestLandmark(level, spot, negative.getOpposite());
      if (before == null || after == null || !after.equals(findPartner(level, before, negative.getOpposite()))) {
        continue;
      }
      for (BlockPos start : new BlockPos[] { before, after }) {
        int[] rect = resolveRect(level, start);
        if (rect != null && onPerimeter(rect, spot)) {
          return rect;
        }
      }
    }
    return null;
  }

  private static boolean onPerimeter(int[] rect, BlockPos spot) {
    int x = spot.getX();
    int z = spot.getZ();
    if (x < rect[0] || x > rect[1] || z < rect[2] || z > rect[3]) {
      return false;
    }
    return x == rect[0] || x == rect[1] || z == rect[2] || z == rect[3];
  }

  @Nullable
  private static BlockPos nearestLandmark(BlockGetter level, BlockPos from, Direction direction) {
    BlockPos.MutableBlockPos cursor = from.mutable();
    for (int i = 1; i <= MAX_SPAN - 1; i++) {
      cursor.move(direction);
      if (level.getBlockState(cursor).is(QuarryRegistry.LANDMARK)) {
        return cursor.immutable();
      }
    }
    return null;
  }

  @Nullable
  public static int[] resolveRect(BlockGetter level, BlockPos start) {
    BlockPos alongX = partnerOnAxis(level, start, Direction.EAST, Direction.WEST);
    BlockPos alongZ = partnerOnAxis(level, start, Direction.SOUTH, Direction.NORTH);
    BlockPos corner = start;
    if (alongX != null && alongZ == null) {
      corner = alongX;
      alongX = start;
      alongZ = partnerOnAxis(level, corner, Direction.SOUTH, Direction.NORTH);
    } else if (alongZ != null && alongX == null) {
      corner = alongZ;
      alongZ = start;
      alongX = partnerOnAxis(level, corner, Direction.EAST, Direction.WEST);
    }
    if (alongX == null || alongZ == null) {
      return null;
    }
    return new int[] {
        Math.min(corner.getX(), alongX.getX()), Math.max(corner.getX(), alongX.getX()),
        Math.min(corner.getZ(), alongZ.getZ()), Math.max(corner.getZ(), alongZ.getZ()) };
  }

  public static boolean rectFits(int[] rect, BlockPos quarryPos) {
    int width = rect[1] - rect[0] + 1;
    int depth = rect[3] - rect[2] + 1;
    boolean inside = quarryPos.getX() >= rect[0] && quarryPos.getX() <= rect[1]
        && quarryPos.getZ() >= rect[2] && quarryPos.getZ() <= rect[3];
    return width >= BlockEntityQuarry.MIN_FRAME_SPAN && depth >= BlockEntityQuarry.MIN_FRAME_SPAN
        && width <= BlockEntityQuarry.MAX_FRAME_SPAN && depth <= BlockEntityQuarry.MAX_FRAME_SPAN && !inside;
  }

  @Nullable
  private static BlockPos partnerOnAxis(BlockGetter level, BlockPos from, Direction first, Direction second) {
    BlockPos a = findPartner(level, from, first);
    BlockPos b = findPartner(level, from, second);
    if (a == null || b == null) {
      return a != null ? a : b;
    }
    return from.distManhattan(a) <= from.distManhattan(b) ? a : b;
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("tooltip.faktocraft.landmark").withStyle(ChatFormatting.GRAY));
  }

  @Override
  public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
    return true;
  }
}
