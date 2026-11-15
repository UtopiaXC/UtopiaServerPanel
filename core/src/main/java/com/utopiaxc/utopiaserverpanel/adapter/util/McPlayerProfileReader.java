package com.utopiaxc.utopiaserverpanel.adapter.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads rich per-player statistics, advancements/achievements, and online status
 * across Minecraft 1.7.10~1.12.2 (legacy flat schema) and 1.13~1.21.1+ (namespaced schema).
 */
public final class McPlayerProfileReader {
    private static final int TOP_ITEMS_LIMIT = 15;
    private static final int MAX_ADVANCEMENTS_RETURNED = 500;

    private McPlayerProfileReader() {}

    public static Map<String, Object> readCharacterData(
            Path worldDir,
            String playerUuid,
            String playerName,
            GameAdapter adapter,
            boolean includeOnlineStatus,
            boolean includePlaytime,
            boolean includeAchievements,
            boolean includeStats) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("playerUuid", playerUuid);
        result.put("playerName", playerName);

        if (includeOnlineStatus) {
            result.put("onlineStatus", readOnlineStatus(playerUuid, playerName, adapter));
        }

        JsonObject statsRoot = null;
        if (worldDir != null && (includePlaytime || includeStats || includeAchievements)) {
            statsRoot = loadStatsJson(worldDir, playerUuid, playerName);
        }

        if (includePlaytime || includeStats) {
            Map<String, Object> parsedStats = parseStats(statsRoot, includeStats);
            if (includePlaytime) {
                result.put("playTimeSeconds", parsedStats.get("playTimeSeconds"));
            }
            if (includeStats) {
                result.put("stats", parsedStats);
            }
        }

        if (includeAchievements) {
            result.put("achievements", parseAchievements(worldDir, playerUuid, statsRoot));
        }

