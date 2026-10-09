package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.recipe.impl.ScrapBoxRecipe;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.integration.jei.category.AbstractRecipeCategory;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import static com.faktocraft.common.util.Constants.JEI;

public class ScrapBoxCategory extends AbstractRecipeCategory<ScrapBoxRecipe> {

  public static final Identifier UID = Identifier.fromNamespaceAndPath(Faktocraft.MODID, "scrap_box");
  public static final IRecipeType<ScrapBoxRecipe> TYPE = IRecipeType.create(UID, ScrapBoxRecipe.class);

  private static volatile float totalWeight = 0.0F;

  public static void setTotalWeight(float weight) {
    totalWeight = weight;
  }

  public ScrapBoxCategory(IGuiHelper guiHelper) {
    super(
        TYPE,
        "scrap_box",
        guiHelper,
        guiHelper.createDrawable(JEI, 0, 220, 92, 28),
        guiHelper.createDrawableItemStack(new ItemStack(ModItems.SCRAP_BOX)));
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, ScrapBoxRecipe recipe, IFocusGroup focuses) {
    builder.addSlot(RecipeIngredientRole.INPUT, 9, 6).add(new ItemStack(ModItems.SCRAP_BOX));
    markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, 65, 6).add(recipe.getResultItem()), true);
  }

  @Override
  public void draw(ScrapBoxRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
      double mouseX, double mouseY) {
    super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

    GuiUtil.renderScaled(graphics, recipe.getDropChance(totalWeight) + " %", 33, 23, 0.75f, 0x7E7E7E, false);
  }
}
