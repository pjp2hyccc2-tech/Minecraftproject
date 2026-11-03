"""Voices: the player's ritual lines (stereo, your own voice inside your head), the counting, the doll's lines and
giggles, the woman's whisper, your name whispered at the fusuma.

All formant-synthesised (voice.py) with explicit Tokyo-accent intonation so the Japanese stays recognisable; the
subtitles carry the words.  The player is a young woman speaking quietly in the dark, deliberately, trying to keep
her voice steady: a hushed voice (the voiced line and a whisper of the same syllables from one timeline), a slight
tremor that grows over the night.  The doll speaks with a child's voice that is wrong - too slow, too flat, an
octave lower voice inside it, muffled by soaked cloth, water in its throat."""
import numpy as np

import dsp
import kit as K
import voice as V
from dsp import SR, TAU, ns

PLAYER = dict(table=V.VF, scale=1.0, f0=208.0)
CHILD = dict(table=V.VF, scale=1.24, f0=330.0)
WOMAN = dict(table=V.VF, scale=0.98, f0=190.0)


def _line(script, rng, pitch, durs, who=PLAYER, hush=0.45, breath=0.45, tilt=-8.5, jitter=0.014, shimmer=0.07,
          tremor=6.0, trem_rate=6.3, pauses=None, gains=None, wander=0.25, smooth_hz=12.0, tempo=1.0, cons=1.0,
          slur=0.0, f0_scale=1.0):
    """A line spoken quietly: voiced harmonics + a whisper of the same syllables (vocal-tract noise on the same
    timeline) + consonant noises.  pitch: semitones above f0 per syllable token (or (start, end) glides).
    Returns (mono signal, f0 track)."""
    table, scale, f0 = who["table"], who["scale"], who["f0"] * f0_scale
    tl = V.timeline(script, rng, table, scale, tempo, bw=1.0, f1=1.0, voiced=True, slur=slur, durs=durs, gains=gains,
                    pauses=pauses)
    n = ns(tl["t_end"])
    vamp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["vamp"], key=lambda p: p[0])), 45.0), 0)
    namp = np.maximum(dsp.lowpass0(dsp.env_pts(n, sorted(tl["namp"], key=lambda p: p[0])), 45.0), 0)
    pts = []
    for k, (ta, tb, w, v) in enumerate(tl["syl"]):
        p = pitch[k]
        a, b = (float(p), float(p)) if np.isscalar(p) else (float(p[0]), float(p[1]))
        pts.append((ta + 0.01, a))
        pts.append((max(ta + 0.02, tb - 0.01), b))
    pts = [(0.0, pts[0][1])] + pts + [(tl["t_end"], pts[-1][1])]
    st = dsp.lowpass0(dsp.env_pts(n, sorted(pts, key=lambda q: q[0])), smooth_hz)
    st = st + wander * dsp.smooth(n, rng, 3.0, periodic=False)
    ff = f0 * 2.0 ** (st / 12.0)
    if tremor > 0:  # nervous tremor: an irregular vibrato
        rate = trem_rate * (1 + 0.15 * dsp.smooth(n, rng, 1.0, periodic=False))
        ff = ff * dsp.cents(tremor * np.sin(TAU * np.cumsum(rate) / SR) + tremor * 0.6 * dsp.smooth(n, rng, 9.0, periodic=False))
    y = V.voice(ff, vamp, tl["vkeys"], rng, tilt=tilt, fmax=7500, jitter=jitter, shimmer=shimmer, breath=breath,
                loud_comp=0.75)
    if hush > 0:
        wh = V.vt_noise(n, tl["vkeys"], vamp, rng, tilt_db=1.0, bw=1.25, hp=300.0)
        y = y + wh * np.std(y) / (np.std(wh) + 1e-9) * hush
    if namp.max() > 0:
        y = y + V.vt_noise(n, tl["vkeys"], namp, rng, tilt_db=1.0, bw=1.4) * np.std(y) * 0.9
    y = y + V.consonants(n, tl["cons"], rng, scale, 1.0) * np.std(y) * 2.2 * cons
    return y, ff


