package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.terminal.ConsoleLogBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j 2.0-beta9 bridge capturing server logs into {@link ConsoleLogBuffer}.
 */
public final class Forge1710LogBridge extends AbstractAppender {
    private static final String APPENDER_NAME = "UspTerminalBridge1710";
    private static final PatternLayout LAYOUT = PatternLayout.createLayout(
            "[%d{HH:mm:ss}] [%t/%p] [%c]: %m%n",
            null,
            null,
            null,
            null
    );

    private Forge1710LogBridge(String name, Filter filter) {
        super(name, filter, LAYOUT, true);
    }

    @Override
    public void append(LogEvent event) {
        if (event == null || event.getLevel() == null) {
            return;
        }
        String levelName = event.getLevel().name();
        if (!ConsoleLogBuffer.shouldCapture(levelName)) {
            return;
        }
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
        ConsoleLogBuffer.appendServerLog(
                event.getMillis(),
                levelName,
                event.getLoggerName(),
                event.getThreadName(),
                message,
                event.getThrown()
        );
    }

    public static void register() {
        org.apache.logging.log4j.Logger rawRoot = LogManager.getRootLogger();
        if (!(rawRoot instanceof org.apache.logging.log4j.core.Logger)) {
            return;
        }
        org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) rawRoot;
        if (rootLogger.getAppenders().containsKey(APPENDER_NAME)) {
            return;
        }
        Forge1710LogBridge appender = new Forge1710LogBridge(APPENDER_NAME, null);
        appender.start();
        rootLogger.addAppender(appender);
    }
}
