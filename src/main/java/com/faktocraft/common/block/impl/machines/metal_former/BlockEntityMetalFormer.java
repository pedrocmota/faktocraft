package com.faktocraft.common.block.impl.machines.metal_former;

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
import com.faktocraft.common.enums.MetalFormerMode;
import com.faktocraft.common.enums.UpgradeType;
import com.faktocraft.common.interfaces.entity.IElectricSlot;
import com.faktocraft.common.interfaces.entity.IExpCollector;
import com.faktocraft.common.interfaces.entity.IMachineActions;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.interfaces.receipe.IBaseRecipe;
import com.faktocraft.common.interfaces.receipe.IRecipeSingleIngredient;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.util.RecipeUtil;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.EnergyCosts;
import com.faktocraft.common.util.StackHandlerHelper;
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

public class BlockEntityMetalFormer extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound, IExpCollector, IMachineActions.IModeSwitcher {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT = 1;

  public final BlockEntityProgress progress = new BlockEntityProgress();
  protected MetalFormerMode mode = MetalFormerMode.CUTTING;
  @Nullable
  protected IRecipeSingleIngredient recipe;

  public BlockEntityMetalFormer(BlockPos pos, BlockState state) {
    super(M3Registry.METAL_FORMER_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().metal_former_energy_capacity, EnergyType.RECEIVE, EnergyTier.LOW);
    initBatterySlots();
  }

  @Override
  public boolean inputSlotChanged(int slotId, ItemStack oldStack, ItemStack newStack) {
    boolean validChange = false;
    IRecipeSingleIngredient oldRecipe = recipe;

    if (oldStack.getItem() != newStack.getItem()) {
      if (newStack.getItem() != Items.AIR) {
        recipe = getRecipe(newStack).orElse(null);
      } else {
        recipe = null;
      }
      validChange = true;
    } else {
      if (recipe != null && newStack.getCount() < recipe.getIngredientCount()) {
        recipe = null;
        validChange = true;
      } else if (recipe == null && !newStack.isEmpty()) {

        recipe = getRecipe(newStack).orElse(null);
      }
    }

    if (oldRecipe == null && recipe != null) {
      validChange = false;
    }

    if (validChange) {
      progress.setBoth(-1);
    }
    return validChange;
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;

    checkInputSlotChange(INPUT_SLOT);

    if (recipe != null) {
      IRecipeSingleIngredient currentRecipe = recipe;

      if (progress.getProgress() == -1) {
        progress.setData(0, currentRecipe.getDuration());
      }

      if (canWork(currentRecipe)) {
        progress.rescaleMax(getSpeedFactor() * currentRecipe.getDuration());

        int energyCost = EnergyCosts.perTick(currentRecipe.getPowerCost(), getEnergyUsageFactor());

        if (getEnergyStorage().consumeEnergy(energyCost, true) == energyCost
            && progress.getProgress() <= progress.getProgressMax()) {
          active = true;
          progress.incProgress(1);
          getEnergyStorage().consumeEnergy(energyCost, false);
          getEnergyStorage().updateConsumed(energyCost);
        }

        if (progress.getProgress() >= progress.getProgressMax()) {
          StackHandlerHelper.shrinkInputStack(getItemStackHandler(), INPUT_SLOT, currentRecipe.getIngredientCount());
          StackHandlerHelper.incMachineOutputStack(getItemStackHandler(), OUTPUT_SLOT, currentRecipe.getResultItem());

          addRecipeUsed(recipe);
          progress.setBoth(-1);
        }
      }
    }

    setActive(active);
    if (progress.changed()) {
      progress.clearChanged();
      shouldUpdateState = true;
    }
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 47, 35, InventorySlotType.INPUT, GuiSlotType.NORMAL, 46, 34));
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
    return ModSounds.METAL_FORMER;
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
    CompoundTag progressTag = new CompoundTag();
    progress.save(progressTag);
    tag.put("progress", progressTag);
    tag.putInt("mode", mode.getId());
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
    this.mode = MetalFormerMode
        .getModeFromId(tag.contains("mode") ? tag.getIntOr("mode", 0) : MetalFormerMode.CUTTING.getId());
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
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

  protected Optional<? extends IRecipeSingleIngredient> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<? extends IRecipeSingleIngredient> findRecipe(ItemStack input) {
    MachineRecipeInput recipeInput = MachineRecipeInput.of(input);
    return switch (mode) {
      case CUTTING -> RecipeUtil.findRecipe(level, ModRecipeType.CUTTING, recipeInput);
      case ROLLING -> RecipeUtil.findRecipe(level, ModRecipeType.ROLLING, recipeInput);
      case EXTRUDING -> RecipeUtil.findRecipe(level, ModRecipeType.EXTRUDING, recipeInput);
    };
  }

  protected boolean isValidInput(final ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    return level != null && findRecipe(stack).isPresent();
  }

  public MetalFormerMode getMode() {
    return mode;
  }

  @Override
  public void changeMode() {
    mode = switch (mode) {
      case CUTTING -> MetalFormerMode.ROLLING;
      case ROLLING -> MetalFormerMode.EXTRUDING;
      case EXTRUDING -> MetalFormerMode.CUTTING;
    };

    final ItemStack inputStack = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    recipe = inputStack.isEmpty() ? null : getRecipe(inputStack).orElse(null);
    if (recipe == null) {
      setActive(false);
    }

    progress.setBoth(-1);
    progress.clearChanged();
    updateBlockState();
  }

  private boolean canWork(IRecipeSingleIngredient currentRecipe) {
    final ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);
    ItemStack resultItem = currentRecipe.getResultItem();
    return outputStack.isEmpty()
        || (outputStack.getItem() == resultItem.getItem()
            && outputStack.getCount() + resultItem.getCount() <= outputStack.getMaxStackSize());
  }
}
