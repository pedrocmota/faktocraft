package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.registries.PipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class PipeValveHelper {

  private PipeValveHelper() {
  }

  public static boolean isStraightPipe(BlockState state) {
    return runAxis(state) != null;
  }

  @Nullable
  public static net.minecraft.core.Direction.Axis runAxis(BlockState state) {
    net.minecraft.core.Direction first = null;
    int count = 0;
    for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
      var property = com.faktocraft.common.block.VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction);
      if (state.hasProperty(property) && state.getValue(property)) {
        count++;
        if (first == null) {
          first = direction;
        } else if (direction != first.getOpposite()) {
          return null;
        }
      }
    }
    return count == 2 && first != null ? first.getAxis() : null;
  }

  public static void validateOrEject(Level level, BlockPos pos, BlockState state, IValveHolder holder) {
    PipeValve valve = holder.getValve();
    if (!valve.isPresent() || isStraightPipe(state)) {
      return;
    }
    com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, valve.direction());
    holder.setValve(PipeValve.NONE);
    Block.popResource(level, pos, new ItemStack(com.faktocraft.common.registries.ModItems.PIPE_VALVE));
    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_BREAK,
        net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.0F);
  }

  public static boolean acceptsValve(Block block) {
    return block == PipeRegistry.FLUID_STONE_PIPE || block == PipeRegistry.FLUID_GOLD_PIPE;
  }

  @Nullable
  public static InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
      InteractionHand hand, BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof IValveHolder holder)) {
      return null;
    }
    PipeValve valve = holder.getValve();
    ItemStack held = player.getItemInHand(hand);

    var runAxis = runAxis(state);
    if (!valve.isPresent() && held.is(com.faktocraft.common.registries.ModItems.PIPE_VALVE)
        && acceptsValve(state.getBlock())
        && runAxis != null && hit.getDirection().getAxis() != runAxis
        && level.getBlockState(pos.relative(hit.getDirection()))
            .getCollisionShape(level, pos.relative(hit.getDirection())).isEmpty()) {
      if (!level.isClientSide()) {
        holder.setValve(PipeValve.of(hit.getDirection(), true));
        com.faktocraft.common.block.impl.BlockHandleGuard.place(level, pos, hit.getDirection());
        if (!player.getAbilities().instabuild) {
          held.shrink(1);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_PLACE,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
      }
      return InteractionResults.sided(level);
    }

    if (valve.isPresent() && !player.isCrouching()) {
      if (!level.isClientSide()) {
        if (holder.isValveRedstoneOnly()) {
          holder.setValveRedstoneOnly(false);
        }
        boolean open = !valve.isOpen();
        holder.setValve(PipeValve.of(valve.direction(), open));
        com.faktocraft.common.block.impl.BlockHandleGuard.place(level, pos, valve.direction());
        level.playSound(null, pos, com.faktocraft.common.registries.ModSounds.VALVE_WHEEL,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.45F, open ? 1.0F : 0.94F);
      }
      return InteractionResults.sided(level);
    }
    if (valve.isPresent() && player.isCrouching() && held.isEmpty()) {
      if (!level.isClientSide()) {
        com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, valve.direction());
        holder.setValve(PipeValve.NONE);
        Block.popResource(level, pos, new ItemStack(com.faktocraft.common.registries.ModItems.PIPE_VALVE));
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_BREAK,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
      }
      return InteractionResults.sided(level);
    }
    return null;
  }

  public static boolean breakValveFirst(Level level, BlockPos pos) {
    if (!(level.getBlockEntity(pos) instanceof IValveHolder holder) || !holder.getValve().isPresent()) {
      return false;
    }
    if (!level.isClientSide()) {
      com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, holder.getValve().direction());
      holder.setValve(PipeValve.NONE);
      Block.popResource(level, pos, new ItemStack(com.faktocraft.common.registries.ModItems.PIPE_VALVE));
      level.playSound(null, pos, net.minecraft.sounds.SoundEvents.METAL_BREAK,
          net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.1F);
    }
    return true;
  }

  public static void dropValve(Level level, BlockPos pos, BlockState state) {
    if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IValveHolder holder
        && holder.getValve().isPresent()) {
      com.faktocraft.common.block.impl.BlockHandleGuard.remove(level, pos, holder.getValve().direction());
      Block.popResource(level, pos, new ItemStack(com.faktocraft.common.registries.ModItems.PIPE_VALVE));
    }
  }
}
