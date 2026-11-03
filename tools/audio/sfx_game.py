"""game.* - non-diegetic cues (stereo): the two jump scares, stingers, false risers, the soft tick of a finished step,
the clock striking three, the title, the morning.

Built from the game's own materials: the toy music box (its comb struck all at once is the stinger of the night), the
CRT's snow, the doll's rice, a shamisen's slapped string, the clock."""
import numpy as np

import dsp
import instruments as ins
import kit as K
import sfx_doll as D
import tune as T
import voice as V
from dsp import SR, TAU, ns


def _wide(x, rng, amount=0.012):
    return dsp.widen(x, rng, amount)


def _sub_drop(dur=1.2, f_hi=90.0, f_lo=28.0, tau=0.12, drive=2.0):
    m = ns(dur)
    t = np.arange(m) / SR
    s = np.sin(TAU * np.cumsum(f_lo + (f_hi - f_lo) * np.exp(-t / tau)) / SR) * dsp.env_ad(m, 0.002, dur * 0.25)
    return dsp.sat(s * drive, 1.0)


def _cluster(names, dur, rng, env, **kw):
    return ins.strings([dsp.hz(x) for x in names], dur, rng, env=env, **kw)


def _comb_crash(rng, dur=3.0, vel=1.0, cents=0.0):
    """The music box's whole comb struck at once (a fall, a fist): every tooth, detuned, jangling, a few teeth
    buzzing against the cylinder's pins."""
    n = ns(dur)
    out = np.zeros((2, n))
    names = list(T.TOOTH_CENTS.keys())
    for k, nm in enumerate(names):
        f = T.box_hz(nm, 1, cents + rng.uniform(-20, 20))
        tn = ins.tine(f, rng, dur=min(dur, 3.0), vel=vel * rng.uniform(0.5, 1.0))
        K.place(out, dsp.pan(tn, rng.uniform(-0.8, 0.8)), rng.uniform(0.0, 0.025))
    for k in range(6):  # buzzing teeth
        m = ns(0.25)
        f = T.box_hz(names[rng.integers(len(names))], 1)
        bz = np.sign(np.sin(TAU * f * np.arange(m) / SR)) * dsp.env_ad(m, 0.002, 0.06) * 0.15
        K.place(out, dsp.pan(dsp.lowpass(bz, 8000, 2), rng.uniform(-0.6, 0.6)), rng.uniform(0.0, 0.2))
    return out


def _shamisen(rng, f0, dur=2.0, bend=-80.0, vel=1.0):
    """A slapped shamisen string: the bachi hitting skin and string together (the 'sawari' buzz), a pitch bend down."""
    n = ns(dur)
    t = np.arange(n) / SR
    s = ins.ks_string(f0, dur, rng, t60=1.4, bright=0.85, pluck_pos=0.12, exc_soft=0.1)
    s = K.nrm(s)
    s = dsp.varispeed(s, dsp.cents(bend * (1 - np.exp(-t / 0.4))))
    s = dsp.pad_to(s, n)
    saw = np.tanh(3.0 * s) * 0.5 + s * 0.5  # sawari buzz
    skin = dsp.bandpass(rng.standard_normal(ns(0.04)), 300, 5000, 2) * dsp.env_ad(ns(0.04), 0.0005, 0.008)
    y = saw
    y[:len(skin)] += skin * 2.0
    body = dsp.resbank(y, [(240, 4, 1.0), (520, 5, 0.6), (1100, 6, 0.3)])
    return (y + body * 0.3) * vel


