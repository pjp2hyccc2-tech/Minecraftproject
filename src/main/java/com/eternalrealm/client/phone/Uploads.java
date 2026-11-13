package com.eternalrealm.client.phone;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.PhotoUploadPacket;
import com.eternalrealm.social.phone.PhotoRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sends a photo taken with the 魔導スマホ to the server (in chunks, {@code PhotoUploadPacket}) once, then hands its
 * server id to what wanted it (a post, a message). A photo already sent is not sent again. Client thread only.
 */
public final class Uploads {
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int next = 1;

    private record Pending(Camera.Shot shot, Consumer<String> then, Runnable failed) {
    }

    private Uploads() {
    }

    /** Whether a photo is on its way. */
    public static boolean sending() {
        return !PENDING.isEmpty();
    }

    /**
     * Sends {@code shot} if needed, then calls {@code then} with its server id; if it is refused, {@code failed} runs
     * instead (and a toast says why).
     */
    public static void send(Camera.Shot shot, Consumer<String> then, Runnable failed) {
        if (!shot.serverId().isEmpty()) {
            then.accept(shot.serverId());
            return;
        }
        if (shot.jpeg().isEmpty()) {
            ClientPhone.toast(Component.translatable("phone.eternalrealm.camera.not_ready"));
            failed.run();
            return;
        }
        byte[] jpeg = shot.jpeg().get();
        if (jpeg.length > PhotoRules.MAX_BYTES) {
            ClientPhone.toast(Component.translatable("phone.eternalrealm.toast.bad_photo"));
            failed.run();
            return;
        }
        int upload = next++ & 0x7FFF_FFFF;
        PENDING.put(upload, new Pending(shot, then, failed));
        int total = (jpeg.length + PhotoRules.CHUNK - 1) / PhotoRules.CHUNK;
        for (int i = 0; i < total; i++) {
            byte[] chunk = Arrays.copyOfRange(jpeg, i * PhotoRules.CHUNK, Math.min(jpeg.length, (i + 1) * PhotoRules.CHUNK));
            ERNetwork.sendToServer(new PhotoUploadPacket(upload, i, total, chunk));
        }
    }

    /** UI channel {@code phone.upload}: {upload, ok, id | message}. */
    static void onResult(JsonObject result) {
        Pending pending = PENDING.remove(ClientUiState.intOr(result, "upload", -1));
        if (pending == null) {
            return;
        }
        boolean ok = result.has("ok") && result.get("ok").getAsBoolean();
        String id = ClientUiState.stringOr(result, "id", "");
        if (ok && PhotoRules.validId(id)) {
            pending.shot().sent(id);
            pending.then().accept(id);
            return;
        }
        try {
            Component message = Component.Serializer.fromJson(ClientUiState.stringOr(result, "message", "\"\""));
            ClientPhone.toast(message == null ? Component.translatable("phone.eternalrealm.toast.bad_photo") : message);
        } catch (JsonParseException e) {
            ClientPhone.toast(Component.translatable("phone.eternalrealm.toast.bad_photo"));
        }
        pending.failed().run();
    }

    static void clear() {
        for (Pending pending : PENDING.values()) {
            pending.failed().run();
        }
        PENDING.clear();
    }
}
