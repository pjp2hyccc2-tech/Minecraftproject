"""Arrangement and mixing: a Song holds buses; notes are added at times in beats; mix() renders the stereo master.

Looping songs wrap every sound that runs past the loop end (release tails, reverb, delays, chorus) onto the start,
so the file is exactly one period of an endlessly repeating piece: no seam, no fade at the loop point.
"""
import numpy as np

from . import dsp, theory
from .dsp import SR


REFERENCE_LUFS = -20.0


class Bus:
    """A mixer channel. `level` (LU relative to the mix reference) normalises the bus's own integrated loudness
    (measured while it plays), so balances are explicit: e.g. melody 0, countermelody -4, pads -9."""

    def __init__(self, song, name, level=None, gain_db=0.0, pan=0.0, width=1.0, eq=(), comp=None, chorus=None,
                 sends=None, drive=0.0):
        self.song = song
        self.name = name
        self.level = level
        self.gain_db = gain_db
        self.pan = pan
        self.width = width
        self.eq = list(eq)
        self.comp = comp
        self.chorus = chorus
        self.sends = dict(sends or {})
        self.drive = drive
        self.buf = np.zeros((2, song.total))


class Song:
    def __init__(self, name, bpm, bars, beats_per_bar=4, loop=True, tail=0.0, pickup_beats=0.0):
        self.name = name
        self.bpm = bpm
        self.beat = 60.0 / bpm
        self.beats_per_bar = beats_per_bar
        self.bars = bars
        self.loop = loop
        self.length = int(round(bars * beats_per_bar * self.beat * SR))
        self.total = self.length + (0 if loop else int(tail * SR))
        self.buses = {}
        self.returns = {}
        self.chords = []        # (start_beat, end_beat, Chord)
        self.warnings = []
        self.master_eq = [("peak", 320, 0.8, -1.5), ("highshelf", 7000, 0.7, 2.0)]
        self.lfo_period = self.length / SR    # modulation repeats exactly once per musical period
        self.debug = []

    # ----------------------------------------------------------------------------------------------- time

    def seconds(self, beat):
        return beat * self.beat

    def bar(self, b, beat=0.0):
        """Beat number of bar b (0-based) plus an offset in beats."""
        return b * self.beats_per_bar + beat

    # ----------------------------------------------------------------------------------------------- buses

    def bus(self, name, **settings):
        if name not in self.buses:
            self.buses[name] = Bus(self, name, **settings)
        return self.buses[name]

    def reverb(self, name, rt60=2.2, predelay=0.02, gain_db=0.0, hp=180.0, lp=9000.0, **ir_kw):
        self.returns[name] = dict(kind="reverb", rt60=rt60, predelay=predelay, gain_db=gain_db, hp=hp, lp=lp,
                                  ir=ir_kw)

    def delay(self, name, beats=0.75, feedback=0.35, gain_db=0.0, taps=6, hp=300.0, lp=5000.0):
        self.returns[name] = dict(kind="delay", seconds=beats * self.beat, feedback=feedback, gain_db=gain_db,
                                  taps=taps, hp=hp, lp=lp)

    def add(self, bus, t_seconds, audio):
        """Adds stereo (2, m) or mono audio at a time in seconds (wrapping for loops)."""
        if audio.ndim == 1:
            audio = dsp.to_stereo(audio)
        b = bus if isinstance(bus, Bus) else self.buses[bus]
        start = int(round(t_seconds * SR))
        m = audio.shape[1]
        if self.loop:
            start %= self.length
            pos = 0
            while pos < m:
                s = (start + pos) % self.length
                k = min(m - pos, self.length - s)
                b.buf[:, s:s + k] += audio[:, pos:pos + k]
                pos += k
        else:
            if start < 0:
                audio = audio[:, -start:]
                start = 0
                m = audio.shape[1]
            k = min(m, self.total - start)
            if k > 0:
                b.buf[:, start:start + k] += audio[:, :k]

    def hit(self, bus, beat, audio, pan=0.0, offset=0.0):
        self.add(bus, self.seconds(beat) + offset, dsp.to_stereo(audio, pan) if audio.ndim == 1 else audio)

    # ----------------------------------------------------------------------------------------------- harmony

    def set_chords(self, spec, start_bar=0):
        """spec: list of bars; each bar is a chord symbol or a list of (symbol, beats)."""
        beat = self.bar(start_bar)
        for entry in spec:
            parts = [(entry, self.beats_per_bar)] if isinstance(entry, str) else entry
            for sym, beats in parts:
                self.chords.append((beat, beat + beats, theory.chord(sym)))
                beat += beats
        return beat

    def chord_at(self, beat):
        total = self.bars * self.beats_per_bar
        if self.loop:
            beat %= total
        for s, e, c in self.chords:
            if s <= beat + 1e-6 < e:
                return c
        return None

    def check_melody(self, notes, name, at=0.0):
        shifted = theory.shift(notes, at)
        self.warnings += theory.harmony_warnings(shifted, self.chord_at, f"{self.name}/{name}")

    # ----------------------------------------------------------------------------------------------- mixdown

    def mix(self):
        loop = self.loop
        n = self.total
        master = np.zeros((2, n))
        sends = {name: np.zeros((2, n)) for name in self.returns}
        for b in self.buses.values():
            x = b.buf
            if not np.any(x):
                continue
            if b.eq:
                x = dsp.circular(lambda s: dsp.eq_chain(s, b.eq), x, int(SR * 0.5), loop)
            if b.comp:
                x = dsp.compressor(x, loop=loop, **b.comp)
            if b.drive:
                x = dsp.soft_clip(x, b.drive)
            if b.chorus:
                x = dsp.chorus(x, loop=loop, seed=self.name + b.name, period=self.lfo_period, **b.chorus)
            if b.width != 1.0:
                x = dsp.width(x, b.width)
            if b.pan:
                gl, gr = dsp.pan_gains(b.pan)
                x = np.stack([x[0] * gl * np.sqrt(2), x[1] * gr * np.sqrt(2)])
            gain = b.gain_db
            if b.level is not None:
                lu = dsp.loudness(x)
                gain += REFERENCE_LUFS + b.level - lu
                self.debug.append(f"{b.name}: {lu:.1f} LUFS -> {gain:+.1f} dB")
            x = x * dsp.db_to_gain(gain)
            master += x
            for name, amount in b.sends.items():
                sends[name] += x * amount
            b.buf = None
        for name, r in self.returns.items():
            x = sends[name]
            if not np.any(x):
                continue
            if r["kind"] == "reverb":
                ir_l, ir_r = dsp.make_ir(rt60=r["rt60"], predelay=r["predelay"], seed=self.name + name, **r["ir"])
                y = dsp.convolve_stereo(x, ir_l, ir_r, loop=loop)
            else:
                ir_l, ir_r = dsp.delay_ir(r["seconds"], r["feedback"], r["taps"])
                y = dsp.convolve_stereo(x, ir_l, ir_r, loop=loop, cross=0.0)
            y = dsp.circular(lambda s: dsp.lowpass(dsp.highpass(s, r["hp"]), r["lp"]), y, int(SR * 0.5), loop)
            master += y * dsp.db_to_gain(r["gain_db"])
        self.buses = {}
        if self.master_eq:
            master = dsp.circular(lambda s: dsp.eq_chain(s, self.master_eq), master, int(SR * 0.5), loop)
        import os
        if os.environ.get("ER_AUDIO_DEBUG"):
            print(f"[{self.name}] " + "; ".join(self.debug), flush=True)
        return master


def humanized(rng, sd=0.004):
    return float(rng.normal() * sd)
