#!/usr/bin/env python3
"""Runtime check of the acoustics engine's OpenAL EFX usage (com.reisatsu.client.sound.Acoustics*), without
Minecraft: drives the system's OpenAL Soft through a loopback device (ALC_SOFT_loopback, no sound card) with the very
call sequence of AcousticsEfx.java - one auxiliary slot with an EAX reverb, send-auto off, two low-pass filter
objects reused for every source, sources set up like Minecraft's Channel (per-source linear distance model) - and
measures what comes out:

  * every preset of AcousticsPresets.java loads without an AL error, and its reverb tail decays at about the
    preset's decay time (RT60 fitted on the rendered tail);
  * the occlusion filter really removes high frequencies (-3 dB HF per block, capped at -24 dB);
  * with send-auto off the wet (reverb) path fades with distance exactly like the dry path, so a sound beyond
    its attenuation distance is silent in the reverb too.

    python3 tools/audio/efx_selftest.py
"""
import ctypes as C
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SR = 44100

AL_NO_ERROR = 0
AL_SOURCE_DISTANCE_MODEL = 0x200
AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE = 0xD000, 0xD003
AL_POSITION, AL_SOURCE_RELATIVE, AL_BUFFER, AL_GAIN = 0x1004, 0x202, 0x1009, 0x100A
AL_MAX_DISTANCE, AL_ROLLOFF_FACTOR, AL_REFERENCE_DISTANCE = 0x1023, 0x1021, 0x1020
AL_FORMAT_MONO_FLOAT32 = 0x10010
ALC_FORMAT_CHANNELS_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_STEREO_SOFT, ALC_FLOAT_SOFT, ALC_FREQUENCY = 0x1990, 0x1991, 0x1501, 0x1406, 0x1007
ALC_MAX_AUXILIARY_SENDS = 0x20003
AL_DIRECT_FILTER, AL_AUXILIARY_SEND_FILTER = 0x20005, 0x20006
AL_EFFECT_TYPE, AL_EFFECT_REVERB, AL_EFFECT_EAXREVERB = 0x8001, 0x0001, 0x8000
AL_EFFECTSLOT_EFFECT, AL_EFFECTSLOT_GAIN, AL_EFFECTSLOT_AUXILIARY_SEND_AUTO = 0x1, 0x2, 0x3
AL_FILTER_TYPE, AL_FILTER_LOWPASS, AL_LOWPASS_GAIN, AL_LOWPASS_GAINHF = 0x8001, 0x1, 0x1, 0x2
EAX = dict(DENSITY=0x1, DIFFUSION=0x2, GAIN=0x3, GAINHF=0x4, GAINLF=0x5, DECAY_TIME=0x6, DECAY_HFRATIO=0x7,
           DECAY_LFRATIO=0x8, REFLECTIONS_GAIN=0x9, REFLECTIONS_DELAY=0xA, LATE_REVERB_GAIN=0xC, LATE_REVERB_DELAY=0xD,
           ECHO_TIME=0xF, ECHO_DEPTH=0x10, MODULATION_TIME=0x11, MODULATION_DEPTH=0x12, AIR_ABSORPTION_GAINHF=0x13,
           HFREFERENCE=0x14, LFREFERENCE=0x15, ROOM_ROLLOFF_FACTOR=0x16, DECAY_HFLIMIT=0x17)
# AcousticsPresets index -> (EAX parameter, min, max), exactly as AcousticsEfx.syncReverb clamps them
ORDER = [("DENSITY", 0, 1), ("DIFFUSION", 0, 1), ("GAIN", 0, 1), ("GAINHF", 0, 1), ("GAINLF", 0, 1),
         ("DECAY_TIME", 0.1, 20), ("DECAY_HFRATIO", 0.1, 2), ("DECAY_LFRATIO", 0.1, 2), ("REFLECTIONS_GAIN", 0, 3.16),
         ("REFLECTIONS_DELAY", 0, 0.3), ("LATE_REVERB_GAIN", 0, 10), ("LATE_REVERB_DELAY", 0, 0.1),
         ("ECHO_TIME", 0.075, 0.25), ("ECHO_DEPTH", 0, 1), ("MODULATION_TIME", 0.04, 4), ("MODULATION_DEPTH", 0, 1),
         ("AIR_ABSORPTION_GAINHF", 0.892, 1), ("HFREFERENCE", 1000, 20000), ("LFREFERENCE", 20, 1000)]


