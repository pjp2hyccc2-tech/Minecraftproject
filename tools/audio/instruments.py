"""Instrument / physical-ish sound sources for MEIKON."""
import numpy as np
from scipy import signal

import dsp
from dsp import SR, TAU, ns

# Miyako-bushi (In) scale on D, and the warabe-uta scale for kagome
IN_SCALE = ["D", "Eb", "G", "A", "Bb"]

KAGOME = [
    [("A", 2), ("A", 1), ("B", 1)], [("A", 2), ("A", 1), ("B", 1)],
    [("A", 1), ("A", 1), ("B", 1), ("B", 1), ("A", 1), ("A", 1)],
    [("G", 1), ("G", 1), ("A", 2)], [("A", 1), ("G", 1), ("A", 1), ("G", 1)], [("A", 1), ("G", 1), ("E", 2)],
    [("G", 1), ("G", 1), ("A", 1), ("A", 1)], [("A", 1), ("G", 1), ("E", 2)],
    [("A", 1), ("A", 1), ("A", 1), ("B", 1), ("A", 1), ("A", 1)], [("A", 1), ("G", 1), ("A", 2)],
    [("A", 1), ("A", 1), ("A", 1), ("A", 1), ("A", 1), ("B", 1), ("A", 1), ("G", 1)], [("A", 1), ("A", 3)],
]


def in_note(degree, octave_base=3, root="D"):
    """Degree in the In scale (can be negative / > 4) -> note name."""
    o, d = divmod(degree, 5)
    name = IN_SCALE[d]
    octv = octave_base + o
    # notes below D in the same 'octave' wrap: scale is D-based, so A, Bb are above D
    return f"{name}{octv}"


# --------------------------------------------------------------------------
# Karplus-Strong plucked string (koto / shamisen)
# --------------------------------------------------------------------------
def ks_string(freq, dur, rng, t60=3.0, bright=0.6, pluck_pos=0.2, exc_soft=0.3, exc=None):
    """Plucked string: Karplus-Strong with a tunable 2-tap loss filter and a
    first-order all-pass for exact fractional tuning, evaluated by lfilter."""
    n = ns(dur)
    P = SR / freq
    b = 0.5 - 0.42 * float(np.clip(bright, 0.0, 1.0))  # loop LP: (1-b) + b z^-1
    N = int(np.floor(P - b - 0.5))
    d = P - b - N
    c = (1.0 - d) / (1.0 + d)
    g = 10.0 ** (-3.0 / (freq * t60))
    a = np.zeros(N + 3)
    a[0] = 1.0
    a[1] = c
    a[N] -= g * (1 - b) * c
    a[N + 1] -= g * ((1 - b) + b * c)
    a[N + 2] -= g * b
    if exc is None:
        L = max(4, int(P))
        e = rng.uniform(-1, 1, L)
        e -= e.mean()
        if exc_soft > 0:
            e = signal.lfilter([1 - exc_soft], [1, -exc_soft], e)
        k = max(1, int(pluck_pos * P))
        e = e - np.concatenate([np.zeros(k), e[:-k]])
        exc = e
    x = np.zeros(n)
    x[: min(n, len(exc))] = exc[:n]
    y = signal.lfilter([1.0, c], a, x)
    return y


def koto(freq, dur, rng, vel=1.0, t60=None, bright=0.55, bend=None, detune_c=0.0, body=0.25, tsume=0.6):
    """Koto note: two slightly detuned KS strings (main + sympathetic), plectrum
    click, paulownia body resonances, optional pitch-bend curve in cents
    (callable t->cents or array of (t, cents) points)."""
    f = freq * dsp.cents(detune_c)
    t60 = t60 if t60 is not None else float(np.clip(4.2 * (220.0 / f) ** 0.35, 1.6, 5.5))
    s1 = ks_string(f, dur, rng, t60=t60, bright=bright * (0.85 + 0.3 * vel), pluck_pos=rng.uniform(0.12, 0.22))
    s2 = ks_string(f * dsp.cents(rng.uniform(3, 7)), dur, rng, t60=t60 * 0.8, bright=bright * 0.6,
                   pluck_pos=rng.uniform(0.2, 0.35))
    y = s1 + 0.22 * s2
    y /= np.max(np.abs(y)) + 1e-9
    n = len(y)
    # plectrum (tsume) click
    L = ns(0.006)
    click = rng.standard_normal(L) * np.exp(-np.arange(L) / (0.0012 * SR))
    click = dsp.highpass(click, 1800, 2)
    y[:L] += click * tsume * 0.35 * vel
    # body
    bodyr = dsp.resbank(y, [(190, 3, 1.0), (390, 4, 0.8), (820, 5, 0.5), (1650, 6, 0.3)])
    y = y + body * bodyr
    if bend is not None:
        tt = np.arange(n) / SR
        if callable(bend):
            cc = bend(tt)
        else:
            bt = np.array([p[0] for p in bend])
            bc = np.array([p[1] for p in bend])
            cc = np.interp(tt, bt, bc)
        y = dsp.varispeed(y, dsp.cents(cc))
        y = dsp.pad_to(y, n)
    y = dsp.fade(y, 0.0005, 0.08)
    return y * vel


