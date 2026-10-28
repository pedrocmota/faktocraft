package com.faktocraft.gametest.parity;

import com.faktocraft.Faktocraft;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Faktocraft.MODID)
@PrefixGameTestTemplate(false)
public class ParitySnapshotGameTest {

  public static final String PROPERTY = "faktocraft.paritySnapshot";

  private static void clearArea(GameTestHelper helper) {
    for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 11, 9, 11)) {
      BlockState state = helper.getBlockState(pos);
      if (!state.isAir() && !state.is(Blocks.STRUCTURE_BLOCK)) {
        helper.setBlock(pos, Blocks.AIR.defaultBlockState());
      }
    }
    AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
    for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
      entity.discard();
    }
  }

  @GameTest(template = "gametest_platform", timeoutTicks = 200)
  public static void buildParitySnapshot(GameTestHelper helper) {
    String target = System.getProperty(PROPERTY, "").trim();
    if (target.isEmpty()) {
      helper.succeed();
      return;
    }
    ParitySnapshot snapshot = new ParitySnapshot(helper, new BlockPos(1, 1, 1));
    JsonObject out = new JsonObject();
    for (String category : ParitySnapshot.CATEGORIES) {
      clearArea(helper);
      long started = System.nanoTime();
      out.add(category, snapshot.category(category));
      Faktocraft.LOGGER.info("[ParitySnapshot] {} took {} ms", category, (System.nanoTime() - started) / 1_000_000);
    }
    Path path = Path.of(target);
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.writeString(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Faktocraft.LOGGER.info("[ParitySnapshot] written to {}", path.toAbsolutePath());
    helper.succeed();
  }
}
