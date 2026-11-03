"""Everything built on the restaurant's jingle (jingle.py): music.store, music.store_wrong, music.closing,
music.title, music.ending_good, music.ending_bad.  Loops are circular (notes placed modulo the loop, circular
reverb, a circular varispeed for the tape effects)."""
import numpy as np

import dsp
import instruments as ins
import jingle as J
import voice as V
from dsp import SR, TAU, ns


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0, phase=None):
    ph = cycles * np.arange(n) / n + (rng.random() if phase is None else phase)
    return lo + (hi - lo) * (0.5 - 0.5 * np.cos(TAU * ph)) ** shape


def circ_varispeed(x, n_out, rate):
    """Read the periodic signal x at a periodic rate (per output sample), rescaled so that exactly one pass of x
    fills n_out samples: tape slow-down / wow / flutter that still loops seamlessly.  Catmull-Rom interpolation."""
    m = x.shape[-1]
    r = np.asarray(rate, float)
    r = r * (m / r.sum())
    pos = np.concatenate([[0.0], np.cumsum(r)[:-1]])
    i1 = np.floor(pos).astype(np.int64)
    f = pos - i1
    i0, i2, i3 = (i1 - 1) % m, (i1 + 1) % m, (i1 + 2) % m
    i1 = i1 % m
    def interp(s):
        p0, p1, p2, p3 = s[i0], s[i1], s[i2], s[i3]
        return p1 + 0.5 * f * (p2 - p0 + f * (2 * p0 - 5 * p1 + 4 * p2 - p3 + f * (3 * (p1 - p2) + p3 - p0)))
    if x.ndim == 1:
        return interp(x)
    return np.vstack([interp(c) for c in x])


def _flute(f, d, rng, vel=0.6):
    n = ns(d + 0.25)
    t = np.arange(n) / SR
    ff = f * dsp.cents(9 * np.sin(TAU * 5.0 * t + rng.random() * 6) * np.clip((t - 0.18) / 0.3, 0, 1))
    ph = np.cumsum(ff) / SR
    y = np.sin(TAU * ph) + 0.12 * np.sin(2 * TAU * ph + 0.5) + 0.04 * np.sin(3 * TAU * ph)
    env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (max(0.06, d - 0.04), 0.85), (d + 0.2, 0.0), (d + 0.25, 0)])
    br = dsp.bandpass(rng.standard_normal(n), 1500, 7000, 2) * env * 0.05
    return (y * env + br) * vel


def _lofi(out, rng, L, lp=10500.0, hp=70.0, drive=1.25, hiss=0.0025, wow=0.0012):
    n = out.shape[-1]
    t = np.arange(n) / SR
    rate = 1.0 + wow * np.sin(TAU * dsp.loop_freq(0.55, L) * t) + wow * 0.35 * np.sin(TAU * dsp.loop_freq(5.9, L) * t)
    out = circ_varispeed(out, n, rate)
    out = dsp.circ(lambda x: dsp.lowpass(x, lp, 2), out)
    out = dsp.circ(lambda x: dsp.highpass(x, hp, 2), out)
    out = dsp.circ(lambda x: dsp.peq(x, 2800, 1.0, 1.5), out)
    pk = np.max(np.abs(out)) + 1e-12
    out = dsp.sat(out / pk * drive, 1.0) * pk / drive
    hs = np.vstack([dsp.colored(n, rng, -1.0, lo=800, hi=12000) for _ in range(2)])
    return out + hs * hiss * pk


def _store_mix(rng, bars, beat, melody_a=True, melody_b=True, drop=0.0, detune_mel=0.0, detune_keys=0.0):
    L = bars * 4 * beat
    n = ns(L)
    back = J.bossa_backing(rng, bars, beat, detune_keys=detune_keys)
    mel = np.zeros((2, n))
    if melody_a:
        J.place_notes(mel, J.MELODY, beat,
                      lambda f, d, k: dsp.pan(ins.vibraphone(f * dsp.cents(detune_mel), d + 1.4, rng,
                                                             vel=rng.uniform(0.75, 0.9), depth=0.22,
                                                             release=d + 0.5), 0.15),
                      rng=rng, drop=drop)
    if melody_b and bars > 8:
        def mk(f, d, k):
            e = ins.epiano(f * dsp.cents(detune_mel), d + 0.8, rng, vel=0.55, bright=0.45, tine=0.8, release=d + 0.1)
            fl = _flute(f * dsp.cents(detune_mel + 4), d, rng, vel=0.22)
            return dsp.pan(dsp.mix(e, fl), -0.12)
        notes = [(b, nm, d_) for b, nm, d_ in J.MELODY_B if b < (bars - 8) * 4]
        J.place_notes(mel, notes, beat, mk, t0=32 * beat, rng=rng, drop=drop)
    out = _nrm(mel) * 0.95 + _nrm(back) * 0.85
    ir = dsp.make_ir(0.7, rng, stereo=True, predelay=0.008, hf_ratio=0.5)
    return dsp.circ_reverb(out, ir, wet=0.22, dry=0.95)


