package com.utopiaxc.utopiaserverpanel.bridge.jvm;

import com.utopiaxc.utopiaserverpanel.logging.PanelLogger;
import org.apache.logging.log4j.Logger;

/**
 * Log4j2 logger adapter bridging {@link PanelLogger} for platforms prior to 1.18 (e.g., 1.16.5).
 */
public final class Log4jPanelLogger implements PanelLogger {
    private final Logger logger;

    public Log4jPanelLogger(Logger logger) {
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

