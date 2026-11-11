"""House kit for the guild houses (docs/modules/guildhouse.md): what every house has, and the furniture and grounds
they are dressed with.

A design is built into an empty ``capital.world.World`` around the origin (local y = 0 is world y 100 in the estates
dimension; the late-afternoon sun shines from the west) and exported like the capital (``tools/estates/build.py``).

Every house has
* the **return gate**: the twin of its gate on the Guild Gate Terrace (``_gate_kit``), on the west side of the
  island facing east, filled with the animated portal of its theme. Spots ``exit`` (just in front of the portal)
  and ``entry`` (a few steps further, facing into the house); walking back into the portal leads home.
* the **guild board** (``eternalrealm:guild_board``, a 3 x 2 notice board on a wall; spot ``board``),
* **storage**: chests named ``chest.<n>`` (every member; tier + 1 of them) and trapped chests named ``vault.<n>``
  (the master and the officers; one, two from tier 4), plus trophy frames ``frame.<n>`` and armour stands
  ``stand.<n>`` from tier 3 - all moved by name when the guild switches designs (estates core),
* **training dummies** (``eternalrealm:training_dummy``: hit them to see your damage; they never fall and give no
  ranking credit), a **rally bell** (ringing it calls the members who are online), and **work stations** - more of
  everything in the higher tiers (``REQUIRED``).
"""
from __future__ import annotations

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.abspath(os.path.join(HERE, "..", ".."))
if TOOLS not in sys.path:
    sys.path.insert(0, TOOLS)

from capital import blocks as B  # noqa: E402
from capital import deco  # noqa: E402
from capital import shapes as S  # noqa: E402
from capital.districts import _furnish as FN  # noqa: E402
from capital.districts import _gate_kit as GK  # noqa: E402
from capital.districts import _town_kit as TK  # noqa: E402
from capital.noise import fbm2, hash2, hash3  # noqa: E402
from capital.world import DIR_VEC, OPPOSITE, YAW, Frame, left_of, right_of  # noqa: E402
from estates import kit as EK  # noqa: E402

AIR = "minecraft:air"
KIND = "guild"
F, ST, SL, WL = B.full, B.stairs, B.slab, B.wall
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}

# what a house of each tier must hold (checked by House.finish)
REQUIRED = {
    #     chests vaults dummies stations frames stands enchanting
    1: dict(chest=2, vault=1, dummy=1, stations=8, frame=0, stand=0, enchanting=False),
    2: dict(chest=3, vault=1, dummy=2, stations=10, frame=0, stand=0, enchanting=False),
    3: dict(chest=4, vault=1, dummy=3, stations=11, frame=4, stand=0, enchanting=True),
    4: dict(chest=5, vault=2, dummy=4, stations=12, frame=6, stand=2, enchanting=True),
    5: dict(chest=6, vault=2, dummy=5, stations=12, frame=8, stand=4, enchanting=True),
}
SOFT = {"grass", "fern", "tall_grass", "large_fern", "red_mushroom", "brown_mushroom", "dandelion", "poppy",
        "azure_bluet", "oxeye_daisy", "cornflower", "allium", "blue_orchid", "lily_of_the_valley", "dead_bush", "snow",
        "moss_carpet", "azalea", "flowering_azalea", "sweet_berry_bush", "pink_petals", "seagrass", "red_tulip",
        "white_tulip", "pink_tulip", "orange_tulip", "void"}
HALF = {1: 16, 2: 19, 3: 23, 4: 27, 5: 31}      # half the width of each tier's island; the gate is at x = -HALF + 6
STATION_IDS = ("crafting_table", "furnace", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
               "cartography_table", "blast_furnace", "smoker", "brewing_stand", "fletching_table", "enchanting_table")


