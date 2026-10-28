package com.faktocraft.common.block.impl.machines.sawmill;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.entity.block.BlockEntityStandardMachine;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.SawingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M2Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;

public class BlockEntitySawmill extends BlockEntityStandardMachine {

  private final RecipeManager.CachedCheck<Container, SawingRecipe> quickCheck = RecipeManager
      .createCheck(ModRecipeType.SAWING);

  private final LazyOptional<IItemHandler> itemHandlerDown = LazyOptional
      .of(() -> createSlotTypeStorage(InventorySlotType.OUTPUT, InventorySlotType.BONUS));
  private final LazyOptional<IItemHandler> itemHandlerSides = LazyOptional
      .of(() -> createSlotTypeStorage(InventorySlotType.INPUT));

  public BlockEntitySawmill(BlockPos pos, BlockState state) {
    super(M2Registry.SAWMILL_BE, pos, state, ModConfig.server().sawmill_energy_capacity);
  }

  @Override
  protected Optional<SawingRecipe> getRecipe(ItemStack input) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return Optional.empty();
    }
    return quickCheck.getRecipeFor(MachineRecipeInput.of(input), serverLevel);
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot != INPUT_SLOT) {
      return false;
    }
    return level != null && quickCheck.getRecipeFor(MachineRecipeInput.of(stack), level).isPresent();
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.SAWMILL;
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
}
