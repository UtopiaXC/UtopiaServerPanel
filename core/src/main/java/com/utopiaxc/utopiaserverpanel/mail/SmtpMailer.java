package com.utopiaxc.utopiaserverpanel.mail;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Pure Java 8 SMTP client supporting SSL/TLS (465), STARTTLS (587/25), and plain SMTP,
 * with AUTH PLAIN / AUTH LOGIN and RFC 2045 Base64 UTF-8 multipart MIME emails.
 */
public final class SmtpMailer {
    private static final int TIMEOUT_MS = 12_000;

    private SmtpMailer() {}

    public static final class SmtpConfig {
        private final String host;
        private final int port;
        private final String encryption; // "ssl", "starttls", "none"
        private final String username;
        private final String password;
        private final String fromAddress;
        private final String fromName;

        public SmtpConfig(String host, int port, String encryption, String username, String password,
                          String fromAddress, String fromName) {
            this.host = host;
            this.port = port;
            this.encryption = encryption;
            this.username = username;
            this.password = password;
            this.fromAddress = fromAddress;
            this.fromName = fromName;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getEncryption() { return encryption; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getFromAddress() { return fromAddress; }
        public String getFromName() { return fromName; }
    }

    public static void testConnection(SmtpConfig cfg) throws IOException {
        try (SmtpConnection conn = connectAndAuthenticate(cfg)) {
            conn.sendCommand("NOOP", 250);
            conn.sendQuit();
        }
    }

    public static void sendHtmlEmail(SmtpConfig cfg, String to, String subject, String htmlBody, String textBody) throws IOException {
        if (to == null || to.trim().isEmpty()) {
            throw new IOException("Recipient email is empty");
        }
        String fromAddr = (cfg.getFromAddress() != null && !cfg.getFromAddress().trim().isEmpty())
                ? cfg.getFromAddress().trim()
                : (cfg.getUsername() != null ? cfg.getUsername().trim() : "");
        String boundary = "----USPPart_" + UUID.randomUUID().toString().replace("-", "");

        try (SmtpConnection conn = connectAndAuthenticate(cfg)) {
            conn.sendCommand("MAIL FROM:<" + sanitizeHeader(fromAddr) + ">", 250);
            conn.sendCommand("RCPT TO:<" + sanitizeHeader(to.trim()) + ">", 250, 251);
            conn.sendCommand("DATA", 354);

            StringBuilder msg = new StringBuilder();
            String safeFromName = sanitizeHeader(cfg.getFromName() != null && !cfg.getFromName().trim().isEmpty()
                    ? cfg.getFromName() : "Utopia Server Panel");
            msg.append("From: ").append(encodeMimeWord(safeFromName)).append(" <").append(sanitizeHeader(fromAddr)).append(">\r\n");
            msg.append("To: <").append(sanitizeHeader(to.trim())).append(">\r\n");
            msg.append("Subject: ").append(encodeMimeWord(sanitizeHeader(subject))).append("\r\n");
            msg.append("MIME-Version: 1.0\r\n");
            msg.append("Content-Type: multipart/alternative; boundary=\"").append(boundary).append("\"\r\n");
            msg.append("\r\n");

            msg.append("--").append(boundary).append("\r\n");
            msg.append("Content-Type: text/plain; charset=UTF-8\r\n");
            msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
            msg.append(encodeMimeBase64(textBody != null ? textBody : "")).append("\r\n");

            msg.append("--").append(boundary).append("\r\n");
            msg.append("Content-Type: text/html; charset=UTF-8\r\n");
            msg.append("Content-Transfer-Encoding: base64\r\n\r\n");
            msg.append(encodeMimeBase64(htmlBody != null ? htmlBody : "")).append("\r\n");

            msg.append("--").append(boundary).append("--\r\n");

            conn.sendDataBody(msg.toString());
            conn.sendQuit();
        }
    }

    private static SmtpConnection connectAndAuthenticate(SmtpConfig cfg) throws IOException {
        if (cfg.getHost() == null || cfg.getHost().trim().isEmpty()) {
            throw new IOException("SMTP host is required");
        }
        String enc = cfg.getEncryption() != null ? cfg.getEncryption().trim().toLowerCase(Locale.ROOT) : "ssl";
        int port = cfg.getPort() > 0 ? cfg.getPort() : ("ssl".equals(enc) ? 465 : 587);

        Socket socket;
        if ("ssl".equals(enc) || "tls".equals(enc)) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket();
            sslSocket.connect(new InetSocketAddress(cfg.getHost().trim(), port), TIMEOUT_MS);
            sslSocket.setSoTimeout(TIMEOUT_MS);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(cfg.getHost().trim(), port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
        }

        SmtpConnection conn = new SmtpConnection(socket);
        boolean success = false;
        try {
            conn.expectCode(220);
            String ehloResponse = conn.sendCommand("EHLO localhost", 250);

            if ("starttls".equals(enc)) {
                conn.sendCommand("STARTTLS", 220);
                conn.upgradeToTls(cfg.getHost().trim(), port);
                ehloResponse = conn.sendCommand("EHLO localhost", 250);
            }

            if (cfg.getUsername() != null && !cfg.getUsername().trim().isEmpty()) {
                String password = cfg.getPassword() != null ? cfg.getPassword() : "";
                if (ehloResponse.toUpperCase(Locale.ROOT).contains("PLAIN")) {
                    String authStr = "\0" + cfg.getUsername().trim() + "\0" + password;
                    String b64 = Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));
                    conn.sendCommand("AUTH PLAIN " + b64, 235);
                } else {
                    conn.sendCommand("AUTH LOGIN", 334);
                    conn.sendCommand(Base64.getEncoder().encodeToString(cfg.getUsername().trim().getBytes(StandardCharsets.UTF_8)), 334);
                    conn.sendCommand(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
                }
            }
            success = true;
            return conn;
        } finally {
            if (!success) {
                conn.close();
            }
        }
    }

