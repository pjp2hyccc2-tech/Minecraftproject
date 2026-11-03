"""amb.* looping beds (stereo).  Everything is built circularly (periodic FFT noise, loop-quantised oscillators and
LFOs, events placed modulo the loop length, circular reverb / filters), so every loop seam is sample-continuous.

The closed restaurant at night is *quiet*: these beds sit far below the one-shots (-48 .. -29 LUFS) - compressor hum,
a ticking clock, drips - with faint undercurrents from the psychoacoustic palette (binaural theta tones riding the
machines' hum, a sub-audible swell, edge-of-hearing ringing) rather than anything overtly musical."""
import numpy as np

import dsp
import instruments as ins
from dsp import SR, TAU, ns


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0):
    ph = cycles * np.arange(n) / n + rng.random()
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * ph)) ** shape


def _norm01(x):
    return (x - x.min()) / (x.max() - x.min() + 1e-12)


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _cr(x, fn, pre=2.0):
    return dsp.circ(fn, x, ns(pre))


def _place_circ(buf, x, t):
    return dsp.place(buf, x, t, circular=True)


def _tone(f, L, n, rng):
    return np.sin(TAU * (dsp.loop_freq(f, L) * np.arange(n) / SR + rng.random()))


def _hum(L, n, rng, f, harm):
    return sum(a * _tone(f * k, L, n, rng) for k, a in harm)


def _drip(rng, f0, vel=1.0):
    """A water drop: the rising Minnaert chirp of the bubble it traps, a tiny splash."""
    tau = rng.uniform(0.012, 0.025)
    m = ns(0.12)
    t = np.arange(m) / SR
    f = f0 * (1 + 1.6 * (1 - np.exp(-t / (tau * 1.5))))
    y = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / tau) * np.minimum(1, t / 0.0008)
    L = ns(0.004)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * 0.035
    return y * vel


def _room(rng, rt, stereo=True, predelay=0.012, hf=0.45):
    return dsp.make_ir(rt, rng, stereo=stereo, predelay=predelay, hf_ratio=hf)


def _compressor(L, n, rng, on_spans, f=50.0, level=1.0, rattle=0.3):
    """A hermetic refrigeration compressor cycling: 100 Hz hum with harmonics while on, a start surge, a stop shudder."""
    t = np.arange(n) / SR
    gate = np.zeros(n)
    for a, b in on_spans:
        ia, ib = ns(a), ns(b)
        idx = np.arange(ia, ib) % n
        gate[idx] = 1.0
    gate = _cr(gate, lambda x: dsp.lowpass0(x, 3.0))
    hum = _hum(L, n, rng, f, ((1, 0.3), (2, 1.0), (3, 0.25), (4, 0.45), (6, 0.18), (8, 0.08)))
    mech = dsp.colored(n, rng, -2.5, lo=80, hi=1500) * rattle
    y = (_nrm(hum) * 0.8 + mech / np.std(mech) * 0.15) * gate
    for a, b in on_spans:  # start clunk + surge, stop shudder + click
        m = ns(0.5)
        tt = np.arange(m) / SR
        surge = np.sin(TAU * np.cumsum(60 + 40 * np.exp(-tt / 0.08)) / SR) * dsp.env_ad(m, 0.01, 0.15)
        _place_circ(y, surge * 0.5, a)
        _place_circ(y, ins.modal_hit(rng, [(310, 0.05, 1.0), (720, 0.04, 0.6), (1300, 0.03, 0.3)], hardness=0.5,
                                     vel=0.4, dur=0.15), a)
        sh = dsp.lowpass(rng.standard_normal(m), 300, 2) * dsp.env_ad(m, 0.02, 0.12)
        _place_circ(y, sh * 0.6, b)
        _place_circ(y, ins.modal_hit(rng, [(1900, 0.03, 1.0), (3100, 0.02, 0.5)], hardness=1.0, vel=0.15, dur=0.08), b + 0.3)
    return y * level, gate


