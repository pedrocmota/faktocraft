package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.Faktocraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RecipeImport {

  public static final String CHANCE_SLOT = "chance";
  public static final int BREWING_BATCH = 3;
  public static final int SCAN_LIMIT = BlockEntityRecipePipe.MAX_TAG_ITEMS + 1;

  private static final String KEY = "logistics." + Faktocraft.MODID + ".craft.";

  public enum Note {
    FLUIDS, CHANCE, TRUNCATED
  }

  public record Slot(boolean input, List<ItemStack> alternatives, ItemStack shown, boolean chance, boolean other) {
  }

  public record Entry(ItemStack stack, int count, String tag, IoMode mode) {

    public Entry {
      stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
      count = Math.max(1, Math.min(BlockEntityRecipePipe.MAX_COUNT, count));
      tag = tag == null ? "" : tag;
      mode = mode == null ? IoMode.PER_UNIT : mode;
    }

    Entry withCount(int newCount) {
      return new Entry(stack, newCount, tag, mode);
    }

    Entry withMode(IoMode newMode) {
      return new Entry(stack, count, tag, newMode);
    }

    boolean sameAs(Entry other) {
      return tag.equals(other.tag) && ItemKey.of(stack).equals(ItemKey.of(other.stack));
    }
  }

  public record Plan(List<Entry> inputs, List<Entry> outputs, int batch, Set<Note> notes) {

    public boolean complete() {
      return !inputs.isEmpty() && !outputs.isEmpty();
    }
  }

  private RecipeImport() {
  }

  public static Plan plan(List<Slot> slots, int batch) {
    Set<Note> notes = EnumSet.noneOf(Note.class);
    List<Entry> inputs = new ArrayList<>();
    List<Entry> outputs = new ArrayList<>();
    for (Slot slot : slots) {
      List<ItemStack> items = slot.alternatives().stream().filter(stack -> !stack.isEmpty()).toList();
      if (items.isEmpty()) {
        if (slot.other()) {
          notes.add(Note.FLUIDS);
        }
        continue;
      }
      if (!slot.input() && slot.chance()) {
        notes.add(Note.CHANCE);
        continue;
      }
      ItemStack shown = slot.shown().isEmpty() ? items.get(0) : slot.shown();
      String tag = slot.input() ? inferTag(items) : "";
      merge(slot.input() ? inputs : outputs, new Entry(shown, shown.getCount(), tag, IoMode.PER_UNIT));
    }
    markCatalysts(inputs, outputs);
    int units = batch > 1 ? applyBatch(inputs, batch) : 1;
    return new Plan(limit(inputs, BlockEntityRecipePipe.MAX_INPUTS, notes),
        limit(outputs, BlockEntityRecipePipe.MAX_OUTPUTS, notes),
        Math.max(1, Math.min(BlockEntityRecipePipe.MAX_BATCH, units)), Collections.unmodifiableSet(notes));
  }

  @Nullable
  public static Component problem(Plan plan) {
    if (plan.complete()) {
      return null;
    }
    boolean onlyChance = plan.outputs().isEmpty() && plan.notes().contains(Note.CHANCE);
    return Component.translatable(KEY + (onlyChance ? "transfer_no_fixed_output" : "transfer_items_only"));
  }

  public static List<Component> describe(Plan plan) {
    List<Component> lines = new ArrayList<>();
    lines.add(Component.translatable(KEY + "transfer_preview"));
    for (Entry input : plan.inputs()) {
      lines.add(Component.literal("  ").append(line(input, true)).withStyle(ChatFormatting.GRAY));
    }
    for (Entry output : plan.outputs()) {
      lines.add(Component.literal("→ ").append(line(output, false)).withStyle(ChatFormatting.GRAY));
    }
    if (plan.batch() > 1) {
      lines.add(Component.translatable(KEY + "transfer_batch", plan.batch()).withStyle(ChatFormatting.DARK_GRAY));
    }
    for (Note note : plan.notes()) {
      lines.add(Component.translatable(KEY + "transfer_" + note.name().toLowerCase(Locale.ROOT))
          .withStyle(ChatFormatting.YELLOW));
    }
    return lines;
  }

  private static MutableComponent line(Entry entry, boolean input) {
    MutableComponent text = Component.literal(entry.count() + "× ").append(entry.tag().isEmpty()
        ? entry.stack().getHoverName()
        : Component.translatable(KEY + "tag_mode", "#" + entry.tag()));
    if (input && entry.mode() != IoMode.PER_UNIT) {
      text.append(" (").append(Component.translatable(KEY + "mode." + entry.mode().name().toLowerCase(Locale.ROOT)))
          .append(")");
    }
    return text;
  }

  private static String inferTag(List<ItemStack> items) {
    if (items.size() < 2 || items.size() > BlockEntityRecipePipe.MAX_TAG_ITEMS) {
      return "";
    }
    int count = items.get(0).getCount();
    Set<Item> distinct = new HashSet<>();
    for (ItemStack stack : items) {
      if (!stack.getComponentsPatch().isEmpty() || stack.getCount() != count) {
        return "";
      }
      distinct.add(stack.getItem());
    }
    if (distinct.size() < 2) {
      return "";
    }
    return items.get(0).typeHolder().tags()
        .filter(key -> sameMembers(key, distinct))
        .map(key -> key.location().toString())
        .min(Comparator.comparing((String id) -> !id.startsWith("c:")).thenComparing(Comparator.naturalOrder()))
        .orElse("");
  }

  private static boolean sameMembers(TagKey<Item> key, Set<Item> items) {
    HolderSet.Named<Item> set = BuiltInRegistries.ITEM.get(key).orElse(null);
    if (set == null || set.size() != items.size()) {
      return false;
    }
    for (Holder<Item> holder : set) {
      if (!items.contains(holder.value())) {
        return false;
      }
    }
    return true;
  }

  private static void merge(List<Entry> entries, Entry entry) {
    for (int i = 0; i < entries.size(); i++) {
      Entry existing = entries.get(i);
      if (existing.sameAs(entry)) {
        entries.set(i, existing.withCount(existing.count() + entry.count()));
        return;
      }
    }
    entries.add(entry);
  }

  private static void markCatalysts(List<Entry> inputs, List<Entry> outputs) {
    for (int i = 0; i < inputs.size(); i++) {
      Entry input = inputs.get(i);
      if (outputs.size() < 2 || consumed(inputs) < 2) {
        return;
      }
      for (int o = 0; o < outputs.size(); o++) {
        Entry output = outputs.get(o);
        if (input.tag().isEmpty() && output.sameAs(input) && output.count() == input.count()) {
          inputs.set(i, input.withMode(IoMode.MAINTAIN));
          outputs.remove(o);
          break;
        }
      }
    }
  }

  private static int consumed(List<Entry> inputs) {
    int consumed = 0;
    for (Entry input : inputs) {
      if (input.mode() != IoMode.MAINTAIN) {
        consumed++;
      }
    }
    return consumed;
  }

  private static int applyBatch(List<Entry> inputs, int batch) {
    boolean carrier = true;
    for (int i = 0; i < inputs.size(); i++) {
      Entry input = inputs.get(i);
      if (input.mode() == IoMode.MAINTAIN) {
        continue;
      }
      if (carrier) {
        inputs.set(i, input.withCount(input.count() % batch == 0 ? input.count() / batch : input.count()));
        carrier = false;
      } else if (input.mode() == IoMode.PER_UNIT) {
        inputs.set(i, input.withMode(IoMode.PER_BATCH));
      }
    }
    return carrier ? 1 : batch;
  }

  private static List<Entry> limit(List<Entry> entries, int max, Set<Note> notes) {
    if (entries.size() <= max) {
      return List.copyOf(entries);
    }
    notes.add(Note.TRUNCATED);
    return List.copyOf(entries.subList(0, max));
  }
}
