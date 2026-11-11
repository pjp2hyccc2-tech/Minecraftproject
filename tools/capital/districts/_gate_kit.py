"""ギルド門 The guild gates of the Guild Gate Terrace (not a district: files starting with "_" are not run).

Twenty monuments, one per guild-house design (data/eternalrealm/guildhouse/designs.json), each true to its theme,
sized by its tier (tier 1 five blocks wide, tier 5 nine wide and up to sixteen tall). Every gate has an opening
filled with the animated portal surface ``eternalrealm:guild_portal[axis, theme]`` (Java: guild.house.forge),
a plaque (a waxed sign whose text is translated in the client: name, tier stars, price, "right-click") and the
spot ``guildgate.<theme>`` (centre of the portal plane at feet height, yaw = the way a player faces walking in)
by which the game finds the gate.

Local frame of every gate (``gate()`` turns it to face the court or the street): the floor is the top of y = 0
(players stand at y = 1); the portal plane is z = 0 spanning x = -hw .. hw; the front is +z ("south"), the back
-z. Wall gates (``WALL``: the street wall and the upper court's retaining wall) stand in a six-block retaining
wall: its face is the plane z = +2 (wall blocks at z <= 2), the floor in front is z >= 3, the level above is y = 6
(players stand at 7) behind the wall; their crowns stand on that level. Free gates stand on the floor with the
terrace edge about five blocks behind the portal plane.
"""
from __future__ import annotations

import json
import math
import os

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3
from capital.world import OPPOSITE, YAW, Frame

AIR = "minecraft:air"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
with open(os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "guildhouse", "designs.json"),
          encoding="utf-8") as _f:
    DESIGNS = {d["id"]: d for d in json.load(_f)["designs"]}
ROT = {"south": 0, "west": 1, "north": 2, "east": 3}     # frame rotation that turns the local front to a direction
PLAQUE = "terrace"   # "house": the twin gate inside a guild house, whose plaque shows the way back instead of the price
F, ST, SL, WL = B.full, B.stairs, B.slab, B.wall


def S_(name, **kw):
    return B.S(name, **kw)


# ====================================================================================== common pieces
def portal_state(theme):
    return B.S("guild_portal", axis="x", theme=theme)


def rect(w, h):
    return np.ones((w, h), bool)


def arch(w, h, *cap):
    """Opening mask [u, v] (u across from -hw, v up from y = 1): ``h`` full rows, then the ``cap`` rows (strings
    of '#' and '.', e.g. ".#." for a pointed top)."""
    m = np.zeros((w, h + len(cap)), bool)
    m[:, :h] = True
    for i, row in enumerate(cap):
        m[:, h + i] = np.array([c == "#" for c in row], bool)
    return m


def carve(f, mask, z0, z1, state=AIR):
    """Write ``state`` into the opening ``mask`` for every plane z0..z1."""
    hw = mask.shape[0] // 2
    us, vs = np.nonzero(mask)
    for z in range(min(z0, z1), max(z0, z1) + 1):
        f.set_many(us - hw, vs + 1, np.full(us.size, z), state)


def portal(f, theme, mask, z=0):
    carve(f, mask, z, z, portal_state(theme))


def _line(d):
    return "'" + json.dumps(d, ensure_ascii=False, separators=(",", ":")).replace("\\", "\\\\").replace("'", "\\'") \
        + "'"


def plaque_nbt(theme, ink="black"):
    """The plaque's sign data: the design's name (translated in the client), tier stars, price, a hint."""
    d = DESIGNS[theme]
    front = [{"translate": f"guildhouse.eternalrealm.design.{theme}", "bold": True},
             {"text": "★" * d["tier"], "color": "gold"},
             {"text": f"{d['price']:,} G"},
             {"translate": "guildhouse.eternalrealm.plaque.hint", "italic": True}]
    if PLAQUE == "house":
        front = [{"translate": f"guildhouse.eternalrealm.design.{theme}", "bold": True},
                 {"text": "★" * d["tier"], "color": "gold"},
                 {"translate": "guildhouse.eternalrealm.plaque.home"},
                 {"translate": "guildhouse.eternalrealm.plaque.home_hint", "italic": True}]
    blank = ",".join(_line({"text": ""}) for _ in range(4))
    msgs = ",".join(_line(x) for x in front)
    return (f'{{front_text:{{messages:[{msgs}],color:"{ink}",has_glowing_text:1b}},'
            f'back_text:{{messages:[{blank}],color:"black",has_glowing_text:0b}},is_waxed:1b}}')


def plaque_post(f, theme, x, z, base, wood="dark_oak", ink="white", y=0, rotation=0, height=1):
    """A pedestal ``height`` blocks high with the plaque standing on it, facing the front."""
    for k in range(height):
        f.set(x, y + 1 + k, z, base if isinstance(base, str) else base[min(k, len(base) - 1)])
    f.set(x, y + 1 + height, z, B.sign(wood, rotation))
    f.be(x, y + 1 + height, z, plaque_nbt(theme, ink))


def plaque_wall(f, theme, x, y, z, wood="dark_oak", ink="white", facing="south"):
    """The plaque as a wall sign at (x, y, z), hung on the block behind it."""
    f.set(x, y, z, B.wall_sign(wood, facing))
    f.be(x, y, z, plaque_nbt(theme, ink))


def hang_lantern(f, x, y, z, soul=False, chain=0):
    for k in range(chain):
        f.set(x, y + 1 + k, z, B.chain())
    f.set(x, y, z, B.lantern(hanging=True, soul=soul))


def scatter(f, cells, states, seed):
    """Put states[hash] into the cells (deterministic variety)."""
    for (x, y, z) in cells:
        f.set(x, y, z, states[int(hash3(x, y, z, seed) * len(states)) % len(states)])


def box_cells(x0, y0, z0, x1, y1, z1):
    return [(x, y, z) for x in range(min(x0, x1), max(x0, x1) + 1) for y in range(min(y0, y1), max(y0, y1) + 1)
            for z in range(min(z0, z1), max(z0, z1) + 1)]


def mixfill(f, x0, y0, z0, x1, y1, z1, states, seed):
    scatter(f, box_cells(x0, y0, z0, x1, y1, z1), states, seed)


def ring_xy(f, cx, cy, z, r, state, thick=0.6):
    """A ring in the vertical plane z (x across, y up)."""
    R = int(math.ceil(r)) + 1
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            d = math.hypot(dx, dy)
            if abs(d - r) <= thick:
                f.set(cx + dx, cy + dy, z, state)


def disc_xy(f, cx, cy, z, r, state):
    R = int(math.ceil(r)) + 1
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            if math.hypot(dx, dy) <= r + 0.25:
                f.set(cx + dx, cy + dy, z, state)


def ball(f, cx, cy, cz, r, state, ry=None, only_empty=False, seed=0, rough=0.0):
    ry = ry or r
    R = int(math.ceil(max(r, ry))) + 1
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            for dz in range(-R, R + 1):
                d = (dx / (r + 0.3)) ** 2 + (dy / (ry + 0.3)) ** 2 + (dz / (r + 0.3)) ** 2
                if rough:
                    d += (hash3(cx + dx, cy + dy, cz + dz, seed) - 0.5) * rough
                if d <= 1.0:
                    f.set(cx + dx, cy + dy, cz + dz, state, only_empty=only_empty)


def threshold(f, hw, states, z0=0, z1=2, seed=0):
    """Paving of the gate's threshold (floor y = 0) from the portal to the front."""
    mixfill(f, -hw, 0, z0, hw, 0, z1, states, seed)


# ====================================================================================== wall gates: the street wall
def recess(f, mask, floor, z1=2):
    """Cut the doorway through the wall from its face back to the portal plane and floor it."""
    carve(f, mask, 0, z1)
    hw = mask.shape[0] // 2
    f.fill(-hw, 0, 0, hw, 0, z1, floor)


def g_northern_longhouse(f, th):
    """Street gate. A stave porch of tarred spruce under a turf roof: carved posts with painted round shields, the
    gable rising over the parapet with crossed barge boards ending in dragon heads, a runestone beside the door."""
    m = rect(3, 4)
    recess(f, m, F("spruce_planks"))
    f.fill(-2, 1, 0, -2, 4, 2, B.log("spruce", stripped=True))
    f.fill(2, 1, 0, 2, 4, 2, B.log("spruce", stripped=True))
    f.fill(-1, 5, 0, 1, 5, 2, B.log("spruce", "z"))
    portal(f, th, m)
    for x in (-2, 2):                                          # carved posts in front of the wall
        f.fill(x, 1, 3, x, 6, 3, B.log("spruce"))
        f.set(x, 3, 3, B.log("spruce", stripped=True))
        f.set(x, 0, 3, F("cobblestone"))
    f.fill(-2, 6, 2, 2, 6, 2, B.log("spruce", "x"))
    f.fill(-1, 5, 3, 1, 5, 3, AIR)
    f.set(-1, 5, 3, ST("spruce", "west", "top"))               # knee braces
    f.set(1, 5, 3, ST("spruce", "east", "top"))
    f.fill(-3, 7, 3, 3, 7, 3, ST("spruce", "north"))            # pent roof over the porch, turfed behind
    for x in (-3, 3):
        f.set(x, 6, 3, ST("spruce", "east" if x < 0 else "west", "top"))
        hang_lantern(f, x, 5, 3)
    # the gable over the parapet: plank walls, turf roof (ridge front-back), crossed dragon-head barge boards
    f.fill(-2, 7, -3, 2, 8, 2, F("spruce_planks"))
    f.fill(-1, 7, 2, 1, 9, 2, F("dark_oak_planks"))
    f.set(0, 8, 2, F("spruce_planks"))
    for (half, y, turf) in ((3, 9, False), (2, 10, True), (1, 11, True)):
        f.fill(-half, y, -4, -half, y, 2, F("grass_block") if turf else ST("spruce", "east"))
        f.fill(half, y, -4, half, y, 2, F("grass_block") if turf else ST("spruce", "west"))
        f.fill(-half + 1, y, -3, half - 1, y, 1, F("spruce_planks"))
    f.fill(0, 12, -4, 0, 12, 2, F("moss_block"))
    for (x, y) in ((-3, 9), (-2, 10), (-1, 11), (0, 12), (1, 11), (2, 10), (3, 9)):   # barge boards at the front
        f.set(x, y, 3, B.log("dark_oak", "z"))
    f.set(-1, 13, 3, ST("dark_oak", "east", "top"))           # crossed horns: the dragon heads
    f.set(1, 13, 3, ST("dark_oak", "west", "top"))
    f.set(-2, 14, 3, ST("dark_oak", "east"))
    f.set(2, 14, 3, ST("dark_oak", "west"))
    f.set(0, 10, 3, S_("spruce_trapdoor", facing="south", half="bottom", open=True))
    f.set(0, 10, 2, F("yellow_stained_glass"))
    for x, c, p in ((-2, "white", [("mc", "blue"), ("bo", "white")]), (2, "yellow", [("mc", "red"), ("bo", "yellow")])):
        f.set(x, 4, 4, B.wall_banner(c, "south"))            # round shields on the posts
        f.be(x, 4, 4, B.banner_nbt(p))
    for (x, z) in ((-2, -2), (2, 0), (1, -3)):                   # snow patches on the turf
        y = 10 if abs(x) == 2 else 11
        f.set(x, y + 1, z, B.S("snow", layers=2), only_empty=True)
    for x in (-2, 2):
        f.set(x, 7, -4, F("cobblestone"))
        f.set(x, 8, -4, B.S("campfire", lit=True))
    plaque_wall(f, th, 2, 2, 4, wood="spruce", ink="white")
    f.set(-3, 1, 3, F("mossy_cobblestone"))                   # runestone
    f.set(-3, 2, 3, F("chiseled_stone_bricks"))
    f.set(-3, 3, 3, SL("stone_bricks"))


