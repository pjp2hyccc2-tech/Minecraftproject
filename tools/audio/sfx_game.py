"""game.* - non-diegetic cues (stereo): jumpscares, stingers, false risers,
capture, waking up, checkpoints, area titles, the fog lifting, the loop,
the car, the title and the 神隠し ending sting."""
import numpy as np

import dsp
import instruments as ins
import sfx_monsters as MO
import voice as V
from dsp import SR, TAU, ns


def _nrm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _wide(x, rng, amount=0.012):
    return dsp.widen(x, rng, amount)


def _sub_drop(dur=1.2, f_hi=90.0, f_lo=28.0, tau=0.12, drive=2.0):
    m = ns(dur)
    t = np.arange(m) / SR
    s = np.sin(TAU * np.cumsum(f_lo + (f_hi - f_lo) * np.exp(-t / tau)) / SR) * dsp.env_ad(m, 0.002, dur * 0.25)
    return dsp.sat(s * drive, 1.0)


def _cluster(names, dur, rng, env, **kw):
    return ins.strings([dsp.hz(x) for x in names], dur, rng, env=env, **kw)


# --------------------------------------------------------------------------
# game.jumpscare_1..3 - extremely loud (dense master, <= -1 dBTP)
# --------------------------------------------------------------------------
def jumpscare(rng, i=1):
    dur = {1: 2.2, 2: 2.4, 3: 1.7}[i]
    n = ns(dur)
    out = np.zeros((2, n))
    body = dsp.env_pts(n, [(0, 1.0), (dur * 0.45, 0.95), (dur - 0.15, 0.15), (dur, 0.0)])
    epis = [(0, 3.93), (0.14, 3.93), (0.18, 0), (0.5, 0), (0.54, 3.3), (0.85, 3.3), (0.9, 3.92), (1.3, 3.92),
            (1.34, 3.5), (1.6, 3.5), (1.64, 3.94), (dur, 3.94)]
    if i == 1:  # 覗き-type: a shriek with a second throat a tritone below
        s = MO._shriek_core(rng, dur, 1600, chaos=epis, jumps=[(0.5, 5, 0.12), (1.3, -4, None)], biphon_at=0.9,
                            vowels=[(0, "i"), (0.4, "a"), (dur, "a")], rough=(0.7, 70, 0.6))
        out += dsp.pan(_nrm(s) * body, 0.0) * 1.0
        s2 = MO._shriek_core(rng, dur, 2050, chaos=epis[::-1][::-1], jumps=[(0.8, -6, None)], biphon_at=0.4,
                             vowels=[(0, "e"), (dur, "i")], rough=(0.6, 85, 0.6), scale=1.2)
        out += _wide(_nrm(s2) * body, rng, 0.02)[:, :n] * 0.35
        cl = ["E5", "F5", "F#5", "A#4", "B4", "C6", "C#6"]
    elif i == 2:  # the giant: a roar with a scream inside it
        r = MO.oyama_roar(rng, 1)[:n]
        out += _wide(_nrm(dsp.pad_to(r, n)) * body, rng)[:, :n] * 0.8
        s, _ = V.scream(dur, rng, [(0, 900), (0.1, 1300), (1.2, 1200), (dur, 700)], [(0, 1.0), (dur * 0.6, 0.9), (dur, 0)],
                        vowel_pts=[(0, "a"), (dur, "e")], chaos_pts=epis, jumps=[(0.7, 4, None)], rough=(0.7, 66, 0.6),
                        rasp=1.0, breath=0.5)
        out += dsp.pan(_nrm(s), 0.25) * 0.9
        cl = ["D3", "D#3", "A3", "G#4", "D5", "D#5"]
    else:  # 後ろ: a scream inside your ear, then the signal tears
        s = MO.ushiro_attack(rng, 1)
        s = dsp.pad_to(_nrm(s), n)
        out += np.vstack([s, s * 0.5])
        cl = ["G5", "G#5", "A5", "D#6", "E6", "C#5"]
    st = _cluster(cl, dur, rng, body, voices=3, detune=28, vib_depth=35, trem=0.55, trem_rate=17, bright=0.95, pont=0.8)
    out += st / (np.max(np.abs(st)) + 1e-9) * 0.3
    nz = np.vstack([dsp.bandpass(rng.standard_normal(n), 400, 9000, 2) for _ in range(2)])
    out += dsp.sat(nz * 2.5, 1.0) * dsp.env_pts(n, [(0, 1.0), (0.12, 0.35), (0.3, 0.0), (dur, 0)]) * 0.45
    m = ns(0.05)
    tr = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.008 * SR))
    dsp.place(out, np.vstack([tr, tr]) * 1.2, 0)
    if i == 3:  # stutter glitch: a 31 ms slice repeated like a torn buffer
        seg = out[:, ns(0.3):ns(0.331)].copy()
        for k in range(7):
            dsp.place(out, seg * 0.8, 0.9 + k * 0.031)
    out = dsp.highpass(out, 120, 2)
    out = _nrm(out)
    sub = _sub_drop(0.9, 95, 32, 0.07, 2.4)
    dsp.place(out, np.vstack([sub, sub]) * 0.28, 0)
    out = dsp.highpass(out, 30, 2)
    return _nrm(out)


