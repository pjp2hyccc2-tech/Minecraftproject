"""game.* sounds: UI cues, scares, interactions."""
import numpy as np

import dsp
import instruments as ins
import voice as V
import sfx_bride
from dsp import SR, ns


# --------------------------------------------------------------------------
# game.bell_chime_1..3 (M) - suzu jingle
# --------------------------------------------------------------------------
def bell_chime(rng, i=1):
    dur = 2.4
    if i == 1:
        shakes, nh = [(0.02, 1.0), (0.17, 0.75)], (4, 8)
    elif i == 2:
        shakes, nh = [(0.02, 1.0)], (2, 3)
    else:
        shakes, nh = [(0.02, 1.0), (0.14, 0.8), (0.28, 0.9), (0.42, 0.65), (0.57, 0.45)], (3, 7)
    y = ins.suzu_jingle(rng, dur, shakes, n_hits=nh)
    ir = dsp.make_ir(0.7, rng, predelay=0.004)
    y = dsp.reverb(y, ir, wet=0.25)
    return dsp.trim(dsp.highpass(y, 300, 2), -60)


# --------------------------------------------------------------------------
# game.bell_get (S, 4 s) - jingle + sacred swell + reverse shimmer
# --------------------------------------------------------------------------
def bell_get(rng, i=1):
    dur = 4.0
    n = ns(dur)
    out = np.zeros((2, n))
    for p, t0 in [(-0.5, 0.0), (0.5, 0.025)]:
        j = ins.suzu_jingle(rng, 2.5, [(0.0, 1.0), (0.12, 0.6)], n_hits=(4, 7))
        dsp.place(out, dsp.pan(j, p), t0)
    chord = [dsp.hz(x) for x in ["A2", "E3", "A3", "B3", "E4"]]
    sw = ins.sho([(0.0, dur, chord, 1.0)], dur, rng)
    env = dsp.env_pts(n, [(0, 0), (1.3, 1.0), (2.6, 0.7), (4.0, 0.0)]) ** 1.2
    sw = dsp.lowpass(sw, 2500, 2) * env
    out += sw / (np.max(np.abs(sw)) + 1e-9) * 0.6
    sub = np.sin(dsp.TAU * 55 * np.arange(n) / SR) * env * 0.3
    out += sub
    # reverse shimmer rising into a soft second chime at 2.2 s
    sh = ins.suzu_jingle(rng, 1.0, [(0.0, 0.8)], n_hits=(3, 5))
    sh = dsp.reverb(sh, dsp.make_ir(2.2, rng, stereo=True), wet=1.0, dry=0.0)
    sh = dsp.reverse(sh)
    sh = sh / (np.max(np.abs(sh)) + 1e-9) * 0.5
    dsp.place_end(out, sh, 2.2)
    ring = ins.suzu_strike(rng, 4700, 0.6, dur=1.8)
    dsp.place(out, dsp.pan(ring, 0.1), 2.2)
    ir = dsp.make_ir(2.8, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.35)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 40, 2), 0.0, 0.6)


# --------------------------------------------------------------------------
# game.jumpscare_1..2 (S) - brutally loud
# --------------------------------------------------------------------------
def jumpscare(rng, i=1):
    dur = 2.3 if i == 1 else 1.9
    n = ns(dur)
    tt = np.arange(n) / SR
    out = np.zeros((2, n))
    body = dsp.env_pts(n, [(0, 1.0), (0.9 if i == 1 else 0.75, 0.95), (dur - 0.15, 0.12), (dur, 0.0)])
    # scream layers (wide)
    if i == 1:
        layers = [([(0, 700), (0.25, 1150), (1.0, 1250), (dur, 700)], -0.6, 1.0),
                  ([(0, 930), (0.2, 1480), (1.0, 1390), (dur, 900)], 0.6, 0.85),
                  ([(0, 520), (0.3, 640), (1.0, 600), (dur, 380)], 0.0, 0.7)]
    else:
        layers = [([(0, 1250), (0.12, 1750), (0.9, 1650), (dur, 1100)], -0.5, 1.0),
                  ([(0, 1600), (0.15, 2050), (0.9, 1900), (dur, 1300)], 0.5, 0.8),
                  ([(0, 780), (0.2, 900), (1.0, 820), (dur, 600)], 0.0, 0.5)]
    for pts, p, g in layers:
        y, _ = sfx_bride._scream_layer(n, pts, body, rng, sub=0.5, flutter=0.04, breaks=2, breath=0.4)
        car = np.sin(dsp.TAU * np.cumsum(rng.uniform(90, 160) * (1 + 0.2 * dsp.smooth(n, rng, 5))) / SR)
        y = y * 0.7 + 0.5 * y * car
        y = dsp.sat(y / (np.max(np.abs(y)) + 1e-9) * 3.0, 1.0)
        out += dsp.pan(y, p) * g
    # string cluster stab, sul ponticello tremolo
    cl = ["E5", "F5", "F#5", "A#4", "B4", "C6", "C#6"] if i == 1 else ["G5", "G#5", "A5", "D#6", "E6", "C#5"]
    st = ins.strings([dsp.hz(x) for x in cl], dur, rng, env=body, voices=3, detune=25, vib_depth=30, trem=0.5,
                     trem_rate=16, bright=0.9, pont=0.7)
    out += st / (np.max(np.abs(st)) + 1e-9) * 0.7
    # distorted noise burst
    nz = np.vstack([dsp.bandpass(rng.standard_normal(n), 400, 7000, 2) for _ in range(2)])
    out += dsp.sat(nz * 2, 1.0) * dsp.env_pts(n, [(0, 1.0), (0.25, 0.25), (1.0, 0.06), (dur, 0)]) * 0.35
    # impact transient
    m = ns(0.05)
    tr = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.008 * SR))
    dsp.place(out, np.vstack([tr, tr]) * 1.5, 0)
    if i == 2:  # clatter + stutter glitch
        for k in range(16):
            c = ins.wood_hit(rng, ins.CERAMIC, size=rng.uniform(0.8, 1.3), hardness=0.95, vel=rng.uniform(0.4, 1.0), dur=0.1)
            dsp.place(out, dsp.pan(c, rng.uniform(-0.8, 0.8)), rng.uniform(0, 0.8))
        seg = out[:, ns(0.05): ns(0.08)].copy()
        for k in range(5):
            dsp.place(out, seg * 0.6, 0.9 + k * 0.032)
    out = dsp.highpass(out, 110, 2)
    out = out / (np.max(np.abs(out)) + 1e-9)
    # sub hit: short punch (with harmonics so small speakers carry it)
    m = ns(0.9)
    t = np.arange(m) / SR
    f = 45 + 50 * np.exp(-t / 0.08)
    sub = dsp.sat(np.sin(dsp.TAU * np.cumsum(f) / SR) * dsp.env_ad(m, 0.002, 0.16) * 2.0, 1.0)
    dsp.place(out, np.vstack([sub, sub]) * 0.4, 0)
    out = dsp.highpass(out, 25, 2)
    # final density stage (maximize to RMS -8 dBFS, true-peak checked after
    # encoding) happens in gen_sounds.py, norm=('dense', -8, -0.5)
    return out / (np.max(np.abs(out)) + 1e-9)



