"""終焉の塔 — the Tower of the End. Dark and ominous.

C minor over a C pedal, 60 BPM, 4/4, 20 bars (80.0 s loop).
  (1-4)     organ pedal, tolling bell, slow heartbeat, thin high string clusters (Cm Db/C Cm Gb/C: the tritone)
  (5-12)    cello lament (Cm Db/C Abmaj7/C G7b9 Cm Db/C Gb/C G7b9), low choir humming
  (13-20)   male choir sings the kingdom's leitmotif turned into a C minor dirge
            (Cm Ab Fm-Db G7b9 Cm Ab-Fm7 Dbmaj7-G7b9 Cm), organ, bowed-metal swells
"""
import numpy as np

from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..dsp import SR, TAU
from ..song import Song

LAMENT = """
C3:q. D3:e G3:h | Ab3:q G3:q F3:q Ab3:q | G3:h. Eb3:q | D3:h B2:h |
C3:q. D3:e G3:h | Ab3:q Bb3:q C4:q Db4:q | Bb3:h. Gb3:q | F3:h B2:h
"""
DIRGE = """
C3:e. D3:s G3:h. | Ab3:q G3:e F3:e Eb3:q C3:q | Eb3:e. F3:s Ab3:q C4:q Bb3:e Ab3:e | G3:h. r:q |
C3:e. D3:s G3:h. | Ab3:q G3:e Ab3:e C4:q Eb4:q | F4:q Eb4:q D4:q Ab3:q | G3:w
"""
CHORDS = (["Cm", "Db/C", "Cm", "Gb/C"]
          + ["Cm", "Db/C", "Abmaj7/C", "G7b9", "Cm", "Db/C", "Gb/C", "G7b9"]
          + ["Cm", "Ab", [("Fm", 2), ("Db", 2)], "G7b9", "Cm", [("Ab", 2), ("Fm7", 2)],
             [("Dbmaj7", 2), ("G7b9", 2)], "Cm"])


def bowed_metal(dur, rng, base=None):
    n = int(dur * SR)
    t = np.arange(n) / SR
    base = base or rng.uniform(180, 320)
    ratios = np.array([1.0, 1.73, 2.41, 3.17, 3.9, 4.63, 5.8, 7.1])
    out = np.zeros(n)
    for r in ratios:
        a = rng.uniform(0.3, 1.0) / r ** 0.5
        trem = 1 + 0.25 * np.sin(TAU * rng.uniform(0.2, 0.9) * t + rng.uniform(0, TAU))
        out += a * np.sin(TAU * base * r * t + rng.uniform(0, TAU)) * trem
    env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 2
    scrape = dsp.butter(rng.normal(size=n), "bandpass", (base * 2, base * 9), 2) * 0.3
    return (out + scrape) * env / len(ratios)


def heartbeat(vel, rng):
    a = drums.boom(vel, rng, length=0.9, top=62.0, bottom=38.0)
    b = drums.boom(vel * 0.6, rng, length=0.9, top=58.0, bottom=36.0)
    out = np.zeros(int(1.3 * SR))
    out[:a.size] += a
    k = int(0.28 * SR)
    out[k:k + b.size] += b[: out.size - k]
    return out


def render():
    s = Song("area7", bpm=60, bars=20)
    s.set_chords(CHORDS)
    s.reverb("tower", rt60=5.0, predelay=0.04, brightness=4500, low_rt=6.0)

    cello_b = s.bus("cello", level=0.0, sends={"tower": 0.45}, eq=[("highpass", 55, 0.7, 0)])
    choir_b = s.bus("choir", level=0.0, sends={"tower": 0.55}, chorus=dict(mix=0.25, rate=0.12))
    hum_b = s.bus("hum", level=-8.0, sends={"tower": 0.6})
    organ_b = s.bus("organ", level=-6.0, sends={"tower": 0.5})
    bell_b = s.bus("bell", level=-7.0, sends={"tower": 0.6})
    heart_b = s.bus("heart", level=-9.0, sends={"tower": 0.25})
    cluster_b = s.bus("cluster", level=-15.0, sends={"tower": 0.6}, eq=[("highpass", 900, 0.7, 0)])
    metal_b = s.bus("metal", level=-15.0, sends={"tower": 0.7})

    cello = I.cellos(voices=2, detune=3, attack=0.25, release=0.8, bright=0.35, vib_depth=9, portamento=0.08)
    choir = I.choir("ten_o", voices=6, attack=0.3, release=0.9, vib_depth=12, portamento=0.06)
    hum = I.choir("bas_u", voices=5, attack=1.2, release=1.8)
    organ = I.organ(stops={"16": 0.9, "8": 0.6, "4": 0.2}, attack=0.4, release=1.5)
    bell = I.Bell("church", decay_scale=1.3)
    cluster = I.strings(attack=1.5, release=2.0, bright=1.0, voices=4, tremolo=0.35, vib_depth=4)

    lament = theory.parse(LAMENT, name="lament")
    dirge = theory.parse(DIRGE, name="dirge")
    s.check_melody(lament, "lament", s.bar(4))
    s.check_melody(dirge, "dirge", s.bar(12))

    I.play_notes(s, cello_b, cello, lament, at=s.bar(4), vel=0.75, mono=True)
    I.play_notes(s, choir_b, choir, dirge, at=s.bar(12), vel=0.8, mono=True)
    I.play_notes(s, cello_b, cello, theory.shift(dirge, 0, -12), at=s.bar(12), vel=0.55, mono=True, seed="dbl")
    arrange.pad(s, hum_b, hum, 4, 20, voices=3, lo=43, hi=60, vel=0.55)

    rng = dsp.rng_for("area7", "fx")
    for bar in range(0, 20, 2):
        for p in (36, 43):
            s.add(organ_b, s.seconds(s.bar(bar)) - 0.2, organ.render(p, s.seconds(8) + 0.6, 0.7, rng))
        s.hit(bell_b, s.bar(bar), bell.render(48, 4.0, 0.75, rng)[0], -0.2)
    for bar in range(20):
        for beat in (0, 2):
            s.hit(heart_b, s.bar(bar, beat), heartbeat(0.7 if beat == 0 else 0.55, rng), 0.0)
    for bar in range(0, 20, 4):
        for p in (84, 85, 91):
            s.add(cluster_b, s.seconds(s.bar(bar)), cluster.render(p, s.seconds(15), 0.5, rng, swell=0.8))
    for bar in (2, 7, 11, 16):
        s.add(metal_b, s.seconds(s.bar(bar, rng.uniform(0, 2))),
              dsp.to_stereo(bowed_metal(7.0, rng), rng.uniform(-0.6, 0.6)))
    for bar in (11, 19):
        s.add(metal_b, s.seconds(s.bar(bar, 1)), dsp.to_stereo(drums.reverse_swell(3.0, rng, 200, 6000), 0.0))
    return {"audio": s.mix(), "loop": True, "warnings": s.warnings}
