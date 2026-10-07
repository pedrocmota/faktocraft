package com.faktocraft.common.item.impl.armor;

import com.faktocraft.common.enums.ModArmorMaterials;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.item.base.BaseArmor;
import com.faktocraft.common.item.base.FluidItem;
import com.faktocraft.common.item.base.FluidItemHandlerProvider;
import com.faktocraft.common.util.transfer.CapabilityBridge;
import com.faktocraft.common.util.transfer.IFluidHandlerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;
import java.util.function.Consumer;

public class JetpackItem extends BaseArmor implements CapabilityBridge.IFluidHandlerItemProvider {
  public static final String TAG_THRUST = "faktocraftJetpackThrust";

  public static final String TAG_GLIDE = "faktocraftJetpackGlide";

  public static final String TAG_AIR_TICKS = "faktocraftJetpackAir";

  public static final int VERTICAL_MB_PER_TICK = 2;
  public static final int GLIDE_MB_PER_4_TICKS = 1;
  public static final int BOOST_MB_PER_TICK = 6;

  private final int capacityMb;

  public JetpackItem(Properties properties) {
    this(ModArmorMaterials.JETPACK, properties, 6000);
  }

  protected JetpackItem(ArmorMaterial material, Properties properties, int capacityMb) {
    super(material, ArmorType.CHESTPLATE, properties.stacksTo(1));
    this.capacityMb = capacityMb;
  }

  public int getFluidCapacity() {
    return capacityMb;
  }

  public static boolean isJetpackFuel(Fluid fluid) {
    return fluid.isSame(ModFluids.FUEL.still()) || fluid.isSame(ModFluids.BIOGAS.still());
  }

  private static void normalizeToFuel(ItemStack stack) {
    if (FluidItem.getFluid(stack).isSame(ModFluids.BIOGAS.still())) {
      FluidItem.setFluid(stack, ModFluids.FUEL.still(), FluidItem.getFluidAmount(stack) / 2);
    }
  }

  public static boolean drainFuel(ItemStack stack, int cost, boolean simulate) {
    normalizeToFuel(stack);
    int amount = FluidItem.getFluidAmount(stack);
    if (amount < cost) {
      return false;
    }
    if (!simulate) {
      FluidItem.setFluid(stack, ModFluids.FUEL.still(), amount - cost);
    }
    return true;
  }

