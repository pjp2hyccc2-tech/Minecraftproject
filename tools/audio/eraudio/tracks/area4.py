"""ノルディア雪原 — snowfield. Cold and crystalline.

B minor, 72 BPM, 4/4, 24 bars (80.0 s loop). Open voicings (add9, sus2, maj7#11) and a high, thin texture.
  A (1-8)    music-box motif over celesta arpeggios, piano pedal tones, string pad
             (Bm(add9) Gmaj7 Dsus2 A Em9 Gmaj7#11 F#sus4 F#m)
  B (9-16)   solo violin sings low and warm; choir "oo", FM ice sparkles
  A' (17-24) the motif on high strings with glockenspiel, choir, piano; F#m leads back to the top
A cold wind bed (periodic noise with gusts) runs throughout.
"""
import numpy as np

from .. import arrange, dsp, theory
from .. import instruments as I
from ..dsp import SR
from ..song import Song

MOTIF = """
F#5:q B5:q C#6:q D6:q | B5:h. F#5:q | E5:q A5:q D6:q E6:q | C#6:w |
G5:q B5:q F#6:q E6:q | D6:h. C#6:q | B5:h C#6:h | A5:w
"""
VIOLIN = """
D5:h. C#5:q | B4:h F#5:h | E5:h. A4:q | C#5:w |
B4:q. G4:e B4:q E5:q | F#5:h. E5:q | B4:h. C#5:q | C#5:w
"""
CHORDS = ["Bmadd9", "Gmaj7", "Dsus2", "A", "Em9", "Gmaj7#11", "F#sus4", "F#m"]


def wind(s, rng):
    n = s.length
    out = np.zeros((2, n))
    f = np.fft.rfftfreq(n, 1 / SR)
    for ch in range(2):
        spec = np.fft.rfft(rng.normal(size=n))
        # two whistling resonances drifting slowly are approximated by fixed bands; gusts give the motion
        shape = (np.exp(-0.5 * ((np.log2(np.maximum(f, 1)) - np.log2(900)) / 0.8) ** 2)
                 + 0.5 * np.exp(-0.5 * ((np.log2(np.maximum(f, 1)) - np.log2(2600)) / 0.5) ** 2))
        x = np.fft.irfft(spec * shape, n)
        gust = np.clip(0.5 + 0.5 * dsp.periodic_random(n, 9 + ch, rng), 0.05, 1.0) ** 1.5
        out[ch] = x * gust
    return out / np.max(np.abs(out))


def render():
    s = Song("area4", bpm=72, bars=24)
    s.set_chords(CHORDS * 3)
    s.reverb("ice", rt60=3.8, predelay=0.04, brightness=9000, high_rt=3.0)

    box_b = s.bus("musicbox", level=0.0, sends={"ice": 0.5})
    vln_b = s.bus("violin", level=0.0, sends={"ice": 0.45}, eq=[("highpass", 180, 0.7, 0)])
    hi_b = s.bus("high_strings", level=-1.0, sends={"ice": 0.5}, eq=[("highpass", 300, 0.7, 0)],
                 chorus=dict(mix=0.3, rate=0.2))
    cel_b = s.bus("celesta", level=-7.0, sends={"ice": 0.55}, eq=[("highpass", 300, 0.7, 0)])
    piano_b = s.bus("piano", level=-8.0, sends={"ice": 0.5})
    pad_b = s.bus("pad", level=-11.0, sends={"ice": 0.5}, chorus=dict(mix=0.4, rate=0.12))
    choir_b = s.bus("choir", level=-10.0, sends={"ice": 0.6}, chorus=dict(mix=0.3, rate=0.12))
    ice_b = s.bus("ice_fx", level=-14.0, sends={"ice": 0.7}, eq=[("highpass", 1200, 0.7, 0)])
    glock_b = s.bus("glock", level=-11.0, sends={"ice": 0.55})
    wind_b = s.bus("wind", level=-19.0, eq=[("highpass", 250, 0.7, 0)])

    musicbox = I.Bell("musicbox", decay_scale=1.4)
    violin = I.strings(voices=1, detune=0, attack=0.12, release=0.4, bright=0.45, vib_depth=14, vib_rate=5.4,
                       portamento=0.06, noise=0.006, spread=0.0)
    high = I.strings(attack=0.25, release=0.6, bright=0.4, voices=5, portamento=0.04)
    celesta = I.Bell("celesta")
    piano = I.Piano(hardness=0.8)
    pad = I.pad(spectrum=I.spec_strings, attack=1.2, release=1.8, bright=0.15, voices=4, detune=7)
    choir = I.choir("sop_u", attack=0.8, release=1.2)
    fm = I.FMBell(ratio=3.51, index=2.5, decay=1.4)
    glock = I.Bell("glock")

    motif = theory.parse(MOTIF, name="motif")
    vln = theory.parse(VIOLIN, name="violin")
    s.check_melody(motif, "A", 0)
    s.check_melody(vln, "B", s.bar(8))

    I.play_notes(s, box_b, musicbox, motif, at=0, vel=0.7)
    I.play_notes(s, vln_b, violin, vln, at=s.bar(8), vel=0.75, mono=True)
    I.play_notes(s, hi_b, high, theory.shift(motif, 0, -12), at=s.bar(16), vel=0.7, mono=True)
    I.play_notes(s, glock_b, glock, motif, at=s.bar(16), vel=0.45, seed="g")

    arrange.arpeggio(s, cel_b, celesta, 0, 24, [0, 2, 4, 5, 6, 5, 4, 2], step=0.5, lo=62, hi=96, vel=0.42)
    arrange.bass_line(s, piano_b, piano, 0, 24, [(0, "low", 4), (0, "fifth", 4)], lo=35, hi=52, vel=0.5)
    arrange.pad(s, pad_b, pad, 0, 24, voices=4, lo=54, hi=76, vel=0.5)
    arrange.pad(s, choir_b, choir, 8, 24, voices=3, lo=62, hi=81, vel=0.5)

    rng = dsp.rng_for("area4", "sparkle")
    for bar in range(24):
        c = s.chord_at(s.bar(bar))
        tones = [p for p in range(84, 104) if p % 12 in c.pcs]
        for _ in range(rng.integers(1, 4)):
            b = s.bar(bar) + rng.uniform(0, 4)
            s.add(ice_b, s.seconds(b), fm.render(int(rng.choice(tones)), 0.2, rng.uniform(0.3, 0.7), rng,
                                                 pan=rng.uniform(-0.8, 0.8)))
    s.add(wind_b, 0.0, wind(s, rng))
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
