"""Instrument / physical-ish sound sources for HITORI."""
import numpy as np
from scipy import signal

import dsp
from dsp import SR, TAU, ns

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


# --------------------------------------------------------------------------
# modal material sets (freq, t60, amp)
# --------------------------------------------------------------------------
METAL = [(620, 0.5, 0.8), (1277, 0.4, 0.6), (1890, 0.35, 0.7), (2710, 0.28, 0.5), (3650, 0.2, 0.4), (4800, 0.15, 0.3)]
GLASS = [(2650, 0.18, 1.0), (4380, 0.12, 0.6), (6300, 0.09, 0.4), (8700, 0.06, 0.25)]
BOTTLE = [(820, 0.25, 1.0), (1960, 0.16, 0.5), (3420, 0.12, 0.45), (5180, 0.08, 0.3), (7300, 0.05, 0.2)]
STONE = [(1900, 0.03, 1.0), (2850, 0.025, 0.8), (4100, 0.02, 0.6), (5600, 0.015, 0.4), (7800, 0.01, 0.25)]
TRUNK = [(165, 0.09, 0.9), (262, 0.08, 1.0), (395, 0.07, 0.75), (580, 0.05, 0.5), (830, 0.04, 0.35),
         (1240, 0.03, 0.2), (1800, 0.02, 0.12)]
KEYS = [(3150, 0.35, 1.0), (4620, 0.28, 0.7), (6030, 0.2, 0.5), (7710, 0.15, 0.35), (9800, 0.1, 0.2)]
CHAIN = [(1850, 0.12, 1.0), (2930, 0.09, 0.7), (4300, 0.07, 0.5), (6100, 0.05, 0.3)]
PLASTIC = [(1450, 0.03, 1.0), (2380, 0.025, 0.7), (3900, 0.02, 0.5), (5600, 0.012, 0.3)]
TIN = [(87, 0.9, 0.6), (131, 0.8, 0.7), (196, 0.7, 0.9), (283, 0.6, 1.0), (377, 0.55, 0.8), (512, 0.5, 0.75),
       (698, 0.42, 0.6), (911, 0.35, 0.5), (1230, 0.3, 0.4), (1650, 0.22, 0.3), (2240, 0.16, 0.2), (3100, 0.1, 0.12)]


def modal_hit(rng, modes, size=1.0, hardness=0.5, vel=1.0, dur=0.6, jitter=0.05, noise=0.3):
    """Impact on a resonant body (any modal set) - alias of wood_hit."""
    return wood_hit(rng, modes, size=size, hardness=hardness, vel=vel, dur=dur, jitter=jitter, noise=noise)


# --------------------------------------------------------------------------
# forest floor: dry leaves / cedar needles
# --------------------------------------------------------------------------
def _grain(rng, fc, q, dec, L=None):
    L = L or ns(dec * 6 + 0.0005)
    g = rng.standard_normal(L) * np.exp(-np.arange(L) / max(1.0, dec * SR))
    return dsp.reson(g, fc, q, 1.0) * np.sqrt(q)


def crunch(dur, rng, rate_env, lo=900.0, hi=9000.0, dec=(0.0003, 0.0018), q=(0.8, 3.5), pareto=2.2):
    """Dry-leaf fracture cloud: Poisson micro-clicks of random colour, density
    following rate_env(t) (grains / second), Pareto-distributed loudness."""
    n = ns(dur)
    y = np.zeros(n)
    t = 0.0
    while t < dur:
        r = max(rate_env(t), 1.0)
        t += rng.exponential(1.0 / r)
        if t >= dur:
            break
        if rate_env(t) < 1.0:
            continue
        fc = np.exp(rng.uniform(np.log(lo), np.log(hi)))
        g = _grain(rng, fc, rng.uniform(*q), rng.uniform(*dec))
        dsp.place(y, g * min(rng.pareto(pareto) + 0.15, 3.5), t)
    return y


