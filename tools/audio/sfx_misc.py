"""doll.*, shadow.* and player.* sounds."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, ns


# --------------------------------------------------------------------------
# doll.move_1..5 - "カタ"
# --------------------------------------------------------------------------
def _click(rng, vel=1.0, size=1.0):
    c = ins.wood_hit(rng, ins.CERAMIC, size=size * rng.uniform(0.9, 1.15), hardness=0.97, vel=1.0, dur=0.14, noise=0.5)
    w = ins.wood_hit(rng, ins.WOOD_DOOR, size=0.42 * size, hardness=0.9, vel=0.7, dur=0.12, noise=0.3)
    return dsp.mix(c * 0.7, w) * vel


def doll_move(rng, i=1):
    y = np.zeros(ns(0.6))
    if i == 5:
        t = 0.01
        for k in range(4):
            dsp.place(y, _click(rng, 0.9 - 0.15 * k), t)
            t += rng.uniform(0.045, 0.075)
    else:
        dsp.place(y, _click(rng, 1.0, size=1.0 + 0.08 * (i - 2)), 0.01)
        dsp.place(y, _click(rng, rng.uniform(0.45, 0.65), size=1.1), 0.01 + rng.uniform(0.04, 0.09))
    ir = dsp.make_ir(0.45, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.2)
    return dsp.trim(dsp.highpass(y, 200, 2), -60)


# --------------------------------------------------------------------------
# doll.head_turn_1..2 - slow wooden creak of a small joint
# --------------------------------------------------------------------------
def doll_head_turn(rng, i=1):
    dur = 1.5 if i == 1 else 1.1
    cr = ins.creak(dur, rng, [(0, 14), (dur * 0.4, 32), (dur * 0.8, 20), (dur, 10)],
                   [(0, 0), (0.08, 0.8), (dur * 0.7, 1.0), (dur, 0)], pitch=1.9 if i == 1 else 2.3, jitter=0.3, q_scale=1.4)
    n = len(cr)
    grind = dsp.bandpass(np.random.default_rng(int(rng.integers(1 << 30))).standard_normal(n), 2500, 7000, 2)
    grind *= dsp.env_pts(n, [(0, 0), (0.1, 0.5), (dur * 0.8, 0.4), (dur, 0)]) * (0.5 + 0.5 * np.abs(dsp.smooth(n, rng, 20.0)))
    y = cr + grind * 0.08
    y = dsp.mix(y, dsp.place(np.zeros(ns(dur + 0.3)), _click(rng, 0.5, 1.2), dur - 0.02))
    ir = dsp.make_ir(0.45, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.2)
    return dsp.trim(dsp.highpass(y, 150, 2), -60)


# --------------------------------------------------------------------------
# doll.attack - shrill stinger + clatter
# --------------------------------------------------------------------------
def doll_attack(rng, i=1):
    dur = 1.8
    n = ns(dur)
    st = ins.strings([dsp.hz(x) for x in ["F6", "F#6", "G6", "C7", "C#7"]], dur, rng,
                     env=dsp.env_pts(n, [(0, 0), (0.06, 1.0), (0.5, 0.7), (1.6, 0.0)]), voices=2, detune=30, vib_depth=40,
                     vib_rate=9.0, trem=0.6, trem_rate=18, bright=0.9, pont=0.8, stereo=False,
                     gliss=dsp.env_pts(n, [(0, -300), (0.08, 0), (1.8, 120)]))
    # child-like shriek, very high and thin
    f0 = 1500 * 2 ** (0.03 * dsp.smooth(n, rng, 14.0)) * dsp.env_pts(n, [(0, 0.8), (0.08, 1.0), (0.9, 1.07), (1.5, 0.8)])
    amp = dsp.env_pts(n, [(0, 0), (0.04, 1.0), (0.8, 0.8), (1.3, 0.0)])
    shriek = V.voice(f0, amp, [(0, V.fs("i", scale=1.25))], rng, tilt=-2, fmax=15000, jitter=0.02, shimmer=0.2, breath=0.4,
                     loud_comp=0.4, gain_fn=V.parallel_env([(lambda t: 1600, 500, 0), (3400, 700, -4), (5200, 900, -9), (7800, 1500, -15)], -32))
    y = st / (np.max(np.abs(st)) + 1e-9) * 0.8 + shriek / (np.max(np.abs(shriek)) + 1e-9) * 0.6
    y = dsp.sat(y * 1.5, 1.0)
    t = 0.0
    for k in range(14):  # clatter of wooden limbs
        dsp.place(y, _click(rng, rng.uniform(0.4, 1.0) * (1 - k / 18), size=rng.uniform(0.8, 1.4)), t)
        t += rng.uniform(0.015, 0.06)
    ir = dsp.make_ir(0.9, rng, predelay=0.006)
    y = dsp.reverb(y, ir, wet=0.3)
    return dsp.trim(dsp.highpass(y, 120, 2), -60)


# --------------------------------------------------------------------------
# shadow.whoosh_1..3 - dark breathy whoosh
# --------------------------------------------------------------------------
def shadow_whoosh(rng, i=1):
    dur = {1: 1.3, 2: 1.0, 3: 1.7}[i]
    n = ns(dur)
    pk = {1: 0.55, 2: 0.7, 3: 0.95}[i]
    env = dsp.env_pts(n, [(0, 0), (pk * dur, 1.0), (dur, 0)]) ** 1.6
    fc = dsp.env_pts(n, [(0, 180), (pk * dur, 900 if i != 3 else 650), (dur, 160)])
    nz = dsp.colored(n, rng, -2.0)
    a = dsp.tv_filter(nz, fc, 2.5, "bp")
    b = dsp.tv_filter(nz, fc * 2.4, 4.0, "bp")
    keys = [(0, V.fs("u", V.VM, 0.8)), (pk * dur, V.fs("o", V.VM, 0.85)), (dur, V.fs("x", V.VM, 0.8))]
    br = V.vt_noise(n, keys, env, rng, tilt_db=-2.0, bw=1.4)
    rum = dsp.lowpass(rng.standard_normal(n), 120, 2)
    y = (a * 1.0 + b * 0.35) / (np.std(a) + 1e-9) * env * 0.5 + br * 0.45 + rum / (np.std(rum) + 1e-9) * env * 0.35
    if i == 3:  # a breathy voice smeared inside
        f0 = np.full(n, 82.0) * dsp.cents(dsp.env_pts(n, [(0, 0), (dur, -200)]))
        vv = V.voice(f0, env * 0.5, keys, rng, tilt=-12, fmax=3000, breath=1.5, jitter=0.03, loud_comp=0.5)
        y += vv / (np.std(vv) + 1e-9) * 0.15
    ir = dsp.make_ir(1.4, rng, predelay=0.01, lp_hz=3000)
    y = dsp.reverb(y, ir, wet=0.35)
    return dsp.trim(dsp.highpass(y, 40, 2), -60)


# --------------------------------------------------------------------------
# shadow.drone - 6 s seamless loop (mono)
# --------------------------------------------------------------------------
def shadow_drone(rng, i=1):
    L = 6.0
    n = ns(L)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for f, a in [(49.0, 1.0), (49.0 * 1.5, 0.4), (98.0, 0.5), (103.83, 0.25), (146.83, 0.2), (196.0 * dsp.cents(-20), 0.08)]:
        fq = dsp.loop_freq(f, L)
        bt = dsp.loop_freq(f + rng.uniform(0.3, 0.9), L)
        y += a * (np.sin(dsp.TAU * fq * t + rng.random() * 6) + 0.7 * np.sin(dsp.TAU * bt * t + rng.random() * 6))
    puls = 0.65 + 0.35 * (0.5 - 0.5 * np.cos(dsp.TAU * 3 * t / L))
    y *= puls
    nz = dsp.colored(n, rng, -6.0, lo=30, hi=300)
    y += nz * 0.5 * (0.6 + 0.4 * np.roll(puls, n // 6))
    hi = dsp.colored(n, rng, -1.0, lo=900, hi=2400)
    y += hi * 0.03 * (0.5 - 0.5 * np.cos(dsp.TAU * 2 * t / L))
    ir = dsp.make_ir(1.2, rng, predelay=0.01, lp_hz=2000)
    y = dsp.circ_reverb(y, ir, wet=0.3, dry=0.9)
    return dsp.dc_block(y, circular=True)


# --------------------------------------------------------------------------
# player.heartbeat - one deep lub-dub
# --------------------------------------------------------------------------
def heartbeat(rng, i=1):
    y = ins.heartbeat(rng)
    return dsp.fade(dsp.highpass(y, 25, 2), 0.001, 0.05)


# --------------------------------------------------------------------------
# player breathing (male)
# --------------------------------------------------------------------------
def _breath(rng, kind, d, tremor=0.0, trem_rate=8.0, voiced=0.0, f0=125.0, vowel_in="x", vowel_out="a", hiss=0.0):
    """One breath phase. kind 'in' (higher, hissier) or 'out' (lower, voiced)."""
    m = ns(d)
    x = np.linspace(0, 1, m)
    if kind == "in":
        env = np.sin(np.pi * x ** 0.8) ** 1.1
        keys = [(0, V.fs(vowel_in, V.VM, f1=0.85, bw=1.5)), (d, V.fs("i" if hiss else vowel_in, V.VM, bw=1.5))]
        tilt = 2.5
    else:
        env = np.minimum(1, x * d / 0.05) * (1 - x) ** 1.2
        keys = [(0, V.fs(vowel_out, V.VM, f1=0.9)), (d, V.fs("x", V.VM))]
        tilt = -1.0
    if tremor > 0:
        rate = trem_rate * (1 + 0.15 * dsp.smooth(m, rng, 1.0))
        tr = 0.5 + 0.5 * np.sin(dsp.TAU * np.cumsum(rate) / SR + rng.random() * 6)
        env = env * ((1 - tremor) + tremor * tr ** 1.5)
    s = V.vt_noise(m, keys, env, rng, tilt_db=tilt, bw=1.6)
    if hiss > 0:  # through the teeth
        s += dsp.bandpass(rng.standard_normal(m), 3500, 9000, 2) * env * hiss
    if voiced > 0:
        ff = f0 * dsp.cents(dsp.env_pts(m, [(0, 60), (d, -120)]) + 25 * dsp.smooth(m, rng, 9.0))
        vv = V.voice(ff, env, keys, rng, tilt=-12, fmax=4000, breath=0.8, jitter=0.02, shimmer=0.15)
        s = s + vv / (np.std(vv) + 1e-9) * np.std(s) * voiced
    return dsp.fade(s, 0.005, 0.03)


def _seq(rng, dur, parts):
    y = np.zeros(ns(dur))
    for t0, args, kw in parts:
        dsp.place(y, _breath(rng, *args, **kw), t0)
    return y


def _breath_post(y, rng, room=0.12):
    y = dsp.lowpass(y, 7500, 2)
    ir = dsp.make_ir(0.4, rng, predelay=0.002, lp_hz=6000)
    y = dsp.reverb(y, ir, wet=room)
    return dsp.trim(dsp.highpass(y, 90, 2), -60)


def breath_fear(rng, i=1):
    if i == 1:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.85), dict(tremor=0.6, trem_rate=9.5)),
                            (1.0, ("out", 1.05), dict(tremor=0.55, trem_rate=7.0, voiced=0.35, f0=135)),
                            (2.25, ("in", 0.45), dict(tremor=0.5, trem_rate=11.0))])
    elif i == 2:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.32), dict(tremor=0.4)), (0.4, ("out", 0.42), dict(tremor=0.4, voiced=0.25)),
                            (0.9, ("in", 0.3), dict(tremor=0.5)), (1.24, ("out", 0.45), dict(tremor=0.5, voiced=0.3, f0=140)),
                            (1.8, ("in", 0.55), dict(tremor=0.6, trem_rate=10.0))])
    else:
        y = _seq(rng, 3.2, [(0.05, ("in", 0.95), dict(tremor=0.35, hiss=0.25)),
                            (1.15, ("out", 1.6), dict(tremor=0.75, trem_rate=6.0, voiced=0.2, f0=120))])
    return _breath_post(y, rng)


def gasp(rng, i=1):
    if i == 1:
        dur = 1.0
        y = np.zeros(ns(dur))
        g = _breath(rng, "in", 0.36, voiced=0.6, f0=185, vowel_in="a")
        m = len(g)
        g *= np.exp(-np.linspace(0, 1, m) * 0.6)
        g[:ns(0.012)] *= np.linspace(0, 1, ns(0.012))
        dsp.place(y, g * 1.6, 0.02)
    else:
        dur = 1.2
        y = np.zeros(ns(dur))
        dsp.place(y, _breath(rng, "in", 0.12, voiced=0.3, f0=170, vowel_in="a") * 0.9, 0.02)
        dsp.place(y, _breath(rng, "in", 0.42, voiced=0.7, f0=200, vowel_in="a") * 1.6, 0.17)
    # glottal closure click (breath held)
    cl = dsp.bandpass(rng.standard_normal(ns(0.006)), 600, 2500) * 0.05
    dsp.place(y, cl, 0.4 if i == 1 else 0.6)
    return _breath_post(y, rng, room=0.15)


def breath_exhausted(rng, i=1):
    parts = []
    t = 0.05
    cyc = 0.52 if i == 1 else 0.68
    k = 7 if i == 1 else 6
    for c in range(k):
        di = cyc * rng.uniform(0.38, 0.45)
        do = cyc * rng.uniform(0.5, 0.58)
        parts.append((t, ("in", di), dict(tremor=0.15)))
        parts.append((t + di + 0.01, ("out", do), dict(voiced=0.55 if i == 1 else 0.7, f0=rng.uniform(112, 128))))
        t += di + do + rng.uniform(0.0, 0.05)
    y = _seq(rng, t + 0.3, parts)
    return _breath_post(y, rng)
