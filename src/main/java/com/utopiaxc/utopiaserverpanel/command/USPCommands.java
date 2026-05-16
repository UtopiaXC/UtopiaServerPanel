package com.utopiaxc.utopiaserverpanel.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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

        source.sendSuccess(() -> Component.literal(
                "§aYour binding code: §e§l" + code + "§a\n" +
                        "§7Use this code in the web panel to bind your player account.\n" +
                        "§7This code expires in 5 minutes."
        ), false);
        return 1;
    }

    private int executeUnbind(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player."));
            return 0;
        }

        String uuid = player.getStringUUID();

        boolean success = BindingService.unbindByPlayer(uuid);
        if (success) {
            source.sendSuccess(() -> Component.literal(
                    "§aYou have been unbound from your panel account.\n" +
                            "§7You will need to re-bind before you can log in again."
            ), false);
        } else {
            source.sendFailure(Component.literal(
                    "§cYou are not currently bound to any panel account."
            ));
        }
        return success ? 1 : 0;
    }
}
