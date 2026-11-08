"""サンドリア砂漠 — desert. Exotic scale.

E phrygian dominant (hijaz: E F G# A B C D), 100 BPM, 4/4, 32 bars (76.8 s loop).
  (1-4)     oud riff, darbuka (maqsum: D T - T D - T -), tanpura-like E/B drone
  A (5-12)  ney melody with the F-G# augmented second; harmony E F E Dm E F Dm-E E
  B (13-20) violins in A minor (Am Am G F Dm E F E), shaker, horns
  A' (21-28) ney with koto doubling, horns, zills
  C (29-32) percussion break with taiko, back to the riff
"""
from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song

NEY = """
E4:q F4:e G#4:e A4:e G#4:e F4:e G#4:e | A4:q. G#4:e F4:q E4:q | G#4:q B4:q C5:e B4:e A4:e G#4:e | A4:h F4:q D4:q |
E4:q F4:e G#4:e A4:e G#4:e B4:q | C5:q. D5:e C5:q A4:q | B4:e C5:e B4:e A4:e G#4:h | E4:w
"""
VIOLINS = """
E5:q. D5:e C5:q B4:q | A4:h. r:q | D5:q. C5:e B4:q A4:q | C5:h. r:q |
F5:q. E5:e D5:q C5:q | B4:h G#4:q E4:q | A4:q. B4:e C5:q D5:q | E5:w
"""
RIFFS = {  # eighth notes per chord root (first half used for two-beat chords)
    "E": "E3 F3 G#3 F3 E3 G#3 A3 G#3",
    "F": "F3 A3 C4 A3 G#3 A3 F3 E3",
    "D": "D3 F3 A3 F3 E3 F3 D3 C3",
    "A": "A2 C3 E3 C3 B2 C3 A2 G#2",
    "G": "G2 B2 D3 B2 A2 B2 G2 A2",
}
A_CHORDS = ["E", "F", "E", "Dm", "E", "F", [("Dm", 2), ("E", 2)], "E"]
B_CHORDS = ["Am", "Am", "G", "F", "Dm", "E", "F", "E"]
INTRO = ["E", "E", "F", "E"]
BREAK = ["E", "F", "Dm", "E"]


