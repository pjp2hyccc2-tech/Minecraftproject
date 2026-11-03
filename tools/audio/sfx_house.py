"""The apartment: doors, fixtures, the CRT, the intercom, the phone, the action cameras, the laptop, small items.

コーポ白樺 203 is a 1970s wooden apartment: a hollow steel front door with a chain, cheap hollow interior doors, paper
fusuma sliding in wooden grooves, a resin folding door to the bath, Panasonic-style wide rocker switches, a hanging
lamp with a pull cord, a breaker panel in the genkan, an old CRT, an intercom handset with a small speaker.
Positional sounds are mono and fairly dry (the acoustics engine adds the room); UI and the phone are stereo."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import tune as T
import voice as V
from dsp import SR, TAU, ns

STEEL_DOOR = [(96, 0.35, 1.0), (152, 0.3, 0.9), (231, 0.26, 0.8), (347, 0.22, 0.7), (520, 0.18, 0.55),
              (760, 0.14, 0.4), (1130, 0.1, 0.3), (1690, 0.07, 0.2), (2480, 0.05, 0.12)]
LATCH = [(1850, 0.06, 1.0), (2900, 0.05, 0.8), (4300, 0.035, 0.5), (6200, 0.02, 0.3), (980, 0.05, 0.4)]
CHAIN = [(3100, 0.12, 1.0), (4650, 0.09, 0.7), (6400, 0.06, 0.45), (8300, 0.04, 0.25)]
HOLLOW_DOOR = [(110, 0.12, 0.9), (190, 0.1, 1.0), (300, 0.08, 0.8), (450, 0.06, 0.6), (680, 0.05, 0.4), (1000, 0.04, 0.25)]
FUSUMA = [(140, 0.07, 1.0), (240, 0.06, 0.8), (390, 0.05, 0.6), (620, 0.04, 0.4), (980, 0.03, 0.25)]
PILLAR = [(260, 0.05, 1.0), (520, 0.04, 0.8), (890, 0.035, 0.6), (1450, 0.025, 0.4), (2300, 0.02, 0.2)]
RESIN = [(420, 0.05, 1.0), (760, 0.04, 0.8), (1250, 0.03, 0.6), (2050, 0.025, 0.4), (3300, 0.02, 0.25)]
SWITCH = [(2900, 0.012, 1.0), (4700, 0.009, 0.7), (6900, 0.006, 0.4), (1700, 0.015, 0.5)]
PLASTIC = [(1450, 0.03, 1.0), (2380, 0.025, 0.7), (3900, 0.02, 0.5), (5600, 0.012, 0.3)]
CERAMIC = [(1850, 0.12, 1.0), (2950, 0.09, 0.7), (4300, 0.07, 0.5), (5600, 0.05, 0.35), (7400, 0.035, 0.2)]
GLASS = [(2480, 0.5, 1.0), (4050, 0.35, 0.55), (6150, 0.22, 0.35), (8600, 0.12, 0.2)]
POT_LID = [(520, 0.9, 1.0), (1170, 0.7, 0.8), (1980, 0.55, 0.6), (2900, 0.4, 0.45), (4100, 0.3, 0.3)]


def _hit(rng, modes, vel=1.0, size=1.0, hard=0.7, dur=0.4, noise=0.3):
    return ins.modal_hit(rng, modes, size=size, hardness=hard, vel=vel, dur=dur, noise=noise)


def _jingle(rng, dur, shakes, modes=CHAIN):
    """Small metal links knocking together: shakes = [(t, intensity)]."""
    y = np.zeros(ns(dur))
    for t0, inten in shakes:
        t = t0
        for k in range(rng.integers(4, 9)):
            if t >= dur - 0.05:
                break
            v = inten * rng.uniform(0.3, 1.0) * (1 - 0.08 * k)
            K.place(y, _hit(rng, modes, vel=v, size=rng.uniform(0.85, 1.2), hard=1.0, dur=0.12, noise=0.3), t)
            t += rng.uniform(0.012, 0.05)
    return y


def _slide(dur, rng, rough=1.0, body=FUSUMA, judder=3, level=1.0):
    """A paper sliding door running in its wooden grooves: a rumbling wooden hiss with the panel drumming, small
    stick-slip judders."""
    n = ns(dur)
    e = K.env_shape(n, [(0, 0), (min(0.06, dur * 0.2), 1.0), (dur * 0.8, 0.85), (dur, 0)])
    speed = e * (1 + 0.2 * dsp.smooth(n, rng, 6.0, periodic=False))
    fr = dsp.colored(n, rng, -2.5, lo=80, hi=3200) * speed
    pan_ = dsp.resbank(fr, [(f, 4, a) for f, T, a in body])
    x = ins.friction_pulses(dur, rng, 90 + 160 * speed, speed * rough, jitter=0.5)
    grit = dsp.resbank(x, [(f * 2.2, 8, a) for f, T, a in body])
    y = K.rmsn(fr) * 0.5 + K.rmsn(pan_) * 0.35 + K.rmsn(grit) * 0.2
    for _ in range(judder):
        t0 = rng.uniform(0.1, max(0.11, dur - 0.1))
        K.place(y, _hit(rng, body, vel=rng.uniform(0.4, 0.9), hard=0.4, dur=0.12, noise=0.4) * 0.5, t0)
    return y * level


# ==========================================================================
# doors
# ==========================================================================
def front_open(rng, i=1):
    """The steel front door: the chain slid out of its track and dropped jangling, the thumb-turn's clunk, the lever
    handle, the old hinge groaning as the heavy door swings, the rain outside rushing in through the gap."""
    dur = 3.2
    y = np.zeros(ns(dur))
    sl = _slide(0.12, rng, body=CHAIN, judder=0, level=0.15)
    K.place(y, sl, 0.02)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.6, hard=1.0, dur=0.2)) * 0.35, 0.14)
    K.place(y, K.nrm(_jingle(rng, 1.0, [(0.0, 1.0), (0.18, 0.6), (0.42, 0.35), (0.7, 0.15)])) * 0.4, 0.16)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=1.0, size=1.3, hard=0.9, dur=0.3)) * 0.6, 0.9)        # thumb-turn bolt
    K.place(y, K.nrm(_hit(rng, STEEL_DOOR, vel=0.4, hard=0.5, dur=0.5, noise=0.2)) * 0.25, 0.905)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.8, size=1.1, hard=1.0, dur=0.2)) * 0.45, 1.35)      # lever down
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.5, size=0.9, hard=1.0, dur=0.2)) * 0.3, 1.43)       # latch out
    cr = ins.creak(1.1, rng, [(0, 25), (0.4, 55), (1.1, 30)], [(0, 0), (0.15, 0.8), (0.8, 1.0), (1.1, 0)],
                   modes=[(180, 9, 1.0), (330, 11, 0.8), (520, 12, 0.6), (840, 14, 0.4)], tonal=0.4)
    K.place(y, K.nrm(cr) * 0.45, 1.55)
    seal = dsp.colored(ns(0.3), rng, -1.0, lo=500, hi=6000) * K.env_shape(ns(0.3), [(0, 0), (0.03, 1.0), (0.3, 0)], 2.0)
    K.place(y, K.rmsn(seal) * 0.06, 1.55)
    m = ns(1.6)  # the outside pours in
    rain = K.rain_hiss(m, rng, bright=0.9) * K.env_shape(m, [(0, 0), (0.5, 1.0), (1.6, 0.6)])
    K.place(y, rain * 0.07, 1.6)
    return K.fin(K.rev(y, rng, 0.35, 0.08), 50)


def front_close(rng, i=1):
    """The steel door slams: a rush of air, the hollow steel booming in its frame, the latch, the chain swinging
    against the door, the handle rattling."""
    dur = 2.6
    y = np.zeros(ns(dur))
    m = ns(0.3)
    wh = dsp.bandpass(rng.standard_normal(m), 120, 1200, 2) * K.env_shape(m, [(0, 0), (0.25, 1.0), (0.3, 0)], 2.0)
    K.place(y, K.rmsn(wh) * 0.05, 0.0)
    t0 = 0.3
    boom = _hit(rng, STEEL_DOOR, vel=1.0, size=1.0, hard=0.75, dur=1.4, noise=0.35)
    K.place(y, K.nrm(boom), t0)
    m = ns(0.3)
    tt = np.arange(m) / SR
    thud = np.sin(TAU * np.cumsum(70 + 50 * np.exp(-tt / 0.015)) / SR) * dsp.env_ad(m, 0.002, 0.05)
    K.place(y, thud * 0.7, t0)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=1.0, size=1.1, hard=1.0, dur=0.25)) * 0.5, t0 + 0.012)
    K.place(y, K.nrm(_jingle(rng, 1.2, [(0.0, 1.0), (0.25, 0.7), (0.55, 0.4), (0.85, 0.2)])) * 0.35, t0 + 0.03)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.4, size=0.9, hard=1.0, dur=0.15)) * 0.2, t0 + 0.12)
    return K.fin(K.rev(y, rng, 0.4, 0.1), 40)


def interior_open(rng, i=1):
    """A cheap hollow interior door: the knob's loose spring, the latch, a short squeaky hinge, the door brushing
    the frame."""
    y = np.zeros(ns(1.5))
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.6, size=1.2, hard=0.9, dur=0.15)) * 0.4, 0.02)
    sp = ins.creak(0.12, rng, [(0, 200), (0.12, 500)], [(0, 0), (0.02, 0.6), (0.12, 0)], modes=[(2100, 15, 1.0), (3400, 18, 0.5)])
    K.place(y, K.nrm(sp) * 0.12, 0.03)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.5, size=0.9, hard=1.0, dur=0.15)) * 0.35, 0.18)
    K.place(y, K.nrm(_hit(rng, HOLLOW_DOOR, vel=0.4, hard=0.4, dur=0.3)) * 0.25, 0.19)
    cr = K.creak(rng, 0.55, pitch=1.6, rate=(40, 110), q=1.4, tonal=0.5)
    K.place(y, K.nrm(cr) * 0.35, 0.3)
    br = K.fabric(0.25, rng, K.env_shape(ns(0.25), [(0, 0), (0.1, 1.0), (0.25, 0)]), coarse=0.3)
    K.place(y, K.nrm(br) * 0.08, 0.7)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)


def interior_close(rng, i=1):
    y = np.zeros(ns(1.1))
    cr = K.creak(rng, 0.3, pitch=1.5, rate=(40, 90), q=1.3)
    K.place(y, K.nrm(cr) * 0.2, 0.0)
    K.place(y, K.nrm(_hit(rng, HOLLOW_DOOR, vel=1.0, hard=0.55, dur=0.6, noise=0.3)), 0.3)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.8, size=1.0, hard=1.0, dur=0.2)) * 0.5, 0.305)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 50)


def fusuma(rng, i=1):
    """The paper fusuma sliding in its wooden grooves: 1 opened briskly, 2 closed against the pillar, 3 opened slowly
    and carefully, catching."""
    if i == 1:
        d = 0.65
        y = np.zeros(ns(d + 0.5))
        K.place(y, _slide(d, rng, judder=2), 0.02)
        K.place(y, K.nrm(_hit(rng, FUSUMA, vel=0.3, hard=0.3, dur=0.2)) * 0.15, d)
    elif i == 2:
        d = 0.75
        y = np.zeros(ns(d + 0.6))
        K.place(y, _slide(d, rng, judder=2), 0.02)
        K.place(y, K.nrm(_hit(rng, PILLAR, vel=1.0, hard=0.6, dur=0.3, noise=0.3)) * 0.9, d + 0.01)   # ton
        K.place(y, K.nrm(_hit(rng, FUSUMA, vel=0.6, hard=0.4, dur=0.3)) * 0.35, d + 0.012)
    else:
        d = 1.5
        y = np.zeros(ns(d + 0.5))
        sl = _slide(d, rng, rough=1.6, judder=6, level=0.7)
        sl *= 0.6 + 0.4 * K.norm01(dsp.smooth(len(sl), rng, 3.0, periodic=False))
        K.place(y, sl, 0.02)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 60)


def fusuma_crack(rng, i=1):
    """The closet's fusuma eased open by a few millimetres: a tiny dry squeak of wood on wood, one catch."""
    y = np.zeros(ns(0.6))
    d = 0.26
    sl = _slide(d, rng, rough=2.0, judder=1, level=0.5)
    K.place(y, sl, 0.02)
    sq = ins.creak(0.1, rng, [(0, 120), (0.1, 260)], [(0, 0), (0.02, 0.7), (0.1, 0)], modes=[(1450, 12, 1.0), (2600, 14, 0.5)])
    K.place(y, K.nrm(sq) * 0.25, 0.1)
    return K.fin(K.rev(y, rng, 0.1, 0.03), 80)


