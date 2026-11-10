"""Building kit for the field set pieces: fitting things to the real terrain (pads, blends, foundations, steps),
paving, lights, signs, flags, fences, plants and other small furniture of the countryside.

Every helper takes the piece ``P`` (or a ``Frame`` over it where noted) and absolute world coordinates.
"""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage

from capital import blocks as B
from capital.noise import hash2, hash3

from . import blocks_ext as BX
from .world import AIR_S, ground_state

AIR = AIR_S
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
YAW_ROT = {"south": 0, "west": 4, "north": 8, "east": 12}   # banner/sign rotation facing a direction


def F(m):
    """Full block of a material family, or any block id."""
    return B.full(m)


def S(name, **props):
    return B.S(name, **props)


def facing_of(dx, dz):
    """Horizontal direction name closest to the vector (dx, dz)."""
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def rotation_of(dx, dz):
    """Sign/banner rotation (0..15) facing the vector (dx, dz) (0 = south, 4 = west, 8 = north, 12 = east)."""
    ang = math.degrees(math.atan2(-dx, dz))       # yaw: 0 south, 90 west
    return int(round(ang / 22.5)) % 16


def u(seed, *k):
    """Deterministic uniform [0, 1) from integers."""
    v = seed
    for i, kk in enumerate(k):
        v = int(hash2(int(kk), v, 7919 + i * 31) * 2147483647)
    return v / 2147483647.0


# ====================================================================================== masks & coordinates
def grid(P):
    """World coordinates of the piece columns: (X, Z) arrays shaped (NX, NZ)."""
    return np.meshgrid(np.arange(P.X0, P.X1 + 1), np.arange(P.Z0, P.Z1 + 1), indexing="ij")


def disc(P, cx, cz, r, shape="circle"):
    X, Z = grid(P)
    dx, dz = X - cx, Z - cz
    if shape == "square":
        return (np.abs(dx) <= r) & (np.abs(dz) <= r)
    if shape == "octagon":
        cut = int(round(r * 0.42))
        return (np.abs(dx) <= r) & (np.abs(dz) <= r) & (np.abs(dx) + np.abs(dz) <= 2 * r - cut)
    return dx * dx + dz * dz <= (r + 0.35) ** 2


def rect(P, x0, z0, x1, z1):
    X, Z = grid(P)
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    return (X >= x0) & (X <= x1) & (Z >= z0) & (Z <= z1)


def segment_mask(P, a, b, half):
    """Columns within ``half`` of the segment a-b."""
    X, Z = grid(P)
    ax, az = a
    bx, bz = b
    L2 = (bx - ax) ** 2 + (bz - az) ** 2
    t = np.clip(((X - ax) * (bx - ax) + (Z - az) * (bz - az)) / max(L2, 1e-9), 0, 1)
    px, pz = ax + t * (bx - ax), az + t * (bz - az)
    return (X - px) ** 2 + (Z - pz) ** 2 <= half * half


def polyline_mask(P, pts, half):
    m = np.zeros((P.NX, P.NZ), bool)
    for i in range(len(pts) - 1):
        m |= segment_mask(P, pts[i], pts[i + 1], half)
    return m


def cells(mask, P):
    """World (x, z) pairs of a mask."""
    ii, jj = np.nonzero(mask)
    return list(zip((ii + P.X0).tolist(), (jj + P.Z0).tolist()))


def ring(mask):
    """Edge cells of a mask (inside, with a 4-neighbour outside)."""
    pad = np.pad(mask, 1)
    core = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
    return mask & ~core


def dilate(mask, r):
    if r <= 0:
        return mask.copy()
    return ndimage.binary_dilation(mask, structure=_disc_struct(r))


def _disc_struct(r):
    R = int(math.ceil(r))
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    return dx * dx + dz * dz <= r * r + 0.3


# ====================================================================================== natural ground
NATURAL = {
    "abyss_dead_forest": ("minecraft:coarse_dirt", "minecraft:dirt"),
    "abyss_wastes": ("minecraft:cobbled_deepslate", "minecraft:deepslate"),
    "abyss_void_rift": ("minecraft:blackstone", "minecraft:deepslate"),
    "abyss_spire": ("minecraft:deepslate", "minecraft:deepslate"),
    "nordia_snowfield": ("minecraft:snow_block", "minecraft:dirt"),
    "nordia_ice_spikes": ("minecraft:snow_block", "minecraft:snow_block"),
    "nordia_crystal_hollow": ("minecraft:snow_block", "minecraft:dirt"),
    "sandria_dunes": ("minecraft:sand", "minecraft:sand"),
    "sandria_mesa": ("minecraft:red_sand", "minecraft:terracotta"),
    "dragonia_basalt_fields": ("minecraft:smooth_basalt", "minecraft:blackstone"),
}


