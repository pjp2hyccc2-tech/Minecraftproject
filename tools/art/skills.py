"""Skill icons (16x16, framed and colour-coded by job) and job emblems (16x16)."""
from __future__ import annotations

import math

import numpy as np

import palette as P
from icon import render
from px import Canvas, Ramp, hexc, mix

JOB_RAMP = {
    "knight": P.JOB_KNIGHT, "assassin": P.JOB_ASSASSIN, "mage": P.JOB_MAGE, "ranger": P.JOB_RANGER,
    "priest": P.JOB_PRIEST, "spellblade": P.JOB_SPELLBLADE, "weapon": P.JOB_WEAPON,
}
# metal used for the frame bevel of each job
JOB_METAL = {
    "knight": P.SILVER, "assassin": P.Ramp("120408", "3a0a12", "5c1420", "7e2230", "a8404a", "d27a7a"),
    "mage": P.GOLD, "ranger": P.BRONZE, "priest": P.HOLY_GOLD, "spellblade": P.STEEL, "weapon": P.BRONZE,
}

GLOW_Y = ("glow", P.HOLY_GOLD)
WHITE = ("flat", hexc("ffffff"))

M = dict(
    W=P.SILVER, S=P.STEEL, G=P.GOLD, F=P.FLAME, I=P.ICE, V=P.SLIME, R=P.BLOOD, D=P.SHADOW,
    C=P.Ramp("2a3040", "6a7488", "98a2b4", "c4ccd8", "e4e9f0", "ffffff"), L=("glow", P.Ramp(
        "4a3a06", "a08a10", "e0c420", "fff04a", "fffaa0", "ffffff")), Y=GLOW_Y, H=P.HEAL_GREEN,
    A=P.STEEL, T=P.WOOD, E=P.LINEN, P=("glow", P.VOID_GLOW), Q=("glow", P.CYAN_RUNE), B=P.WOOD,
    O=P.OBSIDIAN, X=WHITE, U=P.SKY, N=P.STONE, Z=P.HOLY_WHITE, K=None,
)

