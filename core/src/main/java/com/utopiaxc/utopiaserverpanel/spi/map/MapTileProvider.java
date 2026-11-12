package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Pure Java SPI for map tile providers (self-built top-down chunk renderer or third-party map bridge).
 */
public interface MapTileProvider {

    /** Identifier of the map backend (e.g., "builtin", "journeymap", "xaero"). */
    String getProviderId();

    /** MIME type of returned tile images (e.g., "image/png" or "image/webp"). */
    String getContentType();

    /**
     * Render or fetch an encoded tile image, or return {@code null} when no tile exists.
     */
    byte[] getTile(String dimension, int zoom, int tileX, int tileZ);
}

