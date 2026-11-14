"""Icons of the twelve second classes (二次職): 480 skill icons and 12 class emblems, all 16x16 and original.

The skill icons are composed exactly like the six jobs' trees (tools/art/skill_tree.py: the job's frame, an effect
layer, a glyph and a badge, from the recipes of tools/jobs/class_icons.json written by tools/jobs/class_trees.py),
with two additions:

* glyphs the classes need and the jobs did not have (a scroll, a substitution log, kunai, shuriken, a mask, a katana,
  a scythe, a bell, a lantern, a tome, scales, a leaf, a bone), drawn here in the same letter format;
* the class's mark on the frame: gems of the class colour on the four rivets and a crest at the top of the frame, so a
  class skill is told from a skill of the job's own tree at a glance, and the two classes of a job from each other.

The emblems (textures/gui/job/<class>.png) are medallions like the jobs' (tools/art/skills.py emblem) in the class
colour with the class's glyph.

    python3 tools/art/class_skill_icons.py            # writes the icons and tools/art/class_skill_icons_preview.png
"""
from __future__ import annotations

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import palette as P  # noqa: E402
import skill_tree as st  # noqa: E402
import skills  # noqa: E402
from icon import render  # noqa: E402
from px import Canvas, Ramp, mix  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RECIPES = os.path.join(ROOT, "tools", "jobs", "class_icons.json")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui")

# class id -> (base job, colour of its mark)
CLASSES = {
    "guardian": ("knight", Ramp("0c1830", "1e3a6a", "2f5ea8", "4a86d8", "86b6f0", "d6e8ff")),
    "paladin": ("knight", Ramp("3a2a06", "7a5a10", "b8901c", "e6c03a", "fbe27a", "fff8d8")),
    "shinobi": ("assassin", Ramp("120a26", "2a1a52", "44308a", "6a52c0", "9a86e6", "dcd2ff")),
    "reaper": ("assassin", Ramp("1a0206", "4a0612", "7e0e22", "b01e36", "e0505e", "ffb0b0")),
    "sage": ("mage", Ramp("04222a", "0a4a58", "14788a", "28a8b8", "6ad8e0", "d4fbff")),
    "necromancer": ("mage", Ramp("0c1a06", "1e3a0e", "3a6418", "5e9428", "96c850", "e0f6b8")),
    "sniper": ("ranger", Ramp("1e1c08", "403c12", "6a6420", "948c34", "c2b85a", "f0eab0")),
    "spirit_archer": ("ranger", Ramp("04200e", "0a4a22", "167a3c", "2aaa5a", "66dc8a", "d0ffe0")),
    "bishop": ("priest", Ramp("2a2418", "5a4e34", "948260", "c8b68a", "ece0bc", "fffdf2")),
    "inquisitor": ("priest", Ramp("2a0c02", "5e1c04", "9a340a", "d25814", "f48a3a", "ffd2a0")),
    "sword_saint": ("spellblade", Ramp("141c26", "2e3c50", "4c627e", "7490b0", "a8c4de", "eef6ff")),
    "hexblade": ("spellblade", Ramp("200418", "4a0a3a", "7a1462", "aa2a8a", "d660b8", "ffc0ec")),
}

# the emblem glyph of each class (one of the objects below or of skill_tree's) and its theme
EMBLEM_GLYPH = {
    "guardian": ("tower", "STEEL"), "paladin": ("shield_cross", "HOLY"), "shinobi": ("shuriken", "STEEL"),
    "reaper": ("scythe", "SHADOW"), "sage": ("tome", "ARCANE"), "necromancer": ("skull", "VENOM"),
    "sniper": ("target", "STEEL"), "spirit_archer": ("leaf", "LIFE"), "bishop": ("bell", "HOLY"),
    "inquisitor": ("scales", "FIRE"), "sword_saint": ("katana", "STEEL"), "hexblade": ("sword", "BLOOD"),
}


def _shuriken() -> list[str]:
    """A four-bladed throwing star, drawn from its shape: pinwheel blades, a bright leading edge, a hole."""
    rows = []
    size = 11
    c = (size - 1) / 2
    for y in range(size):
        row = ""
        for x in range(size):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 360.0) % 90.0
            reach = 5.6 * (1.0 - a / 90.0) + 1.4
            if r < 1.0:
                row += "D"
            elif r <= reach:
                row += "W" if a < 14 and r > 1.8 else "S" if r < reach - 1.0 or a < 45 else "s"
            else:
                row += "."
        rows.append(row)
    return rows