# --------------------------------------------------------------------------
# game.jumpscare_1..2 - maximised to -1 dBTP
# --------------------------------------------------------------------------
def jumpscare(rng, i=1):
    if i == 1:  # the doll springs at your face: the fusuma ripped aside, its shriek, the knife, a cluster slam
        dur = 2.3
        n = ns(dur)
        out = np.zeros((2, n))
        body = dsp.env_pts(n, [(0, 1.0), (dur * 0.45, 0.95), (dur - 0.15, 0.15), (dur, 0.0)])
        sl = np.zeros(ns(0.5))  # the fusuma torn open
        K.place(sl, K.nrm(K.fibres_tear(0.25, rng, rate=12000, bright=1.0)) * 0.7, 0.0)
        K.place(sl, K.nrm(ins.modal_hit(rng, [(240, 0.08, 1.0), (430, 0.06, 0.8), (760, 0.05, 0.6), (1300, 0.03, 0.3)],
                                        hardness=0.9, vel=1.0, dur=0.4, noise=0.5)), 0.02)
        K.place(out, np.vstack([sl, sl]), 0.0)
        chaos = [(0, 3.93), (0.14, 3.93), (0.18, 3.3), (0.5, 3.3), (0.54, 3.95), (1.3, 3.92), (1.34, 3.5), (1.6, 3.5), (1.64, 3.95), (dur, 3.95)]
        s, _ = V.scream(dur, rng, [(0, 1100), (0.03, 1800), (0.5, 1950), (1.2, 1750), (dur, 1100)],
                        [(0, 0), (0.006, 1.0), (1.5, 0.9), (dur, 0)], vowel_pts=[(0, "i"), (0.5, "a"), (dur, "a")],
                        chaos_pts=chaos, jumps=[(0.5, 5, 0.12), (1.3, -4, None)], breath=0.5, rasp=1.1,
                        rough=(0.75, 70, 0.6), scale=1.24, oq=0.38, closure=0.6,
                        biphon=dict(ratio=[(0, 0.5), (dur, 0.47)], amp=[(0, 0.4), (dur, 0.5)]))
        s = dsp.pad_to(K.nrm(s), n) * body
        out += np.vstack([s, s * 0.85])
        cl = ["E5", "F5", "F#5", "A#4", "B4", "C6", "C#6"]
    else:  # the woman's face filling the camera feed: her shriek torn by the codec, the snow screaming with her
        dur = 2.4
        n = ns(dur)
        out = np.zeros((2, n))
        body = dsp.env_pts(n, [(0, 1.0), (dur * 0.5, 0.9), (dur - 0.15, 0.1), (dur, 0.0)])
        epis = [(0, 3.9), (0.2, 3.9), (0.25, 3.4), (0.7, 3.4), (0.75, 3.95), (dur, 3.95)]
        s, _ = V.scream(dur, rng, [(0, 700), (0.05, 1300), (0.6, 1450), (1.4, 1200), (dur, 600)],
                        [(0, 0), (0.01, 1.0), (1.6, 0.85), (dur, 0)], vowel_pts=[(0, "a"), (0.9, "e"), (dur, "a")],
                        chaos_pts=epis, jumps=[(0.72, -5, None)], breath=0.6, rasp=1.3, rough=(0.7, 58, 0.6),
                        scale=1.02, oq=0.42, closure=0.7)
        s = dsp.pad_to(K.nrm(s), n) * body
        crushed = dsp.crush(s, 5, 3)  # the feed's codec breaking up
        gate = (np.sin(TAU * 11.0 * np.arange(n) / SR) > -0.2).astype(float)
        gate = dsp.lowpass0(gate, 200)
        s = s * (0.6 + 0.4 * gate) + crushed * 0.35 * (1 - gate)
        out += np.vstack([s, np.roll(s, ns(0.0008))])
        st = dsp.colored(n, rng, 0.0, lo=200, hi=12000) * body
        out += np.vstack([st, st]) / np.std(st) * 0.12
        cl = ["D#5", "E5", "A4", "A#4", "D6", "D#6"]
    st = _cluster(cl, dur, rng, body, voices=3, detune=28, vib_depth=35, trem=0.55, trem_rate=17, bright=0.95, pont=0.8)
    out += st / (np.max(np.abs(st)) + 1e-9) * 0.3
    nz = np.vstack([dsp.bandpass(rng.standard_normal(n), 400, 9000, 2) for _ in range(2)])
    out += dsp.sat(nz * 2.5, 1.0) * dsp.env_pts(n, [(0, 1.0), (0.12, 0.35), (0.3, 0.0), (dur, 0)]) * 0.45
    m = ns(0.05)
    tr = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.008 * SR))
    K.place(out, np.vstack([tr, tr]) * 1.2, 0)
    cc = _comb_crash(rng, 1.6, 1.0, cents=-40)
    K.place(out, cc / (np.max(np.abs(cc)) + 1e-9) * 0.25, 0.0)
    out = dsp.highpass(out, 120, 2)
    out = K.nrm(out)
    sub = _sub_drop(0.9, 95, 32, 0.07, 2.4)
    K.place(out, np.vstack([sub, sub]) * 0.28, 0)
    out = dsp.highpass(out, 30, 2)
    return K.nrm(out)


