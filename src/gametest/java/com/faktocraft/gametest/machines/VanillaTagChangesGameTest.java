package com.faktocraft.gametest.machines;

import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.item.impl.tools.ToolboxMenu;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.gametest.GameTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class VanillaTagChangesGameTest {

  private static final String TEMPLATE = "gametest_platform";

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void toolboxTakesVanillaTools(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TOOLBOX));
    ToolboxMenu menu = new ToolboxMenu(1, player.getInventory(), BlockPos.ZERO);
    Slot slot = menu.slots.get(0);
    List<String> refused = new ArrayList<>();
    for (Item tool : new Item[] { Items.WOODEN_PICKAXE, Items.STONE_AXE, Items.IRON_SHOVEL, Items.GOLDEN_HOE,
        Items.DIAMOND_SWORD, Items.NETHERITE_PICKAXE, Items.COPPER_AXE, Items.TRIDENT, Items.BRUSH, Items.SPYGLASS,
        ModItems.WRENCH }) {
      if (!slot.mayPlace(new ItemStack(tool))) {
        refused.add(tool.toString());
      }
    }
    if (!refused.isEmpty()) {
      helper.fail("the toolbox refuses " + refused);
      return;
    }
    if (slot.mayPlace(new ItemStack(Items.DIRT)) || slot.mayPlace(new ItemStack(ModItems.TOOLBOX))) {
      helper.fail("the toolbox takes dirt or another toolbox");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 100)
  public static void quarryMinesTheWholeDirtFamily(GameTestHelper helper) {
    List<String> skipped = new ArrayList<>();
    for (Block block : new Block[] { Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRASS_BLOCK,
        Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.MOSS_BLOCK, Blocks.OBSIDIAN,
        Blocks.STONE, Blocks.GRAVEL }) {
      if (!block.defaultBlockState().is(BlockEntityGantry.QUARRY_MINEABLE)) {
        skipped.add(block.toString());
      }
    }
    if (!skipped.isEmpty()) {
      helper.fail("the quarry skips " + skipped);
      return;
    }
    if (Blocks.CRYING_OBSIDIAN.defaultBlockState().is(BlockEntityGantry.QUARRY_MINEABLE)) {
      helper.fail("the quarry mines crying obsidian, 1.20.1 left it");
      return;
    }
    helper.succeed();
  }
}
