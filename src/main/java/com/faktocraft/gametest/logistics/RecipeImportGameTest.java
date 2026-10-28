package com.faktocraft.gametest.logistics;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.logistics.BlockEntityRecipePipe;
import com.faktocraft.common.block.impl.logistics.BlockEntityRequestTable;
import com.faktocraft.common.block.impl.logistics.IoMode;
import com.faktocraft.common.block.impl.logistics.ItemKey;
import com.faktocraft.common.block.impl.logistics.LogisticsRegistry;
import com.faktocraft.common.block.impl.logistics.RecipeImport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class RecipeImportGameTest {

  private static final String TEMPLATE = "gametest_platform";

  private static RecipeImport.Slot in(ItemStack... alternatives) {
    return new RecipeImport.Slot(true, List.of(alternatives), alternatives[0], false, false);
  }

  private static RecipeImport.Slot out(ItemStack stack) {
    return new RecipeImport.Slot(false, List.of(stack), stack, false, false);
  }

  private static RecipeImport.Slot chance(ItemStack stack) {
    return new RecipeImport.Slot(false, List.of(stack), stack, true, false);
  }

  private static RecipeImport.Slot fluid(boolean input) {
    return new RecipeImport.Slot(input, List.of(), ItemStack.EMPTY, false, true);
  }

  private static List<RecipeImport.Slot> jeiBrewing() {
    ItemStack water = RecipePipeBatchGameTest.waterBottle();
    return List.of(in(water), in(water), in(water), in(new ItemStack(Items.NETHER_WART)),
        out(RecipePipeBatchGameTest.awkwardPotion()));
  }

  private static boolean expect(GameTestHelper helper, String what, RecipeImport.Entry entry, ItemStack stack,
      int count, IoMode mode) {
    if (!ItemKey.of(entry.stack()).equals(ItemKey.of(stack)) || entry.count() != count || entry.mode() != mode) {
      helper.fail(what + " is " + entry + ", expected " + count + "x " + stack + " " + mode);
      return false;
    }
    return true;
  }

  private static boolean expectBrewingPlan(GameTestHelper helper, RecipeImport.Plan plan) {
    if (plan.inputs().size() != 2 || plan.outputs().size() != 1) {
      helper.fail("brewing plan has " + plan.inputs() + " -> " + plan.outputs());
      return false;
    }
    if (plan.batch() != RecipeImport.BREWING_BATCH || !plan.notes().isEmpty()) {
      helper.fail("brewing plan batch " + plan.batch() + ", notes " + plan.notes());
      return false;
    }
    return expect(helper, "bottle input", plan.inputs().get(0), RecipePipeBatchGameTest.waterBottle(), 1,
        IoMode.PER_UNIT)
        && expect(helper, "wart input", plan.inputs().get(1), new ItemStack(Items.NETHER_WART), 1,
            IoMode.PER_BATCH)
        && expect(helper, "potion output", plan.outputs().get(0), RecipePipeBatchGameTest.awkwardPotion(), 1,
            IoMode.PER_UNIT);
  }

  private static String key(Component component) {
    return component != null && component.getContents() instanceof TranslatableContents contents
        ? contents.getKey()
        : String.valueOf(component);
  }

  @GameTest(template = TEMPLATE)
  public static void jeiBrewingLayoutBecomesBatch(GameTestHelper helper) {
    if (expectBrewingPlan(helper, RecipeImport.plan(jeiBrewing(), RecipeImport.BREWING_BATCH))) {
      helper.succeed();
    }
  }

  @GameTest(template = TEMPLATE)
  public static void reiBrewingLayoutBecomesBatch(GameTestHelper helper) {
    List<RecipeImport.Slot> slots = List.of(in(RecipePipeBatchGameTest.waterBottle()),
        in(new ItemStack(Items.NETHER_WART)), out(RecipePipeBatchGameTest.awkwardPotion()));
    if (expectBrewingPlan(helper, RecipeImport.plan(slots, RecipeImport.BREWING_BATCH))) {
      helper.succeed();
    }
  }

  @GameTest(template = TEMPLATE)
  public static void splashBrewingKeepsTheBottleAsCarrier(GameTestHelper helper) {
    ItemStack strong = potion(Items.POTION, Potions.STRONG_POISON);
    ItemStack splash = potion(Items.SPLASH_POTION, Potions.STRONG_POISON);
    RecipeImport.Plan rei = RecipeImport.plan(List.of(in(strong), in(new ItemStack(Items.GUNPOWDER)), out(splash)),
        RecipeImport.BREWING_BATCH);
    RecipeImport.Plan jei = RecipeImport.plan(List.of(in(strong), in(strong), in(strong),
        in(new ItemStack(Items.GUNPOWDER)), out(splash)), RecipeImport.BREWING_BATCH);
    for (RecipeImport.Plan plan : List.of(rei, jei)) {
      if (plan.batch() != RecipeImport.BREWING_BATCH || plan.inputs().size() != 2) {
        helper.fail("splash plan is " + plan);
        return;
      }
      if (!expect(helper, "strong poison", plan.inputs().get(0), strong, 1, IoMode.PER_UNIT)
          || !expect(helper, "gunpowder", plan.inputs().get(1), new ItemStack(Items.GUNPOWDER), 1, IoMode.PER_BATCH)
          || !expect(helper, "splash", plan.outputs().get(0), splash, 1, IoMode.PER_UNIT)) {
        return;
      }
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void repeatedInputsMerge(GameTestHelper helper) {
    RecipeImport.Plan plan = RecipeImport.plan(List.of(in(new ItemStack(Items.COPPER_INGOT)),
        in(new ItemStack(Items.IRON_INGOT)), in(new ItemStack(Items.COPPER_INGOT, 2)),
        out(new ItemStack(Items.GOLD_INGOT)), out(new ItemStack(Items.GOLD_INGOT))), 1);
    if (plan.inputs().size() != 2 || plan.outputs().size() != 1 || plan.batch() != 1) {
      helper.fail("merged plan is " + plan);
      return;
    }
    if (expect(helper, "copper", plan.inputs().get(0), new ItemStack(Items.COPPER_INGOT), 3, IoMode.PER_UNIT)
        && expect(helper, "iron", plan.inputs().get(1), new ItemStack(Items.IRON_INGOT), 1, IoMode.PER_UNIT)
        && expect(helper, "gold", plan.outputs().get(0), new ItemStack(Items.GOLD_INGOT), 2, IoMode.PER_UNIT)) {
      helper.succeed();
    }
  }

  @GameTest(template = TEMPLATE)
  public static void alternativesBecomeMatchingTag(GameTestHelper helper) {
    HolderSet.Named<Item> planks = BuiltInRegistries.ITEM.getTag(ItemTags.PLANKS).orElse(null);
    if (planks == null || planks.size() < 2) {
      helper.fail("planks tag is not loaded");
      return;
    }
    List<ItemStack> alternatives = new ArrayList<>();
    Set<Item> members = new HashSet<>();
    for (Holder<Item> holder : planks) {
      alternatives.add(new ItemStack(holder.value(), 2));
      members.add(holder.value());
    }
    RecipeImport.Plan plan = RecipeImport.plan(List.of(
        new RecipeImport.Slot(true, alternatives, alternatives.get(1), false, false),
        out(new ItemStack(Items.STICK, 4))), 1);
    RecipeImport.Entry input = plan.inputs().get(0);
    ResourceLocation id = ResourceLocation.tryParse(input.tag());
    HolderSet.Named<Item> inferred = id == null ? null
        : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).orElse(null);
    if (inferred == null || inferred.size() != members.size()
        || !inferred.stream().allMatch(holder -> members.contains(holder.value()))) {
      helper.fail("inferred tag '" + input.tag() + "' does not hold exactly the planks");
      return;
    }
    if (!ItemKey.of(input.stack()).equals(ItemKey.of(alternatives.get(1))) || input.count() != 2) {
      helper.fail("tag input should keep the shown plank and its count: " + input);
      return;
    }
    RecipeImport.Plan partial = RecipeImport.plan(List.of(
        in(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS)), out(new ItemStack(Items.STICK))), 1);
    if (!partial.inputs().get(0).tag().isEmpty()) {
      helper.fail("a partial set of planks picked tag " + partial.inputs().get(0).tag());
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void componentAlternativesKeepTheShownStack(GameTestHelper helper) {
    ItemStack water = RecipePipeBatchGameTest.waterBottle();
    ItemStack awkward = RecipePipeBatchGameTest.awkwardPotion();
    RecipeImport.Plan plan = RecipeImport.plan(List.of(
        new RecipeImport.Slot(true, List.of(water, awkward), awkward, false, false),
        out(new ItemStack(Items.GLASS_BOTTLE))), 1);
    RecipeImport.Entry input = plan.inputs().get(0);
    if (!input.tag().isEmpty() || !ItemKey.of(input.stack()).equals(ItemKey.of(awkward))) {
      helper.fail("potion alternatives became " + input);
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void returnedInputIsKeptStocked(GameTestHelper helper) {
    RecipeImport.Plan plan = RecipeImport.plan(List.of(in(new ItemStack(Items.SHEARS)),
        in(new ItemStack(Items.OAK_LOG)), out(new ItemStack(Items.SHEARS)), out(new ItemStack(Items.STRING, 4))),
        1);
    if (plan.inputs().size() != 2 || plan.outputs().size() != 1) {
      helper.fail("catalyst plan is " + plan);
      return;
    }
    if (!expect(helper, "shears", plan.inputs().get(0), new ItemStack(Items.SHEARS), 1, IoMode.MAINTAIN)
        || !expect(helper, "string", plan.outputs().get(0), new ItemStack(Items.STRING), 4, IoMode.PER_UNIT)) {
      return;
    }
    RecipeImport.Plan single = RecipeImport.plan(List.of(in(new ItemStack(Items.BUCKET)),
        in(new ItemStack(Items.SAND)), out(new ItemStack(Items.BUCKET))), 1);
    if (single.outputs().size() != 1 || single.inputs().get(0).mode() != IoMode.PER_UNIT) {
      helper.fail("the only output was turned into a catalyst: " + single);
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void chanceAndFluidSlotsAreLeftOut(GameTestHelper helper) {
    RecipeImport.Plan plan = RecipeImport.plan(List.of(in(new ItemStack(Items.COBBLESTONE)), fluid(true),
        out(new ItemStack(Items.GRAVEL)), chance(new ItemStack(Items.FLINT)), fluid(false)), 1);
    if (plan.outputs().size() != 1 || !plan.outputs().get(0).stack().is(Items.GRAVEL)) {
      helper.fail("chance output was imported: " + plan.outputs());
      return;
    }
    if (!plan.notes().contains(RecipeImport.Note.CHANCE) || !plan.notes().contains(RecipeImport.Note.FLUIDS)) {
      helper.fail("missing notes: " + plan.notes());
      return;
    }
    RecipeImport.Plan onlyChance = RecipeImport.plan(List.of(in(new ItemStack(Items.COBBLESTONE)),
        chance(new ItemStack(Items.FLINT))), 1);
    String problem = key(RecipeImport.problem(onlyChance));
    if (!problem.endsWith("transfer_no_fixed_output")) {
      helper.fail("chance-only recipe reported " + problem);
      return;
    }
    String itemsOnly = key(RecipeImport.problem(RecipeImport.plan(List.of(fluid(true), fluid(false)), 1)));
    if (!itemsOnly.endsWith("transfer_items_only")) {
      helper.fail("fluid-only recipe reported " + itemsOnly);
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void extraEntriesAreReported(GameTestHelper helper) {
    Item[] items = { Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.DIRT, Items.SAND,
        Items.GRAVEL, Items.CLAY_BALL, Items.FLINT, Items.COAL, Items.CHARCOAL, Items.BONE, Items.FEATHER,
        Items.STRING };
    List<RecipeImport.Slot> slots = new ArrayList<>();
    for (Item item : items) {
      slots.add(in(new ItemStack(item)));
    }
    slots.add(out(new ItemStack(Items.DIAMOND)));
    RecipeImport.Plan plan = RecipeImport.plan(slots, 1);
    if (plan.inputs().size() != BlockEntityRecipePipe.MAX_INPUTS
        || !plan.notes().contains(RecipeImport.Note.TRUNCATED)) {
      helper.fail("expected " + BlockEntityRecipePipe.MAX_INPUTS + " inputs and a note, got "
          + plan.inputs().size() + " " + plan.notes());
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE)
  public static void importValidatesAndKeepsBindings(GameTestHelper helper) {
    BlockPos rel = new BlockPos(2, 1, 2);
    helper.setBlock(rel, LogisticsRegistry.RECIPE_PIPE.defaultBlockState());
    if (!(helper.getBlockEntity(rel) instanceof BlockEntityRecipePipe pipe)) {
      helper.fail("no recipe pipe");
      return;
    }
    int recipe = pipe.addRecipe();
    pipe.setIo(recipe, 0, RecipePipeBatchGameTest.waterBottle());
    pipe.bind(recipe, 0, 0, 2, "minecraft:brewing_stand", 5);
    pipe.setIo(recipe, 1, new ItemStack(Items.SUGAR));
    pipe.bind(recipe, 1, 3, 3, "minecraft:brewing_stand", 5);
    pipe.setIo(recipe, 2, new ItemStack(Items.STONE));
    pipe.setIo(recipe, 3, new ItemStack(Items.DIRT));
    pipe.importRecipe(recipe, List.of(
        new RecipeImport.Entry(RecipePipeBatchGameTest.waterBottle(), 1, "", IoMode.PER_UNIT),
        new RecipeImport.Entry(new ItemStack(Items.NETHER_WART), 1, "minecraft:planks", IoMode.PER_BATCH),
        new RecipeImport.Entry(new ItemStack(Items.OAK_PLANKS), 99999, "minecraft:planks", IoMode.PER_UNIT)),
        List.of(new RecipeImport.Entry(RecipePipeBatchGameTest.awkwardPotion(), 1, "", IoMode.MAINTAIN)), 3);
    BlockEntityRecipePipe.MachineRecipe entry = pipe.recipe(recipe);
    BlockEntityRecipePipe.Io bottles = entry.inputs[0];
    BlockEntityRecipePipe.Io wart = entry.inputs[1];
    BlockEntityRecipePipe.Io planks = entry.inputs[2];
    BlockEntityRecipePipe.Io potion = entry.outputs[0];
    if (bottles.bindSlot != 0 || bottles.bindEnd != 2) {
      helper.fail("binding of the unchanged bottle input was lost: " + bottles.bindSlot + ".." + bottles.bindEnd);
      return;
    }
    if (wart.bindSlot != -1 || !wart.tag.isEmpty() || wart.mode != IoMode.PER_BATCH) {
      helper.fail("wart input kept a stale binding or took a foreign tag: bind=" + wart.bindSlot + " tag="
          + wart.tag + " mode=" + wart.mode);
      return;
    }
    if (!planks.tag.equals("minecraft:planks") || planks.count != BlockEntityRecipePipe.MAX_COUNT) {
      helper.fail("planks input is tag=" + planks.tag + " count=" + planks.count);
      return;
    }
    if (!entry.inputs[3].isEmpty()) {
      helper.fail("an old input survived the import: " + entry.inputs[3].stack);
      return;
    }
    if (potion.mode != IoMode.PER_UNIT || entry.batchSize != 3) {
      helper.fail("output mode " + potion.mode + ", batch " + entry.batchSize);
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 1400)
  public static void importedBrewingRecipeRuns(GameTestHelper helper) {
    RecipePipeBatchGameTest.buildNetwork(helper, RecipePipeBatchGameTest.brewingStock(8, 8, 4));
    helper.setBlock(RecipePipeBatchGameTest.MACHINE, Blocks.BREWING_STAND.defaultBlockState());
    if (!(helper.getBlockEntity(RecipePipeBatchGameTest.MACHINE) instanceof BrewingStandBlockEntity stand)) {
      helper.fail("no brewing stand");
      return;
    }
    stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 4));
    if (!(helper.getBlockEntity(RecipePipeBatchGameTest.PIPE) instanceof BlockEntityRecipePipe pipe)) {
      helper.fail("no recipe pipe");
      return;
    }
    RecipeImport.Plan plan = RecipeImport.plan(jeiBrewing(), RecipeImport.BREWING_BATCH);
    pipe.importRecipe(pipe.addRecipe(), plan.inputs(), plan.outputs(), plan.batch());
    RecipePipeBatchGameTest.requestPotions(helper, 4);
    RecipePipeBatchGameTest.expectPotions(helper, 4, 2, 8, 8);
  }

  private static ItemStack potion(Item item, Potion potion) {
    return PotionUtils.setPotion(new ItemStack(item), potion);
  }

  private static void importBrewing(BlockEntityRecipePipe pipe, ItemStack base, Item ingredient, ItemStack result) {
    RecipeImport.Plan plan = RecipeImport.plan(List.of(in(base), in(base), in(base), in(new ItemStack(ingredient)),
        out(result)), RecipeImport.BREWING_BATCH);
    pipe.importRecipe(pipe.addRecipe(), plan.inputs(), plan.outputs(), plan.batch());
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 3600)
  public static void chainedSplashPotionsUseOneOfEachIngredient(GameTestHelper helper) {
    ItemStack water = potion(Items.POTION, Potions.WATER);
    ItemStack awkward = potion(Items.POTION, Potions.AWKWARD);
    ItemStack poison = potion(Items.POTION, Potions.POISON);
    ItemStack strong = potion(Items.POTION, Potions.STRONG_POISON);
    ItemStack splash = potion(Items.SPLASH_POTION, Potions.STRONG_POISON);
    RecipePipeBatchGameTest.buildNetwork(helper, water.copy(), water.copy(), water.copy(), water.copy(),
        new ItemStack(Items.NETHER_WART, 8), new ItemStack(Items.SPIDER_EYE, 8),
        new ItemStack(Items.GLOWSTONE_DUST, 8), new ItemStack(Items.GUNPOWDER, 8));
    helper.setBlock(RecipePipeBatchGameTest.MACHINE, Blocks.BREWING_STAND.defaultBlockState());
    if (!(helper.getBlockEntity(RecipePipeBatchGameTest.MACHINE) instanceof BrewingStandBlockEntity stand)) {
      helper.fail("no brewing stand");
      return;
    }
    stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 4));
    if (!(helper.getBlockEntity(RecipePipeBatchGameTest.PIPE) instanceof BlockEntityRecipePipe pipe)) {
      helper.fail("no recipe pipe");
      return;
    }
    importBrewing(pipe, water, Items.NETHER_WART, awkward);
    importBrewing(pipe, awkward, Items.SPIDER_EYE, poison);
    importBrewing(pipe, poison, Items.GLOWSTONE_DUST, strong);
    importBrewing(pipe, strong, Items.GUNPOWDER, splash);
    helper.runAfterDelay(20, () -> {
      if (helper.getBlockEntity(RecipePipeBatchGameTest.TABLE) instanceof BlockEntityRequestTable table) {
        table.setGhostTarget(splash);
        table.request(null, 2);
      } else {
        helper.fail("no request table");
      }
    });
    ItemKey splashKey = ItemKey.of(splash);
    ItemKey waterKey = ItemKey.of(water);
    helper.succeedWhen(() -> {
      if (!(helper.getBlockEntity(RecipePipeBatchGameTest.TABLE) instanceof BlockEntityRequestTable table)) {
        helper.fail("no request table");
        return;
      }
      int delivered = RecipePipeBatchGameTest.countIn(table.getItemStackHandler(), splashKey::matches);
      if (delivered != 2) {
        helper.fail("expected 2 splash potions at the table, found " + delivered + "; "
            + RecipePipeBatchGameTest.ledgerState(helper));
        return;
      }
      for (int i = 0; i < 4; i++) {
        if (!stand.getItem(i).isEmpty()) {
          helper.fail("stand slot " + i + " still holds " + stand.getItem(i) + "; "
              + RecipePipeBatchGameTest.ledgerState(helper));
          return;
        }
      }
      Item[] used = { Items.NETHER_WART, Items.SPIDER_EYE, Items.GLOWSTONE_DUST, Items.GUNPOWDER };
      for (Item item : used) {
        int left = RecipePipeBatchGameTest.countInChest(helper, RecipePipeBatchGameTest.STOCK, stack -> stack.is(item));
        if (left != 7) {
          helper.fail("expected 7 " + item + " left in stock, found " + left + "; "
              + RecipePipeBatchGameTest.ledgerState(helper));
          return;
        }
      }
      int bottles = RecipePipeBatchGameTest.countInChest(helper, RecipePipeBatchGameTest.STOCK, waterKey::matches);
      if (bottles != 2) {
        helper.fail("expected 2 water bottles left in stock, found " + bottles);
      }
    });
  }
}
