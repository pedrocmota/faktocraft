package com.faktocraft.common.screen;

import java.util.List;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;

public final class ClickRouting {

  private ClickRouting() {
  }

  public static boolean clickChildren(ContainerEventHandler handler, MouseButtonEvent event, boolean doubleClick) {
    for (GuiEventListener child : List.copyOf(handler.children())) {
      if (!child.isMouseOver(event.x(), event.y()) || !child.mouseClicked(event, doubleClick)) {
        continue;
      }
      if (child.shouldTakeFocusAfterInteraction()) {
        handler.setFocused(child);
        if (event.button() == 1) {
          handler.setDragging(true);
        }
      }
      return true;
    }
    return false;
  }
}
