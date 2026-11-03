"""player.* - your own body (mono positional, except breath_hold and the swallow which are inside your head).

The player is a young woman in socks in her own apartment at night: soft steps on tatami, on old floorboards that
give and creak, on the bathroom's tiles, wet steps after the bath.  Her breathing when she is afraid, the breath she
holds, the gasp when she cannot any more, breathing through her nose with a mouthful of salt water."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import voice as V
from dsp import SR, TAU, ns

ME = dict(table=V.VF, scale=1.0)


def _breath_post(y, rng, room=0.05):
    y = dsp.lowpass(y, 9000, 2)
    y = dsp.close_mic(y, prox_db=3.0, pres_db=2.0, air_db=1.0)
    y = K.rev(y, rng, 0.3, room, lp=8000)
    return K.fin(y, 100, -62)


def _seq(rng, dur, parts):
    y = np.zeros(ns(dur))
    for t0, args, kw in parts:
        k = dict(ME)
        k.update(kw)
        K.place(y, V.breath(rng, *args, **k), t0)
    return y


# --------------------------------------------------------------------------
def heartbeat(rng, i=1):
    """One deep, muffled lub-dub (<= 0.5 s so it still works at 180 bpm).  The thump is saturated so its 2nd-4th
    harmonics carry it on small speakers."""
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
        K.place(y, (th + cl) * g, t0)
    y = dsp.lowpass(y, 320, 2)
    return dsp.fade(dsp.highpass(y, 22, 2), 0.001, 0.04)


def breath_fear(rng, i=1):
    """Trembling breaths in the dark: 1 a long shaky in and out, 2 quick shallow breaths, 3 a held-in breath let out
    unsteadily."""
    if i == 1:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.85), dict(tremor=0.6, trem_rate=9.5)),
                            (1.0, ("out", 1.05), dict(tremor=0.55, trem_rate=7.0, voiced=0.25, f0=235)),
                            (2.25, ("in", 0.45), dict(tremor=0.5, trem_rate=11.0))])
    elif i == 2:
        y = _seq(rng, 3.0, [(0.05, ("in", 0.32), dict(tremor=0.4)), (0.4, ("out", 0.42), dict(tremor=0.4, voiced=0.2, f0=240)),
                            (0.9, ("in", 0.3), dict(tremor=0.5)), (1.24, ("out", 0.45), dict(tremor=0.5, voiced=0.25, f0=245)),
                            (1.8, ("in", 0.55), dict(tremor=0.6, trem_rate=10.0))])
    else:
        y = _seq(rng, 3.2, [(0.05, ("in", 0.95), dict(tremor=0.35, hiss=0.25)),
                            (1.15, ("out", 1.6), dict(tremor=0.75, trem_rate=6.0, voiced=0.2, f0=225))])
    return _breath_post(y, rng)


def breath_hold(rng, i=1):
    """Holding your breath: a quick deep inhale through the nose, the throat closing, strained silence, the pulse in
    your ears.  Stereo, inside your head."""
    y = np.zeros(ns(3.0))
    b = V.breath(rng, "in", 0.5, table=V.VF, scale=1.0, tremor=0.25, trem_rate=10.0, vowel_in="m")
    K.place(y, b, 0.03)
    m = ns(0.014)
    K.place(y, dsp.bandpass(rng.standard_normal(m), 500, 2500, 2) * np.exp(-np.arange(m) / (0.002 * SR)) * 3.0 * np.std(b), 0.56)
    m = ns(1.8)  # strain: the faint creak of a closed throat
    st = V.creak(m, 22 + 6 * dsp.smooth(m, rng, 1.0, periodic=False), dsp.env_pts(m, [(0, 0), (0.6, 0.3), (1.8, 0.55)]), rng,
                 vowel="x", table=V.VF, jitter=0.5)
    K.place(y, st * np.std(b) * 0.22, 0.9)
    for t0 in (0.95, 1.7, 2.42):  # the pulse in the ears
        K.place(y, heartbeat(rng) * np.max(np.abs(b)) * 0.35, t0)
    y = _breath_post(y, rng, 0.02)
    return np.vstack([y, np.roll(y, ns(0.0003))])


def gasp(rng, i=1):
    """Letting the held breath go: a ragged gasp for air."""
    if i == 1:
        y = np.zeros(ns(1.7))
        g = V.breath(rng, "in", 0.36, table=V.VF, voiced=0.55, f0=290, vowel_in="a")
        g *= np.exp(-np.linspace(0, 1, len(g)) * 0.6)
        g[:ns(0.012)] *= np.linspace(0, 1, ns(0.012))
        K.place(y, g * 1.6, 0.02)
        K.place(y, V.breath(rng, "out", 0.75, table=V.VF, voiced=0.25, f0=230, vowel_out="a"), 0.45)
    else:
        y = np.zeros(ns(1.9))
        K.place(y, V.breath(rng, "in", 0.12, table=V.VF, voiced=0.3, f0=270, vowel_in="a") * 0.9, 0.02)
        K.place(y, V.breath(rng, "in", 0.42, table=V.VF, voiced=0.65, f0=300, vowel_in="a") * 1.6, 0.17)
        K.place(y, V.breath(rng, "out", 0.85, table=V.VF, voiced=0.3, f0=225, vowel_out="a"), 0.66)
    return _breath_post(y, rng, 0.07)


def breath_mouth(rng, i=1):
    """Breathing through the nose with the cheeks full of salt water: nasal breaths, the water shifting and gurgling
    in the mouth, the throat refusing to swallow."""
    dur = 3.4
    y = np.zeros(ns(dur))
    parts = [(0.05, "in", 0.7), (0.95, "out", 0.9), (2.1, "in", 0.6)] if i == 1 else [(0.05, "out", 0.8), (1.0, "in", 0.5), (1.7, "out", 1.1)]
    for t0, kind, d in parts:
        b = V.breath(rng, kind, d, table=V.VF, scale=1.0, tremor=0.35, vowel_in="m", vowel_out="m")
        b = dsp.bandpass(b, 250, 4500, 2)  # through the nose
        K.place(y, b, t0)
    ref = np.std(y[y != 0]) if np.any(y != 0) else 1.0
    for t0 in ([0.8, 1.9, 2.95] if i == 1 else [0.85, 1.55, 2.9]):  # the water moving in the mouth
        m = ns(0.25)
        g = K.bubbles(0.25, rng, 160, (0.4, 1.4), env=K.env_shape(m, [(0, 0), (0.05, 1.0), (0.25, 0)]))
        g = dsp.lowpass(g, 2600, 2)
        K.place(y, K.nrm(g) * ref * 2.2, t0)
    K.place(y, V.mouth_clicks(dur, rng, rate=1.2) * ref * 1.5, 0.0)
    if i == 2:  # the throat almost swallowing - stopped
        m = ns(0.05)
        K.place(y, dsp.bandpass(rng.standard_normal(m), 300, 1800, 2) * dsp.env_ad(m, 0.003, 0.012) * ref * 3.0, 2.55)
    return _breath_post(dsp.lowpass(y, 6000, 2), rng, 0.03)


def swallow(rng, i=1):
    """A gulp: the tongue pushing back, the throat's click, water going down.  Stereo, inside your head."""
    y = np.zeros(ns(0.9))
    m = ns(0.18)
    g = K.bubbles(0.18, rng, 300, (1.0, 4.0), env=K.env_shape(m, [(0, 0), (0.03, 1.0), (0.18, 0)]))
    K.place(y, dsp.lowpass(K.nrm(g), 1800, 2) * 0.6, 0.05)
    cl = ins.modal_hit(rng, [(420, 0.03, 1.0), (900, 0.02, 0.6), (1700, 0.015, 0.3)], hardness=0.5, vel=1.0, dur=0.1, noise=0.5)
    K.place(y, K.nrm(cl) * 0.8, 0.2)
    m = ns(0.12)
    th = np.sin(TAU * np.cumsum(np.linspace(180, 90, m)) / SR) * dsp.env_ad(m, 0.005, 0.03)
    K.place(y, th * 0.6, 0.22)
    y = dsp.close_mic(dsp.lowpass(y, 5000, 2), 4.0, 1.0, 0.0)
    y = K.fin(y, 60, -62)
    return np.vstack([y, np.roll(y, ns(0.0003))])


