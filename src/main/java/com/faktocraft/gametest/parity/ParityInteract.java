package com.faktocraft.gametest.parity;

import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.GameType;
import com.faktocraft.gametest.gui.GuiProbes;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ParityInteract {

  private static final int RADIUS = 2;
  private static final String[] ON_TARGETS = { "minecraft:stone", "minecraft:dirt", "minecraft:grass_block",
      "minecraft:oak_log", "minecraft:campfire", "faktocraft:rubber_log#wet", "faktocraft:rubber_log",
      "minecraft:chest", "faktocraft:crusher", "faktocraft:tank", "faktocraft:copper_cable", "faktocraft:battery_box",
      "faktocraft:generator" };
  private static final String[] AIR_TARGETS = { "air", "minecraft:water", "minecraft:lava" };
  private static final String[] CELL_FLUIDS = { "minecraft:water", "minecraft:lava" };

  private final GameTestHelper helper;
  private final ServerLevel level;
  private final BlockPos rel;
  private final BlockPos abs;
  private final Map<BlockPos, BlockState> baseline = new LinkedHashMap<>();
  private ServerPlayer player;

  public ParityInteract(GameTestHelper helper, BlockPos rel) {
    this.helper = helper;
    this.level = helper.getLevel();
    this.rel = rel;
    this.abs = helper.absolutePos(rel);
  }

  private ServerPlayer createPlayer() {
    ServerPlayer created = new ServerPlayer(level.getServer(), level,
        new GameProfile(UUID.randomUUID(), "parity-player"));
    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    new EmbeddedChannel(connection);
    level.getServer().getPlayerList().placeNewPlayer(connection, created);
    created.setGameMode(GameType.SURVIVAL);
    return created;
  }

  private void removePlayer() {
    level.getServer().getPlayerList().remove(player);
  }

  private static String result(InteractionResult result) {
    if (result == InteractionResult.FAIL) {
      return "fail";
    }
    return result.consumesAction() ? "act" : "pass";
  }

  private BlockState targetState(String target) {
    if (target.equals("air")) {
      return Blocks.AIR.defaultBlockState();
    }
    String id = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
    Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
    BlockState state = ParitySnapshot.placementState(block);
    if (target.endsWith("#wet")) {
      state = setFlag(state, "wet");
    }
    return state;
  }

  @SuppressWarnings("rawtypes")
  private static BlockState setFlag(BlockState state, String name) {
    for (Property property : state.getProperties()) {
      if (property.getName().equals(name) && property instanceof BooleanProperty flag) {
        return state.setValue(flag, true);
      }
    }
    return state;
  }

  private void snapshotRegion() {
    baseline.clear();
    for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-RADIUS, -1, -RADIUS), abs.offset(RADIUS, 6, RADIUS))) {
      baseline.put(pos.immutable(), level.getBlockState(pos));
    }
  }

  private String changesAndRestore() {
    TreeSet<String> changes = new TreeSet<>();
    for (Map.Entry<BlockPos, BlockState> entry : baseline.entrySet()) {
      BlockState now = level.getBlockState(entry.getKey());
      if (now != entry.getValue()) {
        BlockPos d = entry.getKey().subtract(abs);
        changes.add(d.getX() + "," + d.getY() + "," + d.getZ() + "=" + ParitySnapshot.blockId(now.getBlock()) + "["
            + ParityBehavior.props(now) + "]");
      }
    }
    for (Map.Entry<BlockPos, BlockState> entry : baseline.entrySet()) {
      if (level.getBlockState(entry.getKey()) != entry.getValue()) {
        level.setBlock(entry.getKey(), entry.getValue(), 3);
      }
    }
    return String.join(" ", changes);
  }

  private String drops() {
    TreeSet<String> out = new TreeSet<>();
    AABB box = new AABB(abs).inflate(RADIUS + 2);
    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
      out.add(ParitySnapshot.stack(item.getItem()));
      item.discard();
    }
    int xp = 0;
    for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
      xp += orb.getValue();
      orb.discard();
    }
    if (xp > 0) {
      out.add("xp=" + xp);
    }
    for (Entity other : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
      out.add("entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()));
      other.discard();
    }
    return String.join(" ", out);
  }

  private String inventory() {
    List<String> out = new ArrayList<>();
    var inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      if (i == inventory.selected) {
        continue;
      }
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        out.add(ParitySnapshot.stack(stack));
      }
    }
    out.sort(null);
    return String.join(" ", out);
  }

  private String effects() {
    TreeSet<String> out = new TreeSet<>();
    for (MobEffectInstance effect : player.getActiveEffects()) {
      out.add(String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect())));
    }
    return String.join(" ", out);
  }

  private void resetPlayer(boolean sneak, double x, double y, double z, float yaw, float pitch) {
    player.getInventory().clearContent();
    player.removeAllEffects();
    player.setHealth(player.getMaxHealth());
    player.getFoodData().setFoodLevel(10);
    player.setShiftKeyDown(sneak);
    player.moveTo(x, y, z, yaw, pitch);
    player.setYHeadRot(yaw);
  }

  private void prime() {
    if (level.getBlockEntity(abs) instanceof FaktocraftBlockEntity machine && machine.hasEnergy()) {
      if (machine.hasBatteryDock() && machine.getBatteryStackHandler() != null) {
        machine.getBatteryStackHandler().setStackInSlot(0, new ItemStack(
            BuiltInRegistries.ITEM.get(new ResourceLocation("faktocraft:advanced_capacitor"))));
      }
      machine.getEnergyStorage().setEnergy(Math.min(machine.getEnergyStorage().maxEnergy(), 1234));
    }
  }

  private String interact(ItemStack stack, String target, boolean sneak, boolean air) {
    return interact(stack, target, sneak, air, false);
  }

  private String interact(ItemStack stack, String target, boolean sneak, boolean air, boolean destroy) {
    BlockState state = targetState(target);
    boolean fluid = !state.getFluidState().isEmpty();
    if (fluid) {
      helper.setBlock(rel.below(), Blocks.STONE.defaultBlockState());
    }
    helper.setBlock(rel, state);
    if (destroy) {
      prime();
    }
    snapshotRegion();
    long seed = (ParitySnapshot.stackKey(stack) + target + sneak).hashCode();
    level.getRandom().setSeed(seed);
    player.getRandom().setSeed(seed);
    ItemStack held = stack.copy();
    if (air) {
      resetPlayer(sneak, abs.getX() + 0.5, abs.getY() + 1.2, abs.getZ() + 0.5, 0, target.equals("air") ? 0 : 90);
    } else {
      resetPlayer(sneak, abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5, 180, 45);
    }
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    String res;
    try {
      InteractionResult result;
      if (destroy) {
        result = player.gameMode.destroyBlock(abs) ? InteractionResult.SUCCESS : InteractionResult.PASS;
      } else if (air) {
        result = player.gameMode.useItem(player, level, held, InteractionHand.MAIN_HAND);
      } else {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        result = player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
      }
      res = result(result);
    } catch (Throwable e) {
      res = "!" + e.getClass().getSimpleName();
    }
    String after = ParitySnapshot.stack(player.getMainHandItem());
    String blocks = changesAndRestore();
    String dropped = drops();
    String inv = inventory();
    String fx = effects();
    int food = player.getFoodData().getFoodLevel();
    boolean heldSame = ItemStack.matches(player.getMainHandItem(), stack);
    player.stopUsingItem();
    player.getInventory().clearContent();
    helper.setBlock(rel, Blocks.AIR.defaultBlockState());
    if (fluid) {
      helper.setBlock(rel.below(), Blocks.AIR.defaultBlockState());
    }
    if (res.equals("pass") && blocks.isEmpty() && dropped.isEmpty() && inv.isEmpty() && fx.isEmpty() && food == 10
        && heldSame) {
      return null;
    }
    StringBuilder out = new StringBuilder(res);
    out.append(" | held ").append(heldSame ? "same" : after);
    if (!blocks.isEmpty()) {
      out.append(" | blocks ").append(blocks);
    }
    if (!dropped.isEmpty()) {
      out.append(" | drops ").append(dropped);
    }
    if (!inv.isEmpty()) {
      out.append(" | inv ").append(inv);
    }
    if (!fx.isEmpty()) {
      out.append(" | effects ").append(fx);
    }
    if (food != 10) {
      out.append(" | food ").append(food);
    }
    return out.toString();
  }

  private static boolean wanted(GuiProbes.Probe probe) {
    String key = probe.key();
    int fluid = key.indexOf("#fluid=");
    if (fluid < 0) {
      return true;
    }
    String id = key.substring(fluid + "#fluid=".length());
    for (String allowed : CELL_FLUIDS) {
      if (allowed.equals(id)) {
        return true;
      }
    }
    return false;
  }

  public JsonObject interact() {
    JsonObject out = new JsonObject();
    player = createPlayer();
    try {
      for (GuiProbes.Probe probe : GuiProbes.all()) {
        ItemStack sample = probe.create();
        if (!GuiProbes.isMod(sample.getItem()) || !wanted(probe)) {
          continue;
        }
        JsonObject entry = new JsonObject();
        for (String target : ON_TARGETS) {
          for (boolean sneak : new boolean[] { false, true }) {
            String outcome = interact(probe.create(), target, sneak, false);
            if (outcome != null) {
              entry.addProperty(target + (sneak ? " sneak" : ""), outcome);
            }
          }
        }
        for (String target : ON_TARGETS) {
          String outcome = interact(probe.create(), target, false, false, true);
          if (outcome != null) {
            entry.addProperty("break " + target, outcome);
          }
        }
        for (String target : AIR_TARGETS) {
          String outcome = interact(probe.create(), target, false, true);
          if (outcome != null) {
            entry.addProperty("use " + target, outcome);
          }
        }
        if (entry.size() > 0) {
          out.add(probe.key(), entry);
        }
      }
    } finally {
      removePlayer();
    }
    return out;
  }
}
