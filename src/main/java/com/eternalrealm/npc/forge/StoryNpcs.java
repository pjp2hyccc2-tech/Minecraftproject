package com.eternalrealm.npc.forge;

import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.quest.forge.QuestService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.Dialogue;
import com.eternalrealm.story.DialogueLine;
import com.eternalrealm.story.Speaker;
import com.eternalrealm.story.StoryNpc;
import com.eternalrealm.story.StoryTrigger;
import com.eternalrealm.story.forge.StoryDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;

/**
 * The story characters and quest givers: STORY-role NPCs that also carry their character id. Talking to one plays
 * the story: a quest giver first speaks about a quest whose "talk to" objective points at them (and the objective
 * completes), otherwise offers and accepts their next quest, otherwise says what the town is talking about at the
 * server's story progress.
 */
public final class StoryNpcs {
    /** Persistent data key holding the story character id. */
    public static final String CHARACTER = "eternalrealm_character";

    private StoryNpcs() {
    }

    public static Optional<String> characterOf(Mob npc) {
        String id = npc.getPersistentData().getString(CHARACTER);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    /** Places (or moves back) a quest giver of the quest module at {@code pos}. */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, NpcDefinition character, float yaw) {
        return ensure(level, pos, character.id(), Component.translatable(character.translationKey()), yaw);
    }

    /** Places (or moves back) any story character ({@link com.eternalrealm.story.StoryNpc} id) at {@code pos}. */
    public static Optional<Mob> ensure(ServerLevel level, BlockPos pos, String characterId, Component name, float yaw) {
        Optional<Mob> npc = Npcs.ensure(level, pos, NpcRole.STORY, yaw, CHARACTER, characterId);
        npc.ifPresent(mob -> {
            mob.setCustomName(name.copy().withStyle(ChatFormatting.AQUA));
            NpcWhereabouts.saw(mob);
        });
        return npc;
    }

    static void talk(ServerPlayer player, Mob npc) {
        String id = characterOf(npc).orElse("");
        ServerContext context = ServerContext.current().orElse(null);
        if (id.isEmpty() || context == null) {
            player.sendSystemMessage(Component.translatable("npc.eternalrealm.greeting", npc.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        QuestService quests = context.game().quests();
        StoryDirector story = context.game().story();
        for (String ready : quests.readyQuestsOfGiver(player, id)) {
            quests.turnIn(player, ready);
        }
        // Quest changes happen once the player has read the conversation (dialogue.done).
        Optional<String> talkQuest = quests.questWaitingForTalk(player, id);
        if (talkQuest.isPresent()) {
            story.trigger(new StoryTrigger.QuestTalk(id, talkQuest.get()), player, List.of(player),
                    () -> quests.event(player, new GameEvent.NpcTalked(id)));
            return;
        }
        Optional<String> offered = quests.nextQuestOfGiver(player, id);
        if (offered.isPresent()) {
            String quest = offered.get();
            story.trigger(new StoryTrigger.QuestTalk(id, quest), player, List.of(player), () -> {
                quests.accept(player, quest);
                quests.event(player, new GameEvent.NpcTalked(id)); // this conversation counts for its "talk to" objective
            });
            return;
        }
        if (!story.trigger(new StoryTrigger.NpcTalk(id, story.progress()), player, List.of(player),
                () -> quests.event(player, new GameEvent.NpcTalked(id)))) {
            String speaker = StoryNpc.byId(id).map(character -> character.speaker().key()).orElse(Speaker.NARRATOR.key());
            story.play(player, List.of(new DialogueLine(speaker, "dialogue.eternalrealm." + id + ".greeting")), true, null);
        }
    }

    /**
     * Talking to a service NPC: the first visit at each story progress (per session) plays their line in the dialogue
     * window and opens the service once the player has read it; later visits open it at once. Sneaking only talks.
     */
    static void serviceTalk(ServerPlayer player, NpcRole role, Runnable open) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            open.run();
            return;
        }
        StoryDirector story = context.game().story();
        int progress = story.progress();
        Optional<Dialogue> dialogue = story.catalog().dialogue(new StoryTrigger.NpcTalk(role.id(), progress));
        boolean talkOnly = player.isShiftKeyDown();
        if (dialogue.isEmpty() || (!talkOnly && !story.firstServiceTalk(player, role.id(), progress))) {
            if (!talkOnly) {
                open.run();
            }
            return;
        }
        story.play(player, dialogue.get().lines(), true, talkOnly ? null : open);
    }
}