def bath_fold(rng, i=1):
    """The bathroom's folding door: resin panels folding in their aluminium frame with a hollow clatter, the roller
    rumbling and squeaking in the top rail, the magnet catch."""
    y = np.zeros(ns(1.5))
    d = 0.55
    n = ns(d)
    roll = dsp.colored(n, rng, -2.0, lo=200, hi=3000) * K.env_shape(n, [(0, 0), (0.05, 1.0), (d, 0)])
    roll *= 1 + 0.6 * np.abs(np.sin(TAU * 23 * np.arange(n) / SR))
    K.place(y, K.rmsn(roll) * 0.12, 0.05)
    sq = ins.creak(0.3, rng, [(0, 300), (0.3, 700)], [(0, 0), (0.05, 0.7), (0.3, 0)], modes=[(2800, 20, 1.0), (4600, 22, 0.4)], tonal=0.4)
    K.place(y, K.nrm(sq) * 0.12, 0.15)
    for k, t0 in enumerate((0.04, 0.3, 0.52)):
        K.place(y, K.nrm(_hit(rng, RESIN, vel=rng.uniform(0.6, 1.0), size=rng.uniform(0.9, 1.15), hard=0.7, dur=0.25, noise=0.4)) * 0.55, t0)
    K.place(y, K.nrm(_hit(rng, LATCH, vel=0.6, size=0.8, hard=1.0, dur=0.12)) * 0.4, 0.64)
    return K.fin(K.rev(y, rng, 0.3, 0.08), 80)


