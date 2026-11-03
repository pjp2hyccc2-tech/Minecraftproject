"""ひだまり - the restaurant's own jingle (original composition for HITORI).

An 8-bar tune in F major, 4/4, easy-listening bossa feel.  It is the musical
spine of the sound set: music.store (the in-store loop), music.store_wrong
(the same, slowed and warped), music.closing (in F minor on an organ),
music.title (on a dying music box), music.ending_good / ending_bad, the
waitress humming it off key, and the PA chime (its first four notes).

Times are in beats (quarter notes).  Everything is rendered circularly so the
loops are seamless."""
import numpy as np

import dsp
import instruments as ins
from dsp import ns

# ---------------------------------------------------------------- the tune (beats, note, length in beats)
MELODY = [
    (0.0, "C5", 0.5), (0.5, "D5", 0.5), (1.0, "F5", 1.5), (2.5, "E5", 0.5), (3.0, "C5", 1.0),
    (4.0, "A4", 1.5), (5.5, "G4", 0.5), (6.0, "A4", 1.0), (7.0, "C5", 1.0),
    (8.0, "A#4", 0.5), (8.5, "A4", 0.5), (9.0, "G4", 1.5), (10.5, "F4", 0.5), (11.0, "G4", 1.0),
    (12.0, "A4", 3.0),
    (16.0, "C5", 0.5), (16.5, "D5", 0.5), (17.0, "F5", 1.5), (18.5, "E5", 0.5), (19.0, "C5", 1.0),
    (20.0, "A4", 1.5), (21.5, "G4", 0.5), (22.0, "A4", 1.0), (23.0, "D5", 1.0),
    (24.0, "C5", 1.5), (25.5, "A#4", 0.5), (26.0, "A4", 1.0), (27.0, "G4", 1.0),
    (28.0, "F4", 3.0),
]
# the second time round: an octave up, a few grace notes, a different cadence that leads back to the top
MELODY_B = [
    (0.0, "C6", 0.5), (0.5, "D6", 0.5), (1.0, "F6", 1.5), (2.5, "E6", 0.5), (3.0, "C6", 1.0),
    (4.0, "A5", 1.0), (5.0, "C6", 0.5), (5.5, "G5", 0.5), (6.0, "A5", 1.0), (7.0, "C6", 1.0),
    (8.0, "A#5", 0.5), (8.5, "A5", 0.5), (9.0, "G5", 1.0), (10.0, "A5", 0.5), (10.5, "F5", 0.5), (11.0, "G5", 1.0),
    (12.0, "A5", 2.0), (14.5, "G5", 0.5), (15.0, "A5", 0.5), (15.5, "A#5", 0.5),
    (16.0, "C6", 0.5), (16.5, "D6", 0.5), (17.0, "F6", 1.5), (18.5, "E6", 0.5), (19.0, "C6", 1.0),
    (20.0, "A5", 1.5), (21.5, "G5", 0.5), (22.0, "A5", 1.0), (23.0, "D6", 1.0),
    (24.0, "C6", 1.0), (25.0, "D6", 0.5), (25.5, "A#5", 0.5), (26.0, "A5", 1.0), (27.0, "G5", 1.0),
    (28.0, "F5", 2.0), (30.5, "G5", 0.5), (31.0, "A5", 0.5), (31.5, "A#5", 0.5),
]
# chords: (beat, beats, bass root, rootless voicing for the keys / guitar)
CHORDS = [
    (0, 4, "F2", ["A3", "C4", "E4", "G4"]),     # Fmaj9
    (4, 4, "D2", ["F3", "A3", "C4", "E4"]),     # Dm9
    (8, 2, "G2", ["F3", "A3", "A#3", "D4"]),    # Gm9
    (10, 2, "C2", ["E3", "G3", "A#3", "D4"]),   # C9
    (12, 4, "F2", ["A3", "C4", "D4", "G4"]),    # F6/9
    (16, 4, "F2", ["A3", "C4", "E4", "G4"]),
    (20, 4, "D2", ["F3", "A3", "C4", "E4"]),
    (24, 2, "A#1", ["A3", "C4", "D4", "F4"]),   # Bbmaj9
    (26, 2, "C2", ["E3", "G3", "A#3", "D4"]),   # C9
    (28, 4, "F2", ["A3", "C4", "E4", "G4"]),
]
FIFTH = {"F2": "C3", "D2": "A2", "G2": "D3", "C2": "G2", "A#1": "F2"}

