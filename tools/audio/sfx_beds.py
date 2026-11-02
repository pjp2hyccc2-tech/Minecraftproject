"""amb.* looping beds.  Everything is built circularly (periodic FFT noise,
loop-quantised oscillators and LFOs, events placed modulo the loop length,
circular reverb / filters), so every loop seam is sample-continuous.

The night forest is *quiet*: beds sit far below the one-shots, with faint
psychoacoustic undercurrents (binaural wind tones, a sub-audible 30-40 Hz
swell, edge-of-hearing ringing) rather than anything overtly musical."""
import numpy as np

import dsp
import instruments as ins
from dsp import SR, TAU, ns


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0):
    ph = cycles * np.arange(n) / n + rng.random()
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * ph)) ** shape


def _norm01(x):
    return (x - x.min()) / (x.max() - x.min() + 1e-12)


def _gusts(n, rng, base=None, rate=0.12, mix=0.6, floor=0.08, power=2.2):
    g = dsp.smooth(n, rng, rate)
    if base is not None:
        g = mix * base + (1 - mix) * g
    return floor + (1 - floor) * _norm01(g) ** power


def _cr(x, fn, pre=2.0):
    return dsp.circ(fn, x, ns(pre))


def _place_circ(buf, x, t):
    return dsp.place(buf, x, t, circular=True)


