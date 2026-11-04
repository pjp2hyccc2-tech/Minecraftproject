"""Synthesises every sound of 殺人喜 (machines, gore, voices, UI jingles, BGM) and writes sounds.json.

Pure numpy/scipy DSP -> WAV -> ffmpeg/libvorbis OGG. Positional sounds are mono; the BGM is stereo.
"""
import json
import math
import os
import subprocess
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'satsujinki'))
SND = os.path.join(ROOT, 'sounds')
RNG = np.random.RandomState(20261104)


# ------------------------------------------------------------------------------------------------ DSP helpers

def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur, rng=RNG):
    return rng.randn(int(dur * SR))


def bp(x, lo, hi, order=2):
    lo = max(20.0, lo)
    hi = min(SR / 2 - 100, hi)
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype='band')
    return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, min(f, SR / 2 - 100) / (SR / 2), btype='low')
    return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, max(20.0, f) / (SR / 2), btype='high')
    return signal.lfilter(b, a, x)


def reson(x, f, bw):
    """Two-pole resonator (formant)."""
    r = math.exp(-math.pi * bw / SR)
    th = 2 * math.pi * f / SR
    b = [1 - r]
    a = [1, -2 * r * math.cos(th), r * r]
    return signal.lfilter(b, a, x)


def reson_tv(x, freqs, bw):
    """Time-varying formant: process in blocks of 256 samples."""
    out = np.zeros_like(x)
    zi = np.zeros(2)
    blk = 256
    for s in range(0, len(x), blk):
        f = float(freqs[min(s, len(freqs) - 1)])
        r = math.exp(-math.pi * bw / SR)
        th = 2 * math.pi * f / SR
        b = [1 - r]
        a = [1, -2 * r * math.cos(th), r * r]
        y, zi = signal.lfilter(b, a, x[s:s + blk], zi=zi)
        out[s:s + blk] = y
    return out


def osc(freq, kind='sine', phase0=0.0):
    ph = phase0 + np.cumsum(2 * np.pi * np.asarray(freq) / SR)
    if kind == 'sine':
        return np.sin(ph)
    if kind == 'saw':
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if kind == 'square':
        return np.sign(np.sin(ph))
    if kind == 'tri':
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1.0) - 1) - 1
    raise ValueError(kind)


def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def adsr(n, a, d, s, r):
    e = np.ones(n) * s
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = min(na, n)
    e[:na] = np.linspace(0, 1, na) if na > 0 else e[:na]
    if nd > 0 and na + nd < n:
        e[na:na + nd] = np.linspace(1, s, nd)
    if nr > 0:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def pad(x, dur):
    n = int(dur * SR)
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def at(buf, x, t0, gain=1.0):
    i = int(t0 * SR)
    if i >= len(buf):
        return
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i] * gain


def reverb(x, decay=0.8, mix=0.3, tone=4000, seed=1):
    n = int(decay * SR)
    r = np.random.RandomState(seed)
    ir = r.randn(n) * np.exp(-np.arange(n) / (decay * SR / 6.0))
    ir = lp(ir, tone)
    ir /= np.max(np.abs(ir)) + 1e-9
    wet = signal.fftconvolve(x, ir)
    total = len(x) + n
    wet = np.concatenate([wet, np.zeros(max(0, total - len(wet)))])[:total]
    dry = np.concatenate([x, np.zeros(n)])
    wet /= np.max(np.abs(wet)) + 1e-9
    wet *= np.max(np.abs(x)) + 1e-9
    return dry * (1 - mix) + wet * mix


