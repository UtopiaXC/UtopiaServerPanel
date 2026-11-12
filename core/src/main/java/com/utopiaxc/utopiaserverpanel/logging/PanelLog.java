package com.utopiaxc.utopiaserverpanel.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central logger accessor for {@code core}. Defaults to a JUL-backed logger so core can
 * run standalone in tests or plugins, and delegates to platform loggers once initialized.
 */
public final class PanelLog {
    private static volatile PanelLogger logger = new JulPanelLogger();

    private PanelLog() {}

    public static void setLogger(PanelLogger platformLogger) {
        if (platformLogger != null) {
            logger = platformLogger;
        }
    }

    public static PanelLogger get() {
        return logger;
    }

    static String format(String pattern, Object... args) {
        if (pattern == null) return "null";
        if (args == null || args.length == 0) return pattern;
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int argIndex = 0;
        int i = 0;
        while (i < pattern.length()) {
            int brace = pattern.indexOf("{}", i);
            if (brace < 0 || argIndex >= args.length) {
                sb.append(pattern, i, pattern.length());
                break;
            }
            sb.append(pattern, i, brace);
            sb.append(args[argIndex++]);
            i = brace + 2;
        }
        return sb.toString();
    }

    private static final class JulPanelLogger implements PanelLogger {
        private static final Logger JUL = Logger.getLogger("UtopiaServerPanel");

        @Override
        public void info(String message, Object... args) {
            JUL.log(Level.INFO, format(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            Throwable thrown = extractTrailingThrowable(args);
            String formatted = format(message, stripTrailingThrowable(args, thrown));
            if (thrown != null) {
                JUL.log(Level.WARNING, formatted, thrown);
            } else {
                JUL.log(Level.WARNING, formatted);
            }
        }

        @Override
        public void error(String message, Object... args) {
            Throwable thrown = extractTrailingThrowable(args);
            String formatted = format(message, stripTrailingThrowable(args, thrown));
            if (thrown != null) {
                JUL.log(Level.SEVERE, formatted, thrown);
            } else {
                JUL.log(Level.SEVERE, formatted);
            }
        }

        @Override
        public void error(String message, Throwable throwable) {
            JUL.log(Level.SEVERE, message, throwable);
        }

        @Override
        public void debug(String message, Object... args) {
            JUL.log(Level.FINE, format(message, args));
        }

        private static Throwable extractTrailingThrowable(Object[] args) {
            if (args != null && args.length > 0 && args[args.length - 1] instanceof Throwable) {
                return (Throwable) args[args.length - 1];
            }
            return null;
        }

        private static Object[] stripTrailingThrowable(Object[] args, Throwable thrown) {
            if (thrown == null || args == null) return args;
            Object[] trimmed = new Object[args.length - 1];
            System.arraycopy(args, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
    }
}

