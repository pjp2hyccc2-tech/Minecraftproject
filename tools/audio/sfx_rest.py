"""The restaurant's one-shots (mono, positional, mostly dry: the acoustics engine adds the room at runtime) and its
PA (stereo).  Everything is synthesised: noise, resonator banks and modal impacts, friction pulse trains, motor and
fluid models.  Chimes and melodies come from jingle.py (original)."""
import numpy as np

import dsp
import instruments as ins
import jingle as J
import voice as V
from dsp import SR, TAU, ns

# ---------------------------------------------------------------- materials (freq, t60, amp)
STEEL_PANEL = [(142, 0.35, 0.8), (231, 0.3, 1.0), (377, 0.28, 0.9), (512, 0.25, 0.8), (698, 0.2, 0.7),
               (933, 0.17, 0.55), (1210, 0.14, 0.45), (1580, 0.11, 0.35), (2090, 0.09, 0.25), (2760, 0.07, 0.18)]
LAMINATE = [(118, 0.12, 0.9), (196, 0.11, 1.0), (305, 0.09, 0.85), (448, 0.08, 0.7), (640, 0.06, 0.5),
            (905, 0.05, 0.35), (1290, 0.04, 0.22), (1850, 0.03, 0.12)]
DOOR_SOLID = [(96, 0.16, 0.9), (168, 0.14, 1.0), (262, 0.12, 0.85), (385, 0.1, 0.7), (560, 0.08, 0.5),
              (790, 0.06, 0.35), (1120, 0.05, 0.22), (1600, 0.04, 0.12)]
CERAMIC = [(1720, 0.18, 1.0), (2940, 0.14, 0.75), (4260, 0.1, 0.55), (5810, 0.07, 0.38), (7650, 0.05, 0.22),
           (9900, 0.03, 0.12)]
SHARD = [(3100, 0.05, 1.0), (5200, 0.035, 0.7), (7400, 0.025, 0.45), (10200, 0.015, 0.25)]
GLASS_TUMBLER = [(1480, 0.35, 1.0), (3620, 0.2, 0.55), (6100, 0.12, 0.3), (8900, 0.07, 0.15)]
ICE = [(2400, 0.02, 1.0), (4100, 0.015, 0.7), (6300, 0.01, 0.45), (8800, 0.008, 0.25)]
COIN = [(4100, 0.25, 1.0), (6850, 0.18, 0.7), (9600, 0.12, 0.45), (12400, 0.08, 0.25)]
LATCH = [(1850, 0.05, 1.0), (2900, 0.04, 0.8), (4300, 0.03, 0.55), (6100, 0.02, 0.3)]
CHROME = [(880, 0.25, 0.8), (1460, 0.2, 1.0), (2310, 0.16, 0.7), (3480, 0.12, 0.5), (5020, 0.08, 0.3)]
CHAIR = [(210, 0.1, 0.8), (340, 0.09, 1.0), (520, 0.07, 0.8), (760, 0.06, 0.6), (1100, 0.045, 0.4),
         (1580, 0.035, 0.25)]
TUBE_GLASS = [(2210, 0.08, 1.0), (3560, 0.06, 0.7), (5120, 0.045, 0.5), (7300, 0.03, 0.3)]


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _near(y, rng, rt=0.16, wet=0.07):
    """A breath of the object's surroundings (the acoustics engine adds the room itself)."""
    return dsp.reverb(y, dsp.make_ir(rt, rng, predelay=0.002, hf_ratio=0.6), wet=wet, dry=1.0)


def _fin(y, hp=40.0, thresh=-64.0, min_len=0.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh, min_len=min_len)


def _hit(rng, modes, vel=1.0, size=1.0, hard=0.7, dur=0.5, noise=0.3):
    return ins.modal_hit(rng, modes, size=size, hardness=hard, vel=vel, dur=dur, noise=noise)


def _click(rng, vel=1.0, f=3200.0, body=LATCH, size=1.0):
    m = ns(0.004)
    c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0004 * SR))
    c = dsp.reson(c, f, 2.0, 2.0) + dsp.highpass(c, 2000, 2)
    b = _hit(rng, body, vel=0.6, size=size, hard=1.0, dur=0.1, noise=0.4)
    return dsp.mix(c, b) * vel


def _thud(rng, f_hi=110.0, f_lo=55.0, tau=0.05, dur=0.35, noise=0.6, lp=600.0):
    m = ns(dur)
    t = np.arange(m) / SR
    s = np.sin(TAU * np.cumsum(f_lo + (f_hi - f_lo) * np.exp(-t / 0.02)) / SR) * dsp.env_ad(m, 0.002, tau)
    s += dsp.lowpass(rng.standard_normal(m), lp, 2) * dsp.env_ad(m, 0.001, tau * 0.5) * noise
    return s


def _friction(rng, dur, rate_pts, amp_pts, modes, jitter=0.25, tonal=0.0, noise=0.06, q_scale=1.0):
    return ins.creak(dur, rng, rate_pts, amp_pts, modes=[(f, q * q_scale, a) for f, q, a in modes], jitter=jitter,
                     tonal=tonal, noise=noise)


def _squeal(rng, dur, f_pts, amp_pts, wob=25.0, harm=(1.0, 0.35, 0.15), bw=30.0):
    """A tonal friction squeal (valve handle, hinge, rubber): narrow-band tone with a wandering pitch."""
    n = ns(dur)
    f = dsp.env_pts(n, f_pts) * dsp.cents(wob * dsp.smooth(n, rng, 9.0, periodic=False))
    a = dsp.env_pts(n, amp_pts)
    y = np.zeros(n)
    for k, g in enumerate(harm, start=1):
        y += g * dsp.nb_noise(f * k, bw * k, rng)
    return y * a


def _water_stream(n, rng, flow=1.0, splash=0.5, bright=1.0, sink=None):
    """Water falling into a basin: turbulent rush + a cloud of bubbles (Minnaert) + the basin's ring."""
    dur = n / SR
    y = np.zeros(n)
    k = rng.poisson(450 * dur * flow)
    for _ in range(k):
        r = np.exp(rng.uniform(np.log(0.6), np.log(4.5)))
        dsp.place(y, ins.bubble(rng, r, rng.pareto(2.5) * 0.25 + 0.08), rng.uniform(0, dur))
    rush = dsp.colored(n, rng, -1.5, lo=250, hi=9000 * bright)
    sp = dsp.colored(n, rng, 0.5, lo=2500, hi=12000 * bright) * np.clip(1 + 0.5 * dsp.smooth(n, rng, 25.0), 0.2, None)
    out = _nrm(y) * 0.6 + rush / np.std(rush) * 0.12 * flow + sp / np.std(sp) * 0.06 * splash
    if sink is not None:
        out = out + dsp.resbank(out, sink) * 0.25
    return out


SINK_STEEL = [(410, 30, 0.6), (990, 40, 0.5), (1720, 50, 0.4), (2580, 60, 0.3)]
SINK_CERAMIC = [(520, 20, 0.5), (1310, 25, 0.35), (2150, 30, 0.25)]


# ==========================================================================
# doors
# ==========================================================================
def _motor(rng, speed, base=34.0, teeth=11, pwm=3150.0, vel=1.0):
    """Door operator: DC motor + gearbox.  speed: per-sample 0..1."""
    n = len(speed)
    f = base * np.maximum(speed, 1e-3)
    ph = np.cumsum(f) / SR
    mesh = dsp.saw(f * teeth, n) * 0.5 + np.sin(TAU * ph * teeth * 2) * 0.25
    comm = np.sin(TAU * ph * 6) * 0.45 + np.sin(TAU * ph * 12) * 0.2 + np.sin(TAU * ph * 18) * 0.1
    y = (dsp.lowpass(mesh, 1400, 2) + comm) * speed ** 1.2
    y = y * (1.0 + 0.25 * np.sin(TAU * ph))  # once per revolution: a slightly bent shaft
    y += dsp.bandpass(rng.standard_normal(n), 250, 1200, 2) * speed * 0.08
    y += np.sin(TAU * pwm * np.arange(n) / SR) * (speed > 0.02) * 0.015
    return y * vel


def _rolling(rng, speed, glass=0.4):
    n = len(speed)
    r = dsp.lowpass(rng.standard_normal(n), 380, 2) * speed
    ticks = ins.friction_pulses(n / SR, rng, 14 + 22 * speed, speed * 0.6, jitter=0.4)
    r += dsp.lowpass(dsp.resbank(ticks, [(f * 1.0, 8, a) for f, _, a in ins.GLASS]), 5000, 2) * glass * 0.03
    r += dsp.resbank(ticks, [(180, 4, 1.0), (310, 5, 0.6)]) * 0.15
    return r


