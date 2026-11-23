package com.faktocraft.common.screen;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class TensionRemovalScreen extends ConfirmScreen {

  private static final int MESSAGE_WIDTH = 304;

  public TensionRemovalScreen(BooleanConsumer callback, Component title, Component message, Component yes,
      Component no) {
    super(callback, title, message, yes, no);
  }

  @Override
  protected LayoutElement addMessage() {
    return new MultiLineTextWidget(this.message, this.font).setMaxWidth(Math.min(this.width - 50, MESSAGE_WIDTH))
        .setMaxRows(15).setCentered(true);
  }
}
