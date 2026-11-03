"""Voice synthesis for REISATSU.

* ``voice``   - additive glottal-pulse source (band-limited harmonics whose
                amplitudes follow a cascade formant envelope evaluated at every
                harmonic), jitter / shimmer / sub-harmonics, plus pitch-
                synchronous aspiration noise shaped by the same vocal tract.
* ``vt_noise`` - noise through a time-varying vocal tract (breath, whisper).
* ``whisper`` / ``speak`` - shared Japanese syllable timeline (romaji-ish
                scripts) rendered as whisper (vocal-tract noise) or as voiced
                speech / sleep-mumble (additive voice + consonant noises).
* ``glottal`` / ``scream`` - pulse-by-pulse glottal source with nonlinear
                phenomena (logistic-map period doubling -> deterministic chaos,
                biphonation, frequency jumps) through time-varying formants.
* ``creak``    - irregular vocal-fry pulses through a causal formant cascade
                (the "onryo" death-rattle croak).
"""
import numpy as np
from scipy import signal

import dsp
from dsp import SR, TAU, ns

# (formant freqs, bandwidths) – 5 formants
VF = {
    "a": ([850, 1220, 2810, 3800, 4950], [80, 100, 140, 250, 300]),
    "i": ([310, 2790, 3310, 4000, 4950], [45, 120, 200, 250, 300]),
    "u": ([370, 950, 2670, 3800, 4950], [55, 90, 150, 250, 300]),
    "e": ([610, 2330, 2990, 3900, 4950], [65, 110, 170, 250, 300]),
    "o": ([500, 900, 2830, 3800, 4950], [70, 90, 160, 250, 300]),
    "m": ([280, 1150, 2300, 2900, 4000], [90, 350, 250, 300, 400]),
    "n": ([280, 1550, 2450, 3050, 4100], [90, 350, 250, 300, 400]),
    "x": ([600, 1600, 2700, 3800, 4950], [80, 120, 160, 250, 300]),  # schwa
}
VM = {
    "a": ([730, 1090, 2440, 3400, 4500], [80, 90, 120, 200, 250]),
    "i": ([270, 2290, 3010, 3500, 4500], [45, 110, 160, 200, 250]),
    "u": ([300, 870, 2240, 3300, 4500], [50, 80, 120, 200, 250]),
    "e": ([530, 1840, 2480, 3500, 4500], [60, 100, 140, 200, 250]),
    "o": ([570, 840, 2410, 3300, 4500], [70, 80, 130, 200, 250]),
    "m": ([250, 1050, 2100, 2700, 3700], [90, 300, 250, 300, 400]),
    "n": ([250, 1400, 2200, 2800, 3800], [90, 300, 250, 300, 400]),
    "x": ([500, 1500, 2500, 3500, 4500], [80, 110, 150, 200, 250]),
}
ZERO = {"m": (950.0, 180.0), "n": (1650.0, 220.0)}


def fs(v, table=VF, scale=1.0, bw=1.0, f1=1.0, nasal=None):
    """Formant state vector [F1..F5, B1..B5, zf, zb, zdepth]."""
    if isinstance(v, np.ndarray):
        return v
    F, B = table[v]
    F = [F[0] * scale * f1] + [f * scale for f in F[1:]]
    B = [b * bw for b in B]
    zf, zb = ZERO.get(v, (1000.0, 200.0))
    zd = 1.0 if v in ZERO else 0.0
    if nasal is not None:
        zd = nasal
    return np.array(F + B + [zf * scale, zb, zd], float)


def blend(a, b, w):
    return a * (1 - w) + b * w


def states_at(keys, times):
    """keys: [(t, state_vec)] -> (13, len(times))"""
    kt = np.array([k[0] for k in keys], float)
    ks = np.array([k[1] for k in keys], float)
    if len(kt) == 1:
        return np.repeat(ks.T, len(np.atleast_1d(times)), axis=1)
    return np.stack([np.interp(times, kt, ks[:, j]) for j in range(ks.shape[1])])


def vt_gain(f, S):
    """Cascade-resonator magnitude response (unity DC gain per formant) with an
    optional nasal anti-resonance."""
    g = 1.0
    f2 = f * f
    for i in range(5):
        Fi, Bi = S[i], S[5 + i]
        g = g * (Fi * Fi) / np.sqrt((Fi * Fi - f2) ** 2 + (Bi * f) ** 2 + 1e-9)
    zf, zb, zd = S[10], S[11], S[12]
    z = np.sqrt((zf * zf - f2) ** 2 + (zb * f) ** 2) / (zf * zf)
    return g * ((1 - zd) + zd * np.minimum(z, 1.0))


