package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.util.transfer.CapabilityBlockEntity;
import com.faktocraft.common.block.ISupportHost;
import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.entity.block.FluidStorage;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.common.util.Constants;
import com.faktocraft.common.util.TransferUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import com.faktocraft.common.util.transfer.Capability;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.LazyOptional;
import com.faktocraft.common.util.transfer.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockEntityFluidPipe extends CapabilityBlockEntity
    implements IValveHolder, ISupportHost, com.faktocraft.common.cover.ICoverHost,
    com.faktocraft.common.block.IConnectionExtensionHost {

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
    if (cover != previousCover || coverHoles != previousHoles || extensions.mask() != previousExtensions) {
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
  }

  public final FluidStorage tank;
  private boolean dirtySync;

  private final LazyOptional<IFluidHandler> tankCap;

  public BlockEntityFluidPipe(BlockPos pos, BlockState state) {
    this(PipeRegistry.FLUID_PIPE_BLOCK_ENTITY, pos, state);
  }

  protected BlockEntityFluidPipe(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
      BlockPos pos, BlockState state) {
    super(type, pos, state);
    this.tank = new FluidStorage(getTier().capacityMb);
    this.tank.setChangeListener(() -> {
      setChanged();
      dirtySync = true;
    });
    this.tankCap = LazyOptional.of(() -> guardedTank);
  }

  private final IFluidHandler guardedTank = new IFluidHandler() {
    @Override
    public int getTanks() {
      return tank.getTanks();
    }

    @Override
    public @NotNull net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int index) {
      return tank.getFluidInTank(index);
    }

    @Override
    public int getTankCapacity(int index) {
      return tank.getTankCapacity(index);
    }

    @Override
    public boolean isFluidValid(int index, @NotNull net.neoforged.neoforge.fluids.FluidStack stack) {
      return tank.isFluidValid(index, stack);
    }

    @Override
    public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
      return valveClosed() ? 0 : tank.fill(resource, action);
    }

    @Override
    public @NotNull net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource,
        FluidAction action) {
      return valveClosed() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : tank.drain(resource, action);
    }

    @Override
    public @NotNull net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
      return valveClosed() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : tank.drain(maxDrain, action);
    }
  };

  public BlockFluidPipe.Tier getTier() {
    return getBlockState().getBlock() instanceof BlockFluidPipe pipe ? pipe.getTier() : BlockFluidPipe.Tier.STONE;
  }

  private PipeValve valve = PipeValve.NONE;

  @Override
  public PipeValve getValve() {
    return valve;
  }

  @Override
  public void setValve(PipeValve valve) {
    this.valve = valve;
    setChanged();
    if (level != null && !level.isClientSide()) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  private boolean valveRedstoneOnly = false;

  @Override
  public boolean isValveRedstoneOnly() {
    return valveRedstoneOnly;
  }

  @Override
  public void setValveRedstoneOnly(boolean redstoneOnly) {
    this.valveRedstoneOnly = redstoneOnly;
    setChanged();
    if (level != null && !level.isClientSide()) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  public boolean valveClosed() {
    return valve.isClosed();
  }

  private void tickValveActuator() {
    if (!valveRedstoneOnly || !valve.isPresent() || level == null || level.isClientSide()) {
      return;
    }
    boolean desired = level.hasNeighborSignal(worldPosition);
    if (valve.isOpen() != desired) {
      setValve(PipeValve.of(valve.direction(), desired));
      level.playSound(null, worldPosition, com.faktocraft.common.registries.ModSounds.VALVE_WHEEL,
          net.minecraft.sounds.SoundSource.BLOCKS, 0.45F, desired ? 1.0F : 0.94F);
    }
  }

  @Nullable
  private Direction lastFillSide;

  @Nullable
  public Direction getLastFillSide() {
    return lastFillSide;
  }

  @Nullable
  private Direction inflowSide;

  public void noteFillFrom(Direction from) {
    inflowSide = from;
    if (valve.isPresent() && lastFillSide != from) {
      lastFillSide = from;
      dirtySync = true;
      setChanged();
    }
  }

  public void tick() {
    if (level == null) {
      return;
    }
    extensions.resolve(this);
    if (dirtySync && level.getGameTime() % 10 == 0) {
      dirtySync = false;
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    if (!level.isClientSide() && level.getGameTime() % 20 == 0 && valve.isPresent()) {
      PipeValveHelper.validateOrEject(level, worldPosition, getBlockState(), this);
    }
    tickValveActuator();
    if (valveClosed() || tank.isEmpty()) {
      settledMb = tank.getFluidAmount();
      inflowSide = null;
      return;
    }
    int rate = getTier().ratePerTickMb;
    boolean wood = getTier() == BlockFluidPipe.Tier.EXTRACTOR;

    int budget = Math.min(rate, Math.min(settledMb, tank.getFluidAmount()));
    int terminalLeft = budget;
    for (Direction direction : Constants.DIRECTIONS) {
      if (terminalLeft <= 0 || tank.isEmpty()) {
        break;
      }
      if (!getBlockState().getValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))) {
        continue;
      }
      if (wood && direction == sourceSide()) {
        continue;
      }
      BlockPos neighborPos = worldPosition.relative(direction);
      if (level.getBlockEntity(neighborPos) instanceof BlockEntityFluidPipe) {
        continue;
      }
      IFluidHandler target = neighborFluidCache.get(direction);
      if (target != null) {
        int moved = TransferUtil.moveFluid(tank, target, terminalLeft);
        terminalLeft -= moved;
        budget -= moved;
      }
    }

    for (Direction direction : Constants.DIRECTIONS) {
      if (budget <= 0 || tank.isEmpty()) {
        break;
      }
      if (direction == inflowSide) {
        continue;
      }
      if (!getBlockState().getValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))) {
        continue;
      }
      BlockPos neighborPos = worldPosition.relative(direction);

      if (level.getBlockEntity(neighborPos) instanceof BlockEntityFluidPipe neighborPipe) {
        if (neighborPipe.valveClosed()) {
          continue;
        }
        if (neighborPipe.tank.isEmpty()
            || (com.faktocraft.common.util.FluidStackCompat.isFluidEqual(neighborPipe.tank.getFluidStack(),
                tank.getFluidStack())
                && neighborPipe.tank.getFluidAmount() < tank.getFluidAmount())) {
          int difference = tank.getFluidAmount() - neighborPipe.tank.getFluidAmount();
          int move = Math.min(budget, Math.max(1, difference / 2));
          int accepted = neighborPipe.tank.fillFluid(tank.getFluidStack(),
              Math.min(move, tank.getFluidAmount()), false);
          if (accepted > 0) {
            tank.takeFluid(accepted, false);
            budget -= accepted;
            neighborPipe.noteFillFrom(direction.getOpposite());
          }
        }
      }
    }
    settledMb = tank.getFluidAmount();
    if (tank.isEmpty()) {
      inflowSide = null;
    }
  }

  private final com.faktocraft.common.util.NeighborFluidCache neighborFluidCache =
      new com.faktocraft.common.util.NeighborFluidCache(
          this);

  private int settledMb;

  @Nullable
  private Direction sourceSide() {
    BlockState state = getBlockState();
    return state.hasProperty(BlockFluidExtractorPipe.SOURCE) ? state.getValue(BlockFluidExtractorPipe.SOURCE) : null;
  }

  public boolean extractPulse(int amountMb) {
    Direction direction = sourceSide();
    if (getTier() != BlockFluidPipe.Tier.EXTRACTOR || level == null || direction == null
        || !getBlockState().getValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction))) {
      return false;
    }
    BlockPos neighborPos = worldPosition.relative(direction);
    if (level.getBlockEntity(neighborPos) instanceof BlockEntityFluidPipe) {
      return false;
    }
    IFluidHandler source = TransferUtil.findFluidHandler(level, neighborPos, direction.getOpposite());
    return source != null && TransferUtil.moveFluid(source, tank, amountMb) > 0;
  }

  @NotNull
  @Override
  public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
      return tankCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  public void setRemoved() {
    com.faktocraft.common.cover.CoverSupport.onClientRemoved(this, this);
    super.setRemoved();
    tankCap.invalidate();
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putString("valve", valve.getSerializedName());
    tag.putBoolean("valveRedstoneOnly", valveRedstoneOnly);
    tag.putInt("valveFill", lastFillSide != null ? lastFillSide.get3DDataValue() : -1);
    CompoundTag tankTag = new CompoundTag();
    tank.save(tankTag);
    tag.put("tank", tankTag);
    com.faktocraft.common.cover.CoverSupport.save(tag, cover, coverHoles);
    extensions.save(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    valve = PipeValve.byName(tag.getStringOr("valve", ""));
    valveRedstoneOnly = tag.getBooleanOr("valveRedstoneOnly", false);
    int fillSide = tag.contains("valveFill") ? tag.getIntOr("valveFill", 0) : -1;
    lastFillSide = fillSide >= 0 ? Direction.from3DDataValue(fillSide) : null;
    if (tag.contains("tank")) {
      tank.load(tag.getCompoundOrEmpty("tank"));
    }
    settledMb = tank.getFluidAmount();
    cover = com.faktocraft.common.cover.CoverSupport.load(tag);
    coverHoles = com.faktocraft.common.cover.CoverSupport.loadHoles(tag);
    extensions.load(tag);
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag tag = new CompoundTag();
    extensions.resolveForSync(this);
    saveAdditional(tag);
    return tag;
  }

  @Nullable
  @Override
  public net.minecraft.network.protocol.Packet<
      net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
    return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
  }

  private net.minecraft.core.Direction supportDirection;

  private long supportCheckedAt = -SUPPORT_REFRESH_TICKS;

  @Override
  @org.jetbrains.annotations.Nullable
  public net.minecraft.core.Direction supportDirection() {
    if (level == null) {
      return null;
    }
    long now = level.getGameTime();
    if (now - supportCheckedAt >= SUPPORT_REFRESH_TICKS) {
      supportCheckedAt = now - Math.floorMod(worldPosition.hashCode(), SUPPORT_REFRESH_TICKS);
      supportDirection = com.faktocraft.common.block.PipeSupport.directionFor(level, worldPosition, getBlockState());
    }
    return supportDirection;
  }

}
