"""セレスティア天空大陸 — the sky continent. Airy, with choir.

E major with lydian colour (F#/E: the raised fourth A#), 76 BPM, 4/4, 24 bars (75.8 s loop).
  A (1-8)    sopranos sing the melody, flute an octave above (Emaj9 F#/E C#m9 Amaj7 Emaj9 F#/E Amaj7 Bsus4-B)
  B (9-16)   strings and flute (C#m7 G#m7 Amaj7 Emaj7/G# F#m7 G#m7 Amaj7 Bsus4-B), choir pads
  A' (17-24) full choir and strings, harp in sixteenths, glockenspiel
No drums: soft timpani rolls and cymbal swells at the section joins, harp glissandi, chimes.
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

A_MEL = """
G#4:q. F#4:e E4:q B4:q | A#4:h. C#5:q | B4:q. G#4:e E4:q D#5:q | C#5:w |
E5:q. D#5:e B4:q F#5:q | F#5:h. E5:q | C#5:q E5:q G#5:q E5:q | E5:h D#5:h
"""
B_MEL = """
E5:q. D#5:e C#5:q B4:q | D#5:h B4:h | C#5:q. B4:e A4:q E5:q | D#5:w |
A4:q. B4:e C#5:q E5:q | F#5:h D#5:h | E5:q F#5:q G#5:q A5:q | B5:h F#5:h
"""
A_CH = ["Emaj9", "F#/E", "C#m9", "Amaj7", "Emaj9", "F#/E", "Amaj7", [("Bsus4", 2), ("B", 2)]]
B_CH = ["C#m7", "G#m7", "Amaj7", "Emaj7/G#", "F#m7", "G#m7", "Amaj7", [("Bsus4", 2), ("B", 2)]]


def render():
    s = Song("area6", bpm=76, bars=24)
    s.set_chords(A_CH + B_CH + A_CH)
    s.reverb("sky", rt60=4.2, predelay=0.05, brightness=9000, high_rt=3.4)

    sop_b = s.bus("sopranos", level=0.0, sends={"sky": 0.55}, chorus=dict(mix=0.25, rate=0.15))
    flute_b = s.bus("flute", level=-3.0, sends={"sky": 0.5}, eq=[("highpass", 300, 0.7, 0)])
    str_b = s.bus("strings", level=0.0, sends={"sky": 0.5}, eq=[("highpass", 150, 0.7, 0)],
                  chorus=dict(mix=0.3, rate=0.2))
    choir_b = s.bus("choir_pad", level=-8.0, sends={"sky": 0.65}, chorus=dict(mix=0.35, rate=0.12))
    harp_b = s.bus("harp", level=-6.0, sends={"sky": 0.5}, eq=[("highpass", 120, 0.7, 0)])
    pad_b = s.bus("air_pad", level=-12.0, sends={"sky": 0.5}, chorus=dict(mix=0.45, rate=0.1))
    low_b = s.bus("low", level=-10.0, sends={"sky": 0.35}, eq=[("lowpass", 2000, 0.7, 0)])
    bell_b = s.bus("bells", level=-12.0, sends={"sky": 0.6})
    perc_b = s.bus("perc", level=-13.0, sends={"sky": 0.5})

    sopranos = I.choir("sop_a", voices=5, attack=0.18, release=0.5, vib_depth=14, portamento=0.05)
    flute = I.flute(noise=0.045, portamento=0.03)
    strings = I.strings(attack=0.2, release=0.6, bright=0.5, portamento=0.05)
    choir = I.choir("alt_a", attack=0.7, release=1.2)
    harp = I.Pluck("harp")
    air = I.pad(spectrum=I.spec_flute, attack=1.5, release=2.0, bright=0.3, voices=4, detune=9, noise=0.06,
                noise_band=(3000, 11000))
    cellos = I.cellos(attack=0.5, release=1.0, bright=0.2)
    glock = I.Bell("glock", decay_scale=1.3)

    a_mel = theory.parse(A_MEL, name="A")
    b_mel = theory.parse(B_MEL, name="B")
    for notes, nm, bar in ((a_mel, "A", 0), (b_mel, "B", 8), (a_mel, "A'", 16)):
        s.check_melody(notes, nm, s.bar(bar))

    I.play_notes(s, sop_b, sopranos, a_mel, at=0, vel=0.75, mono=True)
    I.play_notes(s, flute_b, flute, theory.shift(a_mel, 0, 12), at=0, vel=0.55, mono=True)
    I.play_notes(s, str_b, strings, b_mel, at=s.bar(8), vel=0.72, mono=True)
    I.play_notes(s, flute_b, flute, b_mel, at=s.bar(8), vel=0.5, mono=True, seed="b")
    I.play_notes(s, sop_b, sopranos, a_mel, at=s.bar(16), vel=0.85, mono=True, seed="a2")
    I.play_notes(s, str_b, strings, theory.shift(a_mel, 0, 12), at=s.bar(16), vel=0.6, mono=True, seed="a2s")
    I.play_notes(s, bell_b, glock, theory.shift(a_mel, 0, 12), at=s.bar(16), vel=0.4, seed="gl")

    arrange.arpeggio(s, harp_b, harp, 0, 16, [0, 2, 3, 4, 5, 6, 5, 3], step=0.5, lo=52, hi=88, vel=0.42)
    arrange.arpeggio(s, harp_b, harp, 16, 24, [0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1], step=0.25,
                     lo=52, hi=92, vel=0.36, seed="h16")
    arrange.pad(s, choir_b, choir, 8, 24, voices=4, lo=55, hi=74, vel=0.55)
    arrange.pad(s, pad_b, air, 0, 24, voices=4, lo=59, hi=80, vel=0.5)
    arrange.bass_line(s, low_b, cellos, 0, 24, [(0, "root", 4)], lo=36, hi=52, vel=0.5)

    rng = dsp.rng_for("area6", "fx")
    for bar in (7, 15, 23):
        arrange.roll(s, perc_b, lambda v, r: drums.timpani(40, v, r), s.bar(bar, 2), 2.0, rate=6, vel0=0.1,
                     vel1=0.45, seed=f"t{bar}")
        s.add(perc_b, s.seconds(s.bar(bar, 1)),
              dsp.to_stereo(drums.suspended_cymbal_swell(s.seconds(3), rng, 0.6), 0.3))
    # harp glissandi into each section
    for bar in (0, 8, 16):
        c = s.chord_at(s.bar(bar))
        tones = [p for p in range(60, 96) if p % 12 in c.pcs]
        for k, p in enumerate(tones):
            s.add(harp_b, s.seconds(s.bar(bar)) - 0.9 + k * 0.05, harp.render(p, 0.5, 0.35, rng, pan=0.3))
    chime = I.Bell("glock", decay_scale=1.8)
    for bar in range(0, 24, 3):
        t0 = s.bar(bar) + rng.uniform(0, 3)
        for k in range(rng.integers(3, 6)):
            c = s.chord_at(t0)
            tones = [p for p in range(84, 100) if p % 12 in c.pcs]
            s.add(bell_b, s.seconds(t0) + k * rng.uniform(0.08, 0.2),
                  chime.render(int(rng.choice(tones)), 0.3, rng.uniform(0.2, 0.45), rng, pan=rng.uniform(-0.8, 0.8)))
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
