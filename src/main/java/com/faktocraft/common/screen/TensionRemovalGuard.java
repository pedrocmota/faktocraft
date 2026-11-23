package com.faktocraft.common.screen;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.container.TensionRemoval;
import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.enums.EnergyTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public final class TensionRemovalGuard {

  private static final String KEY = "gui." + Faktocraft.MODID + ".tension_removal.";

  private TensionRemovalGuard() {
  }

  public static boolean confirm(AbstractContainerScreen<?> screen, @Nullable Slot slot, int button,
      ContainerInput type, @Nullable BasicEnergyStorage storage, Runnable proceed) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || !TensionRemoval.energized(storage)) {
      return false;
    }
    AbstractContainerMenu menu = screen.getMenu();
    TensionRemoval.Change change = TensionRemoval.change(menu, slot, minecraft.player.getInventory(), button, type);
    if (change == null) {
      return false;
    }
    Component from = tierName(TensionRemoval.inputTier(storage, change.from()));
    Component to = tierName(TensionRemoval.inputTier(storage, change.to()));
    minecraft.gui.setScreen(new TensionRemovalScreen(confirmed -> {
      boolean open = minecraft.player != null && minecraft.player.containerMenu == menu;
      minecraft.gui.setScreen(open ? screen : null);
      if (open && confirmed) {
        proceed.run();
      }
    }, Component.translatable(KEY + "title"), Component.translatable(KEY + "message", from, to),
        Component.translatable(KEY + "remove"), CommonComponents.GUI_CANCEL));
    return true;
  }

  private static Component tierName(EnergyTier tier) {
    return tier.getLang().getTranslationComponent().withStyle(tier.getColor());
  }
}
