package com.faktocraft.common.block.impl.machines.fluid_enricher;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.BlockEntityProgress;
import com.faktocraft.common.entity.block.FluidStorage;
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
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.item.crafting.CountedIngredient;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.FluidIngredientData;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.FluidEnrichingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.EnergyCosts;
import com.faktocraft.common.util.StackHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockEntityFluidEnricher extends FaktocraftBlockEntity
    implements IEnergyBlock, IExpCollector, ISupportUpgrades, ITileSound {

  public static final int INPUT_SLOT = 0;
  public static final int INPUT_SLOT_2 = 1;

  public final FluidStorage fluidInputStorage = new FluidStorage(8000);
  public final FluidStorage fluidInputStorage2 = new FluidStorage(8000);
  public final FluidStorage fluidOutputStorage = new FluidStorage(8000).markOutputOnly();

  private int cachedInput = 0;
  private int cachedInput2 = 0;
  private int cachedOutput = 0;

  public final BlockEntityProgress progress = new BlockEntityProgress();
  @Nullable
  private FluidEnrichingRecipe recipe;
  private ItemStack cachedInputStack = ItemStack.EMPTY;
  private ItemStack cachedInputStack2 = ItemStack.EMPTY;
  private net.minecraft.world.level.material.Fluid cachedFluid1 = net.minecraft.world.level.material.Fluids.EMPTY;
  private net.minecraft.world.level.material.Fluid cachedFluid2 = net.minecraft.world.level.material.Fluids.EMPTY;

  private final LazyOptional<IFluidHandler> fluidPortsCap = LazyOptional.of(
      () -> new com.faktocraft.common.block.impl.machines.MachineFluidPorts(
          java.util.List.of(fluidInputStorage, fluidInputStorage2), java.util.List.of(fluidOutputStorage)));

  public BlockEntityFluidEnricher(BlockPos pos, BlockState state) {
    super(M3Registry.FLUID_ENRICHER_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().fluid_enricher_energy_capacity, EnergyType.RECEIVE, EnergyTier.MEDIUM);
    initBatterySlots();
    fluidInputStorage.setChangeListener(this::setChanged);
    fluidInputStorage2.setChangeListener(this::setChanged);
    fluidOutputStorage.setChangeListener(this::setChanged);
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 13, 26, InventorySlotType.INPUT, GuiSlotType.NORMAL, 12, 25));
    slots.add(new FaktocraftSlot(INPUT_SLOT_2, 13, 44, InventorySlotType.INPUT, GuiSlotType.NORMAL, 12, 43));
    return super.addInventorySlot(slots);
  }

  @Override
  public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
    slots.add(new SlotBattery(0, 152, 62, false));
    return super.addBatterySlot(slots);
  }

  protected Optional<FluidEnrichingRecipe> getRawRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<FluidEnrichingRecipe> findRecipe(ItemStack input) {
    return level.getRecipeManager().getRecipeFor(ModRecipeType.FLUID_ENRICHING, MachineRecipeInput.of(input),
        level);
  }

  @Nullable
  private int[] itemAssignment(FluidEnrichingRecipe enrichingRecipe, boolean checkCounts) {
    ItemStack a = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    ItemStack b = getItemStackHandler().getStackInSlot(INPUT_SLOT_2);
    CountedIngredient first = enrichingRecipe.getCountedIngredient();
    CountedIngredient second = enrichingRecipe.getCountedIngredient2().orElse(null);
    if (second == null) {
      if (fits(first, a, checkCounts)) {
        return new int[] { INPUT_SLOT, -1 };
      }
      if (fits(first, b, checkCounts)) {
        return new int[] { INPUT_SLOT_2, -1 };
      }
      return null;
    }
    if (fits(first, a, checkCounts) && fits(second, b, checkCounts)) {
      return new int[] { INPUT_SLOT, INPUT_SLOT_2 };
    }
    if (fits(first, b, checkCounts) && fits(second, a, checkCounts)) {
      return new int[] { INPUT_SLOT_2, INPUT_SLOT };
    }
    return null;
  }

  private static boolean fits(CountedIngredient ingredient, ItemStack stack, boolean checkCounts) {
    return ingredient.testType(stack) && (!checkCounts || stack.getCount() >= ingredient.count());
  }

  @Nullable
  private FluidStorage[] fluidAssignment(FluidEnrichingRecipe enrichingRecipe, boolean checkAmounts) {
    FluidIngredientData first = enrichingRecipe.getFluidInput();
    FluidIngredientData second = enrichingRecipe.getFluidInput2().orElse(null);
    if (second == null) {
      if (holds(fluidInputStorage, first, checkAmounts)) {
        return new FluidStorage[] { fluidInputStorage, null };
      }
      if (holds(fluidInputStorage2, first, checkAmounts)) {
        return new FluidStorage[] { fluidInputStorage2, null };
      }
      return null;
    }
    if (holds(fluidInputStorage, first, checkAmounts) && holds(fluidInputStorage2, second, checkAmounts)) {
      return new FluidStorage[] { fluidInputStorage, fluidInputStorage2 };
    }
    if (holds(fluidInputStorage2, first, checkAmounts) && holds(fluidInputStorage, second, checkAmounts)) {
      return new FluidStorage[] { fluidInputStorage2, fluidInputStorage };
    }
    return null;
  }

  private static boolean holds(FluidStorage tank, FluidIngredientData data, boolean checkAmounts) {
    return tank.getFluid() == data.getFluid() && (!checkAmounts || tank.getFluidAmount() >= data.amountMb());
  }

  protected Optional<FluidEnrichingRecipe> getRecipe() {
    if (!(level instanceof ServerLevel serverLevel)) {
      return Optional.empty();
    }
    List<FluidEnrichingRecipe> candidates = serverLevel.getRecipeManager()
        .getAllRecipesFor(ModRecipeType.FLUID_ENRICHING);
    for (FluidEnrichingRecipe enrichingRecipe : candidates) {
      if (itemAssignment(enrichingRecipe, false) != null && fluidAssignment(enrichingRecipe, false) != null) {
        return Optional.of(enrichingRecipe);
      }
    }
    return Optional.empty();
  }

  private boolean isValidInput(final ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    return level != null && findRecipe(stack).isPresent();
  }

  private boolean outputFits(FluidEnrichingRecipe currentRecipe) {
    return (currentRecipe.getResult().getFluid() == fluidOutputStorage.getFluid() || fluidOutputStorage.isEmpty())
        && fluidOutputStorage.fillFluid(
            new FluidStack(currentRecipe.getResult().getFluid(), currentRecipe.getResult().amountMb()),
            currentRecipe.getResult().amountMb(), true) == currentRecipe.getResult().amountMb();
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;
    boolean updateState = false;
    getEnergyStorage().updateConsumed(0);

    final ItemStack inputStack = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    final ItemStack inputStack2 = getItemStackHandler().getStackInSlot(INPUT_SLOT_2);

    if (cachedInput != fluidInputStorage.getFluidAmount()) {
      cachedInput = fluidInputStorage.getFluidAmount();
      updateState = true;
    }
    if (cachedInput2 != fluidInputStorage2.getFluidAmount()) {
      cachedInput2 = fluidInputStorage2.getFluidAmount();
      updateState = true;
    }
    if (cachedOutput != fluidOutputStorage.getFluidAmount()) {
      cachedOutput = fluidOutputStorage.getFluidAmount();
      updateState = true;
    }

    if (cachedInputStack.getItem() != inputStack.getItem()
        || cachedInputStack2.getItem() != inputStack2.getItem()
        || cachedFluid1 != fluidInputStorage.getFluid()
        || cachedFluid2 != fluidInputStorage2.getFluid()) {
      boolean itemsChanged = (!cachedInputStack.isEmpty() && cachedInputStack.getItem() != inputStack.getItem())
          || (!cachedInputStack2.isEmpty() && cachedInputStack2.getItem() != inputStack2.getItem());
      FluidEnrichingRecipe oldRecipe = recipe;
      cachedInputStack = inputStack.copy();
      cachedInputStack2 = inputStack2.copy();
      cachedFluid1 = fluidInputStorage.getFluid();
      cachedFluid2 = fluidInputStorage2.getFluid();
      recipe = (inputStack.getItem() != Items.AIR || inputStack2.getItem() != Items.AIR)
          ? getRecipe().orElse(null)
          : null;
      if (itemsChanged || (oldRecipe != null && oldRecipe != recipe)) {
        progress.setBoth(-1);
      }
    }

    if (recipe != null) {
      FluidEnrichingRecipe currentRecipe = recipe;
      int[] slots = itemAssignment(currentRecipe, true);
      FluidStorage[] tanks = fluidAssignment(currentRecipe, true);

      if (slots != null && tanks != null && outputFits(currentRecipe)) {
        if (progress.getProgress() == -1) {
          progress.setData(0, currentRecipe.getDuration());
        }
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
          StackHandlerHelper.shrinkInputStack(getItemStackHandler(), slots[0],
              currentRecipe.getCountedIngredient().count());
          if (slots[1] != -1) {
            StackHandlerHelper.shrinkInputStack(getItemStackHandler(), slots[1],
                currentRecipe.getCountedIngredient2().orElseThrow().count());
          }
          tanks[0].takeFluid(currentRecipe.getFluidInput().amountMb(), false);
          if (tanks[1] != null) {
            tanks[1].takeFluid(currentRecipe.getFluidInput2().orElseThrow().amountMb(), false);
          }
          fluidOutputStorage.fillFluid(
              new FluidStack(currentRecipe.getResult().getFluid(), currentRecipe.getResult().amountMb()),
              currentRecipe.getResult().amountMb(), false);

          addRecipeUsed(recipe);
          progress.setBoth(-1);
        }
      }
    } else {
      progress.setBoth(-1);
    }

    if (progress.changed()) {
      progress.clearChanged();
      updateState = true;
    }

    setActive(active);
    if (updateState) {
      updateBlockState();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    CompoundTag fluidInputTag = new CompoundTag();
    fluidInputStorage.save(fluidInputTag);
    tag.put("fluidInputStorage", fluidInputTag);
    CompoundTag fluidInput2Tag = new CompoundTag();
    fluidInputStorage2.save(fluidInput2Tag);
    tag.put("fluidInputStorage2", fluidInput2Tag);
    CompoundTag fluidOutputTag = new CompoundTag();
    fluidOutputStorage.save(fluidOutputTag);
    tag.put("fluidOutputStorage", fluidOutputTag);
    tag.putBoolean("active", activeState);
    CompoundTag progressTag = new CompoundTag();
    progress.save(progressTag);
    tag.put("progress", progressTag);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    if (tag.contains("fluidInputStorage")) {
      fluidInputStorage.load(tag.getCompound("fluidInputStorage"));
    }
    if (tag.contains("fluidInputStorage2")) {
      fluidInputStorage2.load(tag.getCompound("fluidInputStorage2"));
    }
    if (tag.contains("fluidOutputStorage")) {
      fluidOutputStorage.load(tag.getCompound("fluidOutputStorage"));
    }
    this.activeState = tag.getBoolean("active");
    if (tag.contains("progress")) {
      progress.load(tag.getCompound("progress"));
    }
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    return ((FluidEnrichingRecipe) recipe).getExperience();
  }

  @Override
  public Runnable collectExp() {
    BlockPos pos = getBlockPos();
    return () -> ModNetworking.sendToServer(new PacketExperience(pos));
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    return (slot == INPUT_SLOT || slot == INPUT_SLOT_2) && isValidInput(stack);
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
      if (isFrontSide(side)) {
        return LazyOptional.empty();
      }
      return fluidPortsCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    fluidPortsCap.invalidate();
  }

  @Override
  public java.util.List<com.faktocraft.common.entity.block.FluidStorage> getGuiTanks() {
    return java.util.List.of(fluidInputStorage, fluidInputStorage2, fluidOutputStorage);
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.FLUID_ENRICHER;
  }
}
