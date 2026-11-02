"""Synthesises the clockwork sound effects and encodes them as OGG Vorbis (needs numpy and ffmpeg).

Run from the repository root:  python3 tools/gen_sounds.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "clockworkdiver", "sounds")
rng = np.random.default_rng(7)


def t(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, attack=0.002, decay=0.1, sustain=0.0, release=None):
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-5), 0, 1)
    d = np.exp(-np.maximum(x - attack, 0) / max(decay, 1e-5))
    e = a * (sustain + (1 - sustain) * d)
    if release:
        tail = np.clip((x[-1] - x) / release, 0, 1)
        e *= tail
    return e


def ping(freq, dur, decay, partials=((1, 1.0),), detune=0.0):
    x = t(dur)
    s = np.zeros_like(x)
    for ratio, amp in partials:
        s += amp * np.sin(2 * np.pi * freq * ratio * x * (1 + detune * np.sin(2 * np.pi * 5 * x)))
    return s * env(len(x), 0.001, decay)


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    prev = 0.0
    for i, v in enumerate(x):
        prev = (1 - a) * v + a * prev
        y[i] = prev
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, lo, hi):
    return lowpass(highpass(x, lo), hi)


def place(total, *parts):
    """parts: (start_seconds, signal)"""
    out = np.zeros(int(SR * total))
    for start, sig in parts:
        i = int(start * SR)
        n = min(len(sig), len(out) - i)
        if n > 0:
            out[i:i + n] += sig[:n]
    return out


def save(name, sig, gain=0.9):
    sig = np.asarray(sig, dtype=np.float64)
    peak = np.max(np.abs(sig)) or 1.0
    sig = sig / peak * gain
    fade = min(len(sig), int(0.004 * SR))
    sig[-fade:] *= np.linspace(1, 0, fade)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())
    target = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4", target], check=True)
    os.remove(path)


def bell(freq, dur, decay=0.8):
    return ping(freq, dur, decay, ((1, 1.0), (2.4, 0.5), (3.9, 0.3), (5.4, 0.18), (6.8, 0.08)))


def main():
    click = bandpass(noise(0.01), 2000, 9000) * env(int(0.01 * SR), 0.0005, 0.002)
    save("tick", place(0.12, (0, click), (0, ping(3200, 0.12, 0.025, ((1, 1.0), (2.7, 0.3))) * 0.6)), 0.8)
    knock = bandpass(noise(0.02), 600, 3000) * env(int(0.02 * SR), 0.0005, 0.004)
    save("tock", place(0.14, (0, knock), (0, ping(2050, 0.14, 0.03, ((1, 1.0), (2.3, 0.25))) * 0.6)), 0.8)

    shimmer = ping(1760, 0.6, 0.18, ((1, 1.0), (1.5, 0.45), (2.0, 0.3), (3.0, 0.15)), detune=0.002)
    save("perfect", place(0.6, (0, shimmer), (0.0, click * 0.6)), 0.85)

    hiss = highpass(noise(0.9), 1500) * env(int(0.9 * SR), 0.01, 0.25)
    thump = ping(90, 0.4, 0.08) * 0.8
    save("steam_burst", place(0.9, (0, hiss), (0, thump)), 0.9)

    x = t(1.6)
    sweep = np.sin(2 * np.pi * np.cumsum(200 + 1800 * (x / 1.6) ** 2) / SR) * (0.4 + 0.6 * x / 1.6)
    whoosh = bandpass(noise(1.6), 300, 4000) * np.sin(np.pi * x / 1.6)
    sparkle = sum(ping(f, 0.4, 0.12) for f in (2093, 2637, 3136))
    save("shrink", place(1.8, (0, sweep * 0.5), (0, whoosh * 0.8), (1.2, sparkle * 0.5)), 0.85)

    x = t(1.1)
    sweep = np.sin(2 * np.pi * np.cumsum(2000 - 1800 * (x / 1.1) ** 0.6) / SR) * (1 - x / 1.1)
    whoosh = bandpass(noise(1.1), 200, 2500) * np.sin(np.pi * x / 1.1)
    save("grow", place(1.2, (0, sweep * 0.5), (0, whoosh * 0.7)), 0.8)

    save("discover", place(0.8, (0, bell(1047, 0.7, 0.3)), (0.07, bell(1319, 0.7, 0.3)), (0.14, bell(1568, 0.66, 0.35))), 0.8)
    save("repaired", place(1.8, (0, bell(523, 1.6, 0.6)), (0.12, bell(659, 1.5, 0.6)), (0.24, bell(784, 1.4, 0.6)),
                         (0.36, bell(1047, 1.4, 0.8)), (0.9, click), (1.1, click), (1.3, click), (1.5, click)), 0.85)

    clicks = []
    for i in range(6):
        c = bandpass(noise(0.03), 1500, 7000) * env(int(0.03 * SR), 0.0005, 0.003)
        clicks.append((i * 0.035, c + ping(2600 - i * 120, 0.03, 0.008) * 0.4))
    save("ratchet", place(0.3, *clicks), 0.8)

    save("clunk", place(0.4, (0, ping(170, 0.4, 0.07, ((1, 1.0), (2.1, 0.4), (3.3, 0.2)))),
                        (0, bandpass(noise(0.05), 300, 2500) * env(int(0.05 * SR), 0.0005, 0.01))), 0.9)

    save("shop_bell", place(1.6, (0, bell(1568, 1.2, 0.5)), (0.22, bell(1568, 1.3, 0.55))), 0.8)
    save("glasses", place(0.4, (0, click), (0.03, ping(4100, 0.35, 0.09, ((1, 1.0), (1.6, 0.3))) * 0.6)), 0.7)

    x = t(0.35)
    chitter = bandpass(noise(0.35), 2500, 6000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 38 * x))) * np.sin(np.pi * x / 0.35)
    save("mite", chitter, 0.6)

    parts = []
    for i, f in enumerate((620, 700, 820)):
        xx = t(0.12)
        tone = np.sign(np.sin(2 * np.pi * (f + 60 * np.sin(2 * np.pi * 18 * xx)) * xx)) * 0.4 + np.sin(2 * np.pi * f * 2 * xx) * 0.3
        parts.append((i * 0.14, lowpass(tone, 3000) * env(len(xx), 0.005, 0.05)))
    save("gremlin", place(0.45, *parts), 0.6)

    x = t(0.45)
    flutter = lowpass(noise(0.45), 1200) * (0.4 + 0.6 * np.abs(np.sin(2 * np.pi * 22 * x))) * np.sin(np.pi * x / 0.45)
    save("moth", flutter, 0.6)

    x = t(1.6)
    saw = 2 * ((70 * x + 0.3 * np.sin(2 * np.pi * 3 * x)) % 1.0) - 1
    growl = np.tanh(3 * (saw + 0.4 * lowpass(noise(1.6), 400))) * env(len(x), 0.08, 0.7, 0.3, release=0.3)
    save("boss_roar", lowpass(growl, 1800), 0.9)

    x = t(1.5)
    freq = 100 + 300 * (x / 1.5) ** 1.5
    hum = np.sin(2 * np.pi * np.cumsum(freq) / SR) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * x)) * (x / 1.5)
    save("boss_pulse", hum + 0.2 * bandpass(noise(1.5), 800, 3000) * (x / 1.5), 0.85)

    notes = [(0.0, 392, 0.18), (0.18, 523, 0.18), (0.36, 659, 0.18), (0.54, 784, 0.6)]
    parts = []
    for start, f, dur in notes:
        xx = t(dur + 0.2)
        saw = sum(np.sin(2 * np.pi * f * k * xx) / k for k in range(1, 8))
        parts.append((start, lowpass(saw, 2500) * env(len(xx), 0.02, dur * 0.8, 0.4, release=0.15)))
    save("fanfare", place(1.4, *parts), 0.85)
    print("sounds written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
