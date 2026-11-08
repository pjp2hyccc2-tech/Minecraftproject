"""Percussion: membranes and plates by modal synthesis, cymbals/shakers from shaped noise. Mono output.

Every function takes a velocity in [0, 1] and a numpy Generator, so each hit varies slightly but deterministically.
"""
import numpy as np

from . import dsp
from .dsp import SR, TAU, mtof
from .instruments import modal

MEMBRANE = [1.0, 1.594, 2.136, 2.296, 2.653, 2.918, 3.156, 3.501]
TIMPANI = [1.0, 1.504, 1.742, 2.0, 2.245, 2.494, 2.8, 2.979, 3.429]


def _glide_modal(f0, ratios, amps, taus, n, rng, glide=0.0, glide_time=0.05):
    """Membrane modes with a falling pitch at the strike (tension modulation)."""
    t = np.arange(n) / SR
    bend = 1.0 + glide * np.exp(-t / glide_time)
    base_phase = TAU * np.cumsum(bend) / SR
    out = np.zeros(n)
    for r, a, tau in zip(ratios, amps, taus):
        f = f0 * r
        if f > 0.45 * SR or a <= 0:
            continue
        m = min(n, int(tau * 7 * SR) + 1)
        out[:m] += a * np.exp(-t[:m] / tau) * np.sin(f * base_phase[:m] + rng.uniform(0, TAU))
    k = int(0.001 * SR)
    out[:k] *= np.linspace(0, 1, k)
    return out


def noise_burst(n, rng, lo, hi, decay, attack=0.0005):
    t = np.arange(n) / SR
    nz = rng.normal(size=n)
    nz = dsp.butter(nz, "bandpass", (lo, min(hi, 0.45 * SR)), 2)
    env = np.exp(-t / decay)
    k = max(1, int(attack * SR))
    env[:k] *= np.linspace(0, 1, k)
    return nz * env


def timpani(m, vel, rng, length=3.0):
    f0 = float(mtof(m))
    n = int(length * SR)
    b = 0.5 + vel
    amps = np.array([1.0, 0.8, 0.55, 0.5, 0.38, 0.3, 0.22, 0.16, 0.1]) * np.array([1] + [b] * 8)
    taus = np.array([1.9, 1.35, 1.0, 0.85, 0.7, 0.55, 0.45, 0.38, 0.3]) * (100 / f0) ** 0.2
    sig = _glide_modal(f0, TIMPANI, amps, taus, n, rng, glide=0.018, glide_time=0.07)
    t = np.arange(n) / SR
    sig += 0.5 * np.sin(TAU * f0 * 0.62 * t) * np.exp(-t / 0.09)
    sig += noise_burst(n, rng, 80, 1500 + 2500 * vel, 0.012) * 0.6 * vel
    return sig * vel ** 1.2 * 0.5


def taiko(vel, rng, size="odaiko", length=None):
    f0 = {"odaiko": 52.0, "chu": 92.0, "shime": 330.0}[size]
    f0 *= 1 + 0.02 * rng.normal()
    length = length or {"odaiko": 2.2, "chu": 1.2, "shime": 0.35}[size]
    n = int(length * SR)
    scale = {"odaiko": 1.6, "chu": 1.0, "shime": 0.25}[size]
    amps = np.array([1.0, 0.55, 0.35, 0.3, 0.18, 0.12, 0.08, 0.05])
    taus = np.array([0.8, 0.45, 0.32, 0.28, 0.2, 0.15, 0.12, 0.1]) * scale
    sig = _glide_modal(f0, MEMBRANE, amps, taus, n, rng, glide=0.35, glide_time=0.035)
    sig += noise_burst(n, rng, 60, 900 + 1500 * vel, 0.02) * 0.9 * vel
    sig += noise_burst(n, rng, 500, 2500, 0.012) * 0.25 * vel
    sig = dsp.soft_clip(sig * 1.3, 1.2)
    return sig * vel ** 1.1 * 0.55