# --------------------------------------------------------------------------
# music box
# --------------------------------------------------------------------------
def tine(freq, rng, dur=None, vel=1.0):
    """Music-box comb tine: strong fundamental, weak 2nd harmonic, inharmonic
    cantilever partials that die fast, a tiny pin click."""
    T1 = float(np.clip(2.6 * (880.0 / freq) ** 0.7, 0.8, 4.5))
    dur = dur or T1 * 1.1
    n = ns(dur)
    t = np.arange(n) / SR
    beat = rng.uniform(0.3, 1.4)
    y = np.exp(-6.9 * t / T1) * (np.sin(TAU * freq * t) + 0.12 * np.sin(TAU * (freq + beat) * t + 1.0))
    y = dsp.fade(y, 0.0, 0.02)
    y += 0.10 * np.exp(-6.9 * t / (T1 * 0.4)) * np.sin(TAU * 2 * freq * t + 0.3)
    r2 = 5.93 * (1 + rng.uniform(-0.02, 0.02))
    r3 = 16.8 * (1 + rng.uniform(-0.03, 0.03))
    if freq * r2 < SR * 0.45:
        y += 0.30 * np.exp(-6.9 * t / 0.35) * np.sin(TAU * freq * r2 * t + rng.uniform(0, 6))
    if freq * r3 < SR * 0.45:
        y += 0.10 * np.exp(-6.9 * t / 0.09) * np.sin(TAU * freq * r3 * t + rng.uniform(0, 6))
    L = ns(0.004)
    click = rng.standard_normal(L) * np.exp(-np.arange(L) / (0.0006 * SR))
    y[:L] += dsp.highpass(click, 3000, 2) * 0.25
    att = np.minimum(1.0, t / 0.0015)
    return y * att * vel


# --------------------------------------------------------------------------
# bells
# --------------------------------------------------------------------------
def suzu_strike(rng, base, vel=1.0, dur=1.4, shimmer=1.0):
    """One hit of a small suzu bell (pellet bell): cluster of high inharmonic
    partials, each a slightly split pair -> shimmering beats."""
    n = ns(dur)
    t = np.arange(n) / SR
    ratios = np.array([1.0, 1.47, 2.09, 2.76, 3.52]) * (1 + rng.uniform(-0.02, 0.02, 5))
    amps = np.array([1.0, 0.7, 0.5, 0.32, 0.2]) * rng.uniform(0.6, 1.0, 5)
    t60s = np.array([1.1, 0.75, 0.55, 0.38, 0.25]) * np.sqrt(4000.0 / base)
    y = np.zeros(n)
    for r, a, T in zip(ratios, amps, t60s):
        f = base * r
        if f > SR * 0.46:
            continue
        df = rng.uniform(2.0, 9.0) * shimmer
        ph = rng.uniform(0, TAU, 2)
        y += a * np.exp(-6.9 * t / T) * (np.sin(TAU * f * t + ph[0]) + 0.6 * np.sin(TAU * (f + df) * t + ph[1]))
    L = ns(0.002)
    click = dsp.highpass(rng.standard_normal(L), 2500, 2) * np.exp(-np.arange(L) / (0.0004 * SR))
    y[:L] += click * 0.6
    y *= np.minimum(1.0, t / 0.0008)
    return dsp.fade(y, 0.0, min(0.05, dur * 0.2)) * vel


