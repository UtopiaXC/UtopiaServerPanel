package com.utopiaxc.utopiaserverpanel.platform.shared;

import com.mojang.brigadier.CommandDispatcher;
import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.command.CommandCaller;
import com.utopiaxc.utopiaserverpanel.command.CommandOutcome;
import com.utopiaxc.utopiaserverpanel.command.RichText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
//? if mc >= 1.19 {
import net.minecraft.network.chat.Component;
//?} else {
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
//?}
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared Brigadier {@code /usp} command tree covering modern Forge, NeoForge, and Fabric
 * from 1.16.5 through 26.3+.
 */
public final class ModernCommandBridge {

    private ModernCommandBridge() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("usp")
                        .then(Commands.literal("bind")
                                .executes(ctx -> dispatch(ctx.getSource(),
                                        PanelEngine.commands().executeBind(callerFrom(ctx.getSource())))))
                        .then(Commands.literal("unbind")
                                .executes(ctx -> dispatch(ctx.getSource(),
                                        PanelEngine.commands().executeUnbind(callerFrom(ctx.getSource())))))
                        .then(Commands.literal("setup")
                                .requires(ModernCommandBridge::hasAdminPermission)
                                .then(Commands.literal("token")
                                        .executes(ctx -> dispatch(ctx.getSource(),
                                                PanelEngine.commands().executeSetupToken(callerFrom(ctx.getSource()))))))
                        .then(Commands.literal("recover")
                                .then(Commands.literal("password")
                                        .executes(ctx -> dispatch(ctx.getSource(),
                                                PanelEngine.commands().executeRecover(callerFrom(ctx.getSource()), "password"))))
                                .then(Commands.literal("2fa")
                                        .executes(ctx -> dispatch(ctx.getSource(),
                                                PanelEngine.commands().executeRecover(callerFrom(ctx.getSource()), "2fa")))))
        );
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        //? if mc >= 1.21.11 {
        return Commands.LEVEL_ADMINS.check(source.permissions());
        //?} else {
        return source.hasPermission(3);
        //?}
    }

    private static CommandCaller callerFrom(CommandSourceStack source) {
        boolean hasOp = hasAdminPermission(source);
        if (source.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) source.getEntity();
            //? if mc >= 1.20.2 {
            String lang = player.clientInformation().language();
            //?} else if loader == 'forge' {
            String lang = player.getLanguage();
            //?} else {
            String lang = "en_us";
            //?}
            return CommandCaller.player(
                    player.getStringUUID(),
                    player.getName().getString(),
                    lang,
                    hasOp
            );
        }
        return CommandCaller.console(hasOp);
    }

    private static int dispatch(CommandSourceStack source, CommandOutcome outcome) {
        final MutableComponent component = renderRichText(outcome.getMessage());
        if (outcome.isSuccess()) {
            //? if mc >= 1.20 {
            source.sendSuccess(() -> component, false);
            //?} else {
            source.sendSuccess(component, false);
            //?}
            return 1;
        }
        source.sendFailure(component);
        return 0;
    }

    private static MutableComponent renderRichText(RichText richText) {
        //? if mc >= 1.19 {
        MutableComponent root = Component.empty();
        //?} else {
        MutableComponent root = new TextComponent("");
        //?}
        if (richText == null) {
            return root;
        }
        for (RichText.Segment seg : richText.getSegments()) {
            //? if mc >= 1.19 {
            MutableComponent part = Component.literal(seg.getText());
            //?} else {
            MutableComponent part = new TextComponent(seg.getText());
            //?}
            final ChatFormatting color = mapColor(seg.getColor());
            part.withStyle(style -> {
                if (color != null) {
                    style = style.withColor(color);
                }
                if (seg.isBold()) {
                    style = style.withBold(true);
                }
                if (seg.isUnderlined()) {
                    style = style.withUnderlined(true);
                }
                if (seg.getCopyValue() != null) {
                    //? if mc >= 1.19 {
                    MutableComponent hover = Component.translatable("chat.copy.click");
                    //?} else {
                    MutableComponent hover = new TranslatableComponent("chat.copy.click");
                    //?}
                    //? if mc >= 1.21.5 {
                    style = style
                            .withClickEvent(new ClickEvent.CopyToClipboard(seg.getCopyValue()))
                            .withHoverEvent(new HoverEvent.ShowText(hover));
                    //?} else {
                    style = style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, seg.getCopyValue()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
                    //?}
                }
                return style;
            });
            root.append(part);
        }
        return root;
    }

    private static ChatFormatting mapColor(RichText.Color color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case GREEN: return ChatFormatting.GREEN;
            case YELLOW: return ChatFormatting.YELLOW;
            case RED: return ChatFormatting.RED;
            case GRAY: return ChatFormatting.GRAY;
            case AQUA: return ChatFormatting.AQUA;
            case DEFAULT:
            default:
                return null;
        }
    }
}

