"""冒険者ギルド The adventurers' guild, on the west side of the central plaza and facing it.

Hall x -50..-27, z -38..-7: white castle-stone ground floor with tall round-arched windows, a half-timbered
upper storey, a steep royal-blue roof (ridge north-south) with dormers, gilded trim and a projecting entrance
pavilion facing the fountain. The clock tower stands at the north-east corner (x -27..-19, z -38..-30): clock
dials on its north, east and south faces, an open belfry with the guild bell and a spire carrying a pennant.

Inside: double-height hall with long tables, three chandeliers, a great fireplace, the quest counter in front
of the quest board (Mina), the registrar's desk (Marta), a U-shaped upper gallery; north wing with the kitchen
(Laszlo, the ex-bandit king, at the sink) and the archive below, the infirmary (Wolfram) and the
guildmaster's office (Gustav) above; a spiral stair in the clock tower up to the belfry.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "guild"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF, CARVED = K.CB, K.SM, K.GOLD, K.ROOF, K.CARVED

X0, X1, Z0, Z1 = -50, -27, -38, -7        # hall outer walls
GY = 8                                     # first floor (gallery) level
EAVE = 16                                  # attic floor / eave line
TX0, TX1, TZ0, TZ1 = -27, -19, -37, -29    # clock tower
TCX, TCZ = -23, -33
CLOCK_Y = 29
NZ = -30                                   # internal wall between the hall and the north wing

GUILD_BANNER = B.banner_nbt([("cr", "yellow"), ("bo", "yellow"), ("mc", "red"), ("bts", "yellow"),
                             ("tts", "yellow")])

VIEWS = {
    "guild_hall": dict(pos=(-29.5, 5.5, -13.5), look=(-46, 3, -28), fov=76, sun=(40, -110), fog=900,
                       label="Guild hall: long tables, quest counter and board, gallery"),
    "guild_front": dict(pos=(-7, 7, 10), look=(-38, 14, -22), fov=70, sun=(38, -120), fog=2600,
                        label="The adventurers' guild and its clock tower from the plaza"),
    "guild_rooms": dict(pos=(-30.5, 10.6, -24.5), look=(-47, 9, -29), fov=80, sun=(40, -110), fog=900,
                        label="Guild upper floor: gallery, office and infirmary doors"),
}


def full(m):
    return TK.st_of(m)


# ============================================================================== shell
def shell(w):
    # foundation + floor
    w.fill(X0, -1, Z0, X1, -1, Z1, B.S("stone"))
    w.fill(X0, 0, Z0, X1, 0, Z1, F("stone_bricks"))
    w.fill(X0 + 1, 1, Z0 + 1, X1 - 1, EAVE + 14, Z1 - 1, AIR)
    # ground floor: castle bricks, two blocks thick on the long sides for deep window reveals
    S.walls(w, X0, Z0, X1, Z1, 1, GY - 1, F(CB))
    S.walls(w, X0, Z0, X1, Z1, 1, 1, F("polished_andesite"))         # plinth course
    w.fill(X0, 0, Z0 - 1, X1, 0, Z0 - 1, B.stairs("stone_bricks", "south")) if False else None
    # string course at the first floor line
    S.walls(w, X0, Z0, X1, Z1, GY, GY, F(SM))
    for (x0, z0, x1, z1, fac) in ((X0, Z0 - 1, X1, Z0 - 1, "south"), (X0, Z1 + 1, X1, Z1 + 1, "north"),
                                  (X0 - 1, Z0, X0 - 1, Z1, "east"), (X1 + 1, Z0, X1 + 1, Z1, "west")):
        w.fill(x0, GY, z0, x1, GY, z1, B.stairs(SM, fac, "top"))
    # upper storey: white plaster with dark oak timbering
    S.walls(w, X0, Z0, X1, Z1, GY + 1, EAVE - 1, F("white_plaster"))
    for x in range(X0, X1 + 1):
        if (x - X0) % 3 == 0 or x == X1:
            for z in (Z0, Z1):
                w.fill(x, GY + 1, z, x, EAVE - 1, z, B.log("dark_oak"))
    for z in range(Z0, Z1 + 1):
        if (z - Z0) % 3 == 0 or z == Z1:
            for x in (X0, X1):
                w.fill(x, GY + 1, z, x, EAVE - 1, z, B.log("dark_oak"))
    S.rect_ring(w, X0, Z0, X1, Z1, GY + 4, B.log("dark_oak", "x")) if False else None
    for (a, b, c, d, ax) in ((X0, Z0, X1, Z0, "x"), (X0, Z1, X1, Z1, "x"), (X0, Z0, X0, Z1, "z"),
                             (X1, Z0, X1, Z1, "z")):
        w.fill(a, EAVE, b, c, EAVE, d, B.log("dark_oak", ax))
    # pilasters on the ground floor (castle stone) every 4 along the long facades
    for z in range(Z0, Z1 + 1, 4):
        for (x, fac) in ((X1 + 1, "east"), (X0 - 1, "west")):
            w.fill(x, 1, z, x, GY - 1, z, F(SM))
            w.set(x, GY - 1, z, B.stairs(SM, {"east": "west", "west": "east"}[fac], "top")) if False else None
    for x in range(X0 + 2, X1, 4):
        for z in (Z0 - 1, Z1 + 1):
            w.fill(x, 1, z, x, GY - 1, z, F(SM))
    # ceiling of the hall / attic floor
    w.fill(X0 + 1, EAVE, Z0 + 1, X1 - 1, EAVE, Z1 - 1, F("dark_oak_planks"))


def roof(w):
    r = K.Roof(w, X0, Z0, X1, Z1, EAVE + 1, ROOF, axis="z", overhang=1, gable=F("white_plaster"),
               trim=GOLD, end_overhang=1)
    ridge = r.ridge_y
    # timber on the gables + windows + crest roundel
    for z in (Z0, Z1):
        for x in range(X0, X1 + 1):
            for y in range(EAVE + 1, ridge + 1):
                if w.get(x, y, z) == B.canon(F("white_plaster")):
                    if (x - X0) % 3 == 0:
                        w.set(x, y, z, B.log("dark_oak"))
                    elif y == EAVE + 6:
                        w.set(x, y, z, B.log("dark_oak", "x"))
        f = K.Face(w, "x", z, -1 if z == Z0 else 1)
        for u0 in (X0 + 7, X1 - 8):
            K.window(f, u0, EAVE + 2, 2, 3, arch=None, depth=1, recess=0, sill=None, glass=TK.LEADED)
        cx = (X0 + X1) // 2
        K.window(f, cx - 1, EAVE + 8, 3, 3, arch="round", depth=1, recess=0, sill=None, glass=TK.LEADED)
    # dormers on the east slope (towards the plaza) and the west slope
    for a in (-34, -12):
        if Z0 + 3 <= a <= Z1 - 3:
            pass
    for a in (-35, -25, -12):
        K.dormer(w, r, 1, a, width=3, wall="white_plaster", frame="dark_oak", finial=True, glass=TK.LEADED)
    for a in (-31, -18):
        K.dormer(w, r, -1, a, width=3, wall="white_plaster", frame="dark_oak", finial=True, glass=TK.LEADED)
    # gilded finials on the gable peaks
    cx = (X0 + X1) // 2
    for z in (Z0 - 1, Z1 + 1):
        w.set(cx, ridge + 1, z, B.S("gilded_finial"))
        w.set(cx + 1, ridge + 1, z, B.S("gilded_finial")) if (X1 - X0) % 2 == 1 else None
    return r


# ============================================================================== facades
def east_facade(w):
    """Facade towards the plaza (x = -27): tall arched windows, the entrance pavilion."""
    f = K.Face(w, "z", X1, 1)
    for z0 in (-28, -24, -13, -9):
        K.window(f, z0 + 1, 2, 2, 5, arch="round", depth=1, recess=0, frame=None, sill=SM, keystone=F(GOLD),
                 glass=TK.LEADED)
    # upper storey windows (between posts)
    for z0 in range(Z0 + 10, Z1, 3):
        if -22 <= z0 <= -14:
            continue
        f.fill(z0 + 1, GY + 2, 0, z0 + 2, GY + 4, 0, TK.LEADED)
        f.set(z0 + 1, GY + 1, 1, B.leaves("flowering_azalea"))
        f.set(z0 + 2, GY + 1, 1, B.leaves("azalea"))
        f.set(z0 + 1, GY, 1, B.trapdoor("spruce", "east", "top")) if False else None
    # entrance pavilion: projects to x = -25, z -22..-14, gable roof with the guild crest
    px0, px1, pz0, pz1 = X1, X1 + 2, -22, -14
    w.fill(px0, 0, pz0, px1, 0, pz1, F("stone_bricks"))
    S.walls(w, px0, pz0, px1, pz1, 1, EAVE - 1, F(CB))
    w.fill(px0 + 1, 1, pz0 + 1, px1, EAVE - 1, pz1 - 1, F(CB))
    # big round-arched portal through the pavilion (3 wide, 5 tall) with double doors
    fp = K.Face(w, "z", px1, 1)
    m = S.round_arch_mask(3, 6)
    fp.mask(-19, 1, 0, m, AIR)
    for d in range(-1, -4, -1):
        fp.mask(-19, 1, d, m, AIR)
    for i, zz in enumerate((-19, -18, -17)):
        pass
    for zz, hinge in ((-19, "right"), (-17, "left")):
        lo, hi = B.door_states("dark_oak", "west", hinge)
        w.set(X1, 1, zz, lo)
        w.set(X1, 2, zz, hi)
    lo, hi = B.door_states("dark_oak", "west", "left")
    w.set(X1, 1, -18, B.S("air"))
    w.set(X1, 2, -18, B.S("air"))
    for zz in (-19, -18, -17):
        for y in (3, 4, 5):
            if w.get(X1, y, zz).endswith(":air"):
                w.set(X1, y, zz, B.pane("yellow") if (zz == -18 and y == 5) else TK.LEADED)
    fp.mask(-20, 0, 0, K.ring_of(m), F(SM))
    w.set(px1, 7, -18, F(GOLD))
    w.set(px1, 7, -19, B.stairs(GOLD, "north", "top")) if False else None
    # steps in front of the portal
    for z in range(-21, -14):
        w.set(px1 + 1, 0, z, B.stairs("stone_bricks", "west"))
        w.set(px1 + 2, 0, z, F("polished_andesite"))
    # pavilion pilasters + lanterns
    for z in (pz0, pz1):
        w.fill(px1 + 1, 1, z, px1 + 1, EAVE - 2, z, F(SM))
        w.set(px1 + 1, EAVE - 1, z, B.stairs(SM, "west", "top"))
        w.set(px1 + 2, 4, z, B.wall("polished_blackstone"))
        w.set(px1 + 2, 3, z, B.lantern(hanging=True))
    # banners of the guild either side of the portal, crest panel above
    for z in (-21, -15):
        deco.kingdom_wall_banner(w, px1 + 1, 6, z, facing="east", color="red", nbt=GUILD_BANNER)
    w.set(px1, 9, -18, B.S("castle_crest_stone"))
    w.set(px1, 9, -19, F(GOLD))
    w.set(px1, 9, -17, F(GOLD))
    # pavilion upper window and its sign
    fp.fill(-19, GY + 2, 0, -17, GY + 5, 0, B.pane("light_blue"))
    fp.fill(-19, GY + 6, 0, -17, GY + 6, 0, F(SM))
    deco.sign_text(w, px1 + 1, GY, -18, B.S("dark_oak_wall_sign", facing="east"),
                   ["", "冒険者ギルド", "Adventurers' Guild", ""], color="yellow", glowing=True)
    # pavilion gable roof (ridge along x) intersecting the main roof
    S.gable_roof(w, px0 - 6, pz0, px1, pz1, EAVE, ROOF, axis="x", overhang=1, gable_state=F(CB),
                 ridge_state=B.slab(GOLD), eave_trim=GOLD, end_overhang=1)
    # gable face: big rose window + the guild flag on a pole at the apex
    fg = K.Face(w, "z", px1, 1)
    ry = EAVE + 3
    S.rose_window(w, "z", -18, ry, px1, 2.2, F(SM), colors=("red", "yellow", "orange", "red", "yellow"),
                  spokes=6)
    apex_y = EAVE + 5
    for y in range(apex_y, apex_y + 2):
        pass
    top = None
    for y in range(EAVE + 12, EAVE, -1):
        if not w.is_empty(px1, y, -18):
            top = y
            break
    if top:
        TK.flagpole(w, px1 + 1, top - 1, -18, 4, "east", "guild", 3.0, 2.0)


def side_facades(w):
    """South gable (towards the west avenue) and north gable (towards street_n1), west back wall."""
    for (z, out) in ((Z1, 1), (Z0, -1)):
        f = K.Face(w, "x", z, out)
        for x0 in (X0 + 3, X0 + 9, X1 - 10, X1 - 4):
            if z == Z0 and x0 > -28:
                continue
            K.window(f, x0, 2, 2, 5, arch="round", depth=1, recess=0, sill=SM, keystone=F(GOLD), glass=TK.LEADED)
        for x0 in range(X0 + 1, X1 - 1, 3):
            if z == Z0 and x0 > -29:
                continue
            f.fill(x0, GY + 2, 0, x0 + 1, GY + 4, 0, TK.LEADED)
        if z == Z1:
            # the guild's big crest banners hang from the gable
            for x in (-42, -35):
                deco.kingdom_wall_banner(w, x, EAVE - 2, z + 1, facing="south", color="red", nbt=GUILD_BANNER)
                deco.kingdom_wall_banner(w, x, EAVE - 4, z + 1, facing="south", color="red", nbt=GUILD_BANNER) \
                    if False else None
            # side door to the west avenue
            K.doorway(f, -39, 1, 2, 4, wood="dark_oak", arch="round", depth=1, surround=F(SM))
            K.canopy(f, -40, -37, 5, mat=ROOF, d=1)
    fw = K.Face(w, "z", X0, -1)
    for z0 in range(Z0 + 2, Z1 - 1, 4):
        K.window(fw, z0, 3, 2, 3, arch="round", depth=1, recess=0, sill=SM, glass=TK.LEADED)
    for z0 in range(Z0 + 1, Z1 - 1, 3):
        fw.fill(z0, GY + 2, 0, z0 + 1, GY + 4, 0, TK.LEADED)


# ============================================================================== clock tower
def clock_tower(w):
    x0, x1, z0, z1 = TX0, TX1, TZ0, TZ1
    top_shaft = 34
    w.fill(x0, 0, z0, x1, 0, z1, F("stone_bricks"))
    w.fill(x0 + 1, 1, z0 + 1, x1 - 1, top_shaft + 8, z1 - 1, AIR)
    S.walls(w, x0, z0, x1, z1, 1, top_shaft, F(CB))
    S.walls(w, x0, z0, x1, z1, 1, 1, F("polished_andesite"))
    # corner pilasters (quoins) projecting one block, stopped below the clock stage
    for (cx, cz, sx, sz) in ((x0, z0, -1, -1), (x1, z0, 1, -1), (x0, z1, -1, 1), (x1, z1, 1, 1)):
        for y in range(1, top_shaft + 1):
            st = F(SM) if y % 2 else F(CB)
            w.set(cx + sx, y, cz, st)
            w.set(cx, y, cz + sz, st)
            w.set(cx + sx, y, cz + sz, st)
    # string courses
    for y in (GY, 24):
        S.walls(w, x0, z0, x1, z1, y, y, F(SM))
        S.corbel_rect(w, x0, z0, x1, z1, y, SM)
    # clock stage: smooth stone panel with a flush gilded ring, the dial block at the centre
    for (axis, c, out, fac) in (("z", x1, 1, "east"), ("x", z0, -1, "north"), ("x", z1, 1, "south")):
        f = K.Face(w, axis, c, out)
        uc = TCZ if axis == "z" else TCX
        for du in range(-3, 4):
            for dv in range(-4, 5):
                d = math.hypot(du, dv)
                st = F(SM) if d < 3.0 else (F(GOLD) if d < 3.7 else None)
                if st:
                    f.set(uc + du, CLOCK_Y + dv, 0, st)
        f.set(uc, CLOCK_Y, 0, B.S("clock_face", facing=fac))
        f.be(uc, CLOCK_Y, 0, "{Size:2.6f}")
        # lancet windows on the middle stage and a tall window on the base
        K.window(f, uc, 17, 1, 5, arch="pointed", depth=1, recess=0, sill=SM, glass=TK.LEADED)
        if fac != "north":
            K.window(f, uc - 1, 9, 3, 6, arch="round", depth=1, recess=0, sill=SM, keystone=F(GOLD), glass=TK.LEADED)
    # cornice + balustrade at the top of the shaft
    S.corbel_rect(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top_shaft, SM)
    w.fill(x0 - 1, top_shaft + 1, z0 - 1, x1 + 1, top_shaft + 1, z1 + 1, F(SM))
    S.rect_ring(w, x0 - 2, z0 - 2, x1 + 2, z1 + 2, top_shaft + 1, B.slab(GOLD, "top")) if False else None
    by = top_shaft + 2
    S.balustrade(w, x0 - 1, z0 - 1, x1 + 1, z0 - 1, by, post=F(SM), rail=B.wall(CB), post_every=2)
    S.balustrade(w, x0 - 1, z1 + 1, x1 + 1, z1 + 1, by, post=F(SM), rail=B.wall(CB), post_every=2)
    S.balustrade(w, x0 - 1, z0 - 1, x0 - 1, z1 + 1, by, post=F(SM), rail=B.wall(CB), post_every=2)
    S.balustrade(w, x1 + 1, z0 - 1, x1 + 1, z1 + 1, by, post=F(SM), rail=B.wall(CB), post_every=2)
    # belfry: corner piers with open round arches between them
    b0, b1 = by, by + 7
    for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        w.fill(cx, b0, cz, cx + (1 if cx == x0 else -1), b1, cz + (1 if cz == z0 else -1), F(CB))
    w.fill(x0, b1 + 1, z0, x1, b1 + 1, z1, F(SM))
    S.corbel_rect(w, x0, z0, x1, z1, b1 + 1, SM)
    w.fill(x0 + 2, b0, z0, x1 - 2, b1, z0, AIR)
    for (axis, c, out) in (("x", z0, -1), ("x", z1, 1), ("z", x0, -1), ("z", x1, 1)):
        f = K.Face(w, axis, c, out)
        uc = TCX if axis == "x" else TCZ
        f.fill(uc - 2, b0, 0, uc + 2, b1, 0, F(CB))
        m = S.round_arch_mask(5, 6)
        f.mask(uc - 2, b0, 0, m, AIR)
        f.fill(uc - 2, b0, 0, uc + 2, b0, 0, B.wall(CB))
    # the bell
    w.set(TCX, b1, TCZ, B.chain("y"))
    w.set(TCX, b1 - 1, TCZ, B.S("bell", attachment="ceiling", facing="north"))
    w.fill(x0 + 1, b0 - 1, z0 + 1, x1 - 1, b0 - 1, z1 - 1, F("spruce_planks"))
    # corner pinnacles on the belfry
    for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        S.pinnacle(w, cx, b1 + 2, cz, height=5)
    # spire: square spire with gilded ribs, then a flag
    tip = S.square_spire(w, TCX, TCZ, 4, b1 + 2, 22, ROOF, rib=B.stairs(GOLD, "north") if False else None,
                         finial=False)
    # small lucarnes (spire lights) on the spire faces
    for (dx, dz, fac) in ((0, -3, "north"), (0, 3, "south"), (3, 0, "east"), (-3, 0, "west")):
        y = b1 + 5
        w.set(TCX + dx, y, TCZ + dz, F(SM))
        w.set(TCX + dx, y + 1, TCZ + dz, B.S("gilded_finial"))
    w.set(TCX, tip, TCZ, F(GOLD))
    TK.flagpole(w, TCX, tip, TCZ, 3, "east", "pennant", 6.0, 1.5, pole="wall")
    # inside: spiral stair (stairs around the walls) from the floor up to the belfry floor
    _spiral(w, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, b0 - 1)
    # clock room: the movement (gears) behind the dials
    for y in (CLOCK_Y - 1, CLOCK_Y, CLOCK_Y + 1):
        pass
    w.set(TCX, CLOCK_Y, TCZ, B.S("chain", axis="x"))
    w.set(TCX - 1, CLOCK_Y, TCZ, B.chain("x")) if False else None
    # door to the hall and to the street_n1 side
    for (x, z) in ((x0, z0 + 4),):
        w.fill(x, 1, z, x, 2, z, AIR)
        lo, hi = B.door_states("dark_oak", "east", "left")
        w.set(x, 1, z, lo)
        w.set(x, 2, z, hi)
    fs = K.Face(w, "x", z0, -1)
    K.doorway(fs, TCX, 1, 1, 3, wood="dark_oak", arch="round", depth=1, surround=F(SM))
    w.set(TCX, 0, z0 - 1, B.stairs("stone_bricks", "south"))


def _spiral(w, x0, z0, x1, z1, y0, y1):
    """Stairs climbing clockwise around the inside walls of a square shaft (with a landing every turn)."""
    ring = []
    for x in range(x0, x1 + 1):
        ring.append((x, z0, "east"))
    for z in range(z0 + 1, z1 + 1):
        ring.append((x1, z, "south"))
    for x in range(x1 - 1, x0 - 1, -1):
        ring.append((x, z1, "west"))
    for z in range(z1 - 1, z0, -1):
        ring.append((x0, z, "north"))
    y = y0
    i = 0
    n = len(ring)
    while y <= y1:
        x, z, d = ring[i % n]
        corner = (x in (x0, x1)) and (z in (z0, z1))
        if corner:
            w.set(x, y - 1, z, F("spruce_planks")) if y > y0 else None
            w.fill(x, y, z, x, y + 3, z, AIR)
        else:
            w.set(x, y, z, B.stairs("spruce", d))
            w.fill(x, y + 1, z, x, y + 3, z, AIR)
            w.set(x, y - 1, z, B.stairs("spruce", {"east": "west", "west": "east", "north": "south",
                                                     "south": "north"}[d], "top")) if y > y0 else None
            y += 1
        i += 1
        if i > 400:
            break
    # lanterns in the shaft
    for yy in range(6, y1, 8):
        w.set((x0 + x1) // 2, yy, (z0 + z1) // 2, B.chain("y"))


# ============================================================================== interior
def interior(w):
    # floors: dark oak planks with a spruce border; stone hearth
    w.fill(X0 + 1, 0, Z0 + 1, X1 - 1, 0, Z1 - 1, F("dark_oak_planks"))
    for z in range(Z0 + 1, Z1):
        if z % 4 == 0:
            w.fill(X0 + 1, 0, z, X1 - 1, 0, z, F("spruce_planks"))
    # internal wall between the hall and the north wing (ground + upper floor)
    w.fill(X0 + 1, 1, NZ, X1 - 1, EAVE - 1, NZ, F(CB))
    w.fill(X0 + 1, GY, Z0 + 1, X1 - 1, GY, NZ - 1, F("spruce_planks"))     # north wing first floor
    # galleries at GY: north (z -29..-27), west (x -49..-46), south (z -11..-8)
    gal = F("spruce_planks")
    w.fill(X0 + 1, GY, NZ + 1, X1 - 1, GY, NZ + 3, gal)
    w.fill(X0 + 1, GY, NZ + 1, X0 + 4, GY, Z1 - 1, gal)
    w.fill(X0 + 1, GY, Z1 - 4, X1 - 1, GY, Z1 - 1, gal)
    # railings on the gallery edges + posts down to the floor (dark oak logs)
    for x in range(X0 + 5, X1):
        w.set(x, GY + 1, NZ + 3, B.fence("dark_oak"))
        w.set(x, GY + 1, Z1 - 4, B.fence("dark_oak"))
        w.set(x, GY - 1, NZ + 3, B.stairs("dark_oak", "north", "top")) if False else None
    for z in range(NZ + 3, Z1 - 3):
        w.set(X0 + 4, GY + 1, z, B.fence("dark_oak"))
    for (x, z) in ((X0 + 4, NZ + 3), (X0 + 4, Z1 - 4), (-38, NZ + 3), (-38, Z1 - 4), (X0 + 4, -18), (-31, NZ + 3),
                   (-31, Z1 - 4)):
        w.fill(x, 1, z, x, GY - 1, z, B.log("dark_oak"))
        w.set(x, GY + 1, z, B.fence("dark_oak"))
        w.set(x, GY + 2, z, B.lantern())
    # gallery soffit brackets
    # ceiling beams across the hall + chandeliers over the tables
    for z in range(NZ + 4, Z1 - 4, 3):
        w.fill(X0 + 5, EAVE - 1, z, X1 - 1, EAVE - 1, z, B.log("dark_oak", "x"))
    for (x, z) in ((-38, -23), (-38, -16), (-32, -20)):
        deco.chandelier(w, x, EAVE - 1, z, drop=4)
    # stair from the hall (NE corner) up to the north gallery
    for i in range(7):
        y = 1 + i
        z = -20 - i
        for x in (X1 - 2, X1 - 1):
            w.set(x, y, z, B.stairs("dark_oak", "north"))
            w.fill(x, 1, z, x, y - 1, z, F("dark_oak_planks"))
        w.set(X1 - 3, y + 1, z, B.fence("dark_oak"))
    w.fill(X1 - 2, GY, -27, X1 - 1, GY, -27, gal)
    w.fill(X1 - 2, GY + 1, -27, X1 - 1, GY + 1, -27, AIR)
    for z in range(-27, -20):
        w.fill(X1 - 2, GY, z, X1 - 1, GY + 3, z, AIR) if z > -27 else None
    # ---------------------------------------------------------------- the quest board + counter (Mina)
    board_x0, board_x1 = -46, -38
    for x in range(board_x0, board_x1 + 1):
        for y in range(2, 7):
            w.set(x, y, NZ + 1, F("spruce_planks") if (x in (board_x0, board_x1) or y in (2, 6))
                  else F("birch_planks"))
    w.fill(board_x0, 7, NZ + 1, board_x1, 7, NZ + 1, B.stairs("dark_oak", "south", "top"))
    papers = ["minecraft:paper", "minecraft:map", "minecraft:writable_book", "minecraft:paper",
              "minecraft:filled_map", "minecraft:paper"]
    for x in range(board_x0 + 1, board_x1):
        for y in range(3, 6):
            item = papers[int(hash2(x, y, 7) * len(papers))]
            if item == "minecraft:filled_map":
                item = "minecraft:map"
            deco.item_frame(w, x, y, NZ + 2, "south", item, rotation=int(hash2(y, x, 8) * 3) - 1 & 7)
    deco.sign_text(w, -42, 7, NZ + 2, B.S("dark_oak_wall_sign", facing="south"),
                   ["", "依頼掲示板", "Quest Board", ""], color="yellow", glowing=True)
    # counter in front of the board (z = -26), Mina stands between counter and board
    for x in range(board_x0, board_x1 + 1):
        w.set(x, 1, NZ + 4, F("dark_oak_planks") if x in (board_x0, board_x1) else B.S("barrel", facing="south"))
        w.set(x, 2, NZ + 4, B.slab("dark_oak", "bottom") if False else B.slab("spruce", "top"))
    w.set(-42, 1, NZ + 4, B.trapdoor("dark_oak", "north", "bottom", True))   # gap in the counter (gate)
    w.set(-42, 2, NZ + 4, AIR)
    for x in (-45, -40):
        w.set(x, 3, NZ + 4, B.candle(2, "white"))
    w.set(-43, 3, NZ + 4, B.lectern("south", True))
    w.set(-39, 3, NZ + 4, B.S("flower_pot")) if False else None
    w.spot("npc.quest_board", (-42, 1, NZ + 3), 0.0)
    # ---------------------------------------------------------------- registrar's desk (Marta) on the west side
    for z in range(-22, -13):
        w.set(X0 + 6, 1, z, F("dark_oak_planks") if z in (-22, -14) else B.S("chiseled_bookshelf", facing="east",
                                                                               slot_0_occupied=True,
                                                                               slot_2_occupied=True))
        w.set(X0 + 6, 2, z, B.slab("dark_oak", "top"))
    w.set(X0 + 6, 3, -20, B.lectern("east", True))
    w.be(X0 + 6, 3, -20, B.lectern_nbt("冒険者名簿", "Marta", ["Register of adventurers of the Guild of Arseria.",
                                                               "Name / Rank / Party / Notes"]))
    w.set(X0 + 6, 3, -16, B.candle(3, "white"))
    w.set(X0 + 6, 3, -18, B.S("decorated_pot"))
    deco.bookshelf_wall(w, X0 + 1, -23, X0 + 1, -13, 1, 4, seed=5, facing="east")
    deco.sign_text(w, X0 + 6, 4, -18, B.S("dark_oak_hanging_sign", attached=False, rotation=12) if False else
                   B.S("dark_oak_wall_hanging_sign", facing="north"), ["受付", "Registrar"], color="black",
                   glowing=False) if False else None
    w.spot("npc.guild_registrar", (X0 + 5, 1, -18), -90.0)
    deco.carpet_runner(w, X0 + 2, -22, X0 + 4, -14, 0, color="red", border="yellow")
    # ---------------------------------------------------------------- long tables
    for x in (-39, -34):
        deco.long_table(w, x, 0, -22, 10, axis="z", top="dark_oak", cloth=None, chairs=True, chair_wood="spruce",
                        candles=False)
        for z in range(-22, -12):
            w.set(x, 1, z, F("dark_oak_planks") if False else B.slab("dark_oak", "top"))
            if hash2(x, z, 3) < 0.35:
                w.set(x, 2, z, (B.S("flower_pot"), B.candle(2, "white"), B.S("decorated_pot"),
                                B.S("cake"))[int(hash2(z, x, 4) * 4)])
    # entrance runner from the portal to the counter
    deco.carpet_runner(w, -36, -19, X1 - 1, -17, 0, color="blue", border="yellow")
    # ---------------------------------------------------------------- great fireplace on the south wall
    fs = K.Face(w, "x", Z1, 1)
    K.fireplace(fs, -38, 1, w=3, h=4, d=-1, mat="stone_bricks", mantel=SM)
    w.fill(-39, 5, Z1 - 1, -37, GY - 1, Z1 - 1, F("stone_bricks"))
    deco.kingdom_wall_banner(w, -38, 6, Z1 - 2, facing="north", color="red", nbt=GUILD_BANNER)
    for x in (-41, -35):
        deco.armor_stand(w, x, 0, Z1 - 2, yaw=180.0, armor="iron", weapon="minecraft:iron_sword",
                         offhand=deco.blue_shield())
    # chimney through the south gable to above the roof
    w.fill(-38, GY, Z1, -38, EAVE + 14, Z1, F("stone_bricks")) if False else None
    # ---------------------------------------------------------------- north wing ground floor: kitchen + archive
    w.fill(-41, 1, Z0 + 1, -41, GY - 1, NZ - 1, F(CB))            # kitchen | archive
    # kitchen door from the hall, hatch beside the board
    lo, hi = B.door_states("spruce", "north", "left")
    w.set(-48, 1, NZ, lo)
    w.set(-48, 2, NZ, hi)
    kitchen(w)
    archive(w)
    # ---------------------------------------------------------------- north wing upper floor: infirmary + office
    w.fill(-41, GY + 1, Z0 + 1, -41, EAVE - 1, NZ - 1, F("white_plaster"))
    for (x, hinge) in ((-45, "left"), (-34, "right")):
        lo, hi = B.door_states("dark_oak", "north", hinge)
        w.set(x, GY + 1, NZ, lo)
        w.set(x, GY + 2, NZ, hi)
    infirmary(w)
    office(w)
    # ---------------------------------------------------------------- gallery furnishings
    for x in range(-44, -29, 4):
        deco.bench(w, x, GY, Z1 - 1, facing="north", length=2, wood="dark_oak")
    for z in range(-24, -12, 5):
        w.set(X0 + 1, GY + 1, z, B.S("barrel", facing="east"))
        deco.armor_stand(w, X0 + 2, GY, z + 2, yaw=-90.0, armor="chainmail", weapon="minecraft:bow")
    # trophies on the gallery walls
    for x in range(-46, -29, 3):
        w.set(x, GY + 3, Z1 - 1, B.S("skeleton_wall_skull", facing="north") if hash2(x, 1, 2) < 0.5 else
              B.S("zombie_wall_head", facing="north"))
    for z in range(-26, -10, 4):
        deco.kingdom_wall_banner(w, X0 + 1, EAVE - 2, z, facing="east", color="red", nbt=GUILD_BANNER)
    # light: morning sun through the east windows into the hall
    for z in (-27, -23, -12, -8):
        deco.light_shaft(w, X1 - 1, 5, z + 1, "west", length=16.0, width=2.0, height=4.0, pitch=34.0)
    # sconces between the windows
    for z in (-25, -15, -10):
        w.set(X1 - 1, 5, z, B.wall(CB)) if False else None


def kitchen(w):
    """Kitchen (x -49..-42, z -37..-31): sink, stoves, larder; Laszlo washes the dishes."""
    x0, x1, z0, z1 = X0 + 1, -42, Z0 + 1, NZ - 1
    w.fill(x0, 0, z0, x1, 0, z1, F("polished_andesite"))
    # counter with the sink along the north wall
    for x in range(x0, x1 + 1):
        w.set(x, 1, z0, B.S("smoker", facing="south", lit=True) if x == x0 + 1 else
              (B.S("water_cauldron", level=3) if x in (x0 + 4, x0 + 5) else F("spruce_planks")))
    w.set(x0 + 2, 1, z0, B.S("furnace", facing="south", lit=True))
    w.set(x0 + 3, 1, z0, B.S("barrel", facing="up"))
    for x in range(x0, x1 + 1):
        w.set(x, 2, z0, B.S("chain") if x in (x0 + 1, x0 + 2) else AIR) if False else None
        w.set(x, 3, z0, B.stairs("spruce", "south", "top"))
    w.set(x0 + 4, 2, z0, B.S("flower_pot")) if False else None
    # dirty dishes: stacked decorated pots / candles on the counter
    w.set(x0 + 6, 2, z0, B.S("decorated_pot"))
    w.set(x0 + 3, 2, z0, B.S("cake", bites=3))
    # hanging pots and a hearth along the west wall
    w.set(x0, 1, z0 + 3, B.S("campfire", lit=True, signal_fire=False))
    w.set(x0, 2, z0 + 3, B.S("air"))
    w.set(x0, 3, z0 + 3, B.S("hopper", facing="down"))
    w.fill(x0, 4, z0 + 3, x0, GY - 1, z0 + 3, F("bricks"))
    for z in (z0 + 2, z0 + 4):
        w.set(x0, 1, z, F("bricks"))
        w.set(x0, 2, z, F("bricks"))
        w.set(x0, 3, z, F("bricks"))
    # larder: barrels, hay, hanging hams (lanterns), crates
    for (x, z) in ((x1, z1), (x1 - 1, z1), (x1, z1 - 1)):
        w.set(x, 1, z, B.S("barrel", facing="up"))
    w.set(x1, 2, z1, B.S("hay_block", axis="y"))
    w.set(x0 + 4, 1, z1, B.S("crafting_table"))
    deco.table(w, x0 + 3, z0 + 3, x0 + 5, z0 + 3, 0, top="spruce")
    w.set(x0 + 3, 2, z0 + 3, B.S("melon")) if False else None
    w.set(x0 + 4, 2, z0 + 3, B.S("pumpkin")) if False else None
    w.set(x0 + 4, GY - 1, z0 + 3, B.lantern(hanging=True))
    w.spot("story.laszlo", (x0 + 4, 1, z0 + 1), 180.0)


def archive(w):
    """Archive / passage (x -40..-28, z -37..-31): records, maps, the door to the clock tower."""
    x0, x1, z0, z1 = -40, X1 - 1, Z0 + 1, NZ - 1
    deco.bookshelf_wall(w, x0, z0, x1 - 2, z0, 1, 5, seed=9, facing="south")
    w.fill(x0 + 2, 1, z0 + 3, x0 + 5, 1, z0 + 3, B.S("cartography_table"))
    w.set(x0 + 3, 2, z0 + 3, B.S("lantern"))
    lo, hi = B.door_states("dark_oak", "south", "left")
    w.set(-34, 1, NZ, lo)
    w.set(-34, 2, NZ, hi)
    w.set(x1 - 3, GY - 1, z0 + 3, B.lantern(hanging=True))


def infirmary(w):
    """Infirmary (x -49..-42, z -37..-31, first floor): beds with white linen, potions, Wolfram's desk."""
    x0, x1, z0, z1 = X0 + 1, -42, Z0 + 1, NZ - 1
    y = GY
    w.fill(x0, y, z0, x1, y, z1, F("birch_planks"))
    for x in (x0, x0 + 3, x0 + 6):
        if x <= x1:
            K.bed(w, x, y, z0, facing="north", color="white")
            w.set(x + 1, y + 1, z0, B.S("barrel", facing="up")) if x + 1 <= x1 else None
    w.set(x1, y + 1, z1, B.S("brewing_stand", has_bottle_0=True, has_bottle_1=True))
    w.set(x1 - 1, y + 1, z1, B.S("water_cauldron", level=2))
    w.set(x0, y + 1, z1, B.S("chest", facing="east"))
    w.set(x0 + 1, y + 1, z1, B.S("lectern", facing="south", has_book=True))
    w.be(x0 + 1, y + 1, z1, B.lectern_nbt("治療記録", "Wolfram", ["Patients of the week.",
                                                                  "Slime burns: 4. Wolf bites: 2."]))
    for x in (x0 + 2, x0 + 5):
        w.set(x, EAVE - 1, z0 + 3, B.lantern(hanging=True))
    for x in range(x0, x1 + 1):
        w.set(x, y + 1, z0 + 3, B.carpet("white")) if x % 2 == 0 else None
    w.spot("story.wolfram", (x0 + 3, y + 1, z1 - 1), 180.0)


