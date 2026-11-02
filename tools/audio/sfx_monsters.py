"""Monster sounds (all mono, positional).  Every creature has its own
signature:
  覗き nozoki     wet breath, a broken too-high giggle, a chaotic shriek
  呼び声 yobigoe  the far 「おーい」 of a rescuer (and what it turns into)
  後ろ ushiro     footsteps in step with yours, breath and whispers at your ear
  小屋主 koyanushi a wet snore that becomes bone cracks and a howl
  御山様 oyama    sub-heavy groans of a giant tree with a voice in it
  写るもの utsuru radio-static whispers only the camera hears"""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _rev(y, rng, rt=1.3, wet=0.3, dry=0.9, lp=None, predelay=0.01):
    return dsp.reverb(y, dsp.make_ir(rt, rng, predelay=predelay, hf_ratio=0.4, lp_hz=lp), wet=wet, dry=dry)


def _fin(y, hp=40.0, thresh=-62.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh)


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _bone_pop(rng, vel=1.0, size=1.0):
    """Joint cavitation pop / bone crack: a hard click through a small hollow
    resonance plus a few cartilage crunch grains."""
    m = ns(0.06)
    imp = rng.standard_normal(ns(0.0012)) * np.exp(-np.arange(ns(0.0012)) / (0.00025 * SR))
    x = np.zeros(m)
    x[:len(imp)] = imp
    y = dsp.reson(x, rng.uniform(700, 1600) / size, rng.uniform(3, 7), 3.0) + dsp.reson(x, rng.uniform(2000, 3800) / size, 4, 1.5)
    y += dsp.highpass(x, 2500, 2) * 1.5
    y += ins.crunch(0.06, rng, lambda t: 1500 * np.exp(-t / 0.008), lo=1500, hi=7000, dec=(0.0002, 0.0006)) * 0.3
    return _nrm(y) * vel


# ==========================================================================
# 覗き nozoki
# ==========================================================================
def nozoki_breath(rng, i=1):
    tbl, sc = V.VF, 0.84  # long, thin throat
    if i == 1:
        seq = [("in", 1.5, dict(wet=0.9, rasp=0.25)), ("out", 2.0, dict(wet=1.0, rasp=0.45, voiced=0.08, f0=150))]
    elif i == 2:
        seq = [("in", 1.1, dict(wet=0.8, rasp=0.2, tremor=0.3, trem_rate=11)), ("hold", 0.35, {}),
               ("out", 2.3, dict(wet=1.2, rasp=0.6))]
    else:
        seq = [("in", 0.35, dict(wet=0.7)), ("out", 0.4, dict(wet=0.8)), ("in", 0.35, dict(wet=0.7)),
               ("out", 1.9, dict(wet=0.9, hiss=0.3, rasp=0.3))]
    y = np.zeros(ns(sum(s[1] for s in seq) + 1.0))
    t = 0.05
    for kind, d, kw in seq:
        if kind == "hold":
            dsp.place(y, V.wet_crackle(d, rng, density=70) * 0.02, t)
        else:
            b = V.breath(rng, kind, d, table=tbl, scale=sc, vowel_in="x", vowel_out="o", **kw)
            dsp.place(y, b, t)
            if kind == "out":  # gurgle deep in the throat
                gl = V.creak(ns(d), 22 + 8 * dsp.smooth(ns(d), rng, 2.0), dsp.env_pts(ns(d), [(0, 0), (0.2, 1), (d, 0)]),
                             rng, vowel="o", table=tbl, scale=sc, jitter=0.5)
                dsp.place(y, gl * np.std(b) * 0.9, t)
        t += d + rng.uniform(0.05, 0.15)
    y = dsp.lowpass(y, 8500, 2)
    y = dsp.close_mic(y, prox_db=3.0, pres_db=2.0, air_db=1.0)
    y = _rev(y, rng, rt=0.9, wet=0.15)
    return _fin(y, 70)


def nozoki_giggle(rng, i=1):
    if i == 1:
        a = V.laugh(rng, n_syl=7, f0=610, decline=0.015, gap=(0.03, 0.07), syl=(0.06, 0.1), vowel="i", table=V.VF,
                    scale=1.16, breath=0.9, tilt=-7, broken=0.55, sub=0.3, f0_jitter_c=120)
        b = V.laugh(rng, n_syl=7, f0=305, decline=0.015, gap=(0.03, 0.07), syl=(0.06, 0.1), vowel="i", table=V.VF,
                    scale=0.9, breath=0.6, tilt=-9, broken=0.4, sub=0.4, f0_jitter_c=120)
        y = dsp.mix(a, dsp.place(np.zeros(len(b) + ns(0.03)), b * 0.35, 0.03))
    else:  # starts too high, then runs down like a dying tape
        a = V.laugh(rng, n_syl=8, f0=650, decline=0.01, gap=(0.04, 0.06), syl=(0.07, 0.1), vowel="e", table=V.VF,
                    scale=1.15, breath=0.8, tilt=-7, broken=0.3, sub=0.2)
        n = len(a)
        rate = dsp.env_pts(n, [(0, 1.0), (n / SR * 0.5, 1.0), (n / SR, 0.45)])
        y = dsp.varispeed(a, rate)
    y = y + dsp.pad_to(V.mouth_clicks(len(y) / SR * 0.6, rng, rate=4.0) * np.std(y) * 1.5, len(y))
    y = dsp.highpass(y, 200, 2)
    y = _rev(y, rng, rt=1.1, wet=0.25)
    return _fin(y, 150)


