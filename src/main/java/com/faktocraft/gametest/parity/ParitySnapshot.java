package com.faktocraft.gametest.parity;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.machines.nuclear_reactor.BlockNuclearReactor;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.item.base.FluidItem;
import com.faktocraft.gametest.gui.GuiProbes;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public final class ParitySnapshot {

  public static final String VERSION = "1.20.1";
  public static final String[] CATEGORIES = { "universe", "items", "blocks", "tags", "fuel", "drops", "crafting",
      "machine_recipes", "menus", "sides", "states", "tabs", "tooltips", "fluids", "roundtrip", "process",
      "interact" };

  private static final String[] SIDE_NAMES = { "none", "down", "up", "north", "south", "west", "east" };
  private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
      Direction.WEST, Direction.EAST };
  private static final int SAMPLE_EVERY = 40;
  private static final int DROP_ROLLS = 2000;
  private static final int DROP_PROBE_ROLLS = 32;
  private static final String[] TOOL_TARGETS = { "minecraft:stone", "minecraft:deepslate", "minecraft:iron_ore",
      "minecraft:obsidian", "minecraft:oak_log", "minecraft:oak_planks", "minecraft:dirt", "minecraft:sand",
      "minecraft:gravel", "minecraft:cobweb", "minecraft:oak_leaves", "minecraft:white_wool", "minecraft:glass",
      "faktocraft:tin_ore", "faktocraft:lead_ore", "faktocraft:uranium_ore", "faktocraft:iridium_ore",
      "faktocraft:generator", "faktocraft:rubber_log" };
  private static final Set<String> SKIPPED_FIELDS = Set.of("id", "group", "category", "showNotification",
      "placementInfo", "commonInfo", "bookInfo", "notification");
  private static final AbstractContainerMenu CRAFTING_MENU = new AbstractContainerMenu(null, -1) {
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }
  };

  private final GameTestHelper helper;
  private final ServerLevel level;
  private final BlockPos rel;
  private final BlockPos abs;
  private final Player player;

  public ParitySnapshot(GameTestHelper helper, BlockPos rel) {
    this.helper = helper;
    this.level = helper.getLevel();
    this.rel = rel;
    this.abs = helper.absolutePos(rel);
    this.player = helper.makeMockPlayer();
  }

  public JsonObject category(String name) {
    return switch (name) {
      case "universe" -> universe();
      case "items" -> items();
      case "blocks" -> blocks();
      case "tags" -> tags();
      case "fuel" -> fuel();
      case "drops" -> drops();
      case "crafting" -> crafting();
      case "machine_recipes" -> machineRecipes();
      case "menus" -> menus();
      case "sides" -> sides();
      default -> new ParityBehavior(helper, rel, player).category(name);
    };
  }

  static String itemId(Item item) {
    return BuiltInRegistries.ITEM.getKey(item).toString();
  }

  static String blockId(Block block) {
    return BuiltInRegistries.BLOCK.getKey(block).toString();
  }

  static String fluidId(Fluid fluid) {
    return BuiltInRegistries.FLUID.getKey(fluid).toString();
  }

  static String keyId(ResourceKey<?> key) {
    return key.location().toString();
  }

  static String enchantId(Enchantment enchantment) {
    return String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(enchantment));
  }

  static double round(double value) {
    return Math.round(value * 10000.0) / 10000.0;
  }

  static JsonArray array(Collection<String> values) {
    JsonArray array = new JsonArray();
    for (String value : new TreeSet<>(values)) {
      array.add(value);
    }
    return array;
  }

  static JsonObject set(Collection<String> values) {
    JsonObject object = new JsonObject();
    object.add("set", array(values));
    return object;
  }

  static JsonObject probeSet(Set<String> members, List<String> universe) {
    JsonObject object = new JsonObject();
    if (members.size() * 2 > universe.size()) {
      List<String> missing = new ArrayList<>();
      for (String key : universe) {
        if (!members.contains(key)) {
          missing.add(key);
        }
      }
      object.add("except", array(missing));
    } else {
      object.add("only", array(members));
    }
    return object;
  }

  static List<String> probeKeys() {
    List<String> keys = new ArrayList<>();
    for (GuiProbes.Probe probe : GuiProbes.all()) {
      keys.add(probe.key());
    }
    return keys;
  }

  static String stackKey(ItemStack stack) {
    if (stack.isEmpty()) {
      return "empty";
    }
    StringBuilder out = new StringBuilder(itemId(stack.getItem()));
    if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
      out.append("+dmg=").append(stack.getDamageValue());
    }
    if (stack.getItem() instanceof FluidItem) {
      Fluid fluid = FluidItem.getFluid(stack);
      if (fluid != Fluids.EMPTY) {
        out.append("+fluid=").append(fluidId(fluid)).append(':').append(FluidItem.getFluidAmount(stack));
      }
    }
    if (stack.getItem() instanceof IElectricItem electric) {
      IEnergy energy = electric.getEnergy(stack);
      if (energy != null && energy.energyStored() > 0) {
        out.append("+energy=").append(energy.energyStored());
      }
    }
    if (stack.isEnchanted()) {
      TreeSet<String> enchants = new TreeSet<>();
      EnchantmentHelper.getEnchantments(stack).forEach((e, l) -> enchants.add(enchantId(e) + "@" + l));
      out.append("+ench=").append(String.join(",", enchants));
    }
    return out.toString();
  }

  static String stack(ItemStack stack) {
    return stack.isEmpty() ? "empty" : stackKey(stack) + " x" + stack.getCount();
  }

  static List<String> ingredientItems(Ingredient ingredient) {
    List<String> ids = new ArrayList<>();
    for (ItemStack stack : ingredient.getItems()) {
      ids.add(itemId(stack.getItem()));
    }
    return ids;
  }

  static JsonObject ingredientSet(Ingredient ingredient) {
    return set(ingredientItems(ingredient));
  }

  ItemStack representative(Ingredient ingredient) {
    ItemStack best = ItemStack.EMPTY;
    for (ItemStack candidate : ingredient.getItems()) {
      if (candidate.isEmpty() || !ingredient.test(candidate)) {
        continue;
      }
      if (best.isEmpty() || itemId(candidate.getItem()).compareTo(itemId(best.getItem())) < 0) {
        best = candidate.copyWithCount(1);
      }
    }
    return best;
  }

  static String attributeId(String id) {
    String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    for (String prefix : new String[] { "generic.", "player.", "zombie.", "horse." }) {
      if (path.startsWith(prefix)) {
        return path.substring(prefix.length());
      }
    }
    return path;
  }

  private void place(BlockState state) {
    helper.setBlock(rel, state);
  }

  private void clearPlace() {
    BlockEntity be = level.getBlockEntity(abs);
    if (be instanceof FaktocraftBlockEntity machine && machine.getItemStackHandler() != null) {
      for (int i = 0; i < machine.getItemStackHandler().getSlots(); i++) {
        machine.getItemStackHandler().setStackInSlot(i, ItemStack.EMPTY);
      }
    }
    helper.setBlock(rel, Blocks.AIR.defaultBlockState());
  }

  static BlockState placementState(Block block) {
    if (block instanceof BlockNuclearReactor) {
      return block.defaultBlockState().setValue(BlockNuclearReactor.PART, BlockNuclearReactor.CORE_PART);
    }
    return block.defaultBlockState();
  }

  private static List<Block> modBlocks() {
    List<Block> blocks = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      if (GuiProbes.isMod(block)) {
        blocks.add(block);
      }
    }
    blocks.sort((a, b) -> blockId(a).compareTo(blockId(b)));
    return blocks;
  }

  private static List<Item> modItems() {
    List<Item> items = new ArrayList<>();
    for (Item item : BuiltInRegistries.ITEM) {
      if (GuiProbes.isMod(item)) {
        items.add(item);
      }
    }
    items.sort((a, b) -> itemId(a).compareTo(itemId(b)));
    return items;
  }

  private static List<Enchantment> enchantments() {
    List<Enchantment> out = new ArrayList<>();
    for (Enchantment enchantment : BuiltInRegistries.ENCHANTMENT) {
      out.add(enchantment);
    }
    out.sort((a, b) -> enchantId(a).compareTo(enchantId(b)));
    return out;
  }

  public JsonObject universe() {
    JsonObject out = new JsonObject();
    out.addProperty("version", VERSION);
    out.add("probes", array(probeKeys()));
    List<String> items = new ArrayList<>();
    for (Item item : BuiltInRegistries.ITEM) {
      items.add(itemId(item));
    }
    out.add("items", array(items));
    List<String> blocks = new ArrayList<>();
    for (Block block : modBlocks()) {
      blocks.add(blockId(block));
    }
    out.add("mod_blocks", array(blocks));
    List<String> allBlocks = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      allBlocks.add(blockId(block));
    }
    out.add("blocks", array(allBlocks));
    List<String> enchants = new ArrayList<>();
    for (Enchantment enchantment : enchantments()) {
      enchants.add(enchantId(enchantment));
    }
    out.add("enchantments", array(enchants));
    List<String> fluids = new ArrayList<>();
    for (Fluid fluid : GuiProbes.sourceFluids()) {
      fluids.add(fluidId(fluid));
    }
    out.add("fluids", array(fluids));
    return out;
  }

  public JsonObject items() {
    JsonObject out = new JsonObject();
    List<Enchantment> enchantments = enchantments();
    for (Item item : modItems()) {
      ItemStack stack = new ItemStack(item);
      JsonObject entry = new JsonObject();
      entry.addProperty("maxStack", stack.getMaxStackSize());
      entry.addProperty("maxDamage", stack.isDamageableItem() ? stack.getMaxDamage() : 0);
      entry.addProperty("rarity", stack.getRarity().name());
      entry.addProperty("fireResistant", item.isFireResistant());
      int enchantValue = item.getEnchantmentValue(stack);
      entry.addProperty("enchantable", stack.isEnchantable());
      List<String> table = new ArrayList<>();
      List<String> anvil = new ArrayList<>();
      for (Enchantment enchantment : enchantments) {
        String id = enchantId(enchantment);
        if (stack.isEnchantable() && enchantValue > 0 && !enchantment.isTreasureOnly()
            && enchantment.isDiscoverable() && stack.canApplyAtEnchantingTable(enchantment)) {
          table.add(id);
        }
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1));
        if (enchantment.canEnchant(stack) && item.isBookEnchantable(stack, book)) {
          anvil.add(id);
        }
      }
      entry.addProperty("enchantValue", table.isEmpty() ? 0 : enchantValue);
      entry.add("tableEnchants", set(table));
      entry.add("anvilEnchants", set(anvil));
      JsonObject attributes = new JsonObject();
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        Map<String, Double> sums = new TreeMap<>();
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
        modifiers.forEach((attribute, modifier) -> sums.merge(
            attributeId(String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(attribute))) + "/"
                + modifier.getOperation().ordinal(),
            modifier.getAmount(), Double::sum));
        if (!sums.isEmpty()) {
          JsonObject slotObject = new JsonObject();
          sums.forEach((key, value) -> slotObject.addProperty(key, round(value)));
          attributes.add(slot.getName(), slotObject);
        }
      }
      entry.add("attributes", attributes);
      JsonObject speeds = new JsonObject();
      List<String> correct = new ArrayList<>();
      for (String target : TOOL_TARGETS) {
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(target));
        if (block.isEmpty() || block.get() == Blocks.AIR) {
          continue;
        }
        BlockState state = block.get().defaultBlockState();
        float speed = stack.getDestroySpeed(state);
        if (speed != 1.0F) {
          speeds.addProperty(target, round(speed));
        }
        if (stack.isCorrectToolForDrops(state)) {
          correct.add(target);
        }
      }
      entry.addProperty("sweeps", stack.canPerformAction(ToolActions.SWORD_SWEEP));
      entry.add("speeds", speeds);
      entry.add("correctTool", set(correct));
      if (item instanceof IElectricItem electric) {
        IEnergy energy = electric.getEnergy(stack);
        JsonObject e = new JsonObject();
        e.addProperty("tier", electric.getEnergyTier().name());
        e.addProperty("type", electric.getEnergyType().name());
        if (energy != null) {
          e.addProperty("max", energy.maxEnergy());
          e.addProperty("receiveTick", energy.maxReceiveTick());
          e.addProperty("extractTick", energy.maxExtractTick());
        }
        entry.add("electric", e);
      }
      if (item instanceof FluidItem fluidItem) {
        entry.addProperty("fluidCapacity", fluidItem.getFluidCapacity());
      }
      FoodProperties food = item.getFoodProperties(stack, null);
      if (food != null) {
        JsonObject f = new JsonObject();
        f.addProperty("nutrition", food.getNutrition());
        f.addProperty("saturation", round(food.getNutrition() * food.getSaturationModifier() * 2.0F));
        entry.add("food", f);
      }
      out.add(itemId(item), entry);
    }
    return out;
  }

  public JsonObject blocks() {
    JsonObject out = new JsonObject();
    for (Block block : modBlocks()) {
      BlockState state = block.defaultBlockState();
      JsonObject entry = new JsonObject();
      entry.addProperty("hardness", round(state.getDestroySpeed(level, BlockPos.ZERO)));
      entry.addProperty("resistance", round(block.getExplosionResistance()));
      entry.addProperty("requiresTool", state.requiresCorrectToolForDrops());
      entry.addProperty("light", state.getLightEmission());
      entry.addProperty("randomTicks", state.isRandomlyTicking());
      entry.addProperty("push", state.getPistonPushReaction().name());
      entry.addProperty("item", itemId(block.asItem()));
      if (block instanceof EntityBlock entityBlock) {
        BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, placementState(block));
        entry.addProperty("blockEntity", be == null ? "none"
            : String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
      }
      out.add(blockId(block), entry);
    }
    return out;
  }

  @SuppressWarnings("deprecation")
  public JsonObject tags() {
    Map<String, Set<String>> itemTags = new TreeMap<>();
    for (Item item : BuiltInRegistries.ITEM) {
      boolean mod = GuiProbes.isMod(item);
      String id = itemId(item);
      item.builtInRegistryHolder().tags().forEach(tag -> {
        if (mod || Faktocraft.MODID.equals(tag.location().getNamespace())) {
          itemTags.computeIfAbsent(tag.location().toString(), k -> new TreeSet<>()).add(id);
        }
      });
    }
    Map<String, Set<String>> blockTags = new TreeMap<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      boolean mod = GuiProbes.isMod(block);
      String id = blockId(block);
      block.builtInRegistryHolder().tags().forEach(tag -> {
        if (mod || Faktocraft.MODID.equals(tag.location().getNamespace())) {
          blockTags.computeIfAbsent(tag.location().toString(), k -> new TreeSet<>()).add(id);
        }
      });
    }
    JsonObject out = new JsonObject();
    JsonObject items = new JsonObject();
    itemTags.forEach((tag, members) -> items.add(tag, set(members)));
    JsonObject blocks = new JsonObject();
    blockTags.forEach((tag, members) -> blocks.add(tag, set(members)));
    out.add("items", items);
    out.add("blocks", blocks);
    return out;
  }

  public JsonObject fuel() {
    JsonObject out = new JsonObject();
    for (GuiProbes.Probe probe : GuiProbes.all()) {
      int burn = ForgeHooks.getBurnTime(probe.create(), null);
      if (burn > 0) {
        out.addProperty(probe.key(), burn);
      }
    }
    return out;
  }

  private Map<String, ItemStack> dropTools() {
    Map<String, ItemStack> tools = new java.util.LinkedHashMap<>();
    tools.put("none", ItemStack.EMPTY);
    tools.put("pickaxe", new ItemStack(Items.DIAMOND_PICKAXE));
    ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
    silk.enchant(Enchantments.SILK_TOUCH, 1);
    ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
    fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
    tools.put("silk", silk);
    tools.put("fortune", fortune);
    tools.put("shears", new ItemStack(Items.SHEARS));
    return tools;
  }

  public JsonObject drops() {
    JsonObject out = new JsonObject();
    Map<String, ItemStack> tools = dropTools();
    for (Block block : modBlocks()) {
      BlockState state = placementState(block);
      JsonObject entry = new JsonObject();
      try {
        place(state);
        BlockEntity be = level.getBlockEntity(abs);
        for (Map.Entry<String, ItemStack> tool : tools.entrySet()) {
          Map<String, int[]> ranges = new TreeMap<>();
          List<Map<String, Integer>> rolls = new ArrayList<>();
          level.getRandom().setSeed(blockId(block).hashCode() * 31L + tool.getKey().hashCode());
          for (int roll = 0; roll < DROP_ROLLS; roll++) {
            Map<String, Integer> counts = new HashMap<>();
            for (ItemStack drop : Block.getDrops(state, level, abs, be, null, tool.getValue())) {
              counts.merge(stackKey(drop), drop.getCount(), Integer::sum);
            }
            rolls.add(counts);
            if (roll + 1 == DROP_PROBE_ROLLS && rolls.stream().allMatch(r -> r.equals(rolls.get(0)))) {
              break;
            }
          }
          Set<String> keys = new TreeSet<>();
          rolls.forEach(r -> keys.addAll(r.keySet()));
          for (String key : keys) {
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (Map<String, Integer> r : rolls) {
              int count = r.getOrDefault(key, 0);
              min = Math.min(min, count);
              max = Math.max(max, count);
            }
            ranges.put(key, new int[] { min, max });
          }
          JsonObject toolObject = new JsonObject();
          ranges.forEach((key, range) -> {
            JsonArray pair = new JsonArray();
            pair.add(range[0]);
            pair.add(range[1]);
            toolObject.add(key, pair);
          });
          entry.add(tool.getKey(), toolObject);
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", e.getClass().getSimpleName());
      } finally {
        clearPlace();
      }
      out.add(blockId(block), entry);
    }
    return out;
  }

  private static boolean isModItem(ItemStack stack) {
    return !stack.isEmpty() && GuiProbes.isMod(stack.getItem());
  }

  private static TransientCraftingContainer grid(int width, int height, List<ItemStack> items) {
    TransientCraftingContainer container = new TransientCraftingContainer(CRAFTING_MENU, width, height);
    for (int i = 0; i < items.size() && i < width * height; i++) {
      container.setItem(i, items.get(i));
    }
    return container;
  }

  public JsonObject crafting() {
    JsonObject out = new JsonObject();
    for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
      String id = recipe.getId().toString();
      boolean modRecipe = id.startsWith(Faktocraft.MODID + ":");
      JsonObject entry = new JsonObject();
      entry.addProperty("serializer", String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(
          recipe.getSerializer())));
      ItemStack result;
      try {
        if (recipe instanceof ShapedRecipe shaped) {
          entry.addProperty("kind", "shaped");
          entry.addProperty("w", shaped.getWidth());
          entry.addProperty("h", shaped.getHeight());
          JsonArray cells = new JsonArray();
          List<ItemStack> items = new ArrayList<>();
          for (Ingredient cell : shaped.getIngredients()) {
            boolean empty = cell.isEmpty();
            cells.add(empty ? JsonNull.INSTANCE : ingredientSet(cell));
            items.add(empty ? ItemStack.EMPTY : representative(cell));
          }
          entry.add("cells", cells);
          TransientCraftingContainer input = grid(shaped.getWidth(), shaped.getHeight(), items);
          entry.addProperty("matches", shaped.matches(input, level));
          result = shaped.assemble(input, level.registryAccess());
          entry.add("remainders", remainders(shaped.getRemainingItems(input)));
        } else if (recipe instanceof CraftingRecipe crafting) {
          List<Ingredient> ingredients = new ArrayList<>();
          for (Ingredient ingredient : crafting.getIngredients()) {
            if (!ingredient.isEmpty()) {
              ingredients.add(ingredient);
            }
          }
          if (ingredients.isEmpty()) {
            entry.addProperty("kind", "special");
            if (modRecipe) {
              out.add(id, entry);
            }
            continue;
          }
          entry.addProperty("kind", "shapeless");
          List<String> sets = new ArrayList<>();
          List<ItemStack> items = new ArrayList<>();
          for (Ingredient ingredient : ingredients) {
            sets.add(String.join(",", new TreeSet<>(ingredientItems(ingredient))));
            items.add(representative(ingredient));
          }
          JsonArray list = new JsonArray();
          sets.stream().sorted().forEach(list::add);
          entry.add("ingredients", list);
          TransientCraftingContainer input = grid(3, 3, items);
          entry.addProperty("matches", crafting.matches(input, level));
          result = crafting.assemble(input, level.registryAccess());
          entry.add("remainders", remainders(crafting.getRemainingItems(input)));
        } else if (recipe instanceof AbstractCookingRecipe cooking) {
          entry.addProperty("kind", "single:" + BuiltInRegistries.RECIPE_TYPE.getKey(cooking.getType()));
          Ingredient input = cooking.getIngredients().get(0);
          entry.add("input", ingredientSet(input));
          result = cooking.assemble(new SimpleContainer(representative(input)), level.registryAccess());
          entry.addProperty("cookingTime", cooking.getCookingTime());
          entry.addProperty("xp", round(cooking.getExperience()));
        } else if (recipe instanceof SingleItemRecipe single) {
          entry.addProperty("kind", "single:" + BuiltInRegistries.RECIPE_TYPE.getKey(single.getType()));
          Ingredient input = single.getIngredients().get(0);
          entry.add("input", ingredientSet(input));
          result = single.assemble(new SimpleContainer(representative(input)), level.registryAccess());
        } else {
          continue;
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", e.getClass().getSimpleName());
        if (modRecipe) {
          out.add(id, entry);
        }
        continue;
      }
      if (!modRecipe && !isModItem(result)) {
        continue;
      }
      entry.addProperty("result", stack(result));
      out.add(id, entry);
    }
    return out;
  }

  private static JsonArray remainders(NonNullList<ItemStack> items) {
    List<String> out = new ArrayList<>();
    for (ItemStack stack : items) {
      if (!stack.isEmpty()) {
        out.add(stack(stack));
      }
    }
    return array(out);
  }

  public JsonObject machineRecipes() {
    JsonObject out = new JsonObject();
    for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
      ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
      if (type == null || !Faktocraft.MODID.equals(type.getNamespace())) {
        continue;
      }
      JsonObject entry = new JsonObject();
      entry.addProperty("type", type.toString());
      entry.add("fields", fields(recipe, 0));
      out.add(recipe.getId().toString(), entry);
    }
    return out;
  }

  static JsonObject fields(Object value, int depth) {
    JsonObject out = new JsonObject();
    for (Class<?> type = value.getClass(); type != null && type != Object.class
        && type != Record.class; type = type.getSuperclass()) {
      if (!type.getName().startsWith("com.faktocraft")) {
        continue;
      }
      for (Field field : type.getDeclaredFields()) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || field.isSynthetic() || Modifier.isTransient(mods)
            || SKIPPED_FIELDS.contains(field.getName()) || out.has(field.getName())) {
          continue;
        }
        try {
          field.setAccessible(true);
          out.add(field.getName(), norm(field.get(value), depth));
        } catch (ReflectiveOperationException | RuntimeException e) {
          out.addProperty(field.getName(), "!" + e.getClass().getSimpleName());
        }
      }
    }
    return out;
  }

  static JsonElement norm(Object value, int depth) {
    if (value == null) {
      return JsonNull.INSTANCE;
    }
    if (value instanceof Optional<?> optional) {
      return optional.isPresent() ? norm(optional.get(), depth) : JsonNull.INSTANCE;
    }
    if (value instanceof Float || value instanceof Double) {
      return new JsonPrimitive(round(((Number) value).doubleValue()));
    }
    if (value instanceof Number number) {
      return new JsonPrimitive(number.longValue());
    }
    if (value instanceof Boolean bool) {
      return new JsonPrimitive(bool);
    }
    if (value instanceof CharSequence text) {
      return new JsonPrimitive(text.toString());
    }
    if (value instanceof Enum<?> e) {
      return new JsonPrimitive(e.name());
    }
    if (value instanceof ResourceLocation id) {
      return new JsonPrimitive(id.toString());
    }
    if (value instanceof Ingredient ingredient) {
      return ingredientSet(ingredient);
    }
    if (value instanceof ItemStack stack) {
      return new JsonPrimitive(stack(stack));
    }
    if (value instanceof FluidStack fluid) {
      return new JsonPrimitive(fluid.isEmpty() ? "empty" : fluidId(fluid.getFluid()) + " x" + fluid.getAmount());
    }
    if (value instanceof Fluid fluid) {
      return new JsonPrimitive(fluidId(fluid));
    }
    if (value instanceof Item item) {
      return new JsonPrimitive(itemId(item));
    }
    if (value instanceof Block block) {
      return new JsonPrimitive(blockId(block));
    }
    if (value instanceof TagKey<?> tag) {
      return new JsonPrimitive("#" + tag.location());
    }
    if (value instanceof Holder<?> holder) {
      return norm(holder.value(), depth);
    }
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, JsonElement> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        JsonElement key = norm(entry.getKey(), depth);
        JsonArray pair = new JsonArray();
        pair.add(key);
        pair.add(norm(entry.getValue(), depth));
        sorted.put(key.toString(), pair);
      }
      JsonArray array = new JsonArray();
      sorted.values().forEach(array::add);
      return array;
    }
    if (value instanceof Collection<?> collection) {
      JsonArray array = new JsonArray();
      for (Object element : collection) {
        array.add(norm(element, depth));
      }
      return array;
    }
    if (value.getClass().isArray()) {
      JsonArray array = new JsonArray();
      for (int i = 0; i < Array.getLength(value); i++) {
        array.add(norm(Array.get(value, i), depth));
      }
      return array;
    }
    if (depth < 5 && value.getClass().getName().startsWith("com.faktocraft")) {
      return fields(value, depth + 1);
    }
    return new JsonPrimitive("?" + value.getClass().getSimpleName());
  }

  @SuppressWarnings("unchecked")
  static int dataSlotCount(AbstractContainerMenu menu) {
    try {
      Field field = AbstractContainerMenu.class.getDeclaredField("dataSlots");
      field.setAccessible(true);
      return ((List<DataSlot>) field.get(menu)).size();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  static String slotClass(Slot slot) {
    Class<?> type = slot.getClass();
    String name = type.getSimpleName();
    return name.isEmpty() ? type.getSuperclass().getSimpleName() + "~anon" : name;
  }

  private static boolean quickMove(AbstractContainerMenu menu, Player player, int index) {
    Slot slot = menu.slots.get(index);
    if (!slot.mayPickup(player)) {
      return true;
    }
    ItemStack clicked = menu.quickMoveStack(player, index);
    int guard = 0;
    while (!clicked.isEmpty() && ItemStack.isSameItem(slot.getItem(), clicked)) {
      if (++guard > 64) {
        return false;
      }
      clicked = menu.quickMoveStack(player, index);
    }
    return true;
  }

  public JsonObject menus() {
    JsonObject out = new JsonObject();
    List<GuiProbes.Probe> probes = GuiProbes.all();
    List<String> universe = probeKeys();
    for (Block block : GuiProbes.menuBlocks()) {
      JsonObject entry = new JsonObject();
      try {
        place(placementState(block));
        AbstractContainerMenu menu = ((IHasMenu) block).getMenu(1, level, abs, player.getInventory(), player);
        if (menu == null) {
          entry.addProperty("error", "null menu");
          continue;
        }
        entry.addProperty("type", String.valueOf(BuiltInRegistries.MENU.getKey(menu.getType())));
        entry.addProperty("dataSlots", dataSlotCount(menu));
        int playerSlots = 0;
        for (Slot slot : menu.slots) {
          if (GuiProbes.isPlayerSlot(slot, player)) {
            playerSlots++;
          }
        }
        entry.addProperty("playerSlots", playerSlots);
        int invIndex = GuiProbes.playerSlotIndex(menu, player, 9);
        if (invIndex >= 0) {
          entry.addProperty("playerOrigin", menu.slots.get(invIndex).x + "," + menu.slots.get(invIndex).y);
        }
        List<Integer> machine = GuiProbes.machineSlots(menu, player);
        Set<String> anyAccepted = new LinkedHashSet<>();
        JsonObject slots = new JsonObject();
        Map<Integer, String> firstAccepted = new HashMap<>();
        for (int index : machine) {
          Slot slot = menu.slots.get(index);
          Set<String> accepted = new LinkedHashSet<>();
          for (GuiProbes.Probe probe : probes) {
            if (slot.mayPlace(probe.create())) {
              accepted.add(probe.key());
            }
          }
          if (!accepted.isEmpty()) {
            firstAccepted.put(index, accepted.iterator().next());
          }
          anyAccepted.addAll(accepted);
          JsonObject s = new JsonObject();
          s.addProperty("cls", slotClass(slot));
          s.addProperty("pos", slot.x + "," + slot.y);
          s.addProperty("idx", slot.getContainerSlot());
          s.addProperty("max", slot.getMaxStackSize());
          s.add("accepts", probeSet(accepted, universe));
          slots.add(String.valueOf(index), s);
        }
        entry.add("slots", slots);
        JsonObject shift = new JsonObject();
        if (invIndex >= 0) {
          for (GuiProbes.Probe probe : probes) {
            if (!anyAccepted.contains(probe.key())) {
              continue;
            }
            GuiProbes.clear(menu, player);
            ItemStack full = probe.full();
            menu.slots.get(invIndex).set(full.copy());
            if (!quickMove(menu, player, invIndex)) {
              shift.addProperty(probe.key(), "loop");
              continue;
            }
            List<String> where = new ArrayList<>();
            for (int index : machine) {
              ItemStack held = menu.slots.get(index).getItem();
              if (GuiProbes.same(held, full)) {
                where.add(index + ":" + held.getCount());
              }
            }
            int left = 0;
            for (Slot slot : menu.slots) {
              if (GuiProbes.isPlayerSlot(slot, player) && GuiProbes.same(slot.getItem(), full)) {
                left += slot.getItem().getCount();
              }
            }
            shift.addProperty(probe.key(), String.join(" ", where) + " left=" + left);
          }
        }
        entry.add("shift", shift);
        JsonObject shiftOut = new JsonObject();
        for (Map.Entry<Integer, String> first : firstAccepted.entrySet()) {
          GuiProbes.Probe probe = null;
          for (GuiProbes.Probe candidate : probes) {
            if (candidate.key().equals(first.getValue())) {
              probe = candidate;
              break;
            }
          }
          GuiProbes.clear(menu, player);
          Slot slot = menu.slots.get(first.getKey());
          ItemStack one = probe.create();
          slot.set(one.copy());
          if (!quickMove(menu, player, first.getKey())) {
            shiftOut.addProperty(String.valueOf(first.getKey()), "loop");
            continue;
          }
          shiftOut.addProperty(String.valueOf(first.getKey()), slot.getItem().isEmpty() ? "out" : "stay");
        }
        entry.add("shiftOut", shiftOut);
        GuiProbes.clear(menu, player);
      } catch (RuntimeException e) {
        entry.addProperty("error", e.getClass().getSimpleName());
      } finally {
        clearPlace();
        out.add(blockId(block), entry);
      }
    }
    return out;
  }

  private static IItemHandler items(BlockEntity be, Direction side) {
    return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
  }

  private static IFluidHandler fluids(BlockEntity be, Direction side) {
    return be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
  }

  public JsonObject sides() {
    JsonObject out = new JsonObject();
    List<GuiProbes.Probe> probes = GuiProbes.all();
    List<Fluid> fluids = GuiProbes.sourceFluids();
    for (Block block : modBlocks()) {
      if (!(block instanceof EntityBlock)) {
        continue;
      }
      BlockState state = placementState(block);
      JsonObject entry = new JsonObject();
      try {
        place(state);
        BlockEntity be = level.getBlockEntity(abs);
        if (be == null) {
          continue;
        }
        IItemHandler internal = items(be, null);
        List<GuiProbes.Probe> candidates = new ArrayList<>();
        for (int p = 0; p < probes.size(); p++) {
          GuiProbes.Probe probe = probes.get(p);
          boolean valid = p % SAMPLE_EVERY == 0;
          if (!valid && internal != null) {
            ItemStack sample = probe.create();
            for (int slot = 0; slot < internal.getSlots(); slot++) {
              if (internal.isItemValid(slot, sample)) {
                valid = true;
                break;
              }
            }
          }
          if (valid) {
            candidates.add(probe);
          }
        }
        List<String> candidateKeys = new ArrayList<>();
        candidates.forEach(c -> candidateKeys.add(c.key()));
        JsonObject itemSides = new JsonObject();
        itemSides.add("probes", set(candidateKeys));
        for (int s = 0; s < SIDES.length; s++) {
          IItemHandler handler = items(be, SIDES[s]);
          if (handler == null) {
            continue;
          }
          JsonObject side = new JsonObject();
          side.addProperty("slots", handler.getSlots());
          JsonArray limits = new JsonArray();
          JsonArray accepts = new JsonArray();
          JsonArray fullInsert = new JsonArray();
          for (int slot = 0; slot < handler.getSlots(); slot++) {
            limits.add(handler.getSlotLimit(slot));
            List<String> accepted = new ArrayList<>();
            GuiProbes.Probe first = null;
            for (GuiProbes.Probe probe : candidates) {
              if (handler.insertItem(slot, probe.create(), true).isEmpty()) {
                accepted.add(probe.key());
                if (first == null) {
                  first = probe;
                }
              }
            }
            accepts.add(set(accepted));
            if (first == null) {
              fullInsert.add(0);
            } else {
              ItemStack full = first.full();
              fullInsert.add(full.getCount() - handler.insertItem(slot, full, true).getCount());
            }
          }
          side.add("limits", limits);
          side.add("accepts", accepts);
          side.add("fullInsert", fullInsert);
          itemSides.add(SIDE_NAMES[s], side);
        }
        if (be instanceof FaktocraftBlockEntity machine && machine.getItemStackHandler() != null) {
          var stacks = machine.getItemStackHandler();
          for (int slot = 0; slot < stacks.getSlots(); slot++) {
            stacks.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 10));
          }
          JsonObject extract = new JsonObject();
          for (int s = 0; s < SIDES.length; s++) {
            IItemHandler handler = items(be, SIDES[s]);
            if (handler == null) {
              continue;
            }
            JsonArray counts = new JsonArray();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
              counts.add(handler.extractItem(slot, 64, true).getCount());
            }
            extract.add(SIDE_NAMES[s], counts);
          }
          itemSides.add("extract", extract);
          for (int slot = 0; slot < stacks.getSlots(); slot++) {
            stacks.setStackInSlot(slot, ItemStack.EMPTY);
          }
        }
        entry.add("items", itemSides);
        JsonObject fluidSides = new JsonObject();
        for (int s = 0; s < SIDES.length; s++) {
          IFluidHandler handler = fluids(be, SIDES[s]);
          if (handler == null) {
            continue;
          }
          JsonObject side = new JsonObject();
          JsonArray capacities = new JsonArray();
          for (int tank = 0; tank < handler.getTanks(); tank++) {
            capacities.add(handler.getTankCapacity(tank));
          }
          side.add("capacity", capacities);
          List<String> accepted = new ArrayList<>();
          for (Fluid fluid : fluids) {
            if (handler.fill(new FluidStack(fluid, 1000), IFluidHandler.FluidAction.SIMULATE) > 0) {
              accepted.add(fluidId(fluid));
            }
          }
          side.add("accepts", set(accepted));
          fluidSides.add(SIDE_NAMES[s], side);
        }
        entry.add("fluids", fluidSides);
        if (be instanceof FaktocraftBlockEntity machine && machine.hasEnergy()) {
          IEnergy energy = machine.getEnergyStorage();
          JsonObject e = new JsonObject();
          e.addProperty("max", energy.maxEnergy());
          e.addProperty("tier", energy.energyTier().name());
          e.addProperty("type", energy.energyType().name());
          e.addProperty("receiveTick", energy.maxReceiveTick());
          e.addProperty("extractTick", energy.maxExtractTick());
          List<String> receive = new ArrayList<>();
          List<String> extract = new ArrayList<>();
          for (int s = 0; s < SIDES.length; s++) {
            if (machine.canReceiveEnergyDir(SIDES[s]) && energy.canReceiveEnergy(SIDES[s])) {
              receive.add(SIDE_NAMES[s]);
            }
            if (machine.canExtractEnergyDir(SIDES[s]) && energy.canExtractEnergy(SIDES[s])) {
              extract.add(SIDE_NAMES[s]);
            }
          }
          e.add("receive", set(receive));
          e.add("extract", set(extract));
          entry.add("energy", e);
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", e.getClass().getSimpleName());
      } finally {
        clearPlace();
        out.add(blockId(block), entry);
      }
    }
    return out;
  }
}
