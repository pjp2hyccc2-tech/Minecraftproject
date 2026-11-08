"""Worn armor textures (64x32, vanilla humanoid armor layout).

layer_1: helmet (head cube), chestplate (body + arms), boots (legs, lower part)
layer_2: leggings (body = waist, legs)

UV layout of a cube at texOffs (u, v) with size (w, h, d):
    top    (u+d,     v)   w x d      bottom (u+d+w, v) w x d
    right  (u,       v+d) d x h      front  (u+d,   v+d) w x h
    left   (u+d+w,   v+d) d x h      back   (u+2d+w, v+d) w x h
"right" is the wearer's right side (-X); on arms/legs it is the outer face (the
left limbs mirror the right ones). In a side face the column next to the front face
is the front edge: right faces have the front at their last column, left faces at
their first column.

Every face is drawn as a little ASCII picture with the theme letters from
armor_icons.THEMES; '.' stays transparent so the player's skin shows through.
"""
from __future__ import annotations

import math
import random

import numpy as np

import palette as P
from armor_icons import THEMES
from px import Canvas, Ramp, hexc, seed_of, LIGHT

CUBES = {
    # part: (u, v, w, h, d)
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}


def face_rect(part: str, face: str):
    u, v, w, h, d = CUBES[part]
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }[face]


# --------------------------------------------------------------------------- face helpers
def fill(w, h, ch="A"):
    return [ch * w for _ in range(h)]


def mirror(face):
    return [r[::-1] for r in face]


def rows_set(face, start, rows):
    out = list(face)
    for i, r in enumerate(rows):
        out[start + i] = r
    return out


def blank(w, h):
    return fill(w, h, ".")


def lower(face, keep):
    """Keep only the last ``keep`` rows (others transparent)."""
    h = len(face)
    w = len(face[0])
    return ["." * w if i < h - keep else face[i] for i in range(h)]


METAL = {id(r) for r in (P.IRON, P.IRON_DARK, P.STEEL, P.STEEL_DARK, P.SILVER, P.FROST_STEEL, P.SANDSTONE,
                         P.HOLY_WHITE, P.ABYSS, P.GOLD, P.HOLY_GOLD)}


def render_face(rows, mats, seed: int, grad: float = 0.7, edge: float = 0.85, noise: float = 0.04) -> Canvas:
    h, w = len(rows), len(rows[0])
    c = Canvas(w, h)
    rng = random.Random(seed)
    letters = {}
    for y, r in enumerate(rows):
        assert len(r) == w, (rows, r)
        for x, ch in enumerate(r):
            if ch != ".":
                letters.setdefault(ch.upper(), np.zeros((h, w), bool))[y, x] = True
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            key = ch.upper()
            spec = mats[key]
            m = letters[key]
            # local bevel inside the letter region, but face borders do not count as edges
            up = y > 0 and m[y - 1, x]
            dn = y < h - 1 and m[y + 1, x]
            lf = x > 0 and m[y, x - 1]
            rt = x < w - 1 and m[y, x + 1]
            tone = 3.0
            if y > 0 and not up:
                tone += edge
            if x > 0 and not lf:
                tone += edge * 0.5
            if y < h - 1 and not dn:
                tone -= edge
            if x < w - 1 and not rt:
                tone -= edge * 0.5
            tone += grad * (0.5 - y / max(1, h - 1))
            r01 = rng.random()
            if r01 < noise and isinstance(spec, Ramp) and id(spec) in METAL:
                tone += 0.6 if rng.random() < 0.5 else -0.6
            if ch.islower():
                tone -= 1.6
            if isinstance(spec, Ramp):
                k = int(math.floor(tone + 0.5))
                c.put(x, y, spec[max(1, min(len(spec) - 1, k))])
            elif spec[0] == "flat":
                c.put(x, y, spec[1])
            elif spec[0] == "glow":
                c.put(x, y, spec[1][4] if not ch.islower() else spec[1][3])
            elif spec[0] == "gem":
                ramp = spec[1]
                # top-left of a gem blob bright, rest mid
                c.put(x, y, ramp[5] if not up and not lf else ramp[3] if (up and lf) else ramp[4])
            elif spec[0] == "tone":
                k = int(math.floor(tone + 0.5)) + spec[2]
                c.put(x, y, spec[1][max(1, min(len(spec[1]) - 1, k))])
    return c


class Layer:
    def __init__(self, mats, seed_name):
        self.c = Canvas(64, 32)
        self.mats = mats
        self.seed = seed_of(seed_name)

    def put_face(self, part, face, rows, **kw):
        if rows is None:
            return
        x0, y0, w, h = face_rect(part, face)
        assert len(rows) == h and all(len(r) == w for r in rows), (part, face, w, h, rows)
        fc = render_face(rows, self.mats, self.seed + x0 * 131 + y0 * 7, **kw)
        self.c.paste(fc, x0, y0)