def _shriek_core(rng, dur, base, chaos, jumps, biphon_at, vowels, rough=(0.75, 72, 0.5), scale=1.12):
    f0p = [(0, base * 0.45), (0.05, base), (dur * 0.35, base * 1.12), (dur * 0.7, base * 0.95), (dur, base * 0.55)]
    amp = [(0, 0), (0.025, 1.0), (dur * 0.75, 0.9), (dur * 0.92, 0.5), (dur, 0)]
    a, f0 = V.scream(dur, rng, f0p, amp, vowel_pts=vowels, chaos_pts=chaos, jumps=jumps,
                     biphon=dict(ratio=[(0, 1.33), (dur, 1.26)],
                                 amp=[(0, 0), (biphon_at, 0), (biphon_at + 0.08, 0.7), (biphon_at + 0.5, 0.7), (biphon_at + 0.6, 0), (dur, 0)]),
                     breath=0.45, rasp=0.8, rough=rough, scale=scale, oq=0.4, closure=0.7)
    # the second throat, a tritone below, a hair late, other formants
    b, _ = V.scream(dur, rng, [(t + 0.02, f / 1.41) for t, f in f0p], amp, vowel_pts=vowels, chaos_pts=chaos,
                    jumps=[(t + 0.02, s, w) for t, s, w in jumps], breath=0.5, rasp=1.0, rough=(0.65, 58, 0.6),
                    scale=scale * 0.82, oq=0.42, closure=0.8)
    # low growl under it
    c, _ = V.scream(dur, rng, [(0, 150), (dur, 120)], amp, vowel="o", chaos_pts=[(0, 3.9), (dur, 3.9)], ct=0.4,
                    ca=0.6, breath=0.6, rasp=1.2, rough=(0.7, 45, 0.7), scale=0.85, oq=0.5)
    return _nrm(a) + 0.55 * _nrm(b) + 0.35 * _nrm(c)


def nozoki_shriek(rng, i=1):
    if i == 1:
        dur = 2.5
        core = _shriek_core(rng, dur, 1550,
                            chaos=[(0, 3.92), (0.12, 3.92), (0.16, 0), (0.55, 0), (0.6, 3.3), (0.95, 3.3), (1.0, 3.55),
                                   (1.25, 3.55), (1.3, 3.93), (2.0, 3.93), (2.05, 0), (dur, 0)],
                            jumps=[(0.45, 5, 0.12), (1.05, -3, 0.07), (2.0, -7, None)], biphon_at=1.35,
                            vowels=[(0, "i"), (0.5, "a"), (1.6, "a"), (2.2, "e")])
    else:
        dur = 2.1
        core = _shriek_core(rng, dur, 1850,
                            chaos=[(0, 0), (0.25, 0), (0.3, 3.88), (0.8, 3.92), (0.85, 3.25), (1.2, 3.25), (1.25, 3.94), (dur, 3.94)],
                            jumps=[(0.22, 7, 0.06), (0.9, -5, 0.15), (1.5, 4, None)], biphon_at=0.95,
                            vowels=[(0, "e"), (0.4, "i"), (1.3, "a"), (dur, "a")], rough=(0.7, 85, 0.6))
    gasp = V.breath(rng, "in", 0.2, table=V.VF, scale=1.1, vowel_in="a", hiss=0.4)
    y = np.zeros(ns(dur + 0.25))
    dsp.place(y, gasp * 0.25 / (np.max(np.abs(gasp)) + 1e-9), 0.0)
    dsp.place(y, core, 0.2)
    y = _nrm(y)
    y = 0.55 * dsp.sat(y * 2.5, 1.0) + 0.45 * y
    y = _rev(y, rng, rt=1.6, wet=0.3, predelay=0.012)
    return _fin(y, 90)


def nozoki_crawl(rng, i=1):
    dur = rng.uniform(1.3, 1.6)
    y = np.zeros(ns(dur + 0.6))
    t = 0.03
    cyc = rng.uniform(0.28, 0.34)
    while t < dur:
        offs = [0.0, rng.uniform(0.035, 0.06), rng.uniform(0.11, 0.15), rng.uniform(0.16, 0.2)]
        for k, o in enumerate(offs):
            st = ins.leaf_step(rng, weight=1.5 if k >= 2 else 1.1, crunch_amt=1.4, heel=1.0, toe=0.4, pace=0.35, dur=0.35)
            dsp.place(y, st * rng.uniform(0.7, 1.0), t + o)
            if k < 2 and rng.random() < 0.5:  # knuckles / long fingers knocking on roots
                dsp.place(y, ins.modal_hit(rng, ins.TRUNK, size=0.45, hardness=0.9, vel=0.35, dur=0.15), t + o + 0.005)
        if rng.random() < 0.5:  # wet hair dragging through the leaves
            m = ns(0.2)
            hs = dsp.bandpass(rng.standard_normal(m), 1200, 6000, 2) * dsp.env_pts(m, [(0, 0), (0.05, 1), (0.2, 0)])
            dsp.place(y, hs * 0.05, t + 0.1)
        t += cyc * rng.uniform(0.92, 1.08)
    y = dsp.highpass(y, 45, 2)
    y = _rev(y, rng, rt=1.1, wet=0.2)
    return _fin(y)