class House:
    """One design being built: the world, its theme and tier, the storage names handed out and the features."""

    def __init__(self, w, theme: str):
        self.w = w
        self.theme = theme
        self.design = GK.DESIGNS[theme]
        self.tier = self.design["tier"]
        self.storage = EK.Storage()
        self.dummies = 0
        self.bells = 0
        self.boards = 0
        self.stations = set()
        self.enchanting = False

    # ------------------------------------------------------------------ storage
    def chest(self, t, x, y, z, facing="south"):
        """A members' chest (a vanilla chest, 27 slots) named chest.<n>; ``facing`` = the side its lid opens to."""
        name = self.storage.name("chest")
        t.set(x, y, z, B.chest(facing))
        t.be(x, y, z, "{" + EK.slot_nbt(name) + "}")
        return name

    def vault(self, t, x, y, z, facing="south"):
        """The officers' vault: a trapped chest named vault.<n> (only the master and the officers open it)."""
        name = self.storage.name("vault")
        t.set(x, y, z, B.S("trapped_chest", facing=facing, type="single"))
        t.be(x, y, z, "{" + EK.slot_nbt(name) + "}")
        return name

    def frame(self, t, x, y, z, facing, glow=False):
        """A trophy frame (frame.<n>) occupying (x, y, z) on the wall behind it."""
        return EK.frame(t, self.storage, x, y, z, facing, glow)

    def stand(self, t, x, y, z, yaw=0.0):
        """An armour stand (stand.<n>) on the floor block at y, for the guild's best gear."""
        return EK.stand(t, self.storage, x, y, z, yaw)

    # ------------------------------------------------------------------ the way in and out
    def gate(self, x=None, y=0, z=0, facing="east", arrive=4, mass=None, mass_half=None, mass_depth=8):
        """The return gate: the terrace gate of this theme with its portal plane centred on (x, z), floor top at y,
        front towards ``facing``. Wall gates (``_gate_kit.WALL``) stand in a six-block bank: ``mass`` (a state or
        a list mixed by noise) is filled behind the portal first. Sets ``exit`` and ``entry``. Returns the frame."""
        if x is None:
            x = -HALF[self.tier] + 6
        f = Frame(self.w, origin=(x, y, z), rot=GK.ROT[facing])
        if self.theme in GK.WALL and mass is not None:
            states = mass if isinstance(mass, (list, tuple)) else [mass]
            bank(f, half=mass_half or 9, depth=mass_depth, states=states, seed=sum(map(ord, self.theme)))
        old = GK.PLAQUE
        GK.PLAQUE = "house"
        try:
            GK.GATES[self.theme](f, self.theme)
        finally:
            GK.PLAQUE = old
        for dy in (1, 2):
            got = f.get(0, dy, 0)
            assert got.startswith("eternalrealm:guild_portal"), (self.theme, dy, got)
        front = YAW["south"]
        for k in range(1, arrive + 1):
            for dy in (1, 2):
                if not f.get(0, dy, k).startswith("minecraft:air"):
                    f.set(0, dy, k, AIR)
        f.spot("exit", (0, 1, 1), front)
        f.spot("entry", (0, 1, arrive), front)
        return f

    def board(self, t, x, y, z, facing):
        """The guild board: its block at (x, y, z) - the middle of the bottom row of a 3 x 2 free area on a wall
        behind it, at eye level (y = floor + 2) - ``facing`` into the room. Spot ``board`` in front, at the feet."""
        sx, sz = STEP[left_of(facing)]
        for k in (-1, 0, 1):
            for dy in (0, 1):
                t.set(x + sx * k, y + dy, z + sz * k, AIR)
        t.set(x, y, z, B.S("guild_board", facing=facing))
        fx, fz = STEP[facing]
        t.spot("board", (x + fx, y - 1, z + fz), YAW[OPPOSITE[facing]])
        self.boards += 1

    def bell(self, t, x, y, z, facing="north", attachment="ceiling"):
        """The rally bell: ringing it calls every member who is online (``attachment`` floor/ceiling/single_wall/
        double_wall like vanilla)."""
        t.set(x, y, z, B.S("bell", attachment=attachment, facing=facing, powered=False))
        self.bells += 1

    def dummy(self, t, x, y, z, yaw=0.0, look=0):
        """A training dummy standing on the floor block at y (``look`` 0 straw, 1 armoured, 2 painted target)."""
        t.entity("eternalrealm:training_dummy", (x + 0.5, y + 1.0, z + 0.5), yaw, f"{{Look:{int(look)}b}}")
        self.dummies += 1

    def target(self, t, x, y, z):
        """An archery target on a hay bale, standing on the floor block at y."""
        t.set(x, y + 1, z, B.S("hay_block", axis="y"))
        t.set(x, y + 2, z, B.S("target", power=0))

    def station(self, t, x, y, z, kind, facing="north"):
        """One work station at (x, y, z) (the block itself, not the floor)."""
        st = {
            "crafting_table": lambda: B.S("crafting_table"),
            "furnace": lambda: B.S("furnace", facing=facing, lit=False),
            "anvil": lambda: B.S("anvil", facing=CW[facing]),
            "smithing_table": lambda: B.S("smithing_table"),
            "grindstone": lambda: B.S("grindstone", face="floor", facing=facing),
            "stonecutter": lambda: B.S("stonecutter", facing=facing),
            "loom": lambda: B.S("loom", facing=facing),
            "cartography_table": lambda: B.S("cartography_table"),
            "blast_furnace": lambda: B.S("blast_furnace", facing=facing, lit=True),
            "smoker": lambda: B.S("smoker", facing=facing, lit=True),
            "brewing_stand": lambda: B.S("brewing_stand", has_bottle_0=True, has_bottle_1=False,
                                         has_bottle_2=True),
            "fletching_table": lambda: B.S("fletching_table"),
            "enchanting_table": lambda: B.S("enchanting_table"),
        }[kind]()
        t.set(x, y, z, st)
        self.stations.add(kind)

    def stations_row(self, t, cells, y, facing, kinds=None, seed=0):
        """Work stations along a row of floor cells (standing on y), facing ``facing`` (towards the users)."""
        kinds = list(kinds or STATION_IDS[:12])
        for (x, z), kind in zip(cells, kinds):
            self.station(t, x, y + 1, z, kind, facing)
        return len(list(zip(cells, kinds)))

    def enchanting_nook(self, t, cx, y, cz, open_side="south"):
        """A working enchanting table at (cx, y + 1, cz) ringed by bookshelves at distance two, two rows high and
        open one block towards ``open_side`` (30 shelves: full power); candles on the corner shelves."""
        self.station(t, cx, y + 1, cz, "enchanting_table")
        gx, gz = STEP[open_side]
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                ring = max(abs(dx), abs(dz)) == 2
                for dy in (1, 2):
                    if (dx, dz) == (0, 0):
                        continue
                    if not ring or (dx, dz) == (2 * gx, 2 * gz):
                        t.set(cx + dx, y + dy, cz + dz, AIR)
                    else:
                        t.set(cx + dx, y + dy, cz + dz, B.S("bookshelf"))
                if ring and abs(dx) == 2 and abs(dz) == 2:
                    t.set(cx + dx, y + 3, cz + dz, B.candle(3, "white"))
        self.enchanting = True

    def name_containers(self):
        """Every other container gets a storage name too (``furnace.<n>``, ``barrel.<n>``, ``brewer.<n>``,
        ``shelf.<n>``...): what players leave in a furnace or a barrel moves with the guild when it switches designs
        (the estates core moves storage by name), nothing is left behind unnamed."""
        import numpy as np
        from capital.world import X0, Y0, Z0
        kinds = {"barrel": "barrel", "furnace": "furnace", "blast_furnace": "furnace", "smoker": "furnace",
                 "brewing_stand": "brewer", "chiseled_bookshelf": "shelf", "jukebox": "jukebox", "hopper": "hopper",
                 "dispenser": "dispenser", "dropper": "dropper", "chest": "crate", "trapped_chest": "crate"}
        ids = {i: kinds[s.split("[")[0].split(":")[1]] for i, s in enumerate(self.w.palette)
               if s.split("[")[0].split(":")[1] in kinds or s.split("[")[0].endswith("shulker_box")}
        if not ids:
            return
        xs, zs, ys = np.nonzero(np.isin(self.w.a, list(ids)))
        cells = sorted(((int(y) + Y0, int(x) + X0, int(z) + Z0, ids[int(self.w.a[x, z, y])])
                        for x, z, y in zip(xs, zs, ys)))
        for (y, x, z, kind) in cells:
            nbt = self.w.block_entities.get((x, y, z), "")
            if EK.SLOT in nbt:
                continue
            name = self.storage.name(kind)
            body = nbt.strip()[1:-1].strip() if nbt.strip().startswith("{") else ""
            self.w.be(x, y, z, "{" + (body + "," if body else "") + EK.slot_nbt(name) + "}")

    def finish(self):
        """Check the house holds what its tier promises; returns the storage (for the exporter's checks)."""
        self.name_containers()
        need = REQUIRED[self.tier]
        got = self.storage.summary()
        problems = []
        for kind in ("chest", "vault", "frame", "stand"):
            if got.get(kind, 0) < need[kind]:
                problems.append(f"{kind}: {got.get(kind, 0)} < {need[kind]}")
        if got.get("chest", 0) != need["chest"] or got.get("vault", 0) != need["vault"]:
            problems.append(f"chests/vaults must be exactly {need['chest']}/{need['vault']}: {got}")
        if self.dummies < need["dummy"]:
            problems.append(f"dummies {self.dummies} < {need['dummy']}")
        if len(self.stations - {"enchanting_table"}) < need["stations"]:
            problems.append(f"stations {sorted(self.stations)} < {need['stations']}")
        if need["enchanting"] and not self.enchanting:
            problems.append("no enchanting nook")
        if self.boards != 1:
            problems.append(f"boards: {self.boards}")
        if self.bells < 1:
            problems.append("no rally bell")
        for spot in ("entry", "exit", "board"):
            if spot not in self.w.spots:
                problems.append(f"missing spot {spot}")
        for name, sp in self.w.spots.items():                 # plants sown over a spot are pulled up again
            x, y, z = (int(math.floor(v)) for v in sp["pos"])
            for yy in (y, y + 1):
                st = self.w.get(x, yy, z)
                if st.split("[")[0].split(":")[1] in SOFT:
                    self.w.set(x, yy, z, AIR)
        if problems:
            raise AssertionError(f"{self.theme}: " + "; ".join(problems))
        self.w.idx(B.chain())       # capital.tidy looks the chain up after reading the palette: have it there
        return self.storage


