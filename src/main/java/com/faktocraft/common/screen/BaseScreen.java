package com.faktocraft.common.screen;

import com.faktocraft.common.container.FaktocraftMenu;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.screen.IGuiWrapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

public class BaseScreen<T extends FaktocraftMenu> extends AbstractContainerScreen<T> implements IGuiWrapper {

  private final T container;

  public BaseScreen(T container, Inventory inventory, Component component) {
    this(container, inventory, component, 176, 166);
  }

  public BaseScreen(T container, Inventory inventory, Component component, int imageWidth, int imageHeight) {
    super(container, inventory, component);
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.container = container;
  }

  @Override
  protected void renderLabels(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
    int maxTitleWidth = this.imageWidth - this.titleLabelX - 8;
    if (getBlockEntity() instanceof com.faktocraft.common.energy.interfaces.IEnergyBlock energyBlock
        && energyBlock.showBarInGui() && energyBlock.showVertical()) {
      maxTitleWidth = Math.min(maxTitleWidth, energyBlock.leftOffsetVertical() - this.titleLabelX - 4);
    }
    com.faktocraft.common.util.GuiUtil.renderScaledToFit(graphics, this.title.getString(),
        this.titleLabelX, this.titleLabelY, maxTitleWidth, 4210752);
    graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        4210752, false);
  }

  @Override
  public FaktocraftBlockEntity getBlockEntity() {
    return container.getBlockEntity();
  }

  @Override
  public ResourceLocation getGuiLocation() {
    return null;
  }

  @Override
  protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
    FaktocraftBlockEntity blockEntity = getBlockEntity();
    if (TensionRemovalGuard.confirm(this, slot, mouseButton, type,
        blockEntity == null ? null : blockEntity.getEnergyStorage(),
        () -> super.slotClicked(slot, slotId, mouseButton, type))) {
      return;
    }
    super.slotClicked(slot, slotId, mouseButton, type);
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
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    renderTooltip(graphics, mouseX, mouseY);
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    ResourceLocation guiLocation = getGuiLocation();
    if (guiLocation != null) {
      graphics.blit(guiLocation, getGuiLeft(), getGuiTop(), 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }
  }
}