    private static String encodeMimeWord(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(bytes) + "?=";
    }

    private static String encodeMimeBase64(String content) {
        return Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitizeHeader(String s) {
        return s == null ? "" : s.replace("\r", "").replace("\n", "");
    }

    private static final class SmtpConnection implements Closeable {
        private Socket socket;
        private BufferedReader reader;
        private BufferedWriter writer;

        SmtpConnection(Socket socket) throws IOException {
            this.socket = socket;
            initStreams();
        }

        private void initStreams() throws IOException {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void upgradeToTls(String host, int port) throws IOException {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket(socket, host, port, true);
            ssl.setSoTimeout(TIMEOUT_MS);
            ssl.startHandshake();
            this.socket = ssl;
            initStreams();
        }

        String sendCommand(String cmd, int... expectedCodes) throws IOException {
            writer.write(cmd);
            writer.write("\r\n");
            writer.flush();
            return expectCode(expectedCodes);
        }

        void sendDataBody(String rawBody) throws IOException {
            BufferedReader bodyReader = new BufferedReader(new StringReader(rawBody));
            String line;
            while ((line = bodyReader.readLine()) != null) {
                if (line.startsWith(".")) {
                    writer.write('.');
                }
                writer.write(line);
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();
            expectCode(250);
        }

        void sendQuit() {
            try {
                writer.write("QUIT\r\n");
                writer.flush();
            } catch (IOException ignored) {}
        }

        String expectCode(int... expected) throws IOException {
            StringBuilder full = new StringBuilder();
            String lastLine = null;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("SMTP connection closed unexpectedly");
                }
                full.append(line).append('\n');
                lastLine = line;
                if (line.length() < 4 || line.charAt(3) != '-') {
                    break;
                }
            }
            if (lastLine.length() < 3) {
                throw new IOException("Invalid SMTP reply: " + lastLine);
            }
            int code;
            try {
                code = Integer.parseInt(lastLine.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid SMTP reply code: " + lastLine);
            }
            for (int ok : expected) {
                if (code == ok) return full.toString();
            }
            throw new IOException("SMTP error " + code + ": " + lastLine);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}