# ====================================================================================== furniture
def storage_wall(h, t, cells, y, facing, vaults=0, between=None, seed=0):
    """Chests on every other cell of ``cells`` (floor y), lids towards ``facing``; the cells between hold
    ``between`` (a state, e.g. a lantern on a slab) so no two chests join. The first ``vaults`` are vaults."""
    names = []
    for i, (x, z) in enumerate(cells):
        if i % 2 == 0:
            if vaults > 0:
                names.append(h.vault(t, x, y + 1, z, facing))
                vaults -= 1
            else:
                names.append(h.chest(t, x, y + 1, z, facing))
        elif between:
            t.set(x, y + 1, z, between)
    return names


def lounge(t, x, y, z, facing, wood="spruce", carpet="red", border="yellow", length=3, seed=0):
    """Two benches facing each other over a rug and a low table; ``facing`` = the way the first bench's sitters
    look; (x, y, z) = the floor block under the first seat."""
    sx, sz = STEP[right_of(facing)]
    fx, fz = STEP[facing]
    for k in range(length):
        t.set(x + sx * k, y + 1, z + sz * k, B.stairs(wood, OPPOSITE[facing]))
        t.set(x + sx * k + fx * 3, y + 1, z + sz * k + fz * 3, B.stairs(wood, facing))
    for k in range(length):
        for d in (1, 2):
            t.set(x + sx * k + fx * d, y + 1, z + sz * k + fz * d, B.carpet(border if k in (0, length - 1)
                                                                             else carpet))
    mid = length // 2
    t.set(x + sx * mid + fx, y + 1, z + sz * mid + fz, B.slab(wood, "bottom"))
    t.set(x + sx * mid + fx * 2, y + 1, z + sz * mid + fz * 2, B.slab(wood, "bottom"))
    FN.goods(t, x + sx * mid + fx, y + 2, z + sz * mid + fz, seed=seed, kinds=("candle", "potted_fern",
                                                                               "flower_pot"))


