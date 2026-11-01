#!/usr/bin/env python3
"""FROSTBOUND sound generator.

Synthesises every sound of the mod with numpy (no samples, no recordings), encodes
them to mono 44.1 kHz Ogg Vorbis with ffmpeg (-c:a libvorbis -q:a 4) and writes
sounds.json. Fixed seeds and bit-exact muxing make the output deterministic.

    python3 tools/gen_sounds.py            # synthesise + encode + verify

Loops are built to be exactly periodic (integer-cycle tones, circular FFT noise,
zero-mean frequency drift, circularly placed events, circular reverb), so the last
sample flows straight into the first one. The script decodes every file again and
checks duration, channel count, peak level and the loop seam.
"""
from __future__ import annotations

import json
import subprocess
import zlib
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "frostbound"
NS = "frostbound"
SR = 44100
FFMPEG = "/usr/local/bin/ffmpeg"
PEAK = 10 ** (-1.0 / 20.0)  # normalise to -1 dBFS (headroom for the lossy codec)
SEED = 0x0F205B0D


def rng_for(name: str) -> np.random.Generator:
    return np.random.default_rng([SEED, zlib.crc32(name.encode("utf-8"))])


def ns(sec: float) -> int:
    return int(round(sec * SR))


# --------------------------------------------------------------------------------------
# DSP helpers
# --------------------------------------------------------------------------------------

def bp(lo=None, hi=None, order=2, tilt=0.0, ref=1000.0):
    """Magnitude response: Butterworth-like band edges plus a spectral tilt (dB/oct ~ 6*tilt)."""
    def f(fr):
        fr = np.maximum(fr, 1.0)
        g = (fr / ref) ** tilt
        if lo:
            g = g / np.sqrt(1 + (lo / fr) ** (2 * order))
        if hi:
            g = g / np.sqrt(1 + (fr / hi) ** (2 * order))
        return g
    return f


def peak(fc, bw, gain=1.0):
    return lambda fr: gain / (1 + ((fr - fc) / bw) ** 2)


def noise(n, rng, shape) -> np.ndarray:
    """Noise of length n with the given magnitude spectrum. Periodic with period n."""
    fr = np.fft.rfftfreq(n, 1 / SR)
    spec = shape(fr) * np.exp(1j * rng.uniform(0, 2 * np.pi, len(fr)))
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-12)


def circ_filter(x, shape):
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) * shape(fr), len(x))


def lin_filter(x, shape, pad=0.5):
    """Zero-phase filtering without wrap-around (for one-shots)."""
    p = len(x) + ns(pad)
    fr = np.fft.rfftfreq(p, 1 / SR)
    return np.fft.irfft(np.fft.rfft(x, p) * shape(fr), p)[:len(x)]


def lfo(n, rng, harmonics, power=1.0):
    """Smooth random signal in [-1, 1], periodic over n samples (harmonics 1..k of the loop)."""
    t = np.arange(n) / n
    y = np.zeros(n)
    for k in range(1, harmonics + 1):
        y += (rng.normal() * np.cos(2 * np.pi * k * t) + rng.normal() * np.sin(2 * np.pi * k * t)) / k ** power
    return y / (np.max(np.abs(y)) + 1e-12)


def circ_bump(n, center, width):
    """Raised-cosine bump (seconds) on a circular timeline of n samples."""
    T = n / SR
    d = (np.arange(n) / SR - center + T / 2) % T - T / 2
    w = width / 2
    return np.where(np.abs(d) < w, 0.5 * (1 + np.cos(np.pi * d / w)), 0.0)


def add_circ(buf, start, ev):
    idx = (int(start) + np.arange(len(ev))) % len(buf)
    np.add.at(buf, idx, ev)


def add_lin(buf, start, ev):
    start = int(start)
    end = min(len(buf), start + len(ev))
    if end > start:
        buf[start:end] += ev[:end - start]


