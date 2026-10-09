package com.faktocraft.common.item.impl.bronze;

import com.faktocraft.common.item.base.BaseItem;
import com.faktocraft.common.registries.ModTiers;
import com.faktocraft.common.util.InteractionResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public class BronzeShovel extends BaseItem {

  public BronzeShovel(Item.Properties properties) {
    super(properties.shovel(ModTiers.BRONZE, 1.5F, -3.0F));
  }

  @Override
  public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
    return ability == ItemAbilities.SHOVEL_DOUSE || super.canPerformAction(stack, ability);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    BlockState state = level.getBlockState(pos);
    if (context.getClickedFace() == Direction.DOWN || !(state.getBlock() instanceof CampfireBlock)
        || !state.getValue(CampfireBlock.LIT)) {
      return super.useOn(context);
    }
    Player player = context.getPlayer();
    if (!level.isClientSide()) {
      level.levelEvent(null, 1009, pos, 0);
    }
    CampfireBlock.douse(player, level, pos, state);
    BlockState doused = state.setValue(CampfireBlock.LIT, false);
    if (!level.isClientSide()) {
      level.setBlock(pos, doused, 11);
      level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, doused));
      if (player != null) {
        context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
      }
    }
    return InteractionResults.sided(level);
  }
}
