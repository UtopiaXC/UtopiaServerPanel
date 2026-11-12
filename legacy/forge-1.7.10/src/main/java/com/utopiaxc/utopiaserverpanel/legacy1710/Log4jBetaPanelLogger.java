package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.logging.PanelLogger;
import org.apache.logging.log4j.Logger;

/**
 * Log4j 2.0-beta9 logger adapter for Forge 1.7.10.
 */
public final class Log4jBetaPanelLogger implements PanelLogger {
    private final Logger logger;

    public Log4jBetaPanelLogger(Logger logger) {
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

