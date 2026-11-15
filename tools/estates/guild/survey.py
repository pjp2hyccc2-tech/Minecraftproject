#!/usr/bin/env python3
"""Walks through the guild houses the way a player would and reports what cannot be reached or looks broken.

    python3 tools/estates/guild/survey.py                 # all twenty
    python3 tools/estates/guild/survey.py dragon_lair -v  # one, with every finding

From the ``entry`` spot it floods every place a player can stand (two blocks of room, a floor below), walking,
stepping or jumping up one block (with room above the head), dropping down, climbing ladders, vines and scaffolding
and swimming. Then it reports
* features nobody can use: storage (chests, vaults, barrels, bookshelves), work stations, the board, the bell, beds,
  dummies, armour stands and frames out of reach of every place a player can stand,
* rooms nobody can enter: enclosed air under a roof, big enough to stand in, that the flood never touched,
* the exit: the return gate's portal must be reachable,
* loose blocks: small groups of solid blocks touching nothing else (debris left in the air).
"""
from __future__ import annotations

import argparse
import os
import sys
from collections import deque

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..", "..")))

from capital import tidy  # noqa: E402
from capital.world import X0, Y0, Z0, World  # noqa: E402

from estates.guild import houses  # noqa: E402

# what a body passes through (no collision, or a door or gate a player opens)
PASS_PREFIX = ("air", "cave_air", "void", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch",
               "redstone_wall_torch", "lever", "tripwire", "rail", "powered_rail", "detector_rail", "activator_rail",
               "redstone_wire", "light", "structure_void", "guild_portal", "nether_portal", "fire", "soul_fire")
PASS_SUFFIX = ("_button", "_sign", "_wall_sign", "_hanging_sign", "_wall_hanging_sign", "_banner", "_wall_banner",
               "_carpet", "_pressure_plate", "_sapling", "_flower", "_tulip", "_door", "_fence_gate", "_coral",
               "_coral_fan", "_coral_wall_fan", "_vines", "_roots", "_mushroom", "_head", "_skull")
PASS_EXACT = {"grass", "fern", "tall_grass", "large_fern", "dead_bush", "seagrass", "tall_seagrass", "kelp",
              "kelp_plant", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
              "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "sugar_cane", "vine",
              "ladder", "cobweb", "snow", "moss_carpet", "pink_petals", "spore_blossom", "glow_lichen", "sculk_vein",
              "hanging_roots", "small_dripleaf", "big_dripleaf_stem", "sweet_berry_bush", "lily_pad", "water", "lava",
              "bubble_column", "scaffolding", "string", "tripwire_hook", "nether_sprouts", "warped_roots",
              "crimson_roots", "twisting_vines", "weeping_vines", "twisting_vines_plant", "weeping_vines_plant",
              "cave_vines", "cave_vines_plant", "glow_berries", "torchflower", "pitcher_plant", "frogspawn",
              "end_rod", "lightning_rod", "chain", "candle", "white_candle", "pointed_dripstone"}
CLIMB = {"ladder", "vine", "scaffolding", "twisting_vines", "twisting_vines_plant", "weeping_vines",
         "weeping_vines_plant", "cave_vines", "cave_vines_plant"}
SWIM = {"water", "bubble_column", "kelp", "kelp_plant", "seagrass", "tall_seagrass"}
STATIONS = ("crafting_table", "furnace", "anvil", "chipped_anvil", "damaged_anvil", "smithing_table", "grindstone",
            "stonecutter", "loom", "cartography_table", "blast_furnace", "smoker", "brewing_stand", "fletching_table",
            "enchanting_table", "lectern", "barrel", "chest", "trapped_chest", "chiseled_bookshelf", "bell",
            "guild_board", "jukebox", "sorting_desk", "storage_chest", "composter", "cauldron", "water_cauldron")


def base(state: str) -> str:
    name = state.split("[")[0]
    return name.split(":", 1)[1] if ":" in name else name


def props(state: str) -> dict:
    if "[" not in state:
        return {}
    out = {}
    for kv in state.split("[", 1)[1].rstrip("]").split(","):
        if "=" in kv:
            k, v = kv.split("=", 1)
            out[k] = v
    return out


def passable(state: str) -> bool:
    b = base(state)
    if b in PASS_EXACT or b.startswith(PASS_PREFIX) or b.endswith(PASS_SUFFIX):
        return True
    if b.endswith("_trapdoor"):
        return props(state).get("open") == "true"      # an open trapdoor is a thin panel at the side
    if b.endswith("_candle") or b.endswith("candles"):
        return True
    return False