def council(t, x, y, z, length, axis="x", top="dark_oak", cloth="red", chair="spruce", seed=0):
    """The council table: a long table with chairs on both sides, a chair at each end and candles."""
    deco.long_table(t, x, y, z, length, axis=axis, top=top, cloth=cloth, chairs=True, chair_wood=chair, seed=seed)
    ax = (1, 0) if axis == "x" else (0, 1)
    head = {(1, 0): ("west", "east"), (0, 1): ("north", "south")}[ax]
    t.set(x - ax[0], y + 1, z - ax[1], B.stairs(chair, head[1]))
    t.set(x + ax[0] * length, y + 1, z + ax[1] * length, B.stairs(chair, head[0]))


def hearth(t, x, y, z, facing, stone="stone_bricks", chimney_top=None, soul=False):
    """A fireplace built into the wall at (x, z) (the wall line), opening towards ``facing``: a campfire in a
    three-wide stone surround with a mantel (candles), the chimney rising to ``chimney_top``."""
    sx, sz = STEP[right_of(facing)]
    fx, fz = STEP[facing]
    for k in (-1, 0, 1):
        for yy in range(y + 1, y + 4):
            t.set(x + sx * k, yy, z + sz * k, F(stone))
    t.set(x, y + 1, z, B.S("soul_campfire" if soul else "campfire", lit=True, facing=facing))
    t.set(x, y + 2, z, AIR)
    for k in (-1, 0, 1):
        t.set(x + sx * k + fx, y + 4, z + sz * k + fz, SL(stone, "top") if B.has_stairs(stone) else F(stone))
    t.set(x + sx + fx, y + 5, z + sz + fz, B.candle(2, "white"))
    t.set(x - sx + fx, y + 5, z - sz + fz, B.candle(3, "white"))
    if chimney_top:
        for yy in range(y + 4, chimney_top + 1):
            t.set(x, yy, z, F(stone))
        t.set(x, chimney_top + 1, z, B.S("smoke_emitter", heavy=False))


