package com.birdiecraft.match;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.course.Course;
import com.birdiecraft.course.Hole;
import com.birdiecraft.course.HoleMap;
import com.birdiecraft.entity.CpuGolferEntity;
import com.birdiecraft.golf.engine.BallPhysics;
import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.Difficulty;
import com.birdiecraft.golf.engine.GolfAI;
import com.birdiecraft.golf.engine.GolfUnits;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.PlayStyle;
import com.birdiecraft.golf.engine.ScoreTerm;
import com.birdiecraft.golf.engine.ShotCalculator;
import com.birdiecraft.golf.engine.ShotInput;
import com.birdiecraft.golf.engine.ShotLaunch;
import com.birdiecraft.golf.engine.ShotMode;
import com.birdiecraft.golf.engine.Trajectory;
import com.birdiecraft.golf.engine.Wind;
import com.birdiecraft.item.GolfClubItem;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.network.S2CBallFlightPacket;
import com.birdiecraft.network.S2CHoleMapPacket;
import com.birdiecraft.network.S2CMatchStatePacket;
import com.birdiecraft.network.S2CMessagePacket;
import com.birdiecraft.registry.ModEntities;
import com.birdiecraft.registry.ModItems;
import com.birdiecraft.world.LevelTerrain;
import com.birdiecraft.world.SurfaceResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/** One round of golf: state machine, turn order, shots, scoring. */
public final class GolfMatch {
    private static final int INTRO_TICKS = 70;
    private static final int HOLE_END_TICKS = 110;
    private static final int FINISHED_TICKS = 240;
    private static final int AFTER_FLIGHT_TICKS = 24;
    private static final long AI_BUDGET_NANOS = 6_000_000L;
    private static final String[] CPU_NAMES = {"Riku", "Hana", "Tetsu", "Mia", "Kenta", "Luna", "Sora", "Nagi"};
    private static final PlayStyle[] CPU_STYLES = {PlayStyle.POWER, PlayStyle.CONTROL, PlayStyle.SPIN, PlayStyle.BALANCED,
            PlayStyle.BALANCED, PlayStyle.SPIN, PlayStyle.POWER, PlayStyle.CONTROL};

    /** Log every shot (enabled by the automated self-test). */
    public static boolean verbose;

    public final int id;
    public final MatchMode mode;
    public final Course course;
    public final int startHole;
    public final int holeCount;
    public final List<Participant> participants = new ArrayList<>();
    public UUID host;
    public MatchState state = MatchState.LOBBY;

    private final MinecraftServer server;
    private final Random rng = new Random();
    private int stateTicks;
    private int holeIdx;
    private Wind wind = Wind.CALM;
    private int turn = -1;
    private int turnTicks;
    private int nextCpuSkin;
    private boolean disposed;

    // in-flight shot
    private Participant shooter;
    private Trajectory flight;
    private ShotInput flightInput;
    private Lie flightStartLie;
    private double fsx, fsy, fsz;
    private int flightEndTick;

    // cpu / auto-play planning
    private GolfAI.Planner planner;
    private Participant planningFor;
    private boolean autoPlay;

    public GolfMatch(int id, MinecraftServer server, MatchMode mode, Course course, int startHole, int holeCount) {
        this.id = id;
        this.server = server;
        this.mode = mode;
        this.course = course;
        this.startHole = Math.max(0, Math.min(startHole, course.holes.size() - 1));
        this.holeCount = Math.max(1, Math.min(holeCount, course.holes.size() - this.startHole));
    }

    // ------------------------------------------------------------------ membership

    public Participant addPlayer(ServerPlayer player) {
        Participant p = new Participant(nextParticipantId(), player.getGameProfile().getName(), player.getUUID(), false,
                PlayerProfiles.style(player), holeCount);
        participants.add(p);
        return p;
    }

    public Participant addCpu(Difficulty difficulty) {
        int skin = nextCpuSkin++ % CPU_NAMES.length;
        Participant p = new Participant(nextParticipantId(), CPU_NAMES[skin], null, true, CPU_STYLES[skin], holeCount);
        p.difficulty = difficulty;
        p.skin = skin % 4;
        participants.add(p);
        return p;
    }

    private int nextParticipantId() {
        int max = -1;
        for (Participant p : participants) max = Math.max(max, p.id);
        return max + 1;
    }

