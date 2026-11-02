"""Sounds added for the story: a memory chime, the story card bell and an original music box waltz (used for Mira's
music box and the ending).

Run from the repository root:  python3 tools/gen_sounds_story.py
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_sounds import SR, bandpass, bell, env, noise, ping, place, save, t  # noqa: E402

NOTE = {"C3": 130.81, "D3": 146.83, "E3": 164.81, "F3": 174.61, "G3": 196.00, "A3": 220.00, "B3": 246.94,
        "C4": 261.63, "D4": 293.66, "E4": 329.63, "F4": 349.23, "G4": 392.00, "A4": 440.00, "B4": 493.88,
        "C5": 523.25, "D5": 587.33, "E5": 659.25, "F5": 698.46, "G5": 783.99, "A5": 880.00, "B5": 987.77,
        "C6": 1046.50, "D6": 1174.66, "E6": 1318.51}


def pluck(freq, dur=1.4, decay=0.55, amp=1.0):
    """A music box comb tooth: bright partials that fade fast, with a tiny metallic click."""
    s = ping(freq, dur, decay, ((1, 1.0), (2.0, 0.32), (3.0, 0.12), (4.17, 0.07), (5.4, 0.03)))
    click = bandpass(noise(0.006), 3000, 9000) * env(int(0.006 * SR), 0.0003, 0.0015) * 0.15
    s[:len(click)] += click
    return s * amp


def music_box():
    beat = 0.55
    melody = [
        # A
        ("E5", 1), ("G5", 1), ("C6", 1), ("B5", 1.5), ("A5", 0.5), ("G5", 1), ("A5", 1), ("F5", 1), ("D5", 1), ("E5", 3),
        # B
        ("E5", 1), ("G5", 1), ("C6", 1), ("D6", 1.5), ("C6", 0.5), ("B5", 1), ("A5", 1), ("B5", 1), ("G5", 1), ("C6", 3),
        # C
        ("A5", 1), ("C6", 1), ("E6", 1), ("D6", 1), ("C6", 1), ("A5", 1), ("G5", 1), ("E5", 1), ("G5", 1), ("F5", 1.5), ("E5", 0.5), ("D5", 1),
        # D
        ("E5", 1), ("G5", 1), ("C6", 1), ("B5", 1), ("D6", 1), ("G5", 1), ("A5", 1), ("B5", 1), ("D6", 1), ("C6", 3),
    ]
    chords = [("C4", "E4", "G4"), ("G3", "B3", "D4"), ("F3", "A3", "D4"), ("C4", "E4", "G4"),
              ("C4", "E4", "G4"), ("G3", "B3", "D4"), ("F3", "A3", "C4"), ("C4", "E4", "G4"),
              ("A3", "C4", "E4"), ("F3", "A3", "C4"), ("C4", "E4", "G4"), ("G3", "B3", "D4"),
              ("C4", "E4", "G4"), ("G3", "B3", "D4"), ("F3", "A3", "C4"), ("C4", "E4", "G4")]
    parts = []
    pos = 0.0
    for name, length in melody:
        parts.append((pos, pluck(NOTE[name], 1.6, 0.5 + 0.12 * length, 1.0)))
        pos += length * beat
    for bar, (root, third, fifth) in enumerate(chords):
        start = bar * 3 * beat
        parts.append((start, pluck(NOTE[root] / 2, 1.8, 0.7, 0.55)))
        parts.append((start + beat, pluck(NOTE[third], 1.0, 0.35, 0.28)))
        parts.append((start + 2 * beat, pluck(NOTE[fifth], 1.0, 0.35, 0.28)))
    total = len(chords) * 3 * beat + 2.0
    # A last high note to close the phrase.
    parts.append((len(chords) * 3 * beat, pluck(NOTE["C6"] * 2, 2.0, 0.9, 0.35)))
    save("music_box", place(total, *parts), 0.8)


def memory():
    parts = [(0.0, bell(1318.5, 1.4, 0.5) * 0.7), (0.09, bell(1568.0, 1.3, 0.5) * 0.6), (0.18, bell(1975.5, 1.2, 0.55) * 0.55),
             (0.27, bell(2637.0, 1.2, 0.6) * 0.45)]
    x = t(1.6)
    swell = bandpass(noise(1.6), 2000, 8000) * np.sin(np.pi * x / 1.6) ** 2 * 0.12
    save("memory", place(1.7, (0, swell), *parts), 0.75)


def story_card():
    deep = bell(196.0, 2.6, 1.2)
    high = bell(784.0, 2.0, 0.8) * 0.5
    save("story_card", place(2.8, (0, deep), (0.05, high), (0.6, bell(587.3, 2.0, 0.9) * 0.35)), 0.8)


if __name__ == "__main__":
    music_box()
    memory()
    story_card()
    print("story sounds written")
