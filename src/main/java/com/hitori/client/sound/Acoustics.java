package com.hitori.client.sound;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.audio.Channel;
import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.world.Layout;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * 音MOD: the acoustics of コーポ白樺 203 (docs/DESIGN.md §10), a small built-in version of what sound-physics mods do.
 * <ul>
 * <li><b>Room reverb</b>: one OpenAL EFX auxiliary slot with an EAX reverb whose preset follows the listener's room
 * ({@link Layout#roomAt}: {@code tiny_dead} the 押し入れ, {@code restroom} the tiled bath and toilet, {@code small_soft}
 * the tatami and western rooms, {@code small_hard} the genkan / DK / washroom, {@code corridor} the hall; outside = a
 * slap back from the street), cross-faded over 0.5 s. Sounds that start in another room than the listener's get half
 * the send.</li>
 * <li><b>Occlusion</b>: every positional sound gets a low-pass on its direct path from a block ray march between the
 * listener and the sound (opaque blocks −3 dB HF each, shut doors half, the paper fusuma less, cap −24 dB); looping and
 * moving sounds are re-evaluated every 4 ticks.</li>
 * <li><b>Closet</b> ({@link AcousticsState#enclosure()}): hiding in the 押し入れ, everything outside it is low-passed
 * hard (−18 dB in the 6-12 kHz band, −3 dB overall, measured), what touches the fusuma less, the player's own sounds
 * stay dry and close; the room preset becomes the dead {@code closet}. The apartment's non-positional beds
 * ({@code amb.apartment}, {@code amb.bath}, {@code amb.outside}) are muffled the same way.</li>
 * <li><b>Camera listening</b> ({@link AcousticsState#cameraView()}): with the camera app open the listener is the
 * camera entity (Minecraft does that by itself once the game sets the camera entity). Sounds nearer the camera than
 * the player's body get the microphone's band-limit (−12 dB under ~200 Hz and over ~6 kHz, measured) and
 * {@code camera.mic_hiss} plays; sounds nearer the body (the doll at the fusuma while you watch another room) are heard
 * as at the body: muffled, their fall-off rescaled to their distance to the body, spread around the listener.</li>
 * </ul>
 * Threads: the level is read only on the client thread (when a sound is played: {@link PlaySoundEvent}, and in the
 * client tick); OpenAL is touched only on the sound engine's thread (Forge's source events, which carry the
 * {@link Channel}, and tasks handed to the engine's {@link SoundEngineExecutor}). Relative sounds other than those beds
 * (music, UI, the player's own voice) and every dimension but {@code hitori:shirakaba} are left alone. Client config
 * {@code sound.acoustics} ({@link HitoriConfig#acoustics()}) switches it off. Fail-safe: no EFX = nothing happens; any
 * exception disables the engine for the session with one log line, sound keeps working. Press F3 to see its state
 * ({@code -Dhitori.acoustics.debug=true} also logs every room change).
 * <p>
 * Game API: {@link #setState(AcousticsState)} (optional: the closet / camera state; without it the engine reads
 * {@code com.hitori.client.ClientState} reflectively or observes by itself), {@link #setMicHiss(boolean)} (default on:
 * the engine plays {@code camera.mic_hiss} during camera view; turn it off if the game plays it itself).
 */
@Mod.EventBusSubscriber(modid = Hitori.MODID, value = Dist.CLIENT)
public final class Acoustics {
    private static final boolean DEBUG = Boolean.getBoolean("hitori.acoustics.debug");
    /** Ticks between re-evaluations of looping / moving sources (§10: every 4 ticks). */
    private static final int UPDATE_INTERVAL = 4;
    /** Cross-fade between room presets: 10 ticks = 0.5 s. */
    private static final int FADE_TICKS = 10;
    private static final int MAX_TRACKS = 128;
    /** Send level of a sound that started in another room than the listener's. */
    private static final float OTHER_ROOM_SEND = 0.5f;
    /** Occlusion per block unit (an opaque block = 1, a shut door = 0.5): high-frequency loss (§10: 3 dB) ... */
    static final float HF_DB_PER_BLOCK = 3.0f;
    /** ... a small broadband loss on top (capped at 6 dB) ... */
    static final float DB_PER_BLOCK = 1.2f;
    /** ... and the reverb send, which reaches us around the obstacle, loses half as much. */
    static final float SEND_HF_DB_PER_BLOCK = 1.5f;

    /** Closet, shut in: outside sounds' GAINHF (OpenAL Soft's 5 kHz shelf: 0.28 = −18 dB at 6-12 kHz, measured by
     *  tools/audio/efx_selftest.py, relative to the low band) and gain (−3 dB). */
    static final float CLOSET_HF = 0.28f, CLOSET_GAIN = 0.708f;
    /** Sounds touching the hiding place (a knock on the fusuma, the doll right outside it): half the closet loss. */
    static final float BOUNDARY_HF = 0.53f, BOUNDARY_GAIN = 0.89f;
    /** Within this distance of the body a sound is the player's own: dry and close. */
    static final double OWN_RADIUS = 1.0, BOUNDARY_RADIUS = 2.3;
    static final float OWN_SEND = 0.3f;
    /** Camera microphone: band-pass shelves (LF reference 250 Hz, HF reference 5 kHz) and gain. */
    static final float MIC_LF = 0.30f, MIC_HF = 0.40f, MIC_GAIN = 1.0f;
    /** Sounds heard at the body during camera view: muffled (attention on the screen), mostly dry. */
    static final float BODY_HF = 0.5f, BODY_GAIN = 0.8f, BODY_SEND = 0.25f;
    /** Only sounds this close to the body take the body path during camera view. */
    static final double BODY_RADIUS = 14.0;
    /** The apartment's non-positional beds, muffled with the closet / camera view. */
    private static final Set<String> WORLD_BEDS = Set.of("amb.apartment", "amb.bath", "amb.outside");
    /** Mods that do the same job: if one is loaded this engine stays out of its way. */
    private static final String[] OTHER_ACOUSTICS_MODS = {"sound_physics_remastered", "soundphysics"};

    static final int PATH_ROOM = 0, PATH_MIC = 1, PATH_BODY = 2, PATH_BED = 3;

    /**
     * Filters and sends of one source. lfGain < 1 = band-pass (the microphone), maxScale / radius = the body path's
     * distance rescaling and spread.
     */
    public record Params(float directGain, float directHF, float sendGain, float sendHF, float occlusion, String room,
                         float lfGain, float maxScale, float radius, int path) {
        boolean differs(Params o) {
            return o == null || o.path != path || Math.abs(directHF - o.directHF) > 0.02f
                    || Math.abs(directGain - o.directGain) > 0.02f || Math.abs(sendGain - o.sendGain) > 0.03f
                    || Math.abs(sendHF - o.sendHF) > 0.03f || Math.abs(lfGain - o.lfGain) > 0.02f
                    || Math.abs(maxScale - o.maxScale) > 0.05f * Math.max(1f, o.maxScale) || Math.abs(radius - o.radius) > 0.5f;
        }
    }

    /** A playing source (created on the sound thread, then owned by the client thread). */
    private static final class Track {
        final SoundInstance instance;
        final Channel channel;
        final int source;
        final boolean bed;
        final float origMax;
        Params params;

        Track(SoundInstance instance, Channel channel, int source, boolean bed, float origMax, Params params) {
            this.instance = instance;
            this.channel = channel;
            this.source = source;
            this.bed = bed;
            this.origMax = origMax;
            this.params = params;
        }
    }

    private static volatile boolean broken;
    private static volatile boolean restorePending;
    private static volatile String failure;
    private static volatile Executor soundExecutor;
    private static volatile boolean active;
    private static volatile float lastOcclusion;

    /** Played on the client thread, waiting for their channel (weak, identity keys). */
    private static final ConcurrentMap<SoundInstance, Params> PENDING = new MapMaker().weakKeys().concurrencyLevel(2).makeMap();
    /** Sources that just got their channel (sound thread -> client thread). */
    private static final ConcurrentLinkedQueue<Track> FRESH = new ConcurrentLinkedQueue<>();

    // ---- client thread only
    private static final List<Track> TRACKS = new ArrayList<>();
    private static String presetKey = "off";
    private static float[] fadeFrom = AcousticsPresets.get("off");
    private static float[] fadeTo = fadeFrom.clone();
    private static final float[] CURRENT = fadeFrom.clone();
    private static int fadeTick = FADE_TICKS;
    private static boolean presetDirty = true;
    private static String listenerRoom;
    private static String bodyRoom;
    private static boolean forceUpdate;
    private static int tick;
    private static boolean triedEngine;
    private static Boolean otherMod;

    private Acoustics() {}

    // ------------------------------------------------------------------------------------------------------------
    // game API

    /** The game's closet / camera state (polled every client tick on the client thread); null = fall back. */
    public static void setState(AcousticsState state) {
        AcousticsModes.api = state;
    }

    /** Whether the engine itself plays {@code camera.mic_hiss} while the camera view is active (default true). */
    public static void setMicHiss(boolean on) {
        AcousticsModes.micHiss = on;
    }

    /** The closet enclosure the engine is using this tick (0..1). */
    public static float enclosure() {
        return AcousticsModes.enclosure();
    }

    /** Whether the engine is in camera listening this tick. */
    public static boolean cameraListening() {
        return AcousticsModes.cameraView();
    }

    // ------------------------------------------------------------------------------------------------------------
    // client thread

    /** A sound is about to play: compute its filters and send while we may still read the level. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        if (broken || !active) return;
        try {
            SoundInstance s = event.getSound();
            if (s == null) return;
            boolean bed = isWorldBed(s);
            if (!bed && (s.isRelative() || s.getAttenuation() != SoundInstance.Attenuation.LINEAR)) return;
            captureExecutor(event.getEngine());
            Params p = compute(s.getX(), s.getY(), s.getZ(), null, bed);
            if (p != null) PENDING.put(s, p);
        } catch (Throwable t) {
            fail("play", t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inDim = mc.level != null && Layout.DIMENSION.equals(mc.level.dimension());
        boolean modesChanged = false;
        try {
            modesChanged = AcousticsModes.tick(mc, inDim);
        } catch (Throwable t) {
            Hitori.LOGGER.debug("[hitori] acoustics: listening modes unavailable this tick", t);
        }
        if (broken) {
            if (restorePending) {
                restorePending = false;
                try {
                    detachAll();
                } catch (Throwable ignored) {
                    // already disabled
                }
            }
            return;
        }
        try {
            if (modesChanged) forceUpdate = true;
            tickClient(mc, inDim);
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    private static void tickClient(Minecraft mc, boolean inDim) {
        tick++;
        if (soundExecutor == null && !triedEngine) {
            triedEngine = true;
            captureExecutor(engineOf(mc.getSoundManager()));
        }
        ClientLevel level = mc.level;
        if (otherMod == null) otherMod = otherAcousticsMod();
        boolean want = HitoriConfig.acoustics() && !otherMod && level != null && inDim;
        if (!want) {
            if (active) {
                active = false;
                listenerRoom = bodyRoom = null;
                setPreset("off", true);
                detachAll();
            }
            advanceFade();
            flushPreset();
            return;
        }
        active = true;
        Vec3 ear = listenerPos(mc);
        Layout.Room room = Layout.roomAt(BlockPos.containing(ear));
        listenerRoom = room == null ? null : room.key();
        Layout.Room broom = mc.player == null ? room : Layout.roomAt(BlockPos.containing(mc.player.getEyePosition()));
        bodyRoom = broom == null ? null : broom.key();
        String key = room == null ? "outside" : room.acoustics();
        if (AcousticsModes.enclosure() >= 0.5f && !AcousticsModes.cameraView()) key = "closet";
        if (!AcousticsPresets.known(key)) key = "medium";
        if (!key.equals(presetKey)) setPreset(key, "off".equals(presetKey));
        advanceFade();
        flushPreset();
        if (forceUpdate || tick % UPDATE_INTERVAL == 0) {
            updateTracks(mc, forceUpdate);
            forceUpdate = false;
        }
    }

    private static void setPreset(String key, boolean immediate) {
        if (DEBUG) Hitori.LOGGER.info("[hitori] acoustics: room preset {} -> {}", presetKey, key);
        presetKey = key;
        fadeFrom = CURRENT.clone();
        fadeTo = AcousticsPresets.get(key);
        fadeTick = immediate ? FADE_TICKS - 1 : 0;
        presetDirty = true;
    }

    private static void advanceFade() {
        if (fadeTick >= FADE_TICKS) return;
        fadeTick++;
        float a = (float) fadeTick / FADE_TICKS;
        a = a * a * (3f - 2f * a);
        for (int i = 0; i < CURRENT.length; i++) CURRENT[i] = fadeFrom[i] + (fadeTo[i] - fadeFrom[i]) * a;
        presetDirty = true;
    }

    private static void flushPreset() {
        Executor ex = soundExecutor;
        if (!presetDirty || ex == null) return;
        presetDirty = false;
        AcousticsEfx.wanted = CURRENT.clone();
        ex.execute(Acoustics::syncTask);
    }

    private static void updateTracks(Minecraft mc, boolean force) {
        Track fresh;
        while ((fresh = FRESH.poll()) != null) TRACKS.add(fresh);
        while (TRACKS.size() > MAX_TRACKS) TRACKS.remove(0);
        Executor ex = soundExecutor;
        if (ex == null) return;
        SoundManager sm = mc.getSoundManager();
        for (Iterator<Track> it = TRACKS.iterator(); it.hasNext(); ) {
            Track t = it.next();
            if (!sm.isActive(t.instance)) {
                it.remove();
                continue;
            }
            Params np = compute(t.instance.getX(), t.instance.getY(), t.instance.getZ(), force ? null : t.params, t.bed);
            if (np == null || !np.differs(t.params)) continue;
            t.params = np;
            Channel ch = t.channel;
            int src = t.source;
            float om = t.origMax;
            ex.execute(() -> {
                try {
                    AcousticsEfx.update(ch, src, np, om);
                } catch (Throwable e) {
                    fail("update", e);
                }
            });
        }
    }

    private static void detachAll() {
        Track fresh;
        while ((fresh = FRESH.poll()) != null) TRACKS.add(fresh);
        Executor ex = soundExecutor;
        if (ex != null) {
            for (Track t : TRACKS) {
                Channel ch = t.channel;
                int src = t.source;
                float om = t.origMax;
                ex.execute(() -> {
                    try {
                        AcousticsEfx.detach(ch, src, om);
                    } catch (Throwable e) {
                        fail("detach", e);
                    }
                });
            }
        }
        TRACKS.clear();
        PENDING.clear();
    }

    /**
     * Filters and send for a sound at (x, y, z) (bed: one of the apartment's non-positional beds), smoothed toward
     * the previous value for moving sources.
     */
    private static Params compute(double x, double y, double z, Params prev, boolean bed) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;
        float enc = AcousticsModes.enclosure();
        boolean cam = AcousticsModes.cameraView();
        if (bed) {  // around the body: only the closet and the attention on the camera feed muffle it
            float hf = 1f, g = 1f;
            if (enc > 0f) {
                hf *= lerp(1f, CLOSET_HF, enc);
                g *= lerp(1f, CLOSET_GAIN, enc);
            }
            if (cam) {
                hf *= BODY_HF;
                g *= BODY_GAIN;
            }
            return new Params(g, hf, 0f, 1f, 0f, null, 1f, 1f, 0f, PATH_BED);
        }
        Vec3 ear = listenerPos(mc);
        Vec3 body = mc.player != null ? mc.player.getEyePosition() : ear;
        Vec3 src = new Vec3(x, y, z);
        double dEar = src.distanceTo(ear);
        double dBody = src.distanceTo(body);
        int path = !cam ? PATH_ROOM : (dBody < dEar && dBody < BODY_RADIUS ? PATH_BODY : PATH_MIC);
        Vec3 from = path == PATH_BODY ? body : ear;
        float occ = AcousticsOcclusion.march(level, from, src);
        if (prev != null && prev.path() == path) occ = prev.occlusion() + (occ - prev.occlusion()) * 0.6f;
        lastOcclusion = occ;
        String room = roomNear(BlockPos.containing(x, y, z), path == PATH_BODY ? bodyRoom : listenerRoom);
        float o = Math.min(occ, AcousticsOcclusion.CAP);
        float gain = db(-DB_PER_BLOCK * Math.min(o, 5f));
        float hf = db(-HF_DB_PER_BLOCK * o);
        float sendHF = db(-SEND_HF_DB_PER_BLOCK * o);
        float send = (Objects.equals(room, path == PATH_BODY ? bodyRoom : listenerRoom) ? 1f : OTHER_ROOM_SEND) * sendHF;
        float lf = 1f, maxScale = 1f, radius = 0f;
        if (path != PATH_MIC && enc > 0f) {  // shut in a hiding place: where is the sound, seen from the body?
            if (dBody < OWN_RADIUS) {
                send *= lerp(1f, OWN_SEND, enc);
            } else if (room != null && room.equals(bodyRoom) && "closet".equals(bodyRoom)) {
                send *= lerp(1f, 0.5f, enc);
            } else if (dBody < BOUNDARY_RADIUS) {
                hf *= lerp(1f, BOUNDARY_HF, enc);
                gain *= lerp(1f, BOUNDARY_GAIN, enc);
            } else {
                hf *= lerp(1f, CLOSET_HF, enc);
                gain *= lerp(1f, CLOSET_GAIN, enc);
                send *= lerp(1f, 0.5f, enc);
            }
        }
        if (path == PATH_MIC) {
            lf = MIC_LF;
            hf *= MIC_HF;
            gain *= MIC_GAIN;
        } else if (path == PATH_BODY) {
            if (dBody >= OWN_RADIUS) {
                hf *= BODY_HF;
                gain *= BODY_GAIN;
            }
            send *= BODY_SEND;
            maxScale = (float) Math.max(1.0, dEar / Math.max(dBody, 0.25));
            radius = (float) Math.max(1.0, dEar);
        }
        return new Params(gain, hf, send, sendHF, occ, room, lf, maxScale, radius, path);
    }

    private static boolean isWorldBed(SoundInstance s) {
        ResourceLocation id = s.getLocation();
        return id != null && Hitori.MODID.equals(id.getNamespace()) && WORLD_BEDS.contains(id.getPath());
    }

    /** The room of a sound; one emitted by a block set into a wall (a door, a fusuma, a wall fixture) belongs to the
     *  room it faces (the given one, if it borders it). */
    private static String roomNear(BlockPos p, String prefer) {
        Layout.Room r = Layout.roomAt(p);
        if (r != null) return r.key();
        String found = null;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Layout.Room n = Layout.roomAt(p.offset(d[0], 0, d[1]));
            if (n == null) continue;
            if (n.key().equals(prefer)) return n.key();
            if (found == null) found = n.key();
        }
        return found;
    }

    private static Vec3 listenerPos(Minecraft mc) {
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam != null && cam.isInitialized()) return cam.getPosition();
        return mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    // ------------------------------------------------------------------------------------------------------------
    // sound thread

    @SubscribeEvent
    public static void onSource(PlaySoundSourceEvent event) {
        attach(event.getEngine(), event.getSound(), event.getChannel());
    }

    @SubscribeEvent
    public static void onStream(PlayStreamingSourceEvent event) {
        attach(event.getEngine(), event.getSound(), event.getChannel());
    }

    private static void attach(SoundEngine engine, SoundInstance s, Channel channel) {
        if (broken) return;
        try {
            captureExecutor(engine);
            if (s == null || channel == null) return;
            Params p = PENDING.remove(s);
            if (p == null || !AcousticsEfx.ready()) return;
            int source = AcousticsEfx.sourceOf(channel);
            if (source == 0) return;
            float origMax = p.path() == PATH_BED ? 0f : AcousticsEfx.maxDistance(source);
            AcousticsEfx.applySource(source, p, origMax);
            FRESH.add(new Track(s, channel, source, p.path() == PATH_BED, origMax, p));
        } catch (Throwable t) {
            fail("source", t);
        }
    }

    private static void syncTask() {
        if (broken) return;
        try {
            AcousticsEfx.syncReverb();
        } catch (Throwable t) {
            fail("reverb", t);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // plumbing

    private static void captureExecutor(SoundEngine engine) {
        if (soundExecutor != null || engine == null) return;
        try {
            for (Field f : SoundEngine.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && SoundEngineExecutor.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object o = f.get(engine);
                    if (o instanceof Executor ex) {
                        soundExecutor = ex;
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            fail("executor", t);
        }
    }

    private static boolean otherAcousticsMod() {
        try {
            for (String id : OTHER_ACOUSTICS_MODS) {
                if (net.minecraftforge.fml.ModList.get().isLoaded(id)) {
                    Hitori.LOGGER.info("[hitori] acoustics: {} is installed, leaving room reverb and occlusion to it", id);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // no mod list: assume we are alone
        }
        return false;
    }

    private static SoundEngine engineOf(SoundManager manager) {
        try {
            for (Field f : SoundManager.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && SoundEngine.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (SoundEngine) f.get(manager);
                }
            }
        } catch (Throwable t) {
            Hitori.LOGGER.debug("[hitori] acoustics: no SoundEngine field yet", t);
        }
        return null;
    }

    private static void fail(String where, Throwable t) {
        if (broken) return;
        broken = true;
        restorePending = true;
        failure = where + ": " + t;
        Hitori.LOGGER.warn("[hitori] acoustics disabled after an error in {} (sound keeps working without room reverb, occlusion, closet and camera filters)", where, t);
        Executor ex = soundExecutor;
        if (ex != null) {
            try {
                AcousticsEfx.wanted = AcousticsPresets.get("off");
                ex.execute(() -> {
                    try {
                        if (AcousticsEfx.usable()) AcousticsEfx.syncReverb();
                    } catch (Throwable ignored) {
                        // already disabled
                    }
                });
            } catch (Throwable ignored) {
                // already disabled
            }
        }
    }

    private static float db(float v) {
        return (float) Math.pow(10.0, v / 20.0);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** One line for the F3 screen: the preset, the listening mode, EFX state, tracked sources, the last occlusion. */
    public static String status() {
        String mode = (AcousticsModes.cameraView() ? "camera" : "ears")
                + (AcousticsModes.enclosure() > 0f ? String.format(Locale.ROOT, ", closet %.1f", AcousticsModes.enclosure()) : "")
                + " [" + AcousticsModes.source() + "]";
        if (broken) return "disabled (" + failure + "), " + mode;
        if (!HitoriConfig.acoustics()) return "off (config sound.acoustics = false), " + mode;
        if (Boolean.TRUE.equals(otherMod)) return "off (another acoustics mod is installed), " + mode;
        if (!active) return "idle (outside hitori:shirakaba)";
        return String.format(Locale.ROOT, "%s%s, %s, %s, %d tracked, last occlusion %.1f", presetKey,
                fadeTick < FADE_TICKS ? " (fading)" : "", mode, AcousticsEfx.info, TRACKS.size(), lastOcclusion);
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        try {
            if (Minecraft.getInstance().options.renderDebug) event.getLeft().add("[HITORI acoustics] " + status());
        } catch (Throwable ignored) {
            // the debug screen is not worth an error
        }
    }
}