# --------------------------------------------------------------------------
# game.stinger_1..4
# --------------------------------------------------------------------------
def stinger(rng, i=1):
    if i == 1:  # a string cluster stab over a dead, heavy body blow
        dur = 3.6
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (0.6, 0.75), (dur - 0.2, 0.0), (dur, 0)])
        st = _cluster(["G#4", "A4", "A#4", "D5", "D#5", "G#5", "A5"], dur, rng, env, voices=3, detune=18, vib_depth=15,
                      trem=0.55, trem_rate=15, bright=0.8, pont=0.6)
        lo = _cluster(["A1", "A#1", "E2"], dur, rng, env, voices=3, detune=12, bright=0.4)
        out = K.nrm(st) + 0.6 * K.nrm(lo)
        m = ns(0.5)
        tt = np.arange(m) / SR
        blow = np.sin(TAU * np.cumsum(55 + 60 * np.exp(-tt / 0.02)) / SR) * dsp.env_ad(m, 0.002, 0.09)
        blow = np.tanh(2.5 * blow) + dsp.lowpass(rng.standard_normal(m), 600, 2) * dsp.env_ad(m, 0.001, 0.02) * 0.6
        K.place(out, np.vstack([blow, blow]) * 0.8, 0.0)
    elif i == 2:  # a reversed swell sucked into the music box's comb struck all at once
        dur = 4.4
        n = ns(dur)
        th = 1.3
        src = _comb_crash(rng, 1.2, 0.8)
        rev = dsp.reverse(dsp.reverb(src, dsp.make_ir(2.5, rng, stereo=True), wet=1.0, dry=0.2))
        out = np.zeros((2, n))
        dsp.place_end(out, K.nrm(rev) * 0.8, th)
        cc = _comb_crash(rng, dur - th, 1.0, cents=-25)
        K.place(out, K.nrm(cc), th)
        env = dsp.env_pts(n, [(0, 0), (th, 0), (th + 0.04, 1.0), (th + 0.8, 0.5), (dur - 0.2, 0), (dur, 0)])
        lo = _cluster(["A2", "A#2", "D#3"], dur, rng, env, voices=3, detune=20, bright=0.4)
        out += K.nrm(lo) * 0.5
    elif i == 3:  # a glassy high cluster gliding in, the floor dropping out under it, snow hissing
        dur = 3.4
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.55, 1.0), (1.2, 0.8), (dur - 0.2, 0), (dur, 0)]) ** 1.3
        gl = dsp.env_pts(n, [(0, -500), (0.6, 0), (dur, 60)])
        st = ins.strings([dsp.hz(x) for x in ["E6", "F6", "A#5", "B5", "F#6"]], dur, rng, env=env, voices=3, detune=30,
                         vib_depth=25, trem=0.6, trem_rate=18, bright=1.0, pont=0.9, gliss=gl)
        out = K.nrm(st)
        sn = K.tv_speaker(dsp.colored(n, rng, 0.0, lo=100, hi=12000))
        out += np.vstack([sn, sn]) / np.std(sn) * env * 0.08
        sub = _sub_drop(1.6, 68, 28, 0.2, 1.6)
        K.place(out, np.vstack([sub, sub]) * 0.9, 0.58)
    else:  # a shamisen string slapped and bent down, a low cluster, the doll's rice spilling
        dur = 4.0
        n = ns(dur)
        out = np.zeros((2, n))
        sh = _shamisen(rng, dsp.hz("A3", -20), 2.5, bend=-120)
        K.place(out, dsp.pan(K.nrm(sh), -0.2), 0.0)
        sh2 = _shamisen(rng, dsp.hz("A#3", 10), 2.5, bend=-90, vel=0.7)
        K.place(out, dsp.pan(K.nrm(sh2), 0.3) * 0.6, 0.012)
        env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (0.5, 0.6), (dur - 0.3, 0), (dur, 0)])
        lo = _cluster(["A1", "A#1", "E2", "D#3"], dur, rng, env, voices=3, detune=16, bright=0.35)
        out += K.nrm(lo) * 0.5
        rc = K.rice_pour(1.5, rng, 3000 * K.env_shape(ns(1.5), [(0, 0), (0.05, 1.0), (1.5, 0)], 2.0) + 1, into="wood")
        K.place(out, dsp.pan(K.nrm(rc), 0.5) * 0.15, 0.1)
    ir = dsp.make_ir(2.4, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.highpass(out, 30, 2)
    out = dsp.limiter(dsp.norm_peak(out, 0.0), -3.0, 0.005)
    return dsp.trim(out, -60)


# --------------------------------------------------------------------------
# game.riser_1..2 - rising tension that cuts to silence
# --------------------------------------------------------------------------
def riser(rng, i=1):
    dur = 5.2 if i == 1 else 4.6
    n = ns(dur)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    prog = np.clip(t / dur, 0, 1)
    if i == 1:  # an accelerating Shepard rise, strings tightening, the heartbeat racing
        rate = 0.2 + 1.4 * prog ** 2
        ph_oct = np.cumsum(rate) / SR
        sh = np.zeros(n)
        for k in range(7):
            pos = (ph_oct / 7.0 + k / 7.0) % 1.0
            f = 40.0 * 2 ** (7.0 * pos)
            sh += np.sin(TAU * np.cumsum(f) / SR) * np.sin(np.pi * pos) ** 2
        out += _wide(sh, rng)[:, :n] * 0.18 * (0.3 + 0.7 * prog)
        st = _cluster(["A3", "A#3", "D#4", "E4", "A4"], dur, rng, prog ** 1.8, voices=3, detune=20, trem=0.6, trem_rate=8,
                      bright=0.7, pont=0.5, gliss=dsp.env_pts(n, [(0, 0), (dur, 150)]))
        out += K.nrm(st) * 0.45
        beat, tt = 0.9, 0.2
        while tt < dur - 0.15:
            hb = ins.heartbeat(rng, vel=0.5 + 0.5 * tt / dur)
            K.place(out, np.vstack([hb, hb]) * 0.9, tt)
            tt += beat
            beat = max(0.28, beat * 0.88)
    else:  # the snow rising, sweeping upward, a child's whisper riding it: 「みつけた」 - cut
        sn = dsp.colored(n, rng, 0.0, lo=80, hi=14000)
        sn = dsp.tv_filter(sn, 300 * 2 ** (prog * 5.0), 0.9, "lp", block=128)
        out += np.vstack([sn, np.roll(sn, 150)]) / np.std(sn) * 0.14 * prog ** 1.4
        rs = dsp.tv_filter(rng.standard_normal(n), 200 * 2 ** (prog * 5.5), 1.6, "bp")
        out += np.vstack([rs, np.roll(rs, 200)]) / np.std(rs) * 0.1 * prog ** 1.5
        st = _cluster(["D#5", "E5", "A5", "A#5"], dur, rng, prog ** 1.8, voices=3, detune=20, trem=0.6, trem_rate=8,
                      bright=0.7, pont=0.5, gliss=dsp.env_pts(n, [(0, 0), (dur, 150)]))
        out += K.nrm(st) * 0.35
        w = V.whisper_line("mi tsu ke ta", rng, [0.14, 0.1, 0.12, 0.3], table=V.VF, scale=1.22, close=1.0, voicing=0.15, f0=330)
        K.place(out, dsp.pan(K.nrm(w), -0.85) * 0.3, dur - 1.3)
    out *= (0.15 + 0.85 * prog ** 1.4)[None, :]
    out = dsp.highpass(out, 30, 2)
    out = K.nrm(out) * 0.9
    out[:, -ns(0.003):] *= np.linspace(1, 0, ns(0.003))[None, :]
    return out


# --------------------------------------------------------------------------
def step_done(rng, i=1):
    """A ritual step is done: one soft tooth of the music box, a pencil ticking it off the printout."""
    y = np.zeros(ns(1.6))
    K.place(y, ins.tine(T.box_hz("D5", 1), rng, dur=1.5, vel=0.5), 0.03)
    m = ns(0.07)
    tick = dsp.bandpass(rng.standard_normal(m), 2000, 8000, 2) * K.env_shape(m, [(0, 0), (0.01, 1.0), (0.04, 0.5), (0.07, 0)])
    K.place(y, K.rmsn(tick) * 0.03, 0.0)
    out = dsp.reverb(np.vstack([y, np.roll(y, ns(0.004))]), dsp.make_ir(1.4, rng, stereo=True, predelay=0.02), wet=0.3)
    return dsp.trim(out, -60)


def _clock_gong(rng, f0, dur, vel=1.0):
    """The coiled wire gong of an old pendulum clock: inharmonic, deep, long, the hammer's soft thud."""
    y = ins.bell_partials(f0, dur, rng, [1.0, 2.04, 2.97, 4.07, 5.33, 6.8], [1.0, 0.65, 0.45, 0.3, 0.18, 0.1],
                          [6.0, 4.2, 3.0, 2.0, 1.3, 0.8], beat=(0.4, 1.8), attack=0.003)
    m = ns(0.05)
    y[:m] += dsp.lowpass(rng.standard_normal(m), 900, 2) * dsp.env_ad(m, 0.001, 0.008) * 0.6
    return y * vel


def three_am(rng, i=1):
    """Three o'clock: an old clock somewhere strikes three, deep and slow, the last stroke left to die away."""
    dur = 10.0
    n = ns(dur)
    out = np.zeros((2, n))
    for k, t0 in enumerate((0.05, 2.25, 4.45)):
        g = _clock_gong(rng, 172.0, dur - t0, vel=1.0 - 0.05 * k)
        K.place(out, np.vstack([g, np.roll(g, ns(0.0012))]), t0)
    sub = np.sin(TAU * 43.0 * np.arange(n) / SR) * dsp.env_pts(n, [(0, 0), (0.1, 0.4), (6.0, 0.25), (dur, 0)])
    out += np.vstack([sub, sub]) * 0.05
    ir = dsp.make_ir(3.0, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.35, dry=0.9), n)
    return dsp.fade(dsp.highpass(out, 30, 2), 0.0, 2.0)


