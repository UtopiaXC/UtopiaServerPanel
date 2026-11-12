package com.utopiaxc.utopiaserverpanel.adapter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed server runtime configuration snapshot mapped into {@code status.properties}.
 */
public final class ServerPropertiesSnapshot {
    private String levelSeed = "";
    private String levelType = "default";
    private boolean generateStructures = true;
    private int viewDistance = 10;
    private int simulationDistance = 10;
    private int maxBuildHeight = 320;
    private int maxWorldSize = 29999984;

    private String gamemode = "survival";
    private String difficulty = "normal";
    private boolean hardcore = false;
    private boolean pvp = true;
    private boolean allowFlight = false;
    private boolean allowNether = true;
    private int spawnProtection = 16;

    private boolean onlineMode = true;
    private String serverIp = "0.0.0.0";
    private int serverPort = 25565;
    private long maxTickTime = 60000L;
    private boolean whiteList = false;
    private int playerIdleTimeout = 0;
    private int maxPlayers = 20;
    private List<String> whitelistPlayers = Collections.emptyList();

    public String getLevelSeed() { return levelSeed; }
    public void setLevelSeed(String levelSeed) { this.levelSeed = levelSeed != null ? levelSeed : ""; }

    public String getLevelType() { return levelType; }
    public void setLevelType(String levelType) { this.levelType = levelType != null ? levelType : "default"; }

    public boolean isGenerateStructures() { return generateStructures; }
    public void setGenerateStructures(boolean generateStructures) { this.generateStructures = generateStructures; }

    public int getViewDistance() { return viewDistance; }
    public void setViewDistance(int viewDistance) { this.viewDistance = viewDistance; }

    public int getSimulationDistance() { return simulationDistance; }
    public void setSimulationDistance(int simulationDistance) { this.simulationDistance = simulationDistance; }

    public int getMaxBuildHeight() { return maxBuildHeight; }
    public void setMaxBuildHeight(int maxBuildHeight) { this.maxBuildHeight = maxBuildHeight; }

    public int getMaxWorldSize() { return maxWorldSize; }
    public void setMaxWorldSize(int maxWorldSize) { this.maxWorldSize = maxWorldSize; }

    public String getGamemode() { return gamemode; }
    public void setGamemode(String gamemode) { this.gamemode = gamemode != null ? gamemode : "survival"; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty != null ? difficulty : "normal"; }

    public boolean isHardcore() { return hardcore; }
    public void setHardcore(boolean hardcore) { this.hardcore = hardcore; }

    public boolean isPvp() { return pvp; }
    public void setPvp(boolean pvp) { this.pvp = pvp; }

    public boolean isAllowFlight() { return allowFlight; }
    public void setAllowFlight(boolean allowFlight) { this.allowFlight = allowFlight; }

    public boolean isAllowNether() { return allowNether; }
    public void setAllowNether(boolean allowNether) { this.allowNether = allowNether; }

    public int getSpawnProtection() { return spawnProtection; }
    public void setSpawnProtection(int spawnProtection) { this.spawnProtection = spawnProtection; }

    public boolean isOnlineMode() { return onlineMode; }
    public void setOnlineMode(boolean onlineMode) { this.onlineMode = onlineMode; }

    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = (serverIp != null && !serverIp.isEmpty()) ? serverIp : "0.0.0.0"; }

    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }

    public long getMaxTickTime() { return maxTickTime; }
    public void setMaxTickTime(long maxTickTime) { this.maxTickTime = maxTickTime; }

    public boolean isWhiteList() { return whiteList; }
    public void setWhiteList(boolean whiteList) { this.whiteList = whiteList; }

    public int getPlayerIdleTimeout() { return playerIdleTimeout; }
    public void setPlayerIdleTimeout(int playerIdleTimeout) { this.playerIdleTimeout = playerIdleTimeout; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public List<String> getWhitelistPlayers() { return whitelistPlayers; }
    public void setWhitelistPlayers(List<String> whitelistPlayers) {
        this.whitelistPlayers = whitelistPlayers != null
                ? Collections.unmodifiableList(new ArrayList<String>(whitelistPlayers))
                : Collections.<String>emptyList();
    }
}