def locked(rng, i=1):
    """The lever handle of a locked steel door rattled: the latch knocking against the strike plate."""
    y = np.zeros(ns(1.0))
    t = 0.02
    for k in range(3):
        K.place(y, K.nrm(_hit(rng, LATCH, vel=rng.uniform(0.7, 1.0), size=rng.uniform(0.95, 1.1), hard=1.0, dur=0.15)) * 0.6, t)
        K.place(y, K.nrm(_hit(rng, STEEL_DOOR, vel=0.5, hard=0.6, dur=0.3, noise=0.2)) * 0.25, t + 0.004)
        K.place(y, K.nrm(_hit(rng, LATCH, vel=0.4, size=0.9, hard=1.0, dur=0.1)) * 0.3, t + rng.uniform(0.05, 0.08))
        t += rng.uniform(0.17, 0.24)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 80)


# ==========================================================================
# switches, lamp, breaker
# ==========================================================================
def switch(rng, i=1):
    """A wide rocker switch: the rocker tips, the contacts snap."""
    y = np.zeros(ns(0.25))
    K.place(y, K.nrm(_hit(rng, PLASTIC, vel=0.4, size=1.1, hard=0.9, dur=0.06, noise=0.4)) * 0.3, 0.01)
    K.place(y, K.nrm(_hit(rng, SWITCH, vel=1.0, size=[1.0, 1.07][i - 1], hard=1.0, dur=0.08, noise=0.5)), 0.016 + 0.003 * i)
    return K.fin(K.rev(y, rng, 0.12, 0.04), 200)


def pull_cord(rng, i=1):
    """The hanging lamp's pull cord: the string tugged, the ratchet switch inside clacking, the cord's bob swinging
    against the shade."""
    y = np.zeros(ns(1.0))
    m = ns(0.12)
    tug = dsp.bandpass(rng.standard_normal(m), 1500, 7000, 2) * K.env_shape(m, [(0, 0), (0.06, 1.0), (0.12, 0)])
    K.place(y, K.rmsn(tug) * 0.06, 0.01)
    K.place(y, K.nrm(_hit(rng, [(2400, 0.02, 1.0), (3900, 0.015, 0.8), (5600, 0.01, 0.5), (1300, 0.03, 0.4)], vel=1.0,
                          hard=1.0, dur=0.08, noise=0.4)), 0.11)
    for t0, v in ((0.35, 0.3), (0.6, 0.15)):
        K.place(y, K.nrm(_hit(rng, PLASTIC, vel=v, size=0.8, hard=0.8, dur=0.06)) * v, t0)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 150)


def _breaker_snap(rng):
    y = _hit(rng, [(1600, 0.04, 1.0), (2700, 0.03, 0.8), (4100, 0.02, 0.6), (6300, 0.015, 0.3), (800, 0.05, 0.5)],
             vel=1.0, size=1.0, hard=1.0, dur=0.2, noise=0.6)
    box = _hit(rng, [(420, 0.08, 1.0), (790, 0.06, 0.6), (1300, 0.04, 0.3)], vel=0.6, hard=0.6, dur=0.25, noise=0.2)
    out = np.zeros(ns(0.3))
    K.place(out, K.nrm(y), 0.0)
    K.place(out, K.nrm(box) * 0.4, 0.001)
    return out


def breaker_off(rng, i=1):
    """The breaker snapped down: the lever's hard snap in its steel box, and the apartment dies - the fridge's hum
    sinking away, a tick somewhere, then nothing."""
    y = np.zeros(ns(2.0))
    K.place(y, _breaker_snap(rng), 0.02)
    m = ns(1.2)
    tt = np.arange(m) / SR
    f = 100.0 * np.exp(-tt / 0.5)
    hum = (np.sin(TAU * np.cumsum(f) / SR) + 0.4 * np.sin(TAU * np.cumsum(2 * f) / SR)) * np.exp(-tt / 0.35)
    K.place(y, dsp.lowpass(hum, 400, 2) * 0.25, 0.03)
    K.place(y, K.nrm(_hit(rng, [(2100, 0.02, 1.0), (3600, 0.015, 0.5)], vel=0.2, hard=1.0, dur=0.05)) * 0.12, 0.6)
    return K.fin(K.rev(y, rng, 0.25, 0.06), 40)


def breaker_on(rng, i=1):
    """The breaker snapped up: the fridge's compressor thumps back on, a fluorescent ring's glow starter pings and the
    tube catches with a hum."""
    y = np.zeros(ns(2.4))
    K.place(y, _breaker_snap(rng), 0.02)
    m = ns(1.6)
    tt = np.arange(m) / SR
    surge = np.sin(TAU * np.cumsum(60 + 40 * np.exp(-tt / 0.08)) / SR) * dsp.env_ad(m, 0.01, 0.2)
    hum = (np.sin(TAU * 100 * tt) + 0.4 * np.sin(TAU * 200 * tt)) * K.env_shape(m, [(0, 0), (0.2, 1.0), (1.6, 0.8)])
    K.place(y, dsp.lowpass(surge * 0.4 + hum * 0.08, 500, 2), 0.1)
    for t0 in (0.45, 0.9):
        p = ins.bell_partials(2900, 0.3, rng, [1.0, 2.3], [1.0, 0.4], [0.15, 0.08])
        K.place(y, p * 0.12, t0)
    m = ns(1.2)
    tt = np.arange(m) / SR
    tube = sum(a * np.sin(TAU * f * tt) for f, a in ((100, 1.0), (200, 0.5), (300, 0.3)))
    tube = tube * K.env_shape(m, [(0, 0), (0.05, 1.0), (1.2, 0.7)]) + dsp.bandpass(rng.standard_normal(m), 3000, 8000, 2) * 0.05
    K.place(y, tube * 0.06, 1.1)
    return K.fin(K.rev(y, rng, 0.25, 0.06), 40)


# ==========================================================================
# the CRT
# ==========================================================================
def _static_burst(rng, dur, level_pts):
    n = ns(dur)
    s = dsp.colored(n, rng, 0.0, lo=80, hi=16000)
    s = dsp.lowpass(s, 6500, 1) * dsp.env_pts(n, level_pts)
    return K.tv_speaker(s)


def tv_on(rng, i=1):
    """The CRT switched on: the heavy push button, the degaussing coil's booming hum with the shadow mask ringing,
    static charge crackling over the glass, the line whine chirping up to 15.7 kHz, the snow fading in."""
    dur = 3.4
    n = ns(dur)
    y = np.zeros(n)
    K.place(y, K.nrm(_hit(rng, [(900, 0.05, 1.0), (1700, 0.04, 0.7), (2900, 0.03, 0.5), (420, 0.06, 0.6)], vel=1.0,
                          hard=0.8, dur=0.2, noise=0.5)) * 0.6, 0.02)
    t0 = 0.12
    m = ns(1.4)
    tt = np.arange(m) / SR
    deg_env = np.exp(-tt / 0.35) * np.minimum(1, tt / 0.01)
    deg = sum(a * np.sin(TAU * f * tt + rng.random() * 6) for f, a in ((50, 1.0), (100, 0.8), (150, 0.5), (200, 0.35), (250, 0.2), (350, 0.12)))
    deg = np.tanh(2.0 * deg) * deg_env
    mask = ins.bell_partials(212, 1.4, rng, [1.0, 1.9, 2.75, 3.6], [1.0, 0.5, 0.3, 0.2], [0.8, 0.6, 0.4, 0.3], beat=(0.5, 2.5))
    K.place(y, deg * 0.9 + mask * 0.18, t0)
    crk = ins.crackle(1.2, rng, 70, env=K.env_shape(ns(1.2), [(0, 1.0), (1.2, 0.1)]), hp=2500, decay=0.0004)
    K.place(y, K.nrm(crk) * 0.35, t0 + 0.05)
    m = ns(1.0)  # the line oscillator chirping up and locking
    tt = np.arange(m) / SR
    f = 15734.0 - 9000.0 * np.exp(-tt / 0.09)
    wh = np.sin(TAU * np.cumsum(f) / SR) * K.env_shape(m, [(0, 0), (0.05, 1.0), (1.0, 0.7)])
    K.place(y, wh * 0.06, t0 + 0.3)
    K.place(y, np.sin(TAU * 15734.0 * np.arange(ns(dur - 1.42)) / SR) * 0.042, t0 + 1.3)
    st = _static_burst(rng, dur - 0.8, [(0, 0), (0.5, 0.15), (1.2, 0.55), (dur - 0.8, 0.5)])
    K.place(y, K.rmsn(st) * 0.13, 0.8)
    y = dsp.highpass(y, 35, 2)
    return K.fin(y, 35)


