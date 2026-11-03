"""player.*, camera.*, photo.* and item.* sounds.

Footsteps: one shoe model (heel strike, toe roll, the body's thump, a scuff) through the floor's own resonances,
with material extras: the kitchen's sticky release, the splash of wet asphalt, glass shards crunching, laminated
menus slapping and sliding.  The camera is an old instant camera: a heavy mechanical shutter, the xenon flash's pop
and its charging whine, the eject motor pushing the photo out through the rollers."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _small_rev(y, rng, rt=0.3, wet=0.1):
    return dsp.reverb(y, dsp.make_ir(rt, rng, predelay=0.003, lp_hz=8000), wet=wet)


def _fin(y, hp=40.0, thresh=-62.0, min_len=0.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh, min_len=min_len)


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _st(x, rng, width=0.0006):
    """Mono -> a narrow stereo image (things in your own hands)."""
    d = ns(width)
    return np.vstack([x, np.concatenate([np.zeros(d), x[:len(x) - d]])])


def _hit(rng, modes, vel=1.0, size=1.0, hard=0.7, dur=0.4, noise=0.3):
    return ins.modal_hit(rng, modes, size=size, hardness=hard, vel=vel, dur=dur, noise=noise)


# --------------------------------------------------------------------------
# player
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


def _breath_post(y, rng, room=0.06):
    y = dsp.lowpass(y, 8500, 2)
    y = dsp.close_mic(y, prox_db=3.0, pres_db=2.0, air_db=1.0)
    y = _small_rev(y, rng, 0.3, room)
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


def breath_hold(rng, i=1):
    """A quick deep inhale, the glottis closing, then strained silence: a swallow, the pulse in the ears."""
    y = np.zeros(ns(2.8))
    b = V.breath(rng, "in", 0.55, table=V.VM, scale=1.0, tremor=0.25, trem_rate=10.0)
    dsp.place(y, b, 0.03)
    m = ns(0.012)
    dsp.place(y, dsp.bandpass(rng.standard_normal(m), 500, 2500, 2) * np.exp(-np.arange(m) / (0.002 * SR)) * 0.4 * np.std(b) / 0.1, 0.6)
    sw = V.mouth_clicks(0.3, rng, rate=6.0, level=1.0)  # a swallow
    dsp.place(y, sw * np.std(b) * 2.0, 1.3)
    m = ns(1.6)  # strain: the faint creak of a closed throat
    st = V.creak(m, 22 + 6 * dsp.smooth(m, rng, 1.0, periodic=False), dsp.env_pts(m, [(0, 0), (0.5, 0.3), (1.6, 0.5)]), rng,
                 vowel="x", table=V.VM, jitter=0.5)
    dsp.place(y, st * np.std(b) * 0.25, 1.0)
    for t0 in (1.05, 1.82, 2.55):  # the pulse, felt in the ears
        dsp.place(y, heartbeat(rng) * np.max(np.abs(b)) * 0.3, t0)
    return _breath_post(y, rng)


def gasp(rng, i=1):
    """Letting the held breath go: a ragged gasp for air."""
    if i == 1:
        y = np.zeros(ns(1.6))
        g = V.breath(rng, "in", 0.36, voiced=0.6, f0=190, vowel_in="a")
        g *= np.exp(-np.linspace(0, 1, len(g)) * 0.6)
        g[:ns(0.012)] *= np.linspace(0, 1, ns(0.012))
        dsp.place(y, g * 1.6, 0.02)
        dsp.place(y, V.breath(rng, "out", 0.7, voiced=0.25, f0=150, vowel_out="a"), 0.45)
    else:
        y = np.zeros(ns(1.8))
        dsp.place(y, V.breath(rng, "in", 0.12, voiced=0.3, f0=175, vowel_in="a") * 0.9, 0.02)
        dsp.place(y, V.breath(rng, "in", 0.42, voiced=0.7, f0=205, vowel_in="a") * 1.6, 0.17)
        dsp.place(y, V.breath(rng, "out", 0.8, voiced=0.3, f0=140, vowel_out="a"), 0.65)
    return _breath_post(y, rng, 0.08)


# footsteps ---------------------------------------------------------------------------------------------------
FLOOR = {  # (modes, hardness, body Hz, scuff band, noise)
    "tile": ([(1650, 0.05, 1.0), (2900, 0.035, 0.7), (4300, 0.025, 0.45), (6100, 0.015, 0.25), (850, 0.06, 0.4)], 0.95, 120, (2500, 9000), 0.35),
    "vinyl": ([(520, 0.03, 1.0), (980, 0.025, 0.6), (1600, 0.02, 0.35), (260, 0.04, 0.6)], 0.45, 95, (1200, 6000), 0.25),
    "kitchen": ([(1180, 0.04, 1.0), (2100, 0.03, 0.7), (3500, 0.02, 0.45), (640, 0.05, 0.5)], 0.85, 110, (2000, 8000), 0.3),
    "wet": ([(700, 0.02, 1.0), (1400, 0.015, 0.5)], 0.6, 90, (1500, 9000), 0.4),
}


def _shoe(rng, floor, weight=1.0, heel=1.0, toe=0.7, pace=1.0, scuff=0.3, dur=0.45):
    modes, hard, body_f, (slo, shi), noise = FLOOR[floor]
    y = np.zeros(ns(dur))
    th = 0.008
    tt = th + rng.uniform(0.07, 0.11) * pace
    for t0, g in ((th, heel), (tt, toe * rng.uniform(0.8, 1.1))):
        h = ins.modal_hit(rng, modes, size=rng.uniform(0.92, 1.08), hardness=hard * rng.uniform(0.85, 1.0),
                          vel=g * weight, dur=0.18, noise=noise)
        m = ns(0.12)
        t = np.arange(m) / SR
        b = np.sin(TAU * np.cumsum(body_f * (1 + 0.5 * np.exp(-t / 0.01))) / SR) * dsp.env_ad(m, 0.002, 0.022)
        b += dsp.lowpass(rng.standard_normal(m), 400, 2) * dsp.env_ad(m, 0.001, 0.012) * 0.6
        dsp.place(y, h * 0.6 + dsp.pad_to(b, len(h)) * 0.5 * weight * g, t0)
    if scuff > 0:
        d = rng.uniform(0.04, 0.09)
        m = ns(d)
        sc = dsp.bandpass(rng.standard_normal(m), slo, shi, 2) * dsp.env_pts(m, [(0, 0), (d * 0.3, 1), (d, 0)])
        dsp.place(y, sc * scuff * 0.25, tt + rng.uniform(0.0, 0.03))
    return y


def step_tile(rng, i=1):
    y = _shoe(rng, "tile", weight=1.0, heel=1.0, toe=[0.6, 0.75, 0.5, 0.7][i - 1], scuff=0.16)
    if i == 4:  # a squeak of a rubber sole on glaze
        sq = ins.creak(0.07, rng, [(0, 400), (0.07, 700)], [(0, 0), (0.01, 0.6), (0.07, 0)],
                       modes=[(2300, 20, 1.0), (4600, 25, 0.4)], tonal=0.6)
        dsp.place(y, sq * 0.12, 0.09)
    return _fin(_small_rev(y, rng, 0.12, 0.05), 60)


def step_vinyl(rng, i=1):
    y = _shoe(rng, "vinyl", weight=1.0, heel=1.0, toe=[0.65, 0.55, 0.75, 0.6][i - 1], scuff=0.2, pace=1.1)
    return _fin(_small_rev(y, rng, 0.12, 0.04), 50)


def step_kitchen(rng, i=1):
    """Quarry tiles with a film of old grease: the sole sticks a moment and peels away."""
    y = _shoe(rng, "kitchen", weight=1.0, heel=1.0, toe=[0.6, 0.7, 0.55, 0.65][i - 1], scuff=0.15)
    d = rng.uniform(0.05, 0.09)
    m = ns(d)
    peel = ins.friction_pulses(d, rng, np.linspace(900, 300, m), dsp.env_pts(m, [(0, 0), (0.005, 1.0), (d, 0)]), jitter=0.5)
    peel = dsp.bandpass(peel, 900, 6000, 2)
    dsp.place(y, _nrm(peel) * 0.1, 0.17 + rng.uniform(0, 0.03))
    return _fin(_small_rev(y, rng, 0.12, 0.05), 60)


def step_wet(rng, i=1):
    """Wet asphalt: a slap in the film of water, a splash, grit under the sole."""
    y = _shoe(rng, "wet", weight=0.8, heel=1.0, toe=[0.7, 0.6, 0.8, 0.65][i - 1], scuff=0.2)
    for t0, g in ((0.01, 1.0), (0.1, 0.6)):
        m = ns(0.16)
        sp = ins.water_texture(m, rng, density=900, r_range=(0.5, 2.5), noise=0.3, splash=0.6)
        sp *= dsp.env_pts(m, [(0, 0), (0.006, 1.0), (0.16, 0)]) ** 1.5
        dsp.place(y, _nrm(sp) * 0.45 * g, t0)
    grit = ins.crunch(0.25, rng, lambda t: 1200 * np.exp(-t / 0.04), lo=2500, hi=8000, dec=(0.0002, 0.0006))
    dsp.place(y, grit * 0.04, 0.012)
    return _fin(_small_rev(y, rng, 0.2, 0.05), 60)


def step_glass(rng, i=1):
    """Broken glass under a shoe: shards cracking, grinding against the floor."""
    y = _shoe(rng, "tile", weight=0.8, heel=1.0, toe=0.6, scuff=0.1, dur=0.6)
    shard = [(3100, 0.05, 1.0), (5200, 0.035, 0.7), (7400, 0.025, 0.45), (10200, 0.015, 0.25)]
    for t0, k_n in ((0.01, 14), (0.1, 9)):
        t = t0
        for k in range(k_n):
            dsp.place(y, ins.modal_hit(rng, shard, size=rng.uniform(0.6, 1.4), hardness=1.0, vel=rng.uniform(0.2, 0.8),
                                       dur=0.06, noise=0.5), t)
            t += rng.exponential(0.008)
    gr = ins.crunch(0.3, rng, lambda t: 2500 * np.exp(-t / 0.08), lo=2000, hi=10000, dec=(0.0001, 0.0005))
    dsp.place(y, gr * 0.12, 0.02)
    return _fin(_small_rev(y, rng, 0.15, 0.05), 100)


def step_paper(rng, i=1):
    """Stepping on scattered laminated menus: a flat plastic slap, a crackle, the sheet sliding under the foot."""
    y = _shoe(rng, "vinyl", weight=0.7, heel=1.0, toe=0.5, scuff=0.0)
    m = ns(0.05)
    sl = dsp.bandpass(rng.standard_normal(m), 400, 7000, 2) * dsp.env_ad(m, 0.0006, 0.008)
    dsp.place(y, sl * 0.6, 0.009)
    cr = ins.crackle(0.18, rng, 1800, env=dsp.env_pts(ns(0.18), [(0, 1), (0.18, 0)]), hp=2000, decay=0.0003)
    dsp.place(y, _nrm(cr) * 0.25, 0.012)
    d = 0.22 if i == 1 else 0.3
    m = ns(d)
    slide = dsp.bandpass(rng.standard_normal(m), 1800, 9000, 2) * dsp.env_pts(m, [(0, 0), (0.03, 1), (d, 0)]) ** 1.3
    dsp.place(y, slide * 0.12, 0.1)
    return _fin(_small_rev(y, rng, 0.15, 0.05), 80)


# --------------------------------------------------------------------------
# camera 霊写機 (an old instant camera)
# --------------------------------------------------------------------------
LEATHER = [(240, 0.05, 1.0), (520, 0.04, 0.6), (980, 0.03, 0.35)]
CAM_BODY = [(780, 0.06, 1.0), (1420, 0.05, 0.8), (2380, 0.04, 0.55), (3600, 0.03, 0.35), (5200, 0.02, 0.2)]


def _rustle(rng, d, vel=1.0, lo=500, hi=6000):
    m = ns(d)
    env = dsp.env_pts(m, [(0, 0), (d * 0.25, 1.0), (d, 0)])
    r = dsp.bandpass(rng.standard_normal(m), lo, hi, 2) * env * (0.5 + 0.5 * np.abs(dsp.smooth(m, rng, 30.0, periodic=False)))
    cr = ins.creak(d, rng, [(0, 30), (d / 2, 70), (d, 25)], [(0, 0), (d * 0.3, 0.6), (d, 0)], modes=[(f, 6, a) for f, _, a in LEATHER],
                   jitter=0.4)
    return (r * 0.3 + cr * 0.15) * vel


def camera_raise(rng, i=1):
    y = np.zeros(ns(0.8))
    dsp.place(y, _rustle(rng, 0.35, 1.0), 0.0)  # the strap, leather on cloth
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.6, size=1.1, hard=0.9, dur=0.15), 0.33)  # the finder snaps up
    dsp.place(y, _hit(rng, [(3300, 0.02, 1.0), (5000, 0.015, 0.5)], vel=0.4, hard=1.0, dur=0.06), 0.36)
    return dsp.trim(_st(_small_rev(y, rng, 0.2, 0.05), rng), -60)


def camera_lower(rng, i=1):
    y = np.zeros(ns(0.8))
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.45, size=1.15, hard=0.9, dur=0.15), 0.02)
    dsp.place(y, _rustle(rng, 0.4, 0.9), 0.08)
    return dsp.trim(_st(_small_rev(y, rng, 0.2, 0.05), rng), -60)


def _flash_pop(rng, vel=1.0):
    """Xenon flash discharge: a sharp tick, a crackle of the arc, the tube's ring, the capacitor's dying whistle."""
    m = ns(0.35)
    t = np.arange(m) / SR
    y = rng.standard_normal(m) * np.exp(-t / 0.0015)
    y = dsp.highpass(y, 1500, 2) * 0.8 + dsp.lowpass(rng.standard_normal(m), 500, 2) * np.exp(-t / 0.006) * 0.6
    y += np.sin(TAU * np.cumsum(np.full(m, 6200.0) * np.exp(-t / 0.08)) / SR) * np.exp(-t / 0.05) * 0.12
    return y * vel


def camera_shutter(rng, i=1):
    """The heavy mechanical shutter and the flash (positional: others hear it)."""
    y = np.zeros(ns(0.9))
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.5, size=0.9, hard=1.0, dur=0.08), 0.0)  # the release button
    dsp.place(y, _hit(rng, CAM_BODY, vel=1.0, size=1.25 if i == 1 else 1.35, hard=1.0, dur=0.25, noise=0.6), 0.03)  # ka-
    m = ns(0.08)
    thunk = np.sin(TAU * np.cumsum(170 + 120 * np.exp(-np.arange(m) / SR / 0.01)) / SR) * dsp.env_ad(m, 0.001, 0.02)
    dsp.place(y, thunk * 0.6, 0.03)
    dsp.place(y, _flash_pop(rng, 1.0), 0.045)
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.85, size=1.05, hard=1.0, dur=0.2, noise=0.5), 0.11 if i == 1 else 0.13)  # -chunk
    y = 0.65 * dsp.sat(_nrm(y) * 1.5, 1.0) + 0.35 * _nrm(y)
    return _fin(_small_rev(y, rng, 0.15, 0.04), 80)


def camera_charge(rng, i=1):
    """The flash capacitor charging: the inverter's whine climbing, a faint ripple, then 'ready'."""
    dur = 2.6
    n = ns(dur)
    t = np.arange(n) / SR
    f = 2400 + 5600 * (1 - np.exp(-t / 0.7))
    ph = np.cumsum(f) / SR
    y = np.sin(TAU * ph) + 0.25 * np.sin(2 * TAU * ph + 0.4)
    y *= (1 + 0.15 * np.sin(TAU * 31 * t)) * dsp.env_pts(n, [(0, 0), (0.05, 0.7), (1.8, 1.0), (2.3, 0.6), (dur, 0)])
    y += dsp.bandpass(rng.standard_normal(n), 4000, 10000, 2) * 0.02
    m = ns(0.05)
    dsp.place(y, dsp.highpass(rng.standard_normal(m), 3000, 2) * np.exp(-np.arange(m) / (0.003 * SR)) * 0.15, 2.05)
    return dsp.fade(_st(y * 0.5, rng), 0.0, 0.15)


