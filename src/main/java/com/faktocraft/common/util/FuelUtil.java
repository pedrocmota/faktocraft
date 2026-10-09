package com.faktocraft.common.util;

import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;

public final class FuelUtil {
  private FuelUtil() {
  }

  public static int burnTime(@Nullable Level level, ItemStack stack, @Nullable BlockEntity blockEntity) {
    if (stack.isEmpty()) {
      return 0;
    }
    CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
    if (fuel == null) {
      return 0;
    }
    if (fuel.burnTime() instanceof ResolvableInt.Constant constant) {
      return constant.value();
    }
    if (!(level instanceof ServerLevel serverLevel)) {
      return 1;
    }
    BlockPos pos = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
    LootParams.Builder params = new LootParams.Builder(serverLevel)
        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        .withOptionalParameter(net.neoforged.neoforge.common.loot.NeoForgeLootContextParams.QUERIED_STACK, stack);
    if (blockEntity != null) {
      params.withParameter(LootContextParams.BLOCK_STATE, blockEntity.getBlockState())
          .withParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
          .withParameter(LootContextParams.CONTAINER, containerOf(blockEntity));
    }
    LootContext context;
    try {
      context = new LootContext.Builder(params.create(LootContextParamSets.CONTAINER_PROCESS)).create(Optional.empty());
    } catch (RuntimeException e) {
      return 0;
    }
    return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
  }

  private static Container containerOf(BlockEntity blockEntity) {
    if (blockEntity instanceof Container container) {
      return container;
    }
    if (blockEntity instanceof FaktocraftBlockEntity machine && machine.getItemStackHandler() != null) {
      return machine.getItemStackHandler();
    }
    return new SimpleContainer(0);
  }

  public static boolean isFuel(@Nullable Level level, ItemStack stack, @Nullable BlockEntity blockEntity) {
    return burnTime(level, stack, blockEntity) > 0;
  }
}
