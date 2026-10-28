package com.faktocraft.integration.rei;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.logistics.BlockEntityCraftPipe;
import com.faktocraft.common.block.impl.logistics.MenuCraftPipe;
import com.faktocraft.common.block.impl.logistics.MenuRecipePipe;
import com.faktocraft.common.block.impl.logistics.RecipeImport;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketCraftPattern;
import com.faktocraft.common.network.packet.PacketRecipePipeFill;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class PipeTransfers {

  private PipeTransfers() {
  }

  private static ItemStack firstItem(EntryIngredient ingredient) {
    for (EntryStack<?> stack : ingredient) {
      if (stack.getType() == VanillaEntryTypes.ITEM) {
        ItemStack item = stack.castValue();
        if (!item.isEmpty()) {
          return item.copyWithCount(1);
        }
      }
    }
    return ItemStack.EMPTY;
  }

  private static boolean isCrafting(Display display) {
    CategoryIdentifier<?> id = display.getCategoryIdentifier();
    return id.equals(BuiltinPlugin.CRAFTING);
  }

  private static void addSlots(List<RecipeImport.Slot> slots, Display display, boolean input) {
    List<EntryIngredient> entries = input ? display.getInputEntries() : display.getOutputEntries();
    for (int index = 0; index < entries.size(); index++) {
      EntryIngredient ingredient = entries.get(index);
      List<ItemStack> items = new ArrayList<>();
      for (EntryStack<?> stack : ingredient) {
        if (items.size() >= RecipeImport.SCAN_LIMIT) {
          break;
        }
        if (stack.getType() == VanillaEntryTypes.ITEM) {
          ItemStack item = stack.castValue();
          if (!item.isEmpty()) {
            items.add(item);
          }
        }
      }
      boolean chance = !input && display instanceof MachineDisplay machine && machine.chanceOutput(index);
      slots.add(new RecipeImport.Slot(input, items, ItemStack.EMPTY, chance,
          items.isEmpty() && !ingredient.isEmpty()));
    }
  }

  private static RecipeImport.Plan plan(Display display) {
    List<RecipeImport.Slot> slots = new ArrayList<>();
    addSlots(slots, display, true);
    addSlots(slots, display, false);
    boolean brewing = display.getCategoryIdentifier().equals(BuiltinPlugin.BREWING);
    return RecipeImport.plan(slots, brewing ? RecipeImport.BREWING_BATCH : 1);
  }

  public static final TransferHandler CRAFT_PIPE_PATTERN = context -> {
    if (!(context.getMenu() instanceof MenuCraftPipe menu)
        || !(context.getDisplay() instanceof DefaultCraftingDisplay<?> display)) {
      return TransferHandler.Result.createNotApplicable();
    }
    if (!context.isActuallyCrafting()) {
      return TransferHandler.Result.createSuccessful();
    }
    List<EntryIngredient> grid = display.getOrganisedInputEntries(3, 3);
    List<ItemStack> stacks = new ArrayList<>(BlockEntityCraftPipe.PATTERN_SIZE);
    for (int i = 0; i < BlockEntityCraftPipe.PATTERN_SIZE; i++) {
      stacks.add(i < grid.size() ? firstItem(grid.get(i)) : ItemStack.EMPTY);
    }
    ModNetworking.sendToServer(new PacketCraftPattern(menu.getPipePos(), stacks));
    return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
  };

  public static final TransferHandler RECIPE_PIPE = context -> {
    if (!(context.getMenu() instanceof MenuRecipePipe menu)) {
      return TransferHandler.Result.createNotApplicable();
    }
    Display display = context.getDisplay();
    if (isCrafting(display)) {
      return TransferHandler.Result.createFailed(
          Component.translatable("logistics." + Faktocraft.MODID + ".craft.transfer_crafting"));
    }
    RecipeImport.Plan plan = plan(display);
    Component problem = RecipeImport.problem(plan);
    if (problem != null) {
      return TransferHandler.Result.createFailed(problem);
    }
    if (!context.isActuallyCrafting()) {
      TransferHandler.Result preview = TransferHandler.Result.createSuccessful();
      for (Component line : RecipeImport.describe(plan)) {
        preview = preview.tooltip(line);
      }
      return preview;
    }
    ModNetworking.sendToServer(new PacketRecipePipeFill(menu.getPipePos(), plan.inputs(), plan.outputs(),
        plan.batch()));
    return TransferHandler.Result.createSuccessful().blocksFurtherHandling();
  };
}
