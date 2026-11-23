package com.faktocraft.common.block.impl.generators.solar_panels;

import com.faktocraft.common.block.ModelFlags;
import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.block.impl.cable.BlockCable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class SolarPanelFlags {

  public static final String KEY = "panelFlags";
  public static final int CORNER_NE = 1 << 16;
  public static final int CORNER_SE = 1 << 17;
  public static final int CORNER_SW = 1 << 18;
  public static final int CORNER_NW = 1 << 19;
  private static final Direction[] CABLE_SIDES = {
      Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN };
  private static final Direction[] PANEL_SIDES = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
  private static final int[][] CORNERS = {
      { CORNER_NE, Direction.NORTH.get3DDataValue(), Direction.EAST.get3DDataValue() },
      { CORNER_SE, Direction.SOUTH.get3DDataValue(), Direction.EAST.get3DDataValue() },
      { CORNER_SW, Direction.SOUTH.get3DDataValue(), Direction.WEST.get3DDataValue() },
      { CORNER_NW, Direction.NORTH.get3DDataValue(), Direction.WEST.get3DDataValue() } };
  public static final int ALL = all();

  private SolarPanelFlags() {
  }

  private static int all() {
    int mask = CORNER_NE | CORNER_SE | CORNER_SW | CORNER_NW;
    for (Direction direction : CABLE_SIDES) {
      mask |= cableBit(direction);
    }
    for (Direction direction : PANEL_SIDES) {
      mask |= panelBit(direction);
    }
    return mask;
  }

  public static int cableBit(Direction direction) {
    return 1 << direction.get3DDataValue();
  }

  public static int panelBit(Direction direction) {
    return 1 << (8 + direction.get3DDataValue());
  }

  public static boolean isCableSide(Direction direction) {
    return direction != Direction.UP;
  }

  public static boolean isPanelSide(Direction direction) {
    return direction.getAxis().isHorizontal();
  }

  public static ModelFlags create() {
    return new ModelFlags(KEY, ALL);
  }

  public static boolean cableConnectsFrom(BlockState neighborState, Direction direction) {
    if (!(neighborState.getBlock() instanceof BlockCable)) {
      return false;
    }
    BooleanProperty toPanel = VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction.getOpposite());
    return neighborState.hasProperty(toPanel) && neighborState.getValue(toPanel);
  }

  private static boolean isPanel(LevelReader level, BlockPos pos) {
    return level.getBlockState(pos).getBlock() instanceof BlockSolarGenerator;
  }

  private static boolean innerCorner(ModelFlags flags, LevelReader level, BlockPos pos, Direction a, Direction b) {
    return flags.has(panelBit(a)) && flags.has(panelBit(b)) && !isPanel(level, pos.relative(a).relative(b));
  }

  private static void updateCorners(BlockEntity blockEntity, ModelFlags flags, LevelReader level, BlockPos pos) {
    for (int[] corner : CORNERS) {
      Direction a = Direction.from3DDataValue(corner[1]);
      Direction b = Direction.from3DDataValue(corner[2]);
      flags.set(blockEntity, corner[0], innerCorner(flags, level, pos, a, b));
    }
  }

  public static void onShapeUpdate(BlockEntity blockEntity, ModelFlags flags, LevelReader level, BlockPos pos,
      Direction direction, BlockState neighborState) {
    if (isCableSide(direction)) {
      flags.set(blockEntity, cableBit(direction), cableConnectsFrom(neighborState, direction));
    }
    if (isPanelSide(direction)) {
      flags.set(blockEntity, panelBit(direction), neighborState.getBlock() instanceof BlockSolarGenerator);
      updateCorners(blockEntity, flags, level, pos);
    }
  }

  public static int resolve(BlockEntity blockEntity, ModelFlags flags, Level level, BlockPos pos, int pending) {
    int remaining = pending;
    for (Direction direction : CABLE_SIDES) {
      int bit = cableBit(direction);
      BlockPos neighbor = pos.relative(direction);
      if (ModelFlags.has(pending, bit) && ModelFlags.readable(level, neighbor)) {
        flags.set(blockEntity, bit, cableConnectsFrom(level.getBlockState(neighbor), direction));
        remaining &= ~bit;
      }
    }
    for (Direction direction : PANEL_SIDES) {
      int bit = panelBit(direction);
      BlockPos neighbor = pos.relative(direction);
      if (ModelFlags.has(pending, bit) && ModelFlags.readable(level, neighbor)) {
        flags.set(blockEntity, bit, isPanel(level, neighbor));
        remaining &= ~bit;
      }
    }
    for (int[] corner : CORNERS) {
      Direction a = Direction.from3DDataValue(corner[1]);
      Direction b = Direction.from3DDataValue(corner[2]);
      boolean sidesKnown = !ModelFlags.has(remaining, panelBit(a)) && !ModelFlags.has(remaining, panelBit(b));
      if (ModelFlags.has(pending, corner[0]) && sidesKnown
          && ModelFlags.readable(level, pos.relative(a).relative(b))) {
        flags.set(blockEntity, corner[0], innerCorner(flags, level, pos, a, b));
        remaining &= ~corner[0];
      }
    }
    return remaining;
  }
}