  @Override
  public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
    super.inventoryTick(stack, level, entity, slot);
    wornTick(stack, level, entity);
  }

  public void wornTick(ItemStack stack, Level level, Entity entity) {
    if (entity instanceof Player player && player.getItemBySlot(EquipmentSlot.CHEST) == stack) {
      if (player.onGround()) {
        player.getPersistentData().remove(TAG_GLIDE);
        player.getPersistentData().putInt(TAG_AIR_TICKS, 0);
      } else {
        player.getPersistentData().putInt(TAG_AIR_TICKS,
            player.getPersistentData().getIntOr(TAG_AIR_TICKS, 0) + 1);
      }
      jetpackTick(stack, level, player);
      if (player instanceof ServerPlayer serverPlayer && (player.getPersistentData().getBooleanOr(TAG_THRUST, false)
          || player.getPersistentData().getBooleanOr(TAG_GLIDE, false))) {
        resetFloatingTicks(serverPlayer);
      }
    }
  }

  // Resolved lazily: touching ServerGamePacketListenerImpl during item registration forces that class
  // (and every mixin targeting it) to load early, which crashes the game if another mod's mixin fails.
  private static final class FloatingFields {
    @Nullable
    static final Field ABOVE_GROUND_TICKS = findFloatingField("aboveGroundTickCount");

    @Nullable
    static final Field ABOVE_GROUND_VEHICLE_TICKS = findFloatingField("aboveGroundVehicleTickCount");
  }

  @Nullable
  private static Field findFloatingField(String name) {
    try {
      return ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, name);
    } catch (Exception helperFailure) {
      try {
        Field field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (Exception reflectionFailure) {
        return null;
      }
    }
  }

  private static void resetFloatingTicks(ServerPlayer player) {
    ServerGamePacketListenerImpl connection = player.connection;
    if (connection == null) {
      return;
    }
    resetFloatingField(FloatingFields.ABOVE_GROUND_TICKS, connection);
    resetFloatingField(FloatingFields.ABOVE_GROUND_VEHICLE_TICKS, connection);
  }

  private static void resetFloatingField(@Nullable Field field, ServerGamePacketListenerImpl connection) {
    if (field == null) {
      return;
    }
    try {
      field.setInt(connection, 0);
    } catch (Exception failure) {
      return;
    }
  }

  public static final float GLIDE_FALL_SPEED = 0.15F;

  protected void jetpackTick(ItemStack stack, Level level, Player player) {
    if (player.isFallFlying() || !player.getPersistentData().getBooleanOr(TAG_THRUST, false)) {
      return;
    }
    if (!drainFuel(stack, VERTICAL_MB_PER_TICK, true)) {
      glideTick(level, player);
      return;
    }
    player.getPersistentData().putBoolean(TAG_GLIDE, true);
    Vec3 dm = player.getDeltaMovement();
    if (dm.y < 0.45) {
      player.setDeltaMovement(dm.x, Math.min(0.45, dm.y + 0.15), dm.z);
    }
    player.resetFallDistance();
    if (level.isClientSide()) {
      spawnNozzleParticles(level, player);
    } else {
      drainFuel(stack, VERTICAL_MB_PER_TICK, false);
    }
  }

  protected static void glideTick(Level level, Player player) {
    if (player.onGround() || player.isInWater() || player.getAbilities().flying
        || !player.getPersistentData().getBooleanOr(TAG_GLIDE, false)) {
      return;
    }
    Vec3 dm = player.getDeltaMovement();
    if (dm.y >= 0) {
      return;
    }
    if (dm.y < -GLIDE_FALL_SPEED) {
      player.setDeltaMovement(dm.x, -GLIDE_FALL_SPEED, dm.z);
    }
    player.resetFallDistance();
    if (level.isClientSide() && level.getRandom().nextInt(3) == 0) {
      float yaw = player.yBodyRot * ((float) Math.PI / 180F);
      double backX = net.minecraft.util.Mth.sin(yaw) * 0.32;
      double backZ = -net.minecraft.util.Mth.cos(yaw) * 0.32;
      level.addParticle(ParticleTypes.SMOKE,
          player.getX() + backX, player.getY() + 0.8, player.getZ() + backZ, 0.0, -0.1, 0.0);
    }
  }

  protected static void spawnNozzleParticles(Level level, Player player) {
    float yaw = player.yBodyRot * ((float) Math.PI / 180F);
    double backX = net.minecraft.util.Mth.sin(yaw) * 0.32;
    double backZ = -net.minecraft.util.Mth.cos(yaw) * 0.32;
    double sideX = net.minecraft.util.Mth.cos(yaw) * 0.14;
    double sideZ = net.minecraft.util.Mth.sin(yaw) * 0.14;
    double y = player.getY() + 0.8;
    for (int i = -1; i <= 1; i += 2) {
      double x = player.getX() + backX + sideX * i;
      double z = player.getZ() + backZ + sideZ * i;
      level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, -0.35, 0.0);
      if (level.getRandom().nextInt(3) == 0) {
        level.addParticle(ParticleTypes.SMOKE, x, y - 0.1, z, 0.0, -0.25, 0.0);
      }
    }
  }

  @Override
  public IFluidHandlerItem createFluidHandler(ItemStack stack) {
    return new FluidItemHandlerProvider(stack, capacityMb) {
      @Override
      public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isJetpackFuel(resource.getFluid())) {
          return 0;
        }
        normalizeToFuel(stack);
        int stored = FluidItem.getFluidAmount(stack);
        int space = capacityMb - stored;
        if (space <= 0) {
          return 0;
        }
        boolean biogas = resource.getFluid().isSame(ModFluids.BIOGAS.still());
        int accepted = biogas
            ? Math.min(resource.getAmount() - (resource.getAmount() % 2), space * 2)
            : Math.min(resource.getAmount(), space);
        if (accepted <= 0) {
          return 0;
        }
        if (action.execute()) {
          FluidItem.setFluid(stack, ModFluids.FUEL.still(), stored + (biogas ? accepted / 2 : accepted));
        }
        return accepted;
      }

      @NotNull
      @Override
      public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
      }

      @NotNull
      @Override
      public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
      }
    };
  }

  @Override
  public boolean isBarVisible(ItemStack stack) {
    return true;
  }

  @Override
  public int getBarWidth(ItemStack stack) {
    return Math.round(13.0F * FluidItem.getFluidAmount(stack) / capacityMb);
  }

  @Override
  public int getBarColor(ItemStack stack) {
    return net.minecraft.util.Mth.hsvToRgb(0.09F, 1.0F, 1.0F);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    Fluid fluid = FluidItem.getFluid(stack);
    if (fluid != Fluids.EMPTY) {
      tooltip.accept(Component.literal("< " + FluidItem.getFluidAmount(stack) + " / " + capacityMb + " mB, ")
          .append(FluidItem.getFluidName(fluid))
          .append(" >").withStyle(ChatFormatting.GRAY));
    } else {
      tooltip.accept(Component.translatable("item.faktocraft.empty_fluid").withStyle(ChatFormatting.GRAY));
    }
    super.appendHoverText(stack, level, display, tooltip, flag);
  }

  public static boolean isWearingJetpack(Player player) {
    return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof JetpackItem;
  }
}