def tv_channel(rng, i=1):
    """The rotary channel selector clunks round one detent: a burst of brighter static, the sync rolling and catching."""
    y = np.zeros(ns(1.3))
    K.place(y, K.nrm(_hit(rng, [(1300, 0.04, 1.0), (2300, 0.03, 0.8), (3700, 0.02, 0.5), (650, 0.05, 0.5)], vel=1.0,
                          hard=0.95, dur=0.18, noise=0.5)), 0.02)
    K.place(y, K.nrm(_hit(rng, PLASTIC, vel=0.5, size=1.2, hard=0.9, dur=0.1)) * 0.3, 0.06)
    st = _static_burst(rng, 1.1, [(0, 0), (0.02, 1.0), (0.3, 0.7), (0.6, 0.3), (1.1, 0)])
    roll = np.sin(TAU * 59.94 * np.arange(len(st)) / SR)
    st = st * (1 + 0.5 * np.sign(roll) * K.env_shape(len(st), [(0, 0), (0.2, 1.0), (0.5, 0)]))
    K.place(y, K.rmsn(st) * 0.22, 0.05)
    return K.fin(y, 60)


def tv_voice(rng, i=1):
    """Voices out of the TV through the snow: 1 a newsreader drifting in and out, 2 a woman, slowed:
    「……そこにいるの」, 3 a child's 「もういいかい」 and a giggle."""
    if i == 1:
        v = V.speak("ho n ji tsu wa , a me no yo ru de su , go chu - i ku da sa i", rng, table=V.VM, scale=0.98, f0=110,
                    rate=1.0, accent=2.2, declination=2.0, breath=0.2, tilt=-9.0)
    elif i == 2:
        v, _ = V.speak_line("so ko ni , i ru no", rng, [3, 4, 3.5, (2, 1), 0, (1, -2)], [0.16, 0.14, 0.16, 0.12, 0.13, 0.5],
                            table=V.VF, scale=1.03, f0=205, breath=0.45, tilt=-10, jitter=0.02)
        v = dsp.varispeed(v, 0.8)
    else:
        v, _ = V.speak_line("mo - i i ka i -", rng, [4, 4, 4, 1, 4, (1, 0.5)], [0.2, 0.14, 0.18, 0.2, 0.16, 0.36],
                            table=V.VF, scale=1.22, f0=340, vibrato=10.0, breath=0.4)
        g = V.laugh(rng, n_syl=4, f0=420, decline=0.03, gap=(0.04, 0.06), syl=(0.06, 0.08), vowel="i", table=V.VF,
                    scale=1.24, breath=1.6, tilt=-10)
        v = np.concatenate([v, np.zeros(ns(0.15)), g * np.std(v) / (np.std(g) + 1e-9) * 0.7])
    v = dsp.bandpass(K.nrm(v), 250, 3500, 3)
    n = len(v) + ns(0.8)
    x = np.linspace(0, 1, len(v))
    fade = np.clip(np.sin(np.pi * x) * 1.5, 0, 1) * (0.6 + 0.4 * K.norm01(dsp.smooth(len(v), rng, 5.0, periodic=False)))
    y = np.zeros(n)
    K.place(y, v * fade, 0.3)
    st = dsp.colored(n, rng, 0.0, lo=80, hi=16000)
    st = dsp.lowpass(st, 6000, 1) * K.env_shape(n, [(0, 0), (0.1, 1.0), (n / SR - 0.2, 1.0), (n / SR, 0)])
    y = K.rmsn(y) * 0.6 + K.rmsn(st) * 0.32
    return K.fin(K.tv_speaker(y, drive=1.2), 120)


# ==========================================================================
# intercom, knocking
# ==========================================================================
def intercom(rng, i=1):
    """The apartment's door chime on the handset by the genkan: a relay click, then 'pin-pon' - an electronic struck
    bar falling a fourth (B5, F#5, both a little flat, the old speaker warbling) - original."""
    dur = 2.6
    y = np.zeros(ns(dur))
    K.place(y, K.nrm(_hit(rng, [(2200, 0.01, 1.0), (3800, 0.008, 0.5)], vel=0.3, hard=1.0, dur=0.04)) * 0.15, 0.0)
    pin = ins.chime_bar(dsp.hz("B5", -22), 0.9, rng, vel=1.0, bright=0.6, decay=0.5)
    pon = ins.chime_bar(dsp.hz("F#5", -30), 2.2, rng, vel=1.0, bright=0.55, decay=1.6)
    K.place(y, pin, 0.03)
    K.place(y, pon * 0.95, 0.42)
    n = len(y)
    wob = 1 + 0.002 * np.sin(TAU * 4.3 * np.arange(n) / SR)
    y = dsp.varispeed(y, wob)
    y = K.intercom_speaker(dsp.pad_to(y, n))
    return K.fin(K.rev(y, rng, 0.3, 0.08), 200)


def knock_door(rng, i=1):
    """Knuckles on the hollow steel front door: 1 two knocks, 2 three slow knocks, 3 a fist pounding."""
    if i == 1:
        pat, vel, fist = [0.0, 0.2], [1.0, 0.85], False
    elif i == 2:
        pat, vel, fist = [0.0, 0.55, 1.1], [0.85, 0.85, 1.0], False
    else:
        pat, vel, fist = [0.0, 0.17, 0.33, 0.5, 0.66], [1.0, 0.9, 1.0, 0.95, 1.0], True
    y = np.zeros(ns(pat[-1] + 1.4))
    for t0, v in zip(pat, vel):
        b = _hit(rng, STEEL_DOOR, vel=v, size=1.0, hard=0.5 if fist else 0.85, dur=1.0, noise=0.3)
        K.place(y, K.nrm(b) * v, t0)
        kn = K.burst(rng, 0.01, 600, 5000, 0.0003, 0.0015) if not fist else K.burst(rng, 0.03, 100, 1500, 0.001, 0.006)
        K.place(y, K.nrm(kn) * 0.5 * v, t0)
    return K.fin(K.rev(y, rng, 0.3, 0.07), 40)


