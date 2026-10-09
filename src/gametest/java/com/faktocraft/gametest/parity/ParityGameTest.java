package com.faktocraft.gametest.parity;

import com.faktocraft.Faktocraft;
import com.faktocraft.gametest.GameTest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class ParityGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static final Path REPORTS = Path.of("parity");
  private static final int SHOWN = 25;

  private static JsonObject snapshot;
  private static JsonObject acceptedFile;

  private static Path fixtureDir() {
    String property = System.getProperty("faktocraft.parityFixture", "").trim();
    return property.isEmpty() ? Path.of("..", "docs", "fixtures", "parity-1201") : Path.of(property);
  }

  private static synchronized JsonObject snapshot() {
    if (snapshot == null) {
      snapshot = read(fixtureDir().resolve("snapshot.json"));
    }
    return snapshot;
  }

  private static synchronized JsonObject acceptedFile() {
    if (acceptedFile == null) {
      Path path = fixtureDir().resolve("accepted.json");
      acceptedFile = Files.exists(path) ? read(path) : new JsonObject();
    }
    return acceptedFile;
  }

  private static JsonObject read(Path path) {
    try {
      return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (IOException e) {
      throw new UncheckedIOException("parity fixture missing at " + path.toAbsolutePath()
          + " - build it on the 1.20.1 branch (see docs/fixtures/parity-1201/README.md)", e);
    }
  }

  private static void write(Path path, String text) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, String> aliases(String key) {
    Map<String, String> out = new HashMap<>();
    JsonObject object = acceptedFile().getAsJsonObject(key);
    if (object != null) {
      object.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
    }
    return out;
  }

  private static List<ParityDiff.Accepted> accepted() {
    List<ParityDiff.Accepted> out = new ArrayList<>();
    JsonArray array = acceptedFile().getAsJsonArray("accepted");
    if (array != null) {
      for (JsonElement element : array) {
        JsonObject rule = element.getAsJsonObject();
        out.add(new ParityDiff.Accepted(Pattern.compile(rule.get("match").getAsString()),
            rule.get("reason").getAsString()));
      }
    }
    return out;
  }

  private static JsonObject modOnly(JsonObject universe) {
    JsonObject out = new JsonObject();
    List<String> items = new ArrayList<>();
    universe.getAsJsonArray("items").forEach(e -> {
      if (e.getAsString().startsWith(Faktocraft.MODID + ":")) {
        items.add(e.getAsString());
      }
    });
    List<String> blocks = new ArrayList<>();
    universe.getAsJsonArray("mod_blocks").forEach(e -> blocks.add(e.getAsString()));
    out.add("mod_items", ParitySnapshot.set(items));
    out.add("mod_blocks", ParitySnapshot.set(blocks));
    return out;
  }

  private static Set<String> setOf(JsonObject holder) {
    Set<String> out = new HashSet<>();
    if (holder != null && holder.has("set")) {
      holder.getAsJsonArray("set").forEach(e -> out.add(e.getAsString()));
    }
    return out;
  }

  private static void restrictSides(ParityDiff diff, JsonObject old, JsonObject now) {
    for (String block : now.keySet()) {
      JsonObject n = now.getAsJsonObject(block);
      JsonObject o = old.has(block) ? old.getAsJsonObject(block) : null;
      if (o == null || !o.has("items") || !n.has("items")) {
        continue;
      }
      JsonObject oi = o.getAsJsonObject("items");
      JsonObject ni = n.getAsJsonObject("items");
      Set<String> shared = new HashSet<>();
      for (String probe : setOf(oi.getAsJsonObject("probes"))) {
        shared.add(diff.canon(probe));
      }
      shared.retainAll(setOf(ni.getAsJsonObject("probes")));
      restrictAccepts(diff, oi, shared, true);
      restrictAccepts(diff, ni, shared, false);
      oi.remove("probes");
      ni.remove("probes");
    }
  }

  private static void restrictAccepts(ParityDiff diff, JsonObject items, Set<String> shared, boolean old) {
    for (Map.Entry<String, JsonElement> side : items.entrySet()) {
      if (!side.getValue().isJsonObject() || !side.getValue().getAsJsonObject().has("accepts")) {
        continue;
      }
      JsonArray accepts = side.getValue().getAsJsonObject().getAsJsonArray("accepts");
      for (int i = 0; i < accepts.size(); i++) {
        List<String> kept = new ArrayList<>();
        for (String member : setOf(accepts.get(i).getAsJsonObject())) {
          String key = old ? diff.canon(member) : member;
          if (shared.contains(key)) {
            kept.add(key);
          }
        }
        accepts.set(i, ParitySnapshot.set(kept));
      }
    }
  }

  static String normalizeTooltip(String line) {
    return line.replaceAll("<neoforge\\.value\\.flat\\(([^()]*)\\)>", "$1")
        .replaceAll("<neoforge\\.modifier\\.(plus|take)\\(", "<attribute.modifier.$1.0(")
        .replaceAll("<attribute\\.name\\.(generic|player)\\.", "<attribute.name.");
  }

  private static void normalizeTooltips(JsonObject tooltips) {
    for (Map.Entry<String, JsonElement> entry : tooltips.entrySet()) {
      JsonArray lines = entry.getValue().getAsJsonArray();
      for (int i = 0; i < lines.size(); i++) {
        lines.set(i, new JsonPrimitive(normalizeTooltip(lines.get(i).getAsString())));
      }
    }
  }

  private static void restrictTabs(ParityDiff diff, JsonObject old, JsonObject newUniverse) {
    Set<String> items = new HashSet<>();
    newUniverse.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
    for (Map.Entry<String, JsonElement> tab : old.entrySet()) {
      JsonObject entry = tab.getValue().getAsJsonObject();
      if (!entry.has("order")) {
        continue;
      }
      List<String> kept = new ArrayList<>();
      for (String token : entry.get("order").getAsString().split(" ")) {
        int cut = token.indexOf('+');
        String id = diff.canon(cut < 0 ? token : token.substring(0, cut));
        if (items.contains(id)) {
          kept.add(cut < 0 ? id : id + token.substring(cut));
        }
      }
      entry.addProperty("order", String.join(" ", kept));
    }
  }

  private static void run(GameTestHelper helper, String category) {
    ParitySnapshot snap = new ParitySnapshot(helper, MACHINE);
    long started = System.nanoTime();
    JsonObject newUniverse = snap.universe();
    JsonObject now = category.equals("universe") ? newUniverse : snap.category(category);
    write(REPORTS.resolve(category + "-263.json"), GSON.toJson(now));
    Faktocraft.LOGGER.info("[Parity] {} snapshot took {} ms", category, (System.nanoTime() - started) / 1_000_000);
    JsonObject fixture = snapshot();
    JsonObject oldUniverse = fixture.getAsJsonObject("universe");
    JsonObject old = fixture.getAsJsonObject(category).deepCopy();
    now = now.deepCopy();
    ParityDiff diff = new ParityDiff(aliases("aliases"), aliases("valueAliases"), accepted(), oldUniverse,
        newUniverse);
    switch (category) {
      case "universe" -> {
        JsonObject o = modOnly(old);
        JsonObject n = modOnly(now);
        for (String key : n.keySet()) {
          diff.compareRaw(category + "/" + key, setOf(o.getAsJsonObject(key)), setOf(n.getAsJsonObject(key)));
        }
      }
      case "tags" -> diff.compare(category, ParityDiff.migrateTags(old), now);
      case "tooltips" -> {
        normalizeTooltips(old);
        normalizeTooltips(now);
        diff.compare(category, old, now);
      }
      case "tabs" -> {
        restrictTabs(diff, old, newUniverse);
        diff.compare(category, old, now);
      }
      case "sides" -> {
        restrictSides(diff, old, now);
        diff.compare(category, old, now);
      }
      default -> diff.compare(category, old, now);
    }
    StringBuilder report = new StringBuilder();
    report.append("parity ").append(category).append(": ").append(diff.differences().size())
        .append(" difference(s), ").append(diff.acceptedDifferences().size()).append(" accepted\n\n");
    diff.differences().forEach(line -> report.append(line).append('\n'));
    if (!diff.acceptedDifferences().isEmpty()) {
      report.append("\naccepted:\n");
      diff.acceptedDifferences().forEach(line -> report.append(line).append('\n'));
    }
    Path reportPath = REPORTS.resolve(category + ".txt");
    write(reportPath, report.toString());
    Faktocraft.LOGGER.info("[Parity] {}: {} difference(s), {} accepted, report at {}", category,
        diff.differences().size(), diff.acceptedDifferences().size(), reportPath.toAbsolutePath());
    if (diff.differences().isEmpty()) {
      helper.succeed();
      return;
    }
    List<String> shown = diff.differences().subList(0, Math.min(SHOWN, diff.differences().size()));
    helper.fail(diff.differences().size() + " difference(s) from 1.20.1 in " + category + " (report: "
        + reportPath.toAbsolutePath() + ")\n" + String.join("\n", shown));
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityUniverse(GameTestHelper helper) {
    run(helper, "universe");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityItems(GameTestHelper helper) {
    run(helper, "items");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityBlocks(GameTestHelper helper) {
    run(helper, "blocks");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityTags(GameTestHelper helper) {
    run(helper, "tags");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityFuel(GameTestHelper helper) {
    run(helper, "fuel");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityDrops(GameTestHelper helper) {
    run(helper, "drops");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityCrafting(GameTestHelper helper) {
    run(helper, "crafting");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityMachineRecipes(GameTestHelper helper) {
    run(helper, "machine_recipes");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityMenus(GameTestHelper helper) {
    run(helper, "menus");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void paritySides(GameTestHelper helper) {
    run(helper, "sides");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityStates(GameTestHelper helper) {
    run(helper, "states");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityTabs(GameTestHelper helper) {
    run(helper, "tabs");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityTooltips(GameTestHelper helper) {
    run(helper, "tooltips");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityFluids(GameTestHelper helper) {
    run(helper, "fluids");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityRoundtrip(GameTestHelper helper) {
    run(helper, "roundtrip");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityProcess(GameTestHelper helper) {
    run(helper, "process");
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 200)
  public static void parityInteract(GameTestHelper helper) {
    run(helper, "interact");
  }
}
