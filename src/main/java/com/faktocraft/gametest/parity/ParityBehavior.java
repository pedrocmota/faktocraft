package com.faktocraft.gametest.parity;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.slot.FaktocraftSlot;
import com.faktocraft.common.enums.InventorySlotType;
import com.faktocraft.common.util.ItemStackHandler;
import com.faktocraft.gametest.gui.GuiProbes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

public final class ParityBehavior {

  public static final String[] CATEGORIES = { "states", "tabs", "tooltips", "fluids", "roundtrip", "process",
      "interact" };

  private static final int MAX_STATES = 48;
  private static final int PROCESS_PROBES = 4;
  private static final int PROCESS_TICKS = 800;
  private static final int GENERATOR_TICKS = 200;
  private static final int FEED_COUNT = 8;
  private static final int PRIMED_ENERGY = 1234;
  private static final String[] PREFERRED_FEEDS = { "minecraft:cobblestone", "minecraft:raw_iron",
      "minecraft:iron_ore", "minecraft:raw_copper", "minecraft:oak_log", "minecraft:sand", "minecraft:gravel",
      "minecraft:coal", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot", "minecraft:diamond",
      "minecraft:redstone", "minecraft:wheat", "minecraft:bone", "minecraft:stone", "minecraft:clay_ball",
      "minecraft:oak_planks", "minecraft:sugar_cane", "minecraft:netherrack", "minecraft:blaze_rod",
      "minecraft:white_wool", "minecraft:dirt", "minecraft:glass", "minecraft:water_bucket", "minecraft:lava_bucket",
      "minecraft:bucket" };
  private static final String DOCK_CAPACITOR = "faktocraft:advanced_capacitor";
  private static final String[][] LOOKS = { { "south", "0", "0" }, { "west", "90", "0" }, { "north", "180", "0" },
      { "east", "270", "0" }, { "down", "0", "90" }, { "up", "0", "-90" } };

  private final GameTestHelper helper;
  private final ServerLevel level;
  private final BlockPos rel;
  private final BlockPos abs;
  private final Player player;

  public ParityBehavior(GameTestHelper helper, BlockPos rel, Player player) {
    this.helper = helper;
    this.level = helper.getLevel();
    this.rel = rel;
    this.abs = helper.absolutePos(rel);
    this.player = player;
  }

  public JsonObject category(String name) {
    return switch (name) {
      case "states" -> states();
      case "tabs" -> tabs();
      case "tooltips" -> tooltips();
      case "fluids" -> fluids();
      case "roundtrip" -> roundtrip();
      case "process" -> process();
      case "interact" -> new ParityInteract(helper, new BlockPos(5, 1, 5)).interact();
      default -> throw new IllegalArgumentException(name);
    };
  }

