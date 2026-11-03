"""Positional loops (mono) and the spirit meter (stereo).  All are <= 8 s so Minecraft keeps them as static buffers
(it has only 8 streaming channels for beds and music), and all are built circularly: periodic noise, loop-quantised
tones and LFOs, events placed modulo the loop length, circular filters and reverb."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _cr(x, fn, pre=1.0):
    return dsp.circ(fn, x, ns(pre))


def _tone(f, L, n, rng, phase=None):
    return np.sin(TAU * (dsp.loop_freq(f, L) * np.arange(n) / SR + (rng.random() if phase is None else phase)))


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0):
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * (cycles * np.arange(n) / n + rng.random()))) ** shape


def _hum(L, n, rng, f=100.0, harm=((1, 1.0), (2, 0.45), (3, 0.6), (4, 0.2), (5, 0.25), (7, 0.1), (9, 0.05))):
    return sum(a * _tone(f * k, L, n, rng) for k, a in harm)


def _zzz(L, n, rng, f=100.0, sharp=8, lo=2500, hi=9000):
    """The 'zzz' of a discharge: HF noise gated at the mains' double frequency."""
    t = np.arange(n) / SR
    gate = (0.5 + 0.5 * np.cos(TAU * dsp.loop_freq(f, L) * t)) ** sharp
    return dsp.colored(n, rng, 0.0, lo=lo, hi=hi) * gate


def _fin_loop(y, rng, rt=0.15, wet=0.06):
    if wet > 0:
        y = dsp.circ_reverb(y, dsp.make_ir(rt, rng, predelay=0.002, hf_ratio=0.6), wet=wet, dry=1.0)
    return dsp.dc_block(y, circular=True)


# --------------------------------------------------------------------------
# lights, machines, signs
# --------------------------------------------------------------------------
def fluorescent(rng, i=1):
    """A tube that is going: ballast hum, the tube's zzz, flickers with starter ticks."""
    L = 8.0
    n = ns(L)
    hum = _hum(L, n, rng)
    z = _zzz(L, n, rng)
    gate = np.ones(n)
    for _ in range(5):  # flickers: dips and stutters
        t0 = rng.uniform(0, L)
        for k in range(rng.integers(1, 5)):
            d = rng.uniform(0.02, 0.09)
            idx = (ns(t0) + np.arange(ns(d))) % n
            gate[idx] = rng.uniform(0.0, 0.3)
            t0 += d + rng.uniform(0.02, 0.1)
    gate = _cr(gate, lambda x: dsp.lowpass0(x, 250.0))
    y = (_nrm(hum) * 0.6 + z / np.std(z) * 0.18) * gate
    edges = np.nonzero(np.abs(np.diff(np.concatenate([gate, gate[:1]]))) > 0.04)[0]
    for e in edges[::3]:
        c = ins.modal_hit(rng, [(3300, 0.01, 1.0), (5100, 0.008, 0.5)], hardness=1.0, vel=0.25, dur=0.03, noise=0.6)
        dsp.place(y, c, int(e), circular=True)
    y += dsp.colored(n, rng, -2.0, lo=200, hi=4000) * 0.01
    return _fin_loop(y, rng)


def vending(rng, i=1):
    """Drinks vending machine: compressor, condenser fan, the lit panel's ballast."""
    L = 8.0
    n = ns(L)
    t = np.arange(n) / SR
    comp = _hum(L, n, rng, 50.0, ((1, 0.6), (2, 1.0), (3, 0.35), (4, 0.5), (6, 0.2), (8, 0.1)))
    comp *= 1 + 0.06 * _tone(0.25, L, n, rng)
    mech = _cr(dsp.colored(n, rng, -3.0, lo=60, hi=900), lambda x: dsp.peq(x, 180, 2.0, 6.0))
    blade = dsp.loop_freq(118.0, L)
    fan = dsp.nb_noise(np.full(n, blade), 6.0, rng, periodic_fix=True) * 0.5
    fan += dsp.colored(n, rng, -1.5, lo=300, hi=5000) * (1 + 0.3 * np.cos(TAU * dsp.loop_freq(blade / 7, L) * t))
    pan_b = _hum(L, n, rng, 100.0, ((1, 0.5), (3, 0.3), (5, 0.15))) * 0.3 + _zzz(L, n, rng) * 0.05
    y = _nrm(comp) * 0.55 + mech / np.std(mech) * 0.12 + fan / np.std(fan) * 0.16 + _nrm(pan_b) * 0.12
    for _ in range(2):  # something settles inside: a can shifts, a tick
        h = ins.modal_hit(rng, [(1250, 0.05, 1.0), (2400, 0.04, 0.6), (3900, 0.03, 0.4)], hardness=0.8, vel=0.2, dur=0.15)
        dsp.place(y, h, rng.uniform(0, L), circular=True)
    return _fin_loop(y, rng)


