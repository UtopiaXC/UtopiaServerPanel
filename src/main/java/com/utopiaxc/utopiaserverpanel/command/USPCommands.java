package com.utopiaxc.utopiaserverpanel.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the /usp (UtopiaServerPanel) commands.
 * <p>
 * Commands:
 * <ul>
 *   <li>/usp bind — Generate a binding code to link your player to a panel account</li>
 *   <li>/usp unbind — Unlink your player from the panel account</li>
 * </ul>
 * </p>
 */
public class USPCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("usp")
                        .then(Commands.literal("bind")
                                .executes(this::executeBind))
                        .then(Commands.literal("unbind")
                                .executes(this::executeUnbind))
        );
    }

    private int executeBind(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player."));
            return 0;
        }

        String uuid = player.getStringUUID();
        String name = player.getName().getString();

        String code = BindingService.generateBindingCode(uuid, name);
        if (code == null) {
            source.sendFailure(Component.literal("Failed to generate binding code. Please try again."));
            return 0;
        }

        String lang = player.clientInformation().language().toLowerCase();
        boolean isZh = lang.contains("zh");

        MutableComponent clickToCopy = Component.literal(isZh ? "点击复制" : "Click to copy")
                .withStyle(ChatFormatting.GRAY);

        MutableComponent codeComp = Component.literal(code)
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                );

        MutableComponent msg = Component.literal(isZh ? "您的绑定码：" : "Your binding code: ")
                .withStyle(ChatFormatting.GREEN)
                .append(codeComp)
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(clickToCopy)
                .append(Component.literal(")\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(isZh ? "请在Web面板中使用此绑定码以绑定您的游戏账号。" : "Use this code in the web panel to bind your player account.").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.literal(isZh ? "该绑定码将在5分钟后过期。" : "This code expires in 5 minutes.").withStyle(ChatFormatting.GRAY));

        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private int executeUnbind(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player."));
            return 0;
        }
        
        String lang = player.clientInformation().language().toLowerCase();
        boolean isZh = lang.contains("zh");

        String uuid = player.getStringUUID();
        boolean success = BindingService.unbindByPlayer(uuid);
        if (success) {
            source.sendSuccess(() -> Component.literal(isZh ? "您已成功解除与面板账号的绑定。\n" : "You have been unbound from your panel account.\n")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(isZh ? "您需要重新绑定后才能再次登录面板。" : "You will need to re-bind before you can log in again.").withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendFailure(Component.literal(isZh ? "您当前未绑定任何面板账号。" : "You are not currently bound to any panel account."));
        }
        return success ? 1 : 0;
    }
}