def norm(x, peak=0.89):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def fade(x, fin=0.003, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def crossfade_loop(x, xf):
    """Make a seamless loop: blend the tail into the head."""
    n = int(xf * SR)
    head = x[:n].copy()
    tail = x[-n:]
    w = np.linspace(0, 1, n)
    head = head * w + tail * (1 - w)
    return np.concatenate([head, x[n:-n]])


def sat(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


WRITTEN = []


def write(rel, x, stereo=False, q=4):
    path = os.path.join(SND, rel + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if stereo:
        data = np.clip(x, -1, 1)
        pcm = (data * 32767).astype(np.int16)
    else:
        pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        wavfile.write(tf.name, SR, pcm)
        tmp = tf.name
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', str(q), path], check=True)
    os.unlink(tmp)
    WRITTEN.append(rel)


# ------------------------------------------------------------------------------------------------ gore

def splat(seed):
    r = np.random.RandomState(seed)
    dur = 0.45
    out = np.zeros(int(dur * SR))
    body = lp(r.randn(int(0.12 * SR)), 900 + r.rand() * 700) * env_exp(int(0.12 * SR), 0.03)
    slap = bp(r.randn(int(0.02 * SR)), 800, 5000) * env_exp(int(0.02 * SR), 0.004)
    at(out, sat(body * 3, 1.5), 0.0, 1.0)
    at(out, slap, 0.0, 0.8)
    for k in range(r.randint(4, 9)):
        f = 1800 + r.rand() * 2500
        n = int(0.015 * SR)
        tick = np.sin(2 * np.pi * np.cumsum(np.linspace(f, f * 1.6, n)) / SR) * env_exp(n, 0.004)
        at(out, tick, 0.04 + r.rand() * 0.3, 0.25 + r.rand() * 0.2)
    return fade(norm(out))


def crunch(seed):
    r = np.random.RandomState(seed)
    dur = 0.4
    out = np.zeros(int(dur * SR))
    for k in range(r.randint(9, 16)):
        n = int(0.012 * SR)
        click = bp(r.randn(n), 1200, 6000) * env_exp(n, 0.0025)
        at(out, click, r.rand() * 0.14, 0.5 + r.rand() * 0.6)
    thud = np.sin(2 * np.pi * np.cumsum(np.linspace(140, 60, int(0.15 * SR))) / SR) * env_exp(int(0.15 * SR), 0.04)
    at(out, thud, 0.0, 0.9)
    at(out, lp(r.randn(int(0.1 * SR)), 1200) * env_exp(int(0.1 * SR), 0.03), 0.01, 0.5)
    return fade(norm(out))


def squelch(seed):
    r = np.random.RandomState(seed)
    dur = 0.6
    n = int(dur * SR)
    x = r.randn(n)
    f = 300 + 900 * np.sin(np.linspace(0, np.pi, n)) ** 0.7 * (0.8 + 0.4 * r.rand())
    y = reson_tv(x, f, 120) * 0.5 + reson_tv(x, f * 2.2, 200) * 0.25
    am = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(14 + 20 * r.rand(n)) / SR)
    y *= am * adsr(n, 0.02, 0.1, 0.7, 0.25)
    return fade(norm(sat(y * 2, 1.2)))


def spray():
    dur = 1.3
    n = int(dur * SR)
    x = bp(noise(dur), 1800, 7000)
    flick = 0.5 + 0.5 * lp(np.abs(RNG.randn(n)), 12) * 6
    y = x * np.clip(flick, 0, 1.5) * adsr(n, 0.01, 0.2, 0.6, 0.6)
    return fade(norm(y))


def gib_land(seed):
    r = np.random.RandomState(seed)
    dur = 0.25
    out = np.zeros(int(dur * SR))
    n = int(0.12 * SR)
    at(out, np.sin(2 * np.pi * np.cumsum(np.linspace(160 + 60 * r.rand(), 70, n)) / SR) * env_exp(n, 0.03), 0, 0.9)
    at(out, lp(r.randn(n), 1400) * env_exp(n, 0.02), 0.0, 0.7)
    return fade(norm(out))


def metal_ring(dur, partials, seed, base_decay=0.6):
    r = np.random.RandomState(seed)
    n = int(dur * SR)
    tt = np.arange(n) / SR
    y = np.zeros(n)
    for i, f in enumerate(partials):
        y += np.sin(2 * np.pi * f * (1 + (r.rand() - 0.5) * 0.004) * tt + r.rand() * 6) * np.exp(-tt / (base_decay / (1 + i * 0.35))) / (1 + i * 0.4)
    return y


def impale(seed):
    dur = 0.6
    out = np.zeros(int(dur * SR))
    at(out, metal_ring(0.5, [930, 1710, 2890, 4100], seed, 0.25), 0, 0.5)
    at(out, crunch(seed + 5), 0.01, 0.8)
    at(out, splat(seed + 9), 0.03, 0.6)
    return fade(norm(out))


# ------------------------------------------------------------------------------------------------ voices

FORMANTS = {
    'a': [(800, 90), (1200, 110), (2600, 160), (3500, 220)],
    'i': [(300, 60), (2300, 120), (3000, 160), (3700, 220)],
    'u': [(350, 70), (900, 90), (2400, 150), (3400, 220)],
    'e': [(500, 70), (1800, 110), (2600, 160), (3600, 220)],
    'o': [(500, 80), (850, 90), (2500, 150), (3500, 220)],
}


def glottal(f0, rng, jitter=0.01, shimmer=0.1, rough=0.0):
    n = len(f0)
    jit = 1 + lp(rng.randn(n), 40) * jitter * 8
    f = f0 * jit
    ph = np.cumsum(2 * np.pi * f / SR)
    src = np.zeros(n)
    for h in range(1, 40):
        mask = (f * h) < (SR / 2 - 2000)
        src += np.sin(ph * h) * mask / h ** 1.05
    if rough > 0:
        src *= 1 + rough * np.sin(ph * 0.5)
    sh = 1 + lp(rng.randn(n), 30) * shimmer * 6
    return src * sh


def voice(f0, vowel_seq, female, rng, breath=0.15, rough=0.0, drive=1.5, jitter=0.01, bright=0.3):
    """Additive formant synthesis. f0: Hz array; vowel_seq: list of (time_fraction, vowel)."""
    n = len(f0)
    jit = 1 + lp(rng.randn(n), 30) * jitter * 6
    f = f0 * jit
    ph = np.cumsum(2 * np.pi * f / SR)
    fr = np.linspace(0, 1, n)
    scale = 1.17 if female else 1.0
    tracks, widths = [], []
    for k in range(4):
        tracks.append(np.interp(fr, [p[0] for p in vowel_seq], [FORMANTS[p[1]][k][0] * scale for p in vowel_seq]))
        widths.append(FORMANTS[vowel_seq[0][1]][k][1] * (2.2 if female else 1.8))
    gains = [1.0, 0.85, 0.5, 0.3]
    out = np.zeros(n)
    for h in range(1, 70):
        fh = f * h
        if np.min(fh) > SR / 2 - 1500:
            break
        amp = np.full(n, 0.035)
        for k in range(4):
            amp += gains[k] / (1.0 + ((fh - tracks[k]) / widths[k]) ** 2)
        amp *= (fh < SR / 2 - 1500) / (h ** bright)
        out += np.sin(ph * h + rng.rand() * 0.3) * amp
    if rough > 0:
        out *= 1 + rough * np.sin(ph * 0.5)
    sh = 1 + lp(rng.randn(n), 25) * 0.15
    out = out * sh
    out /= np.max(np.abs(out)) + 1e-9
    asp = rng.randn(n)
    asp = reson_tv(asp, tracks[1], 300) * 0.6 + reson_tv(asp, tracks[2], 400) * 0.4
    asp /= np.max(np.abs(asp)) + 1e-9
    out = out + asp * breath
    return sat(out, drive)


def scream(seed, female, dur=1.5, death=False):
    r = np.random.RandomState(seed)
    n = int(dur * SR)
    tt = np.linspace(0, 1, n)
    base = (520 if female else 300) * (0.9 + 0.2 * r.rand())
    peak = base * (1.45 + 0.2 * r.rand())
    f0 = base + (peak - base) * np.clip(tt * 6, 0, 1) - (peak - base) * 0.35 * np.clip((tt - 0.5) * 2, 0, 1)
    f0 *= 1 + 0.035 * np.sin(2 * np.pi * (5.5 + r.rand() * 2) * tt * dur)
    vowels = [(0.0, 'a'), (0.7, 'a'), (1.0, 'o' if r.rand() < 0.5 else 'a')]
    x = voice(f0, vowels, female, r, breath=0.12, rough=0.22, drive=2.6, jitter=0.025, bright=0.15)
    e = adsr(n, 0.04, 0.2, 0.85, 0.35)
    if death:
        cut = int(n * 0.55)
        g = np.ones(n)
        g[cut:] = np.linspace(1, 0.0, n - cut) ** 2
        gurgle = lp(r.randn(n), 400) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(22 + 15 * r.rand(n)) / SR)))
        gurgle *= np.clip((np.arange(n) - cut) / (0.1 * SR), 0, 1) * np.linspace(1, 0, n)
        x = x * g + gurgle * 0.45
    y = reverb(x * e, decay=0.5, mix=0.18, seed=seed)
    return fade(norm(y), 0.01, 0.08)