def kitchen_run(t, cells, y, wall_facing, seed=0, extra=()):
    """A kitchen along a wall: smoker, cauldron, barrels, a worktop, pots above (FN.kitchen on any cells)."""
    pieces = [B.S("smoker", facing=OPPOSITE[wall_facing], lit=True), B.S("water_cauldron", level=2),
              B.barrel("up"), B.slab("spruce", "top"), B.barrel(OPPOSITE[wall_facing]), B.S("composter", level=4)]
    pieces += list(extra)
    for i, (x, z) in enumerate(cells):
        st = pieces[(i + seed) % len(pieces)]
        t.set(x, y + 1, z, st)
        if "slab" in st or "barrel" in st:
            FN.goods(t, x, y + 2, z, seed + i, kinds=("flower_pot", "candle", "decorated_pot", "potted_fern"))


def trophy_wall(h, t, cells, y, facing, glow=False):
    """Trophy frames (frame.<n>) along a wall at height y, facing out of the wall."""
    return [h.frame(t, x, y, z, facing, glow) for (x, z) in cells]


def armory(h, t, cells, y, yaw):
    """Armour stands (stand.<n>) on the floor at y."""
    return [h.stand(t, x, y, z, yaw) for (x, z) in cells]


def training_ground(h, t, cells, y, yaw, looks=(0, 1, 2)):
    """Training dummies on the given floor cells (standing on y), facing ``yaw``."""
    for i, (x, z) in enumerate(cells):
        h.dummy(t, x, y, z, yaw, looks[i % len(looks)])


def weapon_rack(t, x, y, z, facing, items=("minecraft:wooden_sword", "minecraft:bow", "minecraft:iron_axe")):
    """Fixed item frames with practice weapons on a solid wall, along it (facing out of the wall)."""
    sx, sz = STEP[right_of(facing)]
    for k, item in enumerate(items):
        if t.is_empty(x + sx * k, y, z + sz * k):
            deco.item_frame(t, x + sx * k, y, z + sz * k, facing, item)


def crest_banner(t, x, y, z, facing, color, patterns):
    """A wall banner in the house's colours."""
    t.set(x, y, z, B.wall_banner(color, facing))
    t.be(x, y, z, B.banner_nbt(patterns))


def lamp(t, x, y, z, soul=False, chain=0):
    for k in range(chain):
        t.set(x, y + 1 + k, z, B.chain())
    t.set(x, y, z, B.lantern(hanging=True, soul=soul))


def light_grid(t, x0, z0, x1, z1, y, every=4, soul=False, chain=0, state=None):
    """Hanging lanterns (or ``state``) under a ceiling: the light at y, chains above."""
    for x in range(x0 + 1, x1, every):
        for z in range(z0 + 1, z1, every):
            if t.is_empty(x, y, z):
                for k in range(chain):
                    t.set(x, y + 1 + k, z, B.chain())
                t.set(x, y, z, state or B.lantern(hanging=True, soul=soul))


def windows_line(t, a0, a1, c, y0, h, axis="x", every=3, glass=None, skip=()):
    """Window openings along a wall: for axis x the wall is the plane z = c spanning x = a0..a1."""
    g = glass or B.pane()
    for a in range(a0, a1 + 1, every):
        if a in skip:
            continue
        for yy in range(y0, y0 + h):
            if axis == "x":
                t.set(a, yy, c, g)
            else:
                t.set(c, yy, a, g)


def door(t, x, y, z, facing, wood="spruce", hinge="left", open_=False):
    """A vanilla door standing on the floor block at y (lower half at y + 1)."""
    lo, hi = B.door_states(wood, facing, hinge, open_)
    t.set(x, y + 1, z, lo)
    t.set(x, y + 2, z, hi)


