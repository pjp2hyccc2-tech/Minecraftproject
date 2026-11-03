"""music.* score cues (stereo): dread, tension, danger, chase, safe, exorcism.  (The cues built on the store's
jingle - store, store_wrong, closing, title, the endings - are in music_store.py.)

Tonal plan, so that the layers the client mixes at runtime (dread, tension, danger, chase) clash on purpose without
turning to mush, and sit in D minor, the relative minor of the jingle's F major:
  dread    : low D drone (D1/D2/A2) with slowly beating partner partials, a 6 Hz binaural theta pair
             (129 / 135 Hz, between the drone partials), sub swells, faint bowled metal
  tension  : micro-tonal string clusters around D - Eb - Ab (minor second, tritone), irregular heartbeat
  danger   : endless Shepard rise (key independent, tritone-paired strands) + rough 52 Hz AM strings on D/Eb/A + the
             wall clock ticking twice too fast
  chase    : D/Eb pumping bass, falling Shepard, screeching clusters, stainless steel struck in the kitchen
  safe     : the jingle's melody in D minor, sparse, on a piano and a music box, rain dripping off the canopy
  exorcism : singing bowls, a chant drone over a wooden fish, light rising, a release into F major
Loops are circular (loop-quantised oscillators, circular placement / reverb); layers that cannot be periodic are
rendered longer and crossfaded into their start."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0, phase=None):
    ph = cycles * np.arange(n) / n + (rng.random() if phase is None else phase)
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * ph)) ** shape


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _qt(f, L):
    return dsp.loop_freq(f, L)


def _tone(f, L, n, rng):
    return np.sin(TAU * (_qt(f, L) * np.arange(n) / SR + rng.random()))


def loop_strings(freqs, L, rng, env=None, voices=3, detune=12.0, vib=6.0, vib_rate=5.2, trem=0.0, trem_rate=12.0,
                 bright=0.5, pont=0.0, width=0.8):
    """Loop-safe string section (whole cycles, periodic vibrato / tremolo,
    circular filters).  freqs in Hz."""
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(voices):
            dc = rng.normal(0.0, detune / 2.0)
            vr = _qt(vib_rate * (1 + 0.1 * rng.standard_normal()), L)
            vc = vib * np.sin(TAU * vr * t + rng.random() * 6) + 4.0 * dsp.smooth(n, rng, 2.0)
            s = dsp.saw(f * dsp.cents(dc + vc), n, rng.random(), loop=True)
            if trem > 0:
                tr = _qt(trem_rate * (1 + 0.06 * rng.standard_normal()), L)
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


def loop_brass(freqs, L, rng, env, voices=3):
    """Low brass-like cluster: detuned saws whose brightness follows the
    dynamics (crossfade dark/bright filtered copies), a growl AM."""
    n = ns(L)
    t = np.arange(n) / SR
    raw = np.zeros((2, n))
    for f in freqs:
        for v in range(voices):
            fv = f * dsp.cents(rng.normal(0, 7) + 3 * dsp.smooth(n, rng, 1.5))
            raw += dsp.pan(dsp.saw(fv, n, rng.random(), loop=True), rng.uniform(-0.6, 0.6))
    dark = dsp.circ(lambda x: dsp.lowpass(x, 260, 2), raw)
    bright = dsp.circ(lambda x: dsp.lowpass(x, 2400, 2), raw)
    e = np.clip(env, 0, 1)
    y = (dark * (1 - e) + bright * e) * env
    y = dsp.am(y, 31.0, 0.12, L)
    return y


def _xf_loop(render, L, X, *a, **k):
    """Render L + X seconds of a non-periodic layer and crossfade into a loop."""
    x = render(L + X, *a, **k)
    return dsp.loop_xfade(x, ns(L), ns(X))


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


# --------------------------------------------------------------------------
# music.dread - 64 s base score
# --------------------------------------------------------------------------
def dread(rng, i=1):
    L = 64.0
    n = ns(L)
    # NB: steady, loop-quantised tones are added *dry* after the reverb: a circular reverb gives every
    # spectral line a random complex gain per ear (it once cancelled a binaural carrier by 37 dB)
    out = np.zeros((2, n))
    for f, a, bt in ((36.71, 0.55, 0.07), (73.42, 1.0, 0.11), (110.0, 0.32, 0.17), (146.83, 0.2, 0.09), (220.0, 0.06, 0.23)):
        for c in range(2):
            sw = _lfo(n, rng.integers(1, 4), rng, 0.45, 1.0)
            out[c] += a * (_tone(f, L, n, rng) + 0.8 * _tone(f + bt * (1 + 0.3 * c), L, n, rng)) * sw
    q = _tone(151.1, L, n, rng) * _lfo(n, 3, rng, 0.0, 1.0, 2.0)  # a quarter-tone above D3: 4.3 Hz acoustic beat
    out += np.vstack([q, q]) * 0.06
    for f, a, bt in ((293.66, 0.06, 0.31), (440.0, 0.025, 0.47), (587.33, 0.012, 0.6)):  # D4 A4 D5: the drone's pitch on small speakers
        for c in range(2):
            out[c] += a * (_tone(f, L, n, rng) + 0.8 * _tone(f + bt * (1 + 0.25 * c), L, n, rng)) * _lfo(n, rng.integers(1, 3), rng, 0.2, 1.0, 1.5)
    out += dsp.binaural(132.0, 6.0, n, L=L, rng=rng) * 0.16 * _lfo(n, 2, rng, 0.35, 1.0)  # 129 / 135 Hz: between the drone partials
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=5.5, am_depth=0.5) * _lfo(n, 2, rng, 0.0, 1.0, 2.5)
    out += np.vstack([sub, sub]) * 0.4
    br = np.vstack([dsp.colored(n, rng, -6.0, lo=25, hi=220) for _ in range(2)]) * 0.22 * _lfo(n, 3, rng, 0.2, 1.0, 1.5)
    s = 1.0 / (np.max(np.abs(out + br)) + 1e-12)
    met = np.zeros((2, n))
    for k in range(3):
        d = rng.uniform(6.0, 9.0)
        bm = _bowed_metal(rng, d, rng.uniform(410, 640), 1.0)
        dsp.place(met, dsp.pan(bm, rng.uniform(-0.8, 0.8)), k * L / 3 + rng.uniform(2, 10), circular=True)
    wet_in = br * s + _nrm(met) * 0.1
    ir = dsp.make_ir(4.5, rng, stereo=True, predelay=0.04, hf_ratio=0.35)
    out = out * s * 0.95 + dsp.circ_reverb(wet_in, ir, wet=0.5, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.tension - 32 s micro-tonal clusters + irregular heartbeat
# --------------------------------------------------------------------------
def tension(rng, i=1):
    L = 32.0
    n = ns(L)
    cl = [dsp.hz("D3"), dsp.hz("D3", 32), dsp.hz("D#3", -14), dsp.hz("G#3", -20), dsp.hz("A3", 12),
          dsp.hz("D4", -8), dsp.hz("D#4", 22), dsp.hz("G#4", 6)]
    out = np.zeros((2, n))
    for k, f in enumerate(cl):
        env = _lfo(n, rng.integers(1, 4), rng, 0.15, 1.0, 1.5)
        s = loop_strings([f], L, rng, env=env, voices=3, detune=10, vib=5, bright=0.35, pont=0.25 if k % 3 == 0 else 0.0)
        out += s * (0.9 if f < 200 else 0.6)
    hi = loop_strings([dsp.hz("A5", -30), dsp.hz("A#5", 15), dsp.hz("D#6", -10)], L, rng,
                      env=_lfo(n, 2, rng, 0.0, 1.0, 3.0), voices=2, vib=12, trem=0.4, trem_rate=9, bright=0.8, pont=0.8)
    out = _nrm(out) + _nrm(hi) * 0.18
    hb = np.zeros(n)
    t = 0.4
    while t < L - 0.2:
        h = ins.heartbeat(rng, vel=rng.uniform(0.6, 1.0), gap=rng.uniform(0.22, 0.3))
        dsp.place(hb, h, t, circular=True)
        t += rng.choice([0.75, 0.82, 0.9, 1.0, 1.35, 1.6], p=[0.25, 0.25, 0.2, 0.15, 0.1, 0.05]) * rng.uniform(0.95, 1.05)
    hb = np.tanh(2.0 * _nrm(hb))
    out += np.vstack([hb, hb]) * 0.5
    sub = dsp.sub_rumble(n, 38.89, L=L, rng=rng, am_rate=6.0, am_depth=0.4)  # Eb1 under the D drone above
    ir = dsp.make_ir(3.0, rng, stereo=True, predelay=0.03)
    out = dsp.circ_reverb(out, ir, wet=0.4, dry=0.85) + np.vstack([sub, sub]) * 0.15
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.danger - 16 s endless Shepard rise + rough strings + ticking
# --------------------------------------------------------------------------
def danger(rng, i=1):
    L = 16.0
    n = ns(L)
    t = np.arange(n) / SR
    sh = dsp.shepard(n, 1 / 8.0, f_lo=45.0, n_oct=7.0, L=L, partials=((1, 1.0), (2, 0.35), (3, 0.18)),
                     strands=(0.0, 0.5), gains=(1.0, 0.7))
    sh2 = dsp.shepard(n, 1 / 8.0, f_lo=45.0 * 2 ** (1 / 12), n_oct=7.0, L=L, strands=(0.25,))  # + a minor 2nd strand
    shl = _nrm(sh + 0.35 * sh2)
    out = np.vstack([shl, np.roll(shl, ns(0.009))]) * 0.55
    rough = loop_strings([dsp.hz("D4"), dsp.hz("D#4", -10), dsp.hz("A4", -15), dsp.hz("D5", 8)], L, rng, voices=3,
                         detune=14, vib=8, bright=0.6, pont=0.35)
    rough = dsp.am(rough, 52.0, 0.65, L)  # roughness band
    out += _nrm(rough) * 0.42
    ticks = np.zeros(n)  # the dining room's wall clock, ticking twice as fast as it should
    for k in range(32):
        c = ins.modal_hit(rng, [(2100, 0.025, 1.0), (3400, 0.018, 0.6), (5600, 0.01, 0.3), (900, 0.03, 0.3)],
                          size=1.0, hardness=1.0, vel=1.0 if k % 2 == 0 else 0.8, dur=0.07, noise=0.35)
        dsp.place(ticks, c, k * L / 32, circular=True)
    out += np.vstack([ticks * 0.9, ticks]) * 0.2 / (np.max(np.abs(ticks)) + 1e-9)
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=6.0, am_depth=0.6)
    ir = dsp.make_ir(2.0, rng, stereo=True, predelay=0.02)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.9) + np.vstack([sub, sub]) * 0.22
    whine = np.vstack([_tone(14800.0, L, n, rng), _tone(14806.0, L, n, rng)])  # at the edge of hearing, unstable between the ears
    out += whine * 0.004 * _lfo(n, 2, rng, 0.1, 1.0, 2.0)[None, :]
    return np.roll(dsp.dc_block(out, circular=True), ns(0.03), axis=-1)  # first tick just after the seam


# --------------------------------------------------------------------------
# music.chase - 16 s, 150 bpm, irregular accents
# --------------------------------------------------------------------------
def chase(rng, i=1):
    L = 16.0
    n = ns(L)
    beat = L / 40.0  # 150 bpm
    e8 = beat / 2.0
    out = np.zeros((2, n))
    big = [ins.taiko(rng, f0=52, vel=1.0, decay=0.45, dur=1.1, size=1.35) for _ in range(3)]
    mid = [ins.taiko(rng, f0=94, vel=0.85, decay=0.22, dur=0.55, size=0.8, stick=0.9) for _ in range(4)]
    pats = ["x..x..x.", "x..x.x..", "x.x..x..", "x..x..x."]  # 3+3+2 / 3+2+3 / 2+3+3 groupings
    for bar in range(10):
        b0 = bar * 4 * beat
        if bar == 9:  # fill
            for k in range(16):
                dsp.place(out, dsp.pan(mid[k % 4], -0.4 + 0.8 * (k % 2)) * (0.45 + 0.55 * k / 15), b0 + k * e8 / 2, circular=True)
            for k in (0, 6, 12):
                dsp.place(out, dsp.pan(big[k % 3], 0.0), b0 + k * e8 / 2, circular=True)
            continue
        p = pats[bar % 4]
        for k in range(8):
            tk = b0 + k * e8 + rng.normal(0, 0.003)
            if p[k] == "x":
                dsp.place(out, dsp.pan(big[rng.integers(3)], rng.uniform(-0.15, 0.15)) * (1.0 if k == 0 else 0.85), tk, circular=True)
            elif rng.random() < (0.55 if bar >= 4 else 0.35):
                dsp.place(out, dsp.pan(mid[rng.integers(4)], rng.uniform(-0.5, 0.5)) * rng.uniform(0.45, 0.8), tk, circular=True)
            rim = ins.kakko_hit(rng, 0.14 + (0.08 if p[k] == "x" else 0.0))
            dsp.place(out, dsp.pan(rim, 0.6 if k % 2 else -0.6), tk + e8 / 2, circular=True)
    drums = _nrm(out)
    t = np.arange(n) / SR
    bar_i = np.floor(t / (4 * beat)).astype(int)
    bf = np.where((bar_i // 2) % 2 == 0, dsp.hz("D2"), dsp.hz("D#2"))
    bf = dsp.circ(lambda x: dsp.lowpass0(x, 30), bf)
    pump = np.exp(-((t / e8) % 1.0) / 0.35)
    bass = dsp.saw(bf, n, loop=True) + 0.6 * dsp.saw(bf * 1.004, n, 0.3, loop=True)
    bass = dsp.circ(lambda x: dsp.lowpass(x, 420, 3), bass) * pump
    sh = dsp.shepard(n, -1 / 4.0, f_lo=50.0, n_oct=7.0, L=L, partials=((1, 1.0), (2, 0.4)), strands=(0.0, 0.5), gains=(1.0, 0.6))
    scr = np.zeros((2, n))
    for k in range(5):  # screeching clusters
        d = rng.uniform(1.0, 1.8)
        m = ns(d)
        x = np.linspace(0, 1, m)
        s = np.zeros(m)
        g0 = rng.choice([-1, 1]) * rng.uniform(150, 400)
        for nm in ("A5", "A#5", "E6", "F6"):
            fr = dsp.hz(nm) * dsp.cents(g0 * x + 30 * dsp.smooth(m, rng, 7, periodic=False))
            sv = dsp.saw(fr, m, rng.random()) * (0.6 + 0.4 * (0.5 + 0.5 * np.cos(TAU * 17 * np.arange(m) / SR)))
            s += sv
        s = dsp.bandpass(s, 1500, 8000, 2) * np.sin(np.pi * x) ** 1.2
        dsp.place(scr, dsp.pan(s, rng.uniform(-0.8, 0.8)), k * L / 5 + rng.uniform(0, 1.0), circular=True)
    met = np.zeros((2, n))
    for k in range(3):  # metal scrapes
        d = beat * rng.uniform(3, 5)
        m = ns(d)
        x = np.linspace(0, 1, m)
        s = np.zeros(m)
        for f in (1630, 2290, 3110, 4570, 6230):
            s += np.sin(TAU * np.cumsum(f * dsp.cents(rng.uniform(-200, 200) + 300 * x * rng.choice([-1, 1]))) / SR) * rng.uniform(0.3, 1.0)
        s = s * np.sin(np.pi * x) ** 1.5 + dsp.bandpass(rng.standard_normal(m), 2500, 8000, 2) * np.sin(np.pi * x) ** 1.5 * 0.4
        dsp.place(met, dsp.pan(s, np.linspace(-0.8, 0.8, m)), (k * 3 + 1.5) * 4 * beat / 2.2, circular=True)
    mix = drums * 1.0 + np.vstack([bass, bass]) / (np.max(np.abs(bass)) + 1e-9) * 0.4
    mix += np.vstack([sh, np.roll(sh, ns(0.007))]) / (np.max(np.abs(sh)) + 1e-9) * 0.3
    mix += _nrm(scr) * 0.22 + _nrm(met) * 0.12
    steel = np.zeros((2, n))  # stainless steel struck in the kitchen on the off-accents
    STEEL = [(410, 0.6, 0.8), (980, 0.5, 1.0), (1730, 0.4, 0.8), (2650, 0.3, 0.6), (3900, 0.2, 0.4), (5400, 0.15, 0.25)]
    for bar in range(10):
        for k in (3, 6) if bar % 2 else (5,):
            h = ins.modal_hit(rng, STEEL, size=rng.uniform(0.8, 1.3), hardness=0.9, vel=rng.uniform(0.5, 1.0), dur=0.9, noise=0.3)
            dsp.place(steel, dsp.pan(h, rng.uniform(-0.7, 0.7)), bar * 4 * beat + k * e8 + rng.normal(0, 0.004), circular=True)
    mix += _nrm(steel) * 0.16
    ir = dsp.make_ir(1.4, rng, stereo=True, predelay=0.015)
    mix = dsp.circ_reverb(mix, ir, wet=0.28, dry=0.9)
    return np.roll(dsp.dc_block(mix, circular=True), ns(0.02), axis=-1)  # downbeat 20 ms after the seam


def _bird_tit(rng):
    y = np.zeros(ns(0.5))
    for k, t0 in enumerate((0.0, 0.16)):
        m = ns(0.09)
        tt = np.arange(m) / SR
        f = (5200 if k == 0 else 3600) * (1 - 0.15 * tt / 0.09)
        dsp.place(y, np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * tt / 0.09) ** 2, t0)
    return y


def _bird_bulbul(rng):
    m = ns(0.5)
    tt = np.arange(m) / SR
    f = np.interp(tt, [0, 0.15, 0.3, 0.5], [2600, 3900, 3100, 2300]) * (1 + 0.03 * np.sin(TAU * 38 * tt))
    return np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * tt / 0.5) ** 1.5


def _crow(rng):
    y, _ = V.scream(0.32, rng, [(0, 480), (0.05, 560), (0.32, 420)], [(0, 0), (0.03, 1), (0.25, 0.7), (0.32, 0)],
                    vowel="a", chaos_pts=[(0, 3.9), (0.32, 3.9)], breath=0.6, rasp=1.0, scale=1.2, oq=0.45)
    return y




# --------------------------------------------------------------------------
# music.safe - 40 s: outside with the medium; the jingle's melody in D minor, sparse, rain dripping off the canopy
# --------------------------------------------------------------------------
def safe(rng, i=1):
    import jingle as J
    L = 40.0
    n = ns(L)
    ev = np.zeros((2, n))
    # the jingle's head and its cadence, transposed to D minor (F minor down a minor third), very slowly, with gaps
    phrase = [(b, J.to_minor(nm), d) for b, nm, d in J.MELODY[:9]] + [(b - 12, J.to_minor(nm), d) for b, nm, d in J.MELODY[23:]]
    beat = 1.05
    t0 = 1.0
    for k, (b, nm, d) in enumerate(phrase):
        f = dsp.hz(nm, -300 + rng.uniform(-14, 8))
        tb = t0 + b * beat + (4.0 if b >= 16 else 0.0)
        if tb > L - 3:
            break
        p = ins.piano(f, 6.0, rng, vel=rng.uniform(0.45, 0.6), unison_c=2.5, bright=0.3)
        dsp.place(ev, dsp.pan(p, rng.uniform(-0.3, 0.3)), tb, circular=True)
        if rng.random() < 0.4:  # a music-box ghost an octave up, a little sour
            tn = ins.tine(f * 2 * dsp.cents(rng.uniform(18, 35)), rng, vel=0.22)
            dsp.place(ev, dsp.pan(tn, rng.uniform(-0.6, 0.6)), tb + 0.012, circular=True)
    for tb, nm in ((1.0, "D3"), (9.4, "A#2"), (17.8, "G2"), (26.2, "A2"), (33.5, "D2")):
        p = ins.piano(dsp.hz(nm, rng.uniform(-8, 4)), 7.0, rng, vel=0.38, bright=0.22)
        dsp.place(ev, dsp.pan(p, -0.2), tb, circular=True)
    ev = _nrm(ev)
    pad = loop_strings([dsp.hz("D3"), dsp.hz("A3"), dsp.hz("F4")], L, rng, env=_lfo(n, 2, rng, 0.0, 1.0, 2.0),
                       voices=2, detune=6, vib=4, bright=0.1)
    from sfx_beds import _drip
    rain = np.zeros((2, n))  # the canopy and the car roof still dripping
    for s_ in range(3):
        per, pn, f0 = rng.uniform(0.9, 2.2), rng.uniform(-0.8, 0.8), rng.uniform(700, 1200)
        tt = rng.uniform(0, per)
        while tt < L:
            d = _drip(rng, f0 * rng.uniform(0.96, 1.04), rng.uniform(0.5, 1.0))
            dsp.place(rain, dsp.pan(dsp.lowpass(d, 7000, 2), pn), tt, circular=True)
            tt += per * rng.uniform(0.85, 1.2)
    out = ev + _nrm(pad) * 0.07 + _nrm(rain) * 0.06
    air = np.vstack([dsp.colored(n, rng, -3.0, lo=150, hi=4000) for _ in range(2)])
    out += air * 0.004
    ir = dsp.make_ir(5.0, rng, stereo=True, predelay=0.035, hf_ratio=0.3)
    out = dsp.circ_reverb(out, ir, wet=0.5, dry=0.8)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.exorcism - 60 s (no loop): singing bowls, a chant drone over a wooden fish, rising light, the release
# --------------------------------------------------------------------------
def _bowl(rng, f0, dur, vel=1.0):
    """A large singing bowl struck with a padded mallet: long beating partials (split modes)."""
    return ins.bell_partials(f0, dur, rng, [1.0, 2.71, 5.03, 7.9, 11.2], [1.0, 0.55, 0.3, 0.12, 0.05],
                             [14.0, 8.0, 4.5, 2.5, 1.4], beat=(0.4, 1.6), attack=0.004) * vel


def _mokugyo(rng, vel=1.0):
    """The wooden fish: a hollow, round knock."""
    return ins.modal_hit(rng, [(420, 0.09, 1.0), (980, 0.05, 0.45), (1650, 0.03, 0.25), (2400, 0.02, 0.1)],
                         size=1.0, hardness=0.6, vel=vel, dur=0.35, noise=0.2)


def _chant_drone(rng, dur, f0, vowels, voices=4, table=V.VM, scale=0.93, breath=0.3, level_pts=None):
    n = ns(dur)
    out = np.zeros((2, n))
    for k in range(voices):
        f = f0 * dsp.cents(rng.normal(0, 9) + 6 * dsp.smooth(n, rng, 0.4, periodic=False))
        f = f * dsp.cents(7 * np.sin(TAU * rng.uniform(4.6, 5.6) * np.arange(n) / SR + rng.random() * 6))
        amp = 0.75 + 0.25 * np.sin(np.pi * np.arange(n) / n * rng.integers(2, 6) + rng.random() * 6) ** 2
        if level_pts:
            amp = amp * dsp.env_pts(n, level_pts)
        keys = [(dur * j / (len(vowels) - 1), V.fs(v, table, scale)) for j, v in enumerate(vowels)]
        v = V.voice(f, amp, keys, rng, tilt=-9, fmax=5000, jitter=0.008, shimmer=0.06, breath=breath)
        out += dsp.pan(v / (np.std(v) + 1e-9), rng.uniform(-0.6, 0.6))
    return out


def exorcism(rng, i=1):
    import jingle as J
    dur = 60.0
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    rel = 47.0  # the release
    ch = _chant_drone(rng, rel + 1.5, dsp.hz("D2"), ["o", "u", "o", "a", "o", "u"], voices=4,
                      level_pts=[(0, 0), (3, 0.0), (8, 0.7), (30, 0.9), (44, 1.0), (rel, 1.0), (rel + 1.5, 0)])
    ch2 = _chant_drone(rng, rel + 1.5, dsp.hz("A2", -6), ["u", "o", "a", "o"], voices=2,
                       level_pts=[(0, 0), (12, 0.0), (18, 0.6), (rel, 0.9), (rel + 1.5, 0)])
    hi = _chant_drone(rng, rel + 1.5, dsp.hz("D4", 8), ["a", "o", "a", "u"], voices=1, table=V.VF, scale=1.0,
                      breath=0.6, level_pts=[(0, 0), (20, 0.0), (26, 0.5), (rel, 0.8), (rel + 1.5, 0)])
    dsp.place(out, _nrm(ch) * 0.32 + _nrm(ch2) * 0.15 + _nrm(hi) * 0.06, 0.0)
    tt = 6.0  # the wooden fish keeps the chant's pulse, quickening toward the release
    per = 0.62
    while tt < rel - 0.2:
        dsp.place(out, dsp.pan(_mokugyo(rng, rng.uniform(0.8, 1.0)), 0.25) * 0.22, tt)
        tt += per
        per = max(0.36, per * 0.995)
    for tb, f0, v in ((0.5, 196.0, 1.0), (14.0, 196.0, 0.8), (28.0, 220.0, 0.85), (38.0, 233.1, 0.9)):
        b = _bowl(rng, f0, 14.0, v)
        dsp.place(out, np.vstack([b, np.roll(b, ns(0.004))]) * 0.22, tb)
        rn = ins.rin(rng, f0 * 5.62, 6.0, 0.6)
        dsp.place(out, dsp.pan(rn, 0.4) * 0.12, tb + 0.02)
    m = ns(rel - 24.0)  # light rising: glassy partials climbing out of the drone, denser and brighter
    tt2 = np.arange(m) / SR
    shim = np.zeros(m)
    for k in range(9):
        f_start = dsp.hz("D5") * 2 ** (k / 4.0)
        f = f_start * 2 ** (tt2 / (m / SR) * rng.uniform(0.6, 1.1))
        g = np.clip(f / 9000.0, 0, 1)
        shim += np.sin(TAU * np.cumsum(f) / SR + rng.random() * 6) * (1 - g) * (0.5 + 0.5 * np.sin(TAU * rng.uniform(0.3, 0.9) * tt2) ** 2)
    shim *= dsp.env_pts(m, [(0, 0), (m / SR * 0.6, 0.5), (m / SR, 1.0)]) ** 1.5
    dsp.place(out, np.vstack([shim, np.roll(shim, ns(0.011))]) / (np.max(np.abs(shim)) + 1e-9) * 0.07, 24.0)
    sw = np.clip((t - 20.0) / (rel - 20.0), 0, 1) ** 2 * (t < rel)  # the low swell under it all
    sub = dsp.sub_rumble(n, 36.71, rng=rng, am_rate=5.0, am_depth=0.4)
    out += np.vstack([sub, sub]) * 0.06 * sw
    # the release: the chant stops dead; a wide F major chord of light (the jingle's key), bowls, then stillness
    chord = ["F3", "C4", "A4", "E5", "G5", "C6", "A6"]
    m = ns(dur - rel)
    pad = ins.strings([J.hz(x) for x in chord], dur - rel, rng, env=dsp.env_pts(m, [(0, 0), (0.4, 1.0), (4.0, 0.7), (dur - rel, 0)]),
                      voices=3, detune=6, vib_depth=4, vib_rate=4.8, bright=0.35)
    dsp.place(out, _nrm(pad) * 0.3, rel)
    for k, nm in enumerate(("F5", "A5", "C6", "F6")):
        dsp.place(out, dsp.pan(ins.vibraphone(J.hz(nm), 7.0, rng, vel=0.5, depth=0.0, hard=0.3), -0.4 + 0.27 * k) * 0.25, rel + 0.05 + 0.12 * k)
    b = _bowl(rng, 174.6, dur - rel, 1.0)
    dsp.place(out, np.vstack([b, np.roll(b, ns(0.005))]) * 0.28, rel)
    rn = ins.rin(rng, 1175.0, 9.0, 0.9)
    dsp.place(out, dsp.pan(rn, 0.3) * 0.2, rel + 5.0)
    ir = dsp.make_ir(4.5, rng, stereo=True, predelay=0.04, hf_ratio=0.35)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.42, dry=0.85), n)
    return dsp.fade(dsp.highpass(out, 28, 2), 0.5, 4.0)
