package com.faktocraft.common.subscribers;

import net.neoforged.fml.common.EventBusSubscriber;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.interfaces.item.IElectricItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = Faktocraft.MODID)
public class ModEvents {

  @SubscribeEvent
  public static void onLevelTick(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
      return;
    }
    com.faktocraft.common.radiation.RadiationManager.tick(level);
    com.faktocraft.common.block.impl.nuke.NukeBlast.tick(level);
    if (level.getGameTime() % 20 != 0) {
      return;
    }
    for (ServerPlayer player : level.players()) {
      com.faktocraft.common.util.NightVisionHandler.check(player);
    }
  }

  @SubscribeEvent
  public static void onServerStarted(ServerStartedEvent event) {
    com.faktocraft.common.block.impl.chunk_loader.ChunkLoaderManager.reloadTickets(event.getServer());
  }

  @SubscribeEvent
  public static void onItemTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
    if (com.faktocraft.common.radiation.Radioactivity.isRadioactive(event.getItemStack())) {
      event.getToolTip().add(net.minecraft.network.chat.Component
          .translatable("tooltip." + Faktocraft.MODID + ".radioactive")
          .withStyle(net.minecraft.ChatFormatting.GREEN));
    }
  }

  @SubscribeEvent
  public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
    if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide()) {
      com.faktocraft.common.energy.provider.EnergyCore.removeClientCore(level);
    } else if (event.getLevel() instanceof ServerLevel serverLevel) {
      com.faktocraft.common.energy.WindSim.clear(serverLevel.dimension());
      com.faktocraft.common.energy.WindFarmRegistry.clear(serverLevel.dimension());
      com.faktocraft.common.radiation.RadiationManager.clear(serverLevel.dimension());
    }
  }

  @SubscribeEvent
  public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
    if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
      com.faktocraft.common.radiation.RadiationManager.track(living);
    }
  }

  @SubscribeEvent
  public static void onLivingHurt(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
      return;
    }
    for (ItemStack stack : com.faktocraft.common.util.NightVisionHandler.armorItems(player)) {
      if (!stack.isEmpty() && stack.getItem() instanceof IElectricItem electricItem) {
        IEnergy energy = electricItem.getEnergy(stack);
        if (energy != null) {
          energy.consumeEnergy((int) event.getNewDamage() * 500, false);
          electricItem.tickElectric(stack);
        }
      }
    }
  }

  @SubscribeEvent
  public static void onLivingFall(LivingFallEvent event) {
    if (event.getEntity() instanceof Player player
        && (com.faktocraft.common.item.impl.armor.HazmatArmorItem.hasBoots(player)
            || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
                .getItem() instanceof com.faktocraft.common.item.impl.nano.ItemNanoArmor)) {
      event.setDistance(event.getDistance() / 3.3F);
    }
  }

  @SubscribeEvent
  public static void onLivingAttack(LivingIncomingDamageEvent event) {
    if (event.getEntity().level().isClientSide()) {
      return;
    }
    if (fireProtection(event.getEntity(), event.getSource())) {
      event.setCanceled(true);
    }
  }

  public static boolean fireProtection(net.minecraft.world.entity.LivingEntity living,
      net.minecraft.world.damagesource.DamageSource source) {
    if (!source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
      return false;
    }
    if (com.faktocraft.common.item.impl.armor.HazmatArmorItem.isFullSuit(living)) {
      if (living.getRandom().nextBoolean()) {
        com.faktocraft.common.item.impl.armor.HazmatArmorItem.wearFullSuit(living, 1);
      }
      return true;
    }
    if (com.faktocraft.common.item.impl.nano.ItemNanoArmor.tryUseFullSuit(living,
        com.faktocraft.common.item.impl.nano.ItemNanoArmor.FIRE_COST)) {
      living.clearFire();
      return true;
    }
    return false;
  }

  @SubscribeEvent
  public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
    com.faktocraft.common.item.impl.tools.VeinMining.onBreak(event);
  }

  @SubscribeEvent
  public static void onPlayerLoggedOut(
      net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
    com.faktocraft.common.item.impl.tools.VeinMining.forget(event.getEntity());
  }

  @SubscribeEvent
  public static void onContainerOpen(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getContainer() instanceof com.faktocraft.common.block.impl.logistics.MenuRequestTable menu) {
      com.faktocraft.common.network.packet.PacketReqTableState.sendTo(player, menu);
    }
  }
}
