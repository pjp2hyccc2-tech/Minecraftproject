"""Proves the loop construction: a looping render must equal one period of the steady state of the same music
played over and over (rendered linearly, four periods back to back; the third is compared). Covers note tails that run past the loop end,
reverb, delay, chorus, EQ and compression."""
import numpy as np

from . import dsp, instruments as I
from .song import Song


def _build(song, periods):
    s = song
    s.reverb("hall", rt60=2.0, predelay=0.02)
    s.delay("echo", beats=0.75, feedback=0.4)
    a = s.bus("a", sends={"hall": 0.5, "echo": 0.3}, chorus=dict(mix=0.4, rate=0.25),
              eq=[("highpass", 120, 0.7, 0), ("peak", 1000, 1.0, 3.0)])
    b = s.bus("b", sends={"hall": 0.4}, comp=dict(threshold_db=-24, ratio=3.0))
    strings = I.strings()
    harp = I.Pluck("harp")
    period = s.seconds(8)
    for k in range(periods):
        base = k * period
        for i, (t, p, d) in enumerate([(0.0, 60, 1.0), (1.5, 64, 1.5), (3.2, 67, 1.6), (3.6, 72, 0.9)]):
            rng = dsp.rng_for("selftest", "s", i)
            s.add(a, base + t, strings.render(p, d, 0.8, rng))
            rng = dsp.rng_for("selftest", "h", i)
            s.add(b, base + t + 0.1, harp.render(p - 12, 0.5, 0.8, rng))
    return s.mix()


def run():
    loop = _build(Song("selftest", bpm=120, bars=2, loop=True), 1)
    # harp notes ring 6 s, longer than the 4 s period: the third of four periods has every earlier tail in it
    linear = Song("selftest", bpm=120, bars=8, loop=False, tail=0.0)
    linear.lfo_period = 4.0      # same musical period as the loop
    lin = _build(linear, 4)
    n = loop.shape[-1]
    mid = lin[:, 2 * n:3 * n]
    err = np.max(np.abs(mid - loop)) / (np.max(np.abs(loop)) + 1e-12)
    return float(err)


if __name__ == "__main__":
    print(run())
