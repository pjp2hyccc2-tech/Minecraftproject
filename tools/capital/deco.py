"""Decoration helpers: trees, street furniture, interior furniture, lights, banners, entities.

All functions take a target ``t`` (``World`` or ``Frame``) and positions in its coordinates. Directions are
given in the target's local terms (a Frame maps them to world directions).
"""
from __future__ import annotations

import math

import numpy as np

from . import blocks as B
from .noise import hash2, hash3
from .world import DIR_VEC, OPPOSITE, World, left_of, right_of

AIR = "minecraft:air"


def _wdir(t, d):
    return t.dir(d) if hasattr(t, "dir") and not isinstance(t, World) else d


# ============================================================================== trees
def tree_oak(t, x, y, z, seed=0, height=None, crown=None, kind="oak", log_kind=None):
    """Broadleaf tree standing on the block at y (trunk starts at y+1)."""
    r = hash2(x, z, seed)
    h = height or int(5 + r * 3)
    cr = crown or (2.6 + hash2(z, x, seed + 1) * 1.2)
    logk = log_kind or kind
    for i in range(1, h + 1):
        t.set(x, y + i, z, B.log(logk))
    cy = y + h
    R = int(math.ceil(cr)) + 1
    dx, dy, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-2, R + 1), np.arange(-R, R + 1), indexing="ij")
    d = np.sqrt(dx * dx + (dy * 1.25) ** 2 + dz * dz)
    jit = hash3(x + dx, cy + dy, z + dz, seed + 5)
    m = d <= cr + (jit - 0.5) * 0.9
    m &= ~((dx == 0) & (dz == 0) & (dy <= 0))
    t.set_many(x + dx[m], cy + dy[m], z + dz[m], B.leaves(kind), only_empty=True)
    # a couple of branches
    for k, (bx, bz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        if hash2(x + k, z, seed + 9) < 0.35:
            t.set(x + bx, cy - 1, z + bz, B.log(logk, "x" if bx else "z"))
    t.set(x, cy, z, B.log(logk))


def tree_spruce(t, x, y, z, seed=0, height=None):
    h = height or int(8 + hash2(x, z, seed) * 6)
    for i in range(1, h + 1):
        t.set(x, y + i, z, B.log("spruce"))
    top = y + h
    for i in range(h - 2):
        yy = top - i
        rad = min(3.2, 0.6 + i * 0.42) if i % 3 != 2 else max(0.8, min(3.2, 0.6 + i * 0.42) - 1.0)
        R = int(math.ceil(rad))
        dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
        m = (dx * dx + dz * dz) <= rad * rad + 0.3
        m &= ~((dx == 0) & (dz == 0))
        t.set_many(x + dx[m], np.full(int(m.sum()), yy), z + dz[m], B.leaves("spruce"), only_empty=True)
    t.set(x, top + 1, z, B.leaves("spruce"))
    t.set(x, top + 2, z, B.leaves("spruce"))


def tree_cypress(t, x, y, z, seed=0, height=None):
    """Slender columnar garden tree (dark oak leaves around a spruce trunk)."""
    h = height or int(7 + hash2(x, z, seed) * 4)
    for i in range(1, h):
        t.set(x, y + i, z, B.log("spruce"))
    for i in range(2, h + 2):
        f = (i - 2) / max(1, h - 1)
        rad = 1.4 * math.sin(math.pi * min(1.0, 0.15 + f * 0.95)) + 0.2
        R = int(math.ceil(rad))
        dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
        m = (dx * dx + dz * dz) <= rad * rad + 0.25
        t.set_many(x + dx[m], np.full(int(m.sum()), y + i), z + dz[m], B.leaves("dark_oak"), only_empty=True)


def bush(t, x, y, z, seed=0, r=1.4, kind="azalea"):
    R = int(math.ceil(r))
    dx, dy, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(0, R + 1), np.arange(-R, R + 1), indexing="ij")
    d = np.sqrt(dx * dx + (dy * 1.4) ** 2 + dz * dz)
    m = d <= r + (hash3(x + dx, y + dy, z + dz, seed) - 0.5) * 0.6
    leaf = B.leaves(kind) if kind in ("azalea", "flowering_azalea", "oak", "birch", "spruce", "dark_oak",
                                       "jungle", "acacia", "cherry", "mangrove") else B.S(kind)
    t.set_many(x + dx[m], y + 1 + dy[m], z + dz[m], leaf, only_empty=True)


def topiary(t, x, y, z, height=3, kind="oak"):
    """Clipped cone topiary in a planter."""
    for i in range(height):
        t.set(x, y + 1 + i, z, B.leaves(kind))
    t.set(x, y + 1, z + 1, B.leaves(kind), only_empty=True)
    t.set(x, y + 1, z - 1, B.leaves(kind), only_empty=True)
    t.set(x + 1, y + 1, z, B.leaves(kind), only_empty=True)
    t.set(x - 1, y + 1, z, B.leaves(kind), only_empty=True)