def panic(seed, female):
    r = np.random.RandomState(seed)
    dur = 0.55 + r.rand() * 0.2
    n = int(dur * SR)
    tt = np.linspace(0, 1, n)
    base = (420 if female else 230) * (0.9 + 0.2 * r.rand())
    f0 = base * (1 + 0.5 * np.exp(-tt * 3)) * (1 + 0.03 * np.sin(2 * np.pi * 7 * tt * dur))
    x = voice(f0, [(0.0, 'u'), (0.18, 'a'), (1.0, 'a')], female, r, breath=0.1, rough=0.1, drive=2.2, bright=0.2)
    y = x * adsr(n, 0.02, 0.1, 0.8, 0.2)
    return fade(norm(reverb(y, 0.4, 0.15, seed=seed)), 0.005, 0.05)


def hurt(seed, female):
    r = np.random.RandomState(seed)
    dur = 0.28
    n = int(dur * SR)
    tt = np.linspace(0, 1, n)
    base = (300 if female else 150) * (0.9 + 0.2 * r.rand())
    f0 = base * (1.25 - 0.35 * tt)
    x = voice(f0, [(0.0, 'u'), (1.0, 'u' if r.rand() < 0.5 else 'a')], female, r, breath=0.15, drive=2.5, bright=0.4)
    y = x * adsr(n, 0.005, 0.05, 0.6, 0.12)
    return fade(norm(y), 0.002, 0.03)


def beg(seed, female):
    r = np.random.RandomState(seed)
    dur = 1.7
    out = np.zeros(int(dur * SR))
    t0 = 0.0
    for k in range(3):
        d = 0.35 + r.rand() * 0.2
        n = int(d * SR)
        tt = np.linspace(0, 1, n)
        base = (330 if female else 175) * (0.95 + 0.1 * r.rand())
        f0 = base * (1.15 - 0.25 * tt) * (1 + 0.06 * np.sin(2 * np.pi * (8 + r.rand() * 3) * tt * d + r.rand() * 3))
        x = voice(f0, [(0.0, 'e'), (0.5, 'u'), (1.0, 'u')], female, r, breath=0.3, drive=1.4, jitter=0.035, bright=0.5)
        x *= adsr(n, 0.04, 0.1, 0.7, 0.2) * (0.9 - 0.2 * k)
        at(out, x, t0)
        t0 += d + 0.06
        # gasp
        gn = int(0.12 * SR)
        gasp = hp(r.randn(gn), 1500) * adsr(gn, 0.05, 0.02, 0.6, 0.05) * 0.25
        at(out, gasp, t0)
        t0 += 0.13
    return fade(norm(reverb(out, 0.5, 0.15, seed=seed)), 0.01, 0.06)


def fight(seed):
    r = np.random.RandomState(seed)
    dur = 0.65
    n = int(dur * SR)
    tt = np.linspace(0, 1, n)
    f0 = 170 + 110 * np.sin(np.pi * np.clip(tt * 1.3, 0, 1)) * (0.9 + 0.2 * r.rand())
    x = voice(f0, [(0.0, 'o'), (0.35, 'a'), (1.0, 'a')], False, r, breath=0.1, rough=0.35, drive=3.0, bright=0.25)
    y = x * adsr(n, 0.01, 0.1, 0.8, 0.2)
    return fade(norm(y), 0.003, 0.05)


# ------------------------------------------------------------------------------------------------ machines

def motor_layer(rot, n, rng, blade_count=4):
    """rot: rotation Hz array (len n). Motor hum + whine + blade whoosh + rattle."""
    hum = osc(rot * 7.0, 'saw') * 0.5 + osc(rot * 14.0, 'sine') * 0.3
    hum = lp(hum, 900)
    whine = osc(rot * 200.0 + 3 * np.sin(np.cumsum(2 * np.pi * 3.0 / SR * np.ones(n))), 'sine') * 0.12
    whoosh = bp(rng.randn(n), 700, 4500) * (0.55 + 0.45 * osc(rot * blade_count, 'sine'))
    rattle = np.zeros(n)
    k = 0
    while k < n:
        k += int(SR * (0.01 + rng.rand() * 0.04))
        if k < n:
            m = min(n - k, int(0.004 * SR))
            rattle[k:k + m] += bp(rng.randn(m), 2000, 6000) * env_exp(m, 0.001) * 0.6
    return hum * 0.9 + whine + whoosh * 0.45 + rattle * 0.3


