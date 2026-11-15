package com.utopiaxc.utopiaserverpanel.auth;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Pure Java 8 WebAuthn registration and assertion verification helper (ES256 / RS256).
 */
public final class WebAuthnUtil {

    public static final int ALG_ES256 = -7;
    public static final int ALG_RS256 = -257;

    private static final int FLAG_UP = 0x01;
    private static final int FLAG_AT = 0x40;

    private WebAuthnUtil() {}

    public static String encodeB64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] decodeB64Url(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty base64url payload");
        }
        return Base64.getUrlDecoder().decode(value.trim());
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String resolveRpId(RequestContext ctx) {
        String host = ctx.header("X-Forwarded-Host");
        if (host != null && !host.trim().isEmpty()) {
            host = host.split(",")[0].trim();
        } else {
            host = ctx.header("Host");
        }
        if ((host == null || host.trim().isEmpty()) && ctx.header("Origin") != null) {
            try {
                host = URI.create(ctx.header("Origin").trim()).getHost();
            } catch (Exception ignored) {
                host = null;
            }
        }
        if (host == null || host.trim().isEmpty()) {
            return "localhost";
        }
        String trimmed = host.trim();
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close > 1) {
                return trimmed.substring(1, close).toLowerCase();
            }
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            trimmed = trimmed.substring(0, colon);
        }
        return trimmed.toLowerCase();
    }

    public static void verifyOrigin(String clientOrigin, String expectedRpId, String requestOriginHeader) {
        if (clientOrigin == null || clientOrigin.trim().isEmpty()) {
            throw new SecurityException("Missing WebAuthn clientData origin");
        }
        URI uri;
        try {
            uri = URI.create(clientOrigin.trim());
        } catch (Exception e) {
            throw new SecurityException("Malformed WebAuthn clientData origin");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new SecurityException("Unsupported WebAuthn origin scheme");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new SecurityException("Missing WebAuthn origin host");
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (!host.equalsIgnoreCase(expectedRpId)) {
            throw new SecurityException("WebAuthn origin host mismatch");
        }
        if (requestOriginHeader != null && !requestOriginHeader.trim().isEmpty()) {
            String normalizedClient = stripTrailingSlash(clientOrigin.trim());
            String normalizedHeader = stripTrailingSlash(requestOriginHeader.trim());
            if (!normalizedClient.equalsIgnoreCase(normalizedHeader)) {
                throw new SecurityException("WebAuthn request Origin mismatch");
            }
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static final class RegistrationVerified {
        public final String credentialIdB64;
        public final String publicKeyCoseB64;
        public final int algorithm;
        public final long signCount;

        public RegistrationVerified(String credentialIdB64, String publicKeyCoseB64, int algorithm, long signCount) {
            this.credentialIdB64 = credentialIdB64;
            this.publicKeyCoseB64 = publicKeyCoseB64;
            this.algorithm = algorithm;
            this.signCount = signCount;
        }
    }

    public static RegistrationVerified verifyRegistration(
            String clientDataJsonB64,
            String attestationObjectB64,
            String rawIdB64,
            byte[] expectedChallenge,
            String expectedRpId,
            String requestOriginHeader) {
        byte[] clientDataBytes = decodeB64Url(clientDataJsonB64);
        verifyClientData(clientDataBytes, "webauthn.create", expectedChallenge, expectedRpId, requestOriginHeader);

        byte[] attestationBytes = decodeB64Url(attestationObjectB64);
        Object attObj = CborLiteReader.decode(attestationBytes);
        if (!(attObj instanceof Map)) {
            throw new SecurityException("Invalid attestationObject CBOR");
        }
        Object authDataRaw = ((Map<?, ?>) attObj).get("authData");
        if (!(authDataRaw instanceof byte[])) {
            throw new SecurityException("Missing authData in attestationObject");
        }
        byte[] authData = (byte[]) authDataRaw;
        if (authData.length < 55) {
            throw new SecurityException("Attestation authData too short");
        }

        verifyRpIdHashAndFlags(authData, expectedRpId, true);
        long signCount = readUint32(authData, 33);

        // Skip 16-byte AAGUID at [37..52]
        int credIdLen = ((authData[53] & 0xFF) << 8) | (authData[54] & 0xFF);
        if (credIdLen <= 0 || 55 + credIdLen >= authData.length) {
            throw new SecurityException("Invalid credentialId length in authData");
        }
        byte[] credentialId = Arrays.copyOfRange(authData, 55, 55 + credIdLen);
        String credentialIdB64 = encodeB64Url(credentialId);
        if (rawIdB64 != null && !rawIdB64.trim().isEmpty()) {
            byte[] rawId = decodeB64Url(rawIdB64);
            if (!MessageDigest.isEqual(credentialId, rawId)) {
                throw new SecurityException("Credential ID mismatch");
            }
        }

        int coseOffset = 55 + credIdLen;
        CborLiteReader.DecodedSlice coseSlice = CborLiteReader.decodeAt(authData, coseOffset);
        byte[] coseBytes = Arrays.copyOfRange(authData, coseSlice.start, coseSlice.end);
        int alg = validateAndExtractAlgorithm(coseSlice.value);

        return new RegistrationVerified(credentialIdB64, encodeB64Url(coseBytes), alg, signCount);
    }

    public static long verifyAssertion(
            String clientDataJsonB64,
            String authenticatorDataB64,
            String signatureB64,
            byte[] expectedChallenge,
            String expectedRpId,
            String requestOriginHeader,
            String storedCoseB64,
            int storedAlgorithm,
            long storedSignCount) {
        byte[] clientDataBytes = decodeB64Url(clientDataJsonB64);
        verifyClientData(clientDataBytes, "webauthn.get", expectedChallenge, expectedRpId, requestOriginHeader);

        byte[] authData = decodeB64Url(authenticatorDataB64);
        if (authData.length < 37) {
            throw new SecurityException("Assertion authenticatorData too short");
        }
        verifyRpIdHashAndFlags(authData, expectedRpId, false);

        long newSignCount = readUint32(authData, 33);
        if ((storedSignCount > 0L || newSignCount > 0L) && newSignCount <= storedSignCount) {
            throw new SecurityException("WebAuthn signature counter regression detected");
        }

        byte[] signature = decodeB64Url(signatureB64);
        byte[] clientHash = sha256(clientDataBytes);
        byte[] signedPayload = new byte[authData.length + clientHash.length];
        System.arraycopy(authData, 0, signedPayload, 0, authData.length);
        System.arraycopy(clientHash, 0, signedPayload, authData.length, clientHash.length);

        PublicKey pubKey = decodePublicKeyFromCose(decodeB64Url(storedCoseB64), storedAlgorithm);
        verifySignature(pubKey, storedAlgorithm, signedPayload, signature);
        return newSignCount;
    }

    private static void verifyClientData(
            byte[] clientDataBytes,
            String expectedType,
            byte[] expectedChallenge,
            String expectedRpId,
            String requestOriginHeader) {
        JsonObject cd;
        try {
            cd = JsonHelper.parseObject(new String(clientDataBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SecurityException("Malformed clientDataJSON");
        }
        String type = JsonHelper.str(cd, "type");
        if (!expectedType.equals(type)) {
            throw new SecurityException("Unexpected WebAuthn ceremony type: " + type);
        }
        String challengeStr = JsonHelper.str(cd, "challenge");
        byte[] actualChallenge;
        try {
            actualChallenge = decodeB64Url(challengeStr);
        } catch (Exception e) {
            throw new SecurityException("Invalid WebAuthn challenge encoding");
        }
        if (!MessageDigest.isEqual(expectedChallenge, actualChallenge)) {
            throw new SecurityException("WebAuthn challenge mismatch");
        }
        verifyOrigin(JsonHelper.str(cd, "origin"), expectedRpId, requestOriginHeader);
    }

    private static void verifyRpIdHashAndFlags(byte[] authData, String expectedRpId, boolean requireAttestedCred) {
        byte[] expectedHash = sha256(expectedRpId.getBytes(StandardCharsets.UTF_8));
        byte[] actualHash = Arrays.copyOfRange(authData, 0, 32);
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            throw new SecurityException("WebAuthn rpIdHash mismatch");
        }
        int flags = authData[32] & 0xFF;
        if ((flags & FLAG_UP) == 0) {
            throw new SecurityException("WebAuthn user presence flag missing");
        }
        if (requireAttestedCred && (flags & FLAG_AT) == 0) {
            throw new SecurityException("WebAuthn attested credential data missing");
        }
    }

    private static long readUint32(byte[] buf, int offset) {
        return ((long) (buf[offset] & 0xFF) << 24)
                | ((long) (buf[offset + 1] & 0xFF) << 16)
                | ((long) (buf[offset + 2] & 0xFF) << 8)
                | (buf[offset + 3] & 0xFF);
    }

    private static int validateAndExtractAlgorithm(Object coseObj) {
        if (!(coseObj instanceof Map)) {
            throw new SecurityException("Invalid COSE key map");
        }
        Map<?, ?> cose = (Map<?, ?>) coseObj;
        int kty = intCoseField(cose, 1L);
        int alg = intCoseField(cose, 3L);
        buildPublicKey(cose, alg, kty);
        return alg;
    }

    public static PublicKey decodePublicKeyFromCose(byte[] coseBytes, int expectedAlg) {
        Object coseObj = CborLiteReader.decode(coseBytes);
        if (!(coseObj instanceof Map)) {
            throw new SecurityException("Invalid stored COSE key");
        }
        Map<?, ?> cose = (Map<?, ?>) coseObj;
        int kty = intCoseField(cose, 1L);
        int alg = intCoseField(cose, 3L);
        if (alg != expectedAlg) {
            throw new SecurityException("COSE algorithm mismatch");
        }
        return buildPublicKey(cose, alg, kty);
    }

    private static PublicKey buildPublicKey(Map<?, ?> cose, int alg, int kty) {
        try {
            if (alg == ALG_ES256 && kty == 2) {
                int crv = intCoseField(cose, -1L);
                if (crv != 1) {
                    throw new SecurityException("Unsupported EC curve: " + crv);
                }
                byte[] xBytes = bytesCoseField(cose, -2L);
                byte[] yBytes = bytesCoseField(cose, -3L);
                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
                ECPoint point = new ECPoint(new BigInteger(1, xBytes), new BigInteger(1, yBytes));
                return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, ecSpec));
            }
            if (alg == ALG_RS256 && kty == 3) {
                byte[] nBytes = bytesCoseField(cose, -1L);
                byte[] eBytes = bytesCoseField(cose, -2L);
                RSAPublicKeySpec rsaSpec = new RSAPublicKeySpec(new BigInteger(1, nBytes), new BigInteger(1, eBytes));
                return KeyFactory.getInstance("RSA").generatePublic(rsaSpec);
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Failed to reconstruct WebAuthn public key", e);
        }
        throw new SecurityException("Unsupported WebAuthn key algorithm: kty=" + kty + ", alg=" + alg);
    }

    private static void verifySignature(PublicKey key, int alg, byte[] payload, byte[] signature) {
        try {
            if (alg == ALG_ES256) {
                byte[] derSig = (signature.length == 64 && signature[0] != 0x30)
                        ? rawP1363ToDer(signature)
                        : signature;
                Signature verifier = Signature.getInstance("SHA256withECDSA");
                verifier.initVerify(key);
                verifier.update(payload);
                if (!verifier.verify(derSig)) {
                    throw new SecurityException("Invalid ES256 passkey signature");
                }
                return;
            }
            if (alg == ALG_RS256) {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(key);
                verifier.update(payload);
                if (!verifier.verify(signature)) {
                    throw new SecurityException("Invalid RS256 passkey signature");
                }
                return;
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Passkey signature verification failed", e);
        }
        throw new SecurityException("Unsupported passkey signature algorithm: " + alg);
    }

    private static byte[] rawP1363ToDer(byte[] p1363) {
        byte[] r = new BigInteger(1, Arrays.copyOfRange(p1363, 0, 32)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(p1363, 32, 64)).toByteArray();
        int seqLen = 2 + r.length + 2 + s.length;
        byte[] der = new byte[2 + seqLen];
        int idx = 0;
        der[idx++] = 0x30;
        der[idx++] = (byte) seqLen;
        der[idx++] = 0x02;
        der[idx++] = (byte) r.length;
        System.arraycopy(r, 0, der, idx, r.length);
        idx += r.length;
        der[idx++] = 0x02;
        der[idx++] = (byte) s.length;
        System.arraycopy(s, 0, der, idx, s.length);
        return der;
    }

    private static int intCoseField(Map<?, ?> map, long key) {
        Object v = map.get(Long.valueOf(key));
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        throw new SecurityException("Missing COSE integer field: " + key);
    }

    private static byte[] bytesCoseField(Map<?, ?> map, long key) {
        Object v = map.get(Long.valueOf(key));
        if (v instanceof byte[]) {
            return (byte[]) v;
        }
        throw new SecurityException("Missing COSE byte field: " + key);
    }
}