def leaf_step(rng, weight=1.0, crunch_amt=1.0, needles=1.0, damp=0.0, drag=0.0, heel=1.0, toe=1.0, dur=0.5,
              twig=0.0, pace=1.0, bright=1.0):
    """One footstep on the dead-needle / dry-leaf forest floor: soft body
    thump of the heel and of the toe roll, two dense bursts of leaf fractures,
    the soft 'shff' of needles, optional drag (shuffle) and a twig."""
    n = ns(dur)
    y = np.zeros(n)
    th_ = 0.012
    tt_ = th_ + rng.uniform(0.075, 0.115) * pace
    for t0, g, f0 in ((th_, heel, 92), (tt_, toe * 0.6, 120)):
        m = ns(0.14)
        tt = np.arange(m) / SR
        th = np.sin(TAU * np.cumsum(f0 * (1 + 0.6 * np.exp(-tt / 0.012))) / SR) * dsp.env_ad(m, 0.004, 0.028)
        th += dsp.lowpass(rng.standard_normal(m), 320, 2) * dsp.env_ad(m, 0.002, 0.02) * 0.7
        dsp.place(y, th * 0.32 * weight * g, t0)
    a1, a2 = heel * crunch_amt, toe * crunch_amt

    def dens(t):
        d = 0.0
        if t >= th_:
            d += 2600 * a1 * np.exp(-(t - th_) / 0.032)
        if t >= tt_:
            d += 1900 * a2 * np.exp(-(t - tt_) / 0.045)
        if t >= th_:
            d += 160 * crunch_amt * np.exp(-(t - th_) / 0.16)
        return d

    hi = 9500 * bright * (1 - 0.55 * damp)
    cr = crunch(dur, rng, dens, lo=800, hi=hi)
    y += cr * 0.2
    env = np.zeros(n)
    tt = np.arange(n) / SR
    env += np.where(tt >= th_, np.exp(-(tt - th_) / 0.06), 0) * heel
    env += np.where(tt >= tt_, np.exp(-(tt - tt_) / 0.09), 0) * toe * 0.8
    env = dsp.lowpass0(env, 60.0)
    shf = dsp.bandpass(rng.standard_normal(n), 1800, 7500 * bright, 2) * env * (1 + 0.5 * dsp.smooth(n, rng, 40.0))
    y += shf * 0.05 * needles
    if damp > 0:
        sq = dsp.bandpass(rng.standard_normal(n), 250, 1400, 2) * env * damp
        y += sq * 0.12
    if drag > 0:
        d0 = tt_ + 0.05
        m = ns(0.28)
        de = dsp.env_pts(m, [(0, 0), (0.06, 1.0), (0.2, 0.6), (0.28, 0)])
        sc = dsp.bandpass(rng.standard_normal(m), 400, 3500, 2) * de * (1 + 0.6 * dsp.smooth(m, rng, 30.0))
        sc += crunch(0.28, rng, lambda t: 500 * np.exp(-t / 0.12), lo=1000, hi=6000) * 0.06
        dsp.place(y, sc * 0.09 * drag, d0)
    if twig > 0:
        dsp.place(y, twig_snap(rng, thick=rng.uniform(0.05, 0.25)) * 0.35 * twig, th_ + rng.uniform(0.0, 0.05))
    return y


