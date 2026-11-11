"""The trinkets (装身具): 16x16 icons of the accessories (ring, amulet, earring, bracelet) and the charms (talisman,
ward bell, ward stone, charm pouch), drawn from ASCII patterns in each relic's own metal, gem and cloth.

Letters: A metal, a darker metal, G gem (shaded as a ball), R glow (lit runes and seals), C cloth, F paper or
embroidery, S cord.
"""
from __future__ import annotations

import palette as P
from icon import render

PATTERNS = {
    "RING": [
        "................",
        "................",
        "......GGGG......",
        ".....GGGGGG.....",
        ".....GGGGGG.....",
        "......GGGG......",
        ".....aAAAAa.....",
        "....AA....AA....",
        "...AA......AA...",
        "...A........A...",
        "...A........A...",
        "...AA......AA...",
        "....AA....AA....",
        ".....AAAAAA.....",
        "................",
        "................",
    ],
    "AMULET": [
        "..S..........S..",
        "...S........S...",
        "...S........S...",
        "....S......S....",
        ".....S....S.....",
        "......S..S......",
        ".......AA.......",
        "......AGGA......",
        ".....AGGGGA.....",
        "....AGGGGGGA....",
        "....AGGGGGGA....",
        ".....AGGGGA.....",
        "......AGGA......",
        ".......AA.......",
        ".......a........",
        "................",
    ],
    "EARRING": [
        "................",
        "......AAA.......",
        ".....A...A......",
        ".........A......",
        ".........A......",
        "........A.......",
        ".......AA.......",
        "......aAAa......",
        ".....AGGGGA.....",
        ".....AGGGGA.....",
        "......AGGA......",
        ".......AA.......",
        ".......RR.......",
        "........R.......",
        "................",
        "................",
    ],
    "BRACELET": [
        "................",
        "................",
        "................",
        ".....AAAAAA.....",
        "...AAGAAAAGAA...",
        "..AA........AA..",
        ".AA..........AA.",
        ".A............A.",
        ".A............A.",
        ".AA..........AA.",
        "..AA........AA..",
        "...aaGaaaaGaa...",
        ".....aaaaaa.....",
        "................",
        "................",
        "................",
    ],
    "TALISMAN": [
        ".......SS.......",
        "......S..S......",
        ".......SS.......",
        ".....FFFFFF.....",
        ".....FFFFFF.....",
        ".....FRRRRF.....",
        ".....FRFFRF.....",
        ".....FRRRRF.....",
        ".....FFRRFF.....",
        ".....FFRRFF.....",
        ".....FFFFFF.....",
        ".....FRFFRF.....",
        ".....FFFFFF.....",
        ".....AFFFFA.....",
        "......A..A......",
        "................",
    ],
    "BELL": [
        ".......SS.......",
        ".......SS.......",
        "......AAAA......",
        ".....AAAAAA.....",
        ".....AAAAAA.....",
        "....AAAAAAAA....",
        "....AAGAAGAA....",
        "...AAAAAAAAAA...",
        "...aaaaaaaaaa...",
        ".......GG.......",
        ".......CC.......",
        "......CCCC......",
        "......C..C......",
        "......C..C......",
        "................",
        "................",
    ],
    "STONE": [
        ".......SS.......",
        "......S..S......",
        "......S..S......",
        ".....AAAAAA.....",
        "....AAAAAAAA....",
        "...AAAARRAAAA...",
        "...AAARAARAAA...",
        "...AAAARRAAAA...",
        "...AAAAARAAAA...",
        "...AAAARAAAAA...",
        "....AAAARAAA....",
        ".....AAAAAA.....",
        "................",
        "................",
        "................",
        "................",
    ],
    "POUCH": [
        ".......SS.......",
        "......S..S......",
        "......SSSS......",
        ".....CCCCCC.....",
        "....CCCCCCCC....",
        "....CCFFFFCC....",
        "....CCFRRFCC....",
        "....CCFRRFCC....",
        "....CCFFFFCC....",
        "....CCCCCCCC....",
        "....CCCCCCCC....",
        "....cCCCCCCc....",
        ".....cccccc.....",
        "................",
        "................",
        "................",
    ],
}


def icon(kind: str, metal, gem, cloth):
    """The icon of a trinket of ``kind`` in its materials (ramps)."""
    mats = {"A": metal, "G": ("gem", gem), "R": ("glow", gem), "C": cloth, "S": P.STRING if kind != "AMULET" else metal}
    if kind == "TALISMAN":
        mats["F"] = P.PARCHMENT
        mats["R"] = ("glow", gem)
    elif kind == "POUCH":
        mats["F"] = metal
    if kind == "STONE":
        mats["A"] = cloth  # the carved stone in the relic's cloth colour, the rune in its gem
    return render(PATTERNS[kind], mats, local="AGRF")


def base_icons():
    """(accessory, charm): the plain icons of the trinket base items."""
    return icon("RING", P.SILVER, P.SKY, P.LINEN), icon("TALISMAN", P.BRONZE, P.CLOTH_RED, P.LINEN)