    public Participant participant(UUID uuid) {
        for (Participant p : participants) if (uuid.equals(p.uuid)) return p;
        return null;
    }

    public Participant byId(int pid) {
        for (Participant p : participants) if (p.id == pid) return p;
        return null;
    }

    public List<ServerPlayer> humans() {
        List<ServerPlayer> list = new ArrayList<>();
        for (Participant p : participants) {
            if (p.cpu) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(p.uuid);
            if (sp != null) list.add(sp);
        }
        return list;
    }

    public int humanCount() {
        int n = 0;
        for (Participant p : participants) if (!p.cpu) n++;
        return n;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public int holeIndex() {
        return holeIdx;
    }

    public Hole currentHole() {
        return course.holes.get(startHole + holeIdx);
    }

    public ServerLevel level() {
        ServerLevel l = server.getLevel(course.dimension);
        return l != null ? l : server.overworld();
    }

    /** Removes a player; returns true when the match should be disposed. */
    public boolean removePlayer(UUID uuid) {
        Participant p = participant(uuid);
        if (p == null) return humanCount() == 0;
        participants.remove(p);
        if (humanCount() == 0) return true;
        if (uuid.equals(host)) {
            for (Participant o : participants) {
                if (!o.cpu) {
                    host = o.uuid;
                    break;
                }
            }
        }
        if (state == MatchState.PLAYING && turn == p.id) {
            planner = null;
            nextTurn();
        } else if (state == MatchState.BALL_MOVING && shooter == p) {
            shooter = null;
            flight = null;
            nextTurn();
        }
        sync();
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    public void begin() {
        if (state != MatchState.LOBBY) return;
        int powerShots = GolfConfig.POWER_SHOTS_PER_ROUND.get();
        for (Participant p : participants) p.powerShots = powerShots;
        for (ServerPlayer sp : humans()) ensureClub(sp);
        holeIdx = 0;
        startHole();
    }

    private void ensureClub(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).getItem() instanceof GolfClubItem) return;
        }
        ItemStack club = new ItemStack(ModItems.GOLF_CLUB.get());
        if (!sp.getInventory().add(club)) sp.drop(club, false);
    }

    private void startHole() {
        Hole h = currentHole();
        Wind w = Wind.random(rng);
        double factor = course.windFactor * GolfConfig.WIND_MULTIPLIER.get();
        wind = new Wind(Math.round(w.speedMps * factor * 10.0) / 10.0, w.yawDeg);
        for (Participant p : participants) {
            p.cur = 0;
            p.holed = false;
            p.finished = false;
            p.placeBall(h.teeX(), h.teeY(), h.teeZ(), Lie.TEE);
            p.rememberPrevious();
        }
        state = MatchState.INTRO;
        stateTicks = 0;
        turn = -1;
        planner = null;

        ServerLevel level = level();
        try {
            HoleMap map = HoleMap.build(level, h);
            for (ServerPlayer sp : humans()) GolfNetwork.sendTo(sp, new S2CHoleMapPacket(id, holeIdx, map));
        } catch (Exception e) {
            BirdieCraft.LOGGER.warn("Failed to build hole map", e);
        }

        double yaw = GolfUnits.yawTo(h.teeX(), h.teeZ(), h.cupX(), h.cupZ());
        double bx = -Math.sin(Math.toRadians(yaw));
        double bz = Math.cos(Math.toRadians(yaw));
        int k = 0;
        for (ServerPlayer sp : humans()) {
            double side = (k % 2 == 0 ? 1 : -1) * (1 + k / 2) * 1.6;
            double x = h.teeX() - bx * 3.0 - bz * side;
            double z = h.teeZ() - bz * 3.0 + bx * side;
            if (GolfConfig.TELEPORT_TO_BALL.get()) sp.teleportTo(level, x, h.teeY(), z, (float) yaw, 8f);
            k++;
        }
        for (Participant p : participants) {
            if (p.cpu) placeCpuIdle(p, k++);
        }
        broadcast(S2CMessagePacket.KIND_TITLE,
                Component.translatable("msg.birdiecraft.hole_intro", startHole + holeIdx + 1, h.par, h.yards()),
                0xFFFFFF, S2CMessagePacket.SOUND_TURN, 60);
        sync();
    }

    public void tick() {
        if (disposed) return;
        stateTicks++;
        switch (state) {
            case LOBBY -> {
                if (host == null || server.getPlayerList().getPlayer(host) == null) {
                    // host vanished: promote someone else or close
                    for (ServerPlayer sp : humans()) {
                        host = sp.getUUID();
                        sync();
                        return;
                    }
                    disposed = true;
                }
            }
            case INTRO -> {
                if (stateTicks >= INTRO_TICKS) {
                    state = MatchState.PLAYING;
                    nextTurn();
                }
            }
            case PLAYING -> tickTurn();
            case BALL_MOVING -> {
                if (stateTicks >= flightEndTick) resolveShot();
            }
            case HOLE_END -> {
                if (stateTicks >= HOLE_END_TICKS) {
                    if (holeIdx + 1 < holeCount) {
                        holeIdx++;
                        startHole();
                    } else {
                        finishRound();
                    }
                }
            }
            case FINISHED -> {
                if (stateTicks >= FINISHED_TICKS) disposed = true;
            }
        }
        if (stateTicks % 20 == 0 && (state == MatchState.PLAYING)) sync();
    }

    // ------------------------------------------------------------------ turns

    private void nextTurn() {
        planner = null;
        planningFor = null;
        autoPlay = false;
        List<Participant> active = new ArrayList<>();
        for (Participant p : participants) if (!p.finished) active.add(p);
        if (active.isEmpty()) {
            endHole();
            return;
        }
        Hole h = currentHole();
        Participant next = null;
        boolean allOnTee = true;
        for (Participant p : active) if (p.cur > 0) allOnTee = false;
        if (allOnTee) {
            // honour: best score on the previous hole tees off first
            List<Participant> order = new ArrayList<>(active);
            if (holeIdx > 0) {
                int prev = holeIdx - 1;
                order.sort(Comparator.comparingInt(p -> p.strokes[prev]));
            }
            next = order.get(0);
        } else {
            double best = -1;
            for (Participant p : active) {
                double d = GolfUnits.horizontalDistance(p.bx, p.bz, h.cupX(), h.cupZ());
                if (p.cur == 0) d += 10000;
                if (d > best) {
                    best = d;
                    next = p;
                }
            }
        }
        turn = next.id;
        state = MatchState.PLAYING;
        stateTicks = 0;
        turnTicks = 0;

        // everyone else steps aside
        int k = 0;
        for (Participant p : participants) {
            if (p.cpu && p != next) placeCpuIdle(p, k++);
        }
        if (next.cpu) {
            placeCpuAddress(next);
            startPlanning(next, next.difficulty);
        } else {
            ServerPlayer sp = server.getPlayerList().getPlayer(next.uuid);
            if (sp != null) {
                if (GolfConfig.TELEPORT_TO_BALL.get()) teleportToAddress(sp, next);
                GolfNetwork.sendTo(sp, new S2CMessagePacket(S2CMessagePacket.KIND_SUB,
                        Component.translatable("msg.birdiecraft.your_turn"), 0x7CFF6B, S2CMessagePacket.SOUND_TURN, 40));
            }
        }
        sync();
    }

    private void tickTurn() {
        Participant p = byId(turn);
        if (p == null) {
            nextTurn();
            return;
        }
        if (p.cpu || autoPlay) {
            if (planner == null) startPlanning(p, p.cpu ? p.difficulty : Difficulty.AMATEUR);
            planner.step(AI_BUDGET_NANOS);
            int think = p.cpu ? p.difficulty.thinkTicks : 10;
            if (planner.isDone() && stateTicks >= think) {
                ShotInput ideal = planner.result();
                ShotInput shot = GolfAI.applyError(ideal, p.cpu ? p.difficulty : Difficulty.AMATEUR, rng);
                planner = null;
                executeShot(p, shot);
            }
            return;
        }
        turnTicks++;
        int limit = GolfConfig.TURN_TIMEOUT_SECONDS.get() * 20;
        if (mode == MatchMode.MULTI && turnTicks > limit) {
            autoPlay = true;
            stateTicks = 0;
            broadcast(S2CMessagePacket.KIND_TOAST, Component.translatable("msg.birdiecraft.timeout", p.name), 0xFFAA00,
                    S2CMessagePacket.SOUND_NONE, 60);
        }
    }

    private void startPlanning(Participant p, Difficulty difficulty) {
        Hole h = currentHole();
        LevelTerrain terrain = new LevelTerrain(level());
        Lie lie = terrain.lieUnder(p.bx, p.by, p.bz);
        GolfAI.Context ctx = new GolfAI.Context(terrain, p.bx, p.by, p.bz, lie, h.cupX(), h.cupY(), h.cupZ(),
                wind, p.style, difficulty, p.powerShots, inBounds());
        planner = new GolfAI.Planner(ctx);
        planningFor = p;
    }

    private Predicate<Lie> inBounds() {
        return SurfaceResolver.inBoundsRule();
    }

    // ------------------------------------------------------------------ shots

    /** Called for a player's shot packet. */
    public void playerShot(ServerPlayer player, ShotInput in) {
        Participant p = participant(player.getUUID());
        if (p == null || state != MatchState.PLAYING || turn != p.id || autoPlay) return;
        executeShot(p, in);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    private void executeShot(Participant p, ShotInput in) {
        ServerLevel level = level();
        LevelTerrain terrain = new LevelTerrain(level);
        Lie lie = terrain.lieUnder(p.bx, p.by, p.bz);
        Club club = in.club();
        ShotInput input = in;
        if (!club.isPutter() && in.mode() == ShotMode.POWER) {
            if (p.powerShots > 0) {
                p.powerShots--;
            } else {
                input = new ShotInput(club, ShotMode.NORMAL, in.yawDeg(), in.power(), in.deviation(), in.spinX(), in.spinY(), in.puttRange());
            }
        }
        double lo = lie.minPower(club);
        double hi = lie.maxPower(club);
        double lieFactor = lo + rng.nextDouble() * (hi - lo);
        ShotLaunch launch = ShotCalculator.compute(input, lie, p.style, p.bx, p.by, p.bz, lieFactor, rng.nextLong());
        Trajectory t = BallPhysics.simulate(terrain, launch, wind, BallPhysics.MAX_TICKS);

        p.shots++;
        boolean perfect = input.isPerfect();
        if (perfect && !club.isPutter()) {
            p.perfects++;
            p.powerShots = Math.min(6, p.powerShots + 1);
        }
        shooter = p;
        flight = t;
        flightInput = input;
        flightStartLie = lie;
        fsx = p.bx;
        fsy = p.by;
        fsz = p.bz;
        state = MatchState.BALL_MOVING;
        stateTicks = 0;
        flightEndTick = t.length() + AFTER_FLIGHT_TICKS + (p.cpu ? CpuGolferEntity.IMPACT_TICK : 0);

        S2CBallFlightPacket packet = new S2CBallFlightPacket(p.id, false, p.color, club.ordinal(), perfect, t);
        for (ServerPlayer sp : humans()) GolfNetwork.sendTo(sp, packet);
        if (perfect && !club.isPutter()) {
            broadcast(S2CMessagePacket.KIND_SUB, Component.translatable("msg.birdiecraft.perfect"), 0xFFE14A,
                    S2CMessagePacket.SOUND_PERFECT, 30);
        }
        if (p.cpu) swingCpu(p);
        sync();
    }

    private void resolveShot() {
        Participant p = shooter;
        Trajectory t = flight;
        shooter = null;
        flight = null;
        if (p == null || t == null || !participants.contains(p)) {
            nextTurn();
            return;
        }
        Hole h = currentHole();
        int par = h.par;
        p.rememberPrevious();
        p.px = fsx;
        p.py = fsy;
        p.pz = fsz;
        p.cur++;
        boolean putt = flightInput != null && flightInput.club().isPutter();
        LevelTerrain terrain = new LevelTerrain(level());
        Predicate<Lie> inBounds = inBounds();
        switch (t.result) {
            case IN_CUP -> {
                p.holed = true;
                p.finished = true;
                p.placeBall(h.cupX(), h.cupY() - 0.12, h.cupZ(), Lie.GREEN);
                p.strokes[holeIdx] = p.cur;
                ScoreTerm term = ScoreTerm.of(p.cur, par);
                p.lastTerm = term.ordinal();
                announceHoleOut(p, term, putt);
            }
            case WATER -> {
                p.cur++;
                double[] drop = BallPhysics.findDropSpot(terrain, t, inBounds);
                p.placeBall(drop[0], drop[1], drop[2], terrain.lieUnder(drop[0], drop[1], drop[2]));
                broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable("msg.birdiecraft.water"), 0x55AAFF,
                        S2CMessagePacket.SOUND_GROAN, 45);
            }
            case OUT_OF_WORLD -> {
                p.cur++;
                p.placeBall(fsx, fsy, fsz, flightStartLie);
                broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable("msg.birdiecraft.ob"), 0xFF5555,
                        S2CMessagePacket.SOUND_GROAN, 45);
            }
            default -> {
                Lie lie = t.restLie;
                if (lie.isHazard()) {
                    p.cur++;
                    double[] drop = BallPhysics.findDropSpot(terrain, t, inBounds);
                    p.placeBall(drop[0], drop[1], drop[2], terrain.lieUnder(drop[0], drop[1], drop[2]));
                    broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable("msg.birdiecraft.hazard"), 0x9FE8FF,
                            S2CMessagePacket.SOUND_GROAN, 45);
                } else if (!inBounds.test(lie)) {
                    p.cur++;
                    p.placeBall(fsx, fsy, fsz, flightStartLie);
                    broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable("msg.birdiecraft.ob"), 0xFF5555,
                            S2CMessagePacket.SOUND_GROAN, 45);
                } else {
                    p.placeBall(t.endX, t.endY, t.endZ, lie);
                    double toCup = GolfUnits.horizontalDistance(t.endX, t.endZ, h.cupX(), h.cupZ());
                    if (!putt && lie == Lie.GREEN && p.cur <= par - 2) {
                        broadcast(S2CMessagePacket.KIND_SUB, Component.translatable("msg.birdiecraft.nice_on"), 0x7CFF6B,
                                S2CMessagePacket.SOUND_APPLAUSE, 35);
                    } else if (!putt && toCup < 2.5) {
                        broadcast(S2CMessagePacket.KIND_SUB, Component.translatable("msg.birdiecraft.nice_approach"), 0x7CFF6B,
                                S2CMessagePacket.SOUND_APPLAUSE, 35);
                    } else if (lie == Lie.BUNKER) {
                        broadcast(S2CMessagePacket.KIND_TOAST, Component.translatable("msg.birdiecraft.bunker", p.name), 0xE8D8A8,
                                S2CMessagePacket.SOUND_NONE, 40);
                    }
                }
            }
        }
        if (verbose) {
            BirdieCraft.LOGGER.info("[SelfTest] hole {} {} shot {} with {} -> {} lie {} at ({}, {}, {}) {}Y to pin",
                    startHole + holeIdx + 1, p.name, p.cur, flightInput != null ? flightInput.club().label : "?", t.result,
                    p.lie, Math.round(p.bx), Math.round(p.by), Math.round(p.bz),
                    GolfUnits.yardsRounded(GolfUnits.horizontalDistance(p.bx, p.bz, h.cupX(), h.cupZ())));
        }
        int maxStrokes = par + GolfConfig.MAX_STROKES_OVER_PAR.get();
        if (!p.holed && p.cur >= maxStrokes) {
            p.finished = true;
            p.strokes[holeIdx] = p.cur;
            p.lastTerm = ScoreTerm.of(p.cur, par).ordinal();
            broadcast(S2CMessagePacket.KIND_TOAST, Component.translatable("msg.birdiecraft.give_up", p.name, p.cur), 0xAAAAAA,
                    S2CMessagePacket.SOUND_NONE, 50);
        }
        boolean allDone = true;
        for (Participant o : participants) if (!o.finished) allDone = false;
        if (allDone) endHole();
        else nextTurn();
    }

    private void announceHoleOut(Participant p, ScoreTerm term, boolean putt) {
        int sound;
        if (term == ScoreTerm.HOLE_IN_ONE || term.ordinal() <= ScoreTerm.EAGLE.ordinal()) sound = S2CMessagePacket.SOUND_FANFARE_BIG;
        else if (term == ScoreTerm.BIRDIE) sound = S2CMessagePacket.SOUND_FANFARE_SMALL;
        else if (term == ScoreTerm.PAR) sound = S2CMessagePacket.SOUND_APPLAUSE;
        else sound = S2CMessagePacket.SOUND_NONE;
        broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable(term.translationKey()), term.color, sound, 70);
        MutableComponent sub = Component.literal(p.name + "  ").append(Component.translatable("msg.birdiecraft.strokes", p.cur));
        if (!putt && p.cur > 1 && flightStartLie != Lie.GREEN) {
            sub = Component.translatable("msg.birdiecraft.chip_in").append("  ").append(sub);
        }
        broadcast(S2CMessagePacket.KIND_SUB, sub, 0xFFFFFF, S2CMessagePacket.SOUND_NONE, 70);
        for (ServerPlayer sp : humans()) {
            sp.sendSystemMessage(Component.literal("[Golf] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(p.name + ": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.translatable(term.translationKey()).withStyle(s -> s.withColor(term.color)))
                    .append(Component.literal(" (" + p.cur + ")").withStyle(ChatFormatting.GRAY)));
        }
    }

    private void endHole() {
        state = MatchState.HOLE_END;
        stateTicks = 0;
        turn = -1;
        for (Participant p : participants) {
            if (p.strokes[holeIdx] == 0) p.strokes[holeIdx] = Math.max(1, p.cur);
        }
        sync();
    }

    private void finishRound() {
        state = MatchState.FINISHED;
        stateTicks = 0;
        turn = -1;
        List<Participant> ranking = new ArrayList<>(participants);
        ranking.sort(Comparator.comparingInt(Participant::total));
        int totalPar = 0;
        for (int i = 0; i < holeCount; i++) totalPar += course.holes.get(startHole + i).par;
        Participant winner = ranking.get(0);
        int diff = winner.total() - totalPar;
        String toPar = diff == 0 ? "E" : (diff > 0 ? "+" + diff : String.valueOf(diff));
        broadcast(S2CMessagePacket.KIND_TITLE, Component.translatable("msg.birdiecraft.winner", winner.name), 0xFFD700,
                S2CMessagePacket.SOUND_FANFARE_BIG, 100);
        broadcast(S2CMessagePacket.KIND_SUB, Component.literal(winner.total() + " (" + toPar + ")"), 0xFFFFFF,
                S2CMessagePacket.SOUND_NONE, 100);
        for (ServerPlayer sp : humans()) {
            Participant me = participant(sp.getUUID());
            sp.sendSystemMessage(Component.translatable("msg.birdiecraft.round_over", course.title()).withStyle(ChatFormatting.GOLD));
            int rank = 1;
            for (Participant r : ranking) {
                int d = r.total() - totalPar;
                String tp = d == 0 ? "E" : (d > 0 ? "+" + d : String.valueOf(d));
                sp.sendSystemMessage(Component.literal(" " + rank + ". " + r.name + "  " + r.total() + " (" + tp + ")")
                        .withStyle(r == me ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
                rank++;
            }
            if (me != null && PlayerProfiles.recordRound(sp, course.id, holeCount, me.total())) {
                sp.sendSystemMessage(Component.translatable("msg.birdiecraft.new_record", me.total()).withStyle(ChatFormatting.AQUA));
            }
        }
        removeCpuEntities();
        sync();
    }

    public void dispose() {
        if (disposed && participants.isEmpty()) return;
        disposed = true;
        removeCpuEntities();
        for (ServerPlayer sp : humans()) GolfNetwork.sendTo(sp, S2CMatchStatePacket.none());
    }

    // ------------------------------------------------------------------ positioning

    private void teleportToAddress(ServerPlayer sp, Participant p) {
        Hole h = currentHole();
        double yaw = GolfUnits.yawTo(p.bx, p.bz, h.cupX(), h.cupZ());
        double dx = -Math.sin(Math.toRadians(yaw));
        double dz = Math.cos(Math.toRadians(yaw));
        double x = p.bx - dx * 1.4;
        double z = p.bz - dz * 1.4;
        double y = p.by;
        ServerLevel level = level();
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(x, y + 0.1, z);
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
            x = p.bx;
            z = p.bz;
        }
        sp.teleportTo(level, x, y, z, (float) yaw, 12f);
    }

    private CpuGolferEntity cpuEntity(Participant p) {
        ServerLevel level = level();
        if (p.entityId != null) {
            Entity e = level.getEntity(p.entityId);
            if (e instanceof CpuGolferEntity g && g.isAlive()) return g;
        }
        CpuGolferEntity g = ModEntities.CPU_GOLFER.get().create(level);
        if (g == null) return null;
        g.setup(p.name, p.skin, p.difficulty);
        g.moveTo(p.bx, p.by, p.bz, 0, 0);
        level.addFreshEntity(g);
        p.entityId = g.getUUID();
        return g;
    }

    private void placeCpuAddress(Participant p) {
        CpuGolferEntity g = cpuEntity(p);
        if (g == null) return;
        Hole h = currentHole();
        double shotYaw = GolfUnits.yawTo(p.bx, p.bz, h.cupX(), h.cupZ());
        double faceYaw = shotYaw + 90.0;
        double fx = -Math.sin(Math.toRadians(faceYaw));
        double fz = Math.cos(Math.toRadians(faceYaw));
        g.placeAt(p.bx - fx * 0.75, p.by, p.bz - fz * 0.75, (float) faceYaw);
    }

    private void placeCpuIdle(Participant p, int slot) {
        CpuGolferEntity g = cpuEntity(p);
        if (g == null) return;
        Hole h = currentHole();
        double yaw = GolfUnits.yawTo(p.bx, p.bz, h.cupX(), h.cupZ());
        double dx = -Math.sin(Math.toRadians(yaw));
        double dz = Math.cos(Math.toRadians(yaw));
        double side = 2.5 + (slot % 3) * 0.8;
        double x = p.bx - dx * 2.0 + dz * side;
        double z = p.bz - dz * 2.0 - dx * side;
        g.placeAt(x, p.by, z, (float) yaw);
    }

    private void swingCpu(Participant p) {
        CpuGolferEntity g = cpuEntity(p);
        if (g != null) g.playSwing();
    }

    private void removeCpuEntities() {
        ServerLevel level = level();
        for (Participant p : participants) {
            if (p.entityId != null) {
                Entity e = level.getEntity(p.entityId);
                if (e != null) e.discard();
                p.entityId = null;
            }
        }
    }

    // ------------------------------------------------------------------ networking

    private void broadcast(int kind, Component text, int color, int sound, int duration) {
        S2CMessagePacket msg = new S2CMessagePacket(kind, text, color, sound, duration);
        for (ServerPlayer sp : humans()) GolfNetwork.sendTo(sp, msg);
    }

    public void sync() {
        for (ServerPlayer sp : humans()) GolfNetwork.sendTo(sp, new S2CMatchStatePacket(view(sp)));
    }

    public MatchView view(ServerPlayer viewer) {
        MatchView v = new MatchView();
        v.matchId = id;
        v.mode = mode;
        v.state = state;
        v.stateTicks = stateTicks;
        v.courseId = course.id;
        v.courseTitle = course.title();
        v.flagColor = course.flagColor.ordinal();
        v.holeIdx = holeIdx;
        v.holeCount = holeCount;
        v.holeNumbers = new int[holeCount];
        v.pars = new int[holeCount];
        v.yards = new int[holeCount];
        for (int i = 0; i < holeCount; i++) {
            Hole h = course.holes.get(startHole + i);
            v.holeNumbers[i] = startHole + i + 1;
            v.pars[i] = h.par;
            v.yards[i] = h.yards();
        }
        Hole h = currentHole();
        v.tee = h.tee;
        v.cup = h.cup;
        v.windSpeed = (float) wind.speedMps;
        v.windYaw = (float) wind.yawDeg;
        v.turnId = turn;
        Participant me = viewer != null ? participant(viewer.getUUID()) : null;
        v.myId = me != null ? me.id : -1;
        v.isHost = viewer != null && viewer.getUUID().equals(host);
        ServerPlayer hostPlayer = host != null ? server.getPlayerList().getPlayer(host) : null;
        v.hostName = hostPlayer != null ? hostPlayer.getGameProfile().getName() : "";
        if (state == MatchState.PLAYING && mode == MatchMode.MULTI) {
            v.turnTicksLeft = Math.max(0, GolfConfig.TURN_TIMEOUT_SECONDS.get() * 20 - turnTicks);
        }
        for (Participant p : participants) {
            MatchView.PView pv = new MatchView.PView();
            pv.id = p.id;
            pv.name = p.name;
            pv.cpu = p.cpu;
            pv.style = p.style.ordinal();
            pv.difficulty = p.difficulty.ordinal();
            pv.skin = p.skin;
            pv.color = p.color;
            pv.strokes = p.strokes.clone();
            pv.cur = p.cur;
            pv.bx = p.bx;
            pv.by = p.by;
            pv.bz = p.bz;
            pv.lie = p.lie.ordinal();
            pv.holed = p.holed;
            pv.finished = p.finished;
            pv.powerShots = p.powerShots;
            pv.lastTerm = p.lastTerm;
            v.players.add(pv);
        }
        return v;
    }
}