def voice(f0, amp, keys, rng, tilt=-6.0, fmax=8000.0, jitter=0.006, shimmer=0.05, breath=0.15,
          breath_env=None, subharm=0.0, hpc=0.0, breath_tilt=0.0, noise_mod=0.6, hop=64,
          breath_hp=400.0, nfft=1024, loud_comp=0.8, gain_fn=None):
    """Additive singing/speaking voice.

    f0, amp : per-sample arrays (Hz / linear)
    keys    : [(t_sec, formant_state)]   (see ``fs``)
    tilt    : source spectral slope in dB/octave (−12 soft/breathy … −3 pressed)
    subharm : 0..1 (scalar or per-sample) period-doubling roughness
    hpc     : high-pole correction, lifts the top end (screams)
    breath  : aspiration level relative to the harmonic part
    loud_comp : 0..1, how much the formant/pitch dependent level change is
                compensated so that ``amp`` really controls loudness
    gain_fn : optional f(freqs, times) -> magnitude replacing the cascade
              vocal tract (used for screams with explicit parallel formants)
    """
    f0 = np.asarray(f0, float)
    n = len(f0)
    amp = np.broadcast_to(np.asarray(amp, float), (n,))
    f0j = f0 * (1.0 + jitter * dsp.smooth(n, rng, 80.0) + 0.5 * jitter * dsp.smooth(n, rng, 9.0))
    f0j = np.maximum(f0j, 20.0)
    ph = np.cumsum(f0j) / SR
    idx = np.arange(0, n, hop)
    if idx[-1] != n - 1:
        idx = np.append(idx, n - 1)
    tc = idx / SR
    S = states_at(keys, tc)
    f0c = f0j[idx]
    active = amp[idx] > 1e-4
    fmin = np.min(f0c[active]) if active.any() else np.min(f0c)
    nh = int(fmax / max(fmin, 30.0))
    xs = np.arange(n)
    sub = np.broadcast_to(np.asarray(subharm, float), (n,))[idx] if np.ndim(subharm) else np.full(len(idx), subharm)
    use_sub = bool(np.any(sub > 0))
    G = np.zeros((nh, len(idx)))
    Gs = np.zeros((nh, len(idx))) if use_sub else None
    vt = (lambda f: vt_gain(f, S)) if gain_fn is None else (lambda f: gain_fn(f, tc))
    for k in range(1, nh + 1):
        fk = k * f0c
        g = vt(fk) * k ** (tilt / 6.0206) * (1.0 + hpc * (fk / 4000.0) ** 2)
        G[k - 1] = g * np.clip((fmax - fk) / (0.25 * fmax), 0.0, 1.0)
        if use_sub:
            ks = k - 0.5
            gs = vt(ks * f0c) * ks ** (tilt / 6.0206) * sub * (1.0 + hpc * (ks * f0c / 4000.0) ** 2)
            Gs[k - 1] = gs * np.clip((fmax - ks * f0c) / (0.25 * fmax), 0.0, 1.0)
    if loud_comp > 0:
        E = np.sum(G ** 2, axis=0)
        if use_sub:
            E = E + np.sum(Gs ** 2, axis=0)
        E = np.sqrt(E + 1e-12)
        ref = np.median(E[active]) if active.any() else np.median(E)
        comp = (E / ref) ** (-loud_comp)
        G *= comp
        if use_sub:
            Gs *= comp
    harm = np.zeros(n)
    for k in range(1, nh + 1):
        if G[k - 1].max() > 1e-7:
            harm += np.interp(xs, idx, G[k - 1]) * np.sin(TAU * k * ph)
        if use_sub and Gs[k - 1].max() > 1e-7:
            harm += np.interp(xs, idx, Gs[k - 1]) * np.sin(TAU * (k - 0.5) * ph + 0.7)
    if shimmer > 0:
        harm *= 1.0 + shimmer * dsp.smooth(n, rng, 60.0)
    w = amp > 1e-3 * amp.max() if amp.max() > 0 else np.ones(n, bool)
    r = np.sqrt(np.mean(harm[w] ** 2)) if w.any() else 1.0
    harm /= max(r, 1e-9)
    out = harm * amp
    if breath > 0:
        be = amp if breath_env is None else np.broadcast_to(breath_env, (n,))
        nz = rng.standard_normal(n)
        if noise_mod > 0:
            nz *= (1.0 - noise_mod) + noise_mod * 2.0 * (0.5 + 0.5 * np.cos(TAU * ph)) ** 2
        if gain_fn is None:
            nz = vt_noise_shape(nz, keys, tilt_db=breath_tilt, nfft=nfft)
        else:
            nz = dsp.spectral_shape(nz, lambda t, f: gain_fn(f[:, None], t[None, :]) *
                                    (np.maximum(f, 50.0)[:, None] / 1000.0) ** (breath_tilt / 6.0206), nfft=nfft)
        nz = dsp.highpass(nz, breath_hp, 2)
        r2 = np.sqrt(np.mean(nz[w] ** 2)) if w.any() else 1.0
        out = out + breath * nz / max(r2, 1e-9) * be
    return out


def vt_noise_shape(x, keys, tilt_db=0.0, bw=1.0, nfft=1024, hop=256):
    def envf(t, f):
        St = states_at(keys, t)
        if bw != 1.0:
            St = St.copy()
            St[5:10] *= bw
        g = vt_gain(f[:, None], St[:, None, :])
        if tilt_db:
            g = g * (np.maximum(f, 50.0)[:, None] / 1000.0) ** (tilt_db / 6.0206)
        return g

    return dsp.spectral_shape(x, envf, nfft=nfft, hop=hop)


def vt_noise(n, keys, amp, rng, tilt_db=0.0, bw=1.3, hp=250.0):
    """Unvoiced breath / whisper noise through the vocal tract."""
    nz = rng.standard_normal(n)
    y = vt_noise_shape(nz, keys, tilt_db=tilt_db, bw=bw)
    y = dsp.highpass(y, hp, 2)
    y /= np.std(y) + 1e-12
    return y * amp


def formant_cascade(x, F, B):
    y = x
    for f, b in zip(F, B):
        if f >= SR * 0.47:
            continue
        r = np.exp(-np.pi * b / SR)
        th = TAU * f / SR
        a = [1.0, -2.0 * r * np.cos(th), r * r]
        y = signal.lfilter([sum(a)], a, y)
    return y


def creak(n, rate, amp, rng, vowel="a", table=VF, jitter=0.3, scale=1.0, f1=1.0, bright=1.0):
    """Vocal fry / death-rattle: sparse irregular glottal pulses (rate ~15-90 Hz)
    through a formant cascade.  rate & amp: scalar or per-sample arrays."""
    rate = np.broadcast_to(np.asarray(rate, float), (n,))
    amp = np.broadcast_to(np.asarray(amp, float), (n,))
    x = np.zeros(n)
    t = rng.uniform(0, 0.01)
    while True:
        i = int(t * SR)
        if i >= n:
            break
        if amp[i] > 1e-4:
            # double pulses are typical for fry
            x[i] += amp[i] * rng.uniform(0.5, 1.0)
            if rng.random() < 0.35 and i + 40 < n:
                j = i + int(rng.uniform(0.002, 0.006) * SR)
                if j < n:
                    x[j] += amp[i] * rng.uniform(0.2, 0.6)
        per = 1.0 / max(rate[i], 2.0)
        t += max(0.003, per * (1.0 + jitter * rng.standard_normal()))
    # glottal pulse shape (short asymmetric bump, derivative-ish)
    pl = int(0.0025 * SR)
    tt = np.arange(pl) / pl
    gp = np.sin(np.pi * tt) ** 2 * (1 - tt) - 0.25 * np.sin(TAU * tt) * bright
    x = signal.fftconvolve(x, gp)[:n]
    F, B = table[vowel]
    F = [F[0] * scale * f1] + [f * scale for f in F[1:]]
    y = formant_cascade(x, F, B)
    return y / (np.max(np.abs(y)) + 1e-12)


def parallel_env(formants, floor_db=-40.0):
    """Explicit parallel formant envelope for f(freqs, times).
    formants: [(F, B, level_db)] where F may be a callable of time (seconds)."""
    floor = dsp.db(floor_db)

    def g(f, t):
        out = floor
        for F, B, L in formants:
            Fv = F(t) if callable(F) else F
            out = out + dsp.db(L) / (1.0 + ((f - Fv) / (B / 2.0)) ** 2)
        return out

    return g


def _bc(v, n):
    return np.broadcast_to(np.asarray(v, float), (n,))