def twig_snap(rng, thick=0.3, splinter=1.0, double=False):
    """Dry branch fracture: a cluster of fibre breaks spread over a few ms
    (thin twig) to tens of ms (thick branch); each break is a short noise
    burst coloured by the wood's broad bending resonances, with only a faint
    modal ring, then splinter crackle."""
    dur = 0.45 + 0.5 * thick
    n = ns(dur)
    y = np.zeros(n)
    f1 = 2400.0 * (1.0 - 0.72 * thick) * rng.uniform(0.85, 1.15)
    breaks = [0.004]
    for _ in range(rng.integers(2, 5) + int(6 * thick)):
        breaks.append(breaks[-1] + rng.uniform(0.0005, 0.003 + 0.012 * thick))
    if double:
        b2 = breaks[-1] + rng.uniform(0.06, 0.16)
        breaks += [b2 + k * rng.uniform(0.0008, 0.004) for k in range(rng.integers(2, 4))]
    ring = dsp.modal_ir([(f1 * r * rng.uniform(0.97, 1.03), (0.006 + 0.02 * thick) * rng.uniform(0.7, 1.3), a)
                         for r, a in [(1.0, 1.0), (2.76, 0.5), (5.40, 0.25)]], 0.12, rng)
    for k, tb in enumerate(breaks):
        g = (1.0 if k == 0 else rng.uniform(0.25, 0.8)) * (0.85 if double and tb > breaks[0] + 0.05 else 1.0)
        L = ns(rng.uniform(0.001, 0.003) + 0.004 * thick)
        b = rng.standard_normal(L) * np.exp(-np.arange(L) / (rng.uniform(0.0003, 0.0009) * SR))
        col = dsp.bandpass(b, f1 * 0.45, min(f1 * 3.2, 15000), 1) + 0.6 * dsp.highpass(b, 2500, 2)
        col = col + signal.fftconvolve(b, ring)[:L + len(ring) - 1][:L] * 0.0
        dsp.place(y, col * g, tb)
        if k % 2 == 0:
            r = signal.fftconvolve(b[:ns(0.0008)], ring)
            dsp.place(y, r / (np.max(np.abs(r)) + 1e-9) * 0.25 * g, tb)
    y = y / (np.max(np.abs(y)) + 1e-9)
    if thick > 0.4:  # thick wood: low knock of the halves
        m = ns(0.12)
        tt = np.arange(m) / SR
        dsp.place(y, np.sin(TAU * (120 + 60 * np.exp(-tt / 0.01)) * tt) * dsp.env_ad(m, 0.002, 0.025) * 0.3 * thick, breaks[0])
    t_sp = breaks[0]
    sp = crunch(dur, rng, lambda t: 1100 * splinter * np.exp(-(t - t_sp) / (0.025 + 0.06 * thick)) if t > t_sp else 0,
                lo=1500, hi=9000, dec=(0.0002, 0.0009))
    y += sp * 0.14
    return y


# --------------------------------------------------------------------------
# crickets & katydids (autumn chorus)
# --------------------------------------------------------------------------
def insect_phrase(rng, f, pulses, rate, duty=0.6, drop=0.02, env=None, shape=1.5, harm=0.08):
    """A stridulation phrase: `pulses` near-pure tone pulses at `rate` per
    second; each pulse glides down slightly (the file-and-scraper), with a
    weak 2nd harmonic."""
    per = 1.0 / rate
    dur = pulses * per + 0.02
    n = ns(dur)
    t = np.arange(n) / SR
    k = np.floor(t / per)
    ph = (t / per) - k
    on = (ph < duty) & (k < pulses)
    pe = np.where(on, np.sin(np.pi * np.clip(ph / duty, 0, 1)) ** shape, 0.0)
    fi = f * (1.0 + drop * (0.5 - np.clip(ph / duty, 0, 1)))
    phase = np.cumsum(fi) / SR
    y = (np.sin(TAU * phase) + harm * np.sin(2 * TAU * phase + 0.4)) * pe
    if env is not None:
        y *= np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(env)), env)
    return y


# --------------------------------------------------------------------------
# bells
# --------------------------------------------------------------------------
BEAR_BELL = dict(ratios=[1.0, 1.52, 2.21, 2.74, 3.40, 4.13, 5.02, 6.10],
                 amps=[1.0, 0.55, 0.75, 0.38, 0.45, 0.25, 0.2, 0.12],
                 t60s=[2.6, 1.9, 1.45, 1.15, 0.9, 0.7, 0.5, 0.35])


