package com.sounan.client;

import com.sounan.client.sound.EntityLoopSound;
import com.sounan.client.sound.LoopSound;
import com.sounan.client.sound.PositionLoopSound;
import com.sounan.entity.Koyanushi;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.Utsurumono;
import com.sounan.game.GameManager;
import com.sounan.item.GameItem;
import com.sounan.registry.ModDimensions;
import com.sounan.registry.ModSounds;
import com.sounan.world.TerrainData;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The sound direction of the night. Beds (forest, insects, wind, tunnel, village), six music layers mixed by heart
 * rate, danger and place (dread with a binaural theta beat underneath everything, micro-tonal tension clusters,
 * the endless Shepard rise of danger, the chase, the final act, the fragile safe theme), the heartbeat at the real
 * bpm, frightened breathing, whispers at one ear, ringing ears — and silence: when something hostile comes close the
 * insects stop.
 */
public final class HorrorAudio {
    private static final RandomSource RAND = RandomSource.create();
    private static LoopSound forest, insects, ridge, tunnel, village, silence;
    private static LoopSound dread, binaural, tension, danger, chase, oyama, safe, flare;
    private static PositionLoopSound stream;
    private static final Map<Integer, EntityLoopSound> ENTITY_LOOPS = new HashMap<>();
    private static float heartbeatIn = 40;
    private static int breathIn = 60;
    private static int tinnitusIn = 300;
    private static int whisperIn = 600;
    private static int lifeIn = 300;
    private static int streamScan;
    private static int safeTimer;
    private static float lastDanger;

    private HorrorAudio() {}

