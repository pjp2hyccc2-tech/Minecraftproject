"""Icons of the job skill trees (16x16) and the tree-shaped preview sheet.

The first 24 job skills keep their hand-drawn icons from skills.py. The other 300 nodes of the six trees are composed
from layers, so they stay consistent with those and with each other:

1. the job frame of skills.py; passives get a medallion ring inside, ultimates a gold bevel and a halo behind,
2. an effect layer drawn from shapes (slash arcs, rings, rays, spirals, beams, rain, waves, zones ...) in the colours of
   the skill's theme (fire, frost, holy ...),
3. an object: a hand-drawn glyph (sword, shield, flask, skull ...) lit and outlined like the original icons, or a drawn
   one (orbs, arrows, knives, portals, clocks),
4. a small badge for the main status effect or bonus (stun sparkle, poison drop, snowflake, x3 hits ...).

RECIPES (one line per node, in tree order) says which layers a node uses. Deterministic: no randomness at all.

    python3 tools/art/skill_tree.py            # writes the 300 icons and tools/art/skill_tree_preview.png
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import palette as P  # noqa: E402
import skills  # noqa: E402
from icon import render  # noqa: E402
from px import Canvas, Ramp, hexc, mix  # noqa: E402

JOBS = ["knight", "assassin", "mage", "ranger", "priest", "spellblade"]

# --------------------------------------------------------------------------- themes
THUNDER = Ramp("2a2406", "7a6a10", "c8b020", "f0dc3a", "fff27a", "ffffe0")
WIND = Ramp("0a2a26", "1d5e52", "36927e", "62c2a4", "a6ead2", "f0fff8")
ARCANE = Ramp("061a3a", "123c7a", "2a62b8", "4a90e8", "8ac4ff", "e4f4ff")
LIFE = P.HEAL_GREEN
# theme -> (main ramp, glow ramp)
THEMES = {
    "STEEL": (P.SILVER, P.HOLY_WHITE),
    "FIRE": (P.FLAME, P.EMBER),
    "MAGMA": (P.DRAGON, P.EMBER),
    "FROST": (P.ICE, P.ICE_DEEP),
    "THUNDER": (THUNDER, THUNDER),
    "HOLY": (P.HOLY_GOLD, P.HOLY_WHITE),
    "SHADOW": (P.VOID, P.VOID_GLOW),
    "ARCANE": (ARCANE, P.CYAN_RUNE),
    "VENOM": (P.SLIME, P.SPORE),
    "BLOOD": (P.BLOOD, P.POTION_RED),
    "WIND": (WIND, WIND),
    "EARTH": (P.SANDSTONE, P.BRONZE),
    "LIFE": (LIFE, P.SPORE),
    "LIGHT": (P.HOLY_WHITE, P.HOLY_GOLD),
}
HOOD = Ramp("0c0812", "1c1426", "2c2238", "3e3250", "54466a", "7a6c90")
BOMB = Ramp("0a0a10", "1c1c26", "2c2c3a", "40404f", "585868", "8a8a9a")

# --------------------------------------------------------------------------- object glyphs (<= 12x12)
# M = theme colour, A = theme glow, W blade, X white, G gold, B wood, L leather, S steel, C glass, E linen,
# O bone, D hood/dark, N bomb iron, Z holy white, R blood; lower case = two shades darker.
OBJECTS = {
    "sword": [
        "...........X",
        "..........XW",
        ".........XWw",
        "........XWw.",
        ".......XWw..",
        "..G...XWw...",
        "...G.XWw....",
        "....GWw.....",
        "...L.G......",
        "..L...G.....",
        ".L..........",
        "G...........",
    ],
    "greatsword": [
        ".....X......",
        "....XWw.....",
        "....XWw.....",
        "....XWw.....",
        "....XWw.....",
        "....XWw.....",
        "....XWw.....",
        "....XWw.....",
        ".GGGGGGGGG..",
        ".....L......",
        ".....L......",
        "....GGG.....",
    ],
    "dagger": [
        "........X",
        ".......XW",
        "......XWw",
        ".....XWw.",
        "..G.XWw..",
        "...GWw...",
        "..L.G....",
        ".L.......",
        "G........",
    ],
    "spear": [
        "..........XX",
        ".........XWX",
        "........XWW.",
        "........Ww..",
        ".......B....",
        "......B.....",
        ".....B......",
        "....B.......",
        "...B........",
        "..B.........",
        ".B..........",
        "B...........",
    ],
    "lance": [
        "........X...",
        ".......GWX..",
        "BBBBBBBGWWWX",
        "bbbbbbbGWWw.",
        ".......Gw...",
    ],
    "shield": [
        ".SSSSSSSSSS.",
        ".SMMMMMMMMS.",
        ".SMXMMMMMMS.",
        ".SMMMMMMMMS.",
        ".SMMMMMMMMS.",
        ".SMMMMMMMMS.",
        "..SMMMMMMS..",
        "..SMMMMMMS..",
        "...SMMMMS...",
        "....SMMS....",
        ".....SS.....",
    ],
    "shield_cross": [
        ".SSSSSSSSSS.",
        ".SMMMAAMMMS.",
        ".SMMMAAMMMS.",
        ".SAAAAAAAAS.",
        ".SMMMAAMMMS.",
        ".SMMMAAMMMS.",
        "..SMMAAMMS..",
        "..SMMAAMMS..",
        "...SMAAMS...",
        "....SMMS....",
        ".....SS.....",
    ],
    "shield_spikes": [
        ".....WW.....",
        "..W.SSSS.W..",
        "...SSMMSS...",
        "..SSMMMMSS..",
        "WSSMMAAMMSSW",
        "WSSMMAAMMSSW",
        "..SSMMMMSS..",
        "...SSMMSS...",
        "..W.SSSS.W..",
        ".....WW.....",
    ],
    "shield_broken": [
        ".SSSSS.SSSS.",
        ".SMMMMm.MMS.",
        ".SMXMMMm.MS.",
        ".SMMMMm.MMS.",
        ".SMMMMMm.MS.",
        ".SMMMMm.MMS.",
        "..SMMMMm.S..",
        "..SMMMm.MS..",
        "...SMMm.S...",
        "....SMS.....",
        ".....S......",
    ],
    "buckler": [
        "....SSSS....",
        "..SSMMMMSS..",
        ".SMMXMMMMMS.",
        ".SMXMMMMMMS.",
        "SMMMMAAMMMMS",
        "SMMMAAAAMMMS",
        "SMMMAAAAMMMS",
        "SMMMMAAMMMMS",
        ".SMMMMMMMMS.",
        ".SMMMMMMMMS.",
        "..SSMMMMSS..",
        "....SSSS....",
    ],
    "bow": [
        "....BB......",
        "...B.E......",
        "..B..E......",
        ".B...E......",
        ".B...E......",
        "B....LLLLWWX",
        ".B...E......",
        ".B...E......",
        "..B..E......",
        "...B.E......",
        "....BB......",
    ],
    "staff": [
        "........AAA.",
        ".......AXAAA",
        ".......AAAA.",
        "........GA..",
        ".......B....",
        "......B.....",
        ".....B......",
        "....B.......",
        "...B........",
        "..B.........",
        ".B..........",
        "B...........",
    ],
    "hammer": [
        "..SSSSSSSS..",
        "..SXSSSSSS..",
        "..SSSSSSSS..",
        "..ssssssss..",
        ".....BB.....",
        ".....BB.....",
        ".....BB.....",
        ".....BB.....",
        ".....LL.....",
        ".....LL.....",
        ".....GG.....",
    ],
    "skull": [
        "...OOOOOO...",
        "..OOOOOOOO..",
        "..OXOOOOOO..",
        "..ODDOODDO..",
        "..ODDOODDO..",
        "..OOOOOOOO..",
        "...OOddOO...",
        "....OOOO....",
        "....OdOd....",
    ],
    "heart": [
        "..MMM..MMM..",
        ".MMXMMMMMMM.",
        ".MXMMMMMMMM.",
        ".MMMMMMMMMM.",
        "..MMMMMMMM..",
        "...MMMMMM...",
        "....MMMM....",
        ".....MM.....",
    ],
    "cross": [
        ".....AA.....",
        ".....MM.....",
        ".....MM.....",
        ".AMMMMMMMMA.",
        ".AMMMMMMMMA.",
        ".....MM.....",
        ".....MM.....",
        ".....MM.....",
        ".....MM.....",
        ".....AA.....",
    ],
    "drop": [
        ".....MM.....",
        ".....MM.....",
        "....MMMM....",
        "....MMMM....",
        "...MXMMMM...",
        "...MXMMMM...",
        "...MMMMMM...",
        "...MMMMMM...",
        "....MMMM....",
    ],
    "flask": [
        ".....LL.....",
        ".....CC.....",
        ".....CC.....",
        "....CMMC....",
        "...CMMMMC...",
        "..CMMAMMMC..",
        "..CMAMMMMC..",
        "..CMMMMMMC..",
        "...CMMMMC...",
        "....CCCC....",
    ],
    "flame": [
        "......A.....",
        ".....AA.....",
        ".....AMA....",
        "....AMMA..A.",
        "...AMMMMA.A.",
        "..AMMXMMMAA.",
        "..AMXXXMMMA.",
        "..AMXXXXMMA.",
        "..AMMXXMMMA.",
        "...AMMMMMA..",
        "....AMMMA...",
    ],
    "snowflake": [
        ".....M.....",
        "...M.M.M...",
        "....MMM....",
        ".M...M...M.",
        "..M..M..M..",
        "MMMMMXMMMMM",
        "..M..M..M..",
        ".M...M...M.",
        "....MMM....",
        "...M.M.M...",
        ".....M.....",
    ],
    "bolt": [
        "......AAAA..",
        ".....AMMA...",
        "....AMMA....",
        "...AMXA.....",
        "..AMMMMMMA..",
        ".....AMMA...",
        "....AMMA....",
        "...AMMA.....",
        "..AMA.......",
        "..AA........",
    ],
    "crystal": [
        ".....A......",
        "....AMA.....",
        "....AMMA....",
        "...AMXMA....",
        "...AMXMMA...",
        "..AMXMMMA...",
        "..AMXMMMA...",
        "..AMMMMMA...",
        "...AMMMA....",
        "....AMA.....",
        ".....A......",
    ],
    "star": [
        ".....AA.....",
        ".....AA.....",
        "....AMMA....",
        "AAAAMMMMAAAA",
        ".AMMMXXMMMA.",
        "..AMMXXMMA..",
        "...AMMMMA...",
        "..AMMAAMMA..",
        "..AMA..AMA..",
        ".AA......AA.",
    ],
    "hourglass": [
        "..GGGGGGGG..",
        "...C....C...",
        "...CAAAAC...",
        "....CAAC....",
        ".....CC.....",
        ".....AC.....",
        "....CAAC....",
        "...CAAAAC...",
        "...CAAAAC...",
        "..GGGGGGGG..",
    ],
    "chains": [
        "MMM.........",
        "M.MM........",
        "MMM.M.......",
        "....MMM.....",
        "....M.MM....",
        "....MMM.M...",
        "........MMM.",
        "........M.MM",
        "........MMM.",
    ],
    "note": [
        "....MMMMMMM.",
        "....MMMMMMM.",
        "....M.....M.",
        "....M.....M.",
        "....M.....M.",
        "....M.....M.",
        "..MMM...MMM.",
        ".MMMM..MMMM.",
        ".MMM...MMM..",
    ],
    "wings": [
        "M..........M",
        "MM........MM",
        "MMm......mMM",
        "MMMm....mMMM",
        ".MMMm..mMMM.",
        ".MMMMAAMMMM.",
        "..MMM..MMM..",
        "...MM..MM...",
        "....M..M....",
    ],
    "feather": [
        "..........MM",
        ".........MMM",
        "........MMMm",
        ".......MMMm.",
        "......MMMm..",
        ".....MMMm...",
        "....MMMm....",
        "...MMMm.....",
        "..MMm.......",
        "..Bm........",
        ".B..........",
        "B...........",
    ],
    "flower": [
        "....MM....",
        "...MAAM...",
        ".MM.MM.MM.",
        "MAAMGGMAAM",
        "MAAMGGMAAM",
        ".MM.MM.MM.",
        "...MAAM...",
        "....MM....",
    ],
    "paw": [
        "...MM..MM...",
        "...MM..MM...",
        "MM........MM",
        "MM..MMMM..MM",
        "...MMMMMM...",
        "..MMMMMMMM..",
        "..MMMMMMMM..",
        "...MM..MM...",
    ],
    "fangs": [
        "..MMMMMMMM..",
        ".MMMMMMMMMM.",
        "MMZMZMMZMZMM",
        "MRZRZRRZRZRM",
        "MRRRRRRRRRRM",
        "MRRZRRRRZRRM",
        "MMZMZMMZMZMM",
        ".MMMMMMMMMM.",
        "..MMMMMMMM..",
    ],
    "hand": [
        "...M.M.M....",
        "...M.M.M.M..",
        "...M.M.M.M..",
        "...MMMMMMM..",
        "M..MMMMMMM..",
        "MM.MMMMMMM..",
        ".MMMMMMMMM..",
        "..MMMMMMMM..",
        "...MMMMMM...",
        "....MMMM....",
    ],
    "banner": [
        ".G..........",
        ".BMMMMMMMM..",
        ".BMMMMMMMMM.",
        ".BMMAAMMMM..",
        ".BMMAAMMMMM.",
        ".BMMMMMMMM..",
        ".B..........",
        ".B..........",
        ".B..........",
        ".B..........",
        "GGG.........",
    ],
    "crown": [
        "G....G....G",
        "GG..GGG..GG",
        "GGGGGGGGGGG",
        "GAGGGAGGGAG",
        "GGGGGGGGGGG",
        ".GGGGGGGGG.",
    ],
    "gem": [
        "...AAAAAA...",
        "..AXMMMMMA..",
        ".AXMMMMMMMA.",
        "AMMMMMMMMMMA",
        ".AMMMMMMMMA.",
        "..AMMMMMMA..",
        "...AMMMMA...",
        "....AMMA....",
        ".....AA.....",
    ],
    "eye": [
        "...MMMMMM...",
        ".MM......MM.",
        "M...AAAA...M",
        "M..AADDAA..M",
        "M..AADDAA..M",
        "M...AAAA...M",
        ".MM......MM.",
        "...MMMMMM...",
    ],
    "target": [
        ".....MM.....",
        "...MMMMMM...",
        "..M..MM..M..",
        ".M...MM...M.",
        ".M...AA...M.",
        "MMMMAAAAMMMM",
        "MMMMAAAAMMMM",
        ".M...AA...M.",
        ".M...MM...M.",
        "..M..MM..M..",
        "...MMMMMM...",
        ".....MM.....",
    ],
    "snake": [
        ".......MMM..",
        "......MAMMMR",
        "......MMM...",
        ".....MM.....",
        "....MM......",
        "...MM.......",
        "...MMMMMM...",
        "........MM..",
        ".........MM.",
        "........MM..",
        "..MMMMMMM...",
        ".MM.........",
    ],
    "helmet": [
        "....SSSS....",
        "...SSSSSS...",
        "..SXSSSSSS..",
        "..SSSSSSSS..",
        "..SDDDDDDS..",
        "..SSSSDSSS..",
        "..SSSSDSSS..",
        "..SSSSSSSS..",
        "..SSS..SSS..",
    ],
    "chest": [
        "..SS....SS..",
        ".SSSSSSSSSS.",
        ".SXSsSSsSSS.",
        ".SSSsMMsSSS.",
        "..SSSMMSSS..",
        "..SSSSSSSS..",
        "..SsSSSSsS..",
        "..SSSSSSSS..",
        "...SSSSSS...",
    ],
    "tower": [
        ".T.T.TT.T.T.",
        ".TTTTTTTTTT.",
        "..TTTTTTTT..",
        "..TXTTTTTT..",
        "..TTTDDTTT..",
        "..TTDDDDTT..",
        "..TTDDDDTT..",
        "..TTDDDDTT..",
        ".TTTTTTTTTT.",
    ],
    "shield_small": [
        "SSSSSSSS",
        "SMMMMMMS",
        "SMXMMMMS",
        "SMMMMMMS",
        "SMMMMMMS",
        ".SMMMMS.",
        ".SMMMMS.",
        "..SMMS..",
        "...SS...",
    ],
    "daggers": [
        "X..........X",
        ".XW......WX.",
        "..XW....WX..",
        "...XW..WX...",
        "....XWWX....",
        ".....WW.....",
        "....G..G....",
        "...GL..LG...",
        "...L....L...",
        "..L......L..",
        ".G........G.",
    ],
    "boot": [
        "....LLLL....",
        "MM..LLLL....",
        "MMM.LLLL....",
        ".MMMLLLL....",
        "..MMLLLL....",
        "....LLLLLL..",
        "...LLLLLLLL.",
        "...LLLLLLLL.",
        "...llllllll.",
    ],
    "bomb": [
        "........A...",
        ".......AXA..",
        "......LA....",
        ".....L......",
        "...NNNN.....",
        "..NNNNNN....",
        ".NNXNNNNN...",
        ".NXNNNNNN...",
        ".NNNNNNNN...",
        ".NNNNNNNN...",
        "..NNNNNN....",
        "...NNNN.....",
    ],
    "trap": [
        "S..........S",
        "SW........WS",
        "SSW.W..W.WSS",
        ".SSWSWWSWSS.",
        "..SSSSSSSS..",
        "...SSSSSS...",
        ".....SS.....",
        "..BBBBBBBB..",
    ],
    "caltrops": [
        "...W........",
        "..WSW...W...",
        ".SS.SS.WSW..",
        "......SS.SS.",
        "..W.........",
        ".WSW....W...",
        "SS.SS..WSW..",
        ".......S.S..",
    ],
    "hood": [
        "....DDDD....",
        "...DDDDDD...",
        "..DDDDDDDD..",
        "..DDddddDD..",
        ".DDdAddAdDD.",
        ".DDddddddDD.",
        ".DDDddddDDD.",
        "DDDDDddDDDDD",
        "DDDDDDDDDDDD",
        "DDDDDDDDDDDD",
    ],
    "quiver": [
        "..W.W.W.....",
        "..W.W.W.....",
        ".EEEEEEE....",
        ".LLLLLLL....",
        ".LLLLLLL....",
        ".LGLLLLL....",
        ".LLLLLLL....",
        ".LLLLLLL....",
        ".LLLLGLL....",
        ".LLLLLLL....",
        "..LLLLL.....",
    ],
    "hat": [
        ".......MM...",
        "......MMM...",
        ".....MMMm...",
        ".....MMM....",
        "....MMMM....",
        "....MAMM....",
        "...MMMMMM...",
        "...MMMMMM...",
        "GGGGGGGGGGGG",
        ".gggggggggg.",
    ],
}

# --------------------------------------------------------------------------- badges (<= 6x6)
BADGES = {
    "stun": (dict(T=("glow", THUNDER)), ["..T..", ".TTT.", "TTXTT", ".TTT.", "..T.."]),
    "slow": (dict(I=P.ICE), ["I...I", ".I.I.", "..I..", "I...I", ".I.I.", "..I.."]),
    "freeze": (dict(I=P.ICE), ["I.I.I", ".III.", "IIXII", ".III.", "I.I.I"]),
    "burn": (dict(F=P.FLAME), [".F..", ".FF.", "FFXF", "FXXF", ".FF."]),
    "bleed": (dict(R=P.BLOOD), [".R.", ".R.", "RRR", "RXR", "RRR"]),
    "poison": (dict(V=P.SLIME), [".V.", ".V.", "VVV", "VXV", "VVV"]),
    "blind": (dict(D=HOOD, A=("glow", P.HOLY_WHITE)), [".DDD.", "DAXAD", ".DDD."]),
    "weaken": (dict(R=P.POTION_RED), ["..R..", "..R..", "RRRRR", ".RRR.", "..R.."]),
    "vuln": (dict(S=P.STEEL, M=P.BLOOD), ["SS.SS", "SMmMS", "SM.MS", ".SmS.", "..S.."]),
    "pull": (dict(A=("glow", P.VOID_GLOW)), ["A...A", ".A.A.", "..X..", ".A.A.", "A...A"]),
    "launch": (dict(A=("glow", WIND)), ["..A..", ".AAA.", "AAAAA", "..A..", "..A.."]),
    "heal": (dict(H=P.HEAL_GREEN), ["..H..", "..H..", "HHHHH", "..H..", "..H.."]),
    "up": (dict(G=("glow", P.HOLY_GOLD)), ["..G..", ".GGG.", "GGGGG", "..G..", "..G.."]),
    "clock": (dict(G=P.GOLD), [".GGG.", "G.X.G", "G.XXG", "G...G", ".GGG."]),
    "skull": (dict(O=P.BONE, D=HOOD), [".OOO.", "OOOOO", "ODODO", ".OOO.", ".O.O."]),
    "taunt": (dict(R=P.POTION_RED), ["RR", "RR", "RR", "..", "RR"]),
    "speed": (dict(A=("glow", WIND)), ["A..A..", ".A..A.", "..A..A", ".A..A.", "A..A.."]),
    "star": (dict(G=("glow", P.HOLY_GOLD)), ["..G..", ".GGG.", "GGXGG", ".GGG.", ".G.G."]),
    "shield": (dict(S=P.SILVER, M=P.JOB_KNIGHT), ["SSSSS", "SMMMS", "SMMMS", ".SMS.", "..S.."]),
    "heart": (dict(R=P.POTION_RED), ["RR.RR", "RXRRR", "RRRRR", ".RRR.", "..R.."]),
    "crit": (dict(R=P.POTION_RED), ["..R..", ".R.R.", "RRXRR", ".R.R.", "..R.."]),
    "thorns": (dict(W=P.SILVER), ["W.W.W", ".WWW.", "WWXWW", ".WWW.", "W.W.W"]),
    "stamina": (dict(T=("glow", THUNDER)), ["..TT", ".TT.", "TTTT", ".TT.", "TT.."]),
}
COUNTS = {"x2": 2, "x3": 3, "x4": 4, "x5": 5, "x6": 6, "x7": 7}


def _mats(theme: str, job: str) -> dict:
    main, glow = THEMES[theme]
    if theme == "STEEL":
        main = skills.JOB_RAMP[job]
    return dict(
        M=main, A=("glow", glow), W=P.SILVER, X=("flat", hexc("ffffff")), G=P.GOLD, B=P.WOOD, L=P.LEATHER,
        S=P.STEEL, C=P.GLASS, E=P.LINEN, O=P.BONE, D=HOOD, N=BOMB, T=P.STONE, Z=P.HOLY_WHITE, R=P.BLOOD,
        K=skills.JOB_RAMP[job],
    )


def _glyph(rows, mats, offset=None) -> Canvas:
    h = len(rows)
    w = max(len(r) for r in rows)
    rows = [r.ljust(w, ".") for r in rows]
    if offset is None:
        offset = (2 + (12 - w) // 2, 2 + (12 - h) // 2)
    return render(rows, mats, local="", size=16, offset=offset, edge=1.2, grad=0.6, base=3.4)


# --------------------------------------------------------------------------- drawn layers
class Layer:
    """Additive glow layer over the 12x12 inside of the frame: intensity per pixel, coloured with a ramp."""

    def __init__(self):
        self.v = np.zeros((16, 16))

    def add(self, fn):
        for y in range(2, 14):
            for x in range(2, 14):
                self.v[y, x] = max(self.v[y, x], max(0.0, min(1.0, fn(x + 0.5, y + 0.5))))

    def onto(self, c: Canvas, ramp: Ramp, strength: float = 1.0) -> None:
        for y in range(2, 14):
            for x in range(2, 14):
                v = self.v[y, x] * strength
                if v < 0.22:
                    continue
                col = ramp[5] if v > 0.85 else ramp[4] if v > 0.6 else ramp[3] if v > 0.4 else ramp[2]
                alpha = 1.0 if v > 0.6 else 0.75 if v > 0.4 else 0.5
                c.put(x, y, mix(c.get(x, y), col, alpha))


def _seg(px, py, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    t = max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / max(1e-9, dx * dx + dy * dy)))
    return math.hypot(px - (x0 + t * dx), py - (y0 + t * dy)), t


def _ring(cx, cy, r, w):
    return lambda x, y: 1.0 - abs(math.hypot(x - cx, y - cy) - r) / w


def _arc(cx, cy, r, w, a0, a1):
    def f(x, y):
        ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
        span = (a1 - a0) % 360
        u = ((ang - a0) % 360) / max(1e-9, span)
        if u > 1.0:
            return 0.0
        taper = math.sin(u * math.pi) ** 0.5
        return (1.0 - abs(math.hypot(x - cx, y - cy) - r) / (w * taper + 1e-6)) * (0.6 + 0.4 * u)
    return f


def _line(x0, y0, x1, y1, w, fade=False):
    def f(x, y):
        d, t = _seg(x, y, x0, y0, x1, y1)
        v = 1.0 - d / w
        return v * (0.35 + 0.65 * t) if fade else v
    return f


def _rays(n, r0, r1, phase=0.0, w=0.9):
    def f(x, y):
        dx, dy = x - 8, y - 8
        d = math.hypot(dx, dy)
        if not r0 <= d <= r1:
            return 0.0
        ang = math.atan2(dy, dx) - phase
        step = 2 * math.pi / n
        off = (ang + step / 2) % step - step / 2
        return (1.0 - abs(off * d) / w) * (1.0 - (d - r0) / (r1 - r0) * 0.5)
    return f


def _glow(cx, cy, r):
    return lambda x, y: (1.0 - math.hypot(x - cx, y - cy) / r) * 0.95


FX = {}


def fx(name):
    def deco(fn):
        FX[name] = fn
        return fn
    return deco


@fx("arc")
def _fx_arc(L):
    L.add(_arc(5.0, 11.0, 7.0, 1.6, 270, 15))


@fx("arc2")
def _fx_arc2(L):
    L.add(_arc(11.0, 11.0, 7.0, 1.6, 165, 270))


@fx("x")
def _fx_x(L):
    L.add(_line(3, 3, 13, 13, 1.1, True))
    L.add(_line(13, 3, 3, 13, 1.1, True))


@fx("ring")
def _fx_ring(L):
    L.add(_ring(8, 8, 5.3, 0.9))


@fx("burst")
def _fx_burst(L):
    L.add(_rays(8, 2.0, 6.5, math.pi / 8))
    L.add(_glow(8, 8, 3.2))


@fx("nova")
def _fx_nova(L):
    L.add(_ring(8, 8, 5.4, 0.9))
    L.add(_rays(8, 1.5, 6.5, 0.0, 0.7))
    L.add(_glow(8, 8, 3.0))


@fx("glow")
def _fx_glow(L):
    L.add(_glow(8, 8, 6.5))


@fx("halo")
def _fx_halo(L):
    def f(x, y):
        return 1.0 - abs(math.hypot((x - 8) / 1.0, (y - 3.6) * 2.4) - 3.6) / 0.9
    L.add(f)


@fx("spiral")
def _fx_spiral(L):
    def f(x, y):
        dx, dy = x - 8, y - 8
        d = math.hypot(dx, dy)
        if d < 1.0 or d > 6.6:
            return 0.0
        ang = math.atan2(dy, dx)
        best = 9.0
        for k in range(-1, 4):
            target = 0.95 * (ang + 2 * math.pi * k)
            if target > 0:
                best = min(best, abs(d - target))
        return (1.0 - best / 0.8) * min(1.0, d / 3.0)
    L.add(f)


@fx("beam")
def _fx_beam(L):
    L.add(_line(2, 8, 14, 8, 1.3, True))
    L.add(_line(2, 8, 14, 8, 0.6))


@fx("beamd")
def _fx_beamd(L):
    L.add(_line(2.5, 13.5, 13.5, 2.5, 1.3, True))


@fx("rain")
def _fx_rain(L):
    for i, (x, y) in enumerate(((4, 3), (8, 2), (12, 4), (6, 7), (10, 8), (3, 10), (13, 10))):
        L.add(_line(x + 1.2, y - 1.6, x - 0.4, y + 1.6, 0.6, True))


@fx("hail")
def _fx_hail(L):
    for (x, y) in ((4, 4), (9, 3), (12, 7), (6, 9), (11, 12), (3, 12)):
        L.add(_line(x + 1.5, y - 2.0, x, y, 0.5, True))
        L.add(_glow(x, y, 1.4))


@fx("waves")
def _fx_waves(L):
    for r in (3.5, 6.0, 8.5):
        L.add(_arc(1.0, 8.0, r, 0.8, 300, 60))


@fx("zone")
def _fx_zone(L):
    L.add(lambda x, y: 1.0 - abs(math.hypot((x - 8) / 5.6, (y - 11) / 2.2) - 1.0) * 2.4)


@fx("pillar")
def _fx_pillar(L):
    L.add(lambda x, y: 1.0 - abs(x - 8) / 2.6)


@fx("smite")
def _fx_smite(L):
    L.add(lambda x, y: (1.0 - abs(x - 8) / 1.6) * (0.4 + 0.6 * y / 14))
    L.add(lambda x, y: 1.0 - abs(math.hypot((x - 8) / 4.5, (y - 12.5) / 1.6) - 1.0) * 2.0)


@fx("crack")
def _fx_crack(L):
    for seg in (((8, 13), (6, 10)), ((6, 10), (7, 8)), ((8, 13), (11, 11)), ((11, 11), (13, 11.5)),
                ((8, 13), (4, 12)), ((4, 12), (2.5, 12.5))):
        (x0, y0), (x1, y1) = seg
        L.add(_line(x0, y0, x1, y1, 0.7))


@fx("speed")
def _fx_speed(L):
    for y, x0, x1 in ((4, 2, 7), (7, 2, 9), (10, 2, 6), (12.5, 3, 8)):
        L.add(_line(x0, y, x1, y, 0.55, True))


@fx("orbit")
def _fx_orbit(L):
    for ang in (30, 150, 270):
        a = math.radians(ang)
        L.add(_glow(8 + 5 * math.cos(a), 8 + 5 * math.sin(a), 1.9))
    L.add(_ring(8, 8, 5.0, 0.4))


@fx("mist")
def _fx_mist(L):
    for (x, y, r) in ((4, 11, 3.0), (8, 12, 3.4), (12, 11, 3.0), (6, 4, 2.0), (11, 5, 2.2)):
        L.add(lambda px, py, x=x, y=y, r=r: (1.0 - math.hypot(px - x, (py - y) * 1.3) / r) * 0.7)


@fx("cloud")
def _fx_cloud(L):
    for (x, y, r) in ((5, 8, 3.2), (8.5, 6.5, 3.8), (11.5, 8.5, 3.0), (8, 10, 3.6)):
        L.add(lambda px, py, x=x, y=y, r=r: (1.0 - math.hypot(px - x, py - y) / r) * 1.1)


@fx("claws")
def _fx_claws(L):
    for dx in (-3.2, 0.0, 3.2):
        L.add(_line(10.5 + dx, 2.5, 5.5 + dx, 13.5, 0.8, True))


@fx("flamewall")
def _fx_flamewall(L):
    for x in (4, 8, 12):
        L.add(lambda px, py, x=x: (1.0 - abs(px - x) / (1.0 + (py - 3) * 0.22)) * (py - 3) / 9.0 * 1.4)


# --------------------------------------------------------------------------- drawn objects
def _sphere_obj(c: Canvas, cx, cy, r, ramp, glow=None):
    if glow is not None:
        for y in range(2, 14):
            for x in range(2, 14):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r < d <= r + 1.3:
                    c.put(x, y, mix(c.get(x, y), glow[4], 0.55))
    obj = Canvas(16)
    from px import sphere
    sphere(obj, cx, cy, r, ramp)
    obj.outline(None, auto=1.0)
    c.paste(obj)


def _projectile(c: Canvas, x0, y0, x1, y1, head="arrow"):
    """Arrow, knife or dart from (x0, y0) (tail) to (x1, y1) (tip), pixel accurate."""
    obj = Canvas(16)
    n = int(max(abs(x1 - x0), abs(y1 - y0)))
    pts = [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]
    shaft = {"arrow": P.WOOD[3], "knife": P.LEATHER[3], "dart": P.STEEL[2]}[head]
    for i, (x, y) in enumerate(pts):
        if head == "arrow" and i <= 1:
            obj.put(x, y, P.LINEN[4] if i == 0 else P.LINEN[3])
        elif i >= n - (1 if head == "arrow" else 3 if head == "knife" else 1):
            obj.put(x, y, P.SILVER[5] if i == n else P.SILVER[3])
        else:
            obj.put(x, y, shaft)
    if head == "arrow":
        ux, uy = (x1 - x0) / n, (y1 - y0) / n
        for side in (-1, 1):
            px_, py_ = round(x1 - ux * 1.4 - uy * side), round(y1 - uy * 1.4 + ux * side)
            obj.put(px_, py_, P.SILVER[2])
    obj.outline(None, auto=1.0)
    c.paste(obj)


def _drawn(name: str, c: Canvas, theme: str):
    main, glow = THEMES[theme]
    if name == "orb":
        _sphere_obj(c, 8, 8, 4.2, main, glow)
    elif name == "orb_small":
        _sphere_obj(c, 8, 8, 2.8, main, glow)
    elif name == "orbs3":
        for (x, y) in ((4.5, 11.5), (8, 8), (11.5, 4.5)):
            _sphere_obj(c, x, y, 1.9, main, glow)
    elif name == "meteor":
        L = Layer()
        L.add(_line(13.5, 2.5, 7, 9, 2.2, False))
        L.onto(c, glow)
        _sphere_obj(c, 6.5, 9.5, 3.4, P.DRAGON_DARK if theme in ("FIRE", "MAGMA") else main, None)
    elif name == "sun":
        L = Layer()
        L.add(_rays(12, 3.8, 6.4, 0.0, 0.8))
        L.onto(c, glow)
        _sphere_obj(c, 8, 8, 3.4, main, None)
    elif name == "moon":
        obj = Canvas(16)
        for y in range(16):
            for x in range(16):
                d0 = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                d1 = math.hypot(x + 0.5 - 10.4, y + 0.5 - 6.4)
                if d0 <= 5.2 and d1 > 4.4:
                    lit = (x - 8) * -0.5 + (y - 8) * -0.7
                    obj.put(x, y, main[4] if lit > 1.5 else main[3] if lit > -1.5 else main[2])
        obj.outline(None, auto=1.0)
        c.paste(obj)
    elif name == "portal":
        obj = Canvas(16)
        for y in range(16):
            for x in range(16):
                d = math.hypot((x + 0.5 - 8) / 4.6, (y + 0.5 - 8) / 5.6)
                if d <= 1.0:
                    obj.put(x, y, HOOD[0] if d < 0.55 else HOOD[1] if d < 0.75 else main[3] if d < 0.9 else glow[4])
        obj.outline(None, auto=1.0)
        c.paste(obj)
    elif name == "clock":
        obj = Canvas(16)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if d <= 5.4:
                    obj.put(x, y, P.GOLD[3] if d > 4.4 else main[1] if d > 3.6 else P.LINEN[4])
        for (x, y) in ((8, 5), (8, 6), (8, 7), (8, 8), (9, 8), (10, 8)):
            obj.put(x, y, HOOD[1])
        obj.put(8, 8, P.GOLD[4])
        obj.outline(None, auto=1.0)
        c.paste(obj)
    elif name == "rune":
        L = Layer()
        L.add(_ring(8, 8, 5.0, 0.7))
        for k in range(3):
            a0 = math.radians(-90 + k * 120)
            a1 = math.radians(-90 + (k + 1) * 120)
            L.add(_line(8 + 5 * math.cos(a0), 8 + 5 * math.sin(a0), 8 + 5 * math.cos(a1), 8 + 5 * math.sin(a1), 0.6))
        L.add(_glow(8, 8, 2.0))
        L.onto(c, glow, 1.15)
    elif name == "crescent":
        L = Layer()
        L.add(_arc(4.5, 8.0, 7.0, 2.2, 300, 60))
        L.onto(c, glow, 1.2)
        L2 = Layer()
        L2.add(_arc(4.5, 8.0, 7.2, 0.9, 305, 55))
        L2.onto(c, main, 1.0)
    elif name == "dart":
        _projectile(c, 3, 12, 12, 3, "dart")
    elif name == "arrow1":
        _projectile(c, 2, 13, 13, 2, "arrow")
    elif name == "arrow2":
        _projectile(c, 2, 11, 11, 2, "arrow")
        _projectile(c, 4, 13, 13, 4, "arrow")
    elif name == "arrows3fan":
        for (x1, y1) in ((13, 2), (13, 7), (8, 2)):
            _projectile(c, 2, 13, x1, y1, "arrow")
    elif name == "arrows5fan":
        for (x1, y1) in ((13, 2), (13, 6), (13, 10), (9, 2), (5, 2)):
            _projectile(c, 2, 13, x1, y1, "arrow")
    elif name == "arrows_rain":
        for (x, y) in ((4, 3), (8, 5), (12, 3)):
            _projectile(c, x + 2, y - 1, x - 1, y + 8, "arrow")
    elif name == "knives_rain":
        for (x, y) in ((4, 2), (8, 4), (12, 2)):
            _projectile(c, x + 1, y, x - 1, y + 8, "knife")
    elif name == "knives3":
        for (x1, y1) in ((13, 3), (13, 8), (8, 3)):
            _projectile(c, 3, 13, x1, y1, "knife")
    elif name == "knives7":
        for (x1, y1) in ((13, 2), (13, 5), (13, 8), (13, 11), (10, 2), (7, 2), (4, 2)):
            _projectile(c, 2, 13, x1, y1, "knife")
    else:
        raise KeyError(name)


DRAWN = {"orb", "orb_small", "orbs3", "meteor", "sun", "moon", "portal", "clock", "rune", "crescent", "dart",
         "arrow1", "arrow2", "arrows3fan", "arrows5fan", "arrows_rain", "knives3", "knives7", "knives_rain"}


# --------------------------------------------------------------------------- frames
def _frame(job: str, kind: str, theme: str) -> Canvas:
    c = skills.frame(job)
    if kind == "p":
        metal = skills.JOB_METAL[job]
        for y in range(2, 14):
            for x in range(2, 14):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if 5.6 <= d <= 6.4:
                    c.put(x, y, mix(c.get(x, y), metal[3], 0.85))
                elif d > 6.4:
                    c.put(x, y, mix(c.get(x, y), (0, 0, 0, 255), 0.35))
    elif kind == "u":
        # ultimates: a frame glowing in the theme colour with gold corners, and a halo inside
        glow = P.HOLY_WHITE if theme in ("HOLY", "LIGHT") else THEMES[theme][1]
        for y in range(16):
            for x in range(16):
                edge = min(x, y, 15 - x, 15 - y)
                if edge == 0:
                    c.put(x, y, P.GOLD[1])
                elif edge == 1:
                    c.put(x, y, glow[5] if x == 1 or y == 1 else glow[3])
        L = Layer()
        L.add(_glow(8, 8, 7.5))
        L.onto(c, glow, 0.55)
        for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            c.put(x, y, P.GOLD[5] if (x, y) == (1, 1) else P.GOLD[4])
    return c


# --------------------------------------------------------------------------- composition
def compose(sid: str, job: str, kind: str, obj: str, effects: str, badge: str, theme: str, flags: str = "") -> Canvas:
    if job == "priest" and theme == "HOLY":
        theme = "LIGHT"
    c = _frame(job, kind, theme)
    main, glow = THEMES[theme]
    if effects != "-":
        for name in effects.split("+"):
            L = Layer()
            FX[name](L)
            L.onto(c, glow if name not in ("zone", "crack") else main, 1.0)
    if obj != "-":
        if obj in DRAWN:
            _drawn(obj, c, theme)
        else:
            mats = _mats(theme, job)
            sym = _glyph(OBJECTS[obj], mats)
            if "flip" in flags:
                sym.a = sym.a[:, ::-1].copy()
            if "ghost" in flags:
                for shift, alpha in ((4, 0.3), (2, 0.5)):
                    for y in range(16):
                        for x in range(16):
                            sx = x + shift
                            if sym.a[y, x, 3] and 2 <= x - shift <= 13 and 2 <= y <= 13:
                                px_ = x - shift
                                c.put(px_, y, mix(c.get(px_, y), glow[4], alpha))
            m = sym.mask()
            sh = np.zeros_like(m)
            sh[1:, 1:] = m[:-1, :-1]
            for y, x in zip(*np.nonzero(sh & ~m)):
                if 2 <= x <= 13 and 2 <= y <= 13:
                    c.put(int(x), int(y), mix(c.get(int(x), int(y)), (0, 0, 0, 255), 0.45))
            for y in range(16):
                for x in range(16):
                    if sym.a[y, x, 3] and 1 <= x <= 14 and 1 <= y <= 14:
                        c.a[y, x] = sym.a[y, x]
    if badge != "-":
        if badge in COUNTS:
            n = COUNTS[badge]
            for i in range(n):
                x = 3 + i * 2 if n <= 5 else 2 + i * 2 - (1 if i >= 6 else 0)
                c.put(x - 1, 13, (20, 16, 24, 255))
                c.put(x, 13, glow[5])
        else:
            mats, rows = BADGES[badge]
            mats = dict(mats)
            mats.setdefault("X", ("flat", hexc("ffffff")))
            w = max(len(r) for r in rows)
            h = len(rows)
            b = render([r.ljust(w, ".") for r in rows], mats, local="", size=16, offset=(14 - w, 14 - h), edge=1.0,
                       grad=0.3, base=3.6)
            for y in range(16):
                for x in range(16):
                    if b.a[y, x, 3] and 1 <= x <= 14 and 1 <= y <= 14:
                        c.a[y, x] = b.a[y, x]
    return c


# --------------------------------------------------------------------------- recipes
# One line per node in tree order (branch by branch, tier 1..6, columns left to right).
# name kind object effects badge theme [flags]; "*" = the original hand-drawn icon of skills.py.
RECIPES = {
    "knight": """
