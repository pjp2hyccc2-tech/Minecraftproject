package jp.satsujinki.game;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.block.TrapBlock;
import jp.satsujinki.block.SpikeBedBlock;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.network.ModNetwork;
import jp.satsujinki.network.RoundPacket;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death game rounds: intro announcement → contestants appear → countdown → kill them all before time runs out.
 */
public final class DeathGameManager {
    public static final int PHASE_INTRO = 0, PHASE_RUNNING = 1, PHASE_ENDING = 2;

    private static final class Round {
        final UUID id = UUID.randomUUID();
        final UUID host;
        final ResourceKey<Level> dim;
        final BlockPos center;
        final int count;
        final int limit;
        final List<UUID> members = new ArrayList<>();
        int phase = PHASE_INTRO;
        int ticks;
        int alive;
        int pointsAtStart;
        int endTicks;
        boolean perfect;

        Round(UUID host, ResourceKey<Level> dim, BlockPos center, int count, int limit) {
            this.host = host;
            this.dim = dim;
            this.center = center;
            this.count = count;
            this.limit = limit;
        }
    }

    private static final Map<UUID, Round> ROUNDS = new HashMap<>();

    private DeathGameManager() {
    }

    public static boolean isRunning(UUID host) {
        return ROUNDS.containsKey(host);
    }

