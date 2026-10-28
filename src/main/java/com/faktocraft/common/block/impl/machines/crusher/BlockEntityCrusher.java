package com.faktocraft.common.block.impl.machines.crusher;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.entity.block.BlockEntityStandardMachine;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.CrushingRecipe;
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
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BlockEntityCrusher extends BlockEntityStandardMachine {

  private final RecipeManager.CachedCheck<Container, CrushingRecipe> quickCheck = RecipeManager
      .createCheck(ModRecipeType.CRUSHING);

  private final Map<Direction, LazyOptional<IItemHandler>> sidedItemCaps = new EnumMap<>(Direction.class);
  private LazyOptional<IItemHandler> nullSideItemCap = LazyOptional.empty();

  public BlockEntityCrusher(BlockPos pos, BlockState state) {
    super(M2Registry.CRUSHER_BE, pos, state, ModConfig.server().crusher_energy_capacity);
  }

  @Override
  protected Optional<CrushingRecipe> getRecipe(ItemStack input) {
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
    return ModSounds.CRUSHER;
  }

  public IItemHandler getItemStorageForSide(@Nullable Direction side) {
    if (side == null) {
      return new InvWrapper(getItemStackHandler());
    }
    if (side == Direction.DOWN) {
      return createSlotTypeStorage(InventorySlotType.OUTPUT, InventorySlotType.BONUS);
    }
    return createSlotTypeStorage(InventorySlotType.INPUT);
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
}
