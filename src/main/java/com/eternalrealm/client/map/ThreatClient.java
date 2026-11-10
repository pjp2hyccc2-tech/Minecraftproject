package com.eternalrealm.client.map;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.FieldLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * The client's view of the field threats: the board the server sends every few seconds (miasma and phase per field,
 * lords, calamities) for the map, the 手配書 and the gauge under the minimap, plus the omen haze — while the field the
 * player stands in is under an omen or its calamity has risen, the fog turns a sickly violet and closes in.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ThreatClient {
    public static final String BOARD_CHANNEL = "threat_board";
    public static final String RESULT_CHANNEL = "threat_result";
    private static JsonObject board;
    private static long boardAt;
    private static float haze;

    private ThreatClient() {
    }

    public static void init() {
        ClientChannels.register(BOARD_CHANNEL, data -> {
            board = data;
            boardAt = System.nanoTime();
        });
        ClientChannels.register(RESULT_CHANNEL, com.eternalrealm.ui.map.ThreatOverlay::showResult);
    }

    /** The latest board, if any arrived in this session. */
    public static Optional<JsonObject> board() {
        return Optional.ofNullable(board);
    }

    /** Seconds since the board arrived (its countdowns are relative to that moment). */
    public static double boardAge() {
        return (System.nanoTime() - boardAt) / 1.0E9;
    }

    /** The board entry of {@code field}. */
    public static Optional<JsonObject> field(int field) {
        if (board == null || !board.has("fields") || !board.get("fields").isJsonArray()) {
            return Optional.empty();
        }
        for (JsonElement element : board.getAsJsonArray("fields")) {
            JsonObject entry = element.getAsJsonObject();
            if (ClientUiState.intOr(entry, "field", -1) == field) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** The field the local player stands in (0 in the capital or outside アストリア). */
    public static int currentField() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientMapState.inRealm(minecraft.level)
                || !ClientMapState.view().realm().astoria() || FieldLayout.inCapital(player.getX(), player.getZ())) {
            return 0;
        }
        return FieldLayout.areaAt(player.getX(), player.getZ());
    }

    private static float targetHaze() {
        int field = currentField();
        if (field == 0) {
            return 0.0F;
        }
        String phase = field(field).map(entry -> ClientUiState.stringOr(entry, "phase", "calm")).orElse("calm");
        return switch (phase) {
            case "omen" -> 0.45F + 0.15F * (float) Math.sin(System.nanoTime() / 6.0E8);
            case "manifest" -> 0.6F;
            default -> 0.0F;
        };
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        haze += (targetHaze() - haze) * 0.02F;
        if (haze < 0.005F) {
            haze = 0.0F;
            return;
        }
        float k = haze;
        event.setRed(event.getRed() * (1.0F - k) + 0.24F * k);
        event.setGreen(event.getGreen() * (1.0F - k) + 0.07F * k);
        event.setBlue(event.getBlue() * (1.0F - k) + 0.30F * k);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (haze <= 0.0F) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float closer = far * (1.0F - 0.55F * haze);
        event.setFarPlaneDistance(Math.max(48.0F, closer));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), closer * 0.25F));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        board = null;
        haze = 0.0F;
    }
}
