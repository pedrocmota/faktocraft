package com.faktocraft.integration.jei.category;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.logistics.RecipeImport;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import java.util.ArrayList;
import java.util.List;
import static com.faktocraft.common.util.Constants.JEI;

public abstract class AbstractRecipeCategory<T> extends mezz.jei.api.recipe.category.AbstractRecipeCategory<T> {
  protected final IGuiHelper guiHelper;
  protected final IDrawableStatic background;

  protected final int halfX;

  protected static String key(String name) {
    return "jei." + Faktocraft.MODID + "." + name;
  }

  protected AbstractRecipeCategory(IRecipeType<T> recipeType, String unlocalizedName, IGuiHelper guiHelper,
      IDrawableStatic background, IDrawable icon) {
    super(recipeType, Component.translatable(key(unlocalizedName)), icon, background.getWidth(),
        background.getHeight());
    this.guiHelper = guiHelper;
    this.background = background;
    this.halfX = background.getWidth() / 2;
  }

  @Override
  public void draw(T recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor graphics, double mouseX,
      double mouseY) {
    background.draw(graphics);
  }

  protected static IRecipeSlotBuilder markChance(IRecipeSlotBuilder slot, boolean chance) {
    return chance ? slot.setSlotName(RecipeImport.CHANCE_SLOT) : slot;
  }

  protected IDrawableAnimated createEnergyDrawable() {
    return guiHelper.drawableBuilder(JEI, 249, 0, 7, 37).buildAnimated(200, IDrawableAnimated.StartDirection.TOP, true);
  }

  protected static List<ItemStack> stacks(Ingredient ingredient, int count) {
    List<ItemStack> result = new ArrayList<>();
    for (ItemStack stack : ingredient.display().resolveForStacks(displayContext())) {
      if (!stack.isEmpty()) {
        result.add(stack.copyWithCount(count));
      }
    }
    return result;
  }

  private static ContextMap displayContext() {
    ClientLevel level = Minecraft.getInstance().level;
    return level != null ? SlotDisplayContext.fromLevel(level)
        : ContextMap.builder().buildAndValidate(SlotDisplayContext.CONTEXT);
  }
}
