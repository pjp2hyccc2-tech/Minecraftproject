#!/usr/bin/env python3
"""Build the twenty guild houses (docs/modules/guildhouse.md) and everything derived from them.

    python3 tools/estates/guild/build.py                 # build + export + check all twenty, sizes, thumbnails
    python3 tools/estates/guild/build.py great_tree      # only these
    python3 tools/estates/guild/build.py --no-thumbs     # skip the thumbnails (slow part)
    python3 tools/estates/guild/build.py --views great_tree_hall --scale 0.6

Exports through tools/estates (the same blueprints ``python3 tools/estates/build.py`` writes), then
* writes each design's measured size (width x depth of the island and buildings, height above the ground) into
  ``data/eternalrealm/guildhouse/designs.json`` (the catalogue the game and the screens read; widths must not shrink
  from a tier to the next),
* renders the 128 x 72 catalogue thumbnails ``textures/gui/guildhouse/<id>.png`` (ray-traced previews).
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..", "..")))

from capital import export, tidy  # noqa: E402
from capital import preview as P  # noqa: E402
from capital.world import X0, Y0, Z0, World  # noqa: E402

from estates import check, views  # noqa: E402
from estates.guild import houses  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "estates", "guild")
THUMBS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "guildhouse")


def measure(w):
    """Width (x), depth (z) of everything at or above the ground's underside (y >= -2), height above y = 0."""
    a = w.a
    solid = a[:, :, (-2 - Y0):] > 1
    cols = solid.any(axis=2)
    xs, zs = np.nonzero(cols)
    ys = np.nonzero(solid.any(axis=(0, 1)))[0]
    top = int(ys.max()) + (-2) if ys.size else 0
    return [int(xs.max() - xs.min() + 1), int(zs.max() - zs.min() + 1), max(1, top)]


def house_checks(w):
    """What the generic estate check does not know: every chest's lid can open (no solid block on it), the board
    stands in front of a wall with its 3 x 2 face free, the portal of the return gate is next to ``exit``."""
    from capital.districts._furnish import is_full
    from capital.world import DIR_VEC, left_of
    problems = []
    for (x, y, z), nbt in w.block_entities.items():
        st = w.get(x, y, z)
        if "er_slot" in nbt and ("chest" in st) and is_full(w.get(x, y + 1, z)):
            problems.append(f"chest at {(x, y, z)} cannot open: {w.get(x, y + 1, z)} on its lid")
    boards = [(p, st) for p, st in _states(w, "eternalrealm:guild_board")]
    if len(boards) != 1:
        problems.append(f"{len(boards)} guild boards")
    for (x, y, z), st in boards:
        facing = st.split("facing=")[1].split("]")[0].split(",")[0]
        bx, _, bz = DIR_VEC[facing]
        lx, _, lz = DIR_VEC[left_of(facing)]
        for k in (-1, 0, 1):
            for dy in (0, 1):
                cell = (x + lx * k, y + dy, z + lz * k)
                if cell != (x, y, z) and not w.is_empty(*cell):
                    problems.append(f"board face cell {cell} holds {w.get(*cell)}")
                if not is_full(w.get(cell[0] - bx, cell[1], cell[2] - bz)):
                    problems.append(f"no wall behind the board at {cell}: {w.get(cell[0] - bx, cell[1], cell[2] - bz)}")
    return problems


def _states(w, block_id):
    ids = [i for i, s in enumerate(w.palette) if s.split("[")[0] == block_id]
    if not ids:
        return []
    xs, zs, ys = np.nonzero(np.isin(w.a, ids))
    return [((int(x) + X0, int(y) + Y0, int(z) + Z0), w.palette[int(w.a[x, z, y])]) for x, z, y in zip(xs, zs, ys)]


def thumbnail(w, theme, tier, height):
    """A 128 x 72 picture of the house from the south-west, above the gate (supersampled 4x)."""
    from PIL import Image
    half = {1: 16, 2: 19, 3: 23, 4: 27, 5: 31}[tier]
    old = P.OUT_DIR
    P.OUT_DIR = "/tmp/guild_thumbs"
    try:
        scene = P.Scene(w)
        d = max(half * 1.35, height * 1.15)
        path = P.render(scene, f"thumb_{theme}", pos=(-d, height * 0.5 + 12, d * 0.78),
                        look=(3.0, height * 0.36, 0.0), size=(512, 288), fov=58, sun=P.sun_dir(30.0, 180.0),
                        fog=900)
    finally:
        P.OUT_DIR = old
    img = Image.open(path).convert("RGB").resize((128, 72), Image.LANCZOS)
    os.makedirs(THUMBS, exist_ok=True)
    img.save(os.path.join(THUMBS, f"{theme}.png"), optimize=True)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("designs", nargs="*")
    ap.add_argument("--no-thumbs", action="store_true")
    ap.add_argument("--views", default="")
    ap.add_argument("--scale", type=float, default=1.0)
    args = ap.parse_args(argv)
    with open(houses.CATALOGUE, encoding="utf-8") as f:
        catalogue = json.load(f)
    by_id = {d["id"]: d for d in catalogue["designs"]}
    wanted = args.designs or [d["id"] for d in catalogue["designs"]]
    problems = []
    for theme in wanted:
        w = World()
        storage = houses.BUILDERS[theme](w)
        tidy.tidy(w, log=lambda *a: None)
        out = os.path.join(OUT, theme)
        os.makedirs(out, exist_ok=True)
        export.export(w, out)
        problems += [f"{theme}: {p}" for p in check.check(out, storage, verbose=False)]
        problems += [f"{theme}: {p}" for p in house_checks(w)]
        size = measure(w)
        by_id[theme]["size"] = size
        print(f"{theme}: tier {by_id[theme]['tier']}, size {size}, storage {storage.summary()}")
        if not args.no_thumbs:
            thumbnail(w, theme, by_id[theme]["tier"], size[2])
        if args.views:
            views.render(w, "guild", theme, names=[v for v in args.views.split(",") if v], scale=args.scale)
    # widths must not shrink from one tier to the next (GuildHouseCatalogTest)
    for tier in range(2, 6):
        below = max(d["size"][0] for d in catalogue["designs"] if d["tier"] == tier - 1)
        for d in catalogue["designs"]:
            if d["tier"] == tier and d["size"][0] < below:
                problems.append(f"{d['id']} is narrower ({d['size'][0]}) than tier {tier - 1} ({below})")
    with open(houses.CATALOGUE, "w", encoding="utf-8") as f:
        f.write(_dump(catalogue))
    problems += houses.check({("guild", t): None for t in by_id})
    for p in problems:
        print("PROBLEM:", p)
    print("ok" if not problems else f"{len(problems)} problems")
    return 1 if problems else 0


def _dump(catalogue):
    """designs.json with one design per line (stable, readable diffs)."""
    lines = ["{", f'  "schema": {catalogue["schema"]},', '  "designs": [']
    rows = [json.dumps(d, ensure_ascii=False, separators=(", ", ": ")) for d in catalogue["designs"]]
    lines += ["    " + r + ("," if i < len(rows) - 1 else "") for i, r in enumerate(rows)]
    lines += ["  ]", "}"]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.exit(main())
