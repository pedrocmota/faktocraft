package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.screen.widgets.FilteredEditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketModuleTree;
import com.faktocraft.common.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ScreenModule extends AbstractContainerScreen<MenuModule> {

  private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/logistics_module.png");

  private static final int CONTROLS_Y = 146;
  private static final int SEARCH_Y = 14;
  private static final int SEARCH_RIGHT = 118;
  private static final int BUTTON_W = 42;

  private ModuleType builtType;
  private boolean closing;
  private EditBox search;
  private String query = "";
  private ModuleTreePanel treePanel;
  private FilteredEditBox countBox;
  private String countNode;
  private Button flagButton;
  private Button priorityButton;
  private Button reserveButton;

  public ScreenModule(MenuModule menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 236, 256);
    this.inventoryLabelX = 37;
    this.inventoryLabelY = MenuModule.PLAYER_INV_Y - 11;
  }

  private String key(String name) {
    return "logistics." + Faktocraft.MODID + "." + name;
  }

  @Override
  protected void init() {

    if (this.minecraft != null && !closing) {
      com.faktocraft.client.GuiScaleHelper.fit(this.minecraft, this, this.imageWidth + 8, this.imageHeight + 8);
    }
    super.init();
    builtType = this.menu.getModuleType();
    buildWidgets();
  }

  @Override
  public void removed() {
    closing = true;
    if (this.minecraft != null) {
      com.faktocraft.client.GuiScaleHelper.restore(this.minecraft);
    }
    super.removed();
  }

  private Tooltip tip(String name) {
    return Tooltip.create(Component.translatable(key(name)));
  }

  private Tooltip adjustTip(String name) {
    return Tooltip.create(Component.translatable(key(name))
        .append(" ")
        .append(Component.translatable(key("module.adjust_tip")).withStyle(ChatFormatting.GRAY)));
  }

  private void buildWidgets() {
    clearWidgets();
    search = null;
    countBox = null;
    countNode = null;
    treePanel = null;
    flagButton = null;
    priorityButton = null;
    reserveButton = null;
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    ModuleType type = builtType;
    if (type == null) {
      return;
    }
    int controlsY = top + CONTROLS_Y;
    if (type == ModuleType.SINK || type == ModuleType.EJECTOR || type == ModuleType.DISPOSAL) {
      priorityButton = addRenderableWidget(Button.builder(priorityLabel(),
          b -> press(MenuModule.encode(MenuModule.ACTION_PRIORITY_UP, 0)))
          .bounds(left + 8, controlsY, 96, 14).tooltip(adjustTip("module.priority_tip")).build());
      flagButton = addRenderableWidget(Button.builder(flagLabel(),
          b -> press(MenuModule.encode(MenuModule.ACTION_TOGGLE_FLAG, 0)))
          .bounds(left + 112, controlsY, 96, 14).tooltip(tip("module.overflow_tip")).build());
    } else if (type == ModuleType.PROVIDER) {
      priorityButton = addRenderableWidget(Button.builder(priorityLabel(),
          b -> press(MenuModule.encode(MenuModule.ACTION_PRIORITY_UP, 0)))
          .bounds(left + 8, controlsY, 96, 14).tooltip(adjustTip("module.priority_tip")).build());
      reserveButton = addRenderableWidget(Button.builder(reserveLabel(),
          b -> press(MenuModule.encode(MenuModule.ACTION_RESERVE_UP, 0)))
          .bounds(left + 112, controlsY, 96, 14).tooltip(adjustTip("module.reserve_tip")).build());
    } else if (type == ModuleType.SUPPLIER) {
      flagButton = addRenderableWidget(Button.builder(flagLabel(),
          b -> press(MenuModule.encode(MenuModule.ACTION_TOGGLE_FLAG, 0)))
          .bounds(left + 8, controlsY, 96, 14).tooltip(tip("module.crafts_tip")).build());
    }

    if (this.minecraft != null && this.minecraft.level != null) {
      LogisticsItemTree.ensureBuilt(this.minecraft.level);
    }
    treePanel = new ModuleTreePanel(this.menu, this.font);
    treePanel.setQuery(query);
    treePanel.rebuild();
    search = new EditBox(this.font, left + 12, top + SEARCH_Y + 3, SEARCH_RIGHT - 16, 10, Component.empty());
    search.setBordered(false);
    search.setMaxLength(48);
    search.setTextColor(0xFFFFFFFF);
    search.setValue(query);
    search.setHint(Component.translatable(key("search_hint")).withStyle(ChatFormatting.GRAY));
    search.setResponder(text -> {
      query = text;
      if (treePanel != null) {
        treePanel.setQuery(text);
      }
    });
    addRenderableWidget(search);
    addRenderableWidget(Button.builder(Component.translatable(key("tree.all")),
        b -> ModNetworking.sendToServer(new PacketModuleTree(LogisticsItemTree.NODE_ALL, true)))
        .bounds(left + 122, top + SEARCH_Y, BUTTON_W, 13).tooltip(tip("tree.all_tip")).build());
    addRenderableWidget(Button.builder(Component.translatable(key("tree.none")),
        b -> ModNetworking.sendToServer(new PacketModuleTree(LogisticsItemTree.NODE_ALL, false)))
        .bounds(left + 168, top + SEARCH_Y, BUTTON_W, 13).tooltip(tip("tree.none_tip")).build());

    addRenderableWidget(new com.faktocraft.common.screen.button.GuiCopyPasteButton(
        left + 185, top + 2, false,
        b -> press(MenuModule.encode(MenuModule.ACTION_COPY_CONFIG, 0)),
        Component.translatable("gui." + com.faktocraft.Faktocraft.MODID + ".config.copy")));
    addRenderableWidget(new com.faktocraft.common.screen.button.GuiCopyPasteButton(
        left + 198, top + 2, true,
        b -> press(MenuModule.encode(MenuModule.ACTION_PASTE_CONFIG, 0)),
        Component.translatable("gui." + com.faktocraft.Faktocraft.MODID + ".config.paste")));
  }

  private Component priorityLabel() {
    return Component.translatable(key("priority"))
        .append(": " + ModuleSettings.getPriority(this.menu.getModuleStack()));
  }

  private Component reserveLabel() {
    return Component.translatable(key("module.reserve"))
        .append(": " + ModuleSettings.getMinReserve(this.menu.getModuleStack()));
  }

  private Component flagLabel() {
    ItemStack module = this.menu.getModuleStack();
    ModuleType type = this.menu.getModuleType();
    String flag;
    String label;
    if (type == ModuleType.SINK || type == ModuleType.EJECTOR || type == ModuleType.DISPOSAL) {
      flag = ModuleSettings.FLAG_OVERFLOW;
      label = "overflow";
    } else if (type == ModuleType.PROVIDER) {
      flag = ModuleSettings.FLAG_EXCLUDE;
      label = "exclude";
    } else {
      flag = ModuleSettings.FLAG_ALLOW_CRAFTS;
      label = "allow_crafts";
    }
    boolean on = ModuleSettings.getFlag(module, flag);
    return Component.translatable(key(label))
        .append(": ")
        .append(Component.translatable(key(on ? "on" : "off"))
            .withStyle(on ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
  }

  private void openCountEditor(ModuleTreePanel.CountField field) {
    closeCountEditor(true);
    countNode = field.node();
    countBox = new FilteredEditBox(this.font, field.x(), field.y(), field.width(), 10, Component.empty());
    countBox.setBordered(false);
    countBox.setMaxLength(4);
    countBox.setTextColor(0xFFFFFFFF);
    countBox.setFilter(text -> text.isEmpty() || text.matches("\\d{1,4}"));
    countBox.setValue(String.valueOf(field.count()));
    countBox.moveCursorToEnd(false);
    countBox.setHighlightPos(0);
    addWidget(countBox);
    setFocused(countBox);
    countBox.setFocused(true);
    if (treePanel != null) {
      treePanel.setEditingNode(countNode);
    }
  }

  private void closeCountEditor(boolean discard) {
    if (countBox != null && countNode != null && !discard) {
      String text = countBox.getValue().trim();
      int value = text.isEmpty() ? 0 : Integer.parseInt(text);
      ModNetworking.sendToServer(
          new com.faktocraft.common.network.packet.PacketModuleTreeCount(countNode, value));
    }
    if (countBox != null) {
      removeWidget(countBox);
    }
    countBox = null;
    countNode = null;
    if (treePanel != null) {
      treePanel.setEditingNode(null);
    }
  }

  private void press(int id) {
    ModNetworking.sendToServer(
        new com.faktocraft.common.network.packet.PacketMenuAction(this.menu.containerId, id));
  }

  @Override
  public void onClose() {
    press(MenuModule.encode(MenuModule.ACTION_BACK, 0));
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    ModuleType current = this.menu.getModuleType();
    if (current != builtType) {
      builtType = current;
      buildWidgets();
    }
    if (flagButton != null) {
      flagButton.setMessage(flagLabel());
    }
    if (priorityButton != null) {
      priorityButton.setMessage(priorityLabel());
    }
    if (reserveButton != null) {
      reserveButton.setMessage(reserveLabel());
    }
    if (treePanel != null) {
      treePanel.rebuildIfChanged();
    }
  }

  private boolean adjustClicked(double mouseX, double mouseY, int direction) {
    return adjust(mouseX, mouseY, direction, true);
  }

  private boolean adjust(double mouseX, double mouseY, int direction, boolean sound) {
    Button target = null;
    int upAction = 0;
    int downAction = 0;
    if (priorityButton != null && priorityButton.isMouseOver(mouseX, mouseY)) {
      target = priorityButton;
      upAction = MenuModule.ACTION_PRIORITY_UP;
      downAction = MenuModule.ACTION_PRIORITY_DOWN;
    } else if (reserveButton != null && reserveButton.isMouseOver(mouseX, mouseY)) {
      target = reserveButton;
      upAction = MenuModule.ACTION_RESERVE_UP;
      downAction = MenuModule.ACTION_RESERVE_DOWN;
    }
    if (target == null) {
      return false;
    }
    press(MenuModule.encode(direction > 0 ? upAction : downAction, 0));
    if (sound && this.minecraft != null) {
      target.playDownSound(this.minecraft.getSoundManager());
    }
    return true;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mouseX = event.x();
    double mouseY = event.y();
    int button = GuiUtil.legacyButton(event);
    if (button == 1 && adjustClicked(mouseX, mouseY, -1)) {
      return true;
    }
    if (treePanel != null) {
      int left = (this.width - this.imageWidth) / 2;
      int top = (this.height - this.imageHeight) / 2;
      if (countBox != null && countBox.isMouseOver(mouseX, mouseY)) {
        return countBox.mouseClicked(event, doubleClick);
      }
      closeCountEditor(false);
      ModuleTreePanel.CountField field = treePanel.countFieldAt(left, top, mouseX, mouseY);
      if (field != null) {
        if (button == 0) {
          openCountEditor(field);
        }
        return true;
      }
      if (treePanel.mouseClicked(left, top, mouseX, mouseY, button)) {
        if (search != null && search.isFocused() && !search.isMouseOver(mouseX, mouseY)) {
          search.setFocused(false);
          if (getFocused() == search) {
            setFocused(null);
          }
        }
        return true;
      }
    }
    closeCountEditor(false);
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    double mouseY = event.y();
    if (treePanel != null && treePanel.mouseDragged((this.height - this.imageHeight) / 2, mouseY)) {
      return true;
    }
    return super.mouseDragged(event, dragX, dragY);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (treePanel != null) {
      treePanel.mouseReleased();
    }
    return super.mouseReleased(event);
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    if (countBox != null && countBox.isFocused()) {
      return countBox.charTyped(event);
    }
    return super.charTyped(event);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    int keyCode = event.key();
    if (countBox != null && countBox.isFocused()) {
      if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
        closeCountEditor(false);
        return true;
      }
      if (keyCode == InputConstants.KEY_ESCAPE) {
        closeCountEditor(true);
        return true;
      }
      if (countBox.keyPressed(event)) {
        return true;
      }
      return keyCode != InputConstants.KEY_TAB;
    }
    if (search != null && search.isFocused()) {
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
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
    if (delta != 0 && adjust(mouseX, mouseY, delta > 0 ? 1 : -1, false)) {
      return true;
    }
    if (treePanel != null) {
      int left = (this.width - this.imageWidth) / 2;
      int top = (this.height - this.imageHeight) / 2;
      if (treePanel.mouseScrolled(left, top, mouseX, mouseY, delta)) {
        closeCountEditor(false);
        return true;
      }
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (treePanel != null) {
      int left = (this.width - this.imageWidth) / 2;
      int top = (this.height - this.imageHeight) / 2;
      treePanel.renderTooltip(graphics, left, top, mouseX, mouseY);
    }
  }

  @Override
  public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    renderBg(graphics, partialTick, mouseX, mouseY);
    super.extractContents(graphics, mouseX, mouseY, partialTick);
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    if (treePanel != null) {
      treePanel.render(graphics, left, top, mouseX, mouseY);
    }
    if (countBox != null) {
      countBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
  }

  protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;

    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0, 0, this.imageWidth, 130, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 130, 0, 100, this.imageWidth, 20, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 150, 0, 130, this.imageWidth, 106, 256, 256);
    graphics.fill(left + 5, top + 14, left + 231, top + 171, 0xFFC6C6C6);
    slotFrame(graphics, left + 213, top + 8);
    if (this.menu.getModuleType() == null) {
      return;
    }
    graphics.fill(left + 8, top + SEARCH_Y, left + SEARCH_RIGHT, top + SEARCH_Y + 13, 0xFF000000);
    graphics.fill(left + 9, top + SEARCH_Y + 1, left + SEARCH_RIGHT - 1, top + SEARCH_Y + 12, 0xFF1E1E1E);
  }

  private void slotFrame(GuiGraphicsExtractor graphics, int itemX, int itemY) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, itemX - 1, itemY - 1, 84, 27, 18, 18, 256, 256);
  }
}