# ---------------------------------------------------------------- the same tune in F minor (closing time)
MINOR = {"A": "G#", "D": "C#", "E": "D#"}  # A -> Ab, D -> Db, E -> Eb  (sharps spelled for dsp.hz)


def to_minor(name):
    letter, octv = name[:-1], name[-1]
    if letter in MINOR:
        return MINOR[letter] + octv
    return name


MELODY_MINOR = [(b, to_minor(nm), d) for b, nm, d in MELODY]
CHORDS_MINOR = [  # Fm9 | Dbmaj7 | Gm7b5 C7b9 | Fm | Fm | Dbmaj7 | Bbm7 C7 | Fm
    (0, 4, "F2", ["G#3", "C4", "D#4", "G4"]),
    (4, 4, "C#2", ["F3", "G#3", "C4", "F4"]),
    (8, 2, "G2", ["F3", "A#3", "C#4", "F4"]),
    (10, 2, "C2", ["E3", "A#3", "C#4", "E4"]),
    (12, 4, "F2", ["G#3", "C4", "F4", "G#4"]),
    (16, 4, "F2", ["G#3", "C4", "D#4", "G4"]),
    (20, 4, "C#2", ["F3", "G#3", "C4", "F4"]),
    (24, 2, "A#1", ["G#3", "C#4", "F4", "C5"]),
    (26, 2, "C2", ["E3", "A#3", "C#4", "G4"]),
    (28, 4, "F2", ["F3", "G#3", "C4", "F4"]),
]

# the PA chime: the jingle's head motif C D F E, rung slowly ("ピンポンパンポーン")
PA_CHIME = [("C6", 0.0), ("D6", 0.36), ("F6", 0.72), ("E6", 1.08)]
# the entrance chime "pin-poon" and the table call button "pin-pon"
DOOR_CHIME = [("A5", 0.0, 0.5), ("F5", 0.42, 1.6)]
CALL_BUTTON = [("E6", 0.0), ("C6", 0.22)]


def hz(name, cents=0.0):
    return dsp.hz(name, cents)


def place_notes(buf, notes, beat_s, make, t0=0.0, circular=True, rng=None, drop=0.0, wobble=None):
    """Render [(beat, note, beats)] with make(freq, dur_s, k) -> mono and add it to buf (mono or stereo).
    drop: probability of a missing note; wobble(t) -> cents."""
    for k, (b, nm, d) in enumerate(notes):
        if drop > 0 and rng is not None and rng.random() < drop:
            continue
        t = t0 + b * beat_s
        c = wobble(t) if wobble else 0.0
        y = make(hz(nm, c), d * beat_s, k)
        if y is None:
            continue
        dsp.place(buf, y, t, circular=circular)
    return buf