# --------------------------------------------------------------------------- generic pieces
def arm_faces(pauldron="S", sleeve="A", cuff="B", pr=3, cuff_rows=2, outer_extra=None, front_extra=None):
    """Arm cover: pauldron rows on top, sleeve, cuff at the wrist."""
    def face(extra=None):
        rows = [pauldron * 4] * pr + [sleeve * 4] * (12 - pr - cuff_rows) + [cuff * 4] * cuff_rows
        rows[pr - 1] = pauldron.lower() * 4 if pr else rows[0]
        if extra:
            for i, r in extra.items():
                rows[i] = r
        return rows
    return {
        "right": face(outer_extra), "front": face(front_extra), "left": face(), "back": face(),
        "top": [pauldron * 4] * 4, "bottom": None,
    }


def boot_faces(top="B", body="A", sole="b", rows=5, outer_extra=None, front_extra=None):
    def face(extra=None):
        r = ["...."] * (12 - rows) + [top * 4] + [body * 4] * (rows - 2) + [sole * 4]
        if extra:
            for i, s in extra.items():
                r[i] = s
        return r
    return {"right": face(outer_extra), "front": face(front_extra), "left": face(), "back": face(),
            "bottom": [sole * 4] * 4, "top": None}


def leg_faces(body="A", belt=None, knee=None, extra=None, front_extra=None):
    def face(ex=None):
        r = [body * 4] * 12
        if knee:
            r[5] = knee * 4
            r[6] = knee.lower() * 4
        if ex:
            for i, s in ex.items():
                r[i] = s
        return r
    return {"right": face(extra), "front": face(front_extra or extra), "left": face(extra), "back": face(extra),
            "top": [body * 4] * 4, "bottom": None}


def waist_faces(front_rows, back_rows=None, side="A", n=4):
    """Leggings part on the body cube: only the lowest ``n`` rows."""
    b = back_rows or front_rows
    return {
        "front": ["........"] * (12 - len(front_rows)) + front_rows,
        "back": ["........"] * (12 - len(b)) + b,
        "right": ["...."] * (12 - n) + [side * 4] * n,
        "left": ["...."] * (12 - n) + [side * 4] * n,
        "top": None, "bottom": None,
    }