def nozoki_creak(rng, i=1):
    if i == 1:  # the long neck turning, joint by joint
        d = 1.5
        y = np.zeros(ns(d + 0.6))
        cr = ins.creak(d, rng, [(0, 9), (d * 0.5, 18), (d, 7)], [(0, 0), (0.15, 0.6), (d * 0.8, 0.5), (d, 0)],
                       pitch=1.4, jitter=0.4, q_scale=1.5)
        dsp.place(y, cr * 0.6, 0.02)
        for t0 in sorted(rng.uniform(0.05, d, 7)):
            dsp.place(y, _bone_pop(rng, rng.uniform(0.4, 1.0)), t0)
        dsp.place(y, _bone_pop(rng, 1.3, 0.8), d + 0.05)
    else:  # a cascade of long fingers cracking, then one big pop
        y = np.zeros(ns(1.6))
        t = 0.03
        for k in range(9):
            dsp.place(y, _bone_pop(rng, rng.uniform(0.35, 0.8), rng.uniform(0.9, 1.3)), t)
            t += rng.uniform(0.04, 0.12)
        dsp.place(y, _bone_pop(rng, 1.4, 0.75), t + 0.25)
    y = dsp.close_mic(y, 2.0, 2.0, 1.0)
    y = _rev(y, rng, rt=0.7, wet=0.15)
    return _fin(y, 100)


# ==========================================================================
# 呼び声 yobigoe
# ==========================================================================
def _far_call(y, rng, lp=3600, rt=3.0, dry=0.5):
    y = dsp.highpass(y, 140, 2)
    return dsp.far(y, rng, rt60=rt, wet=0.9, dry=dry, lp=lp, predelay=0.05,
                   taps=[(0.38, 0.28, lp * 0.6), (0.86, 0.14, lp * 0.45)])


def yobigoe_call(rng, i=1):
    if i == 1:
        c = V.call_oi(rng, dur_o=0.95, f0=208)
    elif i == 2:
        c = V.call_oi(rng, dur_o=0.7, dur_i=0.34, f0=240, rise=1.15, breath=0.36)
    elif i == 3:  # twice - and the second is *exactly* the first
        a = V.call_oi(rng, dur_o=0.85, f0=214)
        c = np.zeros(len(a) + ns(1.75))
        dsp.place(c, a, 0)
        dsp.place(c, a, 1.75)
    else:
        c = V.call_oi(rng, dur_o=1.55, f0=200, rise=1.1, crack=(1.35, 2.0))
    return _fin(_far_call(c, rng), 120, -60)


def yobigoe_call_wrong(rng, i=1):
    if i == 1:  # held far too long, sinking, glitching
        c = V.call_oi(rng, dur_o=3.0, dur_i=0.5, f0=212, rise=1.1, end_drop=0.8)
        n = len(c)
        rate = dsp.env_pts(n, [(0, 1.0), (0.5, 1.0), (3.0, 0.62), (n / SR, 0.5)])
        y = dsp.varispeed(c, rate)
        for t0 in (1.3, 2.4):  # buffer glitches: a 40-70 ms grain repeated
            g0 = ns(t0)
            gl = ns(rng.uniform(0.04, 0.07))
            grain = dsp.fade(y[g0:g0 + gl].copy(), 0.002, 0.002)
            rep = np.concatenate([grain * (0.9 ** k) for k in range(rng.integers(3, 6))])
            y = np.concatenate([y[:g0], rep, y[g0:]])
    else:  # normal... then the /i/ freezes and grinds down like a tape stopping
        c = V.call_oi(rng, dur_o=0.9, dur_i=0.6, f0=210)
        t_i = 0.06 + 0.9 + 0.2
        i0 = ns(t_i)
        gl = ns(0.045)
        grain = c[i0:i0 + gl] * np.hanning(gl)
        frz = np.zeros(ns(2.6))
        t, k = 0.0, 0
        while t < 2.4:
            rr = 1.0 - 0.55 * t / 2.4
            g = dsp.varispeed(grain, rr)
            dsp.place(frz, g * (1 - 0.3 * t / 2.4), t)
            t += len(g) / SR * rng.uniform(0.45, 0.6)
            k += 1
        y = np.concatenate([c[:i0], frz])
        y = y + dsp.pad_to(dsp.place(np.zeros(ns(1.0)), dsp.reverse(c[ns(0.1):ns(0.9)]) * 0.18, 0.0), len(y))
    return _fin(_far_call(y, rng, rt=3.3, dry=0.45), 120, -60)


def yobigoe_laugh(rng, i=1):
    if i == 1:  # a mouthless face: closed-lip humming laugh
        y = V.laugh(rng, n_syl=6, f0=108, decline=0.02, gap=(0.12, 0.16), syl=(0.12, 0.16), table=V.VM, scale=0.9,
                    nasal=True, breath=0.6, tilt=-11, sub=0.15)
    else:  # は … は … は … - far too slow, sinking
        y = V.laugh(rng, n_syl=5, f0=118, decline=0.06, gap=(0.35, 0.45), syl=(0.16, 0.2), vowel="a", table=V.VM,
                    scale=0.86, breath=1.0, tilt=-10, sub=0.3)
    y = dsp.highpass(y, 80, 2)
    y = _rev(y, rng, rt=1.8, wet=0.4, lp=4000)
    return _fin(y, 60)


def yobigoe_rush(rng, i=1):
    dur = 2.0
    n = ns(dur)
    y = np.zeros(n)
    m = ns(0.45)
    tt = np.arange(m) / SR
    fc = 300 * 2 ** (np.clip(tt / 0.42, 0, 1) * 4.0)
    wh = dsp.tv_filter(rng.standard_normal(m), fc, 1.4, "bp") * (tt / 0.45) ** 2.5
    dsp.place(y, wh / (np.max(np.abs(wh)) + 1e-9) * 0.7, 0.0)
    s, _ = V.scream(0.75, rng, [(0, 420), (0.04, 760), (0.35, 820), (0.75, 480)], [(0, 0), (0.015, 1.0), (0.5, 0.8), (0.75, 0)],
                    vowel_pts=[(0, "a"), (0.75, "o")], chaos_pts=[(0, 3.9), (0.75, 3.92)], ct=0.4, ca=0.6, breath=0.5,
                    rasp=1.2, rough=(0.7, 65, 0.6), scale=0.95, oq=0.4, closure=0.7)
    dsp.place(y, _nrm(s) * 1.0, 0.43)
    m = ns(0.5)
    t2 = np.arange(m) / SR
    thump = dsp.sat(np.sin(TAU * np.cumsum(60 + 70 * np.exp(-t2 / 0.03)) / SR) * dsp.env_ad(m, 0.002, 0.09) * 1.8, 1.0)
    dsp.place(y, thump * 0.7, 0.43)
    y = _nrm(y)
    y = 0.6 * dsp.sat(y * 2.2, 1.0) + 0.4 * y
    y = _rev(y, rng, rt=1.4, wet=0.3)
    return _fin(y, 40)


