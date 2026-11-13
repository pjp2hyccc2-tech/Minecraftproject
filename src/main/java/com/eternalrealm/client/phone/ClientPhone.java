package com.eternalrealm.client.phone;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.network.forge.ClientboundHandlers;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The 魔導スマホ on the client: the latest view of each kind the server sent (UI channel {@code phone}), the badges,
 * a toast, and the requests the screens make (UI actions {@code phone.*}). Client thread only.
 */
public final class ClientPhone {
    private static final Map<String, JsonObject> VIEWS = new HashMap<>();
    private static int unread;
    private static int requests;
    private static int activity;
    private static Component toast;
    private static long toastAt;
    private static boolean moderator;
    private static int revision;

    private ClientPhone() {
    }

    /** Client setup: listens to the phone's channels. */
    public static void init() {
        ClientChannels.register("phone", ClientPhone::onView);
        ClientChannels.register("phone.badges", ClientPhone::badges);
        ClientChannels.register("phone.toast", ClientPhone::onToast);
        ClientChannels.register("phone.open", ClientPhone::onOpen);
        ClientChannels.register("phone.upload", Uploads::onResult);
        ClientboundHandlers.setPhotoHandler(PhotoCache::accept);
    }

    private static void onView(JsonObject view) {
        String kind = ClientUiState.stringOr(view, "view", "home");
        VIEWS.put(kind, view);
        badges(view);
        moderator = view.has("mod") && view.get("mod").getAsBoolean();
        revision++;
    }

    private static void badges(JsonObject data) {
        unread = ClientUiState.intOr(data, "unread", unread);
        requests = ClientUiState.intOr(data, "requests", requests);
        activity = ClientUiState.intOr(data, "activity", activity);
        revision++;
    }

    private static void onToast(JsonObject data) {
        try {
            toast = Component.Serializer.fromJson(ClientUiState.stringOr(data, "message", "\"\""));
        } catch (JsonParseException e) {
            toast = null;
        }
        toastAt = System.currentTimeMillis();
        revision++;
    }

    private static void onOpen(JsonObject data) {
        com.eternalrealm.ui.phone.PhoneScreen.open(ClientUiState.stringOr(data, "view", "home"),
                ClientUiState.stringOr(data, "id", ""));
    }

    /** The latest view of that kind, if the server sent one. */
    public static Optional<JsonObject> view(String kind) {
        return Optional.ofNullable(VIEWS.get(kind));
    }

    /** Changes with everything that arrives (screens compare it to rebuild). */
    public static int revision() {
        return revision;
    }

    public static int unread() {
        return unread;
    }

    public static int requests() {
        return requests;
    }

    public static int activity() {
        return activity;
    }

    /** Everything that waits for the player (the journal's badge). */
    public static int waiting() {
        return unread + requests + activity;
    }

    public static boolean moderator() {
        return moderator;
    }

    /** The toast to show now (for three seconds after it came). */
    public static Optional<Component> toast() {
        if (toast == null || System.currentTimeMillis() - toastAt > 3000L) {
            return Optional.empty();
        }
        return Optional.of(toast);
    }

    public static void toast(Component message) {
        toast = message;
        toastAt = System.currentTimeMillis();
        revision++;
    }

    /** Asks the server for a view ({@code phone.view}). */
    public static void show(String view, JsonObject args) {
        JsonObject out = args == null ? new JsonObject() : args.deepCopy();
        out.addProperty("view", view);
        send("phone.view", out);
    }

    public static void send(String action, JsonObject args) {
        if (Minecraft.getInstance().getConnection() != null) {
            ERNetwork.sendToServer(new UiActionPacket(action, args));
        }
    }

    /** Leaving the world. */
    public static void clear() {
        VIEWS.clear();
        unread = 0;
        requests = 0;
        activity = 0;
        toast = null;
        moderator = false;
        revision++;
        PhotoCache.clear();
        Camera.clear();
        Uploads.clear();
    }
}
