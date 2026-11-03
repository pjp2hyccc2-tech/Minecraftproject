"""The dead of HIDAMARI and the medium (mono, positional).

Every spirit has its own signature (docs/DESIGN.md section 5):
  来店者 visitor      wet boots, one dragging; breath fogging inside a full-face helmet; 「……まだかな」
  給仕 waitress       soft steps too regular (one shoe missing); 「いらっしゃいませ」 three ways; humming the jingle
  料理長 chef         the cleaver's rhythm and its abrupt stop; smoke in the lungs; skin still crackling
  かくれんぼの子 child small feet, giggles, the counting that goes wrong, the hide-and-seek calls
  鏡の女 mirror woman crying behind glass, palms squeaking on it, a scream that cracks it
  店長 manager        leather shoes, measured; keys at the belt; a tired monotone; typing in the dark office
  霊媒師 medium       an old woman's murmurs, the rin, the chant, the claps
Voices are formant-synthesised (voice.py) with explicit Japanese intonation so the words are roughly
recognisable; the subtitles carry them.  Positional sounds stay fairly dry (the acoustics engine adds the room),
except where an impossible reverb is the point."""
import numpy as np

import dsp
import instruments as ins
import jingle as J
import voice as V
from dsp import SR, TAU, ns


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _rev(y, rng, rt=0.3, wet=0.1, dry=1.0, lp=None, predelay=0.004):
    return dsp.reverb(y, dsp.make_ir(rt, rng, predelay=predelay, hf_ratio=0.5, lp_hz=lp), wet=wet, dry=dry)


def _fin(y, hp=50.0, thresh=-62.0, min_len=0.0):
    return dsp.trim(dsp.highpass(y, hp, 2), thresh, min_len=min_len)


def _hit(rng, modes, vel=1.0, size=1.0, hard=0.7, dur=0.4, noise=0.3):
    return ins.modal_hit(rng, modes, size=size, hardness=hard, vel=vel, dur=dur, noise=noise)


def _thud(rng, f_hi=110.0, f_lo=55.0, tau=0.05, dur=0.35, noise=0.6, lp=600.0):
    m = ns(dur)
    t = np.arange(m) / SR
    s = np.sin(TAU * np.cumsum(f_lo + (f_hi - f_lo) * np.exp(-t / 0.02)) / SR) * dsp.env_ad(m, 0.002, tau)
    s += dsp.lowpass(rng.standard_normal(m), lp, 2) * dsp.env_ad(m, 0.001, tau * 0.5) * noise
    return s


def _ear(y, rng, clicks=1.0):
    """Ultra-close: proximity bass, presence, air, wet mouth clicks."""
    y = dsp.highpass(y, 110, 2)
    y = dsp.close_mic(y, prox_db=5.0, pres_db=4.0, air_db=3.0)
    if clicks > 0:
        mc = V.mouth_clicks(len(y) / SR, rng, rate=1.5) * np.std(y) * 2.0 * clicks
        y = y + dsp.pad_to(mc, len(y))
    return y


# voices of the dead (table, formant scale, f0, tilt, breath, jitter, shimmer)
WAITRESS = dict(table=V.VF, scale=1.06, f0=246.0, tilt=-7.0, breath=0.3, jitter=0.008, shimmer=0.05)
CHILD = dict(table=V.VF, scale=1.24, f0=330.0, tilt=-8.0, breath=0.4, jitter=0.012, shimmer=0.07)
MANAGER = dict(table=V.VM, scale=0.97, f0=101.0, tilt=-10.0, breath=0.38, jitter=0.016, shimmer=0.09)
VISITOR = dict(table=V.VM, scale=1.0, f0=118.0, tilt=-10.0, breath=0.5, jitter=0.02, shimmer=0.1)
MEDIUM = dict(table=V.VF, scale=0.97, f0=182.0, tilt=-10.0, breath=0.55, jitter=0.025, shimmer=0.12)


def _say(script, rng, who, pitch, durs, **kw):
    p = dict(who)
    p.update(kw)
    y, f = V.speak_line(script, rng, pitch, durs, **p)
    return y


def _helmet(y, rng, amount=1.0):
    """Inside a full-face helmet: the shell's resonances, the padding eating the highs, the visor's fog close in."""
    r = dsp.resbank(y, [(420, 6, 1.0), (780, 7, 0.8), (1350, 8, 0.5), (2100, 9, 0.3)])
    z = dsp.lowpass(y, 1900, 3)
    out = z * (1.0 - 0.4 * amount) + r / (np.std(r) + 1e-9) * np.std(z) * 0.55 * amount
    return dsp.lowpass(out, 3200, 2)


# ==========================================================================
# 来店者 the visitor
# ==========================================================================
def _squelch(rng, vel=1.0, dur=0.22):
    """A boot full of water pressed into the floor."""
    m = ns(dur)
    w = ins.water_texture(m, rng, density=700, r_range=(1.0, 6.0), noise=0.35, splash=0.2)
    w = dsp.lowpass(w, 3500, 2) * dsp.env_pts(m, [(0, 0), (0.01, 1.0), (dur * 0.4, 0.6), (dur, 0)]) ** 1.3
    sq = ins.creak(dur * 0.6, rng, [(0, 120), (dur * 0.3, 260), (dur * 0.6, 90)], [(0, 0), (0.01, 0.6), (dur * 0.6, 0)],
                   modes=[(380, 4, 1.0), (760, 5, 0.5), (1300, 6, 0.3)], jitter=0.5)
    return (_nrm(w) * 0.7 + dsp.pad_to(_nrm(sq), m) * 0.25) * vel


def visitor_step(rng, i=1):
    """Soaked leather boots; on 2 and 4 the broken leg's boot drags."""
    y = np.zeros(ns(0.9))
    leather = [(310, 0.04, 1.0), (640, 0.03, 0.6), (1150, 0.02, 0.3)]
    dsp.place(y, _hit(rng, leather, vel=0.8, size=1.1, hard=0.4, dur=0.15, noise=0.4), 0.01)
    dsp.place(y, _thud(rng, 120, 70, 0.03, 0.2, noise=0.5) * 0.5, 0.01)
    dsp.place(y, _squelch(rng, 1.0), 0.015)
    if i in (2, 4):  # the dragged foot: wet rubber and leather scraping the floor
        d = 0.45
        m = ns(d)
        dr = ins.friction_pulses(d, rng, np.linspace(70, 140, m), dsp.env_pts(m, [(0, 0), (0.05, 1.0), (d * 0.8, 0.8), (d, 0)]), jitter=0.45)
        dr = dsp.resbank(dr, [(260, 5, 1.0), (520, 6, 0.6), (980, 8, 0.4)]) + dsp.bandpass(rng.standard_normal(m), 800, 5000, 2) * 0.02
        wet = ins.water_texture(m, rng, density=300, r_range=(1.0, 4.0), noise=0.2, splash=0.1) * dsp.env_pts(m, [(0, 0), (0.1, 1), (d, 0)])
        dsp.place(y, _nrm(dr) * 0.35 + _nrm(wet) * 0.15, 0.2)
    for k in range(rng.integers(1, 3)):  # water running off him
        from sfx_beds import _drip
        dsp.place(y, _drip(rng, rng.uniform(700, 1100), rng.uniform(0.2, 0.4)), rng.uniform(0.15, 0.7))
    return _fin(_rev(y, rng, 0.15, 0.06), 50)


def visitor_breath(rng, i=1):
    """Breathing inside a helmet: slow, wet, a rattle in the chest, the visor fogging."""
    seq = [("in", 1.3), ("out", 1.8)] if i == 1 else [("in", 0.9), ("out", 0.6), ("in", 1.1), ("out", 1.9)]
    y = np.zeros(ns(sum(d for _, d in seq) + 0.8))
    t = 0.05
    for kind, d in seq:
        b = V.breath(rng, kind, d, table=V.VM, scale=1.0, wet=0.7, rasp=0.35 if kind == "out" else 0.15,
                     voiced=0.12 if kind == "out" else 0.0, f0=105, vowel_out="o", tremor=0.2 if i == 2 else 0.0)
        dsp.place(y, b, t)
        t += d + rng.uniform(0.05, 0.25)
    fog = dsp.bandpass(rng.standard_normal(len(y)), 2500, 7000, 2) * dsp.lowpass0(np.abs(y), 6.0) * 1.5
    y = _helmet(y, rng) + fog * 0.15
    y = dsp.close_mic(y, 4.0, 1.0, 0.0)
    return _fin(y, 70, -60)


