package com.faktocraft.common.block.impl.quarry;

import com.faktocraft.common.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntityQuarry extends BlockEntityGantry {

  public static final int STAGE_MINE = STAGE_WORK;
  public static final int STATUS_MINING = STATUS_WORKING;

  private static final ItemStack MINING_TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

  private long mineIndex = 0;

  public BlockEntityQuarry(BlockPos pos, BlockState state) {
    super(QuarryRegistry.QUARRY_BLOCK_ENTITY, pos, state, ModConfig.server().quarry_energy_capacity);
  }

  @Override
  protected int configEnergyPerAction() {
    return ModConfig.server().quarry_energy_per_block;
  }

  @Override
  protected int configMaxDrawPerTick() {
    return ModConfig.server().quarry_max_draw_per_tick;
  }

  @Override
  protected float baseSpeedMultiplier() {
    return 2.0F;
  }

  @Override
  protected void onWorkStageStarted() {
    mineIndex = 0;
  }

  @Override
  protected int tickWorkStage(ServerLevel serverLevel) {
    int width = maxX - minX - 1;
    int depth = maxZ - minZ - 1;
    long perLayer = (long) width * depth;
    int topY = worldPosition.getY() + frameHeightAbove();
    int bottomY = serverLevel.getMinY();
    long total = perLayer * (topY - bottomY + 1);

    if (!targetValid) {
      int budget = SCAN_BUDGET;
      while (mineIndex < total && budget-- > 0) {
        long layer = mineIndex / perLayer;
        int inLayer = (int) (mineIndex % perLayer);
        BlockPos pos = new BlockPos(minX + 1 + inLayer % width, topY - (int) layer,
            minZ + 1 + inLayer / width);
        if (!serverLevel.isLoaded(pos)) {
          return STATUS_WAITING_CHUNKS;
        }
        BlockState state = serverLevel.getBlockState(pos);

        if (isMineable(serverLevel, pos, state)
            || state.canBeReplaced() && !state.isAir() && state.getFluidState().isEmpty()) {
          setTarget(pos);
          break;
        }
        mineIndex++;
      }
      if (!targetValid) {
        if (mineIndex >= total) {
          stage = STAGE_DONE;
          setChanged();
          return STATUS_DONE;
        }
        return STATUS_MINING;
      }
    }

    BlockPos pos = targetPos();
    if (!serverLevel.isLoaded(pos)) {
      return STATUS_WAITING_CHUNKS;
    }
    BlockState state = serverLevel.getBlockState(pos);
    if (!isMineable(serverLevel, pos, state) && !(state.canBeReplaced() && !state.isAir())) {
      clearTarget();
      mineIndex++;
      return STATUS_MINING;
    }

    int cost = breakCost(serverLevel, pos, state);
    int charge = chargeProgress(cost);
    if (charge == CHARGE_READY && headAtTarget()) {
      if (!dwellComplete()) {
        return STATUS_MINING;
      }
      return breakAt(serverLevel, pos, state, STATUS_MINING, () -> {
        clearTarget();
        mineIndex++;
      }, MINING_TOOL);
    }
    dwellTicks = 0;
    return charge == CHARGE_STARVED ? STATUS_NO_ENERGY : STATUS_MINING;
  }

  @Override
  protected void saveWork(CompoundTag tag) {
    tag.putLong("mineIndex", mineIndex);
  }

  @Override
  protected void loadWork(CompoundTag tag) {
    mineIndex = tag.getLongOr("mineIndex", 0L);
  }
}