# --------------------------------------------------------------------------
# music.store - 32 s: the in-store easy-listening bossa (120 bpm, 16 bars), slightly lo-fi
# --------------------------------------------------------------------------
def store(rng, i=1):
    beat = 0.5
    out = _store_mix(rng, 16, beat)
    out = _lofi(out, rng, 32.0)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.store_wrong - 32 s: the same tape running slow (-5 semitones), warbling, dropping out, notes missing,
# a woman humming along under it, late and off key
# --------------------------------------------------------------------------
def store_wrong(rng, i=1):
    beat = 0.5
    src = _store_mix(rng, 12, beat, drop=0.12, detune_mel=32.0, detune_keys=-18.0)  # 24 s, periodic
    L = 32.0
    n = ns(L)
    t = np.arange(n) / SR
    ph = rng.random(3)
    rate = (1.0 + 0.035 * np.sin(TAU * (t / L + ph[0])) + 0.011 * np.sin(TAU * (0.5 * t + ph[1]))
            + 0.004 * np.sin(TAU * (6.5 * t + ph[2])) + 0.006 * np.sin(TAU * (3 * t / L)))
    y = circ_varispeed(src, n, rate)
    g = np.ones(n)
    for _ in range(7):  # tape dropouts
        t0, d, depth = rng.uniform(0, L), rng.uniform(0.05, 0.32), rng.uniform(0.55, 0.97)
        idx = (ns(t0) + np.arange(ns(d))) % n
        g[idx] = np.minimum(g[idx], 1.0 - depth * np.sin(np.pi * np.linspace(0, 1, len(idx))) ** 0.6)
    g = dsp.circ(lambda x: dsp.lowpass0(x, 40.0), g)
    y = y * g[None, :]
    y = dsp.circ(lambda x: dsp.lowpass(x, 6200, 2), y)
    y = _lofi(y, rng, L, lp=6800, hp=90, drive=1.6, hiss=0.006, wow=0.0)
    hum50 = np.sin(TAU * 50.0 * t) + 0.3 * np.sin(TAU * 150.0 * t)
    y = y + np.vstack([hum50, hum50]) * 0.004 * np.max(np.abs(y))
    # the humming voice: follows the melody at the slowed speed, late, flat, two wrong notes
    slow = 0.75
    wrong = set(rng.choice(len(J.MELODY), 2, replace=False).tolist())
    notes = [(b * beat / slow + 0.22, d * beat / slow * 0.95, J.hz(nm) * slow, 0.8) for b, nm, d in J.MELODY]
    h = V.hum(rng, notes, table=V.VF, scale=1.04, vowel="m", breath=0.5, vibrato=12, scoop=90,
              off_key=lambda k: (-35.0 + rng.uniform(-25, 25)) + (rng.choice([-180, 170]) if k in wrong else 0.0))
    h = dsp.wrap(dsp.pad_to(h, n + ns(2.0)), n)
    hv = dsp.circ_reverb(np.vstack([h, h]), dsp.make_ir(1.6, rng, stereo=True, predelay=0.02, hf_ratio=0.4),
                         wet=0.6, dry=0.6)
    out = _nrm(y) + _nrm(hv) * 0.16
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.closing - 32 s: the jingle in F minor on an organ (60 bpm), through the PA; low clusters, a clock
# --------------------------------------------------------------------------
def closing(rng, i=1):
    beat = 1.0
    L = 32.0
    n = ns(L)
    t = np.arange(n) / SR
    ch = [(b * beat, (b + d) * beat + 0.05, [J.hz(x) for x in v], 0.18) for b, d, root, v in J.CHORDS_MINOR]
    org = ins.organ(ch, L + 2.0, rng, bars=(5, 0, 7, 5, 0, 2, 0, 0, 0), leslie=0.7, leslie_depth=0.22, click=0.15)
    mel = [(b * beat, (b + d) * beat - 0.04, [J.hz(nm)], 0.42) for b, nm, d in J.MELODY_MINOR]
    org += ins.organ(mel, L + 2.0, rng, bars=(0, 0, 8, 6, 2, 4, 0, 2, 0), leslie=0.75, leslie_depth=0.2, click=0.3)
    ped = [(b * beat, (b + d) * beat, [J.hz(root)], 0.3) for b, d, root, v in J.CHORDS_MINOR]
    org += ins.organ(ped, L + 2.0, rng, bars=(8, 0, 6, 0, 0, 0, 0, 0, 0), leslie=0.0, leslie_depth=0.0, click=0.05)
    org = dsp.wrap(org, n)
    pa = np.vstack([ins.small_speaker(org[c], lo=220, hi=6500, res=2300, drive=1.4) for c in range(2)])
    pa = dsp.circ_reverb(pa, dsp.make_ir(1.3, rng, stereo=True, predelay=0.02, hf_ratio=0.45), wet=0.35, dry=0.9)
    out = _nrm(pa)
    hiss = np.vstack([dsp.colored(n, rng, -1.5, lo=350, hi=7000) for _ in range(2)])
    out += hiss * 0.012 * _lfo(n, 2, rng, 0.7, 1.0)[None, :]
    hum = np.sin(TAU * 50 * t) * 0.6 + np.sin(TAU * 100 * t) * 0.4 + np.sin(TAU * 150 * t) * 0.25
    out += np.vstack([hum, hum]) * 0.004
    cl = np.zeros(n)  # low clusters: F1 + Gb1 sub, a dark string cluster swelling
    sub = dsp.sub_rumble(n, J.hz("F1"), L=L, rng=rng, am_rate=6.0, am_depth=0.4)
    sub2 = dsp.sub_rumble(n, J.hz("F#1"), L=L, rng=rng, am_rate=5.0, am_depth=0.3)
    cl += (sub + 0.7 * sub2) * _lfo(n, 2, rng, 0.2, 1.0, 1.5)
    from music import loop_strings
    st = loop_strings([J.hz("F2"), J.hz("F#2", -10), J.hz("C3", 8), J.hz("C#3", -6)], L, rng,
                      env=_lfo(n, 1, rng, 0.0, 1.0, 2.0), voices=2, detune=10, vib=4, bright=0.2)
    out += np.vstack([cl, cl]) * 0.12 + _nrm(st) * 0.12
    tick = np.zeros((2, n))  # the wall clock, in time with the music (60 bpm), left
    for k in range(int(L)):
        c = ins.modal_hit(rng, [(2400, 0.02, 1.0), (3700, 0.015, 0.6), (5200, 0.01, 0.3)], size=1.0, hardness=1.0,
                          vel=1.0 if k % 2 == 0 else 0.75, dur=0.06, noise=0.4)
        dsp.place(tick, dsp.pan(c, -0.55), k * beat + 0.003, circular=True)
    tick = dsp.circ_reverb(tick, dsp.make_ir(0.9, rng, stereo=True, predelay=0.01), wet=0.4, dry=0.9)
    out += _nrm(tick) * 0.07
    return np.roll(dsp.dc_block(out, circular=True), ns(0.04), axis=-1)  # the seam sits just before the downbeat