def _inhale(rng, d=0.35, who=PLAYER, level=0.35, tremor=0.3):
    return V.breath(rng, "in", d, table=who["table"], scale=who["scale"], tremor=tremor, trem_rate=9.0) * level


def _head(y, rng, room=0.05):
    """Your own voice as you hear it: dry and close, warm with bone conduction, centred, a hint of the small
    tatami room at night."""
    y = dsp.highpass(y, 90, 2)
    y = dsp.peq(y, 280, 1.0, 3.0)        # bone conduction warmth
    y = dsp.peq(y, 2800, 0.9, 3.5)       # presence: keep the consonants and F2/F3 legible
    y = dsp.shelf(y, 8000, -2.5, "high")
    y = K.rev(y, rng, 0.35, room, hf=0.4)
    st = np.vstack([y, np.concatenate([np.zeros(ns(0.0003)), y[:len(y) - ns(0.0003)]])])
    return dsp.trim(st, -62)


def _seq(parts, tail=0.4):
    """Concatenate (signal, gap_after) parts."""
    total = sum(len(p) for p, g in parts) + sum(ns(g) for p, g in parts) + ns(tail)
    y = np.zeros(total)
    i = 0
    for p, g in parts:
        y[i:i + len(p)] += p
        i += len(p) + ns(g)
    return y


def _norm_to(x, ref):
    return x * (np.std(ref) / (np.std(x) + 1e-12))


# ==========================================================================
# the player's ritual lines (stereo)
# ==========================================================================
def first_oni(rng, i=1):
    """「最初の鬼は、わたしだから」 - さ↗いしょの お↗に↘は、わ↗たしだ↘から: slow, deliberate, steadying herself."""
    script = "sa i sho no o ni wa , wa ta shi da ka ra"
    pitch = [0, 4, 4.5, 4, 3, 4.5, (1.5, -0.5), 0.5, 3.5, 3.5, 3, 1.5, (0, -2)]
    durs = [0.11, 0.09, 0.12, 0.1, 0.11, 0.1, 0.17, 0.1, 0.09, 0.07, 0.1, 0.1, 0.24]
    v, _ = _line(script, rng, pitch, durs, pauses={7: 0.34}, tremor=5.0)
    b = _inhale(rng, 0.4)
    y = _seq([(_norm_to(b, v) * 0.35, 0.05), (v, 0.0)])
    return _head(y, rng)


def mitsuketa(rng, i=1):
    """「みーちゃん、みつけた」 - the knife in her hand; the name almost tender, the rest flat and quick."""
    script = "mi - cha N , mi tsu ke ta"
    pitch = [3.5, 2.5, 0.5, 4, 4, (3, 0)]
    durs = [0.2, 0.12, 0.1, 0.09, 0.1, 0.22]
    v, _ = _line(script, rng, pitch, durs, pauses={4: 0.42}, tremor=8.0, hush=0.6, breath=0.55)
    b = _inhale(rng, 0.3, tremor=0.5)
    y = _seq([(_norm_to(b, v) * 0.45, 0.04), (v, 0.0)])
    return _head(y, rng)


def next_oni(rng, i=1):
    """「次は、みーちゃんが鬼だから」 - つ↗ぎは、み↗ーちゃんが お↗に↘だから: said quickly, already backing away."""
    script = "tsu gi wa , mi - cha N ga o ni da ka ra"
    pitch = [1, 4.5, (2, 1), 1, 3.5, 3, 3, 4.5, 2, 1.5, (0.5, -1.5)]
    durs = [0.08, 0.1, 0.13, 0.12, 0.09, 0.08, 0.09, 0.09, 0.09, 0.08, 0.2]
    v, _ = _line(script, rng, pitch, durs, pauses={3: 0.26}, tremor=9.0, tempo=0.92, hush=0.55)
    return _head(_seq([(v, 0.0)]), rng)


