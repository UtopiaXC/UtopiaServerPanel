package com.utopiaxc.utopiaserverpanel.spi.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Loaded mod metadata returned by loader-specific adapters for the Mods &amp; Config manager.
 */
public final class ModEntryDto {
    private final String modId;
    private final String name;
    private final String version;
    private final String description;
    private final String authors;
    private final String homepage;
    private final String iconBase64;
    private final List<String> configFiles = new ArrayList<>();

    public ModEntryDto(String modId, String name, String version, String description, String authors, String homepage, String iconBase64) {
        this.modId = modId;
        this.name = name;
        this.version = version;
        this.description = description;
        this.authors = authors;
        this.homepage = homepage;
        this.iconBase64 = iconBase64;
    }

    public String getModId() { return modId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthors() { return authors; }
    public String getHomepage() { return homepage; }
    public String getIconBase64() { return iconBase64; }
    public List<String> getConfigFiles() { return configFiles; }
}