def clear(t, x0, y0, z0, x1, y1, z1):
    t.fill(min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1), AIR)


def mix(t, x0, y0, z0, x1, y1, z1, states, seed=0):
    GK.mixfill(t, min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1), list(states), seed)


# ====================================================================================== grounds
def bank(f, half=9, depth=8, states=("stone", "andesite", "cobblestone"), top=("grass_block", "grass_block",
         "moss_block", "coarse_dirt"), seed=0, core=4):
    """A natural rock bank for a wall gate, in the gate's frame: a cliff face in the plane z = 2 for |x| <= core
    (where the gate is cut in), level at y = 6 there; beyond it the face recedes and the top falls away in an
    irregular slope to the ground. Grassy top."""
    for lx in range(-half, half + 1):
        out = max(0, abs(lx) - core)
        for lz in range(-depth, 3):
            n = fbm2(lx, lz, 4.0, 2, seed)
            face = 2 - int(out * 0.6 + max(0.0, n) * 2) if out else 2
            if lz > face:
                continue
            back = lz + depth
            h = 6 - int(out * 0.55 + abs(n) * 1.5) if out else 6
            h = min(h, 2 + back * 2) if back < 2 else h
            if h < 1:
                continue
            for yy in range(1, h + 1):
                st = states[int(hash3(lx, yy, lz, seed) * len(states)) % len(states)]
                f.set(lx, yy, lz, B.S(st) if "[" not in st else st)
            if out or lz < 0:
                t_ = top[int(hash2(lx, lz, seed + 1) * len(top)) % len(top)]
                f.set(lx, h, lz, B.S(t_))


def island(w, cx, cz, rx, rz, top=0, depth=16, surface=("grass_block",), soil="dirt",
           rock=("stone", "andesite", "tuff", "stone", "cobblestone"), seed=1, rough=0.25, shape=None):
    """A floating island: a noisy ellipse (or any ``shape(x, z) -> distance`` <= 1 inside) whose top is y = ``top``
    (``surface`` blocks), two layers of ``soil`` below and a rocky underside tapering to a point about ``depth``
    blocks down. Returns the set of surface cells."""
    cells = set()
    R = int(max(rx, rz) * 1.35) + 3
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            if shape:
                d = shape(x, z)
            else:
                ex, ez = (x - cx) / rx, (z - cz) / rz
                d = math.sqrt(ex * ex + ez * ez)
            d += fbm2(x, z, 6.0, 3, seed) * rough
            if d > 1.0:
                continue
            cells.add((x, z))
            bottom = top - 3 - int(depth * (1.0 - d) ** 0.6 + hash2(x, z, seed + 3) * 3)
            for y in range(bottom, top + 1):
                if y == top:
                    st = surface_at(surface, x, z, seed)
                elif y >= top - 2 and soil:
                    st = B.S(soil)
                else:
                    st = B.S(rock[int(hash3(x, y, z, seed) * len(rock)) % len(rock)])
                w.set(x, y, z, st)
    return cells


def surface_at(surface, x, z, seed=0, patch=0.2):
    """The ground block at (x, z): ``surface[0]`` with patches of the others (low-frequency noise, not speckles)."""
    st = surface[0]
    if len(surface) > 1 and fbm2(x, z, 7.0, 2, seed + 5) > patch:
        k = fbm2(z + 31, x - 17, 5.0, 2, seed + 6) * 0.5 + 0.5
        st = surface[1 + min(len(surface) - 2, max(0, int(k * (len(surface) - 1))))]
    return B.S(st) if "[" not in st and ":" not in st else st


def drip_stalactites(w, cells, top, seed=0, kinds=("stone", "andesite", "cobblestone"), chance=0.07):
    """Rocky icicles hanging under an island's edge (for silhouettes seen from below)."""
    for (x, z) in sorted(cells):
        if hash2(x, z, seed + 77) < chance:
            y = top - 4
            while y > -40 and not w.is_empty(x, y, z):
                y -= 1
            n = 1 + int(hash2(z, x, seed + 78) * 3)
            for k in range(n):
                w.set(x, y - k, z, B.S(kinds[k % len(kinds)]))


