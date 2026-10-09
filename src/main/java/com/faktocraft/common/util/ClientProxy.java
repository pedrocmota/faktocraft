package com.faktocraft.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

public interface ClientProxy {
  ClientProxy NOOP = new ClientProxy() {
  };

  final class Holder {
    private static ClientProxy instance = NOOP;

    private Holder() {
    }
  }

  static ClientProxy get() {
    return Holder.instance;
  }

  static void set(ClientProxy proxy) {
    Holder.instance = proxy;
  }

  @Nullable
  default HolderLookup.Provider clientThreadRegistries() {
    return null;
  }

  default void startTileSound(SoundEvent sound, float volume, BlockPos pos) {
  }

  default void stopTileSound(BlockPos pos) {
  }

  default void ensureExtraSoundPlaying(SoundEvent sound, BlockPos pos) {
  }

  default void stopExtraSound(BlockPos pos) {
  }

  default boolean isFirstPerson() {
    return false;
  }

  default void openRedstoneControlScreen(BlockPos pos, boolean current) {
  }

  default void openProspectorScreen() {
  }

  default void registerFluidFog(FluidType type, float red, float green, float blue, float fogEnd) {
  }

  default boolean hasStatusBridges() {
    return false;
  }

  @Nullable
  default Object buildStatusBridge(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
      CompoundTag data) {
    return null;
  }

  default void displayOverlay(Player player, Component message) {
  }
}