def _auto_door(rng, closing):
    dur = 1.9 if closing else 1.6
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.zeros(n)
    dsp.place(y, _click(rng, 0.35, 4200, LATCH, 0.6), 0.01)  # sensor relay
    if closing:
        speed = dsp.env_pts(n, [(0, 0), (0.08, 0), (0.3, 1.0), (0.85, 1.0), (1.2, 0.35), (1.45, 0.15), (1.5, 0), (dur, 0)])
    else:
        speed = dsp.env_pts(n, [(0, 0), (0.06, 0), (0.25, 1.0), (0.75, 1.0), (0.98, 0.25), (1.08, 0.0), (dur, 0)])
    speed = dsp.lowpass0(speed, 12.0)
    mot = _motor(rng, np.clip(speed, 0, 1), base=33.0 if closing else 36.0)
    roll = _rolling(rng, np.clip(speed, 0, 1))
    y += _nrm(mot) * 0.55 + _nrm(roll) * 0.35
    hiss = dsp.bandpass(rng.standard_normal(n), 1500, 6000, 2) * speed * 0.006  # air through the gap
    y += hiss
    if closing:
        t_end = 1.5
        seal = _thud(rng, 120, 70, 0.06, 0.4, noise=0.9, lp=500)  # the two rubber edges meet
        dsp.place(y, seal * 0.9, t_end)
        dsp.place(y, _hit(rng, ins.GLASS, vel=0.22, size=1.6, hard=0.5, dur=0.5, noise=0.1), t_end + 0.004)
        dsp.place(y, _click(rng, 0.4, 2600, LATCH, 1.2), t_end + 0.11)  # the lock bolt
    else:
        dsp.place(y, _thud(rng, 90, 60, 0.04, 0.3, noise=0.7, lp=400) * 0.45, 1.06)  # soft stop on the bumper
        dsp.place(y, _hit(rng, ins.GLASS, vel=0.12, size=1.6, hard=0.4, dur=0.4, noise=0.1), 1.07)
    return _fin(_near(y, rng), 50)


def auto_open(rng, i=1):
    return _auto_door(rng, False)


def auto_close(rng, i=1):
    return _auto_door(rng, True)


def _speaker_room(y, rng, lo=420.0, hi=7600.0, res=2700.0):
    return ins.small_speaker(y, lo=lo, hi=hi, res=res, drive=1.08)


def chime(rng, i=1):
    """The entrance chime (original): A5 'pin', F5 'poon' from a small speaker above the door."""
    y = J.door_chime(rng, rate=1.0, cents=0.0 if i == 1 else 3.0, vel=1.0, bright=0.55 if i == 1 else 0.7)
    y = _speaker_room(y, rng, res=2700 if i == 1 else 3100)
    return _fin(_near(y, rng, 0.25, 0.08), 200)


def chime_wrong(rng, i=1):
    if i == 1:  # too slow, a quarter-tone flat, the tones beat against themselves; a low third tone, a tritone off
        y = J.door_chime(rng, rate=0.62, cents=-55.0, vel=1.0, bright=0.5)
        y2 = J.door_chime(rng, rate=0.62, cents=-38.0, vel=0.6, bright=0.4)
        y = dsp.mix(y, y2)
        third = ins.chime_bar(J.hz("B4", -55), 4.5, rng, vel=0.9, bright=0.4, decay=3.2)
        n3 = len(third)
        third = dsp.varispeed(third, dsp.cents(dsp.env_pts(n3, [(0, 0), (1.0, 0), (4.5, -140)])))
        out = np.zeros(len(y) + ns(3.5))
        dsp.place(out, y, 0.0)
        dsp.place(out, third, 1.65)
    else:  # normal... then the poon swells instead of fading, and a third tone that should not be there
        y = J.door_chime(rng, rate=0.85, cents=-25.0, vel=1.0)
        n = len(y)
        sw = ins.chime_bar(J.hz("F5", -25), 2.4, rng, vel=1.0, decay=2.0)
        sw = dsp.reverse(dsp.reverb(sw, dsp.make_ir(1.8, rng), wet=1.0, dry=0.3))
        out = np.zeros(n + ns(4.0))
        dsp.place(out, y, 0.0)
        dsp.place_end(out, _nrm(sw) * 0.7, 2.6)
        third = ins.chime_bar(J.hz("C#6", 18), 3.0, rng, vel=0.85, bright=0.8, decay=2.4)
        dsp.place(out, third, 2.62)
    out = _speaker_room(out, rng, res=2500)
    out = dsp.reverb(out, dsp.make_ir(1.4, rng, predelay=0.01, hf_ratio=0.4), wet=0.25, dry=1.0)
    return _fin(out, 200)


def swing(rng, i=1):
    """Kitchen swing doors: the push, the double-acting spring hinge, the leaves flapping and settling."""
    y = np.zeros(ns(2.4))
    push = _hit(rng, DOOR_SOLID, vel=0.9, size=1.1, hard=0.35, dur=0.4, noise=0.35)
    dsp.place(y, push, 0.01)
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.18, size=0.9, hard=0.6, dur=0.4), 0.012)  # the kick plate
    hinge = _friction(rng, 0.45, [(0, 25), (0.2, 60), (0.45, 30)], [(0, 0), (0.05, 0.7), (0.45, 0)],
                      [(640, 18, 1.0), (1290, 22, 0.6), (2110, 25, 0.35)], tonal=0.4)
    dsp.place(y, _nrm(hinge) * 0.2, 0.05)
    t, a, per = 0.45, 0.75, rng.uniform(0.32, 0.4)
    for k in range({1: 4, 2: 5, 3: 3}[i]):  # the leaves swing back and knock on each other, less each time
        h = _hit(rng, DOOR_SOLID, vel=a, size=1.0, hard=0.25, dur=0.35, noise=0.25)
        h = dsp.lowpass(h, 2500, 2)
        dsp.place(y, h, t)
        if rng.random() < 0.6:
            sq = _squeal(rng, 0.25, [(0, 900), (0.25, 1200)], [(0, 0), (0.05, 1), (0.25, 0)], harm=(1.0, 0.3))
            dsp.place(y, sq * 0.04 * a, t - 0.12)
        t += per
        per *= 0.82
        a *= 0.55
    sw = dsp.bandpass(rng.standard_normal(ns(0.4)), 300, 2500, 2) * dsp.env_pts(ns(0.4), [(0, 0), (0.1, 1), (0.4, 0)])
    dsp.place(y, sw * 0.05, 0.0)
    return _fin(_near(y, rng), 50)


def door_open(rng, i=1):
    y = np.zeros(ns(1.4))
    dsp.place(y, _click(rng, 0.45, 2400, LATCH, 1.0), 0.02)  # lever handle down
    dsp.place(y, _click(rng, 0.35, 3100, LATCH, 0.9), 0.11)  # the latch clears the strike
    dsp.place(y, _hit(rng, DOOR_SOLID, vel=0.3, size=1.0, hard=0.3, dur=0.3), 0.115)
    d = 0.55 if i == 1 else 0.8
    cr = _friction(rng, d, [(0, 18), (d * 0.5, 40 if i == 1 else 28), (d, 12)], [(0, 0), (0.08, 0.8), (d, 0)],
                   [(380, 14, 1.0), (760, 18, 0.7), (1240, 22, 0.45), (1900, 25, 0.25)], tonal=0.35 if i == 2 else 0.15)
    dsp.place(y, _nrm(cr) * (0.38 if i == 1 else 0.55), 0.16)
    air = dsp.lowpass(rng.standard_normal(ns(0.6)), 500, 2) * dsp.env_pts(ns(0.6), [(0, 0), (0.25, 1), (0.6, 0)])
    dsp.place(y, air * 0.2, 0.15)
    return _fin(_near(y, rng), 50)


def door_close(rng, i=1):
    y = np.zeros(ns(1.3))
    air = dsp.lowpass(rng.standard_normal(ns(0.35)), 450, 2) * dsp.env_pts(ns(0.35), [(0, 0), (0.25, 1), (0.35, 0)])
    dsp.place(y, air * 0.12, 0.0)
    t0 = 0.33
    dsp.place(y, _hit(rng, DOOR_SOLID, vel=1.0, size=1.0 if i == 1 else 1.15, hard=0.45, dur=0.5, noise=0.3), t0)
    dsp.place(y, _thud(rng, 95, 60, 0.05, 0.3, noise=0.5) * 0.6, t0)
    dsp.place(y, _click(rng, 0.7, 2700, LATCH, 1.1), t0 + 0.006)  # latch snaps into the strike
    if i == 2:  # the door rattles in its frame
        for k in range(3):
            dsp.place(y, _click(rng, 0.25 * (1 - k / 3), 1900, LATCH, 1.3), t0 + 0.05 + k * 0.035)
    return _fin(_near(y, rng), 40)


def fridge_open(rng, i=1):
    y = np.zeros(ns(2.4))
    dsp.place(y, _hit(rng, CHROME, vel=1.0, size=1.2, hard=0.9, dur=0.5, noise=0.3), 0.02)  # the big latch lever
    dsp.place(y, _click(rng, 0.8, 1900, CHROME, 1.3), 0.12)
    m = ns(0.25)  # the gasket lets go: suction pop + tearing rubber
    pop = dsp.lowpass(rng.standard_normal(m), 260, 2) * dsp.env_ad(m, 0.004, 0.035)
    tear = ins.friction_pulses(0.12, rng, 380, 0.5, jitter=0.5)
    tear = dsp.bandpass(dsp.pad_to(tear, m), 400, 3000, 2)
    dsp.place(y, pop * 1.4 + tear * 0.6, 0.2)
    d = 1.3
    cr = _friction(rng, d, [(0, 9), (d * 0.5, 16), (d, 7)], [(0, 0), (0.2, 0.8), (d, 0)],
                   [(160, 10, 1.0), (330, 14, 0.8), (540, 16, 0.5), (820, 18, 0.3)], tonal=0.5, jitter=0.3)
    dsp.place(y, _nrm(cr) * 0.35, 0.35)
    cold = dsp.bandpass(rng.standard_normal(ns(1.6)), 400, 5000, 2) * dsp.env_pts(ns(1.6), [(0, 0), (0.3, 1), (1.6, 0)])
    dsp.place(y, cold * 0.06, 0.25)
    return _fin(_near(y, rng), 40)