def suzu_jingle(rng, dur, shakes, bases=(3300, 4200, 5300), n_hits=(4, 9), spread=0.28):
    """Jingle of a cluster of suzu: shakes = [(t, intensity)].  Each shake is a
    burst of pellet hits with random micro-timing spread over the bells."""
    n = ns(dur)
    y = np.zeros(n)
    for t0, inten in shakes:
        k = rng.integers(n_hits[0], n_hits[1] + 1)
        tt = t0
        for i in range(k):
            b = bases[rng.integers(len(bases))] * (1 + rng.uniform(-0.01, 0.01))
            v = inten * (0.35 + 0.65 * rng.random()) * (1.0 - 0.6 * i / k)
            s = suzu_strike(rng, b, v, dur=min(1.4, dur - tt + 0.01) if dur - tt > 0.05 else 0.05)
            dsp.place(y, s, tt)
            tt += rng.uniform(0.008, 0.045) * (spread / 0.28)
    return y


def bell_partials(f0, dur, rng, ratios, amps, t60s, beat=(0.3, 2.0), attack=0.004):
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for r, a, T in zip(ratios, amps, t60s):
        f = f0 * r
        if f > SR * 0.45:
            continue
        df = rng.uniform(*beat)
        ph = rng.uniform(0, TAU, 2)
        e = np.exp(-6.9 * t / T)
        y += a * e * (np.sin(TAU * f * t + ph[0]) + 0.7 * np.sin(TAU * (f + df) * t + ph[1]))
    y *= 1 - np.exp(-t / attack)
    return dsp.fade(y, 0.0, min(0.3, dur * 0.1))


def bonsho(f0, dur, rng):
    """Japanese temple bell struck with a wooden log."""
    ratios = [0.5, 1.0, 1.31, 1.73, 2.21, 2.72, 3.36, 4.11, 4.92, 5.83, 7.0]
    amps = [0.45, 1.0, 0.45, 0.65, 0.42, 0.32, 0.26, 0.18, 0.12, 0.08, 0.05]
    t60s = [34, 24, 15, 13, 10, 7.5, 5.5, 3.8, 2.6, 1.9, 1.3]
    y = bell_partials(f0, dur, rng, ratios, amps, t60s, beat=(0.35, 1.6), attack=0.012)
    n = len(y)
    L = ns(0.06)
    thud = dsp.lowpass(rng.standard_normal(L), 400, 2) * np.exp(-np.arange(L) / (0.012 * SR))
    y[:L] += thud * 1.2
    thump = np.sin(TAU * 55 * np.arange(ns(0.3)) / SR) * np.exp(-np.arange(ns(0.3)) / (0.06 * SR))
    y[: len(thump)] += thump * 0.6
    return y


# --------------------------------------------------------------------------
# strings (detuned saw stacks)
# --------------------------------------------------------------------------
def strings(freqs, dur, rng, env=None, voices=3, detune=14.0, vib_depth=10.0, vib_rate=5.4, trem=0.0,
            trem_rate=14.0, bright=0.5, pont=0.0, gliss=None, stereo=True, width=0.8):
    """String section cluster.  freqs: list of Hz.  env: per-sample amplitude.
    trem: 0..1 bowed tremolo depth.  pont: 0..1 sul ponticello (glassy/noisy).
    gliss: optional per-sample cents offset applied to all voices."""
    n = ns(dur)
    env = np.ones(n) if env is None else dsp.pad_to(np.asarray(env, float), n)
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(voices):
            dc = rng.normal(0.0, detune / 2.0)
            vib = vib_depth * np.sin(TAU * (vib_rate * (1 + 0.1 * rng.standard_normal())) * np.arange(n) / SR + rng.uniform(0, TAU))
            vib += 4.0 * dsp.smooth(n, rng, 2.0)
            c = dc + vib + (gliss if gliss is not None else 0.0)
            fv = f * dsp.cents(c)
            s = dsp.saw(fv, n, phase0=rng.random())
            if trem > 0:
                rate = trem_rate * (1 + 0.08 * dsp.smooth(n, rng, 1.0))
                ph = np.cumsum(rate) / SR + rng.random()
                bow = 0.5 + 0.5 * np.cos(TAU * ph)
                s *= (1 - trem) + trem * bow ** 1.5
            p = rng.uniform(-width, width) if stereo else 0.0
            out += dsp.pan(s, p)
    nv = len(freqs) * voices
    out /= np.sqrt(nv)
    cutoff = 1800 + 6000 * bright
    out = dsp.lowpass(out, cutoff, 2)
    out = dsp.peq(out, 2500, 1.2, 3.0 * bright)
    out = dsp.peq(out, 300, 1.0, 2.0)
    if pont > 0:
        hi = dsp.highpass(out, 3000, 2) * 2.0
        nz = rng.standard_normal((2, n))
        nz = dsp.bandpass(nz, 3000, 9000, 2) * 0.08
        out = out * (1 - 0.4 * pont) + pont * (hi + nz)
    out *= env
    return out if stereo else out.mean(axis=0)


