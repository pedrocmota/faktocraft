package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.item.base.ElectricItem;
import com.faktocraft.common.item.impl.CapacitorItem;
import com.faktocraft.common.registries.PipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class MenuPump extends AbstractContainerMenu {

  public static final int MACHINE_SLOTS = 7;

  private final BlockEntityPump pump;

  public BlockEntityPump getPump() {
    return pump;
  }

  public MenuPump(int windowId, Level level, BlockPos pos, Inventory playerInventory, Player player) {
    super(PipeRegistry.PUMP_MENU, windowId);
    this.pump = level.getBlockEntity(pos) instanceof BlockEntityPump found ? found : null;

    if (pump != null) {
      for (int i = 0; i < 2; i++) {
        addSlot(new Slot(pump.getBatteryStackHandler(), i, 8 + i * 18, 22) {
          @Override
          public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof CapacitorItem;
          }

          @Override
          public int getMaxStackSize() {
            return 1;
          }
        });
      }
      addSlot(new Slot(pump.getBatteryStackHandler(),
          com.faktocraft.common.entity.block.FaktocraftBlockEntity.TENSION_DOCK_SLOT, 44, 22) {
        @Override
        public boolean mayPlace(ItemStack stack) {
          return stack.getItem() instanceof com.faktocraft.common.item.impl.upgrade.TensionUpgrade;
        }

        @Override
        public int getMaxStackSize() {
          return 1;
        }
      });
      for (int i = 0; i < BlockEntityPump.UPGRADE_SLOTS; i++) {
        addSlot(new Slot(pump.getUpgrades(), i, 80 + i * 18, 22) {
          @Override
          public boolean mayPlace(ItemStack stack) {
            return PipeExtractor.isMotorUpgrade(stack);
          }

          @Override
          public int getMaxStackSize() {
            return 1;
          }
        });
      }
      addSlot(new Slot(pump.getBatteryStackHandler(), 3, 152, 22) {
        @Override
        public boolean mayPlace(ItemStack stack) {
          return stack.getItem() instanceof ElectricItem;
        }

        @Override
        public int getMaxStackSize() {
          return 1;
        }
      });
    }

    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 108 + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(playerInventory, col, 8 + col * 18, 166));
    }

    this.runMode = addDataSlot(pump != null ? new net.minecraft.world.inventory.DataSlot() {
      @Override
      public int get() {
        return pump.getRunMode();
      }

      @Override
      public void set(int value) {
        pump.setRunMode(value);
      }
    } : net.minecraft.world.inventory.DataSlot.standalone());
  }

  private final net.minecraft.world.inventory.DataSlot runMode;

  public int getRunMode() {
    return runMode.get();
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (pump != null && id >= 0 && id <= 2) {
      pump.setRunMode(id);
      return true;
    }
    return false;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (pump == null) {
      return ItemStack.EMPTY;
    }
    Slot slot = this.slots.get(index);
    if (!slot.hasItem()) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, this.slots.size(), true)) {
        return ItemStack.EMPTY;
      }
    } else if (stack.getItem() instanceof com.faktocraft.common.item.impl.upgrade.TensionUpgrade) {
      if (!moveItemStackTo(stack, 2, 3, false)) {
        return ItemStack.EMPTY;
      }
    } else if (PipeExtractor.isMotorUpgrade(stack)) {
      if (!moveItemStackTo(stack, 3, 6, false)) {
        return ItemStack.EMPTY;
      }
    } else if (stack.getItem() instanceof CapacitorItem) {
      if (!moveItemStackTo(stack, 0, 2, false)) {
        return ItemStack.EMPTY;
      }
    } else if (stack.getItem() instanceof ElectricItem) {
      if (!moveItemStackTo(stack, 6, MACHINE_SLOTS, false)) {
        return ItemStack.EMPTY;
      }
    } else {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      slot.set(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
  }

  @Override
  public boolean stillValid(Player player) {
    return pump != null && !pump.isRemoved()
        && player.distanceToSqr(pump.getBlockPos().getCenter()) <= 64.0;
  }
}