# ==========================================================================
# 後ろ ushiro
# ==========================================================================
def ushiro_step(rng, i=1):
    y = ins.leaf_step(rng, weight=0.8, crunch_amt=0.95, needles=1.2, damp=0.2, drag=0.6, heel=0.6, toe=1.0,
                      dur=0.6, pace=1.1, bright=0.85)
    m = ns(0.32)  # kimono hem brushing the leaves
    hem = dsp.bandpass(rng.standard_normal(m), 300, 2500, 2) * dsp.env_pts(m, [(0, 0), (0.1, 1.0), (0.32, 0)])
    dsp.place(y, hem * 0.03 * rng.uniform(0.6, 1.0), 0.0)
    return _fin(y, 60)


def _cold_exhale(rng, d, scale=1.08, tremor=0.0):
    m = ns(d)
    keys = [(0, V.fs("u", V.VF, scale, f1=0.9)), (d, V.fs("u", V.VF, scale))]
    env = dsp.env_pts(m, [(0, 0), (0.12, 1.0), (d * 0.6, 0.7), (d, 0)])
    if tremor:
        env = env * (1 - tremor + tremor * (0.5 + 0.5 * np.sin(TAU * 7 * np.arange(m) / SR)) ** 2)
    s = V.vt_noise(m, keys, env, rng, tilt_db=2.0, bw=1.3)
    s += dsp.bandpass(rng.standard_normal(m), 3500, 10000, 2) * env * 0.5
    return s


def ushiro_breath(rng, i=1):
    if i == 1:
        a = V.breath(rng, "in", 1.2, table=V.VF, scale=1.08, vowel_in="x")
        b = _cold_exhale(rng, 1.9)
        y = np.zeros(len(a) + len(b) + ns(0.6))
        dsp.place(y, a * 0.7, 0.05)
        dsp.place(y, b / (np.std(b) + 1e-9) * np.std(a) * 1.1, len(a) / SR + 0.2)
    elif i == 2:
        b = _cold_exhale(rng, 3.0, tremor=0.35)
        y = np.zeros(len(b) + ns(0.4))
        dsp.place(y, b, 0.05)
    else:  # two sniffs - smelling you - then the exhale
        y = np.zeros(ns(3.2))
        for t0 in (0.05, 0.32):
            s = V.breath(rng, "in", 0.18, table=V.VF, scale=1.1, vowel_in="i", hiss=0.5)
            dsp.place(y, s, t0)
        b = _cold_exhale(rng, 1.8)
        dsp.place(y, b / (np.std(b) + 1e-9) * 0.12, 0.85)
    y = y + dsp.pad_to(V.mouth_clicks(len(y) / SR, rng, rate=0.8) * np.std(y) * 2.5, len(y))
    y = dsp.close_mic(y, prox_db=5.0, pres_db=3.0, air_db=4.0)
    return _fin(y, 80, -60)


def ushiro_whisper(rng, i=1):
    if i == 1:
        w = V.whisper("fu ri ka e q te", rng, scale=1.07, rate=0.74, close=1.0, bw=1.22)
    elif i == 2:
        w = V.whisper("fu ri ka e q te - -", rng, scale=1.09, rate=0.62, close=0.9, bw=1.2, voicing=0.12, f0=240)
    else:
        w = V.whisper("ne e , fu ri ka e q te", rng, scale=1.06, rate=0.95, close=1.0, bw=1.25)
    br = _cold_exhale(rng, 0.9)
    y = np.zeros(len(w) + len(br))
    dsp.place(y, w, 0)
    dsp.place(y, br / (np.std(br) + 1e-9) * np.std(w) * 0.5, len(w) / SR - 0.05)
    y = dsp.highpass(y, 110, 2)
    y = dsp.close_mic(y, prox_db=5.0, pres_db=4.0, air_db=3.0)
    y = y + dsp.pad_to(V.mouth_clicks(len(y) / SR, rng, rate=1.2) * np.std(y) * 2.0, len(y))
    return dsp.trim(y, -60)


def ushiro_leave(rng, i=1):
    sigh = V.breath(rng, "out", 1.6, table=V.VF, scale=1.06, voiced=0.25, f0=210, vowel_out="a")
    w = V.whisper("tsu ma ra na i", rng, scale=1.06, rate=0.7, close=0.6, bw=1.25, declination=0.85)
    y = np.zeros(len(sigh) + len(w) + ns(1.4))
    dsp.place(y, sigh / (np.std(sigh) + 1e-9) * np.std(w) * 0.8, 0.02)
    dsp.place(y, w, len(sigh) / SR + 0.25)
    n = len(y)
    tt = np.arange(n) / SR
    dist = np.clip((tt - 0.6) / (n / SR - 0.6), 0, 1)  # walking away from your ear
    y = dsp.tv_filter(y, 9000 - 6500 * dist, 0.6, "lp", block=256) * (1 - 0.6 * dist)
    wet = dsp.convolve(y, dsp.make_ir(1.6, rng, predelay=0.02, lp_hz=4000))[:n] * (0.05 + 0.5 * dist)
    y = dsp.highpass(y + wet, 100, 2)
    return dsp.trim(y, -60)


