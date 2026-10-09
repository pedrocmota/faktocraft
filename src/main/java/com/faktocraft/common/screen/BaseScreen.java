package com.faktocraft.common.screen;

import com.faktocraft.common.util.GuiUtil;
import net.minecraft.client.renderer.RenderPipelines;
import com.faktocraft.common.container.FaktocraftMenu;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.screen.IGuiWrapper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import java.util.Optional;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;

public class BaseScreen<T extends FaktocraftMenu> extends AbstractContainerScreen<T> implements IGuiWrapper {
  private final T container;

  public BaseScreen(T container, Inventory inventory, Component component) {
    this(container, inventory, component, 176, 166);
  }

  public BaseScreen(T container, Inventory inventory, Component component, int imageWidth, int imageHeight) {
    super(container, inventory, component, imageWidth, imageHeight);
    this.container = container;
  }

  @Override
  protected void extractLabels(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int maxTitleWidth = this.imageWidth - this.titleLabelX - 8;
    if (getBlockEntity() instanceof com.faktocraft.common.energy.interfaces.IEnergyBlock energyBlock
        && energyBlock.showBarInGui() && energyBlock.showVertical()) {
      maxTitleWidth = Math.min(maxTitleWidth, energyBlock.leftOffsetVertical() - this.titleLabelX - 4);
    }
    com.faktocraft.common.util.GuiUtil.renderScaledToFit(graphics, this.title.getString(),
        this.titleLabelX, this.titleLabelY, maxTitleWidth, 4210752);
    graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        GuiUtil.opaque(4210752), false);
  }

  @Override
  public FaktocraftBlockEntity getBlockEntity() {
    return container.getBlockEntity();
  }

  @Override
  public Identifier getGuiLocation() {
    return null;
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
  public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    renderBg(graphics, partialTick, mouseX, mouseY);
    super.extractContents(graphics, mouseX, mouseY, partialTick);
  }

  protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
    Identifier guiLocation = getGuiLocation();
    if (guiLocation != null) {
      graphics.blit(RenderPipelines.GUI_TEXTURED, guiLocation, getGuiLeft(), getGuiTop(), 0.0F, 0.0F, this.imageWidth,
          this.imageHeight, 256, 256);
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
