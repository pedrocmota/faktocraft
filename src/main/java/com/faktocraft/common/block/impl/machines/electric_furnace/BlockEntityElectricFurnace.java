package com.faktocraft.common.block.impl.machines.electric_furnace;

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
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.EnergyCosts;
import com.faktocraft.common.util.StackHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BlockEntityElectricFurnace extends FaktocraftBlockEntity
    implements IEnergyBlock, IExpCollector, ISupportUpgrades {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT = 1;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private final RecipeManager.CachedCheck<Container, SmeltingRecipe> quickCheck = RecipeManager
      .createCheck(RecipeType.SMELTING);

  private ItemStack cachedInputStack = ItemStack.EMPTY;
  private ItemStack resultStack = ItemStack.EMPTY;
  @Nullable
  private SmeltingRecipe furnaceRecipe;

  private final Map<Direction, LazyOptional<IItemHandler>> sidedItemCaps = new EnumMap<>(Direction.class);
  private LazyOptional<IItemHandler> nullSideItemCap = LazyOptional.empty();

  public BlockEntityElectricFurnace(BlockPos pos, BlockState state) {
    super(M2Registry.ELECTRIC_FURNACE_BE, pos, state);
    createEnergyStorage(0, ModConfig.server().electric_furnace_energy_capacity, EnergyType.RECEIVE, EnergyTier.LOW);
    initBatterySlots();
  }

  protected Optional<SmeltingRecipe> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return Optional.empty();
    }
    return quickCheck.getRecipeFor(MachineRecipeInput.of(input), serverLevel);
  }

  protected ItemStack getRecipeResult(ItemStack stack) {
    return furnaceRecipe != null && level != null
        ? furnaceRecipe.assemble(MachineRecipeInput.of(stack), level.registryAccess())
        : ItemStack.EMPTY;
  }

  private boolean canSmelt(ItemStack inputStack, ItemStack outputStack, ItemStack resultStack) {
    return !inputStack.isEmpty() && !resultStack.isEmpty()
        && outputStack.getCount() < outputStack.getMaxStackSize()
        && (outputStack.isEmpty() || ItemStack.isSameItemSameTags(outputStack, resultStack));
  }

  private float getSmeltTime() {
    return furnaceRecipe != null ? furnaceRecipe.getCookingTime() : 200;
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

    if (!ItemStack.isSameItemSameTags(cachedInputStack, inputStack)) {
      boolean hadInput = !cachedInputStack.isEmpty();
      SmeltingRecipe oldRecipe = furnaceRecipe;
      cachedInputStack = inputStack.copy();
      furnaceRecipe = inputStack.isEmpty() ? null : getRecipe(inputStack).orElse(null);
      resultStack = furnaceRecipe != null ? getRecipeResult(inputStack) : ItemStack.EMPTY;
      if (hadInput || (oldRecipe != null && oldRecipe != furnaceRecipe)) {
        progress.setBoth(-1);
      }
    }

    if (furnaceRecipe != null) {
      if (canSmelt(inputStack, outputStack, resultStack) && progress.getProgress() != -1) {

        progress.rescaleMax(getSpeedFactor() * furnaceRecipe.getCookingTime() * 0.70F);
        int energyCost = EnergyCosts.perTick(ModConfig.server().electric_furnace_tick_usage, getEnergyUsageFactor());

        if (getEnergyStorage().consumeEnergy(energyCost, true) == energyCost) {
          active = true;
          progress.incProgress(1);
          getEnergyStorage().consumeEnergy(energyCost, false);
          getEnergyStorage().updateConsumed(energyCost);
        }

        if (progress.getProgress() >= progress.getProgressMax()) {
          StackHandlerHelper.incMachineOutputStack(itemStackHandler, OUTPUT_SLOT, resultStack.copy());
          StackHandlerHelper.shrinkInputStack(itemStackHandler, INPUT_SLOT, 1);
          addRecipeUsed(furnaceRecipe);
          progress.setProgress(-1);
        }
      }
    } else {
      progress.setBoth(-1);
    }

    if (progress.getProgress() == -1 && furnaceRecipe != null && canSmelt(inputStack, outputStack, resultStack)) {
      progress.setData(0, getSmeltTime() * 0.70F);
    }

    setActive(active);

    if (progress.changed()) {
      progress.clearChanged();
      updateBlockState();
    }
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot == INPUT_SLOT) {
      return level != null && quickCheck.getRecipeFor(MachineRecipeInput.of(stack), level).isPresent();
    }
    return false;
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
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
      return cookingRecipe.getExperience();
    }
    return 0;
  }

  @Override
  public Runnable collectExp() {
    BlockPos pos = getBlockPos();
    return () -> ModNetworking.sendToServer(new PacketExperience(pos));
  }

  public IItemHandler getItemStorageForSide(@Nullable Direction side) {
    if (side == null) {
      return new InvWrapper(getItemStackHandler());
    }
    return switch (side) {
      case UP -> createSlotTypeStorage(InventorySlotType.INPUT);
      case DOWN -> createSlotTypeStorage(InventorySlotType.OUTPUT);
      default -> new InvWrapper(getItemStackHandler());
    };
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER && hasInventory() && getItemStackHandler() != null) {
      if (isFrontSide(side)) {
        return LazyOptional.empty();
      }
      if (side == null) {
        if (!nullSideItemCap.isPresent()) {
          nullSideItemCap = LazyOptional.of(() -> getItemStorageForSide(null));
        }
        return nullSideItemCap.cast();
      }
      return sidedItemCaps.computeIfAbsent(side, s -> LazyOptional.of(() -> getItemStorageForSide(s))).cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    nullSideItemCap.invalidate();
    sidedItemCaps.values().forEach(LazyOptional::invalidate);
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
    activeState = tag.getBoolean("active");
    if (tag.contains("progress")) {
      progress.load(tag.getCompound("progress"));
    }
  }

}