def kick(vel, rng, length=0.6, tone=48.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = tone + (160 - tone) * np.exp(-t / 0.035)
    sig = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / 0.28)
    sig += noise_burst(n, rng, 2000, 8000, 0.003) * 0.35
    return dsp.soft_clip(sig * 1.2, 1.1) * vel * 0.6


def snare(vel, rng, length=0.45, bright=1.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    body = 0.6 * np.sin(TAU * 188 * t) * np.exp(-t / 0.07) + 0.35 * np.sin(TAU * 335 * t) * np.exp(-t / 0.045)
    wires = noise_burst(n, rng, 1400, 9000 * bright, 0.15)
    return (body * 0.7 + wires * 1.3) * vel * 0.4


def hat(vel, rng, open_=False):
    length = 0.6 if open_ else 0.12
    n = int(length * SR)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    for f in (205.3, 304.4, 369.6, 522.7, 540.0, 800.0):
        sig += np.sign(np.sin(TAU * f * 7.3 * t + rng.uniform(0, TAU)))
    sig = dsp.butter(sig, "highpass", 7000, 2) * 0.15 + dsp.butter(rng.normal(size=n), "highpass", 8000, 2) * 0.5
    sig *= np.exp(-t / (0.22 if open_ else 0.035))
    return sig * vel * 0.25


def crash(vel, rng, length=3.5, low=3000):
    n = int(length * SR)
    t = np.arange(n) / SR
    nz = dsp.butter(rng.normal(size=n), "highpass", low, 2)
    env = np.exp(-t / 0.9) * (0.4 + 0.6 * np.exp(-t / 0.12))
    sig = nz * env
    freqs = np.exp(rng.uniform(np.log(2500), np.log(11000), 30))
    sig += modal(freqs, np.full(30, 0.03), rng.uniform(0.6, 1.8, 30), n, rng)
    return sig * vel * 0.35


def suspended_cymbal_swell(dur, rng, peak=1.0):
    """Mallet roll crescendo on a cymbal (used before downbeats); ends at its peak."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    nz = dsp.butter(rng.normal(size=n), "bandpass", (2500, 12000), 2)
    shimmer = modal(np.exp(rng.uniform(np.log(3000), np.log(9000), 20)), np.full(20, 0.02),
                    np.full(20, 5.0), n, rng)
    env = (t / dur) ** 2.2
    return (nz * 0.8 + shimmer) * env * peak * 0.35


def tambourine(vel, rng, length=0.35):
    n = int(length * SR)
    freqs = rng.uniform(5000, 11000, 12)
    sig = modal(freqs, np.full(12, 0.08), rng.uniform(0.05, 0.14, 12), n, rng)
    sig += noise_burst(n, rng, 5000, 14000, 0.05) * 0.6
    return sig * vel * 0.45


def shaker(vel, rng, length=0.14):
    n = int(length * SR)
    t = np.arange(n) / SR
    nz = dsp.butter(rng.normal(size=n), "bandpass", (3500, 11000), 2)
    env = np.minimum(t / 0.018, 1.0) * np.exp(-np.maximum(t - 0.018, 0) / 0.035)
    return nz * env * vel * 0.4


def darbuka(stroke, vel, rng):
    if stroke == "doum":
        n = int(0.6 * SR)
        sig = _glide_modal(98.0, MEMBRANE, [1.0, 0.45, 0.3, 0.2, 0.1], [0.32, 0.18, 0.12, 0.1, 0.08], n, rng,
                           glide=0.12, glide_time=0.03)
        sig += noise_burst(n, rng, 100, 1200, 0.01) * 0.4
        return sig * vel * 0.6
    n = int(0.25 * SR)
    hi = 1.0 if stroke == "tek" else 0.6
    sig = modal([680.0, 1130.0, 1720.0], [0.5, 0.35, 0.2], [0.05, 0.035, 0.025], n, rng)
    sig += noise_burst(n, rng, 1500, 7000, 0.018 if stroke == "tek" else 0.012) * 1.1
    return sig * vel * hi * 0.45


def frame_drum(vel, rng, f0=72.0):
    n = int(1.0 * SR)
    sig = _glide_modal(f0, MEMBRANE, [1.0, 0.5, 0.3, 0.2, 0.12], [0.45, 0.25, 0.18, 0.12, 0.1], n, rng,
                       glide=0.15, glide_time=0.04)
    t = np.arange(n) / SR
    buzz = dsp.butter(rng.normal(size=n), "bandpass", (1200, 6000), 2) * np.abs(sig) * 1.5
    sig += buzz + noise_burst(n, rng, 200, 2500, 0.015) * 0.4
    return sig * vel * 0.55 * np.exp(-t / 2.0)


def zill(vel, rng):
    n = int(2.2 * SR)
    base = 2380.0 * (1 + 0.01 * rng.normal())
    ratios = np.array([1.0, 1.0035, 1.47, 2.09, 2.56, 3.3])
    sig = modal(base * ratios, [0.5, 0.4, 0.3, 0.25, 0.15, 0.08], [1.5, 1.4, 1.0, 0.7, 0.5, 0.3], n, rng)
    return sig * vel * 0.35


def woodblock(vel, rng, f0=900.0):
    n = int(0.15 * SR)
    sig = modal([f0, f0 * 2.6, f0 * 4.1], [1.0, 0.4, 0.15], [0.045, 0.025, 0.015], n, rng)
    return sig * vel * 0.45


def triangle(vel, rng):
    n = int(3.0 * SR)
    f = 1290.0
    sig = modal(f * np.array([1, 2.03, 3.04, 4.11, 5.2, 6.5]), [0.4, 0.5, 0.35, 0.3, 0.2, 0.1],
                [2.5, 2.0, 1.6, 1.2, 0.9, 0.6], n, rng)
    return sig * vel * 0.25


def gong(vel, rng, length=7.0, f0=68.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    count = 70
    freqs = np.exp(rng.uniform(np.log(f0 * 1.5), np.log(5000), count))
    out = np.zeros(n)
    for f in freqs:
        rise = 0.05 + 0.6 * (f / 5000) ** 0.5
        decay = 4.5 * (300 / f) ** 0.35
        out += (1 - np.exp(-t / rise)) * np.exp(-t / decay) * np.sin(TAU * f * t + rng.uniform(0, TAU)) * 0.03
    out += 0.5 * np.sin(TAU * f0 * t) * np.exp(-t / 3.5)
    out += noise_burst(n, rng, 60, 600, 0.05) * 0.5
    return out * vel * 0.5


def anvil(vel, rng, f0=1050.0, length=2.0):
    n = int(length * SR)
    ratios = [1.0, 2.39, 2.98, 4.15, 5.2, 6.9]
    sig = modal(np.array(ratios) * f0, [1.0, 0.6, 0.45, 0.3, 0.2, 0.12], [1.1, 0.75, 0.6, 0.4, 0.3, 0.2], n, rng)
    sig += noise_burst(n, rng, 2000, 12000, 0.006) * 0.8
    return sig * vel * 0.35


def boom(vel, rng, length=2.5, top=70.0, bottom=30.0):
    """Cinematic low impact."""
    n = int(length * SR)
    t = np.arange(n) / SR
    f = bottom + (top - bottom) * np.exp(-t / 0.35)
    sig = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / 0.9)
    sig += dsp.butter(rng.normal(size=n), "lowpass", 240, 2) * np.exp(-t / 0.4) * 0.8
    return dsp.soft_clip(sig * 1.4, 1.3) * vel * 0.6


def reverse_swell(dur, rng, lo=300, hi=9000):
    """Noise that swells up to a cut (reverse-cymbal feel)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    nz = dsp.butter(rng.normal(size=n), "bandpass", (lo, hi), 2)
    return nz * (t / dur) ** 3 * 0.5


def clock_tick(vel, rng, f0=2400.0):
    n = int(0.06 * SR)
    sig = modal([f0, f0 * 1.9, f0 * 3.3], [1.0, 0.5, 0.2], [0.012, 0.008, 0.005], n, rng)
    return sig * vel * 0.3
