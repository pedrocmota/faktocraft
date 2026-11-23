package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.enums.GuiSlotType;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.common.util.TextComponentUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import com.faktocraft.common.screen.TensionRemovalGuard;

public class ScreenExtractorPipe extends AbstractContainerScreen<MenuExtractorPipe>
    implements com.faktocraft.common.interfaces.screen.IGuiWrapper {

  private static final ResourceLocation BACKGROUND = new ResourceLocation(Faktocraft.MODID,
      "textures/gui/container/pipe_machine.png");

  private static final int PANEL_W = 24;
  private static final int PANEL_V = 134;
  private static final int PANEL_CAP = 4;
  private static final int RIGHT_PANEL_X = 175;
  private static final int LEFT_PANEL_X = -23;
  private static final int PANEL_Y = 4;

  private static final int WARN_BG = 0xFF3B3B3B;
  private static final int WARN_BORDER = 0xFFD8433B;
  private static final int WARN_MARK = 0xFFFFD75E;

  private final Button[] runModeButtons = new Button[3];
  private EnergyBar energyBar;

  public ScreenExtractorPipe(MenuExtractorPipe menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    this.imageWidth = 176;
    this.imageHeight = 190;
    this.inventoryLabelY = 190 - 94;
  }

  @Override
  public com.faktocraft.common.entity.block.FaktocraftBlockEntity getBlockEntity() {
    return null;
  }

  @Override
  protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
    PipeExtractor extractor = menu.getExtractor();
    if (TensionRemovalGuard.confirm(this, slot, mouseButton, type, extractor == null ? null : extractor.energy(),
        () -> super.slotClicked(slot, slotId, mouseButton, type))) {
      return;
    }
    super.slotClicked(slot, slotId, mouseButton, type);
  }

  @Override
  public ResourceLocation getGuiLocation() {
    return BACKGROUND;
  }

  public java.util.List<net.minecraft.client.renderer.Rect2i> extraAreas() {
    return java.util.List.of(
        new net.minecraft.client.renderer.Rect2i(this.leftPos + LEFT_PANEL_X, this.topPos + PANEL_Y,
            PANEL_W, PANEL_CAP * 2 + PipeExtractor.DOCK_SLOTS * 18),
        new net.minecraft.client.renderer.Rect2i(this.leftPos + RIGHT_PANEL_X, this.topPos + PANEL_Y,
            PANEL_W, PANEL_CAP * 2 + PipeExtractor.UPGRADE_SLOTS * 18));
  }

  @Override
  protected void init() {
    super.init();
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;

    for (int i = 0; i < 3; i++) {
      final int id = i;
      runModeButtons[i] = addRenderableWidget(Button
          .builder(Component.translatable("extractor." + Faktocraft.MODID + ".run." + i), b -> press(id))
          .bounds(left + 8, top + 43 + i * 17, 112, 16).build());
    }
    if (menu.getExtractor() != null) {
      energyBar = addRenderableWidget(new EnergyBar(this, 151, 42, menu.getExtractor().energy()));
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

  private boolean missingCapacitor() {
    return menu.getExtractor() != null && menu.getExtractor().energy().maxEnergy() <= 0;
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderCapacitorWarning(graphics);
    renderTooltip(graphics, mouseX, mouseY);
    renderEmptySlotTooltip(graphics, mouseX, mouseY);
    if (energyBar != null) {
      if (missingCapacitor() && energyBar.isMouseOver(mouseX, mouseY)) {
        graphics.renderTooltip(GuiUtil.getFont(),
            Component.translatable("gui." + Faktocraft.MODID + ".capacitor_required")
                .withStyle(net.minecraft.ChatFormatting.RED),
            mouseX, mouseY);
      } else {
        energyBar.renderWidgetToolTip(this, graphics, mouseX, mouseY);
      }
    }
  }

  private void renderCapacitorWarning(GuiGraphics graphics) {
    if (!missingCapacitor() || energyBar == null) {
      return;
    }
    int l = energyBar.getX();
    int t = energyBar.getY();
    int w = energyBar.getWidth();
    int h = energyBar.getHeight();
    graphics.fill(l, t, l + w, t + h, WARN_BORDER);
    graphics.fill(l + 1, t + 1, l + w - 1, t + h - 1, WARN_BG);
    if ((System.currentTimeMillis() / 600) % 2 == 0) {
      int cx = l + w / 2;
      int cy = t + h / 2;
      graphics.fill(cx - 1, cy - 12, cx + 1, cy + 4, WARN_MARK);
      graphics.fill(cx - 1, cy + 8, cx + 1, cy + 10, WARN_MARK);
    }
  }

  private void renderEmptySlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= MenuExtractorPipe.MACHINE_SLOTS) {
      return;
    }
    int dockSlot = hoveredSlot.index - MenuExtractorPipe.DOCK_INDEX;
    String key = dockSlot < 0 ? "gui." + Faktocraft.MODID + ".extractor.upgrade"
        : dockSlot == PipeExtractor.DOCK_TENSION_SLOT ? "gui." + Faktocraft.MODID + ".slot.tension"
            : dockSlot == PipeExtractor.DOCK_BATTERY_SLOT ? "gui." + Faktocraft.MODID + ".slot.dock_battery"
                : "gui." + Faktocraft.MODID + ".slot.capacitor";
    graphics.renderTooltip(GuiUtil.getFont(), Component.translatable(key), mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    panel(graphics, left + RIGHT_PANEL_X, top + PANEL_Y, PipeExtractor.UPGRADE_SLOTS);
    panel(graphics, left + LEFT_PANEL_X, top + PANEL_Y, PipeExtractor.DOCK_SLOTS);
    for (int i = 0; i < PipeExtractor.UPGRADE_SLOTS; i++) {
      slotFrame(graphics, GuiSlotType.UPGRADE, left + MenuExtractorPipe.UPGRADE_X - 1,
          top + MenuExtractorPipe.UPGRADE_Y - 1 + i * 18);
    }

    for (int i = 0; i < PipeExtractor.DOCK_SLOTS; i++) {
      int x = left + MenuExtractorPipe.DOCK_X;
      int y = top + MenuExtractorPipe.DOCK_Y + i * 18;
      slotFrame(graphics, GuiSlotType.BATTERY_DOCK, x - 1, y - 1);
      if (i == PipeExtractor.DOCK_TENSION_SLOT) {
        graphics.blit(Constants.PROCESS, x, y, 84, 46, 16, 16, 256, 256);
      } else if (i == PipeExtractor.DOCK_BATTERY_SLOT) {
        graphics.blit(Constants.PROCESS, x, y, 104, 28, 16, 16, 256, 256);
      } else {
        graphics.blit(Constants.PROCESS, x, y, 110, 0, 16, 16, 256, 256);
      }
    }
  }

  private void panel(GuiGraphics graphics, int x, int y, int slots) {
    int body = slots * 18;
    graphics.blit(Constants.COMMON, x, y, 0, PANEL_V, PANEL_W, PANEL_CAP, 256, 256);
    graphics.blit(Constants.COMMON, x, y + PANEL_CAP, 0, PANEL_V + PANEL_CAP, PANEL_W, body, 256, 256);
    graphics.blit(Constants.COMMON, x, y + PANEL_CAP + body, 0, PANEL_V + 76, PANEL_W, PANEL_CAP, 256, 256);
  }

  private void slotFrame(GuiGraphics graphics, GuiSlotType type, int x, int y) {
    graphics.blit(Constants.PROCESS, x, y, type.getOffsetLeft(), type.getOffsetTop(), type.getWidth(),
        type.getHeight(), 256, 256);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    GuiUtil.renderScaledToFit(graphics, this.title.getString(), this.titleLabelX, this.titleLabelY,
        this.imageWidth - this.titleLabelX - 8, 4210752);
    graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        4210752, false);
  }

  private static final class EnergyBar extends com.faktocraft.common.screen.progress.GuiProgress {

    private EnergyBar(com.faktocraft.common.interfaces.screen.IGuiWrapper wrapper, int leftOffset,
        int topOffset, com.faktocraft.common.energy.impl.BasicEnergyStorage energy) {
      super(wrapper, leftOffset, topOffset, energy, com.faktocraft.common.enums.GuiSprite.ELECTRIC_VERTICAL,
          Direction.VERTICAL, true);
    }

    @Override
    public ResourceLocation getResourceLocation() {
      return Constants.COMMON;
    }

    @Override
    public void renderWidgetToolTip(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
      if (isMouseOver(mouseX, mouseY)) {
        graphics.renderTooltip(GuiUtil.getFont(),
            Component.translatable("gui." + Faktocraft.MODID + ".energy",
                TextComponentUtil.getFormattedEnergyUnit(getProgress().getProgress()),
                TextComponentUtil.getFormattedEnergyUnit(getProgress().getProgressMax())),
            mouseX, mouseY);
      }
    }
  }
}