def natural_top(P, x, z):
    """A plausible top block for a re-shaped natural column (terrain surface + biome repaint)."""
    b = P.biome_at(x, z)
    s = P.surface(x, z)
    if s in ("dirt_path", "sand", "gravel", "stone", "snow", "mud", "coarse_dirt", "cliff", "quarry"):
        return ground_state(s)
    if b in NATURAL:
        return NATURAL[b][0]
    if b in ("elder_forest", "elder_old_growth") and hash2(x, z, 77) < 0.12:
        return "minecraft:podzol[snowy=false]"
    return "minecraft:grass_block[snowy=false]"


def natural_under(P, x, z):
    b = P.biome_at(x, z)
    s = P.surface(x, z)
    if s in ("stone", "cliff", "quarry"):
        return "minecraft:stone"
    if s == "sand":
        return "minecraft:sand"
    if b in NATURAL:
        return NATURAL[b][1]
    return "minecraft:dirt"


def set_ground(P, x, z, y, top=None, under=None, clear=6):
    """Re-shape column (x, z) so its ground ends with ``top`` at height y: fills up from the terrain with ``under``
    or cuts the terrain down (air up to ``clear`` blocks above the old top)."""
    t = P.top(x, z)
    top = top or natural_top(P, x, z)
    under = under or natural_under(P, x, z)
    if t < y:
        P.fill(x, t + 1, z, x, y - 1, z, under) if y - 1 >= t + 1 else None
    elif t > y:
        P.fill(x, y + 1, z, x, t + clear, z, AIR)
    P.set(x, y, z, top)


def pad(P, mask, y, top=None, under=None, clear=8):
    """Flatten the masked columns to ground height ``y``; ``top``: state or callable(x, z) -> state."""
    for (x, z) in cells(mask, P):
        tp = top(x, z) if callable(top) else top
        set_ground(P, x, z, y, tp, under, clear)


def blend(P, mask, y, width=6, skip=None, top=None):
    """Ease the natural terrain from height ``y`` at the edge of ``mask`` back to its own height over ``width``
    blocks (only columns outside the mask; ``skip``: columns to leave alone)."""
    if width <= 0:
        return
    dist = ndimage.distance_transform_edt(~mask)
    zone = (dist > 0) & (dist <= width)
    if skip is not None:
        zone &= ~skip
    for (x, z) in cells(zone, P):
        d = dist[x - P.X0, z - P.Z0]
        t = d / (width + 0.5)
        t = t * t * (3 - 2 * t)
        nat = P.top(x, z)
        if P.wet(x, z) and nat < y:
            continue                          # never fill rivers and lakes
        target = int(round(y + (nat - y) * t))
        if target != nat:
            set_ground(P, x, z, target, top(x, z) if callable(top) else top)


_GROUNDS = ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud", "mycelium", "sand",
            "red_sand", "gravel", "snow_block", "dirt_path", "farmland", "clay", "soul_soil")


def ground_y(P, x, z):
    """y of the ground top of column (x, z) after the piece's changes (terrain or the piece's own blocks)."""
    t = P.top(x, z)
    col = P.a[x - P.X0, z - P.Z0]
    for k in range(P.NY - 1, -1, -1):
        y = k + P.Y0
        i = col[k]
        if i == 0:
            if y <= t:
                return y
            continue
        if i == 1:
            continue
        return y
    return t


def plantable(P, x, z, soils=("grass_block", "dirt", "podzol", "moss_block", "coarse_dirt", "rooted_dirt")):
    """y of the soil block a plant could stand on in column (x, z), or None (paved, built on, wet)."""
    if P.wet(x, z):
        return None
    y = ground_y(P, x, z)
    st = P.effective(x, y, z)
    name = B.parse(st)[0].split(":", 1)[1]
    if name not in soils:
        return None
    if P.get_idx(x, y + 1, z) > 1:
        return None
    return y


def foundation(P, x, z, y, state, depth=2):
    """Masonry under the block at (x, y, z): from ``depth`` below the terrain up to y - 1."""
    t = P.top(x, z)
    if t < y:
        P.fill(x, t - depth, z, x, y - 1, z, state)


