"""Building blocks shared by the HITORI generators: the apartment's materials and textures.

  rain     rain on window glass (drop ticks through the pane, rivulets), rain hiss outside, eaves drips on metal,
           the gutter's downpipe gurgle
  water    drops (Minnaert chirps), splashes, sloshing waves, bubble clouds rising out of a soaked plush
  fabric   cloth friction (dry / wet), fibres tearing, polyester stuffing pulled apart
  rice     grains poured, rustling inside a cloth body, stray grains on wood
  bodies   a soft heavy plush thump, wet squelch, wood creaks of an old house, tatami / flooring / tiles
  speakers a CRT TV's oval speaker, the intercom, a phone, a laptop

Everything takes an explicit np.random.Generator.  Functions with an ``L`` argument build periodic (loopable)
textures: events are placed modulo the loop and filters run circularly."""
import numpy as np
from scipy import signal

import dsp
import instruments as ins
from dsp import SR, TAU, ns


# --------------------------------------------------------------------------
# small helpers
# --------------------------------------------------------------------------
def nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def rmsn(x):
    """Unit RMS."""
    return x / (np.sqrt(np.mean(np.asarray(x) ** 2)) + 1e-12)


def lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0, phase=None):
    """Periodic raised-cosine LFO with an integer number of cycles over n samples."""
    ph = cycles * np.arange(n) / n + (rng.random() if phase is None else phase)
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * ph)) ** shape


def norm01(x):
    return (x - x.min()) / (x.max() - x.min() + 1e-12)


def cr(x, fn, pre=1.0):
    """Run a causal filter circularly (loops)."""
    return dsp.circ(fn, x, ns(pre))


def place(buf, x, t, gain=1.0, L=None):
    return dsp.place(buf, x, t, gain, circular=L is not None)


def tone(f, L, n, rng, phase=None):
    """Loop-quantised sine."""
    return np.sin(TAU * (dsp.loop_freq(f, L) * np.arange(n) / SR + (rng.random() if phase is None else phase)))


def hum(L, n, rng, f, harm):
    return sum(a * tone(f * k, L, n, rng) for k, a in harm)


def room(rng, rt, stereo=False, predelay=0.004, hf=0.5, lp=None):
    return dsp.make_ir(rt, rng, stereo=stereo, predelay=predelay, hf_ratio=hf, lp_hz=lp)


def rev(y, rng, rt=0.3, wet=0.1, dry=1.0, predelay=0.004, hf=0.5, lp=None, stereo=False):
    return dsp.reverb(y, room(rng, rt, stereo, predelay, hf, lp), wet=wet, dry=dry)


def fin(y, hp=40.0, thresh=-64.0, min_len=0.0):
    """One-shot finish: rumble filter, trim the inaudible tail."""
    return dsp.trim(dsp.highpass(y, hp, 2), thresh, min_len=min_len)


def env_shape(n, pts, power=1.0):
    return np.maximum(dsp.env_pts(n, pts), 0.0) ** power


def burst(rng, dur, lo, hi, attack=0.0005, decay=0.004, order=2):
    """A short band-limited noise burst (a contact, a click's body)."""
    m = max(8, ns(dur))
    b = rng.standard_normal(m) * dsp.env_ad(m, attack, decay)
    return dsp.bandpass(b, lo, min(hi, SR * 0.45), order)


def stream(dur, rng, rate, amp_env=None, pareto=2.4, L=None, cap=4.0):
    """Poisson impulse train (unit impulses with Pareto loudness, capped so no single grain sticks out) - the
    skeleton of every granular texture."""
    n = ns(dur)
    k = rng.poisson(rate * dur)
    pos = rng.integers(0, n, k)
    a = np.minimum(rng.pareto(pareto, k) + 0.2, cap) * rng.choice([-1.0, 1.0], k)
    if amp_env is not None:
        a *= amp_env[pos]
    x = np.zeros(n)
    np.add.at(x, pos, a)
    return x