def bell_strike(rng, base, vel=1.0, dur=2.5, spec=BEAR_BELL, beat=(1.2, 6.0), click=0.5, damp=1.0):
    """One clapper strike on a small brass bell: split-mode doublets (beats),
    per-strike random mode excitation, a bright metal click."""
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for r, a, T in zip(spec["ratios"], spec["amps"], spec["t60s"]):
        f = base * r
        if f > SR * 0.45:
            continue
        a = a * rng.uniform(0.45, 1.0)
        df = rng.uniform(*beat)
        ph = rng.uniform(0, TAU, 2)
        e = np.exp(-6.9 * t / (T * damp))
        y += a * e * (np.sin(TAU * f * t + ph[0]) + 0.65 * np.sin(TAU * (f + df) * t + ph[1]))
    L = ns(0.003)
    c = dsp.highpass(rng.standard_normal(L), 3000, 2) * np.exp(-np.arange(L) / (0.0004 * SR))
    y[:L] += c * click * 3.0
    y *= np.minimum(1.0, t / 0.0005)
    return dsp.fade(y, 0.0, min(0.08, dur * 0.2)) * vel


def bear_bell(rng, dur, strikes, base=1650.0, damp=1.0):
    """strikes = [(t, vel)] -> brass bear bell (熊鈴) ringing."""
    n = ns(dur)
    y = np.zeros(n)
    for t0, v in strikes:
        if t0 >= dur:
            continue
        s = bell_strike(rng, base * rng.uniform(0.998, 1.002), v, dur=min(3.0, dur - t0 + 0.01), damp=damp)
        dsp.place(y, s, t0)
        if rng.random() < 0.3:  # clapper bounce
            dsp.place(y, bell_strike(rng, base, v * rng.uniform(0.15, 0.35), dur=min(2.0, max(0.05, dur - t0))), t0 + rng.uniform(0.02, 0.05))
    return y


def furin(rng, base=None, vel=1.0, detune=0.0, dur=2.4):
    """Japanese wind bell: clear tone with a few inharmonic partials.
    detune (cents) splits the doublets wide -> sour beating."""
    base = base or rng.uniform(2100, 3300)
    bt = (1.0, 4.0) if detune == 0 else (base * (dsp.cents(detune) - 1) * 0.8, base * (dsp.cents(detune) - 1) * 1.2)
    y = bell_partials(base, dur, rng, [1.0, 2.71, 5.12, 7.9], [1.0, 0.35, 0.15, 0.06], [1.8, 0.9, 0.45, 0.25],
                      beat=bt, attack=0.0006)
    L = ns(0.002)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * 0.3
    return y * vel


def rin(rng, f0=1180.0, dur=4.0, vel=1.0):
    """Small Buddhist rin bowl: long shimmering partials."""
    return bell_partials(f0, dur, rng, [1.0, 2.71, 5.15, 8.3], [1.0, 0.45, 0.22, 0.08], [4.5, 2.5, 1.2, 0.6],
                         beat=(0.6, 2.2), attack=0.001) * vel


def phone_bell(rng, dur, ring_spans, rate=16.0, gongs=(1060.0, 1190.0)):
    """Electromechanical telephone bell: a clapper hammering two steel gongs
    alternately at `rate` Hz during each (t0, t1) ring span."""
    n = ns(dur)
    y = np.zeros(n)
    spec = [(1.0, 1.0, 1.4), (2.31, 0.6, 0.9), (3.92, 0.45, 0.6), (5.61, 0.3, 0.4), (7.43, 0.2, 0.25)]
    for t0, t1 in ring_spans:
        t = t0
        k = 0
        while t < t1:
            g = gongs[k % 2]
            ir = bell_partials(g, 1.6, rng, [s[0] for s in spec], [s[1] * rng.uniform(0.7, 1.0) for s in spec],
                               [s[2] for s in spec], beat=(2.0, 7.0), attack=0.0003)
            L = ns(0.002)
            ir[:L] += dsp.highpass(rng.standard_normal(L), 2500) * 0.8
            dsp.place(y, ir * rng.uniform(0.8, 1.0), t, circular=True)
            k += 1
            t += 1.0 / rate * (1 + rng.normal(0, 0.03))
        m = ns(t1 - t0)
        buzz = dsp.bandpass(rng.standard_normal(m), 200, 2500, 2)
        buzz *= (0.5 + 0.5 * np.cos(TAU * rate * np.arange(m) / SR)) ** 4
        dsp.place(y, buzz * 0.08, t0, circular=True)
    return y


