"""Furniture and room dressing for the town's interiors (not a district: files starting with "_" are not run).

Everything works in any ``Frame`` (local coordinates: states and directions are turned with the frame) or on the
``World``. The owner found the interiors too plain (2026-11-11: 「内装が質素すぎたり」): these helpers dress rooms
with beams, wainscoting, rugs, shelves of goods, kitchens, beds with nightstands, wardrobes, desks, plants and
paintings.

Paintings are entities that the game takes down when the wall behind them is not solid or something stands in
front of them, so :func:`painting` only hangs one where every block behind it is a full solid block and the blocks
it covers are free (it returns False otherwise).
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2, hash3
from capital.world import World, YAW

AIR = "minecraft:air"
CCW = {"north": "west", "west": "south", "south": "east", "east": "north"}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
PAINT_1x1 = ("kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland")
PAINT_2x1 = ("pool", "courbet", "sea", "sunset", "creebet")
PAINT_1x2 = ("wanderer", "graham")
PAINT_2x2 = ("match", "bust", "stage", "void", "skull_and_roses")
GOODS_SMALL = ("flower_pot", "candle", "decorated_pot", "lantern", "potted_fern", "potted_red_tulip",
               "potted_cornflower", "potted_azure_bluet", "brewing_stand", "candle_red", "cake")
# full solid cubes a painting may hang on (names without the namespace)
_NOT_FULL = ("stairs", "slab", "pane", "glass", "fence", "wall", "door", "trapdoor", "carpet", "leaves", "torch",
             "lantern", "candle", "sign", "banner", "button", "pressure_plate", "chain", "bars", "ladder", "rail",
             "flower", "potted", "pot", "bed", "chest", "barrel", "anvil", "cauldron", "lectern", "campfire",
             "table", "stand", "grindstone", "stonecutter", "bell", "head", "skull", "chandelier", "light_shaft",
             "smoke_emitter", "spray", "flag", "clock_face", "air", "void", "water", "lava", "snow", "vine",
             "scaffolding", "composter", "hopper", "cake", "candle", "rod", "amethyst", "pointed", "farmland",
             "dirt_path", "soul_sand", "honey", "slime", "beacon", "conduit", "piston", "dragon", "egg", "shulker",
             "bookshelf_x")


def _world(t):
    return t if isinstance(t, World) else t.w


def _wpos(t, x, y, z):
    return (x, y, z) if isinstance(t, World) else t.pos(x, y, z)


def _wdir(t, d):
    return d if isinstance(t, World) else t.dir(d)


def is_full(state: str) -> bool:
    name = state.split("[")[0].split(":")[-1]
    return not any(k in name for k in _NOT_FULL)


def is_free(state: str) -> bool:
    return state in ("minecraft:air", "eternalrealm:void") or "light_shaft" in state


def painting(t, x, y, z, facing, variant=None, w=1, h=1, seed=0) -> bool:
    """Hang a painting whose lowest block on the side ``CCW`` of ``facing``... is (x, y, z), facing ``facing``
    (into the room): it covers w blocks towards the counter-clockwise side of ``facing`` and h blocks up. Only
    hangs it on full solid walls with free blocks in front; returns whether it hung."""
    world = _world(t)
    wx, wy, wz = _wpos(t, x, y, z)
    wf = _wdir(t, facing)
    if variant is None:
        pool = {(1, 1): PAINT_1x1, (2, 1): PAINT_2x1, (1, 2): PAINT_1x2, (2, 2): PAINT_2x2}[(w, h)]
        variant = pool[int(hash3(wx, wy, wz, seed + 71) * len(pool))]
    side = STEP[CCW[wf]]
    back = STEP[wf]
    covered = set()
    for k in range(w):
        for l in range(h):
            cx, cy, cz = wx + side[0] * k, wy + l, wz + side[1] * k
            if not is_free(world.get(cx, cy, cz)):
                return False
            if not is_full(world.get(cx - back[0], cy, cz - back[1])):
                return False
            covered.add((cx, cy, cz))
    # no other entity (item frame, painting, armour stand) in those blocks: hanging things must not overlap
    for e in world.entities:
        ex, ey, ez = (int(v // 1) for v in e["pos"])
        if (ex, ey, ez) in covered or (e["type"].endswith("armor_stand") and (ex, ey + 1, ez) in covered):
            return False
    data = {"south": 0, "west": 1, "north": 2, "east": 3}[wf]
    world.entity("minecraft:painting", (wx + 0.5, wy + 0.5, wz + 0.5), YAW[wf],
                 f'{{variant:"minecraft:{variant}",facing:{data}b,Invulnerable:1b}}')
    return True


def wainscot(t, x0, z0, x1, z1, y, wood="spruce", out_of_walls=None):
    """Panelling: a course of ``wood`` trapdoors (closed, top half) flat on the room side of the walls along the
    room box (x0..x1, z0..z1 are the room's free cells), only in front of solid wall blocks."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    sides = (("north", [(x, z0) for x in range(x0, x1 + 1)]), ("south", [(x, z1) for x in range(x0, x1 + 1)]),
             ("west", [(x0, z) for z in range(z0, z1 + 1)]), ("east", [(x1, z) for z in range(z0, z1 + 1)]))
    for wall_side, cells in sides:
        dx, dz = STEP[wall_side]
        for (x, z) in cells:
            if t.is_empty(x, y, z) and is_full(t.get(x + dx, y, z + dz)):
                t.set(x, y, z, B.trapdoor(wood, {"north": "south", "south": "north", "east": "west",
                                                 "west": "east"}[wall_side], "top", True))