def camera_eject(rng, i=1):
    """The motor whirrs, the gears turn, the photo slides out between the rollers."""
    dur = 1.5
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.zeros(n)
    sp = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (1.05, 1.0), (1.2, 0.0), (dur, 0)])
    fm = 95.0 * np.maximum(sp, 1e-3)
    ph = np.cumsum(fm) / SR
    mot = (np.sin(TAU * ph * 8) * 0.5 + dsp.saw(fm * 21, n) * 0.4 + np.sin(TAU * ph * 2) * 0.2) * sp
    mot = dsp.lowpass(mot, 3000, 2) + dsp.bandpass(rng.standard_normal(n), 800, 5000, 2) * sp * 0.15
    y += mot * 0.5
    m = ns(0.95)  # the print sliding through the rollers
    sl = dsp.bandpass(rng.standard_normal(m), 1500, 8000, 2) * dsp.env_pts(m, [(0, 0), (0.1, 1.0), (0.85, 1.0), (0.95, 0)])
    dsp.place(y, sl * 0.08, 0.12)
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.35, size=0.8, hard=1.0, dur=0.1), 1.2)  # the photo drops into the slot's lip
    return dsp.trim(_st(_small_rev(y, rng, 0.15, 0.04), rng), -60)


def camera_empty(rng, i=1):
    y = np.zeros(ns(0.6))
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.6, size=0.9, hard=1.0, dur=0.08), 0.0)
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.7, size=1.2, hard=1.0, dur=0.12, noise=0.6), 0.03)  # a dry clack, no flash
    m = ns(0.18)
    mot = np.sin(TAU * 760 * np.arange(m) / SR) * dsp.env_pts(m, [(0, 0), (0.02, 1.0), (0.12, 0.3), (0.18, 0)])
    dsp.place(y, mot * 0.1, 0.12)  # the motor tries, finds nothing, stops
    return dsp.trim(_st(_small_rev(y, rng, 0.15, 0.04), rng), -60)