# --------------------------------------------------------------------------
# tuned percussion & keyboards
# --------------------------------------------------------------------------
def marimba(freq, rng, vel=1.0, dur=None, bright=0.5, hard=0.4):
    """Marimba bar: tuned modes 1 : 3.93 : 9.2, tube resonator on the
    fundamental, soft mallet contact."""
    T1 = float(np.clip(1.6 * (300.0 / freq) ** 0.55, 0.25, 2.5))
    dur = dur or T1 * 1.3
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.sin(TAU * freq * t) * np.exp(-6.9 * t / T1)
    y += 0.9 * np.sin(TAU * freq * t + 0.3) * np.exp(-6.9 * t / (T1 * 1.5)) * 0.35  # resonator
    for r, a, T in ((3.93, 0.32 * bright, 0.22), (9.2, 0.12 * bright, 0.07), (2.0, 0.05, 0.3)):
        if freq * r < SR * 0.45:
            y += a * np.sin(TAU * freq * r * t + rng.uniform(0, 6)) * np.exp(-6.9 * t / T)
    cl = max(2, ns(0.0008 + 0.003 * (1 - hard)))
    contact = np.sin(np.pi * np.arange(cl) / cl) ** 2
    y = signal.fftconvolve(y, contact / contact.sum())[:n]
    L = ns(0.004)
    y[:L] += dsp.bandpass(rng.standard_normal(L), 1500, 6000) * np.exp(-np.arange(L) / (0.0006 * SR)) * 0.08 * hard
    return dsp.fade(y, 0.0, 0.03) * vel


def piano(freq, dur, rng, vel=0.7, detune_c=0.0, unison_c=1.2, bright=0.5, release=None, strike=0.12):
    """Piano note: stiff-string partials (inharmonicity B), 2-3 unison strings
    slightly detuned (beating), two-stage decay, hammer thump, felt damper."""
    n = ns(dur)
    t = np.arange(n) / SR
    f = freq * dsp.cents(detune_c)
    B = 0.00025 * (f / 260.0) ** 0.8
    K = int(min(40, (SR * 0.42) / f))
    nstr = 3 if f > 180 else 2
    offs = [0.0] + [rng.normal(0, unison_c) for _ in range(nstr - 1)]
    y = np.zeros(n)
    T0 = float(np.clip(9.0 * (130.0 / f) ** 0.6, 1.2, 14.0))
    for k in range(1, K + 1):
        fk = k * f * np.sqrt(1 + B * k * k)
        if fk > SR * 0.45:
            break
        a = k ** (-1.1 + 0.6 * bright * vel) * abs(np.sin(np.pi * k * strike)) / (np.sin(np.pi * strike) + 1e-9)
        Tk = T0 / (1 + 0.35 * (k - 1)) ** 0.9
        for j, oc in enumerate(offs):
            ph = rng.uniform(0, TAU)
            fj = fk * dsp.cents(oc)
            e = 0.7 * np.exp(-6.9 * t / (Tk * 0.25)) + 0.3 * np.exp(-6.9 * t / Tk)
            y += a * e * np.sin(TAU * fj * t + ph) / nstr
    y *= np.minimum(1.0, t / 0.0015)
    m = ns(0.06)
    th = dsp.lowpass(rng.standard_normal(m), 900 + 1500 * vel, 2) * np.exp(-np.arange(m) / (0.008 * SR))
    y[:m] += th * 0.25 * vel
    if release is not None and release < dur:
        r0 = ns(release)
        rl = ns(0.12)
        y[r0:r0 + rl] *= np.linspace(1, 0.05, len(y[r0:r0 + rl]))
        y[r0 + rl:] *= 0.05
        dn = dsp.bandpass(rng.standard_normal(ns(0.08)), 300, 2500) * dsp.env_ad(ns(0.08), 0.01, 0.02) * 0.02
        dsp.place(y, dn, release)
    y = y / (np.max(np.abs(y)) + 1e-9)
    return dsp.fade(y, 0.0, 0.05) * vel