# --------------------------------------------------------------------------
# glottal pulse source with nonlinear phenomena
# --------------------------------------------------------------------------
def glottal(f0, amp, rng, os=4, oq=0.6, skew=0.66, closure=1.2, chaos_r=0.0, chaos_t=0.0, chaos_a=0.0,
            jitter=0.0, shimmer=0.0, x0=0.37):
    """Pulse-by-pulse glottal flow (Rosenberg-like, skewed) and its derivative.

    Every period is perturbed by the logistic map x <- r x (1-x):
      chaos_r < 3        : clean periodic phonation
      3.0 .. 3.45        : period doubling -> subharmonic at f0/2
      3.45 .. 3.57       : period 4 (f0/4 sidebands)
      > 3.57             : deterministic chaos (the harsh, noisy-yet-pitched
                           segments of animal distress calls and screams)
    chaos_t / chaos_a  : how much the map perturbs the period / the amplitude.
    closure            : 0.5 = very abrupt (bright, pressed) .. 3 = smooth (dark).
    Rendered at os x SR, then decimated with an anti-alias FIR, so even 2 kHz
    shrieks have clean harmonics.  Returns (derivative, flow), both at SR."""
    n = len(f0)
    f0 = np.maximum(np.asarray(f0, float), 15.0)
    amp, cr, ct, ca = _bc(amp, n), _bc(chaos_r, n), _bc(chaos_t, n), _bc(chaos_a, n)
    jit, shm, oqv = _bc(jitter, n), _bc(shimmer, n), _bc(oq, n)
    starts, Ts, As, Os = [], [], [], []
    t, x, m = 0.0, x0, 0.5
    while t < n:
        i = int(t)
        r = cr[i]
        if r > 0.0:
            x = r * x * (1.0 - x)
            if not 0.0 < x < 1.0:
                x = 0.37
            m = 0.85 * m + 0.15 * x
            d = 2.0 * (x - m)
        else:
            d = 0.0
        T = max(1.5, SR / f0[i] * (1.0 + ct[i] * d + jit[i] * rng.standard_normal()))
        A = amp[i] * max(0.0, 1.0 + ca[i] * d + shm[i] * rng.standard_normal())
        starts.append(t)
        Ts.append(T)
        As.append(A)
        Os.append(oqv[i])
        t += T
    st, T, A, O = np.array(starts), np.array(Ts), np.array(As), np.array(Os)
    N = n * os
    s = np.arange(N) / os
    k = np.clip(np.searchsorted(st, s, side="right") - 1, 0, len(st) - 1)
    ph = (s - st[k]) / T[k]
    o = np.clip(O[k], 0.05, 0.98)
    tp = o * skew
    u = np.zeros(N)
    m1 = ph < tp
    u[m1] = 0.5 - 0.5 * np.cos(np.pi * ph[m1] / tp[m1])
    m2 = (~m1) & (ph < o)
    u[m2] = np.cos(0.5 * np.pi * (ph[m2] - tp[m2]) / (o[m2] - tp[m2])) ** closure
    u *= A[k]
    e = np.diff(u, prepend=0.0) * os
    e = signal.resample_poly(e, 1, os)[:n]
    u = signal.resample_poly(u, 1, os)[:n]
    return dsp.pad_to(e, n), dsp.pad_to(u, n)


SCREAM_F = {  # (F, level dB) above the f0-tracking F1; bandwidth = bwr * F
    "a": [(1750, -2.0), (2900, -6.0), (4100, -10.0), (5600, -15.0), (8200, -22.0)],
    "e": [(2250, -1.0), (3000, -5.0), (4200, -10.0), (5600, -15.0), (8200, -22.0)],
    "i": [(2800, 0.0), (3450, -4.0), (4500, -9.0), (5900, -14.0), (8400, -21.0)],
    "o": [(1050, -1.0), (2650, -9.0), (3600, -14.0), (4900, -18.0), (7500, -26.0)],
    "u": [(950, -4.0), (2450, -13.0), (3500, -18.0), (4800, -22.0), (7500, -28.0)],
}
F1_RANGE = {"a": (750, 1400), "e": (550, 1100), "i": (380, 900), "o": (480, 800), "u": (330, 600)}


def _contour(n, pts, rng, flutter=0.02, flutter_rate=12.0, jumps=(), smooth_hz=20.0):
    tt = np.arange(n) / SR
    lf = np.interp(tt, [p[0] for p in pts], np.log2([p[1] for p in pts]))
    lf = dsp.lowpass0(lf, smooth_hz)
    if flutter:
        lf = lf + flutter * dsp.smooth(n, rng, flutter_rate) + 0.5 * flutter * dsp.smooth(n, rng, 35.0)
    for tj, semis, wd in jumps:  # sudden register breaks (3 ms transitions)
        on = 1.0 / (1.0 + np.exp(np.clip(-(tt - tj) / 0.0025, -60, 60)))
        off = 0.0 if wd is None else 1.0 / (1.0 + np.exp(np.clip(-(tt - tj - wd) / 0.0025, -60, 60)))
        lf = lf + semis / 12.0 * (on - off)
    return 2.0 ** lf


def scream(dur, rng, f0_pts, amp_pts, vowel="a", vowel_pts=None, chaos_pts=None, ct=0.32, ca=0.45, jumps=(),
           flutter=0.025, flutter_rate=12.0, biphon=None, rough=None, breath=0.35, rasp=0.0, oq=0.45,
           closure=0.8, scale=1.0, bwr=0.13, tilt_db=0.0, os=4, jitter=0.004, shimmer=0.05, f1_track=1.05):
    """Scream / shriek / roar with nonlinear phenomena.

    f0_pts, amp_pts : [(t, value)] keyframes (f0 interpolated in log2)
    chaos_pts       : [(t, r)] logistic-map parameter (0 = off; 3.2 subharmonic,
                      3.5 period-4, 3.7-3.95 chaos)
    jumps           : [(t, semitones, width_s or None)] sudden frequency jumps
    biphon          : dict(ratio=[(t, ratio)], amp=[(t, a)]): a second,
                      independent vibrator (biphonation, two pitches at once)
    rough           : (depth, rate_hz) explicit 30-150 Hz amplitude modulation
    vowel / vowel_pts: formant set (SCREAM_F keys) or [(t, vowel)] morph
    Returns (signal, f0)."""
    n = ns(dur)
    tt = np.arange(n) / SR
    f0 = _contour(n, f0_pts, rng, flutter, flutter_rate, jumps)
    amp = dsp.env_pts(n, amp_pts)
    if chaos_pts:
        r = dsp.env_pts(n, chaos_pts)
        on = (r > 0).astype(float)
    else:
        r, on = 0.0, 0.0
    e, u = glottal(f0, amp, rng, os=os, oq=oq, closure=closure, chaos_r=r, chaos_t=ct * on, chaos_a=ca * on,
                   jitter=jitter, shimmer=shimmer)
    if biphon:
        ratio = dsp.env_pts(n, biphon["ratio"])
        a2 = dsp.env_pts(n, biphon["amp"])
        e2, u2 = glottal(f0 * ratio, amp * a2, rng, os=os, oq=oq * 0.9, closure=closure,
                         chaos_r=r * 0.98 if chaos_pts else 0.0, chaos_t=ct * on * 0.7, chaos_a=ca * on,
                         jitter=jitter * 1.5, shimmer=shimmer, x0=0.61)
        e, u = e + e2, u + u2
    w = amp > 1e-3 * (amp.max() + 1e-12)
    er = np.sqrt(np.mean(e[w] ** 2)) + 1e-12 if w.any() else 1.0
    exc = e / er
    un = u / (np.max(np.abs(u)) + 1e-12)
    if breath > 0:
        nz = rng.standard_normal(n) * (0.3 + 0.7 * np.clip(un, 0, 1)) * amp
        exc = exc + breath * nz / (np.sqrt(np.mean(nz[w] ** 2)) + 1e-12)
    if rasp > 0:  # turbulent rasp that flares with the chaos
        rc = np.clip((np.asarray(r) - 3.5) / 0.4, 0, 1) if chaos_pts else np.zeros(n)
        nz = dsp.lowpass(rng.standard_normal(n), 6000, 2) * amp * (0.3 + rc)
        exc = exc + rasp * nz / (np.sqrt(np.mean(nz[w] ** 2)) + 1e-12)
    f0_at = lambda t: np.interp(t, tt, f0)
    if vowel_pts:
        vt_ = np.array([p[0] for p in vowel_pts])
        vsets = [SCREAM_F[p[1]] for p in vowel_pts]
        f1r = [F1_RANGE[p[1]] for p in vowel_pts]
    else:
        vt_ = np.array([0.0])
        vsets = [SCREAM_F[vowel]]
        f1r = [F1_RANGE[vowel]]

    def gain(f, t):
        t = np.asarray(t, float)
        if len(vt_) > 1:
            w2 = np.clip(np.interp(t, vt_, np.arange(len(vt_))), 0, len(vt_) - 1)
        else:
            w2 = np.zeros_like(t)
        lo = np.floor(w2).astype(int)
        hi = np.minimum(lo + 1, len(vt_) - 1)
        fr = w2 - lo
        f1lo = np.array([f1r[i][0] for i in range(len(f1r))])
        f1hi = np.array([f1r[i][1] for i in range(len(f1r))])
        F1 = np.clip(f0_at(t) * f1_track, f1lo[lo] * (1 - fr) + f1lo[hi] * fr, f1hi[lo] * (1 - fr) + f1hi[hi] * fr) * scale
        g = 1.0 / (1.0 + ((f - F1) / (np.maximum(F1 * 0.35, 120.0) / 2.0)) ** 2)
        for j in range(5):
            Fa = np.array([vsets[i][j][0] for i in range(len(vsets))])
            La = np.array([vsets[i][j][1] for i in range(len(vsets))])
            F = (Fa[lo] * (1 - fr) + Fa[hi] * fr) * scale
            L = La[lo] * (1 - fr) + La[hi] * fr
            g = g + dsp.db(L) / (1.0 + ((f - F) / (F * bwr / 2.0)) ** 2)
        g = g + dsp.db(-34.0)
        if tilt_db:
            g = g * (np.maximum(f, 50.0) / 1000.0) ** (tilt_db / 6.0206)
        return g

    y = dsp.spectral_shape(exc, lambda t, f: gain(f[:, None], t[None, :]), nfft=1024, hop=256)
    if rough:
        y = rough_mod(y, rng, *rough)
    return y, f0