def knock_closet(rng, i=1):
    """Knocking on the closet's fusuma from outside - right in front of you: the taut paper thumps like a drum over
    its lattice; 1 two soft knocks, 2 three (the last on the frame), 3 one slow knock and a scratch down the paper."""
    def paper(v):
        m = ns(0.25)
        tt = np.arange(m) / SR
        th = np.sin(TAU * np.cumsum(300 + 120 * np.exp(-tt / 0.006)) / SR) * dsp.env_ad(m, 0.0008, 0.03)
        buzz = dsp.bandpass(rng.standard_normal(m), 800, 6000, 2) * dsp.env_ad(m, 0.0005, 0.008)
        lat = _hit(rng, FUSUMA, vel=0.6, hard=0.4, dur=0.2, noise=0.2)
        out = th * 0.8 + K.rmsn(buzz) * 0.15
        out[:len(lat)] += K.nrm(lat)[:m] * 0.35
        return out * v
    if i == 1:
        y = np.zeros(ns(1.0))
        K.place(y, paper(0.9), 0.02)
        K.place(y, paper(0.75), 0.3)
    elif i == 2:
        y = np.zeros(ns(1.5))
        K.place(y, paper(0.8), 0.02)
        K.place(y, paper(0.8), 0.33)
        K.place(y, K.nrm(_hit(rng, PILLAR, vel=1.0, hard=0.8, dur=0.3, noise=0.3)) * 0.9, 0.66)
    else:
        y = np.zeros(ns(2.6))
        K.place(y, paper(1.0), 0.02)
        d = 1.3
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (0.1, 1.0), (d * 0.8, 0.7), (d, 0)])
        x = ins.friction_pulses(d, rng, 140 + 80 * dsp.smooth(m, rng, 2.0, periodic=False), e, jitter=0.4)
        sc = dsp.resbank(x, [(1800, 5, 1.0), (3200, 6, 0.7), (5200, 6, 0.4)]) + dsp.resbank(x, [(f, 3, a) for f, T, a in FUSUMA])
        K.place(y, K.nrm(sc) * 0.5, 0.9)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 60)


# ==========================================================================
# things in the kitchen, the house
# ==========================================================================
def thing_fall(rng, i=1):
    """Something falls in the kitchen: 1 a plastic bottle bouncing and rolling, 2 a pot lid spinning down on the
    floor, 3 the ladle and chopsticks clattering off the rack."""
    if i == 1:
        y = np.zeros(ns(2.4))
        for t0, v in ((0.02, 1.0), (0.31, 0.5), (0.48, 0.25)):
            K.place(y, K.nrm(_hit(rng, [(310, 0.05, 1.0), (690, 0.04, 0.7), (1240, 0.03, 0.5), (2100, 0.02, 0.3)], vel=v,
                                  hard=0.6, dur=0.3, noise=0.4)) * v, t0)
        m = ns(1.6)
        roll = dsp.colored(m, rng, -2.0, lo=150, hi=2500) * K.env_shape(m, [(0, 0), (0.1, 1.0), (1.6, 0)], 1.4)
        roll *= 1 + 0.5 * np.abs(np.sin(TAU * np.cumsum(np.linspace(6, 2, m)) / SR))
        K.place(y, K.rmsn(roll) * 0.08, 0.6)
    elif i == 2:
        y = np.zeros(ns(3.0))
        K.place(y, K.nrm(_hit(rng, POT_LID, vel=1.0, hard=0.9, dur=1.2, noise=0.2)), 0.02)
        t, gap, v = 0.4, 0.3, 0.8
        while gap > 0.012 and t < 2.7:  # the wobble speeding up as it settles
            K.place(y, K.nrm(_hit(rng, POT_LID, vel=v, size=rng.uniform(0.97, 1.03), hard=0.8, dur=0.5, noise=0.2)) * v, t)
            t += gap
            gap *= 0.82
            v *= 0.93
    else:
        y = np.zeros(ns(1.6))
        K.place(y, K.nrm(_hit(rng, [(720, 0.25, 1.0), (1650, 0.2, 0.8), (2900, 0.15, 0.6), (4400, 0.1, 0.4)], vel=1.0,
                              hard=0.95, dur=0.8, noise=0.3)), 0.02)                 # the ladle
        for k in range(5):  # chopsticks
            K.place(y, K.nrm(_hit(rng, [(1900, 0.03, 1.0), (3400, 0.02, 0.6), (5200, 0.015, 0.3)], vel=rng.uniform(0.4, 0.9),
                                  hard=1.0, dur=0.08, noise=0.4)) * 0.4, 0.05 + rng.uniform(0, 0.4))
    return K.fin(K.rev(y, rng, 0.3, 0.07), 60)


def dish_break(rng, i=1):
    """A plate slides off the counter and shatters on the floor: the crack, shards scattering and spinning."""
    dur = 2.0
    y = np.zeros(ns(dur))
    K.place(y, K.nrm(_hit(rng, CERAMIC, vel=1.0, hard=1.0, dur=0.6, noise=0.6)), 0.02)
    crack = K.burst(rng, 0.05, 900, 12000, 0.0002, 0.008)
    K.place(y, K.nrm(crack) * 0.9, 0.02)
    m = ns(0.3)
    tt = np.arange(m) / SR
    K.place(y, np.sin(TAU * np.cumsum(140 + 80 * np.exp(-tt / 0.01)) / SR) * dsp.env_ad(m, 0.001, 0.03) * 0.5, 0.02)
    t = 0.04
    for k in range(38):  # shards
        v = rng.uniform(0.2, 0.9) * np.exp(-(t - 0.04) / 0.45)
        K.place(y, _hit(rng, CERAMIC, vel=v, size=rng.uniform(0.35, 0.9), hard=1.0, dur=0.15, noise=0.5), t)
        t += rng.exponential(0.025)
    for t0 in (0.5, 0.75):  # a piece spinning
        for k in range(6):
            K.place(y, _hit(rng, CERAMIC, vel=0.15 * (1 - k / 6), size=0.5, hard=1.0, dur=0.08), t0 + k * 0.04 * (1 - 0.1 * k))
    return K.fin(K.rev(y, rng, 0.35, 0.08), 80)


def lamp_swing(rng, i=1):
    """The hanging lamp swaying by itself: its cord creaking at the ceiling hook at each end of the swing, the shade
    rattling faintly."""
    y = np.zeros(ns(3.6))
    for k, t0 in enumerate((0.05, 1.2, 2.35)):
        c = K.creak(rng, 0.45, pitch=0.9 + 0.05 * k, rate=(30, 70), q=1.5, tonal=0.3)
        K.place(y, K.nrm(c) * (1.0 - 0.2 * k), t0)
        K.place(y, K.nrm(_hit(rng, PLASTIC, vel=0.2, size=1.3, hard=0.6, dur=0.08)) * 0.12, t0 + 0.3)
    return K.fin(K.rev(y, rng, 0.25, 0.06), 80)


