package com.eternalrealm.world.forge;

import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.npc.Townsfolk;
import com.eternalrealm.npc.forge.Crowds;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.npc.forge.StoryNpcs;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.Speaker;
import com.eternalrealm.story.StoryNpc;
import com.eternalrealm.world.gen.HubSpot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Puts the game into アストリア: the capital is built when the server starts and its NPCs stand at their spots (the
 * story characters around the plaza, a townsperson at every {@code crowd.*} spot and a guard at every {@code guard.*}
 * spot of the build), every dungeon entrance gets its gatekeeper and the area's quest giver once the entrance is built,
 * players arrive in the capital when they log in outside the game's dimensions and respawn there, and the return scroll
 * leads home. Without the dimension nothing happens (see {@code /eternalrealm setup town}).
 */
public final class RealmSetup {
    private static final Map<NpcRole, HubSpot> SPOTS = new EnumMap<>(NpcRole.class);
    /** The dungeon entrance where each area's quest giver waits (Nordia's at the prison, d4). */
    private static final Map<String, String> GIVER_AT = Map.of("elder_forest_sage", "d2", "sandria_caravan_master", "d3",
            "nordia_chieftain", "d4", "dragonia_blacksmith", "d6", "celestia_oracle", "d7", "tower_watcher", "d8");
    private static final List<StoryNpc> CHARACTERS = List.of(StoryNpc.LILIA, StoryNpc.GUSTAV, StoryNpc.NOX,
            StoryNpc.LASZLO, StoryNpc.WOLFRAM);
    private static final Set<String> ENTRANCES_DONE = new HashSet<>();

    static {
        SPOTS.put(NpcRole.QUEST_BOARD, HubSpot.QUEST_BOARD);
        SPOTS.put(NpcRole.ITEM_SHOP, HubSpot.ITEM_SHOP);
        SPOTS.put(NpcRole.BLACKSMITH, HubSpot.BLACKSMITH);
        SPOTS.put(NpcRole.JOB_MASTER, HubSpot.JOB_MASTER);
        SPOTS.put(NpcRole.AUCTION, HubSpot.AUCTION_HOUSE);
        SPOTS.put(NpcRole.MAILBOX, HubSpot.MAILBOX);
        SPOTS.put(NpcRole.GUILD_REGISTRAR, HubSpot.GUILD_REGISTRAR);
        SPOTS.put(NpcRole.GATEKEEPER, HubSpot.GATEKEEPER);
        SPOTS.put(NpcRole.ARENA_MASTER, HubSpot.ARENA_MASTER);
        SPOTS.put(NpcRole.HALL_OF_FAME, HubSpot.HALL_OF_FAME);
    }

