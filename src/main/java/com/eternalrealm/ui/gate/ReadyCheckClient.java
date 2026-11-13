package com.eternalrealm.ui.gate;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.dungeon.forge.ReadyChecks;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The party's ready check (出発の準備) as this client knows it ({@link ReadyChecks#CHANNEL}): opens the screen when the
 * check begins (unless another screen is up), keeps the members' answers for the screen, the HUD panel and the party
 * frames, plays the chimes, and remembers how it ended for a few seconds. Client thread only.
 */
public final class ReadyCheckClient {
    /** A member of the check as last sent. */
    public record Member(UUID id, String name, boolean ready, boolean leader) {
    }

    /** How long the outcome stays on the HUD panel. */
    static final long END_SHOWN_MS = 4000;

    private static JsonObject state;
    private static long receivedAt;
    private static List<Member> members = List.of();
    private static Component endMessage;
    private static String endOutcome = "";
    private static long endAt;

    private ReadyCheckClient() {
    }

    public static void accept(JsonObject data) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = data.has("active") && data.get("active").isJsonPrimitive() && data.get("active").getAsBoolean();
        if (!active) {
            boolean had = state != null;
            state = null;
            members = List.of();
            endOutcome = ClientUiState.stringOr(data, "outcome", "");
            endMessage = parse(ClientUiState.stringOr(data, "message", ""));
            if (endMessage == null && "go".equals(endOutcome)) {
                endMessage = Component.translatable("screen.eternalrealm.ready.went");
            }
            endAt = Util.getMillis();
            if (minecraft.screen instanceof ReadyCheckScreen) {
                minecraft.setScreen(null);
            }
            if (had) {
                play("go".equals(endOutcome) ? SoundEvents.ENCHANTMENT_TABLE_USE : SoundEvents.NOTE_BLOCK_BASS.value(),
                        "go".equals(endOutcome) ? 1.2F : 0.6F);
            }
            return;
        }
        JsonObject before = state;
        int readyBefore = before == null ? -1 : readyCount();
        boolean countdownBefore = before != null && "countdown".equals(ClientUiState.stringOr(before, "phase", ""));
        state = data;
        receivedAt = Util.getMillis();
        members = parseMembers(data);
        endMessage = null;
        boolean countdown = countdownNow();
        if (countdown && !countdownBefore) {
            play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F);
        } else if (readyBefore >= 0 && readyCount() > readyBefore) {
            play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F);
        }
        boolean open = data.has("open") && data.get("open").isJsonPrimitive() && data.get("open").getAsBoolean();
        if (open && !(minecraft.screen instanceof ReadyCheckScreen)
                && (minecraft.screen == null || minecraft.screen instanceof GatekeeperScreen)) {
            minecraft.setScreen(new ReadyCheckScreen());
        }
    }

    private static List<Member> parseMembers(JsonObject data) {
        List<Member> out = new ArrayList<>();
        if (data.get("members") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject row) {
                    try {
                        out.add(new Member(UUID.fromString(ClientUiState.stringOr(row, "id", "")),
                                ClientUiState.stringOr(row, "name", "?"), flag(row, "ready"), flag(row, "leader")));
                    } catch (IllegalArgumentException e) {
                        // not a UUID: skip the row
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    private static Component parse(String json) {
        if (json.isEmpty()) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(json);
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    /** Forgets everything (leaving the world). */
    public static void clear() {
        state = null;
        members = List.of();
        endMessage = null;
        endOutcome = "";
    }

    public static boolean active() {
        return state != null;
    }

    static Optional<JsonObject> state() {
        return Optional.ofNullable(state);
    }

    static List<Member> members() {
        return members;
    }

    static int readyCount() {
        int count = 0;
        for (Member member : members) {
            if (member.ready()) {
                count++;
            }
        }
        return count;
    }

    static boolean countdownNow() {
        return state != null && "countdown".equals(ClientUiState.stringOr(state, "phase", ""));
    }

    /** Ticks left (of the countdown or of the time to get ready), counted down since the last update. */
    static long ticksLeft() {
        if (state == null) {
            return 0;
        }
        long left = ClientUiState.longOr(state, "left", 0);
        return Math.max(0, left - (Util.getMillis() - receivedAt) / 50);
    }

    /** This player's answer, if they are in the check. */
    static Optional<Boolean> mine() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Optional.empty();
        }
        return readyOf(minecraft.player.getUUID());
    }

    /** A member's answer while a check runs (the party frames show it). */
    public static Optional<Boolean> readyOf(UUID id) {
        for (Member member : members) {
            if (member.id().equals(id)) {
                return Optional.of(member.ready());
            }
        }
        return Optional.empty();
    }

    static boolean iAmLeader() {
        Minecraft minecraft = Minecraft.getInstance();
        return state != null && minecraft.player != null
                && minecraft.player.getUUID().toString().equals(ClientUiState.stringOr(state, "leader", ""));
    }

    /** How the last check ended, while it is still worth showing. */
    static Optional<Component> recentEnd() {
        if (endMessage == null || Util.getMillis() - endAt > END_SHOWN_MS) {
            return Optional.empty();
        }
        return Optional.of(endMessage);
    }

    static String recentOutcome() {
        return endOutcome;
    }

    static long endAge() {
        return Util.getMillis() - endAt;
    }

    static void answer(boolean ready) {
        JsonObject args = new JsonObject();
        args.addProperty("ready", ready);
        ERNetwork.sendToServer(new UiActionPacket(ReadyChecks.SET, args));
    }

    static void callOff() {
        ERNetwork.sendToServer(new UiActionPacket(ReadyChecks.CANCEL, new JsonObject()));
    }

    /** The ready (check) or waiting (dots) mark of the gate's atlas, 9x9 at {@code (x, y)}: for the party frames. */
    public static void drawMark(GuiGraphics graphics, int x, int y, boolean ready) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        GateArt.atlas(graphics, x, y, GateArt.MARK_U + (ready ? 0 : 9), GateArt.MARK_V, 9, 9);
    }
}
