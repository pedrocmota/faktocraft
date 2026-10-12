package com.faktocraft.common.block.impl.forester;

import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.block.impl.rubber_wood.RubberLog;
import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.interfaces.block.IStateRubberLog;
import com.faktocraft.common.item.impl.Fertilizer;
import com.faktocraft.common.registries.ModBlocks;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.util.BoneMealHelper;
import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockEntityForester extends BlockEntityGantry {

  public static final int SAPLING_SLOTS = 3;
  public static final int FERTILIZER_SLOTS = 2;
  public static final int INPUT_SLOTS = SAPLING_SLOTS + FERTILIZER_SLOTS;

  public static final int STATUS_IDLE = 10;
  public static final int STATUS_NO_SAPLINGS = 11;

  public static final int GRID = 6;
  public static final int GRID_OFFSET = 2;
  public static final int MAX_TREE_HEIGHT = 32;
  public static final int DEFAULT_FRAME_HEIGHT = 16;
  public static final int MIN_FRAME_HEIGHT = 6;
  public static final int MAX_FRAME_HEIGHT = 32;
  public static final int FRAME_MARGIN = 2;
  public static final int TREE_DEPTH_BELOW = 3;

  private static final int JOB_NONE = 0;
  private static final int JOB_PLANT = 1;
  private static final int JOB_FERTILIZE = 2;
  private static final int JOB_HARVEST = 3;
  private static final int JOB_TAP = 4;

  private static final int FERTILIZE_COOLDOWN = 30;
  private static final int FERTILIZE_MAX_DOSES = 12;
  private static final int STUBBORN_TICKS = 1200;
  private static final int RESIZE_NONE = 0;
  private static final int RESIZE_CHECK = 1;
  private static final int RESIZE_BUILD = 2;
  private static final int RESIZE_GLIDE = 3;
  private static final int RESIZE_REMOVE = 4;
  private static final int RESIZE_BUDGET = 2;
  private static final int CHECK_BUDGET = 64;
  private static final int GLIDE_MARGIN_TICKS = 10;
  private static final int SHRINK_RETRY_TICKS = 600;
  private static final int TREE_BLOCK_CAP = 2048;
  private static final ItemStack AXE = new ItemStack(Items.DIAMOND_AXE);

  private final ItemStackHandler inputs = new ItemStackHandler(INPUT_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityForester.this.setChanged();
      heightDirty = true;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return slot < SAPLING_SLOTS ? isSapling(stack) : isFertilizer(stack);
    }
  };

  private final IItemHandler external = new IItemHandler() {
    @Override
    public int getSlots() {
      return INPUT_SLOTS + inventory.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
      return slot < INPUT_SLOTS ? inputs.getStackInSlot(slot) : inventory.getStackInSlot(slot - INPUT_SLOTS);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
      if (slot >= INPUT_SLOTS || !inputs.isItemValid(slot, stack)) {
        return stack;
      }
      return inputs.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
      return slot < INPUT_SLOTS ? ItemStack.EMPTY : inventory.extractItem(slot - INPUT_SLOTS, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
      return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
      return slot < INPUT_SLOTS && inputs.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
      if (slot < INPUT_SLOTS) {
        inputs.setStackInSlot(slot, stack);
      } else {
        inventory.setStackInSlot(slot - INPUT_SLOTS, stack);
      }
    }

    @Override
    public boolean supportsSetStackInSlot() {
      return true;
    }
  };

  private boolean resinMode = false;
  private int scanIndex = 0;
  private boolean treeBoxSet;
  private int treeMinX;
  private int treeMinY;
  private int treeMinZ;
  private int treeMaxX;
  private int treeMaxY;
  private int treeMaxZ;
  private int job = JOB_NONE;
  private int treeBaseX;
  private int treeBaseZ;
  private int fertilizeCooldown = 0;
  private int fertilizeDoses = 0;
  private final Map<BlockPos, Integer> stubbornSaplings = new HashMap<>();
  private int emptyScanned = 0;
  private boolean plantableSeen = false;
  private int idleResult = 0;
  private int frameHeight = DEFAULT_FRAME_HEIGHT;
  private int frameTarget = DEFAULT_FRAME_HEIGHT;
  private boolean heightDirty = true;
  private int resizePhase = RESIZE_NONE;
  private int resizeOldHeight = DEFAULT_FRAME_HEIGHT;
  private int resizeNewHeight = DEFAULT_FRAME_HEIGHT;
  private int resizeIndex = 0;
  private int resizeWait = 0;
  private int shrinkRetry = 0;
  private List<BlockPos> resizeQueue = List.of();

  public BlockEntityForester(BlockPos pos, BlockState state) {
    super(ForesterRegistry.FORESTER_BLOCK_ENTITY, pos, state, ModConfig.server().forester_energy_capacity);
  }

  public static boolean isSapling(ItemStack stack) {
    return stack.getItem() instanceof BlockItem && SaplingHeights.isSupported(stack);
  }

  public boolean coversColumn(BlockPos pos) {
    return areaSet && pos.getX() > minX && pos.getX() < maxX && pos.getZ() > minZ && pos.getZ() < maxZ;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    ForesterAreas.register(this);
  }

  @Override
  public void setRemoved() {
    ForesterAreas.unregister(this);
    super.setRemoved();
  }

  public static boolean isFertilizer(ItemStack stack) {
    return stack.getItem() instanceof BoneMealItem || stack.getItem() instanceof Fertilizer;
  }

  public ItemStackHandler getInputs() {
    return inputs;
  }

  public boolean isResinMode() {
    return resinMode;
  }

  public void setResinMode(boolean value) {
    resinMode = value;
    setChanged();
  }

  @Override
  protected int configEnergyPerAction() {
    return ModConfig.server().forester_energy_per_action;
  }

  @Override
  protected int configMaxDrawPerTick() {
    return ModConfig.server().forester_max_draw_per_tick;
  }

  @Override
  protected IItemHandler externalItemHandler() {
    return external;
  }

  @Override
  protected ItemStack clearingTool() {
    return AXE;
  }

  @Override
  public int frameHeightAbove() {
    return frameHeight;
  }

  public int frameTarget() {
    return frameTarget;
  }

  public boolean isResizing() {
    return resizePhase != RESIZE_NONE;
  }

  @Override
  protected void onInventoryChanged() {
    heightDirty = true;
  }

  @Override
  protected void onAreaResolved() {
    int desired = desiredHeight();
    frameHeight = desired > 0 ? desired : MIN_FRAME_HEIGHT;
    frameTarget = frameHeight;
    resizePhase = RESIZE_NONE;
    invalidateFramePositions();
  }

  @Override
  protected List<BlockPos> buildFramePositions() {
    List<BlockPos> list = framePositionsFor(frameHeight);
    if (resizePhase == RESIZE_NONE) {
      return list;
    }
    int otherHeight = resizePhase == RESIZE_GLIDE || resizePhase == RESIZE_REMOVE ? resizeOldHeight
        : resizeNewHeight;
    if (otherHeight == frameHeight) {
      return list;
    }
    Set<BlockPos> seen = new HashSet<>(list);
    for (BlockPos pos : framePositionsFor(otherHeight)) {
      if (seen.add(pos)) {
        list.add(pos);
      }
    }
    return list;
  }

  @Override
  public boolean headGoal(double[] out) {
    if (stage == STAGE_WORK && resizePhase != RESIZE_NONE && !targetValid) {
      boolean client = level != null && level.isClientSide();
      out[0] = client ? clientHeadX : headX;
      out[1] = worldPosition.getY() + frameHeight - 0.5;
      out[2] = client ? clientHeadZ : headZ;
      return true;
    }
    return super.headGoal(out);
  }

  private int desiredHeight() {
    int best = -1;
    for (int i = 0; i < SAPLING_SLOTS; i++) {
      best = Math.max(best, potential(inputs.getStackInSlot(i)));
    }
    for (int i = 0; i < inventory.getSlots(); i++) {
      best = Math.max(best, potential(inventory.getStackInSlot(i)));
    }
    if (best < 0) {
      return -1;
    }
    return Math.max(MIN_FRAME_HEIGHT, Math.min(MAX_FRAME_HEIGHT, best + FRAME_MARGIN));
  }

  private static int potential(ItemStack stack) {
    return isSapling(stack) ? SaplingHeights.of(stack) : -1;
  }

  private void refreshFrameTarget() {
    if (!heightDirty) {
      return;
    }
    heightDirty = false;
    int desired = desiredHeight();
    if (desired > 0 && desired != frameTarget) {
      frameTarget = desired;
      setChanged();
    }
  }

  private void beginResize(int firstPhase) {
    resizePhase = firstPhase;
    resizeOldHeight = frameHeight;
    resizeNewHeight = frameTarget;
    resizeIndex = 0;
    resizeWait = 0;
    resizeQueue = firstPhase == RESIZE_BUILD ? buildQueue() : List.of();
    finishJob();
    invalidateFramePositions();
    setChanged();
  }

  private List<BlockPos> buildQueue() {
    int y0 = worldPosition.getY();
    if (resizeNewHeight > frameHeight) {
      List<BlockPos> list = pillarPositions(y0 + frameHeight + 1, y0 + resizeNewHeight - 1);
      list.addAll(ringPositions(y0 + resizeNewHeight, true));
      return list;
    }
    return ringPositions(y0 + resizeNewHeight, false);
  }

  private List<BlockPos> removeQueue() {
    int y0 = worldPosition.getY();
    if (frameHeight > resizeOldHeight) {
      return ringPositions(y0 + resizeOldHeight, false);
    }
    List<BlockPos> list = ringPositions(y0 + resizeOldHeight, true);
    for (int y = y0 + resizeOldHeight - 1; y > y0 + frameHeight; y--) {
      list.addAll(pillarPositions(y, y));
    }
    return list;
  }

  private int tickResize(ServerLevel serverLevel) {
    switch (resizePhase) {
      case RESIZE_CHECK -> {
        return tickShrinkCheck(serverLevel);
      }
      case RESIZE_BUILD -> {
        return tickResizeBuild(serverLevel);
      }
      case RESIZE_GLIDE -> {
        if (resizeWait-- <= 0) {
          resizePhase = RESIZE_REMOVE;
          resizeIndex = 0;
          resizeQueue = removeQueue();
          setChanged();
        }
        return STATUS_FRAMING;
      }
      case RESIZE_REMOVE -> {
        return tickResizeRemove(serverLevel);
      }
      default -> {
        resizePhase = RESIZE_NONE;
        return STATUS_WORKING;
      }
    }
  }

  private int tickResizeBuild(ServerLevel serverLevel) {
    int placed = 0;
    while (resizeIndex < resizeQueue.size() && placed < RESIZE_BUDGET) {
      BlockPos pos = resizeQueue.get(resizeIndex);
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      int result = placeFrameAt(serverLevel, pos);
      if (result == FRAME_BLOCKED) {
        BlockState state = serverLevel.getBlockState(pos);
        if (!isTreeBlock(state) && !isMineable(serverLevel, pos, state)) {
          return STATUS_OBSTRUCTED;
        }
        return breakAt(serverLevel, pos, state, STATUS_FRAMING, () -> {
        }, AXE);
      }
      resizeIndex++;
      if (result == FRAME_PLACED) {
        placed++;
      }
    }
    if (resizeIndex >= resizeQueue.size()) {
      frameHeight = resizeNewHeight;
      resizePhase = RESIZE_GLIDE;
      resizeWait = Mth.ceil(Math.abs(frameHeight - resizeOldHeight) / armSpeed()) + GLIDE_MARGIN_TICKS;
      invalidateFramePositions();
      setChanged();
    }
    return STATUS_FRAMING;
  }

  private int tickResizeRemove(ServerLevel serverLevel) {
    int removed = 0;
    while (resizeIndex < resizeQueue.size() && removed < RESIZE_BUDGET) {
      BlockPos pos = resizeQueue.get(resizeIndex);
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      if (serverLevel.getBlockState(pos).is(frameBlock())) {
        serverLevel.removeBlock(pos, false);
        removed++;
      }
      resizeIndex++;
    }
    if (resizeIndex >= resizeQueue.size()) {
      resizePhase = RESIZE_NONE;
      resizeQueue = List.of();
      invalidateFramePositions();
      setChanged();
    }
    return STATUS_FRAMING;
  }

  private int tickShrinkCheck(ServerLevel serverLevel) {
    int width = interiorWidth();
    int depth = interiorDepth();
    int perLayer = width * depth;
    int y0 = worldPosition.getY();
    int total = perLayer * (frameHeight - resizeNewHeight);
    int budget = CHECK_BUDGET;
    while (resizeIndex < total && budget-- > 0) {
      int layer = resizeIndex / perLayer;
      int inLayer = resizeIndex % perLayer;
      BlockPos pos = new BlockPos(minX + 1 + inLayer % width, y0 + resizeNewHeight + 1 + layer,
          minZ + 1 + inLayer / width);
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      BlockState state = serverLevel.getBlockState(pos);
      if (isTreeBlock(state)) {
        resizePhase = RESIZE_NONE;
        shrinkRetry = SHRINK_RETRY_TICKS;
        invalidateFramePositions();
        return STATUS_IDLE;
      }
      resizeIndex++;
    }
    if (resizeIndex >= total) {
      beginResize(RESIZE_BUILD);
      return STATUS_FRAMING;
    }
    return STATUS_IDLE;
  }

  @Override
  protected Block frameBlock() {
    return ForesterRegistry.FORESTER_FRAME;
  }

  @Override
  protected boolean workStatus(int value) {
    return value == STATUS_WORKING;
  }

  @Override
  protected int renderBoxBottom() {
    return worldPosition.getY() - TREE_DEPTH_BELOW;
  }

  @Override
  protected int renderBoxTop() {
    return worldPosition.getY() + MAX_TREE_HEIGHT + 1;
  }

  @Override
  protected ItemStack storeDrop(ItemStack drop) {
    if (isSapling(drop)) {
      for (int i = 0; i < SAPLING_SLOTS && !drop.isEmpty(); i++) {
        drop = inputs.insertItem(i, drop, false);
      }
    }
    return insertStacked(inventory, drop);
  }

  @Override
  protected void dropExtraContents() {
    dropAll(inputs);
  }

  @Override
  protected void onWorkStageStarted() {
    scanIndex = 0;
    job = JOB_NONE;
    emptyScanned = 0;
    plantableSeen = false;
    idleResult = 0;
  }

  private int interiorWidth() {
    return maxX - minX - 1;
  }

  private int interiorDepth() {
    return maxZ - minZ - 1;
  }

  private boolean isGridCell(int x, int z) {
    return (x - (minX + 1)) % GRID == GRID_OFFSET && (z - (minZ + 1)) % GRID == GRID_OFFSET;
  }

  private boolean inside(BlockPos pos) {
    int y0 = worldPosition.getY();
    return pos.getX() > minX && pos.getX() < maxX && pos.getZ() > minZ && pos.getZ() < maxZ
        && pos.getY() >= y0 - TREE_DEPTH_BELOW && pos.getY() <= y0 + MAX_TREE_HEIGHT;
  }

  private static boolean isRubber(BlockState state) {
    return state.is(ModBlocks.RUBBER_LOG) || state.is(ModBlocks.DRIED_RUBBER_LOG)
        || state.is(ModBlocks.RUBBER_LEAVES);
  }

  private static boolean isWetRubber(BlockState state) {
    return state.getBlock() instanceof IStateRubberLog rubber && state.is(ModBlocks.RUBBER_LOG)
        && rubber.isWet(state);
  }

  private static boolean isMangroveRoots(BlockState state) {
    return state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
  }

  private static boolean isTreeBlock(BlockState state) {
    return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || isMangroveRoots(state);
  }

  private boolean isTreePart(BlockState state) {
    if (resinMode && isRubber(state)) {
      return false;
    }
    return isTreeBlock(state);
  }

  @Nullable
  private BlockPos highestTreeBlock(ServerLevel serverLevel, BlockPos base) {
    if (!isTreePart(serverLevel.getBlockState(base))) {
      return null;
    }
    Set<BlockPos> visited = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(base);
    visited.add(base);
    BlockPos best = base;
    growTreeBox(base);
    while (!queue.isEmpty() && visited.size() < TREE_BLOCK_CAP) {
      BlockPos current = queue.poll();
      growTreeBox(current);
      if (current.getY() >= best.getY()) {
        best = current;
      }
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          for (int dz = -1; dz <= 1; dz++) {
            BlockPos next = current.offset(dx, dy, dz);
            if (visited.contains(next) || !inside(next) || !isTreePart(serverLevel.getBlockState(next))) {
              continue;
            }
            visited.add(next);
            queue.add(next);
          }
        }
      }
    }
    return best;
  }

  @Nullable
  private BlockPos findWetRubber(ServerLevel serverLevel, BlockPos base) {
    Set<BlockPos> visited = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    queue.add(base);
    visited.add(base);
    while (!queue.isEmpty() && visited.size() < TREE_BLOCK_CAP) {
      BlockPos current = queue.poll();
      BlockState state = serverLevel.getBlockState(current);
      if (isWetRubber(state)) {
        return current;
      }
      for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
        BlockPos next = current.relative(direction);
        BlockState nextState = serverLevel.getBlockState(next);
        if (visited.contains(next) || !inside(next)
            || !(nextState.is(ModBlocks.RUBBER_LOG) || nextState.is(ModBlocks.DRIED_RUBBER_LOG))) {
          continue;
        }
        visited.add(next);
        queue.add(next);
      }
    }
    return null;
  }

  private boolean canPlantAt(ServerLevel serverLevel, BlockPos pos) {
    BlockState state = serverLevel.getBlockState(pos);
    if (!state.isAir() && !(state.canBeReplaced() && state.getFluidState().isEmpty())) {
      return false;
    }
    return Blocks.OAK_SAPLING.defaultBlockState().canSurvive(serverLevel, pos);
  }

  private int saplingSlot() {
    for (int i = 0; i < SAPLING_SLOTS; i++) {
      if (isSapling(inputs.getStackInSlot(i))) {
        return i;
      }
    }
    return -1;
  }

  private int storedSaplingSlot() {
    for (int i = 0; i < inventory.getSlots(); i++) {
      if (isSapling(inventory.getStackInSlot(i))) {
        return i;
      }
    }
    return -1;
  }

  private boolean hasSapling() {
    return saplingSlot() >= 0 || storedSaplingSlot() >= 0;
  }

  private ItemStack takeSapling() {
    int slot = saplingSlot();
    if (slot >= 0) {
      return inputs.extractItem(slot, 1, false);
    }
    slot = storedSaplingSlot();
    return slot >= 0 ? inventory.extractItem(slot, 1, false) : ItemStack.EMPTY;
  }

  private int fertilizerSlot() {
    for (int i = SAPLING_SLOTS; i < INPUT_SLOTS; i++) {
      if (isFertilizer(inputs.getStackInSlot(i))) {
        return i;
      }
    }
    return -1;
  }

  private int actionCost(BlockState state) {
    int base = energyPerBlock();
    if (job == JOB_HARVEST) {
      return harvestCost(state);
    }
    return job == JOB_PLANT || job == JOB_FERTILIZE ? Math.max(1, base / 2) : base;
  }

  public int harvestCost(BlockState state) {
    int base = energyPerBlock();
    return state.is(BlockTags.LEAVES) ? Math.max(1, base / 2) : base;
  }

  private void startJob(int kind, BlockPos target) {
    job = kind;
    setTarget(target);
    emptyScanned = 0;
    plantableSeen = false;
    idleResult = 0;
    fertilizeDoses = 0;
  }

  private void tickStubborn() {
    Iterator<Map.Entry<BlockPos, Integer>> it = stubbornSaplings.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<BlockPos, Integer> entry = it.next();
      if (entry.getValue() <= 1) {
        it.remove();
      } else {
        entry.setValue(entry.getValue() - 1);
      }
    }
  }

  private void finishJob() {
    job = JOB_NONE;
    treeBoxSet = false;
    clearTarget();
  }

  private void growTreeBox(BlockPos pos) {
    if (!treeBoxSet) {
      treeBoxSet = true;
      treeMinX = pos.getX();
      treeMinY = pos.getY();
      treeMinZ = pos.getZ();
      treeMaxX = pos.getX();
      treeMaxY = pos.getY();
      treeMaxZ = pos.getZ();
      return;
    }
    treeMinX = Math.min(treeMinX, pos.getX());
    treeMinY = Math.min(treeMinY, pos.getY());
    treeMinZ = Math.min(treeMinZ, pos.getZ());
    treeMaxX = Math.max(treeMaxX, pos.getX());
    treeMaxY = Math.max(treeMaxY, pos.getY());
    treeMaxZ = Math.max(treeMaxZ, pos.getZ());
  }

  @Nullable
  private BlockPos nextHarvestTarget(ServerLevel serverLevel) {
    BlockPos next = highestTreeBlock(serverLevel, new BlockPos(treeBaseX, worldPosition.getY(), treeBaseZ));
    return next != null ? next : leftoverLeaf(serverLevel);
  }

  @Nullable
  private BlockPos leftoverLeaf(ServerLevel serverLevel) {
    if (!treeBoxSet) {
      return null;
    }
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int y = treeMaxY; y >= treeMinY; y--) {
      for (int x = treeMinX; x <= treeMaxX; x++) {
        for (int z = treeMinZ; z <= treeMaxZ; z++) {
          cursor.set(x, y, z);
          BlockState state = serverLevel.getBlockState(cursor);
          if (state.is(BlockTags.LEAVES) && inside(cursor) && isTreePart(state)) {
            return cursor.immutable();
          }
        }
      }
    }
    return null;
  }

  @Override
  protected int tickWorkStage(ServerLevel serverLevel) {
    if (fertilizeCooldown > 0) {
      fertilizeCooldown--;
    }
    if (shrinkRetry > 0) {
      shrinkRetry--;
    }
    if (!stubbornSaplings.isEmpty()) {
      tickStubborn();
    }
    if (resizePhase != RESIZE_NONE) {
      return tickResize(serverLevel);
    }
    refreshFrameTarget();
    if (frameTarget > frameHeight) {
      beginResize(RESIZE_BUILD);
      return tickResize(serverLevel);
    }
    if (!targetValid) {
      int result = scan(serverLevel);
      if (frameTarget < frameHeight && shrinkRetry == 0
          && (result == STATUS_IDLE || result == STATUS_NO_SAPLINGS)) {
        beginResize(RESIZE_CHECK);
      }
      return result;
    }
    return work(serverLevel);
  }

  private int scan(ServerLevel serverLevel) {
    int width = interiorWidth();
    int depth = interiorDepth();
    int total = width * depth;
    int y0 = worldPosition.getY();
    int budget = Math.min(SCAN_BUDGET, total);
    while (budget-- > 0) {
      int index = scanIndex % total;
      scanIndex = (scanIndex + 1) % total;
      int x = minX + 1 + index % width;
      int z = minZ + 1 + index / width;
      BlockPos pos = new BlockPos(x, y0, z);
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      BlockState state = serverLevel.getBlockState(pos);
      if (state.is(BlockTags.LOGS) || isMangroveRoots(state)) {
        if (isRubber(state)) {
          BlockPos wet = findWetRubber(serverLevel, pos);
          if (wet != null) {
            startJob(JOB_TAP, wet);
            return STATUS_WORKING;
          }
          if (resinMode) {
            countScanned(total);
            continue;
          }
        }
        treeBoxSet = false;
        BlockPos top = highestTreeBlock(serverLevel, pos);
        if (top != null) {
          treeBaseX = x;
          treeBaseZ = z;
          startJob(JOB_HARVEST, top);
          return STATUS_WORKING;
        }
      } else if (state.is(BlockTags.SAPLINGS)) {
        if (fertilizeCooldown == 0 && fertilizerSlot() >= 0 && !stubbornSaplings.containsKey(pos)) {
          startJob(JOB_FERTILIZE, pos);
          return STATUS_WORKING;
        }
      } else if (isGridCell(x, z) && canPlantAt(serverLevel, pos)) {
        if (hasSapling()) {
          startJob(JOB_PLANT, pos);
          return STATUS_WORKING;
        }
        plantableSeen = true;
      }
      countScanned(total);
    }
    return idleResult != 0 ? idleResult : STATUS_WORKING;
  }

  private void countScanned(int total) {
    emptyScanned++;
    if (emptyScanned >= total) {
      idleResult = plantableSeen ? STATUS_NO_SAPLINGS : STATUS_IDLE;
      emptyScanned = 0;
      plantableSeen = false;
    }
  }

  private int work(ServerLevel serverLevel) {
    BlockPos pos = targetPos();
    if (!serverLevel.isLoaded(pos)) {
      return STATUS_WAITING_CHUNKS;
    }
    BlockState state = serverLevel.getBlockState(pos);
    if (!jobStillValid(serverLevel, pos, state)) {
      return STATUS_WORKING;
    }
    if (job == JOB_FERTILIZE && fertilizeCooldown > 0) {
      return STATUS_WORKING;
    }
    int cost = actionCost(state);
    int charge = chargeProgress(cost);
    if (charge == CHARGE_READY && headAtTarget()) {
      if (!dwellComplete()) {
        return STATUS_WORKING;
      }
      return perform(serverLevel, pos, state, cost);
    }
    dwellTicks = 0;
    return charge == CHARGE_STARVED ? STATUS_NO_ENERGY : STATUS_WORKING;
  }

  private boolean jobStillValid(ServerLevel serverLevel, BlockPos pos, BlockState state) {
    switch (job) {
      case JOB_HARVEST -> {
        if (isTreePart(state)) {
          return true;
        }
        BlockPos next = nextHarvestTarget(serverLevel);
        if (next != null) {
          setTarget(next);
        } else {
          finishJob();
        }
        return false;
      }
      case JOB_TAP -> {
        if (isWetRubber(state)) {
          return true;
        }
        finishJob();
        return false;
      }
      case JOB_FERTILIZE -> {
        if (state.is(BlockTags.SAPLINGS) && fertilizerSlot() >= 0) {
          return true;
        }
        finishJob();
        return false;
      }
      case JOB_PLANT -> {
        if (canPlantAt(serverLevel, pos) && hasSapling()) {
          return true;
        }
        finishJob();
        return false;
      }
      default -> {
        finishJob();
        return false;
      }
    }
  }

  private int perform(ServerLevel serverLevel, BlockPos pos, BlockState state, int cost) {
    switch (job) {
      case JOB_HARVEST -> {
        return breakAt(serverLevel, pos, state, STATUS_WORKING, cost, () -> {
          dwellTicks = 0;
          BlockPos next = nextHarvestTarget(serverLevel);
          if (next != null) {
            setTarget(next);
          } else {
            finishJob();
          }
        }, AXE);
      }
      case JOB_TAP -> {
        List<ItemStack> drops = RubberLog.tapDrops(serverLevel.getRandom(), state);
        if (!fitsAll(drops)) {
          return STATUS_FULL;
        }
        serverLevel.setBlock(pos, RubberLog.tapped(state), 2);
        for (ItemStack drop : drops) {
          storeDrop(drop);
        }
        serverLevel.playSound(null, pos, ModSounds.TREETAP, SoundSource.BLOCKS, 0.5F, 1.0F);
        progress -= cost;
        finishJob();
        return STATUS_WORKING;
      }
      case JOB_FERTILIZE -> {
        int slot = fertilizerSlot();
        if (slot >= 0 && BoneMealHelper.grow(inputs.getStackInSlot(slot), serverLevel, pos)) {
          serverLevel.levelEvent(1505, pos, 0);
          inputs.setStackInSlot(slot, inputs.getStackInSlot(slot));
        }
        fertilizeCooldown = FERTILIZE_COOLDOWN;
        progress -= cost;
        dwellTicks = 0;
        if (++fertilizeDoses >= FERTILIZE_MAX_DOSES) {
          stubbornSaplings.put(pos, STUBBORN_TICKS);
          finishJob();
        }
        return STATUS_WORKING;
      }
      case JOB_PLANT -> {
        ItemStack sapling = takeSapling();
        if (sapling.getItem() instanceof BlockItem blockItem) {
          BlockState planted = blockItem.getBlock().defaultBlockState();
          if (planted.canSurvive(serverLevel, pos)) {
            serverLevel.setBlock(pos, planted, 3);
            serverLevel.playSound(null, pos, planted.getSoundType(serverLevel, pos, null).getPlaceSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
          } else {
            storeDrop(sapling);
          }
        }
        progress -= cost;
        finishJob();
        return STATUS_WORKING;
      }
      default -> {
        finishJob();
        return STATUS_WORKING;
      }
    }
  }

  @Override
  protected void saveWork(CompoundTag tag) {
    CompoundTag inputsTag = new CompoundTag();
    inputs.save(inputsTag);
    tag.put("inputs", inputsTag);
    tag.putBoolean("resinMode", resinMode);
    tag.putInt("scanIndex", scanIndex);
    tag.putInt("job", job);
    tag.putInt("treeBaseX", treeBaseX);
    tag.putInt("treeBaseZ", treeBaseZ);
    tag.putInt("fertilizeCooldown", fertilizeCooldown);
    tag.putInt("fertilizeDoses", fertilizeDoses);
    tag.putInt("emptyScanned", emptyScanned);
    if (treeBoxSet) {
      tag.putIntArray("treeBox", new int[] { treeMinX, treeMinY, treeMinZ, treeMaxX, treeMaxY, treeMaxZ });
    }
    tag.putBoolean("plantableSeen", plantableSeen);
    tag.putInt("idleResult", idleResult);
    tag.putInt("frameHeight", frameHeight);
    tag.putInt("frameTarget", frameTarget);
    tag.putInt("resizePhase", resizePhase);
    tag.putInt("resizeOldHeight", resizeOldHeight);
    tag.putInt("resizeNewHeight", resizeNewHeight);
    tag.putInt("resizeIndex", resizeIndex);
    tag.putInt("resizeWait", resizeWait);
    tag.putInt("shrinkRetry", shrinkRetry);
  }

  @Override
  protected void loadWork(CompoundTag tag) {
    if (tag.contains("inputs")) {
      inputs.load(tag.getCompoundOrEmpty("inputs"));
    }
    resinMode = tag.getBooleanOr("resinMode", false);
    scanIndex = tag.getIntOr("scanIndex", 0);
    job = tag.getIntOr("job", 0);
    treeBaseX = tag.getIntOr("treeBaseX", 0);
    treeBaseZ = tag.getIntOr("treeBaseZ", 0);
    fertilizeCooldown = tag.getIntOr("fertilizeCooldown", 0);
    fertilizeDoses = tag.getIntOr("fertilizeDoses", 0);
    emptyScanned = tag.getIntOr("emptyScanned", 0);
    int[] box = tag.getIntArray("treeBox").orElse(new int[0]);
    treeBoxSet = box.length == 6;
    if (treeBoxSet) {
      treeMinX = box[0];
      treeMinY = box[1];
      treeMinZ = box[2];
      treeMaxX = box[3];
      treeMaxY = box[4];
      treeMaxZ = box[5];
    }
    plantableSeen = tag.getBooleanOr("plantableSeen", false);
    idleResult = tag.getIntOr("idleResult", 0);
    frameHeight = tag.contains("frameHeight") ? tag.getIntOr("frameHeight", 0) : DEFAULT_FRAME_HEIGHT;
    frameTarget = tag.contains("frameTarget") ? tag.getIntOr("frameTarget", 0) : frameHeight;
    resizePhase = tag.getIntOr("resizePhase", 0);
    resizeOldHeight = tag.contains("resizeOldHeight") ? tag.getIntOr("resizeOldHeight", 0) : frameHeight;
    resizeNewHeight = tag.contains("resizeNewHeight") ? tag.getIntOr("resizeNewHeight", 0) : frameTarget;
    resizeIndex = tag.getIntOr("resizeIndex", 0);
    resizeWait = tag.getIntOr("resizeWait", 0);
    shrinkRetry = tag.getIntOr("shrinkRetry", 0);
    if (resizePhase == RESIZE_BUILD) {
      resizeQueue = buildQueue();
    } else if (resizePhase == RESIZE_REMOVE) {
      resizeQueue = removeQueue();
    } else {
      resizeQueue = List.of();
    }
    heightDirty = true;
    invalidateFramePositions();
  }
}