# --------------------------------------------------------------------------
# game.stinger_1..3 (S) - dissonant cluster swells that slam in
# --------------------------------------------------------------------------
def stinger(rng, i=1):
    if i == 1:
        dur = 3.6
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.07, 1.0), (0.6, 0.75), (dur - 0.2, 0.0), (dur, 0)])
        st = ins.strings([dsp.hz(x) for x in ["C#5", "D5", "D#5", "G5", "G#5", "C6", "C#6"]], dur, rng, env=env, voices=3,
                         detune=18, vib_depth=15, trem=0.55, trem_rate=15, bright=0.8, pont=0.6)
        lo = ins.strings([dsp.hz(x) for x in ["D2", "D#2", "A2"]], dur, rng, env=env, voices=3, detune=12, bright=0.4)
        out = st / np.max(np.abs(st)) + 0.6 * lo / np.max(np.abs(lo))
        boom = ins.taiko(rng, f0=48, vel=1.0, decay=0.8, dur=2.5)
        dsp.place(out, np.vstack([boom, boom]) * 0.8, 0.0)
    elif i == 2:
        dur = 4.4
        n = ns(dur)
        th = 1.3
        cl = [dsp.hz(x) for x in ["C3", "C#3", "G3", "G#3", "D4", "D#4", "A4"]]
        rev_src = ins.strings(cl, 1.0, rng, env=dsp.env_pts(ns(1.0), [(0, 1), (1.0, 0)]), voices=2, bright=0.7)
        rev = dsp.reverse(dsp.reverb(rev_src, dsp.make_ir(2.5, rng, stereo=True), wet=1.0, dry=0.2))
        rev = rev / np.max(np.abs(rev)) * 0.8
        out = np.zeros((2, n))
        dsp.place_end(out, rev, th)
        env = dsp.env_pts(n, [(0, 0), (th, 0), (th + 0.04, 1.0), (th + 0.8, 0.6), (dur - 0.2, 0), (dur, 0)])
        hit = ins.strings(cl + [dsp.hz("C2"), dsp.hz("C#2")], dur, rng, env=env, voices=3, detune=20, trem=0.3, bright=0.6, pont=0.3)
        out += hit / np.max(np.abs(hit))
        tk = ins.taiko(rng, f0=52, vel=1.0, decay=0.7, dur=2.0)
        dsp.place(out, np.vstack([tk, tk]) * 0.9, th)
    else:
        dur = 3.4
        n = ns(dur)
        env = dsp.env_pts(n, [(0, 0), (0.55, 1.0), (1.2, 0.8), (dur - 0.2, 0), (dur, 0)]) ** 1.3
        gl = dsp.env_pts(n, [(0, -500), (0.6, 0), (dur, 60)])
        st = ins.strings([dsp.hz(x) for x in ["E6", "F6", "A#5", "B5", "F#6"]], dur, rng, env=env, voices=3, detune=30,
                         vib_depth=25, trem=0.6, trem_rate=18, bright=1.0, pont=0.9, gliss=gl)
        out = st / np.max(np.abs(st))
        wh = dsp.tv_filter(rng.standard_normal(n), dsp.env_pts(n, [(0, 300), (0.6, 3000), (dur, 500)]), 1.5, "bp")
        out += np.vstack([wh, wh]) / np.std(wh) * env * 0.12
        m = ns(1.6)
        t = np.arange(m) / SR
        sub = np.sin(dsp.TAU * np.cumsum(28 + 40 * np.exp(-t / 0.2)) / SR) * dsp.env_ad(m, 0.003, 0.5)
        dsp.place(out, np.vstack([sub, sub]) * 0.9, 0.58)
    ir = dsp.make_ir(2.4, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.highpass(out, 30, 2)
    out = dsp.limiter(dsp.norm_peak(out, 0.0), -3.0, 0.005)
    return dsp.trim(out, -60)


# --------------------------------------------------------------------------
# game.chase (S) - 16 s seamless loop at 135 BPM (36 beats)
# --------------------------------------------------------------------------
def chase(rng, i=1):
    L = 16.0
    n = ns(L)
    beat = L / 36.0
    s16 = beat / 4.0
    out = np.zeros((2, n))
    low_pat = "x.....x.x......."
    mid_pat = "..x.x..x..x.x.xx"
    mid_pat2 = "x.xxx.xxx.xxx.xx"
    rim_pat = ".x.x.x.x.x.x.x.x"
    big = [ins.taiko(rng, f0=54, vel=1.0, decay=0.5, dur=1.2, size=1.3) for _ in range(3)]
    mid = [ins.taiko(rng, f0=92, vel=0.8, decay=0.25, dur=0.6, size=0.8, stick=0.8) for _ in range(4)]
    for bar in range(9):
        b0 = bar * 4 * beat
        if bar == 8:  # fill: 16th roll crescendo on mid drums, big hits on 1 and 3
            for k in range(16):
                v = 0.4 + 0.6 * k / 15
                dsp.place(out, dsp.pan(mid[k % 4], -0.3 + 0.6 * (k % 2)) * v, b0 + k * s16, circular=True)
            for k in (0, 8, 14):
                dsp.place(out, dsp.pan(big[k % 3], 0.0) * 0.9, b0 + k * s16, circular=True)
            continue
        dense = bar >= 4
        for k in range(16):
            t = b0 + k * s16 + rng.normal(0, 0.003)
            if low_pat[k] == "x":
                dsp.place(out, dsp.pan(big[rng.integers(3)], rng.uniform(-0.15, 0.15)) * (1.0 if k == 0 else 0.8), t, circular=True)
            mp = mid_pat2 if dense else mid_pat
            if mp[k] == "x":
                dsp.place(out, dsp.pan(mid[rng.integers(4)], rng.uniform(-0.5, 0.5)) * rng.uniform(0.5, 0.85), t, circular=True)
            if rim_pat[k] == "x":
                rim = ins.kakko_hit(rng, 0.18 if dense else 0.12)
                dsp.place(out, dsp.pan(rim, 0.6 if k % 4 == 1 else -0.6), t, circular=True)
    drums = out.copy()
    # pulsing bass: D/Eb alternation, 8th-note pumping
    tt = np.arange(n) / SR
    ph8 = (tt / (beat / 2)) % 1.0
    pump = np.exp(-ph8 / 0.35)
    bass_f = np.where(tt < 16 * beat, dsp.hz("D2"), np.where(tt < 32 * beat, dsp.hz("Eb2"), dsp.hz("D2") * dsp.cents((tt - 32 * beat) / (4 * beat) * 300)))
    bass_f = dsp.lowpass0(bass_f, 30)
    bass = dsp.saw(bass_f, n, loop=True) + 0.6 * dsp.saw(bass_f * 1.003, n, 0.3, loop=True)
    bass = dsp.circ(lambda x: dsp.lowpass(x, 420, 3), bass) * pump
    # tremolo dissonant strings (rate locked to 16ths -> periodic)
    trem_rate = 1.0 / s16
    def trem_voice(f, depth=0.6):
        fv = f * dsp.cents(rng.normal(0, 9) + 6 * dsp.smooth(n, rng, 0.5))
        s = dsp.saw(fv, n, rng.random(), loop=True)
        ph = trem_rate * tt + rng.random()
        return s * ((1 - depth) + depth * (0.5 + 0.5 * np.cos(dsp.TAU * ph)) ** 1.5)
    strings = np.zeros((2, n))
    for nm in ["D4", "Eb4", "Ab4", "D5", "Eb5"]:
        for v in range(3):
            strings += dsp.pan(trem_voice(dsp.hz(nm)), rng.uniform(-0.8, 0.8))
    hi_env = np.clip((tt - 16 * beat) / (16 * beat), 0, 1) ** 1.5
    hi_env = np.where(tt >= 32 * beat, 1.0 - (tt - 32 * beat) / (4 * beat), hi_env)
    for nm in ["A5", "Bb5", "E6"]:
        for v in range(2):
            strings += dsp.pan(trem_voice(dsp.hz(nm), 0.7) * hi_env * 0.8, rng.uniform(-0.9, 0.9))
    strings = dsp.circ(lambda x: dsp.lowpass(x, 5000, 2), strings)
    strings = dsp.circ(lambda x: dsp.peq(x, 2800, 1.0, 4.0), strings)
    cresc = 0.65 + 0.35 * np.interp(tt, [0, 32 * beat, 34 * beat, L], [0, 1, 1, 0])
    strings *= cresc
    # scraping metal every two bars
    scr = np.zeros((2, n))
    for k, t0 in enumerate([1.5 * beat, 9.5 * beat, 17.5 * beat, 25.5 * beat, 31.0 * beat]):
        d = beat * (3.0 if k < 4 else 4.5)
        m = ns(d)
        x = np.linspace(0, 1, m)
        e = np.sin(np.pi * x) ** 1.5
        s = np.zeros(m)
        g0 = rng.uniform(-200, 200)
        for f in [1630, 2290, 3110, 4570, 6230]:
            fr = f * dsp.cents(g0 + 300 * x * rng.choice([-1, 1]) + 20 * dsp.smooth(m, rng, 8))
            s += np.sin(dsp.TAU * np.cumsum(fr) / SR) * rng.uniform(0.3, 1.0)
        fr_n = ins.friction_pulses(d, rng, 180 + 120 * x, e, jitter=0.4)
        s = s * e * (0.6 + 0.4 * dsp.lowpass(np.abs(fr_n), 50, 1) / (np.max(np.abs(fr_n)) + 1e-9) * 8)
        s += dsp.bandpass(rng.standard_normal(m), 2500, 8000, 2) * e * 0.4
        p = np.linspace(rng.uniform(-0.9, 0), rng.uniform(0, 0.9), m)
        dsp.place(scr, dsp.pan(s, p), t0, circular=True)
    mixd = drums / (np.max(np.abs(drums)) + 1e-9) * 1.0 + np.vstack([bass, bass]) / (np.max(np.abs(bass)) + 1e-9) * 0.35
    mixd += strings / (np.max(np.abs(strings)) + 1e-9) * 0.4 + scr / (np.max(np.abs(scr)) + 1e-9) * 0.18
    ir = dsp.make_ir(1.6, rng, stereo=True, predelay=0.015)
    mixd = dsp.circ_reverb(mixd, ir, wet=0.3, dry=0.9)
    mixd = dsp.dc_block(mixd, circular=True)
    return mixd


# --------------------------------------------------------------------------
# game.phase (S, 6 s) - reversed swell -> deep boom + bell
# --------------------------------------------------------------------------
def phase(rng, i=1):
    dur = 6.0
    n = ns(dur)
    th = 1.5
    out = np.zeros((2, n))
    src = mixsrc = ins.bonsho(110.0, 1.2, rng) * 0.6 + ins.suzu_jingle(rng, 1.2, [(0, 0.8)], n_hits=(3, 5)) * 0.5
    rv = dsp.reverse(dsp.reverb(mixsrc, dsp.make_ir(3.0, rng, stereo=True), wet=1.0, dry=0.0))
    rv = rv / np.max(np.abs(rv)) * 0.7
    dsp.place_end(out, rv, th)
    boom = ins.taiko(rng, f0=42, vel=1.0, decay=1.1, dur=3.5, size=1.6)
    m = ns(3.0)
    t = np.arange(m) / SR
    sub = np.sin(dsp.TAU * np.cumsum(30 + 35 * np.exp(-t / 0.25)) / SR) * dsp.env_ad(m, 0.003, 0.9)
    dsp.place(out, np.vstack([boom, boom]) * 0.9 + 0.0, th)
    dsp.place(out, np.vstack([sub, sub]) * 0.8, th)
    bell = ins.bonsho(110.0, dur - th, rng)
    dsp.place(out, dsp.pan(dsp.lowpass(bell, 3000), 0.0) * 0.55, th + 0.01)
    jg = ins.suzu_jingle(rng, 3.0, [(0, 0.9), (0.15, 0.5)], n_hits=(4, 7))
    dsp.place(out, dsp.pan(jg, 0.3) * 0.35, th + 0.02)
    ir = dsp.make_ir(3.0, rng, stereo=True, predelay=0.03)
    out = dsp.reverb(out, ir, wet=0.35)
    out = dsp.pad_to(out, n)
    out = dsp.highpass(out, 25, 2)
    return dsp.fade(out, 0.0, 1.0)


# --------------------------------------------------------------------------
# game.death (S, 5 s) - descending rumble, heartbeat stopping, ringing
# --------------------------------------------------------------------------
def death(rng, i=1):
    dur = 5.0
    n = ns(dur)
    tt = np.arange(n) / SR
    out = np.zeros((2, n))
    drop = 2 ** (-np.clip(tt / 3.8, 0, 1) ** 1.3 * 2.2)
    rum = np.zeros(n)
    for f, a in [(110, 1.0), (165, 0.5), (220, 0.35), (233, 0.3), (55, 0.8)]:
        rum += a * np.sin(dsp.TAU * np.cumsum(f * drop) / SR + rng.random() * 6)
    rum += dsp.lowpass(rng.standard_normal(n), 300, 2) * 1.2
    rum *= dsp.env_pts(n, [(0, 0.0), (0.08, 1.0), (2.5, 0.7), (4.0, 0.2), (dur, 0.0)])
    rum = dsp.sat(rum * 0.7, 1.5)
    out += np.vstack([rum, np.roll(rum, 300)]) * 0.5
    for t0, v, sc in [(0.15, 1.0, 1.0), (1.05, 0.85, 0.95), (2.15, 0.65, 0.9), (3.6, 0.4, 0.85)]:
        hb = ins.heartbeat(rng, vel=v, lub_f=(68 * sc, 44 * sc), dub_f=(78 * sc, 54 * sc), gap=0.27 / sc ** 2)
        dsp.place(out, np.vstack([hb, hb]) * 1.4, t0)
    ring = np.sin(dsp.TAU * 6800 * tt) + 0.25 * np.sin(dsp.TAU * 8470 * tt)
    ring *= dsp.env_pts(n, [(0, 0), (3.2, 0.0), (4.0, 0.12), (dur, 0.0)])
    out += np.vstack([ring, ring])
    ir = dsp.make_ir(2.5, rng, stereo=True, predelay=0.02, lp_hz=4000)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 25, 2), 0.0, 0.4)