# --------------------------------------------------------------------------
# drums
# --------------------------------------------------------------------------
def taiko(rng, f0=62.0, vel=1.0, decay=0.55, dur=1.6, stick=0.5, size=1.0):
    n = ns(dur)
    t = np.arange(n) / SR
    f = f0 * (1.0 + 0.45 * vel * np.exp(-t / 0.03))
    body = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / (decay * size))
    modes = [(1.59, 0.35, 0.18), (2.14, 0.25, 0.12), (2.30, 0.2, 0.09), (2.65, 0.15, 0.07), (3.16, 0.1, 0.05)]
    for r, a, T in modes:
        body += a * np.sin(TAU * f0 * r * t * (1 + 0.1 * np.exp(-t / 0.02)) + rng.uniform(0, 6)) * np.exp(-t / T)
    air = dsp.lowpass(rng.standard_normal(n), 180, 2) * np.exp(-t / (0.25 * size)) * 0.6
    L = ns(0.015)
    st = dsp.lowpass(rng.standard_normal(L), 3500, 2) * np.exp(-np.arange(L) / (0.003 * SR))
    y = body + air
    y[:L] += st * stick * 1.5
    shell = dsp.reson(y, 210 * (1 / size) ** 0.3, 6, 0.4)
    y = y + shell
    y *= np.minimum(1.0, t / 0.001)
    return dsp.fade(y, 0.0, min(0.15, dur * 0.2)) * vel


def kakko_hit(rng, vel=1.0):
    n = ns(0.25)
    t = np.arange(n) / SR
    y = np.sin(TAU * 420 * t * (1 + 0.15 * np.exp(-t / 0.01))) * np.exp(-t / 0.05)
    y += 0.5 * np.sin(TAU * 690 * t) * np.exp(-t / 0.03)
    nz = dsp.bandpass(rng.standard_normal(n), 800, 6000, 2) * np.exp(-t / 0.006)
    return (y + nz * 1.4) * vel


def shoko_hit(rng, vel=1.0):
    """Small bronze gong of gagaku."""
    y = bell_partials(1180.0, 1.6, rng, [1.0, 1.52, 2.34, 2.97, 3.85], [1.0, 0.6, 0.5, 0.35, 0.2],
                      [0.9, 0.6, 0.45, 0.3, 0.2], beat=(2, 7), attack=0.0006)
    L = ns(0.004)
    y[:L] += dsp.highpass(rng.standard_normal(L), 2000) * 0.5
    return y * vel


# --------------------------------------------------------------------------
# gagaku winds
# --------------------------------------------------------------------------
def sho(chords, dur, rng, stereo=True, breath=0.03):
    """Sho mouth organ.  chords = [(t_start, t_end, [freqs], level)].  Free-reed
    tone (narrow pulse + saw through pipe resonances) with slow breathing
    swells; notes of a new chord enter one by one (te-utsuri)."""
    n = ns(dur)
    out = np.zeros((2, n))
    for (t0, t1, fr, lvl) in chords:
        for j, f in enumerate(fr):
            ent = t0 + j * rng.uniform(0.04, 0.12)
            i0, i1 = ns(ent), min(n, ns(t1 + rng.uniform(0.0, 0.15)))
            m = i1 - i0
            if m <= 0:
                continue
            tt = np.arange(m) / SR
            sw = np.minimum(1, tt / rng.uniform(0.6, 1.2)) * np.minimum(1, (m / SR - tt) / 0.5)
            sw *= 0.75 + 0.25 * np.sin(np.pi * tt / (m / SR)) ** 1.5
            fv = f * dsp.cents(2.5 * dsp.smooth(m, rng, 0.8))
            s = 0.6 * dsp.pulse(fv, 0.27, m, rng.random()) + 0.4 * dsp.saw(fv, m, rng.random())
            s *= sw * lvl
            p = rng.uniform(-0.6, 0.6) if stereo else 0.0
            out[:, i0:i1] += dsp.pan(s, p)
    out = dsp.lowpass(out, 4200, 2)
    out = dsp.peq(out, 1700, 1.4, 4.0)
    out = dsp.peq(out, 250, 1.0, -4.0)
    nz = dsp.bandpass(rng.standard_normal((2, n)), 1500, 7000, 2)
    lvl_env = dsp.lowpass0(np.abs(out).mean(axis=0), 3.0)
    out += nz * lvl_env * breath * 4
    return out


