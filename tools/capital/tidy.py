"""Final clean-up pass over the built capital: decorations that lost what holds them up.

The castle and the districts are built by separate code paths that meet at their edges, and a later stage
can carve away the wall behind a banner or the ceiling above a lantern. In the game such blocks float until
a neighbour update makes them pop off, so this pass fixes them once, after everything is built:

* wall banners, wall torches, wall signs and ladders whose block behind is not solid move back onto the wall
  when it is just one block further, otherwise they are removed (with their block entity);
* hanging lanterns and chain columns that end in the air get a chain up to the ceiling when it is close
  (at most ``MAX_CHAIN`` blocks), otherwise the lantern stands on the block below or is removed;
* ground plants (grass, ferns, flowers, tall plants, petals) that do not stand on soil are removed
  (e.g. meadow flowers left on a viaduct deck), together with the upper half of tall plants.

Deterministic, and quiet when there is nothing to do.
"""
from __future__ import annotations

import numpy as np

from . import blocks as B
from .world import DIR_VEC, OPPOSITE, X0, Y0, Z0

MAX_CHAIN = 6

# blocks that cannot carry anything hung on or stood on them
_SOFT_SCHEMAS = {"lantern", "wall_torch", "banner", "wall_banner", "sign", "wall_sign", "hanging_sign",
                 "wall_hanging_sign", "candle", "button", "pressure_plate", "weighted_plate", "lever", "ladder",
                 "vine", "glow_lichen", "double_plant", "fluid", "light", "light_shaft", "smoke_emitter",
                 "fountain_spray", "pink_petals", "amethyst", "age15", "age3", "age7", "skull", "wall_skull",
                 "snow", "sea_pickle"}
_SOFT_IDS = {"air", "cave_air", "grass", "fern", "dead_bush", "dandelion", "poppy", "blue_orchid", "allium",
             "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
             "lily_of_the_valley", "torchflower", "azalea", "flowering_azalea", "spore_blossom", "lily_pad", "torch",
             "soul_torch", "cobweb", "moss_carpet", "flower_pot", "hanging_roots", "oak_sapling", "seagrass",
             "kelp_plant", "barrier"}
_PLANTS = {"grass", "fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
           "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
           "torchflower", "oak_sapling", "pink_petals", "sweet_berry_bush"}
_TALL_PLANTS = {"sunflower", "lilac", "rose_bush", "peony", "tall_grass", "large_fern", "pitcher_plant"}
_SOIL = {"grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "moss_block", "mud",
         "farmland", "muddy_mangrove_roots", "dirt_path"}
_WALL_ATTACHED = {"wall_banner", "wall_torch", "wall_sign", "ladder"}


def _short(bid: str) -> str:
    return bid.split(":", 1)[1]


PAINTING_SIZES = {"kebab": (1, 1), "aztec": (1, 1), "alban": (1, 1), "aztec2": (1, 1), "bomb": (1, 1),
                  "plant": (1, 1), "wasteland": (1, 1), "pool": (2, 1), "courbet": (2, 1), "sea": (2, 1),
                  "sunset": (2, 1), "creebet": (2, 1), "wanderer": (1, 2), "graham": (1, 2), "match": (2, 2),
                  "bust": (2, 2), "stage": (2, 2), "void": (2, 2), "skull_and_roses": (2, 2)}
_CCW = {0: (1, 0), 1: (0, 1), 2: (-1, 0), 3: (0, -1)}      # facing south/west/north/east -> counter-clockwise
_BACK = {0: (0, 1), 1: (-1, 0), 2: (0, -1), 3: (1, 0)}


def painting_holds(w, e) -> bool:
    """Whether the painting entity ``e`` would stay up: every block it covers free, a full block behind each
    (the rules check.py enforces on the export)."""
    from .districts import _furnish as FU
    nbt = e["nbt"]
    variant = nbt.split('variant:"minecraft:')[1].split('"')[0]
    facing = int(nbt.split("facing:")[1].split("b")[0])
    w_, h_ = PAINTING_SIZES.get(variant, (0, 0))
    if not w_:
        return False
    x, y, z = (int(np.floor(v)) for v in e["pos"])
    sx, sz = _CCW[facing]
    bx, bz = _BACK[facing]
    for k in range(w_):
        for l in range(h_):
            cx, cy, cz = x + sx * k, y + l, z + sz * k
            if not FU.is_free(w.get(cx, cy, cz)) or not FU.is_full(w.get(cx - bx, cy, cz - bz)):
                return False
    return True


