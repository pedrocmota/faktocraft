package com.faktocraft.common.block;

import com.faktocraft.common.cover.CoverSupport;
import com.faktocraft.common.registries.ModBlocks;
import com.faktocraft.common.util.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.EnumMap;
import java.util.Map;

public class VoxelBlock extends FaktocraftBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

  public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

  public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
  public static final BooleanProperty EAST = BlockStateProperties.EAST;
  public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
  public static final BooleanProperty WEST = BlockStateProperties.WEST;
  public static final BooleanProperty UP = BlockStateProperties.UP;
  public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

  public static final Map<Direction, BooleanProperty> FACING_TO_PROPERTY_MAP = createFacingMap();

  private final VoxelShape[] shapes;

  private final float apothem;

  public VoxelBlock(Properties properties, float apothem) {
    super(properties);
    this.apothem = apothem;
    this.shapes = makeShapes(apothem);
    BlockState state = getStateDefinition().any()
        .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
        .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false)
        .setValue(WATERLOGGED, false);
    if (coverable()) {
      state = state.setValue(CoverSupport.COVERED, false);
    }
    registerDefaultState(state);
  }

  public float getApothem() {
    return apothem;
  }

  public boolean coverable() {
    return true;
  }

  @Override
  @SuppressWarnings("deprecation")
  public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
    return CoverSupport.isCovered(state) ? CoverSupport.HOLE_LIGHT_BLOCK : super.getLightBlock(state, level, pos);
  }

  @Override
  public void playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
      net.minecraft.world.entity.player.Player player) {
    if (CoverSupport.isCovered(state) && CoverSupport.coverAt(level, pos) != null) {
      spawnDestroyParticles(level, player, pos, state);
      level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos);
      return;
    }
    super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  public boolean onDestroyedByPlayer(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
      net.minecraft.world.entity.player.Player player, boolean willHarvest,
      net.minecraft.world.level.material.FluidState fluid) {
    if (CoverSupport.isCovered(state) && CoverSupport.coverAt(level, pos) != null) {
      if (level.isClientSide()) {
        return false;
      }
      if (willHarvest && !player.isCreative()) {
        dropResources(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
      }
      CoverSupport.releaseToDrilled(level, pos, state);
      return false;
    }
    return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
  }

  @Override
  public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState,
      boolean isMoving) {
    if (CoverSupport.isCovered(state) && !newState.is(this) && !newState.is(ModBlocks.DRILLED_BLOCK)) {
      CoverSupport.dropCover(level, pos, CoverSupport.coverAt(level, pos));
    }
    super.onRemove(state, level, pos, newState, isMoving);
  }

  protected boolean connectionExtensions() {
    return false;
  }

  public boolean hasConnectionExtensions() {
    return connectionExtensions();
  }

  private void updateExtension(LevelAccessor level, BlockPos pos, Direction direction, boolean connected) {
    if (level.getBlockEntity(pos) instanceof IConnectionExtensionHost host) {
      host.extensions().update((net.minecraft.world.level.block.entity.BlockEntity) host, direction,
          extendsInto(level, pos, direction, connected));
    }
  }

  protected BlockState finishShapeUpdate(LevelAccessor level, BlockPos pos, BlockState before, BlockState after) {
    if (connectionExtensions() && level.getBlockEntity(pos) instanceof IConnectionExtensionHost host
        && host.extensions().takeDirty() && after == before && !level.isClientSide()) {
      after.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT);
    }
    return after;
  }

  public boolean extendsInto(LevelAccessor level, BlockPos pos, Direction direction, boolean connected) {
    if (!connected) {
      return false;
    }
    BlockPos relative = pos.relative(direction);
    BlockState neighbor = level.getBlockState(relative);
    if (neighbor.getBlock() instanceof VoxelBlock) {
      return false;
    }
    return !neighbor.canOcclude() || !neighbor.isFaceSturdy(level, relative, direction.getOpposite());
  }

  private static Map<Direction, BooleanProperty> createFacingMap() {
    Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
    map.put(Direction.NORTH, NORTH);
    map.put(Direction.EAST, EAST);
    map.put(Direction.SOUTH, SOUTH);
    map.put(Direction.WEST, WEST);
    map.put(Direction.UP, UP);
    map.put(Direction.DOWN, DOWN);
    return map;
  }

  private VoxelShape[] makeShapes(float apothem) {
    float min = 8 - apothem * 16;
    float max = 8 + apothem * 16;
    VoxelShape core = Block.box(min, min, min, max, max, max);

    VoxelShape[] directionShapes = new VoxelShape[] {
        Block.box(min, 0, min, max, max, max),
        Block.box(min, min, min, max, 16, max),
        Block.box(min, min, 0, max, max, max),
        Block.box(min, min, min, max, max, 16),
        Block.box(0, min, min, max, max, max),
        Block.box(min, min, min, 16, max, max)
    };

    VoxelShape[] result = new VoxelShape[64];
    for (int i = 0; i < 64; i++) {
      VoxelShape shape = core;
      for (int d = 0; d < 6; d++) {
        if ((i & (1 << d)) != 0) {
          shape = Shapes.or(shape, directionShapes[d]);
        }
      }
      result[i] = shape;
    }
    return result;
  }

  protected int getShapeIndex(BlockState state) {
    int index = 0;
    for (int d = 0; d < Constants.DIRECTIONS.length; d++) {
      if (state.getValue(FACING_TO_PROPERTY_MAP.get(Constants.DIRECTIONS[d]))) {
        index |= 1 << d;
      }
    }
    return index;
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return CoverSupport.isCovered(state) ? Shapes.block() : shapes[getShapeIndex(state)];
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    if (coverable()) {
      builder.add(CoverSupport.COVERED);
    }
  }

  protected boolean canConnect(LevelAccessor level, BlockPos pos, Direction direction) {
    return false;
  }

  public BlockState withConnections(BlockState state, LevelAccessor level, BlockPos pos) {
    for (Direction direction : Constants.DIRECTIONS) {
      boolean connected = canConnect(level, pos, direction);
      state = state.setValue(FACING_TO_PROPERTY_MAP.get(direction), connected);
      if (connectionExtensions()) {
        updateExtension(level, pos, direction, connected);
      }
    }
    return state;
  }

  @Override
  public BlockState setStateForPlacement(BlockPlaceContext context, BlockState state) {
    state = state.setValue(WATERLOGGED,
        waterloggable() && hasWaterAbove(context.getLevel(), context.getClickedPos()));
    return withConnections(state, context.getLevel(), context.getClickedPos());
  }

  @Override
  public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
      BlockPos pos, BlockPos neighborPos) {
    return finishShapeUpdate(level, pos, state,
        computeShapeUpdate(state, direction, neighborState, level, pos, neighborPos));
  }

  protected BlockState computeShapeUpdate(BlockState state, Direction direction, BlockState neighborState,
      LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
    if (waterloggable()) {
      boolean above = hasWaterAbove(level, pos);
      if (!state.getValue(WATERLOGGED)) {
        if (above) {
          state = state.setValue(WATERLOGGED, true);
        }
      } else if (!above && !hasSideWater(level, pos)) {
        state = state.setValue(WATERLOGGED, false);
      }
    }
    if (state.getValue(WATERLOGGED)) {
      level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER,
          net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
    }
    boolean connected = canConnect(level, pos, direction);
    state = state.setValue(FACING_TO_PROPERTY_MAP.get(direction), connected);
    if (connectionExtensions()) {
      updateExtension(level, pos, direction, connected);
    }
    return state;
  }

  @Override
  public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
    return false;
  }

  private static boolean hasWaterAbove(LevelAccessor level, BlockPos pos) {
    return level.getBlockState(pos.above()).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
  }

  private static boolean hasSideWater(LevelAccessor level, BlockPos pos) {
    for (Direction direction : Constants.DIRECTIONS) {
      if (direction == Direction.UP) {
        continue;
      }
      BlockPos relative = pos.relative(direction);
      BlockState neighbor = level.getBlockState(relative);
      if (neighbor.getBlock() instanceof VoxelBlock) {
        continue;
      }
      if (neighbor.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
        return true;
      }
    }
    return false;
  }

  protected boolean waterloggable() {
    return true;
  }

  @Override
  public boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state,
      net.minecraft.world.level.material.Fluid fluid) {
    return waterloggable()
        && net.minecraft.world.level.block.SimpleWaterloggedBlock.super.canPlaceLiquid(level, pos, state, fluid);
  }

  @Override
  @SuppressWarnings("deprecation")
  public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
    return state.getValue(WATERLOGGED)
        ? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
        : super.getFluidState(state);
  }
}
