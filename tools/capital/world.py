"""Voxel world over the capital footprint, plus ``Frame`` for rotated local building coordinates.

Coordinates are capital-local: x east, z south, y up, y = 0 is the top of the plaza floor block
(players stand at y = 1). The array covers the whole footprint box; palette index 0 is
``eternalrealm:void`` ("do not touch"), index 1 is ``minecraft:air``.

All write methods go through the optional clip box (see :meth:`World.clip`), which district builders use so
that every agent stays inside its own region.
"""
from __future__ import annotations

import contextlib

import numpy as np

from . import blocks as B

X0, X1 = -176, 175
Z0, Z1 = -208, 175
Y0, Y1 = -32, 159
NX, NZ, NY = X1 - X0 + 1, Z1 - Z0 + 1, Y1 - Y0 + 1
VOID, AIR = 0, 1

H_DIRS = ("north", "east", "south", "west")
DIR_VEC = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}  # entity yaw facing a direction


def left_of(facing):
    return {"north": "west", "west": "south", "south": "east", "east": "north"}[facing]


def right_of(facing):
    return {"north": "east", "east": "south", "south": "west", "west": "north"}[facing]


def norm_yaw(yaw) -> float:
    """Normalise a Minecraft yaw to (-180, 180] rounded to 0.1 degree."""
    y = round(((float(yaw) + 180.0) % 360.0) - 180.0, 1)
    return 180.0 if y == -180.0 else (0.0 if y == 0 else y)


class ClipError(RuntimeError):
    pass