# --------------------------------------------------------------------------
# music.title - 48 s: the jingle on a music box, slowly going flat, drips, a far entrance chime, a low drone
# --------------------------------------------------------------------------
def _music_box_line(rng, notes, beat, t0=0.0, octave=1, sag=-45.0, span=32.0, vel=0.6, broken=(), warp=0.0):
    out = []
    for k, (b, nm, d) in enumerate(notes):
        if k in broken:
            continue
        tb = t0 + b * beat + warp * (b * beat) ** 2
        c = sag * min(1.0, tb / span) ** 1.6 + rng.uniform(-12, 12)
        f = J.hz(nm, c) * 2 ** octave
        out.append((tb, ins.tine(f, rng, vel=vel * rng.uniform(0.85, 1.0))))
    return out


def title(rng, i=1):
    L = 48.0
    n = ns(L)
    t = np.arange(n) / SR
    beat = 1.0
    box = np.zeros(n)
    for tb, y in _music_box_line(rng, J.MELODY, beat, t0=0.6, octave=1, sag=-55.0, span=31.0, vel=0.6):
        dsp.place(box, y, tb, circular=True)
    for (b, d, root, v) in J.CHORDS[::1]:  # soft low tines on each chord (the comb's bass)
        tb = 0.6 + b * beat
        c = -55.0 * min(1.0, tb / 31.0) ** 1.6
        dsp.place(box, ins.tine(J.hz(root, c) * 4, rng, vel=0.3), tb + 0.01, circular=True)
        dsp.place(box, ins.tine(J.hz(v[1], c) * 2, rng, vel=0.16), tb + 0.02, circular=True)
    box = dsp.circ(lambda x: dsp.highpass(x, 150, 2), box)
    boxs = np.vstack([box, np.roll(box, ns(0.006))])
    ir = dsp.make_ir(3.6, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.circ_reverb(boxs, ir, wet=0.55, dry=0.8)
    out = _nrm(out) * 0.7
    from sfx_beds import _drip
    dr = np.zeros((2, n))
    for _ in range(9):
        d = _drip(rng, rng.uniform(600, 1400), rng.uniform(0.4, 1.0))
        dsp.place(dr, dsp.pan(dsp.lowpass(d, 6000, 2), rng.uniform(-0.8, 0.8)), rng.uniform(0, L), circular=True)
    dr = dsp.circ_reverb(dr, dsp.make_ir(2.2, rng, stereo=True, predelay=0.02), wet=0.6, dry=0.5)
    out += _nrm(dr) * 0.12
    ch = J.door_chime(rng, rate=1.0, cents=-20.0, vel=1.0)  # far away, through the glass
    ch = dsp.lowpass(ch, 2400, 2)
    chs = dsp.reverb(ch, dsp.make_ir(3.0, rng, stereo=True, predelay=0.05, hf_ratio=0.3), wet=1.0, dry=0.15)
    dsp.place(out, _nrm(chs) * 0.11, 37.5, circular=True)
    dry = np.zeros((2, n))
    for f, a, bt in ((J.hz("F1"), 0.6, 0.07), (J.hz("C2"), 0.45, 0.11), (J.hz("F2"), 0.3, 0.13)):
        for c in range(2):
            fq = dsp.loop_freq(f, L)
            dry[c] += a * (np.sin(TAU * (fq * t + rng.random())) + 0.7 * np.sin(TAU * (dsp.loop_freq(f + bt * (1 + 0.4 * c), L) * t + rng.random())))
    dry *= _lfo(n, 2, rng, 0.5, 1.0)[None, :]
    out += dry * 0.05 + dsp.binaural(120.0, 6.0, n, L=L, rng=rng) * 0.012 * _lfo(n, 3, rng, 0.3, 1.0)[None, :]
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# music.ending_good - 50 s: dawn; warm pads, first birds, the jingle's melody resolved on a piano
# --------------------------------------------------------------------------
def ending_good(rng, i=1):
    import music as MU
    dur = 50.0
    n = ns(dur)
    out = np.zeros((2, n))
    wind = np.vstack([dsp.colored(n, rng, -4.0, lo=120, hi=2500) for _ in range(2)])
    out += wind * 0.035 * dsp.env_pts(n, [(0, 1.0), (14, 0.6), (40, 0.35), (dur, 0.25)])
    chords = [(1.0, ["F2", "C3", "A3", "E4", "G4"]), (9.5, ["D2", "A2", "F3", "C4", "E4"]),
              (18.0, ["A#1", "F2", "D3", "A3", "C4"]), (26.5, ["C2", "G2", "D3", "F3", "A#3"]),
              (35.0, ["F2", "C3", "A3", "C4", "E4", "G4"])]
    for k, (t0, names) in enumerate(chords):
        d = 9.5 if k < 4 else dur - t0
        m = ns(d)
        env = dsp.env_pts(m, [(0, 0), (3.0, 1.0), (d - 2.0, 0.85), (d, 0)])
        pad = ins.strings([J.hz(x) for x in names], d, rng, env=env, voices=3, detune=7, vib_depth=5, vib_rate=4.6,
                          bright=0.12 + 0.06 * k)
        dsp.place(out, dsp.lowpass(pad, 1700 + 300 * k, 2) / (np.max(np.abs(pad)) + 1e-9) * (0.2 + 0.04 * k), t0)
    beat = 1.05  # the melody, gently, resolving
    for b, nm, d in J.MELODY:
        tb = 4.5 + b * beat
        if tb > 40:
            continue
        p = ins.piano(J.hz(nm, rng.uniform(-3, 3)), 5.5, rng, vel=0.42 + 0.1 * (b < 16), bright=0.35)
        dsp.place(out, dsp.pan(p, rng.uniform(-0.25, 0.25)) * 0.4, tb)
    for nm, dt in (("F3", 0.0), ("A3", 0.06), ("C4", 0.12), ("G4", 0.18), ("A4", 0.3), ("F4", 1.2)):
        p = ins.piano(J.hz(nm), 9.0, rng, vel=0.38, bright=0.3)
        dsp.place(out, dsp.pan(p, 0.1) * 0.32, 4.5 + 28 * beat + dt)
    for _ in range(18):
        t0 = rng.uniform(5.0, 46.0)
        bd = MU._bird_tit(rng) if rng.random() < 0.6 else MU._bird_bulbul(rng)
        dsp.place(out, dsp.pan(dsp.lowpass(bd, 7000, 2), rng.uniform(-0.9, 0.9)) * 0.03 * min(1.0, (t0 - 3) / 12), t0)
    for t0 in (21.0, 21.55):
        dsp.place(out, dsp.pan(dsp.lowpass(MU._crow(rng), 3000, 2), 0.6) * 0.03, t0)
    ir = dsp.make_ir(3.4, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.4, dry=0.8), n)
    return dsp.fade(dsp.highpass(out, 30, 2), 1.0, 3.0)


