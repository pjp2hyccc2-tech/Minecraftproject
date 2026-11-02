"""player.*, block.* and item.* sounds (mono, positional)."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _small_rev(y, rng, rt=0.45, wet=0.15):
    return dsp.reverb(y, dsp.make_ir(rt, rng, predelay=0.003, lp_hz=8000), wet=wet)


def _fin(y, hp=40.0, thresh=-62.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh)


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


# --------------------------------------------------------------------------
# player
# --------------------------------------------------------------------------
def heartbeat(rng, i=1):
    """One deep, muffled lub-dub (<= 0.5 s so it still works at 180 bpm).
    The thump is saturated so its 2nd-4th harmonics carry it on small
    speakers."""
    n = ns(0.5)
    y = np.zeros(n)
    for t0, (fa, fb), g, T in ((0.008, (66, 42), 1.0, 0.07), (0.205, (76, 50), 0.6, 0.05)):
        m = ns(0.26)
        t = np.arange(m) / SR
        f = fb + (fa - fb) * np.exp(-t / 0.035)
        e = (1 - np.exp(-t / 0.005)) * np.exp(-t / T)
        th = np.sin(TAU * np.cumsum(f) / SR) * e
        th = np.tanh(2.2 * (th + 0.25)) - np.tanh(0.55)
        cl = dsp.lowpass(rng.standard_normal(m), 700, 2) * np.exp(-t / 0.004) * 0.2
        dsp.place(y, (th + cl) * g, t0)
    y = dsp.lowpass(y, 320, 2)
    return dsp.fade(dsp.highpass(y, 22, 2), 0.001, 0.04)


def _seq(rng, dur, parts, **common):
    y = np.zeros(ns(dur))
    for t0, args, kw in parts:
        k = dict(common)
        k.update(kw)
        dsp.place(y, V.breath(rng, *args, **k), t0)
    return y


def _breath_post(y, rng, room=0.1):
    y = dsp.lowpass(y, 8500, 2)
    y = dsp.close_mic(y, prox_db=3.0, pres_db=2.0, air_db=1.0)
    y = _small_rev(y, rng, 0.4, room)
    return _fin(y, 90, -60)


def breath_fear(rng, i=1):
    c = dict(table=V.VM, scale=1.0)
    if i == 1:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.85), dict(tremor=0.6, trem_rate=9.5)),
                            (1.0, ("out", 1.05), dict(tremor=0.55, trem_rate=7.0, voiced=0.3, f0=140)),
                            (2.25, ("in", 0.45), dict(tremor=0.5, trem_rate=11.0))], **c)
    elif i == 2:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.32), dict(tremor=0.4)), (0.4, ("out", 0.42), dict(tremor=0.4, voiced=0.25)),
                            (0.9, ("in", 0.3), dict(tremor=0.5)), (1.24, ("out", 0.45), dict(tremor=0.5, voiced=0.3, f0=145)),
                            (1.8, ("in", 0.55), dict(tremor=0.6, trem_rate=10.0))], **c)
    else:
        y = _seq(rng, 3.2, [(0.05, ("in", 0.95), dict(tremor=0.35, hiss=0.25)),
                            (1.15, ("out", 1.6), dict(tremor=0.75, trem_rate=6.0, voiced=0.22, f0=125))], **c)
    return _breath_post(y, rng)


def breath_run(rng, i=1):
    parts = []
    t = 0.05
    cyc = 0.5 if i == 1 else 0.62
    for c in range(7 if i == 1 else 6):
        di = cyc * rng.uniform(0.38, 0.45)
        do = cyc * rng.uniform(0.5, 0.58)
        parts.append((t, ("in", di), dict(tremor=0.1, hiss=0.15)))
        parts.append((t + di + 0.01, ("out", do), dict(voiced=0.5 if i == 1 else 0.65, f0=rng.uniform(115, 130))))
        t += di + do + rng.uniform(0.0, 0.04)
    y = _seq(rng, t + 0.3, parts, table=V.VM)
    return _breath_post(y, rng)


def gasp(rng, i=1):
    if i == 1:
        y = np.zeros(ns(1.0))
        g = V.breath(rng, "in", 0.36, voiced=0.6, f0=190, vowel_in="a")
        g *= np.exp(-np.linspace(0, 1, len(g)) * 0.6)
        g[:ns(0.012)] *= np.linspace(0, 1, ns(0.012))
        dsp.place(y, g * 1.6, 0.02)
    else:
        y = np.zeros(ns(1.2))
        dsp.place(y, V.breath(rng, "in", 0.12, voiced=0.3, f0=175, vowel_in="a") * 0.9, 0.02)
        dsp.place(y, V.breath(rng, "in", 0.42, voiced=0.7, f0=205, vowel_in="a") * 1.6, 0.17)
    cl = dsp.bandpass(rng.standard_normal(ns(0.006)), 600, 2500) * 0.05  # glottis closes: breath held
    dsp.place(y, cl, 0.4 if i == 1 else 0.6)
    return _breath_post(y, rng, 0.12)


def fall(rng, i=1):
    """Intro: tumbling down the slope through leaves and branches, a heavy
    impact, then the ears ring."""
    dur = 3.0
    n = ns(dur)
    y = np.zeros(n)
    t = 0.0
    k = 0
    while t < 1.55:  # tumbling: body rolls, cloth, impacts, branches
        st = ins.leaf_step(rng, weight=1.8, crunch_amt=1.6, heel=1.0, toe=1.0, pace=0.5, dur=0.4,
                           twig=1.0 if rng.random() < 0.3 else 0.0)
        dsp.place(y, st * rng.uniform(0.9, 1.5), t)
        if rng.random() < 0.5:
            m = ns(0.2)
            body = np.sin(TAU * 80 * np.arange(m) / SR) * dsp.env_ad(m, 0.003, 0.04)
            dsp.place(y, body * 0.3, t + 0.02)
        if rng.random() < 0.4:
            m = ns(0.25)
            cloth = dsp.bandpass(rng.standard_normal(m), 400, 4000, 2) * dsp.env_pts(m, [(0, 0), (0.08, 1), (0.25, 0)])
            dsp.place(y, cloth * 0.12, t)
        t += rng.uniform(0.08, 0.16)
        k += 1
    m = ns(1.0)  # the impact
    tt = np.arange(m) / SR
    imp = dsp.sat(np.sin(TAU * np.cumsum(38 + 60 * np.exp(-tt / 0.03)) / SR) * dsp.env_ad(m, 0.002, 0.16) * 2.4, 1.0)
    imp += dsp.lowpass(rng.standard_normal(m), 400, 2) * dsp.env_ad(m, 0.001, 0.05) * 1.2
    imp += dsp.pad_to(ins.crunch(0.5, rng, lambda x: 4000 * np.exp(-x / 0.05), lo=600, hi=6000) * 0.25, m)
    dsp.place(y, imp * 1.3, 1.62)
    tn = np.arange(n) / SR  # concussion: the world muffles, a ring takes over
    muff = np.where(tn < 1.62, 9000.0, 9000.0 * np.exp(-(tn - 1.62) / 0.12) + 350.0)
    y = dsp.tv_filter(y, muff, 0.7, "lp", block=128)
    ring = (np.sin(TAU * 4200 * tn) + 0.3 * np.sin(TAU * 6300 * tn + 1)) * dsp.env_pts(n, [(0, 0), (1.66, 0), (1.9, 1.0), (dur, 0.0)]) ** 1.2
    y = y + ring * 0.05
    y = _small_rev(y, rng, 1.0, 0.25)
    return dsp.fade(dsp.pad_to(dsp.highpass(y, 25, 2), n), 0.0, 0.3)


# --------------------------------------------------------------------------
# block.leaves_step_1..6 (forest_floor / fallen_leaves sound type)
# --------------------------------------------------------------------------
def leaves_step(rng, i=1):
    kw = [dict(), dict(crunch_amt=1.2), dict(toe=0.7, needles=1.4), dict(damp=0.25), dict(weight=1.2, twig=0.5),
          dict(crunch_amt=0.85, bright=0.9)][i - 1]
    y = ins.leaf_step(rng, **kw)
    return _fin(y, 50)


# --------------------------------------------------------------------------
# items
# --------------------------------------------------------------------------
def _click(rng, f=3200.0, vel=1.0, body=ins.PLASTIC, size=1.0):
    m = ns(0.004)
    c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0004 * SR))
    c = dsp.reson(c, f, 2.0, 2.0) + dsp.highpass(c, 2000, 2)
    b = ins.modal_hit(rng, body, size=size, hardness=0.95, vel=0.5, dur=0.08, noise=0.4)
    return dsp.mix(c, b) * vel


def flashlight_on(rng, i=1):
    y = np.zeros(ns(0.35))
    dsp.place(y, _click(rng, 3000, 1.0, ins.METAL, 1.6), 0.01)
    dsp.place(y, _click(rng, 4200, 0.45, ins.METAL, 1.8), 0.075)
    return _fin(_small_rev(y, rng, 0.25, 0.08), 200)


def flashlight_off(rng, i=1):
    y = np.zeros(ns(0.35))
    dsp.place(y, _click(rng, 3600, 0.8, ins.METAL, 1.7), 0.01)
    dsp.place(y, _click(rng, 2700, 0.5, ins.METAL, 1.5), 0.06)
    return _fin(_small_rev(y, rng, 0.25, 0.08), 200)


def flashlight_flicker(rng, i=1):
    dur = {1: 0.8, 2: 1.1, 3: 0.6}[i]
    n = ns(dur)
    t = np.arange(n) / SR
    gate = np.zeros(n)
    tt = 0.0
    while tt < dur:  # bad contact: irregular on/off stutter
        on = rng.uniform(0.01, 0.09)
        gate[ns(tt):ns(tt + on)] = rng.uniform(0.4, 1.0)
        tt += on + rng.uniform(0.01, 0.12)
    gate = dsp.lowpass0(gate, 400)
    whine = np.sin(TAU * rng.uniform(5200, 7400) * t) * 0.25 + np.sin(TAU * 120 * t) * 0.2 + np.sin(TAU * 240 * t) * 0.12
    buzz = dsp.bandpass(rng.standard_normal(n), 2000, 9000, 2) * (0.5 + 0.5 * np.cos(TAU * 120 * t)) ** 6
    y = (whine + buzz * 0.6) * gate
    edges = np.nonzero(np.abs(np.diff(gate)) > 0.05)[0]
    for e in edges[::3]:
        m = ns(0.003)
        dsp.place(y, rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0003 * SR)) * 0.6, int(e))
    return _fin(_small_rev(y, rng, 0.2, 0.05), 80)


def battery(rng, i=1):
    y = np.zeros(ns(2.4))

    def thread(t0, d, up):
        m = ns(d)
        rot = np.cumsum(np.full(m, 3.2 / d)) / SR
        rate = 140 + 60 * np.sin(TAU * rot)
        fp = ins.friction_pulses(d, rng, rate, dsp.env_pts(m, [(0, 0), (0.03, 1.0), (d - 0.03, 1.0), (d, 0)]), jitter=0.3)
        fp = dsp.resbank(fp, [(f, 12, a) for f, a in ((2400, 1.0), (3700, 0.7), (5600, 0.4))])
        dsp.place(y, _nrm(fp) * 0.35, t0)
    thread(0.02, 0.45, True)
    dsp.place(y, _click(rng, 2500, 0.6, ins.METAL, 1.4), 0.5)
    for t0, sz in ((0.75, 1.0), (0.98, 1.05)):  # cells sliding in, clacking
        m = ns(0.12)
        sl = dsp.bandpass(rng.standard_normal(m), 1500, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.08, 1), (0.12, 0)])
        dsp.place(y, sl * 0.15, t0)
        dsp.place(y, ins.modal_hit(rng, ins.METAL, size=sz * 0.8, hardness=0.9, vel=0.8, dur=0.25), t0 + 0.12)
    m = ns(0.15)
    spring = np.sin(TAU * 1900 * np.arange(m) / SR) * dsp.env_ad(m, 0.001, 0.03) * 0.2
    dsp.place(y, spring, 1.12)
    thread(1.35, 0.5, False)
    dsp.place(y, _click(rng, 2800, 0.8, ins.METAL, 1.4), 1.88)
    return _fin(_small_rev(y, rng, 0.3, 0.1), 150)


def bear_bell(rng, i=1):
    if i == 1:  # vigorous shake
        strikes = [(t, rng.uniform(0.6, 1.0)) for t in np.cumsum(rng.uniform(0.07, 0.12, 14))]
        dur = 3.2
    elif i == 2:  # three distinct rings
        strikes = [(0.02, 1.0), (0.5, 0.9), (0.98, 1.0)]
        dur = 3.4
    else:  # shake and let it ring out
        strikes = [(t, rng.uniform(0.5, 1.0)) for t in np.cumsum(rng.uniform(0.08, 0.14, 7))]
        dur = 3.6
    y = ins.bear_bell(rng, dur, strikes, base=1650.0)
    y = dsp.highpass(y, 300, 2)
    y = dsp.reverb(y, dsp.make_ir(1.3, rng, predelay=0.01, hf_ratio=0.5), wet=0.25)
    return dsp.fade(dsp.pad_to(y, ns(dur)), 0.0, 0.4)


def rope(rng, i=1):
    y = np.zeros(ns(2.6))
    t = 0.02
    for k in range(5):  # coils slithering off and slapping down
        d = rng.uniform(0.18, 0.3)
        m = ns(d)
        sl = dsp.bandpass(rng.standard_normal(m), 300, 4000, 2) * dsp.env_pts(m, [(0, 0), (d * 0.3, 1), (d, 0)])
        sl *= 1 + 0.6 * dsp.smooth(m, rng, 40.0)
        dsp.place(y, sl * 0.2, t)
        m2 = ns(0.12)
        slap = dsp.lowpass(rng.standard_normal(m2), 1500, 2) * dsp.env_ad(m2, 0.002, 0.02)
        dsp.place(y, slap * 0.4, t + d * 0.8)
        t += d + rng.uniform(0.02, 0.08)
    d = 0.6  # the knot pulled tight around iron: fibre creak, then the ring clanks
    cr = ins.creak(d, rng, [(0, 40), (d * 0.7, 130), (d, 60)], [(0, 0), (0.1, 0.8), (d, 0)],
                   modes=[(f, 10, a) for f, a in ((520, 1.0), (980, 0.7), (1700, 0.4))], tonal=0.3)
    dsp.place(y, cr * 0.3, t + 0.1)
    dsp.place(y, ins.modal_hit(rng, ins.METAL, size=1.3, hardness=0.9, vel=0.9, dur=0.8), t + 0.1 + d)
    return _fin(_small_rev(y, rng, 0.9, 0.25), 80)


def map_(rng, i=1):
    y = np.zeros(ns(2.0))
    for k, (t0, d) in enumerate(((0.02, 0.35), (0.45, 0.3), (0.85, 0.45))):
        m = ns(d)
        env = dsp.env_pts(m, [(0, 0), (d * 0.2, 1.0), (d, 0)])
        cr = ins.crackle(d, rng, 1800, env, hp=1000, decay=0.0005)
        sl = dsp.bandpass(rng.standard_normal(m), 500, 6000, 2) * env * 0.2
        wh = dsp.lowpass(rng.standard_normal(m), 400, 2) * env * 0.25
        dsp.place(y, _nrm(cr) * 0.6 + sl + wh, t0)
    return _fin(_small_rev(y, rng, 0.4, 0.12), 120)


def flare_ignite(rng, i=1):
    dur = 3.0
    n = ns(dur)
    y = np.zeros(n)
    m = ns(0.22)  # strike cap scraped across the igniter
    sc = dsp.bandpass(rng.standard_normal(m), 800, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.03, 1.0), (0.2, 0.8), (0.22, 0)])
    sc *= 1 + 0.9 * np.abs(dsp.smooth(m, rng, 80.0))
    dsp.place(y, sc * 0.5, 0.02)
    m = ns(0.12)
    pop = dsp.lowpass(rng.standard_normal(m), 2500, 2) * dsp.env_ad(m, 0.002, 0.02)
    dsp.place(y, pop * 1.0, 0.25)
    m = n - ns(0.26)
    t = np.arange(m) / SR
    env = dsp.env_pts(m, [(0, 0), (0.05, 1.3), (0.35, 0.9), (m / SR, 0.8)])
    roar = dsp.colored(m, rng, -1.0, lo=250, hi=9000) * env * (1 + 0.35 * dsp.smooth(m, rng, 18.0))
    roar += ins.crackle(m / SR, rng, 260, env, hp=1500) * 0.5
    roar += dsp.lowpass(rng.standard_normal(m), 300, 2) * env * 0.3
    dsp.place(y, roar * 0.45, 0.26)
    return dsp.fade(_small_rev(y, rng, 0.5, 0.12)[:n], 0.0, 0.25)


def flare_burn(rng, i=1):
    L = 4.0
    n = ns(L)
    hiss = dsp.colored(n, rng, -1.0, lo=300, hi=9000)
    sput = np.clip(1 + 0.45 * dsp.smooth(n, rng, 14.0) + 0.25 * dsp.smooth(n, rng, 40.0), 0.25, None)
    y = hiss * sput
    y += dsp.colored(n, rng, -6.0, lo=40, hi=350) * 0.6 * sput
    pops = np.zeros(n)
    for _ in range(int(L * 9)):
        m = ns(0.01)
        p = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0012 * SR))
        dsp.place(pops, dsp.highpass(p, 1200, 2) * rng.pareto(2.5) * 0.6, rng.uniform(0, L), circular=True)
    y = y / (np.std(y) + 1e-9) * 0.3 + pops
    ir = dsp.make_ir(0.5, rng, predelay=0.003)
    y = dsp.circ_reverb(y, ir, wet=0.12, dry=0.95)
    return dsp.dc_block(y, circular=True)


def omamori(rng, i=1):
    """Protective burst (stereo): a burst of shimmering bells and a bright
    whoosh, as the charm splits."""
    dur = 3.4
    n = ns(dur)
    out = np.zeros((2, n))
    m = ns(0.5)
    tt = np.arange(m) / SR
    wh = dsp.tv_filter(rng.standard_normal(m), 400 * 2 ** (np.clip(tt / 0.45, 0, 1) * 4), 1.0, "bp") * dsp.env_pts(m, [(0, 0), (0.42, 1), (0.5, 0)]) ** 2
    dsp.place(out, np.vstack([wh, np.roll(wh, 300)]) * 0.35 / (np.max(np.abs(wh)) + 1e-9), 0.0)
    m = ns(0.2)
    rip = ins.crackle(0.2, rng, 3000, dsp.env_ad(m, 0.002, 0.04), hp=1500)
    dsp.place(out, np.vstack([rip, rip]) * 0.4 / (np.max(np.abs(rip)) + 1e-9), 0.45)
    for p, bases in ((-0.7, (3300, 4200, 5300)), (0.7, (3550, 4500, 5800))):
        j = ins.suzu_jingle(rng, 2.6, [(0.0, 1.0), (0.1, 0.7), (0.22, 0.5)], bases=bases, n_hits=(5, 9))
        dsp.place(out, dsp.pan(j, p) * 0.35, 0.46)
    sh = np.zeros(ns(2.6))
    t = np.arange(len(sh)) / SR
    for f in (2093.0, 2637.0, 3136.0, 4186.0):
        sh += np.sin(TAU * f * (1 + 0.002 * np.sin(TAU * 5.5 * t)) * t + rng.random() * 6) * np.exp(-t / 0.9)
    dsp.place(out, np.vstack([sh, np.roll(sh, 400)]) * 0.06, 0.46)
    m = ns(1.2)
    sub = np.sin(TAU * np.cumsum(55 + 30 * np.exp(-np.arange(m) / SR / 0.1)) / SR) * dsp.env_ad(m, 0.004, 0.25)
    dsp.place(out, np.vstack([sub, sub]) * 0.35, 0.45)
    out = dsp.reverb(out, dsp.make_ir(2.0, rng, stereo=True, predelay=0.015), wet=0.35)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 35, 2), n), 0.0, 0.5)


def key(rng, i=1):
    y = np.zeros(ns(1.2))
    t = 0.02
    for k in range(rng.integers(9, 14)):
        dsp.place(y, ins.modal_hit(rng, ins.KEYS, size=rng.uniform(0.8, 1.25), hardness=1.0, vel=rng.uniform(0.25, 1.0) * (1 - k / 16), dur=0.4, noise=0.3), t)
        t += rng.exponential(0.035) + 0.01
    return _fin(_small_rev(y, rng, 0.4, 0.15), 400)


def unlock(rng, i=1):
    y = np.zeros(ns(3.0))
    m = ns(0.25)
    ins_ = dsp.bandpass(rng.standard_normal(m), 2500, 8000, 2) * dsp.env_pts(m, [(0, 0), (0.05, 1), (0.25, 0.4)]) * (1 + 0.7 * np.abs(dsp.smooth(m, rng, 50)))
    dsp.place(y, ins_ * 0.2, 0.02)
    for k, t0 in enumerate((0.33, 0.42)):
        dsp.place(y, ins.modal_hit(rng, ins.METAL, size=0.55, hardness=1.0, vel=0.45, dur=0.15, noise=0.4), t0)
    dsp.place(y, ins.modal_hit(rng, ins.METAL, size=1.1, hardness=0.95, vel=1.0, dur=0.5, noise=0.5), 0.55)  # shackle springs
    t = 0.85  # the chain slides through the bars and falls
    for k in range(18):
        v = rng.uniform(0.2, 0.7) * (0.5 + 0.5 * np.sin(np.pi * k / 18))
        dsp.place(y, ins.modal_hit(rng, ins.CHAIN, size=rng.uniform(0.85, 1.15), hardness=1.0, vel=v, dur=0.2, noise=0.3), t)
        t += rng.uniform(0.02, 0.06)
    for k in range(10):  # the pile hits the asphalt
        dsp.place(y, ins.modal_hit(rng, ins.CHAIN, size=rng.uniform(0.9, 1.3), hardness=1.0, vel=rng.uniform(0.4, 1.0), dur=0.25, noise=0.5), t + rng.uniform(0, 0.15))
    m = ns(0.3)
    thud = dsp.lowpass(rng.standard_normal(m), 500, 2) * dsp.env_ad(m, 0.002, 0.04)
    dsp.place(y, thud * 0.8, t)
    return _fin(dsp.reverb(y, dsp.make_ir(1.4, rng, predelay=0.01), wet=0.3), 80)


def offering(rng, i=1):
    y = np.zeros(ns(4.0))
    g = ins.modal_hit(rng, ins.BOTTLE, size=1.0, hardness=0.8, vel=0.8, dur=0.5, noise=0.2)
    w = ins.modal_hit(rng, ins.WOOD_DOOR, size=0.9, hardness=0.7, vel=0.7, dur=0.3)
    dsp.place(y, g, 0.02)
    dsp.place(y, w, 0.02)
    dsp.place(y, ins.modal_hit(rng, ins.BOTTLE, size=1.0, hardness=0.8, vel=0.25, dur=0.4), 0.11)
    dsp.place(y, ins.rin(rng, rng.uniform(1100, 1250), 3.4, 0.5), 0.6)
    return _fin(dsp.reverb(y, dsp.make_ir(1.5, rng, predelay=0.01), wet=0.3), 150)


def paper(rng, i=1):
    dur = 0.9 if i == 1 else 0.7
    n = ns(dur)
    env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (dur * 0.5, 0.6), (dur, 0)]) if i == 1 else \
        dsp.env_pts(n, [(0, 0), (0.04, 0.7), (0.2, 1.0), (0.45, 0.3), (dur, 0)])
    cr = ins.crackle(dur, rng, 1500 if i == 1 else 900, env, hp=1200, decay=0.0004)
    sl = dsp.bandpass(rng.standard_normal(n), 800, 7000, 2) * env * (0.5 + 0.5 * np.abs(dsp.smooth(n, rng, 15)))
    y = _nrm(cr) * 0.6 + sl * 0.15
    return _fin(_small_rev(y, rng, 0.4, 0.12), 200)


def bag(rng, i=1):
    y = np.zeros(ns(2.6))
    d = 0.7  # zipper: teeth pulse train, accelerating
    m = ns(d)
    rate = np.interp(np.arange(m) / SR, [0, d * 0.3, d], [180, 600, 450])
    zp = ins.friction_pulses(d, rng, rate, dsp.env_pts(m, [(0, 0), (0.04, 1), (d - 0.05, 0.9), (d, 0)]), jitter=0.15)
    zp = dsp.resbank(zp, [(f, 8, a) for f, a in ((1800, 1.0), (3200, 0.8), (5200, 0.5))])
    dsp.place(y, _nrm(zp) * 0.5, 0.02)
    t = 0.85
    for k in range(6):  # rummaging: nylon rustle, things knocking together
        dd = rng.uniform(0.12, 0.25)
        mm = ns(dd)
        ru = dsp.bandpass(rng.standard_normal(mm), 600, 6000, 2) * dsp.env_pts(mm, [(0, 0), (dd / 3, 1), (dd, 0)])
        ru += ins.crackle(dd, rng, 500, dsp.env_pts(mm, [(0, 0), (dd / 3, 1), (dd, 0)]), hp=2000) * 0.5
        dsp.place(y, ru * 0.25, t)
        if rng.random() < 0.6:
            body = [ins.PLASTIC, ins.METAL, ins.BOTTLE][rng.integers(3)]
            dsp.place(y, ins.modal_hit(rng, body, size=rng.uniform(1.0, 1.6), hardness=0.8, vel=0.35, dur=0.2), t + dd * 0.5)
        t += dd + rng.uniform(0.02, 0.1)
    return _fin(_small_rev(y, rng, 0.35, 0.12), 100)
