"""Material, consumable and currency icons (16x16)."""
from __future__ import annotations

import palette as P
from icon import render, overlay
from px import Canvas, Ramp, hexc

W = hexc("ffffff")
DARK = ("flat", hexc("1a1520"))

MATERIALS = {
    "slime_gel": (dict(G=P.SLIME, W=("flat", hexc("eaffe0")), O=("flat", hexc("b8f5a4"))), [
        "................",
        "................",
        "................",
        "................",
        ".......GG.......",
        ".....GGGGGG.....",
        "....GGWWGGGG....",
        "...GGWGGGGGGG...",
        "...GGGGGGGGOGG..",
        "..GGGGGGGGGGGG..",
        "..GGGGGOGGGGGG..",
        "..GGGGGGGGGGGG..",
        "..gGGGGGGGGGGg..",
        "...gggggggggg...",
        "................",
        "................",
    ]),
    "wolf_fang": (dict(B=P.BONE, R=P.BLOOD), [
        "................",
        "..B.............",
        "..BB............",
        "..BBB...........",
        "...BBB..........",
        "...BBBB.........",
        "....BBBB........",
        "....BBBBB.......",
        ".....BBBBB......",
        "......BBBBB.....",
        ".......BBBBBB...",
        "........BBBBBb..",
        ".........BBBbb..",
        "..........RRRR..",
        "...........RR...",
        "................",
    ]),
    "treant_bark": (dict(B=P.WOOD, M=P.MOSS), [
        "................",
        "...........BB...",
        ".........BBBBB..",
        "........BbBBbBB.",
        ".......BbBBbBBb.",
        "......BbBBbBBbB.",
        ".....BbBBbBBbBB.",
        "....BbBBbBBbBB..",
        "...BBBBbBBbBB...",
        "..MMBbBBbBBB....",
        ".MMMMBBbBBB.....",
        ".MMMMBbBBB......",
        "..MMBBBBB.......",
        "...BBBBB........",
        "................",
        "................",
    ]),
    "forest_spore": (dict(S=P.SPORE, L=P.MOSS, O=("flat", hexc("f6ff9a"))), [
        "................",
        "..........O.....",
        "...O............",
        ".............O..",
        "......SSSS......",
        ".....SSSSSS.....",
        "....SSsSSsSS....",
        "....SSSSSSSS....",
        "....SsSSSSsS..O.",
        "....SSSSsSSS....",
        ".....SSSSSS.....",
        "......SSSS......",
        ".......LL.......",
        "......LLLL......",
        "................",
        "................",
    ]),
    "scarab_shell": (dict(S=P.SCARAB, B=P.GOLD), [
        "................",
        "................",
        "......BBBB......",
        ".....BBBBBB.....",
        "....SSSSSSSS....",
        "...SSSSSsSSSS...",
        "...SSSSSsSSSS...",
        "...SSSSSsSSSS...",
        "...SSSSSsSSSS...",
        "...SSSSSsSSSS...",
        "...SSSSSsSSSS...",
        "....SSSSsSSS....",
        ".....SSSsSS.....",
        "......SSSS......",
        "................",
        "................",
    ]),
    "desert_crystal": (dict(T=P.TURQUOISE, S=P.SANDSTONE), [
        "................",
        ".........T......",
        "........TTT.....",
        "....T...TtT.....",
        "...TTT..TtT.....",
        "...TtT.TTtT..T..",
        "...TtT.TTtT.TTT.",
        "...TtTTTTtT.TtT.",
        "..TTtTTTTtTTTtT.",
        "..SSTTTTTTTTSS..",
        ".SSSSSSSSSSSSSS.",
        ".SsSSSSSSSSsSSS.",
        "..SSSSSSSSSSSS..",
        "................",
        "................",
        "................",
    ]),
    "frost_shard": (dict(I=P.ICE, W=("tone", P.ICE, 1), J=("tone", P.ICE, -1)), None),
    "yeti_fur": (dict(F=P.FUR_WHITE, B=P.Ramp("1e2a3a", "5a7088", "7f95ad", "a6bacd", "cbd9e6", "eef4fa")), [
        "................",
        "....F..F...F....",
        "...FF.FF..FF.F..",
        "..FFFFFFFFFFFF..",
        ".FFFfFFFfFFFfF..",
        ".FFFFfFFFfFFFfF.",
        ".FFfFFfFFFfFFFF.",
        "..FFfFFfFFFfFF..",
        "..FFFfFFfFFFfFF.",
        ".BFFFFfFFfFFFFB.",
        ".BBFfFFfFFfFFBB.",
        "..BBBfBBfBBfBB..",
        "..BB.BB..BB.B...",
        "...B..B...B.....",
        "................",
        "................",
    ]),
    "magma_core": (dict(O=P.DRAGON_DARK, M=("glow", P.FLAME)), [
        "................",
        "......OOOO......",
        "....OOOMOOOO....",
        "...OOOMOOOOOO...",
        "..OOOMMMOOMOOO..",
        "..OOMOOOMMOOOO..",
        ".OOOMOOOOOMOOOO.",
        ".OOMMOOOOOMMOOO.",
        ".OOOOMMOOMOOMOO.",
        ".OOOOOOMMOOOMOO.",
        "..OOOOOMOOOMOO..",
        "..OOOOMOOOMOOO..",
        "...OOOMOOOOOO...",
        "....OOOOOOOO....",
        "......OOOO......",
        "................",
    ]),
    "drake_scale": (dict(D=P.DRAGON, G=P.GOLD), [
        "................",
        "................",
        "...GGGGGGGGGG...",
        "..DDDDDDDDDDDD..",
        "..DDDdDDDDdDDD..",
        "..DDDDdDDdDDDD..",
        "..DDDDDddDDDDD..",
        "..DDDDDDDDDDDD..",
        "...DDDDdDDDDD...",
        "...DDDDdDDDDD...",
        "....DDDdDDDD....",
        ".....DDdDDD.....",
        "......DDDD......",
        ".......DD.......",
        "................",
        "................",
    ]),
    "sky_feather": (dict(W=P.HOLY_WHITE, S=P.SKY, Q=P.GOLD_PALE), [
        "................",
        "...........SSS..",
        "..........WWSSS.",
        ".........WWWQSS.",
        "........WWWQWS..",
        ".......WWWQWWS..",
        "......WWWQWWW...",
        ".....WWWQWWW....",
        "....WWWQWWW.....",
        "....WWQWWW......",
        "...WWQWW........",
        "...WQW..........",
        "..QQ............",
        ".Q..............",
        "................",
        "................",
    ]),
    "star_fragment": (dict(S=P.HOLY_GOLD, O=("flat", hexc("fffbd2"))), [
        "................",
        ".......S........",
        ".......SS.......",
        "......SSS.......",
        "......SSSS....O.",
        "SSSSSSSSSSSSSS..",
        ".SSSSSSSSSSSS...",
        "..SSSSSSSSSS....",
        "...SSSSSSSS.....",
        "...SSSSSSSSS....",
        "..SSSS..SSSS....",
        "..SSS....SSS....",
        ".SS.......SS..O.",
        "...........O.OOO",
        "..............O.",
        "................",
    ]),
    "void_essence": (dict(V=P.VOID, L=("glow", P.VOID_GLOW), O=("flat", hexc("e6c2ff"))), [
        "................",
        "...........O....",
        ".......V........",
        "......VV........",
        ".....VVVV.......",
        ".....VVVVV......",
        "....VVVLVVV.....",
        "...VVVLLLVVV.O..",
        "...VVLLLLLVV....",
        "...VVLLLLLVV....",
        "...VVVLLLVVV....",
        "....VVVVVVV.....",
        ".....VVVVV......",
        "..O...VVV.......",
        "................",
        "................",
    ]),
    "abyss_crystal": (dict(A=P.ABYSS, V=("glow", P.VOID_GLOW)), [
        "................",
        ".......AA.......",
        "......AAAA......",
        ".....AAAAAA.....",
        ".....AAVVAA.....",
        "....AAAVVAAA....",
        "....AAVVVVAA....",
        "...AAAVVVVAAA...",
        "....AAVVVVAA....",
        "....AAAVVAAA....",
        ".....AAVVAA.....",
        ".....AAAAAA.....",
        "......AAAA......",
        ".......AA.......",
        "................",
        "................",
    ]),
    "enhancement_stone": (dict(S=P.STONE, R=("glow", P.HOLY_GOLD)), [
        "................",
        "................",
        "......SSSS......",
        "....SSSSSSSS....",
        "...SSSSRRSSSS...",
        "..SSSSRRRRSSSS..",
        "..SSSRRRRRRSSS..",
        "..SSSSSRRSSSSS..",
        "..SSSSSRRSSSSS..",
        "..SSSSSRRSSSSS..",
        "..SSSSSSSSSSSS..",
        "...SSSSSSSSSS...",
        "....SSSSSSSS....",
        "......SSSS......",
        "................",
        "................",
    ]),
    "reroll_crystal": (dict(A=P.Ramp("2a0a2e", "7a1f8a", "b43cc4", "e070e8", "f8b0ff", "ffffff"),
                            B=P.Ramp("06283a", "0f5c80", "1c90c0", "3cc8ea", "98ecff", "ffffff"),
                            C=P.Ramp("3a2a06", "8a6a10", "c8a020", "f0d040", "fff09a", "ffffff")), [
        "................",
        "................",
        "................",
        "....AAABBBCC....",
        "...AAABBBBCCC...",
        "..AAAABBBBCCCC..",
        "..aaaabbbbcccc..",
        "...AAABBBCCCC...",
        "....AABBBCCC....",
        ".....ABBBCC.....",
        "......BBBC......",
        ".......BB.......",
        "................",
        "................",
        "................",
        "................",
    ]),
}