def visitor_visor(rng, i=1):
    """The cracked visor gives a little more: stress ticks, a sharp crack running across polycarbonate."""
    y = np.zeros(ns(1.2))
    t = 0.02
    for k in range(6):  # creaking stress before it goes
        dsp.place(y, _hit(rng, [(rng.uniform(2500, 4500), 0.01, 1.0), (rng.uniform(5000, 8000), 0.006, 0.5)], vel=rng.uniform(0.1, 0.3),
                          hard=1.0, dur=0.03, noise=0.6), t)
        t += rng.uniform(0.03, 0.08)
    m = ns(0.06)
    cr = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.003 * SR))
    cr = dsp.highpass(cr, 1800, 2) + dsp.resbank(cr, [(1650, 12, 1.0), (3300, 14, 0.7), (5100, 16, 0.4)]) * 0.6
    dsp.place(y, cr * 1.4, t + 0.05)
    run = ins.crackle(0.25, rng, 900, env=dsp.env_pts(ns(0.25), [(0, 1), (0.25, 0)]), hp=3000, decay=0.0002)
    dsp.place(y, _nrm(run) * 0.35, t + 0.06)
    dsp.place(y, _hit(rng, [(880, 0.08, 1.0), (1700, 0.05, 0.6), (2900, 0.03, 0.3)], vel=0.4, hard=0.9, dur=0.25), t + 0.05)
    return _fin(_rev(y, rng, 0.12, 0.05), 200)


def visitor_drip(rng, i=1):
    from sfx_beds import _drip
    y = np.zeros(ns(2.2))
    t = 0.02
    for k in range(rng.integers(3, 6)):
        d = _drip(rng, rng.uniform(650, 1050), rng.uniform(0.5, 1.0))
        if rng.random() < 0.5:  # onto the floor, not into water: a small splat
            m = ns(0.03)
            d = dsp.mix(d * 0.4, dsp.bandpass(rng.standard_normal(m), 1500, 8000, 2) * np.exp(-np.arange(m) / (0.003 * SR)) * 0.6)
        dsp.place(y, d, t)
        t += rng.uniform(0.15, 0.55) * (1 + 0.6 * k / 5)
    return _fin(_rev(y, rng, 0.15, 0.08), 200)


def visitor_whisper(rng, i=1):
    """「……まだかな」 inside the helmet: half whispered, half voiced, sad."""
    if i == 1:
        w = V.whisper_line("ma da ka na", rng, [0.16, 0.12, 0.12, 0.42], table=V.VM, scale=1.0, voicing=0.35, f0=112,
                           close=0.4, bw=1.25)
    else:
        w = V.whisper_line("ma da , ka na -", rng, [0.2, 0.15, 0.13, 0.3], table=V.VM, scale=1.0, voicing=0.2, f0=105,
                           close=0.5, bw=1.25, pauses={2: 0.55})
    y = np.zeros(len(w) + ns(1.6))
    b = V.breath(rng, "in", 0.5, table=V.VM, scale=1.0, wet=0.5)
    dsp.place(y, b * 0.4 * np.std(w) / (np.std(b) + 1e-9), 0.0)
    dsp.place(y, w, 0.45)
    ex = V.breath(rng, "out", 1.1, table=V.VM, scale=1.0, wet=0.6, rasp=0.3)
    dsp.place(y, ex * 0.5 * np.std(w) / (np.std(ex) + 1e-9), 0.45 + len(w) / SR - 0.05)
    y = _helmet(y, rng, 0.9)
    y = dsp.close_mic(y, 4.0, 2.0, 0.0)
    return _fin(y, 80, -60)


# ==========================================================================
# 給仕 the waitress
# ==========================================================================
def waitress_step(rng, i=1):
    """Soft rubber-soled work shoes on the vinyl, so regular it is wrong; variant 3 is the foot with no shoe."""
    y = np.zeros(ns(0.45))
    vinyl = [(520, 0.03, 1.0), (980, 0.025, 0.6), (1600, 0.02, 0.35), (260, 0.04, 0.6)]
    if i < 3:
        dsp.place(y, _hit(rng, vinyl, vel=0.7, size=1.0, hard=0.35, dur=0.15, noise=0.25), 0.008)
        dsp.place(y, _thud(rng, 110, 80, 0.02, 0.12, noise=0.3) * 0.5, 0.008)
        dsp.place(y, _hit(rng, vinyl, vel=0.4, size=1.05, hard=0.3, dur=0.12, noise=0.2), 0.098)  # exactly 90 ms later
    else:  # a stockinged foot: a soft pad, nylon sliding, a faint squeak
        dsp.place(y, _thud(rng, 95, 70, 0.025, 0.15, noise=0.4, lp=500) * 0.6, 0.008)
        m = ns(0.12)
        sl = dsp.bandpass(rng.standard_normal(m), 2500, 9000, 2) * dsp.env_pts(m, [(0, 0), (0.03, 1), (0.12, 0)])
        dsp.place(y, sl * 0.08, 0.04)
        sq = ins.creak(0.06, rng, [(0, 300), (0.06, 500)], [(0, 0), (0.01, 0.5), (0.06, 0)], modes=[(1900, 18, 1.0), (3800, 22, 0.4)], tonal=0.5)
        dsp.place(y, sq * 0.05, 0.09)
    return _fin(_rev(y, rng, 0.12, 0.05), 50)


def _greet(rng, rate=1.0, f0=246.0, scale=1.06, stutter=0):
    """「いらっしゃいませ」 in the bright, sing-song service voice (accent: i↗RASSHAIMA↘se)."""
    if stutter:
        script = "i ra q sha i " + " ".join(["ma"] * (stutter + 1)) + " se"
        pitch = [0, 5.5, (5, 4.5), 4] + [3 + 1.4 * k for k in range(stutter + 1)] + [(-1, -3)]
        durs = [0.07, 0.1, 0.12, 0.08] + [0.075] * (stutter + 1) + [0.3]
    else:
        script = "i ra q sha i ma se"
        pitch = [0, 5.5, (5, 4.5), 4, 3, (-0.5, -3)]
        durs = [0.07, 0.1, 0.12, 0.08, 0.09, 0.3]
    return _say(script, rng, WAITRESS, pitch, [d / rate for d in durs], f0=f0, scale=scale, vibrato=6.0, wander=0.15)


def waitress_greet(rng, i=1):
    if i == 1:  # almost normal: bright, polite... and a smile you can hear, held a little too long
        v = _greet(rng)
        y = np.zeros(len(v) + ns(0.6))
        dsp.place(y, v, 0.02)
        y = _rev(y, rng, 0.3, 0.12)
    elif i == 2:  # slowed: a tape at half speed in a room far bigger than the restaurant
        v = _greet(rng, rate=0.95, f0=250.0)
        v = dsp.varispeed(v, np.linspace(0.62, 0.5, int(len(v) / 0.56)))
        y = np.zeros(len(v) + ns(3.2))
        dsp.place(y, v, 0.02)
        y = dsp.reverb(y, dsp.make_ir(3.4, rng, predelay=0.05, hf_ratio=0.3), wet=0.7, dry=0.8)
    else:  # too many syllables: the "ma" sticks and climbs
        v = _greet(rng, stutter=4)
        y = np.zeros(len(v) + ns(0.6))
        dsp.place(y, v, 0.02)
        y = _rev(y, rng, 0.3, 0.12)
    return _fin(y, 120)


def waitress_water(rng, i=1):
    """「お冷やを、どうぞ」 - soft, close, bending over the table."""
    v = _say("o hi ya o , do u zo", rng, WAITRESS, [0, 4, 4, (3, 2.5), 5, 2, (0, -2.5)],
             [0.09, 0.08, 0.1, 0.12, 0.11, 0.1, 0.32], pauses={4: 0.38}, f0=232.0, breath=0.45, vibrato=4.0, wander=0.15)
    y = np.zeros(len(v) + ns(0.5))
    dsp.place(y, v, 0.02)
    y = dsp.close_mic(y, 2.0, 2.0, 1.5)
    return _fin(_rev(y, rng, 0.25, 0.1), 120)