# --------------------------------------------------------------------------
# game.stinger_1..4 - dissonant cluster swell hits
# --------------------------------------------------------------------------
def stinger(rng, i=1):
    if i == 1:
        dur = 3.6
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.06, 1.0), (0.6, 0.75), (dur - 0.2, 0.0), (dur, 0)])
        st = _cluster(["C#5", "D5", "D#5", "G5", "G#5", "C6", "C#6"], dur, rng, env, voices=3, detune=18, vib_depth=15,
                      trem=0.55, trem_rate=15, bright=0.8, pont=0.6)
        lo = _cluster(["D2", "D#2", "A2"], dur, rng, env, voices=3, detune=12, bright=0.4)
        out = _nrm(st) + 0.6 * _nrm(lo)
        boom = ins.taiko(rng, f0=48, vel=1.0, decay=0.8, dur=2.5)
        dsp.place(out, np.vstack([boom, boom]) * 0.8, 0.0)
    elif i == 2:
        dur = 4.4
        n = ns(dur)
        th = 1.3
        cl = ["C3", "C#3", "G3", "G#3", "D4", "D#4", "A4"]
        rev_src = _cluster(cl, 1.0, rng, dsp.env_pts(ns(1.0), [(0, 1), (1.0, 0)]), voices=2, bright=0.7)
        rev = dsp.reverse(dsp.reverb(rev_src, dsp.make_ir(2.5, rng, stereo=True), wet=1.0, dry=0.2))
        out = np.zeros((2, n))
        dsp.place_end(out, _nrm(rev) * 0.8, th)
        env = dsp.env_pts(n, [(0, 0), (th, 0), (th + 0.04, 1.0), (th + 0.8, 0.6), (dur - 0.2, 0), (dur, 0)])
        hit = _cluster(cl + ["C2", "C#2"], dur, rng, env, voices=3, detune=20, trem=0.3, bright=0.6, pont=0.3)
        out += _nrm(hit)
        tk = ins.taiko(rng, f0=52, vel=1.0, decay=0.7, dur=2.0)
        dsp.place(out, np.vstack([tk, tk]) * 0.9, th)
    elif i == 3:
        dur = 3.4
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.55, 1.0), (1.2, 0.8), (dur - 0.2, 0), (dur, 0)]) ** 1.3
        gl = dsp.env_pts(n, [(0, -500), (0.6, 0), (dur, 60)])
        st = ins.strings([dsp.hz(x) for x in ["E6", "F6", "A#5", "B5", "F#6"]], dur, rng, env=env, voices=3, detune=30,
                         vib_depth=25, trem=0.6, trem_rate=18, bright=1.0, pont=0.9, gliss=gl)
        out = _nrm(st)
        wh = dsp.tv_filter(rng.standard_normal(n), dsp.env_pts(n, [(0, 300), (0.6, 3000), (dur, 500)]), 1.5, "bp")
        out += np.vstack([wh, wh]) / np.std(wh) * env * 0.12
        sub = _sub_drop(1.6, 68, 28, 0.2, 1.6)
        dsp.place(out, np.vstack([sub, sub]) * 0.9, 0.58)
    else:  # low brass cluster slam + taiko + metal scrape
        dur = 4.0
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (0.5, 0.7), (dur - 0.3, 0), (dur, 0)])
        out = _brass_cluster([36.71, 38.89, 55.0, 51.91, 73.42, 103.83], dur, rng, env)
        tk = ins.taiko(rng, f0=44, vel=1.0, decay=1.0, dur=3.0, size=1.6)
        dsp.place(out, np.vstack([tk, tk]) * 0.9, 0.0)
        m = ns(1.6)
        x = np.linspace(0, 1, m)
        sc = np.zeros(m)
        for f in (1630, 2290, 3110, 4570):
            sc += np.sin(TAU * np.cumsum(f * dsp.cents(-500 * x + 20 * dsp.smooth(m, rng, 8))) / SR) * rng.uniform(0.3, 1)
        sc *= np.sin(np.pi * x) ** 1.5
        dsp.place(out, dsp.pan(sc, np.linspace(-0.8, 0.8, m)) * 0.06, 0.1)
    ir = dsp.make_ir(2.4, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.highpass(out, 30, 2)
    out = dsp.limiter(dsp.norm_peak(out, 0.0), -3.0, 0.005)
    return dsp.trim(out, -60)


def _brass_cluster(freqs, dur, rng, env):
    n = ns(dur)
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(3):
            fv = f * dsp.cents(rng.normal(0, 8)) * dsp.cents(dsp.env_pts(n, [(0, -60), (0.08, 0), (dur, 0)]))
            s = dsp.saw(fv, n, rng.random())
            out += dsp.pan(s, rng.uniform(-0.6, 0.6))
    bright = 300 + 2600 * env
    out = np.vstack([dsp.tv_filter(out[c], bright, 0.8, "lp", block=128) for c in range(2)])
    out = dsp.am(out, 31.0, 0.15)
    return _nrm(out) * env


# --------------------------------------------------------------------------
# game.riser_1..2 - rising tension that cuts to silence
# --------------------------------------------------------------------------
def riser(rng, i=1):
    dur = 5.2 if i == 1 else 4.3
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    prog = np.clip(t / dur, 0, 1)
    # accelerating Shepard rise (the rate itself climbs)
    rate = 0.2 + 1.4 * prog ** 2
    ph_oct = np.cumsum(rate) / SR
    sh = np.zeros(n)
    for k in range(7):
        pos = (ph_oct / 7.0 + k / 7.0) % 1.0
        f = 40.0 * 2 ** (7.0 * pos)
        sh += np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * pos) ** 2
    out += _wide(sh, rng)[:, :n] * 0.18 * (0.3 + 0.7 * prog)
    nz = rng.standard_normal(n)
    fc = 200 * 2 ** (prog * 5.5)
    rs = dsp.tv_filter(nz, fc, 1.6, "bp")
    out += np.vstack([rs, np.roll(rs, 200)]) / np.std(rs) * 0.1 * prog ** 1.5
    cl = ["D4", "D#4", "G#4", "A4", "D5"] if i == 1 else ["C#5", "D5", "G5", "G#5"]
    st = _cluster(cl, dur, rng, prog ** 1.8, voices=3, detune=20, trem=0.6, trem_rate=8, bright=0.7, pont=0.5,
                  gliss=dsp.env_pts(n, [(0, 0), (dur, 150)]))
    out += _nrm(st) * 0.45
    beat = 0.9
    tt = 0.2
    while tt < dur - 0.15:  # heartbeat accelerating
        hb = ins.heartbeat(rng, vel=0.5 + 0.5 * tt / dur)
        dsp.place(out, np.vstack([hb, hb]) * 0.9, tt)
        tt += beat
        beat = max(0.28, beat * 0.88)
    if i == 2:  # a whisper rides the swell: 「うしろ」
        w = V.whisper("u shi ro", rng, rate=0.9, close=0.8)
        dsp.place(out, dsp.pan(_nrm(w), -0.85) * 0.25, dur - 1.4)
    out *= (0.15 + 0.85 * prog ** 1.4)[None, :]
    out = dsp.highpass(out, 30, 2)
    out = _nrm(out) * 0.9
    out[:, -ns(0.003):] *= np.linspace(1, 0, ns(0.003))[None, :]  # cut to silence
    return out


