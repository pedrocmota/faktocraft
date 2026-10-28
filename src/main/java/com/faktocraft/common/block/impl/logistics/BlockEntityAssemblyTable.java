package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.BlockEntityProgress;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.UpgradeType;
import com.faktocraft.common.interfaces.entity.ISupportUpgrades;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockEntityAssemblyTable extends FaktocraftBlockEntity
    implements IEnergyBlock, ISupportUpgrades, ITileSound {

  public static final int ZONE_SIZE = 9;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  private final ItemStackHandler ingredients = new ItemStackHandler(ZONE_SIZE) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityAssemblyTable.this.setChanged();
    }
  };
  private final ItemStackHandler output = new ItemStackHandler(ZONE_SIZE) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityAssemblyTable.this.setChanged();
    }
  };

  public BlockEntityAssemblyTable(BlockPos pos, BlockState state) {
    super(LogisticsRegistry.ASSEMBLY_TABLE_BLOCK_ENTITY, pos, state);

    createEnergyStorage(0, ModConfig.server().assembly_table_energy_capacity, EnergyType.RECEIVE,
        EnergyTier.LOW);
    initBatterySlots();
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  @Override
  public List<UpgradeType> getSupportedUpgrades() {
    return List.of(UpgradeType.OVERCLOCKER, UpgradeType.EFFICIENCY);
  }

  public int craftDuration() {
    return Math.max(1, Math.round(getSpeedFactor() * ModConfig.server().assembly_table_duration));
  }

  public int tickUsage() {
    return Math.max(1, Math.round(ModConfig.server().assembly_table_tick_usage * getEnergyUsageFactor()));
  }

  public ItemStackHandler getIngredients() {
    return ingredients;
  }

  public ItemStackHandler getOutput() {
    return output;
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
      return LazyOptional.empty();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide()) {
      LogisticsCores.markDirtyNear(level, worldPosition);
    }
  }

  @Nullable
  private BlockPos cachedPipePos;
  private int pipeRecheck;

  @Nullable
  public BlockEntityCraftPipe craftPipe() {
    if (level == null) {
      return null;
    }
    if (cachedPipePos != null && --pipeRecheck > 0
        && level.getBlockEntity(cachedPipePos) instanceof BlockEntityCraftPipe cached) {
      return cached;
    }
    pipeRecheck = 20;
    for (Direction direction : Direction.values()) {
      if (level.getBlockEntity(worldPosition.relative(direction)) instanceof BlockEntityCraftPipe pipe
          && worldPosition.equals(pipe.dockedPos())) {
        cachedPipePos = pipe.getBlockPos();
        return pipe;
      }
    }
    cachedPipePos = null;
    return null;
  }

  private int activeRecipe = -1;

  public int displayRecipeIndex() {
    if (activeRecipe >= 0) {
      return activeRecipe;
    }
    BlockEntityCraftPipe pipe = craftPipe();
    if (pipe == null || pipe.recipeCount() == 0) {
      return -1;
    }
    for (int index = 0; index < pipe.recipeCount(); index++) {
      if (pipe.patternRecipe(index) != null) {
        return index;
      }
    }
    return 0;
  }

  private int pickRecipe(BlockEntityCraftPipe pipe) {
    ItemKey wanted = wantedResult(pipe);
    if (activeRecipe >= 0 && activeRecipe < pipe.recipeCount()) {
      CraftingRecipe recipe = pipe.patternRecipe(activeRecipe);
      if (recipe != null && makes(pipe, activeRecipe, wanted) && tryCraft(pipe, activeRecipe, recipe, true)) {
        return activeRecipe;
      }
    }
    for (int index = 0; index < pipe.recipeCount(); index++) {
      if (index == activeRecipe) {
        continue;
      }
      CraftingRecipe recipe = pipe.patternRecipe(index);
      if (recipe != null && makes(pipe, index, wanted) && tryCraft(pipe, index, recipe, true)) {
        return index;
      }
    }
    return -1;
  }

  @Nullable
  private ItemKey wantedResult(BlockEntityCraftPipe pipe) {
    if (level == null) {
      return null;
    }
    BlockEntityLogisticsController core = LogisticsCores.coreFor(level, pipe.getBlockPos());
    return core != null ? core.getLedger().claimedResult(worldPosition) : null;
  }

  private static boolean makes(BlockEntityCraftPipe pipe, int index, @Nullable ItemKey wanted) {
    return wanted == null || wanted.equals(ItemKey.of(pipe.patternResult(index)));
  }

  @Override
  public void tickWork(BlockState state) {
    if (level == null || level.isClientSide()) {
      return;
    }
    boolean active = false;
    BlockEntityCraftPipe pipe = craftPipe();
    int picked = pipe != null ? pickRecipe(pipe) : -1;

    if (picked != activeRecipe) {
      activeRecipe = picked;
      progress.setBoth(-1);
      setChanged();
    }
    CraftingRecipe recipe = pipe != null && picked >= 0 ? pipe.patternRecipe(picked) : null;
    if (recipe != null) {
      int duration = craftDuration();
      if (progress.getProgressMax() != duration) {
        progress.setData(Math.max(0, progress.getProgress()), duration);
      }
      int cost = tickUsage();
      if (getEnergyStorage().consumeEnergy(cost, true) == cost) {
        active = true;
        getEnergyStorage().consumeEnergy(cost, false);
        getEnergyStorage().updateConsumed(cost);
        progress.incProgress(1);
        if (progress.getProgress() >= duration) {
          tryCraft(pipe, picked, recipe, false);
          progress.setData(0, duration);
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

  private boolean tryCraft(BlockEntityCraftPipe pipe, int index, CraftingRecipe recipe, boolean simulate) {
    if (level == null || pipe == null) {
      return false;
    }
    List<List<Item>> cells = pipe.cellOptions(index);
    if (cells.size() != BlockEntityCraftPipe.PATTERN_SIZE) {
      return false;
    }

    ItemStackHandler claimed = new ItemStackHandler(BlockEntityCraftPipe.PATTERN_SIZE);
    int[] claims = new int[ingredients.getSlots()];
    List<Integer> consumeSlots = new ArrayList<>();
    for (int cell = 0; cell < BlockEntityCraftPipe.PATTERN_SIZE; cell++) {
      List<Item> options = cells.get(cell);
      if (options.isEmpty()) {
        continue;
      }

      int slot = claimSlot(options, claims, true);
      if (slot < 0) {
        slot = claimSlot(options, claims, false);
      }
      if (slot < 0) {
        return false;
      }
      claims[slot]++;
      consumeSlots.add(slot);
      claimed.setStackInSlot(cell, ingredients.getStackInSlot(slot).copyWithCount(1));
    }
    if (consumeSlots.isEmpty()) {
      return false;
    }
    CraftingContainer view = BlockEntityCraftPipe.viewOf(claimed);

    if (!recipe.matches(view, level)) {
      return false;
    }
    ItemStack result = recipe.assemble(view, level.registryAccess());
    if (result.isEmpty()) {
      return false;
    }
    InvWrapper outputWrapper = new InvWrapper(output);
    List<ItemStack> toInsert = new ArrayList<>();
    toInsert.add(result);
    for (ItemStack remainder : recipe.getRemainingItems(view)) {
      if (!remainder.isEmpty()) {
        toInsert.add(remainder);
      }
    }

    ItemStackHandler ghost = new ItemStackHandler(ZONE_SIZE);
    for (int i = 0; i < ZONE_SIZE; i++) {
      ghost.setStackInSlot(i, output.getStackInSlot(i).copy());
    }
    InvWrapper ghostWrapper = new InvWrapper(ghost);
    for (ItemStack stack : toInsert) {
      if (!ItemHandlerHelper.insertItemStacked(ghostWrapper, stack.copy(), false).isEmpty()) {
        return false;
      }
    }
    if (simulate) {
      return true;
    }
    for (int slot : consumeSlots) {
      ingredients.getStackInSlot(slot).shrink(1);
      if (ingredients.getStackInSlot(slot).isEmpty()) {
        ingredients.setStackInSlot(slot, ItemStack.EMPTY);
      }
    }
    ingredients.setChanged();
    for (ItemStack stack : toInsert) {
      ItemHandlerHelper.insertItemStacked(outputWrapper, stack, false);
    }
    return true;
  }

  private int claimSlot(List<Item> options, int[] claims, boolean declaredOnly) {
    for (int slot = 0; slot < ingredients.getSlots(); slot++) {
      ItemStack stack = ingredients.getStackInSlot(slot);
      if (stack.isEmpty() || stack.getCount() <= claims[slot]) {
        continue;
      }
      if (declaredOnly ? stack.is(options.get(0)) : options.contains(stack.getItem())) {
        return slot;
      }
    }
    return -1;
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    return super.addInventorySlot(slots);
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    return true;
  }

  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    if (level != null && !level.isClientSide()) {
      for (ItemStackHandler handler : new ItemStackHandler[] { ingredients, output }) {
        for (int i = 0; i < handler.getSlots(); i++) {
          ItemStack stack = handler.getStackInSlot(i);
          if (!stack.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
          }
        }
      }
    }
    super.preRemoveSideEffects(pos, state);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    CompoundTag ingredientsTag = new CompoundTag();
    ingredients.save(ingredientsTag);
    tag.put("ingredients", ingredientsTag);
    CompoundTag outputTag = new CompoundTag();
    output.save(outputTag);
    tag.put("output", outputTag);
    tag.putFloat("progress", progress.getProgress());
    tag.putFloat("progressMax", progress.getProgressMax());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    if (tag.contains("ingredients")) {
      ingredients.load(tag.getCompound("ingredients"));
    }
    if (tag.contains("output")) {
      output.load(tag.getCompound("output"));
    }
    progress.setData(tag.getFloat("progress"), tag.getFloat("progressMax"));
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.CANNING_MACHINE;
  }
}
