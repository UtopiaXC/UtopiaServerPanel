package com.utopiaxc.utopiaserverpanel.command;

import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.InGameRecoveryService;
import com.utopiaxc.utopiaserverpanel.web.service.SetupService;

/**
 * Pure Java 8 command business handler for all {@code /usp} subcommands:
 * <ul>
 *     <li>{@code /usp bind}</li>
 *     <li>{@code /usp unbind}</li>
 *     <li>{@code /usp setup token}</li>
 *     <li>{@code /usp recover password}</li>
 *     <li>{@code /usp recover 2fa}</li>
 * </ul>
 */
public final class UspCommandService {

    public CommandOutcome executeBind(CommandCaller caller) {
        if (caller == null || !caller.isPlayer()) {
            return CommandOutcome.fail("This command can only be executed by a player.");
        }
        boolean isZh = caller.isChinese();
        String code = BindingService.generateBindingCode(caller.getPlayerUuid(), caller.getPlayerName());
        if (code == null) {
            return CommandOutcome.fail(isZh ? "生成绑定码失败，请重试。" : "Failed to generate binding code.");
        }

        RichText rt = new RichText()
                .append(isZh ? "绑定码: " : "Binding code: ", RichText.Color.GREEN)
                .appendCopyable(code);
        appendClickHint(rt, isZh);
        rt.append("\n")
                .append(isZh ? "请在 Web 面板中提交此绑定码，5 分钟内有效。" : "Submit this code in the web panel within 5 minutes.",
                        RichText.Color.GRAY);
        return CommandOutcome.ok(rt);
    }

    public CommandOutcome executeUnbind(CommandCaller caller) {
        if (caller == null || !caller.isPlayer()) {
            return CommandOutcome.fail("This command can only be executed by a player.");
        }
        boolean isZh = caller.isChinese();
        boolean success = BindingService.unbindByPlayer(caller.getPlayerUuid());
        if (success) {
            return CommandOutcome.ok(new RichText().append(
                    isZh ? "已解除与面板账号的绑定。" : "Your player account has been unbound from the panel.",
                    RichText.Color.GREEN));
        }
        return CommandOutcome.fail(isZh ? "当前未绑定任何面板账号。" : "You are not bound to any panel account.");
    }

    public CommandOutcome executeSetupToken(CommandCaller caller) {
        boolean isZh = caller != null && caller.isChinese();
        if (caller != null && !caller.hasSetupPermission()) {
            return CommandOutcome.fail(isZh ? "您没有执行此指令的权限。" : "You do not have permission to view the setup token.");
        }
        if (!SetupService.isSetupRequired()) {
            return CommandOutcome.fail(new RichText().append(
                    isZh ? "Web 面板已完成初始化配置，初始化令牌已失效。" : "The web panel is already initialized; the setup token is no longer active.",
                    RichText.Color.YELLOW));
        }

        String token = SetupService.ensureSetupToken();
        if (token == null || token.trim().isEmpty()) {
            return CommandOutcome.fail(isZh ? "无法读取初始化令牌。" : "Unable to obtain setup token.");
        }

        RichText rt = new RichText()
                .append(isZh ? "初始化令牌: " : "Setup Token: ", RichText.Color.GREEN)
                .appendCopyable(token);
        appendClickHint(rt, isZh);
        rt.append("\n")
                .append(isZh ? "请在浏览器打开面板初始化向导并输入此令牌。" : "Enter this token in the web setup wizard.",
                        RichText.Color.GRAY);
        return CommandOutcome.ok(rt);
    }

    public CommandOutcome executeRecover(CommandCaller caller, String type) {
        if (caller == null || !caller.isPlayer()) {
            return CommandOutcome.fail("This command can only be executed by a player.");
        }
        boolean isZh = caller.isChinese();
        InGameRecoveryService.InGameRecoveryResult result = InGameRecoveryService.createRecoveryTokenForPlayer(
                caller.getPlayerUuid(),
                caller.getPlayerName(),
                type
        );

        if (!result.isSuccess()) {
            if ("TWO_FA_NOT_ENABLED".equals(result.getErrorCode())) {
                return CommandOutcome.fail(isZh
                        ? "您绑定的面板账号未开启两步验证。"
                        : "Two-factor authentication is not enabled on your account.");
            }
            return CommandOutcome.fail(isZh
                    ? "您尚未绑定面板账号，请先使用 /usp bind 绑定。"
                    : "You are not bound to any panel account.");
        }

        String label = "2fa".equals(type)
                ? (isZh ? "两步验证重置令牌: " : "2FA reset token: ")
                : (isZh ? "密码重置令牌: " : "Password reset token: ");
        String tip = "2fa".equals(type)
                ? (isZh ? "请在登录页点击两步验证找回并填入此令牌，15 分钟内有效。" : "Use this token in the login page 2FA recovery dialog within 15 minutes.")
                : (isZh ? "请在登录页点击忘记密码并填入此令牌，15 分钟内有效。" : "Use this token in the login page password reset dialog within 15 minutes.");

        RichText rt = new RichText()
                .append(label, RichText.Color.GREEN)
                .appendCopyable(result.getToken());
        appendClickHint(rt, isZh);
        rt.append("\n")
                .append(tip, RichText.Color.GRAY);
        return CommandOutcome.ok(rt);
    }

    private static void appendClickHint(RichText rt, boolean isZh) {
        rt.append(" [", RichText.Color.GRAY)
                .append(isZh ? "点击复制" : "Click to copy", RichText.Color.AQUA)
                .append("]", RichText.Color.GRAY);
    }
}

