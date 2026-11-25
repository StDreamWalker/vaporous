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
 * /vaporous grant [players] [vapor|plasma]  – give the powers (vapor by default)
 * /vaporous revoke [players]                – take them away
 * /vaporous reload                          – re-read config/vaporous.json
 */
public final class VaporCommand {
    private VaporCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("vaporous")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("grant")
                        .executes(ctx -> set(ctx.getSource(), List.of(ctx.getSource().getPlayerOrThrow()), Variant.VAPOR))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), Variant.VAPOR))
                                .then(CommandManager.literal("vapor")
                                        .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), Variant.VAPOR)))
                                .then(CommandManager.literal("plasma")
                                        .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), Variant.PLASMA)))))
                .then(CommandManager.literal("revoke")
                        .executes(ctx -> set(ctx.getSource(), List.of(ctx.getSource().getPlayerOrThrow()), Variant.NONE))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(ctx -> set(ctx.getSource(), EntityArgumentType.getPlayers(ctx, "targets"), Variant.NONE))))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            VaporConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.translatable("command.vaporous.reloaded"), true);
                            return 1;
                        })));
    }

    private static int set(ServerCommandSource source, Collection<ServerPlayerEntity> players, int variant) {
        for (ServerPlayerEntity p : players) {
            SpeedsterManager.setVariant(p, variant);
        }
        int n = players.size();
        String key = variant == Variant.NONE ? "command.vaporous.revoked"
                : variant == Variant.PLASMA ? "command.vaporous.granted_plasma" : "command.vaporous.granted";
        source.sendFeedback(() -> Text.translatable(key, n), true);
        return n;
    }
}
