package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.screen.widgets.FilteredEditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketReqTableState;
import com.faktocraft.common.network.packet.PacketRequestTarget;
import com.faktocraft.common.network.packet.PacketTableState;
import com.faktocraft.common.network.packet.PacketTaskHistoryOp;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ScreenRequestTable extends AbstractContainerScreen<MenuRequestTable> {

  private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/request_table.png");
  private static final Identifier BACKGROUND_TASKS = Identifier.fromNamespaceAndPath(Faktocraft.MODID,
      "textures/gui/container/request_table_tasks.png");

  private static final int PANEL_X = 176;
  private static final int PANEL_Y = 16;
  private static final int PANEL_RIGHT = 330;
  private static final int PANEL_BOTTOM = 158;
  private static final int GRID_X = 180;
  private static final int GRID_Y = 30;
  private static final int COLS = 8;
  private static final int ROWS = 7;
  private static final int PAGE_SIZE = COLS * ROWS;
  private static final int REFRESH_INTERVAL = 40;

  private static final int AUTO_W = 12;
  private static final int AUTO_H = 11;
  private static final int AUTO_X = 154;
  private static final int AUTO_Y = 88;

  private static final int TAB_Y = 17;
  private static final int TAB_H = 11;
  private static final float TAB_FONT_SCALE = 0.7f;
  private static final int TAB_MAX_W = 64;
  private static final int QTY_X = 202;
  private static final int QTY_Y = 193;
  private static final int QTY_BUTTON_W = 14;
  private static final int QTY_H = 14;
  private static final int QTY_FIELD_W = 36;
  private static final int QTY_TEXT_COLOR = 0x404040;
  private static final int MESSAGE_X = 176;
  private static final int MESSAGE_Y = 210;
  private static final int MESSAGE_W = 154;
  private static final int MESSAGE_LINES = 2;
  private static final long MESSAGE_MILLIS = 6000L;
  private static final int MESSAGE_ERROR_COLOR = 0xA02020;
  private static final int MESSAGE_INFO_COLOR = 0x404040;

  private enum Tab {
    REQUESTS, TASKS
  }

  private Tab tab = Tab.REQUESTS;
  private List<PacketTableState.Entry> all = List.of();
  private final List<PacketTableState.Entry> filtered = new ArrayList<>();
  private int page;
  private int quantity = 1;
  private int refreshTimer;
  private String query = "";
  private EditBox search;
  private Button autoExtractButton;
  private boolean autoExtractShown;
  private Button minusButton;
  private Button plusButton;
  private QuantityBox quantityBox;
  private Button requestButton;
  private List<net.minecraft.util.FormattedCharSequence> messageLines = List.of();
  private int messageColor;
  private long messageUntil;
  private TaskListPanel taskPanel;

  public ScreenRequestTable(MenuRequestTable menu, Inventory inventory, Component title) {
    super(menu, inventory, title, 340, 256);
    this.inventoryLabelX = 8;
    this.inventoryLabelY = 161;
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

    autoExtractButton = addRenderableWidget(new Button(left + AUTO_X, top + AUTO_Y, AUTO_W, AUTO_H,
        autoExtractLabel(), b -> {
          com.faktocraft.common.network.ModNetworking.sendToServer(
              new com.faktocraft.common.network.packet.PacketMenuAction(this.menu.containerId,
                  MenuRequestTable.BUTTON_AUTO_EXTRACT));
          requestState();
        }, supplier -> supplier.get()) {

      @Override
      protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean on = ScreenRequestTable.this.menu.isAutoExtract();
        boolean hovered = isHoveredOrFocused();
        drawArrow(graphics, getX() + 1, getY() + 1, 0x50000000);
        drawArrow(graphics, getX(), getY(),
            on ? (hovered ? 0xFF5FE377 : 0xFF3CB54C) : (hovered ? 0xFF8A8A8A : 0xFF5E5E5E));
      }
    });
    autoExtractButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(autoExtractTooltip()));

    minusButton = addRenderableWidget(Button.builder(Component.literal("-"),
        b -> stepQuantity(-1)).bounds(left + QTY_X, top + QTY_Y, QTY_BUTTON_W, QTY_H).build());
    plusButton = addRenderableWidget(Button.builder(Component.literal("+"),
        b -> stepQuantity(1)).bounds(left + QTY_X + QTY_BUTTON_W + QTY_FIELD_W, top + QTY_Y, QTY_BUTTON_W, QTY_H)
        .build());
    quantityBox = new QuantityBox(this.font, left + QTY_X + QTY_BUTTON_W, top + QTY_Y, QTY_FIELD_W, QTY_H);
    quantityBox.setValue(String.valueOf(quantity));
    addRenderableWidget(quantityBox);

    requestButton = addRenderableWidget(Button.builder(Component.translatable(key("request_button")),
        b -> request()).bounds(left + 176, top + 230, 154, 18).build());

    search = new EditBox(this.font, left + 180, top + 176, 146, 10, Component.empty());
    search.setBordered(false);
    search.setMaxLength(48);
    search.setTextColor(0xFFFFFFFF);
    search.setValue(query);
    search.setHint(Component.translatable(key("search_hint")).withStyle(ChatFormatting.DARK_GRAY));
    search.setResponder(text -> {
      query = text;
      refilter();
    });
    addRenderableWidget(search);

    if (taskPanel == null) {
      taskPanel = new TaskListPanel(this.minecraft, this.font, false, new TaskListPanel.Host() {
        @Override
        public void sendHistoryOp(int mode, long id) {
          ModNetworking.sendToServer(new PacketTaskHistoryOp(
              ScreenRequestTable.this.menu.getTablePos(), dimensionId(), mode, id));
        }

        @Override
        public void requestRefresh() {
          requestState();
        }
      });
    }
    taskPanel.initWidgets(left, top, this::addRenderableWidget);

    applyTabVisibility();
    requestState();
    refreshTimer = REFRESH_INTERVAL;
  }

  public boolean isRequestsTab() {
    return tab == Tab.REQUESTS;
  }

  private void switchTab(Tab newTab) {
    tab = newTab;
    applyTabVisibility();
  }

  private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, int color) {
    graphics.fill(x + 4, y, x + 8, y + 6, color);
    for (int row = 0; row < 5; row++) {
      graphics.fill(x + 1 + row, y + 6 + row, x + 11 - row, y + 7 + row, color);
    }
  }

  private Component autoExtractTooltip() {
    return Component.empty()
        .append(autoExtractLabel())
        .append("\n")
        .append(Component.translatable(key("table.auto_extract_tip")).withStyle(ChatFormatting.GRAY));
  }

  private Component autoExtractLabel() {
    return Component.translatable(key("table.auto_extract"))
        .append(": ")
        .append(Component.translatable(key(this.menu.isAutoExtract() ? "on" : "off")));
  }

  private void applyTabVisibility() {
    boolean main = tab == Tab.REQUESTS;
    search.setVisible(main);
    autoExtractButton.visible = main;
    minusButton.visible = main;
    plusButton.visible = main;
    quantityBox.setVisible(main);
    if (!main) {
      quantityBox.setFocused(false);
      if (getFocused() == quantityBox) {
        setFocused(null);
      }
    }
    requestButton.visible = main;
    taskPanel.setVisible(!main);
  }

  private String dimensionId() {
    return this.minecraft != null && this.minecraft.level != null
        ? this.minecraft.level.dimension().identifier().toString()
        : "minecraft:overworld";
  }

  private Component tabLabel(Tab which) {
    return Component.translatable(key(which == Tab.REQUESTS ? "tab_main" : "tab_tasks"));
  }

  private int tabWidth(Tab which) {
    int text = (int) Math.ceil(this.font.width(tabLabel(which)) * TAB_FONT_SCALE);
    return Math.min(TAB_MAX_W, Math.max(32, text + 9));
  }

  private int tabX(Tab which) {
    int left = (this.width - this.imageWidth) / 2;
    return which == Tab.REQUESTS ? left + 8 : left + 8 + tabWidth(Tab.REQUESTS) + 2;
  }

  private boolean isOverTab(double mouseX, double mouseY, Tab which) {
    int top = (this.height - this.imageHeight) / 2;
    int x = tabX(which);
    return mouseX >= x && mouseX < x + tabWidth(which)
        && mouseY >= top + TAB_Y && mouseY < top + TAB_Y + TAB_H;
  }

  private void renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int top = (this.height - this.imageHeight) / 2;
    for (Tab which : Tab.values()) {
      int x = tabX(which);
      int w = tabWidth(which);
      int y = top + TAB_Y;
      boolean selected = tab == which;
      boolean hovered = !selected && isOverTab(mouseX, mouseY, which);
      graphics.fill(x - 1, y - 1, x + w + 1, y + TAB_H + 1, 0xFF000000);
      graphics.fill(x, y, x + w, y + TAB_H, selected ? 0xFF2E2E2E : (hovered ? 0xFFB8B8B8 : 0xFF9E9E9E));
      if (!selected) {
        graphics.fill(x, y, x + w, y + 1, 0xFFD8D8D8);
        graphics.fill(x, y, x + 1, y + TAB_H, 0xFFD8D8D8);
        graphics.fill(x, y + TAB_H - 1, x + w, y + TAB_H, 0xFF5E5E5E);
        graphics.fill(x + w - 1, y, x + w, y + TAB_H, 0xFF5E5E5E);
      }
      Component label = tabLabel(which);
      float scale = TAB_FONT_SCALE;
      int rawWidth = this.font.width(label);
      if (rawWidth * scale > w - 6) {
        scale = (w - 6) / (float) rawWidth;
      }
      float textWidth = rawWidth * scale;
      float textHeight = 8 * scale;
      graphics.pose().pushMatrix();
      graphics.pose().translate(x + (w - textWidth) / 2.0f, y + 1 + (TAB_H - 1 - textHeight) / 2.0f);
      graphics.pose().scale(scale, scale);
      graphics.text(this.font, label, 0, 0, GuiUtil.opaque(selected ? 0xFFFFFF : 0x3A3A3A), selected);
      graphics.pose().popMatrix();
    }
  }

  private int step() {
    if (GuiUtil.hasControlDown()) {
      return 64;
    }
    return GuiUtil.hasShiftDown() ? 16 : 1;
  }

  private void stepQuantity(int direction) {
    int step = step();
    int next;
    if (step == 1) {
      next = quantity + direction;
    } else if (direction > 0) {
      next = (quantity / step + 1) * step;
    } else {
      next = (quantity - 1) / step * step;
    }
    setQuantity(next);
  }

  private void setQuantity(int value) {
    quantity = Math.max(1, Math.min(MenuRequestTable.MAX_REQUEST, value));
    if (quantityBox != null && !quantityBox.getValue().equals(String.valueOf(quantity))) {
      quantityBox.setValue(String.valueOf(quantity));
    }
  }

  private boolean quantityTyped(String text) {
    if (text.isEmpty()) {
      return true;
    }
    if (text.length() > String.valueOf(MenuRequestTable.MAX_REQUEST).length()) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) < '0' || text.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }

  private void quantityChanged(String text) {
    if (text.isEmpty()) {
      return;
    }
    int parsed = Integer.parseInt(text);
    quantity = Math.max(1, Math.min(MenuRequestTable.MAX_REQUEST, parsed));
    if (parsed > MenuRequestTable.MAX_REQUEST) {
      quantityBox.setValue(String.valueOf(quantity));
    }
  }

  private void commitQuantity() {
    if (quantityBox != null) {
      String expected = String.valueOf(quantity);
      if (!quantityBox.getValue().equals(expected)) {
        quantityBox.setValue(expected);
      }
    }
  }

  private PacketTableState.Entry selectedEntry() {
    ItemStack ghost = this.menu.getGhostStack();
    if (ghost.isEmpty()) {
      return null;
    }
    ItemKey target = ItemKey.of(ghost);
    for (PacketTableState.Entry entry : all) {
      if (target.matches(entry.stack())) {
        return entry;
      }
    }
    return null;
  }

  private boolean canRequest() {
    PacketTableState.Entry entry = selectedEntry();
    if (entry == null || entry.blocked()) {
      return false;
    }
    return entry.count() >= quantity || entry.craftableOnly();
  }

  private void updateControls() {
    minusButton.active = quantity > 1;
    plusButton.active = quantity < MenuRequestTable.MAX_REQUEST;
    requestButton.active = canRequest();
  }

  private void request() {
    if (!canRequest()) {
      return;
    }
    if (this.minecraft != null && this.minecraft.gameMode != null) {

      com.faktocraft.common.network.ModNetworking.sendToServer(
          new com.faktocraft.common.network.packet.PacketMenuAction(this.menu.containerId,
              MenuRequestTable.BUTTON_REQUEST_BASE + quantity));

      requestState();
      refreshTimer = 10;
    }
  }

  public boolean matches(BlockPos pos) {
    return pos.equals(this.menu.getTablePos());
  }

  public void showMessage(Component message, boolean error) {
    List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(message, MESSAGE_W);
    messageLines = lines.size() > MESSAGE_LINES ? lines.subList(0, MESSAGE_LINES) : lines;
    messageColor = error ? MESSAGE_ERROR_COLOR : MESSAGE_INFO_COLOR;
    messageUntil = Util.getMillis() + MESSAGE_MILLIS;
  }

  private void renderMessage(GuiGraphicsExtractor graphics) {
    if (messageLines.isEmpty()) {
      return;
    }
    if (Util.getMillis() >= messageUntil) {
      messageLines = List.of();
      return;
    }
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    int y = top + MESSAGE_Y;
    for (net.minecraft.util.FormattedCharSequence line : messageLines) {
      int x = left + MESSAGE_X + (MESSAGE_W - this.font.width(line)) / 2;
      graphics.text(this.font, line, x, y, GuiUtil.opaque(messageColor), false);
      y += this.font.lineHeight;
    }
  }

  public void applyState(PacketTableState state) {
    List<PacketTableState.Entry> sorted = new ArrayList<>(state.entries());

    sorted.sort(Comparator
        .comparingInt((PacketTableState.Entry entry) -> -entry.count())
        .thenComparing(entry -> entry.blocked() ? 1 : 0)
        .thenComparing(entry -> entry.stack().getHoverName().getString()));
    all = sorted;
    taskPanel.setData(state.tasks(), state.errors());
    refilter();
  }

  private void refilter() {
    filtered.clear();
    String needle = query.toLowerCase(Locale.ROOT).trim();
    for (PacketTableState.Entry entry : all) {
      if (needle.isEmpty()
          || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
        filtered.add(entry);
      }
    }
    page = Math.min(page, maxPage());
  }

  private int maxPage() {
    return Math.max(0, (filtered.size() - 1) / PAGE_SIZE);
  }

  private void turnPage(int direction) {
    page = Math.max(0, Math.min(maxPage(), page + direction));
  }

  private void requestState() {
    ModNetworking.sendToServer(new PacketReqTableState(this.menu.getTablePos(), dimensionId()));
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    if (autoExtractButton != null && autoExtractShown != this.menu.isAutoExtract()) {
      autoExtractShown = this.menu.isAutoExtract();
      autoExtractButton.setMessage(autoExtractLabel());
      autoExtractButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(autoExtractTooltip()));
    }
    if (--refreshTimer <= 0) {
      requestState();
      refreshTimer = REFRESH_INTERVAL;
    }
  }

  private int cellIndexAt(double mouseX, double mouseY) {
    if (tab != Tab.REQUESTS) {
      return -1;
    }
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    int col = (int) Math.floor((mouseX - (left + GRID_X)) / 18.0);
    int row = (int) Math.floor((mouseY - (top + GRID_Y)) / 18.0);
    if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
      return -1;
    }
    int index = page * PAGE_SIZE + row * COLS + col;
    return index < filtered.size() ? index : -1;
  }

  private boolean overPanel(double mouseX, double mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    return mouseX >= left + PANEL_X && mouseX < left + PANEL_RIGHT
        && mouseY >= top + PANEL_Y && mouseY < top + PANEL_BOTTOM;
  }

  private static String compactCount(int count) {
    if (count >= 1_000_000) {
      return (count / 1_000_000) + "M";
    }
    if (count >= 10_000) {
      return (count / 1_000) + "k";
    }
    if (count >= 1_000) {
      return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
    }
    return String.valueOf(count);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    if (tab == Tab.TASKS) {
      this.hoveredSlot = null;
      graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TASKS, left, top, 0, 0, this.imageWidth, this.imageHeight,
          512, 256);
      taskPanel.render(graphics, left, top, mouseX, mouseY);
      for (Renderable renderable : this.renderables) {
        renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
      }
      graphics.text(this.font, this.title, left + 8, top + 6, GuiUtil.opaque(0x404040), false);
      renderTabs(graphics, mouseX, mouseY);
      taskPanel.renderTooltip(graphics, left, top, mouseX, mouseY);
      extractCarriedItem(graphics, mouseX, mouseY);
      return;
    }
    updateControls();
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    renderBrowserTooltip(graphics, mouseX, mouseY);
  }

  @Override
  public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    renderBg(graphics, partialTick, mouseX, mouseY);
    super.extractContents(graphics, mouseX, mouseY, partialTick);
    renderTabs(graphics, mouseX, mouseY);
    renderBrowser(graphics, mouseX, mouseY);
    renderPagination(graphics, mouseX, mouseY);
    renderMessage(graphics);
  }

  private static final String PAGER_CHARS = "0123456789/";

  private static final int[][] PAGER_GLYPHS = {
      { 7, 5, 5, 5, 7 }, { 2, 6, 2, 2, 7 }, { 7, 1, 7, 4, 7 }, { 7, 1, 7, 1, 7 }, { 5, 5, 7, 1, 1 },
      { 7, 4, 7, 1, 7 }, { 7, 4, 7, 5, 7 }, { 7, 1, 2, 2, 2 }, { 7, 5, 7, 5, 7 }, { 7, 5, 7, 1, 7 },
      { 1, 1, 2, 4, 4 }
  };

  private void drawTinyText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
    for (char character : text.toCharArray()) {
      int index = PAGER_CHARS.indexOf(character);
      if (index >= 0) {
        int[] glyph = PAGER_GLYPHS[index];
        for (int row = 0; row < 5; row++) {
          for (int col = 0; col < 3; col++) {
            if ((glyph[row] & (4 >> col)) != 0) {
              graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
          }
        }
      }
      x += 4;
    }
  }

  private void drawPagerButton(GuiGraphicsExtractor graphics, int x, int y, boolean rightArrow, boolean hovered,
      boolean enabled) {
    graphics.fill(x, y, x + 8, y + 8, hovered && enabled ? 0xFFCFCFCF : 0xFFB4B4B4);
    graphics.fill(x, y, x + 8, y + 1, 0xFFE4E4E4);
    graphics.fill(x, y, x + 1, y + 8, 0xFFE4E4E4);
    graphics.fill(x, y + 7, x + 8, y + 8, 0xFF6B6B6B);
    graphics.fill(x + 7, y, x + 8, y + 8, 0xFF6B6B6B);
    int color = enabled ? 0xFF3A3A3A : 0xFF9A9A9A;
    if (rightArrow) {
      graphics.fill(x + 3, y + 2, x + 4, y + 7, color);
      graphics.fill(x + 4, y + 3, x + 5, y + 6, color);
      graphics.fill(x + 5, y + 4, x + 6, y + 5, color);
    } else {
      graphics.fill(x + 4, y + 2, x + 5, y + 7, color);
      graphics.fill(x + 3, y + 3, x + 4, y + 6, color);
      graphics.fill(x + 2, y + 4, x + 3, y + 5, color);
    }
  }

  private int[] pagerLayout() {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    String pages = (page + 1) + "/" + (maxPage() + 1);
    int numberWidth = pages.length() * 4 - 1;
    int rightButton = left + PANEL_RIGHT - 4 - 8;
    int numberX = rightButton - 3 - numberWidth;
    int leftButton = numberX - 3 - 8;
    return new int[] { leftButton, rightButton, top + 19, numberX };
  }

  private void renderPagination(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    boolean single = maxPage() <= 0;
    int[] layout = pagerLayout();
    drawPagerButton(graphics, layout[0], layout[2], false, isOverPager(mouseX, mouseY, -1), !single);
    drawPagerButton(graphics, layout[1], layout[2], true, isOverPager(mouseX, mouseY, 1), !single);
    String pages = (page + 1) + "/" + (maxPage() + 1);
    drawTinyText(graphics, pages, layout[3], layout[2] + 2, single ? 0xFF6E6E6E : 0xFF2E2E2E);
  }

  private boolean isOverPager(double mouseX, double mouseY, int direction) {
    int[] layout = pagerLayout();
    int x = direction < 0 ? layout[0] : layout[1];
    return mouseX >= x && mouseX < x + 8 && mouseY >= layout[2] && mouseY < layout[2] + 8;
  }

  private void renderBrowser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    int start = page * PAGE_SIZE;

    ItemKey target = ItemKey.of(this.menu.getGhostStack());
    for (int i = 0; i < PAGE_SIZE && start + i < filtered.size(); i++) {
      PacketTableState.Entry entry = filtered.get(start + i);
      int x = left + GRID_X + (i % COLS) * 18;
      int y = top + GRID_Y + (i / COLS) * 18;
      boolean selected = target.matches(entry.stack());
      if (selected) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x8033AAFF);
      }
      graphics.item(entry.stack(), x, y);
      graphics.itemDecorations(this.font, entry.stack(), x, y, "");
      if (entry.blocked()) {

        graphics.fill(x, y, x + 16, y + 16, 0xB0787878);
      }
      String text = entry.count() > 0 ? compactCount(entry.count()) : "C";
      int color = entry.blocked() ? 0xFF5555 : (entry.craftableOnly() ? 0xFFA000 : 0xFFFFFF);
      graphics.text(this.font, text, x + 17 - this.font.width(text), y + 9, GuiUtil.opaque(color), true);
    }
    int hovered = cellIndexAt(mouseX, mouseY);
    if (hovered >= 0) {
      int x = left + GRID_X + ((hovered - start) % COLS) * 18;
      int y = top + GRID_Y + ((hovered - start) / COLS) * 18;
      graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
    }
  }

  private void renderBrowserTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    int hovered = cellIndexAt(mouseX, mouseY);
    if (hovered < 0 || !this.menu.getCarried().isEmpty() || this.minecraft == null) {
      return;
    }
    PacketTableState.Entry entry = filtered.get(hovered);
    List<Component> tooltip = new ArrayList<>(getTooltipFromContainerItem(entry.stack()));
    if (entry.count() > 0) {
      tooltip.add(Component.translatable(key("in_network"), entry.count()).withStyle(ChatFormatting.GRAY));
    }
    if (entry.blocked()) {
      tooltip.add(Component.translatable(key("craft_blocked")).withStyle(ChatFormatting.RED));
      tooltip.add(Component.translatable(key("craft_missing"), entry.missing().getHoverName())
          .withStyle(ChatFormatting.GRAY));
    } else if (entry.craftableOnly()) {
      tooltip.add(Component.translatable(key("craftable")).withStyle(ChatFormatting.GOLD));
    }
    graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mouseX = event.x();
    double mouseY = event.y();
    int button = GuiUtil.legacyButton(event);
    for (Tab which : Tab.values()) {
      if (isOverTab(mouseX, mouseY, which)) {
        if (tab != which) {
          switchTab(which);
          if (this.minecraft != null) {
            this.minecraft.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
          }
        }
        return true;
      }
    }
    if (tab == Tab.TASKS) {
      int left = (this.width - this.imageWidth) / 2;
      int top = (this.height - this.imageHeight) / 2;
      if (taskPanel.mouseClicked(left, top, mouseX, mouseY)) {
        return true;
      }
      for (GuiEventListener child : children()) {
        if (child.mouseClicked(event, doubleClick)) {
          setFocused(child);
          return true;
        }
      }
      return true;
    }
    if (quantityBox.isFocused() && !quantityBox.isMouseOver(mouseX, mouseY)) {
      quantityBox.setFocused(false);
      if (getFocused() == quantityBox) {
        setFocused(null);
      }
    }
    if (isOverPager(mouseX, mouseY, -1)) {
      turnPage(-1);
      return true;
    }
    if (isOverPager(mouseX, mouseY, 1)) {
      turnPage(1);
      return true;
    }
    int hovered = cellIndexAt(mouseX, mouseY);
    if (hovered >= 0 && filtered.get(hovered).blocked()) {

      return true;
    }
    if (hovered >= 0 && button == 0 && this.menu.getCarried().isEmpty()) {
      ItemStack target = filtered.get(hovered).stack().copyWithCount(1);
      ModNetworking.sendToServer(new PacketRequestTarget(this.menu.getTablePos(), target));
      if (this.minecraft != null) {
        this.minecraft.getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
      return true;
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
    if (tab == Tab.TASKS) {
      taskPanel.mouseScrolled(delta);
      return true;
    }
    if (overPanel(mouseX, mouseY)) {
      turnPage(delta < 0 ? 1 : -1);
      return true;
    }
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    int qtyWidth = QTY_BUTTON_W * 2 + QTY_FIELD_W;
    if (mouseX >= left + QTY_X && mouseX < left + QTY_X + qtyWidth && mouseY >= top + QTY_Y
        && mouseY < top + QTY_Y + QTY_H) {
      stepQuantity(delta > 0 ? 1 : -1);
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    int keyCode = event.key();
    EditBox taskSearch = taskPanel != null ? taskPanel.searchBox() : null;
    EditBox focused = search != null && search.isFocused() && search.isVisible() ? search
        : quantityBox != null && quantityBox.isFocused() && quantityBox.isVisible() ? quantityBox
            : taskSearch != null && taskSearch.isFocused() && taskSearch.isVisible() ? taskSearch : null;
    if (focused != null) {
      boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
      if (keyCode == InputConstants.KEY_ESCAPE || (enter && focused == quantityBox)) {
        focused.setFocused(false);
        if (getFocused() == focused) {
          setFocused(null);
        }
        return true;
      }
      if (focused.keyPressed(event)) {
        return true;
      }
      return keyCode != InputConstants.KEY_TAB;
    }
    return super.keyPressed(event);
  }

  @Override
  protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    super.extractLabels(graphics, mouseX, mouseY);
    graphics.text(this.font, Component.translatable(key("storage_label")), 8, 93, GuiUtil.opaque(0x404040), false);
  }

  @Override
  public void removed() {
    if (this.minecraft != null) {
      com.faktocraft.client.GuiScaleHelper.restore(this.minecraft);
    }
    super.removed();
  }

  protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
    int left = (this.width - this.imageWidth) / 2;
    int top = (this.height - this.imageHeight) / 2;
    graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, 512,
        256);
  }

  private final class QuantityBox extends FilteredEditBox {

    private final Font boxFont;

    QuantityBox(Font font, int x, int y, int width, int height) {
      super(font, x, y, width, height, Component.empty());
      this.boxFont = font;
      setBordered(false);
      setMaxLength(String.valueOf(MenuRequestTable.MAX_REQUEST).length());
      setFilter(ScreenRequestTable.this::quantityTyped);
      setResponder(ScreenRequestTable.this::quantityChanged);
    }

    @Override
    public void setFocused(boolean focused) {
      boolean was = isFocused();
      super.setFocused(focused);
      if (focused) {
        moveCursorToEnd(false);
        setHighlightPos(getCursorPosition());
      } else if (was) {
        commitQuantity();
      }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
      moveCursorToEnd(false);
      setHighlightPos(getCursorPosition());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      if (!isVisible()) {
        return;
      }
      String value = getValue();
      int textWidth = boxFont.width(value);
      int x = getX() + (getWidth() - textWidth) / 2;
      int y = getY() + (getHeight() - boxFont.lineHeight) / 2 + 1;
      graphics.text(boxFont, value, x, y, GuiUtil.opaque(QTY_TEXT_COLOR), false);
      if (isFocused() && (Util.getMillis() / 300L) % 2L == 0L) {
        int cursorX = x + boxFont.width(value.substring(0, getCursorPosition()));
        graphics.fill(cursorX, y - 1, cursorX + 1, y + boxFont.lineHeight, 0xFF000000 | QTY_TEXT_COLOR);
      }
    }
  }
}
