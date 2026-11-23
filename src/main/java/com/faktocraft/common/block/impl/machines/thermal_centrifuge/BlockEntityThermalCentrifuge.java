package com.faktocraft.common.block.impl.machines.thermal_centrifuge;

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
import com.faktocraft.common.interfaces.entity.IExpCollector;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.interfaces.receipe.IBaseRecipe;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.ThermalCentrifugingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.util.RecipeUtil;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.EnergyCosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockEntityThermalCentrifuge extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound, IExpCollector {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT_1 = 1;
  public static final int OUTPUT_SLOT_2 = 2;

  public final BlockEntityProgress progress = new BlockEntityProgress();
  public final BlockEntityProgress tempLevel = new BlockEntityProgress(0, 3000);

  private ItemStack cachedInputStack = ItemStack.EMPTY;

  @Nullable
  protected ThermalCentrifugingRecipe recipe;

  public BlockEntityThermalCentrifuge(BlockPos pos, BlockState state) {
    super(M3Registry.THERMAL_CENTRIFUGE_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().thermal_centrifuge_energy_capacity, EnergyType.RECEIVE,
        EnergyTier.HIGH);
    initBatterySlots();
  }

  protected Optional<ThermalCentrifugingRecipe> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<ThermalCentrifugingRecipe> findRecipe(ItemStack input) {
    return RecipeUtil.findRecipe(level, ModRecipeType.THERMAL_CENTRIFUGING, MachineRecipeInput.of(input));
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    return ((IBaseRecipe<?>) recipe).getExperience();
  }

  @Override
  public Runnable collectExp() {
    BlockPos pos = getBlockPos();
    return () -> ModNetworking.sendToServer(new PacketExperience(pos));
  }

  private boolean isValidInput(final ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    return level != null && findRecipe(stack).isPresent();
  }

  private static boolean outputFits(ItemStack outputStack, ItemStack resultStack) {
    return outputStack.isEmpty()
        || (outputStack.getItem() == resultStack.getItem()
            && outputStack.getCount() + resultStack.getCount() <= outputStack.getMaxStackSize());
  }

  private boolean canWork(ItemStack inputStack, ThermalCentrifugingRecipe currentRecipe) {
    List<ItemStack> results = currentRecipe.getResults();
    if (inputStack.getCount() < currentRecipe.getIngredientCount()) {
      return false;
    }

    ItemStack outputStack1 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_1);
    ItemStack outputStack2 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_2);

    boolean fits1 = outputFits(outputStack1, results.get(0));
    boolean fits2 = results.get(1).isEmpty() || outputFits(outputStack2, results.get(1));
    return fits1 && fits2;
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;
    boolean decHeat = false;
    boolean updateState = false;
    getEnergyStorage().updateConsumed(0);
    int tempCost = ModConfig.server().thermal_centrifuge_temp_cost;

    final ItemStack inputStack = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    final ItemStack outputSlot1 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_1);
    final ItemStack outputSlot2 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_2);

    if (cachedInputStack.getItem() != inputStack.getItem()) {
      boolean hadInput = !cachedInputStack.isEmpty();
      ThermalCentrifugingRecipe oldRecipe = recipe;
      cachedInputStack = inputStack.copy();
      recipe = inputStack.getItem() != Items.AIR ? getRecipe(inputStack).orElse(null) : null;
      if (hadInput || (oldRecipe != null && oldRecipe != recipe)) {
        progress.setBoth(-1);
      }
    }

    if (recipe != null) {
      ThermalCentrifugingRecipe currentRecipe = recipe;

      if (progress.getProgress() == -1) {
        progress.setData(0, currentRecipe.getDuration());
      }

      progress.rescaleMax(getSpeedFactor() * currentRecipe.getDuration());

      if (tempLevel.getProgress() >= currentRecipe.getTemperature()) {
        int energyCost = EnergyCosts.perTick(currentRecipe.getPowerCost(), getEnergyUsageFactor());

        if (canWork(inputStack, currentRecipe) && getEnergyStorage().consumeEnergy(energyCost, true) >= energyCost) {

          if (progress.getProgress() <= progress.getProgressMax()) {
            active = true;
            progress.incProgress(1);
            getEnergyStorage().consumeEnergy(energyCost, false);
            getEnergyStorage().updateConsumed(energyCost);
          }

          if (progress.getProgress() >= progress.getProgressMax()) {
            inputStack.shrink(currentRecipe.getIngredientCount());
            getItemStackHandler().setStackInSlot(INPUT_SLOT, inputStack.copy());

            List<ItemStack> results = currentRecipe.getResults();

            if (outputSlot1.isEmpty()) {
              getItemStackHandler().setStackInSlot(OUTPUT_SLOT_1, results.get(0).copy());
            } else {
              getItemStackHandler().getStackInSlot(OUTPUT_SLOT_1).grow(results.get(0).getCount());
            }

            if (!results.get(1).isEmpty()) {
              if (outputSlot2.isEmpty()) {
                getItemStackHandler().setStackInSlot(OUTPUT_SLOT_2, results.get(1).copy());
              } else {
                getItemStackHandler().getStackInSlot(OUTPUT_SLOT_2).grow(results.get(1).getCount());
              }
            }

            addRecipeUsed(recipe);
            progress.setBoth(-1);
          }

          if (tickCounter == 20 && tempLevel.getProgress() < tempLevel.getProgressMax()) {
            tempLevel.incProgress(1.5f);
          }
        } else {
          decHeat = true;
        }
      } else {
        if (tickCounter == 20
            && tempLevel.getProgress() < tempLevel.getProgressMax()
            && getEnergyStorage().consumeEnergy(tempCost, true) >= tempCost) {
          tempLevel.incProgress(1.5f);
          getEnergyStorage().consumeEnergy(tempCost, false);
        }
      }
    } else {
      decHeat = true;
      progress.setBoth(-1);
    }

    if (decHeat && tempLevel.getProgress() > 0 && tickCounter == 20) {
      tempLevel.decProgress(Math.min(tempLevel.getProgress(), 2.5f));
    }

    if (progress.changed() || tempLevel.changed()) {
      progress.clearChanged();
      tempLevel.clearChanged();
      updateState = true;
    }

    setActive(active);
    if (updateState) {
      updateBlockState();
    }
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 48, 33, InventorySlotType.INPUT, GuiSlotType.NORMAL, 47, 32));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT_1, 109, 24, InventorySlotType.OUTPUT, GuiSlotType.NORMAL, 108, 23));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT_2, 109, 43, InventorySlotType.OUTPUT, GuiSlotType.NORMAL, 108, 42));
    return super.addInventorySlot(slots);
  }

  @Override
  public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
    slots.add(new SlotBattery(0, 152, 62, false));
    return super.addBatterySlot(slots);
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.THERMAL_CENTRIFUGE;
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot == INPUT_SLOT) {
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

    CompoundTag tempLevelTag = new CompoundTag();
    tempLevel.save(tempLevelTag);
    tag.put("tempLevel", tempLevelTag);

    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    this.activeState = tag.getBooleanOr("active", false);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
    if (tag.contains("tempLevel")) {
      tempLevel.load(tag.getCompoundOrEmpty("tempLevel"));
    }
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }
}
