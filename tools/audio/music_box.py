"""The toy music box: music.title (the tune over rain, loop), music.ending_good (morning, the tune resolved at last,
then one sour tooth), music.ending_bad (the box running down).

The box is a cheap 18-tooth toy: every tooth out of tune by its own amount and the whole comb flat (tune.py), the
cylinder's pins clicking as they lift each tooth, the governor's little air-brake fan whirring, the wooden case
adding its hollow body.  When the spring runs down, the governor slows, the tempo sags and every note falls flat."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import tune as T
from dsp import SR, TAU, ns


def _pluck(rng, name, octave=1, vel=0.7, extra=0.0, dur=None):
    """One tooth: the tine, the pin's click as it lets go, a little comb rattle."""
    f = T.box_hz(name, octave, extra + rng.uniform(-4, 4))
    tn = ins.tine(f, rng, dur=dur, vel=vel)
    m = ns(0.006)
    pin = dsp.bandpass(rng.standard_normal(m), 3000, 11000, 2) * np.exp(-np.arange(m) / (0.0006 * SR)) * 0.25 * vel
    y = tn.copy()
    y[:m] += pin
    return y


def _case(x):
    """The small wooden case the comb is screwed to: a hollow body under the tines."""
    body = dsp.resbank(x, [(310, 5, 1.0), (620, 6, 0.6), (1150, 7, 0.4), (2200, 8, 0.2)])
    return x + body / (np.std(body) + 1e-9) * np.std(x) * 0.25


def _governor(n, rng, speed, L=None):
    """The air-brake fan of the mechanism: a soft whirr whose pitch follows the spring's speed (array 0..1)."""
    f = 34.0 * np.maximum(speed, 0.05)
    ph = dsp.periodic_phase(f) if L else np.cumsum(f) / SR
    blade = (0.5 + 0.5 * np.cos(TAU * ph * 4)) ** 4
    nz = dsp.bandpass(rng.standard_normal(n), 900, 6000, 2)
    gear = dsp.bandpass(rng.standard_normal(n), 200, 1500, 2) * (0.5 + 0.5 * np.cos(TAU * ph * 13)) ** 6
    return (nz * blade * 0.6 + gear * 0.4) * np.clip(speed, 0, 1) ** 0.7


def _rain_window(n, rng, L=None):
    st = np.zeros((2, n))
    for c, p in ((0, 0.8), (1, 1.0)):
        g = K.rain_glass(n, rng, rate=35.0, L=L, close=0.6)
        st[c] = K.cr(g, lambda v: dsp.lowpass(v, 5500, 2)) if L else dsp.lowpass(g, 5500, 2)
        st[c] *= p
    hz = np.vstack([K.rain_hiss(n, rng, L=L, bright=0.5) for _ in range(2)])
    hz = K.cr(hz, lambda v: dsp.lowpass(v, 1300, 2)) if L else dsp.lowpass(hz, 1300, 2)
    return st * 0.5 + hz * 0.15


