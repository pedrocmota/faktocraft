package com.faktocraft.common.block.impl.quarry;

import net.minecraft.client.renderer.RenderPipelines;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import com.faktocraft.common.screen.ClickRouting;
import java.util.Optional;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;

public class ScreenQuarry extends AbstractContainerScreen<MenuQuarry>
    implements com.faktocraft.common.interfaces.screen.IGuiWrapper {

  private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/quarry.png");

  private static final String[] STATUS_KEYS = {
      "off", "invalid_area", "clearing", "framing", "mining", "obstructed", "full", "no_energy",
      "waiting_chunks", "done" };

  private com.faktocraft.common.screen.bar.GuiElectricBarVertical energyBar;
  private com.faktocraft.common.screen.widgets.GuiCapacitorWarning capacitorWarning;
  private final net.minecraft.client.gui.components.Button[] runModeButtons =
      new net.minecraft.client.gui.components.Button[3];

  public ScreenQuarry(MenuQuarry menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 176, 223);
    this.inventoryLabelY = 223 - 94;
  }

  @Override
  public com.faktocraft.common.entity.block.FaktocraftBlockEntity getBlockEntity() {
    return menu.getQuarry();
  }

  @Override
  public int getGuiLeft() {
    return this.leftPos;
  }

  @Override
  public int getGuiTop() {
    return this.topPos;
  }

  @Override
  public Identifier getGuiLocation() {
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
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    renderEmptySlotTooltip(graphics, mouseX, mouseY);
    if (energyBar != null) {
      energyBar.renderWidgetToolTip(this, graphics, mouseX, mouseY);
    }
    if (capacitorWarning != null) {
      capacitorWarning.renderWidgetToolTip(this, graphics, mouseX, mouseY);
    }
  }

  private void renderEmptySlotTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
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
    graphics.setTooltipForNextFrame(GuiUtil.getFont(), Component.translatable(key), mouseX, mouseY);
  }

  @Override
  public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    renderBg(graphics, partialTick, mouseX, mouseY);
    super.extractContents(graphics, mouseX, mouseY, partialTick);
  }

  protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 256,
        256);

    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.COMMON, left - 23, top + 4, 0, 134, 24, 80, 256, 256);
    for (int i = 0; i < 4; i++) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left - 21, top + 8 + i * 18, 103, 46, 18, 18, 256,
          256);
    }
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left - 20, top + 9, 110, 0, 16, 16, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left - 20, top + 27, 110, 0, 16, 16, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left - 20, top + 45, 84, 46, 16, 16, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left - 20, top + 63, 104, 28, 16, 16, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.COMMON, left + 175, top + 4, 0, 134, 24, 80, 256, 256);
    for (int i = 0; i < 4; i++) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left + 177, top + 8 + i * 18, 103, 46, 18, 18, 256,
          256);
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
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    GuiUtil.renderScaledToFit(graphics, this.title.getString(), this.titleLabelX, this.titleLabelY, 88, 4210752);
    graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        GuiUtil.opaque(4210752), false);
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

  private boolean routingSlots;

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (ClickRouting.clickChildren(this, event, doubleClick)) {
      return true;
    }
    routingSlots = true;
    try {
      return super.mouseClicked(event, doubleClick);
    } finally {
      routingSlots = false;
    }
  }

  @Override
  public Optional<GuiEventListener> getChildAt(double x, double y) {
    return routingSlots ? Optional.empty() : super.getChildAt(x, y);
  }
}
