package com.faktocraft.gametest.gui;

import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.MockPlayers;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

public class GuiSweepGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
  private static final BlockPos PLAYER = new BlockPos(3, 1, 1);
  private static final int SAMPLE_EVERY = 40;

  @SuppressWarnings("unchecked")
  private static int dataSlotCount(AbstractContainerMenu menu) {
    try {
      Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
      field.setAccessible(true);
      return ((List<DataSlot>) field.get(menu)).size();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String describe(Slot slot) {
    return slot.getClass().getSimpleName() + "@" + slot.x + "," + slot.y + "#" + slot.getContainerSlot()
        + "/" + slot.container.getClass().getSimpleName();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void everyMachineOpensItsMenuFromARightClick(GameTestHelper helper) {
    List<Block> blocks = GuiProbes.menuBlocks();
    BlockPos playerAbs = helper.absolutePos(PLAYER);
    MockPlayers.Mock mock = MockPlayers.survival(helper, playerAbs.getX() + 0.5, playerAbs.getY(),
        playerAbs.getZ() + 0.5);
    mock.allowAllPayloads();
    BlockSweep.Report report = new BlockSweep.Report("open");
    BlockSweep.forEachBlock(helper, MACHINE, blocks, report, block -> checkOpen(helper, mock, block, report),
        mock::remove);
  }

  private static void checkOpen(GameTestHelper helper, MockPlayers.Mock mock, Block block,
      BlockSweep.Report report) {
    ServerPlayer player = mock.player();
    ServerLevel level = helper.getLevel();
    BlockPos abs = helper.absolutePos(MACHINE);
    mock.flushAndDrainOutbound();
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.5, 0.0, 0.0), Direction.EAST, abs, false);
    InteractionResult result = player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
        hit);
    if (player.containerMenu == player.inventoryMenu) {
      ItemStack wrench = new ItemStack(ModItems.WRENCH);
      player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
      result = player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
    AbstractContainerMenu server = player.containerMenu;
    try {
      if (server == player.inventoryMenu) {
        report.add(block, "a right click (empty hand or wrench) did not open any menu (" + result + ")");
        return;
      }
      AdvancedOpenScreenPayload payload = null;
      boolean plainOpen = false;
      for (Object message : mock.flushAndDrainOutbound()) {
        if (message instanceof ClientboundCustomPayloadPacket packet
            && packet.payload() instanceof AdvancedOpenScreenPayload open) {
          payload = open;
        }
        if (message instanceof ClientboundOpenScreenPacket) {
          plainOpen = true;
        }
      }
      if (payload == null) {
        report.add(block, plainOpen ? "menu opened without the block position (client cannot build it)"
            : "no open screen packet was sent");
        return;
      }
      if (payload.menuType() != server.getType()) {
        report.add(block, "open packet names menu type " + payload.menuType() + " but the server opened "
            + server.getType());
      }
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.additionalData()),
          level.registryAccess(), ConnectionType.NEOFORGE);
      AbstractContainerMenu client;
      try {
        client = payload.menuType().create(payload.windowId(), player.getInventory(), buf);
        if (buf.readableBytes() != 0) {
          report.add(block, "client menu left " + buf.readableBytes() + " unread byte(s) of open data");
        }
      } catch (RuntimeException e) {
        report.add(block, "client side menu failed to build: " + e);
        return;
      } finally {
        buf.release();
      }
      if (client.slots.size() != server.slots.size()) {
        report.add(block, "client menu has " + client.slots.size() + " slots, server menu " + server.slots.size());
      } else {
        for (int i = 0; i < server.slots.size(); i++) {
          String s = describe(server.slots.get(i));
          String c = describe(client.slots.get(i));
          if (!s.equals(c)) {
            report.add(block, "slot " + i + " differs: server " + s + " client " + c);
          }
        }
      }
      if (dataSlotCount(client) != dataSlotCount(server)) {
        report.add(block, "client menu tracks " + dataSlotCount(client) + " data slots, server "
            + dataSlotCount(server));
      }
      if (!server.stillValid(player)) {
        report.add(block, "menu reports itself invalid right after opening (would close at once)");
      }
      server.broadcastChanges();
    } finally {
      if (player.containerMenu != player.inventoryMenu) {
        player.closeContainer();
      }
    }
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void shiftClicksKeepEveryItem(GameTestHelper helper) {
    List<Block> blocks = GuiProbes.menuBlocks();
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    BlockSweep.Report report = new BlockSweep.Report("shift");
    BlockSweep.forEachBlock(helper, MACHINE, blocks, report,
        block -> checkShiftClicks(helper, player, block, report), () -> {
        });
  }

  private static AbstractContainerMenu menuFor(GameTestHelper helper, Player player, Block block) {
    return ((IHasMenu) block).getMenu(1, helper.getLevel(), helper.absolutePos(MACHINE), player.getInventory(),
        player);
  }

  private static boolean quickMove(AbstractContainerMenu menu, Player player, int index) {
    Slot slot = menu.slots.get(index);
    if (!slot.mayPickup(player)) {
      return true;
    }
    ItemStack clicked = menu.quickMoveStack(player, index);
    int guard = 0;
    while (!clicked.isEmpty() && ItemStack.isSameItem(slot.getItem(), clicked)) {
      if (++guard > 64) {
        return false;
      }
      clicked = menu.quickMoveStack(player, index);
    }
    return true;
  }

  private static void checkSlotRules(AbstractContainerMenu menu, Player player, Block block, ItemStack like,
      String probe, String action, BlockSweep.Report report) {
    for (int index : GuiProbes.machineSlots(menu, player)) {
      Slot slot = menu.slots.get(index);
      ItemStack stack = slot.getItem();
      if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, like)) {
        continue;
      }
      if (!slot.mayPlace(stack)) {
        report.add(block, action + " put " + probe + " into slot " + index + " which refuses it");
      }
      if (stack.getCount() > slot.getMaxStackSize(stack)) {
        report.add(block, action + " put " + stack.getCount() + "x " + probe + " into slot " + index
            + " (limit " + slot.getMaxStackSize(stack) + ")");
      }
    }
  }

  private static void checkShiftClicks(GameTestHelper helper, Player player, Block block,
      BlockSweep.Report report) {
    AbstractContainerMenu menu = menuFor(helper, player, block);
    if (menu == null) {
      report.add(block, "getMenu returned null");
      return;
    }
    int invIndex = GuiProbes.playerSlotIndex(menu, player, 9);
    if (invIndex < 0) {
      return;
    }
    List<Integer> machine = GuiProbes.machineSlots(menu, player);
    List<GuiProbes.Probe> probes = GuiProbes.all();
    try {
      for (int p = 0; p < probes.size(); p++) {
        GuiProbes.Probe probe = probes.get(p);
        ItemStack sample = probe.create();
        boolean accepted = false;
        for (int index : machine) {
          if (menu.slots.get(index).mayPlace(sample)) {
            accepted = true;
            break;
          }
        }
        if (!accepted && p % SAMPLE_EVERY != 0) {
          continue;
        }
        GuiProbes.clear(menu, player);
        ItemStack full = probe.full();
        menu.slots.get(invIndex).set(full.copy());
        int before = GuiProbes.count(menu, full);
        if (!quickMove(menu, player, invIndex)) {
          report.add(block, "shift-clicking " + probe.key() + " from the inventory never stops");
          continue;
        }
        int after = GuiProbes.count(menu, full);
        if (after != before) {
          report.add(block, "shift-clicking " + before + "x " + probe.key() + " from the inventory left " + after);
        }
        checkSlotRules(menu, player, block, full, probe.key(), "shift-click", report);
      }
      for (int index : machine) {
        Slot slot = menu.slots.get(index);
        GuiProbes.Probe first = null;
        for (GuiProbes.Probe probe : probes) {
          if (slot.mayPlace(probe.create())) {
            first = probe;
            break;
          }
        }
        if (first == null) {
          continue;
        }
        GuiProbes.clear(menu, player);
        ItemStack one = first.create();
        slot.set(one.copy());
        int before = GuiProbes.count(menu, one);
        if (!quickMove(menu, player, index)) {
          report.add(block, "shift-clicking " + first.key() + " out of slot " + index + " never stops");
          continue;
        }
        int after = GuiProbes.count(menu, one);
        if (after != before) {
          report.add(block, "shift-clicking " + first.key() + " out of slot " + index + " changed the count "
              + before + " -> " + after);
        } else if (slot.mayPickup(player) && !slot.getItem().isEmpty()) {
          report.add(block, "shift-clicking " + first.key() + " out of slot " + index
              + " does not move it to the inventory");
        }
      }
    } finally {
      GuiProbes.clear(menu, player);
    }
  }

  private static boolean isGhostCopy(AbstractContainerMenu menu, Slot slot, ItemStack clicked) {
    ItemStack carried = menu.getCarried();
    ItemStack held = slot.getItem();
    return ItemStack.isSameItemSameComponents(carried, clicked) && carried.getCount() == clicked.getCount()
        && ItemStack.isSameItemSameComponents(held, clicked) && held.getCount() == 1;
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void clickingItemsIntoSlotsFollowsTheSlotRules(GameTestHelper helper) {
    List<Block> blocks = GuiProbes.menuBlocks();
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    BlockSweep.Report report = new BlockSweep.Report("click");
    BlockSweep.forEachBlock(helper, MACHINE, blocks, report,
        block -> checkClicks(helper, player, block, report), () -> {
        });
  }

  private static void checkClicks(GameTestHelper helper, Player player, Block block,
      BlockSweep.Report report) {
    AbstractContainerMenu menu = menuFor(helper, player, block);
    if (menu == null) {
      report.add(block, "getMenu returned null");
      return;
    }
    List<GuiProbes.Probe> probes = GuiProbes.all();
    try {
      for (int index : GuiProbes.machineSlots(menu, player)) {
        Slot slot = menu.slots.get(index);
        boolean refusesAll = true;
        for (GuiProbes.Probe probe : probes) {
          if (slot.mayPlace(probe.create())) {
            refusesAll = false;
            break;
          }
        }
        for (int p = 0; p < probes.size(); p++) {
          GuiProbes.Probe probe = probes.get(p);
          boolean mayPlace = slot.mayPlace(probe.create());
          if (!mayPlace && p % SAMPLE_EVERY != 0) {
            continue;
          }
          GuiProbes.clear(menu, player);
          ItemStack full = probe.full();
          menu.setCarried(full.copy());
          int before = GuiProbes.count(menu, full);
          menu.clicked(index, 0, ContainerInput.PICKUP, player);
          if (refusesAll && isGhostCopy(menu, slot, full)) {
            break;
          }
          int after = GuiProbes.count(menu, full);
          if (after != before) {
            report.add(block, "clicking " + before + "x " + probe.key() + " into slot " + index + " left " + after);
          }
          if (mayPlace && slot.getItem().isEmpty()) {
            report.add(block, "slot " + index + " accepts " + probe.key() + " but a click did not place it");
          }
          if (!mayPlace && !slot.getItem().isEmpty()) {
            report.add(block, "slot " + index + " refuses " + probe.key() + " but a click placed it");
          }
          checkSlotRules(menu, player, block, full, probe.key(), "click", report);
        }
      }
    } finally {
      GuiProbes.clear(menu, player);
    }
  }
}