def tidy(w, log=print) -> dict:
    pal = w.palette
    parsed = [B.parse(s) for s in pal]
    schema = [B.REGISTRY.get(bid, "plain") for bid, _ in parsed]
    soft = np.zeros(len(pal) + 1, bool)
    for i, (bid, _) in enumerate(parsed):
        if i == 0:
            continue  # void: whatever is in the world there stays, assume it holds
        soft[i] = schema[i] in _SOFT_SCHEMAS or _short(bid) in _SOFT_IDS or _short(bid).endswith("_carpet") \
            or _short(bid).startswith("potted_")
    soil = np.array([_short(bid) in _SOIL for bid, _ in parsed] + [False])
    air = w.idx("minecraft:air")
    a = w.a
    stats = {"moved": 0, "wall": 0, "chained": 0, "stood": 0, "lantern": 0, "plant": 0}
    where = {k: [] for k in stats}

    def at(x, y, z):
        return w.get_idx(x, y, z)

    def solid(x, y, z):
        return not soft[at(x, y, z)]

    def positions(pred):
        ids = np.array([i for i in range(len(pal)) if pred(i)], np.int64)
        if ids.size == 0:
            return []
        xs, zs, ys = np.nonzero(np.isin(a, ids))
        return [(int(x) + X0, int(y) + Y0, int(z) + Z0) for x, z, y in zip(xs, zs, ys)]

    def remove(x, y, z, kind):
        a[x - X0, z - Z0, y - Y0] = air
        w.block_entities.pop((x, y, z), None)
        stats[kind] += 1
        if len(where[kind]) < 8:
            where[kind].append((x, y, z))

    # 1. things hung on a wall
    for (x, y, z) in positions(lambda i: schema[i] in _WALL_ATTACHED):
        facing = parsed[at(x, y, z)][1].get("facing", "north")
        dx, dy, dz = DIR_VEC[OPPOSITE[facing]]
        if solid(x + dx, y + dy, z + dz):
            continue
        if at(x + dx, y, z + dz) == air and solid(x + 2 * dx, y, z + 2 * dz):
            a[x + dx - X0, z + dz - Z0, y - Y0] = a[x - X0, z - Z0, y - Y0]
            a[x - X0, z - Z0, y - Y0] = air
            nbt = w.block_entities.pop((x, y, z), None)
            if nbt is not None:
                w.block_entities[(x + dx, y, z + dz)] = nbt
            stats["moved"] += 1
            if len(where["moved"]) < 8:
                where["moved"].append((x, y, z))
            continue
        remove(x, y, z, "wall")

    # 2. hanging lanterns and chains that end in the air
    chain_id = w.idx(B.chain())

    def ceiling(x, y, z):
        """y of the first block above (x, y, z) that can carry a chain, if within MAX_CHAIN blocks of air."""
        for k in range(1, MAX_CHAIN + 2):
            i = at(x, y + k, z)
            if i == air:
                continue
            return y + k if not soft[i] else None
        return None

    def hang_top(x, y, z):
        """Top of the run of chains directly above (x, y, z)."""
        while at(x, y + 1, z) == chain_id:
            y += 1
        return y

    lanterns = positions(lambda i: schema[i] == "lantern" and parsed[i][1].get("hanging") == "true")
    for (x, y, z) in lanterns:
        top = hang_top(x, y, z)
        if solid(x, top + 1, z):
            continue
        c = ceiling(x, top, z)
        if c is not None:
            for yy in range(top + 1, c):
                a[x - X0, z - Z0, yy - Y0] = chain_id
            stats["chained"] += 1
            if len(where["chained"]) < 8:
                where["chained"].append((x, y, z))
            continue
        for yy in range(y + 1, top + 1):  # the dangling chain goes too
            a[x - X0, z - Z0, yy - Y0] = air
        if solid(x, y - 1, z):
            bid, props = parsed[at(x, y, z)]
            a[x - X0, z - Z0, y - Y0] = w.idx(B.fmt(bid, {**props, "hanging": "false"}))
            stats["stood"] += 1
            if len(where["stood"]) < 8:
                where["stood"].append((x, y, z))
        else:
            remove(x, y, z, "lantern")

    # 3. ground plants off the soil
    def is_plant(i):
        bid, props = parsed[i]
        s = _short(bid)
        return s in _PLANTS or (s in _TALL_PLANTS and props.get("half") == "lower")

    for (x, y, z) in positions(is_plant):
        below = at(x, y - 1, z)
        if below == 0 or soil[below]:
            continue
        s = _short(parsed[at(x, y, z)][0])
        remove(x, y, z, "plant")
        if s in _TALL_PLANTS and _short(parsed[at(x, y + 1, z)][0]) == s:
            a[x - X0, z - Z0, y + 1 - Y0] = air
    # orphaned upper halves
    for (x, y, z) in positions(lambda i: _short(parsed[i][0]) in _TALL_PLANTS and parsed[i][1].get("half") == "upper"):
        if _short(parsed[at(x, y - 1, z)][0]) != _short(parsed[at(x, y, z)][0]):
            remove(x, y, z, "plant")

    # 4. paintings that a later builder walled over or left hanging on nothing: the game would drop them as items
    stats["painting"] = 0
    where["painting"] = []
    keep = []
    for e in w.entities:
        if e["type"] == "minecraft:painting" and not painting_holds(w, e):
            stats["painting"] += 1
            if len(where["painting"]) < 8:
                where["painting"].append(tuple(e["pos"]))
            continue
        keep.append(e)
    w.entities[:] = keep

    if any(stats.values()):
        log("tidy: " + ", ".join(f"{k} {v}" for k, v in stats.items() if v)
            + "  e.g. " + "; ".join(f"{k} {where[k][:3]}" for k in stats if where[k]))
    return stats
