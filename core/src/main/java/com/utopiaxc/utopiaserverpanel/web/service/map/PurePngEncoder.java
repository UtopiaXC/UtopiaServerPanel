package com.utopiaxc.utopiaserverpanel.web.service.map;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Pure-Java 32-bit RGBA PNG encoder using {@link Deflater} and {@link CRC32}.
 * Avoids {@code java.awt} completely so headless Linux JREs never fail.
 */
public final class PurePngEncoder {
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PurePngEncoder() {}

    /**
     * Encode an ARGB pixel buffer (row-major, length {@code width * height}) as an RGBA PNG byte array.
     */
    public static byte[] encodeRgba(int width, int height, int[] argbPixels) {
        if (width <= 0 || height <= 0 || argbPixels == null || argbPixels.length < width * height) {
            throw new IllegalArgumentException("Invalid image dimensions or buffer");
        }
        int stride = 1 + width * 4;
        byte[] raw = new byte[stride * height];
        for (int y = 0; y < height; y++) {
            int rowBase = y * stride;
            raw[rowBase] = 0; // Filter type 0 (None)
            int srcBase = y * width;
            for (int x = 0; x < width; x++) {
                int argb = argbPixels[srcBase + x];
                int dst = rowBase + 1 + (x * 4);
                raw[dst] = (byte) ((argb >>> 16) & 0xFF);     // R
                raw[dst + 1] = (byte) ((argb >>> 8) & 0xFF);  // G
                raw[dst + 2] = (byte) (argb & 0xFF);          // B
                raw[dst + 3] = (byte) ((argb >>> 24) & 0xFF); // A
            }
        }

        byte[] compressed = deflate(raw);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(compressed.length + 128);
        try {
            baos.write(PNG_SIGNATURE);

            // IHDR
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
            DataOutputStream ihdrOut = new DataOutputStream(ihdr);
            ihdrOut.writeInt(width);
            ihdrOut.writeInt(height);
            ihdrOut.writeByte(8); // bit depth
            ihdrOut.writeByte(6); // color type RGBA
            ihdrOut.writeByte(0); // compression
            ihdrOut.writeByte(0); // filter
            ihdrOut.writeByte(0); // interlace
            ihdrOut.flush();
            writeChunk(baos, "IHDR", ihdr.toByteArray());

            // IDAT
            writeChunk(baos, "IDAT", compressed);

            // IEND
            writeChunk(baos, "IEND", new byte[0]);
        } catch (IOException ignored) {
            // ByteArrayOutputStream never throws IOException
        }
        return baos.toByteArray();
    }

    /**
     * Downsample a square {@code srcSize x srcSize} ARGB buffer by 2x into {@code (srcSize/2) x (srcSize/2)},
     * averaging non-transparent pixels in each 2x2 block.
     */
    public static int[] downsampleHalf(int srcSize, int[] srcArgb) {
        int dstSize = srcSize / 2;
        int[] dst = new int[dstSize * dstSize];
        for (int y = 0; y < dstSize; y++) {
            int sy = y * 2;
            for (int x = 0; x < dstSize; x++) {
                int sx = x * 2;
                int r = 0, g = 0, b = 0, aSum = 0, count = 0;
                for (int dy = 0; dy < 2; dy++) {
                    int row = (sy + dy) * srcSize;
                    for (int dx = 0; dx < 2; dx++) {
                        int px = srcArgb[row + sx + dx];
                        int alpha = (px >>> 24) & 0xFF;
                        if (alpha > 0) {
                            r += (px >>> 16) & 0xFF;
                            g += (px >>> 8) & 0xFF;
                            b += px & 0xFF;
                            aSum += alpha;
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    int avgA = Math.min(255, aSum / count);
                    dst[y * dstSize + x] = (avgA << 24)
                            | ((r / count) << 16)
                            | ((g / count) << 8)
                            | (b / count);
                }
            }
        }
        return dst;
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(4);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            if (n > 0) {
                out.write(buf, 0, n);
            } else if (!deflater.finished() && deflater.needsInput()) {
                break;
            }
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes("US-ASCII");
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(data.length);
        dos.write(typeBytes);
        dos.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }
}
