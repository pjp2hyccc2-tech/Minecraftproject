"""Dungeon — tense ambience.

A minor, 92 BPM, 4/4, 28 bars (73.0 s loop).
  (1-4)      spiccato low-string ostinato with a chromatic flat second (A A C A Bb A E A), ticking clock
  (5-12)     clarinet motif leaning on the leading tone (Am Am F E Am Bb Dm E7)
  (13-20)    the motif on muted horns, string tremolo swells, low drum hits
  (21-24)    break: only the clock, drips, a low drone and harp harmonics; a riser
  (25-28)    the ostinato returns (Am F Dm E7) into the loop
"""
import numpy as np

from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..dsp import SR, TAU
from ..song import Song

MOTIF = """
E4:h. C4:q | B3:q. A3:e G#3:h | A3:h C4:h | B3:w |
E4:h. D4:q | D4:h. F4:q | E4:q. D4:e C4:q A3:q | G#3:w
"""
INTRO = ["Am", "Am", "F", "E"]
MAIN = ["Am", "Am", "F", "E", "Am", "Bb", "Dm", "E7"]
BREAK = ["Am", "Am", "Am", "Am"]
OUTRO = ["Am", "F", "Dm", "E7"]
RIFF = {"Am": [0, 0, 3, 0, 1, 0, 7, 0], "Dm": [0, 0, 3, 0, 2, 0, 7, 0], "F": [0, 0, 4, 0, 2, 0, 7, 0],
        "Bb": [0, 0, 4, 0, 2, 0, 7, 0], "E": [0, 0, 4, 0, 1, 0, 7, 0], "E7": [0, 0, 4, 0, 1, 0, 10, 0]}


def drip(rng):
    n = int(0.15 * SR)
    t = np.arange(n) / SR
    f0 = rng.uniform(900, 1800)
    f = f0 * (1 + 1.1 * (1 - np.exp(-t / 0.012)))
    return np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / 0.028) * 0.5


def render():
    s = Song("dungeon", bpm=92, bars=28)
    s.set_chords(INTRO + MAIN + MAIN + BREAK + OUTRO)
    s.reverb("crypt", rt60=2.8, predelay=0.03, brightness=5000)
    s.delay("echo", beats=1.5, feedback=0.35)

    riff_b = s.bus("ostinato", level=-1.0, sends={"crypt": 0.25}, eq=[("lowpass", 3500, 0.7, 0)])
    clar_b = s.bus("clarinet", level=-2.0, sends={"crypt": 0.4, "echo": 0.1}, eq=[("highpass", 150, 0.7, 0)])
    horn_b = s.bus("muted_horns", level=-3.0, sends={"crypt": 0.45}, eq=[("lowpass", 2200, 0.7, 0)])
    trem_b = s.bus("tremolo", level=-9.0, sends={"crypt": 0.5}, eq=[("highpass", 200, 0.7, 0)])
    pad_b = s.bus("pad", level=-12.0, sends={"crypt": 0.5})
    bass_b = s.bus("bass", level=-8.0, sends={"crypt": 0.2})
    tick_b = s.bus("clock", level=-14.0, sends={"crypt": 0.15}, eq=[("highpass", 800, 0.7, 0)])
    drip_b = s.bus("drips", level=-16.0, sends={"crypt": 0.6, "echo": 0.3})
    hit_b = s.bus("hits", level=-9.0, sends={"crypt": 0.4})
    harp_b = s.bus("harp", level=-11.0, sends={"crypt": 0.6, "echo": 0.25})
    fx_b = s.bus("risers", level=-13.0, sends={"crypt": 0.4})

    spiccato = I.cellos(attack=0.006, release=0.06, voices=3, bright=0.5, blat=0.3, vib_depth=0.0)
    clarinet = I.clarinet(portamento=0.03)
    horns = I.horns(bright=0.15, vel_bright=0.3, blat=0.1, attack=0.08)
    trem = I.strings(attack=0.6, release=0.8, bright=0.7, voices=4, tremolo=0.45, vib_depth=3)
    pad = I.pad(spectrum=I.spec_strings, attack=1.5, release=2.0, bright=0.1, voices=3)
    bass = I.Pluck("bass")
    harp = I.Pluck("harp")

    motif = theory.parse(MOTIF, name="motif")
    s.check_melody(motif, "motif", s.bar(4))
    I.play_notes(s, clar_b, clarinet, motif, at=s.bar(4), vel=0.7, mono=True)
    I.play_notes(s, horn_b, horns, theory.shift(motif, 0, -12), at=s.bar(12), vel=0.6, mono=True)

    rng = dsp.rng_for("dungeon", "riff")
    prev = None
    for st, en, c in arrange.chord_spans(s, 0, 28):
        if 20 <= st / 4 < 24:
            continue
        root = theory.bass_note(c, 33, 45, prev)
        prev = root
        pat = RIFF[c.symbol]
        for k in range(int(round((en - st) * 2))):
            v = (0.75 if k % 2 == 0 else 0.5) * (1 + 0.06 * rng.normal())
            s.add(riff_b, s.seconds(st + k * 0.5) + rng.normal() * 0.003,
                  spiccato.render(root + pat[k % 8], s.seconds(0.28), v, rng))
    arrange.bass_line(s, bass_b, bass, 4, 20, [(0, "low", 1.5), (2, "low", 1.5)], lo=33, hi=45, vel=0.6)
    arrange.pad(s, pad_b, pad, 0, 20, voices=3, lo=48, hi=64, vel=0.5)
    arrange.pad(s, pad_b, pad, 24, 28, voices=3, lo=48, hi=64, vel=0.5, seed="out")
    for bar in range(12, 20, 2):
        c = s.chord_at(s.bar(bar))
        for p in theory.voice_chord(c, None, 3, 64, 79):
            s.add(trem_b, s.seconds(s.bar(bar)), trem.render(p, s.seconds(7.5), 0.6, rng, swell=0.9))
    # the break: a drone and harp harmonics
    for p in (33, 40):
        s.add(pad_b, s.seconds(s.bar(20)), pad.render(p, s.seconds(16), 0.6, rng))
    for k, (b, p) in enumerate([(20.0, 81), (20.75, 88), (21.5, 84), (22.25, 93), (22.5, 81), (23.25, 88)]):
        s.add(harp_b, s.seconds(s.bar(0, b * 4)), harp.render(p, 0.4, 0.5, rng, pan=0.4 if k % 2 else -0.4))
    # clock: tick-tock on the eighths
    arrange.drum_pattern(s, tick_b, 0, 28, [(k * 0.5, (lambda v, r, f=(2600.0 if k % 2 == 0 else 1900.0):
                                                       drums.clock_tick(v, r, f)), 0.6 if k % 2 == 0 else 0.4,
                                             0.3 if k % 2 == 0 else -0.3) for k in range(8)])
    for bar in range(28):
        for _ in range(rng.integers(0, 3)):
            s.hit(drip_b, s.bar(bar, rng.uniform(0, 4)), drip(rng), rng.uniform(-0.8, 0.8))
    for bar in (12, 14, 16, 18):
        s.hit(hit_b, s.bar(bar), drums.taiko(0.55, rng, "chu"), -0.2)
    for bar in (4, 12, 24):
        s.hit(hit_b, s.bar(bar), drums.boom(0.6, rng), 0.0)
    for bar in (11, 19, 23, 27):
        s.add(fx_b, s.seconds(s.bar(bar, 1)), dsp.to_stereo(drums.reverse_swell(s.seconds(3), rng, 400, 7000), 0.0))
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