def mixer_sounds():
    r = np.random.RandomState(11)
    # loop: rot 8 Hz exact -> 2.0 s holds whole cycles; crossfade noise parts
    dur = 2.3
    n = int(dur * SR)
    x = motor_layer(np.full(n, 8.0), n, r)
    x = crossfade_loop(x, 0.3)
    write('mixer/loop', fade(norm(x, 0.8), 0, 0))
    # start: spin up
    dur = 1.7
    n = int(dur * SR)
    rot = 8.0 * np.clip(np.linspace(0, 1.15, n), 0, 1) ** 1.6
    x = motor_layer(rot, n, r) * np.clip(np.linspace(0.2, 1.4, n), 0, 1)
    clunk = np.zeros(n)
    at(clunk, metal_ring(0.4, [120, 310, 580, 1100], 3, 0.15), 0, 0.8)
    at(clunk, lp(r.randn(int(0.05 * SR)), 2000) * env_exp(int(0.05 * SR), 0.01), 0, 0.6)
    write('mixer/start', fade(norm(x + clunk)))
    # stop
    dur = 1.6
    n = int(dur * SR)
    rot = 8.0 * np.clip(1 - np.linspace(0, 1.1, n), 0, 1) ** 1.3
    x = motor_layer(rot, n, r) * np.clip(1 - np.linspace(0, 1, n), 0, 1)
    at(x, metal_ring(0.5, [95, 260, 470], 4, 0.2), 1.25, 0.4)
    write('mixer/stop', fade(norm(x)))
    # grind variants
    for v in range(3):
        rr = np.random.RandomState(100 + v)
        dur = 1.25
        n = int(dur * SR)
        hits = 0.5 + 0.5 * osc(np.full(n, 31.0 + v * 3), 'square')
        wet = bp(rr.randn(n), 250, 2600) * hits
        wet = reson(wet, 600 + v * 150, 300) * 0.6 + wet * 0.5
        out = sat(wet * 1.6, 1.5) * adsr(n, 0.01, 0.2, 0.8, 0.3)
        out += motor_layer(np.full(n, 6.0 - v * 0.5), n, rr) * 0.35
        for k in range(5):
            at(out, crunch(200 + v * 10 + k), rr.rand() * 0.9, 0.6)
        for k in range(3):
            at(out, squelch(300 + v * 10 + k), rr.rand() * 0.8, 0.7)
        write(f'mixer/grind_{v + 1}', fade(norm(out)))


def press_sounds():
    r = np.random.RandomState(21)
    dur = 1.5
    n = int(dur * SR)
    hiss = hp(r.randn(n), 3000) * np.linspace(0.2, 1.0, n) ** 2 * 0.5
    pump = np.zeros(n)
    for k in range(5):
        m = int(0.12 * SR)
        thump = np.sin(2 * np.pi * np.cumsum(np.linspace(70, 40, m)) / SR) * env_exp(m, 0.04)
        at(pump, thump, 0.05 + k * 0.27, 0.9)
    creak = reson_tv(r.randn(n) * 0.2, 400 + 300 * np.sin(np.linspace(0, 7, n)), 25) * 0.6
    write('press/charge', fade(norm(hiss + pump + creak)))
    for v in range(2):
        rr = np.random.RandomState(30 + v)
        dur = 2.6
        out = np.zeros(int(dur * SR))
        m = int(0.9 * SR)
        boom = np.sin(2 * np.pi * np.cumsum(np.linspace(75 - v * 8, 28, m)) / SR) * env_exp(m, 0.25)
        at(out, boom, 0, 1.0)
        at(out, lp(rr.randn(int(0.2 * SR)), 1500) * env_exp(int(0.2 * SR), 0.04), 0, 0.9)
        at(out, metal_ring(2.0, [180, 427, 761, 1120, 1677, 2320, 3110], 40 + v, 1.2), 0.005, 0.55)
        at(out, crunch(50 + v), 0.01, 0.9)
        at(out, crunch(60 + v), 0.05, 0.6)
        at(out, splat(70 + v), 0.02, 0.8)
        at(out, splat(80 + v), 0.09, 0.5)
        y = reverb(sat(out * 1.3, 1.3), decay=1.4, mix=0.3, tone=3000, seed=v)
        write(f'press/slam_{v + 1}', fade(norm(y, 0.95), 0.001, 0.2))
    dur = 2.6
    n = int(dur * SR)
    whine = lp(osc(np.linspace(70, 140, n), 'saw'), 1200) * 0.4
    hiss = hp(r.randn(n), 2500) * 0.15 * adsr(n, 0.2, 0.3, 0.7, 0.6)
    drips = np.zeros(n)
    for k in range(7):
        m = int(0.05 * SR)
        f = 900 + r.rand() * 900
        drip = np.sin(2 * np.pi * np.cumsum(np.linspace(f, f * 1.8, m)) / SR) * env_exp(m, 0.012)
        at(drips, drip, 0.3 + r.rand() * 2.0, 0.35)
    write('press/retract', fade(norm(whine * adsr(n, 0.3, 0.2, 0.8, 0.6) + hiss + drips)))


