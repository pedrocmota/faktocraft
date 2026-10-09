package com.faktocraft.client;

import com.faktocraft.Faktocraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Faktocraft.MODID, value = Dist.CLIENT)
public final class MachineVolumeSlider {

  private MachineVolumeSlider() {
  }

  @SubscribeEvent
  public static void onScreenInit(ScreenEvent.Init.Post event) {
    if (!(event.getScreen() instanceof SoundOptionsScreen screen)) {
      return;
    }
    OptionsList list = null;
    for (GuiEventListener child : screen.children()) {
      if (child instanceof OptionsList options) {
        list = options;
      }
    }
    if (list == null) {
      return;
    }
    double initial = ClientSoundConfig.machineVolume();
    list.addSmall(new AbstractSliderButton(0, 0, 150, 20, machineVolumeLabel(initial), initial) {
      @Override
      protected void updateMessage() {
        setMessage(machineVolumeLabel(this.value));
      }

      @Override
      protected void applyValue() {
        ClientSoundConfig.setMachineVolume((float) this.value);
      }
    }, null);
  }

  private static Component machineVolumeLabel(double value) {
    String pct = value <= 0 ? I18n.get("options.off") : (int) (value * 100) + "%";
    return Component.translatable("options." + Faktocraft.MODID + ".machine_volume").append(": " + pct);
  }
}
