package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockEntityCraftPipe extends BlockEntityDockingPipe {

  public static final int PATTERN_SIZE = 9;
  public static final int MAX_RECIPES = 256;

  private static final int MAX_CANDIDATES = 64;

  private final List<Pattern> recipes = new ArrayList<>();

  public BlockEntityCraftPipe(BlockPos pos, BlockState state) {
    super(LogisticsRegistry.CRAFT_PIPE_BLOCK_ENTITY, pos, state);
  }

  @Override
  protected boolean canDockTo(BlockState state) {
    return state.getBlock() instanceof BlockAssemblyTable;
  }

  private final class Pattern {

    final ItemStackHandler slots = new ItemStackHandler(PATTERN_SIZE) {
      @Override
      protected void onContentsChanged(int slot) {
        invalidate();
        BlockEntityCraftPipe.this.setChanged();
      }
    };
    @Nullable
    CraftingRecipe cachedRecipe;
    boolean recipeResolved;

    boolean strict;
    List<List<Item>> cellOptions = List.of();
    boolean optionsResolved;

    void invalidate() {
      cachedRecipe = null;
      recipeResolved = false;
      optionsResolved = false;
    }

    boolean isEmpty() {
      return slots.isEmpty();
    }
  }

  public int recipeCount() {
    return recipes.size();
  }

  @Nullable
  public ItemStackHandler pattern(int recipe) {
    return recipe >= 0 && recipe < recipes.size() ? recipes.get(recipe).slots : null;
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
    recipes.add(new Pattern());
    setChanged();
    sync();
    return recipes.size() - 1;
  }

  private CompoundTag savePattern(Pattern pattern) {
    CompoundTag tag = new CompoundTag();
    pattern.slots.save(tag);
    if (pattern.strict) {
      tag.putBoolean("strict", true);
    }
    return tag;
  }

  private Pattern loadPattern(CompoundTag tag) {
    Pattern pattern = new Pattern();
    pattern.slots.load(tag);
    pattern.strict = tag.getBoolean("strict");
    pattern.invalidate();
    return pattern;
  }

  @Nullable
  public CompoundTag copyRecipe(int index) {
    return index >= 0 && index < recipes.size() ? savePattern(recipes.get(index)) : null;
  }

  public boolean pasteRecipe(CompoundTag tag) {
    Pattern pasted = loadPattern(tag.copy());
    for (int i = 0; i < recipes.size(); i++) {
      if (recipes.get(i).isEmpty()) {
        recipes.set(i, pasted);
        setChanged();
        sync();
        return true;
      }
    }
    if (recipes.size() >= MAX_RECIPES) {
      return false;
    }
    recipes.add(pasted);
    setChanged();
    sync();
    return true;
  }

  public void removeRecipe(int recipe) {
    if (recipe >= 0 && recipe < recipes.size()) {
      recipes.remove(recipe);
      setChanged();
      sync();
    }
  }

  public void moveRecipe(int from, int to) {
    if (from < 0 || from >= recipes.size() || to < 0 || to > recipes.size() || from == to) {
      return;
    }
    Pattern moved = recipes.remove(from);

    recipes.add(Math.min(to > from ? to - 1 : to, recipes.size()), moved);
    setChanged();
    sync();
  }

  public boolean isStrict(int recipe) {
    return recipe >= 0 && recipe < recipes.size() && recipes.get(recipe).strict;
  }

  public void toggleStrict(int recipe) {
    if (recipe >= 0 && recipe < recipes.size()) {
      Pattern pattern = recipes.get(recipe);
      pattern.strict = !pattern.strict;
      pattern.optionsResolved = false;
      setChanged();
      sync();
    }
  }

  public void setPatternSlot(int recipe, int slot, ItemStack stack) {
    if (recipe >= 0 && recipe < recipes.size() && slot >= 0 && slot < PATTERN_SIZE) {
      recipes.get(recipe).slots.setStackInSlot(slot,
          stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
      sync();
    }
  }

  private static final class PatternView implements CraftingContainer {

    private final ItemStackHandler slots;

    PatternView(ItemStackHandler slots) {
      this.slots = slots;
    }

    @Override
    public int getWidth() {
      return 3;
    }

    @Override
    public int getHeight() {
      return 3;
    }

    @Override
    public List<ItemStack> getItems() {
      NonNullList<ItemStack> items = NonNullList.withSize(PATTERN_SIZE, ItemStack.EMPTY);
      for (int i = 0; i < PATTERN_SIZE; i++) {
        items.set(i, slots.getStackInSlot(i));
      }
      return items;
    }

    @Override
    public int getContainerSize() {
      return PATTERN_SIZE;
    }

    @Override
    public boolean isEmpty() {
      return slots.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
      return slots.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
      return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
      for (int i = 0; i < PATTERN_SIZE; i++) {
        contents.accountSimpleStack(slots.getStackInSlot(i));
      }
    }
  }

  @Nullable
  public CraftingContainer patternView(int recipe) {
    ItemStackHandler slots = pattern(recipe);
    return slots != null ? viewOf(slots) : null;
  }

  public static CraftingContainer viewOf(ItemStackHandler slots) {
    return new PatternView(slots);
  }

  @Nullable
  public CraftingRecipe patternRecipe(int recipe) {
    if (level == null || recipe < 0 || recipe >= recipes.size()) {
      return null;
    }
    Pattern pattern = recipes.get(recipe);
    if (!pattern.recipeResolved) {
      pattern.cachedRecipe = pattern.isEmpty() ? null
          : level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, new PatternView(pattern.slots), level)
              .orElse(null);
      pattern.recipeResolved = true;
    }
    return pattern.cachedRecipe;
  }

  public ItemStack patternResult(int recipe) {
    CraftingRecipe craftingRecipe = patternRecipe(recipe);
    if (craftingRecipe == null || level == null) {
      return ItemStack.EMPTY;
    }
    return craftingRecipe.assemble(new PatternView(recipes.get(recipe).slots), level.registryAccess());
  }

  public List<LogisticsPlanner.ItemChoice> patternIngredients(int recipe) {
    if (recipe < 0 || recipe >= recipes.size()) {
      return List.of();
    }
    Pattern pattern = recipes.get(recipe);
    List<List<Item>> options = cellOptions(recipe);
    Map<List<ItemKey>, Integer> counts = new LinkedHashMap<>();
    for (int i = 0; i < options.size(); i++) {
      List<Item> cell = options.get(i);
      if (cell.isEmpty()) {
        continue;
      }

      List<ItemKey> keys = new ArrayList<>(cell.size());
      keys.add(ItemKey.of(pattern.slots.getStackInSlot(i)));
      for (int option = 1; option < cell.size(); option++) {
        ItemKey key = ItemKey.of(cell.get(option));
        if (!keys.contains(key)) {
          keys.add(key);
        }
      }
      counts.merge(List.copyOf(keys), 1, Integer::sum);
    }
    List<LogisticsPlanner.ItemChoice> choices = new ArrayList<>();
    counts.forEach((cell, count) -> choices.add(new LogisticsPlanner.ItemChoice(cell, count)));
    return choices;
  }

  public List<List<Item>> cellOptions(int recipe) {
    if (recipe < 0 || recipe >= recipes.size()) {
      return List.of();
    }
    Pattern pattern = recipes.get(recipe);
    if (!pattern.optionsResolved) {
      pattern.cellOptions = resolveOptions(pattern, recipe);
      pattern.optionsResolved = true;
    }
    return pattern.cellOptions;
  }

  private List<List<Item>> resolveOptions(Pattern pattern, int index) {
    List<List<Item>> options = new ArrayList<>(PATTERN_SIZE);
    for (int i = 0; i < PATTERN_SIZE; i++) {
      ItemStack declared = pattern.slots.getStackInSlot(i);
      options.add(declared.isEmpty() ? List.of() : List.of(declared.getItem()));
    }
    CraftingRecipe recipe = patternRecipe(index);
    if (level == null || pattern.strict || recipe == null) {
      return options;
    }
    List<Item> pool = candidatePool(recipe);
    if (pool.size() <= 1) {
      return options;
    }
    ItemStack result = recipe.getResultItem(level.registryAccess());
    ItemStackHandler probe = new ItemStackHandler(PATTERN_SIZE);
    for (int i = 0; i < PATTERN_SIZE; i++) {
      probe.setStackInSlot(i, pattern.slots.getStackInSlot(i).copy());
    }
    CraftingContainer view = viewOf(probe);
    for (int i = 0; i < PATTERN_SIZE; i++) {
      ItemStack declared = pattern.slots.getStackInSlot(i);
      if (declared.isEmpty()) {
        continue;
      }
      List<Item> accepted = new ArrayList<>();
      accepted.add(declared.getItem());
      for (Item candidate : pool) {
        if (candidate == declared.getItem()) {
          continue;
        }
        probe.setStackInSlot(i, new ItemStack(candidate));
        if (recipe.matches(view, level)) {
          ItemStack made = recipe.assemble(view, level.registryAccess());
          if (ItemStack.isSameItem(made, result) && made.getCount() == result.getCount()) {
            accepted.add(candidate);
          }
        }
      }
      probe.setStackInSlot(i, declared.copy());
      options.set(i, List.copyOf(accepted));
    }
    return options;
  }

  private static List<Item> candidatePool(CraftingRecipe recipe) {
    List<Item> pool = new ArrayList<>();
    for (Ingredient ingredient : recipe.getIngredients()) {
      for (ItemStack stack : ingredient.getItems()) {
        if (!stack.isEmpty() && !pool.contains(stack.getItem())) {
          pool.add(stack.getItem());
          if (pool.size() >= MAX_CANDIDATES) {
            return pool;
          }
        }
      }
    }
    return pool;
  }

  @Nullable
  public BlockEntityAssemblyTable adjacentAssembly() {
    BlockPos docked = dockedPos();
    return level != null && docked != null
        && level.getBlockEntity(docked) instanceof BlockEntityAssemblyTable assembly ? assembly : null;
  }

  private int drainCooldown = 10;

  public void tickServer() {
    if (level == null || --drainCooldown > 0) {
      return;
    }
    drainCooldown = Math.max(1, ModConfig.server().logistics_extractor_interval);
    BlockEntityAssemblyTable assembly = adjacentAssembly();
    if (assembly == null || assembly.getOutput().isEmpty()) {
      return;
    }
    BlockEntityLogisticsController core = LogisticsCores.coreFor(level, worldPosition);
    if (core == null || !core.networkOnline()
        || core.getLedger().outputInUse(assembly.getBlockPos())) {
      return;
    }
    LogisticsGraph graph = core.graph();
    if (graph == null) {
      return;
    }
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    int budget = Math.max(1, ModConfig.server().logistics_extractor_items_per_op);
    Endpoint source = Endpoint.assemblyOut(assembly.getBlockPos());
    List<BlockEntityChassis.SinkCandidate> sinks = null;
    for (int slot = 0; slot < assembly.getOutput().getSlots() && budget > 0; slot++) {
      ItemStack stack = assembly.getOutput().getStackInSlot(slot);
      if (stack.isEmpty()) {
        continue;
      }
      if (sinks == null) {
        sinks = BlockEntityChassis.sinkCandidates(level, graph, worldPosition);
      }
      BlockEntityChassis.SinkTarget sink = BlockEntityChassis.findSink(level, sinks, stack);
      if (sink == null) {
        continue;
      }

      ItemKey key = ItemKey.of(stack);
      int maxByEnergy = perItem > 0 ? core.getEnergyStorage().energyStored() / perItem : stack.getCount();
      int take = Math.min(stack.getCount(), Math.min(budget, maxByEnergy));
      if (take <= 0) {
        return;
      }
      int taken = source.extract(level, key, take, false);
      if (taken <= 0) {
        continue;
      }
      core.consumeEnergy(taken * perItem);
      List<BlockPos> route = graph.route(worldPosition, sink.nodePos());
      TaskLedger.DeliveryTask delivery = core.getLedger().createDelivery(key.stack(taken), source,
          sink.endpoint(), route, 0);
      delivery.leftover = true;
      delivery.originPos = worldPosition.asLong();
      core.getLedger().noteLeftoverShipped(assembly.getBlockPos(), key, sinkDescription(sink));
      budget -= taken;
    }
  }

  private String sinkDescription(BlockEntityChassis.SinkTarget sink) {
    BlockPos sinkChassisPos = sink.nodePos();
    boolean fallback = sink.endpoint().type() == Endpoint.Type.EJECTOR
        || sink.endpoint().type() == Endpoint.Type.DISPOSAL;
    if (!fallback && level != null
        && level.getBlockEntity(sinkChassisPos) instanceof BlockEntityChassis sinkChassis) {
      Direction dir = sinkChassis.selectedInventoryDirection();
      if (dir != null) {
        BlockPos inventoryPos = sinkChassisPos.relative(dir);
        return level.getBlockState(inventoryPos).getBlock().getName().getString()
            + " (" + inventoryPos.getX() + ", " + inventoryPos.getY() + ", " + inventoryPos.getZ() + ")";
      }
    }
    String name = level != null
        ? level.getBlockState(sinkChassisPos).getBlock().getName().getString() + " "
        : "";
    return name + "(" + sinkChassisPos.getX() + ", " + sinkChassisPos.getY() + ", "
        + sinkChassisPos.getZ() + ")";
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    ListTag list = new ListTag();
    for (Pattern pattern : recipes) {
      list.add(savePattern(pattern));
    }
    tag.put("recipes", list);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    recipes.clear();
    ListTag list = tag.getList("recipes", Tag.TAG_COMPOUND);
    for (int i = 0; i < Math.min(list.size(), MAX_RECIPES); i++) {
      recipes.add(loadPattern(list.getCompound(i)));
    }

    if (recipes.isEmpty() && tag.contains("pattern")) {
      Pattern pattern = new Pattern();
      pattern.slots.load(tag.getCompound("pattern"));
      if (!pattern.isEmpty()) {
        recipes.add(pattern);
      }
    }
  }
}
