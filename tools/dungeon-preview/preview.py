"""Previews of decorated dungeon interiors (tools/dungeon-preview).

Builds the pure modules offline (tools/offline-test.sh must have run once), dumps a decorated layout with
DumpDecor.java and renders it with the capital's voxel ray-caster (tools/capital/preview.py): top-down cut-aways and
eye-level views of the entrance hall, a combat room, a corridor, the antechamber, a set-piece room and the arena.
Colours are approximations from tools/capital/colors.py; no game textures are used.

    python3 tools/dungeon-preview/preview.py d1 [seed] [--out DIR] [--views hall,room,...] [--scale 0.6]
"""
from __future__ import annotations

import argparse
import math
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools"))

from capital import colors as COLORS  # noqa: E402
from capital import preview as P  # noqa: E402

# colours of blocks the capital never used (approximate average texture colours)
COLORS.BASE.update({
    "brown_mushroom": (153, 116, 92), "red_mushroom": (200, 50, 40), "brown_mushroom_block": (149, 111, 81),
    "red_mushroom_block": (200, 46, 45), "mushroom_stem": (203, 196, 185), "cave_vines": (96, 120, 40),
    "cave_vines_plant": (96, 120, 40), "chiseled_red_sandstone": (183, 96, 27), "cut_red_sandstone": (189, 101, 31),
    "smooth_red_sandstone": (181, 98, 31), "red_sandstone": (186, 99, 29), "honeycomb_block": (229, 148, 29),
    "light_weighted_pressure_plate": (246, 208, 61), "wall_torch": (255, 214, 120), "torch": (255, 214, 120),
    "soul_wall_torch": (120, 220, 230), "soul_torch": (120, 220, 230), "magma_block": (142, 63, 31),
    "crying_obsidian": (32, 10, 60), "amethyst_cluster": (163, 126, 207), "large_amethyst_bud": (163, 126, 207),
    "budding_amethyst": (132, 96, 186), "end_rod": (240, 236, 230), "verdant_froglight": (229, 244, 228),
    "spore_blossom": (206, 96, 164), "hanging_roots": (161, 115, 91), "glow_lichen": (112, 131, 121),
    "bone_block": (229, 225, 207), "packed_mud": (142, 106, 79), "lightning_rod": (198, 110, 80),
    "dripstone_block": (134, 107, 92), "pointed_dripstone": (134, 107, 92), "tinted_glass": (44, 38, 46),
    "sculk": (12, 29, 36), "soul_soil": (75, 57, 46), "soul_sand": (81, 62, 50), "powder_snow": (248, 253, 253),
    "lodestone": (147, 149, 152), "chiseled_tuff": (100, 100, 95), "reinforced_deepslate": (80, 82, 78),
    "cracked_deepslate_bricks": (64, 64, 65), "cracked_polished_blackstone_bricks": (44, 37, 43),
    "dropper": (110, 110, 110), "observer": (98, 98, 98), "end_portal_frame": (91, 120, 97),
    "end_stone": (219, 222, 158), "lava_cauldron": (200, 90, 30), "oxidized_cut_copper": (79, 153, 126),
    "powder_snow_cauldron": (230, 240, 245), "purpur_pillar": (171, 129, 171), "tripwire_hook": (140, 120, 100),
    "wither_skeleton_skull": (40, 40, 40),
})
EMIT = {"cave_vines": 14, "cave_vines_plant": 14, "verdant_froglight": 15, "end_rod": 14, "crying_obsidian": 10,
        "magma_block": 3, "amethyst_cluster": 5, "wall_torch": 14, "soul_wall_torch": 10, "glow_lichen": 7,
        "lava": 15, "soul_campfire": 10, "light": 15, "lava_cauldron": 15}
_material = COLORS.material


def material(state):
    m = dict(_material(state))
    name = state.split("[")[0].split(":")[-1]
    if name == "light":
        m["kind"] = "air"
    if name in EMIT and m.get("emit", 0) == 0 and ("berries=false" not in state):
        m["emit"] = EMIT[name]
    return m