# --------------------------------------------------------------------------
# water
# --------------------------------------------------------------------------
def bubble(rng, r_mm, vel=1.0):
    """Minnaert bubble: f = 3.26 kHz / r(mm), slightly rising, fast decay."""
    f = 3260.0 / r_mm
    tau = 0.004 + 0.0025 * r_mm
    m = ns(tau * 6)
    tt = np.arange(m) / SR
    fr = f * (1 + 0.25 * tt / (tau * 6))
    return np.sin(TAU * np.cumsum(fr) / SR) * np.exp(-tt / tau) * np.minimum(1, tt / 0.0004) * vel


def water_texture(n, rng, L=None, density=220.0, r_range=(0.7, 6.0), noise=0.5, splash=0.3):
    """Running water over stones: a cloud of bubbles (circular if L) on a
    pink rush, plus bright spray."""
    y = np.zeros(n)
    dur = n / SR
    k = rng.poisson(density * dur)
    for _ in range(k):
        r = np.exp(rng.uniform(np.log(r_range[0]), np.log(r_range[1])))
        b = bubble(rng, r, rng.pareto(2.5) * 0.3 + 0.1)
        dsp.place(y, b, rng.uniform(0, dur), circular=L is not None)
    rush = dsp.colored(n, rng, -2.0, lo=150, hi=6000)
    mod = 1 + 0.35 * dsp.smooth(n, rng, 3.0) + 0.2 * dsp.smooth(n, rng, 12.0)
    spray = dsp.colored(n, rng, 0.0, lo=3000, hi=11000)
    sm = np.clip(1 + 0.5 * dsp.smooth(n, rng, 6.0), 0.25, None)
    return y / (np.std(y) + 1e-9) * 0.5 + rush * mod * noise + spray * sm * splash * 0.25


# --------------------------------------------------------------------------
# keyboards, mallets, plucked strings, small percussion, chimes, a cheap loudspeaker
# --------------------------------------------------------------------------
def epiano(freq, dur, rng, vel=0.7, bright=0.5, tine=0.6, release=None):
    """Tine electric piano (2-op FM 1:1 whose index decays = the bark, a high
    operator near 14x for the tine 'ding', two-stage decay, damper)."""
    n = ns(dur)
    t = np.arange(n) / SR
    idx = (0.5 + 2.4 * vel * bright) * np.exp(-t / 0.3) + 0.22
    ph = rng.random()
    car = np.sin(TAU * (freq * t + ph) + idx * np.sin(TAU * freq * t))
    T = float(np.clip(5.0 * (260.0 / freq) ** 0.5, 0.9, 7.0))
    env = (0.62 * np.exp(-t / (T * 0.16)) + 0.38 * np.exp(-t / T)) * (1 - np.exp(-t / 0.0015))
    y = car * env
    fd = freq * 13.97
    if fd < SR * 0.45:
        y += 0.28 * tine * vel * np.sin(TAU * fd * t) * np.exp(-t / 0.012)
    y += 0.06 * np.sin(TAU * 2.0 * freq * t + 0.4) * np.exp(-t / 0.25)
    if release is not None and release < dur:
        r0 = ns(release)
        rl = ns(0.09)
        y[r0:r0 + rl] *= np.linspace(1, 0.0, len(y[r0:r0 + rl])) ** 1.5
        y[r0 + rl:] = 0.0
    return dsp.fade(y, 0.0, 0.03) * vel


