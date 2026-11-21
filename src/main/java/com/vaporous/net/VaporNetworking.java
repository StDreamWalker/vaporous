package com.vaporous.net;

import com.vaporous.power.SpeedsterManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VaporNetworking {
    private VaporNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(VaporPayloads.Ability.ID, VaporPayloads.Ability.CODEC);
        PayloadTypeRegistry.playC2S().register(VaporPayloads.VaporReport.ID, VaporPayloads.VaporReport.CODEC);
        PayloadTypeRegistry.playS2C().register(VaporPayloads.VaporState.ID, VaporPayloads.VaporState.CODEC);
        PayloadTypeRegistry.playS2C().register(VaporPayloads.TimeFactor.ID, VaporPayloads.TimeFactor.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(VaporPayloads.Ability.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> SpeedsterManager.handleAbility(player, payload.ability(), payload.charge()));
        });
        ServerPlayNetworking.registerGlobalReceiver(VaporPayloads.VaporReport.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> SpeedsterManager.handleVaporReport(player, payload.vapor(), payload.floating()));
        });
    }

    /** Sends only if the client has this mod installed. */
    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
