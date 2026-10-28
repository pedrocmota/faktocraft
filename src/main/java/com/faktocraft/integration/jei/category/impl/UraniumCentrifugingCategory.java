package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.enums.EnumLang;
import com.faktocraft.common.recipe.ChanceResult;
import com.faktocraft.common.recipe.impl.UraniumCentrifugingRecipe;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.integration.jei.category.AbstractRecipeCategory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import static com.faktocraft.common.util.Constants.JEI;
import static com.faktocraft.common.util.Constants.PROCESS;

public class UraniumCentrifugingCategory extends AbstractRecipeCategory<UraniumCentrifugingRecipe> {

  public static final ResourceLocation UID = new ResourceLocation(Faktocraft.MODID, "uranium_centrifuging");
  public static final RecipeType<UraniumCentrifugingRecipe> TYPE = new RecipeType<>(UID,
      UraniumCentrifugingRecipe.class);

  private final LoadingCache<Integer, IDrawableAnimated> progress;
  private final IDrawableAnimated energy;

  public UraniumCentrifugingCategory(IGuiHelper guiHelper) {
    super(
        TYPE,
        "uranium_centrifuging",
        guiHelper,
        guiHelper.createDrawable(JEI, 0, 0, 114, 54),
        guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(M3Registry.URANIUM_CENTRIFUGE)));
    this.progress = CacheBuilder.newBuilder().build(CacheLoader.from(
        (Integer duration) -> guiHelper.drawableBuilder(PROCESS, 25, 34, 24, 16).buildAnimated(duration,
            IDrawableAnimated.StartDirection.LEFT, false)));
    this.energy = createEnergyDrawable();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, UraniumCentrifugingRecipe recipe, IFocusGroup focuses) {
    builder.addSlot(RecipeIngredientRole.INPUT, 9, 19)
        .addItemStacks(stacks(recipe.getIngredient(), recipe.getIngredientCount()));
    builder.addSlot(RecipeIngredientRole.OUTPUT, halfX + 8, 6).addItemStack(recipe.getResultItem());

    List<ChanceResult> bonuses = recipe.getBonusResult().getResults();
    if (!bonuses.isEmpty()) {
      markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, halfX + 8, 29)
          .addItemStacks(bonuses.stream().map(ChanceResult::stack).toList()),
          bonuses.size() > 1 || bonuses.get(0).chance() < 100.0F)
          .addRichTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(shown -> {
            for (ChanceResult chanceResult : bonuses) {
              if (ItemStack.isSameItemSameTags(shown, chanceResult.result())) {
                tooltip.add(Component.translatable(EnumLang.CHANCE.getTranslationKey(),
                    Component.literal((Math.round(chanceResult.chance() * 100.0) / 100.0) + "%")
                        .withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.DARK_GRAY));
                return;
              }
            }
          }));
    }
  }

  @Override
  public void draw(UraniumCentrifugingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
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