def grain_bank(x, bands, decay=0.0015, L=None):
    """Colour an impulse train with a bank of short resonant grains: bands = [(fc, q, gain)].  Each impulse rings
    every band (a hard grain); the decay sets how sharp it is."""
    out = np.zeros_like(x, dtype=float)
    for fc, q, g in bands:
        b, a = dsp.rbj("bp", fc, q)
        f = (lambda s, b=b, a=a: signal.lfilter(b, a, s))
        out += (cr(x, f, 0.05) if L else f(x)) * g
    kl = max(3, ns(decay * 5))
    kern = np.exp(-np.arange(kl) / max(1.0, decay * SR))
    if L:
        return dsp.circ_convolve(out, kern)
    return signal.fftconvolve(out, kern)[: len(out)]


# --------------------------------------------------------------------------
# water
# --------------------------------------------------------------------------
def drip(rng, f0, vel=1.0, tau=None, splash=0.035):
    """A water drop falling into water: the rising Minnaert chirp of the bubble it traps, a tiny splash."""
    tau = tau or rng.uniform(0.012, 0.025)
    m = ns(0.14)
    t = np.arange(m) / SR
    f = f0 * (1 + 1.6 * (1 - np.exp(-t / (tau * 1.5))))
    y = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / tau) * np.minimum(1, t / 0.0008)
    L = ns(0.004)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * splash
    return y * vel


def drop_on(rng, surface="wood", vel=1.0):
    """A drop of water landing on a surface (no pool): tatami (a dull pat), wood (a tick), tile (a crisp tick),
    metal (a ping)."""
    if surface == "tatami":
        y = burst(rng, 0.02, 300, 2500, 0.0004, 0.003) + 0.5 * burst(rng, 0.015, 2000, 7000, 0.0002, 0.0012)
    elif surface == "wood":
        y = ins.modal_hit(rng, [(1900, 0.012, 1.0), (3300, 0.008, 0.6), (900, 0.015, 0.4)], hardness=0.6, vel=1.0,
                          dur=0.05, noise=0.6)
    elif surface == "tile":
        y = ins.modal_hit(rng, [(3200, 0.02, 1.0), (5100, 0.012, 0.6), (7300, 0.008, 0.3)], hardness=0.9, vel=1.0,
                          dur=0.06, noise=0.5)
    else:  # metal
        y = ins.modal_hit(rng, [(2400, 0.25, 1.0), (3900, 0.18, 0.5), (6100, 0.1, 0.3), (1300, 0.2, 0.3)],
                          hardness=1.0, vel=1.0, dur=0.4, noise=0.25)
    m = ns(0.03)
    sp = rng.standard_normal(m) * dsp.env_ad(m, 0.0003, 0.003)
    sp = dsp.bandpass(sp, 2500, 11000, 2)
    return (nrm(y) + 0.25 * dsp.pad_to(sp, len(y))) * vel


def bubbles(dur, rng, rate, r_range=(1.0, 6.0), env=None, L=None):
    """A cloud of Minnaert bubbles (rising out of a soaked plush, gurgles)."""
    n = ns(dur)
    y = np.zeros(n)
    k = rng.poisson(rate * dur)
    for _ in range(k):
        t0 = rng.uniform(0, dur)
        if env is not None and rng.random() > env[min(ns(t0), n - 1)]:
            continue
        r = np.exp(rng.uniform(np.log(r_range[0]), np.log(r_range[1])))
        place(y, ins.bubble(rng, r, rng.pareto(2.5) * 0.3 + 0.15), t0, L=L)
    return y


