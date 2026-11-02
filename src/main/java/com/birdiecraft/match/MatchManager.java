package com.birdiecraft.match;

import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.course.Course;
import com.birdiecraft.golf.engine.BallPhysics;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.ShotInput;
import com.birdiecraft.golf.engine.ShotLaunch;
import com.birdiecraft.golf.engine.Trajectory;
import com.birdiecraft.golf.engine.Wind;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.network.S2CBallFlightPacket;
import com.birdiecraft.network.S2CMessagePacket;
import com.birdiecraft.world.LevelTerrain;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Owns every running match on the server. */
public final class MatchManager {
    private static final Map<Integer, GolfMatch> MATCHES = new LinkedHashMap<>();
    private static final Map<UUID, Integer> PLAYER_MATCH = new HashMap<>();
    private static final Map<UUID, Long> PRACTICE_READY = new HashMap<>();
    private static final Random RNG = new Random();
    private static MinecraftServer server;
    private static int nextId = 1;
    private static long serverTicks;

    private MatchManager() {
    }

    public static void onServerStart(MinecraftServer s) {
        server = s;
        MATCHES.clear();
        PLAYER_MATCH.clear();
        PRACTICE_READY.clear();
    }

    public static void onServerStop() {
        for (GolfMatch m : MATCHES.values()) m.dispose();
        MATCHES.clear();
        PLAYER_MATCH.clear();
        server = null;
    }

    public static void tick() {
        serverTicks++;
        Iterator<GolfMatch> it = MATCHES.values().iterator();
        while (it.hasNext()) {
            GolfMatch m = it.next();
            try {
                m.tick();
            } catch (Exception e) {
                com.birdiecraft.BirdieCraft.LOGGER.error("Golf match {} crashed, closing it", m.id, e);
                m.dispose();
            }
            if (m.isDisposed()) {
                m.dispose();
                PLAYER_MATCH.values().removeIf(id -> id == m.id);
                it.remove();
            }
        }
    }

    public static GolfMatch matchOf(ServerPlayer player) {
        Integer id = PLAYER_MATCH.get(player.getUUID());
        return id == null ? null : MATCHES.get(id);
    }

    public static List<GolfMatch> openLobbies() {
        List<GolfMatch> list = new ArrayList<>();
        for (GolfMatch m : MATCHES.values()) {
            if (m.mode == MatchMode.MULTI && m.state == MatchState.LOBBY) list.add(m);
        }
        return list;
    }

    public static GolfMatch create(ServerPlayer host, MatchMode mode, Course course, int startHole, int holeCount,
                                   int cpus, Difficulty difficulty) {
        leave(host);
        if (course.holes.isEmpty()) {
            host.sendSystemMessage(Component.translatable("msg.birdiecraft.course_empty").withStyle(ChatFormatting.RED));
            return null;
        }
        GolfMatch m = new GolfMatch(nextId++, server, mode, course, startHole, holeCount);
        m.host = host.getUUID();
        m.addPlayer(host);
        int max = GolfConfig.MAX_PLAYERS_PER_MATCH.get();
        int cpuCount = mode == MatchMode.SINGLE ? 0 : Math.max(0, Math.min(cpus, max - 1));
        if (mode == MatchMode.VS_CPU) cpuCount = Math.max(1, cpuCount);
        for (int i = 0; i < cpuCount; i++) m.addCpu(difficulty);
        MATCHES.put(m.id, m);
        PLAYER_MATCH.put(host.getUUID(), m.id);
        if (mode == MatchMode.MULTI) {
            host.sendSystemMessage(Component.translatable("msg.birdiecraft.lobby_created", course.title()).withStyle(ChatFormatting.GREEN));
            m.sync();
        } else {
            m.begin();
        }
        return m;
    }