guard_stance        a shield_spikes glow        -       STEEL
toughness           p heart         -           -       BLOOD
provoke             a sword         arc         taunt   STEEL
war_cry             *
bulwark             p chest         -           -       STEEL
shield_charge       a shield_small  speed       stun    STEEL
iron_wall           *
guardian_oath       p buckler       halo        -       HOLY
rampart             a tower         ring        -       EARTH
retaliation         a shield_spikes burst       -       BLOOD
second_wind         p skull         glow        heal    LIFE
challenge           a banner        nova        vuln    STEEL
fortress            u tower         glow        shield  EARTH
unbreakable         p gem           -           -       STEEL
protector           a shield        orbit       -       HOLY
aegis               u shield_cross  nova        -       HOLY
last_bastion        p tower         -           heart   BLOOD
shield_wall         a shield_small  waves       x3      STEEL
shield_bash         *
cleave              a greatsword    arc         -       STEEL
swordsmanship       p sword         -           -       STEEL
heavy_slam          a hammer        crack       stun    EARTH
rising_slash        a sword         arc2        launch  WIND
lance_thrust        a lance         speed       -       STEEL
armor_break         a shield_broken burst       -       STEEL
whirl_guard         a greatsword    spiral      shield  WIND
battle_hardened     p target        -           -       STEEL
earthshaker         a greatsword    crack       slow    EARTH
foresight_cut       a eye           arc         -       STEEL
spear_sweep         a spear         spiral      x3      WIND
titan_strike        u hammer        nova        stun    EARTH
relentless          p greatsword    glow        star    STEEL
impale              a spear         glow        bleed   BLOOD
grand_cross         u greatsword    x           vuln    HOLY
war_master          p crown         -           -       STEEL
dragon_lance        a spear         speed       launch  WIND
holy_strike         a sword         glow        heal    HOLY
faith               p cross         -           -       HOLY
consecrate          a rune          zone        -       HOLY
lay_on_hands        a hand          glow        heal    LIFE
divine_shield       a buckler       halo        heal    HOLY
judgment_hammer     a hammer        smite       stun    HOLY
aura_of_valor       a sword         ring        up      HOLY
holy_vigor          p heart         halo        -       HOLY
radiant_wave        a crescent      -           blind   HOLY
purify              a drop          halo        heal    HOLY
martyr              p cross         -           thorns  BLOOD
sacred_spear        a spear         beamd       vuln    HOLY
holy_judgment       *
blessed_armor       p chest         halo        -       HOLY
heavens_wrath       a hammer        rain        x3      HOLY
resurgence          a heart         pillar      heal    HOLY
crusader            p sword         -           star    HOLY
divine_lance        a lance         beam        launch  HOLY
""",
    "assassin": """
