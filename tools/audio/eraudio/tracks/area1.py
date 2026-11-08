"""アルセリア王国 — field of the starting kingdom. Bright and pastoral.

G major, 6/8 (dotted quarter = 64), 40 bars (75.0 s loop). Beats are dotted quarters: t = eighth, q3 = quarter.
  A (1-8)     ocarina tune over a lute ostinato, pizzicato bass, soft strings
  A' (9-16)   flute with a clarinet countermelody, frame drum
  B (17-24)   violins sing over harp and horns (C D Bm7 Em Am7 D C D7sus4-D7)
  A'' (25-32) flute and violins, glockenspiel sparkle, tambourine
  C (33-36)   playful clarinet bridge (Em C Am7 D7)
  (37-40)     interlude: lute and an ocarina call that leads back to the top
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

TUNE = """
D5:q3 G5:t B5:q3 A5:t | G5:q3 E5:t C5:q | D5:q3 G5:t B5:q3 D6:t | C6:q3 B5:t A5:q |
B5:q3 G5:t E5:q3 G5:t | A5:q3 G5:t E5:q | C5:q3 E5:t F#5:q3 A5:t | G5:h
"""
COUNTER = """
B4:h | C5:q G4:q | B4:q D5:q | F#4:q A4:q | G4:q B4:q | C5:q G4:q | C5:q C5:q | B4:h
"""
B_TUNE = """
E5:q G5:q | F#5:h | F#5:q3 E5:t D5:q | E5:h | C5:q E5:q | D5:q3 E5:t F#5:q | G5:q3 F#5:t E5:q | D5:q C5:q
"""
BRIDGE = """
E4:t G4:t B4:t E5:q3 D5:t | C5:q3 B4:t G4:q | A4:t C5:t E5:t G5:q3 E5:t | F#5:q3 E5:t D5:t C5:t A4:t
"""
CALL = """
r:q D5:t G5:t B5:t | C6:q G5:q | B5:q3 A5:t G5:q | A5:h
"""

A_CHORDS = ["G", "C/G", "G", "D7/F#", "Em", "C", [("Am7", 1), ("D7", 1)], "G"]
B_CHORDS = ["C", "D", "Bm7", "Em", "Am7", "D", "C", [("D7sus4", 1), ("D7", 1)]]
C_CHORDS = ["Em", "C", "Am7", "D7"]
I_CHORDS = ["G", "C/G", "G", "D7"]


def render():
    s = Song("area1", bpm=64, bars=40, beats_per_bar=2)
    s.set_chords(A_CHORDS * 2 + B_CHORDS + A_CHORDS + C_CHORDS + I_CHORDS)
    s.reverb("hall", rt60=1.7, predelay=0.015)

    lead_b = s.bus("lead", level=0.0, sends={"hall": 0.3}, eq=[("highpass", 250, 0.7, 0)])
    counter_b = s.bus("counter", level=-5.0, sends={"hall": 0.3}, eq=[("highpass", 150, 0.7, 0)])
    strings_b = s.bus("violins", level=-0.5, sends={"hall": 0.35}, eq=[("highpass", 160, 0.7, 0)],
                      chorus=dict(mix=0.25, rate=0.25))
    lute_b = s.bus("lute", level=-6.0, sends={"hall": 0.22}, eq=[("highpass", 70, 0.7, 0)])
    pad_b = s.bus("pad", level=-11.0, sends={"hall": 0.4}, eq=[("highpass", 120, 0.7, 0)],
                  chorus=dict(mix=0.35, rate=0.2))
    horn_b = s.bus("horns", level=-10.0, sends={"hall": 0.45})
    harp_b = s.bus("harp", level=-9.0, sends={"hall": 0.35}, eq=[("highpass", 120, 0.7, 0)])
    bass_b = s.bus("bass", level=-7.0, sends={"hall": 0.15})
    glock_b = s.bus("glock", level=-13.0, sends={"hall": 0.4})
    perc_b = s.bus("perc", level=-11.0, sends={"hall": 0.2})

    ocarina = I.ocarina()
    flute = I.flute()
    clarinet = I.clarinet()
    violins = I.strings(attack=0.08, release=0.35, bright=0.5, portamento=0.04)
    lute = I.Pluck("lute")
    pizz = I.Pluck("pizz")
    pad = I.strings(attack=0.4, release=0.8, bright=0.25, voices=4, blat=0.0)
    horns = I.horns(attack=0.25, blat=0.1)
    harp = I.Pluck("harp")
    glock = I.Bell("glock")

    tune = theory.parse(TUNE, 2, name="tune")
    counter = theory.parse(COUNTER, 2, name="counter")
    btune = theory.parse(B_TUNE, 2, name="b")
    bridge = theory.parse(BRIDGE, 2, name="bridge")
    call = theory.parse(CALL, 2, name="call")
    for notes, nm, bar in ((tune, "A", 0), (tune, "A'", 8), (counter, "counter", 8), (btune, "B", 16),
                           (tune, "A''", 24), (bridge, "C", 32), (call, "call", 36)):
        s.check_melody(notes, nm, s.bar(bar))

    # melody
    I.play_notes(s, lead_b, ocarina, tune, at=s.bar(0), vel=0.75, mono=True)
    I.play_notes(s, lead_b, flute, tune, at=s.bar(8), vel=0.72, mono=True, seed="a2")
    I.play_notes(s, counter_b, clarinet, counter, at=s.bar(8), vel=0.6, mono=True)
    I.play_notes(s, strings_b, violins, btune, at=s.bar(16), vel=0.75, mono=True)
    I.play_notes(s, lead_b, flute, tune, at=s.bar(24), vel=0.8, mono=True, seed="a3")
    I.play_notes(s, strings_b, violins, theory.shift(tune, 0, -12), at=s.bar(24), vel=0.62, mono=True, seed="a3v")
    I.play_notes(s, glock_b, glock, theory.shift([n for n in tune if n.start < 8], 0, 12), at=s.bar(24), vel=0.55)
    I.play_notes(s, counter_b, clarinet, counter, at=s.bar(24), vel=0.6, mono=True, seed="a3c")
    I.play_notes(s, counter_b, clarinet, bridge, at=s.bar(32), vel=0.75, mono=True, seed="bridge")
    I.play_notes(s, lead_b, ocarina, call, at=s.bar(36), vel=0.7, mono=True, seed="call")

    # accompaniment
    lute_pat = [0, 2, 3, 4, 3, 2]
    arrange.arpeggio(s, lute_b, lute, 0, 16, lute_pat, step=1 / 3, lo=40, hi=76, vel=0.55, accent=0.2, gate=3.0)
    arrange.arpeggio(s, lute_b, lute, 24, 40, lute_pat, step=1 / 3, lo=40, hi=76, vel=0.55, accent=0.2, gate=3.0,
                     seed="l2")
    arrange.arpeggio(s, harp_b, harp, 16, 24, [0, 1, 2, 3, 4, 5], step=1 / 3, lo=48, hi=86, vel=0.45)
    arrange.pad(s, pad_b, pad, 0, 40, voices=4, lo=52, hi=72, vel=0.45)
    arrange.pad(s, horn_b, horns, 16, 24, voices=3, lo=50, hi=65, vel=0.5)
    arrange.bass_line(s, bass_b, pizz, 0, 40, [(0, "root", 1), (1, "fifth", 1)], lo=31, hi=47, vel=0.8)

    # percussion: frame drum on the bar, tambourine on the second beat (A' onwards), light shaker in A''
    fd = [(0, lambda v, r: drums.frame_drum(v, r, 78.0), 0.55, 0.0),
          (1 + 2 / 3, lambda v, r: drums.frame_drum(v, r, 78.0), 0.25, 0.0)]
    arrange.drum_pattern(s, perc_b, 8, 36, fd)
    arrange.drum_pattern(s, perc_b, 24, 36, [(1, drums.tambourine, 0.45, 0.35)], seed="tamb")
    arrange.drum_pattern(s, perc_b, 24, 32, [(k / 3, drums.shaker, 0.3 + 0.15 * (k % 3 == 0), -0.3)
                                             for k in range(6)], seed="shk")
    rng = dsp.rng_for("area1", "tri")
    for bar in (16, 24):
        s.hit(perc_b, s.bar(bar), drums.triangle(0.35, rng), 0.4)
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