def analytic(x):
    n = len(x)
    h = np.zeros(n)
    h[0] = 1
    if n % 2 == 0:
        h[n // 2] = 1
        h[1:n // 2] = 2
    else:
        h[1:(n + 1) // 2] = 2
    return np.fft.ifft(np.fft.fft(x) * h)


def whistle(n, rng, f0, bw, drift, depth):
    """Narrow-band noise around f0 whose centre wanders by depth*f0*drift(t).

    drift is made zero-mean, so the accumulated phase returns to its start after n
    samples and the result stays periodic.
    """
    nb = noise(n, rng, lambda fr: np.exp(-0.5 * ((fr - f0) / bw) ** 2))
    df = depth * f0 * drift
    df = df - df.mean()
    phase = 2 * np.pi * np.cumsum(df) / SR
    return np.real(analytic(nb) * np.exp(1j * phase))


def modes(L, table, rng=None, phase0=True):
    """Sum of exponentially decaying sinusoids: table = [(freq, decay_s, amp), ...]."""
    t = np.arange(L) / SR
    y = np.zeros(L)
    for f, d, a in table:
        ph = 0.0 if (phase0 or rng is None) else rng.uniform(0, 2 * np.pi)
        y += a * np.exp(-t / d) * np.sin(2 * np.pi * f * t + ph)
    return y


def attack(L, sec):
    return 1 - np.exp(-np.arange(L) / max(1.0, sec * SR))


def thump(L, f_end, f_start, drop_s, decay_s, amp=1.0):
    t = np.arange(L) / SR
    f = f_end + (f_start - f_end) * np.exp(-t / drop_s)
    return amp * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay_s) * attack(L, 0.002)


def reverb_ir(rng, sec, rt60, lp, predelay=0.012):
    L = ns(sec)
    t = np.arange(L) / SR
    ir = rng.normal(size=L) * np.exp(-6.91 * t / rt60)
    ir = lin_filter(ir, bp(lo=120, hi=lp, order=1), pad=0.1)
    ir[:ns(predelay)] = 0
    return ir / np.sqrt(np.sum(ir ** 2))


def circ_convolve(x, ir):
    n = len(x)
    irp = np.zeros(n)
    np.add.at(irp, np.arange(len(ir)) % n, ir)
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(irp), n)


def lin_convolve(x, ir):
    p = len(x) + len(ir)
    return np.fft.irfft(np.fft.rfft(x, p) * np.fft.rfft(ir, p), p)[:len(x)]


def fade(x, fin=0.0, fout=0.0):
    x = x.copy()
    if fin > 0:
        k = ns(fin)
        x[:k] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, k))
    if fout > 0:
        k = ns(fout)
        x[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
        x[-1] = 0.0
    return x


def normalise(x):
    x = x - np.mean(x)
    return x * (PEAK / np.max(np.abs(x)))


def seam_candidates(x, win=2048, count=8, min_gap=0.25, search=441):
    """Quiet, upward zero crossings of a periodic loop, quietest first.

    Rotating a periodic loop to start at any of these keeps it sample-identical as a
    cycle; the encoder then has the least signal to reconstruct at the file edges.
    """
    n = len(x)
    k = np.zeros(n)
    k[:win] = 1.0 / win
    env = np.fft.irfft(np.fft.rfft(x ** 2) * np.fft.rfft(k), n)
    centres = []
    for c in np.argsort(env):
        c = (int(c) - win // 2) % n
        if all(min(abs(c - d), n - abs(c - d)) > min_gap * SR for d in centres):
            centres.append(c)
        if len(centres) == count:
            break
    out = []
    for c in centres:
        best = None
        for j in range(c - search, c + search):
            a, b = x[(j - 1) % n], x[j % n]
            if a < 0 <= b and (best is None or abs(a) + abs(b) < best[0]):
                best = (abs(a) + abs(b), j % n)
        out.append(best[1] if best else c)
    return out


# --------------------------------------------------------------------------------------
# Sounds
# --------------------------------------------------------------------------------------

def snd_generator_hum():
    """8 s loop: 40/40.5 Hz drone (0.5 Hz beat), 1 Hz piston pulse, faint metal resonance."""
    rng = rng_for("generator_hum")
    T = 8.0
    n = ns(T)
    t = np.arange(n) / SR
    drone = np.zeros(n)
    for f, a in ((40.0, 1.0), (40.5, 0.8), (56.0, 0.22)):  # all integer cycles per 8 s
        for h, ah in ((1, 1.0), (2, 0.5), (3, 0.28), (4, 0.13), (5, 0.06)):
            drone += a * ah * np.sin(2 * np.pi * f * h * t + rng.uniform(0, 2 * np.pi))
    pulse = 0.5 + 0.5 * np.cos(2 * np.pi * 1.0 * t)  # one stroke per second
    pulse = pulse ** 3
    stroke = 0.55 + 0.45 * np.cos(2 * np.pi * 0.5 * t)  # strong / weak alternation
    drone *= 0.78 + 0.22 * pulse * stroke
    rumble = noise(n, rng, bp(28, 95, order=3)) * (0.55 + 0.45 * pulse)
    out = drone + 0.45 * rumble

    chuff_src = noise(n, rng, bp(180, 1400, order=2, tilt=-0.5))
    hiss_src = noise(n, rng, bp(2500, 9000, order=2))
    ring_tab = [(233.0, 1.4, 0.045), (347.25, 1.1, 0.035), (521.5, 0.9, 0.03),
                (788.0, 0.7, 0.022), (1147.125, 0.5, 0.015)]
    L = ns(1.6)
    for k in range(8):
        st = ns(k * 1.0 + 0.02)
        strength = 1.0 if k % 2 == 0 else 0.62
        add_circ(out, st, strength * thump(ns(0.7), 44, 70, 0.05, 0.17, 1.1))
        tt = np.arange(ns(0.5)) / SR
        env = (1 - np.exp(-tt / 0.02)) * np.exp(-tt / 0.14)
        add_circ(out, st + ns(0.03), strength * 0.16 * env * chuff_src[st:st + len(tt)])
        add_circ(out, st + ns(0.03), strength * 0.04 * env * hiss_src[st:st + len(tt)])
        ring = modes(L, [(f * (1 + rng.normal() * 0.0004), d, a * strength) for f, d, a in ring_tab])
        add_circ(out, st, ring * attack(L, 0.004))
    shimmer = 0.012 * np.sin(2 * np.pi * 466.0 * t) * (0.5 + 0.5 * lfo(n, rng, 3))
    return out + shimmer, True


def snd_steam_hiss():
    """1.5 s pressurised steam burst: valve pop, bright jet, dropping whistle, decay."""
    rng = rng_for("steam_hiss")
    n = ns(1.5)
    t = np.arange(n) / SR
    atk = 1 - np.exp(-t / 0.012)
    bright = noise(n, rng, bp(2600, 9500, order=2)) * atk * np.exp(-np.maximum(t - 0.07, 0) / 0.26)
    mid = noise(n, rng, bp(700, 3200, order=2)) * atk * np.exp(-np.maximum(t - 0.10, 0) / 0.40)
    low = noise(n, rng, bp(120, 650, order=2)) * atk * np.exp(-np.maximum(t - 0.04, 0) / 0.18)
    drop = 1.0 - 0.18 * (1 - np.exp(-t / 0.5))  # pitch of the jet falls as pressure drops
    nb = noise(n, rng, lambda fr: np.exp(-0.5 * ((fr - 3600) / 160) ** 2))
    phase = 2 * np.pi * np.cumsum(3600 * (drop - 1)) / SR
    whistle_ = np.real(analytic(nb) * np.exp(1j * phase)) * atk * np.exp(-t / 0.35)
    flutter = 1 + 0.15 * lfo(n, rng, 40)
    x = (0.9 * bright + 0.55 * mid + 0.3 * low + 0.25 * whistle_) * flutter
    x += thump(n, 120, 180, 0.01, 0.025, 0.35)
    return fade(x, fin=0.002, fout=0.15), False


def snd_wind_loop():
    """10 s loop of cold wind: slow gusts, a wandering resonance and soft whistles."""
    rng = rng_for("wind_loop")
    T = 10.0
    n = ns(T)
    g = lfo(n, rng, 7, power=1.1)
    gust = np.exp(0.85 * g)
    gust /= gust.max()
    low = noise(n, rng, bp(70, 420, order=2, tilt=-0.5))
    mid = noise(n, rng, bp(300, 1500, order=2, tilt=-0.3))
    high = noise(n, rng, bp(1600, 6000, order=2))
    res = whistle(n, rng, 540, 110, lfo(n, rng, 5), 0.28)
    out = 0.35 * low * (0.6 + 0.4 * gust) + 0.55 * mid * gust + 0.12 * high * gust ** 1.6
    out += 0.45 * res * gust ** 1.2
    for f0, bw, amp in ((880.0, 9.0, 0.5), (1320.0, 11.0, 0.36), (1745.0, 13.0, 0.22)):
        swell = np.clip(lfo(n, rng, 5) + 0.25, 0, None) ** 1.2
        out += amp * whistle(n, rng, f0, bw, lfo(n, rng, 4), 0.06) * swell * (0.5 + 0.5 * gust)
    return out, True


def snd_blizzard_loop():
    """10 s loop of harsh wind: three big gusts, howling resonances, pelting ice grains."""
    rng = rng_for("blizzard_loop")
    T = 10.0
    n = ns(T)
    g = 0.22 * (lfo(n, rng, 9) + 1)
    for c, w, a in ((1.1, 2.4, 0.75), (4.6, 2.0, 0.6), (7.7, 2.8, 0.85)):
        g = g + a * circ_bump(n, c, w)
    g = g / g.max()
    low = noise(n, rng, bp(35, 220, order=2, tilt=-0.4))
    mid = noise(n, rng, bp(250, 1900, order=2, tilt=-0.2))
    high = noise(n, rng, bp(1800, 8000, order=2))
    out = 0.5 * low * (0.5 + 0.5 * g) + 0.7 * mid * (0.35 + 0.65 * g) + 0.42 * high * (0.2 + 0.8 * g) ** 1.5
    out += 0.95 * whistle(n, rng, 430, 40, lfo(n, rng, 6), 0.14) * (0.3 + 0.7 * g)
    out += 0.75 * whistle(n, rng, 640, 30, lfo(n, rng, 6), 0.12) * (0.2 + 0.8 * g) ** 1.4
    out += 0.35 * whistle(n, rng, 1880, 16, lfo(n, rng, 5), 0.08) * g ** 2.5
    grains_src = noise(ns(1.0), rng, bp(3000, 10000, order=2))
    count = 0
    while count < 1600:
        st = int(rng.integers(n))
        if rng.random() > 0.15 + 0.85 * g[st]:
            continue
        L = int(rng.integers(40, 160))
        off = int(rng.integers(len(grains_src) - L))
        env = np.exp(-np.arange(L) / (L / 4)) * attack(L, 0.0002)
        add_circ(out, st, 0.22 * rng.uniform(0.2, 1.0) * env * grains_src[off:off + L])
        count += 1
    return out, True


def snd_build():
    """0.5 s hammer knock: wood body, metal ring, short rebound."""
    rng = rng_for("build")
    n = ns(0.5)
    out = np.zeros(n)

    def hit(scale, detune):
        L = ns(0.45)
        wood = modes(L, [(185 * detune, 0.065, 1.0), (412 * detune, 0.045, 0.62), (868 * detune, 0.03, 0.45),
                         (1650 * detune, 0.018, 0.3), (2930 * detune, 0.009, 0.18)])
        metal = modes(L, [(2380, 0.13, 0.1), (3575, 0.09, 0.07), (5110, 0.06, 0.045)])
        burst = noise(L, rng, bp(400, 7000, order=1)) * np.exp(-np.arange(L) / (0.003 * SR)) * 0.6
        return scale * (wood + metal + burst + thump(L, 75, 120, 0.01, 0.05, 0.7)) * attack(L, 0.0008)

    add_lin(out, ns(0.004), hit(1.0, 1.0))
    add_lin(out, ns(0.094), hit(0.2, 1.04))
    return fade(out, fout=0.04), False


def snd_mining():
    """0.5 s pick on stone: sharp crack, metallic tink, crumbling grit."""
    rng = rng_for("mining")
    n = ns(0.5)
    out = np.zeros(n)
    L = ns(0.45)
    t = np.arange(L) / SR
    crack = noise(L, rng, bp(1200, 11000, order=1)) * np.exp(-t / 0.006)
    tink = modes(L, [(2930, 0.065, 0.36), (4415, 0.045, 0.26), (6170, 0.03, 0.18), (1725, 0.05, 0.14)])
    body = noise(L, rng, bp(300, 2200, order=2)) * np.exp(-t / 0.03) * 0.55
    hit = (crack + tink + body + thump(L, 105, 160, 0.008, 0.035, 0.55)) * attack(L, 0.0005)
    add_lin(out, ns(0.003), hit)
    grit = noise(ns(0.5), rng, bp(900, 6500, order=2))
    for k in range(26):
        st = ns(0.02 + (rng.random() ** 1.7) * 0.32)
        Lg = int(rng.integers(60, 200))
        amp = 0.28 * rng.uniform(0.25, 1.0) * np.exp(-(st / SR) / 0.12)
        env = np.exp(-np.arange(Lg) / (Lg / 3.5)) * attack(Lg, 0.0003)
        add_lin(out, st, amp * env * grit[st:st + Lg])
    return fade(out, fout=0.05), False


def snd_alert():
    """1.2 s two-tone warning: brassy reed tone alternating G5 / D5, short room tail."""
    rng = rng_for("alert")
    n = ns(1.2)
    out = np.zeros(n)
    segs = [(0.0, 784.0), (0.3, 587.33), (0.6, 784.0), (0.9, 587.33)]
    for start, f in segs:
        L = ns(0.27)
        t = np.arange(L) / SR
        fi = f * (1 + 0.004 * np.sin(2 * np.pi * 6.0 * t))
        ph = 2 * np.pi * np.cumsum(fi) / SR
        tone = np.zeros(L)
        for h in range(1, 11):
            a = (1.0 if h % 2 else 0.45) / h ** 1.05
            tone += a * np.sin(h * ph)
        env = attack(L, 0.004) * np.minimum(1.0, (L - np.arange(L)) / ns(0.03))
        env *= 1 - 0.12 * (t / t[-1])
        add_lin(out, ns(start + 0.004), tone * env)
    out = lin_filter(out, bp(lo=250, hi=5500, order=2) )
    out += 0.03 * noise(n, rng, bp(1000, 3000)) * (np.abs(out) > 0.05)
    out = out + 0.18 * lin_convolve(out, reverb_ir(rng, 0.6, 0.35, 4000))
    return fade(out, fin=0.001, fout=0.03), False


def snd_event_major():
    """3 s ominous swell: low bell strike, A-minor brass crescendo, sub boom, hall tail."""
    rng = rng_for("event_major")
    n = ns(3.0)
    t = np.arange(n) / SR
    prime = 110.0
    bell = np.zeros(n)
    for ratio, amp, dec in ((0.5, 0.6, 2.4), (1.0, 0.8, 1.7), (1.19, 0.5, 1.4), (1.5, 0.3, 1.1),
                            (2.0, 0.65, 1.2), (2.5, 0.22, 0.7), (2.67, 0.18, 0.6), (3.0, 0.16, 0.5),
                            (4.0, 0.09, 0.4), (5.33, 0.05, 0.3)):
        for det in (-0.0012, 0.0012):  # paired partials beat slowly
            bell += 0.5 * amp * np.exp(-t / dec) * np.sin(2 * np.pi * prime * ratio * (1 + det) * t)
    bell *= attack(n, 0.002)
    strike = noise(n, rng, bp(900, 5000, order=2)) * np.exp(-t / 0.006) * 0.35
    strike += modes(n, [(1210, 0.08, 0.08), (1873, 0.06, 0.06), (2634, 0.05, 0.04)])

    swell = np.clip((t - 0.15) / 1.55, 0, 1)
    swell = np.sin(swell * np.pi / 2) ** 2
    swell *= np.where(t < 2.1, 1.0, np.clip(1 - (t - 2.1) / 0.85, 0, 1) ** 1.5)
    brass = np.zeros(n)
    for f, a in ((55.0, 0.7), (110.0, 0.55), (130.81, 0.42), (164.81, 0.42), (220.0, 0.2)):
        for det in (-0.0018, 0.0015):
            fi = f * (1 + det) * (1 + 0.0025 * np.sin(2 * np.pi * 4.6 * t + rng.uniform(0, 6)))
            ph = 2 * np.pi * np.cumsum(fi) / SR
            for h in range(1, 26):
                if f * h > 6000:
                    break
                bright = np.exp(-h / (1.2 + 8.5 * swell))
                brass += 0.5 * a / h * bright * np.sin(h * ph)
    brass *= swell
    boom = thump(n, 42, 72, 0.06, 0.45, 0.8)
    dry = 0.75 * bell + strike + 1.25 * brass + boom
    wet = lin_convolve(dry, reverb_ir(rng, 2.5, 2.2, 3500, predelay=0.02))
    return fade(0.8 * dry + 0.35 * wet, fin=0.001, fout=0.45), False


def snd_ui_click():
    """80 ms small metallic click: press + latch."""
    rng = rng_for("ui_click")
    n = ns(0.08)
    out = np.zeros(n)
    for start, amp, det in ((0.0015, 1.0, 1.0), (0.0145, 0.42, 1.07)):
        L = ns(0.06)
        tab = [(3150 * det, 0.005, 0.6), (5300 * det, 0.003, 0.4), (1850 * det, 0.007, 0.35),
               (8900 * det, 0.0015, 0.2)]
        tick = modes(L, tab) + noise(L, rng, bp(2000, 12000, order=1)) * np.exp(-np.arange(L) / 40.0) * 0.5
        add_lin(out, ns(start), amp * tick * attack(L, 0.0002))
    return fade(out, fout=0.012), False


def snd_ui_open():
    """0.3 s soft mechanical slide ending in a damped clunk."""
    rng = rng_for("ui_open")
    n = ns(0.3)
    t = np.arange(n) / SR
    slide_env = np.clip(t / 0.09, 0, 1) ** 1.5 * np.clip((0.175 - t) / 0.08, 0, 1)
    slide = noise(n, rng, bp(600, 3200, order=2, tilt=-0.4)) * slide_env * 0.3
    for k in range(9):  # faint ratchet ticks along the slide
        st = ns(0.03 + k * 0.016 + rng.normal() * 0.002)
        L = ns(0.01)
        add_lin(slide, st, 0.08 * slide_env[st] * modes(L, [(2400 + 150 * k, 0.002, 1.0)]))
    out = slide
    L = ns(0.12)
    clunk = (thump(L, 120, 170, 0.01, 0.04, 0.8)
             + modes(L, [(430, 0.025, 0.5), (965, 0.015, 0.3), (2710, 0.018, 0.1)])
             + noise(L, rng, bp(200, 4000, order=1)) * np.exp(-np.arange(L) / (0.003 * SR)) * 0.25)
    add_lin(out, ns(0.178), clunk * attack(L, 0.0008))
    out = lin_filter(out, bp(lo=60, hi=7000, order=1))
    return fade(out, fin=0.002, fout=0.03), False


VOWELS = [(730, 1090, 2440), (530, 1840, 2480), (300, 2200, 2950), (570, 840, 2410),
          (320, 900, 2300), (500, 1500, 2500), (660, 1700, 2400)]


def babble_voice(n, rng, T):
    """One distant, unintelligible voice: periodic f0 contour, random vowel formants."""
    male = rng.random() < 0.6
    f_base = rng.uniform(98, 140) if male else rng.uniform(175, 235)
    fscale = 1.0 if male else 1.16
    f0 = f_base * (1 + 0.07 * lfo(n, rng, int(4 * T)))
    cycles = f0.sum() / SR
    f0 += (np.round(cycles) - cycles) / T  # whole number of cycles per loop
    phase = 2 * np.pi * np.cumsum(f0) / SR
    phase -= phase[0]
    hop = 60
    nc = n // hop
    tc = np.arange(nc) * hop / SR
    env = np.zeros(nc)
    F = np.zeros((3, nc))
    cur = rng.uniform(0, T)
    elapsed = 0.0
    vowel = VOWELS[0]
    while elapsed < T:
        talk, pause = rng.uniform(0.8, 2.3), rng.uniform(0.25, 1.0)
        tt = 0.0
        while tt < talk and elapsed + tt < T:
            dur = rng.uniform(0.13, 0.28)
            c = (cur + elapsed + tt + dur / 2) % T
            d = (tc - c + T / 2) % T - T / 2
            w = dur * 0.6
            bump = np.where(np.abs(d) < w, 0.5 * (1 + np.cos(np.pi * d / w)), 0.0) * rng.uniform(0.5, 1.0)
            env = np.maximum(env, bump)
            vowel = VOWELS[int(rng.integers(len(VOWELS)))]
            sel = np.abs(d) < dur / 2
            for i in range(3):
                F[i, sel] = vowel[i] * fscale
            tt += dur
        elapsed += talk + pause
    for i in range(3):  # fill silent gaps, then glide smoothly between vowels
        row = F[i]
        if (row == 0).all():
            row[:] = VOWELS[5][i] * fscale
        else:
            idx = np.nonzero(row)[0]
            row[:] = np.interp(np.arange(nc), np.concatenate([idx - nc, idx, idx + nc]),
                               np.concatenate([row[idx]] * 3))
        sp = np.fft.rfft(row)
        sp[int(nc * hop / SR * 9):] = 0  # ~9 Hz glide
        F[i] = np.fft.irfft(sp, nc)
    f0c = f0[::hop][:nc]
    xs = np.arange(n)
    xc = np.concatenate([np.arange(nc) * hop, [n]])
    y = np.zeros(n)
    bws, gains = (90.0, 110.0, 160.0), (1.0, 0.7, 0.35)
    h = 1
    while h * f_base * 1.1 < 3800:
        fh = h * f0c
        g = sum(gn / (1 + ((fh - F[i]) / bws[i]) ** 2) for i, gn in enumerate(gains))
        amp = g * env / h ** 0.9
        y += np.interp(xs, xc, np.concatenate([amp, amp[:1]])) * np.sin(h * phase)
        h += 1
    return y * rng.uniform(0.45, 1.0)


def snd_city_murmur():
    """6 s loop: distant crowd murmur (synthetic babble, no words) and work clatter."""
    rng = rng_for("city_murmur")
    T = 6.0
    n = ns(T)
    crowd = sum(babble_voice(n, rng, T) for _ in range(16))
    crowd = circ_filter(crowd, bp(lo=110, hi=1500, order=2))
    crowd /= np.std(crowd)
    air = noise(n, rng, bp(180, 1100, order=1, tilt=-0.5)) * (0.8 + 0.2 * lfo(n, rng, 3))

    clatter = np.zeros(n)
    L = ns(0.8)
    for k in range(5):  # metal clanks
        d = rng.uniform(0.94, 1.06)
        ev = modes(L, [(1180 * d, 0.16, 0.5), (1830 * d, 0.12, 0.4), (2615 * d, 0.09, 0.3), (3870 * d, 0.06, 0.2)])
        ev += noise(L, rng, bp(500, 6000)) * np.exp(-np.arange(L) / (0.004 * SR)) * 0.4
        add_circ(clatter, ns(rng.uniform(0, T)), rng.uniform(0.35, 0.8) * ev * attack(L, 0.0005))
    for start in (0.7, 3.9):  # someone hammering: three strikes
        for j in range(3):
            ev = modes(L, [(2105, 0.05, 0.5), (3290, 0.04, 0.35), (760, 0.03, 0.3)])
            ev += thump(L, 90, 140, 0.01, 0.03, 0.4)
            add_circ(clatter, ns(start + j * 0.42 + rng.normal() * 0.01), 0.5 * ev * attack(L, 0.0005))
    for k in range(4):  # wooden knocks
        ev = modes(L, [(205, 0.05, 0.8), (468, 0.035, 0.5), (990, 0.02, 0.3)])
        add_circ(clatter, ns(rng.uniform(0, T)), rng.uniform(0.3, 0.6) * ev * attack(L, 0.0008))
    puff = noise(ns(0.6), rng, bp(500, 3000))
    tt = np.arange(len(puff)) / SR
    add_circ(clatter, ns(2.5), 0.25 * puff * (1 - np.exp(-tt / 0.03)) * np.exp(-tt / 0.15))
    clatter = circ_filter(clatter, bp(lo=150, hi=3200, order=2))
    clatter *= 0.55 / (np.max(np.abs(clatter)) + 1e-12) * np.max(np.abs(crowd)) * 0.5

    dry = crowd * (0.85 + 0.15 * lfo(n, rng, 2)) + 0.35 * air + clatter
    wet = circ_convolve(dry, reverb_ir(rng, 1.8, 1.4, 2600, predelay=0.02))
    wet *= np.std(dry) / (np.std(wet) + 1e-12)
    return 0.6 * dry + 0.55 * wet, True


SOUNDS = {
    "generator_hum": snd_generator_hum,
    "steam_hiss": snd_steam_hiss,
    "wind_loop": snd_wind_loop,
    "blizzard_loop": snd_blizzard_loop,
    "build": snd_build,
    "mining": snd_mining,
    "alert": snd_alert,
    "event_major": snd_event_major,
    "ui_click": snd_ui_click,
    "ui_open": snd_ui_open,
    "city_murmur": snd_city_murmur,
}


# --------------------------------------------------------------------------------------
# Encode / verify / sounds.json
# --------------------------------------------------------------------------------------

def encode(x: np.ndarray) -> bytes:
    cmd = [FFMPEG, "-hide_banner", "-loglevel", "error",
           "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "pipe:0",
           "-c:a", "libvorbis", "-q:a", "4",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", "-f", "ogg", "pipe:1"]
    return subprocess.run(cmd, input=x.astype("<f4").tobytes(), capture_output=True, check=True).stdout


def decode(ogg: bytes) -> np.ndarray:
    out = subprocess.run([FFMPEG, "-v", "error", "-f", "ogg", "-i", "pipe:0", "-f", "f32le", "-ac", "1",
                          "-ar", str(SR), "pipe:1"], input=ogg, capture_output=True, check=True).stdout
    return np.frombuffer(out, "<f4").astype(np.float64)


def ogg_samples(data: bytes) -> int:
    """Exact sample count from the final Ogg page granule position (what the decoder must output)."""
    pos, gran = 0, -1
    while (i := data.find(b"OggS", pos)) >= 0:
        g = int.from_bytes(data[i + 6:i + 14], "little", signed=True)
        if g >= 0:
            gran = g
        nseg = data[i + 26]
        pos = i + 27 + nseg + sum(data[i + 27:i + 27 + nseg])
    return gran


def seam_ratio(y: np.ndarray, order=32, k=4410):
    """Click detector for the loop point.

    Fits a linear predictor on the 100 ms around the wrap (end -> start) and returns
    (ratio, level): the largest prediction error at the seam relative to the RMS error
    elsewhere, and that error in dBFS. A clean, periodic loop scores like any other spot
    (ratio ~1-3); a discontinuity scores >> 10. A seam only counts as a click if it is
    both an outlier and loud enough to hear (> -50 dBFS).
    """
    w = np.concatenate([y[-k:], y[:k]])
    X = np.stack([w[order - i - 1:len(w) - i - 1] for i in range(order)], axis=1)
    tgt = w[order:]
    a, *_ = np.linalg.lstsq(X, tgt, rcond=None)
    e = tgt - X @ a
    s = k - order
    rest = np.concatenate([e[:s - 2], e[s + 3:]])
    worst = float(np.abs(e[s - 2:s + 3]).max())
    return worst / float(np.sqrt(np.mean(rest ** 2))), 20 * np.log10(worst + 1e-12)


def main():
    out_dir = ASSETS / "sounds"
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = {}
    problems = []
    print(f"{'sound':15s} {'dur':>6s} {'peak':>6s} {'rms dB':>7s}  loop seam (synth -> ogg)")
    for name, fn in SOUNDS.items():
        x, loop = fn()
        x = normalise(x)
        if loop:
            # try a few equivalent rotations of the loop and keep the cleanest decoded seam
            trials = []
            for j in seam_candidates(x):
                xr = np.roll(x, -j)
                ogg = encode(xr)
                y = decode(ogg)
                r, lv = seam_ratio(y)
                trials.append((r, lv, j, xr, ogg, y))
            r1, lv, _, x, ogg, y = min(trials, key=lambda t: t[0])
        else:
            ogg = encode(x)
            y = decode(ogg)
        (out_dir / f"{name}.ogg").write_bytes(ogg)
        count = ogg_samples(ogg)
        dur = count / SR
        pk = float(np.max(np.abs(y)))
        rms = 20 * np.log10(np.sqrt(np.mean(y ** 2)))
        seam = ""
        if count != len(x):
            problems.append(f"{name}: ogg holds {count} samples, expected {len(x)}")
        if loop:
            if len(y) != len(x):
                problems.append(f"{name}: loop decodes to {len(y)} samples, expected {len(x)}")
            r0, _ = seam_ratio(x)
            seam = f"{r0:4.2f} -> {r1:4.2f} (seam residual {lv:5.1f} dBFS)"
            if r1 > 6.0 and lv > -50.0:
                problems.append(f"{name}: click at loop seam (ratio {r1:.1f}, {lv:.1f} dBFS)")
        if pk >= 1.0:
            problems.append(f"{name}: clipping after encode (peak {pk:.3f})")
        print(f"{name:15s} {dur:6.3f} {pk:6.3f} {rms:7.1f}  {seam}")
        manifest[name] = {"subtitle": f"subtitles.{NS}.{name}",
                          "sounds": [{"name": f"{NS}:{name}", "stream": bool(loop and dur > 5.0)}]}
    (ASSETS / "sounds.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    if problems:
        raise SystemExit("\n".join(problems))
    print(f"wrote {len(manifest)} sounds and sounds.json under {ASSETS}")


if __name__ == "__main__":
    main()