P.material = material

SIZE, MIN_Y, MAX_Y = 288, -3, 35
NY = MAX_Y - MIN_Y + 1

# DungeonPalette (forge) per theme: floor, floor_alt, wall, wall_alt, pillar, light, accent, door
PALETTE = {
    "underground_city": ("stone_bricks", "cracked_stone_bricks", "deepslate_bricks", "mossy_stone_bricks",
                         "polished_deepslate", "glowstone", "chiseled_stone_bricks", "iron_bars"),
    "forest": ("moss_block", "rooted_dirt", "dark_oak_log", "mossy_cobblestone", "oak_log", "shroomlight",
               "azalea_leaves", "dark_oak_fence"),
    "desert_temple": ("smooth_sandstone", "cut_sandstone", "cut_sandstone", "chiseled_sandstone",
                      "chiseled_sandstone", "ochre_froglight", "blue_terracotta", "iron_bars"),
    "ice_prison": ("snow_block", "packed_ice", "packed_ice", "blue_ice", "blue_ice", "sea_lantern", "iron_block",
                   "iron_bars"),
    "ice_castle": ("polished_diorite", "calcite", "quartz_bricks", "packed_ice", "quartz_pillar", "sea_lantern",
                   "blue_ice", "iron_bars"),
    "volcano": ("polished_blackstone", "basalt", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
                "basalt", "shroomlight", "gilded_blackstone", "chain"),
    "sky_ruins": ("smooth_quartz", "calcite", "quartz_bricks", "chiseled_quartz_block", "quartz_pillar",
                  "pearlescent_froglight", "gold_block", "iron_bars"),
    "dark_tower": ("deepslate_tiles", "cracked_deepslate_tiles", "polished_blackstone_bricks", "crying_obsidian",
                   "obsidian", "crying_obsidian", "amethyst_block", "chain"),
    "end_throne": ("end_stone_bricks", "purpur_block", "purpur_block", "end_stone_bricks", "purpur_pillar",
                   "pearlescent_froglight", "obsidian", "chain"),
}
ROLE = {"floor": 0, "wall": 2, "pillar": 4, "light": 5, "accent": 6, "door": 7}


def role_state(theme, role, x, y, z):
    pal = PALETTE[theme]
    wy = y + 64
    alt = ((x * 31 + wy * 17 + z * 13 + (x ^ z) * 7) % 7) == 0
    if role in ("floor", "wall"):
        name = pal[ROLE[role] + (1 if alt else 0)]
    else:
        name = pal[ROLE[role]]
    if name.endswith("_log") or name in ("basalt", "quartz_pillar", "purpur_pillar"):
        name += "[axis=y]"
    return "minecraft:" + name


class DungeonWorld:
    """What capital.preview.Scene needs: a palette and a[x, z, y] of palette indices (0 void, 1 air)."""

    def __init__(self, path):
        self.palette = ["eternalrealm:void", "minecraft:air"]
        self.index = {s: i for i, s in enumerate(self.palette)}
        self.a = np.zeros((SIZE, SIZE, NY), np.uint16)
        self.spaces = {"R": [], "K": [], "S": []}
        raw = {}
        with open(path) as f:
            for line in f:
                parts = line.split()
                tag = parts[0]
                if tag == "T":
                    self.theme = parts[1]
                elif tag in ("H", "C", "A", "N"):
                    self.spaces[tag] = tuple(map(int, parts[1:7]))
                elif tag in ("R", "K", "S"):
                    self.spaces[tag].append(tuple(map(int, parts[1:7])))
                elif tag == "V":
                    self.revival = (int(parts[1]), int(parts[2]), int(parts[3]), float(parts[4]))
                elif tag == "E":
                    self.entry = tuple(map(int, parts[1:4]))
                elif tag == "P":
                    raw[int(parts[1])] = parts[2]
                else:
                    x, y, z, k = map(int, parts)
                    state = raw[k]
                    if state.startswith("#"):
                        state = role_state(self.theme, state[1:], x, y, z)
                    self.a[x, z, y - MIN_Y] = self.idx(state)

    def idx(self, state):
        i = self.index.get(state)
        if i is None:
            i = len(self.palette)
            self.palette.append(state)
            self.index[state] = i
        return i


