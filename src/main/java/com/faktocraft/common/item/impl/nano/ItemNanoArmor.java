package com.faktocraft.common.item.impl.nano;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.ModArmorMaterials;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.item.base.ElectricArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

public class ItemNanoArmor extends ElectricArmorItem {

  public static final int SHOCK_COST = 500;
  public static final int FIRE_COST = 250;
  public static final int FLUID_COST_PER_TICK = 25;

  public ItemNanoArmor(ArmorType armorType, Properties properties) {
    super(ModArmorMaterials.NANO, armorType, properties, 0, 1000000, EnergyType.RECEIVE, EnergyTier.HIGH);
  }

  protected ItemNanoArmor(ArmorMaterial material, ArmorType armorType, Properties properties, int maxEnergy,
      EnergyTier energyTier) {
    super(material, armorType, properties, 0, maxEnergy, EnergyType.RECEIVE, energyTier);
  }

  protected String protectionTooltipKey() {
    return "tooltip." + Faktocraft.MODID + ".nano_protection";
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable(protectionTooltipKey())
        .withStyle(ChatFormatting.GRAY));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }

  @Override
  public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner,
      @Nullable EquipmentSlot slot) {
    super.inventoryTick(stack, level, owner, slot);
    if (!(owner instanceof Player)) {
      return;
    }
    if (getType() == ArmorType.CHESTPLATE
        && owner instanceof LivingEntity living && living.isOnFire()
        && living.getItemBySlot(EquipmentSlot.CHEST) == stack
        && isFullSuit(living) && hasAnyCharge(living)) {
      living.clearFire();
    }
  }

  private static boolean hasAnyCharge(LivingEntity living) {
    for (EquipmentSlot slot : new EquipmentSlot[] {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
      IEnergy energy = energyOf(living.getItemBySlot(slot));
      if (energy != null && energy.energyStored() > 0) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static IEnergy energyOf(ItemStack stack) {
    return stack.getItem() instanceof IElectricItem electricItem ? electricItem.getEnergy(stack) : null;
  }

  public static boolean tryUseShockProtection(LivingEntity living) {
    ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
    if (!(boots.getItem() instanceof ItemNanoArmor)) {
      return false;
    }
    IEnergy energy = energyOf(boots);
    if (energy == null || energy.energyStored() < SHOCK_COST) {
      return false;
    }
    energy.consumeEnergy(SHOCK_COST, false);
    ((IElectricItem) boots.getItem()).tickElectric(boots);
    return true;
  }

  public static boolean isFullSuit(LivingEntity living) {
    return living.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ItemNanoArmor
        && living.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ItemNanoArmor
        && living.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ItemNanoArmor
        && living.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof ItemNanoArmor;
  }

  public static boolean tryUseFullSuit(LivingEntity living, int totalCost) {
    if (!isFullSuit(living)) {
      return false;
    }
    EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    long available = 0;
    for (EquipmentSlot slot : slots) {
      IEnergy energy = energyOf(living.getItemBySlot(slot));
      if (energy != null) {
        available += energy.energyStored();
      }
    }
    if (available < totalCost) {
      return false;
    }
    int remaining = totalCost;
    for (EquipmentSlot slot : slots) {
      if (remaining <= 0) {
        break;
      }
      ItemStack piece = living.getItemBySlot(slot);
      IEnergy energy = energyOf(piece);
      if (energy == null) {
        continue;
      }
      int take = Math.min(remaining, energy.energyStored());
      if (take > 0) {
        energy.consumeEnergy(take, false);
        ((IElectricItem) piece.getItem()).tickElectric(piece);
        remaining -= take;
      }
    }
    return true;
  }
}