def g_mushroom_grove(f, th):
    """Street gate. A giant fly-agaric grown out of the wall: its stem is the doorway, the spotted cap overhangs the
    parapet with glowing gills; small mushrooms, lichen and moss at its foot."""
    m = arch(3, 3, "###", ".#.")
    for z in range(0, 4):                                      # the stem, flaring at the foot
        f.fill(-2, 1, z, 2, 7, z, F("mushroom_stem"))
    for (x, z) in ((-3, 3), (3, 3), (-3, 2), (3, 2)):
        f.set(x, 1, z, F("mushroom_stem"))
        f.set(x, 2, z, F("mushroom_stem") if z == 2 else AIR)
    recess(f, m, F("podzol"), z1=3)
    f.set(-1, 4, 3, ST("birch", "west", "top"))
    f.set(1, 4, 3, ST("birch", "east", "top"))
    portal(f, th, m)
    f.fill(-1, 7, -2, 1, 8, 1, F("mushroom_stem"))
    # the cap: a flattened dome of red mushroom block with white spots, gills underneath
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            r = math.hypot(dx, dz + 0.5)
            if r > 5.2:
                continue
            top = 12.6 - (r / 5.2) ** 2 * 3.6
            for y in range(9, int(top) + 1):
                outer = y >= int(top) - 0 or r >= 4.4
                if outer:
                    spot = hash3(dx, y, dz, 31) < 0.15
                    f.set(dx, y, dz, F("mushroom_stem") if spot else F("red_mushroom_block"))
                else:
                    f.set(dx, y, dz, F("red_mushroom_block"))
            if r <= 4.6:
                f.set(dx, 8, dz, F("shroomlight") if hash2(dx, dz, 12) < 0.16 else F("mushroom_stem"),
                      only_empty=True)
    for dx in range(-5, 6):                                    # the cap's rim curls down
        for dz in range(-5, 6):
            r = math.hypot(dx, dz + 0.5)
            if 4.4 < r <= 5.2:
                f.set(dx, 8, dz, F("red_mushroom_block"))
    for (x, z) in ((-3, 4), (3, 4)):
        f.set(x, 1, z, F("mushroom_stem"))
        f.set(x, 2, z, F("brown_mushroom_block"))
        f.set(x, 3, z, SL("birch", "bottom") if False else F("brown_mushroom_block"))
    f.set(-4, 1, 3, B.potted("red_mushroom"))
    f.set(4, 1, 3, B.potted("brown_mushroom"))
    for (x, y, z) in ((-2, 3, 4), (2, 5, 4), (-2, 6, 4), (2, 2, 4)):
        f.set(x, y, z, S_("glow_lichen", north=True))
    f.fill(-3, 0, 3, 3, 0, 4, F("moss_block"))
    f.fill(-1, 0, 3, 1, 0, 4, F("podzol"))
    for (x, z) in ((-4, -2), (4, -1), (-3, -4), (3, -4)):
        f.set(x, 7, z, F("mushroom_stem"))
        f.set(x, 8, z, F("brown_mushroom_block") if x < 0 else F("red_mushroom_block"))
    plaque_wall(f, th, 2, 2, 4, wood="mangrove", ink="white")


def g_dwarven_forge(f, th):
    """Street gate. A mine mouth in a rugged outcrop seamed with ore: a timbered adit under a rune-carved lintel,
    lanterns on iron brackets, an anvil on the ledge; over the parapet a winding-tower headframe and the forge
    chimney smoking."""
    rock = [F("deepslate"), F("cobbled_deepslate"), F("tuff"), F("andesite"), F("stone"), F("deepslate"),
            F("deepslate_iron_ore"), F("deepslate_gold_ore"), F("cobbled_deepslate"), F("deepslate_copper_ore"),
            F("tuff"), F("deepslate_diamond_ore"), F("deepslate_redstone_ore") if False else F("deepslate_lapis_ore")]
    for x in range(-4, 5):
        for y in range(0, 9):
            for z in (2, 3):
                edge = 0.72 - abs(x) * 0.06 - y * 0.04 if z == 3 else (1.0 if y < 7 else 0.75 - abs(x) * 0.1)
                if hash3(x, y, z, 77) < edge:
                    f.set(x, y, z, rock[int(hash3(x, y, z, 78) * len(rock))])
    m = rect(3, 4)
    recess(f, m, F("gravel"), z1=3)
    portal(f, th, m)
    for z in (1, 3):                                           # timber sets
        f.fill(-2, 1, z, -2, 4, z, B.log("spruce"))
        f.fill(2, 1, z, 2, 4, z, B.log("spruce"))
        f.fill(-2, 5, z, 2, 5, z, B.log("spruce", "x"))
    f.fill(-2, 6, 3, 2, 6, 3, F("polished_deepslate"))          # rune lintel
    f.set(0, 6, 3, F("chiseled_deepslate"))
    f.set(-1, 6, 3, F("chiseled_polished_blackstone"))
    f.set(1, 6, 3, F("chiseled_polished_blackstone"))
    f.set(0, 7, 3, F("gilded_blackstone"))
    for x in (-3, 3):                                          # lanterns on iron brackets
        f.set(x, 5, 3, F("polished_deepslate"))
        f.set(x, 5, 4, B.S("iron_bars"))
        hang_lantern(f, x, 4, 4)
    f.set(-4, 1, 3, F("polished_deepslate"))
    f.set(-4, 2, 3, S_("anvil", facing="east"))
    f.set(4, 1, 3, B.barrel("up"))
    f.set(4, 2, 3, F("raw_iron_block"))
    # headframe over the parapet: two legs with raking struts, the sheave wheel, the forge chimney
    for x in (-2, 2):
        for y in range(7, 14):
            f.set(x, y, -1, B.log("spruce"))
        f.set(x, 7, -4, B.log("spruce"))
        for k in range(1, 4):
            f.set(x, 7 + k * 2 - 1, -4 + k, B.fence("spruce"))
            f.set(x, 7 + k * 2, -4 + k, B.fence("spruce"))
    f.fill(-2, 14, -1, 2, 14, -1, B.log("spruce", "x"))
    ring_xy(f, 0, 16, -1, 2.0, B.fence("dark_oak"), thick=0.5)
    f.set(0, 16, -1, F("iron_block"))
    for y in range(7, 14):
        f.set(0, y, -1, B.chain())
    f.set(0, 7, -1, AIR)
    f.set(0, 7, -2, B.barrel("up"))
    f.fill(3, 7, -4, 4, 11, -3, F("bricks"))
    f.set(3, 11, -4, F("magma_block"))
    f.set(3, 12, -4, S_("smoke_emitter", heavy=True))
    f.set(4, 12, -3, SL("polished_blackstone_bricks"))
    plaque_wall(f, th, 3, 2, 4, wood="spruce", ink="yellow")
    f.set(3, 2, 3, F("polished_deepslate"))


