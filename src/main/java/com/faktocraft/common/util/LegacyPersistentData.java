package com.faktocraft.common.util;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public final class LegacyPersistentData {
  public static final String LEGACY_KEY = "ForgeData";
  private static final String PREFIX = "faktocraft";

  private LegacyPersistentData() {
  }

  public static void merge(Entity entity, CompoundTag legacy) {
    CompoundTag data = entity.getPersistentData();
    for (String key : legacy.keySet()) {
      if (key.toLowerCase(Locale.ROOT).startsWith(PREFIX) && !data.contains(key)) {
        data.put(key, legacy.get(key).copy());
      }
    }
  }
}
