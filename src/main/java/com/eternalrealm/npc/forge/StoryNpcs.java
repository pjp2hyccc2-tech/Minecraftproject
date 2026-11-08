package com.eternalrealm.npc.forge;

import com.eternalrealm.quest.GameEvent;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

/**
 * The story characters of the quest module ({@link com.eternalrealm.quest.Npcs}): STORY-role NPCs that also carry their
 * character id. Talking to one counts for "talk to" objectives and shows the character's line.
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

    /** Places (or moves back) the story character at {@code pos}. */
    public static Optional<Villager> ensure(ServerLevel level, BlockPos pos, NpcDefinition character, float yaw) {
        Optional<Villager> npc = Npcs.ensure(level, pos, NpcRole.STORY, yaw, CHARACTER, character.id());
        npc.ifPresent(villager -> villager.setCustomName(Component.translatable(character.translationKey())
                .withStyle(ChatFormatting.AQUA)));
        return npc;
    }

    static void talk(ServerPlayer player, Villager npc) {
        String id = characterOf(npc).orElse("");
        player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.VILLAGER_AMBIENT,
                SoundSource.NEUTRAL, 0.6F, 1.0F);
        if (id.isEmpty()) {
            player.sendSystemMessage(Component.translatable("npc.eternalrealm.greeting", npc.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        player.sendSystemMessage(Component.literal("[").append(npc.getDisplayName()).append("] ")
                .append(Component.translatable("dialogue.eternalrealm." + id + ".greeting"))
                .withStyle(ChatFormatting.WHITE));
        ServerContext.current().ifPresent(context -> context.game().quests().event(player, new GameEvent.NpcTalked(id)));
    }
}