# ============================================================================== street furniture
def lamp_post(t, x, y, z, height=4, soul=False, double=False, facing="north"):
    """Black iron-style lamp post standing on the block at y: walls up to y+height, lantern on top
    (``double``: a cross-arm with two hanging lanterns left/right of ``facing``)."""
    t.set(x, y + 1, z, B.full("polished_blackstone"))
    for i in range(2, height + 1):
        t.set(x, y + i, z, B.wall("polished_blackstone"))
    if not double:
        t.set(x, y + height + 1, z, B.lantern(hanging=False, soul=soul))
        return
    t.set(x, y + height + 1, z, B.slab("polished_blackstone", "top"))
    t.set(x, y + height + 2, z, B.lantern(hanging=False, soul=soul))
    for d in (left_of(facing), right_of(facing)):
        vx, _, vz = DIR_VEC[d]
        t.set(x + vx, y + height + 1, z + vz, B.slab("polished_blackstone", "top"))
        t.set(x + vx, y + height, z + vz, B.lantern(hanging=True, soul=soul))


def bench(t, x, y, z, facing="south", length=3, wood="dark_oak"):
    """Bench of stairs on the block at y; sitters face ``facing``."""
    back = OPPOSITE[facing]
    rx, _, rz = DIR_VEC[right_of(facing)]
    for i in range(length):
        t.set(x + rx * i, y + 1, z + rz * i, B.stairs(wood, back))
    lx, _, lz = DIR_VEC[left_of(facing)]
    t.set(x + lx, y + 1, z + lz, B.trapdoor(wood, right_of(facing), "bottom", True))
    t.set(x + rx * length, y + 1, z + rz * length, B.trapdoor(wood, left_of(facing), "bottom", True))


def flower_bed(t, x0, z0, x1, z1, y, seed=0, border="smooth_castle_stone", flowers=None, soil="grass_block"):
    """Raised flower bed: border slabs around, soil top at y, flowers at y+1."""
    flowers = flowers or ["poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium",
                          "red_tulip", "white_tulip", "pink_tulip", "lily_of_the_valley", "blue_orchid"]
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y, z0, x1, y, z1, B.S(soil) if soil != "grass_block" else B.S("grass_block"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge and border:
                t.set(x, y + 1, z, B.slab(border, "bottom"))
            else:
                k = int(hash2(x, z, seed) * len(flowers))
                if hash2(z, x, seed + 3) < 0.85:
                    t.set(x, y + 1, z, B.S(flowers[k]))


# ============================================================================== interior furniture
def chandelier(t, x, y_ceiling, z, drop=3, arms=True):
    """Chain from below the ceiling block ``y_ceiling`` down ``drop`` blocks, chandelier at the bottom."""
    for i in range(1, drop):
        t.set(x, y_ceiling - i, z, B.chain())
    t.set(x, y_ceiling - drop, z, B.S("chandelier"))
    return y_ceiling - drop


def hanging_lantern(t, x, y_ceiling, z, drop=1, soul=False):
    for i in range(1, drop):
        t.set(x, y_ceiling - i, z, B.chain())
    t.set(x, y_ceiling - drop, z, B.lantern(hanging=True, soul=soul))


def sconce(t, x, y, z, out_dir, soul=False):
    """Wall sconce: a wall stub at (x, y, z) (the air block in front of the wall) with a lantern below."""
    t.set(x, y, z, B.wall("castle_bricks"))
    t.set(x, y - 1, z, B.lantern(hanging=True, soul=soul))


def kingdom_wall_banner(t, x, y, z, facing="south", color="blue", nbt=None):
    """Wall banner on the block at (x, y, z) attached to the wall behind it, cloth facing ``facing``."""
    t.set(x, y, z, B.wall_banner(color, facing))
    t.be(x, y, z, nbt or B.KINGDOM_BANNER)


def kingdom_banner(t, x, y, z, rotation=0, color="blue", nbt=None):
    t.set(x, y, z, B.banner(color, rotation))
    t.be(x, y, z, nbt or B.KINGDOM_BANNER)


def light_shaft(t, x, y, z, facing, length=14.0, width=2.0, height=4.0, pitch=38.0):
    """Invisible beam emitter just inside a window; the beam goes towards ``facing``."""
    t.set(x, y, z, B.light_shaft(facing))
    t.be(x, y, z, B.light_shaft_nbt(length, width, height, pitch))


def table(t, x0, z0, x1, z1, y, top="dark_oak", leg="dark_oak_fence", cloth=None):
    """Table standing on the floor block at y: legs at the corners, slab top at y+1."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y + 1, z0, x1, y + 1, z1, B.slab(top, "top") if not cloth else B.slab(top, "top"))
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        pass
    if cloth:
        t.fill(x0, y + 2, z0, x1, y + 2, z1, B.carpet(cloth))


def long_table(t, x, y, z, length, axis="z", top="dark_oak", cloth="white", chairs=True, chair_wood="spruce",
               candles=True, seed=0):
    """Banquet table along ``axis``: top at y+1 (planks), white table cloth carpet, chairs both sides,
    candles and dishes. (x, y, z) is the floor block under the first top block."""
    ax = (0, 1) if axis == "z" else (1, 0)
    side = (1, 0) if axis == "z" else (0, 1)
    for i in range(length):
        px, pz = x + ax[0] * i, z + ax[1] * i
        t.set(px, y + 1, pz, B.full(top) if not cloth else B.slab(top, "top"))
        if cloth:
            t.set(px, y + 2, pz, B.carpet(cloth))
        if chairs and 0 < i < length - 1:
            for s in (1, -1):
                cx, cz = px + side[0] * s, pz + side[1] * s
                # sitter faces the table: back is away from the table
                face_away = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(side[0] * s,
                                                                                                    side[1] * s)]
                t.set(cx, y + 1, cz, B.stairs(chair_wood, face_away))
        if candles and i % 3 == 1 and cloth:
            t.set(px, y + 2, pz, B.candle(3, "white"))
    return


def chair(t, x, y, z, faces="north", wood="spruce"):
    """Chair on the floor block at y; the sitter faces ``faces``."""
    t.set(x, y + 1, z, B.stairs(wood, OPPOSITE[faces]))


def bookshelf_wall(t, x0, z0, x1, z1, y0, y1, seed=0, chiseled_ratio=0.12, facing="south"):
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                if hash3(x, y, z, seed) < chiseled_ratio:
                    occ = {f"slot_{i}_occupied": hash3(x + i, y, z, seed + 1) < 0.7 for i in range(6)}
                    t.set(x, y, z, B.S("chiseled_bookshelf", facing=facing, **occ))
                else:
                    t.set(x, y, z, B.S("bookshelf"))


def carpet_runner(t, x0, z0, x1, z1, y, color="red", border="yellow"):
    """Carpet on top of the floor at y (carpet at y+1) with a border colour."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y + 1, z0, x1, y + 1, z1, B.carpet(color))
    if border:
        t.fill(x0, y + 1, z0, x0, y + 1, z1, B.carpet(border))
        t.fill(x1, y + 1, z0, x1, y + 1, z1, B.carpet(border))


# ============================================================================== entities
FACING_BYTE = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}


