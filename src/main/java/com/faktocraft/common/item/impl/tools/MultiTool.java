package com.faktocraft.common.item.impl.tools;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.util.wrench.WrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class MultiTool extends ElectricHoe {

  public MultiTool(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties,
      int energyStored, int maxEnergy, int energyCostMine, int energyCostHurt, int energyCostTill,
      EnergyType energyType, EnergyTier energyTier) {
    super(material, attackDamage, attackSpeed, properties, energyStored, maxEnergy,
        energyCostMine, energyCostHurt, energyCostTill, energyType, energyTier);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    BlockState state = level.getBlockState(pos);
    if (!state.isAir() && WrenchHelper.hasAction(state.getBlock())) {
      return WrenchHelper.onWrenchUse(state, level, pos, context.getPlayer(), context.getHand(),
          context.getClickedFace())
              ? InteractionResults.serverSwing(level)
              : InteractionResult.PASS;
    }
    return super.useOn(context);
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
