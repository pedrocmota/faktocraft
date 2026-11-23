package com.faktocraft.common.block;

import com.faktocraft.common.cover.CoverSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;

public final class ConnectionExtensions {

  private static final int ALL = 0x3F;

  private final ModelFlags flags = new ModelFlags("extensions", ALL);

  public int mask() {
    return flags.mask();
  }

  public static boolean has(int mask, Direction direction) {
    return ModelFlags.has(mask, CoverSupport.bit(direction));
  }

  public ModelData modelData(ModelData base) {
    return flags.modelData(base);
  }

  public void save(CompoundTag tag) {
    flags.save(tag);
  }

  public void load(CompoundTag tag) {
    flags.load(tag);
  }

  public void onLevelSet(BlockEntity blockEntity) {
    flags.onLevelSet(blockEntity);
  }

  public void onClientLoad(BlockEntity blockEntity) {
    flags.onClientLoad(blockEntity);
  }

  public boolean takeDirty() {
    return flags.takeDirty();
  }

  public void update(BlockEntity blockEntity, Direction direction, boolean extended) {
    flags.set(blockEntity, CoverSupport.bit(direction), extended);
  }

  public void resolve(BlockEntity blockEntity) {
    flags.resolve(blockEntity, this::resolvePending);
  }

  public void resolveForSync(BlockEntity blockEntity) {
    flags.resolveForSync(blockEntity, this::resolvePending);
  }

  private int resolvePending(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int pending) {
    if (!(state.getBlock() instanceof VoxelBlock block) || !block.hasConnectionExtensions()) {
      return 0;
    }
    int remaining = pending;
    for (Direction direction : Direction.values()) {
      int bit = CoverSupport.bit(direction);
      if (!ModelFlags.has(pending, bit) || !ModelFlags.readable(level, pos.relative(direction))) {
        continue;
      }
      boolean connected = state.getValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(direction));
      flags.set(blockEntity, bit, block.extendsInto(level, pos, direction, connected));
      remaining &= ~bit;
    }
    return remaining;
  }
}
