package com.utopiaxc.utopiaserverpanel.platform.shared;

import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-loader installed mod metadata scanner for Fabric, NeoForge, and Forge.
 */
public final class ModernModScanner {

    private ModernModScanner() {}

    public static List<ModEntryDto> getLoadedMods() {
        List<ModEntryDto> list = new ArrayList<ModEntryDto>();
        try {
            //? if loader == 'fabric' {
            for (net.fabricmc.loader.api.ModContainer container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
                net.fabricmc.loader.api.metadata.ModMetadata meta = container.getMetadata();
                StringBuilder authors = new StringBuilder();
                for (net.fabricmc.loader.api.metadata.Person p : meta.getAuthors()) {
                    if (authors.length() > 0) authors.append(", ");
                    authors.append(p.getName());
                }
                String homepage = meta.getContact().get("homepage").orElse("");
                list.add(new ModEntryDto(
                        meta.getId(),
                        meta.getName(),
                        meta.getVersion().getFriendlyString(),
                        meta.getDescription(),
                        authors.toString(),
                        homepage,
                        null
                ));
            }
            //?} else if loader == 'neoforge' {
            for (net.neoforged.neoforgespi.language.IModInfo info : net.neoforged.fml.ModList.get().getMods()) {
                String authors = info.getConfig().getConfigElement("authors").map(Object::toString).orElse("");
                String homepage = info.getModURL().map(Object::toString).orElse("");
                list.add(new ModEntryDto(
                        info.getModId(),
                        info.getDisplayName(),
                        info.getVersion().toString(),
                        info.getDescription(),
                        authors,
                        homepage,
                        null
                ));
            }
            //?} else {
            //? if mc >= 26.1 {
            java.util.List<? extends net.minecraftforge.forgespi.language.IModInfo> mods = net.minecraftforge.fml.ModList.getMods();
            //?} else {
            java.util.List<? extends net.minecraftforge.forgespi.language.IModInfo> mods = net.minecraftforge.fml.ModList.get().getMods();
            //?}
            for (net.minecraftforge.forgespi.language.IModInfo info : mods) {
                //? if mc >= 1.19 {
                String authors = info.getConfig().getConfigElement("authors").map(Object::toString).orElse("");
                String homepage = info.getModURL().map(Object::toString).orElse("");
                //?} else if mc >= 1.18 {
                String authors = info.getConfig().getConfigElement("authors").map(Object::toString).orElse("");
                String homepage = info.getConfig().getConfigElement("displayURL").map(Object::toString).orElse("");
                //?} else {
                String authors = "";
                String homepage = info.getUpdateURL() != null ? info.getUpdateURL().toString() : "";
                //?}
                list.add(new ModEntryDto(
                        info.getModId(),
                        info.getDisplayName(),
                        info.getVersion().toString(),
                        info.getDescription(),
                        authors,
                        homepage,
                        null
                ));
            }
            //?}
        } catch (Throwable ignored) {}
        return list;
    }
}
