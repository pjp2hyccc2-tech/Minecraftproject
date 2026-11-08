"""Boss battle — fast and intense.

E minor, 168 BPM, 4/4, 48 bars (68.6 s loop).
  (1-4)      drums, 16th-note string ostinato, synth bass (Em Em C B7)
  A (5-12)   horns: Em C Am B7 Em C D B7
  B (13-20)  violins soar over brass stabs: C D Bm Em C D Esus4-E B7
  A' (21-28) horns with trumpets an octave up, choir shouts
  C (29-36)  half-time: trombones and choir (Am Em Am B7 Am Em F#m7b5 B7), tom fills
  D (37-44)  climax: trumpets and violins (C D Em Em C D B7 B7), snare roll
  (45-48)    drums and ostinato back to the top
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

A_MEL = """
E4:q B4:q E5:h | D5:q. C5:e B4:q G4:q | A4:q C5:q E5:q A5:q | F#5:h. D#5:q |
E5:q. F#5:e G5:q B5:q | A5:h G5:q E5:q | F#5:q. E5:e D5:q A4:q | B4:w
"""
B_MEL = """
G5:h E5:h | F#5:h A5:h | B5:q A5:q F#5:q D5:q | E5:w |
G5:h C6:h | A5:h F#5:h | A5:h G#5:h | F#5:h D#5:h
"""
C_MEL = """
A3:h. C4:q | B3:h G3:h | E4:q. D4:e C4:q A3:q | B3:w |
A3:h. C4:q | E4:h G4:h | A4:h C5:h | B4:w
"""
D_MEL = """
G5:h E5:h | F#5:h A5:h | B5:q A5:q G5:q E5:q | E5:w |
G5:h C6:h | A5:h F#5:h | B5:h A5:h | F#5:h D#5:h
"""
INTRO = ["Em", "Em", "C", "B7"]
A_CH = ["Em", "C", "Am", "B7", "Em", "C", "D", "B7"]
B_CH = ["C", "D", "Bm", "Em", "C", "D", [("Esus4", 2), ("E", 2)], "B7"]
C_CH = ["Am", "Em", "Am", "B7", "Am", "Em", "F#m7b5", "B7"]
D_CH = ["C", "D", "Em", "Em", "C", "D", "B7", "B7"]
OUT = ["Em", "C", "Am", "B7"]


def render():
    s = Song("boss", bpm=168, bars=48)
    s.set_chords(INTRO + A_CH + B_CH + A_CH + C_CH + D_CH + OUT)
    s.reverb("arena", rt60=1.8, predelay=0.015, brightness=7000)

    horn_b = s.bus("horns", level=0.0, sends={"arena": 0.3}, eq=[("highpass", 100, 0.7, 0)])
    tpt_b = s.bus("trumpets", level=-1.0, sends={"arena": 0.3}, eq=[("highpass", 250, 0.7, 0)])
    vln_b = s.bus("violins", level=-0.5, sends={"arena": 0.3}, eq=[("highpass", 200, 0.7, 0)],
                  chorus=dict(mix=0.2, rate=0.3))
    tbn_b = s.bus("trombones", level=-0.5, sends={"arena": 0.3})
    ost_b = s.bus("ostinato", level=-7.0, sends={"arena": 0.15}, eq=[("highpass", 150, 0.7, 0)])
    bass_b = s.bus("synth_bass", level=-6.0, eq=[("lowpass", 1800, 0.7, 0)], comp=dict(threshold_db=-18, ratio=3))
    stab_b = s.bus("stabs", level=-6.0, sends={"arena": 0.3})
    choir_b = s.bus("choir", level=-7.0, sends={"arena": 0.4})
    kit_b = s.bus("kit", level=-3.0, sends={"arena": 0.12}, comp=dict(threshold_db=-14, ratio=3, attack=0.003,
                                                                       release=0.1))
    hat_b = s.bus("hats", level=-14.0, sends={"arena": 0.1})
    big_b = s.bus("taiko", level=-6.0, sends={"arena": 0.25})
    cym_b = s.bus("cymbals", level=-10.0, sends={"arena": 0.3})
    timp_b = s.bus("timpani", level=-8.0, sends={"arena": 0.3})

    horns = I.horns(blat=0.5, vel_bright=0.9)
    trumpets = I.trumpets()
    violins = I.strings(attack=0.05, release=0.25, bright=0.6, portamento=0.03)
    trombones = I.trombones()
    spiccato = I.strings(attack=0.004, release=0.04, voices=3, bright=0.6, blat=0.4, vib_depth=0.0)
    sbass = I.synth_bass()
    stabs = I.trombones(attack=0.015, release=0.12, blat=0.7)
    choir = I.choir("ten_a", attack=0.2, release=0.4)
    shout = I.choir("bas_a", attack=0.015, release=0.15, voices=6, vib_depth=5.0)

    a = theory.parse(A_MEL, name="A")
    b = theory.parse(B_MEL, name="B")
    c = theory.parse(C_MEL, name="C")
    d = theory.parse(D_MEL, name="D")
    for notes, nm, bar in ((a, "A", 4), (b, "B", 12), (a, "A'", 20), (c, "C", 28), (d, "D", 36)):
        s.check_melody(notes, nm, s.bar(bar))

    I.play_notes(s, horn_b, horns, a, at=s.bar(4), vel=0.85, mono=True)
    I.play_notes(s, vln_b, violins, b, at=s.bar(12), vel=0.8, mono=True)
    I.play_notes(s, horn_b, horns, a, at=s.bar(20), vel=0.95, mono=True, seed="a2")
    I.play_notes(s, tpt_b, trumpets, theory.shift(a, 0, 12), at=s.bar(20), vel=0.75, mono=True)
    I.play_notes(s, tbn_b, trombones, c, at=s.bar(28), vel=0.9, mono=True)
    I.play_notes(s, choir_b, choir, c, at=s.bar(28), vel=0.7, mono=True, seed="cc")
    I.play_notes(s, tpt_b, trumpets, d, at=s.bar(36), vel=0.9, mono=True, seed="d")
    I.play_notes(s, vln_b, violins, d, at=s.bar(36), vel=0.8, mono=True, seed="dv")
    I.play_notes(s, horn_b, horns, theory.shift(d, 0, -12), at=s.bar(36), vel=0.75, mono=True, seed="dh")

    for a0, a1, step in ((0, 28, 0.25), (28, 36, 0.5), (36, 48, 0.25)):
        arrange.arpeggio(s, ost_b, spiccato, a0, a1, [0, 2, 1, 2], step=step, lo=52, hi=76, vel=0.6, accent=0.3,
                         gate=0.8, seed=f"o{a0}", pan_by_pitch=0.6)
    eighths = [(k * 0.5, w, 0.5) for k, w in enumerate(["root", "root", "octave", "root", "root", "root",
                                                          "octave", "fifth"])]
    arrange.bass_line(s, bass_b, sbass, 0, 48, eighths, lo=28, hi=43, vel=0.8)
    arrange.pad(s, choir_b, choir, 36, 44, voices=4, lo=50, hi=67, vel=0.6, seed="dpad")
    rng = dsp.rng_for("boss", "fx")
    for bar in range(12, 20):
        v = theory.voice_chord(s.chord_at(s.bar(bar)), None, 4, 50, 65)
        for beat in (0, 1.5, 2.5):
            for p in v:
                s.add(stab_b, s.seconds(s.bar(bar, beat)), stabs.render(p, s.seconds(0.4), 0.85, rng))
    for bar in list(range(20, 28, 2)) + list(range(36, 44, 2)):
        for p in theory.voice_chord(s.chord_at(s.bar(bar)), None, 3, 45, 57):
            s.add(choir_b, s.seconds(s.bar(bar)), shout.render(p, 0.25, 1.0, rng))

    # drums
    kick, snare = drums.kick, drums.snare
    hat = lambda v, r: drums.hat(v, r)              # noqa: E731
    ohat = lambda v, r: drums.hat(v, r, True)       # noqa: E731
    main = [(0, kick, 1.0, 0.0), (1.5, kick, 0.7, 0.0), (2, kick, 0.9, 0.0), (1, snare, 0.9, 0.05),
            (3, snare, 0.95, 0.05)]
    half = [(0, kick, 1.0, 0.0), (2, snare, 1.0, 0.05), (2.5, kick, 0.6, 0.0)]
    hats = [(k * 0.5, hat, 0.7 if k % 2 == 0 else 0.45, 0.35) for k in range(7)] + [(3.5, ohat, 0.6, 0.35)]
    arrange.drum_pattern(s, kit_b, 0, 28, main)
    arrange.drum_pattern(s, kit_b, 28, 36, half, seed="half")
    arrange.drum_pattern(s, kit_b, 36, 48, main, seed="d")
    arrange.drum_pattern(s, hat_b, 0, 48, hats)
    od = lambda v, r: drums.taiko(v, r, "odaiko")   # noqa: E731
    chu = lambda v, r: drums.taiko(v, r, "chu")     # noqa: E731
    for bar in range(0, 48, 2):
        s.hit(big_b, s.bar(bar), od(0.9, rng), 0.0)
    for bar in (11, 19, 27, 35, 47):
        for k, beat in enumerate((2, 2.5, 3, 3.25, 3.5, 3.75)):
            s.hit(big_b, s.bar(bar, beat), chu(0.6 + 0.07 * k, rng), -0.4 + 0.16 * k)
    for bar in range(28, 36, 2):
        s.hit(big_b, s.bar(bar + 1, 2), chu(0.7, rng), 0.3)
    arrange.roll(s, kit_b, lambda v, r: drums.snare(v, r), s.bar(43), 4.0, rate=4, vel0=0.3, vel1=1.0, seed="sr")
    for bar in (4, 12, 20, 28, 36, 44):
        s.hit(cym_b, s.bar(bar), drums.crash(1.0, rng), -0.3 if bar % 8 else 0.3)
        s.hit(timp_b, s.bar(bar), drums.timpani(40, 0.95, rng), 0.0)
    for bar in (3, 27, 35):
        arrange.roll(s, timp_b, lambda v, r: drums.timpani(47, v, r), s.bar(bar, 2), 2.0, rate=6, vel0=0.2,
                     vel1=0.9, seed=f"t{bar}")
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