def sign_buzz(rng, i=1):
    """The pole sign's transformer: a fat 100 Hz buzz, its harmonics, and the flicker's arcing crackle."""
    L = 8.0
    n = ns(L)
    hum = _hum(L, n, rng, 100.0, ((1, 1.0), (2, 0.7), (3, 0.8), (4, 0.4), (5, 0.45), (6, 0.2), (7, 0.25), (9, 0.12), (11, 0.08)))
    hum = np.tanh(1.8 * _nrm(hum))  # saturated laminations
    z = _zzz(L, n, rng, sharp=6, lo=1800, hi=8000)
    gate = np.ones(n)
    for _ in range(4):
        t0 = rng.uniform(0, L)
        for k in range(rng.integers(2, 6)):
            d = rng.uniform(0.015, 0.06)
            idx = (ns(t0) + np.arange(ns(d))) % n
            gate[idx] = rng.uniform(0.1, 0.5)
            t0 += d + rng.uniform(0.01, 0.06)
    gate = _cr(gate, lambda x: dsp.lowpass0(x, 300.0))
    arc = np.zeros(n)
    for _ in range(int(L * 6)):
        m = ns(0.004)
        a = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0005 * SR))
        dsp.place(arc, dsp.highpass(a, 1500, 2) * rng.pareto(2.5) * 0.3, rng.uniform(0, L), circular=True)
    y = (_nrm(hum) * 0.55 + z / np.std(z) * 0.2) * gate + arc * 0.4 * (1.2 - gate)
    return _fin_loop(y, rng, 0.2, 0.08)


def drip(rng, i=1):
    """One drop: into a puddle, onto a steel sink, onto tiles, two into water."""
    from sfx_beds import _drip
    y = np.zeros(ns(1.6))
    if i == 1:
        f0 = rng.uniform(780, 900)
        dsp.place(y, _drip(rng, f0), 0.01)
        dsp.place(y, ins.bubble(rng, 3.5, 0.25), 0.03)
    elif i == 2:
        d = _drip(rng, rng.uniform(1100, 1250), 0.8)
        ring = dsp.resbank(d, [(410, 30, 0.6), (990, 45, 0.5), (1720, 55, 0.4), (2580, 60, 0.3)])
        dsp.place(y, d + ring * 0.5, 0.01)
    elif i == 3:
        c = ins.modal_hit(rng, [(2900, 0.03, 1.0), (4700, 0.02, 0.6), (6900, 0.012, 0.3)], hardness=1.0, vel=0.4, dur=0.08)
        dsp.place(y, c, 0.01)
        sp = dsp.bandpass(rng.standard_normal(ns(0.03)), 3000, 9000, 2) * np.exp(-np.arange(ns(0.03)) / (0.004 * SR))
        dsp.place(y, sp * 0.4, 0.012)
    else:
        f0 = rng.uniform(900, 1000)
        dsp.place(y, _drip(rng, f0), 0.01)
        dsp.place(y, _drip(rng, f0 * 1.09, 0.6), 0.47)
    y = dsp.reverb(y, dsp.make_ir(0.25, rng, predelay=0.003, hf_ratio=0.6), wet=0.12)
    return dsp.trim(dsp.highpass(y, 200, 2), -60)