def title_hit(rng, i=1):
    """The title card: the snow swelling backwards into a hit - the music box's comb struck, a low blow, a sub."""
    dur = 6.0
    n = ns(dur)
    out = np.zeros((2, n))
    th = 1.2
    sn = dsp.colored(ns(1.4), rng, 0.0, lo=100, hi=12000)
    sn = K.tv_speaker(sn) * K.env_shape(ns(1.4), [(0, 1.0), (1.4, 0)], 2.0)
    rev = dsp.reverse(dsp.reverb(np.vstack([sn, sn]), dsp.make_ir(2.0, rng, stereo=True), wet=1.0, dry=0.3))
    dsp.place_end(out, K.nrm(rev) * 0.5, th)
    cc = _comb_crash(rng, dur - th, 1.0, cents=-30)
    K.place(out, K.nrm(cc) * 0.9, th)
    sub = _sub_drop(2.5, 70, 30, 0.25, 1.8)
    K.place(out, np.vstack([sub, sub]) * 0.6, th)
    m = ns(0.4)
    tt = np.arange(m) / SR
    blow = np.tanh(2.0 * np.sin(TAU * np.cumsum(60 + 50 * np.exp(-tt / 0.02)) / SR) * dsp.env_ad(m, 0.002, 0.08))
    K.place(out, np.vstack([blow, blow]) * 0.5, th)
    ir = dsp.make_ir(3.5, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.4, dry=0.85), n)
    return dsp.fade(dsp.highpass(out, 28, 2), 0.0, 1.0)


