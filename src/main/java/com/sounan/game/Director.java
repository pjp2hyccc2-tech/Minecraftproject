package com.sounan.game;

import com.sounan.block.FacingBlock;
import com.sounan.entity.Koyanushi;
import com.sounan.entity.Nozoki;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.SounanMonster;
import com.sounan.entity.Ushiro;
import com.sounan.entity.Utsurumono;
import com.sounan.entity.Yobigoe;
import com.sounan.registry.ModBlocks;
import com.sounan.registry.ModEntities;
import com.sounan.registry.ModSounds;
import com.sounan.world.Pois;
import com.sounan.world.PoiLayout;
import com.sounan.world.TerrainData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paces the night: ambient tricks played to one player only (a branch snapping behind you, someone running past in the
 * trees, a deer screaming like a woman, knocks on a trunk, a bear bell that is not yours, a whisper at one ear, the
 * forest falling silent, jizo that have turned to face you), monster appearances chosen by place and progress, and the
 * scripted moments (the ringing public phone, something under the bridge, the final act, the tunnel).
 */
public final class Director {
    private static final RandomSource RAND = RandomSource.create();
    private static final Map<Long, UUID> RESIDENTS = new HashMap<>();
    private static final Map<UUID, Integer> PAYPHONE = new HashMap<>();
    private static final Map<UUID, Integer> TUNNEL_LAMPS = new HashMap<>();

    private Director() {}

    // ------------------------------------------------------------------------------------------------ queries
    static List<SounanMonster> monstersOf(ServerLevel level, ServerPlayer p, double radius) {
        return level.getEntitiesOfClass(SounanMonster.class, new AABB(p.blockPosition()).inflate(radius),
                m -> m.isAlive() && m.target() == p);
    }

    static boolean hasActive(ServerLevel level, ServerPlayer p) {
        for (SounanMonster m : monstersOf(level, p, 96)) {
            if (!(m instanceof Utsurumono) && !(m instanceof Koyanushi)) return true;
        }
        return false;
    }

    static <T extends SounanMonster> T spawn(ServerLevel level, EntityType<T> type, Vec3 at, float yaw, ServerPlayer target) {
        T m = type.create(level);
        if (m == null) return null;
        m.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        m.setYHeadRot(yaw);
        m.setTarget(target);
        level.addFreshEntity(m);
        return m;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0f;
    }

    static int ground(int x, int z) {
        return TerrainData.get().height(x, z) + 1;
    }

