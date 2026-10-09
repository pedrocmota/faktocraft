package com.faktocraft.common.item.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.item.base.MaterialItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

public class Briquette extends MaterialItem {
  public static final ResourceKey<ContextIntProvider> BURN_TIME = ResourceKey.create(Registries.CONTEXT_INT_PROVIDER,
      Identifier.fromNamespaceAndPath(Faktocraft.MODID, "cooking/time_briquette"));

  public Briquette(Properties properties) {
    super(properties.cookingFuel(BURN_TIME));
  }
}