def rough_mod(y, rng, depth=0.4, rate=70.0, chaos=0.5, r=3.86):
    """Roughness: amplitude modulation in the 30-150 Hz alarm band, part
    sinusoidal with a wandering rate (like ventricular folds flapping against
    the voice), part driven by a logistic map in its chaotic regime iterated
    at 2x rate (irregular, never settling).  depth may be an array."""
    n = len(y)
    rate_t = rate * (1.0 + 0.12 * dsp.smooth(n, rng, 3.0))
    ph = np.cumsum(rate_t) / SR
    sinm = 0.5 + 0.5 * np.cos(TAU * ph)
    k = int(n / SR * rate * 2) + 3
    xs = dsp.logistic(k, r, x0=rng.uniform(0.2, 0.8))
    cm = np.interp(np.arange(n) / SR * rate * 2, np.arange(k), xs)
    m = (1.0 - chaos) * sinm + chaos * cm
    depth = np.broadcast_to(np.asarray(depth, float), (n,))
    return y * ((1.0 - depth) + depth * m)


# --------------------------------------------------------------------------
# Japanese syllable timeline, shared by whisper() and speak()
# --------------------------------------------------------------------------
_VOW = "aiueo"
_CONS = ["sh", "ch", "ts", "ky", "gy", "ny", "ry", "hy", "my", "by", "py", "k", "g", "s", "z", "t", "d", "n", "h",
         "f", "b", "p", "m", "y", "r", "w", "j"]
_UNVOICED = ("sh", "ch", "ts", "s", "k", "ky", "h", "hy", "f", "p", "py", "t")


def parse(script):
    """'fu ri ka e q te' -> [(cons, vowel) | (',',) | ('.',) | ('q',) | ('-',) | ('N',) | ('|',)]"""
    toks = []
    for word in script.split():
        if word in (",", ".", "q", "-", "N", "|"):
            toks.append((word,))
            continue
        cons = ""
        for c in _CONS:
            if word.startswith(c) and len(word) > len(c):
                cons = c
                break
        v = word[len(cons):]
        toks.append((cons, v[0] if v and v[0] in _VOW else "u"))
    return toks