def setup_globals():
    P.NX, P.NY, P.NZ = SIZE, NY, SIZE
    P.X0, P.Y0, P.Z0 = 0, MIN_Y, 0


def center(box):
    return ((box[0] + box[3] + 1) / 2.0, (box[1] + box[4] + 1) / 2.0, (box[2] + box[5] + 1) / 2.0)


def topdown(scene, name, box, out, scale, cut=5.5):
    """Straight down onto the floor, everything above {cut} blocks over the floor removed."""
    cx, _, cz = center(box)
    span = max(box[3] - box[0], box[5] - box[2]) + 2
    height = span * 1.2
    pos = (cx, box[1] + height, cz + 0.01)
    look = (cx, box[1], cz)
    fov = 2 * math.degrees(math.atan((span / 2.0) / (height - 0.5)))
    clip = [(1, box[1] + cut, True)]
    return render(scene, name, pos, look, out, scale, fov=fov, clip=clip, size=(900, 900))


def iso(scene, name, box, out, scale, corner=0, top=None):
    """A dollhouse cut-away: the two walls nearest the camera and everything above {top} removed."""
    x0, y0, z0, x1, y1, z1 = box
    top = y1 - 0.5 if top is None else top
    span = max(x1 - x0, z1 - z0)
    sx = -1 if corner in (0, 3) else 1
    sz = -1 if corner in (0, 1) else 1
    cx, _, cz = center(box)
    pos = (cx + sx * span * 0.62, y0 + span * 0.78 + 4, cz + sz * span * 0.62)
    look = (cx, y0 + 1.5, cz)
    clip = [(1, top, True)]
    clip.append((0, x0 + 0.5, False) if sx < 0 else (0, x1 - 0.5, True))
    clip.append((2, z0 + 0.5, False) if sz < 0 else (2, z1 - 0.5, True))
    return render(scene, name, pos, look, out, scale, fov=62.0, clip=clip, size=(1280, 900))


def render(scene, name, pos, look, out, scale, fov=70.0, clip=None, size=(1280, 720)):
    P.OUT_DIR = out
    return P.render(scene, name, pos, look, size=size, fov=fov, clip=clip, scale=scale, ambient_boost=1.6,
                    fog=400.0, exposure=1.15, label=name)


def front_of(core, corridor):
    if corridor[2] > core[5]:
        return (0, 1)
    if corridor[5] < core[2]:
        return (0, -1)
    if corridor[0] > core[3]:
        return (1, 0)
    return (-1, 0)


