package com.faktocraft.client.screens;

import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.common.config.BasicConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public class BasicConfigScreen extends Screen {

  private static final int ROW_HEIGHT = 24;
  private static final int WIDTH = 260;

  private final Screen parent;

  public BasicConfigScreen(Screen parent) {
    super(Component.translatable("gui.faktocraft.config.title"));
    this.parent = parent;
  }

  private boolean serverEditable() {
    return BasicConfig.SERVER_SPEC.isLoaded()
        && minecraft != null
        && minecraft.hasSingleplayerServer();
  }

  @Override
  protected void init() {
    int left = (width - WIDTH) / 2;
    int y = 46;

    addToggle(left, y, "gui.faktocraft.config.show_pipe_supports",
        BasicConfig.CLIENT.showPipeSupports, BasicConfig.CLIENT_SPEC, true);
    y += ROW_HEIGHT;
    addToggle(left, y, "gui.faktocraft.config.notify_updates",
        BasicConfig.CLIENT.notifyUpdates, BasicConfig.CLIENT_SPEC, true);
    y += ROW_HEIGHT + 10;

    boolean editable = serverEditable();
    addToggle(left, y, "gui.faktocraft.config.teleport_anchor",
        BasicConfig.SERVER.teleportAnchorEnabled, BasicConfig.SERVER_SPEC, editable);
    y += ROW_HEIGHT;
    addToggle(left, y, "gui.faktocraft.config.chunk_loader",
        BasicConfig.SERVER.chunkLoaderEnabled, BasicConfig.SERVER_SPEC, editable);
    y += ROW_HEIGHT;
    addToggle(left, y, "gui.faktocraft.config.nuke",
        BasicConfig.SERVER.nukeEnabled, BasicConfig.SERVER_SPEC, editable);
    y += ROW_HEIGHT;
    addToggle(left, y, "gui.faktocraft.config.tank_break_fluid",
        BasicConfig.SERVER.tankBreakPlacesFluid, BasicConfig.SERVER_SPEC, editable);
    y += ROW_HEIGHT;
    addToggle(left, y, "gui.faktocraft.config.vein_mining",
        BasicConfig.SERVER.veinMiningEnabled, BasicConfig.SERVER_SPEC, editable);

    addRenderableWidget(Button.builder(Component.translatable("gui.done"),
        b -> onClose()).bounds(width / 2 - 100, height - 30, 200, 20).build());
  }

  private void addToggle(int x, int y, String key, ModConfigSpec.BooleanValue value,
      ModConfigSpec spec, boolean editable) {
    Button button = addRenderableWidget(Button.builder(Component.empty(), b -> {
      value.set(!value.get());
      spec.save();
      b.setMessage(label(key, value.get()));
    }).bounds(x, y, WIDTH, 20).build());

    if (!editable) {
      button.active = false;
      button.setMessage(label(key, spec.isLoaded() ? value.get() : true));
    } else {
      button.setMessage(label(key, value.get()));
    }
  }

  private static Component label(String key, boolean on) {
    return Component.translatable(key)
        .append(Component.literal(": "))
        .append(Component.translatable(on ? "gui.faktocraft.config.on" : "gui.faktocraft.config.off")
            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    graphics.centeredText(font, title, width / 2, 18, GuiUtil.opaque(0xFFFFFF));
    if (!serverEditable()) {
      graphics.centeredText(font,
          Component.translatable("gui.faktocraft.config.server_locked").withStyle(ChatFormatting.GRAY),
          width / 2, height - 48, GuiUtil.opaque(0xA0A0A0));
    }
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
  }

  @Override
  public void onClose() {
    if (minecraft != null) {
      minecraft.gui.setScreen(parent);
    }
  }
}
