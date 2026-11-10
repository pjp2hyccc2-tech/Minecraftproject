#!/usr/bin/env python3
"""Build the field set pieces of アストリア and export them for the chunk generator.

    python3 tools/fields/build.py                       # build + export + check (cached terrain: ~1-2 min)
    python3 tools/fields/build.py --previews            # + render every piece into tools/fields/previews/
    python3 tools/fields/build.py --only shrine_capital --previews --no-export   # iterate on one piece

Pieces come from the modules in ``tools/fields/pieces/`` (``build() -> list[Piece]``, run in file-name order).
The terrain is the real field-map terrain (``probe.py`` runs the pure Java terrain); the output is deterministic.
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

from fields import blocks_ext  # noqa: E402,F401
from fields import connect  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "fieldpieces")


def modules():
    from fields import pieces as pkg
    return sorted(m.name for m in pkgutil.iter_modules(pkg.__path__) if not m.name.startswith("_"))


# which piece keeps a column two pieces write in (lower first; ties: build order)
RANK = {"waystone": 0, "bridge": 1, "border": 1, "signpost": 2, "scenery": 3, "road": 4, "flowers": 5}


def resolve_overlaps(pieces, log=print):
    """Where two pieces write in the same column the higher-ranked one keeps it (waystone shrines, then bridges and
    border markers, signposts, the set pieces, roadside works, flowers) and the other leaves that whole column to
    it, so a fence line stops at a bridge or a shrine instead of cutting through it, and no piece overwrites
    another one in the world."""
    import math as _m
    import numpy as np
    claimed = {}
    order = sorted(range(len(pieces)), key=lambda i: (RANK.get(pieces[i].kind, 3), i))
    for i in order:
        P = pieces[i]
        ixs, izs = np.nonzero((P.a > 0).any(axis=2))
        lost = {}
        gone = set()
        for ix, iz in zip(ixs.tolist(), izs.tolist()):
            key = (ix + P.X0, iz + P.Z0)
            owner = claimed.get(key)
            if owner is None:
                claimed[key] = P.id
            else:
                P.a[ix, iz, :] = 0
                gone.add(key)
                lost[owner] = lost.get(owner, 0) + 1
        if not gone:
            continue
        P.block_entities = {k: v for k, v in P.block_entities.items() if (k[0], k[2]) not in gone}
        P.fluid_ticks = {k for k in P.fluid_ticks if (k[0], k[2]) not in gone}
        P.probes = [k for k in P.probes if (k[0], k[2]) not in gone]
        P.entities = [e for e in P.entities
                      if (_m.floor(e["pos"][0]), _m.floor(e["pos"][2])) not in gone]
        log(f"  {P.id}: leaves {len(gone)} column(s) to " +
            ", ".join(f"{k} ({v})" for k, v in sorted(lost.items())))


def build_pieces(only=None, log=print):
    out = []
    for name in modules():
        mod = importlib.import_module(f"fields.pieces.{name}")
        if not hasattr(mod, "build"):
            continue
        t0 = time.time()
        made = mod.build(only=only)
        for P in made:
            if only and P.id not in only:
                continue
            if P.clipped_writes:
                log(f"  {P.id}: {P.clipped_writes} writes outside the box were dropped")
            out.append(P)
        log(f"{name}: {len(made)} pieces in {time.time() - t0:.1f}s")
    ids = [P.id for P in out]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        raise SystemExit(f"duplicate piece ids: {sorted(dup)}")
    resolve_overlaps(out, log)
    for P in out:
        connect.finalize(P)
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="", help="comma separated piece ids (implies --no-export unless --export)")
    ap.add_argument("--previews", action="store_true")
    ap.add_argument("--no-export", action="store_true")
    ap.add_argument("--export", action="store_true")
    ap.add_argument("--scale", type=float, default=1.0)
    args = ap.parse_args(argv)
    only = {s for s in args.only.split(",") if s} or None
    t0 = time.time()
    pieces = build_pieces(only)
    print(f"built {len(pieces)} pieces in {time.time() - t0:.1f}s")
    if (not only and not args.no_export) or args.export:
        from fields import check, export
        export.export(pieces, OUT)
        problems = check.check(OUT, pieces)
        if problems:
            for p in problems:
                print("CHECK:", p)
            return 1
        print("check: ok")
    if args.previews:
        from fields import render
        size = (int(960 * args.scale), int(600 * args.scale))
        shots = []
        for P in pieces:
            shots += render.render_piece(P, size=size)
        if not only:
            views = [s for s in shots if not s.endswith("_plan.png")]
            plans = [s for s in shots if s.endswith("_plan.png")]
            render.contact_sheet(views, "contact_sheet.png", title="ETERNAL REALM - field set pieces")
            render.contact_sheet(plans, "contact_sheet_plans.png", title="Field set pieces - plans")
            render.overview(pieces)
    return 0


if __name__ == "__main__":
    sys.exit(main())
