package com.faktocraft.client.model;

import com.faktocraft.common.block.ModelFlags;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class FlagsBakedModel implements IDynamicBakedModel {

  public record Part(BakedModel model, IntPredicate condition) {
  }

  private final BakedModel original;
  private final List<Part> parts;

  public FlagsBakedModel(BakedModel original, List<Part> parts) {
    this.original = original;
    this.parts = List.copyOf(parts);
  }

  private static int mask(ModelData data) {
    Integer mask = data.get(ModelFlags.MASK);
    return mask != null ? mask : 0;
  }

  @NotNull
  @Override
  public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
      @NotNull ModelData data, @Nullable RenderType renderType) {
    List<BakedQuad> quads = original.getQuads(state, side, rand, data, renderType);
    if (state == null) {
      return quads;
    }
    int mask = mask(data);
    List<BakedQuad> out = new ArrayList<>(quads);
    for (Part part : parts) {
      if (!part.condition().test(mask)) {
        continue;
      }
      if (renderType == null || part.model().getRenderTypes(state, rand, data).contains(renderType)) {
        out.addAll(part.model().getQuads(state, side, rand, data, renderType));
      }
    }
    return out;
  }

  @Override
  public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
      @NotNull ModelData data) {
    ChunkRenderTypeSet types = original.getRenderTypes(state, rand, data);
    int mask = mask(data);
    for (Part part : parts) {
      if (part.condition().test(mask)) {
        types = ChunkRenderTypeSet.union(types, part.model().getRenderTypes(state, rand, data));
      }
    }
    return types;
  }

  @Override
  public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
    return original.getParticleIcon(data);
  }

  @Override
  public boolean useAmbientOcclusion() {
    return original.useAmbientOcclusion();
  }

  @Override
  public boolean isGui3d() {
    return original.isGui3d();
  }

  @Override
  public boolean usesBlockLight() {
    return original.usesBlockLight();
  }

  @Override
  public boolean isCustomRenderer() {
    return original.isCustomRenderer();
  }

  @Override
  public TextureAtlasSprite getParticleIcon() {
    return original.getParticleIcon(ModelData.EMPTY);
  }

  @Override
  public ItemOverrides getOverrides() {
    return original.getOverrides();
  }

  @Override
  @SuppressWarnings("deprecation")
  public ItemTransforms getTransforms() {
    return original.getTransforms();
  }
}
