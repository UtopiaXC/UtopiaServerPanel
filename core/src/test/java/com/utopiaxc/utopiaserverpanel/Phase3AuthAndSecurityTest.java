package com.utopiaxc.utopiaserverpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.auth.CaptchaService;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.auth.WebAuthnUtil;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.Schema;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.InGameChatEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.InSiteMessage;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserPasskey;
import com.utopiaxc.utopiaserverpanel.web.db.model.WhitelistApplication;
import com.utopiaxc.utopiaserverpanel.web.handler.HttpRequestDispatcher;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.MessageService;
import com.utopiaxc.utopiaserverpanel.web.service.PasskeyService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import com.utopiaxc.utopiaserverpanel.web.service.WhitelistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Phase3AuthAndSecurityTest {

    @AfterEach
    void cleanup() {
        try {
            MyBatisFactory.shutdown();
        } catch (Throwable ignored) {}
        try {
            AdapterRegistry.shutdown();
        } catch (Throwable ignored) {}
    }

    @Test
    void testSmtpGatingAndGuestWhitelistToggleAndStructuredSocial(@TempDir Path tempDir) throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        Files.write(serverDir.resolve("server.properties"),
                "white-list=true\nonline-mode=false\n".getBytes(StandardCharsets.UTF_8));
        Files.write(serverDir.resolve("whitelist.json"), "[]".getBytes(StandardCharsets.UTF_8));

        List<String> executedCommands = new ArrayList<String>();
        AdapterRegistry.initialize(createMockAdapter(serverDir, configDir, worldDir, executedCommands));
        MyBatisFactory.initialize(worldDir.resolve("utopia_server_panel.db"));
        Schema.initialize();
        SettingsService.reload();

        // 1. SMTP gating: when SMTP disabled or missing host, requireEmailVerification & whitelistSendEmail resolve false
        SettingsService.set("smtp_enabled", "false");
        SettingsService.set("require_email_verification", "true");
        SettingsService.set("whitelist_send_email", "true");
        assertFalse(SettingsService.isRequireEmailVerification());
        assertFalse(SettingsService.isWhitelistSendEmail());

        SettingsService.set("smtp_enabled", "true");
        SettingsService.set("smtp_host", "smtp.example.com");
        assertTrue(SettingsService.isRequireEmailVerification());
        assertTrue(SettingsService.isWhitelistSendEmail());

        // 2. Guest whitelist toggle preserves other guest permissions
        SettingsService.setGuestWhitelistApplyAllowed(false);
        assertFalse(PermissionService.guestMeetsRequirement("whitelist_apply:2"));
        assertTrue(PermissionService.guestMeetsRequirement("dashboard:1"));

        SettingsService.set("whitelist_management_enabled", "true");
        boolean guestBlocked = false;
        try {
            WhitelistService.submitApplication(
                    null, "GuestApplicant", null, null, "auto",
                    "guest@example.com", "Play", "", false, null, "127.0.0.1");
        } catch (SecurityException expected) {
            guestBlocked = true;
        }
        assertTrue(guestBlocked);

        SettingsService.setGuestWhitelistApplyAllowed(true);
        assertTrue(PermissionService.guestMeetsRequirement("whitelist_apply:2"));

        // 3. Structured social accounts & required platform checks
        SettingsService.set("whitelist_social_required", "true");
        SettingsService.set("whitelist_required_social_platforms", "[\"discord\",\"wechat\"]");
        assertTrue(SettingsService.isWhitelistSocialRequired());
        assertEquals(Arrays.asList("discord", "wechat"), SettingsService.getWhitelistRequiredSocialPlatforms());

        final int[] applicantId = new int[1];
        MyBatisFactory.doWork(session -> {
            UserMapper um = session.getMapper(UserMapper.class);
            long now = System.currentTimeMillis() / 1000L;
            UserAccount appUser = new UserAccount();
            appUser.setUsername("wluser");
            appUser.setNickname("WL User");
            appUser.setEmail("a@example.com");
            appUser.setPasswordHash(PasswordUtil.hash("Pass1234!"));
            appUser.setRoleId(1);
            appUser.setStatus("active");
            appUser.setLocale("en-US");
            appUser.setCreatedAt(now);
            appUser.setUpdatedAt(now);
            um.insert(appUser);
            applicantId[0] = appUser.getId();
        });

        boolean missingSocial = false;
        try {
            WhitelistService.submitApplication(
                    applicantId[0], "AliceApplicant", null, null, "auto",
                    "a@example.com", "Join server", "", false, solveTestCaptcha(), "127.0.0.1");
        } catch (IllegalArgumentException expected) {
            missingSocial = true;
        }
        assertTrue(missingSocial);

        boolean missingWechat = false;
        try {
            WhitelistService.submitApplication(
                    applicantId[0], "AliceApplicant", null, null, "auto",
                    "a@example.com", "Join server",
                    "[{\"platform\":\"discord\",\"account\":\"alice#1234\"}]",
                    false, solveTestCaptcha(), "127.0.0.1");
        } catch (IllegalArgumentException expected) {
            missingWechat = true;
        }
        assertTrue(missingWechat);

        String validSocialJson = "[{\"platform\":\"discord\",\"account\":\"alice#1234\"},"
                + "{\"platform\":\"wechat\",\"account\":\"alice_wx\"}]";
        WhitelistApplication createdApp = WhitelistService.submitApplication(
                applicantId[0], "AliceApplicant", null, null, "auto",
                "a@example.com", "Join server", validSocialJson,
                true, solveTestCaptcha(), "127.0.0.2");
        assertNotNull(createdApp);
        assertTrue(createdApp.getSocialAccount().contains("discord"));
        assertTrue(createdApp.getSocialAccount().contains("wechat"));
    }

    @Test
    void testMultiPlayerBindingChatOwnershipAndBindOnApproveSecurity(@TempDir Path tempDir) throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        Files.write(serverDir.resolve("server.properties"),
                "white-list=true\nonline-mode=false\n".getBytes(StandardCharsets.UTF_8));
        Files.write(serverDir.resolve("whitelist.json"), "[]".getBytes(StandardCharsets.UTF_8));

        List<String> cmds = new ArrayList<String>();
        AdapterRegistry.initialize(createMockAdapter(serverDir, configDir, worldDir, cmds));
        MyBatisFactory.initialize(worldDir.resolve("utopia_server_panel.db"));
        Schema.initialize();
        SettingsService.reload();
        SettingsService.set("whitelist_management_enabled", "true");

        final int[] userIds = new int[2];
        final int[] limitedRoleId = new int[1];
        MyBatisFactory.doWork(session -> {
            RoleMapper rm = session.getMapper(RoleMapper.class);
            long now = System.currentTimeMillis() / 1000L;
            RoleInfo restricted = new RoleInfo();
            restricted.setName("no_wl");
            restricted.setDisplayName("NoWL");
            restricted.setDescription("Cannot apply whitelist");
            restricted.setSystemRole(false);
            restricted.setImmutable(false);
            restricted.setCreatedAt(now);
            restricted.setUpdatedAt(now);
            rm.insert(restricted);
            limitedRoleId[0] = restricted.getId();

            UserMapper um = session.getMapper(UserMapper.class);
            UserAccount admin = new UserAccount();
            admin.setUsername("admintest");
            admin.setNickname("Admin");
            admin.setEmail("admin@example.com");
            admin.setPasswordHash(PasswordUtil.hash("Pass1234!"));
            admin.setRoleId(1);
            admin.setStatus("active");
            admin.setLocale("en-US");
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            um.insert(admin);
            userIds[0] = admin.getId();

            UserAccount restrictedUser = new UserAccount();
            restrictedUser.setUsername("restricted");
            restrictedUser.setNickname("Restricted");
            restrictedUser.setEmail("res@example.com");
            restrictedUser.setPasswordHash(PasswordUtil.hash("Pass1234!"));
            restrictedUser.setRoleId(limitedRoleId[0]);
            restrictedUser.setStatus("active");
            restrictedUser.setLocale("en-US");
            restrictedUser.setCreatedAt(now);
            restrictedUser.setUpdatedAt(now);
            um.insert(restrictedUser);
            userIds[1] = restrictedUser.getId();
        });
        PermissionService.setRoleLevels(limitedRoleId[0], java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, Integer>() {{
            put("dashboard", Integer.valueOf(1));
            put("whitelist_apply", Integer.valueOf(2));
        }}));

        // 1. Even an admin cannot post public chat when unbound
        boolean adminUnboundBlocked = false;
        try {
            MessageService.sendWebPublicChat(userIds[0], true, "Should fail");
        } catch (IllegalStateException expected) {
            adminUnboundBlocked = true;
        }
        assertTrue(adminUnboundBlocked);

        // 2. Bind two players to admin account via code & test primary switching
        String code1 = BindingService.generateBindingCode("11111111-1111-3111-8111-111111111111", "CharOne");
        String code2 = BindingService.generateBindingCode("22222222-2222-3222-8222-222222222222", "CharTwo");
        assertTrue(BindingService.bindUserWithCode(userIds[0], code1).success());
        assertTrue(BindingService.bindUserWithCode(userIds[0], code2).success());

        List<PlayerBindingInfo> bindings = BindingService.listByUserId(userIds[0]);
        assertEquals(2, bindings.size());
        assertEquals("11111111-1111-3111-8111-111111111111", BindingService.findByUserId(userIds[0]).getPlayerUuid());

        assertTrue(BindingService.setPrimary(userIds[0], "22222222-2222-3222-8222-222222222222"));
        assertEquals("22222222-2222-3222-8222-222222222222", BindingService.findByUserId(userIds[0]).getPlayerUuid());

        // UserMapper search across second character returns single deduplicated user row
        List<UserAccount> filtered = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).listFiltered("CharTwo", null, null, "id", "asc", 0, 20));
        int count = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).countFiltered("CharTwo", null, null));
        assertEquals(1, filtered.size());
        assertEquals(1, count);

        // 3. Send public chat choosing non-primary character CharOne, reject foreign UUID
        InGameChatEntry chatEntry = MessageService.sendWebPublicChat(
                userIds[0], "11111111-1111-3111-8111-111111111111", "Hello from CharOne");
        assertEquals("CharOne", chatEntry.getSenderName());
        assertTrue(cmds.contains("say [Web] <CharOne> Hello from CharOne"));

        boolean foreignRejected = false;
        try {
            MessageService.sendWebPublicChat(userIds[0], "99999999-9999-3999-8999-999999999999", "Spoof");
        } catch (SecurityException expected) {
            foreignRejected = true;
        }
        assertTrue(foreignRejected);

        // Player mail targeting any bound player delivers to the owning account
        InSiteMessage mail = MessageService.sendDirectMessage(null, null, "System", "CharOne", "Ping", "Body");
        assertEquals(Integer.valueOf(userIds[0]), mail.getRecipientUserId());
        assertEquals(1, MessageService.getUnreadCount(userIds[0]));

        // Selective unbind removes only CharOne, keeps CharTwo
        assertTrue(BindingService.unbindByUserAndUuid(userIds[0], "11111111-1111-3111-8111-111111111111"));
        assertEquals(1, BindingService.listByUserId(userIds[0]).size());
        assertEquals("CharTwo", BindingService.listByUserId(userIds[0]).get(0).getPlayerName());

        // 4. Submit while permitted, then drop whitelist_apply and verify reviewApplication does not require it for bindOnApprove
        WhitelistApplication app = WhitelistService.submitApplication(
                userIds[1], "ThirdPlayer", null, null, "auto",
                "res@example.com", "Play now", "", true, solveTestCaptcha(), "127.0.0.10");
        PermissionService.setRoleLevels(limitedRoleId[0], Collections.singletonMap("dashboard", Integer.valueOf(1)));
        WhitelistApplication reviewed = WhitelistService.reviewApplication(app.getId(), userIds[0], true, "Welcome");
        assertEquals("approved", reviewed.getStatus());
        assertEquals(1, BindingService.listByUserId(userIds[1]).size());
        assertEquals("ThirdPlayer", BindingService.listByUserId(userIds[1]).get(0).getPlayerName());
    }

    @Test
    void testPasskeyRegistrationMfaLoginAndSecurityChecks(@TempDir Path tempDir) throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        AdapterRegistry.initialize(createMockAdapter(serverDir, configDir, worldDir, new ArrayList<String>()));
        MyBatisFactory.initialize(worldDir.resolve("utopia_server_panel.db"));
        Schema.initialize();
        SettingsService.reload();

        final int[] uid = new int[1];
        MyBatisFactory.doWork(session -> {
            UserMapper um = session.getMapper(UserMapper.class);
            long now = System.currentTimeMillis() / 1000L;
            UserAccount user = new UserAccount();
            user.setUsername("passkeyuser");
            user.setNickname("Passkey User");
            user.setEmail("pk@example.com");
            user.setPasswordHash(PasswordUtil.hash("Secret123!"));
            user.setRoleId(1);
            user.setStatus("active");
            user.setLocale("en-US");
            user.setTokenVersion(1);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            um.insert(user);
            uid[0] = user.getId();
        });

        // Generate real EC P-256 keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        byte[] credId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        // 1. Register Passkey via PasskeyService
        PasskeyService.ServiceResult regOpts = PasskeyService.beginRegistration(uid[0], "localhost");
        assertTrue(regOpts.success());
        String regChallengeId = String.valueOf(regOpts.data().get("challengeId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> regPubKey = (Map<String, Object>) regOpts.data().get("publicKey");
        String regChallengeB64 = String.valueOf(regPubKey.get("challenge"));

        JsonObject regCred = buildRegistrationCredential(
                "https://localhost:8443", "localhost", regChallengeB64, credId, pub);
        PasskeyService.ServiceResult regDone = PasskeyService.finishRegistration(
                uid[0], regChallengeId, "Hardware Key", regCred,
                "https://localhost:8443", null, "127.0.0.1", "JUnit");
        assertTrue(regDone.success(), regDone.message());

        List<UserPasskey> stored = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(com.utopiaxc.utopiaserverpanel.web.db.mapper.PasskeyMapper.class)
                        .listByUserId(uid[0]));
        assertEquals(1, stored.size());
        assertEquals("Hardware Key", stored.get(0).getName());

        // 2. Password login now triggers 2FA offering passkey
        AuthService.LoginRequest pwReq = new AuthService.LoginRequest(
                "passkeyuser", "Secret123!", true, null, null, null, null);
        AuthService.LoginResult loginRes = AuthService.login(pwReq, "127.0.0.1", "JUnit");
        assertTrue(loginRes.mfaRequired());
        assertNotNull(loginRes.mfaToken());
        assertTrue(loginRes.available2faMethods().contains("passkey"));
        assertFalse(loginRes.available2faMethods().contains("totp"));

        // 3. Complete MFA step with Passkey signature + signCount=1
        PasskeyService.ServiceResult mfaOpts = PasskeyService.beginMfa(loginRes.mfaToken(), "localhost");
        assertTrue(mfaOpts.success(), mfaOpts.message());
        String mfaChallengeId = String.valueOf(mfaOpts.data().get("challengeId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mfaPubKey = (Map<String, Object>) mfaOpts.data().get("publicKey");
        JsonObject mfaCred = buildAssertionCredential(
                "https://localhost:8443", "localhost",
                String.valueOf(mfaPubKey.get("challenge")), credId, kp, 1L);
        PasskeyService.ServiceResult mfaVerified = PasskeyService.finishMfa(
                mfaChallengeId, loginRes.mfaToken(), mfaCred,
                "https://localhost:8443", "127.0.0.1", "JUnit");
        assertTrue(mfaVerified.success(), mfaVerified.message());
        assertNotNull(mfaVerified.data().get("accessToken"));

        // 4. Replay signCount=1 fails; signCount=2 succeeds in passwordless Passkey login
        PasskeyService.ServiceResult replayOpts = PasskeyService.beginLogin("passkeyuser", "localhost");
        assertTrue(replayOpts.success());
        String replayCid = String.valueOf(replayOpts.data().get("challengeId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> replayPk = (Map<String, Object>) replayOpts.data().get("publicKey");
        JsonObject replayCred = buildAssertionCredential(
                "https://localhost:8443", "localhost",
                String.valueOf(replayPk.get("challenge")), credId, kp, 1L);
        PasskeyService.ServiceResult replayFail = PasskeyService.finishLogin(
                replayCid, replayCred, false, "https://localhost:8443", "127.0.0.1", "JUnit");
        assertFalse(replayFail.success());

        PasskeyService.ServiceResult loginOpts = PasskeyService.beginLogin("passkeyuser", "localhost");
        String loginCid = String.valueOf(loginOpts.data().get("challengeId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> loginPk = (Map<String, Object>) loginOpts.data().get("publicKey");
        JsonObject freshCred = buildAssertionCredential(
                "https://localhost:8443", "localhost",
                String.valueOf(loginPk.get("challenge")), credId, kp, 2L);
        PasskeyService.ServiceResult loginOk = PasskeyService.finishLogin(
                loginCid, freshCred, false, "https://localhost:8443", "127.0.0.1", "JUnit");
        assertTrue(loginOk.success(), loginOk.message());
        assertNotNull(loginOk.data().get("accessToken"));

        // 5. Force 2FA prevents deleting last Passkey
        SettingsService.set("force_2fa", "true");
        PasskeyService.ServiceResult deleteBlocked = PasskeyService.deletePasskey(uid[0], stored.get(0).getId());
        assertFalse(deleteBlocked.success());

        // 6. Token version invalidation invalidates issued MFA token
        MyBatisFactory.doWork(session -> session.getMapper(UserMapper.class)
                .bumpTokenVersion(uid[0], System.currentTimeMillis() / 1000L));
        assertNull(AuthService.validateMfaToken(loginRes.mfaToken(), "mfa"));

        // 7. PAT scope checks
        assertTrue(HttpRequestDispatcher.isPatScopeAllowed("dashboard:1", "dashboard:1", "GET", "/api/dashboard"));
        assertFalse(HttpRequestDispatcher.isPatScopeAllowed("dashboard:1", "terminal:2", "POST", "/api/console/command"));
        assertFalse(HttpRequestDispatcher.isPatScopeAllowed("profile:2", null, "POST", "/api/me/password"));
        assertTrue(HttpRequestDispatcher.isPatScopeAllowed("*", "admin:2", "POST", "/api/admin/settings"));
    }

    private static String solveTestCaptcha() {
        Map<String, Object> c = CaptchaService.createChallenge(10_000);
        String salt = String.valueOf(c.get("salt"));
        String challenge = String.valueOf(c.get("challenge"));
        for (int n = 0; n < 10_000; n++) {
            if (PasswordUtil.sha256Hex(salt + n).equals(challenge)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("algorithm", "SHA-256");
                obj.addProperty("challenge", challenge);
                obj.addProperty("number", n);
                obj.addProperty("salt", salt);
                obj.addProperty("signature", String.valueOf(c.get("signature")));
                return JsonHelper.toJson(obj);
            }
        }
        throw new IllegalStateException("Failed to solve test PoW challenge");
    }

    private static JsonObject buildRegistrationCredential(
            String origin, String rpId, String challengeB64, byte[] credId, ECPublicKey pub) throws Exception {
        JsonObject clientData = new JsonObject();
        clientData.addProperty("type", "webauthn.create");
        clientData.addProperty("challenge", challengeB64);
        clientData.addProperty("origin", origin);
        byte[] clientDataBytes = JsonHelper.toJson(clientData).getBytes(StandardCharsets.UTF_8);

        byte[] coseKey = encodeEcCoseKey(pub);
        byte[] rpIdHash = WebAuthnUtil.sha256(rpId.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream authData = new ByteArrayOutputStream();
        authData.write(rpIdHash);
        authData.write(0x41); // UP | AT
        authData.write(new byte[4]); // signCount = 0
        authData.write(new byte[16]); // AAGUID
        authData.write((credId.length >> 8) & 0xFF);
        authData.write(credId.length & 0xFF);
        authData.write(credId);
        authData.write(coseKey);

        // Minimal CBOR map { "fmt": "none", "authData": <bytes> }
        ByteArrayOutputStream att = new ByteArrayOutputStream();
        att.write(0xA2);
        writeCborText(att, "fmt");
        writeCborText(att, "none");
        writeCborText(att, "authData");
        writeCborBytes(att, authData.toByteArray());

        JsonObject response = new JsonObject();
        response.addProperty("clientDataJSON", WebAuthnUtil.encodeB64Url(clientDataBytes));
        response.addProperty("attestationObject", WebAuthnUtil.encodeB64Url(att.toByteArray()));
        JsonArray transports = new JsonArray();
        transports.add(new JsonPrimitive("internal"));
        response.add("transports", transports);

        JsonObject cred = new JsonObject();
        cred.addProperty("id", WebAuthnUtil.encodeB64Url(credId));
        cred.addProperty("rawId", WebAuthnUtil.encodeB64Url(credId));
        cred.addProperty("type", "public-key");
        cred.add("response", response);
        return cred;
    }

    private static JsonObject buildAssertionCredential(
            String origin, String rpId, String challengeB64, byte[] credId, KeyPair kp, long signCount) throws Exception {
        JsonObject clientData = new JsonObject();
        clientData.addProperty("type", "webauthn.get");
        clientData.addProperty("challenge", challengeB64);
        clientData.addProperty("origin", origin);
        byte[] clientDataBytes = JsonHelper.toJson(clientData).getBytes(StandardCharsets.UTF_8);
        byte[] clientDataHash = WebAuthnUtil.sha256(clientDataBytes);

        byte[] rpIdHash = WebAuthnUtil.sha256(rpId.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream authData = new ByteArrayOutputStream();
        authData.write(rpIdHash);
        authData.write(0x01); // UP
        authData.write(ByteBuffer.allocate(4).putInt((int) signCount).array());
        byte[] authDataBytes = authData.toByteArray();

        byte[] signedInput = new byte[authDataBytes.length + clientDataHash.length];
        System.arraycopy(authDataBytes, 0, signedInput, 0, authDataBytes.length);
        System.arraycopy(clientDataHash, 0, signedInput, authDataBytes.length, clientDataHash.length);

        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(kp.getPrivate());
        sig.update(signedInput);
        byte[] sigBytes = sig.sign();

        JsonObject response = new JsonObject();
        response.addProperty("clientDataJSON", WebAuthnUtil.encodeB64Url(clientDataBytes));
        response.addProperty("authenticatorData", WebAuthnUtil.encodeB64Url(authDataBytes));
        response.addProperty("signature", WebAuthnUtil.encodeB64Url(sigBytes));

        JsonObject cred = new JsonObject();
        cred.addProperty("id", WebAuthnUtil.encodeB64Url(credId));
        cred.addProperty("rawId", WebAuthnUtil.encodeB64Url(credId));
        cred.addProperty("type", "public-key");
        cred.add("response", response);
        return cred;
    }

    private static byte[] encodeEcCoseKey(ECPublicKey pub) throws Exception {
        ECPoint pt = pub.getW();
        byte[] x = toFixed32(pt.getAffineX());
        byte[] y = toFixed32(pt.getAffineY());
        // CBOR map(5): {1:2, 3:-7, -1:1, -2:x, -3:y}
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA5);
        out.write(0x01); out.write(0x02);        // 1 (kty) -> 2 (EC2)
        out.write(0x03); out.write(0x26);        // 3 (alg) -> -7 (ES256)
        out.write(0x20); out.write(0x01);        // -1 (crv) -> 1 (P-256)
        out.write(0x21); writeCborBytes(out, x); // -2 -> x
        out.write(0x22); writeCborBytes(out, y); // -3 -> y
        return out.toByteArray();
    }

    private static byte[] toFixed32(BigInteger val) {
        byte[] raw = val.toByteArray();
        byte[] out = new byte[32];
        if (raw.length == 32) return raw;
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static void writeCborText(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeCborLength(out, 0x60, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeCborBytes(ByteArrayOutputStream out, byte[] bytes) {
        writeCborLength(out, 0x40, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeCborLength(ByteArrayOutputStream out, int majorMask, int len) {
        if (len < 24) {
            out.write(majorMask | len);
        } else if (len < 256) {
            out.write(majorMask | 24);
            out.write(len);
        } else {
            out.write(majorMask | 25);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
    }

    private static GameAdapter createMockAdapter(Path serverDir, Path configDir, Path worldDir, List<String> cmds) {
        return (GameAdapter) Proxy.newProxyInstance(
                GameAdapter.class.getClassLoader(),
                new Class<?>[]{GameAdapter.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getServerDirectory".equals(name)) return serverDir;
                    if ("getConfigDirectory".equals(name)) return configDir;
                    if ("getWorldDirectory".equals(name)) return worldDir;
                    if ("getWorldName".equals(name)) return "world";
                    if ("getModVersion".equals(name)) return "2.0.0-test";
                    if ("getMinecraftVersion".equals(name)) return "1.21.1";
                    if ("getLoaderName".equals(name)) return "neoforge";
                    if ("executeCommand".equals(name) && args != null && args.length == 1) {
                        cmds.add(String.valueOf(args[0]));
                        return true;
                    }
                    if ("broadcastPublicChat".equals(name) && args != null && args.length == 2) {
                        cmds.add("say [Web] <" + args[0] + "> " + args[1]);
                        return null;
                    }
                    if ("sendDirectPlayerMessage".equals(name) && args != null && args.length == 2) {
                        cmds.add("msg " + args[0] + " " + args[1]);
                        return null;
                    }
                    if ("getServerProperties".equals(name)) {
                        ServerPropertiesSnapshot snap = new ServerPropertiesSnapshot();
                        snap.setWhiteList(true);
                        snap.setOnlineMode(false);
                        return snap;
                    }
                    if ("getServerStatus".equals(name)) {
                        return new ServerStatusSnapshot(0, 20, "Test Server", "1.21.1", "world", 20.0, 10.0);
                    }
                    if ("getLoadedMods".equals(name)
                            || "getMapDimensions".equals(name)
                            || "listMapMarkers".equals(name)
                            || "getRealtimePlayerPositions".equals(name)
                            || "getOnlinePlayers".equals(name)
                            || "getCommandSuggestions".equals(name)) {
                        return Collections.emptyList();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == double.class) return 0.0;
                    return null;
                }
        );
    }
}
