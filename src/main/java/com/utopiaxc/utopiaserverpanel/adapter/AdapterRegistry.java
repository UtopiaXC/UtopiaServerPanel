package com.utopiaxc.utopiaserverpanel.adapter;

/**
 * Registry for the active {@link GameAdapter} instance.
 * Initialized during server startup with the platform-specific adapter.
 */
public final class AdapterRegistry {
    private static volatile GameAdapter adapter;

    private AdapterRegistry() {}

    public static void initialize(GameAdapter instance) {
        adapter = instance;
    }

    public static GameAdapter getAdapter() {
        if (adapter == null) {
            throw new IllegalStateException("AdapterRegistry not initialized. Call initialize() during server startup.");
        }
        return adapter;
    }

    public static void shutdown() {
        adapter = null;
    }
}
