"""music.* cues (stereo).

Tonal plan, so that the layers the client mixes at runtime (dread, tension,
danger, binaural, chase, oyama) clash on purpose without turning to mush:
  dread    : low D drone (D1/D2/A2) with slowly beating partner partials, a
             6 Hz binaural theta pair at A2 (110 / 116 Hz), sub swells
  binaural : D3 carriers 146.8 / 152.8 Hz (the right ear sits a sour 70 cents
             above D3) + D2 pair 4.5 Hz apart
  tension  : micro-tonal string clusters around D - Eb - Ab (minor second,
             tritone), irregular heartbeat
  danger   : endless Shepard rise (key independent, tritone-paired strands) +
             rough 52 Hz AM strings on D/Eb/A + ticking
  chase    : D/Eb pumping bass, falling Shepard, screeching clusters
  oyama    : D/Eb/Ab low brass clusters, shō, a chant drone
Loops are circular (loop-quantised oscillators, circular placement /
reverb); layers that cannot be periodic are rendered longer and crossfaded
into their start."""
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


SAFE = [  # the safe motif (D minor, sparse): (t, note, vel)
    (0.5, "A4", 0.6), (1.6, "D5", 0.7), (2.5, "E5", 0.55), (3.3, "F5", 0.75),
    (7.5, "E5", 0.6), (8.6, "D5", 0.55), (9.9, "A4", 0.6),
    (14.5, "A#4", 0.6), (15.4, "A4", 0.5), (16.2, "G4", 0.55), (17.4, "F4", 0.65),
    (22.0, "E4", 0.55), (23.2, "F4", 0.5), (24.0, "A4", 0.6), (25.5, "C#5", 0.6),
    (30.5, "D5", 0.6), (31.8, "F5", 0.5), (33.0, "E5", 0.45),
]
SAFE_BASS = [(0.5, "D3", 0.4), (0.52, "A3", 0.3), (7.5, "A#2", 0.4), (14.5, "G2", 0.4), (22.0, "A2", 0.45), (30.5, "D2", 0.45)]


# --------------------------------------------------------------------------
# music.dread - 64 s base score
# --------------------------------------------------------------------------
def dread(rng, i=1):
    L = 64.0
    n = ns(L)
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
    br = np.vstack([dsp.colored(n, rng, -6.0, lo=25, hi=220) for _ in range(2)])
    out += br * 0.22 * _lfo(n, 3, rng, 0.2, 1.0, 1.5)
    out = _nrm(out)
    met = np.zeros((2, n))
    for k in range(3):
        d = rng.uniform(6.0, 9.0)
        bm = _bowed_metal(rng, d, rng.uniform(410, 640), 1.0)
        dsp.place(met, dsp.pan(bm, rng.uniform(-0.8, 0.8)), k * L / 3 + rng.uniform(2, 10), circular=True)
    out += _nrm(met) * 0.1
    ir = dsp.make_ir(4.5, rng, stereo=True, predelay=0.04, hf_ratio=0.35)
    out = dsp.circ_reverb(out, ir, wet=0.45, dry=0.85)
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
    out += np.vstack([sub, sub]) * 0.15
    ir = dsp.make_ir(3.0, rng, stereo=True, predelay=0.03)
    out = dsp.circ_reverb(out, ir, wet=0.4, dry=0.85)
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
    ticks = np.zeros(n)
    acc = rng.random(64) < 0.3
    for k in range(64):
        m = ns(0.02)
        c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0012 * SR))
        c = dsp.reson(c, 3200 if k % 2 == 0 else 2500, 6, 2.0)
        dsp.place(ticks, c * (1.0 if acc[k] else 0.45), k * L / 64, circular=True)
    out += np.vstack([ticks * 0.9, ticks]) * 0.18 / (np.max(np.abs(ticks)) + 1e-9)
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=6.0, am_depth=0.6)
    out += np.vstack([sub, sub]) * 0.22
    ir = dsp.make_ir(2.0, rng, stereo=True, predelay=0.02)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.9)
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
    ir = dsp.make_ir(1.4, rng, stereo=True, predelay=0.015)
    mix = dsp.circ_reverb(mix, ir, wet=0.28, dry=0.9)
    return np.roll(dsp.dc_block(mix, circular=True), ns(0.02), axis=-1)  # downbeat 20 ms after the seam