shadow_step         *
light_feet          p boot          -           -       WIND
vital_strike        a dagger        burst       skull   SHADOW
phantom_dash        a boot          speed       -       SHADOW ghost
shadow_sense        p eye           -           -       SHADOW
caltrops            a caltrops      -           slow    STEEL
smoke_bomb          *
night_veil          p moon          -           -       SHADOW
shadow_bind         a chains        glow        stun    SHADOW
mirage              a hood          mist        -       SHADOW ghost
predator            p dagger        -           speed   BLOOD
shadow_claw         a -             claws       blind   SHADOW
eclipse             u moon          nova        -       SHADOW
ghost_step          p feather       -           -       SHADOW
abyss_gate          a portal        -           pull    SHADOW
death_mark          u skull         nova        vuln    BLOOD
phantom_master      p hood          -           clock   SHADOW
void_rend           a dagger        beam        weaken  SHADOW
poison_dart         a dart          beamd       poison  VENOM
toxicology          p flask         -           poison  VENOM
rupture             a dagger        arc         bleed   BLOOD
venom_blade         *
venom_coat          a dagger        glow        poison  VENOM flip
hemorrhage          a drop          arc         bleed   BLOOD
toxic_cloud         a -             cloud       poison  VENOM
virulence           p drop          -           weaken  VENOM
serpent_fang        a snake         -           poison  VENOM
plague_bomb         a flask         burst       weaken  VENOM
bloodthirst         p fangs         -           heal    BLOOD
viper_dance         a snake         spiral      x5      VENOM
pandemic            a skull         cloud       poison  VENOM
blood_frenzy        p drop          burst       -       BLOOD
crimson_rain        u knives_rain   rain        bleed   BLOOD
hydra_venom         u snake         nova        x3      VENOM
venom_lord          p crown         -           poison  VENOM
exsanguinate        a drop          spiral      heal    BLOOD
quick_slash         a dagger        arc         x2      WIND
twin_mastery        p daggers       -           -       STEEL
throwing_knives     a knives3       -           -       STEEL
cross_cut           a -             x           x2      STEEL
keen_edge           p dagger        -           crit    STEEL flip
fan_of_knives       a knives7       -           -       STEEL
assassinate         a skull         arc         -       BLOOD
adrenaline          p bolt          -           -       BLOOD
blade_flurry        a dagger        arc+arc2    x6      WIND
heart_piercer       a heart         beamd       bleed   BLOOD
killing_spree       p skull         burst       -       BLOOD
dancing_blades      a dagger        spiral      x5      WIND flip
thousand_blades     *
lethal_precision    p target        -           -       BLOOD
tempest_edge        a dagger        speed+arc   x2      WIND
phantom_assault     a daggers       burst       skull   SHADOW ghost
blade_saint         p daggers       glow        star    STEEL
shadow_requiem      a note          pillar      stun    SHADOW
""",
    "mage": """
