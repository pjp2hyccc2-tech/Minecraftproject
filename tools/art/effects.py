"""Particles (8x8 / 16x16 animation frames) and the mob effect icons (18x18)."""
from __future__ import annotations

import math

import numpy as np

import palette as P
from icon import render
from px import Canvas, Ramp, hexc, mix, with_alpha

OUT = hexc("18161e")
THUNDER = Ramp("2a2406", "7a6a10", "c8b020", "f0dc3a", "fff27a", "ffffe0")


# =========================================================================== particles
def _canvas_from_fn(n, fn):
    c = Canvas(n)
    for y in range(n):
        for x in range(n):
            col = fn(x, y)
            if col is not None:
                c.put(x, y, col)
    return c


def slash(frame: int) -> Canvas:
    """White crescent arc that sweeps in, then thins out."""
    n = 16
    cx, cy = 7.5, 11.0
    r_out = [6.6, 7.0, 7.4, 7.6][frame]
    thick = [2.4, 2.6, 1.8, 1.0][frame]
    a0, a1 = [(195, 275), (190, 300), (185, 330), (200, 340)][frame]
    cols = [P.HOLY_WHITE[5], hexc("eaf2ff"), hexc("c8d6f0"), hexc("98a8c8")]

    def f(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        d = math.hypot(dx, dy)
        ang = math.degrees(math.atan2(dy, dx)) % 360
        if not (a0 <= ang <= a1):
            return None
        u = (ang - a0) / (a1 - a0)
        th = thick * math.sin(u * math.pi) ** 0.6
        if r_out - th <= d <= r_out:
            inner = d > r_out - th * 0.45
            return cols[0] if inner and frame < 2 else cols[min(3, frame + (0 if inner else 1))]
        return None
    return _canvas_from_fn(n, f)


def rune(frame: int) -> Canvas:
    """Glowing magic circle glyph: ring, inner square and tick marks; rotates and fades."""
    n = 16
    c = Canvas(n)
    glow = [P.CYAN_RUNE[5], P.CYAN_RUNE[4], P.CYAN_RUNE[4], P.CYAN_RUNE[3]][frame]
    dim = [P.CYAN_RUNE[3], P.CYAN_RUNE[3], P.CYAN_RUNE[2], P.CYAN_RUNE[2]][frame]
    rot = frame * math.pi / 8
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 6.2 <= d <= 7.3:
                c.put(x, y, glow)
            elif frame < 3 and 3.6 <= d <= 4.4:
                c.put(x, y, dim)
    # rotated square (two diagonal lines pairs)
    for k in range(4):
        a = rot + k * math.pi / 2
        x0, y0 = 8 + 5.6 * math.cos(a), 8 + 5.6 * math.sin(a)
        x1, y1 = 8 + 5.6 * math.cos(a + math.pi / 2), 8 + 5.6 * math.sin(a + math.pi / 2)
        steps = 12
        for i in range(steps + 1):
            u = i / steps
            c.put(int(x0 + (x1 - x0) * u), int(y0 + (y1 - y0) * u), dim if frame else glow)
    # tick glyphs on the ring
    for k in range(8):
        a = rot * 2 + k * math.pi / 4
        c.put(int(8 + 7.0 * math.cos(a)), int(8 + 7.0 * math.sin(a)), hexc("ffffff") if frame < 2 else glow)
    c.put(7, 7, glow)
    c.put(8, 8, glow)
    c.put(7, 8, glow)
    c.put(8, 7, glow)
    return c


def holy(frame: int) -> Canvas:
    """Golden four-point sparkle: grows, flashes, shrinks."""
    n = 8
    r = [1, 2, 3, 2][frame]
    c = Canvas(n)
    core = P.HOLY_GOLD[5] if frame in (1, 2) else P.HOLY_GOLD[4]
    for k in range(1, r + 1):
        col = P.HOLY_GOLD[4] if k < r else P.HOLY_GOLD[3]
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            c.put(3 + dx, 3 + dy, col)
    if frame == 2:
        for dx, dy in ((1, 1), (-1, -1), (1, -1), (-1, 1)):
            c.put(3 + dx, 3 + dy, P.HOLY_GOLD[2])
    c.put(3, 3, core)
    return c


def frost(frame: int) -> Canvas:
    """Small ice shard tumbling; last frame breaks apart."""
    shapes = [
        ["........", "......I.", ".....IW.", "....IWI.", "...IWI..", "..IWI...", "..II....", "........"],
        ["........", "........", "...II...", "..IWWI..", "..IWWI..", "...II...", "........", "........"],
        ["........", ".I......", ".WI.....", ".IWI....", "..IWI...", "...IWI..", "....II..", "........"],
    ]
    rows = shapes[frame]
    c = Canvas(8)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == "I":
                c.put(x, y, P.ICE[3])
            elif ch == "W":
                c.put(x, y, P.ICE[5])
    c.outline(P.ICE[1])
    return c


def ember(frame: int) -> Canvas:
    """Fire spark: bright yellow core cooling to red as it shrinks."""
    c = Canvas(8)
    sizes = [2.6, 2.1, 1.6, 1.0]
    ramp = [P.FLAME[5], P.FLAME[4], P.FLAME[3], P.FLAME[2]]
    r = sizes[frame]
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
            if d <= r:
                k = min(3, frame + (1 if d > r * 0.55 else 0))
                c.put(x, y, ramp[k])
    if frame < 2:
        c.put(4, 1 + frame, ramp[min(3, frame + 1)])
    return c


def shadow(frame: int) -> Canvas:
    """Dark wisp: a curling smoky blob that thins and drifts up."""
    n = 16
    c = Canvas(n)
    rad = [5.0, 5.4, 4.6, 3.4][frame]
    lift = [0, 1, 2, 3][frame]
    for y in range(n):
        for x in range(n):
            cx, cy = 8 + 1.2 * math.sin((y + frame) * 0.7), 9 - lift
            dx, dy = (x + 0.5 - cx), (y + 0.5 - cy) * 0.8
            d = math.hypot(dx, dy)
            wob = 0.8 * math.sin(math.atan2(dy, dx) * 3 + frame)
            if d <= rad + wob:
                edge = d > rad + wob - 1.4
                hole = frame >= 2 and ((x * 7 + y * 3 + frame) % 5 == 0)
                if hole:
                    continue
                c.put(x, y, P.SHADOW[3] if edge else P.SHADOW[1])
    # wisp tail
    for i in range(4 - frame):
        c.put(8 + (i % 2), 3 - lift + i, P.SHADOW[2])
    # faint violet glint
    if frame < 2:
        c.put(7, 8 - lift, P.VOID[2])
    return c


def heal(frame: int) -> Canvas:
    """Green plus: pops in small, glows full size, then fades to pale."""
    shapes = [
        ["........", "........", "...GG...", "..GHGG..", "..GGGG..", "...GG...", "........", "........"],
        ["........", "...GG...", "...HG...", ".GHHGGG.", ".GGGGGG.", "...GG...", "...GG...", "........"],
        ["........", "...GG...", "...GG...", ".GGGGGG.", ".GGGGGG.", "...GG...", "...GG...", "........"],
    ]
    body = [P.HEAL_GREEN[3], P.HEAL_GREEN[4], P.HEAL_GREEN[5]][frame]
    hi = [P.HEAL_GREEN[5], P.HEAL_GREEN[5], P.HEAL_GREEN[5]][frame]
    c = Canvas(8)
    for y, r in enumerate(shapes[frame]):
        for x, ch in enumerate(r):
            if ch == "G":
                c.put(x, y, body)
            elif ch == "H":
                c.put(x, y, hi)
    c.outline(P.HEAL_GREEN[1] if frame < 2 else P.HEAL_GREEN[3])
    return c


def spark(frame: int) -> Canvas:
    """Impact spark: a bright burst whose rays fly outward and break up."""
    n = 16
    c = Canvas(n)
    core = [3, 2, 1, 0][frame]
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= core:
                c.put(x, y, hexc("ffffff") if d < core * 0.6 else P.FLAME[5])
    inner = [2, 3, 5, 6][frame]
    outer = [5, 7, 8, 8][frame]
    for k in range(8):
        a = k * math.pi / 4 + (math.pi / 8 if k % 2 else 0)
        ln_out = outer if k % 2 == 0 else outer - 2
        for rr in np.arange(inner, ln_out, 0.5):
            x, y = int(8 + rr * math.cos(a)), int(8 + rr * math.sin(a))
            if frame == 3 and int(rr * 2) % 3 == 0:
                continue
            col = P.FLAME[5] if rr < (inner + ln_out) / 2 else P.FLAME[4]
            if frame >= 2:
                col = P.FLAME[4] if rr < (inner + ln_out) / 2 else P.FLAME[3]
            c.put(x, y, col)
    return c


PARTICLES = {
    "slash": (slash, 4),
    "rune": (rune, 4),
    "holy": (holy, 4),
    "frost": (frost, 3),
    "ember": (ember, 4),
    "shadow": (shadow, 4),
    "heal": (heal, 3),
    "spark": (spark, 4),
}


# =========================================================================== mob effects
EFFECTS = {
    "guard_up": (dict(S=P.STEEL, G=P.GOLD, W=P.SILVER), [
        "..................",
        "..WWWWWWWWWWWWWW..",
        "..WSSSSSGGSSSSSW..",
        "..WSSSSGGGGSSSSW..",
        "..WSSSGGGGGGSSSW..",
        "..WSSGGGGGGGGSSW..",
        "..WSSSSSGGSSSSSW..",
        "..WSSSSSGGSSSSSW..",
        "..WSSSSSGGSSSSSW..",
        "...WSSSSGGSSSSW...",
        "...WSSSSGGSSSSW...",
        "....WSSSSSSSSW....",
        ".....WSSSSSSW.....",
        "......WSSSSW......",
        ".......WSSW.......",
        "........WW........",
        "..................",
        "..................",
    ]),
    "haste": (dict(B=P.LEATHER, W=P.HOLY_WHITE, Y=("glow", P.HOLY_GOLD)), [
        "..................",
        "............W.....",
        "...........WW.....",
        "..........WWW..W..",
        ".....BBB.WWW..WW..",
        ".....BBBWWW..WWW..",
        ".....BBBWW..WWW...",
        ".....BBBBWWWWW....",
        "YY...BBBBBWWW.....",
        ".....BBBBBBB......",
        "YYY.BBBBBBBB......",
        "...BBBBBBBBB......",
        ".YYBBBBBBBBB......",
        "...bbbbbbbbb......",
        "..................",
        "..................",
        "..................",
        "..................",
    ]),
    "frozen": (dict(I=P.ICE, W=("flat", hexc("f0fdff"))), [
        "..................",
        "........II........",
        "......I.II.I......",
        ".......IWWI.......",
        "..I.....II.....I..",
        "...II...II...II...",
        "..I.II..II..II.I..",
        "......IIWWII......",
        ".IIIIIIWWWWIIIIII.",
        ".IIIIIIWWWWIIIIII.",
        "......IIWWII......",
        "..I.II..II..II.I..",
        "...II...II...II...",
        "..I.....II.....I..",
        ".......IWWI.......",
        "......I.II.I......",
        "........II........",
        "..................",
    ]),
    "cursed": (dict(P=P.Ramp("1a0a2a", "4a2a6a", "6e4a92", "9a78ba", "c6aee0", "f0e6ff"),
                    D=("flat", hexc("12081c")), G=("glow", P.VOID_GLOW), V=P.VOID), [
        "..................",
        ".....PPPPPPPP.....",
        "....PPPPPPPPPP....",
        "...PPPPPPPPPPPP...",
        "...PPPPPPPPPPPP...",
        "...PPDDDPPDDDPP...",
        "...PPDGDPPDGDPP...",
        "...PPDDDPPDDDPP...",
        "...PPPPPPPPPPPP...",
        "....PPPPDDPPPP....",
        ".....PPPPPPPP.....",
        ".....PDPDPDPP.....",
        ".....PPPPPPPP.....",
        "..V...PPPPPP...V..",
        ".VV............VV.",
        "..VV..........VV..",
        "...VVV......VVV...",
        "..................",
    ]),
    # ---- skill tree effects (stun, poison, weaken, vulnerability, buffs of the new skills)
    "stunned": (dict(T=("glow", THUNDER), O=P.HOLY_WHITE, X=("flat", hexc("ffffff"))), [
        "..................",
        "..................",
        "........T.........",
        ".......TTT........",
        "......TTXTT.......",
        ".......TTT........",
        "........T.........",
        "..................",
        "..TOOO......OOOT..",
        ".TTT..........TTT.",
        "TTXTT........TTXTT",
        ".TTT..........TTT.",
        ".OTO..........OTO.",
        "...OOOOOOOOOOOO...",
        ".......OOOO.......",
        "..................",
        "..................",
        "..................",
    ]),
    "poisoned": (dict(V=P.SLIME, D=P.JOB_RANGER, X=("flat", hexc("e2ffd8")), B=P.POTION_GREEN), [
        "..................",
        "..................",
        "........VV....B...",
        ".......VVVV..BBB..",
        ".......VVVV...B...",
        "...B..VVVVVV......",
        "......VVVVVV......",
        ".....VVVVVVVV.....",
        "....VVVVVVVXVV....",
        "....VVXVVVVVVV....",
        "....VVVDDDVVVV....",
        "...VVVVDVDVVVVV...",
        "...VVVVDDDVVVVV...",
        "....VVVVDVVVVV....",
        "....VVVVVVVVVV....",
        ".....VVVVVVVV.....",
        "......VVVVVV......",
        "........VV........",
    ]),
    "weakened": (dict(W=P.SILVER, G=P.GOLD, L=P.LEATHER, R=P.POTION_RED), [
        "..................",
        "..................",
        "....W.............",
        "....W.............",
        "....W.............",
        "....W.......RR....",
        "....W.......RR....",
        "............RR....",
        "....W.......RR....",
        "....W.....RRRRRR..",
        "....W......RRRR...",
        "....W.......RR....",
        ".GGGGGGG..........",
        "....L.............",
        "....L.............",
        "....L.............",
        "....G.............",
        "..................",
    ]),
    "vulnerable": (dict(S=P.SILVER, M=P.CLOTH_RED), [
        "..................",
        "..SSSSSSSSSSSSSS..",
        "..SMMMMMM.MMMMMS..",
        "..SMMMMM.MMMMMMS..",
        "..SMMMMM.MMMMMMS..",
        "..SMMMMMM.MMMMMS..",
        "..SMMMMMMM.MMMMS..",
        "..SMMMMMMM.MMMMS..",
        "..SMMMMMM.MMMMMS..",
        "..SMMMMM.MMMMMMS..",
        "...SMMMM.MMMMMS...",
        "....SMMMM.MMMS....",
        ".....SMMM.MMS.....",
        "......SMMM.S......",
        ".......SMMS.......",
        "........SS........",
        "..................",
        "..................",
    ]),
    "crit_up": (dict(R=P.POTION_RED, X=("flat", hexc("ffffff")), Y=("glow", P.HOLY_GOLD)), [
        "...............Y..",
        "..............YYY.",
        "........R....YYYYY",
        "........R......Y..",
        ".....RRRRRR....Y..",
        "....RR..R.RR......",
        "...R....R...R.....",
        "..RR....R...RR....",
        "..R..........R....",
        "..R.....X....R....",
        "RRRRRR.XXX.RRRRR..",
        "..R.....X....R....",
        "..RR........RR....",
        "...R....R...R.....",
        "....RR..R.RR......",
        ".....RRRRRR.......",
        "........R.........",
        "........R.........",
    ]),
    "lifesteal": (dict(Z=P.HOLY_WHITE, R=P.BLOOD, X=("flat", hexc("ffd0d0"))), [
        "..................",
        "...ZZZZZZZZZZZZ...",
        "....ZZ......ZZ....",
        "....ZZ......ZZ....",
        ".....Z......Z.....",
        ".....Z......Z.....",
        "..................",
        "........RR........",
        ".......RRRR.......",
        "......RRRRRR......",
        "......RRRRRR......",
        ".....RRXRRRRR.....",
        ".....RRXRRRRR.....",
        ".....RRRRRRRR.....",
        ".....RRRRRRRR.....",
        "......RRRRRR......",
        "........RR........",
        "..................",
    ]),
    "frost_weapon": (dict(I=P.ICE, X=("flat", hexc("f0fdff")), G=P.GOLD, L=P.LEATHER), [
        "..................",
        "..................",
        "...........X..IX..",
        ".............IXI..",
        "............IXI...",
        "...........IXI....",
        "..........IXI..X..",
        ".........IXI.I.I..",
        "........IXI...X...",
        "...G...IXI...I.IX.",
        "....G.IXI.........",
        ".....GXI..........",
        ".....IG...........",
        "....L..G..........",
        "...L....G.........",
        "..L...............",
        ".G................",
        "..................",
    ]),
    "venom_weapon": (dict(W=P.SILVER, X=("flat", hexc("ffffff")), G=P.GOLD, L=P.LEATHER, V=P.SLIME), [
        "..................",
        "..................",
        "..............WX..",
        ".............WXW..",
        "............WVW...",
        "...........WVW....",
        "..........WVWV....",
        ".........WVW.V....",
        "........WVW..V....",
        "...G...WXWV.......",
        "....G.WXW.V.......",
        ".....GXW..........",
        ".....WG...........",
        "....L..G..........",
        "...L....G.........",
        "..L...............",
        ".G................",
        "..................",
    ]),
    "thunder_weapon": (dict(W=P.SILVER, X=("flat", hexc("ffffff")), G=P.GOLD, L=P.LEATHER, T=("glow", THUNDER)), [
        "..................",
        "..................",
        "..............WX..",
        ".............WXW..",
        "............WXW...",
        "...........WXW....",
        "..........WXWTTT..",
        ".........WXWTTT...",
        "........WXWTTTTT..",
        "...G...WXW...TT...",
        "....G.WXW...TT....",
        ".....GXW....T.....",
        ".....WG...........",
        "....L..G..........",
        "...L....G.........",
        "..L...............",
        ".G................",
        "..................",
    ]),
    "evasion": (dict(E=P.LINEN, B=P.WOOD, A=("glow", P.SKY)), [
        "..................",
        "..................",
        "..................",
        "..............E...",
        ".AAAAA......Ee....",
        "...........EeE....",
        ".........EEeE.....",
        "..AAAAA.EEeE......",
        ".......EEeEE......",
        "......EEeEE.......",
        "......EeEE........",
        ".....EeEE.........",
        "....EeE...........",
        "....BE............",
        "...B..............",
        "..B........AAAAAA.",
        "..................",
        "..................",
    ]),
    "thorns": (dict(S=P.STEEL, M=P.LEATHER, W=P.SILVER, G=P.GOLD), [
        "..................",
        ".........W........",
        ".........W........",
        "...W....SW.....W..",
        "....W.SSSWSS..W...",
        ".....WMMMMMMSW....",
        "....SMMMMMMMMS....",
        "....SMMMGGMMMS....",
        "...SSMMGGGGMMSS...",
        ".WWWWMMGGGGMMSWWWW",
        "....SMMMGGMMMS....",
        "....SMMMMMMMMS....",
        ".....SMMMMMMS.....",
        ".....WSSSSSS.W....",
        "....W...SW....W...",
        "...W.....W.....W..",
        ".........W........",
        ".........W........",
    ]),
}


def effect(name: str) -> Canvas:
    mats, rows = EFFECTS[name]
    return render(rows, mats, outline=OUT, local="GWY")


def particle_frames(name: str):
    fn, n = PARTICLES[name]
    return [fn(i) for i in range(n)]


def eff_i(i):
    return effect(list(EFFECTS)[i])


def part_i(i):
    names = list(PARTICLES)
    out = []
    for nme in names:
        out.extend(particle_frames(nme))
    return out[i]