def render():
    s = Song("area3", bpm=100, bars=32)
    s.set_chords(INTRO + A_CHORDS + B_CHORDS + A_CHORDS + BREAK)
    s.reverb("hall", rt60=2.0, predelay=0.03, brightness=7000)
    s.delay("echo", beats=0.75, feedback=0.3)

    ney_b = s.bus("ney", level=0.0, sends={"hall": 0.35, "echo": 0.12}, eq=[("highpass", 200, 0.7, 0)])
    vln_b = s.bus("violins", level=-0.5, sends={"hall": 0.35}, eq=[("highpass", 180, 0.7, 0)],
                  chorus=dict(mix=0.25, rate=0.25))
    oud_b = s.bus("oud", level=-5.0, sends={"hall": 0.2}, eq=[("highpass", 90, 0.7, 0)])
    koto_b = s.bus("koto", level=-8.0, sends={"hall": 0.3, "echo": 0.15})
    drone_b = s.bus("drone", level=-12.0, sends={"hall": 0.3}, chorus=dict(mix=0.3, rate=0.1))
    horn_b = s.bus("horns", level=-9.0, sends={"hall": 0.45})
    low_b = s.bus("low", level=-8.0, sends={"hall": 0.2}, eq=[("lowpass", 3000, 0.7, 0)])
    perc_b = s.bus("darbuka", level=-6.0, sends={"hall": 0.18})
    metal_b = s.bus("metal", level=-12.0, sends={"hall": 0.35})
    big_b = s.bus("taiko", level=-9.0, sends={"hall": 0.3})

    ney = I.ney(portamento=0.06)
    violins = I.strings(attack=0.08, release=0.3, bright=0.55, portamento=0.05, vib_depth=12)
    oud = I.Pluck("oud")
    koto = I.Pluck("koto")
    drone = I.pad(spectrum=I.spec_saw, attack=1.5, release=1.5, bright=0.22, voices=3, detune=4)
    horns = I.horns(attack=0.2, blat=0.1)
    cellos = I.cellos(attack=0.1, release=0.4)

    ney_n = theory.parse(NEY, name="ney")
    vln_n = theory.parse(VIOLINS, name="violins")
    s.check_melody(ney_n, "A", s.bar(4))
    s.check_melody(vln_n, "B", s.bar(12))

    I.play_notes(s, ney_b, ney, ney_n, at=s.bar(4), vel=0.75, mono=True)
    I.play_notes(s, vln_b, violins, vln_n, at=s.bar(12), vel=0.75, mono=True)
    I.play_notes(s, ney_b, ney, ney_n, at=s.bar(20), vel=0.85, mono=True, seed="a2")
    I.play_notes(s, koto_b, koto, theory.shift(ney_n, 0, 12), at=s.bar(20), vel=0.5)

    # oud riff following the chords
    rng = dsp.rng_for("area3", "oud")
    for st, en, c in arrange.chord_spans(s, 0, 32):
        root = theory.PC_NAMES[c.root][0]
        riff = RIFFS[root].split()
        for k in range(int(round((en - st) * 2))):
            p = theory.midi(riff[k % len(riff)])
            v = 0.7 if k % 2 == 0 else 0.5
            s.add(oud_b, s.seconds(st + k * 0.5) + rng.normal() * 0.004,
                  oud.render(p, s.seconds(0.45), v * (1 + 0.06 * rng.normal()), rng, pan=-0.2))

    # tanpura-like drone on E and B, outside the B section
    for a, b in ((0, 12), (20, 32)):
        for p in (40, 47, 52):
            s.add(drone_b, s.seconds(s.bar(a)), drone.render(p, s.seconds(s.bar(b - a)) - 0.5, 0.5, rng))
    arrange.pad(s, horn_b, horns, 12, 28, voices=3, lo=50, hi=65, vel=0.5)
    arrange.bass_line(s, low_b, cellos, 12, 20, [(0, "root", 2), (2, "fifth", 2)], lo=33, hi=50, vel=0.6)

    # percussion
    D = lambda v, r: drums.darbuka("doum", v, r)  # noqa: E731
    T = lambda v, r: drums.darbuka("tek", v, r)   # noqa: E731
    K = lambda v, r: drums.darbuka("ka", v, r)    # noqa: E731
    maqsum = [(0, D, 0.9, -0.1), (0.5, T, 0.7, 0.15), (1.5, T, 0.65, 0.15), (2, D, 0.8, -0.1), (3, T, 0.75, 0.15),
              (2.5, K, 0.35, 0.2), (3.5, K, 0.4, 0.2), (1, K, 0.25, 0.2)]
    arrange.drum_pattern(s, perc_b, 0, 32, maqsum)
    arrange.drum_pattern(s, perc_b, 0, 32, [(0, lambda v, r: drums.frame_drum(v, r, 70.0), 0.6, 0.0),
                                            (2, lambda v, r: drums.frame_drum(v, r, 70.0), 0.35, 0.0)], seed="fd")
    arrange.drum_pattern(s, metal_b, 12, 20, [(k * 0.5, drums.shaker, 0.5 if k % 2 == 0 else 0.3, 0.3)
                                              for k in range(8)], seed="shk")
    for bar in list(range(4, 12, 2)) + list(range(20, 32, 2)):
        s.hit(metal_b, s.bar(bar), drums.zill(0.6, rng), -0.35)
    for bar in (11, 19, 27):
        s.hit(metal_b, s.bar(bar, 3.5), drums.zill(0.45, rng), 0.35)
    for bar, beat, v in [(20, 0, 0.9), (28, 0, 0.9), (29, 0, 0.6), (30, 0, 0.7), (31, 0, 0.8), (31, 2, 0.6),
                         (31, 3, 0.7), (31, 3.5, 0.8), (12, 0, 0.6)]:
        s.hit(big_b, s.bar(bar, beat), drums.taiko(v, rng, "odaiko"), 0.0)
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
