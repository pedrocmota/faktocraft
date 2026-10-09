package com.faktocraft.gametest.regression;

import com.faktocraft.common.block.VoxelBlock;
import com.faktocraft.common.block.impl.generators.geo_generator.BlockEntityGeoGenerator;
import com.faktocraft.common.block.impl.generators.wind_generator.BlockEntityWindGenerator;
import com.faktocraft.common.block.impl.pipe.BlockEntityFluidPipe;
import com.faktocraft.common.block.impl.pipe.BlockEntityTank;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.item.impl.nano.ItemNanoArmor;
import com.faktocraft.common.item.impl.tools.VeinMining;
import com.faktocraft.common.radiation.RadiationExposure;
import com.faktocraft.common.registries.ModComponents;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.common.registries.machines.M1Registry;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.common.util.ClientProxy;
import com.faktocraft.common.util.NbtBridge;
import com.faktocraft.common.util.transfer.CapabilityBridge;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.MockPlayers;
import com.faktocraft.gametest.TestUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.fluids.FluidStack;

public class PortRegressionGameTest {

  private static final String TEMPLATE = "gametest_platform";

  private static CompoundTag saved(GameTestHelper helper, BlockPos rel) {
    BlockEntity be = TestUtil.blockEntity(helper, rel);
    return be == null ? new CompoundTag() : be.saveWithoutMetadata(helper.getLevel().registryAccess());
  }

