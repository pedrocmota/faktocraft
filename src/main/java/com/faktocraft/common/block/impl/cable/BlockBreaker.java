package com.faktocraft.common.block.impl.cable;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.energy.provider.EnergyCore;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.tier.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

public class BlockBreaker extends BlockCable {

  @Override
  public boolean coverable() {
    return false;
  }

  public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
  public static final BooleanProperty ON = BlockStateProperties.ENABLED;
  public static final EnumProperty<Direction> HANDLE = EnumProperty.create("handle", Direction.class);
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty DIAL =
      net.minecraft.world.level.block.state.properties.IntegerProperty
          .create("dial", 0, 3);

  public BlockBreaker(Properties properties) {
    super(0.5F, CableTier.COPPER_CABLE, properties);
    registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(ON, true)
        .setValue(HANDLE, Direction.UP).setValue(DIAL, 0));
    com.faktocraft.common.util.wrench.WrenchHelper.registerAction(this)
        .add((level, pos, state, player, clickedFace) -> {
          if (level.isClientSide()) {
            return false;
          }
          int dial = state.getValue(DIAL);
          if (dial < 3) {
            level.setBlockAndUpdate(pos, state.setValue(DIAL, dial + 1));
            return true;
          }
          state = state.setValue(DIAL, 0);
          Direction.Axis axis = state.getValue(AXIS);
          Direction handle = state.getValue(HANDLE);
          java.util.List<Direction.Axis> axes = java.util.List.of(Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z);
          int axisIndex = axes.indexOf(axis);
          for (int a = 0; a < axes.size(); a++) {
            Direction.Axis candidateAxis = axes.get((axisIndex + a) % axes.size());
            java.util.List<Direction> ring = handleRing(candidateAxis);
            int start = a == 0 ? ring.indexOf(handle) + 1 : 0;
            int count = a == 0 ? ring.size() - 1 : ring.size();
            for (int i = 0; i < count; i++) {
              Direction candidate = ring.get((start + i) % ring.size());
              if (!handleFree(level, pos, candidate)) {
                continue;
              }
              BlockState rotated = state.setValue(AXIS, candidateAxis).setValue(HANDLE, candidate);
              if (candidateAxis != axis) {
                EnergyCore.get(level).getNetworks().onRemove(pos);
              }
              com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, handle);
              level.setBlockAndUpdate(pos, rotated);
              com.faktocraft.common.block.impl.BlockHandleGuard.place(level, pos, candidate);
              if (candidateAxis != axis) {
                tryJoinNetwork(level, pos, rotated);
              }
              return true;
            }
          }
          return false;
        });
  }

  @Override
  protected boolean waterloggable() {
    return false;
  }

  public static java.util.List<Direction> handleRing(Direction.Axis axis) {
    return switch (axis) {
      case X -> java.util.List.of(Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH);
      case Z -> java.util.List.of(Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST);
      default -> java.util.List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    };
  }

  public static boolean handleFree(LevelAccessor level, BlockPos pos, Direction face) {
    BlockPos relative = pos.relative(face);
    return level.getBlockState(relative).getCollisionShape(level, relative).isEmpty();
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(AXIS, ON, HANDLE, DIAL);
  }

  private static Direction.Axis bridgeAxis(net.minecraft.world.level.LevelReader level, BlockPos pos,
      Direction.Axis fallback) {
    Direction.Axis best = fallback;
    int bestScore = 0;
    java.util.List<Direction.Axis> ordered = new java.util.ArrayList<>();
    ordered.add(fallback);
    for (Direction.Axis axis : Direction.Axis.values()) {
      if (axis != fallback) {
        ordered.add(axis);
      }
    }
    for (Direction.Axis axis : ordered) {
      int score = 0;
      for (Direction direction : Direction.values()) {
        if (direction.getAxis() != axis) {
          continue;
        }
        BlockPos relative = pos.relative(direction);
        BlockState relativeState = level.getBlockState(relative);
        if (relativeState.getBlock() instanceof BlockCable) {
          score += 2;
        } else if (level instanceof Level realLevel
            && com.faktocraft.common.energy.EnergyLookup.isPresent(realLevel, relative, direction.getOpposite())) {
          score += 1;
        }
      }
      if (score > bestScore) {
        bestScore = score;
        best = axis;
      }
    }
    return best;
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = super.getStateForPlacement(context);
    if (state == null) {
      return null;
    }
    Direction.Axis axis = bridgeAxis(context.getLevel(), context.getClickedPos(),
        context.getHorizontalDirection().getClockWise().getAxis());
    Direction handle = Direction.UP;
    for (Direction candidate : handleRing(axis)) {
      if (handleFree(context.getLevel(), context.getClickedPos(), candidate)) {
        handle = candidate;
        break;
      }
    }
    return state.setValue(AXIS, axis).setValue(ON, true).setValue(HANDLE, handle);
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return Shapes.block();
  }

  @Override
  protected boolean canConnect(LevelReader level, BlockPos pos, Direction direction) {
    if (level.getBlockState(pos).getBlock() instanceof BlockBreaker
        && level.getBlockState(pos).getValue(AXIS) != direction.getAxis()) {
      return false;
    }
    return super.canConnect(level, pos, direction);
  }

  @Nullable
  public static EnergyTier adoptTier(Level level, BlockPos pos, BlockState state) {
    for (Direction direction : Direction.values()) {
      if (direction.getAxis() != state.getValue(AXIS)) {
        continue;
      }
      BlockPos relative = pos.relative(direction);
      BlockState relativeState = level.getBlockState(relative);
      if (relativeState.getBlock() instanceof BlockBreaker) {
        if (level.getBlockEntity(relative) instanceof BlockEntityBreaker neighborBe
            && neighborBe.getAdoptedTier() != null) {
          return neighborBe.getAdoptedTier();
        }
      } else if (relativeState.getBlock() instanceof BlockCable cable) {
        return cable.getCableTier().getEnergyTier();
      }
    }
    return null;
  }

  private void tryJoinNetwork(Level level, BlockPos pos, BlockState state) {
    if (!state.getValue(ON)) {
      return;
    }
    EnergyTier tier = adoptTier(level, pos, state);
    if (tier != null && level.getBlockEntity(pos) instanceof BlockEntityBreaker be) {
      be.setAdoptedTier(tier);
      EnergyCore.get(level).getNetworks().onPlaced(pos, state, tier);
    }
  }

  public void switchTo(Level level, BlockPos pos, BlockState state, boolean turnOn) {
    if (state.getValue(ON) == turnOn) {
      return;
    }
    BlockState toggled = state.setValue(ON, turnOn);
    level.setBlock(pos, toggled, 3);
    com.faktocraft.common.block.impl.BlockHandleGuard.place(level, pos, state.getValue(HANDLE));
    var networks = EnergyCore.get(level).getNetworks();
    if (turnOn) {
      tryJoinNetwork(level, pos, toggled);
    } else {
      networks.onRemove(pos);

      if (level.getBlockEntity(pos) instanceof BlockEntityBreaker be) {
        be.setAdoptedTier(null);
      }
    }
    level.playSound(null, pos, com.faktocraft.common.registries.ModSounds.BREAKER_SWITCH,
        net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, turnOn ? 1.0F : 0.92F);
  }

  public void applyRedstoneState(Level level, BlockPos pos, BlockState state) {
    if (!(level.getBlockEntity(pos) instanceof BlockEntityBreaker be) || !be.isRedstoneOnly()) {
      return;
    }
    switchTo(level, pos, state, level.hasNeighborSignal(pos));
  }

  @Override
  public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
      ItemStack stack) {
    if (!level.isClientSide()) {
      tryJoinNetwork(level, pos, state);
      com.faktocraft.common.block.impl.BlockHandleGuard.place(level, pos, state.getValue(HANDLE));
    }
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
      boolean movedByPiston) {
    com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, state.getValue(HANDLE));
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
  }

  @Override
  public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
      @Nullable Orientation orientation, boolean movedByPiston) {
    super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
    if (!level.isClientSide()) {
      applyRedstoneState(level, pos, state);
      BlockState current = level.getBlockState(pos);
      if (current.getBlock() instanceof BlockBreaker && current.getValue(ON)) {
        healNetworkBridge(level, pos, current);
      }
    }
  }

  private void healNetworkBridge(Level level, BlockPos pos, BlockState state) {
    var networks = EnergyCore.get(level).getNetworks();
    var mine = networks.getNetwork(pos);
    boolean broken = mine == null;
    if (level.getBlockEntity(pos) instanceof BlockEntityBreaker be) {
      EnergyTier expected = adoptTier(level, pos, state);
      if (expected != null && expected != be.getAdoptedTier()) {
        be.setAdoptedTier(expected);
        broken = true;
      }
      if (!broken && be.getAdoptedTier() != null) {
        for (Direction direction : Direction.values()) {
          if (direction.getAxis() != state.getValue(AXIS)) {
            continue;
          }
          BlockPos relative = pos.relative(direction);
          BlockState relativeState = level.getBlockState(relative);
          if (relativeState.getBlock() instanceof BlockCable cable
              && !(relativeState.getBlock() instanceof BlockBreaker)
              && cable.getCableTier().getEnergyTier() == be.getAdoptedTier()) {
            var other = networks.getNetwork(relative);
            if (other != null && other != mine) {
              broken = true;
              break;
            }
          }
        }
      }
    }
    if (broken) {
      if (mine != null) {
        networks.onRemove(pos);
      }
      tryJoinNetwork(level, pos, state);
    }
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
      BlockHitResult hit) {
    if (player.getItemInHand(hand).is(com.faktocraft.common.registries.ModTags.WRENCHES)) {
      if (level.isClientSide() && level.getBlockEntity(pos) instanceof BlockEntityBreaker be) {
        final boolean current = be.isRedstoneOnly();
        com.faktocraft.common.util.ClientProxy.get().openRedstoneControlScreen(pos, current);
      }
      return InteractionResults.sided(level);
    }
    if (!level.isClientSide()) {
      if (level.getBlockEntity(pos) instanceof BlockEntityBreaker be && be.isRedstoneOnly()) {
        be.setRedstoneOnly(false);
      }
      switchTo(level, pos, state, !state.getValue(ON));
    }
    return InteractionResults.sided(level);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityBreaker(pos, state);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component
        .translatable("tooltip.faktocraft.circuit_breaker")
        .withStyle(net.minecraft.ChatFormatting.GRAY));
    tooltip.accept(Component
        .translatable("tooltip.faktocraft.circuit_breaker_usage")
        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
  }
}
