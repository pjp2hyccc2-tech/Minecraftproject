package com.eternalrealm.quest.forge;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.npc.forge.NpcWhereabouts;
import com.eternalrealm.quest.Npcs;
import com.eternalrealm.quest.QuestDefinition;
import com.eternalrealm.quest.QuestGuide;
import com.eternalrealm.quest.QuestLogState;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.guide.AstoriaPlaces;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The quest guide (クエストの道案内): for each player, where their guided quest wants them next ({@link QuestGuide} over
 * {@link AstoriaPlaces}), sent on UI channel {@value #CHANNEL} when it changes; the client walks the yellow dots there.
 * The player picks the quest in the journal ({@code quest.guide}); by default the main quest is guided. Server thread
 * only.
 */
final class QuestGuides {
    static final String CHANNEL = "quest_guide";
    /** The player switched the guide off. */
    static final String OFF = "-";

    private final QuestService quests;
    private final MinecraftServer server;
    private final AstoriaPlaces places = new AstoriaPlaces(NpcWhereabouts::of);
    private final Map<UUID, String> chosen = new HashMap<>();
    private final Map<UUID, String> sent = new HashMap<>();

    QuestGuides(QuestService quests, MinecraftServer server) {
        this.quests = quests;
        this.server = server;
    }

    /** The player chose the quest to guide ("" for the default, {@link #OFF} for none). */
    void choose(ServerPlayer player, String questId) {
        if (questId.isEmpty()) {
            chosen.remove(player.getUUID());
        } else {
            chosen.put(player.getUUID(), questId);
        }
        send(player, true);
    }

    /** The quest the player's guide follows ("" when none). */
    String guidedQuest(ServerPlayer player) {
        QuestLogState state = quests.log(player);
        String choice = chosen.get(player.getUUID());
        if (state == null || OFF.equals(choice)) {
            return "";
        }
        return QuestGuide.guided(state.activeQuestIds(), choice, quests.engine().catalog()).orElse("");
    }

    void tick() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, false);
        }
    }

    void onLogout(UUID playerId) {
        chosen.remove(playerId);
        sent.remove(playerId);
    }

    private void send(ServerPlayer player, boolean force) {
        JsonObject data = view(player);
        String text = data.toString();
        if (!force && text.equals(sent.get(player.getUUID()))) {
            return;
        }
        sent.put(player.getUUID(), text);
        UiData.send(player, CHANNEL, data);
    }

    private JsonObject view(ServerPlayer player) {
        JsonObject data = new JsonObject();
        String guided = guidedQuest(player);
        data.addProperty("off", OFF.equals(chosen.get(player.getUUID())));
        data.addProperty("quest", guided);
        data.addProperty("active", false);
        QuestLogState state = quests.log(player);
        if (guided.isEmpty() || state == null || !AstoriaTravel.isInAstoria(player)) {
            return data;
        }
        QuestDefinition quest = quests.engine().catalog().byId(guided).orElse(null);
        if (quest == null) {
            return data;
        }
        Optional<QuestGuide.Target> target = QuestGuide.target(quest, state.progress(guided),
                quests.engine().isReadyToTurnIn(state, guided), new QuestGuide.Point(player.getX(), player.getZ()),
                places);
        if (target.isEmpty()) {
            return data;
        }
        QuestGuide.Target found = target.get();
        data.addProperty("active", true);
        data.addProperty("kind", found.kind().name().toLowerCase(java.util.Locale.ROOT));
        // whole blocks: a target that moves a little (where a giver was last seen) does not resend every second
        data.addProperty("x", Math.floor(found.point().x()) + 0.5);
        data.addProperty("z", Math.floor(found.point().z()) + 0.5);
        data.addProperty("dim", player.level().dimension().location().toString());
        data.addProperty("label", Component.Serializer.toJson(label(found)));
        return data;
    }

    private static Component label(QuestGuide.Target target) {
        return switch (target.kind()) {
            case TALK -> Component.translatable("guide.eternalrealm.talk", npc(target.subject()));
            case TURN_IN -> Component.translatable("guide.eternalrealm.turn_in", npc(target.subject()));
            case AREA -> Component.translatable("guide.eternalrealm.area",
                    Component.translatable(area(target.areaId())));
            case HUNT -> com.eternalrealm.quest.Objective.KillMonsters.ANY.equals(target.subject())
                    ? Component.translatable("guide.eternalrealm.hunt_any", Component.translatable(area(target.areaId())))
                    : Component.translatable("guide.eternalrealm.hunt", Component.translatable(Monsters.byId(
                    target.subject()).map(t -> t.translationKey()).orElse("monster.eternalrealm." + target.subject())));
            case COLLECT -> Component.translatable("guide.eternalrealm.collect", item(target.subject()));
            case DUNGEON -> Component.translatable("guide.eternalrealm.dungeon", Component.translatable(
                    Dungeons.byId(target.subject()).map(d -> d.translationKey())
                            .orElse(Dungeons.FINAL_RAID.translationKey())));
            case BOSS -> Component.translatable("guide.eternalrealm.boss", Component.translatable(
                    Bosses.byId(target.subject()).map(b -> b.translationKey())
                            .orElse("boss.eternalrealm." + target.subject())));
        };
    }

    private static Component npc(String npcId) {
        return Component.translatable(Npcs.byId(npcId).map(n -> n.translationKey()).orElse("npc.eternalrealm." + npcId));
    }

    private static String area(int areaId) {
        return Areas.byId(areaId).map(AreaDefinition::translationKey).orElse("?");
    }

    private static Component item(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Component.literal(itemId) : Component.translatable(item.getDescriptionId());
    }
}