def watashi_kachi(rng, i=1):
    """「わたしの勝ち」 - わ↗たしの か↘ち: firmer, the salt still on her tongue, her voice shaking under it."""
    script = "wa ta shi no ka chi"
    pitch = [0.5, 4.5, 4.5, 4.5, 5, (1.5, -0.5)]
    durs = [0.11, 0.1, 0.07, 0.11, 0.13, 0.22]
    v, _ = _line(script, rng, pitch, durs, tremor=10.0, hush=0.35, breath=0.4, tilt=-9.0, f0_scale=1.04)
    b = _inhale(rng, 0.32, tremor=0.4)
    y = _seq([(_norm_to(b, v) * 0.4, 0.03), (v, 0.0)])
    return _head(y, rng)


def count(rng, i=1):
    """「いち、に、さん、し、ご、ろく、なな、はち、きゅう、じゅう」 eyes shut at the TV: about one number a second,
    murmured, tighter and higher as it goes, a breath snatched between two of them."""
    script = "i chi , ni , sa N , shi , go , ro ku , na na , ha chi , kyu - , ju -"
    pitch = [(2.5, 1.5), 0, (2.5, 0), (2.5, 2), (2.5, 0.5), (2.5, 0.5), (3, 1.5), 0.5, (3, 2.5), 1, (3.5, 2), 1,
             (4, 1.5), (4, 0)]
    durs = [0.12, 0.08, 0.2, 0.13, 0.17, 0.16, 0.12, 0.08, 0.11, 0.1, 0.11, 0.08, 0.2, 0.2]
    # the parse: i chi , ni , sa N , shi , go , ro ku , na na , ha chi , kyu - , ju -
    # token index of each ',' -> pause so that the numbers fall about a second apart
    toks = V.parse(script)
    commas = [k for k, t in enumerate(toks) if t[0] == ","]
    gaps = [0.74, 0.8, 0.72, 0.78, 0.74, 0.7, 0.78, 0.67, 0.72]
    v, ff = _line(script, rng, pitch, durs, pauses=dict(zip(commas, gaps)), tremor=7.0, hush=0.75, breath=0.6,
                  tilt=-10.0, wander=0.35)
    n = len(v)
    rise = np.linspace(0, 1, n)  # tighter as it goes: a little louder, more tremor in the level
    v = v * (0.85 + 0.25 * rise) * (1 + 0.12 * rise * np.sin(TAU * 7.0 * np.arange(n) / SR))
    y = np.zeros(n + ns(0.6))
    K.place(y, v, 0.25)
    b = _inhale(rng, 0.25, tremor=0.6)  # a breath snatched after 「ろく」
    K.place(y, _norm_to(b, v) * 0.4, 0.25 + 5.9)
    return _head(y, rng, 0.04)


# ==========================================================================
# the doll
# ==========================================================================
def _soaked(y, rng, wet=1.0, rice=0.15):
    """A voice coming out of a sodden plush body: the cloth eats the highs, the cavity rings, water gurgles in it,
    rice shifts with the effort of speaking (both follow the voice's own envelope)."""
    z = dsp.lowpass(y, 3600, 2)
    r = dsp.resbank(y, [(820, 5, 1.0), (1500, 6, 0.6), (2600, 7, 0.3)])
    out = z * 0.75 + r / (np.std(r) + 1e-9) * np.std(z) * 0.4
    n = len(out)
    env = dsp.lowpass0(np.abs(y), 12.0)
    env = np.clip(env / (np.max(env) + 1e-9) * 1.6, 0, 1)
    if wet > 0:
        g = K.bubbles(n / SR, rng, 140 * wet, (0.6, 2.4), env=env)
        out = out + g / (np.std(g) + 1e-9) * np.std(out) * 0.22 * wet
    if rice > 0:
        rc = K.rice_inside(n / SR, rng, env * 0.5, muffle=0.8)
        out = out + rc / (np.std(rc) + 1e-9) * np.std(out) * rice
    return out