def vibraphone(freq, dur, rng, vel=0.8, motor=5.2, depth=0.3, hard=0.5, release=None):
    """Vibraphone bar: modes 1 : 3.99 : 9.9, a long fundamental, the motor's
    rotating-disc tremolo, soft mallet contact."""
    n = ns(dur)
    t = np.arange(n) / SR
    T1 = float(np.clip(3.4 * (440.0 / freq) ** 0.45, 1.0, 6.5))
    y = np.sin(TAU * freq * t + rng.random() * 6) * np.exp(-6.9 * t / T1)
    for r, a, T in ((3.99, 0.22 + 0.2 * hard, 0.45), (9.9, 0.06 + 0.1 * hard, 0.1)):
        if freq * r < SR * 0.45:
            y += a * np.sin(TAU * freq * r * t + rng.random() * 6) * np.exp(-6.9 * t / T)
    cl = max(2, ns(0.0007 + 0.0025 * (1 - hard)))
    contact = np.sin(np.pi * np.arange(cl) / cl) ** 2
    y = signal.fftconvolve(y, contact / contact.sum())[:n]
    if depth > 0:
        y *= 1.0 - depth * (0.5 + 0.5 * np.cos(TAU * motor * t + rng.random() * 6))
    if release is not None and release < dur:
        r0 = ns(release)
        rl = ns(0.12)
        y[r0:r0 + rl] *= np.linspace(1, 0.0, len(y[r0:r0 + rl]))
        y[r0 + rl:] = 0.0
    return dsp.fade(y, 0.0, 0.03) * vel


def nylon(freq, dur, rng, vel=0.7, bright=0.32, pos=None):
    """Nylon-string guitar pluck: Karplus-Strong + a small hollow body."""
    t60 = float(np.clip(3.2 * (196.0 / freq) ** 0.4, 0.9, 4.2))
    s = ks_string(freq, dur, rng, t60=t60, bright=bright * (0.8 + 0.4 * vel),
                  pluck_pos=pos if pos is not None else rng.uniform(0.14, 0.22), exc_soft=0.55)
    s = s / (np.max(np.abs(s)) + 1e-9)
    body = dsp.resbank(s, [(102, 3.0, 1.0), (215, 4.0, 0.7), (395, 5.0, 0.45), (610, 6.0, 0.3)])
    y = s + 0.35 * body / (np.max(np.abs(body)) + 1e-9)
    y = dsp.lowpass(y, 5200, 2)
    return dsp.fade(y, 0.0005, 0.04) * vel


def upright_bass(freq, dur, rng, vel=0.8, release=None):
    """Plucked double bass: few harmonics, a woody thump, a slight pitch drop."""
    n = ns(dur)
    t = np.arange(n) / SR
    f = freq * dsp.cents(18.0 * np.exp(-t / 0.03))
    ph = np.cumsum(f) / SR
    y = (np.sin(TAU * ph) * np.exp(-t / 0.95) + 0.42 * np.sin(2 * TAU * ph + 0.3) * np.exp(-t / 0.45)
         + 0.16 * np.sin(3 * TAU * ph + 0.9) * np.exp(-t / 0.22) + 0.05 * np.sin(4 * TAU * ph) * np.exp(-t / 0.12))
    y *= 1 - np.exp(-t / 0.004)
    m = ns(0.03)
    th = dsp.lowpass(rng.standard_normal(m), 700, 2) * np.exp(-np.arange(m) / (0.006 * SR))
    y[:m] += th * 0.35
    if release is not None and release < dur:
        r0 = ns(release)
        rl = ns(0.06)
        y[r0:r0 + rl] *= np.linspace(1, 0, len(y[r0:r0 + rl]))
        y[r0 + rl:] = 0
    return dsp.fade(y, 0.0, 0.03) * vel


def shaker(rng, vel=0.5, dur=0.085):
    m = ns(dur)
    e = dsp.env_pts(m, [(0, 0), (dur * 0.25, 1.0), (dur, 0.0)]) ** 1.6
    return dsp.bandpass(rng.standard_normal(m), 4200, 11500, 2) * e * vel


def rim_click(rng, vel=0.6):
    return modal_hit(rng, [(1650, 0.04, 1.0), (3900, 0.02, 0.5), (720, 0.05, 0.35)], size=1.0, hardness=1.0,
                     vel=vel, dur=0.12, noise=0.25)


def soft_kick(rng, vel=0.7):
    m = ns(0.35)
    t = np.arange(m) / SR
    y = np.sin(TAU * np.cumsum(48 + 62 * np.exp(-t / 0.018)) / SR) * dsp.env_ad(m, 0.002, 0.11)
    y += dsp.lowpass(rng.standard_normal(m), 1500, 2) * np.exp(-t / 0.003) * 0.15
    return y * vel


