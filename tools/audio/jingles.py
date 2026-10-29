#!/usr/bin/env python3
"""Synthesizes HuntCraft's ORIGINAL jingles (no sampled/copied music) to OGG via ffmpeg."""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft', 'sounds', 'jingle')
NOTE = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}


def freq(n):
    name, octv = n[:-1], int(n[-1])
    return 440.0 * 2 ** ((NOTE[name] + (octv - 4) * 12) / 12.0)


def env(n, a=0.01, d=0.08, s=0.7, r=0.15):
    t = np.arange(n) / SR
    dur = n / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / d
    rel = t > dur - r
    e[rel] *= np.clip((dur - t[rel]) / r, 0, 1)
    return e


def brass(f, dur, vol=0.3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t) * np.clip(t / 0.3, 0, 1)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    sig = sum((0.9 ** k) / k * np.sin(k * ph) for k in range(1, 9))
    bright = np.clip(t / 0.06, 0, 1)
    return vol * sig * env(n, 0.03, 0.12, 0.75, 0.12) * (0.6 + 0.4 * bright)


def pluck(f, dur, vol=0.3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(4 * np.pi * f * t) * np.exp(-t * 6) + 0.25 * np.sin(6 * np.pi * f * t) * np.exp(-t * 9)
    return vol * sig * np.exp(-t * 4.5) * env(n, 0.003, 0.02, 1.0, 0.05)


def bell(f, dur, vol=0.3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 3) + 0.4 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 6)
    return vol * sig * np.exp(-t * 2.2) * env(n, 0.002, 0.01, 1.0, 0.1)


def flute(f, dur, vol=0.25):
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.006 * np.sin(2 * np.pi * 6 * t) * np.clip((t - 0.1) / 0.2, 0, 1)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    sig = np.sin(ph) + 0.15 * np.sin(2 * ph) + 0.05 * np.sin(3 * ph)
    breath = np.random.default_rng(int(f)).normal(0, 0.03, n) * env(n, 0.01, 0.05, 0.3, 0.05)
    return vol * (sig * env(n, 0.04, 0.06, 0.85, 0.08) + breath)