def column(P, x, z, y0, y1, state):
    P.fill(x, y0, z, x, y1, z, state)


# ====================================================================================== paving
def mixed(P, x, y, z, mix, seed=0):
    """One block of a weighted mix [(state, weight), ...] chosen by position."""
    tot = sum(w for _, w in mix)
    r = hash3(x, y, z, seed) * tot
    for st, w in mix:
        r -= w
        if r < 0:
            P.set(x, y, z, F(st) if (st in B.MATERIALS or ":" not in st and not st.endswith("]")) else st)
            return
    P.set(x, y, z, F(mix[-1][0]))


def pave(P, mask, y, mix, seed=0):
    for (x, z) in cells(mask, P):
        mixed(P, x, y, z, mix, seed)


COBBLE_MIX = [("cobbled_paving", 64), ("cobblestone", 10), ("stone_bricks", 8), ("andesite", 6),
              ("mossy_cobblestone", 6), ("polished_andesite", 6)]
FLAG_MIX = [("plaza_flagstones", 70), ("smooth_castle_stone", 8), ("polished_diorite", 6), ("stone_bricks", 6),
            ("polished_andesite", 6), ("cracked_stone_bricks", 4)]
RURAL_MIX = [("stone_bricks", 40), ("mossy_stone_bricks", 18), ("cracked_stone_bricks", 10), ("cobblestone", 12),
             ("mossy_cobblestone", 10), ("andesite", 10)]
MOSS_MIX = [("mossy_stone_bricks", 40), ("mossy_cobblestone", 22), ("stone_bricks", 12), ("moss_block", 14),
            ("cracked_stone_bricks", 12)]


# ====================================================================================== lights
def lantern_post(P, x, y, z, height=3, wood="spruce", soul=False, arm=None):
    """Wooden post standing on the block at y with a lantern: on top, or hanging from an arm towards ``arm``."""
    P.set(x, y + 1, z, B.log(wood))
    for i in range(2, height + 1):
        P.set(x, y + i, z, B.fence(wood))
    if arm:
        dx, dz = DIRS[arm]
        P.set(x, y + height + 1, z, B.fence(wood))
        P.set(x + dx, y + height + 1, z + dz, B.fence(wood))
        P.set(x + dx, y + height, z + dz, B.lantern(hanging=True, soul=soul))
    else:
        P.set(x, y + height + 1, z, B.lantern(hanging=False, soul=soul))


def lamp_post(P, x, y, z, height=4, soul=False, metal="polished_blackstone"):
    """Iron-style lamp post (blackstone base, wall shaft, lantern on a slab cap)."""
    P.set(x, y + 1, z, F(metal))
    for i in range(2, height + 1):
        P.set(x, y + i, z, B.wall(metal) if B._mat(metal)[3] else B.fence("dark_oak"))
    P.set(x, y + height + 1, z, B.lantern(hanging=False, soul=soul))


def stone_lantern(P, x, y, z, stone="stone_bricks", soul=False, cap=None):
    """Japanese-style stone lantern (tōrō): base, shaft, lantern box, cap."""
    P.set(x, y + 1, z, B.slab(stone, "top") if B._mat(stone)[2] else F(stone))
    P.set(x, y + 2, z, B.wall(stone) if B._mat(stone)[3] else F(stone))
    P.set(x, y + 3, z, B.lantern(soul=soul))
    P.set(x, y + 4, z, cap or B.slab(stone, "bottom"))


def torch_on_post(P, x, y, z, wood="oak", height=2):
    for i in range(1, height + 1):
        P.set(x, y + i, z, B.fence(wood))
    P.set(x, y + height + 1, z, B.torch())


# ====================================================================================== signs, flags, banners
FIELD_SLUGS = {1: "arcelia", 2: "elder_forest", 3: "sandria_desert", 4: "nordia_snowfield", 5: "dragonia_volcano",
               6: "celestia", 7: "tower_of_the_end", 8: "fertile_farm", 9: "lakeside", 10: "wind_harbour"}
FIELD_LEVELS = {1: (5, 15), 2: (10, 20), 3: (15, 25), 4: (20, 30), 5: (30, 40), 6: (40, None), 7: (50, None),
                8: (10, 20), 9: (15, 25), 10: (20, 30)}
