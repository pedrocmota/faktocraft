package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.UpgradeType;
import com.faktocraft.common.interfaces.item.IUpgradeItem;
import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import java.util.function.BooleanSupplier;

public class PipeExtractor {

  public static final int RUN_ALWAYS = 0;
  public static final int RUN_OFF = 1;
  public static final int RUN_REDSTONE = 2;

  public static final int UPGRADE_SLOTS = 3;
  private static final int BASE_INTERVAL_TICKS = 20;
  private static final int BASE_COST_PER_PULSE = 5;

  public static final int DOCK_SLOTS = 4;
  public static final int DOCK_TENSION_SLOT = 2;
  public static final int DOCK_BATTERY_SLOT = 3;

  private final BlockEntity host;
  private final BasicEnergyStorage energy;
  private final ItemStackHandler upgrades;
  private final ItemStackHandler dock;
  private int runMode = RUN_ALWAYS;

  public PipeExtractor(BlockEntity host) {
    this.host = host;

    this.energy = new BasicEnergyStorage(0, 0, EnergyType.RECEIVE, EnergyTier.LOW) {
      @Override
      public boolean canReceiveEnergy(@Nullable Direction side) {
        return true;
      }
    };
    this.upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
      @Override
      protected void onContentsChanged(int slot) {
        host.setChanged();
      }
    };
    this.dock = new ItemStackHandler(DOCK_SLOTS) {
      @Override
      protected void onContentsChanged(int slot) {
        applyDock();
        host.setChanged();
      }
    };
  }

  private void applyDock() {
    int cap = 0;
    for (int i = 0; i < DOCK_TENSION_SLOT; i++) {
      if (dock.getStackInSlot(i).getItem() instanceof com.faktocraft.common.item.impl.CapacitorItem capacitor) {
        cap += capacitor.getCapacity();
      }
    }
    energy.setMaxEnergy(cap);
    energy.applyTierShift(getDockTensionLevel());
  }

  public int getDockTensionLevel() {
    return dock.getStackInSlot(DOCK_TENSION_SLOT)
        .getItem() instanceof com.faktocraft.common.item.impl.upgrade.TensionUpgrade tension
            ? tension.getMkLevel()
            : 0;
  }

  private void tickBatteryDischarge() {
    ItemStack stack = dock.getStackInSlot(DOCK_BATTERY_SLOT);
    if (!(stack.getItem() instanceof com.faktocraft.common.item.base.ElectricItem electricItem)
        || electricItem.getEnergyType() == EnergyType.RECEIVE) {
      return;
    }
    int free = energy.maxEnergy() - energy.energyStored();
    if (free <= 0) {
      return;
    }
    int itemEnergy = com.faktocraft.common.registries.ModComponents.getEnergy(stack, 0);
    if (itemEnergy <= 0) {
      return;
    }
    int rate = electricItem.getEnergyTier().getBasicTransfer();
    int move = Math.min(Math.min(free, rate), itemEnergy);
    if (move > 0) {
      com.faktocraft.common.registries.ModComponents.setEnergy(stack, itemEnergy - move);
      energy.setEnergy(energy.energyStored() + move);
      host.setChanged();
    }
  }

  public BasicEnergyStorage energy() {
    return energy;
  }

  public ItemStackHandler getUpgrades() {
    return upgrades;
  }

  public ItemStackHandler getDock() {
    return dock;
  }

  public static boolean isMotorUpgrade(ItemStack stack) {
    return stack.getItem() instanceof IUpgradeItem upgrade
        && (upgrade.getUpgradeType() == UpgradeType.OVERCLOCKER
            || upgrade.getUpgradeType() == UpgradeType.EFFICIENCY);
  }

  private int points(UpgradeType type) {
    int points = 0;
    for (int i = 0; i < upgrades.getSlots(); i++) {
      ItemStack stack = upgrades.getStackInSlot(i);
      if (stack.getItem() instanceof IUpgradeItem upgrade && upgrade.getUpgradeType() == type) {
        points += upgrade.isAdvancedUpgrade() ? 3 : 1;
      }
    }
    return points;
  }

  public int getPulseInterval() {
    return Math.max(3, (int) Math.round(
        BASE_INTERVAL_TICKS * Math.pow(0.8, points(UpgradeType.OVERCLOCKER))));
  }

  public int getCostPerPulse() {
    return (int) Math.ceil(BASE_COST_PER_PULSE
        * Math.pow(1.5, points(UpgradeType.OVERCLOCKER))
        * Math.pow(0.85, points(UpgradeType.EFFICIENCY)));
  }

  public int getRunMode() {
    return runMode;
  }

  public void setRunMode(int runMode) {
    this.runMode = net.minecraft.util.Mth.clamp(runMode, 0, 2);
    host.setChanged();
  }

  public void tick(BooleanSupplier pulse) {
    Level level = host.getLevel();
    if (level == null) {
      return;
    }
    tickBatteryDischarge();
    if (runMode == RUN_OFF
        || (runMode == RUN_REDSTONE && level.getBestNeighborSignal(host.getBlockPos()) <= 0)) {
      return;
    }
    if (level.getGameTime() % getPulseInterval() != 0) {
      return;
    }
    int cost = getCostPerPulse();
    if (energy.consumeEnergy(cost, true) < cost) {
      return;
    }
    if (pulse.getAsBoolean()) {
      energy.consumeEnergy(cost, false);
      energy.updateConsumed(cost);
    }
  }

  public void dropUpgrades(Level level, net.minecraft.core.BlockPos pos) {
    for (int i = 0; i < upgrades.getSlots(); i++) {
      ItemStack stack = upgrades.getStackInSlot(i);
      if (!stack.isEmpty()) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
      }
    }
    for (int i = 0; i < dock.getSlots(); i++) {
      ItemStack docked = dock.getStackInSlot(i);
      if (!docked.isEmpty()) {
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), docked);
      }
    }
  }

  public void save(CompoundTag tag) {
    tag.put("extractorEnergy", energy.serializeNBT());
    tag.putInt("runMode", runMode);
    CompoundTag upgradesTag = new CompoundTag();
    upgrades.save(upgradesTag);
    tag.put("upgrades", upgradesTag);
    CompoundTag dockTag = new CompoundTag();
    dock.save(dockTag);
    tag.put("dock", dockTag);
  }

  public void load(CompoundTag tag) {
    if (tag.contains("extractorEnergy")) {
      energy.deserializeNBT(tag.getCompoundOrEmpty("extractorEnergy"));
    }
    runMode = tag.contains("runMode") ? tag.getIntOr("runMode", 0) : RUN_ALWAYS;
    if (tag.contains("upgrades")) {
      upgrades.load(tag.getCompoundOrEmpty("upgrades"));
    }
    if (tag.contains("dock")) {
      dock.load(tag.getCompoundOrEmpty("dock"));
    }

    applyDock();
  }
}