def fridge_close(rng, i=1):
    y = np.zeros(ns(1.6))
    air = dsp.lowpass(rng.standard_normal(ns(0.4)), 300, 2) * dsp.env_pts(ns(0.4), [(0, 0), (0.3, 1), (0.4, 0)])
    dsp.place(y, air * 0.15, 0.0)
    t0 = 0.38
    dsp.place(y, _thud(rng, 85, 42, 0.09, 0.6, noise=1.0, lp=350) * 1.2, t0)  # heavy insulated door
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.35, size=1.8, hard=0.4, dur=0.6, noise=0.2), t0 + 0.003)
    dsp.place(y, _hit(rng, CHROME, vel=0.7, size=1.2, hard=0.95, dur=0.4), t0 + 0.07)  # latch drops
    m = ns(0.5)
    seal = dsp.bandpass(rng.standard_normal(m), 600, 4000, 2) * dsp.env_pts(m, [(0, 0), (0.03, 1), (0.5, 0)]) ** 2
    dsp.place(y, seal * 0.08, t0 + 0.02)
    return _fin(_near(y, rng), 30)


def stall_bang(rng, i=1):
    y = np.zeros(ns(1.6))
    dsp.place(y, _hit(rng, LAMINATE, vel=1.0, size=1.0 if i == 1 else 1.12, hard=0.75, dur=0.8, noise=0.45), 0.01)
    dsp.place(y, _thud(rng, 120, 70, 0.04, 0.3, noise=0.8, lp=900) * 0.8, 0.01)
    for k in range(4 if i == 1 else 6):  # latch, hinges and the whole partition rattling
        dsp.place(y, _click(rng, rng.uniform(0.2, 0.5) * (1 - k / 7), rng.uniform(1700, 3200), LATCH, 1.2),
                  0.02 + k * rng.uniform(0.03, 0.06))
    dsp.place(y, _hit(rng, LAMINATE, vel=0.3, size=1.4, hard=0.4, dur=0.6), 0.09)
    y = 0.6 * dsp.sat(_nrm(y) * 1.8, 1.0) + 0.4 * _nrm(y)
    return _fin(_near(y, rng), 40)


def locked(rng, i=1):
    y = np.zeros(ns(1.3))
    for k, t0 in enumerate((0.02, 0.26, 0.47)):  # handle pushed down against the lock, three times
        v = (1.0, 0.8, 0.95)[k]
        dsp.place(y, _click(rng, 0.7 * v, 2300, LATCH, 1.0), t0)
        dsp.place(y, _hit(rng, DOOR_SOLID, vel=0.45 * v, size=1.0, hard=0.4, dur=0.3, noise=0.3), t0 + 0.01)
        dsp.place(y, _click(rng, 0.5 * v, 2800, LATCH, 1.1), t0 + 0.09)
    return _fin(_near(y, rng), 60)


def unlock(rng, i=1):
    y = np.zeros(ns(1.0))
    m = ns(0.2)
    ins_ = dsp.bandpass(rng.standard_normal(m), 2500, 9000, 2) * dsp.env_pts(m, [(0, 0), (0.05, 1), (0.2, 0.4)])
    ins_ *= 1 + 0.8 * np.abs(dsp.smooth(m, rng, 60))
    dsp.place(y, ins_ * 0.25, 0.02)  # key into the cylinder
    for k in range(5):  # pins
        dsp.place(y, _click(rng, 0.2, rng.uniform(3500, 5500), ins.KEYS, 0.7), 0.05 + k * 0.03)
    tw = ins.friction_pulses(0.15, rng, 90, 0.4, jitter=0.4)
    dsp.place(y, dsp.resbank(tw, [(f, 12, a) for f, _, a in LATCH]) * 0.4, 0.3)
    dsp.place(y, _hit(rng, LATCH, vel=1.0, size=1.2, hard=1.0, dur=0.2, noise=0.4), 0.45)  # the bolt
    dsp.place(y, _hit(rng, DOOR_SOLID, vel=0.3, size=1.0, hard=0.5, dur=0.3), 0.452)
    return _fin(_near(y, rng), 80)


def door_switch(rng, i=1):
    y = np.zeros(ns(0.8))
    dsp.place(y, _click(rng, 0.25, 4500, ins.KEYS, 0.7), 0.02)
    tw = ins.friction_pulses(0.12, rng, 120, 0.3, jitter=0.4)
    dsp.place(y, dsp.resbank(tw, [(f, 10, a) for f, _, a in ins.PLASTIC]) * 0.3, 0.06)
    dsp.place(y, _hit(rng, ins.PLASTIC, vel=1.0, size=1.3, hard=1.0, dur=0.15, noise=0.5), 0.2)  # detent clack
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.25, size=0.5, hard=0.9, dur=0.2), 0.2)
    dsp.place(y, _click(rng, 0.25, 3800, LATCH, 0.5), 0.27)  # the relay inside
    return _fin(_near(y, rng), 80)


# ==========================================================================
# the counter, the tables, the call system
# ==========================================================================
def call_button(rng, i=1):
    """Table call button (original two-tone, E6 -> C6) from the little receiver speaker."""
    y = np.zeros(ns(1.1))
    for nm, dt in J.CALL_BUTTON:
        dsp.place(y, J.call_tone(rng, J.hz(nm, 0 if i == 1 else 4), 0.34, 1.0, duty=0.32 if i == 1 else 0.28), dt)
    y = _speaker_room(y, rng, lo=520, hi=7200, res=3000)
    m = ns(0.01)
    dsp.place(y, dsp.highpass(rng.standard_normal(m), 3000, 2) * np.exp(-np.arange(m) / (0.001 * SR)) * 0.05, 0.0)
    return _fin(_near(y, rng), 300)


def call_display(rng, i=1):
    """The call display above the register: a softer three-tone chime, then its beep."""
    y = np.zeros(ns(2.2))
    for nm, dt, v in (("G5", 0.0, 0.9), ("C6", 0.16, 0.95), ("E6", 0.32, 1.0)):
        tone = ins.marimba(J.hz(nm), rng, vel=v, dur=1.4, bright=0.6, hard=0.6)
        tone += 0.4 * ins.chime_bar(J.hz(nm), 1.4, rng, vel=v, bright=0.4, decay=1.0)
        dsp.place(y, tone, dt)
    for dt in (1.05, 1.17):
        dsp.place(y, J.call_tone(rng, 2093.0, 0.09, 0.35, duty=0.5), dt)
    y = _speaker_room(y, rng, lo=380, hi=7800, res=2500)
    return _fin(_near(y, rng, 0.2, 0.06), 250)


BELL_DESK = dict(ratios=[1.0, 2.27, 2.84, 4.08, 5.39, 6.95], amps=[1.0, 0.45, 0.62, 0.3, 0.22, 0.1],
                 t60s=[3.6, 2.1, 1.7, 1.1, 0.7, 0.45])


def service_bell(rng, i=1):
    y = np.zeros(ns(4.0))
    s = ins.bell_strike(rng, 2380.0, vel=1.0, dur=3.8, spec=BELL_DESK, beat=(0.8, 3.0), click=0.7)
    dsp.place(y, s, 0.01)
    dsp.place(y, _hit(rng, ins.METAL, vel=0.25, size=0.6, hard=1.0, dur=0.15), 0.01)  # the plunger
    return _fin(_near(y, rng), 400)


def pass_bell(rng, i=1):
    y = np.zeros(ns(3.0))
    s = ins.bell_strike(rng, 3290.0, vel=1.0, dur=2.8, spec=BELL_DESK, beat=(1.5, 5.0), click=0.9, damp=0.7)
    dsp.place(y, s, 0.01)
    dsp.place(y, _hit(rng, ins.METAL, vel=0.3, size=0.5, hard=1.0, dur=0.12), 0.01)
    return _fin(_near(y, rng), 500)


def register_drawer(rng, i=1):
    y = np.zeros(ns(1.8))
    m = ns(0.09)
    t = np.arange(m) / SR
    beep = np.sign(np.sin(TAU * 2400 * t)) * 0.3 * np.minimum(1, t / 0.002) * np.minimum(1, (0.09 - t) / 0.004)
    dsp.place(y, dsp.lowpass(beep, 6000, 2) * 0.35, 0.01)
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.9, size=0.7, hard=0.9, dur=0.25, noise=0.5), 0.13)  # solenoid release
    d = 0.32
    sl = dsp.lowpass(rng.standard_normal(ns(d)), 1600, 2) * dsp.env_pts(ns(d), [(0, 0), (0.03, 1), (d, 0.4)])
    sl += ins.friction_pulses(d, rng, 160, 0.5, jitter=0.3) * 0.5
    dsp.place(y, sl * 0.35, 0.14)
    t0 = 0.46
    dsp.place(y, _thud(rng, 140, 80, 0.035, 0.25, noise=0.7, lp=1200) * 0.8, t0)  # drawer at its stop
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.5, size=0.8, hard=0.8, dur=0.4), t0)
    for k in range(rng.integers(14, 22)):  # the coins jump in their trays
        dsp.place(y, _hit(rng, COIN, vel=rng.uniform(0.1, 0.45), size=rng.uniform(0.8, 1.3), hard=1.0, dur=0.3, noise=0.2),
                  t0 + rng.exponential(0.06))
    return _fin(_near(y, rng), 80)


