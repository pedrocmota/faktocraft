package com.faktocraft.common.block.impl.machines.polymerizer;

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
import com.faktocraft.common.recipe.impl.PolymerizingRecipe;
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

public class BlockEntityPolymerizer extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound, IExpCollector {

  public static final int INPUT_SLOT = 0;
  public static final int OUTPUT_SLOT = 1;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private int cachedOil = 0;
  private ItemStack cachedInputStack = ItemStack.EMPTY;

  public final FluidStorage oilStorage = new FluidStorage(
      ModConfig.server().polymerizer_fluid_capacity,
      stack -> stack.getFluid().isSame(ModFluids.OIL.still()));

  private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(OilFillHandler::new);

  @Nullable
  protected PolymerizingRecipe recipe;

  public BlockEntityPolymerizer(BlockPos pos, BlockState state) {
    super(M3Registry.POLYMERIZER_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, ModConfig.server().polymerizer_energy_capacity, EnergyType.RECEIVE, EnergyTier.MEDIUM);
    initBatterySlots();
    oilStorage.setChangeListener(this::setChanged);
  }

  protected Optional<PolymerizingRecipe> getRawRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<PolymerizingRecipe> findRecipe(ItemStack input) {
    return RecipeUtil.findRecipe(level, ModRecipeType.POLYMERIZING, MachineRecipeInput.of(input));
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    return ((PolymerizingRecipe) recipe).getExperience();
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

  private boolean canWork(ItemStack inputStack, PolymerizingRecipe currentRecipe) {
    if (inputStack.getCount() < currentRecipe.getIngredientCount()) {
      return false;
    }
    ItemStack result = currentRecipe.getResult();
    ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);
    return outputStack.isEmpty()
        || (outputStack.getItem() == result.getItem()
            && outputStack.getCount() + result.getCount() <= outputStack.getMaxStackSize());
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;
    boolean updateState = false;
    getEnergyStorage().updateConsumed(0);

    final ItemStack inputStack = getItemStackHandler().getStackInSlot(INPUT_SLOT);
    final ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);

    if (cachedOil != oilStorage.getFluidAmount()) {
      cachedOil = oilStorage.getFluidAmount();
      updateState = true;
    }

    if (cachedInputStack.getItem() != inputStack.getItem()) {
      boolean hadInput = !cachedInputStack.isEmpty();
      PolymerizingRecipe oldRecipe = recipe;
      cachedInputStack = inputStack.copy();
      recipe = inputStack.getItem() != Items.AIR ? getRawRecipe(inputStack).orElse(null) : null;
      if (hadInput || (oldRecipe != null && oldRecipe != recipe)) {
        progress.setBoth(-1);
      }
    }

    if (recipe != null && oilStorage.getFluidAmount() >= recipe.getFluidInput().amountMb()) {
      PolymerizingRecipe currentRecipe = recipe;

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
          oilStorage.takeFluid(currentRecipe.getFluidInput().amountMb(), false);

          ItemStack result = currentRecipe.getResult();
          if (outputStack.isEmpty()) {
            getItemStackHandler().setStackInSlot(OUTPUT_SLOT, result);
          } else {
            getItemStackHandler().getStackInSlot(OUTPUT_SLOT).grow(result.getCount());
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
    updateGaugeLevel();
  }

  private void updateGaugeLevel() {
    int lvl = oilStorage.getFluidAmount() <= 0 ? 0
        : Math.max(1, Math.min(4, (int) Math.ceil(oilStorage.getFluidAmount() * 4.0 / oilStorage.getCapacityMb())));
    BlockState current = level.getBlockState(getBlockPos());
    if (current.hasProperty(BlockPolymerizer.LEVEL)
        && current.getValue(BlockPolymerizer.LEVEL) != lvl) {
      level.setBlockAndUpdate(getBlockPos(), current.setValue(BlockPolymerizer.LEVEL, lvl));
    }
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 62, 35, InventorySlotType.INPUT, GuiSlotType.NORMAL, 61, 34));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT, 122, 35, InventorySlotType.OUTPUT, GuiSlotType.NORMAL, 121, 34));
    return super.addInventorySlot(slots);
  }

  @Override
  public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
    slots.add(new SlotBattery(0, 152, 62, false));
    return super.addBatterySlot(slots);
  }

  @Override
  public List<FluidStorage> getGuiTanks() {
    return List.of(oilStorage);
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.POLYMERIZER;
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

    CompoundTag oilTag = new CompoundTag();
    oilStorage.save(oilTag);
    tag.put("oilStorage", oilTag);

    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    this.activeState = tag.getBooleanOr("active", false);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
    if (tag.contains("oilStorage")) {
      oilStorage.load(tag.getCompoundOrEmpty("oilStorage"));
    }
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  private class OilFillHandler implements IFluidHandler {
    @Override
    public int getTanks() {
      return 1;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
      return oilStorage.getFluidStack();
    }

    @Override
    public int getTankCapacity(int tank) {
      return oilStorage.getCapacityMb();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
      return stack.getFluid().isSame(ModFluids.OIL.still());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      if (!isFluidValid(0, resource)) {
        return 0;
      }
      return oilStorage.fillFluid(resource, resource.getAmount(), action.simulate());
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