def over_stairway(t, x, y, z, depth=4) -> bool:
    """Whether a stair step or a ladder lies within ``depth`` blocks under (x, y, z): anything put there takes the
    head room a player needs to climb it."""
    for d in range(1, depth + 1):
        st = t.get(x, y - d, z)
        if ("stairs" in st and "half=bottom" in st) or "ladder" in st:
            return True
    return False


def beams(t, x0, z0, x1, z1, y, axis="x", every=3, wood="dark_oak", only_empty=True):
    """Exposed ceiling beams at height y (logs along ``axis``), only through free cells and never over a stair
    or ladder (build those first)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    cells = ([(x, z) for z in range(z0, z1 + 1, every) for x in range(x0, x1 + 1)] if axis == "x" else
             [(x, z) for x in range(x0, x1 + 1, every) for z in range(z0, z1 + 1)])
    for (x, z) in cells:
        if (not only_empty or t.is_empty(x, y, z)) and not over_stairway(t, x, y, z):
            t.set(x, y, z, B.log(wood, axis))


def rug(t, x0, z0, x1, z1, y, field="red", border="yellow", pattern=None):
    """Carpet rug standing on the floor block at y (carpets at y + 1), only on free cells. ``pattern`` "check"
    alternates the field with the border colour."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not t.is_empty(x, y + 1, z):
                continue
            edge = x in (x0, x1) or z in (z0, z1)
            c = border if edge else field
            if pattern == "check" and not edge and (x + z) % 2:
                c = border
            t.set(x, y + 1, z, B.carpet(c))


