package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.util.PlayerMessages;
import com.faktocraft.common.util.LegacyNbtBlockEntity;
import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.util.ItemStackHandler;
import com.faktocraft.common.util.TransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.IItemHandler;
import com.faktocraft.common.util.transfer.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockEntityChassis extends LegacyNbtBlockEntity
    implements com.faktocraft.common.cover.ICoverHost, com.faktocraft.common.block.IConnectionExtensionHost {

  @org.jetbrains.annotations.Nullable
  private BlockState cover;
  private int coverHoles;
  private final com.faktocraft.common.block.ConnectionExtensions extensions =
      new com.faktocraft.common.block.ConnectionExtensions();

  @Override
  public com.faktocraft.common.block.ConnectionExtensions extensions() {
    return extensions;
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public BlockState getCover() {
    return cover;
  }

  @Override
  public int getCoverHoles() {
    return coverHoles;
  }

  @Override
  public void setCover(@org.jetbrains.annotations.Nullable BlockState cover, int holes) {
    this.cover = cover;
    this.coverHoles = holes;
    com.faktocraft.common.cover.CoverSupport.markChanged(this);
  }

  @Override
  public net.neoforged.neoforge.model.data.ModelData getModelData() {
    return extensions.modelData(com.faktocraft.common.cover.CoverSupport.modelData(cover, coverHoles));
  }

  @Override
  public void onDataPacket(net.minecraft.network.Connection connection,
      net.minecraft.world.level.storage.ValueInput input) {
    BlockState previousCover = cover;
    int previousHoles = coverHoles;
    int previousExtensions = extensions.mask();
    super.onDataPacket(connection, input);
    if (previousCover != cover || previousHoles != coverHoles || extensions.mask() != previousExtensions) {
      com.faktocraft.common.cover.CoverSupport.refreshClientModel(this);
    }
  }

  @Override
  public void setLevel(net.minecraft.world.level.Level level) {
    super.setLevel(level);
    extensions.onLevelSet(this);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    com.faktocraft.common.cover.CoverSupport.onClientLoad(this, this);
    extensions.resolve(this);
    extensions.onClientLoad(this);
    if (level != null && !level.isClientSide()) {
      LogisticsCores.markDirtyNear(level, worldPosition);
    }
  }

  @Override
  public void setRemoved() {
    com.faktocraft.common.cover.CoverSupport.onClientRemoved(this, this);
    super.setRemoved();
  }

  public static final int MODULE_SLOTS = 8;
  public static final int UPGRADE_SLOTS = 3;
  public static final int COLLECTOR_BUFFER_SLOTS = 9;
  private static final int VACUUM_INTERVAL = 5;

  private final ItemStackHandler modules = new ItemStackHandler(MODULE_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityChassis.this.setChanged();
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }
  };
  private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityChassis.this.setChanged();
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }
  };

  private final ItemStackHandler collectorBuffer = new ItemStackHandler(COLLECTOR_BUFFER_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityChassis.this.setChanged();
    }
  };
  private int extractCooldown = 10;
  private int supplyCooldown = 45;

  @Nullable
  private Direction selectedInventory;

  public BlockEntityChassis(BlockPos pos, BlockState state) {
    super(LogisticsRegistry.CHASSIS_BLOCK_ENTITY, pos, state);
  }

  public ItemStackHandler getModules() {
    return modules;
  }

  public ItemStackHandler getUpgrades() {
    return upgrades;
  }

  public ItemStackHandler getCollectorBuffer() {
    return collectorBuffer;
  }

  public boolean hasModule(ModuleType type) {
    for (int i = 0; i < moduleSlotCount(); i++) {
      if (ModuleItem.typeOf(modules.getStackInSlot(i)) == type) {
        return true;
      }
    }
    return false;
  }

  private static final Direction[] EJECT_ORDER = { Direction.UP, Direction.NORTH, Direction.SOUTH,
      Direction.WEST, Direction.EAST, Direction.DOWN };

  @Nullable
  public Direction freeFace() {
    BlockState state = getBlockState();
    if (!(state.getBlock() instanceof BlockChassis)) {
      return null;
    }
    for (Direction direction : EJECT_ORDER) {
      if (!state.getValue(com.faktocraft.common.block.VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))) {
        return direction;
      }
    }
    return null;
  }

  public int tier() {
    return getBlockState().getBlock() instanceof BlockChassis chassis ? chassis.getTier() : 1;
  }

  public int moduleSlotCount() {
    return getBlockState().getBlock() instanceof BlockChassis chassis ? chassis.moduleSlots() : 2;
  }

  public int upgradeSlotCount() {
    return getBlockState().getBlock() instanceof BlockChassis chassis ? chassis.upgradeSlots() : 1;
  }

  public int throughput() {
    int count = 0;
    for (int i = 0; i < upgradeSlotCount(); i++) {
      if (upgrades.getStackInSlot(i).getItem() instanceof ThroughputUpgradeItem) {
        count++;
      }
    }
    return count;
  }

  public record AdjacentHandler(BlockPos pos, IItemHandler handler, @Nullable Direction side) {
  }

  public List<Direction> inventoryDirections() {
    return level != null ? BlockChassis.inventoryDirections(level, worldPosition) : List.of();
  }

  @Nullable
  public Direction selectedInventoryDirection() {
    List<Direction> candidates = inventoryDirections();
    if (candidates.isEmpty()) {
      return null;
    }
    if (selectedInventory != null && candidates.contains(selectedInventory)) {
      return selectedInventory;
    }
    Direction first = candidates.get(0);
    if (level != null && !level.isClientSide()) {
      selectedInventory = first;
      setChanged();
    }
    return first;
  }

  public boolean cycleInventory(@Nullable net.minecraft.world.entity.player.Player player) {
    if (level == null || level.isClientSide()) {
      return false;
    }
    List<Direction> candidates = inventoryDirections();
    if (candidates.size() < 2) {
      if (player != null) {
        PlayerMessages.display(player, net.minecraft.network.chat.Component.translatable(
            "logistics." + com.faktocraft.Faktocraft.MODID
                + (candidates.isEmpty() ? ".chassis.inventory_none" : ".chassis.inventory_single")),
            true);
      }
      return false;
    }
    Direction current = selectedInventoryDirection();
    selectedInventory = candidates.get((candidates.indexOf(current) + 1) % candidates.size());
    setChanged();
    refreshConnections();
    LogisticsCores.markDirtyNear(level, worldPosition);
    if (player != null) {
      BlockPos target = worldPosition.relative(selectedInventory);
      PlayerMessages.display(player, net.minecraft.network.chat.Component.translatable(
          "logistics." + com.faktocraft.Faktocraft.MODID + ".chassis.inventory_selected",
          level.getBlockState(target).getBlock().getName(), selectedInventory.getName()), true);
    }
    return true;
  }

  private void refreshConnections() {
    if (level == null || !(getBlockState().getBlock() instanceof BlockChassis block)) {
      return;
    }
    BlockState state = block.withConnections(getBlockState(), level, worldPosition);
    boolean changed = level.setBlock(worldPosition, state, 3);
    level.sendBlockUpdated(worldPosition, state, state, 3);
    boolean dirty = extensions.takeDirty();
    if (!changed && dirty && !level.isClientSide()) {
      level.updateNeighborsAt(worldPosition, state.getBlock());
      state.updateNeighbourShapes(level, worldPosition, net.minecraft.world.level.block.Block.UPDATE_CLIENTS,
          net.minecraft.world.level.block.Block.UPDATE_LIMIT);
    }
  }

  public List<AdjacentHandler> adjacentHandlers() {
    List<AdjacentHandler> handlers = new ArrayList<>();
    Direction direction = selectedInventoryDirection();
    if (level == null || direction == null) {
      return handlers;
    }
    BlockPos relative = worldPosition.relative(direction);
    if (LogisticsGraph.isNetworkMember(level.getBlockState(relative))) {
      return handlers;
    }
    IItemHandler handler = TransferUtil.findItemHandler(level, relative, direction.getOpposite());
    if (handler != null) {
      handlers.add(new AdjacentHandler(relative, handler, direction.getOpposite()));
    }
    return handlers;
  }

  @Nullable
  public BlockEntityAssemblyTable adjacentAssembly() {
    Direction direction = selectedInventoryDirection();
    if (level == null || direction == null) {
      return null;
    }
    return level.getBlockEntity(worldPosition.relative(direction)) instanceof BlockEntityAssemblyTable assembly
        ? assembly : null;
  }

  public List<ItemStack> modulesOf(ModuleType type) {
    List<ItemStack> result = new ArrayList<>();
    for (int i = 0; i < moduleSlotCount(); i++) {
      ItemStack stack = modules.getStackInSlot(i);
      if (ModuleItem.typeOf(stack) == type) {
        result.add(stack);
      }
    }
    return result;
  }

  public void tickServer() {
    if (level == null) {
      return;
    }
    extensions.resolve(this);
    if (level.getGameTime() % VACUUM_INTERVAL == 0) {
      vacuum();
    }
    boolean extract = --extractCooldown <= 0;
    boolean supply = --supplyCooldown <= 0;
    if (!extract && !supply) {
      return;
    }
    BlockEntityLogisticsController core = LogisticsCores.coreFor(level, worldPosition);
    if (core == null || !core.networkOnline()) {
      if (extract) {
        extractCooldown = Math.max(1, ModConfig.server().logistics_extractor_interval);
      }
      if (supply) {
        supplyCooldown = Math.max(2, ModConfig.server().logistics_extractor_interval * 2);
      }
      return;
    }
    if (extract) {
      extractCooldown = Math.max(1, ModConfig.server().logistics_extractor_interval);
      tickExtractors(core);
      tickCollector(core);
    }
    if (supply) {
      supplyCooldown = Math.max(2, ModConfig.server().logistics_extractor_interval * 2);
      tickSuppliers(core);
    }
  }

  @Nullable
  private static java.util.Map<String, Boolean> treeOf(ItemStack module) {
    if (!ModuleSettings.hasTree(module)) {
      return null;
    }
    java.util.Map<String, Boolean> tree = ModuleSettings.treeOverrides(module);
    return tree.isEmpty() && !ModuleSettings.isTreeCurrent(module) ? null : tree;
  }

  private void tickExtractors(BlockEntityLogisticsController core) {
    List<ItemStack> extractors = modulesOf(ModuleType.EXTRACTOR);
    if (extractors.isEmpty()) {
      return;
    }
    LogisticsGraph graph = core.graph();
    if (graph == null) {
      return;
    }
    LogisticsItemTree.ensureBuilt(level);
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    int budget = Math.max(1, ModConfig.server().logistics_extractor_items_per_op) * (1 + throughput());

    List<AdjacentHandler> sources = adjacentHandlers();
    List<SinkCandidate> sinks = null;
    for (ItemStack module : extractors) {
      List<ModuleSettings.FilterLine> lines = ModuleSettings.lines(module);
      boolean filterAll = !ModuleSettings.hasAnyLine(lines);
      java.util.Map<String, Boolean> tree = treeOf(module);
      for (AdjacentHandler source : sources) {
        for (int slot = 0; slot < source.handler().getSlots() && budget > 0; slot++) {
          ItemStack peek = source.handler().extractItem(slot, budget, true);
          if (peek.isEmpty()) {
            continue;
          }
          if (!(tree != null ? LogisticsItemTree.allows(tree, peek)
              : filterAll || ModuleSettings.anyMatches(lines, peek))) {
            continue;
          }
          if (sinks == null) {
            sinks = sinkCandidates(level, graph, worldPosition);
          }
          SinkTarget sink = findSink(level, sinks, peek);
          if (sink == null) {
            continue;
          }
          int maxByEnergy = perItem > 0 ? core.getEnergyStorage().energyStored() / perItem : peek.getCount();
          int take = Math.min(peek.getCount(), Math.min(budget, maxByEnergy));
          if (take <= 0) {
            return;
          }
          ItemStack extracted = source.handler().extractItem(slot, take, false);
          if (extracted.isEmpty()) {
            continue;
          }
          core.consumeEnergy(extracted.getCount() * perItem);
          List<BlockPos> route = graph.route(worldPosition, sink.nodePos());
          TaskLedger.DeliveryTask moved = core.getLedger().createDelivery(extracted,
              Endpoint.inventory(source.pos(), source.side()), sink.endpoint(), route, 0);
          moved.originPos = worldPosition.asLong();
          budget -= extracted.getCount();
        }
        if (budget <= 0) {
          return;
        }
      }
    }
  }

  private static boolean moduleAllows(ItemStack module, ItemStack stack) {
    java.util.Map<String, Boolean> tree = treeOf(module);
    if (tree != null) {
      return LogisticsItemTree.allows(tree, stack);
    }
    List<ModuleSettings.FilterLine> lines = ModuleSettings.lines(module);
    return !ModuleSettings.hasAnyLine(lines) || ModuleSettings.anyMatches(lines, stack);
  }

  private void vacuum() {
    List<ItemStack> collectors = modulesOf(ModuleType.COLLECTOR);
    if (collectors.isEmpty()) {
      return;
    }
    LogisticsItemTree.ensureBuilt(level);
    BlockState state = getBlockState();
    for (Direction direction : com.faktocraft.common.util.Constants.DIRECTIONS) {
      if (state.getValue(com.faktocraft.common.block.VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))) {
        continue;
      }
      var box = new net.minecraft.world.phys.AABB(worldPosition.relative(direction)).inflate(0.25);
      for (net.minecraft.world.entity.item.ItemEntity entity : level
          .getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
        if (!entity.isAlive() || entity.getItem().isEmpty()) {
          continue;
        }
        boolean allowed = false;
        for (ItemStack module : collectors) {
          if (moduleAllows(module, entity.getItem())) {
            allowed = true;
            break;
          }
        }
        if (!allowed) {
          continue;
        }
        ItemStack leftover = entity.getItem().copy();
        for (int slot = 0; slot < collectorBuffer.getSlots() && !leftover.isEmpty(); slot++) {
          leftover = collectorBuffer.insertItem(slot, leftover, false);
        }
        if (leftover.getCount() == entity.getItem().getCount()) {
          return;
        }
        if (leftover.isEmpty()) {
          entity.discard();
        } else {
          entity.setItem(leftover);
        }
        return;
      }
    }
  }

  private void tickCollector(BlockEntityLogisticsController core) {
    if (!hasModule(ModuleType.COLLECTOR)) {
      return;
    }
    LogisticsGraph graph = core.graph();
    if (graph == null || !graph.contains(worldPosition)) {
      return;
    }
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    int budget = Math.max(1, ModConfig.server().logistics_extractor_items_per_op) * (1 + throughput());
    List<SinkCandidate> sinks = null;
    for (int slot = 0; slot < collectorBuffer.getSlots() && budget > 0; slot++) {
      ItemStack peek = collectorBuffer.getStackInSlot(slot);
      if (peek.isEmpty()) {
        continue;
      }
      if (sinks == null) {
        sinks = sinkCandidates(level, graph, null);
        sinks.removeIf(candidate -> candidate.nodePos().equals(worldPosition)
            && candidate.endpoint().type() == Endpoint.Type.EJECTOR);
      }
      SinkTarget sink = findSink(level, sinks, peek);
      if (sink == null) {
        continue;
      }
      int maxByEnergy = perItem > 0 ? core.getEnergyStorage().energyStored() / perItem : peek.getCount();
      int take = Math.min(peek.getCount(), Math.min(budget, maxByEnergy));
      if (take <= 0) {
        return;
      }
      ItemStack extracted = collectorBuffer.extractItem(slot, take, false);
      if (extracted.isEmpty()) {
        continue;
      }
      core.consumeEnergy(extracted.getCount() * perItem);
      List<BlockPos> route = graph.route(worldPosition, sink.nodePos());
      TaskLedger.DeliveryTask moved = core.getLedger().createDelivery(extracted,
          Endpoint.chassisBuffer(worldPosition), sink.endpoint(), route, 0);
      moved.originPos = worldPosition.asLong();
      budget -= extracted.getCount();
    }
  }

  private void tickSuppliers(BlockEntityLogisticsController core) {
    List<ItemStack> suppliers = modulesOf(ModuleType.SUPPLIER);
    if (suppliers.isEmpty()) {
      return;
    }
    LogisticsItemTree.ensureBuilt(level);
    Endpoint dest = Endpoint.chassis(worldPosition);
    if (core.getLedger().hasActiveJobFor(dest) || core.hasPendingPlanFor(dest)) {
      return;
    }
    for (ItemStack module : suppliers) {
      boolean allowCrafts = ModuleSettings.getFlag(module, ModuleSettings.FLAG_ALLOW_CRAFTS);
      if (ModuleSettings.hasTree(module)) {
        for (java.util.Map.Entry<Item, Integer> target : LogisticsItemTree.supplyTargets(module)) {

          ItemKey key = ItemKey.of(target.getKey());
          int deficit = target.getValue() - dest.count(level, key);
          if (deficit > 0) {
            core.submitRequest(key, deficit, dest, worldPosition, allowCrafts, null, true);
            return;
          }
        }
        continue;
      }
      for (ModuleSettings.FilterLine line : ModuleSettings.lines(module)) {
        if (line.mode() != ModuleSettings.LineMode.ITEM || line.item().isEmpty() || line.count() <= 0) {
          continue;
        }
        ItemKey key = ItemKey.of(line.item());
        int current = dest.count(level, key);
        int deficit = line.count() - current;
        if (deficit > 0) {
          core.submitRequest(key, deficit, dest, worldPosition, allowCrafts, null, true);
          return;
        }
      }
    }
  }

  public record SinkTarget(BlockPos nodePos, Endpoint endpoint, int priority) {
  }

  private static final int RANK_MATCH = 0;
  private static final int RANK_OVERFLOW = 1;

  public static final class SinkCandidate {

    private final BlockPos nodePos;
    private final Endpoint endpoint;
    @Nullable
    private final List<ModuleSettings.FilterLine> lines;
    @Nullable
    private final java.util.Map<String, Boolean> tree;
    private final int fallbackRank;
    private final int priority;
    private final int distance;
    @Nullable
    private List<AdjacentHandler> handlers;

    public SinkCandidate(BlockPos nodePos, Endpoint endpoint, @Nullable List<ModuleSettings.FilterLine> lines,
        @Nullable java.util.Map<String, Boolean> tree, int fallbackRank, int priority, int distance) {
      this.nodePos = nodePos;
      this.endpoint = endpoint;
      this.lines = lines;
      this.tree = tree;
      this.fallbackRank = fallbackRank;
      this.priority = priority;
      this.distance = distance;
    }

    public BlockPos nodePos() {
      return nodePos;
    }

    public Endpoint endpoint() {
      return endpoint;
    }

    public int fallbackRank() {
      return fallbackRank;
    }

    public int priority() {
      return priority;
    }

    public int distance() {
      return distance;
    }

    boolean accepts(ItemStack stack) {
      if (lines == null && tree == null) {
        return false;
      }
      return tree != null ? LogisticsItemTree.allows(tree, stack)
          : ModuleSettings.anyMatches(lines, stack);
    }

    ItemStack simulateInsert(Level level, ItemStack stack) {
      if (endpoint.type() != Endpoint.Type.CHASSIS) {
        return endpoint.insert(level, stack, true);
      }
      if (!endpoint.isLoaded(level) || stack.isEmpty()) {
        return stack;
      }
      if (handlers == null) {
        handlers = level.getBlockEntity(endpoint.pos()) instanceof BlockEntityChassis chassis
            ? chassis.adjacentHandlers() : List.of();
      }
      ItemStack remaining = stack.copy();
      for (AdjacentHandler adjacent : handlers) {
        remaining = ItemHandlerHelper.insertItemStacked(adjacent.handler(), remaining, true);
        if (remaining.isEmpty()) {
          return ItemStack.EMPTY;
        }
      }
      return remaining;
    }
  }

  public static List<SinkCandidate> sinkCandidates(Level level, LogisticsGraph graph,
      @Nullable BlockPos excludeChassis) {
    LogisticsItemTree.ensureBuilt(level);
    List<SinkCandidate> result = new ArrayList<>();
    for (BlockPos pos : graph.chassisList()) {
      if (pos.equals(excludeChassis) || !(level.getBlockEntity(pos) instanceof BlockEntityChassis chassis)) {
        continue;
      }
      for (int i = 0; i < chassis.moduleSlotCount(); i++) {
        ItemStack module = chassis.modules.getStackInSlot(i);
        ModuleType type = ModuleItem.typeOf(module);
        if (type == ModuleType.SINK || type == ModuleType.EJECTOR || type == ModuleType.DISPOSAL) {
          Endpoint endpoint = switch (type) {
            case EJECTOR -> Endpoint.ejector(pos);
            case DISPOSAL -> Endpoint.disposal(pos);
            default -> Endpoint.chassis(pos);
          };
          boolean overflow = ModuleSettings.getFlag(module, ModuleSettings.FLAG_OVERFLOW);
          result.add(new SinkCandidate(pos, endpoint, ModuleSettings.lines(module),
              ModuleSettings.hasTree(module) ? ModuleSettings.treeOverrides(module) : null,
              overflow ? RANK_OVERFLOW : RANK_MATCH,
              ModuleSettings.getPriority(module), graph.distance(pos)));
        }
      }
    }
    return result;
  }

  @Nullable
  public static SinkTarget findSink(Level level, List<SinkCandidate> candidates, ItemStack stack) {
    SinkTarget best = null;
    int bestRank = Integer.MAX_VALUE;
    int bestDistance = Integer.MAX_VALUE;
    for (SinkCandidate candidate : candidates) {

      int rank = candidate.fallbackRank();
      if (candidate.accepts(stack)) {
        rank = RANK_MATCH;
      } else if (candidate.fallbackRank() != RANK_OVERFLOW) {
        continue;
      }
      boolean better;
      if (best == null) {
        better = true;
      } else if (rank != bestRank) {
        better = rank < bestRank;
      } else if (candidate.priority() != best.priority()) {
        better = candidate.priority() > best.priority();
      } else {
        better = candidate.distance() < bestDistance;
      }
      if (!better) {
        continue;
      }
      ItemStack leftover = candidate.simulateInsert(level, stack);
      if (leftover.getCount() < stack.getCount()) {
        best = new SinkTarget(candidate.nodePos(), candidate.endpoint(), candidate.priority());
        bestRank = rank;
        bestDistance = candidate.distance();
      }
    }
    return best;
  }

  public static final class ProviderRef {

    private final BlockPos chassisPos;
    private final BlockPos inventoryPos;
    @Nullable
    private final Direction inventorySide;
    private final ItemStack module;
    @Nullable
    private List<ModuleSettings.FilterLine> lines;
    @Nullable
    private java.util.Map<String, Boolean> tree;
    private boolean treeChecked;
    private boolean exclude;
    private int reserve = -1;
    private int priority = Integer.MIN_VALUE;

    ProviderRef(BlockPos chassisPos, BlockPos inventoryPos, @Nullable Direction inventorySide, ItemStack module) {
      this.chassisPos = chassisPos;
      this.inventoryPos = inventoryPos;
      this.inventorySide = inventorySide;
      this.module = module;
    }

    public BlockPos chassisPos() {
      return chassisPos;
    }

    public BlockPos inventoryPos() {
      return inventoryPos;
    }

    @Nullable
    public Direction inventorySide() {
      return inventorySide;
    }

    public ItemStack module() {
      return module;
    }

    private List<ModuleSettings.FilterLine> lines() {
      if (lines == null) {
        lines = ModuleSettings.lines(module);
        exclude = ModuleSettings.getFlag(module, ModuleSettings.FLAG_EXCLUDE);
      }
      return lines;
    }

    public int reserve() {
      if (reserve < 0) {
        reserve = ModuleSettings.getMinReserve(module);
      }
      return reserve;
    }

    public int priority() {
      if (priority == Integer.MIN_VALUE) {
        priority = ModuleSettings.getPriority(module);
      }
      return priority;
    }

    public boolean allows(ItemStack stack) {
      if (!treeChecked) {
        treeChecked = true;
        tree = treeOf(module);
      }
      if (tree != null) {
        return LogisticsItemTree.allows(tree, stack);
      }
      List<ModuleSettings.FilterLine> parsed = lines();
      boolean matches = ModuleSettings.anyMatches(parsed, stack);
      if (!ModuleSettings.hasAnyLine(parsed)) {
        return true;
      }
      return exclude ? !matches : matches;
    }

    public int availableOf(Level level, ItemKey key) {
      if (!allows(key.stack())) {
        return 0;
      }
      int count = Endpoint.inventory(inventoryPos, inventorySide).count(level, key);
      return Math.max(0, count - reserve());
    }
  }

  public static List<ProviderRef> providers(Level level, LogisticsGraph graph) {
    LogisticsItemTree.ensureBuilt(level);
    List<ProviderRef> result = new ArrayList<>();
    for (BlockPos pos : graph.chassisList()) {
      if (!(level.getBlockEntity(pos) instanceof BlockEntityChassis chassis)) {
        continue;
      }
      List<AdjacentHandler> handlers = null;
      for (int i = 0; i < chassis.moduleSlotCount(); i++) {
        ItemStack module = chassis.modules.getStackInSlot(i);
        if (ModuleItem.typeOf(module) != ModuleType.PROVIDER) {
          continue;
        }
        if (handlers == null) {
          handlers = chassis.adjacentHandlers();
        }
        for (AdjacentHandler handler : handlers) {
          result.add(new ProviderRef(pos, handler.pos(), handler.side(), module));
        }
      }
    }
    return result;
  }

  public static Map<ItemKey, Integer> stockSnapshot(Level level, LogisticsGraph graph, TaskLedger ledger) {
    return stockSnapshot(level, ledger, providers(level, graph));
  }

  public static Map<ItemKey, Integer> stockSnapshot(Level level, TaskLedger ledger, List<ProviderRef> providers) {
    Map<ItemKey, Integer> stock = new HashMap<>();
    java.util.Set<BlockPos> seen = new java.util.HashSet<>();
    for (ProviderRef provider : providers) {
      if (!seen.add(provider.inventoryPos())) {
        continue;
      }
      IItemHandler handler = Endpoint.resolveHandler(level, provider.inventoryPos(), provider.inventorySide());
      if (handler == null) {
        continue;
      }
      Map<ItemKey, Integer> counts = new HashMap<>();
      for (int i = 0; i < handler.getSlots(); i++) {
        ItemStack inSlot = handler.getStackInSlot(i);
        if (!inSlot.isEmpty() && provider.allows(inSlot)) {
          counts.merge(ItemKey.of(inSlot), inSlot.getCount(), Integer::sum);
        }
      }
      int reserve = provider.reserve();
      counts.forEach((item, count) -> {
        int usable = Math.max(0, count - reserve);
        if (usable > 0) {
          stock.merge(item, usable, Integer::sum);
        }
      });
    }
    stock.replaceAll((item, count) -> Math.max(0, count - ledger.reservedFor(item)));
    stock.values().removeIf(count -> count <= 0);
    return stock;
  }

  public void dropContents() {
    if (level == null || level.isClientSide()) {
      return;
    }
    for (int i = 0; i < modules.getSlots(); i++) {
      if (!modules.getStackInSlot(i).isEmpty()) {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
            modules.getStackInSlot(i));
        modules.setStackInSlot(i, ItemStack.EMPTY);
      }
    }
    for (int i = 0; i < upgrades.getSlots(); i++) {
      if (!upgrades.getStackInSlot(i).isEmpty()) {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
            upgrades.getStackInSlot(i));
        upgrades.setStackInSlot(i, ItemStack.EMPTY);
      }
    }
    for (int i = 0; i < collectorBuffer.getSlots(); i++) {
      if (!collectorBuffer.getStackInSlot(i).isEmpty()) {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
            collectorBuffer.getStackInSlot(i));
        collectorBuffer.setStackInSlot(i, ItemStack.EMPTY);
      }
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    CompoundTag modulesTag = new CompoundTag();
    modules.save(modulesTag);
    tag.put("modules", modulesTag);
    CompoundTag upgradesTag = new CompoundTag();
    upgrades.save(upgradesTag);
    tag.put("upgrades", upgradesTag);
    CompoundTag bufferTag = new CompoundTag();
    collectorBuffer.save(bufferTag);
    tag.put("collectorBuffer", bufferTag);
    if (selectedInventory != null) {
      tag.putInt("invDir", selectedInventory.get3DDataValue());
    }
    com.faktocraft.common.cover.CoverSupport.save(tag, cover, coverHoles);
    extensions.save(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    cover = com.faktocraft.common.cover.CoverSupport.load(tag);
    coverHoles = com.faktocraft.common.cover.CoverSupport.loadHoles(tag);
    extensions.load(tag);
    if (tag.contains("modules")) {
      modules.load(tag.getCompoundOrEmpty("modules"));
    }
    if (tag.contains("upgrades")) {
      upgrades.load(tag.getCompoundOrEmpty("upgrades"));
    }
    if (tag.contains("collectorBuffer")) {
      collectorBuffer.load(tag.getCompoundOrEmpty("collectorBuffer"));
    }
    selectedInventory = tag.contains("invDir") ? Direction.from3DDataValue(tag.getIntOr("invDir", 0)) : null;
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag tag = new CompoundTag();
    if (selectedInventory != null) {
      tag.putInt("invDir", selectedInventory.get3DDataValue());
    }
    com.faktocraft.common.cover.CoverSupport.save(tag, cover, coverHoles);
    extensions.resolveForSync(this);
    extensions.save(tag);
    return tag;
  }

  @Override
  public net.minecraft.network.protocol.Packet<
      net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(
        this, (entity, registries) -> ((BlockEntityChassis) entity).getUpdateTag(registries));
  }
}
