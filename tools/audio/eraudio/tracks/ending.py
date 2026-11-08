"""Ending / staff roll — emotional, plays once (about 2:26).

F major, modulating to G major; 72 BPM, 4/4, 42 bars plus the final ring.
  (1-4)      piano alone (F Dm Bb C)
  A1 (5-12)  the main theme on piano, strings enter softly
  A2 (13-20) the theme on violins, the cellos sing the countermelody, harp
  B (21-28)  flute and oboe (Dm Am/C Bb F/A Gm7 F/A Bb Csus4-C7), crescendo
  (29-30)    modulation through the flat sixth and seventh: Eb F | D7sus4 D7
  A3 (31-38) the theme in G major: horns and violins, choir, timpani and cymbals
  Coda (39-42) the opening motif once more on piano over Em7 Cmaj7 Am7-D7sus4, ending on G(add9)
"""
import numpy as np

from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song
from .title import B_MELODY, COUNTER, THEME

A_F = ["F", [("Bb", 2), ("C", 2)], [("Dm", 2), ("Bb", 2)], [("Csus4", 2), ("C", 2)],
       "F", [("Bb", 2), ("Gm7", 2)], [("Dm7", 2), ("Csus4", 1), ("C", 1)]]
A_G = ["G", [("C", 2), ("D", 2)], [("Em", 2), ("C", 2)], [("Dsus4", 2), ("D", 2)],
       "G", [("C", 2), ("Am7", 2)], [("Em7", 2), ("Dsus4", 1), ("D", 1)], "G"]
B_CH = ["Dm", "Am/C", "Bb", "F/A", "Gm7", "F/A", "Bb", [("Csus4", 2), ("C7", 2)]]
CODA = """
G4:e. A4:s D5:h. | E5:q D5:q B4:h | C5:h A4:q G4:q | G4:w
"""
BRIDGE = """
G4:q Bb4:q Eb5:q D5:q | C5:h. A4:q
"""