  private static void furnace(GameTestHelper helper, List<String> wrong, BlockPos rel, BlockState block,
      ItemStack input, ItemStack fuel, int burn, int cook, String what) {
    helper.setBlock(rel, block);
    if (TestUtil.blockEntity(helper, rel) instanceof net.minecraft.world.Container container) {
      container.setItem(0, input);
      container.setItem(1, fuel);
    }
    helper.runAfterDelay(3, () -> {
      CompoundTag tag = saved(helper, rel);
      int litTotal = tag.getIntOr("lit_total_time", -1);
      int cookTotal = tag.getIntOr("cooking_total_time", -1);
      if (litTotal != burn || cookTotal != cook) {
        wrong.add(what + ": burns " + litTotal + " ticks and cooks each item in " + cookTotal + " (1.20.1: " + burn
            + " and " + cook + ")");
      }
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 60)
  public static void modFuelsAndBlastingRecipesMatch1201InFastFurnaces(GameTestHelper helper) {
    List<String> wrong = new java.util.ArrayList<>();
    furnace(helper, wrong, new BlockPos(1, 1, 1), Blocks.BLAST_FURNACE.defaultBlockState(),
        new ItemStack(Items.RAW_IRON, 8), new ItemStack(ModItems.BRIQUETTE), 800, 100, "briquette in a blast furnace");
    furnace(helper, wrong, new BlockPos(3, 1, 1), Blocks.SMOKER.defaultBlockState(), new ItemStack(Items.BEEF, 8),
        new ItemStack(ModItems.SCRAP), 100, 100, "scrap in a smoker");
    furnace(helper, wrong, new BlockPos(5, 1, 1), Blocks.BLAST_FURNACE.defaultBlockState(),
        new ItemStack(ModItems.IRON_DUST, 8), new ItemStack(Items.COAL), 800, 100,
        "faktocraft:iron_dust blasting with coal");
    helper.runAfterDelay(5, () -> {
      if (!wrong.isEmpty()) {
        helper.fail(String.join("; ", wrong));
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void bronzeToolsKeepTheirVanillaAbilities(GameTestHelper helper) {
    if (!new ItemStack(ModItems.BRONZE_SWORD).canPerformAction(ItemAbilities.SWORD_SWEEP)) {
      helper.fail("the bronze sword cannot sweep");
      return;
    }
    if (!new ItemStack(ModItems.BRONZE_SHOVEL).canPerformAction(ItemAbilities.SHOVEL_DOUSE)) {
      helper.fail("the bronze shovel cannot put out a campfire");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 80)
  public static void nanoSaberOnAnArmorStandDoesNotDrain(GameTestHelper helper) {
    ItemStack saber = new ItemStack(ModItems.NANO_SABER);
    var energy = ((IElectricItem) saber.getItem()).getEnergy(saber);
    energy.setEnergy(10_000);
    ModComponents.setActive(saber, true);
    ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2));
    stand.setItemSlot(EquipmentSlot.MAINHAND, saber);
    helper.runAfterDelay(40, () -> {
      ItemStack held = stand.getItemBySlot(EquipmentSlot.MAINHAND);
      int left = ((IElectricItem) held.getItem()).getEnergy(held).energyStored();
      if (left != 10_000) {
        helper.fail("an ignited nano saber on an armor stand went from 10000 to " + left
            + " energy; in 1.20.1 only player inventories ticked items");
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 60)
  public static void veinMiningWithSilkTouchGivesNoExperience(GameTestHelper helper) {
    BlockPos origin = new BlockPos(2, 1, 2);
    for (int i = 0; i < 4; i++) {
      helper.setBlock(origin.east(i), Blocks.COAL_ORE.defaultBlockState());
    }
    BlockPos abs = helper.absolutePos(origin);
    MockPlayers.Mock mock = MockPlayers.survival(helper, abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
    mock.allowAllPayloads();
    var player = mock.player();
    ItemStack drill = new ItemStack(ModItems.IRIDIUM_DRILL);
    ((IElectricItem) drill.getItem()).getEnergy(drill).setEnergy(1_000_000);
    HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> enchantments =
        helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    drill.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
    player.setItemInHand(InteractionHand.MAIN_HAND, drill);
    VeinMining.setActive(player, true);
    boolean mined;
    try {
      mined = player.gameMode.destroyBlock(abs);
    } finally {
      VeinMining.setActive(player, false);
    }
    int ores = 0;
    for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
      if (stack.is(Items.COAL_ORE)) {
        ores += stack.getCount();
      }
    }
    int xp = player.totalExperience;
    mock.remove();
    if (ores < 4) {
      int air = 0;
      for (int i = 0; i < 4; i++) {
        if (helper.getBlockState(origin.east(i)).isAir()) {
          air++;
        }
      }
      helper.fail("vein mining collected " + ores + " coal ore blocks, expected 4 (air=" + air + " xp=" + xp
          + " items=" + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
              new net.minecraft.world.phys.AABB(abs).inflate(6)).size()
          + " active=" + mined + ")");
      return;
    }
    if (xp != 0) {
      helper.fail("vein mining with Silk Touch gave " + xp + " experience; 1.20.1 gave none");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void itemsDecodeWithTheClientRegistries(GameTestHelper helper) {
    HolderLookup.Provider registries = helper.getLevel().registryAccess();
    ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
    pick.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
    CompoundTag tag = NbtBridge.withResult(registries, () -> NbtBridge.saveStack(pick));
    HolderLookup.Provider previous = NbtBridge.registries();
    ClientProxy previousProxy = ClientProxy.get();
    ItemStack loaded;
    try {
      NbtBridge.setFallbackRegistries(null);
      ClientProxy.set(new ClientProxy() {
        @Override
        public HolderLookup.Provider clientThreadRegistries() {
          return registries;
        }
      });
      loaded = NbtBridge.loadStack(tag);
    } finally {
      ClientProxy.set(previousProxy);
      NbtBridge.setFallbackRegistries(previous);
    }
    if (!ItemStack.isSameItemSameComponents(loaded, pick)) {
      helper.fail("an enchanted pickaxe read back as " + loaded + " " + loaded.getComponentsPatch()
          + " on a client without server registries (toolbox tooltip, module filters)");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 60)
  public static void windRotorPushReachesThePushedPlayer(GameTestHelper helper) {
    BlockPos rel = new BlockPos(5, 1, 5);
    BlockState state = M1Registry.WIND_GENERATOR.defaultBlockState();
    if (M1Registry.WIND_GENERATOR instanceof IStateFacing facing) {
      state = facing.setDirection(state, Direction.NORTH);
    }
    helper.setBlock(rel, state);
    if (TestUtil.blockEntity(helper, rel) instanceof BlockEntityWindGenerator generator) {
      generator.getItemStackHandler().setStackInSlot(BlockEntityWindGenerator.ROTOR_SLOT,
          new ItemStack(ModItems.WIND_ROTOR));
    }
    BlockPos abs = helper.absolutePos(rel);
    MockPlayers.Mock mock = MockPlayers.survival(helper, abs.getX() + 0.5, abs.getY() - 0.4, abs.getZ() - 0.75);
    mock.allowAllPayloads();
    mock.flushAndDrainOutbound();
    helper.runAfterDelay(5, () -> {
      int id = mock.player().getId();
      mock.channel().flushOutbound();
      List<ClientboundSetEntityMotionPacket> motions = mock.drainOutbound(ClientboundSetEntityMotionPacket.class);
      long own = motions.stream().filter(p -> p.id() == id).count();
      mock.remove();
      if (own == 0) {
        helper.fail("the rotor pushed the player but no motion packet reached the player's own client");
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void pipeFeedsAReplacedBlockEntity(GameTestHelper helper) {
    BlockPos genRel = new BlockPos(3, 1, 1);
    BlockPos pipeRel = new BlockPos(2, 1, 1);
    helper.setBlock(genRel, M1Registry.GEO_GENERATOR.defaultBlockState());
    helper.setBlock(pipeRel, PipeRegistry.FLUID_STONE_PIPE.defaultBlockState()
        .setValue(VoxelBlock.FACING_TO_PROPERTY_MAP.get(Direction.EAST), true));
    Runnable fill = () -> {
      if (TestUtil.blockEntity(helper, pipeRel) instanceof BlockEntityFluidPipe pipe) {
        pipe.tank.fillFluid(new FluidStack(Fluids.LAVA, 100), 100, false);
      }
    };
    fill.run();
    BlockPos genAbs = helper.absolutePos(genRel);
    ServerLevel level = helper.getLevel();
    helper.runAfterDelay(60, () -> {
      if (!(level.getBlockEntity(genAbs) instanceof BlockEntityGeoGenerator first)
          || first.fluidStorage.getFluidAmount() == 0 && first.getEnergyStorage().energyStored() == 0) {
        helper.fail("the pipe never fed the geothermal generator");
        return;
      }
      level.removeBlockEntity(genAbs);
      if (!(level.getBlockEntity(genAbs) instanceof BlockEntityGeoGenerator)) {
        helper.fail("no new block entity");
        return;
      }
      fill.run();
    });
    helper.runAfterDelay(140, () -> {
      if (!(level.getBlockEntity(genAbs) instanceof BlockEntityGeoGenerator second)) {
        helper.fail("no block entity");
        return;
      }
      if (second.fluidStorage.getFluidAmount() == 0 && second.getEnergyStorage().energyStored() == 0) {
        helper.fail("after the generator's block entity was replaced (same block state, e.g. chunk reload) the"
            + " pipe kept pushing into the old one and the new one got nothing");
        return;
      }
      helper.succeed();
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 40)
  public static void tankBucketNeverGoesIntoArmorSlots(GameTestHelper helper) {
    BlockPos rel = new BlockPos(2, 1, 2);
    helper.setBlock(rel, PipeRegistry.TANK.defaultBlockState());
    if (TestUtil.blockEntity(helper, rel) instanceof BlockEntityTank tank) {
      tank.tank.fillFluid(new FluidStack(Fluids.WATER, 4000), 4000, false);
    }
    BlockPos abs = helper.absolutePos(rel);
    MockPlayers.Mock mock = MockPlayers.survival(helper, abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
    mock.allowAllPayloads();
    var player = mock.player();
    var inventory = player.getInventory();
    for (int i = 0; i < 36; i++) {
      inventory.setItem(i, new ItemStack(Items.STONE, 64));
    }
    inventory.setItem(inventory.getSelectedSlot(), new ItemStack(Items.BUCKET, 2));
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    StringBuilder wrong = new StringBuilder();
    for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
        EquipmentSlot.HEAD, EquipmentSlot.BODY, EquipmentSlot.SADDLE }) {
      ItemStack stack = player.getItemBySlot(slot);
      if (!stack.isEmpty()) {
        wrong.append(slot.getName()).append('=').append(stack).append(' ');
      }
    }
    mock.remove();
    if (wrong.length() > 0) {
      helper.fail("the filled bucket was stowed into " + wrong + "(1.20.1: offhand or dropped)");
      return;
    }
    helper.succeed();
  }

  private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
      EquipmentSlot.FEET };

  private static int suitEnergy(net.minecraft.world.entity.LivingEntity living) {
    int total = 0;
    for (EquipmentSlot slot : ARMOR) {
      ItemStack piece = living.getItemBySlot(slot);
      total += ((IElectricItem) piece.getItem()).getEnergy(piece).energyStored();
    }
    return total;
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 40)
  public static void nanoSuitStillReactsToFireUnderFireResistance(GameTestHelper helper) {
    BlockPos abs = helper.absolutePos(new BlockPos(5, 1, 5));
    MockPlayers.Mock mock = MockPlayers.survival(helper, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
    mock.allowAllPayloads();
    var player = mock.player();
    net.minecraft.world.item.Item[] suit = { ModItems.NANO_HELMET, ModItems.NANO_CHESTPLATE, ModItems.NANO_LEGGINGS,
        ModItems.NANO_BOOTS };
    for (int i = 0; i < ARMOR.length; i++) {
      ItemStack piece = new ItemStack(suit[i]);
      var energy = ((IElectricItem) piece.getItem()).getEnergy(piece);
      energy.setEnergy(energy.maxEnergy());
      player.setItemSlot(ARMOR[i], piece);
    }
    int before = suitEnergy(player);
    player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
    player.setRemainingFireTicks(100);
    player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().lava(), 4.0F);
    int drained = before - suitEnergy(player);
    boolean burning = player.isOnFire();
    mock.remove();
    if (drained != ItemNanoArmor.FIRE_COST || burning) {
      helper.fail("lava with Fire Resistance drained " + drained + " from the nano suit (1.20.1: "
          + ItemNanoArmor.FIRE_COST + ") and the player is " + (burning ? "still" : "no longer") + " burning");
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void legacyForgeDataIsReadOnLoad(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
    pig.saveWithoutId(output);
    CompoundTag tag = output.buildResult();
    CompoundTag legacy = new CompoundTag();
    legacy.putFloat("FaktocraftRadiation", 12.5F);
    legacy.putString("othermod", "kept out");
    tag.put("ForgeData", legacy);
    var loaded = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
    loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    float dose = RadiationExposure.get(loaded);
    if (dose != 12.5F || loaded.getPersistentData().contains("othermod")) {
      helper.fail("a 1.20.1 radiation dose of 12.5 read back as " + dose + ", data " + loaded.getPersistentData());
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void modTransportIgnoresHandlersForge1201DidNotHave(GameTestHelper helper) {
    BlockPos jukebox = new BlockPos(1, 1, 1);
    BlockPos composter = new BlockPos(3, 1, 1);
    BlockPos cauldron = new BlockPos(5, 1, 1);
    BlockPos chest = new BlockPos(7, 1, 1);
    helper.setBlock(jukebox, Blocks.JUKEBOX.defaultBlockState());
    helper.setBlock(composter, Blocks.COMPOSTER.defaultBlockState());
    helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
    helper.setBlock(chest, Blocks.CHEST.defaultBlockState());
    ServerLevel level = helper.getLevel();
    List<String> wrong = new java.util.ArrayList<>();
    if (CapabilityBridge.itemHandler(level, helper.absolutePos(jukebox), Direction.UP) != null) {
      wrong.add("jukebox");
    }
    if (CapabilityBridge.itemHandler(level, helper.absolutePos(composter), Direction.UP) != null) {
      wrong.add("composter");
    }
    if (CapabilityBridge.fluidHandler(level, helper.absolutePos(cauldron), Direction.UP) != null) {
      wrong.add("cauldron");
    }
    if (CapabilityBridge.itemHandler(level, helper.absolutePos(chest), Direction.UP) == null) {
      wrong.add("chest missing");
    }
    if (!wrong.isEmpty()) {
      helper.fail("mod transport sees inventories 1.20.1 did not expose: " + wrong);
      return;
    }
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void serverResultsSwingLikeIn1201(GameTestHelper helper) {
    BlockPos rel = new BlockPos(5, 1, 5);
    helper.setBlock(rel, M2Registry.CRUSHER.defaultBlockState());
    BlockPos abs = helper.absolutePos(rel);
    MockPlayers.Mock mock = MockPlayers.survival(helper, abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
    mock.allowAllPayloads();
    var player = mock.player();
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    InteractionResult wrench = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
        hit));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TOOLBOX));
    InteractionResult toolbox = player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    mock.remove();
    boolean wrenchSwingsSelf = wrench instanceof InteractionResult.Success s
        && s.swingSource() == InteractionResult.SwingSource.SERVER_ONLY;
    boolean toolboxHidden = toolbox instanceof InteractionResult.Success s
        && s.swingSource() == InteractionResult.SwingSource.NONE;
    if (!wrenchSwingsSelf || !toolboxHidden) {
      helper.fail("server results: wrench " + wrench + " (1.20.1 swung for the user too), toolbox " + toolbox
          + " (1.20.1 did not swing for other players)");
      return;
    }
    helper.succeed();
  }
}