def saw_sounds():
    r = np.random.RandomState(41)
    dur = 1.8
    n = int(dur * SR)
    base = 160.0
    tone = sum(osc(np.full(n, base * k), 'sine') * (0.6 / k) for k in (1, 2, 3, 5, 8, 13))
    teeth = osc(np.full(n, 2080.0), 'saw') * 0.25
    whine = osc(np.full(n, 3120.0), 'sine') * 0.1
    air = bp(r.randn(n), 3000, 9000) * 0.25
    x = crossfade_loop(sat(tone * 0.5 + teeth + whine + air, 1.4), 0.3)
    write('saw/loop', fade(norm(x, 0.8), 0, 0))
    for v in range(2):
        rr = np.random.RandomState(50 + v)
        dur = 1.1
        n = int(dur * SR)
        f = np.linspace(2080, 1300 - v * 150, n)
        bite = osc(f, 'saw') * (0.6 + 0.4 * osc(np.full(n, 37.0), 'square'))
        rough = bp(rr.randn(n), 500, 5000) * 0.6
        y = sat((bite * 0.6 + rough) * adsr(n, 0.01, 0.2, 0.9, 0.3), 2.2)
        for k in range(6):
            at(y, crunch(500 + v * 10 + k), 0.1 + rr.rand() * 0.8, 0.5)
        at(y, squelch(520 + v), 0.05, 0.8)
        at(y, spray() * 0.6, 0.1, 0.5)
        write(f'saw/cut_{v + 1}', fade(norm(y)))


def guillotine_sounds():
    r = np.random.RandomState(61)
    dur = 0.55
    n = int(dur * SR)
    sweep = np.linspace(500, 2600, n)
    y = reson_tv(r.randn(n), sweep, 500) * np.linspace(0.1, 1.0, n) ** 2
    zip_ = bp(r.randn(n), 3000, 8000) * np.linspace(0, 0.4, n)
    write('guillotine/drop', fade(norm(y + zip_), 0.01, 0.01))
    dur = 1.4
    out = np.zeros(int(dur * SR))
    m = int(0.25 * SR)
    at(out, np.sin(2 * np.pi * np.cumsum(np.linspace(110, 55, m)) / SR) * env_exp(m, 0.07), 0, 1.0)
    at(out, lp(r.randn(m), 2200) * env_exp(m, 0.03), 0, 0.9)
    at(out, metal_ring(1.0, [520, 1240, 2210, 3480], 62, 0.4), 0.0, 0.35)
    at(out, crunch(63), 0.0, 0.9)
    at(out, splat(64), 0.02, 0.7)
    at(out, spray() * 0.5, 0.06, 0.6)
    write('guillotine/chop', fade(norm(reverb(out, 0.9, 0.25, seed=6), 0.95), 0.001, 0.15))
    dur = 0.45
    out = np.zeros(int(dur * SR))
    for k, t0 in enumerate((0.0, 0.11)):
        m = int(0.06 * SR)
        at(out, bp(r.randn(m), 700, 3200) * env_exp(m, 0.008), t0, 1.0 - k * 0.2)
        at(out, np.sin(2 * np.pi * 240 * np.arange(m) / SR) * env_exp(m, 0.015), t0, 0.5)
    at(out, metal_ring(0.2, [2600, 4100], 65, 0.05), 0.2, 0.3)
    write('guillotine/lock', fade(norm(out)))


# ------------------------------------------------------------------------------------------------ misc / ui

def bell(freq, dur, decay=0.6, partials=((1, 1.0), (2.76, 0.35), (5.4, 0.2), (8.93, 0.08))):
    n = int(dur * SR)
    tt = np.arange(n) / SR
    y = np.zeros(n)
    for mul, g in partials:
        y += np.sin(2 * np.pi * freq * mul * tt) * g * np.exp(-tt / (decay / (mul ** 0.5)))
    return y * adsr(n, 0.002, 0.0, 1.0, 0.02)


def note(name):
    names = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}
    p, o = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[p] + (o - 4) * 12) / 12)


def taiko(seed, gain=1.0):
    r = np.random.RandomState(seed)
    m = int(0.8 * SR)
    y = np.sin(2 * np.pi * np.cumsum(np.linspace(95, 48, m)) / SR) * env_exp(m, 0.18)
    y += lp(r.randn(m), 600) * env_exp(m, 0.03) * 0.6
    return sat(y * gain, 1.2)


def misc_sounds():
    r = np.random.RandomState(71)
    # spawn: smoke puff + chains
    out = np.zeros(int(0.9 * SR))
    m = int(0.5 * SR)
    at(out, lp(r.randn(m), 1200) * adsr(m, 0.05, 0.1, 0.5, 0.3), 0, 0.8)
    for k in range(10):
        at(out, metal_ring(0.08, [2400 + r.rand() * 1500, 5200], 80 + k, 0.03), 0.1 + r.rand() * 0.6, 0.35)
    write('misc/spawn', fade(norm(out)))
    # grab
    out = np.zeros(int(0.35 * SR))
    m = int(0.1 * SR)
    at(out, bp(r.randn(m), 900, 4000) * env_exp(m, 0.02), 0, 0.8)
    at(out, np.sin(2 * np.pi * np.cumsum(np.linspace(120, 60, m)) / SR) * env_exp(m, 0.04), 0, 0.7)
    write('host/grab', fade(norm(out)))
    # throw: whoosh
    n = int(0.6 * SR)
    y = reson_tv(r.randn(n), np.linspace(300, 1600, n), 350) * np.sin(np.linspace(0, np.pi, n)) ** 1.5
    write('host/throw', fade(norm(y)))
    # remote double beep
    out = np.zeros(int(0.3 * SR))
    for f, t0, d in ((1760, 0.0, 0.06), (2093, 0.09, 0.09)):
        m = int(d * SR)
        at(out, sat(osc(np.full(m, float(f)), 'square') * 0.4, 1.0) * adsr(m, 0.002, 0.0, 1.0, 0.01), t0, 0.6)
    write('host/remote', fade(lp(norm(out), 6000)))
    # mop
    out = np.zeros(int(1.1 * SR))
    for k in range(3):
        m = int(0.25 * SR)
        at(out, bp(r.randn(m), 900, 3200) * np.sin(np.linspace(0, np.pi, m)) ** 2, 0.05 + k * 0.32, 0.8)
        at(out, squelch(90 + k) * 0.4, 0.1 + k * 0.32, 0.5)
    write('misc/mop', fade(norm(out)))
    # spray, splats, crunches, squelches, gib landings, impale
    write('gore/spray', spray())
    for v in range(4):
        write(f'gore/splat_{v + 1}', splat(700 + v))
    for v in range(3):
        write(f'gore/crunch_{v + 1}', crunch(710 + v))
        write(f'gore/squelch_{v + 1}', squelch(720 + v))
        write(f'gore/gib_{v + 1}', gib_land(730 + v))
    for v in range(2):
        write(f'gore/impale_{v + 1}', impale(740 + v))


