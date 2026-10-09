package com.faktocraft.common.block.impl.forester;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.GuiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import com.faktocraft.common.screen.ClickRouting;
import java.util.Optional;
import net.minecraft.client.gui.components.events.GuiEventListener;

public class ScreenForester extends AbstractContainerScreen<MenuForester>
    implements com.faktocraft.common.interfaces.screen.IGuiWrapper {

  private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/quarry.png");

  private static final String[] STATUS_KEYS = {
      "quarry.status_off", "quarry.status_invalid_area", "quarry.status_clearing", "quarry.status_framing",
      "forester.status_working", "quarry.status_obstructed", "quarry.status_full", "quarry.status_no_energy",
      "quarry.status_waiting_chunks", "quarry.status_done", "forester.status_idle", "forester.status_no_saplings" };

  private static final int BUTTON_WIDTH = 84;
  private static final int TOGGLE_X = MenuForester.FERTILIZER_X + 3;
  private static final int TOGGLE_Y = MenuForester.INPUT_Y + 40;
  private static final int TOGGLE_SIZE = 10;

  private com.faktocraft.common.screen.bar.GuiElectricBarVertical energyBar;
  private com.faktocraft.common.screen.widgets.GuiCapacitorWarning capacitorWarning;
  private final Button[] runModeButtons = new Button[3];
  private ResinToggle resinToggle;

  public ScreenForester(MenuForester menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 176, 223);
    this.inventoryLabelY = 223 - 94;
  }

  @Override
  public com.faktocraft.common.entity.block.FaktocraftBlockEntity getBlockEntity() {
    return menu.getForester();
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
    if (menu.getForester() != null) {
      capacitorWarning = addRenderableWidget(new com.faktocraft.common.screen.widgets.GuiCapacitorWarning(
          this, 151, 17, menu.getForester()));
      energyBar = addRenderableWidget(new com.faktocraft.common.screen.bar.GuiElectricBarVertical(
          this, 151, 17, menu.getForester().getEnergyStorage(), menu.getForester()));
    }
    for (int i = 0; i < 3; i++) {
      final int id = i;
      runModeButtons[i] = addRenderableWidget(Button
          .builder(Component.translatable("motor." + Faktocraft.MODID + ".run." + i), b -> press(id))
          .bounds(left + 8, top + 18 + i * 17, BUTTON_WIDTH, 16).build());
    }
    resinToggle = addRenderableWidget(new ResinToggle(left + TOGGLE_X, top + TOGGLE_Y));
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
    if (resinToggle != null && resinToggle.isHovered()) {
      graphics.setTooltipForNextFrame(GuiUtil.getFont(),
          Component.translatable("gui." + Faktocraft.MODID + ".forester.resin_mode"), mouseX, mouseY);
    }
  }

  private void renderEmptySlotTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= MenuForester.INPUT_END) {
      return;
    }
    int index = hoveredSlot.index;
    String key;
    if (index < 2) {
      key = "gui." + Faktocraft.MODID + ".slot.capacitor";
    } else if (index == 2) {
      key = "gui." + Faktocraft.MODID + ".slot.tension";
    } else if (index < 7) {
      key = "gui." + Faktocraft.MODID + ".extractor.upgrade";
    } else if (index < MenuForester.INPUT_START) {
      key = "gui." + Faktocraft.MODID + ".slot.dock_battery";
    } else if (index < MenuForester.SAPLING_END) {
      key = "gui." + Faktocraft.MODID + ".forester.slot.sapling";
    } else {
      key = "gui." + Faktocraft.MODID + ".forester.slot.fertilizer";
    }
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
    for (int i = 0; i < BlockEntityForester.SAPLING_SLOTS; i++) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left + MenuForester.SAPLING_X - 1,
          top + MenuForester.INPUT_Y - 1 + i * 18, 103, 46, 18, 18, 256, 256);
    }
    for (int i = 0; i < BlockEntityForester.FERTILIZER_SLOTS; i++) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, left + MenuForester.FERTILIZER_X - 1,
          top + MenuForester.INPUT_Y - 1 + i * 18, 103, 46, 18, 18, 256, 256);
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
      case BlockEntityGantry.STATUS_CLEARING, BlockEntityGantry.STATUS_FRAMING -> 0x2E7D32;
      case BlockEntityGantry.STATUS_WORKING -> 0x2E7D32;
      case BlockEntityGantry.STATUS_DONE, BlockEntityForester.STATUS_IDLE -> 0x1565C0;
      case BlockEntityGantry.STATUS_OFF -> 4210752;
      default -> 0xB71C1C;
    };
    GuiUtil.renderScaledToFit(graphics,
        Component.translatable("gui.faktocraft." + STATUS_KEYS[code]).getString(), 98, 7, 50, color);
  }

  private final class ResinToggle extends AbstractWidget {

    private ResinToggle(int x, int y) {
      super(x, y, TOGGLE_SIZE, TOGGLE_SIZE, Component.empty());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      int x0 = getX();
      int y0 = getY();
      int x1 = x0 + TOGGLE_SIZE;
      int y1 = y0 + TOGGLE_SIZE;
      int border = isHovered() ? 0xFF6A6A6A : 0xFF8B8B8B;
      graphics.fill(x0, y0, x1, y1, border);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
      if (menu.isResinMode()) {
        graphics.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, 0xFFC98A2E);
      }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
      press(MenuForester.BUTTON_RESIN);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }
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
