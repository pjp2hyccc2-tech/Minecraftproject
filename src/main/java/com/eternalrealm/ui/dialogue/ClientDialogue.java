package com.eternalrealm.ui.dialogue;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.client.sound.UiSounds;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiActionPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's dialogues ({@code dialogue} UI data from the server's StoryDirector). Conversations ("modal") open the
 * {@link DialogueScreen} as soon as no other screen is open and wait for the player on every line; ambient dialogues
 * play in the {@link DialogueOverlay} above the hotbar and advance with the dialogue key (or by themselves when the
 * player enabled that). Lines are written out character by character; the first click completes the line, the next
 * one turns the page. Finished conversations go to the chat log, so nothing is lost, and the server is told
 * ({@code dialogue.done}) so it can run what follows (accept the quest, open the shop...). Client thread only.
 */
public final class ClientDialogue {
    /** Ambient dialogues waiting beyond this are written to the chat log instead of piling up. */
    private static final int MAX_AMBIENT_QUEUE = 6;
    private static final Deque<Conversation> MODAL = new ArrayDeque<>();
    private static final Deque<Conversation> AMBIENT = new ArrayDeque<>();
    /** Portrait textures by speaker id: their side in pixels, 0 when the resource pack has none. */
    private static final Map<String, Integer> PORTRAITS = new HashMap<>();
    @Nullable
    private static Conversation modal;
    @Nullable
    private static Conversation ambient;

    private ClientDialogue() {
    }

    /**
     * One line: narration has no speaker. {@code portraitPixels} is the side of the portrait texture (48 for the
     * townsfolk, 96 for the legendary travellers' sharper portraits).
     */
    public record Line(@Nullable Component speaker, Component text, @Nullable ResourceLocation portrait,
                       int portraitPixels) {
        public boolean narration() {
            return speaker == null;
        }

        /** "[Name] text" for the chat log (narration grey and italic). */
        Component logEntry() {
            if (speaker == null) {
                return text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            }
            return Component.literal("[").append(speaker.copy().withStyle(ChatFormatting.AQUA)).append("] ")
                    .append(text.copy().withStyle(ChatFormatting.WHITE));
        }
    }

    /** A dialogue being read: the current line and how much of it is written out. */
    public static final class Conversation {
        private final int id;
        private final boolean modal;
        private final List<Line> lines;
        private final List<Component> choices;
        private int index;
        private long lineStart;
        private boolean revealed;
        private boolean finished;

        Conversation(int id, boolean modal, List<Line> lines) {
            this(id, modal, lines, List.of());
        }

        Conversation(int id, boolean modal, List<Line> lines, List<Component> choices) {
            this.id = id;
            this.modal = modal;
            this.lines = List.copyOf(lines);
            this.choices = List.copyOf(choices);
        }

        /** The answers of a question (shown under its last line), empty for a plain dialogue. */
        public List<Component> choices() {
            return choices;
        }

        /** Whether the last line is written out and the player has to pick an answer. */
        public boolean awaitingChoice() {
            return !finished && !choices.isEmpty() && index == lines.size() - 1 && lineComplete();
        }

        public Line line() {
            return lines.get(index);
        }

        public int index() {
            return index;
        }

        public int size() {
            return lines.size();
        }

        public boolean modal() {
            return modal;
        }

        public boolean finished() {
            return finished;
        }

        /** How many characters of the current line ({@code length} long) are written out now. */
        public int visibleChars(int length) {
            int speed = ERClientConfig.DIALOGUE_TEXT_SPEED.get();
            if (revealed || speed <= 0) {
                return length;
            }
            long shown = (Util.getMillis() - lineStart) * speed / 1000L;
            if (shown >= length) {
                revealed = true;
                return length;
            }
            return (int) Math.max(0, shown);
        }

        /** Whether the current line is fully written out (the "next" prompt shows). */
        public boolean lineComplete() {
            return revealed || visibleChars(line().text().getString().length()) >= line().text().getString().length();
        }

        long lineAgeMillis() {
            return Util.getMillis() - lineStart;
        }

        void startLine() {
            lineStart = Util.getMillis();
            revealed = false;
        }
    }

    // ------------------------------------------------------------------ input from the server