# --------------------------------------------------------------------------
# music.safe - 40 s, fragile detuned piano / music box, long reverb
# --------------------------------------------------------------------------
def safe(rng, i=1):
    L = 40.0
    n = ns(L)
    ev = np.zeros((2, n))
    for t0, nm, v in SAFE:
        f = dsp.hz(nm, rng.uniform(-18, 12))
        p = ins.piano(f, 6.0, rng, vel=v, unison_c=2.5, bright=0.35)
        dsp.place(ev, dsp.pan(p, rng.uniform(-0.35, 0.35)), t0, circular=True)
        if rng.random() < 0.45:  # a sour music-box ghost an octave up
            tn = ins.tine(f * 2 * dsp.cents(rng.uniform(15, 35)), rng, vel=0.25)
            dsp.place(ev, dsp.pan(tn, rng.uniform(-0.6, 0.6)), t0 + 0.01, circular=True)
    for t0, nm, v in SAFE_BASS:
        p = ins.piano(dsp.hz(nm, rng.uniform(-10, 6)), 7.0, rng, vel=v, bright=0.25)
        dsp.place(ev, dsp.pan(p, -0.2), t0, circular=True)
    ev = _nrm(ev)
    pad = loop_strings([dsp.hz("D3"), dsp.hz("A3"), dsp.hz("F4")], L, rng, env=_lfo(n, 2, rng, 0.0, 1.0, 2.0),
                       voices=2, detune=6, vib=4, bright=0.1)
    out = ev + _nrm(pad) * 0.08
    air = np.vstack([dsp.colored(n, rng, -3.0, lo=150, hi=4000) for _ in range(2)])
    out += air * 0.004
    ir = dsp.make_ir(5.5, rng, stereo=True, predelay=0.035, hf_ratio=0.3)
    out = dsp.circ_reverb(out, ir, wet=0.55, dry=0.8)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.oyama - 32 s final act
# --------------------------------------------------------------------------
def _chant(dur, rng):
    n = ns(dur)
    out = np.zeros((2, n))
    for f, vw in ((73.42, ("o", "u", "o")), (73.42 * dsp.cents(14), ("u", "o", "a")), (103.83, ("o", "a", "u")),
                  (110.0, ("u", "o", "u")), (146.83, ("o", "o", "a"))):
        ff = f * dsp.cents(10 * np.sin(TAU * rng.uniform(4.2, 5.4) * np.arange(n) / SR + rng.random() * 6) + 5 * dsp.smooth(n, rng, 0.5, periodic=False))
        amp = 0.6 + 0.4 * np.sin(np.pi * np.arange(n) / n * rng.integers(2, 5) + rng.random() * 6) ** 2
        keys = [(0, V.fs(vw[0], V.VM, 0.92)), (dur / 2, V.fs(vw[1], V.VM, 0.92)), (dur, V.fs(vw[2], V.VM, 0.92))]
        v = V.voice(ff, amp, keys, rng, tilt=-9, fmax=5000, jitter=0.008, shimmer=0.06, breath=0.25)
        out += dsp.pan(v / (np.std(v) + 1e-9), rng.uniform(-0.7, 0.7))
    return out


def _sho_layer(dur, rng):
    chords = []
    seq = [["D4", "E4", "G#4", "A4", "C#5", "D5"], ["D#4", "F4", "A4", "A#4", "D5", "D#5"],
           ["C#4", "D4", "G#4", "A4", "D#5", "E5"], ["D4", "D#4", "A4", "A#4", "D5", "E5"]]
    k = 0
    t = 0.0
    while t < dur:
        fr = [dsp.hz(x, rng.uniform(-12, 12)) for x in seq[k % len(seq)]]
        chords.append((t, t + 8.6, fr, 1.0))
        t += 8.0
        k += 1
    return ins.sho(chords, dur, rng)


