package com.faktocraft.client;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.config.BasicConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

public final class UpdateNotice {
  private static final int WAIT_TICKS = 20 * 30;

  private static boolean shown;
  private static int pendingTicks;

  private UpdateNotice() {
  }

  public static void onLogin() {
    if (shown || !BasicConfig.notifyUpdates()) {
      return;
    }
    pendingTicks = WAIT_TICKS;
  }

  public static void tick(@Nullable LocalPlayer player) {
    if (pendingTicks <= 0 || player == null) {
      return;
    }
    IModInfo info = modInfo();
    VersionChecker.CheckResult result = info != null ? VersionChecker.getResult(info) : null;
    if (result == null || result.status() == VersionChecker.Status.PENDING) {
      pendingTicks--;
      return;
    }
    pendingTicks = 0;
    shown = true;
    if (!result.status().shouldDraw() || result.target() == null) {
      return;
    }
    player.sendSystemMessage(message(info, result));
  }

  @Nullable
  private static IModInfo modInfo() {
    return ModList.get().getModContainerById(Faktocraft.MODID).map(container -> container.getModInfo()).orElse(null);
  }

  private static Component message(IModInfo info, VersionChecker.CheckResult result) {
    Component text = Component.translatable("faktocraft.update.available",
        result.target().toString(), info.getVersion().toString()).withStyle(ChatFormatting.YELLOW);
    String url = result.url() != null ? result.url() : info.getModURL().map(Object::toString).orElse(null);
    if (url == null) {
      return text;
    }
    Component link = Component.translatable("faktocraft.update.link").withStyle(style -> style
        .withColor(ChatFormatting.AQUA)
        .withUnderlined(true)
        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
    return text.copy().append(" ").append(link);
  }
}
