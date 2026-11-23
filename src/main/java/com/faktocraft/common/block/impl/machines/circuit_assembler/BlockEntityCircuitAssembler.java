package com.faktocraft.common.block.impl.machines.circuit_assembler;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.BlockEntityProgress;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.entity.slot.SlotBattery;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.GuiSlotType;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.enums.UpgradeType;
import com.faktocraft.common.interfaces.entity.IElectricSlot;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.CircuitAssemblingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.EnergyCosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockEntityCircuitAssembler extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound {

  public static final int INPUT_SLOT_0 = 0;
  public static final int INPUT_SLOT_1 = 1;
  public static final int INPUT_SLOT_2 = 2;
  public static final int OUTPUT_SLOT = 3;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private ItemStack resultStack = ItemStack.EMPTY;
  @Nullable
  protected CircuitAssemblingRecipe recipe;
  private int energyCostPerTick = 0;
  private int duration = 0;

  private ItemStack cachedInputStack0 = ItemStack.EMPTY;
  private ItemStack cachedInputStack1 = ItemStack.EMPTY;
  private ItemStack cachedInputStack2 = ItemStack.EMPTY;
  private ItemStack cachedOutput = ItemStack.EMPTY;
  private boolean cachedWork;

  public BlockEntityCircuitAssembler(BlockPos pos, BlockState state) {
    super(M3Registry.CIRCUIT_ASSEMBLER_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().circuit_assembler_energy_capacity, EnergyType.RECEIVE, EnergyTier.LOW);
    initBatterySlots();
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT_0, 16, 33, InventorySlotType.INPUT, GuiSlotType.NORMAL, 15, 32));
    slots.add(new FaktocraftSlot(INPUT_SLOT_1, 37, 21, InventorySlotType.INPUT, GuiSlotType.NORMAL, 36, 20));
    slots.add(new FaktocraftSlot(INPUT_SLOT_2, 58, 33, InventorySlotType.INPUT, GuiSlotType.NORMAL, 57, 32));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT, 118, 33, InventorySlotType.OUTPUT, GuiSlotType.LARGE, 113, 28));
    return super.addInventorySlot(slots);
  }

  @Override
  public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
    slots.add(new SlotBattery(0, 152, 62, false));
    return super.addBatterySlot(slots);
  }

  protected Optional<CircuitAssemblingRecipe> getRecipe(ItemStack... input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<CircuitAssemblingRecipe> findRecipe(ItemStack... input) {
    return level.getRecipeManager().getRecipeFor(ModRecipeType.CIRCUIT_ASSEMBLING, MachineRecipeInput.of(input),
        level);
  }

  private boolean isValidInput(final ItemStack... stack) {
    return level != null && findRecipe(stack).isPresent();
  }

  private boolean canWork(ItemStack inputStack0, ItemStack inputStack1, ItemStack inputStack2, ItemStack outputStack,
      ItemStack resultStack) {
    return getRecipe(inputStack0, inputStack1, inputStack2).isPresent()
        && (outputStack.isEmpty()
            || (resultStack.getCount() + outputStack.getCount() <= outputStack.getMaxStackSize()
                && resultStack.getItem() == outputStack.getItem()));
  }

  private static boolean stackChanged(ItemStack cached, ItemStack current) {
    return !ItemStack.isSameItemSameTags(cached, current) || cached.getCount() != current.getCount();
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;
    getEnergyStorage().updateConsumed(0);

    final ItemStack inputStack0 = getItemStackHandler().getStackInSlot(INPUT_SLOT_0);
    final ItemStack inputStack1 = getItemStackHandler().getStackInSlot(INPUT_SLOT_1);
    final ItemStack inputStack2 = getItemStackHandler().getStackInSlot(INPUT_SLOT_2);
    final ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);

    if (stackChanged(cachedInputStack0, inputStack0) || stackChanged(cachedInputStack1, inputStack1)
        || stackChanged(cachedInputStack2, inputStack2) || stackChanged(cachedOutput, outputStack)) {
      cachedInputStack0 = inputStack0.copy();
      cachedInputStack1 = inputStack1.copy();
      cachedInputStack2 = inputStack2.copy();
      cachedOutput = outputStack.copy();

      CircuitAssemblingRecipe oldRecipe = recipe;
      recipe = getRecipe(inputStack0, inputStack1, inputStack2).orElse(null);
      if (recipe != oldRecipe) {

        progress.setBoth(-1);
      }
      if (recipe != null && level != null) {
        resultStack = recipe.assemble(MachineRecipeInput.of(inputStack0, inputStack1, inputStack2),
            level.registryAccess());
        energyCostPerTick = recipe.getPowerCost();
        duration = recipe.getDuration();
      }

      cachedWork = false;
    }

    if (recipe != null && (cachedWork || canWork(inputStack0, inputStack1, inputStack2, outputStack, resultStack))) {
      cachedWork = true;

      if (progress.getProgress() == -1) {
        progress.setData(0, duration);
      }

      progress.rescaleMax(getSpeedFactor() * duration);
      int energyCost = EnergyCosts.perTick(energyCostPerTick, getEnergyUsageFactor());

      if (getEnergyStorage().consumeEnergy(energyCost, true) == energyCost
          && progress.getProgress() <= progress.getProgressMax()) {
        active = true;
        progress.incProgress(1);
        getEnergyStorage().consumeEnergy(energyCost, false);
        getEnergyStorage().updateConsumed(energyCost);
      }

      if (progress.getProgress() >= progress.getProgressMax()) {
        int[] consume = recipe.slotConsumption(
            MachineRecipeInput.of(inputStack0, inputStack1, inputStack2));
        if (consume == null) {
          progress.setBoth(-1);
        } else {
          if (outputStack.isEmpty()) {
            getItemStackHandler().setStackInSlot(OUTPUT_SLOT, resultStack.copy());
          } else {
            outputStack.grow(resultStack.getCount());
            getItemStackHandler().setStackInSlot(OUTPUT_SLOT, outputStack.copy());
          }

          ItemStack[] inputs = { inputStack0, inputStack1, inputStack2 };
          int[] inputSlots = { INPUT_SLOT_0, INPUT_SLOT_1, INPUT_SLOT_2 };
          for (int i = 0; i < inputs.length; i++) {
            if (consume[i] > 0) {
              inputs[i].shrink(consume[i]);
              getItemStackHandler().setStackInSlot(inputSlots[i], inputs[i].copy());
            }
          }

          addRecipeUsed(recipe);
          progress.setBoth(-1);
        }
      }
    } else if (recipe == null) {
      progress.setBoth(-1);
    }

    setActive(active);
    if (progress.changed()) {
      progress.clearChanged();
      updateBlockState();
    }
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot == INPUT_SLOT_0 || slot == INPUT_SLOT_1 || slot == INPUT_SLOT_2) {
      return isValidInput(stack);
    }
    return false;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    tag.putBoolean("active", activeState);
    CompoundTag progressTag = new CompoundTag();
    progress.save(progressTag);
    tag.put("progress", progressTag);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    this.activeState = tag.getBoolean("active");
    if (tag.contains("progress")) {
      progress.load(tag.getCompound("progress"));
    }
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.COMPRESSOR;
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }
}