def _clock(L, n, rng, pan=-0.5, vel=1.0, period=1.0):
    """A quartz wall clock: a light plastic tick every second."""
    out = np.zeros((2, n))
    k = 0
    t = 0.37
    while t < L - 1e-6:
        c = ins.modal_hit(rng, [(2100, 0.025, 1.0), (3400, 0.018, 0.6), (5600, 0.01, 0.3), (900, 0.03, 0.3)],
                          size=1.0, hardness=1.0, vel=vel * (1.0 if k % 2 == 0 else 0.82), dur=0.07, noise=0.35)
        _place_circ(out, dsp.pan(c, pan), t)
        t += period
        k += 1
    return out


# --------------------------------------------------------------------------
# amb.night_road - 60 s: wet road hiss, far traffic swells, gutters dripping, a far dog, wind in the wires
# --------------------------------------------------------------------------
def night_road(rng, i=1):
    L = 60.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    weather = _norm01(dsp.smooth(n, rng, 0.05))
    for c in range(2):  # the wet world's own hiss + low air
        h = dsp.colored(n, rng, -1.0, lo=500, hi=9000)
        a = dsp.colored(n, rng, -5.0, lo=30, hi=400)
        out[c] += h / np.std(h) * 0.025 + a / np.std(a) * 0.04 * (0.5 + 0.5 * weather)
    for k in range(6):  # traffic swelling and fading on wet asphalt, near and far
        far = k >= 2
        tm = rng.uniform(0, L)
        dur = rng.uniform(9, 16) if far else rng.uniform(6, 9)
        m = ns(dur)
        x = np.linspace(-1, 1, m)
        g = 1.0 / (1.0 + (x * (3.0 if far else 5.0)) ** 2)
        sp = dsp.colored(m, rng, -1.0, lo=200, hi=9000)
        lp = (1500 if far else 3500) + (2000 if far else 6000) * g
        sp = dsp.tv_filter(sp, lp, 0.7, "lp", block=256)
        eng = dsp.lowpass(rng.standard_normal(m), 250, 2) * 0.6
        car = (sp / np.std(sp) + eng / np.std(eng) * 0.3) * g * (0.06 if far else 0.16)
        pn = np.linspace(-0.9, 0.9, m) * (1 if rng.random() < 0.5 else -1)
        _place_circ(out, dsp.pan(car, pn), tm - dur / 2)
    drips = np.zeros((2, n))  # gutters and the canopy: a few drip streams, each with its own pulse
    for s in range(4):
        per = rng.uniform(0.8, 2.6)
        pn = rng.uniform(-0.8, 0.8)
        f0 = rng.uniform(600, 1200)
        dist = rng.uniform(1.0, 3.0)
        tt = rng.uniform(0, per)
        while tt < L:
            d = _drip(rng, f0 * rng.uniform(0.95, 1.05), rng.uniform(0.6, 1.0) / dist)
            d = dsp.lowpass(d, 9000 / dist, 2)
            _place_circ(drips, dsp.pan(d, pn), tt)
            tt += per * rng.uniform(0.85, 1.25)
    for _ in range(60):  # stray drops into puddles
        d = _drip(rng, rng.uniform(500, 1400), rng.uniform(0.2, 0.6))
        _place_circ(drips, dsp.pan(dsp.lowpass(d, 5000, 2), rng.uniform(-1, 1)), rng.uniform(0, L))
    out += _nrm(drips) * 0.09
    t0 = rng.uniform(15, 45)  # a dog, very far away, three barks
    for k in range(3):
        from voice import scream
        b, _ = scream(0.16, rng, [(0, 520), (0.03, 760), (0.16, 480)], [(0, 0), (0.01, 1.0), (0.16, 0)], vowel="a",
                      chaos_pts=[(0, 3.7), (0.16, 3.8)], breath=0.5, rasp=0.6, scale=0.8, oq=0.5)
        b = dsp.lowpass(dsp.highpass(b, 300, 2), 2200, 2)
        bf = dsp.far(b, rng, rt60=2.8, wet=1.0, dry=0.2, lp=1800, predelay=0.08, taps=[(0.45, 0.4, 1200)])
        _place_circ(out, dsp.pan(_nrm(bf), 0.75) * 0.05, t0 + k * rng.uniform(0.45, 0.7))
    gust = _norm01(dsp.smooth(n, rng, 0.12)) ** 2.0  # wind in the wires: aeolian tones with the gusts
    for c in range(2):
        for f0, bw, lv in ((330, 6, 0.5), (470, 8, 0.35), (690, 10, 0.2)):
            fr = (f0 + (2.0 if c else -2.0)) * (0.9 + 0.2 * gust)
            w = dsp.nb_noise(fr, bw, rng, periodic_fix=True)
            out[c] += w * lv * 0.03 * np.clip((gust - 0.3) / 0.7, 0, 1) ** 1.5
        wn = dsp.colored(n, rng, -4.0, lo=100, hi=2500)
        out[c] += wn / np.std(wn) * 0.05 * (0.3 + 0.7 * gust)
    ir = _room(rng, 1.6, predelay=0.03, hf=0.35)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.9)
    tones = dsp.binaural(296.0, 5.5, n, L=L, rng=rng) * 0.004 * (0.3 + 0.7 * gust)[None, :]
    sub = dsp.sub_rumble(n, 33.0, L=L, rng=rng, am_rate=5.0, am_depth=0.4) * _lfo(n, 2, rng, 0.0, 1.0, 3.0)
    out += tones + np.vstack([sub, sub]) * 0.02
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.dining - 40 s: the closed dining hall
# --------------------------------------------------------------------------
def dining(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    for c in range(2):  # room tone of a big room with absorbent tiles
        r = dsp.colored(n, rng, -4.5, lo=40, hi=2200)
        out[c] += r / np.std(r) * 0.022
    cake, g1 = _compressor(L, n, rng, [(5.5, 29.0)], level=1.0, rattle=0.35)  # the cake case, cycling
    out += dsp.pan(_nrm(cake), 0.55) * 0.12
    bar, g2 = _compressor(L, n, rng, [(21.0, 45.5)], level=1.0, rattle=0.25)  # the drink bar, out of phase with it
    out += dsp.pan(_nrm(bar), -0.65) * 0.08
    gur = np.zeros(n)  # refrigerant gurgling after the stops
    for t0 in (29.3, 5.8):
        m = ns(4.0)
        w = ins.water_texture(m, rng, density=60, r_range=(2.0, 7.0), noise=0.05, splash=0.0)
        _place_circ(gur, dsp.lowpass(w, 2500, 2) * dsp.env_pts(m, [(0, 0), (0.3, 1), (4.0, 0)]), t0)
    out += dsp.pan(_nrm(gur), 0.45) * 0.025
    exit_ = _hum(L, n, rng, 100.0, ((1, 0.4), (3, 0.3), (5, 0.12)))  # the green exit sign
    out += dsp.pan(_nrm(exit_), 0.2) * 0.006
    clock = _clock(L, n, rng, pan=-0.45, vel=1.0) * 0.2
    for _ in range(3):  # the building cooling: a tick in the ceiling, a creak in a booth
        h = ins.modal_hit(rng, [(rng.uniform(600, 1800), 0.04, 1.0), (rng.uniform(2000, 3500), 0.03, 0.6)], hardness=0.8,
                          vel=1.0, dur=0.12)
        _place_circ(out, dsp.pan(h, rng.uniform(-0.9, 0.9)) * 0.012, rng.uniform(0, L))
    ir = _room(rng, 0.9, predelay=0.012, hf=0.5)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.9)
    out += dsp.circ_reverb(clock, ir, wet=0.15, dry=1.0)  # the clock stays crisp: it is the loudest thing here
    out += dsp.binaural(200.0, 6.0, n, L=L, rng=rng) * 0.0025 * _lfo(n, 2, rng, 0.3, 1.0)[None, :]  # theta under the hum
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.kitchen - 40 s: the big compressor, the exhaust fan idling, a drip in the sink, stainless ticking as it cools
# --------------------------------------------------------------------------
def kitchen(rng, i=1):
    L = 40.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    comp = _hum(L, n, rng, 50.0, ((1, 0.6), (2, 1.0), (3, 0.4), (4, 0.5), (5, 0.15), (6, 0.25), (8, 0.1)))
    comp *= 1 + 0.05 * _tone(0.5, L, n, rng)
    rum = dsp.colored(n, rng, -4.0, lo=30, hi=400)
    out += np.vstack([_nrm(comp) * 0.16 + rum / np.std(rum) * 0.07] * 2) * np.array([[0.85], [1.0]])
    blade = dsp.loop_freq(37.5, L)  # the hood's fan idling (6 blades at 375 rpm)
    fan = dsp.colored(n, rng, -2.0, lo=120, hi=3500) * (1 + 0.25 * np.cos(TAU * blade * t))
    fan += dsp.nb_noise(np.full(n, blade * 4), 4.0, rng, periodic_fix=True) * 0.6
    rattle = ins.friction_pulses(L, rng, blade, 0.3, jitter=0.15)
    rattle = _cr(rattle, lambda x: dsp.resbank(x, [(820, 15, 1.0), (1650, 18, 0.6), (2600, 20, 0.3)]))
    out += dsp.pan(fan / np.std(fan) * 0.06 + _nrm(rattle) * 0.01, 0.3)
    sink = np.zeros(n)  # the tap at the dish pit drips into the steel sink
    tt = 0.8
    while tt < L - 0.1:
        d = _drip(rng, rng.uniform(1050, 1150), rng.uniform(0.8, 1.0))
        d = d + dsp.resbank(d, [(410, 30, 0.6), (990, 40, 0.5), (1720, 50, 0.4), (2580, 60, 0.3)]) * 0.4
        _place_circ(sink, d, tt)
        tt += rng.uniform(3.2, 3.9)
    out += dsp.pan(_nrm(sink), 0.75) * 0.05
    for _ in range(14):  # stainless steel ticking as it cools
        h = ins.modal_hit(rng, [(rng.uniform(1200, 2600), 0.15, 1.0), (rng.uniform(3000, 5200), 0.1, 0.6)],
                          hardness=1.0, vel=rng.uniform(0.3, 1.0), dur=0.25, noise=0.3)
        _place_circ(out, dsp.pan(h, rng.uniform(-0.9, 0.9)) * 0.012, rng.uniform(0, L))
    pilot = dsp.colored(n, rng, -1.0, lo=800, hi=6000)  # pilot flames, barely
    out += dsp.pan(pilot / np.std(pilot), -0.4) * 0.004
    ir = _room(rng, 0.75, predelay=0.006, hf=0.9)
    out = dsp.circ_reverb(out, ir, wet=0.3, dry=0.9)
    out += dsp.binaural(150.0, 5.0, n, L=L, rng=rng) * 0.003 * _lfo(n, 2, rng, 0.3, 1.0)[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.restroom - 30 s: tiled room tone, water in the pipes, a slow drip, the ventilation fan
# --------------------------------------------------------------------------
def restroom(rng, i=1):
    L = 30.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    blade = dsp.loop_freq(142.0, L)
    fan = dsp.colored(n, rng, -1.5, lo=200, hi=5000)
    fan = _cr(fan, lambda x: dsp.peq(x, 1100, 1.2, 5.0))
    fan += dsp.nb_noise(np.full(n, blade), 3.0, rng, periodic_fix=True) * 0.8
    out += dsp.pan(fan / np.std(fan), 0.2) * 0.035
    pipe = np.zeros(n)  # somewhere in the walls water moves: a rush, a gurgle
    for t0, d in ((4.0, 5.5), (19.0, 3.5)):
        m = ns(d)
        w = ins.water_texture(m, rng, density=160, r_range=(2.0, 9.0), noise=0.6, splash=0.1)
        w = dsp.resbank(w, [(180, 6, 1.0), (390, 8, 0.6), (620, 10, 0.4)]) * 0.5 + dsp.lowpass(w, 900, 2)
        _place_circ(pipe, w * dsp.env_pts(m, [(0, 0), (d * 0.3, 1), (d, 0)]), t0)
    out += dsp.pan(_nrm(pipe), -0.5) * 0.05
    drip = np.zeros(n)
    tt = 1.1
    while tt < L - 0.1:
        d = _drip(rng, rng.uniform(820, 880), rng.uniform(0.85, 1.0))
        d = d + dsp.resbank(d, [(520, 20, 0.5), (1310, 25, 0.35), (2150, 30, 0.25)]) * 0.3  # into the ceramic basin
        _place_circ(drip, d, tt)
        tt += rng.uniform(2.5, 3.0)
    out += dsp.pan(_nrm(drip), 0.55) * 0.06
    tone = _hum(L, n, rng, 50.0, ((2, 1.0), (4, 0.3), (6, 0.15)))  # the lights over the mirror
    out += np.vstack([tone, tone]) * 0.004
    ir = _room(rng, 1.4, predelay=0.006, hf=0.85)
    out = dsp.circ_reverb(out, ir, wet=0.55, dry=0.85)
    out += dsp.binaural(240.0, 6.0, n, L=L, rng=rng) * 0.003 * _lfo(n, 2, rng, 0.2, 1.0)[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.back - 30 s: corridor / office: 50 Hz ballast hum with harmonics, PC fans, the breaker panel's buzz
# --------------------------------------------------------------------------
def back(rng, i=1):
    L = 30.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    bal = _hum(L, n, rng, 50.0, ((1, 0.3), (2, 1.0), (4, 0.45), (6, 0.5), (8, 0.2), (10, 0.15), (12, 0.08)))
    bal = np.tanh(1.5 * _nrm(bal))
    zzz = dsp.colored(n, rng, 0.0, lo=2500, hi=8000) * (0.5 + 0.5 * np.cos(TAU * dsp.loop_freq(100.0, L) * t)) ** 8
    flick = np.ones(n)  # the corridor tube that flickers
    for _ in range(4):
        t0 = rng.uniform(0, L)
        for k in range(rng.integers(2, 5)):
            d = rng.uniform(0.03, 0.12)
            idx = (ns(t0) + np.arange(ns(d))) % n
            flick[idx] = rng.uniform(0.2, 0.6)
            t0 += d + rng.uniform(0.03, 0.15)
    flick = _cr(flick, lambda x: dsp.lowpass0(x, 200.0))
    out += dsp.pan((bal * 0.07 + zzz / np.std(zzz) * 0.012) * flick, -0.2)
    pc_b = dsp.loop_freq(175.0, L)  # the office PC: fan blade tone, bearing whine, airflow
    pc = dsp.nb_noise(np.full(n, pc_b), 4.0, rng, periodic_fix=True) + 0.4 * _tone(2950.0, L, n, rng)
    pc += dsp.colored(n, rng, -1.5, lo=300, hi=6000) * 0.8
    out += dsp.pan(pc / np.std(pc), 0.6) * 0.02
    brk = _hum(L, n, rng, 100.0, ((1, 1.0), (2, 0.3), (3, 0.4))) * (1 + 0.3 * dsp.smooth(n, rng, 0.3))
    out += dsp.pan(_nrm(brk), -0.7) * 0.012
    for _ in range(2):  # a relay in the security system
        h = ins.modal_hit(rng, [(2500, 0.02, 1.0), (4100, 0.015, 0.5)], hardness=1.0, vel=1.0, dur=0.05)
        _place_circ(out, dsp.pan(h, 0.5) * 0.01, rng.uniform(0, L))
    ir = _room(rng, 0.8, predelay=0.006, hf=0.6)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.9)
    out += dsp.binaural(176.0, 5.0, n, L=L, rng=rng) * 0.003 * _lfo(n, 2, rng, 0.2, 1.0)[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.fridge - 20 s: inside the walk-in: the evaporator fans, the compressor through the wall, ice crackling
# --------------------------------------------------------------------------
def fridge(rng, i=1):
    L = 20.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for c, (fb, pn) in enumerate(((dsp.loop_freq(118.0, L), -0.5), (dsp.loop_freq(124.0, L), 0.5))):
        fan = dsp.colored(n, rng, -1.0, lo=150, hi=7000)
        fan = _cr(fan, lambda x: dsp.peq(x, 900, 1.0, 4.0))
        fan *= 1 + 0.15 * np.cos(TAU * fb / 5.0 * t)
        fan += dsp.nb_noise(np.full(n, fb), 3.0, rng, periodic_fix=True) * 1.2
        fan += dsp.nb_noise(np.full(n, fb * 2), 4.0, rng, periodic_fix=True) * 0.5
        out += dsp.pan(fan / np.std(fan), pn) * 0.2
    comp = _hum(L, n, rng, 50.0, ((1, 0.5), (2, 1.0), (3, 0.3), (4, 0.4), (6, 0.15)))
    out += np.vstack([comp, comp]) / np.max(np.abs(comp)) * 0.1
    ice = ins.crackle(L, rng, 14, hp=2500, decay=0.0005)
    out += dsp.pan(ice / (np.max(np.abs(ice)) + 1e-9), 0.3) * 0.08
    for _ in range(6):  # frost cracking on the coils
        h = ins.modal_hit(rng, [(rng.uniform(2500, 5000), 0.02, 1.0), (rng.uniform(5000, 8000), 0.01, 0.5)], hardness=1.0,
                          vel=1.0, dur=0.05, noise=0.6)
        _place_circ(out, dsp.pan(h, rng.uniform(-0.8, 0.8)) * 0.05, rng.uniform(0, L))
    ir = _room(rng, 0.12, predelay=0.002, hf=0.5)
    out = dsp.circ_reverb(out, ir, wet=0.15, dry=1.0)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.silence - 20 s near-silence: room-tone hiss, faint high ringing, ear pressure
# --------------------------------------------------------------------------
def silence(rng, i=1):
    L = 20.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.vstack([dsp.colored(n, rng, -2.0, lo=300, hi=9000) for _ in range(2)]) * 0.02
    for c, (f, f2) in enumerate(((11250.0, 13020.0), (11870.0, 12460.0))):
        fq, fq2 = dsp.loop_freq(f, L), dsp.loop_freq(f2, L)
        ring = np.sin(TAU * fq * t) + 0.4 * np.sin(TAU * fq2 * t + 1.0)
        out[c] += ring * 0.006 * _lfo(n, 3 + c, rng, 0.4, 1.0)
    sub = dsp.sub_rumble(n, 24.0, L=L, rng=rng, am_rate=0.5, am_depth=0.3, drive=1.4)
    prs = dsp.colored(n, rng, -6.0, lo=20, hi=140)
    sw = _lfo(n, 2, rng, 0.25, 1.0, 1.5)
    out += np.vstack([sub, sub]) * 0.12 * sw + np.vstack([prs, np.roll(prs, ns(0.3))]) * 0.06 * sw
    out += dsp.binaural(224.0, 5.0, n, L=L, rng=rng) * 0.004 * sw[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# amb.closing - 40 s: every light's hum at once, the store music bleeding through the walls, PA hiss
# --------------------------------------------------------------------------
def closing(rng, i=1):
    import jingle as J
    L = 40.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for c in range(2):  # a hall full of ballasts: the hum thickens, comb-filtered by the room
        h = _hum(L, n, rng, 50.0, ((2, 1.0), (4, 0.5), (6, 0.6), (8, 0.25), (10, 0.3), (14, 0.12), (18, 0.08)))
        z = dsp.colored(n, rng, 0.0, lo=2000, hi=9000) * (0.5 + 0.5 * np.cos(TAU * dsp.loop_freq(100.0, L) * t + c)) ** 6
        out[c] += _nrm(h) * 0.1 + z / np.std(z) * 0.02
    beat = 0.5  # the bossa, slightly flat, through a wall (20 bars = 40 s)
    back = J.bossa_backing(rng, 20, beat, detune_keys=-40)
    mel = np.zeros((2, n))
    J.place_notes(mel, J.MELODY + [(b + 32, nm, d) for b, nm, d in J.MELODY[:15]], beat,
                  lambda f, d, k: dsp.pan(ins.vibraphone(f * dsp.cents(-40), d + 1.0, rng, vel=0.7, depth=0.2,
                                                         release=d + 0.4), 0.1), rng=rng)
    mus = _nrm(back) * 0.8 + _nrm(mel)
    mus = _cr(mus, lambda x: dsp.lowpass(x, 420, 3))
    mus = _cr(mus, lambda x: dsp.highpass(x, 70, 2))
    out += _nrm(mus) * 0.12
    hiss = np.vstack([dsp.colored(n, rng, -1.0, lo=400, hi=7000) for _ in range(2)])
    out += hiss * 0.012 * _lfo(n, 2, rng, 0.7, 1.0)[None, :]
    ir = _room(rng, 1.1, predelay=0.012, hf=0.5)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.9)
    sub = dsp.sub_rumble(n, 43.65, L=L, rng=rng, am_rate=6.0, am_depth=0.4) * _lfo(n, 2, rng, 0.0, 1.0, 2.0)
    out += np.vstack([sub, sub]) * 0.04 + dsp.binaural(130.0, 6.0, n, L=L, rng=rng) * 0.004
    return dsp.dc_block(out, circular=True)
