"""Jingles: short, non-looping, in the kingdom's key (F major) so they sit well after any area theme.

victory        brass fanfare with triplet call, Bb-C-F cadence, timpani roll and cymbal (about 8 s)
level_up       rising harp/celesta arpeggio into a bright brass chord with sparkle (about 3 s)
quest_complete the leitmotif's opening (F-G-C) on horns over a warm plagal cadence (about 4.5 s)
discovery      a mysterious swell that opens onto a wide major chord (Dm(add9) -> Bb/D -> F), about 6 s
"""
import numpy as np

from .. import arrange, drums, dsp, theory
from .. import instruments as I
from ..song import Song


def _finish(s, seconds, fade_out=0.6):
    audio = s.mix()[:, : int(seconds * dsp.SR)]
    return dsp.fade(audio, fade_in=0.005, fade_out=fade_out)


def victory():
    s = Song("victory", bpm=120, bars=3, loop=False, tail=3.5)
    s.set_chords(["F", [("Bb", 2), ("C", 2)], "F"])
    s.reverb("hall", rt60=2.2, predelay=0.02)
    tpt_b = s.bus("trumpets", level=0.0, sends={"hall": 0.35})
    horn_b = s.bus("horns", level=-4.0, sends={"hall": 0.4})
    str_b = s.bus("strings", level=-7.0, sends={"hall": 0.4})
    low_b = s.bus("low", level=-8.0, sends={"hall": 0.3})
    perc_b = s.bus("perc", level=-7.0, sends={"hall": 0.35})
    fanfare = theory.parse("C5:t C5:t C5:t F5:q. G5:e A5:q | Bb5:q D6:q C6:q. Bb5:e | A5:w", name="victory")
    s.check_melody(fanfare, "fanfare")
    trumpets = I.trumpets(blat=0.5)
    I.play_notes(s, tpt_b, trumpets, fanfare, vel=0.95, mono=True, humanize=0.002)
    I.play_notes(s, tpt_b, I.trumpets(), theory.shift(fanfare, 0, -5), vel=0.6, mono=True, seed="2nd")
    horns = I.horns(attack=0.04)
    trem = I.strings(attack=0.08, release=0.5, bright=0.6, tremolo=0.45)
    arrange.pad(s, horn_b, horns, 0, 3, voices=4, lo=53, hi=69, vel=0.8, release=1.2)
    arrange.pad(s, str_b, trem, 0, 3, voices=4, lo=60, hi=81, vel=0.7, release=1.5)
    arrange.bass_line(s, low_b, I.trombones(), 0, 3, [(0, "root", 4)], lo=34, hi=46, vel=0.85)
    rng = dsp.rng_for("victory")
    for k in range(3):
        s.hit(perc_b, k / 3, drums.snare(0.55 + 0.15 * k, rng), 0.1)
    for beat in (1.0, 4.0, 6.0):
        s.hit(perc_b, beat, drums.timpani(41 if beat != 6.0 else 48, 0.9, rng), 0.0)
    arrange.roll(s, perc_b, lambda v, r: drums.timpani(41, v, r), 7.0, 1.0, rate=8, vel0=0.3, vel1=0.9)
    s.hit(perc_b, 8.0, drums.crash(1.0, rng), -0.3)
    s.hit(perc_b, 8.0, drums.timpani(41, 1.0, rng), 0.0)
    return {"audio": _finish(s, 8.6, 1.2), "loop": False, "warnings": s.warnings}


def level_up():
    s = Song("level_up", bpm=120, bars=2, loop=False, tail=1.0)
    s.set_chords(["F", "F"])
    s.reverb("hall", rt60=1.9, predelay=0.015)
    up_b = s.bus("arpeggio", level=-2.0, sends={"hall": 0.4})
    brass_b = s.bus("brass", level=0.0, sends={"hall": 0.35})
    spark_b = s.bus("sparkle", level=-8.0, sends={"hall": 0.6}, eq=[("highpass", 2000, 0.7, 0)])
    perc_b = s.bus("perc", level=-8.0, sends={"hall": 0.3})
    rng = dsp.rng_for("level_up")
    harp, celesta = I.Pluck("harp"), I.Bell("celesta")
    for k, p in enumerate([60, 65, 69, 72, 77, 81]):
        t = k * 0.075
        s.add(up_b, t, harp.render(p, 0.6, 0.7, rng, pan=-0.5 + 0.2 * k))
        s.add(up_b, t, celesta.render(p + 12, 0.4, 0.55, rng, pan=-0.5 + 0.2 * k))
    hit = 0.45
    trumpets, horns = I.trumpets(blat=0.55), I.horns()
    for p in (65, 69, 72, 77):
        s.add(brass_b, hit, trumpets.render(p, 0.9, 0.9, rng, release=0.6))
    for p in (53, 60, 65):
        s.add(brass_b, hit, horns.render(p, 1.0, 0.8, rng, release=0.7))
    fm = I.FMBell(ratio=3.51, index=2.0, decay=1.0)
    for k in range(10):
        p = int(rng.choice([84, 89, 93, 96, 101]))
        s.add(spark_b, hit + 0.05 + k * 0.09, fm.render(p, 0.2, rng.uniform(0.3, 0.7), rng, pan=rng.uniform(-0.8, 0.8)))
    s.add(perc_b, hit, dsp.to_stereo(drums.timpani(41, 0.9, rng), 0.0))
    s.add(perc_b, hit, dsp.to_stereo(drums.crash(0.5, rng), 0.3))
    s.add(perc_b, hit, dsp.to_stereo(drums.triangle(0.6, rng), -0.3))
    return {"audio": _finish(s, 3.0, 0.8), "loop": False, "warnings": s.warnings}


