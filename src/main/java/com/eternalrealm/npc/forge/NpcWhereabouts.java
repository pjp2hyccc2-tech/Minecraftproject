package com.eternalrealm.npc.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.quest.QuestGuide;
import com.eternalrealm.world.forge.AstoriaTravel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the story characters and quest givers of アストリア were last seen standing (placed by the setup, or loaded
 * with their chunk), for the quest guide's yellow dots ({@code QuestGuides}). Only positions in アストリア are kept.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcWhereabouts {
    private static final Map<String, QuestGuide.Point> SEEN = new ConcurrentHashMap<>();

    private NpcWhereabouts() {
    }

    /** Where the character {@code id} was last seen in アストリア. */
    public static Optional<QuestGuide.Point> of(String id) {
        return Optional.ofNullable(SEEN.get(id));
    }

    static void saw(Mob npc) {
        if (!AstoriaTravel.isInAstoria(npc)) {
            return;
        }
        StoryNpcs.characterOf(npc).ifPresent(id -> SEEN.put(id, new QuestGuide.Point(npc.getX(), npc.getZ())));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) {
            saw(mob);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SEEN.clear();
    }
}
