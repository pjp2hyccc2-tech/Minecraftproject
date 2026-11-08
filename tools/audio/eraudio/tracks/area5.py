"""ドラゴニア火山 — volcano. Heavy drums.

D minor with the phrygian flat second (E flat), 132 BPM, 4/4, 40 bars (72.7 s loop).
  (1-4)      taiko ensemble and the low-string riff (Dm Dm Eb Dm)
  A (5-12)   horns: Dm Dm Bb C Dm Eb C A7
  B (13-20)  trumpets over choir and brass stabs: Bb C Dm Dm Bb C Eb A7
  A' (21-28) tutti: horns, trumpets an octave up, choir, anvils
  C (29-36)  trombones' development over Dm/Eb alternation, gong, anvils on the backbeat
  (37-40)    drum break with the riff, A7 back to the top
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

A_MEL = """
D4:q. E4:e F4:q A4:q | D5:h. C5:q | Bb4:q. A4:e G4:q F4:q | G4:h. r:q |
D4:q. E4:e F4:q A4:q | G4:q. F4:e Eb4:q G4:q | E4:q G4:q C5:q E5:q | C#5:h. A4:q
"""
B_MEL = """
F5:h D5:h | E5:h. G5:q | F5:q. E5:e D5:q A4:q | D5:w |
F5:h Bb5:h | G5:h. E5:q | G5:q F5:q Eb5:q Bb4:q | A4:h C#5:h
"""
C_MEL = """
D3:q. Eb3:e D3:q A2:q | Bb2:q. C3:e Eb3:q G3:q | F3:q. E3:e D3:q A2:q | G3:h Bb3:h |
F3:q. G3:e Bb3:q D4:q | E4:h. C4:q | D4:q A3:q F3:q D3:q | C#3:h E3:h
"""
INTRO = ["Dm", "Dm", "Eb", "Dm"]
A_CH = ["Dm", "Dm", "Bb", "C", "Dm", "Eb", "C", "A7"]
B_CH = ["Bb", "C", "Dm", "Dm", "Bb", "C", "Eb", "A7"]
C_CH = ["Dm", "Eb", "Dm", "Eb", "Bb", "C", "Dm", "A7"]
BREAK = ["Dm", "Dm", "Eb", "A7"]
RIFF = {"m": [0, 0, 12, 0, 3, 0, 7, 5], "": [0, 0, 12, 0, 4, 0, 7, 5], "7": [0, 0, 12, 0, 4, 0, 7, 10]}


def render():
    s = Song("area5", bpm=132, bars=40)
    s.set_chords(INTRO + A_CH + B_CH + A_CH + C_CH + BREAK)
    s.reverb("cavern", rt60=2.3, predelay=0.02, brightness=5500)

    horn_b = s.bus("horns", level=0.0, sends={"cavern": 0.35}, eq=[("highpass", 90, 0.7, 0)])
    tpt_b = s.bus("trumpets", level=-0.5, sends={"cavern": 0.35}, eq=[("highpass", 200, 0.7, 0)])
    tbn_b = s.bus("trombones", level=-1.0, sends={"cavern": 0.3}, eq=[("highpass", 60, 0.7, 0)])
    riff_b = s.bus("riff", level=-5.0, sends={"cavern": 0.15}, eq=[("lowpass", 4000, 0.7, 0)],
                   comp=dict(threshold_db=-20, ratio=2.5))
    choir_b = s.bus("choir", level=-8.0, sends={"cavern": 0.45})
    stab_b = s.bus("stabs", level=-6.0, sends={"cavern": 0.3})
    taiko_b = s.bus("taiko", level=-3.0, sends={"cavern": 0.22}, comp=dict(threshold_db=-16, ratio=3,
                                                                              attack=0.004, release=0.12))
    shime_b = s.bus("shime", level=-12.0, sends={"cavern": 0.15})
    metal_b = s.bus("metal", level=-11.0, sends={"cavern": 0.35})
    timp_b = s.bus("timpani", level=-7.0, sends={"cavern": 0.3})

    horns = I.horns(blat=0.45)
    trumpets = I.trumpets()
    trombones = I.trombones()
    riff_inst = I.cellos(attack=0.008, release=0.07, voices=3, bright=0.55, blat=0.4, vib_depth=0.0)
    choir_m = I.choir("ten_a", attack=0.25, release=0.5, voices=6)
    shout = I.choir("bas_a", attack=0.02, release=0.18, voices=6, vib_depth=6.0)
    stabs = I.trombones(attack=0.02, release=0.15, blat=0.6)

    a_mel = theory.parse(A_MEL, name="A")
    b_mel = theory.parse(B_MEL, name="B")
    c_mel = theory.parse(C_MEL, name="C")
    for notes, nm, bar in ((a_mel, "A", 4), (b_mel, "B", 12), (a_mel, "A'", 20), (c_mel, "C", 28)):
        s.check_melody(notes, nm, s.bar(bar))

    I.play_notes(s, horn_b, horns, a_mel, at=s.bar(4), vel=0.8, mono=True)
    I.play_notes(s, tpt_b, trumpets, b_mel, at=s.bar(12), vel=0.85, mono=True)
    I.play_notes(s, horn_b, horns, a_mel, at=s.bar(20), vel=0.95, mono=True, seed="a2")
    I.play_notes(s, tpt_b, trumpets, theory.shift(a_mel, 0, 12), at=s.bar(20), vel=0.8, mono=True, seed="a2t")
    I.play_notes(s, tbn_b, trombones, c_mel, at=s.bar(28), vel=0.9, mono=True)
    I.play_notes(s, horn_b, horns, theory.shift(c_mel, 0, 12), at=s.bar(28), vel=0.6, mono=True, seed="c8")

    # low-string riff, following the chords
    rng = dsp.rng_for("area5", "riff")
    prev = None
    for st, en, c in arrange.chord_spans(s, 0, 40):
        root = theory.bass_note(c, 33, 45, prev)
        prev = root
        kind = "7" if 10 in c.tones else ("m" if 3 in c.tones else "")
        for k in range(int(round((en - st) * 2))):
            p = root + RIFF[kind][k % 8]
            v = (0.95 if k % 2 == 0 else 0.7) * (1 + 0.05 * rng.normal())
            s.add(riff_b, s.seconds(st + k * 0.5) + rng.normal() * 0.003,
                  riff_inst.render(p, s.seconds(0.3), v, rng))
    arrange.pad(s, choir_b, choir_m, 12, 36, voices=4, lo=45, hi=64, vel=0.65)
    # stabs and shouts
    for bar in range(12, 20):
        c = s.chord_at(s.bar(bar))
        v = theory.voice_chord(c, None, 4, 48, 64)
        for beat in (0, 2.5):
            for p in v:
                s.add(stab_b, s.seconds(s.bar(bar, beat)), stabs.render(p, s.seconds(0.4), 0.85, rng))
    for bar in (12, 14, 16, 18, 20, 24, 28, 32):
        c = s.chord_at(s.bar(bar))
        for p in theory.voice_chord(c, None, 3, 45, 57):
            s.add(choir_b, s.seconds(s.bar(bar)), shout.render(p, 0.3, 1.0, rng))

    # taiko ensemble
    od = lambda v, r: drums.taiko(v, r, "odaiko")  # noqa: E731
    chu = lambda v, r: drums.taiko(v, r, "chu")    # noqa: E731
    shime = lambda v, r: drums.taiko(v, r, "shime")  # noqa: E731
    groove = [(0, od, 1.0, 0.0), (1.5, od, 0.55, 0.0), (2, od, 0.8, 0.0), (3.5, od, 0.5, 0.0),
              (1, chu, 0.7, -0.3), (3, chu, 0.85, 0.3)]
    fill = [(3, chu, 0.6, -0.3), (3.25, chu, 0.5, 0.3), (3.5, chu, 0.75, -0.3), (3.75, chu, 0.9, 0.3)]
    for bar in range(40):
        arrange.drum_pattern(s, taiko_b, bar, bar + 1, groove + (fill if bar % 4 == 3 else []), seed=f"g{bar}")
    arrange.drum_pattern(s, shime_b, 4, 36, [(k * 0.5, shime, 0.55 if k % 2 == 0 else 0.35, 0.4) for k in range(8)])
    arrange.drum_pattern(s, metal_b, 28, 36, [(1, drums.anvil, 0.7, -0.4), (3, drums.anvil, 0.8, 0.4)], seed="anv")
    arrange.drum_pattern(s, metal_b, 20, 28, [(3, drums.anvil, 0.6, 0.4)], seed="anv2")
    for bar in (4, 12, 20, 36):
        s.hit(metal_b, s.bar(bar), drums.crash(0.9, rng), 0.3)
    s.hit(metal_b, s.bar(28), drums.gong(0.9, rng), 0.0)
    for bar, p, v in ((4, 38, 0.9), (12, 45, 0.8), (20, 38, 1.0), (28, 38, 0.9), (36, 45, 0.8)):
        s.hit(timp_b, s.bar(bar), drums.timpani(p, v, rng), -0.1)
    for bar in (11, 19, 27, 35, 39):
        arrange.roll(s, timp_b, lambda v, r: drums.timpani(45, v, r), s.bar(bar, 2), 2.0, rate=8, vel0=0.2,
                     vel1=0.9, seed=f"r{bar}")
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
