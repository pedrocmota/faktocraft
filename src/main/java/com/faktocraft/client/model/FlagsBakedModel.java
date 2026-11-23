package com.faktocraft.client.model;

import com.faktocraft.common.block.ModelFlags;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.IntPredicate;

public class FlagsBakedModel implements BlockStateModel {

  public record Part(BlockStateModelPart model, IntPredicate condition) {
  }

  private final BlockStateModel original;
  private final List<Part> parts;

  public FlagsBakedModel(BlockStateModel original, List<Part> parts) {
    this.original = original;
    this.parts = List.copyOf(parts);
  }

  private static int mask(ModelData data) {
    Integer mask = data.get(ModelFlags.MASK);
    return mask != null ? mask : 0;
  }

  @Override
  @Deprecated
  public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
    original.collectParts(random, output);
  }

  @Override
  public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
      List<BlockStateModelPart> output) {
    original.collectParts(level, pos, state, random, output);
    int mask = mask(level.getModelData(pos));
    for (Part part : parts) {
      if (part.condition().test(mask)) {
        output.add(part.model());
      }
    }
  }

  @Override
  @Deprecated
  public Material.Baked particleMaterial() {
    return original.particleMaterial();
  }

  @Override
  public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    return original.particleMaterial(level, pos, state);
  }

  @Override
  @Deprecated
  public int materialFlags() {
    return original.materialFlags();
  }

  @Override
  public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
    int flags = original.materialFlags(level, pos, state);
    int mask = mask(level.getModelData(pos));
    for (Part part : parts) {
      if (part.condition().test(mask)) {
        flags |= part.model().materialFlags();
      }
    }
    return flags;
  }

  @Override
  @Nullable
  public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
    return mask(level.getModelData(pos)) == 0 ? original.createGeometryKey(level, pos, state, random) : null;
  }
}