def timeline(script, rng, table=VF, scale=1.0, tempo=1.0, bw=1.6, f1=1.12, voiced=False, slur=0.0, durs=None,
             gains=None, pauses=None):
    """Builds formant keys, amplitude keys and consonant events for a line.
    Returns dict(vkeys, vamp, namp, cons, t_end, syl) where vamp is the
    voiced (speech) / vowel (whisper) amplitude, namp the unvoiced-vowel
    noise amplitude (devoiced vowels in speech), syl the syllable list
    [(t_onset, t_vowel_end, word_index, vowel)].
    durs   : optional vowel duration (s) for every syllable token, in order (else random)
    gains  : optional loudness (0..1) for every syllable token
    pauses : optional dict {token index of ',' or '.': seconds}"""
    toks = parse(script)
    syl_k = 0

    def S(v, **kw):
        st = fs(v, table, scale, bw=kw.get("bw", bw), f1=kw.get("f1", f1))
        if slur > 0:
            st = blend(st, fs("x", table, scale, bw=bw, f1=f1), slur)
        return st

    t = 0.06
    prev_v = "x"
    vkeys = [(0.0, S("x"))]
    vamp, namp = [(0.0, 0.0)], [(0.0, 0.0)]
    cons, syl = [], []
    word = 0
    vg = 1.0 if voiced else 0.0  # voicing gain of voiced consonant segments
    for idx, tok in enumerate(toks):
        k0 = tok[0]
        if k0 == "|":
            word += 1
            continue
        if k0 in (",", "."):
            vamp += [(t + 0.025, 0.0)]
            namp += [(t + 0.025, 0.0)]
            if pauses is not None and idx in pauses:
                t += pauses[idx]
            else:
                t += (rng.uniform(0.18, 0.32) if k0 == "," else rng.uniform(0.35, 0.6)) * tempo
            vamp += [(t, 0.0)]
            namp += [(t, 0.0)]
            word += 1
            continue
        if k0 == "q":
            vamp += [(t + 0.015, 0.0)]
            namp += [(t + 0.015, 0.0)]
            t += rng.uniform(0.07, 0.1) * tempo
            vamp += [(t, 0.0)]
            namp += [(t, 0.0)]
            continue
        if k0 == "-":
            t += rng.uniform(0.11, 0.17) * tempo
            vamp += [(t, 0.85)]
            vkeys.append((t, S(prev_v)))
            continue
        if k0 == "N":
            vkeys.append((t + 0.03, S("n", bw=1.3)))
            vamp += [(t + 0.03, 0.4)]
            t += rng.uniform(0.08, 0.12) * tempo
            vamp += [(t, 0.35)]
            continue
        c, v = tok
        nxt = toks[idx + 1] if idx + 1 < len(toks) else (".",)
        devoiced = v in "iu" and c in _UNVOICED and (len(nxt) == 1 or nxt[0] in _UNVOICED or nxt[0] in (",", ".", "q"))
        vdur = rng.uniform(0.075, 0.125) * tempo * (0.6 if devoiced else 1.0)
        if durs is not None:
            vdur = durs[syl_k] * tempo * (0.6 if devoiced else 1.0)
        sg = 1.0 if gains is None else float(gains[syl_k])
        syl_k += 1
        t_on = t
        if c in ("k", "t", "p", "g", "d", "b", "ky", "gy", "ch", "py", "by"):
            vamp += [(t + 0.012, 0.0 if c not in ("g", "d", "b", "gy", "by") else 0.15 * vg)]
            namp += [(t + 0.012, 0.0)]
            t += rng.uniform(0.03, 0.045) * tempo
            vamp += [(t, 0.0 if c not in ("g", "d", "b", "gy", "by") else 0.12 * vg)]
            cons.append(("burst", t, c))
            t += 0.012
            if c == "ch":
                cons.append(("sh", t, rng.uniform(0.05, 0.07) * tempo))
                t += 0.05 * tempo
            elif c in ("k", "t", "p", "ky", "py") and voiced:
                cons.append(("asp", t, 0.025 * tempo))
                t += 0.02 * tempo
        elif c in ("s", "z", "ts"):
            if c == "ts":
                vamp += [(t + 0.01, 0.0)]
                t += 0.03 * tempo
                cons.append(("burst", t, "t"))
                t += 0.01
            d = rng.uniform(0.07, 0.11) * tempo
            vamp += [(t, 0.35 * vg if c == "z" else 0.0)]
            cons.append(("s", t, d))
            t += d * 0.85
        elif c in ("sh", "j"):
            d = rng.uniform(0.08, 0.12) * tempo
            vamp += [(t, 0.35 * vg if c == "j" else 0.0)]
            cons.append(("sh", t, d))
            t += d * 0.85
        elif c in ("h", "f", "hy"):
            d = rng.uniform(0.05, 0.08) * tempo
            cons.append(("f" if c == "f" else "h", t, d))
            vamp += [(t, 0.0 if voiced else 0.15)]
            t += d * 0.7
        elif c in ("m", "n", "ny", "my"):
            nas = "m" if c in ("m", "my") else "n"
            vkeys.append((t, S(nas, bw=1.3)))
            vamp += [(t, 0.45 if voiced else 0.2)]
            t += rng.uniform(0.04, 0.065) * tempo
            vkeys.append((t, S(nas, bw=1.3)))
            vamp += [(t, 0.5 if voiced else 0.25)]
            if c in ("ny", "my"):
                vkeys.append((t + 0.02, S("i")))
        elif c in ("r", "ry"):
            vamp += [(t, 0.75 if voiced else 0.3), (t + 0.012, 0.35 if voiced else 0.2)]
            vkeys.append((t + 0.012, blend(S(prev_v), S("e"), 0.6)))
            t += 0.022 * tempo
        elif c in ("y", "w"):
            g = "i" if c == "y" else "u"
            vkeys.append((t, S(g)))
            vamp += [(t, 0.55 if voiced else 0.4)]
            t += 0.045 * tempo
        vkeys.append((t + 0.025, S(v)))
        if devoiced and voiced:
            vamp += [(t + 0.01, 0.0)]
            namp += [(t, 0.0), (t + 0.02, 0.55 * sg), (t + vdur - 0.01, 0.4 * sg), (t + vdur + 0.02, 0.0)]
        else:
            vamp += [(t + 0.02, (1.0 if not devoiced else 0.45) * sg)]
        t += vdur
        vkeys.append((t, S(v)))
        if not (devoiced and voiced):
            vamp += [(t - 0.01, (0.85 if not devoiced else 0.35) * sg)]
        syl.append((t_on, t, word, v))
        prev_v = v
    t_end = t + 0.18
    vamp += [(t + 0.07, 0.0), (t_end, 0.0)]
    namp += [(t_end, 0.0)]
    vkeys.append((t_end, vkeys[-1][1]))
    return dict(vkeys=vkeys, vamp=vamp, namp=namp, cons=cons, t_end=t_end, syl=syl)


def consonants(n, events, rng, scale=1.0, hiss=1.0, close=0.0):
    """Render consonant noises.  close > 0 adds low 'breath blast' puffs on
    plosives / fricatives (air hitting the ear or the microphone)."""
    out = np.zeros(n)
    for ev in events:
        kind, t0 = ev[0], ev[1]
        i0 = ns(t0)
        if kind == "burst":
            c = ev[2]
            L = ns(0.02)
            b = rng.standard_normal(L) * np.exp(-np.arange(L) / (0.004 * SR))
            fc = {"k": 2100, "ky": 3000, "g": 1800, "gy": 2600, "t": 4300, "d": 3600, "ch": 3800,
                  "p": 900, "py": 1200, "b": 800, "by": 1100}.get(c, 3000) * scale
            b = dsp.reson(b, fc, 1.5) * 2.2 + 0.2 * b
            dsp.place(out, b * 0.55, i0)
            if close > 0 and c in ("p", "b", "t", "k", "py", "by"):
                m = ns(0.06)
                puff = dsp.lowpass(rng.standard_normal(m), 180, 2) * dsp.env_ad(m, 0.004, 0.018)
                dsp.place(out, puff * close * 2.5, i0)
        else:
            d = ev[2]
            L = max(ns(d), 8)
            e = np.sin(np.pi * np.linspace(0, 1, L)) ** 0.7
            nz = rng.standard_normal(L)
            if kind == "s":
                y = dsp.bandpass(nz, 4600 * scale, 10500, 3) * 0.8
            elif kind == "sh":
                y = dsp.bandpass(nz, 1900 * scale, 6400 * scale, 2)
                y = dsp.peq(y, 2800 * scale, 2.0, 7.0) * 0.72
            elif kind == "h":
                y = dsp.bandpass(nz, 500, 6000, 1) * 0.35
            elif kind == "asp":
                y = dsp.bandpass(nz, 800, 5000, 1) * 0.3
            else:  # f (bilabial, soft in Japanese)
                y = dsp.bandpass(nz, 700, 6500, 1) * 0.2
            dsp.place(out, y * e * hiss, i0)
            if close > 0 and kind in ("f", "h"):
                puff = dsp.lowpass(rng.standard_normal(L), 220, 2) * e
                dsp.place(out, puff * close * 1.8, i0)
    return out


