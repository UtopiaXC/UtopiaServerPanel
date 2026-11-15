package com.utopiaxc.utopiaserverpanel.auth;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared validation rules for panel usernames.
 * Ensures minimum length >= 4 (so top-level /{username} profile URLs stay clean)
 * and prevents collisions with panel routes and API paths.
 */
public final class UsernamePolicy {
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 32;
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,32}$");

    private static final Set<String> RESERVED_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "login",
            "logout",
            "register",
            "setup",
            "home",
            "tokens",
            "settings",
            "profiler",
            "mods",
            "whitelist",
            "messages",
            "forbidden",
            "bind",
            "activate",
            "assets",
            "static",
            "favicon",
            "index",
            "health",
            "status",
            "overview",
            "terminal",
            "logs",
            "public",
            "profile",
            "robots"
    )));

    private UsernamePolicy() {}

    public static final class ValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String message;
        private final String normalized;

        private ValidationResult(boolean valid, String errorCode, String message, String normalized) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.message = message;
            this.normalized = normalized;
        }

        public boolean isValid() { return valid; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getNormalized() { return normalized; }
    }

    public static ValidationResult validate(String rawUsername) {
        String trimmed = rawUsername != null ? rawUsername.trim() : "";
        if (!PATTERN.matcher(trimmed).matches()) {
            return new ValidationResult(
                    false,
                    "INVALID_USERNAME",
                    "Username must be 4-32 alphanumeric or underscore characters",
                    trimmed
            );
        }
        if (RESERVED_NAMES.contains(trimmed.toLowerCase(Locale.ROOT))) {
            return new ValidationResult(
                    false,
                    "RESERVED_USERNAME",
                    "This username is reserved by the system",
                    trimmed
            );
        }
        return new ValidationResult(true, null, null, trimmed);
    }

    public static boolean isReserved(String username) {
        if (username == null) return false;
        return RESERVED_NAMES.contains(username.trim().toLowerCase(Locale.ROOT));
    }
}