class Survey:
    def __init__(self, w: World, theme: str):
        self.w = w
        self.theme = theme
        pal = w.palette
        self.n_pass = np.array([passable(s) for s in pal], bool)
        self.n_climb = np.array([base(s) in CLIMB for s in pal], bool)
        self.n_swim = np.array([base(s) in SWIM or props(s).get("waterlogged") == "true" and passable(s)
                                for s in pal], bool)
        self.pas = self.n_pass[w.a]
        self.climb = self.n_climb[w.a]
        self.swim = self.n_swim[w.a]
        self.visited = np.zeros(w.a.shape, bool)

    def idx(self, x, y, z):
        return x - X0, z - Z0, y - Y0

    def ok(self, i, k, j):
        return 0 <= i < self.pas.shape[0] and 0 <= k < self.pas.shape[1] and 0 <= j < self.pas.shape[2] - 2

    def can_stand(self, i, k, j):
        """Feet in (i, k, j): room for the body, something to stand on (or water / a ladder to hold)."""
        if not (self.ok(i, k, j) and j >= 1 and self.pas[i, k, j] and self.pas[i, k, j + 1]):
            return False
        return (not self.pas[i, k, j - 1]) or self.climb[i, k, j] or self.swim[i, k, j] or self.swim[i, k, j - 1] \
            or self.climb[i, k, j - 1]

    def flood(self, start):
        i, k, j = self.idx(*start)
        q = deque()
        if self.can_stand(i, k, j):
            self.visited[i, k, j] = True
            q.append((i, k, j))
        while q:
            i, k, j = q.popleft()
            nbrs = []
            for di, dk in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ni, nk = i + di, k + dk
                if not self.ok(ni, nk, j):
                    continue
                if self.can_stand(ni, nk, j):
                    nbrs.append((ni, nk, j))
                    continue
                # a step or a jump up one block (room above the head first)
                if self.can_stand(ni, nk, j + 1) and self.pas[i, k, j + 2]:
                    nbrs.append((ni, nk, j + 1))
                    continue
                # walk off an edge and fall to the next floor (at most 24 blocks)
                if self.pas[ni, nk, j] and self.pas[ni, nk, j + 1]:
                    for dj in range(1, 25):
                        if j - dj < 1:
                            break
                        if self.can_stand(ni, nk, j - dj):
                            nbrs.append((ni, nk, j - dj))
                            break
                        if not self.pas[ni, nk, j - dj]:
                            break
            # climb or swim up and down
            if self.climb[i, k, j] or self.swim[i, k, j]:
                for dj in (1, -1):
                    if self.ok(i, k, j + dj) and self.can_stand(i, k, j + dj):
                        nbrs.append((i, k, j + dj))
            if self.climb[i, k, j + 1] and self.ok(i, k, j + 1) and self.can_stand(i, k, j + 1):
                nbrs.append((i, k, j + 1))
            for n in nbrs:
                if not self.visited[n]:
                    self.visited[n] = True
                    q.append(n)
        return int(self.visited.sum())

    def reachable_block(self, x, y, z, reach=4.0):
        """A block a player can use: within ``reach`` of the eyes (feet + 1.62) of some standing place, with a clear
        line of sight to the block's centre (the block itself may be anything)."""
        i, k, j = self.idx(x, y, z)
        r = int(np.ceil(reach)) + 1
        i0, i1 = max(0, i - r), min(self.visited.shape[0], i + r + 1)
        k0, k1 = max(0, k - r), min(self.visited.shape[1], k + r + 1)
        j0, j1 = max(0, j - r - 2), min(self.visited.shape[2], j + r)
        sub = np.argwhere(self.visited[i0:i1, k0:k1, j0:j1])
        if sub.size == 0:
            return False
        target = np.array([i + 0.5, k + 0.5, j + 0.5])
        feet = sub + np.array([i0, k0, j0])
        eyes = feet + np.array([0.5, 0.5, 1.62])
        d = np.linalg.norm(eyes - target, axis=1)
        for n in np.argsort(d):
            if d[n] > reach:
                break
            if self.sight(eyes[n], target, (i, k, j)):
                return True
        return False

    def sight(self, eye, target, cell):
        """Nothing solid between the eye and the target block's centre (the target itself and the cells the
        player's body is in do not count)."""
        v = target - eye
        steps = max(2, int(np.linalg.norm(v) / 0.2))
        for t in range(1, steps):
            p = eye + v * (t / steps)
            c = (int(np.floor(p[0])), int(np.floor(p[1])), int(np.floor(p[2])))
            if c == cell:
                return True
            if not self.pas[c]:
                return False
        return True

    def features(self):
        w = self.w
        out = []
        ids = [n for n, s in enumerate(w.palette) if base(s) in STATIONS or base(s).endswith("_bed")
               and props(s).get("part") == "head"]
        if ids:
            xs, zs, ys = np.nonzero(np.isin(w.a, ids))
            for i, k, j in zip(xs, zs, ys):
                out.append((base(w.palette[w.a[i, k, j]]), int(i) + X0, int(j) + Y0, int(k) + Z0))
        for e in w.entities:
            kind = e.get("id", e.get("type", "")).split(":")[-1] if isinstance(e, dict) else ""
            pos = e.get("pos") if isinstance(e, dict) else None
            if pos is None:
                continue
            if any(t in kind for t in ("armor_stand", "item_frame", "training_dummy", "glow_item_frame")):
                out.append((kind, int(np.floor(pos[0])), int(np.floor(pos[1])), int(np.floor(pos[2]))))
        return out

    def sealed_rooms(self, min_cells=10):
        """Air under a roof (something solid above within 12 blocks) where a body fits, never visited, grouped."""
        w = self.w
        stand = np.zeros_like(self.visited)
        pas = self.pas
        floor = ~pas
        # feet cells: pas here and above, solid below
        stand[:, :, 1:-1] = pas[:, :, 1:-1] & pas[:, :, 2:] & floor[:, :, :-2]
        # covered: some solid within 12 above the head
        covered = np.zeros_like(stand)
        acc = np.zeros(pas.shape[:2] + (pas.shape[2],), bool)
        for d in range(2, 14):
            shifted = np.zeros_like(pas)
            shifted[:, :, :-d] = floor[:, :, d:]
            acc |= shifted
        covered = acc
        # only inside the build's columns (not the void around): the island's columns have something below
        cand = stand & covered & ~self.visited
        # ignore columns outside every building: cells whose 3x3 surroundings at body height are all visited-free
        # and at the island's rim; group by 6-connectivity over the feet layer
        seen = np.zeros_like(cand)
        rooms = []
        xs, zs, ys = np.nonzero(cand)
        for i, k, j in zip(xs, zs, ys):
            if seen[i, k, j]:
                continue
            q = deque([(i, k, j)])
            seen[i, k, j] = True
            cells = []
            while q:
                a, b, c = q.popleft()
                cells.append((a, b, c))
                for da, db, dc in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    na, nb, nc = a + da, b + db, c + dc
                    if 0 <= na < cand.shape[0] and 0 <= nb < cand.shape[1] and 0 <= nc < cand.shape[2] \
                            and cand[na, nb, nc] and not seen[na, nb, nc]:
                        seen[na, nb, nc] = True
                        q.append((na, nb, nc))
            if len(cells) >= min_cells:
                arr = np.array(cells)
                lo = arr.min(axis=0)
                hi = arr.max(axis=0)
                rooms.append((len(cells), (int(lo[0]) + X0, int(lo[2]) + Y0, int(lo[1]) + Z0),
                              (int(hi[0]) + X0, int(hi[2]) + Y0, int(hi[1]) + Z0)))
        rooms.sort(reverse=True)
        return rooms

    def loose_blocks(self, max_size=6):
        """Groups of at most ``max_size`` blocks (anything but air: chains and lanterns hold each other) that touch
        no other block (6-connected): debris left floating."""
        from scipy import ndimage  # noqa: E402
        air = np.array([base(s) in ("air", "cave_air", "void") for s in self.w.palette], bool)
        filled = ~air[self.w.a]
        lab, n = ndimage.label(filled)
        sizes = ndimage.sum(filled, lab, range(1, n + 1))
        out = []
        for idx, size in enumerate(sizes, start=1):
            if size <= max_size:
                pos = np.argwhere(lab == idx)[0]
                state = self.w.palette[self.w.a[tuple(pos)]]
                out.append((int(size), (int(pos[0]) + X0, int(pos[2]) + Y0, int(pos[1]) + Z0), state))
        return out