# 12x12 symbols drawn inside the frame (x, y offset 2, 2). K = the job colour.
SYMBOLS = {
    "knight_shield_bash": ("knight", [
        "............",
        "..WWWWWW....",
        ".WWWKKWWW.X.",
        ".WWWKKWWW..X",
        ".WKKKKKKW...",
        ".WKKKKKKW.XX",
        ".WWWKKWWW...",
        "..WWKKWW...X",
        "..WWKKWW..X.",
        "...WWWW.....",
        "....WW......",
        "............",
    ]),
    "knight_war_cry": ("knight", [
        "..........X.",
        "........X...",
        ".......X..X.",
        "....GGG...X.",
        "...GSSSG..X.",
        "G.GSSSSSG.X.",
        "GGSSGSSSG...",
        ".GSSGSSG..X.",
        "..GGSSG..X..",
        "....GG...X..",
        "............",
        "............",
    ]),
    "knight_iron_wall": ("knight", [
        "............",
        ".WWWWWWWWWW.",
        ".WSSSSSSSSW.",
        ".WSsSSSSsSW.",
        ".WSSSSSSSSW.",
        ".WWWWWWWWWW.",
        ".WSSSSSSSSW.",
        ".WSsSSSSsSW.",
        ".WSSSSSSSSW.",
        "..WSSSSSSW..",
        "...WWWWWW...",
        "............",
    ]),
    "knight_holy_judgment": ("knight", [
        ".Y...GG...Y.",
        "..Y..GG..Y..",
        "...GGGGGG...",
        ".....WW.....",
        "Y....WW....Y",
        ".....WW.....",
        "..Y..WW..Y..",
        ".....WW.....",
        ".....WW.....",
        "...Y.WW.Y...",
        ".....WW.....",
        "......W.....",
    ]),
    "assassin_shadow_step": ("assassin", [
        "............",
        "......DDD...",
        ".RR...DDD...",
        "......DDD...",
        "..RR..DDD...",
        "......DDDD..",
        ".RRR..DDDDD.",
        "......DDDDD.",
        "..RR.DDDDDD.",
        "......dddd..",
        "............",
        "............",
    ]),
    "assassin_venom_blade": ("assassin", [
        "..........W.",
        ".........WW.",
        "........WW..",
        ".......WW...",
        "......WW....",
        ".....WW.....",
        "..RRWW......",
        "...RR.......",
        "..RVRV......",
        ".R..V.V.....",
        "....V.......",
        "............",
    ]),
    "assassin_smoke_bomb": ("assassin", [
        ".......C.C..",
        "...C..C.C.C.",
        "........F...",
        ".......FC...",
        "....DDDD....",
        "...DDDDDD...",
        "..DDDDDDDD..",
        "..DDDDDDDD..",
        "..DDDDDDDD..",
        "...DDDDDD.C.",
        ".C..DDDD.CC.",
        "..CC.....C..",
    ]),
    "assassin_thousand_blades": ("assassin", [
        ".....W......",
        ".W...W...W..",
        "..W..W..W...",
        "..WW.W.WW...",
        "W..W.W.W..W.",
        ".WW.WWW.WW..",
        "...WWWWW....",
        "....RRR.....",
        "...RRRRR....",
        "............",
        "............",
        "............",
    ]),
    "mage_fireball": ("mage", [
        "............",
        "......FFFF..",
        ".....FFFFFF.",
        "....FFFFFFF.",
        "...FFFFFFFF.",
        "..FF.FFFFFF.",
        ".F..FFFFFF..",
        "...F..FFF...",
        ".F...F......",
        "....F.......",
        "..F.........",
        "............",
    ]),
    "mage_ice_lance": ("mage", [
        "..........I.",
        ".........II.",
        "........III.",
        ".......III..",
        "......III...",
        ".....III....",
        "..I.III.....",
        "...III......",
        "..IIII......",
        ".I...I......",
        "............",
        "............",
    ]),
    "mage_thunderstorm": ("mage", [
        "............",
        "...CCCC.....",
        "..CCCCCCC...",
        ".CCCCCCCCC..",
        ".CCCCCCCCCC.",
        "..CCCCCCCC..",
        "......LL....",
        ".....LL.....",
        "....LLLL....",
        "......LL....",
        ".....LL.....",
        ".....L......",
    ]),
    "mage_meteor": ("mage", [
        "..........FF",
        ".........FFF",
        "........FFF.",
        "......FFFF..",
        "....OOOF....",
        "...OOOOOO...",
        "..OOOOOOO...",
        "..OOOOOOO...",
        "..OOOOOO....",
        "...OOOO.....",
        "............",
        "............",
    ]),
    "ranger_rapid_shot": ("ranger", [
        "........A...",
        ".......TAA..",
        "......T.....",
        ".....T...A..",
        "....T...TAA.",
        "...T...T....",
        "..E...T.....",
        ".EE..T......",
        "....T.......",
        "...E........",
        "..EE........",
        "............",
    ]),
    "ranger_spread_shot": ("ranger", [
        ".....A....A.",
        "....AA...AA.",
        ".....T..T..A",
        ".....T.T..AA",
        ".....TT..T..",
        ".....T..T...",
        ".....TTT....",
        "....TT......",
        "...TE.......",
        "..EE........",
        ".E..........",
        "............",
    ]),
    "ranger_leap_shot": ("ranger", [
        "............",
        ".........A..",
        "....TTTTTAA.",
        "..EE.....A..",
        "............",
        "..KKK.......",
        ".K...K......",
        "K.....K.....",
        "K.....K.....",
        "KK...KKK....",
        ".....KK.....",
        "......K.....",
    ]),
    "ranger_arrow_rain": ("ranger", [
        ".E...E...E..",
        ".T...T...T..",
        ".T...T...T..",
        "AAA.AAA.AAA.",
        ".A...A...A..",
        "...E...E....",
        "...T...T....",
        "...T...T....",
        "..AAA.AAA...",
        "...A...A....",
        "............",
        "............",
    ]),
    "priest_heal": ("priest", [
        "............",
        "....HHHH....",
        "....HZZH....",
        "....HZZH....",
        ".HHHHZZHHHH.",
        ".HZZZZZZZZH.",
        ".HZZZZZZZZH.",
        ".HHHHZZHHHH.",
        "....HZZH....",
        "....HZZH....",
        "....HHHH....",
        "............",
    ]),
    "priest_holy_light": ("priest", [
        ".....Y......",
        "..Y..Y..Y...",
        "...Y.Y.Y....",
        "....ZZZ.....",
        "...ZZZZZ....",
        "YYYZZZZZYYY.",
        "...ZZZZZ....",
        "....ZZZ.....",
        "...Y.Y.Y....",
        "..Y..Y..Y...",
        ".....Y......",
        "............",
    ]),
    "priest_blessing": ("priest", [
        "...GGGGGG...",
        "..G......G..",
        "...GGGGGG...",
        "............",
        "Z....YY....Z",
        "ZZ..YYYY..ZZ",
        "ZZZ.YYYY.ZZZ",
        ".ZZZ.YY.ZZZ.",
        "..ZZZ..ZZZ..",
        "...ZZ..ZZ...",
        "............",
        "............",
    ]),
    "priest_sanctuary": ("priest", [
        "..Y.....Y...",
        "..Y..W..Y...",
        "..Y.WWW.Y...",
        "..Y..W..Y...",
        "..Y..W..Y...",
        "..Y..W..Y...",
        ".YYYYYYYYY..",
        "Y.........Y.",
        "Y.........Y.",
        ".YYYYYYYYY..",
        "............",
        "............",
    ]),
    "spellblade_arcane_edge": ("spellblade", [
        "........QQQ.",
        ".......QWWQ.",
        "......QWWQ..",
        ".....QWWQ...",
        "....QWWQ....",
        "...QWWQ.....",
        "..GQWQ......",
        "...GG.......",
        "..BBG.......",
        ".BB.........",
        "............",
        "............",
    ]),
    "spellblade_flame_blade": ("spellblade", [
        ".......F..F.",
        "......FFWWF.",
        ".....F.WWF..",
        "....FFWWFF..",
        "...F.WWF....",
        "..FFWWFF....",
        "...WWF......",
        "..GGF.......",
        ".BBG........",
        "BB..........",
        "............",
        "............",
    ]),
    "spellblade_mana_wave": ("spellblade", [
        "............",
        "....QQQ.....",
        "......QQ....",
        ".......QQ...",
        "..KK....Q...",
        "....KK..QQ..",
        "....KK..QQ..",
        "..KK....Q...",
        ".......QQ...",
        "......QQ....",
        "....QQQ.....",
        "............",
    ]),
    "spellblade_dimension_slash": ("spellblade", [
        "..........Q.",
        ".........QD.",
        "...X....QDQ.",
        "........DQ..",
        ".......QDQ..",
        "......QDQ...",
        ".....QDQ..X.",
        "....QDQ.....",
        "...QDQ......",
        "..QDQ.......",
        ".QD.....X...",
        ".Q..........",
    ]),
    "whirlwind": ("weapon", [
        "....WWWW....",
        "..WW....WW..",
        ".W..KKKK..W.",
        ".W.K....K.W.",
        "W.K..SS..K..",
        "W.K..SS..K.W",
        "..K...S..K.W",
        ".W.K....K.W.",
        ".W..KKKK..W.",
        "..WW....WW..",
        "....WWWW....",
        "............",
    ]),
    "ground_rend": ("weapon", [
        ".....GG.....",
        ".....SS.....",
        "...GGGGGG...",
        ".....SS.....",
        ".....SS.....",
        ".....SS.....",
        ".N...SS...N.",
        "..N..SS..N..",
        "NNNNNNNNNNNN",
        "NnNNNnnNNNnN",
        "..n..nn..n..",
        "............",
    ]),
    "blade_dance": ("weapon", [
        "W..........W",
        ".W..KKKK..W.",
        "..W.....KW..",
        "...W....W.K.",
        "....W..W...K",
        ".....WW....K",
        ".....WW.....",
        "....W..W....",
        "..GW....WG..",
        "..G......G..",
        ".B........B.",
        "............",
    ]),
    "spiral_thrust": ("weapon", [
        ".........SS.",
        "........SSS.",
        "......KSSK..",
        "....K.SS..K.",
        "...K.SS..K..",
        "..K.TT..K...",
        ".K.TT..K....",
        "..TT..K.....",
        ".TT.........",
        "TT..........",
        "............",
        "............",
    ]),
    "arcane_nova": ("weapon", [
        ".....P......",
        "..P..P..P...",
        "...P.P.P....",
        "....PPP.....",
        ".PPPPXPPPP..",
        "....PPP.....",
        "...P.P.P....",
        "..P..P..P...",
        ".....P......",
        "............",
        "............",
        "............",
    ]),
    "piercing_arrow": ("weapon", [
        "....RRRR....",
        "...R....R...",
        "..R.RRRR.R..",
        "..R.R..R.R..",
        "EETTTTTTTTAA",
        "..R.R..R.R..",
        "..R.RRRR.R..",
        "...R....R...",
        "....RRRR....",
        "............",
        "............",
        "............",
    ]),
}