def g_ancient_ruins(f, th):
    """Street gate. A broken round arch of weathered temple masonry, half fallen; ivy pours down the wall, runes glow
    on the voussoirs; over the parapet the stumps of a colonnade and a fallen lintel."""
    stones = [F("mossy_stone_bricks"), F("cracked_stone_bricks"), F("stone_bricks"), F("mossy_stone_bricks"),
              F("chiseled_stone_bricks")]
    m = arch(3, 4, ".#.")
    recess(f, m, F("mossy_cobblestone"))
    mixfill(f, -2, 1, 0, -2, 5, 2, stones, 3)
    mixfill(f, 2, 1, 0, 2, 5, 2, stones, 4)
    mixfill(f, -1, 6, 0, 1, 6, 2, stones, 5)
    f.set(-1, 5, 0, ST("mossy_stone_bricks", "west", "top"))
    f.set(1, 5, 0, ST("mossy_stone_bricks", "east", "top"))
    portal(f, th, m)
    for x in (-2, 2):                                          # the columns in front of the wall
        f.set(x, 0, 3, F("stone_bricks"))
        for y in range(1, 6):
            f.set(x, y, 3, F("chiseled_stone_bricks") if y in (1, 5) else
                  F("polished_andesite") if hash3(x, y, 3, 9) < 0.6 else F("andesite"))
    f.set(-2, 6, 3, ST("stone_bricks", "east", "top"))        # the arch, its right half fallen
    f.set(-1, 7, 3, ST("stone_bricks", "east", "top"))
    f.set(0, 7, 3, F("chiseled_stone_bricks"))
    f.set(1, 7, 3, ST("mossy_stone_bricks", "west", "top"))
    f.set(-1, 6, 3, F("mossy_stone_bricks"))
    f.set(2, 5, 3, ST("cobblestone", "west"))
    for (x, y, z) in ((3, 1, 3), (3, 1, 4), (2, 1, 4), (-3, 1, 4)):
        f.set(x, y, z, SL("cobblestone") if hash3(x, y, z, 2) < 0.5 else ST("mossy_cobblestone", "west"))
    f.set(-3, 1, 3, F("mossy_cobblestone"))
    for (x, y) in ((-1, 6), (1, 6), (0, 7)):
        f.set(x, y, 4 if y == 7 else 3, S_("glow_lichen", north=True), only_empty=True)
    f.set(-2, 4, 4, S_("glow_lichen", north=True))
    for x in (-3, -1, 1, 3):                                   # ivy curtains down the wall face
        for y in range(2 + int(hash2(x, 0, 4) * 3), 7):
            f.set(x, y, 3, S_("vine", north=True), only_empty=True)
    for (x, h) in ((-2, 3), (2, 5)):                           # over the parapet: column stumps, a fallen lintel
        for y in range(7, 7 + h):
            f.set(x, y, -1, F("polished_andesite") if y % 3 else F("chiseled_stone_bricks"))
    f.set(2, 12, -1, SL("stone_bricks"))
    f.fill(-1, 7, -2, 1, 7, -2, F("cracked_stone_bricks"))
    f.set(0, 7, -1, F("chiseled_stone_bricks"))
    f.set(0, 8, -1, F("mossy_stone_bricks"))
    for (x, y, z) in ((-2, 10, 0), (2, 12, 0), (-1, 8, -1), (1, 8, 0)):
        f.set(x, y, z, B.leaves("azalea"), only_empty=True)
    f.set(-3, 7, 0, S_("fern"))
    f.set(3, 7, -1, S_("fern"))
    plaque_wall(f, th, -2, 2, 4, wood="birch", ink="black")


