"""game.* - non-diegetic cues (stereo): jump scares, stingers, false risers, being caught, waking at the car, a name
appearing, closing time, the title, a spirit released, dawn, and the 相席 ending."""
import numpy as np

import dsp
import instruments as ins
import jingle as J
import sfx_spirits as SP
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
    if i == 1:  # a woman's shriek with a second throat a tritone below (the waitress, the woman in the mirror)
        s = SP._shriek_core(rng, dur, 1600, chaos=epis, jumps=[(0.5, 5, 0.12), (1.3, -4, None)], biphon_at=0.9,
                            vowels=[(0, "i"), (0.4, "a"), (dur, "a")], rough=(0.7, 70, 0.6))
        out += dsp.pan(_nrm(s) * body, 0.0)
        s2 = SP._shriek_core(rng, dur, 2050, chaos=epis, jumps=[(0.8, -6, None)], biphon_at=0.4,
                             vowels=[(0, "e"), (dur, "i")], rough=(0.6, 85, 0.6), scale=1.2)
        out += _wide(_nrm(s2) * body, rng, 0.02)[:, :n] * 0.35
        cl = ["E5", "F5", "F#5", "A#4", "B4", "C6", "C#6"]
    elif i == 2:  # a man's roar through a burnt throat with a scream inside it (the chef, the manager)
        r, _ = V.scream(dur, rng, [(0, 110), (0.1, 190), (0.8, 240), (1.6, 200), (dur, 130)], [(0, 0), (0.03, 1.0), (dur * 0.7, 0.9), (dur, 0)],
                        vowel_pts=[(0, "o"), (0.3, "a"), (dur, "a")], chaos_pts=[(0, 3.93), (dur, 3.95)], ct=0.4, ca=0.6,
                        breath=0.7, rasp=1.6, rough=(0.75, 48, 0.6), scale=0.85, oq=0.45, closure=0.8)
        out += _wide(_nrm(r) * body, rng)[:, :n] * 0.85
        s, _ = V.scream(dur, rng, [(0, 900), (0.1, 1300), (1.2, 1200), (dur, 700)], [(0, 1.0), (dur * 0.6, 0.9), (dur, 0)],
                        vowel_pts=[(0, "a"), (dur, "e")], chaos_pts=epis, jumps=[(0.7, 4, None)], rough=(0.7, 66, 0.6),
                        rasp=1.0, breath=0.5)
        out += dsp.pan(_nrm(s), 0.25) * 0.8
        cl = ["D3", "D#3", "A3", "G#4", "D5", "D#5"]
    else:  # a child's scream right inside your ear, then the signal tears
        s, _ = V.scream(dur, rng, [(0, 700), (0.04, 1350), (0.3, 1500), (0.7, 1650), (1.2, 1450), (dur, 900)],
                        [(0, 0), (0.012, 1.0), (1.3, 0.9), (dur, 0)], vowel_pts=[(0, "a"), (0.8, "a"), (dur, "i")],
                        chaos_pts=[(0, 3.92), (0.1, 3.92), (0.15, 3.3), (0.6, 3.3), (0.65, 3.9), (dur, 3.94)],
                        jumps=[(0.62, 4, None), (1.25, -6, None)], breath=0.5, rasp=1.0, rough=(0.75, 62, 0.55),
                        scale=1.22, oq=0.38, closure=0.6)
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
def _brass_cluster(freqs, dur, rng, env):
    n = ns(dur)
    out = np.zeros((2, n))
    for f in freqs:
        for v in range(3):
            fv = f * dsp.cents(rng.normal(0, 8)) * dsp.cents(dsp.env_pts(n, [(0, -60), (0.08, 0), (dur, 0)]))
            out += dsp.pan(dsp.saw(fv, n, rng.random()), rng.uniform(-0.6, 0.6))
    bright = 300 + 2600 * env
    out = np.vstack([dsp.tv_filter(out[c], bright, 0.8, "lp", block=128) for c in range(2)])
    out = dsp.am(out, 31.0, 0.15)
    return _nrm(out) * env


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
    elif i == 2:  # a reversed swell that lands on a cluster (and the call button's two notes inside it)
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
        for nm, dt in J.CALL_BUTTON:
            ct = J.call_tone(rng, J.hz(nm, -60), 0.5, 0.25)
            dsp.place(out, dsp.pan(ct, 0.5), th + dt)
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
    else:  # low brass cluster slam + taiko + a cleaver dragged over steel
        dur = 4.0
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (0.5, 0.7), (dur - 0.3, 0), (dur, 0)])
        out = _brass_cluster([36.71, 38.89, 55.0, 51.91, 73.42, 103.83], dur, rng, env)
        tk = ins.taiko(rng, f0=44, vel=1.0, decay=1.0, dur=3.0, size=1.6)
        dsp.place(out, np.vstack([tk, tk]) * 0.9, 0.0)
        sc = SP.chef_scrape(rng, 1)
        dsp.place(out, dsp.pan(_nrm(sc), np.linspace(-0.8, 0.8, len(sc))) * 0.12, 0.1)
    ir = dsp.make_ir(2.4, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.highpass(out, 30, 2)
    out = dsp.limiter(dsp.norm_peak(out, 0.0), -3.0, 0.005)
    return dsp.trim(out, -60)


# --------------------------------------------------------------------------
# game.riser_1..2 - rising tension that cuts to silence
# --------------------------------------------------------------------------
def riser(rng, i=1):
    dur = 5.2 if i == 1 else 4.3
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    prog = np.clip(t / dur, 0, 1)
    rate = 0.2 + 1.4 * prog ** 2  # an accelerating Shepard rise
    ph_oct = np.cumsum(rate) / SR
    sh = np.zeros(n)
    for k in range(7):
        pos = (ph_oct / 7.0 + k / 7.0) % 1.0
        f = 40.0 * 2 ** (7.0 * pos)
        sh += np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * pos) ** 2
    out += _wide(sh, rng)[:, :n] * 0.18 * (0.3 + 0.7 * prog)
    nz = rng.standard_normal(n)
    rs = dsp.tv_filter(nz, 200 * 2 ** (prog * 5.5), 1.6, "bp")
    out += np.vstack([rs, np.roll(rs, 200)]) / np.std(rs) * 0.1 * prog ** 1.5
    cl = ["D4", "D#4", "G#4", "A4", "D5"] if i == 1 else ["C#5", "D5", "G5", "G#5"]
    st = _cluster(cl, dur, rng, prog ** 1.8, voices=3, detune=20, trem=0.6, trem_rate=8, bright=0.7, pont=0.5,
                  gliss=dsp.env_pts(n, [(0, 0), (dur, 150)]))
    out += _nrm(st) * 0.45
    beat = 0.9
    tt = 0.2
    while tt < dur - 0.15:  # the heartbeat accelerating
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
# game.caught - being taken: a rush, a choir-like cluster, cut
# --------------------------------------------------------------------------
def caught(rng, i=1):
    dur = 3.0
    cut = 2.45
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    env = np.clip(t / cut, 0, 1) ** 1.5 * (t < cut)
    rush = dsp.tv_filter(rng.standard_normal(n), 150 * 2 ** (np.clip(t / cut, 0, 1) * 5), 0.9, "bp")
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
# game.wake - coming to beside the car: the ringing fades, the heart settles, the medium's rin
# --------------------------------------------------------------------------
def wake(rng, i=1):
    dur = 6.0
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    tt, gap = 0.15, 0.55
    while tt < 3.6:
        hb = ins.heartbeat(rng, vel=min(1.0, 0.3 + tt / 2.5))
        dsp.place(out, np.vstack([hb, hb]) * 1.1, tt)
        tt += gap
        gap = min(0.95, gap * 1.08)
    ring = (np.sin(TAU * 5300 * t) + 0.3 * np.sin(TAU * 7950 * t + 1)) * dsp.env_pts(n, [(0, 0), (0.6, 1.0), (3.0, 0.3), (4.5, 0)])
    out += np.vstack([ring, ring * 0.8]) * 0.04
    amb = np.vstack([dsp.colored(n, rng, -3.0, lo=100, hi=6000) for _ in range(2)])
    fc = 300 + 7000 * np.clip((t - 1.0) / 2.5, 0, 1) ** 2  # hearing comes back: the wet night
    amb = np.vstack([dsp.tv_filter(amb[c], fc, 0.7, "lp", block=256) for c in range(2)])
    out += amb * 0.03 * np.clip(t / 1.5, 0, 1)
    rin = SP.medium_bell(rng, 1)
    dsp.place(out, dsp.pan(_nrm(rin), 0.45) * 0.3, 3.3)
    return dsp.fade(dsp.highpass(out, 25, 2), 0.3, 0.8)


