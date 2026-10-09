package com.faktocraft.common.screen.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import java.util.function.Predicate;

public class FilteredEditBox extends EditBox {
  private Predicate<String> filter = text -> true;
  private int maxLength = 32;
  private int highlightPos;

  public FilteredEditBox(Font font, int x, int y, int width, int height, Component narration) {
    super(font, x, y, width, height, narration);
  }

  public void setFilter(Predicate<String> filter) {
    this.filter = filter;
  }

  @Override
  public void setMaxLength(int maxLength) {
    this.maxLength = maxLength;
    super.setMaxLength(maxLength);
  }

  @Override
  public void setHighlightPos(int pos) {
    super.setHighlightPos(pos);
    this.highlightPos = Mth.clamp(pos, 0, getValue().length());
  }

  @Override
  public void insertText(String input) {
    String value = getValue();
    int cursor = Mth.clamp(getCursorPosition(), 0, value.length());
    int highlight = Mth.clamp(highlightPos, 0, value.length());
    int start = Math.min(cursor, highlight);
    int end = Math.max(cursor, highlight);
    int room = maxLength - value.length() - (start - end);
    if (room > 0) {
      String text = StringUtil.filterText(input);
      if (room < text.length()) {
        text = text.substring(0, Character.isHighSurrogate(text.charAt(room - 1)) ? room - 1 : room);
      }
      String result = new StringBuilder(value).replace(start, end, text).toString();
      if (!result.isEmpty() && !filter.test(result)) {
        return;
      }
    }
    super.insertText(input);
  }

  @Override
  public void setValue(String value) {
    if (!value.isEmpty() && !filter.test(value)) {
      return;
    }
    super.setValue(value);
  }
}
