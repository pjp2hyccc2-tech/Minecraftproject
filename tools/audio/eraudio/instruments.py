"""Synthesised instruments. Every render() returns stereo (2, n) float64 starting at the note onset, including the
release tail. Pitches are MIDI numbers; durations in seconds; velocity in [0, 1].

Techniques: band-limited wavetables built per note from an absolute-frequency spectral envelope (body resonances,
formants) with detuned ensembles, vibrato and random pitch drift; modal synthesis (sums of decaying partials) for
plucked strings, piano, bells and drums.
"""
import numpy as np

from . import dsp
from .dsp import SR, TAU, mtof

TABLE_SIZE = 2048


# ------------------------------------------------------------------------------------------------- oscillators

def table_from_amps(amps, phases=None):
    n = min(len(amps), TABLE_SIZE // 2 - 1)
    spec = np.zeros(TABLE_SIZE // 2 + 1, dtype=complex)
    ph = np.zeros(n) if phases is None else np.asarray(phases[:n])
    spec[1:n + 1] = np.asarray(amps[:n]) * np.exp(1j * (ph - np.pi / 2)) * (TABLE_SIZE / 2)
    tab = np.fft.irfft(spec, TABLE_SIZE)
    return np.append(tab, tab[0])


def osc(table, phase):
    pos = (phase % 1.0) * TABLE_SIZE
    i = pos.astype(np.int64)
    fr = pos - i
    return table[i] * (1.0 - fr) + table[i + 1] * fr


def harmonic_count(f0, headroom=1.04):
    return max(1, int(0.45 * SR / (f0 * headroom)))


def lp_shape(f, fc, order=2.0):
    return 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def bumps(f, spec):
    """spec: list of (center_hz, gain_db, width_octaves) log-gaussian resonances; returns linear gain."""
    g = np.zeros_like(f, dtype=float)
    lf = np.log2(np.maximum(f, 1.0))
    for fc, gdb, w in spec:
        g += gdb * np.exp(-0.5 * ((lf - np.log2(fc)) / w) ** 2)
    return 10 ** (g / 20.0)


def freq_track(f0, n, rng, detune=0.0, vib_rate=5.5, vib_depth=0.0, vib_delay=0.25, drift=0.0, scoop=0.0,
               scoop_time=0.05, glide_from=None, glide_time=0.08):
    t = np.arange(n) / SR
    cents = np.full(n, float(detune))
    if drift:
        k = max(4, int(n / SR * 1.5) + 4)
        pts = rng.uniform(-1, 1, k)
        cents += drift * np.interp(t, np.linspace(0, n / SR + 1e-9, k), pts)
    if vib_depth:
        onset = np.clip((t - vib_delay) / 0.35, 0.0, 1.0)
        rate = vib_rate * (1 + 0.04 * rng.uniform(-1, 1))
        cents += vib_depth * onset * np.sin(TAU * rate * t + rng.uniform(0, TAU))
    if scoop:
        cents += scoop * np.exp(-t / scoop_time)
    if glide_from is not None:
        cents += 100.0 * (glide_from) * np.exp(-t / glide_time)
    return f0 * 2.0 ** (cents / 1200.0)


def vel_amp(vel):
    return float(np.clip(vel, 0.05, 1.2)) ** 1.3


# ------------------------------------------------------------------------------------------------- spectra

def spec_strings(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    fc = 1700 + 4800 * bright
    body = bumps(f, [(290, 4, 0.45), (1050, 2.5, 0.5), (2600, 3 + 2 * bright, 0.55), (1600, -2.5, 0.3)])
    return (1.0 / k) * lp_shape(f, fc, 1.6) * body


def spec_cello(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    fc = 1100 + 3000 * bright
    body = bumps(f, [(200, 5, 0.5), (620, 3, 0.5), (1500, 2, 0.5)])
    return (1.0 / k ** 1.05) * lp_shape(f, fc, 1.6) * body


def spec_brass(f0, bright, formant=(500, 5, 0.6)):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    fc = 450 + 4200 * bright ** 1.3
    return (1.0 / k ** 0.75) * lp_shape(f, fc, 1.3) * bumps(f, [formant])


def spec_horn(f0, bright):
    return spec_brass(f0, bright * 0.55, (420, 6, 0.6))


def spec_trumpet(f0, bright):
    return spec_brass(f0, 0.35 + 0.65 * bright, (1300, 6, 0.55))


def spec_trombone(f0, bright):
    return spec_brass(f0, 0.2 + 0.6 * bright, (600, 5, 0.6))


def spec_flute(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    base = np.array([1.0, 0.32 + 0.2 * bright, 0.12 + 0.1 * bright, 0.07, 0.035, 0.02, 0.012, 0.008])
    amps = np.zeros(len(k))
    m = min(len(k), len(base))
    amps[:m] = base[:m]
    return amps * lp_shape(k * f0, 5000, 2)


def spec_ocarina(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    amps = np.zeros(len(k))
    amps[0] = 1.0
    if len(k) > 1:
        amps[1] = 0.06 + 0.05 * bright
    if len(k) > 2:
        amps[2] = 0.03
    return amps


def spec_oboe(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    return (1.0 / k ** 0.6) * bumps(f, [(1350, 9, 0.45), (3000, 4, 0.4)]) * lp_shape(f, 3500 + 3000 * bright, 2)


def spec_clarinet(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    odd = np.where(k % 2 == 1, 1.0, 0.18 + 0.2 * (f > 1500))
    return odd / k * lp_shape(f, 2500 + 2500 * bright, 2)


def spec_ney(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    f = k * f0
    return (1.0 / k ** 1.6) * lp_shape(f, 2600 + 1500 * bright, 2) * bumps(f, [(1200, 3, 0.5)])


def spec_saw(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    return (1.0 / k) * lp_shape(k * f0, 600 + 7000 * bright ** 1.5, 1.5)


def spec_square(f0, bright):
    k = np.arange(1, harmonic_count(f0) + 1)
    amps = np.where(k % 2 == 1, 1.0 / k, 0.0)
    return amps * lp_shape(k * f0, 800 + 6000 * bright, 1.5)


VOWELS = {
    # (formant Hz, gain dB, bandwidth Hz) — classic singer formant tables
    "sop_a": [(800, 0, 80), (1150, -6, 90), (2900, -32, 120), (3900, -20, 130), (4950, -50, 140)],
    "alt_a": [(800, 0, 80), (1150, -4, 90), (2800, -20, 120), (3500, -36, 130), (4950, -60, 140)],
    "ten_a": [(650, 0, 80), (1080, -6, 90), (2650, -7, 120), (2900, -8, 130), (3250, -22, 140)],
    "bas_a": [(600, 0, 60), (1040, -7, 70), (2250, -9, 110), (2450, -9, 120), (2750, -20, 130)],
    "sop_o": [(450, 0, 70), (800, -11, 80), (2830, -22, 100), (3800, -22, 130), (4950, -50, 135)],
    "alt_o": [(450, 0, 70), (800, -9, 80), (2830, -16, 100), (3500, -28, 130), (4950, -55, 135)],
    "ten_o": [(400, 0, 40), (800, -10, 80), (2600, -12, 100), (2800, -12, 120), (3000, -26, 120)],
    "bas_o": [(400, 0, 40), (750, -11, 80), (2400, -21, 100), (2600, -20, 120), (2900, -40, 120)],
    "sop_u": [(325, 0, 50), (700, -16, 60), (2700, -35, 170), (3800, -40, 180), (4950, -60, 200)],
    "bas_u": [(350, 0, 40), (600, -20, 80), (2400, -32, 100), (2675, -28, 120), (2950, -36, 120)],
    "sop_e": [(350, 0, 60), (2000, -20, 100), (2800, -15, 120), (3600, -40, 150), (4950, -56, 200)],
}


def formant_env(f, vowel, bw_scale=1.8):
    env = np.full_like(f, 0.004, dtype=float)
    for fc, gdb, bw in VOWELS[vowel]:
        b = bw * bw_scale / 2
        env += 10 ** (gdb / 20) / np.sqrt(1 + ((f - fc) / b) ** 2)
    return env


def spec_choir(vowel):
    def spec(f0, bright):
        k = np.arange(1, harmonic_count(f0) + 1)
        f = k * f0
        src = (1.0 / k ** (1.15 - 0.25 * bright)) * lp_shape(f, 5000, 1)
        return src * formant_env(f, vowel)
    return spec


def spec_organ(stops):
    """stops: dict of footage -> level; the table is built at the 16' pitch (f0/2) so every stop is harmonic."""
    def spec(f0, bright):
        base = f0 / 2.0
        k = np.arange(1, harmonic_count(base) + 1)
        amps = np.zeros(len(k))
        principal = np.array([1.0, 0.45, 0.22, 0.14, 0.08, 0.05, 0.035, 0.02])
        flute = np.array([1.0, 0.08, 0.15, 0.02, 0.05])
        for foot, level in stops.items():
            mult = {"16": 1, "8": 2, "4": 4, "2-2/3": 6, "2": 8, "1-3/5": 10, "mixture": 12, "reed": 2}[foot]
            if foot == "reed":
                for j in range(1, 40):
                    h = mult * j
                    if h <= len(k):
                        amps[h - 1] += level * (1.0 / j ** 0.7) * lp_shape(np.array(h * base), 2500 + 2000 * bright, 2)
                continue
            shape = flute if foot == "16" else principal
            for j, a in enumerate(shape, start=1):
                h = mult * j
                if h <= len(k):
                    amps[h - 1] += level * a
            if foot == "mixture":
                for h in (16, 24):
                    if h <= len(k):
                        amps[h - 1] += level * 0.6
        return amps * lp_shape(k * base, 7000, 2)
    return spec


# ------------------------------------------------------------------------------------------------- ensembles

class WaveEnsemble:
    """Detuned ensemble of wavetable voices: strings, brass, winds, choir, organ, pads."""

    def __init__(self, name, spectrum, voices=3, detune=6.0, vib_rate=5.5, vib_depth=8.0, vib_delay=0.25,
                 drift=3.0, attack=0.08, decay=0.0, sustain=1.0, release=0.3, spread=0.5, bright=0.5,
                 vel_bright=0.5, blat=0.0, blat_time=0.18, scoop=0.0, scoop_time=0.05, noise=0.0,
                 noise_band=(1500, 7000), amp=1.0, octave_table=False, timing=0.012, tremolo=0.0,
                 chiff=0.0, attack_shape="smooth", portamento=0.0):
        self.name = name
        self.portamento = portamento
        self.spectrum = spectrum
        self.voices = voices
        self.detune = detune
        self.vib_rate = vib_rate
        self.vib_depth = vib_depth
        self.vib_delay = vib_delay
        self.drift = drift
        self.attack = attack
        self.decay = decay
        self.sustain = sustain
        self.release = release
        self.spread = spread
        self.bright = bright
        self.vel_bright = vel_bright
        self.blat = blat
        self.blat_time = blat_time
        self.scoop = scoop
        self.scoop_time = scoop_time
        self.noise = noise
        self.noise_band = noise_band
        self.amp = amp
        self.timing = timing
        self.tremolo = tremolo
        self.chiff = chiff
        self.attack_shape = attack_shape
        self._tables = {}

    def tables(self, m):
        key = int(round(m * 4))
        if key not in self._tables:
            f0 = float(mtof(m))
            self._tables[key] = (table_from_amps(self.spectrum(f0, 0.0)), table_from_amps(self.spectrum(f0, 1.0)))
        return self._tables[key]

    def render(self, m, dur, vel, rng, attack=None, release=None, glide_from=None, swell=0.0, pan=0.0):
        f0 = float(mtof(m))
        a = self.attack if attack is None else attack
        r = self.release if release is None else release
        env = dsp.adsr(dur, a, self.decay, self.sustain, r, attack_shape=self.attack_shape)
        n = env.size
        t = np.arange(n) / SR
        if swell:
            env *= np.clip(1.0 - swell + swell * t / max(dur, 1e-3), 0.0, 1.0)
        dark, bright_t = self.tables(m)
        b = np.clip(self.bright + self.vel_bright * (vel - 0.6), 0.0, 1.0)
        if self.blat:
            bump = (1 - np.exp(-t / 0.025)) * np.exp(-t / self.blat_time)
            b = np.clip(b + self.blat * vel * bump, 0.0, 1.0)
        out = np.zeros((2, n))
        nv = self.voices
        for v in range(nv):
            pos = (v - (nv - 1) / 2) / max(1.0, (nv - 1) / 2) if nv > 1 else 0.0
            det = self.detune * pos + rng.normal() * self.detune * 0.15
            offset = int(abs(rng.normal()) * self.timing * SR) if nv > 1 else 0
            f = freq_track(f0, n, rng, det, self.vib_rate, self.vib_depth, self.vib_delay, self.drift,
                           self.scoop * (0.7 + 0.6 * rng.uniform()) if glide_from is None else 0.0,
                           self.scoop_time, glide_from, max(self.portamento, 0.01))
            ph = np.cumsum(f) / SR + rng.uniform()
            if np.isscalar(b):
                sig = osc(dark, ph) * (1 - b) + osc(bright_t, ph) * b
            else:
                sig = osc(dark, ph) * (1 - b) + osc(bright_t, ph) * b
            if offset:
                sig = np.concatenate([np.zeros(offset), sig[:-offset]])
            gl, gr = dsp.pan_gains(np.clip(pan + self.spread * pos, -1, 1))
            out[0] += sig * gl
            out[1] += sig * gr
        out /= np.sqrt(nv)
        if self.tremolo:
            out *= 1 + self.tremolo * np.sin(TAU * 5.1 * t + rng.uniform(0, TAU))
        out *= env
        if self.noise:
            nz = rng.normal(size=n)
            nz = dsp.butter(nz, "bandpass", self.noise_band, 2)
            nz *= env * self.noise
            out += dsp.to_stereo(nz, pan)
        if self.chiff:
            k = min(n, int(0.035 * SR))
            ch = rng.normal(size=k) * np.exp(-np.arange(k) / (0.008 * SR))
            ch = dsp.butter(ch, "bandpass", (min(f0 * 2, 9000), min(f0 * 6, 15000)), 2)
            out[:, :k] += dsp.to_stereo(ch * self.chiff * vel, pan)
        return out * self.amp * vel_amp(vel)


def strings(**kw):
    p = dict(voices=5, detune=7.0, vib_rate=5.6, vib_depth=9.0, vib_delay=0.22, drift=3.0, attack=0.12,
             release=0.35, spread=0.6, bright=0.45, vel_bright=0.6, blat=0.25, blat_time=0.15, noise=0.012,
             noise_band=(2500, 9000), timing=0.010)
    p.update(kw)
    return WaveEnsemble("strings", spec_strings, **p)


def cellos(**kw):
    p = dict(voices=4, detune=5.0, vib_rate=5.2, vib_depth=7.0, vib_delay=0.25, drift=2.5, attack=0.12,
             release=0.4, spread=0.4, bright=0.4, vel_bright=0.6, blat=0.2, noise=0.008, noise_band=(1500, 6000))
    p.update(kw)
    return WaveEnsemble("cellos", spec_cello, **p)


def horns(**kw):
    p = dict(voices=3, detune=4.0, vib_rate=5.0, vib_depth=5.0, vib_delay=0.35, drift=2.0, attack=0.05,
             release=0.25, spread=0.35, bright=0.35, vel_bright=0.8, blat=0.35, blat_time=0.2, scoop=-25,
             scoop_time=0.04, noise=0.004, noise_band=(800, 3000), timing=0.012)
    p.update(kw)
    return WaveEnsemble("horns", spec_horn, **p)


def trumpets(**kw):
    p = dict(voices=2, detune=3.5, vib_rate=5.3, vib_depth=6.0, vib_delay=0.3, drift=1.5, attack=0.035,
             release=0.2, spread=0.3, bright=0.4, vel_bright=0.7, blat=0.4, blat_time=0.15, scoop=-20,
             scoop_time=0.03, noise=0.004, noise_band=(1500, 5000), timing=0.008)
    p.update(kw)
    return WaveEnsemble("trumpets", spec_trumpet, **p)


def trombones(**kw):
    p = dict(voices=3, detune=4.0, vib_rate=4.8, vib_depth=3.0, vib_delay=0.4, drift=2.0, attack=0.05,
             release=0.25, spread=0.3, bright=0.3, vel_bright=0.8, blat=0.45, blat_time=0.2, scoop=-20,
             scoop_time=0.04, noise=0.003, noise_band=(600, 2500))
    p.update(kw)
    return WaveEnsemble("trombones", spec_trombone, **p)


def flute(**kw):
    p = dict(voices=1, detune=0.0, vib_rate=5.0, vib_depth=11.0, vib_delay=0.2, drift=2.0, attack=0.05,
             release=0.15, spread=0.0, bright=0.4, vel_bright=0.5, noise=0.035, noise_band=(1800, 9000),
             tremolo=0.04, chiff=0.25)
    p.update(kw)
    return WaveEnsemble("flute", spec_flute, **p)


def ocarina(**kw):
    p = dict(voices=1, vib_rate=5.2, vib_depth=7.0, vib_delay=0.18, drift=1.5, attack=0.04, release=0.12,
             bright=0.3, noise=0.02, noise_band=(1500, 6000), tremolo=0.03, chiff=0.15)
    p.update(kw)
    return WaveEnsemble("ocarina", spec_ocarina, **p)


def oboe(**kw):
    p = dict(voices=1, vib_rate=5.6, vib_depth=8.0, vib_delay=0.18, drift=1.5, attack=0.04, release=0.12,
             bright=0.4, vel_bright=0.5, noise=0.006, noise_band=(1500, 5000), chiff=0.05)
    p.update(kw)
    return WaveEnsemble("oboe", spec_oboe, **p)


def clarinet(**kw):
    p = dict(voices=1, vib_rate=5.0, vib_depth=3.0, vib_delay=0.3, drift=1.0, attack=0.05, release=0.12,
             bright=0.4, vel_bright=0.5, noise=0.005, noise_band=(1500, 5000))
    p.update(kw)
    return WaveEnsemble("clarinet", spec_clarinet, **p)


def ney(**kw):
    p = dict(voices=1, vib_rate=4.8, vib_depth=16.0, vib_delay=0.25, drift=4.0, attack=0.09, release=0.18,
             bright=0.35, vel_bright=0.4, noise=0.08, noise_band=(700, 6000), tremolo=0.06, chiff=0.35)
    p.update(kw)
    return WaveEnsemble("ney", spec_ney, **p)


def choir(vowel="sop_a", **kw):
    p = dict(voices=6, detune=9.0, vib_rate=5.4, vib_depth=16.0, vib_delay=0.15, drift=4.0, attack=0.35,
             release=0.6, spread=0.7, bright=0.5, vel_bright=0.5, noise=0.01, noise_band=(2500, 7000),
             timing=0.03)
    p.update(kw)
    return WaveEnsemble("choir_" + vowel, spec_choir(vowel), **p)


def organ(stops=None, **kw):
    stops = stops or {"16": 0.5, "8": 1.0, "4": 0.6, "2-2/3": 0.25, "2": 0.35, "mixture": 0.2}
    p = dict(voices=2, detune=2.0, vib_depth=0.0, drift=0.7, attack=0.045, release=0.14, spread=0.8,
             bright=0.5, vel_bright=0.0, chiff=0.12, timing=0.004)
    p.update(kw)
    return WaveEnsemble("organ", spec_organ(stops), **p)


def pad(spectrum=spec_saw, **kw):
    p = dict(voices=4, detune=11.0, vib_rate=4.0, vib_depth=0.0, drift=4.0, attack=0.9, release=1.6,
             spread=0.85, bright=0.18, vel_bright=0.2, timing=0.0)
    p.update(kw)
    return WaveEnsemble("pad", spectrum, **p)


def synth_bass(**kw):
    p = dict(voices=2, detune=6.0, vib_depth=0.0, drift=0.5, attack=0.004, decay=0.25, sustain=0.7,
             release=0.08, spread=0.0, bright=0.25, vel_bright=0.4, blat=0.35, blat_time=0.08, timing=0.0,
             attack_shape="linear")
    p.update(kw)
    return WaveEnsemble("synth_bass", spec_saw, **p)


# ------------------------------------------------------------------------------------------------- modal

def modal(freqs, amps, taus, n, rng=None, attack=0.0015, phases=None, coherent=False):
    """Sum of exponentially decaying partials. coherent=True starts every partial in sine phase (struck/plucked)."""
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, (f, a, tau) in enumerate(zip(freqs, amps, taus)):
        if f >= 0.47 * SR or a <= 0 or tau <= 0:
            continue
        m = min(n, int(tau * 7.0 * SR) + 1)
        if phases is not None:
            ph = phases[i]
        elif coherent or rng is None:
            ph = 0.0
        else:
            ph = rng.uniform(0, TAU)
        out[:m] += a * np.exp(-t[:m] / tau) * np.sin(TAU * f * t[:m] + ph)
    if attack:
        k = min(n, int(attack * SR))
        out[:k] *= np.linspace(0, 1, k)
    return out


class Pluck:
    """Plucked string by modal synthesis: harp, nylon guitar/lute, oud, koto, pizzicato, bass."""

    KINDS = {
        # pluck position, decay of the fundamental at A3 (s), high-partial damping, body resonances, brightness
        "harp": dict(pos=0.18, tau=2.8, damp=0.10, body=[(350, 2, 0.6), (1200, 1, 0.6)], bright=7.0, max_dur=6.0,
                     inharm=0.00008, ring=True),
        "lute": dict(pos=0.16, tau=1.6, damp=0.16, body=[(110, 4, 0.4), (220, 3, 0.4), (450, 2, 0.5)], bright=6.0,
                     max_dur=4.0, inharm=0.0001, ring=False),
        "oud": dict(pos=0.11, tau=1.1, damp=0.2, body=[(150, 4, 0.4), (330, 3, 0.4), (1200, 2, 0.5)], bright=8.0,
                    max_dur=3.0, inharm=0.00015, ring=False),
        "koto": dict(pos=0.09, tau=2.0, damp=0.12, body=[(180, 3, 0.5), (900, 3, 0.5)], bright=9.0, max_dur=4.5,
                     inharm=0.0002, ring=True),
        "pizz": dict(pos=0.25, tau=0.45, damp=0.35, body=[(290, 4, 0.45), (1000, 2, 0.5)], bright=4.0,
                     max_dur=1.2, inharm=0.0001, ring=False),
        "bass": dict(pos=0.22, tau=1.2, damp=0.3, body=[(80, 4, 0.5), (200, 2, 0.5)], bright=3.5, max_dur=3.0,
                     inharm=0.00005, ring=False),
        "kalimba": dict(pos=0.5, tau=1.2, damp=0.6, body=[(500, 2, 0.6)], bright=2.0, max_dur=3.0, inharm=0.0,
                        ring=True),
    }

    def __init__(self, kind="harp", amp=1.0, release=0.12):
        self.kind = kind
        self.p = self.KINDS[kind]
        self.amp = amp
        self.release = release

    def render(self, m, dur, vel, rng, pan=0.0, **_):
        p = self.p
        f0 = float(mtof(m))
        ring = p["ring"]
        length = p["max_dur"] if ring else min(p["max_dur"], dur + 0.25)
        tau1 = p["tau"] * (220.0 / f0) ** 0.45
        n = int(length * SR)
        kmax = min(60, harmonic_count(f0, 1.0))
        k = np.arange(1, kmax + 1)
        fk = k * f0 * np.sqrt(1 + p["inharm"] * k ** 2)
        bright = p["bright"] * (0.5 + vel)
        amps = np.abs(np.sin(np.pi * k * p["pos"])) / k ** 0.9 * np.exp(-(k - 1) / bright)
        amps *= bumps(fk, p["body"])
        taus = tau1 / (1 + p["damp"] * (k - 1) ** 1.25) * (1 + 0.06 * rng.normal(size=kmax))
        sig = modal(fk, amps, np.maximum(taus, 0.01), n, rng, attack=0.0004, coherent=True)
        # pluck transient
        kn = int(0.004 * SR)
        nz = rng.normal(size=kn) * np.exp(-np.arange(kn) / (0.0012 * SR))
        sig[:kn] += dsp.butter(nz, "bandpass", (min(f0 * 3, 8000), min(f0 * 12, 16000)), 2) * 0.08 * vel
        if not ring:
            t = np.arange(n) / SR
            sig *= np.where(t > dur, np.exp(-(t - dur) / 0.07), 1.0)
        sig *= self.amp * vel_amp(vel) * 0.6
        return dsp.to_stereo(sig, pan)


class Piano:
    """Modal piano: inharmonic partials, 2-3 detuned strings per note (beating), two-stage decay, dampers."""

    def __init__(self, amp=1.0, hardness=1.0, release=0.15):
        self.amp = amp
        self.hardness = hardness
        self.release = release

    def render(self, m, dur, vel, rng, pan=0.0, sustain_pedal=False, **_):
        f0 = float(mtof(m))
        b = 0.00012 * 2 ** ((m - 60) / 18.0)
        tau1 = float(np.clip(7.0 * (130.0 / f0) ** 0.55, 0.6, 14.0))
        natural = min(tau1 * 3.5, 12.0)
        length = natural if sustain_pedal else min(natural, dur + 0.5)
        n = int(length * SR)
        kmax = min(48, harmonic_count(f0, 1.0))
        k = np.arange(1, kmax + 1)
        fk = k * f0 * np.sqrt(1 + b * k ** 2)
        hard = (3.5 + 12 * vel) * self.hardness
        amps = np.exp(-(k - 1) / hard) / k ** 0.6 * (0.35 + 0.65 * np.abs(np.sin(np.pi * k / 7.3)))
        taus = tau1 / (1 + 0.22 * (k - 1) ** 1.1)
        t = np.arange(n) / SR
        sig = np.zeros(n)
        strings = 1 if m < 40 else (2 if m < 52 else 3)
        dets = np.linspace(-0.9, 0.9, strings) if strings > 1 else [0.0]
        for d in dets:
            r = 2 ** ((d + rng.normal() * 0.25) / 1200)
            fast = modal(fk * r, amps * 0.62, taus * 0.22, n, rng, attack=0.0006, coherent=True)
            slow = modal(fk * r, amps * 0.38, taus, n, rng, attack=0.0006, coherent=True)
            sig += fast + slow
        sig /= strings
        kn = int(0.006 * SR)
        nz = rng.normal(size=kn) * np.exp(-np.arange(kn) / (0.0015 * SR))
        sig[:kn] += dsp.butter(nz, "lowpass", 2500 + 3000 * vel, 2) * 0.03 * vel
        sig += 0.04 * vel * np.sin(TAU * 62 * t) * np.exp(-t / 0.04)
        if not sustain_pedal:
            sig *= np.where(t > dur, np.exp(-(t - dur) / 0.11), 1.0)
        sig *= self.amp * vel_amp(vel) * 0.5
        return dsp.to_stereo(sig, pan)


BELLS = {
    # partial ratios, relative amplitudes, decay (s) of each partial at the reference pitch
    "glock": ([1.0, 2.756, 5.404, 8.933], [1.0, 0.45, 0.22, 0.1], [2.2, 0.8, 0.35, 0.15]),
    "celesta": ([1.0, 2.005, 3.97, 6.8], [1.0, 0.08, 0.18, 0.04], [1.6, 0.6, 0.4, 0.2]),
    "musicbox": ([1.0, 2.0, 5.95, 9.5], [1.0, 0.15, 0.12, 0.05], [1.4, 0.5, 0.2, 0.1]),
    "church": ([0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166, 5.433],
               [0.55, 0.75, 0.6, 0.35, 1.0, 0.4, 0.3, 0.3, 0.2, 0.12],
               [7.0, 5.0, 4.0, 3.0, 3.2, 2.0, 1.8, 1.6, 1.0, 0.7]),
    "tubular": ([1.0, 2.76, 5.40, 8.93, 13.34], [1.0, 0.6, 0.4, 0.2, 0.1], [4.0, 2.6, 1.6, 0.9, 0.5]),
    "vibes": ([1.0, 3.99, 10.0], [1.0, 0.12, 0.03], [2.5, 0.8, 0.3]),
}


class Bell:
    def __init__(self, kind="glock", amp=1.0, decay_scale=1.0, release=0.0, tremolo=0.0):
        self.kind = kind
        self.amp = amp
        self.decay_scale = decay_scale
        self.release = release
        self.tremolo = tremolo

    def render(self, m, dur, vel, rng, pan=0.0, **_):
        ratios, amps, taus = BELLS[self.kind]
        f0 = float(mtof(m))
        scale = self.decay_scale * (523.0 / f0) ** 0.3
        taus = np.array(taus) * scale
        length = min(10.0, float(np.max(taus)) * 5.0)
        n = int(length * SR)
        a = np.array(amps) * np.array([1.0] + [0.6 + 0.6 * vel] * (len(amps) - 1))
        fr = np.array(ratios) * f0 * (1 + 0.0008 * rng.normal(size=len(ratios)))
        sig = modal(fr, a, taus, n, rng, attack=0.0006)
        # slightly detuned pair on the prime for a shimmering beat
        sig += modal(fr[:2] * 1.0012, a[:2] * 0.3, taus[:2], n, rng, attack=0.0006)
        if self.tremolo:
            t = np.arange(n) / SR
            sig *= 1 + self.tremolo * np.sin(TAU * 5.5 * t)
        kn = int(0.003 * SR)
        sig[:kn] += rng.normal(size=kn) * np.linspace(1, 0, kn) * 0.05 * vel
        return dsp.to_stereo(sig * self.amp * vel_amp(vel) * 0.45, pan)


class FMBell:
    """Two-operator FM: glassy crystal / ice tones."""

    def __init__(self, ratio=3.5, index=3.0, index_decay=0.25, decay=1.6, amp=1.0, release=0.0):
        self.ratio = ratio
        self.index = index
        self.index_decay = index_decay
        self.decay = decay
        self.amp = amp
        self.release = release

    def render(self, m, dur, vel, rng, pan=0.0, **_):
        f0 = float(mtof(m))
        length = self.decay * 5
        n = int(length * SR)
        t = np.arange(n) / SR
        idx = self.index * (0.5 + vel) * np.exp(-t / self.index_decay)
        mod = np.sin(TAU * f0 * self.ratio * t)
        sig = np.sin(TAU * f0 * t + idx * mod) * np.exp(-t / (self.decay * (440 / f0) ** 0.2))
        k = int(0.002 * SR)
        sig[:k] *= np.linspace(0, 1, k)
        return dsp.to_stereo(sig * self.amp * vel_amp(vel) * 0.35, pan)


def play_notes(song, bus, inst, notes, at=0.0, vel=1.0, transpose=0, pan=0.0, legato=1.0, seed="",
               humanize=0.006, mono=False, shape=0.25, **kw):
    """Schedules parsed notes (theory.Note) on a bus. `at` is the start in beats.

    mono: connected notes of a single-line part are played legato (overlap, soft re-attack, portamento if the
    instrument has one). shape: phrase dynamics, higher notes of the phrase up to `shape` louder.
    """
    rng = dsp.rng_for(song.name, bus.name, getattr(inst, "name", type(inst).__name__), seed, at)
    notes = sorted(notes, key=lambda n: n.start)
    pitches = [p for n in notes for p in n.pitches]
    lo, hi = (min(pitches), max(pitches)) if pitches else (0, 1)
    wave = isinstance(inst, WaveEnsemble)

    def connected(a, b):
        return (a is not None and b is not None and not a.short and len(a.pitches) == 1 and len(b.pitches) == 1
                and -1e-6 < b.start - (a.start + a.dur) < 0.02)

    for i, n in enumerate(notes):
        start_beat = at + n.start
        d = n.dur * song.beat * (0.5 if n.short else legato)
        extra = dict(kw)
        if mono and wave:
            prev = notes[i - 1] if i > 0 else None
            nxt = notes[i + 1] if i + 1 < len(notes) else None
            if connected(n, nxt):
                d = n.dur * song.beat + 0.035
                extra["release"] = 0.09
            if connected(prev, n):
                extra["attack"] = 0.03
                if inst.portamento:
                    extra["glide_from"] = prev.pitches[0] - n.pitches[0]
        for p in n.pitches:
            off = rng.normal() * humanize
            contour = 1.0 + shape * ((p - lo) / max(1, hi - lo) - 0.5)
            v = n.vel * vel * contour * (1 + 0.04 * rng.normal())
            audio = inst.render(p + transpose, max(0.03, d), float(np.clip(v, 0.05, 1.2)), rng, pan=pan, **extra)
            song.add(bus, song.seconds(start_beat) + off, audio)