def hichiriki(f0_traj, amp, rng, nasal=1.0):
    """Double-reed oboe of gagaku: bright saw + odd emphasis through strong
    formants, breathy edge.  f0_traj/amp per-sample."""
    n = len(f0_traj)
    s = dsp.saw(f0_traj, n) * 0.7 + dsp.pulse(f0_traj, 0.42, n) * 0.5
    s += 0.15 * dsp.saw(f0_traj * 2.0, n, 0.1)
    s = dsp.lowpass(s, 6500, 2)
    y = s * 0.25
    y += dsp.reson(s, 1150, 3.5, 1.4 * nasal)
    y += dsp.reson(s, 2450, 5.0, 0.8 * nasal)
    y += dsp.reson(s, 3600, 6.0, 0.4)
    y = dsp.highpass(y, 300, 2)
    nz = dsp.bandpass(rng.standard_normal(n), 1500, 6000, 2) * 0.05
    y = (y + nz) * amp
    return y


# --------------------------------------------------------------------------
# wood / impacts / friction
# --------------------------------------------------------------------------
WOOD_FLOOR = [(95, 0.20, 1.0), (170, 0.16, 0.8), (260, 0.12, 0.6), (390, 0.10, 0.45),
              (560, 0.08, 0.3), (820, 0.06, 0.22), (1250, 0.05, 0.15), (1900, 0.035, 0.1)]
WOOD_DOOR = [(140, 0.18, 0.7), (230, 0.15, 1.0), (360, 0.12, 0.8), (520, 0.10, 0.6), (760, 0.08, 0.45),
             (1050, 0.06, 0.35), (1480, 0.05, 0.25), (2200, 0.04, 0.15), (3100, 0.03, 0.08)]
CERAMIC = [(1850, 0.12, 1.0), (2950, 0.09, 0.7), (4300, 0.07, 0.5), (5600, 0.05, 0.35), (7400, 0.035, 0.2)]


def wood_hit(rng, modes=WOOD_DOOR, size=1.0, hardness=0.5, vel=1.0, dur=0.6, jitter=0.05, noise=0.3):
    """Impact on a wooden body: raised-cosine contact pulse -> modal IR."""
    ms = [(f / size * (1 + rng.uniform(-jitter, jitter)), T * size ** 0.5, a * rng.uniform(0.6, 1.0)) for f, T, a in modes]
    ir = dsp.modal_ir(ms, dur, rng)
    cl = max(2, ns(0.0004 + 0.004 * (1 - hardness)))
    contact = np.sin(np.pi * np.arange(cl) / cl) ** 2
    y = signal.fftconvolve(ir, contact)[: len(ir)]
    nz = rng.standard_normal(len(y)) * np.exp(-np.arange(len(y)) / (0.004 * SR))
    nz = dsp.lowpass(nz, 1500 + 6000 * hardness, 2)
    y = y / (np.max(np.abs(y)) + 1e-9) + noise * nz
    return y * vel


def friction_pulses(dur, rng, rate, amp, jitter=0.25, width=0.00015):
    """Stick-slip impulse train with time-varying rate (Hz) and amplitude."""
    n = ns(dur)
    rate = np.broadcast_to(np.asarray(rate, float), (n,))
    amp = np.broadcast_to(np.asarray(amp, float), (n,))
    x = np.zeros(n)
    t = rng.uniform(0, 0.005)
    while True:
        i = int(t * SR)
        if i >= n:
            break
        x[i] += amp[i] * (0.4 + 0.6 * rng.random())
        per = 1.0 / max(rate[i], 1.0)
        t += max(0.0008, per * (1.0 + jitter * rng.standard_normal()))
    w = max(1, ns(width))
    if w > 1:
        x = signal.fftconvolve(x, np.hanning(w + 2)[1:-1])[:n]
    return x


