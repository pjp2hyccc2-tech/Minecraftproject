"""Sound effects (mono). render(stem) returns (audio, target true-peak dBFS).

Building blocks: band-swept noise (STFT filtering) for whooshes and fire, modal synthesis for metal, wood, glass,
coins and bells, formant-filtered pulse trains for the boss roar, and the music instruments for chimes and horns.
"""
import numpy as np

from . import drums, dsp
from . import instruments as I
from .dsp import SR, TAU


# ------------------------------------------------------------------------------------------------- primitives

def seconds(n):
    return np.arange(n) / SR


def env_ar(n, attack, release_shape=3.0, peak_at=None):
    """0 -> 1 -> 0 envelope; peak at `peak_at` (fraction) with a power-curve rise and fall."""
    u = np.linspace(0, 1, n)
    p = peak_at if peak_at is not None else min(0.95, attack)
    rise = np.clip(u / max(p, 1e-4), 0, 1) ** 1.5
    fall = np.clip((1 - u) / max(1 - p, 1e-4), 0, 1) ** release_shape
    return np.where(u < p, rise, fall)


def sweep_noise(dur, f0, f1, rng, bw_oct=1.0, curve=1.0):
    """Noise through a band-pass whose centre glides from f0 to f1 (log), via STFT filtering."""
    n = int(dur * SR)
    frame, hop = 1024, 256
    x = rng.normal(size=n + frame)
    win = np.hanning(frame)
    nfr = 1 + (x.size - frame) // hop
    idx = np.arange(frame)[None, :] + hop * np.arange(nfr)[:, None]
    spec = np.fft.rfft(x[idx] * win, axis=1)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    t = (np.arange(nfr) * hop + frame / 2) / SR
    u = np.clip(t / dur, 0, 1) ** curve
    fc = f0 * (f1 / f0) ** u
    resp = np.exp(-0.5 * ((np.log2(freqs[None, :] + 1) - np.log2(fc[:, None])) / (bw_oct / 2)) ** 2)
    frames = np.fft.irfft(spec * resp, frame, axis=1) * win
    y = np.zeros(x.size)
    for i in range(nfr):
        y[i * hop:i * hop + frame] += frames[i]
    y = y[frame // 2: frame // 2 + n] / 1.5
    return y / (np.max(np.abs(y)) + 1e-12)


def whoosh(dur, f0, f1, rng, bw_oct=1.2, peak_at=0.4, shape=2.5):
    n = int(dur * SR)
    return sweep_noise(dur, f0, f1, rng, bw_oct) * env_ar(n, 0, shape, peak_at)


def metal(rng, base, n, dur, decay=0.4, spread=(1.0, 6.5), bright=1.0):
    ratios = np.sort(np.concatenate([[1.0], rng.uniform(*spread, n - 1)]))
    amps = rng.uniform(0.4, 1.0, n) / ratios ** (0.6 / bright)
    taus = decay * rng.uniform(0.5, 1.2, n) / ratios ** 0.3
    return I.modal(base * ratios, amps, taus, int(dur * SR), rng, attack=0.0003, coherent=True)


def thump(rng, f_top=110.0, f_bottom=45.0, dur=0.5, decay=0.18):
    n = int(dur * SR)
    t = seconds(n)
    f = f_bottom + (f_top - f_bottom) * np.exp(-t / 0.03)
    return np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / decay)


def crackle(dur, rate, rng, lo=1500, hi=9000, decay=0.004):
    n = int(dur * SR)
    out = np.zeros(n)
    count = rng.poisson(rate * dur)
    k = int(0.02 * SR)
    for _ in range(count):
        i = rng.integers(0, max(1, n - k))
        g = rng.uniform(0.2, 1.0) ** 2
        out[i:i + k] += g * rng.normal(size=k) * np.exp(-np.arange(k) / (decay * SR))
    return dsp.butter(out, "bandpass", (lo, hi), 2)


def layer(*arrays):
    """Sum of mono arrays of different lengths (zero-padded)."""
    out = np.zeros(max(a.size for a in arrays))
    for a in arrays:
        out[: a.size] += a
    return out


def place(total, *parts):
    """Mixes (start_seconds, mono array, gain) parts into one buffer of `total` seconds."""
    out = np.zeros(int(total * SR))
    for start, x, g in parts:
        i = int(start * SR)
        k = min(x.size, out.size - i)
        if k > 0:
            out[i:i + k] += g * x[:k]
    return out


