package com.faktocraft.common.item.impl.bronze;

import com.faktocraft.common.item.base.BaseItem;
import com.faktocraft.common.registries.ModTiers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public class BronzeSword extends BaseItem {

  public BronzeSword(Item.Properties properties) {
    super(properties.sword(ModTiers.BRONZE, 3, -2.4F));
  }

  @Override
  public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
    return ability == ItemAbilities.SWORD_SWEEP || super.canPerformAction(stack, ability);
  }
}
