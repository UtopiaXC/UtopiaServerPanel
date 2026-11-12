package com.utopiaxc.utopiaserverpanel.command;

/**
 * Outcome of executing a core {@code /usp} command.
 */
public final class CommandOutcome {
    private final boolean success;
    private final RichText message;

    private CommandOutcome(boolean success, RichText message) {
        this.success = success;
        this.message = message;
    }

    public static CommandOutcome ok(RichText message) {
        return new CommandOutcome(true, message);
    }

    public static CommandOutcome fail(String plainText) {
        return new CommandOutcome(false, new RichText().append(plainText));
    }

    public static CommandOutcome fail(RichText message) {
        return new CommandOutcome(false, message);
    }

    public boolean isSuccess() { return success; }
    public RichText getMessage() { return message; }
}