def goods(t, x, y, z, seed=0, kinds=GOODS_SMALL):
    """One small item standing on a shelf or table top at (x, y, z) (only if free)."""
    if not t.is_empty(x, y, z):
        return
    k = kinds[int(hash3(*_wpos(t, x, y, z), seed) * len(kinds))]
    if k == "candle":
        t.set(x, y, z, B.candle(1 + int(hash3(x, y, z, seed + 1) * 3), "white"))
    elif k == "candle_red":
        t.set(x, y, z, B.candle(2, "red"))
    elif k.startswith("potted_"):
        t.set(x, y, z, B.potted(k[len("potted_"):]))
    elif k == "lantern":
        t.set(x, y, z, B.lantern())
    elif k == "brewing_stand":
        t.set(x, y, z, B.S("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
    else:
        t.set(x, y, z, B.S(k))


def cupboard(t, x, y, z, facing, height=2, wood="spruce", top_goods=True, seed=0):
    """A cupboard against a wall: barrels facing out, a slab top with a small item on it."""
    for k in range(height):
        t.set(x, y + 1 + k, z, B.S("barrel", facing=facing))
    if top_goods:
        goods(t, x, y + 1 + height, z, seed)


def wardrobe(t, x, y, z, facing, wood="spruce"):
    """A tall wardrobe: two blocks of planks with closed trapdoor doors on the room side."""
    dx, dz = STEP[facing]
    t.set(x, y + 1, z, B.S(f"{wood}_planks"))
    t.set(x, y + 2, z, B.S(f"{wood}_planks"))
    for k in (1, 2):
        if t.is_empty(x + dx, y + k, z + dz):
            t.set(x + dx, y + k, z + dz, B.trapdoor(wood, facing, "bottom", True))


def nightstand(t, x, y, z, seed=0):
    t.set(x, y + 1, z, B.barrel("up"))
    t.set(x, y + 2, z, B.candle(1 + int(hash3(x, y, z, seed) * 2), ("white", "yellow", "red")[int(hash3(x, z, y,
                                                                                                       seed) * 3)]))


def desk(t, x, y, z, facing, wood="spruce", seed=0):
    """A writing desk: lectern or crafting top with a chair in front (the sitter faces ``facing``... the desk)."""
    dx, dz = STEP[facing]
    choice = int(hash3(*_wpos(t, x, y, z), seed + 5) * 3)
    if choice == 0:
        t.set(x, y + 1, z, B.S("lectern", facing=_opposite(facing), has_book=True))
    elif choice == 1:
        t.set(x, y + 1, z, B.S("cartography_table"))
    else:
        t.set(x, y + 1, z, B.slab(wood, "top"))
        goods(t, x, y + 2, z, seed, kinds=("candle", "flower_pot", "potted_fern", "lantern"))
    cx, cz = x - dx, z - dz
    if t.is_empty(cx, y + 1, cz):
        t.set(cx, y + 1, cz, B.stairs(wood, _opposite(facing)))


def kitchen(t, x0, z, x1, y, wall_facing, seed=0):
    """A kitchen run along a wall (cells x0..x1 at depth z, local x axis), the wall behind towards
    ``wall_facing``: stove (smoker), cauldron, barrels and a worktop, a shelf of pots above."""
    xs = list(range(min(x0, x1), max(x0, x1) + 1))
    pieces = [B.S("smoker", facing=_opposite(wall_facing), lit=True), B.S("water_cauldron", level=2),
              B.barrel("up"), B.slab("spruce", "top"), B.S("crafting_table"), B.barrel(_opposite(wall_facing))]
    start = int(hash3(*_wpos(t, x0, y, z), seed) * len(pieces))
    for i, x in enumerate(xs):
        if t.is_empty(x, y + 1, z):
            t.set(x, y + 1, z, pieces[(start + i) % len(pieces)])
            above = y + 2
            if pieces[(start + i) % len(pieces)].endswith("[type=top]") or "barrel" in pieces[(start + i) % len(pieces)]:
                goods(t, x, above, z, seed + i, kinds=("flower_pot", "candle", "decorated_pot", "potted_fern"))


def bed_pair(t, x, y, z, facing, color="red", double=False):
    """A bed whose head lies towards ``facing`` (foot at (x, y + 1, z)); ``double`` adds one beside it."""
    foot, head = B.bed_states(color, facing)
    dx, dz = STEP[facing]
    t.set(x, y + 1, z, foot)
    t.set(x + dx, y + 1, z + dz, head)
    if double:
        sx, sz = STEP[CCW[facing]]
        if t.is_empty(x + sx, y + 1, z + sz) and t.is_empty(x + sx + dx, y + 1, z + sz + dz):
            t.set(x + sx, y + 1, z + sz, foot)
            t.set(x + sx + dx, y + 1, z + sz + dz, head)


def plants(t, cells, y, seed=0):
    for i, (x, z) in enumerate(cells):
        if t.is_empty(x, y, z):
            t.set(x, y, z, B.potted(("fern", "azalea_bush", "flowering_azalea_bush", "red_tulip", "white_tulip",
                                     "cornflower", "blue_orchid", "allium", "oxeye_daisy")[int(hash2(i, seed, 3) * 9)]))


def crates(t, x, y, z, n=3, seed=0):
    """A small pile of crates and sacks (barrels, chests, hay, wool) at (x, y + 1, z) and around."""
    pile = [B.barrel("up"), B.S("hay_block", axis="y"), B.wool("white"), B.chest("north"), B.S("composter"),
            B.wool("brown")]
    cells = [(0, 0, 1), (1, 0, 1), (0, 1, 1), (0, 0, 2), (1, 1, 1)]
    for i, (dx, dz, dy) in enumerate(cells[:n]):
        if t.is_empty(x + dx, y + dy, z + dz):
            t.set(x + dx, y + dy, z + dz, pile[int(hash3(x + dx, y + dy, z + dz, seed) * len(pile))])


def _opposite(d):
    return {"north": "south", "south": "north", "east": "west", "west": "east"}[d]


def item_on_wall(t, x, y, z, facing, item):
    """A fixed item frame in front of a solid wall (only if the wall is solid and the cell is free)."""
    dx, dz = STEP[facing]
    if t.is_empty(x, y, z) and is_full(t.get(x - dx, y, z - dz)):
        deco.item_frame(t, x, y, z, facing, item)


# ====================================================================================== whole rooms
def doors_around(t, x0, z0, x1, z1, y):
    """Cells just inside the room in front of door or open doorway blocks in its walls (kept free)."""
    keep = set()
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            inside = x0 <= x <= x1 and z0 <= z <= z1
            if inside:
                continue
            s = t.get(x, y + 1, z)
            name = s.split("[")[0]
            if name.endswith("_door") or (t.is_empty(x, y + 1, z) and t.is_empty(x, y + 2, z)):
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        nx, nz = x + dx, z + dz
                        if x0 <= nx <= x1 and z0 <= nz <= z1:
                            keep.add((nx, nz))
                            for ddx in (-1, 0, 1):
                                for ddz in (-1, 0, 1):
                                    keep.add((nx + ddx, nz + ddz))
    return keep


def dress(t, x0, z0, x1, z1, y, ceiling, seed=0, wood="spruce", beam_wood="dark_oak", beam_axis="x",
          rug_colors=("red", "yellow"), paintings=2, plants=True, wainscot_wood=None, keep=(), beams_on=True):
    """Dress a rectangular room whose free cells are x0..x1, z0..z1 standing on the floor at y, ceiling block at
    ``ceiling``: beams under a high enough ceiling, panelling along solid walls, plants in the corners,
    paintings on blank walls at eye level, a rug in the middle. Only free cells are used; the cells by doors and
    ``keep`` stay free."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    free_keep = set(keep) | doors_around(t, x0, z0, x1, z1, y)
    if beams_on and ceiling - y >= 5:
        beams(t, x0, z0, x1, z1, ceiling - 1, axis=beam_axis, every=3, wood=beam_wood)
    if wainscot_wood:
        wainscot(t, x0, z0, x1, z1, y + 1, wood=wainscot_wood)
        # take it away again by the doors
        for (x, z) in free_keep:
            if "trapdoor" in t.get(x, y + 1, z):
                t.set(x, y + 1, z, AIR)
    if plants:
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            if (x, z) not in free_keep and t.is_empty(x, y + 1, z) and t.is_empty(x, y + 2, z):
                kind = ("azalea_bush", "flowering_azalea_bush", "fern", "bamboo")[int(hash2(x, z, seed) * 4)]
                t.set(x, y + 1, z, B.potted(kind))
    hung = 0
    if paintings:
        walls = []
        for x in range(x0 + 1, x1, 3):
            walls.append((x, z0, "south"))
            walls.append((x, z1, "north"))
        for z in range(z0 + 1, z1, 3):
            walls.append((x0, z, "east"))
            walls.append((x1, z, "west"))
        walls.sort(key=lambda c: hash2(c[0] * 7 + c[1], seed, 41))
        for (x, z, fac) in walls:
            if hung >= paintings:
                break
            if (x, z) in free_keep:
                continue
            size = (2, 1) if hash2(x, z, seed + 9) < 0.5 else (1, 1)
            if painting(t, x, y + 2, z, fac, w=size[0], h=size[1], seed=seed + hung):
                hung += 1
    if rug_colors and x1 - x0 >= 4 and z1 - z0 >= 4:
        rug(t, x0 + 2, z0 + 2, x1 - 2, z1 - 2, y, field=rug_colors[0], border=rug_colors[1])
    return hung


def stair_up(t, x, z, y0, rise, direction, wood="spruce", width=1, side="east"):
    """A straight stair from the floor at y0 rising ``rise`` blocks towards ``direction`` (first step at
    (x, y0 + 1, z)), ``width`` wide towards ``side``; the last step is level with the floor above (y0 + rise),
    so it carves the opening in that floor. Head room: two free blocks over every step, and a third over every
    step but the last, because a player (1.8 tall) climbing onto the next step is still partly over this one.
    Returns the landing cell (x, y0 + rise + 1, z) one step past the top."""
    dx, dz = STEP[direction]
    sx, sz = STEP[side]
    for k in range(rise):
        for i in range(width):
            px, pz = x + dx * k + sx * i, z + dz * k + sz * i
            t.set(px, y0 + 1 + k, pz, B.stairs(wood, direction))
            for y in range(y0 + 1, y0 + 1 + k):
                t.set(px, y, pz, B.S(f"{wood}_planks"))
            for y in range(y0 + 2 + k, y0 + (4 if k == rise - 1 else 5) + k):
                t.set(px, y, pz, AIR)
    return (x + dx * rise, y0 + rise + 1, z + dz * rise)