def whisper(script, rng, table=VF, scale=1.0, rate=1.0, intensity=1.0, hiss=1.0, declination=0.5, bw=1.35,
            f1=1.12, tilt_db=1.5, close=0.0, voicing=0.0, f0=None, slur=0.0, air=0.15):
    """Whispered line (vocal-tract-shaped noise + consonant noises).
    voicing > 0 mixes in a faint breathy voice (half-whisper), close > 0 adds
    the breath blasts of a mouth right at your ear."""
    tl = timeline(script, rng, table, scale, 1.0 / rate, bw=bw, f1=f1, voiced=False, slur=slur)
    n = ns(tl["t_end"])
    amp = dsp.env_pts(n, sorted(tl["vamp"], key=lambda p: p[0]))
    amp = np.maximum(dsp.lowpass0(amp, 60.0), 0)
    amp *= np.linspace(1.0, 1.0 - 0.45 * declination, n) * (1.0 + 0.25 * dsp.smooth(n, rng, 4.0))
    body = vt_noise(n, tl["vkeys"], amp, rng, tilt_db=tilt_db, bw=1.0, hp=280.0)
    if voicing > 0:
        ff = np.full(n, f0 or (230.0 if table is VF else 120.0)) * dsp.cents(
            np.linspace(40, -160, n) + 30 * dsp.smooth(n, rng, 3.0))
        vv = voice(ff, amp, tl["vkeys"], rng, tilt=-13.0, fmax=5000, jitter=0.03, shimmer=0.2, breath=1.2,
                   loud_comp=0.6)
        body = body + vv / (np.std(vv) + 1e-9) * np.std(body) * voicing
    if air > 0:  # turbulent breath above the formants (what makes a whisper 'at your ear')
        hf = dsp.bandpass(rng.standard_normal(n), 5000, 11000, 2) * amp
        body = body + hf / (np.std(hf) + 1e-9) * np.std(body) * air
    out = body + consonants(n, tl["cons"], rng, scale, hiss, close) * 0.9
    return out * intensity


def speak(script, rng, table=VM, scale=1.0, f0=120.0, rate=1.0, accent=2.5, declination=3.0, breath=0.25,
          jitter=0.012, shimmer=0.07, tilt=-8.0, subharm=0.0, slur=0.0, cons_gain=1.0, fmax=7000.0, wander=1.0,
          f0_end=None):
    """Voiced speech-like line (unintelligible Japanese-sounding speech /
    mumble).  accent / declination in semitones."""
    tl = timeline(script, rng, table, scale, 1.0 / rate, bw=1.0, f1=1.0, voiced=True, slur=slur)
    n = ns(tl["t_end"])
    vamp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["vamp"], key=lambda p: p[0])), 45.0), 0)
    namp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["namp"], key=lambda p: p[0])), 45.0), 0)
    # pitch: phrase declination + simple Tokyo-style word accent (low-high ... fall)
    pts = [(0.0, 0.0)]
    words = {}
    for (ta, tb, w, v) in tl["syl"]:
        words.setdefault(w, []).append((ta, tb))
    for w, ss in words.items():
        kern = rng.integers(1, len(ss) + 1) if len(ss) > 1 else 1
        for j, (ta, tb) in enumerate(ss):
            lvl = 0.0 if j == 0 else (accent if j <= kern else -0.6 * accent)
            pts.append((ta + 0.02, lvl + rng.normal(0, 0.5)))
            pts.append((tb, lvl + rng.normal(0, 0.4)))
    pts.append((tl["t_end"], -accent))
    pts = sorted(pts, key=lambda p: p[0])
    st = dsp.env_pts(n, pts)
    st = dsp.lowpass0(st, 7.0)
    st += -declination * np.linspace(0, 1, n) + wander * 0.6 * dsp.smooth(n, rng, 3.0)
    ff = f0 * 2.0 ** (st / 12.0)
    if f0_end is not None:
        ff *= np.linspace(1.0, f0_end / f0, n)
    y = voice(ff, vamp, tl["vkeys"], rng, tilt=tilt, fmax=fmax, jitter=jitter, shimmer=shimmer, breath=breath,
              subharm=subharm, loud_comp=0.75)
    if namp.max() > 0:
        y = y + vt_noise(n, tl["vkeys"], namp, rng, tilt_db=1.0, bw=1.4) * 0.35
    y = y + consonants(n, tl["cons"], rng, scale, 1.0) * 0.45 * cons_gain
    return y


# --------------------------------------------------------------------------
# breath, laughter, mouth noises
# --------------------------------------------------------------------------
def breath(rng, kind, d, table=VM, tremor=0.0, trem_rate=8.0, voiced=0.0, f0=125.0, vowel_in="x", vowel_out="a",
           hiss=0.0, wet=0.0, scale=1.0, rasp=0.0):
    """One breath phase: kind 'in' (higher, hissier) or 'out' (lower, voiced).
    tremor = trembling, wet = saliva crackle, rasp = throat flutter."""
    m = ns(d)
    x = np.linspace(0, 1, m)
    if kind == "in":
        env = np.sin(np.pi * x ** 0.8) ** 1.1
        keys = [(0, fs(vowel_in, table, scale, f1=0.85, bw=1.5)), (d, fs("i" if hiss else vowel_in, table, scale, bw=1.5))]
        tilt = 2.5
    else:
        env = np.minimum(1, x * d / 0.05) * (1 - x) ** 1.2
        keys = [(0, fs(vowel_out, table, scale, f1=0.9)), (d, fs("x", table, scale))]
        tilt = -1.0
    if tremor > 0:
        rate = trem_rate * (1 + 0.15 * dsp.smooth(m, rng, 1.0))
        tr = 0.5 + 0.5 * np.sin(TAU * np.cumsum(rate) / SR + rng.random() * 6)
        env = env * ((1 - tremor) + tremor * tr ** 1.5)
    s = vt_noise(m, keys, env, rng, tilt_db=tilt, bw=1.6)
    if hiss > 0:
        s += dsp.bandpass(rng.standard_normal(m), 3500, 9000, 2) * env * hiss
    if voiced > 0:
        ff = f0 * dsp.cents(dsp.env_pts(m, [(0, 60), (d, -120)]) + 25 * dsp.smooth(m, rng, 9.0))
        vv = voice(ff, env, keys, rng, tilt=-12, fmax=4000, breath=0.8, jitter=0.02, shimmer=0.15)
        s = s + vv / (np.std(vv) + 1e-9) * np.std(s) * voiced
    if rasp > 0:
        fl = creak(m, 38 + 10 * dsp.smooth(m, rng, 2.0), env, rng, vowel="x", table=table, jitter=0.4)
        s += fl * rasp * np.std(s) / (np.std(fl) + 1e-9)
    if wet > 0:
        s += wet_crackle(d, rng, density=60 * wet, env=env) * np.std(s) * 2.5
    return dsp.fade(s, 0.005, 0.03)


