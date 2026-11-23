package com.faktocraft.common.block;

import com.faktocraft.common.cover.CoverSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelDataManager;
import net.neoforged.neoforge.model.data.ModelProperty;

public final class ModelFlags {

  public static final ModelProperty<Integer> MASK = new ModelProperty<>();

  public interface Resolver {
    int resolve(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int pending);
  }

  private final String key;
  private final int all;
  private int mask;
  private int pending;
  private boolean dirty;
  private boolean quiet;

  public ModelFlags(String key, int all) {
    this.key = key;
    this.all = all;
    this.pending = all;
  }

  public int mask() {
    return mask;
  }

  public boolean has(int bit) {
    return (mask & bit) != 0;
  }

  public static boolean has(int mask, int bit) {
    return (mask & bit) != 0;
  }

  public static boolean readable(Level level, BlockPos pos) {
    return level.isOutsideBuildHeight(pos) || level.isLoaded(pos);
  }

  public ModelData modelData(ModelData base) {
    return mask == 0 ? base : base.derive().with(MASK, mask).build();
  }

  public void save(CompoundTag tag) {
    tag.putInt(key, mask);
  }

  public void load(CompoundTag tag) {
    if (tag.contains(key)) {
      mask = tag.getIntOr(key, 0) & all;
      pending = 0;
    }
  }

  public void onLevelSet(BlockEntity blockEntity) {
    Level level = blockEntity.getLevel();
    if (level != null && level.isClientSide()) {
      blockEntity.requestModelDataUpdate();
    }
  }

  public void onClientLoad(BlockEntity blockEntity) {
    Level level = blockEntity.getLevel();
    if (mask == 0 || level == null || !level.isClientSide()) {
      return;
    }
    ModelDataManager manager = level.getModelDataManager();
    if (manager == null || manager.getAt(blockEntity.getBlockPos()) == ModelData.EMPTY) {
      CoverSupport.refreshClientModel(blockEntity);
    }
  }

  public boolean takeDirty() {
    boolean was = dirty;
    dirty = false;
    return was;
  }

  public void set(BlockEntity blockEntity, int bit, boolean value) {
    pending &= ~bit;
    int next = value ? mask | bit : mask & ~bit;
    if (next == mask) {
      return;
    }
    mask = next;
    dirty = true;
    Level level = blockEntity.getLevel();
    if (level == null) {
      return;
    }
    if (level.isClientSide()) {
      CoverSupport.refreshClientModel(blockEntity);
    } else if (quiet) {
      blockEntity.setChanged();
    } else {
      CoverSupport.markChanged(blockEntity);
    }
  }

  public void resolveForSync(BlockEntity blockEntity, Resolver resolver) {
    quiet = true;
    try {
      resolve(blockEntity, resolver);
    } finally {
      quiet = false;
    }
  }

  public void resolve(BlockEntity blockEntity, Resolver resolver) {
    if (pending == 0) {
      return;
    }
    Level level = blockEntity.getLevel();
    if (level == null || level.isClientSide()) {
      return;
    }
    pending = resolver.resolve(blockEntity, level, blockEntity.getBlockPos(), blockEntity.getBlockState(), pending)
        & all;
    dirty = false;
  }
}