def bossa_backing(rng, bars, beat_s, chords=CHORDS, gtr=0.5, bass=0.8, keys=0.42, perc=1.0, swing=0.0,
                  repeats=1, detune_keys=0.0):
    """Guitar comping on the bossa clave, root-fifth bass, e-piano pads, shaker / rim / kick.
    Returns a stereo buffer (circular) of `bars` bars."""
    L = bars * 4 * beat_s
    n = ns(L)
    out = np.zeros((2, n))
    e8 = beat_s / 2.0
    chord_len = 32.0  # beats in the chord chart
    for rep in range(int(np.ceil(bars * 4 / chord_len))):
        for (b, d, root, voicing) in chords:
            beat0 = rep * chord_len + b
            if beat0 >= bars * 4:
                continue
            tb = beat0 * beat_s
            # bass: dotted quarter + eighth, root / fifth
            fifth = FIFTH.get(root, root)
            pat = [(0.0, root, 1.4), (1.5, fifth, 0.45), (2.0, fifth, 1.4), (3.5, root, 0.45)] if d >= 4 else \
                [(0.0, root, 1.4), (1.5, fifth, 0.45)]
            for (o, nm, ln) in pat:
                y = ins.upright_bass(hz(nm), ln * beat_s + 0.3, rng, vel=rng.uniform(0.8, 1.0) * bass,
                                     release=ln * beat_s)
                dsp.place(out, dsp.pan(y, -0.1), tb + o * beat_s + rng.normal(0, 0.004), circular=True)
            # keys: a soft pad stab on the downbeat, held
            for j, nm in enumerate(voicing):
                y = ins.epiano(hz(nm, detune_keys + rng.normal(0, 2.0)), d * beat_s + 0.4, rng, vel=0.5 * keys,
                               bright=0.25, tine=0.3, release=d * beat_s - 0.05)
                dsp.place(out, dsp.pan(y, 0.35 - 0.15 * j), tb + 0.012 * j, circular=True)
    # guitar comping on the 2-bar bossa clave (eighths 0 3 6 10 13), chord of the moment
    clave = [0, 3, 6, 10, 13]
    for bar2 in range(int(np.ceil(bars / 2))):
        for e in clave:
            beat = bar2 * 8 + e / 2.0
            if beat >= bars * 4:
                continue
            cb = beat % chord_len
            ch = [c for c in chords if c[0] <= cb < c[0] + c[1]]
            voicing = ch[0][3] if ch else chords[0][3]
            t = beat * beat_s + swing * e8 * (e % 2)
            for j, nm in enumerate(voicing):  # a quick strum (upward)
                y = ins.nylon(hz(nm, rng.normal(0, 3)), 0.9, rng, vel=rng.uniform(0.5, 0.7) * gtr)
                y = y[:ns(min(0.9, 1.6 * e8))]
                y = dsp.fade(y, 0.0, 0.05)
                dsp.place(out, dsp.pan(y, -0.45 + 0.05 * j), t + 0.008 * j, circular=True)
    if perc > 0:
        for k in range(bars * 8):
            t = k * e8 + (swing * e8 if k % 2 else 0.0)
            v = (0.55 if k % 2 else 0.35) * rng.uniform(0.8, 1.1)
            dsp.place(out, dsp.pan(ins.shaker(rng, v * perc), 0.55), t, circular=True)
        for bar2 in range(int(np.ceil(bars / 2))):
            for e in clave:
                beat = bar2 * 8 + e / 2.0
                if beat < bars * 4:
                    dsp.place(out, dsp.pan(ins.rim_click(rng, 0.45 * perc), 0.2), beat * beat_s, circular=True)
        for bar in range(bars):
            for o, v in ((0.0, 0.7), (1.5, 0.35), (2.0, 0.55), (3.5, 0.3)):
                dsp.place(out, dsp.pan(ins.soft_kick(rng, v * perc), 0.0), (bar * 4 + o) * beat_s, circular=True)
    return out


# ---------------------------------------------------------------- the store's chimes (shared by sfx and music)
def door_chime(rng, rate=1.0, cents=0.0, vel=1.0, bright=0.55, notes=None):
    """The entrance chime "pin-poon" (A5 -> F5 on soft chime bars), dry, mono."""
    notes = notes or DOOR_CHIME
    end = max(t for _, t, _ in notes) / rate + 2.6 / rate
    y = np.zeros(ns(end))
    for nm, t0, dec in notes:
        tone = ins.chime_bar(hz(nm, cents + rng.normal(0, 1.5)), 2.4 / rate, rng, vel=vel, bright=bright,
                             decay=dec / rate)
        dsp.place(y, tone, t0 / rate)
    return y


def pa_chime(rng, rate=1.0, cents=0.0, vel=1.0, notes=None, decay=1.6):
    """The store PA's four-tone chime: the jingle's head C D F E on vibraphone-like bars."""
    notes = notes or PA_CHIME
    y = np.zeros(ns(notes[-1][1] / rate + 3.0 / rate))
    for k, (nm, t0) in enumerate(notes):
        tone = ins.vibraphone(hz(nm, cents + rng.normal(0, 1.5)), 2.8 / rate, rng, vel=vel * (0.9 if k < 3 else 1.0),
                              depth=0.0, hard=0.35)
        tone += 0.35 * ins.chime_bar(hz(nm, cents), 2.8 / rate, rng, vel=vel, bright=0.3, decay=decay / rate)
        dsp.place(y, tone, t0 / rate)
    return y


def call_tone(rng, f, dur=0.3, vel=1.0, duty=0.32):
    """One tone of the table call button: a small piezo/speaker square-ish tone with a quick decay."""
    m = ns(dur + 0.25)
    t = np.arange(m) / SR_
    sq = dsp.pulse(f, duty, m, phase0=0.25) * 0.8 + 0.35 * np.sin(TAU_ * f * t)
    env = (0.5 - 0.5 * np.cos(np.pi * np.clip(t / 0.006, 0, 1))) * np.exp(-t / (dur * 0.45))
    y = dsp.lowpass(sq * env, 6500, 2)
    return dsp.fade(y, 0.0, 0.12) * vel


SR_ = dsp.SR
TAU_ = dsp.TAU