def wet_crackle(d, rng, density=80.0, env=None):
    """Saliva / mucus crackle: tiny bubble pops (rising micro-chirps) and clicks."""
    n = ns(d)
    y = np.zeros(n)
    k = rng.poisson(density * d)
    for _ in range(k):
        t0 = rng.uniform(0, d)
        i0 = ns(t0)
        if env is not None and rng.random() > env[min(i0, n - 1)] * 1.2:
            continue
        m = ns(rng.uniform(0.002, 0.008))
        tt = np.arange(m) / SR
        f = rng.uniform(900, 4200) * (1 + 1.2 * tt / (tt[-1] + 1e-9))
        p = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-tt / rng.uniform(0.0008, 0.002))
        dsp.place(y, p * rng.pareto(3.0) * 0.4, i0)
    return y


def mouth_clicks(d, rng, rate=2.0, level=1.0):
    """Wet lip / tongue clicks (ultra-close, ASMR-intimate)."""
    n = ns(d)
    y = np.zeros(n)
    t = rng.uniform(0.02, 0.2)
    while t < d - 0.02:
        L = ns(0.006)
        c = rng.standard_normal(L) * np.exp(-np.arange(L) / (rng.uniform(0.0004, 0.0012) * SR))
        c = dsp.reson(c, rng.uniform(1400, 4800), rng.uniform(2, 6), 3.0) + 0.3 * c
        dsp.place(y, c * rng.uniform(0.3, 1.0), t)
        if rng.random() < 0.4:  # tongue release double click
            dsp.place(y, c * rng.uniform(0.2, 0.5), t + rng.uniform(0.01, 0.03))
        t += rng.exponential(1.0 / rate) + 0.05
    return y * level


def laugh(rng, n_syl=5, f0=200.0, decline=0.03, gap=(0.05, 0.09), syl=(0.09, 0.13), vowel="a", table=VM,
          scale=1.0, breath=0.9, tilt=-10.0, sub=0.0, broken=0.0, nasal=False, jitter=0.012, start=0.15,
          f0_jitter_c=40.0, h_gain=1.0):
    """Laugh / giggle: a train of breathy voiced syllables, each with a
    falling pitch and an 'h' onset.  broken > 0 adds frequency jumps and
    stutters (glitchy, wrong).  Returns mono."""
    total = start + n_syl * (syl[1] + gap[1]) + 0.8
    n = ns(total)
    ff = np.full(n, f0)
    amp = np.zeros(n)
    subv = np.zeros(n)
    t = start
    onsets = []
    for k in range(n_syl):
        sd = rng.uniform(*syl) * (1.3 if k == n_syl - 1 else 1.0)
        i0, i1 = ns(t), min(n, ns(t + sd))
        m = i1 - i0
        st = f0 * (1 - decline * k) * dsp.cents(rng.uniform(-f0_jitter_c, f0_jitter_c))
        if broken > 0 and rng.random() < broken:
            st *= 2.0 ** (rng.choice([-1, 1]) * rng.uniform(4, 9) / 12.0)
        ff[i0:i1] = np.linspace(st, st * 0.84, m)
        a = dsp.env_ad(m, 0.02, sd * 0.45) * (1 - 0.1 * k)
        amp[i0:i1] = a
        subv[i0:i1] = sub
        onsets.append(t)
        if broken > 0 and rng.random() < broken * 0.6:  # stutter: repeat a fragment
            t += sd * 0.5
            continue
        t += sd + rng.uniform(*gap)
    ff = dsp.lowpass0(ff, 30)
    v = "m" if nasal else vowel
    keys = [(0, fs(v, table, scale)), (total, fs(v, table, scale))]
    y = voice(ff, amp, keys, rng, tilt=tilt, fmax=7000, jitter=jitter, shimmer=0.12, breath=breath,
              subharm=subv, noise_mod=0.5, loud_comp=0.7)
    ref = np.std(y[amp > 0.05]) if np.any(amp > 0.05) else 1.0
    for t0 in onsets:
        m = ns(0.05)
        if nasal:  # air through the nose
            h = dsp.bandpass(rng.standard_normal(m), 250, 1200, 2) * dsp.env_ad(m, 0.02, 0.025) * 0.12
        else:
            h = dsp.bandpass(rng.standard_normal(m), 500, 4000, 2) * dsp.env_ad(m, 0.02, 0.025) * 0.5
        dsp.place(y, h * ref * h_gain, max(0.0, t0 - 0.035))
    m = ns(0.6)
    ex = vt_noise(m, [(0, fs("x", table, scale))], dsp.env_ad(m, 0.05, 0.18), rng, tilt_db=0) * ref * 0.9
    dsp.place(y, ex, t + 0.02)
    return y


# --------------------------------------------------------------------------
# the call: 「おーい」 shouted by a man
# --------------------------------------------------------------------------
def call_oi(rng, dur_o=0.95, dur_i=0.40, f0=215.0, rise=1.12, end_drop=0.86, breath=0.32, scale=1.0, tilt=-3.8,
            vib=14.0, onset=0.06, tail=0.45, crack=None):
    """Shouted 「おーい」: hard glottal onset, a long /o/ (F1 raised by the open
    jaw of shouting) with the rising calling intonation and a little vibrato,
    a quick formant glide to /i/ (F2 ~850 -> 2300 Hz), a slight pitch drop at
    the end and the devoiced breath that follows.  crack = (t, semitones) adds
    a voice break.  Dry, mono."""
    t_o = onset
    t_g = t_o + dur_o
    t_end = t_g + dur_i
    total = t_end + tail
    n = ns(total)
    pts = [(0, f0 * 0.8), (t_o, f0 * 0.84), (t_o + 0.12, f0), (t_g - 0.12, f0 * rise), (t_g + 0.06, f0 * rise * 1.04),
           (t_end - 0.12, f0 * rise * 0.97), (t_end, f0 * rise * end_drop), (total, f0 * rise * end_drop * 0.95)]
    jumps = [(crack[0], crack[1], 0.09)] if crack else ()
    ff = _contour(n, pts, rng, flutter=0.006, flutter_rate=6.0, jumps=jumps)
    tt = np.arange(n) / SR
    ff = ff * dsp.cents(vib * np.sin(TAU * 5.2 * tt + rng.random() * 6) * np.clip((tt - t_o - 0.3) / 0.4, 0, 1))
    amp = dsp.env_pts(n, [(0, 0), (t_o, 0), (t_o + 0.03, 0.8), (t_o + 0.16, 1.0), (t_g - 0.06, 0.9), (t_g + 0.07, 0.82),
                          (t_g + 0.16, 0.95), (t_end - 0.14, 0.78), (t_end, 0.0), (total, 0)])
    o = fs("o", VM, scale, f1=1.13)
    i_ = fs("i", VM, scale, f1=1.1)
    keys = [(0, o), (t_g - 0.02, o), (t_g + 0.17, i_), (total, i_)]
    y = voice(ff, amp, keys, rng, tilt=tilt, fmax=9000, jitter=0.008, shimmer=0.06, breath=breath, hpc=0.4,
              loud_comp=0.8, breath_tilt=0.5)
    m = ns(0.24)
    br = vt_noise(m, [(0, i_)], dsp.env_pts(m, [(0, 0.0), (0.04, 1.0), (0.24, 0.0)]) ** 2, rng, tilt_db=0.0, bw=1.3)
    dsp.place(y, br * np.std(y[amp > 0.3]) * 0.22, t_end - 0.06)
    return y