fireball            *
pyromancy           p staff         -           -       FIRE
flame_burst         a flame         beam        burn    FIRE
explosion           a orb           burst       burn    FIRE
kindling            p flame         -           -       FIRE
fire_wall           a -             flamewall   burn    FIRE
magma_orb           a orb           glow        slow    MAGMA
heat_haze           p sun           -           crit    FIRE
flame_pillar        a flame         pillar      launch  FIRE
phoenix_bolt        a wings         glow        heal    FIRE
combustion          p flame         burst       star    FIRE
inferno_ring        a -             nova        burn    FIRE
meteor              *
ember_heart         p heart         glow        -       FIRE
dragon_breath       a fangs         beam        burn    FIRE
armageddon          u meteor        rain        -       FIRE
flame_lord          p crown         -           burn    FIRE
sunfire             a sun           pillar      burn    FIRE
frost_bolt          a orb_small     -           slow    FROST
cryomancy           p staff         -           -       FROST
frost_nova          a -             nova        freeze  FROST
ice_lance           *
permafrost          p crystal       -           slow    FROST
ice_wall            a shield        glow        -       FROST
glacial_spike       a crystal       pillar      launch  FROST
cold_blood          p drop          -           crit    FROST
blizzard            a -             rain+cloud  slow    FROST
shatter             a crystal       burst       skull   FROST
frozen_heart        p heart         glow        -       FROST
ice_prison          a chains        glow        stun    FROST
absolute_zero       u snowflake     nova+glow   stun    FROST
winter_soul         p snowflake     -           clock   FROST
hail_storm          a -             hail+cloud  slow    FROST
frost_giant         a hand          crack       freeze  FROST
ice_queen           p crown         -           freeze  FROST
eternal_winter      u snowflake     spiral      freeze  FROST
spark               a bolt          -           -       THUNDER
arcane_mind         p gem           -           -       ARCANE
mana_shield         a buckler       glow        -       ARCANE
chain_lightning     a bolt          orbit       stun    THUNDER
focus               p eye           -           -       ARCANE
blink               a star          speed       -       ARCANE
thunderstorm        *
arcane_flow         p flask         -           -       ARCANE
arcane_missiles     a orbs3         -           x5      ARCANE
thunder_spear       a spear         beam        stun    THUNDER
overload            p bolt          glow        star    THUNDER
gravity_well        a orb           spiral      pull    ARCANE
thunder_god         u bolt          nova        stun    THUNDER
mana_surge          p staff         -           -       THUNDER
time_warp           a hourglass     spiral      -       ARCANE
ball_lightning      a orb           orbit       stun    THUNDER
archmage            p hat           -           -       ARCANE
singularity         u portal        nova        pull    ARCANE
""",
    "ranger": """