def frame(job: str) -> Canvas:
    ramp = JOB_RAMP[job]
    metal = JOB_METAL[job]
    c = Canvas(16)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                col = metal[0]
            elif edge == 1:
                if x == 1 and y == 1:
                    col = metal[5]
                elif y == 1 or x == 1:
                    col = metal[4]
                elif y == 14 or x == 14:
                    col = metal[2]
                else:
                    col = metal[3]
            else:
                # inner background: dark, vertical gradient + vignette
                d = math.hypot(x - 7.5, y - 7.5) / 7.5
                f = 0.55 - 0.35 * d + 0.12 * (1 - y / 15)
                col = mix(ramp[0], ramp[2], max(0.0, min(1.0, f)))
            c.put(x, y, col)
    # rivets in the corners
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.put(x, y, metal[5] if (x, y) == (1, 1) else metal[3])
    return c


def skill_icon(sid: str) -> Canvas:
    job, rows = SYMBOLS[sid]
    mats = dict(M)
    mats["K"] = JOB_RAMP[job] if job != "weapon" else P.GOLD
    sym = render(rows, mats, local="", size=16, offset=(2, 2), edge=1.2, grad=0.6, base=3.4)
    c = frame(job)
    # dark drop shadow under the symbol for readability
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
    return c


JOB_SKILLS = {
    "knight": ["knight_shield_bash", "knight_war_cry", "knight_iron_wall", "knight_holy_judgment"],
    "assassin": ["assassin_shadow_step", "assassin_venom_blade", "assassin_smoke_bomb", "assassin_thousand_blades"],
    "mage": ["mage_fireball", "mage_ice_lance", "mage_thunderstorm", "mage_meteor"],
    "ranger": ["ranger_rapid_shot", "ranger_spread_shot", "ranger_leap_shot", "ranger_arrow_rain"],
    "priest": ["priest_heal", "priest_holy_light", "priest_blessing", "priest_sanctuary"],
    "spellblade": ["spellblade_arcane_edge", "spellblade_flame_blade", "spellblade_mana_wave",
                   "spellblade_dimension_slash"],
}
WEAPON_SKILLS = ["whirlwind", "ground_rend", "blade_dance", "spiral_thrust", "arcane_nova", "piercing_arrow"]
ALL_SKILLS = [s for v in JOB_SKILLS.values() for s in v] + WEAPON_SKILLS