# glyphs in skill_tree's letter format (M theme, A glow, W silver, X white, G gold, B wood, L leather, S steel,
# C glass, E linen, O bone, D dark, T stone, Z holy white, R blood, K job colour; lower case: darker)
EXTRA = {
    "scroll": [
        ".B........B.",
        "BBEEEEEEEEBB",
        "BBEeMMMMeEBB",
        "BBEEEEEEEEBB",
        "BBEeMMMeeEBB",
        "BBEEEEEEEEBB",
        "BBEeeMMMeEBB",
        ".B........B.",
    ],
    "log": [
        "..BBBBBBBBB.",
        ".BbbbbbbbbBO",
        "BBBBBBBBBBOE",
        "BbbBBbbBbBOE",
        "BBBBBBBBBBOE",
        "BbBBbbBBBBOE",
        ".BbbbbbbbbBO",
        "..BBBBBBBBB.",
    ],
    "kunai": [
        "..........XW",
        ".........SWS",
        "........SWS.",
        ".......SWS..",
        "......SWS...",
        ".....SSS....",
        "....LL......",
        "...LlL......",
        "..LlL.......",
        ".OO.........",
        "O..O........",
        ".OO.........",
    ],
    "shuriken": _shuriken(),
    "mask": [
        "..EEEEEEEE..",
        ".EEEEEEEEEE.",
        "EERREEEERREE",
        "EEDDEEEEDDEE",
        "EEDDEEEEDDEE",
        "EEEEEEEEEEEE",
        "EEEEEEEEEEEE",
        ".EEEERREEEE.",
        "..EERRRREE..",
        "...EEEEEE...",
    ],
    "katana": [
        "...........X",
        "..........WS",
        ".........WS.",
        "........WS..",
        ".......WS...",
        "......WS....",
        ".....WS.....",
        "...GWS......",
        "..GGG.......",
        "..LlG.......",
        ".LlL........",
        "Ll..........",
    ],
    "scythe": [
        "..SSSSSSS...",
        ".SWWWWWWWSS.",
        "SWS.....BSWS",
        "SS......B..S",
        "S......B....",
        "......B.....",
        ".....B......",
        "....B.......",
        "...B........",
        "..B.........",
        ".B..........",
        "B...........",
    ],
    "bell": [
        ".....GG.....",
        "....G..G....",
        "....GGGG....",
        "...GGGGGG...",
        "..GgGGGGGG..",
        "..GgGGGGGG..",
        "..GgGGGGGG..",
        ".GGgGGGGGGG.",
        "GGGGGGGGGGGG",
        ".....DD.....",
    ],
    "lantern": [
        "....SS....",
        "...S..S...",
        "..SSSSSS..",
        "..SAAAAS..",
        "..SAXAAS..",
        "..SAAAAS..",
        "..SAAAAS..",
        "..SSSSSS..",
        "....SS....",
    ],
    "tome": [
        ".MMMMMMMM",
        "MMGMMMMGM",
        "MMMMGGMMM",
        "MMMGAAGMM",
        "MMMMGGMMM",
        "MMGMMMMGM",
        "EEEEEEEEM",
        ".eeeeeeeE",
    ],
    "scales": [
        ".....GG.....",
        "GGGGGGGGGGGG",
        "G....GG....G",
        "G....GG....G",
        "GG...GG...GG",
        "GGG..GG..GGG",
        "gGGg.GG.gGGg",
        ".....GG.....",
        "....GGGG....",
        "...GGGGGG...",
    ],
    "leaf": [
        ".........MMM",
        ".......MMMMM",
        "......MMMMmM",
        ".....MMMMmMM",
        "....MMMMmMMM",
        "...MMMmMMMM.",
        "..MMMmMMMM..",
        "..MMmMMMM...",
        ".MMmMMMM....",
        ".MmMMM......",
        "Bm..........",
        "B...........",
    ],
    "bone": [
        "OO........OO",
        "OOO......OOO",
        ".OOOOOOOOOO.",
        ".OoOOOOOOoO.",
        "OOO......OOO",
        "OO........OO",
    ],
    "knight": [
        "......MM....",
        ".....MMM....",
        "...SSSSSS...",
        "..SWSSSSSS..",
        "..SWSSSSSS..",
        "..SDDDDDDS..",
        "..SSSSSSSS..",
        "..SSSsSSSS..",
        "...SSsSSS...",
        ".WWSSSSSSWW.",
        "WSSSSKKSSSSW",
        "SSSSKKKKSSSS",
    ],
}


def install() -> None:
    """Makes the class glyphs known to skill_tree's composer."""
    for name, rows in EXTRA.items():
        if name in st.OBJECTS or name in st.DRAWN:
            raise SystemExit(f"glyph {name} already exists in skill_tree")
        st.OBJECTS[name] = rows


def check(recipes: dict) -> list[str]:
    bad = []
    for cls, nodes in recipes.items():
        if cls not in CLASSES:
            bad.append(f"unknown class {cls}")
        for node in nodes:
            sid, kind, obj, fx, badge, theme = node[:6]
            flags = node[6:]
            if kind not in ("a", "p", "u"):
                bad.append(f"{sid}: kind {kind}")
            if obj != "-" and obj not in st.OBJECTS and obj not in st.DRAWN:
                bad.append(f"{sid}: unknown object {obj}")
            for name in fx.split("+"):
                if fx != "-" and name not in st.FX:
                    bad.append(f"{sid}: unknown effect {name}")
            if badge != "-" and badge not in st.BADGES and badge not in st.COUNTS:
                bad.append(f"{sid}: unknown badge {badge}")
            if theme not in st.THEMES:
                bad.append(f"{sid}: unknown theme {theme}")
            for flag in flags:
                if flag not in ("flip", "ghost"):
                    bad.append(f"{sid}: unknown flag {flag}")
    return bad