# --------------------------------------------------------------------------- the 13 sets
def set_spec(key):
    """Returns dict(head=..., body=..., arm=..., boots=..., legs=..., waist=...) of face dicts."""
    if key == 1:   # iron: open kettle helm, plain plate
        head = {
            "front": ["AAAAAAAA", "BBBBBBBB", "AAAaaAAA", "A..AA..A", "A..AA..A", "AA.Aa.AA", "AA....AA", "Ab....bA"],
            "right": ["AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAaAA", "AAAAAAAA", "AAAAAAAA", "bbbbbbbA"],
            "back": ["AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "bbbbbbbb"],
            "top": fill(8, 8, "A"),
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["AbAAAAbA", "AAAAAAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA", "AAAAAAAA", "AAAaaAAA",
                      "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AbAAAAbA"],
            "back": ["AAAAAAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": fill(4, 12, "A"), "left": fill(4, 12, "A"), "top": fill(8, 4, "A"), "bottom": None,
        }
        arm = arm_faces("A", "A", "B", pr=4)
        boots = boot_faces("B", "A", "b", rows=5)
        legs = leg_faces("A", knee="B")
        waist = waist_faces(["BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"])
    elif key == 2:   # steel: closed great helm, polished plate with rivets and rims
        head = {
            "front": ["AAABBAAA", "AAABBAAA", "AAABBAAA", "DDDDDDDD", "AAABBAAA", "AaAaaAaA", "AAABBAAA", "BBBBBBBB"],
            "right": ["AAAAAAAA", "AAAAAAAA", "AAAAAAaA", "AAAAAAAA", "AAAAAAAA", "AaAAAAaA", "AAAAAAAA", "BBBBBBBB"],
            "back": ["AAABBAAA", "AAABBAAA", "AAABBAAA", "AAABBAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "top": ["AAABBAAA"] * 8,
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["BBBBBBBB", "AAABBAAA", "AaABBAaA", "AAABBAAA", "AAABBAAA", "AAABBAAA", "AaABBAaA",
                      "AAABBAAA", "BBBBBBBB", "AAAAAAAA", "AaAAAAaA", "AAAAAAAA"],
            "back": ["BBBBBBBB", "AAAAAAAA", "AaAAAAaA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AaAAAAaA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": fill(4, 12, "A"), "left": fill(4, 12, "A"), "top": ["BBBBBBBB"] + fill(8, 3, "A"), "bottom": None,
        }
        arm = arm_faces("B", "A", "B", pr=4, outer_extra={1: "BaaB", 2: "BBBB"})
        arm["right"][0] = "BBBB"
        boots = boot_faces("B", "A", "b", rows=6, front_extra={9: "AaaA"})
        legs = leg_faces("A", knee="B", extra={2: "AaAA", 9: "AAaA"})
        waist = waist_faces(["BBBBBBBB", "BBBGGBBB", "AAAAAAAA", "AaAAAAaA"])
    elif key == 3:   # desert: gold-banded sandstone helm, linen veil, lamellar with sash
        head = {
            "front": ["AAAAAAAA", "BBBGGBBB", "BBBBBBBB", "C......C", "C......C", "C......C", "CC....CC", "CC....CC"],
            "right": ["AAAAAAAA", "BBBBBBBB", "BBBBBBBB", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "cCcCcCcC"],
            "back": ["AAAAAAAA", "BBBBBBBB", "BBBBBBBB", "CCCCCCCC", "CCCCCCCC", "CCCCcCCC", "CCCCCCCC", "cCcCcCcC"],
            "top": ["AAAAAAAA", "AAABBAAA", "AABAABAA", "ABAGGABA", "ABAGGABA", "AABAABAA", "AAABBAAA", "AAAAAAAA"],
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["BBBBBBBB", "AAaAAaAA", "AAAAAAAA", "AaABBAaA", "AABGGBAA", "AAABBAAA", "AAaAAaAA",
                      "AAAAAAAA", "EEEEEEEE", "EEEEEEeE", "AaAAAAaA", "BBBBBBBB"],
            "back": ["BBBBBBBB", "AAaAAaAA", "AAAAAAAA", "AaAAAAaA", "AAAAAAAA", "AAAAAAAA", "AAaAAaAA",
                     "AAAAAAAA", "EEEEEEEE", "EeEEEEEE", "AaAAAAaA", "BBBBBBBB"],
            "right": ["BBBB"] + ["AaAA", "AAAA", "AAaA"] * 2 + ["AAAA", "EEEE", "EEEE", "AAAA", "BBBB"],
            "left": None, "top": fill(8, 4, "B"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("B", "C", "B", pr=3, cuff_rows=2, outer_extra={1: "BGBB"})
        boots = boot_faces("B", "A", "b", rows=6, front_extra={7: "CCCC", 8: "CCCC"})
        legs = leg_faces("C", extra={8: "BBBB", 9: "CCCC"})
        legs["right"] = ["AAAA", "AaAA", "AAAA", "BBBB"] + ["CCCC"] * 4 + ["BBBB"] + ["CCCC"] * 3
        waist = waist_faces(["BBBBBBBB", "EEEEEEEE", "EEEEEeEE", "AaAAAAaA"], side="E")
    elif key == 4:   # frost: crystal-crested helm with fur rim, frosty plate with ice accents
        head = {
            "front": ["ASSAASSA", "AAAAAAAA", "AAAAAAAA", "ADDDDDDA", "AAADDAAA", "AAADDAAA", "EEEEEEEE", "EeEEEEeE"],
            "right": ["AAASSAAA", "AAAAAAAA", "AAAAAAAA", "AASAAAAA", "AAAAAAAA", "AAAAAAAA", "EEEEEEEE", "EEeEEeEE"],
            "back": ["AAASSAAA", "AAAAAAAA", "AAAAAAAA", "AAASSAAA", "AAAAAAAA", "AAAAAAAA", "EEEEEEEE", "EeEEEEeE"],
            "top": ["AAASSAAA"] * 8,
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["EEEEEEEE", "EEEeEEEE", "AAASSAAA", "AASAASAA", "ASAAAASA", "AAASSAAA", "AAAAAAAA",
                      "AAABBAAA", "BBBBBBBB", "AAAAAAAA", "AAASSAAA", "AAAAAAAA"],
            "back": ["EEEEEEEE", "EeEEEEEE", "AAAAAAAA", "AAASSAAA", "AAAAAAAA", "AAAAAAAA", "AAASSAAA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": ["EEEE", "EEEE"] + fill(4, 6, "A") + ["BBBB"] + fill(4, 3, "A"),
            "left": None, "top": fill(8, 4, "E"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("E", "A", "B", pr=3, outer_extra={3: "ASSA", 4: "AASA"})
        boots = boot_faces("E", "A", "b", rows=6, outer_extra={8: "AASA"})
        boots["right"][6] = "EEEE"
        legs = leg_faces("A", knee="B", extra={7: "ASAA", 8: "AASA"})
        waist = waist_faces(["EEEEEEEE", "EEEeEEEE", "AAAAAAAA", "AAASSAAA"], side="A")
    elif key == 5:   # flame dragon: red scale, glowing eyes, bone horn bases, gold belly plates
        sc = ["AaAaAaAa", "AAAAAAAA", "aAaAaAaA", "AAAAAAAA"]
        head = {
            "front": ["SAAAAAAS", "SAaAAaAS", "AAAAAAAA", "RRRAARRR", "ADDAADDA", "AAAAAAAA", "BAAaaAAB", "BBBBBBBB"],
            "right": ["AAAASSAA", "AAASSSAA", "AAAAAAAA", "AaAaAaAa", "AAAAAAAA", "aAaAaAaA", "AAAAAAAA", "BBBBBBBB"],
            "back": sc + sc,
            "top": ["SSAAAASS", "SAAAAAAS", "AAAaaAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA"],
        }
        head["back"] = sc + sc[:3] + ["BBBBBBBB"]
        head["left"] = mirror(head["right"])
        body = {
            "front": ["AAAAAAAA", "AaABBAaA", "AAABBAAA", "AaBBBBaA", "AABBBBAA", "AaBbbBaA", "AABBBBAA",
                      "AaBbbBaA", "AABBBBAA", "BBBGGBBB", "AaAAAAaA", "AAAAAAAA"],
            "back": sc + sc + ["BBBBBBBB", "AaAaAaAa", "AAAAAAAA", "aAaAaAaA"],
            "right": ["AaAa", "AAAA", "aAaA", "AAAA"] * 2 + ["AaAa", "BBBB", "AAAA", "aAaA"],
            "left": None, "top": fill(8, 4, "A"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("S", "A", "B", pr=3, outer_extra={4: "AaAa", 6: "aAaA", 8: "AaAa"})
        arm["right"][0] = "SSAS"
        boots = boot_faces("B", "C", "S", rows=5, front_extra={11: "SbSb"})
        legs = leg_faces("A", knee="B", extra={1: "AaAa", 3: "aAaA", 8: "AaAa", 10: "aAaA"})
        legs["front"][7] = "BSSB"
        waist = waist_faces(["BBBBBBBB", "BBBGGBBB", "AaAaAaAa", "AAAAAAAA"])
    elif key == 6:   # celestial: white plate, gold trims, wing motifs, star
        head = {
            "front": ["AAABBAAA", "AABBBBAA", "BBBBBBBB", "ADDDDDDA", "AAADDAAA", "AAADDAAA", "AAAAAAAA", "BBBBBBBB"],
            "right": ["AAAAAAAA", "AAAAABBA", "AAABBEBA", "ABBEEEBA", "BEEEEEBA", "AEEEEBAA", "AAAAAAAA", "BBBBBBBB"],
            "back": ["AAAAAAAA", "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAABBAAA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "top": ["AAABBAAA"] * 8,
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["BBBBBBBB", "AAAAAAAA", "AAABBAAA", "AABGGBAA", "ABBGGBBA", "AABBBBAA", "AAABBAAA",
                      "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "back": ["BBBBBBBB", "AAAAAAAA", "BEAAAAEB", "BEEAAEEB", "ABEEEEBA", "AABEEBAA", "AAABBAAA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": ["BBBB"] + fill(4, 7, "A") + ["BBBB"] + fill(4, 3, "A"),
            "left": None, "top": ["BBBBBBBB"] + fill(8, 3, "A"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("B", "A", "B", pr=3, outer_extra={0: "BBBB", 1: "BEEB", 2: "BBBB"})
        boots = boot_faces("B", "A", "B", rows=6, outer_extra={7: "AEEA", 8: "EEAA"})
        legs = leg_faces("A", knee="B", extra={11: "BBBB"})
        waist = waist_faces(["BBBBBBBB", "AAABBAAA", "AAAAAAAA", "AAAAAAAA"])
    elif key == 7:   # abyss: black plate, violet runes and eye slits, spikes
        head = {
            "front": ["SASAASAS", "AAAAAAAA", "AAAGGAAA", "RRRAARRR", "ADDAADDA", "AAAaaAAA", "BAAaaAAB", "BBBBBBBB"],
            "right": ["SAASAASA", "AAAAAAAA", "AARAAAAA", "AAARRAAA", "AAAAARAA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "back": ["SAASSAAS", "AAAAAAAA", "AAARRAAA", "AARAARAA", "AAARRAAA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "top": ["SAAAAAAS", "AAAAAAAA", "AAASSAAA", "AASAASAA", "AASAASAA", "AAASSAAA", "AAAAAAAA", "SAAAAAAS"],
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["BBBBBBBB", "AAAAAAAA", "ARAAAARA", "AARAARAA", "AAAGGAAA", "AAAGGAAA", "AARAARAA",
                      "ARAAAARA", "BBBBBBBB", "AAARRAAA", "AAAAAAAA", "AAAAAAAA"],
            "back": ["BBBBBBBB", "AAAAAAAA", "AAARRAAA", "AARAARAA", "ARAAAARA", "AARAARAA", "AAARRAAA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": ["BBBB", "AAAA", "ARAA", "AARA", "ARAA", "AAAA", "AAAA", "AAAA", "BBBB", "AAAA", "AAAA", "AAAA"],
            "left": None, "top": ["SAASSAAS"] + fill(8, 3, "A"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("S", "A", "B", pr=3, outer_extra={5: "ARAA", 6: "AARA", 7: "ARAA"})
        arm["right"][0] = "SASA"
        boots = boot_faces("B", "A", "b", rows=6, outer_extra={8: "ARRA", 7: "SAAS"})
        legs = leg_faces("A", knee="S", extra={2: "ARAA", 3: "AARA", 9: "AARA", 10: "ARAA"})
        waist = waist_faces(["BBBBBBBB", "BBBGGBBB", "AARAARAA", "AAARRAAA"])
    elif key == "knight":   # heavy silver plate, blue tabard with silver cross, cross visor
        head = {
            "front": ["AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "DDDDDDDD", "AAADDAAA", "AaADDAaA", "AAADDAAA", "BBBBBBBB"],
            "right": ["AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AaAAAAaA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "back": ["AAAAAAAA", "AAAAAAAA", "AAAEEAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "BBBBBBBB"],
            "top": ["AAAEEAAA"] * 8,
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["AAAAAAAA", "ACCCCCCA", "ACCCFCCA", "ACCFFFCA", "ACCCFCCA", "ACCCFCCA", "ACCCFCCA",
                      "ACCCCCCA", "BBBBBBBB", "ACCCCCCA", "ACCCCCCA", "ACCcCCCA"],
            "back": ["AAAAAAAA", "ACCCCCCA", "ACCCCCCA", "ACCCCCCA", "ACCCCCCA", "ACCCCCCA", "ACCCCCCA",
                     "ACCCCCCA", "BBBBBBBB", "ACCCCCCA", "ACCCCCCA", "ACCCcCCA"],
            "right": fill(4, 8, "A") + ["BBBB"] + fill(4, 3, "A"),
            "left": fill(4, 8, "A") + ["BBBB"] + fill(4, 3, "A"), "top": fill(8, 4, "A"), "bottom": None,
        }
        arm = arm_faces("A", "A", "A", pr=4, cuff_rows=2, outer_extra={1: "AaaA", 4: "CCCC"})
        arm["front"][4] = "CCCC"
        boots = boot_faces("A", "A", "b", rows=6, front_extra={7: "CCCC"})
        legs = leg_faces("A", knee="A", extra={4: "AAAA", 5: "AaaA", 6: "AAAA"})
        legs["front"] = ["CCCC", "CCCC", "CcCC", "AAAA", "AAAA", "AaaA", "AAAA", "AAAA", "AAAA", "AaAA", "AAAA", "AAAA"]
        waist = waist_faces(["BBBBBBBB", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC"], side="A")
    elif key == "assassin":   # dark hood + crimson scarf, dark leather with straps
        head = {
            "front": ["CCCCCCCC", "CCCCCCCC", "C......C", "C......C", "EEEEEEEE", "EEEEEEEE", "EEEeEEEE", "EEEEEEEE"],
            "right": ["CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCE", "CCCCCCEE", "CCCCCCEE", "CCCCCEEE"],
            "back": ["CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCcCCC", "CCCCCCCC", "EEEEEEEE"],
            "top": fill(8, 8, "C"),
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["EEEEEEEE", "AAAAAABA", "AAAAABAA", "AAAABAAA", "AAABAAAA", "AABSAAAA", "ABAAAAAA",
                      "BAAAAAAA", "EEEEEEEE", "EEEEEEeE", "AAAAAAEA", "AAAAAAEA"],
            "back": ["EEEEEEEE", "ABAAAAAA", "AABAAAAA", "AAABAAAA", "AAAABAAA", "AAAAABAA", "AAAAAABA",
                     "AAAAAAAB", "EEEEEEEE", "EEEEEEEE", "AAAAAAAA", "AAAAAAAA"],
            "right": fill(4, 8, "A") + ["EEEE", "EEEE", "AAAA", "AAAA"],
            "left": fill(4, 8, "A") + ["EEEE", "EEEE", "AAAA", "AAAA"], "top": fill(8, 4, "E"), "bottom": None,
        }
        arm = arm_faces("A", "A", "B", pr=2, cuff_rows=4, outer_extra={8: "BAAB", 9: "ABBA", 10: "BAAB", 11: "ABBA"})
        boots = boot_faces("A", "A", "b", rows=6, outer_extra={7: "BAAB", 8: "ABBA", 9: "BAAB"})
        legs = leg_faces("A", extra={6: "BAAA", 7: "ABAA", 8: "AABA", 9: "AAAB"})
        waist = waist_faces(["EEEEEEEE", "EEEEEEeE", "AAAAAAEA", "AAAAAAEA"])
    elif key == "mage":   # gold circlet with violet gem, deep blue/violet robe with gold trim
        head = {
            "front": ["........", "BBBGGBBB", "........", "........", "........", "........", "........", "........"],
            "right": ["........", "BBBBBBBB", "........", "........", "........", "........", "........", "........"],
            "back": ["........", "BBBBBBBB", "........", "........", "........", "........", "........", "........"],
            "top": None,
        }
        head["left"] = head["right"]
        body = {
            "front": ["AABBBBAA", "AAABBAAA", "AAABBAAA", "AAABBAAA", "AAABBAAA", "AAAGGAAA", "AAABBAAA",
                      "EEEEEEEE", "EEEBBEEE", "AAABBAAA", "AAABBAAA", "AAABBAAA"],
            "back": ["BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAaaAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA",
                     "EEEEEEEE", "EEEEEEEE", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": fill(4, 7, "A") + ["EEEE", "EEEE"] + fill(4, 3, "A"),
            "left": fill(4, 7, "A") + ["EEEE", "EEEE"] + fill(4, 3, "A"), "top": fill(8, 4, "A"), "bottom": None,
        }
        arm = arm_faces("A", "A", "B", pr=2, cuff_rows=2)
        arm["right"][9] = "BBBB"
        arm["front"][9] = "BBBB"
        boots = boot_faces("B", "A", "B", rows=4)
        legs = leg_faces("A", extra={10: "BBBB", 11: "AAAA"})
        legs["front"] = ["AAAA", "AAAB", "AAAB", "AAAB", "AAAB", "AAAB", "AAAB", "AAAB", "AAAB", "AAAB", "BBBB", "AAAA"]
        waist = waist_faces(["EEEEEEEE", "EEEBBEEE", "AAABBAAA", "AAABBAAA"])
    elif key == "ranger":   # green hood, leather vest, green cloak on the back
        head = {
            "front": ["CCCCCCCC", "CCCCCCCC", "C......C", "C......C", "C......C", "C......C", "CC....CC", "CCC..CCC"],
            "right": ["CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCcCC", "CCCCCCCC", "BBBBBBBC"],
            "back": ["CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC"],
            "top": ["CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC"],
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["CCCCCCCC", "CCAAAACC", "CAAbbAAC", "CAAAAAAC", "CAAbbAAC", "CAAAAAAC", "CAAAAAAC",
                      "CAAAAAAC", "BBBGBBBB", "AAAAAAAA", "AAAAAAAA", "AaAAAAaA"],
            "back": ["CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC",
                     "CCCCcCCC", "CCCCCCCC", "CCCCCCCC", "CcCCCCcC", "cCcCcCcC"],
            "right": fill(4, 8, "C") + ["BBBB", "CCCC", "CCCC", "cCcC"],
            "left": fill(4, 8, "C") + ["BBBB", "CCCC", "CCCC", "cCcC"], "top": fill(8, 4, "C"), "bottom": None,
        }
        arm = arm_faces("C", "A", "B", pr=3, cuff_rows=4, outer_extra={9: "BbbB"})
        boots = boot_faces("C", "A", "b", rows=6, outer_extra={7: "CCCC"})
        legs = leg_faces("A", extra={2: "CCCC", 3: "CCCC", 7: "BBBB", 9: "BBBB"})
        waist = waist_faces(["BBBGBBBB", "AAAAAAAA", "AAAAAAAA", "AaAAAAaA"])
    elif key == "priest":   # white cowl with gold trim and symbol, white/gold vestments with stole
        head = {
            "front": ["CCCEECCC", "CBBBBBBC", "CB....BC", "CB....BC", "CB....BC", "CB....BC", "CBB..BBC", "CCB..BCC"],
            "right": ["CCCCCCCC", "CCCCCCCB", "CCCCCCCB", "CCCCCCCB", "CCCcCCCB", "CCCCCCCB", "CCCCCCCB", "BBBBBBBB"],
            "back": ["CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCcCCCC", "CCCCCCCC", "CCCCCCCC", "BBBBBBBB"],
            "top": fill(8, 8, "C"),
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["CBBBBBBC", "CCBCCBCC", "CCBEEBCC", "CCEEEECC", "CCBEEBCC", "CCBCCBCC", "CCBCCBCC",
                      "BBBBBBBB", "CCBCCBCC", "CCBCCBCC", "CCBCCBCC", "CCBCCBCC"],
            "back": ["CBBBBBBC", "CCCCCCCC", "CCCEECCC", "CCEEEECC", "CCCEECCC", "CCCCCCCC", "CCCCCCCC",
                     "BBBBBBBB", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC"],
            "right": fill(4, 7, "C") + ["BBBB"] + fill(4, 4, "C"),
            "left": fill(4, 7, "C") + ["BBBB"] + fill(4, 4, "C"), "top": fill(8, 4, "C"), "bottom": None,
        }
        arm = arm_faces("B", "C", "B", pr=2, cuff_rows=2)
        arm["right"][0] = "CCCC"
        arm["right"][1] = "BBBB"
        boots = boot_faces("B", "C", "B", rows=5)
        legs = leg_faces("C", extra={11: "BBBB"})
        legs["front"] = ["CCCC", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "CCCB", "BBBB"]
        waist = waist_faces(["BBBBBBBB", "CCBCCBCC", "CCBCCBCC", "CCBCCBCC"], side="C")
    elif key == "spellblade":   # dark steel with glowing cyan runes and visor
        head = {
            "front": ["AAAAAAAA", "AAARRAAA", "AAAAAAAA", "RRRRRRRR", "AAAAAAAA", "AAa..aAA", "AA....AA", "BA....AB"],
            "right": ["AAAAAAAA", "AAAAAAAA", "AARAAAAA", "AAARRRRR", "AAAAAAAA", "AAAAARAA", "AAAAAAAA", "BBBBBBBB"],
            "back": ["AAAAAAAA", "AAAAAAAA", "AAARRAAA", "RRRAARRR", "AAAAAAAA", "AAARRAAA", "AAAAAAAA", "BBBBBBBB"],
            "top": ["AAARRAAA", "AAAAAAAA", "AAAAAAAA", "AAARRAAA", "AAAAAAAA", "AAAAAAAA", "AAARRAAA", "AAAAAAAA"],
        }
        head["left"] = mirror(head["right"])
        body = {
            "front": ["BBBBBBBB", "AAAAAAAA", "ARRAARRA", "AAARRAAA", "AAAGGAAA", "AAARRAAA", "AARAARAA",
                      "ARAAAARA", "BBBBBBBB", "AAAAAAAA", "AAARRAAA", "AAAAAAAA"],
            "back": ["BBBBBBBB", "AAAAAAAA", "AARRRRAA", "AARAARAA", "AAARRAAA", "AAAAAAAA", "AAARRAAA",
                     "AAAAAAAA", "BBBBBBBB", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"],
            "right": ["BBBB", "AAAA", "ARAA", "ARAA", "ARRA", "AAAA", "AAAA", "AAAA", "BBBB", "AAAA", "AAAA", "AAAA"],
            "left": None, "top": fill(8, 4, "A"), "bottom": None,
        }
        body["left"] = mirror(body["right"])
        arm = arm_faces("A", "A", "B", pr=3, outer_extra={1: "ARRA", 5: "AARA", 6: "AARA", 7: "ARRA"})
        boots = boot_faces("B", "A", "b", rows=6, outer_extra={8: "ARRA"})
        legs = leg_faces("A", knee="B", extra={2: "AARA", 3: "AARA", 9: "ARAA", 10: "ARAA"})
        waist = waist_faces(["BBBBBBBB", "BBBGGBBB", "AARAARAA", "AAAAAAAA"])
    else:
        raise KeyError(key)
    return dict(head=head, body=body, arm=arm, boots=boots, legs=legs, waist=waist)


def build(key):
    spec = set_spec(key)
    mats = THEMES[key]
    l1 = Layer(mats, f"armor_l1_{key}")
    l2 = Layer(mats, f"armor_l2_{key}")
    for face, rows in spec["head"].items():
        l1.put_face("head", face, rows)
    for face, rows in spec["body"].items():
        l1.put_face("body", face, rows)
    for face, rows in spec["arm"].items():
        l1.put_face("arm", face, rows)
    for face, rows in spec["boots"].items():
        l1.put_face("leg", face, rows)
    for face, rows in spec["waist"].items():
        l2.put_face("body", face, rows)
    for face, rows in spec["legs"].items():
        l2.put_face("leg", face, rows)
    return l1.c, l2.c


SETS = [1, 2, 3, 4, 5, 6, 7, "knight", "assassin", "mage", "ranger", "priest", "spellblade"]


def file_names(key):
    base = f"er_tier{key}" if isinstance(key, int) else f"er_job_{key}"
    return f"{base}_layer_1.png", f"{base}_layer_2.png"


# --------------------------------------------------------------------------- preview helpers
MANNEQUIN_SKIN = (150, 120, 100, 255)


def _crop(c: Canvas, part, face, flip=False):
    x0, y0, w, h = face_rect(part, face)
    out = Canvas(w, h)
    out.a = c.a[y0:y0 + h, x0:x0 + w].copy()
    if flip:
        out.a = out.a[:, ::-1].copy()
    return out


def mannequin(view):
    """Simple neutral body under the armor so open helmets / hoods show a face."""
    c = Canvas(16, 32)
    skin = (196, 150, 118, 255)
    shirt = (92, 104, 120, 255)
    pants = (70, 70, 84, 255)
    hair = (84, 58, 40, 255)
    for y in range(8):
        for x in range(4, 12):
            c.put(x, y, skin)
    for x in range(4, 12):
        c.put(x, 0, hair)
        c.put(x, 1, hair)
    if view == "front":
        for x in (5, 6, 9, 10):
            c.put(x, 4, (255, 255, 255, 255))
        c.put(6, 4, (40, 60, 120, 255))
        c.put(9, 4, (40, 60, 120, 255))
        c.put(7, 6, (150, 96, 80, 255))
        c.put(8, 6, (150, 96, 80, 255))
    else:
        for y in range(2, 6):
            for x in range(4, 12):
                c.put(x, y, hair)
    for y in range(8, 20):
        for x in range(4, 12):
            c.put(x, y, shirt)
        for x in list(range(0, 4)) + list(range(12, 16)):
            c.put(x, y, skin if y > 16 else shirt)
    for y in range(20, 32):
        for x in range(4, 12):
            c.put(x, y, pants)
    return c


def paperdoll(l1: Canvas, l2: Canvas, view: str = "front") -> Canvas:
    """16x32 front or back view: mannequin, then leggings (layer 2), then layer 1."""
    out = mannequin(view)
    f = view
    # leggings
    if view == "front":
        out.paste(_crop(l2, "body", "front"), 4, 8)
        out.paste(_crop(l2, "leg", "front"), 4, 20)
        out.paste(_crop(l2, "leg", "front", flip=True), 8, 20)
    else:
        out.paste(_crop(l2, "body", "back"), 4, 8)
        out.paste(_crop(l2, "leg", "back", flip=True), 4, 20)
        out.paste(_crop(l2, "leg", "back"), 8, 20)
    # layer 1
    if view == "front":
        out.paste(_crop(l1, "head", "front"), 4, 0)
        out.paste(_crop(l1, "body", "front"), 4, 8)
        out.paste(_crop(l1, "arm", "front"), 0, 8)
        out.paste(_crop(l1, "arm", "front", flip=True), 12, 8)
        out.paste(_crop(l1, "leg", "front"), 4, 20)
        out.paste(_crop(l1, "leg", "front", flip=True), 8, 20)
    else:
        out.paste(_crop(l1, "head", "back"), 4, 0)
        out.paste(_crop(l1, "body", "back"), 4, 8)
        out.paste(_crop(l1, "arm", "back", flip=True), 0, 8)
        out.paste(_crop(l1, "arm", "back"), 12, 8)
        out.paste(_crop(l1, "leg", "back", flip=True), 4, 20)
        out.paste(_crop(l1, "leg", "back"), 8, 20)
    return out


def side_doll(l1: Canvas, l2: Canvas) -> Canvas:
    """8x32 right-side view (head right face, arm outer face over the body)."""
    out = Canvas(8, 32)
    skin = (196, 150, 118, 255)
    for y in range(8):
        for x in range(8):
            out.put(x, y, skin)
    for y in range(8, 32):
        for x in range(2, 6):
            out.put(x, y, (92, 104, 120, 255) if y < 20 else (70, 70, 84, 255))
    out.paste(_crop(l2, "body", "right"), 2, 8)
    out.paste(_crop(l2, "leg", "right"), 2, 20)
    out.paste(_crop(l1, "head", "right"), 0, 0)
    out.paste(_crop(l1, "body", "right"), 2, 8)
    out.paste(_crop(l1, "leg", "right"), 2, 20)
    out.paste(_crop(l1, "arm", "right"), 2, 8)
    return out
