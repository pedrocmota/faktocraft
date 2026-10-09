package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.util.NbtBridge;
import com.faktocraft.common.util.PlayerMessages;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.Faktocraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class RemoteRequesterItem extends Item {

  public RemoteRequesterItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  @Nullable
  private static BlockPos boundPos(ItemStack stack) {
    CompoundTag tag = NbtBridge.customData(stack);
    return tag != null && tag.contains("tablePos") ? BlockPos.of(tag.getLongOr("tablePos", 0L)) : null;
  }

  @Nullable
  private static ResourceKey<Level> boundDim(ItemStack stack) {
    CompoundTag tag = NbtBridge.customData(stack);
    if (tag == null || !tag.contains("tableDim")) {
      return null;
    }
    return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
        Identifier.parse(tag.getStringOr("tableDim", "")));
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof BlockRequestTable)) {
      return InteractionResult.PASS;
    }
    if (!level.isClientSide()) {
      ItemStack stack = context.getItemInHand();
      NbtBridge.updateCustomData(stack, tag -> {
        tag.putLong("tablePos", context.getClickedPos().asLong());
        tag.putString("tableDim", level.dimension().identifier().toString());
      });
      if (context.getPlayer() != null) {
        PlayerMessages.display(context.getPlayer(),
            Component.translatable("logistics." + Faktocraft.MODID + ".remote.bound"), true);
      }
    }
    return InteractionResults.sided(level);
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
      return InteractionResults.sided(level);
    }
    BlockPos pos = boundPos(stack);
    ResourceKey<Level> dim = boundDim(stack);
    if (pos == null || dim == null) {
      PlayerMessages.display(serverPlayer,
          Component.translatable("logistics." + Faktocraft.MODID + ".remote.unbound"), true);
      return InteractionResult.FAIL;
    }
    ServerLevel targetLevel = serverPlayer.level().getServer().getLevel(dim);
    if (targetLevel == null || !targetLevel.isLoaded(pos)
        || !(targetLevel.getBlockEntity(pos) instanceof BlockEntityRequestTable)) {
      PlayerMessages.display(serverPlayer,
          Component.translatable("logistics." + Faktocraft.MODID + ".remote.unreachable"), true);
      return InteractionResult.FAIL;
    }
    serverPlayer.openMenu(new SimpleMenuProvider(
        (windowId, inventory, p) -> new MenuRequestTable(windowId, targetLevel, pos, inventory, p, true),
        Component.translatable("block." + Faktocraft.MODID + ".request_table")),
        buf -> {
          buf.writeBlockPos(pos);
          buf.writeBoolean(true);
        });
    return InteractionResult.CONSUME;
  }

  @Override
  @SuppressWarnings("deprecation")
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    super.appendHoverText(stack, level, display, tooltip, flag);
    tooltip.accept(Component.translatable("logistics." + Faktocraft.MODID + ".remote.desc")
        .withStyle(ChatFormatting.GRAY));
    BlockPos pos = boundPos(stack);
    if (pos != null) {
      tooltip.accept(Component.translatable("logistics." + Faktocraft.MODID + ".remote.bound_to",
          pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.DARK_GRAY));
    }
  }
}