def ui_sounds():
    r = np.random.RandomState(81)
    # kill: taiko + bell
    out = np.zeros(int(1.0 * SR))
    at(out, taiko(1), 0, 1.0)
    at(out, bell(note('C6'), 0.8, 0.4), 0.0, 0.35)
    at(out, bell(note('G6'), 0.6, 0.3), 0.0, 0.2)
    write('ui/kill', fade(norm(reverb(out, 0.6, 0.15, seed=2))))
    # combo arpeggio
    out = np.zeros(int(0.5 * SR))
    for i, nm in enumerate(('C6', 'E6', 'G6', 'C7')):
        at(out, bell(note(nm), 0.3, 0.15), i * 0.05, 0.6)
    write('ui/combo', fade(norm(out)))
    # rank up: shimmer chord + sweep + hit
    dur = 1.5
    n = int(dur * SR)
    out = np.zeros(n)
    for nm in ('C5', 'E5', 'G5', 'C6', 'E6'):
        f = note(nm)
        out += osc(np.full(n, f), 'sine') * (0.5 + 0.5 * osc(np.full(n, 9.0), 'sine')) * env_exp(n, 0.5) * 0.25
    out += reson_tv(r.randn(n), np.linspace(400, 6000, n), 600) * env_exp(n, 0.4) * 0.3
    at(out, taiko(2, 0.8), 0, 0.8)
    write('ui/rankup', fade(norm(reverb(out, 0.9, 0.25, seed=3))))
    # announce: ピンポンパンポーン
    out = np.zeros(int(2.4 * SR))
    for i, nm in enumerate(('F5', 'A5', 'C6', 'F6')):
        at(out, bell(note(nm), 1.2, 0.9, ((1, 1.0), (2.0, 0.25), (3.0, 0.1), (4.2, 0.05))), i * 0.38, 0.7)
    write('ui/announce', fade(norm(reverb(out, 1.2, 0.3, tone=5000, seed=4))))
    # countdown beep + tick
    out = np.zeros(int(0.5 * SR))
    m = int(0.16 * SR)
    at(out, osc(np.full(m, 880.0), 'sine') * adsr(m, 0.003, 0.0, 1.0, 0.03), 0, 0.7)
    at(out, bp(r.randn(int(0.02 * SR)), 2000, 6000) * env_exp(int(0.02 * SR), 0.003), 0, 0.5)
    write('ui/countdown', fade(norm(out)))
    # start: buzzer + gong
    dur = 2.4
    out = np.zeros(int(dur * SR))
    m = int(0.85 * SR)
    buzz = sat(osc(np.full(m, 220.0), 'square') + osc(np.full(m, 233.0), 'square'), 1.5) * adsr(m, 0.01, 0.0, 1.0, 0.05)
    at(out, lp(buzz, 3000) * 0.5, 0, 1.0)
    at(out, metal_ring(2.0, [97, 147, 232, 318, 461, 652], 90, 1.4), 0.7, 0.8)
    at(out, taiko(3), 0.7, 0.8)
    write('ui/start', fade(norm(reverb(out, 1.2, 0.2, seed=5))))
    # game over
    out = np.zeros(int(3.0 * SR))
    for i, nm in enumerate(('F6', 'C6', 'A5', 'F5')):
        at(out, bell(note(nm), 1.4, 1.0, ((1, 1.0), (2.0, 0.25), (3.0, 0.1))), i * 0.45, 0.6)
    at(out, metal_ring(1.5, [82, 131, 205, 297], 91, 1.2), 1.8, 0.6)
    write('ui/gameover', fade(norm(reverb(out, 1.4, 0.3, seed=6))))
    # buy: ka-ching
    out = np.zeros(int(0.8 * SR))
    at(out, metal_ring(0.6, [2637, 3520, 4186, 5274], 92, 0.3), 0.0, 0.5)
    at(out, metal_ring(0.6, [3136, 4186, 5587], 93, 0.35), 0.08, 0.5)
    for k in range(6):
        at(out, metal_ring(0.1, [4000 + r.rand() * 2000], 94 + k, 0.04), 0.15 + r.rand() * 0.3, 0.25)
    write('ui/buy', fade(norm(out)))
    # r18: ominous hit
    dur = 3.0
    n = int(dur * SR)
    out = np.zeros(n)
    sub = osc(np.full(n, 41.0), 'sine') * env_exp(n, 1.0)
    grit = lp(sat(osc(np.full(n, 55.0), 'saw') * 2.5, 2.0), 700) * env_exp(n, 0.8) * 0.5
    cluster = sum(lp(osc(np.full(n, f), 'saw'), 1500) for f in (220.0, 233.1, 277.2, 311.1)) * 0.12 * adsr(n, 0.4, 0.5, 0.6, 1.2)
    swell = reson_tv(r.randn(n), np.linspace(200, 2000, n), 300) * np.linspace(0, 1, n) ** 3 * 0.2
    out = sub + grit + cluster + swell
    at(out, taiko(4, 1.2), 0, 0.9)
    write('ui/r18', fade(norm(reverb(out, 1.6, 0.3, tone=2500, seed=7))))


