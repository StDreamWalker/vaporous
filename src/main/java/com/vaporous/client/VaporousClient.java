package com.vaporous.client;

import com.vaporous.VaporRegistry;
import com.vaporous.net.VaporPayloads;
import com.vaporous.time.TimeDilation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class VaporousClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(VaporRegistry.VAPOR, VaporParticle.Factory::new);
        VaporKeys.register();

        ClientPlayNetworking.registerGlobalReceiver(VaporPayloads.VaporState.ID, (payload, context) ->
                context.client().execute(() -> ClientVaporState.onState(payload)));
        ClientPlayNetworking.registerGlobalReceiver(VaporPayloads.TimeFactor.ID, (payload, context) ->
                context.client().execute(() -> TimeDilation.CLIENT.setFactor(payload.factor())));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.world != null && !client.isPaused()) {
                TimeDilation.CLIENT.advance();
                ClientVaporState.markTick();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(VaporClientController::tick);

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> VaporHud.render(drawContext));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientVaporState::reset));
    }
}