def water_slosh(rng, i=1):
    """Water moving in the full tub when nobody is there: 1 a gentle lapping, 2 something turning over in it, 3 a slow
    drag through the water and a drip."""
    if i == 1:
        y = K.slosh(2.2, rng, [(0.05, 0.6), (0.7, 0.45), (1.3, 0.3)], bright=0.5)
    elif i == 2:
        y = K.slosh(3.0, rng, [(0.05, 1.0), (0.45, 0.9), (1.0, 0.6), (1.7, 0.35)], bright=0.7)
        g = K.bubbles(1.5, rng, 120, (1.0, 6.0), env=K.env_shape(ns(1.5), [(0, 0), (0.1, 1.0), (1.5, 0)]))
        y[:len(g)] += K.nrm(g) * np.max(np.abs(y)) * 0.3
        for t0 in (1.4, 2.0, 2.6):
            K.place(y, K.drip(rng, rng.uniform(600, 900), 0.25 * np.max(np.abs(y))), t0)
    else:
        n = ns(3.0)
        e = K.env_shape(n, [(0, 0), (0.6, 1.0), (1.8, 0.8), (2.4, 0), (3.0, 0)])
        drag = dsp.colored(n, rng, -3.0, lo=150, hi=2500) * e
        drag = dsp.peq(drag, 380, 1.5, 6.0)
        y = K.rmsn(drag) * 0.1 + K.nrm(K.bubbles(3.0, rng, 60, (1.5, 7.0), env=e)) * 0.25
        K.place(y, K.drip(rng, 760, 0.3), 2.6)
    return K.fin(K.rev(y, rng, 0.7, 0.1, hf=0.75), 60)


def washing_start(rng, i=1):
    """The old washing machine starts by itself: the timer dial ticks over, a relay clunks, the inlet valve hisses
    open and water rushes into the tub, the motor turns the pulsator once."""
    dur = 3.6
    y = np.zeros(ns(dur))
    for k in range(4):
        K.place(y, K.nrm(_hit(rng, PLASTIC, vel=0.5, size=1.1, hard=1.0, dur=0.05)) * 0.25, 0.02 + k * 0.05)
    K.place(y, K.nrm(_hit(rng, [(320, 0.06, 1.0), (760, 0.04, 0.6), (1400, 0.03, 0.4)], vel=1.0, hard=0.7, dur=0.25)) * 0.6, 0.35)
    m = ns(dur - 0.5)
    e = K.env_shape(m, [(0, 0), (0.05, 1.0), (dur - 0.5, 0.85)])
    valve = dsp.colored(m, rng, -1.0, lo=400, hi=8000) * e
    rush = K.bubbles(dur - 0.5, rng, 500, (1.0, 6.0), env=e)
    K.place(y, K.rmsn(valve) * 0.08 + K.nrm(rush) * 0.3, 0.5)
    m = ns(1.6)
    tt = np.arange(m) / SR
    drv = K.env_shape(m, [(0, 0), (0.4, 1.0), (1.1, 1.0), (1.6, 0)])
    f = 48 + 26 * drv
    ph = np.cumsum(f) / SR
    motor = sum(a * np.sin(TAU * k * ph) for k, a in ((1, 0.6), (2, 1.0), (3, 0.5), (4, 0.4))) * drv
    K.place(y, motor * 0.12, 1.6)
    K.place(y, K.slosh(1.0, rng, [(0.0, 0.8)], bright=0.4) * 0.4, 2.7)
    return K.fin(K.rev(y, rng, 0.3, 0.06), 40)


def creak(rng, i=1):
    """The wooden building settling at night (家鳴り): 1 a long low floorboard creak, 2 a sharp dry crack of timber,
    3 a slow creak in the ceiling, 4 a board taking someone's weight, a tick."""
    if i == 1:
        y = K.creak(rng, 1.2, pitch=0.7, rate=(25, 60), q=1.3, tonal=0.2)
    elif i == 2:
        y = np.zeros(ns(0.8))
        K.place(y, K.nrm(ins.twig_snap(rng, thick=0.6, splinter=0.3)) * 0.8, 0.02)
        K.place(y, K.nrm(_hit(rng, [(420, 0.08, 1.0), (880, 0.06, 0.7), (1500, 0.04, 0.4)], vel=1.0, hard=0.9, dur=0.4)) * 0.6, 0.022)
    elif i == 3:
        y = K.creak(rng, 1.6, pitch=1.15, rate=(18, 45), q=1.5, tonal=0.5)
        y = dsp.lowpass(y, 2500, 2)
    else:
        y = np.zeros(ns(1.2))
        K.place(y, K.nrm(K.creak(rng, 0.5, pitch=0.85, rate=(40, 90), q=1.2)) * 0.8, 0.02)
        K.place(y, K.nrm(_hit(rng, ins.WOOD_FLOOR, vel=0.6, hard=0.6, dur=0.3)) * 0.4, 0.55)
    return K.fin(K.rev(y, rng, 0.35, 0.08), 50)


def footsteps_ceiling(rng, i=1):
    """Footsteps above the ceiling of a top-floor flat - where nobody can be: muffled thuds through the boards, the
    joists creaking; 1 slow and heavy, stopping; 2 something small running across."""
    if i == 1:
        times = [0.05, 0.75, 1.42, 2.15, 2.8]
        weight, f0 = 1.0, 70.0
    else:
        times = list(0.05 + np.arange(9) * 0.16 + rng.uniform(-0.01, 0.01, 9))
        weight, f0 = 0.55, 110.0
    y = np.zeros(ns(times[-1] + 1.2))
    for k, t0 in enumerate(times):
        m = ns(0.35)
        tt = np.arange(m) / SR
        th = np.sin(TAU * np.cumsum(f0 * (1 + 0.6 * np.exp(-tt / 0.012))) / SR) * dsp.env_ad(m, 0.004, 0.05)
        th += dsp.lowpass(rng.standard_normal(m), 300, 2) * dsp.env_ad(m, 0.003, 0.03) * 0.6
        K.place(y, th * weight * rng.uniform(0.8, 1.0), t0)
        if i == 1 and k % 2 == 1:
            K.place(y, K.nrm(K.creak(rng, 0.4, pitch=0.8, rate=(30, 60))) * 0.12, t0 + 0.05)
    y = dsp.lowpass(y, 450, 2)
    cav = dsp.resbank(y, [(95, 5, 1.0), (180, 6, 0.5)])  # the ceiling cavity booming
    y = K.nrm(y) + K.nrm(cav) * 0.3
    return K.fin(K.rev(y, rng, 0.4, 0.12, hf=0.3), 30)


# ==========================================================================
# the phone
# ==========================================================================
def phone_vibrate(rng, i=1):
    """The phone vibrating on the wooden desk: the motor's buzz and the case rattling against the wood, twice."""
    dur = 1.6
    n = ns(dur)
    t = np.arange(n) / SR
    gate = np.zeros(n)
    for a, b in ((0.02, 0.5), (0.75, 1.23)):
        gate[ns(a):ns(b)] = 1.0
    gate = dsp.lowpass0(gate, 60.0)
    f = 178.0 * (1 + 0.02 * dsp.smooth(n, rng, 3.0, periodic=False))
    ph = np.cumsum(f) / SR
    motor = np.sin(TAU * ph) + 0.5 * np.sin(2 * TAU * ph) + 0.25 * np.sin(3 * TAU * ph)
    rattle = np.clip(np.sin(TAU * ph) * 3.0, -1, 1) * dsp.bandpass(rng.standard_normal(n), 800, 6000, 2)
    desk = dsp.resbank(motor, [(f_ * 2.5, 6, a) for f_, T_, a in ins.WOOD_FLOOR[:5]])
    y = (K.nrm(motor) * 0.5 + K.rmsn(rattle) * 0.25 + K.nrm(desk) * 0.4) * gate
    return K.fin(y, 60)


RINGTONE = ["E6", "G6", "A6", "C7", "A6", "G6", "A6", None, "E6", "G6", "A6", "C7", "D7", "C7", "A6", None]