def presets():
    src = open(os.path.join(ROOT, "src/main/java/com/reisatsu/client/sound/AcousticsPresets.java"), encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'def\("([a-z_]+)",\s*([^)]*)\);', src):
        out[m.group(1)] = [float(v.strip().rstrip("f")) for v in m.group(2).split(",")]
    return out


class AL:
    def __init__(self):
        self.lib = C.CDLL("libopenal.so.1")
        L = self.lib
        L.alcGetProcAddress.restype = C.c_void_p
        L.alcGetProcAddress.argtypes = [C.c_void_p, C.c_char_p]
        L.alGetProcAddress.restype = C.c_void_p
        L.alGetProcAddress.argtypes = [C.c_char_p]
        L.alcCreateContext.restype = C.c_void_p
        L.alcCreateContext.argtypes = [C.c_void_p, C.POINTER(C.c_int)]
        L.alcMakeContextCurrent.argtypes = [C.c_void_p]
        L.alcIsExtensionPresent.argtypes = [C.c_void_p, C.c_char_p]
        L.alcGetIntegerv.argtypes = [C.c_void_p, C.c_int, C.c_int, C.POINTER(C.c_int)]
        L.alcGetCurrentContext.restype = C.c_void_p
        L.alcGetContextsDevice.restype = C.c_void_p
        L.alcGetContextsDevice.argtypes = [C.c_void_p]
        L.alcDestroyContext.argtypes = [C.c_void_p]
        L.alcCloseDevice.argtypes = [C.c_void_p]
        open_ = C.CFUNCTYPE(C.c_void_p, C.c_char_p)(L.alcGetProcAddress(None, b"alcLoopbackOpenDeviceSOFT"))
        self.render = C.CFUNCTYPE(None, C.c_void_p, C.c_void_p, C.c_int)(L.alcGetProcAddress(None, b"alcRenderSamplesSOFT"))
        self.dev = open_(None)
        if not self.dev:
            raise RuntimeError("no loopback device")
        attrs = (C.c_int * 7)(ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT, ALC_FORMAT_TYPE_SOFT, ALC_FLOAT_SOFT,
                              ALC_FREQUENCY, SR, 0)
        self.ctx = L.alcCreateContext(self.dev, attrs)  # like Minecraft: no ALC_MAX_AUXILIARY_SENDS attribute
        L.alcMakeContextCurrent(self.ctx)

        def fn(name, *args):
            return C.CFUNCTYPE(None, *args)(L.alGetProcAddress(name.encode()))

        u = C.c_uint
        self.genEffects = fn("alGenEffects", C.c_int, C.POINTER(u))
        self.genFilters = fn("alGenFilters", C.c_int, C.POINTER(u))
        self.genSlots = fn("alGenAuxiliaryEffectSlots", C.c_int, C.POINTER(u))
        self.effecti = fn("alEffecti", u, C.c_int, C.c_int)
        self.effectf = fn("alEffectf", u, C.c_int, C.c_float)
        self.filteri = fn("alFilteri", u, C.c_int, C.c_int)
        self.filterf = fn("alFilterf", u, C.c_int, C.c_float)
        self.sloti = fn("alAuxiliaryEffectSloti", u, C.c_int, C.c_int)
        self.slotf = fn("alAuxiliaryEffectSlotf", u, C.c_int, C.c_float)
        L.alGetError.restype = C.c_int
        L.alSourcef.argtypes = [u, C.c_int, C.c_float]
        L.alSourcei.argtypes = [u, C.c_int, C.c_int]
        L.alSource3f.argtypes = [u, C.c_int, C.c_float, C.c_float, C.c_float]
        L.alSource3i.argtypes = [u, C.c_int, C.c_int, C.c_int, C.c_int]
        L.alBufferData.argtypes = [u, C.c_int, C.c_void_p, C.c_int, C.c_int]
        L.alIsSource.argtypes = [u]
        L.alIsSource.restype = C.c_char

    def gen(self, f):
        v = C.c_uint(0)
        f(1, C.byref(v))
        return v.value

    def err(self):
        return self.lib.alGetError()

    def run(self, seconds):
        n = int(seconds * SR)
        buf = (C.c_float * (2 * n))()
        self.render(self.dev, buf, n)
        return np.frombuffer(buf, dtype=np.float32).reshape(-1, 2).T.copy()

    def close(self):
        self.lib.alcMakeContextCurrent(None)
        self.lib.alcDestroyContext(self.ctx)
        self.lib.alcCloseDevice(self.dev)


