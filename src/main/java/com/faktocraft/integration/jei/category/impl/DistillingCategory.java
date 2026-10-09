package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.machines.distillery.DistilleryRegistry;
import com.faktocraft.common.util.GuiUtil;
import com.faktocraft.integration.jei.category.AbstractRecipeCategory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import static com.faktocraft.common.util.Constants.JEI_LARGE;
import static com.faktocraft.common.util.Constants.PROCESS;

public class DistillingCategory extends AbstractRecipeCategory<DistillingCategory.Entry> {

  public record Entry(FluidStack oil, FluidStack acid, FluidStack water, FluidStack fuel,
      ItemStack byproduct, float byproductChance, int duration, int powerCost) {
  }

  public static final Identifier UID = Identifier.fromNamespaceAndPath(Faktocraft.MODID, "distilling");
  public static final IRecipeType<Entry> TYPE = IRecipeType.create(UID, Entry.class);

  private final LoadingCache<Integer, IDrawableAnimated> progress;
  private final IDrawableAnimated energy;

  private final IDrawableStatic tankFrame;
  private final IDrawableStatic slotFrame;

  public DistillingCategory(IGuiHelper guiHelper) {
    super(
        TYPE,
        "distilling",
        guiHelper,
        guiHelper.createDrawable(JEI_LARGE, 0, 165, 152, 54),
        guiHelper.createDrawableItemStack(new ItemStack(DistilleryRegistry.DISTILLERY)));
    this.tankFrame = guiHelper.createDrawable(JEI_LARGE, 160, 165, 16, 37);
    this.slotFrame = guiHelper.createDrawable(JEI_LARGE, 180, 165, 18, 18);
    this.progress = CacheBuilder.newBuilder().build(CacheLoader.from(
        (Integer duration) -> guiHelper.drawableBuilder(PROCESS, 25, 0, 24, 16).buildAnimated(duration,
            IDrawableAnimated.StartDirection.LEFT, false)));
    this.energy = createEnergyDrawable();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, Entry recipe, IFocusGroup focuses) {
    builder.addSlot(RecipeIngredientRole.INPUT, 8, 12)
        .setFluidRenderer(recipe.oil().getAmount(), false, 8, 29)
        .add(recipe.oil().getFluid(), recipe.oil().getAmount());
    builder.addSlot(RecipeIngredientRole.INPUT, 26, 12)
        .setFluidRenderer(recipe.acid().getAmount(), false, 8, 29)
        .add(recipe.acid().getFluid(), recipe.acid().getAmount());
    builder.addSlot(RecipeIngredientRole.INPUT, 44, 12)
        .setFluidRenderer(recipe.water().getAmount(), false, 8, 29)
        .add(recipe.water().getFluid(), recipe.water().getAmount());

    builder.addSlot(RecipeIngredientRole.OUTPUT, 106, 12)
        .setFluidRenderer(recipe.fuel().getAmount(), false, 8, 29)
        .add(recipe.fuel().getFluid(), recipe.fuel().getAmount());

    markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, 74, 36).add(recipe.byproduct()),
        recipe.byproductChance() < 1.0F);
  }

  @Override
  public void draw(Entry recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics,
      double mouseX, double mouseY) {
    super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

    tankFrame.draw(graphics, 4, 8);
    tankFrame.draw(graphics, 22, 8);
    tankFrame.draw(graphics, 40, 8);
    slotFrame.draw(graphics, 73, 35);

    this.progress.getUnchecked(recipe.duration()).draw(graphics, halfX - 6, 19);
    this.energy.draw(graphics, halfX + 58, 7);

    GuiUtil.renderScaled(graphics, recipe.duration() / 20 + "s", 3, 1, 0.75f, 0x7E7E7E, false);
    GuiUtil.renderScaled(graphics, recipe.powerCost() + " IE/T", 3, 46, 0.75f, 0x7E7E7E, false);
    GuiUtil.renderScaled(graphics, (int) (recipe.byproductChance() * 100) + "%", 93, 41, 0.75f, 0x7E7E7E, false);
  }
}
