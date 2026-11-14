"""Icons of the places on the capital's map (12x12 each, one atlas row), drawn pixel by pixel.

python3 tools/art/capital_map_icons.py  ->  src/main/resources/assets/eternalrealm/textures/gui/map/capital_icons.png
                                            (and tools/art/capital_map_icons_preview.png, 8x)

The order is ``com.eternalrealm.world.map.CapitalMap.icons()`` (keep both in step). The town's NPCs keep the icons
they have on the minimap (tools/art/map_npc_icons.py); the arrival point, the goddess statue, the ranking board and
the castle's rooms get their own.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from map_npc_icons import BASE, ICONS as NPC_ICONS, SIZE, hexc  # noqa: E402

OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "map")

NEW = {
    # a blue flag with a golden star on a pole: where one arrives in the capital
    "arrival": ([
        "..oo........",
        ".oYYo.......",
        "..oPooooooo.",
        "..oPBBBBBBo.",
        "..oPBBWBBBo.",
        "..oPBWWWBBo.",
        "..oPBBWBBBo.",
        "..oPBBBBBbo.",
        "..oPooooooo.",
        "..oPo.......",
        ".ooPoo......",
        ".oooooo.....",
    ], {"Y": hexc("ffd75a"), "P": hexc("8a5a32"), "B": hexc("2f7fd8"), "b": hexc("1f5aa0"),
        "W": hexc("ffe27a")}),
    # the goddess statue: a winged marble figure under a golden halo, on its pedestal
    "goddess": ([
        "...oYYYYo...",
        "....oooo....",
        "....oWWo....",
        "oo..oWwo..oo",
        "oWo.oWwo.oWo",
        "oWWooWWooWWo",
        ".oWWWWWWWwo.",
        "..ooWWWwoo..",
        "...oWWWwo...",
        "..oWWWWWwo..",
        ".oGGGGGGGgo.",
        ".oooooooooo.",
    ], {"Y": hexc("ffe27a"), "W": hexc("f2f4fa"), "w": hexc("b8c0d8"), "G": hexc("9a9aa6"),
        "g": hexc("6a6a76")}),
    # the ranking board: a podium, gold in the middle, silver left, bronze right, a star above
    "ranking_board": ([
        ".....YY.....",
        "....YYYY....",
        "...oooooo...",
        "...oGHGGo...",
        "...oGGGGo...",
        "ooooGGGGo...",
        "oSSoGGGGoooo",
        "oSSoGGGGoBBo",
        "oSSoGGGDoBBo",
        "oSSoGGGDoBBo",
        "oSSoGGGDoBBo",
        "oooooooooooo",
    ], {"Y": hexc("fff0a0"), "G": hexc("f2c23a"), "H": hexc("fff3b0"), "D": hexc("b8862a"),
        "S": hexc("c8d0dc"), "B": hexc("c07a3a")}),
    # the castle gate: a gatehouse with battlements and its portcullis down
    "castle_gate": ([
        "ooo.oooo.ooo",
        "oSo.oSSo.oSo",
        "oSoooSSoooSo",
        "oSSSSSSSSSSo",
        "oSSSooooSSso",
        "oSSoDdDdoSso",
        "oSSoDdDdoSso",
        "oSSoddddoSso",
        "oSSoDdDdoSso",
        "oSSoDdDdoSso",
        "oSSoDdDdoSso",
        "oooooooooooo",
    ], {"S": hexc("b8bcc6"), "s": hexc("8a8e9a"), "D": hexc("2a2228"), "d": hexc("6a6e78")}),
    # the great hall: the royal banner, red with a golden crown, hanging from its rod
    "great_hall": ([
        "YooooooooooY",
        ".oRRRRRRRro.",
        ".oRRRRRRRro.",
        ".oRYRYYRYro.",
        ".oRYYYYYYro.",
        ".oRRRRRRRro.",
        ".oRRRRRRRro.",
        ".oRRRRRRRro.",
        ".oRRRooRRro.",
        ".oRRo..oRro.",
        ".oRo....oro.",
        ".oo......oo.",
    ], {"Y": hexc("ffd75a"), "R": hexc("c8303a"), "r": hexc("8a1e2c")}),
    # the throne room: the crown
    "throne": ([
        "............",
        ".o...oo...o.",
        "oYo.oYYo.oYo",
        "oYYoYYYYoYYo",
        "oYYYYYYYYYYo",
        "oYHYYYYYYYYo",
        "oYRYYBBYYRYo",
        "oYYYYYYYYYYo",
        "oDDDDDDDDDDo",
        "oooooooooooo",
        "............",
        "............",
    ], {"Y": hexc("f2c23a"), "H": hexc("fff3b0"), "D": hexc("b8862a"), "R": hexc("d8342c"),
        "B": hexc("3a6ad8")}),
    # the library: an open book
    "library": ([
        "............",
        "............",
        ".oooo..oooo.",
        "oWWWWooWWWWo",
        "oWllWooWllWo",
        "oWWWWooWWWWo",
        "oWllWooWllWo",
        "oWWWWooWWWWo",
        "oWllWooWllWo",
        "oWWWWooWWWWo",
        "oBBBBooBBBBo",
        ".oooo..oooo.",
    ], {"W": hexc("f2ead2"), "l": hexc("8a8070"), "B": hexc("7a3a2a")}),
    # the observatory: a domed tower with its slit open under a star
    "observatory": ([
        "........Y...",
        ".......YWY..",
        "........Y...",
        "....oooo....",
        "...oBBdBo...",
        "..oBBBdBbo..",
        ".oBBBBdBBbo.",
        ".oooooooooo.",
        ".oSSSSSSSso.",
        ".oSSoDDoSso.",
        ".oSSoDDoSso.",
        ".oooooooooo.",
    ], {"Y": hexc("ffe27a"), "W": hexc("ffffff"), "B": hexc("7a8ab8"), "b": hexc("4a5a8a"),
        "d": hexc("1a1a2a"), "S": hexc("c8c4b8"), "s": hexc("8a8678"), "D": hexc("3a2a22")}),
    # the chapel: its bell
    "chapel": ([
        ".....oo.....",
        "....oYYo....",
        "...oYYYYo...",
        "...oYHYYo...",
        "..oYHYYYDo..",
        "..oYHYYYDo..",
        "..oYYYYYDo..",
        ".oYYYYYYYDo.",
        "oYYYYYYYYYDo",
        "oDDDDDDDDDDo",
        ".ooooYYoooo.",
        ".....oo.....",
    ], {"Y": hexc("f2c23a"), "H": hexc("fff3b0"), "D": hexc("b8862a")}),
}

# the atlas: CapitalMap.icons() order; the NPCs' places take their minimap icon (place id -> npc icon)
ORDER = ["arrival", "goddess", "quest_board", "item_shop", "blacksmith", "job_master", "auction", "mailbox",
         "guild_registrar", "guild_estates", "gatekeeper", "arena_master", "hall_of_fame", "ranking_board", "inn",
         "stable", "castle_gate", "great_hall", "throne", "library", "observatory", "chapel", "story"]
FROM_NPC = {"inn": "innkeeper", "stable": "stable_master"}


def icon(name):
    if name in NEW:
        return NEW[name]
    npc = FROM_NPC.get(name, name)
    for icon_name, rows, colors in NPC_ICONS:
        if icon_name == npc:
            return rows, colors
    raise KeyError(name)


def build():
    atlas = Image.new("RGBA", (SIZE * len(ORDER), SIZE), (0, 0, 0, 0))
    for i, name in enumerate(ORDER):
        rows, colors = icon(name)
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
    atlas.save(os.path.join(OUT, "capital_icons.png"))
    preview = Image.new("RGBA", (atlas.width * 8 + 8 * len(ORDER), SIZE * 8), (60, 56, 52, 255))
    for i in range(len(ORDER)):
        cell = atlas.crop((i * SIZE, 0, (i + 1) * SIZE, SIZE)).resize((SIZE * 8, SIZE * 8), Image.NEAREST)
        preview.alpha_composite(cell, (i * (SIZE * 8 + 8), 0))
    preview.save(os.path.join(HERE, "capital_map_icons_preview.png"))
    print("capital map icons:", ", ".join(ORDER))


if __name__ == "__main__":
    main()