def mono(stereo):
    return stereo.mean(axis=0) if stereo.ndim == 2 else stereo


def room(x, rt60=0.9, mix=0.25, seed="room"):
    ir_l, _ = dsp.make_ir(rt60=rt60, predelay=0.008, seed=seed)
    from scipy.signal import fftconvolve
    wet = fftconvolve(x, ir_l[: int(rt60 * 1.2 * SR)])[: x.size]
    return x + mix * wet / (np.max(np.abs(wet)) + 1e-12) * np.max(np.abs(x))


def tail(x, seconds_):
    return np.concatenate([x, np.zeros(int(seconds_ * SR))])


def formant_growl(dur, rng, f0=85.0, vowel_from="bas_a", vowel_to="bas_o", contour=(1.0, 1.25, 0.8)):
    """Pulse train with jitter, glided pitch and formant filtering that morphs between two vowels."""
    n = int(dur * SR)
    t = seconds(n)
    u = t / dur
    c0, c1, c2 = contour
    pitch = np.where(u < 0.35, c0 + (c1 - c0) * u / 0.35, c1 + (c2 - c1) * (u - 0.35) / 0.65)
    jitter = 1 + 0.06 * dsp.smooth_random(n, 30, rng) + 0.03 * np.sin(TAU * 7 * t)
    f = f0 * pitch * jitter
    ph = np.cumsum(f) / SR
    pulse = (ph % 1.0) ** 6 * 2 - 0.3
    pulse += 0.5 * rng.normal(size=n) * 0.4
    out = np.zeros(n)
    fa = I.VOWELS[vowel_from]
    fb = I.VOWELS[vowel_to]
    for k in range(5):
        for seg in range(4):
            lo, hi = seg * n // 4, (seg + 1) * n // 4
            m = (seg + 0.5) / 4
            fc = fa[k][0] * (1 - m) + fb[k][0] * m
            g = 10 ** ((fa[k][1] * (1 - m) + fb[k][1] * m) / 20)
            bw = (fa[k][2] * (1 - m) + fb[k][2] * m) * 2.5
            band = dsp.bandpass(pulse, fc, fc / bw)
            ramp = np.zeros(n)
            ramp[lo:hi] = 1.0
            ramp = dsp.one_pole_lp(ramp, 8.0)
            out += g * band * ramp
    return out / (np.max(np.abs(out)) + 1e-12)


# ------------------------------------------------------------------------------------------------- skills

def slash(rng, variant):
    sh = 1.0 + 0.08 * variant
    w = whoosh(0.2, 1200 * sh, 4200 * sh, rng, 1.0, peak_at=0.55, shape=3)
    ring = metal(rng, 3300 * sh, 8, 0.45, decay=0.12, spread=(1.0, 2.6))
    click = drums.noise_burst(int(0.02 * SR), rng, 2000, 12000, 0.002)
    return place(0.6, (0.0, w, 1.0), (0.1, ring, 0.35), (0.1, click, 0.5)), -1.0


def heavy_slash(rng):
    w = whoosh(0.38, 380, 1700, rng, 1.3, peak_at=0.6, shape=2)
    hit = layer(thump(rng, 120, 38, 0.7, 0.22), drums.noise_burst(int(0.25 * SR), rng, 150, 2500, 0.05) * 0.8)
    ring = metal(rng, 1900, 10, 0.9, decay=0.25, spread=(1.0, 3.0))
    return place(1.1, (0.0, w, 0.9), (0.24, hit, 1.0), (0.25, ring, 0.3)), -1.0


def fire_cast(rng):
    dur = 0.95
    n = int(dur * SR)
    roar = sweep_noise(dur, 250, 2600, rng, 2.2, curve=0.7) * env_ar(n, 0, 1.6, 0.55)
    ign = thump(rng, 160, 60, 0.4, 0.09)
    cr = crackle(dur, 70, rng) * np.linspace(0.3, 1.0, n)
    whomp = whoosh(0.5, 300, 900, rng, 1.5, 0.3)
    out = place(1.2, (0.0, roar, 1.0), (0.0, ign, 0.6), (0.1, cr, 0.6), (0.4, whomp, 0.5))
    return dsp.soft_clip(out * 1.5, 1.2), -2.0