def waitress_hum(rng, i=1):
    """Humming the store jingle under her breath, off key, slower than the record."""
    beat = 0.62
    notes = [(0.3 + b * beat, d * beat * 0.92, J.hz(nm) * 0.5 * 2 ** (-0.6 / 12), 0.85) for b, nm, d in J.MELODY[:15]]
    wrong = {4: -110.0, 9: 140.0, 13: -60.0}
    y = V.hum(rng, notes, table=V.VF, scale=1.05, vowel="m", breath=0.45, vibrato=10, scoop=80,
              off_key=lambda k: wrong.get(k, -25.0 + 20.0 * np.sin(k)))
    y = dsp.close_mic(y, 2.0, 1.0, 1.0)
    return _fin(_rev(y, rng, 0.3, 0.12), 100)


def waitress_tray(rng, i=1):
    """A round tray hits the table, wobbles to rest; the glass on it rocks and chimes."""
    y = np.zeros(ns(2.0))
    tray = [(380, 0.25, 0.8), (690, 0.2, 1.0), (1150, 0.16, 0.8), (1720, 0.12, 0.6), (2480, 0.09, 0.4), (3500, 0.06, 0.25)]
    dsp.place(y, _hit(rng, tray, vel=1.0, size=1.0, hard=0.8, dur=0.6, noise=0.4), 0.01)
    t, per, v = 0.12, 0.09, 0.6
    while per > 0.012 and t < 1.4:  # the rim rolling around, faster and faster
        dsp.place(y, _hit(rng, tray, vel=v, size=1.0, hard=0.7, dur=0.25, noise=0.3), t)
        t += per
        per *= 0.84
        v *= 0.93
    glass = [(1480, 0.35, 1.0), (3620, 0.2, 0.55), (6100, 0.12, 0.3)]
    for t0, gv in ((0.02, 0.6), (0.16, 0.4), (0.27, 0.25)):
        dsp.place(y, _hit(rng, glass, vel=gv, size=1.0, hard=1.0, dur=0.6), t0)
    y = 0.6 * dsp.sat(_nrm(y) * 1.4, 1.0) + 0.4 * _nrm(y)
    return _fin(_rev(y, rng, 0.15, 0.05), 60)


# ==========================================================================
# 料理長 the chef
# ==========================================================================
BOARD = [(140, 0.06, 1.0), (260, 0.05, 0.9), (430, 0.04, 0.7), (690, 0.03, 0.5), (1050, 0.025, 0.3)]
BLADE = [(2300, 0.15, 1.0), (3700, 0.1, 0.6), (5300, 0.07, 0.35)]
STEEL = [(410, 0.6, 0.8), (980, 0.5, 1.0), (1730, 0.4, 0.8), (2650, 0.3, 0.6), (3900, 0.2, 0.4), (5400, 0.15, 0.25)]


def _chop(rng, vel=1.0, veg=True):
    y = np.zeros(ns(0.35))
    dsp.place(y, _hit(rng, BOARD, vel=vel, size=rng.uniform(0.95, 1.05), hard=0.85, dur=0.25, noise=0.5), 0.0)
    dsp.place(y, _thud(rng, 160, 90, 0.025, 0.15, noise=0.5, lp=1200) * 0.5 * vel, 0.0)
    dsp.place(y, _hit(rng, BLADE, vel=0.12 * vel, size=1.0, hard=1.0, dur=0.2, noise=0.1), 0.001)
    if veg:  # through something crisp
        m = ns(0.03)
        dsp.place(y, dsp.bandpass(rng.standard_normal(m), 1500, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.004, 1), (0.03, 0)]) * 0.25 * vel, 0.0)
    return y


def chef_chop(rng, i=1):
    """A 4 s loop segment of the cleaver's rhythm on the board (seamless; a chop 30 ms after the start)."""
    L = 4.0
    n = ns(L)
    y = np.zeros(n)
    if i == 1:  # steady eighths at 120 bpm with an accent pattern
        times = [k * 0.25 for k in range(16)]
        vels = [1.0 if k % 4 == 0 else (0.8 if k % 2 == 0 else 0.65) for k in range(16)]
    else:  # bursts: fast triplets, a pause, a heavy one
        times, vels = [], []
        for b0 in (0.0, 1.0, 2.0, 3.0):
            for k, dt in enumerate((0.0, 0.14, 0.28, 0.42) if b0 != 2.0 else (0.0, 0.5)):
                times.append(b0 + dt)
                vels.append(1.0 if k == 0 else 0.7)
    for t0, v in zip(times, vels):
        dsp.place(y, _chop(rng, v * rng.uniform(0.9, 1.05)), t0 + float(np.clip(rng.normal(0, 0.004), -0.008, 0.008)) + 0.03,
                  circular=True)  # the seam falls in a decay, 30 ms before a chop
    y = dsp.circ_reverb(y, dsp.make_ir(0.2, rng, predelay=0.003, hf_ratio=0.8), wet=0.06, dry=1.0)
    return dsp.dc_block(y, circular=True)


def chef_chop_stop(rng, i=1):
    """The last chop, harder, the cleaver left standing in the board, ringing - then nothing."""
    y = np.zeros(ns(2.4))
    for k, (t0, v) in enumerate(((0.0, 0.8), (0.25, 0.75), (0.5, 0.85))):
        dsp.place(y, _chop(rng, v), t0 + 0.01)
    t1 = 0.95  # a beat too late
    dsp.place(y, _chop(rng, 1.4, veg=False), t1)
    dsp.place(y, _hit(rng, BLADE, vel=0.5, size=1.0, hard=1.0, dur=1.2, noise=0.1), t1 + 0.002)
    dsp.place(y, _thud(rng, 120, 60, 0.06, 0.4, noise=0.8, lp=900) * 0.7, t1)
    y = 0.6 * dsp.sat(_nrm(y) * 1.5, 1.0) + 0.4 * _nrm(y)
    return _fin(_rev(y, rng, 0.2, 0.06), 50)


def chef_breath(rng, i=1):
    """Lungs full of smoke: wheezing (stridor) on the in-breath, wet crackles, the out-breath rattling."""
    seq = [("in", 1.4), ("out", 1.9)] if i == 1 else [("in", 1.0), ("out", 0.8), ("in", 0.6), ("out", 1.6)]
    y = np.zeros(ns(sum(d for _, d in seq) + 1.0))
    t = 0.05
    for kind, d in seq:
        b = V.breath(rng, kind, d, table=V.VM, scale=0.92, wet=1.2, rasp=0.6 if kind == "out" else 0.3, voiced=0.15 if kind == "out" else 0,
                     f0=88, vowel_out="o", hiss=0.3 if kind == "in" else 0.0)
        m = len(b)
        if kind == "in":  # stridor: narrow whistles in the airway
            fw = rng.uniform(780, 1100) * dsp.cents(dsp.env_pts(m, [(0, -100), (d, 150)]) + 40 * dsp.smooth(m, rng, 6.0, periodic=False))
            wz = dsp.nb_noise(fw, 30, rng) + 0.4 * dsp.nb_noise(fw * 2.03, 50, rng)
            b = b + wz * dsp.env_pts(m, [(0, 0), (0.15, 1.0), (d, 0.3)]) * np.std(b) * 0.5
        dsp.place(y, b, t)
        t += d + rng.uniform(0.05, 0.2)
    if i == 2:  # a dry, broken cough
        for k in range(2):
            c, _ = V.scream(0.18, rng, [(0, 160), (0.18, 120)], [(0, 0), (0.01, 1.0), (0.18, 0)], vowel="a",
                            chaos_pts=[(0, 3.9), (0.18, 3.9)], breath=1.2, rasp=1.5, scale=0.9, oq=0.5)
            dsp.place(y, _nrm(c) * np.max(np.abs(y)) * 0.8, t + 0.1 + k * 0.28)
    cr = ins.crackle(len(y) / SR, rng, 25, hp=2500, decay=0.0004)  # embers in the skin
    y = y + _nrm(cr) * np.max(np.abs(y)) * 0.08
    y = dsp.close_mic(y, 3.0, 2.0, 0.5)
    return _fin(_rev(y, rng, 0.2, 0.06), 70, -60)


def chef_sizzle(rng, i=1):
    """Burnt skin still crackling: fat sizzle, embers popping."""
    dur = 2.4
    n = ns(dur)
    env = dsp.env_pts(n, [(0, 0), (0.25, 1.0), (1.6, 0.8), (dur, 0)])
    sz = ins.crackle(dur, rng, 1800, env=env, hp=2200, decay=0.00025)
    pops = np.zeros(n)
    for k in range(14):
        m = ns(0.02)
        p = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.0015 * SR))
        dsp.place(pops, dsp.bandpass(p, 600, 6000, 2) * rng.uniform(0.4, 1.0), rng.uniform(0.1, dur - 0.1))
    hs = dsp.bandpass(rng.standard_normal(n), 3000, 10000, 2) * env
    y = _nrm(sz) * 0.5 + _nrm(pops) * 0.5 + hs * 0.04
    return _fin(_rev(y, rng, 0.15, 0.05), 300)