# --------------------------------------------------------------------------
# game.captured - a spirit's name appears: a soft chime, a low warm swell
# --------------------------------------------------------------------------
def captured(rng, i=1):
    dur = 4.5
    n = ns(dur)
    out = np.zeros((2, n))
    for k, (nm, dt, p) in enumerate((("A5", 0.0, -0.3), ("D6", 0.18, 0.3), ("E6", 0.5, 0.0))):
        tn = ins.vibraphone(J.hz(nm, -8), 3.5, rng, vel=0.6, depth=0.18, hard=0.3)
        dsp.place(out, dsp.pan(tn, p), 0.05 + dt)
    env = dsp.env_pts(n, [(0, 0), (1.2, 1.0), (2.8, 0.7), (dur, 0)])
    pad = _cluster(["D3", "A3", "E4"], dur, rng, env, voices=3, detune=8, vib_depth=5, bright=0.12)
    out += dsp.lowpass(pad, 1400, 2) / (np.max(np.abs(pad)) + 1e-9) * 0.3
    sub = dsp.sub_rumble(n, 36.71, rng=rng, am_rate=5.0, am_depth=0.3) * env
    out += np.vstack([sub, sub]) * 0.12
    out = dsp.reverb(out, dsp.make_ir(3.2, rng, stereo=True, predelay=0.025), wet=0.45)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 30, 2), n), 0.0, 0.8)


