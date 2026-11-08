"""アルセリア王都 — title screen and hub town. Warm and heroic.

F major, 96 BPM, 4/4, 32 bars (80.0 s loop).
  A1 (1-8)   main theme on horns over strings, harp arpeggios and pizzicato bass
  A2 (9-16)  theme on violins, horn countermelody, light snare march
  B (17-24)  flute and oboe over a descending bass (Dm Am/C Bb F/A Gm7 F/A Bb Csus4-C7), crescendo
  A3 (25-32) tutti: trumpets and violins (8va), horns' countermelody, choir, glockenspiel, timpani and cymbals;
             the last bar settles so the loop returns to A1 naturally.
The main theme (F-G-C, dotted) is the game's leitmotif; the ending, victory fanfare and jingles quote it.
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

THEME = """
F4:e. G4:s C5:h. | D5:q C5:e Bb4:e A4:q G4:q | A4:e. Bb4:s C5:q F5:q E5:e D5:e | C5:h. r:q |
F4:e. G4:s C5:h. | D5:q C5:e D5:e F5:q G5:q | A5:q G5:q F5:q E5:q | F5:h. r:q
"""
THEME_A2 = THEME.replace("F5:h. r:q\n", "F5:h r:h\n")

COUNTER = """
C4:h A3:h | D4:h E4:h | F4:h D4:h | F4:h E4:h | A3:h C4:h | Bb3:h D4:h | D4:h C4:h | F4:h E4:h
"""
COUNTER_END = COUNTER.replace("F4:h E4:h\n", "F4:h. E4:q\n")

B_MELODY = """
A4:q. D5:e E5:q F5:q | E5:h C5:h | D5:q. Bb4:e C5:q D5:q | C5:h. A4:q |
Bb4:q. G4:e A4:q Bb4:q | C5:q. A4:e F4:q A4:q | Bb4:q D5:q F5:q. G5:e | F5:h E5:h
"""

A_CHORDS = ["F", [("Bb", 2), ("C", 2)], [("Dm", 2), ("Bb", 2)], [("Csus4", 2), ("C", 2)],
            "F", [("Bb", 2), ("Gm7", 2)], [("Dm7", 2), ("Csus4", 1), ("C", 1)]]
B_CHORDS = ["Dm", "Am/C", "Bb", "F/A", "Gm7", "F/A", "Bb", [("Csus4", 2), ("C7", 2)]]


def render():
    s = Song("title", bpm=96, bars=32)
    s.set_chords(A_CHORDS + ["F"] + A_CHORDS + [[("F", 2), ("A7", 2)]] + B_CHORDS + A_CHORDS + [[("F", 3), ("C7", 1)]])

    s.reverb("hall", rt60=2.4, predelay=0.025)
    hall = {"hall": 0.32}
    horns_b = s.bus("horns", level=0.0, sends={"hall": 0.42}, eq=[("highpass", 90, 0.7, 0)])
    hcounter_b = s.bus("horn_counter", level=-5.0, sends={"hall": 0.45}, eq=[("highpass", 90, 0.7, 0)])
    lead_b = s.bus("violins", level=-0.5, sends={"hall": 0.36}, eq=[("highpass", 160, 0.7, 0)],
                   chorus=dict(mix=0.25, base_ms=12, depth_ms=2.5, rate=0.25))
    pad_b = s.bus("strings_pad", level=-9.0, sends={"hall": 0.4}, eq=[("highpass", 110, 0.7, 0)],
                  chorus=dict(mix=0.35, base_ms=15, depth_ms=3.5, rate=0.2))
    low_b = s.bus("low_strings", level=-8.0, sends={"hall": 0.22}, eq=[("lowpass", 3500, 0.7, 0)])
    harp_b = s.bus("harp", level=-8.0, sends={"hall": 0.38}, eq=[("highpass", 120, 0.7, 0)])
    wind_b = s.bus("winds", level=-0.5, sends={"hall": 0.4}, eq=[("highpass", 200, 0.7, 0)])
    pizz_b = s.bus("pizz", level=-7.0, sends={"hall": 0.3})
    brass_b = s.bus("trumpets", level=-1.0, sends={"hall": 0.35}, eq=[("highpass", 180, 0.7, 0)])
    choir_b = s.bus("choir", level=-9.0, sends={"hall": 0.55}, chorus=dict(mix=0.3, rate=0.18))
    glock_b = s.bus("glock", level=-13.0, sends={"hall": 0.45})
    perc_b = s.bus("perc", level=-9.0, sends={"hall": 0.28}, eq=[("highpass", 30, 0.7, 0)])
    timp_b = s.bus("timpani", level=-8.0, sends={"hall": 0.35})

    horns = I.horns()
    violins = I.strings(attack=0.07, release=0.3, bright=0.55, voices=5, portamento=0.035)
    string_pad = I.strings(attack=0.35, release=0.7, bright=0.3, voices=4, blat=0.0)
    cellos = I.cellos(attack=0.15, release=0.5)
    harp = I.Pluck("harp")
    pizz = I.Pluck("pizz")
    flute = I.flute()
    oboe = I.oboe()
    trumpets = I.trumpets()
    choir = I.choir("alt_a", attack=0.45)
    glock = I.Bell("glock")

    theme = theory.parse(THEME, name="theme")
    theme2 = theory.parse(THEME_A2, name="theme2")
    counter = theory.parse(COUNTER, name="counter")
    counter_end = theory.parse(COUNTER_END, name="counter_end")
    bmel = theory.parse(B_MELODY, name="b")

    # ---- A1 (bars 1-8): horns
    I.play_notes(s, horns_b, horns, theme, at=s.bar(0), vel=0.72, legato=0.98, mono=True)
    s.check_melody(theme, "A1", s.bar(0))
    arrange.pad(s, pad_b, string_pad, 0, 8, voices=4, lo=53, hi=74, vel=0.55)
    arrange.arpeggio(s, harp_b, harp, 0, 8, [0, 2, 3, 4, 5, 4, 3, 2], step=0.5, lo=41, hi=84, vel=0.5)
    arrange.bass_line(s, pizz_b, pizz, 0, 8, [(0, "root", 1), (2, "fifth", 1)], lo=29, hi=48, vel=0.8)

    # ---- A2 (bars 9-16): violins + horn countermelody + snare
    I.play_notes(s, lead_b, violins, theme2, at=s.bar(8), vel=0.75, legato=1.0, mono=True)
    s.check_melody(theme2, "A2", s.bar(8))
    I.play_notes(s, hcounter_b, horns, counter, at=s.bar(8), vel=0.55, legato=1.0, mono=True)
    s.check_melody(counter, "A2 counter", s.bar(8))
    arrange.pad(s, pad_b, string_pad, 8, 16, voices=3, lo=50, hi=67, vel=0.5)
    arrange.arpeggio(s, harp_b, harp, 8, 16, [0, 2, 3, 4, 5, 4, 3, 2], step=0.5, lo=41, hi=84, vel=0.45)
    arrange.bass_line(s, pizz_b, pizz, 8, 16, [(0, "root", 1), (1.5, "fifth", 0.5), (2, "root", 1),
                                              (3, "octave", 1)], lo=29, hi=48, vel=0.8)
    arrange.bass_line(s, low_b, cellos, 8, 16, [(0, "root", 4)], lo=36, hi=50, vel=0.55, seed="a2cello")
    march = [(0, lambda v, r: drums.snare(v, r, bright=0.7), 0.45, 0.15),
             (1.5, lambda v, r: drums.snare(v, r, bright=0.7), 0.22, 0.15),
             (2, lambda v, r: drums.snare(v, r, bright=0.7), 0.35, 0.15),
             (3, lambda v, r: drums.snare(v, r, bright=0.7), 0.3, 0.15),
             (3.5, lambda v, r: drums.snare(v, r, bright=0.7), 0.2, 0.15)]
    arrange.drum_pattern(s, perc_b, 8, 16, march)

    # ---- B (bars 17-24): flute, oboe joins; descending bass; crescendo into A3
    I.play_notes(s, wind_b, flute, bmel, at=s.bar(16), vel=0.7, legato=0.95, mono=True)
    s.check_melody(bmel, "B", s.bar(16))
    bmel2 = [n for n in bmel if n.start >= 16]
    I.play_notes(s, wind_b, oboe, theory.shift(bmel2, 0, -12), at=s.bar(16), vel=0.55, legato=0.95, seed="oboe", mono=True)
    arrange.pad(s, pad_b, string_pad, 16, 24, voices=4, lo=50, hi=69, vel=0.45,
                vel_curve=lambda b: 1.0 + 0.5 * max(0.0, (b - s.bar(21)) / 12.0))
    arrange.arpeggio(s, harp_b, harp, 16, 24, [0, 3, 4, 5], step=1.0, lo=50, hi=86, vel=0.5)
    arrange.arpeggio(s, pizz_b, pizz, 16, 24, [None, 2, None, 3, None, 2, None, 4], step=0.5, lo=55, hi=79,
                     vel=0.45, seed="pizz_up")
    arrange.bass_line(s, low_b, cellos, 16, 24, [(0, "root", 4)], lo=33, hi=50, vel=0.55)
    arrange.bass_line(s, pizz_b, pizz, 16, 24, [(0, "root", 1), (2, "root", 1)], lo=29, hi=45, vel=0.7,
                      seed="bpizz")

    # ---- A3 (bars 25-32): tutti
    a3 = theory.parse(THEME, name="a3")
    I.play_notes(s, brass_b, trumpets, a3, at=s.bar(24), vel=0.85, legato=0.97, mono=True)
    I.play_notes(s, lead_b, violins, theory.shift(a3, 0, 12), at=s.bar(24), vel=0.8, legato=1.0, seed="8va", mono=True)
    I.play_notes(s, hcounter_b, horns, counter_end, at=s.bar(24), vel=0.75, legato=1.0, seed="a3c", mono=True)
    I.play_notes(s, glock_b, glock, theory.shift([n for n in a3 if n.start < 16], 0, 12), at=s.bar(24), vel=0.6)
    arrange.pad(s, choir_b, choir, 24, 32, voices=4, lo=53, hi=72, vel=0.65)
    arrange.pad(s, pad_b, string_pad, 24, 32, voices=4, lo=48, hi=67, vel=0.6)
    arrange.pad(s, low_b, cellos, 24, 32, voices=2, lo=33, hi=52, vel=0.7)
    arrange.arpeggio(s, harp_b, harp, 24, 31, [0, 2, 3, 4, 5, 6, 5, 4, 3, 4, 5, 6, 7, 6, 5, 3], step=0.25,
                     lo=48, hi=88, vel=0.4)
    arrange.bass_line(s, pizz_b, pizz, 24, 32, [(0, "root", 1), (1, "octave", 1), (2, "root", 1), (3, "fifth", 1)],
                      lo=29, hi=48, vel=0.85, seed="a3")
    arrange.drum_pattern(s, perc_b, 24, 31, march + [(0, lambda v, r: drums.taiko(v, r, "odaiko"), 0.5, 0.0),
                                                      (2, lambda v, r: drums.taiko(v, r, "odaiko"), 0.35, 0.0)],
                         seed="a3")

    # ---- percussion: timpani and cymbals
    F2, C3, A2 = 41, 48, 45
    rng = dsp.rng_for("title", "timp")
    for bar, beat, pitch, v in [(0, 0, F2, 0.35), (4, 0, F2, 0.3), (3, 2, C3, 0.3), (8, 0, F2, 0.5), (12, 0, F2, 0.45),
                                (11, 2, C3, 0.45), (24, 0, F2, 0.95), (25, 2, C3, 0.6), (27, 0, C3, 0.8),
                                (28, 0, F2, 0.85), (30, 2, C3, 0.7), (31, 0, F2, 0.9)]:
        s.hit(timp_b, s.bar(bar, beat), drums.timpani(pitch, v, rng), 0.1)
    arrange.roll(s, timp_b, lambda v, r: drums.timpani(A2, v, r), s.bar(15, 2), 2.0, rate=6, vel0=0.15, vel1=0.6)
    arrange.roll(s, timp_b, lambda v, r: drums.timpani(C3, v, r), s.bar(22), 8.0, rate=6, vel0=0.1, vel1=0.85,
                 seed="roll2")
    s.hit(perc_b, s.bar(24), drums.crash(0.9, rng), -0.3)
    s.add(perc_b, s.seconds(s.bar(22)), dsp.to_stereo(drums.suspended_cymbal_swell(s.seconds(8), rng, 0.9), 0.3))
    s.hit(perc_b, s.bar(31), drums.crash(0.75, rng), 0.3)
    s.hit(perc_b, s.bar(31), drums.taiko(0.6, rng, "odaiko"), 0.0)
    # bar 32: settle, harp pickup into the loop start
    for k, p in enumerate([60, 64, 67, 70]):
        s.hit(harp_b, s.bar(31, 3 + k * 0.25), harp.render(p, 0.3, 0.45, rng, pan=0.2)[0] * 1.4, 0.2)

    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
