package com.eternalrealm.client.guildhouse;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.guide.GuideClient;
import com.eternalrealm.guild.house.forge.GuildGateLabels;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The gates of the Guild Gate Terrace as the server told them ({@link GuildGateLabels}): where each stands and which
 * one is the player's guild's. Read by the gate labels ({@link GateLabelRenderer}), the capital's map and the guild
 * page's 「ギルドハウスの門へ案内」. Client thread only.
 */
public final class GuildGateClient {
    /** How far in front of the portal the guide leads (blocks): out of the light, facing it. */
    private static final double GUIDE_FRONT = 2.5;

    /** A gate: the feet block at the centre of its portal and the way one faces walking in. */
    public record Gate(String design, String nameKey, int x, int y, int z, float yaw) {
        /** The way out of the gate, towards where one stands before walking in: {x, z}. */
        public double[] front() {
            double radians = Math.toRadians(yaw);
            return new double[]{Math.sin(radians), -Math.cos(radians)};
        }

        /** {x, z} of a point {@code distance} blocks in front of the portal's centre. */
        public double[] inFront(double distance) {
            double[] front = front();
            return new double[]{x + 0.5 + front[0] * distance, z + 0.5 + front[1] * distance};
        }
    }

    /** The player's guild and the gate of its house. */
    public record Own(Gate gate, String guild, String tag, int color) {
        /** 【TAG】 Guild name, in the colour of the guild's standing. */
        public MutableComponent title() {
            return Component.literal("【" + tag + "】 " + guild)
                    .withStyle(Style.EMPTY.withColor(color).withBold(true));
        }
    }

    private static long revision = -1;
    private static String dimension = "";
    private static List<Gate> gates = List.of();
    private static Own own;

    private GuildGateClient() {
    }

    private static void refresh() {
        long now = ClientUiState.revision();
        if (now == revision) {
            return;
        }
        revision = now;
        dimension = "";
        gates = List.of();
        own = null;
        Optional<JsonObject> document = ClientUiState.get(GuildGateLabels.CHANNEL);
        if (document.isEmpty() || !document.get().has("gates") || !document.get().get("gates").isJsonArray()) {
            return;
        }
        JsonObject data = document.get();
        dimension = ClientUiState.stringOr(data, "dim", "");
        List<Gate> list = new ArrayList<>();
        for (JsonElement element : data.getAsJsonArray("gates")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject g = element.getAsJsonObject();
            list.add(new Gate(ClientUiState.stringOr(g, "design", ""), ClientUiState.stringOr(g, "name", ""),
                    ClientUiState.intOr(g, "x", 0), ClientUiState.intOr(g, "y", 0), ClientUiState.intOr(g, "z", 0),
                    (float) ClientUiState.doubleOr(g, "yaw", 0.0)));
        }
        gates = List.copyOf(list);
        String design = ClientUiState.stringOr(data, "own", "");
        if (!design.isEmpty()) {
            for (Gate gate : gates) {
                if (gate.design().equals(design)) {
                    own = new Own(gate, ClientUiState.stringOr(data, "guild", ""), ClientUiState.stringOr(data, "tag",
                            ""), ClientUiState.intOr(data, "color", 0xFFD040));
                    break;
                }
            }
        }
    }

    /** Whether {@code level} is the world of the gates (the capital's). */
    public static boolean here(Level level) {
        refresh();
        return level != null && !dimension.isEmpty() && level.dimension().location().toString().equals(dimension);
    }

    public static List<Gate> gates() {
        refresh();
        return gates;
    }

    /** The gate of the player's guild's house, if the guild has one. */
    public static Optional<Own> own() {
        refresh();
        return Optional.ofNullable(own);
    }

    /** Whether the guide to the guild's gate can start: a house, and the player in the capital's world. */
    public static boolean canGuide() {
        Minecraft minecraft = Minecraft.getInstance();
        return own().isPresent() && here(minecraft.level);
    }

    /** Lays the guide's yellow dots to the front of the guild's gate. */
    public static boolean guideToOwn() {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Own> mine = own();
        if (mine.isEmpty() || !here(minecraft.level)) {
            return false;
        }
        double[] spot = mine.get().gate().inFront(GUIDE_FRONT);
        Component name = Component.translatable("label.eternalrealm.guildgate.guide", mine.get().guild());
        GuideClient.guideTo(dimension, spot[0], spot[1], name);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("map.eternalrealm.capital.guide.started", name)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return true;
    }
}