def quest_complete():
    s = Song("quest_complete", bpm=100, bars=2, loop=False, tail=2.0)
    s.set_chords([[("F", 2), ("Bb/F", 1), ("F", 1)], "F"])
    s.reverb("hall", rt60=2.3, predelay=0.02)
    horn_b = s.bus("horns", level=0.0, sends={"hall": 0.4})
    str_b = s.bus("strings", level=-5.0, sends={"hall": 0.45}, chorus=dict(mix=0.3, rate=0.2))
    harp_b = s.bus("harp", level=-7.0, sends={"hall": 0.45})
    bell_b = s.bus("bells", level=-10.0, sends={"hall": 0.55})
    motif = theory.parse("F4:e. G4:s C5:h. | A4:w", name="quest")
    s.check_melody(motif, "motif")
    I.play_notes(s, horn_b, I.horns(attack=0.05), motif, vel=0.85, mono=True)
    arrange.pad(s, str_b, I.strings(attack=0.15, release=1.2, bright=0.4, voices=4), 0, 2, voices=4, lo=53,
                hi=72, vel=0.6)
    arrange.bass_line(s, str_b, I.cellos(attack=0.15, release=1.0), 0, 2, [(0, "root", 4)], lo=29, hi=41,
                      vel=0.6)
    rng = dsp.rng_for("quest")
    harp = I.Pluck("harp")
    for k, p in enumerate([53, 57, 60, 65, 69, 72, 77]):
        s.add(harp_b, s.seconds(4) + k * 0.07, harp.render(p, 1.5, 0.55, rng, pan=-0.4 + 0.13 * k))
    glock = I.Bell("glock")
    for k, p in enumerate([84, 89, 93]):
        s.add(bell_b, s.seconds(4) + 0.2 + k * 0.12, glock.render(p, 0.5, 0.5, rng, pan=0.3))
    return {"audio": _finish(s, 5.2, 1.2), "loop": False, "warnings": s.warnings}


def discovery():
    s = Song("discovery", bpm=60, bars=2, loop=False, tail=2.5)
    s.set_chords([[("Dmadd9", 1.5), ("Bb/D", 1.0), ("F", 5.5)]])
    s.reverb("vast", rt60=3.6, predelay=0.04, brightness=8000)
    swell_b = s.bus("swell", level=-4.0, sends={"vast": 0.6}, chorus=dict(mix=0.35, rate=0.15))
    choir_b = s.bus("choir", level=-3.0, sends={"vast": 0.6})
    horn_b = s.bus("horns", level=0.0, sends={"vast": 0.45})
    harp_b = s.bus("harp", level=-7.0, sends={"vast": 0.5})
    fx_b = s.bus("fx", level=-9.0, sends={"vast": 0.5})
    rng = dsp.rng_for("discovery")
    strings = I.strings(attack=1.0, release=1.8, bright=0.45, voices=5)
    choir = I.choir("sop_a", attack=0.7, release=1.8)
    prev = None
    for st, en, c in s.chords:
        v = theory.voice_chord(c, prev, 5, 57, 81)
        prev = v
        for p in v:
            sw = 0.7 if st < 2.5 else 0.0
            s.add(swell_b, s.seconds(st), strings.render(p, s.seconds(en - st) + 0.1, 0.65, rng, swell=sw,
                                                         attack=None if sw else 0.25))
            if st >= 1.5:
                s.add(choir_b, s.seconds(st), choir.render(p, s.seconds(en - st) + 0.1, 0.7, rng))
    arrival = s.seconds(2.5)
    motif = theory.parse("C4:e F4:q. A4:h", 4, name="arrival")
    I.play_notes(s, horn_b, I.horns(attack=0.08), motif, at=2.5, vel=0.85, mono=True)
    harp = I.Pluck("harp")
    tones = [p for p in range(53, 92) if p % 12 in (5, 9, 0)]
    for k, p in enumerate(tones):
        s.add(harp_b, arrival - 0.6 + k * 0.045, harp.render(p, 1.0, 0.45, rng, pan=-0.6 + 1.2 * k / len(tones)))
    s.add(fx_b, 0.0, dsp.to_stereo(drums.reverse_swell(arrival, rng, 1500, 12000), 0.0))
    arrange.roll(s, fx_b, lambda v, r: drums.timpani(41, v, r), 1.5, 1.0, rate=10, vel0=0.1, vel1=0.6)
    s.add(fx_b, arrival, dsp.to_stereo(drums.timpani(41, 0.8, rng), 0.0))
    s.add(fx_b, arrival, dsp.to_stereo(drums.crash(0.4, rng), 0.3))
    glock = I.Bell("glock", decay_scale=1.5)
    for k, p in enumerate([89, 93, 96, 101]):
        s.add(fx_b, arrival + 0.3 + 0.15 * k, glock.render(p, 0.4, 0.45, rng, pan=0.4 - 0.25 * k))
    return {"audio": _finish(s, 6.5, 2.0), "loop": False, "warnings": s.warnings}
