package com.faktocraft.client.model;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.ConnectionExtensions;
import com.faktocraft.common.block.ModelFlags;
import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.block.impl.generators.solar_panels.BlockSolarGenerator;
import com.faktocraft.common.block.impl.generators.solar_panels.SolarPanelFlags;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;

public final class FlagModels {

  private static final BlockModelRotation Y0 = BlockModelRotation.by(0, 0);
  private static final BlockModelRotation Y90 = BlockModelRotation.by(0, 90);
  private static final BlockModelRotation Y180 = BlockModelRotation.by(0, 180);
  private static final BlockModelRotation Y270 = BlockModelRotation.by(0, 270);
  private static final BlockModelRotation X90 = BlockModelRotation.by(90, 0);
  private static final BlockModelRotation X270 = BlockModelRotation.by(-90, 0);
  private static final Direction[] HORIZONTAL = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
  private static final BlockModelRotation[] HORIZONTAL_ROTATIONS = { Y0, Y90, Y180, Y270 };
  private static final ResourceLocation RIM_EDGE = model("block/base/solar_panel_rim_edge");
  private static final ResourceLocation RIM_CORNER = model("block/base/solar_panel_rim_corner");
  private static final ResourceLocation RIM_CORNER_INNER = model("block/base/solar_panel_rim_corner_inner");
  private static final ResourceLocation RIM_SHADOW = model("block/base/solar_panel_rim_shadow");
  private static final ResourceLocation CONNECTOR = model("block/base/solar_panel_connector");

  private final ModelBakery bakery;
  private final Map<ResourceLocation, BakedModel> models;
  private final Map<String, BakedModel> rotated = new HashMap<>();
  private final Map<Block, List<FlagsBakedModel.Part>> parts = new HashMap<>();

  public FlagModels(ModelBakery bakery, Map<ResourceLocation, BakedModel> models) {
    this.bakery = bakery;
    this.models = models;
  }

  private static ResourceLocation model(String path) {
    return new ResourceLocation(Faktocraft.MODID, path);
  }

  private static ResourceLocation blockModel(Block block, String prefix, String suffix) {
    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
    return new ResourceLocation(id.getNamespace(), prefix + id.getPath() + suffix);
  }

  public static ResourceLocation pipeExtension(Block block) {
    return blockModel(block, "block/pipe/", "_ext");
  }

  public static ResourceLocation solarAdapter(Block block) {
    return blockModel(block, "block/generator/", "_adapter");
  }

  public static boolean hasFlags(Block block) {
    return block instanceof BlockSolarGenerator
        || block instanceof VoxelBlock voxel && voxel.hasConnectionExtensions();
  }

  public static List<ResourceLocation> additionalModels() {
    List<ResourceLocation> out = new ArrayList<>(List.of(RIM_EDGE, RIM_CORNER, RIM_CORNER_INNER, RIM_SHADOW,
        CONNECTOR));
    for (Block block : ForgeRegistries.BLOCKS.getValues()) {
      if (block instanceof BlockSolarGenerator) {
        out.add(solarAdapter(block));
      } else if (block instanceof VoxelBlock voxel && voxel.hasConnectionExtensions()) {
        out.add(pipeExtension(block));
      }
    }
    return out;
  }

  @Nullable
  public List<FlagsBakedModel.Part> partsFor(Block block) {
    if (!hasFlags(block)) {
      return null;
    }
    return parts.computeIfAbsent(block, b -> b instanceof BlockSolarGenerator ? solarParts(b) : pipeParts(b));
  }

  @Nullable
  private List<FlagsBakedModel.Part> pipeParts(Block block) {
    ResourceLocation location = pipeExtension(block);
    List<FlagsBakedModel.Part> out = new ArrayList<>();
    for (int i = 0; i < HORIZONTAL.length; i++) {
      Direction direction = HORIZONTAL[i];
      if (!add(out, location, HORIZONTAL_ROTATIONS[i], mask -> ConnectionExtensions.has(mask, direction))) {
        return null;
      }
    }
    if (!add(out, location, X270, mask -> ConnectionExtensions.has(mask, Direction.UP))
        || !add(out, location, X90, mask -> ConnectionExtensions.has(mask, Direction.DOWN))) {
      return null;
    }
    return out;
  }