def armor_stand(t, x, y, z, yaw=0.0, armor="iron", weapon="minecraft:iron_sword", offhand=None, small=False,
                name=None, color=None, pose=None, base_plate=False):
    """Decorative armour stand standing on the floor block at y (feet at y+1). Invulnerable, locked slots."""
    def piece(kind):
        if armor is None:
            return "{}"
        tag = None
        if armor == "leather" and color is not None:
            tag = f"{{display:{{color:{int(color)}}}}}"
        return B.item_snbt(f"{armor}_{kind}", 1, tag)
    items = [piece("boots"), piece("leggings"), piece("chestplate"), piece("helmet")]
    hands = [B.item_snbt(weapon) if weapon else "{}", (offhand if offhand and offhand.startswith("{")
                                                       else (B.item_snbt(offhand) if offhand else "{}"))]
    nbt = (f"{{ShowArms:1b,NoBasePlate:{'0b' if base_plate else '1b'},Invulnerable:1b,DisabledSlots:4144959,"
           f"ArmorItems:[{','.join(items)}],HandItems:[{','.join(hands)}]")
    if small:
        nbt += ",Small:1b"
    if pose:
        nbt += f",Pose:{pose}"
    if name:
        nbt += f",CustomName:{B.snbt_str(name)}"
    nbt += "}"
    t.entity("minecraft:armor_stand", (x + 0.5, y + 1.0, z + 0.5), yaw, nbt)


def blue_shield():
    return ('{id:"minecraft:shield",Count:1b,tag:{BlockEntityTag:{Base:11,Patterns:[{Pattern:"er_crest",Color:4},'
            '{Pattern:"bo",Color:4}]}}}')


def item_frame(t, x, y, z, facing, item, rotation=0, glow=False, invisible=False):
    """Item frame occupying block (x, y, z), hanging on the wall behind it, facing ``facing``."""
    wf = _wdir(t, facing)
    nbt = (f"{{Facing:{FACING_BYTE[wf]}b,Item:{B.item_snbt(item) if not item.startswith('{') else item},"
           f"ItemRotation:{int(rotation)}b,Fixed:1b,Invulnerable:1b,Invisible:{'1b' if invisible else '0b'}}}")
    t.entity("minecraft:glow_item_frame" if glow else "minecraft:item_frame", (x + 0.5, y + 0.5, z + 0.5), 0.0,
             nbt)


def sign_text(t, x, y, z, state, lines, color="black", glowing=False):
    t.set(x, y, z, state)
    t.be(x, y, z, B.sign_nbt(lines, color=color, glowing=glowing))