def splash(rng, size=1.0, dur=1.6, bright=1.0):
    """Something falling into deep water: the plop of the entering body with its cavity's low 'bloop', the spray,
    the crown collapsing, bubbles; then waves lapping."""
    n = ns(dur)
    y = np.zeros(n)
    t = np.arange(n) / SR
    m = ns(0.25)  # the cavity's resonance: a falling low bubble
    tt = np.arange(m) / SR
    f = (240.0 / size ** 0.5) * (1 + 0.8 * np.exp(-tt / 0.03))
    bloop = np.sin(TAU * np.cumsum(f) / SR) * dsp.env_ad(m, 0.004, 0.06)
    place(y, bloop * 0.9, 0.012)
    hit = rng.standard_normal(ns(0.08)) * dsp.env_ad(ns(0.08), 0.0008, 0.012)  # the slap on the surface
    place(y, dsp.bandpass(hit, 250, 4500, 2) * 1.2, 0.0)
    sp_n = ns(0.7)  # spray falling back
    spray = dsp.colored(sp_n, rng, 0.0, lo=1200, hi=11000 * bright) * env_shape(sp_n, [(0, 0), (0.02, 1.0), (0.12, 0.6), (0.7, 0)], 1.6)
    place(y, rmsn(spray) * 0.12, 0.01)
    drops = stream(0.8, rng, 120, env_shape(ns(0.8), [(0, 0), (0.05, 1.0), (0.6, 0.3), (0.8, 0)]))
    dr = grain_bank(drops, [(900, 3, 1.0), (1700, 4, 0.8), (3200, 4, 0.5)], decay=0.004)
    place(y, nrm(dr) * 0.25, 0.08)
    bb = bubbles(dur, rng, 160 * size, (0.8, 5.0), env=np.exp(-np.maximum(t - 0.05, 0) / 0.35) * (t > 0.03))
    y += nrm(bb) * 0.35
    lap = dsp.colored(n, rng, -4.0, lo=120, hi=1500) * env_shape(n, [(0, 0), (0.15, 0.0), (0.4, 0.8), (dur, 0.0)], 1.5)
    lap *= 0.6 + 0.4 * np.sin(TAU * 1.7 * t + rng.random() * 6) ** 2  # waves hitting the walls
    y += rmsn(lap) * 0.05
    return y


def slosh(dur, rng, waves, level=1.0, bright=0.6):
    """Water moving in a full tub: waves = [(t, strength)] lapping against the walls (low swishes with bubbles and
    a few drips)."""
    n = ns(dur)
    y = np.zeros(n)
    for t0, s in waves:
        d = rng.uniform(0.35, 0.7)
        m = ns(d)
        e = env_shape(m, [(0, 0), (d * 0.3, 1.0), (d, 0)], 1.4)
        sw = dsp.colored(m, rng, -3.0, lo=150, hi=1800 + 3000 * bright) * e
        sw = dsp.peq(sw, 420, 1.5, 6.0)
        place(y, rmsn(sw) * 0.25 * s, t0)
        bb = bubbles(d, rng, 90 * s, (1.5, 7.0), env=e)
        place(y, bb * 0.4 * s, t0 + 0.02)
        if s > 0.6:
            place(y, drip(rng, rng.uniform(500, 900), 0.3 * s), t0 + d * rng.uniform(0.5, 0.9))
    return y * level


# --------------------------------------------------------------------------
# rain
# --------------------------------------------------------------------------
def rain_glass(n, rng, rate=55.0, L=None, close=1.0):
    """Rain on a window pane heard from inside: each drop a small tick of the glass (damped high modes), a faint
    splash; a few rivulets trickling down; returns unit-peak mono."""
    dur = n / SR
    x = stream(dur, rng, rate, pareto=2.2)
    ticks = grain_bank(x, [(2350, 6, 1.0), (3700, 7, 0.8), (5200, 6, 0.55), (1450, 4, 0.5), (7400, 5, 0.3)], decay=0.0011, L=L)
    sp = stream(dur, rng, rate * 0.8, pareto=2.6)
    spl = grain_bank(sp, [(6500, 1.2, 1.0), (9500, 1.4, 0.6)], decay=0.0006, L=L)
    riv = np.zeros(n)
    for _ in range(max(1, int(dur / 7))):  # rivulets: little gurgles of water running down the glass
        d = rng.uniform(1.5, 4.0)
        m = ns(d)
        e = env_shape(m, [(0, 0), (0.3, 1.0), (d - 0.4, 0.8), (d, 0)])
        bb = bubbles(d, rng, 60, (0.5, 1.6), env=e)
        place(riv, bb, rng.uniform(0, dur), L=L)
    y = nrm(ticks) * 1.0 * close + nrm(spl) * 0.35 * close + nrm(riv) * 0.12
    return y


def rain_hiss(n, rng, L=None, bright=1.0, dens=1.0):
    """The wide hiss of rain falling on everything outside: pink-ish noise plus a dense sizzle of micro-impacts."""
    dur = n / SR
    base = dsp.colored(n, rng, -1.5, lo=250, hi=9000 * bright)
    base *= 1.0 + 0.12 * dsp.smooth(n, rng, 0.3) + 0.06 * dsp.smooth(n, rng, 3.0)
    x = stream(dur, rng, 1800 * dens, pareto=3.0)
    siz = grain_bank(x, [(3000, 1.0, 1.0), (6000, 1.2, 0.7), (1500, 1.0, 0.5)], decay=0.0005, L=L)
    return rmsn(base) * 0.6 + rmsn(siz) * 0.4