def _sparrow(rng):
    """A tree sparrow's 'chun': a short, slightly buzzy chirp."""
    m = ns(rng.uniform(0.06, 0.1))
    tt = np.arange(m) / SR
    d = m / SR
    f = np.interp(tt, [0, d * 0.3, d], [rng.uniform(3600, 4300), rng.uniform(4800, 5600), rng.uniform(3300, 4000)])
    y = np.sin(TAU * np.cumsum(f) / SR) * (1 + 0.3 * np.sin(TAU * 140 * tt)) * np.sin(np.pi * tt / d) ** 1.5
    return y


def morning(rng, i=1):
    """It is morning: the light comes up as a slow warm chord, sparrows outside the window, the rain has stopped."""
    dur = 7.0
    n = ns(dur)
    out = np.zeros((2, n))
    env = dsp.env_pts(n, [(0, 0), (2.0, 0.8), (4.0, 1.0), (dur, 0)])
    pad = ins.strings([dsp.hz(x) for x in ["C3", "G3", "E4", "A4", "D5"]], dur, rng, env=env, voices=3, detune=6,
                      vib_depth=4, vib_rate=4.6, bright=0.25)
    out += K.nrm(pad) * 0.45
    for k in range(14):
        t0 = rng.uniform(0.8, dur - 0.5)
        ch = np.zeros(ns(0.5))
        for j in range(rng.integers(1, 4)):
            K.place(ch, _sparrow(rng), j * rng.uniform(0.09, 0.14))
        g = 0.12 * min(1.0, t0 / 2.5)
        K.place(out, dsp.pan(dsp.lowpass(ch, 8000, 2), rng.uniform(-0.9, 0.9)) * g, t0)
    ir = dsp.make_ir(2.5, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.pad_to(dsp.reverb(out, ir, wet=0.35, dry=0.85), n)
    return dsp.fade(dsp.highpass(out, 40, 2), 0.3, 1.5)
