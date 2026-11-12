package com.utopiaxc.utopiaserverpanel.adapter.model;

/**
 * Typed server status telemetry provided by a {@link com.utopiaxc.utopiaserverpanel.adapter.GameAdapter}.
 */
public final class ServerStatusSnapshot {
    private final int onlinePlayers;
    private final int maxPlayers;
    private final String motd;
    private final String version;
    private final String worldName;
    private final double tps;
    private final double tickTimeMs;

    public ServerStatusSnapshot(
            int onlinePlayers,
            int maxPlayers,
            String motd,
            String version,
            String worldName,
            double tps,
            double tickTimeMs
    ) {
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.motd = motd != null ? motd : "";
        this.version = version != null ? version : "";
        this.worldName = worldName != null ? worldName : "";
        this.tps = Double.isNaN(tps) || Double.isInfinite(tps) ? 20.0 : tps;
        this.tickTimeMs = Double.isNaN(tickTimeMs) || Double.isInfinite(tickTimeMs) ? 0.0 : Math.max(0.0, tickTimeMs);
    }

    public int getOnlinePlayers() { return onlinePlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public String getMotd() { return motd; }
    public String getVersion() { return version; }
    public String getWorldName() { return worldName; }
    public double getTps() { return tps; }
    public double getTickTimeMs() { return tickTimeMs; }
}

