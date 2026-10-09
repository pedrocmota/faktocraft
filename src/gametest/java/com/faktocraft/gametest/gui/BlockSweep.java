package com.faktocraft.gametest.gui;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.machines.nuclear_reactor.BlockNuclearReactor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockSweep {

  private static final int MAX_PROBLEMS_PER_BLOCK = 6;

  private BlockSweep() {
  }

  public static final class Report {
    private final String test;
    private final List<String> lines = new ArrayList<>();
    private final Map<String, Integer> perBlock = new LinkedHashMap<>();

    public Report(String test) {
      this.test = test;
    }

    public void add(Block block, String problem) {
      String id = GuiProbes.blockId(block);
      int seen = perBlock.merge(id, 1, Integer::sum);
      Faktocraft.LOGGER.warn("[Sweep] {} {}: {}", test, id, problem);
      if (seen <= MAX_PROBLEMS_PER_BLOCK) {
        lines.add(id + ": " + problem);
      } else if (seen == MAX_PROBLEMS_PER_BLOCK + 1) {
        lines.add(id + ": ... more problems in build/gametest.log ([Sweep] " + test + ")");
      }
    }

    public int problems(Block block) {
      return perBlock.getOrDefault(GuiProbes.blockId(block), 0);
    }

    public void finish(GameTestHelper helper) {
      if (lines.isEmpty()) {
        helper.succeed();
        return;
      }
      helper.fail(perBlock.size() + " block(s) with problems:\n" + String.join("\n", lines));
    }
  }

  @FunctionalInterface
  public interface BlockCheck {
    void run(Block block);
  }

  public static BlockState placementState(Block block) {
    if (block instanceof BlockNuclearReactor) {
      return block.defaultBlockState().setValue(BlockNuclearReactor.PART, BlockNuclearReactor.CORE_PART);
    }
    return block.defaultBlockState();
  }

  public static void forEachBlock(GameTestHelper helper, BlockPos rel, List<Block> blocks, Report report,
      BlockCheck check, Runnable after) {
    for (int i = 0; i < blocks.size(); i++) {
      Block block = blocks.get(i);
      helper.runAfterDelay(2L * i + 1, () -> helper.setBlock(rel, placementState(block)));
      helper.runAfterDelay(2L * i + 2, () -> {
        try {
          check.run(block);
        } catch (RuntimeException e) {
          report.add(block, "threw " + e);
          Faktocraft.LOGGER.warn("[Sweep] stack trace", e);
        } finally {
          helper.setBlock(rel, Blocks.AIR.defaultBlockState());
        }
      });
    }
    helper.runAfterDelay(2L * blocks.size() + 3, () -> {
      after.run();
      report.finish(helper);
    });
  }
}