def phone_ring_own(rng, i=1):
    """Your ringtone (original: a bright marimba phrase) - wrong: the second time through the notes go sour, the
    tempo drags and the last ones sink; through the phone's small speaker.  7 s loop."""
    L = 7.0
    n = ns(L)
    y = np.zeros(n)
    step = 0.155
    t = 0.03  # the first note just after the seam
    for k, nm in enumerate(RINGTONE):
        if nm:
            K.place(y, ins.marimba(dsp.hz(nm), rng, vel=0.9, bright=0.7, hard=0.7), t, L=L)
        t += step
    t += 0.55
    for k, nm in enumerate(RINGTONE):  # wrong
        frac = k / len(RINGTONE)
        if nm and k not in (5, 13):
            c = -25 * frac - (90 if k >= 12 else 0) * (k - 11) / 4 + rng.uniform(-15, 15)
            K.place(y, ins.marimba(dsp.hz(nm, c), rng, vel=0.85, bright=0.7, hard=0.7), t, L=L)
            if k == 10:  # a note that sticks, repeated
                K.place(y, ins.marimba(dsp.hz(nm, c - 20), rng, vel=0.6, bright=0.7, hard=0.7), t + step * 0.5, L=L)
        t += step * (1 + 0.6 * frac ** 2)
    y = K.phone_speaker(y)
    st = np.vstack([y, np.roll(y, ns(0.0004))])
    return dsp.dc_block(st, circular=True)


def phone_notify(rng, i=1):
    """A message arrives: a short two-note chime (original), small speaker."""
    y = np.zeros(ns(0.9))
    K.place(y, ins.marimba(dsp.hz("D6"), rng, vel=0.8, bright=0.6, hard=0.6), 0.01)
    K.place(y, ins.marimba(dsp.hz("A6"), rng, vel=0.9, bright=0.6, hard=0.6), 0.12)
    y = K.phone_speaker(y, drive=1.2)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0004))]), -62)


def phone_tap(rng, i=1):
    y = np.zeros(ns(0.12))
    K.place(y, K.burst(rng, 0.01, 1200, 6000, 0.0002, 0.0012) * 0.5, 0.002)
    m = ns(0.02)
    K.place(y, np.sin(TAU * 165 * np.arange(m) / SR) * np.hanning(m) * 0.6, 0.004)
    return dsp.trim(np.vstack([y, y]), -62)


# ==========================================================================
# the action cameras, the laptop
# ==========================================================================
def _beep(f, d, vel=1.0):
    m = ns(d)
    tt = np.arange(m) / SR
    sq = np.sign(np.sin(TAU * f * tt)) * 0.3 + np.sin(TAU * f * tt)
    return dsp.lowpass(sq, 7000, 2) * np.minimum(1, np.minimum(tt / 0.002, (d - tt) / 0.004)) * vel


def camera_place(rng, i=1):
    """The action camera on its mini tripod set down: three plastic feet clacking, the body settling, a REC beep."""
    y = np.zeros(ns(1.0))
    for k, t0 in enumerate((0.02, 0.035, 0.055)):
        K.place(y, K.nrm(_hit(rng, PLASTIC, vel=rng.uniform(0.6, 1.0), size=0.8, hard=0.9, dur=0.06, noise=0.4)) * 0.6, t0)
    K.place(y, K.nrm(_hit(rng, [(900, 0.04, 1.0), (1800, 0.03, 0.6), (3100, 0.02, 0.4)], vel=0.6, hard=0.6, dur=0.1)) * 0.35, 0.12)
    K.place(y, _beep(2730, 0.09, 0.35), 0.45)
    return K.fin(K.rev(y, rng, 0.12, 0.04), 150)


def _ui_blip(rng, f0, f1, d=0.09, vel=1.0):
    m = ns(d)
    tt = np.arange(m) / SR
    f = f0 * (f1 / f0) ** (tt / d)
    return np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * tt / d) ** 2 * vel


def _digital_noise(rng, d, bits=6, hold=6):
    m = ns(d)
    x = dsp.bandpass(rng.standard_normal(m), 300, 9000, 2)
    return dsp.crush(K.nrm(x), bits, hold)


def camera_app_open(rng, i=1):
    """The camera app opens: a soft rising blip, the feed's noise rushing in and settling."""
    y = np.zeros(ns(0.7))
    K.place(y, _ui_blip(rng, 900, 1500, 0.07, 0.5), 0.0)
    K.place(y, _ui_blip(rng, 1500, 2100, 0.07, 0.4), 0.07)
    m = ns(0.45)
    nz = _digital_noise(rng, 0.45) * K.env_shape(m, [(0, 0), (0.03, 1.0), (0.45, 0)], 1.6)
    nz = dsp.tv_filter(nz, np.linspace(1500, 7000, m), 0.8, "lp", block=128)
    K.place(y, K.rmsn(nz) * 0.12, 0.12)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0005))]), -62)


def camera_app_close(rng, i=1):
    y = np.zeros(ns(0.45))
    K.place(y, _ui_blip(rng, 1700, 950, 0.08, 0.45), 0.0)
    K.place(y, K.burst(rng, 0.004, 1000, 8000, 0.0001, 0.0008) * 0.3, 0.09)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0005))]), -62)


def camera_switch(rng, i=1):
    """Switching feeds: a click and a burst of static as the next camera's stream comes up."""
    y = np.zeros(ns(0.55))
    K.place(y, K.nrm(_hit(rng, SWITCH, vel=0.8, hard=1.0, dur=0.05)) * 0.5, 0.01)
    m = ns(0.32)
    st = (K.rmsn(dsp.colored(m, rng, 0.0, lo=200, hi=9000)) * 0.6 + K.rmsn(_digital_noise(rng, 0.32)) * 0.4)
    st *= K.env_shape(m, [(0, 0), (0.01, 1.0), (0.2, 0.6), (0.32, 0)])
    K.place(y, st * 0.3, 0.03)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0006))]), -62)


def camera_glitch(rng, i=1):
    """The feed glitching: 1 a datamoshed buzz stuttering on one slice, 2 an analogue tear - static swelling, a tone
    squealing through it, 3 a dropout: the sound cuts, a digital buzz loops, it snaps back."""
    if i == 1:
        y = np.zeros(ns(0.8))
        nz = _digital_noise(rng, 0.25, bits=4, hold=14)
        seg = nz[ns(0.05):ns(0.08)]
        K.place(y, nz * 0.7, 0.0)
        for k in range(8):
            K.place(y, seg * (0.9 - 0.06 * k), 0.25 + k * 0.03)
        K.place(y, _digital_noise(rng, 0.15, bits=3, hold=30) * 0.4, 0.5)
    elif i == 2:
        m = ns(0.9)
        tt = np.arange(m) / SR
        st = dsp.colored(m, rng, 0.0, lo=150, hi=10000) * K.env_shape(m, [(0, 0), (0.25, 1.0), (0.6, 0.8), (0.9, 0)])
        sq = np.sin(TAU * np.cumsum(np.interp(tt, [0, 0.4, 0.9], [2400, 4100, 1800])) / SR) * K.env_shape(m, [(0, 0), (0.3, 1.0), (0.9, 0)])
        y = K.rmsn(st) * 0.5 + sq * 0.25
        y *= 1 + 0.4 * np.sign(np.sin(TAU * 59.94 * tt)) * K.env_shape(m, [(0, 0), (0.4, 1.0), (0.9, 0)])
    else:
        y = np.zeros(ns(0.8))
        K.place(y, K.burst(rng, 0.004, 500, 9000, 0.0001, 0.0006) * 0.6, 0.0)
        m = ns(0.3)
        tt = np.arange(m) / SR
        buzz = np.sign(np.sin(TAU * 120 * tt)) * (0.5 + 0.5 * np.sign(np.sin(TAU * 7.5 * tt)))
        K.place(y, dsp.lowpass(buzz, 5000, 2) * 0.35, 0.12)
        K.place(y, K.burst(rng, 0.006, 300, 9000, 0.0001, 0.001) * 0.8, 0.46)
        m = ns(0.3)
        K.place(y, dsp.colored(m, rng, 0.0, lo=200, hi=8000) * K.env_shape(m, [(0, 1.0), (0.3, 0)]) * 0.1, 0.47)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0007))]), -62)


