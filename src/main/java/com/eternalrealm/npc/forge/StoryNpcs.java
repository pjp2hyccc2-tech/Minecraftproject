package com.eternalrealm.npc.forge;

import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.quest.forge.QuestService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.StoryTrigger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;

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

    public static Optional<String> characterOf(Villager npc) {
        String id = npc.getPersistentData().getString(CHARACTER);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    /** Places (or moves back) a quest giver of the quest module at {@code pos}. */
    public static Optional<Villager> ensure(ServerLevel level, BlockPos pos, NpcDefinition character, float yaw) {
        return ensure(level, pos, character.id(), Component.translatable(character.translationKey()), yaw);
    }

    /** Places (or moves back) any story character ({@link com.eternalrealm.story.StoryNpc} id) at {@code pos}. */
    public static Optional<Villager> ensure(ServerLevel level, BlockPos pos, String characterId, Component name, float yaw) {
        Optional<Villager> npc = Npcs.ensure(level, pos, NpcRole.STORY, yaw, CHARACTER, characterId);
        npc.ifPresent(villager -> villager.setCustomName(name.copy().withStyle(ChatFormatting.AQUA)));
        return npc;
    }

    static void talk(ServerPlayer player, Villager npc) {
        String id = characterOf(npc).orElse("");
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.VILLAGER_AMBIENT,
                SoundSource.NEUTRAL, 0.6F, 1.0F);
        ServerContext context = ServerContext.current().orElse(null);
        if (id.isEmpty() || context == null) {
            player.sendSystemMessage(Component.translatable("npc.eternalrealm.greeting", npc.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        QuestService quests = context.game().quests();
        Optional<String> talkQuest = quests.questWaitingForTalk(player, id);
        if (talkQuest.isPresent()) {
            context.game().story().trigger(new StoryTrigger.QuestTalk(id, talkQuest.get()), player, List.of(player));
            quests.event(player, new GameEvent.NpcTalked(id));
            return;
        }
        Optional<String> offered = quests.nextQuestOfGiver(player, id);
        if (offered.isPresent()) {
            context.game().story().trigger(new StoryTrigger.QuestTalk(id, offered.get()), player, List.of(player));
            quests.accept(player, offered.get());
            return;
        }
        if (!context.game().story().trigger(new StoryTrigger.NpcTalk(id, context.game().story().progress()), player,
                List.of(player))) {
            player.sendSystemMessage(Component.literal("[").append(npc.getDisplayName()).append("] ")
                    .append(Component.translatable("dialogue.eternalrealm." + id + ".greeting"))
                    .withStyle(ChatFormatting.WHITE));
        }
        quests.event(player, new GameEvent.NpcTalked(id));
    }

    /** Service NPCs say one line of the story before opening their menu. */
    static void serviceLine(ServerPlayer player, NpcRole role) {
        ServerContext.current().ifPresent(context -> context.game().story().catalog()
                .dialogue(new StoryTrigger.NpcTalk(role.id(), context.game().story().progress()))
                .ifPresent(dialogue -> player.sendSystemMessage(
                        com.eternalrealm.story.forge.StoryDirector.format(dialogue.first()))));
    }
}