# --------------------------------------------------------------------------
# sliding doors
# --------------------------------------------------------------------------
def _slide(rng, d, rough=1.0, pitch=1.0, peak=0.5):
    m = ns(d)
    env = dsp.env_pts(m, [(0, 0), (d * 0.15, 0.8), (d * peak, 1.0), (d * 0.9, 0.6), (d, 0)])
    rate = (90 + 150 * env) * pitch * (1 + 0.2 * dsp.smooth(m, rng, 6))
    fp = ins.friction_pulses(d, rng, rate, env, jitter=0.45)
    body = dsp.resbank(fp, [(f * pitch, 8, g) for f, _, g in ins.WOOD_DOOR[:7]])
    body /= np.max(np.abs(body)) + 1e-9
    nz = dsp.bandpass(rng.standard_normal(m), 250, 3000, 2) * env * (1 + 0.4 * dsp.smooth(m, rng, 30))
    return dsp.lowpass(body * 0.6 * rough + nz * 0.5, 4500, 2)


def fusuma(rng, i=1):
    if i == 1:
        dur, d, end = 1.2, 0.6, 0.25
    elif i == 2:
        dur, d, end = 1.2, 0.5, 0.6
    else:
        dur, d, end = 1.8, 1.1, 0.2
    y = np.zeros(ns(dur))
    s = _slide(rng, d, rough=1.3 if i == 3 else 0.9, pitch=1.0)
    s = dsp.peq(s, 220, 1.2, 5.0)
    dsp.place(y, s, 0.02)
    if i == 3:
        cr = ins.creak(d, rng, [(0, 60), (d * 0.5, 130), (d, 80)], [(0, 0), (0.1, 0.6), (d * 0.8, 0.5), (d, 0)], pitch=1.2, tonal=0.4)
        dsp.place(y, cr * 0.25, 0.02)
    dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=1.1, hardness=0.5, vel=end, dur=0.5), 0.02 + d - 0.02)
    ir = dsp.make_ir(0.7, rng, predelay=0.004)
    y = dsp.reverb(y, ir, wet=0.25)
    return dsp.trim(dsp.highpass(y, 60, 2), -60)


