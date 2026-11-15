package com.utopiaxc.utopiaserverpanel.auth;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal pure-Java 8 RFC 8949 CBOR reader sufficient for WebAuthn attestation objects and COSE keys.
 */
public final class CborLiteReader {

    private final byte[] data;
    private int pos;

    public CborLiteReader(byte[] data, int offset) {
        if (data == null || offset < 0 || offset > data.length) {
            throw new IllegalArgumentException("Invalid CBOR buffer");
        }
        this.data = data;
        this.pos = offset;
    }

    public int position() {
        return pos;
    }

    public static Object decode(byte[] data) {
        return new CborLiteReader(data, 0).readValue();
    }

    public static final class DecodedSlice {
        public final Object value;
        public final int start;
        public final int end;

        public DecodedSlice(Object value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }

    public static DecodedSlice decodeAt(byte[] data, int offset) {
        CborLiteReader reader = new CborLiteReader(data, offset);
        Object value = reader.readValue();
        return new DecodedSlice(value, offset, reader.position());
    }

    public Object readValue() {
        int initial = readByte();
        int majorType = (initial >> 5) & 0x07;
        int additional = initial & 0x1F;

        switch (majorType) {
            case 0: // unsigned integer
                return Long.valueOf(readUnsignedArgument(additional));
            case 1: // negative integer (-1 - n)
                return Long.valueOf(-1L - readUnsignedArgument(additional));
            case 2: // byte string
                if (additional == 31) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while (!peekBreak()) {
                        Object chunk = readValue();
                        if (!(chunk instanceof byte[])) {
                            throw new IllegalArgumentException("Invalid indefinite byte string chunk");
                        }
                        byte[] bytes = (byte[]) chunk;
                        baos.write(bytes, 0, bytes.length);
                    }
                    pos++; // consume break
                    return baos.toByteArray();
                }
                return readBytes((int) readUnsignedArgument(additional));
            case 3: // text string
                if (additional == 31) {
                    StringBuilder sb = new StringBuilder();
                    while (!peekBreak()) {
                        Object chunk = readValue();
                        if (!(chunk instanceof String)) {
                            throw new IllegalArgumentException("Invalid indefinite text string chunk");
                        }
                        sb.append((String) chunk);
                    }
                    pos++; // consume break
                    return sb.toString();
                }
                return new String(readBytes((int) readUnsignedArgument(additional)), StandardCharsets.UTF_8);
            case 4: // array
                List<Object> list = new ArrayList<>();
                if (additional == 31) {
                    while (!peekBreak()) {
                        list.add(readValue());
                    }
                    pos++;
                    return list;
                }
                long arrLen = readUnsignedArgument(additional);
                if (arrLen > 4096) {
                    throw new IllegalArgumentException("CBOR array too large: " + arrLen);
                }
                for (long i = 0; i < arrLen; i++) {
                    list.add(readValue());
                }
                return list;
            case 5: // map
                Map<Object, Object> map = new LinkedHashMap<>();
                if (additional == 31) {
                    while (!peekBreak()) {
                        Object k = readValue();
                        Object v = readValue();
                        map.put(k, v);
                    }
                    pos++;
                    return map;
                }
                long mapLen = readUnsignedArgument(additional);
                if (mapLen > 4096) {
                    throw new IllegalArgumentException("CBOR map too large: " + mapLen);
                }
                for (long i = 0; i < mapLen; i++) {
                    Object k = readValue();
                    Object v = readValue();
                    map.put(k, v);
                }
                return map;
            case 6: // semantic tag -> unwrap tagged value
                readUnsignedArgument(additional);
                return readValue();
            case 7: // simple & floats
                if (additional == 20) return Boolean.FALSE;
                if (additional == 21) return Boolean.TRUE;
                if (additional == 22 || additional == 23) return null;
                if (additional == 24) return Long.valueOf(readByte());
                if (additional == 25) return Integer.valueOf((readByte() << 8) | readByte());
                if (additional == 26) {
                    int bits = (readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte();
                    return Float.valueOf(Float.intBitsToFloat(bits));
                }
                if (additional == 27) {
                    long bits = 0L;
                    for (int i = 0; i < 8; i++) {
                        bits = (bits << 8) | readByte();
                    }
                    return Double.valueOf(Double.longBitsToDouble(bits));
                }
                return null;
            default:
                throw new IllegalArgumentException("Unsupported CBOR major type: " + majorType);
        }
    }

    private boolean peekBreak() {
        if (pos >= data.length) {
            throw new IllegalArgumentException("Unexpected end of CBOR data");
        }
        return (data[pos] & 0xFF) == 0xFF;
    }

    private int readByte() {
        if (pos >= data.length) {
            throw new IllegalArgumentException("Unexpected end of CBOR data at " + pos);
        }
        return data[pos++] & 0xFF;
    }

    private byte[] readBytes(int len) {
        if (len < 0 || pos + len > data.length) {
            throw new IllegalArgumentException("Invalid CBOR byte length: " + len);
        }
        byte[] out = new byte[len];
        System.arraycopy(data, pos, out, 0, len);
        pos += len;
        return out;
    }

    private long readUnsignedArgument(int additional) {
        if (additional < 24) {
            return additional;
        }
        if (additional == 24) {
            return readByte();
        }
        if (additional == 25) {
            return ((long) readByte() << 8) | readByte();
        }
        if (additional == 26) {
            return ((long) readByte() << 24)
                    | ((long) readByte() << 16)
                    | ((long) readByte() << 8)
                    | readByte();
        }
        if (additional == 27) {
            long val = 0L;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | readByte();
            }
            if (val < 0) {
                throw new IllegalArgumentException("CBOR uint64 overflow");
            }
            return val;
        }
        throw new IllegalArgumentException("Unsupported CBOR additional info: " + additional);
    }
}