# --------------------------------------------------------------------------
# game.capture - violent rush, choir cluster, cut to silence
# --------------------------------------------------------------------------
def _choir(rng, freqs, dur, env, vowel="a", table=V.VM):
    n = ns(dur)
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(2):
            ff = f * dsp.cents(rng.normal(0, 12) + 18 * np.sin(TAU * rng.uniform(4.5, 6.0) * np.arange(n) / SR + rng.random() * 6))
            vv = V.voice(ff, env, [(0, V.fs(vowel, table)), (dur, V.fs("o", table))], rng, tilt=-7, fmax=7000,
                         jitter=0.01, shimmer=0.08, breath=0.3)
            out += dsp.pan(vv / (np.std(vv) + 1e-9), rng.uniform(-0.8, 0.8))
    return out


def capture(rng, i=1):
    dur = 3.0
    cut = 2.45
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    env = np.clip(t / cut, 0, 1) ** 1.5 * (t < cut)
    nz = rng.standard_normal(n)
    rush = dsp.tv_filter(nz, 150 * 2 ** (np.clip(t / cut, 0, 1) * 5), 0.9, "bp")
    out += np.vstack([rush, np.roll(rush, 300)]) / np.std(rush) * env * 0.35
    ch = _choir(rng, [146.83, 155.56, 207.65, 220.0, 293.66, 311.13], dur, env)
    chf = _choir(rng, [587.33, 622.25, 830.61], dur, env, vowel="i", table=V.VF)
    out += _nrm(ch) * 0.6 + _nrm(chf) * 0.3
    sub = dsp.sub_rumble(n, 36.71, rng=rng, am_rate=7.0, am_depth=0.5)
    out += np.vstack([sub, sub]) * env * 0.4
    out = dsp.highpass(out, 25, 2) * (t < cut)[None, :]
    out = _nrm(out) * 0.95
    k = ns(cut)
    out[:, k - ns(0.002):k] *= np.linspace(1, 0, ns(0.002))[None, :]
    return out


