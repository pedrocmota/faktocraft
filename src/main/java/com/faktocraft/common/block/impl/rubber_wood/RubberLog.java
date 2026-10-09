package com.faktocraft.common.block.impl.rubber_wood;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.block.FaktocraftBlock;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.interfaces.block.IStateAxis;
import com.faktocraft.common.interfaces.block.IStateRubberLog;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.ModTags;
import com.faktocraft.common.util.BlockStateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class RubberLog extends FaktocraftBlock implements IStateRubberLog, IStateAxis {

  public RubberLog(Properties properties) {
    super(properties);
  }

  public static BlockBehaviour.Properties logProperties() {
    return BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BROWN)
        .strength(2.0F, 3.0F)
        .sound(SoundType.WOOD)
        .ignitedByLava()
        .randomTicks();
  }

  @Override
  public BlockState rotate(BlockState state, Rotation rotation) {
    return switch (rotation) {
      case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(BlockStateHelper.axisProperty)) {
        case X -> state.setValue(BlockStateHelper.axisProperty, Direction.Axis.Z);
        case Z -> state.setValue(BlockStateHelper.axisProperty, Direction.Axis.X);
        default -> state;
      };
      default -> state;
    };
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
      BlockHitResult hitResult) {
    ItemStack itemStack = player.getItemInHand(hand);
    if (itemStack.is(ModTags.TREETAPS)) {
      return dropRubber(player, itemStack, hand, state, level, pos, hitResult);
    }
    return super.use(state, level, pos, player, hand, hitResult);
  }

  private InteractionResult dropRubber(Player player, ItemStack itemStack, InteractionHand hand, BlockState state,
      Level level, BlockPos pos, BlockHitResult trace) {
    if (level.isClientSide()) {
      return InteractionResult.PASS;
    }
    if (!isWet(state) && !isDry(state)) {
      return InteractionResult.PASS;
    }
    RandomSource random = level.getRandom();

    if (itemStack.getItem() instanceof IElectricItem electricItem) {
      IEnergy energy = electricItem.getEnergy(itemStack);
      if (energy == null || energy.energyStored() == 0) {
        return InteractionResult.PASS;
      }
      energy.consumeEnergy(50, false);
    } else {
      itemStack.hurtAndBreak(1, player, hand);
    }

    int dropCount = 0;
    SoundEvent sound = null;
    float pitch = 0.8F / (random.nextFloat() * 0.4F + 0.8F);
    if (isWet(state)) {
      sound = ModSounds.TREETAP;
      dropCount = random.nextInt(3) + 1;
      BlockPos dropPos = pos.relative(trace.getDirection());
      Containers.dropItemStack(level, dropPos.getX(), dropPos.getY(), dropPos.getZ(),
          new ItemStack(ModItems.STICKY_RESIN, dropCount));

      state = this.setWet(state, false);
      state = this.setDry(state, true);
      level.setBlock(pos, state, 2);
    } else if (isDry(state)) {

      sound = SoundEvents.WOOD_BREAK;
      pitch = 0.6F + random.nextFloat() * 0.2F;
      dropCount = random.nextInt(2);
      if (dropCount > 0) {
        BlockPos dropPos = pos.relative(trace.getDirection());
        Containers.dropItemStack(level, dropPos.getX(), dropPos.getY(), dropPos.getZ(),
            new ItemStack(ModItems.STICKY_RESIN, dropCount));
      }
      state = this.setDry(state, false);
      level.setBlock(pos, state, 2);
    }

    if (sound != null) {
      level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS, 0.5F,
          pitch);
      return InteractionResults.serverSwing(level);
    }

    return InteractionResult.PASS;
  }

  public static java.util.List<ItemStack> tapDrops(RandomSource random, BlockState state) {
    if (!(state.getBlock() instanceof RubberLog log) || !log.isWet(state)) {
      return java.util.List.of();
    }
    return java.util.List.of(new ItemStack(ModItems.STICKY_RESIN, random.nextInt(3) + 1));
  }

  public static BlockState tapped(BlockState state) {
    if (!(state.getBlock() instanceof RubberLog log)) {
      return state;
    }
    return log.setDry(log.setWet(state, false), true);
  }

  @Override
  public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    if (!isWet(state) || random.nextInt(5) != 0) {
      return;
    }

    int pick = Math.abs((int) RandomSource.create(state.getSeed(pos)).nextLong()) % 4;
    double dx = 0.56D;
    double dz = -0.11D;
    for (int i = 0; i < pick; i++) {
      double t = dx;
      dx = -dz;
      dz = t;
    }
    level.addParticle(ParticleTypes.DRIPPING_HONEY,
        pos.getX() + 0.5D + dx, pos.getY() + 0.4D, pos.getZ() + 0.5D + dz, 0.0D, 0.0D, 0.0D);
  }

  @Override
  public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (isDry(state)) {
      if (random.nextInt(6) == 0) {
        state = this.setWet(state, true);
        state = this.setDry(state, false);
        level.setBlock(pos, state, 2);
      }
    }
  }
}