def chef_roar(rng, i=1):
    """A furious roar through a burnt throat, and the cleaver slammed into the steel counter."""
    dur = 2.2
    s, _ = V.scream(dur, rng, [(0, 120), (0.12, 210), (0.6, 260), (1.4, 230), (dur, 150)],
                    [(0, 0), (0.05, 0.8), (0.25, 1.0), (1.6, 0.9), (dur, 0)], vowel_pts=[(0, "o"), (0.4, "a"), (1.6, "a"), (dur, "o")],
                    chaos_pts=[(0, 3.92), (0.5, 3.88), (0.55, 3.35), (0.95, 3.35), (1.0, 3.94), (dur, 3.95)], ct=0.4, ca=0.55,
                    breath=0.7, rasp=1.6, rough=(0.7, 52, 0.6), scale=0.88, oq=0.45, closure=0.8)
    y = np.zeros(ns(dur + 2.2))
    dsp.place(y, _nrm(s), 0.02)
    t1 = 1.25
    dsp.place(y, _hit(rng, STEEL, vel=1.0, size=1.0, hard=1.0, dur=2.0, noise=0.6) * 0.9, t1)
    dsp.place(y, _thud(rng, 140, 70, 0.05, 0.3, noise=1.0, lp=2500) * 0.8, t1)
    y = 0.6 * dsp.sat(_nrm(y) * 2.2, 1.0) + 0.4 * _nrm(y)
    return _fin(_rev(y, rng, 0.25, 0.08), 50)


def chef_step(rng, i=1):
    """A heavy man in kitchen clogs on quarry tiles."""
    y = np.zeros(ns(0.6))
    tile = [(1180, 0.04, 1.0), (2100, 0.03, 0.7), (3500, 0.02, 0.45), (640, 0.05, 0.5)]
    dsp.place(y, _hit(rng, tile, vel=1.0, size=1.0, hard=0.75, dur=0.2, noise=0.35), 0.01)
    dsp.place(y, _thud(rng, 100, 55, 0.05, 0.3, noise=0.8, lp=500) * 0.9, 0.01)
    dsp.place(y, _hit(rng, tile, vel=0.5, size=1.0, hard=0.6, dur=0.15), 0.13 + 0.03 * (i - 2))
    if i == 3:  # the grease grabs the sole
        m = ns(0.07)
        pe = ins.friction_pulses(0.07, rng, np.linspace(800, 250, m), dsp.env_pts(m, [(0, 0), (0.005, 1), (0.07, 0)]), jitter=0.5)
        dsp.place(y, dsp.bandpass(pe, 900, 6000, 2) * 0.6, 0.24)
    return _fin(_rev(y, rng, 0.15, 0.05), 40)


def chef_scrape(rng, i=1):
    """The cleaver dragged slowly along the stainless counter."""
    d = 1.5
    m = ns(d)
    y = np.zeros(ns(d + 1.0))
    rate = dsp.env_pts(m, [(0, 160), (0.4, 260), (1.0, 210), (d, 120)])
    fp = ins.friction_pulses(d, rng, rate, dsp.env_pts(m, [(0, 0), (0.05, 1.0), (d * 0.8, 0.9), (d, 0)]), jitter=0.3)
    body = dsp.resbank(fp, [(f, 25, a) for f, _, a in STEEL] + [(f, 30, a) for f, _, a in BLADE])
    sq = dsp.nb_noise(rate * 6.0, 60, rng) * dsp.env_pts(m, [(0, 0), (0.1, 1), (d, 0.4)])
    dsp.place(y, _nrm(body) * 0.8 + sq * 0.05, 0.02)
    dsp.place(y, _hit(rng, STEEL, vel=0.4, size=1.0, hard=0.9, dur=0.9), d)  # the blade's tip leaves the edge
    return _fin(_rev(y, rng, 0.2, 0.06), 80)


# ==========================================================================
# かくれんぼの子 the child
# ==========================================================================
def child_giggle(rng, i=1):
    if i == 1:  # ふふふ, mouth closed
        y = V.laugh(rng, n_syl=5, f0=360, decline=0.03, table=V.VF, scale=1.22, nasal=True, breath=1.1, tilt=-10)
    elif i == 2:  # くすくす, mostly breath
        y = V.laugh(rng, n_syl=6, f0=400, decline=0.02, gap=(0.03, 0.06), syl=(0.06, 0.09), vowel="u", table=V.VF,
                    scale=1.24, breath=2.4, tilt=-12, h_gain=2.0)
    elif i == 3:  # a bright little peal, running away
        y = V.laugh(rng, n_syl=7, f0=430, decline=0.025, gap=(0.04, 0.07), syl=(0.07, 0.11), vowel="a", table=V.VF,
                    scale=1.24, breath=1.0, tilt=-8)
        n = len(y)
        y = dsp.tv_filter(y, dsp.env_pts(n, [(0, 9000), (n / SR, 2500)]), 0.6, "lp", block=256) * dsp.env_pts(n, [(0, 1.0), (n / SR, 0.4)])
    else:  # right behind you: dry, breathy, at the ear
        y = V.laugh(rng, n_syl=4, f0=380, decline=0.03, gap=(0.05, 0.08), syl=(0.06, 0.09), vowel="i", table=V.VF,
                    scale=1.24, breath=1.8, tilt=-11, h_gain=1.6)
        y = _ear(y, rng, 0.6)
        return dsp.trim(y, -60)
    y = dsp.highpass(y, 180, 2)
    return _fin(_rev(y, rng, 0.25, 0.1), 180)


def child_count(rng, i=1):
    """「いーち、にーい、さーん、しーい、ごーお、ろーく」 sing-song, face to the pillar; then it sticks: ろーく……ろーく……"""
    script = "i - chi , ni - i , sa - N , shi - i , go - o , ro - ku , ro - ku , ro - ku"
    #          i   chi    ni   i    sa     shi  i    go   o    ro   ku   ro   ku   ro   ku
    pitch = [5, 2, 5, 2, 5, 5, 2, 5, 2, 5, 2, 3, 0, 0.5, -2.5]
    durs = [0.24, 0.16, 0.24, 0.2, 0.26, 0.22, 0.2, 0.24, 0.2, 0.26, 0.16, 0.34, 0.2, 0.46, 0.3]
    v = _say(script, rng, CHILD, pitch, durs, pauses={3: 0.2, 7: 0.22, 11: 0.22, 15: 0.24, 19: 0.26, 23: 0.75, 27: 1.2},
             vibrato=8.0, wander=0.2)
    n = len(v)
    tt = np.arange(n) / SR
    slow = np.where(tt < n / SR * 0.62, 1.0, np.interp(tt, [n / SR * 0.62, n / SR], [1.0, 0.82]))
    v = dsp.varispeed(v, slow)  # the last ones sag, slower and lower
    y = np.zeros(len(v) + ns(0.8))
    dsp.place(y, v, 0.05)
    return _fin(_rev(y, rng, 0.35, 0.12), 150)


def child_moui(rng, i=1):
    """「もういいかい」 called out from nowhere: the hide-and-seek chant."""
    v = _say("mo - i i ka i -", rng, CHILD, [5, 5, 5, 2, 5, (2.5, 2)], [0.2, 0.14, 0.18, 0.2, 0.16, 0.38],
             vibrato=12.0, wander=0.15, f0=340.0)
    y = np.zeros(len(v) + ns(3.0))
    dsp.place(y, v, 0.05)
    y = dsp.highpass(y, 200, 2)
    y = dsp.far(y, rng, rt60=2.2, wet=0.45, dry=0.85, lp=6500, predelay=0.03, taps=[(0.23, 0.18, 3000)])
    return _fin(y, 150)


def child_madada(rng, i=1):
    """「まーだだよ」 the answer, sing-song."""
    v = _say("ma - da da yo -", rng, CHILD, [5, 2, 2, (5, 3)], [0.22, 0.12, 0.14, 0.42], vibrato=10.0, wander=0.15, f0=345.0)
    y = np.zeros(len(v) + ns(2.4))
    dsp.place(y, v, 0.05)
    y = dsp.highpass(y, 200, 2)
    y = dsp.far(y, rng, rt60=1.8, wet=0.4, dry=0.85, lp=7000, predelay=0.02)
    return _fin(y, 150)