# --------------------------------------------------------------------------
# game.wake / game.checkpoint / game.area / game.fog_lift / game.loop
# --------------------------------------------------------------------------
def wake(rng, i=1):
    dur = 4.0
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    tt, gap = 0.15, 0.62
    while tt < dur - 0.4:
        hb = ins.heartbeat(rng, vel=min(1.0, 0.25 + tt / 2.0))
        dsp.place(out, np.vstack([hb, hb]) * 1.2, tt)
        tt += gap
        gap = min(0.9, gap * 1.07)
    ring = (np.sin(TAU * 5300 * t) + 0.3 * np.sin(TAU * 7950 * t + 1)) * dsp.env_pts(n, [(0, 0), (0.6, 1.0), (2.4, 0.4), (dur, 0)])
    out += np.vstack([ring, ring * 0.8]) * 0.04
    amb = np.vstack([dsp.colored(n, rng, -3.0, lo=100, hi=6000) for _ in range(2)])
    fc = 300 + 7000 * np.clip((t - 1.0) / 2.5, 0, 1) ** 2  # hearing comes back
    amb = np.vstack([dsp.tv_filter(amb[c], fc, 0.7, "lp", block=256) for c in range(2)])
    out += amb * 0.03 * np.clip(t / 1.5, 0, 1)
    return dsp.fade(dsp.highpass(out, 25, 2), 0.3, 0.6)


