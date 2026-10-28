package com.faktocraft.integration.jei.category.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.enums.EnumLang;
import com.faktocraft.common.recipe.impl.RecyclingRecipe;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.GuiUtil;
import net.minecraftforge.registries.ForgeRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import static com.faktocraft.common.util.Constants.JEI;
import static com.faktocraft.common.util.Constants.PROCESS;

public class RecyclingCategory extends AbstractRecipeCategory<RecyclingRecipe> {

  public static final ResourceLocation UID = new ResourceLocation(Faktocraft.MODID, "recycling");
  public static final RecipeType<RecyclingRecipe> TYPE = new RecipeType<>(UID, RecyclingRecipe.class);

  private final LoadingCache<Integer, IDrawableAnimated> progress;
  private final IDrawableAnimated energy;

  public RecyclingCategory(IGuiHelper guiHelper) {
    super(
        TYPE,
        "recycling",
        guiHelper,
        guiHelper.createDrawable(JEI, 117, 0, 114, 54),
        guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(M2Registry.RECYCLER)));
    this.progress = CacheBuilder.newBuilder().build(CacheLoader.from(
        (Integer duration) -> guiHelper.drawableBuilder(PROCESS, 25, 85, 24, 16).buildAnimated(duration,
            IDrawableAnimated.StartDirection.LEFT, false)));
    this.energy = createEnergyDrawable();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, RecyclingRecipe recipe, IFocusGroup focuses) {
    List<ItemStack> inputs = recipe.isSpecific()
        ? List.of(recipe.getIngredient().getItems())
        : ForgeRegistries.ITEMS.getValues().stream()
            .map(Item::getDefaultInstance)
            .filter(stack -> !stack.isEmpty() && !recipe.isExcluded(stack))
            .toList();

    builder.addSlot(RecipeIngredientRole.INPUT, 9, 19).addItemStacks(inputs);
    markChance(builder.addSlot(RecipeIngredientRole.OUTPUT, halfX + 8, 19).addItemStack(recipe.getResultItem()),
        recipe.getChance() < 1.0F)
        .addRichTooltipCallback((view, tooltip) -> tooltip.add(
            Component.translatable(EnumLang.CHANCE.getTranslationKey(),
                Component.literal((Math.round(recipe.getChance() * 100.0) / 100.0) + "%")
                    .withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.DARK_GRAY)));
  }

  @Override
  public void draw(RecyclingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics,
      double mouseX, double mouseY) {
    super.draw(recipe, recipeSlotsView, graphics, mouseX, mouseY);

    this.progress.getUnchecked(recipe.getDuration()).draw(graphics, halfX - 24, 19);
    this.energy.draw(graphics, halfX + 39, 7);

    GuiUtil.renderScaled(graphics, recipe.getPowerCost() + " IE/T", 0, 48, 0.75f, 0x7E7E7E, false);
  }
}
