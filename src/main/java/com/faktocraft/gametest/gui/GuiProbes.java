package com.faktocraft.gametest.gui;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.item.base.FluidItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class GuiProbes {

  public record Probe(String key, Supplier<ItemStack> factory) {
    public ItemStack create() {
      return factory.get();
    }

    public ItemStack full() {
      ItemStack stack = factory.get();
      stack.setCount(stack.getMaxStackSize());
      return stack;
    }
  }

  private static List<Probe> cached;

  private GuiProbes() {
  }

  public static synchronized List<Probe> all() {
    if (cached == null) {
      cached = build();
    }
    return cached;
  }

  public static String itemId(Item item) {
    return BuiltInRegistries.ITEM.getKey(item).toString();
  }

  public static String blockId(Block block) {
    return BuiltInRegistries.BLOCK.getKey(block).toString();
  }

  public static String fluidId(Fluid fluid) {
    return BuiltInRegistries.FLUID.getKey(fluid).toString();
  }

  public static boolean isMod(Block block) {
    return Faktocraft.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
  }

  public static boolean isMod(Item item) {
    return Faktocraft.MODID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
  }

  public static List<Fluid> sourceFluids() {
    List<Fluid> fluids = new ArrayList<>();
    for (Fluid fluid : BuiltInRegistries.FLUID) {
      if (fluid != Fluids.EMPTY && fluid.defaultFluidState().isSource()) {
        fluids.add(fluid);
      }
    }
    fluids.sort(Comparator.comparing(GuiProbes::fluidId));
    return fluids;
  }

  public static List<Block> menuBlocks() {
    List<Block> blocks = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      if (block instanceof IHasMenu && isMod(block)) {
        blocks.add(block);
      }
    }
    blocks.sort(Comparator.comparing(GuiProbes::blockId));
    return blocks;
  }

  private static List<Probe> build() {
    List<Probe> out = new ArrayList<>();
    List<Fluid> fluids = sourceFluids();
    for (Item item : BuiltInRegistries.ITEM) {
      if (item == Items.AIR) {
        continue;
      }
      String id = itemId(item);
      out.add(new Probe(id, () -> new ItemStack(item)));
      if (item instanceof FluidItem fluidItem && fluidItem.getFluidCapacity() > 0) {
        for (Fluid fluid : fluids) {
          out.add(new Probe(id + "#fluid=" + fluidId(fluid), () -> {
            ItemStack stack = new ItemStack(item);
            FluidItem.setFluid(stack, fluid, fluidItem.getFluidCapacity());
            return stack;
          }));
        }
      }
      if (item instanceof IElectricItem electric) {
        out.add(new Probe(id + "#charged", () -> {
          ItemStack stack = new ItemStack(item);
          IEnergy energy = electric.getEnergy(stack);
          if (energy != null) {
            energy.setEnergy(energy.maxEnergy());
          }
          return stack;
        }));
      }
    }
    out.sort(Comparator.comparing(Probe::key));
    return out;
  }

  public static boolean isPlayerSlot(Slot slot, Player player) {
    return slot.container instanceof Inventory inventory && inventory.player == player;
  }

  public static List<Integer> machineSlots(AbstractContainerMenu menu, Player player) {
    List<Integer> out = new ArrayList<>();
    for (int i = 0; i < menu.slots.size(); i++) {
      if (!isPlayerSlot(menu.slots.get(i), player)) {
        out.add(i);
      }
    }
    return out;
  }

  public static int playerSlotIndex(AbstractContainerMenu menu, Player player, int inventorySlot) {
    for (int i = 0; i < menu.slots.size(); i++) {
      Slot slot = menu.slots.get(i);
      if (isPlayerSlot(slot, player) && slot.getContainerSlot() == inventorySlot) {
        return i;
      }
    }
    return -1;
  }

  public static void clear(AbstractContainerMenu menu, Player player) {
    for (Slot slot : menu.slots) {
      if (!slot.getItem().isEmpty()) {
        slot.set(ItemStack.EMPTY);
      }
    }
    player.getInventory().clearContent();
    menu.setCarried(ItemStack.EMPTY);
  }

  public static boolean same(ItemStack a, ItemStack b) {
    return ItemStack.isSameItemSameTags(a, b);
  }
}
