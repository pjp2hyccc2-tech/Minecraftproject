"""The game's own tune (original): a warabe-uta-like hide-and-seek song for a toy music box.

Scale: A minor pentatonic (A C D E G) - the min'yo / warabe-uta scale.  The head is built on the falling minor
third G-E, the interval children chant 「もういいかい」 / 「まあだだよ」 on; the answer phrase sinks a step lower;
the song rises to the high A once and ends on D, the nucleus tone of the lower tetrachord, so it never comes to rest
on the tonic and loops back into its own call.  32 beats (8 bars of 4)."""
import dsp

#        beat  note   beats       (words it is shaped after - never sung)
TUNE = [(0, "G5", 1), (1, "G5", 1), (2, "E5", 1), (3, "G5", 1),          # mo- u  i-  i
        (4, "E5", 2), (6, "D5", 2),                                       # ka-    i
        (8, "E5", 1), (9, "E5", 1), (10, "D5", 1), (11, "C5", 1),         # ma- a  da- da
        (12, "D5", 2), (14, "A4", 2),                                     # yo-    o
        (16, "C5", 1), (17, "D5", 1), (18, "E5", 1), (19, "G5", 1),
        (20, "A5", 2), (22, "G5", 1), (23, "E5", 1),
        (24, "G5", 1), (25, "E5", 1), (26, "D5", 1), (27, "C5", 1),
        (28, "D5", 1.5), (29.5, "E5", 0.5), (30, "D5", 2)]

# the comb's low teeth: (beat, notes)
BASS = [(0, ("A3", "E4")), (4, ("D4", "A4")), (8, ("A3", "E4")), (12, ("D4",)), (14, ("A3",)),
        (16, ("C4", "G4")), (20, ("A3", "E4")), (24, ("G3", "D4")), (28, ("D4", "A4"))]

BEATS = 32

# Every tooth of the old toy comb is out of tune by its own amount (cents), the same every time it is plucked;
# the whole box sits flat.
BOX_FLAT = -32.0
TOOTH_CENTS = {"G3": 14, "A3": -18, "C4": 9, "D4": -22, "E4": 16, "G4": -11, "A4": 21, "C5": -15, "D5": 12,
               "E5": -26, "G5": 19, "A5": -9, "C6": 24, "D6": -17, "E6": 13, "G6": -20, "A6": 8}


def hz(name, cents=0.0):
    return dsp.hz(name, cents)


def box_hz(name, octave=0, extra=0.0):
    """Frequency of a tooth of the toy music box (its own detuning + the box's flatness)."""
    return dsp.hz(name, BOX_FLAT + TOOTH_CENTS.get(name, 0) + extra) * 2 ** octave
