package com.faktocraft.client.model;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.ConnectionExtensions;
import com.faktocraft.common.block.ModelFlags;
import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.block.impl.generators.solar_panels.BlockSolarGenerator;
import com.faktocraft.common.block.impl.generators.solar_panels.SolarPanelFlags;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

public final class FlagModels {

  private record Rotated(Identifier model, Variant.SimpleModelState rotation) {
  }

  private record Spec(Rotated model, IntPredicate condition) {
  }

  private static final Variant.SimpleModelState Y0 = Variant.SimpleModelState.DEFAULT;
  private static final Variant.SimpleModelState Y90 = Y0.withY(Quadrant.R90);
  private static final Variant.SimpleModelState Y180 = Y0.withY(Quadrant.R180);
  private static final Variant.SimpleModelState Y270 = Y0.withY(Quadrant.R270);
  private static final Variant.SimpleModelState X90 = Y0.withX(Quadrant.R90);
  private static final Variant.SimpleModelState X270 = Y0.withX(Quadrant.R270);
  private static final Direction[] HORIZONTAL = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
  private static final Variant.SimpleModelState[] HORIZONTAL_ROTATIONS = { Y0, Y90, Y180, Y270 };
  private static final Identifier RIM_EDGE = model("block/base/solar_panel_rim_edge");
  private static final Identifier RIM_CORNER = model("block/base/solar_panel_rim_corner");
  private static final Identifier RIM_CORNER_INNER = model("block/base/solar_panel_rim_corner_inner");
  private static final Identifier RIM_SHADOW = model("block/base/solar_panel_rim_shadow");
  private static final Identifier CONNECTOR = model("block/base/solar_panel_connector");

  private static Map<Block, List<Spec>> specs;
  private static Map<Rotated, StandaloneModelKey<BlockStateModelPart>> keys;

  private FlagModels() {
  }

  private static Identifier model(String path) {
    return Identifier.fromNamespaceAndPath(Faktocraft.MODID, path);
  }

  private static Identifier blockModel(Block block, String prefix, String suffix) {
    Identifier id = BuiltInRegistries.BLOCK.getKey(block);
    return Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
  }

  public static Identifier pipeExtension(Block block) {
    return blockModel(block, "block/pipe/", "_ext");
  }

  public static Identifier solarAdapter(Block block) {
    return blockModel(block, "block/generator/", "_adapter");
  }

  public static boolean hasFlags(Block block) {
    return block instanceof BlockSolarGenerator
        || block instanceof VoxelBlock voxel && voxel.hasConnectionExtensions();
  }

  private static synchronized Map<Block, List<Spec>> specs() {
    if (specs == null) {
      Map<Block, List<Spec>> out = new LinkedHashMap<>();
      Map<Rotated, StandaloneModelKey<BlockStateModelPart>> keyMap = new HashMap<>();
      for (Block block : BuiltInRegistries.BLOCK) {
        if (!hasFlags(block)) {
          continue;
        }
        List<Spec> list = block instanceof BlockSolarGenerator ? solarSpecs(block) : pipeSpecs(block);
        for (Spec spec : list) {
          keyMap.computeIfAbsent(spec.model(), rotated -> new StandaloneModelKey<>(
              () -> rotated.model() + "#" + rotated.rotation()));
        }
        out.put(block, list);
      }
      keys = keyMap;
      specs = out;
    }
    return specs;
  }

  public static void registerStandalone(ModelEvent.RegisterStandalone event) {
    specs();
    for (Map.Entry<Rotated, StandaloneModelKey<BlockStateModelPart>> entry : keys.entrySet()) {
      Rotated rotated = entry.getKey();
      event.register(entry.getValue(), SimpleUnbakedStandaloneModel.simpleModelWrapper(rotated.model(),
          rotated.rotation().asModelState()));
    }
  }

  public static Map<Block, List<FlagsBakedModel.Part>> parts(StandaloneModelLoader.BakedModels baked) {
    Map<Block, List<FlagsBakedModel.Part>> out = new HashMap<>();
    for (Map.Entry<Block, List<Spec>> entry : specs().entrySet()) {
      List<FlagsBakedModel.Part> parts = new ArrayList<>();
      for (Spec spec : entry.getValue()) {
        BlockStateModelPart part = baked.get(keys.get(spec.model()));
        if (part == null) {
          parts = null;
          break;
        }
        parts.add(new FlagsBakedModel.Part(part, spec.condition()));
      }
      if (parts != null) {
        out.put(entry.getKey(), parts);
      }
    }
    return out;
  }

  private static void add(List<Spec> out, Identifier location, Variant.SimpleModelState rotation,
      IntPredicate condition) {
    out.add(new Spec(new Rotated(location, rotation), condition));
  }

  private static List<Spec> pipeSpecs(Block block) {
    Identifier location = pipeExtension(block);
    List<Spec> out = new ArrayList<>();
    for (int i = 0; i < HORIZONTAL.length; i++) {
      Direction direction = HORIZONTAL[i];
      add(out, location, HORIZONTAL_ROTATIONS[i], mask -> ConnectionExtensions.has(mask, direction));
    }
    add(out, location, X270, mask -> ConnectionExtensions.has(mask, Direction.UP));
    add(out, location, X90, mask -> ConnectionExtensions.has(mask, Direction.DOWN));
    return out;
  }

  private static List<Spec> solarSpecs(Block block) {
    List<Spec> out = new ArrayList<>();
    IntPredicate[] missingPanel = new IntPredicate[HORIZONTAL.length];
    for (int i = 0; i < HORIZONTAL.length; i++) {
      int bit = SolarPanelFlags.panelBit(HORIZONTAL[i]);
      missingPanel[i] = mask -> !ModelFlags.has(mask, bit);
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      add(out, RIM_EDGE, HORIZONTAL_ROTATIONS[i], missingPanel[i]);
    }
    IntPredicate[] outerCorner = {
        missingPanel[0].or(missingPanel[3]), missingPanel[0].or(missingPanel[1]),
        missingPanel[2].or(missingPanel[1]), missingPanel[2].or(missingPanel[3]) };
    for (int i = 0; i < outerCorner.length; i++) {
      add(out, RIM_CORNER, HORIZONTAL_ROTATIONS[i], outerCorner[i]);
    }
    int[] innerCorner = { SolarPanelFlags.CORNER_NW, SolarPanelFlags.CORNER_NE, SolarPanelFlags.CORNER_SE,
        SolarPanelFlags.CORNER_SW };
    for (int i = 0; i < innerCorner.length; i++) {
      int bit = innerCorner[i];
      add(out, RIM_CORNER_INNER, HORIZONTAL_ROTATIONS[i], mask -> ModelFlags.has(mask, bit));
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      add(out, RIM_SHADOW, HORIZONTAL_ROTATIONS[i], missingPanel[i]);
    }
    for (int i = 0; i < HORIZONTAL.length; i++) {
      int bit = SolarPanelFlags.cableBit(HORIZONTAL[i]);
      add(out, CONNECTOR, HORIZONTAL_ROTATIONS[i], mask -> ModelFlags.has(mask, bit));
    }
    int downBit = SolarPanelFlags.cableBit(Direction.DOWN);
    add(out, solarAdapter(block), X90, mask -> ModelFlags.has(mask, downBit));
    return out;
  }
}
