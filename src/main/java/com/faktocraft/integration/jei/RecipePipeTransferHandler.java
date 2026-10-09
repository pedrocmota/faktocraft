package com.faktocraft.integration.jei;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.logistics.LogisticsRegistry;
import com.faktocraft.common.block.impl.logistics.MenuRecipePipe;
import com.faktocraft.common.block.impl.logistics.RecipeImport;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketRecipePipeFill;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipePipeTransferHandler implements IUniversalRecipeTransferHandler<MenuRecipePipe> {

  private final IRecipeTransferHandlerHelper helper;

  public RecipePipeTransferHandler(IRecipeTransferHandlerHelper helper) {
    this.helper = helper;
  }

  @Override
  public Class<? extends MenuRecipePipe> getContainerClass() {
    return MenuRecipePipe.class;
  }

  @Override
  public Optional<MenuType<MenuRecipePipe>> getMenuType() {
    return Optional.of(LogisticsRegistry.RECIPE_PIPE_MENU);
  }

  @Nullable
  @Override
  public IRecipeTransferError transferRecipe(MenuRecipePipe menu, Object recipe, IRecipeSlotsView recipeSlots,
      Player player, boolean maxTransfer, boolean doTransfer) {
    if (recipe instanceof CraftingRecipe
        || (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe)) {
      return helper.createUserErrorWithTooltip(
          Component.translatable("logistics." + Faktocraft.MODID + ".craft.transfer_crafting"));
    }
    RecipeImport.Plan plan = RecipeImport.plan(slots(recipeSlots),
        recipe instanceof IJeiBrewingRecipe ? RecipeImport.BREWING_BATCH : 1);
    Component problem = RecipeImport.problem(plan);
    if (problem != null) {
      return helper.createUserErrorWithTooltip(problem);
    }
    if (!doTransfer) {
      return new Preview(RecipeImport.describe(plan), !plan.notes().isEmpty());
    }
    ModNetworking.sendToServer(new PacketRecipePipeFill(menu.getPipePos(), plan.inputs(), plan.outputs(),
        plan.batch()));
    return null;
  }

  private static List<RecipeImport.Slot> slots(IRecipeSlotsView recipeSlots) {
    List<RecipeImport.Slot> slots = new ArrayList<>();
    for (IRecipeSlotView view : recipeSlots.getSlotViews()) {
      RecipeIngredientRole role = view.getRole();
      if (role != RecipeIngredientRole.INPUT && role != RecipeIngredientRole.OUTPUT) {
        continue;
      }
      List<ItemStack> items = view.getItemStacks().limit(RecipeImport.SCAN_LIMIT).toList();
      slots.add(new RecipeImport.Slot(role == RecipeIngredientRole.INPUT, items,
          view.getDisplayedItemStack().orElse(ItemStack.EMPTY),
          view.getSlotName().filter(RecipeImport.CHANCE_SLOT::equals).isPresent(),
          items.isEmpty() && !view.isEmpty()));
    }
    return slots;
  }

  private record Preview(List<Component> lines, boolean warn) implements IRecipeTransferError {

    @Override
    public Type getType() {
      return Type.COSMETIC;
    }

    @Override
    public int getButtonHighlightColor() {
      return warn ? IRecipeTransferError.super.getButtonHighlightColor() : 0;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip) {
      tooltip.addAll(lines);
    }
  }
}
