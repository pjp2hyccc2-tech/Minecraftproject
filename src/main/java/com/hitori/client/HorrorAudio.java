package com.hitori.client;

import com.hitori.client.sound.EntityLoopSound;
import com.hitori.client.sound.LoopSound;
import com.hitori.client.sound.PositionLoopSound;
import com.hitori.entity.OniDoll;
import com.hitori.game.Clock;
import com.hitori.game.GameData;
import com.hitori.game.GameManager;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The sound direction of the night (docs/DESIGN.md §8): the apartment at 3 a.m. is silent — the beds by where you are
 * (the apartment's rain and fridge, the closet's close dead air, the tiled bathroom, the corridor's rain), the CRT's
 * static (the most important sound of the game) at the TV whenever it is on, the camera microphone's hiss and the
 * laptop's fan while you watch a feed; music layers by phase, fear and danger; your body: the heartbeat at its real
 * rate (loud while you hold your breath), frightened breathing — muffled and gurgling with salt water in the mouth;
 * the doll's dragging. While the listener is a camera, what happens close to your body is still heard, faintly
 * ({@link #bodyEcho}).
 */
public final class HorrorAudio {
    private static final RandomSource RAND = RandomSource.create();
    private static LoopSound apartment, closet, bath, outside, silence, micHiss, fan;
    private static LoopSound prep, dread, tension, danger, chase;
    private static PositionLoopSound tvStatic;
    private static final Map<Integer, EntityLoopSound> DRAG = new HashMap<>();
    private static float heartbeatIn = 40;
    private static int breathIn = 60;
    private static float lastDanger;

    private HorrorAudio() {}

    private static LoopSound ensure(LoopSound loop, SoundEvent event, float ease, SoundSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && mc.getSoundManager().isActive(loop)) return loop;
        LoopSound l = new LoopSound(event, source, ease);
        mc.getSoundManager().play(l);
        return l;
    }

    public static void stopAll() {
        for (LoopSound l : new LoopSound[]{apartment, closet, bath, outside, silence, micHiss, fan, prep, dread, tension, danger, chase}) {
            if (l != null) l.finish();
        }
        apartment = closet = bath = outside = silence = micHiss = fan = null;
        prep = dread = tension = danger = chase = null;
        if (tvStatic != null) tvStatic.finish();
        tvStatic = null;
        for (EntityLoopSound s : DRAG.values()) Minecraft.getInstance().getSoundManager().stop(s);
        DRAG.clear();
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ClientState.inGame()) {
            if (apartment != null || dread != null || tvStatic != null) stopAll();
            return;
        }
        if (mc.isPaused() || mc.screen instanceof com.hitori.client.gui.EndingScreen) return;
        float fear = ClientState.fearShown;
        float dng = ClientState.dangerShown;
        int phase = ClientState.phase;
        boolean caught = ClientState.flag(GameManager.F_CAUGHT) || ClientState.jumpscareTicks >= 0 || ClientState.caughtTicks >= 0;
        boolean ending = ClientState.flag(GameManager.F_ENDING) || ClientState.morningTicks >= 0;
        boolean intro = ClientState.introTicks >= 0 && ClientState.introTicks < ClientState.introLength() - 40;
        boolean holding = ClientState.breathHeld();
        boolean app = CameraApp.viewing();
        boolean inCloset = ClientState.inCloset();
        Layout.Room room = Layout.roomAt(mc.player.blockPosition());
        String key = room == null ? "outside" : room.key();
        if (app) {
            Entity cam = mc.getCameraEntity();
            Layout.Room cr = cam == null ? null : Layout.roomAt(cam.blockPosition());
            key = cr == null ? "outside" : cr.key();
        }
        float hush = caught || intro ? 0.0f : ending ? Math.max(0.0f, 1.0f - Math.max(0, ClientState.morningTicks) / 80.0f) : 1.0f;

        // ---- beds
        apartment = ensure(apartment, ModSounds.AMB_APARTMENT.get(), 0.03f, SoundSource.AMBIENT);
        closet = ensure(closet, ModSounds.AMB_CLOSET.get(), 0.04f, SoundSource.AMBIENT);
        bath = ensure(bath, ModSounds.AMB_BATH.get(), 0.03f, SoundSource.AMBIENT);
        outside = ensure(outside, ModSounds.AMB_OUTSIDE.get(), 0.03f, SoundSource.AMBIENT);
        silence = ensure(silence, ModSounds.AMB_SILENCE.get(), 0.05f, SoundSource.AMBIENT);
        boolean out = key.equals("outside") || key.equals("corridor");
        apartment.target = (out ? 0.1f : inCloset && !app ? 0.18f : 0.5f) * hush;
        closet.target = inCloset && !app ? 0.65f * hush : 0.0f;
        bath.target = (key.equals("bath") || key.equals("washroom")) && !inCloset ? 0.45f * hush : 0.0f;
        outside.target = out ? 0.65f * hush : (key.equals("tatami") || key.equals("western") ? 0.08f : 0.03f) * hush;
        if (dng - lastDanger > 0.3f) ClientState.stingerTicks = Math.max(ClientState.stingerTicks, 0);
        lastDanger = dng;
        silence.target = (dng > 0.5f && dng < 0.95f) || ClientState.scareTicks >= 0 ? 0.5f : 0.0f;
        // the CRT's static at the TV: always there once it is on
        if (ClientState.flag(GameManager.F_TV) && Layout.has("tv")) {
            if (tvStatic == null || !mc.getSoundManager().isActive(tvStatic)) {
                Vec3 at = Layout.marker("tv").center().add(0.0, 0.7, 0.0);
                tvStatic = new PositionLoopSound(ModSounds.AMB_TV_STATIC.get(), SoundSource.BLOCKS, at.x, at.y, at.z);
                mc.getSoundManager().play(tvStatic);
            }
            tvStatic.target = 0.85f * (caught ? 0.0f : 1.0f);
        } else if (tvStatic != null) {
            tvStatic.target = 0.0f;
        }
        // the feed: the microphone's hiss, the laptop's fan
        micHiss = ensure(micHiss, ModSounds.CAMERA_MIC_HISS.get(), 0.2f, SoundSource.PLAYERS);
        fan = ensure(fan, ModSounds.LAPTOP_FAN.get(), 0.1f, SoundSource.PLAYERS);
        micHiss.target = app ? 0.35f : 0.0f;
        fan.target = CameraApp.isOpen() && CameraApp.device() == 0 ? 0.18f : 0.0f;

        // ---- music
        prep = ensure(prep, ModSounds.MUSIC_PREP.get(), 0.01f, SoundSource.MUSIC);
        dread = ensure(dread, ModSounds.MUSIC_DREAD.get(), 0.01f, SoundSource.MUSIC);
        tension = ensure(tension, ModSounds.MUSIC_TENSION.get(), 0.02f, SoundSource.MUSIC);
        danger = ensure(danger, ModSounds.MUSIC_DANGER.get(), 0.05f, SoundSource.MUSIC);
        boolean hunted = phase == GameData.PHASE_FINISH && dng > 0.85f && !ClientState.hiding();
        chase = ensure(chase, ModSounds.MUSIC_CHASE.get(), hunted ? 0.12f : 0.02f, SoundSource.MUSIC);
        float musicHush = caught || intro || ending ? 0.0f : 1.0f;
        float chaseV = hunted ? 0.9f : 0.0f;
        prep.target = phase == GameData.PHASE_PREP ? 0.28f * musicHush : 0.0f;
        // phase 3 is silence: the TV and the rain
        boolean quiet = phase == GameData.PHASE_FINISH && !hunted;
        dread.target = (phase == GameData.PHASE_HIDING || phase == GameData.PHASE_RITUAL && ClientState.clock > Clock.THREE + 120) && !quiet
                ? 0.26f * (1.0f - 0.7f * chaseV) * musicHush : 0.0f;
        tension.target = fear > 0.4f && !quiet ? (fear - 0.4f) / 0.6f * 0.55f * (1.0f - chaseV) * musicHush : 0.0f;
        danger.target = dng > 0.25f && !quiet ? Mth.clamp((dng - 0.25f) * 1.3f, 0.0f, 0.85f) * (1.0f - 0.8f * chaseV) * musicHush : 0.0f;
        chase.target = chaseV * musicHush;
        if (caught || intro) {
            for (EntityLoopSound s : DRAG.values()) mc.getSoundManager().stop(s);
            DRAG.clear();
            return;
        }

        // ---- the body
        heartbeatIn -= 1.0f;
        if (heartbeatIn <= 0) {
            if (fear >= 0.3f || holding) {
                float k = Mth.clamp((fear - 0.3f) / 0.7f, 0.0f, 1.0f);
                float vol = Mth.lerp(k, 0.15f, 0.9f) + dng * 0.2f + (holding ? 0.45f : 0.0f) + (inCloset ? 0.15f : 0.0f);
                ClientPacketHandler.ui(ModSounds.PLAYER_HEARTBEAT.get(), Math.min(1.3f, vol), 1.0f + dng * 0.05f);
                heartbeatIn += 1200.0f / ClientState.bpm();
            } else {
                heartbeatIn = 20;
            }
        }
        if (--breathIn <= 0) {
            if (!holding) {
                if (ClientState.mouth > 0.01f) {
                    if (fear >= 0.45f) ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_MOUTH.get(), 0.2f + fear * 0.25f, 1.0f);
                } else if (fear >= 0.7f) {
                    ClientPacketHandler.ui(ModSounds.PLAYER_BREATH_FEAR.get(), 0.3f + fear * 0.3f, 1.0f);
                }
            }
            breathIn = 50 + RAND.nextInt(40);
        }
        tickDrag(mc);
    }

    /** The doll dragging itself along the floor. */
    private static void tickDrag(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof OniDoll d)) continue;
            EntityLoopSound s = DRAG.get(e.getId());
            if (s != null && mc.getSoundManager().isActive(s)) continue;
            if (!d.flag(OniDoll.F_MOVING) || d.pose() != OniDoll.CRAWL) continue;
            s = new EntityLoopSound(ModSounds.DOLL_DRAG_LOOP.get(), e, 0.7f,
                    en -> !en.isRemoved() && ((OniDoll) en).flag(OniDoll.F_MOVING) && ((OniDoll) en).pose() == OniDoll.CRAWL);
            mc.getSoundManager().play(s);
            DRAG.put(e.getId(), s);
        }
        DRAG.entrySet().removeIf(en -> {
            if (en.getValue().entity().isRemoved()) {
                mc.getSoundManager().stop(en.getValue());
                return true;
            }
            return false;
        });
    }

    /** A copy of a near-body sound, played quietly close to the listener (no position): not echoed again. */
    public static final class BodySound extends SimpleSoundInstance {
        public BodySound(SoundEvent event, float volume, float pitch) {
            super(event.getLocation(), SoundSource.AMBIENT, volume, pitch, RAND, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }
    }

    /**
     * The listener is a camera: a sound right by your body (in the closet: the doll at the fusuma, knocking, your own
     * breath) is still heard, quietly and without a position — the muffled bit of what is near you.
     */
    public static void bodyEcho(SoundInstance sound) {
        if (sound instanceof BodySound || !CameraApp.viewing() || sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        double dBody = at.distanceTo(mc.player.getEyePosition());
        Entity cam = mc.getCameraEntity();
        double dCam = cam == null ? 99 : at.distanceTo(cam.getEyePosition());
        if (dBody > 7.0 || dCam < 4.0) return;
        SoundEvent ev = ClientPacketHandler.event(sound.getLocation().toString());
        float vol = (float) (0.35 * (1.0 - dBody / 7.0));
        if (vol > 0.03f) mc.getSoundManager().play(new BodySound(ev, vol, 1.0f));
    }
}