# --------------------------------------------------------------------------
# game.closing - closing time begins: the PA chime, every light clunking on, a deep boom
# --------------------------------------------------------------------------
def closing(rng, i=1):
    import sfx_rest as R
    dur = 7.0
    n = ns(dur)
    out = np.zeros((2, n))
    pa = R.pa_chime(rng, 1)
    dsp.place(out, _nrm(pa) * 0.7, 0.0)
    t0 = 2.6
    for k in range(6):  # breaker after breaker, light after light, walking down the hall
        tk = t0 + k * 0.22
        clunk = R._hit(rng, R.STEEL_PANEL, vel=1.0, size=0.9, hard=0.8, dur=0.5, noise=0.5)
        clunk = dsp.mix(clunk, R._thud(rng, 150, 70, 0.05, 0.3, noise=0.8, lp=1500) * 0.8)
        p = -0.8 + 0.32 * k
        dsp.place(out, dsp.pan(_nrm(clunk), p) * (0.6 + 0.08 * k), tk)
        ping = R._hit(rng, R.TUBE_GLASS, vel=0.6, size=1.0, hard=0.9, dur=0.4)
        dsp.place(out, dsp.pan(ping, p) * 0.2, tk + 0.08)
    tt = np.arange(n) / SR
    hum = R._fluor_buzz(n, rng, level=1.0) * np.clip((tt - t0) / 1.2, 0, 1) ** 1.5
    out += np.vstack([hum, np.roll(hum, ns(0.01))]) * 0.05
    boom_t = t0 + 6 * 0.22 + 0.15
    tk = ins.taiko(rng, f0=38, vel=1.0, decay=1.6, dur=3.5, size=2.0)
    sub = _sub_drop(3.0, 70, 26, 0.4, 2.0)
    dsp.place(out, np.vstack([tk, tk]) * 0.8, boom_t)
    dsp.place(out, np.vstack([sub, sub]) * 0.75, boom_t)
    env = dsp.env_pts(n, [(0, 0), (boom_t, 0), (boom_t + 0.05, 1.0), (dur, 0)])
    cl = _cluster(["F2", "F#2", "C3", "C#3"], dur, rng, env, voices=3, detune=10, bright=0.3)
    out += _nrm(cl) * 0.25
    out = dsp.reverb(out, dsp.make_ir(2.6, rng, stereo=True, predelay=0.03), wet=0.35)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 25, 2), n), 0.0, 1.0)


