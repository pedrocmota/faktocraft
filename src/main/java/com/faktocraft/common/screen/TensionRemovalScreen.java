package com.faktocraft.common.screen;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class TensionRemovalScreen extends Screen {

  private static final int BUTTON_WIDTH = 150;
  private static final int BUTTON_HEIGHT = 20;
  private static final int BUTTON_GAP = 10;
  private static final int MESSAGE_WIDTH = BUTTON_WIDTH * 2 + BUTTON_GAP;
  private static final int LINE_HEIGHT = 9;
  private static final int TITLE_GAP = 20;

  private final BooleanConsumer callback;
  private final Component message;
  private final Component yes;
  private final Component no;
  private MultiLineLabel label = MultiLineLabel.EMPTY;

  public TensionRemovalScreen(BooleanConsumer callback, Component title, Component message, Component yes,
      Component no) {
    super(title);
    this.callback = callback;
    this.message = message;
    this.yes = yes;
    this.no = no;
  }

  @Override
  public Component getNarrationMessage() {
    return CommonComponents.joinForNarration(super.getNarrationMessage(), message);
  }

  @Override
  protected void init() {
    super.init();
    label = MultiLineLabel.create(font, message, Math.min(width - 50, MESSAGE_WIDTH));
    int y = Mth.clamp(messageTop() + messageHeight() + TITLE_GAP, height / 6 + 96, height - 24);
    int left = width / 2 - BUTTON_WIDTH - BUTTON_GAP / 2;
    addRenderableWidget(Button.builder(yes, button -> callback.accept(true))
        .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    addRenderableWidget(Button.builder(no, button -> callback.accept(false))
        .bounds(left + BUTTON_WIDTH + BUTTON_GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    graphics.drawCenteredString(font, title, width / 2, titleTop(), 0xFFFFFF);
    label.renderCentered(graphics, width / 2, messageTop());
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  @Override
  public void onClose() {
    callback.accept(false);
  }

  private int titleTop() {
    return Mth.clamp((height - messageHeight()) / 2 - TITLE_GAP - LINE_HEIGHT, 10, 80);
  }

  private int messageTop() {
    return titleTop() + TITLE_GAP;
  }

  private int messageHeight() {
    return label.getLineCount() * LINE_HEIGHT;
  }
}