def checkpoint(rng, i=1):
    dur = 3.2
    n = ns(dur)
    out = np.zeros((2, n))
    for t0, nm, p in ((0.05, "D6", -0.3), (0.42, "A5", 0.3), (0.9, "F6", 0.0)):
        tn = ins.tine(dsp.hz(nm, rng.uniform(-12, 8)), rng, vel=0.8)
        dsp.place(out, dsp.pan(tn, p), t0)
    env = dsp.env_pts(n, [(0, 0), (0.8, 1.0), (2.2, 0.7), (dur, 0)])
    pad = _cluster(["D3", "A3", "F4"], dur, rng, env, voices=3, detune=8, vib_depth=5, bright=0.15)
    out += dsp.lowpass(pad, 1500, 2) / (np.max(np.abs(pad)) + 1e-9) * 0.25
    out = dsp.reverb(out, dsp.make_ir(3.0, rng, stereo=True, predelay=0.02), wet=0.45)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 40, 2), n), 0.0, 0.8)


def area(rng, i=1):
    dur = 4.0
    n = ns(dur)
    th = 1.2
    out = np.zeros((2, n))
    src = ins.piano(dsp.hz("D2"), 1.5, rng, vel=0.9) + ins.piano(dsp.hz("D#3"), 1.5, rng, vel=0.6) + ins.piano(dsp.hz("A3"), 1.5, rng, vel=0.6)
    rv = dsp.reverse(dsp.reverb(src, dsp.make_ir(3.0, rng, stereo=True), wet=1.0, dry=0.0))
    dsp.place_end(out, _nrm(rv) * 0.6, th)
    tk = ins.taiko(rng, f0=42, vel=1.0, decay=1.2, dur=2.8, size=1.7)
    sub = _sub_drop(2.0, 60, 30, 0.3, 1.5)
    dsp.place(out, np.vstack([tk, tk]) * 0.8, th)
    dsp.place(out, np.vstack([sub, sub]) * 0.6, th)
    out = dsp.reverb(out, dsp.make_ir(2.8, rng, stereo=True, predelay=0.03), wet=0.35)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 25, 2), n), 0.0, 0.8)


def fog_lift(rng, i=1):
    dur = 5.0
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    nz = rng.standard_normal((2, n))
    fc = dsp.env_pts(n, [(0, 1800), (1.0, 900), (dur, 4000)])
    env = dsp.env_pts(n, [(0, 0), (0.3, 1.0), (1.5, 0.7), (dur, 0)]) ** 1.3
    for c in range(2):
        w = dsp.tv_filter(nz[c], fc, 0.8, "bp")
        out[c] = w / np.std(w) * env * 0.25 * (1 + (0.6 if c else -0.6) * np.clip(t / dur, 0, 1))  # rushing out to the sides
    rin = dsp.mix(ins.rin(rng, 1180.0, 4.5, 1.0), ins.rin(rng, 1771.0, 4.0, 0.5))
    dsp.place(out, np.vstack([rin, np.roll(rin, 220)]) * 0.35, 0.6)
    j = ins.suzu_jingle(rng, 3.5, [(0.0, 0.8), (0.12, 0.5)], n_hits=(4, 7))
    dsp.place(out, dsp.pan(j, 0.2) * 0.3, 0.62)
    out = dsp.reverb(out, dsp.make_ir(3.2, rng, stereo=True, predelay=0.03), wet=0.4)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 40, 2), n), 0.0, 1.0)