def camera_zoom(rng, i=1):
    y = np.zeros(ns(0.25))
    m = ns(0.09)
    t = np.arange(m) / SR
    mot = (np.sin(TAU * 1450 * t) * 0.6 + dsp.saw(np.full(m, 2900.0), m) * 0.25) * dsp.env_pts(m, [(0, 0), (0.01, 1), (0.08, 1), (0.09, 0)])
    dsp.place(y, dsp.lowpass(mot, 6000, 2) * 0.5, 0.0)
    dsp.place(y, _hit(rng, [(3800, 0.015, 1.0), (5600, 0.01, 0.5)], vel=0.4, hard=1.0, dur=0.04), 0.09)
    return dsp.trim(_st(y, rng), -60)


def camera_load(rng, i=1):
    y = np.zeros(ns(2.4))
    dsp.place(y, _hit(rng, CAM_BODY, vel=0.8, size=1.0, hard=1.0, dur=0.15, noise=0.5), 0.02)  # the latch
    hg = ins.creak(0.2, rng, [(0, 40), (0.2, 90)], [(0, 0), (0.03, 0.6), (0.2, 0)], modes=[(f, 10, a) for f, _, a in CAM_BODY])
    dsp.place(y, hg * 0.15, 0.08)  # the back swings open
    m = ns(0.35)
    sl = dsp.bandpass(rng.standard_normal(m), 900, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.1, 1), (0.35, 0.3)])
    dsp.place(y, sl * 0.15, 0.45)  # the film pack slides in
    dsp.place(y, _hit(rng, ins.PLASTIC, vel=0.7, size=1.0, hard=1.0, dur=0.12), 0.8)
    dsp.place(y, _hit(rng, CAM_BODY, vel=1.0, size=1.1, hard=1.0, dur=0.2, noise=0.5), 1.15)  # snapped shut
    m = ns(0.8)
    t = np.arange(m) / SR
    mot = (np.sin(TAU * 95 * 8 * t) * 0.5 + dsp.saw(np.full(m, 95.0 * 21), m) * 0.4) * dsp.env_pts(m, [(0, 0), (0.04, 1), (0.7, 1), (0.8, 0)])
    dsp.place(y, dsp.lowpass(mot, 3000, 2) * 0.4, 1.45)  # the dark slide is ejected
    return dsp.trim(_st(_small_rev(y, rng, 0.2, 0.05), rng), -60)


