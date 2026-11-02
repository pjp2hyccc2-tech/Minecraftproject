"""amb.* one-shots: the forest's events (AI director), positional, mono
(except tinnitus / ear_pressure, which are inside your head)."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _forest_rev(y, rng, rt=1.4, wet=0.35, dry=0.9, lp=None, predelay=0.012):
    ir = dsp.make_ir(rt, rng, predelay=predelay, hf_ratio=0.4, lp_hz=lp)
    return dsp.reverb(y, ir, wet=wet, dry=dry)


def _fin(y, hp=40.0, thresh=-62.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh)


# --------------------------------------------------------------------------
# amb.branch_snap_1..5
# --------------------------------------------------------------------------
def branch_snap(rng, i=1):
    y = np.zeros(ns(1.6))
    if i == 1:  # a twig under a foot
        dsp.place(y, ins.leaf_step(rng, weight=1.1, heel=1.0, toe=0.7), 0.0)
        dsp.place(y, ins.twig_snap(rng, thick=0.12) * 0.9, 0.025)
    elif i == 2:  # a thick dead branch cracking under weight
        dsp.place(y, ins.leaf_step(rng, weight=1.3, toe=0.4) * 0.8, 0.0)
        dsp.place(y, ins.twig_snap(rng, thick=0.65, splinter=1.4), 0.05)
    elif i == 3:  # crack ... and it splits
        dsp.place(y, ins.twig_snap(rng, thick=0.35, double=True), 0.02)
    elif i == 4:  # further away, muffled
        s = ins.twig_snap(rng, thick=0.45)
        dsp.place(y, dsp.lowpass(s, 3500, 2) + 0.0, 0.02)
        y = _forest_rev(y, rng, rt=1.8, wet=0.7, dry=0.6, lp=3000)
        return _fin(y)
    else:  # one sharp, close crack
        dsp.place(y, ins.twig_snap(rng, thick=0.22, splinter=0.6), 0.01)
    y = _forest_rev(y, rng, rt=1.2, wet=0.25)
    return _fin(y)


# --------------------------------------------------------------------------
# footsteps (someone else)
# --------------------------------------------------------------------------
def _swish(rng, d=0.18, vel=1.0):
    """A fern / undergrowth branch whipping past a body."""
    m = ns(d)
    e = dsp.env_pts(m, [(0, 0), (d * 0.3, 1.0), (d, 0)]) ** 1.5
    fc = dsp.env_pts(m, [(0, 900), (d * 0.3, 3200), (d, 1500)])
    s = dsp.tv_filter(rng.standard_normal(m), fc, 1.2, "bp") * e
    s += ins.crunch(d, rng, lambda t: 700 * np.sin(np.pi * min(t / d, 1)), lo=1500, hi=7000) * 0.08
    return s * vel


def footsteps_run(rng, i=1):
    dur = {1: 3.6, 2: 3.2, 3: 3.9}[i]
    n = ns(dur)
    y = np.zeros(n)
    tt = np.arange(n) / SR
    if i == 1:  # passing: comes in, passes, recedes
        loud = dsp.env_pts(n, [(0, 0.15), (1.6, 1.0), (2.0, 1.0), (dur, 0.12)])
    elif i == 2:  # running away
        loud = dsp.env_pts(n, [(0, 1.0), (dur, 0.1)])
    else:  # circling around you in the dark
        loud = 0.3 + 0.7 * np.sin(np.pi * tt / dur * 2) ** 2
    t = 0.08
    k = 0
    while t < dur - 0.25:
        g = float(np.interp(t, tt, loud))
        st = ins.leaf_step(rng, weight=1.4, crunch_amt=1.3, heel=1.0, toe=0.8, pace=0.55, twig=0.6 if rng.random() < 0.12 else 0)
        dsp.place(y, st * g * (1.0 if k % 2 == 0 else 0.85), t)
        if rng.random() < 0.35:
            dsp.place(y, _swish(rng, rng.uniform(0.12, 0.22), 0.7 * g), t + rng.uniform(0.02, 0.15))
        t += rng.uniform(0.27, 0.33)
        k += 1
    lp = 2500 + 7000 * loud
    y = dsp.tv_filter(y, lp, 0.6, "lp", block=256)
    y = _forest_rev(y, rng, rt=1.4, wet=0.35)
    return dsp.fade(dsp.pad_to(dsp.highpass(y, 40, 2), n), 0.0, 0.2)


def footsteps_walk(rng, i=1):
    if i == 1:
        dur, steps = 5.0, [0.15, 0.95, 1.8, 2.58, 3.42]
    elif i == 2:
        dur, steps = 5.6, [0.15, 0.92, 1.7, 3.2, 3.98]
    else:
        dur, steps = 5.4, [0.15, 0.85, 1.55, 2.2, 2.85, 3.5]
    n = ns(dur)
    y = np.zeros(n)
    for k, t0 in enumerate(steps):
        g = 0.55 + 0.45 * k / (len(steps) - 1) if i == 3 else rng.uniform(0.8, 1.0)
        last = k == len(steps) - 1
        st = ins.leaf_step(rng, weight=1.1, crunch_amt=1.0, heel=1.0, toe=0.4 if last else 0.85, pace=1.2,
                           drag=0.0 if last else 0.25)
        dsp.place(y, st * g, t0 + rng.uniform(-0.03, 0.03))
    y = _forest_rev(y, rng, rt=1.3, wet=0.32)
    return dsp.pad_to(dsp.highpass(y, 40, 2), n)


# --------------------------------------------------------------------------
# amb.deer_scream_1..3 - sika stag rut call (November is the rut)
# --------------------------------------------------------------------------
def _deer_call(rng, dur, f0_pts, amp_pts, chaos_pts=None, biphon=None, jumps=(), rough=None):
    y, f0 = V.scream(dur, rng, f0_pts, amp_pts, vowel_pts=[(0, "u"), (dur * 0.3, "o"), (dur, "u")],
                     chaos_pts=chaos_pts, biphon=biphon, jumps=jumps, oq=0.55, closure=2.2, breath=0.08,
                     rasp=0.12, tilt_db=-2.0, flutter=0.012, flutter_rate=8.0, rough=rough, scale=1.15, f1_track=1.0)
    return y


def deer_scream(rng, i=1):
    if i == 1:  # one long piercing whistle-scream, sliding down into a moan
        dur = 2.7
        y = _deer_call(rng, dur, [(0, 1050), (0.12, 1900), (0.9, 1780), (1.7, 1450), (2.3, 820), (dur, 640)],
                       [(0, 0), (0.05, 0.9), (0.4, 1.0), (1.7, 0.8), (2.4, 0.35), (dur, 0)],
                       chaos_pts=[(0, 0), (1.85, 0), (1.95, 3.3), (2.2, 3.85), (dur, 3.9)],
                       biphon=dict(ratio=[(0, 1.19), (dur, 1.12)], amp=[(0, 0), (0.7, 0), (0.8, 0.45), (1.3, 0.45), (1.4, 0), (dur, 0)]),
                       rough=(np.clip(np.linspace(-0.6, 0.7, ns(dur)), 0, 0.7), 62, 0.6))
    elif i == 2:  # three calls, each lower
        dur = 3.0
        y = np.zeros(ns(dur))
        for k, (t0, fs_) in enumerate(((0.0, 1950), (1.0, 1750), (1.95, 1500))):
            d = 0.75 + 0.1 * k
            c = _deer_call(rng, d, [(0, fs_ * 0.6), (0.08, fs_), (d * 0.6, fs_ * 0.88), (d, fs_ * 0.5)],
                           [(0, 0), (0.04, 1.0), (d * 0.6, 0.8), (d, 0)],
                           chaos_pts=[(0, 0), (d * 0.7, 0), (d * 0.75, 3.7), (d, 3.9)],
                           rough=(np.clip(np.linspace(-0.5, 0.6, ns(d)), 0, 0.6), 70, 0.5))
            dsp.place(y, c * (1.0 - 0.12 * k), t0)
    else:  # human-like: a groan that jumps into a wail
        dur = 2.5
        y = _deer_call(rng, dur, [(0, 330), (0.25, 360), (0.3, 1450), (1.0, 1550), (1.8, 1100), (dur, 700)],
                       [(0, 0), (0.05, 0.6), (0.27, 0.7), (0.32, 1.0), (1.6, 0.85), (2.2, 0.3), (dur, 0)],
                       chaos_pts=[(0, 3.85), (0.27, 3.85), (0.28, 0), (1.5, 0), (1.55, 3.5), (1.9, 3.92), (dur, 3.92)],
                       biphon=dict(ratio=[(0, 1.31), (dur, 1.24)], amp=[(0, 0), (0.9, 0), (1.0, 0.5), (1.45, 0.5), (1.5, 0), (dur, 0)]),
                       jumps=[(0.28, 0.0, None)], rough=(0.45, 85, 0.6))
    y = dsp.highpass(y, 250, 2)
    y = dsp.far(y, rng, rt60=2.6, wet=0.85, dry=0.55, lp=6500, predelay=0.04, taps=[(0.42, 0.22, 3500)])
    return _fin(y, 120)


# --------------------------------------------------------------------------
# amb.fox_scream_1..2
# --------------------------------------------------------------------------
def fox_scream(rng, i=1):
    if i == 1:  # vixen: two long raspy shrieks
        y = np.zeros(ns(2.6))
        for k, t0 in enumerate((0.0, 1.25)):
            d = rng.uniform(0.75, 0.95)
            f = rng.uniform(620, 720)
            s, _ = V.scream(d, rng, [(0, f * 0.7), (0.06, f * 1.15), (d * 0.5, f), (d, f * 0.62)],
                            [(0, 0), (0.03, 1.0), (d * 0.7, 0.8), (d, 0)], vowel_pts=[(0, "a"), (d, "e")],
                            chaos_pts=[(0, 3.92), (d, 3.88)], ct=0.4, ca=0.55, breath=0.5, rasp=1.0, scale=1.25,
                            rough=(0.55, 75, 0.6), oq=0.4, closure=0.7)
            dsp.place(y, s * (1.0 - 0.15 * k), t0)
    else:  # a run of harsh bark-screams
        y = np.zeros(ns(2.2))
        t = 0.0
        for k in range(5):
            d = rng.uniform(0.14, 0.22)
            f = rng.uniform(700, 900)
            s, _ = V.scream(d, rng, [(0, f * 0.8), (0.03, f * 1.1), (d, f * 0.7)], [(0, 0), (0.015, 1.0), (d, 0)],
                            vowel="a", chaos_pts=[(0, 3.9), (d, 3.9)], ct=0.45, ca=0.6, breath=0.6, rasp=1.2,
                            scale=1.3, rough=(0.5, 90, 0.7), oq=0.4, closure=0.7)
            dsp.place(y, s * rng.uniform(0.75, 1.0), t)
            t += d + rng.uniform(0.12, 0.25)
    y = dsp.highpass(y, 300, 2)
    y = dsp.far(y, rng, rt60=2.0, wet=0.7, dry=0.65, lp=7000, predelay=0.03)
    return _fin(y, 150)


# --------------------------------------------------------------------------
# amb.owl_1..2 - Ural owl (フクロウ, 「ゴロスケホッホー」), far away
# --------------------------------------------------------------------------
def _hoot(rng, d, f, glide=-0.06, vel=1.0):
    n = ns(d)
    tt = np.arange(n) / SR
    ff = f * 2 ** (glide * np.sin(np.pi * np.clip(tt / d, 0, 1)) - 0.04 * tt / d) * (1 + 0.004 * dsp.smooth(n, rng, 10))
    env = np.sin(np.pi * np.clip(tt / d, 0, 1)) ** 0.8 * np.minimum(1, tt / 0.04)
    ph = np.cumsum(ff) / SR
    y = np.sin(TAU * ph) + 0.12 * np.sin(2 * TAU * ph + 0.5) + 0.03 * np.sin(3 * TAU * ph)
    br = dsp.tv_filter(rng.standard_normal(n), ff * 1.0, 6.0, "bp") * 0.25
    return (y + br) * env * vel


def owl(rng, i=1):
    base = rng.uniform(370, 410)
    if i == 1:  # ホー … ホー … ゴロスケ ホッホー
        notes = [(0.0, 0.5, 1.0, 1.0), (0.95, 0.45, 0.98, 0.9), (2.9, 0.09, 1.04, 0.6), (3.02, 0.09, 1.07, 0.65),
                 (3.14, 0.09, 1.02, 0.6), (3.26, 0.1, 0.97, 0.6), (3.5, 0.16, 1.0, 0.8), (3.78, 0.6, 0.97, 0.95)]
        dur = 5.6
    else:  # wohu ... wohu-hu-wohu
        notes = [(0.0, 0.32, 1.0, 0.9), (0.38, 0.4, 0.94, 1.0), (1.9, 0.3, 1.0, 0.85), (2.25, 0.18, 1.05, 0.7),
                 (2.5, 0.32, 1.0, 0.9), (2.86, 0.5, 0.93, 1.0)]
        dur = 4.6
    y = np.zeros(ns(dur))
    for t0, d, r, v in notes:
        dsp.place(y, _hoot(rng, d, base * r, vel=v), t0 + 0.05)
    y = dsp.far(y, rng, rt60=1.9, wet=0.45, dry=0.8, lp=2800, predelay=0.05, taps=[(0.5, 0.12, 1800)])
    return _fin(y, 120)


# --------------------------------------------------------------------------
# amb.tree_knock_1..3 - deliberate knocks on a cedar trunk
# --------------------------------------------------------------------------
def tree_knock(rng, i=1):
    pat = {1: [0.0, 0.46, 0.92], 2: [0.0, 0.85], 3: [0.0, 0.36, 0.72, 1.75]}[i]
    y = np.zeros(ns(pat[-1] + 2.0))
    size = rng.uniform(0.9, 1.1)
    for k, t0 in enumerate(pat):
        v = 1.0 if k == 0 else rng.uniform(0.85, 1.0)
        if i == 3 and k == 3:
            v = 1.2
        h = ins.modal_hit(rng, ins.TRUNK, size=size, hardness=0.55, vel=v, dur=0.6, noise=0.35)
        m = ns(0.15)
        thump = np.sin(TAU * 105 * np.arange(m) / SR) * dsp.env_ad(m, 0.002, 0.03)
        dsp.place(y, h + dsp.pad_to(thump * 0.4, len(h)), 0.02 + t0 + rng.uniform(0, 0.01))
    y = dsp.lowpass(y, 4500, 2)
    y = _forest_rev(y, rng, rt=1.7, wet=0.5, dry=0.8, lp=4000)
    return _fin(y, 50)


# --------------------------------------------------------------------------
# amb.rockfall_1..2
# --------------------------------------------------------------------------
def rockfall(rng, i=1):
    dur = 3.4 if i == 1 else 2.8
    n = ns(dur)
    y = np.zeros(n)
    m = ns(0.35)  # the first stone working loose
    sc = dsp.bandpass(rng.standard_normal(m), 600, 5000, 2) * dsp.env_pts(m, [(0, 0), (0.25, 1), (0.35, 0)])
    dsp.place(y, sc * 0.15, 0.0)
    t, gap = 0.35, 0.32
    while t < dur - 0.6:  # the big stone bouncing down, accelerating
        h = ins.modal_hit(rng, ins.STONE, size=rng.uniform(1.4, 2.2), hardness=0.9, vel=rng.uniform(0.6, 1.0), dur=0.15, noise=0.6)
        thud = dsp.lowpass(rng.standard_normal(ns(0.1)), 400, 2) * dsp.env_ad(ns(0.1), 0.002, 0.02)
        dsp.place(y, h * 0.8 + dsp.pad_to(thud, len(h)) * 0.9, t)
        t += gap * rng.uniform(0.7, 1.3)
        gap = max(0.09, gap * 0.82)

    def dens(tt):  # pebbles following
        return 600 * np.exp(-((tt - 1.4) / 0.8) ** 2) + 80
    peb = np.zeros(n)
    tt = 0.5
    while tt < dur - 0.3:
        tt += rng.exponential(1.0 / dens(tt))
        h = ins.modal_hit(rng, ins.STONE, size=rng.uniform(0.5, 1.1), hardness=1.0, vel=rng.pareto(2.5) * 0.15 + 0.03, dur=0.05, noise=0.7)
        dsp.place(peb, h, tt)
    y += peb * 0.5
    gr = ins.crunch(dur, rng, lambda t_: 900 * np.exp(-((t_ - 1.5) / 0.9) ** 2), lo=1500, hi=6000)
    y += gr * 0.05
    y = dsp.lowpass(y, 7000, 2)
    y = _forest_rev(y, rng, rt=1.9, wet=0.5, dry=0.8, lp=5000)
    return _fin(y, 50)


# --------------------------------------------------------------------------
# amb.creak_tree_1..4 - a cedar trunk groaning as it bends
# --------------------------------------------------------------------------
def creak_tree(rng, i=1):
    d = {1: 2.2, 2: 3.2, 3: 1.6, 4: 2.6}[i]
    rate = {1: [(0, 7), (d * 0.5, 22), (d, 9)], 2: [(0, 5), (d * 0.3, 14), (d * 0.6, 28), (d, 6)],
            3: [(0, 10), (d * 0.4, 30), (d, 12)], 4: [(0, 6), (d * 0.7, 26), (d, 20)]}[i]
    amp = [(0, 0), (0.25, 0.8), (d * 0.7, 1.0), (d, 0)]
    y = ins.creak(d, rng, rate, amp, pitch=rng.uniform(0.32, 0.45), jitter=0.3, q_scale=1.4, tonal=0.45, noise=0.05)
    fib = ins.crunch(d, rng, lambda t: 120 * np.sin(np.pi * t / d), lo=1200, hi=5000)
    y = y + fib * 0.04
    out = np.zeros(ns(d + 1.5))
    dsp.place(out, y, 0.02)
    if i == 4:  # and something gives
        dsp.place(out, ins.twig_snap(rng, thick=0.75, splinter=1.5) * 0.8, d - 0.05)
    out = dsp.lowpass(out, 3800, 2)
    out = _forest_rev(out, rng, rt=1.8, wet=0.45, dry=0.85, lp=3500)
    return _fin(out, 45)


# --------------------------------------------------------------------------
# amb.thud_1..3 - something heavy falling in the distance
# --------------------------------------------------------------------------
def _heavy_fall(rng, vel=1.0):
    m = ns(0.7)
    t = np.arange(m) / SR
    sub = np.sin(TAU * np.cumsum(42 + 38 * np.exp(-t / 0.035)) / SR) * dsp.env_ad(m, 0.003, 0.11)
    sub = dsp.sat(sub * 1.6, 1.0)
    nz = dsp.lowpass(rng.standard_normal(m), 300, 2) * dsp.env_ad(m, 0.002, 0.05)
    cr = ins.crunch(0.5, rng, lambda tt: 2500 * np.exp(-tt / 0.06), lo=700, hi=4000)
    return (sub + nz * 0.7 + dsp.pad_to(cr, m) * 0.12) * vel


def thud(rng, i=1):
    y = np.zeros(ns(3.0))
    dsp.place(y, _heavy_fall(rng), 0.03)
    if i == 2:
        dsp.place(y, _heavy_fall(rng, 0.45), 0.36)
    if i == 3:
        dsp.place(y, ins.twig_snap(rng, thick=0.8) * 0.5, 0.0)
        for k in range(5):
            dsp.place(y, ins.twig_snap(rng, thick=0.1) * 0.15, 0.1 + k * rng.uniform(0.05, 0.12))
    y = dsp.lowpass(y, 1100, 3)
    y = _forest_rev(y, rng, rt=2.2, wet=0.75, dry=0.7, lp=1200, predelay=0.03)
    return _fin(y, 25)


# --------------------------------------------------------------------------
# whispers (ultra-close, dry, at one ear)
# --------------------------------------------------------------------------
WHISPERS = {
    1: [("ka e re na i yo", 1.0, 1.05, 0.0, 1.0)],
    2: [("mi te ru , ko q chi", 1.02, 1.1, 0.0, 1.0, "rev")],
    3: [("ma da , ma da", 1.0, 0.9, 0.0, 1.0), ("ya ma no na ka", 0.93, 1.0, 0.35, 0.75)],
    4: [("su ku ra shi ma ta ne ko so ri tsu", 1.0, 1.45, 0.0, 1.0)],
    5: [("mi i tsu ke ta -", 1.0, 0.72, 0.0, 1.0)],
    6: [("ha a . ne e", 1.04, 0.85, 0.0, 0.9), ("ko q chi", 1.0, 0.8, 1.15, 1.0)],
}


def _ear(y, rng, clicks=1.0):
    y = dsp.highpass(y, 110, 2)
    y = dsp.close_mic(y, prox_db=5.0, pres_db=4.0, air_db=3.0)
    if clicks > 0:
        mc = V.mouth_clicks(len(y) / SR, rng, rate=1.5) * np.std(y) * 2.0 * clicks
        y = y + dsp.pad_to(mc, len(y))
    return y


def whisper(rng, i=1):
    parts = []
    for item in WHISPERS[i]:
        script, scale, rate, t0, g = item[:5]
        w = V.whisper(script, rng, scale=scale, rate=rate, intensity=g, close=1.0, bw=1.3)
        if len(item) > 5:
            w = dsp.reverse(w)
        parts.append(dsp.place(np.zeros(ns(t0) + len(w)), w, t0))
    y = dsp.mix(*parts)
    y = _ear(y, rng)
    return dsp.trim(y, -60)


def whisper_name(rng, i=1):
    if i == 1:
        w = V.whisper("ne e", rng, scale=1.07, rate=0.62, close=1.0, bw=1.2, f1=1.08)
        m = ns(0.7)
        br = V.breath(rng, "out", 0.7, table=V.VF, scale=1.07)
        y = np.zeros(len(w) + m)
        dsp.place(y, w, 0)
        dsp.place(y, br * np.std(w) / (np.std(br) + 1e-9) * 0.35, len(w) / SR - 0.1)
    else:
        w = V.whisper("ne e - -", rng, scale=1.1, rate=0.55, close=0.8, bw=1.15, voicing=0.16, f0=245)
        tt = np.arange(len(w)) / SR
        y = w * (0.75 + 0.25 * np.sin(np.pi * np.clip(tt / tt[-1], 0, 1)))
    y = _ear(y, rng, clicks=0.6)
    return dsp.trim(y, -60)


def giggle(rng, i=1):
    if i == 1:  # ふふふ (closed lips)
        y = V.laugh(rng, n_syl=5, f0=300, decline=0.035, table=V.VF, scale=1.08, nasal=True, breath=1.1, tilt=-11)
    elif i == 2:  # くすくす - mostly breath
        y = V.laugh(rng, n_syl=6, f0=330, decline=0.02, gap=(0.03, 0.06), syl=(0.06, 0.09), vowel="u", table=V.VF,
                    scale=1.1, breath=2.4, tilt=-13, h_gain=2.0)
    else:  # ひひ, slightly wrong
        y = V.laugh(rng, n_syl=4, f0=420, decline=0.05, vowel="i", table=V.VF, scale=1.12, breath=1.3, tilt=-10,
                    broken=0.35, sub=0.25)
    y = dsp.highpass(y, 150, 2)
    ir = dsp.make_ir(0.6, rng, predelay=0.006, lp_hz=6000)
    y = dsp.reverb(y, ir, wet=0.25)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# amb.distant_bell_1..3 - someone else's bear bell, far away, walking
# --------------------------------------------------------------------------
def distant_bell(rng, i=1):
    dur = {1: 5.5, 2: 4.6, 3: 5.0}[i]
    strikes = []
    t = 0.2
    stop = 3.4 if i == 3 else dur - 0.6
    k = 0
    while t < stop:
        strikes.append((t, rng.uniform(0.55, 1.0)))
        if rng.random() < 0.6:
            strikes.append((t + rng.uniform(0.18, 0.3), rng.uniform(0.25, 0.6)))
        t += rng.uniform(0.52, 0.6)
        k += 1
    y = ins.bear_bell(rng, dur, strikes, base=rng.uniform(1550, 1750))
    n = len(y)
    tt = np.arange(n) / SR
    if i == 1:
        y *= dsp.env_pts(n, [(0, 0.3), (dur, 1.0)])
    if i == 3:  # a hand closes around the bell
        y *= np.where(tt < 3.55, 1.0, np.exp(-(tt - 3.55) / 0.03))
    y = dsp.far(y, rng, rt60=2.6, wet=0.9, dry=0.3, lp=3600, predelay=0.05)
    return dsp.fade(dsp.pad_to(dsp.highpass(y, 200, 2), ns(dur + 1.0)), 0.0, 0.6)


# --------------------------------------------------------------------------
# amb.distant_scream_1..2 - a human scream far away, cut off
# --------------------------------------------------------------------------
def distant_scream(rng, i=1):
    if i == 1:
        d = 1.45
        s, _ = V.scream(d + 0.3, rng, [(0, 650), (0.08, 1050), (0.7, 1150), (d, 980), (d + 0.3, 900)],
                        [(0, 0), (0.05, 1.0), (d - 0.01, 0.9), (d, 0.0), (d + 0.3, 0)], vowel_pts=[(0, "a"), (d, "a")],
                        chaos_pts=[(0, 0), (0.9, 0), (1.0, 3.6), (d, 3.9)], breath=0.4, rough=(0.45, 70, 0.5), scale=1.05)
    else:
        d = 1.25
        s, _ = V.scream(d + 0.3, rng, [(0, 330), (0.1, 560), (0.6, 600), (d, 520), (d + 0.3, 500)],
                        [(0, 0), (0.06, 1.0), (d - 0.01, 0.95), (d, 0.0), (d + 0.3, 0)], vowel_pts=[(0, "a"), (d, "o")],
                        chaos_pts=[(0, 3.7), (0.15, 3.7), (0.2, 0), (0.8, 0), (0.9, 3.85), (d, 3.9)], breath=0.35,
                        rough=(0.5, 60, 0.5), scale=0.9)
    y = dsp.highpass(s, 300, 2)
    y = dsp.far(y, rng, rt60=3.4, wet=1.0, dry=0.35, lp=2800, predelay=0.07, taps=[(0.55, 0.3, 1800), (1.2, 0.15, 1400)])
    return _fin(y, 150, -60)


def distant_call(rng, i=1):
    if i == 1:
        c = V.call_oi(rng, dur_o=1.0, f0=205)
    elif i == 2:
        a = V.call_oi(rng, dur_o=0.85, f0=212)
        c = np.zeros(len(a) + ns(1.9))
        dsp.place(c, a, 0)
        dsp.place(c, a * 0.95, 1.9)  # the same call again - exactly the same
    else:
        c = V.call_oi(rng, dur_o=1.2, f0=198, end_drop=0.7)
    y = dsp.highpass(c, 160, 2)
    y = dsp.far(y, rng, rt60=3.6, wet=1.0, dry=0.25, lp=2200, predelay=0.08, taps=[(0.5, 0.35, 1500), (1.1, 0.2, 1200)])
    return _fin(y, 120, -60)


def breath_behind(rng, i=1):
    if i == 1:
        cyc = [("in", 1.1), ("out", 1.5), ("in", 1.0), ("out", 1.6)]
    elif i == 2:
        cyc = [("in", 1.4), ("out", 2.2)]
    else:
        cyc = [("in", 0.9), ("out", 1.1), ("in", 0.9), ("out", 1.4), ("in", 0.7)]
    y = np.zeros(ns(sum(d for _, d in cyc) + 0.8))
    t = 0.05
    for kind, d in cyc:
        b = V.breath(rng, kind, d, table=V.VM, scale=0.9, voiced=0.12 if kind == "out" else 0.0, f0=95,
                     vowel_out="o", wet=0.35, rasp=0.15 if kind == "in" else 0.25)
        dsp.place(y, b, t)
        t += d + rng.uniform(0.05, 0.25)
    y = dsp.lowpass(y, 9000, 2)
    y = dsp.close_mic(y, prox_db=6.0, pres_db=2.0, air_db=2.0)
    return dsp.trim(dsp.highpass(y, 70, 2), -60)


# --------------------------------------------------------------------------
# tunnel / village props
# --------------------------------------------------------------------------
def drip(rng, i=1):
    from sfx_beds import _drip, _tunnel_ir
    y = np.zeros(ns(4.0))
    f0 = {1: 820, 2: 1150, 3: 640, 4: 980}[i] * rng.uniform(0.95, 1.05)
    dsp.place(y, _drip(rng, f0), 0.02)
    if i == 4:
        dsp.place(y, _drip(rng, f0 * 1.12, 0.6), 0.47)
    ir = _tunnel_ir(rng)[0]
    y = dsp.reverb(y, ir, wet=0.55, dry=0.8)
    return _fin(y, 200, -60)


def tin_bang(rng, i=1):
    y = np.zeros(ns(3.2))
    hits = [(0.02, 1.0)] if i == 1 else [(0.02, 0.8), (0.31, 1.0)]
    for t0, v in hits:
        h = ins.modal_hit(rng, ins.TIN, size=rng.uniform(0.9, 1.1), hardness=0.8, vel=v, dur=2.0, noise=0.5)
        wob = 1 + 0.08 * np.sin(TAU * rng.uniform(5, 8) * np.arange(len(h)) / SR) * np.exp(-np.arange(len(h)) / (0.4 * SR))
        dsp.place(y, h * wob, t0)
        dsp.place(y, _tin_rattle_short(rng, 0.35, v * 0.4), t0 + 0.04)
    y = _forest_rev(y, rng, rt=1.5, wet=0.4)
    return _fin(y, 50)


def _tin_rattle_short(rng, d, vel):
    y = np.zeros(ns(d + 0.8))
    t = 0.0
    while t < d:
        h = ins.modal_hit(rng, ins.TIN, size=rng.uniform(0.6, 0.8), hardness=0.8, vel=vel * rng.uniform(0.3, 1.0) * (1 - t / d), dur=0.6)
        dsp.place(y, h, t)
        t += rng.uniform(0.02, 0.06)
    return y


def wind_chime(rng, i=1):
    base = rng.uniform(2300, 2600) if i == 1 else rng.uniform(1800, 2050)
    y = np.zeros(ns(3.2))
    dsp.place(y, ins.furin(rng, base, 1.0, detune=40 if i == 1 else 28, dur=3.0), 0.02)
    dsp.place(y, ins.furin(rng, base * dsp.cents(-55), 0.35, detune=40, dur=2.5), 0.02 + rng.uniform(0.15, 0.3))
    y = _forest_rev(y, rng, rt=1.4, wet=0.3)
    return _fin(y, 300)


def radio(rng, i=1):
    dur = 4.6 if i == 1 else 3.6
    n = ns(dur)
    tt = np.arange(n) / SR
    st = dsp.bandpass(rng.standard_normal(n), 250, 5000, 2) * (0.6 + 0.4 * np.abs(dsp.smooth(n, rng, 8.0)))
    st += ins.crackle(dur, rng, 90, hp=1200) * 0.6
    het = np.sin(TAU * np.cumsum(np.interp(tt, [0, dur], [rng.uniform(2200, 3500), rng.uniform(1500, 4000)])) / SR)
    st += het * 0.12 * (0.5 + 0.5 * np.sin(TAU * 0.4 * tt))
    v = np.zeros(n)
    t = 0.3 if i == 1 else 0.9
    scripts = ["ko chi ra wa | ya ma no | sa N so u", "ki N kyu u | ho u so u | de su", "sa ga shi | te i ma su"]
    k = 0
    while t < dur - 0.8:
        s = V.speak(scripts[k % 3], rng, table=V.VM if k % 2 == 0 else V.VF, f0=118 if k % 2 == 0 else 205,
                    rate=1.15, breath=0.1, tilt=-6)
        L_ = min(len(s), ns(rng.uniform(0.6, 1.3)))
        frag = s[:L_] * dsp.env_pts(L_, [(0, 0), (0.05, 1), (L_ / SR - 0.08, 1), (L_ / SR, 0)])
        dsp.place(v, frag, t)
        t += L_ / SR + rng.uniform(0.2, 0.6)
        k += 1
    v = dsp.bandpass(v, 350, 2800, 3)
    v = dsp.sat(v / (np.max(np.abs(v)) + 1e-9) * 2.0, 1.0)
    fade = np.clip(0.55 + 0.6 * dsp.smooth(n, rng, 0.8), 0.05, 1.0)  # selective fading
    y = st / (np.std(st) + 1e-9) * 0.35 + v * fade * 0.5
    if i == 2:  # tuning sweep
        m = ns(0.9)
        sw = np.sin(TAU * np.cumsum(np.linspace(600, 4000, m) * (1 + 0.3 * np.sin(np.linspace(0, 30, m)))) / SR)
        dsp.place(y, sw * dsp.env_pts(m, [(0, 0), (0.1, 0.3), (0.9, 0)]), 0.0)
    y = dsp.bandpass(y, 200, 5000, 2)
    y = dsp.reverb(y, dsp.make_ir(0.7, rng, predelay=0.005), wet=0.25)
    return dsp.fade(dsp.pad_to(y, n), 0.15, 0.3)


# --------------------------------------------------------------------------
# inside your head (stereo)
# --------------------------------------------------------------------------
def tinnitus(rng, i=1):
    dur = 4.0
    n = ns(dur)
    t = np.arange(n) / SR
    env = dsp.env_pts(n, [(0, 0), (0.15, 1.0), (1.8, 0.8), (4.0, 0.0)]) ** 1.4
    out = np.zeros((2, n))
    for c, (f1, f2) in enumerate(((7040.0, 8770.0), (7046.0, 8820.0))):
        fm = f1 * (1 + 0.0012 * dsp.smooth(n, rng, 0.7))
        y = np.sin(TAU * np.cumsum(fm) / SR) * (1 + 0.08 * dsp.smooth(n, rng, 2.0))
        y += 0.25 * np.sin(TAU * f2 * t + rng.random() * 6)
        y += 0.05 * dsp.bandpass(rng.standard_normal(n), 6200, 9000, 2)
        out[c] = y * env
    return dsp.fade(out, 0.005, 0.05)


def ear_pressure(rng, i=1):
    dur = 3.0
    n = ns(dur)
    t = np.arange(n) / SR
    pop = 2.62
    sw = dsp.env_pts(n, [(0, 0), (2.55, 1.0), (pop, 1.0), (pop + 0.02, 0.0), (dur, 0)]) ** 1.6
    f = 26 + 12 * np.clip(t / pop, 0, 1)
    sub = dsp.sat(np.sin(TAU * np.cumsum(f) / SR) * 1.8, 1.0)
    out = np.zeros((2, n))
    for c in range(2):
        nz = dsp.colored(n, rng, -6.0, lo=18, hi=200)
        fc = 2500 * (1 - 0.9 * np.clip(t / pop, 0, 1)) + 120
        muff = dsp.tv_filter(dsp.colored(n, rng, -2.0, lo=100, hi=6000), fc, 0.7, "lp", block=256) * 0.15
        out[c] = (sub * 0.6 + nz * 0.5 + muff) * sw
    m = ns(0.006)
    click = dsp.lowpass(rng.standard_normal(m), 2500, 2) * np.exp(-np.arange(m) / (0.0012 * SR))
    dsp.place(out, np.vstack([click, click * 0.8]) * 1.2, pop)
    m = ns(0.35)
    op = dsp.highpass(rng.standard_normal(m), 2500, 2) * dsp.env_ad(m, 0.003, 0.06)
    dsp.place(out, np.vstack([op, op * 0.9]) * 0.18, pop + 0.004)
    return dsp.fade(out, 0.01, 0.1)


# --------------------------------------------------------------------------
# extra events (not in DESIGN.md section 7, proposed in the sound report)
# --------------------------------------------------------------------------
STONE_MODES = [(310, 25, 1.0), (590, 30, 0.8), (1020, 35, 0.6), (1680, 40, 0.4), (2600, 45, 0.25)]


def stone_grind(rng, i=1):
    """amb.stone_grind - a stone jizo turning on its base to face you."""
    d = 1.9 if i == 1 else 1.3
    y = np.zeros(ns(d + 1.0))
    g = ins.creak(d, rng, [(0, 14), (d * 0.5, 34), (d, 12)], [(0, 0), (0.2, 0.9), (d * 0.8, 1.0), (d, 0)],
                  modes=STONE_MODES, jitter=0.45, noise=0.25)
    gr = ins.crunch(d, rng, lambda t: 900 * np.sin(np.pi * min(t / d, 1.0)) ** 0.5, lo=1500, hi=6500, dec=(0.0002, 0.0008))
    rum = dsp.lowpass(rng.standard_normal(ns(d)), 220, 2) * dsp.env_pts(ns(d), [(0, 0), (0.2, 1), (d, 0)])
    dsp.place(y, _nrm(g) * 0.7 + gr * 0.08 + rum * 0.25, 0.02)
    dsp.place(y, ins.modal_hit(rng, ins.STONE, size=2.6, hardness=0.6, vel=0.7, dur=0.2, noise=0.3), d + 0.02)
    y = _forest_rev(y, rng, rt=1.0, wet=0.25)
    return _fin(y, 60)


def bridge_creak(rng, i=1):
    """amb.bridge_creak - the old suspension bridge: ropes, planks, cables."""
    d = {1: 1.4, 2: 2.2, 3: 1.0, 4: 1.8}[i]
    y = np.zeros(ns(d + 1.2))
    rope = ins.creak(d, rng, [(0, 40), (d * 0.5, 140), (d, 60)], [(0, 0), (0.1, 0.8), (d * 0.7, 1.0), (d, 0)],
                     modes=[(f, 14, a) for f, a in ((480, 1.0), (910, 0.8), (1530, 0.5), (2300, 0.3))], tonal=0.5, jitter=0.2)
    plank = ins.creak(d * 0.8, rng, [(0, 15), (d * 0.4, 35), (d * 0.8, 12)], [(0, 0), (0.15, 0.7), (d * 0.8, 0)], pitch=0.7, jitter=0.35)
    dsp.place(y, _nrm(rope) * 0.6, 0.02)
    dsp.place(y, _nrm(plank) * 0.5, rng.uniform(0.0, 0.3))
    if i in (2, 4):  # a steel cable pings far below
        dsp.place(y, ins.modal_hit(rng, ins.METAL, size=2.4, hardness=0.8, vel=0.25, dur=1.2), rng.uniform(0.3, d))
    y = dsp.far(y, rng, rt60=2.2, wet=0.45, dry=0.9, lp=7000, predelay=0.02)
    return _fin(y, 60)


def under_bridge(rng, i=1):
    """amb.under_bridge - something under the planks: knocks from below, claws."""
    y = np.zeros(ns(3.4))
    t = 0.05
    for k in range(rng.integers(3, 6)):
        h = ins.modal_hit(rng, ins.WOOD_FLOOR, size=1.4, hardness=0.4, vel=rng.uniform(0.5, 1.0), dur=0.5, noise=0.2)
        dsp.place(y, dsp.lowpass(h, 1500, 2), t)
        t += rng.uniform(0.25, 0.7)
    for k in range(2 if i == 1 else 4):  # long nails dragged along the underside
        d = rng.uniform(0.3, 0.6)
        m = ns(d)
        e = dsp.env_pts(m, [(0, 0), (0.04, 1), (d * 0.7, 0.8), (d, 0)])
        sp = ins.friction_pulses(d, rng, rng.uniform(80, 160), e, jitter=0.4)
        body = dsp.resbank(sp, [(f, 12, g) for f, _, g in ins.WOOD_DOOR])
        dsp.place(y, _nrm(body) * 0.35 + dsp.bandpass(rng.standard_normal(m), 1500, 6000, 2) * e * 0.05, rng.uniform(0.3, 2.6))
    y = dsp.far(y, rng, rt60=1.8, wet=0.5, dry=0.8, lp=4500, predelay=0.02)
    return _fin(y, 50)


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)
