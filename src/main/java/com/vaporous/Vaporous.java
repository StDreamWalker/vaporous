package com.vaporous;

import com.vaporous.net.VaporNetworking;
import com.vaporous.power.SpeedsterManager;
import com.vaporous.power.VaporCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vaporous — an original speedster whose body thins into living vapor as he accelerates.
 */
public class Vaporous implements ModInitializer {
    public static final String MOD_ID = "vaporous";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        VaporConfig.load();
        VaporRegistry.init();
        VaporNetworking.registerPayloads();
        VaporNetworking.registerServerReceivers();
        SpeedsterManager.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VaporCommand.register(dispatcher));
        LOGGER.info("Vaporous initialised — the air is thinning.");
    }
}