def views(w):
    """Named cameras: (kind, args)."""
    v = {}
    hall = w.spaces["H"]
    core = w.spaces["C"]
    fx, fz = front_of(core, w.spaces["K"][0])
    hc = center(hall)
    half_depth = (abs(fx) * (hall[3] - hall[0]) + abs(fz) * (hall[5] - hall[2])) / 2.0
    back = (hc[0] - fx * (half_depth - 2.5), hc[2] - fz * (half_depth - 2.5))
    v["hall_iso"] = ("iso", hall, 0)
    v["hall_eye"] = ("eye", (back[0] + fz * 2.0, 3.1, back[1] + fx * 2.0), (hc[0] + fx * half_depth, 2.4,
                                                                         hc[2] + fz * half_depth))
    front = (hc[0] + fx * (half_depth - 2.5), hc[2] + fz * (half_depth - 2.5))
    v["hall_back"] = ("eye", (front[0] - fz * 2.5, 3.1, front[1] - fx * 2.5), (hc[0] - fx * half_depth, 3.0,
                                                                          hc[2] - fz * half_depth))
    rooms = w.spaces["R"]
    if rooms:
        room = max(rooms, key=lambda b: (b[3] - b[0]) * (b[5] - b[2]))
        v["room_iso"] = ("iso", room, 1)
        v["room_top"] = ("top", room)
        rc = center(room)
        if room[3] - room[0] >= room[5] - room[2]:
            v["room_eye"] = ("eye", (room[0] + 2.6, 3.0, rc[2] + 0.5), (room[3], 2.2, rc[2] + 0.5))
        else:
            v["room_eye"] = ("eye", (rc[0] + 0.5, 3.0, room[2] + 2.6), (rc[0] + 0.5, 2.2, room[5]))
        small = min(rooms, key=lambda b: (b[3] - b[0]) * (b[5] - b[2]))
        v["room2_iso"] = ("iso", small, 2)
    corridors = w.spaces["K"]
    if len(corridors) > 1:
        corr = max(corridors[:-1], key=lambda b: max(b[3] - b[0], b[5] - b[2]))
        cx, _, cz = center(corr)
        if corr[3] - corr[0] > corr[5] - corr[2]:
            v["corridor_eye"] = ("eye", (corr[0] + 0.5, 2.6, cz), (corr[3], 2.3, cz))
        else:
            v["corridor_eye"] = ("eye", (cx, 2.6, corr[2] + 0.5), (cx, 2.3, corr[5]))
    if "N" in w.spaces:
        ante = w.spaces["N"]
        arena = w.spaces["A"]
        ac = center(arena)
        n = center(ante)
        if ante[3] - ante[0] >= ante[5] - ante[2] and abs(ac[2] - n[2]) < abs(ac[0] - n[0]):
            sx = ante[0] + 2.5 if ac[0] > n[0] else ante[3] - 1.5
            v["ante_eye"] = ("eye", (sx, 3.2, n[2] + 0.5), (ac[0], 6.5, n[2] + 0.5))
        else:
            sz = ante[2] + 2.5 if ac[2] > n[2] else ante[5] - 1.5
            v["ante_eye"] = ("eye", (n[0] + 0.5, 3.2, sz), (n[0] + 0.5, 6.5, ac[2]))
        v["ante_iso"] = ("iso", ante, 3)
    arena = w.spaces["A"]
    a = center(arena)
    v["arena_eye"] = ("eye", (arena[0] + 3.5, 6.0, arena[2] + 3.5), (a[0] + 6, 3.0, a[2] + 6))
    v["arena_iso"] = ("iso", arena, 0)
    for i, piece in enumerate(w.spaces["S"]):
        v[f"piece{i}_iso"] = ("iso", piece, i)
    return v


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dungeon")
    ap.add_argument("seed", nargs="?", default="1")
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "dungeon-previews"))
    ap.add_argument("--views", default="")
    ap.add_argument("--scale", type=float, default=0.6)
    args = ap.parse_args()
    classes = os.path.join(ROOT, "build", "dungeon-preview-classes")
    os.makedirs(classes, exist_ok=True)
    main_classes = os.path.join(ROOT, ".offline-build", "main")
    subprocess.check_call(["javac", "--release", "17", "-encoding", "UTF-8", "-cp", main_classes, "-d", classes,
                           os.path.join(HERE, "DumpDecor.java")])
    dump = os.path.join(classes, f"{args.dungeon}-{args.seed}.txt")
    subprocess.check_call(["java", "-cp", main_classes + os.pathsep + classes, "DumpDecor", args.dungeon, args.seed,
                           dump])
    setup_globals()
    w = DungeonWorld(dump)
    scene = P.Scene(w)
    out = os.path.join(args.out, args.dungeon)
    wanted = [s for s in args.views.split(",") if s]
    for name, view in views(w).items():
        if wanted and name not in wanted:
            continue
        if view[0] == "top":
            topdown(scene, f"{args.dungeon}_{name}", view[1], out, args.scale)
        elif view[0] == "iso":
            iso(scene, f"{args.dungeon}_{name}", view[1], out, args.scale, corner=view[2])
        else:
            render(scene, f"{args.dungeon}_{name}", view[1], view[2], out, args.scale)


if __name__ == "__main__":
    main()