def closet(rng, i=1):
    dur = 1.8
    y = np.zeros(ns(dur))
    if i == 1:
        d = 0.85
        dsp.place(y, _slide(rng, d, rough=1.4, pitch=0.8, peak=0.3), 0.02)
        cr = ins.creak(0.7, rng, [(0, 40), (0.3, 120), (0.7, 60)], [(0, 0), (0.08, 0.9), (0.6, 0.6), (0.7, 0)], pitch=1.05, tonal=0.5)
        dsp.place(y, cr * 0.5, 0.0)
    else:
        d = 0.45
        dsp.place(y, _slide(rng, d, rough=1.2, pitch=0.8, peak=0.7), 0.02)
        dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=1.4, hardness=0.45, vel=0.9, dur=0.6), 0.02 + d)
    y = dsp.peq(y, 180, 1.0, 4.0)
    ir = dsp.make_ir(0.6, rng, predelay=0.004)
    y = dsp.reverb(y, ir, wet=0.25)
    return dsp.trim(dsp.highpass(y, 60, 2), -60)


# --------------------------------------------------------------------------
# fire & light
# --------------------------------------------------------------------------
def _flame(rng, d, level=1.0, flicker=1.0):
    m = ns(d)
    env = dsp.env_pts(m, [(0, 0), (0.08, 1.0), (d * 0.6, 0.6), (d, 0)])
    nz = dsp.lowpass(rng.standard_normal(m), 900, 2) * env * (1 + 0.5 * flicker * dsp.smooth(m, rng, 12))
    nz += dsp.bandpass(rng.standard_normal(m), 800, 3000, 2) * env * 0.15
    return nz * level