def fire_explosion(rng):
    boom = drums.boom(1.0, rng, length=2.2, top=95, bottom=30)
    n = int(1.8 * SR)
    blast = dsp.butter(rng.normal(size=n), "lowpass", 1800, 2) * np.exp(-seconds(n) / 0.35)
    blast[: int(0.004 * SR)] *= np.linspace(0, 1, int(0.004 * SR))
    cr = crackle(1.8, 45, rng, 1000, 7000, 0.006) * np.exp(-seconds(n) / 0.7)
    debris = crackle(1.2, 25, rng, 2500, 9000, 0.002)
    out = place(2.3, (0.0, boom, 1.0), (0.0, blast, 0.9), (0.05, cr, 0.5), (0.25, debris, 0.3))
    return dsp.soft_clip(out * 1.6, 1.4), -1.0


def ice_cast(rng):
    fm = I.FMBell(ratio=3.51, index=2.2, decay=0.9)
    parts = []
    for k, p in enumerate([88, 95, 100, 91, 98, 103, 96, 107]):
        parts.append((k * 0.055 + rng.uniform(0, 0.02), mono(fm.render(p, 0.2, rng.uniform(0.5, 0.9), rng)), 0.5))
    n = int(0.9 * SR)
    air = dsp.butter(rng.normal(size=n), "highpass", 4000, 2) * np.linspace(0, 1, n) ** 2 * np.exp(-seconds(n) / 0.5)
    frost = crackle(0.9, 120, rng, 5000, 14000, 0.0015) * np.linspace(1, 0.2, n)
    parts += [(0.0, air, 0.35), (0.1, frost, 0.3)]
    return room(place(1.4, *parts), 1.2, 0.35, "ice"), -3.0


def ice_shatter(rng):
    n = int(0.9 * SR)
    out = np.zeros(n)
    for _ in range(46):
        start = int(abs(rng.normal(0, 0.06)) * SR)
        f = rng.uniform(2300, 9500)
        k = int(0.25 * SR)
        ping = I.modal([f, f * 1.53, f * 2.31], [1, 0.5, 0.25], rng.uniform(0.02, 0.12, 3), k, rng,
                       attack=0.0002, coherent=True)
        if start + k <= n:
            out[start:start + k] += ping * rng.uniform(0.3, 1.0)
    burst = drums.noise_burst(int(0.2 * SR), rng, 3000, 14000, 0.025)
    thud = thump(rng, 140, 60, 0.3, 0.06)
    return place(1.0, (0.0, out, 0.5), (0.0, burst, 0.8), (0.0, thud, 0.4)), -1.0


def thunder(rng):
    total = 3.0
    crack = drums.noise_burst(int(0.25 * SR), rng, 800, 14000, 0.03)
    for _ in range(5):
        i = int(rng.uniform(0.0, 0.15) * SR)
        crack[i:] += drums.noise_burst(crack.size - i, rng, 1500, 12000, 0.012) * rng.uniform(0.3, 0.8)
    n = int(2.9 * SR)
    t = seconds(n)
    brown = np.cumsum(rng.normal(size=n))
    brown = dsp.butter(brown - dsp.one_pole_lp(brown, 2.0), "lowpass", 220, 2)
    rumble = brown / (np.max(np.abs(brown)) + 1e-12) * np.exp(-t / 0.9)
    rumble *= 0.6 + 0.4 * (0.5 + 0.5 * dsp.smooth_random(n, 6, rng))
    rumble *= np.clip(t / 0.06, 0, 1)
    rumble = dsp.soft_clip(rumble * 3.5, 1.0)
    body = dsp.butter(rumble, "bandpass", (120, 600), 2)
    rumble = rumble + 0.75 * body / (np.max(np.abs(body)) + 1e-12) * np.max(np.abs(rumble))
    out = place(total, (0.0, crack, 1.0), (0.03, rumble, 1.3))
    return dsp.soft_clip(out * 1.6, 1.5), -1.0


def holy_chime(rng):
    bell = I.Bell("tubular", decay_scale=0.8)
    glock = I.Bell("glock", decay_scale=1.2)
    parts = []
    for k, p in enumerate([77, 81, 84, 91]):
        parts.append((k * 0.03, mono(bell.render(p, 1.0, 0.8, rng)), 0.6))
        parts.append((0.05 + k * 0.06, mono(glock.render(p + 12, 0.5, 0.6, rng)), 0.4))
    out = place(2.2, *parts)
    t = seconds(out.size)
    out *= 1 + 0.15 * np.sin(TAU * 6 * t)
    return room(out, 2.0, 0.45, "holy"), -3.0