def ushiro_attack(rng, i=1):
    dur = 1.7
    s, _ = V.scream(dur, rng, [(0, 560), (0.04, 980), (0.3, 1080), (0.7, 1250), (1.2, 1100), (dur, 700)],
                    [(0, 0), (0.012, 1.0), (1.3, 0.9), (dur, 0)], vowel_pts=[(0, "a"), (0.8, "a"), (dur, "e")],
                    chaos_pts=[(0, 3.92), (0.1, 3.92), (0.15, 3.3), (0.6, 3.3), (0.65, 3.9), (dur, 3.94)],
                    jumps=[(0.62, 4, None), (1.25, -6, None)],
                    biphon=dict(ratio=[(0, 1.27), (dur, 1.21)], amp=[(0, 0), (0.7, 0), (0.78, 0.6), (1.2, 0.6), (1.3, 0), (dur, 0)]),
                    breath=0.5, rasp=1.0, rough=(0.75, 62, 0.55), scale=1.05, oq=0.38, closure=0.6)
    y = np.zeros(ns(dur + 0.1))
    m = ns(0.08)
    blast = dsp.lowpass(rng.standard_normal(m), 250, 2) * dsp.env_ad(m, 0.003, 0.02)
    dsp.place(y, blast * 0.5, 0.0)
    dsp.place(y, _nrm(s), 0.01)
    y = _nrm(y)
    y = 0.6 * dsp.sat(y * 3.0, 1.0) + 0.4 * y
    y = dsp.close_mic(y, prox_db=4.0, pres_db=3.0, air_db=2.0)
    return _fin(y, 80)


# ==========================================================================
# 小屋主 koyanushi
# ==========================================================================
def koyanushi_sleep(rng, i=1):
    L = 8.0
    n = ns(L)
    N = ns(L + 2.0)
    y = np.zeros(N)
    for c, t0 in enumerate((0.25, 4.15)):
        di = rng.uniform(1.4, 1.7)
        m = ns(di)
        env = dsp.env_pts(m, [(0, 0), (0.3, 0.8), (di * 0.7, 1.0), (di, 0)])
        rate = 30 + 9 * dsp.smooth(m, rng, 1.5)
        sn = V.creak(m, rate, env, rng, vowel="o", table=V.VM, scale=0.93, jitter=0.35)
        nz = V.vt_noise(m, [(0, V.fs("o", V.VM, 0.93)), (di, V.fs("a", V.VM, 0.93))], env, rng, tilt_db=1.0)
        snore = sn / (np.std(sn) + 1e-9) * 0.8 + nz / (np.std(nz) + 1e-9) * 0.5
        dsp.place(y, snore * env, t0)
        do = rng.uniform(1.5, 1.9)
        ex = V.breath(rng, "out", do, table=V.VM, scale=0.93, vowel_out="o", wet=1.4, rasp=0.5, voiced=0.06, f0=90)
        dsp.place(y, ex / (np.std(ex) + 1e-9) * 0.55, t0 + di + 0.15)
        m2 = ns(0.09)  # lips flapping at the end: "puh"
        puh = dsp.lowpass(rng.standard_normal(m2), 600, 2) * dsp.env_ad(m2, 0.004, 0.02)
        dsp.place(y, puh * 0.5, t0 + di + 0.15 + do - 0.15)
    y = dsp.wrap(y, n)
    y = dsp.circ(lambda x: dsp.lowpass(x, 6000, 2), y)
    y = dsp.circ(lambda x: dsp.highpass(x, 60, 2), y)
    room = dsp.colored(n, rng, -4.0, lo=80, hi=2000) * 0.004
    y = y + room
    ir = dsp.make_ir(0.45, rng, predelay=0.004, lp_hz=5000)
    y = dsp.circ_reverb(y, ir, wet=0.18, dry=0.95)
    return dsp.dc_block(y, circular=True)


MUMBLES = ["n a | ka a ri | o so N", "mo u | ha ra | ta ri N", "a ka | yo se | ru na", "n n | o ma e | wa a"]


def koyanushi_mumble(rng, i=1):
    s = V.speak(MUMBLES[i - 1] + " | " + MUMBLES[i % 4], rng, table=V.VM, scale=0.93, f0=96, rate=0.72,
                accent=2.0, declination=4.0, breath=0.45, jitter=0.03, shimmer=0.15, tilt=-9, subharm=0.25, slur=0.45,
                cons_gain=0.5)
    s = s + dsp.pad_to(V.mouth_clicks(len(s) / SR, rng, rate=2.0) * np.std(s) * 2.0, len(s))
    s = dsp.lowpass(s, 2600, 2)
    s = _rev(s, rng, rt=0.45, wet=0.15)
    return _fin(s, 70)


def koyanushi_wake(rng, i=1):
    dur = 2.5
    y = np.zeros(ns(dur + 0.6))
    for t0 in (0.05, 0.22, 0.31, 0.58, 0.66, 0.85):
        dsp.place(y, _bone_pop(rng, rng.uniform(0.5, 1.0), rng.uniform(0.8, 1.2)), t0)
    g = V.breath(rng, "in", 0.32, table=V.VM, scale=0.93, vowel_in="a", voiced=0.4, f0=150, hiss=0.3)
    dsp.place(y, _nrm(g) * 0.75, 1.0)
    d = 1.15
    n = ns(d)
    ff = 82 * dsp.cents(dsp.env_pts(n, [(0, 100), (0.3, 0), (d, -250)]))
    amp = dsp.env_pts(n, [(0, 0), (0.12, 1.0), (d * 0.7, 0.8), (d, 0)])
    gr = V.voice(ff, amp, [(0, V.fs("o", V.VM, 0.92)), (d, V.fs("a", V.VM, 0.92, f1=0.8))], rng, tilt=-9, fmax=5000,
                 jitter=0.04, shimmer=0.2, breath=0.6, subharm=0.45)
    dsp.place(y, _nrm(gr) * 0.8, 1.38)
    y = _rev(y, rng, rt=0.5, wet=0.2)
    return _fin(y, 50)