def lantern_on(rng, i=1):
    dur = 1.6
    y = np.zeros(ns(dur))
    m = ns(0.14)
    se = dsp.env_pts(m, [(0, 0), (0.02, 1.0), (0.1, 0.8), (0.14, 0)])
    strike = dsp.bandpass(rng.standard_normal(m), 1800, 9000, 2) * se * (1 + 0.6 * dsp.smooth(m, rng, 60))
    strike += ins.crackle(0.14, rng, 900, se, hp=2500) * 0.6
    dsp.place(y, strike * 0.8, 0.02)
    m = ns(0.45)
    fw = dsp.bandpass(rng.standard_normal(m), 200, 3500, 2) * dsp.env_ad(m, 0.04, 0.12)
    fw += ins.crackle(0.45, rng, 300, dsp.env_ad(m, 0.02, 0.15), hp=1500) * 0.5
    dsp.place(y, fw * 0.9, 0.12)
    dsp.place(y, _flame(rng, 0.9, 0.55), 0.35)
    m = ns(0.35)
    dsp.place(y, dsp.lowpass(rng.standard_normal(m), 1200, 2) * dsp.env_ad(m, 0.05, 0.1) * 0.6, 0.62)
    ir = dsp.make_ir(0.5, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.15)
    return dsp.trim(dsp.highpass(y, 80, 2), -60)


def lantern_off(rng, i=1):
    dur = 0.9
    y = np.zeros(ns(dur))
    m = ns(0.38)
    blow = V.vt_noise(m, [(0, V.fs("u", V.VM, f1=0.8))], dsp.env_ad(m, 0.04, 0.12), rng, tilt_db=0.0, bw=1.5)
    dsp.place(y, blow * 0.5, 0.02)
    m = ns(0.2)
    puff = dsp.lowpass(rng.standard_normal(m), 500, 2) * dsp.env_ad(m, 0.005, 0.04)
    dsp.place(y, puff * 0.7, 0.1)
    dsp.place(y, _flame(rng, 0.3, 0.25, flicker=2.0) * dsp.env_pts(ns(0.3), [(0, 1), (0.3, 0)]), 0.08)
    ir = dsp.make_ir(0.5, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.15)
    return dsp.trim(dsp.highpass(y, 80, 2), -60)


def candle(rng, i=1):
    dur = 0.8
    y = np.zeros(ns(dur))
    m = ns(0.16)
    y_ = dsp.bandpass(rng.standard_normal(m), 300, 4500, 2) * dsp.env_ad(m, 0.015, 0.04)
    y_ += ins.crackle(0.16, rng, 200, dsp.env_ad(m, 0.01, 0.05), hp=2000) * 0.4
    dsp.place(y, y_ * 0.8, 0.02)
    dsp.place(y, _flame(rng, 0.5, 0.35), 0.1)
    ir = dsp.make_ir(0.4, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.12)
    return dsp.trim(dsp.highpass(y, 100, 2), -60)


