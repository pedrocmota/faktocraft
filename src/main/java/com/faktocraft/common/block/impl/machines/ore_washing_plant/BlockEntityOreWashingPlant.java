package com.faktocraft.common.block.impl.machines.ore_washing_plant;

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
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.interfaces.entity.IElectricSlot;
import com.faktocraft.common.interfaces.entity.IExpCollector;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.OreWashingRecipe;
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
import net.minecraft.world.level.material.Fluids;
import com.faktocraft.common.util.transfer.Capability;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.LazyOptional;
import net.neoforged.neoforge.fluids.FluidStack;
import com.faktocraft.common.util.transfer.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockEntityOreWashingPlant extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound, IExpCollector {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT_1 = 1;
  public static final int OUTPUT_SLOT_2 = 2;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private int cachedWater = 0;
  private int cachedAcid = 0;
  private ItemStack cachedInputStack = ItemStack.EMPTY;

  public final FluidStorage waterStorage = new FluidStorage(
      ModConfig.server().ore_washing_plant_fluid_capacity,
      stack -> stack.getFluid().isSame(Fluids.WATER));
  public final FluidStorage acidStorage = new FluidStorage(
      ModConfig.server().ore_washing_plant_fluid_capacity,
      stack -> stack.getFluid().isSame(ModFluids.SULFURIC_ACID.still()));

  private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(RoutingHandler::new);

  @Nullable
  protected OreWashingRecipe recipe;

  public BlockEntityOreWashingPlant(BlockPos pos, BlockState state) {
    super(M3Registry.ORE_WASHING_PLANT_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().ore_washing_plant_energy_capacity, EnergyType.RECEIVE, EnergyTier.MEDIUM);
    initBatterySlots();
    waterStorage.setChangeListener(this::setChanged);
    acidStorage.setChangeListener(this::setChanged);
  }

  protected Optional<OreWashingRecipe> getRawRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<OreWashingRecipe> findRecipe(ItemStack input) {
    return RecipeUtil.findRecipe(level, ModRecipeType.ORE_WASHING, MachineRecipeInput.of(input));
  }

  protected Optional<OreWashingRecipe> getRecipe(ItemStack input) {
    return getRawRecipe(input);
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    return ((OreWashingRecipe) recipe).getExperience();
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

  private int acidCost(OreWashingRecipe currentRecipe) {
    return currentRecipe.getAcidInput().map(acid -> acid.amountMb()).orElse(0);
  }

  private boolean hasFluids(OreWashingRecipe currentRecipe) {
    return waterStorage.getFluidAmount() >= currentRecipe.getFluidInput().amountMb()
        && acidStorage.getFluidAmount() >= acidCost(currentRecipe);
  }

  private boolean canWork(ItemStack inputStack, OreWashingRecipe currentRecipe) {
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
    boolean updateState = false;
    getEnergyStorage().updateConsumed(0);

    final ItemStack inputStack = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    final ItemStack outputSlot1 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_1);
    final ItemStack outputSlot2 = getItemStackHandler().getStackInSlot(OUTPUT_SLOT_2);

    if (cachedWater != waterStorage.getFluidAmount()) {
      cachedWater = waterStorage.getFluidAmount();
      updateState = true;
    }
    if (cachedAcid != acidStorage.getFluidAmount()) {
      cachedAcid = acidStorage.getFluidAmount();
      updateState = true;
    }

    if (cachedInputStack.getItem() != inputStack.getItem()) {
      cachedInputStack = inputStack.copy();
      OreWashingRecipe oldRecipe = recipe;
      recipe = inputStack.getItem() != Items.AIR ? getRecipe(inputStack).orElse(null) : null;
      if (recipe != oldRecipe) {

        progress.setBoth(-1);
      }
    }

    if (recipe != null && hasFluids(recipe)) {
      OreWashingRecipe currentRecipe = recipe;

      if (progress.getProgress() == -1) {
        progress.setData(0, currentRecipe.getDuration());
      }

      progress.rescaleMax(getSpeedFactor() * currentRecipe.getDuration());
      int energyCost = EnergyCosts.perTick(currentRecipe.getPowerCost(), getEnergyUsageFactor());

      if (canWork(inputStack, currentRecipe)) {
        if (getEnergyStorage().consumeEnergy(energyCost, true) == energyCost
            && progress.getProgress() <= progress.getProgressMax()) {
          active = true;
          progress.incProgress(1);
          getEnergyStorage().consumeEnergy(energyCost, false);
          getEnergyStorage().updateConsumed(energyCost);
        }

        if (progress.getProgress() >= progress.getProgressMax()) {
          inputStack.shrink(currentRecipe.getIngredientCount());
          getItemStackHandler().setStackInSlot(INPUT_SLOT, inputStack.copy());
          waterStorage.takeFluid(currentRecipe.getFluidInput().amountMb(), false);
          if (acidCost(currentRecipe) > 0) {
            acidStorage.takeFluid(acidCost(currentRecipe), false);
          }

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
      }
    } else if (recipe == null) {
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
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 62, 35, InventorySlotType.INPUT, GuiSlotType.NORMAL, 61, 34));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT_1, 122, 25, InventorySlotType.OUTPUT, GuiSlotType.NORMAL, 121, 24));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT_2, 122, 44, InventorySlotType.OUTPUT, GuiSlotType.NORMAL, 121, 43));
    return super.addInventorySlot(slots);
  }

  @Override
  public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
    slots.add(new SlotBattery(0, 152, 62, false));
    return super.addBatterySlot(slots);
  }

  @Override
  public java.util.List<FluidStorage> getGuiTanks() {
    return java.util.List.of(waterStorage, acidStorage);
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.ORE_WASHING_PLANT;
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    return slot == INPUT_SLOT && isValidInput(stack);
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
      if (isFrontSide(side)) {
        return LazyOptional.empty();
      }
      return fluidHandlerCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    fluidHandlerCap.invalidate();
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    tag.putBoolean("active", activeState);

    CompoundTag progressTag = new CompoundTag();
    progress.save(progressTag);
    tag.put("progress", progressTag);

    CompoundTag waterTag = new CompoundTag();
    waterStorage.save(waterTag);
    tag.put("waterStorage", waterTag);

    CompoundTag acidTag = new CompoundTag();
    acidStorage.save(acidTag);
    tag.put("acidStorage", acidTag);

    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    this.activeState = tag.getBooleanOr("active", false);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
    if (tag.contains("waterStorage")) {
      waterStorage.load(tag.getCompoundOrEmpty("waterStorage"));
    }
    if (tag.contains("acidStorage")) {
      acidStorage.load(tag.getCompoundOrEmpty("acidStorage"));
    }
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  private class RoutingHandler implements IFluidHandler {
    @Override
    public int getTanks() {
      return 2;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
      return tankByIndex(tank).getFluidStack();
    }

    @Override
    public int getTankCapacity(int tank) {
      return tankByIndex(tank).getCapacityMb();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
      return routeFor(stack) == tankByIndex(tank);
    }

    private FluidStorage tankByIndex(int tank) {
      return tank == 0 ? waterStorage : acidStorage;
    }

    @Nullable
    private FluidStorage routeFor(FluidStack stack) {
      if (stack.getFluid().isSame(Fluids.WATER)) {
        return waterStorage;
      }
      if (stack.getFluid().isSame(ModFluids.SULFURIC_ACID.still())) {
        return acidStorage;
      }
      return null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      FluidStorage target = routeFor(resource);
      if (target == null) {
        return 0;
      }
      return target.fillFluid(resource, resource.getAmount(), action.simulate());
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
      return FluidStack.EMPTY;
    }
  }
}
