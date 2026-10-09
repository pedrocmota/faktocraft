package com.faktocraft.common.util;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

public final class InteractionResults {

  private InteractionResults() {
  }

  public static InteractionResult.Success sided(Level level) {
    return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
  }

  public static InteractionResult.Success serverSwing(Level level) {
    return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
  }
}