def heal(rng):
    harp = I.Pluck("harp")
    pad = I.pad(spectrum=I.spec_flute, attack=0.35, release=0.8, bright=0.4, voices=3, detune=7)
    parts = [(k * 0.07, mono(harp.render(p, 0.8, 0.7, rng)), 0.7) for k, p in enumerate([60, 64, 67, 72, 76, 79])]
    for p in (64, 67, 72):
        parts.append((0.05, mono(pad.render(p, 0.7, 0.7, rng)), 0.45))
    fm = I.FMBell(ratio=4.0, index=1.5, decay=0.6)
    for k in range(6):
        parts.append((0.3 + k * 0.1, mono(fm.render(int(rng.choice([96, 100, 103, 108])), 0.1, 0.5, rng)), 0.25))
    return room(place(1.8, *parts), 1.4, 0.35, "heal"), -3.0


def buff(rng):
    n = int(0.45 * SR)
    t = seconds(n)
    f = 220 * 2 ** (t / 0.45 * 1.0)
    tone = np.zeros(n)
    for h in range(1, 12):
        tone += np.sin(TAU * np.cumsum(f * h) / SR) / h * I.lp_shape(np.array(220.0 * h), 1500 + 3000 * 1.0)
    tone *= env_ar(n, 0, 1.0, 0.9)
    rise = sweep_noise(0.45, 400, 5000, rng, 1.0) * np.linspace(0, 1, n) ** 2
    horns = I.trumpets(blat=0.6)
    stab = sum(mono(horns.render(p, 0.35, 0.9, rng, release=0.35)) for p in (62, 66, 69, 74))
    return room(place(1.3, (0.0, tone, 0.35), (0.0, rise, 0.4), (0.42, stab, 0.6)), 1.0, 0.3, "buff"), -3.0


def shadow_whoosh(rng):
    pre = drums.reverse_swell(0.3, rng, 150, 1500)
    w = whoosh(0.65, 1300, 160, rng, 1.6, peak_at=0.25, shape=2)
    n = int(0.9 * SR)
    t = seconds(n)
    ghost = (np.sin(TAU * 73 * t) + np.sin(TAU * 77.5 * t) + 0.5 * np.sin(TAU * 146 * t)) * env_ar(n, 0, 2.0, 0.3)
    voice = formant_growl(0.8, rng, 110, "bas_u", "bas_o", (1.0, 0.9, 0.7)) * env_ar(int(0.8 * SR), 0, 2, 0.3)
    return place(1.25, (0.0, pre, 0.6), (0.3, w, 1.0), (0.3, ghost, 0.35), (0.32, voice, 0.25)), -2.0


def _twang(rng, f0=118.0, strength=1.0):
    n = int(0.4 * SR)
    t = seconds(n)
    k = np.arange(1, 14)
    s = I.modal(f0 * k, 1 / k * np.abs(np.sin(np.pi * k * 0.1)), 0.15 / k ** 0.5, n, rng, attack=0.0002,
                coherent=True)
    snap = drums.noise_burst(int(0.015 * SR), rng, 2500, 12000, 0.0015)
    out = s / (np.max(np.abs(s)) + 1e-12)
    out[: snap.size] += snap * 0.8 * strength
    return out * np.exp(-t / 0.12)


def arrow_release(rng):
    w = whoosh(0.22, 4500, 1800, rng, 1.0, peak_at=0.3, shape=2.5)
    return place(0.6, (0.0, _twang(rng), 1.0), (0.02, w, 0.7)), -2.0


def arrow_volley(rng):
    parts = []
    for k in range(7):
        st = k * 0.055 + rng.uniform(0, 0.03)
        parts.append((st, _twang(rng, rng.uniform(105, 135), 0.8), 0.55))
        parts.append((st + 0.02, whoosh(0.3, rng.uniform(3500, 5500), rng.uniform(1300, 2200), rng, 1.0, 0.3), 0.45))
    cloud = whoosh(0.9, 2500, 900, rng, 1.6, peak_at=0.5)
    parts.append((0.1, cloud, 0.5))
    return place(1.15, *parts), -2.0


def magic_bolt(rng):
    n = int(0.42 * SR)
    t = seconds(n)
    f = 400 + 1800 * np.exp(-t / 0.08)
    idx = 4.0 * np.exp(-t / 0.15)
    zap = np.sin(TAU * np.cumsum(f) / SR + idx * np.sin(TAU * np.cumsum(f * 1.5) / SR)) * np.exp(-t / 0.15)
    sparkle = crackle(0.5, 90, rng, 4000, 12000, 0.002) * np.exp(-seconds(int(0.5 * SR)) / 0.2)
    w = whoosh(0.3, 2500, 900, rng, 1.2, 0.2)
    return room(place(0.7, (0.0, zap, 0.8), (0.0, sparkle, 0.4), (0.03, w, 0.5)), 0.8, 0.25, "bolt"), -2.0