def eaves_drips(n, rng, streams=3, surface="metal", L=None, per=(0.7, 2.4), vel=1.0):
    """Drips falling from the eaves onto a rail / an AC unit / the balcony floor: each stream its own pulse."""
    dur = n / SR
    y = np.zeros(n)
    for s in range(streams):
        p = rng.uniform(*per)
        t = rng.uniform(0, p)
        g = rng.uniform(0.5, 1.0)
        while t < dur:
            d = drop_on(rng, surface, g * rng.uniform(0.6, 1.0) * vel)
            place(y, d, t, L=L)
            t += p * rng.uniform(0.8, 1.3)
    return y


def gutter(n, rng, L=None, flow=1.0):
    """Water running down a metal downpipe: gurgling bubbles through the pipe's resonances, a trickle."""
    dur = n / SR
    bb = bubbles(dur, rng, 260 * flow, (1.2, 8.0), L=L)
    trick = dsp.colored(n, rng, -2.0, lo=300, hi=5000) * (0.6 + 0.4 * norm01(dsp.smooth(n, rng, 2.0)))
    y = rmsn(bb) * 0.7 + rmsn(trick) * 0.25
    pipe = cr(y, lambda v: dsp.resbank(v, [(310, 8, 1.0), (620, 10, 0.7), (930, 12, 0.5), (1240, 12, 0.3)]))
    return rmsn(y) * 0.6 + rmsn(pipe) * 0.5


# --------------------------------------------------------------------------
# fabric, stuffing, rice
# --------------------------------------------------------------------------
def fabric(dur, rng, env, wet=0.0, coarse=1.0, bright=1.0):
    """Cloth sliding over cloth / a body: friction noise whose colour follows the speed, fibre crackle on top.
    env: per-sample movement intensity (0..1).  wet > 0: duller, squelchy, sticky clicks."""
    n = ns(dur)
    env = dsp.pad_to(np.asarray(env, float), n)
    hi = (6500 - 3500 * wet) * bright
    fr = dsp.colored(n, rng, -2.0, lo=350 - 150 * wet, hi=hi)
    fr = dsp.tv_filter(fr, 900 + 2600 * env * bright * (1 - 0.5 * wet), 0.7, "lp", block=128)
    fr *= env * (1 + 0.5 * dsp.smooth(n, rng, 25.0, periodic=False))
    x = stream(dur, rng, 600 * coarse, amp_env=env ** 1.5, pareto=2.6)
    fib = grain_bank(x, [(3800 * bright, 1.5, 1.0), (6800 * bright, 1.8, 0.6), (2200, 1.2, 0.5)], decay=0.0005)
    y = rmsn(fr) * 0.8 + rmsn(fib) * 0.25 * (1 - 0.6 * wet) * coarse
    if wet > 0:
        sq = bubbles(dur, rng, 120 * wet, (0.8, 3.0), env=env)
        y = y + rmsn(sq) * 0.35 * wet
        y = dsp.lowpass(y, 4500, 2)
    return y * np.maximum(env, 0.0) ** 0.3


def fibres_tear(dur, rng, rate=900.0, env=None, bright=1.0):
    """Woven fabric or felt tearing / fibres snapping: a dense crackle of tiny breaks."""
    n = ns(dur)
    env = np.ones(n) if env is None else dsp.pad_to(env, n)
    x = stream(dur, rng, rate, amp_env=env, pareto=2.0)
    return grain_bank(x, [(2600 * bright, 2.0, 1.0), (4300 * bright, 2.5, 0.8), (7000 * bright, 2.0, 0.5), (1500, 1.5, 0.4)], decay=0.0007)