    /** {@code {id, modal, lines: [{speaker?, portrait?, text}], choices?: [key]}}. Malformed data is ignored. */
    public static void accept(JsonObject data) {
        int id = ClientUiState.intOr(data, "id", 0);
        boolean isModal = data.has("modal") && data.get("modal").isJsonPrimitive() && data.get("modal").getAsBoolean();
        List<Line> lines = new ArrayList<>();
        JsonElement array = data.get("lines");
        if (array instanceof JsonArray entries) {
            for (JsonElement element : entries) {
                if (!(element instanceof JsonObject entry)) {
                    continue;
                }
                String text = ClientUiState.stringOr(entry, "text", "");
                if (text.isEmpty()) {
                    continue;
                }
                String speaker = ClientUiState.stringOr(entry, "speaker", "");
                String portraitId = ClientUiState.stringOr(entry, "portrait", "");
                ResourceLocation portrait = portrait(portraitId);
                lines.add(new Line(speaker.isEmpty() ? null : Component.translatable(speaker),
                        Component.translatable(text), portrait, portrait == null ? 0 : PORTRAITS.get(portraitId)));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        List<Component> choices = new ArrayList<>();
        if (data.get("choices") instanceof JsonArray options && isModal) {
            for (JsonElement option : options) {
                if (option.isJsonPrimitive() && choices.size() < 4) {
                    choices.add(Component.translatable(option.getAsString()));
                }
            }
        }
        Conversation conversation = new Conversation(id, isModal, lines, choices);
        if (isModal) {
            MODAL.add(conversation);
        } else {
            AMBIENT.add(conversation);
            while (AMBIENT.size() > MAX_AMBIENT_QUEUE) {
                log(AMBIENT.poll(), 0);
            }
        }
    }

    /** Portrait texture of a speaker id, if the resource pack has one (its size is remembered in PORTRAITS). */
    @Nullable
    private static ResourceLocation portrait(String speakerId) {
        if (speakerId.isEmpty() || !ResourceLocation.isValidPath(speakerId)) {
            return null;
        }
        ResourceLocation location = new ResourceLocation("eternalrealm", "textures/gui/portrait/" + speakerId + ".png");
        int pixels = PORTRAITS.computeIfAbsent(speakerId, id -> pixelsOf(location));
        return pixels > 0 ? location : null;
    }

    private static int pixelsOf(ResourceLocation location) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return 0;
        }
        try (var in = resource.get().open();
             var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            return Math.max(1, image.getWidth());
        } catch (java.io.IOException | RuntimeException e) {
            return 48;
        }
    }

    // ------------------------------------------------------------------ per tick

    /** Client tick: starts the next dialogues, reads the dialogue key and auto-advances ambient lines if enabled. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (modal == null && !MODAL.isEmpty() && minecraft.screen == null) {
            modal = MODAL.poll();
            modal.startLine();
            UiSounds.npcTalk();
            minecraft.setScreen(new DialogueScreen(modal));
        }
        if (ambient == null && !AMBIENT.isEmpty()) {
            ambient = AMBIENT.poll();
            ambient.startLine();
            UiSounds.click();
        }
        boolean free = minecraft.screen == null;
        while (ClientKeyMappings.DIALOGUE_NEXT.consumeClick()) {
            if (ambient != null && free) {
                advance(ambient);
            }
        }
        if (ambient != null && free && ERClientConfig.DIALOGUE_AUTO_ADVANCE.get() && ambient.lineComplete()) {
            int length = ambient.line().text().getString().length();
            if (ambient.lineAgeMillis() > 2500L + length * 90L) {
                advance(ambient);
            }
        }
    }

    /** The ambient dialogue on screen, if any (the overlay draws it). */
    @Nullable
    static Conversation ambient() {
        return ambient;
    }

    /** Whether an ambient dialogue box is showing (the HUD layout makes room for it). */
    public static boolean ambientShowing() {
        return ambient != null && !(net.minecraft.client.Minecraft.getInstance().screen instanceof DialogueScreen);
    }

    // ------------------------------------------------------------------ advancing

    /**
     * Click / key: completes the line being written, otherwise turns to the next line.
     *
     * @return whether the conversation is over now
     */
    public static boolean advance(Conversation conversation) {
        if (conversation.finished) {
            return true;
        }
        if (!conversation.lineComplete()) {
            conversation.revealed = true;
            return false;
        }
        if (conversation.index + 1 < conversation.size()) {
            conversation.index++;
            conversation.startLine();
            UiSounds.click();
            return false;
        }
        if (!conversation.choices.isEmpty()) {
            return false; // a question: it waits for an answer
        }
        finish(conversation, conversation.size());
        return true;
    }

    /** The player picks answer {@code choice} of a question: the server is told, the exchange goes to the log. */
    public static void choose(Conversation conversation, int choice) {
        if (conversation.finished || choice < 0 || choice >= conversation.choices.size()) {
            return;
        }
        conversation.finished = true;
        log(conversation, conversation.size());
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("» ")
                .append(conversation.choices.get(choice).copy()).withStyle(ChatFormatting.GOLD));
        JsonObject args = new JsonObject();
        args.addProperty("id", conversation.id);
        args.addProperty("choice", choice);
        ERNetwork.sendToServer(new UiActionPacket("dialogue.choice", args));
        UiSounds.click();
        if (modal == conversation) {
            modal = null;
        }
    }

    /** Esc / screen closed: the rest of the conversation goes to the chat log. */
    public static void skip(Conversation conversation) {
        if (!conversation.finished) {
            finish(conversation, conversation.size());
        }
    }

    private static void finish(Conversation conversation, int logUpTo) {
        conversation.finished = true;
        if (conversation.modal) {
            log(conversation, logUpTo); // read in the window: keep it in the chat history
            JsonObject args = new JsonObject();
            args.addProperty("id", conversation.id);
            ERNetwork.sendToServer(new UiActionPacket("dialogue.done", args));
            if (modal == conversation) {
                modal = null;
            }
        } else {
            log(conversation, logUpTo);
            if (ambient == conversation) {
                ambient = null;
            }
        }
    }

    private static void log(Conversation conversation, int upTo) {
        Minecraft minecraft = Minecraft.getInstance();
        int end = upTo <= 0 ? conversation.size() : Math.min(upTo, conversation.size());
        for (int i = 0; i < end; i++) {
            minecraft.gui.getChat().addMessage(conversation.lines.get(i).logEntry());
        }
    }

    /** Leaving the world: drop everything (the server forgets its follow-ups too). */
    public static void clear() {
        MODAL.clear();
        AMBIENT.clear();
        modal = null;
        ambient = null;
        PORTRAITS.clear();
    }
}