# --------------------------------------------------------------------------
# music.ending_bad - 30 s: 相席 - the jingle on a dying music box, the call button rings twice, silence
# --------------------------------------------------------------------------
def ending_bad(rng, i=1):
    dur = 30.0
    n = ns(dur)
    out = np.zeros((2, n))
    t = 0.8
    speed = 1.0
    gaps = [J.MELODY[k + 1][0] - J.MELODY[k][0] for k in range(len(J.MELODY) - 1)] + [2.0]
    for k, (b, nm, d) in enumerate(J.MELODY):
        if k in (6, 11, 15):
            continue  # broken tines
        f = J.hz(nm, -20 - 70 * (1 - speed) + rng.uniform(-10, 10)) * 2
        tn = ins.tine(f * speed ** 0.35, rng, vel=0.6 * speed ** 0.4)
        dsp.place(out, dsp.pan(tn, 0.15), t)
        t += gaps[k] * 0.62 / speed
        speed *= 0.935
        if t > 19.0:
            break
    m = ns(0.05)  # the spring's last click
    clk = dsp.bandpass(rng.standard_normal(m), 2000, 6000) * np.exp(-np.arange(m) / (0.003 * SR))
    dsp.place(out, np.vstack([clk, clk]) * 0.15, t + 1.0)
    ir = dsp.make_ir(2.8, rng, stereo=True, predelay=0.025, hf_ratio=0.35)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.5, dry=0.8), n)
    for t0 in (22.5, 24.1):  # table 7's call button, twice, across the dark dining room
        a = np.zeros(ns(1.2))
        for nm, dt in J.CALL_BUTTON:
            dsp.place(a, J.call_tone(rng, J.hz(nm), 0.32, 1.0), dt)
        a = ins.small_speaker(a, lo=500, hi=7000, res=3000)
        ar = dsp.reverb(a, dsp.make_ir(1.2, rng, stereo=True, predelay=0.012, hf_ratio=0.5), wet=0.45, dry=0.7)
        dsp.place(out, dsp.pan(_nrm(ar[0]), 0.35) * 0.2 + _nrm(ar) * 0.05, t0)
    tt = np.arange(n) / SR
    room = np.vstack([dsp.colored(n, rng, -3.0, lo=80, hi=3000) for _ in range(2)])
    out += room * 0.004 * dsp.env_pts(n, [(0, 1.0), (24, 1.0), (27.5, 0.0), (dur, 0.0)])[None, :]
    out = dsp.highpass(out, 35, 2)
    out *= dsp.env_pts(n, [(0, 1.0), (25.5, 1.0), (dur, 0.0)])[None, :] ** 0.5
    return dsp.fade(out, 0.2, 0.5)