# ------------------------------------------------------------------------------------------------- weapons

def swing_sword(rng, v):
    w = whoosh(0.17 + 0.02 * v, 1500 + 200 * v, 3600 + 300 * v, rng, 1.0, peak_at=0.5)
    glint = metal(rng, 4200 + 300 * v, 5, 0.3, decay=0.06, spread=(1.0, 2.0))
    return place(0.35, (0.0, w, 1.0), (0.07, glint, 0.12)), -3.0


def swing_greatsword(rng, v):
    w = whoosh(0.38 + 0.03 * v, 300 + 30 * v, 1250 + 100 * v, rng, 1.4, peak_at=0.55, shape=2)
    low = whoosh(0.38, 120, 300, rng, 1.0, peak_at=0.55) * 0.6
    return place(0.55, (0.0, w, 1.0), (0.0, low, 0.6)), -2.0


def swing_dual(rng, v):
    a = whoosh(0.12, 1900 + 150 * v, 4200, rng, 0.9, peak_at=0.5)
    b = whoosh(0.12, 2100, 4700 - 200 * v, rng, 0.9, peak_at=0.5)
    return place(0.32, (0.0, a, 1.0), (0.075 + 0.01 * v, b, 0.9)), -3.0


def swing_spear(rng, v):
    w = whoosh(0.15, 2600 + 150 * v, 1300, rng, 1.0, peak_at=0.25, shape=3)
    knock = drums.woodblock(0.7, rng, 520 + 40 * v)
    glint = metal(rng, 3800, 4, 0.2, decay=0.05, spread=(1.0, 2.0))
    return place(0.32, (0.0, w, 1.0), (0.0, knock, 0.35), (0.06, glint, 0.1)), -3.0


def swing_staff(rng, v):
    w = whoosh(0.26, 650 + 60 * v, 1500, rng, 1.3, peak_at=0.5)
    n = int(0.35 * SR)
    t = seconds(n)
    hum = np.sin(TAU * (220 + 30 * v) * t + 0.8 * np.sin(TAU * 5 * t)) * env_ar(n, 0, 2, 0.4)
    shimmer = crackle(0.35, 60, rng, 5000, 12000, 0.0015) * env_ar(n, 0, 2, 0.4)
    return place(0.45, (0.0, w, 1.0), (0.0, hum, 0.3), (0.05, shimmer, 0.25)), -3.0


def swing_bow(rng, v):
    w = whoosh(0.18, 4000, 2000, rng, 1.0, peak_at=0.3)
    return place(0.42, (0.0, _twang(rng, 140 + 10 * v, 0.7), 0.85), (0.02, w, 0.55)), -3.0


# ------------------------------------------------------------------------------------------------- defense

def guard(rng):
    hit = thump(rng, 150, 70, 0.3, 0.05)
    plate = metal(rng, 420, 12, 0.4, decay=0.08, spread=(1.0, 4.5))
    knock = drums.woodblock(0.8, rng, 300)
    noise = drums.noise_burst(int(0.08 * SR), rng, 300, 4000, 0.015)
    return place(0.45, (0.0, hit, 0.9), (0.0, plate, 0.6), (0.0, knock, 0.4), (0.0, noise, 0.6)), -1.0


def parry(rng):
    ring = metal(rng, 1250, 14, 1.1, decay=0.35, spread=(1.0, 5.0))
    trans = drums.noise_burst(int(0.03 * SR), rng, 2500, 15000, 0.003)
    sparks = crackle(0.4, 140, rng, 4000, 14000, 0.0015) * np.exp(-seconds(int(0.4 * SR)) / 0.12)
    w = whoosh(0.12, 2500, 5000, rng, 1.0, 0.6)
    return place(1.15, (0.0, w, 0.4), (0.05, ring, 1.0), (0.05, trans, 0.8), (0.06, sparks, 0.35)), -1.0


def dodge(rng):
    w = whoosh(0.24, 900, 380, rng, 1.6, peak_at=0.3, shape=2)
    cloth = sweep_noise(0.2, 2500, 1800, rng, 1.5) * env_ar(int(0.2 * SR), 0, 2, 0.2)
    return place(0.32, (0.0, w, 1.0), (0.0, cloth, 0.3)), -4.0


