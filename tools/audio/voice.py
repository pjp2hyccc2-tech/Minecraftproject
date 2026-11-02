"""Voice synthesis for MEIKON.

* ``voice``   - additive glottal-pulse source (band-limited harmonics whose
                amplitudes follow a cascade formant envelope evaluated at every
                harmonic), jitter / shimmer / sub-harmonics, plus pitch-
                synchronous aspiration noise shaped by the same vocal tract.
* ``vt_noise`` - noise through a time-varying vocal tract (breath, whisper).
* ``whisper``  - syllable engine (romaji-ish scripts) for unintelligible but
                speech-rhythmic Japanese whispers.
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


# --------------------------------------------------------------------------
# whisper syllable engine
# --------------------------------------------------------------------------
_VOW = "aiueo"
_CONS = ["sh", "ch", "ts", "ky", "ny", "ry", "k", "g", "s", "z", "t", "d", "n", "h", "f", "b", "p", "m", "y", "r", "w", "j"]


def _parse(script):
    """'u shi ro , mi tsu ke ta' -> list of tokens (cons, vowel) / ('q',) / (',',) / ('-',)"""
    toks = []
    for word in script.split():
        if word in (",", ".", "q", "-", "N"):
            toks.append((word,))
            continue
        w = word
        cons = ""
        for c in _CONS:
            if w.startswith(c) and len(w) > len(c):
                cons = c
                break
        v = w[len(cons):]
        toks.append((cons, v[0] if v else "u"))
    return toks


def whisper(script, rng, table=VF, scale=1.0, rate=1.0, intensity=1.0, hiss=1.0, declination=0.5):
    """Render a whispered line.  Returns mono signal (unnormalised)."""
    toks = _parse(script)
    # timeline
    segs = []  # (t0, t1, kind, data)
    t = 0.05
    prev_v = "x"
    vkeys = [(0.0, fs("x", table, scale, bw=1.6, f1=1.1))]
    amp_pts = [(0.0, 0.0)]
    cons_events = []
    tempo = 1.0 / rate
    for tok in toks:
        if tok[0] == ",":
            amp_pts += [(t + 0.02, 0.0)]
            t += rng.uniform(0.18, 0.32) * tempo
            amp_pts += [(t, 0.0)]
            continue
        if tok[0] == ".":
            amp_pts += [(t + 0.02, 0.0)]
            t += rng.uniform(0.35, 0.6) * tempo
            amp_pts += [(t, 0.0)]
            continue
        if tok[0] == "q":  # geminate: silent closure
            amp_pts += [(t + 0.015, 0.0)]
            t += rng.uniform(0.07, 0.1) * tempo
            amp_pts += [(t, 0.0)]
            continue
        if tok[0] == "-":  # long vowel
            t += rng.uniform(0.1, 0.16) * tempo
            amp_pts += [(t, 0.8)]
            vkeys.append((t, fs(prev_v, table, scale, bw=1.6, f1=1.1)))
            continue
        if tok[0] == "N":  # moraic nasal
            vkeys.append((t + 0.03, fs("n", table, scale, bw=1.6)))
            amp_pts += [(t + 0.03, 0.35)]
            t += rng.uniform(0.08, 0.12) * tempo
            amp_pts += [(t, 0.3)]
            continue
        c, v = tok
        devoiced = v in "iu" and c in ("sh", "ch", "ts", "s", "k", "h", "f", "p", "t")
        vdur = rng.uniform(0.075, 0.13) * tempo * (0.6 if devoiced else 1.0)
        # consonant part
        if c in ("k", "t", "p", "g", "d", "b", "ky", "ch"):
            amp_pts += [(t + 0.012, 0.0)]
            t += rng.uniform(0.03, 0.045) * tempo
            amp_pts += [(t, 0.0)]
            cons_events.append(("burst", t, c))
            t += 0.012
            if c == "ch":
                cons_events.append(("sh", t, rng.uniform(0.05, 0.07) * tempo))
                t += 0.05 * tempo
        elif c in ("s", "z", "ts"):
            if c == "ts":
                amp_pts += [(t + 0.01, 0.0)]
                t += 0.03 * tempo
                cons_events.append(("burst", t, "t"))
                t += 0.01
            d = rng.uniform(0.07, 0.11) * tempo
            amp_pts += [(t, 0.0)]
            cons_events.append(("s", t, d))
            t += d * 0.85
        elif c in ("sh", "j"):
            d = rng.uniform(0.08, 0.12) * tempo
            amp_pts += [(t, 0.0)]
            cons_events.append(("sh", t, d))
            t += d * 0.85
        elif c in ("h", "f"):
            d = rng.uniform(0.05, 0.08) * tempo
            cons_events.append(("h" if c == "h" else "f", t, d))
            amp_pts += [(t, 0.15)]
            t += d * 0.7
        elif c in ("m", "n", "ny"):
            vkeys.append((t, fs("n" if c != "m" else "m", table, scale, bw=1.6)))
            amp_pts += [(t, 0.2)]
            t += rng.uniform(0.04, 0.06) * tempo
            vkeys.append((t, fs("n" if c != "m" else "m", table, scale, bw=1.6)))
            amp_pts += [(t, 0.25)]
        elif c in ("r", "ry"):
            amp_pts += [(t, 0.25)]
            t += 0.018 * tempo
        elif c in ("y", "w"):
            g = "i" if c == "y" else "u"
            vkeys.append((t, fs(g, table, scale, bw=1.6, f1=1.1)))
            amp_pts += [(t, 0.4)]
            t += 0.04 * tempo
        # vowel
        vkeys.append((t + 0.025, fs(v, table, scale, bw=1.6, f1=1.15)))
        amp_pts += [(t + 0.02, 1.0 if not devoiced else 0.45)]
        t += vdur
        vkeys.append((t, fs(v, table, scale, bw=1.6, f1=1.15)))
        amp_pts += [(t - 0.01, 0.85 if not devoiced else 0.35)]
        prev_v = v
    t_end = t + 0.15
    amp_pts += [(t + 0.06, 0.0), (t_end, 0.0)]
    n = ns(t_end)
    vkeys.append((t_end, vkeys[-1][1]))
    amp = dsp.env_pts(n, sorted(amp_pts, key=lambda p: p[0]))
    amp = dsp.lowpass0(amp, 60.0)
    amp = np.maximum(amp, 0)
    # declination & random syllable accents
    amp *= np.linspace(1.0, 1.0 - 0.45 * declination, n) * (1.0 + 0.25 * dsp.smooth(n, rng, 4.0))
    body = vt_noise(n, vkeys, amp, rng, tilt_db=1.5, bw=1.5, hp=300.0)
    # consonants
    cons = np.zeros(n)
    for ev in cons_events:
        kind, t0 = ev[0], ev[1]
        i0 = ns(t0)
        if kind == "burst":
            c = ev[2]
            L = ns(0.018)
            b = rng.standard_normal(L) * np.exp(-np.arange(L) / (0.004 * SR))
            fc = {"k": 2200, "ky": 3000, "g": 1900, "t": 4200, "d": 3500, "ch": 3800, "p": 900, "b": 800}.get(c, 3000)
            b = dsp.reson(b, fc, 1.5) * 2.2 + 0.2 * b
            dsp.place(cons, b * 0.55, i0)
        elif kind in ("s", "sh", "h", "f"):
            d = ev[2]
            L = ns(d)
            e = np.sin(np.pi * np.linspace(0, 1, L)) ** 0.7
            nzz = rng.standard_normal(L)
            if kind == "s":
                y = dsp.bandpass(nzz, 4500 * scale, 10500, 3) * 1.3
            elif kind == "sh":
                y = dsp.bandpass(nzz, 1900 * scale, 6200 * scale, 2)
                y = dsp.peq(y, 2800 * scale, 2.0, 7.0) * 0.9
            elif kind == "h":
                y = dsp.bandpass(nzz, 500, 6000, 1) * 0.35
            else:
                y = dsp.bandpass(nzz, 900, 9000, 1) * 0.3
            dsp.place(cons, y * e * hiss, i0)
    out = body + cons * 0.9
    return out * intensity


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
