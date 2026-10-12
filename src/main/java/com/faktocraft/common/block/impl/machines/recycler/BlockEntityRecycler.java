package com.faktocraft.common.block.impl.machines.recycler;

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
import com.faktocraft.common.recipe.impl.RecyclingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.EnergyCosts;
import com.faktocraft.common.util.StackHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import com.faktocraft.common.util.RecipeUtil;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.Capability;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.LazyOptional;
import com.faktocraft.common.util.transfer.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockEntityRecycler extends FaktocraftBlockEntity implements IEnergyBlock, ISupportUpgrades, ITileSound {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT = 1;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private final RecipeUtil.CachedCheck<MachineRecipeInput, RecyclingRecipe> quickCheck = RecipeUtil
      .createCheck(ModRecipeType.RECYCLING);

  private ItemStack cachedInputStack = ItemStack.EMPTY;
  @Nullable
  private RecyclingRecipe recipe;

  private final LazyOptional<IItemHandler> itemHandlerDown = LazyOptional
      .of(() -> createSlotTypeStorage(InventorySlotType.OUTPUT));
  private final LazyOptional<IItemHandler> itemHandlerSides = LazyOptional
      .of(() -> createSlotTypeStorage(InventorySlotType.INPUT));

  public BlockEntityRecycler(BlockPos pos, BlockState state) {
    super(M2Registry.RECYCLER_BE, pos, state);
    createEnergyStorage(0, ModConfig.server().recycler_energy_capacity, EnergyType.RECEIVE, EnergyTier.LOW);
    initBatterySlots();
  }

  protected Optional<RecyclingRecipe> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return Optional.empty();
    }
    return quickCheck.getRecipeFor(MachineRecipeInput.of(input), serverLevel);
  }

  private boolean canWork(ItemStack outputStack, ItemStack resultStack) {
    return outputStack.isEmpty()
        || (ItemStack.isSameItemSameComponents(outputStack, resultStack)
            && resultStack.getCount() + outputStack.getCount() <= outputStack.getMaxStackSize());
  }

  @Override
  public void tickWork(BlockState state) {
    if (level == null || level.isClientSide()) {
      return;
    }

    boolean active = false;
    getEnergyStorage().updateConsumed(0);

    final ItemStack inputStack = itemStackHandler.getStackInSlot(INPUT_SLOT);
    final ItemStack outputStack = itemStackHandler.getStackInSlot(OUTPUT_SLOT);

    if (!ItemStack.isSameItemSameComponents(cachedInputStack, inputStack)) {
      cachedInputStack = inputStack.copy();
      recipe = inputStack.isEmpty() ? null : getRecipe(inputStack).orElse(null);
    }

    if (recipe != null && !inputStack.isEmpty()) {
      RecyclingRecipe recyclingRecipe = recipe;

      if (progress.getProgress() == -1) {
        progress.setData(0, recyclingRecipe.getDuration());
      }

      progress.rescaleMax(getSpeedFactor() * recyclingRecipe.getDuration());
      int energyCost = EnergyCosts.perTick(recyclingRecipe.getPowerCost(), getEnergyUsageFactor());

      ItemStack resultStack = recyclingRecipe.getResultItem();
      if (canWork(outputStack, resultStack)) {
        if (getEnergyStorage().consumeEnergy(energyCost, true) == energyCost
            && progress.getProgress() <= progress.getProgressMax()) {
          active = true;
          progress.incProgress(1);
          getEnergyStorage().consumeEnergy(energyCost, false);
          getEnergyStorage().updateConsumed(energyCost);
        }

        if (progress.getProgress() >= progress.getProgressMax()) {
          StackHandlerHelper.shrinkInputStack(itemStackHandler, INPUT_SLOT, 1);
          if (level.getRandom().nextDouble() <= recyclingRecipe.getChance()) {
            StackHandlerHelper.incMachineOutputStack(itemStackHandler, OUTPUT_SLOT, resultStack.copy());
          }
          progress.setBoth(-1);
        }
      }
    } else {
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
    if (slot != INPUT_SLOT) {
      return false;
    }
    return quickCheck.getRecipeFor(MachineRecipeInput.of(stack), level).isPresent();
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 48, 35, InventorySlotType.INPUT, GuiSlotType.NORMAL, 47, 34));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT, 108, 35, InventorySlotType.OUTPUT, GuiSlotType.LARGE, 103, 30));
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
    return ModSounds.RECYCLER;
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER && hasInventory()) {
      if (isFrontSide(side)) {
        return LazyOptional.empty();
      }
      if (side == null) {
        return super.getCapability(cap, side);
      }
      if (side == Direction.DOWN) {
        return itemHandlerDown.cast();
      }
      return itemHandlerSides.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    itemHandlerDown.invalidate();
    itemHandlerSides.invalidate();
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
    activeState = tag.getBooleanOr("active", false);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
  }
}