def brush(rng, vel=0.4, dur=0.22):
    m = ns(dur)
    e = dsp.env_pts(m, [(0, 0), (0.02, 1.0), (dur, 0.0)]) ** 1.2
    return dsp.bandpass(rng.standard_normal(m), 1800, 8000, 2) * e * vel


DRAWBARS = (0.5, 1.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0)  # 16' 5 1/3' 8' 4' 2 2/3' 2' 1 3/5' 1 1/3' 1'


def organ(chords, dur, rng, bars=(6, 2, 8, 4, 0, 2, 0, 0, 0), leslie=0.75, leslie_depth=0.18, click=0.25,
          stereo=True, detune_c=0.0, vib_c=0.0):
    """Tonewheel-ish drawbar organ.  chords = [(t0, t1, [freqs], level)];
    bars = drawbar settings 0..8 (3 dB per step).  Slow 'leslie': AM + a little
    Doppler FM, phase-shifted between the ears."""
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    amps = [0.0 if b <= 0 else 10 ** ((b - 8) * 3.0 / 20.0) for b in bars]
    lph = rng.random()
    for (t0, t1, fr, lvl) in chords:
        i0, i1 = ns(t0), min(n, ns(t1))
        m = i1 - i0
        if m <= 0:
            continue
        tt = np.arange(m) / SR
        env = np.minimum(1.0, tt / 0.012) * np.minimum(1.0, (m / SR - tt) / 0.06)
        acc = np.zeros(m)
        for f in fr:
            fv = f * dsp.cents(detune_c + rng.normal(0, 1.5))
            for r, a in zip(DRAWBARS, amps):
                if a <= 0 or fv * r > SR * 0.45:
                    continue
                acc += a * np.sin(TAU * fv * r * tt + rng.random() * 6)
        if click > 0:
            k = ns(0.006)
            ck = dsp.bandpass(rng.standard_normal(k), 1500, 7000, 2) * np.exp(-np.arange(k) / (0.0012 * SR))
            ck *= np.minimum(1.0, np.arange(k) / (0.001 * SR))
            acc[:k] += ck * click * len(fr)
        seg = acc * env * lvl
        for c in range(2 if stereo else 1):
            lfo = np.sin(TAU * (leslie * (t[i0:i1]) + lph + 0.25 * c))
            am = 1.0 - leslie_depth * (0.5 + 0.5 * lfo)
            out[c, i0:i1] += seg * am
    if not stereo:
        out[1] = out[0]
    if vib_c:
        pass
    return out


def chime_bar(freq, dur, rng, vel=1.0, bright=0.5, decay=1.2):
    """Electronic door/PA chime tone: a soft struck bar (partials 1, 2.76, 5.4)
    with a slightly slow attack, as heard from a small loudspeaker."""
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.sin(TAU * freq * t) * np.exp(-6.9 * t / decay)
    y += 0.5 * np.sin(TAU * freq * t + 0.3) * np.exp(-6.9 * t / (decay * 0.7)) * 0.4
    for r, a, T in ((2.0, 0.12, 0.6), (2.76, 0.18 * bright, 0.35), (5.4, 0.07 * bright, 0.12)):
        if freq * r < SR * 0.45:
            y += a * np.sin(TAU * freq * r * t + rng.random() * 6) * np.exp(-6.9 * t / (T * decay))
    y *= 1 - np.exp(-t / 0.004)
    return dsp.fade(y, 0.0, min(0.1, dur * 0.2)) * vel


def small_speaker(x, lo=380.0, hi=7800.0, res=2600.0, drive=1.15):
    """A cheap ceiling / panel loudspeaker."""
    y = dsp.highpass(x, lo, 3)
    y = dsp.lowpass(y, hi, 3)
    y = dsp.peq(y, res, 1.2, 3.5)
    y = dsp.peq(y, 900, 1.0, 1.5)
    pk = np.max(np.abs(y)) + 1e-12
    return dsp.sat(y / pk * drive, 1.0) * pk / drive