def survey(theme, verbose=False):
    w = World()
    houses.BUILDERS[theme](w)
    tidy.tidy(w, log=lambda *a: None)
    s = Survey(w, theme)
    entry = w.spots["entry"]["pos"]
    count = s.flood(tuple(entry))
    problems = []
    feats = s.features()
    unreachable = [f for f in feats if not s.reachable_block(f[1], f[2], f[3])]
    for f in unreachable:
        problems.append(f"unreachable {f[0]} at {f[1:]}")
    exit_spot = w.spots["exit"]["pos"]
    ei, ek, ej = s.idx(*exit_spot)
    if not s.visited[ei - 1:ei + 2, ek - 1:ek + 2, ej - 1:ej + 2].any():
        problems.append(f"the exit {exit_spot} cannot be reached")
    rooms = s.sealed_rooms()
    for size, lo, hi in rooms[:12]:
        problems.append(f"sealed room of {size} cells {lo}..{hi}")
    try:
        loose = s.loose_blocks()
    except ImportError:
        loose = []
    for size, pos, state in loose[:12]:
        problems.append(f"loose {state} ({size}) at {pos}")
    print(f"{theme}: {count} standing places, {len(feats)} features, {len(unreachable)} unreachable, "
          f"{len(rooms)} sealed rooms, {len(loose)} loose groups")
    if verbose:
        for p in problems:
            print("   ", p)
    return problems


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("designs", nargs="*")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args(argv)
    themes = args.designs or list(houses.BUILDERS)
    total = 0
    for theme in themes:
        total += len(survey(theme, args.verbose))
    print(f"{total} findings")


if __name__ == "__main__":
    main()
