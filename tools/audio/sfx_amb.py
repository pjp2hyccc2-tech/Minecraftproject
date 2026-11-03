"""amb.* beds, the CRT static and the apartment's positional loops (fridge, clock, taps, washing machine, rain on the
glass), and the camera microphone's hiss.

Everything is built circularly (periodic FFT noise, loop-quantised oscillators and LFOs, events placed modulo the
loop length, circular filters and reverb), so every loop seam is sample-continuous.

The apartment at 3 a.m. is *quiet*.  The beds sit far below the one-shots and carry only what is really there -
rain on the window, the gutter, the fridge cycling, a freight train far away, the house settling - with the
SOUNAN / REISATSU palette kept to faint undercurrents (a binaural theta pair under the closet's silence, a sub
swell).  The one thing that is never quiet is the TV."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import tune as T
import voice as V
from dsp import SR, TAU, ns


# ==========================================================================
# amb.tv_static - the CRT on a dead channel (mono, 40 s, the most important sound in the game)
# ==========================================================================
def _fragment_voice(rng, kind):
    """A broadcast drifting in under the snow: the voice of a far station, band-limited and fading."""
    if kind == "man":
        y = V.speak("ko n ba N wa , ki n ki ni yu - su o o tsu ta e shi ma su", rng, table=V.VM, scale=0.98, f0=112,
                    rate=1.05, accent=2.0, declination=2.0, breath=0.2, tilt=-9.0)
    elif kind == "woman":
        y, _ = V.speak_line("so ko ni , i ru no", rng, [3, 4, 3.5, (2, 1), 0, (1, -2)], [0.16, 0.14, 0.16, 0.12, 0.13, 0.42],
                            table=V.VF, scale=1.03, f0=205, breath=0.45, tilt=-10, jitter=0.02)
        y = dsp.varispeed(y, 0.86)  # a little too slow
    else:  # a child, sing-song: 「もういいかい」 on the G-E call
        y, _ = V.speak_line("mo - i i ka i -", rng, [4, 4, 4, 1, 4, (1, 0.5)], [0.2, 0.14, 0.18, 0.2, 0.16, 0.36],
                            table=V.VF, scale=1.22, f0=340, vibrato=10.0, breath=0.4)
    y = dsp.bandpass(y, 280, 3400, 3)
    return K.nrm(y)


def tv_static(rng, i=1):
    L = 40.0
    n = ns(L)
    t = np.arange(n) / SR

    # --- the snow: the FM detector's white noise with a soft de-emphasis, its colour breathing between a darker
    #     and a brighter hiss (the AGC hunting on nothing)
    w = dsp.colored(n, rng, 0.0, lo=60, hi=16000)
    dark = K.cr(w, lambda v: dsp.lowpass(dsp.lowpass(v, 2800, 1), 6000, 2))
    bright = K.cr(w, lambda v: dsp.lowpass(v, 7000, 1))
    # it breathes: a slow, slightly irregular in-out (about every 5.7 s), brighter on the in-breath
    br = K.lfo(n, 7, rng, 0.0, 1.0, 1.4) * (0.6 + 0.4 * K.norm01(dsp.smooth(n, rng, 0.08)))
    tilt = np.clip(K.norm01(dsp.smooth(n, rng, 0.12)) * 0.55 + 0.35 * br, 0, 1)
    snow = K.rmsn(dark) * (1 - tilt) + K.rmsn(bright) * tilt
    level = (1.0 + 0.13 * dsp.smooth(n, rng, 0.18) + 0.05 * dsp.smooth(n, rng, 1.3)) * (0.88 + 0.24 * br)
    # swells: the static opens up for a breath or two, brighter, then settles
    for t0, d in ((5.5, 2.6), (17.0, 1.8), (26.5, 3.4), (35.2, 1.5)):
        m = ns(d)
        e = np.sin(np.pi * np.linspace(0, 1, m)) ** 2
        idx = (ns(t0) + np.arange(m)) % n
        level[idx] *= 1.0 + 0.32 * e
        tilt_e = np.zeros(n)
        tilt_e[idx] = e
        snow = snow + K.rmsn(bright) * 0.25 * tilt_e
    # flutter episodes: the snow trembles at 6-11 Hz for a moment (a car's ignition, the sync slipping)
    flut = np.ones(n)
    for t0, d, r in ((9.0, 1.4, 7.3), (21.8, 0.9, 10.6), (31.0, 2.0, 6.1)):
        m = ns(d)
        e = np.sin(np.pi * np.linspace(0, 1, m)) ** 1.5
        idx = (ns(t0) + np.arange(m)) % n
        flut[idx] *= 1.0 - 0.28 * e * (0.5 + 0.5 * np.sin(TAU * r * np.arange(m) / SR))
    snow = snow * level * flut

    # --- crackle: sparse ticks and pops (impulse noise), a few torn bursts with the field-rate buzz in them
    pops = K.stream(L, rng, 9.0, pareto=1.8)
    pops = K.grain_bank(pops, [(1200, 0.8, 1.0), (2600, 1.0, 0.8), (4800, 1.2, 0.4)], decay=0.0009, L=L)
    tears = np.zeros(n)
    fbz = dsp.loop_freq(59.94, L)
    for t0 in (3.1, 12.4, 13.0, 24.2, 33.7):
        d = rng.uniform(0.04, 0.13)
        m = ns(d)
        tt = np.arange(m) / SR
        b = rng.standard_normal(m) * (0.55 + 0.45 * np.sign(np.sin(TAU * fbz * tt + rng.random() * 6)))
        b *= K.env_shape(m, [(0, 0), (0.008, 1.0), (d * 0.7, 0.8), (d, 0)])
        K.place(tears, b * rng.uniform(0.6, 1.0), t0, L=L)

    # --- the vertical sync buzz leaking into the audio (59.94 Hz, rich harmonics), the chassis transformer (50 Hz)
    buzz_wave = dsp.pulse(np.full(n, fbz), 0.08, n, rng.random(), loop=True)
    buzz = K.cr(buzz_wave, lambda v: dsp.bandpass(v, 110, 2400, 2))
    buzz *= 0.75 + 0.25 * K.lfo(n, 3, rng)

    # --- what drifts in: a newsreader, a woman, a child's call, a heterodyne whistle, four notes of the tune
    frag = np.zeros(n)
    for t0, kind, g in ((7.8, "man", 1.0), (19.6, "woman", 0.8), (29.9, "child", 0.65)):
        v = _fragment_voice(rng, kind)
        m = len(v)
        x = np.linspace(0, 1, m)
        fade = np.clip(np.sin(np.pi * x) * 1.6, 0, 1) ** 1.3       # the station fades in and out
        fade *= 0.55 + 0.45 * K.norm01(dsp.smooth(m, rng, 4.0, periodic=False))  # selective fading
        fade *= 1.0 - 0.35 * (0.5 + 0.5 * np.sin(TAU * rng.uniform(9, 14) * np.arange(m) / SR))  # picket fence
        K.place(frag, v * fade * g, t0, L=L)
    m = ns(2.8)  # heterodyne whistle gliding down
    tt = np.arange(m) / SR
    fw = 1900.0 * 2 ** (-tt / 2.8 * 1.1) * (1 + 0.004 * np.sin(TAU * 5.0 * tt))
    wh = np.sin(TAU * np.cumsum(fw) / SR) * np.sin(np.pi * tt / 2.8) ** 2
    K.place(frag, wh * 0.35, 14.6, L=L)
    box = np.zeros(ns(3.2))  # four notes of the tune on a music box, far inside the noise
    for k, (b, nm, d) in enumerate(T.TUNE[:4]):
        K.place(box, ins.tine(T.box_hz(nm, 1), rng, dur=1.2, vel=0.8), 0.05 + b * 0.62)
    box = dsp.bandpass(box, 350, 3600, 2)
    box *= np.clip(np.sin(np.pi * np.linspace(0, 1, len(box))) * 1.5, 0, 1)
    K.place(frag, K.nrm(box) * 0.7, 37.0, L=L)

    # --- everything above plays through the TV's oval speaker.  The fragments are set against the snow's own
    #     speech band: about 13 dB under it - heard only if you listen, never sure
    snow = K.rmsn(snow)
    band = lambda v: K.cr(v, lambda u: dsp.bandpass(u, 300, 3400, 2))
    act = np.abs(frag) > 1e-4
    fr_rms = np.sqrt(np.mean(band(frag)[act] ** 2)) if act.any() else 1.0
    sn_rms = np.sqrt(np.mean(band(snow) ** 2))
    frag = frag * (sn_rms / (fr_rms + 1e-12)) * dsp.db(-13.0)
    tears = K.rmsn(tears) * 0.32
    spk = snow + K.nrm(pops) * 0.5 + tears + K.rmsn(buzz) * 0.05 + frag
    spk = K.tv_speaker(spk, circular=True)

    # --- and what the set itself radiates: the flyback's 15.734 kHz whine (wavering with the beam current) and the
    #     transformer's hum, plus the faint crackle of static charge on the glass
    fl = 15734.0 + 1.6 * dsp.smooth(n, rng, 0.35)
    whine = np.sin(TAU * dsp.periodic_phase(fl)) * (1.0 + 0.1 * dsp.smooth(n, rng, 40.0))
    chassis = K.hum(L, n, rng, 50.0, ((2, 1.0), (3, 0.35), (4, 0.5), (6, 0.2)))
    glass = K.stream(L, rng, 3.0, pareto=1.6)
    glass = K.grain_bank(glass, [(6500, 2.0, 1.0), (9800, 2.0, 0.6)], decay=0.0004, L=L)
    ref = np.sqrt(np.mean(spk ** 2))
    y = spk + whine * ref * 0.05 + K.nrm(chassis) * ref * 0.05 + K.nrm(glass) * ref * 0.6
    return dsp.dc_block(y, circular=True)


# ==========================================================================
# shared pieces of the beds
# ==========================================================================
def _fridge_cycle(L, n, rng, on_spans, level=1.0):
    """The old fridge in the DK heard through the walls: compressor hum (50 Hz mains: 100 Hz motor with harmonics)
    while it runs, a start surge and clunk, a stop shudder, then the refrigerant ticking and gurgling as it settles."""
    gate = np.zeros(n)
    for a, b in on_spans:
        idx = np.arange(ns(a), ns(b)) % n
        gate[idx] = 1.0
    gate = K.cr(gate, lambda x: dsp.lowpass0(x, 2.5))
    h = K.hum(L, n, rng, 50.0, ((1, 0.25), (2, 1.0), (3, 0.3), (4, 0.5), (6, 0.2), (8, 0.1)))
    rattle = dsp.am(dsp.colored(n, rng, -2.0, lo=150, hi=900), dsp.loop_freq(100.0, L), 0.7, L)
    y = (K.nrm(h) * 0.8 + K.rmsn(rattle) * 0.06) * gate
    for a, b in on_spans:
        m = ns(0.6)
        tt = np.arange(m) / SR
        surge = np.sin(TAU * np.cumsum(55 + 45 * np.exp(-tt / 0.09)) / SR) * dsp.env_ad(m, 0.01, 0.18)
        K.place(y, surge * 0.6, a, L=L)
        K.place(y, ins.modal_hit(rng, [(220, 0.06, 1.0), (520, 0.05, 0.6), (1100, 0.03, 0.3)], hardness=0.4, vel=0.5, dur=0.2), a, L=L)
        sh = dsp.lowpass(rng.standard_normal(m), 260, 2) * dsp.env_ad(m, 0.02, 0.14)
        K.place(y, sh * 0.7, b, L=L)
        K.place(y, ins.modal_hit(rng, [(1700, 0.03, 1.0), (2900, 0.02, 0.5)], hardness=1.0, vel=0.12, dur=0.08), b + 0.35, L=L)
        for k in range(6):  # settling: ticks of the cooling coil, a gurgle of refrigerant
            K.place(y, ins.modal_hit(rng, [(2600, 0.01, 1.0), (4100, 0.008, 0.5)], hardness=1.0, vel=0.05 * rng.uniform(0.4, 1), dur=0.04),
                    b + 1.0 + k * rng.uniform(0.8, 2.4), L=L)
        g = K.bubbles(2.0, rng, 40, (1.5, 5.0))
        K.place(y, K.nrm(g) * 0.06, b + 2.0, L=L)
    return y * level


def _freight(rng, dur=16.0):
    """A freight train far away at night: a low rumble swelling and fading, the faint rhythm of the rail joints."""
    n = ns(dur)
    t = np.arange(n) / SR
    x = np.linspace(-1, 1, n)
    sw = 1.0 / (1.0 + (x * 2.4) ** 2)
    sw = (sw - sw.min()) / (sw.max() - sw.min())
    rum = dsp.colored(n, rng, -5.0, lo=25, hi=400) * sw
    clk = np.zeros(n)
    tt = 0.3
    while tt < dur - 0.3:  # bogies over joints: ta-tan ... ta-tan
        for dt in (0.0, 0.11):
            K.place(clk, ins.modal_hit(rng, [(180, 0.08, 1.0), (330, 0.06, 0.6), (620, 0.04, 0.3)], hardness=0.6,
                                       vel=rng.uniform(0.6, 1.0), dur=0.3, noise=0.3), tt + dt)
        tt += rng.uniform(0.62, 0.72)
    clk = dsp.lowpass(clk, 700, 2) * sw
    y = K.rmsn(rum) * 0.8 + K.rmsn(clk) * 0.35
    return dsp.lowpass(y, 650, 2)


def _car_pass(rng, dur=8.0, far=True):
    """A car passing on the wet street: tyre hiss on water swelling and fading, a low engine."""
    n = ns(dur)
    x = np.linspace(-1, 1, n)
    g = 1.0 / (1.0 + (x * (3.2 if far else 5.0)) ** 2)
    sp = dsp.colored(n, rng, -1.0, lo=200, hi=9000)
    sp = dsp.tv_filter(sp, (1300 if far else 3000) + (2200 if far else 6000) * g, 0.7, "lp", block=256)
    eng = dsp.lowpass(rng.standard_normal(n), 220, 2)
    return (K.rmsn(sp) + K.rmsn(eng) * 0.3) * g


def _house_ticks(L, n, rng, k=3, muffle=2500.0):
    """The wooden building settling: dry ticks (家鳴り), a soft creak."""
    y = np.zeros(n)
    for j in range(k):
        t0 = rng.uniform(0, L)
        if j % 2 == 0:
            c = ins.modal_hit(rng, [(1400, 0.03, 1.0), (2600, 0.02, 0.6), (700, 0.05, 0.5)], hardness=0.9, vel=1.0, dur=0.2, noise=0.4)
        else:
            c = K.creak(rng, rng.uniform(0.3, 0.7), pitch=rng.uniform(0.7, 1.1), rate=(30, 70))
        K.place(y, K.nrm(c) * rng.uniform(0.4, 1.0), t0, L=L)
    return K.cr(y, lambda v: dsp.lowpass(v, muffle, 2))


def _ring(n, L, rng, f=6300.0, beat=5.0):
    """The ringing of silence: two faint tones at the edge of hearing, a few Hz apart between the ears."""
    return dsp.binaural(f, beat, n, L=L, rng=rng)


# ==========================================================================
# amb.apartment - 60 s: 3 a.m., rain on the window and the gutter, the fridge cycling, a freight train far away,
#                 a car on the wet street, the house settling
# ==========================================================================
def apartment(rng, i=1):
    L = 60.0
    n = ns(L)
    out = np.zeros((2, n))
    # rain on the balcony glass (to the front right), the hiss outside it muffled by the glass
    for c, p in ((0, 0.35), (1, 1.0)):
        g = K.rain_glass(n, rng, rate=42.0, L=L, close=0.7)
        g = K.cr(g, lambda v: dsp.lowpass(v, 6500, 2))
        out[c] += g * p * 0.7
    hz = np.vstack([K.rain_hiss(n, rng, L=L, bright=0.5) for _ in range(2)])
    hz = K.cr(hz, lambda v: dsp.lowpass(v, 1100, 2))
    out += hz * np.array([[0.7], [1.0]]) * 0.11
    # the gutter's downpipe outside the kitchen wall (left), eaves dripping onto the balcony rail (right)
    gt = K.gutter(n, rng, L=L, flow=0.7)
    gt = K.cr(gt, lambda v: dsp.lowpass(v, 2000, 2))
    out += dsp.pan(K.rmsn(gt) * 0.05, -0.7)
    dr = K.eaves_drips(n, rng, streams=3, surface="metal", L=L, per=(1.1, 2.6), vel=0.8)
    dr = K.cr(dr, lambda v: dsp.lowpass(v, 3800, 2))
    out += dsp.pan(K.nrm(dr) * 0.09, 0.55)
    # the fridge cycling through the wall (centre left): on for half a minute, settles, silence
    fr = _fridge_cycle(L, n, rng, [(9.0, 41.0)])
    fr = K.cr(fr, lambda v: dsp.lowpass(v, 420, 2))
    out += dsp.pan(K.nrm(fr) * 0.055, -0.3)
    # a freight train far away (left, behind the houses), a car on the wet street (right)
    tr = _freight(rng, 17.0)
    K.place(out, dsp.pan(K.nrm(tr) * 0.17, -0.55), 22.0, L=L)
    car = _car_pass(rng, 8.0, far=True)
    car = dsp.lowpass(car, 2400, 2)
    K.place(out, dsp.pan(K.nrm(car) * 0.09, np.linspace(0.9, 0.2, len(car))), 48.0, L=L)
    # the house: dry ticks, a creak somewhere
    hs = _house_ticks(L, n, rng, k=4, muffle=3500)
    out += dsp.pan(K.nrm(hs) * 0.045, -0.1)
    # the air of the room, a pressure you do not hear
    air = np.vstack([dsp.colored(n, rng, -4.0, lo=40, hi=1500) for _ in range(2)])
    out += air * 0.006
    sub = dsp.sub_rumble(n, 33.0, L=L, rng=rng, am_rate=5.0, am_depth=0.35) * K.lfo(n, 2, rng, 0.0, 1.0, 3.0)
    out += np.vstack([sub, sub]) * 0.012
    ir = K.room(rng, 0.45, stereo=True, predelay=0.006, hf=0.4)
    out = dsp.circ_reverb(out, ir, wet=0.25, dry=0.9)
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# amb.closet - 40 s: inside the 押し入れ: the world outside muffled, the futon's fabric, your own small space
# ==========================================================================
def closet(rng, i=1):
    L = 40.0
    n = ns(L)
    out = np.zeros((2, n))
    # the rain, far away through the fusuma, the room and the walls
    hz = np.vstack([K.rain_hiss(n, rng, L=L, bright=0.4) for _ in range(2)])
    hz = K.cr(hz, lambda v: dsp.lowpass(dsp.lowpass(v, 380, 2), 900, 2))
    out += hz * 0.10
    g = K.rain_glass(n, rng, rate=35.0, L=L, close=0.5)
    g = K.cr(g, lambda v: dsp.lowpass(v, 800, 2))
    out += dsp.pan(g * 0.12, 0.6)
    fr = _fridge_cycle(L, n, rng, [(4.0, 30.0)])  # through two walls: only its 100 Hz
    fr = K.cr(fr, lambda v: dsp.lowpass(v, 180, 2))
    out += dsp.pan(K.nrm(fr) * 0.035, -0.4)
    hs = _house_ticks(L, n, rng, k=3, muffle=1200)
    out += dsp.pan(K.nrm(hs) * 0.03, 0.3)
    # the futon above you and your own clothes against it: soft shifts, very close
    fab = np.zeros((2, n))
    for t0, d, g_, p in ((3.2, 1.1, 1.0, -0.35), (13.5, 0.7, 0.6, 0.2), (21.0, 1.6, 0.8, -0.1), (33.4, 0.9, 0.7, 0.4)):
        m = ns(d)
        e = K.env_shape(m, [(0, 0), (d * 0.3, 1.0), (d * 0.6, 0.5), (d, 0)], 1.3)
        f = K.fabric(d, rng, e, coarse=0.5, bright=0.7)
        K.place(fab, dsp.pan(f * g_, p), t0, L=L)
    out += K.cr(fab, lambda v: dsp.lowpass(v, 5000, 2)) / (np.max(np.abs(fab)) + 1e-9) * 0.05
    # the plywood under you, once or twice
    for t0 in (8.3, 27.6):
        c = K.creak(rng, rng.uniform(0.25, 0.45), pitch=1.3, rate=(40, 80), q=1.3)
        K.place(out, dsp.pan(K.nrm(c) * 0.035, rng.uniform(-0.3, 0.3)), t0, L=L)
    # your space: the box's own boom (a close, dead cavity), a pressure, the ringing of silence, a theta pair
    cav = np.vstack([dsp.colored(n, rng, -3.0, lo=60, hi=900) for _ in range(2)])
    cav = K.cr(cav, lambda v: dsp.resbank(v, [(150, 4, 1.0), (380, 5, 0.5), (620, 6, 0.25)]))
    out += cav / (np.std(cav) + 1e-12) * 0.004
    press = dsp.sub_rumble(n, 31.0, L=L, rng=rng, am_rate=0.5, am_depth=0.3) * K.lfo(n, 3, rng, 0.4, 1.0)
    out += np.vstack([press, press]) * 0.01
    out += _ring(n, L, rng, 6280.0, 4.5) * 0.0044 * K.lfo(n, 2, rng, 0.4, 1.0)[None, :]
    out += dsp.binaural(140.0, 5.0, n, L=L, rng=rng) * 0.0035
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# amb.bath - 30 s: drips into a full tub, the tiles answering, rain on the frosted window
# ==========================================================================
def bath(rng, i=1):
    L = 30.0
    n = ns(L)
    dry = np.zeros(n)
    t = 0.4
    while t < L - 0.05:  # the tap: one slow drip, its pitch wandering with the drop size
        d = K.drip(rng, rng.uniform(560, 980), rng.uniform(0.6, 1.0), splash=0.05)
        K.place(dry, d, t, L=L)
        if rng.random() < 0.18:
            K.place(dry, K.drip(rng, rng.uniform(700, 1200), rng.uniform(0.2, 0.45)), t + rng.uniform(0.09, 0.2), L=L)
        t += rng.uniform(1.05, 1.75)
    for _ in range(5):  # drops off the shower head onto the tiles
        K.place(dry, K.drop_on(rng, "tile", rng.uniform(0.15, 0.35)), rng.uniform(0, L), L=L)
    gl = K.bubbles(1.8, rng, 50, (1.5, 6.0))  # once, the drain gurgles
    gl = dsp.lowpass(gl, 1800, 2)
    K.place(dry, K.nrm(gl) * 0.25, 18.5, L=L)
    dry = K.cr(dry, lambda v: dsp.highpass(v, 180, 2))
    out = np.vstack([dry, np.roll(dry, ns(0.0012))])
    ir = K.room(rng, 1.15, stereo=True, predelay=0.004, hf=0.75)  # bare tiles: bright and long for its size
    out = dsp.circ_reverb(out, ir, wet=0.55, dry=0.75)
    out = K.nrm(out) * 0.6
    g = K.rain_glass(n, rng, rate=30.0, L=L, close=0.6)
    g = K.cr(g, lambda v: dsp.lowpass(v, 2600, 2))
    out += dsp.pan(g * 0.06, -0.5)
    hz = np.vstack([K.rain_hiss(n, rng, L=L, bright=0.4) for _ in range(2)])
    out += K.cr(hz, lambda v: dsp.lowpass(v, 700, 2)) * 0.012
    tone_ = np.vstack([dsp.colored(n, rng, -2.0, lo=200, hi=4000) for _ in range(2)])  # the tiles' faint ring
    tone_ = K.cr(tone_, lambda v: dsp.resbank(v, [(510, 30, 1.0), (1020, 35, 0.6), (1530, 40, 0.4), (2210, 40, 0.3)]))
    out += tone_ / (np.std(tone_) + 1e-12) * 0.0035
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# amb.outside - 60 s: the open corridor in the rain, the street below
# ==========================================================================
def outside(rng, i=1):
    L = 60.0
    n = ns(L)
    out = np.zeros((2, n))
    for c in range(2):
        out[c] += K.rain_hiss(n, rng, L=L, bright=1.0, dens=1.2) * 0.32
    g = K.rain_glass(n, rng, rate=80.0, L=L, close=1.0)  # on the corridor's handrail cover and the doors
    out += dsp.pan(K.cr(g, lambda v: dsp.lowpass(v, 9000, 2)) * 0.12, -0.3)
    gt = K.gutter(n, rng, L=L, flow=1.6)  # the downpipe at the stairs, overflowing
    out += dsp.pan(K.rmsn(gt) * 0.12, -0.75)
    dr = K.eaves_drips(n, rng, streams=4, surface="metal", L=L, per=(0.5, 1.6))
    out += dsp.pan(K.nrm(dr) * 0.16, 0.45)
    pd = np.zeros(n)
    for _ in range(140):  # drops into puddles on the corridor
        K.place(pd, K.drip(rng, rng.uniform(500, 1400), rng.uniform(0.2, 0.7)), rng.uniform(0, L), L=L)
    out += np.vstack([pd, np.roll(pd, ns(0.004))]) / (np.max(np.abs(pd)) + 1e-9) * 0.07
    vm = K.hum(L, n, rng, 50.0, ((2, 1.0), (4, 0.4), (6, 0.25), (3, 0.2)))  # the vending machine down on the street
    vm = vm / (np.max(np.abs(vm)) + 1e-9) + K.rmsn(dsp.colored(n, rng, -3, lo=200, hi=2000)) * 0.08
    out += dsp.pan(vm * 0.02, 0.7)
    for t0, p in ((18.0, (0.9, -0.6)), (44.0, (-0.8, 0.7))):
        car = _car_pass(rng, 9.0, far=False)
        K.place(out, dsp.pan(K.nrm(car) * 0.22, np.linspace(p[0], p[1], len(car))), t0, L=L)
    tr = _freight(rng, 18.0)
    K.place(out, dsp.pan(K.nrm(tr) * 0.08, 0.2), 30.0, L=L)
    city = np.vstack([dsp.colored(n, rng, -5.0, lo=30, hi=500) for _ in range(2)])
    out += city * 0.025
    gust = K.norm01(dsp.smooth(n, rng, 0.1)) ** 2
    for c in range(2):
        wn = dsp.colored(n, rng, -4.0, lo=100, hi=2500)
        out[c] += wn / np.std(wn) * 0.04 * (0.2 + 0.8 * gust)
    ir = K.room(rng, 0.9, stereo=True, predelay=0.02, hf=0.4)  # the corridor's concrete, the building opposite
    out = dsp.circ_reverb(out, ir, wet=0.22, dry=0.9)
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# amb.silence - 20 s: nothing.  The faintest air, the ringing in your ears
# ==========================================================================
def silence(rng, i=1):
    L = 20.0
    n = ns(L)
    air = np.vstack([dsp.colored(n, rng, -4.0, lo=40, hi=3000) for _ in range(2)])
    out = air * 0.02
    out += _ring(n, L, rng, 5800.0, 4.0) * 0.004 * K.lfo(n, 1, rng, 0.5, 1.0)[None, :]
    lo = dsp.sub_rumble(n, 34.0, L=L, rng=rng, am_rate=4.5, am_depth=0.3)
    out += np.vstack([lo, lo]) * 0.03
    return dsp.dc_block(out, circular=True)


# ==========================================================================
# positional loops (mono)
# ==========================================================================
def fridge(rng, i=1):
    """The old fridge in the DK, running: 100 Hz compressor hum and its harmonics, a loose panel buzzing along,
    refrigerant hissing in the coil, the fan behind the grille."""
    L = 8.0
    n = ns(L)
    h = K.hum(L, n, rng, 50.0, ((1, 0.2), (2, 1.0), (3, 0.35), (4, 0.55), (5, 0.12), (6, 0.25), (8, 0.12), (10, 0.05)))
    h *= 1 + 0.04 * dsp.smooth(n, rng, 0.6)
    pan_ = dsp.am(dsp.colored(n, rng, -1.0, lo=250, hi=1600), dsp.loop_freq(100.0, L), 0.85, L, shape=3.0)
    pan_ *= np.clip(0.3 + 0.9 * K.norm01(dsp.smooth(n, rng, 0.5)), 0, 1)  # the buzz comes and goes
    hiss = dsp.colored(n, rng, -1.5, lo=1500, hi=7000)
    fan = dsp.colored(n, rng, -3.0, lo=150, hi=2500)
    fan = dsp.am(fan, dsp.loop_freq(23.0, L), 0.25, L)
    y = K.nrm(h) * 0.8 + K.rmsn(pan_) * 0.07 + K.rmsn(hiss) * 0.02 + K.rmsn(fan) * 0.06
    for _ in range(3):
        K.place(y, ins.modal_hit(rng, [(2400, 0.01, 1.0), (3900, 0.008, 0.5)], hardness=1.0, vel=0.04, dur=0.03), rng.uniform(0, L), L=L)
    return K.cr(y, lambda v: dsp.lowpass(v, 6000, 2))


def clock(rng, i=1):
    """The cheap quartz wall clock in the tatami room: tick... tick... (the second hand's step and the plastic
    case answering), every second, the odd ones a little different."""
    L = 8.0
    n = ns(L)
    y = np.zeros(n)
    for k in range(8):
        tick = ins.modal_hit(rng, [(2350, 0.022, 1.0), (3800, 0.016, 0.7), (5900, 0.01, 0.35), (1150, 0.03, 0.4)],
                             size=1.0 if k % 2 == 0 else 1.06, hardness=1.0, vel=1.0 if k % 2 == 0 else 0.86, dur=0.09, noise=0.3)
        case = ins.modal_hit(rng, [(820, 0.04, 1.0), (1650, 0.03, 0.5)], hardness=0.7, vel=0.35, dur=0.12, noise=0.1)
        K.place(y, K.nrm(tick), 0.05 + k * 1.0, L=L)
        K.place(y, K.nrm(case) * 0.3, 0.0505 + k * 1.0, L=L)
    return K.cr(y, lambda v: dsp.highpass(v, 300, 2))


def tap_bath(rng, i=1):
    """The bath tap running full into the deep tub: the column's roar in the water, big bubbles churning, the tub's
    body humming along."""
    L = 12.0
    n = ns(L)
    roar = dsp.colored(n, rng, -2.5, lo=120, hi=7000)
    roar *= 1 + 0.18 * dsp.smooth(n, rng, 3.0) + 0.1 * dsp.smooth(n, rng, 11.0)
    roar = K.cr(roar, lambda v: dsp.peq(v, 700, 0.8, 5.0))
    bb = K.bubbles(L, rng, 900, (2.0, 9.0), L=L)
    fine = K.bubbles(L, rng, 1400, (0.6, 2.0), L=L)
    body = K.cr(roar, lambda v: dsp.resbank(v, [(160, 6, 1.0), (310, 7, 0.6), (470, 8, 0.4)]))
    y = K.rmsn(roar) * 0.55 + K.rmsn(bb) * 0.45 + K.rmsn(fine) * 0.2 + K.rmsn(body) * 0.12
    return K.cr(y, lambda v: dsp.highpass(v, 70, 2))


def tap(rng, i=1):
    """A thin stream hitting a stainless sink: bright splash, the steel ringing faintly, water swirling at the drain."""
    L = 10.0
    n = ns(L)
    sp = dsp.colored(n, rng, -0.5, lo=600, hi=11000)
    sp *= 1 + 0.25 * dsp.smooth(n, rng, 6.0)
    x = K.stream(L, rng, 1500, pareto=2.6)
    drops = K.grain_bank(x, [(1800, 3, 1.0), (2900, 4, 0.8), (4400, 4, 0.5)], decay=0.002, L=L)
    steel = K.cr(sp, lambda v: dsp.resbank(v, [(620, 40, 1.0), (1180, 45, 0.7), (1890, 50, 0.5), (2760, 50, 0.3)]))
    drain = K.bubbles(L, rng, 260, (2.0, 6.0), L=L)
    y = K.rmsn(sp) * 0.5 + K.rmsn(drops) * 0.35 + K.rmsn(steel) * 0.12 + K.rmsn(drain) * 0.2
    return K.cr(y, lambda v: dsp.highpass(v, 150, 2))


def washing_machine(rng, i=1):
    """An old top-loader washing: the pulsator driven left, pause, right, pause - the motor's hum rising and falling
    with each stroke, gears grinding, water churned against the tub, the cabinet rattling."""
    L = 14.0
    n = ns(L)
    t = np.arange(n) / SR
    stroke = L / 8.0
    ph = (t % stroke) / stroke
    drive = np.clip(np.sin(np.pi * np.clip(ph / 0.78, 0, 1)), 0, 1) ** 0.8 * (ph < 0.78)
    drive = K.cr(drive, lambda v: dsp.lowpass0(v, 12.0))
    f = 48.0 + 26.0 * drive
    ph_m = dsp.periodic_phase(f)
    motor = sum(a * np.sin(TAU * k * ph_m) for k, a in ((1, 0.6), (2, 1.0), (3, 0.5), (4, 0.4), (6, 0.2), (8, 0.1)))
    motor *= 0.3 + 0.7 * drive
    gear = dsp.colored(n, rng, -1.0, lo=800, hi=4000) * drive ** 2
    churn = dsp.colored(n, rng, -3.0, lo=150, hi=3000)
    churn = churn * (0.25 + 0.75 * K.cr(np.roll(drive, ns(0.15)), lambda v: dsp.lowpass0(v, 6.0)))
    churn *= 1 + 0.4 * dsp.smooth(n, rng, 8.0)
    sl = np.zeros(n)
    for k in range(8):  # the water slapping the tub at the end of every stroke
        K.place(sl, K.slosh(0.8, rng, [(0.0, 1.0)], bright=0.4), k * stroke + stroke * 0.7, L=L)
    rattle = dsp.am(dsp.colored(n, rng, -1.0, lo=300, hi=1800), dsp.loop_freq(37.0, L), 0.8, L, shape=4.0) * drive
    y = K.nrm(motor) * 0.35 + K.rmsn(gear) * 0.05 + K.rmsn(churn) * 0.25 + K.nrm(sl) * 0.35 + K.rmsn(rattle) * 0.04
    return K.cr(y, lambda v: dsp.highpass(v, 40, 2))


def rain_window(rng, i=1):
    """Rain on a window right next to you: drops tapping the glass, little rivulets, the rain outside behind it."""
    L = 10.0
    n = ns(L)
    g = K.rain_glass(n, rng, rate=70.0, L=L, close=1.0)
    hz = K.rain_hiss(n, rng, L=L, bright=0.6)
    hz = K.cr(hz, lambda v: dsp.lowpass(v, 1500, 2))
    frame = K.cr(g, lambda v: dsp.resbank(v, [(240, 8, 1.0), (520, 9, 0.6)]))  # the aluminium frame buzzing along
    y = g * 0.8 + K.rmsn(hz) * 0.12 + K.nrm(frame) * 0.05
    return dsp.dc_block(y, circular=True)


def mic_hiss(rng, i=1):
    """The action camera's microphone on its feed: preamp hiss band-limited by the cheap capsule, the AGC riding
    the noise up and down, a faint switching-regulator whine, codec ticks.  Nearly mono."""
    L = 8.0
    n = ns(L)
    h = dsp.colored(n, rng, -0.8, lo=180, hi=7000)
    h = K.cr(h, lambda v: dsp.peq(v, 2600, 1.2, 3.0))
    agc = 1.0 + 0.16 * dsp.smooth(n, rng, 0.7) + 0.05 * dsp.smooth(n, rng, 4.0)
    whine = K.tone(7812.5, L, n, rng) * (1 + 0.3 * dsp.smooth(n, rng, 2.0))
    low = dsp.colored(n, rng, -6.0, lo=40, hi=300)
    ticks = K.stream(L, rng, 1.5, pareto=2.5)
    ticks = K.grain_bank(ticks, [(3000, 1.0, 1.0)], decay=0.0003, L=L)
    m = K.rmsn(h) * agc + K.rmsn(low) * 0.25 + whine * 0.012 + K.nrm(ticks) * 0.25
    d = dsp.colored(n, rng, -0.8, lo=180, hi=7000)
    out = np.vstack([m + K.rmsn(d) * 0.15, m - K.rmsn(d) * 0.15])
    return dsp.dc_block(out, circular=True)