def photo_develop(rng, i=1):
    """The image coming up: a soft chemical sizzle that swells and settles (3 s)."""
    dur = 3.4
    n = ns(dur)
    env = dsp.env_pts(n, [(0, 0), (1.2, 1.0), (2.2, 0.8), (dur, 0)]) ** 1.3
    fz = ins.crackle(dur, rng, 3500, env=env, hp=3000, decay=0.0002)
    hs = dsp.colored(n, rng, 0.0, lo=3000, hi=11000) * env
    tone = np.sin(TAU * np.cumsum(np.linspace(380, 520, n)) / SR) * env * 0.03  # very faint, a breath of something
    y = _nrm(fz) * 0.5 + hs / np.std(hs) * 0.05 + tone
    out = np.vstack([y, np.roll(y, ns(0.003))])
    out = dsp.reverb(out, dsp.make_ir(1.2, rng, stereo=True, predelay=0.01), wet=0.25)
    return dsp.fade(dsp.pad_to(out, n + ns(0.5)), 0.0, 0.4)


def photo_name(rng, i=1):
    """A whisper, and a pen nib scratching the red name onto the margin, stroke by stroke."""
    dur = 3.4
    n = ns(dur)
    pen = np.zeros(n)
    t = 0.35
    for k in range(9):  # brush/pen strokes of a kanji name
        d = rng.uniform(0.08, 0.28)
        m = ns(d)
        rate = rng.uniform(180, 420)
        fp = ins.friction_pulses(d, rng, rate, dsp.env_pts(m, [(0, 0), (0.01, 1.0), (d * 0.7, 0.8), (d, 0)]), jitter=0.5)
        fp = dsp.bandpass(fp, 1500, 9000, 2) + dsp.bandpass(rng.standard_normal(m), 3000, 9000, 2) * 0.05 * dsp.env_pts(m, [(0, 0), (0.01, 1), (d, 0)])
        dsp.place(pen, fp, t)
        t += d + rng.uniform(0.04, 0.16)
    w = V.whisper("na . ma . e", rng, table=V.VF, scale=1.05, rate=0.62, close=0.8, bw=1.25, voicing=0.1, f0=210)
    out = np.zeros((2, n))
    dsp.place(out, dsp.pan(_nrm(pen) * 0.5, 0.15), 0.0)
    wv = dsp.close_mic(dsp.highpass(w, 120, 2), 4.0, 3.0, 3.0)
    dsp.place(out, dsp.pan(_nrm(wv) * 0.45, -0.75), 0.15)
    wr = dsp.reverb(dsp.reverse(_nrm(wv)), dsp.make_ir(2.5, rng, stereo=True, predelay=0.02), wet=1.0, dry=0.0)
    dsp.place(out, _nrm(wr) * 0.08, 0.0)
    return dsp.fade(dsp.trim(out, -62), 0.0, 0.3)