def child_mitsuketa(rng, i=1):
    """「みーつけた」 close and delighted - then a giggle."""
    v = _say("mi - tsu ke ta", rng, CHILD, [7, 5, 5.5, (7, 3)], [0.26, 0.09, 0.12, 0.3], vibrato=10.0, wander=0.1,
             f0=360.0, breath=0.5)
    g = V.laugh(rng, n_syl=4, f0=420, decline=0.03, gap=(0.04, 0.06), syl=(0.06, 0.08), vowel="i", table=V.VF,
                scale=1.24, breath=1.6, tilt=-10)
    y = np.zeros(len(v) + len(g) + ns(0.3))
    dsp.place(y, v, 0.03)
    dsp.place(y, g * np.std(v) / (np.std(g) + 1e-9) * 0.6, 0.03 + len(v) / SR - 0.05)
    y = dsp.close_mic(dsp.highpass(y, 150, 2), 3.0, 3.0, 2.0)
    return _fin(_rev(y, rng, 0.2, 0.06), 150)


def child_run(rng, i=1):
    """Small feet in little rubber shoes running over the vinyl."""
    dur = {1: 1.8, 2: 1.4, 3: 2.2}[i]
    n = ns(dur + 0.4)
    y = np.zeros(n)
    vinyl = [(620, 0.025, 1.0), (1180, 0.02, 0.6), (1900, 0.015, 0.35)]
    t = 0.02
    k = 0
    tt = np.arange(n) / SR
    loud = {1: [(0, 1.0), (dur, 0.25)], 2: [(0, 0.3), (dur * 0.5, 1.0), (dur, 0.3)], 3: [(0, 0.6), (dur, 0.6)]}[i]
    while t < dur:
        g = float(np.interp(t, [p[0] for p in loud], [p[1] for p in loud]))
        dsp.place(y, _hit(rng, vinyl, vel=g * rng.uniform(0.7, 1.0), size=0.75, hard=0.5, dur=0.08, noise=0.3), t)
        dsp.place(y, _thud(rng, 150, 110, 0.012, 0.06, noise=0.3) * g * 0.35, t)
        if i == 3 and k % 6 == 5:  # a skip
            t += 0.08
        t += rng.uniform(0.15, 0.19) if i != 3 else rng.uniform(0.17, 0.24)
        k += 1
    return _fin(_rev(y, rng, 0.15, 0.06), 80)


NURSERY = [  # an original warabe-uta-like tune (minor pentatonic around D5): (beat, note, beats)
    (0, "D5", 1), (1, "D5", 1), (2, "C5", 1), (3, "A4", 1), (4, "C5", 1), (5, "D5", 1), (6, "C5", 2),
    (8, "A4", 1), (9, "C5", 1), (10, "D5", 1), (11, "C5", 1), (12, "A4", 1), (13, "G4", 1), (14, "A4", 2),
]


def child_hum(rng, i=1):
    beat = 0.42
    notes = [(0.2 + b * beat, d * beat * 0.9, J.hz(nm), 0.8) for b, nm, d in NURSERY]
    y = V.hum(rng, notes, table=V.VF, scale=1.24, vowel="u", breath=0.5, vibrato=14, vib_rate=5.8, scoop=60,
              off_key=lambda k: -15.0 + 18.0 * np.sin(1.7 * k))
    return _fin(_rev(dsp.highpass(y, 150, 2), rng, 0.3, 0.12), 150)


def child_whisper(rng, i=1):
    """「こっちだよ」 whispered by a child, at your ear."""
    w = V.whisper_line("ko q chi da yo", rng, [0.11, 0.14, 0.1, 0.3], table=V.VF, scale=1.22, close=1.0, bw=1.2,
                       pauses=None, voicing=0.12, f0=330)
    y = np.zeros(len(w) + ns(0.7))
    b = V.breath(rng, "in", 0.2, table=V.VF, scale=1.22)
    dsp.place(y, b * np.std(w) / (np.std(b) + 1e-9) * 0.3, 0.0)
    dsp.place(y, w, 0.18)
    y = _ear(y, rng, 0.8)
    return dsp.trim(y, -60)


# ==========================================================================
# 鏡の女 the woman in the mirror
# ==========================================================================
def _behind_glass(y, rng):
    """Heard through a mirror: the glass passes the mids, its own modes ring faintly, the highs are lost."""
    g = dsp.bandpass(y, 250, 2600, 2)
    ring = dsp.resbank(y, [(1450, 25, 1.0), (2380, 30, 0.6), (3900, 35, 0.3)])
    return g + ring / (np.std(ring) + 1e-9) * np.std(g) * 0.12


def mirror_sob(rng, i=1):
    """A woman crying behind glass: catches of breath, voiced sobs, a long shaking exhale."""
    y = np.zeros(ns(4.6 if i == 1 else 5.4))
    t = 0.05
    plan = [("hic", 3), ("sob", 0.9), ("in", 0.5), ("sob", 1.2), ("out", 1.3)] if i == 1 else \
        [("in", 0.7), ("sob", 1.4), ("hic", 2), ("sob", 0.8), ("hic", 3), ("out", 1.5)]
    for kind, d in plan:
        if kind == "hic":  # spasmodic inhalations
            for k in range(int(d)):
                b = V.breath(rng, "in", 0.12, table=V.VF, scale=1.03, voiced=0.5, f0=330, vowel_in="i", hiss=0.2)
                dsp.place(y, b, t)
                t += rng.uniform(0.16, 0.22)
            t += 0.1
        elif kind == "sob":
            n = ns(d)
            ff = 260 * dsp.cents(dsp.env_pts(n, [(0, 150), (0.1, 0), (d, -300)]) + 40 * dsp.smooth(n, rng, 6.0, periodic=False))
            amp = dsp.env_pts(n, [(0, 0), (0.08, 1.0), (d * 0.5, 0.8), (d, 0)])
            amp *= 0.6 + 0.4 * (0.5 + 0.5 * np.sin(TAU * rng.uniform(5, 7) * np.arange(n) / SR)) ** 2  # shaking
            v = V.voice(ff, amp, [(0, V.fs("a", V.VF, 1.03)), (d, V.fs("u", V.VF, 1.03))], rng, tilt=-9, fmax=6000,
                        jitter=0.03, shimmer=0.15, breath=0.9, subharm=0.25)
            dsp.place(y, v * 0.8, t)
            t += d + rng.uniform(0.05, 0.15)
        else:
            b = V.breath(rng, kind, d, table=V.VF, scale=1.03, tremor=0.6, trem_rate=7.5, voiced=0.3 if kind == "out" else 0.0, f0=250)
            dsp.place(y, b, t)
            t += d + 0.1
    y = _behind_glass(y, rng)
    tap = np.zeros(len(y))  # the tap she left running, very faint, also behind the glass
    w = ins.water_texture(len(y), rng, density=200, r_range=(0.8, 3.0), noise=0.4, splash=0.2)
    y = y + dsp.bandpass(w, 300, 2000, 2) * np.std(y) * 0.15
    y = dsp.reverb(y, dsp.make_ir(1.6, rng, predelay=0.02, hf_ratio=0.4), wet=0.45, dry=0.85)  # her side is tiled too
    return _fin(y, 150, -60)


def mirror_whisper(rng, i=1):
    """「……うしろ」 - and it is at your ear, not in the mirror."""
    w = V.whisper_line("u shi ro", rng, [0.18, 0.14, 0.36], table=V.VF, scale=1.03, close=1.0, bw=1.2, voicing=0.08, f0=215)
    y = np.zeros(len(w) + ns(1.0))
    b = V.breath(rng, "in", 0.35, table=V.VF, scale=1.03, wet=0.4)
    dsp.place(y, b * np.std(w) / (np.std(b) + 1e-9) * 0.35, 0.0)
    dsp.place(y, w, 0.4)
    ex = V.breath(rng, "out", 0.6, table=V.VF, scale=1.03)
    dsp.place(y, ex * np.std(w) / (np.std(ex) + 1e-9) * 0.3, 0.4 + len(w) / SR - 0.05)
    y = _ear(y, rng, 1.0)
    return dsp.trim(y, -60)


MIRROR = [(1450, 0.18, 1.0), (2380, 0.14, 0.7), (3900, 0.1, 0.45), (5800, 0.06, 0.25), (420, 0.06, 0.5)]


