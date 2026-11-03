"""Preview-only scene props for the HITORI entity sheets (flat-coloured boxes, not the real block models): the
tatami room, the old flooring, the bathroom, door frames, the closet. World units are blocks; the entity stands at
the origin facing +z (yaw 0), i.e. toward the default preview camera."""
import math

import numpy as np

import ent_render as ER


def _tatami_tex(seed=31):
    r = np.random.default_rng(seed)
    t = np.zeros((32, 32, 4), np.float64)
    for y in range(32):
        for x in range(32):
            weave = 0.92 + 0.08 * ((x + (y // 2) % 2) % 2)
            t[y, x, :3] = np.array([150, 142, 92]) * weave * (1 + r.normal(0, 0.03))
    t[:, 0:2, :3] = (52, 44, 34)          # the mat border (heri)
    t[0:1, :, :3] *= 0.8
    t[..., 3] = 255
    return t


def _floor_tex(seed=33):
    r = np.random.default_rng(seed)
    t = np.zeros((32, 32, 4), np.float64)
    base = np.array([72, 46, 30], float)
    for row in range(4):
        tone = 1 + r.normal(0, 0.07)
        for y in range(row * 8, row * 8 + 8):
            for x in range(32):
                t[y, x, :3] = base * tone * (1 + 0.06 * math.sin(x * 0.5 + row * 2) + r.normal(0, 0.02))
        t[row * 8, :, :3] *= 0.62
    t[..., 3] = 255
    return t


def _tile_tex(seed=35):
    r = np.random.default_rng(seed)
    t = np.zeros((32, 32, 4), np.float64)
    t[..., :3] = np.array([150, 176, 182]) * (1 + r.normal(0, 0.025, (32, 32, 1)))
    t[::8, :, :3] = (92, 100, 98)
    t[:, ::8, :3] = (92, 100, 98)
    t[..., 3] = 255
    return t


TATAMI = _tatami_tex()
FLOOR = _floor_tex()
TILE = _tile_tex()
WALL = ER.flat_tex((168, 156, 128), 0.05, 41)           # old beige plaster
FRAME = ER.flat_tex((86, 60, 40), 0.06, 42)             # door frame wood
FUSUMA = ER.flat_tex((196, 188, 160), 0.03, 43)
FUSUMA_EDGE = ER.flat_tex((40, 30, 24), 0.05, 44)
TUB = ER.flat_tex((150, 178, 186), 0.03, 45)
STEEL = ER.flat_tex((150, 155, 160), 0.05, 46)
SHELF = ER.flat_tex((110, 80, 54), 0.06, 47)


def _water():
    t = np.zeros((16, 16, 4), np.float64)
    r = np.random.default_rng(48)
    t[..., :3] = np.array([40, 58, 62]) * (1 + r.normal(0, 0.05, (16, 16, 1)))
    t[..., 3] = 170
    return t


WATER = _water()


def tatami(light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=TATAMI)
    return sc


def flooring(light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=FLOOR)
    return sc


def wall_behind(light=1.0, dist=0.2, floor_tex=None):
    """A wall right behind the entity (it sits slumped against it)."""
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=TATAMI if floor_tex is None else floor_tex)
        ER.box(cv, (-1.5, 0.0, -dist - 0.3), (1.5, 2.2, -dist), WALL, light=light)
    return sc


def bathtub(light=1.0):
    """The deep square tub: the entity origin is the water surface (y 0), the tub floor 0.55 below."""
    def sc(cv):
        ER.floor(cv, 3, y=-0.62, light=light, tex=TILE)
        for (a, b) in (((-0.62, -0.62, -0.95), (0.62, 0.16, -0.85)), ((-0.62, -0.62, 0.85), (0.62, 0.16, 0.95)),
                       ((-0.72, -0.62, -0.95), (-0.62, 0.16, 0.95)), ((0.62, -0.62, -0.95), (0.72, 0.16, 0.95))):
            ER.box(cv, a, b, TUB, light=light)
        pts = np.array([(-0.62, 0.0, -0.85), (0.62, 0.0, -0.85), (0.62, 0.0, 0.85), (-0.62, 0.0, 0.85)], float)
        cv.quad(pts, np.array([(0, 0), (1, 0), (1, 1), (0, 1)], float), WATER, shade=1.0, mode='translucent')
    return sc


def drained_tub(light=1.0):
    """The emptied tub (the doll burns on its floor): the entity stands on the tub floor (y 0)."""
    def sc(cv):
        ER.floor(cv, 3, y=0.0, light=light, tex=TILE)
        for (a, b) in (((-0.62, 0.0, -0.95), (0.62, 0.8, -0.85)), ((-0.72, 0.0, -0.95), (-0.62, 0.8, 0.95)),
                       ((0.62, 0.0, -0.95), (0.72, 0.8, 0.95))):
            ER.box(cv, a, b, TUB, light=light)
    return sc


def door_frame(light=1.0, side=1.0, edge=0.17):
    """A wall just in front of the entity whose door opening starts `edge` blocks to its left (side +1; -1: its
    right): the body stays hidden behind the wall, only what leans out past the frame shows. At yaw 0 the entity's
    left is world +x."""
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=FLOOR)
        e = side * edge
        if side > 0:
            ER.box(cv, (e - 2.0, 0.0, 0.2), (e, 2.2, 0.34), WALL, light=light)
            ER.box(cv, (e - 0.02, 0.0, 0.18), (e + 0.07, 2.2, 0.36), FRAME, light=light)
        else:
            ER.box(cv, (e, 0.0, 0.2), (e + 2.0, 2.2, 0.34), WALL, light=light)
            ER.box(cv, (e - 0.07, 0.0, 0.18), (e + 0.02, 2.2, 0.36), FRAME, light=light)
    return sc


def corner(light=1.0):
    """A room corner right in front of the entity (at yaw 0 it faces +z, into the corner)."""
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=TATAMI)
        ER.box(cv, (-1.5, 0.0, 0.42), (1.5, 2.6, 0.72), WALL, light=light)
        ER.box(cv, (0.42, 0.0, -1.5), (0.72, 2.6, 0.42), WALL, light=light)
    return sc


def closet(light=1.0):
    """The closet's fusuma right in front of the camera: the doll springs at the viewer from the tatami room."""
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=TATAMI)
    return sc


def shelf(light=1.0, top=0.0, wall=True):
    """A shelf board under the entity (the camera stands on it); the board top is the entity origin."""
    def sc(cv):
        ER.floor(cv, 3, y=-0.9, light=light, tex=FLOOR)
        ER.box(cv, (-0.5, top - 0.06, -0.35), (0.5, top, 0.35), SHELF, light=light)
        if wall:
            ER.box(cv, (-0.5, -0.9, -0.42), (0.5, 0.9, -0.35), WALL, light=light)
    return sc
