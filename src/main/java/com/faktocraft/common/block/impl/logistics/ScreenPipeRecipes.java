package com.faktocraft.common.block.impl.logistics;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketMenuAction;
import com.faktocraft.common.screen.button.GuiCopyPasteButton;
import com.faktocraft.common.screen.button.GuiHelpButton;
import com.faktocraft.common.util.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class ScreenPipeRecipes<M extends MenuPipeRecipes> extends AbstractContainerScreen<M> {

  protected static final int SLOT_U = 84;
  protected static final int SLOT_V = 27;

  protected static final int MAX_HEIGHT = 320;
  private static final int MIN_LIST_ROWS = 4;
  private static final int INV_HEIGHT = 82;
  private static final int CONTENT_GAP = 14;
  private static final int HELP_MAX_H = 150;
  private static final String SLOT_Y_FIELD = "y";

  private static final int TOP_BAND = 120;
  private static final int BOTTOM_BAND = 100;
  private static final int STRIP_V = 40;
  private static final int STRIP_H = 80;

  protected static final int PAD = MenuPipeRecipes.PAD;
  protected static final int LIST_X = 8;
  protected static final int LIST_W = MenuPipeRecipes.WIDTH - 2 * LIST_X;
  protected static final int SEARCH_Y = 18;
  protected static final int LIST_Y = 34;
  protected static final int ROW_H = 20;

  private static final int CARD_X = LIST_X + 2;
  private static final int CARD_W = LIST_W - 11;
  private static final int BAR_X = LIST_X + LIST_W - 6;
  private static final int COPY_ICON_X = CARD_X + CARD_W - 15;
  private static final int COPY_ICON_SIZE = 12;

  private static final int FRAME_FILL = 0xFF8B8B8B;
  private static final int FRAME_DARK = 0xFF373737;
  private static final int FRAME_LIGHT = 0xFFFFFFFF;
  private static final int CARD_FILL = 0xFFC6C6C6;
  private static final int CARD_BORDER = 0xFF6A6A6A;
  private static final int CARD_HOVER_FILL = 0xFFDCDCDC;
  private static final int CARD_HOVER_BORDER = 0xFF4A4A4A;
  private static final int CARD_HELD_FILL = 0xFFA8A8A8;
  private static final int SEARCH_BORDER = 0xFF000000;
  private static final int SEARCH_FILL = 0xFF1E1E1E;
  private static final int BAR_TRACK = 0xFF6E6E6E;
  private static final int BAR_THUMB = 0xFF3A3A3A;
  private static final int TEXT = 0x2E2E2E;
  private static final int TEXT_DIM = 0x555555;
  private static final int DROP_MARK = 0xFFE8B923;

  private static final int DRAG_SLOP = 3;

  private static final int HELP_BUTTON_X = MenuPipeRecipes.WIDTH - 48;
  private static final int HELP_CORNER_X = MenuPipeRecipes.WIDTH - 20;
  private static final int HELP_Y = 36;
  private static final int HELP_X = LIST_X + 4;
  private static final int HELP_TEXT_W = LIST_W - 16;
  private static final int HELP_LINE_H = 10;
  private static final int HELP_GAP = 5;
  private static final int HELP_TITLE = 0x1F4E79;

  protected record HelpTopic(Component title, Component body) {
  }

  private record HelpLine(FormattedCharSequence text, int color, int height) {
  }

  private final List<HelpLine> helpLines = new ArrayList<>();
  private boolean help;
  private int helpScroll;
  private boolean draggingHelpBar;
  private double helpBarGrab;
  private int invY;
  private int dynamicHeight = MAX_HEIGHT;

  private int listScroll;

  private int filteredCount = -1;
  private String filteredQuery;
  private int filterCooldown;
  private int builtEdit = Integer.MIN_VALUE;
  private Button addButton;
  private EditBox search;
  private String query = "";

  private final List<Integer> visible = new ArrayList<>();

  private boolean draggingBar;
  private double barGrab;

  private int edgeScrollCooldown;
  private int grabbedRow = -1;
  private double grabbedAtY;
  private boolean dragging;
  private double dragY;

  protected ScreenPipeRecipes(M menu, Inventory inventory, Component title, int width) {
    super(menu, inventory, title, width, MAX_HEIGHT);
    this.invY = menu.playerInvY();
    this.inventoryLabelX = MenuPipeRecipes.PLAYER_INV_X - 1;
    this.inventoryLabelY = invY - 11;
  }

  protected String key(String name) {
    return "logistics." + Faktocraft.MODID + "." + name;
  }

  protected abstract int listRows();

  protected abstract int maxEntries();

  protected abstract ItemStack entryIcon(int index);

  protected abstract Component entryLabel(int index);

  protected abstract Component listWarning();

  protected abstract int detailBottom();

  protected abstract int removeButtonX();

  protected abstract void buildDetailWidgets(int left, int top);

  protected abstract void renderDetailBg(GuiGraphicsExtractor graphics);

  protected abstract void renderDetailLabels(GuiGraphicsExtractor graphics);

  protected abstract void renderDetailHover(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

  protected abstract boolean detailMouseClicked(double mouseX, double mouseY, int button);

  protected abstract boolean detailMouseScrolled(double mouseX, double mouseY, double delta);

  protected List<HelpTopic> helpTopics() {
    return List.of();
  }

  protected int backButtonX() {
    return LIST_X;
  }

  private int listFrameBottom() {
    return LIST_Y + visibleRows() * ROW_H + 1;
  }

  private List<FormattedCharSequence> warningLines() {
    Component warning = listWarning();
    if (warning.getString().isEmpty()) {
      return List.of();
    }
    return this.font.split(Component.empty().append(warning).withStyle(ChatFormatting.DARK_RED), LIST_W - 8);
  }

  private int addButtonY() {
    List<FormattedCharSequence> lines = warningLines();
    return listFrameBottom() + (lines.isEmpty() ? 4 : 7 + lines.size() * 10);
  }

  private int helpContentBottom() {
    int total = 0;
    for (HelpLine line : helpLines) {
      total += line.height();
    }
    return HELP_Y + Math.min(total, HELP_MAX_H) + 3;
  }

  private int contentBottom() {
    if (help) {
      return helpContentBottom();
    }
    return isEditing() ? detailBottom() : addButtonY() + 16;
  }

  private void applyLayout() {
    int newInvY = contentBottom() + CONTENT_GAP;
    int newHeight = newInvY + INV_HEIGHT;
    int newTop = (this.height - newHeight) / 2;
    int shift = newTop - this.topPos;
    if (addButton != null) {
      addButton.setY(newTop + addButtonY());
    }
    if (newInvY == invY && newHeight == getImageHeight() && shift == 0) {
      return;
    }
    invY = newInvY;
    dynamicHeight = newHeight;
    this.topPos = newTop;
    this.inventoryLabelY = invY - 11;
    for (Slot slot : this.menu.slots) {
      if (!(slot.container instanceof Inventory)) {
        continue;
      }
      int index = slot.getContainerSlot();
      int y = invY + (index < 9 ? 58 : ((index - 9) / 9) * 18);
      if (slot.y != y) {
        ObfuscationReflectionHelper.setPrivateValue(Slot.class, slot, y, SLOT_Y_FIELD);
      }
    }
    if (shift != 0) {
      for (var child : this.children()) {
        if (child instanceof AbstractWidget widget) {
          widget.setY(widget.getY() + shift);
        }
      }
    }
  }

  @Override
  public int getImageHeight() {
    return dynamicHeight;
  }

  @Override
  protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
    return mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + this.imageWidth
        || mouseY >= guiTop + getImageHeight();
  }

  public boolean isEditing() {
    return this.menu.editIndex() >= 0;
  }

  protected void press(int id) {
    ModNetworking.sendToServer(new PacketMenuAction(this.menu.containerId, id));
  }

  @Override
  protected void init() {
    if (this.minecraft != null) {

      com.faktocraft.client.GuiScaleHelper.fit(this.minecraft, this, this.imageWidth + 8, MAX_HEIGHT, 16);
    }
    super.init();
    builtEdit = Integer.MIN_VALUE;
    buildView();
  }

  @Override
  public void removed() {
    if (this.minecraft != null) {
      com.faktocraft.client.GuiScaleHelper.restore(this.minecraft);
    }
    super.removed();
  }

  private void buildView() {
    clearWidgets();
    builtEdit = this.menu.editIndex();
    if (help) {
      buildHelpLines();
    }
    applyLayout();
    int left = this.leftPos;
    int top = this.topPos;
    if (help) {
      addButton = null;
      search = null;
      cancelDrag();
      addRenderableWidget(Button.builder(Component.translatable(key("recipes.back")), b -> toggleHelp(false))
          .bounds(left + backButtonX(), top + 18, 44, 14).build());
      return;
    }
    if (builtEdit < 0) {
      addButton = addRenderableWidget(Button.builder(Component.translatable(key("recipes.add")),
          b -> press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_ADD, 0)))
          .bounds(left + LIST_X, top + addButtonY(), LIST_W, 16).build());
      search = new EditBox(this.font, left + LIST_X + 4, top + SEARCH_Y + 3, LIST_W - 8, 10,
          Component.empty());
      search.setBordered(false);
      search.setMaxLength(48);
      search.setTextColor(0xFFFFFFFF);
      search.setValue(query);
      search.setHint(Component.translatable(key("search_hint")).withStyle(ChatFormatting.GRAY));
      search.setResponder(text -> {
        query = text;
        listScroll = 0;
        refreshFilter();
      });
      addRenderableWidget(search);

      addRenderableWidget(new com.faktocraft.common.screen.button.GuiCopyPasteButton(
          left + MenuPipeRecipes.WIDTH - 34, top + 4, false,
          b -> press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_COPY_CONFIG, 0)),
          Component.translatable(key("recipes.copy_all"))));
      addRenderableWidget(new com.faktocraft.common.screen.button.GuiCopyPasteButton(
          left + MenuPipeRecipes.WIDTH - 20, top + 4, true,
          b -> press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_PASTE_CONFIG, 0)),
          Component.translatable(key("recipes.paste"))));
      addHelpButton(left + HELP_BUTTON_X, top);
      refreshFilter();
    } else {
      addButton = null;
      search = null;
      cancelDrag();
      addRenderableWidget(Button.builder(Component.translatable(key("recipes.back")),
          b -> press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_BACK, 0)))
          .bounds(left + backButtonX(), top + 18, 44, 14).build());
      addRenderableWidget(Button.builder(Component.translatable(key("recipes.remove")),
          b -> press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_REMOVE, 0)))
          .bounds(left + removeButtonX(), top + 18, 48, 14).build());
      buildDetailWidgets(left, top);
      addHelpButton(left + HELP_CORNER_X, top);
    }
  }

  private void addHelpButton(int x, int top) {
    if (helpTopics().isEmpty()) {
      return;
    }
    addRenderableWidget(new GuiHelpButton(x, top + 4, b -> toggleHelp(true),
        Component.translatable(key("help"))));
  }

  private void toggleHelp(boolean shown) {
    help = shown;
    helpScroll = 0;
    draggingHelpBar = false;
    buildView();
  }

  private void buildHelpLines() {
    helpLines.clear();
    for (HelpTopic topic : helpTopics()) {
      for (FormattedCharSequence line : this.font.split(topic.title(), HELP_TEXT_W)) {
        helpLines.add(new HelpLine(line, HELP_TITLE, HELP_LINE_H));
      }
      for (FormattedCharSequence line : this.font.split(topic.body(), HELP_TEXT_W)) {
        helpLines.add(new HelpLine(line, TEXT, HELP_LINE_H));
      }
      helpLines.add(new HelpLine(null, 0, HELP_GAP));
    }
    if (!helpLines.isEmpty()) {
      helpLines.remove(helpLines.size() - 1);
    }
  }

  private int helpBottom() {
    return this.inventoryLabelY - 4;
  }

  private int helpMaxScroll() {
    int budget = helpBottom() - HELP_Y;
    int start = helpLines.size();
    int used = 0;
    while (start > 0 && used + helpLines.get(start - 1).height() <= budget) {
      used += helpLines.get(--start).height();
    }
    return start;
  }

  private int helpTrack() {
    return helpBottom() - HELP_Y;
  }

  private int helpThumb() {
    int shown = helpLines.size() - helpMaxScroll();
    return Math.max(8, helpTrack() * shown / Math.max(1, helpLines.size()));
  }

  private int helpThumbY() {
    int maxScroll = helpMaxScroll();
    return HELP_Y + (maxScroll > 0 ? (helpTrack() - helpThumb()) * helpScroll / maxScroll : 0);
  }

  private void dragHelpBarTo(double mouseY) {
    int span = helpTrack() - helpThumb();
    int maxScroll = helpMaxScroll();
    if (span <= 0 || maxScroll <= 0) {
      helpScroll = 0;
      return;
    }
    double offset = mouseY - helpBarGrab - (this.topPos + HELP_Y);
    helpScroll = (int) Math.round(Math.max(0, Math.min(span, offset)) * maxScroll / span);
  }

  private boolean overHelpBar(double mouseX, double mouseY) {
    int relX = (int) (mouseX - this.leftPos);
    return helpMaxScroll() > 0 && relX >= BAR_X && relX < BAR_X + 5
        && mouseY >= this.topPos + HELP_Y && mouseY < this.topPos + helpBottom();
  }

  private boolean overHelp(double mouseX, double mouseY) {
    return mouseX >= this.leftPos + LIST_X && mouseX < this.leftPos + LIST_X + LIST_W
        && mouseY >= this.topPos + HELP_Y - 3 && mouseY < this.topPos + helpBottom() + 3;
  }

  private void renderHelp(GuiGraphicsExtractor graphics) {
    int bottom = helpBottom();
    frame(graphics, LIST_X, HELP_Y - 3, LIST_W, bottom - HELP_Y + 6);
    graphics.fill(LIST_X + 1, HELP_Y - 2, LIST_X + LIST_W - 1, bottom + 2, CARD_FILL);
    int y = HELP_Y;
    for (int i = helpScroll; i < helpLines.size(); i++) {
      HelpLine line = helpLines.get(i);
      if (y + line.height() > bottom) {
        break;
      }
      if (line.text() != null) {
        graphics.text(this.font, line.text(), HELP_X, y, GuiUtil.opaque(line.color()), false);
      }
      y += line.height();
    }
    if (helpMaxScroll() > 0) {
      graphics.fill(BAR_X, HELP_Y, BAR_X + 4, HELP_Y + helpTrack(), BAR_TRACK);
      graphics.fill(BAR_X, helpThumbY(), BAR_X + 4, helpThumbY() + helpThumb(),
          draggingHelpBar ? FRAME_DARK : BAR_THUMB);
    }
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    if (this.menu.editIndex() != builtEdit) {
      buildView();
    }
    applyLayout();
    if (addButton != null) {
      addButton.active = this.menu.entryCount() < maxEntries();
    }
    if (!isEditing() && (this.menu.entryCount() != filteredCount || !query.equals(filteredQuery)
        || --filterCooldown <= 0)) {
      refreshFilter();
    }
    tickEdgeScroll();

    if (grabbedRow >= visible.size()) {
      cancelDrag();
    }
    int maxScroll = Math.max(0, visible.size() - visibleRows());
    if (listScroll > maxScroll) {
      listScroll = maxScroll;
    }
  }

  private boolean hasScrollBar() {
    return visible.size() > visibleRows();
  }

  private int trackHeight() {
    return visibleRows() * ROW_H - 2;
  }

  private int thumbHeight() {
    return Math.max(8, trackHeight() * visibleRows() / Math.max(1, visible.size()));
  }

  private int thumbY() {
    int maxScroll = Math.max(1, visible.size() - visibleRows());
    return LIST_Y + (trackHeight() - thumbHeight()) * listScroll / maxScroll;
  }

  private int visibleRows() {
    return Math.max(MIN_LIST_ROWS, Math.min(listRows(), this.menu.entryCount()));
  }

  private void refreshFilter() {
    filteredCount = this.menu.entryCount();
    filteredQuery = query;
    filterCooldown = 20;
    visible.clear();
    String needle = query.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < this.menu.entryCount(); i++) {
      if (needle.isEmpty() || entryLabel(i).getString().toLowerCase(Locale.ROOT).contains(needle)) {
        visible.add(i);
      }
    }
  }

  private void cancelDrag() {
    grabbedRow = -1;
    dragging = false;
  }

  private void dragBarTo(double mouseY) {
    int span = trackHeight() - thumbHeight();
    int maxScroll = Math.max(0, visible.size() - visibleRows());
    if (span <= 0 || maxScroll <= 0) {
      listScroll = 0;
      return;
    }
    double offset = mouseY - barGrab - (this.topPos + LIST_Y);
    listScroll = (int) Math.round(Math.max(0, Math.min(span, offset)) * maxScroll / span);
  }

  private void tickEdgeScroll() {
    if (!dragging) {
      edgeScrollCooldown = 0;
      return;
    }
    double top = this.topPos + LIST_Y;
    double bottom = top + visibleRows() * ROW_H;
    int direction = dragY < top + ROW_H ? -1 : dragY > bottom - ROW_H ? 1 : 0;
    if (direction == 0) {
      edgeScrollCooldown = 0;
      return;
    }
    if (--edgeScrollCooldown > 0) {
      return;
    }
    edgeScrollCooldown = 3;
    int maxScroll = Math.max(0, visible.size() - visibleRows());
    listScroll = Math.max(0, Math.min(maxScroll, listScroll + direction));
  }

  private int rowAt(double mouseX, double mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - getImageHeight()) / 2;
    double relX = mouseX - left - LIST_X;
    double relY = mouseY - top - LIST_Y;
    if (relX < 0 || relX >= LIST_W || relY < 0 || relY >= visibleRows() * ROW_H
        || (hasScrollBar() && relX >= BAR_X - LIST_X)) {
      return -1;
    }
    int row = listScroll + (int) (relY / ROW_H);
    return row < visible.size() ? row : -1;
  }

  private boolean copyIconHovered(int relX, int relY, int rowY) {
    return relX >= COPY_ICON_X && relX < COPY_ICON_X + COPY_ICON_SIZE
        && relY >= rowY + 2 && relY < rowY + 2 + COPY_ICON_SIZE;
  }

  private int copyIconRow(double mouseX, double mouseY) {
    int row = rowAt(mouseX, mouseY);
    if (row < 0 || dragging) {
      return -1;
    }
    int rowY = LIST_Y + (row - listScroll) * ROW_H;
    return copyIconHovered((int) (mouseX - this.leftPos), (int) (mouseY - this.topPos), rowY) ? row : -1;
  }

  private int dropRow(double mouseY) {
    int top = (this.height - getImageHeight()) / 2;
    double relY = mouseY - top - LIST_Y;
    int row = listScroll + (int) Math.round(relY / ROW_H);
    return Math.max(0, Math.min(visible.size(), row));
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mouseX = event.x();
    double mouseY = event.y();
    int button = GuiUtil.legacyButton(event);
    if (help) {
      if (button == 0 && overHelpBar(mouseX, mouseY)) {
        draggingHelpBar = true;
        double thumbTop = this.topPos + helpThumbY();
        helpBarGrab = mouseY >= thumbTop && mouseY < thumbTop + helpThumb()
            ? mouseY - thumbTop : helpThumb() / 2.0;
        dragHelpBarTo(mouseY);
        return true;
      }
      return super.mouseClicked(event, doubleClick);
    }
    if (isEditing()) {
      if (detailMouseClicked(mouseX, mouseY, button)) {
        return true;
      }
      return super.mouseClicked(event, doubleClick);
    }
    int relX = (int) (mouseX - this.leftPos);
    if (hasScrollBar() && button == 0 && relX >= BAR_X && relX < BAR_X + 5
        && mouseY >= this.topPos + LIST_Y && mouseY < this.topPos + LIST_Y + trackHeight()) {
      cancelDrag();
      draggingBar = true;
      double thumbTop = this.topPos + thumbY();
      barGrab = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight()
          ? mouseY - thumbTop : thumbHeight() / 2.0;
      dragBarTo(mouseY);
      return true;
    }
    int copyRow = button == 0 ? copyIconRow(mouseX, mouseY) : -1;
    if (copyRow >= 0) {
      press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_COPY_ENTRY, visible.get(copyRow)));
      return true;
    }
    int row = rowAt(mouseX, mouseY);
    if (row >= 0 && button == 0) {

      grabbedRow = row;
      grabbedAtY = mouseY;
      dragY = mouseY;
      dragging = false;
      setFocused(null);
      if (search != null) {
        search.setFocused(false);
      }
      return true;
    }
    cancelDrag();
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY0) {
    double mouseY = event.y();
    if (draggingHelpBar) {
      dragHelpBarTo(mouseY);
      return true;
    }
    if (draggingBar) {
      dragBarTo(mouseY);
      return true;
    }
    if (grabbedRow >= 0) {
      dragY = mouseY;
      if (!dragging && Math.abs(mouseY - grabbedAtY) > DRAG_SLOP) {
        dragging = true;
      }
      return true;
    }
    return super.mouseDragged(event, dragX, dragY0);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    double mouseY = event.y();
    int button = GuiUtil.legacyButton(event);
    if (draggingHelpBar && button == 0) {
      draggingHelpBar = false;
      return true;
    }
    if (draggingBar && button == 0) {
      draggingBar = false;
      return true;
    }
    if (grabbedRow >= 0 && button == 0) {
      int row = grabbedRow;
      boolean moved = dragging;
      cancelDrag();
      if (row >= visible.size()) {
        return true;
      }
      if (!moved) {
        press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_OPEN, visible.get(row)));
        return true;
      }

      int gap = dropRow(mouseY);
      int from = visible.get(row);
      int to = gap >= visible.size() ? this.menu.entryCount() : visible.get(gap);
      if (from != to && !(gap == row) && !(gap == row + 1)) {
        press(MenuPipeRecipes.encodeMove(from, to));
      }
      return true;
    }
    return super.mouseReleased(event);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
    if (help) {
      if (overHelp(mouseX, mouseY)) {
        helpScroll = Math.max(0, Math.min(helpMaxScroll(), helpScroll - (int) Math.signum(delta)));
        return true;
      }
      return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }
    if (isEditing()) {
      if (detailMouseScrolled(mouseX, mouseY, delta)) {
        return true;
      }
      return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }
    int maxScroll = Math.max(0, visible.size() - visibleRows());
    if (maxScroll > 0) {
      listScroll = Math.max(0, Math.min(maxScroll, listScroll - (int) Math.signum(delta)));
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    int keyCode = event.key();
    if (keyCode == InputConstants.KEY_ESCAPE) {
      if (help) {
        toggleHelp(false);
        return true;
      }
      if (isEditing()) {
        press(MenuPipeRecipes.encode(MenuPipeRecipes.ACTION_BACK, 0));
        return true;
      }
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
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (help) {
      return;
    }
    if (isEditing()) {
      renderDetailHover(graphics, mouseX, mouseY);
    } else if (copyIconRow(mouseX, mouseY) >= 0) {
      graphics.setTooltipForNextFrame(this.font, Component.translatable(key("recipes.copy_one")), mouseX, mouseY);
    }
  }

  @Override
  public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    renderBg(graphics, partialTick, mouseX, mouseY);
    super.extractContents(graphics, mouseX, mouseY, partialTick);
  }

  protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {

    int band = (this.imageWidth + 1) / 2;
    int rightU = 176 - band;
    int rightX = this.leftPos + this.imageWidth - band;
    int texH = textureHeight();
    int topBand = Math.min(TOP_BAND, getImageHeight() - BOTTOM_BAND);
    blitBand(graphics, band, rightU, rightX, this.topPos, 0, topBand);
    int bottomY = this.topPos + getImageHeight() - BOTTOM_BAND;
    blitBand(graphics, band, rightU, rightX, bottomY, texH - BOTTOM_BAND, BOTTOM_BAND);
    for (int y = this.topPos + topBand; y < bottomY; y += STRIP_H) {
      blitBand(graphics, band, rightU, rightX, y, STRIP_V, Math.min(STRIP_H, bottomY - y));
    }
    renderPlayerInventory(graphics);
    if (help) {
      return;
    }
    if (isEditing()) {
      renderDetailBg(graphics);
      return;
    }

    int left = this.leftPos + LIST_X;
    int top = this.topPos + SEARCH_Y;
    graphics.fill(left, top, left + LIST_W, top + 14, SEARCH_BORDER);
    graphics.fill(left + 1, top + 1, left + LIST_W - 1, top + 13, SEARCH_FILL);
  }

  private void renderPlayerInventory(GuiGraphicsExtractor graphics) {
    graphics.fill(this.leftPos + 3, this.topPos + invY - 1, this.leftPos + this.imageWidth - 3,
        this.topPos + invY + 76, 0xFFC6C6C6);
    for (int i = 0; i < 27; i++) {
      slotFrame(graphics, MenuPipeRecipes.PLAYER_INV_X + (i % 9) * 18, invY + (i / 9) * 18);
    }
    for (int col = 0; col < 9; col++) {
      slotFrame(graphics, MenuPipeRecipes.PLAYER_INV_X + col * 18, invY + 58);
    }
  }

  private void blitBand(GuiGraphicsExtractor graphics, int band, int rightU, int rightX, int y, int v, int height) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, getGuiTexture(), this.leftPos, y, 0, v, band, height, 256, 256);
    graphics.blit(RenderPipelines.GUI_TEXTURED, getGuiTexture(), rightX, y, rightU, v, band, height, 256, 256);
  }

  protected abstract net.minecraft.resources.Identifier getGuiTexture();

  protected abstract int textureHeight();

  protected void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
    graphics.blit(RenderPipelines.GUI_TEXTURED, Constants.PROCESS, this.leftPos + x - 1, this.topPos + y - 1, SLOT_U,
        SLOT_V, 18, 18, 256, 256);
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiUtil.opaque(0x404040), false);
    graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
        GuiUtil.opaque(0x404040), false);
    if (help) {
      renderHelp(graphics);
      return;
    }
    if (isEditing()) {
      renderDetailLabels(graphics);
      return;
    }
    renderList(graphics, mouseX - this.leftPos, mouseY - this.topPos);
  }

  private void renderList(GuiGraphicsExtractor graphics, int relX, int relY) {
    int rows = visibleRows();
    int frameBottom = listFrameBottom() - 1;
    frame(graphics, LIST_X, LIST_Y - 2, LIST_W, frameBottom - LIST_Y + 3);

    int warningY = frameBottom + 5;
    for (FormattedCharSequence line : warningLines()) {
      graphics.text(this.font, line, LIST_X + (LIST_W - this.font.width(line)) / 2, warningY, GuiUtil.opaque(0x404040),
          false);
      warningY += 10;
    }
    if (visible.isEmpty()) {
      Component empty = Component.translatable(key(
          this.menu.entryCount() == 0 ? "recipes.empty" : "recipes.no_match"));
      graphics.text(this.font, empty, LIST_X + (LIST_W - this.font.width(empty)) / 2,
          LIST_Y + (rows * ROW_H - 8) / 2, GuiUtil.opaque(TEXT_DIM), false);
      return;
    }
    for (int i = 0; i < rows; i++) {
      int row = listScroll + i;
      if (row >= visible.size()) {
        break;
      }
      if (dragging && row == grabbedRow) {
        continue;
      }
      int y = LIST_Y + i * ROW_H;

      boolean hovered = !dragging && relX >= LIST_X
          && relX < (hasScrollBar() ? BAR_X : LIST_X + LIST_W)
          && relY >= y && relY < y + ROW_H;
      int copyState = !hovered ? 0 : copyIconHovered(relX, relY, y) ? 2 : 1;
      drawRow(graphics, visible.get(row), CARD_X, y, hovered ? CARD_HOVER_FILL : CARD_FILL,
          hovered ? CARD_HOVER_BORDER : CARD_BORDER, copyState);
    }
    if (dragging) {
      renderDrag(graphics);
    }
    if (hasScrollBar()) {
      graphics.fill(BAR_X, LIST_Y, BAR_X + 4, LIST_Y + trackHeight(), BAR_TRACK);
      graphics.fill(BAR_X, thumbY(), BAR_X + 4, thumbY() + thumbHeight(),
          draggingBar ? FRAME_DARK : BAR_THUMB);
    }
  }

  private void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
    graphics.fill(x, y, x + width, y + height, FRAME_FILL);
    graphics.fill(x, y, x + width, y + 1, FRAME_DARK);
    graphics.fill(x, y, x + 1, y + height, FRAME_DARK);
    graphics.fill(x, y + height - 1, x + width, y + height, FRAME_LIGHT);
    graphics.fill(x + width - 1, y, x + width, y + height, FRAME_LIGHT);
  }

  private void drawRow(GuiGraphicsExtractor graphics, int index, int x, int y, int fill, int border, int copyState) {
    int bottom = y + ROW_H - 2;
    graphics.fill(x, y, x + CARD_W, bottom, fill);
    graphics.fill(x, y, x + CARD_W, y + 1, border);
    graphics.fill(x, bottom - 1, x + CARD_W, bottom, border);
    graphics.fill(x, y, x + 1, bottom, border);
    graphics.fill(x + CARD_W - 1, y, x + CARD_W, bottom, border);
    ItemStack icon = entryIcon(index);
    if (!icon.isEmpty()) {
      graphics.item(icon, x + 2, y + 1);
    }
    graphics.text(this.font,
        net.minecraft.locale.Language.getInstance().getVisualOrder(
            this.font.substrByWidth(entryLabel(index), CARD_W - 40)),
        x + 22, y + 5, GuiUtil.opaque(TEXT), false);
    if (copyState > 0) {
      GuiCopyPasteButton.drawIcon(graphics, x + CARD_W - 15, y + 2, false, copyState == 2);
    }
  }

  private void renderDrag(GuiGraphicsExtractor graphics) {
    int rows = visibleRows();
    int gap = dropRow(dragY) - listScroll;
    int markY = LIST_Y + Math.max(0, Math.min(rows, gap)) * ROW_H - 1;
    graphics.fill(CARD_X, markY, CARD_X + CARD_W, markY + 2, DROP_MARK);
    int y = (int) Math.round(dragY - (this.height - getImageHeight()) / 2.0) - ROW_H / 2;
    y = Math.max(LIST_Y - ROW_H, Math.min(LIST_Y + rows * ROW_H, y));
    drawRow(graphics, visible.get(grabbedRow), CARD_X + 2, y, CARD_HELD_FILL, CARD_HOVER_BORDER, 0);
  }
}
