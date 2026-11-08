"""Pitches, chords, voice leading and a compact melody notation.

Melody notation (one string per phrase, bars separated by '|', every bar is checked to add up):
    "F4:e. G4:s C5:h. | D5:q C5:e Bb4:e A4:q G4:q"
  pitch: C..B with # or b and octave (C4 = MIDI 60), 'r' for a rest, '+' joins a chord ("F4+A4:q")
  duration: w h q e s (4, 2, 1, 1/2, 1/4 beats), t = eighth triplet (1/3), q3 = quarter triplet (2/3),
            a trailing '.' dots it, or a number of beats ("1.5"); '~' ties into the next note of the same pitch
  accents: '!' strong (velocity 1.0), '?' soft (0.55), default 0.8; '>' a short articulation (staccato)
"""
import re
from dataclasses import dataclass

NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
PC_NAMES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]

SCALES = {
    "major": [0, 2, 4, 5, 7, 9, 11],
    "minor": [0, 2, 3, 5, 7, 8, 10],
    "harmonic_minor": [0, 2, 3, 5, 7, 8, 11],
    "dorian": [0, 2, 3, 5, 7, 9, 10],
    "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "phrygian_dominant": [0, 1, 4, 5, 7, 8, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11],
    "mixolydian": [0, 2, 4, 5, 7, 9, 10],
    "pentatonic": [0, 2, 4, 7, 9],
    "minor_pentatonic": [0, 3, 5, 7, 10],
}


def midi(name):
    m = re.fullmatch(r"([A-G])([#b]*)(-?\d)", name)
    if not m:
        raise ValueError(f"bad pitch {name!r}")
    pc = NOTE_PC[m.group(1)] + m.group(2).count("#") - m.group(2).count("b")
    return 12 * (int(m.group(3)) + 1) + pc


def pc_of(name):
    m = re.fullmatch(r"([A-G])([#b]*)", name)
    if not m:
        raise ValueError(f"bad pitch class {name!r}")
    return (NOTE_PC[m.group(1)] + m.group(2).count("#") - m.group(2).count("b")) % 12


# ------------------------------------------------------------------------------------------------- chords

CHORD_QUALITIES = {
    "": [0, 4, 7], "maj": [0, 4, 7], "m": [0, 3, 7], "dim": [0, 3, 6], "aug": [0, 4, 8], "+": [0, 4, 8],
    "5": [0, 7], "sus2": [0, 2, 7], "sus4": [0, 5, 7], "sus": [0, 5, 7],
    "6": [0, 4, 7, 9], "m6": [0, 3, 7, 9], "7": [0, 4, 7, 10], "maj7": [0, 4, 7, 11], "m7": [0, 3, 7, 10],
    "mmaj7": [0, 3, 7, 11], "m7b5": [0, 3, 6, 10], "dim7": [0, 3, 6, 9], "7sus4": [0, 5, 7, 10],
    "9": [0, 4, 7, 10, 14], "maj9": [0, 4, 7, 11, 14], "m9": [0, 3, 7, 10, 14], "add9": [0, 4, 7, 14],
    "madd9": [0, 3, 7, 14], "m11": [0, 3, 7, 10, 14, 17], "maj7#11": [0, 4, 7, 11, 18], "7b9": [0, 4, 7, 10, 13],
    "6/9": [0, 4, 7, 9, 14], "sus2add11": [0, 2, 5, 7], "add11": [0, 4, 7, 17], "m(b6)": [0, 3, 7, 8],
    "7#9": [0, 4, 7, 10, 15], "b5": [0, 4, 6], "mb6": [0, 3, 8],
}


@dataclass(frozen=True)
class Chord:
    symbol: str
    root: int          # pitch class
    tones: tuple       # semitone offsets from the root (may exceed 12 for tensions)
    bass: int          # pitch class of the bass note

    @property
    def pcs(self):
        return sorted({(self.root + t) % 12 for t in self.tones})

    @property
    def core(self):
        """Root, third (or sus), seventh: the tones a voicing must contain."""
        core = {self.root % 12}
        for t in self.tones:
            if t % 12 in (3, 4, 2, 5) and t < 12:
                core.add((self.root + t) % 12)
                break
        for t in self.tones:
            if t in (10, 11, 9) and len(self.tones) > 3:
                core.add((self.root + t) % 12)
        return core


def chord(symbol):
    m = re.fullmatch(r"([A-G][#b]?)([^/]*)(?:/([A-G][#b]?))?", symbol)
    if not m:
        raise ValueError(f"bad chord {symbol!r}")
    root = pc_of(m.group(1))
    quality = m.group(2)
    if quality not in CHORD_QUALITIES:
        raise ValueError(f"unknown chord quality {quality!r} in {symbol!r}")
    bass = pc_of(m.group(3)) if m.group(3) else root
    return Chord(symbol, root, tuple(CHORD_QUALITIES[quality]), bass)


def nearest(pc, target):
    """MIDI note of pitch class pc closest to target."""
    base = target - ((target - pc) % 12)
    return base if target - base <= 6 else base + 12


