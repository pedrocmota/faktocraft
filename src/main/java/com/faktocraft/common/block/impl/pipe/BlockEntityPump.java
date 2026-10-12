package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.FluidStorage;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.TransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.faktocraft.common.util.transfer.Capability;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.LazyOptional;
import net.neoforged.neoforge.fluids.FluidStack;
import com.faktocraft.common.util.transfer.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class BlockEntityPump extends FaktocraftBlockEntity
    implements IEnergyBlock, com.faktocraft.common.interfaces.entity.ITileSound {
  @org.jetbrains.annotations.Nullable
  @Override
  public net.minecraft.sounds.SoundEvent getSoundEvent() {
    return com.faktocraft.common.registries.ModSounds.PUMP;
  }

  @Override
  public float getVolume() {
    return 0.25F;
  }

  public static final int TANK_CAPACITY_MB = 16000;
  private static final int ENERGY_PER_BUCKET = 1600;
  private static final int MAX_DRAW_PER_TICK = 4;
  public static final int UPGRADE_SLOTS = 3;

  private final com.faktocraft.common.util.ItemStackHandler upgrades = new com.faktocraft.common.util.ItemStackHandler(
      UPGRADE_SLOTS) {
    @Override
    protected void onContentsChanged(int slot) {
      BlockEntityPump.this.setChanged();
    }
  };

  public com.faktocraft.common.util.ItemStackHandler getUpgrades() {
    return upgrades;
  }

  private int points(com.faktocraft.common.enums.UpgradeType type) {
    int points = 0;
    for (int i = 0; i < upgrades.getSlots(); i++) {
      var stack = upgrades.getStackInSlot(i);
      if (stack.getItem() instanceof com.faktocraft.common.interfaces.item.IUpgradeItem upgrade
          && upgrade.getUpgradeType() == type) {
        points += upgrade.isAdvancedUpgrade() ? 3 : 1;
      }
    }
    return Math.min(points, 8);
  }

  private int boostPoints() {
    return points(com.faktocraft.common.enums.UpgradeType.OVERCLOCKER);
  }

  private int efficiencyPoints() {
    return points(com.faktocraft.common.enums.UpgradeType.EFFICIENCY);
  }

  public int energyPerBucket() {
    return (int) Math.ceil(ENERGY_PER_BUCKET * Math.pow(1.6, boostPoints()) * Math.pow(0.85, efficiencyPoints()));
  }

  public int maxDrawPerTick() {
    return (int) Math.ceil(MAX_DRAW_PER_TICK * Math.pow(1.6 / 0.7, boostPoints()));
  }

  private static final int PUSH_RATE_MB = 200;

  private static final int MAX_DEPTH = 384;
  private static final int MAX_DISTANCE = 64;
  private static final int MAX_QUEUE = 4096;

  private static final float TUBE_SPEED = 0.15F;
  public float tubeDepth = 0.0F;
  private float tubeTarget = 0.0F;
  private int tubeSyncTick = 0;

  public float clientTubeDepth = 0.0F;
  public double clientTubeLastTime = Double.NaN;

  private int runMode = PipeExtractor.RUN_ALWAYS;

  public int getRunMode() {
    return runMode;
  }

  public void setRunMode(int runMode) {
    this.runMode = net.minecraft.util.Mth.clamp(runMode, 0, 2);
    setChanged();
  }

  @Override
  public boolean supportsRedstoneControl() {
    return false;
  }

  @Override
  protected int worldSyncIntervalTicks() {
    return tubeDepth != tubeTarget ? 1 : super.worldSyncIntervalTicks();
  }

  private final com.faktocraft.common.util.NeighborFluidCache neighborFluidCache =
      new com.faktocraft.common.util.NeighborFluidCache(
          this);

  private boolean allowedToRun() {
    return runMode != PipeExtractor.RUN_OFF
        && !(runMode == PipeExtractor.RUN_REDSTONE && getRedstonePower() <= 0);
  }

  private static final int REBUILD_COOLDOWN_TICKS = 60;

  public final FluidStorage tank = new FluidStorage(TANK_CAPACITY_MB);

  public final IFluidHandler drainOnlyTank = new IFluidHandler() {
    @Override
    public int getTanks() {
      return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tankIndex) {
      return tank.getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
      return tank.getCapacityMb();
    }

    @Override
    public boolean isFluidValid(int tankIndex, @NotNull FluidStack stack) {
      return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      return 0;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
      return tank.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
      return tank.drain(maxDrain, action);
    }
  };

  private final LazyOptional<IFluidHandler> drainOnlyCap = LazyOptional.of(() -> drainOnlyTank);

  private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
  private Fluid queueFluid = Fluids.EMPTY;
  private int progress = 0;
  private int rebuildCooldown = 0;
  private int lastSyncedFluid = -1;

  public int queuedSources() {
    return queue.size();
  }

  public int pumpProgress() {
    return progress;
  }

  public float tubeTarget() {
    return tubeTarget;
  }

  public BlockEntityPump(BlockPos pos, BlockState state) {
    super(PipeRegistry.PUMP_BLOCK_ENTITY, pos, state);
    createEnergyStorage(0, 4000, EnergyType.RECEIVE, EnergyTier.LOW);
    tank.setChangeListener(this::setChanged);
  }

  @Override
  public void tickWork(BlockState state) {
    getEnergyStorage().updateConsumed(0);
    if (level == null) {
      return;
    }

    boolean worked = false;
    if (rebuildCooldown > 0) {
      rebuildCooldown--;
    }
    if (queue.isEmpty() && rebuildCooldown == 0) {
      buildQueue();
      rebuildCooldown = REBUILD_COOLDOWN_TICKS;
    }

    if (tubeDepth != tubeTarget) {
      int oldSolid = (int) Math.floor(tubeDepth);
      tubeDepth = tubeDepth < tubeTarget
          ? Math.min(tubeTarget, tubeDepth + TUBE_SPEED)
          : Math.max(tubeTarget, tubeDepth - TUBE_SPEED);
      if ((tubeSyncTick++ & 1) == 0 || tubeDepth == tubeTarget) {
        updateBlockState();
      }
      if (oldSolid != (int) Math.floor(tubeDepth)) {
        syncTubeBlocks();
      }
    } else if ((tubeSyncTick++ & 63) == 0) {
      syncTubeBlocks();
    }

    if (allowedToRun() && tubeExtended() && !queue.isEmpty()
        && tank.getCapacityMb() - tank.getFluidAmount() >= 1000) {
      int draw = Math.min(maxDrawPerTick(), energyPerBucket() - progress);
      int accepted = getEnergyStorage().consumeEnergy(draw, true);
      if (accepted > 0) {
        getEnergyStorage().consumeEnergy(accepted, false);
        getEnergyStorage().updateConsumed(accepted);
        progress += accepted;
        worked = true;
      }
      if (progress >= energyPerBucket()) {
        drainNext();
      }
    }

    pushFluidAround();

    if (tank.getFluidAmount() != lastSyncedFluid) {
      lastSyncedFluid = tank.getFluidAmount();
      updateBlockState();
    }

    boolean wasActive = activeState;
    setActive(worked);
    if (wasActive != worked) {
      updateBlockState();
    }
  }

  private void drainNext() {
    while (!queue.isEmpty()) {
      BlockPos target = queue.pollLast();
      FluidState fluidState = level.getFluidState(target);
      if (!fluidState.isSource() || !fluidState.getType().isSame(queueFluid)
          || !(level.getBlockState(target).getBlock() instanceof LiquidBlock)) {
        continue;
      }
      FluidStack bucket = new FluidStack(fluidState.getType(), 1000);
      if (tank.fillFluid(bucket, 1000, true) != 1000) {
        queue.addLast(target);
        return;
      }
      tank.fillFluid(bucket, 1000, false);
      progress -= energyPerBucket();
      if (!isInfiniteWater(target)) {
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
      }
      return;
    }
  }

  private boolean isInfiniteWater(BlockPos pos) {
    if (!queueFluid.isSame(Fluids.WATER)) {
      return false;
    }
    int sources = 0;
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      FluidState neighbor = level.getFluidState(pos.relative(direction));
      if (neighbor.isSource() && neighbor.getType().isSame(Fluids.WATER)) {
        sources++;
      }
    }
    return sources >= 2;
  }

  private void buildQueue() {
    queue.clear();
    queueFluid = Fluids.EMPTY;

    BlockPos.MutableBlockPos scan = worldPosition.mutable().move(Direction.DOWN);
    BlockPos surface = null;
    while (!level.isOutsideBuildHeight(scan) && worldPosition.getY() - scan.getY() <= MAX_DEPTH) {
      FluidState fluidState = level.getFluidState(scan);
      if (!fluidState.isEmpty()) {
        surface = scan.immutable();
        queueFluid = baseFluid(fluidState);
        break;
      }
      var scanState = level.getBlockState(scan);
      if (!scanState.isAir()
          && !scanState.is(com.faktocraft.common.registries.PipeRegistry.PUMP_TUBE_BLOCK)) {
        return;
      }
      scan.move(Direction.DOWN);
    }
    if (surface == null) {
      tubeTarget = 0.0F;
      return;
    }
    tubeTarget = (worldPosition.getY() - surface.getY() - 1) + 0.4F;

    Set<BlockPos> checked = new HashSet<>();
    ArrayDeque<BlockPos> open = new ArrayDeque<>();
    open.add(surface);
    checked.add(surface);
    Direction[] searchDirections = { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
        Direction.EAST };

    while (!open.isEmpty() && queue.size() < MAX_QUEUE) {
      BlockPos current = open.poll();
      FluidState fluidState = level.getFluidState(current);
      if (fluidState.isSource()) {
        queue.addLast(current);
      }
      for (Direction direction : searchDirections) {
        BlockPos next = current.relative(direction);
        if (Math.abs(next.getX() - surface.getX()) > MAX_DISTANCE
            || Math.abs(next.getZ() - surface.getZ()) > MAX_DISTANCE
            || !checked.add(next)) {
          continue;
        }
        FluidState nextState = level.getFluidState(next);
        if (!nextState.isEmpty() && nextState.getType().isSame(queueFluid)) {
          open.add(next);
        }
      }
    }
  }

  private static Fluid baseFluid(FluidState state) {
    return state.getType().isSame(Fluids.WATER) ? Fluids.WATER
        : state.getType().isSame(Fluids.LAVA) ? Fluids.LAVA : state.getType();
  }

  private void pushFluidAround() {
    for (Direction direction : Constants.DIRECTIONS) {
      if (tank.isEmpty()) {
        return;
      }
      if (direction == Direction.DOWN) {
        continue;
      }
      BlockPos neighborPos = worldPosition.relative(direction);

      if (level.getBlockEntity(neighborPos) instanceof BlockEntityFluidPipe neighborPipe) {
        if (neighborPipe.getTier() == BlockFluidPipe.Tier.EXTRACTOR || neighborPipe.valveClosed()) {
          continue;
        }
        int accepted = neighborPipe.tank.fillFluid(tank.getFluidStack(),
            Math.min(PUSH_RATE_MB, tank.getFluidAmount()), false);
        if (accepted > 0) {
          tank.takeFluid(accepted, false);
        }
        continue;
      }

      IFluidHandler target = neighborFluidCache.get(direction);
      if (target != null && !(level.getBlockEntity(neighborPos) instanceof BlockEntityPump)) {
        TransferUtil.moveFluid(tank, target, PUSH_RATE_MB);
      }
    }
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
      return drainOnlyCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    super.setRemoved();
    drainOnlyCap.invalidate();
  }

  public static void clearTubeColumn(net.minecraft.world.level.Level level, BlockPos pumpPos) {
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int i = 1; i <= MAX_DEPTH; i++) {
      cursor.setWithOffset(pumpPos, 0, -i, 0);
      var state = level.getBlockState(cursor);
      if (state.is(com.faktocraft.common.registries.PipeRegistry.PUMP_TUBE_BLOCK)) {
        level.removeBlock(cursor, false);
      } else if (!state.isAir()) {
        break;
      }
    }
  }

  private boolean tubeExtended() {
    return tubeTarget > 0 && tubeDepth >= tubeTarget - 0.01F;
  }

  private void syncTubeBlocks() {
    if (level == null || level.isClientSide()) {
      return;
    }
    int solid = (int) Math.floor(tubeDepth);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int i = 1; i <= MAX_DEPTH; i++) {
      cursor.setWithOffset(worldPosition, 0, -i, 0);
      var state = level.getBlockState(cursor);
      boolean isTube = state.is(com.faktocraft.common.registries.PipeRegistry.PUMP_TUBE_BLOCK);
      if (i <= solid) {
        if (state.isAir()) {
          level.setBlock(cursor, com.faktocraft.common.registries.PipeRegistry.PUMP_TUBE_BLOCK.defaultBlockState(),
              3);
        } else if (!isTube) {
          break;
        }
      } else {
        if (isTube) {
          level.removeBlock(cursor, false);
        } else if (!state.isAir()) {
          break;
        }
      }
    }
  }

  public net.minecraft.world.phys.AABB getRenderBoundingBox() {
    return new net.minecraft.world.phys.AABB(getBlockPos()).expandTowards(0, -(tubeDepth + 2), 0);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    tag.putBoolean("active", activeState);
    tag.putInt("progress", progress);
    tag.putFloat("tubeDepth", tubeDepth);
    tag.putFloat("tubeTarget", tubeTarget);
    CompoundTag upgradesTag = new CompoundTag();
    upgrades.save(upgradesTag);
    tag.put("upgrades", upgradesTag);
    tag.putInt("runMode", runMode);
    CompoundTag tankTag = new CompoundTag();
    tank.save(tankTag);
    tag.put("tank", tankTag);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    activeState = tag.getBooleanOr("active", false);
    progress = tag.contains("progress") ? tag.getIntOr("progress", 0) : 0;
    tubeDepth = tag.getFloatOr("tubeDepth", 0.0F);
    tubeTarget = tag.getFloatOr("tubeTarget", 0.0F);
    if (tag.contains("upgrades")) {
      upgrades.load(tag.getCompoundOrEmpty("upgrades"));
    }
    runMode = tag.contains("runMode") ? tag.getIntOr("runMode", 0) : PipeExtractor.RUN_ALWAYS;
    if (tag.contains("tank")) {
      tank.load(tag.getCompoundOrEmpty("tank"));
    }
  }

  @Override
  public boolean canReceiveEnergyDir(@Nullable Direction side) {
    return side != Direction.DOWN;
  }

  @Override
  protected boolean syncEnergyToWorld() {
    return true;
  }
}