# ------------------------------------------------------------------------------------------------- bosses

def telegraph(rng):
    pre = drums.reverse_swell(0.45, rng, 300, 5000)
    n = int(0.8 * SR)
    t = seconds(n)
    pulses = np.zeros(n)
    for k in range(3):
        st = int(k * 0.22 * SR)
        m = int(0.16 * SR)
        tt = seconds(m)
        tone = np.zeros(m)
        for f in (466.2, 659.3):
            for h in (1, 3, 5):
                tone += np.sin(TAU * f * h * tt) / h
        tone *= np.minimum(tt / 0.006, 1) * np.exp(-tt / 0.08)
        if st + m <= n:
            pulses[st:st + m] += tone
    drone = np.sin(TAU * 55 * t) * 0.5 * env_ar(n, 0, 1.5, 0.1)
    return room(place(1.35, (0.0, pre, 0.6), (0.42, pulses, 0.5), (0.42, drone, 0.5)), 1.0, 0.3, "tel"), -2.0


def roar(rng):
    main = formant_growl(2.2, rng, 82, "bas_a", "bas_o", (0.9, 1.3, 0.75))
    low = formant_growl(2.2, rng, 41, "bas_o", "bas_u", (0.9, 1.2, 0.8))
    high = formant_growl(1.6, rng, 165, "ten_a", "bas_a", (1.0, 1.35, 0.9))
    n = int(2.2 * SR)
    breath = dsp.butter(rng.normal(size=n), "bandpass", (500, 4000), 2) * env_ar(n, 0, 1.5, 0.3)
    env = env_ar(n, 0, 1.2, 0.12)
    out = place(2.5, (0.0, main * env, 1.0), (0.0, low * env, 0.7), (0.15, high * env[: high.size], 0.35),
                (0.0, breath, 0.3))
    out = dsp.soft_clip(out * 2.2, 1.5)
    return room(out, 1.4, 0.3, "roar"), -1.0


def phase_change(rng):
    boom = drums.boom(1.0, rng, length=2.8, top=80, bottom=28)
    bell = I.Bell("church", decay_scale=0.7)
    b1 = mono(bell.render(45, 2.0, 0.9, rng))
    b2 = mono(bell.render(51, 2.0, 0.8, rng))
    choir = I.choir("bas_o", attack=0.8, release=0.6)
    sw = sum(mono(choir.render(p, 1.6, 0.8, rng, swell=0.8)) for p in (38, 44, 49))
    n = int(2.8 * SR)
    rum = dsp.butter(np.cumsum(rng.normal(size=n)), "bandpass", (25, 160), 2)
    rum = rum / (np.max(np.abs(rum)) + 1e-12) * np.exp(-seconds(n) / 1.2)
    out = place(3.2, (0.0, boom, 1.0), (0.0, b1, 0.5), (0.0, b2, 0.45), (0.15, sw, 0.5), (0.0, rum, 0.5))
    return room(dsp.soft_clip(out * 1.4, 1.2), 2.2, 0.35, "phase"), -1.0


# ------------------------------------------------------------------------------------------------- dungeon

def door_open(rng):
    dur = 1.9
    n = int(dur * SR)
    t = seconds(n)
    grind = sweep_noise(dur, 280, 520, rng, 2.5)
    rough = np.clip(1 + 1.4 * dsp.smooth_random(n, 35, rng), 0.05, 3)
    grind *= rough * np.minimum(t / 0.25, 1) * np.minimum((dur - t) / 0.2, 1)
    rumble = dsp.butter(rng.normal(size=n), "lowpass", 120, 2) * 3 * np.minimum(t / 0.3, 1)
    chips = crackle(dur, 18, rng, 1500, 6000, 0.004)
    thud = layer(thump(rng, 90, 40, 0.8, 0.25), drums.noise_burst(int(0.3 * SR), rng, 80, 900, 0.06))
    out = place(2.8, (0.0, grind, 0.7), (0.0, rumble, 0.5), (0.0, chips, 0.25), (1.95, thud, 1.0))
    return room(out, 1.8, 0.35, "door"), -2.0


def wave_start(rng):
    horn = I.WaveEnsemble("warhorn", I.spec_horn, voices=3, detune=7, vib_rate=4.6, vib_depth=12, vib_delay=0.4,
                          drift=4, attack=0.25, release=0.5, bright=0.55, vel_bright=0.6, blat=0.4, scoop=-60,
                          scoop_time=0.12, noise=0.01, noise_band=(400, 2500))
    a = mono(horn.render(45, 1.0, 0.95, rng))
    b = mono(horn.render(52, 1.3, 1.0, rng))
    return room(place(2.9, (0.0, a, 0.9), (1.05, b, 1.0)), 2.4, 0.45, "horn"), -1.0