def loop(rng, i=1):
    dur = 3.2
    n = ns(dur)
    out = np.zeros((2, n))
    m = ns(1.0)
    tt = np.arange(m) / SR
    wh = dsp.tv_filter(rng.standard_normal(m), 3000 * 2 ** (-tt * 3.0), 1.2, "bp") * np.exp(-tt / 0.3)
    wh = dsp.reverse(wh)  # reverse whoosh: sucked back
    dsp.place(out, np.vstack([wh, np.roll(wh, 400)]) * 0.5 / (np.max(np.abs(wh)) + 1e-9), 0.0)
    env = dsp.env_pts(n, [(0, 0), (0.9, 0.0), (1.8, 1.0), (2.3, 0.6), (dur, 0)])
    st = _cluster(["D4", "D#4", "A4", "G#4"], dur, rng, env, voices=3, detune=25, vib_depth=20, trem=0.3, bright=0.6, pont=0.4)
    out += _nrm(st) * 0.4
    w = V.whisper("o ka e ri", rng, scale=1.07, rate=0.75, close=1.0, bw=1.25)  # 「おかえり」- welcome back
    w = dsp.close_mic(w, 5, 4, 3)
    dsp.place(out, dsp.pan(_nrm(w), -0.9) * 0.4, 1.6)
    out = dsp.reverb(out, dsp.make_ir(2.0, rng, stereo=True, predelay=0.02), wet=0.25)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 40, 2), n), 0.0, 0.5)


# --------------------------------------------------------------------------
# game.car - door, engine struggling, then it catches
# --------------------------------------------------------------------------
def _engine(rng, rpm, dur, roughness=0.1, load=1.0):
    n = ns(dur)
    rpm = np.broadcast_to(np.asarray(rpm, float), (n,))
    fire = rpm / 60.0 * 2.0  # 4-cylinder 4-stroke: 2 firings per revolution
    ph = np.cumsum(fire * (1 + roughness * 0.05 * dsp.smooth(n, rng, 20.0))) / SR
    pulses = (0.5 + 0.5 * np.cos(TAU * ph)) ** 6
    y = pulses * (1 + roughness * dsp.smooth(n, rng, 30.0))
    y = dsp.lowpass(y, 900, 2) + 0.3 * dsp.bandpass(rng.standard_normal(n), 200, 2000, 2) * pulses
    y += 0.08 * np.sin(TAU * np.cumsum(rpm / 60.0 * 7.3) / SR)  # belt / alternator whine
    return y * load


def car(rng, i=1):
    dur = 6.0
    n = ns(dur)
    y = np.zeros(n)
    dsp.place(y, ins.modal_hit(rng, ins.METAL, size=1.2, hardness=0.9, vel=0.4, dur=0.2), 0.05)  # handle
    m = ns(0.4)
    tt = np.arange(m) / SR
    thunk = np.sin(TAU * np.cumsum(90 + 60 * np.exp(-tt / 0.02)) / SR) * dsp.env_ad(m, 0.002, 0.07)
    thunk += dsp.lowpass(rng.standard_normal(m), 800, 2) * dsp.env_ad(m, 0.001, 0.02) * 0.5
    dsp.place(y, thunk, 0.55)  # door shuts
    dsp.place(y, ins.modal_hit(rng, ins.METAL, size=0.9, hardness=1.0, vel=0.5, dur=0.25), 0.56)
    dsp.place(y, ins.modal_hit(rng, ins.KEYS, size=1.0, hardness=1.0, vel=0.3, dur=0.3), 1.0)  # key turns

    def crank(t0, d, fails=True):
        mm = ns(d)
        tt2 = np.arange(mm) / SR
        rpm = 210 + 30 * np.sin(TAU * 1.3 * tt2)
        comp = _engine(rng, rpm, d, roughness=0.5)
        whine = np.sin(TAU * np.cumsum(np.full(mm, 380.0) * (1 + 0.05 * np.sin(TAU * 7 * tt2))) / SR) * 0.25
        gears = dsp.bandpass(rng.standard_normal(mm), 1500, 5000, 2) * 0.12
        env = dsp.env_pts(mm, [(0, 0), (0.05, 1), (d - 0.08, 1), (d, 0)])
        dsp.place(y, (comp * 0.9 + whine + gears) * env, t0)
    crank(1.15, 1.25)
    crank(2.75, 1.0)
    t0 = 3.85
    mm = n - ns(t0)
    tt3 = np.arange(mm) / SR
    rpm = np.interp(tt3, [0, 0.08, 0.35, 0.8, 2.15], [220, 600, 1900, 1100, 820])
    eng = _engine(rng, rpm, mm / SR, roughness=np.interp(tt3, [0, 0.3, 2.0], [0.6, 0.3, 0.12]) * 1.0)
    for k in range(4):  # first misfires / pops
        mpop = ns(0.02)
        dsp.place(eng, rng.standard_normal(mpop) * np.exp(-np.arange(mpop) / (0.003 * SR)) * 0.8, rng.uniform(0.02, 0.25))
    dsp.place(y, eng * dsp.env_pts(mm, [(0, 0), (0.05, 1.0), (mm / SR, 0.9)]), t0)
    y = dsp.reverb(y, dsp.make_ir(1.2, rng, predelay=0.01, lp_hz=5000), wet=0.25)
    y = dsp.pad_to(dsp.highpass(y, 30, 2), n)
    return dsp.fade(np.vstack([y, np.roll(y, ns(0.004))]), 0.0, 0.3)