def render():
    s = Song("ending", bpm=72, bars=42, loop=False, tail=7.0)
    s.set_chords(["F", "Dm", "Bb", "C"] + A_F + ["F"] + A_F + [[("F", 2), ("A7", 2)]] + B_CH
                 + [[("Eb", 2), ("F", 2)], [("D7sus4", 2), ("D7", 2)]] + A_G
                 + ["Em7", "Cmaj7", [("Am7", 2), ("D7sus4", 2)], "Gadd9"])
    s.reverb("hall", rt60=2.8, predelay=0.03)

    piano_b = s.bus("piano", level=-1.0, sends={"hall": 0.4})
    vln_b = s.bus("violins", level=0.0, sends={"hall": 0.4}, eq=[("highpass", 180, 0.7, 0)],
                  chorus=dict(mix=0.25, rate=0.22))
    cello_b = s.bus("cellos", level=-3.0, sends={"hall": 0.35})
    wind_b = s.bus("winds", level=-0.5, sends={"hall": 0.4}, eq=[("highpass", 200, 0.7, 0)])
    horn_b = s.bus("horns", level=0.0, sends={"hall": 0.45})
    pad_b = s.bus("strings_pad", level=-9.0, sends={"hall": 0.45}, chorus=dict(mix=0.35, rate=0.18))
    choir_b = s.bus("choir", level=-8.0, sends={"hall": 0.55}, chorus=dict(mix=0.3, rate=0.15))
    harp_b = s.bus("harp", level=-8.0, sends={"hall": 0.45})
    low_b = s.bus("basses", level=-9.0, sends={"hall": 0.25}, eq=[("lowpass", 2500, 0.7, 0)])
    perc_b = s.bus("perc", level=-9.0, sends={"hall": 0.4})
    bell_b = s.bus("bells", level=-12.0, sends={"hall": 0.5})

    piano = I.Piano(hardness=0.85)
    violins = I.strings(attack=0.1, release=0.45, bright=0.5, portamento=0.05, vib_depth=11)
    cellos = I.cellos(attack=0.12, release=0.5, portamento=0.05, vib_depth=9)
    flute = I.flute(portamento=0.03)
    oboe = I.oboe(portamento=0.03)
    horns = I.horns(attack=0.06)
    pad = I.strings(attack=0.5, release=0.9, bright=0.3, voices=4, blat=0.0)
    choir = I.choir("alt_a", attack=0.6, release=1.0)
    harp = I.Pluck("harp")
    basses = I.cellos(attack=0.2, release=0.6, bright=0.2)
    glock = I.Bell("glock", decay_scale=1.4)

    theme = theory.parse(THEME, name="theme")
    theme_a2 = theory.parse(THEME.replace("F5:h. r:q\n", "F5:h r:h\n"), name="theme2")
    counter = theory.parse(COUNTER, name="counter")
    bmel = theory.parse(B_MELODY, name="b")
    bridge = theory.parse(BRIDGE, name="bridge")
    coda = theory.parse(CODA, name="coda")
    for notes, nm, bar, tr in ((theme, "A1", 4, 0), (theme_a2, "A2", 12, 0), (counter, "counter", 12, -12),
                               (bmel, "B", 20, 0), (bridge, "bridge", 28, 0), (theme, "A3", 30, 2),
                               (coda, "coda", 38, 0)):
        s.check_melody(theory.shift(notes, 0, tr), nm, s.bar(bar))

    # intro and A1: piano
    lh = [0, 2, 3, 4, 5, 4, 3, 2]
    arrange.arpeggio(s, piano_b, piano, 0, 12, lh, step=0.5, lo=41, hi=72, vel=0.45, accent=0.1, gate=1.6)
    I.play_notes(s, piano_b, piano, theme, at=s.bar(4), vel=0.7, seed="rh", shape=0.35)
    arrange.pad(s, pad_b, pad, 6, 30, voices=4, lo=50, hi=70, vel=0.45,
                vel_curve=lambda b: 0.7 + 0.5 * min(1.0, max(0.0, (b - s.bar(6)) / s.bar(20))))
    # A2: violins + cello countermelody, piano and harp
    I.play_notes(s, vln_b, violins, theme_a2, at=s.bar(12), vel=0.75, mono=True)
    I.play_notes(s, cello_b, cellos, theory.shift(counter, 0, -12), at=s.bar(12), vel=0.65, mono=True)
    arrange.arpeggio(s, piano_b, piano, 12, 28, lh, step=0.5, lo=41, hi=72, vel=0.4, gate=1.6, seed="p2")
    arrange.arpeggio(s, harp_b, harp, 12, 20, [0, 3, 4, 5], step=1.0, lo=53, hi=86, vel=0.45)
    arrange.bass_line(s, low_b, basses, 12, 30, [(0, "root", 4)], lo=33, hi=48, vel=0.55)
    # B: winds, crescendo
    I.play_notes(s, wind_b, flute, bmel, at=s.bar(20), vel=0.72, mono=True)
    I.play_notes(s, wind_b, oboe, theory.shift([n for n in bmel if n.start >= 16], 0, -12), at=s.bar(20),
                 vel=0.6, mono=True)
    arrange.arpeggio(s, harp_b, harp, 20, 28, [0, 2, 3, 4, 5, 4, 3, 2], step=0.5, lo=48, hi=84, vel=0.45,
                     seed="hb")
    arrange.pad(s, choir_b, choir, 24, 30, voices=4, lo=53, hi=72, vel=0.45)
    # bridge: modulation to G
    I.play_notes(s, horn_b, horns, bridge, at=s.bar(28), vel=0.8, mono=True)
    arrange.pad(s, pad_b, pad, 28, 30, voices=4, lo=50, hi=70, vel=0.7, seed="br")
    # A3 in G major: tutti
    I.play_notes(s, horn_b, horns, theme, at=s.bar(30), transpose=2, vel=0.9, mono=True, seed="a3h")
    I.play_notes(s, vln_b, violins, theory.shift(theme, 0, 14), at=s.bar(30), vel=0.85, mono=True, seed="a3v")
    I.play_notes(s, cello_b, cellos, theory.shift(counter, 0, -10), at=s.bar(30), vel=0.75, mono=True, seed="a3c")
    arrange.pad(s, choir_b, choir, 30, 38, voices=4, lo=55, hi=74, vel=0.7, seed="a3")
    arrange.pad(s, pad_b, pad, 30, 42, voices=4, lo=50, hi=72, vel=0.65, seed="a3p",
                vel_curve=lambda b: 1.0 if b < s.bar(38) else 0.55)
    arrange.arpeggio(s, harp_b, harp, 30, 38, [0, 2, 3, 4, 5, 6, 5, 4, 3, 4, 5, 6, 7, 6, 5, 3], step=0.25,
                     lo=50, hi=91, vel=0.4, seed="h3")
    arrange.bass_line(s, low_b, basses, 30, 42, [(0, "root", 4)], lo=31, hi=47, vel=0.7, seed="b3")
    I.play_notes(s, bell_b, glock, theory.shift([n for n in theme if n.start < 16], 0, 14), at=s.bar(30), vel=0.5)
    # coda: piano
    I.play_notes(s, piano_b, piano, coda, at=s.bar(38), vel=0.6, seed="coda", shape=0.3, sustain_pedal=True)
    arrange.arpeggio(s, piano_b, piano, 38, 41, lh, step=0.5, lo=40, hi=67, vel=0.35, gate=1.8, seed="pc")
    rng = dsp.rng_for("ending", "fx")
    for k, p in enumerate([43, 50, 55, 59, 62, 67, 69, 74, 79]):
        s.add(harp_b, s.seconds(s.bar(41)) + k * 0.11, harp.render(p, 3.0, 0.5, rng, pan=-0.4 + 0.1 * k))
    s.add(bell_b, s.seconds(s.bar(41, 0.5)), glock.render(86, 1.0, 0.45, rng, pan=0.3))

    # percussion
    s.add(perc_b, s.seconds(s.bar(28)), dsp.to_stereo(drums.suspended_cymbal_swell(s.seconds(8), rng, 0.9), 0.2))
    arrange.roll(s, perc_b, lambda v, r: drums.timpani(50, v, r), s.bar(29), 4.0, rate=6, vel0=0.1, vel1=0.8)
    s.hit(perc_b, s.bar(30), drums.crash(0.85, rng), -0.3)
    for bar, beat, p, v in ((30, 0, 43, 0.95), (32, 0, 43, 0.6), (34, 0, 43, 0.75), (36, 0, 50, 0.7),
                            (37, 2, 50, 0.6), (38, 0, 43, 0.5)):
        s.hit(perc_b, s.bar(bar, beat), drums.timpani(p, v, rng), 0.1)
    audio = s.mix()
    end = int((s.seconds(s.bar(42)) + 6.5) * dsp.SR)
    audio = audio[:, :end]
    audio = dsp.fade(audio, fade_in=0.01, fade_out=3.0)
    return {"audio": audio, "loop": False, "warnings": s.warnings, "lufs": -15.0}