def gutter(rng, i=1):
    """A downpipe after the rain: water trickling inside sheet metal, gurgling out at the bottom."""
    L = 8.0
    n = ns(L)
    w = ins.water_texture(n, rng, L=L, density=260, r_range=(1.0, 8.0), noise=0.35, splash=0.25)
    pipe = _cr(w, lambda x: dsp.resbank(x, [(310, 25, 1.0), (620, 30, 0.7), (935, 35, 0.5), (1260, 40, 0.35), (1880, 45, 0.2)]))
    flow = _lfo(n, 2, rng, 0.6, 1.0) * (1 + 0.25 * dsp.smooth(n, rng, 3.0))
    y = (_nrm(w) * 0.6 + _nrm(pipe) * 0.45) * flow
    for _ in range(14):  # bigger gulps at the outlet
        b = ins.bubble(rng, rng.uniform(6, 14), rng.uniform(0.4, 1.0))
        dsp.place(y, b * 0.5, rng.uniform(0, L), circular=True)
    return _fin_loop(y, rng, 0.3, 0.1)


def faucet_loop(rng, i=1):
    from sfx_rest import _water_stream, SINK_STEEL
    L = 6.0
    n = ns(L)
    w = _water_stream(n, rng, flow=1.0, splash=0.8, sink=None)
    w = w + _cr(w, lambda x: dsp.resbank(x, SINK_STEEL)) * 0.25
    y = _nrm(w) * (1 + 0.08 * dsp.smooth(n, rng, 2.0))
    return _fin_loop(y, rng)


def hand_dryer(rng, i=1):
    """5 s of a hand dryer: universal motor whine, the fan's blade tone, a roaring jet of air."""
    L = 5.0
    n = ns(L)
    t = np.arange(n) / SR
    fm = dsp.loop_freq(380.0, L) * (1 + 0.004 * np.sin(TAU * dsp.loop_freq(0.6, L) * t))
    ph = dsp.periodic_phase(fm)
    whine = np.sin(TAU * ph) + 0.6 * np.sin(2 * TAU * ph + 0.3) + 0.45 * np.sin(3 * TAU * ph + 1.1) + 0.3 * np.sin(5 * TAU * ph)
    blade = dsp.nb_noise(fm * 7.0, 40, rng, periodic_fix=True)
    air = dsp.colored(n, rng, -1.0, lo=150, hi=12000)
    air = _cr(air, lambda x: dsp.peq(x, 2600, 0.8, 4.0))
    rattle = dsp.colored(n, rng, 0.0, lo=3000, hi=9000) * (0.6 + 0.4 * np.cos(TAU * dsp.loop_freq(fm.mean() / 4.0, L) * t))
    y = _nrm(whine) * 0.25 + blade / np.std(blade) * 0.1 + air / np.std(air) * 0.5 + rattle / np.std(rattle) * 0.04
    y *= 1 + 0.05 * dsp.smooth(n, rng, 6.0)
    return _fin_loop(y, rng, 0.12, 0.05)


def fryer_loop(rng, i=1):
    """Hot oil: a dense sizzle of tiny bubbles bursting, and slow fat blubs."""
    L = 8.0
    n = ns(L)
    sz = np.zeros(n)
    k = int(L * 2600)
    pos = rng.integers(0, n, k)
    amp = rng.pareto(2.2, k) * rng.choice([-1, 1], k)
    np.add.at(sz, pos, amp)
    kern = np.exp(-np.arange(ns(0.0015)) / (0.00025 * SR))
    sz = dsp.circ_convolve(sz, kern)
    sz = _cr(sz, lambda x: dsp.highpass(x, 1800, 2))
    sz *= 1 + 0.3 * dsp.smooth(n, rng, 4.0)
    blub = np.zeros(n)
    for _ in range(int(L * 9)):
        b = ins.bubble(rng, rng.uniform(4.0, 11.0), rng.uniform(0.2, 1.0))
        dsp.place(blub, b, rng.uniform(0, L), circular=True)
    hiss = dsp.colored(n, rng, -0.5, lo=2500, hi=11000)
    y = sz / np.std(sz) * 0.4 + _nrm(blub) * 0.3 + hiss / np.std(hiss) * 0.08
    return _fin_loop(y, rng)


def gas_loop(rng, i=1):
    """A gas range burner at full: a soft roar with the flame's flutter."""
    L = 6.0
    n = ns(L)
    roar = dsp.colored(n, rng, -2.0, lo=90, hi=4500)
    flut = 1 + 0.18 * dsp.smooth(n, rng, 14.0) + 0.08 * dsp.smooth(n, rng, 40.0)
    hiss = dsp.colored(n, rng, -0.5, lo=1500, hi=8000)
    y = roar / np.std(roar) * flut * 0.5 + hiss / np.std(hiss) * 0.08
    y = _cr(y, lambda x: dsp.peq(x, 220, 1.0, 4.0))
    return _fin_loop(y, rng)