def stuffing(dur, rng, env):
    """Polyester fibrefill pulled apart: a soft airy fizz (fibres sliding), tiny squeaky fibre stretches."""
    n = ns(dur)
    env = dsp.pad_to(env, n)
    fz = dsp.colored(n, rng, -1.0, lo=1200, hi=9000) * env * (1 + 0.6 * dsp.smooth(n, rng, 18.0, periodic=False))
    x = stream(dur, rng, 1500, amp_env=env ** 2, pareto=2.8)
    cr_ = grain_bank(x, [(5200, 3.0, 1.0), (8000, 3.0, 0.6), (3100, 2.0, 0.4)], decay=0.0004)
    sq = np.zeros(n)
    for _ in range(int(dur * 4)):
        t0 = rng.uniform(0, max(0.01, dur - 0.1))
        d = rng.uniform(0.02, 0.07)
        if env[min(ns(t0), n - 1)] < 0.2:
            continue
        sqk = ins.creak(d, rng, [(0, rng.uniform(600, 1200)), (d, rng.uniform(1500, 2600))], [(0, 0), (0.005, 1.0), (d, 0)],
                        modes=[(rng.uniform(2800, 4200), 18, 1.0), (rng.uniform(5000, 7000), 20, 0.5)], jitter=0.4)
        place(sq, sqk * rng.uniform(0.3, 1.0), t0)
    return rmsn(fz) * 0.6 + rmsn(cr_) * 0.25 + nrm(sq) * 0.06


def rice_pour(dur, rng, rate_env, into="fabric", bright=1.0):
    """Rice grains poured: a cascade of tiny hard clicks; into a cloth cavity they land dull, onto rice crisper,
    onto wood they tick.  rate_env: per-sample grains / second."""
    n = ns(dur)
    r = dsp.pad_to(np.asarray(rate_env, float), n)
    k = int(np.sum(r) / SR)
    pos = np.sort(rng.choice(n, size=max(k, 1), p=r / r.sum()))
    x = np.zeros(n)
    np.add.at(x, pos, np.minimum(rng.pareto(3.0, len(pos)) + 0.3, 2.2) * rng.choice([-1.0, 1.0], len(pos)))
    if into == "fabric":
        bands = [(1800, 2.0, 1.0), (3200, 2.5, 0.7), (5200, 2.0, 0.35)]
        dec = 0.0009
    elif into == "rice":
        bands = [(2800, 2.5, 1.0), (4600, 3.0, 0.8), (7200, 2.5, 0.45)]
        dec = 0.0006
    else:  # wood
        bands = [(2200, 6.0, 1.0), (3900, 7.0, 0.7), (6200, 6.0, 0.4)]
        dec = 0.0012
    bands = [(f * bright, q, g) for f, q, g in bands]
    return grain_bank(x, bands, decay=dec)


def rice_inside(dur, rng, env, muffle=1.0):
    """Rice shifting inside a (wet) cloth body: a soft granular hush, low-passed by the fabric."""
    n = ns(dur)
    env = dsp.pad_to(env, n)
    g = rice_pour(dur, rng, 4000 * env ** 1.5 + 5.0, into="rice", bright=0.8)
    hush = dsp.colored(n, rng, -1.0, lo=800, hi=6000) * env
    y = rmsn(g) * 0.8 + rmsn(hush) * 0.2
    return dsp.lowpass(y, 5200 - 2800 * muffle, 2)


# --------------------------------------------------------------------------
# bodies on floors
# --------------------------------------------------------------------------
def plush_thump(rng, weight=1.0, wet=0.0, floor="tatami", dur=0.45):
    """A soft heavy cloth body landing on / pressed into the floor: no hard transient, a low pillowy thud, the floor's
    own dull answer, rice inside shifting, water squelching out if it is soaked."""
    n = ns(dur)
    y = np.zeros(n)
    t = np.arange(n) / SR
    f0 = {"tatami": 95.0, "wood": 120.0, "tile": 160.0}[floor]
    th = np.sin(TAU * np.cumsum(f0 * (1 + 0.5 * np.exp(-t / 0.015))) / SR) * dsp.env_ad(n, 0.006, 0.035)
    soft = dsp.lowpass(rng.standard_normal(n), 700, 2) * dsp.env_ad(n, 0.004, 0.025)
    y += th * 0.8 * weight + soft * 0.5 * weight
    if floor == "wood":
        place(y, nrm(ins.modal_hit(rng, ins.WOOD_FLOOR, size=1.0, hardness=0.15, vel=1.0, dur=0.3, noise=0.1)) * 0.25 * weight, 0.0)
    elif floor == "tile":
        place(y, nrm(ins.modal_hit(rng, [(900, 0.03, 1.0), (1700, 0.02, 0.5)], hardness=0.2, vel=1.0, dur=0.15, noise=0.2)) * 0.15 * weight, 0.0)
    else:
        place(y, fabric(0.12, rng, env_shape(ns(0.12), [(0, 0), (0.01, 1.0), (0.12, 0)]), coarse=0.6) * 0.12, 0.0)
    rc = rice_inside(0.3, rng, env_shape(ns(0.3), [(0, 0), (0.01, 1.0), (0.3, 0)], 1.5), muffle=0.6 + 0.3 * wet)
    place(y, nrm(rc) * 0.18, 0.004)
    if wet > 0:
        m = ns(0.3)
        e = env_shape(m, [(0, 0), (0.008, 1.0), (0.3, 0)], 1.8)
        sq = bubbles(0.3, rng, 700, (0.6, 2.5), env=e) + dsp.lowpass(rng.standard_normal(m), 1800, 2) * e * 0.05
        sq = dsp.lowpass(sq, 4000, 2)
        place(y, nrm(sq) * 0.45 * wet, 0.003)
    return y


