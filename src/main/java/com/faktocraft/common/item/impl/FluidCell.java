package com.faktocraft.common.item.impl;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.item.base.FluidItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class FluidCell extends FluidItem {
  private static final int SOURCE_BLOCK_MB = 1000;

  public FluidCell(Properties properties) {
    super(properties);
  }

  @Override
  public int getFluidCapacity() {
    return getCapacity();
  }

  public static int getCapacity() {
    return ModConfig.server().fluid_cell_capacity;
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    Fluid contained = getFluid(stack);

    if (contained == Fluids.EMPTY) {
      return pickupFluid(level, player, stack);
    }
    return placeFluid(level, player, stack, contained);
  }

  private InteractionResult pickupFluid(Level level, Player player, ItemStack stack) {
    BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
    if (hit.getType() != HitResult.Type.BLOCK) {
      return InteractionResult.PASS;
    }

    BlockPos pos = hit.getBlockPos();
    if (!level.mayInteract(player, pos)
        || !player.mayUseItemAt(pos.relative(hit.getDirection()), hit.getDirection(), stack)) {
      return InteractionResult.FAIL;
    }

    FluidState fluidState = level.getFluidState(pos);
    if (!fluidState.isEmpty() && fluidState.isSource() && getCapacity() >= SOURCE_BLOCK_MB) {
      Fluid fluid = fluidState.getType();

      BlockState blockState = level.getBlockState(pos);
      if (blockState.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
        if (!level.isClientSide()) {
          level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        }
      } else if (blockState.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup) {
        if (pickup.pickupBlock(player, level, pos, blockState).isEmpty()) {
          return InteractionResult.FAIL;
        }
      } else {
        return InteractionResult.FAIL;
      }

      player.awardStat(Stats.ITEM_USED.get(this));
      player.playSound(fluid.isSame(Fluids.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL, 1F, 1F);

      ItemStack filled = new ItemStack(this);
      setFluid(filled, fluid, SOURCE_BLOCK_MB);

      ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
      return InteractionResults.sided(level).heldItemTransformedTo(result);
    }

    return InteractionResult.FAIL;
  }

  private InteractionResult placeFluid(Level level, Player player, ItemStack stack, Fluid fluid) {
    int amount = getFluidAmount(stack);
    if (amount < SOURCE_BLOCK_MB) {
      return InteractionResult.FAIL;
    }

    BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
    if (hit.getType() != HitResult.Type.BLOCK) {
      return InteractionResult.PASS;
    }

    BlockPos pos = hit.getBlockPos();
    if (!level.mayInteract(player, pos)
        || !player.mayUseItemAt(pos.relative(hit.getDirection()), hit.getDirection(), stack)) {
      return InteractionResult.FAIL;
    }

    if (emptyFluid(player, level, pos, fluid, hit)) {
      player.awardStat(Stats.ITEM_USED.get(this));

      ItemStack emptied = stack.copyWithCount(1);
      int remaining = amount - SOURCE_BLOCK_MB;
      setFluid(emptied, remaining > 0 ? fluid : Fluids.EMPTY, Math.max(0, remaining));
      ItemStack result = player.getAbilities().instabuild ? stack
          : ItemUtils.createFilledResult(stack, player, emptied);

      return InteractionResults.sided(level).heldItemTransformedTo(result);
    }

    return InteractionResult.FAIL;
  }

  @SuppressWarnings("deprecation")
  private static boolean emptyFluid(@Nullable LivingEntity user, Level level, BlockPos pos, Fluid fluid,
      @Nullable BlockHitResult hitResult) {
    if (!(fluid instanceof FlowingFluid flowingFluid)) {
      return false;
    }

    BlockState blockState = level.getBlockState(pos);
    Block block = blockState.getBlock();
    boolean mayReplace = blockState.canBeReplaced(fluid);
    boolean shiftKeyDown = user != null && user.isShiftKeyDown();
    boolean placeLiquid = mayReplace || block instanceof LiquidBlockContainer container
        && container.canPlaceLiquid(user, level, pos, blockState, fluid);
    boolean canPlaceFluidInsideBlock = blockState.isAir() || placeLiquid && (!shiftKeyDown || hitResult == null);

    if (!canPlaceFluidInsideBlock) {
      return hitResult != null
          && emptyFluid(user, level, hitResult.getBlockPos().relative(hitResult.getDirection()), fluid, null);
    }

    if ((level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)
        && fluid.defaultFluidState().is(FluidTags.WATER))
        || fluid.getFluidType().isVaporizedOnPlacement(level, pos,
            new net.neoforged.neoforge.fluids.FluidStack(fluid, SOURCE_BLOCK_MB))) {
      RandomSource random = level.getRandom();
      level.playSound(user, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
          2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F);
      for (int i = 0; i < 8; i++) {
        level.addParticle(ParticleTypes.LARGE_SMOKE,
            pos.getX() + random.nextFloat(), pos.getY() + random.nextFloat(), pos.getZ() + random.nextFloat(), 0.0, 0.0,
            0.0);
      }
      return true;
    }

    if (block instanceof LiquidBlockContainer container && fluid == Fluids.WATER) {
      container.placeLiquid(level, pos, blockState, flowingFluid.getSource(false));
      playEmptySound(user, level, pos, fluid);
      return true;
    }

    if (!level.isClientSide() && mayReplace && !blockState.liquid()) {
      level.destroyBlock(pos, true);
    }

    if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11)
        && !blockState.getFluidState().isSource()) {
      return false;
    }

    playEmptySound(user, level, pos, fluid);
    return true;
  }

  private static void playEmptySound(@Nullable LivingEntity user, Level level, BlockPos pos, Fluid fluid) {
    SoundEvent soundEvent = fluid.defaultFluidState().is(FluidTags.LAVA)
        ? SoundEvents.BUCKET_EMPTY_LAVA
        : SoundEvents.BUCKET_EMPTY;
    level.playSound(user, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
    level.gameEvent(user, GameEvent.FLUID_PLACE, pos);
  }
}
