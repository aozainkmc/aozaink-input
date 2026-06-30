package com.aozainkmc.input.command;

import com.aozainkmc.input.dev.AozaiInputDevMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AozaiInputCommand {
    private AozaiInputCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aozaink_input")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dev")
                    .executes(AozaiInputCommand::toggleDev))
        );
    }

    private static int toggleDev(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = AozaiInputDevMode.toggle(player);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[AozaiInk Input] 开发模式: " + (enabled ? "开启" : "关闭")), false);
        return 1;
    }
}