    private RealmSetup() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ENTRANCES_DONE.clear();
        ConsumableItem.setHomeTeleport(player -> AstoriaTravel.sendToHub(player) || teleportToRespawn(player));
        AstoriaTravel.astoria(event.getServer()).ifPresent(RealmSetup::populateCapital);
    }

    private static boolean teleportToRespawn(ServerPlayer player) {
        ServerLevel level = player.server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        return true;
    }

    /**
     * The blueprint capital finished building: the NPCs standing in the old town are removed and everyone is placed
     * at the new spots.
     */
    public static void onCapitalCompleted(ServerLevel astoria) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(-Realm.CAPITAL_HALF_X, astoria.getMinBuildHeight(),
                Realm.CAPITAL_NORTH, Realm.CAPITAL_HALF_X + 1, astoria.getMaxBuildHeight(), Realm.CAPITAL_SOUTH + 1);
        for (net.minecraft.world.entity.Mob npc : astoria.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area,
                mob -> Npcs.roleOf(mob).isPresent())) {
            npc.discard();
        }
        Crowds.removeAll(astoria, area);
        populateCapital(astoria);
    }

    /** Builds the capital (if needed) and places its NPCs (once the capital stands completely). */
    static void populateCapital(ServerLevel astoria) {
        HubLayout layout = Capital.layout(astoria);
        if (!Capital.ready(astoria)) {
            return; // the blueprint capital is still being built: onCapitalCompleted places everyone
        }
        for (Map.Entry<NpcRole, HubSpot> entry : SPOTS.entrySet()) {
            NpcRole role = entry.getKey();
            BlockPos pos = layout.position(entry.getValue());
            Npcs.ensure(astoria, pos, role, layout.yaw(entry.getValue())).ifPresent(npc ->
                    StoryNpc.byId(role.id()).map(StoryNpc::speaker).map(Speaker::key).ifPresent(key ->
                            npc.setCustomName(Component.translatable(key).withStyle(ChatFormatting.GOLD))));
        }
        BlockPos plaza = layout.spawnPoint();
        for (int i = 0; i < CHARACTERS.size(); i++) {
            StoryNpc character = CHARACTERS.get(i);
            double angle = Math.PI * (0.25 + 0.375 * i);
            BlockPos fallback = surface(astoria, plaza.getX() + (int) Math.round(Math.cos(angle) * 6.0),
                    plaza.getZ() + (int) Math.round(Math.sin(angle) * 6.0));
            String spot = "story." + character.id();
            BlockPos pos = layout.named(spot).orElse(fallback);
            StoryNpcs.ensure(astoria, pos, character.id(), Component.translatable(character.speaker().key()),
                    layout.namedYaw(spot, yawTowards(pos, plaza)));
        }
        com.eternalrealm.quest.Npcs.byId("arcelia_knight_captain").ifPresent(captain -> {
            BlockPos board = layout.questBoard();
            BlockPos fallback = surface(astoria, board.getX() + 3, board.getZ() + 2);
            String spot = "story.arcelia_knight_captain";
            BlockPos pos = layout.named(spot).orElse(fallback);
            StoryNpcs.ensure(astoria, pos, captain, layout.namedYaw(spot, yawTowards(pos, plaza)));
        });
        Crowds.populate(astoria, crowdSpots(layout));
    }

    /**
     * The townsfolk, guard and resident spots of the capital build ({@code crowd.<area>.<n>}, {@code guard.<post>.<n>},
     * {@code castle.king}); the castle's own gate-guard spots count as guard posts unless a {@code guard.*} spot stands
     * within two blocks.
     */
    static List<Crowds.Spot> crowdSpots(HubLayout layout) {
        List<Crowds.Spot> spots = new ArrayList<>();
        for (String prefix : List.of(Townsfolk.CROWD_PREFIX, Townsfolk.GUARD_PREFIX)) {
            for (Map.Entry<String, BlockPos> entry : layout.namedWithPrefix(prefix).entrySet()) {
                spots.add(new Crowds.Spot(entry.getKey(), entry.getValue(), layout.namedYaw(entry.getKey(), 0.0F)));
            }
        }
        for (String resident : Townsfolk.residentSpots()) {
            layout.named(resident).ifPresent(pos -> spots.add(new Crowds.Spot(resident, pos, layout.namedYaw(resident, 0.0F))));
        }
        List<BlockPos> guards = new ArrayList<>(layout.namedWithPrefix(Townsfolk.GUARD_PREFIX).values());
        for (Map.Entry<String, BlockPos> entry : layout.namedWithPrefix(Townsfolk.CASTLE_GATE_GUARD_PREFIX).entrySet()) {
            boolean taken = guards.stream().anyMatch(pos -> pos.distSqr(entry.getValue()) <= 4.0);
            if (!taken) {
                spots.add(new Crowds.Spot(entry.getKey(), entry.getValue(), layout.namedYaw(entry.getKey(), 0.0F)));
            }
        }
        return spots;
    }

    /** Once per second: entrances that have been built get their gatekeeper and quest giver. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 11) {
            return;
        }
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(event.getServer());
        if (astoria.isEmpty()) {
            return;
        }
        for (String dungeonId : Landmarks.dungeonIds()) {
            if (ENTRANCES_DONE.contains(dungeonId) || !LandmarkBuilder.isBuilt(astoria.get(), dungeonId)) {
                continue;
            }
            ENTRANCES_DONE.add(dungeonId);
            BlockPos npc = Landmarks.npc(astoria.get(), dungeonId);
            float yaw = Landmarks.npcYaw(dungeonId);
            Npcs.ensure(astoria.get(), npc, NpcRole.GATEKEEPER, yaw, Npcs.ENTRANCE, dungeonId);
            for (Map.Entry<String, String> giver : GIVER_AT.entrySet()) {
                if (giver.getValue().equals(dungeonId)) {
                    Optional<NpcDefinition> definition = com.eternalrealm.quest.Npcs.byId(giver.getKey());
                    BlockPos pos = surface(astoria.get(), npc.getX() + 3, npc.getZ());
                    definition.ifPresent(found -> StoryNpcs.ensure(astoria.get(), pos, found, yaw));
                }
            }
        }
    }

    /** Players who log in outside the game's dimensions arrive in the capital. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !AstoriaTravel.isInAstoria(player)
                && !DungeonLevels.isDungeonLevel(player.level())) {
            AstoriaTravel.sendToHub(player);
        }
    }

    /** After death: back in the capital (members of a running dungeon go back into it instead). */
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        boolean inDungeon = ServerContext.current().map(context -> context.game().dungeons().isInDungeon(player.getUUID()))
                .orElse(false);
        if (inDungeon) {
            return;
        }
        MinecraftServer server = player.server;
        AstoriaTravel.astoria(server).ifPresent(astoria -> {
            HubLayout layout = Capital.layout(astoria);
            AstoriaTravel.teleport(player, astoria, layout.respawnPoint(), layout.yaw(HubSpot.RESPAWN));
        });
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static float yawTowards(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