def dishwasher(rng, i=1):
    """Hood dishwasher: spray arms sweeping water over steel and plates, the pump's hum."""
    L = 8.0
    n = ns(L)
    t = np.arange(n) / SR
    sweep = 0.5 + 0.5 * np.cos(TAU * dsp.loop_freq(1.0, L) * t + rng.random() * 6)
    spray = dsp.colored(n, rng, -0.5, lo=400, hi=11000) * (0.55 + 0.45 * sweep)
    drum = _cr(spray, lambda x: dsp.resbank(x, [(240, 8, 1.0), (520, 10, 0.7), (870, 12, 0.5), (1430, 14, 0.4)]))
    pump = _hum(L, n, rng, 50.0, ((1, 0.4), (2, 1.0), (3, 0.3), (4, 0.35), (6, 0.15)))
    rain = np.zeros(n)
    for _ in range(int(L * 300)):
        dsp.place(rain, ins.bubble(rng, rng.uniform(0.8, 3.0), rng.uniform(0.05, 0.3)), rng.uniform(0, L), circular=True)
    y = spray / np.std(spray) * 0.35 + _nrm(drum) * 0.25 + _nrm(pump) * 0.2 + _nrm(rain) * 0.25
    y = _cr(y, lambda x: dsp.lowpass(x, 7000, 2))  # inside a steel hood
    return _fin_loop(y, rng)


def tv_static(rng, i=1):
    """The staff room's CRT on a dead channel: snow through its speaker, the 15.7 kHz flyback whine, mains buzz."""
    L = 6.0
    n = ns(L)
    t = np.arange(n) / SR
    snow = dsp.colored(n, rng, 0.0, lo=150, hi=8000)
    snow = _cr(snow, lambda x: dsp.peq(x, 2500, 1.0, 3.0))
    snow *= 1 + 0.15 * dsp.smooth(n, rng, 25.0)
    buzz = _hum(L, n, rng, 60.0, ((1, 0.5), (2, 0.3), (3, 0.4), (5, 0.2), (7, 0.1)))
    fly = _tone(15734.0, L, n, rng) * (1 + 0.1 * dsp.smooth(n, rng, 1.0))
    crk = ins.crackle(L, rng, 20, hp=1500)
    y = snow / np.std(snow) * 0.45 + _nrm(buzz) * 0.06 + fly * 0.035 + crk / (np.max(np.abs(crk)) + 1e-9) * 0.2
    return _fin_loop(y, rng, 0.2, 0.06)


def monitor_static(rng, i=1):
    """The bank of security monitors: CRT whine, a faint hiss and the buzz of four tubes."""
    L = 6.0
    n = ns(L)
    fly = sum(_tone(f, L, n, rng) * a for f, a in ((15734.0, 1.0), (15741.0, 0.7), (15722.0, 0.5)))
    buzz = _hum(L, n, rng, 50.0, ((2, 0.5), (4, 0.3), (6, 0.2), (8, 0.1))) * 0.3 + _zzz(L, n, rng, 50.0, 10, 1200, 6000) * 0.05
    hiss = dsp.colored(n, rng, -1.0, lo=500, hi=7000)
    y = fly * 0.05 + _nrm(buzz) * 0.3 + hiss / np.std(hiss) * 0.12
    y *= 1 + 0.2 * dsp.smooth(n, rng, 0.5)
    return _fin_loop(y, rng, 0.12, 0.04)


