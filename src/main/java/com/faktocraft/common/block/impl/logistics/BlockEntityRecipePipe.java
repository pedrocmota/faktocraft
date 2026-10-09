package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.util.NbtBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockEntityRecipePipe extends BlockEntityDockingPipe {

  public static final int MAX_RECIPES = 256;
  public static final int MAX_INPUTS = 12;
  public static final int MAX_OUTPUTS = 4;
  public static final int MIN_INPUTS = 3;
  public static final int MIN_OUTPUTS = 1;
  public static final int MAX_BATCH = 64;
  public static final int MAX_COUNT = 6400;
  public static final int MAX_TAG_ITEMS = 64;

  private boolean shared = true;

  public static int outputId(int index) {
    return -1 - index;
  }

  public static boolean isOutputId(int io) {
    return io < 0;
  }

  public static int outputIndex(int io) {
    return -1 - io;
  }

  public static boolean isValidIo(int io) {
    return isOutputId(io)
        ? io >= outputId(MAX_OUTPUTS - 1) && io <= outputId(0)
        : io < MAX_INPUTS;
  }

  public static final class Io {

    public ItemStack stack = ItemStack.EMPTY;
    public int count;

    public String tag = "";
    public int bindSlot = -1;
    public int bindEnd = -1;
    public String bindBlock = "";
    public int bindSlots;
    public IoMode mode = IoMode.PER_UNIT;

    public boolean isEmpty() {
      return stack.isEmpty();
    }

    public int lastSlot() {
      return Math.max(bindSlot, bindEnd);
    }

    void clear() {
      stack = ItemStack.EMPTY;
      count = 0;
      tag = "";
      mode = IoMode.PER_UNIT;
      clearBinding();
    }

    void clearBinding() {
      bindSlot = -1;
      bindEnd = -1;
      bindBlock = "";
      bindSlots = 0;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      if (!stack.isEmpty()) {
        tag.put("item", NbtBridge.saveStack(stack));
        tag.putInt("cnt", count);
      }
      if (!this.tag.isEmpty()) {
        tag.putString("tag", this.tag);
      }
      if (bindSlot >= 0) {
        tag.putInt("slot", bindSlot);
        tag.putString("block", bindBlock);
        tag.putInt("slots", bindSlots);
        if (bindEnd > bindSlot) {
          tag.putInt("slotEnd", bindEnd);
        }
      }
      if (mode != IoMode.PER_UNIT) {
        tag.putByte("mode", (byte) mode.ordinal());
      }
      return tag;
    }

    static Io load(CompoundTag tag) {
      Io io = new Io();
      io.stack = NbtBridge.loadStack(tag.getCompoundOrEmpty("item"));
      io.count = tag.getIntOr("cnt", 0);
      io.tag = com.faktocraft.common.util.LegacyTags.migrate(tag.getStringOr("tag", ""));
      io.bindSlot = tag.contains("slot") ? tag.getIntOr("slot", 0) : -1;
      io.bindEnd = tag.contains("slotEnd") ? Math.max(io.bindSlot, tag.getIntOr("slotEnd", 0)) : io.bindSlot;
      io.bindBlock = tag.getStringOr("block", "");
      io.bindSlots = tag.getIntOr("slots", 0);
      io.mode = tag.contains("mode") ? IoMode.of(tag.getByteOr("mode", (byte) 0)) : IoMode.PER_UNIT;
      return io;
    }
  }

  public static final class MachineRecipe {

    public final Io[] inputs = new Io[MAX_INPUTS];
    public final Io[] outputs = new Io[MAX_OUTPUTS];
    public int batchSize = 1;

    MachineRecipe() {
      for (int i = 0; i < MAX_INPUTS; i++) {
        inputs[i] = new Io();
      }
      for (int i = 0; i < MAX_OUTPUTS; i++) {
        outputs[i] = new Io();
      }
    }

    @Nullable
    public Io io(int ioId) {
      if (!isValidIo(ioId)) {
        return null;
      }
      return isOutputId(ioId) ? outputs[outputIndex(ioId)] : inputs[ioId];
    }

    public boolean isEmpty() {
      for (Io io : inputs) {
        if (!io.isEmpty()) {
          return false;
        }
      }
      for (Io io : outputs) {
        if (!io.isEmpty()) {
          return false;
        }
      }
      return true;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag inputList = new ListTag();
      for (Io io : inputs) {
        inputList.add(io.save());
      }
      tag.put("in", inputList);
      ListTag outputList = new ListTag();
      for (Io io : outputs) {
        outputList.add(io.save());
      }
      tag.put("out", outputList);
      if (batchSize > 1) {
        tag.putInt("batch", batchSize);
      }
      return tag;
    }

    static MachineRecipe load(CompoundTag tag) {
      MachineRecipe recipe = new MachineRecipe();
      recipe.batchSize = tag.contains("batch") ? Math.max(1, Math.min(MAX_BATCH, tag.getIntOr("batch", 0))) : 1;
      ListTag inputList = tag.getListOrEmpty("in");
      for (int i = 0; i < Math.min(inputList.size(), MAX_INPUTS); i++) {
        recipe.inputs[i] = Io.load(inputList.getCompoundOrEmpty(i));
      }
      ListTag outputList = tag.getListOrEmpty("out");
      for (int i = 0; i < Math.min(outputList.size(), MAX_OUTPUTS); i++) {
        recipe.outputs[i] = Io.load(outputList.getCompoundOrEmpty(i));
      }
      return recipe;
    }
  }

  private final List<MachineRecipe> recipes = new ArrayList<>();
  private int recipesVersion;

  public BlockEntityRecipePipe(BlockPos pos, BlockState state) {
    super(LogisticsRegistry.RECIPE_PIPE_BLOCK_ENTITY, pos, state);
  }

  @Override
  protected boolean canDockTo(BlockState state) {
    return !(state.getBlock() instanceof BlockAssemblyTable);
  }

  public int recipeCount() {
    return recipes.size();
  }

  public int recipesVersion() {
    return recipesVersion;
  }

  @Nullable
  public MachineRecipe recipe(int index) {
    return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
  }

  public int addRecipe() {
    for (int i = 0; i < recipes.size(); i++) {
      if (recipes.get(i).isEmpty()) {
        return i;
      }
    }
    if (recipes.size() >= MAX_RECIPES) {
      return -1;
    }
    recipes.add(new MachineRecipe());
    changed();
    return recipes.size() - 1;
  }

  @Nullable
  public CompoundTag copyRecipe(int index) {
    MachineRecipe recipe = recipe(index);
    return recipe != null ? recipe.save() : null;
  }

  public boolean pasteRecipe(CompoundTag tag) {
    MachineRecipe pasted = MachineRecipe.load(tag.copy());
    for (int i = 0; i < recipes.size(); i++) {
      if (recipes.get(i).isEmpty()) {
        recipes.set(i, pasted);
        changed();
        return true;
      }
    }
    if (recipes.size() >= MAX_RECIPES) {
      return false;
    }
    recipes.add(pasted);
    changed();
    return true;
  }

  public void removeRecipe(int index) {
    if (index >= 0 && index < recipes.size()) {
      recipes.remove(index);
      changed();
    }
  }

  public void moveRecipe(int from, int to) {
    if (from < 0 || from >= recipes.size() || to < 0 || to > recipes.size() || from == to) {
      return;
    }
    MachineRecipe moved = recipes.remove(from);

    recipes.add(Math.min(to > from ? to - 1 : to, recipes.size()), moved);
    changed();
  }

  public void setIo(int recipe, int ioId, ItemStack stack) {
    MachineRecipe entry = recipe(recipe);
    Io io = entry != null ? entry.io(ioId) : null;
    if (io == null) {
      return;
    }
    if (stack.isEmpty()) {
      io.clear();
    } else {
      io.stack = stack.copyWithCount(1);
      io.count = Math.max(1, io.count);
      io.tag = "";
      io.mode = IoMode.PER_UNIT;
      io.clearBinding();
    }
    changed();
  }

  public void cycleMode(int recipe, int ioId) {
    MachineRecipe entry = recipe(recipe);
    if (entry == null || isOutputId(ioId)) {
      return;
    }
    Io io = entry.io(ioId);
    if (io == null || io.isEmpty()) {
      return;
    }
    io.mode = io.mode.next();
    changed();
  }

  public void setMode(int recipe, int ioId, IoMode mode) {
    MachineRecipe entry = recipe(recipe);
    Io io = entry != null && !isOutputId(ioId) ? entry.io(ioId) : null;
    if (io == null || io.isEmpty()) {
      return;
    }
    io.mode = mode;
    changed();
  }

  public void adjustBatch(int recipe, int delta) {
    MachineRecipe entry = recipe(recipe);
    if (entry == null) {
      return;
    }
    entry.batchSize = Math.max(1, Math.min(MAX_BATCH, entry.batchSize + delta));
    changed();
  }

  public void setBatch(int recipe, int batchSize) {
    MachineRecipe entry = recipe(recipe);
    if (entry == null) {
      return;
    }
    entry.batchSize = Math.max(1, Math.min(MAX_BATCH, batchSize));
    changed();
  }

  public void setIo(int recipe, int ioId, ItemStack stack, int count) {
    setIo(recipe, ioId, stack);
    MachineRecipe entry = recipe(recipe);
    Io io = entry != null ? entry.io(ioId) : null;
    if (io != null && !stack.isEmpty()) {
      io.count = Math.max(1, Math.min(MAX_COUNT, count));
      changed();
    }
  }

  public void adjustCount(int recipe, int ioId, int delta) {
    MachineRecipe entry = recipe(recipe);
    Io io = entry != null ? entry.io(ioId) : null;
    if (io != null && !io.isEmpty()) {
      io.count = Math.max(1, Math.min(MAX_COUNT, io.count + delta));
      changed();
    }
  }

  public void importRecipe(int recipe, List<RecipeImport.Entry> inputs, List<RecipeImport.Entry> outputs,
      int batchSize) {
    MachineRecipe entry = recipe(recipe);
    if (entry == null) {
      return;
    }
    for (int i = 0; i < MAX_INPUTS; i++) {
      importIo(entry.inputs[i], i < inputs.size() ? inputs.get(i) : null, true);
    }
    for (int i = 0; i < MAX_OUTPUTS; i++) {
      importIo(entry.outputs[i], i < outputs.size() ? outputs.get(i) : null, false);
    }
    entry.batchSize = Math.max(1, Math.min(MAX_BATCH, batchSize));
    changed();
  }

  private static void importIo(Io io, @Nullable RecipeImport.Entry imported, boolean input) {
    if (imported == null || imported.stack().isEmpty()) {
      io.clear();
      return;
    }
    if (io.isEmpty() || !ItemKey.of(io.stack).equals(ItemKey.of(imported.stack()))) {
      io.clearBinding();
    }
    io.stack = imported.stack().copyWithCount(1);
    io.count = Math.max(1, Math.min(MAX_COUNT, imported.count()));
    io.tag = input && validTag(io.stack, imported.tag()) ? imported.tag() : "";
    io.mode = input ? imported.mode() : IoMode.PER_UNIT;
  }

  private static boolean validTag(ItemStack stack, String tag) {
    Identifier id = tag.isEmpty() ? null : Identifier.tryParse(tag);
    return id != null && stack.is(TagKey.create(Registries.ITEM, id));
  }

  public void cycleTag(int recipe, int ioId) {
    MachineRecipe entry = recipe(recipe);
    if (entry == null || isOutputId(ioId)) {
      return;
    }
    Io io = entry.io(ioId);
    if (io == null || io.isEmpty()) {
      return;
    }
    List<String> tags = io.stack.typeHolder().tags().map(key -> key.location().toString()).sorted().toList();
    int next = io.tag.isEmpty() ? 0 : tags.indexOf(io.tag) + 1;
    io.tag = next >= 0 && next < tags.size() ? tags.get(next) : "";
    changed();
  }

  public static List<ItemKey> options(Io io) {
    List<ItemKey> options = new ArrayList<>();
    if (io.isEmpty()) {
      return options;
    }
    options.add(ItemKey.of(io.stack));
    if (io.tag.isEmpty() || !(Identifier.tryParse(io.tag) != null)) {
      return options;
    }
    TagKey<Item> key = TagKey.create(Registries.ITEM, Identifier.parse(io.tag));
    for (net.minecraft.core.Holder<Item> tagHolder : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key)
        .map(set -> (Iterable<net.minecraft.core.Holder<Item>>) set).orElse(java.util.List.of())) {
      ItemKey option = ItemKey.of(tagHolder.value());
      if (options.size() < MAX_TAG_ITEMS && !options.contains(option)) {
        options.add(option);
      }
    }
    return options;
  }

  public void bind(int recipe, int ioId, int slot, String blockId, int slotCount) {
    bind(recipe, ioId, slot, slot, blockId, slotCount);
  }

  public void bind(int recipe, int ioId, int slot, int slotEnd, String blockId, int slotCount) {
    MachineRecipe entry = recipe(recipe);
    Io io = entry != null ? entry.io(ioId) : null;
    if (io == null) {
      return;
    }
    if (slot < 0) {
      io.clearBinding();
    } else {
      io.bindSlot = slot;
      io.bindEnd = Math.max(slot, slotEnd);
      io.bindBlock = blockId;
      io.bindSlots = slotCount;
    }
    changed();
  }

  public boolean shared() {
    return shared;
  }

  public void setShared(boolean shared) {
    this.shared = shared;
    setChanged();
    sync();
  }

  public void changed() {
    recipesVersion++;
    setChanged();
    sync();
  }

  public void saveRecipes(CompoundTag tag) {
    ListTag list = new ListTag();
    for (MachineRecipe recipe : recipes) {
      list.add(recipe.save());
    }
    tag.put("recipes", list);
  }

  public void loadRecipes(CompoundTag tag) {
    recipes.clear();
    ListTag list = tag.getListOrEmpty("recipes");
    for (int i = 0; i < Math.min(list.size(), MAX_RECIPES); i++) {
      recipes.add(MachineRecipe.load(list.getCompoundOrEmpty(i)));
    }
    recipesVersion++;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    saveRecipes(tag);
    tag.putBoolean("share", shared);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    if (tag.contains("recipes")) {
      loadRecipes(tag);
    }
    shared = !tag.contains("share") || tag.getBooleanOr("share", false);
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag tag = new CompoundTag();
    super.saveAdditional(tag);
    return tag;
  }
}