def mark(c: Canvas, ramp: Ramp) -> None:
    """The class's mark on an icon frame: gems on the four rivets and a crest in the middle of the top edge."""
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.put(x, y, ramp[5] if (x, y) == (1, 1) else ramp[4])
    for x in (6, 9):
        c.put(x, 0, ramp[2])
    for x in (7, 8):
        c.put(x, 0, ramp[4])
        c.put(x, 1, mix(c.get(x, 1), ramp[3], 0.85))


def class_icon(cls: str, node: list) -> Canvas:
    sid, kind, obj, fx, badge, theme = node[:6]
    job, ramp = CLASSES[cls]
    c = st.compose(sid, job, kind, obj, fx, badge, theme, " ".join(node[6:]))
    mark(c, ramp)
    return c


def emblem(cls: str) -> Canvas:
    """A medallion like the jobs' emblems: a rim in the job's metal, a field in the class colour, the class glyph."""
    job, ramp = CLASSES[cls]
    metal = skills.JOB_METAL[job]
    c = Canvas(16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.6:
                if d > 6.4:
                    lit = (x - 7.5) * -0.6 + (y - 7.5) * -0.8
                    c.put(x, y, ramp[4] if lit > 2 else metal[2] if lit < -2 else metal[3])
                else:
                    f = 0.75 - d / 9 + 0.15 * (1 - y / 15)
                    c.put(x, y, mix(ramp[0], ramp[2], max(0.0, min(1.0, f))))
    c.outline(metal[0])
    obj, theme = EMBLEM_GLYPH[cls]
    mats = st._mats(theme, job)
    rows = st.OBJECTS[obj]
    h = len(rows)
    w = max(len(r) for r in rows)
    sym = render([r.ljust(w, ".") for r in rows], mats, local="", size=16, offset=(2 + (12 - w) // 2,
                                                                                  2 + (12 - h) // 2),
                 edge=1.2, grad=0.6, base=3.4)
    for y in range(16):
        for x in range(16):
            if sym.a[y, x, 3] and math.hypot(x - 7.5, y - 7.5) <= 6.6:
                c.a[y, x] = sym.a[y, x]
    # the class gem at the bottom of the rim
    c.put(7, 15, ramp[4])
    c.put(8, 15, ramp[5])
    return c


def preview(path: str, icons: dict, emblems: dict, recipes: dict) -> None:
    from PIL import Image

    scale = 3
    cell = 16 * scale + 6
    cols = 20
    rows = len(recipes) * 2
    W = 70 + cols * cell
    H = rows * cell + 10
    im = Image.new("RGBA", (W, H), (24, 22, 30, 255))
    for i, (cls, nodes) in enumerate(recipes.items()):
        e = emblems[cls].image().resize((16 * scale, 16 * scale), Image.NEAREST)
        im.paste(e, (8, i * 2 * cell + 8), e)
        for j, node in enumerate(nodes):
            branch, k = divmod(j, 20)
            x = 70 + k * cell
            y = (i * 2 + branch) * cell + 5
            ic = icons[node[0]].image().resize((16 * scale, 16 * scale), Image.NEAREST)
            im.paste(ic, (x, y), ic)
    im.save(path)


_installed = False


def generate(with_preview: bool = True) -> int:
    """Writes the class icons and emblems (and the preview sheet); 1 if a recipe is wrong or two icons are equal."""
    global _installed
    if not _installed:
        install()
        _installed = True
    with open(RECIPES, encoding="utf-8") as f:
        recipes = json.load(f)
    problems = check(recipes)
    for p in problems:
        print("CHECK FAILED:", p)
    if problems:
        return 1
    icons = {}
    pixels = {}
    # the jobs' own icons first: a class icon must not repeat one of them either
    for job in st.JOBS:
        for n in st.nodes(job):
            pixels[st.icon(n[0]).a.tobytes()] = n[0]
    for cls, nodes in recipes.items():
        for node in nodes:
            c = class_icon(cls, node)
            key = c.a.tobytes()
            if key in pixels:
                print("CHECK FAILED: identical icons", node[0], pixels[key])
                return 1
            pixels[key] = node[0]
            icons[node[0]] = c
            c.save(os.path.join(OUT, "skill", f"{node[0]}.png"))
    emblems = {}
    for cls in CLASSES:
        e = emblem(cls)
        emblems[cls] = e
        e.save(os.path.join(OUT, "job", f"{cls}.png"))
    print(f"wrote {len(icons)} class skill icons and {len(emblems)} class emblems")
    if with_preview:
        path = os.path.join(HERE, "class_skill_icons_preview.png")
        preview(path, icons, emblems, recipes)
        print("preview:", os.path.relpath(path, ROOT))
    return 0


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    return generate(not args.no_preview)


if __name__ == "__main__":
    sys.exit(main())