# the field-map materials (2026-11): templates + ramps, see materials_v2.py
from materials_v2 import ICONS as _FIELD_MATERIALS  # noqa: E402

MATERIALS.update(_FIELD_MATERIALS)

CONSUMABLES = {
    "health_potion_small": (dict(C=P.WOOD_PALE, G=P.GLASS, R=P.POTION_RED, W=("flat", hexc("ffe0dc"))), [
        "................",
        "................",
        "................",
        ".......CC.......",
        ".......CC.......",
        ".......GG.......",
        "......GGGG......",
        ".....GRRRRG.....",
        "....GRWRRRRG....",
        "....GRRRRRRG....",
        "....GRRRRRRG....",
        ".....GRRRRG.....",
        "......GGGG......",
        "................",
        "................",
        "................",
    ]),
    "health_potion_medium": (dict(C=P.WOOD_PALE, G=P.GLASS, R=P.POTION_RED, W=("flat", hexc("ffe0dc"))), [
        "................",
        "................",
        ".......CC.......",
        "......CCCC......",
        ".......GG.......",
        ".......GG.......",
        "......GGGG......",
        ".....GRRRRG.....",
        "....GRRRRRRG....",
        "...GRWRRRRRRG...",
        "...GRWRRRRRRG...",
        "...GRRRRRRRRG...",
        "...GRRRRRRRRG...",
        "....GRRRRRRG....",
        ".....GGGGGG.....",
        "................",
    ]),
    "health_potion_large": (dict(C=P.GOLD, G=P.GLASS, R=P.POTION_RED, W=("flat", hexc("ffe0dc")),
                                 H=("flat", hexc("fff2f0"))), [
        "................",
        "......CCCC......",
        "......cCCc......",
        ".......GG.......",
        "......GGGG......",
        ".....GRRRRG.....",
        "....GRRRRRRG....",
        "...GRWRRRRRRG...",
        "..GRWRRHRHRRRG..",
        "..GRRRRHHHRRRG..",
        "..GRRRRRHRRRRG..",
        "..GRRRRRRRRRRG..",
        "...GRRRRRRRRG...",
        "....GRRRRRRG....",
        ".....GGGGGG.....",
        "................",
    ]),
    "stamina_tonic": (dict(C=P.WOOD_PALE, G=P.GLASS, R=P.POTION_GREEN, W=("flat", hexc("f4ffd0")),
                           L=("flat", hexc("fff27a"))), [
        "................",
        ".......CC.......",
        "......CCCC......",
        ".......GG.......",
        "......GGGG......",
        ".....GRRRRG.....",
        ".....GRWRRG.....",
        ".....GRWRLG.....",
        ".....GRRLRG.....",
        ".....GRLLRG.....",
        ".....GRRLRG.....",
        ".....GRLRRG.....",
        ".....GRRRRG.....",
        ".....GRRRRG.....",
        "......GGGG......",
        "................",
    ]),
    "return_scroll": (dict(P=P.PARCHMENT, S=P.JOB_KNIGHT, R=P.WOOD), [
        "................",
        "................",
        "...........RR...",
        "..........RPPR..",
        ".........PPPPR..",
        "........PPPPP...",
        ".......PPpPPP...",
        "......PPPpPP....",
        ".....PPPpPPP....",
        "....PPPSSPP.....",
        "...PPPSSSSP.....",
        "..RPPPPSSPP.....",
        "..RPPPPPPP......",
        "...RRPPP........",
        "................",
        "................",
    ]),
    "oblivion_elixir": (dict(C=P.GOLD, G=P.GLASS, R=P.POTION_VIOLET, W=("flat", hexc("f0dcff")),
                             L=("glow", P.VOID_GLOW)), [
        "................",
        ".......CC.......",
        "......CCCC......",
        ".......CC.......",
        ".......GG.......",
        "......GGGG......",
        ".....GRRRRG.....",
        "....GRWRRRRG....",
        "...GRWRRLLRRG...",
        "..GRRRRLRRLRRG..",
        "...GRRRLLRRRG...",
        "....GRRRRRRG....",
        ".....GRRRRG.....",
        "......GRRG......",
        ".......GG.......",
        "................",
    ]),
}