    public static boolean join(ServerPlayer player, int matchId) {
        GolfMatch m = MATCHES.get(matchId);
        if (m == null || m.state != MatchState.LOBBY || m.mode != MatchMode.MULTI) {
            player.sendSystemMessage(Component.translatable("msg.birdiecraft.join_failed").withStyle(ChatFormatting.RED));
            return false;
        }
        if (m.participants.size() >= GolfConfig.MAX_PLAYERS_PER_MATCH.get()) {
            player.sendSystemMessage(Component.translatable("msg.birdiecraft.lobby_full").withStyle(ChatFormatting.RED));
            return false;
        }
        leave(player);
        m.addPlayer(player);
        PLAYER_MATCH.put(player.getUUID(), m.id);
        for (ServerPlayer sp : m.humans()) {
            sp.sendSystemMessage(Component.translatable("msg.birdiecraft.joined", player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW));
        }
        m.sync();
        return true;
    }

    public static void leave(ServerPlayer player) {
        Integer id = PLAYER_MATCH.remove(player.getUUID());
        if (id == null) return;
        GolfMatch m = MATCHES.get(id);
        if (m == null) return;
        boolean empty = m.removePlayer(player.getUUID());
        GolfNetwork.sendTo(player, new com.birdiecraft.network.S2CMatchStatePacket(null));
        if (empty) {
            m.dispose();
            MATCHES.remove(id);
        } else {
            for (ServerPlayer sp : m.humans()) {
                sp.sendSystemMessage(Component.translatable("msg.birdiecraft.left", player.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static void begin(ServerPlayer player) {
        GolfMatch m = matchOf(player);
        if (m == null || m.state != MatchState.LOBBY) return;
        if (!player.getUUID().equals(m.host)) {
            player.sendSystemMessage(Component.translatable("msg.birdiecraft.not_host").withStyle(ChatFormatting.RED));
            return;
        }
        m.begin();
    }

    public static void addCpu(ServerPlayer player, Difficulty difficulty) {
        GolfMatch m = matchOf(player);
        if (m == null || m.state != MatchState.LOBBY || !player.getUUID().equals(m.host)) return;
        if (m.participants.size() >= GolfConfig.MAX_PLAYERS_PER_MATCH.get()) return;
        m.addCpu(difficulty);
        m.sync();
    }

    public static void onLogout(ServerPlayer player) {
        leave(player);
        PRACTICE_READY.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ shots

    public static void handleShot(ServerPlayer player, ShotInput in) {
        GolfMatch m = matchOf(player);
        if (m != null) {
            m.playerShot(player, in);
            return;
        }
        practiceShot(player, in);
    }

    /** Driving-range shot from the player's feet (no match). */
    private static void practiceShot(ServerPlayer player, ShotInput in) {
        Long ready = PRACTICE_READY.get(player.getUUID());
        if (ready != null && serverTicks < ready) return;
        LevelTerrain terrain = new LevelTerrain(player.level());
        double x = player.getX();
        double y = player.getY() + BallPhysics.REST_OFFSET;
        double z = player.getZ();
        Lie lie = terrain.lieUnder(x, y, z);
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("msg.birdiecraft.practice_ground").withStyle(ChatFormatting.RED), true);
            return;
        }
        double lo = lie.minPower(in.club());
        double hi = lie.maxPower(in.club());
        double lieFactor = lo + RNG.nextDouble() * (hi - lo);
        ShotLaunch launch = ShotCalculator.compute(in, lie, PlayerProfiles.style(player), x, y, z, lieFactor, RNG.nextLong());
        Trajectory t = BallPhysics.simulate(terrain, launch, Wind.CALM, BallPhysics.MAX_TICKS);
        PRACTICE_READY.put(player.getUUID(), serverTicks + Math.min(t.length(), 200) / 2 + 10);
        GolfNetwork.sendTo(player, new S2CBallFlightPacket(-1, true, 0xFFFFFF, in.club().ordinal(), in.isPerfect(), t));
        player.swing(InteractionHand.MAIN_HAND, true);
        if (in.isPerfect() && !in.club().isPutter()) {
            GolfNetwork.sendTo(player, new S2CMessagePacket(S2CMessagePacket.KIND_SUB,
                    Component.translatable("msg.birdiecraft.perfect"), 0xFFE14A, S2CMessagePacket.SOUND_PERFECT, 30));
        }
    }
}