def _wrong(make, rng, octave_gain=0.4, slow=0.88):
    """The child's voice made wrong: slowed down a little (pitch and time together, like a tape), with its own
    octave-down shadow inside it.  make(rng, f0_mult) renders the voice; the shadow is rendered from an identically
    seeded generator so its syllables line up exactly."""
    seed = int(rng.integers(1 << 31))
    a = make(np.random.default_rng(seed), 1.0)
    b = dsp.pad_to(make(np.random.default_rng(seed), 0.5), len(a))
    y = a + b * octave_gain * np.std(a) / (np.std(b) + 1e-9)
    return dsp.varispeed(y, slow)


def doll_mitsuketa(rng, i=1):
    """「みつけた」 - flat, slow, from inside the wet cloth; the last syllable sags like a tape running down."""
    def make(r, m):
        v, _ = _line("mi tsu ke ta", r, [2, 2.5, 2.5, (2, -1)], [0.24, 0.12, 0.2, 0.42], who=CHILD, hush=0.3,
                     breath=0.5, tilt=-9.0, tremor=0.0, wander=0.05, jitter=0.02, f0_scale=m)
        return v
    w = _wrong(make, rng, octave_gain=0.45, slow=0.9)
    n = len(w)
    w = dsp.varispeed(w, np.interp(np.arange(n), [0, n * 0.6, n], [1.0, 1.0, 0.8]))  # the end sags
    y = _soaked(w, rng, wet=1.0, rice=0.12)
    y = np.concatenate([np.zeros(ns(0.03)), y, np.zeros(ns(0.4))])
    return K.fin(K.rev(y, rng, 0.25, 0.08), 120)


def doll_doko(rng, i=1):
    """「どこ？」 - 1: sing-song, rising, too slow (どーこ？); 2: whispered right behind the fusuma."""
    if i == 1:
        def make(r, m):
            v, _ = _line("do - ko", r, [(2, 2), (2.5, 5), (5, 8.5)], [0.26, 0.22, 0.4], who=CHILD, hush=0.25,
                         breath=0.45, tilt=-8.5, tremor=0.0, wander=0.05, f0_scale=m)
            return v
        w = _wrong(make, rng, octave_gain=0.35, slow=0.85)
        y = _soaked(w, rng, wet=0.8, rice=0.1)
        y = np.concatenate([np.zeros(ns(0.03)), y, np.zeros(ns(0.5))])
        return K.fin(K.rev(y, rng, 0.3, 0.1), 120)
    w = V.whisper_line("do ko", rng, [0.2, 0.45], table=V.VF, scale=1.22, close=1.0, voicing=0.18, f0=330, bw=1.2)
    w = _soaked(w, rng, wet=0.6, rice=0.0)
    b = V.breath(rng, "in", 0.3, table=V.VF, scale=1.22)
    y = np.zeros(len(w) + ns(0.9))
    K.place(y, _norm_to(b, w) * 0.4, 0.0)
    K.place(y, w, 0.28)
    return K.fin(dsp.close_mic(y, 4.0, 3.0, 1.0), 150)