def _line(x, rng, crush=True, lo=300.0, hi=3400.0):
    """Telephone line / earpiece: band-limited, mu-law quantised, 8 kHz sample-and-hold grit."""
    y = dsp.bandpass(x, lo, hi, 3)
    if crush:
        pk = np.max(np.abs(y)) + 1e-12
        z = y / pk
        mu = 255.0
        c = np.sign(z) * np.log1p(mu * np.abs(z)) / np.log1p(mu)
        c = np.round(c * 31) / 31
        z = np.sign(c) * ((1 + mu) ** np.abs(c) - 1) / mu
        idx = (np.arange(len(z)) // 5) * 5
        y = z[idx] * pk
        y = dsp.bandpass(y, lo, hi + 200, 4)
    return y


def _packets(y, rng, loss=0.15, frame=0.02, protect=()):
    f = ns(frame)
    out = y.copy()
    for k in range(1, len(y) // f):
        t = k * frame
        if any(a <= t <= b for a, b in protect):
            continue
        r = rng.random()
        if r < loss * 0.6:
            out[k * f:(k + 1) * f] = out[(k - 1) * f:k * f]
        elif r < loss:
            out[k * f:(k + 1) * f] *= 0.05
    return out


def phone_voice(rng, i=1):
    """The register phone: static, and a young man's voice, far down the line: 「……二時に……一名で……」."""
    dur = 6.2 if i == 1 else 7.0
    n = ns(dur)
    v = np.zeros(n)
    a, _ = V.speak_line("ni ji ni", rng, [(4, 3), (5, 2), (1, -1)], [0.11, 0.13, 0.2], table=V.VM, scale=0.98,
                        f0=122.0, breath=0.35, jitter=0.02, shimmer=0.1, tilt=-9, wander=0.4)
    b, _ = V.speak_line("i chi me i de", rng, [(3, 4), 5, (3, 2), 1, (0, -3)], [0.08, 0.1, 0.11, 0.1, 0.26],
                        table=V.VM, scale=0.98, f0=118.0, breath=0.4, jitter=0.025, shimmer=0.12, tilt=-10, wander=0.4)
    ta, tb = (1.1, 3.4) if i == 1 else (1.6, 4.4)
    dsp.place(v, _nrm(a), ta)
    dsp.place(v, _nrm(b) * 0.9, tb)
    if i == 2:  # the line stutters on the last word and a breath follows, too close
        k0 = ns(tb + len(b) / SR - 0.3)
        seg = v[k0:k0 + ns(0.06)].copy()
        for j in range(4):
            dsp.place(v, seg * (0.8 ** j), tb + len(b) / SR - 0.24 + j * 0.06)
        br = V.breath(rng, "out", 0.9, table=V.VM, scale=0.98)
        dsp.place(v, _nrm(br) * 0.35, tb + len(b) / SR + 0.4)
    protect = [(ta, ta + len(a) / SR), (tb, tb + len(b) / SR)]
    v = _packets(v, rng, 0.22, protect=[(p0 + 0.08, p1 - 0.08) for p0, p1 in protect])
    st = dsp.colored(n, rng, -1.0, lo=300, hi=3500) * (0.55 + 0.45 * np.abs(dsp.smooth(n, rng, 5.0)))
    st += ins.crackle(dur, rng, 45, hp=800) * 0.6
    tt = np.arange(n) / SR
    hum = np.sin(TAU * 50 * tt) + 0.5 * np.sin(TAU * 150 * tt)
    for _ in range(4):  # digital bursts on the line
        m = ns(rng.uniform(0.03, 0.1))
        bst = np.sin(TAU * np.cumsum(np.linspace(rng.uniform(600, 2500), rng.uniform(600, 2500), m)) / SR)
        dsp.place(v, bst * dsp.env_ad(m, 0.002, 0.03) * 0.25, rng.uniform(0.2, dur - 0.3))
    y = _nrm(v) * 0.75 + st / np.std(st) * 0.07 + hum * 0.012
    y = _line(y, rng)
    y = dsp.fade(y, 0.15, 0.25)
    out = np.vstack([y * 0.35, y])  # held to the right ear
    return dsp.trim(out, -64)


def faucet_on(rng, i=1):
    y = np.zeros(ns(2.0))
    sq = _squeal(rng, 0.32, [(0, 1250), (0.32, 1700)], [(0, 0), (0.05, 1.0), (0.25, 0.7), (0.32, 0)], wob=40,
                 harm=(1.0, 0.4, 0.2))
    dsp.place(y, sq * 0.12, 0.02)
    dsp.place(y, _hit(rng, CHROME, vel=0.25, size=0.8, hard=0.9, dur=0.2), 0.02)
    for k, t0 in enumerate((0.36, 0.45, 0.5, 0.56)):  # air in the pipe spits
        m = ns(0.05)
        p = dsp.bandpass(rng.standard_normal(m), 700, 6000, 2) * dsp.env_ad(m, 0.003, 0.012)
        dsp.place(y, p * (0.5 + 0.3 * k), t0)
    m = ns(1.35)
    w = _water_stream(m, rng, flow=1.0, splash=0.8, sink=SINK_STEEL)
    w *= dsp.env_pts(m, [(0, 0), (0.15, 0.6), (0.4, 1.0), (m / SR, 1.0)])
    dsp.place(y, _nrm(w) * 1.0, 0.6)
    return dsp.fade(_near(y, rng)[:ns(2.0)], 0.0, 0.08)


def faucet_off(rng, i=1):
    y = np.zeros(ns(3.2))
    m = ns(0.5)
    w = _water_stream(m, rng, flow=1.0, splash=0.8, sink=SINK_STEEL)
    w *= dsp.env_pts(m, [(0, 1.0), (0.3, 0.8), (0.42, 0.15), (0.5, 0)])
    dsp.place(y, _nrm(w) * 0.55, 0.0)
    sq = _squeal(rng, 0.28, [(0, 1600), (0.28, 1150)], [(0, 0), (0.04, 1.0), (0.28, 0)], wob=40)
    dsp.place(y, sq * 0.1, 0.12)
    from sfx_beds import _drip
    t = 0.75
    for k in range(4):  # the last drops off the spout into the steel sink
        d = _drip(rng, rng.uniform(900, 1300) * (1 - 0.04 * k), 1.0 - 0.15 * k)
        d = d + dsp.resbank(d, SINK_STEEL) * 0.15
        dsp.place(y, d * 0.6, t)
        t += rng.uniform(0.35, 0.7) * (1 + 0.4 * k)
    return _fin(_near(y, rng), 120)


def drink_pour(rng, i=1):
    y = np.zeros(ns(3.2))
    dsp.place(y, _click(rng, 0.5, 1500, ins.PLASTIC, 1.2), 0.01)  # the lever / solenoid
    m = ns(0.18)
    hum = np.sin(TAU * 100 * np.arange(m) / SR) * dsp.env_pts(m, [(0, 0), (0.02, 1), (0.18, 0.6)]) * 0.08
    dsp.place(y, hum, 0.02)
    d = 1.9 if i == 1 else 2.3
    n = ns(d)
    w = _water_stream(n, rng, flow=0.9, splash=0.5, bright=0.9)
    fizz = ins.crackle(d, rng, 2600, hp=3500, decay=0.0003)  # carbonation
    rise = dsp.env_pts(n, [(0, 0), (0.08, 1.0), (d - 0.12, 1.0), (d, 0)])
    pitch = dsp.tv_filter(rng.standard_normal(n), dsp.env_pts(n, [(0, 380), (d, 820)]), 3.0, "bp")  # the cup fills
    body = _nrm(w) * 0.5 + _nrm(fizz) * 0.25 + _nrm(pitch) * 0.12
    dsp.place(y, body * rise, 0.05)
    for k in range(rng.integers(3, 6)):  # ice shifts in the cup
        dsp.place(y, dsp.mix(_hit(rng, ICE, vel=rng.uniform(0.15, 0.4), size=rng.uniform(0.8, 1.2), hard=1.0, dur=0.15),
                             _hit(rng, ins.PLASTIC, vel=0.1, size=1.3, dur=0.1)), rng.uniform(0.3, d))
    dsp.place(y, _click(rng, 0.35, 1300, ins.PLASTIC, 1.2), d + 0.06)
    tail = ins.crackle(1.0, rng, 900, env=dsp.env_pts(ns(1.0), [(0, 1), (1.0, 0)]), hp=4000, decay=0.0003)
    dsp.place(y, _nrm(tail) * 0.08, d + 0.08)
    return _fin(_near(y, rng), 100)


def ice_drop(rng, i=1):
    y = np.zeros(ns(2.2))
    m = ns(0.5)
    mot = np.sin(TAU * 120 * np.arange(m) / SR) * dsp.env_pts(m, [(0, 0), (0.05, 1), (0.4, 1), (0.5, 0)]) * 0.04
    dsp.place(y, mot, 0.0)
    t = 0.12
    k_tot = rng.integers(18, 28) if i == 1 else rng.integers(10, 16)
    for k in range(k_tot):  # cubes tumble onto the heap
        v = rng.uniform(0.3, 1.0) * (1 - 0.5 * k / k_tot)
        dsp.place(y, _hit(rng, ICE, vel=v, size=rng.uniform(0.7, 1.4), hard=1.0, dur=0.12, noise=0.5), t)
        dsp.place(y, _thud(rng, 220, 140, 0.012, 0.08, noise=0.5, lp=1500) * v * 0.3, t)
        t += rng.exponential(0.03) + 0.004
    return _fin(_near(y, rng, 0.25, 0.1), 150)


def gas_ignite(rng, i=1):
    y = np.zeros(ns(2.6))
    m = ns(0.9)
    hiss = dsp.bandpass(rng.standard_normal(m), 900, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.1, 0.6), (0.9, 0.8)])
    dsp.place(y, hiss * 0.08, 0.0)
    for t0 in (0.08, 0.36, 0.62):  # piezo clicks with their little spark
        c = _click(rng, 0.35, 4200, LATCH, 0.6)
        sp = ins.crackle(0.03, rng, 6000, hp=5000, decay=0.0001) * 0.4
        dsp.place(y, c + dsp.pad_to(sp, len(c)), t0)
    t0 = 0.66
    m = ns(0.8)
    tt = np.arange(m) / SR
    whoomph = dsp.lowpass(rng.standard_normal(m), 260, 2) * dsp.env_ad(m, 0.012, 0.09)
    whoomph += np.sin(TAU * np.cumsum(55 + 50 * np.exp(-tt / 0.04)) / SR) * dsp.env_ad(m, 0.01, 0.08) * 0.8
    dsp.place(y, whoomph * 1.2, t0)
    m = ns(1.8)
    roar = dsp.colored(m, rng, -2.0, lo=120, hi=4000) * (1 + 0.25 * dsp.smooth(m, rng, 12.0))
    roar *= dsp.env_pts(m, [(0, 0), (0.06, 1.0), (0.3, 0.55), (1.8, 0.5)])
    dsp.place(y, roar / np.std(roar) * 0.55, t0)
    return dsp.fade(_near(y, rng)[:ns(2.6)], 0.0, 0.3)


