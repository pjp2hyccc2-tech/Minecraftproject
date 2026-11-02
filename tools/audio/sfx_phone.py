"""phone.* - the smartphone (UI, chimes, ringtones, the line) and the old
public phone in the abandoned hamlet."""
import numpy as np

import dsp
import instruments as ins
import voice as V
from dsp import SR, TAU, ns


def speaker(x, lo=480.0, hi=9500.0, drive=1.3):
    """Tiny phone loudspeaker: no bass, a 2.8 kHz bump, slight saturation."""
    y = dsp.highpass(x, lo, 4)
    y = dsp.lowpass(y, hi, 2)
    y = dsp.peq(y, 2800, 1.0, 4.0)
    pk = np.max(np.abs(y)) + 1e-12
    return dsp.sat(y / pk * drive, 1.0) * pk / drive


def line(x, rng, crush=True):
    """Telephone line / earpiece: 300-3400 Hz, 8 kHz sample-and-hold
    (aliasing grit) and mu-law-ish quantisation."""
    y = dsp.bandpass(x, 300, 3400, 3)
    if crush:
        pk = np.max(np.abs(y)) + 1e-12
        z = y / pk
        mu = 255.0
        c = np.sign(z) * np.log1p(mu * np.abs(z)) / np.log1p(mu)
        c = np.round(c * 31) / 31
        z = np.sign(c) * ((1 + mu) ** np.abs(c) - 1) / mu
        idx = (np.arange(len(z)) // 5) * 5
        y = z[idx] * pk
        y = dsp.bandpass(y, 300, 3600, 2)
    return y


def _st(x, width=0.003, rng=None):
    d = ns(width)
    return np.vstack([x, np.concatenate([np.zeros(d), x[:-d]]) if d > 0 else x])


def _fin_s(x, hp=60.0):
    return dsp.trim(dsp.highpass(x, hp, 2), -64)


def _chime_note(rng, f, vel=1.0, dur=0.9):
    n = ns(dur)
    t = np.arange(n) / SR
    y = np.sin(TAU * f * t) * np.exp(-t / 0.28) + 0.25 * np.sin(TAU * 2.0 * f * t) * np.exp(-t / 0.12)
    y += 0.12 * np.sin(TAU * 3.01 * f * t) * np.exp(-t / 0.05)
    y *= np.minimum(1.0, t / 0.002)
    return dsp.fade(y, 0.0, 0.05) * vel


CHIME = [(0.0, 1318.5, 0.8), (0.11, 1661.2, 0.85), (0.22, 1975.5, 1.0)]  # E6 G#6 B6


def _chime(rng, notes=CHIME, rate=1.0, cents=0.0, wobble=0.0):
    y = np.zeros(ns(1.6 / rate))
    for t0, f, v in notes:
        nt = _chime_note(rng, f * dsp.cents(cents + rng.normal(0, 3)), v, dur=1.0 / rate)
        if rate != 1.0 or wobble:
            m = len(nt)
            rr = rate * dsp.cents(wobble * np.sin(TAU * 3.0 * np.arange(m) / SR))
            nt = dsp.varispeed(nt, rr)
        dsp.place(y, nt, t0 / rate)
    return y


# --------------------------------------------------------------------------
def tap(rng, i=1):
    f = 1750.0 if i == 1 else 2250.0
    n = ns(0.08)
    t = np.arange(n) / SR
    y = np.sin(TAU * f * t) * np.exp(-t / 0.006) * 0.8
    m = ns(0.002)
    y[:m] += dsp.highpass(rng.standard_normal(m), 3000, 2) * 0.4
    return _fin_s(_st(speaker(y, drive=1.0)) * 0.9)


def unlock(rng, i=1):
    y = np.zeros(ns(0.4))
    m = ns(0.14)
    tt = np.arange(m) / SR
    sw = dsp.tv_filter(rng.standard_normal(m), 800 * 2 ** (tt / 0.14 * 2.5), 2.0, "bp") * np.sin(np.pi * tt / 0.14) ** 2
    dsp.place(y, sw * 0.12, 0.0)
    for t0, f in ((0.03, 2100.0), (0.13, 2800.0)):
        n = ns(0.06)
        t = np.arange(n) / SR
        dsp.place(y, np.sin(TAU * f * t) * np.exp(-t / 0.008), t0)
    return _fin_s(_st(speaker(y, drive=1.0)))


def vibrate(rng, i=1):
    dur = 2.0
    n = ns(dur)
    t = np.arange(n) / SR
    gate = np.zeros(n)
    for a, b in ((0.0, 0.42), (0.68, 1.1), (1.36, 1.78)):
        gate[ns(a):ns(b)] = 1.0
    gate = dsp.lowpass0(gate, 60.0)
    f = 172.0 * (1 + 0.04 * dsp.lowpass0(np.diff(gate, prepend=0) > 0, 5.0))
    ph = np.cumsum(f) / SR
    motor = np.sin(TAU * ph) + 0.5 * np.sin(2 * TAU * ph) + 0.3 * np.sign(np.sin(TAU * ph)) * 0.3
    rattle = dsp.bandpass(rng.standard_normal(n), 600, 3500, 2) * (0.5 + 0.5 * np.sin(TAU * ph)) ** 8
    y = (motor * 0.8 + rattle * 0.5) * gate
    y = dsp.lowpass(y, 2500, 2)  # through cloth
    y = dsp.reverb(y, dsp.make_ir(0.3, rng, predelay=0.002), wet=0.1)
    return dsp.fade(dsp.pad_to(dsp.highpass(y, 60, 2), n), 0.0, 0.05)


def notify(rng, i=1):
    y = _chime(rng)
    y = dsp.reverb(y, dsp.make_ir(0.4, rng, predelay=0.003), wet=0.12)
    return _fin_s(_st(speaker(y)))


def notify_wrong(rng, i=1):
    notes = [(0.0, 1318.5, 0.8), (0.22, 1975.5 * dsp.cents(-38), 1.0)]  # the middle note is missing
    y = _chime(rng, notes, rate=0.7, cents=-45, wobble=22)
    ghost = dsp.reverse(_chime(rng, notes[:1], rate=0.6, cents=-60))
    out = np.zeros(len(y) + ns(0.6))
    dsp.place(out, ghost * 0.25, 0.0)
    dsp.place(out, y, 0.35)
    out = dsp.reverb(out, dsp.make_ir(1.2, rng, predelay=0.01), wet=0.3)
    return _fin_s(_st(speaker(out), 0.006))


def signal(rng, i=1):
    dur = 3.2
    n = ns(dur)
    y = np.zeros(n)
    t = 0.05
    k = 0
    while t < 2.4:
        y_ = _chime(rng, cents=rng.choice([0, 0, 0, -200, 300]))
        dsp.place(y, y_ * rng.uniform(0.55, 1.0), t)
        t += max(0.06, 0.35 * 0.82 ** k) * rng.uniform(0.8, 1.2)
        k += 1
    buzz = vibrate(rng)[: ns(2.0)] * 0.25
    dsp.place(y, buzz, 0.1)
    y = y / (np.max(np.abs(y)) + 1e-9)
    y = dsp.reverb(y, dsp.make_ir(0.5, rng, predelay=0.003), wet=0.15)
    return dsp.fade(_st(speaker(dsp.pad_to(y, n))), 0.0, 0.4)


RING = [  # (beat, note) at 120 bpm, 8 s = 16 beats; C major pentatonic, cheerful
    (0.0, "E5"), (0.5, "G5"), (1.0, "A5"), (1.5, "C6"), (2.0, "A5"), (2.5, "G5"), (3.0, "E5"), (3.5, "G5"),
    (4.0, "A5"), (5.0, "C6"), (5.5, "D6"), (6.0, "C6"), (6.5, "A5"),
    (8.0, "G5"), (8.5, "A5"), (9.0, "C6"), (9.5, "D6"), (10.0, "E6"), (10.5, "D6"), (11.0, "C6"), (11.5, "A5"),
    (12.0, "C6"), (14.0, "G5"),
]
RING_BASS = [(0.0, "C4"), (4.0, "A3"), (8.0, "F3"), (12.0, "G3")]


def _ring_notes(rng, L=8.0, wrong=False):
    beat = L / 16.0
    n = ns(L)
    y = np.zeros(n)
    for k, (b, nm) in enumerate(RING + RING_BASS):
        bass = (b, nm) in RING_BASS
        f = dsp.hz(nm)
        v = 0.55 if bass else rng.uniform(0.8, 1.0)
        t0 = b * beat
        if wrong:
            if k % 7 == 3 and not bass:
                continue  # missing notes
            drift = 70 * np.sin(TAU * t0 / L + 0.6) + rng.uniform(-30, 30)
            f *= dsp.cents(drift - 25)
            t0 = t0 + 0.18 * np.sin(TAU * t0 / L)  # the time warp is periodic -> still loops
        nt = ins.marimba(f, rng, vel=v, hard=0.5 if not bass else 0.3)
        if wrong and k % 3 == 1:
            nt = dsp.reverse(nt)
            t0 = max(0.0, t0 - len(nt) / SR + 0.05)
        dsp.place(y, nt, t0, circular=True)
    return y


def ringtone(rng, i=1):
    L = 8.0
    y = _ring_notes(rng, L)
    y = dsp.circ(lambda x: speaker(x), y, ns(1.0))
    return np.roll(dsp.dc_block(y, circular=True), ns(0.01))


def ringtone_wrong(rng, i=1):
    L = 8.0
    n = ns(L)
    y = _ring_notes(rng, L, wrong=True)
    y = dsp.circ(lambda x: dsp.lowpass(x, 5000, 2), y, ns(0.5))
    q = np.round(y / (np.max(np.abs(y)) + 1e-9) * 48) / 48 * (np.max(np.abs(y)))
    y = 0.75 * y + 0.25 * q
    out = np.vstack([y, np.roll(y, ns(0.011))])
    ir = dsp.make_ir(1.4, rng, stereo=True, predelay=0.02)
    out = dsp.circ_reverb(out, ir, wet=0.35, dry=0.85)
    t = np.arange(n) / SR
    out *= (0.85 + 0.15 * np.cos(TAU * 2 * t / L))[None, :]
    return np.roll(dsp.dc_block(dsp.circ(lambda x: speaker(x, lo=350, drive=1.2), out, ns(0.5)), circular=True), ns(0.01), axis=-1)


def ring_old(rng, i=1):
    L = 6.0
    y = ins.phone_bell(rng, L, [(0.05, 1.05), (3.05, 4.05)], rate=16.0)  # Japanese cadence: 1 s ring, 2 s silence
    y = dsp.circ(lambda x: dsp.peq(x, 900, 1.0, 3.0), y, ns(0.5))
    y = dsp.circ(lambda x: dsp.highpass(x, 200, 2), y, ns(0.5))
    ir = dsp.make_ir(0.6, rng, predelay=0.004)
    y = dsp.circ_reverb(y, ir, wet=0.2, dry=0.95)
    return dsp.dc_block(y, circular=True)


def _garble(rng, dur, male=True, f0=None):
    scripts = ["mo shi mo shi | ki ko e ma su ka", "so ko ni | i ru N de su ka", "a no | ya ma wa | ne", "ma da | so ko ni"]
    y = np.zeros(ns(dur))
    t = rng.uniform(0.05, 0.3)
    while t < dur - 0.4:
        s = V.speak(scripts[rng.integers(len(scripts))], rng, table=V.VM if male else V.VF, f0=f0 or (112 if male else 210),
                    rate=rng.uniform(1.0, 1.25), breath=0.15, tilt=-7)
        L_ = min(len(s), ns(rng.uniform(0.5, 1.4)))
        frag = s[:L_] * dsp.env_pts(L_, [(0, 0), (0.03, 1), (L_ / SR - 0.05, 1), (L_ / SR, 0)])
        dsp.place(y, frag, t)
        t += L_ / SR + rng.uniform(0.15, 0.5)
    return y


def _packets(y, rng, loss=0.15, frame=0.02):
    """Codec packet loss: frames repeated (stutter) or dropped."""
    f = ns(frame)
    out = y.copy()
    for k in range(1, len(y) // f):
        r = rng.random()
        if r < loss * 0.6:
            out[k * f:(k + 1) * f] = out[(k - 1) * f:k * f]
        elif r < loss:
            out[k * f:(k + 1) * f] *= 0.05
    return out


def call_static(rng, i=1):
    L = 6.0
    n = ns(L)
    t = np.arange(n) / SR
    st = dsp.colored(n, rng, -1.0, lo=300, hi=3500) * (0.6 + 0.4 * np.abs(dsp.smooth(n, rng, 5.0)))
    st += ins.crackle(L, rng, 50, hp=800) * 0.5
    hum = sum(a * np.sin(TAU * dsp.loop_freq(f, L) * t) for f, a in ((50, 1.0), (150, 0.5), (250, 0.25)))
    v = np.zeros(n)
    g = _garble(rng, 5.0, male=True)
    g = _packets(g, rng, 0.25)
    dsp.place(v, g, 0.5, circular=True)
    for _ in range(5):  # digital bursts
        m = ns(rng.uniform(0.03, 0.12))
        b = np.sin(TAU * np.cumsum(np.linspace(rng.uniform(500, 3000), rng.uniform(500, 3000), m)) / SR) * dsp.env_ad(m, 0.002, 0.03)
        dsp.place(v, b * 0.4, rng.uniform(0, L), circular=True)
    y = st / (np.std(st) + 1e-9) * 0.2 + hum * 0.02 + v / (np.max(np.abs(v)) + 1e-9) * 0.5
    y = dsp.circ(lambda x: line(x, rng), y, ns(0.3))
    out = np.vstack([y, y])
    return dsp.dc_block(out, circular=True)


def voice(rng, i=1):
    dur = {1: 2.6, 2: 3.2, 3: 2.2}[i]
    if i == 1:
        g = _garble(rng, dur, male=True)
    elif i == 2:
        g = _garble(rng, dur, male=False)
    else:  # far too low, slowed
        g = dsp.varispeed(_garble(rng, dur * 0.7, male=True, f0=95), 0.7)
    g = _packets(g, rng, 0.2 + 0.1 * i)
    nz = dsp.colored(len(g), rng, -1.0, lo=300, hi=3500) * 0.04 * np.std(g) / 0.1
    y = line(g + nz * np.max(np.abs(g)), rng)
    return _fin_s(_st(y, 0.0) * 1.0, 200)


def hangup(rng, i=1):
    dur = 3.0
    n = ns(dur)
    t = np.arange(n) / SR
    gate = ((t % 1.0) < 0.5).astype(float)
    gate = dsp.lowpass0(gate, 200.0)
    y = np.sin(TAU * 400 * t) * gate
    y = line(y, rng)
    return dsp.fade(np.vstack([y, y]), 0.002, 0.02)


def shutter(rng, i=1):
    """The forced Japanese phone shutter sound: ka-sha."""
    y = np.zeros(ns(0.4))
    for t0, f, g in ((0.01, 2600, 1.0), (0.1, 1900, 0.8)):
        m = ns(0.03)
        c = rng.standard_normal(m) * np.exp(-np.arange(m) / (0.002 * SR))
        c = dsp.reson(c, f, 3.0, 2.0) + dsp.highpass(c, 3000, 2) * 0.6
        dsp.place(y, c * g, t0)
    m = ns(0.07)
    sh = dsp.bandpass(rng.standard_normal(m), 2500, 9000, 2) * dsp.env_pts(m, [(0, 0), (0.01, 1), (0.07, 0)])
    dsp.place(y, sh * 0.35, 0.025)
    return _fin_s(_st(speaker(y, drive=1.1)), 300)


def low_battery(rng, i=1):
    y = np.zeros(ns(0.6))
    for t0, f in ((0.01, 1046.5), (0.22, 784.0)):
        m = ns(0.16)
        t = np.arange(m) / SR
        b = (np.sin(TAU * f * t) + 0.2 * np.sign(np.sin(TAU * f * t)) * 0.3) * dsp.env_pts(m, [(0, 0), (0.005, 1), (0.13, 1), (0.16, 0)])
        dsp.place(y, b, t0)
    return _fin_s(_st(speaker(y, drive=1.0)), 200)


def dead(rng, i=1):
    dur = 1.6
    n = ns(dur)
    t = np.arange(n) / SR
    f = 1200 * 2 ** (-np.clip(t / 0.9, 0, 1) * 2.6)
    y = (np.sin(TAU * np.cumsum(f) / SR) + 0.3 * np.sin(2 * TAU * np.cumsum(f) / SR)) * dsp.env_pts(n, [(0, 0), (0.02, 1), (0.8, 0.6), (0.95, 0.0), (dur, 0)])
    m = ns(0.01)
    dsp.place(y, rng.standard_normal(m) * np.exp(-np.arange(m) / (0.001 * SR)) * 0.3, 0.97)
    return _fin_s(_st(speaker(y, drive=1.2)), 150)


def mitsuketa(rng, i=1):
    """phone.mitsuketa (extra) - 「みつけた」 whispered from the phone's speaker
    in a dead zone: tiny speaker, a codec stutter on the last syllable."""
    w = V.whisper("mi tsu ke ta -", rng, scale=1.08, rate=0.8, close=0.5, bw=1.25, voicing=0.1, f0=230)
    w = w / (np.max(np.abs(w)) + 1e-9)
    k = int(len(w) * 0.78)
    seg = w[k:k + ns(0.03)]
    w = np.concatenate([w[:k]] + [seg * (0.9 ** j) for j in range(4)] + [w[k:]])
    y = speaker(w, lo=600, hi=8000, drive=1.6)
    y = y + dsp.colored(len(y), rng, -1.0, lo=500, hi=7000) * 0.01
    return _fin_s(_st(y, 0.0), 300)