class World:
    """The voxel model. ``a[x - X0, z - Z0, y - Y0]`` holds palette indices (uint16)."""

    def __init__(self):
        self.a = np.zeros((NX, NZ, NY), np.uint16)
        self.palette: list[str] = ["eternalrealm:void", "minecraft:air"]
        self._index: dict[str, int] = {s: i for i, s in enumerate(self.palette)}
        self.block_entities: dict[tuple, str] = {}
        self.entities: list[dict] = []
        self.fluid_ticks: set[tuple] = set()
        self.spots: dict[str, dict] = {}
        self.footprint = np.ones((NX, NZ), bool)
        self._clip = None
        self._clip_mask = None
        self._cm_key = None
        self._cm_cache = None
        self.clipped_writes = 0
        self.strict_clip = False
        self.notes: list[str] = []

    # ------------------------------------------------------------------ palette
    def idx(self, state: str) -> int:
        i = self._index.get(state)
        if i is not None:
            return i
        c = B.canon(state)
        i = self._index.get(c)
        if i is None:
            if len(self.palette) >= 65535:
                raise RuntimeError("palette overflow")
            i = len(self.palette)
            self.palette.append(c)
            self._index[c] = i
        self._index[state] = i
        return i

    def state(self, i: int) -> str:
        return self.palette[int(i)]

    # ------------------------------------------------------------------ bounds / clip
    @staticmethod
    def inside(x, y, z) -> bool:
        return X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1

    @contextlib.contextmanager
    def clip(self, x0, z0, x1, z1, y0=Y0, y1=Y1, strict=False, mask=None):
        """Restrict all writes to the inclusive box (and the optional (NX, NZ) column ``mask``); writes
        outside are dropped (or raise ``ClipError`` when ``strict``). Nested clips intersect."""
        old, old_strict, old_mask = self._clip, self.strict_clip, self._clip_mask
        box = (min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1))
        if old is not None:
            box = (max(box[0], old[0]), max(box[1], old[1]), max(box[2], old[2]),
                   min(box[3], old[3]), min(box[4], old[4]), min(box[5], old[5]))
        if mask is not None and old_mask is not None:
            mask = mask & old_mask
        elif mask is None:
            mask = old_mask
        self._clip, self.strict_clip, self._clip_mask = box, strict, mask
        try:
            yield self
        finally:
            self._clip, self.strict_clip, self._clip_mask = old, old_strict, old_mask

    def district_clip(self, district, strict=False, y0=Y0, y1=Y1):
        """Clip writes to a ``layout.District`` (its boxes minus excluded circles)."""
        x0, z0, x1, z1 = district.bbox()
        return self.clip(x0, z0, x1, z1, y0, y1, strict=strict, mask=district.mask())

    def _colmask(self):
        if self._clip_mask is None:
            return self.footprint
        if self._cm_key is not self._clip_mask:
            self._cm_cache = self.footprint & self._clip_mask
            self._cm_key = self._clip_mask
        return self._cm_cache

    def set_footprint(self, mask):
        """Set the (NX, NZ) owned-column mask; columns outside stay void forever."""
        self.footprint = np.asarray(mask, bool).copy()
        self._cm_key = None
        self.a[~self.footprint] = VOID

    def _bounds(self):
        if self._clip is None:
            return X0, Y0, Z0, X1, Y1, Z1
        return self._clip

    def _keep(self, x, y, z):
        """Boolean mask of coordinates inside the world box, the clip box and the footprint."""
        bx0, by0, bz0, bx1, by1, bz1 = self._bounds()
        m = (x >= bx0) & (x <= bx1) & (y >= by0) & (y <= by1) & (z >= bz0) & (z <= bz1)
        if m.any():
            mm = m.copy()
            mm[m] = self._colmask()[x[m] - X0, z[m] - Z0]
            m = mm
        dropped = int(m.size - np.count_nonzero(m))
        if dropped:
            self.clipped_writes += dropped
            if self.strict_clip and self._clip is not None:
                raise ClipError(f"{dropped} writes outside clip box {self._clip}")
        return m

    # ------------------------------------------------------------------ writes
    def set(self, x, y, z, state: str, only_empty=False):
        x, y, z = int(x), int(y), int(z)
        bx0, by0, bz0, bx1, by1, bz1 = self._bounds()
        if not (bx0 <= x <= bx1 and by0 <= y <= by1 and bz0 <= z <= bz1) or not self._colmask()[x - X0, z - Z0]:
            self.clipped_writes += 1
            if self.strict_clip and self._clip is not None:
                raise ClipError(f"write at {(x, y, z)} outside clip box {self._clip}")
            return False
        cur = self.a[x - X0, z - Z0, y - Y0]
        if only_empty and cur > AIR:
            return False
        self.a[x - X0, z - Z0, y - Y0] = self.idx(state)
        return True

    def set_idx_many(self, xs, ys, zs, ids, only_empty=False, replace_ids=None):
        xs = np.asarray(xs, np.int64).ravel()
        ys = np.asarray(ys, np.int64).ravel()
        zs = np.asarray(zs, np.int64).ravel()
        if xs.size == 0:
            return 0
        ids = np.broadcast_to(np.asarray(ids, np.uint16), xs.shape).ravel()
        m = self._keep(xs, ys, zs)
        xs, ys, zs, ids = xs[m] - X0, ys[m] - Y0, zs[m] - Z0, ids[m]
        if only_empty or replace_ids is not None:
            cur = self.a[xs, zs, ys]
            if only_empty:
                k = cur <= AIR
            else:
                k = np.isin(cur, np.asarray(list(replace_ids), np.uint16))
            xs, ys, zs, ids = xs[k], ys[k], zs[k], ids[k]
        self.a[xs, zs, ys] = ids
        return int(xs.size)

    def set_many(self, xs, ys, zs, state: str, only_empty=False, replace=None):
        rid = None if replace is None else [self.idx(s) for s in replace]
        return self.set_idx_many(xs, ys, zs, self.idx(state), only_empty, rid)

    def fill(self, x0, y0, z0, x1, y1, z1, state: str, only_empty=False, replace=None):
        """Fill the inclusive box (corners in any order)."""
        x0, x1 = sorted((int(x0), int(x1)))
        y0, y1 = sorted((int(y0), int(y1)))
        z0, z1 = sorted((int(z0), int(z1)))
        bx0, by0, bz0, bx1, by1, bz1 = self._bounds()
        cx0, cy0, cz0 = max(x0, bx0), max(y0, by0), max(z0, bz0)
        cx1, cy1, cz1 = min(x1, bx1), min(y1, by1), min(z1, bz1)
        total = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1)
        if cx0 > cx1 or cy0 > cy1 or cz0 > cz1:
            self.clipped_writes += total
            if self.strict_clip and self._clip is not None:
                raise ClipError(f"fill {(x0, y0, z0, x1, y1, z1)} outside clip box {self._clip}")
            return 0
        inside = (cx1 - cx0 + 1) * (cy1 - cy0 + 1) * (cz1 - cz0 + 1)
        if inside != total:
            self.clipped_writes += total - inside
            if self.strict_clip and self._clip is not None:
                raise ClipError(f"fill {(x0, y0, z0, x1, y1, z1)} crosses clip box {self._clip}")
        i = self.idx(state)
        sl = (slice(cx0 - X0, cx1 - X0 + 1), slice(cz0 - Z0, cz1 - Z0 + 1), slice(cy0 - Y0, cy1 - Y0 + 1))
        fp = self._colmask()[sl[0], sl[1]][:, :, None]
        view = self.a[sl]
        m = np.broadcast_to(fp, view.shape)
        if only_empty:
            m = m & (view <= AIR)
        if replace is not None:
            m = m & np.isin(view, np.asarray([self.idx(s) for s in replace], np.uint16))
        view[m] = i
        return int(np.count_nonzero(m))

    def fill_if_empty(self, x0, y0, z0, x1, y1, z1, state: str):
        return self.fill(x0, y0, z0, x1, y1, z1, state, only_empty=True)

    def replace(self, x0, y0, z0, x1, y1, z1, old_states, new_state: str):
        if isinstance(old_states, str):
            old_states = [old_states]
        return self.fill(x0, y0, z0, x1, y1, z1, new_state, replace=old_states)

    def clear(self, x0, y0, z0, x1, y1, z1):
        return self.fill(x0, y0, z0, x1, y1, z1, "minecraft:air")

    # ------------------------------------------------------------------ reads
    def get_idx(self, x, y, z) -> int:
        if not self.inside(x, y, z):
            return VOID
        return int(self.a[x - X0, z - Z0, y - Y0])

    def get(self, x, y, z) -> str:
        return self.palette[self.get_idx(x, y, z)]

    def is_empty(self, x, y, z) -> bool:
        return self.get_idx(x, y, z) <= AIR

    def top_y(self, x, z, solid_only=True) -> int:
        """Highest non-air y of a column (``Y0 - 1`` if empty)."""
        col = self.a[x - X0, z - Z0]
        nz = np.nonzero(col > AIR)[0]
        return int(nz[-1] + Y0) if nz.size else Y0 - 1

    def heightmap(self) -> np.ndarray:
        """(NX, NZ) int array of the highest non-air y per column."""
        nonair = self.a > AIR
        any_ = nonair.any(axis=2)
        top = NY - 1 - np.argmax(nonair[:, :, ::-1], axis=2)
        return np.where(any_, top + Y0, Y0 - 1)

    # ------------------------------------------------------------------ extras
    def be(self, x, y, z, snbt: str):
        """Attach block-entity SNBT to the block at (x, y, z) (no id/x/y/z keys needed)."""
        x, y, z = int(x), int(y), int(z)
        bx0, by0, bz0, bx1, by1, bz1 = self._bounds()
        if not (bx0 <= x <= bx1 and by0 <= y <= by1 and bz0 <= z <= bz1) or not self.inside(x, y, z) \
                or not self._colmask()[x - X0, z - Z0]:
            return
        self.block_entities[(x, y, z)] = snbt

    def entity(self, etype: str, pos, yaw=0.0, nbt="{}"):
        x, y, z = (float(v) for v in pos)
        bx0, by0, bz0, bx1, by1, bz1 = self._bounds()
        if not (bx0 <= x < bx1 + 1 and bz0 <= z < bz1 + 1):
            self.clipped_writes += 1
            return
        self.entities.append({"type": B.full_id(etype), "pos": [round(x, 4), round(y, 4), round(z, 4)],
                              "yaw": norm_yaw(yaw), "nbt": nbt})

    def fluid_tick(self, x, y, z):
        self.fluid_ticks.add((int(x), int(y), int(z)))

    def spot(self, name: str, pos, yaw=0.0):
        """Named feet position (+ Minecraft yaw: 0 south, 90 west, 180 north, -90 east)."""
        self.spots[name] = {"pos": [int(pos[0]), int(pos[1]), int(pos[2])], "yaw": norm_yaw(yaw)}

    def count_nonair(self) -> int:
        return int(np.count_nonzero(self.a > AIR))

    def frame(self, origin=(0, 0, 0), rot=0, mirror=False) -> "Frame":
        return Frame(self, origin, rot, mirror)