# ------------------------------------------------------------------------------------------------- loot

def rare_drop(rng):
    glock, cel = I.Bell("glock"), I.Bell("celesta")
    parts = []
    for k, p in enumerate([84, 88, 91, 96]):
        parts.append((k * 0.065, mono(glock.render(p, 0.4, 0.8, rng)), 0.6))
        parts.append((k * 0.065, mono(cel.render(p - 12, 0.4, 0.6, rng)), 0.4))
    sparkle = crackle(0.8, 50, rng, 6000, 14000, 0.002) * np.exp(-seconds(int(0.8 * SR)) / 0.4)
    parts.append((0.2, sparkle, 0.25))
    return room(place(1.5, *parts), 1.5, 0.35, "rare"), -3.0


def legendary_drop(rng):
    tpt, horns = I.trumpets(blat=0.5), I.horns()
    choir = I.choir("sop_a", attack=0.3, release=0.8)
    parts = []
    for p in (65, 69, 72, 77):
        parts.append((0.12, mono(tpt.render(p, 1.4, 0.95, rng, release=0.8)), 0.35))
        parts.append((0.12, mono(choir.render(p, 1.5, 0.8, rng)), 0.3))
    for p in (53, 60):
        parts.append((0.12, mono(horns.render(p, 1.5, 0.85, rng, release=0.8)), 0.35))
    glock = I.Bell("glock", decay_scale=1.3)
    for k, p in enumerate([77, 81, 84, 89, 93, 96]):
        parts.append((k * 0.03, mono(glock.render(p + 12, 0.4, 0.8, rng)), 0.3))
    parts.append((0.12, drums.timpani(41, 1.0, rng), 0.6))
    parts.append((0.12, drums.crash(0.7, rng), 0.3))
    parts.append((0.0, drums.reverse_swell(0.14, rng, 2000, 12000), 0.5))
    return room(place(2.8, *parts), 2.0, 0.4, "legend"), -1.0


def gold(rng, v):
    parts = []
    count = 2 + v
    t = 0.0
    for k in range(count):
        f = rng.uniform(2600, 4200)
        coin = I.modal([f, f * 2.13, f * 3.62, f * 5.21], [1.0, 0.6, 0.35, 0.2], rng.uniform(0.05, 0.2, 4),
                       int(0.3 * SR), rng, attack=0.0002, coherent=True)
        parts.append((t, coin, rng.uniform(0.5, 1.0)))
        t += rng.uniform(0.03, 0.09)
    return place(0.45, *parts), -4.0


def enhance_success(rng):
    strike = drums.anvil(1.0, rng, 1100)
    fm = I.FMBell(ratio=3.0, index=1.8, decay=0.7)
    parts = [(0.0, strike, 0.8)]
    for k, p in enumerate([72, 76, 79, 84, 88]):
        parts.append((0.18 + k * 0.07, mono(fm.render(p, 0.2, 0.8, rng)), 0.4))
    glock = I.Bell("glock", decay_scale=1.2)
    for p in (84, 88, 91):
        parts.append((0.55, mono(glock.render(p, 0.6, 0.7, rng)), 0.35))
    return room(place(1.6, *parts), 1.4, 0.3, "succ"), -2.0


def enhance_fail(rng):
    strike = drums.anvil(0.9, rng, 780) * np.exp(-seconds(int(2.0 * SR)) / 0.25)
    crack = drums.noise_burst(int(0.25 * SR), rng, 600, 6000, 0.03) + thump(rng, 110, 45, 0.25, 0.06)
    dull = I.WaveEnsemble("dull", I.spec_square, voices=1, vib_depth=0, attack=0.01, release=0.15, bright=0.15,
                          vel_bright=0.0)
    parts = [(0.0, strike, 0.6), (0.12, crack, 0.8)]
    for k, p in enumerate([64, 63, 62]):
        parts.append((0.3 + k * 0.2, mono(dull.render(p, 0.18 if k < 2 else 0.4, 0.7, rng)), 0.8))
    return room(place(1.4, *parts), 1.0, 0.3, "fail"), -2.0


# ------------------------------------------------------------------------------------------------- ui