def title_hit(rng, i=1):
    dur = 5.0
    n = ns(dur)
    out = np.zeros((2, n))
    tk = ins.taiko(rng, f0=40, vel=1.0, decay=1.4, dur=3.5, size=1.9)
    sub = _sub_drop(3.0, 70, 27, 0.35, 1.8)
    dsp.place(out, np.vstack([tk, tk]) * 0.9, 0.02)
    dsp.place(out, np.vstack([sub, sub]) * 0.7, 0.02)
    b = ins.bonsho(73.42, 4.5, rng)
    dsp.place(out, np.vstack([b, np.roll(b, 300)]) * 0.25, 0.03)
    out = dsp.reverb(out, dsp.make_ir(3.5, rng, stereo=True, predelay=0.03), wet=0.4)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 22, 2), n), 0.0, 1.2)


def kamikakushi(rng, i=1):
    dur = 5.2
    n = ns(dur)
    th = 1.5
    out = np.zeros((2, n))
    sw = np.zeros(ns(1.6))
    for k in range(5):
        w = V.whisper(["ka mi ka ku shi", "o ka e ri", "mo u", "ka e sa na i", "ko q chi"][k], rng,
                      scale=rng.uniform(0.95, 1.1), rate=rng.uniform(0.9, 1.2))
        dsp.place(sw, w * rng.uniform(0.5, 1.0), rng.uniform(0, 0.6))
    sw = sw + ins.suzu_jingle(rng, 1.6, [(0.0, 0.8), (0.3, 0.6), (0.6, 0.7)], n_hits=(3, 6)) * 0.3
    rv = dsp.reverse(dsp.reverb(sw, dsp.make_ir(2.8, rng, stereo=True), wet=1.0, dry=0.3))
    dsp.place_end(out, _nrm(rv) * 0.7, th)
    b = ins.bonsho(61.74, dur - th, rng) + 0.6 * ins.bonsho(65.41, dur - th, rng)  # B1 + C2: a minor second of bells
    dsp.place(out, np.vstack([b, np.roll(b, 500)]) * 0.45, th)
    env = dsp.env_pts(n, [(0, 0), (th, 0), (th + 0.05, 1.0), (dur, 0)])
    cl = _choir(rng, [146.83, 155.56, 220.0, 233.08], dur, env * 0.8)
    out += _nrm(cl) * 0.3
    tt = np.arange(n) / SR
    sh = dsp.shepard(n, -0.6, f_lo=60, n_oct=6)
    out += np.vstack([sh, np.roll(sh, 200)]) * 0.04 * np.clip((tt - th) / 0.5, 0, 1)
    sub = _sub_drop(3.0, 80, 28, 0.3, 2.0)
    dsp.place(out, np.vstack([sub, sub]) * 0.6, th)
    out = dsp.reverb(out, dsp.make_ir(4.0, rng, stereo=True, predelay=0.04), wet=0.4)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 25, 2), n), 0.0, 1.5)
