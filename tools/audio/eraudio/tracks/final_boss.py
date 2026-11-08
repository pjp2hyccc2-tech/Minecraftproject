"""終焉の王 — final boss. Epic, with choir and organ.

D minor, 150 BPM, 4/4, 56 bars (89.6 s loop).
  (1-4)      organ toccata ostinato, pedal, timpani, gong (Dm Bb Gm A7)
  A (5-12)   the choir sings the kingdom's leitmotif in minor (D-E-A instead of F-G-C)
             Dm | Gm F A | Bb Gm | Asus4 A | Dm | Gm Em7b5 | Dm/A Asus4 A | Dm
  B (13-20)  the King's chromatic motif on trombones and horns (Dm Eb Dm Eb Bb C#dim7 Dm A7), organ chords
  A' (21-28) tutti: choir with trumpets, violins an octave up, organ, taiko
  C (29-36)  organ solo over timpani and a low chant (Gm Dm/F Em7b5 A7 Gm Dm/F Bb A7)
  D (37-44)  a glimpse of hope: the major theme on violins (F C/E Dm Bb Gm C A7sus4 A7), falling back to A7
  A'' (45-52) tutti with church bells and gong
  (53-56)    organ and drums back to the top
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

A_MEL = """
D4:e. E4:s A4:h. | Bb4:q A4:e G4:e F4:q E4:q | F4:e. G4:s A4:q D5:q C5:e Bb4:e | A4:h. r:q |
D4:e. E4:s A4:h. | Bb4:q A4:e Bb4:e D5:q E5:q | F5:q E5:q D5:q C#5:q | D5:h. r:q
"""
B_MEL = """
D3:q. D3:e F3:q A3:q | Bb3:h G3:h | A3:q. G3:e F3:q E3:q | Eb3:w |
D4:q. C4:e Bb3:q F3:q | E3:h G3:h | F3:q. E3:e D3:q A2:q | C#3:h E3:h
"""
D_MEL = """
F4:e. G4:s C5:h. | D5:q C5:e Bb4:e A4:q G4:q | A4:e. Bb4:s C5:q F5:q E5:e D5:e | D5:h. r:q |
Bb4:q. A4:e G4:q D5:q | E5:q. D5:e C5:q G4:q | D5:h. r:q | C#5:h. r:q
"""
INTRO = ["Dm", "Bb", "Gm", "A7"]
A_CH = ["Dm", [("Gm", 2), ("F", 1), ("A", 1)], [("Bb", 2), ("Gm", 2)], [("Asus4", 2), ("A", 2)],
        "Dm", [("Gm", 2), ("Em7b5", 2)], [("Dm/A", 2), ("Asus4", 1), ("A", 1)], "Dm"]
B_CH = ["Dm", "Eb", "Dm", "Eb", "Bb", "C#dim7", "Dm", "A7"]
C_CH = ["Gm", "Dm/F", "Em7b5", "A7", "Gm", "Dm/F", "Bb", "A7"]
D_CH = ["F", "C/E", "Dm", "Bb", "Gm", "C", "A7sus4", "A7"]


def render():
    s = Song("final_boss", bpm=150, bars=56)
    s.set_chords(INTRO + A_CH + B_CH + A_CH + C_CH + D_CH + A_CH + INTRO)
    s.reverb("cathedral", rt60=3.6, predelay=0.03, brightness=6000, low_rt=4.2)

    choir_b = s.bus("choir_melody", level=0.0, sends={"cathedral": 0.45}, chorus=dict(mix=0.2, rate=0.15))
    pad_b = s.bus("choir_pad", level=-6.0, sends={"cathedral": 0.55}, chorus=dict(mix=0.25, rate=0.12))
    organ_b = s.bus("organ", level=-3.0, sends={"cathedral": 0.5}, eq=[("highpass", 45, 0.7, 0)])
    pedal_b = s.bus("pedal", level=-8.0, sends={"cathedral": 0.4})
    brass_b = s.bus("brass", level=-0.5, sends={"cathedral": 0.35})
    tpt_b = s.bus("trumpets", level=-2.0, sends={"cathedral": 0.35}, eq=[("highpass", 250, 0.7, 0)])
    vln_b = s.bus("violins", level=-1.5, sends={"cathedral": 0.4}, eq=[("highpass", 200, 0.7, 0)],
                  chorus=dict(mix=0.2, rate=0.25))
    trem_b = s.bus("string_tremolo", level=-9.0, sends={"cathedral": 0.4})
    harp_b = s.bus("harp", level=-9.0, sends={"cathedral": 0.45})
    taiko_b = s.bus("taiko", level=-4.0, sends={"cathedral": 0.25}, comp=dict(threshold_db=-16, ratio=3,
                                                                                attack=0.004, release=0.12))
    timp_b = s.bus("timpani", level=-6.0, sends={"cathedral": 0.35})
    metal_b = s.bus("bells_gong", level=-8.0, sends={"cathedral": 0.5})

    sop = I.choir("sop_a", voices=6, attack=0.12, release=0.45, vib_depth=15, portamento=0.04)
    alto = I.choir("alt_a", voices=6, attack=0.12, release=0.45, vib_depth=13, portamento=0.04)
    pad = I.choir("ten_o", attack=0.3, release=0.6)
    chant = I.choir("bas_o", voices=6, attack=0.1, release=0.4)
    organ_fast = I.organ(stops={"8": 1.0, "4": 0.7, "2": 0.4, "mixture": 0.25}, attack=0.008, release=0.06,
                         chiff=0.06, voices=1)
    organ_full = I.organ(stops={"16": 0.6, "8": 1.0, "4": 0.7, "2-2/3": 0.3, "2": 0.4, "mixture": 0.35,
                                "reed": 0.35}, attack=0.05, release=0.3)
    pedal = I.organ(stops={"16": 1.0, "8": 0.6, "reed": 0.3}, attack=0.06, release=0.3)
    trombones = I.trombones(blat=0.6)
    horns = I.horns(blat=0.5)
    trumpets = I.trumpets()
    violins = I.strings(attack=0.06, release=0.3, bright=0.6, portamento=0.04)
    trem = I.strings(attack=0.2, release=0.4, bright=0.6, tremolo=0.5, voices=4)
    harp = I.Pluck("harp")

    a = theory.parse(A_MEL, name="A")
    b = theory.parse(B_MEL, name="B")
    d = theory.parse(D_MEL, name="D")
    for notes, nm, bar in ((a, "A", 4), (b, "B", 12), (a, "A'", 20), (d, "D", 36), (a, "A''", 44)):
        s.check_melody(notes, nm, s.bar(bar))

    # choir melody (sopranos + altos in unison), with doublings in the tutti
    for bar, vel, seed in ((4, 0.8, "a1"), (20, 0.95, "a2"), (44, 1.0, "a3")):
        I.play_notes(s, choir_b, sop, a, at=s.bar(bar), vel=vel, mono=True, seed=seed + "s")
        I.play_notes(s, choir_b, alto, a, at=s.bar(bar), vel=vel * 0.8, mono=True, seed=seed + "a")
    for bar in (20, 44):
        I.play_notes(s, tpt_b, trumpets, a, at=s.bar(bar), vel=0.85, mono=True, seed=f"t{bar}")
        I.play_notes(s, vln_b, violins, theory.shift(a, 0, 12), at=s.bar(bar), vel=0.8, mono=True, seed=f"v{bar}")
    I.play_notes(s, brass_b, trombones, b, at=s.bar(12), vel=0.95, mono=True)
    I.play_notes(s, brass_b, horns, theory.shift(b, 0, 12), at=s.bar(12), vel=0.8, mono=True, seed="h")
    I.play_notes(s, vln_b, violins, d, at=s.bar(36), vel=0.75, mono=True, seed="hope")
    I.play_notes(s, brass_b, horns, theory.shift(d, 0, -12), at=s.bar(40), vel=0.55, mono=True, seed="hope_h")

    # organ: toccata figures, sustained chords, pedal
    fig = [0, 2, 1, 3, 2, 4, 3, 1]
    for a0, a1 in ((0, 12), (20, 36), (44, 56)):
        arrange.arpeggio(s, organ_b, organ_fast, a0, a1, fig, step=0.25, lo=57, hi=84, vel=0.55, accent=0.2,
                         gate=0.9, seed=f"tocc{a0}", pan_by_pitch=0.4)
    arrange.arpeggio(s, organ_b, organ_fast, 28, 36, [0, 3, 5, 3, 1, 4, 6, 4], step=0.25, lo=55, hi=91, vel=0.65,
                     accent=0.25, gate=0.9, seed="solo", pan_by_pitch=0.5)
    arrange.arpeggio(s, organ_b, organ_fast, 28, 36, [0, 2, 1, 2], step=0.5, lo=43, hi=60, vel=0.55, seed="lh")
    arrange.pad(s, organ_b, organ_full, 12, 20, voices=4, lo=50, hi=69, vel=0.6)
    arrange.bass_line(s, pedal_b, pedal, 0, 56, [(0, "root", 4)], lo=35, hi=47, vel=0.7)

    # choir harmony, strings, harp
    arrange.pad(s, pad_b, pad, 4, 28, voices=4, lo=45, hi=64, vel=0.6)
    arrange.pad(s, pad_b, pad, 44, 52, voices=4, lo=45, hi=64, vel=0.7, seed="a3")
    for bar in range(28, 36):
        for p in theory.voice_chord(s.chord_at(s.bar(bar)), None, 3, 38, 52):
            for beat in (0, 2):
                s.add(pad_b, s.seconds(s.bar(bar, beat)), chant.render(p, s.seconds(1.6), 0.75,
                                                                       dsp.rng_for("chant", bar, beat, p)))
    arrange.pad(s, trem_b, trem, 4, 12, voices=4, lo=55, hi=74, vel=0.55)
    arrange.pad(s, trem_b, trem, 20, 36, voices=4, lo=50, hi=72, vel=0.6, seed="t2")
    arrange.pad(s, trem_b, trem, 44, 52, voices=4, lo=55, hi=76, vel=0.7, seed="t3")
    arrange.arpeggio(s, harp_b, harp, 36, 44, [0, 2, 3, 4, 5, 4, 3, 2], step=0.5, lo=41, hi=84, vel=0.55)
    arrange.pad(s, pad_b, I.choir("alt_a", attack=0.5, release=0.8), 36, 44, voices=4, lo=55, hi=72, vel=0.45,
                seed="hope")

    # percussion
    rng = dsp.rng_for("final_boss", "perc")
    od = lambda v, r: drums.taiko(v, r, "odaiko")  # noqa: E731
    chu = lambda v, r: drums.taiko(v, r, "chu")    # noqa: E731
    groove = [(0, od, 1.0, 0.0), (2, od, 0.8, 0.0), (1, chu, 0.6, -0.3), (3, chu, 0.7, 0.3), (3.5, chu, 0.5, 0.3)]
    for a0, a1 in ((0, 36), (44, 56)):
        arrange.drum_pattern(s, taiko_b, a0, a1, groove, seed=f"g{a0}")
    for bar in (3, 19, 27, 35, 43, 55):
        arrange.roll(s, timp_b, lambda v, r: drums.timpani(45, v, r), s.bar(bar, 2), 2.0, rate=8, vel0=0.2,
                     vel1=1.0, seed=f"r{bar}")
    for bar in (0, 4, 12, 20, 28, 44, 52):
        s.hit(timp_b, s.bar(bar), drums.timpani(38, 1.0, rng), 0.0)
        s.hit(metal_b, s.bar(bar), drums.crash(0.9, rng), 0.35)
    for bar in (0, 44):
        s.hit(metal_b, s.bar(bar), drums.gong(1.0, rng), -0.2)
    bell = I.Bell("church", decay_scale=0.9)
    for bar in range(44, 52, 2):
        s.add(metal_b, s.seconds(s.bar(bar)), bell.render(62, 2.0, 0.8, rng, pan=-0.3))
        s.add(metal_b, s.seconds(s.bar(bar, 2)), bell.render(57, 2.0, 0.7, rng, pan=0.3))
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