  @Nullable
  private List<FlagsBakedModel.Part> solarParts(Block block) {
    List<FlagsBakedModel.Part> out = new ArrayList<>();
    IntPredicate[] missingPanel = new IntPredicate[HORIZONTAL.length];
    for (int i = 0; i < HORIZONTAL.length; i++) {
      int bit = SolarPanelFlags.panelBit(HORIZONTAL[i]);
      missingPanel[i] = mask -> !ModelFlags.has(mask, bit);
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      if (!add(out, RIM_EDGE, HORIZONTAL_ROTATIONS[i], missingPanel[i])) {
        return null;
      }
    }
    IntPredicate[] outerCorner = {
        missingPanel[0].or(missingPanel[3]), missingPanel[0].or(missingPanel[1]),
        missingPanel[2].or(missingPanel[1]), missingPanel[2].or(missingPanel[3]) };
    for (int i = 0; i < outerCorner.length; i++) {
      if (!add(out, RIM_CORNER, HORIZONTAL_ROTATIONS[i], outerCorner[i])) {
        return null;
      }
    }
    int[] innerCorner = { SolarPanelFlags.CORNER_NW, SolarPanelFlags.CORNER_NE, SolarPanelFlags.CORNER_SE,
        SolarPanelFlags.CORNER_SW };
    for (int i = 0; i < innerCorner.length; i++) {
      int bit = innerCorner[i];
      if (!add(out, RIM_CORNER_INNER, HORIZONTAL_ROTATIONS[i], mask -> ModelFlags.has(mask, bit))) {
        return null;
      }
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      if (!add(out, RIM_SHADOW, HORIZONTAL_ROTATIONS[i], missingPanel[i])) {
        return null;
      }
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      int bit = SolarPanelFlags.cableBit(HORIZONTAL[i]);
      if (!add(out, CONNECTOR, HORIZONTAL_ROTATIONS[i], mask -> ModelFlags.has(mask, bit))) {
        return null;
      }
    }
    int downBit = SolarPanelFlags.cableBit(Direction.DOWN);
    if (!add(out, solarAdapter(block), X90, mask -> ModelFlags.has(mask, downBit))) {
      return null;
    }
    return out;
  }

  private boolean add(List<FlagsBakedModel.Part> out, ResourceLocation location, BlockModelRotation rotation,
      IntPredicate condition) {
    BakedModel model = rotated(location, rotation);
    if (model == null) {
      return false;
    }
    out.add(new FlagsBakedModel.Part(model, condition));
    return true;
  }

  @Nullable
  private BakedModel rotated(ResourceLocation location, BlockModelRotation rotation) {
    String cacheKey = location + "#" + rotation.name();
    if (rotated.containsKey(cacheKey)) {
      return rotated.get(cacheKey);
    }
    BakedModel model = bake(location, rotation);
    rotated.put(cacheKey, model);
    return model;
  }

  @Nullable
  private BakedModel bake(ResourceLocation location, BlockModelRotation rotation) {
    BakedModel flat = models.get(location);
    if (flat == null) {
      return null;
    }
    Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
    RandomSource rand = RandomSource.create(0L);
    for (Direction side : Direction.values()) {
      for (BakedQuad quad : flat.getQuads(null, side, rand, ModelData.EMPTY, null)) {
        sprites.put(quad.getSprite().contents().name(), quad.getSprite());
      }
    }
    for (BakedQuad quad : flat.getQuads(null, null, rand, ModelData.EMPTY, null)) {
      sprites.put(quad.getSprite().contents().name(), quad.getSprite());
    }
    TextureAtlasSprite particle = flat.getParticleIcon(ModelData.EMPTY);
    Function<Material, TextureAtlasSprite> spriteGetter = material -> sprites.getOrDefault(material.texture(),
        particle);
    ModelBaker baker = new ModelBaker() {
      @Override
      public UnbakedModel getModel(ResourceLocation modelLocation) {
        return bakery.getModel(modelLocation);
      }

      @Nullable
      @Override
      public BakedModel bake(ResourceLocation modelLocation, ModelState state) {
        return models.get(modelLocation);
      }

      @Nullable
      @Override
      public BakedModel bake(ResourceLocation modelLocation, ModelState state,
          Function<Material, TextureAtlasSprite> textureGetter) {
        return models.get(modelLocation);
      }

      @Override
      public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
        return spriteGetter;
      }
    };
    return bakery.getModel(location).bake(baker, spriteGetter, rotation, location);
  }
}
