"""エルダーフォレスト — mystic forest.

D dorian, 3/4 at 84 BPM, 36 bars (77.1 s loop).
  A (1-8)     breathy flute over harp arpeggios and a soft string pad (Dm9 G/D Fmaj7 Em7 ...); the raised sixth
              (B natural) gives the dorian colour
  B (9-16)    celesta "lights" in F lydian colour (Fmaj7#11 G6 Em7 Am7 ... Asus4-A), choir "oo"
  A' (17-24)  flute with strings and choir, celesta counter-figure
  C (25-32)   deep forest: the motif low on horns over a D pedal, borrowing B flat (Bbmaj7, Gm6, A7)
  (33-36)     harp and chimes settle, A major leads back to the top
A leaf/wind bed and wind chimes run throughout (built as an exactly periodic texture).
"""
import numpy as np

from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..dsp import SR
from ..song import Song

A_MEL = """
A4:q E5:h | D5:q. C5:e B4:q | A4:h. | r:q G4:q B4:q |
A4:q E5:h | F5:q. E5:e D5:q | E5:h G5:q | E5:h D5:q
"""
B_MEL = """
C6:e A5:e B5:e C6:e E6:q | D6:e B5:e G5:e B5:e E6:q | D6:q. B5:e G5:q | C6:h E6:q |
C6:e A5:e B5:e C6:e E6:q | D6:e B5:e G5:e B5:e D6:q | A5:q F5:q D5:q | D5:h C#5:q
"""
C_MEL = """
A3:q E4:h | D4:q. C4:e A3:q | Bb3:h. | r:q G3:q A3:q |
A3:q E4:h | F4:q. E4:e D4:q | D4:h Bb3:q | C#4:h.
"""
A_CHORDS = ["Dm9", "G/D", "Fmaj7", "Em7", "Dm9", "G7/D", "Cmaj7", "Am7"]
B_CHORDS = ["Fmaj7#11", "G6", "Em7", "Am7", "Fmaj7#11", "G6", "Bm7b5", [("Asus4", 2), ("A", 1)]]
C_CHORDS = ["Dm", "Bbmaj7/D", "Gm6/D", "A7sus4", "Dm", "Bbmaj7", "Gm7", "A7"]
CODA = ["Dm9", "G/D", "Dm9", [("Asus4", 2), ("A", 1)]]


def leaf_bed(s, rng):
    """Rustling leaves and air: noise shaped and modulated with whole cycles per loop (seamless)."""
    n = s.length
    out = np.zeros((2, n))
    for ch in range(2):
        nz = rng.normal(size=n)
        spec = np.fft.rfft(nz)
        f = np.fft.rfftfreq(n, 1 / SR)
        shape = 1 / np.sqrt(1 + (f / 4000) ** 2) * (f > 250) * np.sqrt(np.minimum(1, f / 600))
        nz = np.fft.irfft(spec * shape, n)
        gust = 0.55 + 0.45 * dsp.periodic_random(n, 7, rng)
        flutter = 1 + 0.5 * dsp.periodic_random(n, 260, rng, harmonics=3)
        out[ch] = nz * np.clip(gust, 0.1, 1.2) * np.clip(flutter, 0.2, 2)
    return out / np.max(np.abs(out))