def doll_tsugi(rng, i=1):
    """「つぎは、あなたがおに」 - right at your face after it has opened the fusuma: a child's voice and the octave
    below it, slow, flat, perfectly calm; dry, then the room swallows the last word.  Stereo."""
    script = "tsu gi wa , a na ta ga o ni"
    pitch = [2, 3, (2, 1.5), 2, 2.5, 2.5, 2, 2, (2, -2)]
    durs = [0.12, 0.16, 0.24, 0.16, 0.14, 0.15, 0.14, 0.18, 0.6]
    def make(r, m):
        v, _ = _line(script, r, pitch, durs, who=CHILD, pauses={3: 0.55}, hush=0.3, breath=0.5, tilt=-9.0,
                     tremor=0.0, wander=0.05, tempo=1.15, f0_scale=m)
        return v
    w = _wrong(make, rng, octave_gain=0.6, slow=0.86)
    y = _soaked(w, rng, wet=0.5, rice=0.08)
    y = dsp.close_mic(dsp.highpass(y, 100, 2), 4.0, 2.5, 0.5)
    n = len(y)
    y = np.concatenate([y, np.zeros(ns(2.0))])
    tail = np.zeros_like(y)
    tail[n - ns(0.7):n] = y[n - ns(0.7):n] * np.linspace(0, 1, ns(0.7))
    ir = K.room(rng, 1.6, stereo=True, predelay=0.02, hf=0.4)
    rv = dsp.convolve(tail, ir)[:, :len(y)]
    st = np.vstack([y, np.roll(y, ns(0.0004))]) + rv * 0.35
    return dsp.trim(st, -62)


def doll_giggle(rng, i=1):
    """The doll's giggles, each wrong in its own way."""
    if i == 1:  # ふふふ, closed mouth, from inside the soaked cloth
        y = V.laugh(rng, n_syl=5, f0=350, decline=0.025, table=V.VF, scale=1.22, nasal=True, breath=1.2, tilt=-10)
        y = _soaked(y, rng, wet=1.0, rice=0.1)
    elif i == 2:  # くすくす: mostly breath, the octave shadow under it
        y = _wrong(lambda r, m: V.laugh(r, n_syl=7, f0=400 * m, decline=0.015, gap=(0.03, 0.06), syl=(0.06, 0.09),
                                        vowel="u", table=V.VF, scale=1.24, breath=2.4, tilt=-12, h_gain=2.0),
                   rng, octave_gain=0.3, slow=0.95)
        y = _soaked(y, rng, wet=0.4, rice=0.0)
    elif i == 3:  # a bright peal that runs down like a dying music box motor
        y = V.laugh(rng, n_syl=8, f0=430, decline=0.02, gap=(0.04, 0.07), syl=(0.07, 0.11), vowel="a", table=V.VF,
                    scale=1.24, breath=1.0, tilt=-8)
        n = len(y)
        y = dsp.varispeed(y, np.interp(np.arange(n), [0, n * 0.35, n], [1.0, 1.0, 0.55]))
        y = _soaked(y, rng, wet=0.5, rice=0.05)
    else:  # glitched: the same syllable caught and repeated, right next to you
        g = V.laugh(rng, n_syl=3, f0=390, decline=0.03, gap=(0.05, 0.07), syl=(0.07, 0.09), vowel="i", table=V.VF,
                    scale=1.24, breath=1.6, tilt=-10, h_gain=1.4)
        seg = g[ns(0.14):ns(0.24)].copy()
        seg *= np.hanning(len(seg)) ** 0.3
        y = np.zeros(len(g) + ns(0.8))
        K.place(y, g[:ns(0.24)], 0.0)
        for k in range(5):
            K.place(y, seg * (0.9 - 0.1 * k), 0.24 + k * 0.085)
        K.place(y, g[ns(0.24):], 0.24 + 5 * 0.085 + 0.03)
        y = dsp.close_mic(_soaked(y, rng, wet=0.3, rice=0.0), 3.0, 3.0, 2.0)
        return K.fin(y, 160)
    y = np.concatenate([np.zeros(ns(0.02)), y, np.zeros(ns(0.3))])
    return K.fin(K.rev(dsp.highpass(y, 160, 2), rng, 0.25, 0.1), 160)