COIN = (dict(G=P.GOLD, S=("tone", P.GOLD_PALE, 1), E=P.BRONZE), [
    "................",
    "................",
    ".....GGGGGG.....",
    "....GGggggGG....",
    "...GggggSgggG...",
    "..GgggggSggggG..",
    "..GgSSSSSSSggG..",
    "..GggSSSSSgggG..",
    "..GgggSSSggggG..",
    "..GggSSgSSgggG..",
    "..GggSgggSgggG..",
    "...GgggggggggE..",
    "....GGggggGGE...",
    ".....GGGGGGE....",
    "......EEEE......",
    "................",
])


def _shard_rows():
    """Kite-shaped ice crystal (lit left facet W, shaded right facet J) plus a small one."""
    from wbase import point_in_poly
    rows = [["."] * 16 for _ in range(16)]

    def kite(tip_a, tip_b, half):
        ax, ay = tip_a
        bx, by = tip_b
        mx, my = ax + (bx - ax) * 0.42, ay + (by - ay) * 0.42
        dx, dy = bx - ax, by - ay
        ln = (dx * dx + dy * dy) ** 0.5
        nx, ny = -dy / ln, dx / ln
        left = (mx + nx * half, my + ny * half)
        right = (mx - nx * half, my - ny * half)
        for y in range(16):
            for x in range(16):
                if point_in_poly(x + 0.5, y + 0.5, [tip_a, left, tip_b, right]):
                    # side of the axis: cross product sign
                    cr = (x + 0.5 - ax) * dy - (y + 0.5 - ay) * dx
                    rows[y][x] = "J" if cr > 0.9 else ("I" if cr > -0.9 else "W")
    kite((12.6, 0.8), (3.2, 15.0), 3.1)
    kite((14.6, 7.0), (10.5, 14.6), 1.6)
    return ["".join(r) for r in rows]


def material(name: str) -> Canvas:
    mats, rows = MATERIALS[name]
    if rows is None:
        rows = _shard_rows()
    return render(rows, mats, local="BMSGTLQRV")


def consumable(name: str) -> Canvas:
    mats, rows = CONSUMABLES[name]
    return render(rows, mats, local="C")


def gold_coin() -> Canvas:
    mats, rows = COIN
    return render(rows, mats, local="S")


def mat_rows(name):
    return MATERIALS[name][1] or _shard_rows()


def mat_i(i):
    return material(list(MATERIALS)[i])


def con_i(i):
    if i == len(CONSUMABLES):
        return gold_coin()
    return consumable(list(CONSUMABLES)[i])
