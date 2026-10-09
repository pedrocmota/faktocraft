package com.faktocraft.common.block.impl.generators.wind_generator;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.energy.WindSim;
import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.GuiSlotType;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.interfaces.entity.ITileSound;
import com.faktocraft.common.item.impl.tools.ItemWindRotor;
import com.faktocraft.common.registries.ModSounds;
import com.faktocraft.common.registries.machines.M1Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;

public class BlockEntityWindGenerator extends FaktocraftBlockEntity implements IEnergyBlock, ITileSound {
  public static final int ROTOR_SLOT = 0;
  public static final int MIN_GENERATOR_DISTANCE = 18;
  private static final double CROWD_FACTOR_PER_NEIGHBOR = 0.35;
  public static final int ROTOR_CLEARANCE_MARGIN = 6;

  private static final int OBSTRUCTION_SCAN_INTERVAL = 64;
  private static final double OBSTRUCTION_PENALTY = 0.05;

  private int lastAmount = 0;
  public int amount = 0;
  public int windPercent = 0;
  public int crowdCount = 0;
  public boolean rotorBlocked = false;
  public static final int ROTOR_SWEEP_RADIUS = 3;
  private double obstructionFactor = 1.0;
  private double crowdFactor = 1.0;
  private long nextObstructionScan = Long.MIN_VALUE;

  public float rotorAngle = 0.0F;
  public double rotorLastTime = Double.NaN;

  public BlockEntityWindGenerator(BlockPos pos, BlockState state) {
    super(M1Registry.WIND_GENERATOR_BE, pos, state);
    createEnergyStorage(0, ModConfig.server().wind_generator_energy_capacity, EnergyType.EXTRACT, EnergyTier.LOW);
  }

  @Override
  public ArrayList<FaktocraftSlot> addInventorySlot(ArrayList<FaktocraftSlot> slots) {
    slots.add(new FaktocraftSlot(ROTOR_SLOT, 35, 36, InventorySlotType.INPUT, GuiSlotType.NORMAL, 34, 35));
    return super.addInventorySlot(slots);
  }

  @Override
  public boolean isItemValidForSlot(int slot, ItemStack stack) {
    return slot == ROTOR_SLOT && stack.getItem() instanceof ItemWindRotor;
  }

  @Override
  public int getCustomSlotLimit(int slot) {
    return 1;
  }

  @Override
  public boolean inputSlotChanged(int slotId, ItemStack oldStack, ItemStack newStack) {
    updateBlockState();
    return false;
  }

  public ItemStack getRotorStack() {
    return hasInventory() ? getItemStackHandler().getStackInSlot(ROTOR_SLOT) : ItemStack.EMPTY;
  }

  @Override
  public void tickWork(BlockState state) {
    boolean active = false;
    amount = 0;
    getEnergyStorage().updateGenerated(0);

    if (level instanceof ServerLevel serverLevel) {
      long gameTime = serverLevel.getGameTime();
      if (gameTime >= nextObstructionScan) {
        obstructionFactor = scanObstructions();
        int neighbors = scanCrowding();
        crowdFactor = Math.pow(CROWD_FACTOR_PER_NEIGHBOR, neighbors);
        boolean blocked = scanRotorClearance();
        if (neighbors != crowdCount || blocked != rotorBlocked) {
          crowdCount = neighbors;
          rotorBlocked = blocked;
          super.updateBlockState();
        }
        nextObstructionScan = gameTime + OBSTRUCTION_SCAN_INTERVAL;
      }

      double rotorFactor = rotorBlocked ? 0.0 : ItemWindRotor.factorOf(getRotorStack());
      double wind = WindSim.getEffectiveStrength(serverLevel);
      windPercent = (int) Math.round(wind * 100.0);
      double output = ModConfig.server().wind_generator_max_tick_generate
          * wind * WindSim.heightFactor(getBlockPos().getY()) * obstructionFactor
          * rotorFactor * crowdFactor;
      amount = (int) Math.round(output);
    }

    if (amount > 0) {
      active = true;
      if (getEnergyStorage().generateEnergy(amount, true) == amount) {
        getEnergyStorage().generateEnergy(amount, false);
        getEnergyStorage().updateGenerated(amount);
      }
    }

    if (amount != lastAmount) {
      lastAmount = amount;
      super.updateBlockState();
    }

    boolean wasActive = activeState;
    setActive(active);
    if (wasActive != active) {
      super.updateBlockState();
    }

    if (level instanceof ServerLevel serverLevel && !getRotorStack().isEmpty()) {
      tickRotorCollision(serverLevel);
    }
  }