# --------------------------------------------------------------------------
# music.title - 48 s loop: the tune twice on the toy box, rain on the window, a low drone, the mechanism
# --------------------------------------------------------------------------
def title(rng, i=1):
    L = 48.0
    n = ns(L)
    t = np.arange(n) / SR
    beat = L / (2 * T.BEATS)  # 0.75 s
    wow = 1.0 + 0.004 * np.sin(TAU * dsp.loop_freq(0.37, L) * t) + 0.0015 * np.sin(TAU * dsp.loop_freq(3.1, L) * t)
    box = np.zeros(n)
    for p in range(2):
        t0 = 0.4 + p * T.BEATS * beat
        for k, (b, nm, d) in enumerate(T.TUNE):
            if p == 1 and k in (13, 22):  # second time round two teeth do not sound (bent pins)
                continue
            tb = t0 + b * beat + rng.normal(0, 0.006)
            K.place(box, _pluck(rng, nm, 1, vel=0.62 * rng.uniform(0.88, 1.0)), tb, L=L)
        for b, notes in T.BASS:
            for j, nm in enumerate(notes):
                K.place(box, _pluck(rng, nm, 1, vel=0.3 - 0.06 * j), t0 + b * beat + 0.012 * j, L=L)
    box = K.cr(box, lambda v: dsp.highpass(v, 160, 2))
    box = _case(box)
    box = K.circ_varispeed(box, n, wow)
    gov = _governor(n, rng, np.full(n, 1.0), L=L)
    boxs = np.vstack([box, np.roll(box, ns(0.004))]) + np.vstack([gov, np.roll(gov, ns(0.003))]) * np.std(box) * 0.05
    ir = dsp.make_ir(1.8, rng, stereo=True, predelay=0.012, hf_ratio=0.4)  # the tatami room at night
    out = dsp.circ_reverb(boxs, ir, wet=0.35, dry=0.85)
    out = K.nrm(out) * 0.75
    out += _rain_window(n, rng, L=L) * 0.09
    drone = np.zeros((2, n))
    for f, a, bt in ((55.0, 0.6, 0.07), (82.41, 0.35, 0.11), (110.0, 0.25, 0.13)):
        for c in range(2):
            drone[c] += a * (K.tone(f, L, n, rng) + 0.7 * K.tone(f + bt * (1 + 0.4 * c), L, n, rng))
    drone *= K.lfo(n, 2, rng, 0.5, 1.0)[None, :]
    out += drone * 0.03 + dsp.binaural(128.0, 5.5, n, L=L, rng=rng) * 0.008 * K.lfo(n, 3, rng, 0.3, 1.0)[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.ending_good - 50 s: morning.  Sparrows, a warm slow chord, the tune played gently and resolved - and at the
# very end a single tooth of the music box, sour, by itself
# --------------------------------------------------------------------------
def ending_good(rng, i=1):
    dur = 50.0
    n = ns(dur)
    out = np.zeros((2, n))
    import sfx_game as G
    air = np.vstack([dsp.colored(n, rng, -4.0, lo=120, hi=2500) for _ in range(2)])
    out += air * 0.02 * dsp.env_pts(n, [(0, 1.0), (14, 0.6), (40, 0.4), (dur, 0.3)])
    chords = [(0.5, ["A2", "E3", "C4", "G4", "B4"]), (9.0, ["F2", "C3", "A3", "E4", "G4"]),
              (17.5, ["C3", "G3", "E4", "A4", "D5"]), (26.0, ["G2", "D3", "B3", "E4", "A4"]),
              (34.5, ["A2", "E3", "C#4", "E4", "A4", "B4"])]
    for k, (t0, names) in enumerate(chords):
        d = 9.0 if k < 4 else dur - t0 - 4.0
        m = ns(d)
        env = dsp.env_pts(m, [(0, 0), (3.0, 1.0), (d - 2.0, 0.85), (d, 0)])
        pad = ins.strings([dsp.hz(x) for x in names], d, rng, env=env, voices=3, detune=7, vib_depth=5, vib_rate=4.6,
                          bright=0.12 + 0.05 * k)
        K.place(out, dsp.lowpass(pad, 1800 + 300 * k, 2) / (np.max(np.abs(pad)) + 1e-9) * (0.2 + 0.03 * k), t0)
    beat = 1.02  # the tune on a soft piano, slowly, the last phrase resolved down to A
    tune = [(b, nm, d) for b, nm, d in T.TUNE[:-3]] + [(28, "D5", 1), (29, "C5", 1), (30, "A4", 3)]
    for b, nm, d in tune:
        tb = 4.0 + b * beat
        p = ins.piano(dsp.hz(nm, rng.uniform(-3, 3)), 5.5, rng, vel=0.38 + 0.08 * (b < 16), bright=0.32)
        K.place(out, dsp.pan(p, rng.uniform(-0.25, 0.25)) * 0.42, tb)
    for nm, dt in (("A2", 0.0), ("E3", 0.08), ("C#4", 0.16), ("A4", 0.26)):  # the cadence chord under the last note
        p = ins.piano(dsp.hz(nm), 8.0, rng, vel=0.34, bright=0.28)
        K.place(out, dsp.pan(p, 0.05) * 0.3, 4.0 + 30 * beat + dt)
    for _ in range(26):  # sparrows waking up
        t0 = rng.uniform(3.0, 44.0)
        ch = np.zeros(ns(0.6))
        for j in range(rng.integers(1, 4)):
            K.place(ch, G._sparrow(rng), j * rng.uniform(0.09, 0.15))
        K.place(out, dsp.pan(dsp.lowpass(ch, 8000, 2), rng.uniform(-0.9, 0.9)) * 0.05 * min(1.0, (t0 - 2) / 10), t0)
    for t0 in (19.0, 19.6):  # a crow far away
        import voice as V
        c, _ = V.scream(0.32, rng, [(0, 480), (0.05, 560), (0.32, 420)], [(0, 0), (0.03, 1), (0.25, 0.7), (0.32, 0)],
                        vowel="a", chaos_pts=[(0, 3.9), (0.32, 3.9)], breath=0.6, rasp=1.0, scale=1.2, oq=0.45)
        K.place(out, dsp.pan(dsp.lowpass(c, 3000, 2), 0.6) * 0.025, t0)
    sour_t = dur - 6.5  # the playback's last seconds: the closet slides open by itself
    tn = ins.tine(dsp.hz("D#5", T.BOX_FLAT - 18.0) * 2, rng, dur=5.0, vel=0.7)  # a tooth that is not in the tune
    tn = _case(tn)
    K.place(out, dsp.pan(tn, -0.3) * 0.55, sour_t)
    m = ns(6.0)
    cl = ins.strings([dsp.hz(x) for x in ["A3", "A#3", "D#4"]], 6.0, rng,
                     env=dsp.env_pts(m, [(0, 0), (2.5, 0.6), (6.0, 0)]), voices=2, detune=12, bright=0.2)
    K.place(out, K.nrm(cl) * 0.08, sour_t + 0.3)
    ir = dsp.make_ir(3.2, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.38, dry=0.82), n)
    return dsp.fade(dsp.highpass(out, 30, 2), 1.0, 0.8)