def _fluor_buzz(n, rng, f=100.0, level=1.0):
    """Magnetic ballast hum: 100 Hz and its harmonics + the tube's 'zzz' (gated HF noise)."""
    t = np.arange(n) / SR
    y = sum(a * np.sin(TAU * f * k * t + rng.random() * 6) for k, a in ((1, 1.0), (2, 0.45), (3, 0.6), (4, 0.2), (5, 0.25), (7, 0.1)))
    z = dsp.bandpass(rng.standard_normal(n), 2500, 9000, 2) * (0.5 + 0.5 * np.cos(TAU * f * t)) ** 8
    return (y * 0.6 + z * 0.5) * level


def breaker_off(rng, i=1):
    dur = 3.6
    n = ns(dur)
    y = np.zeros(n)
    t0 = 0.03
    dsp.place(y, _hit(rng, ins.PLASTIC, vel=1.0, size=1.6, hard=1.0, dur=0.2, noise=0.6), t0)  # the lever
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.9, size=0.9, hard=0.8, dur=0.5, noise=0.4), t0 + 0.004)
    dsp.place(y, _thud(rng, 150, 70, 0.05, 0.3, noise=0.8, lp=1500) * 0.9, t0)
    tt = np.arange(n) / SR
    buzz = _fluor_buzz(n, rng, level=1.0) * (tt < t0 + 0.012)  # the lights' hum cut dead
    y += buzz * 0.12
    f = 760 * np.exp(-np.clip(tt - t0, 0, None) / 1.1) + 35  # fans and compressors spinning down
    wh = np.sin(TAU * np.cumsum(f) / SR) + 0.3 * np.sin(TAU * np.cumsum(f * 2.02) / SR)
    wh *= np.clip((tt - t0) / 0.01, 0, 1) * np.exp(-np.clip(tt - t0, 0, None) / 1.1) * (tt > t0)
    y += wh * 0.34
    y += dsp.lowpass(rng.standard_normal(n), 800, 2) * np.exp(-np.clip(tt - t0, 0, None) / 0.8) * (tt > t0) * 0.06
    return _fin(_near(y, rng), 25)


def breaker_on(rng, i=1):
    dur = 3.6
    n = ns(dur)
    y = np.zeros(n)
    t0 = 0.03
    dsp.place(y, _hit(rng, ins.PLASTIC, vel=1.0, size=1.6, hard=1.0, dur=0.2, noise=0.6), t0)
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.85, size=0.9, hard=0.8, dur=0.5, noise=0.4), t0 + 0.004)
    dsp.place(y, _thud(rng, 150, 70, 0.05, 0.3, noise=0.8, lp=1500) * 0.9, t0)
    tt = np.arange(n) / SR
    for k in range(7):  # tubes striking one after another: starter tick, a flicker, the ballast's ping
        ts = t0 + 0.25 + k * rng.uniform(0.12, 0.3)
        dsp.place(y, _click(rng, 0.25, rng.uniform(2800, 4200), LATCH, 0.5), ts)
        dsp.place(y, _hit(rng, TUBE_GLASS, vel=rng.uniform(0.25, 0.5), size=rng.uniform(0.9, 1.2), hard=0.9, dur=0.3), ts + 0.06)
        fl = _fluor_buzz(ns(0.25), rng) * dsp.env_pts(ns(0.25), [(0, 0), (0.02, 1), (0.08, 0.2), (0.12, 1), (0.25, 0.6)])
        dsp.place(y, fl * 0.08, ts + 0.02)
    swell = np.clip((tt - t0 - 0.3) / 1.6, 0, 1) ** 1.5
    y += _fluor_buzz(n, rng, level=1.0) * swell * 0.1
    return dsp.fade(_near(y, rng)[:n], 0.0, 0.25)


def light_flicker(rng, i=1):
    dur = {1: 0.9, 2: 1.4, 3: 0.7}[i]
    n = ns(dur)
    gate = np.zeros(n)
    t = 0.0
    while t < dur:
        on = rng.uniform(0.015, 0.12)
        gate[ns(t):ns(t + on)] = rng.uniform(0.5, 1.0)
        t += on + rng.uniform(0.01, 0.15)
    gate = dsp.lowpass0(gate, 300)
    y = _fluor_buzz(n, rng) * gate * 0.5
    for e in np.nonzero(np.abs(np.diff(gate)) > 0.05)[0][::4]:
        dsp.place(y, _click(rng, rng.uniform(0.15, 0.35), rng.uniform(2500, 4500), LATCH, 0.5), int(e))
    return _fin(_near(y, rng), 80)


def light_burst(rng, i=1):
    y = np.zeros(ns(2.4))
    m = ns(0.12)
    pop = dsp.lowpass(rng.standard_normal(m), 3000, 2) * dsp.env_ad(m, 0.0008, 0.012)
    pop += dsp.lowpass(rng.standard_normal(m), 400, 2) * dsp.env_ad(m, 0.002, 0.03) * 1.5
    dsp.place(y, pop * 1.2, 0.02)
    dsp.place(y, _hit(rng, TUBE_GLASS, vel=1.0, size=0.9, hard=1.0, dur=0.4, noise=0.6), 0.021)
    buzz = _fluor_buzz(ns(0.02), rng) * 0.3
    dsp.place(y, buzz, 0.0)
    t = 0.04
    for k in range(rng.integers(30, 45)):  # the tube's glass rains down
        dsp.place(y, _hit(rng, SHARD, vel=rng.uniform(0.08, 0.5) * np.exp(-k / 25), size=rng.uniform(0.6, 1.3), hard=1.0,
                          dur=0.08, noise=0.4), t)
        t += rng.exponential(0.035)
    for k in range(6):  # bigger pieces landing on the floor a moment later
        dsp.place(y, _hit(rng, TUBE_GLASS, vel=rng.uniform(0.2, 0.5), size=rng.uniform(1.0, 1.6), hard=1.0, dur=0.2),
                  rng.uniform(0.35, 0.6))
    y = 0.65 * dsp.sat(_nrm(y) * 1.6, 1.0) + 0.35 * _nrm(y)
    return _fin(_near(y, rng), 60)


def time_card(rng, i=1):
    y = np.zeros(ns(1.4))
    m = ns(0.18)
    sl = dsp.bandpass(rng.standard_normal(m), 900, 6000, 2) * dsp.env_pts(m, [(0, 0), (0.04, 1), (0.18, 0.2)])
    dsp.place(y, sl * 0.2, 0.02)  # the card slides in
    m = ns(0.32)
    tt = np.arange(m) / SR
    mot = np.sin(TAU * 180 * tt) * 0.5 + dsp.bandpass(rng.standard_normal(m), 600, 3000, 2) * 0.5
    dsp.place(y, mot * dsp.env_pts(m, [(0, 0), (0.03, 1), (0.3, 1), (0.32, 0)]) * 0.12, 0.22)
    dsp.place(y, _hit(rng, STEEL_PANEL, vel=1.0, size=0.6, hard=1.0, dur=0.3, noise=0.6), 0.58)  # ka-CHUNK
    dsp.place(y, _thud(rng, 160, 90, 0.03, 0.2, noise=0.8, lp=2000) * 0.8, 0.58)
    dsp.place(y, _click(rng, 0.5, 2500, LATCH, 0.9), 0.66)
    dsp.place(y, sl[::-1] * 0.15, 0.85)
    return _fin(_near(y, rng), 80)