# --------------------------------------------------------------------------
# amb.forest - 60 s: canopy wind swells, far stream, a rare creak
# --------------------------------------------------------------------------
def forest(rng, i=1):
    L = 60.0
    n = ns(L)
    out = np.zeros((2, n))
    weather = dsp.smooth(n, rng, 0.05)
    gsum = np.zeros(n)
    for k in range(6):
        p = -0.95 + 1.9 * k / 5 + rng.uniform(-0.08, 0.08)
        g = _gusts(n, rng, weather, rate=0.11, mix=0.55, floor=0.06, power=2.4)
        gsum += g / 6
        dark = dsp.colored(n, rng, -4.0, lo=160, hi=1500)
        bright = dsp.colored(n, rng, -1.5, lo=1100, hi=6500)  # dead-needle hiss high in the cedars
        flutter = 0.55 + 0.45 * np.abs(dsp.smooth(n, rng, 22.0))  # needles fluttering: foliage, not static
        s = dark * g * (1.0 - 0.35 * g) + bright * flutter * g ** 1.7 * 0.5
        out += dsp.pan(s, p) * 0.22
    # low air over the ridges
    low = dsp.colored(n, rng, -6.0, lo=22, hi=110)
    out += np.vstack([low, np.roll(low, ns(0.4))]) * 0.18 * (0.5 + 0.5 * gsum)
    # far stream in the gully (left, behind the trees)
    st = ins.water_texture(n, rng, L=L, density=140, noise=0.6, splash=0.15)
    st = _cr(st, lambda x: dsp.lowpass(x, 1500, 2))
    out += dsp.pan(st / (np.std(st) + 1e-9), -0.55) * 0.045
    # rare deep creak of a trunk, far
    for t0, p in ((rng.uniform(12, 20), rng.uniform(-0.8, -0.2)), (rng.uniform(38, 48), rng.uniform(0.2, 0.8))):
        d = rng.uniform(1.6, 2.4)
        c = ins.creak(d, rng, [(0, 9), (d * 0.45, 26), (d, 8)], [(0, 0), (0.3, 0.8), (d * 0.8, 0.6), (d, 0)],
                      pitch=0.42, jitter=0.35, q_scale=1.3, tonal=0.25)
        c = dsp.lowpass(c, 1400, 2)
        _place_circ(out, dsp.pan(c / (np.max(np.abs(c)) + 1e-9), p) * 0.07, t0)
    # needle / leaf drops: a few faint ticks
    for _ in range(9):
        g = ins.crunch(0.06, rng, lambda t: 600 * np.exp(-t / 0.01), lo=1500, hi=7000)
        _place_circ(out, dsp.pan(g, rng.uniform(-1, 1)) * 0.012, rng.uniform(0, L))
    # subliminal: binaural wind tone (theta 5.5 Hz) riding the gusts, and a sub swell
    bt = dsp.binaural(421.0, 5.5, n, L=L, rng=rng) * (0.25 + 0.75 * gsum ** 1.5)[None, :]
    out += bt * 0.02
    sub = dsp.sub_rumble(n, 33.0, L=L, rng=rng, am_rate=6.0, am_depth=0.5) * _lfo(n, 2, rng, 0.0, 1.0, 3.0)
    out += np.vstack([sub, sub]) * 0.03
    ir = dsp.make_ir(1.5, rng, stereo=True, predelay=0.015, hf_ratio=0.45)
    out = dsp.circ_reverb(out, ir, wet=0.38, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.insects - 40 s autumn chorus (鈴虫, エンマコオロギ, カンタン, カネタタキ, ツヅレサセコオロギ)
# --------------------------------------------------------------------------
def _bouts(rng, L, sing, pause, start=None):
    """Random singing bouts over the loop: [(t0, t1)]."""
    t = start if start is not None else -rng.uniform(0, sing[1])
    out = []
    while t < L:
        d = rng.uniform(*sing)
        out.append((t, t + d))
        t += d + rng.uniform(*pause)
    return out


def _species_events(rng, sp, L):
    """List of (t, phrase) for one individual of a species over the loop."""
    ev = []
    f = sp["f"] * rng.uniform(0.96, 1.04)
    if sp["kind"] == "trill":
        for t0, t1 in _bouts(rng, L, sp["sing"], sp["pause"]):
            d = t1 - t0
            pulses = int(d * sp["rate"])
            env = np.sin(np.pi * np.linspace(0, 1, 64)) ** 0.6 * (0.8 + 0.2 * np.sin(np.linspace(0, 9, 64)))
            ph = ins.insect_phrase(rng, f, pulses, sp["rate"] * rng.uniform(0.97, 1.03), sp["duty"], sp["drop"], env)
            ev.append((t0, ph))
        return ev
    period = sp["period"] * rng.uniform(0.9, 1.1)
    for t0, t1 in _bouts(rng, L, sp["sing"], sp["pause"]):
        t = t0
        while t < t1:
            ph = sp["make"](rng, f)
            ev.append((t, ph))
            t += period * rng.uniform(1 - sp["jit"], 1 + sp["jit"])
    return ev


def _suzumushi(rng, f):
    p = rng.integers(9, 14)
    return ins.insect_phrase(rng, f, p, rng.uniform(35, 40), 0.78, 0.015, env=[0.35, 0.9, 1.0, 0.85, 0.5], shape=1.1)


def _emma(rng, f):
    a = ins.insect_phrase(rng, f, rng.integers(3, 5), 28, 0.55, 0.03, env=[0.7, 1.0, 0.9])
    b = ins.insect_phrase(rng, f * 0.99, rng.integers(5, 9), 31, 0.65, 0.025, env=[1.0, 0.9, 0.6, 0.3])
    y = np.zeros(len(a) + ns(0.05) + len(b))
    dsp.place(y, a, 0)
    dsp.place(y, b, len(a) / SR + 0.05)
    return y


def _kanetataki(rng, f):
    return ins.insect_phrase(rng, f, 1, 90, 0.85, 0.04, shape=2.0, harm=0.0)


def _tsuzure(rng, f):
    return ins.insect_phrase(rng, f, 3, 24, 0.5, 0.03, env=[0.8, 1.0, 0.8])


SPECIES = {
    "suzumushi": dict(kind="phrase", f=4400, make=_suzumushi, period=1.05, jit=0.08, sing=(8, 22), pause=(1.5, 6)),
    "emma": dict(kind="phrase", f=4750, make=_emma, period=1.9, jit=0.25, sing=(10, 30), pause=(2, 7)),
    "kantan": dict(kind="trill", f=2900, rate=46, duty=0.62, drop=0.02, sing=(6, 13), pause=(5, 11)),
    "kanetataki": dict(kind="phrase", f=7300, make=_kanetataki, period=0.36, jit=0.12, sing=(2, 5), pause=(3, 7)),
    "tsuzure": dict(kind="phrase", f=5200, make=_tsuzure, period=0.27, jit=0.05, sing=(5, 14), pause=(2, 6)),
}


def insects(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    cast = ["suzumushi"] * 5 + ["emma"] * 4 + ["kantan"] * 1 + ["kanetataki"] * 3 + ["tsuzure"] * 3
    for sp_name in cast:
        sp = SPECIES[sp_name]
        d = rng.uniform(1.0, 4.0)
        tr = np.zeros(n)
        for t0, ph in _species_events(rng, sp, L):
            _place_circ(tr, ph, t0)
        fc = 15000.0 / d ** 0.85
        tr = _cr(tr, lambda x: dsp.lowpass(x, fc, 2), 0.2)
        gain = (1.0 / d) * {"kanetataki": 0.55, "kantan": 0.9, "tsuzure": 0.7}.get(sp_name, 1.0)
        out += dsp.pan(tr, rng.uniform(-0.85, 0.85)) * gain
    # a very faint floor so the silence after the chorus stops is *heard*
    fl = np.vstack([dsp.colored(n, rng, -3.0, lo=200, hi=6000) for _ in range(2)])
    out += fl * 0.004
    out += dsp.binaural(333.0, 6.0, n, L=L, rng=rng) * 0.006 * _lfo(n, 3, rng, 0.3, 1.0)[None, :]  # theta under the chorus
    ir = dsp.make_ir(1.1, rng, stereo=True, predelay=0.012, hf_ratio=0.5)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.wind_ridge - 40 s: strong cold wind, whistling in the steel lattice,
# power-line corona buzz
# --------------------------------------------------------------------------
def wind_ridge(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    base = dsp.smooth(n, rng, 0.1)
    for c in range(2):
        g = _gusts(n, rng, base, rate=0.25, mix=0.7, floor=0.3, power=1.4)
        roar = dsp.colored(n, rng, -5.0, lo=40, hi=900)
        body = dsp.colored(n, rng, -3.0, lo=250, hi=3500)
        hiss = dsp.colored(n, rng, -0.5, lo=2500, hi=9000)
        y = roar * g * 0.9 + body * g ** 1.5 * 0.55 + hiss * g ** 2.2 * 0.18
        # lattice whistles (aeolian tones; pitch follows wind speed), a 6 Hz binaural split
        for f0, bw, lv in ((410, 7, 0.35), (566, 9, 0.48), (812, 11, 0.32), (1180, 14, 0.2), (1640, 18, 0.11)):
            fr = (f0 + (3.0 if c else -3.0)) * (0.82 + 0.36 * g) * (1 + 0.006 * dsp.smooth(n, rng, 1.5))
            w = dsp.nb_noise(fr, bw, rng, periodic_fix=True)
            y += w * lv * np.clip((g - 0.45) / 0.55, 0, 1) ** 1.8
        howl = dsp.nb_noise(np.full(n, 205.0 + 8 * c) * (0.9 + 0.2 * g), 22, rng, periodic_fix=True)
        y += howl * 0.25 * np.clip((g - 0.6) / 0.4, 0, 1) ** 2
        out[c] = y
    # high-voltage line: 100 Hz corona hum + crackling buzz
    t = np.arange(n) / SR
    hum = sum(a * np.sin(TAU * f * t + rng.random() * 6) for f, a in ((100, 1.0), (200, 0.5), (300, 0.35), (500, 0.15)))
    buzz = dsp.colored(n, rng, 0.0, lo=1500, hi=8000) * (0.5 + 0.5 * np.cos(TAU * 100 * t)) ** 6
    crk = ins.crackle(L, rng, 25, hp=2500)
    corona = hum * 0.012 + buzz * 0.01 + crk / (np.max(np.abs(crk)) + 1e-9) * 0.03
    out += np.vstack([corona * 0.8, corona])
    gr = _norm01(dsp.smooth(n, rng, 0.1))
    out += dsp.binaural(566.0, 6.0, n, L=L, rng=rng) * (0.2 + 0.8 * gr) * 0.04  # a pure lattice tone, 6 Hz apart
    # the lattice flexing: a few metallic groans / pings
    for _ in range(3):
        d = rng.uniform(0.6, 1.2)
        gr = ins.creak(d, rng, [(0, 30), (d / 2, 70), (d, 25)], [(0, 0), (0.1, 0.7), (d, 0)],
                       modes=[(f, 25, a) for f, a in ((430, 1.0), (980, 0.7), (1730, 0.5), (2620, 0.3))], tonal=0.6)
        _place_circ(out, dsp.pan(gr / (np.max(np.abs(gr)) + 1e-9), rng.uniform(-0.7, 0.7)) * 0.05, rng.uniform(0, L))
    ir = dsp.make_ir(0.9, rng, stereo=True, predelay=0.008)
    out = dsp.circ_reverb(out, ir, wet=0.18, dry=0.95)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.tunnel - 40 s: low hum, air movement, sub resonance, far drips
# --------------------------------------------------------------------------
def _tunnel_ir(rng, rt=3.6, dur=4.5):
    ir = dsp.make_ir(rt, rng, stereo=True, predelay=0.02, hf_ratio=0.35, lf_ratio=1.4, dur=dur)
    n = ir.shape[1]
    fl = np.zeros(n)
    t, a = 0.0262, 0.5
    while t < 1.2:  # flutter echo between the walls
        fl[ns(t)] += a
        t += 0.0262 * rng.uniform(0.995, 1.005)
        a *= 0.9
    fl = dsp.lowpass(fl, 3000, 2)
    ir = ir + np.vstack([fl, np.roll(fl, 7)]) * np.sqrt(np.sum(ir[0] ** 2)) / (np.sqrt(np.sum(fl ** 2)) + 1e-9) * 0.35
    return ir / np.sqrt(np.sum(ir ** 2, axis=1, keepdims=True))


def _drip(rng, f0, vel=1.0):
    tau = rng.uniform(0.012, 0.025)
    m = ns(0.12)
    t = np.arange(m) / SR
    f = f0 * (1 + 1.6 * (1 - np.exp(-t / (tau * 1.5))))
    y = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / tau) * np.minimum(1, t / 0.0008)
    L = ns(0.004)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * 0.035
    return y * vel


def tunnel(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    sub = dsp.sub_rumble(n, 31.0, L=L, rng=rng, am_rate=5.0, am_depth=0.35, drive=2.0)
    out += np.vstack([sub, sub]) * 0.4 * _lfo(n, 3, rng, 0.55, 1.0)
    moan = np.zeros((2, n))
    for c in range(2):
        fr = np.full(n, 64.0 + 2.5 * c) * (1 + 0.03 * dsp.smooth(n, rng, 0.2))
        moan[c] = dsp.nb_noise(fr, 5, rng, periodic_fix=True) + 0.4 * dsp.nb_noise(fr * 2.01, 7, rng, periodic_fix=True)
    out += moan * 0.22 * _lfo(n, 2, rng, 0.2, 1.0, 2.0)
    air = np.vstack([dsp.colored(n, rng, -5.5, lo=25, hi=500) for _ in range(2)])
    out += air * 0.35 * (0.4 + 0.6 * _lfo(n, 4, rng))[None, :]
    hiss = np.vstack([dsp.colored(n, rng, -1.0, lo=1500, hi=7000) for _ in range(2)])
    out += hiss * 0.012 * _lfo(n, 3, rng, 0.3, 1.0)[None, :]
    bt = dsp.binaural(107.0, 4.5, n, L=L, rng=rng)  # 104.75 / 109.25 Hz, clear of the 31 Hz harmonics
    out += bt * 0.05 * _lfo(n, 2, rng, 0.2, 1.0, 2.0)[None, :]
    ir = _tunnel_ir(rng)
    drips = np.zeros((2, n))
    t = rng.uniform(0.5, 2.0)
    while t < L - 0.2:
        d = rng.uniform(1.0, 3.5)
        dp = _drip(rng, rng.uniform(600, 1400), 1.0 / d)
        dp = dsp.lowpass(dp, 9000 / d, 2)
        _place_circ(drips, dsp.pan(dp, rng.uniform(-0.9, 0.9)), t)
        t += rng.uniform(1.8, 5.5)
    drips = dsp.circ_reverb(drips, ir, wet=1.0, dry=0.25)
    out += drips * 0.35
    out = dsp.circ_reverb(out, ir, wet=0.25, dry=0.9)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.village - 40 s: loose tin, a shutter, a detuned wind chime far away
# --------------------------------------------------------------------------
def _tin_rattle(rng, d, vel=1.0, size=1.0):
    y = np.zeros(ns(d + 1.0))
    t = 0.0
    while t < d:
        v = vel * rng.uniform(0.25, 1.0) * np.sin(np.pi * t / d) ** 0.5
        h = ins.modal_hit(rng, ins.TIN, size=size * rng.uniform(0.9, 1.1), hardness=0.7, vel=v, dur=0.9, noise=0.4)
        dsp.place(y, h, t)
        t += rng.uniform(0.025, 0.09)
    return y


def _shutter(rng, d=1.4):
    y = np.zeros(ns(d + 1.2))
    t = 0.0
    while t < d:
        for k in range(rng.integers(2, 5)):
            h = ins.modal_hit(rng, ins.METAL, size=rng.uniform(1.6, 2.4), hardness=0.6, vel=rng.uniform(0.2, 0.7), dur=0.5)
            dsp.place(y, h, t + k * rng.uniform(0.006, 0.02))
        boom = ins.modal_hit(rng, ins.TIN, size=1.8, hardness=0.3, vel=0.6, dur=1.0, noise=0.2)
        dsp.place(y, dsp.lowpass(boom, 900, 2), t)
        t += rng.uniform(0.09, 0.22)
    return y


def village(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    base = dsp.smooth(n, rng, 0.12)
    gl = None
    for c in range(2):
        g = _gusts(n, rng, base, rate=0.2, mix=0.75, floor=0.12, power=1.8)
        gl = g if c == 0 else (gl + g) / 2
        body = dsp.colored(n, rng, -4.5, lo=70, hi=2200)
        y = body * g * 0.35
        for f0, bw, lv in ((330, 12, 0.18), (505, 16, 0.12), (742, 20, 0.07)):
            fr = f0 * (0.88 + 0.24 * g) * (1 + 0.01 * dsp.smooth(n, rng, 1.0))
            y += dsp.nb_noise(fr, bw, rng, periodic_fix=True) * lv * np.clip((g - 0.5) / 0.5, 0, 1) ** 2
        out[c] = y
    # events follow the gust peaks
    peaks = [k for k in range(ns(1.0), n - ns(1.0), ns(0.5)) if gl[k] > 0.55]
    ts = sorted(rng.choice(peaks, size=min(9, len(peaks)), replace=False) / SR) if peaks else []
    for k, t0 in enumerate(ts):
        if k % 3 == 2:
            s = _shutter(rng, rng.uniform(0.8, 1.6))
            p = 0.65
            g = 0.16
        else:
            s = _tin_rattle(rng, rng.uniform(0.3, 0.9), size=rng.choice([0.9, 1.2]))
            p = -0.6 if k % 2 else 0.25
            g = 0.2
        s = dsp.lowpass(s, 5000, 2)
        _place_circ(out, dsp.pan(s / (np.max(np.abs(s)) + 1e-9), p + rng.uniform(-0.15, 0.15)) * g, t0)
    # far, sour wind chime
    base_f = rng.uniform(2300, 2700)
    for t0 in sorted(rng.uniform(0, L, 6)):
        ch = np.zeros(ns(3.5))
        for k in range(rng.integers(1, 4)):
            dsp.place(ch, ins.furin(rng, base_f * dsp.cents(rng.uniform(-60, 60)), rng.uniform(0.4, 1.0), detune=35), k * rng.uniform(0.12, 0.3))
        ch = dsp.lowpass(ch, 4500, 2)
        _place_circ(out, dsp.pan(ch, 0.85) * 0.05, t0)
    # rusted bus-stop sign creaking on its pole
    for t0 in rng.uniform(0, L, 2):
        d = rng.uniform(0.8, 1.4)
        cr = ins.creak(d, rng, [(0, 25), (d / 2, 55), (d, 20)], [(0, 0), (0.15, 0.8), (d, 0)],
                       modes=[(f, 30, a) for f, a in ((610, 1.0), (1290, 0.6), (2050, 0.35))], tonal=0.7, jitter=0.2)
        _place_circ(out, dsp.pan(cr / (np.max(np.abs(cr)) + 1e-9), -0.3) * 0.03, t0)
    sub = dsp.sub_rumble(n, 36.71, L=L, rng=rng, am_rate=6.5, am_depth=0.5)
    out += np.vstack([sub, sub]) * 0.02 * _lfo(n, 2, rng, 0.0, 1.0, 2.0)
    out += dsp.binaural(200.0, 5.5, n, L=L, rng=rng) * 0.02 * _lfo(n, 2, rng, 0.2, 1.0)[None, :]
    ir = dsp.make_ir(1.7, rng, stereo=True, predelay=0.02, hf_ratio=0.4)
    out = dsp.circ_reverb(out, ir, wet=0.32, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.silence - 20 s near-silence: hiss, faint ringing, ear pressure
# --------------------------------------------------------------------------
def silence(rng, i=1):
    L = 20.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.vstack([dsp.colored(n, rng, -2.0, lo=300, hi=9000) for _ in range(2)]) * 0.02
    # ringing at the edge of hearing, different in each ear
    for c, (f, f2) in enumerate(((11250.0, 13020.0), (11870.0, 12460.0))):
        fq, fq2 = dsp.loop_freq(f, L), dsp.loop_freq(f2, L)
        ring = np.sin(TAU * fq * t) + 0.4 * np.sin(TAU * fq2 * t + 1.0)
        out[c] += ring * 0.006 * _lfo(n, 3 + c, rng, 0.4, 1.0)
    # ear pressure: sub swell + muffled low noise breathing in
    sub = dsp.sub_rumble(n, 24.0, L=L, rng=rng, am_rate=0.5, am_depth=0.3, drive=1.4)
    prs = dsp.colored(n, rng, -6.0, lo=20, hi=140)
    sw = _lfo(n, 2, rng, 0.25, 1.0, 1.5)
    out += np.vstack([sub, sub]) * 0.12 * sw + np.vstack([prs, np.roll(prs, ns(0.3))]) * 0.06 * sw
    out += dsp.binaural(224.0, 5.0, n, L=L, rng=rng) * 0.004 * sw[None, :]  # theta at the threshold of hearing
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.stream - 20 s mono (positional): mountain stream over stones
# --------------------------------------------------------------------------
def stream(rng, i=1):
    L = 20.0
    n = ns(L)
    y = ins.water_texture(n, rng, L=L, density=300, r_range=(0.6, 5.0), noise=0.55, splash=0.4)
    for _ in range(40):  # low gurgles under the stones
        b = ins.bubble(rng, rng.uniform(7, 16), rng.uniform(0.3, 1.0))
        _place_circ(y, b * 0.8, rng.uniform(0, L))
    y = _cr(y, lambda x: dsp.peq(x, 420, 0.8, 4.0))
    y = _cr(y, lambda x: dsp.highpass(x, 70, 2))
    ir = dsp.make_ir(0.7, rng, predelay=0.006)
    y = dsp.circ_reverb(y, ir, wet=0.18, dry=0.9)
    return dsp.dc_block(y, circular=True)