def mirror_knock(rng, i=1):
    """Knuckles on the glass from the other side."""
    pat = [0.0, 0.32, 0.64] if i == 1 else [0.0, 0.5, 0.62]
    y = np.zeros(ns(1.8))
    for k, t0 in enumerate(pat):
        dsp.place(y, _hit(rng, MIRROR, vel=1.0 if k == 0 else 0.85, size=1.0, hard=0.65, dur=0.4, noise=0.35), 0.02 + t0)
        dsp.place(y, _thud(rng, 180, 110, 0.02, 0.15, noise=0.6, lp=1400) * 0.6, 0.02 + t0)
    return _fin(_rev(y, rng, 0.25, 0.1), 80)


def mirror_squeak(rng, i=1):
    """Wet palms dragged down the glass: skin sticking and slipping, a squeal."""
    d = 1.3 if i == 1 else 1.0
    m = ns(d)
    y = np.zeros(ns(d + 0.6))
    for h, (t0, off) in enumerate(((0.0, 1.0), (0.12, 0.88))):  # two hands
        rate = dsp.env_pts(m, [(0, 420 * off), (d * 0.3, 760 * off), (d * 0.7, 610 * off), (d, 380 * off)])
        rate = rate * (1 + 0.25 * dsp.smooth(m, rng, 6.0, periodic=False) + 0.08 * dsp.smooth(m, rng, 30.0, periodic=False))
        grip = np.zeros(m)  # skin grabs and lets go: squeaks in bursts, silent slides between them
        tt = rng.uniform(0.0, 0.05)
        while tt < d:
            ln = rng.uniform(0.05, 0.22)
            a, b = ns(tt), min(m, ns(tt + ln))
            grip[a:b] = rng.uniform(0.5, 1.0)
            tt += ln + rng.uniform(0.02, 0.12)
        grip = dsp.lowpass0(grip, 60.0)
        env = dsp.env_pts(m, [(0, 0), (0.05, 1.0), (d * 0.8, 0.8), (d, 0)]) * grip
        fp = ins.friction_pulses(d, rng, rate, env, jitter=0.18)
        body = dsp.resbank(fp, [(f, 18, a) for f, _, a in MIRROR])
        sq = sum(a * dsp.nb_noise(rate * k, 35 * k, rng) for k, a in ((1, 1.0), (2, 0.45), (3, 0.2)))
        sq *= env
        slide = dsp.bandpass(rng.standard_normal(m), 1500, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.05, 1), (d, 0)]) * (1.1 - grip)
        dsp.place(y, _nrm(body) * 0.4 + sq * 0.04 + slide * 0.015, t0)
    return _fin(_rev(y, rng, 0.25, 0.1), 150)


def mirror_scream(rng, i=1):
    """Her scream - head back, mouth too wide - and the mirror cracking across."""
    dur = 2.2
    s, _ = V.scream(dur, rng, [(0, 700), (0.06, 1150), (0.5, 1300), (1.2, 1250), (dur, 820)],
                    [(0, 0), (0.02, 1.0), (1.5, 0.9), (dur, 0)], vowel_pts=[(0, "a"), (0.9, "a"), (dur, "e")],
                    chaos_pts=[(0, 3.92), (0.12, 3.92), (0.16, 3.3), (0.6, 3.3), (0.65, 3.9), (dur, 3.94)],
                    jumps=[(0.62, 4, None), (1.4, -5, None)],
                    biphon=dict(ratio=[(0, 1.27), (dur, 1.21)], amp=[(0, 0), (0.7, 0), (0.78, 0.6), (1.2, 0.6), (1.3, 0), (dur, 0)]),
                    breath=0.5, rasp=1.0, rough=(0.75, 64, 0.55), scale=1.06, oq=0.38, closure=0.6)
    y = np.zeros(ns(dur + 1.6))
    dsp.place(y, _nrm(_behind_glass(s, rng)) * 0.5 + _nrm(s) * 0.5, 0.02)
    for t0 in (0.5, 0.95, 1.3):  # the glass cracking, crack after crack
        m = ns(0.05)
        c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.002 * SR))
        c = dsp.highpass(c, 1500, 2) + dsp.resbank(c, [(f, 15, a) for f, _, a in MIRROR]) * 0.6
        dsp.place(y, c * 1.3, t0)
        run = ins.crackle(0.35, rng, 1200, env=dsp.env_pts(ns(0.35), [(0, 1), (0.35, 0)]), hp=2500, decay=0.0002)
        dsp.place(y, _nrm(run) * 0.3, t0 + 0.01)
    y = 0.6 * dsp.sat(_nrm(y) * 2.0, 1.0) + 0.4 * _nrm(y)
    return _fin(_rev(y, rng, 0.6, 0.15), 100)


# ==========================================================================
# 店長 the manager
# ==========================================================================
def manager_step(rng, i=1):
    """Hard leather soles, heel then sole, measured."""
    y = np.zeros(ns(0.5))
    heel = [(1350, 0.03, 1.0), (2600, 0.02, 0.7), (4100, 0.015, 0.4), (700, 0.04, 0.4)]
    dsp.place(y, _hit(rng, heel, vel=1.0, size=1.0, hard=0.95, dur=0.15, noise=0.4), 0.008)
    dsp.place(y, _thud(rng, 120, 85, 0.02, 0.15, noise=0.4) * 0.4, 0.008)
    m = ns(0.05)
    slap = dsp.bandpass(rng.standard_normal(m), 600, 5000, 2) * dsp.env_ad(m, 0.001, 0.01)
    dsp.place(y, slap * 0.35, 0.12)  # the sole comes down flat
    if i == 2:
        cr = ins.creak(0.12, rng, [(0, 60), (0.12, 30)], [(0, 0), (0.02, 0.5), (0.12, 0)], modes=[(780, 10, 1.0), (1500, 12, 0.5)])
        dsp.place(y, cr * 0.08, 0.14)  # leather creaks
    return _fin(_rev(y, rng, 0.12, 0.05), 60)


def manager_keys(rng, i=1):
    """A heavy ring of keys swinging at his belt, in time with his steps."""
    y = np.zeros(ns(2.2))
    for step in range(3):
        t0 = 0.05 + step * 0.62
        t = t0
        for k in range(rng.integers(5, 9)):
            dsp.place(y, _hit(rng, ins.KEYS, vel=rng.uniform(0.2, 0.8) * (1 - k / 10), size=rng.uniform(0.8, 1.3),
                              hard=1.0, dur=0.35, noise=0.3), t)
            t += rng.exponential(0.025) + 0.006
    return _fin(_rev(y, rng, 0.2, 0.06), 400)


def manager_welcome(rng, i=1):
    """「いらっしゃいませ」 in a tired monotone, the last syllable sinking into fry."""
    v = _say("i ra q sha i ma se", rng, MANAGER, [0, 1.2, (1.2, 1.0), 0.8, 0.5, (0, -2)],
             [0.08, 0.1, 0.12, 0.09, 0.1, 0.32], vibrato=0.0, wander=0.1, tilt=-11.0)
    n = len(v)
    fry = V.creak(ns(0.35), np.linspace(40, 22, ns(0.35)), np.linspace(1, 0, ns(0.35)), rng, vowel="e", table=V.VM, scale=0.97)
    y = np.zeros(n + ns(0.6))
    dsp.place(y, v, 0.02)
    dsp.place(y, fry * np.std(v) * 0.8, 0.02 + n / SR - 0.3)
    return _fin(_rev(y, rng, 0.25, 0.08), 70)


def manager_closed(rng, i=1):
    """「本日の営業は、終了いたしました」 - bowing at the entrance, a little too slow."""
    from sfx_rest import _closed_line
    v, _ = _closed_line(rng, f0=100.0, tired=True)
    v = dsp.varispeed(v, 0.94)
    y = np.zeros(len(v) + ns(0.8))
    dsp.place(y, v, 0.02)
    return _fin(_rev(y, rng, 0.3, 0.1), 70)