def voice_chord(ch, prev, n, lo, hi, top_hint=None):
    """Voice-led voicing of `n` notes for chord `ch` within [lo, hi], moving as little as possible from `prev`.

    Every core tone (root, third, seventh) is present when n allows; parallel fifths/octaves and voice crossing are
    penalised; voices keep at most an octave between neighbours (except the lowest pair).
    """
    import itertools
    pcs = ch.pcs
    if prev is None:
        center = (lo + hi) // 2
        prev = [lo + (hi - lo) * (i + 1) // (n + 1) for i in range(n)]
        if top_hint is not None:
            prev[-1] = top_hint
        del center
    cands = []
    for p in prev:
        opts = set()
        for pc in pcs:
            for o in (-12, 0, 12):
                v = nearest(pc, p) + o
                if lo <= v <= hi and abs(v - p) <= 9:
                    opts.add(v)
        if not opts:
            for pc in pcs:
                v = nearest(pc, min(max(p, lo), hi))
                while v < lo:
                    v += 12
                while v > hi:
                    v -= 12
                opts.add(v)
        cands.append(sorted(opts))
    best = None
    core = ch.core
    for combo in itertools.product(*cands):
        if any(combo[i] >= combo[i + 1] for i in range(n - 1)):
            continue
        present = {v % 12 for v in combo}
        missing = len(core - present) if n >= len(core) else max(0, len(core) - n - (len(core & present)))
        cost = sum(abs(a - b) for a, b in zip(combo, prev)) + 12 * missing
        cost += 3 * (len(pcs) - len(present & set(pcs))) if n >= len(pcs) else 0
        for i in range(1, n - 1):
            if combo[i + 1] - combo[i] > 12:
                cost += 6
        for i in range(n):
            for j in range(i + 1, n):
                a0, a1 = prev[i], prev[j]
                b0, b1 = combo[i], combo[j]
                if a0 != b0 and (a1 - a0) % 12 == (b1 - b0) % 12 and (b1 - b0) % 12 in (0, 7):
                    cost += 4
        doubled_third = sum(1 for v in combo if (v - ch.root) % 12 in (3, 4))
        if doubled_third > 1:
            cost += 2
        if top_hint is not None:
            cost += 0.5 * abs(combo[-1] - top_hint)
        if best is None or cost < best[0]:
            best = (cost, combo)
    if best is None:
        return sorted(nearest(pc, (lo + hi) // 2) for pc in pcs[:n])
    return list(best[1])


def voice_progression(symbols, n=4, lo=52, hi=76, start=None):
    voicings = []
    prev = start
    for s in symbols:
        v = voice_chord(chord(s), prev, n, lo, hi)
        voicings.append(v)
        prev = v
    return voicings


def bass_note(ch, lo=36, hi=52, prev=None):
    target = prev if prev is not None else (lo + hi) // 2
    v = nearest(ch.bass, target)
    while v < lo:
        v += 12
    while v > hi:
        v -= 12
    return v


# ------------------------------------------------------------------------------------------------- notation

DUR = {"w": 4.0, "h": 2.0, "q": 1.0, "e": 0.5, "s": 0.25, "t": 1.0 / 3.0, "q3": 2.0 / 3.0, "x": 0.125}


@dataclass
class Note:
    start: float        # beats from the phrase start
    dur: float          # beats
    pitches: tuple      # MIDI numbers (empty for rests are dropped)
    vel: float = 0.8
    short: bool = False


def _dur(code):
    dotted = code.endswith(".")
    base = code[:-1] if dotted else code
    try:
        d = DUR[base] if base in DUR else float(base)
    except ValueError:
        raise ValueError(f"bad duration {code!r}")
    return d * 1.5 if dotted else d


def parse(text, bar_beats=4.0, transpose=0, check=True, name="phrase"):
    """Parses the notation; returns notes with start/dur in beats. Raises if a bar does not add up."""
    notes = []
    t = 0.0
    bars = text.split("|")
    tie_open = None
    for bi, bar in enumerate(bars):
        bar_start = t
        for tok in bar.split():
            vel = 0.8
            short = False
            tie = False
            while tok and tok[-1] in "!?>~":
                c = tok[-1]
                tok = tok[:-1]
                if c == "!":
                    vel = 1.0
                elif c == "?":
                    vel = 0.55
                elif c == ">":
                    short = True
                else:
                    tie = True
            if ":" not in tok:
                raise ValueError(f"{name}: token {tok!r} needs pitch:duration")
            p, d = tok.split(":")
            dur = _dur(d)
            if p != "r":
                pitches = tuple(midi(x) + transpose for x in p.split("+"))
                if tie_open is not None and tie_open.pitches == pitches and abs(tie_open.start + tie_open.dur - t) < 1e-6:
                    tie_open.dur += dur
                    tie_open = tie_open if tie else None
                else:
                    n = Note(t, dur, pitches, vel, short)
                    notes.append(n)
                    tie_open = n if tie else None
            else:
                tie_open = None
            t += dur
        length = t - bar_start
        if check and abs(length - bar_beats) > 1e-6 and not (bi == len(bars) - 1 and length == 0):
            raise ValueError(f"{name}: bar {bi + 1} has {length:g} beats, expected {bar_beats:g}: {bar.strip()!r}")
    return notes


def shift(notes, beats=0.0, semitones=0):
    return [Note(n.start + beats, n.dur, tuple(p + semitones for p in n.pitches), n.vel, n.short) for n in notes]


# ------------------------------------------------------------------------------------------------- harmony check

def harmony_warnings(melody, chord_at, name, strong_only=True, min_dur=0.75):
    """Flags long melody notes on strong beats that form a minor ninth/second with the chord (avoid notes)."""
    out = []
    for n in melody:
        if n.dur < min_dur:
            continue
        if strong_only and abs(n.start - round(n.start)) > 1e-6:
            continue
        ch = chord_at(n.start)
        if ch is None:
            continue
        for p in n.pitches:
            pc = p % 12
            tones = ch.pcs
            if pc in tones:
                continue
            for tpc in tones:
                if (pc - tpc) % 12 == 1 and (tpc - ch.root) % 12 in (0, 3, 4, 7):
                    out.append(f"{name}: beat {n.start:g} {PC_NAMES[pc]} is a minor 9th over "
                               f"{PC_NAMES[tpc]} of {ch.symbol}")
    return out