        return result;
    }

    private static Map<String, Object> readOnlineStatus(String playerUuid, String playerName, GameAdapter adapter) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        boolean online = false;
        String dimension = null;
        String gamemode = null;
        Float health = null;
        Float maxHealth = null;
        Integer liveDeaths = null;

        if (adapter != null) {
            try {
                List<OnlinePlayerSnapshot> onlineList = adapter.getOnlinePlayers();
                if (onlineList != null) {
                    for (OnlinePlayerSnapshot p : onlineList) {
                        if (p == null) continue;
                        if (matchesPlayer(p.getUuid(), p.getName(), playerUuid, playerName)) {
                            online = true;
                            dimension = p.getDimension();
                            gamemode = p.getGamemode();
                            health = Float.valueOf(p.getHealth());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                PanelLog.get().debug("Online player list check failed for {}: {}", playerUuid, e.getMessage());
            }

            try {
                PlayerInfoSnapshot snap = adapter.getPlayerInfo(playerUuid);
                if (snap != null && snap.isSupported()) {
                    if (snap.isOnline()) {
                        online = true;
                        if (snap.getDimension() != null) dimension = snap.getDimension();
                        if (snap.getGamemode() != null) gamemode = snap.getGamemode();
                        if (snap.getHealth() != null) health = snap.getHealth();
                        if (snap.getMaxHealth() != null) maxHealth = snap.getMaxHealth();
                    } else {
                        if (dimension == null) dimension = snap.getLastDimension();
                        if (gamemode == null) gamemode = snap.getLastGamemode();
                        if (health == null) health = snap.getLastHealth();
                    }
                    liveDeaths = snap.getDeathCount();
                }
            } catch (Exception e) {
                PanelLog.get().debug("Player info snapshot check failed for {}: {}", playerUuid, e.getMessage());
            }
        }

        status.put("online", Boolean.valueOf(online));
        status.put("dimension", dimension);
        status.put("gamemode", gamemode);
        status.put("health", health);
        status.put("maxHealth", maxHealth);
        if (liveDeaths != null) {
            status.put("deathCount", liveDeaths);
        }
        return status;
    }

    private static boolean matchesPlayer(String candidateUuid, String candidateName, String targetUuid, String targetName) {
        if (targetUuid != null && candidateUuid != null && targetUuid.equalsIgnoreCase(candidateUuid)) {
            return true;
        }
        if (targetName != null && candidateName != null && targetName.equalsIgnoreCase(candidateName)) {
            return true;
        }
        return false;
    }

    private static JsonObject loadStatsJson(Path worldDir, String playerUuid, String playerName) {
        if (worldDir == null) return null;
        Path statsDir = worldDir.resolve("stats");
        if (!Files.isDirectory(statsDir)) return null;

        List<Path> candidates = new ArrayList<Path>();
        if (playerUuid != null && !playerUuid.trim().isEmpty()) {
            String trimmed = playerUuid.trim();
            candidates.add(statsDir.resolve(trimmed + ".json"));
            String undashed = trimmed.replace("-", "");
            if (!undashed.equals(trimmed)) {
                candidates.add(statsDir.resolve(undashed + ".json"));
            }
        }
        if (playerName != null && !playerName.trim().isEmpty()) {
            candidates.add(statsDir.resolve(playerName.trim() + ".json"));
        }

        for (Path file : candidates) {
            if (Files.isRegularFile(file)) {
                JsonObject obj = readJsonObject(file);
                if (obj != null) return obj;
            }
        }
        return null;
    }

    private static JsonObject loadAdvancementsJson(Path worldDir, String playerUuid) {
        if (worldDir == null || playerUuid == null || playerUuid.trim().isEmpty()) return null;
        Path advDir = worldDir.resolve("advancements");
        if (!Files.isDirectory(advDir)) return null;

        String trimmed = playerUuid.trim();
        Path dashed = advDir.resolve(trimmed + ".json");
        if (Files.isRegularFile(dashed)) {
            return readJsonObject(dashed);
        }
        String undashed = trimmed.replace("-", "");
        if (!undashed.equals(trimmed)) {
            Path alt = advDir.resolve(undashed + ".json");
            if (Files.isRegularFile(alt)) {
                return readJsonObject(alt);
            }
        }
        return null;
    }

    private static JsonObject readJsonObject(Path file) {
        try {
            String raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(raw);
            return (parsed != null && parsed.isJsonObject()) ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            PanelLog.get().debug("Failed to read JSON file {}: {}", file, e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> parseStats(JsonObject root, boolean fullBreakdown) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (root == null) {
            out.put("available", Boolean.FALSE);
            out.put("playTimeSeconds", Long.valueOf(0L));
            return out;
        }

        boolean modern = root.has("stats") && root.get("stats").isJsonObject();
        Map<String, Long> custom = new LinkedHashMap<String, Long>();
        Map<String, Long> mined = new LinkedHashMap<String, Long>();
        Map<String, Long> crafted = new LinkedHashMap<String, Long>();
        Map<String, Long> used = new LinkedHashMap<String, Long>();
        Map<String, Long> broken = new LinkedHashMap<String, Long>();
        Map<String, Long> pickedUp = new LinkedHashMap<String, Long>();
        Map<String, Long> dropped = new LinkedHashMap<String, Long>();
        Map<String, Long> killed = new LinkedHashMap<String, Long>();
        Map<String, Long> killedBy = new LinkedHashMap<String, Long>();

        if (modern) {
            JsonObject statsObj = root.getAsJsonObject("stats");
            collectModernCategory(statsObj, "minecraft:custom", custom);
            if (fullBreakdown) {
                collectModernCategory(statsObj, "minecraft:mined", mined);
                collectModernCategory(statsObj, "minecraft:crafted", crafted);
                collectModernCategory(statsObj, "minecraft:used", used);
                collectModernCategory(statsObj, "minecraft:broken", broken);
                collectModernCategory(statsObj, "minecraft:picked_up", pickedUp);
                collectModernCategory(statsObj, "minecraft:dropped", dropped);
                collectModernCategory(statsObj, "minecraft:killed", killed);
                collectModernCategory(statsObj, "minecraft:killed_by", killedBy);
            }
        } else {
            collectLegacyStats(root, custom, mined, crafted, used, broken, pickedUp, dropped, killed, killedBy, fullBreakdown);
        }

        long playTicks = firstLong(custom,
                "minecraft:play_time", "minecraft:play_one_minute", "stat.playOneMinute");
        long playSeconds = playTicks / 20L;
        out.put("available", Boolean.TRUE);
        out.put("format", modern ? "modern" : "legacy");
        out.put("playTimeSeconds", Long.valueOf(playSeconds));

        if (!fullBreakdown) {
            return out;
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("playTimeSeconds", Long.valueOf(playSeconds));
        summary.put("totalWorldTimeSeconds", Long.valueOf(firstLong(custom, "minecraft:total_world_time") / 20L));
        summary.put("timeSinceDeathSeconds", Long.valueOf(firstLong(custom, "minecraft:time_since_death", "stat.timeSinceDeath") / 20L));
        summary.put("timeSinceRestSeconds", Long.valueOf(firstLong(custom, "minecraft:time_since_rest") / 20L));
        summary.put("deaths", Long.valueOf(firstLong(custom, "minecraft:deaths", "stat.deaths")));
        summary.put("jumps", Long.valueOf(firstLong(custom, "minecraft:jump", "stat.jump")));
        summary.put("walkDistanceCm", Long.valueOf(firstLong(custom, "minecraft:walk_one_cm", "stat.walkOneCm")));
        summary.put("sprintDistanceCm", Long.valueOf(firstLong(custom, "minecraft:sprint_one_cm", "stat.sprintOneCm")));
        summary.put("crouchDistanceCm", Long.valueOf(firstLong(custom, "minecraft:crouch_one_cm", "stat.crouchOneCm")));
        summary.put("swimDistanceCm", Long.valueOf(firstLong(custom, "minecraft:swim_one_cm", "stat.swimOneCm")));
        summary.put("walkOnWaterCm", Long.valueOf(firstLong(custom, "minecraft:walk_on_water_one_cm")));
        summary.put("walkUnderWaterCm", Long.valueOf(firstLong(custom, "minecraft:walk_under_water_one_cm", "stat.diveOneCm")));
        summary.put("flyDistanceCm", Long.valueOf(firstLong(custom, "minecraft:fly_one_cm", "stat.flyOneCm")));
        summary.put("aviateDistanceCm", Long.valueOf(firstLong(custom, "minecraft:aviate_one_cm", "stat.aviateOneCm")));
        summary.put("boatDistanceCm", Long.valueOf(firstLong(custom, "minecraft:boat_one_cm", "stat.boatOneCm")));
        summary.put("horseDistanceCm", Long.valueOf(firstLong(custom, "minecraft:horse_one_cm", "stat.horseOneCm")));
        summary.put("minecartDistanceCm", Long.valueOf(firstLong(custom, "minecraft:minecart_one_cm", "stat.minecartOneCm")));
        summary.put("climbDistanceCm", Long.valueOf(firstLong(custom, "minecraft:climb_one_cm", "stat.climbOneCm")));
        summary.put("fallDistanceCm", Long.valueOf(firstLong(custom, "minecraft:fall_one_cm", "stat.fallOneCm")));
        summary.put("mobKills", Long.valueOf(firstLong(custom, "minecraft:mob_kills", "stat.mobKills")));
        summary.put("playerKills", Long.valueOf(firstLong(custom, "minecraft:player_kills", "stat.playerKills")));
        summary.put("animalsBred", Long.valueOf(firstLong(custom, "minecraft:animals_bred", "stat.animalsBred")));
        summary.put("fishCaught", Long.valueOf(firstLong(custom, "minecraft:fish_caught", "stat.fishCaught")));
        summary.put("itemsEnchanted", Long.valueOf(firstLong(custom, "minecraft:enchant_item")));
        summary.put("tradedWithVillager", Long.valueOf(firstLong(custom, "minecraft:traded_with_villager", "stat.tradedWithVillager")));
        summary.put("damageDealt", Long.valueOf(firstLong(custom, "minecraft:damage_dealt", "stat.damageDealt")));
        summary.put("damageTaken", Long.valueOf(firstLong(custom, "minecraft:damage_taken", "stat.damageTaken")));
        summary.put("damageBlockedByShield", Long.valueOf(firstLong(custom, "minecraft:damage_blocked_by_shield")));
        summary.put("leaveGame", Long.valueOf(firstLong(custom, "minecraft:leave_game", "stat.leaveGame")));
        summary.put("sleepInBed", Long.valueOf(firstLong(custom, "minecraft:sleep_in_bed", "stat.sleepInBed")));
        summary.put("blocksMinedTotal", Long.valueOf(sumMap(mined)));
        summary.put("itemsCraftedTotal", Long.valueOf(sumMap(crafted)));
        summary.put("itemsUsedTotal", Long.valueOf(sumMap(used)));
        summary.put("itemsBrokenTotal", Long.valueOf(sumMap(broken)));
        summary.put("itemsPickedUpTotal", Long.valueOf(sumMap(pickedUp)));
        summary.put("itemsDroppedTotal", Long.valueOf(sumMap(dropped)));
        summary.put("entitiesKilledTotal", Long.valueOf(sumMap(killed)));
        summary.put("killedByTotal", Long.valueOf(sumMap(killedBy)));

        out.put("summary", summary);
        out.put("topMined", topEntries(mined, TOP_ITEMS_LIMIT));
        out.put("topCrafted", topEntries(crafted, TOP_ITEMS_LIMIT));
        out.put("topUsed", topEntries(used, TOP_ITEMS_LIMIT));
        out.put("topKilled", topEntries(killed, TOP_ITEMS_LIMIT));
        out.put("topKilledBy", topEntries(killedBy, TOP_ITEMS_LIMIT));
        out.put("customEntries", topEntries(custom, 200));
        return out;
    }

    private static void collectModernCategory(JsonObject statsObj, String categoryKey, Map<String, Long> dest) {
        if (!statsObj.has(categoryKey) || !statsObj.get(categoryKey).isJsonObject()) return;
        JsonObject cat = statsObj.getAsJsonObject(categoryKey);
        for (Map.Entry<String, JsonElement> entry : cat.entrySet()) {
            long val = readStatNumber(entry.getValue());
            if (val > 0L) {
                dest.put(entry.getKey(), Long.valueOf(val));
            }
        }
    }

    private static void collectLegacyStats(
            JsonObject root,
            Map<String, Long> custom,
            Map<String, Long> mined,
            Map<String, Long> crafted,
            Map<String, Long> used,
            Map<String, Long> broken,
            Map<String, Long> pickedUp,
            Map<String, Long> dropped,
            Map<String, Long> killed,
            Map<String, Long> killedBy,
            boolean fullBreakdown) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("achievement.")) continue;
            long val = readStatNumber(entry.getValue());
            if (val <= 0L) continue;

            if (!fullBreakdown) {
                if ("stat.playOneMinute".equals(key)) {
                    custom.put(key, Long.valueOf(val));
                }
                continue;
            }

            if (key.startsWith("stat.mineBlock.")) {
                mined.put(normalizeLegacyItemKey(key.substring("stat.mineBlock.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.craftItem.")) {
                crafted.put(normalizeLegacyItemKey(key.substring("stat.craftItem.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.useItem.")) {
                used.put(normalizeLegacyItemKey(key.substring("stat.useItem.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.breakItem.")) {
                broken.put(normalizeLegacyItemKey(key.substring("stat.breakItem.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.pickup.")) {
                pickedUp.put(normalizeLegacyItemKey(key.substring("stat.pickup.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.drop.")) {
                dropped.put(normalizeLegacyItemKey(key.substring("stat.drop.".length())), Long.valueOf(val));
            } else if (key.startsWith("stat.killEntity.")) {
                killed.put(key.substring("stat.killEntity.".length()), Long.valueOf(val));
            } else if (key.startsWith("stat.entityKilledBy.")) {
                killedBy.put(key.substring("stat.entityKilledBy.".length()), Long.valueOf(val));
            } else if (key.startsWith("stat.")) {
                custom.put(key, Long.valueOf(val));
            }
        }
    }

    private static String normalizeLegacyItemKey(String raw) {
        int idx = raw.indexOf('.');
        if (idx > 0 && idx < raw.length() - 1) {
            return raw.substring(0, idx) + ":" + raw.substring(idx + 1);
        }
        return raw;
    }

    private static long readStatNumber(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0L;
        try {
            if (el.isJsonPrimitive()) {
                return el.getAsLong();
            }
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("value") && obj.get("value").isJsonPrimitive()) {
                    return obj.get("value").getAsLong();
                }
            }
        } catch (Exception ignored) {
            // ignore non-numeric stat entries
        }
        return 0L;
    }

    private static long firstLong(Map<String, Long> map, String... keys) {
        for (String k : keys) {
            Long v = map.get(k);
            if (v != null) return v.longValue();
        }
        return 0L;
    }

    private static long sumMap(Map<String, Long> map) {
        long sum = 0L;
        for (Long v : map.values()) {
            if (v != null) sum += v.longValue();
        }
        return sum;
    }

    private static List<Map<String, Object>> topEntries(Map<String, Long> source, int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(source.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                int cmp = Long.compare(b.getValue().longValue(), a.getValue().longValue());
                return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
            }
        });
        int count = Math.min(limit, entries.size());
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", entries.get(i).getKey());
            item.put("value", entries.get(i).getValue());
            out.add(item);
        }
        return out;
    }

    private static Map<String, Object> parseAchievements(Path worldDir, String playerUuid, JsonObject statsRoot) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int completedCount = 0;
        int inProgressCount = 0;
        int recipesUnlocked = 0;

        JsonObject advRoot = loadAdvancementsJson(worldDir, playerUuid);
        if (advRoot != null) {
            for (Map.Entry<String, JsonElement> entry : advRoot.entrySet()) {
                String id = entry.getKey();
                if (id == null || "DataVersion".equals(id)) continue;
                JsonElement val = entry.getValue();
                if (val == null || !val.isJsonObject()) continue;
                JsonObject advObj = val.getAsJsonObject();

                boolean done = advObj.has("done") && advObj.get("done").isJsonPrimitive()
                        && advObj.get("done").getAsBoolean();

                if (id.contains(":recipes/") || id.startsWith("recipes/")) {
                    if (done) recipesUnlocked++;
                    continue;
                }

                String latestAt = null;
                int criteriaCount = 0;
                if (advObj.has("criteria") && advObj.get("criteria").isJsonObject()) {
                    JsonObject criteria = advObj.getAsJsonObject("criteria");
                    criteriaCount = criteria.entrySet().size();
                    for (Map.Entry<String, JsonElement> c : criteria.entrySet()) {
                        if (c.getValue() != null && c.getValue().isJsonPrimitive()) {
                            String ts = c.getValue().getAsString();
                            if (latestAt == null || ts.compareTo(latestAt) > 0) {
                                latestAt = ts;
                            }
                        }
                    }
                }

                if (done) {
                    completedCount++;
                } else {
                    inProgressCount++;
                }

                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", id);
                row.put("category", extractAdvancementCategory(id));
                row.put("done", Boolean.valueOf(done));
                row.put("achievedAt", latestAt);
                row.put("criteriaCount", Integer.valueOf(criteriaCount));
                items.add(row);
            }
        } else if (statsRoot != null) {
            // Legacy MC 1.7.10 ~ 1.11.2 stores achievements inside stats/<uuid>.json ("achievement.*")
            for (Map.Entry<String, JsonElement> entry : statsRoot.entrySet()) {
                String id = entry.getKey();
                if (id == null || !id.startsWith("achievement.")) continue;
                long val = readStatNumber(entry.getValue());
                if (val <= 0L) continue;
                completedCount++;
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", id);
                row.put("category", "legacy");
                row.put("done", Boolean.TRUE);
                row.put("achievedAt", null);
                row.put("criteriaCount", Integer.valueOf(1));
                items.add(row);
            }
        }

        Collections.sort(items, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                boolean da = Boolean.TRUE.equals(a.get("done"));
                boolean db = Boolean.TRUE.equals(b.get("done"));
                if (da != db) return da ? -1 : 1;
                String ta = (String) a.get("achievedAt");
                String tb = (String) b.get("achievedAt");
                if (ta != null && tb != null) {
                    int cmp = tb.compareTo(ta);
                    if (cmp != 0) return cmp;
                } else if (ta != null || tb != null) {
                    return ta != null ? -1 : 1;
                }
                return String.valueOf(a.get("id")).compareTo(String.valueOf(b.get("id")));
            }
        });

        if (items.size() > MAX_ADVANCEMENTS_RETURNED) {
            items = new ArrayList<Map<String, Object>>(items.subList(0, MAX_ADVANCEMENTS_RETURNED));
        }

        out.put("available", Boolean.valueOf(advRoot != null || !items.isEmpty()));
        out.put("completedCount", Integer.valueOf(completedCount));
        out.put("inProgressCount", Integer.valueOf(inProgressCount));
        out.put("recipesUnlocked", Integer.valueOf(recipesUnlocked));
        out.put("items", items);
        return out;
    }

    private static String extractAdvancementCategory(String id) {
        int colon = id.indexOf(':');
        String namespace = colon > 0 ? id.substring(0, colon) : "minecraft";
        String path = colon > 0 ? id.substring(colon + 1) : id;
        if ("minecraft".equals(namespace)) {
            int slash = path.indexOf('/');
            return slash > 0 ? path.substring(0, slash) : "core";
        }
        return namespace.toLowerCase(Locale.ROOT);
    }
}