# ====================================================================================== Frame
_ROT_M = {0: ((1, 0), (0, 1)), 1: ((0, -1), (1, 0)), 2: ((-1, 0), (0, -1)), 3: ((0, 1), (-1, 0))}


class Frame:
    """Local building coordinates: ``Frame(world, origin, rot, mirror)``.

    Local (x, y, z) is mirrored east/west first (if ``mirror``), then rotated ``rot`` quarter turns
    clockwise seen from above (rot=1 turns local south into world west, i.e. Minecraft CLOCKWISE_90),
    then translated by ``origin``. Block states (facing/axis/rotation/hinge/stair shape...) and entity
    yaws are transformed accordingly. Frames nest: ``frame.sub((dx, dy, dz), rot)``.
    """

    def __init__(self, world, origin=(0, 0, 0), rot=0, mirror=False, _chain=None, _m=None, _t=None):
        self.w: World = world
        ox, oy, oz = (int(v) for v in origin)
        if _chain is None:
            r = _ROT_M[rot % 4]
            mx = -1 if mirror else 1
            self.m = np.array([[r[0][0] * mx, r[0][1]], [r[1][0] * mx, r[1][1]]], np.int64)
            self.t = np.array([ox, oz], np.int64)
            self.ty = oy
            self.chain = ((rot % 4, bool(mirror)),)
        else:
            self.m, self.t, self.ty, self.chain = _m, _t, oy, _chain

    def sub(self, origin=(0, 0, 0), rot=0, mirror=False) -> "Frame":
        """Child frame whose origin is given in this frame's local coordinates."""
        ox, oy, oz = origin
        r = _ROT_M[rot % 4]
        mx = -1 if mirror else 1
        cm = np.array([[r[0][0] * mx, r[0][1]], [r[1][0] * mx, r[1][1]]], np.int64)
        m = self.m @ cm
        t = self.m @ np.array([ox, oz], np.int64) + self.t
        return Frame(self.w, (0, self.ty + oy, 0), _chain=((rot % 4, bool(mirror)),) + self.chain, _m=m, _t=t)

    # -------------------------------------------------------------- transforms
    def pos(self, x, y, z):
        wx = self.m[0, 0] * x + self.m[0, 1] * z + self.t[0]
        wz = self.m[1, 0] * x + self.m[1, 1] * z + self.t[1]
        return int(wx), int(y + self.ty), int(wz)

    def pos_f(self, x, y, z):
        """Transform a fractional (entity) position; block (0,0,0) spans [0,1) so we transform the centre."""
        cx, cz = x - 0.5, z - 0.5
        wx = self.m[0, 0] * cx + self.m[0, 1] * cz + self.t[0] + 0.5
        wz = self.m[1, 0] * cx + self.m[1, 1] * cz + self.t[1] + 0.5
        return float(wx), float(y + self.ty), float(wz)

    def st(self, state: str) -> str:
        s = B.canon(state)
        for rot, mir in self.chain:
            s = B.transform(s, rot, mir)
        return s

    def dir(self, d: str) -> str:
        """Local direction name -> world direction name."""
        if d in ("up", "down"):
            return d
        v = DIR_VEC[d]
        wx = self.m[0, 0] * v[0] + self.m[0, 1] * v[2]
        wz = self.m[1, 0] * v[0] + self.m[1, 1] * v[2]
        for k, (a, _, c) in DIR_VEC.items():
            if a == wx and c == wz and k not in ("up", "down"):
                return k
        raise AssertionError(d)

    def yaw(self, yaw: float) -> float:
        # yaw 0 = local south; work through a direction vector
        import math
        rad = math.radians(yaw)
        vx, vz = -math.sin(rad), math.cos(rad)
        wx = self.m[0, 0] * vx + self.m[0, 1] * vz
        wz = self.m[1, 0] * vx + self.m[1, 1] * vz
        return math.degrees(math.atan2(-wx, wz))

    # -------------------------------------------------------------- writes
    def set(self, x, y, z, state: str, only_empty=False):
        return self.w.set(*self.pos(x, y, z), self.st(state), only_empty=only_empty)

    def set_many(self, xs, ys, zs, state: str, only_empty=False, replace=None):
        xs = np.asarray(xs, np.int64)
        zs = np.asarray(zs, np.int64)
        wx = self.m[0, 0] * xs + self.m[0, 1] * zs + self.t[0]
        wz = self.m[1, 0] * xs + self.m[1, 1] * zs + self.t[1]
        rep = None if replace is None else [self.st(s) for s in replace]
        return self.w.set_many(wx, np.asarray(ys, np.int64) + self.ty, wz, self.st(state), only_empty, rep)

    def fill(self, x0, y0, z0, x1, y1, z1, state: str, only_empty=False, replace=None):
        a = self.pos(x0, y0, z0)
        b = self.pos(x1, y1, z1)
        rep = None if replace is None else [self.st(s) for s in replace]
        return self.w.fill(a[0], a[1], a[2], b[0], b[1], b[2], self.st(state), only_empty, rep)

    def fill_if_empty(self, x0, y0, z0, x1, y1, z1, state):
        return self.fill(x0, y0, z0, x1, y1, z1, state, only_empty=True)

    def clear(self, x0, y0, z0, x1, y1, z1):
        return self.fill(x0, y0, z0, x1, y1, z1, "minecraft:air")

    def get(self, x, y, z) -> str:
        return self.w.get(*self.pos(x, y, z))

    def is_empty(self, x, y, z) -> bool:
        return self.w.is_empty(*self.pos(x, y, z))

    def be(self, x, y, z, snbt):
        self.w.be(*self.pos(x, y, z), snbt)

    def entity(self, etype, pos, yaw=0.0, nbt="{}"):
        self.w.entity(etype, self.pos_f(*pos), self.yaw(yaw), nbt)

    def fluid_tick(self, x, y, z):
        self.w.fluid_tick(*self.pos(x, y, z))

    def spot(self, name, pos, yaw=0.0):
        self.w.spot(name, self.pos(*pos), self.yaw(yaw))