def office(w):
    """Guildmaster's office (x -40..-28, z -37..-31, first floor): desk, maps, trophies; Gustav."""
    x0, x1, z0, z1 = -40, X1 - 1, Z0 + 1, NZ - 1
    y = GY
    w.fill(x0, y, z0, x1, y, z1, F("dark_oak_planks"))
    K.rug(w, x0 + 2, z0 + 1, x1 - 2, z1 - 1, y, field="red", border="yellow")
    # big desk
    for x in range(-36, -31):
        w.set(x, y + 1, z0 + 2, F("dark_oak_planks") if x in (-36, -32) else B.S("barrel", facing="south"))
        w.set(x, y + 2, z0 + 2, B.slab("dark_oak", "top"))
    w.set(-34, y + 1, z0 + 1, B.stairs("dark_oak", "north"))      # his chair
    w.set(-35, y + 3, z0 + 2, B.lectern("south", True))
    w.be(-35, y + 3, z0 + 2, B.lectern_nbt("ギルド日誌", "Gustav", ["Rank exams next week.",
                                                                  "Keep an eye on the new ones."]))
    w.set(-33, y + 3, z0 + 2, B.candle(3, "white"))
    deco.bookshelf_wall(w, x0, z0, x0, z1, y + 1, y + 4, seed=13, facing="east")
    deco.item_frame(w, -34, y + 4, z0, "south", "minecraft:map")
    for (x, item) in ((-37, "minecraft:diamond_sword"), (-31, "minecraft:netherite_axe")):
        deco.item_frame(w, x, y + 3, z0, "south", item, rotation=1)
    deco.armor_stand(w, x1 - 1, y, z1 - 1, yaw=180.0, armor="diamond", weapon="minecraft:diamond_sword",
                     offhand=deco.blue_shield())
    w.set(x1 - 1, y + 1, z0, B.S("dragon_wall_head", facing="south")) if False else None
    deco.chandelier(w, -34, EAVE, -34, drop=2)
    w.spot("story.gustav", (-34, y + 1, z0 + 3), 0.0)


