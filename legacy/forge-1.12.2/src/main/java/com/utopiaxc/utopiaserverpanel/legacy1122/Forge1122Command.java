package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.PanelEngine;
import com.utopiaxc.utopiaserverpanel.command.CommandCaller;
import com.utopiaxc.utopiaserverpanel.command.CommandOutcome;
import com.utopiaxc.utopiaserverpanel.command.RichText;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Legacy Forge 1.12.2 {@code /usp} command handler.
 */
public final class Forge1122Command extends CommandBase {

    @Override
    public String getName() {
        return "usp";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/usp <bind|unbind|setup token|recover <password|2fa>>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        CommandCaller caller = callerFrom(sender);
        if (args == null || args.length == 0) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
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
            outcome = CommandOutcome.fail(getUsage(sender));
        }

        sender.sendMessage(renderRichText(outcome.getMessage()));
    }

    @Override
    public List<String> getTabCompletions(
            MinecraftServer server,
            ICommandSender sender,
            String[] args,
            BlockPos targetPos
    ) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "bind", "unbind", "setup", "recover");
        }
        if (args.length == 2 && "setup".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "token");
        }
        if (args.length == 2 && "recover".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "password", "2fa");
        }
        return Collections.emptyList();
    }

    private static String readLanguage(EntityPlayerMP player) {
        for (String fieldName : new String[]{"field_71148_cg", "language"}) {
            try {
                Field f = EntityPlayerMP.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(player);
                if (val instanceof String && !((String) val).isEmpty()) {
                    return (String) val;
                }
            } catch (Exception ignored) {
            }
        }
        return "en_us";
    }

    private static CommandCaller callerFrom(ICommandSender sender) {
        boolean isOp = sender.canUseCommand(3, "usp");
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            return CommandCaller.player(
                    player.getUniqueID().toString(),
                    player.getName(),
                    readLanguage(player),
                    isOp
            );
        }
        return CommandCaller.console(isOp);
    }

    private static ITextComponent renderRichText(RichText richText) {
        TextComponentString root = new TextComponentString("");
        if (richText == null) {
            return root;
        }
        for (RichText.Segment seg : richText.getSegments()) {
            TextComponentString part = new TextComponentString(seg.getText());
            Style style = new Style();
            TextFormatting color = mapColor(seg.getColor());
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
                // MC 1.12.2 predates COPY_TO_CLIPBOARD; SUGGEST_COMMAND lets players copy cleanly from chat input
                style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, seg.getCopyValue()));
                style.setHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new TextComponentString("Click to insert into chat box for copying")
                ));
            }
            part.setStyle(style);
            root.appendSibling(part);
        }
        return root;
    }

    private static TextFormatting mapColor(RichText.Color color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case GREEN: return TextFormatting.GREEN;
            case YELLOW: return TextFormatting.YELLOW;
            case RED: return TextFormatting.RED;
            case GRAY: return TextFormatting.GRAY;
            case AQUA: return TextFormatting.AQUA;
            default: return null;
        }
    }
}