def manager_customer(rng, i=1):
    """「お客様……」 - 1 polite and cold, 2 strained, nearly whispered, with fry."""
    if i == 1:
        v = _say("o kya ku sa ma", rng, MANAGER, [0, 3, 3, 2.5, (2, -1)], [0.09, 0.11, 0.08, 0.1, 0.4], wander=0.1)
    else:
        v = V.whisper_line("o kya ku sa ma -", rng, [0.11, 0.13, 0.09, 0.12, 0.5], table=V.VM, scale=0.97, voicing=0.5,
                           f0=95, close=0.5, bw=1.2)
        fry = V.creak(ns(0.5), np.linspace(35, 20, ns(0.5)), np.linspace(0.8, 0, ns(0.5)), rng, vowel="a", table=V.VM, scale=0.97)
        v = dsp.mix(v, np.concatenate([np.zeros(max(0, len(v) - ns(0.45))), fry * np.std(v) * 0.6]))
    y = np.zeros(len(v) + ns(0.6))
    dsp.place(y, v, 0.02)
    return _fin(_rev(y, rng, 0.25, 0.08), 70)


def manager_sigh(rng, i=1):
    """A long, exhausted sigh with a groan inside it."""
    b = V.breath(rng, "in", 0.7, table=V.VM, scale=0.97)
    ex = V.breath(rng, "out", 2.1, table=V.VM, scale=0.97, voiced=0.35, f0=92, vowel_out="a", rasp=0.3)
    y = np.zeros(len(b) + len(ex) + ns(0.5))
    dsp.place(y, b * 0.7, 0.02)
    dsp.place(y, ex, 0.02 + len(b) / SR + 0.1)
    return _fin(_rev(y, rng, 0.25, 0.08), 70, -60)


def manager_typing(rng, i=1):
    """6 s of typing on the office PC's keyboard in the dark (seamless loop)."""
    L = 6.0
    n = ns(L)
    y = np.zeros(n)
    KEY = [(1900, 0.02, 1.0), (3300, 0.015, 0.6), (5200, 0.01, 0.3), (850, 0.03, 0.5)]
    t = 0.0
    while t < L - 0.05:
        burst = rng.integers(3, 9)
        for k in range(burst):
            v = rng.uniform(0.5, 1.0)
            dn = _hit(rng, KEY, vel=v, size=rng.uniform(0.9, 1.15), hard=0.95, dur=0.06, noise=0.5)
            up = _hit(rng, KEY, vel=v * 0.35, size=rng.uniform(0.9, 1.15), hard=0.9, dur=0.04, noise=0.5)
            dsp.place(y, dn, t, circular=True)
            dsp.place(y, up, t + rng.uniform(0.06, 0.09), circular=True)
            t += rng.uniform(0.08, 0.17)
        if rng.random() < 0.4:  # the space bar
            dsp.place(y, _hit(rng, [(420, 0.04, 1.0), (1100, 0.03, 0.6), (2300, 0.02, 0.3)], vel=0.9, hard=0.8, dur=0.1), t, circular=True)
            t += 0.15
        t += rng.uniform(0.25, 0.9)  # a pause: reading the screen
    y = dsp.circ_reverb(y, dsp.make_ir(0.25, rng, predelay=0.003, hf_ratio=0.6), wet=0.08, dry=1.0)
    return dsp.dc_block(y, circular=True)


def manager_chair(rng, i=1):
    """The office chair: the spring under the seat creaks, the gas lift sighs, a caster rolls."""
    y = np.zeros(ns(1.6))
    cr = ins.creak(0.6, rng, [(0, 25), (0.3, 60), (0.6, 20)], [(0, 0), (0.05, 0.9), (0.6, 0)],
                   modes=[(320, 12, 1.0), (640, 14, 0.7), (1100, 16, 0.4), (1800, 18, 0.25)], tonal=0.4)
    dsp.place(y, cr * 0.6, 0.02)
    m = ns(0.4)
    gas = dsp.bandpass(rng.standard_normal(m), 2000, 7000, 2) * dsp.env_pts(m, [(0, 0), (0.05, 1), (0.4, 0)])
    dsp.place(y, gas * 0.06, 0.3)
    m = ns(0.5)
    roll = dsp.lowpass(rng.standard_normal(m), 900, 2) * dsp.env_pts(m, [(0, 0), (0.1, 1), (0.5, 0)]) * (1 + 0.5 * np.sin(TAU * 11 * np.arange(m) / SR))
    dsp.place(y, roll * 0.15, 0.7)
    return _fin(_rev(y, rng, 0.2, 0.06), 60)


# ==========================================================================
# 霊媒師 the medium
# ==========================================================================
MURMURS = [  # (script, pitch per syllable, durations): wordless murmurs of an old woman
    ("N -", [(2, 0)], [0.32]),
    ("fu mu", [(1, 3), (2, -1)], [0.1, 0.3]),
    ("e e", [(3, 4), (2, -1)], [0.12, 0.3]),
    ("ha a", [(2, 1), (0, -3)], [0.16, 0.42]),
    ("N N", [(0, 3), (3, -1)], [0.18, 0.26]),
    ("ho o", [(4, 5), (3, 0)], [0.12, 0.38]),
]


def medium_voice(rng, i=1):
    script, pitch, durs = MURMURS[i - 1]
    v = _say(script, rng, MEDIUM, pitch, durs, vibrato=28.0, vib_rate=5.4, wander=0.3)
    y = np.zeros(len(v) + ns(0.4))
    dsp.place(y, v, 0.02)
    y = dsp.close_mic(y, 2.0, 1.0, 1.0)
    return _fin(_rev(y, rng, 0.2, 0.06), 90)


def medium_bell(rng, i=1):
    """The rin: a small singing bowl struck once, shimmering for a long time."""
    y = np.zeros(ns(7.0))
    r = ins.bell_partials(1180.0, 6.8, rng, [1.0, 2.71, 5.15, 8.3, 11.9], [1.0, 0.5, 0.25, 0.1, 0.04], [5.5, 3.0, 1.4, 0.7, 0.35],
                          beat=(0.6, 2.2), attack=0.0008)
    dsp.place(y, r, 0.01)
    dsp.place(y, _hit(rng, [(2400, 0.02, 1.0), (4800, 0.01, 0.5)], vel=0.2, hard=0.8, dur=0.05), 0.01)
    return dsp.fade(dsp.highpass(y, 300, 2), 0.0, 0.5)


def medium_chant(rng, i=1):
    """8 s of her chant (seamless loop): a low monotone recitation, the beads rubbing between her palms."""
    L = 8.0
    n = ns(L)
    script = "o N a ri ta ma e , ha ra e ta ma e , ki yo me ta ma e , o N"
    nsy = len([t for t in V.parse(script) if t[0] not in (",", ".", "q", "-", "N", "|")])
    pitch = [0.0 if k % 5 else 1.0 for k in range(nsy)]
    durs = [0.16 + (0.1 if k % 7 == 6 else 0.0) for k in range(nsy)]
    kw = dict(table=V.VF, scale=0.97, f0=170.0, breath=0.5, jitter=0.02, shimmer=0.1, tilt=-10.0, vibrato=20.0,
              vib_rate=5.0, wander=0.2, slur=0.25,
              pauses={k: 0.3 for k, tok in enumerate(V.parse(script)) if tok[0] == ","})
    v0, _ = V.speak_line(script, rng, pitch, durs, **kw)
    tempo = (L + 0.5) / (len(v0) / SR)  # fit one recitation to the loop
    v, _ = V.speak_line(script, dsp.rng_for("medium/chant", 3), pitch, durs, tempo=tempo, **kw)
    v = dsp.pad_to(v, ns(L + 0.5))
    loop = dsp.loop_xfade(v, n, ns(0.5))
    beads = np.zeros(n)
    for k in range(10):
        t0 = k * L / 10 + rng.uniform(0, 0.3)
        for j in range(rng.integers(4, 8)):
            dsp.place(beads, _hit(rng, [(2900, 0.03, 1.0), (4600, 0.02, 0.6)], vel=rng.uniform(0.1, 0.3), hard=0.9, dur=0.05),
                      t0 + j * 0.02, circular=True)
    y = _nrm(loop) + _nrm(beads) * 0.08
    y = dsp.circ_reverb(y, dsp.make_ir(0.6, rng, predelay=0.005, hf_ratio=0.5), wet=0.15, dry=1.0)
    return dsp.dc_block(y, circular=True)


def medium_clap(rng, i=1):
    """Two ritual claps (柏手), hands cupped, deliberate."""
    y = np.zeros(ns(1.6))
    for t0 in (0.02, 0.62):
        m = ns(0.08)
        c = rng.standard_normal(m) * dsp.env_ad(m, 0.0006, 0.009)
        c = dsp.reson(c, rng.uniform(900, 1100), 2.5, 2.5) + dsp.bandpass(c, 1500, 8000, 2) * 0.8
        c += dsp.lowpass(rng.standard_normal(m), 300, 2) * dsp.env_ad(m, 0.001, 0.012) * 0.5
        dsp.place(y, c, t0)
    return _fin(_rev(y, rng, 0.25, 0.06), 100)


