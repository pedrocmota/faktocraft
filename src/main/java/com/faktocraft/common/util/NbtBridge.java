package com.faktocraft.common.util;

import com.faktocraft.Faktocraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class NbtBridge {
  public static final int LEGACY_DATA_VERSION = 3465;

  public static final MapCodec<CompoundTag> WHOLE = new MapCodec<>() {
    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
      return Stream.empty();
    }

    @Override
    public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
      CompoundTag tag = new CompoundTag();
      input.entries().forEach(pair -> {
        String key = ops.getStringValue(pair.getFirst()).result().orElse(null);
        if (key != null) {
          Tag value = ops.convertTo(NbtOps.INSTANCE, pair.getSecond());
          tag.put(key, value);
        }
      });
      return DataResult.success(tag);
    }

    @Override
    public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
      for (String key : input.keySet()) {
        Tag value = input.get(key);
        if (value != null) {
          prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value));
        }
      }
      return prefix;
    }
  };

  private static final ThreadLocal<HolderLookup.Provider> CURRENT = new ThreadLocal<>();
  @Nullable
  private static volatile HolderLookup.Provider fallback;

  private NbtBridge() {
  }

  public static CompoundTag blockStateTag(CompoundTag tag) {
    if (tag.contains("id") || !tag.contains("Name")) {
      return tag;
    }
    CompoundTag fixed = new CompoundTag();
    fixed.putString("id", tag.getStringOr("Name", "minecraft:air"));
    if (tag.contains("Properties")) {
      fixed.put("properties", tag.getCompoundOrEmpty("Properties"));
    }
    return fixed;
  }

  public static void setFallbackRegistries(@Nullable HolderLookup.Provider provider) {
    fallback = provider;
  }

  public static HolderLookup.Provider registries() {
    HolderLookup.Provider current = CURRENT.get();
    if (current != null) {
      return current;
    }
    HolderLookup.Provider client = ClientProxy.get().clientThreadRegistries();
    if (client != null) {
      return client;
    }
    HolderLookup.Provider fb = fallback;
    return fb != null ? fb : RegistryAccess.EMPTY;
  }

  public static RegistryAccess registryAccess() {
    HolderLookup.Provider current = CURRENT.get();
    if (current instanceof RegistryAccess access) {
      return access;
    }
    if (ClientProxy.get().clientThreadRegistries() instanceof RegistryAccess client) {
      return client;
    }
    HolderLookup.Provider fb = fallback;
    return fb instanceof RegistryAccess access ? access : RegistryAccess.EMPTY;
  }

  public static RegistryOps<Tag> ops() {
    return RegistryOps.create(NbtOps.INSTANCE, registries());
  }

  public static void with(HolderLookup.Provider provider, Runnable action) {
    HolderLookup.Provider previous = CURRENT.get();
    CURRENT.set(provider);
    try {
      action.run();
    } finally {
      CURRENT.set(previous);
    }
  }

  public static <T> T withResult(HolderLookup.Provider provider, java.util.function.Supplier<T> action) {
    HolderLookup.Provider previous = CURRENT.get();
    CURRENT.set(provider);
    try {
      return action.get();
    } finally {
      CURRENT.set(previous);
    }
  }

  @SuppressWarnings("deprecation")
  public static CompoundTag read(ValueInput input) {
    return input.read(WHOLE).orElseGet(CompoundTag::new);
  }

  @SuppressWarnings("deprecation")
  public static void write(ValueOutput output, CompoundTag tag) {
    output.store(WHOLE, tag);
  }

  @SuppressWarnings("deprecation")
  public static void load(ValueInput input, Consumer<CompoundTag> loader) {
    with(input.lookup(), () -> loader.accept(read(input)));
  }

  public static void save(ValueOutput output, Consumer<CompoundTag> saver) {
    CompoundTag tag = new CompoundTag();
    saver.accept(tag);
    write(output, tag);
  }

  public static CompoundTag saveStack(ItemStack stack) {
    if (stack.isEmpty()) {
      return new CompoundTag();
    }
    Tag encoded = ItemStack.CODEC.encodeStart(ops(), stack).resultOrPartial(NbtBridge::warn).orElse(null);
    return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
  }

  public static ItemStack loadStack(CompoundTag tag) {
    if (tag == null || tag.isEmpty()) {
      return ItemStack.EMPTY;
    }
    boolean legacy = isLegacyStack(tag);
    Tag fixed = legacy ? fixLegacyStack(tag) : tag;
    ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops(), fixed).resultOrPartial(NbtBridge::warn)
        .orElse(ItemStack.EMPTY);
    if (legacy && !stack.isEmpty()) {
      migrateLegacyComponents(stack);
    }
    return stack;
  }

  public static void migrateLegacyComponents(ItemStack stack) {
    CustomData raw = stack.get(DataComponents.CUSTOM_DATA);
    if (raw == null || !(raw.contains("energy") || raw.contains("fluid"))) {
      return;
    }
    CompoundTag data = raw.copyTag();
    if (data.contains("energy")) {
      com.faktocraft.common.registries.ModComponents.setEnergy(stack, data.getIntOr("energy", 0));
    }
    if (data.contains("fluid")) {
      net.minecraft.resources.Identifier fluid = net.minecraft.resources.Identifier.tryParse(
          data.getStringOr("fluid", ""));
      int amount = data.getIntOr("fluid_amount", 0);
      if (fluid != null) {
        com.faktocraft.common.registries.ModComponentsFluids.setFluid(stack, fluid);
        com.faktocraft.common.registries.ModComponentsFluids.setFluidAmount(stack, amount);
      }
    }
  }

  public static boolean isLegacyStack(CompoundTag tag) {
    return tag.contains("Count") || (tag.contains("tag") && !tag.contains("count"));
  }

  public static Tag fixLegacyStack(CompoundTag tag) {
    try {
      Dynamic<Tag> fixed = DataFixers.getDataFixer().update(References.ITEM_STACK,
          new Dynamic<>(NbtOps.INSTANCE, tag), LEGACY_DATA_VERSION,
          SharedConstants.getCurrentVersion().dataVersion().version());
      return fixed.getValue();
    } catch (RuntimeException e) {
      Faktocraft.LOGGER.warn("Could not upgrade legacy item stack {}: {}", tag, e.toString());
      return tag;
    }
  }

  public static void saveItems(CompoundTag tag, NonNullList<ItemStack> stacks) {
    ListTag list = new ListTag();
    for (int i = 0; i < stacks.size(); i++) {
      ItemStack stack = stacks.get(i);
      if (!stack.isEmpty()) {
        CompoundTag itemTag = saveStack(stack);
        itemTag.putByte("Slot", (byte) i);
        list.add(itemTag);
      }
    }
    tag.put("Items", list);
  }

  public static void loadItems(CompoundTag tag, NonNullList<ItemStack> stacks) {
    ListTag list = tag.getListOrEmpty("Items");
    for (int i = 0; i < list.size(); i++) {
      CompoundTag itemTag = list.getCompoundOrEmpty(i);
      int slot = itemTag.getByteOr("Slot", (byte) 0) & 255;
      if (slot < stacks.size()) {
        CompoundTag copy = itemTag.copy();
        copy.remove("Slot");
        stacks.set(slot, loadStack(copy));
      }
    }
  }

  public static CompoundTag saveFluid(FluidStack stack) {
    if (stack.isEmpty()) {
      return new CompoundTag();
    }
    Tag encoded = FluidStack.CODEC.encodeStart(ops(), stack).resultOrPartial(NbtBridge::warn).orElse(null);
    return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
  }

  public static FluidStack loadFluid(CompoundTag tag) {
    if (tag == null || tag.isEmpty()) {
      return FluidStack.EMPTY;
    }
    if (tag.contains("FluidName")) {
      CompoundTag modern = new CompoundTag();
      modern.putString("id", tag.getStringOr("FluidName", "minecraft:empty"));
      modern.putInt("amount", Math.max(1, tag.getIntOr("Amount", 0)));
      tag = modern;
    }
    return FluidStack.OPTIONAL_CODEC.parse(ops(), tag).resultOrPartial(NbtBridge::warn).orElse(FluidStack.EMPTY);
  }

  @Nullable
  public static CompoundTag customData(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null || data.isEmpty() ? null : data.copyTag();
  }

  public static CompoundTag customDataOrEmpty(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? new CompoundTag() : data.copyTag();
  }

  public static boolean hasCustomData(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data != null && !data.isEmpty();
  }

  public static void updateCustomData(ItemStack stack, Consumer<CompoundTag> updater) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
  }

  public static void setCustomData(ItemStack stack, @Nullable CompoundTag tag) {
    if (tag == null || tag.isEmpty()) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
  }

  private static void warn(String message) {
    Faktocraft.LOGGER.warn("NBT bridge: {}", message);
  }

  public static <T> Codec<T> codec(MapCodec<T> mapCodec) {
    return mapCodec.codec();
  }
}
