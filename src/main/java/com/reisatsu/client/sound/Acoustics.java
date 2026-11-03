package com.reisatsu.client.sound;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.audio.Channel;
import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.world.Layout;
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
 * (music, beds, UI) and every dimension but {@code reisatsu:hidamari} are left alone. Client config
 * {@code sound.acoustics} switches it off. Fail-safe: no EFX = nothing happens; any exception disables the engine for
 * the session with one log line, sound keeps working. Press F3 to see its state ({@code -Dreisatsu.acoustics.debug=true}
 * also logs every room change).
 */
@Mod.EventBusSubscriber(modid = Reisatsu.MODID, value = Dist.CLIENT)
public final class Acoustics {
    private static final boolean DEBUG = Boolean.getBoolean("reisatsu.acoustics.debug");
    /** Ticks between re-evaluations of looping / moving sources (§10: every 4 ticks). */
    private static final int UPDATE_INTERVAL = 4;
    /** Cross-fade between room presets: 10 ticks = 0.5 s. */
    private static final int FADE_TICKS = 10;
    private static final int MAX_TRACKS = 128;
    /** Send level of a sound that started in another room than the listener's. */
    private static final float OTHER_ROOM_SEND = 0.5f;

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
        boolean want = ReisatsuConfig.acoustics() && level != null && Layout.DIMENSION.equals(level.dimension());
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
        if (DEBUG) Reisatsu.LOGGER.info("[reisatsu] acoustics: room preset {} -> {}", presetKey, key);
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
        Layout.Room r = Layout.roomAt(BlockPos.containing(x, y, z));
        String room = r == null ? null : r.key();
        float roomSend = Objects.equals(room, listenerRoom) ? 1f : OTHER_ROOM_SEND;
        float o = Math.min(occ, AcousticsOcclusion.CAP);
        return new Params(db(-1.2f * Math.min(o, 5f)), db(-3f * o), roomSend * db(-1.5f * o), db(-1.5f * o), occ, room);
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

    private static SoundEngine engineOf(SoundManager manager) {
        try {
            for (Field f : SoundManager.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && SoundEngine.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (SoundEngine) f.get(manager);
                }
            }
        } catch (Throwable t) {
            Reisatsu.LOGGER.debug("[reisatsu] acoustics: no SoundEngine field yet", t);
        }
        return null;
    }

    private static void fail(String where, Throwable t) {
        if (broken) return;
        broken = true;
        failure = where + ": " + t;
        Reisatsu.LOGGER.warn("[reisatsu] acoustics disabled after an error in {} (sound keeps working without room reverb and occlusion)", where, t);
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
        if (!ReisatsuConfig.acoustics()) return "off (config sound.acoustics = false)";
        if (!active) return "idle (outside the restaurant's dimension)";
        return String.format(Locale.ROOT, "%s%s, %s, %d tracked, last occlusion %.1f", presetKey,
                fadeTick < FADE_TICKS ? " (fading)" : "", AcousticsEfx.info, TRACKS.size(), lastOcclusion);
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        try {
            if (Minecraft.getInstance().options.renderDebug) event.getLeft().add("[REISATSU acoustics] " + status());
        } catch (Throwable ignored) {
            // the debug screen is not worth an error
        }
    }
}
