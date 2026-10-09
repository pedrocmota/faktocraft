package com.faktocraft.gametest.parity;

import com.faktocraft.common.util.LegacyTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParityDiff {

  private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
  private static final Pattern WHOLE_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

  public record Accepted(Pattern match, String reason) {
  }

  private final Map<String, String> aliases;
  private final Map<String, String> valueAliases;
  private final List<Accepted> accepted;
  private final Set<String> oldIds;
  private final Set<String> newIds;
  private final Set<String> oldProbes;
  private final Set<String> newProbes;
  private final List<String> differences = new ArrayList<>();
  private final List<String> acceptedDifferences = new ArrayList<>();

  public ParityDiff(Map<String, String> aliases, Map<String, String> valueAliases, List<Accepted> accepted,
      JsonObject oldUniverse, JsonObject newUniverse) {
    this.aliases = aliases;
    this.valueAliases = valueAliases;
    this.accepted = accepted;
    this.oldIds = ids(oldUniverse);
    this.newIds = ids(newUniverse);
    this.oldProbes = strings(oldUniverse.getAsJsonArray("probes"));
    this.newProbes = strings(newUniverse.getAsJsonArray("probes"));
  }

  private static Set<String> strings(JsonArray array) {
    Set<String> out = new HashSet<>();
    if (array != null) {
      array.forEach(e -> out.add(e.getAsString()));
    }
    return out;
  }

  private static Set<String> ids(JsonObject universe) {
    Set<String> out = new HashSet<>();
    for (String key : new String[] { "probes", "items", "blocks", "mod_blocks", "enchantments", "fluids" }) {
      out.addAll(strings(universe.getAsJsonArray(key)));
    }
    return out;
  }

  public List<String> differences() {
    return differences;
  }

  public List<String> acceptedDifferences() {
    return acceptedDifferences;
  }

  public String canon(String text) {
    Matcher matcher = ID.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String alias = aliases.get(matcher.group());
      matcher.appendReplacement(out, Matcher.quoteReplacement(alias != null ? alias : matcher.group()));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private boolean comparable(String newKey) {
    String base = newKey.contains("#") ? newKey.substring(0, newKey.indexOf('#')) : newKey;
    if (!WHOLE_ID.matcher(base).matches()) {
      return true;
    }
    return newIds.contains(base) && oldIdsCanon().contains(base);
  }

  private Set<String> oldCanonCache;

  private Set<String> oldIdsCanon() {
    if (oldCanonCache == null) {
      oldCanonCache = new HashSet<>();
      for (String id : oldIds) {
        oldCanonCache.add(canon(id));
      }
    }
    return oldCanonCache;
  }

  private void report(String path, String message) {
    String line = path + ": " + message;
    for (Accepted rule : accepted) {
      if (rule.match().matcher(line).find()) {
        acceptedDifferences.add(line + "  [accepted: " + rule.reason() + "]");
        return;
      }
    }
    differences.add(line);
  }

  public static JsonObject migrateTags(JsonObject oldTags) {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, JsonElement> group : oldTags.entrySet()) {
      Map<String, Set<String>> merged = new HashMap<>();
      for (Map.Entry<String, JsonElement> tag : group.getValue().getAsJsonObject().entrySet()) {
        String name = LegacyTags.migrate(tag.getKey());
        Set<String> members = merged.computeIfAbsent(name, k -> new TreeSet<>());
        tag.getValue().getAsJsonObject().getAsJsonArray("set").forEach(e -> members.add(e.getAsString()));
      }
      JsonObject groupObject = new JsonObject();
      merged.forEach((name, members) -> groupObject.add(name, ParitySnapshot.set(members)));
      out.add(group.getKey(), groupObject);
    }
    return out;
  }

  public void compare(String path, JsonElement old, JsonElement now) {
    if (old == null || old.isJsonNull()) {
      if (now != null && !now.isJsonNull()) {
        report(path, "only in 26.3: " + abbreviate(now.toString()));
      }
      return;
    }
    if (now == null || now.isJsonNull()) {
      report(path, "only in 1.20.1: " + abbreviate(old.toString()));
      return;
    }
    if (old.isJsonObject() && now.isJsonObject()) {
      JsonObject o = old.getAsJsonObject();
      JsonObject n = now.getAsJsonObject();
      if (isProbeSet(o) && isProbeSet(n)) {
        compareSets(path, decodeProbes(o, oldProbes), decodeProbes(n, newProbes), true);
        return;
      }
      if (o.has("set") && n.has("set") && o.size() == 1 && n.size() == 1) {
        compareSets(path, strings(o.getAsJsonArray("set")), strings(n.getAsJsonArray("set")), false);
        return;
      }
      compareObjects(path, o, n);
      return;
    }
    if (old.isJsonArray() && now.isJsonArray()) {
      JsonArray o = old.getAsJsonArray();
      JsonArray n = now.getAsJsonArray();
      if (path.startsWith("drops/") && o.size() == 2 && n.size() == 2 && o.get(0).isJsonPrimitive()) {
        compareRange(path, o, n);
        return;
      }
      if (o.size() != n.size()) {
        report(path, "1.20.1=" + abbreviate(canon(o.toString())) + " 26.3=" + abbreviate(n.toString()));
        return;
      }
      for (int i = 0; i < o.size(); i++) {
        compare(path + "/" + i, o.get(i), n.get(i));
      }
      return;
    }
    if (old.isJsonPrimitive() && now.isJsonPrimitive()) {
      JsonPrimitive o = old.getAsJsonPrimitive();
      JsonPrimitive n = now.getAsJsonPrimitive();
      if (o.isNumber() && n.isNumber()) {
        if (Math.abs(o.getAsDouble() - n.getAsDouble()) > 1.0E-3) {
          report(path, "1.20.1=" + o + " 26.3=" + n);
        }
        return;
      }
      String os = canon(o.getAsString());
      os = valueAliases.getOrDefault(os, os);
      if (!os.equals(n.getAsString())) {
        report(path, "1.20.1=" + abbreviate(os) + " 26.3=" + abbreviate(n.getAsString()));
      }
      return;
    }
    report(path, "shape differs: 1.20.1=" + abbreviate(old.toString()) + " 26.3=" + abbreviate(now.toString()));
  }

  public void compareRaw(String path, Set<String> old, Set<String> now) {
    Set<String> oldCanon = new TreeSet<>();
    for (String member : old) {
      oldCanon.add(canon(member));
    }
    Set<String> missing = new TreeSet<>(oldCanon);
    missing.removeAll(now);
    Set<String> extra = new TreeSet<>(now);
    extra.removeAll(oldCanon);
    for (String member : missing) {
      report(path, "only in 1.20.1: " + member);
    }
    for (String member : extra) {
      report(path, "only in 26.3: " + member);
    }
  }

  private void compareRange(String path, JsonArray o, JsonArray n) {
    int omin = o.get(0).getAsInt();
    int omax = o.get(1).getAsInt();
    int nmin = n.get(0).getAsInt();
    int nmax = n.get(1).getAsInt();
    boolean same = omin == nmin && omax == nmax;
    boolean bothFixed = omin == omax && nmin == nmax;
    boolean overlap = omin <= nmax && nmin <= omax;
    if (!same && (bothFixed || !overlap)) {
      report(path, "1.20.1=" + omin + ".." + omax + " 26.3=" + nmin + ".." + nmax);
    }
  }

  private static boolean isProbeSet(JsonObject object) {
    return object.size() == 1 && (object.has("only") || object.has("except"));
  }

  private static Set<String> decodeProbes(JsonObject object, Set<String> universe) {
    if (object.has("only")) {
      return strings(object.getAsJsonArray("only"));
    }
    Set<String> out = new HashSet<>(universe);
    out.removeAll(strings(object.getAsJsonArray("except")));
    return out;
  }

  private void compareSets(String path, Set<String> old, Set<String> now, boolean probes) {
    Set<String> oldCanon = new TreeSet<>();
    for (String member : old) {
      String c = canon(member);
      if (probes ? newProbes.contains(c) : knownNewOrPlain(c)) {
        oldCanon.add(c);
      }
    }
    Set<String> nowKept = new TreeSet<>();
    for (String member : now) {
      if (probes ? oldProbesCanon().contains(member) : comparable(member)) {
        nowKept.add(member);
      }
    }
    Set<String> missing = new TreeSet<>(oldCanon);
    missing.removeAll(nowKept);
    Set<String> extra = new TreeSet<>(nowKept);
    extra.removeAll(oldCanon);
    if (!missing.isEmpty() || !extra.isEmpty()) {
      StringBuilder message = new StringBuilder();
      if (!missing.isEmpty()) {
        message.append("lost ").append(abbreviate(missing.toString()));
      }
      if (!extra.isEmpty()) {
        if (message.length() > 0) {
          message.append("; ");
        }
        message.append("gained ").append(abbreviate(extra.toString()));
      }
      report(path, message.toString());
    }
  }

  private boolean knownNewOrPlain(String canonMember) {
    String base = canonMember.contains("#") ? canonMember.substring(0, canonMember.indexOf('#')) : canonMember;
    return !WHOLE_ID.matcher(base).matches() || newIds.contains(base);
  }

  private Set<String> oldProbesCanonCache;

  private Set<String> oldProbesCanon() {
    if (oldProbesCanonCache == null) {
      oldProbesCanonCache = new HashSet<>();
      for (String probe : oldProbes) {
        oldProbesCanonCache.add(canon(probe));
      }
    }
    return oldProbesCanonCache;
  }

  private void compareObjects(String path, JsonObject old, JsonObject now) {
    Map<String, String> oldKeys = new HashMap<>();
    for (String key : old.keySet()) {
      oldKeys.put(canon(key), key);
    }
    Set<String> keys = new LinkedHashSet<>(new TreeSet<>(oldKeys.keySet()));
    keys.addAll(new TreeSet<>(now.keySet()));
    for (String key : keys) {
      String oldKey = oldKeys.get(key);
      JsonElement o = oldKey == null ? null : old.get(oldKey);
      JsonElement n = now.get(key);
      String child = path + "/" + key;
      if (o == null) {
        if (n.isJsonNull()) {
          continue;
        }
        if (oldKnows(key)) {
          report(child, "only in 26.3: " + abbreviate(n.toString()));
        }
        continue;
      }
      if (n == null) {
        if (o.isJsonNull()) {
          continue;
        }
        if (knownNewOrPlain(key)) {
          report(child, "only in 1.20.1: " + abbreviate(canon(o.toString())));
        }
        continue;
      }
      compare(child, o, n);
    }
  }

  private boolean oldKnows(String newKey) {
    String base = newKey.contains("#") ? newKey.substring(0, newKey.indexOf('#')) : newKey;
    return !WHOLE_ID.matcher(base).matches() || oldIdsCanon().contains(base);
  }

  static String abbreviate(String text) {
    return text.length() > 400 ? text.substring(0, 400) + "...(" + text.length() + " chars)" : text;
  }
}
