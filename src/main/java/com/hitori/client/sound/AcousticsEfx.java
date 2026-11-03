package com.hitori.client.sound;

import com.mojang.blaze3d.audio.Channel;
import com.hitori.Hitori;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The OpenAL EFX side of {@link Acoustics}. <b>Every method runs on the sound engine's thread</b> (inside Forge's
 * source events or tasks given to the {@code SoundEngineExecutor}), never on the client thread.
 * <p>
 * One auxiliary effect slot holds an EAX reverb (fallback: the standard reverb) whose parameters follow the listener's
 * room; two low-pass filter objects are reused for every source (EFX copies a filter's parameters into the source
 * when it is attached). The slot's "send auto" is off, so the wet path is attenuated exactly like the dry path
 * (Minecraft's per-source linear distance model). Objects belong to an AL context: when Minecraft re-creates its
 * context (device change, sound reload) everything is created again.
 */
final class AcousticsEfx {
    private static long context;
    private static volatile boolean usable;
    private static boolean eax;
    private static int sends;
    private static int slot, effect, direct, send;
    private static List<Field> intFields;
    private static Field sourceField;
    private static Field aliveField;
    private static int alErrors;

    /** Reverb parameters wanted by the client thread (applied by {@link #syncReverb()}). */
    static volatile float[] wanted = AcousticsPresets.get("off");
    static volatile String info = "not initialised";

    private AcousticsEfx() {}

    /** True when the EFX objects exist in the current context (creates them on first use and after a context change). */
    static boolean ready() {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == 0L) return false;
        if (ctx != context) init(ctx);
        return usable;
    }

    private static void init(long ctx) {
        context = ctx;
        usable = false;
        slot = effect = direct = send = 0;
        long device = ALC10.alcGetContextsDevice(ctx);
        if (device == 0L || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            info = "off (no ALC_EXT_EFX on this device)";
            Hitori.LOGGER.info("[hitori] acoustics: the sound device has no EFX, room reverb and occlusion stay off");
            return;
        }
        sends = ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
        if (sends < 1) {
            info = "off (no auxiliary sends)";
            Hitori.LOGGER.info("[hitori] acoustics: EFX without auxiliary sends, room reverb and occlusion stay off");
            return;
        }
        AL10.alGetError();
        slot = EXTEfx.alGenAuxiliaryEffectSlots();
        effect = EXTEfx.alGenEffects();
        direct = EXTEfx.alGenFilters();
        send = EXTEfx.alGenFilters();
        if (AL10.alGetError() != AL10.AL_NO_ERROR || slot == 0 || effect == 0 || direct == 0 || send == 0) {
            release();
            info = "off (could not create EFX objects)";
            Hitori.LOGGER.warn("[hitori] acoustics: could not create the EFX objects, staying off");
            return;
        }
        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        eax = AL10.alGetError() == AL10.AL_NO_ERROR;
        if (!eax) {
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                release();
                info = "off (no reverb effect)";
                Hitori.LOGGER.warn("[hitori] acoustics: neither EAX nor standard reverb is available, staying off");
                return;
            }
        }
        EXTEfx.alFilteri(direct, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilteri(send, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_FALSE);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            release();
            info = "off (EFX setup failed)";
            Hitori.LOGGER.warn("[hitori] acoustics: EFX setup failed, staying off");
            return;
        }
        usable = true;
        syncReverb();
        info = (eax ? "EAX reverb" : "standard reverb") + ", " + sends + " send" + (sends == 1 ? "" : "s");
        Hitori.LOGGER.info("[hitori] acoustics: OpenAL EFX ready ({}): room reverb and occlusion on", info);
    }

    private static void release() {
        try {
            if (slot != 0) EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            if (effect != 0) EXTEfx.alDeleteEffects(effect);
            if (direct != 0) EXTEfx.alDeleteFilters(direct);
            if (send != 0) EXTEfx.alDeleteFilters(send);
        } finally {
            slot = effect = direct = send = 0;
            usable = false;
            AL10.alGetError();
        }
    }

    /** Loads {@link #wanted} into the effect and re-attaches it to the slot (EFX slots copy effect parameters). */
    static void syncReverb() {
        if (!ready()) return;
        float[] p = wanted;
        if (eax) {
            f(EXTEfx.AL_EAXREVERB_DENSITY, p[AcousticsPresets.DENSITY], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_DIFFUSION, p[AcousticsPresets.DIFFUSION], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_GAIN, p[AcousticsPresets.GAIN], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_GAINHF, p[AcousticsPresets.GAIN_HF], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_GAINLF, p[AcousticsPresets.GAIN_LF], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_DECAY_TIME, p[AcousticsPresets.DECAY], 0.1f, 20f);
            f(EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, p[AcousticsPresets.DECAY_HF], 0.1f, 2f);
            f(EXTEfx.AL_EAXREVERB_DECAY_LFRATIO, p[AcousticsPresets.DECAY_LF], 0.1f, 2f);
            f(EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, p[AcousticsPresets.REFL_GAIN], 0f, 3.16f);
            f(EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, p[AcousticsPresets.REFL_DELAY], 0f, 0.3f);
            f(EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, p[AcousticsPresets.LATE_GAIN], 0f, 10f);
            f(EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, p[AcousticsPresets.LATE_DELAY], 0f, 0.1f);
            f(EXTEfx.AL_EAXREVERB_ECHO_TIME, p[AcousticsPresets.ECHO_TIME], 0.075f, 0.25f);
            f(EXTEfx.AL_EAXREVERB_ECHO_DEPTH, p[AcousticsPresets.ECHO_DEPTH], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_MODULATION_TIME, p[AcousticsPresets.MOD_TIME], 0.04f, 4f);
            f(EXTEfx.AL_EAXREVERB_MODULATION_DEPTH, p[AcousticsPresets.MOD_DEPTH], 0f, 1f);
            f(EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, p[AcousticsPresets.AIR_HF], 0.892f, 1f);
            f(EXTEfx.AL_EAXREVERB_HFREFERENCE, p[AcousticsPresets.HF_REF], 1000f, 20000f);
            f(EXTEfx.AL_EAXREVERB_LFREFERENCE, p[AcousticsPresets.LF_REF], 20f, 1000f);
            f(EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, 0f, 0f, 10f);
            EXTEfx.alEffecti(effect, EXTEfx.AL_EAXREVERB_DECAY_HFLIMIT, AL10.AL_TRUE);
        } else {
            f(EXTEfx.AL_REVERB_DENSITY, p[AcousticsPresets.DENSITY], 0f, 1f);
            f(EXTEfx.AL_REVERB_DIFFUSION, p[AcousticsPresets.DIFFUSION], 0f, 1f);
            f(EXTEfx.AL_REVERB_GAIN, p[AcousticsPresets.GAIN], 0f, 1f);
            f(EXTEfx.AL_REVERB_GAINHF, p[AcousticsPresets.GAIN_HF], 0f, 1f);
            f(EXTEfx.AL_REVERB_DECAY_TIME, p[AcousticsPresets.DECAY], 0.1f, 20f);
            f(EXTEfx.AL_REVERB_DECAY_HFRATIO, p[AcousticsPresets.DECAY_HF], 0.1f, 2f);
            f(EXTEfx.AL_REVERB_REFLECTIONS_GAIN, p[AcousticsPresets.REFL_GAIN], 0f, 3.16f);
            f(EXTEfx.AL_REVERB_REFLECTIONS_DELAY, p[AcousticsPresets.REFL_DELAY], 0f, 0.3f);
            f(EXTEfx.AL_REVERB_LATE_REVERB_GAIN, p[AcousticsPresets.LATE_GAIN], 0f, 10f);
            f(EXTEfx.AL_REVERB_LATE_REVERB_DELAY, p[AcousticsPresets.LATE_DELAY], 0f, 0.1f);
            f(EXTEfx.AL_REVERB_AIR_ABSORPTION_GAINHF, p[AcousticsPresets.AIR_HF], 0.892f, 1f);
            f(EXTEfx.AL_REVERB_ROOM_ROLLOFF_FACTOR, 0f, 0f, 10f);
            EXTEfx.alEffecti(effect, EXTEfx.AL_REVERB_DECAY_HFLIMIT, AL10.AL_TRUE);
        }
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, clamp(p[AcousticsPresets.SLOT_GAIN], 0f, 1f));
        checkErrors();
    }

    private static void f(int param, float v, float lo, float hi) {
        EXTEfx.alEffectf(effect, param, clamp(v, lo, hi));
    }

    /** The AL source of a Minecraft channel (the non-static int field that names a live AL source), or 0. */
    static int sourceOf(Channel channel) throws IllegalAccessException {
        if (intFields == null) scan();
        if (sourceField != null) {
            int id = sourceField.getInt(channel);
            if (id != 0 && AL10.alIsSource(id)) return id;
        }
        for (Field f : intFields) {
            int id = f.getInt(channel);
            if (id != 0 && AL10.alIsSource(id)) {
                sourceField = f;
                return id;
            }
        }
        return 0;
    }

    private static void scan() {
        List<Field> ints = new ArrayList<>();
        for (Field f : Channel.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == int.class) {
                f.setAccessible(true);
                ints.add(f);
            } else if (f.getType() == AtomicBoolean.class && aliveField == null) {
                f.setAccessible(true);
                aliveField = f;
            }
        }
        intFields = ints;
    }

    /** False once Minecraft destroyed the channel (its AL source is gone, the id may already name another sound). */
    static boolean alive(Channel channel, int source) {
        try {
            if (aliveField != null) {
                Object o = aliveField.get(channel);
                if (o instanceof AtomicBoolean b && !b.get()) return false;
            }
        } catch (IllegalAccessException e) {
            aliveField = null;
        }
        return AL10.alIsSource(source);
    }

    /** Direct path: low-pass (occlusion); auxiliary send 0: our room reverb through its own low-pass. */
    static void applySource(int source, Acoustics.Params p) {
        EXTEfx.alFilterf(direct, EXTEfx.AL_LOWPASS_GAIN, clamp(p.directGain(), 0f, 1f));
        EXTEfx.alFilterf(direct, EXTEfx.AL_LOWPASS_GAINHF, clamp(p.directHF(), 0f, 1f));
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, direct);
        EXTEfx.alFilterf(send, EXTEfx.AL_LOWPASS_GAIN, clamp(p.sendGain(), 0f, 1f));
        EXTEfx.alFilterf(send, EXTEfx.AL_LOWPASS_GAINHF, clamp(p.sendHF(), 0f, 1f));
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, send);
        checkErrors();
    }

    /** A moving / looping source's new occlusion, if its channel still lives. */
    static void update(Channel channel, int source, Acoustics.Params p) {
        if (!ready() || !alive(channel, source)) return;
        applySource(source, p);
    }

    /** Back to a plain source (acoustics switched off while it plays). */
    static void detach(Channel channel, int source) {
        if (!ready() || !alive(channel, source)) return;
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
        checkErrors();
    }

    /** Clears the AL error flag we may have raised, so Minecraft's own checks do not log it as theirs. */
    private static void checkErrors() {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR && ++alErrors > 64) {
            throw new IllegalStateException("too many OpenAL errors (last 0x" + Integer.toHexString(err) + ")");
        }
    }

    static boolean usable() {
        return usable;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
