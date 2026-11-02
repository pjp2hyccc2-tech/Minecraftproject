#!/usr/bin/env python3
"""Forest scene previews (not shipped): renders the cedar forest textures in perspective the way they will be
seen in game (large repeated areas), by day and with a night + flashlight look.

usage: python3 tools/art/scene.py            -> tools/art/preview/forest_*.png
"""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image

import gen_textures as G  # noqa: E402
import render3d as R  # noqa: E402
from common import PREVIEW, vnoise, rng  # noqa: E402

_CACHE = {}


def tex(name):
    if name not in _CACHE:
        _CACHE[name] = G.render(name).astype(np.float64)
    return _CACHE[name]


def build_world(seed=5, size=44):
    r = rng(seed)
    blocks = {}            # (x, y, z) -> kind
    ground = {}
    hn = vnoise(size, size, 3, 3, seed + 1)
    mossn = vnoise(size, size, 6, 6, seed + 2)
    x0, z0 = -size // 2, -2
    for i in range(size):
        for k in range(size):
            x, z = x0 + i, z0 + k
            h = int(hn[k, i] * 2.2) - 1
            trail = abs(x - 2.5 * math.sin(z / 7.0)) < 1.0
            kind = 'trail' if trail else ('moss' if mossn[k, i] > 0.72 else 'floor')
            ground[(x, z)] = (h, kind)
    trunks = []
    for _ in range(3000):
        if len(trunks) > 70:
            break
        x, z = int(r.integers(x0, x0 + size)), int(r.integers(1, z0 + size))
        if abs(x - 2.5 * math.sin(z / 7.0)) < 2.0:
            continue
        if any((x - a) ** 2 + (z - b) ** 2 < 6.5 for a, b, _ in trunks):
            continue
        trunks.append((x, z, int(r.integers(9, 15))))
    for (x, z, h) in trunks:
        gy = ground[(x, z)][0]
        kind = 'log'
        u = r.random()
        for y in range(gy, gy + h):
            blocks[(x, y, z)] = 'log'
        if u < 0.12:
            blocks[(x, gy + 1, z)] = 'ribbon'
        elif u < 0.16:
            blocks[(x, gy + 1, z)] = 'shimenawa'
        top = gy + h
        for y in range(top - 4, top + 4):
            rad = max(0.0, (top + 4 - y) / 8.0 * 2.6)
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz <= rad * rad + 0.3 and (x + dx, y, z + dz) not in blocks:
                        blocks[(x + dx, y, z + dz)] = 'leaves'
    for _ in range(14):
        x, z = int(r.integers(x0, x0 + size)), int(r.integers(2, z0 + size))
        gy = ground[(x, z)][0]
        if (x, gy, z) not in blocks:
            blocks[(x, gy, z)] = 'mossy_rock' if r.random() < 0.6 else 'rock'
    decals = [(x, z) for (x, z) in ground if r.random() < 0.10 and ground[(x, z)][1] != 'trail']
    return blocks, ground, decals


SIDES = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def face_tex(kind, face):
    if kind in ('log', 'ribbon', 'shimenawa'):
        if face in ('up', 'down'):
            return tex('block/cedar_log_top')
        return tex({'log': 'block/cedar_log', 'ribbon': 'block/cedar_log_ribbon', 'shimenawa': 'block/cedar_log_shimenawa'}[kind])
    if kind == 'leaves':
        return tex('block/cedar_leaves')
    if kind == 'rock':
        return tex('block/mountain_rock')
    if kind == 'mossy_rock':
        return tex('block/mossy_rock')
    if kind == 'floor':
        return tex('block/forest_floor_top') if face == 'up' else tex('block/forest_floor_side' if face != 'down' else 'block/forest_dirt')
    if kind == 'moss':
        return tex('block/moss_floor') if face == 'up' else tex('block/forest_floor_side')
    if kind == 'trail':
        return tex('block/trail_top') if face == 'up' else tex('block/forest_dirt')
    raise KeyError(kind)


