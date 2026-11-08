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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shows the story: dialogues of the story module play line by line ({@link StoryCatalog#LINE_INTERVAL_TICKS} apart) in
 * chat for their audience (the player, the party or everyone), and the ending plays its timed beats and the staff roll
 * for the whole server.
 */
public final class StoryDirector {
    /** Player extension: the season's prologue was shown. */
    public static final String PROLOGUE_SEEN = "story_prologue";

    private final MinecraftServer server;
    private final WorldService world;
    private final com.eternalrealm.player.PlayerDataService playerData;
    private final StoryCatalog catalog = StoryCatalog.standard();
    private final Map<UUID, Deque<DialogueLine>> queues = new HashMap<>();
    private final Map<UUID, Long> nextLineAt = new HashMap<>();
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

    /**
     * Plays the trigger's dialogue, if the story has one.
     *
     * @param source the player who caused it (may be null for server events)
     * @param party  the party/raid for {@link StoryTrigger.Audience#PARTY}
     * @return whether a dialogue was found
     */
    public boolean trigger(StoryTrigger trigger, ServerPlayer source, Collection<ServerPlayer> party) {
        Dialogue dialogue = catalog.dialogue(trigger).orElse(null);
        if (dialogue == null) {
            return false;
        }
        Collection<ServerPlayer> audience = switch (trigger.audience()) {
            case PLAYER -> source == null ? List.of() : List.of(source);
            case PARTY -> party.isEmpty() && source != null ? List.of(source) : party;
            case SERVER -> server.getPlayerList().getPlayers();
        };
        for (ServerPlayer player : audience) {
            play(player, dialogue);
        }
        return true;
    }

    /** Queues the lines of a dialogue for one player (after what they are already reading). */
    public void play(ServerPlayer player, Dialogue dialogue) {
        Deque<DialogueLine> queue = queues.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.isEmpty()) {
            nextLineAt.put(player.getUUID(), now);
        }
        queue.addAll(dialogue.lines());
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
        queues.remove(playerId);
        nextLineAt.remove(playerId);
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
        for (Map.Entry<UUID, Deque<DialogueLine>> entry : queues.entrySet()) {
            Deque<DialogueLine> queue = entry.getValue();
            if (queue.isEmpty() || now < nextLineAt.getOrDefault(entry.getKey(), 0L)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            DialogueLine line = queue.poll();
            if (player != null && line != null) {
                player.sendSystemMessage(format(line));
                nextLineAt.put(entry.getKey(), now + StoryCatalog.LINE_INTERVAL_TICKS);
            }
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