# dye colours of each field (flags/banners of the border markers)
FIELD_COLORS = {1: ("lime", "yellow"), 2: ("green", "cyan"), 3: ("gray", "orange"), 4: ("light_blue", "white"),
                5: ("orange", "brown"), 6: ("white", "light_blue"), 7: ("purple", "black"), 8: ("yellow", "lime"),
                9: ("cyan", "blue"), 10: ("blue", "white")}
TEXT_COLORS = {1: "dark_green", 2: "dark_aqua", 3: "gold", 4: "aqua", 5: "gold", 6: "white", 7: "light_purple",
               8: "gold", 9: "aqua", 10: "blue"}


def field_name(field):
    return BX.text_component(translate=f"area.eternalrealm.{FIELD_SLUGS[field]}", bold=True)


def field_level(field):
    lo, hi = FIELD_LEVELS[field]
    if hi is None:
        return BX.text_component(translate="sign.eternalrealm.level_from", args=[lo])
    return BX.text_component(translate="sign.eternalrealm.level_range", args=[lo, hi])


def place_sign(P, x, y, z, front, back=None, wood="spruce", facing=None, rotation=None, wall=False, glowing=False,
               color="black"):
    """A sign with text components. ``wall``: attached to the block behind, text facing ``facing``;
    otherwise standing with ``rotation`` (or facing ``facing``)."""
    if wall:
        P.set(x, y, z, B.wall_sign(wood, facing))
    else:
        rot = rotation if rotation is not None else YAW_ROT[facing]
        P.set(x, y, z, B.sign(wood, rot))
    P.be(x, y, z, BX.sign_nbt(front, back, color=color, glowing=glowing))


def flag(P, x, y, z, facing="east", design="kingdom", width=3.0, height=2.0, wind=None):
    """The waving cloth flag block (top of a pole); the cloth streams ``width`` towards ``facing``."""
    P.set(x, y, z, B.S("flag", facing=facing))
    P.be(x, y, z, BX.flag_nbt(design, width, height, wind))


def flagpole(P, x, y, z, length, facing="east", design="kingdom", width=3.0, height=2.0, wood="dark_oak",
             base=None, wind=None):
    """Pole standing on the block at y (shaft y+1 .. y+length-1) with the flag on top (y+length)."""
    if base:
        P.set(x, y + 1, z, base)
    for i in range(2 if base else 1, length):
        P.set(x, y + i, z, B.fence(wood))
    flag(P, x, y + length, z, facing, design, width, height, wind)
    return y + length


def banner_nbt_field(field):
    """Banner patterns in the field's colours (base colour comes from the block)."""
    a, b = FIELD_COLORS[field]
    return B.banner_nbt([("bs", b), ("ts", b), ("bo", a), ("mc", b)])


def standing_banner(P, x, y, z, color, rotation, nbt=None):
    P.set(x, y, z, B.banner(color, rotation))
    if nbt:
        P.be(x, y, z, nbt)


def wall_banner(P, x, y, z, color, facing, nbt=None):
    P.set(x, y, z, B.wall_banner(color, facing))
    if nbt:
        P.be(x, y, z, nbt)


# ====================================================================================== plants & small furniture
FLOWERS = ["poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "red_tulip", "white_tulip",
           "pink_tulip", "orange_tulip", "lily_of_the_valley", "blue_orchid"]


def flowers(P, mask, y_of=None, density=0.5, seed=0, kinds=None, tall=0.08):
    """Flowers (and some tall flowers) on the soil of the masked columns."""
    kinds = kinds or FLOWERS
    for (x, z) in cells(mask, P):
        r = hash2(x, z, seed)
        if r > density:
            continue
        y = plantable(P, x, z)
        if y is None:
            continue
        if r < density * tall:
            k = ("rose_bush", "peony", "lilac", "sunflower")[int(hash2(z, x, seed + 1) * 4)]
            P.set(x, y + 1, z, B.S(k, half="lower"))
            P.set(x, y + 2, z, B.S(k, half="upper"))
        else:
            P.set(x, y + 1, z, B.S(kinds[int(hash2(z, x, seed + 2) * len(kinds))]))


def grass_tufts(P, mask, y_of=None, density=0.3, seed=0, fern=False):
    for (x, z) in cells(mask, P):
        r = hash2(x, z, seed + 11)
        if r < density:
            y = plantable(P, x, z)
            if y is None:
                continue
            if r < density * 0.25:
                k = "large_fern" if fern else "tall_grass"
                P.set(x, y + 1, z, B.S(k, half="lower"))
                P.set(x, y + 2, z, B.S(k, half="upper"))
            else:
                P.set(x, y + 1, z, B.S("fern" if fern and r < density * 0.6 else "grass"))


