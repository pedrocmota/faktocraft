package com.faktocraft.common.block.impl.machines.alloy_smelter;

import com.faktocraft.common.entity.block.BlockEntityProgress;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.enums.GuiSlotType;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.interfaces.entity.IExpCollector;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketExperience;
import com.faktocraft.common.recipe.MachineRecipeInput;
import com.faktocraft.common.recipe.impl.AlloySmeltingRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import com.faktocraft.common.util.RecipeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Optional;

public abstract class AbstractBlockEntityAlloySmelter extends FaktocraftBlockEntity implements IExpCollector {

  public static final int INPUT_SLOT_0 = 0;
  public static final int INPUT_SLOT_1 = 1;
  public static final int INPUT_SLOT_2 = 2;
  public static final int OUTPUT_SLOT = 3;

  public final BlockEntityProgress progress = new BlockEntityProgress();

  @Nullable
  protected AlloySmeltingRecipe recipe;
  protected int energyCostPerTick = 0;
  protected int duration = 0;

  private ItemStack resultStack = ItemStack.EMPTY;
  private ItemStack cachedInputStack0 = ItemStack.EMPTY;
  private ItemStack cachedInputStack1 = ItemStack.EMPTY;
  private ItemStack cachedInputStack2 = ItemStack.EMPTY;
  private ItemStack cachedOutput = ItemStack.EMPTY;
  private boolean cachedWork;

  protected AbstractBlockEntityAlloySmelter(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(INPUT_SLOT_0, 16, 33, InventorySlotType.INPUT, GuiSlotType.NORMAL, 15, 32));
    slots.add(new FaktocraftSlot(INPUT_SLOT_1, 37, 21, InventorySlotType.INPUT, GuiSlotType.NORMAL, 36, 20));
    slots.add(new FaktocraftSlot(INPUT_SLOT_2, 58, 33, InventorySlotType.INPUT, GuiSlotType.NORMAL, 57, 32));
    slots.add(new FaktocraftSlot(OUTPUT_SLOT, 118, 33, InventorySlotType.OUTPUT, GuiSlotType.LARGE, 113, 28));
    addExtraSlots(slots);
    return super.addInventorySlot(slots);
  }

  protected void addExtraSlots(ArrayList<FaktocraftSlot> slots) {
  }

  protected Optional<AlloySmeltingRecipe> getRecipe(ItemStack... input) {
    if (!(level instanceof ServerLevel)) {
      return Optional.empty();
    }
    return findRecipe(input);
  }

  private Optional<AlloySmeltingRecipe> findRecipe(ItemStack... input) {
    return RecipeUtil.findRecipe(level, ModRecipeType.ALLOY_SMELTING, MachineRecipeInput.of(input));
  }

  private boolean isValidInput(final ItemStack... stack) {
    return level != null && findRecipe(stack).isPresent();
  }

  private boolean canWork(ItemStack inputStack0, ItemStack inputStack1, ItemStack inputStack2, ItemStack outputStack,
      ItemStack resultStack) {
    return getRecipe(inputStack0, inputStack1, inputStack2).isPresent()
        && (outputStack.isEmpty()
            || (resultStack.getCount() + outputStack.getCount() <= outputStack.getMaxStackSize()
                && resultStack.getItem() == outputStack.getItem()));
  }

  private static boolean stackChanged(ItemStack cached, ItemStack current) {
    return !ItemStack.isSameItemSameComponents(cached, current) || cached.getCount() != current.getCount();
  }

  protected boolean refreshWork() {
    final ItemStack inputStack0 = getItemStackHandler().getStackInSlot(INPUT_SLOT_0);
    final ItemStack inputStack1 = getItemStackHandler().getStackInSlot(INPUT_SLOT_1);
    final ItemStack inputStack2 = getItemStackHandler().getStackInSlot(INPUT_SLOT_2);
    final ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);

    if (stackChanged(cachedInputStack0, inputStack0) || stackChanged(cachedInputStack1, inputStack1)
        || stackChanged(cachedInputStack2, inputStack2) || stackChanged(cachedOutput, outputStack)) {
      cachedInputStack0 = inputStack0.copy();
      cachedInputStack1 = inputStack1.copy();
      cachedInputStack2 = inputStack2.copy();
      cachedOutput = outputStack.copy();

      AlloySmeltingRecipe oldRecipe = recipe;
      recipe = getRecipe(inputStack0, inputStack1, inputStack2).orElse(null);
      if (recipe != oldRecipe) {
        progress.setBoth(-1);
      }
      if (recipe != null && level != null) {
        resultStack = recipe.assemble(MachineRecipeInput.of(inputStack0, inputStack1, inputStack2));
        energyCostPerTick = recipe.getPowerCost();
        duration = recipe.getDuration();
      }
      cachedWork = false;
    }

    if (recipe != null && (cachedWork || canWork(inputStack0, inputStack1, inputStack2, outputStack, resultStack))) {
      cachedWork = true;
      return true;
    }
    return false;
  }

  protected void beginIfIdle() {
    if (progress.getProgress() == -1) {
      progress.setData(0, duration);
    }
    progress.rescaleMax(progressMax());
  }

  protected float progressMax() {
    return duration;
  }

  protected boolean finished() {
    return progress.getProgress() >= progress.getProgressMax();
  }

  protected void idle() {
    if (recipe == null) {
      progress.setBoth(-1);
    }
  }

  protected void craft() {
    if (recipe == null) {
      progress.setBoth(-1);
      return;
    }
    final ItemStack inputStack0 = getItemStackHandler().getStackInSlot(INPUT_SLOT_0);
    final ItemStack inputStack1 = getItemStackHandler().getStackInSlot(INPUT_SLOT_1);
    final ItemStack inputStack2 = getItemStackHandler().getStackInSlot(INPUT_SLOT_2);
    final ItemStack outputStack = getItemStackHandler().getStackInSlot(OUTPUT_SLOT);
    int[] consume = recipe.slotConsumption(MachineRecipeInput.of(inputStack0, inputStack1, inputStack2));
    if (consume == null) {
      progress.setBoth(-1);
      return;
    }
    if (outputStack.isEmpty()) {
      getItemStackHandler().setStackInSlot(OUTPUT_SLOT, resultStack.copy());
    } else {
      outputStack.grow(resultStack.getCount());
      getItemStackHandler().setStackInSlot(OUTPUT_SLOT, outputStack.copy());
    }

    ItemStack[] inputs = { inputStack0, inputStack1, inputStack2 };
    int[] inputSlots = { INPUT_SLOT_0, INPUT_SLOT_1, INPUT_SLOT_2 };
    for (int i = 0; i < inputs.length; i++) {
      if (consume[i] > 0) {
        inputs[i].shrink(consume[i]);
        getItemStackHandler().setStackInSlot(inputSlots[i], inputs[i].copy());
      }
    }

    addRecipeUsed(recipe);
    progress.setBoth(-1);
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    if (slot == INPUT_SLOT_0 || slot == INPUT_SLOT_1 || slot == INPUT_SLOT_2) {
      return isValidInput(stack);
    }
    return false;
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
    this.activeState = tag.getBooleanOr("active", false);
    if (tag.contains("progress")) {
      progress.load(tag.getCompoundOrEmpty("progress"));
    }
  }

  @Override
  public float getExperience(Recipe<?> recipe) {
    return ((AlloySmeltingRecipe) recipe).getExperience();
  }

  @Override
  public Runnable collectExp() {
    BlockPos pos = getBlockPos();
    return () -> ModNetworking.sendToServer(new PacketExperience(pos));
  }
}
