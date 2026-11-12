package com.utopiaxc.utopiaserverpanel.command;

import java.util.Locale;

/**
 * Abstraction of the entity or console invoking a {@code /usp} command.
 */
public final class CommandCaller {
    private final boolean player;
    private final String playerUuid;
    private final String playerName;
    private final String locale;
    private final boolean hasSetupPermission;

    private CommandCaller(boolean player, String playerUuid, String playerName, String locale, boolean hasSetupPermission) {
        this.player = player;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.locale = locale != null ? locale : "";
        this.hasSetupPermission = hasSetupPermission;
    }

    public static CommandCaller player(String uuid, String name, String locale, boolean hasSetupPermission) {
        return new CommandCaller(true, uuid, name, locale, hasSetupPermission);
    }

    public static CommandCaller console(boolean hasSetupPermission) {
        return new CommandCaller(false, null, null, "en_us", hasSetupPermission);
    }

    public boolean isPlayer() { return player; }
    public String getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public String getLocale() { return locale; }
    public boolean hasSetupPermission() { return hasSetupPermission; }

    public boolean isChinese() {
        return locale.toLowerCase(Locale.ROOT).contains("zh");
    }
}

