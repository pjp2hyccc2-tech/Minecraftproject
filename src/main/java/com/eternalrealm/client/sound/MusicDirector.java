package com.eternalrealm.client.sound;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.sound.ERSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decides which ETERNAL REALM music plays on this client (client only).
 *
 * <p>The integrator reports where the player is with {@link #setContext(String)} and one-shot events with
 * {@link #playJingle(String)}; both may be called from any thread (they only queue the request, the work happens on
 * the next client tick). Context changes crossfade (2.5 s, equal power); switching to boss music is immediate
 * (0.3 s); jingles duck the music, the victory fanfare and the ending replace it. Field themes loop seamlessly.
 * Without a world (title screen) the title theme plays. While ETERNAL REALM music is wanted, vanilla music is
 * stopped and kept from starting, so the two never overlap; with the context {@code "none"} vanilla music returns.
 *
 * <p>Forge 47 (1.20.1) has no {@code SelectMusicEvent}, so playback is driven here through the sound manager with
 * fadeable {@link MusicVoice}s in {@link SoundSource#MUSIC} (the MUSIC volume slider applies) and vanilla music is
 * filtered with {@link PlaySoundEvent}.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MusicDirector {
    public static final String CONTEXT_NONE = "none";
    /** Every accepted context, in the order of the adventure. */
    public static final List<String> CONTEXTS = List.of(CONTEXT_NONE, "hub", "area_1", "area_2", "area_3", "area_4",
            "area_5", "area_6", "area_7", "dungeon", "boss", "final_boss");
    /** Every accepted jingle id. */
    public static final List<String> JINGLES = List.of("victory", "level_up", "quest_complete", "discovery", "ending");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TITLE_TRACK = "music.title";

    private static final int CROSSFADE_TICKS = 50;
    private static final int FADE_IN_FROM_SILENCE_TICKS = 30;
    private static final int FADE_OUT_TO_NONE_TICKS = 60;
    private static final int BOSS_OUT_TICKS = 6;
    private static final int BOSS_IN_TICKS = 3;
    private static final int JINGLE_TAKEOVER_TICKS = 10;
    private static final int JINGLE_INTERRUPT_TICKS = 5;
    private static final int RETRY_TICKS = 40;
    private static final int JINGLE_STALE_TICKS = 200;
    private static final int MAX_PENDING_JINGLES = 4;
    private static final int VANILLA_RESTOP_TICKS = 40;
    private static final float DUCK_LEVEL = 0.25F;
    private static final float DUCK_DOWN_PER_TICK = 0.15F;
    private static final float DUCK_UP_PER_TICK = 0.04F;

    // Written from any thread, read on the client thread (and by the sound event handler on the client thread).
    private static volatile String requestedContext = CONTEXT_NONE;
    private static volatile boolean titleMusicEnabled = true;
    private static volatile boolean blockVanilla;
    private static final Queue<String> REQUESTED_JINGLES = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean STOP_JINGLE = new AtomicBoolean();

    // Client thread only.
    private static MusicVoice current;
    private static final List<MusicVoice> FADING = new ArrayList<>();
    private static ActiveJingle jingle;
    private static final Deque<PendingJingle> PENDING = new ArrayDeque<>();
    private static float duck = 1.0F;
    private static long ticks;
    private static long nextStartAttempt;
    private static long nextRevive;
    private static boolean blocking;
    private static SoundInstance lastVanillaMusic;
    private static long restopVanillaUntil;
    private static final Set<String> WARNED = new HashSet<>();

    private MusicDirector() {
    }

    // ------------------------------------------------------------------------------------------------- API

    /**
     * Sets where the player is: {@code "none"} (vanilla music), {@code "hub"}, {@code "area_1"}..{@code "area_7"},
     * {@code "dungeon"}, {@code "boss"} or {@code "final_boss"}. Unknown values are ignored (logged once).
     * Setting the same context again changes nothing (the track keeps playing).
     */
    public static void setContext(String context) {
        String normalized = context == null ? CONTEXT_NONE : context.trim().toLowerCase(Locale.ROOT);
        if (!CONTEXTS.contains(normalized)) {
            LOGGER.warn("Ignoring unknown music context '{}' (known: {})", context, CONTEXTS);
            return;
        }
        requestedContext = normalized;
        if (!CONTEXT_NONE.equals(normalized)) {
            // Before the next vanilla music tick, so vanilla cannot start a song in between.
            blockVanilla = true;
        }
    }

    /** The last context set (the client starts with {@code "none"} and returns to it when leaving a server). */
    public static String context() {
        return requestedContext;
    }

    /**
     * Plays a jingle: {@code "level_up"}, {@code "quest_complete"} and {@code "discovery"} duck the music;
     * {@code "victory"} and {@code "ending"} (staff roll, about 2.5 minutes) replace it and the context music
     * resumes afterwards, so set the context first (e.g. back to the dungeon after a boss). A more important jingle
     * interrupts a running one (ending &gt; victory &gt; discovery = quest_complete &gt; level_up); others wait for
     * it (at most 10 s). Unknown ids are ignored (logged once).
     */
    public static void playJingle(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!JINGLES.contains(normalized)) {
            LOGGER.warn("Ignoring unknown jingle '{}' (known: {})", id, JINGLES);
            return;
        }
        REQUESTED_JINGLES.add(normalized);
        if (replacesMusic(normalized)) {
            blockVanilla = true;
        }
    }

    /** Fades out the running jingle (e.g. when the staff roll is skipped) and drops waiting ones. */
    public static void stopJingle() {
        STOP_JINGLE.set(true);
    }

    /** Whether the title theme plays on the title screen (default on). */
    public static void setTitleMusicEnabled(boolean enabled) {
        titleMusicEnabled = enabled;
    }

    /** Sound id of the track that is currently wanted and playing, or {@code null}. Client thread. */
    public static String currentTrack() {
        return current == null ? null : current.trackId();
    }

    // ------------------------------------------------------------------------------------------------- events

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            // The engine pauses every channel; fades continue when the game does.
            return;
        }
        tick(minecraft);
    }

    /** Keeps vanilla (and other mods') music silent while ETERNAL REALM music is wanted. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC
                || ModInfo.MOD_ID.equals(sound.getLocation().getNamespace())) {
            return;
        }
        if (blockVanilla) {
            event.setSound(null);
        } else {
            lastVanillaMusic = sound;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // A new server starts from "none" again; the title theme takes over on the title screen.
        requestedContext = CONTEXT_NONE;
        REQUESTED_JINGLES.clear();
        PENDING.clear();
        stopAll(Minecraft.getInstance().getSoundManager());
    }

    // ------------------------------------------------------------------------------------------------- tick

    private static void tick(Minecraft minecraft) {
        ticks++;
        SoundManager sounds = minecraft.getSoundManager();
        acceptJingleRequests();
        updateJingle(sounds);

        String desired = desiredTrack(minecraft);
        if (!Objects.equals(desired, current == null ? null : current.trackId()) && ticks >= nextStartAttempt) {
            switchTrack(sounds, desired);
        }
        keepAlive(minecraft, sounds);
        updateGains(sounds);
        guardVanilla(minecraft, sounds, desired);
    }

    private static String desiredTrack(Minecraft minecraft) {
        if (jingle != null && jingle.replacesMusic) {
            return null;
        }
        if (minecraft.level == null) {
            return titleMusicEnabled ? TITLE_TRACK : null;
        }
        return trackFor(requestedContext);
    }

    /** Sound id of the music of a context, or {@code null} for "none". */
    static String trackFor(String context) {
        return switch (context) {
            case "hub" -> TITLE_TRACK;
            case "area_1", "area_2", "area_3", "area_4", "area_5", "area_6", "area_7", "dungeon", "boss",
                    "final_boss" -> "music." + context;
            default -> null;
        };
    }

    private static boolean isBossTrack(String trackId) {
        return "music.boss".equals(trackId) || "music.final_boss".equals(trackId);
    }

    private static void switchTrack(SoundManager sounds, String desired) {
        boolean wasSilent = current == null && FADING.isEmpty();
        boolean urgent = isBossTrack(desired);
        if (current != null) {
            int out;
            if (urgent) {
                out = BOSS_OUT_TICKS;
            } else if (desired != null) {
                out = CROSSFADE_TICKS;
            } else if (jingle != null && jingle.replacesMusic) {
                out = JINGLE_TAKEOVER_TICKS;
            } else {
                out = FADE_OUT_TO_NONE_TICKS;
            }
            current.fadeTo(0.0F, out);
            FADING.add(current);
            current = null;
        }
        if (desired == null) {
            return;
        }
        // Coming back to a track that is still fading out (e.g. stepping out of an area and back in): keep it.
        for (Iterator<MusicVoice> it = FADING.iterator(); it.hasNext(); ) {
            MusicVoice voice = it.next();
            if (voice.trackId().equals(desired) && sounds.isActive(voice)) {
                it.remove();
                voice.fadeTo(1.0F, urgent ? BOSS_IN_TICKS : CROSSFADE_TICKS / 2);
                current = voice;
                return;
            }
        }
        current = start(sounds, desired, true, 0.0F);
        if (current == null) {
            nextStartAttempt = ticks + RETRY_TICKS;
            return;
        }
        current.fadeTo(1.0F, urgent ? BOSS_IN_TICKS : wasSilent ? FADE_IN_FROM_SILENCE_TICKS : CROSSFADE_TICKS);
    }

    /** Restarts the wanted track when the engine dropped it (resource reload, device change, slider back up). */
    private static void keepAlive(Minecraft minecraft, SoundManager sounds) {
        if (current == null || ticks < nextRevive || current.age() < 3 || sounds.isActive(current)) {
            return;
        }
        nextRevive = ticks + RETRY_TICKS;
        if (minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0.0F
                || minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) {
            // The engine stops music while the slider is at 0; start again once it is raised.
            return;
        }
        MusicVoice replacement = start(sounds, current.trackId(), true, 0.0F);
        current = replacement;
        if (replacement != null) {
            replacement.fadeTo(1.0F, FADE_IN_FROM_SILENCE_TICKS);
        }
    }

    private static void updateGains(SoundManager sounds) {
        float duckTarget = jingle != null && !jingle.replacesMusic && jingle.voice.target() > 0.0F ? DUCK_LEVEL : 1.0F;
        duck = duckTarget < duck ? Math.max(duckTarget, duck - DUCK_DOWN_PER_TICK)
                : Math.min(duckTarget, duck + DUCK_UP_PER_TICK);
        if (current != null) {
            current.advance(duck);
        }
        for (Iterator<MusicVoice> it = FADING.iterator(); it.hasNext(); ) {
            MusicVoice voice = it.next();
            voice.advance(duck);
            if (voice.faded() || voice.age() > 3 && !sounds.isActive(voice)) {
                halt(sounds, voice);
                it.remove();
            }
        }
        if (jingle != null) {
            jingle.voice.advance(1.0F);
        }
    }

    private static void guardVanilla(Minecraft minecraft, SoundManager sounds, String desired) {
        boolean wanted = desired != null || current != null || !FADING.isEmpty()
                || jingle != null && jingle.replacesMusic
                || PENDING.stream().anyMatch(pending -> replacesMusic(pending.id()));
        if (wanted && !blocking) {
            minecraft.getMusicManager().stopPlaying();
            restopVanillaUntil = ticks + VANILLA_RESTOP_TICKS;
        }
        blocking = wanted;
        blockVanilla = wanted || !CONTEXT_NONE.equals(requestedContext) && minecraft.level != null;
        if (lastVanillaMusic != null && blocking) {
            // A vanilla song that was starting when we took over may attach its stream after the first stop.
            if (ticks % 10 == 0 || ticks >= restopVanillaUntil) {
                sounds.stop(lastVanillaMusic);
            }
            if (ticks >= restopVanillaUntil) {
                lastVanillaMusic = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- jingles

    private record PendingJingle(String id, long requestedAt) {
    }

    private static final class ActiveJingle {
        final String id;
        final MusicVoice voice;
        final boolean replacesMusic;
        final int priority;

        ActiveJingle(String id, MusicVoice voice) {
            this.id = id;
            this.voice = voice;
            this.replacesMusic = replacesMusic(id);
            this.priority = priority(id);
        }
    }

    private static boolean replacesMusic(String id) {
        return "victory".equals(id) || "ending".equals(id);
    }

    private static int priority(String id) {
        return switch (id) {
            case "ending" -> 4;
            case "victory" -> 3;
            case "discovery", "quest_complete" -> 2;
            default -> 1;
        };
    }

    private static String jingleTrack(String id) {
        return "ending".equals(id) ? "music.ending" : "jingle." + id;
    }

    private static void acceptJingleRequests() {
        if (STOP_JINGLE.getAndSet(false)) {
            PENDING.clear();
            REQUESTED_JINGLES.clear();
            if (jingle != null) {
                jingle.voice.fadeTo(0.0F, CROSSFADE_TICKS / 2);
            }
        }
        String id;
        while ((id = REQUESTED_JINGLES.poll()) != null) {
            if ("ending".equals(id)) {
                PENDING.clear();
            }
            PENDING.addLast(new PendingJingle(id, ticks));
            while (PENDING.size() > MAX_PENDING_JINGLES) {
                PENDING.removeFirst();
            }
        }
    }

    private static void updateJingle(SoundManager sounds) {
        if (jingle != null && (jingle.voice.faded() || jingle.voice.age() > 3 && !sounds.isActive(jingle.voice))) {
            halt(sounds, jingle.voice);
            jingle = null;
        }
        PENDING.removeIf(pending -> ticks - pending.requestedAt() > JINGLE_STALE_TICKS);
        PendingJingle next = PENDING.peekFirst();
        if (next == null) {
            return;
        }
        if (jingle != null) {
            if (priority(next.id()) > jingle.priority && jingle.voice.target() > 0.0F) {
                jingle.voice.fadeTo(0.0F, JINGLE_INTERRUPT_TICKS);
            }
            return;
        }
        PENDING.removeFirst();
        MusicVoice voice = start(sounds, jingleTrack(next.id()), false, 1.0F);
        if (voice != null) {
            jingle = new ActiveJingle(next.id(), voice);
        }
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private static MusicVoice start(SoundManager sounds, String soundId, boolean loop, float gain) {
        Optional<RegistryObject<SoundEvent>> object = ERSounds.byId(soundId);
        if (object.isEmpty() || !object.get().isPresent()) {
            if (WARNED.add(soundId)) {
                LOGGER.warn("Music '{}' is not registered (is ERSounds.register called?)", soundId);
            }
            return null;
        }
        MusicVoice voice = new MusicVoice(object.get().get(), soundId, loop, gain);
        sounds.play(voice);
        return voice;
    }

    private static void halt(SoundManager sounds, MusicVoice voice) {
        voice.halt();
        sounds.stop(voice);
    }

    private static void stopAll(SoundManager sounds) {
        if (current != null) {
            halt(sounds, current);
            current = null;
        }
        for (MusicVoice voice : FADING) {
            halt(sounds, voice);
        }
        FADING.clear();
        if (jingle != null) {
            halt(sounds, jingle.voice);
            jingle = null;
        }
        duck = 1.0F;
        nextStartAttempt = 0;
        nextRevive = 0;
    }
}
