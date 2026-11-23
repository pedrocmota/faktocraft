package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import com.faktocraft.common.screen.TensionRemovalGuard;

public class ScreenPump extends AbstractContainerScreen<MenuPump>
    implements com.faktocraft.common.interfaces.screen.IGuiWrapper {

  @Override
  public com.faktocraft.common.entity.block.FaktocraftBlockEntity getBlockEntity() {
    return menu.getPump();
  }

  @Override
  protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
    com.faktocraft.common.entity.block.FaktocraftBlockEntity blockEntity = getBlockEntity();
    if (TensionRemovalGuard.confirm(this, slot, mouseButton, type,
        blockEntity == null ? null : blockEntity.getEnergyStorage(),
        () -> super.slotClicked(slot, slotId, mouseButton, type))) {
      return;
    }
    super.slotClicked(slot, slotId, mouseButton, type);
  }

  @Override
  public ResourceLocation getGuiLocation() {
    return BACKGROUND;
  }

  private static final ResourceLocation BACKGROUND = new ResourceLocation(Faktocraft.MODID,
      "textures/gui/container/pipe_machine.png");

  private com.faktocraft.common.screen.bar.GuiElectricBarVertical energyBar;
  private com.faktocraft.common.screen.bar.GuiFluidBarVertical fluidBar;
  private final net.minecraft.client.gui.components.Button[] runModeButtons = new net.minecraft.client.gui.components.Button[3];

  public ScreenPump(MenuPump menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    this.imageWidth = 176;
    this.imageHeight = 190;
    this.inventoryLabelY = 190 - 94;
  }

  @Override
  protected void init() {
    super.init();
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    if (menu.getPump() != null) {
      energyBar = addRenderableWidget(new com.faktocraft.common.screen.bar.GuiElectricBarVertical(
          this, 151, 42, menu.getPump().getEnergyStorage(), menu.getPump()));
      fluidBar = addRenderableWidget(new com.faktocraft.common.screen.bar.GuiFluidBarVertical(
          this, 128, 42, menu.getPump().tank));
    }
    for (int i = 0; i < 3; i++) {
      final int id = i;
      runModeButtons[i] = addRenderableWidget(net.minecraft.client.gui.components.Button
          .builder(Component.translatable("motor." + Faktocraft.MODID + ".run." + i), b -> press(id))
          .bounds(left + 8, top + 43 + i * 17, 112, 16).build());
    }
  }

  private void press(int id) {
    if (this.minecraft != null && this.minecraft.gameMode != null) {
      this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    for (int i = 0; i < 3; i++) {
      if (runModeButtons[i] != null) {
        runModeButtons[i].active = menu.getRunMode() != i;
      }
    }
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
    renderEmptySlotTooltip(graphics, mouseX, mouseY);
    if (energyBar != null) {
      energyBar.renderWidgetToolTip(this, graphics, mouseX, mouseY);
    }
    if (fluidBar != null) {
      fluidBar.renderWidgetToolTip(this, graphics, mouseX, mouseY);
    }
  }

  private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= MenuPump.MACHINE_SLOTS) {
      return;
    }
    String key = hoveredSlot.index < 2
        ? "gui." + Faktocraft.MODID + ".slot.capacitor"
        : hoveredSlot.index == 2
            ? "gui." + Faktocraft.MODID + ".slot.tension"
            : hoveredSlot.index < 6
                ? "gui." + Faktocraft.MODID + ".slot.overclock"
                : "gui." + Faktocraft.MODID + ".slot.dock_battery";
    graphics.renderTooltip(GuiUtil.getFont(), Component.translatable(key), mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    for (int i = 0; i < 2; i++) {
      graphics.blit(BACKGROUND, left + 7 + i * 18, top + 21, 7, 107, 18, 18, 256, 256);
      graphics.blit(Constants.PROCESS, left + 8 + i * 18, top + 22, 110, 0, 16, 16, 256, 256);
    }
    graphics.blit(BACKGROUND, left + 43, top + 21, 7, 107, 18, 18, 256, 256);
    graphics.blit(Constants.PROCESS, left + 44, top + 22, 84, 46, 16, 16, 256, 256);
    for (int i = 0; i < 3; i++) {
      graphics.blit(Constants.PROCESS, left + 79 + i * 18, top + 21, 103, 46, 18, 18, 256, 256);
    }
    graphics.blit(BACKGROUND, left + 151, top + 21, 7, 107, 18, 18, 256, 256);
    graphics.blit(Constants.PROCESS, left + 152, top + 22, 104, 28, 16, 16, 256, 256);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    GuiUtil.renderScaledToFit(graphics, this.title.getString(), this.titleLabelX, this.titleLabelY,
        this.imageWidth - this.titleLabelX - 8, 4210752);
    graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        4210752, false);
  }
}