  private void tickRotorCollision(ServerLevel serverLevel) {
    Direction facing = getBlockState().getValue(
        com.faktocraft.common.util.BlockStateHelper.horizontalFacingProperty);
    net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(getBlockPos())
        .add(facing.getStepX() * 1.25, 0, facing.getStepZ() * 1.25);
    boolean alongZ = facing.getAxis() == Direction.Axis.Z;
    AABB slab = new AABB(
        center.x - (alongZ ? ROTOR_SWEEP_RADIUS : 0.4), center.y - ROTOR_SWEEP_RADIUS,
        center.z - (alongZ ? 0.4 : ROTOR_SWEEP_RADIUS),
        center.x + (alongZ ? ROTOR_SWEEP_RADIUS : 0.4), center.y + ROTOR_SWEEP_RADIUS,
        center.z + (alongZ ? 0.4 : ROTOR_SWEEP_RADIUS));
    boolean spinning = amount > 0 && !rotorBlocked;
    for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities(null, slab)) {
      net.minecraft.world.phys.Vec3 delta = entity.position()
          .add(0, entity.getBbHeight() * 0.5, 0).subtract(center);
      double planarU = alongZ ? delta.x : delta.z;
      if (planarU * planarU + delta.y * delta.y > (ROTOR_SWEEP_RADIUS + 0.5) * (ROTOR_SWEEP_RADIUS + 0.5)) {
        continue;
      }
      double axial = alongZ ? delta.z : delta.x;
      double push = (axial >= 0 ? 1 : -1) * 0.35;
      entity.setDeltaMovement(entity.getDeltaMovement()
          .add(alongZ ? 0 : push, 0, alongZ ? push : 0));
      entity.syncVelocity = true;
      if (spinning && entity instanceof net.minecraft.world.entity.LivingEntity living) {
        living.hurtServer(serverLevel, com.faktocraft.common.registries.ModDamageTypes.rotor(serverLevel), 4.0F);
      }
    }
  }

  private double scanObstructions() {
    if (level == null) {
      return 1.0;
    }
    int obstructions = 0;
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    for (int dx = -3; dx <= 3; dx++) {
      for (int dz = -3; dz <= 3; dz++) {
        if (dx == 0 && dz == 0) {
          continue;
        }
        for (int dy = -1; dy <= 1; dy++) {
          cursor.setWithOffset(getBlockPos(), dx, dy, dz);
          if (!level.getBlockState(cursor).isAir()) {
            obstructions++;
          }
        }
      }
    }
    return Math.max(0.0, 1.0 - obstructions * OBSTRUCTION_PENALTY);
  }

  private boolean scanRotorClearance() {
    if (level == null) {
      return false;
    }
    Direction facing = getBlockState().getValue(
        com.faktocraft.common.util.BlockStateHelper.horizontalFacingProperty);
    Direction side = facing.getClockWise();
    BlockPos hub = getBlockPos().relative(facing);
    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    int radius = ROTOR_SWEEP_RADIUS + ROTOR_CLEARANCE_MARGIN;
    for (int u = -radius; u <= radius; u++) {
      for (int v = -radius; v <= radius; v++) {
        if (u * u + v * v > radius * radius + 1) {
          continue;
        }
        cursor.setWithOffset(hub, side.getStepX() * u, v, side.getStepZ() * u);
        if (!level.getBlockState(cursor).isAir()) {
          return true;
        }
      }
    }
    return false;
  }

  private int scanCrowding() {
    if (level == null) {
      return 0;
    }
    return com.faktocraft.common.energy.WindFarmRegistry.countNear(level, getBlockPos(), MIN_GENERATOR_DISTANCE);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide()) {
      com.faktocraft.common.energy.WindFarmRegistry.add(level, getBlockPos());
    }
  }

  @Override
  public void setRemoved() {
    if (level != null && !level.isClientSide()) {
      com.faktocraft.common.energy.WindFarmRegistry.remove(level, getBlockPos());
    }
    super.setRemoved();
  }

  @Override
  public void onChunkUnloaded() {
    if (level != null && !level.isClientSide()) {
      com.faktocraft.common.energy.WindFarmRegistry.remove(level, getBlockPos());
    }
    super.onChunkUnloaded();
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    tag.putBoolean("active", activeState);
    tag.putInt("amount", amount);
    tag.putInt("lastAmount", lastAmount);
    tag.putInt("windPercent", windPercent);
    tag.putInt("crowdCount", crowdCount);
    tag.putBoolean("rotorBlocked", rotorBlocked);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    activeState = tag.getBooleanOr("active", false);
    amount = tag.contains("amount") ? tag.getIntOr("amount", 0) : 0;
    lastAmount = tag.contains("lastAmount") ? tag.getIntOr("lastAmount", 0) : 0;
    windPercent = tag.contains("windPercent") ? tag.getIntOr("windPercent", 0) : 0;
    crowdCount = tag.contains("crowdCount") ? tag.getIntOr("crowdCount", 0) : 0;
    rotorBlocked = tag.getBooleanOr("rotorBlocked", false);
  }

  public AABB getRenderBoundingBox() {
    return new AABB(getBlockPos()).inflate(4.5);
  }

  @Override
  public SoundEvent getSoundEvent() {
    return ModSounds.WIND_GENERATOR;
  }

  @Override
  public int defaultGeneratorPriority() {
    return com.faktocraft.common.config.ModConfig.server().priority_wind_generator;
  }

  @Override
  public float getVolume() {
    return 0.9F;
  }

  @Override
  public boolean canExtractEnergyDir(@Nullable Direction side) {
    if (side == null) {
      return true;
    }
    Direction facing = getBlockState().getValue(
        com.faktocraft.common.util.BlockStateHelper.horizontalFacingProperty);
    return side != facing;
  }

  @Override
  public ArrayList<com.faktocraft.common.interfaces.entity.IElectricSlot> addBatterySlot(
      ArrayList<com.faktocraft.common.interfaces.entity.IElectricSlot> slots) {
    slots.add(new com.faktocraft.common.entity.slot.SlotBattery(0, 152, 62, true));
    return super.addBatterySlot(slots);
  }

  @Override
  protected boolean syncEnergyToWorld() {
    return true;
  }
}