rapid_shot          *
eagle_eye           p eye           -           -       WIND
aimed_shot          a arrow1        ring        -       WIND
piercing_shot       a arrow1        beam        -       WIND
steady_aim          p target        -           -       WIND
crippling_shot      a arrow1        -           slow    EARTH
headshot            a target        burst       skull   BLOOD
hawk_focus          p feather       -           crit    WIND
mark_prey           a eye           ring        vuln    BLOOD
power_shot          a bow           glow        -       WIND
sniper              p bow           -           star    WIND
explosive_arrow     a arrow1        burst       burn    FIRE
deadeye             u target        nova        crit    WIND
wind_reader         p boot          spiral      -       WIND
thunder_arrow       a arrow1        glow        stun    THUNDER
star_piercer        u star          beam        -       HOLY
marksman_creed      p crown         -           crit    WIND
chain_volley        a arrow2        speed       x4      WIND
hunter_slash        a dagger        arc         x2      STEEL
survivalist         p heart         -           -       LIFE
snare_trap          a trap          -           stun    EARTH
beast_call          a paw           ring        up      EARTH
camouflage          p hood          mist        -       LIFE
venom_trap          a trap          mist        poison  VENOM
leap_shot           *
tracker             p boot          -           -       EARTH
wild_claws          a -             claws       bleed   BLOOD
hawk_dive           a wings         smite       -       WIND
natures_blessing    p flower        -           heal    LIFE
explosive_trap      a trap          burst       launch  FIRE
predator_pounce     a paw           crack       stun    EARTH
apex                p fangs         -           up      EARTH
storm_trap          a bolt          zone        stun    THUNDER
beast_lord          u paw           nova+glow   heal    EARTH
wilderness          p paw           -           heart   EARTH
primal_fury         a fangs         spiral      heal    BLOOD
double_shot         a arrow2        -           -       WIND
quiver_mastery      p quiver        -           -       WIND
scatter_shot        a arrows5fan    burst       -       STEEL
spread_shot         *
rapid_reload        p hourglass     -           -       WIND
fire_volley         a arrows3fan    glow        burn    FIRE
arrow_storm         a arrow2        spiral      x6      WIND
volley_expert       p bow           -           up      STEEL
frost_volley        a arrows3fan    glow        slow    FROST
cluster_bomb        a bomb          burst       x3      FIRE
hail_of_arrows      p quiver        glow        -       STEEL
rain_of_thorns      a arrows_rain   -           bleed   BLOOD
arrow_rain          *
barrage_master      p arrows3fan    -           star    WIND
gale_volley         a arrows5fan    spiral      speed   WIND
apocalypse_rain     u arrows_rain   nova        stun    WIND
endless_quiver      p quiver        -           clock   WIND
tempest_arrows      a arrow2        glow        stun    THUNDER
""",
    "priest": """
