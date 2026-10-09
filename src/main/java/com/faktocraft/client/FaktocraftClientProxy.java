package com.faktocraft.client;

import com.faktocraft.client.render.FluidFogVolume;
import com.faktocraft.client.render.StatusClientBridges;
import com.faktocraft.common.util.ClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

public final class FaktocraftClientProxy implements ClientProxy {
  @Override
  public HolderLookup.Provider clientThreadRegistries() {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft == null || !minecraft.isSameThread()) {
      return null;
    }
    ClientPacketListener connection = minecraft.getConnection();
    return connection == null ? null : connection.registryAccess();
  }

  @Override
  public void startTileSound(SoundEvent sound, float volume, BlockPos pos) {
    SoundHandler.startTileSound(sound, volume, pos);
  }

  @Override
  public void stopTileSound(BlockPos pos) {
    SoundHandler.stopTileSound(pos);
  }

  @Override
  public void ensureExtraSoundPlaying(SoundEvent sound, BlockPos pos) {
    ExtraSoundHandler.ensurePlaying(sound, pos);
  }

  @Override
  public void stopExtraSound(BlockPos pos) {
    ExtraSoundHandler.stop(pos);
  }

  @Override
  public boolean isFirstPerson() {
    return ClientCamera.isFirstPerson();
  }

  @Override
  public void openRedstoneControlScreen(BlockPos pos, boolean current) {
    Minecraft.getInstance().gui.setScreen(new RedstoneControlScreen(pos, current));
  }

  @Override
  public void openProspectorScreen() {
    ProspectorClient.openScreen();
  }

  @Override
  public void registerFluidFog(FluidType type, float red, float green, float blue, float fogEnd) {
    FluidFogVolume.register(type, red, green, blue, fogEnd);
  }

  @Override
  public boolean hasStatusBridges() {
    return !StatusClientBridges.isEmpty();
  }

  @Override
  @Nullable
  public Object buildStatusBridge(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
      CompoundTag data) {
    return StatusClientBridges.build(level, pos, state, blockEntity, data);
  }

  @Override
  public void displayOverlay(Player player, Component message) {
    Minecraft.getInstance().gui.hud.setOverlayMessage(message, false);
  }
}