def rt60(x, t0):
    """RT60 from the Schroeder integral of the rendered tail (fit -5 .. -25 dB, extrapolated)."""
    e = np.sum(x[:, int(t0 * SR):] ** 2, axis=0)
    sch = np.cumsum(e[::-1])[::-1]
    sch_db = 10 * np.log10(sch / (sch[0] + 1e-30) + 1e-30)
    t = np.arange(len(sch_db)) / SR
    k = (sch_db < -5) & (sch_db > -25)
    if k.sum() < 10:
        return float("nan")
    a, b = np.polyfit(t[k], sch_db[k], 1)
    return -60.0 / a


def main():
    al = AL()
    L = al.lib
    bad = []
    print("device: loopback, ALC_EXT_EFX:", bool(L.alcIsExtensionPresent(al.dev, b"ALC_EXT_EFX")))
    sends = C.c_int(0)
    L.alcGetIntegerv(al.dev, ALC_MAX_AUXILIARY_SENDS, 1, C.byref(sends))
    print("max auxiliary sends without asking for more (like Minecraft):", sends.value)
    if sends.value < 1:
        bad.append("no auxiliary sends")
    L.alGetError()
    slot, effect = al.gen(al.genSlots), al.gen(al.genEffects)
    direct, send = al.gen(al.genFilters), al.gen(al.genFilters)
    al.effecti(effect, AL_EFFECT_TYPE, AL_EFFECT_EAXREVERB)
    e = al.err()
    print("EAX reverb:", "ok" if e == AL_NO_ERROR else f"error 0x{e:x}")
    al.filteri(direct, AL_FILTER_TYPE, AL_FILTER_LOWPASS)
    al.filteri(send, AL_FILTER_TYPE, AL_FILTER_LOWPASS)
    al.sloti(slot, AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, 0)
    if al.err() != AL_NO_ERROR:
        bad.append("EFX object setup")
    L.alEnable(AL_SOURCE_DISTANCE_MODEL)

    def load(p):
        for k, (name, lo, hi) in enumerate(ORDER):
            al.effectf(effect, EAX[name], float(min(max(p[k], lo), hi)))
        al.effectf(effect, EAX["ROOM_ROLLOFF_FACTOR"], 0.0)
        al.effecti(effect, EAX["DECAY_HFLIMIT"], 1)
        al.sloti(slot, AL_EFFECTSLOT_EFFECT, effect)
        al.slotf(slot, AL_EFFECTSLOT_GAIN, float(min(max(p[19], 0), 1)))
        return al.err()

    def source(sig, pos=(0.0, 0.0, -2.0), max_dist=16.0, gain_hf=1.0, gain=1.0, send_gain=1.0, send_hf=1.0, efx=True):
        buf = al.gen(lambda c, p: L.alGenBuffers(c, p))
        data = np.ascontiguousarray(sig.astype(np.float32))
        L.alBufferData(buf, AL_FORMAT_MONO_FLOAT32, data.ctypes.data, data.nbytes, SR)
        src = al.gen(lambda c, p: L.alGenSources(c, p))
        L.alSourcei(src, AL_BUFFER, buf)
        L.alSourcei(src, AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE)  # Minecraft's Channel.linearAttenuation
        L.alSourcef(src, AL_MAX_DISTANCE, max_dist)
        L.alSourcef(src, AL_ROLLOFF_FACTOR, 1.0)
        L.alSourcef(src, AL_REFERENCE_DISTANCE, 0.0)
        L.alSource3f(src, AL_POSITION, *pos)
        L.alSourcei(src, AL_SOURCE_RELATIVE, 0)
        if efx:  # AcousticsEfx.applySource
            al.filterf(direct, AL_LOWPASS_GAIN, gain)
            al.filterf(direct, AL_LOWPASS_GAINHF, gain_hf)
            L.alSourcei(src, AL_DIRECT_FILTER, direct)
            al.filterf(send, AL_LOWPASS_GAIN, send_gain)
            al.filterf(send, AL_LOWPASS_GAINHF, send_hf)
            L.alSource3i(src, AL_AUXILIARY_SEND_FILTER, slot, 0, send)
        e2 = al.err()
        L.alSourcePlay(src)
        return src, e2

    imp = np.zeros(SR // 10)
    imp[0] = 1.0
    print("\npreset        AL   RT60 measured / preset decay   wet/dry energy")
    P = presets()
    for key in ("dining", "kitchen", "restroom", "small_hard", "small_soft", "corridor", "tiny_dead", "medium", "outside", "off"):
        e1 = load(P[key])
        al.run(0.1)  # OpenAL Soft applies new reverb settings to what enters the reverb after its next update;
        # sounds already ringing keep decaying with the settings they entered under (in game: tails started in the
        # previous room finish with the previous room's reverb)
        src, e2 = source(imp)
        y = al.run(4.0)
        L.alSourceStop(src)
        dry_e = float(np.sum(y[:, :64] ** 2))
        wet_e = float(np.sum(y[:, 64:] ** 2))
        r = rt60(y, 0.004) if key not in ("off",) else float("nan")
        ok = e1 == AL_NO_ERROR and e2 == AL_NO_ERROR
        if key == "off":
            ok = ok and wet_e < 1e-6 * dry_e
        elif key not in ("outside", "tiny_dead"):
            ok = ok and 0.5 * P[key][5] < r < 1.6 * P[key][5]
        print(f"{key:12s} {'ok' if e1 == e2 == 0 else 'ERR'}   {r:5.2f} s / {P[key][5]:4.2f} s            {10 * np.log10(wet_e / (dry_e + 1e-30) + 1e-30):+6.1f} dB   {'ok' if ok else 'CHECK'}")
        if not ok:
            bad.append(f"preset {key}")
    # occlusion: white noise, direct path only, -3 dB HF per block
    load(P["off"])
    al.run(0.1)
    rng = np.random.default_rng(1)
    noise = rng.standard_normal(SR) * 0.2
    print("\nblocks  GAINHF    level 6-12 kHz vs unoccluded   level < 500 Hz")
    ref_hi = ref_lo = None
    for blocks in (0, 1, 2, 4, 8):
        ghf = 10 ** (-3.0 * blocks / 20)
        src, _ = source(noise, gain_hf=ghf)
        y = al.run(1.0)[0]
        L.alSourceStop(src)
        S = np.abs(np.fft.rfft(y)) ** 2
        f = np.fft.rfftfreq(len(y), 1 / SR)
        hi = 10 * np.log10(S[(f > 6000) & (f < 12000)].mean())
        lo = 10 * np.log10(S[(f > 50) & (f < 500)].mean())
        if blocks == 0:
            ref_hi, ref_lo = hi, lo
        d_hi, d_lo = hi - ref_hi, lo - ref_lo
        print(f"{blocks:4d}    {ghf:5.3f}        {d_hi:+6.1f} dB                  {d_lo:+5.1f} dB")
        if blocks == 8 and not (d_hi < -15 and d_lo > -3):
            bad.append("occlusion filter")
    # distance: with send-auto off the reverb fades exactly like the dry sound
    load(P["restroom"])
    al.run(0.1)
    print("\ndistance / max    dry level   wet level (send-auto off: both follow the linear model)")
    base = None
    for d in (2.0, 8.0, 14.0, 17.0):
        src, _ = source(imp, pos=(0.0, 0.0, -d), max_dist=16.0)
        y = al.run(3.0)
        L.alSourceStop(src)
        dry = float(np.sum(y[:, :64] ** 2)) ** 0.5
        wet = float(np.sum(y[:, 400:] ** 2)) ** 0.5
        if base is None:
            base = (dry, wet)
        print(f"{d:5.1f} / 16        {20 * np.log10(dry / base[0] + 1e-12):+6.1f} dB   {20 * np.log10(wet / base[1] + 1e-12):+6.1f} dB")
        if d > 16 and wet > 1e-4 * base[1]:
            bad.append("the reverb of a sound out of range is still audible")
    al.close()
    print("\nRESULT:", "PASS" if not bad else "FAIL " + ", ".join(bad))
    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