def ui_click(rng):
    body = I.modal([1850.0, 3900.0], [1.0, 0.4], [0.012, 0.006], int(0.06 * SR), rng, attack=0.0002, coherent=True)
    tick = drums.noise_burst(int(0.01 * SR), rng, 3000, 12000, 0.0008)
    return place(0.07, (0.0, body, 0.8), (0.0, tick, 0.5)), -8.0


def journal_open(rng):
    dur = 0.48
    n = int(dur * SR)
    rustle = dsp.butter(rng.normal(size=n), "bandpass", (1800, 7500), 2)
    flutter = np.clip(0.4 + 0.9 * dsp.smooth_random(n, 45, rng), 0, 2)
    rustle *= flutter * env_ar(n, 0, 1.5, 0.35)
    thump_ = layer(thump(rng, 160, 90, 0.12, 0.03), drums.noise_burst(int(0.05 * SR), rng, 200, 1500, 0.01))
    return place(0.55, (0.0, thump_, 0.5), (0.02, rustle, 0.8)), -6.0


def npc_talk(rng, v):
    f0 = (560.0, 640.0, 720.0)[v]
    n = int(0.075 * SR)
    t = seconds(n)
    tone = np.zeros(n)
    for h in range(1, 9):
        tone += np.sin(TAU * f0 * h * t * (1 - 0.04 * t / 0.075)) / h * (1 if h % 2 else 0.3)
    tone = dsp.bandpass(tone, 1200, 0.7) + 0.3 * tone
    env = np.minimum(t / 0.004, 1) * np.exp(-t / 0.03)
    return place(0.09, (0.0, tone * env, 1.0)), -8.0


# ------------------------------------------------------------------------------------------------- dispatch

RENDERERS = {
    "sfx/skill/slash_1": lambda r: slash(r, 0), "sfx/skill/slash_2": lambda r: slash(r, 1),
    "sfx/skill/heavy_slash": heavy_slash, "sfx/skill/fire_cast": fire_cast,
    "sfx/skill/fire_explosion": fire_explosion, "sfx/skill/ice_cast": ice_cast, "sfx/skill/ice_shatter": ice_shatter,
    "sfx/skill/thunder": thunder, "sfx/skill/holy_chime": holy_chime, "sfx/skill/heal": heal, "sfx/skill/buff": buff,
    "sfx/skill/shadow_whoosh": shadow_whoosh, "sfx/skill/arrow_release": arrow_release,
    "sfx/skill/arrow_volley": arrow_volley, "sfx/skill/magic_bolt": magic_bolt,
    "sfx/combat/guard": guard, "sfx/combat/parry": parry, "sfx/combat/dodge": dodge,
    "sfx/boss/telegraph": telegraph, "sfx/boss/roar": roar, "sfx/boss/phase_change": phase_change,
    "sfx/dungeon/door_open": door_open, "sfx/dungeon/wave_start": wave_start,
    "sfx/loot/rare_drop": rare_drop, "sfx/loot/legendary_drop": legendary_drop,
    "sfx/enhance/success": enhance_success, "sfx/enhance/fail": enhance_fail,
    "sfx/ui/click": ui_click, "sfx/ui/journal_open": journal_open,
}
for _v in range(2):
    RENDERERS[f"sfx/weapon/one_handed_sword_{_v + 1}"] = (lambda v: lambda r: swing_sword(r, v))(_v)
    RENDERERS[f"sfx/weapon/two_handed_sword_{_v + 1}"] = (lambda v: lambda r: swing_greatsword(r, v))(_v)
    RENDERERS[f"sfx/weapon/dual_blades_{_v + 1}"] = (lambda v: lambda r: swing_dual(r, v))(_v)
    RENDERERS[f"sfx/weapon/spear_{_v + 1}"] = (lambda v: lambda r: swing_spear(r, v))(_v)
    RENDERERS[f"sfx/weapon/staff_{_v + 1}"] = (lambda v: lambda r: swing_staff(r, v))(_v)
    RENDERERS[f"sfx/weapon/bow_{_v + 1}"] = (lambda v: lambda r: swing_bow(r, v))(_v)
for _v in range(3):
    RENDERERS[f"sfx/loot/gold_pickup_{_v + 1}"] = (lambda v: lambda r: gold(r, v))(_v)
    RENDERERS[f"sfx/ui/npc_talk_{_v + 1}"] = (lambda v: lambda r: npc_talk(r, v))(_v)


def render(stem):
    rng = dsp.rng_for("sfx", stem)
    audio, peak = RENDERERS[stem](rng)
    return np.asarray(audio, dtype=float), peak
