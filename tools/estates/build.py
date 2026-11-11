#!/usr/bin/env python3
"""Build the estate designs and export them as blueprints for the Java loader (docs/modules/estates.md).

    python3 tools/estates/build.py                    # build + export + check every design
    python3 tools/estates/build.py room/garret        # only these designs
    python3 tools/estates/build.py --previews         # + previews in tools/estates/previews/
    python3 tools/estates/build.py --views garret_in --scale 0.5

Output: ``src/main/resources/data/eternalrealm/estates/<kind>/<design>/manifest.json`` + ``tiles/``, the same
format as the capital (tools/capital/export.py). Deterministic: the same sources give byte-identical output.
"""
from __future__ import annotations

import argparse
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import export, tidy  # noqa: E402
from capital.world import World  # noqa: E402

from estates import check, rooms  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "estates")

DESIGNS = dict(rooms.DESIGNS)


def build_design(kind, design, log=print):
    t0 = time.time()
    w = World()
    storage = DESIGNS[(kind, design)](w)
    tidy.tidy(w, log=lambda *a: None)
    log(f"{kind}/{design}: built in {time.time() - t0:.1f}s, storage {storage.summary()}")
    return w, storage


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("designs", nargs="*", help="kind/design to build (default: all)")
    ap.add_argument("--previews", action="store_true")
    ap.add_argument("--views", default="")
    ap.add_argument("--scale", type=float, default=1.0)
    ap.add_argument("--no-export", action="store_true")
    args = ap.parse_args(argv)
    wanted = [tuple(d.split("/", 1)) for d in args.designs] or list(DESIGNS)
    results = {}
    problems = []
    for kind, design in wanted:
        w, storage = build_design(kind, design)
        results[(kind, design)] = storage
        if not args.no_export:
            out = os.path.join(OUT, kind, design)
            os.makedirs(out, exist_ok=True)
            export.export(w, out)
            problems += [f"{kind}/{design}: {p}" for p in check.check(out, storage)]
        if args.previews or args.views:
            from estates import views
            views.render(w, kind, design, names=[v for v in args.views.split(",") if v] or None, scale=args.scale)
    if all(("room", tier) in results for tier in rooms.ROOM_TIERS):
        problems += rooms.check_storage(results)
    for p in problems:
        print("PROBLEM:", p)
    print("ok" if not problems else f"{len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