# ============================================================================== forecourt & grounds
def grounds(w):
    # pave the district outside the building
    TK.pave(w, -51, -39, -6, -6, TK.SQUARE_MIX, seed=71)
    # north-east garden by the avenue (x -18..-7, z -39..-27): planters, a well, benches
    for (x, z, k) in ((-14, -36, "oak"), (-9, -30, "birch")):
        K.planter_tree(w, x, 0, z, kind=k, seed=x * 3 + z, r=1)
    deco.bench(w, -16, 0, -29, facing="south", length=3)
    w.set(-10, 1, -36, B.S("water_cauldron", level=3))
    # guild banner masts flanking the forecourt with guild flags
    for (x, z) in ((-24, -25), (-24, -12)):
        K.mast(w, x, 0, z, height=9, sides=("north", "south"), nbt=GUILD_BANNER)
        for s in ("north", "south"):
            vx, _, vz = {"north": (0, 0, -1), "south": (0, 0, 1)}[s]
            w.set(x + vx, 7 + 1, z + vz, B.S("red_wall_banner", facing=s))
            w.be(x + vx, 7 + 1, z + vz, GUILD_BANNER)
        TK.flag(w, x, 10, z, "east", "guild", 3.0, 2.0)
    for (x, z) in ((-26, -11), (-24, -28)):
        K.lamp(w, x, 0, z, height=4)


def build(w, district, terrain):
    grounds(w)
    shell(w)
    interior(w)
    east_facade(w)
    side_facades(w)
    roof(w)
    clock_tower(w)
