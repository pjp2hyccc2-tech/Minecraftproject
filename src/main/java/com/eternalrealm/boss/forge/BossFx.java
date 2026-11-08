package com.eternalrealm.boss.forge;

import com.eternalrealm.server.UiData;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Screen effects of boss fights sent to the players' clients on the UI data channel {@link #CHANNEL}: camera shake
 * (heavy hits, transformations, the collapse) and a full-screen flash (transformation, the boss dissolving in light).
 * The client side is {@code com.eternalrealm.client.boss.BossScreenEffects}.
 */
public final class BossFx {
    public static final String CHANNEL = "boss_fx";

    private BossFx() {
    }

    /** Shakes the camera: {@code intensity} degrees of roll at most, fading over {@code ticks}. */
    public static void shake(Collection<ServerPlayer> players, float intensity, int ticks) {
        send(players, intensity, ticks, 0, 0.0F, 0);
    }

    /** Fills the screen with {@code rgb} at {@code alpha}, fading out over {@code ticks}. */
    public static void flash(Collection<ServerPlayer> players, int rgb, float alpha, int ticks) {
        send(players, 0.0F, 0, rgb, alpha, ticks);
    }

    /** Both at once. */
    public static void send(Collection<ServerPlayer> players, float shake, int shakeTicks, int rgb, float alpha,
                            int flashTicks) {
        JsonObject data = new JsonObject();
        data.addProperty("shake", shake);
        data.addProperty("shakeTicks", shakeTicks);
        data.addProperty("rgb", rgb & 0xFFFFFF);
        data.addProperty("alpha", alpha);
        data.addProperty("flashTicks", flashTicks);
        data.addProperty("nonce", System.nanoTime());
        for (ServerPlayer player : players) {
            UiData.send(player, CHANNEL, data);
        }
    }
}