# --------------------------------------------------------------------------
# footsteps in socks
# --------------------------------------------------------------------------
def _foot(rng, f0=95.0, weight=1.0, heel=1.0, toe=0.7, pace=1.0, soft=0.6):
    """A socked foot: the heel's soft thump and the ball of the foot rolling down."""
    y = np.zeros(ns(0.4))
    th_t = 0.008
    tt_t = th_t + rng.uniform(0.07, 0.11) * pace
    for t0, g in ((th_t, heel), (tt_t, toe * rng.uniform(0.8, 1.1))):
        m = ns(0.12)
        t = np.arange(m) / SR
        b = np.sin(TAU * np.cumsum(f0 * (1 + 0.5 * np.exp(-t / 0.01))) / SR) * dsp.env_ad(m, 0.003 + 0.004 * soft, 0.02)
        b += dsp.lowpass(rng.standard_normal(m), 450 - 200 * soft, 2) * dsp.env_ad(m, 0.002, 0.012) * 0.7
        K.place(y, b * weight * g, t0)
    return y, th_t, tt_t


def step_tatami(rng, i=1):
    """Socks on tatami: a dull, springy thud of the thick straw mat, the woven rush brushing under the sole."""
    y, a, b = _foot(rng, f0=88.0, weight=1.0, toe=[0.6, 0.7, 0.55, 0.65][i - 1], soft=0.9)
    n = len(y)
    e = np.zeros(n)
    t = np.arange(n) / SR
    e += np.where(t >= a, np.exp(-(t - a) / 0.04), 0)
    e += np.where(t >= b, np.exp(-(t - b) / 0.06), 0) * 0.8
    rush = dsp.bandpass(rng.standard_normal(n), 1500, 8000, 2) * dsp.lowpass0(e, 80.0)
    rush *= 1 + 0.5 * dsp.smooth(n, rng, 60.0, periodic=False)
    y = K.nrm(y) + K.rmsn(rush) * 0.08
    x = K.stream(n / SR, rng, 300, amp_env=np.clip(e, 0, 1), pareto=3.0)
    y += K.nrm(K.grain_bank(x, [(3500, 2.0, 1.0), (6000, 2.0, 0.6)], decay=0.0004)) * 0.06
    if i == 3:  # the floor under the mat gives a little
        K.place(y, K.nrm(K.creak(rng, 0.25, pitch=0.8, rate=(40, 80))) * 0.06, 0.1)
    return K.fin(K.rev(y, rng, 0.12, 0.03), 50)