    /** A spot right behind a cedar trunk (seen from the player), roughly in front of them. */
    @Nullable
    static Vec3 trunkSpot(ServerLevel level, ServerPlayer p, double minR, double maxR, double maxAngle) {
        Vec3 eye = p.position();
        float view = p.getYRot() * Mth.DEG_TO_RAD;
        for (int tries = 0; tries < 70; tries++) {
            double ang = view + Math.PI / 2 + (RAND.nextDouble() * 2 - 1) * maxAngle;
            double r = minR + RAND.nextDouble() * (maxR - minR);
            int x = Mth.floor(eye.x + Math.cos(ang) * r), z = Mth.floor(eye.z + Math.sin(ang) * r);
            int y = ground(x, z);
            if (Math.abs(y - p.getY()) > 7) continue;
            BlockPos trunk = new BlockPos(x, y + 1, z);
            if (!level.getBlockState(trunk).is(ModBlocks.CEDAR_LOG.get())) continue;
            Vec3 dir = new Vec3(x + 0.5 - eye.x, 0, z + 0.5 - eye.z).normalize();
            Vec3 spot = new Vec3(x + 0.5, 0, z + 0.5).add(dir.scale(1.05));
            int sx = Mth.floor(spot.x), sz = Mth.floor(spot.z);
            int sy = ground(sx, sz);
            BlockPos at = new BlockPos(sx, sy, sz);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.above(2)).isAir()) continue;
            if (TerrainData.get().area(sx, sz) == TerrainData.AREA_OUT) continue;
            return new Vec3(spot.x, sy, spot.z);
        }
        return null;
    }

    /** An open spot inside the corridor at a distance, biased to the player's front (bias 0..1). */
    @Nullable
    static Vec3 openSpot(ServerLevel level, ServerPlayer p, double minR, double maxR, double maxAngle, boolean behind) {
        float view = p.getYRot() * Mth.DEG_TO_RAD + (behind ? Mth.PI : 0.0f);
        TerrainData t = TerrainData.get();
        for (int tries = 0; tries < 60; tries++) {
            double ang = view + Math.PI / 2 + (RAND.nextDouble() * 2 - 1) * maxAngle;
            double r = minR + RAND.nextDouble() * (maxR - minR);
            int x = Mth.floor(p.getX() + Math.cos(ang) * r), z = Mth.floor(p.getZ() + Math.sin(ang) * r);
            if (t.area(x, z) == TerrainData.AREA_OUT || t.water(x, z) > 0 || t.steepness(x, z) > 2) continue;
            int y = ground(x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.above(2)).isAir()) continue;
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ ticking
    public static void tickLevel(ServerLevel level, GameData data) {
        if (data.ending != 0) return;
        for (ServerPlayer p : level.players()) {
            if (!GameManager.huntable(p)) continue;
            tickPlayer(level, p, GameManager.state(p), data);
        }
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        int area = Math.max(0, ps.area);
        tickScripts(level, p, ps, data, area);
        // fire and the bell keep things away
        if (GameManager.flareLit(p) && p.tickCount % 10 == 0) repelAround(level, p, 14.0, true);
        // sighting: the first time a hostile thing is in view, the heart jumps
        if (p.tickCount % 5 == 0) {
            for (SounanMonster m : monstersOf(level, p, 48)) {
                if (m instanceof Utsurumono) continue;
                if (m.getTags().contains("seen")) continue;
                if (GameManager.looksAt(p, m, 0.9, 48.0)) {
                    m.addTag("seen");
                    GameManager.addFear(p, m instanceof Oyamasama ? 18.0f : 9.0f);
                }
            }
        }
        if (area == TerrainData.AREA_OUT) return;
        if (--ps.nextMinor <= 0) {
            minorScare(level, p, ps, data, area);
            float speed = 0.7f + (1.0f - ps.fear / 100.0f) * 0.6f;
            ps.nextMinor = (int) ((20 * 18 + RAND.nextInt(20 * 26)) * speed);
        }
        if (--ps.nextMonster <= 0) {
            if (!hasActive(level, p)) {
                if (spawnMonster(level, p, ps, data, area)) ps.nextMonster = 20 * 55 + RAND.nextInt(20 * 55);
                else ps.nextMonster = 20 * 12;
            } else {
                ps.nextMonster = 20 * 15;
            }
        }
        if (p.tickCount % 200 == 0) tickUtsurumono(level, p, data, area);
    }

    // ------------------------------------------------------------------------------------------------ monsters
    private static boolean spawnMonster(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, int area) {
        if (!data.bagOpened && data.playTicks < 20 * 150) return false;
        boolean act3 = data.keyTaken;
        int r = RAND.nextInt(100);
        switch (area) {
            case TerrainData.AREA_RAKURAKU, TerrainData.AREA_SAWA -> {
                if (!data.bagOpened) return false;
                return r < 55 ? attachUshiro(level, p) : spawnYobigoe(level, p);
            }
            case TerrainData.AREA_SUGI -> {
                if (r < 50) return spawnNozoki(level, p);
                if (r < 75) return spawnYobigoe(level, p);
                return attachUshiro(level, p);
            }
            case TerrainData.AREA_HAISON -> {
                if (r < 40) return spawnNozoki(level, p);
                return r < 65 && spawnYobigoe(level, p);
            }
            case TerrainData.AREA_HOKORA -> {
                return r < 45 && attachUshiro(level, p);
            }
            case TerrainData.AREA_TSURIBASHI -> {
                return r < 40 ? spawnNozoki(level, p) : r < 60 && attachUshiro(level, p);
            }
            case TerrainData.AREA_KOYA, TerrainData.AREA_TETTO -> {
                if (act3) {
                    if (!data.happened("oyama_hunt") && data.happened("oyama_stalk")) {
                        data.once("oyama_hunt");
                        return huntOyama(level, p);
                    }
                    if (r < 45) {
                        data.once("oyama_stalk");
                        return stalkOyama(level, p);
                    }
                }
                if (r < 35) return spawnYobigoe(level, p);
                return r < 70 && attachUshiro(level, p);
            }
            default -> {
                return false;
            }
        }
    }

    static boolean spawnNozoki(ServerLevel level, ServerPlayer p) {
        Vec3 at = trunkSpot(level, p, 13, 26, 1.25);
        if (at == null) return false;
        Nozoki n = spawn(level, ModEntities.NOZOKI.get(), at, yawTo(at, p.position()), p);
        if (n != null) n.peek(RAND.nextBoolean());
        return n != null;
    }

    static boolean spawnYobigoe(ServerLevel level, ServerPlayer p) {
        Vec3 at = openSpot(level, p, 34, 48, 1.6, false);
        if (at == null) return false;
        return spawn(level, ModEntities.YOBIGOE.get(), at, yawTo(at, p.position()), p) != null;
    }

    static boolean attachUshiro(ServerLevel level, ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        if (p.getDeltaMovement().horizontalDistance() < 0.04 && ps.stillTicks > 10) return false;
        if (ps.area == TerrainData.AREA_TUNNEL || ps.area == TerrainData.AREA_HAISON) return false;
        Ushiro u = ModEntities.USHIRO.get().create(level);
        if (u == null) return false;
        u.attach(p);
        level.addFreshEntity(u);
        GameData data = GameData.get(level.getServer());
        if (data.once("ushiro_first")) GameManager.thought(p, "thought.sounan.ushiro");
        return true;
    }

    static boolean stalkOyama(ServerLevel level, ServerPlayer p) {
        Vec3 at = openSpot(level, p, 40, 58, 1.4, false);
        if (at == null) return false;
        Oyamasama o = spawn(level, ModEntities.OYAMASAMA.get(), at, yawTo(at, p.position()), p);
        if (o != null) o.setMode(Oyamasama.MODE_STALK, 20 * 18);
        return o != null;
    }

    static boolean huntOyama(ServerLevel level, ServerPlayer p) {
        Vec3 at = openSpot(level, p, 24, 34, 1.0, true);
        if (at == null) return false;
        Oyamasama o = spawn(level, ModEntities.OYAMASAMA.get(), at, yawTo(at, p.position()), p);
        if (o != null) {
            o.setMode(Oyamasama.MODE_HUNT, 20 * 24);
            GameManager.thought(p, "thought.sounan.run");
        }
        return o != null;
    }

    /** The lost stand ahead along the route, pointing the way out (camera only). */
    private static void tickUtsurumono(ServerLevel level, ServerPlayer p, GameData data, int area) {
        if (area < TerrainData.AREA_SUGI || area == TerrainData.AREA_TETTO) return;
        int have = 0;
        for (SounanMonster m : monstersOf(level, p, 70)) if (m instanceof Utsurumono) have++;
        if (have >= 2) return;
        List<int[]> route = Pois.get().route;
        int best = 0;
        double bestD = 1e18, bestT = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            double ax = route.get(i)[0], az = route.get(i)[1], bx = route.get(i + 1)[0], bz = route.get(i + 1)[1];
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double tt = Mth.clamp(((p.getX() - ax) * dx + (p.getZ() - az) * dz) / l2, 0, 1);
            double px = ax + dx * tt - p.getX(), pz = az + dz * tt - p.getZ();
            double d = px * px + pz * pz;
            if (d < bestD) {
                bestD = d;
                best = i;
                bestT = tt;
            }
        }
        double ahead = 22 + RAND.nextInt(22);
        int seg = best;
        double t = bestT;
        while (seg < route.size() - 1 && ahead > 0) {
            double ax = route.get(seg)[0], az = route.get(seg)[1], bx = route.get(seg + 1)[0], bz = route.get(seg + 1)[1];
            double len = Math.hypot(bx - ax, bz - az);
            double left = (1 - t) * len;
            if (ahead <= left) {
                t += ahead / len;
                ahead = 0;
            } else {
                ahead -= left;
                seg++;
                t = 0;
            }
        }
        if (seg >= route.size() - 1) return;
        double ax = route.get(seg)[0], az = route.get(seg)[1], bx = route.get(seg + 1)[0], bz = route.get(seg + 1)[1];
        double len = Math.hypot(bx - ax, bz - az);
        double ux = (bx - ax) / len, uz = (bz - az) / len;
        double side = (RAND.nextBoolean() ? 1 : -1) * (2.5 + RAND.nextDouble() * 2);
        double x = ax + (bx - ax) * t - uz * side, z = az + (bz - az) * t + ux * side;
        Vec3 at = new Vec3(x, ground(Mth.floor(x), Mth.floor(z)), z);
        float yaw = (float) (Mth.atan2(uz, ux) * Mth.RAD_TO_DEG) - 90.0f;
        Utsurumono u = spawn(level, ModEntities.UTSURUMONO.get(), at, yaw, p);
        if (u != null) u.pointAt(yaw);
    }

    // ------------------------------------------------------------------------------------------------ the little things
    private static void minorScare(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, int area) {
        Vec3 pos = p.position();
        float fear = ps.fear / 100.0f;
        int r = RAND.nextInt(100);
        if (area == TerrainData.AREA_TUNNEL) {
            if (r < 40) behind(p, ModSounds.AMB_DRIP.get(), 6, 0.8f);
            else if (r < 70) behind(p, ModSounds.AMB_FOOTSTEPS_WALK.get(), 14, 0.9f);
            else if (r < 85) behind(p, ModSounds.AMB_THUD.get(), 25, 1.0f);
            else GameManager.privateSound(p, ModSounds.AMB_BREATH_BEHIND.get(), behindPos(p, 1.5), 0.6f, 1.0f);
            return;
        }
        if (area == TerrainData.AREA_HAISON && r < 25) {
            PoiLayout.House radio = PoiLayout.get().houses.get(1);
            Vec3 at = new Vec3(radio.cx() + 0.5, radio.floorY() + 1.5, radio.cz() + 0.5);
            GameManager.privateSound(p, r < 12 ? ModSounds.AMB_RADIO.get() : ModSounds.AMB_TIN_BANG.get(), at, 0.9f, 1.0f);
            return;
        }
        if (r < 14) behind(p, ModSounds.AMB_BRANCH_SNAP.get(), 7 + RAND.nextInt(8), 1.0f);
        else if (r < 20 && (area == TerrainData.AREA_SUGI || area == TerrainData.AREA_KOYA) && !hasActive(level, p)) dasher(level, p);
        else if (r < 24) passing(p, ModSounds.AMB_FOOTSTEPS_RUN.get(), 18);
        else if (r < 32) around(p, ModSounds.AMB_TREE_KNOCK.get(), 20, 30, 1.0f);
        else if (r < 39) around(p, ModSounds.AMB_DEER_SCREAM.get(), 40, 60, 1.0f);
        else if (r < 44) around(p, ModSounds.AMB_FOX_SCREAM.get(), 30, 45, 1.0f);
        else if (r < 51) around(p, ModSounds.AMB_DISTANT_BELL.get(), 35, 50, 1.0f);
        else if (r < 56) around(p, ModSounds.AMB_DISTANT_CALL.get(), 50, 70, 1.0f);
        else if (r < 61) around(p, ModSounds.AMB_ROCKFALL.get(), 20, 35, 1.0f);
        else if (r < 67) behind(p, ModSounds.AMB_GIGGLE.get(), 10 + RAND.nextInt(6), 0.8f);
        else if (r < 72) behind(p, ModSounds.AMB_FOOTSTEPS_WALK.get(), 10, 0.9f);
        else if (r < 76) around(p, ModSounds.AMB_DISTANT_SCREAM.get(), 60, 80, 1.0f);
        else if (r < 80 && fear > 0.45f) {
            double side = RAND.nextBoolean() ? 0.7 : -0.7;
            Vec3 ear = p.getEyePosition().add(Vec3.directionFromRotation(0, p.getYRot() + 90).scale(side));
            GameManager.privateSound(p, ModSounds.AMB_WHISPER_NAME.get(), ear, 0.6f, 1.0f);
        } else if (r < 85) {
            // the forest holds its breath... then something very close
            GameManager.event(p, GameManager.EV_SILENCE, 0, "");
            GameManager.privateSound(p, ModSounds.AMB_BRANCH_SNAP.get(), behindPos(p, 4), 1.0f, 0.8f);
        } else if (r < 90) {
            turnJizo(level, p);
        } else if (r < 94 && data.playTicks - ps.lastScare > 20 * 300) {
            ps.lastScare = data.playTicks;
            String[] texts = {"phone.sounan.buzz.found", "phone.sounan.buzz.behind", "phone.sounan.buzz.come"};
            GameManager.event(p, GameManager.EV_BUZZ, 0, texts[RAND.nextInt(texts.length)]);
            GameManager.addFear(p, 8.0f);
        } else {
            around(p, ModSounds.AMB_THUD.get(), 25, 45, 1.0f);
        }
    }

    /** A shape crossing the view far ahead, from one side to the other. */
    static void dasher(ServerLevel level, ServerPlayer p) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        double dist = 18 + RAND.nextInt(8);
        double sgn = RAND.nextBoolean() ? 1 : -1;
        Vec3 a = p.position().add(fwd.scale(dist)).add(side.scale(10 * sgn));
        Vec3 b = p.position().add(fwd.scale(dist + 3)).add(side.scale(-12 * sgn));
        int ax = Mth.floor(a.x), az = Mth.floor(a.z), bx = Mth.floor(b.x), bz = Mth.floor(b.z);
        TerrainData t = TerrainData.get();
        if (t.area(ax, az) == TerrainData.AREA_OUT || Math.abs(t.height(ax, az) + 1 - p.getY()) > 5) return;
        Vec3 from = new Vec3(a.x, ground(ax, az), a.z), to = new Vec3(b.x, ground(bx, bz), b.z);
        Nozoki n = spawn(level, ModEntities.NOZOKI.get(), from, yawTo(from, to), p);
        if (n != null) n.dash(to);
    }

    static Vec3 behindPos(ServerPlayer p, double dist) {
        Vec3 back = Vec3.directionFromRotation(0, p.getYRot() + 180 + (RAND.nextFloat() - 0.5f) * 70).scale(dist);
        return p.position().add(back).add(0, 1.0, 0);
    }

    static void behind(ServerPlayer p, SoundEvent s, double dist, float vol) {
        GameManager.privateSound(p, s, behindPos(p, dist), vol, 0.9f + RAND.nextFloat() * 0.2f);
    }

    static void around(ServerPlayer p, SoundEvent s, double min, double max, float vol) {
        double ang = RAND.nextDouble() * Math.PI * 2, d = min + RAND.nextDouble() * (max - min);
        GameManager.privateSound(p, s, p.position().add(Math.cos(ang) * d, 2 + RAND.nextDouble() * 6, Math.sin(ang) * d), vol,
                0.9f + RAND.nextFloat() * 0.2f);
    }

    static void passing(ServerPlayer p, SoundEvent s, double dist) {
        Vec3 side = Vec3.directionFromRotation(0, p.getYRot() + (RAND.nextBoolean() ? 90 : -90)).scale(dist);
        GameManager.privateSound(p, s, p.position().add(side).add(0, 1, 0), 1.0f, 1.0f);
    }

    /** Jizo you are not looking at turn to face you. */
    static void turnJizo(ServerLevel level, ServerPlayer p) {
        for (BlockPos j : PoiLayout.get().jizo) {
            if (j.distSqr(p.blockPosition()) > 24 * 24) continue;
            BlockState s = level.getBlockState(j);
            if (!s.is(ModBlocks.JIZO.get())) continue;
            Vec3 to = Vec3.atCenterOf(j).subtract(p.getEyePosition()).normalize();
            if (to.dot(p.getViewVector(1.0f)) > 0.3) continue;
            Direction face = Direction.fromYRot(yawTo(Vec3.atCenterOf(j), p.position()));
            if (s.getValue(FacingBlock.FACING) != face) level.setBlock(j, s.setValue(FacingBlock.FACING, face), 3);
        }
    }

    // ------------------------------------------------------------------------------------------------ scripts
    public static void onAreaEntered(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, int area) {
        PoiLayout L = PoiLayout.get();
        switch (area) {
            case TerrainData.AREA_SUGI -> ps.nextMonster = Math.min(ps.nextMonster, 20 * 25);
            case TerrainData.AREA_TUNNEL -> GameManager.thought(p, "thought.sounan.tunnel");
            case TerrainData.AREA_TOZANGUCHI -> GameManager.thought(p, "thought.sounan.car");
            default -> {
            }
        }
    }

    private static void tickScripts(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, int area) {
        PoiLayout L = PoiLayout.get();
        // residents: the sleepers of the village house and the hut
        if (p.tickCount % 40 == 0) {
            if (area == TerrainData.AREA_HAISON) ensureResident(level, L.villageSleeper, L.villageSleeperFacing);
            if (area == TerrainData.AREA_KOYA || area == TerrainData.AREA_TETTO) ensureResident(level, L.hutSleeper, L.hutSleeperFacing);
        }
        // the public phone rings as you pass
        if (area == TerrainData.AREA_HAISON && p.distanceToSqr(Vec3.atCenterOf(L.publicPhone)) < 14 * 14 && data.once("payphone")) {
            PAYPHONE.put(p.getUUID(), 0);
        }
        Integer ring = PAYPHONE.get(p.getUUID());
        if (ring != null) {
            if (ring % 120 == 0) GameManager.worldSound(level, Vec3.atCenterOf(L.publicPhone), ModSounds.PHONE_RING_OLD.get(), 1.2f, 1.0f);
            if (ring > 20 * 24) PAYPHONE.remove(p.getUUID());
            else PAYPHONE.put(p.getUUID(), ring + 1);
        }
        // something under the bridge
        if (area == TerrainData.AREA_TSURIBASHI && Math.abs(p.getX() - (L.bridgeX + 0.5)) < 2.5
                && p.getZ() > L.bridgeZ0 + 4 && p.getZ() < L.bridgeZ1 - 4 && data.once("under_bridge")) {
            Vec3 below = p.position().add(0, -5, 0);
            GameManager.privateSound(p, ModSounds.AMB_THUD.get(), below, 1.0f, 0.7f);
            GameManager.privateSound(p, ModSounds.NOZOKI_GIGGLE.get(), below.add(0, 2, 0), 0.9f, 0.9f);
            GameManager.event(p, GameManager.EV_SHAKE, 14, "");
            GameManager.event(p, GameManager.EV_STINGER, 6, "");
            GameManager.addFear(p, 18.0f);
        }
        // the tunnel: the lamps come on one by one toward the exit, and it comes from behind
        if (area == TerrainData.AREA_TUNNEL && data.gateUnlocked && p.getZ() > L.tunnelZ0 + 26 && !data.climax) {
            data.climax = true;
            data.setDirty();
            TUNNEL_LAMPS.put(p.getUUID(), 0);
            Vec3 at = new Vec3(L.tunnelX + 0.5, L.roadY(L.tunnelZ0 + 2) + 1, L.tunnelZ0 + 2.5);
            Oyamasama o = spawn(level, ModEntities.OYAMASAMA.get(), at, 180.0f, p);
            if (o != null) o.setMode(Oyamasama.MODE_TUNNEL, 20 * 70);
            GameManager.thought(p, "thought.sounan.run");
            GameManager.event(p, GameManager.EV_STINGER, 10, "");
        }
        Integer lamps = TUNNEL_LAMPS.get(p.getUUID());
        if (lamps != null) {
            int i = lamps / 8;
            if (lamps % 8 == 0 && i < L.tunnelLamps.size()) {
                GameManager.setTunnelLamp(level, L.tunnelLamps.get(i), true);
                GameManager.worldSound(level, Vec3.atCenterOf(L.tunnelLamps.get(i)), ModSounds.ITEM_FLASHLIGHT_FLICKER.get(), 0.6f, 0.6f);
            }
            TUNNEL_LAMPS.put(p.getUUID(), lamps + 1);
            if (p.getZ() > L.tunnelZ1 + 3 || area == TerrainData.AREA_TOZANGUCHI) {
                for (SounanMonster m : monstersOf(level, p, 120)) if (m instanceof Oyamasama) m.vanish();
                for (BlockPos lp : L.tunnelLamps) GameManager.setTunnelLamp(level, lp, false);
                TUNNEL_LAMPS.remove(p.getUUID());
            }
        }
        // if it got you in the tunnel, it comes again
        if (data.climax && area == TerrainData.AREA_TUNNEL && !TUNNEL_LAMPS.containsKey(p.getUUID()) && p.getZ() < L.tunnelZ0 + 20) {
            data.climax = false;
            data.setDirty();
        }
    }

    static void ensureResident(ServerLevel level, BlockPos home, Direction facing) {
        UUID id = RESIDENTS.get(home.asLong());
        if (id != null && level.getEntity(id) instanceof Koyanushi k && k.isAlive()) return;
        for (Koyanushi k : level.getEntitiesOfClass(Koyanushi.class, new AABB(home).inflate(40))) {
            if (home.equals(k.home())) {
                RESIDENTS.put(home.asLong(), k.getUUID());
                return;
            }
        }
        Koyanushi k = ModEntities.KOYANUSHI.get().create(level);
        if (k == null) return;
        k.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, facing.toYRot(), 0.0f);
        k.setHome(home, facing);
        level.addFreshEntity(k);
        RESIDENTS.put(home.asLong(), k.getUUID());
    }

    // ------------------------------------------------------------------------------------------------ hooks
    public static void clearFor(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        for (SounanMonster m : monstersOf(level, p, 128)) {
            if (m instanceof Koyanushi k) k.repel();
            else m.vanish();
        }
        PAYPHONE.remove(p.getUUID());
        TUNNEL_LAMPS.remove(p.getUUID());
        PoiLayout L = PoiLayout.get();
        for (BlockPos lp : L.tunnelLamps) GameManager.setTunnelLamp(level, lp, false);
        PlayerState ps = GameManager.state(p);
        ps.nextMonster = Math.max(ps.nextMonster, 20 * 50);
    }

    public static void calmDown(ServerPlayer p, int ticks) {
        PlayerState ps = GameManager.state(p);
        ps.nextMonster = Math.max(ps.nextMonster, ticks);
        ps.nextMajor = Math.max(ps.nextMajor, ticks);
    }

    static void repelAround(ServerLevel level, ServerPlayer p, double radius, boolean fire) {
        for (SounanMonster m : level.getEntitiesOfClass(SounanMonster.class, new AABB(p.blockPosition()).inflate(radius))) {
            if (m instanceof Nozoki n) n.repel();
            else if (m instanceof Yobigoe y) y.repel();
            else if (m instanceof Ushiro u && m.target() == p) u.letGo(false);
            else if (m instanceof Koyanushi k && fire) k.repel();
            else if (m instanceof Oyamasama o && fire) o.repel();
        }
    }

    public static void onPhoto(ServerLevel level, ServerPlayer p) {
        for (SounanMonster m : level.getEntitiesOfClass(SounanMonster.class, new AABB(p.blockPosition()).inflate(26))) {
            Vec3 to = m.getBoundingBox().getCenter().subtract(p.getEyePosition());
            double d = to.length();
            if (d < 0.1 || to.scale(1 / d).dot(p.getViewVector(1.0f)) < 0.85) continue;
            if (m instanceof Nozoki n && d < 22) n.provoke();
            else if (m instanceof Utsurumono u) u.photographed(p);
            else if (m instanceof Koyanushi k && d < 12) k.wake(p);
        }
    }

    public static void onBell(ServerLevel level, ServerPlayer p) {
        repelAround(level, p, 32.0, false);
    }

    public static void onFlare(ServerLevel level, ServerPlayer p) {
        repelAround(level, p, 20.0, true);
    }

    public static void onCall(ServerLevel level, ServerPlayer p) {
        GameManager.addFear(p, 10.0f);
    }

    public static void answerPayphone(ServerLevel level, ServerPlayer p) {
        if (PAYPHONE.remove(p.getUUID()) != null) {
            GameManager.event(p, GameManager.EV_CALL, 900, p.getGameProfile().getName() + "|0");
            GameManager.addFear(p, 12.0f);
        } else {
            GameManager.thought(p, "thought.sounan.payphone_dead");
        }
    }

    public static void onBagOpened(ServerLevel level, ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        ps.nextMonster = 20 * 45;
    }

    public static void onKeyTaken(ServerLevel level, ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        ps.nextMonster = Math.min(ps.nextMonster, 20 * 35);
    }

    /** Debug / screenshot tour: stage one monster scene for this player the way the director would. */
    public static boolean scene(ServerLevel level, ServerPlayer p, String what) {
        PoiLayout L = PoiLayout.get();
        return switch (what) {
            case "nozoki" -> spawnNozoki(level, p);
            case "yobigoe" -> spawnYobigoe(level, p);
            case "ushiro" -> attachUshiro(level, p) || forceUshiro(level, p);
            case "oyama" -> stalkOyama(level, p);
            case "hunt" -> huntOyama(level, p);
            case "koyanushi" -> {
                ensureResident(level, L.hutSleeper, L.hutSleeperFacing);
                ensureResident(level, L.villageSleeper, L.villageSleeperFacing);
                yield true;
            }
            case "utsurumono" -> {
                tickUtsurumono(level, p, GameData.get(level.getServer()), TerrainData.AREA_SUGI);
                tickUtsurumono(level, p, GameData.get(level.getServer()), TerrainData.AREA_SUGI);
                yield true;
            }
            case "clear" -> {
                for (SounanMonster m : level.getEntitiesOfClass(SounanMonster.class, new AABB(p.blockPosition()).inflate(96))) m.vanish();
                yield true;
            }
            default -> false;
        };
    }

    private static boolean forceUshiro(ServerLevel level, ServerPlayer p) {
        Ushiro u = ModEntities.USHIRO.get().create(level);
        if (u == null) return false;
        u.attach(p);
        level.addFreshEntity(u);
        return true;
    }

    public static void clearAll() {
        RESIDENTS.clear();
        PAYPHONE.clear();
        TUNNEL_LAMPS.clear();
    }
}
