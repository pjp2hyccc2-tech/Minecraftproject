"""Preview-only scene props for the spirit sheets (flat-coloured boxes, not the real block models).
World units are blocks; the spirit stands at the origin facing +z (yaw 0)."""
import numpy as np

import ent_render as ER

WOOD = ER.flat_tex((58, 38, 26), 0.08, 11)
DARKWOOD = ER.flat_tex((40, 27, 19), 0.08, 12)
VINYL = ER.flat_tex((112, 42, 34), 0.06, 13)
STEEL = ER.flat_tex((150, 155, 160), 0.05, 14)
TILE = ER.flat_tex((150, 152, 150), 0.05, 15)
GREY = ER.flat_tex((96, 98, 102), 0.05, 16)
WALL = ER.flat_tex((180, 170, 150), 0.04, 17)
GLASS = ER.flat_tex((40, 48, 56), 0.02, 18)


def dining(table=False, booth=False, light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light)
        if table:
            ER.box(cv, (-0.5, 0.70, 0.40), (0.5, 0.76, 1.40), WOOD, light=light)          # table top
            ER.box(cv, (-0.06, 0.0, 0.84), (0.06, 0.70, 0.96), DARKWOOD, light=light)     # pedestal
        if booth:
            ER.box(cv, (-0.5, 0.0, -0.45), (0.5, 0.44, 0.10), VINYL, light=light)         # seat
            ER.box(cv, (-0.5, 0.0, -0.75), (0.5, 1.0, -0.45), VINYL, light=light)         # back
    return sc


def booth_table(light=1.0):
    """Seated at a booth: the bench under the spirit, the table edge 0.45 blocks in front."""
    def sc(cv):
        ER.floor(cv, 3, light=light)
        ER.box(cv, (-0.5, 0.0, -0.50), (0.5, 0.44, 0.12), VINYL, light=light)
        ER.box(cv, (-0.5, 0.0, -0.80), (0.5, 1.05, -0.50), VINYL, light=light)
        ER.box(cv, (-0.6, 0.72, 0.42), (0.6, 0.78, 1.42), WOOD, light=light)
        ER.box(cv, (-0.06, 0.0, 0.86), (0.06, 0.72, 0.98), DARKWOOD, light=light)
        ER.box(cv, (0.15, 0.78, 0.55), (0.27, 0.84, 0.67), STEEL, light=light)          # the call button
    return sc


def pillar(light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light)
        ER.box(cv, (-0.5, 0.0, 0.45), (0.5, 3.0, 1.45), DARKWOOD, light=light)
    return sc


def mirror(light=1.0):
    """The cavity side: the dead sink counter and the glass 0.5 blocks in front."""
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=TILE)
        ER.box(cv, (-1.5, 0.0, 0.22), (1.5, 0.80, 0.50), GREY, light=light)
    return sc


def kitchen(board=False, pass_window=False, light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light, tex=ER.flat_tex((96, 44, 36), 0.08, 19))
        if board:
            ER.box(cv, (-0.5, 0.0, 0.45), (0.5, 0.86, 1.45), STEEL, light=light)
        if pass_window:
            ER.box(cv, (-1.5, 0.0, 0.55), (1.5, 0.95, 0.80), STEEL, light=light)
            ER.box(cv, (-1.5, 2.0, 0.55), (1.5, 3.0, 0.80), WALL, light=light)
    return sc


def desk(light=1.0):
    def sc(cv):
        ER.floor(cv, 3, light=light)
        ER.box(cv, (-0.7, 0.72, 0.30), (0.7, 0.78, 1.10), GREY, light=light)
        ER.box(cv, (-0.6, 0.78, 0.85), (0.6, 1.25, 0.95), GLASS, light=light)
        ER.box(cv, (-0.3, 0.0, -0.3), (0.3, 0.45, 0.3), ER.flat_tex((30, 30, 34), 0.05, 20), light=light)
    return sc


def lot(light=1.0):
    def sc(cv):
        ER.floor(cv, 4, light=light, tex=ER.flat_tex((34, 35, 38), 0.10, 21))
    return sc