# ==========================================================================
# the spirits in general
# ==========================================================================
WHISPERS = {
    1: [("ko ko ni i ru", 1.02, 0.85, 0.2, 1.0)],
    2: [("mi te ru , mi te ru", 1.04, 0.95, 0.15, 1.0, "rev")],
    3: [("sa mu i", 1.0, 0.7, 0.2, 1.0), ("ka e ri ta i", 0.95, 0.8, 0.95, 0.8)],
    4: [("su ku ra shi ma ta ne ko so ri tsu , mo ga na i", 1.0, 1.35, 0.1, 1.0)],
    5: [("hi to ri , hi to ri", 1.06, 0.75, 0.2, 1.0)],
    6: [("i ra q sha i ma se", 1.05, 0.9, 0.15, 0.9, "rev"), ("ma da", 1.0, 0.7, 1.4, 1.0)],
}


def spirit_whisper(rng, i=1):
    parts = []
    t_end = 0.0
    for item in WHISPERS[i]:
        script, scale, rate, t0, g = item[:5]
        w = V.whisper(script, rng, scale=scale, rate=rate, intensity=g, close=1.0, bw=1.3)
        if len(item) > 5:
            w = dsp.reverse(w)
        parts.append(dsp.place(np.zeros(ns(t0) + len(w)), w, t0))
        t_end = max(t_end, t0 + len(w) / SR)
    y = dsp.mix(*parts)
    ref = np.std(y[np.abs(y) > 1e-4]) if np.any(np.abs(y) > 1e-4) else 1.0
    out = np.zeros(len(y) + ns(0.9))
    dsp.place(out, y, 0.0)
    m = ns(rng.uniform(0.5, 0.8))
    keys = [(0, V.fs("x", V.VF, 1.06)), (m / SR, V.fs("u", V.VF, 1.06))]
    ex = V.vt_noise(m, keys, dsp.env_pts(m, [(0, 0), (0.06, 1.0), (m / SR, 0)]) ** 1.3, rng, tilt_db=2.0)
    dsp.place(out, ex / (np.std(ex) + 1e-9) * ref * 0.3, t_end - 0.05)
    out = _ear(out, rng)
    return dsp.trim(out, -60, min_len=1.2)


def _shriek_core(rng, dur, base, chaos, jumps, biphon_at, vowels, rough=(0.75, 72, 0.5), scale=1.12):
    f0p = [(0, base * 0.45), (0.05, base), (dur * 0.35, base * 1.12), (dur * 0.7, base * 0.95), (dur, base * 0.55)]
    amp = [(0, 0), (0.025, 1.0), (dur * 0.75, 0.9), (dur * 0.92, 0.5), (dur, 0)]
    a, f0 = V.scream(dur, rng, f0p, amp, vowel_pts=vowels, chaos_pts=chaos, jumps=jumps,
                     biphon=dict(ratio=[(0, 1.33), (dur, 1.26)],
                                 amp=[(0, 0), (biphon_at, 0), (biphon_at + 0.08, 0.7), (biphon_at + 0.5, 0.7), (biphon_at + 0.6, 0), (dur, 0)]),
                     breath=0.45, rasp=0.8, rough=rough, scale=scale, oq=0.4, closure=0.7)
    b, _ = V.scream(dur, rng, [(t + 0.02, f / 1.41) for t, f in f0p], amp, vowel_pts=vowels, chaos_pts=chaos,
                    jumps=[(t + 0.02, s, w) for t, s, w in jumps], breath=0.5, rasp=1.0, rough=(0.65, 58, 0.6),
                    scale=scale * 0.82, oq=0.42, closure=0.8)
    c, _ = V.scream(dur, rng, [(0, 150), (dur, 120)], amp, vowel="o", chaos_pts=[(0, 3.9), (dur, 3.9)], ct=0.4,
                    ca=0.6, breath=0.6, rasp=1.2, rough=(0.7, 45, 0.7), scale=0.85, oq=0.5)
    return _nrm(a) + 0.55 * _nrm(b) + 0.35 * _nrm(c)


def spirit_notice(rng, i=1):
    """The universal 'it noticed you': a sharp, rough inhaled breath-scream, cut short."""
    d = {1: 0.75, 2: 0.6, 3: 0.9}[i]
    base = {1: 900, 2: 1250, 3: 620}[i]
    gasp = V.breath(rng, "in", 0.14, table=V.VF, scale=1.08, vowel_in="a", hiss=0.6, voiced=0.3, f0=base * 0.6)
    s, _ = V.scream(d, rng, [(0, base * 0.6), (0.03, base * 1.1), (d * 0.5, base), (d, base * 0.7)],
                    [(0, 0), (0.012, 1.0), (d * 0.7, 0.85), (d - 0.03, 0.6), (d, 0)],
                    vowel_pts=[(0, "a"), (d, "i" if i == 2 else "a")], chaos_pts=[(0, 3.93), (d, 3.95)], ct=0.45, ca=0.6,
                    breath=0.8, rasp=1.4, rough=(0.8, 66 + 8 * i, 0.6), scale=1.05 if i != 3 else 0.9, oq=0.38, closure=0.6)
    y = np.zeros(ns(d + 0.5))
    dsp.place(y, _nrm(gasp) * 0.5, 0.0)
    dsp.place(y, _nrm(s), 0.1)
    y = 0.6 * dsp.sat(_nrm(y) * 2.6, 1.0) + 0.4 * _nrm(y)
    return _fin(_rev(y, rng, 0.3, 0.1), 120)


def spirit_bound(rng, i=1):
    """Pulled into the photograph: a scream sucked backwards into the lens, a rising shimmer, a snap."""
    d = 1.8
    s = _shriek_core(rng, d, 1100, chaos=[(0, 3.9), (d, 3.94)], jumps=[(0.7, 4, None)], biphon_at=0.5,
                     vowels=[(0, "a"), (d, "e")])
    s = dsp.reverb(_nrm(s), dsp.make_ir(2.2, rng, predelay=0.02), wet=0.9, dry=0.6)
    rv = dsp.reverse(_nrm(s))
    n = len(rv)
    rv = rv * dsp.env_pts(n, [(0, 0.0), (n / SR * 0.7, 0.6), (n / SR, 1.0)]) ** 1.5
    y = np.zeros(n + ns(1.4))
    dsp.place(y, rv * 1.0, 0.0)
    m = n
    tt = np.arange(m) / SR
    sh = np.zeros(m)
    for k in range(6):
        f = 1200 * 2 ** (k / 3.0) * 2 ** (tt / (m / SR) * 1.2)
        sh += np.sin(TAU * np.cumsum(np.minimum(f, 16000)) / SR + rng.random() * 6) * (1 - np.clip(f / 16000, 0, 1))
    dsp.place(y, sh / (np.max(np.abs(sh)) + 1e-9) * dsp.env_pts(m, [(0, 0), (m / SR, 1.0)]) ** 2 * 0.25, 0.0)
    t1 = n / SR
    m = ns(0.08)
    snap = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.004 * SR))
    dsp.place(y, dsp.highpass(snap, 800, 2) * 0.55, t1)
    dsp.place(y, _thud(rng, 90, 45, 0.08, 0.5, noise=0.5) * 0.5, t1)
    return _fin(_rev(y, rng, 0.4, 0.12), 60)


def spirit_vanish(rng, i=1):
    """Fading out: a soft reversed swell of breath and air, then nothing."""
    m = ns(1.2)
    b = V.breath(rng, "out", 1.0, table=V.VF, scale=1.05, voiced=0.25, f0=220)
    src = np.zeros(m)
    dsp.place(src, b, 0.0)
    w = dsp.reverb(src, dsp.make_ir(2.6, rng, predelay=0.03, hf_ratio=0.4), wet=1.0, dry=0.2)
    rv = dsp.reverse(_nrm(w))
    n = len(rv)
    air = dsp.bandpass(rng.standard_normal(n), 2000, 9000, 2) * dsp.env_pts(n, [(0, 0), (n / SR, 1.0)]) ** 2
    y = rv + air * 0.05
    y = dsp.fade(y, 0.2, 0.08)
    return dsp.trim(dsp.highpass(y, 100, 2), -60)