# --------------------------------------------------------------------------
# music.ending_bad - 30 s: the music box running down - slower, flatter, teeth missing, the governor dying - it stops
# one note before the end
# --------------------------------------------------------------------------
def ending_bad(rng, i=1):
    dur = 30.0
    n = ns(dur)
    box = np.zeros(n)
    t = 0.5
    speed = 1.0
    spd_t, spd_v = [0.0], [1.0]
    for k, (b, nm, d) in enumerate(T.TUNE[:-1]):  # never reaches the last note
        nxt = T.TUNE[k + 1][0] - b
        if k not in (7, 12, 19):  # broken teeth
            flat = -120.0 * (1.0 - speed) ** 1.2
            K.place(box, _pluck(rng, nm, 1, vel=0.6 * speed ** 0.35, extra=flat), t)
        t += nxt * 0.62 / speed
        speed *= 0.952
        spd_t.append(t)
        spd_v.append(speed)
        if t > 22.0:
            break
    t_stop = t
    sp = np.interp(np.arange(n) / SR, spd_t + [t_stop + 0.6, dur], spd_v + [0.0, 0.0])
    gov = _governor(n, rng, sp)
    box = _case(dsp.highpass(box, 160, 2))
    box = box + gov * np.std(box[box != 0]) * 0.08
    m = ns(0.06)  # the spring's last click, the cylinder settling
    clk = dsp.bandpass(rng.standard_normal(m), 1500, 7000, 2) * np.exp(-np.arange(m) / (0.004 * SR))
    K.place(box, clk * np.max(np.abs(box)) * 0.25, t_stop + 0.7)
    out = np.vstack([box, np.roll(box, ns(0.004))])
    ir = dsp.make_ir(2.4, rng, stereo=True, predelay=0.02, hf_ratio=0.35)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.42, dry=0.82), n)
    lo = np.vstack([np.sin(TAU * (55.0 + 0.1 * c) * np.arange(n) / SR) for c in range(2)])
    out += K.nrm(out).max() * lo * 0.02 * dsp.env_pts(n, [(0, 0), (4, 0.6), (t_stop, 1.0), (dur, 0)])[None, :]
    room = np.vstack([dsp.colored(n, rng, -3.0, lo=80, hi=2500) for _ in range(2)])
    out += room * 0.0015
    out = dsp.highpass(out, 35, 2)
    return dsp.fade(out, 0.2, 2.0)
