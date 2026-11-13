package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.DifficultyTuning;
import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.dungeon.DungeonRewards;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.GateBoard;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.party.Party;
import com.eternalrealm.ranking.BossDifficulty;
import com.eternalrealm.ranking.TimeAttackRun;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.WorldProgress;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The gatekeeper (転移門の番人, and Gant in the capital): talking to one opens the 転移門 screen
 * ({@code ui.gate.GatekeeperScreen}) with the eight dungeons and the final raid, and the screen sends the party off.
 *
 * <p>Channel {@value #CHANNEL} carries the board ({@link GateBoard} plus the player's party, level and personal bests,
 * each boss's numbers by difficulty and the reward table); the action {@value #START} ({@code id}, {@code difficulty})
 * starts the run and channel {@value #RESULT_CHANNEL} answers it ({@code ok}, or the reason as a component).
 * Setting out is only accepted from a screen a gatekeeper opened within {@link GateBoard#SESSION_TICKS}, near where it
 * was opened; {@link DungeonService#start} checks everything else again. Server thread only.</p>
 */
public final class GatekeeperService {
    public static final String CHANNEL = "gatekeeper";
    public static final String RESULT_CHANNEL = "gatekeeper.result";
    public static final String START = "gate.start";
    private static final Map<UUID, GateBoard.Session> SESSIONS = new HashMap<>();

    private GatekeeperService() {
    }

    public static void register() {
        UiActions.register(START, (player, context, args) -> start(player, context, UiArgs.id(args, "id"),
                difficulty(UiArgs.id(args, "difficulty"))));
    }

    /** Opens the screen; {@code npc} is the gatekeeper spoken to (an entrance's own one opens on its dungeon). */
    public static void open(ServerPlayer player, Mob npc) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        remember(player);
        String entrance = npc != null && npc.getPersistentData().contains(Npcs.ENTRANCE)
                ? npc.getPersistentData().getString(Npcs.ENTRANCE) : null;
        ERNetwork.sendToPlayer(player, new UiDataPacket(CHANNEL, view(player, context, entrance).toString()));
        // the dungeon this player ran last starts being built while they choose
        context.game().dungeons().onGatekeeper(player);
    }

    /** The screen was opened for {@code player} here and now: setting out is accepted from it for a while. */
    public static void remember(ServerPlayer player) {
        long now = player.server.getTickCount();
        SESSIONS.values().removeIf(session -> session.expired(now));
        SESSIONS.put(player.getUUID(), new GateBoard.Session(now, player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ()));
    }

    /** The board as the player's screen gets it ({@code entrance}: the dungeon of the gatekeeper spoken to, or null). */
    public static JsonObject view(ServerPlayer player, ServerContext context, String entrance) {
        WorldProgress progress = context.game().world().progress();
        List<GateBoard.Entry> entries = GateBoard.entries(progress);
        Optional<Party> party = context.game().parties().service().partyOf(player.getUUID());
        boolean inParty = party.isPresent();
        boolean leader = party.map(p -> p.isLeader(player.getUUID())).orElse(true);
        int size = party.map(Party::size).orElse(1);
        boolean ignoreSize = ERServerConfig.IGNORE_PARTY_SIZE.get();
        int level = context.playerData().snapshot(player.getUUID()).map(snapshot -> snapshot.level()).orElse(1);
        String last = context.game().dungeons().lastRun(player.getUUID()).orElse(null);

        JsonObject data = new JsonObject();
        data.addProperty("t", System.nanoTime());
        data.addProperty("focus", GateBoard.focus(entries, entrance, last, level));
        data.addProperty("level", level);
        data.addProperty("inParty", inParty);
        data.addProperty("leader", leader);
        data.addProperty("partySize", size);
        data.addProperty("conquered", GateBoard.conqueredDungeons(progress));
        data.addProperty("dungeons", Areas.regularBossIds().size());
        JsonArray rewards = new JsonArray();
        for (Difficulty difficulty : Difficulty.values()) {
            ItemRarity floor = DungeonRewards.rarityFloor(difficulty);
            JsonObject reward = new JsonObject();
            reward.addProperty("rarity", floor.translationKey());
            reward.addProperty("color", floor.color());
            reward.addProperty("stones", DungeonRewards.stones(difficulty));
            rewards.add(reward);
        }
        data.add("rewards", rewards);
        JsonArray list = new JsonArray();
        for (GateBoard.Entry entry : entries) {
            JsonObject row = new JsonObject();
            row.addProperty("id", entry.id());
            row.addProperty("name", entry.nameKey());
            row.addProperty("area", Areas.byId(entry.areaId()).map(AreaDefinition::translationKey).orElse(""));
            Optional<BossDefinition> boss = Bosses.byId(entry.bossId());
            row.addProperty("boss", boss.map(BossDefinition::translationKey).orElse(""));
            row.addProperty("level", entry.recommendedLevel());
            row.addProperty("min", entry.minParty());
            row.addProperty("max", entry.maxParty());
            row.addProperty("minutes", Math.max(1, entry.timeLimitTicks() / (20 * 60)));
            row.addProperty("rooms", entry.rooms());
            row.addProperty("raid", entry.raid());
            row.addProperty("open", entry.open());
            row.addProperty("conquered", entry.conquered());
            row.addProperty("unlock", entry.unlockBossId() == null ? ""
                    : Bosses.byId(entry.unlockBossId()).map(BossDefinition::translationKey).orElse(""));
            row.addProperty("refusal", GateBoard.check(entry, inParty, leader, size, ignoreSize).id());
            JsonArray hp = new JsonArray();
            JsonArray damage = new JsonArray();
            JsonArray best = new JsonArray();
            for (Difficulty difficulty : Difficulty.values()) {
                DifficultyTuning tuning = boss.map(b -> b.tuning(difficulty)).orElse(DifficultyTuning.NORMAL);
                hp.add(tuning.healthMultiplier());
                damage.add(tuning.damageMultiplier());
                best.add(personalBest(context, player.getUUID(), entry.bossId(), difficulty));
            }
            row.add("hp", hp);
            row.add("dmg", damage);
            row.add("best", best);
            list.add(row);
        }
        data.add("entries", list);
        return data;
    }

    /** The player's fastest clear of this boss on this difficulty this season, in ticks (-1: none yet). */
    private static long personalBest(ServerContext context, UUID playerId, String bossId, Difficulty difficulty) {
        if (context.game().rankings() == null || context.game().rankings().board() == null) {
            return -1;
        }
        Optional<BossDifficulty> ranked = BossDifficulty.byId(difficulty.id());
        return ranked.flatMap(d -> context.game().rankings().board().timeAttack().personalBest(playerId, bossId, d))
                .map(TimeAttackRun::durationTicks).orElse(-1L);
    }

    static Difficulty difficulty(String id) {
        return Difficulty.byId(id).orElseThrow(() -> new UiArgs.InvalidArgumentException("unknown difficulty " + id));
    }

    static void start(ServerPlayer player, ServerContext context, String id, Difficulty difficulty) {
        result(player, setOut(player, context, id, difficulty));
    }

    /**
     * Sends the player's party off to {@code id} if their screen is still good for it ({@link GateBoard.Session}).
     *
     * @return why not, or empty once the run is being prepared
     * @throws UiArgs.InvalidArgumentException for an id that is no dungeon (only a tampered client sends one)
     */
    public static Optional<Component> setOut(ServerPlayer player, ServerContext context, String id,
                                             Difficulty difficulty) {
        if (Dungeons.byId(id).isEmpty() && !Dungeons.FINAL_RAID.id().equals(id)) {
            throw new UiArgs.InvalidArgumentException("unknown dungeon " + id);
        }
        GateBoard.Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.allows(player.server.getTickCount(),
                player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ())) {
            SESSIONS.remove(player.getUUID());
            return Optional.of(Component.translatable("message.eternalrealm.gate.too_far"));
        }
        Optional<Component> refusal = context.game().dungeons().start(player, id, difficulty);
        if (refusal.isEmpty()) {
            SESSIONS.remove(player.getUUID());
        }
        return refusal;
    }

    private static void result(ServerPlayer player, Optional<Component> refusal) {
        JsonObject data = new JsonObject();
        data.addProperty("t", System.nanoTime());
        data.addProperty("ok", refusal.isEmpty());
        refusal.ifPresent(message -> data.addProperty("message", Component.Serializer.toJson(message)));
        ERNetwork.sendToPlayer(player, new UiDataPacket(RESULT_CHANNEL, data.toString()));
    }

    /** Forgets a player's open screen (logout). */
    public static void forget(UUID playerId) {
        SESSIONS.remove(playerId);
    }
}