    private static LoopSound ensure(LoopSound loop, SoundEvent event, float ease) {
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && mc.getSoundManager().isActive(loop)) return loop;
        LoopSound l = new LoopSound(event, event == ModSounds.ITEM_FLARE_BURN.get() ? SoundSource.PLAYERS : SoundSource.AMBIENT, ease);
        mc.getSoundManager().play(l);
        return l;
    }

    public static void stopAll() {
        for (LoopSound l : new LoopSound[]{forest, insects, ridge, tunnel, village, silence, dread, binaural, tension, danger, chase, oyama, safe, flare}) {
            if (l != null) l.finish();
        }
        forest = insects = ridge = tunnel = village = silence = null;
        dread = binaural = tension = danger = chase = oyama = safe = flare = null;
        if (stream != null) stream.finish();
        stream = null;
        for (EntityLoopSound s : ENTITY_LOOPS.values()) Minecraft.getInstance().getSoundManager().stop(s);
        ENTITY_LOOPS.clear();
    }

    public static void onCheckpoint() {
        safeTimer = 20 * 35;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ModDimensions.isMountain(mc.level)) {
            if (dread != null || !ENTITY_LOOPS.isEmpty()) stopAll();
            return;
        }
        if (mc.isPaused()) return;
        float fear = ClientState.fearShown / 100.0f;
        float dng = ClientState.dangerShown;
        int area = ClientState.area;
        boolean intro = ClientState.introTicks >= 0 && ClientState.introTicks < 20 * 15;
        boolean ending = ClientState.flag(GameManager.F_ENDING);
        boolean captured = ClientState.flag(GameManager.F_CAPTURED) || ClientState.jumpscareTicks >= 0;
        boolean inTunnel = area == TerrainData.AREA_TUNNEL;
        boolean inVillage = area == TerrainData.AREA_HAISON;
        boolean exposed = area == TerrainData.AREA_TETTO || area == TerrainData.AREA_TSURIBASHI;
        boolean act3 = ClientState.flag(GameManager.F_KEY);
        float hush = ClientState.silenceTicks >= 0 ? Mth.clamp(Math.abs(ClientState.silenceTicks - 80) / 80.0f, 0.0f, 1.0f) * 0.15f : 1.0f;
        if (captured || ending) hush = 0.0f;
        if (intro) hush *= ClientState.introTicks > 20 * 11 ? (ClientState.introTicks - 220) / 80.0f : 0.0f;

        // ---- beds
        forest = ensure(forest, ModSounds.AMB_FOREST.get(), 0.02f);
        insects = ensure(insects, ModSounds.AMB_INSECTS.get(), 0.006f);
        insects.easeDown = 0.16f;
        ridge = ensure(ridge, ModSounds.AMB_WIND_RIDGE.get(), 0.02f);
        tunnel = ensure(tunnel, ModSounds.AMB_TUNNEL.get(), 0.02f);
        village = ensure(village, ModSounds.AMB_VILLAGE.get(), 0.02f);
        silence = ensure(silence, ModSounds.AMB_SILENCE.get(), 0.05f);
        forest.target = (inTunnel ? 0.0f : inVillage ? 0.3f : 0.55f) * (1.0f - 0.45f * dng) * hush;
        boolean quiet = dng > 0.22f || ClientState.silenceTicks >= 0 || inTunnel || fear > 0.85f;
        insects.target = quiet ? 0.0f : (inVillage ? 0.35f : 0.5f) * hush;
        ridge.target = exposed ? 0.6f * hush : 0.0f;
        tunnel.target = inTunnel ? 0.75f * Math.max(hush, 0.3f) : 0.0f;
        village.target = inVillage ? 0.5f * hush : 0.0f;
        silence.target = (ClientState.silenceTicks >= 0 || (dng > 0.3f && dng < 0.7f)) && !captured ? 0.55f : 0.0f;

        // ---- music
        dread = ensure(dread, ModSounds.MUSIC_DREAD.get(), 0.01f);
        binaural = ensure(binaural, ModSounds.MUSIC_BINAURAL.get(), 0.01f);
        tension = ensure(tension, ModSounds.MUSIC_TENSION.get(), 0.02f);
        danger = ensure(danger, ModSounds.MUSIC_DANGER.get(), 0.04f);
        chase = ensure(chase, ModSounds.MUSIC_CHASE.get(), dng > 0.7f ? 0.1f : 0.02f);
        oyama = ensure(oyama, ModSounds.MUSIC_OYAMA.get(), 0.01f);
        safe = ensure(safe, ModSounds.MUSIC_SAFE.get(), 0.008f);
        float chaseV = dng > 0.68f ? Mth.clamp((dng - 0.6f) * 2.6f, 0.0f, 1.0f) : 0.0f;
        float musicHush = captured || ending ? 0.0f : (intro ? 0.0f : 1.0f);
        dread.target = 0.36f * (1.0f - 0.6f * chaseV) * musicHush * (safeTimer > 0 ? 0.4f : 1.0f);
        binaural.target = (0.12f + 0.45f * fear) * musicHush;
        tension.target = fear > 0.4f ? (fear - 0.4f) / 0.6f * 0.65f * (1.0f - chaseV) * musicHush : 0.0f;
        danger.target = dng > 0.22f ? Mth.clamp((dng - 0.22f) * 1.4f, 0.0f, 0.85f) * (1.0f - 0.7f * chaseV) * musicHush : 0.0f;
        chase.target = chaseV * musicHush;
        oyama.target = (act3 || ClientState.flag(GameManager.F_CLIMAX)) && (inTunnel || area == TerrainData.AREA_KOYA
                || area == TerrainData.AREA_TOZANGUCHI) ? 0.42f * (1.0f - chaseV) * musicHush : 0.0f;
        if (safeTimer > 0) safeTimer--;
        safe.target = safeTimer > 0 && dng < 0.1f && fear < 0.5f ? 0.5f * musicHush : 0.0f;
        boolean holdingFlare = mc.player.getMainHandItem().getItem() instanceof GameItem g && g.kind == GameItem.Kind.FLARE_LIT
                || mc.player.getOffhandItem().getItem() instanceof GameItem g2 && g2.kind == GameItem.Kind.FLARE_LIT;
        flare = ensure(flare, ModSounds.ITEM_FLARE_BURN.get(), 0.2f);
        flare.target = holdingFlare ? 0.7f : 0.0f;

        // a sudden spike of danger: the riser is cut, the forest holds its breath
        if (dng - lastDanger > 0.35f) ClientState.silenceTicks = 40;
        lastDanger = dng;

        tickCall();
        if (intro) {
            int it = ClientState.introTicks;
            if (it == 62) ClientPacketHandler.ui(ModSounds.YOBIGOE_CALL.get(), 0.35f, 1.0f);
            if (it == 192) ClientPacketHandler.ui(ModSounds.PLAYER_FALL.get(), 1.0f, 1.0f);
            if (it == 250) ClientPacketHandler.ui(ModSounds.AMB_TINNITUS.get(), 0.5f, 1.0f);
            if (it == 264) ClientPacketHandler.ui(ModSounds.GAME_TITLE_HIT.get(), 0.8f, 1.0f);
        }
        if (captured || intro || ending) return;

        // ---- the heartbeat, at the real rate
        heartbeatIn -= 1.0f;
        if (heartbeatIn <= 0) {
            float f = ClientState.fearShown;
            if (f >= 35.0f) {
                float k = (f - 35.0f) / 65.0f;
                float vol = Mth.lerp(k, 0.18f, 1.0f) + dng * 0.25f;
                ClientPacketHandler.ui(ModSounds.PLAYER_HEARTBEAT.get(), Math.min(1.2f, vol), 1.0f + dng * 0.06f);
                heartbeatIn += 1200.0f / ClientState.bpm();
            } else {
                heartbeatIn = 20;
            }
        }
        // frightened breathing
        if (--breathIn <= 0) {
            if (ClientState.fearShown >= 72.0f) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_FEAR.get(), 0.35f + fear * 0.3f, 1.0f);
            else if (ClientState.flag(GameManager.F_EXHAUSTED)) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_RUN.get(), 0.6f, 1.0f);
            breathIn = 55 + RAND.nextInt(40);
        }
        // ringing in the ears
        if (--tinnitusIn <= 0) {
            if (ClientState.fearShown >= 90.0f) ClientPacketHandler.ui(ModSounds.AMB_TINNITUS.get(), 0.3f, 1.0f);
            tinnitusIn = 300 + RAND.nextInt(300);
        }
        if (ClientState.stingerTicks == 6 || ClientState.wakeTicks == 10) ClientPacketHandler.ui(ModSounds.AMB_TINNITUS.get(), 0.45f, 1.0f);
        // something whispers right at one ear
        if (--whisperIn <= 0) {
            if (ClientState.fearShown >= 78.0f) {
                double side = RAND.nextBoolean() ? 0.7 : -0.7;
                float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
                double rx = -Mth.cos(yaw) * side, rz = -Mth.sin(yaw) * side;
                ClientPacketHandler.near(ModSounds.AMB_WHISPER.get(), rx, 0.0, rz, 0.55f, 0.9f + RAND.nextFloat() * 0.2f);
            }
            whisperIn = 500 + RAND.nextInt(700);
        }
        // the night's own life: owls, branches, a creaking trunk, drips in the tunnel, tin in the village
        if (--lifeIn <= 0) {
            SoundEvent e;
            if (inTunnel) e = ModSounds.AMB_DRIP.get();
            else if (inVillage) e = RAND.nextBoolean() ? ModSounds.AMB_TIN_BANG.get() : ModSounds.AMB_WIND_CHIME.get();
            else if (dng < 0.1f && fear < 0.45f && RAND.nextInt(3) == 0) e = ModSounds.AMB_OWL.get();
            else e = RAND.nextBoolean() ? ModSounds.AMB_CREAK_TREE.get() : ModSounds.AMB_BRANCH_SNAP.get();
            double ang = RAND.nextDouble() * Math.PI * 2, dist = inTunnel ? 6 + RAND.nextDouble() * 14 : 14 + RAND.nextDouble() * 26;
            ClientPacketHandler.near(e, Math.cos(ang) * dist, inTunnel ? 2.0 : RAND.nextDouble() * 6, Math.sin(ang) * dist,
                    0.5f + RAND.nextFloat() * 0.4f, 0.85f + RAND.nextFloat() * 0.3f);
            lifeIn = (inTunnel ? 90 : 360) + RAND.nextInt(inTunnel ? 160 : 600);
        }

        tickStream(mc);
        tickEntityLoops(mc);
    }

    /** A phone call (yours at the pylon, or the public phone): static and a voice that is not quite a voice. */
    private static void tickCall() {
        int t = ClientState.callTicks;
        if (t < 0 || ClientState.callKind <= 0) return;
        int end = ClientState.callKind == 900 ? 20 * 20 : 20 * 26;
        if (t % 120 == 1 && t < end) ClientPacketHandler.ui(ModSounds.PHONE_CALL_STATIC.get(), 0.55f, 1.0f);
        if (t > 50 && t % 70 == 30 && t < end) ClientPacketHandler.ui(ModSounds.PHONE_VOICE.get(), 0.7f, 0.88f + RAND.nextFloat() * 0.15f);
        if (t == end) ClientPacketHandler.ui(ModSounds.PHONE_HANGUP.get(), 0.7f, 1.0f);
    }

    private static void tickStream(Minecraft mc) {
        if (++streamScan < 40 && stream != null) return;
        streamScan = 0;
        TerrainData t = TerrainData.get();
        int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
        double best = 1e9;
        int bx = 0, bz = 0, by = 0;
        for (int dx = -22; dx <= 22; dx += 2) {
            for (int dz = -22; dz <= 22; dz += 2) {
                int w = t.water(px + dx, pz + dz);
                if (w <= 0) continue;
                double d = dx * dx + dz * dz + (w - mc.player.getY()) * (w - mc.player.getY()) * 0.5;
                if (d < best) {
                    best = d;
                    bx = px + dx;
                    bz = pz + dz;
                    by = w;
                }
            }
        }
        if (stream == null || !mc.getSoundManager().isActive(stream)) {
            stream = new PositionLoopSound(ModSounds.AMB_STREAM.get(), SoundSource.AMBIENT, bx + 0.5, by, bz + 0.5);
            mc.getSoundManager().play(stream);
        }
        if (best < 1e9) {
            stream.tx = bx + 0.5;
            stream.ty = by + 0.5;
            stream.tz = bz + 0.5;
            stream.target = 0.9f;
        } else {
            stream.target = 0.0f;
        }
    }

    private static void tickEntityLoops(Minecraft mc) {
        boolean camera = mc.player.getMainHandItem().getItem() instanceof GameItem g && g.kind == GameItem.Kind.PHONE_CAMERA
                || mc.player.getOffhandItem().getItem() instanceof GameItem g2 && g2.kind == GameItem.Kind.PHONE_CAMERA;
        for (Entity e : mc.level.entitiesForRendering()) {
            SoundEvent ev = null;
            java.util.function.Predicate<Entity> active = null;
            float vol = 1.0f;
            if (e instanceof Koyanushi) {
                ev = ModSounds.KOYANUSHI_SLEEP.get();
                active = ent -> ent instanceof Koyanushi k && k.pose() == Koyanushi.SLEEP;
            } else if (e instanceof Oyamasama) {
                ev = ModSounds.OYAMA_NEAR.get();
                active = ent -> true;
            } else if (e instanceof Utsurumono && camera) {
                ev = ModSounds.UTSURU_HUM.get();
                vol = 0.6f;
                active = ent -> isCamera(Minecraft.getInstance());
            }
            if (ev == null || e.distanceTo(mc.player) > 40) continue;
            EntityLoopSound s = ENTITY_LOOPS.get(e.getId());
            if (s == null || !mc.getSoundManager().isActive(s)) {
                s = new EntityLoopSound(ev, e, vol, active);
                mc.getSoundManager().play(s);
                ENTITY_LOOPS.put(e.getId(), s);
            }
        }
        ENTITY_LOOPS.entrySet().removeIf(en -> {
            if (en.getValue().entity().isRemoved() || en.getValue().entity().distanceTo(mc.player) > 48) {
                mc.getSoundManager().stop(en.getValue());
                return true;
            }
            return false;
        });
    }

    static boolean isCamera(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() instanceof GameItem g && g.kind == GameItem.Kind.PHONE_CAMERA
                || mc.player.getOffhandItem().getItem() instanceof GameItem g2 && g2.kind == GameItem.Kind.PHONE_CAMERA;
    }
}
