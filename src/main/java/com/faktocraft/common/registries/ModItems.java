package com.faktocraft.common.registries;

import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.ModArmorMaterials;
import com.faktocraft.common.item.base.BaseArmor;
import com.faktocraft.common.item.base.EnergyStorageItem;
import com.faktocraft.common.item.base.MaterialItem;
import com.faktocraft.common.item.base.ToolItem;
import com.faktocraft.common.item.block.BlockItemElectric;
import com.faktocraft.common.item.block.FaktocraftBlockItem;
import com.faktocraft.common.item.impl.ChargingBattery;
import com.faktocraft.common.item.impl.Fertilizer;
import com.faktocraft.common.item.impl.FluidCell;
import com.faktocraft.common.item.impl.MemoryCardItem;
import com.faktocraft.common.item.impl.Scrap;
import com.faktocraft.common.item.impl.ScrapBox;
import com.faktocraft.common.item.impl.armor.NightVisionGoggles;
import com.faktocraft.common.item.impl.bronze.BronzeAxe;
import com.faktocraft.common.item.impl.bronze.BronzeHoe;
import com.faktocraft.common.item.impl.bronze.BronzePickaxe;
import com.faktocraft.common.item.impl.bronze.BronzeShovel;
import com.faktocraft.common.item.impl.bronze.BronzeSword;
import com.faktocraft.common.item.impl.nano.ItemNanoArmor;
import com.faktocraft.common.item.impl.nano.ItemNanosaber;
import com.faktocraft.common.item.impl.nano.NanoHelmet;
import com.faktocraft.common.item.impl.tools.Chainsaw;
import com.faktocraft.common.item.impl.tools.ElectricHoe;
import com.faktocraft.common.item.impl.tools.Hammer;
import com.faktocraft.common.item.impl.tools.IEMeter;
import com.faktocraft.common.item.impl.tools.MiningDrill;
import com.faktocraft.common.item.impl.tools.MultiTool;
import com.faktocraft.common.item.impl.treetap.ElectricTreetap;
import com.faktocraft.common.item.impl.treetap.Treetap;
import com.faktocraft.common.item.impl.upgrade.OverclockerUpgrade;
import com.faktocraft.common.item.impl.wrench.ElectricWrench;
import com.faktocraft.common.item.impl.wrench.Wrench;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class ModItems {
  private static final List<Item> ALL_ITEMS = new ArrayList<>();

  public static final Item TIN_ORE = fromBlock(ModBlocks.TIN_ORE);
  public static final Item DEEPSLATE_TIN_ORE = fromBlock(ModBlocks.DEEPSLATE_TIN_ORE);
  public static final Item LEAD_ORE = fromBlock(ModBlocks.LEAD_ORE);
  public static final Item DEEPSLATE_LEAD_ORE = fromBlock(ModBlocks.DEEPSLATE_LEAD_ORE);
  public static final Item URANIUM_ORE = fromBlock(ModBlocks.URANIUM_ORE);
  public static final Item DEEPSLATE_URANIUM_ORE = fromBlock(ModBlocks.DEEPSLATE_URANIUM_ORE);
  public static final Item SILVER_ORE = fromBlock(ModBlocks.SILVER_ORE);
  public static final Item DEEPSLATE_SILVER_ORE = fromBlock(ModBlocks.DEEPSLATE_SILVER_ORE);
  public static final Item LITHIUM_ORE = fromBlock(ModBlocks.LITHIUM_ORE);
  public static final Item DEEPSLATE_LITHIUM_ORE = fromBlock(ModBlocks.DEEPSLATE_LITHIUM_ORE);
  public static final Item IRIDIUM_ORE = fromBlock(ModBlocks.IRIDIUM_ORE);
  public static final Item DEEPSLATE_IRIDIUM_ORE = fromBlock(ModBlocks.DEEPSLATE_IRIDIUM_ORE);

  public static final Item TIN_CABLE = fromBlock(ModBlocks.TIN_CABLE);
  public static final Item TIN_CABLE_INSULATED = fromBlock(ModBlocks.TIN_CABLE_INSULATED);
  public static final Item COPPER_CABLE = fromBlock(ModBlocks.COPPER_CABLE);
  public static final Item COPPER_CABLE_INSULATED = fromBlock(ModBlocks.COPPER_CABLE_INSULATED);
  public static final Item GOLD_CABLE = fromBlock(ModBlocks.GOLD_CABLE);
  public static final Item GOLD_CABLE_INSULATED = fromBlock(ModBlocks.GOLD_CABLE_INSULATED);
  public static final Item HV_CABLE = fromBlock(ModBlocks.HV_CABLE);
  public static final Item HV_CABLE_INSULATED = fromBlock(ModBlocks.HV_CABLE_INSULATED);
  public static final Item GLASS_FIBRE_CABLE = registerBlockItem(ModBlocks.GLASS_FIBRE_CABLE, Rarity.RARE);

  public static final Item PLASTIC_BLOCK = fromBlock(ModBlocks.PLASTIC_BLOCK);
  public static final Item TIN_BLOCK = fromBlock(ModBlocks.TIN_BLOCK);
  public static final Item SILVER_BLOCK = fromBlock(ModBlocks.SILVER_BLOCK);
  public static final Item STEEL_BLOCK = fromBlock(ModBlocks.STEEL_BLOCK);
  public static final Item BRONZE_BLOCK = fromBlock(ModBlocks.BRONZE_BLOCK);
  public static final Item LEAD_BLOCK = fromBlock(ModBlocks.LEAD_BLOCK);

  public static final Item BASIC_MACHINE_CASING = fromBlock(ModBlocks.BASIC_MACHINE_CASING);
  public static final Item ADVANCED_MACHINE_CASING = fromBlock(ModBlocks.ADVANCED_MACHINE_CASING);
  public static final Item NUCLEAR_REACTOR = fromBlock(ModBlocks.NUCLEAR_REACTOR);
  public static final Item NUKE = fromBlock(ModBlocks.NUKE);
  public static final Item RESIN_SHEET = fromBlock(ModBlocks.RESIN_SHEET);
  public static final Item RUBBER_CARPET = fromBlock(ModBlocks.RUBBER_CARPET);
  public static final Item RUBBER_BLOCK = fromBlock(ModBlocks.RUBBER_BLOCK);

  public static final Item TELEPORT_ANCHOR = registerBlockItem(ModBlocks.TELEPORT_ANCHOR, Rarity.RARE);
  public static final Item DIMENSIONAL_TELEPORT_ANCHOR = registerBlockItem(ModBlocks.DIMENSIONAL_TELEPORT_ANCHOR,
      Rarity.EPIC);

  public static final Item RUBBER_LOG = fuelBlock(ModBlocks.RUBBER_LOG, ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);
  public static final Item RUBBER_WOOD = fromBlock(ModBlocks.RUBBER_WOOD);
  public static final Item RUBBER_LEAVES = fromBlock(ModBlocks.RUBBER_LEAVES);
  public static final Item RUBBER_PLANKS = fuelBlock(ModBlocks.RUBBER_PLANKS,
      ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);
  public static final Item DRIED_RUBBER_LOG = fuelBlock(ModBlocks.DRIED_RUBBER_LOG,
      ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);
  public static final Item RUBBER_SAPLING = fuelBlock(ModBlocks.RUBBER_SAPLING,
      ContextIntProviders.COOKING_TIME_DRY_PLANTS);
  public static final Item RUBBER_STAIRS = fromBlock(ModBlocks.RUBBER_STAIRS);
  public static final Item RUBBER_SLAB = fromBlock(ModBlocks.RUBBER_SLAB);

  public static final Item REINFORCED_GLASS = fromBlock(ModBlocks.REINFORCED_GLASS);
  public static final Item REINFORCED_STONE = fromBlock(ModBlocks.REINFORCED_STONE);
  public static final Item REINFORCED_STONE_SLAB = fromBlock(ModBlocks.REINFORCED_STONE_SLAB);
  public static final Item REINFORCED_STONE_STAIRS = fromBlock(ModBlocks.REINFORCED_STONE_STAIRS);
  public static final Item REINFORCED_STONE_DOOR = fromBlock(ModBlocks.REINFORCED_STONE_DOOR);
  public static final Item IRON_SCAFFOLDING = fromBlock(ModBlocks.IRON_SCAFFOLDING);
  public static final Item IRON_FENCE = fromBlock(ModBlocks.IRON_FENCE);
  public static final Item LUMINATOR = fromBlock(ModBlocks.LUMINATOR);

  public static final Item RAW_TIN = register("raw_tin", MaterialItem::new);
  public static final Item RAW_LEAD = register("raw_lead", MaterialItem::new);
  public static final Item RAW_URANIUM = register("raw_uranium", MaterialItem::new);
  public static final Item RAW_SILVER = register("raw_silver", MaterialItem::new);
  public static final Item RAW_LITHIUM = register("raw_lithium", MaterialItem::new);

  public static final Item TIN_INGOT = register("tin_ingot", MaterialItem::new);
  public static final Item BRONZE_INGOT = register("bronze_ingot", MaterialItem::new);
  public static final Item STEEL_INGOT = register("steel_ingot", MaterialItem::new);
  public static final Item MIXED_METAL_INGOT = register("mixed_metal_ingot", MaterialItem::new);
  public static final Item SILVER_INGOT = register("silver_ingot", MaterialItem::new);
  public static final Item LEAD_INGOT = register("lead_ingot", MaterialItem::new);

  public static final Item TIN_DUST = register("tin_dust", MaterialItem::new);
  public static final Item COPPER_DUST = register("copper_dust", MaterialItem::new);
  public static final Item IRON_DUST = register("iron_dust", MaterialItem::new);
  public static final Item GOLD_DUST = register("gold_dust", MaterialItem::new);
  public static final Item LEAD_DUST = register("lead_dust", MaterialItem::new);
  public static final Item URANIUM_DUST = register("uranium_dust", MaterialItem::new);
  public static final Item ENRICHED_URANIUM_DUST = register("enriched_uranium_dust", MaterialItem::new);
  public static final Item DEPLETED_URANIUM_DUST = register("depleted_uranium_dust", MaterialItem::new);
  public static final Item SILVER_DUST = register("silver_dust", MaterialItem::new);
  public static final Item LITHIUM_DUST = register("lithium_dust", MaterialItem::new);

  public static final Item PURIFIED_TIN = register("purified_tin", MaterialItem::new);
  public static final Item PURIFIED_COPPER = register("purified_copper", MaterialItem::new);
  public static final Item PURIFIED_IRON = register("purified_iron", MaterialItem::new);
  public static final Item PURIFIED_GOLD = register("purified_gold", MaterialItem::new);
  public static final Item PURIFIED_LEAD = register("purified_lead", MaterialItem::new);
  public static final Item PURIFIED_SILVER = register("purified_silver", MaterialItem::new);

  public static final Item TIN_CHUNK = register("tin_chunk", MaterialItem::new);
  public static final Item COPPER_CHUNK = register("copper_chunk", MaterialItem::new);
  public static final Item IRON_CHUNK = register("iron_chunk", MaterialItem::new);
  public static final Item GOLD_CHUNK = register("gold_chunk", MaterialItem::new);
  public static final Item LEAD_CHUNK = register("lead_chunk", MaterialItem::new);
  public static final Item SILVER_CHUNK = register("silver_chunk", MaterialItem::new);

  public static final Item COAL_DUST = register("coal_dust", MaterialItem::new);
  public static final Item LAPIS_LAZULI_DUST = register("lapis_lazuli_dust", MaterialItem::new);
  public static final Item DIAMOND_DUST = register("diamond_dust", MaterialItem::new);
  public static final Item ENERGIUM_DUST = register("energium_dust", MaterialItem::new);
  public static final Item STONE_DUST = register("stone_dust", MaterialItem::new);
  public static final Item DEEPSLATE_DUST = register("deepslate_dust", MaterialItem::new);
  public static final Item SAWDUST = register("sawdust", MaterialItem::new);
  public static final Item BRIQUETTE = register("briquette", com.faktocraft.common.item.impl.Briquette::new);
  public static final Item SULFUR_DUST = register("sulfur_dust", MaterialItem::new);
  public static final Item MUD_PILE = register("mud_pile", MaterialItem::new);

  public static final Item STICKY_RESIN = register("sticky_resin", MaterialItem::new);
  public static final Item RUBBER = register("rubber", MaterialItem::new);
  public static final Item RUBBER_SHARD = register("rubber_shard", MaterialItem::new);
  public static final Item PLASTIC = register("plastic", MaterialItem::new);

  public static final Item CRUDE_CIRCUIT = register("crude_circuit", MaterialItem::new);
  public static final Item ELECTRONIC_CIRCUIT = register("electronic_circuit", MaterialItem::new);
  public static final Item ADVANCED_CIRCUIT = register("advanced_circuit", MaterialItem::new);

  public static final Item CRUDE_CAPACITOR = register("crude_capacitor",
      p -> new com.faktocraft.common.item.impl.CapacitorItem(p, 1_000));
  public static final Item BASIC_CAPACITOR = register("basic_capacitor",
      p -> new com.faktocraft.common.item.impl.CapacitorItem(p, 2_500));
  public static final Item INTERMEDIATE_CAPACITOR = register("intermediate_capacitor",
      p -> new com.faktocraft.common.item.impl.CapacitorItem(p, 10_000));
  public static final Item ADVANCED_CAPACITOR = register("advanced_capacitor",
      p -> new com.faktocraft.common.item.impl.CapacitorItem(p, 40_000));

  public static final Item BATTERY = register("battery",
      p -> new EnergyStorageItem(p, 0, 10000, EnergyType.BOTH, EnergyTier.LOW));
  public static final Item ADVANCED_BATTERY = register("advanced_battery",
      p -> new EnergyStorageItem(p, 0, 40000, EnergyType.BOTH, EnergyTier.LOW));
  public static final Item MEDIUM_BATTERY = register("medium_battery",
      p -> new EnergyStorageItem(p, 0, 100000, EnergyType.BOTH, EnergyTier.MEDIUM));
  public static final Item ADVANCED_MEDIUM_BATTERY = register("advanced_medium_battery",
      p -> new EnergyStorageItem(p, 0, 400000, EnergyType.BOTH, EnergyTier.MEDIUM));
  public static final Item ENERGY_CRYSTAL = register("energy_crystal",
      p -> new EnergyStorageItem(p, 0, 100000, EnergyType.BOTH, EnergyTier.HIGH));
  public static final Item LAPOTRON_CRYSTAL = register("lapotron_crystal",
      p -> new EnergyStorageItem(p, 0, 1000000, EnergyType.BOTH, EnergyTier.VERY_HIGH));
  public static final Item ADVANCED_ENERGY_CRYSTAL = register("advanced_energy_crystal",
      p -> new EnergyStorageItem(p, 0, 400000, EnergyType.BOTH, EnergyTier.HIGH));
  public static final Item ADVANCED_LAPOTRON_CRYSTAL = register("advanced_lapotron_crystal",
      p -> new EnergyStorageItem(p, 0, 4000000, EnergyType.BOTH, EnergyTier.VERY_HIGH));
  public static final Item IRIDIUM_CRYSTAL = register("iridium_crystal",
      p -> new EnergyStorageItem(p, 0, 10000000, EnergyType.BOTH, EnergyTier.ULTRA));

  public static final Item FLUID_CELL = register("fluid_cell", p -> new FluidCell(p.stacksTo(16)));

  public static final Item IRON_ROD = register("iron_rod", MaterialItem::new);

  public static final Item IRIDIUM_SHARD = register("iridium_shard", MaterialItem::new,
      new Item.Properties().rarity(Rarity.RARE));
  public static final Item IRIDIUM = register("iridium", MaterialItem::new, new Item.Properties().rarity(Rarity.RARE));

  public static final Item COPPER_PLATE = register("copper_plate", MaterialItem::new);
  public static final Item TIN_PLATE = register("tin_plate", MaterialItem::new);
  public static final Item IRON_PLATE = register("iron_plate", MaterialItem::new);
  public static final Item LEAD_PLATE = register("lead_plate", MaterialItem::new);
  public static final Item GOLD_PLATE = register("gold_plate", MaterialItem::new);
  public static final Item BRONZE_PLATE = register("bronze_plate", MaterialItem::new);
  public static final Item STEEL_PLATE = register("steel_plate", MaterialItem::new);
  public static final Item RAW_IRIDIUM_PLATE = register("raw_iridium_plate", MaterialItem::new,
      new Item.Properties().rarity(Rarity.RARE));
  public static final Item IRIDIUM_PLATE = register("iridium_plate", MaterialItem::new,
      new Item.Properties().rarity(Rarity.RARE));
  public static final Item LAPIS_LAZULI_PLATE = register("lapis_lazuli_plate", MaterialItem::new);
  public static final Item PLASTIC_PLATE = register("plastic_plate", MaterialItem::new);
  public static final Item ADVANCED_ALLOY = register("advanced_alloy", MaterialItem::new);

  public static final Item CARBON_FIBERS = register("carbon_fibers", MaterialItem::new);
  public static final Item COMBINED_CARBON_FIBERS = register("combined_carbon_fibers", MaterialItem::new);
  public static final Item CARBON_PLATE = register("carbon_plate", MaterialItem::new);

  public static final Item NIGHTVISION_GOGGLES = register("nightvision_goggles", NightVisionGoggles::new);
  public static final Item HAZMAT_HELMET = register("hazmat_helmet",
      p -> new com.faktocraft.common.item.impl.armor.HazmatArmorItem(ArmorType.HELMET, p));
  public static final Item HAZMAT_CHESTPLATE = register("hazmat_chestplate",
      p -> new com.faktocraft.common.item.impl.armor.HazmatArmorItem(ArmorType.CHESTPLATE, p));
  public static final Item HAZMAT_LEGGINGS = register("hazmat_leggings",
      p -> new com.faktocraft.common.item.impl.armor.HazmatArmorItem(ArmorType.LEGGINGS, p));
  public static final Item HAZMAT_BOOTS = register("hazmat_boots",
      p -> new com.faktocraft.common.item.impl.armor.HazmatArmorItem(ArmorType.BOOTS, p));

  public static final Item JETPACK = register("jetpack",
      p -> new com.faktocraft.common.item.impl.armor.JetpackItem(p));
  public static final Item ADVANCED_JETPACK = register("advanced_jetpack",
      p -> new com.faktocraft.common.item.impl.armor.AdvancedJetpackItem(p));

  public static final Item BRONZE_HELMET = register("bronze_helmet",
      p -> new BaseArmor(ModArmorMaterials.BRONZE, ArmorType.HELMET, p));
  public static final Item BRONZE_CHESTPLATE = register("bronze_chestplate",
      p -> new BaseArmor(ModArmorMaterials.BRONZE, ArmorType.CHESTPLATE, p));
  public static final Item BRONZE_LEGGINGS = register("bronze_leggings",
      p -> new BaseArmor(ModArmorMaterials.BRONZE, ArmorType.LEGGINGS, p));
  public static final Item BRONZE_BOOTS = register("bronze_boots",
      p -> new BaseArmor(ModArmorMaterials.BRONZE, ArmorType.BOOTS, p));

  public static final Item BRONZE_SWORD = register("bronze_sword", BronzeSword::new);
  public static final Item BRONZE_PICKAXE = register("bronze_pickaxe", BronzePickaxe::new);
  public static final Item BRONZE_AXE = register("bronze_axe", BronzeAxe::new);
  public static final Item BRONZE_SHOVEL = register("bronze_shovel", BronzeShovel::new);
  public static final Item BRONZE_HOE = register("bronze_hoe", BronzeHoe::new);

  public static final Item NANO_HELMET = register("nano_helmet",
      p -> new NanoHelmet(p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item NANO_CHESTPLATE = register("nano_chestplate",
      p -> new ItemNanoArmor(ArmorType.CHESTPLATE, p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item NANO_LEGGINGS = register("nano_leggings",
      p -> new ItemNanoArmor(ArmorType.LEGGINGS, p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item NANO_BOOTS = register("nano_boots", p -> new ItemNanoArmor(ArmorType.BOOTS,
      p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item NANO_SABER = register("nano_saber",
      p -> new ItemNanosaber(p.rarity(Rarity.EPIC).fireResistant()));

  public static final Item QUANTUM_HELMET = register("quantum_helmet",
      p -> new com.faktocraft.common.item.impl.quantum.QuantumHelmet(p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item QUANTUM_CHESTPLATE = register("quantum_chestplate",
      p -> new com.faktocraft.common.item.impl.quantum.ItemQuantumArmor(ArmorType.CHESTPLATE,
          p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item QUANTUM_LEGGINGS = register("quantum_leggings",
      p -> new com.faktocraft.common.item.impl.quantum.ItemQuantumArmor(ArmorType.LEGGINGS,
          p.rarity(Rarity.EPIC).fireResistant()));
  public static final Item QUANTUM_BOOTS = register("quantum_boots",
      p -> new com.faktocraft.common.item.impl.quantum.ItemQuantumArmor(ArmorType.BOOTS,
          p.rarity(Rarity.EPIC).fireResistant()));

  public static final Item SMALL_POWER_UNIT = register("small_power_unit", MaterialItem::new);
  public static final Item POWER_UNIT = register("power_unit", MaterialItem::new);
  public static final Item COIL = register("coil", MaterialItem::new);
  public static final Item ELECTRIC_MOTOR = register("electric_motor", MaterialItem::new);
  public static final Item SCRAP = register("scrap", Scrap::new);
  public static final Item SCRAP_BOX = register("scrap_box", ScrapBox::new);

  public static final Item HAMMER = register("hammer", p -> new Hammer(p, 80));
  public static final Item CUTTER = register("cutter", p -> new ToolItem(p, 60));
  public static final Item TREETAP = register("treetap", p -> new Treetap(p, 20));
  public static final Item WRENCH = register("wrench", p -> new Wrench(p, 120));

  public static final Item ELECTRIC_TREETAP = register("electric_treetap",
      p -> new ElectricTreetap(p, 0, 10000, EnergyType.RECEIVE, EnergyTier.LOW));
  public static final Item ELECTRIC_WRENCH = register("electric_wrench",
      p -> new ElectricWrench(p, 0, 10000, EnergyType.RECEIVE, EnergyTier.LOW));

  public static final Item CHAINSAW = register("chainsaw",
      p -> new Chainsaw(ToolMaterial.IRON, 1.0F, 6.0F, -3.1F, p, 0, 30000, 50, 100, EnergyType.RECEIVE,
          EnergyTier.LOW));
  public static final Item DIAMOND_CHAINSAW = register("diamond_chainsaw", p -> new Chainsaw(ModTiers.DIAMOND_TOOL,
      1.05F, 5.0F, -3.0F, p, 0, 80000, 70, 120, EnergyType.RECEIVE, EnergyTier.MEDIUM));
  public static final Item IRIDIUM_CHAINSAW = register("iridium_chainsaw", p -> new Chainsaw(ModTiers.IRIDIUM_TOOL,
      1.2F, 5.0F, -3.0F, p.rarity(Rarity.RARE), 0, 300000, 200, 400, EnergyType.RECEIVE, EnergyTier.HIGH));

  public static final Item MINING_DRILL = register("mining_drill",
      p -> new MiningDrill(ToolMaterial.IRON, 1.0F, 1, -2.8F, p, 0, 30000, 50, 100, EnergyType.RECEIVE,
          EnergyTier.LOW));
  public static final Item DIAMOND_DRILL = register("diamond_drill", p -> new MiningDrill(ModTiers.DIAMOND_TOOL, 1.05F,
      1, -2.8F, p, 0, 80000, 70, 120, EnergyType.RECEIVE, EnergyTier.MEDIUM));
  public static final Item IRIDIUM_DRILL = register("iridium_drill", p -> new MiningDrill(ModTiers.IRIDIUM_TOOL,
      1.2F, 1, -2.8F, p.rarity(Rarity.RARE), 0, 300000, 200, 400, EnergyType.RECEIVE, EnergyTier.HIGH));

  public static final Item HOLE_DRILL = register("hole_drill",
      com.faktocraft.common.item.impl.tools.HoleDrill::new);
  public static final Item ELECTRIC_HOE = register("electric_hoe", p -> new ElectricHoe(ToolMaterial.IRON, -2, -1.0F, p,
      0, 10000, 50, 100, 50, EnergyType.RECEIVE, EnergyTier.LOW));
  public static final Item WIND_METER = register("wind_meter",
      com.faktocraft.common.item.impl.tools.WindMeter::new);
  public static final Item IE_METER = register("ie_meter", IEMeter::new);
  public static final Item GEIGER_COUNTER = register("geiger_counter",
      com.faktocraft.common.item.impl.tools.GeigerCounter::new);
  public static final Item DECONTAMINATOR = register("decontaminator",
      com.faktocraft.common.item.impl.tools.Decontaminator::new);
  public static final Item PROSPECTOR = register("prospector",
      p -> new com.faktocraft.common.item.impl.tools.Prospector(p.rarity(Rarity.RARE)));
  public static final Item PLUNGER = register("plunger", com.faktocraft.common.item.impl.tools.Plunger::new);
  public static final Item MULTI_TOOL = register("multi_tool", p -> new MultiTool(ToolMaterial.DIAMOND, -3, 0.0F, p, 0,
      300000, 800, 1400, 500, EnergyType.RECEIVE, EnergyTier.HIGH));

  public static final Item MEMORY_CARD = register("memory_card", MemoryCardItem::new,
      new Item.Properties().rarity(Rarity.EPIC));
  public static final Item TELEPORT_CARD = register("teleport_card",
      com.faktocraft.common.item.impl.TeleportCardItem::new, new Item.Properties().rarity(Rarity.RARE));
  public static final Item BIO_CHAFF = register("bio_chaff", MaterialItem::new);
  public static final Item FERTILIZER = register("fertilizer", Fertilizer::new);
  public static final Item HEAT_CONDUCTOR = register("heat_conductor", MaterialItem::new);

  public static final Item CHARGING_BATTERY = register("charging_battery",
      p -> new ChargingBattery(p, 40000, EnergyType.BOTH, EnergyTier.LOW));
  public static final Item ADVANCED_CHARGING_BATTERY = register("advanced_charging_battery",
      p -> new ChargingBattery(p, 400000, EnergyType.BOTH, EnergyTier.MEDIUM));
  public static final Item CHARGING_ENERGY_CRYSTAL = register("charging_energy_crystal",
      p -> new ChargingBattery(p, 4000000, EnergyType.BOTH, EnergyTier.HIGH));
  public static final Item CHARGING_LAPOTRON_CRYSTAL = register("charging_lapotron_crystal",
      p -> new ChargingBattery(p, 40000000, EnergyType.BOTH, EnergyTier.VERY_HIGH));

  public static final Item OVERCLOCKER_UPGRADE = register("overclocker_upgrade", OverclockerUpgrade::new);
  public static final Item TENSION_UPGRADE_MK1 = register("tension_upgrade_mk1",
      p -> new com.faktocraft.common.item.impl.upgrade.TensionUpgrade(p, 1));
  public static final Item TENSION_UPGRADE_MK2 = register("tension_upgrade_mk2",
      p -> new com.faktocraft.common.item.impl.upgrade.TensionUpgrade(p, 2));
  public static final Item TENSION_UPGRADE_MK3 = register("tension_upgrade_mk3",
      p -> new com.faktocraft.common.item.impl.upgrade.TensionUpgrade(p.rarity(Rarity.RARE), 3));
  public static final Item ADVANCED_OVERCLOCKER_UPGRADE = register("advanced_overclocker_upgrade",
      p -> new com.faktocraft.common.item.impl.upgrade.ItemUpgrade(p,
          com.faktocraft.common.enums.UpgradeType.OVERCLOCKER, true));
  public static final Item EFFICIENCY_UPGRADE = register("efficiency_upgrade",
      p -> new com.faktocraft.common.item.impl.upgrade.ItemUpgrade(p,
          com.faktocraft.common.enums.UpgradeType.EFFICIENCY));
  public static final Item ADVANCED_EFFICIENCY_UPGRADE = register("advanced_efficiency_upgrade",
      p -> new com.faktocraft.common.item.impl.upgrade.ItemUpgrade(p,
          com.faktocraft.common.enums.UpgradeType.EFFICIENCY, true));

  public static final Item MEDIUM_COOLANT_CELL = register("medium_coolant_cell",
      p -> new com.faktocraft.common.item.impl.reactor.CoolantCell(p.stacksTo(1), 1));
  public static final Item LARGE_COOLANT_CELL = register("large_coolant_cell",
      p -> new com.faktocraft.common.item.impl.reactor.CoolantCell(p.stacksTo(1), 2));
  public static final Item EMPTY_FUEL_ROD = register("empty_fuel_rod",
      p -> new com.faktocraft.common.item.impl.reactor.ReactorComponentItem(p, "empty_fuel_rod"));
  public static final Item NUCLEAR_WASTE = register("nuclear_waste",
      p -> new com.faktocraft.common.item.impl.reactor.ReactorComponentItem(p, "nuclear_waste"));
  public static final Item PLUTONIUM = register("plutonium",
      p -> new com.faktocraft.common.item.impl.reactor.ReactorComponentItem(p, "plutonium"));
  public static final Item FUEL_ROD = register("fuel_rod", com.faktocraft.common.item.impl.reactor.FuelRodItem::new);
  public static final Item DEPLETED_FUEL_ROD = register("depleted_fuel_rod",
      p -> new com.faktocraft.common.item.impl.reactor.ReactorComponentItem(p, "depleted_fuel_rod"));
  public static final Item NEUTRON_REFLECTOR = register("neutron_reflector",
      p -> new com.faktocraft.common.item.impl.reactor.ReactorComponentItem(p, "neutron_reflector"));

  public static final Item CIRCUIT_BREAKER = registerBlockItem(ModBlocks.CIRCUIT_BREAKER);
  public static final Item PIPE_VALVE = register("pipe_valve",
      p -> new com.faktocraft.common.item.impl.ItemPipeValve(p));
  public static final Item TOOLBOX = register("toolbox",
      p -> new com.faktocraft.common.item.impl.tools.ToolboxItem(p));
  public static final Item WIND_ROTOR = register("wind_rotor",
      p -> new com.faktocraft.common.item.impl.tools.ItemWindRotor(p.rarity(Rarity.UNCOMMON), 1.0));
  public static final Item ADVANCED_WIND_ROTOR = register("advanced_wind_rotor",
      p -> new com.faktocraft.common.item.impl.tools.ItemWindRotor(p.rarity(Rarity.RARE), 1.5));

  public static List<Item> getAllItems() {
    return Collections.unmodifiableList(ALL_ITEMS);
  }

  public static Item register(String name, Function<Item.Properties, Item> factory) {
    return register(name, factory, new Item.Properties());
  }

  public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
    properties.setId(ResourceKey.create(Registries.ITEM, RegistrationHandler.id(name)));
    Item item = factory.apply(properties);
    RegistrationHandler.item(name, item);
    ALL_ITEMS.add(item);
    return item;
  }

  private static Item fromBlock(Block block) {
    return registerBlockItem(block);
  }

  private static Item fuelBlock(Block block, ResourceKey<ContextIntProvider> burnTime) {
    return registerBlockItemInternal(block, Rarity.COMMON,
        (b, properties) -> new FaktocraftBlockItem(b, properties.cookingFuel(burnTime)));
  }

  public static Item registerBlockItem(Block block) {
    return registerBlockItem(block, Rarity.COMMON);
  }

  public static Item registerBlockItem(Block block, Rarity rarity) {
    return registerBlockItemInternal(block, rarity, FaktocraftBlockItem::new);
  }

  public static Item registerBlockItem(Block block, Rarity rarity,
      java.util.function.BiFunction<Block, Item.Properties, Item> factory) {
    return registerBlockItemInternal(block, rarity, factory);
  }

  public static Item registerElectricBlockItem(Block block) {
    return registerElectricBlockItem(block, Rarity.COMMON);
  }

  public static Item registerElectricBlockItem(Block block, Rarity rarity) {
    return registerBlockItemInternal(block, rarity, BlockItemElectric::new);
  }

  private static Item registerBlockItemInternal(Block block, Rarity rarity,
      java.util.function.BiFunction<Block, Item.Properties, Item> factory) {
    String name = ModBlocks.nameOf(block);
    Item.Properties properties = new Item.Properties()
        .setId(ResourceKey.create(Registries.ITEM, RegistrationHandler.id(name)))
        .useBlockDescriptionPrefix();
    if (rarity != Rarity.COMMON) {
      properties = properties.rarity(rarity);
    }
    Item item = factory.apply(block, properties);
    RegistrationHandler.item(name, item);
    ALL_ITEMS.add(item);
    return item;
  }

  public static void register() {
    ScrapBox.registerDispenseBehavior((ScrapBox) SCRAP_BOX);
  }
}
