package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.npc.forge.StoryNpcs;
import com.eternalrealm.party.Party;
import com.eternalrealm.quest.forge.QuestService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.WorldProgress;
import com.eternalrealm.world.map.MapView;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sends every player the map data the client cannot know by itself ({@link MapView} on the {@code map} UI channel),
 * once a second and only when it changed: the realm, the open and the discovered areas, the positions of the other
 * party members, the town NPCs near the player with their quest marks, and the dungeon instance the player is in.
 * Server thread only; the state belongs to the running server.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MapSync {
    static final int INTERVAL_TICKS = 20;
    /** NPCs this close to the player (horizontally) are on the map. */
    static final double NPC_RANGE = 96.0;
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<UUID, String> LAST_SENT = new HashMap<>();
    private static MinecraftServer owner;
    private static boolean warned;

    private MapSync() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null || server.getTickCount() % INTERVAL_TICKS != 7) {
            return;
        }
        if (owner != server) {
            owner = server; // a new world in the same game: nothing was sent to anyone yet
            LAST_SENT.clear();
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || context.server() != server) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            try {
                String json = build(context, player).toJson().toString();
                if (json.length() <= UiDataPacket.MAX_JSON && !json.equals(LAST_SENT.get(player.getUUID()))) {
                    LAST_SENT.put(player.getUUID(), json);
                    ERNetwork.sendToPlayer(player, new UiDataPacket(MapView.CHANNEL, json));
                }
            } catch (RuntimeException e) {
                if (!warned) {
                    warned = true;
                    LOG.warn("Could not build the map data of {}", player.getGameProfile().getName(), e);
                }
            }
        }
        LAST_SENT.keySet().retainAll(online);
    }

    /** A player who comes back gets everything again (their client forgot it when they left). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    /** The map document of one player. */
    public static MapView build(ServerContext context, ServerPlayer player) {
        MinecraftServer server = context.server();
        ServerLevel realm = Realm.level(server);
        BlockPos center = Realm.center(realm);
        MapView.Realm realmView = new MapView.Realm(realm.dimension().location().toString(), center.getX(),
                center.getZ(), Realm.hasDimension(server));
        WorldProgress progress = context.game().world().progress();
        return new MapView(realmView, progress.unlockedAreaIds(),
                context.game().world().discovery(player.getUUID()).areaIds(), progress.finalBossUnlocked(),
                party(context, player), player.level() == realm ? npcs(context, realm, player) : List.of(),
                context.game().dungeons().mapView(player.getUUID()));
    }

    private static List<MapView.Member> party(ServerContext context, ServerPlayer player) {
        Optional<Party> party = context.game().parties().service().partyOf(player.getUUID());
        if (party.isEmpty()) {
            return List.of();
        }
        List<MapView.Member> members = new ArrayList<>();
        for (UUID id : party.get().memberIds()) {
            ServerPlayer member = context.server().getPlayerList().getPlayer(id);
            if (member == null || id.equals(player.getUUID()) || members.size() >= MapView.MAX_MEMBERS) {
                continue;
            }
            members.add(new MapView.Member(id, member.getGameProfile().getName(),
                    member.level().dimension().location().toString(), member.getX(), member.getY(), member.getZ(),
                    member.getYRot(), member.getMaxHealth() > 0.0F ? member.getHealth() / member.getMaxHealth() : 0.0F,
                    party.get().isLeader(id)));
        }
        return members;
    }

    private static List<MapView.Npc> npcs(ServerContext context, ServerLevel realm, ServerPlayer player) {
        List<Mob> found = realm.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NPC_RANGE, 48.0, NPC_RANGE),
                mob -> Npcs.roleOf(mob).isPresent());
        found.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        QuestService quests = context.game().quests();
        List<MapView.Npc> npcs = new ArrayList<>();
        for (Mob mob : found) {
            if (npcs.size() >= MapView.MAX_NPCS) {
                break;
            }
            NpcRole role = Npcs.roleOf(mob).orElse(NpcRole.STORY);
            MapView.Mark mark = MapView.Mark.NONE;
            Optional<String> character = StoryNpcs.characterOf(mob);
            if (character.isPresent()) {
                String id = character.get();
                if (!quests.readyQuestsOfGiver(player, id).isEmpty()) {
                    mark = MapView.Mark.READY;
                } else if (quests.questWaitingForTalk(player, id).isPresent()
                        || quests.nextQuestOfGiver(player, id).isPresent()) {
                    mark = MapView.Mark.OFFER;
                }
            }
            Component name = mob.getCustomName() != null ? mob.getCustomName()
                    : Component.translatable(role.translationKey());
            npcs.add(new MapView.Npc(mob.getX(), mob.getY(), mob.getZ(), role.id(), Component.Serializer.toJson(name),
                    mark));
        }
        return npcs;
    }
}
