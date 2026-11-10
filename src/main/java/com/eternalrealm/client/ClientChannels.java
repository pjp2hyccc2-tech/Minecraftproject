package com.eternalrealm.client;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handlers of server view-data channels ({@code UiDataPacket}) that react to each document as it arrives (open a
 * screen, start an effect). Features register here from their client setup instead of growing the router in
 * {@link ClientModEvents}. Client thread only.
 */
public final class ClientChannels {
    private static final Map<String, Consumer<JsonObject>> HANDLERS = new HashMap<>();

    private ClientChannels() {
    }

    public static void register(String channel, Consumer<JsonObject> handler) {
        HANDLERS.put(channel, handler);
    }

    /** Runs the handler of {@code channel} with its latest document (already stored in {@link ClientUiState}). */
    public static void dispatch(String channel) {
        Consumer<JsonObject> handler = HANDLERS.get(channel);
        if (handler != null) {
            ClientUiState.get(channel).ifPresent(handler);
        }
    }
}