def oyama(rng, i=1):
    L = 32.0
    n = ns(L)
    t = np.arange(n) / SR
    env = np.zeros(n)
    for c in (0.0, 11.0, 21.5):  # three huge swells
        env += np.exp(-((t - c - 3.5) % L) / 4.0) * np.clip(((t - c) % L) / 3.5, 0, 1) ** 2 * (((t - c) % L) < 10.5)
    env = dsp.circ(lambda x: dsp.lowpass0(x, 3.0), np.clip(env, 0, 1)) * 0.85 + 0.15
    br = loop_brass([36.71, 38.89, 73.42, 77.78, 103.83, 110.0], L, rng, env)
    out = _nrm(br) * 0.8
    ch = _xf_loop(_chant, L, 4.0, rng)
    out += _nrm(ch) * 0.3
    sho = _xf_loop(_sho_layer, L, 4.0, rng)
    out += _nrm(sho) * 0.2 * _lfo(n, 2, rng, 0.4, 1.0)[None, :]
    tk = np.zeros((2, n))
    for t0, v in ((0.0, 1.0), (2.4, 0.7), (4.0, 0.9), (8.0, 1.0), (11.0, 1.0), (13.2, 0.6), (16.0, 0.95), (19.6, 0.7),
                  (21.5, 1.0), (24.0, 0.8), (26.3, 0.6), (28.0, 0.95)):
        h = ins.taiko(rng, f0=rng.uniform(46, 54), vel=v, decay=1.0, dur=2.8, size=1.7)
        dsp.place(tk, dsp.pan(dsp.lowpass(h, 1600, 2), rng.uniform(-0.3, 0.3)), t0, circular=True)
    tk = dsp.circ_reverb(tk, dsp.make_ir(3.8, rng, stereo=True, predelay=0.06, hf_ratio=0.3), wet=0.9, dry=0.3)
    out += _nrm(tk) * 0.55
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=6.0, am_depth=0.5)
    out += np.vstack([sub, sub]) * 0.3 * env
    ir = dsp.make_ir(4.0, rng, stereo=True, predelay=0.04, hf_ratio=0.35)
    out = dsp.circ_reverb(out, ir, wet=0.4, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.binaural - 32 s pure theta bed
# --------------------------------------------------------------------------
def binaural(rng, i=1):
    L = 32.0
    n = ns(L)
    out = dsp.binaural(149.83, 6.0, n, L=L, rng=rng) * 1.0  # 146.83 L / 152.83 R
    out += dsp.binaural(75.67, 4.5, n, L=L, rng=rng) * 0.6   # 73.42 L / 77.92 R
    out *= _lfo(n, 2, rng, 0.7, 1.0)[None, :]
    pn = np.vstack([dsp.colored(n, rng, -3.0, lo=40, hi=6000) for _ in range(2)])
    out += pn * 0.012
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.title - 48 s: wind, a distant bear bell, a lonely high tone, drone, piano
# --------------------------------------------------------------------------
def title(rng, i=1):
    L = 48.0
    n = ns(L)
    out = np.zeros((2, n))
    for c in range(2):
        g = dsp.smooth(n, rng, 0.08)
        g = (g - g.min()) / (g.max() - g.min())
        out[c] += dsp.colored(n, rng, -4.0, lo=120, hi=2500) * (0.2 + 0.8 * g ** 2) * 0.08
    for f, a, bt in ((73.42, 1.0, 0.09), (110.0, 0.4, 0.13), (36.71, 0.35, 0.05)):
        for c in range(2):
            out[c] += a * 0.12 * (_tone(f, L, n, rng) + 0.7 * _tone(f + bt * (1 + 0.4 * c), L, n, rng))
    hi = (_tone(1174.66, L, n, rng) + 0.5 * _tone(1174.66 + 0.31, L, n, rng)) * _lfo(n, 2, rng, 0.0, 1.0, 3.0)
    out += np.vstack([hi, np.roll(hi, ns(0.02))]) * 0.025
    ev = np.zeros((2, n))
    for t0, nm, v in [(4.0, "A4", 0.5), (5.2, "D5", 0.6), (6.1, "E5", 0.45), (7.0, "F5", 0.6),
                      (26.0, "A#4", 0.5), (27.0, "A4", 0.45), (27.9, "G4", 0.5), (29.2, "F4", 0.55), (31.5, "E4", 0.45)]:
        p = ins.piano(dsp.hz(nm, rng.uniform(-15, 10)), 6.0, rng, vel=v, unison_c=2.5, bright=0.3)
        dsp.place(ev, dsp.pan(p, rng.uniform(-0.3, 0.3)), t0, circular=True)
    out += _nrm(ev) * 0.35
    for t0 in (12.0, 37.0):  # someone's bear bell, far away, walking
        strikes = []
        tt = 0.0
        while tt < 5.0:
            strikes.append((tt, rng.uniform(0.5, 1.0)))
            tt += rng.uniform(0.5, 0.62)
        b = ins.bear_bell(rng, 6.5, strikes, base=1600)
        b = dsp.lowpass(b, 3200, 2) * dsp.env_pts(len(b), [(0, 0), (1.5, 1), (4.5, 1), (6.5, 0)])
        dsp.place(out, dsp.pan(b, rng.uniform(-0.7, 0.7)) * 0.02, t0, circular=True)
    ir = dsp.make_ir(4.5, rng, stereo=True, predelay=0.04, hf_ratio=0.3)
    out = dsp.circ_reverb(out, ir, wet=0.5, dry=0.8)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.ending_good - 50 s, dawn (no loop)
# --------------------------------------------------------------------------
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


def ending_good(rng, i=1):
    dur = 50.0
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    wind = np.vstack([dsp.colored(n, rng, -4.0, lo=120, hi=2500) for _ in range(2)])
    out += wind * 0.05 * dsp.env_pts(n, [(0, 1.0), (12, 0.6), (40, 0.3), (dur, 0.2)])
    dr = (np.sin(TAU * 73.42 * t) + 0.6 * np.sin(TAU * 73.53 * t)) * dsp.env_pts(n, [(0, 0), (2, 1), (14, 0.6), (30, 0.2), (dur, 0.2)])
    out += np.vstack([dr, dr]) * 0.12
    chords = [(8.0, ["D2", "A2", "F#3", "A3", "E4"]), (16.0, ["G2", "D3", "B3", "D4", "A4"]),
              (24.0, ["A2", "E3", "A3", "D4", "E4"]), (32.0, ["D2", "A2", "F#3", "D4", "F#4", "A4"])]
    for k, (t0, names) in enumerate(chords):
        d = 9.5 if k < 3 else dur - t0
        m = ns(d)
        env = dsp.env_pts(m, [(0, 0), (3.0, 1.0), (d - 2.0, 0.85), (d, 0)])
        pad = ins.strings([dsp.hz(x) for x in names], d, rng, env=env, voices=3, detune=7, vib_depth=5, vib_rate=4.6,
                          bright=0.15 + 0.08 * k)
        dsp.place(out, dsp.lowpass(pad, 1600 + 400 * k, 2) / (np.max(np.abs(pad)) + 1e-9) * (0.22 + 0.05 * k), t0)
    for t0, nm, v in [(18.5, "A4", 0.5), (19.6, "D5", 0.55), (20.5, "E5", 0.45), (21.3, "F#5", 0.6),
                      (26.0, "E5", 0.5), (27.1, "D5", 0.45), (28.4, "A4", 0.5), (34.0, "D5", 0.5), (35.2, "F#5", 0.45),
                      (36.4, "A5", 0.5)]:
        p = ins.piano(dsp.hz(nm, rng.uniform(-4, 4)), 6.0, rng, vel=v, bright=0.4)
        dsp.place(out, dsp.pan(p, rng.uniform(-0.3, 0.3)) * 0.35, t0)
    for _ in range(14):  # dawn birds, and a crow far away
        t0 = rng.uniform(6.0, 46.0)
        b = _bird_tit(rng) if rng.random() < 0.6 else _bird_bulbul(rng)
        dsp.place(out, dsp.pan(dsp.lowpass(b, 7000, 2), rng.uniform(-0.9, 0.9)) * 0.03 * min(1.0, (t0 - 4) / 10), t0)
    for t0 in (13.0, 13.5, 14.1, 38.0, 38.55):
        c = _crow(rng)
        dsp.place(out, dsp.pan(dsp.lowpass(c, 3000, 2), 0.6) * 0.04, t0)
    for t0, nm in [(44.0, "A5"), (45.4, "D6"), (46.9, "E6"), (48.6, "F6")]:  # ...and the motif comes back in minor
        tn = ins.tine(dsp.hz(nm, rng.uniform(-25, -10)), rng, vel=0.22)
        dsp.place(out, dsp.pan(tn, 0.35), t0)
    sw = dsp.sub_rumble(n, 38.89, rng=rng, am_rate=6.0, am_depth=0.5) * dsp.env_pts(n, [(0, 0), (44, 0), (49, 1.0), (dur, 0.6)])
    out += np.vstack([sw, sw]) * 0.12
    ir = dsp.make_ir(3.8, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.reverb(out, ir, wet=0.42, dry=0.8)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 30, 2), 1.0, 2.5)


