#!/usr/bin/env python3
"""Look at the guild gates in their settings without building the capital (development aid).

    python3 tools/estates/guild/gate_preview.py [theme,theme...] [--scale 0.5] [--night] [--side]

Builds every gate (or the listed ones) in a row in an empty ``World``: wall gates (street and upper wall) against a
six-block retaining wall with a terrace behind, free-standing gates on a floor with the terrace edge behind them.
Renders a front view per gate into /tmp/gates/<theme>.png (``--side``: a three-quarter view from the terrace above)
and a contact sheet /tmp/gates/sheet.png.
"""
from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..", "..")))

from PIL import Image  # noqa: E402

from capital import blocks as B  # noqa: E402
from capital import preview as P  # noqa: E402
from capital.districts import _gate_kit as GK  # noqa: E402
from capital.world import World  # noqa: E402

OUT = "/tmp/gates"
ORDER = ["northern_longhouse", "mushroom_grove", "dwarven_forge", "ancient_ruins", "pirate_ship",
         "flower_glasshouse", "great_tree", "sage_library", "japanese_mansion", "jungle_temple", "desert_palace",
         "observatory", "ice_castle", "arcane_tower", "volcanic_fortress", "sky_temple", "marble_palace",
         "gothic_cathedral", "dragon_lair", "coral_hall"]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("themes", nargs="?", default="")
    ap.add_argument("--scale", type=float, default=0.5)
    ap.add_argument("--night", action="store_true")
    ap.add_argument("--side", action="store_true")
    args = ap.parse_args(argv)
    themes = [t for t in args.themes.split(",") if t] or ORDER
    w = World()
    w.fill(-170, -3, -60, 170, -1, 60, B.S("stone"))
    w.fill(-170, 0, -60, 170, 0, 60, B.full("plaza_flagstones"))
    cams = {}
    for i, th in enumerate(themes):
        x = -150 + i * 16
        z = 0
        if th in GK.WALL:
            w.fill(x - 7, 1, z - 9, x + 7, 5, z + 2, B.S("stone"))
            w.fill(x - 7, 1, z + 2, x + 7, 5, z + 2, B.full("castle_bricks"))
            w.fill(x - 7, 6, z - 9, x + 7, 6, z + 2, B.full("plaza_flagstones"))
            for xx in range(x - 7, x + 8, 2):
                w.set(xx, 7, z + 2, B.wall("castle_bricks"), only_empty=True)
        else:
            w.fill(x - 7, 1, z - 8, x + 7, 8, z - 7, B.full("castle_bricks"))
        GK.gate(w, th, x, 0, z, "south")
        if args.side:
            cams[th] = dict(pos=(x + 9, 13, z + 11), look=(x, 6, z - 1), fov=60)
        else:
            cams[th] = dict(pos=(x + 4, 3.5, z + 14), look=(x, 6.5, z - 1), fov=64)
    os.makedirs(OUT, exist_ok=True)
    scene = P.Scene(w)
    sun = P.sun_dir(8, -150) if args.night else P.sun_dir(38, -130)
    paths = []
    old = P.OUT_DIR
    P.OUT_DIR = OUT
    try:
        for th, cam in cams.items():
            paths.append(P.render(scene, th, sun=sun, scale=args.scale, fog=2600, label=th,
                                  exposure=0.55 if args.night else 1.0, **cam))
    finally:
        P.OUT_DIR = old
    ims = [Image.open(p) for p in paths]
    if ims:
        cw, ch = ims[0].size
        cols = 4 if len(ims) > 3 else len(ims)
        rows = (len(ims) + cols - 1) // cols
        sheet = Image.new("RGB", (cw * cols, ch * rows), (0, 0, 0))
        for i, im in enumerate(ims):
            sheet.paste(im, ((i % cols) * cw, (i // cols) * ch))
        sheet.save(os.path.join(OUT, "sheet.png"))
        print("sheet ->", os.path.join(OUT, "sheet.png"))


if __name__ == "__main__":
    main()