def phone_ring(rng, i=1):
    """The business phone at the register: an electronic warble, 1 s on, 2 s off, twice (6 s)."""
    L = 6.0
    n = ns(L)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for t0 in (0.0, 3.0):
        m = ns(1.0)
        tt = np.arange(m) / SR
        sel = (np.sin(TAU * 16.0 * tt) > 0).astype(float)
        sel = dsp.lowpass0(sel, 400.0)
        f = 1080.0 + 270.0 * sel
        ph = np.cumsum(f) / SR
        tone = np.sin(TAU * ph) + 0.4 * np.sign(np.sin(TAU * ph)) * 0.35 + 0.2 * np.sin(3 * TAU * ph)
        env = np.minimum(1.0, tt / 0.004) * np.minimum(1.0, (1.0 - tt) / 0.006)
        dsp.place(y, tone * env, t0 + 0.02, circular=True)
    y = _cr(y, lambda x: dsp.highpass(x, 450, 3))
    y = _cr(y, lambda x: dsp.lowpass(x, 6500, 2))
    y = _cr(y, lambda x: dsp.peq(x, 1400, 1.0, 4.0))
    body = _cr(y, lambda x: dsp.resbank(x, [(820, 12, 0.6), (1700, 14, 0.4), (2900, 16, 0.25)]))  # plastic housing
    y = _nrm(y) + _nrm(body) * 0.25
    return _fin_loop(y, rng, 0.15, 0.04)


# --------------------------------------------------------------------------
# spirits and the camera
# --------------------------------------------------------------------------
def spirit_near(rng, i=1):
    """Pressure near a spirit: a sub swell you feel more than hear (its harmonics carry it), 40 Hz roughness,
    a high faint ringing, breath and whispers deep inside."""
    L = 8.0
    n = ns(L)
    t = np.arange(n) / SR
    sub = dsp.sub_rumble(n, 38.0, L=L, rng=rng, am_rate=5.0, am_depth=0.45, drive=2.6, bias=0.4, lp=420)
    rough = dsp.am(dsp.colored(n, rng, -4.0, lo=60, hi=600), 41.0, 0.7, L)
    ring = _tone(7900.0, L, n, rng) * 0.5 + _tone(7906.0, L, n, rng) * 0.5  # beating 6 Hz at the edge
    w = np.zeros(n)
    for t0, sc in ((0.8, "ko ko ni i ru"), (4.6, "mi te ru")):
        x = V.whisper(sc, rng, table=V.VF, scale=1.03, rate=0.7, bw=1.3)
        dsp.place(w, x, t0, circular=True)
    w = _cr(w, lambda x: dsp.bandpass(x, 400, 4000, 2))
    br = np.zeros(n)
    for t0, kind, d in ((0.1, "in", 1.4), (2.0, "out", 2.0)):
        b = V.breath(rng, kind, d, table=V.VM, scale=0.9)
        dsp.place(br, b, t0, circular=True)
    buzz = np.tanh(5.0 * (sub + 0.3)) - np.tanh(1.5)  # its harmonics, so small speakers carry it too
    buzz = _cr(buzz, lambda x: dsp.bandpass(x, 110, 700, 2))
    buzz = dsp.am(buzz, 41.0, 0.55, L)
    sw = _lfo(n, 1, rng, 0.35, 1.0, 1.5)
    y = (_nrm(sub) * 0.55 + _nrm(buzz) * 0.3 + rough / np.std(rough) * 0.12) * sw + ring * 0.006 + _nrm(w) * 0.05 + _nrm(br) * 0.05
    return dsp.dc_block(y, circular=True)


def camera_sense(rng, i=1):
    """The 霊感計: an irregular Geiger-like crackle, a thin coil whine that wavers, a breath of radio static."""
    L = 6.0
    n = ns(L)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    cr = np.zeros(n)
    tt = rng.uniform(0, 0.05)
    while tt < L:
        m = ns(0.006)
        c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0006 * SR))
        c = dsp.reson(c, rng.uniform(2200, 4200), 3.0, 2.0) + 0.5 * c
        dsp.place(cr, c * rng.uniform(0.3, 1.0), tt, circular=True)
        tt += rng.exponential(0.07) + 0.004
    f = dsp.loop_freq(3150.0, L) * (1 + 0.012 * np.sin(TAU * dsp.loop_freq(0.5, L) * t) + 0.004 * dsp.smooth(n, rng, 3.0))
    wh = np.sin(TAU * dsp.periodic_phase(f))
    st = dsp.colored(n, rng, -1.0, lo=500, hi=6000) * (0.5 + 0.5 * np.abs(dsp.smooth(n, rng, 4.0)))
    mono = _nrm(cr) * 0.55 + wh * 0.06 + st / np.std(st) * 0.05
    out[0] = mono + np.roll(_nrm(cr), ns(0.0007)) * 0.1
    out[1] = np.roll(mono, ns(0.0004)) + wh * 0.02
    return dsp.dc_block(out, circular=True)