heal                *
devotion            p hand          -           -       LIFE
renew               a flower        ring        -       LIFE
greater_heal        a cross         pillar      heal    LIFE
mercy               p heart         -           heal    LIFE
cleansing_light     a drop          glow        -       HOLY
healing_circle      a rune          zone        heal    LIFE
benediction         p hourglass     -           -       HOLY
prayer_of_mending   a hand          halo        shield  HOLY
lifebloom           a flower        burst       heal    LIFE
serenity            p feather       -           -       HOLY
spirit_link         a chains        glow        heal    BLOOD
sanctuary           *
grace               p cross         halo        -       LIFE
guardian_angel      a wings         halo        shield  HOLY
miracle             u hand          nova        heal    HOLY
saintly             p sun           -           -       HOLY
rebirth_hymn        a note          zone        heal    LIFE
protect             a buckler       glow        -       HOLY
piety               p helmet        -           -       HOLY
haste_prayer        a boot          ring        up      WIND
holy_shield         a shield_cross  glow        -       HOLY
inner_light         p star          -           -       HOLY
battle_hymn         a note          burst       up      HOLY
blessing            *
sacred_vow          p buckler       -           heart   HOLY
divine_favor        a star          ring        crit    HOLY
sanctified_ground   a rune          zone        shield  HOLY
aegis_of_faith      p shield_cross  -           heart   HOLY
dispel_evil         a skull         nova        weaken  HOLY
angelic_wings       a wings         speed       up      WIND
conviction          p banner        -           clock   HOLY
holy_barrier        u buckler       nova        shield  HOLY
divine_intervention a hand          pillar      shield  HOLY
high_priest         p crown         -           -       HOLY
ascension           u wings         nova        up      HOLY
smite               a cross         smite       -       HOLY
zeal                p staff         -           -       HOLY
consecrated_blade   a sword         arc         heal    HOLY
holy_light          *
righteousness       p target        -           -       HOLY
radiant_burst       a sun           burst       blind   HOLY
light_ray           a orb_small     beam        x6      HOLY
wrath               p bolt          -           star    HOLY
holy_lance          a lance         glow        vuln    HOLY
chains_of_light     a chains        ring        stun    HOLY
purgatory           p flame         -           burn    HOLY
penance             a orbs3         -           heal    HOLY
judgment_day        u cross         rain+nova   stun    HOLY
divine_power        p sun           -           crit    HOLY
purifying_flame     a flame         zone        burn    HOLY
seraph_wrath        a wings         smite       blind   HOLY
avatar              p hand          -           star    HOLY
nova_of_light       u star          nova        blind   HOLY
""",
    "spellblade": """
