package com.faktocraft.integration.rei;

import com.faktocraft.common.block.impl.machines.distillery.BlockEntityDistillery;
import com.faktocraft.common.block.impl.machines.fermenter.BlockEntityFermenter;
import com.faktocraft.common.block.impl.machines.matter_fabricator.BlockEntityMatterFabricator;
import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.enums.EnumLang;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.recipe.ChanceResult;
import com.faktocraft.common.recipe.FluidIngredientData;
import com.faktocraft.common.recipe.impl.AlloySmeltingRecipe;
import com.faktocraft.common.recipe.impl.CircuitAssemblingRecipe;
import com.faktocraft.common.recipe.impl.CompressingRecipe;
import com.faktocraft.common.recipe.impl.CrushingRecipe;
import com.faktocraft.common.recipe.impl.CuttingRecipe;
import com.faktocraft.common.recipe.impl.ExtractingRecipe;
import com.faktocraft.common.recipe.impl.ExtrudingRecipe;
import com.faktocraft.common.recipe.impl.FluidEnrichingRecipe;
import com.faktocraft.common.recipe.impl.FluidExtrudingRecipe;
import com.faktocraft.common.recipe.impl.OreWashingRecipe;
import com.faktocraft.common.recipe.impl.PolymerizingRecipe;
import com.faktocraft.common.recipe.impl.RecyclingRecipe;
import com.faktocraft.common.recipe.impl.RollingRecipe;
import com.faktocraft.common.recipe.impl.SawingRecipe;
import com.faktocraft.common.recipe.impl.ScannerRecipe;
import com.faktocraft.common.recipe.impl.ScrapBoxRecipe;
import com.faktocraft.common.recipe.impl.ThermalCentrifugingRecipe;
import com.faktocraft.common.recipe.impl.UraniumCentrifugingRecipe;
import com.faktocraft.common.registries.ModItems;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReiDisplays {

  private ReiDisplays() {
  }

  private static EntryIngredient items(Ingredient ingredient, int count) {
    List<ItemStack> stacks = Arrays.stream(ingredient.getItems())
        .map(stack -> new ItemStack(stack.getItem(), count)).toList();
    return EntryIngredients.ofItemStacks(stacks);
  }

  private static EntryIngredient item(ItemStack stack) {
    return stack.isEmpty() ? EntryIngredient.empty() : EntryIngredients.of(stack);
  }

  private static EntryIngredient fluid(FluidIngredientData data) {
    return EntryIngredients.of(data.getFluid(), data.amountMb());
  }

  private static EntryIngredient fluid(Fluid fluid, int amount) {
    return EntryIngredients.of(fluid, Math.max(amount, 1));
  }

  private static EntryIngredient chance(ItemStack stack, float chance) {
    Component line = Component.translatable(EnumLang.CHANCE.getTranslationKey(),
        Component.literal((Math.round(chance * 100.0) / 100.0) + "%").withStyle(ChatFormatting.YELLOW))
        .withStyle(ChatFormatting.DARK_GRAY);
    return EntryIngredient.of(EntryStacks.of(stack).tooltip(line));
  }

  private static Optional<net.minecraft.resources.ResourceLocation> location(Recipe<?> recipe) {
    return Optional.ofNullable(recipe.getId());
  }

  private static MachineDisplay bonusDisplay(CategoryIdentifier<MachineDisplay> category, Recipe<?> recipe,
      Ingredient ingredient, int count, ItemStack result, Optional<ChanceResult> bonus, int duration, int power,
      float experience) {
    List<EntryIngredient> outputs = new ArrayList<>();
    List<Float> chances = new ArrayList<>();
    outputs.add(item(result));
    chances.add(0.0F);
    bonus.ifPresent(chanceResult -> {
      outputs.add(chance(chanceResult.stack(), chanceResult.chance()));
      chances.add(chanceResult.chance());
    });
    return new MachineDisplay(category, List.of(items(ingredient, count)), outputs, location(recipe),
        MachineDisplay.Info.of(duration, power, experience), chances);
  }

  public static MachineDisplay crushing(CrushingRecipe recipe) {
    return bonusDisplay(ReiCategories.CRUSHING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), recipe.getBonusResult().firstResult(), recipe.getDuration(),
        recipe.getPowerCost(), recipe.getExperience());
  }

  public static MachineDisplay compressing(CompressingRecipe recipe) {
    return bonusDisplay(ReiCategories.COMPRESSING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), recipe.getBonusResult().firstResult(), recipe.getDuration(),
        recipe.getPowerCost(), recipe.getExperience());
  }

  public static MachineDisplay extracting(ExtractingRecipe recipe) {
    return bonusDisplay(ReiCategories.EXTRACTING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), recipe.getBonusResult().firstResult(), recipe.getDuration(),
        recipe.getPowerCost(), recipe.getExperience());
  }

  public static MachineDisplay sawing(SawingRecipe recipe) {
    return bonusDisplay(ReiCategories.SAWING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), recipe.getBonusResult().firstResult(), recipe.getDuration(),
        recipe.getPowerCost(), recipe.getExperience());
  }

  public static MachineDisplay fluidExtruding(FluidExtrudingRecipe recipe) {
    return new MachineDisplay(ReiCategories.FLUID_EXTRUDING,
        List.of(fluid(Fluids.WATER, recipe.getWaterCost()), fluid(Fluids.LAVA, recipe.getLavaCost())),
        List.of(item(recipe.getResultItem())), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience()));
  }

  private static List<EntryIngredient> mapped(Map<Ingredient, Integer> map) {
    List<EntryIngredient> inputs = new ArrayList<>();
    for (Map.Entry<Ingredient, Integer> entry : map.entrySet()) {
      inputs.add(items(entry.getKey(), entry.getValue()));
    }
    return inputs;
  }

  public static MachineDisplay alloySmelting(AlloySmeltingRecipe recipe) {
    return new MachineDisplay(ReiCategories.ALLOY_SMELTING, mapped(recipe.getIngredientMap()),
        List.of(item(recipe.getResultItem())), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience()));
  }

  public static MachineDisplay circuitAssembling(CircuitAssemblingRecipe recipe) {
    return new MachineDisplay(ReiCategories.CIRCUIT_ASSEMBLING, mapped(recipe.getIngredientMap()),
        List.of(item(recipe.getResultItem())), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), 0.0F));
  }

  public static MachineDisplay recycling(RecyclingRecipe recipe) {
    List<ItemStack> inputs = recipe.isSpecific()
        ? List.of(recipe.getIngredient().getItems())
        : ForgeRegistries.ITEMS.getValues().stream()
            .map(Item::getDefaultInstance)
            .filter(stack -> !stack.isEmpty() && !recipe.isExcluded(stack))
            .toList();
    return new MachineDisplay(ReiCategories.RECYCLING, List.of(EntryIngredients.ofItemStacks(inputs)),
        List.of(chance(recipe.getResultItem(), recipe.getChance())), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), 0.0F), List.of(recipe.getChance()));
  }

  public static MachineDisplay fluidEnriching(FluidEnrichingRecipe recipe) {
    boolean dual = recipe.getCountedIngredient2().isPresent() || recipe.getFluidInput2().isPresent();
    List<EntryIngredient> inputs = new ArrayList<>();
    inputs.add(items(recipe.getIngredient(), recipe.getIngredientCount()));
    inputs.add(fluid(recipe.getFluidInput()));
    if (dual) {
      inputs.add(recipe.getCountedIngredient2().map(second -> items(second.ingredient(), second.count()))
          .orElse(EntryIngredient.empty()));
      inputs.add(recipe.getFluidInput2().map(ReiDisplays::fluid).orElse(EntryIngredient.empty()));
    }
    return new MachineDisplay(ReiCategories.FLUID_ENRICHING, inputs, List.of(fluid(recipe.getResult())),
        location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience()).dual(dual));
  }

  private static List<EntryIngredient> results(List<ItemStack> results) {
    List<EntryIngredient> outputs = new ArrayList<>();
    for (ItemStack stack : results) {
      if (!stack.isEmpty()) {
        outputs.add(item(stack));
      }
    }
    return outputs;
  }

  public static MachineDisplay oreWashing(OreWashingRecipe recipe) {
    List<EntryIngredient> inputs = new ArrayList<>();
    inputs.add(items(recipe.getIngredient(), recipe.getIngredientCount()));
    inputs.add(fluid(recipe.getFluidInput()));
    inputs.add(recipe.getAcidInput().map(ReiDisplays::fluid).orElse(EntryIngredient.empty()));
    return new MachineDisplay(ReiCategories.ORE_WASHING, inputs, results(recipe.getResults()), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience()));
  }

  public static MachineDisplay polymerizing(PolymerizingRecipe recipe) {
    return new MachineDisplay(ReiCategories.POLYMERIZING,
        List.of(fluid(recipe.getFluidInput()), items(recipe.getIngredient(), recipe.getIngredientCount())),
        List.of(item(recipe.getResult())), location(recipe),
        MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience()));
  }

  public static MachineDisplay uraniumCentrifuging(UraniumCentrifugingRecipe recipe) {
    return bonusDisplay(ReiCategories.URANIUM_CENTRIFUGING, recipe, recipe.getIngredient(),
        recipe.getIngredientCount(), recipe.getResultItem(), recipe.getBonusResult().firstResult(),
        recipe.getDuration(), recipe.getPowerCost(), recipe.getExperience());
  }

  public static MachineDisplay thermalCentrifuging(ThermalCentrifugingRecipe recipe) {
    return new MachineDisplay(ReiCategories.THERMAL_CENTRIFUGING,
        List.of(items(recipe.getIngredient(), recipe.getIngredientCount())), results(recipe.getResults()),
        location(recipe), MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(),
            recipe.getExperience()).temperature(recipe.getTemperature()));
  }

  public static MachineDisplay scanner(ScannerRecipe recipe) {
    return new MachineDisplay(ReiCategories.SCANNER, List.of(), List.of(items(recipe.getIngredient(), 1)),
        location(recipe), MachineDisplay.Info.of(recipe.getDuration(), recipe.getPowerCost(),
            recipe.getExperience()).matterCost(recipe.getMatterCost()).energyCost(recipe.getEnergyCost()));
  }

  public static MachineDisplay scrapBox(ScrapBoxRecipe recipe, float totalWeight) {
    return new MachineDisplay(ReiCategories.SCRAP_BOX, List.of(EntryIngredients.of(ModItems.SCRAP_BOX)),
        List.of(item(recipe.getResultItem())), location(recipe),
        MachineDisplay.Info.of(1, 0, 0.0F).chanceText(recipe.getDropChance(totalWeight) + " %"));
  }

  public static MachineDisplay rolling(RollingRecipe recipe) {
    return bonusDisplay(ReiCategories.ROLLING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), Optional.empty(), recipe.getDuration(), recipe.getPowerCost(),
        recipe.getExperience());
  }

  public static MachineDisplay cutting(CuttingRecipe recipe) {
    return bonusDisplay(ReiCategories.CUTTING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), Optional.empty(), recipe.getDuration(), recipe.getPowerCost(),
        recipe.getExperience());
  }

  public static MachineDisplay extruding(ExtrudingRecipe recipe) {
    return bonusDisplay(ReiCategories.EXTRUDING, recipe, recipe.getIngredient(), recipe.getIngredientCount(),
        recipe.getResultItem(), Optional.empty(), recipe.getDuration(), recipe.getPowerCost(),
        recipe.getExperience());
  }

  public static MachineDisplay fermenting() {
    return new MachineDisplay(ReiCategories.FERMENTING,
        List.of(EntryIngredients.of(ModItems.MUD_PILE, BlockEntityFermenter.MUD_PER_OP),
            fluid(ModFluids.BIOMASS.still(), BlockEntityFermenter.BIOMASS_PER_OP)),
        List.of(fluid(ModFluids.BIOGAS.still(), BlockEntityFermenter.BIOGAS_PER_OP)), Optional.empty(),
        MachineDisplay.Info.of(BlockEntityFermenter.DURATION_TICKS, ModConfig.server().fermenter_tick_usage, 0.0F));
  }

  public static MachineDisplay distilling() {
    return new MachineDisplay(ReiCategories.DISTILLING,
        List.of(fluid(ModFluids.OIL.still(), BlockEntityDistillery.OIL_PER_OP),
            fluid(ModFluids.SULFURIC_ACID.still(), BlockEntityDistillery.ACID_PER_OP),
            fluid(Fluids.WATER, BlockEntityDistillery.WATER_PER_OP)),
        List.of(fluid(ModFluids.FUEL.still(), BlockEntityDistillery.FUEL_PER_OP),
            EntryIngredients.of(ModItems.SULFUR_DUST)),
        Optional.empty(),
        MachineDisplay.Info.of(BlockEntityDistillery.DURATION_TICKS, BlockEntityDistillery.POWER_PER_TICK, 0.0F)
            .chance(BlockEntityDistillery.SULFUR_CHANCE));
  }

  public static List<MachineDisplay> matterFabricating() {
    int matter = ModConfig.server().matter_fabricator_produce_run;
    int baseCost = BlockEntityMatterFabricator.PROGRESS_TARGET;
    int amplifiedCost = Mth.ceil(baseCost / (float) (1 + BlockEntityMatterFabricator.AMPLIFIER_BONUS));
    int scrapCount = Mth.ceil(amplifiedCost / (float) BlockEntityMatterFabricator.SCRAP_AMPLIFIER);
    int scrapBoxCount = Mth.ceil(amplifiedCost / (float) BlockEntityMatterFabricator.SCRAP_BOX_AMPLIFIER);
    EntryIngredient output = fluid(ModFluids.MATTER.still(), matter);
    MachineDisplay.Info info = MachineDisplay.Info.of(200, 0, 0.0F).energyCost(amplifiedCost).matterCost(matter);
    return List.of(
        new MachineDisplay(ReiCategories.MATTER_FABRICATING,
            List.of(EntryIngredients.of(ModItems.SCRAP, scrapCount)), List.of(output), Optional.empty(), info),
        new MachineDisplay(ReiCategories.MATTER_FABRICATING,
            List.of(EntryIngredients.of(ModItems.SCRAP_BOX, scrapBoxCount)), List.of(output), Optional.empty(),
            info));
  }
}
