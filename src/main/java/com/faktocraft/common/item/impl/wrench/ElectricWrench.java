package com.faktocraft.common.item.impl.wrench;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.item.base.ElectricItem;
import com.faktocraft.common.util.wrench.WrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class ElectricWrench extends ElectricItem {

  public ElectricWrench(Properties properties, int energyStored, int maxEnergy, EnergyType energyType,
      EnergyTier energyTier) {
    super(properties, energyStored, maxEnergy, energyType, energyTier);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    return WrenchHelper.onWrenchUse(level.getBlockState(pos), level, pos, context.getPlayer(), context.getHand(),
        context.getClickedFace())
            ? InteractionResults.serverSwing(level)
            : InteractionResult.PASS;
  }

  @Override
  public float getDestroySpeed(net.minecraft.world.item.ItemStack stack,
      net.minecraft.world.level.block.state.BlockState state) {
    return WrenchHelper.hasAction(state.getBlock()) ? 20.0F : super.getDestroySpeed(stack, state);
  }

  @Override
  public boolean isCorrectToolForDrops(net.minecraft.world.item.ItemStack stack,
      net.minecraft.world.level.block.state.BlockState state) {
    return WrenchHelper.hasAction(state.getBlock()) || super.isCorrectToolForDrops(stack, state);
  }
}