arcane_edge         *
blade_attunement    p gem           -           up      ARCANE
mana_slash          a sword         arc         x2      ARCANE
rune_strike         a rune          burst       vuln    ARCANE
mana_conduit        p staff         -           -       ARCANE
spell_parry         a buckler       ring        thorns  ARCANE
mana_wave           *
arcane_knowledge    p target        -           -       ARCANE
arcane_burst        a orb           nova        slow    ARCANE
runic_cascade       a rune          arc         x4      ARCANE
spellsword          p sword         glow        star    ARCANE
mana_drain          a hand          spiral      stamina ARCANE
crescent_moon       a crescent      glow        -       ARCANE
mana_overflow       p drop          -           -       ARCANE
arcane_storm        u rune          spiral+nova stun    ARCANE
excalibur           u greatsword    pillar      -       HOLY
sword_sage          p greatsword    glow        -       ARCANE
mana_blade_dance    a sword         spiral      x6      ARCANE flip
flame_strike        a sword         arc         burn    FIRE
elemental_affinity  p flame         glow        up      FIRE
frost_strike        a sword         arc         slow    FROST flip
flame_blade         *
element_surge       p star          -           crit    ARCANE
frost_blade         a greatsword    glow        freeze  FROST
inferno_slash       a greatsword    arc         burn    FIRE
conductor           p bolt          -           stun    THUNDER
glacier_edge        a crystal       beam        freeze  FROST
thunder_blade       a greatsword    glow        stun    THUNDER
elemental_mastery   p gem           glow        star    ARCANE
frozen_tomb         a crystal       nova        stun    FROST
lightning_slash     a sword         speed       stun    THUNDER flip
storm_heart         p heart         -           clock   THUNDER
elemental_fury      u gem           nova+glow   burn    ARCANE
raijin              a bolt          pillar      stun    THUNDER
avatar_of_elements  p crown         -           freeze  ARCANE
meteor_blade        a meteor        smite       burn    FIRE
warp_strike         a sword         speed       -       ARCANE ghost
spatial_sense       p feather       -           -       ARCANE
gravity_slash       a sword         spiral      pull    ARCANE
phase_shift         a hood          speed       -       SHADOW ghost flip
time_sense          p clock         -           -       ARCANE
rift_blade          a crescent      -           -       SHADOW
echo_slash          a greatsword    arc+arc2    x2      ARCANE ghost
void_walker         p hood          -           -       SHADOW
dimension_gate      a portal        spiral      pull    SHADOW
blink_assault       a star          speed       x3      ARCANE
chronomancy         p hourglass     -           star    ARCANE
time_stop           a clock         nova        stun    ARCANE
dimension_slash     *
eternity            p hourglass     glow        -       SHADOW
rewind              a clock         spiral      heal    ARCANE
world_cutter        u greatsword    beam        vuln    SHADOW
dimensional_lord    p crown         -           clock   SHADOW
collapse            u portal        nova        stun    SHADOW
""",
}


def nodes(job: str):
    """(skill id, kind or '*', object, effects, badge, theme, flags) of the job's 54 nodes in tree order."""
    out = []
    for line in RECIPES[job].strip().splitlines():
        parts = line.split()
        sid = f"{job}_{parts[0]}"
        if parts[1] == "*":
            out.append((sid, "*", None, None, None, None, ""))
        else:
            kind, obj, effects, badge, theme = parts[1:6]
            out.append((sid, kind, obj, effects, badge, theme, " ".join(parts[6:])))
    return out