def bush(P, x, y, z, r=1.3, kind="azalea", seed=0):
    R = int(math.ceil(r))
    for dx in range(-R, R + 1):
        for dy in range(0, R + 1):
            for dz in range(-R, R + 1):
                d = math.sqrt(dx * dx + (dy * 1.4) ** 2 + dz * dz)
                if d <= r + (hash3(x + dx, y + dy, z + dz, seed) - 0.5) * 0.6:
                    P.set(x + dx, y + 1 + dy, z + dz, B.leaves(kind), only_empty=True)


def tree(P, x, y, z, kind="oak", height=None, crown=None, seed=0, log=None):
    """A broadleaf tree standing on the block at y (persistent leaves)."""
    r = hash2(x, z, seed)
    h = height or int(4 + r * 3)
    cr = crown or (2.4 + hash2(z, x, seed + 1) * 1.0)
    lg = log or kind
    for i in range(1, h + 1):
        P.set(x, y + i, z, B.log(lg))
    cy = y + h
    R = int(math.ceil(cr)) + 1
    for dx in range(-R, R + 1):
        for dy in range(-2, R + 1):
            for dz in range(-R, R + 1):
                d = math.sqrt(dx * dx + (dy * 1.3) ** 2 + dz * dz)
                if d <= cr + (hash3(x + dx, cy + dy, z + dz, seed + 5) - 0.5) * 0.9:
                    if dx == 0 and dz == 0 and dy <= 0:
                        continue
                    P.set(x + dx, cy + dy, z + dz, B.leaves(kind), only_empty=True)
    P.set(x, cy, z, B.log(lg))


def spruce(P, x, y, z, height=None, seed=0, snowy=False):
    h = height or int(7 + hash2(x, z, seed) * 5)
    for i in range(1, h + 1):
        P.set(x, y + i, z, B.log("spruce"))
    top = y + h
    for i in range(h - 1):
        yy = top - i
        rad = min(3.0, 0.5 + i * 0.4) if i % 3 != 2 else max(0.7, min(3.0, 0.5 + i * 0.4) - 1.0)
        R = int(math.ceil(rad))
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if (dx or dz) and dx * dx + dz * dz <= rad * rad + 0.3:
                    P.set(x + dx, yy, z + dz, B.leaves("spruce"), only_empty=True)
                    if snowy and i % 3 == 0 and P.get_idx(x + dx, yy + 1, z + dz) <= 1:
                        P.set(x + dx, yy + 1, z + dz, B.S("snow", layers=1), only_empty=True)
    P.set(x, top + 1, z, B.leaves("spruce"))
    P.set(x, top + 2, z, B.leaves("spruce"))


def potted(P, x, y, z, plant):
    P.set(x, y, z, B.potted(plant))


def barrel(P, x, y, z, facing="up"):
    P.set(x, y, z, B.barrel(facing))


def crate(P, x, y, z, seed=0):
    """A crate (a barrel on its side, or wood with trapdoor lids)."""
    if hash2(x, z, seed) < 0.5:
        P.set(x, y, z, B.barrel(("north", "east", "south", "west")[int(hash2(z, x, seed) * 4)]))
    else:
        P.set(x, y, z, F("spruce_planks") if hash2(x, y, seed) < 0.5 else B.log("spruce", "x"))


def bench(P, x, y, z, facing, length=3, wood="spruce", axis_dir=None):
    """Bench of stairs standing on the block at y; sitters face ``facing``; extends towards ``axis_dir``."""
    axis_dir = axis_dir or CW[facing]
    dx, dz = DIRS[axis_dir]
    for i in range(length):
        P.set(x + dx * i, y + 1, z + dz * i, B.stairs(wood, OPP[facing]))
    ex, ez = DIRS[OPP[axis_dir]]
    P.set(x + ex, y + 1, z + ez, B.trapdoor(wood, OPP[axis_dir], "bottom", True))
    P.set(x + dx * length, y + 1, z + dz * length, B.trapdoor(wood, axis_dir, "bottom", True))