def render(blocks, ground, decals, eye, target, w=960, h=540, fov=70, night=False, rot_floor=True):
    cv = R.Canvas(w, h, R.look_at(eye, target), fov=fov, bg=(150, 162, 178) if not night else (20, 23, 30))
    cv.mip = True
    solid = set(blocks)
    for (x, y, z), kind in blocks.items():
        for face, (dx, dy, dz) in SIDES.items():
            nb = (x + dx, y + dy, z + dz)
            if nb in solid and not (kind == 'leaves' or blocks[nb] == 'leaves'):
                continue
            if nb in solid and kind == 'leaves' and blocks[nb] == 'leaves':
                continue
            gnb = ground.get((x + dx, z + dz))
            if gnb and y + dy < gnb[0]:
                continue
            t = face_tex(kind, face)
            pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), face), float) / 16 + [x, y, z]
            uv = R._uv_corners(R._default_uv((0, 0, 0), (16, 16, 16), face))
            cv.quad(pts, uv, t, shade=R.FACE_SHADE[face], double=(kind == 'leaves'))
    for (x, z), (gy, kind) in ground.items():
        t = face_tex(kind, 'up')
        pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), 'up'), float) / 16 + [x, gy - 1, z]
        rot = ((x * 7 + z * 13) % 4) * 90 if (rot_floor and kind != 'trail') else 0
        uv = R._uv_corners([0, 0, 16, 16], rot)
        cv.quad(pts, uv, t, shade=1.0, double=False)
        for face in ('north', 'south', 'east', 'west'):
            dx, _, dz = SIDES[face]
            nb = ground.get((x + dx, z + dz))
            if nb is None or nb[0] >= gy:
                continue
            for yy in range(nb[0], gy):
                tt = face_tex(kind, face) if yy == gy - 1 else tex('block/forest_dirt')
                pts = np.array(R._face_corners((0, 0, 0), (16, 16, 16), face), float) / 16 + [x, yy, z]
                uv = R._uv_corners(R._default_uv((0, 0, 0), (16, 16, 16), face))
                cv.quad(pts, uv, tt, shade=R.FACE_SHADE[face], double=False)
    dl = tex('block/fallen_leaves')
    for (x, z) in decals:
        gy = ground[(x, z)][0]
        pts = np.array([(0, 0.2, 0), (16, 0.2, 0), (16, 0.2, 16), (0, 0.2, 16)], float) / 16 + [x, gy, z]
        rot = ((x * 5 + z * 3) % 4) * 90
        cv.quad(pts, R._uv_corners([0, 0, 16, 16], rot), dl, shade=1.0)
    cv.finish()
    if night:
        cv.night(flashlight=True, strength=1.25, fog_dist=16.0, cone=0.42, amb=0.13)
    return cv


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    blocks, ground, decals = build_world()
    out = []
    views = [('forest_day', (0.5, 1.62, -0.5), (0.0, 1.2, 12.0), False),
             ('forest_night', (0.5, 1.62, -0.5), (0.0, 1.2, 12.0), True),
             ('forest_up', (0.5, 1.62, 5.5), (1.5, 9.0, 12.0), False),
             ('forest_ground', (0.5, 2.4, 3.0), (0.5, 0.0, 9.0), False)]
    sel = sys.argv[1:]
    for name, eye, tgt, night in views:
        if sel and name not in sel:
            continue
        gy = ground[(int(math.floor(eye[0])), int(math.floor(eye[2])))][0]
        eye = (eye[0], eye[1] + gy, eye[2])
        tgt = (tgt[0], tgt[1] + gy, tgt[2])
        cv = render(blocks, ground, decals, eye, tgt, night=night)
        p = os.path.join(PREVIEW, name + '.png')
        cv.image().save(p)
        out.append(p)
        print(p)
    return out


if __name__ == '__main__':
    main()
