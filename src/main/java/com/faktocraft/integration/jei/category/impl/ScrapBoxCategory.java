package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.recipe.impl.ScrapBoxRecipe;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.integration.jei.category.AbstractRecipeCategory;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import static com.faktocraft.common.util.Constants.JEI;

public class ScrapBoxCategory extends AbstractRecipeCategory<ScrapBoxRecipe> {

  public static final ResourceLocation UID = new ResourceLocation(Faktocraft.MODID, "scrap_box");
  public static final RecipeType<ScrapBoxRecipe> TYPE = new RecipeType<>(UID, ScrapBoxRecipe.class);

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
        guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.SCRAP_BOX)));
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, ScrapBoxRecipe recipe, IFocusGroup focuses) {
    builder.addSlot(RecipeIngredientRole.INPUT, 9, 6).addItemStack(new ItemStack(ModItems.SCRAP_BOX));
    markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, 65, 6).addItemStack(recipe.getResultItem()), true);
  }

  @Override
  public void draw(ScrapBoxRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
      double mouseX, double mouseY) {
    super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

    GuiUtil.renderScaled(graphics, recipe.getDropChance(totalWeight) + " %", 33, 23, 0.75f, 0x7E7E7E, false);
  }
}