def g_pirate_ship(f, th):
    """Street gate. The stern of a pirate ship moored against the terrace: carved transom with lit gallery windows,
    the captain's cabin door is the portal, the name board is the plaque; over the parapet the poop deck rail, stern
    lanterns and the mizzen mast with a black sail and the jolly roger."""
    hull = F("dark_oak_planks")
    m = rect(3, 4)
    f.fill(-3, 0, 3, 3, 7, 3, hull)                            # the transom
    f.fill(-3, 1, 2, -3, 7, 2, hull)
    f.fill(3, 1, 2, 3, 7, 2, hull)
    recess(f, m, F("spruce_planks"), z1=3)
    portal(f, th, m)
    f.fill(-2, 1, 4, 2, 1, 4, ST("dark_oak", "north", "top"))
    for (x, y) in ((-2, 5), (2, 5), (-2, 6), (2, 6)):          # the stern gallery windows, lit
        f.set(x, y, 3, F("yellow_stained_glass"))
    f.fill(-3, 4, 4, 3, 4, 4, ST("spruce", "north", "top"))    # gallery walkway under the windows
    f.fill(-3, 8, 3, 3, 8, 3, ST("dark_oak", "north", "top"))   # the taffrail moulding
    f.fill(-3, 9, 3, 3, 9, 3, B.fence("dark_oak"))
    for x in (-3, 3):                                          # stern lanterns
        f.set(x, 9, 3, F("gilded_trim"))
        f.set(x, 10, 3, B.lantern())
    for x in (-2, 2):
        f.set(x, 3, 3, F("gilded_trim"))
        f.set(x, 7, 3, F("gilded_trim"))
    f.set(0, 7, 3, F("gilded_trim"))
    f.set(-1, 6, 3, F("spruce_planks"))
    f.set(1, 6, 3, F("spruce_planks"))
    f.set(0, 6, 3, F("spruce_planks"))
    f.set(0, 5, 3, F("spruce_planks"))
    plaque_wall(f, th, 0, 6, 4, wood="dark_oak", ink="yellow")    # the ship's name board
    f.set(-1, 6, 4, S_("dark_oak_trapdoor", facing="south", half="top", open=True))
    f.set(1, 6, 4, S_("dark_oak_trapdoor", facing="south", half="top", open=True))
    # poop deck on the terrace with the mizzen mast
    f.fill(-3, 7, -3, 3, 7, 2, F("spruce_planks"))
    f.fill(-3, 8, -3, -3, 8, 2, B.fence("dark_oak"))
    f.fill(3, 8, -3, 3, 8, 2, B.fence("dark_oak"))
    for y in range(8, 21):
        f.set(0, y, -1, B.log("spruce"))
    f.fill(-3, 16, -1, 3, 16, -1, B.log("spruce", "x"))        # yard with the black sail
    f.fill(-3, 12, -2, 3, 15, -2, F("black_wool"))
    f.fill(-2, 11, -2, 2, 11, -2, F("black_wool"))
    for (x, y) in ((0, 14), (-1, 14), (1, 14), (0, 13), (-1, 12), (1, 12)):   # a skull painted on the sail
        f.set(x, y, -2, F("white_wool"))
    f.fill(-1, 19, -2, 1, 19, 0, SL("dark_oak", "top"))          # crow's nest
    f.fill(-1, 20, -2, 1, 20, 0, B.fence("dark_oak"))
    f.fill(0, 20, -1, 0, 20, -1, B.log("spruce"))
    f.set(0, 21, -1, B.fence("dark_oak"))
    f.set(0, 22, -1, B.fence("dark_oak"))
    f.set(0, 22, 0, B.wall_banner("black", "south"))
    f.be(0, 22, 0, B.banner_nbt([("sku", "white"), ("cr", "white")]))
    for x in (-3, 3):                                          # shrouds from the deck rail to the yard
        for k in range(1, 8):
            f.set(x - (1 if x > 0 else -1) * (k // 3), 8 + k, -3 + (k // 4), B.chain(), only_empty=True)
    f.set(2, 8, 1, B.barrel("up"))
    f.set(-2, 8, 1, F("hay_block"))
    f.set(2, 9, 1, B.lantern())
    f.set(-2, 8, -2, S_("bell", attachment="floor", facing="south"))


# ====================================================================================== the lower court's west end
def g_flower_glasshouse(f, th):
    """Free gate (west end of the lower court). A little white-framed conservatory full of flowers; its glazed door
    is the portal. Barrel-vaulted glass roof with white ribs, a fanlight and a gilded finial; roses climb the corner
    posts, planters line the sides."""
    q, qp = F("smooth_quartz"), S_("quartz_pillar", axis="y")
    f.fill(-3, 0, -5, 3, 0, 1, F("polished_diorite"))
    f.fill(-3, 1, -5, 3, 1, 0, F("calcite"))                  # low base wall
    for (x, z) in ((-3, 0), (3, 0), (-3, -5), (3, -5), (-3, -2), (3, -2)):
        f.fill(x, 1, z, x, 6, z, qp)
    for x in (-3, 3):
        f.fill(x, 2, -4, x, 5, -3, B.pane())
        f.fill(x, 2, -1, x, 5, -1, B.pane())
    f.fill(-2, 2, -5, 2, 5, -5, B.pane())
    f.fill(-2, 1, 0, 2, 6, 0, q)
    m = arch(3, 4, ".#.")
    carve(f, m, 0, 0)
    portal(f, th, m)
    f.set(-1, 5, 0, ST("quartz", "west", "top"))
    f.set(1, 5, 0, ST("quartz", "east", "top"))
    for z in range(-5, 1):                                     # the barrel-vaulted glass roof
        rib = z in (-5, -2, 0)
        for (x, y) in ((-3, 7), (-2, 8), (-1, 9), (0, 9), (1, 9), (2, 8), (3, 7)):
            f.set(x, y, z, q if rib else F("glass"))
        f.set(-2, 7, z, q if rib else F("glass"))
        f.set(2, 7, z, q if rib else F("glass"))
        f.set(-3, 6, z, q)
        f.set(3, 6, z, q)
        f.fill(-1, 8, z, 1, 8, z, AIR)
        f.fill(-1, 7, z, 1, 7, z, AIR)
    f.fill(-2, 6, 0, 2, 6, 0, q)
    for (x, y) in ((-1, 7), (0, 7), (1, 7), (-1, 8), (1, 8), (0, 8), (-2, 7), (2, 7)):   # fanlight
        f.set(x, y, 0, F("pink_stained_glass") if (x + y) % 2 else F("glass"))
    f.set(0, 8, 0, q)
    f.set(0, 10, 0, S_("gilded_finial"))
    f.set(0, 10, -5, S_("gilded_finial"))
    f.fill(-1, 10, -4, 1, 10, -1, SL("quartz", "bottom"))
    # inside: flower beds and a little basin, lanterns under the ribs
    f.fill(-2, 1, -4, 2, 1, -1, F("moss_block"))
    f.fill(-1, 1, -3, 1, 1, -2, S_("water", level=0))
    f.set(0, 1, -3, F("prismarine_bricks"))
    f.set(0, 2, -3, S_("fountain_spray", power=1, facing="up"))
    for i, (x, z) in enumerate(((-2, -4), (-1, -4), (1, -4), (2, -4), (-2, -1), (2, -1), (-2, -2), (2, -3))):
        p = ("rose_bush", "peony", "lilac", "sunflower")[i % 4]
        lo, hi = B.tall_plant_states(p)
        f.set(x, 2, z, lo)
        f.set(x, 3, z, hi)
    for (x, z) in ((-1, -1), (1, -1), (0, -4)):
        f.set(x, 2, z, S_(("allium", "pink_tulip", "cornflower")[(x + z) % 3]))
    for z in (-4, -1):
        hang_lantern(f, 0, 8, z)
    for x in (-4, 4):                                          # planters outside, roses up the front posts
        for z in (-4, -3, -1):
            f.set(x, 1, z, F("spruce_planks") if z != -3 else F("moss_block"))
            f.set(x, 2, z, S_(("red_tulip", "oxeye_daisy", "azure_bluet")[(z + 4) % 3]) if z != -3 else
                  B.leaves("flowering_azalea"))
        f.set(x, 1, 0, F("moss_block"))
        lo, hi = B.tall_plant_states("rose_bush")
        f.set(x, 2, 0, lo)
        f.set(x, 3, 0, hi)
    f.fill(-1, 0, 1, 1, 0, 2, F("pink_terracotta"))
    plaque_post(f, th, 3, 2, [F("calcite")], wood="birch", ink="black")


# ====================================================================================== wall gates: the upper wall
def g_great_tree(f, th):
    """Upper-wall gate. A giant tree grows out of the terrace wall: two trunks rise from a tangle of roots spilling
    onto the lower court and lean together into one crown spreading over the upper court; the doorway is the hollow
    between them. Glow berries hang from the canopy."""
    wood = S_("dark_oak_wood", axis="y")
    for (x0, x1) in ((-3, -2), (2, 3)):
        f.fill(x0, 1, 1, x1, 6, 3, wood)
    for y in range(7, 10):                                     # the trunks lean in and merge
        lean = y - 6
        f.fill(-3 + lean, y, 0, -2 + lean, y, 2, wood)
        f.fill(2 - lean, y, 0, 3 - lean, y, 2, wood)
    f.fill(-1, 9, -1, 1, 13, 1, wood)
    f.fill(0, 14, -1, 0, 15, 0, wood)
    m = arch(3, 6, "###", ".#.")
    recess(f, m, F("rooted_dirt"), z1=3)
    portal(f, th, m)
    for (x, y, z, ax) in ((-4, 1, 3, "x"), (4, 1, 3, "x"), (-5, 1, 4, "x"), (5, 1, 4, "x"), (-3, 1, 4, "z"),
                          (3, 1, 4, "z"), (-4, 1, 5, "z"), (4, 2, 3, "y"), (-4, 2, 2, "y")):
        f.set(x, y, z, S_("dark_oak_wood", axis=ax))
    for (x, z) in ((-6, 4), (6, 5), (-2, 5), (2, 5), (-5, 5), (5, 3)):
        f.set(x, 1, z, S_("moss_carpet"))
    for (x, z) in ((-3, 5), (3, 6), (-6, 3), (6, 4), (0, 5)):
        f.set(x, 1, z, S_(("lily_of_the_valley", "azure_bluet", "fern", "allium", "blue_orchid")[(x + z) % 5]),
              only_empty=True)
    # crown over the upper court
    for dx in range(-7, 8):
        for dy in range(10, 19):
            for dz in range(-7, 5):
                d = math.sqrt((dx / 6.6) ** 2 + ((dy - 14.0) / 3.8) ** 2 + ((dz + 1.5) / 5.4) ** 2)
                d += (hash3(dx, dy, dz, 61) - 0.5) * 0.24
                if d <= 1.0:
                    k = hash3(dx, dy, dz, 62)
                    st = B.leaves("flowering_azalea") if k < 0.16 else B.leaves("azalea") if k < 0.38 else \
                        F("shroomlight") if k > 0.985 else B.leaves("oak")
                    f.set(dx, dy, dz, st, only_empty=True)
    for (bx, bz) in ((-4, -3), (4, -2), (-3, 2), (3, 1)):     # boughs
        f.set(bx, 12, bz, S_("dark_oak_wood", axis="x"))
        f.set(bx // 2, 12, bz // 2, S_("dark_oak_wood", axis="x"))
    for (x, z) in ((-5, -3), (5, 1), (-6, 0), (6, -2), (-3, 3), (3, 3), (-1, -5), (-6, 2), (2, -6)):  # glow berries
        y = 13
        while y > 10 and f.is_empty(x, y, z):
            y -= 1
        while y > 10 and not f.is_empty(x, y - 1, z):
            y -= 1
        if not f.is_empty(x, y, z):
            n = 2 + int(hash2(x, z, 3) * 3)
            for k in range(1, n + 1):
                if f.is_empty(x, y - k, z) and y - k >= 8:
                    f.set(x, y - k, z, S_("cave_vines", age=25, berries=k % 2 == 1) if k == n else
                          S_("cave_vines_plant", berries=k % 2 == 1))
    for (x, y, z) in ((-4, 4, 4), (4, 5, 4), (-3, 8, 3), (3, 7, 3)):
        f.set(x, y, z, S_("vine", north=True), only_empty=True)
    plaque_post(f, th, 4, 6, [B.log("oak")], wood="oak", ink="black")


def g_sage_library(f, th):
    """Upper-wall gate. A round-arched doorway whose piers are bookcases under a cornice of candles; on the upper
    court a great open book lies on its stand, a ribbon hanging from its pages, a quill in the inkpot."""
    stone = F("stone_bricks")
    f.fill(-3, 0, 3, 3, 0, 4, F("polished_andesite"))
    for x0, x1 in ((-3, -2), (2, 3)):
        f.fill(x0, 1, 3, x1, 5, 3, F("bookshelf"))
        f.fill(x0, 0, 3, x1, 0, 3, F("chiseled_stone_bricks"))
        f.fill(x0, 1, 4, x1, 1, 4, ST("spruce", "north"))
    f.set(-3, 3, 3, S_("chiseled_bookshelf", facing="south", slot_0_occupied=True, slot_1_occupied=True,
                       slot_3_occupied=True))
    f.set(3, 4, 3, S_("chiseled_bookshelf", facing="south", slot_0_occupied=True, slot_2_occupied=True,
                      slot_3_occupied=True, slot_5_occupied=True))
    f.fill(-3, 6, 3, 3, 6, 3, stone)
    f.fill(-1, 1, 3, 1, 6, 3, stone)
    m = arch(3, 4, ".#.")
    carve(f, m, 3, 3)
    recess(f, m, F("spruce_planks"))
    portal(f, th, m)
    f.set(-1, 5, 3, ST("stone_bricks", "west", "top"))
    f.set(1, 5, 3, ST("stone_bricks", "east", "top"))
    f.set(0, 6, 3, F("chiseled_stone_bricks"))
    f.fill(-4, 7, 3, 4, 7, 3, ST("polished_andesite", "north", "top"))   # cornice with candles
    f.fill(-4, 6, 2, 4, 6, 2, F("polished_andesite"))
    for x in (-4, -2, 2, 4):
        f.set(x, 7, 2, B.candle(1 + (x + 4) % 3, "white"))
    for x in (-3, 3):
        hang_lantern(f, x, 5, 4)
        f.set(x, 6, 4, B.S("iron_bars"))
    # the open book on its stand (on the upper court)
    f.fill(-1, 7, -1, 1, 7, 0, F("spruce_planks"))
    f.fill(0, 8, -1, 0, 8, 0, F("spruce_planks"))
    for x in range(-4, 5):
        if x == 0:
            continue
        h = 10 if abs(x) <= 2 else 9
        for z in (-2, -1, 0):
            f.set(x, h, z, F("white_wool"))
        f.set(x, h + 1 if abs(x) <= 1 else h, 1 if False else -2, F("white_wool"))
        f.set(x, h - 1, -1, F("white_wool") if abs(x) <= 2 else ST("birch", "east" if x < 0 else "west", "top"))
    for z in (-2, -1, 0):
        f.set(0, 9, z, F("brown_wool"))
    f.fill(-4, 9, -1, -4, 9, -1, ST("birch", "east"))
    f.fill(4, 9, -1, 4, 9, -1, ST("birch", "west"))
    f.set(0, 8, 1, S_("red_wall_banner", facing="south"))
    f.set(3, 7, -3, S_("flower_pot"))
    f.set(3, 8, -3, S_("end_rod", facing="up"))
    plaque_post(f, th, -4, 4, [F("chiseled_stone_bricks")], wood="spruce", ink="yellow")
    f.set(4, 1, 4, S_("lectern", facing="south", has_book=True))


def g_japanese_mansion(f, th):
    """Upper-wall gate. A roofed mansion gate (yakuimon) in a white plastered wall with a tiled coping: vermilion
    pillars with black caps, the name board on the pillar, a dark tiled gable roof with ridge-end tiles reaching over
    the upper court's edge, stone lanterns and a cherry tree leaning over the wall."""
    red, black, plaster = F("red_concrete"), F("black_concrete"), F("white_plaster")
    tile = "deepslate_tiles"
    f.fill(-6, 1, 2, 6, 5, 2, plaster)                         # plastered wall with a tiled coping
    f.fill(-6, 0, 2, 6, 0, 2, F("stone_bricks"))
    f.fill(-6, 6, 2, 6, 6, 2, F(tile))
    f.fill(-6, 6, 3, 6, 6, 3, SL(tile, "bottom"))
    m = rect(5, 5)
    carve(f, m, 0, 3)
    f.fill(-2, 0, 0, 2, 0, 3, F("polished_andesite"))
    f.fill(-1, 0, 1, 1, 0, 3, F("smooth_stone"))
    portal(f, th, rect(3, 5))
    f.fill(-2, 1, 0, -2, 5, 0, AIR)
    f.fill(2, 1, 0, 2, 5, 0, AIR)
    for x in (-2, 2):                                          # four vermilion pillars
        for z in (0, 3):
            f.fill(x, 1, z, x, 6, z, red)
            f.set(x, 0, z, F("polished_andesite"))
            f.set(x, 7, z, black)
    f.fill(-1, 1, 0, -1, 5, 0, AIR)
    f.fill(-3, 6, 3, 3, 6, 3, red)                             # tie beams
    f.fill(-3, 6, 0, 3, 6, 0, red)
    f.fill(-2, 7, 0, 2, 7, 3, F("dark_oak_planks"))
    portal(f, th, rect(3, 5))
    # roof: tiled gable, ridge across the gate, eaves over both sides of the wall
    for (y, z0, z1) in ((8, -1, 4), (9, 0, 3), (10, 1, 2)):
        f.fill(-4, y, z0, 4, y, z0, ST(tile, "south"))
        f.fill(-4, y, z1, 4, y, z1, ST(tile, "north"))
        if z1 - z0 > 1:
            f.fill(-3, y, z0 + 1, 3, y, z1 - 1, F(tile))
    f.fill(-4, 11, 1, 4, 11, 2, SL(tile, "bottom"))
    for x in (-5, 5):
        f.fill(x, 8, -1, x, 8, 4, SL(tile, "top"))
        f.set(x, 11, 1, F("chiseled_deepslate"))              # ridge-end tiles
        f.set(x, 11, 2, F("chiseled_deepslate"))
        f.set(x, 12, 1, SL("deepslate_tiles"))
    for (x, z) in ((-5, -1), (5, -1), (-5, 4), (5, 4)):         # upturned eave corners
        f.set(x, 9, z, SL(tile, "bottom"))
    f.fill(-3, 7, 4, 3, 7, 4, ST("dark_oak", "north", "top"))
    f.fill(-3, 7, -1, 3, 7, -1, ST("dark_oak", "south", "top"))
    f.set(0, 7, 4, F("gilded_trim"))
    plaque_wall(f, th, 2, 3, 4, wood="spruce", ink="white")    # the vertical name board on the pillar
    for x in (-5, 5):                                          # stone lanterns
        f.set(x, 1, 4, S_("andesite_wall"))
        f.set(x, 2, 4, F("polished_andesite"))
        f.set(x, 3, 4, B.lantern())
        f.set(x, 4, 4, SL("andesite", "bottom"))
    # a cherry tree in the upper court leaning over the wall's left end
    for (x, y, z) in ((-6, 7, -2), (-6, 8, -2), (-6, 9, -1), (-5, 10, 0)):
        f.set(x, y, z, B.log("cherry"))
    for dx in range(-9, -1):
        for dy in range(9, 14):
            for dz in range(-4, 4):
                if math.hypot((dx + 5.5) * 0.85, (dy - 11) * 1.3, dz) < 2.9 and hash3(dx, dy, dz, 3) < 0.88:
                    f.set(dx, dy, dz, B.leaves("cherry"), only_empty=True)
    for (x, z, n) in ((-3, 4, 3), (-4, 5, 2), (-1, 5, 1), (-6, 4, 2)):
        f.set(x, 1, z, S_("pink_petals", facing="north", flower_amount=n), only_empty=True)


def g_jungle_temple(f, th):
    """Upper-wall gate. The front of a stepped pyramid swallowed by the jungle: a corbelled doorway, serpent heads
    guarding the steps, bands of gold and carved glyphs, a shrine with a golden idol on the upper court, vines and
    leaves everywhere."""
    stones = [F("mossy_cobblestone"), F("mossy_stone_bricks"), F("cobblestone"), F("mossy_stone_bricks"),
              F("cracked_stone_bricks")]
    mixfill(f, -4, 1, 2, 4, 3, 4, stones, 40)                   # tier 1 juts out of the wall
    f.fill(-4, 3, 4, 4, 3, 4, F("chiseled_stone_bricks"))
    mixfill(f, -3, 4, 2, 3, 6, 3, stones, 41)                   # tier 2
    f.fill(-3, 6, 3, 3, 6, 3, F("gold_block"))
    m = arch(3, 3, "###", ".#.")
    recess(f, m, F("mossy_stone_bricks"), z1=4)
    f.set(-1, 4, 3, ST("mossy_stone_bricks", "west", "top"))
    f.set(1, 4, 3, ST("mossy_stone_bricks", "east", "top"))
    f.set(-1, 4, 4, AIR)
    f.set(1, 4, 4, AIR)
    f.set(0, 4, 4, F("chiseled_stone_bricks"))
    f.set(0, 5, 4, AIR)
    portal(f, th, m)
    for x in (-2, 2):                                          # glyph stones with emerald eyes
        f.set(x, 5, 4, F("emerald_block"))
        f.set(x, 2, 5, AIR)
    for x in (-4, 4):                                          # serpent heads at the foot
        f.set(x, 1, 5, ST("mossy_stone_bricks", "north"))
        f.set(x, 2, 5, ST("mossy_stone_bricks", "north", "top"))
        f.set(x, 1, 6, ST("mossy_stone_bricks", "north"))
        f.set(x, 3, 5, S_("torch"))
    # the shrine and idol on the upper court
    mixfill(f, -2, 7, -2, 2, 8, 1, stones, 42)
    f.fill(-2, 8, 1, 2, 8, 1, F("chiseled_stone_bricks"))
    f.fill(-1, 9, -1, 1, 9, 0, F("gold_block"))
    f.set(0, 10, -1, F("gold_block"))
    f.set(0, 11, -1, F("raw_gold_block"))
    f.set(-1, 10, -1, ST("gilded_trim", "east"))
    f.set(1, 10, -1, ST("gilded_trim", "west"))
    f.set(0, 10, 0, F("emerald_block"))
    f.set(0, 12, -1, S_("gilded_finial"))
    for x in (-2, 2):
        f.set(x, 9, 1, S_("campfire", lit=True, facing="south"))
    for (x, y, z) in ((-4, 4, 3), (4, 5, 2), (-3, 7, 2), (3, 7, 1), (-4, 1, 1), (2, 9, -2), (-2, 9, -2)):
        ball(f, x, y, z, 1.3, B.leaves("jungle"), only_empty=True, seed=9, rough=0.5)
    for x in (-3, 3):
        for y in range(2, 7):
            f.set(x, y, 5, S_("vine", north=True), only_empty=True)
    plaque_post(f, th, 3, 6, [F("chiseled_stone_bricks")], wood="jungle", ink="black")


def g_desert_palace(f, th):
    """Upper-wall gate. A palace doorway of the oasis: a horseshoe arch in a frame of turquoise tiles, pierced screens,
    stepped merlons; on the upper court a drum carrying a turquoise onion dome with a golden finial."""
    sand, cut = F("smooth_sandstone"), F("cut_sandstone")
    tile = S_("light_blue_glazed_terracotta", facing="south")
    f.fill(-3, 0, 3, 3, 8, 3, sand)                            # the frontispiece
    m = arch(3, 4, "###", ".#.")
    carve(f, m, 3, 3)
    recess(f, m, F("cut_sandstone"))
    portal(f, th, m)
    f.set(-1, 5, 3, ST("sandstone", "west", "top"))
    f.set(1, 5, 3, ST("sandstone", "east", "top"))
    for (x, y) in ((-2, 1), (-2, 2), (-2, 3), (-2, 4), (2, 1), (2, 2), (2, 3), (2, 4), (-2, 5), (2, 5),
                   (-1, 6), (0, 7), (1, 6)):
        f.set(x, y, 3, cut)                                    # the horseshoe's voussoirs
    for x in range(-3, 4):
        f.set(x, 8, 3, tile)
    for y in range(1, 9):
        f.set(-3, y, 3, S_("cyan_glazed_terracotta", facing="east") if y % 2 else tile)
        f.set(3, y, 3, S_("cyan_glazed_terracotta", facing="west") if y % 2 else tile)
    f.set(0, 7, 3, F("gold_block"))
    for x in (-2, 2):
        f.set(x, 6, 3, S_("sandstone_wall"))
        f.set(x, 7, 3, S_("sandstone_wall"))
    for x in range(-3, 4):                                     # stepped merlons
        f.set(x, 9, 3, F("chiseled_sandstone") if x % 2 == 0 else SL("sandstone", "bottom"))
    f.fill(-3, 7, 2, 3, 8, 2, sand)
    for x in (-4, 4):                                          # brass lanterns on brackets
        f.set(x, 4, 3, F("cut_sandstone"))
        f.set(x, 4, 4, B.S("iron_bars"))
        hang_lantern(f, x, 3, 4)
    f.set(-4, 1, 4, B.potted("cactus"))
    f.set(4, 1, 4, B.potted("dead_bush"))
    # drum and onion dome on the upper court
    f.fill(-2, 7, -4, 2, 9, 0, cut)
    for (x, z) in ((-2, -2), (2, -2), (0, 0), (0, -4)):
        f.set(x, 8, z, F("light_blue_stained_glass"))
    f.fill(-2, 10, -4, 2, 10, 0, SL("cut_sandstone", "bottom"))
    copper = F("waxed_oxidized_cut_copper")
    f.fill(-1, 10, -3, 1, 10, -1, copper)
    f.fill(-2, 11, -3, 2, 12, -1, copper)
    f.fill(-1, 11, -4, 1, 12, 0, copper)
    f.fill(-1, 13, -3, 1, 13, -1, copper)
    f.set(0, 14, -2, copper)
    f.set(0, 15, -2, F("gold_block"))
    f.set(0, 16, -2, S_("gilded_finial"))
    plaque_wall(f, th, 3, 2, 4, wood="jungle", ink="black")


def g_observatory(f, th):
    """Upper-wall gate. A round-headed door in a midnight-blue tower front studded with gold stars and a constellation
    of light; on the upper court a verdigris dome with an open slit and a brass telescope, and an armillary sphere."""
    blue, lap, gold = F("blue_concrete"), F("lapis_block"), F("gilded_trim")
    f.fill(-3, 0, 3, 3, 0, 3, F("polished_blackstone_bricks"))
    f.fill(-3, 1, 3, 3, 9, 3, blue)
    m = arch(3, 4, "###", ".#.")
    carve(f, m, 3, 3)
    recess(f, m, F("polished_blackstone_bricks"))
    portal(f, th, m)
    for x in (-2, 2):                                          # gold-banded door jambs
        f.set(x, 1, 4, gold)
        f.fill(x, 2, 4, x, 4, 4, lap)
        f.set(x, 5, 4, ST("gilded_trim", "east" if x < 0 else "west", "top"))
    f.set(-1, 6, 4, ST("gilded_trim", "east", "top"))
    f.set(0, 6, 4, gold)
    f.set(1, 6, 4, ST("gilded_trim", "west", "top"))
    f.fill(-3, 9, 4, 3, 9, 4, ST("polished_blackstone_bricks", "north", "top"))
    f.fill(-3, 10, 2, 3, 10, 3, F("polished_blackstone_bricks"))
    stars = ((-3, 7), (-2, 8), (3, 6), (2, 8), (-1, 7), (1, 9), (-3, 3), (3, 3), (0, 8), (-2, 6), (2, 2), (-3, 5))
    for i, (x, y) in enumerate(stars):
        f.set(x, y, 3, F("glowstone") if i % 3 else gold)
    for (x, y) in ((-2, 7), (2, 7)):
        f.set(x, y, 4, S_("end_rod", facing="south"))
    # dome, slit and telescope on the upper court
    f.fill(-3, 7, -4, 3, 10, 1, blue)
    f.fill(-3, 10, -4, 3, 10, 1, F("polished_blackstone_bricks"))
    for dx in range(-3, 4):
        for dz in range(-4, 2):
            for dy in range(0, 5):
                d = math.sqrt((dx / 3.4) ** 2 + ((dz + 1.5) / 3.4) ** 2 + (dy / 3.8) ** 2)
                if 0.7 <= d <= 1.0:
                    f.set(dx, 11 + dy, dz, F("waxed_weathered_copper") if dx != 0 else B.pane("black"))
    f.set(0, 15, -2, F("waxed_weathered_copper"))
    f.set(0, 15, -1, F("waxed_weathered_copper"))
    for k in range(0, 4):                                      # the telescope aims at the sky over the court
        f.set(0, 12 + k, 1 - k, F("waxed_cut_copper") if k < 3 else F("waxed_exposed_cut_copper"))
    f.set(0, 16, -2, S_("lightning_rod", facing="up"))
    f.set(3, 11, 1, F("polished_blackstone_bricks"))           # armillary sphere on the corner
    ring_xy(f, 3, 13, 1, 1.5, gold, thick=0.45)
    f.set(3, 13, 1, F("blue_stained_glass"))
    f.set(3, 13, 0, gold)
    f.set(3, 13, 2, gold)
    plaque_post(f, th, -4, 4, [F("lodestone")], wood="dark_oak", ink="yellow")
    f.set(4, 1, 4, B.potted("cornflower"))


# ====================================================================================== the upper court: north row
def g_ice_castle(f, th):
    """Free gate. Twin ice towers with snow-capped spires flank a lancet of packed snow; icicles hang from the arch,
    frost crystals sprout from the merlons and soul lanterns burn blue."""
    ice, blue, snow = F("packed_ice"), F("blue_ice"), F("snow_block")
    f.fill(-4, 0, -5, 4, 0, 2, ice)
    f.fill(-1, 0, 1, 1, 0, 2, snow)
    for x0 in (-3, 2):                                         # towers 2 x 3
        f.fill(x0, 1, -3, x0 + 1, 10, 0, ice)
        for y in (3, 6, 9):
            f.fill(x0, y, 0, x0 + 1, y, 0, blue)
        f.fill(x0, 11, -3, x0 + 1, 11, 0, snow)
        f.set(x0 + (0 if x0 < 0 else 1), 12, 0, snow)
        f.set(x0 + (0 if x0 < 0 else 1), 12, -3, snow)
        # spire: tapering, snow cap, crystal tip
        f.fill(x0, 12, -2, x0 + 1, 13, -1, ice)
        f.set(x0 + (1 if x0 < 0 else 0), 14, -2, ice)
        f.set(x0 + (1 if x0 < 0 else 0), 14, -1, ice)
        f.set(x0 + (1 if x0 < 0 else 0), 15, -2, snow)
        f.set(x0 + (1 if x0 < 0 else 0), 16, -2, F("white_stained_glass"))
        f.set(x0 + (1 if x0 < 0 else 0), 17, -2, S_("end_rod", facing="up"))
        f.set(x0 + (0 if x0 < 0 else 1), 6, 1, B.pane("light_blue"))
        f.set(x0 + (0 if x0 < 0 else 1), 7, 1, B.pane("light_blue"))
    f.fill(-1, 1, -3, 1, 9, -1, blue)
    m = arch(3, 6, ".#.", ".#.")
    f.fill(-1, 7, 0, 1, 9, 0, snow)
    carve(f, m, -1, 0)
    portal(f, th, m)
    f.set(-1, 7, 0, ST("diorite", "west", "top"))
    f.set(1, 7, 0, ST("diorite", "east", "top"))
    f.fill(-1, 10, -3, 1, 10, 0, snow)
    for x in (-1, 1):                                          # icicles along the arch front
        f.set(x, 9, 1, snow)
        f.set(x, 8, 1, S_("end_rod", facing="down"))
    f.set(0, 10, 1, snow)
    f.set(0, 9, 1, S_("end_rod", facing="down"))
    f.set(0, 11, -1, F("white_stained_glass"))                 # a frost crystal on the arch
    f.set(0, 12, -1, F("light_blue_stained_glass"))
    f.set(0, 13, -1, S_("end_rod", facing="up"))
    f.set(-1, 11, -1, S_("end_rod", facing="up"))
    f.set(1, 11, -1, S_("end_rod", facing="up"))
    for x in (-4, 4):
        f.set(x, 8, 1, B.S("iron_bars"))
        hang_lantern(f, x, 7, 1, soul=True)
        f.set(x, 8, 0, ice)
        f.set(x, 1, 1, S_("snow", layers=3))
    plaque_post(f, th, 3, 2, [F("packed_ice")], wood="birch", ink="blue")


def g_arcane_tower(f, th):
    """Free gate. A tall lancet of purpur and amethyst between two obelisks, crowned by a crystal spire; floating
    rune-stones hover round the arch, soul lanterns hang on chains, violet light glows in the obelisks."""
    pur, amy = F("purpur_block"), F("amethyst_block")
    f.fill(-4, 0, -5, 4, 0, 2, F("polished_deepslate"))
    f.fill(-1, 0, 1, 1, 0, 2, F("purpur_block"))
    f.set(0, 0, 2, amy)
    f.fill(-2, 1, -3, 2, 10, -1, pur)
    for x in (-2, 2):
        f.fill(x, 1, 0, x, 9, 0, S_("purpur_pillar", axis="y"))
        f.set(x, 10, 0, amy)
    m = arch(3, 7, ".#.", ".#.")
    f.fill(-1, 8, 0, 1, 10, 0, pur)
    carve(f, m, -1, 0)
    portal(f, th, m)
    f.set(-1, 8, 0, ST("purpur_block", "west", "top"))
    f.set(1, 8, 0, ST("purpur_block", "east", "top"))
    for (x, y) in ((0, 11), (-1, 11), (1, 11), (0, 12), (0, 13)):     # the crystal spire
        f.set(x, y, -1, amy)
    f.fill(0, 11, -2, 0, 14, -2, pur)
    f.set(0, 14, -1, F("budding_amethyst"))
    f.set(0, 15, -1, S_("amethyst_cluster", facing="up"))
    f.set(-1, 12, -1, S_("large_amethyst_bud", facing="up"))
    f.set(1, 12, -1, S_("large_amethyst_bud", facing="up"))
    f.set(0, 15, -2, S_("end_rod", facing="up"))
    for x in (-4, 4):                                          # obelisks
        f.fill(x, 1, -2, x, 9, -1, S_("purpur_pillar", axis="y"))
        f.set(x, 5, 0, B.pane("purple"))
        f.set(x, 6, 0, B.pane("purple"))
        f.fill(x, 1, 0, x, 4, 0, S_("purpur_pillar", axis="y"))
        f.set(x, 7, 0, S_("purpur_pillar", axis="y"))
        f.set(x, 10, -1, amy)
        f.set(x, 11, -1, S_("amethyst_cluster", facing="up"))
        f.set(x, 4, 1, F("crying_obsidian"))
    for i in range(12):                                        # floating rune-stones round the arch
        a = math.radians(i * 30 + 15)
        x = round(math.cos(a) * 4.4)
        y = 7 + round(math.sin(a) * 5.2)
        if y < 3 or (abs(x) <= 2 and y < 12):
            continue
        f.set(x, y, 1, F("crying_obsidian") if i % 3 == 0 else amy if i % 3 == 1 else S_("purpur_pillar", axis="y"),
              only_empty=True)
    for x in (-3, 3):
        f.set(x, 11, 0, pur)
        hang_lantern(f, x, 8, 0, soul=True, chain=2)
    plaque_post(f, th, 3, 2, [S_("purpur_pillar", axis="y")], wood="dark_oak", ink="magenta")


def g_volcanic_fortress(f, th):
    """Free gate. A basalt gatehouse with horned battlements: a raised portcullis over the door, slots of molten lava
    glowing behind glass in both towers, braziers on the merlons and smoke rising from the crater behind."""
    blk, bas = F("polished_blackstone_bricks"), F("polished_basalt")
    f.fill(-4, 0, -5, 4, 0, 2, bas)
    f.fill(-1, 0, 1, 1, 0, 2, F("blackstone"))
    for x0 in (-3, 2):
        f.fill(x0, 1, -4, x0 + 1, 10, 0, blk)
        f.fill(x0, 1, 1, x0 + 1, 1, 1, ST("polished_blackstone_bricks", "north"))
        f.fill(x0, 11, -4, x0 + 1, 11, 0, F("chiseled_polished_blackstone"))
    f.fill(-1, 6, -4, 1, 10, 0, blk)
    f.fill(-1, 1, -4, 1, 5, -4, F("obsidian"))
    m = rect(3, 5)
    carve(f, m, -3, 0)
    portal(f, th, m)
    f.fill(-1, 6, 0, 1, 6, 0, B.S("iron_bars"))                # portcullis teeth under the lintel
    f.fill(-1, 7, 1, 1, 7, 1, F("gilded_blackstone"))
    f.fill(-1, 6, 1, 1, 6, 1, ST("polished_blackstone_bricks", "north", "top"))
    for x in (-3, 3):                                          # lava slots behind glass
        f.fill(x, 3, 0, x, 8, 0, S_("lava", level=0))
        f.fill(x, 3, 1, x, 8, 1, B.pane("orange"))
        f.set(x, 2, 1, blk)
        f.set(x, 9, 1, blk)
        f.set(x, 2, 0, blk)
        f.set(x, 9, 0, blk)
    for x in (-3, 3):                                          # horned merlons and braziers
        f.set(x, 12, 0, bas)
        f.set(x, 13, 0, ST("polished_blackstone", "east" if x < 0 else "west", "top"))
        f.set(x + (1 if x < 0 else -1), 14, 0, ST("polished_blackstone", "east" if x < 0 else "west"))
        f.set(x, 12, -3, bas)
        f.set(x, 13, -3, S_("campfire", lit=True))
    f.fill(-1, 11, -3, 1, 11, -1, F("magma_block"))
    f.set(0, 12, -2, S_("smoke_emitter", heavy=True))
    f.set(0, 11, 0, F("crying_obsidian"))
    f.set(-1, 11, 0, ST("polished_blackstone", "east"))
    f.set(1, 11, 0, ST("polished_blackstone", "west"))
    f.set(0, 12, 0, ST("polished_blackstone", "south"))
    plaque_post(f, th, -3, 2, [bas], wood="dark_oak", ink="orange")


def g_sky_temple(f, th):
    """Free gate, left of the axis. A white-and-gold temple front on clouds: slender columns with gilded capitals
    carry a round arch; a golden sun disc with rays and great white wings spread above; cloud puffs drift over it."""
    q, qp, gold = F("smooth_quartz"), S_("quartz_pillar", axis="y"), F("gilded_trim")
    f.fill(-4, 0, -5, 4, 0, 2, F("quartz_bricks"))
    f.fill(-1, 0, 1, 1, 0, 2, F("gold_block"))
    for x in (-4, -3, 3, 4):
        z = 0 if abs(x) == 3 else -1
        f.fill(x, 1, z, x, 8, z, qp)
        f.set(x, 9, z, gold)
        f.set(x, 0, z, F("chiseled_quartz_block"))
    f.fill(-2, 1, -3, 2, 9, -2, q)
    m = arch(5, 6, ".###.")
    f.fill(-2, 7, -1, 2, 9, 0, q)
    carve(f, m, -1, 0)
    portal(f, th, m)
    f.set(-2, 7, 0, ST("quartz", "west", "top"))
    f.set(2, 7, 0, ST("quartz", "east", "top"))
    f.fill(-4, 10, -2, 4, 10, 0, F("quartz_bricks"))
    f.fill(-4, 10, 1, 4, 10, 1, ST("gilded_trim", "north", "top"))
    disc_xy(f, 0, 13, -1, 2.1, F("gold_block"))                # the sun disc with rays
    f.set(0, 13, 0, F("glowstone"))
    for (dx, dy) in ((0, 3), (-2, 2), (2, 2)):
        f.set(dx, 13 + dy, -1, S_("end_rod", facing="up"))
    for side in (-1, 1):                                       # wings
        for k, (dx, dy) in enumerate(((3, 12), (4, 12), (5, 13), (4, 13), (5, 14), (6, 14), (6, 15), (5, 15),
                                      (3, 11), (4, 11), (5, 12))):
            f.set(side * dx, dy, -1, F("white_wool") if k % 3 else F("white_concrete"))
        f.set(side * 6, 16, -1, ST("quartz", "west" if side > 0 else "east"))
    for (x, y, z, r) in ((-5, 1, -1, 1.3), (5, 1, -2, 1.4), (-5, 2, -3, 1.0), (4, 2, 1, 0.9), (-3, 17, -2, 1.3),
                         (2, 18, -1, 1.5), (4, 17, -3, 1.1)):
        ball(f, x, y, z, r, F("white_wool"), ry=r * 0.7, only_empty=True)
    hang_lantern(f, 0, 8, 1)
    f.set(0, 9, 1, gold)
    plaque_post(f, th, -3, 2, [F("chiseled_quartz_block")], wood="birch", ink="black")


def g_marble_palace(f, th):
    """Free gate, right of the axis. A triumphal arch of white marble: engaged columns with gilded capitals, a
    coffered round arch, an entablature with a gilded frieze, an attic with medallions and a crowned statue between
    golden urns."""
    q, qp, qb, gold = F("smooth_quartz"), S_("quartz_pillar", axis="y"), F("quartz_bricks"), F("gilded_trim")
    f.fill(-4, 0, -5, 4, 0, 2, F("polished_diorite"))
    f.fill(-2, 0, 1, 2, 0, 2, F("quartz_bricks"))
    f.fill(-4, 1, -3, 4, 8, -1, qb)
    f.fill(-4, 1, 0, -3, 8, 0, qb)
    f.fill(3, 1, 0, 4, 8, 0, qb)
    f.fill(-2, 7, 0, 2, 8, 0, qb)
    m = arch(5, 5, "#####", ".###.")
    carve(f, m, -2, 0)
    portal(f, th, m)
    f.set(-2, 6, 0, ST("quartz", "west", "top"))
    f.set(2, 6, 0, ST("quartz", "east", "top"))
    f.set(-1, 7, 0, ST("quartz", "west", "top"))
    f.set(1, 7, 0, ST("quartz", "east", "top"))
    f.set(0, 8, 0, F("chiseled_quartz_block"))
    for x in (-4, 4):                                          # engaged columns
        f.fill(x, 1, 1, x, 7, 1, qp)
        f.set(x, 0, 1, F("chiseled_quartz_block"))
        f.set(x, 8, 1, gold)
    for x in (-3, 3):
        f.set(x, 1, 1, ST("quartz", "north"))
        f.set(x, 5, 1, F("chiseled_quartz_block"))            # medallions
    f.fill(-5, 9, -3, 5, 9, 1, q)                               # entablature with gilded frieze
    f.fill(-5, 10, -3, 5, 10, 1, gold)
    f.fill(-5, 11, -3, 5, 11, 1, q)
    f.fill(-5, 11, 2, 5, 11, 2, ST("quartz", "north", "top"))
    f.fill(-4, 12, -2, 4, 13, 0, qb)                           # attic
    for x in (-3, 0, 3):
        f.set(x, 12, 1, gold if x == 0 else F("chiseled_quartz_block"))
    f.fill(-4, 14, -2, 4, 14, 0, SL("quartz", "bottom"))
    S.statue_figure(f, 0, 14, -1, facing="south", material="quartz_block", wings=False, gold_crown=True,
                    plinth="smooth_quartz")
    for x in (-4, 4):                                          # golden urns
        f.set(x, 15, -1, F("gold_block"))
        f.set(x, 16, -1, S_("gilded_finial"))
    for x in (-5, 5):
        hang_lantern(f, x, 8, 1, chain=1)
    plaque_post(f, th, 3, 2, [F("chiseled_quartz_block")], wood="birch", ink="black")


def g_gothic_cathedral(f, th):
    """Free gate. A cathedral portal at night: a lancet doorway in receding archivolts, buttresses with pinnacles and
    gargoyles, a rose window of deep blue and violet glass, a steep gable with a gilded finial, candles in niches."""
    stone, dark = F("deepslate_bricks"), F("polished_deepslate")
    f.fill(-4, 0, -5, 4, 0, 2, F("deepslate_tiles"))
    f.fill(-3, 1, -3, 3, 12, -1, stone)
    for x in (-3, 3):                                          # buttresses
        f.fill(x, 1, 0, x, 10, 1, dark)
        f.set(x, 11, 0, SL("deepslate_bricks"))
        f.set(x, 11, 1, SL("deepslate_bricks"))
        S.pinnacle(f, x, 12, -1, 5, base=F("deepslate_bricks"), shaft=WL("deepslate_bricks"))
        f.set(x, 9, 2, ST("polished_blackstone", "north", "top"))   # gargoyles
    for x in (-2, 2):
        f.fill(x, 1, 0, x, 8, 0, stone)
    m = arch(3, 6, "###", ".#.", ".#.")
    f.fill(-1, 7, 0, 1, 9, 0, stone)
    carve(f, m, -1, 0)
    portal(f, th, m)
    for (x, y) in ((-1, 7), (1, 7)):
        f.set(x, y, 0, ST("deepslate_bricks", "west" if x < 0 else "east", "top"))
    for (x, y) in ((-2, 8), (2, 8), (-1, 9), (1, 9), (0, 10)):   # outer archivolt
        f.set(x, y, 1, dark)
    f.set(-2, 7, 1, ST("polished_deepslate", "west", "top"))
    f.set(2, 7, 1, ST("polished_deepslate", "east", "top"))
    for y in range(1, 7):
        f.set(-2, y, 1, dark if y % 2 else WL("polished_blackstone"))
        f.set(2, y, 1, dark if y % 2 else WL("polished_blackstone"))
    S.rose_window(f, "x", 0, 13, -1, 2.4, F("polished_blackstone"), colors=("blue", "purple", "magenta",
                                                                           "light_blue", "cyan"), spokes=8)
    f.fill(-2, 11, 0, 2, 11, 0, ST("deepslate_bricks", "north", "top"))
    for k in range(0, 5):                                      # the steep gable: two rises per step
        y = 13 + 2 * k
        hw = 3 - k
        if hw < 0:
            break
        f.fill(-hw, y, -1, hw, y + 1, -1, F("deepslate_tiles") if hw else F("deepslate_tiles"))
        f.fill(-hw, y, -2, hw, y + 1, -2, F("deepslate_tiles"))
        if hw:
            f.set(-hw, y + 1, -1, SL("deepslate_tiles"))
            f.set(hw, y + 1, -1, SL("deepslate_tiles"))
    S.rose_window(f, "x", 0, 13, -1, 2.4, F("polished_blackstone"), colors=("blue", "purple", "magenta",
                                                                           "light_blue", "cyan"), spokes=8)
    f.set(0, 21, -1, S_("gilded_finial"))
    for x in (-4, 4):                                          # candle niches and lanterns
        f.set(x, 1, 1, F("polished_blackstone"))
        f.set(x, 2, 1, B.candle(3, "white"))
        hang_lantern(f, x, 7, 1, soul=True, chain=1)
        f.set(x, 9, 1, dark)
        f.set(x, 9, 0, dark)
    plaque_post(f, th, 3, 3, [F("polished_blackstone")], wood="dark_oak", ink="light_blue")


def g_dragon_lair(f, th):
    """Free gate. The skull of a great dragon: the portal glows in its open jaws, its eyes burn, smoke curls from the
    nostrils, horns sweep back; gold and treasure spill over scorched stone."""
    bone, bx, bz = S_("bone_block", axis="y"), S_("bone_block", axis="x"), S_("bone_block", axis="z")
    f.fill(-4, 0, -5, 4, 0, 3, F("blackstone"))
    for z in range(-5, 2):                                     # cranium
        hw = 4 if z < -1 else 3
        f.fill(-hw, 6, z, hw, 9 if z < 0 else 8, z, bz if z % 2 else bone)
    f.fill(-3, 10, -4, 3, 10, -1, bone)
    f.fill(-2, 11, -4, 2, 11, -2, bone)
    for x in (-4, 4):                                          # cheek bones down to the jaw hinge
        f.fill(x, 1, -4, x, 5, -1, bone)
    f.fill(-4, 1, -5, 4, 5, -5, bone)
    f.fill(-3, 0, 1, 3, 0, 3, bx)                               # the lower jaw on the ground
    f.fill(-3, 1, 1, -3, 1, 3, bone)
    f.fill(3, 1, 1, 3, 1, 3, bone)
    for x in (-3, -1, 1, 3):                                   # fangs
        f.set(x, 5, 1, S_("end_rod", facing="down") if abs(x) == 1 else ST("diorite", "south", "top"))
    for x in (-3, 3):
        f.set(x, 2, 2, S_("end_rod", facing="up"))
    f.fill(-3, 6, 2, 3, 6, 2, ST("diorite", "north", "top"))
    f.fill(-2, 7, 2, 2, 7, 2, bone)
    m = rect(5, 5)
    carve(f, m, -4, 1)
    portal(f, th, m)
    for x in (-2, 2):                                          # eyes
        f.set(x, 8, 2, F("shroomlight"))
        f.set(x, 9, 2, ST("polished_blackstone", "north", "top"))
    f.set(-1, 8, 2, F("red_stained_glass"))
    f.set(1, 8, 2, F("red_stained_glass"))
    f.set(-1, 7, 3, bone)                                      # snout and smoking nostrils
    f.set(1, 7, 3, bone)
    f.set(0, 7, 3, bone)
    f.set(0, 8, 3, bone)
    f.set(-1, 8, 3, S_("smoke_emitter", heavy=False))
    f.set(1, 8, 3, S_("smoke_emitter", heavy=False))
    for side in (-1, 1):                                       # horns sweeping back
        for k, (dx, dy, dz) in enumerate(((3, 11, -3), (4, 12, -4), (4, 13, -5), (4, 14, -5), (4, 15, -5))):
            f.set(side * dx, dy, dz, bone)
        f.set(side * 4, 16, -5, S_("end_rod", facing="up"))
        f.set(side * 2, 12, -3, bone)
        f.set(side * 2, 13, -3, S_("pointed_dripstone", thickness="tip", vertical_direction="up"))
    for (x, z, st) in ((-4, 2, F("gold_block")), (4, 1, F("raw_gold_block")), (-4, 3, F("raw_gold_block")),
                       (4, 3, F("gold_block")), (4, 2, F("gold_block"))):
        f.set(x, 1, z, st)
    for (x, z) in ((-4, 1), (-2, 3), (2, 3), (0, 3), (-3, 4), (3, 4)):
        f.set(x, 1 if f.is_empty(x, 1, z) else 2, z, S_("light_weighted_pressure_plate", power=0))
    f.set(-4, 2, 2, S_("chest", facing="south"))
    f.set(4, 2, 2, S_("light_weighted_pressure_plate", power=0))
    plaque_post(f, th, 4, 4, [F("polished_blackstone_bricks")], wood="dark_oak", ink="orange")


def g_coral_hall(f, th):
    """Free gate. A scallop-shell arch of sea-green prismarine studded with coral; a glowing pearl sits in the shell,
    jets of water arc from its rim into coral pools on either side."""
    pb, dp, pr = F("prismarine_bricks"), F("dark_prismarine"), F("prismarine")
    f.fill(-5, 0, -5, 5, 0, 2, dp)
    f.fill(-3, 1, -3, 3, 7, -1, pb)
    for x0 in (-3, 2):
        f.fill(x0, 1, 0, x0 + 1, 7, 0, pb)
        f.fill(x0, 1, 1, x0 + 1, 1, 1, ST("prismarine_bricks", "north"))
    m = arch(3, 5, "###", ".#.")
    carve(f, m, -1, 0)
    portal(f, th, m)
    f.set(-1, 6, 0, ST("dark_prismarine", "west", "top"))
    f.set(1, 6, 0, ST("dark_prismarine", "east", "top"))
    for i, ang in enumerate(range(0, 181, 20)):                # the shell: ribs fanning out above the door
        a = math.radians(ang)
        for r in range(2, 6):
            x = round(math.cos(a) * r * 1.0)
            y = 8 + round(math.sin(a) * r * 0.95)
            f.set(x, y, -1, dp if i % 2 else pr)
            f.set(x, y, -2, pr)
    f.fill(-5, 8, -1, 5, 8, -1, dp)
    f.set(0, 9, 0, F("pearlescent_froglight"))
    f.set(0, 10, 0, F("pearlescent_froglight"))
    f.set(-1, 9, 0, F("sea_lantern"))
    f.set(1, 9, 0, F("sea_lantern"))
    f.fill(-2, 8, 0, 2, 8, 0, ST("prismarine_bricks", "north", "top"))
    corals = ["tube", "brain", "bubble", "fire", "horn"]       # coral pools: the corals touch the water
    for x0 in (-5, 4):
        f.fill(x0, 0, -2, x0 + 1, 0, 1, S_("water", level=0))
        f.fill(x0, -1, -2, x0 + 1, -1, 1, pb)
        for (x, z) in ((x0, -2), (x0 + 1, 1)):
            f.set(x, 1, z, F(corals[(x * 3 + z) % 5] + "_coral_block"))
        f.set(x0 + (1 if x0 < 0 else 0), 1, -1, F(corals[(x0 + 2) % 5] + "_coral_block"))
        f.fill(x0, 0, 2, x0 + 1, 0, 2, pb)
        f.fill(x0, 1, 2, x0 + 1, 1, 2, SL("prismarine_bricks"))
    for (x, y, z, c) in ((-3, 8, 0, 0), (3, 8, 0, 3), (-4, 9, -1, 2), (4, 9, -1, 1), (-2, 12, -1, 4), (2, 12, -1, 0)):
        f.set(x, y, z, F(corals[c] + "_coral_block"))
    f.set(-3, 9, 1, S_("fountain_spray", power=2, facing="west"))
    f.set(3, 9, 1, S_("fountain_spray", power=2, facing="east"))
    f.set(-3, 8, 1, F("prismarine"))
    f.set(3, 8, 1, F("prismarine"))
    plaque_post(f, th, 2, 2, [F("prismarine_bricks")], wood="cherry", ink="black")


GATES = {
    "northern_longhouse": g_northern_longhouse, "mushroom_grove": g_mushroom_grove, "ancient_ruins": g_ancient_ruins,
    "pirate_ship": g_pirate_ship, "flower_glasshouse": g_flower_glasshouse, "dwarven_forge": g_dwarven_forge,
    "desert_palace": g_desert_palace, "observatory": g_observatory, "japanese_mansion": g_japanese_mansion,
    "great_tree": g_great_tree, "sage_library": g_sage_library, "jungle_temple": g_jungle_temple,
    "ice_castle": g_ice_castle, "volcanic_fortress": g_volcanic_fortress, "gothic_cathedral": g_gothic_cathedral,
    "coral_hall": g_coral_hall, "arcane_tower": g_arcane_tower, "sky_temple": g_sky_temple,
    "marble_palace": g_marble_palace, "dragon_lair": g_dragon_lair,
}
# wall gates: set into a six-block retaining wall (face at local z = +2)
WALL = ("northern_longhouse", "mushroom_grove", "dwarven_forge", "ancient_ruins", "pirate_ship", "great_tree",
        "sage_library", "japanese_mansion", "jungle_temple", "desert_palace", "observatory")


def gate(w, theme, x, y, z, facing):
    """Build the gate of ``theme`` with its portal plane centred on (x, z), floor top at y, front towards
    ``facing``; register the spot ``guildgate.<theme>``. Returns the frame."""
    f = Frame(w, origin=(x, y, z), rot=ROT[facing])
    assert f.dir("south") == facing, (facing, f.dir("south"))
    GATES[theme](f, theme)
    for dy in (1, 2):
        got = f.get(0, dy, 0)
        assert got.startswith("eternalrealm:guild_portal") and f"theme={theme}" in got, (theme, dy, got)
    w.spot(f"guildgate.{theme}", (x, y + 1, z), YAW[OPPOSITE[facing]])
    return f