def locker(rng, i=1):
    y = np.zeros(ns(1.6))
    if i == 1:  # opened: latch lifted, thin steel door swings, wobbles
        dsp.place(y, _hit(rng, LATCH, vel=0.8, size=1.1, hard=1.0, dur=0.2), 0.02)
        hg = _friction(rng, 0.5, [(0, 30), (0.25, 70), (0.5, 25)], [(0, 0), (0.05, 0.8), (0.5, 0)],
                       [(720, 16, 1.0), (1450, 20, 0.6), (2300, 24, 0.4)], tonal=0.5)
        dsp.place(y, _nrm(hg) * 0.18, 0.1)
        dsp.place(y, _hit(rng, STEEL_PANEL, vel=0.45, size=0.8, hard=0.5, dur=0.8, noise=0.2), 0.12)
    else:  # shut: the panel slams, rattles, latch clicks
        dsp.place(y, _hit(rng, STEEL_PANEL, vel=1.0, size=0.8, hard=0.8, dur=0.9, noise=0.4), 0.02)
        dsp.place(y, _thud(rng, 130, 70, 0.04, 0.25, noise=0.6) * 0.6, 0.02)
        for k in range(4):
            dsp.place(y, _click(rng, 0.3 * (1 - k / 4), rng.uniform(1800, 3000), LATCH, 1.0), 0.05 + k * 0.03)
    return _fin(_near(y, rng), 50)


def chair_scrape(rng, i=1):
    d = {1: 0.6, 2: 1.15, 3: 0.95}[i]
    y = np.zeros(ns(d + 0.6))
    if i == 3:  # two jerks
        segs = [(0.0, 0.38, 1.0), (0.55, d - 0.55, 0.8)]
    else:
        segs = [(0.0, d, 1.0)]
    for t0, sd, v in segs:
        rate = [(0, 40), (sd * 0.4, 110 if i != 2 else 75), (sd, 50)]
        amp = [(0, 0), (0.04, 0.9 * v), (sd * 0.8, v), (sd, 0)]
        sc = _friction(rng, sd, rate, amp, [(f, 6, a) for f, _, a in CHAIR] + [(1850, 10, 0.4), (2600, 12, 0.25)],
                       jitter=0.35, tonal=0.25, noise=0.15)
        dsp.place(y, _nrm(sc) * v, t0)
        dsp.place(y, _hit(rng, CHAIR, vel=0.25 * v, size=1.0, hard=0.4, dur=0.2), t0)
    return _fin(_near(y, rng), 60)


def chair_fall(rng, i=1):
    y = np.zeros(ns(2.0))
    sc = _friction(rng, 0.18, [(0, 60), (0.18, 120)], [(0, 0), (0.03, 0.6), (0.18, 0.3)], [(f, 6, a) for f, _, a in CHAIR])
    dsp.place(y, _nrm(sc) * 0.4, 0.0)
    t0 = 0.32
    dsp.place(y, _hit(rng, CHAIR, vel=1.0, size=1.1, hard=0.7, dur=0.5, noise=0.5), t0)  # the backrest lands
    dsp.place(y, _thud(rng, 110, 60, 0.05, 0.35, noise=0.9, lp=1200) * 0.9, t0)
    for k, (dt, v) in enumerate(((0.07, 0.6), (0.16, 0.45), (0.21, 0.3), (0.33, 0.2))):  # legs, a bounce, the settle
        dsp.place(y, _hit(rng, CHAIR, vel=v, size=rng.uniform(0.8, 1.2), hard=0.6, dur=0.3), t0 + dt + (0.05 if i == 2 else 0))
    if i == 2:  # it clatters against a table leg on the way down
        dsp.place(y, _hit(rng, ins.METAL, vel=0.4, size=1.6, hard=0.8, dur=0.6), 0.2)
    y = 0.55 * dsp.sat(_nrm(y) * 1.6, 1.0) + 0.45 * _nrm(y)
    return _fin(_near(y, rng), 40)


def plate_break(rng, i=1):
    y = np.zeros(ns(2.6))
    t0 = 0.02
    dsp.place(y, _hit(rng, CERAMIC, vel=1.0, size=rng.uniform(0.9, 1.1), hard=1.0, dur=0.4, noise=0.9), t0)
    m = ns(0.12)
    crash = dsp.highpass(rng.standard_normal(m), 900, 2) * dsp.env_ad(m, 0.0005, 0.02)
    crash += dsp.lowpass(rng.standard_normal(m), 500, 2) * dsp.env_ad(m, 0.001, 0.025) * 1.2
    dsp.place(y, crash * 1.3, t0)
    t = t0 + 0.004
    n_sh = rng.integers(45, 70)
    for k in range(n_sh):  # fragments flying and landing on the tiles
        v = rng.uniform(0.1, 0.8) * np.exp(-k / (n_sh * 0.45))
        dsp.place(y, _hit(rng, SHARD if rng.random() < 0.7 else CERAMIC, vel=v, size=rng.uniform(0.6, 1.6), hard=1.0,
                          dur=0.15, noise=0.5), t)
        t += rng.exponential(0.018) * (1 + 3 * k / n_sh)
    if i != 2:  # a large piece spins on the floor and settles, faster and faster
        tt, per, v = 0.5, 0.11, 0.35
        while per > 0.012 and tt < 2.2:
            dsp.place(y, _hit(rng, CERAMIC, vel=v, size=1.3, hard=0.9, dur=0.12, noise=0.3), tt)
            tt += per
            per *= 0.86
            v *= 0.94
    y = 0.6 * dsp.sat(_nrm(y) * 1.7, 1.0) + 0.4 * _nrm(y)
    return _fin(_near(y, rng), 60)


def glass_set(rng, i=1):
    y = np.zeros(ns(1.6))
    dsp.place(y, _hit(rng, DOOR_SOLID, vel=0.5, size=0.6, hard=0.6, dur=0.2, noise=0.3), 0.02)  # on the table top
    dsp.place(y, _hit(rng, GLASS_TUMBLER, vel=0.5, size=1.0, hard=0.8, dur=0.6, noise=0.2), 0.021)
    m = ns(0.45)
    slosh = ins.water_texture(m, rng, density=220, r_range=(1.5, 6.0), noise=0.3, splash=0.1)
    dsp.place(y, dsp.lowpass(slosh, 3000, 2) * dsp.env_pts(m, [(0, 0), (0.03, 1), (0.45, 0)]) * 0.15, 0.03)
    for k in range(2 if i == 1 else 4):  # ice against the glass
        dsp.place(y, dsp.mix(_hit(rng, GLASS_TUMBLER, vel=rng.uniform(0.12, 0.3), size=rng.uniform(0.6, 0.8), hard=1.0, dur=0.3),
                             _hit(rng, ICE, vel=0.15, dur=0.1)), 0.06 + rng.uniform(0, 0.35))
    return _fin(_near(y, rng), 120)


def menu_scatter(rng, i=1):
    dur = 2.4 if i == 1 else 2.0
    n = ns(dur)
    y = np.zeros(n)
    k_tot = 5 if i == 1 else 4
    for k in range(k_tot):
        t0 = k * rng.uniform(0.08, 0.2)
        fl = rng.uniform(0.4, 0.75)  # tumbling flight: low flapping of a stiff laminated sheet
        m = ns(fl)
        tt = np.arange(m) / SR
        rate = rng.uniform(9, 16)
        flap = (0.5 + 0.5 * np.sin(TAU * rate * tt + rng.random() * 6)) ** 3
        air = dsp.bandpass(rng.standard_normal(m), 250, 2500, 2) * flap * dsp.env_pts(m, [(0, 0), (0.05, 1), (fl, 0.7)])
        dsp.place(y, air * 0.25, t0)
        tl = t0 + fl
        m2 = ns(0.05)  # it slaps down flat on the floor
        sl = dsp.bandpass(rng.standard_normal(m2), 300, 6000, 2) * dsp.env_ad(m2, 0.0008, 0.008)
        sl += dsp.lowpass(rng.standard_normal(m2), 400, 2) * dsp.env_ad(m2, 0.001, 0.012) * 0.8
        dsp.place(y, sl * rng.uniform(0.6, 1.0), tl)
        ds = rng.uniform(0.25, 0.6)  # and slides over the vinyl
        m3 = ns(ds)
        sd = dsp.bandpass(rng.standard_normal(m3), 1500, 9000, 2) * dsp.env_pts(m3, [(0, 0), (0.02, 1), (ds, 0)]) ** 1.5
        dsp.place(y, sd * 0.12, tl + 0.01)
        cr = ins.crackle(ds, rng, 300, env=dsp.env_pts(m3, [(0, 1), (ds, 0)]), hp=2500)
        dsp.place(y, _nrm(cr) * 0.05, tl + 0.01)
    if i == 1:  # the stand topples too: condiments rattle
        dsp.place(y, _hit(rng, ins.PLASTIC, vel=0.5, size=1.2, dur=0.15), 0.1)
        dsp.place(y, _hit(rng, GLASS_TUMBLER, vel=0.2, size=0.5, dur=0.2), 0.14)
    return _fin(_near(y, rng), 80)


# ==========================================================================
# the PA (stereo: the ceiling speakers are everywhere)
# ==========================================================================
def _pa(x, rng, stereo=True, wet=0.45, rt=1.4):
    """Store PA: a microphone too close, a cheap amp, ceiling speakers all over a hall."""
    y = dsp.highpass(x, 260, 3)
    y = dsp.lowpass(y, 6200, 3)
    y = dsp.peq(y, 1900, 1.0, 5.0)
    pk = np.max(np.abs(y)) + 1e-12
    y = dsp.sat(y / pk * 1.6, 1.0) * pk / 1.6
    n = len(y)
    out = np.vstack([y, y])
    for c, d in ((0, 0.021), (1, 0.034)):  # the next speakers down the hall
        dl = np.zeros(n)
        k = ns(d)
        dl[k:] = y[:n - k]
        out[c] += dl * 0.45
    ir = dsp.make_ir(rt, rng, stereo=True, predelay=0.03, hf_ratio=0.45)
    return dsp.reverb(out, ir, wet=wet, dry=0.8)