def photo_fail(rng, i=1):
    """A moan smeared under a distorted, slowed shutter."""
    dur = 3.2
    n = ns(dur)
    s, _ = V.scream(2.4, rng, [(0, 180), (0.5, 230), (1.5, 170), (2.4, 120)], [(0, 0), (0.3, 0.8), (1.6, 1.0), (2.4, 0)],
                    vowel_pts=[(0, "o"), (1.2, "a"), (2.4, "u")], chaos_pts=[(0, 0), (1.0, 0), (1.4, 3.6), (2.4, 3.9)],
                    breath=0.8, rasp=0.5, scale=0.95, oq=0.55, closure=1.5)
    s = dsp.varispeed(s, np.linspace(1.0, 0.7, len(s)))
    smear = dsp.reverb(s, dsp.make_ir(2.8, rng, predelay=0.03), wet=1.0, dry=0.4)
    sh = camera_shutter(rng, 1)
    sh = dsp.varispeed(sh, 0.55)
    sh = dsp.crush(sh / (np.max(np.abs(sh)) + 1e-9), bits=5, hold=3)
    out = np.zeros(n)
    dsp.place(out, _nrm(smear) * 0.6, 0.05)
    dsp.place(out, _nrm(sh) * 0.7, 0.0)
    o2 = np.vstack([out, np.roll(out, ns(0.004))])
    return dsp.fade(dsp.highpass(o2, 60, 2), 0.0, 0.4)


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
    return _fin(_small_rev(y, rng, 0.15, 0.05), 200)