def scatter_plants(w, cells, y, seed=0, kinds=("grass", "fern", "dandelion", "poppy", "azure_bluet"),
                   density=0.25, on=("grass_block",)):
    for (x, z) in sorted(cells):
        below = w.get(x, y, z)
        if w.is_empty(x, y + 1, z) and any(o in below for o in on) and hash2(x, z, seed) < density:
            k = kinds[int(hash2(z, x, seed + 1) * len(kinds)) % len(kinds)]
            w.set(x, y + 1, z, B.S(k))


def path(w, pts, y, states, width=3, seed=0):
    """A paved path through the points (straight segments), ``width`` wide, on the ground at y."""
    hw = width // 2
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(z1 - z0), 1)
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            z = round(z0 + (z1 - z0) * i / n)
            for dx in range(-hw, hw + 1):
                for dz in range(-hw, hw + 1):
                    st = states[int(hash2(x + dx, z + dz, seed) * len(states)) % len(states)]
                    w.set(x + dx, y, z + dz, B.S(st) if "[" not in st and ":" not in st else st)
                    above = w.get(x + dx, y + 1, z + dz)
                    if any(p in above for p in ("grass", "fern", "flower", "poppy", "dandelion", "bluet", "tulip",
                                                "daisy", "petals", "orchid", "allium", "cornflower")):
                        w.set(x + dx, y + 1, z + dz, AIR)


def pond(w, cx, cz, rx, rz, y, border=("mossy_cobblestone", "stone"), depth=2, seed=0, lily=0.08):
    """A pond whose water surface is y: water down to y - depth + 1, a stony border at y."""
    R = int(max(rx, rz)) + 2
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            d = math.hypot((x - cx) / rx, (z - cz) / rz) + fbm2(x, z, 5.0, 2, seed) * 0.15
            if d <= 1.0:
                for yy in range(y - depth + 1, y + 1):
                    w.set(x, yy, z, B.S("water"))
                w.set(x, y - depth, z, B.S("clay") if hash2(x, z, seed) < 0.5 else B.S("gravel"))
                w.set(x, y + 1, z, AIR)
                if hash2(x, z, seed + 9) < lily and d < 0.8:
                    w.set(x, y + 1, z, B.S("lily_pad"))
            elif d <= 1.0 + 1.6 / max(rx, rz):
                w.set(x, y, z, B.S(border[int(hash2(x, z, seed + 2) * len(border)) % len(border)]))


def tree(w, x, y, z, kind="oak", seed=0, height=None, crown=None):
    """A tree standing on the ground block at y: oak-like kinds (oak, birch, cherry, dark_oak, acacia, jungle,
    mangrove, azalea) or spruce / cypress."""
    if kind == "spruce":
        deco.tree_spruce(w, x, y, z, seed=seed, height=height)
    elif kind == "cypress":
        deco.tree_cypress(w, x, y, z, seed=seed, height=height)
    else:
        log = {"azalea": "oak", "flowering_azalea": "oak"}.get(kind, kind)
        deco.tree_oak(w, x, y, z, seed=seed, height=height, crown=crown, kind=kind, log_kind=log)


def palm(w, x, y, z, height=7, seed=0, lean=(1, 0)):
    """A palm: a leaning trunk of jungle logs and a crown of drooping fronds."""
    cx, cz = x, z
    top = y + height
    for k in range(1, height + 1):
        if k > height // 2 and hash2(k, seed, 5) < 0.6:
            cx += lean[0] if k % 2 else 0
            cz += lean[1] if k % 2 else 0
        w.set(cx, y + k, cz, B.log("jungle", "y"))
    leaf = B.leaves("jungle")
    w.set(cx, top + 1, cz, leaf)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        for r in (1, 2, 3):
            yy = top + (1 if r == 1 else 0) - (1 if r == 3 else 0)
            if abs(dx) + abs(dz) == 2 and r == 3:
                continue
            w.set(cx + dx * r, yy, cz + dz * r, leaf)


def rock(w, x, y, z, r=1.6, states=("stone", "andesite", "mossy_cobblestone"), seed=0):
    """A boulder sitting on the ground at y."""
    GK.ball(w, x, y + int(r * 0.5), z, r, B.S(states[0]), ry=r * 0.8, seed=seed, rough=0.6)
    for xx in range(int(x - r - 1), int(x + r + 2)):
        for yy in range(y, int(y + r + 2)):
            for zz in range(int(z - r - 1), int(z + r + 2)):
                if w.get(xx, yy, zz) == B.S(states[0]) and hash3(xx, yy, zz, seed) < 0.45:
                    w.set(xx, yy, zz, B.S(states[int(hash3(zz, yy, xx, seed) * len(states)) % len(states)]))


