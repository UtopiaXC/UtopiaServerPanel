package com.utopiaxc.utopiaserverpanel;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue WEB_PORT = BUILDER
            .comment("The port for the web panel server (HTTP + WebSocket)")
            .defineInRange("webPort", 55533, 1024, 65535);

    public static final ModConfigSpec.IntValue BROADCAST_INTERVAL = BUILDER
            .comment("Interval in milliseconds for broadcasting server status to connected web panels")
            .defineInRange("broadcastInterval", 3000, 200, 60000);

    public static final ModConfigSpec.IntValue MAX_LOG_LINES = BUILDER
            .comment("Maximum number of log lines to keep in the console buffer")
            .defineInRange("maxLogLines", 1000, 100, 100000);

    static final ModConfigSpec SPEC = BUILDER.build();
}