def flashlight_off(rng, i=1):
    y = np.zeros(ns(0.35))
    dsp.place(y, _click(rng, 3600, 0.8, ins.METAL, 1.7), 0.01)
    dsp.place(y, _click(rng, 2700, 0.5, ins.METAL, 1.5), 0.06)
    return _fin(_small_rev(y, rng, 0.15, 0.05), 200)


def flashlight_flicker(rng, i=1):
    dur = {1: 0.8, 2: 1.1, 3: 0.6}[i]
    n = ns(dur)
    t = np.arange(n) / SR
    gate = np.zeros(n)
    tt = 0.0
    while tt < dur:  # a bad contact: irregular on/off stutter
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
    return _fin(_small_rev(y, rng, 0.15, 0.04), 80)


def ofuda_burn(rng, i=1):
    """An ofuda flares up: the paper catches with a whoosh, burns fast and bright, small bells, ash falling."""
    dur = 3.6
    n = ns(dur)
    out = np.zeros((2, n))
    m = ns(0.5)
    tt = np.arange(m) / SR
    wh = dsp.tv_filter(rng.standard_normal(m), 300 * 2 ** (np.clip(tt / 0.4, 0, 1) * 4.5), 1.0, "bp") * dsp.env_pts(m, [(0, 0), (0.35, 1), (0.5, 0.6)]) ** 2
    dsp.place(out, np.vstack([wh, np.roll(wh, 300)]) * 0.35 / (np.max(np.abs(wh)) + 1e-9), 0.0)
    m = ns(2.2)
    env = dsp.env_pts(m, [(0, 0), (0.05, 1.0), (0.9, 0.8), (2.2, 0)])
    fl = ins.crackle(2.2, rng, 900, env=env, hp=1200, decay=0.0006)
    roar = dsp.colored(m, rng, -1.5, lo=300, hi=8000) * env * (1 + 0.4 * dsp.smooth(m, rng, 15.0, periodic=False))
    burn = _nrm(fl) * 0.45 + roar / np.std(roar) * 0.08
    dsp.place(out, np.vstack([burn, np.roll(burn, ns(0.002))]), 0.35)
    for p, bases in ((-0.6, (3300, 4200, 5300)), (0.6, (3550, 4500, 5800))):
        j = ins.suzu_jingle(rng, 2.4, [(0.0, 0.9), (0.12, 0.6)], bases=bases, n_hits=(4, 8))
        dsp.place(out, dsp.pan(j, p) * 0.25, 0.4)
    m = ns(1.0)
    sub = np.sin(TAU * np.cumsum(60 + 30 * np.exp(-np.arange(m) / SR / 0.1)) / SR) * dsp.env_ad(m, 0.004, 0.2)
    dsp.place(out, np.vstack([sub, sub]) * 0.25, 0.36)
    out = dsp.reverb(out, dsp.make_ir(1.8, rng, stereo=True, predelay=0.015), wet=0.3)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 35, 2), n), 0.0, 0.5)


