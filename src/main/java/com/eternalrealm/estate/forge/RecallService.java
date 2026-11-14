package com.eternalrealm.estate.forge;

import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.combat.forge.PvpRules;
import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.RecallRules;
import com.eternalrealm.revival.forge.RevivalService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Going home to one's room, or to the royal capital, from anywhere (docs/modules/estates.md, task #42; the capital
 * 2026-11-14: 「どこからでも王国へテレポートできるようにして」): the journal's buttons, the map's button or the keys
 * (Home: the room, End: the capital) start a cast of {@value RecallRules#CAST_TICKS} ticks; moving, taking damage or
 * getting into a fight breaks it. It is refused in a fight, in a dungeon or raid, in a boss fight, in a PvP match,
 * while fallen, when already there, for the room with no room (or one still being prepared), and for
 * {@value RecallRules#COOLDOWN_TICKS} ticks after the last arrival at the same place. On arrival in the room the way
 * back is the room's door, as for anyone who came in through the inn; the capital is reached at its arrival point.
 * The rules are {@link RecallRules}; this class gathers the facts and moves the player. Server thread.
 */
public final class RecallService {
    /** A cast in progress; the player object itself (a player who logs out and in again is a new one). */
    private record Cast(ServerPlayer player, RecallRules.Destination to, long started, Vec3 start, float health,
                        ResourceKey<Level> dimension) {
    }

    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    /**
     * Server tick of each player's last arrival at each place (the cooldown counts from it). Forgotten when the
     * server stops: the tick counter starts again at zero when a world is opened again in the same game.
     */
    private static final Map<RecallRules.Destination, Map<UUID, Long>> ARRIVALS =
            new EnumMap<>(RecallRules.Destination.class);
    /** How far another player who could hurt this one may stand for it to count as a PvP match (blocks). */
    private static final double PVP_RANGE = 48.0;

    private RecallService() {
    }

    static void register() {
        UiActions.register(RecallRules.ACTION, (player, context, args) -> {
            if (UiArgs.flag(args, "cancel")) {
                interrupt(player, RecallRules.Interrupt.CANCELLED);
            } else {
                start(player, RecallRules.Destination.byId(args.has("to") ? UiArgs.id(args, "to") : ""));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(RecallService::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            CASTS.remove(event.getEntity().getUUID());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && CASTS.containsKey(player.getUUID())) {
                interrupt(player, RecallRules.Interrupt.MOVED);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            CASTS.clear();
            ARRIVALS.clear();
        });
    }

    private static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    /** What the rules need to know about the way home to {@code player}'s room right now. */
    public static RecallRules.Situation situation(ServerPlayer player) {
        return situation(player, RecallRules.Destination.ROOM);
    }

    /** What the rules need to know about a trip of {@code player}'s to {@code to} right now. */
    public static RecallRules.Situation situation(ServerPlayer player, RecallRules.Destination to) {
        UUID id = player.getUUID();
        Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, id);
        boolean there = to == RecallRules.Destination.ROOM
                ? room.isPresent() && EstateService.at(player).filter(room.get()::sameEstate).isPresent()
                : nearCapital(player);
        Optional<ServerContext> context = ServerContext.current();
        boolean combat = context.map(c -> c.combat().inCombat(player)).orElse(false);
        boolean dungeon = DungeonLevels.isDungeonLevel(player.level())
                || context.map(c -> c.game().dungeons().isInDungeon(id)).orElse(false);
        boolean boss = !BossFight.fightsOf(id).isEmpty();
        boolean pvp = false;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.distanceToSqr(player) < PVP_RANGE * PVP_RANGE
                    && (PvpRules.canHurt(other, player) || PvpRules.canHurt(player, other))) {
                pvp = true;
                break;
            }
        }
        long cooldown = RecallRules.cooldownLeft(now(player.server), arrivals(to).getOrDefault(id, -1L));
        return new RecallRules.Situation(to, room.isPresent(), room.map(EstateService::ready).orElse(false), there,
                combat, dungeon, boss, pvp, RevivalService.isDown(player), CASTS.containsKey(id), cooldown);
    }

    private static Map<UUID, Long> arrivals(RecallRules.Destination to) {
        return ARRIVALS.computeIfAbsent(to, key -> new HashMap<>());
    }

    /** Standing on the capital's plaza already (within {@value RecallRules#CAPITAL_NEAR} blocks of its arrival). */
    private static boolean nearCapital(ServerPlayer player) {
        if (!AstoriaTravel.isInAstoria(player)) {
            return false;
        }
        BlockPos arrival = Capital.layout(player.serverLevel()).spawnPoint();
        return player.distanceToSqr(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5)
                < RecallRules.CAPITAL_NEAR * RecallRules.CAPITAL_NEAR;
    }

    /** Starts the way home to one's room (or says why not). */
    public static RecallRules.Check start(ServerPlayer player) {
        return start(player, RecallRules.Destination.ROOM);
    }

    /** Starts the trip to {@code to} (or says why not). */
    public static RecallRules.Check start(ServerPlayer player, RecallRules.Destination to) {
        RecallRules.Situation situation = situation(player, to);
        RecallRules.Check check = RecallRules.check(situation);
        if (!check.ok()) {
            player.displayClientMessage(Component.translatable(check.messageKey(),
                    (situation.cooldownLeft() + 19) / 20).withStyle(ChatFormatting.RED), true);
            state(player, to, "refused", check.id());
            return check;
        }
        CASTS.put(player.getUUID(), new Cast(player, to, now(player.server), player.position(), player.getHealth(),
                player.level().dimension()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, to == RecallRules.Destination.ROOM ? 0.8F : 1.1F);
        state(player, to, "cast", "");
        return check;
    }

    /** Whether the player is on the way home right now. */
    public static boolean isCasting(ServerPlayer player) {
        return CASTS.containsKey(player.getUUID());
    }

    /** Breaks the player's cast, if any. */
    public static void interrupt(ServerPlayer player, RecallRules.Interrupt why) {
        Cast cast = CASTS.remove(player.getUUID());
        if (cast == null) {
            return;
        }
        player.displayClientMessage(Component.translatable(why.messageKey()).withStyle(ChatFormatting.RED), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 0.7F, 0.7F);
        state(player, cast.to(), "broken", why.id());
    }

    /** For tests: forgets the player's cooldowns. */
    public static void resetCooldown(UUID player) {
        for (Map<UUID, Long> arrivals : ARRIVALS.values()) {
            arrivals.remove(player);
        }
    }

    private static void state(ServerPlayer player, RecallRules.Destination to, String state, String reason) {
        JsonObject data = new JsonObject();
        data.addProperty("state", state);
        data.addProperty("to", to.id());
        data.addProperty("reason", reason);
        data.addProperty("ticks", RecallRules.CAST_TICKS);
        data.addProperty("nonce", System.nanoTime());
        UiData.send(player, RecallRules.CHANNEL, data);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CASTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = now(server);
        Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cast> entry = it.next();
            Cast cast = entry.getValue();
            ServerPlayer player = cast.player();
            if (player.isRemoved() || !player.isAlive() || player.hasDisconnected()) {
                it.remove();
                continue;
            }
            RecallRules.Interrupt broken = null;
            Vec3 drift = player.position().subtract(cast.start());
            if (player.level().dimension() != cast.dimension() || RecallRules.moved(drift.x, drift.y, drift.z)) {
                broken = RecallRules.Interrupt.MOVED;
            } else if (player.getHealth() < cast.health() || player.hurtTime > 0 && now > cast.started()) {
                broken = RecallRules.Interrupt.HURT;
            } else if (ServerContext.current().map(c -> c.combat().inCombat(player)).orElse(false)
                    || RevivalService.isDown(player)) {
                broken = RecallRules.Interrupt.COMBAT;
            }
            if (broken != null) {
                it.remove();
                CASTS.put(entry.getKey(), cast); // interrupt() removes it and tells the player
                interrupt(player, broken);
                return; // the map changed: the others are looked at next tick
            }
            if ((now - cast.started()) % 4 == 0) {
                swirl(player.serverLevel(), player, RecallRules.progress(cast.started(), now));
            }
            if (RecallRules.complete(cast.started(), now)) {
                it.remove();
                arrive(player, cast.to(), now);
                return; // entering the room changes the players' levels
            }
        }
    }

    /** Amber motes rising and circling closer as the cast goes on. */
    private static void swirl(ServerLevel level, ServerPlayer player, double progress) {
        double radius = 1.1 - 0.6 * progress;
        for (int i = 0; i < 3; i++) {
            double angle = (level.getGameTime() * 0.35) + i * (Math.PI * 2.0 / 3.0);
            level.sendParticles(ParticleTypes.WAX_ON, player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.2 + progress * 1.6, player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0,
                    0.0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.5, 0.6,
                0.5, 0.6);
    }

    private static void arrive(ServerPlayer player, RecallRules.Destination to, long now) {
        if (to == RecallRules.Destination.CAPITAL) {
            arriveInCapital(player, now);
            return;
        }
        Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, player.getUUID());
        if (room.isEmpty() || !EstateService.ready(room.get())) {
            CASTS.put(player.getUUID(), new Cast(player, to, now, player.position(), player.getHealth(),
                    player.level().dimension()));
            interrupt(player, RecallRules.Interrupt.REFUSED);
            return;
        }
        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.7, 0.3,
                0.04);
        if (EstateService.enter(player, room.get()) == EstateResult.OK) {
            arrivals(to).put(player.getUUID(), now);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
            state(player, to, "done", "");
        } else {
            state(player, to, "broken", RecallRules.Interrupt.REFUSED.id());
        }
    }

    /** To the capital's arrival point, from the field, a room or a guild house. */
    private static void arriveInCapital(ServerPlayer player, long now) {
        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.7, 0.3,
                0.04);
        if (EstateLevels.isEstateLevel(player.level())) {
            EstateReturns.clear(player); // the capital is where the door would have led anyway
        }
        player.stopRiding();
        player.closeContainer();
        if (!AstoriaTravel.sendToHub(player)) {
            state(player, RecallRules.Destination.CAPITAL, "broken", "refused_capital");
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        arrivals(RecallRules.Destination.CAPITAL).put(player.getUUID(), now);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.9,
                0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        state(player, RecallRules.Destination.CAPITAL, "done", "");
    }
}
