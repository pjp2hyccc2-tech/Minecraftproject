"""ambient.* sounds: beds, house noises and distant events."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, ns


# --------------------------------------------------------------------------
# loop helpers
# --------------------------------------------------------------------------
def _qf(f, L):
    return dsp.loop_freq(f, L)


def _loop_sine(f, L, n, rng):
    return np.sin(dsp.TAU * (_qf(f, L) * np.arange(n) / SR + rng.random()))


def _loop_lfo(n, cycles, rng, lo=0.0, hi=1.0, shape=1.0):
    ph = cycles * np.arange(n) / n + rng.random()
    v = 0.5 - 0.5 * np.cos(dsp.TAU * ph)
    return lo + (hi - lo) * v ** shape


# --------------------------------------------------------------------------
# ambient.drone - 48 s seamless loop
# --------------------------------------------------------------------------
def drone(rng, i=1):
    L = 48.0
    n = ns(L)
    out = np.zeros((2, n))
    # (freq, level, beat Hz) - D based, dark, slow beating pairs
    tones = [(36.71, 0.55, 0.06), (73.42, 1.0, 0.10), (110.0, 0.42, 0.15), (146.83, 0.30, 0.08),
             (220.0, 0.12, 0.21), (293.66, 0.07, 0.12), (155.56, 0.05, 0.1)]
    for f, a, bt in tones:
        for c in range(2):
            sw = _loop_lfo(n, rng.integers(1, 4), rng, 0.45, 1.0)
            s = _loop_sine(f, L, n, rng) + 0.8 * _loop_sine(f + bt * (1 + 0.3 * c), L, n, rng)
            out[c] += a * s * sw
    # dark noise bed, slowly breathing
    for c in range(2):
        nz = dsp.colored(n, rng, -6.0, lo=25, hi=220)
        out[c] += 0.35 * nz * _loop_lfo(n, 3, rng, 0.4, 1.0)
        mid = dsp.colored(n, rng, -3.0, lo=250, hi=700)
        out[c] += 0.05 * mid * _loop_lfo(n, 2, rng, 0.1, 1.0, 2.0)
    # faint metal shimmer (like a bowed cymbal far away)
    sh = np.zeros((2, n))
    for f in [1873.0, 2411.0, 2967.0, 3558.0, 4419.0, 5302.0]:
        fm = _qf(f, L) * (1 + 0.002 * dsp.smooth(n, rng, 0.3))
        for c in range(2):
            sh[c] += dsp.sine_loop(fm * (1 + 0.0007 * c), n, rng.random()) * _loop_lfo(n, rng.integers(1, 3), rng, 0.0, 1.0, 3.0)
    out += sh * 0.035
    ir = dsp.make_ir(3.2, rng, stereo=True, predelay=0.03, hf_ratio=0.4)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.85)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# ambient.drone_tense - 32 s seamless loop, dissonant cluster + slow pulse
# --------------------------------------------------------------------------
def drone_tense(rng, i=1):
    L = 32.0
    n = ns(L)
    out = np.zeros((2, n))
    cluster = [73.42, 77.78, 103.83, 146.83, 155.56, 207.65, 311.13, 293.66, 440.0 * dsp.cents(-30)]
    lv = [0.9, 0.7, 0.5, 0.55, 0.5, 0.3, 0.18, 0.2, 0.08]
    for f, a in zip(cluster, lv):
        for v in range(3):
            fv = f * dsp.cents(rng.normal(0, 7))
            traj = fv * dsp.cents(5.0 * dsp.smooth(n, rng, 0.5))
            s = dsp.saw(traj, n, rng.random(), loop=True)
            p = rng.uniform(-0.8, 0.8)
            out += dsp.pan(s * a, p)
    out = dsp.circ(lambda x: dsp.lowpass(x, 1400, 2), out)
    out = dsp.circ(lambda x: dsp.peq(x, 200, 0.8, 3.0), out)
    # slow throbbing pulse, 0.75 Hz (24 per loop): fast swell, slow release
    ph = (np.arange(n) / n * 24) % 1.0
    throb = np.exp(-ph / 0.28) * np.minimum(1, ph / 0.04) + np.exp(-(1 - ph) / 0.01) * 0.0
    out *= 0.6 + 0.4 * throb
    # sub pulse
    sub = np.sin(dsp.TAU * _qf(41.2, L) * np.arange(n) / SR) * (0.2 + 0.8 * throb ** 2)
    out += 0.5 * sub
    # high glassy harmonics, very quiet, swelling
    for f in [1661.2, 1760.0, 2489.0, 2637.0]:
        g = dsp.sine_loop(f * (1 + 0.003 * dsp.smooth(n, rng, 0.4)), n, rng.random())
        out += dsp.pan(g * _loop_lfo(n, rng.integers(1, 3), rng, 0.0, 1.0, 2.0) * 0.02, rng.uniform(-1, 1))
    rum = np.vstack([dsp.colored(n, rng, -6, lo=20, hi=150) for _ in range(2)])
    out += rum * 0.25
    ir = dsp.make_ir(2.5, rng, stereo=True, predelay=0.02)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.8)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# ambient.wind - 30 s seamless loop
# --------------------------------------------------------------------------
def wind(rng, i=1):
    L = 30.0
    n = ns(L)
    base_gust = dsp.smooth(n, rng, 0.12)
    out = np.zeros((2, n))
    for c in range(2):
        g = base_gust + 0.45 * dsp.smooth(n, rng, 0.25)
        g = (g - g.min()) / (g.max() - g.min())
        gust = 0.15 + 0.85 * g ** 1.6
        body = dsp.colored(n, rng, -4.5, lo=60, hi=2500)
        hiss = dsp.colored(n, rng, -1.0, lo=1500, hi=7000)
        y = body * gust * 0.8 + hiss * gust ** 2 * 0.12
        # whistles through gaps (narrow band, pitch follows the gust)
        for f0, bw, lv in [(610, 18, 0.35), (920, 25, 0.22), (1370, 30, 0.12), (455, 14, 0.2)]:
            fr = f0 * (0.85 + 0.3 * g) * (1 + 0.01 * dsp.smooth(n, rng, 1.0))
            w = dsp.nb_noise(fr, bw, rng, periodic_fix=True)
            y += w * lv * np.clip((g - 0.45) / 0.55, 0, 1) ** 2
        # paper screen flutter in the strong gusts
        rate = _qf(11.0 + c, L)
        flut = 0.5 + 0.5 * np.sin(dsp.TAU * (rate * np.arange(n) / SR + 0.03 * np.cumsum(dsp.smooth(n, rng, 2.0)) / SR))
        pap = dsp.colored(n, rng, -2.0, lo=180, hi=900) * flut ** 3 * np.clip((g - 0.6) / 0.4, 0, 1) ** 2
        y += pap * 0.35
        out[c] = y
    ir = dsp.make_ir(1.4, rng, stereo=True, predelay=0.01)
    out = dsp.circ_reverb(out, ir, wet=0.25, dry=0.9)
    return dsp.dc_block(out, circular=True)


# --------------------------------------------------------------------------
# ambient.koto_1..4
# --------------------------------------------------------------------------
KOTO_PHRASES = {
    1: (12.0, [(0.3, ["A4"], 0.9, None), (1.45, ["G4"], 0.55, None), (2.05, ["Eb4"], 0.75, [(0, 0), (0.25, 0), (0.6, -90)]),
               (4.0, ["D4"], 0.8, None), (6.8, ["A3"], 0.5, None)]),
    2: (10.0, [(0.2, ["D3", "D4"], 0.95, None), (2.3, ["Eb5"], 0.45, None), (2.95, ["D5"], 0.4, None),
               (5.6, ["D3", "A3"], 0.6, None)]),
    3: (9.0, [(0.3, ["D4", "Eb4"], 0.85, None), (2.6, ["G4"], 0.7, [(0, 0), (0.3, 0), (0.75, 100), (1.6, 100), (2.2, 40)]),
              (5.2, ["Bb3"], 0.5, None)]),
    4: (14.0, [(0.2, ["D4"], 0.65, None), (0.95, ["Eb4"], 0.55, None), (1.65, ["G4"], 0.7, None), (2.7, ["A4"], 0.8, None),
               (4.6, ["Bb4"], 0.95, lambda t: 22 * np.sin(dsp.TAU * 5.0 * t) * np.clip((t - 0.4) / 0.5, 0, 1) * np.exp(-t / 2.5)),
               (8.3, ["Eb5"], 0.38, None)]),
}


def koto_phrase(rng, i=1):
    dur, notes = KOTO_PHRASES[i]
    n = ns(dur)
    out = np.zeros((2, n))
    for t0, names, vel, bend in notes:
        for j, nm in enumerate(names):
            f = dsp.hz(nm, rng.uniform(-14, 6))
            nd = min(dur - t0, 6.0)
            s = ins.koto(f, nd, rng, vel=vel * (0.9 if j else 1.0), bend=bend)
            out = dsp.place(out, dsp.pan(s, rng.uniform(-0.45, 0.45)), t0 + j * rng.uniform(0.01, 0.03))
    ir = dsp.make_ir(3.2, rng, stereo=True, predelay=0.03, hf_ratio=0.35)
    out = dsp.reverb(out, ir, wet=0.5, dry=0.8)
    out = dsp.pad_to(out, n)
    return dsp.fade(dsp.highpass(out, 50, 2), 0.0, 1.2)


# --------------------------------------------------------------------------
# ambient.creak_1..6
# --------------------------------------------------------------------------
def creak(rng, i=1):
    if i == 1:
        y = ins.creak(1.0, rng, [(0, 18), (0.35, 45), (0.75, 26), (1.0, 15)], [(0, 0), (0.1, 0.9), (0.7, 0.7), (1.0, 0)], pitch=0.6, jitter=0.3)
    elif i == 2:
        y = ins.creak(1.5, rng, [(0, 40), (0.5, 160), (1.0, 110), (1.5, 70)], [(0, 0), (0.15, 0.8), (1.2, 1.0), (1.5, 0)],
                      pitch=1.3, jitter=0.12, q_scale=2.0, tonal=0.6)
    elif i == 3:
        a = ins.creak(1.2, rng, [(0, 12), (0.6, 32), (1.2, 10)], [(0, 0), (0.25, 0.9), (1.0, 0.5), (1.2, 0)], pitch=0.8, jitter=0.35)
        b = ins.creak(1.1, rng, [(0, 15), (0.5, 26), (1.1, 8)], [(0, 0), (0.2, 0.6), (1.1, 0)], pitch=0.75, jitter=0.35)
        y = dsp.mix(a, dsp.place(np.zeros(ns(2.4)), b * 0.8, 1.25))
    elif i == 4:
        y = ins.creak(0.4, rng, [(0, 100), (0.25, 230), (0.4, 180)], [(0, 0), (0.05, 1.0), (0.32, 0.8), (0.4, 0)],
                      pitch=1.6, jitter=0.1, q_scale=2.2, tonal=0.8)
    elif i == 5:
        a = ins.creak(0.6, rng, [(0, 22), (0.3, 55), (0.6, 20)], [(0, 0), (0.1, 0.9), (0.6, 0)], pitch=0.9)
        b = ins.creak(0.7, rng, [(0, 25), (0.35, 65), (0.7, 18)], [(0, 0), (0.1, 0.7), (0.7, 0)], pitch=0.95)
        y = dsp.mix(a, dsp.place(np.zeros(ns(1.4)), b, 0.62))
    else:
        a = ins.creak(0.9, rng, [(0, 25), (0.6, 62), (0.9, 40)], [(0, 0), (0.15, 0.8), (0.85, 1.0), (0.9, 0)], pitch=0.75)
        hit = ins.wood_hit(rng, ins.WOOD_FLOOR, size=1.2, hardness=0.6, vel=0.7, dur=0.4)
        y = dsp.mix(a, dsp.place(np.zeros(ns(1.4)), hit, 0.88))
    ir = dsp.make_ir(0.85, rng, predelay=0.006)
    y = dsp.reverb(y, ir, wet=0.3)
    return dsp.trim(dsp.highpass(y, 60, 2))


# --------------------------------------------------------------------------
# ambient.footsteps_above_1..3
# --------------------------------------------------------------------------
def _above_step(rng, vel=1.0, creak=False):
    y = ins.wood_hit(rng, ins.WOOD_FLOOR, size=2.0, hardness=0.3, vel=1.0, dur=0.7, noise=0.25)
    m = ns(0.4)
    boom = np.sin(dsp.TAU * 58 * np.arange(m) / SR) * dsp.env_ad(m, 0.004, 0.07)
    y = dsp.mix(y, boom * 0.8)
    if creak:
        c = ins.creak(0.5, rng, [(0, 20), (0.25, 45), (0.5, 18)], [(0, 0), (0.1, 0.6), (0.5, 0)], pitch=0.7)
        y = dsp.mix(y, dsp.place(np.zeros(ns(0.7)), c * 0.5, 0.05))
    return y * vel


def footsteps_above(rng, i=1):
    if i == 1:
        dur, steps = 5.0, [(0.2, 1.0, False), (1.25, 0.9, True), (2.35, 1.0, False), (3.45, 0.85, False)]
    elif i == 2:
        dur, steps = 6.0, [(0.2, 0.9, False), (1.15, 1.0, False), (2.1, 0.95, True), (4.7, 1.0, True)]
    else:
        dur, steps = 5.2, [(0.2, 1.0, False), (1.6, 0.9, False), (3.0, 1.0, True)]
    n = ns(dur)
    y = np.zeros(n)
    for t0, v, cr in steps:
        dsp.place(y, _above_step(rng, v, cr), t0 + rng.uniform(-0.04, 0.04))
        if i == 3:  # dragging the other foot
            m = ns(0.75)
            dr = dsp.bandpass(rng.standard_normal(m), 150, 1500, 2) * dsp.env_pts(m, [(0, 0), (0.15, 1), (0.6, 0.6), (0.75, 0)])
            dr *= 1 + 0.5 * dsp.smooth(m, rng, 20.0)
            dsp.place(y, dr * 0.25, t0 + 0.45)
    y = dsp.lowpass(y, 380, 4)
    y = dsp.peq(y, 90, 1.0, 5.0)
    ir = dsp.make_ir(0.7, rng, predelay=0.01, lp_hz=600)
    y = dsp.reverb(y, ir, wet=0.4)
    y = dsp.highpass(y, 30, 2)
    return dsp.pad_to(dsp.fade(y, 0, 0.3), n)


# --------------------------------------------------------------------------
# ambient.knock_1..4
# --------------------------------------------------------------------------
def _knock(rng, vel=1.0, hard=0.65, size=1.0):
    y = ins.wood_hit(rng, ins.WOOD_DOOR, size=size, hardness=hard, vel=1.0, dur=0.45, noise=0.35)
    return y * vel


def knock(rng, i=1):
    if i == 1:
        dur, ks = 1.4, [(0.05, 1.0), (0.25, 0.85), (0.44, 0.9)]
    elif i == 2:
        dur, ks = 3.4, [(0.05, 1.0), (1.1, 0.95), (2.2, 0.7)]
    elif i == 3:
        dur, ks = 2.6, [(0.05, 0.9), (0.3, 0.8), (1.75, 1.0)]
    else:
        dur, ks = 2.6, [(0.05, 1.0)]
    n = ns(dur)
    y = np.zeros(n)
    size = rng.uniform(0.9, 1.1)
    for t0, v in ks:
        if i == 4:
            h = _knock(rng, v, hard=0.35, size=1.5)
        else:
            h = _knock(rng, v, size=size)
        dsp.place(y, h, t0 + rng.uniform(0, 0.015))
    if i == 4:  # an answer from further away
        far = dsp.lowpass(_knock(rng, 0.35, size=1.1), 900, 2)
        dsp.place(y, far, 1.6)
    y = dsp.lowpass(y, 6500, 2)
    ir = dsp.make_ir(0.8, rng, predelay=0.005)
    y = dsp.reverb(y, ir, wet=0.3)
    return dsp.trim(dsp.highpass(y, 60, 2))


# --------------------------------------------------------------------------
# ambient.whisper_1..6
# --------------------------------------------------------------------------
WHISPERS = {
    1: [("u shi ro no sho - me N , da - re", 1.0, 1.15, 0.0, 1.0)],
    2: [("mi tsu ke ta", 1.0, 0.75, 0.0, 1.0)],
    3: [("i q sho ni , i yo - ne", 1.04, 1.0, 0.0, 1.0)],
    4: [("ka e sa na i", 1.0, 0.95, 0.0, 1.0), ("ka e sa na i", 0.88, 0.9, 0.55, 0.6)],
    5: [("o me de to - go za i ma su", 1.0, 1.0, 0.0, 1.0), ("o me de to - go za i ma su", 1.08, 0.92, 0.13, 0.8),
        ("o me de to - go za i ma su", 0.9, 1.06, 0.3, 0.7), ("o me de to - go za i ma su", 0.85, 0.85, 0.5, 0.5)],
    6: [("ko q chi . o i de", 1.0, 0.9, 0.0, 1.0), ("o i de", 0.9, 0.8, 1.05, 0.55)],
}


def whisper(rng, i=1):
    parts = []
    for script, scale, rate, t0, g in WHISPERS[i]:
        y = V.whisper(script, rng, scale=scale, rate=rate, intensity=g)
        parts.append(dsp.place(np.zeros(ns(t0) + len(y)), y, t0))
    y = dsp.mix(*parts)
    y = dsp.peq(y, 180, 0.9, 3.0)
    y = dsp.highpass(y, 120, 2)
    ir = dsp.make_ir(0.35, rng, predelay=0.002, lp_hz=8000)
    y = dsp.reverb(y, ir, wet=0.12)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# ambient.door_slam_1..3 (far away)
# --------------------------------------------------------------------------
def door_slam(rng, i=1):
    dur = 3.5
    n = ns(dur)
    y = np.zeros(n)
    t_hit = 0.05
    if i == 1:
        ts = 0.2
        m = ns(ts)
        env = (np.arange(m) / m) ** 1.6
        sl = dsp.lowpass(rng.standard_normal(m), 1200, 2) * env
        ch = ins.friction_pulses(ts, rng, np.linspace(50, 150, m), env, jitter=0.35)
        ch = dsp.resbank(ch, [(f, 9, g) for f, _, g in ins.WOOD_DOOR[:6]])
        dsp.place(y, sl * 0.4 + ch / (np.max(np.abs(ch)) + 1e-9) * 0.4, 0.02)
        t_hit = 0.02 + ts
    bang = ins.wood_hit(rng, ins.WOOD_DOOR, size=1.25, hardness=0.9, vel=1.0, dur=0.8, noise=0.5)
    dsp.place(y, bang, t_hit)
    m = ns(0.2)
    dsp.place(y, dsp.bandpass(rng.standard_normal(m), 60, 300, 2) * dsp.env_ad(m, 0.002, 0.05) * 0.8, t_hit)
    tr = t_hit + 0.02
    for k in range(7):
        tr += rng.uniform(0.02, 0.05)
        dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=0.6, hardness=0.7, vel=0.3 * 0.75 ** k, dur=0.15), tr)
    if i == 3:  # bounces back and knocks again
        dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=1.2, hardness=0.8, vel=0.45, dur=0.6), t_hit + 0.42)
    y = dsp.lowpass(y, 1700, 2)
    ir = dsp.make_ir(2.2, rng, predelay=0.045, lp_hz=2500, hf_ratio=0.3)
    y = dsp.reverb(y, ir, wet=0.95, dry=0.35)
    y = dsp.highpass(y, 40, 2)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# ambient.music_box - かごめかごめ, detuned, spring winding down (~20 s)
# --------------------------------------------------------------------------
def music_box(rng, i=1):
    octv = {"E": "E5", "G": "G5", "A": "A5", "B": "B5"}
    # each pitch has two tines with their own (bad) tuning
    tines = {p: [rng.uniform(-28, 12), rng.uniform(-10, 30)] for p in "EGAB"}
    tines["B"][1] = 38.0
    flat = [(p, d) for ph in ins.KAGOME for (p, d) in ph]
    e = 0.300
    tot = sum(d for _, d in flat)
    events = []
    t = 0.25
    acc = 0
    use = {p: 0 for p in "EGAB"}
    for k, (p, d) in enumerate(flat):
        prog = acc / tot
        slow = 1.0 + 2.6 * max(0.0, (prog - 0.62) / 0.38) ** 2.2
        events.append((t, p, k))
        t += d * e * slow * (1 + rng.uniform(-0.03, 0.03))
        acc += d
    t_end = events[-1][0]
    dur = t_end + 2.6
    n = ns(dur)
    y = np.zeros(n)
    for (t0, p, k) in events:
        if k == 16:  # broken tine: the pin only clicks
            m = ns(0.03)
            dsp.place(y, dsp.bandpass(rng.standard_normal(m), 2000, 7000) * np.exp(-np.arange(m) / (0.003 * SR)) * 0.15, t0)
            continue
        tn = use[p] % 2
        use[p] += 1
        f = dsp.hz(octv[p], tines[p][tn])
        vel = 0.75 + 0.25 * rng.random()
        if k == len(events) - 1:
            vel *= 0.6
        dsp.place(y, ins.tine(f, rng, vel=vel), t0)
    # sparse bass tines on bar lines
    bar_t = [ev[0] for ev in events if sum(d for _, d in flat[: ev[2]]) % 8 == 0]
    bass = ["A4", "A4", "E4", "G4", "E4", "A4", "E4"]
    for j, tb in enumerate(bar_t):
        nm = bass[j % len(bass)]
        dsp.place(y, ins.tine(dsp.hz(nm, rng.uniform(-20, 10)), rng, vel=0.45), tb + 0.004)
    # governor whirr slowing down with the spring
    tt = np.arange(n) / SR
    speed = np.interp(tt, [0, events[int(len(events) * 0.62)][0], t_end, t_end + 0.25], [1.0, 1.0, 0.3, 0.0])
    rot = np.cumsum(28.0 * speed) / SR
    whirr = dsp.bandpass(rng.standard_normal(n), 1800, 5200, 2) * (0.4 + 0.6 * np.abs(np.sin(np.pi * rot))) * speed
    y += whirr * 0.012
    tick = np.zeros(n)
    k_prev = 0
    for idx in np.nonzero(np.diff(np.floor(rot / 2.0)) > 0)[0]:
        tick[idx] = 0.05 * speed[idx]
    y += dsp.bandpass(tick, 1500, 6000, 2)
    m = ns(0.08)
    dsp.place(y, ins.wood_hit(rng, ins.CERAMIC, size=1.6, hardness=0.8, vel=0.12, dur=0.2), t_end + 0.3)
    y = dsp.mix(y, dsp.reson(y, 520, 2.0, 0.3), dsp.reson(y, 1150, 3.0, 0.2))
    ir = dsp.make_ir(0.9, rng, predelay=0.006, lp_hz=7000)
    y = dsp.reverb(y, ir, wet=0.3)
    y = dsp.highpass(y, 150, 2)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# ambient.crying_1..2 - distant woman sobbing
# --------------------------------------------------------------------------
def crying(rng, i=1):
    dur = 5.6 if i == 1 else 5.0
    n = ns(dur)
    f0 = np.full(n, 420.0)
    amp = np.zeros(n)
    sub = np.zeros(n)
    noise_parts = []
    if i == 1:
        ev = [("in", 0.05, 0.5), ("wail", 0.55, 1.8, (470, 525, 380)), ("sob", 1.9), ("sob", 2.12), ("sob", 2.36),
              ("in", 2.55, 3.0), ("wail", 3.05, 4.75, (440, 455, 330)), ("sniff", 5.0)]
    else:
        ev = [("sob", 0.1), ("sob", 0.33), ("wail", 0.58, 1.95, (500, 540, 400)), ("in", 2.05, 2.55),
              ("wail", 2.65, 3.95, (430, 420, 340)), ("sob", 4.1), ("in", 4.35, 4.8)]
    tt = np.arange(n) / SR
    for e in ev:
        if e[0] == "wail":
            _, a, b, (fa, fm, fb) = e
            i0, i1 = ns(a), ns(b)
            m = i1 - i0
            x = np.linspace(0, 1, m)
            lp = np.interp(x, [0, 0.25, 1], np.log2([fa, fm, fb]))
            trem = dsp.smooth(m, rng, 7.0)
            lp += 0.03 * trem + 0.012 * dsp.smooth(m, rng, 18.0)
            f0[i0:i1] = 2 ** lp
            env = np.minimum(1, x * (b - a) / 0.06) * (1 - x) ** 0.6
            env *= np.clip(1 + 0.45 * trem, 0.15, 1.6)
            amp[i0:i1] = env
            sub[i0 + int(m * 0.65): i1] = 0.45
        elif e[0] == "sob":
            a = e[1]
            sd = rng.uniform(0.12, 0.17)
            i0, i1 = ns(a), ns(a + sd)
            m = i1 - i0
            st_ = rng.uniform(470, 520)
            f0[i0:i1] = np.linspace(st_, st_ * 0.78, m)
            amp[i0:i1] = dsp.env_ad(m, 0.012, sd * 0.4) * 0.85
        elif e[0] == "in":
            noise_parts.append(("in", e[1], e[2]))
        elif e[0] == "sniff":
            noise_parts.append(("sniff", e[1], e[1] + 0.2))
    f0 = dsp.lowpass0(f0, 25)
    keys = [(0, V.fs("u", scale=1.05)), (0.9, V.fs("a", scale=1.05, f1=0.9)), (1.8, V.fs("u", scale=1.05)),
            (3.4, V.fs("a", scale=1.05, f1=0.85)), (4.7, V.fs("u", scale=1.05)), (dur, V.fs("u"))]
    y = V.voice(f0, amp, keys, rng, tilt=-7.0, fmax=7000, jitter=0.02, shimmer=0.12, breath=0.55, subharm=sub,
                noise_mod=0.6, loud_comp=0.7)
    ref = np.std(y[amp > 0.1]) if np.any(amp > 0.1) else 1.0
    for kind, a, b in noise_parts:
        m = ns(b - a)
        x = np.linspace(0, 1, m)
        if kind == "in":  # stuttered, shaky inhale
            stut = 0.35 + 0.65 * (0.5 + 0.5 * np.sin(dsp.TAU * rng.uniform(7, 10) * x * (b - a))) ** 2
            env = np.sin(np.pi * x) ** 0.8 * stut
            s = V.vt_noise(m, [(0, V.fs("x", f1=0.8)), (b - a, V.fs("i"))], env, rng, tilt_db=2.0, bw=1.6) * 0.5 * ref
        else:
            env = dsp.env_ad(m, 0.02, 0.06)
            s = dsp.bandpass(rng.standard_normal(m), 1200, 6000, 2) * env * 0.35 * ref
            s = dsp.peq(s, 2600, 2, 6)
        dsp.place(y, s, a)
    y = dsp.lowpass(y, 2800, 2)
    ir = dsp.make_ir(1.3, rng, predelay=0.02, lp_hz=2500)
    y = dsp.reverb(y, ir, wet=0.65, dry=0.5)
    y = dsp.highpass(y, 120, 2)
    return dsp.trim(y, -60)


# --------------------------------------------------------------------------
# ambient.clock_chime (~8 s)
# --------------------------------------------------------------------------
def clock_chime(rng, i=1):
    dur = 8.5
    n = ns(dur)
    y = np.zeros(n)
    # mechanism whirr + release
    m = ns(0.45)
    wh = ins.friction_pulses(0.45, rng, np.linspace(160, 90, m), dsp.env_pts(m, [(0, 0), (0.05, 1), (0.4, 0.8), (0.45, 0)]), jitter=0.1)
    wh = dsp.resbank(wh, [(1800, 8, 1), (2900, 10, 0.6), (4100, 12, 0.4)])
    dsp.place(y, wh / (np.max(np.abs(wh)) + 1e-9) * 0.08, 0.0)
    f0 = 128.0
    ratios = [1.0, 2.02, 2.73, 3.95, 5.38, 6.9, 8.6]
    amps = [1.0, 0.5, 0.55, 0.3, 0.25, 0.12, 0.08]
    t60s = [7.5, 5.0, 4.0, 3.0, 2.0, 1.4, 1.0]
    for k, t0 in enumerate([0.5, 2.05, 3.6, 5.15]):
        g = ins.bell_partials(f0, dur - t0, rng, ratios, amps, t60s, beat=(0.4, 2.2), attack=0.002)
        L = ns(0.004)
        g[:L] += dsp.bandpass(rng.standard_normal(L), 2000, 6000) * 0.4
        dsp.place(y, g * (1.0 if k < 3 else 0.95), t0)
    # pendulum tick-tock
    for k, t0 in enumerate(np.arange(0.0, dur, 0.5)):
        tk = ins.wood_hit(rng, ins.CERAMIC, size=1.3 if k % 2 else 1.45, hardness=0.9, vel=0.025, dur=0.08)
        dsp.place(y, tk, t0 + 0.12)
    ir = dsp.make_ir(1.3, rng, predelay=0.01)
    y = dsp.reverb(y, ir, wet=0.35)
    y = dsp.highpass(y, 50, 2)
    return dsp.fade(dsp.pad_to(y, n), 0, 1.5)


# --------------------------------------------------------------------------
# ambient.temple_bell - distant bonsho (~10 s)
# --------------------------------------------------------------------------
def temple_bell(rng, i=1):
    dur = 10.0
    n = ns(dur)
    b = ins.bonsho(92.0, dur, rng)
    b = dsp.lowpass(b, 2200, 2)
    y = dsp.place(np.zeros(n), b, 0.05)
    ir = dsp.make_ir(4.5, rng, predelay=0.09, lp_hz=3000, hf_ratio=0.3)
    y = dsp.reverb(y, ir, wet=0.85, dry=0.4)
    y = dsp.highpass(y, 35, 2)
    return dsp.fade(dsp.pad_to(y, n), 0.0, 2.0)


# --------------------------------------------------------------------------
# ambient.drip_1..4
# --------------------------------------------------------------------------
def _drip(rng, f0, vel=1.0):
    tau = rng.uniform(0.012, 0.025)
    m = ns(0.12)
    t = np.arange(m) / SR
    f = f0 * (1 + 1.6 * (1 - np.exp(-t / (tau * 1.5))))
    y = np.sin(dsp.TAU * np.cumsum(f) / SR) * np.exp(-t / tau) * np.minimum(1, t / 0.0008)
    L = ns(0.004)
    y[:L] += dsp.highpass(rng.standard_normal(L), 3000) * 0.035
    return y * vel


def drip(rng, i=1):
    dur = 1.8
    y = np.zeros(ns(dur))
    f0 = {1: 820, 2: 1150, 3: 640, 4: 980}[i] * rng.uniform(0.95, 1.05)
    dsp.place(y, _drip(rng, f0), 0.03)
    if i == 4:
        dsp.place(y, _drip(rng, f0 * 1.12, 0.6), 0.48)
    ir = dsp.make_ir(1.5, rng, predelay=0.012, lp_hz=6000)
    y = dsp.reverb(y, ir, wet=0.45)
    return dsp.trim(dsp.highpass(y, 200, 2), -60)


# --------------------------------------------------------------------------
# ambient.scratch_1..3 - nails behind the wall / on the shoji
# --------------------------------------------------------------------------
def _stroke(rng, d, paper=False, press=1.0):
    m = ns(d)
    env = dsp.env_pts(m, [(0, 0), (0.04, 1.0), (d * 0.7, 0.8), (d, 0)]) * (1 + 0.3 * dsp.smooth(m, rng, 8.0))
    rate = rng.uniform(70, 160) * (1 + 0.3 * dsp.smooth(m, rng, 3.0))
    sp = ins.friction_pulses(d, rng, rate, env * press, jitter=0.35)
    if paper:
        nz = dsp.bandpass(rng.standard_normal(m), 1800, 8000, 2)
        y = nz * env * (0.4 + 0.6 * dsp.lowpass(np.abs(sp), 300, 1) / (np.max(np.abs(sp)) + 1e-9) * 6)
        y += ins.crackle(d, rng, 400, env, hp=2500) * 0.3
    else:
        nz = dsp.bandpass(rng.standard_normal(m), 900, 5000, 2)
        body = dsp.resbank(sp, [(f, 12, g) for f, _, g in ins.WOOD_DOOR])
        y = nz * env * 0.5 + body / (np.max(np.abs(body)) + 1e-9) * 0.7
    return y


def scratch(rng, i=1):
    if i == 1:
        dur, strokes = 3.0, [(0.05, 0.55), (0.85, 0.5), (1.6, 0.65)]
    elif i == 2:
        dur, strokes = 2.6, [(0.05, 2.1)]
    else:
        dur, strokes = 2.8, [(0.05, 0.45), (0.6, 0.4), (1.15, 0.5), (1.8, 0.7)]
    n = ns(dur)
    y = np.zeros(n)
    for t0, d in strokes:
        dsp.place(y, _stroke(rng, d, paper=(i == 3), press=rng.uniform(0.7, 1.0)), t0)
    if i != 3:  # behind a wall
        y = dsp.lowpass(y, 2200, 4)
        y = dsp.peq(y, 260, 1.5, 6.0)
    ir = dsp.make_ir(0.5, rng, predelay=0.004, lp_hz=5000)
    y = dsp.reverb(y, ir, wet=0.2)
    return dsp.trim(dsp.highpass(y, 80, 2), -60)


# --------------------------------------------------------------------------
# ambient.thud_1..3 - heavy muffled thud
# --------------------------------------------------------------------------
def _thud(rng, vel=1.0):
    y = ins.wood_hit(rng, ins.WOOD_FLOOR, size=2.4, hardness=0.2, vel=1.0, dur=0.9, noise=0.3)
    m = ns(0.6)
    t = np.arange(m) / SR
    sub = np.sin(dsp.TAU * np.cumsum(38 + 30 * np.exp(-t / 0.04)) / SR) * dsp.env_ad(m, 0.003, 0.12)
    nz = dsp.lowpass(rng.standard_normal(m), 250, 2) * dsp.env_ad(m, 0.002, 0.06)
    return dsp.mix(y * 0.8, sub * 1.1, nz * 0.6) * vel


def thud(rng, i=1):
    dur = 2.5
    y = np.zeros(ns(dur))
    dsp.place(y, _thud(rng), 0.03)
    if i == 2:
        dsp.place(y, _thud(rng, 0.45), 0.33)
    if i == 3:
        for k in range(6):
            dsp.place(y, ins.wood_hit(rng, ins.WOOD_DOOR, size=0.5, hardness=0.8, vel=0.12 * 0.8 ** k, dur=0.15),
                      0.06 + k * rng.uniform(0.03, 0.08))
    y = dsp.lowpass(y, 650, 3)
    ir = dsp.make_ir(1.0, rng, predelay=0.015, lp_hz=1000)
    y = dsp.reverb(y, ir, wet=0.55, dry=0.75)
    return dsp.trim(dsp.highpass(y, 28, 2), -60)


# --------------------------------------------------------------------------
# ambient.tinnitus (4 s)
# --------------------------------------------------------------------------
def tinnitus(rng, i=1):
    dur = 4.0
    n = ns(dur)
    t = np.arange(n) / SR
    env = dsp.env_pts(n, [(0, 0), (0.35, 1.0), (2.4, 0.85), (4.0, 0.0)], shape="lin") ** 1.3
    f1 = 7350 * (1 + 0.0015 * dsp.smooth(n, rng, 0.8))
    y = np.sin(dsp.TAU * np.cumsum(f1) / SR) * (1 + 0.08 * dsp.smooth(n, rng, 2.0))
    y += 0.22 * np.sin(dsp.TAU * np.cumsum(f1 * 1.2466) / SR)
    y += 0.06 * dsp.bandpass(rng.standard_normal(n), 6000, 8500, 2)
    return dsp.fade(y * env, 0.01, 0.05)
