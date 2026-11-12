package com.eternalrealm.story.forge;

import com.eternalrealm.story.Dialogue;
import com.eternalrealm.story.DialogueLine;
import com.eternalrealm.story.EndingSequence;
import com.eternalrealm.story.StoryCatalog;
import com.eternalrealm.story.StoryProgress;
import com.eternalrealm.story.StoryTrigger;
import com.eternalrealm.world.forge.WorldService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shows the story. Dialogues go to the client's dialogue box as a whole ({@link #CHANNEL}) and the player reads them at
 * their own pace: conversations (talking to an NPC, quest talks, the prologue) open the dialogue window and wait for a
 * click per line; everything else (areas, dungeons, bosses, server news) appears in the box above the hotbar and
 * advances with the dialogue key. Follow-ups such as accepting a quest or opening a shop run once the player has read
 * the conversation ({@code dialogue.done}). The ending plays its timed beats and the staff roll for the whole server.
 */
public final class StoryDirector {
    /** Player extension: the season's prologue was shown. */
    public static final String PROLOGUE_SEEN = "story_prologue";
    /** UI data channel of dialogues ({@code {id, modal, lines: [{speaker?, portrait?, text}]}}). */
    public static final String CHANNEL = "dialogue";
    /** A follow-up waits at most this long for the player to finish reading (then it is dropped). */
    private static final long PENDING_TTL_TICKS = 20L * 60 * 15;

    private final MinecraftServer server;
    private final WorldService world;
    private final com.eternalrealm.player.PlayerDataService playerData;
    private final StoryCatalog catalog = StoryCatalog.standard();
    /** Per player: dialogue id -> what to do once they have read it. */
    private final Map<UUID, Map<Integer, Pending>> pending = new HashMap<>();
    /** Per player: service NPC id -> story progress whose line they have heard this session. */
    private final Map<UUID, Map<String, Integer>> heardServiceLines = new HashMap<>();
    private int nextDialogueId = 1;
    private final java.util.Set<UUID> checkedLogins = new java.util.HashSet<>();
    private List<String> heroes = List.of();
    private long now;
    private long endingStart = -1;

    public StoryDirector(MinecraftServer server, WorldService world, com.eternalrealm.player.PlayerDataService playerData) {
        this.server = Objects.requireNonNull(server, "server");
        this.world = Objects.requireNonNull(world, "world");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        world.onDiscover((player, area) -> trigger(new StoryTrigger.AreaEntered(area.id()), player, List.of(player)));
    }

    public StoryCatalog catalog() {
        return catalog;
    }

    /** Bosses defeated on this server so far (0..9): what the town talks about. */
    public int progress() {
        return StoryProgress.of(world.progress().defeatedBossIds());
    }

    private record Pending(Runnable action, long expiresAt) {
    }

    /** A question waiting for the player's answer. */
    private record Asked(java.util.function.IntConsumer answer, long expiresAt) {
    }

    /** Per player: dialogue id -> what to do with the answer. */
    private final Map<UUID, Map<Integer, Asked>> asked = new HashMap<>();

    /** Plays the trigger's dialogue, if the story has one (see {@link #trigger(StoryTrigger, ServerPlayer, Collection, Runnable)}). */
    public boolean trigger(StoryTrigger trigger, ServerPlayer source, Collection<ServerPlayer> party) {
        return trigger(trigger, source, party, null);
    }

    /**
     * Plays the trigger's dialogue for its audience (the player, the party or everyone). For the player who started a
     * conversation ({@link #isConversation}) it opens the dialogue window.
     *
     * @param source the player who caused it (may be null for server events)
     * @param party  the party/raid for {@link StoryTrigger.Audience#PARTY}
     * @param then   runs once {@code source} has read the dialogue, or at once when there is none or the source does not
     *               see it (may be null)
     * @return whether a dialogue was found
     */
    public boolean trigger(StoryTrigger trigger, ServerPlayer source, Collection<ServerPlayer> party, Runnable then) {
        Dialogue dialogue = catalog.dialogue(trigger).orElse(null);
        if (dialogue == null) {
            if (then != null) {
                then.run();
            }
            return false;
        }
        Collection<ServerPlayer> audience = switch (trigger.audience()) {
            case PLAYER -> source == null ? List.of() : List.of(source);
            case PARTY -> party.isEmpty() && source != null ? List.of(source) : party;
            case SERVER -> server.getPlayerList().getPlayers();
        };
        boolean scheduled = false;
        for (ServerPlayer player : audience) {
            boolean own = player == source;
            play(player, dialogue.lines(), own && isConversation(trigger), own ? then : null);
            scheduled |= own && then != null;
        }
        if (then != null && !scheduled) {
            then.run();
        }
        return true;
    }