def ofuda_burn(rng, i=1):
    dur = 2.6
    n = ns(dur)
    y = np.zeros(n)
    m = ns(1.4)
    fl = dsp.bandpass(rng.standard_normal(m), 150, 4000, 2) * dsp.env_pts(m, [(0, 0), (0.08, 1.0), (0.5, 0.6), (1.4, 0)])
    fl += ins.crackle(1.4, rng, 500, dsp.env_pts(m, [(0, 0), (0.05, 1), (1.4, 0)]), hp=1500) * 0.7
    dsp.place(y, fl * 0.8, 0.02)
    sh = np.zeros(ns(2.4))
    t = np.arange(len(sh)) / SR
    for f in [1320, 1980, 2640, 3520, 4410]:
        fr = f * (1 + 0.15 * np.clip(t / 1.5, 0, 1)) * (1 + 0.004 * np.sin(dsp.TAU * 5.5 * t + rng.random() * 6))
        sh += np.sin(dsp.TAU * np.cumsum(fr) / SR) * rng.uniform(0.4, 1.0)
    sh *= dsp.env_pts(len(sh), [(0, 0), (0.5, 1.0), (1.3, 0.7), (2.4, 0)])
    dsp.place(y, sh * 0.12, 0.15)
    jg = ins.suzu_jingle(rng, 1.5, [(0.0, 0.5)], n_hits=(2, 4))
    dsp.place(y, jg * 0.3, 0.2)
    ir = dsp.make_ir(1.6, rng, predelay=0.01)
    y = dsp.reverb(y, ir, wet=0.35)
    return dsp.trim(dsp.highpass(y, 80, 2), -60)


def salt(rng, i=1):
    dur = 1.4
    y = np.zeros(ns(dur))
    m = ns(0.25)
    wh = dsp.bandpass(rng.standard_normal(m), 300, 2500, 2) * dsp.env_ad(m, 0.08, 0.06)
    dsp.place(y, wh * 0.4, 0.0)
    m = ns(0.75)
    env = dsp.env_pts(m, [(0, 0), (0.05, 1.0), (0.3, 0.5), (0.75, 0)])
    grains = ins.crackle(0.75, rng, 6000, env, hp=3000, decay=0.0002)
    hiss = dsp.bandpass(rng.standard_normal(m), 3000, 10000, 2) * env * 0.25
    dsp.place(y, grains * 0.5 + hiss, 0.12)
    m = ns(0.8)
    siz = dsp.bandpass(rng.standard_normal(m), 4000, 9000, 2) * dsp.env_ad(m, 0.1, 0.3) * (0.5 + 0.5 * np.abs(dsp.smooth(m, rng, 40)))
    dsp.place(y, siz * 0.12, 0.3)
    ir = dsp.make_ir(0.6, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.18)
    return dsp.trim(dsp.highpass(y, 150, 2), -60)


# --------------------------------------------------------------------------
# small objects
# --------------------------------------------------------------------------
GLASS = [(2650, 0.18, 1.0), (4380, 0.12, 0.6), (6300, 0.09, 0.4), (8700, 0.06, 0.25)]


def _glass_hit(rng, vel):
    g = ins.wood_hit(rng, GLASS, size=rng.uniform(0.85, 1.15), hardness=1.0, vel=1.0, dur=0.25, noise=0.2)
    w = ins.wood_hit(rng, ins.WOOD_FLOOR, size=0.5, hardness=0.95, vel=0.8, dur=0.2, noise=0.3)
    return dsp.mix(g * 0.6, w) * vel


def marble(rng, i=1):
    dur = 2.2
    n = ns(dur)
    y = np.zeros(n)
    if i == 2:
        t = 0.02
        for k, v in enumerate([1.0, 0.55, 0.3]):
            g = ins.wood_hit(rng, GLASS, size=0.75, hardness=1.0, vel=v, dur=0.4, noise=0.15)
            dsp.place(y, g, t)
            t += rng.uniform(0.06, 0.1)
        nb = 2
    else:
        nb = 7 if i == 1 else 4
        t = 0.02
        dt = rng.uniform(0.22, 0.3)
        v = 1.0
        for k in range(nb):
            dsp.place(y, _glass_hit(rng, v), t)
            t += dt
            dt *= 0.68
            v *= 0.66
    if i != 3:
        m = ns(0.9)
        env = dsp.env_pts(m, [(0, 0), (0.05, 0.6), (0.9, 0)])
        roll = dsp.bandpass(rng.standard_normal(m), 250, 1800, 2) * env * (0.6 + 0.4 * np.abs(dsp.smooth(m, rng, 25)))
        dsp.place(y, roll * 0.12, t)
    ir = dsp.make_ir(0.6, rng, predelay=0.004)
    y = dsp.reverb(y, ir, wet=0.2)
    return dsp.trim(dsp.highpass(y, 150, 2), -60)


def drawer(rng, i=1):
    dur = 1.4
    y = np.zeros(ns(dur))
    d = 0.6 if i == 1 else 0.35
    m = ns(d)
    env = dsp.env_pts(m, [(0, 0), (0.05, 1.0), (d * 0.8, 0.8), (d, 0)])
    fp = ins.friction_pulses(d, rng, (40 + 60 * env) * (1 + 0.3 * dsp.smooth(m, rng, 5)), env, jitter=0.5)
    box = dsp.resbank(fp, [(210, 6, 1.0), (330, 7, 0.8), (520, 8, 0.6), (790, 9, 0.4), (1300, 10, 0.25)])
    box /= np.max(np.abs(box)) + 1e-9
    nz = dsp.bandpass(rng.standard_normal(m), 300, 2500, 2) * env * 0.35
    dsp.place(y, dsp.lowpass(box * 0.7 + nz, 4500, 2), 0.02)
    dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=0.8, hardness=0.5, vel=0.6 if i == 1 else 0.9, dur=0.4), 0.02 + d)
    ir = dsp.make_ir(0.5, rng, predelay=0.003)
    y = dsp.reverb(y, ir, wet=0.2)
    return dsp.trim(dsp.highpass(y, 80, 2), -60)


