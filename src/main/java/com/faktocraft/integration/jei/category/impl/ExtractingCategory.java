package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.enums.EnumLang;
import com.faktocraft.common.recipe.ChanceResult;
import com.faktocraft.common.recipe.impl.ExtractingRecipe;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.integration.jei.category.AbstractRecipeCategory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import static com.faktocraft.common.util.Constants.JEI;
import static com.faktocraft.common.util.Constants.PROCESS;

public class ExtractingCategory extends AbstractRecipeCategory<ExtractingRecipe> {

  public static final Identifier UID = Identifier.fromNamespaceAndPath(Faktocraft.MODID, "extracting");
  public static final IRecipeType<ExtractingRecipe> TYPE = IRecipeType.create(UID, ExtractingRecipe.class);

  private final LoadingCache<Integer, IDrawableAnimated> progress;
  private final IDrawableAnimated energy;

  public ExtractingCategory(IGuiHelper guiHelper) {
    super(
        TYPE,
        "extracting",
        guiHelper,
        guiHelper.createDrawable(JEI, 0, 110, 114, 54),
        guiHelper.createDrawableItemStack(new ItemStack(M2Registry.EXTRACTOR)));
    this.progress = CacheBuilder.newBuilder().build(CacheLoader.from(
        (Integer duration) -> guiHelper.drawableBuilder(PROCESS, 25, 51, 24, 16).buildAnimated(duration,
            IDrawableAnimated.StartDirection.LEFT, false)));
    this.energy = createEnergyDrawable();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, ExtractingRecipe recipe, IFocusGroup focuses) {
    builder.addSlot(RecipeIngredientRole.INPUT, 9, 19)
        .addItemStacks(stacks(recipe.getIngredient(), recipe.getIngredientCount()));
    builder.addSlot(RecipeIngredientRole.OUTPUT, halfX + 8, 6).add(recipe.getResultItem());

    Optional<ChanceResult> bonus = recipe.getBonusResult().firstResult();
    if (bonus.isPresent()) {
      ChanceResult chanceResult = bonus.get();
      markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, halfX + 8, 29).add(chanceResult.stack()),
          chanceResult.chance() < 100.0F)
              .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                  Component.translatable(EnumLang.CHANCE.getTranslationKey(),
                      (Math.round(chanceResult.chance() * 100.0) / 100.0) + "%").withStyle(ChatFormatting.BLUE)));
    }
  }

  @Override
  public void draw(ExtractingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
      double mouseX, double mouseY) {
    super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

    this.progress.getUnchecked(recipe.getDuration()).draw(graphics, halfX - 24, 19);
    this.energy.draw(graphics, halfX + 39, 7);

    if (recipe.getExperience() > 0) {
      GuiUtil.renderScaled(graphics, recipe.getExperience() + " XP", 0, 0, 0.75f, 0x7E7E7E, false);
    }

    GuiUtil.renderScaled(graphics, recipe.getPowerCost() + " IE/T", 0, 48, 0.75f, 0x7E7E7E, false);
  }
}
