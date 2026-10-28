package com.faktocraft.common.block.impl.machines.uranium_centrifuge;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.entity.block.BlockEntityStandardMachine;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.UraniumCentrifugingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M3Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;

public class BlockEntityUraniumCentrifuge extends BlockEntityStandardMachine {

  private final RecipeManager.CachedCheck<Container, UraniumCentrifugingRecipe> quickCheck = RecipeManager
      .createCheck(ModRecipeType.URANIUM_CENTRIFUGING);

  public float drumAngle;
  public float drumSpeed;
  public double drumLastTime = Double.NaN;

  public BlockEntityUraniumCentrifuge(BlockPos pos, BlockState state) {
    super(M3Registry.URANIUM_CENTRIFUGE_BLOCK_ENTITY, pos, state,
        ModConfig.server().uranium_centrifuge_energy_capacity, EnergyTier.MEDIUM);
  }

  @Override
  protected Optional<UraniumCentrifugingRecipe> getRecipe(ItemStack input) {
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
    return ModSounds.URANIUM_CENTRIFUGE;
  }

  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER && side != null && side != Direction.DOWN) {
      return LazyOptional.empty();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public AABB getRenderBoundingBox() {
    return new AABB(worldPosition).expandTowards(0.0, 0.2, 0.0);
  }
}
