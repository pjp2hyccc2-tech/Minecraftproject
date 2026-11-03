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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * 音MOD: the restaurant's acoustics (docs/DESIGN.md §10), a small built-in version of what sound-physics mods do.
 * <ul>
 * <li><b>Room reverb</b>: one OpenAL EFX auxiliary slot with an EAX reverb whose preset follows the listener's room
 * ({@link Layout#roomAt}: dining, kitchen, restroom, small_hard, small_soft, corridor, tiny_dead, medium; outside =
 * only a faint facade slap), cross-faded over 0.5 s. Sounds that start in another room than the listener's get half
 * the send.</li>
 * <li><b>Occlusion</b>: every new positional sound gets a low-pass on its direct path from a block ray march between
 * the listener and the sound (opaque blocks -3 dB HF each, closed doors half, cap -24 dB); looping and moving sounds are
 * re-evaluated every 4 ticks.</li>
 * </ul>
 * Threads: the level is read only on the client thread (when a sound is played: {@link PlaySoundEvent}, and in the
 * client tick); OpenAL is touched only on the sound engine's thread (Forge's source events, which carry the
 * {@link Channel}, and tasks handed to the engine's {@link SoundEngineExecutor}). Relative / non-attenuated sounds
 * (music, beds, UI) and every dimension but {@code hitori:hidamari} are left alone. Client config
 * {@code sound.acoustics} switches it off. Fail-safe: no EFX = nothing happens; any exception disables the engine for
 * the session with one log line, sound keeps working. Press F3 to see its state ({@code -Dhitori.acoustics.debug=true}
 * also logs every room change).
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
    /** Occlusion per block unit (an opaque block = 1, a closed door = 0.5): high-frequency loss (§10: 3 dB) ... */
    static final float HF_DB_PER_BLOCK = 3.0f;
    /** ... a small broadband loss on top (capped at 6 dB) ... */
    static final float DB_PER_BLOCK = 1.2f;
    /** ... and the reverb send, which reaches us around the obstacle, loses half as much. */
    static final float SEND_HF_DB_PER_BLOCK = 1.5f;
    /** Mods that do the same job: if one is loaded this engine stays out of its way. */
    private static final String[] OTHER_ACOUSTICS_MODS = {"sound_physics_remastered", "soundphysics"};

    /** Occlusion and send of one source. */
    public record Params(float directGain, float directHF, float sendGain, float sendHF, float occlusion, String room) {
        boolean differs(Params o) {
            return o == null || Math.abs(directHF - o.directHF) > 0.02f || Math.abs(directGain - o.directGain) > 0.02f
                    || Math.abs(sendGain - o.sendGain) > 0.03f || Math.abs(sendHF - o.sendHF) > 0.03f;
        }
    }

    /** A playing positional source (created on the sound thread, then owned by the client thread). */
    private static final class Track {
        final SoundInstance instance;
        final Channel channel;
        final int source;
        Params params;

        Track(SoundInstance instance, Channel channel, int source, Params params) {
            this.instance = instance;
            this.channel = channel;
            this.source = source;
            this.params = params;
        }
    }

    private static volatile boolean broken;
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
    private static int tick;
    private static boolean triedEngine;
    private static Boolean otherMod;

    private Acoustics() {}

    // ------------------------------------------------------------------------------------------------------------
    // client thread

    /** A sound is about to play: compute its occlusion and send while we may still read the level. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        if (broken || !active) return;
        try {
            SoundInstance s = event.getSound();
            if (s == null || s.isRelative() || s.getAttenuation() != SoundInstance.Attenuation.LINEAR) return;
            captureExecutor(event.getEngine());
            Params p = compute(s.getX(), s.getY(), s.getZ(), null);
            if (p != null) PENDING.put(s, p);
        } catch (Throwable t) {
            fail("play", t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || broken) return;
        try {
            tickClient();
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    private static void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        tick++;
        if (soundExecutor == null && !triedEngine) {
            triedEngine = true;
            captureExecutor(engineOf(mc.getSoundManager()));
        }
        ClientLevel level = mc.level;
        if (otherMod == null) otherMod = otherAcousticsMod();
        boolean want = HitoriConfig.acoustics() && !otherMod && level != null && Layout.DIMENSION.equals(level.dimension());
        if (!want) {
            if (active) {
                active = false;
                listenerRoom = null;
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
        String key = room == null ? "outside" : room.acoustics();
        if (!AcousticsPresets.known(key)) key = "medium";
        if (!key.equals(presetKey)) setPreset(key, "off".equals(presetKey));
        advanceFade();
        flushPreset();
        if (tick % UPDATE_INTERVAL == 0) updateTracks(mc, ear);
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

    private static void updateTracks(Minecraft mc, Vec3 ear) {
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
            Params np = compute(t.instance.getX(), t.instance.getY(), t.instance.getZ(), t.params);
            if (np == null || !np.differs(t.params)) continue;
            t.params = np;
            Channel ch = t.channel;
            int src = t.source;
            ex.execute(() -> {
                try {
                    AcousticsEfx.update(ch, src, np);
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
                ex.execute(() -> {
                    try {
                        AcousticsEfx.detach(ch, src);
                    } catch (Throwable e) {
                        fail("detach", e);
                    }
                });
            }
        }
        TRACKS.clear();
        PENDING.clear();
    }

    /** Occlusion and send for a sound at (x, y, z), smoothed toward the previous value for moving sources. */
    private static Params compute(double x, double y, double z, Params prev) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return null;
        Vec3 ear = listenerPos(mc);
        float occ = AcousticsOcclusion.march(level, ear, new Vec3(x, y, z));
        if (prev != null) occ = prev.occlusion() + (occ - prev.occlusion()) * 0.6f;
        lastOcclusion = occ;
        String room = roomNear(BlockPos.containing(x, y, z));
        float roomSend = Objects.equals(room, listenerRoom) ? 1f : OTHER_ROOM_SEND;
        float o = Math.min(occ, AcousticsOcclusion.CAP);
        return new Params(db(-DB_PER_BLOCK * Math.min(o, 5f)), db(-HF_DB_PER_BLOCK * o), roomSend * db(-SEND_HF_DB_PER_BLOCK * o),
                db(-SEND_HF_DB_PER_BLOCK * o), occ, room);
    }

    /** The room of a sound; one emitted by a block set into a wall (a door, a wall fixture) belongs to the room it faces
     *  (the listener's, if it borders it). */
    private static String roomNear(BlockPos p) {
        Layout.Room r = Layout.roomAt(p);
        if (r != null) return r.key();
        String found = null;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Layout.Room n = Layout.roomAt(p.offset(d[0], 0, d[1]));
            if (n == null) continue;
            if (n.key().equals(listenerRoom)) return n.key();
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
            AcousticsEfx.applySource(source, p);
            FRESH.add(new Track(s, channel, source, p));
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
        failure = where + ": " + t;
        Hitori.LOGGER.warn("[hitori] acoustics disabled after an error in {} (sound keeps working without room reverb and occlusion)", where, t);
        Executor ex = soundExecutor;
        if (ex != null) {
            try {
                float[] off = AcousticsPresets.get("off");
                AcousticsEfx.wanted = off;
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

    /** One line for the F3 screen: the preset, EFX state, tracked sources, the last occlusion. */
    public static String status() {
        if (broken) return "disabled (" + failure + ")";
        if (!HitoriConfig.acoustics()) return "off (config sound.acoustics = false)";
        if (Boolean.TRUE.equals(otherMod)) return "off (another acoustics mod is installed)";
        if (!active) return "idle (outside the restaurant's dimension)";
        return String.format(Locale.ROOT, "%s%s, %s, %d tracked, last occlusion %.1f", presetKey,
                fadeTick < FADE_TICKS ? " (fading)" : "", AcousticsEfx.info, TRACKS.size(), lastOcclusion);
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
