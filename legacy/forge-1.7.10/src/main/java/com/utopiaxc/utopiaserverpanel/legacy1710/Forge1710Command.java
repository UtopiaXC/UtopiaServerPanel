package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.command.CommandCaller;
import com.utopiaxc.utopiaserverpanel.command.CommandOutcome;
import com.utopiaxc.utopiaserverpanel.command.RichText;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Legacy Forge 1.7.10 {@code /usp} command handler.
 */
public final class Forge1710Command extends CommandBase {

    @Override
    public String getCommandName() {
        return "usp";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/usp <bind|unbind|setup token|recover <password|2fa>>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        CommandCaller caller = callerFrom(sender);
        if (args == null || args.length == 0) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        CommandOutcome outcome;
        if ("bind".equals(sub)) {
            outcome = PanelEngine.commands().executeBind(caller);
        } else if ("unbind".equals(sub)) {
            outcome = PanelEngine.commands().executeUnbind(caller);
        } else if ("setup".equals(sub) && args.length >= 2 && "token".equalsIgnoreCase(args[1])) {
            outcome = PanelEngine.commands().executeSetupToken(caller);
        } else if ("recover".equals(sub) && args.length >= 2) {
            outcome = PanelEngine.commands().executeRecover(caller, args[1].toLowerCase(Locale.ROOT));
        } else {
            outcome = CommandOutcome.fail(getCommandUsage(sender));
        }

        sender.addChatMessage(renderRichText(outcome.getMessage()));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "bind", "unbind", "setup", "recover");
        }
        if (args.length == 2 && "setup".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "token");
        }
        if (args.length == 2 && "recover".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "password", "2fa");
        }
        return Arrays.asList();
    }

    private static CommandCaller callerFrom(ICommandSender sender) {
        boolean isOp = sender.canCommandSenderUseCommand(3, "usp");
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            return CommandCaller.player(
                    player.getUniqueID().toString(),
                    player.getCommandSenderName(),
                    "en_us",
                    isOp
            );
        }
        return CommandCaller.console(isOp);
    }

    private static IChatComponent renderRichText(RichText richText) {
        ChatComponentText root = new ChatComponentText("");
        if (richText == null) {
            return root;
        }
        for (RichText.Segment seg : richText.getSegments()) {
            ChatComponentText part = new ChatComponentText(seg.getText());
            ChatStyle style = new ChatStyle();
            EnumChatFormatting color = mapColor(seg.getColor());
            if (color != null) {
                style.setColor(color);
            }
            if (seg.isBold()) {
                style.setBold(Boolean.TRUE);
            }
            if (seg.isUnderlined()) {
                style.setUnderlined(Boolean.TRUE);
            }
            if (seg.getCopyValue() != null) {
                // MC 1.7.10 predates COPY_TO_CLIPBOARD; SUGGEST_COMMAND lets players copy cleanly from chat input
                style.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, seg.getCopyValue()));
                style.setChatHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText("Click to insert into chat box for copying")
                ));
            }
            part.setChatStyle(style);
            root.appendSibling(part);
        }
        return root;
    }

    private static EnumChatFormatting mapColor(RichText.Color color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case GREEN: return EnumChatFormatting.GREEN;
            case YELLOW: return EnumChatFormatting.YELLOW;
            case RED: return EnumChatFormatting.RED;
            case GRAY: return EnumChatFormatting.GRAY;
            case AQUA: return EnumChatFormatting.AQUA;
            default: return null;
        }
    }
}

