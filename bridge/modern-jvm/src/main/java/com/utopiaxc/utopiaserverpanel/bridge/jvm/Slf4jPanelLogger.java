package com.utopiaxc.utopiaserverpanel.bridge.jvm;

import com.utopiaxc.utopiaserverpanel.logging.PanelLogger;
import org.slf4j.Logger;

/**
 * SLF4J logger adapter bridging {@link PanelLogger} for Minecraft 1.18+ platforms.
 */
public final class Slf4jPanelLogger implements PanelLogger {
    private final Logger logger;

    public Slf4jPanelLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message, Object... args) {
        if (args == null || args.length == 0) {
            logger.info(message);
        } else {
            logger.info(message, args);
        }
    }

    @Override
    public void warn(String message, Object... args) {
        if (args == null || args.length == 0) {
            logger.warn(message);
        } else {
            logger.warn(message, args);
        }
    }

    @Override
    public void error(String message, Object... args) {
        if (args == null || args.length == 0) {
            logger.error(message);
        } else {
            logger.error(message, args);
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public void debug(String message, Object... args) {
        if (args == null || args.length == 0) {
            logger.debug(message);
        } else {
            logger.debug(message, args);
        }
    }
}

