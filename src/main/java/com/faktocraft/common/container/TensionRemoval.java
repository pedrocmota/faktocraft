package com.faktocraft.common.container;

import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.item.impl.upgrade.TensionUpgrade;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TensionRemoval {

  public record Change(int from, int to) {
  }

  private TensionRemoval() {
  }

  public static boolean energized(@Nullable BasicEnergyStorage storage) {
    return storage != null && storage.energyStored() > 0
        && storage.origTier.getLvl() < EnergyTier.VERY_HIGH.getLvl();
  }

  public static EnergyTier inputTier(BasicEnergyStorage storage, int mkLevel) {
    int base = storage.origTier.getLvl();
    if (base >= EnergyTier.VERY_HIGH.getLvl()) {
      return storage.origTier;
    }
    return EnergyTier.getTierFromLvl(Math.min(EnergyTier.VERY_HIGH.getLvl(), base + mkLevel));
  }

  public static int installedLevel(ItemStack stack) {
    return stack.getItem() instanceof TensionUpgrade upgrade ? upgrade.getMkLevel() : 0;
  }

  @Nullable
  public static Change change(AbstractContainerMenu menu, @Nullable Slot slot, Inventory inventory, int button,
      ClickType type) {
    if (slot == null) {
      return null;
    }
    ItemStack carried = menu.getCarried();
    if (type == ClickType.PICKUP_ALL) {
      return gathered(menu, slot, carried, inventory);
    }
    if (slot.container instanceof Inventory) {
      return null;
    }
    int installed = installedLevel(slot.getItem());
    if (installed <= 0) {
      return null;
    }
    int after = switch (type) {
      case PICKUP -> carried.isEmpty() ? 0 : replaced(slot, carried, installed, true);
      case QUICK_MOVE -> 0;
      case THROW -> carried.isEmpty() ? 0 : installed;
      case SWAP -> {
        ItemStack hotbar = button >= 0 && button < inventory.getContainerSize() ? inventory.getItem(button)
            : ItemStack.EMPTY;
        yield hotbar.isEmpty() ? 0 : replaced(slot, hotbar, installed, false);
      }
      default -> installed;
    };
    return after < installed ? new Change(installed, after) : null;
  }

  @Nullable
  private static Change gathered(AbstractContainerMenu menu, Slot clicked, ItemStack carried, Inventory inventory) {
    int level = installedLevel(carried);
    if (level <= 0 || (clicked.hasItem() && clicked.mayPickup(inventory.player))) {
      return null;
    }
    for (Slot other : menu.slots) {
      if (!(other.container instanceof Inventory) && ItemStack.isSameItemSameTags(other.getItem(), carried)) {
        return new Change(level, 0);
      }
    }
    return null;
  }

  private static int replaced(Slot slot, ItemStack incoming, int installed, boolean countLimited) {
    int level = installedLevel(incoming);
    if (level <= 0 || ItemStack.isSameItemSameTags(slot.getItem(), incoming) || !slot.mayPlace(incoming)
        || (countLimited && incoming.getCount() > slot.getMaxStackSize(incoming))) {
      return installed;
    }
    return level;
  }
}
