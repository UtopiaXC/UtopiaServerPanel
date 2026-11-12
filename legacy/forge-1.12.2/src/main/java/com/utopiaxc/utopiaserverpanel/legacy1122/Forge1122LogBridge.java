package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.terminal.ConsoleLogBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j 2.8 bridge capturing server logs into {@link ConsoleLogBuffer}.
 */
public final class Forge1122LogBridge extends AbstractAppender {
    private static final String APPENDER_NAME = "UspTerminalBridge1122";
    private static final PatternLayout LAYOUT = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%p] [%c]: %m%n")
            .withNoConsoleNoAnsi(false)
            .withAlwaysWriteExceptions(true)
            .build();

    private Forge1122LogBridge(String name, Filter filter) {
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
                event.getTimeMillis(),
                levelName,
                event.getLoggerName(),
                event.getThreadName(),
                message,
                event.getThrown()
        );
    }

    public static void register() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (ctx.getConfiguration().getAppender(APPENDER_NAME) != null) {
            return;
        }
        Forge1122LogBridge appender = new Forge1122LogBridge(APPENDER_NAME, null);
        appender.start();
        ctx.getConfiguration().addAppender(appender);
        ctx.getRootLogger().addAppender(ctx.getConfiguration().getAppender(appender.getName()));
        ctx.updateLoggers();
    }
}

