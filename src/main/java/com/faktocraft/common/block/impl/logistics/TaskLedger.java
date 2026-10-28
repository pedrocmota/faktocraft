package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.config.ModConfig;
import com.faktocraft.common.network.packet.PacketLogisticsGhost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TaskLedger {

  private static final int DELIVER_RETRIES = 10;
  private static final int RETRY_INTERVAL = 20;
  private static final int STALL_TIMEOUT_TICKS = 1200;
  private static final int DISPATCH_INTERVAL = 20;
  private static final int MAX_UNITS_PER_TICK = 64;
  public static final int HISTORY_LIMIT = 100;

  public static final class SubRecord {
    String kind = "craft";
    ItemStack item = ItemStack.EMPTY;
    int count;
    String stateKey = "pending";
    ItemStack leftoverItem = ItemStack.EMPTY;
    int leftoverCount;

    String where = "";
    String whereDetail = "";
    long leftoverPos;
    int done;
    int inputsDone;
    int inputsTotal;

    public int done() {
      return done;
    }

    public int inputsDone() {
      return inputsDone;
    }

    public int inputsTotal() {
      return inputsTotal;
    }

    public String kind() {
      return kind;
    }

    public ItemStack item() {
      return item;
    }

    public int count() {
      return count;
    }

    public String stateKey() {
      return stateKey;
    }

    public ItemStack leftoverItem() {
      return leftoverItem;
    }

    public int leftoverCount() {
      return leftoverCount;
    }

    public String where() {
      return where;
    }

    public String whereDetail() {
      return whereDetail;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("kind", kind);
      tag.put("item", item.save(new CompoundTag()));
      tag.putInt("count", count);
      tag.putString("state", stateKey);
      tag.putInt("done", done);
      tag.putInt("inDone", inputsDone);
      tag.putInt("inTotal", inputsTotal);
      if (leftoverCount > 0) {
        tag.put("leftItem", leftoverItem.save(new CompoundTag()));
        tag.putInt("leftCount", leftoverCount);
        tag.putString("where", where);
        tag.putString("whereDetail", whereDetail);
        tag.putLong("leftPos", leftoverPos);
      }
      return tag;
    }

    static SubRecord load(CompoundTag tag) {
      SubRecord sub = new SubRecord();
      sub.kind = tag.getString("kind");
      sub.item = ItemStack.of(tag.getCompound("item"));
      sub.count = tag.getInt("count");
      sub.stateKey = tag.getString("state");
      sub.done = tag.getInt("done");
      sub.inputsDone = tag.getInt("inDone");
      sub.inputsTotal = tag.getInt("inTotal");
      if (tag.contains("leftCount")) {
        sub.leftoverItem = ItemStack.of(tag.getCompound("leftItem"));
        sub.leftoverCount = tag.getInt("leftCount");
        sub.where = tag.getString("where");
        sub.whereDetail = tag.getString("whereDetail");
        sub.leftoverPos = tag.getLong("leftPos");
      }
      return sub;
    }
  }

  public static final class HistoryRecord {
    long id;
    ItemStack stack = ItemStack.EMPTY;
    int count;
    String stateKey = "done";
    String detail = "";
    boolean system;
    long originPos;
    String originLabel = "";
    int delivered;
    final List<SubRecord> subs = new ArrayList<>();

    public long id() {
      return id;
    }

    public int delivered() {
      return delivered;
    }

    public boolean system() {
      return system;
    }

    public long originPos() {
      return originPos;
    }

    public String originLabel() {
      return originLabel;
    }

    public ItemStack stack() {
      return stack;
    }

    public int count() {
      return count;
    }

    public String stateKey() {
      return stateKey;
    }

    public String detail() {
      return detail;
    }

    public List<SubRecord> subs() {
      return subs;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putLong("id", id);
      tag.put("stack", stack.save(new CompoundTag()));
      tag.putInt("count", count);
      tag.putString("state", stateKey);
      tag.putString("detail", detail);
      tag.putBoolean("system", system);
      tag.putLong("origin", originPos);
      tag.putString("originLabel", originLabel);
      tag.putInt("delivered", delivered);
      ListTag subsTag = new ListTag();
      for (SubRecord sub : subs) {
        subsTag.add(sub.save());
      }
      tag.put("subs", subsTag);
      return tag;
    }

    static HistoryRecord load(CompoundTag tag) {
      HistoryRecord record = new HistoryRecord();
      record.id = tag.getLong("id");
      record.stack = ItemStack.of(tag.getCompound("stack"));
      record.count = tag.getInt("count");
      record.stateKey = tag.getString("state");
      record.detail = tag.getString("detail");
      record.system = tag.getBoolean("system");
      record.originPos = tag.getLong("origin");
      record.originLabel = tag.getString("originLabel");
      record.delivered = tag.getInt("delivered");
      for (Tag element : tag.getList("subs", Tag.TAG_COMPOUND)) {
        record.subs.add(SubRecord.load((CompoundTag) element));
      }
      return record;
    }
  }

  public record TaskSummary(long id, ItemStack stack, int count, String stateKey, String detail,
      boolean system, long originPos, String originLabel, int delivered, List<SubRecord> subs) {
  }

  public static final class DeliveryTask {
    long id;
    ItemStack stack;
    Endpoint from;
    Endpoint to;
    List<BlockPos> route = List.of();
    int travelTicks = 2;
    int progress;
    int retries;
    int retryCooldown;
    long jobId;
    boolean delivering;
    boolean stockPart;

    boolean leftover;

    long originPos;

    int[] sectionLastIndex = new int[0];

    public int travelTicks() {
      return travelTicks;
    }

    void sliceRoute() {
      Map<Long, Integer> last = new java.util.LinkedHashMap<>();
      for (int i = 0; i < route.size(); i++) {
        BlockPos pos = route.get(i);
        last.put(SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), 0,
            SectionPos.blockToSectionCoord(pos.getZ())), i);
      }
      sectionLastIndex = new int[last.size()];
      int i = 0;
      for (int index : last.values()) {
        sectionLastIndex[i++] = index;
      }
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putLong("id", id);

      tag.put("stack", stack.copyWithCount(Math.min(1, stack.getCount())).save(new CompoundTag()));
      tag.putInt("stackCount", stack.getCount());
      tag.put("from", from.save());
      tag.put("to", to.save());
      ListTag routeTag = new ListTag();
      for (BlockPos pos : route) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("p", pos.asLong());
        routeTag.add(entry);
      }
      tag.put("route", routeTag);
      tag.putInt("travel", travelTicks);
      tag.putInt("progress", progress);
      tag.putInt("retries", retries);
      tag.putLong("job", jobId);
      tag.putBoolean("delivering", delivering);
      tag.putBoolean("stockPart", stockPart);
      tag.putBoolean("leftover", leftover);
      tag.putLong("origin", originPos);
      return tag;
    }

    static DeliveryTask load(CompoundTag tag) {
      DeliveryTask task = new DeliveryTask();
      task.id = tag.getLong("id");
      task.stack = ItemStack.of(tag.getCompound("stack"));
      if (tag.contains("stackCount") && !task.stack.isEmpty()) {
        task.stack.setCount(Math.max(1, tag.getInt("stackCount")));
      }
      task.from = Endpoint.load(tag.getCompound("from"));
      task.to = Endpoint.load(tag.getCompound("to"));
      List<BlockPos> route = new ArrayList<>();
      for (Tag element : tag.getList("route", Tag.TAG_COMPOUND)) {
        route.add(BlockPos.of(((CompoundTag) element).getLong("p")));
      }
      task.route = route;
      task.travelTicks = Math.max(2, tag.getInt("travel"));
      task.progress = tag.getInt("progress");
      task.retries = tag.getInt("retries");
      task.jobId = tag.getLong("job");
      task.delivering = tag.getBoolean("delivering");
      task.stockPart = tag.getBoolean("stockPart");
      task.leftover = tag.getBoolean("leftover");
      task.originPos = tag.getLong("origin");
      task.sliceRoute();
      return task;
    }
  }

  public static final class StepData {
    ItemStack result = ItemStack.EMPTY;
    int resultCount = 1;
    int times = 1;
    int surplus;
    int timeout;
    List<LogisticsPlanner.ItemCount> ingredients = List.of();

    List<LogisticsPlanner.StationChoice> stations = List.of();
    int chosen;
    boolean machine;
    int batchSize = 1;
    int[] unitCounts = new int[0];
    byte[] modes = new byte[0];
    int[] origins = new int[0];

    boolean claimed;
    boolean queued;
    boolean done;
    int[] remaining = new int[0];
    int[] need = new int[0];
    int unitsStarted;
    int batchUnits;
    int drained;
    int batchProduced;
    int outputBaseline = Integer.MIN_VALUE;
    int wait;
    long mark;
    Map<ItemKey, Integer> delivered = new HashMap<>();
    Set<Long> pending = new HashSet<>();

    LogisticsPlanner.StationChoice station() {
      return stations.get(chosen < stations.size() ? chosen : 0);
    }

    Endpoint output() {
      return station().output();
    }

    List<Endpoint> ingredientEnds() {
      return station().ingredientEnds();
    }

    List<Endpoint> maintainEnds() {
      return station().maintainEnds();
    }

    List<LogisticsPlanner.Maintain> maintains() {
      return station().maintains();
    }

    BlockPos node() {
      return station().node();
    }

    boolean rolling() {
      return batchSize <= 1;
    }

    int target() {
      return times * resultCount;
    }

    int unitCount(int entry) {
      if (entry < unitCounts.length) {
        return Math.max(1, unitCounts[entry]);
      }
      int total = entry < ingredients.size() ? ingredients.get(entry).count() : 1;
      return Math.max(1, total / Math.max(1, times));
    }

    IoMode mode(int entry) {
      return entry < modes.length ? IoMode.of(modes[entry]) : IoMode.PER_UNIT;
    }

    int origin(int entry) {
      return entry < origins.length ? origins[entry] : entry;
    }

    boolean needsEmpty() {
      for (int value : need) {
        if (value > 0) {
          return false;
        }
      }
      return true;
    }

    boolean inFlight() {
      return unitsStarted * resultCount > drained || !needsEmpty() || !pending.isEmpty();
    }

    void prepare() {
      remaining = new int[ingredients.size()];
      for (int i = 0; i < remaining.length; i++) {
        remaining[i] = ingredients.get(i).count();
      }
      need = new int[ingredients.size()];
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.put("result", result.save(new CompoundTag()));
      tag.putInt("rc", resultCount);
      tag.putInt("times", times);
      tag.putInt("surplus", surplus);
      tag.putInt("timeout", timeout);
      ListTag ingTag = new ListTag();
      for (LogisticsPlanner.ItemCount ingredient : ingredients) {
        CompoundTag entry = new CompoundTag();
        entry.put("key", ingredient.item().save());
        entry.putInt("count", ingredient.count());
        ingTag.add(entry);
      }
      tag.put("ings", ingTag);
      ListTag stationsTag = new ListTag();
      for (LogisticsPlanner.StationChoice choice : stations) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("node", choice.node().asLong());
        entry.put("out", choice.output().save());
        if (!choice.shared()) {
          entry.putBoolean("solo", true);
        }
        ListTag endsTag = new ListTag();
        for (Endpoint end : choice.ingredientEnds()) {
          endsTag.add(end.save());
        }
        entry.put("ends", endsTag);
        if (!choice.maintainEnds().isEmpty()) {
          ListTag maintainEndsTag = new ListTag();
          for (Endpoint end : choice.maintainEnds()) {
            maintainEndsTag.add(end.save());
          }
          entry.put("mends", maintainEndsTag);
          entry.put("maint", saveMaintains(choice.maintains()));
        }
        stationsTag.add(entry);
      }
      tag.put("stations", stationsTag);
      tag.putInt("chosen", chosen);
      tag.putBoolean("machine", machine);
      tag.putInt("batch", batchSize);
      tag.putIntArray("unitCounts", unitCounts);
      tag.putByteArray("modes", modes);
      tag.putIntArray("origins", origins);
      tag.putBoolean("done", done);
      tag.putIntArray("rem", remaining);
      tag.putIntArray("need", need);
      tag.putInt("started", unitsStarted);
      tag.putInt("bUnits", batchUnits);
      tag.putInt("drained", drained);
      tag.putInt("bProd", batchProduced);
      tag.putInt("baseline", outputBaseline);
      tag.putInt("wait", wait);
      tag.put("deliv", saveKeyMap(delivered));
      return tag;
    }

    static StepData load(CompoundTag tag) {
      StepData step = new StepData();
      step.result = ItemStack.of(tag.getCompound("result"));
      step.resultCount = Math.max(1, tag.getInt("rc"));
      step.times = Math.max(1, tag.getInt("times"));
      step.surplus = tag.getInt("surplus");
      step.timeout = tag.getInt("timeout");
      List<LogisticsPlanner.ItemCount> ingredients = new ArrayList<>();
      List<Endpoint> legacyEnds = new ArrayList<>();
      for (Tag element : tag.getList("ings", Tag.TAG_COMPOUND)) {
        CompoundTag entry = (CompoundTag) element;

        ItemKey key = entry.contains("key")
            ? ItemKey.load(entry.getCompound("key"))
            : ItemKey.of(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(
                entry.getString("item"))));
        ingredients.add(new LogisticsPlanner.ItemCount(key, entry.getInt("count")));
        if (entry.contains("end")) {
          legacyEnds.add(Endpoint.load(entry.getCompound("end")));
        }
      }
      step.ingredients = ingredients;
      List<LogisticsPlanner.Maintain> legacyMaintains = loadMaintains(tag.getList("maintains", Tag.TAG_COMPOUND));
      List<LogisticsPlanner.StationChoice> stations = new ArrayList<>();
      for (Tag element : tag.getList("stations", Tag.TAG_COMPOUND)) {
        CompoundTag entry = (CompoundTag) element;
        List<Endpoint> ends = new ArrayList<>();
        for (Tag endTag : entry.getList("ends", Tag.TAG_COMPOUND)) {
          ends.add(Endpoint.load((CompoundTag) endTag));
        }
        List<Endpoint> maintainEnds = new ArrayList<>();
        for (Tag endTag : entry.getList("mends", Tag.TAG_COMPOUND)) {
          maintainEnds.add(Endpoint.load((CompoundTag) endTag));
        }
        List<LogisticsPlanner.Maintain> maintains = entry.contains("maint")
            ? loadMaintains(entry.getList("maint", Tag.TAG_COMPOUND))
            : legacyMaintains;
        stations.add(new LogisticsPlanner.StationChoice(BlockPos.of(entry.getLong("node")),
            Endpoint.load(entry.getCompound("out")), ends, maintains, maintainEnds, !entry.getBoolean("solo")));
      }
      if (stations.isEmpty() && tag.contains("out")) {

        stations.add(new LogisticsPlanner.StationChoice(BlockPos.of(tag.getLong("chassis")),
            Endpoint.load(tag.getCompound("out")), legacyEnds));
      }
      step.stations = stations;
      step.chosen = tag.getInt("chosen");
      step.machine = tag.getBoolean("machine");
      if (tag.contains("batch")) {
        step.batchSize = Math.max(1, tag.getInt("batch"));
        step.unitCounts = tag.getIntArray("unitCounts");
        step.modes = tag.getByteArray("modes");
        step.origins = tag.getIntArray("origins");
      }
      step.prepare();
      if (tag.contains("rem") && tag.getIntArray("rem").length == step.remaining.length) {
        step.remaining = tag.getIntArray("rem");
      }
      if (tag.contains("need") && tag.getIntArray("need").length == step.need.length) {
        step.need = tag.getIntArray("need");
      }
      step.done = tag.getBoolean("done");
      step.unitsStarted = tag.getInt("started");
      step.batchUnits = tag.getInt("bUnits");
      step.drained = tag.getInt("drained");
      step.batchProduced = tag.getInt("bProd");
      step.outputBaseline = tag.contains("baseline") ? tag.getInt("baseline") : Integer.MIN_VALUE;
      step.wait = tag.getInt("wait");
      step.delivered = loadKeyMap(tag, "deliv");
      return step;
    }
  }

  static final class Parked {
    ItemKey item = ItemKey.EMPTY;
    int count;
    int step;

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.put("k", item.save());
      tag.putInt("n", count);
      tag.putInt("s", step);
      return tag;
    }

    static Parked load(CompoundTag tag) {
      Parked parked = new Parked();
      parked.item = ItemKey.load(tag.getCompound("k"));
      parked.count = tag.getInt("n");
      parked.step = tag.getInt("s");
      return parked;
    }
  }

  public static final class RequestJob {
    long id;
    ItemStack target = ItemStack.EMPTY;
    int quantity;

    boolean system;
    long originPos;
    String originLabel = "";

    int directQuantity;
    Map<ItemKey, Integer> stockToDeliver = new HashMap<>();
    Set<Long> stockPending = new HashSet<>();
    Endpoint dest;
    BlockPos destNode = BlockPos.ZERO;
    Map<ItemKey, Integer> withdrawalsRemaining = new HashMap<>();
    List<StepData> steps = new ArrayList<>();
    int delivered;
    Set<Long> finalPending = new HashSet<>();
    int dispatchWait;

    int stallWait;
    long stallMark;

    List<Parked> parked = new ArrayList<>();

    List<BlockPos> stations = new ArrayList<>();
    @Nullable
    String error;
    int lingerTicks;
    boolean split;

    int crafted() {
      return Math.max(0, quantity - directQuantity);
    }

    boolean stepsDone() {
      for (StepData step : steps) {
        if (!step.done) {
          return false;
        }
      }
      return true;
    }

    int doneSteps() {
      int count = 0;
      for (StepData step : steps) {
        if (step.done) {
          count++;
        }
      }
      return count;
    }

    void removePending(long taskId) {
      stockPending.remove(taskId);
      finalPending.remove(taskId);
      for (StepData step : steps) {
        step.pending.remove(taskId);
      }
    }

    @Nullable
    StepData pending(long taskId) {
      for (StepData step : steps) {
        if (step.pending.contains(taskId)) {
          return step;
        }
      }
      return null;
    }

    CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("v", 2);
      tag.putLong("id", id);
      tag.put("target", target.save(new CompoundTag()));
      tag.putInt("qty", quantity);
      tag.putBoolean("system", system);
      tag.putLong("origin", originPos);
      tag.putString("originLabel", originLabel);
      tag.putInt("direct", directQuantity);
      tag.put("stockDeliver", saveKeyMap(stockToDeliver));
      tag.put("dest", dest.save());
      tag.putLong("destNode", destNode.asLong());
      tag.put("withdraw", saveKeyMap(withdrawalsRemaining));
      ListTag stepsTag = new ListTag();
      for (StepData step : steps) {
        stepsTag.add(step.save());
      }
      tag.put("steps", stepsTag);
      tag.putInt("delivered", delivered);
      tag.putInt("dispatch", dispatchWait);
      long[] held = new long[stations.size()];
      for (int i = 0; i < held.length; i++) {
        held[i] = stations.get(i).asLong();
      }
      tag.putLongArray("stations", held);
      if (error != null) {
        tag.putString("error", error);
      }
      if (split) {
        tag.putBoolean("split", true);
      }
      if (!parked.isEmpty()) {
        ListTag parkedTag = new ListTag();
        for (Parked entry : parked) {
          parkedTag.add(entry.save());
        }
        tag.put("parked", parkedTag);
      }
      return tag;
    }

    static RequestJob load(CompoundTag tag) {
      RequestJob job = new RequestJob();
      job.id = tag.getLong("id");
      job.target = ItemStack.of(tag.getCompound("target"));
      job.quantity = tag.getInt("qty");
      job.system = tag.getBoolean("system");
      job.originPos = tag.getLong("origin");
      job.originLabel = tag.getString("originLabel");
      job.directQuantity = tag.getInt("direct");
      job.stockToDeliver = loadKeyMap(tag, "stockDeliver");
      job.dest = Endpoint.load(tag.getCompound("dest"));
      job.destNode = BlockPos.of(tag.getLong("destNode"));
      job.withdrawalsRemaining = loadKeyMap(tag, "withdraw");
      for (Tag element : tag.getList("steps", Tag.TAG_COMPOUND)) {
        job.steps.add(StepData.load((CompoundTag) element));
      }
      job.delivered = tag.getInt("delivered");
      job.dispatchWait = tag.getInt("dispatch");
      for (long packed : tag.getLongArray("stations")) {
        job.stations.add(BlockPos.of(packed));
      }
      job.error = tag.contains("error") ? tag.getString("error") : null;
      job.split = tag.getBoolean("split");
      for (Tag element : tag.getList("parked", Tag.TAG_COMPOUND)) {
        Parked entry = Parked.load((CompoundTag) element);
        if (!entry.item.isEmpty() && entry.count > 0) {
          job.parked.add(entry);
        }
      }
      if (tag.getInt("v") < 2) {
        int legacyStage = tag.getBoolean("final") ? job.steps.size() : tag.getInt("stage");
        for (int i = 0; i < job.steps.size(); i++) {
          StepData step = job.steps.get(i);
          step.done = i < legacyStage;
          if (step.done) {
            step.drained = step.target();
          }
        }
        job.stations.clear();
      }
      for (StepData step : job.steps) {
        step.claimed = !step.done && !step.stations.isEmpty() && job.stations.contains(step.output().pos());
        if (!step.claimed) {
          step.outputBaseline = Integer.MIN_VALUE;
        }
      }
      return job;
    }
  }

  private static ListTag saveKeyMap(Map<ItemKey, Integer> map) {
    ListTag list = new ListTag();
    map.forEach((key, count) -> {
      CompoundTag entry = new CompoundTag();
      entry.put("k", key.save());
      entry.putInt("n", count);
      list.add(entry);
    });
    return list;
  }

  private static Map<ItemKey, Integer> loadKeyMap(CompoundTag owner, String name) {
    Map<ItemKey, Integer> map = new HashMap<>();
    if (owner.contains(name, Tag.TAG_LIST)) {
      for (Tag element : owner.getList(name, Tag.TAG_COMPOUND)) {
        CompoundTag entry = (CompoundTag) element;
        ItemKey key = ItemKey.load(entry.getCompound("k"));
        if (!key.isEmpty()) {
          map.put(key, entry.getInt("n"));
        }
      }
      return map;
    }
    CompoundTag legacy = owner.getCompound(name);
    for (String id : legacy.getAllKeys()) {
      ItemKey key = ItemKey.of(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
      if (!key.isEmpty()) {
        map.put(key, legacy.getInt(id));
      }
    }
    return map;
  }

  private final BlockEntityLogisticsController core;
  private long nextId = 1;
  private final List<DeliveryTask> deliveries = new ArrayList<>();
  private final List<RequestJob> jobs = new ArrayList<>();
  private final Map<ItemKey, Integer> reserved = new HashMap<>();

  private final List<HistoryRecord> userHistory = new ArrayList<>();
  private final List<HistoryRecord> systemHistory = new ArrayList<>();

  public List<HistoryRecord> userHistory() {
    return userHistory;
  }

  public List<HistoryRecord> systemHistory() {
    return systemHistory;
  }

  private List<SubRecord> subRecordsFor(RequestJob job) {
    List<SubRecord> subs = new ArrayList<>();
    if (job.directQuantity > 0) {
      SubRecord sub = new SubRecord();
      sub.kind = "deliver";
      sub.item = job.target.copy();
      sub.count = job.directQuantity;
      sub.stateKey = job.stockToDeliver.isEmpty() && job.stockPending.isEmpty() ? "done" : "delivering";
      sub.done = directDelivered(job);
      subs.add(sub);
    }
    for (StepData step : job.steps) {
      SubRecord sub = new SubRecord();
      sub.kind = "craft";
      sub.item = step.result.copy();
      sub.count = step.target();
      sub.done = Math.min(step.target(), step.drained);
      for (LogisticsPlanner.ItemCount ingredient : step.ingredients) {
        sub.inputsTotal += ingredient.count();
      }
      for (int delivered : step.delivered.values()) {
        sub.inputsDone += delivered;
      }
      sub.inputsDone = Math.max(0, Math.min(sub.inputsTotal, sub.inputsDone));
      if (step.done) {
        sub.inputsDone = sub.inputsTotal;
      }
      if (step.done) {
        sub.stateKey = "done";
      } else if (step.claimed) {
        sub.stateKey = step.needsEmpty() ? "producing" : "collecting";
      } else {
        sub.stateKey = canStart(job, step) ? "waiting" : "pending";
      }
      if (step.surplus > 0 && step.done) {
        sub.leftoverItem = step.result.copy();
        sub.leftoverCount = step.surplus;
        sub.where = step.machine ? "machine" : "assembly";
        sub.whereDetail = step.machine ? posText(step.output().pos()) : "";
        sub.leftoverPos = step.output().pos().asLong();
      }
      subs.add(sub);
    }
    return subs;
  }

  private static int directDelivered(RequestJob job) {
    return Math.max(0, job.directQuantity - job.stockToDeliver.getOrDefault(ItemKey.of(job.target), 0));
  }

  private static int deliveredSoFar(RequestJob job) {
    return Math.min(job.quantity, directDelivered(job) + job.delivered);
  }

  private void addHistory(RequestJob job, String stateKey, String detail) {
    HistoryRecord record = newRecord(job.target, job.quantity, stateKey, detail);
    record.id = job.id;
    record.delivered = "done".equals(stateKey) ? job.quantity : deliveredSoFar(job);
    record.system = job.system;
    record.originPos = job.originPos;
    record.originLabel = job.originLabel;
    record.subs.addAll(subRecordsFor(job));

    if (stateKey.startsWith("error.")) {
      String terminal = "error.cancelled".equals(stateKey) ? "cancelled" : "failed";
      for (SubRecord sub : record.subs) {
        if (!"done".equals(sub.stateKey)) {
          sub.stateKey = terminal;
        }
      }
    }
    pushHistory(record);
  }

  public void addRecord(ItemStack stack, int count, String stateKey, String detail, BlockPos origin,
      String originLabel) {
    HistoryRecord record = newRecord(stack, count, stateKey, detail);
    record.originPos = origin.asLong();
    record.originLabel = originLabel;
    pushHistory(record);
  }

  private HistoryRecord newRecord(ItemStack stack, int count, String stateKey, String detail) {
    HistoryRecord record = new HistoryRecord();
    record.id = nextId();
    record.stack = stack.copy();
    record.count = count;
    record.stateKey = stateKey;
    record.detail = detail;
    return record;
  }

  private void pushHistory(HistoryRecord record) {
    List<HistoryRecord> list = record.system ? systemHistory : userHistory;
    list.add(0, record);
    while (list.size() > HISTORY_LIMIT) {
      list.remove(list.size() - 1);
    }
    core.setChanged();
  }

  public void noteLeftoverShipped(BlockPos assemblyPos, ItemKey item, String destDetail) {
    long posLong = assemblyPos.asLong();
    for (List<HistoryRecord> list : List.of(userHistory, systemHistory)) {
      for (HistoryRecord record : list) {
        for (SubRecord sub : record.subs) {
          if (sub.leftoverPos == posLong && "assembly".equals(sub.where)
              && item.matches(sub.leftoverItem)) {
            sub.where = "sink";
            sub.whereDetail = destDetail;
            core.setChanged();
            return;
          }
        }
      }
    }
  }

  public void removeHistory(long id, @Nullable BlockPos restrictTable) {
    boolean removed = userHistory.removeIf(record -> record.id == id
        && (restrictTable == null || record.originPos == restrictTable.asLong()));
    if (restrictTable == null) {
      removed |= systemHistory.removeIf(record -> record.id == id);
    }
    if (removed) {
      core.setChanged();
    }
  }

  public void clearHistoryDone(@Nullable BlockPos restrictTable) {
    boolean removed = userHistory.removeIf(record -> "done".equals(record.stateKey)
        && (restrictTable == null || record.originPos == restrictTable.asLong()));
    if (restrictTable == null) {
      removed |= systemHistory.removeIf(record -> "done".equals(record.stateKey));
    }
    if (removed) {
      core.setChanged();
    }
  }

  public TaskLedger(BlockEntityLogisticsController core) {
    this.core = core;
  }

  public long nextId() {
    return nextId++;
  }

  public int reservedFor(ItemKey item) {
    return reserved.getOrDefault(item, 0);
  }

  public void reserve(Map<ItemKey, Integer> amounts) {
    amounts.forEach((item, count) -> reserved.merge(item, count, Integer::sum));
  }

  private void unreserve(ItemKey item, int count) {
    reserved.merge(item, -count, Integer::sum);
    if (reserved.getOrDefault(item, 0) <= 0) {
      reserved.remove(item);
    }
  }

  public boolean outputInUse(BlockPos assemblyPos) {
    return claimedResult(assemblyPos) != null;
  }

  @Nullable
  public ItemKey claimedResult(BlockPos assemblyPos) {
    for (RequestJob job : jobs) {
      if (job.error != null) {
        continue;
      }
      for (StepData step : job.steps) {
        if (step.claimed && step.output().pos().equals(assemblyPos)) {
          return ItemKey.of(step.result);
        }
      }
    }
    return null;
  }

  public boolean hasActiveJobFor(Endpoint dest) {
    for (RequestJob job : jobs) {
      if (job.error == null && job.dest.equals(dest)) {
        return true;
      }
    }
    return false;
  }

  public void addJob(RequestJob job) {
    jobs.add(job);
    core.setChanged();
  }

  public void prepare(RequestJob job) {
    for (StepData step : job.steps) {
      step.prepare();
    }
  }

  public DeliveryTask createDelivery(ItemStack stack, Endpoint from, Endpoint to, List<BlockPos> route, long jobId) {
    DeliveryTask task = new DeliveryTask();
    task.id = nextId();
    task.stack = stack;
    task.from = from;
    task.to = to;
    task.route = route;

    int ticksPerPipe = Math.max(1, ModConfig.server().logistics_ticks_per_pipe);
    int goldTicks = Math.max(1, ticksPerPipe / 2);
    int travel = 0;
    Level level = core.getLevel();
    int[] hopTicks = new int[Math.max(0, route.size() - 1)];
    for (int i = 1; i < route.size(); i++) {
      boolean gold = level != null && level.isLoaded(route.get(i))
          && level.getBlockState(route.get(i))
              .getBlock() instanceof com.faktocraft.common.block.impl.logistics.BlockGoldPipe;
      hopTicks[i - 1] = gold ? goldTicks : ticksPerPipe;
      travel += hopTicks[i - 1];
    }
    task.travelTicks = Math.max(2, Math.max(travel, ticksPerPipe));
    task.jobId = jobId;
    task.sliceRoute();
    deliveries.add(task);
    if (core.getLevel() instanceof ServerLevel serverLevel && route.size() >= 2) {
      PacketLogisticsGhost.send(serverLevel, route, stack, task.travelTicks, hopTicks);
    }
    core.setChanged();
    return task;
  }

  public void tick(Level level, LogisticsGraph graph) {
    tickDeliveries(level, graph);
    tickJobs(level, graph);
  }

  private boolean remainingRouteLoaded(Level level, DeliveryTask task) {
    if (task.route.isEmpty()) {
      return true;
    }
    int index = Math.min(task.route.size() - 1, task.progress * task.route.size() / task.travelTicks);
    for (int lastIndex : task.sectionLastIndex) {
      if (lastIndex >= index && !level.isLoaded(task.route.get(lastIndex))) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  private RequestJob jobOf(long jobId) {
    if (jobId == 0) {
      return null;
    }
    for (RequestJob job : jobs) {
      if (job.id == jobId) {
        return job;
      }
    }
    return null;
  }

  private void tickDeliveries(Level level, LogisticsGraph graph) {
    Iterator<DeliveryTask> iterator = deliveries.iterator();
    List<DeliveryTask> finished = new ArrayList<>();
    while (iterator.hasNext()) {
      DeliveryTask task = iterator.next();
      if (!task.delivering) {

        if (!remainingRouteLoaded(level, task)) {
          continue;
        }
        if (++task.progress < task.travelTicks) {
          continue;
        }
        task.delivering = true;
        task.retryCooldown = 0;
      }
      if (--task.retryCooldown > 0) {
        continue;
      }
      task.retryCooldown = RETRY_INTERVAL;
      if (!task.to.isLoaded(level)) {
        continue;
      }
      task.stack = task.to.insert(level, task.stack, false);
      if (task.stack.isEmpty()) {
        iterator.remove();
        finished.add(task);
        continue;
      }
      if (++task.retries < DELIVER_RETRIES) {
        continue;
      }
      bounce(level, task);
      iterator.remove();
      finished.add(task);
    }
    if (!finished.isEmpty()) {
      for (DeliveryTask task : finished) {
        RequestJob job = jobOf(task.jobId);
        if (job != null) {
          job.removePending(task.id);
        }
      }
      core.setChanged();
    }
  }

  private void bounce(Level level, DeliveryTask task) {
    RequestJob job = jobOf(task.jobId);
    if (job != null && !task.stockPart) {
      StepData step = job.pending(task.id);
      if (step != null) {
        ItemKey item = ItemKey.of(task.stack);
        for (int k = 0; k < step.ingredients.size() && k < step.need.length; k++) {
          if (step.ingredients.get(k).item().equals(item)) {
            step.need[k] += task.stack.getCount();
            break;
          }
        }
        step.delivered.merge(item, -task.stack.getCount(), Integer::sum);
      } else if (job.finalPending.contains(task.id)) {
        job.delivered -= task.stack.getCount();
      }
      park(job, ItemKey.of(task.stack), task.stack.getCount(), parkedStepFor(job, task.from));
      task.stack = ItemStack.EMPTY;
      return;
    }
    if (task.from.isLoaded(level)) {
      task.stack = task.from.insert(level, task.stack, false);
    }
    if (!task.stack.isEmpty()) {
      BlockPos drop = task.to.pos();
      Containers.dropItemStack(level, drop.getX() + 0.5, drop.getY() + 1, drop.getZ() + 0.5, task.stack);
      task.stack = ItemStack.EMPTY;
    }
  }

  private static int parkedStepFor(RequestJob job, Endpoint from) {
    for (int i = 0; i < job.steps.size(); i++) {
      if (job.steps.get(i).output().pos().equals(from.pos())) {
        return i;
      }
    }
    return 0;
  }

  private void tickJobs(Level level, LogisticsGraph graph) {
    Iterator<RequestJob> iterator = jobs.iterator();
    while (iterator.hasNext()) {
      RequestJob job = iterator.next();
      tickJob(level, graph, job);
      if (job.error != null) {
        String[] parts = job.error.split(":", 2);
        addHistory(job, "error." + parts[0], parts.length > 1 ? parts[1] : "");
        iterator.remove();
        core.setChanged();
        continue;
      }
      if (job.stepsDone() && job.delivered >= job.crafted() && job.finalPending.isEmpty()
          && job.stockToDeliver.isEmpty() && job.stockPending.isEmpty()) {
        releaseReservations(job);
        releaseParked(job);
        addHistory(job, "done", "");
        iterator.remove();
        core.setChanged();
      }
    }
  }

  public boolean cancelUserJob(Level level, long jobId, @Nullable BlockPos restrictOrigin) {
    RequestJob job = jobOf(jobId);
    if (job == null || job.system || job.error != null) {
      return false;
    }
    if (restrictOrigin != null && job.originPos != restrictOrigin.asLong()) {
      return false;
    }
    abort(level, core.graph(), job, "cancelled");
    return true;
  }

  private void abort(Level level, @Nullable LogisticsGraph graph, RequestJob job, String error) {
    returnInFlight(level, job);
    if (graph != null) {
      for (StepData step : job.steps) {
        if (step.claimed) {
          drainOutput(level, job, step, ItemKey.of(step.result));
          recoverInputs(level, graph, job, step);
        }
      }
    }
    failJob(job, error);
  }

  private void returnInFlight(Level level, RequestJob job) {
    Iterator<DeliveryTask> iterator = deliveries.iterator();
    while (iterator.hasNext()) {
      DeliveryTask task = iterator.next();
      if (task.jobId != job.id) {
        continue;
      }
      if (task.stockPart && task.from.isLoaded(level)) {
        task.stack = task.from.insert(level, task.stack, false);
      }
      if (!task.stack.isEmpty()) {
        if (job.steps.isEmpty()) {
          BlockPos drop = task.from.pos();
          Containers.dropItemStack(level, drop.getX() + 0.5, drop.getY() + 1, drop.getZ() + 0.5, task.stack);
        } else {
          park(job, ItemKey.of(task.stack), task.stack.getCount(), parkedStepFor(job, task.from));
        }
        task.stack = ItemStack.EMPTY;
      }
      iterator.remove();
    }
  }

  private void recoverInputs(Level level, LogisticsGraph graph, RequestJob job, StepData step) {
    if (step.delivered.isEmpty()) {
      return;
    }
    List<BlockEntityChassis.SinkCandidate> sinks = BlockEntityChassis.sinkCandidates(level, graph, null);
    if (sinks.isEmpty()) {
      return;
    }
    Map<ItemKey, Integer> budget = new HashMap<>(step.delivered);
    for (int k = 0; k < step.ingredients.size(); k++) {
      ItemKey item = step.ingredients.get(k).item();
      int allowed = budget.getOrDefault(item, 0);
      if (allowed <= 0) {
        continue;
      }
      int shipped = shipToSinks(level, graph, sinks, step.ingredientEnds().get(k), step.node(), item, allowed);
      budget.merge(item, -shipped, Integer::sum);
    }
    step.delivered.clear();
  }

  private int shipToSinks(Level level, LogisticsGraph graph, List<BlockEntityChassis.SinkCandidate> sinks,
      Endpoint source, BlockPos node, ItemKey item, int amount) {
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    int shipped = 0;
    while (shipped < amount) {
      int take = Math.min(amount - shipped, item.maxStackSize());
      if (perItem > 0) {
        take = Math.min(take, core.getEnergyStorage().energyStored() / perItem);
      }
      if (take <= 0) {
        break;
      }
      BlockEntityChassis.SinkTarget sink = BlockEntityChassis.findSink(level, sinks,
          item.stack(take));
      if (sink == null) {
        break;
      }
      int taken = source.extract(level, item, take, false);
      if (taken <= 0) {
        break;
      }
      core.consumeEnergy(taken * perItem);
      DeliveryTask delivery = createDelivery(item.stack(taken), source,
          sink.endpoint(), graph.route(node, sink.nodePos()), 0);
      delivery.leftover = true;
      delivery.originPos = node.asLong();
      shipped += taken;
    }
    return shipped;
  }

  private void releaseReservations(RequestJob job) {
    job.withdrawalsRemaining.forEach(this::unreserve);
    job.withdrawalsRemaining.clear();
    job.stations.clear();
    for (StepData step : job.steps) {
      step.claimed = false;
    }
  }

  public long stationHolder(BlockPos station) {
    for (RequestJob job : jobs) {
      if (job.stations.contains(station)) {
        return job.id;
      }
    }
    return 0;
  }

  private void failJob(RequestJob job, String error) {
    job.error = error;
    releaseReservations(job);
    releaseParked(job);
    core.setChanged();
  }

  private static String posText(BlockPos pos) {
    return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
  }

  private static boolean stationLost(Level level, LogisticsPlanner.StationChoice station) {
    if (level.isLoaded(station.node())
        && !LogisticsGraph.isNetworkMember(level.getBlockState(station.node()))) {
      return true;
    }
    return station.output().missing(level);
  }

  private static int stepTimeout(StepData step) {
    return step.timeout > 0 ? step.timeout : Math.max(100, ModConfig.server().logistics_machine_timeout);
  }

  private void tickJob(Level level, LogisticsGraph graph, RequestJob job) {
    if (!job.stockToDeliver.isEmpty()) {
      supply(level, graph, job, job.stockToDeliver, job.dest, job.destNode, job.stockPending, true);
    }
    for (StepData step : job.steps) {
      if (!step.done) {
        tickStep(level, graph, job, step);
        if (job.error != null) {
          return;
        }
      }
    }
    dispatch(level, graph, job);
    tickStallWatchdog(level, graph, job);
  }

  private void tickStallWatchdog(Level level, LogisticsGraph graph, RequestJob job) {
    for (StepData step : job.steps) {
      if (!step.done && !step.claimed && step.queued) {
        job.stallWait = STALL_TIMEOUT_TICKS;
        return;
      }
    }
    long mark = job.delivered;
    mark = mark * 31 + job.finalPending.size();
    mark = mark * 31 + job.stockPending.size();
    mark = mark * 31 + job.stockToDeliver.size();
    for (int count : job.stockToDeliver.values()) {
      mark += count;
    }
    for (StepData step : job.steps) {
      mark = mark * 31 + (step.done ? 1 : 0);
      mark = mark * 31 + (step.claimed ? stepMark(level, step) : step.drained);
    }
    if (mark != job.stallMark || job.stallWait <= 0) {
      job.stallMark = mark;
      job.stallWait = STALL_TIMEOUT_TICKS;
      return;
    }
    if (--job.stallWait <= 0) {
      abort(level, graph, job, "timeout:" + posText(job.dest.pos()));
    }
  }

  private void tickStep(Level level, LogisticsGraph graph, RequestJob job, StepData step) {
    if (step.claimed && stationLost(level, step.station())) {
      abort(level, graph, job, "lost:" + posText(step.output().pos()));
      return;
    }
    if (!step.claimed) {
      if (!canStart(job, step)) {
        step.queued = false;
        return;
      }
      if (!claim(level, job, step)) {
        return;
      }
    }
    if (!step.output().isLoaded(level)) {
      return;
    }
    ItemKey resultKey = ItemKey.of(step.result);
    maintain(level, graph, job, step);
    startBatches(job, step);
    supplyStep(level, graph, job, step);
    drainOutput(level, job, step, resultKey);
    if (!step.rolling() && step.batchUnits > 0 && step.needsEmpty() && step.pending.isEmpty()
        && step.batchProduced >= step.batchUnits * step.resultCount) {
      step.batchUnits = 0;
      step.batchProduced = 0;
      core.setChanged();
    }
    if (step.drained >= step.target()) {
      finishStep(level, graph, job, step);
      return;
    }
    long mark = stepMark(level, step);
    if (mark != step.mark) {
      step.mark = mark;
      step.wait = stepTimeout(step);
    }
    if (!step.inFlight()) {
      if (!canStart(job, step)) {
        release(job, step);
      }
      return;
    }
    if (--step.wait <= 0) {
      abort(level, graph, job, "timeout:" + posText(step.output().pos()));
    }
  }

  private static long stepMark(Level level, StepData step) {
    long mark = step.drained;
    mark = mark * 31 + step.unitsStarted;
    mark = mark * 31 + step.pending.size();
    for (int value : step.need) {
      mark = mark * 31 + value;
    }
    for (int k = 0; k < step.ingredients.size(); k++) {
      Endpoint end = step.ingredientEnds().get(k);
      mark = mark * 31 + (end.isLoaded(level) ? end.count(level, step.ingredients.get(k).item()) : 0);
    }
    return mark;
  }

  private boolean claim(Level level, RequestJob job, StepData step) {
    List<BlockPos> busy = new ArrayList<>(job.stations);
    for (RequestJob other : jobs) {
      if (other != job) {
        busy.addAll(other.stations);
      }
    }
    boolean anyBusy = false;
    for (int option = 0; option < step.stations.size(); option++) {
      LogisticsPlanner.StationChoice choice = step.stations.get(option);
      if ((job.split && !choice.shared()) || stationLost(level, choice)) {
        continue;
      }
      if (busy.contains(choice.output().pos())) {
        anyBusy = true;
        continue;
      }
      if (!choice.output().isLoaded(level)) {
        anyBusy = true;
        continue;
      }
      step.chosen = option;
      step.claimed = true;
      step.queued = false;
      step.outputBaseline = choice.output().count(level, ItemKey.of(step.result));
      step.wait = stepTimeout(step);
      step.mark = 0;
      job.stations.add(choice.output().pos());
      core.setChanged();
      return true;
    }
    step.queued = anyBusy;
    if (!anyBusy) {
      failJob(job, "lost:" + posText(step.output().pos()));
    }
    return false;
  }

  private void release(RequestJob job, StepData step) {
    if (!step.claimed) {
      return;
    }
    step.claimed = false;
    step.outputBaseline = Integer.MIN_VALUE;
    job.stations.remove(step.output().pos());
    core.setChanged();
  }

  private void finishStep(Level level, LogisticsGraph graph, RequestJob job, StepData step) {
    recoverInputs(level, graph, job, step);
    step.done = true;
    release(job, step);
    core.setChanged();
  }

  private static int[] planTake(StepData step, int units) {
    int entries = step.ingredients.size();
    int[] take = new int[entries];
    Map<Integer, Integer> originLeft = new HashMap<>();
    for (int k = 0; k < entries && k < step.remaining.length; k++) {
      int origin = step.origin(k);
      Integer left = originLeft.get(origin);
      if (left == null) {
        left = step.mode(k) == IoMode.PER_BATCH ? step.unitCount(k) : step.unitCount(k) * units;
      }
      int amount = Math.max(0, Math.min(left, step.remaining[k]));
      take[k] = amount;
      originLeft.put(origin, left - amount);
    }
    return take;
  }

  private static int nextUnits(StepData step) {
    return step.rolling() ? 1 : Math.min(Math.max(1, step.batchSize), step.times - step.unitsStarted);
  }

  private static Map<ItemKey, Integer> availability(RequestJob job) {
    Map<ItemKey, Integer> available = new HashMap<>(job.withdrawalsRemaining);
    for (Parked parked : job.parked) {
      available.merge(parked.item, parked.count, Integer::sum);
    }
    for (StepData step : job.steps) {
      for (int k = 0; k < step.ingredients.size() && k < step.need.length; k++) {
        if (step.need[k] > 0) {
          available.merge(step.ingredients.get(k).item(), -step.need[k], Integer::sum);
        }
      }
    }
    return available;
  }

  private static boolean canStart(RequestJob job, StepData step) {
    return canStart(job, step, availability(job));
  }

  private static boolean canStart(RequestJob job, StepData step, Map<ItemKey, Integer> available) {
    if (step.done || step.unitsStarted >= step.times || (!step.rolling() && step.batchUnits > 0)) {
      return false;
    }
    int[] take = planTake(step, nextUnits(step));
    Map<ItemKey, Integer> left = new HashMap<>(available);
    for (int k = 0; k < take.length; k++) {
      if (take[k] <= 0) {
        continue;
      }
      ItemKey item = step.ingredients.get(k).item();
      if (left.getOrDefault(item, 0) < take[k]) {
        return false;
      }
      left.merge(item, -take[k], Integer::sum);
    }
    return true;
  }

  private void startBatches(RequestJob job, StepData step) {
    Map<ItemKey, Integer> available = availability(job);
    int started = 0;
    while (started < MAX_UNITS_PER_TICK && canStart(job, step, available)) {
      int units = nextUnits(step);
      int[] take = planTake(step, units);
      for (int k = 0; k < take.length; k++) {
        if (take[k] <= 0) {
          continue;
        }
        step.need[k] += take[k];
        step.remaining[k] -= take[k];
        available.merge(step.ingredients.get(k).item(), -take[k], Integer::sum);
      }
      step.unitsStarted += units;
      started += units;
      core.setChanged();
      if (!step.rolling()) {
        step.batchUnits = units;
        step.batchProduced = 0;
        return;
      }
    }
  }

  private int inFlightTo(RequestJob job, Endpoint dest, ItemKey item) {
    int total = 0;
    for (DeliveryTask task : deliveries) {
      if (task.jobId == job.id && !task.stockPart && task.to.equals(dest) && item.matches(task.stack)) {
        total += task.stack.getCount();
      }
    }
    return total;
  }

  private void supplyStep(Level level, LogisticsGraph graph, RequestJob job, StepData step) {
    for (int k = 0; k < step.ingredients.size() && k < step.need.length; k++) {
      int need = step.need[k];
      if (need <= 0) {
        continue;
      }
      ItemKey item = step.ingredients.get(k).item();
      Endpoint dest = step.ingredientEnds().get(k);
      if (!dest.isLoaded(level)) {
        continue;
      }
      int room = need - dest.insert(level, item.stack(need), true).getCount() - inFlightTo(job, dest, item);
      if (room <= 0) {
        continue;
      }
      int pulled = room - pullAmount(level, graph, job, item, room, dest, step.node(), step.pending, false);
      if (pulled > 0) {
        step.need[k] -= pulled;
        step.delivered.merge(item, pulled, Integer::sum);
        core.setChanged();
      }
    }
  }

  private void drainOutput(Level level, RequestJob job, StepData step, ItemKey resultKey) {
    if (step.outputBaseline == Integer.MIN_VALUE || !step.output().isLoaded(level)) {
      return;
    }
    int produced = step.output().count(level, resultKey) - step.outputBaseline;
    if (produced <= 0) {
      return;
    }
    int taken = step.output().extract(level, resultKey, produced, false);
    if (taken <= 0) {
      return;
    }
    step.drained += taken;
    step.batchProduced += taken;
    park(job, resultKey, taken, job.steps.indexOf(step));
    core.setChanged();
  }

  private static void park(RequestJob job, ItemKey item, int count, int stepIndex) {
    if (count <= 0 || item.isEmpty()) {
      return;
    }
    for (Parked parked : job.parked) {
      if (parked.step == stepIndex && parked.item.equals(item)) {
        parked.count += count;
        return;
      }
    }
    Parked parked = new Parked();
    parked.item = item;
    parked.count = count;
    parked.step = Math.max(0, stepIndex);
    job.parked.add(parked);
  }

  private void dispatch(Level level, LogisticsGraph graph, RequestJob job) {
    int left = job.crafted() - job.delivered;
    if (left <= 0 || job.steps.isEmpty()) {
      return;
    }
    if (job.dispatchWait > 0) {
      job.dispatchWait--;
      return;
    }
    ItemKey targetKey = ItemKey.of(job.target);
    int parkedCount = 0;
    for (Parked parked : job.parked) {
      if (parked.item.equals(targetKey)) {
        parkedCount += parked.count;
      }
    }
    if (parkedCount <= 0) {
      return;
    }
    int amount = Math.min(left, parkedCount);
    int rest = pullFromParked(level, graph, job, targetKey, amount, job.dest, job.destNode, job.finalPending);
    job.delivered += amount - rest;
    job.dispatchWait = DISPATCH_INTERVAL;
    core.setChanged();
  }

  private static ListTag saveMaintains(List<LogisticsPlanner.Maintain> maintains) {
    ListTag list = new ListTag();
    for (LogisticsPlanner.Maintain maintain : maintains) {
      CompoundTag entry = new CompoundTag();
      entry.put("key", maintain.item().save());
      entry.putInt("count", maintain.count());
      list.add(entry);
    }
    return list;
  }

  private static List<LogisticsPlanner.Maintain> loadMaintains(ListTag list) {
    List<LogisticsPlanner.Maintain> maintains = new ArrayList<>();
    for (Tag element : list) {
      CompoundTag entry = (CompoundTag) element;
      ItemKey key = ItemKey.load(entry.getCompound("key"));
      if (!key.isEmpty()) {
        maintains.add(new LogisticsPlanner.Maintain(key, Math.max(1, entry.getInt("count"))));
      }
    }
    return maintains;
  }

  private void maintain(Level level, LogisticsGraph graph, RequestJob job, StepData step) {
    List<Endpoint> ends = step.maintainEnds();
    List<LogisticsPlanner.Maintain> maintains = step.maintains();
    for (int i = 0; i < maintains.size() && i < ends.size(); i++) {
      LogisticsPlanner.Maintain maintain = maintains.get(i);
      Endpoint end = ends.get(i);
      if (!end.isLoaded(level)) {
        continue;
      }
      int missing = maintain.count() - end.count(level, maintain.item());
      if (missing <= 0 || inFlightTo(job, end, maintain.item()) > 0) {
        continue;
      }
      int room = missing - end.insert(level, maintain.item().stack(missing), true).getCount();
      if (room > 0) {
        pullUnreserved(level, graph, job, step, maintain.item(), room, end, step.node());
      }
    }
  }

  private void pullUnreserved(Level level, LogisticsGraph graph, RequestJob job, StepData step, ItemKey item,
      int amount, Endpoint dest, BlockPos destNode) {
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    List<BlockEntityChassis.ProviderRef> providers = BlockEntityChassis.providers(level, graph);
    providers.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    for (BlockEntityChassis.ProviderRef provider : providers) {
      if (amount <= 0) {
        break;
      }
      int available = Math.min(amount, provider.availableOf(level, item) - reservedFor(item));
      if (perItem > 0) {
        available = Math.min(available, core.getEnergyStorage().energyStored() / perItem);
      }
      if (available <= 0) {
        continue;
      }
      Endpoint source = Endpoint.inventory(provider.inventoryPos(), provider.inventorySide());
      int taken = source.extract(level, item, available, false);
      if (taken <= 0) {
        continue;
      }
      core.consumeEnergy(taken * perItem);
      DeliveryTask task = createDelivery(item.stack(taken), source, dest,
          graph.route(provider.chassisPos(), destNode), job.id);
      step.pending.add(task.id);
      amount -= taken;
    }
  }

  private void releaseParked(RequestJob job) {
    Level level = core.getLevel();
    if (job.parked.isEmpty() || level == null) {
      return;
    }
    LogisticsGraph graph = core.graph();
    List<BlockEntityChassis.SinkCandidate> sinks = graph != null
        ? BlockEntityChassis.sinkCandidates(level, graph, null)
        : List.of();
    int perItem = Math.max(0, ModConfig.server().logistics_energy_per_item);
    for (Parked parked : job.parked) {
      StepData step = parked.step >= 0 && parked.step < job.steps.size() ? job.steps.get(parked.step) : null;
      int remaining = parked.count;
      while (remaining > 0 && step != null && graph != null) {
        int take = Math.min(remaining, parked.item.maxStackSize());
        if (perItem > 0) {
          take = Math.min(take, core.getEnergyStorage().energyStored() / perItem);
        }
        BlockEntityChassis.SinkTarget sink = take > 0
            ? BlockEntityChassis.findSink(level, sinks, parked.item.stack(take))
            : null;
        if (sink == null) {
          break;
        }
        core.consumeEnergy(take * perItem);
        DeliveryTask delivery = createDelivery(parked.item.stack(take), step.output(), sink.endpoint(),
            graph.route(step.node(), sink.nodePos()), 0);
        delivery.leftover = true;
        delivery.originPos = step.node().asLong();
        remaining -= take;
      }
      if (remaining > 0) {
        BlockPos drop = step != null ? step.output().pos() : job.dest.pos();
        Containers.dropItemStack(level, drop.getX() + 0.5, drop.getY() + 1, drop.getZ() + 0.5,
            parked.item.stack(remaining));
      }
    }
    job.parked.clear();
  }

  private int pullFromParked(Level level, LogisticsGraph graph, RequestJob job, ItemKey item, int amount,
      Endpoint dest, BlockPos destNode, Set<Long> pending) {
    Iterator<Parked> iterator = job.parked.iterator();
    while (iterator.hasNext() && amount > 0) {
      Parked parked = iterator.next();
      if (parked.count <= 0 || !parked.item.equals(item) || parked.step >= job.steps.size()) {
        continue;
      }
      StepData source = job.steps.get(parked.step);
      int take = Math.min(amount, parked.count);
      DeliveryTask task = createDelivery(item.stack(take), source.output(), dest,
          graph.route(source.node(), destNode), job.id);
      pending.add(task.id);
      parked.count -= take;
      amount -= take;
      if (parked.count <= 0) {
        iterator.remove();
      }
    }
    return amount;
  }

  private void supply(Level level, LogisticsGraph graph, RequestJob job, Map<ItemKey, Integer> needs,
      Endpoint dest, BlockPos destNode, Set<Long> pending, boolean stockPart) {
    List<ItemKey> items = new ArrayList<>(needs.keySet());
    for (ItemKey item : items) {
      int remaining = needs.getOrDefault(item, 0);
      if (remaining <= 0) {
        needs.remove(item);
        continue;
      }
      remaining = pullAmount(level, graph, job, item, remaining, dest, destNode, pending, stockPart);
      if (remaining <= 0) {
        needs.remove(item);
      } else {
        needs.put(item, remaining);
      }
    }
  }

  private int pullAmount(Level level, LogisticsGraph graph, RequestJob job, ItemKey item, int remaining,
      Endpoint dest, BlockPos destNode, Set<Long> pending, boolean stockPart) {
    if (!stockPart) {
      remaining = pullFromParked(level, graph, job, item, remaining, dest, destNode, pending);
    }
    if (remaining > 0) {
      remaining = pullFromProviders(level, graph, job, item, remaining, dest, destNode, pending, stockPart);
    }
    return remaining;
  }

  private int pullFromProviders(Level level, LogisticsGraph graph, RequestJob job, ItemKey item, int amount,
      Endpoint dest, BlockPos destNode, Set<Long> pending, boolean stockPart) {
    int allowed = Math.min(amount, job.withdrawalsRemaining.getOrDefault(item, 0));
    if (allowed <= 0) {
      return amount;
    }
    int pulled = 0;
    List<BlockEntityChassis.ProviderRef> providers = BlockEntityChassis.providers(level, graph);

    providers.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    for (BlockEntityChassis.ProviderRef provider : providers) {
      if (pulled >= allowed) {
        break;
      }
      int available = provider.availableOf(level, item);
      if (available <= 0) {
        continue;
      }
      int take = Math.min(available, allowed - pulled);
      Endpoint source = Endpoint.inventory(provider.inventoryPos(), provider.inventorySide());
      int taken = source.extract(level, item, take, false);
      if (taken <= 0) {
        continue;
      }
      List<BlockPos> route = graph.route(provider.chassisPos(), destNode);
      DeliveryTask task = createDelivery(item.stack(taken), source, dest, route, job.id);
      task.stockPart = stockPart;
      pending.add(task.id);
      pulled += taken;
    }
    if (pulled > 0) {
      job.withdrawalsRemaining.merge(item, -pulled, Integer::sum);
      if (job.withdrawalsRemaining.getOrDefault(item, 0) <= 0) {
        job.withdrawalsRemaining.remove(item);
      }
      unreserve(item, pulled);
    }
    return amount - pulled;
  }

  public List<TaskSummary> summaries() {
    List<TaskSummary> result = new ArrayList<>();
    for (RequestJob job : jobs) {
      String state;
      String detail = "";
      if (job.steps.isEmpty() || job.stepsDone()) {
        state = "delivering";
      } else {
        boolean running = false;
        for (StepData step : job.steps) {
          running |= step.claimed;
        }
        state = running ? "producing" : "collecting";
        detail = Math.min(job.steps.size(), job.doneSteps() + 1) + "/" + job.steps.size();
      }
      result.add(new TaskSummary(job.id, job.target, job.quantity, state, detail,
          job.system, job.originPos, job.originLabel, deliveredSoFar(job), subRecordsFor(job)));
    }
    for (DeliveryTask task : deliveries) {
      if (task.jobId == 0 && !task.leftover) {
        result.add(new TaskSummary(0, task.stack, task.stack.getCount(),
            task.delivering ? "delivering" : "traveling", "", true, task.originPos, "", 0, List.of()));
      }
    }
    return result;
  }

  public int activeJobCount() {
    int count = 0;
    for (RequestJob job : jobs) {
      if (job.error == null) {
        count++;
      }
    }
    return count;
  }

  public void dropCustody(Level level, BlockPos pos) {
    for (DeliveryTask task : deliveries) {
      if (!task.stack.isEmpty()) {
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, task.stack);
      }
    }
    for (RequestJob job : jobs) {
      for (Parked parked : job.parked) {
        if (parked.count > 0) {
          Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
              parked.item.stack(parked.count));
        }
      }
    }
    deliveries.clear();
    jobs.clear();
    reserved.clear();
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putLong("nextId", nextId);
    ListTag deliveriesTag = new ListTag();
    for (DeliveryTask task : deliveries) {
      deliveriesTag.add(task.save());
    }
    tag.put("deliveries", deliveriesTag);
    ListTag jobsTag = new ListTag();
    for (RequestJob job : jobs) {
      jobsTag.add(job.save());
    }
    tag.put("jobs", jobsTag);
    tag.put("reserved", saveKeyMap(reserved));
    ListTag userTag = new ListTag();
    for (HistoryRecord record : userHistory) {
      userTag.add(record.save());
    }
    tag.put("historyUser", userTag);
    ListTag systemTag = new ListTag();
    for (HistoryRecord record : systemHistory) {
      systemTag.add(record.save());
    }
    tag.put("historySystem", systemTag);
    return tag;
  }

  public void load(CompoundTag tag) {
    deliveries.clear();
    jobs.clear();
    reserved.clear();
    nextId = Math.max(1, tag.getLong("nextId"));
    for (Tag element : tag.getList("deliveries", Tag.TAG_COMPOUND)) {
      DeliveryTask task = DeliveryTask.load((CompoundTag) element);
      if (!task.stack.isEmpty()) {
        deliveries.add(task);
      }
    }
    for (Tag element : tag.getList("jobs", Tag.TAG_COMPOUND)) {
      jobs.add(RequestJob.load((CompoundTag) element));
    }
    reserved.putAll(loadKeyMap(tag, "reserved"));
    userHistory.clear();
    systemHistory.clear();

    for (String key : new String[] { "historyUser", "historySystem", "history" }) {
      for (Tag element : tag.getList(key, Tag.TAG_COMPOUND)) {
        HistoryRecord record = HistoryRecord.load((CompoundTag) element);
        List<HistoryRecord> list = record.system ? systemHistory : userHistory;
        if (!record.stack.isEmpty() && list.size() < HISTORY_LIMIT) {
          if (record.id == 0) {
            record.id = nextId();
          }
          list.add(record);
        }
      }
    }

    for (RequestJob job : jobs) {
      for (DeliveryTask task : deliveries) {
        if (task.jobId != job.id) {
          continue;
        }
        if (task.stockPart) {
          job.stockPending.add(task.id);
        } else if (task.to.equals(job.dest)) {
          job.finalPending.add(task.id);
        } else {
          StepData owner = null;
          for (StepData step : job.steps) {
            if (!step.done && step.claimed && step.ingredientEnds().contains(task.to)) {
              owner = step;
              break;
            }
          }
          if (owner != null) {
            owner.pending.add(task.id);
          } else {
            job.finalPending.add(task.id);
          }
        }
      }
    }
  }
}