def fence_line(w, pts, y, state):
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(z1 - z0), 1)
        for i in range(n + 1):
            w.set(round(x0 + (x1 - x0) * i / n), y + 1, round(z0 + (z1 - z0) * i / n), state)


def rim(w, cells, y, state, every=1, seed=0):
    """A border (fence, wall, hedge) on the outermost surface cells of an island at y + 1."""
    out = []
    for (x, z) in sorted(cells):
        if any((x + dx, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out.append((x, z))
    for i, (x, z) in enumerate(out):
        if w.is_empty(x, y + 1, z) and (every == 1 or hash2(x, z, seed) < 1.0 / every):
            w.set(x, y + 1, z, state)
    return out


# ====================================================================================== stairs and shapes
def ring_path(cx, cz, r):
    """A closed 4-connected loop of cells approximating the circle r round (cx, cz), counter-clockwise from east."""
    pts = []
    n = max(16, int(2 * math.pi * r * 3))
    for i in range(n):
        a = 2 * math.pi * i / n
        c = (cx + int(round(r * math.cos(a))), cz + int(round(r * math.sin(a))))
        if not pts or c != pts[-1]:
            if pts:
                px, pz = pts[-1]
                if c[0] != px and c[1] != pz:            # step round the corner: keep the path 4-connected
                    pts.append((c[0], pz))
            pts.append(c)
    if pts[0] == pts[-1]:
        pts.pop()
    out = []
    for c in pts:
        if not out or c != out[-1]:
            out.append(c)
    return out


def spiral_stair(w, cx, cz, r, y0, y1, wood="spruce", start=0, support=None, landing=None):
    """A spiral stair round (cx, cz) at radius r from the floor at y0 up to the floor at y1 along ``ring_path``:
    two steps up, then a level landing (so no straight flight is longer than two steps), each step facing the
    next cell; head room cleared above every step (cutting the floors it passes). ``support`` fills the cell under
    each step. Returns the cells used."""
    path = ring_path(cx, cz, r)
    land = landing or B.full(f"{wood}_planks") if B.has_stairs(wood) else (landing or B.full(wood))
    cells = []
    y = y0                                   # the top of the last step: players stand at y + 1
    i = 0
    while y < y1:
        x, z = path[(start + i) % len(path)]
        nx, nz = path[(start + i + 1) % len(path)]
        facing = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(nx - x, nz - z)]
        if i % 3 == 2 and y < y1:
            w.set(x, y, z, land)                 # a landing level with the step before it
            for k in range(1, 4):
                w.set(x, y + k, z, AIR)
            cells.append((x, y, z))
        else:
            y += 1
            w.set(x, y, z, B.stairs(wood, facing))
            if support:
                w.set(x, y - 1, z, support)
            for k in range(1, 4 if y < y1 else 3):
                w.set(x, y + k, z, AIR)
            cells.append((x, y, z))
        i += 1
    return cells


def stone_lantern(w, x, y, z, stone="stone"):
    """A Japanese stone lantern (tōrō) standing on the ground at y."""
    w.set(x, y + 1, z, F("stone_bricks") if stone == "stone" else F(stone))
    w.set(x, y + 2, z, B.wall("stone_brick" if stone == "stone" else stone))
    w.set(x, y + 3, z, B.lantern(hanging=False))
    w.set(x, y + 4, z, B.slab("stone_brick" if stone == "stone" else stone, "bottom"))


def torii(w, x, y, z, axis="x", width=5, height=6, wood="crimson"):
    """A torii gate standing on the ground at y, its posts ``width`` apart across ``axis`` (the way through)."""
    hw = width // 2
    for k in (-hw, hw):
        px, pz = (x, z + k) if axis == "x" else (x + k, z)
        w.fill(px, y + 1, pz, px, y + height, pz, B.log(f"stripped_{wood}" if wood not in ("crimson", "warped")
                                                      else wood, "y") if wood not in ("crimson", "warped")
               else B.S(f"stripped_{wood}_stem", axis="y"))
    for k in range(-hw - 1, hw + 2):
        px, pz = (x, z + k) if axis == "x" else (x + k, z)
        w.set(px, y + height - 1, pz, B.full(f"{wood}_planks"))
        w.set(px, y + height + 1, pz, B.full("blackstone") if abs(k) <= hw else B.slab("blackstone", "bottom"))
        w.set(px, y + height, pz, B.full(f"{wood}_planks"))
