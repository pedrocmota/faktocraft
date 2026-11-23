package com.faktocraft.common.block.impl.quarry;

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

public class ScreenQuarry extends AbstractContainerScreen<MenuQuarry>
    implements com.faktocraft.common.interfaces.screen.IGuiWrapper {

  private static final ResourceLocation BACKGROUND = new ResourceLocation(Faktocraft.MODID,
      "textures/gui/container/quarry.png");

  private static final String[] STATUS_KEYS = {
      "off", "invalid_area", "clearing", "framing", "mining", "obstructed", "full", "no_energy",
      "waiting_chunks", "done" };

  private com.faktocraft.common.screen.bar.GuiElectricBarVertical energyBar;
  private com.faktocraft.common.screen.widgets.GuiCapacitorWarning capacitorWarning;
  private final net.minecraft.client.gui.components.Button[] runModeButtons = new net.minecraft.client.gui.components.Button[3];

  public ScreenQuarry(MenuQuarry menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    this.imageWidth = 176;
    this.imageHeight = 223;
    this.inventoryLabelY = 223 - 94;
  }

  @Override
  public com.faktocraft.common.entity.block.FaktocraftBlockEntity getBlockEntity() {
    return menu.getQuarry();
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

  @Override
  protected void init() {
    super.init();
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    if (menu.getQuarry() != null) {
      capacitorWarning = addRenderableWidget(new com.faktocraft.common.screen.widgets.GuiCapacitorWarning(
          this, 151, 17, menu.getQuarry()));
      energyBar = addRenderableWidget(new com.faktocraft.common.screen.bar.GuiElectricBarVertical(
          this, 151, 17, menu.getQuarry().getEnergyStorage(), menu.getQuarry()));
    }
    for (int i = 0; i < 3; i++) {
      final int id = i;
      runModeButtons[i] = addRenderableWidget(net.minecraft.client.gui.components.Button
          .builder(Component.translatable("motor." + Faktocraft.MODID + ".run." + i), b -> press(id))
          .bounds(left + 8, top + 18 + i * 17, 112, 16).build());
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
    if (capacitorWarning != null) {
      capacitorWarning.renderWidgetToolTip(this, graphics, mouseX, mouseY);
    }
  }

  private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= MenuQuarry.DOCK_SLOTS) {
      return;
    }
    String key = hoveredSlot.index < 2
        ? "gui." + Faktocraft.MODID + ".slot.capacitor"
        : hoveredSlot.index == 2
            ? "gui." + Faktocraft.MODID + ".slot.tension"
            : hoveredSlot.index < 7
                ? "gui." + Faktocraft.MODID + ".extractor.upgrade"
                : "gui." + Faktocraft.MODID + ".slot.dock_battery";
    graphics.renderTooltip(GuiUtil.getFont(), Component.translatable(key), mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

    graphics.blit(Constants.COMMON, left - 23, top + 4, 0, 134, 24, 80, 256, 256);
    for (int i = 0; i < 4; i++) {
      graphics.blit(Constants.PROCESS, left - 21, top + 8 + i * 18, 103, 46, 18, 18, 256, 256);
    }
    graphics.blit(Constants.PROCESS, left - 20, top + 9, 110, 0, 16, 16, 256, 256);
    graphics.blit(Constants.PROCESS, left - 20, top + 27, 110, 0, 16, 16, 256, 256);
    graphics.blit(Constants.PROCESS, left - 20, top + 45, 84, 46, 16, 16, 256, 256);
    graphics.blit(Constants.PROCESS, left - 20, top + 63, 104, 28, 16, 16, 256, 256);
    graphics.blit(Constants.COMMON, left + 175, top + 4, 0, 134, 24, 80, 256, 256);
    for (int i = 0; i < 4; i++) {
      graphics.blit(Constants.PROCESS, left + 177, top + 8 + i * 18, 103, 46, 18, 18, 256, 256);
    }
  }

  public java.util.List<net.minecraft.client.renderer.Rect2i> extraAreas() {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    return java.util.List.of(
        new net.minecraft.client.renderer.Rect2i(left - 23, top + 4, 24, 80),
        new net.minecraft.client.renderer.Rect2i(left + 175, top + 4, 24, 80));
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    GuiUtil.renderScaledToFit(graphics, this.title.getString(), this.titleLabelX, this.titleLabelY, 88, 4210752);
    graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        4210752, false);
    int code = Math.max(0, Math.min(menu.getStatus(), STATUS_KEYS.length - 1));
    int color = switch (code) {
      case BlockEntityQuarry.STATUS_CLEARING, BlockEntityQuarry.STATUS_FRAMING -> 0x2E7D32;
      case BlockEntityQuarry.STATUS_MINING -> 0x2E7D32;
      case BlockEntityQuarry.STATUS_DONE -> 0x1565C0;
      case BlockEntityQuarry.STATUS_OFF -> 4210752;
      default -> 0xB71C1C;
    };
    GuiUtil.renderScaledToFit(graphics,
        Component.translatable("gui.faktocraft.quarry.status_" + STATUS_KEYS[code]).getString(),
        98, 7, 50, color);
  }
}
