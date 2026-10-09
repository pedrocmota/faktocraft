package com.faktocraft.common.block.impl.logistics;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketCoreTasksReq;
import com.faktocraft.common.network.packet.PacketOpenCoreView;
import com.faktocraft.common.network.packet.PacketTableState;
import com.faktocraft.common.network.packet.PacketTaskHistoryOp;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ScreenCoreTasks extends AbstractContainerScreen<MenuCoreTasks> {

  private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/request_table_tasks.png");
  private static final int REFRESH_INTERVAL = 40;

  private TaskListPanel taskPanel;
  private int refreshTimer;

  public ScreenCoreTasks(MenuCoreTasks menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 340, 256);
  }

  private String key(String name) {
    return "logistics." + Faktocraft.MODID + "." + name;
  }

  @Override
  protected void init() {
    if (this.minecraft != null) {
      com.faktocraft.client.GuiScaleHelper.fit(this.minecraft, this, this.imageWidth + 8, this.imageHeight + 8);
    }
    super.init();
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;

    if (taskPanel == null) {
      taskPanel = new TaskListPanel(this.minecraft, this.font, true, new TaskListPanel.Host() {
        @Override
        public void sendHistoryOp(int mode, long id) {
          ModNetworking.sendToServer(new PacketTaskHistoryOp(
              ScreenCoreTasks.this.menu.getCorePos(), dimensionId(), mode, id));
        }

        @Override
        public void requestRefresh() {
          requestState();
        }
      });
    }
    taskPanel.initWidgets(left, top, this::addRenderableWidget);
    taskPanel.setVisible(true);

    addRenderableWidget(Button.builder(Component.translatable(key("back")),
        b -> ModNetworking.sendToServer(new PacketOpenCoreView(this.menu.getCorePos(), false)))
        .bounds(left + 270, top + 12, 60, 16).build());

    requestState();
    refreshTimer = REFRESH_INTERVAL;
  }

  public boolean matches(BlockPos pos) {
    return pos.equals(this.menu.getCorePos());
  }

  public void applyState(PacketTableState state) {
    taskPanel.setData(state.tasks(), state.errors());
  }

  private void requestState() {
    ModNetworking.sendToServer(new PacketCoreTasksReq(this.menu.getCorePos()));
  }

  private String dimensionId() {
    return this.minecraft != null && this.minecraft.level != null
        ? this.minecraft.level.dimension().identifier().toString()
        : "minecraft:overworld";
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    if (--refreshTimer <= 0) {
      requestState();
      refreshTimer = REFRESH_INTERVAL;
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 512,
        256);
    taskPanel.render(graphics, left, top, mouseX, mouseY);
    for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
      renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    graphics.text(this.font, this.title, left + 8, top + 6, GuiUtil.opaque(0x404040), false);
    taskPanel.renderTooltip(graphics, left, top, mouseX, mouseY);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mouseX = event.x();
    double mouseY = event.y();
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    if (taskPanel.mouseClicked(left, top, mouseX, mouseY)) {
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
    taskPanel.mouseScrolled(delta);
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    int keyCode = event.key();
    EditBox search = taskPanel != null ? taskPanel.searchBox() : null;
    if (search != null && search.isFocused() && search.isVisible()) {
      if (keyCode == InputConstants.KEY_ESCAPE) {
        search.setFocused(false);
        if (getFocused() == search) {
          setFocused(null);
        }
        return true;
      }
      if (search.keyPressed(event)) {
        return true;
      }
      return keyCode != InputConstants.KEY_TAB;
    }
    return super.keyPressed(event);
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
  }

  @Override
  public void removed() {
    if (this.minecraft != null) {
      com.faktocraft.client.GuiScaleHelper.restore(this.minecraft);
    }
    super.removed();
  }

}
