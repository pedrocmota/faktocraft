package com.faktocraft.common.item.impl.wrench;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.item.base.ToolItem;
import com.faktocraft.common.util.wrench.WrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class Wrench extends ToolItem {

  public Wrench(Properties properties, int maxDamage) {
    super(properties, maxDamage);
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