def key(rng, i=1):
    y = np.zeros(ns(1.2))
    t = 0.02
    for k in range(rng.integers(9, 14)):
        dsp.place(y, ins.modal_hit(rng, ins.KEYS, size=rng.uniform(0.8, 1.25), hardness=1.0,
                                   vel=rng.uniform(0.25, 1.0) * (1 - k / 16), dur=0.4, noise=0.3), t)
        t += rng.exponential(0.035) + 0.01
    return _fin(_small_rev(y, rng, 0.25, 0.08), 400)


def paper(rng, i=1):
    dur = 0.9 if i == 1 else 0.7
    n = ns(dur)
    env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (dur * 0.5, 0.6), (dur, 0)]) if i == 1 else \
        dsp.env_pts(n, [(0, 0), (0.04, 0.7), (0.2, 1.0), (0.45, 0.3), (dur, 0)])
    cr = ins.crackle(dur, rng, 1500 if i == 1 else 900, env, hp=1200, decay=0.0004)
    sl = dsp.bandpass(rng.standard_normal(n), 800, 7000, 2) * env * (0.5 + 0.5 * np.abs(dsp.smooth(n, rng, 15)))
    y = _nrm(cr) * 0.6 + sl * 0.15
    y = _small_rev(y, rng, 0.2, 0.06)
    return dsp.trim(dsp.highpass(_st(y, rng, 0.0008), 200, 2), -62)