# --------------------------------------------------------------------------
# game.title_hit - the title card: the entrance chime pulled backwards into a deep hit
# --------------------------------------------------------------------------
def title_hit(rng, i=1):
    dur = 6.0
    n = ns(dur)
    out = np.zeros((2, n))
    th = 1.0
    ch = J.door_chime(rng, rate=0.8, cents=-30.0)
    rv = dsp.reverse(dsp.reverb(ch, dsp.make_ir(3.0, rng, stereo=True), wet=1.0, dry=0.3))
    dsp.place_end(out, _nrm(rv) * 0.45, th)
    tk = ins.taiko(rng, f0=40, vel=1.0, decay=1.4, dur=3.5, size=1.9)
    sub = _sub_drop(3.0, 70, 27, 0.35, 1.8)
    dsp.place(out, np.vstack([tk, tk]) * 0.9, th)
    dsp.place(out, np.vstack([sub, sub]) * 0.7, th)
    b = ins.bonsho(73.42, 4.5, rng)
    dsp.place(out, np.vstack([b, np.roll(b, 300)]) * 0.22, th + 0.01)
    out = dsp.reverb(out, dsp.make_ir(3.5, rng, stereo=True, predelay=0.03), wet=0.4)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 22, 2), n), 0.0, 1.2)


# --------------------------------------------------------------------------
# game.release - a spirit sent on at the exorcism: a bright shimmer swelling into light
# --------------------------------------------------------------------------
def release(rng, i=1):
    dur = 5.5
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    env = dsp.env_pts(n, [(0, 0), (1.6, 0.5), (2.6, 1.0), (dur, 0)]) ** 1.3
    sh = np.zeros(n)
    for k in range(10):
        f = J.hz("F5") * 2 ** (k / 5.0) * 2 ** (np.clip(t / 2.6, 0, 1) * 0.5)
        sh += np.sin(TAU * np.cumsum(np.minimum(f, 15000)) / SR + rng.random() * 6) * (1 - np.clip(f / 15000, 0, 1)) * (0.5 + 0.5 * np.sin(TAU * rng.uniform(2, 6) * t) ** 2)
    out += np.vstack([sh, np.roll(sh, ns(0.013))]) / (np.max(np.abs(sh)) + 1e-9) * env * 0.25
    ch = _choir(rng, [J.hz("F4"), J.hz("A4"), J.hz("C5")], dur, env, vowel="a", table=V.VF)
    out += _nrm(ch) * 0.35
    for k, nm in enumerate(("C6", "F6", "A6")):
        dsp.place(out, dsp.pan(ins.tine(J.hz(nm), rng, vel=0.5), -0.4 + 0.4 * k) * 0.3, 2.5 + 0.15 * k)
    out = dsp.reverb(out, dsp.make_ir(4.0, rng, stereo=True, predelay=0.03, hf_ratio=0.5), wet=0.5)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 60, 2), n), 0.2, 1.2)


