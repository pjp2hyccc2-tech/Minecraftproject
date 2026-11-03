"""music.* score cues (stereo): dread, tension, danger, chase (layers the client mixes at runtime), prep (near nothing
before three), playback (the laptop replaying the night's recordings).  The music box cues (title, endings) are in
music_box.py.

Tonal plan: everything sits on A, the tonic of the tune's A minor pentatonic, and clashes on purpose with B-flat and
E-flat (minor second, tritone) so the layers can be stacked without turning to mush:
  dread    : low A drone (A1 / A2 / E2) with slowly beating partner partials, a 6 Hz binaural theta pair between the
             drone partials, a sub that swells like breathing, faint bowed metal, one far tooth of the music box
  tension  : micro-tonal string clusters around A - B-flat - E-flat, col legno ticks that never settle into a beat,
             the tune's falling minor third (G - E) as a bowed glass harmonic sinking flat
  danger   : an endless Shepard rise, rough (52 Hz AM) strings on A / B-flat / E, the wall clock ticking twice too fast
  chase    : 150 bpm in 3+3+2, a pumping A / B-flat bass, frantic spiccato on the cluster, dead body blows, the knife
             scraping, the tune's head screaming past on a broken music box
  prep     : a cold low pad, a clock in the next room, two far teeth of the music box, nearly nothing
  playback : the laptop's speakers: VHS hum and hiss, the recorded room's rain, a low pad under it, tracking glitches
Loops are circular (loop-quantised oscillators, circular placement and reverb)."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import tune as T
import voice as V
from dsp import SR, TAU, ns


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0, phase=None):
    return K.lfo(n, cycles, rng, lo, hi, shape, phase)


def _tone(f, L, n, rng):
    return K.tone(f, L, n, rng)


def loop_strings(freqs, L, rng, env=None, voices=3, detune=12.0, vib=6.0, vib_rate=5.2, trem=0.0, trem_rate=12.0,
                 bright=0.5, pont=0.0, width=0.8):
    """Loop-safe string section (whole cycles, periodic vibrato / tremolo, circular filters).  freqs in Hz."""
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(voices):
            dc = rng.normal(0.0, detune / 2.0)
            vr = dsp.loop_freq(vib_rate * (1 + 0.1 * rng.standard_normal()), L)
            vc = vib * np.sin(TAU * vr * t + rng.random() * 6) + 4.0 * dsp.smooth(n, rng, 2.0)
            s = dsp.saw(f * dsp.cents(dc + vc), n, rng.random(), loop=True)
            if trem > 0:
                tr = dsp.loop_freq(trem_rate * (1 + 0.06 * rng.standard_normal()), L)
                s *= (1 - trem) + trem * (0.5 + 0.5 * np.cos(TAU * tr * t + rng.random() * 6)) ** 1.5
            out += dsp.pan(s, rng.uniform(-width, width))
    out /= np.sqrt(len(freqs) * voices)
    out = dsp.circ(lambda x: dsp.lowpass(x, 1800 + 6000 * bright, 2), out)
    out = dsp.circ(lambda x: dsp.peq(x, 2500, 1.2, 3.0 * bright), out)
    out = dsp.circ(lambda x: dsp.peq(x, 300, 1.0, 2.0), out)
    if pont > 0:
        hi = dsp.circ(lambda x: dsp.highpass(x, 3000, 2), out) * 2.0
        nz = np.vstack([dsp.colored(n, rng, 0.0, lo=3000, hi=9000) for _ in range(2)]) * 0.08 * np.std(out)
        out = out * (1 - 0.4 * pont) + pont * (hi + nz)
    if env is not None:
        out *= env
    return out


def _bowed_metal(rng, d, f0, vel=1.0):
    """Faint bowed cymbal / saw: inharmonic partials swelling, bow hiss."""
    n = ns(d)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for k, (r, a) in enumerate(((1.0, 1.0), (2.32, 0.7), (3.86, 0.5), (5.51, 0.35), (7.38, 0.2), (9.4, 0.12))):
        f = f0 * r * (1 + 0.0015 * dsp.smooth(n, rng, 0.5, periodic=False))
        sw = np.sin(np.pi * np.clip((t - rng.uniform(0, d * 0.3)) / (d * 0.7), 0, 1)) ** 2
        y += a * np.sin(TAU * np.cumsum(f) / SR + rng.random() * 6) * sw
    bow = dsp.bandpass(rng.standard_normal(n), f0 * 2, f0 * 8, 2) * np.sin(np.pi * t / d) ** 2 * 0.15
    return (y + bow) * vel


def _far_tooth(rng, name, vel=0.5, cents=0.0, dur=4.0):
    """One tooth of the music box heard from far away in the dark."""
    tn = ins.tine(T.box_hz(name, 1, cents), rng, dur=dur, vel=vel)
    return dsp.lowpass(dsp.highpass(tn, 300, 2), 5000, 2)


# --------------------------------------------------------------------------
# music.dread - 64 s base score
# --------------------------------------------------------------------------
def dread(rng, i=1):
    L = 64.0
    n = ns(L)
    # steady, loop-quantised tones are added dry after the reverb (a circular reverb gives every spectral line a
    # random gain per ear and can cancel a binaural carrier)
    out = np.zeros((2, n))
    for f, a, bt in ((27.5, 0.35, 0.05), (55.0, 1.0, 0.09), (82.41, 0.34, 0.15), (110.0, 0.22, 0.08), (164.81, 0.07, 0.21)):
        for c in range(2):
            sw = _lfo(n, rng.integers(1, 4), rng, 0.45, 1.0)
            out[c] += a * (_tone(f, L, n, rng) + 0.8 * _tone(f + bt * (1 + 0.3 * c), L, n, rng)) * sw
    q = _tone(113.2, L, n, rng) * _lfo(n, 3, rng, 0.0, 1.0, 2.0)  # a quarter-tone above A2: a slow acoustic beat
    out += np.vstack([q, q]) * 0.06
    for f, a, bt in ((220.0, 0.06, 0.31), (329.63, 0.025, 0.47), (440.0, 0.012, 0.6)):  # the drone's pitch on small speakers
        for c in range(2):
            out[c] += a * (_tone(f, L, n, rng) + 0.8 * _tone(f + bt * (1 + 0.25 * c), L, n, rng)) * _lfo(n, rng.integers(1, 3), rng, 0.2, 1.0, 1.5)
    out += dsp.binaural(137.0, 6.0, n, L=L, rng=rng) * 0.15 * _lfo(n, 2, rng, 0.35, 1.0)  # between A2 and C#3
    sub = dsp.sub_rumble(n, 34.0, L=L, rng=rng, am_rate=5.5, am_depth=0.5) * _lfo(n, 11, rng, 0.0, 1.0, 2.5)  # breathing
    out += np.vstack([sub, sub]) * 0.38
    br = np.vstack([dsp.colored(n, rng, -6.0, lo=25, hi=220) for _ in range(2)]) * 0.22 * _lfo(n, 11, rng, 0.2, 1.0, 1.5)
    s = 1.0 / (np.max(np.abs(out + br)) + 1e-12)
    met = np.zeros((2, n))
    for k in range(3):
        d = rng.uniform(6.0, 9.0)
        bm = _bowed_metal(rng, d, rng.uniform(410, 640), 1.0)
        K.place(met, dsp.pan(bm, rng.uniform(-0.8, 0.8)), k * L / 3 + rng.uniform(2, 10), L=L)
    box = np.zeros((2, n))
    for t0, nm, c in ((23.0, "E5", -30), (49.5, "G5", -45)):  # one far tooth, sinking flat
        K.place(box, dsp.pan(_far_tooth(rng, nm, 0.6, c), rng.uniform(-0.7, 0.7)), t0, L=L)
    wet_in = br * s + K.nrm(met) * 0.1 + K.nrm(box) * 0.06
    ir = dsp.make_ir(4.5, rng, stereo=True, predelay=0.04, hf_ratio=0.35)
    out = out * s * 0.95 + dsp.circ_reverb(wet_in, ir, wet=0.55, dry=0.8)
    air = np.vstack([dsp.colored(n, rng, -2.0, lo=300, hi=7000) for _ in range(2)])  # the room's air (also hides the
    out += air * np.sqrt(np.mean(out ** 2)) * 0.02                                    # codec's join at the loop seam)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.tension - 32 s
# --------------------------------------------------------------------------
def tension(rng, i=1):
    L = 32.0
    n = ns(L)
    cl = [dsp.hz("A2"), dsp.hz("A2", 32), dsp.hz("A#2", -14), dsp.hz("D#3", -20), dsp.hz("E3", 12),
          dsp.hz("A3", -8), dsp.hz("A#3", 22), dsp.hz("D#4", 6)]
    out = np.zeros((2, n))
    for k, f in enumerate(cl):
        env = _lfo(n, rng.integers(1, 4), rng, 0.15, 1.0, 1.5)
        s = loop_strings([f], L, rng, env=env, voices=3, detune=10, vib=5, bright=0.35, pont=0.25 if k % 3 == 0 else 0.0)
        out += s * (0.9 if f < 150 else 0.6)
    hi = loop_strings([dsp.hz("E5", -30), dsp.hz("F5", 15), dsp.hz("A#5", -10)], L, rng,
                      env=_lfo(n, 2, rng, 0.0, 1.0, 3.0), voices=2, vib=12, trem=0.4, trem_rate=9, bright=0.8, pont=0.8)
    out = K.nrm(out) + K.nrm(hi) * 0.18
    ticks = np.zeros((2, n))  # col legno: wood on string, never quite a beat
    t = 0.3
    while t < L - 0.1:
        c = ins.modal_hit(rng, [(1900, 0.03, 1.0), (3100, 0.02, 0.6), (950, 0.04, 0.5)], hardness=1.0,
                          vel=rng.uniform(0.4, 1.0), dur=0.08, noise=0.4)
        K.place(ticks, dsp.pan(c, rng.uniform(-0.7, 0.7)), t, L=L)
        t += rng.choice([0.25, 0.5, 0.75, 1.0, 1.5], p=[0.15, 0.3, 0.25, 0.2, 0.1]) * rng.uniform(0.9, 1.1)
    out += K.nrm(ticks) * 0.12
    glass = np.zeros((2, n))  # the tune's falling third on a bowed glass harmonic, sinking flat each time
    for k, t0 in enumerate((4.0, 20.0)):
        for j, (nm, d) in enumerate((("G5", 1.6), ("E5", 2.6))):
            m = ns(d)
            tt = np.arange(m) / SR
            f = dsp.hz(nm, -15 * k - 20 * j) * dsp.cents(-40 * tt / d)
            g = np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * tt / d) ** 2
            K.place(glass, dsp.pan(g, -0.3 + 0.6 * j), t0 + j * 1.5, L=L)
    out += glass * 0.05
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=6.0, am_depth=0.4)  # B-flat-ish against the A drone above
    ir = dsp.make_ir(3.0, rng, stereo=True, predelay=0.03)
    out = dsp.circ_reverb(out, ir, wet=0.4, dry=0.85) + np.vstack([sub, sub]) * 0.15
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.danger - 16 s
# --------------------------------------------------------------------------
def danger(rng, i=1):
    L = 16.0
    n = ns(L)
    sh = dsp.shepard(n, 1 / 8.0, f_lo=55.0, n_oct=7.0, L=L, partials=((1, 1.0), (2, 0.35), (3, 0.18)),
                     strands=(0.0, 0.5), gains=(1.0, 0.7))
    sh2 = dsp.shepard(n, 1 / 8.0, f_lo=55.0 * 2 ** (1 / 12), n_oct=7.0, L=L, strands=(0.25,))
    shl = K.nrm(sh + 0.35 * sh2)
    out = np.vstack([shl, np.roll(shl, ns(0.009))]) * 0.55
    rough = loop_strings([dsp.hz("A3"), dsp.hz("A#3", -10), dsp.hz("E4", -15), dsp.hz("A4", 8)], L, rng, voices=3,
                         detune=14, vib=8, bright=0.6, pont=0.35)
    rough = dsp.am(rough, 52.0, 0.65, L)
    out += K.nrm(rough) * 0.42
    ticks = np.zeros(n)  # the tatami room's clock ticking twice as fast as it should
    for k in range(32):
        c = ins.modal_hit(rng, [(2350, 0.022, 1.0), (3800, 0.016, 0.7), (5900, 0.01, 0.35), (1150, 0.03, 0.4)],
                          size=1.0, hardness=1.0, vel=1.0 if k % 2 == 0 else 0.8, dur=0.07, noise=0.35)
        K.place(ticks, c, k * L / 32, L=L)
    out += np.vstack([ticks * 0.9, ticks]) * 0.2 / (np.max(np.abs(ticks)) + 1e-9)
    sub = dsp.sub_rumble(n, 36.0, L=L, rng=rng, am_rate=6.0, am_depth=0.6)
    ir = dsp.make_ir(2.0, rng, stereo=True, predelay=0.02)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.9) + np.vstack([sub, sub]) * 0.22
    whine = np.vstack([_tone(15734.0, L, n, rng), _tone(15740.0, L, n, rng)])  # the CRT, unstable between the ears
    out += whine * 0.004 * _lfo(n, 2, rng, 0.1, 1.0, 2.0)[None, :]
    return np.roll(dsp.dc_block(out, circular=True), ns(0.03), axis=-1)


# --------------------------------------------------------------------------
# music.chase - 16 s, 150 bpm
# --------------------------------------------------------------------------
def _blow(rng, f0=52.0, vel=1.0):
    """A dead, heavy body blow (a drum with the skin muffled): no ring, all thump."""
    m = ns(0.5)
    t = np.arange(m) / SR
    th = np.sin(TAU * np.cumsum(f0 * (1 + 0.8 * np.exp(-t / 0.018))) / SR) * dsp.env_ad(m, 0.002, 0.09)
    th = np.tanh(2.2 * th)
    th += dsp.lowpass(rng.standard_normal(m), 900, 2) * dsp.env_ad(m, 0.001, 0.015) * 0.5
    return th * vel


def chase(rng, i=1):
    L = 16.0
    n = ns(L)
    beat = L / 40.0
    e8 = beat / 2.0
    out = np.zeros((2, n))
    big = [_blow(rng, 50, 1.0) for _ in range(3)]
    mid = [_blow(rng, 92, 0.7) for _ in range(4)]
    pats = ["x..x..x.", "x..x.x..", "x.x..x..", "x..x..x."]
    for bar in range(10):
        b0 = bar * 4 * beat
        if bar == 9:
            for k in range(16):
                K.place(out, dsp.pan(mid[k % 4], -0.4 + 0.8 * (k % 2)) * (0.45 + 0.55 * k / 15), b0 + k * e8 / 2, L=L)
            for k in (0, 6, 12):
                K.place(out, dsp.pan(big[k % 3], 0.0), b0 + k * e8 / 2, L=L)
            continue
        p = pats[bar % 4]
        for k in range(8):
            tk = b0 + k * e8 + rng.normal(0, 0.003)
            if p[k] == "x":
                K.place(out, dsp.pan(big[rng.integers(3)], rng.uniform(-0.15, 0.15)) * (1.0 if k == 0 else 0.85), tk, L=L)
            elif rng.random() < (0.55 if bar >= 4 else 0.35):
                K.place(out, dsp.pan(mid[rng.integers(4)], rng.uniform(-0.5, 0.5)) * rng.uniform(0.45, 0.8), tk, L=L)
    drums = K.nrm(out)
    t = np.arange(n) / SR
    bar_i = np.floor(t / (4 * beat)).astype(int)
    bf = np.where((bar_i // 2) % 2 == 0, dsp.hz("A1"), dsp.hz("A#1"))
    bf = dsp.circ(lambda x: dsp.lowpass0(x, 30), bf)
    pump = np.exp(-((t / e8) % 1.0) / 0.35)
    bass = dsp.saw(bf, n, loop=True) + 0.6 * dsp.saw(bf * 1.004, n, 0.3, loop=True)
    bass = dsp.circ(lambda x: dsp.lowpass(x, 420, 3), bass) * pump
    spic = np.zeros((2, n))  # spiccato sixteenths on the cluster
    seq = ["A4", "A#4", "E5", "A4", "D#5", "A4", "A#4", "E5"]
    for k in range(int(L / (e8 / 2))):
        f = dsp.hz(seq[k % len(seq)], rng.uniform(-12, 12))
        m = ns(0.09)
        tt = np.arange(m) / SR
        s = dsp.saw(f, m, rng.random()) * dsp.env_ad(m, 0.004, 0.025)
        s = dsp.lowpass(s, 5000, 2)
        K.place(spic, dsp.pan(s, 0.5 if k % 2 else -0.5) * (1.0 if k % 4 == 0 else 0.65), k * e8 / 2, L=L)
    sh = dsp.shepard(n, -1 / 4.0, f_lo=55.0, n_oct=7.0, L=L, partials=((1, 1.0), (2, 0.4)), strands=(0.0, 0.5), gains=(1.0, 0.6))
    box = np.zeros((2, n))  # the tune's head screaming past on a broken music box, too fast, an octave apart
    for rep, t0 in enumerate((3.2, 11.2)):
        for k, (b, nm, d) in enumerate(T.TUNE[:8]):
            for o in (1, 2):
                tn = ins.tine(T.box_hz(nm, o, -40 * rep + rng.uniform(-20, 20)), rng, dur=0.8, vel=0.6 if o == 1 else 0.35)
                K.place(box, dsp.pan(tn, -0.3 if o == 1 else 0.4), t0 + b * e8, L=L)
    met = np.zeros((2, n))
    for k in range(3):  # the knife scraping steel
        d = beat * rng.uniform(3, 5)
        m = ns(d)
        x = np.linspace(0, 1, m)
        s = np.zeros(m)
        for f in (1850, 3300, 4900, 6800):
            s += np.sin(TAU * np.cumsum(f * dsp.cents(rng.uniform(-200, 200) + 300 * x * rng.choice([-1, 1]))) / SR) * rng.uniform(0.3, 1.0)
        s = s * np.sin(np.pi * x) ** 1.5 + dsp.bandpass(rng.standard_normal(m), 2500, 8000, 2) * np.sin(np.pi * x) ** 1.5 * 0.4
        K.place(met, dsp.pan(s, np.linspace(-0.8, 0.8, m)), (k * 3 + 1.5) * 4 * beat / 2.2, L=L)
    mix = drums * 1.0 + np.vstack([bass, bass]) / (np.max(np.abs(bass)) + 1e-9) * 0.4
    mix += K.nrm(spic) * 0.28
    mix += np.vstack([sh, np.roll(sh, ns(0.007))]) / (np.max(np.abs(sh)) + 1e-9) * 0.25
    mix += K.nrm(box) * 0.2 + K.nrm(met) * 0.1
    ir = dsp.make_ir(1.4, rng, stereo=True, predelay=0.015)
    mix = dsp.circ_reverb(mix, ir, wet=0.28, dry=0.9)
    return np.roll(dsp.dc_block(mix, circular=True), ns(0.02), axis=-1)


# --------------------------------------------------------------------------
# music.prep - 40 s: near nothing.  A low cold pad, a clock in the next room, two far teeth of the music box
# --------------------------------------------------------------------------
def prep(rng, i=1):
    L = 40.0
    n = ns(L)
    pad = loop_strings([dsp.hz("A2"), dsp.hz("E3"), dsp.hz("B3", -8)], L, rng, env=_lfo(n, 2, rng, 0.25, 1.0, 1.5),
                       voices=2, detune=6, vib=3, bright=0.05)
    pad = dsp.circ(lambda x: dsp.lowpass(x, 900, 2), pad)
    out = K.nrm(pad) * 0.5
    clk = np.zeros((2, n))
    for k in range(40):
        c = ins.modal_hit(rng, [(2350, 0.022, 1.0), (3800, 0.016, 0.7), (1150, 0.03, 0.4)], size=1.0 if k % 2 == 0 else 1.06,
                          hardness=1.0, vel=1.0 if k % 2 == 0 else 0.85, dur=0.08, noise=0.3)
        K.place(clk, dsp.pan(dsp.lowpass(c, 3500, 2), -0.6), 0.2 + k * 1.0, L=L)
    out += K.nrm(clk) * 0.07
    box = np.zeros((2, n))
    for t0, nm in ((9.5, "G5"), (27.0, "E5")):
        K.place(box, dsp.pan(_far_tooth(rng, nm, 0.5, -20), rng.uniform(-0.5, 0.5)), t0, L=L)
    out += K.nrm(box) * 0.1
    ir = dsp.make_ir(3.8, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.circ_reverb(out, ir, wet=0.45, dry=0.8)
    out += dsp.binaural(110.0, 4.5, n, L=L, rng=rng) * 0.01 * _lfo(n, 2, rng, 0.3, 1.0)[None, :]
    air = np.vstack([dsp.colored(n, rng, -2.0, lo=300, hi=7000) for _ in range(2)])
    out += air * np.sqrt(np.mean(out ** 2)) * 0.02
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.playback - 40 s: the laptop replaying the recordings the next afternoon
# --------------------------------------------------------------------------
def playback(rng, i=1):
    L = 40.0
    n = ns(L)
    t = np.arange(n) / SR
    rec = np.zeros((2, n))
    for c in range(2):  # what the cameras heard: the room, the rain, through their cheap microphones
        g = K.rain_glass(n, rng, rate=35.0, L=L, close=0.6)
        hz = K.rain_hiss(n, rng, L=L, bright=0.6)
        rec[c] = K.cr(g, lambda v: dsp.lowpass(v, 5000, 2)) * 0.5 + K.cr(hz, lambda v: dsp.lowpass(v, 1500, 2)) * 0.15
    rec = K.cr(rec, lambda v: dsp.bandpass(v, 200, 6000, 2))
    pad = loop_strings([dsp.hz("A2"), dsp.hz("C3", -10), dsp.hz("D#3", 8)], L, rng, env=_lfo(n, 2, rng, 0.3, 1.0, 1.5),
                       voices=2, detune=8, vib=3, bright=0.08)
    hum = K.hum(L, n, rng, 50.0, ((1, 0.6), (2, 1.0), (3, 0.5), (4, 0.3), (6, 0.15)))  # VHS / mains hum
    hiss = np.vstack([dsp.colored(n, rng, -0.5, lo=1500, hi=12000) for _ in range(2)])
    glitches = np.zeros((2, n))
    for t0 in (7.3, 18.9, 31.4):  # tracking errors: the picture tears, the sound with it
        d = rng.uniform(0.3, 0.6)
        m = ns(d)
        g = dsp.colored(m, rng, 0.0, lo=300, hi=9000) * K.env_shape(m, [(0, 0), (0.03, 1.0), (d, 0)], 1.5)
        g *= 1 + 0.6 * np.sign(np.sin(TAU * 59.94 * np.arange(m) / SR))
        K.place(glitches, np.vstack([g, g]), t0, L=L)
    mix = K.nrm(rec) * 0.5 + K.nrm(pad) * 0.35 + np.vstack([hum, hum]) / (np.max(np.abs(hum)) + 1e-9) * 0.06
    mix += hiss / np.std(hiss) * 0.012 + K.nrm(glitches) * 0.18
    wow = 1.0 + 0.0025 * np.sin(TAU * dsp.loop_freq(0.6, L) * t) + 0.001 * np.sin(TAU * dsp.loop_freq(6.3, L) * t)
    mix = K.circ_varispeed(mix, n, wow)
    mix = K.cr(mix, lambda v: K.laptop_speaker(v))
    mid = mix.mean(axis=0)
    mix = np.vstack([mid + (mix[0] - mid) * 0.3, mid + (mix[1] - mid) * 0.3])  # two small speakers close together
    ir = dsp.make_ir(0.5, rng, stereo=True, predelay=0.004, hf_ratio=0.5)
    mix = dsp.circ_reverb(mix, ir, wet=0.15, dry=0.9)
    return dsp.dc_block(mix, circular=True)