def koyanushi_scream(rng, i=1):
    dur = 2.3
    s, _ = V.scream(dur, rng, [(0, 180), (0.15, 300), (0.6, 420), (1.4, 380), (dur, 240)],
                    [(0, 0), (0.06, 0.7), (0.3, 1.0), (1.7, 0.9), (dur, 0)], vowel_pts=[(0, "o"), (0.5, "a"), (1.8, "a"), (dur, "o")],
                    chaos_pts=[(0, 3.92), (0.4, 3.88), (0.45, 3.4), (0.8, 3.4), (0.85, 3.93), (dur, 3.95)], ct=0.4, ca=0.55,
                    breath=0.6, rasp=1.4, rough=(0.65, 55, 0.6), scale=0.92, oq=0.45, closure=0.8)
    cr = V.creak(ns(0.35), np.linspace(18, 60, ns(0.35)), np.linspace(0.3, 1, ns(0.35)), rng, vowel="a", table=V.VM)
    y = np.zeros(ns(dur + 0.35))
    dsp.place(y, cr * 0.5, 0.0)
    dsp.place(y, _nrm(s), 0.3)
    y = _nrm(y)
    y = 0.55 * dsp.sat(y * 2.5, 1.0) + 0.45 * y
    y = _rev(y, rng, rt=0.6, wet=0.25)
    return _fin(y, 60)


def koyanushi_step(rng, i=1):
    y = np.zeros(ns(0.9))
    m = ns(0.03)
    slap = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0035 * SR))
    slap = dsp.bandpass(slap, 500, 6000, 2)
    dsp.place(y, slap * 0.6, 0.01)
    fl = ins.modal_hit(rng, ins.WOOD_FLOOR, size=rng.uniform(1.0, 1.3), hardness=0.35, vel=1.0, dur=0.6, noise=0.2)
    dsp.place(y, fl, 0.01)
    m = ns(0.2)
    t = np.arange(m) / SR
    dsp.place(y, np.sin(TAU * np.cumsum(70 + 40 * np.exp(-t / 0.015)) / SR) * dsp.env_ad(m, 0.002, 0.04) * 0.8, 0.01)
    if i in (2, 4):
        c = ins.creak(0.35, rng, [(0, 30), (0.15, 80), (0.35, 40)], [(0, 0), (0.05, 0.7), (0.35, 0)], pitch=0.8)
        dsp.place(y, c * 0.25, 0.05)
    y = _rev(y, rng, rt=0.5, wet=0.22)
    return _fin(y, 45)


# ==========================================================================
# 御山様 oyama
# ==========================================================================
def oyama_step(rng, i=1):
    y = np.zeros(ns(3.0))
    m = ns(1.0)
    t = np.arange(m) / SR
    sub = np.sin(TAU * np.cumsum(28 + 32 * np.exp(-t / 0.05)) / SR) * dsp.env_ad(m, 0.004, 0.22)
    sub = dsp.sat(sub * 2.2, 1.0)
    dsp.place(y, sub, 0.02)
    nz = dsp.lowpass(rng.standard_normal(ns(0.4)), 250, 2) * dsp.env_ad(ns(0.4), 0.003, 0.07)
    dsp.place(y, nz * 0.8, 0.02)
    cr = ins.crunch(0.6, rng, lambda tt: 3500 * np.exp(-tt / 0.07), lo=600, hi=5000)
    dsp.place(y, cr * 0.15, 0.025)
    for k in range(rng.integers(1, 4)):
        dsp.place(y, ins.twig_snap(rng, thick=rng.uniform(0.2, 0.5)) * 0.25, 0.03 + rng.uniform(0, 0.08))
    d = rng.uniform(0.6, 1.0)
    wc = ins.creak(d, rng, [(0, 8), (d * 0.5, 20), (d, 7)], [(0, 0), (0.15, 0.9), (d, 0)], pitch=0.35, tonal=0.4, jitter=0.3)
    dsp.place(y, wc * 0.35, rng.uniform(0.25, 0.45))
    y = _rev(y, rng, rt=2.2, wet=0.45, lp=3500, predelay=0.02)
    return _fin(y, 22)


def _giant_voice(rng, d, f0a, f0b, vowels, chaos):
    v, _ = V.scream(d, rng, [(0, f0a), (d * 0.4, f0a * 1.15), (d, f0b)], [(0, 0), (d * 0.25, 1.0), (d * 0.75, 0.85), (d, 0)],
                    vowel_pts=vowels, chaos_pts=chaos, ct=0.25, ca=0.4, breath=0.5, rasp=0.8, scale=0.48,
                    rough=(0.45, 38, 0.6), oq=0.6, closure=1.4, bwr=0.18)
    return v


