package com.aozainkmc.input.command;

import com.aozainkmc.input.dev.AozaiInputDevMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.aozainkmc.input.network.AozaiInkNetworking;

public final class AozaiInputCommand {
    private AozaiInputCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("aozaink_input")
                .then(Commands.literal("dev")
                    .requires(source -> source.hasPermission(2))
                    .executes(AozaiInputCommand::toggleDev))
                .then(Commands.literal("menu").executes(AozaiInputCommand::openMenu))
        );
        dispatcher.register(Commands.literal("molu")
            .then(Commands.literal("menu").executes(AozaiInputCommand::openMenu)));
    }

    private static int toggleDev(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = AozaiInputDevMode.toggle(player);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[AozaiInk Input] 开发模式: " + (enabled ? "开启" : "关闭")), false);
        return 1;
    }

    private static int openMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        AozaiInkNetworking.sendMenu(ctx.getSource().getPlayerOrException());
        return 1;
    }
}
