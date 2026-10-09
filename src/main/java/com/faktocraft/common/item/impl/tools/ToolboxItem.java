package com.faktocraft.common.item.impl.tools;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.item.base.DiggerElectricItem;
import com.faktocraft.common.registries.ModTags;
import com.faktocraft.common.util.NbtBridge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;

public class ToolboxItem extends Item {
  public ToolboxItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  public static boolean isTool(ItemStack stack) {
    if (stack.getItem() instanceof ToolboxItem) {
      return false;
    }
    Item item = stack.getItem();
    return stack.is(ModTags.TOOLBOX_TOOLS)
        || stack.has(DataComponents.TOOL)
        || item instanceof ShearsItem
        || item instanceof FlintAndSteelItem
        || item instanceof FishingRodItem
        || item instanceof com.faktocraft.common.item.base.ToolItem
        || item instanceof DiggerElectricItem;
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new ToolboxMenu(id, inv, p.blockPosition()),
          stack.getHoverName()),
          buf -> buf.writeBlockPos(player.blockPosition()));
      level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
          net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.6F);
    }
    return InteractionResults.sided(level);
  }

  @Override
  public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
    net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList
        .withSize(ToolboxMenu.SIZE, ItemStack.EMPTY);
    boolean hasAny = false;
    CompoundTag tag = NbtBridge.customData(stack);
    if (tag != null && tag.contains("Items")) {
      com.faktocraft.common.util.transfer.LegacyItemStackHandler handler =
          new com.faktocraft.common.util.transfer.LegacyItemStackHandler(ToolboxMenu.SIZE);
      handler.deserializeNBT(tag.getCompoundOrEmpty("Items"));
      for (int i = 0; i < ToolboxMenu.SIZE; i++) {
        items.set(i, handler.getStackInSlot(i));
        hasAny |= !handler.getStackInSlot(i).isEmpty();
      }
    }
    return hasAny ? java.util.Optional.of(new ToolboxTooltip(items)) : java.util.Optional.empty();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void appendHoverText(ItemStack stack, Item.TooltipContext level,
      net.minecraft.world.item.component.TooltipDisplay display,
      java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
      net.minecraft.world.item.TooltipFlag flag) {
    if (getTooltipImage(stack).isEmpty()) {
      tooltip.accept(net.minecraft.network.chat.Component
          .translatable("tooltip." + com.faktocraft.Faktocraft.MODID + ".toolbox_empty")
          .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    super.appendHoverText(stack, level, display, tooltip, flag);
  }
}