def oyama_groan(rng, i=1):
    d = {1: 4.6, 2: 5.6, 3: 4.0}[i]
    n = ns(d)
    y = np.zeros(ns(d + 4.0))
    cr = ins.creak(d, rng, [(0, 4), (d * 0.4, 13), (d * 0.75, 7), (d, 3)], [(0, 0), (0.6, 0.8), (d * 0.7, 1.0), (d, 0)],
                   pitch=0.22, jitter=0.35, q_scale=1.2, tonal=0.6, noise=0.04)
    gv = _giant_voice(rng, d, 52 if i != 2 else 44, 38, [(0, "u"), (d * 0.5, "o"), (d, "u")],
                      [(0, 3.2), (d * 0.3, 3.2), (d * 0.4, 3.5), (d * 0.6, 3.9), (d, 3.9)])
    sub = dsp.sub_rumble(n, 33.0, rng=rng, am_rate=6.0, am_depth=0.5, drive=2.0) * dsp.env_pts(n, [(0, 0), (d * 0.4, 1.0), (d, 0)])
    fib = ins.crunch(d, rng, lambda t: 60 + 140 * np.sin(np.pi * t / d), lo=800, hi=4000)
    core = _nrm(cr) * 0.6 + _nrm(gv) * 0.75 + sub * 0.55 + fib * 0.05
    dsp.place(y, core, 0.05)
    y = dsp.lowpass(y, 3200, 2)
    y = dsp.far(y, rng, rt60=4.0, wet=0.8, dry=0.7, lp=3000, predelay=0.06)
    return _fin(y, 22, -60)


def oyama_roar(rng, i=1):
    d = 3.2 if i == 1 else 3.6
    n = ns(d)
    main, _ = V.scream(d, rng, [(0, 70), (0.25, 130), (1.2, 160), (2.2, 120), (d, 70)],
                       [(0, 0), (0.12, 0.8), (0.4, 1.0), (2.4, 0.85), (d, 0)], vowel_pts=[(0, "o"), (0.6, "a"), (2.4, "a"), (d, "o")],
                       chaos_pts=[(0, 3.9), (d, 3.94)], ct=0.35, ca=0.55, breath=0.7, rasp=1.6, scale=0.72,
                       rough=(0.7, 44, 0.6), oq=0.5, closure=0.8, bwr=0.16)
    hi, _ = V.scream(d, rng, [(0, 400), (0.3, 900), (1.5, 1000), (d, 520)], [(0, 0), (0.3, 0.7), (2.3, 0.6), (d, 0)],
                     vowel="a", chaos_pts=[(0, 3.6), (d, 3.93)], breath=0.5, rasp=1.0, scale=0.75, rough=(0.6, 70, 0.5))
    hi = dsp.varispeed(hi, 0.62)[:n]
    m = ns(2.2)
    t = np.arange(m) / SR
    sub = dsp.sat(np.sin(TAU * np.cumsum(28 + 45 * np.exp(-t / 0.35)) / SR) * dsp.env_ad(m, 0.05, 0.9) * 2.0, 1.0)
    air = dsp.lowpass(rng.standard_normal(n), 900, 2) * dsp.env_pts(n, [(0, 0), (0.3, 1.0), (d, 0)])
    core = _nrm(main) * 1.0 + dsp.pad_to(_nrm(hi), n) * 0.5 + dsp.pad_to(sub, n) * 0.42 + air * 0.08
    y = np.zeros(ns(d + 4.0))
    dsp.place(y, core, 0.05)
    for k in range(3):
        dsp.place(y, ins.twig_snap(rng, thick=rng.uniform(0.6, 0.9)) * 0.3, 0.1 + rng.uniform(0, 1.5))
    y = _nrm(y)
    y = 0.6 * dsp.sat(y * 2.2, 1.0) + 0.4 * y
    y = dsp.far(y, rng, rt60=3.6, wet=0.7, dry=0.8, lp=5000, predelay=0.04)
    return _fin(y, 22, -60)


def oyama_near(rng, i=1):
    L = 6.0
    n = ns(L)
    y = np.zeros(n)
    di, do = 2.4, 3.0
    mi = ns(di)
    keys = [(0, V.fs("o", V.VM, 0.5, bw=1.5)), (di, V.fs("u", V.VM, 0.5, bw=1.5))]
    inh = V.vt_noise(mi, keys, dsp.env_pts(mi, [(0, 0), (di * 0.6, 1.0), (di, 0)]), rng, tilt_db=-3.0, hp=60)
    dsp.place(y, inh * 0.6, 0.1, circular=True)
    mo = ns(do)
    eo = dsp.env_pts(mo, [(0, 0), (0.25, 1.0), (do * 0.6, 0.7), (do, 0)])
    exh = V.vt_noise(mo, [(0, V.fs("a", V.VM, 0.5, bw=1.4)), (do, V.fs("o", V.VM, 0.5))], eo, rng, tilt_db=-4.0, hp=50)
    rumble = V.creak(mo, 34 + 6 * dsp.smooth(mo, rng, 1.0), eo, rng, vowel="o", table=V.VM, scale=0.5, jitter=0.3)
    dsp.place(y, exh * 0.7 + rumble * np.std(exh) * 1.2, 0.1 + di + 0.2, circular=True)
    sub = dsp.sub_rumble(n, 34.0, L=L, rng=rng, am_rate=6.0, am_depth=0.55, drive=2.2)
    y = y / (np.std(y) + 1e-9) * 0.35 + sub * 0.5
    for _ in range(2):
        d = rng.uniform(0.5, 0.9)
        c = ins.creak(d, rng, [(0, 7), (d / 2, 16), (d, 6)], [(0, 0), (0.1, 0.7), (d, 0)], pitch=0.3, tonal=0.3)
        dsp.place(y, c * 0.12, rng.uniform(0, L), circular=True)
    y = dsp.circ(lambda x: dsp.lowpass(x, 2500, 2), y)
    ir = dsp.make_ir(1.8, rng, predelay=0.02, lp_hz=2500)
    y = dsp.circ_reverb(y, ir, wet=0.35, dry=0.85)
    return dsp.dc_block(y, circular=True)