def camera_fall(rng, i=1):
    """A camera knocked off its perch: the tripod and the body clattering on the floor, a short roll, a last tick."""
    y = np.zeros(ns(1.2))
    t = 0.02
    for k, v in enumerate((1.0, 0.7, 0.55, 0.3, 0.2)):
        K.place(y, K.nrm(_hit(rng, PLASTIC, vel=v, size=rng.uniform(0.8, 1.2), hard=0.9, dur=0.1, noise=0.4)) * v, t)
        K.place(y, K.nrm(_hit(rng, ins.WOOD_FLOOR, vel=v * 0.5, hard=0.7, dur=0.15)) * v * 0.3, t)
        t += rng.uniform(0.05, 0.14)
    m = ns(0.4)
    roll = dsp.colored(m, rng, -1.0, lo=500, hi=4000) * K.env_shape(m, [(0, 0), (0.05, 1.0), (0.4, 0)])
    K.place(y, K.rmsn(roll) * 0.06, t)
    return K.fin(K.rev(y, rng, 0.2, 0.05), 100)


def laptop_open(rng, i=1):
    """The old laptop's lid lifted: the stiff hinge creaking, the magnet letting go, the fan spinning up, a faint
    whine of the backlight."""
    y = np.zeros(ns(1.8))
    K.place(y, K.nrm(_hit(rng, PLASTIC, vel=0.4, size=1.2, hard=0.8, dur=0.05)) * 0.3, 0.02)
    cr = ins.creak(0.5, rng, [(0, 40), (0.5, 70)], [(0, 0), (0.05, 0.7), (0.45, 0.6), (0.5, 0)],
                   modes=[(600, 8, 1.0), (1100, 9, 0.6), (1900, 10, 0.3)], jitter=0.4)
    K.place(y, K.nrm(cr) * 0.25, 0.05)
    m = ns(1.2)
    tt = np.arange(m) / SR
    sp = K.env_shape(m, [(0, 0), (0.6, 0.8), (1.2, 1.0)])
    fan = dsp.colored(m, rng, -2.0, lo=200, hi=5000) * sp
    blade = np.sin(TAU * np.cumsum(120 + 180 * sp) / SR) * sp * 0.1
    K.place(y, K.rmsn(fan) * 0.06 + blade * 0.3, 0.55)
    K.place(y, np.sin(TAU * 9800 * np.arange(ns(1.1)) / SR) * 0.006, 0.65)
    y = dsp.highpass(y, 60, 2)
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0006))]), -64)


def laptop_fan(rng, i=1):
    """The laptop's fan, quietly: air rush, the blades' tone wandering with the speed, a coil whine, the hard disk
    seeking twice."""
    L = 8.0
    n = ns(L)
    rpm = 1.0 + 0.04 * dsp.smooth(n, rng, 0.3)
    air = dsp.colored(n, rng, -2.0, lo=150, hi=4500) * rpm
    f = dsp.loop_freq(260.0, L) * rpm
    blade = np.sin(TAU * dsp.periodic_phase(f)) * 0.5 + 0.25 * np.sin(2 * TAU * dsp.periodic_phase(f))
    coil = K.tone(9800.0, L, n, rng) * (1 + 0.5 * dsp.smooth(n, rng, 1.0))
    hdd = np.zeros(n)
    for t0 in (2.3, 6.1):
        for k in range(rng.integers(3, 6)):
            K.place(hdd, K.nrm(_hit(rng, [(2800, 0.01, 1.0), (4400, 0.008, 0.5)], vel=rng.uniform(0.4, 1.0), hard=1.0, dur=0.03)), t0 + k * rng.uniform(0.02, 0.05), L=L)
    m = K.rmsn(air) * 0.6 + blade * 0.08 + coil * 0.004 + K.nrm(hdd) * 0.12
    d = dsp.colored(n, rng, -2.0, lo=150, hi=4500)
    out = np.vstack([m + K.rmsn(d) * 0.1, m - K.rmsn(d) * 0.1])
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# items
# ==========================================================================
def lighter(rng, i=1):
    """A disposable lighter: the flint wheel, the gas catching, the little flame fluttering, let go."""
    import sfx_ritual as R
    y = np.zeros(ns(1.4))
    K.place(y, R._lighter(rng, catch=True, strikes=1), 0.0)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 100)


def paper(rng, i=1):
    """The printout: 1 picked up and handled (crisp rustle, the sheet flexing), 2 a page turned."""
    if i == 1:
        d = 0.9
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (0.1, 1.0), (0.4, 0.6), (0.6, 0.9), (d, 0)], 1.2)
        cr = ins.crackle(d, rng, 1500, env=e, hp=1500, decay=0.0004)
        flex = dsp.bandpass(rng.standard_normal(m), 300, 2500, 2) * e * (1 + 0.6 * dsp.smooth(m, rng, 12.0, periodic=False))
        y = K.nrm(cr) * 0.5 + K.rmsn(flex) * 0.12
    else:
        d = 0.5
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (0.12, 0.6), (0.3, 1.0), (0.36, 0.2), (d, 0)], 1.3)
        sw = dsp.bandpass(rng.standard_normal(m), 600, 9000, 2) * e
        cr = ins.crackle(d, rng, 900, env=e, hp=2000, decay=0.0003)
        y = K.rmsn(sw) * 0.25 + K.nrm(cr) * 0.4
        K.place(y, K.burst(rng, 0.02, 400, 5000, 0.001, 0.005) * 0.3, 0.33)
    y = np.concatenate([np.zeros(ns(0.01)), y, np.zeros(ns(0.1))])
    return dsp.trim(np.vstack([y, np.roll(y, ns(0.0008))]), -62)


def cup(rng, i=1):
    """A glass of water set down on the table: the glass's dull 'tok' on wood (the water damping its ring), the
    water rocking."""
    y = np.zeros(ns(0.9))
    g = _hit(rng, GLASS, vel=1.0, size=1.0, hard=0.8, dur=0.4, noise=0.3)
    g *= np.exp(-np.arange(len(g)) / (0.05 * SR))
    K.place(y, K.nrm(g) * 0.7, 0.02)
    K.place(y, K.nrm(_hit(rng, ins.WOOD_FLOOR, vel=0.6, size=0.6, hard=0.8, dur=0.15)) * 0.35, 0.02)
    sl = K.slosh(0.6, rng, [(0.0, 0.5)], bright=1.0)
    K.place(y, dsp.highpass(sl, 500, 2) * 0.3, 0.05)
    return K.fin(K.rev(y, rng, 0.15, 0.04), 150)
