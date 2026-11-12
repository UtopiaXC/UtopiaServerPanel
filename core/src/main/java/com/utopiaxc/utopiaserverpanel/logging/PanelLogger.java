package com.utopiaxc.utopiaserverpanel.logging;

/**
 * Platform-independent logger facade so the panel core stays decoupled from SLF4J or Log4j versions.
 */
public interface PanelLogger {
    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    void error(String message, Throwable throwable);

    void debug(String message, Object... args);
}

