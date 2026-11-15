package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Installed mod inspector, mod config editor (with .bak backup), {@code server.properties} editor,
 * and graceful server restart coordinator.
 */
public final class ModConfigService {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean pendingRestart = false;

    private ModConfigService() {}

    public static boolean isPendingRestart() { return pendingRestart; }

    public static void clearPendingRestart() { pendingRestart = false; }

    public static Map<String, Object> getOverview() {
        GameAdapter adapter = AdapterRegistry.getNullable();
        List<ModEntryDto> mods = adapter != null ? adapter.getLoadedMods() : Collections.emptyList();
        if (mods == null) mods = Collections.emptyList();

        List<String> allConfigRelPaths = discoverConfigFiles(adapter);
        Set<String> matchedFiles = new LinkedHashSet<String>();
        List<Map<String, Object>> modRows = new ArrayList<Map<String, Object>>();

        for (ModEntryDto mod : mods) {
            List<String> filesForMod = new ArrayList<String>(mod.getConfigFiles());
            String idLower = mod.getModId() != null ? mod.getModId().toLowerCase(Locale.ROOT) : "";
            String idCompact = idLower.replace("_", "").replace("-", "");

            for (String rel : allConfigRelPaths) {
                String relLower = rel.toLowerCase(Locale.ROOT);
                String relCompact = relLower.replace("_", "").replace("-", "");
                if ((!idLower.isEmpty() && relLower.contains(idLower))
                        || (!idCompact.isEmpty() && idCompact.length() >= 3 && relCompact.contains(idCompact))) {
                    if (!filesForMod.contains(rel)) {
                        filesForMod.add(rel);
                    }
                }
            }
            matchedFiles.addAll(filesForMod);

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("modId", mod.getModId());
            row.put("name", mod.getName());
            row.put("version", mod.getVersion());
            row.put("description", mod.getDescription());
            row.put("authors", mod.getAuthors());
            row.put("homepage", mod.getHomepage());
            row.put("iconBase64", mod.getIconBase64());
            row.put("configFiles", filesForMod);
            modRows.add(row);
        }

        List<String> otherConfigs = new ArrayList<String>();
        for (String rel : allConfigRelPaths) {
            if (!matchedFiles.contains(rel)) otherConfigs.add(rel);
        }

        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("mods", modRows);
        res.put("allConfigFiles", allConfigRelPaths);
        res.put("unmappedConfigFiles", otherConfigs);
        res.put("pendingRestart", Boolean.valueOf(pendingRestart));
        res.put("restartHint", SettingsService.getString("server_restart_hint", ""));
        return res;
    }

