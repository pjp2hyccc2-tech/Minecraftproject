"""A field set piece: a voxel box anchored at absolute world coordinates over the real terrain.

``Piece`` has the write API of the capital ``World`` (``set``, ``fill``, ``set_many``, ``be``, ``fluid_tick``...), so the
capital helpers (``shapes``, ``deco``, ``Frame``) draw into it directly, with absolute world coordinates.

Palette index 0 (``eternalrealm:void``) keeps whatever the terrain generator put there, 1 is air (carves the
terrain), every other index is a block state. The terrain under and around the piece comes from the pure Java
terrain (``probe``): ``top(x, z)`` is the top solid block, ``water(x, z)`` the top water block (or None).
"""
from __future__ import annotations

import contextlib
import math

import numpy as np

from capital import blocks as B
from capital.world import Frame

from . import blocks_ext  # noqa: F401  (registers the extra blocks)
from . import probe

VOID, AIR = 0, 1
AIR_S = "minecraft:air"
NO_WATER = probe.NO_WATER
MARGIN = 3          # terrain known around the piece box (slopes, connections, previews)


class Piece:
    def __init__(self, pid, name, field, x0, z0, x1, z1, below=12, above=40, y0=None, y1=None, kind="scenery",
                 grid=None):
        x0, x1 = sorted((int(x0), int(x1)))
        z0, z1 = sorted((int(z0), int(z1)))
        self.id, self.name, self.field, self.kind = pid, name, int(field), kind
        self.X0, self.Z0, self.X1, self.Z1 = x0, z0, x1, z1
        g = grid or probe.grid(x0 - MARGIN, z0 - MARGIN, x1 + MARGIN, z1 + MARGIN, 1)
        self.g = g
        self.gx0, self.gz0 = g.x0, g.z0
        tops = g.top[MARGIN:MARGIN + x1 - x0 + 1, MARGIN:MARGIN + z1 - z0 + 1]
        ylo = int(tops.min()) - below if y0 is None else int(y0)
        yhi = int(tops.max()) + above if y1 is None else int(y1)
        self.Y0, self.Y1 = max(-60, ylo), min(318, yhi)
        self.NX, self.NZ, self.NY = x1 - x0 + 1, z1 - z0 + 1, self.Y1 - self.Y0 + 1
        self.a = np.zeros((self.NX, self.NZ, self.NY), np.uint16)
        self.palette = ["eternalrealm:void", AIR_S]
        self._index = {s: i for i, s in enumerate(self.palette)}
        self.block_entities: dict[tuple, str] = {}
        self.fluid_ticks: set[tuple] = set()
        self.probes: list[tuple] = []
        self.spots: dict[str, dict] = {}
        self.entities: list = []
        self.clipped_writes = 0
        self.notes: list[str] = []
        self.waystone = None         # (x, y, z, facing) of the waystone's lower half
        self.keep_clear: list = []   # (x0, z0, x1, z1) rectangles that must stay walkable (checked)
        self.views: list[dict] = []  # preview cameras (render.py)

    # ------------------------------------------------------------------ terrain
    def _gi(self, x, z):
        return int(x) - self.gx0, int(z) - self.gz0

    def top(self, x, z) -> int:
        i, j = self._gi(x, z)
        return int(self.g.top[i, j])

    def tops(self, xs, zs):
        return self.g.top[np.asarray(xs) - self.gx0, np.asarray(zs) - self.gz0]

    def water(self, x, z):
        i, j = self._gi(x, z)
        w = int(self.g.water[i, j])
        return None if w == NO_WATER else w

    def surface(self, x, z) -> str:
        i, j = self._gi(x, z)
        return probe.layout()["surfaces"][int(self.g.surface[i, j])]

    def field_at(self, x, z) -> int:
        i, j = self._gi(x, z)
        return int(self.g.field[i, j])

    def biome_at(self, x, z) -> str:
        i, j = self._gi(x, z)
        return probe.layout()["biomes"][int(self.g.biome[i, j])]

    def wet(self, x, z) -> bool:
        return self.water(x, z) is not None

    def ground(self, x, z) -> int:
        """Top of what one stands on: the terrain top, or the water top where there is water."""
        w = self.water(x, z)
        return w if w is not None else self.top(x, z)

    def median_top(self, x0, z0, x1, z1) -> int:
        xs, zs = np.meshgrid(np.arange(min(x0, x1), max(x0, x1) + 1), np.arange(min(z0, z1), max(z0, z1) + 1),
                             indexing="ij")
        return int(np.median(self.tops(xs, zs)))

    # ------------------------------------------------------------------ palette
    def idx(self, state: str) -> int:
        i = self._index.get(state)
        if i is not None:
            return i
        c = B.canon(state)
        i = self._index.get(c)
        if i is None:
            i = len(self.palette)
            self.palette.append(c)
            self._index[c] = i
        self._index[state] = i
        return i

    def state(self, i: int) -> str:
        return self.palette[int(i)]

    # ------------------------------------------------------------------ bounds
    def inside(self, x, y, z) -> bool:
        return self.X0 <= x <= self.X1 and self.Y0 <= y <= self.Y1 and self.Z0 <= z <= self.Z1

    @contextlib.contextmanager
    def clip(self, *a, **k):  # compatibility with capital helpers that clip
        yield self

    def _keep(self, x, y, z):
        m = (x >= self.X0) & (x <= self.X1) & (y >= self.Y0) & (y <= self.Y1) & (z >= self.Z0) & (z <= self.Z1)
        self.clipped_writes += int(m.size - np.count_nonzero(m))
        return m

    # ------------------------------------------------------------------ writes
    def set(self, x, y, z, state: str, only_empty=False):
        x, y, z = int(x), int(y), int(z)
        if not self.inside(x, y, z):
            self.clipped_writes += 1
            return False
        if only_empty and self.a[x - self.X0, z - self.Z0, y - self.Y0] > AIR:
            return False
        self.a[x - self.X0, z - self.Z0, y - self.Y0] = self.idx(state)
        return True

    def set_idx_many(self, xs, ys, zs, ids, only_empty=False, replace_ids=None):
        xs = np.asarray(xs, np.int64).ravel()
        ys = np.asarray(ys, np.int64).ravel()
        zs = np.asarray(zs, np.int64).ravel()
        if xs.size == 0:
            return 0
        ids = np.broadcast_to(np.asarray(ids, np.uint16), xs.shape).ravel()
        m = self._keep(xs, ys, zs)
        xs, ys, zs, ids = xs[m] - self.X0, ys[m] - self.Y0, zs[m] - self.Z0, ids[m]
        if only_empty or replace_ids is not None:
            cur = self.a[xs, zs, ys]
            k = cur <= AIR if only_empty else np.isin(cur, np.asarray(list(replace_ids), np.uint16))
            xs, ys, zs, ids = xs[k], ys[k], zs[k], ids[k]
        self.a[xs, zs, ys] = ids
        return int(xs.size)

    def set_many(self, xs, ys, zs, state: str, only_empty=False, replace=None):
        rid = None if replace is None else [self.idx(s) for s in replace]
        return self.set_idx_many(xs, ys, zs, self.idx(state), only_empty, rid)

    def fill(self, x0, y0, z0, x1, y1, z1, state: str, only_empty=False, replace=None):
        x0, x1 = sorted((int(x0), int(x1)))
        y0, y1 = sorted((int(y0), int(y1)))
        z0, z1 = sorted((int(z0), int(z1)))
        cx0, cy0, cz0 = max(x0, self.X0), max(y0, self.Y0), max(z0, self.Z0)
        cx1, cy1, cz1 = min(x1, self.X1), min(y1, self.Y1), min(z1, self.Z1)
        total = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1)
        if cx0 > cx1 or cy0 > cy1 or cz0 > cz1:
            self.clipped_writes += total
            return 0
        self.clipped_writes += total - (cx1 - cx0 + 1) * (cy1 - cy0 + 1) * (cz1 - cz0 + 1)
        i = self.idx(state)
        view = self.a[cx0 - self.X0:cx1 - self.X0 + 1, cz0 - self.Z0:cz1 - self.Z0 + 1,
                      cy0 - self.Y0:cy1 - self.Y0 + 1]
        if not only_empty and replace is None:
            view[...] = i
            return view.size
        m = np.ones(view.shape, bool)
        if only_empty:
            m &= view <= AIR
        if replace is not None:
            m &= np.isin(view, np.asarray([self.idx(s) for s in replace], np.uint16))
        view[m] = i
        return int(np.count_nonzero(m))

    def fill_if_empty(self, x0, y0, z0, x1, y1, z1, state: str):
        return self.fill(x0, y0, z0, x1, y1, z1, state, only_empty=True)

    def replace(self, x0, y0, z0, x1, y1, z1, old_states, new_state: str):
        if isinstance(old_states, str):
            old_states = [old_states]
        return self.fill(x0, y0, z0, x1, y1, z1, new_state, replace=old_states)

    def clear(self, x0, y0, z0, x1, y1, z1):
        return self.fill(x0, y0, z0, x1, y1, z1, AIR_S)

    def unset(self, x, y, z):
        """Back to 'keep the terrain'."""
        if self.inside(x, y, z):
            self.a[x - self.X0, z - self.Z0, y - self.Y0] = VOID

    # ------------------------------------------------------------------ reads
    def get_idx(self, x, y, z) -> int:
        if not self.inside(x, y, z):
            return VOID
        return int(self.a[x - self.X0, z - self.Z0, y - self.Y0])

    def get(self, x, y, z) -> str:
        return self.palette[self.get_idx(x, y, z)]

    def is_empty(self, x, y, z) -> bool:
        """Nothing solid there: the piece leaves it (and the terrain is air there) or the piece put air."""
        i = self.get_idx(x, y, z)
        if i == AIR:
            return True
        if i == VOID:
            return not self.terrain_solid(x, y, z) and not self.terrain_wet(x, y, z)
        return False

    def terrain_solid(self, x, y, z) -> bool:
        gi, gj = int(x) - self.gx0, int(z) - self.gz0
        if 0 <= gi < self.g.nx and 0 <= gj < self.g.nz:
            return y <= int(self.g.top[gi, gj])
        return False

    def terrain_wet(self, x, y, z) -> bool:
        gi, gj = int(x) - self.gx0, int(z) - self.gz0
        if 0 <= gi < self.g.nx and 0 <= gj < self.g.nz:
            return int(self.g.top[gi, gj]) < y <= int(self.g.water[gi, gj])
        return False

    def effective(self, x, y, z) -> str:
        """The block that will be in the world: the piece's block, or the terrain's (approximately)."""
        i = self.get_idx(x, y, z)
        if i > VOID:
            return self.palette[i]
        if self.terrain_solid(x, y, z):
            return "minecraft:stone" if y < self.top(x, z) - 3 else _ground_state(self.surface(x, z),
                                                                                    y == self.top(x, z))
        if self.terrain_wet(x, y, z):
            return "minecraft:water[level=0]"
        return AIR_S

    def top_y(self, x, z) -> int:
        col = self.a[x - self.X0, z - self.Z0]
        nz = np.nonzero(col > AIR)[0]
        return int(nz[-1] + self.Y0) if nz.size else self.Y0 - 1

    # ------------------------------------------------------------------ extras
    def be(self, x, y, z, snbt: str):
        x, y, z = int(x), int(y), int(z)
        if self.inside(x, y, z):
            self.block_entities[(x, y, z)] = snbt

    def entity(self, etype: str, pos, yaw=0.0, nbt="{}"):
        """A decoration entity (minecart on a rail, armour stand...) at absolute position ``pos`` (feet)."""
        x, y, z = (float(v) for v in pos)
        if not (self.X0 <= x < self.X1 + 1 and self.Z0 <= z < self.Z1 + 1):
            self.clipped_writes += 1
            return
        self.entities.append({"type": B.full_id(etype), "pos": [round(x, 4), round(y, 4), round(z, 4)],
                              "yaw": round(float(yaw), 1), "nbt": nbt})

    def fluid_tick(self, x, y, z):
        self.fluid_ticks.add((int(x), int(y), int(z)))

    def spot(self, name, pos, yaw=0.0):
        self.spots[name] = {"pos": [int(pos[0]), int(pos[1]), int(pos[2])], "yaw": float(yaw)}

    def probe(self, x, y, z):
        """Mark a block the GameTest checks after world generation (must be a vanilla state)."""
        self.probes.append((int(x), int(y), int(z)))

    def frame(self, origin=(0, 0, 0), rot=0, mirror=False) -> Frame:
        return Frame(self, origin, rot, mirror)

    def count_nonvoid(self) -> int:
        return int(np.count_nonzero(self.a > VOID))

    def view(self, name, pos, look, fov=55.0, sun=(40.0, -150.0), label=None):
        self.views.append(dict(name=name, pos=pos, look=look, fov=fov, sun=sun, label=label))