def haystack(P, x, y, z, r=1.6, seed=0):
    R = int(math.ceil(r))
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            for dy in range(0, R + 1):
                d = math.sqrt(dx * dx + dz * dz + (dy * 1.15) ** 2)
                if d <= r + (hash3(x + dx, dy, z + dz, seed) - 0.5) * 0.4:
                    P.set(x + dx, y + 1 + dy, z + dz, B.S("hay_block", axis="y"))


def well(P, x, y, z, stone="cobblestone", roof="spruce", seed=0):
    """Village well centred on (x, z), standing on the ground block at y (3x3 shaft with water, roof on posts)."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            edge = max(abs(dx), abs(dz)) == 2
            if edge:
                P.fill(x + dx, y - 3, z + dz, x + dx, y + 1, z + dz, F(stone))
            else:
                P.fill(x + dx, y - 3, z + dz, x + dx, y - 3, z + dz, F(stone))
                P.fill(x + dx, y - 2, z + dz, x + dx, y, z + dz, B.S("water", level=0))
                P.set(x + dx, y + 1, z + dz, AIR)
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        P.fill(x + dx, y + 2, z + dz, x + dx, y + 3, z + dz, B.fence(roof))
    P.fill(x - 2, y + 4, z - 2, x + 2, y + 4, z + 2, B.slab(roof, "bottom"))
    P.fill(x - 1, y + 5, z, x + 1, y + 5, z, B.slab(roof, "bottom"))
    for dz in (-2, 2):
        for dx in range(-2, 3):
            P.set(x + dx, y + 4, z + dz, B.stairs(roof, "south" if dz < 0 else "north"))
    P.fill(x - 1, y + 4, z - 1, x + 1, y + 4, z + 1, F(f"{roof}_planks"))
    P.set(x, y + 3, z, B.chain())
    P.set(x, y + 2, z, B.lantern(hanging=True))


def cart(P, x, y, z, facing="east", wood="spruce", load=None, broken=False):
    """Hand cart standing on the block at y: bed along ``facing``, wheels at the sides."""
    dx, dz = DIRS[facing]
    sx, sz = DIRS[CW[facing]]
    for i in (0, 1):
        P.set(x + dx * i, y + 1, z + dz * i, B.slab(wood, "top") if not broken or i == 0 else
              B.stairs(wood, OPP[facing], "bottom"))
    for side in (1, -1):
        wx, wz = x + sx * side, z + sz * side
        P.set(wx, y + 1, wz, B.trapdoor(wood, CW[facing] if side == 1 else CCW[facing], "bottom", True)
              if not (broken and side == -1) else B.S("dark_oak_button", face="floor", facing=facing))
    P.set(x - dx, y + 1, z - dz, B.fence(wood))               # shaft
    if load:
        P.set(x, y + 2, z, load)


def scarecrow(P, x, y, z, facing="south", seed=0):
    """Scarecrow on the ground block at y: fence pole, hay body, pumpkin head, stick arms."""
    P.set(x, y + 1, z, B.fence("spruce"))
    P.set(x, y + 2, z, B.S("hay_block", axis="y"))
    P.set(x, y + 3, z, B.S("carved_pumpkin", facing=facing))
    sx, sz = DIRS[CW[facing]]
    for s in (1, -1):
        P.set(x + sx * s, y + 2, z + sz * s, B.fence("spruce"))


def standing_stone(P, x, y, z, height, stone="stone", lean=None, seed=0, moss=0.3):
    """A rough standing stone (2x1 or 1x1 footprint) sunk into the ground at y."""
    mats = {"stone": ["stone", "andesite", "cobblestone", "mossy_cobblestone"],
            "mossy": ["mossy_cobblestone", "mossy_stone_bricks", "stone", "andesite"],
            "dark": ["deepslate", "cobbled_deepslate", "tuff", "blackstone"],
            "pale": ["calcite", "diorite", "smooth_stone", "polished_diorite"]}[stone]
    wide = hash2(x, z, seed) < 0.5
    ox, oz = (1, 0) if hash2(z, x, seed) < 0.5 else (0, 1)
    for i in range(-1, height):
        for k in ((0, 0), (ox, oz)) if (wide and i < height - 1) else ((0, 0),):
            px, pz = x + k[0], z + k[1]
            if lean and i >= height // 2:
                px += DIRS[lean][0]
                pz += DIRS[lean][1]
            r = hash3(px, y + 1 + i, pz, seed + 3)
            m = mats[0] if r > moss + 0.3 else mats[1] if r > moss else mats[2 + int(r * 10) % 2]
            P.set(px, y + 1 + i, pz, F(m))
