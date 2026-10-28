package com.faktocraft.gametest.world;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.BlockIridiumOre;
import com.faktocraft.common.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class IridiumOreGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos ORE = new BlockPos(2, 1, 2);

  private static void check(GameTestHelper helper, Block ore) {
    BlockPos orePos = helper.absolutePos(ORE);
    BlockPos lit = ORE.east();
    for (int x = 0; x <= 4; x++) {
      for (int y = 0; y <= 3; y++) {
        for (int z = 0; z <= 4; z++) {
          BlockPos pos = new BlockPos(x, y, z);
          if (!pos.equals(ORE) && !pos.equals(lit)) {
            helper.setBlock(pos, Blocks.STONE.defaultBlockState());
          }
        }
      }
    }
    helper.setBlock(lit, Blocks.AIR.defaultBlockState());
    helper.setBlock(ORE, ore.defaultBlockState());
    int emission = ore.defaultBlockState().getLightEmission(helper.getLevel(), orePos);
    if (emission != BlockIridiumOre.LIGHT_LEVEL) {
      helper.fail(ore + " emits " + emission + ", expected " + BlockIridiumOre.LIGHT_LEVEL);
      return;
    }
    int expected = BlockIridiumOre.LIGHT_LEVEL - 1;
    helper.runAfterDelay(5, () -> {
      int beside = helper.getLevel().getBrightness(LightLayer.BLOCK, orePos.east());
      if (beside != expected) {
        helper.fail("block light beside " + ore + " is " + beside + ", expected " + expected);
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE)
  public static void iridiumOreGlowsFaintly(GameTestHelper helper) {
    check(helper, ModBlocks.IRIDIUM_ORE);
  }

  @GameTest(template = TEMPLATE)
  public static void deepslateIridiumOreGlowsFaintly(GameTestHelper helper) {
    check(helper, ModBlocks.DEEPSLATE_IRIDIUM_ORE);
  }
}