_GROUND = {
    "grass": "minecraft:grass_block[snowy=false]", "forest_floor": "minecraft:grass_block[snowy=false]",
    "dirt_path": "minecraft:dirt_path", "sand": "minecraft:sand", "gravel": "minecraft:gravel",
    "stone": "minecraft:stone", "snow": "minecraft:snow_block", "mud": "minecraft:mud",
    "coarse_dirt": "minecraft:coarse_dirt", "riverbed": "minecraft:gravel", "lakebed": "minecraft:sand",
    "seabed": "minecraft:sand", "cliff": "minecraft:stone", "quarry": "minecraft:andesite",
    "ash": "minecraft:coarse_dirt",
}
_UNDER = {"sand": "minecraft:sand", "gravel": "minecraft:gravel", "snow": "minecraft:snow_block",
          "stone": "minecraft:stone", "cliff": "minecraft:stone", "quarry": "minecraft:stone",
          "seabed": "minecraft:sand", "lakebed": "minecraft:sand"}


def _ground_state(surface: str, top: bool) -> str:
    if top:
        return _GROUND.get(surface, "minecraft:grass_block[snowy=false]")
    return _UNDER.get(surface, "minecraft:dirt")


def ground_state(surface: str, top=True) -> str:
    return _ground_state(surface, top)