# --------------------------------------------------------------------------- job emblems
EMBLEMS = {
    "knight": [
        "....WWWWWW....",
        "...WWWWWWWW...",
        "...WWWKKWWW...",
        "...WWWKKWWW...",
        "...WKKKKKKW...",
        "...WKKKKKKW...",
        "...WWWKKWWW...",
        "....WWKKWW....",
        "....WWKKWW....",
        ".....WWWW.....",
        "......WW......",
    ],
    "assassin": [
        "..........W...",
        ".....KK..WW...",
        "...KKK..WW....",
        "..KKK..WW.....",
        "..KK..WW......",
        "..KK.WW.......",
        "..KKWWKK......",
        "...GGKKKKK....",
        "..BBG.KKK.....",
        ".BB...........",
        "..............",
    ],
    "mage": [
        "......K.......",
        ".....KK.......",
        "....KKKK......",
        "....KKYKK.....",
        "...KKYYYKK....",
        "...KKKYKKK....",
        "..KKKKKKKKK...",
        "..GGGGGGGGG...",
        ".KKKKKKKKKKK..",
        "..............",
        "..............",
    ],
    "ranger": [
        "...BB.........",
        "..B..B........",
        ".B....B....A..",
        ".B.....B..AA..",
        "B......TTAA...",
        "B.....TT.B....",
        "B....TT..B....",
        ".B..TT..B.....",
        ".B.EE..B......",
        "..BE..B.......",
        "...BBB........",
    ],
    "priest": [
        "......YY......",
        "..Y...ZZ...Y..",
        "...Y..ZZ..Y...",
        "......ZZ......",
        ".YY.ZZZZZZ.YY.",
        "....ZZZZZZ....",
        "......ZZ......",
        "...Y..ZZ..Y...",
        "..Y...ZZ...Y..",
        "......ZZ......",
        "......YY......",
    ],
    "spellblade": [
        "....QQQQ...W..",
        "...Q....Q.WW..",
        "..Q.....WWQ...",
        "..Q....WW.Q...",
        "..Q...WW..Q...",
        "...Q.WW..Q....",
        "....GWWQQ.....",
        "....GG........",
        "...BBG........",
        "..BB..........",
        "..............",
    ],
}


