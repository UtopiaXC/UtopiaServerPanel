package com.utopiaxc.utopiaserverpanel.mail;

import java.util.Locale;

/**
 * Generates responsive HTML & plain-text email bodies for account verification,
 * password reset, and 2FA reset. Zero emojis, emerald/slate aesthetic.
 */
public final class EmailTemplateRenderer {

    private EmailTemplateRenderer() {}

    public static final class RenderedEmail {
        private final String subject;
        private final String html;
        private final String text;

        public RenderedEmail(String subject, String html, String text) {
            this.subject = subject;
            this.html = html;
            this.text = text;
        }

        public String getSubject() { return subject; }
        public String getHtml() { return html; }
        public String getText() { return text; }
    }

    public static RenderedEmail renderActionEmail(
            String locale,
            String siteName,
            String type, // "activation", "password", "2fa"
            String username,
            String actionUrl,
            String token
    ) {
        boolean isEn = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en");
        String safeSite = escapeHtml(siteName != null && !siteName.trim().isEmpty() ? siteName : "Utopia Server Panel");
        String safeUser = escapeHtml(username != null ? username : "");
        String cleanType = type != null ? type : "password";

        String title;
        String desc;
        String btnText;
        String expireText;

        if (isEn) {
            switch (cleanType) {
                case "activation":
                    title = "Activate Your Account";
                    desc = "Hello " + safeUser + ", click below to verify your email and activate your account.";
                    btnText = "Activate Account";
                    expireText = "Valid for 24 hours. Ignore this email if you did not register.";
                    break;
                case "2fa":
                    title = "Reset Two-Factor Authentication";
                    desc = "We received a request to remove two-factor authentication for account " + safeUser + ".";
                    btnText = "Remove Two-Factor Auth";
                    expireText = "Valid for 30 minutes. Ignore this email if you did not request this.";
                    break;
                default:
                    title = "Reset Your Password";
                    desc = "We received a password reset request for account " + safeUser + ".";
                    btnText = "Reset Password";
                    expireText = "Valid for 30 minutes. Ignore this email if you did not request this.";
                    break;
            }
        } else {
            switch (cleanType) {
                case "activation":
                    title = "激活账号";
                    desc = safeUser + "，点击下方按钮验证邮箱并激活账号。";
                    btnText = "立即激活";
                    expireText = "链接 24 小时内有效，如非本人操作请忽略。";
                    break;
                case "2fa":
                    title = "重置两步验证";
                    desc = "您正在申请移除账号 " + safeUser + " 的两步验证保护。";
                    btnText = "移除两步验证";
                    expireText = "链接 30 分钟内有效，如非本人操作请忽略。";
                    break;
                default:
                    title = "重置登录密码";
                    desc = "您正在重置账号 " + safeUser + " 的登录密码。";
                    btnText = "重置密码";
                    expireText = "链接 30 分钟内有效，如非本人操作请忽略。";
                    break;
            }
        }

        String subject = "[" + safeSite + "] " + title;
        String safeUrl = escapeHtml(actionUrl);
        String safeToken = escapeHtml(token);

        String html = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>\n"
                + "<body style=\"margin:0;padding:0;background:#101014;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#e5e7eb;\">\n"
                + "  <div style=\"max-width:560px;margin:32px auto;background:#18181c;border:1px solid #2b2b33;border-radius:10px;overflow:hidden;\">\n"
                + "    <div style=\"padding:24px 28px;border-bottom:1px solid #26262e;\">\n"
                + "      <span style=\"font-size:17px;font-weight:700;color:#63e2b7;\">" + safeSite + "</span>\n"
                + "    </div>\n"
                + "    <div style=\"padding:28px;\">\n"
                + "      <h2 style=\"margin:0 0 12px;font-size:20px;color:#ffffff;\">" + escapeHtml(title) + "</h2>\n"
                + "      <p style=\"margin:0 0 22px;font-size:14px;line-height:1.6;color:#9ca3af;\">" + desc + "</p>\n"
                + "      <div style=\"margin:24px 0;\">\n"
                + "        <a href=\"" + safeUrl + "\" style=\"display:inline-block;padding:11px 22px;background:#63e2b7;color:#0f172a;text-decoration:none;border-radius:6px;font-weight:600;font-size:14px;\">" + escapeHtml(btnText) + "</a>\n"
                + "      </div>\n"
                + "      <p style=\"margin:0 0 8px;font-size:12px;color:#9ca3af;\">Token: <code style=\"background:#23232a;padding:2px 6px;border-radius:4px;color:#e5e7eb;\">" + safeToken + "</code></p>\n"
                + "      <p style=\"margin:12px 0 0;font-size:12px;color:#6b7280;\">" + escapeHtml(expireText) + "</p>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "</html>\n";

        String text = siteName + " - " + title + "\n\n"
                + desc.replaceAll("<[^>]+>", "") + "\n\n"
                + actionUrl + "\n\n"
                + "Token: " + token + "\n"
                + expireText;

        return new RenderedEmail(subject, html, text);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