def timpani(dur, vol=0.5, f=65.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.sin(2 * np.pi * f * t * (1 + 0.1 * np.exp(-t * 20))) + 0.4 * np.sin(2 * np.pi * f * 1.5 * t)
    noise = np.random.default_rng(7).normal(0, 1, n) * np.exp(-t * 30) * 0.4
    return vol * (sig * np.exp(-t * 3.5) + noise)


def roll(dur, vol=0.35):
    n = int(dur * SR)
    out = np.zeros(n)
    k = 0
    while k * 0.045 < dur:
        hit = timpani(0.4, vol * (0.3 + 0.7 * (k * 0.045 / dur)))
        s = int(k * 0.045 * SR)
        e = min(n, s + len(hit))
        out[s:e] += hit[:e - s]
        k += 1
    return out


class Track:
    def __init__(self, dur):
        self.buf = np.zeros(int(dur * SR) + SR)

    def add(self, sig, at):
        s = int(at * SR)
        e = min(len(self.buf), s + len(sig))
        self.buf[s:e] += sig[:e - s]

    def seq(self, inst, notes, start, beat, vol=0.3, legato=1.0):
        t = start
        for n, b in notes:
            if n != 'R':
                for nn in n.split('+'):
                    self.add(inst(freq(nn), b * beat * legato + 0.05, vol), t)
            t += b * beat
        return t

    def save(self, name, gain=0.9):
        b = self.buf
        nz = np.nonzero(np.abs(b) > 1e-4)[0]
        if len(nz):
            b = b[:nz[-1] + int(0.05 * SR)]
        # gentle reverb
        rev = np.zeros_like(b)
        for d, g in ((0.031, 0.35), (0.047, 0.28), (0.071, 0.2), (0.113, 0.14)):
            k = int(d * SR)
            rev[k:] += b[:-k] * g
        b = b + rev * 0.6
        b = b / max(1e-6, np.max(np.abs(b))) * gain
        os.makedirs(OUT, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
            with wave.open(tf.name, 'wb') as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes((b * 32767).astype(np.int16).tobytes())
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tf.name, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tf.name)
        print(name, '%.2fs' % (len(b) / SR))


# quest accepted: bright rising arpeggio + chord
t = Track(2.0)
t.seq(pluck, [('G4', 1), ('C5', 1), ('E5', 1), ('G5', 1)], 0.0, 0.11, 0.35)
t.seq(brass, [('C4+E4+G4+C5', 6)], 0.44, 0.13, 0.18)
t.add(bell(freq('C6'), 1.0, 0.15), 0.44)
t.save('quest_accept')

# quest start: timpani roll then horn call
t = Track(4.0)
t.add(roll(1.3, 0.35), 0.0)
end = t.seq(brass, [('D4', 1.5), ('A4', 0.5), ('D5', 2), ('C5', 0.5), ('A4', 0.5), ('B4', 1), ('D5+F#4+A4', 4)], 1.3, 0.22, 0.26)
t.add(timpani(1.2, 0.6, 73.4), 1.3 + 0.22 * 6)
t.save('quest_start')

# quest clear: triumphant fanfare
t = Track(6.5)
mel = [('G4', 0.5), ('G4', 0.5), ('G4', 0.5), ('C5', 1.5), ('E5', 1), ('D5', 0.5), ('C5', 0.5), ('D5', 2),
       ('E5', 0.5), ('E5', 0.5), ('F5', 0.5), ('G5', 1.5), ('A5', 0.5), ('G5', 0.5), ('E5', 1), ('C5+E5+G5', 4)]
t.seq(brass, mel, 0.05, 0.2, 0.25)
t.seq(brass, [('C3+G3', 3), ('G2+D3', 3), ('C3+E3', 3), ('G2+B2', 2), ('C3+G3', 4)], 0.05, 0.2, 0.18)
for k, at in enumerate((0.05, 0.65, 1.25, 2.45, 3.05, 4.15)):
    t.add(timpani(0.6, 0.45, 65.4 if k % 2 == 0 else 98.0), at)
t.add(bell(freq('C6'), 2.0, 0.18), 0.05 + 0.2 * 12)
t.save('quest_clear')

# quest failed: slow descending minor phrase
t = Track(4.0)
t.seq(flute, [('E5', 2), ('D#5', 2), ('D5', 2), ('C#5', 4)], 0.0, 0.3, 0.28)
t.seq(brass, [('A2+E3', 10)], 0.0, 0.3, 0.12)
t.add(timpani(1.5, 0.4, 55.0), 1.8)
t.save('quest_fail')

# meat roasting tune (6/8 folk feel). Perfect window = 4.60s .. 5.30s -> bell cue at 4.65s
t = Track(6.2)
b = 0.19
roast = [('C5', 1), ('E5', 1), ('G5', 1), ('A5', 2), ('G5', 1), ('E5', 1), ('D5', 1), ('C5', 1), ('D5', 3),
         ('E5', 1), ('G5', 1), ('A5', 1), ('C6', 2), ('A5', 1), ('G5', 1), ('E5', 1), ('D5', 1), ('E5', 2), ('G5', 1)]
t.seq(flute, roast, 0.0, b, 0.26)
bass = [('C3', 3), ('G2', 3), ('F2', 3), ('G2', 3), ('C3', 3), ('A2', 3), ('F2', 3), ('G2', 3)]
t.seq(pluck, bass, 0.0, b, 0.3)
for k in range(0, 24):
    t.add(pluck(freq('G4') if k % 3 else freq('C5'), 0.12, 0.08), k * b)
t.add(bell(freq('C6'), 1.4, 0.32), 4.65)
t.add(bell(freq('G6'), 1.2, 0.18), 4.65)
t.seq(brass, [('C5+E5+G5', 3)], 4.65, b, 0.16)
t.save('meat_roast')

# meat done: ta-da
t = Track(1.4)
t.seq(brass, [('G4+B4', 0.6), ('C5+E5+G5', 3)], 0.0, 0.15, 0.25)
t.add(bell(freq('E6'), 0.9, 0.2), 0.09)
t.save('meat_done')

# level up / upgrade
t = Track(1.6)
t.seq(bell, [('C5', 1), ('E5', 1), ('G5', 1), ('C6', 3)], 0.0, 0.09, 0.3)
t.seq(pluck, [('C4+G4', 4)], 0.0, 0.12, 0.2)
t.save('level_up')
