package com.utopiaxc.utopiaserverpanel.spi.integration;

/**
 * Submission outcome returned after dispatching a crafting request onto a modded network.
 */
public final class CraftingJobResult {
    private final boolean accepted;
    private final String jobId;
    private final String message;

    public CraftingJobResult(boolean accepted, String jobId, String message) {
        this.accepted = accepted;
        this.jobId = jobId;
        this.message = message;
    }

    public boolean isAccepted() { return accepted; }
    public String getJobId() { return jobId; }
    public String getMessage() { return message; }
}

