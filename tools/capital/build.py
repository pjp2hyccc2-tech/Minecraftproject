#!/usr/bin/env python3
"""Build the royal capital and export it for the Java loader.

    python3 tools/capital/build.py                 # build + export + check
    python3 tools/capital/build.py --previews      # also render tools/capital/previews/*.png
    python3 tools/capital/build.py --only terrain  # stop after a stage (terrain|castle|districts)
    python3 tools/capital/build.py --views plaza_north,great_hall   # render selected previews

Deterministic: no unseeded randomness; the same sources give byte-identical output.
District builders: every module ``tools/capital/districts/<name>.py`` that defines ``DISTRICT`` (a name in
``layout.DISTRICTS``) and ``build(world, district, terrain)`` is run in file-name order, clipped to its region.
"""
from __future__ import annotations

import argparse
import importlib
import os
import pkgutil
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from capital import layout as L  # noqa: E402
from capital import terrain  # noqa: E402
from capital.world import World  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "capital")


def run_districts(w, T, log=print):
    from capital import districts as pkg
    mods = sorted(m.name for m in pkgutil.iter_modules(pkg.__path__) if not m.name.startswith("_"))
    for name in mods:
        mod = importlib.import_module(f"capital.districts.{name}")
        dname = getattr(mod, "DISTRICT", None)
        if not dname or not hasattr(mod, "build"):
            continue
        d = L.DISTRICTS[dname]
        before = w.clipped_writes
        t0 = time.time()
        with w.district_clip(d):
            mod.build(w, d, T)
        log(f"district {dname} ({name}.py): {time.time() - t0:.1f}s, clipped writes {w.clipped_writes - before}")


def build_world(only=None, log=print) -> tuple:
    t0 = time.time()
    w = World()
    T = terrain.build(w)
    log(f"terrain: {time.time() - t0:.1f}s")
    if only == "terrain":
        return w, T
    from capital.castle import build as castle_build
    t1 = time.time()
    castle_build.build(w, T)
    log(f"castle: {time.time() - t1:.1f}s")
    if only == "castle":
        return w, T
    run_districts(w, T, log)
    for name, (pos, yaw) in L.DEFAULT_SPOTS.items():
        if name not in w.spots:
            w.spot(name, pos, yaw)
    return w, T


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", choices=["terrain", "castle", "districts"])
    ap.add_argument("--previews", action="store_true")
    ap.add_argument("--views", default="")
    ap.add_argument("--no-export", action="store_true")
    ap.add_argument("--scale", type=float, default=1.0, help="preview resolution scale")
    args = ap.parse_args(argv)
    w, T = build_world(args.only)
    for name, (pos, yaw) in L.DEFAULT_SPOTS.items():
        if name not in w.spots:
            w.spot(name, pos, yaw)
    if not args.no_export:
        from capital import export
        stats = export.export(w, OUT)
        from capital import check
        problems = check.check(OUT)
        if problems:
            for p in problems:
                print("CHECK:", p)
            return 1
        print("check: ok")
    if args.previews or args.views:
        from capital import preview
        names = [v for v in args.views.split(",") if v] or None
        preview.render_all(w, names, scale=args.scale)
    return 0


if __name__ == "__main__":
    sys.exit(main())