def pa_chime(rng, i=1):
    y = J.pa_chime(rng)
    m = ns(0.08)
    y = np.concatenate([np.zeros(ns(0.12)), y])
    pop = dsp.lowpass(rng.standard_normal(m), 200, 2) * dsp.env_ad(m, 0.002, 0.02) * 0.4  # the mic switches on
    dsp.place(y, pop, 0.0)
    hiss = dsp.colored(len(y), rng, -1.0, lo=500, hi=6000) * 0.015
    out = _pa(_nrm(y) + hiss, rng, wet=0.4)
    return dsp.trim(dsp.highpass(out, 120, 2), -60)


def pa_voice(rng, i=1):
    if i == 3:  # the manager, through the PA: 本日の営業は、終了いたしました
        v, _ = _closed_line(rng, f0=104.0, tired=True)
    else:
        scripts = (["go ra i te N | no | o kya ku sa ma | ni | o shi ra se | i ta shi ma su",
                    "ta da i ma | yo ri | to u te N | wa"] if i == 1 else
                   ["o kya ku sa ma | o kya ku sa ma", "ma da | o ra re ma su ka"])
        parts = []
        for k, sc in enumerate(scripts):
            s = V.speak(sc, rng, table=V.VF if i == 1 else V.VM, f0=215 if i == 1 else 102, rate=0.95 if i == 1 else 0.8,
                        accent=2.0, declination=2.5, breath=0.2, tilt=-7, slur=0.15 if i == 1 else 0.35)
            parts.append(s)
        v = np.zeros(sum(len(p) for p in parts) + ns(0.8))
        t = 0.0
        for p in parts:
            dsp.place(v, p, t)
            t += len(p) / SR + 0.35
        if i == 2:  # dropouts: the cable is loose
            g = np.ones(len(v))
            for _ in range(5):
                a = ns(rng.uniform(0.2, t))
                g[a:a + ns(rng.uniform(0.05, 0.3))] *= 0.04
            v = v * dsp.lowpass0(g, 60.0)
    n = len(v) + ns(0.7)
    y = np.zeros(n)
    m = ns(0.08)
    dsp.place(y, dsp.lowpass(rng.standard_normal(m), 220, 2) * dsp.env_ad(m, 0.002, 0.02) * 0.5, 0.0)
    dsp.place(y, _nrm(v), 0.25)
    dsp.place(y, dsp.lowpass(rng.standard_normal(m), 300, 2) * dsp.env_ad(m, 0.002, 0.015) * 0.35, n / SR - 0.2)
    y += dsp.colored(n, rng, -1.0, lo=400, hi=6000) * (0.008 if i == 3 else 0.02)
    tt = np.arange(n) / SR
    y += (np.sin(TAU * 50 * tt) + 0.4 * np.sin(TAU * 150 * tt)) * 0.008
    out = _pa(y, rng, wet=0.32 if i == 3 else 0.5, rt=1.2)
    return dsp.trim(dsp.highpass(out, 120, 2), -60)


def _closed_line(rng, f0=106.0, tired=True):
    """「本日の営業は、終了いたしました」 - the announcement every closing time (male, formal, flat)."""
    script = "ho N ji tsu no | e i gyo u wa , shu u ryo u | i ta shi ma shi ta"
    #          ho  ji  tsu no   e  i gyo u  wa   shu u  ryo u   i  ta shi ma shi ta
    pitch = [2, 4, 3, 3, 2, 3, 3, 2, 1, 3, 2, 2, 1, 1.5, 1.5, 1, 0, -1, (-2, -4)]
    durs = [0.1, 0.08, 0.07, 0.09, 0.09, 0.07, 0.1, 0.08, 0.13, 0.11, 0.08, 0.12, 0.1, 0.07, 0.08, 0.06, 0.09, 0.06, 0.18]
    if tired:
        durs = [d * 1.12 for d in durs]
    return V.speak_line(script, rng, pitch, durs, table=V.VM, scale=0.97, f0=f0, breath=0.3 if tired else 0.2,
                        jitter=0.015, shimmer=0.08, tilt=-9 if tired else -7, wander=0.25, pauses={11: 0.32})


# ==========================================================================
# outside: the road
# ==========================================================================
def _passby(rng, dur, t_mid, near=1.0, speed=1.0):
    """Gain / brightness / doppler curves of something passing on the road."""
    n = ns(dur)
    t = np.arange(n) / SR
    x = (t - t_mid) * 16.0 * speed  # metres along the road
    d = np.sqrt(x ** 2 + (9.0 / near) ** 2)
    g = (9.0 / near) / d
    v_r = -16.0 * speed * x / d  # radial speed (approaching > 0)
    dop = 343.0 / (343.0 - v_r)
    lp = 1800 + 9000 * g ** 1.2
    return g, dop, lp


def car_pass(rng, i=1):
    dur = 7.0
    n = ns(dur)
    t_mid = {1: 3.3, 2: 3.6, 3: 3.4}[i]
    g, dop, lp = _passby(rng, dur, t_mid, near=1.0 if i != 3 else 0.8, speed=1.0 if i != 3 else 0.85)
    spray = dsp.colored(n, rng, -0.5, lo=300, hi=11000) * (1 + 0.3 * dsp.smooth(n, rng, 18.0))  # tyres on wet asphalt
    spray = dsp.tv_filter(spray, lp, 0.7, "lp", block=256)
    tyre = dsp.tv_filter(rng.standard_normal(n), 700 * dop, 1.4, "bp", block=256)  # tread roar
    rpm = (1900 if i != 3 else 1300) * dop
    ph = np.cumsum(rpm / 60.0 * (2 if i != 3 else 3)) / SR
    eng = np.sin(TAU * ph) + 0.5 * np.sin(2 * TAU * ph + 0.4) + 0.25 * np.sin(3 * TAU * ph)
    eng = dsp.lowpass(eng, 900, 2) * (0.6 if i != 3 else 1.1)
    y = spray / np.std(spray) * 0.5 * g ** 1.8 + tyre / np.std(tyre) * 0.25 * g ** 1.6 + eng * 0.12 * g ** 1.4
    if i == 3:  # a truck: rattling body, air brake hiss far off
        y += dsp.bandpass(rng.standard_normal(n), 60, 300, 2) * g ** 1.5 * 0.4
    y = dsp.far(y, rng, rt60=1.4, wet=0.25, dry=1.0, lp=None, predelay=0.03, taps=[(0.09, 0.2, 2500)])
    y = dsp.pad_to(y, n)
    return dsp.fade(dsp.highpass(y, 35, 2), 0.3, 0.6)


def _bike_engine(rng, rpm, cyl=4, rough=0.08):
    n = len(rpm)
    fire = rpm / 60.0 * cyl / 2.0
    ph = np.cumsum(fire * (1 + rough * 0.03 * dsp.smooth(n, rng, 30.0, periodic=False))) / SR
    pulses = (0.5 + 0.5 * np.cos(TAU * ph)) ** 5
    y = pulses * (1 + rough * dsp.smooth(n, rng, 40.0, periodic=False))
    y = dsp.lowpass(y, 2500, 2)
    y += 0.25 * dsp.saw(fire * 2.0, n) * 0.3
    y += dsp.bandpass(rng.standard_normal(n), 1000, 5000, 2) * pulses * 0.15  # intake / exhaust rasp
    return y


def motorbike(rng, i=1):
    """A motorbike far away down the road, approaching, shifting up (8 s)."""
    dur = 8.2
    n = ns(dur)
    t = np.arange(n) / SR
    rpm = np.zeros(n)
    shifts = [(0.0, 5200), (1.6, 8800), (1.7, 6100), (3.4, 9200), (3.5, 6600), (5.4, 9600), (5.5, 7100), (dur, 9900)]
    for (ta, ra), (tb, rb) in zip(shifts[:-1], shifts[1:]):
        k = (t >= ta) & (t < tb)
        rpm[k] = ra + (rb - ra) * ((t[k] - ta) / (tb - ta)) ** 0.8
    rpm = dsp.lowpass0(rpm, 8.0)
    eng = _bike_engine(rng, rpm * 1.06)
    g = np.clip(t / dur, 0, 1) ** 1.7 * 0.85 + 0.15
    lp = 1200 + 4500 * np.clip(t / dur, 0, 1) ** 2
    y = dsp.tv_filter(eng / np.std(eng) * g, lp, 0.7, "lp", block=256)
    y += dsp.tv_filter(rng.standard_normal(n), lp * 0.8, 0.7, "lp", block=256) * g ** 2 * 0.06  # tyres
    y = dsp.far(y, rng, rt60=2.6, wet=0.9, dry=0.7, lp=None, predelay=0.06, taps=[(0.35, 0.3, 1800), (0.8, 0.15, 1200)])
    y = dsp.pad_to(y, n)
    return dsp.fade(dsp.highpass(y, 40, 2), 1.2, 0.08)


