package com.example.ps5controller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PS5ControllerClient implements ClientModInitializer {
    public static final String MOD_ID = "ps5controller";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null && ControllerInput.isGamepadConnected()) {
                ControllerScreen.applyDpadNavigation();
            }
        });

        LOGGER.info("PS5 controller support initialized. D-pad and menu hover navigation are active.");
    }
}
