package com.faktocraft.common.block.impl.machines.iron_furnace;

import com.faktocraft.common.util.FuelUtil;
import com.faktocraft.common.util.ItemStackUtil;
import com.faktocraft.common.entity.block.BlockEntityProgress;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.enums.GuiSlotType;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.interfaces.entity.IExpCollector;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.StackHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import com.faktocraft.common.util.RecipeUtil;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.Capability;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.LazyOptional;
import com.faktocraft.common.util.transfer.IItemHandler;
import com.faktocraft.common.util.transfer.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BlockEntityIronFurnace extends FaktocraftBlockEntity implements IExpCollector {

  public static final int FUEL_SLOT = 0;
  public static final int INPUT_SLOT = 1;
  public static final int OUTPUT_SLOT = 2;

  public final BlockEntityProgress smelting = new BlockEntityProgress();
  public final BlockEntityProgress fuel = new BlockEntityProgress();

  private final RecipeUtil.CachedCheck<SingleRecipeInput, SmeltingRecipe> quickCheck = RecipeUtil
      .createCheck(RecipeType.SMELTING);

  private ItemStack cachedInputStack = ItemStack.EMPTY;
  private ItemStack resultStack = ItemStack.EMPTY;
  @Nullable
  private SmeltingRecipe furnaceRecipe;

  private final Map<Direction, LazyOptional<IItemHandler>> sidedItemCaps = new EnumMap<>(Direction.class);
  private LazyOptional<IItemHandler> nullSideItemCap = LazyOptional.empty();

  public BlockEntityIronFurnace(BlockPos pos, BlockState state) {
    super(M2Registry.IRON_FURNACE_BE, pos, state);
  }

  protected Optional<SmeltingRecipe> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return Optional.empty();
    }
    return quickCheck.getRecipeFor(new SingleRecipeInput(input), serverLevel);
  }

  protected ItemStack getRecipeResult(ItemStack stack) {
    return furnaceRecipe != null && level != null
        ? furnaceRecipe.assemble(new SingleRecipeInput(stack))
        : ItemStack.EMPTY;
  }

  private boolean canSmelt(ItemStack inputStack, ItemStack outputStack, ItemStack resultStack) {
    return !inputStack.isEmpty() && !resultStack.isEmpty()
        && outputStack.getCount() < outputStack.getMaxStackSize()
        && (outputStack.isEmpty() || ItemStack.isSameItemSameComponents(outputStack, resultStack));
  }

  private float getSmeltTime() {
    return furnaceRecipe != null ? furnaceRecipe.cookingTime() : 200;
  }

  @Override
  public void tickWork(BlockState state) {
    if (level == null || level.isClientSide()) {
      return;
    }

    boolean active = false;

    final ItemStack inputStack = itemStackHandler.getStackInSlot(INPUT_SLOT);

    if (!ItemStack.isSameItemSameComponents(cachedInputStack, inputStack)) {
      boolean hadInput = !cachedInputStack.isEmpty();
      SmeltingRecipe oldRecipe = furnaceRecipe;
      cachedInputStack = inputStack.copy();
      furnaceRecipe = inputStack.isEmpty() ? null : getRecipe(inputStack).orElse(null);
      resultStack = furnaceRecipe != null ? getRecipeResult(inputStack) : ItemStack.EMPTY;
      if (hadInput || (oldRecipe != null && oldRecipe != furnaceRecipe)) {
        smelting.setBoth(-1);
      }
    }

    final ItemStack fuelItemStack = itemStackHandler.getStackInSlot(FUEL_SLOT);
    final ItemStack outputItemStack = itemStackHandler.getStackInSlot(OUTPUT_SLOT);
    final boolean canSmelt = furnaceRecipe != null && canSmelt(inputStack, outputItemStack, resultStack);

    if (fuel.getProgress() > 0) {
      active = true;
      fuel.decProgress(1);

      if (inputStack.isEmpty()) {
        smelting.setBoth(-1);
      } else if (canSmelt) {
        if (smelting.getProgress() == -1) {
          smelting.setData(0, getSmeltTime() * 0.80F);
        } else {
          smelting.incProgress(1);
          if (smelting.getProgress() >= smelting.getProgressMax()) {
            StackHandlerHelper.incMachineOutputStack(itemStackHandler, OUTPUT_SLOT, resultStack.copy());
            StackHandlerHelper.shrinkInputStack(itemStackHandler, INPUT_SLOT, 1);
            addRecipeUsed(furnaceRecipe);
            smelting.setBoth(-1);
          }
        }
      }
    } else {
      fuel.setBoth(-1);

      if (smelting.getProgress() > 0) {
        smelting.decProgress(1);
      }

      if (canSmelt) {
        final int burnTime = FuelUtil.burnTime(level, fuelItemStack, this);
        if (burnTime > 0) {
          fuel.setBoth(burnTime * 1.20F);

          ItemStack remainder = ItemStackUtil.craftingRemainder(fuelItemStack);
          StackHandlerHelper.shrinkInputStack(itemStackHandler, FUEL_SLOT, 1);
          if (!remainder.isEmpty()) {
            if (itemStackHandler.getStackInSlot(FUEL_SLOT).isEmpty()) {
              itemStackHandler.setStackInSlot(FUEL_SLOT, remainder);
            } else if (level != null) {
              net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                  worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, remainder);
            }
          }
          active = true;
        }
      }
    }

    setActive(active);

    if (fuel.changed() || smelting.changed()) {
      fuel.clearChanged();
      smelting.clearChanged();
      updateBlockState();
    }
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot == INPUT_SLOT) {
      return quickCheck.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
    }
    if (slot == FUEL_SLOT) {
      return level != null && FuelUtil.burnTime(level, stack, this) > 0;
    }
    return false;
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT, 56, 17, InventorySlotType.INPUT, GuiSlotType.NORMAL, 55, 16));
    slots.add(new FaktocraftSlot(FUEL_SLOT, 56, 53, InventorySlotType.NORMAL, GuiSlotType.NORMAL, 55, 52));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT, 116, 35, InventorySlotType.OUTPUT, GuiSlotType.LARGE, 111, 30));
    return super.addInventorySlot(slots);
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
      return cookingRecipe.experience();
    }
    return 0;
  }

  @Override
  public boolean hasExpButton() {
    return false;
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
      default -> createSlotTypeStorage(InventorySlotType.NORMAL);
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
    CompoundTag smeltingTag = new CompoundTag();
    smelting.save(smeltingTag);
    tag.put("smelting", smeltingTag);
    CompoundTag fuelTag = new CompoundTag();
    fuel.save(fuelTag);
    tag.put("fuel", fuelTag);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    activeState = tag.getBooleanOr("active", false);
    if (tag.contains("smelting")) {
      smelting.load(tag.getCompoundOrEmpty("smelting"));
    }
    if (tag.contains("fuel")) {
      fuel.load(tag.getCompoundOrEmpty("fuel"));
    }
  }

}
