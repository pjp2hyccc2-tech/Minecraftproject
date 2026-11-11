"""Minimap icons of the town's NPCs (12x12 each, one atlas row), drawn pixel by pixel.

python3 tools/art/map_npc_icons.py  ->  src/main/resources/assets/eternalrealm/textures/gui/map/npc_icons.png
                                        (and tools/art/map_npc_icons_preview.png, 8x)

The order is the icon index of ``com.eternalrealm.world.map.NpcMarkers`` (keep both in step).
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "map")


def hexc(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


BASE = {".": None, "o": hexc("120c08")}

ICONS = [
    # 0 quest board: a notice board with a pinned paper and a red mark
    ("quest_board", [
        "............",
        ".oooooooooo.",
        ".oBBBBBBBBo.",
        ".oBWWWWWWBo.",
        ".oBWrWWlWBo.",
        ".oBWrWWWWBo.",
        ".oBWWWlWWBo.",
        ".oBWrWWWWBo.",
        ".oBWWWWWWBo.",
        ".oBBBBBBBBo.",
        ".ooBoooooBo.",
        "..oo....oo..",
    ], {"B": hexc("8a5a32"), "W": hexc("f2ead2"), "r": hexc("d8342c"), "l": hexc("7a7468")}),
    # 1 guild registrar: a quill over a scroll
    ("guild_registrar", [
        ".........oo.",
        "........oWo.",
        ".......oWWo.",
        "......oWWo..",
        ".ooooo3Wooo.",
        "oSSSSo3oSSSo",
        "oSllll3llSSo",
        "oSSSSS3SSSSo",
        "oSlllllllSSo",
        "oSSSSSSSSSSo",
        ".oooooooooo.",
        "............",
    ], {"W": hexc("e8f0ff"), "3": hexc("2a3a7a"), "S": hexc("ead9a8"), "l": hexc("9a8456")}),
    # 2 item shop: a red potion flask
    ("item_shop", [
        "....oooo....",
        "....oCCo....",
        ".....oo.....",
        "....oGGo....",
        "...oGHGGo...",
        "..oRRRRRRo..",
        ".oRHRRRRRRo.",
        ".oRRRRRRRRo.",
        ".oRRRRRRDRo.",
        ".oDRRRRRRDo.",
        "..oDDDDDDo..",
        "...oooooo...",
    ], {"C": hexc("a0703c"), "G": hexc("cfe6f2"), "H": hexc("ffffff"), "R": hexc("d83a4a"), "D": hexc("8a1e2c")}),
    # 3 blacksmith: an anvil with a spark
    ("blacksmith", [
        "..........y.",
        ".........yYy",
        ".oooooooo.y.",
        "oHHHHHHHHo..",
        "oSSSSSSSSSoo",
        ".oDSSSSSDDo.",
        "...oSSSDo...",
        "...oSSSDo...",
        "..oSSSSSDo..",
        ".oSSSSSSSDo.",
        ".oDDDDDDDDo.",
        "..oooooooo..",
    ], {"H": hexc("c8ccd4"), "S": hexc("7e8592"), "D": hexc("4a505c"), "y": hexc("f08a24"), "Y": hexc("ffe27a")}),
    # 4 job master: a pickaxe crossed with a hammer handle
    ("job_master", [
        "..oooooooo..",
        ".oSSSSSSSSo.",
        "oSHHHHHHHSSo",
        "oSoooBBoooSo",
        ".o...BBo..o.",
        ".....oBBo...",
        "......oBBo..",
        ".......oBBo.",
        "........oBBo",
        ".........oBo",
        "..........o.",
        "............",
    ], {"S": hexc("8a94a6"), "H": hexc("d8dee8"), "B": hexc("9a6a3a")}),
    # 5 auction: a gavel and a gold coin
    ("auction", [
        "..oooo......",
        ".oBBBBo.....",
        "oBHBBBBo....",
        "oBBBBBBoo...",
        ".oBBBBoBBo..",
        "..oooo.oBBo.",
        ".......ooBBo",
        "..ooo....oBo",
        ".oGGGo....o.",
        "oGYGGGo.....",
        "oGGGgGo.....",
        ".ooooo......",
    ], {"B": hexc("8a5a32"), "H": hexc("c49a6c"), "G": hexc("f4cf4f"), "Y": hexc("fff3a6"), "g": hexc("b37a17")}),
    # 6 mailbox: an envelope with a red seal
    ("mailbox", [
        "............",
        "............",
        "oooooooooooo",
        "oWLWWWWWWLWo",
        "oWWLWWWWLWWo",
        "oWWWLWWLWWWo",
        "oWWWWRRWWWWo",
        "oWWWWRRWWWWo",
        "oWWWWWWWWWWo",
        "oLLLLLLLLLLo",
        "oooooooooooo",
        "............",
    ], {"W": hexc("f6f1e4"), "L": hexc("b8ae96"), "R": hexc("d83a3a")}),
    # 7 gatekeeper (dungeon warden): a gold key
    ("gatekeeper", [
        "..oooo......",
        ".oGGGGo.....",
        "oGYoooGo....",
        "oGo..oGo....",
        "oGo..oGo....",
        ".oGGGGGo....",
        "..oooGGGo...",
        ".....oGGGo..",
        "......oGGGo.",
        ".....oGoGGo.",
        ".....oo.oGo.",
        "........oo..",
    ], {"G": hexc("e8b83a"), "Y": hexc("fff3a6")}),
    # 8 arena master: crossed swords
    ("arena_master", [
        "o..........o",
        "oHo......oHo",
        ".oHo....oHo.",
        "..oHo..oHo..",
        "...oHooHo...",
        "....oHHo....",
        "....oHHo....",
        "...oBooBo...",
        "..oGBo.oBGo.",
        ".oBo.....oBo",
        "oBo.......oB",
        ".o.........o",
    ], {"H": hexc("dfe6f0"), "B": hexc("7a4a22"), "G": hexc("f4cf4f")}),
    # 9 hall of fame: a gold trophy
    ("hall_of_fame", [
        "oooooooooooo",
        "oGoYGGGGGoGo",
        "oGoGGGGGgoGo",
        ".ooGGGGGgoo.",
        "...oGGGGo...",
        "....oGgo....",
        ".....oo.....",
        ".....Go.....",
        "....oGGo....",
        "...oGGGgo...",
        "..oBBBBBBo..",
        "..oooooooo..",
    ], {"G": hexc("f4cf4f"), "Y": hexc("fff3a6"), "g": hexc("b37a17"), "B": hexc("5a3a20")}),
    # 10 stable master: a horseshoe
    ("stable_master", [
        "............",
        "..oo....oo..",
        ".oSSo..oSSo.",
        ".oSHo..oHSo.",
        "oSHo....oHSo",
        "oSSo....oSSo",
        "oSHo....oHSo",
        "oSSo....oSSo",
        ".oSHo..oHSo.",
        ".oSSSooSSSo.",
        "..oSSSSSSo..",
        "...oooooo...",
    ], {"S": hexc("8a94a6"), "H": hexc("dfe6f0")}),
    # 11 story character: a speech bubble
    ("story", [
        "............",
        "..oooooooo..",
        ".oWWWWWWWWo.",
        "oWWWWWWWWWWo",
        "oWWbWWbWWbWo",
        "oWWWWWWWWWWo",
        ".oWWWWWWWWo.",
        "..oooWWoooo.",
        "....oWo.....",
        "....oo......",
        "............",
        "............",
    ], {"W": hexc("f2f6ff"), "b": hexc("3a5aa8")}),
]

SIZE = 12


def build():
    atlas = Image.new("RGBA", (SIZE * len(ICONS), SIZE), (0, 0, 0, 0))
    for i, (name, rows, colors) in enumerate(ICONS):
        assert len(rows) == SIZE and all(len(r) == SIZE for r in rows), name
        pal = dict(BASE)
        pal.update(colors)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c = pal[ch]
                if c is not None:
                    atlas.putpixel((i * SIZE + x, y), c)
    return atlas


def main():
    os.makedirs(OUT, exist_ok=True)
    atlas = build()
    atlas.save(os.path.join(OUT, "npc_icons.png"))
    preview = Image.new("RGBA", (atlas.width * 8 + 8 * len(ICONS), SIZE * 8), (60, 56, 52, 255))
    for i in range(len(ICONS)):
        cell = atlas.crop((i * SIZE, 0, (i + 1) * SIZE, SIZE)).resize((SIZE * 8, SIZE * 8), Image.NEAREST)
        preview.alpha_composite(cell, (i * (SIZE * 8 + 8), 0))
    preview.save(os.path.join(HERE, "map_npc_icons_preview.png"))
    print("npc icons:", ", ".join(name for name, _, _ in ICONS))


if __name__ == "__main__":
    main()