def crash(rng, i=1):
    """At the curve: tyres screaming, the impact, glass, the bike scraping along the asphalt - then nothing."""
    dur = 6.5
    n = ns(dur)
    y = np.zeros(n)
    sd = 1.35
    m = ns(sd)
    tt = np.arange(m) / SR
    f = 1150 * (1 + 0.15 * np.sin(TAU * 1.3 * tt)) * dsp.cents(120 * dsp.smooth(m, rng, 6.0, periodic=False))
    sq = sum(a * dsp.nb_noise(f * k, 40 * k, rng) for k, a in ((1, 1.0), (2, 0.55), (3, 0.3), (4, 0.15)))
    sq *= dsp.env_pts(m, [(0, 0), (0.08, 1.0), (sd - 0.1, 1.0), (sd, 0.6)])
    sq = sq + dsp.bandpass(rng.standard_normal(m), 1500, 6000, 2) * 0.4 * dsp.env_pts(m, [(0, 0), (0.1, 1), (sd, 0.8)])
    eng = _bike_engine(rng, np.linspace(9500, 11500, m))
    dsp.place(y, _nrm(sq) * 0.7 + _nrm(eng) * 0.3 * dsp.env_pts(m, [(0, 1), (sd, 0.6)]), 0.0)
    t0 = sd
    mi = ns(1.2)
    t2 = np.arange(mi) / SR
    boom = dsp.sat(np.sin(TAU * np.cumsum(35 + 70 * np.exp(-t2 / 0.05)) / SR) * dsp.env_ad(mi, 0.002, 0.22) * 2.5, 1.0)
    crunch = dsp.lowpass(rng.standard_normal(mi), 3500, 2) * dsp.env_ad(mi, 0.001, 0.08)
    dsp.place(y, boom * 1.0 + crunch * 0.9, t0)
    for k in range(25):  # metal and plastic breaking
        mods = ins.METAL if rng.random() < 0.5 else ins.PLASTIC
        dsp.place(y, _hit(rng, mods, vel=rng.uniform(0.2, 0.8), size=rng.uniform(0.7, 2.0), hard=0.9, dur=0.3),
                  t0 + rng.exponential(0.08))
    for k in range(35):  # glass (the visor, the headlamp)
        dsp.place(y, _hit(rng, SHARD, vel=rng.uniform(0.1, 0.5), size=rng.uniform(0.6, 1.3), hard=1.0, dur=0.1),
                  t0 + 0.02 + rng.exponential(0.15))
    d = 2.2  # the bike slides away on its side, scraping, slowing
    m = ns(d)
    tt = np.arange(m) / SR
    sp = dsp.bandpass(rng.standard_normal(m), 1200, 9000, 2) * (1 + 0.5 * np.abs(dsp.smooth(m, rng, 25.0, periodic=False)))
    sp += _squeal(rng, d, [(0, 2600), (d, 1800)], [(0, 0.5), (d, 0.1)], wob=80) * 0.4
    sp *= dsp.env_pts(m, [(0, 0), (0.05, 1.0), (d * 0.5, 0.6), (d, 0)]) ** 1.3
    dsp.place(y, sp * 0.35, t0 + 0.15)
    dsp.place(y, _hit(rng, ins.METAL, vel=0.5, size=2.2, hard=0.8, dur=0.8), t0 + 0.15 + d * 0.8)  # it hits the guardrail
    y = dsp.far(y, rng, rt60=2.2, wet=0.7, dry=0.9, lp=7000, predelay=0.04, taps=[(0.18, 0.3, 3000), (0.42, 0.15, 2000)])
    y = dsp.pad_to(y, n)
    return dsp.fade(dsp.highpass(y, 30, 2), 0.0, 0.8)


# ==========================================================================
# the restrooms, the kids' corner
# ==========================================================================
def toilet_flush(rng, i=1):
    dur = 5.5
    n = ns(dur)
    y = np.zeros(n)
    dsp.place(y, _hit(rng, ins.PLASTIC, vel=0.7, size=1.4, dur=0.2), 0.02)
    dsp.place(y, _click(rng, 0.4, 1800, CHROME, 1.0), 0.06)
    m = ns(3.0)
    tt = np.arange(m) / SR
    rush = dsp.colored(m, rng, -1.0, lo=150, hi=8000) * (1 + 0.4 * dsp.smooth(m, rng, 8.0, periodic=False))
    swirl = dsp.tv_filter(rng.standard_normal(m), 420 + 220 * np.sin(TAU * 1.6 * tt), 2.5, "bp")
    gurgle = ins.water_texture(m, rng, density=380, r_range=(2.0, 12.0), noise=0.3, splash=0.2)
    env = dsp.env_pts(m, [(0, 0), (0.15, 1.0), (1.4, 0.9), (2.3, 0.35), (3.0, 0.0)])
    body = (rush / np.std(rush) * 0.35 + swirl / np.std(swirl) * 0.25 + _nrm(gurgle) * 0.5) * env
    dsp.place(y, body, 0.12)
    m = ns(2.6)
    fill = dsp.tv_filter(rng.standard_normal(m), dsp.env_pts(m, [(0, 900), (2.6, 2100)]), 4.0, "bp")
    dsp.place(y, fill / np.std(fill) * 0.06 * dsp.env_pts(m, [(0, 0), (0.4, 1), (2.6, 0.5)]), 2.4)
    return dsp.fade(_near(y, rng)[:n], 0.0, 0.6)


def _knuckle(rng, modes, vel=1.0, size=1.0, lp=None):
    k = _hit(rng, modes, vel=vel, size=size * 0.8, hard=0.75, dur=0.35, noise=0.45)
    k = dsp.mix(k, _thud(rng, 150, 90, 0.025, 0.2, noise=0.6, lp=1000) * 0.5 * vel)
    return dsp.lowpass(k, lp, 2) if lp else k


def stall_knock(rng, i=1):
    pats = {1: [0.0, 0.42], 2: [0.0, 0.36, 0.72], 3: [0.0, 1.25]}
    y = np.zeros(ns(2.8))
    for k, t0 in enumerate(pats[i]):
        v = 1.0 if i != 3 or k else 0.7
        dsp.place(y, _knuckle(rng, LAMINATE, v, 1.0), 0.02 + t0)
    if i == 3:  # nails dragged down the inside of the door
        m = ns(0.7)
        sc = ins.friction_pulses(0.7, rng, np.linspace(220, 90, m), dsp.env_pts(m, [(0, 0), (0.05, 1), (0.7, 0)]), jitter=0.4)
        sc = dsp.resbank(sc, [(f, 10, a) for f, _, a in LAMINATE])
        dsp.place(y, _nrm(sc) * 0.25, 0.45)
    return _fin(_near(y, rng), 50)


def fridge_knock(rng, i=1):
    pats = {1: [0.0, 0.5, 1.0], 2: [0.0, 0.45, 1.6], 3: list(np.cumsum([0.0] + [0.17] * 7))}
    y = np.zeros(ns(3.2))
    for k, t0 in enumerate(pats[i]):
        v = 1.0 if i != 3 else 0.7 + 0.3 * rng.random()
        th = _thud(rng, 120, 70, 0.06, 0.4, noise=1.0, lp=700) * v  # through 10 cm of insulation
        th = dsp.mix(th, dsp.lowpass(_hit(rng, STEEL_PANEL, vel=0.6 * v, size=1.5, hard=0.45, dur=0.5), 1100, 2))
        dsp.place(y, th, 0.02 + t0)
    return _fin(_near(y, rng), 30)


def ball_bounce(rng, i=1):
    y = np.zeros(ns(3.4))
    t, per, v = 0.02, 0.48, 1.0
    while per > 0.03 and t < 2.6:
        m = ns(0.12)
        tt = np.arange(m) / SR
        b = np.sin(TAU * np.cumsum(440 * (1 + 0.3 * np.exp(-tt / 0.004))) / SR) * dsp.env_ad(m, 0.001, 0.02)
        b += dsp.lowpass(rng.standard_normal(m), 1500, 2) * dsp.env_ad(m, 0.0005, 0.006) * 0.8
        b += _thud(rng, 160, 120, 0.02, 0.12, noise=0.3)[:m] * 0.6
        dsp.place(y, b * v, t)
        t += per
        per *= 0.78
        v *= 0.8
    m = ns(0.8)
    roll = dsp.lowpass(rng.standard_normal(m), 600, 2) * dsp.env_pts(m, [(0, 0.5), (0.8, 0)]) * 0.12
    dsp.place(y, roll * (1 + 0.5 * np.sin(TAU * 7 * np.arange(m) / SR)), t)
    return _fin(_near(y, rng), 60)


def toy_squeak(rng, i=1):
    y = np.zeros(ns(1.1))
    for t0, (fa, fb), d, v in ((0.02, (1900, 2500), 0.22, 1.0), (0.42, (2300, 1700), 0.3, 0.8)):
        sq = _squeal(rng, d, [(0, fa), (d * 0.6, fb), (d, fb * 0.9)], [(0, 0), (0.02, 1), (d * 0.8, 0.8), (d, 0)],
                     wob=30, harm=(1.0, 0.5, 0.25, 0.12), bw=60)
        air = dsp.bandpass(rng.standard_normal(ns(d)), 800, 5000, 2) * dsp.env_pts(ns(d), [(0, 0), (0.03, 1), (d, 0)]) * 0.3
        dsp.place(y, (sq + air) * v, t0)
        dsp.place(y, _friction(rng, 0.12, [(0, 60), (0.12, 30)], [(0, 0), (0.02, 0.4), (0.12, 0)], [(420, 6, 1.0), (900, 8, 0.5)]) * 0.15, t0)
    return _fin(_near(y, rng), 200)