def render():
    s = Song("area2", bpm=84, bars=36, beats_per_bar=3)
    s.set_chords(A_CHORDS + B_CHORDS + A_CHORDS + C_CHORDS + CODA)
    s.reverb("forest", rt60=3.2, predelay=0.035, brightness=5000)

    flute_b = s.bus("flute", level=0.0, sends={"forest": 0.45}, eq=[("highpass", 250, 0.7, 0)])
    celesta_b = s.bus("celesta", level=-2.0, sends={"forest": 0.5})
    horn_b = s.bus("horns", level=-1.0, sends={"forest": 0.5}, eq=[("highpass", 80, 0.7, 0)])
    harp_b = s.bus("harp", level=-5.0, sends={"forest": 0.45}, eq=[("highpass", 90, 0.7, 0)])
    pad_b = s.bus("pad", level=-10.0, sends={"forest": 0.5}, chorus=dict(mix=0.4, rate=0.15))
    choir_b = s.bus("choir", level=-9.0, sends={"forest": 0.6}, chorus=dict(mix=0.35, rate=0.13))
    low_b = s.bus("low", level=-9.0, sends={"forest": 0.3}, eq=[("lowpass", 2500, 0.7, 0)])
    chime_b = s.bus("chimes", level=-15.0, sends={"forest": 0.6}, eq=[("highpass", 1500, 0.7, 0)])
    bed_b = s.bus("bed", level=-21.0, eq=[("highpass", 300, 0.7, 0)])
    perc_b = s.bus("perc", level=-14.0, sends={"forest": 0.35})

    flute = I.flute(noise=0.05, vib_depth=13.0, portamento=0.03)
    celesta = I.Bell("celesta", decay_scale=1.2)
    horns = I.horns(attack=0.12, blat=0.15)
    harp = I.Pluck("harp")
    pad = I.strings(attack=0.6, release=1.0, bright=0.25, voices=4, blat=0.0)
    choir = I.choir("sop_u", attack=0.6, release=0.9)
    cellos = I.cellos(attack=0.4, release=0.8, bright=0.25)

    a_mel = theory.parse(A_MEL, 3, name="A")
    b_mel = theory.parse(B_MEL, 3, name="B")
    c_mel = theory.parse(C_MEL, 3, name="C")
    for notes, nm, bar in ((a_mel, "A", 0), (b_mel, "B", 8), (a_mel, "A'", 16), (c_mel, "C", 24)):
        s.check_melody(notes, nm, s.bar(bar))

    I.play_notes(s, flute_b, flute, a_mel, at=s.bar(0), vel=0.7, mono=True)
    I.play_notes(s, celesta_b, celesta, b_mel, at=s.bar(8), vel=0.7)
    I.play_notes(s, flute_b, flute, a_mel, at=s.bar(16), vel=0.78, mono=True, seed="a2")
    I.play_notes(s, celesta_b, celesta, theory.shift(a_mel, 0, 12), at=s.bar(16), vel=0.35, seed="dbl")
    I.play_notes(s, horn_b, horns, c_mel, at=s.bar(24), vel=0.6, mono=True)

    arrange.arpeggio(s, harp_b, harp, 0, 36, [0, 2, 3, 4, 5, 3], step=0.5, lo=38, hi=86, vel=0.5)
    arrange.pad(s, pad_b, pad, 0, 36, voices=4, lo=50, hi=72, vel=0.5)
    arrange.pad(s, choir_b, choir, 8, 24, voices=4, lo=57, hi=76, vel=0.55)
    arrange.pad(s, choir_b, choir, 24, 32, voices=3, lo=50, hi=67, vel=0.5, seed="low")
    arrange.bass_line(s, low_b, cellos, 0, 36, [(0, "root", 3)], lo=33, hi=50, vel=0.55)

    rng = dsp.rng_for("area2", "fx")
    chime = I.Bell("glock", decay_scale=1.6)
    scale = [74, 76, 77, 79, 81, 83, 84, 86, 88, 89, 91]
    for bar in range(0, 36, 2):
        t0 = s.bar(bar) + rng.uniform(0, 2)
        for k in range(rng.integers(3, 7)):
            p = int(rng.choice(scale))
            s.add(chime_b, s.seconds(t0) + k * rng.uniform(0.06, 0.16),
                  chime.render(p, 0.3, rng.uniform(0.25, 0.6), rng, pan=rng.uniform(-0.7, 0.7)))
    s.add(bed_b, 0.0, leaf_bed(s, rng))
    heart = [(0, lambda v, r: drums.frame_drum(v, r, 64.0), 0.5, 0.0),
             (0.5, lambda v, r: drums.frame_drum(v, r, 64.0), 0.25, 0.0)]
    arrange.drum_pattern(s, perc_b, 16, 32, heart)
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