    /** Talks with an NPC and the prologue are read in the dialogue window; the rest is ambient. */
    static boolean isConversation(StoryTrigger trigger) {
        return trigger instanceof StoryTrigger.NpcTalk || trigger instanceof StoryTrigger.QuestTalk
                || trigger instanceof StoryTrigger.FirstLogin;
    }

    /** Plays a whole dialogue for one player (after what they are already reading). */
    public void play(ServerPlayer player, Dialogue dialogue) {
        play(player, dialogue.lines(), false, null);
    }

    /**
     * Sends lines to the player's dialogue box.
     *
     * @param modal in the dialogue window (one click per line) rather than the ambient box
     * @param then  runs when the player has read (or skipped) them; dropped if they log out first (may be null)
     */
    public void play(ServerPlayer player, List<DialogueLine> lines, boolean modal, Runnable then) {
        if (lines.isEmpty()) {
            if (then != null) {
                then.run();
            }
            return;
        }
        int id = nextDialogueId++;
        JsonObject data = payload(id, lines, modal);
        if (then != null) {
            pending.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(id, new Pending(then, now + PENDING_TTL_TICKS));
        }
        com.eternalrealm.server.UiData.send(player, CHANNEL, data);
    }

    /**
     * Asks the player: the lines in the dialogue window, then the choices (translation keys, at most
     * {@link #MAX_CHOICES}) under the last line. {@code answer} gets the index of the choice, or -1 when the player
     * closed the window without choosing (or logged out first: then it is dropped).
     */
    public void ask(ServerPlayer player, List<DialogueLine> lines, List<String> choices,
                    java.util.function.IntConsumer answer) {
        if (lines.isEmpty() || choices.isEmpty() || choices.size() > MAX_CHOICES) {
            throw new IllegalArgumentException("a question needs lines and 1.." + MAX_CHOICES + " choices");
        }
        int id = nextDialogueId++;
        JsonObject data = payload(id, lines, true);
        JsonArray options = new JsonArray();
        choices.forEach(options::add);
        data.add("choices", options);
        asked.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(id, new Asked(answer, now + PENDING_TTL_TICKS));
        com.eternalrealm.server.UiData.send(player, CHANNEL, data);
    }

    /** Most choices a question can offer. */
    public static final int MAX_CHOICES = 4;

    private static JsonObject payload(int id, List<DialogueLine> lines, boolean modal) {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("modal", modal);
        JsonArray array = new JsonArray();
        for (DialogueLine line : lines) {
            JsonObject entry = new JsonObject();
            if (!line.isNarration()) {
                entry.addProperty("speaker", line.speakerKey());
                entry.addProperty("portrait", line.speakerKey().substring(line.speakerKey().lastIndexOf('.') + 1));
            }
            entry.addProperty("text", line.textKey());
            array.add(entry);
        }
        data.add("lines", array);
        return data;
    }

    /** {@code dialogue.choice}: the player answered question {@code id} with choice {@code choice}. */
    public void onDialogueChoice(ServerPlayer player, int id, int choice) {
        Map<Integer, Asked> mine = asked.get(player.getUUID());
        Asked question = mine == null ? null : mine.remove(id);
        if (question != null && choice >= 0 && choice < MAX_CHOICES) {
            question.answer().accept(choice);
        }
    }

    /** One line of battle talk (a boss's bark) in the ambient box of everyone fighting. */
    public void bark(Collection<ServerPlayer> players, DialogueLine line) {
        for (ServerPlayer player : players) {
            play(player, List.of(line), false, null);
        }
    }

    /** {@code dialogue.done}: the player closed dialogue {@code id} (read to the end or skipped). */
    public void onDialogueDone(ServerPlayer player, int id) {
        Map<Integer, Pending> mine = pending.get(player.getUUID());
        Pending done = mine == null ? null : mine.remove(id);
        if (done != null) {
            done.action().run();
        }
        Map<Integer, Asked> questions = asked.get(player.getUUID());
        Asked question = questions == null ? null : questions.remove(id);
        if (question != null) {
            question.answer().accept(-1); // closed without an answer
        }
    }

