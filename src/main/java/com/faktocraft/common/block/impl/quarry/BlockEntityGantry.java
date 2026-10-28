package com.faktocraft.common.block.impl.quarry;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.pipe.PipeExtractor;
import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public abstract class BlockEntityGantry extends FaktocraftBlockEntity implements IEnergyBlock {

  public static final int INVENTORY_SLOTS = 27;
  public static final int UPGRADE_SLOTS = 4;
  public static final int FRAME_HEIGHT_ABOVE = 4;
  public static final int DEFAULT_FRAME_SPAN = 11;
  public static final int MAX_FRAME_SPAN = 30;
  public static final int MIN_FRAME_SPAN = 10;

  public static final TagKey<Block> QUARRY_MINEABLE = TagKey.create(Registries.BLOCK,
      new ResourceLocation("faktocraft", "quarry_mineable"));
  public static final TagKey<Block> QUARRY_BLACKLIST = TagKey.create(Registries.BLOCK,
      new ResourceLocation("faktocraft", "quarry_blacklist"));

  public static final int STAGE_AREA = 0;
  public static final int STAGE_CLEAR = 1;
  public static final int STAGE_FRAME = 2;
  public static final int STAGE_WORK = 3;
  public static final int STAGE_DONE = 4;

  public static final int STATUS_OFF = 0;
  public static final int STATUS_INVALID_AREA = 1;
  public static final int STATUS_CLEARING = 2;
  public static final int STATUS_FRAMING = 3;
  public static final int STATUS_WORKING = 4;
  public static final int STATUS_OBSTRUCTED = 5;
  public static final int STATUS_FULL = 6;
  public static final int STATUS_NO_ENERGY = 7;
  public static final int STATUS_WAITING_CHUNKS = 8;
  public static final int STATUS_DONE = 9;

  protected static final int SCAN_BUDGET = 128;
  protected static final int REPLACEABLE_BUDGET = 16;
  protected static final int AREA_RETRY_TICKS = 60;
  protected static final float ARM_BASE_SPEED = 0.25F;
  protected static final float ARM_BOOST_PER_POINT = 0.35F;
  protected static final double DRAW_BOOST_PER_POINT = 1.6 / 0.65;
  protected static final int MAX_UPGRADE_POINTS = 8;
  protected static final float GLOBAL_SPEED = 1.1F;
  protected static final float HEAD_HOVER = 1.03F;
  protected static final int DWELL_TICKS = 2;
  protected static final int FRAME_SKIPPED = 0;
  protected static final int FRAME_PLACED = 1;
  protected static final int FRAME_BLOCKED = 2;
  protected static final int SYNC_INTERVAL_TICKS = 5;

  protected final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityGantry.this.setChanged();
      BlockEntityGantry.this.onInventoryChanged();
    }
  };

  protected final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityGantry.this.setChanged();
    }
  };

  private final IItemHandler extractOnly = new IItemHandler() {
    @Override
    public int getSlots() {
      return inventory.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
      return inventory.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
      return stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
      return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
      return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
      return false;
    }
  };

  private final LazyOptional<IItemHandler> externalCap = LazyOptional.of(this::externalItemHandler);

  protected int runMode = PipeExtractor.RUN_ALWAYS;
  protected boolean areaSet = false;
  protected int minX;
  protected int minZ;
  protected int maxX;
  protected int maxZ;
  protected int stage = STAGE_AREA;
  protected int status = STATUS_OFF;
  protected int clearIndex = 0;
  protected int frameIndex = 0;
  protected int progress = 0;
  protected int areaRetry = 0;
  protected int syncTick = 0;
  private boolean targetDirty = false;

  protected boolean targetValid = false;
  protected int dwellTicks = 0;
  protected int targetX;
  protected int targetY;
  protected int targetZ;

  public double headX;
  public double headY;
  public double headZ;

  public double clientHeadX;
  public double clientHeadY;
  public double clientHeadZ;
  public double clientHeadLastTime = Double.NaN;
  public float clientRailY = Float.NaN;
  public double clientSyncedX = Double.NaN;
  public double clientSyncedY = Double.NaN;
  public double clientSyncedZ = Double.NaN;
  public long clientSyncTick;

  private List<BlockPos> framePositions;

  protected BlockEntityGantry(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity) {
    super(type, pos, state);
    createEnergyStorage(0, energyCapacity, EnergyType.RECEIVE, EnergyTier.HIGH);
    headX = pos.getX() + 0.5F;
    headY = pos.getY() + frameHeightAbove() + 0.5F;
    headZ = pos.getZ() + 0.5F;
  }

  public int frameHeightAbove() {
    return FRAME_HEIGHT_ABOVE;
  }

  protected Block frameBlock() {
    return QuarryRegistry.QUARRY_FRAME;
  }

  protected void onInventoryChanged() {
  }

  protected void onAreaResolved() {
  }

  protected abstract int tickWorkStage(ServerLevel serverLevel);

  protected abstract int configEnergyPerAction();

  protected abstract int configMaxDrawPerTick();

  protected boolean clearsInterior() {
    return false;
  }

  protected IItemHandler externalItemHandler() {
    return extractOnly;
  }

  protected ItemStack storeDrop(ItemStack drop) {
    return insertStacked(inventory, drop);
  }

  protected boolean workStatus(int value) {
    return value == STATUS_WORKING;
  }

  protected int renderBoxBottom() {
    return level != null ? level.getMinBuildHeight() : worldPosition.getY();
  }

  protected int renderBoxTop() {
    return worldPosition.getY() + frameHeightAbove() + 1;
  }

  protected void saveWork(CompoundTag tag) {
  }

  protected void loadWork(CompoundTag tag) {
  }

  protected void dropExtraContents() {
  }

  @Override
  public boolean supportsRedstoneControl() {
    return false;
  }

  @Override
  protected int worldSyncIntervalTicks() {
    return SYNC_INTERVAL_TICKS;
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return true;
  }

  public ItemStackHandler getInventory() {
    return inventory;
  }

  public ItemStackHandler getUpgrades() {
    return upgrades;
  }

  public int getRunMode() {
    return runMode;
  }

  public void setRunMode(int runMode) {
    this.runMode = net.minecraft.util.Mth.clamp(runMode, 0, 2);
    setChanged();
  }

  public int getStatus() {
    return status;
  }

  public void setStatusClient(int value) {
    status = value;
  }

  public boolean hasArea() {
    return areaSet;
  }

  public int stage() {
    return stage;
  }

  public int areaMinX() {
    return minX;
  }

  public int areaMinZ() {
    return minZ;
  }

  public int areaMaxX() {
    return maxX;
  }

  public int areaMaxZ() {
    return maxZ;
  }

  protected boolean allowedToRun() {
    return runMode != PipeExtractor.RUN_OFF
        && !(runMode == PipeExtractor.RUN_REDSTONE && getRedstonePower() <= 0);
  }

  private int upgradePoints(com.faktocraft.common.enums.UpgradeType type) {
    int points = 0;
    for (int i = 0; i < upgrades.getSlots(); i++) {
      var stack = upgrades.getStackInSlot(i);
      if (stack.getItem() instanceof com.faktocraft.common.interfaces.item.IUpgradeItem upgrade
          && upgrade.getUpgradeType() == type) {
        points += upgrade.isAdvancedUpgrade() ? 3 : 1;
      }
    }
    return Math.min(points, MAX_UPGRADE_POINTS);
  }

  protected float baseSpeedMultiplier() {
    return 1.0F;
  }

  protected int boostPoints() {
    return upgradePoints(com.faktocraft.common.enums.UpgradeType.OVERCLOCKER);
  }

  protected int efficiencyPoints() {
    return upgradePoints(com.faktocraft.common.enums.UpgradeType.EFFICIENCY);
  }

  public int energyPerBlock() {
    return (int) Math.ceil(Math.max(1, configEnergyPerAction())
        * Math.pow(1.6, boostPoints()) * Math.pow(0.85, efficiencyPoints()));
  }

  public int maxDrawPerTick() {
    float base = baseSpeedMultiplier();
    double perPoint = DRAW_BOOST_PER_POINT / Math.pow(base, 1.0 / MAX_UPGRADE_POINTS);
    return (int) Math.ceil(Math.max(1, configMaxDrawPerTick()) * GLOBAL_SPEED * base
        * Math.pow(perPoint, boostPoints()) * Math.pow(0.85, efficiencyPoints()));
  }

  public float armSpeed() {
    float base = baseSpeedMultiplier();
    float maxGain = 1.0F + ARM_BOOST_PER_POINT * MAX_UPGRADE_POINTS;
    float perPoint = (maxGain / base - 1.0F) / MAX_UPGRADE_POINTS;
    return GLOBAL_SPEED * Math.min(1.2F, ARM_BASE_SPEED * base * (1.0F + perPoint * boostPoints()));
  }

  @Override
  public void tickWork(BlockState state) {
    getEnergyStorage().updateConsumed(0);
    if (!(level instanceof ServerLevel serverLevel)) {
      return;
    }

    int newStatus;
    boolean worked = false;
    if (!allowedToRun()) {
      newStatus = STATUS_OFF;
    } else if (!areaSet) {
      newStatus = initArea(serverLevel) ? statusForStage() : STATUS_INVALID_AREA;
    } else {
      newStatus = switch (stage) {
        case STAGE_CLEAR -> tickClear(serverLevel);
        case STAGE_FRAME -> tickFrame(serverLevel);
        case STAGE_WORK -> tickWorkStage(serverLevel);
        default -> STATUS_DONE;
      };
      worked = stage >= STAGE_WORK && workStatus(newStatus);
    }

    moveHead();

    boolean statusChanged = newStatus != status;
    status = newStatus;
    boolean activeChanged = this.setActive(worked);
    if (statusChanged || targetDirty) {
      targetDirty = false;
      syncTick = 1;
      setChanged();
      serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    } else if (activeChanged || syncTick++ % SYNC_INTERVAL_TICKS == 0) {
      updateBlockState();
    }
  }

  private int statusForStage() {
    return switch (stage) {
      case STAGE_CLEAR -> STATUS_CLEARING;
      case STAGE_FRAME -> STATUS_FRAMING;
      case STAGE_WORK -> STATUS_WORKING;
      case STAGE_DONE -> STATUS_DONE;
      default -> STATUS_INVALID_AREA;
    };
  }

  private boolean initArea(ServerLevel serverLevel) {
    if (areaRetry > 0) {
      areaRetry--;
      return false;
    }
    areaRetry = AREA_RETRY_TICKS;

    int[] rect = BlockLandmark.rectAround(serverLevel, worldPosition);
    boolean marked = rect != null;
    if (!marked && BlockLandmark.touchesLandmarks(serverLevel, worldPosition)) {
      return false;
    }

    int rectMinX;
    int rectMaxX;
    int rectMinZ;
    int rectMaxZ;
    if (marked) {
      rectMinX = rect[0];
      rectMaxX = rect[1];
      rectMinZ = rect[2];
      rectMaxZ = rect[3];
    } else {
      Direction front = getBlockState().getBlock() instanceof IStateFacing facing
          ? facing.getDirection(getBlockState()).getOpposite()
          : Direction.NORTH;
      int half = DEFAULT_FRAME_SPAN / 2;
      BlockPos near = worldPosition.relative(front);
      BlockPos far = worldPosition.relative(front, DEFAULT_FRAME_SPAN);
      rectMinX = Math.min(near.getX(), far.getX()) - (front.getAxis() == Direction.Axis.Z ? half : 0);
      rectMaxX = Math.max(near.getX(), far.getX()) + (front.getAxis() == Direction.Axis.Z ? half : 0);
      rectMinZ = Math.min(near.getZ(), far.getZ()) - (front.getAxis() == Direction.Axis.X ? half : 0);
      rectMaxZ = Math.max(near.getZ(), far.getZ()) + (front.getAxis() == Direction.Axis.X ? half : 0);
    }

    int width = rectMaxX - rectMinX + 1;
    int depth = rectMaxZ - rectMinZ + 1;
    boolean hostInside = worldPosition.getX() >= rectMinX && worldPosition.getX() <= rectMaxX
        && worldPosition.getZ() >= rectMinZ && worldPosition.getZ() <= rectMaxZ;
    if (width < MIN_FRAME_SPAN || depth < MIN_FRAME_SPAN
        || width > MAX_FRAME_SPAN || depth > MAX_FRAME_SPAN || hostInside) {
      return false;
    }
    if (volumeHasForeignStructure(serverLevel, rectMinX, rectMaxX, rectMinZ, rectMaxZ)) {
      return false;
    }

    minX = rectMinX;
    maxX = rectMaxX;
    minZ = rectMinZ;
    maxZ = rectMaxZ;
    areaSet = true;
    stage = STAGE_CLEAR;
    clearIndex = 0;
    framePositions = null;
    onAreaResolved();

    if (marked) {
      popLandmarks(serverLevel);
    }
    setChanged();
    updateBlockState();
    return true;
  }

  private boolean volumeHasForeignStructure(ServerLevel serverLevel, int rectMinX, int rectMaxX,
      int rectMinZ, int rectMaxZ) {
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int x = rectMinX; x <= rectMaxX; x++) {
      for (int z = rectMinZ; z <= rectMaxZ; z++) {
        for (int y = worldPosition.getY(); y <= worldPosition.getY() + frameHeightAbove(); y++) {
          cursor.set(x, y, z);
          BlockState state = serverLevel.getBlockState(cursor);
          if (state.getBlock() instanceof BlockQuarryFrame
              || (IGantryHost.isHost(state) && !cursor.equals(worldPosition))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  public static BlockPos findRingObstruction(BlockGetter level, BlockPos host, int[] rect, int frameHeight) {
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int y = host.getY(); y <= host.getY() + frameHeight; y++) {
      for (int x = rect[0]; x <= rect[1]; x++) {
        for (int z = rect[2]; z <= rect[3]; z++) {
          if (x != rect[0] && x != rect[1] && z != rect[2] && z != rect[3]) {
            continue;
          }
          cursor.set(x, y, z);
          if (cursor.equals(host)) {
            continue;
          }
          BlockState state = level.getBlockState(cursor);
          if (state.isAir() || state.canBeReplaced() || state.is(QuarryRegistry.LANDMARK)
              || (!state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock)) {
            continue;
          }
          return cursor.immutable();
        }
      }
    }
    return null;
  }

  private void popLandmarks(ServerLevel serverLevel) {
    int y = worldPosition.getY();
    BlockPos[] corners = {
        new BlockPos(minX, y, minZ), new BlockPos(maxX, y, minZ),
        new BlockPos(minX, y, maxZ), new BlockPos(maxX, y, maxZ) };
    for (BlockPos corner : corners) {
      if (serverLevel.getBlockState(corner).is(QuarryRegistry.LANDMARK)) {
        serverLevel.removeBlock(corner, false);
        ItemStack leftover = insertStacked(inventory, new ItemStack(QuarryRegistry.LANDMARK_ITEM));
        if (!leftover.isEmpty()) {
          Containers.dropItemStack(serverLevel, corner.getX() + 0.5, corner.getY() + 0.5, corner.getZ() + 0.5,
              leftover);
        }
      }
    }
  }

  private int tickClear(ServerLevel serverLevel) {
    int width = maxX - minX + 1;
    int depth = maxZ - minZ + 1;
    int perLayer = width * depth;
    int total = perLayer * (frameHeightAbove() + 1);
    int replaceableBudget = REPLACEABLE_BUDGET;

    while (clearIndex < total) {
      int layer = clearIndex / perLayer;
      int inLayer = clearIndex % perLayer;
      BlockPos pos = new BlockPos(minX + inLayer % width, worldPosition.getY() + layer,
          minZ + inLayer / width);
      boolean onRing = pos.getX() == minX || pos.getX() == maxX || pos.getZ() == minZ || pos.getZ() == maxZ;
      if (pos.equals(worldPosition) || (!onRing && !clearsInterior())) {
        clearIndex++;
        continue;
      }
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      BlockState state = serverLevel.getBlockState(pos);
      if (state.isAir()
          || (!state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock)) {
        clearIndex++;
        continue;
      }
      if (state.canBeReplaced()) {
        serverLevel.destroyBlock(pos, false);
        clearIndex++;
        if (replaceableBudget-- <= 0) {
          return STATUS_CLEARING;
        }
        continue;
      }
      if (isMineable(serverLevel, pos, state)) {
        return breakAt(serverLevel, pos, state, STATUS_CLEARING, () -> clearIndex++, clearingTool());
      }
      if (onRing) {
        return STATUS_OBSTRUCTED;
      }
      clearIndex++;
    }
    stage = STAGE_FRAME;
    frameIndex = 0;
    setChanged();
    return STATUS_FRAMING;
  }

  protected ItemStack clearingTool() {
    return new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
  }

  public List<BlockPos> framePositions() {
    if (framePositions == null) {
      framePositions = buildFramePositions();
    }
    return framePositions;
  }

  protected void invalidateFramePositions() {
    framePositions = null;
  }

  protected List<BlockPos> buildFramePositions() {
    return framePositionsFor(frameHeightAbove());
  }

  protected List<BlockPos> framePositionsFor(int height) {
    List<BlockPos> list = new ArrayList<>();
    int y = worldPosition.getY();
    int top = y + height;
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        boolean onRing = x == minX || x == maxX || z == minZ || z == maxZ;
        boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
        if (onRing) {
          list.add(new BlockPos(x, y, z));
          list.add(new BlockPos(x, top, z));
        }
        if (corner) {
          for (int py = y + 1; py < top; py++) {
            list.add(new BlockPos(x, py, z));
          }
        }
      }
    }
    return list;
  }

  protected List<BlockPos> ringPositions(int y, boolean corners) {
    List<BlockPos> list = new ArrayList<>();
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        boolean onRing = x == minX || x == maxX || z == minZ || z == maxZ;
        boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
        if (onRing && (corners || !corner)) {
          list.add(new BlockPos(x, y, z));
        }
      }
    }
    return list;
  }

  protected List<BlockPos> pillarPositions(int yFrom, int yTo) {
    List<BlockPos> list = new ArrayList<>();
    for (int y = yFrom; y <= yTo; y++) {
      list.add(new BlockPos(minX, y, minZ));
      list.add(new BlockPos(maxX, y, minZ));
      list.add(new BlockPos(minX, y, maxZ));
      list.add(new BlockPos(maxX, y, maxZ));
    }
    return list;
  }

  protected int placeFrameAt(ServerLevel serverLevel, BlockPos pos) {
    BlockState state = serverLevel.getBlockState(pos);
    if (state.is(frameBlock())) {
      return FRAME_SKIPPED;
    }
    if (state.isAir() || state.canBeReplaced()
        || (!state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock)) {
      serverLevel.setBlock(pos, BlockQuarryFrame.connectedState(
          frameBlock().defaultBlockState(), serverLevel, pos), 3);
      return FRAME_PLACED;
    }
    return FRAME_BLOCKED;
  }

  private int tickFrame(ServerLevel serverLevel) {
    List<BlockPos> positions = framePositions();
    int placed = 0;
    while (frameIndex < positions.size() && placed < 2) {
      BlockPos pos = positions.get(frameIndex);
      if (pos.equals(worldPosition)) {
        frameIndex++;
        continue;
      }
      if (!serverLevel.isLoaded(pos)) {
        return STATUS_WAITING_CHUNKS;
      }
      int result = placeFrameAt(serverLevel, pos);
      if (result == FRAME_BLOCKED) {
        return STATUS_OBSTRUCTED;
      }
      frameIndex++;
      if (result == FRAME_PLACED) {
        placed++;
      }
    }
    if (frameIndex >= positions.size()) {
      stage = STAGE_WORK;
      targetValid = false;
      onWorkStageStarted();
      setChanged();
      return STATUS_WORKING;
    }
    return STATUS_FRAMING;
  }

  protected void onWorkStageStarted() {
  }

  protected static final int CHARGE_READY = 0;
  protected static final int CHARGE_PARTIAL = 1;
  protected static final int CHARGE_STARVED = 2;

  protected int chargeProgress(int cost) {
    if (progress >= cost) {
      return CHARGE_READY;
    }
    int draw = Math.min(maxDrawPerTick(), cost - progress);
    int accepted = getEnergyStorage().consumeEnergy(draw, true);
    if (accepted <= 0) {
      return CHARGE_STARVED;
    }
    getEnergyStorage().consumeEnergy(accepted, false);
    getEnergyStorage().updateConsumed(accepted);
    progress += accepted;
    return progress >= cost ? CHARGE_READY : CHARGE_PARTIAL;
  }

  protected int breakAt(ServerLevel serverLevel, BlockPos pos, BlockState state, int workingStatus,
      Runnable advance, ItemStack tool) {
    return breakAt(serverLevel, pos, state, workingStatus, breakCost(serverLevel, pos, state), advance, tool);
  }

  protected int breakCost(ServerLevel serverLevel, BlockPos pos, BlockState state) {
    return state.canBeReplaced() || state.getDestroySpeed(serverLevel, pos) == 0.0F ? 0 : energyPerBlock();
  }

  protected int breakAt(ServerLevel serverLevel, BlockPos pos, BlockState state, int workingStatus, int cost,
      Runnable advance, ItemStack tool) {
    int charge = chargeProgress(cost);
    if (charge != CHARGE_READY) {
      return charge == CHARGE_PARTIAL ? workingStatus : STATUS_NO_ENERGY;
    }
    List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), null, tool);
    if (!fitsAll(drops)) {
      return STATUS_FULL;
    }
    serverLevel.destroyBlock(pos, false);
    for (ItemStack drop : drops) {
      storeDrop(drop.copy());
    }
    progress -= cost;
    advance.run();
    return workingStatus;
  }

  protected boolean isMineable(ServerLevel serverLevel, BlockPos pos, BlockState state) {
    if (state.isAir() || state.canBeReplaced()) {
      return false;
    }
    if (!state.getFluidState().isEmpty() && state.getBlock() instanceof LiquidBlock) {
      return false;
    }
    if (state.getDestroySpeed(serverLevel, pos) < 0 || state.is(QUARRY_BLACKLIST)) {
      return false;
    }
    if (state.is(QUARRY_MINEABLE)) {
      return true;
    }
    if (state.hasBlockEntity()) {
      return false;
    }
    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
    return id == null || !Faktocraft.MODID.equals(id.getNamespace());
  }

  protected boolean fitsAll(List<ItemStack> drops) {
    ItemStackHandler temp = new ItemStackHandler(inventory.getSlots());
    for (int i = 0; i < inventory.getSlots(); i++) {
      temp.setStackInSlot(i, inventory.getStackInSlot(i).copy());
    }
    for (ItemStack drop : drops) {
      if (!insertStacked(temp, drop.copy()).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  protected static ItemStack insertStacked(ItemStackHandler handler, ItemStack stack) {
    for (int i = 0; i < handler.getSlots() && !stack.isEmpty(); i++) {
      if (!handler.getStackInSlot(i).isEmpty()) {
        stack = handler.insertItem(i, stack, false);
      }
    }
    for (int i = 0; i < handler.getSlots() && !stack.isEmpty(); i++) {
      stack = handler.insertItem(i, stack, false);
    }
    return stack;
  }

  protected void setTarget(BlockPos pos) {
    if (!targetValid || targetX != pos.getX() || targetY != pos.getY() || targetZ != pos.getZ()) {
      targetDirty = true;
    }
    targetValid = true;
    targetX = pos.getX();
    targetY = pos.getY();
    targetZ = pos.getZ();
  }

  protected void clearTarget() {
    if (targetValid) {
      targetDirty = true;
    }
    targetValid = false;
    dwellTicks = 0;
  }

  protected BlockPos targetPos() {
    return new BlockPos(targetX, targetY, targetZ);
  }

  protected boolean headAtTarget() {
    double dx = headX - (targetX + 0.5);
    double dy = headY - (targetY + HEAD_HOVER);
    double dz = headZ - (targetZ + 0.5);
    return dx * dx + dy * dy + dz * dz < 0.05;
  }

  protected boolean dwellComplete() {
    if (dwellTicks < DWELL_TICKS) {
      dwellTicks++;
      return false;
    }
    return true;
  }

  public boolean headGoal(double[] out) {
    if (stage == STAGE_WORK) {
      if (!targetValid) {
        return false;
      }
      out[0] = targetX + 0.5F;
      out[1] = targetY + HEAD_HOVER;
      out[2] = targetZ + 0.5F;
      return true;
    }
    if (areaSet) {
      out[0] = (minX + maxX + 1) / 2.0F;
      out[1] = worldPosition.getY() + frameHeightAbove() - 0.5F;
      out[2] = (minZ + maxZ + 1) / 2.0F;
      return true;
    }
    return false;
  }

  private void moveHead() {
    double[] goal = new double[3];
    if (!headGoal(goal)) {
      return;
    }
    double dx = goal[0] - headX;
    double dy = goal[1] - headY;
    double dz = goal[2] - headZ;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    double speed = armSpeed();
    if (distance <= speed) {
      headX = goal[0];
      headY = goal[1];
      headZ = goal[2];
    } else {
      headX += dx / distance * speed;
      headY += dy / distance * speed;
      headZ += dz / distance * speed;
    }
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
      return externalCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    externalCap.invalidate();
  }

  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    if (level instanceof ServerLevel serverLevel && areaSet) {
      for (BlockPos framePos : framePositions()) {
        if (serverLevel.getBlockState(framePos).is(frameBlock())) {
          serverLevel.removeBlock(framePos, false);
        }
      }
    }
    super.preRemoveSideEffects(pos, state);
  }

  protected void dropAll(ItemStackHandler handler) {
    if (level == null) {
      return;
    }
    for (int i = 0; i < handler.getSlots(); i++) {
      Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
          worldPosition.getZ() + 0.5, handler.getStackInSlot(i));
      handler.setStackInSlot(i, ItemStack.EMPTY);
    }
  }

  @Override
  public void onBreakServer() {
    super.onBreakServer();
    dropAll(inventory);
    dropAll(upgrades);
    dropExtraContents();
  }

  @Override
  public net.minecraft.world.phys.AABB getRenderBoundingBox() {
    if (!areaSet || level == null) {
      return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1);
    }
    return new net.minecraft.world.phys.AABB(minX, renderBoxBottom(), minZ, maxX + 1, renderBoxTop(), maxZ + 1);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putBoolean("active", activeState);
    tag.putInt("runMode", runMode);
    tag.putBoolean("areaSet", areaSet);
    tag.putInt("minX", minX);
    tag.putInt("minZ", minZ);
    tag.putInt("maxX", maxX);
    tag.putInt("maxZ", maxZ);
    tag.putInt("stage", stage);
    tag.putInt("clearIndex", clearIndex);
    tag.putInt("frameIndex", frameIndex);
    tag.putInt("progress", progress);
    tag.putBoolean("targetValid", targetValid);
    tag.putInt("targetX", targetX);
    tag.putInt("targetY", targetY);
    tag.putInt("targetZ", targetZ);
    tag.putDouble("headX", headX);
    tag.putDouble("headY", headY);
    tag.putDouble("headZ", headZ);
    CompoundTag inventoryTag = new CompoundTag();
    inventory.save(inventoryTag);
    tag.put("inventory", inventoryTag);
    CompoundTag upgradesTag = new CompoundTag();
    upgrades.save(upgradesTag);
    tag.put("upgrades", upgradesTag);
    saveWork(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    activeState = tag.getBoolean("active");
    runMode = tag.contains("runMode") ? tag.getInt("runMode") : PipeExtractor.RUN_ALWAYS;
    areaSet = tag.getBoolean("areaSet");
    minX = tag.getInt("minX");
    minZ = tag.getInt("minZ");
    maxX = tag.getInt("maxX");
    maxZ = tag.getInt("maxZ");
    stage = tag.getInt("stage");
    clearIndex = tag.getInt("clearIndex");
    frameIndex = tag.getInt("frameIndex");
    progress = tag.getInt("progress");
    targetValid = tag.getBoolean("targetValid");
    targetX = tag.getInt("targetX");
    targetY = tag.getInt("targetY");
    targetZ = tag.getInt("targetZ");
    if (tag.contains("headX")) {
      headX = tag.getDouble("headX");
      headY = tag.getDouble("headY");
      headZ = tag.getDouble("headZ");
    }
    if (tag.contains("inventory")) {
      inventory.load(tag.getCompound("inventory"));
    }
    if (tag.contains("upgrades")) {
      upgrades.load(tag.getCompound("upgrades"));
    }
    loadWork(tag);
  }
}