def creak(rng, dur=0.8, pitch=1.0, rate=(35, 90), amp=None, q=1.0, tonal=0.0, noise=0.06):
    """A wooden creak: stick-slip through resonant wood (rate rises and falls)."""
    r0, r1 = rate
    rp = [(0, r0), (dur * 0.5, r1), (dur, r0 * 0.8)]
    ap = amp or [(0, 0), (dur * 0.15, 1.0), (dur * 0.7, 0.7), (dur, 0)]
    return ins.creak(dur, rng, rp, ap, pitch=pitch, q_scale=q, tonal=tonal, noise=noise)


# --------------------------------------------------------------------------
# loudspeakers
# --------------------------------------------------------------------------
def tv_speaker(x, drive=1.0, circular=False):
    """A 1980s CRT TV's oval speaker in its plastic cabinet: no bass, a boxy hump, a peaky presence, rolled-off
    top, a little cone break-up."""
    def f(v):
        v = dsp.highpass(v, 170, 3)
        v = dsp.peq(v, 330, 1.4, 4.5)      # cabinet
        v = dsp.peq(v, 1250, 1.2, 2.0)
        v = dsp.peq(v, 2700, 2.2, 5.0)     # cone resonance
        v = dsp.lowpass(v, 7600, 3)
        return v
    y = cr(x, f, 0.5) if circular else f(x)
    if drive != 1.0:
        pk = np.max(np.abs(y)) + 1e-12
        y = dsp.sat(y / pk * drive, 1.0) * pk / drive
    return y


def intercom_speaker(x):
    """The intercom handset's tiny speaker behind a perforated plastic grille."""
    y = dsp.highpass(x, 420, 3)
    y = dsp.peq(y, 1900, 1.4, 5.0)
    y = dsp.peq(y, 3600, 2.0, 3.0)
    y = dsp.lowpass(y, 6200, 3)
    pk = np.max(np.abs(y)) + 1e-12
    return dsp.sat(y / pk * 1.3, 1.0) * pk / 1.3


def phone_speaker(x, drive=1.4):
    """A smartphone's loudspeaker: thin, bright, a little harsh when loud."""
    y = dsp.highpass(x, 650, 4)
    y = dsp.peq(y, 2400, 1.0, 4.0)
    y = dsp.lowpass(y, 9000, 3)
    pk = np.max(np.abs(y)) + 1e-12
    return dsp.sat(y / pk * drive, 1.0) * pk / drive


def laptop_speaker(x):
    """An old laptop's speakers under the keyboard."""
    y = dsp.highpass(x, 300, 3)
    y = dsp.peq(y, 900, 1.0, 2.0)
    y = dsp.peq(y, 3200, 1.6, 3.0)
    return dsp.lowpass(y, 9500, 2)


def mic(x, lo=200.0, hi=6000.0):
    """A cheap action camera's microphone (the feed's sound): band-limited, a bit of AGC squash."""
    y = dsp.highpass(x, lo, 3)
    y = dsp.lowpass(y, hi, 4)
    y = dsp.peq(y, 2500, 1.2, 3.0)
    return dsp.compress(y, thresh_db=-24.0, ratio=3.0, window=0.03)
