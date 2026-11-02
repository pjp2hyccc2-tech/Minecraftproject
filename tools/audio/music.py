"""music.* cues."""
import numpy as np

import dsp
import instruments as ins
import sfx_bride
import voice as V
from dsp import SR, ns


def _loop_tone(f, L, n, rng):
    return np.sin(dsp.TAU * (dsp.loop_freq(f, L) * np.arange(n) / SR + rng.random()))


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0):
    ph = cycles * np.arange(n) / n + rng.random()
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(dsp.TAU * ph)) ** shape


def furin(rng, base=None, vel=1.0):
    """Japanese wind bell: clear tone with a few inharmonic partials."""
    base = base or rng.uniform(2100, 3300)
    y = ins.bell_partials(base, 2.4, rng, [1.0, 2.71, 5.12, 7.9], [1.0, 0.35, 0.15, 0.06], [1.8, 0.9, 0.45, 0.25],
                          beat=(1.0, 4.0), attack=0.0006)
    L = ns(0.002)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * 0.3
    return y * vel


# --------------------------------------------------------------------------
# music.title - low drone, sparse koto, wind chimes (40 s, loops seamlessly)
# --------------------------------------------------------------------------
def title(rng, i=1):
    L = 40.0
    n = ns(L)
    N = ns(L + 9.0)
    ev = np.zeros((2, N))
    phrases = [
        (2.5, [("D4", 0.0, 0.85, None), ("A3", 1.15, 0.6, None), ("Bb3", 2.1, 0.7, [(0, 0), (0.3, 0), (0.8, -60)])]),
        (12.2, [("G4", 0.0, 0.7, None), ("Eb4", 0.85, 0.6, None), ("D4", 1.6, 0.75, None)]),
        (22.4, [("A4", 0.0, 0.8, None), ("Bb4", 0.55, 0.55, None), ("A4", 1.7, 0.6, None), ("D4", 3.0, 0.7, None)]),
        (32.2, [("Eb5", 0.0, 0.45, None), ("D5", 0.95, 0.4, None), ("D3", 1.0, 0.6, None)]),
    ]
    for t0, notes in phrases:
        for nm, dt, vel, bend in notes:
            s = ins.koto(dsp.hz(nm, rng.uniform(-12, 6)), 6.0, rng, vel=vel, bend=bend)
            dsp.place(ev, dsp.pan(s, rng.uniform(-0.5, 0.5)), t0 + dt)
    t = 0.8
    while t < L - 1.5:  # no chime attack right on the loop point
        p = rng.uniform(-0.9, 0.9)
        base = rng.uniform(2100, 3300)
        for k in range(rng.integers(1, 4)):
            dsp.place(ev, dsp.pan(furin(rng, base * dsp.cents(rng.uniform(-30, 30)), rng.uniform(0.25, 0.6)), p),
                      t + k * rng.uniform(0.12, 0.35))
        t += rng.uniform(3.0, 7.0)
    ev = dsp.wrap(ev, n)
    dr = np.zeros((2, n))
    for f, a, bt in [(73.42, 1.0, 0.08), (110.0, 0.5, 0.12), (146.83, 0.3, 0.1), (36.71, 0.4, 0.05), (220.0, 0.08, 0.2)]:
        for c in range(2):
            dr[c] += a * (_loop_tone(f, L, n, rng) + 0.7 * _loop_tone(f + bt * (1 + 0.4 * c), L, n, rng)) * _lfo(n, rng.integers(1, 3), rng, 0.5, 1.0)
    wind = np.vstack([dsp.colored(n, rng, -4.0, lo=100, hi=1800) for _ in range(2)])
    gust = 0.3 + 0.7 * _lfo(n, 2, rng, 0, 1, 2.0)
    out = ev / (np.max(np.abs(ev)) + 1e-9) + dr / (np.max(np.abs(dr)) + 1e-9) * 0.35 + wind * gust * 0.03
    ir = dsp.make_ir(3.5, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.circ_reverb(out, ir, wet=0.45, dry=0.8)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# gagaku core - wrong, slowed wedding music
# --------------------------------------------------------------------------
SHO_CHORDS = [
    ["E4", "F#4", "A4", "B4", "E5", "F#5"],
    ["D4", "E4", "A4", "B4", "D5", "E5"],
    ["A3", "B3", "E4", "F#4", "A4", "B4"],
    ["B3", "C#4", "E4", "F#4", "B4", "C5"],   # C5: the wrong pipe
    ["E4", "F#4", "A4", "B4", "E5", "F#5"],
    ["C#4", "E4", "F#4", "B4", "C#5", "E5"],
    ["A3", "B3", "E4", "F#4", "A4", "Bb4"],   # Bb4 clashes
    ["B3", "E4", "F#4", "A4", "B4", "E5"],
]

HICHIRIKI = [  # (start, dur, note, slide_from_cents, slide_time)
    (1.0, 2.2, "E5", -200, 0.6), (3.3, 1.3, "F#5", -100, 0.25), (4.7, 2.8, "E5", 0, 0.2), (8.7, 1.7, "B4", -200, 0.5),
    (10.4, 2.6, "A4", 0, 0.2), (13.1, 0.9, "B4", -100, 0.2), (14.1, 3.4, "E5", -300, 0.8), (18.5, 1.7, "F#5", -200, 0.5),
    (20.3, 3.3, "A5", -300, 0.9), (23.7, 1.3, "F#5", 0, 0.2), (25.1, 2.9, "E5", -100, 0.3), (30.5, 2.0, "B4", -200, 0.5),
    (32.6, 1.9, "A4", 0, 0.2), (35.0, 3.8, "E5", -200, 0.7),
]


def gagaku_core(rng, L=40.0, loop=True, flat=-38.0):
    n = ns(L)
    T = 6.0
    N = ns(L + T)
    beat = 2.5
    chords = []
    for k in range(int(L // 5.0)):
        names = SHO_CHORDS[k % len(SHO_CHORDS)]
        fr = [dsp.hz(x, rng.uniform(-9, 9)) for x in names]
        chords.append((k * 5.0, k * 5.0 + 5.5, fr, 1.0))
    sho = ins.sho(chords, L + T, rng)
    sho = sho / (np.max(np.abs(sho)) + 1e-9)
    # hichiriki: f0 in log domain with enbai slides, flat and drifting
    lp = np.full(N, np.log2(dsp.hz("E5")))
    amp = np.zeros(N)
    for (st, du, nm, sl, stime) in HICHIRIKI:
        if st >= L:
            continue
        i0, i1 = ns(st), min(N, ns(st + du))
        m = i1 - i0
        x = np.arange(m) / SR
        target = np.log2(dsp.hz(nm, flat + rng.uniform(-8, 8)))
        slide = sl / 1200.0 * np.clip(1 - x / stime, 0, 1) ** 1.6
        lp[i0:i1] = target + slide
        sag = -0.035 * np.clip((x - (du - 0.5)) / 0.5, 0, 1) ** 2
        lp[i0:i1] += sag
        e = np.minimum(1, x / 0.12) * (0.75 + 0.25 * np.sin(np.pi * np.clip(x / du, 0, 1)) ** 1.5)
        e *= np.clip((du - x) / 0.15, 0, 1)
        amp[i0:i1] = e
    lp = dsp.lowpass0(lp, 12.0)
    lp += 0.006 * dsp.smooth(N, rng, 4.0) + 0.004 * np.sin(dsp.TAU * 4.3 * np.arange(N) / SR)
    hk = ins.hichiriki(2.0 ** lp, amp, rng)
    hk = hk / (np.max(np.abs(hk)) + 1e-9)
    perc = np.zeros((2, N))
    for mi in range(int(L // (4 * beat))):
        m0 = mi * 4 * beat
        t = m0 + 0.05
        gap = 0.32
        while gap > 0.05:  # kakko roll (mororai): accelerating strokes
            dsp.place(perc, dsp.pan(ins.kakko_hit(rng, 0.25 + 0.3 * (0.32 - gap) / 0.27), -0.4), t)
            t += gap
            gap *= 0.8
        dsp.place(perc, dsp.pan(ins.shoko_hit(rng, 0.25), 0.5), m0 + 2 * beat)
        dsp.place(perc, dsp.pan(ins.shoko_hit(rng, 0.18), 0.5), m0 + 2 * beat + 0.28)
        dsp.place(perc, dsp.pan(ins.taiko(rng, f0=58, vel=0.35, decay=0.7, dur=2.2, size=1.4), -0.1), m0 + 3 * beat)
        dsp.place(perc, dsp.pan(ins.taiko(rng, f0=56, vel=1.0, decay=0.9, dur=2.6, size=1.5), 0.1), m0 + 3 * beat + 1.25)
    perc = perc / (np.max(np.abs(perc)) + 1e-9)
    tt = np.arange(N) / SR
    drone = np.zeros((2, N))
    for f, a in [(82.41, 1.0), (123.47, 0.5), (87.31, 0.25)]:
        for c in range(2):
            drone[c] += a * np.sin(dsp.TAU * (f * (1 + 0.001 * c)) * tt + rng.random() * 6)
    drone = drone / (np.max(np.abs(drone)) + 1e-9)
    mixd = sho * 0.55 + dsp.pan(hk, -0.15) * 0.5 + perc * 0.7 + drone * 0.12
    if loop:
        mixd = dsp.wrap(mixd, n)
        ir = dsp.make_ir(3.8, rng, stereo=True, predelay=0.035, hf_ratio=0.4)
        mixd = dsp.circ_reverb(mixd, ir, wet=0.42, dry=0.8)
        return dsp.dc_block(mixd, circular=True)
    mixd = mixd[:, :n]
    ir = dsp.make_ir(3.8, rng, stereo=True, predelay=0.035, hf_ratio=0.4)
    return dsp.reverb(mixd, ir, wet=0.42, dry=0.8)


def gagaku(rng, i=1):
    return gagaku_core(rng, 40.0, loop=True)


# --------------------------------------------------------------------------
# music.bad_ending - the wedding music collapsing (~30 s)
# --------------------------------------------------------------------------
def bad_ending(rng, i=1):
    dur = 30.0
    n = ns(dur)
    src = gagaku_core(rng, 26.0, loop=False, flat=-45.0)
    tt = np.arange(n) / SR
    rate = np.interp(tt, [0, 7.0, 30.0], [1.0, 1.0, 0.5]) ** 1.0
    rate *= 1 + 0.01 * np.sin(dsp.TAU * 0.6 * tt)  # warble
    y = dsp.varispeed(src, rate, n)
    y = dsp.pad_to(y, n)
    prog = np.clip((tt - 6.0) / 22.0, 0, 1)
    y = y / (np.max(np.abs(y)) + 1e-9)
    drive = 1.0 + 5.0 * prog ** 1.5
    y = (1 - prog * 0.7) * y + prog * 0.7 * dsp.sat(y * drive, 1.0)
    cr = dsp.crush(y, bits=6, hold=4)
    w = np.clip((tt - 16.0) / 10.0, 0, 1) ** 2
    y = y * (1 - 0.6 * w) + cr * 0.6 * w
    # dissonant low cluster rising underneath
    cl = np.zeros(n)
    for f in [41.2, 43.65, 61.74, 65.41]:
        cl += dsp.saw(f * dsp.cents(dsp.env_pts(n, [(0, 0), (30, -150)])), n, rng.random())
    cl = dsp.lowpass(cl, 300, 2) * dsp.env_pts(n, [(0, 0), (10, 0.0), (26, 1.0), (28.5, 0.6), (30, 0.0)])
    y = y + np.vstack([cl, cl]) / (np.max(np.abs(cl)) + 1e-9) * 0.35
    # her laughter inside the music, and a chorus of whispered congratulations
    la = sfx_bride.laugh(rng, 2)
    la = dsp.reverb(la, dsp.make_ir(2.5, rng, stereo=True), wet=0.6, dry=0.7)
    dsp.place(y, la / (np.max(np.abs(la)) + 1e-9) * 0.45, 15.5)
    ch = np.zeros(ns(4.0))
    for k, (sc, sp) in enumerate([(1.0, 0.9), (0.9, 0.85), (1.1, 0.95), (0.85, 0.8)]):
        w_ = V.whisper("o me de to - go za i ma su", rng, scale=sc, rate=sp)
        dsp.place(ch, w_ * (1 - 0.15 * k), k * 0.18)
    ch = dsp.reverb(ch, dsp.make_ir(2.0, rng, stereo=True), wet=0.5)
    dsp.place(y, ch / (np.max(np.abs(ch)) + 1e-9) * 0.4, 21.0)
    boom = ins.taiko(rng, f0=40, vel=1.0, decay=1.4, dur=3.0, size=1.8)
    dsp.place(y, np.vstack([boom, boom]) * 0.9, 26.6)
    y = dsp.highpass(y, 25, 2)
    y = y * dsp.env_pts(n, [(0, 0.0), (0.5, 1.0), (27.0, 1.0), (30.0, 0.0)])
    return dsp.fade(y, 0.01, 0.5)


# --------------------------------------------------------------------------
# music.ending - melancholic music box / koto, dawn (~60 s)
# --------------------------------------------------------------------------
def ending(rng, i=1):
    dur = 60.0
    n = ns(dur)
    out = np.zeros((2, n))
    tt = np.arange(n) / SR
    # warm pad (A sus2 colours), breathing
    pad_env = dsp.env_pts(n, [(0, 0), (6, 0.8), (30, 1.0), (48, 0.8), (56, 0.4), (60, 0)])
    pad = ins.strings([dsp.hz(x) for x in ["A2", "E3", "B3", "E4"]], dur, rng, env=pad_env, voices=3, detune=8,
                      vib_depth=6, vib_rate=4.5, bright=0.15)
    pad = dsp.lowpass(pad, 1400, 2)
    out += pad / (np.max(np.abs(pad)) + 1e-9) * 0.28
    # koto intro and accompaniment
    intro = [(1.5, "A3", 0.7), (2.6, "E4", 0.6), (3.8, "D4", 0.55, [(0, 0), (0.4, 0), (0.9, -100)]), (5.6, "A2", 0.7)]
    for item in intro:
        t0, nm, vel = item[:3]
        bend = item[3] if len(item) > 3 else None
        dsp.place(out, dsp.pan(ins.koto(dsp.hz(nm, rng.uniform(-6, 6)), 6.0, rng, vel=vel, bend=bend, bright=0.5), rng.uniform(-0.4, 0.4)), t0)
    # music box: kagome, slower and gentle (8 s .. ~33 s)
    octv = {"E": "E5", "G": "G5", "A": "A5", "B": "B5"}
    e = 0.41
    t = 8.0
    flat = [(p, d) for ph in ins.KAGOME for (p, d) in ph]
    acc = 0
    for k, (p, d) in enumerate(flat):
        tn = ins.tine(dsp.hz(octv[p], rng.uniform(-8, 8)), rng, vel=0.33 + 0.12 * rng.random())
        dsp.place(out, dsp.pan(tn, 0.15), t)
        if acc % 8 == 0:
            bass = ["A2", "E3", "A2", "G2", "E3", "A2", "E3"][(acc // 8) % 7]
            dsp.place(out, dsp.pan(ins.koto(dsp.hz(bass), 5.0, rng, vel=0.5, bright=0.4), -0.3), t + 0.01)
        t += d * e * (1.0 + 0.6 * (k == len(flat) - 1))
        acc += d
    mb_end = t
    # koto solo, rubato
    solo = [(mb_end + 1.5, "E4", 0.6), (mb_end + 2.4, "G4", 0.55), (mb_end + 3.1, "A4", 0.65, lambda x: 18 * np.sin(dsp.TAU * 5 * x) * np.clip((x - 0.4) / 0.4, 0, 1) * np.exp(-x / 2)),
            (mb_end + 5.6, "B4", 0.5), (mb_end + 6.4, "A4", 0.55), (mb_end + 7.8, "E4", 0.6, [(0, 0), (0.5, 0), (1.0, -80)]),
            (mb_end + 10.2, "D4", 0.5), (mb_end + 11.0, "E4", 0.6), (mb_end + 13.0, "A3", 0.65)]
    for item in solo:
        t0, nm, vel = item[:3]
        bend = item[3] if len(item) > 3 else None
        dsp.place(out, dsp.pan(ins.koto(dsp.hz(nm, rng.uniform(-5, 5)), 6.0, rng, vel=vel, bend=bend, bright=0.55), rng.uniform(-0.35, 0.35)), t0)
    rin = ins.bell_partials(dsp.hz("A5") * 1.0, 8.0, rng, [1.0, 2.76, 5.4, 8.9], [1.0, 0.45, 0.2, 0.08], [6.0, 3.0, 1.5, 0.8], beat=(0.8, 2.5))
    dsp.place(out, dsp.pan(rin, 0.2) * 0.25, mb_end + 9.0)
    # the music box tries the first phrase once more and runs down
    t = mb_end + 15.0
    for k, (p, d) in enumerate(flat[:6]):
        tn = ins.tine(dsp.hz(octv[p], rng.uniform(-10, 10)), rng, vel=0.32 * (1 - 0.06 * k))
        dsp.place(out, dsp.pan(tn, 0.15), t)
        t += d * e * (1.0 + 0.45 * k)
    dsp.place(out, dsp.pan(ins.koto(dsp.hz("A2"), 6.0, rng, vel=0.6, bright=0.4), 0.0), min(t + 0.8, 54.0))
    dsp.place(out, dsp.pan(rin, -0.2) * 0.2, min(t + 0.9, 54.2))
    ir = dsp.make_ir(3.4, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.reverb(out, ir, wet=0.42, dry=0.8)
    out = dsp.pad_to(out, n)
    out = dsp.highpass(out, 35, 2)
    return dsp.fade(out, 0.5, 4.0)