  private static List<Block> modBlocks() {
    List<Block> blocks = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      if (GuiProbes.isMod(block)) {
        blocks.add(block);
      }
    }
    blocks.sort(Comparator.comparing(ParitySnapshot::blockId));
    return blocks;
  }

  private static String error(Throwable e) {
    return "!" + e.getClass().getSimpleName();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  static String props(BlockState state) {
    TreeMap<String, String> values = new TreeMap<>();
    for (Property property : state.getProperties()) {
      values.put(property.getName(), property.getName(state.getValue(property)));
    }
    if (values.isEmpty()) {
      return "-";
    }
    StringBuilder out = new StringBuilder();
    values.forEach((k, v) -> out.append(out.length() == 0 ? "" : ",").append(k).append('=').append(v));
    return out.toString();
  }

  private static String sixteenths(double value) {
    double scaled = Math.round(value * 32.0) / 2.0;
    return scaled == Math.rint(scaled) ? String.valueOf((long) scaled) : String.valueOf(scaled);
  }

  static String shape(VoxelShape shape) {
    if (shape.isEmpty()) {
      return "empty";
    }
    AABB bounds = shape.bounds();
    double volume = 0;
    for (AABB box : shape.toAabbs()) {
      volume += (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
    }
    return sixteenths(bounds.minX) + "," + sixteenths(bounds.minY) + "," + sixteenths(bounds.minZ) + ".."
        + sixteenths(bounds.maxX) + "," + sixteenths(bounds.maxY) + "," + sixteenths(bounds.maxZ) + " v"
        + ParitySnapshot.round(volume * 4096.0);
  }

  private static String fingerprint(BlockState state) {
    StringBuilder out = new StringBuilder();
    out.append("light=").append(state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    out.append(";damp=").append(state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    out.append(";sky=").append(state.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    out.append(";occl=").append(state.canOcclude());
    out.append(";signal=").append(state.isSignalSource());
    out.append(";analog=").append(state.hasAnalogOutputSignal());
    out.append(";ticks=").append(state.isRandomlyTicking());
    out.append(";shape=").append(shape(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
    out.append(";collision=").append(shape(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)));
    return out.toString();
  }

  private static List<BlockState> sample(Block block) {
    List<BlockState> all = new ArrayList<>(block.getStateDefinition().getPossibleStates());
    all.sort(Comparator.comparing(ParityBehavior::props));
    if (all.size() <= MAX_STATES) {
      return all;
    }
    List<BlockState> out = new ArrayList<>();
    out.add(block.defaultBlockState());
    for (int i = 0; i < MAX_STATES; i++) {
      BlockState state = all.get(i * all.size() / MAX_STATES);
      if (!out.contains(state)) {
        out.add(state);
      }
    }
    return out;
  }

  private void look(float yaw, float pitch) {
    player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
    player.setYRot(yaw);
    player.setYHeadRot(yaw);
    player.setXRot(pitch);
    player.yRotO = yaw;
    player.xRotO = pitch;
  }

  private JsonObject placements(Block block) {
    JsonObject out = new JsonObject();
    Item item = block.asItem();
    if (item == Items.AIR) {
      return out;
    }
    helper.setBlock(rel, Blocks.AIR.defaultBlockState());
    BlockPos below = abs.below();
    for (String[] look : LOOKS) {
      look(Float.parseFloat(look[1]), Float.parseFloat(look[2]));
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
      out.addProperty(look[0], placed(block, item, hit));
    }
    BlockPos wall = abs.north();
    helper.setBlock(rel.north(), Blocks.STONE.defaultBlockState());
    look(180, 0);
    BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, 0.5), Direction.SOUTH, wall, false);
    out.addProperty("wall_north", placed(block, item, side));
    helper.setBlock(rel.north(), Blocks.AIR.defaultBlockState());
    return out;
  }

  private String placed(Block block, Item item, BlockHitResult hit) {
    try {
      BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item), hit);
      Block target = item instanceof BlockItem blockItem ? blockItem.getBlock() : block;
      BlockState placed = target.getStateForPlacement(context);
      return placed == null ? "null" : ParitySnapshot.blockId(placed.getBlock()) + "[" + props(placed) + "]";
    } catch (RuntimeException e) {
      return error(e);
    }
  }

  public JsonObject states() {
    JsonObject out = new JsonObject();
    for (Block block : modBlocks()) {
      JsonObject entry = new JsonObject();
      try {
        entry.addProperty("count", block.getStateDefinition().getPossibleStates().size());
        JsonObject states = new JsonObject();
        for (BlockState state : sample(block)) {
          try {
            states.addProperty(props(state), fingerprint(state));
          } catch (RuntimeException e) {
            states.addProperty(props(state), error(e));
          }
        }
        entry.add("states", states);
        BlockState base = block.defaultBlockState();
        JsonObject transform = new JsonObject();
        transform.addProperty("cw90", props(base.rotate(level, abs, Rotation.CLOCKWISE_90)));
        transform.addProperty("ccw90", props(base.rotate(level, abs, Rotation.COUNTERCLOCKWISE_90)));
        transform.addProperty("mirror", props(base.mirror(Mirror.FRONT_BACK)));
        entry.add("transform", transform);
        entry.add("place", placements(block));
      } catch (RuntimeException e) {
        entry.addProperty("error", error(e));
      }
      out.add(ParitySnapshot.blockId(block), entry);
    }
    return out;
  }

  public JsonObject tabs() {
    JsonObject out = new JsonObject();
    CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
    for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
      String id = String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
      boolean modTab = id.startsWith(Faktocraft.MODID + ":");
      List<String> keys = new ArrayList<>();
      try {
        for (ItemStack stack : tab.getDisplayItems()) {
          if (modTab || GuiProbes.isMod(stack.getItem())) {
            keys.add(ParitySnapshot.stackKey(stack));
          }
        }
      } catch (RuntimeException e) {
        keys.add(error(e));
      }
      if (keys.isEmpty()) {
        continue;
      }
      JsonObject entry = new JsonObject();
      entry.add("items", ParitySnapshot.set(keys));
      entry.addProperty("order", String.join(" ", keys));
      out.add(id, entry);
    }
    return out;
  }

  static String flatten(Component component) {
    StringBuilder out = new StringBuilder();
    TextColor color = component.getStyle().getColor();
    if (color != null) {
      out.append('{').append(color.serialize()).append('}');
    }
    ComponentContents contents = component.getContents();
    if (contents instanceof TranslatableContents translatable) {
      out.append('<').append(translatable.getKey());
      Object[] args = translatable.getArgs();
      if (args.length > 0) {
        out.append('(');
        for (int i = 0; i < args.length; i++) {
          if (i > 0) {
            out.append('|');
          }
          out.append(args[i] instanceof Component c ? flatten(c) : String.valueOf(args[i]));
        }
        out.append(')');
      }
      out.append('>');
    } else if (contents instanceof LiteralContents plain) {
      out.append(plain.text());
    } else if (contents == ComponentContents.EMPTY) {
      out.append("");
    } else {
      out.append('[').append(contents.getClass().getSimpleName()).append(']');
    }
    for (Component sibling : component.getSiblings()) {
      out.append(flatten(sibling));
    }
    return out.toString();
  }

  public JsonObject tooltips() {
    JsonObject out = new JsonObject();
    for (GuiProbes.Probe probe : GuiProbes.all()) {
      ItemStack stack = probe.create();
      if (!GuiProbes.isMod(stack.getItem())) {
        continue;
      }
      JsonArray lines = new JsonArray();
      try {
        for (Component line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
          lines.add(flatten(line));
        }
      } catch (Throwable e) {
        lines.add(error(e));
      }
      out.add(probe.key(), lines);
    }
    return out;
  }

  private static Object invokeInt(Object target, String name, LevelReader level) {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      for (Method method : type.getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == 1
            && method.getParameterTypes()[0].isAssignableFrom(level.getClass())) {
          try {
            method.setAccessible(true);
            return method.invoke(target, level);
          } catch (ReflectiveOperationException e) {
            return error(e);
          }
        }
      }
    }
    return "?";
  }

  public JsonObject fluids() {
    JsonObject out = new JsonObject();
    LivingEntity pig = EntityType.PIG.create(level);
    for (Fluid fluid : BuiltInRegistries.FLUID) {
      if (!Faktocraft.MODID.equals(BuiltInRegistries.FLUID.getKey(fluid).getNamespace())) {
        continue;
      }
      JsonObject entry = new JsonObject();
      try {
        FluidState state = fluid.defaultFluidState();
        entry.addProperty("source", state.isSource());
        entry.addProperty("tickDelay", fluid.getTickDelay(level));
        entry.addProperty("resistance", ParitySnapshot.round(state.getExplosionResistance()));
        entry.addProperty("bucket", ParitySnapshot.itemId(fluid.getBucket()));
        entry.addProperty("block", ParitySnapshot.blockId(state.createLegacyBlock().getBlock()));
        if (fluid instanceof FlowingFluid flowing) {
          entry.addProperty("slope", String.valueOf(invokeInt(flowing, "getSlopeFindDistance", level)));
          entry.addProperty("dropOff", String.valueOf(invokeInt(flowing, "getDropOff", level)));
          entry.addProperty("source_of", ParitySnapshot.fluidId(flowing.getSource()));
          entry.addProperty("flowing_of", ParitySnapshot.fluidId(flowing.getFlowing()));
        }
        FluidType type = fluid.getFluidType();
        entry.addProperty("type", String.valueOf(ForgeRegistries.FLUID_TYPES.get().getKey(type)));
        entry.addProperty("light", type.getLightLevel());
        entry.addProperty("density", type.getDensity());
        entry.addProperty("temperature", type.getTemperature());
        entry.addProperty("viscosity", type.getViscosity());
        entry.addProperty("rarity", type.getRarity().name());
        entry.addProperty("lighterThanAir", type.isLighterThanAir());
        entry.addProperty("convertToSource", type.canConvertToSource(new FluidStack(fluid, 1000)));
        entry.addProperty("hydrates", type.canHydrate(new FluidStack(fluid, 1000)));
        entry.addProperty("descriptionId", type.getDescriptionId());
        var fill = type.getSound(SoundActions.BUCKET_FILL);
        var empty = type.getSound(SoundActions.BUCKET_EMPTY);
        entry.addProperty("soundFill", fill == null ? "none" : String.valueOf(fill.getLocation()));
        entry.addProperty("soundEmpty", empty == null ? "none" : String.valueOf(empty.getLocation()));
        if (pig != null) {
          entry.addProperty("swim", type.canSwim(pig));
          entry.addProperty("drown", type.canDrownIn(pig));
          entry.addProperty("fall", ParitySnapshot.round(type.getFallDistanceModifier(pig)));
          entry.addProperty("motion", ParitySnapshot.round(type.motionScale(pig)));
          entry.addProperty("push", type.canPushEntity(pig));
          entry.addProperty("extinguish", type.canExtinguish(pig));
          entry.addProperty("hydrateEntity", type.canHydrate(pig));
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", error(e));
      }
      out.add(ParitySnapshot.fluidId(fluid), entry);
    }
    return out;
  }

  private IFluidHandler internalFluids(BlockEntity be) {
    return be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
  }

  private void prefillFluids(BlockEntity be) {
    IFluidHandler fluids = internalFluids(be);
    if (fluids == null) {
      return;
    }
    for (Fluid fluid : GuiProbes.sourceFluids()) {
      for (int i = 0; i < 64; i++) {
        if (fluids.fill(new FluidStack(fluid, 1000), IFluidHandler.FluidAction.EXECUTE) <= 0) {
          break;
        }
      }
    }
  }

  private String tanks(BlockEntity be) {
    IFluidHandler fluids = internalFluids(be);
    if (fluids == null) {
      return "none";
    }
    List<String> out = new ArrayList<>();
    for (int tank = 0; tank < fluids.getTanks(); tank++) {
      FluidStack stack = fluids.getFluidInTank(tank);
      out.add(stack.isEmpty() ? "empty" : ParitySnapshot.fluidId(stack.getFluid()) + "x" + stack.getAmount());
    }
    return String.join(" ", out);
  }

  private String describe(BlockEntity be) {
    if (be == null) {
      return "no block entity";
    }
    StringBuilder out = new StringBuilder(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
    if (be instanceof FaktocraftBlockEntity machine && machine.hasEnergy()) {
      out.append(" energy=").append(machine.getEnergyStorage().energyStored());
    }
    out.append(" tanks=").append(tanks(be));
    return out.toString();
  }

  private void clearArea() {
    BlockEntity be = level.getBlockEntity(abs);
    if (be instanceof FaktocraftBlockEntity machine && machine.getItemStackHandler() != null) {
      ItemStackHandler stacks = machine.getItemStackHandler();
      for (int i = 0; i < stacks.getSlots(); i++) {
        stacks.setStackInSlot(i, ItemStack.EMPTY);
      }
    }
    helper.setBlock(rel, Blocks.AIR.defaultBlockState());
    for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3))) {
      item.discard();
    }
  }

  private ItemStack tool(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
  }

  public JsonObject roundtrip() {
    JsonObject out = new JsonObject();
    Map<String, ItemStack> tools = new TreeMap<>();
    tools.put("pickaxe", new ItemStack(Items.DIAMOND_PICKAXE));
    tools.put("wrench", tool("faktocraft:wrench"));
    for (Block block : modBlocks()) {
      if (!(block instanceof EntityBlock)) {
        continue;
      }
      JsonObject entry = new JsonObject();
      try {
        BlockState state = ParitySnapshot.placementState(block);
        helper.setBlock(rel, state);
        BlockEntity be = level.getBlockEntity(abs);
        if (be == null) {
          continue;
        }
        state = level.getBlockState(abs);
        if (be instanceof FaktocraftBlockEntity machine && machine.hasEnergy()) {
          if (machine.hasBatteryDock() && machine.getBatteryStackHandler() != null) {
            machine.getBatteryStackHandler().setStackInSlot(0, tool(DOCK_CAPACITOR));
          }
          machine.getEnergyStorage().setEnergy(Math.min(machine.getEnergyStorage().maxEnergy(), PRIMED_ENERGY));
        }
        prefillFluids(be);
        be.setChanged();
        entry.addProperty("before", describe(be));
        ItemStack carried = ItemStack.EMPTY;
        for (Map.Entry<String, ItemStack> tool : tools.entrySet()) {
          List<String> keys = new ArrayList<>();
          for (ItemStack drop : Block.getDrops(state, level, abs, be, player, tool.getValue().copy())) {
            keys.add(ParitySnapshot.stack(drop));
            if (drop.is(block.asItem()) && carried.isEmpty()) {
              carried = drop.copy();
            }
          }
          entry.add("drops_" + tool.getKey(), ParitySnapshot.array(keys));
        }
        clearArea();
        if (!carried.isEmpty()) {
          carried.setCount(1);
          look(180, 0);
          player.setItemInHand(InteractionHand.MAIN_HAND, carried);
          BlockPos below = abs.below();
          BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
          String result;
          try {
            result = String.valueOf(carried.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)));
          } catch (RuntimeException e) {
            result = error(e);
          }
          entry.addProperty("placed", result.startsWith("!") ? result
              : level.getBlockState(abs).is(block)
                  ? "yes"
                  : "no");
          entry.addProperty("after", describe(level.getBlockEntity(abs)));
          player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", error(e));
      } finally {
        clearArea();
      }
      out.add(ParitySnapshot.blockId(block), entry);
    }
    return out;
  }

  private static List<Integer> slotsOf(FaktocraftBlockEntity machine, InventorySlotType type) {
    List<Integer> out = new ArrayList<>();
    for (FaktocraftSlot slot : machine.getSlots()) {
      if (slot.getInventorySlotType() == type) {
        out.add(slot.getSlotId());
      }
    }
    return out;
  }

  private static List<GuiProbes.Probe> accepted(ItemStackHandler stacks, int slot) {
    List<GuiProbes.Probe> out = new ArrayList<>();
    for (GuiProbes.Probe probe : GuiProbes.all()) {
      try {
        if (stacks.isItemValid(slot, probe.create())) {
          out.add(probe);
        }
      } catch (RuntimeException e) {
        Faktocraft.LOGGER.debug("probe {} threw", probe.key(), e);
      }
    }
    return out;
  }

  private static List<GuiProbes.Probe> pick(List<GuiProbes.Probe> accepted, int count) {
    List<GuiProbes.Probe> out = new ArrayList<>();
    for (String id : PREFERRED_FEEDS) {
      for (GuiProbes.Probe probe : accepted) {
        if (out.size() < count / 2 && probe.key().equals(id)) {
          out.add(probe);
        }
      }
    }
    for (GuiProbes.Probe probe : accepted) {
      if (out.size() < count && probe.key().startsWith(Faktocraft.MODID + ":") && !out.contains(probe)) {
        out.add(probe);
      }
    }
    if (out.isEmpty() && !accepted.isEmpty()) {
      out.add(accepted.get(0));
    }
    return out;
  }

  private String contents(FaktocraftBlockEntity machine) {
    ItemStackHandler stacks = machine.getItemStackHandler();
    List<String> out = new ArrayList<>();
    for (FaktocraftSlot slot : machine.getSlots()) {
      ItemStack stack = stacks.getStackInSlot(slot.getSlotId());
      if (!stack.isEmpty()) {
        out.add(slot.getInventorySlotType().getType() + slot.getSlotId() + "=" + ParitySnapshot.stack(stack));
      }
    }
    return String.join(" ", out);
  }

  @SuppressWarnings("unchecked")
  private String runScenario(Block block, GuiProbes.Probe feed, int overclockers) {
    BlockState state = ParitySnapshot.placementState(block);
    helper.setBlock(rel, state);
    BlockEntity be = level.getBlockEntity(abs);
    if (!(be instanceof FaktocraftBlockEntity machine) || machine.getItemStackHandler() == null) {
      return "no machine";
    }
    long seed = ParitySnapshot.blockId(block).hashCode() * 31L + (feed == null ? 0 : feed.key().hashCode());
    level.random.setSeed(seed);
    ItemStackHandler stacks = machine.getItemStackHandler();
    List<Integer> feedSlots = slotsOf(machine, InventorySlotType.INPUT);
    List<Integer> extra = slotsOf(machine, InventorySlotType.NORMAL);
    if (feed != null && !feedSlots.isEmpty()) {
      ItemStack stack = feed.create();
      stack.setCount(Math.min(FEED_COUNT, stack.getMaxStackSize()));
      stacks.setStackInSlot(feedSlots.get(0), stack);
    }
    for (int i = 1; i < feedSlots.size(); i++) {
      extra.add(feedSlots.get(i));
    }
    for (int slot : extra) {
      List<GuiProbes.Probe> options = pick(accepted(stacks, slot), 2);
      if (!options.isEmpty()) {
        ItemStack stack = options.get(0).create();
        stack.setCount(Math.min(FEED_COUNT, stack.getMaxStackSize()));
        stacks.setStackInSlot(slot, stack);
      }
    }
    if (overclockers > 0 && machine.hasUpgrades()) {
      machine.getUpgradeStackHandler().setStackInSlot(0, new ItemStack(
          BuiltInRegistries.ITEM.get(new ResourceLocation("faktocraft:overclocker_upgrade")),
          overclockers));
    }
    if (machine.hasBatteryDock() && machine.getBatteryStackHandler() != null) {
      machine.getBatteryStackHandler().setStackInSlot(0, tool(DOCK_CAPACITOR));
    }
    prefillFluids(be);
    boolean generator = machine.isGenerator();
    BasicEnergyStorage energy = machine.hasEnergy() ? machine.getEnergyStorage() : null;
    if (energy != null) {
      energy.setEnergy(generator ? 0 : energy.maxEnergy());
    }
    BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) level.getBlockState(abs)
        .getTicker(level, be.getType());
    if (ticker == null) {
      return "no ticker";
    }
    int ticks = generator ? GENERATOR_TICKS : PROCESS_TICKS;
    long used = 0;
    int firstChange = -1;
    String start = contents(machine) + "|" + tanks(be);
    for (int tick = 0; tick < ticks; tick++) {
      if (energy != null && !generator) {
        used += energy.maxEnergy() - energy.energyStored();
        energy.setEnergy(energy.maxEnergy());
      }
      ticker.tick(level, abs, level.getBlockState(abs), be);
      if (firstChange < 0 && !(contents(machine) + "|" + tanks(be)).equals(start)) {
        firstChange = tick + 1;
      }
    }
    if (energy != null && !generator) {
      used += energy.maxEnergy() - energy.energyStored();
    }
    StringBuilder out = new StringBuilder();
    out.append("change@").append(firstChange);
    if (energy != null) {
      out.append(generator ? " generated=" + energy.energyStored() : " used=" + used);
    }
    out.append(" | ").append(contents(machine)).append(" | tanks ").append(tanks(be));
    return out.toString();
  }

  public JsonObject process() {
    JsonObject out = new JsonObject();
    for (Block block : modBlocks()) {
      if (!(block instanceof EntityBlock)) {
        continue;
      }
      JsonObject entry = new JsonObject();
      try {
        helper.setBlock(rel, ParitySnapshot.placementState(block));
        BlockEntity be = level.getBlockEntity(abs);
        if (!(be instanceof FaktocraftBlockEntity machine) || machine.getItemStackHandler() == null
            || machine.getSlots() == null) {
          continue;
        }
        List<Integer> feedSlots = slotsOf(machine, InventorySlotType.INPUT);
        List<GuiProbes.Probe> feeds = feedSlots.isEmpty() ? List.of()
            : pick(accepted(machine.getItemStackHandler(), feedSlots.get(0)), PROCESS_PROBES);
        clearArea();
        if (feeds.isEmpty()) {
          entry.addProperty("none", runScenario(block, null, 0));
        }
        for (GuiProbes.Probe feed : feeds) {
          entry.addProperty(feed.key(), runScenario(block, feed, 0));
          clearArea();
        }
        if (!feeds.isEmpty()) {
          entry.addProperty(feeds.get(0).key() + "+2oc", runScenario(block, feeds.get(0), 2));
        }
      } catch (RuntimeException e) {
        entry.addProperty("error", error(e));
        Faktocraft.LOGGER.warn("[Parity] process {} threw", ParitySnapshot.blockId(block), e);
      } finally {
        clearArea();
      }
      out.add(ParitySnapshot.blockId(block), entry);
    }
    return out;
  }
}