METAL = [(620, 0.5, 0.8), (1240 * 1.03, 0.4, 0.6), (1890, 0.35, 0.7), (2710, 0.28, 0.5), (3650, 0.2, 0.4), (4800, 0.15, 0.3)]


def unlock(rng, i=1):
    dur = 1.8
    y = np.zeros(ns(dur))
    m = ns(0.28)
    env = dsp.env_pts(m, [(0, 0), (0.03, 1.0), (0.28, 0.3)])
    scr = dsp.bandpass(rng.standard_normal(m), 2000, 8000, 2) * env * (1 + 0.8 * dsp.smooth(m, rng, 40))
    dsp.place(y, scr * 0.25, 0.02)
    for k, t0 in enumerate([0.36, 0.47, 0.55]):
        c = ins.wood_hit(rng, METAL, size=0.5, hardness=1.0, vel=0.35 + 0.1 * k, dur=0.15, noise=0.4)
        dsp.place(y, c, t0)
    clunk = ins.wood_hit(rng, METAL, size=1.6, hardness=0.7, vel=1.0, dur=0.8, noise=0.5)
    thud = ins.wood_hit(rng, ins.WOOD_DOOR, size=1.3, hardness=0.6, vel=0.9, dur=0.5)
    dsp.place(y, clunk + dsp.pad_to(thud, len(clunk)), 0.72)
    ir = dsp.make_ir(0.9, rng, predelay=0.005)
    y = dsp.reverb(y, ir, wet=0.28)
    return dsp.trim(dsp.highpass(y, 60, 2), -60)


def paper(rng, i=1):
    dur = 0.9 if i == 1 else 0.7
    n = ns(dur)
    env = dsp.env_pts(n, [(0, 0), (0.05, 1.0), (dur * 0.5, 0.6), (dur, 0)])
    if i == 2:
        env = dsp.env_pts(n, [(0, 0), (0.04, 0.7), (0.2, 1.0), (0.45, 0.3), (dur, 0)])
    cr = ins.crackle(dur, rng, 1500 if i == 1 else 900, env, hp=1200, decay=0.0004)
    sl = dsp.bandpass(rng.standard_normal(n), 800, 7000, 2) * env * (0.5 + 0.5 * np.abs(dsp.smooth(n, rng, 15)))
    y = cr / (np.max(np.abs(cr)) + 1e-9) * 0.6 + sl * 0.15
    ir = dsp.make_ir(0.4, rng, predelay=0.002)
    y = dsp.reverb(y, ir, wet=0.15)
    return dsp.trim(dsp.highpass(y, 200, 2), -60)


def puzzle_fail(rng, i=1):
    dur = 2.0
    y = np.zeros(ns(dur))
    for k, (sc, sp, t0, g) in enumerate([(1.0, 1.6, 0.0, 1.0), (0.9, 1.4, 0.05, 0.8), (1.1, 1.8, 0.1, 0.7)]):
        w = V.whisper("shi su sho sa shi", rng, scale=sc, rate=sp, intensity=g, hiss=2.2)
        dsp.place(y, w, t0)
    m = ns(0.7)
    hiss = dsp.bandpass(rng.standard_normal(m), 2500, 9000, 2) * dsp.env_ad(m, 0.05, 0.2)
    dsp.place(y, hiss * 0.6 * np.std(y[y != 0]) * 3, 0.05)
    m = ns(0.25)
    snuff = dsp.lowpass(rng.standard_normal(m), 600, 2) * dsp.env_ad(m, 0.004, 0.05)
    dsp.place(y, snuff * np.max(np.abs(y)) * 0.9, 0.85)
    m = ns(1.0)
    t = np.arange(m) / SR
    dr = np.sin(dsp.TAU * np.cumsum(90 * 2 ** (-t)) / SR) * dsp.env_ad(m, 0.05, 0.3)
    dsp.place(y, dr * np.max(np.abs(y)) * 0.4, 0.8)
    ir = dsp.make_ir(0.9, rng, predelay=0.005)
    y = dsp.reverb(y, ir, wet=0.3)
    return dsp.trim(dsp.highpass(y, 60, 2), -60)


def _well_ir(rng, dur=2.5):
    n = ns(dur)
    ir = np.zeros(n)
    per = 0.0165
    t = 0.004
    a = 1.0
    while t < dur:
        ir[ns(t)] += a * rng.choice([-1, 1]) * rng.uniform(0.7, 1.0)
        t += per * rng.uniform(0.97, 1.03)
        a *= 0.93
    ir = dsp.lowpass(ir, 2500, 2) + dsp.make_ir(1.8, rng, dur=dur) * 0.4
    return ir / np.sqrt(np.sum(ir ** 2))