def voices():
    for v in range(3):
        write(f'voice/scream_m_{v + 1}', scream(1000 + v, False, 1.3 + v * 0.15))
        write(f'voice/scream_f_{v + 1}', scream(1100 + v, True, 1.3 + v * 0.15))
        write(f'voice/panic_m_{v + 1}', panic(1200 + v, False))
        write(f'voice/panic_f_{v + 1}', panic(1300 + v, True))
        write(f'voice/hurt_m_{v + 1}', hurt(1400 + v, False))
        write(f'voice/hurt_f_{v + 1}', hurt(1500 + v, True))
    for v in range(2):
        write(f'voice/beg_m_{v + 1}', beg(1600 + v, False))
        write(f'voice/beg_f_{v + 1}', beg(1700 + v, True))
        write(f'voice/death_m_{v + 1}', scream(1800 + v, False, 1.2, death=True))
        write(f'voice/death_f_{v + 1}', scream(1900 + v, True, 1.2, death=True))
        write(f'voice/fight_{v + 1}', fight(2000 + v))


# ------------------------------------------------------------------------------------------------ BGM

def music():
    """Creepy music-box waltz in A minor over a drone, heartbeat and clock ticks. 3/4 at 84 BPM, 24 bars."""
    bpm = 84.0
    beat = 60.0 / bpm
    bars = 24
    dur = bars * 3 * beat
    n = int(dur * SR)
    L = np.zeros(n)
    R = np.zeros(n)
    rng = np.random.RandomState(5)

    def put(sig, t0, pan=0.0, gain=1.0):
        i = int(t0 * SR) % n
        seg = sig * gain
        end = i + len(seg)
        gl, gr = math.cos((pan + 1) * math.pi / 4), math.sin((pan + 1) * math.pi / 4)
        if end <= n:
            L[i:end] += seg * gl
            R[i:end] += seg * gr
        else:  # wrap around for a seamless loop
            k = n - i
            L[i:] += seg[:k] * gl
            R[i:] += seg[:k] * gr
            L[:end - n] += seg[k:] * gl
            R[:end - n] += seg[k:] * gr

    # chord progression (per bar): Am Am Dm E | F Dm E E7 ... repeated with variation
    prog = ['Am', 'Am', 'Dm', 'E', 'F', 'Dm', 'E', 'E', 'Am', 'G#dim', 'Am', 'E',
            'F', 'F', 'Dm', 'E', 'Am', 'Am', 'Dm', 'Bdim', 'F', 'E', 'Am', 'E']
    chords = {'Am': ['A2', 'E3', 'A3', 'C4'], 'Dm': ['D3', 'A3', 'D4', 'F4'], 'E': ['E2', 'B2', 'E3', 'G#3'],
              'F': ['F2', 'C3', 'F3', 'A3'], 'G#dim': ['G#2', 'D3', 'G#3', 'B3'], 'Bdim': ['B2', 'D3', 'F3', 'B3']}
    # drone pad
    for b, ch in enumerate(prog):
        t0 = b * 3 * beat
        m = int(3 * beat * SR) + int(0.6 * SR)
        pad = np.zeros(m)
        for nm in chords[ch]:
            f = note(nm)
            for det in (-0.12, 0.0, 0.12):
                pad += osc(np.full(m, f * 2 ** (det / 12)), 'saw') * 0.05
        pad = lp(pad, 900) * adsr(m, 0.5, 0.3, 0.8, 0.6)
        put(pad, t0, pan=-0.2 if b % 2 else 0.2, gain=0.9)
        # sub bass on the downbeat
        bm = int(1.4 * SR)
        f = note(chords[ch][0])
        put(osc(np.full(bm, f), 'sine') * env_exp(bm, 0.6) * 0.8, t0, 0.0, 0.8)
    # music box melody (A harmonic minor), mostly quarter notes, some eighths
    mel = ['E5', '-', 'A5', 'C6', 'B5', 'A5', 'G#5', '-', 'B5', 'E5', '-', '-',
           'F5', '-', 'A5', 'D6', 'C6', 'A5', 'B5', 'G#5', 'E5', 'E5', '-', '-',
           'A5', 'C6', 'E6', 'D6', 'C6', 'B5', 'C6', 'A5', 'F5', 'E5', '-', 'D#5',
           'E5', '-', 'B4', 'C5', 'D5', 'E5', 'F5', 'E5', 'D5', 'E5', '-', '-']
    for k in range(bars * 3):
        nm = mel[k % len(mel)]
        if nm == '-':
            continue
        t0 = k * beat
        f = note(nm) * (2 if (k // len(mel)) % 2 == 1 else 1)
        sig = bell(f, 1.6, 0.7, ((1, 1.0), (3.0, 0.3), (5.04, 0.12), (7.1, 0.05)))
        # music box is slightly out of tune and wobbly
        put(sig, t0 + rng.rand() * 0.012, pan=0.35 * math.sin(k * 0.7), gain=0.55)
        if k % 6 == 5:
            put(bell(f * 0.5, 1.2, 0.5), t0 + beat * 0.5, pan=-0.4, gain=0.25)
    # heartbeat every bar, getting louder in the second half
    for b in range(bars):
        t0 = b * 3 * beat
        g = 0.55 + 0.45 * (b / bars)
        for dt, amp in ((0.0, 1.0), (0.22, 0.7)):
            m = int(0.25 * SR)
            hb = np.sin(2 * np.pi * np.cumsum(np.linspace(65, 40, m)) / SR) * env_exp(m, 0.06)
            put(hb, t0 + dt, 0.0, 0.9 * amp * g)
    # clock ticks on each beat, metal scrapes now and then
    for k in range(bars * 3):
        m = int(0.03 * SR)
        tick = bp(rng.randn(m), 3000, 9000) * env_exp(m, 0.004)
        put(tick, k * beat, pan=0.6 if k % 2 else -0.6, gain=0.18)
        if k % 24 == 17:
            sm = int(1.2 * SR)
            scrape = reson_tv(rng.randn(sm) * 0.3, np.linspace(1800, 900, sm), 40) * adsr(sm, 0.2, 0.3, 0.5, 0.5)
            put(scrape, k * beat, pan=-0.7, gain=0.25)
    st = np.stack([L, R], axis=1)
    # light stereo reverb
    for ch in range(2):
        st[:, ch] = reverb(st[:, ch], 1.4, 0.25, tone=3500, seed=10 + ch)[:n] if True else st[:, ch]
    st /= np.max(np.abs(st)) + 1e-9
    st *= 0.85
    write('music/deathgame', st, stereo=True, q=3)


# ------------------------------------------------------------------------------------------------ sounds.json

def sounds_json():
    def ev(files, subtitle=None, stream=False, attenuation=None):
        d = {'sounds': []}
        for f in files:
            e = {'name': 'satsujinki:' + f}
            if stream:
                e['stream'] = True
            if attenuation:
                e['attenuation_distance'] = attenuation
            d['sounds'].append(e)
        if subtitle:
            d['subtitle'] = 'subtitles.satsujinki.' + subtitle
        return d

    def many(prefix, count):
        return [f'{prefix}_{i + 1}' for i in range(count)]

    j = {
        'mixer.start': ev(['mixer/start'], 'mixer_start', attenuation=24),
        'mixer.loop': ev(['mixer/loop'], 'mixer_loop', attenuation=24),
        'mixer.grind': ev(many('mixer/grind', 3), 'mixer_grind', attenuation=28),
        'mixer.stop': ev(['mixer/stop'], 'mixer_stop', attenuation=24),
        'press.charge': ev(['press/charge'], 'press_charge', attenuation=24),
        'press.slam': ev(many('press/slam', 2), 'press_slam', attenuation=40),
        'press.retract': ev(['press/retract'], 'press_retract', attenuation=20),
        'saw.loop': ev(['saw/loop'], 'saw_loop', attenuation=24),
        'saw.cut': ev(many('saw/cut', 2), 'saw_cut', attenuation=28),
        'guillotine.drop': ev(['guillotine/drop'], 'guillotine_drop', attenuation=20),
        'guillotine.chop': ev(['guillotine/chop'], 'guillotine_chop', attenuation=32),
        'guillotine.lock': ev(['guillotine/lock'], 'guillotine_lock', attenuation=16),
        'gore.splat': ev(many('gore/splat', 4), 'gore_splat'),
        'gore.crunch': ev(many('gore/crunch', 3), 'gore_crunch'),
        'gore.squelch': ev(many('gore/squelch', 3), 'gore_squelch'),
        'gore.spray': ev(['gore/spray'], 'gore_spray'),
        'gore.gib_land': ev(many('gore/gib', 3), 'gore_gib'),
        'gore.impale': ev(many('gore/impale', 2), 'gore_impale'),
        'npc.scream_male': ev(many('voice/scream_m', 3), 'scream', attenuation=32),
        'npc.scream_female': ev(many('voice/scream_f', 3), 'scream', attenuation=32),
        'npc.panic_male': ev(many('voice/panic_m', 3), 'panic', attenuation=24),
        'npc.panic_female': ev(many('voice/panic_f', 3), 'panic', attenuation=24),
        'npc.beg_male': ev(many('voice/beg_m', 2), 'beg'),
        'npc.beg_female': ev(many('voice/beg_f', 2), 'beg'),
        'npc.hurt_male': ev(many('voice/hurt_m', 3), 'hurt'),
        'npc.hurt_female': ev(many('voice/hurt_f', 3), 'hurt'),
        'npc.death_male': ev(many('voice/death_m', 2), 'death', attenuation=32),
        'npc.death_female': ev(many('voice/death_f', 2), 'death', attenuation=32),
        'npc.fight': ev(many('voice/fight', 2), 'fight'),
        'npc.spawn': ev(['misc/spawn'], 'spawn'),
        'host.grab': ev(['host/grab'], 'grab'),
        'host.throw': ev(['host/throw'], 'throw'),
        'host.remote': ev(['host/remote'], 'remote'),
        'ui.kill': ev(['ui/kill']),
        'ui.combo': ev(['ui/combo']),
        'ui.rankup': ev(['ui/rankup']),
        'ui.announce': ev(['ui/announce'], 'announce'),
        'ui.countdown': ev(['ui/countdown']),
        'ui.start': ev(['ui/start'], 'start'),
        'ui.gameover': ev(['ui/gameover'], 'gameover'),
        'ui.buy': ev(['ui/buy']),
        'ui.r18': ev(['ui/r18']),
        'mop.clean': ev(['misc/mop'], 'mop'),
        'music.deathgame': ev(['music/deathgame'], stream=True),
    }
    with open(os.path.join(ROOT, 'sounds.json'), 'w', encoding='utf-8') as f:
        json.dump(j, f, indent=2)
    names = set()
    for v in j.values():
        for s in v['sounds']:
            names.add(s['name'].split(':')[1])
    missing = [nm for nm in names if not os.path.exists(os.path.join(SND, nm + '.ogg'))]
    if missing:
        raise SystemExit('missing sound files: ' + ', '.join(sorted(missing)))
    return sorted({v.get('subtitle') for v in j.values() if v.get('subtitle')})


def main():
    import sys
    which = sys.argv[1:] or ['gore', 'machines', 'voices', 'ui', 'music']
    if 'machines' in which:
        mixer_sounds()
        press_sounds()
        saw_sounds()
        guillotine_sounds()
    if 'gore' in which:
        misc_sounds()
    if 'voices' in which:
        voices()
    if 'ui' in which:
        ui_sounds()
    if 'music' in which:
        music()
    subs = sounds_json()
    print('sounds done:', len(WRITTEN), 'files written; subtitles:', ', '.join(subs))


if __name__ == '__main__':
    main()
