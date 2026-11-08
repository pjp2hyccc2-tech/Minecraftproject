"""Accompaniment generators that follow a Song's chord timeline (set with Song.set_chords)."""
import numpy as np

from . import dsp, theory


def chord_spans(song, start_bar, end_bar):
    """(start_beat, end_beat, chord) clipped to [start_bar, end_bar)."""
    s0, s1 = song.bar(start_bar), song.bar(end_bar)
    out = []
    for s, e, c in song.chords:
        if e <= s0 or s >= s1:
            continue
        out.append((max(s, s0), min(e, s1), c))
    return out


def pad(song, bus, inst, start_bar, end_bar, voices=4, lo=52, hi=76, vel=0.5, attack=None, release=None,
        overlap=0.06, swell=0.0, seed="pad", top=None, pan=0.0, vel_curve=None):
    """Sustained voice-led chords, one per chord span."""
    rng = dsp.rng_for(song.name, bus.name, seed, start_bar)
    prev = None
    for s, e, c in chord_spans(song, start_bar, end_bar):
        v = theory.voice_chord(c, prev, voices, lo, hi, top_hint=top)
        prev = v
        dur = song.seconds(e - s) + overlap
        vv = vel * (vel_curve(s) if vel_curve else 1.0)
        for i, p in enumerate(v):
            audio = inst.render(p, dur, vv * (1 + 0.04 * rng.normal()), rng, attack=attack, release=release,
                                swell=swell, pan=pan + 0.25 * (i / max(1, voices - 1) - 0.5))
            song.add(bus, song.seconds(s) + rng.normal() * 0.004, audio)
    return prev


def arpeggio(song, bus, inst, start_bar, end_bar, pattern, step=0.5, lo=48, hi=84, vel=0.6, accent=0.15,
             gate=1.0, seed="arp", pan_by_pitch=0.5, humanize=0.005, octaves=None, vel_curve=None):
    """Plays chord tones by index (0 = lowest tone in range) on a grid of `step` beats.

    pattern: list of indexes (None = rest); indexes beyond the available tones wrap upwards by octaves.
    """
    rng = dsp.rng_for(song.name, bus.name, seed, start_bar)
    b0, b1 = song.bar(start_bar), song.bar(end_bar)
    i = 0
    beat = b0
    while beat < b1 - 1e-9:
        c = song.chord_at(beat)
        idx = pattern[i % len(pattern)]
        i += 1
        if c is not None and idx is not None:
            tones = [p for p in range(lo, hi + 1) if p % 12 in c.pcs]
            bass_pc = c.bass
            # start from the chord's bass note when it is in range
            first = next((k for k, p in enumerate(tones) if p % 12 == bass_pc), 0)
            tones = tones[first:] + [t + 12 for t in tones[:first]]
            if octaves is not None:
                tones = tones[: octaves * len(c.pcs) + 1]
            k = idx % len(tones)
            p = tones[k] + 12 * (idx // len(tones))
            on_beat = abs(beat - round(beat)) < 1e-6
            v = vel * (1 + (accent if on_beat else 0)) * (1 + 0.05 * rng.normal())
            if vel_curve:
                v *= vel_curve(beat)
            pan = pan_by_pitch * (p - 66) / 24.0
            audio = inst.render(p, song.seconds(step) * gate, v, rng, pan=float(np.clip(pan, -0.8, 0.8)))
            song.add(bus, song.seconds(beat) + rng.normal() * humanize, audio)
        beat += step


def bass_line(song, bus, inst, start_bar, end_bar, pattern, lo=31, hi=50, vel=0.75, seed="bass", humanize=0.004,
              pan=0.0, vel_curve=None):
    """pattern: per chord span, list of (beat offset, 'root'|'fifth'|'octave'|'third'|'low'|int semitones, beats).

    The pattern restarts at each chord change and stops at the next one.
    """
    rng = dsp.rng_for(song.name, bus.name, seed, start_bar)
    prev = None
    for s, e, c in chord_spans(song, start_bar, end_bar):
        root = theory.bass_note(c, lo, hi, prev)
        prev = root
        for off, what, beats in pattern:
            b = s + off
            if b >= e - 1e-9:
                continue
            if what == "root":
                p = root
            elif what == "fifth":
                p = root + 7 if root + 7 <= hi else root - 5
            elif what == "octave":
                p = root + 12 if root + 12 <= hi + 7 else root - 12
            elif what == "low":
                p = root - 12 if root - 12 >= lo - 5 else root
            elif what == "third":
                third = 3 if 3 in c.tones else 4
                p = root + third
            else:
                p = root + int(what)
            d = min(beats, e - b)
            v = vel * (1 + 0.05 * rng.normal()) * (vel_curve(b) if vel_curve else 1.0)
            audio = inst.render(p, song.seconds(d), v, rng, pan=pan)
            song.add(bus, song.seconds(b) + rng.normal() * humanize, audio)


def drum_pattern(song, bus, start_bar, end_bar, hits, seed="drums", humanize=0.003, vel_curve=None):
    """hits: list of (beat offset within the bar, function(vel, rng) -> mono, velocity, pan)."""
    rng = dsp.rng_for(song.name, bus.name, seed, start_bar)
    for bar in range(start_bar, end_bar):
        for off, fn, vel, pan in hits:
            b = song.bar(bar, off)
            v = vel * (1 + 0.06 * rng.normal()) * (vel_curve(b) if vel_curve else 1.0)
            song.hit(bus, b, fn(max(0.02, v), rng), pan, offset=rng.normal() * humanize)


def roll(song, bus, fn, start_beat, beats, rate=8, vel0=0.2, vel1=0.9, seed="roll", pan=0.0):
    """Single-stroke roll (timpani, snare) with a crescendo; `rate` strokes per beat."""
    rng = dsp.rng_for(song.name, bus.name, seed, start_beat)
    count = int(beats * rate)
    for i in range(count):
        v = vel0 + (vel1 - vel0) * (i / max(1, count - 1)) ** 1.5
        b = start_beat + i / rate
        song.hit(bus, b, fn(v * (1 + 0.08 * rng.normal()), rng), pan, offset=rng.normal() * 0.004)