    public static Map<String, Object> readConfigFile(String relPath) throws IOException {
        Path file = resolveEditableFile(relPath);
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Config file not found: " + relPath);
        }
        long size = Files.size(file);
        if (size > 2L * 1024L * 1024L) {
            throw new IllegalArgumentException("Config file exceeds 2 MB limit");
        }
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String ext = getExtension(file.getFileName().toString());

        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("path", relPath);
        res.put("fileName", file.getFileName().toString());
        res.put("extension", ext);
        res.put("size", Long.valueOf(size));
        res.put("lastModified", Long.valueOf(Files.getLastModifiedTime(file).toMillis()));
        res.put("content", content);
        res.put("structuredEntries", parseFlatStructuredEntries(ext, content));
        res.put("pendingRestart", Boolean.valueOf(pendingRestart));
        return res;
    }

    public static void saveConfigFile(String relPath, String rawContent, List<Map<String, String>> structuredUpdates) throws IOException {
        Path file = resolveEditableFile(relPath);
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Config file not found: " + relPath);
        }
        String ext = getExtension(file.getFileName().toString());
        String toWrite = rawContent != null ? rawContent : new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        if (structuredUpdates != null && !structuredUpdates.isEmpty()) {
            toWrite = applyStructuredUpdates(ext, toWrite, structuredUpdates);
        }

        if ("json".equals(ext)) {
            // Validate JSON syntax before overwriting
            new JsonParser().parse(toWrite);
        }

        // Create .bak backup
        Path backup = file.resolveSibling(file.getFileName().toString() + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

        Files.write(file, toWrite.getBytes(StandardCharsets.UTF_8));
        pendingRestart = true;
    }

    public static String readServerPropertiesRaw() throws IOException {
        Path p = resolveServerPropertiesPath();
        if (p == null || !Files.isRegularFile(p)) return "";
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static Map<String, Object> getServerPropertiesView() throws IOException {
        Path p = resolveServerPropertiesPath();
        String raw = (p != null && Files.isRegularFile(p))
                ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : "";
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("exists", Boolean.valueOf(p != null && Files.isRegularFile(p)));
        res.put("raw", raw);
        res.put("entries", parseFlatStructuredEntries("properties", raw));
        res.put("pendingRestart", Boolean.valueOf(pendingRestart));
        res.put("restartHint", SettingsService.getString("server_restart_hint", ""));
        return res;
    }

    public static void saveServerProperties(String rawContent, List<Map<String, String>> updates) throws IOException {
        Path p = resolveServerPropertiesPath();
        if (p == null) {
            throw new IllegalStateException("Server directory unavailable");
        }
        String base = rawContent != null ? rawContent : (Files.isRegularFile(p) ? readServerPropertiesRaw() : "");
        if (updates != null && !updates.isEmpty()) {
            base = applyStructuredUpdates("properties", base, updates);
        }
        if (Files.isRegularFile(p)) {
            Path bak = p.resolveSibling("server.properties.bak");
            Files.copy(p, bak, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(p, base.getBytes(StandardCharsets.UTF_8));
        pendingRestart = true;

        // Sync live white-list property immediately if edited in server.properties
        String whiteListVal = findStructuredValue(base, "white-list");
        if (whiteListVal != null) {
            GameAdapter adapter = AdapterRegistry.getNullable();
            if (adapter != null) {
                boolean wlOn = "true".equalsIgnoreCase(whiteListVal.trim());
                adapter.executeCommand(wlOn ? "whitelist on" : "whitelist off");
                if (!wlOn) {
                    SettingsService.set("whitelist_management_enabled", "false");
                }
            }
        }
    }

    private static String findStructuredValue(String rawProperties, String targetKey) {
        if (rawProperties == null || targetKey == null) return null;
        for (String line : rawProperties.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq > 0 && targetKey.equalsIgnoreCase(trimmed.substring(0, eq).trim())) {
                return trimmed.substring(eq + 1).trim();
            }
        }
        return null;
    }

    public static Map<String, Object> triggerRestart(String adminUsername) {
        GameAdapter adapter = AdapterRegistry.getNullable();
        String customCmd = SettingsService.getString("server_restart_command", "");
        String hint = SettingsService.getString("server_restart_hint", "");
        if (adapter != null) {
            if (customCmd != null && !customCmd.trim().isEmpty()) {
                adapter.executeCommand(customCmd.trim());
            } else {
                adapter.shutdownServer("Restart requested from UtopiaServerPanel by " + adminUsername);
            }
        }
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("dispatched", Boolean.valueOf(adapter != null));
        res.put("method", (customCmd != null && !customCmd.trim().isEmpty()) ? customCmd.trim() : "stop");
        res.put("restartHint", hint);
        return res;
    }

    private static List<String> discoverConfigFiles(GameAdapter adapter) {
        if (adapter == null) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        Path serverDir = adapter.getServerDirectory();
        Path configDir = adapter.getConfigDirectory();
        Path worldDir = adapter.getWorldDirectory();

        if (configDir != null && Files.isDirectory(configDir)) {
            scanDirConfigFiles(configDir, "config/", out);
        }
        if (worldDir != null && Files.isDirectory(worldDir.resolve("serverconfig"))) {
            scanDirConfigFiles(worldDir.resolve("serverconfig"), "serverconfig/", out);
        } else if (serverDir != null && Files.isDirectory(serverDir.resolve("defaultconfigs"))) {
            scanDirConfigFiles(serverDir.resolve("defaultconfigs"), "defaultconfigs/", out);
        }
        Collections.sort(out);
        return out;
    }

    private static void scanDirConfigFiles(Path root, String prefix, List<String> out) {
        try (Stream<Path> walk = Files.walk(root, 3)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".bak") || name.endsWith(".db") || name.endsWith(".png")) return;
                if (name.endsWith(".toml") || name.endsWith(".json") || name.endsWith(".json5")
                        || name.endsWith(".cfg") || name.endsWith(".properties")
                        || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt")) {
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    out.add(prefix + rel);
                }
            });
        } catch (IOException ignored) {}
    }

    private static Path resolveEditableFile(String relPath) {
        if (relPath == null || relPath.trim().isEmpty()) return null;
        String norm = relPath.trim().replace('\\', '/');
        if (norm.contains("..")) return null;

        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null) return null;

        Path baseDir = null;
        String subPath = norm;
        if (norm.startsWith("config/")) {
            baseDir = adapter.getConfigDirectory();
            subPath = norm.substring("config/".length());
        } else if (norm.startsWith("serverconfig/") && adapter.getWorldDirectory() != null) {
            baseDir = adapter.getWorldDirectory().resolve("serverconfig");
            subPath = norm.substring("serverconfig/".length());
        } else if (norm.startsWith("defaultconfigs/") && adapter.getServerDirectory() != null) {
            baseDir = adapter.getServerDirectory().resolve("defaultconfigs");
            subPath = norm.substring("defaultconfigs/".length());
        }
        if (baseDir == null || subPath.isEmpty()) return null;

        Path resolved = baseDir.resolve(subPath).normalize();
        if (!resolved.startsWith(baseDir.normalize())) return null;
        return resolved;
    }

    private static Path resolveServerPropertiesPath() {
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null || adapter.getServerDirectory() == null) return null;
        return adapter.getServerDirectory().resolve("server.properties");
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static List<Map<String, String>> parseFlatStructuredEntries(String ext, String content) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        if (content == null || content.isEmpty()) return list;
        if ("properties".equals(ext) || "toml".equals(ext) || "cfg".equals(ext)) {
            String section = "";
            String[] lines = content.split("\r?\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith(";")) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed.substring(1, trimmed.length() - 1).trim();
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    String key = trimmed.substring(0, eq).trim();
                    String val = trimmed.substring(eq + 1).trim();
                    // Strip trailing TOML inline comment only if not inside quotes
                    if ("toml".equals(ext) && val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    if (!val.startsWith("[") && !val.startsWith("{")) {
                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("section", section);
                        row.put("key", key);
                        row.put("value", val);
                        row.put("type", inferValueType(val));
                        list.add(row);
                    }
                }
            }
        } else if ("json".equals(ext)) {
            try {
                JsonElement el = new JsonParser().parse(content);
                if (el != null && el.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            Map<String, String> row = new LinkedHashMap<String, String>();
                            row.put("section", "");
                            row.put("key", e.getKey());
                            row.put("value", e.getValue().getAsString());
                            row.put("type", inferValueType(e.getValue().getAsString()));
                            list.add(row);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static String inferValueType(String v) {
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) return "boolean";
        try {
            Double.parseDouble(v);
            return "number";
        } catch (NumberFormatException ignored) {
            return "string";
        }
    }

    static String applyStructuredUpdates(String ext, String raw, List<Map<String, String>> updates) {
        if ("json".equals(ext)) {
            JsonElement el = new JsonParser().parse(raw);
            if (el != null && el.isJsonObject()) {
                for (Map<String, String> u : updates) {
                    String k = u.get("key");
                    String v = u.get("value");
                    String t = u.get("type");
                    if (k == null || v == null) continue;
                    if ("boolean".equals(t)) {
                        el.getAsJsonObject().addProperty(k, Boolean.valueOf(Boolean.parseBoolean(v)));
                    } else if ("number".equals(t)) {
                        try {
                            if (v.contains(".")) el.getAsJsonObject().addProperty(k, Double.valueOf(Double.parseDouble(v)));
                            else el.getAsJsonObject().addProperty(k, Long.valueOf(Long.parseLong(v)));
                        } catch (NumberFormatException e) {
                            el.getAsJsonObject().addProperty(k, v);
                        }
                    } else {
                        el.getAsJsonObject().addProperty(k, v);
                    }
                }
                return PRETTY_GSON.toJson(el);
            }
            return raw;
        }

        // Preserve line comments and formatting for .properties / .toml / .cfg
        Map<String, String> pending = new LinkedHashMap<String, String>();
        for (Map<String, String> u : updates) {
            String k = u.get("key");
            String v = u.get("value");
            if (k != null && v != null) {
                String sec = u.get("section");
                String fullKey = (sec != null && !sec.isEmpty()) ? (sec + "::" + k) : k;
                pending.put(fullKey, v);
            }
        }

        String[] lines = raw.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder(raw.length() + 128);
        String curSection = "";
        Set<String> applied = new LinkedHashSet<String>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                curSection = trimmed.substring(1, trimmed.length() - 1).trim();
                sb.append(line);
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//") && trimmed.indexOf('=') > 0) {
                int eq = line.indexOf('=');
                String left = line.substring(0, eq).trim();
                String secKey = curSection.isEmpty() ? left : (curSection + "::" + left);
                String newVal = pending.containsKey(secKey) ? pending.get(secKey) : pending.get(left);
                if (newVal != null) {
                    applied.add(pending.containsKey(secKey) ? secKey : left);
                    if ("toml".equals(ext) && "string".equals(inferValueType(newVal))) {
                        sb.append(line, 0, eq + 1).append(" \"").append(newVal.replace("\"", "\\\"")).append('"');
                    } else if ("toml".equals(ext)) {
                        sb.append(line, 0, eq + 1).append(' ').append(newVal);
                    } else {
                        sb.append(line, 0, eq + 1).append(newVal);
                    }
                } else {
                    sb.append(line);
                }
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) sb.append('\n');
        }
        // Append any newly added keys cleanly for properties
        if ("properties".equals(ext)) {
            for (Map.Entry<String, String> e : pending.entrySet()) {
                if (!applied.contains(e.getKey()) && !e.getKey().contains("::")) {
                    sb.append('\n').append(e.getKey()).append('=').append(e.getValue());
                }
            }
        }
        return sb.toString();
    }
}
