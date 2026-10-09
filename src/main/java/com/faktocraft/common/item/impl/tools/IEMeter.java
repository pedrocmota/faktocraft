package com.faktocraft.common.item.impl.tools;

import com.faktocraft.common.util.InteractionResults;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.cable.BlockEntityCable;
import com.faktocraft.common.energy.EnergyLookup;
import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.energy.provider.EnergyNetwork;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.item.base.BaseItem;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketIEMeterInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class IEMeter extends BaseItem {

  public IEMeter(Properties properties) {
    super(properties);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("ie." + Faktocraft.MODID + ".desc").withStyle(ChatFormatting.GRAY));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    Player player = context.getPlayer();
    if (level.isClientSide() || player == null || !(player instanceof ServerPlayer serverPlayer)) {
      return super.useOn(context);
    }

    BlockPos pos = context.getClickedPos();
    BlockEntity blockEntity = level.getBlockEntity(pos);

    if (blockEntity instanceof BlockEntityCable cable) {
      EnergyNetwork network = cable.getNetwork();
      if (network != null) {
        int generators = 0;
        int machines = 0;
        int batteries = 0;
        for (BlockPos elPos : network.getElectrics()) {
          IEnergy cap = EnergyLookup.find(level, elPos, null);
          if (cap != null) {
            generators += cap.energyType() == EnergyType.EXTRACT ? 1 : 0;
            machines += cap.energyType() == EnergyType.RECEIVE ? 1 : 0;
            batteries += cap.energyType() == EnergyType.BOTH ? 1 : 0;
          }
        }
        ModNetworking.sendToPlayer(serverPlayer, new PacketIEMeterInfo(true,
            network.getEnergyTier().getLvl(),
            network.getEnergyFlowing() != null ? network.getEnergyFlowing().getLvl() : -1,
            network.getEnergyTier().getBasicTransfer(),
            generators, machines, batteries,
            network.energyStored(), network.maxEnergy(),
            false, 0, 0, false, 0, 0,
            network.getLastReceived(), network.getLastDelivered(), network.getLastDemand(), 0));
        return InteractionResults.serverSwing(level);
      }
    }

    if (blockEntity instanceof FaktocraftBlockEntity faktocraftBlockEntity && faktocraftBlockEntity.hasEnergy()) {
      BasicEnergyStorage storage = faktocraftBlockEntity.getEnergyStorage();
      ModNetworking.sendToPlayer(serverPlayer, new PacketIEMeterInfo(false,
          storage.energyTier().getLvl(), -1,
          storage.energyTier().getBasicTransfer(),
          0, 0, 0,
          storage.energyStored(), storage.maxEnergy(),
          storage.energyType() == EnergyType.EXTRACT, storage.getLastGenerated(), storage.getTotalGenerated(),
          storage.energyType() == EnergyType.RECEIVE, storage.getLastConsumed(), storage.getTotalConsumed(),
          0, 0, storage.getDemandShortfall(),
          storage.energyType() == EnergyType.RECEIVE ? storage.maxReceiveTick() : 0));
      return InteractionResults.serverSwing(level);
    }

    return super.useOn(context);
  }
}