def creak(dur, rng, rate_pts, amp_pts, modes=None, jitter=0.22, q_scale=1.0, pitch=1.0, noise=0.08, tonal=0.0):
    """Wood creak: stick-slip pulses through resonant wood band-passes.
    rate_pts/amp_pts: [(t, value)] keyframes (rate in Hz)."""
    n = ns(dur)
    rate = dsp.env_pts(n, rate_pts)
    rate *= 1 + 0.12 * dsp.smooth(n, rng, 6.0)
    amp = dsp.env_pts(n, amp_pts)
    x = friction_pulses(dur, rng, rate, amp, jitter=jitter)
    if modes is None:
        base = rng.uniform(0.85, 1.2) * pitch
        modes = [(230 * base, 10, 1.0), (420 * base, 14, 0.9), (690 * base, 16, 0.7), (1100 * base, 18, 0.5),
                 (1650 * base, 20, 0.35), (2400 * base, 22, 0.2)]
    modes = [(f, q * q_scale, g) for f, q, g in modes]
    y = dsp.resbank(x, modes)
    if tonal > 0:
        # a squeal component: narrow-band noise tracking the pulse rate harmonics
        sq = dsp.nb_noise(np.maximum(rate * 3.0, 60), 25, rng) * amp
        y += tonal * dsp.bandpass(sq, 300, 3000, 1) * np.max(np.abs(y)) * 0.5
    nz = dsp.bandpass(rng.standard_normal(n), 800, 4000, 2) * amp * noise
    y = y / (np.max(np.abs(y)) + 1e-9) + nz
    return dsp.lowpass(y, 4500, 2)


def grains(dur, rng, rate, make_grain, rate_env=None):
    """Poisson grain cloud.  make_grain(rng, i, t) -> mono grain."""
    n = ns(dur)
    y = np.zeros(n)
    t = 0.0
    i = 0
    while t < dur:
        r = rate * (rate_env(t) if rate_env else 1.0)
        t += rng.exponential(1.0 / max(r, 1e-3))
        if t >= dur:
            break
        g = make_grain(rng, i, t)
        if g is not None:
            dsp.place(y, g, t)
        i += 1
    return y


def crackle(dur, rng, density, env=None, hp=1500.0, decay=0.0008):
    """Sparse random impulses (fire / paper crackle)."""
    n = ns(dur)
    x = np.zeros(n)
    k = rng.poisson(density * dur)
    pos = rng.integers(0, n, k)
    amps = rng.pareto(2.5, k) * rng.choice([-1, 1], k)
    if env is not None:
        amps *= env[pos]
    np.add.at(x, pos, amps)
    L = max(3, ns(decay * 6))
    kern = np.exp(-np.arange(L) / max(1, decay * SR))
    x = signal.fftconvolve(x, kern)[:n]
    return dsp.highpass(x, hp, 2)


def heartbeat(rng, vel=1.0, lub_f=(68, 44), dub_f=(78, 54), gap=0.27, dub_gain=0.55, dur=0.62):
    n = ns(dur)
    y = np.zeros(n)
    for t0, (fa, fb), g, T in [(0.012, lub_f, 1.0, 0.075), (0.012 + gap, dub_f, dub_gain, 0.055)]:
        m = ns(0.32)
        t = np.arange(m) / SR
        f = fb + (fa - fb) * np.exp(-t / 0.04)
        th = np.sin(TAU * np.cumsum(f) / SR)
        e = (1 - np.exp(-t / 0.006)) * np.exp(-t / T)
        thump = th * e
        thump += 0.35 * np.sin(2 * TAU * np.cumsum(f) / SR) * e * np.exp(-t / 0.03)
        cl = dsp.lowpass(rng.standard_normal(m), 900, 2) * np.exp(-t / 0.004) * 0.25
        dsp.place(y, (thump + cl) * g, t0)
    y = dsp.lowpass(y, 260, 2)
    return y * vel
