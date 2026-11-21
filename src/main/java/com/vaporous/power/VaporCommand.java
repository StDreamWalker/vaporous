package com.vaporous.power;

import com.mojang.brigadier.CommandDispatcher;
import com.vaporous.VaporConfig;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;

/**
 * /vaporous grant [players]   – give the powers
 * /vaporous revoke [players]  – take them away
 * /vaporous reload            – re-read config/vaporous.json
 */
public final class VaporCommand {
    private VaporCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("vaporous")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("grant")
                        .executes(ctx -> set(ctx.getSource(), List.of(ctx.getSource().getPlayerOrThrow()), true))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), true))))
                .then(CommandManager.literal("revoke")
                        .executes(ctx -> set(ctx.getSource(), List.of(ctx.getSource().getPlayerOrThrow()), false))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), false))))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            VaporConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.translatable("command.vaporous.reloaded"), true);
                            return 1;
                        })));
    }

    private static int set(ServerCommandSource source, Collection<ServerPlayerEntity> players, boolean powered) {
        for (ServerPlayerEntity p : players) {
            SpeedsterManager.setPowered(p, powered);
        }
        int n = players.size();
        source.sendFeedback(() -> Text.translatable(powered ? "command.vaporous.granted" : "command.vaporous.revoked", n), true);
        return n;
    }
}
