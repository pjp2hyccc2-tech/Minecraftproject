"""花嫁 (the bride) sounds."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, ns


# --------------------------------------------------------------------------
# bride.hum - かごめかごめ hummed in another room (seamless 20 s loop)
# --------------------------------------------------------------------------
def hum(rng, i=1):
    L = 20.0
    n = ns(L)
    octv = {"E": "E4", "G": "G4", "A": "A4", "B": "B4"}
    e = 0.30
    notes = []
    t = 0.12
    for pi, ph in enumerate(ins.KAGOME):
        for ni, (p, d) in enumerate(ph):
            dur = d * e * (1 + rng.uniform(-0.06, 0.06))
            last = ni == len(ph) - 1
            if last:
                dur *= 1.30
            if pi == len(ins.KAGOME) - 1 and last:
                dur = 1.75
            notes.append([t, dur, p, pi, last])
            t += dur
        if pi == 7:
            t += 0.25  # short breath in the middle of the song
    sc = (L - 1.05 - 0.12) / (t - 0.12)
    for nt in notes:
        nt[0] = 0.12 + (nt[0] - 0.12) * sc
        nt[1] *= sc
    lp = np.full(n, np.log2(dsp.hz("A4", -28)))
    for k, (st, du, p, pi, last) in enumerate(notes):
        # slightly flat, getting flatter through the song; one wrong note
        c = -28 + rng.uniform(-10, 8) - 12 * (st / L)
        if pi == 10 and k == sum(len(x) for x in ins.KAGOME[:10]) + 5:
            c += 70  # the B in うしろのしょうめん comes out sharp - wrong
        lp[ns(st): ns(st + du)] = np.log2(dsp.hz(octv[p], c))
    # hold pitch across the gaps (no swoops in silence)
    last_v = lp[0]
    lp = dsp.lowpass0(lp, 9.0)
    for k, (st, du, p, pi, last) in enumerate(notes):
        if k > 0 and notes[k - 1][2] != p:
            i0 = ns(st)
            m = ns(0.09)
            lp[i0: i0 + m] -= np.linspace(0.045, 0, m) * (1.0 if p in "AB" else 0.5)
        if du > 0.45:
            i0, i1 = ns(st), ns(st + du)
            kk = np.arange(i1 - i0) / SR
            depth = np.clip((kk - 0.22) / 0.4, 0, 1) * 0.024
            rate = 5.1 + 0.4 * rng.standard_normal()
            lp[i0:i1] += depth * np.sin(dsp.TAU * rate * kk + rng.uniform(0, 6)) * (1 + 0.3 * dsp.smooth(i1 - i0, rng, 2.0))
    st, du = notes[-1][0], notes[-1][1]
    i0, i1 = ns(st + 0.55), ns(st + du)
    lp[i0:i1] -= np.linspace(0, 1, i1 - i0) ** 1.6 * 0.12  # だあれ sags down like a sigh
    f0 = 2.0 ** lp
    amp = np.zeros(n)
    for k, (st, du, p, pi, last) in enumerate(notes):
        i0, i1 = ns(st), ns(st + du)
        m = i1 - i0
        kk = np.arange(m) / SR
        a = np.minimum(1, kk / 0.06) * (1 - 0.15 * np.clip((kk - 0.1) / du, 0, 1))
        rep = k > 0 and notes[k - 1][2] == p and ns(notes[k - 1][0] + notes[k - 1][1]) >= i0 - 5
        a *= (1 - 0.55 * np.exp(-kk / 0.035)) if rep else (1 - 0.2 * np.exp(-kk / 0.04))
        a *= 0.82 + 0.18 * np.sin(np.pi * ((k % 7) / 7.0))
        amp[i0:i1] = a
        if last:
            rel = ns(min(0.18, du * 0.4))
            amp[i1 - rel: i1] *= np.linspace(1, 0, rel) ** 1.5
    st, du = notes[-1][0], notes[-1][1]
    i0, i1 = ns(st), ns(st + du)
    amp[i0:i1] *= 0.78 * np.clip(1 - (np.arange(i1 - i0) / SR - 0.5) / (du - 0.5), 0, 1) ** 1.2
    amp = np.maximum(dsp.lowpass0(amp, 25.0), 0)
    amp *= 1 + 0.06 * dsp.smooth(n, rng, 3.0)
    m_ = V.fs("m")
    keys = [(0.0, m_), (6.0, V.blend(m_, V.fs("n"), 0.5)), (9.0, m_), (st, m_),
            (st + 0.35, V.blend(m_, V.fs("o"), 0.65)), (st + 1.2, V.blend(m_, V.fs("u"), 0.5)), (L, m_)]
    y = V.voice(f0, amp, keys, rng, tilt=-9.0, fmax=7000, jitter=0.0045, shimmer=0.05, breath=0.05,
                noise_mod=0.7, loud_comp=0.85)
    # inhale before the loop restarts (and a tiny one mid-song, after ばんに)
    kb = sum(len(p) for p in ins.KAGOME[:8]) - 1
    for b0, b1, g in [(L - 0.86, L - 0.12, 0.22), (notes[kb][0] + notes[kb][1] + 0.02, notes[kb + 1][0] - 0.01, 0.1)]:
        bi0, bi1 = ns(b0), ns(b1)
        if bi1 - bi0 < ns(0.08):
            continue
        m = bi1 - bi0
        benv = np.sin(np.pi * np.linspace(0, 1, m)) ** 1.5
        bk = [(0, V.fs("a", f1=0.8)), (1, V.fs("x"))]
        y[bi0:bi1] += V.vt_noise(m, bk, benv, rng, tilt_db=1.0, bw=1.8) * g
    y = dsp.lowpass(y, 4800, 2)
    # faint room tone so the loop never drops to digital silence
    y += dsp.lowpass(dsp.colored(n, rng, -6), 600) * 0.0025
    ir = dsp.make_ir(0.95, rng, predelay=0.012, lp_hz=3600, hf_ratio=0.35)
    y = dsp.circ_reverb(y, ir, wet=0.55, dry=0.8)
    return dsp.dc_block(y, circular=True)


# --------------------------------------------------------------------------
# bride.step
# --------------------------------------------------------------------------
def step(rng, i=1):
    dur = 1.2 if i == 6 else 0.9
    n = ns(dur)
    y = np.zeros(n)
    size = rng.uniform(1.15, 1.45)
    heel = ins.wood_hit(rng, ins.WOOD_FLOOR, size=size, hardness=0.12, vel=1.0, dur=0.5, noise=0.15)
    heel = dsp.lowpass(heel, 1100, 2)
    dsp.place(y, heel, 0.015)
    t_toe = 0.015 + rng.uniform(0.07, 0.12)
    toe = ins.wood_hit(rng, ins.WOOD_FLOOR, size=size * 0.9, hardness=0.2, vel=0.45, dur=0.4, noise=0.2)
    dsp.place(y, dsp.lowpass(toe, 1600, 2), t_toe)
    # tabi cloth on wood
    m = ns(0.16)
    cloth = dsp.lowpass(dsp.bandpass(rng.standard_normal(m), 600, 3000, 2), 3000, 2) * dsp.env_ad(m, 0.015, 0.045)
    dsp.place(y, cloth * 0.14, t_toe - 0.01)
    if i in (4, 5):
        cr = ins.creak(0.55, rng, [(0, 25), (0.15, 70 + 30 * (i - 4)), (0.4, 35), (0.55, 20)],
                       [(0, 0), (0.06, 0.8), (0.35, 0.6), (0.55, 0)], pitch=0.65 + 0.15 * (i - 4), jitter=0.3)
        dsp.place(y, dsp.lowpass(cr, 2500) * 0.35, 0.06)
    if i == 6:  # she drags the other foot
        m = ns(0.5)
        drag = dsp.bandpass(rng.standard_normal(m), 350, 2500, 2)
        drag *= dsp.env_pts(m, [(0, 0), (0.12, 1.0), (0.4, 0.7), (0.5, 0)]) * (1 + 0.5 * dsp.smooth(m, rng, 25.0))
        dsp.place(y, drag * 0.3, 0.3)
        cr = ins.creak(0.4, rng, [(0, 40), (0.2, 90), (0.4, 30)], [(0, 0), (0.1, 0.5), (0.4, 0)], pitch=0.8)
        dsp.place(y, dsp.lowpass(cr, 2500) * 0.18, 0.36)
    ir = dsp.make_ir(0.6, rng, predelay=0.004, lp_hz=5000)
    y = dsp.reverb(y, ir, wet=0.22)
    y = dsp.highpass(y, 45, 2)
    return dsp.trim(y)


# --------------------------------------------------------------------------
# bride.screech - the scream when she spots you
# --------------------------------------------------------------------------
def _contour(n, pts, rng, flutter=0.025, flutter_rate=13.0, breaks=0):
    """Scream pitch contour: log-interpolated key points + irregular (not
    sinusoidal) flutter + a few sudden voice breaks."""
    tt = np.array([p[0] for p in pts])
    ff = np.log2(np.array([p[1] for p in pts], float))
    lp = np.interp(np.arange(n) / SR, tt, ff)
    lp = dsp.lowpass0(lp, 18.0)
    lp += flutter * dsp.smooth(n, rng, flutter_rate) + 0.5 * flutter * dsp.smooth(n, rng, 30.0)
    for _ in range(breaks):
        c = rng.uniform(0.3, 0.85) * n / SR
        w = rng.uniform(0.04, 0.09)
        jump = rng.choice([-1, 1]) * rng.uniform(0.15, 0.35)
        tt_ = np.arange(n) / SR
        lp += jump * (1 / (1 + np.exp(-(tt_ - c) / 0.004)) - 1 / (1 + np.exp(-(tt_ - c - w) / 0.004)))
    return 2.0 ** lp


def scream_env(f0_fn, bright=1.0):
    """Parallel formants for a scream; F1 tracks the fundamental (formant tuning)."""
    return V.parallel_env([
        (lambda t: np.clip(f0_fn(t) * 1.05, 700, 1500), 420, 0.0),
        (2550, 700, -3.0 * bright),
        (3550, 800, -7.0),
        (4900, 1000, -11.0 / bright),
        (6600, 1400, -16.0 / bright),
        (8800, 2000, -22.0),
    ], floor_db=-34.0)


def _scream_layer(n, pts, amp, rng, sub=0.0, flutter=0.025, breaks=1, breath=0.3, bright=1.0, tilt=0.0):
    f0 = _contour(n, pts, rng, flutter=flutter, breaks=breaks)
    tc_f0 = lambda t: np.interp(t, np.arange(n) / SR, f0)
    y = V.voice(f0, amp, [(0, V.fs("a"))], rng, tilt=tilt, fmax=15000, jitter=0.015, shimmer=0.18,
                breath=breath, subharm=sub, breath_tilt=1.0, noise_mod=0.5, loud_comp=0.5,
                gain_fn=scream_env(tc_f0, bright))
    return y, f0


def screech(rng, i=1):
    if i == 1:
        dur = 2.7
        n = ns(dur)
        t0 = 0.05
        pts = [(0, 430), (t0 + 0.04, 470), (t0 + 0.34, 1030), (1.1, 1140), (1.55, 1090), (1.95, 830), (2.35, 560), (dur, 500)]
        amp = dsp.env_pts(n, [(0, 0), (t0, 0.0), (t0 + 0.025, 0.6), (t0 + 0.28, 1.0), (1.6, 0.95), (2.05, 0.55), (2.42, 0.0), (dur, 0)])
    else:
        dur = 2.5
        n = ns(dur)
        t0 = 0.42
        pts = [(0, 600), (t0, 640), (t0 + 0.13, 1260), (1.0, 1330), (1.35, 1190), (1.7, 1270), (2.05, 700), (dur, 520)]
        amp = dsp.env_pts(n, [(0, 0), (t0 - 0.015, 0.0), (t0 + 0.015, 0.85), (t0 + 0.14, 1.0), (1.75, 0.9), (2.1, 0.4), (2.4, 0.0), (dur, 0)])
    sub = dsp.env_pts(n, [(0, 0), (t0 + 0.15, 0.0), (t0 + 0.3, 0.45), (t0 + 0.55, 0.1), (1.25, 0.5), (1.6, 0.15), (dur, 0.35)])
    main, f0 = _scream_layer(n, pts, amp, rng, sub=sub, breaks=2)
    pts2 = [(p[0] + 0.03, p[1] * 1.059) for p in pts]
    second, _ = _scream_layer(n, pts2, np.roll(amp, ns(0.03)) * 0.65, rng, sub=sub * 0.5, flutter=0.035, breaks=1, bright=0.8)
    pts3 = [(p[0], p[1] * 0.5) for p in pts]
    low, _ = _scream_layer(n, pts3, amp * 0.5, rng, sub=0.6, flutter=0.03, breaks=0, breath=0.6, bright=0.6, tilt=-4.0)
    y = main + second * 0.8 + low * 0.45
    y /= np.max(np.abs(y)) + 1e-9
    tt = np.arange(n) / SR
    # ring modulation with a sweeping carrier -> inhuman inharmonic sidebands
    car = np.sin(dsp.TAU * np.cumsum(np.interp(tt, [0, dur], [150, 85]) * (1 + 0.1 * dsp.smooth(n, rng, 4))) / SR)
    y = y * 0.8 + 0.35 * y * car
    # faint inharmonic metal riding on the scream (randomly wobbling)
    metal = np.zeros(n)
    for fm in [2310, 3170, 4630, 6020]:
        wob = 1 + 0.012 * dsp.smooth(n, rng, 9.0)
        metal += np.sin(dsp.TAU * np.cumsum(fm * wob * (f0 / f0.mean()) ** 0.3) / SR) * rng.uniform(0.4, 1)
    y += 0.022 * metal * amp
    y = 0.5 * dsp.sat(y * 3.0, 1.0) + 0.5 * y
    m = ns(0.3)
    burst = dsp.bandpass(rng.standard_normal(m), 1500, 10000, 2) * dsp.env_ad(m, 0.006, 0.06)
    dsp.place(y, burst * 0.5, t0 - 0.008)
    if i == 2:  # the onryo croak right before the shriek
        cn = ns(t0 + 0.02)
        rate = np.linspace(22, 75, cn)
        ca = np.linspace(0.25, 1.0, cn) ** 1.5
        cr = V.creak(cn, rate, ca, rng, vowel="a", scale=1.0, jitter=0.35)
        dsp.place(y, cr * 0.75, 0.0)
    ir = dsp.make_ir(1.5, rng, predelay=0.018, hf_ratio=0.5)
    y = dsp.reverb(y, ir, wet=0.28)
    y = dsp.highpass(y, 70, 2)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# bride.breath - slow raspy breathing (with the onryo croak)
# --------------------------------------------------------------------------
def breath(rng, i=1):
    dur = {1: 3.4, 2: 3.0, 3: 3.8}[i]
    n = ns(dur)
    y = np.zeros(n)
    if i == 1:
        segs = [("in", 0.05, 1.25), ("out", 1.45, 3.2)]
    elif i == 2:
        segs = [("in", 0.05, 0.75), ("croak", 0.9, 2.8)]
    else:
        segs = [("in", 0.1, 1.5), ("out", 1.6, 3.6)]
    for kind, a, b in segs:
        m = ns(b - a)
        tt = np.arange(m) / SR
        if kind == "in":
            env = np.sin(np.pi * np.clip(tt / (b - a), 0, 1)) ** 1.2 * (1 + 0.25 * dsp.smooth(m, rng, 6.0))
            keys = [(0, V.fs("x", f1=0.9, bw=1.4)), (b - a, V.fs("o", bw=1.4))]
            s = V.vt_noise(m, keys, env, rng, tilt_db=2.5, bw=1.8) * 0.8
            # raspy flutter in the throat while inhaling
            fl = V.creak(m, 38 + 10 * dsp.smooth(m, rng, 2.0), env, rng, vowel="x", jitter=0.4)
            s += fl * 0.35 * np.std(s) / (np.std(fl) + 1e-9)
        elif kind == "out":
            env = (np.minimum(1, tt / 0.15) * np.exp(-tt / (b - a) * 1.4)) * (1 + 0.3 * dsp.smooth(m, rng, 5.0))
            keys = [(0, V.fs("a", f1=0.9)), (b - a, V.fs("x"))]
            s = V.vt_noise(m, keys, env, rng, tilt_db=-1.0, bw=1.5)
            rate = np.interp(tt, [0, b - a], [55, 24]) * (1 + 0.15 * dsp.smooth(m, rng, 4.0))
            cr = V.creak(m, rate, env, rng, vowel="a", jitter=0.35, scale=0.92)
            s = s * 0.6 + cr * 0.9 * np.std(s) / (np.std(cr) + 1e-9)
        else:  # croak: the drawn-out death rattle "a-a-a-a-a"
            env = np.minimum(1, tt / 0.1) * np.clip(1 - (tt - (b - a) + 0.5) / 0.5, 0, 1)
            env *= 1 + 0.35 * dsp.smooth(m, rng, 3.0)
            rate = np.interp(tt, [0, 0.4 * (b - a), b - a], [30, 52, 18]) * (1 + 0.2 * dsp.smooth(m, rng, 5.0))
            cr = V.creak(m, rate, env, rng, vowel="a", jitter=0.3, scale=0.95, f1=0.9)
            cr = cr / (np.std(cr) + 1e-9) * 0.75
            nz = V.vt_noise(m, [(0, V.fs("a"))], env, rng, tilt_db=0.0)
            s = cr + nz * 0.22
        s = dsp.fade(s, 0.02, 0.08)
        dsp.place(y, s, a)
    y = dsp.lowpass(y, 6500, 2)
    ir = dsp.make_ir(0.55, rng, predelay=0.003, lp_hz=5000)
    y = dsp.reverb(y, ir, wet=0.15)
    return dsp.trim(dsp.highpass(y, 80, 2))


# --------------------------------------------------------------------------
# bride.rustle - silk kimono
# --------------------------------------------------------------------------
def rustle(rng, i=1):
    dur = {1: 1.0, 2: 1.4, 3: 0.8}[i]
    n = ns(dur)
    if i == 2:
        sw = dsp.env_pts(n, [(0, 0), (0.25, 1.0), (0.55, 0.5), (0.8, 0.9), (1.25, 0.0), (dur, 0)])
    else:
        pk = rng.uniform(0.25, 0.4) * dur
        sw = dsp.env_pts(n, [(0, 0), (pk, 1.0), (dur * 0.85, 0.0), (dur, 0)])
    sw = np.maximum(sw, 0) ** 1.3

    def grain(r, k, t):
        L = ns(r.uniform(0.006, 0.03))
        g = r.standard_normal(L) * np.hanning(L)
        fc = r.uniform(2000, 7500)
        g = dsp.reson(g, fc, r.uniform(0.8, 2.0), 1.0) * 2.0
        return g * r.uniform(0.25, 1.0) * sw[min(n - 1, ns(t))]

    y = ins.grains(dur, rng, 900.0, grain, rate_env=lambda t: 0.1 + sw[min(n - 1, ns(t))])
    hiss = dsp.bandpass(rng.standard_normal(n), 2500, 9000, 2) * sw * (1 + 0.6 * dsp.smooth(n, rng, 30.0)) * 0.12
    swoosh = dsp.bandpass(rng.standard_normal(n), 250, 1800, 2) * sw * 0.07
    y = y + hiss + swoosh
    ir = dsp.make_ir(0.5, rng, predelay=0.002, lp_hz=7000)
    y = dsp.reverb(y, ir, wet=0.15)
    return dsp.trim(dsp.highpass(y, 150, 2))


# --------------------------------------------------------------------------
# bride.laugh - low breathy chuckle
# --------------------------------------------------------------------------
def laugh(rng, i=1):
    dur = 2.0 if i == 1 else 2.4
    n = ns(dur)
    f0 = np.full(n, 190.0)
    amp = np.zeros(n)
    sub = np.zeros(n)
    t = 0.15
    k_n = 5 if i == 1 else 6
    onsets = []
    for k in range(k_n):
        sd = rng.uniform(0.09, 0.13) * (1.3 if k == k_n - 1 else 1.0)
        i0, i1 = ns(t), ns(t + sd)
        m = i1 - i0
        start = (205 if i == 1 else 170) * (1 - 0.03 * k) * dsp.cents(rng.uniform(-40, 40))
        f0[i0:i1] = np.linspace(start, start * 0.86, m)
        a = dsp.env_ad(m, 0.025, sd * 0.45) * (1 - 0.13 * k)
        a[-ns(0.02):] *= np.linspace(1, 0, ns(0.02))
        if i == 2:
            a = np.minimum(1, np.arange(m) / (0.004 * SR)) * np.exp(-np.arange(m) / (0.07 * SR)) * (1 - 0.12 * k)
            sub[i0:i1] = 0.5
        amp[i0:i1] = a
        onsets.append(t)
        t += sd + rng.uniform(0.05, 0.09)
    f0 = dsp.lowpass0(f0, 30)
    for k in range(1, len(onsets)):
        pass
    vk = "u" if i == 1 else "o"
    keys = [(0, V.fs(vk, scale=0.92)), (dur, V.fs(vk, scale=0.92))]
    y = V.voice(f0, amp, keys, rng, tilt=-10.0, fmax=6000, jitter=0.012, shimmer=0.1, breath=0.9 if i == 1 else 0.5,
                subharm=sub, noise_mod=0.5, loud_comp=0.7)
    # 'f'/'h' onsets
    for t0 in onsets:
        m = ns(0.05)
        h = dsp.lowpass(dsp.bandpass(rng.standard_normal(m), 400, 2800, 3), 3000, 2) * dsp.env_ad(m, 0.02, 0.025)
        dsp.place(y, h * 0.06 * np.std(y[y != 0]) * 6, t0 - 0.035)
    # trailing exhale
    m = ns(0.6)
    ex = V.vt_noise(m, [(0, V.fs("x", scale=0.9))], dsp.env_ad(m, 0.05, 0.18), rng, tilt_db=0) * np.std(y) * 1.2
    dsp.place(y, ex, t + 0.02)
    y = dsp.lowpass(y, 6000, 2)
    ir = dsp.make_ir(0.8, rng, predelay=0.01, lp_hz=4500)
    y = dsp.reverb(y, ir, wet=0.3)
    return dsp.trim(dsp.highpass(y, 90, 2))


# --------------------------------------------------------------------------
# bride.door - fusuma violently thrown open
# --------------------------------------------------------------------------
def door(rng, i=1):
    dur = 1.8
    n = ns(dur)
    y = np.zeros(n)
    ts = 0.26
    m = ns(ts + 0.02)
    tt = np.arange(m) / SR
    slide_env = (tt / ts) ** 1.5
    rumble = dsp.lowpass(rng.standard_normal(m), 900, 2) * slide_env
    chatter = ins.friction_pulses(ts + 0.02, rng, np.linspace(40, 160, m), slide_env, jitter=0.35)
    chatter = dsp.resbank(chatter, [(f, 10, g) for f, _, g in ins.WOOD_DOOR[:6]])
    chatter /= np.max(np.abs(chatter)) + 1e-9
    whoosh = dsp.bandpass(rng.standard_normal(m), 400, 3000, 1) * slide_env * 0.3
    dsp.place(y, rumble * 0.9 + chatter * 0.8 + whoosh * 1.5, 0.0)
    bang = ins.wood_hit(rng, ins.WOOD_DOOR, size=1.15, hardness=0.85, vel=1.0, dur=0.9, noise=0.5)
    dsp.place(y, bang * 1.6, ts)
    mm = ns(0.25)
    boom = dsp.bandpass(rng.standard_normal(mm), 70, 380, 2) * dsp.env_ad(mm, 0.002, 0.05)
    dsp.place(y, boom * 1.2, ts)
    tr = ts + 0.03
    for k in range(9):  # lattice / paper rattle
        tr += rng.uniform(0.018, 0.05)
        r = ins.wood_hit(rng, ins.WOOD_DOOR, size=0.55, hardness=0.7, vel=0.35 * (0.8 ** k), dur=0.2)
        dsp.place(y, r, tr)
    ir = dsp.make_ir(1.0, rng, predelay=0.008)
    y = dsp.reverb(y, ir, wet=0.35)
    y = dsp.highpass(y, 40, 2)
    y = dsp.limiter(dsp.norm_peak(y, 0.0), -7.0, 0.006)
    return dsp.trim(y, -65)