# ==========================================================================
# the woman, the whispers
# ==========================================================================
def woman_whisper(rng, i=1):
    """「……そこにいるの」 - close to the microphone / the gap of the fusuma: 1 plain and slow, 2 half-voiced, low and
    slower, 3 a long breath in first, the last word trailing off with a wet click."""
    durs = [0.16, 0.15, 0.13, 0.12, 0.13, 0.42]
    if i == 1:
        w = V.whisper_line("so ko ni i ru no", rng, durs, table=V.VF, scale=0.98, close=0.8, bw=1.25, voicing=0.0)
        pre = 0.05
    elif i == 2:
        w = V.whisper_line("so ko ni i ru no", rng, [d * 1.35 for d in durs], table=V.VF, scale=0.95, close=0.6,
                           bw=1.3, voicing=0.3, f0=170)
        pre = 0.05
    else:
        w = V.whisper_line("so ko ni i ru no -", rng, durs + [0.5], table=V.VF, scale=0.98, close=1.0, bw=1.2,
                           voicing=0.1, f0=185)
        pre = 0.75
    y = np.zeros(len(w) + ns(pre + 0.6))
    if i == 3:
        b = V.breath(rng, "in", 0.7, table=V.VF, scale=0.98, tremor=0.2)
        K.place(y, _norm_to(b, w) * 0.5, 0.0)
        K.place(y, V.mouth_clicks(0.3, rng, rate=5.0) * np.std(w) * 3.0, pre + len(w) / SR - 0.1)
    K.place(y, w, pre)
    y = dsp.close_mic(dsp.highpass(y, 120, 2), 4.0, 3.0, 2.0)
    return K.fin(y, 120)


def _ear(mono, side, rng):
    """At one ear: the near ear loud and bright, the far ear behind the head (delayed, quieter, duller)."""
    near = mono
    far = dsp.lowpass(np.concatenate([np.zeros(ns(0.00065)), mono[:len(mono) - ns(0.00065)]]), 1800, 2) * 0.3
    return np.vstack([near, far]) if side < 0 else np.vstack([far, near])


def whisper_name(rng, i=1):
    """Your name, whispered at the gap of the fusuma: two smeared syllables nobody could write down, then a clear
    「……ちゃん」.  1: at the left ear; 2: at the right, slower, a breath first, a voice edge in it."""
    if i == 1:
        blur = V.whisper_line("a ka", rng, [0.14, 0.16], table=V.VF, scale=1.0, close=0.6, bw=1.5, voicing=0.0)
        blur = dsp.lowpass(blur, 2600, 2)  # smeared, as if through the paper
        chan = V.whisper_line("cha N", rng, [0.2, 0.12], table=V.VF, scale=1.0, close=1.0, bw=1.2)
        parts = [(blur, 0.06), (_norm_to(chan, blur) * 1.1, 0.0)]
        side, pre = -1, 0.05
    else:
        blur = V.whisper_line("ha ru", rng, [0.2, 0.22], table=V.VF, scale=0.97, close=0.6, bw=1.5, voicing=0.15, f0=180)
        blur = dsp.lowpass(blur, 2400, 2)
        chan = V.whisper_line("cha N -", rng, [0.26, 0.16, 0.3], table=V.VF, scale=0.97, close=1.0, bw=1.2, voicing=0.15, f0=175)
        parts = [(blur, 0.1), (_norm_to(chan, blur) * 1.1, 0.0)]
        side, pre = 1, 0.6
    w = _seq(parts, tail=0.5)
    y = np.zeros(len(w) + ns(pre))
    if i == 2:
        b = V.breath(rng, "in", 0.5, table=V.VF, scale=0.97)
        K.place(y, _norm_to(b, w) * 0.45, 0.0)
    K.place(y, w, pre)
    y = dsp.close_mic(dsp.highpass(y, 130, 2), 5.0, 3.0, 3.0)
    mc = V.mouth_clicks(len(y) / SR, rng, rate=1.2) * np.std(y) * 1.5
    y = y + dsp.pad_to(mc, len(y))
    return dsp.trim(_ear(y, side, rng), -62)