    /**
     * Service NPCs (shop, blacksmith...) say their line once per story progress and session; later visits go straight
     * to the service. @return whether {@code player} has not heard {@code npcId}'s line at this progress yet (and now has)
     */
    public boolean firstServiceTalk(ServerPlayer player, String npcId, int storyProgress) {
        Map<String, Integer> heard = heardServiceLines.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        Integer before = heard.put(npcId, storyProgress);
        return before == null || before != storyProgress;
    }

    /** Once the character is loaded: the first login of the season starts the prologue. */
    private void checkPrologue(ServerPlayer player) {
        if (!playerData.isActive(player.getUUID()) || !checkedLogins.add(player.getUUID())) {
            return;
        }
        if (playerData.extension(player.getUUID(), PROLOGUE_SEEN).isPresent()) {
            return;
        }
        playerData.setExtension(player.getUUID(), PROLOGUE_SEEN, "true");
        trigger(StoryTrigger.FIRST_LOGIN, player, List.of(player));
    }

    public void onLogout(UUID playerId) {
        pending.remove(playerId);
        asked.remove(playerId);
        heardServiceLines.remove(playerId);
        checkedLogins.remove(playerId);
    }

    /** The King of the End fell: everyone watches the ending; the raid's names appear in the staff roll. */
    public void startEnding(List<String> raidNames) {
        heroes = List.copyOf(raidNames);
        endingStart = now;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.eternalrealm.sound.forge.MusicContexts.jingle(player, "ending");
        }
    }

    public void tick(long tick) {
        now = tick;
        if (tick % 20 == 3) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkPrologue(player);
            }
        }
        if (tick % 200 == 7) {
            pending.values().forEach(mine -> mine.values().removeIf(entry -> entry.expiresAt() < now));
            pending.values().removeIf(Map::isEmpty);
            asked.values().forEach(mine -> mine.values().removeIf(entry -> entry.expiresAt() < now));
            asked.values().removeIf(Map::isEmpty);
        }
        if (endingStart >= 0) {
            tickEnding();
        }
    }

    private void tickEnding() {
        EndingSequence ending = catalog.ending();
        long at = now - endingStart;
        for (EndingSequence.Beat beat : ending.beats()) {
            if (beat.atTick() == at) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(format(beat.line()));
                    if (beat.display() == EndingSequence.Display.TITLE) {
                        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
                        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                        player.connection.send(new ClientboundSetSubtitleTextPacket(
                                Component.translatable(beat.line().textKey()).withStyle(ChatFormatting.GOLD)));
                    }
                }
            }
        }
        long creditsFrom = ending.staffRollStartTick();
        if (at >= creditsFrom && (at - creditsFrom) % ending.creditIntervalTicks() == 0) {
            int index = (int) ((at - creditsFrom) / ending.creditIntervalTicks());
            if (index < ending.staffRoll().size()) {
                EndingSequence.Credit credit = ending.staffRoll().get(index);
                MutableComponent text = Component.translatable(credit.textKey());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(5, ending.creditIntervalTicks(), 5));
                    player.connection.send(new ClientboundSetTitleTextPacket(credit.kind() == EndingSequence.CreditKind.HEADING
                            ? text.copy().withStyle(ChatFormatting.GOLD) : Component.empty()));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(credit.kind() == EndingSequence.CreditKind.HEADING
                            ? Component.empty() : text.copy().withStyle(ChatFormatting.WHITE)));
                    if (credit.kind() == EndingSequence.CreditKind.PLAYERS && !heroes.isEmpty()) {
                        player.sendSystemMessage(text.copy().withStyle(ChatFormatting.GOLD).append(" ")
                                .append(Component.literal(String.join(", ", heroes)).withStyle(ChatFormatting.WHITE)));
                    }
                }
            }
        }
        if (at >= ending.totalTicks()) {
            endingStart = -1;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
            }
            trigger(new StoryTrigger.SeasonEnded(true), null, List.of());
        }
    }

    /** "[Name] text" (narration grey and italic without a name). */
    public static Component format(DialogueLine line) {
        if (line.isNarration()) {
            return Component.translatable(line.textKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        return Component.literal("[").append(Component.translatable(line.speakerKey()).withStyle(ChatFormatting.AQUA))
                .append("] ").append(Component.translatable(line.textKey()).withStyle(ChatFormatting.WHITE));
    }
}