def emblem(job: str) -> Canvas:
    ramp = JOB_RAMP[job]
    metal = JOB_METAL[job]
    c = Canvas(16)
    # medallion: metal rim, job colour field
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.6:
                if d > 6.4:
                    lit = (x - 7.5) * -0.6 + (y - 7.5) * -0.8
                    c.put(x, y, metal[4] if lit > 2 else metal[2] if lit < -2 else metal[3])
                else:
                    f = 0.75 - d / 9 + 0.15 * (1 - y / 15)
                    c.put(x, y, mix(ramp[1], ramp[3], max(0.0, min(1.0, f))))
    c.outline(metal[0])
    mats = dict(M)
    mats["K"] = ramp if job not in ("knight", "spellblade") else ramp
    if job == "knight":
        mats["K"] = P.JOB_KNIGHT
        mats["W"] = P.SILVER
    if job == "assassin":
        mats["K"] = P.Ramp("1a0408", "4a0a14", "7a1222", "b02032", "e04a4a", "ff9a8a")
    if job == "mage":
        mats["K"] = P.Ramp("120626", "2c1060", "4a1e96", "6a34c8", "9a64ec", "d8c0ff")
    if job == "ranger":
        mats["B"] = P.WOOD_PALE
    sym = render(EMBLEMS[job], mats, local="", size=16, offset=(1, 2), edge=1.2, grad=0.6, base=3.4)
    for y in range(16):
        for x in range(16):
            if sym.a[y, x, 3]:
                c.a[y, x] = sym.a[y, x]
    return c


JOBS = ["knight", "assassin", "mage", "ranger", "priest", "spellblade"]


def skill_i(i):
    return skill_icon(ALL_SKILLS[i])


def emblem_i(i):
    return emblem(JOBS[i])
