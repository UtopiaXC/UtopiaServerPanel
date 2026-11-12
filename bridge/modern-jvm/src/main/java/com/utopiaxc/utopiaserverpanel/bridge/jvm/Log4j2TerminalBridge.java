package com.utopiaxc.utopiaserverpanel.bridge.jvm;

import com.utopiaxc.utopiaserverpanel.terminal.ConsoleLogBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Shared Log4j2 appender forwarding server console logs into {@link ConsoleLogBuffer}
 * across modern Minecraft versions (1.16.5 through 26.3+).
 */
public final class Log4j2TerminalBridge extends AbstractAppender {
    private static final String APPENDER_NAME = "UspTerminalBridge";
    private static final PatternLayout LAYOUT = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss}] [%t/%p] [%c]: %m%n")
            .withNoConsoleNoAnsi(false)
            .withAlwaysWriteExceptions(true)
            .build();

    private Log4j2TerminalBridge(String name, Filter filter) {
        super(name, filter, LAYOUT, true, Property.EMPTY_ARRAY);
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
        Log4j2TerminalBridge appender = new Log4j2TerminalBridge(APPENDER_NAME, null);
        appender.start();
        ctx.getConfiguration().addAppender(appender);
        ctx.getRootLogger().addAppender(ctx.getConfiguration().getAppender(appender.getName()));
        ctx.updateLoggers();
    }
}

