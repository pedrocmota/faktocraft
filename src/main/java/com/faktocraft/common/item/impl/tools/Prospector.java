package com.faktocraft.common.item.impl.tools;

import com.faktocraft.common.util.InteractionResults;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.item.base.ElectricItem;
import com.faktocraft.common.scan.ScanChannel;
import com.faktocraft.common.scan.ScanChannels;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import com.faktocraft.common.util.NbtBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class Prospector extends ElectricItem {

  public static final int SCAN_COST = 10000;
  public static final float FALSE_NEGATIVE_CHANCE = 0.08F;
  public static final int SCAN_DURATION_TICKS = 160;
  public static final int VIEW_RADIUS = 3;
  public static final String TAG_SCANS = "Scans";
  public static final String TAG_JOB = "ScanJob";
  public static final String TAG_CODE = "ScanCode";

  public Prospector(Properties properties) {
    super(properties.stacksTo(1), 0, 400000, EnergyType.RECEIVE, EnergyTier.HIGH);
  }

  public static ItemStack held(Player player) {
    if (player.getMainHandItem().getItem() instanceof Prospector) {
      return player.getMainHandItem();
    }
    if (player.getOffhandItem().getItem() instanceof Prospector) {
      return player.getOffhandItem();
    }
    return ItemStack.EMPTY;
  }

  public static int getCode(ItemStack stack) {
    CompoundTag tag = NbtBridge.customData(stack);
    if (tag != null && tag.contains(TAG_CODE)) {
      return ScanChannels.sanitize(tag.getIntOr(TAG_CODE, 0));
    }
    return ScanChannels.DEFAULT_CODE;
  }

  public static void setCode(ItemStack stack, int code) {
    NbtBridge.updateCustomData(stack, tag -> tag.putInt(TAG_CODE, ScanChannels.sanitize(code)));
  }

  @Nullable
  public static CompoundTag getJob(ItemStack stack) {
    CompoundTag tag = NbtBridge.customData(stack);
    return tag != null && tag.contains(TAG_JOB) ? tag.getCompoundOrEmpty(TAG_JOB) : null;
  }

  @Override
  public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel serverLevel,
      net.minecraft.world.entity.Entity owner, @Nullable EquipmentSlot slot) {
    super.inventoryTick(stack, serverLevel, owner, slot);
    if (!(owner instanceof Player)) {
      return;
    }
    importLegacyScans(serverLevel, stack);
    CompoundTag job = getJob(stack);
    if (job == null) {
      return;
    }
    int remaining = job.getIntOr("remaining", 0) - 1;
    if (remaining > 0) {
      job.putInt("remaining", remaining);
      NbtBridge.updateCustomData(stack, tag -> tag.put(TAG_JOB, job));
      return;
    }
    finishScan(serverLevel, stack, job);
    NbtBridge.updateCustomData(stack, tag -> tag.remove(TAG_JOB));
    if (owner != null) {
      serverLevel.playSound(null, owner.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
          net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.7F);
    }
  }

  private static void importLegacyScans(net.minecraft.server.level.ServerLevel level, ItemStack stack) {
    CompoundTag tag = NbtBridge.customData(stack);
    if (tag == null || !tag.contains(TAG_SCANS)) {
      return;
    }
    CompoundTag legacy = tag.getCompoundOrEmpty(TAG_SCANS);
    if (!legacy.isEmpty()) {
      ScanChannels.get(level).channel(getCode(stack)).importKeyed(legacy);
    }
    NbtBridge.updateCustomData(stack, data -> data.remove(TAG_SCANS));
  }

  public static CompoundTag computeScanEntries(net.minecraft.server.level.ServerLevel level, int chunkX, int chunkZ) {
    net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(chunkX, chunkZ);
    java.util.Map<String, Integer> found = new java.util.HashMap<>();
    net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
    int baseX = chunk.getPos().getMinBlockX();
    int baseZ = chunk.getPos().getMinBlockZ();
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = level.getMinY(); y < level.getMaxY() + 1; y++) {
          cursor.set(baseX + x, y, baseZ + z);
          var state = chunk.getBlockState(cursor);
          if (state.isAir()) {
            continue;
          }
          if (state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            found.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                1, Integer::sum);
          } else if (state.is(com.faktocraft.common.fluid.ModFluids.OIL.block())) {
            found.merge(y >= 30 ? "faktocraft:oil_lake" : "faktocraft:oil_underground", 1, Integer::sum);
          }
        }
      }
    }
    Integer undergroundOil = found.remove("faktocraft:oil_underground");
    if (undergroundOil != null) {
      found.put(undergroundOil >= 600 ? "faktocraft:oil_giant" : "faktocraft:oil_pocket", undergroundOil);
    }
    CompoundTag entries = new CompoundTag();
    var random = level.getRandom();
    for (java.util.Map.Entry<String, Integer> entry : found.entrySet()) {
      if (random.nextFloat() < FALSE_NEGATIVE_CHANCE) {
        continue;
      }
      entries.putInt(entry.getKey(),
          Math.max(1, Math.round(entry.getValue() * (0.75F + random.nextFloat() * 0.5F))));
    }
    return entries;
  }

  private static void finishScan(net.minecraft.server.level.ServerLevel level, ItemStack stack, CompoundTag job) {
    int chunkX = job.getIntOr("cx", 0);
    int chunkZ = job.getIntOr("cz", 0);
    if (!level.dimension().identifier().toString().equals(job.getStringOr("dim", ""))) {
      return;
    }
    CompoundTag scan = new CompoundTag();
    scan.putLong("t", level.getGameTime());
    scan.put("entries", computeScanEntries(level, chunkX, chunkZ));
    ScanChannel channel = ScanChannels.get(level).channel(getCode(stack));
    channel.put(level, chunkX, chunkZ, scan);
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    if (level.isClientSide()) {
      com.faktocraft.common.util.ClientProxy.get().openProspectorScreen();
    }
    return InteractionResults.sided(level);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("tooltip." + Faktocraft.MODID + ".prospector")
        .withStyle(ChatFormatting.GRAY));
    tooltip.accept(Component.translatable("tooltip." + Faktocraft.MODID + ".prospector_accuracy")
        .withStyle(ChatFormatting.DARK_GRAY));
    tooltip.accept(Component.translatable("tooltip." + Faktocraft.MODID + ".scan_code",
        ScanChannels.codeText(getCode(stack))).withStyle(ChatFormatting.LIGHT_PURPLE));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }
}