def icon(sid: str) -> Canvas:
    job = sid.split("_", 1)[0]
    for node in nodes(job):
        if node[0] == sid:
            if node[1] == "*":
                return skills.skill_icon(sid)
            return compose(sid, job, *node[1:])
    raise KeyError(sid)


def new_skills() -> list[str]:
    return [n[0] for job in JOBS for n in nodes(job) if n[1] != "*"]


def check() -> list[str]:
    bad = []
    seen = {}
    for job in JOBS:
        ns = nodes(job)
        if len(ns) != 54:
            bad.append(f"{job}: {len(ns)} nodes")
        for n in ns:
            if n[1] == "*" and n[0] not in skills.ALL_SKILLS:
                bad.append(f"{n[0]}: no original icon")
            if n[1] != "*":
                for e in (n[3] or "-").split("+"):
                    if e != "-" and e not in FX:
                        bad.append(f"{n[0]}: unknown effect {e}")
                if n[2] != "-" and n[2] not in OBJECTS and n[2] not in DRAWN:
                    bad.append(f"{n[0]}: unknown object {n[2]}")
                if n[4] != "-" and n[4] not in BADGES and n[4] not in COUNTS:
                    bad.append(f"{n[0]}: unknown badge {n[4]}")
                if n[5] not in THEMES:
                    bad.append(f"{n[0]}: unknown theme {n[5]}")
                key = (n[1],) + tuple(n[2:])
                if key in seen:
                    bad.append(f"{n[0]}: same recipe as {seen[key]}")
                seen[key] = n[0]
    return bad


# --------------------------------------------------------------------------- preview
BRANCHES = {
    "knight": ["Guardian", "Weapon Arts", "Paladin"], "assassin": ["Shadow", "Venom", "Blades"],
    "mage": ["Fire", "Frost", "Storm & Arcane"], "ranger": ["Marksmanship", "Hunting", "Barrage"],
    "priest": ["Healing", "Protection", "Judgment"], "spellblade": ["Arcane Blade", "Elements", "Dimension"],
}
TIER_LEVELS = [1, 8, 18, 24, 30, 40]


def preview(path: str, icons: dict) -> None:
    from PIL import Image, ImageDraw
    import preview as PV

    import effects

    scale, pitch, gap = 3, 58, 22
    panel_w = 40 + 3 * (3 * pitch) + 2 * gap + 10
    panel_h = 70 + 6 * pitch + 10
    W = 2 * panel_w + 30
    H = 3 * panel_h + 60 + 120
    im = Image.new("RGBA", (W, H), PV.BG)
    d = ImageDraw.Draw(im)
    d.text((12, 10), "Skill trees - 54 nodes per job (x3). Columns = branches, rows = tiers (character level). "
                     "Gold frame = ultimate, ring = passive, * = original icon", fill=PV.TEXT, font=PV._font(16))
    for j, job in enumerate(JOBS):
        ox = 10 + (j % 2) * (panel_w + 10)
        oy = 40 + (j // 2) * panel_h
        d.rectangle([ox, oy, ox + panel_w - 4, oy + panel_h - 8], outline=(90, 90, 110, 255))
        d.text((ox + 8, oy + 6), job.upper(), fill=PV.TEXT, font=PV._font(16))
        for b, name in enumerate(BRANCHES[job]):
            bx = ox + 40 + b * (3 * pitch + gap)
            d.text((bx + 4, oy + 30), name, fill=PV.SUB, font=PV._font(13))
            d.line([(bx, oy + 48), (bx + 3 * pitch - 8, oy + 48)], fill=(110, 100, 70, 255))
        for t, lv in enumerate(TIER_LEVELS):
            d.text((ox + 6, oy + 60 + t * pitch + 16), f"Lv{lv}", fill=PV.SUB, font=PV._font(11))
        for i, n in enumerate(nodes(job)):
            b, t, col = i // 18, (i % 18) // 3, i % 3
            x = ox + 40 + b * (3 * pitch + gap) + col * pitch
            y = oy + 60 + t * pitch
            img = icons[n[0]].image().resize((16 * scale, 16 * scale), Image.NEAREST)
            im.alpha_composite(img, (x, y))
            if n[1] == "*":
                d.text((x + 16 * scale - 6, y - 2), "*", fill=(255, 220, 120, 255), font=PV._font(12))
    ey = 40 + 3 * panel_h + 4
    d.text((12, ey), "Status effect icons 18x18 (x4)", fill=PV.TEXT, font=PV._font(16))
    for i, eid in enumerate(effects.EFFECTS):
        x = 12 + i * 83
        im.alpha_composite(effects.effect(eid).image().resize((72, 72), Image.NEAREST), (x, ey + 24))
        d.text((x, ey + 98), eid.replace("_weapon", " wpn"), fill=PV.SUB, font=PV._font(10))
    im.save(path, optimize=True)


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    problems = check()
    for p in problems:
        print("CHECK FAILED:", p)
    if problems:
        return 1
    root = os.path.abspath(os.path.join(HERE, "..", ".."))
    out = os.path.join(root, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "skill")
    icons = {}
    pixels = {}
    for job in JOBS:
        for n in nodes(job):
            c = icon(n[0])
            icons[n[0]] = c
            if n[1] != "*":
                c.save(os.path.join(out, f"{n[0]}.png"))
                key = c.a.tobytes()
                if key in pixels:
                    print("CHECK FAILED: identical icons", n[0], pixels[key])
                    return 1
                pixels[key] = n[0]
    print(f"wrote {len(pixels)} skill tree icons")
    if not args.no_preview:
        path = os.path.join(HERE, "skill_tree_preview.png")
        preview(path, icons)
        print("preview:", os.path.relpath(path, root))
    return 0


if __name__ == "__main__":
    sys.exit(main())
