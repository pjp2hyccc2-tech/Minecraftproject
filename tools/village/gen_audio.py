#!/usr/bin/env python3
"""Synthesizes the village module's ORIGINAL sound effects / jingles (no samples) to OGG via ffmpeg.

Run from the repository root:  python3 tools/village/gen_audio.py
Outputs: src/main/resources/assets/huntcraft/sounds/village/*.ogg
(The synth voices follow tools/audio/jingles.py; they are re-implemented here because that script renders the core
jingles when imported.)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft', 'sounds', 'village')
NOTE = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}
RNG = np.random.default_rng(1234)


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


def koto(f, dur, vol=0.3):
    """Plucked zither: bright attack, quick decay, slight pitch bend down at the start."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    bend = 1 + 0.012 * np.exp(-t * 40)
    ph = 2 * np.pi * f * np.cumsum(bend) / SR
    sig = np.sin(ph) + 0.55 * np.sin(2 * ph) * np.exp(-t * 7) + 0.3 * np.sin(3 * ph) * np.exp(-t * 11) + 0.12 * np.sin(5 * ph) * np.exp(-t * 18)
    return vol * sig * np.exp(-t * 3.2) * env(n, 0.002, 0.02, 1.0, 0.06)


def bell(f, dur, vol=0.3):
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 3) + 0.4 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 6)
    return vol * sig * np.exp(-t * 2.2) * env(n, 0.002, 0.01, 1.0, 0.1)


def flute(f, dur, vol=0.25):
    """Shakuhachi-like breathy flute."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.007 * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.08) / 0.2, 0, 1)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    sig = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    breath = RNG.normal(0, 0.05, n) * env(n, 0.01, 0.06, 0.35, 0.05)
    return vol * (sig * env(n, 0.05, 0.06, 0.85, 0.1) + breath)


def horn(f, dur, vol=0.3):
    """Small round horn (companion healing horn)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.005 * np.sin(2 * np.pi * 6 * t) * np.clip(t / 0.25, 0, 1)
    ph = 2 * np.pi * f * np.cumsum(vib) / SR
    sig = sum((0.75 ** k) / k * np.sin(k * ph) for k in range(1, 7))
    return vol * sig * env(n, 0.04, 0.1, 0.8, 0.12)


def noise_burst(dur, lo, hi, vol=0.4, attack=0.004, decay=18.0, sweep=1.0):
    """Band-limited noise (paper rustle, swish): simple FFT band-pass with an optional frequency sweep."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    segs = 6
    for s in range(segs):
        a, b = int(n * s / segs), int(n * (s + 1) / segs)
        if b <= a:
            continue
        x = RNG.normal(0, 1, b - a)
        spec = np.fft.rfft(x)
        fr = np.fft.rfftfreq(b - a, 1 / SR)
        k = sweep ** (s / max(1, segs - 1))
        mask = (fr >= lo * k) & (fr <= hi * k)
        spec[~mask] = 0
        out[a:b] = np.fft.irfft(spec, b - a)
    out /= max(1e-6, np.max(np.abs(out)))
    e = np.clip(t / attack, 0, 1) * np.exp(-t * decay)
    return vol * out * e


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

    def save(self, name, gain=0.9, reverb=0.6):
        b = self.buf
        nz = np.nonzero(np.abs(b) > 1e-4)[0]
        if len(nz):
            b = b[:nz[-1] + int(0.05 * SR)]
        rev = np.zeros_like(b)
        for d, g in ((0.031, 0.35), (0.047, 0.28), (0.071, 0.2), (0.113, 0.14)):
            k = int(d * SR)
            rev[k:] += b[:-k] * g
        b = b + rev * reverb
        b = b / max(1e-6, np.max(np.abs(b))) * gain
        os.makedirs(OUT, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
            with wave.open(tf.name, 'wb') as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(SR)
                w.writeframes((b * 32767).astype(np.int16).tobytes())
            subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tf.name, '-c:a', 'libvorbis', '-q:a', '4',
                            os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tf.name)
        print(name, '%.2fs' % (len(b) / SR))


def main():
    # arrival in Kazami village: pentatonic koto phrase over a soft flute drone (yo-scale D E G A B)
    t = Track(4.0)
    t.seq(koto, [('D5', 1), ('E5', 1), ('A5', 1), ('B5', 1), ('A5', 2), ('E5', 1), ('G5', 1), ('A5', 4)], 0.0, 0.17, 0.3)
    t.seq(koto, [('D4', 4), ('A3', 4), ('D4', 4)], 0.0, 0.17, 0.18)
    t.seq(flute, [('A4', 6), ('D5', 6)], 0.2, 0.17, 0.12)
    t.add(bell(freq('D6'), 1.6, 0.12), 0.17 * 8)
    t.save('welcome')
    # companion healing horn: rising three-note call + warm chord
    t = Track(2.2)
    t.seq(horn, [('G4', 1), ('C5', 1), ('E5', 2.5)], 0.0, 0.16, 0.3)
    t.seq(horn, [('C4+G4', 4.5)], 0.0, 0.16, 0.12)
    t.add(bell(freq('C6'), 1.2, 0.1), 0.32)
    t.save('mofu_horn')
    # meal served: cheerful "itadakimasu" jingle (koto + bells)
    t = Track(2.0)
    t.seq(koto, [('G5', 1), ('A5', 1), ('C6', 1), ('A5', 1), ('D6', 3)], 0.0, 0.11, 0.3)
    t.seq(koto, [('C4+G4', 4), ('D4+A4', 3)], 0.0, 0.11, 0.16)
    t.add(bell(freq('D6'), 1.0, 0.18), 0.44)
    t.add(bell(freq('A6'), 0.9, 0.1), 0.5)
    t.save('meal')
    # NPC greeting chime (two soft bells)
    t = Track(1.0)
    t.add(bell(freq('E6'), 0.7, 0.25), 0.0)
    t.add(bell(freq('B5'), 0.8, 0.22), 0.09)
    t.save('npc_greet', gain=0.7)
    # quick blade swish for companion attacks (3 variants)
    for i, (lo, hi, sw) in enumerate(((900, 5200, 1.6), (700, 4200, 1.9), (1100, 6000, 1.4))):
        t = Track(0.4)
        t.add(noise_burst(0.22, lo, hi, 0.5, 0.02, 14.0, sw), 0.0)
        t.save('swish%d' % (i + 1), gain=0.75, reverb=0.2)
    # page / paper flip for the screens (2 variants)
    for i, (lo, hi) in enumerate(((1500, 7000), (1200, 6000))):
        t = Track(0.3)
        t.add(noise_burst(0.12, lo, hi, 0.4, 0.003, 30.0, 0.8), 0.0)
        t.add(noise_burst(0.08, lo * 1.3, hi, 0.25, 0.003, 40.0, 1.0), 0.06)
        t.save('page%d' % (i + 1), gain=0.6, reverb=0.15)


if __name__ == '__main__':
    main()