# --------------------------------------------------------------------------
# game.dawn - a warm swell, the first birds
# --------------------------------------------------------------------------
def dawn(rng, i=1):
    import music as MU
    dur = 7.0
    n = ns(dur)
    out = np.zeros((2, n))
    env = dsp.env_pts(n, [(0, 0), (2.5, 1.0), (5.0, 0.8), (dur, 0)])
    pad = _cluster(["F2", "C3", "A3", "E4", "G4"], dur, rng, env, voices=3, detune=7, vib_depth=5, vib_rate=4.6, bright=0.2)
    out += dsp.lowpass(pad, 2200, 2) / (np.max(np.abs(pad)) + 1e-9) * 0.4
    for k in range(7):
        t0 = 1.5 + k * rng.uniform(0.5, 0.9)
        b = MU._bird_tit(rng) if rng.random() < 0.6 else MU._bird_bulbul(rng)
        dsp.place(out, dsp.pan(dsp.lowpass(b, 7000, 2), rng.uniform(-0.9, 0.9)) * 0.06 * min(1.0, (t0 - 1.0) / 3), t0)
    air = np.vstack([dsp.colored(n, rng, -4.0, lo=150, hi=3000) for _ in range(2)])
    out += air * 0.01 * env[None, :]
    out = dsp.reverb(out, dsp.make_ir(3.0, rng, stereo=True, predelay=0.03, hf_ratio=0.45), wet=0.35)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 30, 2), n), 0.5, 1.5)


# --------------------------------------------------------------------------
# game.ending_bad - 相席: a glass of water set down in front of you; 「いらっしゃいませ。……お一人様ですね」
# --------------------------------------------------------------------------
def ending_bad(rng, i=1):
    import sfx_rest as R
    dur = 7.5
    n = ns(dur)
    out = np.zeros((2, n))
    g = R.glass_set(rng, 1)
    dsp.place(out, dsp.pan(_nrm(g), 0.1) * 0.6, 0.2)
    a = SP._say("i ra q sha i ma se", rng, SP.WAITRESS, [0, 4, (4, 3.5), 3, 2, (0, -2.5)], [0.08, 0.11, 0.12, 0.09, 0.1, 0.3],
                f0=236.0, breath=0.55, vibrato=4.0, wander=0.1)
    b = SP._say("o hi to ri sa ma , de su ne", rng, SP.WAITRESS, [0, 3, 3, 2.5, 2, (1.5, 1), 1, 0.5, (0, -3)],
                [0.08, 0.08, 0.08, 0.11, 0.09, 0.12, 0.09, 0.07, 0.34], f0=228.0, breath=0.6, vibrato=3.0, wander=0.1,
                pauses={6: 0.45})
    v = np.zeros(len(a) + len(b) + ns(1.2))
    dsp.place(v, a, 0.0)
    dsp.place(v, b, len(a) / SR + 0.9)
    v = dsp.close_mic(v, 3.0, 2.5, 2.0)
    dsp.place(out, dsp.pan(_nrm(v), 0.25) * 0.55, 0.9)
    env = dsp.env_pts(n, [(0, 0), (1.0, 0.0), (5.6, 1.0), (6.4, 0.0), (dur, 0)]) ** 1.5
    ch = _choir(rng, [146.83, 155.56, 220.0, 233.08], dur, env * 0.8)
    out += _nrm(ch) * 0.25
    sub = dsp.sub_rumble(n, 36.71, rng=rng, am_rate=6.0, am_depth=0.5) * env
    out += np.vstack([sub, sub]) * 0.18
    out = dsp.reverb(out, dsp.make_ir(1.6, rng, stereo=True, predelay=0.015), wet=0.25)
    return dsp.fade(dsp.pad_to(dsp.highpass(out, 30, 2), n), 0.0, 0.6)
