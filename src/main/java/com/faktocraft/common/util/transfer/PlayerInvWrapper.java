package com.faktocraft.common.util.transfer;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerInvWrapper extends InvWrapper {
  public static final int SLOTS = Inventory.SLOT_OFFHAND + 1;

  private final Inventory inventory;
  private final boolean armorTakesOnlyEquipment;

  private PlayerInvWrapper(Inventory inventory, boolean armorTakesOnlyEquipment) {
    super(inventory);
    this.inventory = inventory;
    this.armorTakesOnlyEquipment = armorTakesOnlyEquipment;
  }

  public static PlayerInvWrapper of(Inventory inventory) {
    return new PlayerInvWrapper(inventory, true);
  }

  public static PlayerInvWrapper container(Inventory inventory) {
    return new PlayerInvWrapper(inventory, false);
  }

  @Override
  public int getSlots() {
    return SLOTS;
  }

  @NotNull
  @Override
  public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
    if (armorTakesOnlyEquipment && slot >= Inventory.INVENTORY_SIZE && slot < Inventory.SLOT_OFFHAND) {
      EquipmentSlot equipmentSlot = Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);
      if (stack.isEmpty() || !inventory.player.isEquippableInSlot(stack, equipmentSlot)) {
        return stack;
      }
    }
    return super.insertItem(slot, stack, simulate);
  }
}