def oyama_branch(rng, i=1):
    d = {1: 1.8, 2: 2.3, 3: 1.5}[i]
    y = np.zeros(ns(d + 1.5))
    for k in range({1: 3, 2: 5, 3: 2}[i]):
        dsp.place(y, ins.twig_snap(rng, thick=rng.uniform(0.55, 0.95), splinter=1.5), rng.uniform(0, d * 0.7))
    m = ns(d)
    sh = dsp.bandpass(rng.standard_normal(m), 1500, 8000, 2) * dsp.env_pts(m, [(0, 0), (0.1, 1.0), (d * 0.6, 0.6), (d, 0)])
    sh *= 0.6 + 0.4 * np.abs(dsp.smooth(m, rng, 25.0))
    sh += ins.crunch(d, rng, lambda t: 500 * np.exp(-t / (d / 2)), lo=1500, hi=8000) * 0.1
    dsp.place(y, sh * 0.12, 0.0)
    for k in range(4):
        dsp.place(y, _heavyish_debris(rng), rng.uniform(0.3, d))
    y = _rev(y, rng, rt=1.8, wet=0.4, lp=6000)
    return _fin(y, 40)


def _heavyish_debris(rng):
    m = ns(0.25)
    t = np.arange(m) / SR
    return (np.sin(TAU * 90 * t) * dsp.env_ad(m, 0.002, 0.03) * 0.3 +
            ins.crunch(0.25, rng, lambda tt: 1500 * np.exp(-tt / 0.03), lo=800, hi=5000) * 0.1)


# ==========================================================================
# 写るもの utsuru
# ==========================================================================
def utsuru_hum(rng, i=1):
    L = 6.0
    n = ns(L)
    st = dsp.colored(n, rng, -1.0, lo=350, hi=5500) * (0.7 + 0.3 * np.abs(dsp.smooth(n, rng, 6.0)))
    st += ins.crackle(L, rng, 40, hp=1500) * 0.4
    w = np.zeros(n)
    for t0, script, sc in ((0.6, "ko q chi da yo", 1.0), (3.4, "ko q chi", 0.96)):
        x = V.whisper(script, rng, scale=sc, rate=0.8, bw=1.3)
        dsp.place(w, x, t0, circular=True)
    w = dsp.circ(lambda x: dsp.bandpass(x, 450, 3500, 2), w)
    w = dsp.sat(w / (np.max(np.abs(w)) + 1e-9) * 1.5, 1.0)
    y = st / (np.std(st) + 1e-9) * 0.25 + w * 0.6
    t = np.arange(n) / SR
    y += 0.03 * np.sin(TAU * dsp.loop_freq(15600.0, L) * t)  # the camera's coil whine, at the edge of hearing
    return dsp.dc_block(y, circular=True)


def utsuru_turn(rng, i=1):
    m = ns(0.38)
    keys = [(0, V.fs("a", V.VF, 1.05, bw=1.3)), (0.38, V.fs("i", V.VF, 1.05, bw=1.3))]
    g = V.vt_noise(m, keys, dsp.env_pts(m, [(0, 0), (0.05, 1.0), (0.25, 0.8), (0.38, 0)]), rng, tilt_db=2.5)
    w = V.whisper("a -", rng, scale=1.05, rate=0.8, bw=1.3)
    y = np.zeros(m + len(w) + ns(0.3))
    dsp.place(y, g, 0.02)
    dsp.place(y, w * np.std(g) / (np.std(w) + 1e-9) * 0.5, 0.5)
    y = dsp.bandpass(y, 300, 9000, 2)
    y = _rev(y, rng, rt=0.8, wet=0.25)
    return dsp.trim(y, -60)


def oyama_recoil(rng, i=1):
    """oyama.recoil (extra) - 御山様 recoiling from the flare: a pained,
    hissing shriek inside a cracking tree, pulling away."""
    d = 2.2 if i == 1 else 1.8
    s, _ = V.scream(d, rng, [(0, 160), (0.1, 420), (0.6, 520), (d, 200)], [(0, 0), (0.05, 1.0), (d * 0.6, 0.7), (d, 0)],
                    vowel_pts=[(0, "i"), (0.5, "e"), (d, "u")], chaos_pts=[(0, 3.92), (d, 3.95)], ct=0.4, ca=0.55,
                    breath=1.0, rasp=1.8, scale=0.62, rough=(0.65, 48, 0.6), oq=0.42, closure=0.7)
    hiss = dsp.bandpass(rng.standard_normal(ns(d)), 1500, 9000, 2) * dsp.env_pts(ns(d), [(0, 0), (0.05, 1), (d, 0)])
    y = np.zeros(ns(d + 3.0))
    dsp.place(y, _nrm(s) + hiss * 0.12, 0.02)
    for k in range(3):
        dsp.place(y, ins.twig_snap(rng, thick=rng.uniform(0.6, 0.9)) * 0.4, rng.uniform(0.05, d * 0.7))
    m = ns(0.8)
    tt = np.arange(m) / SR
    wh = dsp.tv_filter(rng.standard_normal(m), 2500 * 2 ** (-tt * 3), 1.0, "bp") * np.exp(-tt / 0.3)
    dsp.place(y, wh * 0.15, d * 0.4)
    y = _nrm(y)
    y = 0.5 * dsp.sat(y * 2.0, 1.0) + 0.5 * y
    y = dsp.far(y, rng, rt60=3.0, wet=0.6, dry=0.85, lp=6000, predelay=0.03)
    return _fin(y, 30, -60)