def well(rng, i=1):
    if i == 1:
        dur = 2.8
        n = ns(dur)
        t = np.arange(n) / SR
        rot = 0.5 + 0.5 * np.sin(dsp.TAU * 1.4 * t)
        rate = 30 + 110 * rot
        y = ins.creak(dur, rng, [(0, 1), (dur, 1)], [(0, 0), (0.15, 0.8), (dur - 0.3, 0.8), (dur, 0)], pitch=0.9, tonal=0.5, jitter=0.25)
        cr2 = ins.friction_pulses(dur, rng, rate, dsp.env_pts(n, [(0, 0), (0.15, 1), (dur - 0.3, 1), (dur, 0)]) * rot, jitter=0.2)
        cr2 = dsp.resbank(cr2, [(380, 15, 1.0), (760, 18, 0.7), (1140, 20, 0.5), (1900, 22, 0.3)])
        y = y * 0.4 + cr2 / (np.max(np.abs(cr2)) + 1e-9) * 0.6
        rope = dsp.bandpass(np.random.default_rng(int(rng.integers(1 << 30))).standard_normal(n), 300, 3000, 2)
        y += rope * 0.05 * dsp.env_pts(n, [(0, 0), (0.2, 1), (dur - 0.3, 1), (dur, 0)])
        wet = 0.3
    else:
        dur = 3.2
        n = ns(dur)
        y = np.zeros(n)
        dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=1.6, hardness=0.6, vel=0.8, dur=0.5), 0.02)
        dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=1.5, hardness=0.6, vel=0.5, dur=0.5), 0.3)
        m = ns(0.8)
        spl = dsp.bandpass(rng.standard_normal(m), 300, 5000, 2) * dsp.env_ad(m, 0.008, 0.12)
        bub = np.zeros(m)
        for k in range(12):
            tb = rng.uniform(0.02, 0.6)
            mb = ns(0.05)
            tt = np.arange(mb) / SR
            fb = rng.uniform(400, 1400)
            dsp.place(bub, np.sin(dsp.TAU * np.cumsum(fb * (1 + 1.5 * tt / 0.05)) / SR) * np.exp(-tt / 0.012) * rng.uniform(0.2, 0.6), tb)
        dsp.place(y, dsp.lowpass(spl * 0.8 + bub, 3000, 2) * 0.8, 1.05)
        wet = 0.7
    y = dsp.reverb(y, _well_ir(rng), wet=wet, dry=0.8)
    return dsp.trim(dsp.highpass(y, 60, 2), -60)


# --------------------------------------------------------------------------
# game.gate_open (S, 6 s) and game.ritual (S, ~6 s)
# --------------------------------------------------------------------------
def gate_open(rng, i=1):
    dur = 6.0
    n = ns(dur)
    tt = np.arange(n) / SR
    out = np.zeros((2, n))
    chord = [dsp.hz(x) for x in ["D3", "A3", "D4", "E4", "A4", "B4"]]
    sw = ins.sho([(0.0, dur, chord, 1.0)], dur, rng)
    env = dsp.env_pts(n, [(0, 0), (3.2, 1.0), (4.8, 0.8), (dur, 0.0)]) ** 1.5
    out += sw / (np.max(np.abs(sw)) + 1e-9) * env * 0.45
    for f, a in [(73.42, 0.5), (110.0, 0.3), (146.83, 0.2)]:
        out += np.vstack([np.sin(dsp.TAU * f * tt), np.sin(dsp.TAU * f * 1.002 * tt)]) * a * env
    shakes = []
    t = 0.3
    gap = 0.5
    while t < 4.6:
        shakes.append((t, 0.5 + 0.5 * t / 4.6))
        t += gap
        gap = max(0.07, gap * 0.85)
    for p in (-0.6, 0.6):
        out += dsp.pan(ins.suzu_jingle(rng, dur, shakes, n_hits=(2, 5)), p) * 0.5
    nz = rng.standard_normal(n)
    fc = dsp.env_pts(n, [(0, 200), (3.5, 2500), (dur, 600)])
    wind = dsp.tv_filter(nz, fc, 0.9, "bp")
    wind = wind / np.std(wind) * dsp.env_pts(n, [(0, 0), (3.4, 1.0), (4.5, 0.6), (dur, 0)])
    out += np.vstack([wind, np.roll(wind, ns(0.013))]) * 0.18
    ir = dsp.make_ir(3.2, rng, stereo=True, predelay=0.03)
    out = dsp.reverb(out, ir, wet=0.35)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 30, 2), 0.0, 0.8)


def ritual(rng, i=1):
    dur = 6.2
    n = ns(dur)
    tt = np.arange(n) / SR
    out = np.zeros((2, n))
    shakes = []
    t = 0.05
    gap = 0.55
    while t < 5.1:
        shakes.append((t, 0.55 + 0.45 * t / 5.1))
        t += gap
        gap = max(0.035, gap * 0.86)
    for p, bases in [(-0.5, (3300, 4200, 5300)), (0.5, (3550, 4500, 5800))]:
        out += dsp.pan(ins.suzu_jingle(rng, dur, shakes, bases=bases, n_hits=(2, 4)), p) * 0.5
    env = dsp.env_pts(n, [(0, 0), (5.1, 1.0), (5.3, 0.6), (dur, 0.0)]) ** 2
    for f, a in [(73.42, 0.6), (110.0, 0.35), (220.0 * dsp.cents(15), 0.12)]:
        out += np.vstack([np.sin(dsp.TAU * f * tt), np.sin(dsp.TAU * f * 1.003 * tt)]) * a * env
    hi = ins.strings([dsp.hz(x) for x in ["D5", "A5", "E6"]], dur, rng, env=env, voices=2, trem=0.4, bright=0.6)
    out += hi / (np.max(np.abs(hi)) + 1e-9) * 0.25
    fin = ins.suzu_jingle(rng, 1.2, [(0.0, 1.0)], n_hits=(6, 9))
    dsp.place(out, np.vstack([fin, fin]) * 0.6, 5.15)
    ir = dsp.make_ir(2.6, rng, stereo=True, predelay=0.02)
    out = dsp.reverb(out, ir, wet=0.3)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 30, 2), 0.0, 0.5)