# --------------------------------------------------------------------------
# music.ending_bad - 30 s 神隠し: the safe motif on a dying music box
# --------------------------------------------------------------------------
def ending_bad(rng, i=1):
    dur = 30.0
    n = ns(dur)
    out = np.zeros((2, n))
    t = 0.8
    speed = 1.0
    for k, (t0, nm, v) in enumerate(SAFE):
        if k in (5, 9, 13):
            continue  # broken tines
        f = dsp.hz(nm, -15 - 40 * (1 - speed) + rng.uniform(-10, 10)) * 2
        tn = ins.tine(f * speed ** 0.5, rng, vel=0.6 * speed ** 0.3)
        dsp.place(out, dsp.pan(tn, 0.15), t)
        if k + 1 < len(SAFE):
            t += (SAFE[k + 1][0] - t0) * 0.42 / speed
        speed *= 0.93
        if t > 24.0:
            break
    m = ns(0.05)  # the spring's last click
    dsp.place(out, np.vstack([dsp.bandpass(rng.standard_normal(m), 2000, 6000) * np.exp(-np.arange(m) / (0.003 * SR))] * 2) * 0.2, t + 1.2)
    for t_hit in (6.0, 14.5, 22.0):  # reversed swells
        src = ins.strings([dsp.hz(x) for x in ("D3", "D#3", "A3", "G#4")], 1.2, rng, env=dsp.env_pts(ns(1.2), [(0, 1), (1.2, 0)]), voices=2, bright=0.4)
        rv = dsp.reverse(dsp.reverb(src, dsp.make_ir(3.0, rng, stereo=True), wet=1.0, dry=0.1))
        dsp.place_end(out, _nrm(rv) * 0.25, t_hit)
    tt = np.arange(n) / SR
    wind = np.vstack([dsp.colored(n, rng, -4.0, lo=120, hi=3000) for _ in range(2)])
    g = dsp.env_pts(n, [(0, 0.3), (15, 0.6), (26, 1.0), (dur, 0.0)]) * (0.6 + 0.4 * np.abs(dsp.smooth(n, rng, 0.2, periodic=False)))
    out += wind * 0.06 * g
    ir = dsp.make_ir(4.0, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.reverb(out, ir, wet=0.5, dry=0.75)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 35, 2), 0.3, 3.0)