    public static boolean start(ServerPlayer host, BlockPos center, int count) {
        if (ROUNDS.containsKey(host.getUUID())) {
            host.displayClientMessage(Component.translatable("satsujinki.round.already").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Round r = new Round(host.getUUID(), host.serverLevel().dimension(), center.immutable(), Mth.clamp(count, 1, 40), SatsujinkiConfig.roundTicks());
        r.pointsAtStart = HostData.get(host.server).of(host.getUUID()).points;
        ROUNDS.put(host.getUUID(), r);
        broadcast(host.server, r, RoundPacket.intro(r.count));
        sound(host.serverLevel(), r, ModSounds.UI_ANNOUNCE.get(), 1.0F);
        return true;
    }

    public static void stop(ServerPlayer host) {
        Round r = ROUNDS.get(host.getUUID());
        if (r == null) return;
        ServerLevel level = host.server.getLevel(r.dim);
        if (level != null && r.phase != PHASE_ENDING) end(host.server, level, r, false);
    }

    @Nullable
    public static UUID hostOf(ContestantEntity c) {
        UUID rid = c.getRoundId();
        if (rid == null) return null;
        for (Round r : ROUNDS.values()) {
            if (r.id.equals(rid)) return r.host;
        }
        return null;
    }

    public static void onContestantDeath(ContestantEntity c) {
        UUID rid = c.getRoundId();
        if (rid == null) return;
        for (Round r : ROUNDS.values()) {
            if (r.id.equals(rid) && r.phase == PHASE_RUNNING) {
                r.alive = Math.max(0, r.alive - 1);
                if (c.level() instanceof ServerLevel sl) {
                    broadcast(sl.getServer(), r, RoundPacket.update(r.alive, r.count, Math.max(0, r.limit - r.ticks)));
                }
            }
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<Round> it = ROUNDS.values().iterator();
        while (it.hasNext()) {
            Round r = it.next();
            ServerLevel level = server.getLevel(r.dim);
            ServerPlayer host = server.getPlayerList().getPlayer(r.host);
            if (level == null || host == null) {
                if (level != null) despawnSurvivors(level, r);
                it.remove();
                continue;
            }
            r.ticks++;
            switch (r.phase) {
                case PHASE_INTRO -> {
                    if (r.ticks == 40) spawnContestants(level, r);
                    if (r.ticks == 60 || r.ticks == 80 || r.ticks == 100) {
                        broadcast(server, r, RoundPacket.count(3 - (r.ticks - 60) / 20));
                        sound(level, r, ModSounds.UI_COUNTDOWN.get(), 1.0F);
                    }
                    if (r.ticks >= 120) {
                        r.phase = PHASE_RUNNING;
                        r.ticks = 0;
                        recount(level, r);
                        broadcast(server, r, RoundPacket.go(r.alive, r.count, r.limit));
                        sound(level, r, ModSounds.UI_START.get(), 1.0F);
                    }
                }
                case PHASE_RUNNING -> {
                    if (r.ticks % 20 == 0) {
                        recount(level, r);
                        broadcast(server, r, RoundPacket.update(r.alive, r.count, Math.max(0, r.limit - r.ticks)));
                    }
                    if (r.alive <= 0) end(server, level, r, true);
                    else if (r.ticks >= r.limit) end(server, level, r, false);
                }
                default -> {
                    r.endTicks++;
                    if (r.endTicks == 70) despawnSurvivors(level, r);
                    if (r.endTicks >= 90) it.remove();
                }
            }
        }
    }

    private static void end(MinecraftServer server, ServerLevel level, Round r, boolean allDead) {
        recount(level, r);
        r.phase = PHASE_ENDING;
        r.endTicks = 0;
        r.perfect = allDead && r.alive == 0;
        HostData data = HostData.get(server);
        HostData.Stats s = data.of(r.host);
        int bonus = 0;
        if (r.perfect) bonus = 300 + r.count * 60 + Math.max(0, (r.limit - r.ticks) / 20) * 4;
        s.points += bonus;
        s.roundsPlayed++;
        if (r.perfect) s.roundsPerfect++;
        data.setDirty();
        int kills = r.count - r.alive;
        int earned = s.points - r.pointsAtStart;
        broadcast(server, r, RoundPacket.end(kills, r.count, earned, bonus, r.ticks, r.perfect));
        sound(level, r, ModSounds.UI_GAMEOVER.get(), 1.0F);
        for (UUID id : r.members) {
            Entity e = level.getEntity(id);
            if (e instanceof ContestantEntity c && c.isAlive()) {
                c.say(Lines.random(Lines.Cat.SURVIVE, level.random), 70);
            }
        }
        ServerPlayer host = server.getPlayerList().getPlayer(r.host);
        if (host != null) {
            ScoreManager.sync(host, -1, 0, false);
            Adv.grant(host, "round");
            if (r.perfect) Adv.grant(host, "perfect_game");
        }
    }

    private static void despawnSurvivors(ServerLevel level, Round r) {
        for (UUID id : r.members) {
            Entity e = level.getEntity(id);
            if (e instanceof ContestantEntity c && c.isAlive()) {
                GoreHandler.fx(level, c.getX(), c.getY() + 1.0D, c.getZ(), GoreFxPacket.SPAWN_POOF, 1.0F);
                c.discard();
            }
        }
    }

    private static void recount(ServerLevel level, Round r) {
        int alive = 0;
        for (UUID id : r.members) {
            Entity e = level.getEntity(id);
            if (e instanceof ContestantEntity c && c.isAlive()) alive++;
        }
        r.alive = alive;
    }

    private static void spawnContestants(ServerLevel level, Round r) {
        List<Vec3> spots = findSpots(level, r.center, r.count, level.random);
        for (Vec3 v : spots) {
            ContestantEntity c = ContestantEntity.spawnAt(level, v, null);
            if (c != null) {
                c.setRoundId(r.id);
                c.addFear(0.15F);
                r.members.add(c.getUUID());
            }
        }
        r.alive = r.members.size();
    }

    /** Free standing spots around the center that are not on top of a trap. */
    public static List<Vec3> findSpots(ServerLevel level, BlockPos center, int count, RandomSource rnd) {
        List<Vec3> out = new ArrayList<>();
        int tries = 0;
        while (out.size() < count && tries++ < count * 40) {
            double ang = rnd.nextDouble() * Math.PI * 2.0D;
            double rad = 2.0D + rnd.nextDouble() * 5.5D;
            int x = Mth.floor(center.getX() + 0.5D + Math.cos(ang) * rad);
            int z = Mth.floor(center.getZ() + 0.5D + Math.sin(ang) * rad);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                BlockPos below = feet.below();
                BlockState under = level.getBlockState(below);
                if (!under.isFaceSturdy(level, below, Direction.UP)) continue;
                if (under.getBlock() instanceof TrapBlock || under.getBlock() instanceof SpikeBedBlock) break;
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                if (level.getBlockState(feet).getBlock() instanceof SpikeBedBlock) break;
                Vec3 v = new Vec3(x + 0.5D, feet.getY(), z + 0.5D);
                boolean clash = false;
                for (Vec3 o : out) {
                    if (o.distanceToSqr(v) < 0.8D) {
                        clash = true;
                        break;
                    }
                }
                if (!clash) out.add(v);
                break;
            }
        }
        while (out.size() < count) out.add(Vec3.atBottomCenterOf(center.above()));
        return out;
    }

    private static List<ServerPlayer> viewers(MinecraftServer server, Round r) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(r.host) || (p.level().dimension() == r.dim && p.blockPosition().distSqr(r.center) < 64 * 64)) list.add(p);
        }
        return list;
    }

    private static void broadcast(MinecraftServer server, Round r, RoundPacket pkt) {
        for (ServerPlayer p : viewers(server, r)) ModNetwork.sendTo(p, pkt);
    }

    private static void sound(ServerLevel level, Round r, SoundEvent s, float pitch) {
        for (ServerPlayer p : viewers(level.getServer(), r)) {
            p.playNotifySound(s, SoundSource.MASTER, 1.0F, pitch);
        }
    }

    public static void clear() {
        ROUNDS.clear();
    }
}