def step_floor(rng, i=1):
    """Socks on the old varnished floorboards: a soft woody thump; boards 2 and 4 creak under the weight."""
    y, a, b = _foot(rng, f0=105.0, weight=1.0, toe=[0.65, 0.6, 0.7, 0.55][i - 1], soft=0.6)
    wood = ins.modal_hit(rng, ins.WOOD_FLOOR, size=rng.uniform(0.9, 1.1), hardness=0.25, vel=1.0, dur=0.3, noise=0.15)
    K.place(y, K.nrm(wood) * 0.35, a)
    K.place(y, K.nrm(ins.modal_hit(rng, ins.WOOD_FLOOR, size=1.0, hardness=0.2, vel=0.6, dur=0.25, noise=0.1)) * 0.2, b)
    n = len(y)
    sl = dsp.bandpass(rng.standard_normal(n), 2000, 7000, 2) * K.env_shape(n, [(0, 0), (b, 0), (b + 0.03, 1.0), (b + 0.09, 0), (n / SR, 0)])
    y = K.nrm(y) + K.rmsn(sl) * 0.03
    if i in (2, 4):
        y = np.concatenate([y, np.zeros(ns(0.4))])
        K.place(y, K.nrm(K.creak(rng, 0.45, pitch=rng.uniform(0.8, 1.1), rate=(35, 85), q=1.2)) * 0.3, b + 0.02)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 50)


def step_tile(rng, i=1):
    """Feet on the bathroom / genkan tiles: a small slap, the hard floor answering brightly, skin sticking a moment."""
    y, a, b = _foot(rng, f0=125.0, weight=0.35, toe=[0.6, 0.75, 0.5, 0.7][i - 1], soft=0.3)
    tile = ins.modal_hit(rng, [(1650, 0.04, 1.0), (2900, 0.03, 0.7), (4300, 0.02, 0.45), (850, 0.05, 0.4)],
                         size=rng.uniform(0.95, 1.05), hardness=0.55, vel=1.0, dur=0.15, noise=0.4)
    K.place(y, K.nrm(tile) * 0.3, a)
    K.place(y, K.nrm(ins.modal_hit(rng, [(1650, 0.04, 1.0), (2900, 0.03, 0.7)], hardness=0.5, vel=0.6, dur=0.1)) * 0.15, b)
    slap = K.burst(rng, 0.012, 700, 6000, 0.0003, 0.0018)
    K.place(y, K.nrm(slap) * 0.55, a)
    K.place(y, K.nrm(K.burst(rng, 0.01, 700, 6000, 0.0003, 0.0015)) * 0.3, b)
    K.place(y, K.nrm(K.burst(rng, 0.01, 1500, 7000, 0.0004, 0.002)) * 0.12, b + 0.06)  # the sole peeling off
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)


def step_wet(rng, i=1):
    """Wet feet: a wet slap, water squelching out of the socks, droplets."""
    y, a, b = _foot(rng, f0=110.0, weight=0.9, toe=[0.7, 0.6, 0.8, 0.65][i - 1], soft=0.5)
    y = np.concatenate([y, np.zeros(ns(0.2))])
    for t0, g in ((a, 1.0), (b, 0.6)):
        m = ns(0.18)
        e = K.env_shape(m, [(0, 0), (0.006, 1.0), (0.18, 0)], 1.6)
        sq = K.bubbles(0.18, rng, 900, (0.5, 2.0), env=e)
        sp = dsp.colored(m, rng, 0.0, lo=1500, hi=9000) * e
        K.place(y, (K.nrm(sq) * 0.4 + K.rmsn(sp) * 0.06) * g, t0)
    slap = K.burst(rng, 0.015, 500, 5000, 0.0003, 0.002)
    K.place(y, K.nrm(slap) * 0.35, a)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)