# --------------------------------------------------------------------------
# lines with explicit timing and intonation (REISATSU's Japanese voice lines)
# --------------------------------------------------------------------------
def speak_line(script, rng, pitch, durs, table=VF, scale=1.0, f0=230.0, gains=None, pauses=None, smooth_hz=14.0,
               breath=0.25, jitter=0.01, shimmer=0.06, tilt=-8.0, subharm=0.0, slur=0.0, cons_gain=1.0, fmax=7500.0,
               vibrato=0.0, vib_rate=5.5, tempo=1.0, wander=0.3, hiss=1.0, bw=1.0, f1=1.0):
    """A Japanese line with explicit timing and intonation, so that it is roughly recognisable.

    script : romaji syllables ('i ra q sha i ma se'); ',' '.' pauses, 'q' geminate closure, '-' long vowel,
             'N' moraic nasal
    pitch  : one entry per syllable token (not for , . q - N): semitones above f0, or (start, end) to glide
    durs   : vowel duration (s) of every syllable token
    Returns (signal, f0 track)."""
    tl = timeline(script, rng, table, scale, tempo, bw=bw, f1=f1, voiced=True, slur=slur, durs=durs, gains=gains,
                  pauses=pauses)
    n = ns(tl["t_end"])
    vamp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["vamp"], key=lambda p: p[0])), 45.0), 0)
    namp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["namp"], key=lambda p: p[0])), 45.0), 0)
    pts = []
    for k, (ta, tb, w, v) in enumerate(tl["syl"]):
        p = pitch[k]
        a, b = (float(p), float(p)) if np.isscalar(p) else (float(p[0]), float(p[1]))
        pts.append((ta + 0.01, a))
        pts.append((max(ta + 0.02, tb - 0.01), b))
    if not pts:
        pts = [(0.0, 0.0)]
    pts = [(0.0, pts[0][1])] + pts + [(tl["t_end"], pts[-1][1])]
    st = dsp.env_pts(n, sorted(pts, key=lambda q: q[0]))
    st = dsp.lowpass0(st, smooth_hz)
    if wander > 0:
        st = st + wander * dsp.smooth(n, rng, 3.0)
    ff = f0 * 2.0 ** (st / 12.0)
    if vibrato > 0:
        ff = ff * dsp.cents(vibrato * np.sin(TAU * vib_rate * np.arange(n) / SR + rng.random() * 6))
    y = voice(ff, vamp, tl["vkeys"], rng, tilt=tilt, fmax=fmax, jitter=jitter, shimmer=shimmer, breath=breath,
              subharm=subharm, loud_comp=0.75)
    if namp.max() > 0:
        y = y + vt_noise(n, tl["vkeys"], namp, rng, tilt_db=1.0, bw=1.4) * 0.35
    y = y + consonants(n, tl["cons"], rng, scale, hiss) * 0.45 * cons_gain
    return y, ff


def whisper_line(script, rng, durs, table=VF, scale=1.0, gains=None, pauses=None, intensity=1.0, hiss=1.0,
                 close=0.0, voicing=0.0, f0=None, bw=1.3, f1=1.12, tilt_db=1.5, air=0.15, declination=0.3):
    """whisper() with explicit syllable timing."""
    tl = timeline(script, rng, table, scale, 1.0, bw=bw, f1=f1, voiced=False, durs=durs, gains=gains, pauses=pauses)
    n = ns(tl["t_end"])
    amp = dsp.env_pts(n, sorted(tl["vamp"], key=lambda p: p[0]))
    amp = np.maximum(dsp.lowpass0(amp, 60.0), 0)
    amp *= np.linspace(1.0, 1.0 - 0.45 * declination, n) * (1.0 + 0.2 * dsp.smooth(n, rng, 4.0))
    body = vt_noise(n, tl["vkeys"], amp, rng, tilt_db=tilt_db, bw=1.0, hp=280.0)
    if voicing > 0:
        ff = np.full(n, f0 or (230.0 if table is VF else 120.0)) * dsp.cents(
            np.linspace(40, -160, n) + 30 * dsp.smooth(n, rng, 3.0))
        vv = voice(ff, amp, tl["vkeys"], rng, tilt=-13.0, fmax=5000, jitter=0.03, shimmer=0.2, breath=1.2,
                   loud_comp=0.6)
        body = body + vv / (np.std(vv) + 1e-9) * np.std(body) * voicing
    if air > 0:
        hf = dsp.bandpass(rng.standard_normal(n), 5000, 11000, 2) * amp
        body = body + hf / (np.std(hf) + 1e-9) * np.std(body) * air
    out = body + consonants(n, tl["cons"], rng, scale, hiss, close) * 0.9
    return out * intensity


def hum(rng, notes, table=VF, scale=1.0, vowel="m", breath=0.3, tilt=-11.0, jitter=0.012, shimmer=0.08,
        vibrato=18.0, vib_rate=5.2, scoop=60.0, off_key=None, legato=0.06, fmax=5000.0, tail=0.4):
    """Humming a tune with the mouth closed ('m') or half open ('u').
    notes = [(t_start, dur, freq_hz, vel)].  off_key(k) -> cents for note k (wrong notes)."""
    total = max(t + d for t, d, f, v in notes) + tail
    n = ns(total)
    tt = np.arange(n) / SR
    lf = np.full(n, np.nan)
    amp = np.zeros(n)
    for k, (t0, d, f, v) in enumerate(notes):
        c = off_key(k) if off_key else 0.0
        i0, i1 = ns(t0), min(n, ns(t0 + d))
        m = i1 - i0
        if m <= 0:
            continue
        x = np.arange(m) / SR
        sc = -scoop * np.exp(-x / 0.05)
        lf[i0:i1] = np.log2(f) + (c + sc) / 1200.0
        e = np.minimum(1.0, x / 0.04) * np.minimum(1.0, (d - x) / max(legato, 0.02)) * v
        amp[i0:i1] = np.maximum(amp[i0:i1], np.clip(e, 0, None))
    # hold pitch through gaps
    idx = np.nonzero(~np.isnan(lf))[0]
    lf = np.interp(np.arange(n), idx, lf[idx])
    lf = dsp.lowpass0(lf, 18.0)
    vib = vibrato * np.sin(TAU * vib_rate * tt + rng.random() * 6) * np.clip((tt - 0.2) / 0.5, 0, 1)
    ff = 2.0 ** lf * dsp.cents(vib + 8.0 * dsp.smooth(n, rng, 2.0))
    amp = dsp.lowpass0(amp, 30.0)
    keys = [(0, fs(vowel, table, scale)), (total, fs(vowel, table, scale))]
    y = voice(ff, np.maximum(amp, 0), keys, rng, tilt=tilt, fmax=fmax, jitter=jitter, shimmer=shimmer,
              breath=breath, loud_comp=0.7)
    return y
