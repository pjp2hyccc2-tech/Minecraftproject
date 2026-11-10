"""大聖堂 The cathedral of Arseria, north-east of the plaza at the foot of the castle cliff.

Gothic church oriented east (altar) - west (twin-towered front with the great rose window, facing the castle
viaduct). Nave x 31..79 (walls z -72/-60, vault apex y 21) with aisles (outer walls z -77/-55), transept
x 58..68 reaching z -84..-49 (south rose window towards street_n1), chancel and a polygonal apse with tall
stained-glass windows, flying buttresses with pinnacles, gargoyles, a crossing flèche with the cathedral flag,
west towers with octagonal spires. Inside: pews, a red runner, chandeliers, the altar on a dais, pulpit, font;
light shafts at the west rose (afternoon sun) and the apse windows (morning sun). Respawn point in the nave.
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

DISTRICT = "cathedral"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF, CARVED = K.CB, K.SM, K.GOLD, K.ROOF, K.CARVED

WX = 30                     # west front wall
EX = 80                     # east end of the chancel (apse beyond)
CX0, CX1 = 58, 68           # transept walls (x)
NZ0, NZ1 = -72, -60         # nave walls (z)
AZ0, AZ1 = -77, -55         # aisle outer walls
TZ0, TZ1 = -84, -49         # transept end walls
MZ = -66                    # nave axis
AISLE_H = 11
NAVE_H = 22
VAULT_SPRING, VAULT_TOP = 14, 21

GLASS = ("blue", "light_blue", "yellow", "red", "purple", "cyan", "magenta")

VIEWS = {
    "cathedral_nave": dict(pos=(33.5, 6.5, -66.5), look=(80, 8, -66), fov=72, sun=(30, -95), fog=900,
                           label="Cathedral nave towards the altar (afternoon light through the west rose)"),
    "cathedral_west": dict(pos=(13, 6, -58), look=(34, 28, -66), fov=78, sun=(40, -150), fog=2600,
                           label="The cathedral's west front"),
    "cathedral_se": dict(pos=(92, 14, -32), look=(60, 24, -66), fov=70, sun=(40, -150), fog=2600,
                         label="The cathedral from the south-east: transept rose, buttresses, apse"),
}


def mixed_stone(w, x, y, z):
    u = hash3(x, y, z, 1201)
    w.set(x, y, z, F(CB) if u < 0.8 else (F(SM) if u < 0.92 else B.S("calcite")))


def wall_box(w, x0, z0, x1, z1, y0, y1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y1 + 1):
                mixed_stone(w, x, y, z)


# ============================================================================== body
def body(w):
    # foundations and floor
    w.fill(WX - 1, -1, AZ0 - 1, EX + 8, -1, AZ1 + 1, B.S("stone"))
    w.fill(CX0, -1, TZ0, CX1, -1, TZ1, B.S("stone"))
    w.fill(WX, 0, AZ0, EX + 7, 0, AZ1, F("polished_diorite"))
    w.fill(CX0, 0, TZ0, CX1, 0, TZ1, F("polished_diorite"))
    X, Z = np.meshgrid(np.arange(WX, EX + 7), np.arange(AZ0, AZ1 + 1), indexing="ij")
    chk = ((X + Z) % 2 == 0)
    w.set_many(X[chk], np.zeros(int(chk.sum()), int), Z[chk], F("plaza_flagstones"))
    # aisle outer walls, nave clerestory walls, west and east walls
    wall_box(w, WX, AZ0, EX, AZ0, 1, AISLE_H)
    wall_box(w, WX, AZ1, EX, AZ1, 1, AISLE_H)
    wall_box(w, WX, NZ0, EX, NZ0, AISLE_H + 1, NAVE_H)
    wall_box(w, WX, NZ1, EX, NZ1, AISLE_H + 1, NAVE_H)
    wall_box(w, WX, AZ0, WX, AZ1, 1, NAVE_H)
    # transept walls
    wall_box(w, CX0, TZ0, CX0, TZ1, 1, NAVE_H)
    wall_box(w, CX1, TZ0, CX1, TZ1, 1, NAVE_H)
    wall_box(w, CX0, TZ0, CX1, TZ0, 1, NAVE_H)
    wall_box(w, CX0, TZ1, CX1, TZ1, 1, NAVE_H)
    # clear interiors
    w.fill(WX + 1, 1, AZ0 + 1, EX, NAVE_H + 12, AZ1 - 1, AIR)
    w.fill(CX0 + 1, 1, TZ0 + 1, CX1 - 1, NAVE_H + 12, TZ1 - 1, AIR)
    # arcade: piers (castle pillars) every 4 with pointed arches between, along both nave walls
    for zc in (NZ0, NZ1):
        w.fill(WX + 1, 1, zc, EX, AISLE_H, zc, F(CB))
        for x in range(WX + 4, EX + 1, 4):
            if CX0 <= x <= CX1:
                continue
            w.fill(x, 1, zc, x, AISLE_H + 1, zc, B.pillar("y"))
        f = K.Face(w, "x", zc, 1)
        for x in range(WX + 5, EX - 2, 4):
            if CX0 - 3 <= x <= CX1:
                continue
            f.mask(x, 1, 0, S.pointed_arch_mask(3, 9, 1.1), AIR)
        # crossing: open the nave walls into the transept arms
        w.fill(CX0 + 1, 1, zc, CX1 - 1, NAVE_H, zc, AIR)
    # aisle ceilings (flat stone at AISLE_H) and the clerestory wall top course
    for (z0, z1) in ((AZ0 + 1, NZ0 - 1), (NZ1 + 1, AZ1 - 1)):
        w.fill(WX + 1, AISLE_H + 1, z0, EX - 1, AISLE_H + 1, z1, F(CB))
    # nave + chancel pointed vault (from VAULT_SPRING to VAULT_TOP) with transverse ribs
    half = (NZ1 - NZ0 - 1) / 2.0
    for z in range(NZ0 + 1, NZ1):
        dz = abs(z - MZ)
        prof = VAULT_SPRING + (VAULT_TOP - VAULT_SPRING) * (1 - (dz / (half + 0.5)) ** 1.6)
        y0 = int(math.floor(prof)) + 1
        w.fill(WX + 1, y0, z, EX, NAVE_H, z, F(CB))
        for x in range(WX + 4, EX + 1, 4):
            w.set(x, y0 - 1, z, F(SM))
    for x in range(CX0 + 1, CX1):                                       # transept vault (along z)
        dx = abs(x - (CX0 + CX1) / 2.0)
        prof = VAULT_SPRING + (VAULT_TOP - VAULT_SPRING) * (1 - (dx / 5.5) ** 1.6)
        y0 = int(math.floor(prof)) + 1
        w.fill(x, y0, TZ0 + 1, x, NAVE_H, TZ1 - 1, F(CB))
    w.fill(CX0 + 1, VAULT_TOP - 1, NZ0 + 1, CX1 - 1, NAVE_H, NZ1 - 1, AIR)   # crossing lantern void
    w.fill(CX0 + 1, NAVE_H, NZ0 + 1, CX1 - 1, NAVE_H, NZ1 - 1, F(CB))
    # string courses outside
    for (x0, z0, x1, z1) in ((WX, AZ0, EX, AZ0), (WX, AZ1, EX, AZ1)):
        w.fill(x0, AISLE_H + 1, z0, x1, AISLE_H + 1, z1, F(SM))
    S.rect_ring(w, WX, NZ0, EX, NZ1, NAVE_H, F(SM))


def roofs(w):
    # aisle lean-to roofs (slope up towards the nave wall)
    for (zw, zn, fac) in ((AZ0, NZ0, "south"), (AZ1, NZ1, "north")):
        sgn = 1 if zn > zw else -1
        n = abs(zn - zw)
        for i in range(0, n):
            z = zw - sgn + i * sgn
            y = AISLE_H + 2 + i // 2 + (0 if i % 2 == 0 else 0)
            st = B.stairs(ROOF, fac) if i % 2 == 0 else B.slab(ROOF, "top")
            w.fill(WX, y, z, EX, y, z, B.stairs(ROOF, fac))
            w.fill(WX, AISLE_H + 2, z, EX, y - 1, z, F(ROOF)) if y - 1 >= AISLE_H + 2 else None
        w.fill(WX, AISLE_H + 1, zw - sgn, EX, AISLE_H + 1, zw - sgn, B.stairs(GOLD, K.OPP[fac], "top"))
    # nave + chancel gable roof (ridge along x), transept roof (ridge along z)
    r = K.Roof(w, WX, NZ0, EX, NZ1, NAVE_H + 1, ROOF, axis="x", overhang=1, gable=F(CB), trim=GOLD, end_overhang=0)
    rt = K.Roof(w, CX0, TZ0, CX1, TZ1, NAVE_H + 1, ROOF, axis="z", overhang=1, gable=F(CB), trim=GOLD,
                end_overhang=1)
    return r, rt


# ============================================================================== apse
def apse(w):
    cx, cz, R = EX, MZ, 6.5
    for dx in range(0, 9):
        for dz in range(-8, 9):
            d = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            if d <= R + 0.4:
                w.set(x, -1, z, B.S("stone"))
                w.set(x, 0, z, F("polished_diorite"))
                if d > R - 0.6:
                    for y in range(1, NAVE_H + 1):
                        mixed_stone(w, x, y, z)
                else:
                    w.fill(x, 1, z, x, NAVE_H, z, AIR)
    # half-dome vault and a half-cone roof
    for dx in range(0, 9):
        for dz in range(-8, 9):
            d = math.hypot(dx, dz)
            if d <= R - 0.6:
                y0 = int(VAULT_SPRING + (VAULT_TOP - VAULT_SPRING) * (1 - (d / R) ** 1.6)) + 1
                w.fill(cx + dx, y0, cz + dz, cx + dx, NAVE_H, cz + dz, F(CB))
    for i in range(0, 9):
        rr = R + 1.0 - i * 0.85
        if rr < 0.5:
            break
        y = NAVE_H + 1 + i
        for dx in range(0, 9):
            for dz in range(-8, 9):
                d = math.hypot(dx, dz)
                if d <= rr + 0.3 and (dx > 0 or True):
                    w.set(cx + dx, y, cz + dz, F(ROOF) if d < rr - 0.7 else B.stairs(ROOF, _inward(dx, dz)))
    # tall stained windows in the apse (5 lancets) with mullions
    for k, ang in enumerate((-60, -30, 0, 30, 60)):
        a = math.radians(ang)
        x = cx + int(round(R * math.cos(a)))
        z = cz + int(round(R * math.sin(a)))
        for y in range(3, 19):
            for dd in (0,):
                w.set(x, y, z, B.pane(GLASS[(y // 3 + k) % len(GLASS)]))
        w.set(x, 19, z, F(SM))
        w.set(x, 2, z, F(SM))
        if ang == 0:
            for y in (5, 10, 15):
                deco.light_shaft(w, x - 2, y, z, "west", length=18.0, width=2.0, height=4.0, pitch=26.0)
    # buttresses between the apse windows
    for ang in (-45, -15, 15, 45, -75, 75):
        a = math.radians(ang)
        for rr in (R + 1, R + 2):
            x = cx + int(round(rr * math.cos(a)))
            z = cz + int(round(rr * math.sin(a)))
            top = 14 if rr == R + 1 else 10
            w.fill(x, 1, z, x, top, z, F(CB))
            w.set(x, top + 1, z, B.stairs(SM, _inward(x - cx, z - cz)))
        x = cx + int(round((R + 1) * math.cos(a)))
        z = cz + int(round((R + 1) * math.sin(a)))
        S.pinnacle(w, x, 15, z, height=5)


def _inward(dx, dz):
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


# ============================================================================== windows
def windows(w):
    # aisle lancets (stained) between the buttresses
    for (zc, out) in ((AZ0, -1), (AZ1, 1)):
        f = K.Face(w, "x", zc, out)
        for i, x in enumerate(range(WX + 10, EX - 1, 4)):
            if CX0 - 2 <= x <= CX1 + 1:
                continue
            K.stained_lancet(f, x, 3, 2, 7, GLASS, depth=1, recess=0, sharp=1.2, seed=i, pattern="bands")
    # clerestory windows above the aisle roofs
    for (zc, out) in ((NZ0, -1), (NZ1, 1)):
        f = K.Face(w, "x", zc, out)
        for i, x in enumerate(range(WX + 10, EX - 1, 4)):
            if CX0 - 1 <= x <= CX1:
                continue
            K.stained_lancet(f, x, 16, 2, 5, GLASS, depth=1, recess=0, sharp=1.2, seed=i + 20, pattern="medallion")
    # transept ends: south (portal + rose), north (rose + tall lancets)
    for (zc, out, portal) in ((TZ1, 1, True), (TZ0, -1, False)):
        f = K.Face(w, "x", zc, out)
        cxm = (CX0 + CX1) // 2
        S.rose_window(w, "x", cxm, 16, zc, 4.2, F(SM), colors=("blue", "red", "yellow", "purple", "light_blue"),
                      spokes=8)
        if portal:
            K.doorway(f, cxm - 1, 1, 3, 7, wood="dark_oak", arch="pointed", depth=1, surround=F(SM))
            for x in (cxm - 3, cxm + 3):
                K.stained_lancet(f, x, 3, 1, 7, GLASS, depth=1, recess=0, sharp=1.3, seed=x)
            w.set(cxm, 9, zc + 1, B.S("gilded_finial")) if False else None
            deco.kingdom_wall_banner(w, cxm - 3, 11, zc + 1, facing="south", color="white",
                                     nbt=B.banner_nbt([("cr", "yellow"), ("mc", "yellow"), ("bo", "yellow")]))
            deco.kingdom_wall_banner(w, cxm + 3, 11, zc + 1, facing="south", color="white",
                                     nbt=B.banner_nbt([("cr", "yellow"), ("mc", "yellow"), ("bo", "yellow")]))
        else:
            for x in (cxm - 3, cxm, cxm + 3):
                K.stained_lancet(f, x if x != cxm else cxm, 3, 1, 8, GLASS, depth=1, recess=0, sharp=1.3, seed=x)
    # transept side windows (east/west faces of the arms): tall lancets, east ones catch the morning sun
    for (xc, out) in ((CX0, -1), (CX1, 1)):
        f = K.Face(w, "z", xc, out)
        for z in (TZ0 + 4, TZ1 - 5):
            K.stained_lancet(f, z, 4, 2, 12, GLASS, depth=1, recess=0, sharp=1.25, seed=z + xc, pattern="figure")
            if out > 0:
                deco.light_shaft(w, xc - 1, 10, z, "west", length=12.0, width=2.0, height=4.0, pitch=30.0)
            else:
                deco.light_shaft(w, xc + 1, 10, z, "east", length=12.0, width=2.0, height=4.0, pitch=30.0)


# ============================================================================== west front & towers
def west_front(w):
    f = K.Face(w, "z", WX, -1)
    # great portal: pointed, 5 wide, 10 tall, with stepped orders (recessed jambs)
    for order, (wd, ht) in enumerate(((9, 13), (7, 12), (5, 10))):
        z0 = MZ - wd // 2
        m = S.pointed_arch_mask(wd, ht, 1.15)
        if order < 2:
            ring = K.ring_of(m)
            f.mask(z0 - 1, 0, -order, ring, F(SM) if order == 0 else F(CARVED))
    m = S.pointed_arch_mask(5, 10, 1.15)
    f.mask(MZ - 2, 1, 0, m, AIR)
    f.mask(MZ - 2, 1, -1, m, AIR)
    for i, hinge in ((0, "right"), (1, "left")):
        pass
    for zz, hinge in ((MZ - 1, "left"), (MZ + 1, "right")):
        lo, hi = B.door_states("dark_oak", "east", hinge)
        w.set(WX - 1 + 1, 1, zz, lo)
        w.set(WX, 2, zz, hi)
    w.set(WX, 1, MZ, B.door_states("dark_oak", "east", "left")[0])
    w.set(WX, 2, MZ, B.door_states("dark_oak", "east", "left")[1])
    for z in range(MZ - 2, MZ + 3):
        for y in range(3, 11):
            if w.get(WX, y, z).endswith(":air") and abs(z - MZ) <= 2:
                w.set(WX, y, z, B.pane("yellow") if (y + z) % 3 == 0 else B.pane("orange"))
    # trumeau statue and gilded tympanum crest
    w.set(WX - 1, 12, MZ, B.S("castle_crest_stone"))
    # steps
    for z in range(MZ - 5, MZ + 6):
        w.set(WX - 1, 0, z, B.stairs("smooth_castle_stone", "east"))
    # great rose window
    S.rose_window(w, "z", MZ, 21, WX, 5.2, F(SM), colors=("blue", "red", "yellow", "purple", "light_blue"),
                  spokes=12)
    for dy in (-3, 0, 3):
        deco.light_shaft(w, WX + 1, 21 + dy, MZ, "east", length=26.0, width=3.0, height=3.0, pitch=22.0)
    # gallery of kings (niches with statues) above the portal
    for z in range(MZ - 4, MZ + 5, 2):
        w.set(WX, 15, z, AIR)
        w.set(WX - 1, 14, z, B.slab(SM, "top"))
        w.set(WX, 14, z, F(CARVED))
    # gable over the west front up to the roof ridge, with a statue niche and a gilded cross
    for y in range(NAVE_H + 1, NAVE_H + 10):
        k = y - NAVE_H
        for z in range(NZ0 + k - 1, NZ1 - k + 2):
            mixed_stone(w, WX, y, z)
    w.set(WX - 1, NAVE_H + 4, MZ, F(CARVED))
    w.set(WX - 1, NAVE_H + 5, MZ, B.S("quartz_pillar", axis="y"))
    w.set(WX - 1, NAVE_H + 6, MZ, B.S("chiseled_quartz_block"))
    w.fill(WX, NAVE_H + 10, MZ, WX, NAVE_H + 12, MZ, F(GOLD))
    w.set(WX, NAVE_H + 11, MZ - 1, F(GOLD))
    w.set(WX, NAVE_H + 11, MZ + 1, F(GOLD))
    # twin west towers
    for (z0, z1) in ((AZ0 - 2, NZ0), (NZ1, AZ1 + 2)):
        tower(w, WX - 1, z0, WX + 7, z1)


def tower(w, x0, z0, x1, z1):
    top = 40
    cxx, czz = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(1, top + 1):
                if edge:
                    mixed_stone(w, x, y, z)
                else:
                    w.set(x, y, z, AIR)
    w.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, F("polished_diorite"))
    # corner buttresses stepping back, pinnacles at the top
    for (cx, cz, sx, sz) in ((x0, z0, -1, -1), (x1, z0, 1, -1), (x0, z1, -1, 1), (x1, z1, 1, 1)):
        for y in range(1, 33):
            st = F(CB) if y % 3 else F(SM)
            w.set(cx + sx, y, cz, st)
            w.set(cx, y, cz + sz, st)
            if y < 20:
                w.set(cx + sx, y, cz + sz, st)
        S.pinnacle3(w, cx, top + 1, cz, height=8, material=CB, roof=ROOF)
    # lancet windows on every face, belfry openings, string courses
    for (axis, c, out, a0, a1) in (("x", z0, -1, x0, x1), ("x", z1, 1, x0, x1), ("z", x0, -1, z0, z1),
                                   ("z", x1, 1, z0, z1)):
        f = K.Face(w, axis, c, out)
        mid = (a0 + a1) // 2
        K.stained_lancet(f, mid, 14, 1, 7, GLASS, depth=1, recess=0, sharp=1.3, seed=mid + c)
        f.mask(mid - 1, 30, 0, S.pointed_arch_mask(3, 8, 1.2), AIR)
        f.fill(mid - 1, 30, 0, mid + 1, 30, 0, B.wall(CB))
        for u in range(a0, a1 + 1):
            f.set(u, 26, 1, B.stairs(SM, f.in_dir, "top"))
            f.set(u, top, 1, B.stairs(SM, f.in_dir, "top"))
        K.gargoyle(f, a0 + 1, 38, d=1) if False else None
    w.set(cxx, 37, czz, B.S("bell", attachment="ceiling", facing="north"))
    w.set(cxx, 38, czz, B.chain("y"))
    w.fill(x0 + 1, 29, z0 + 1, x1 - 1, 29, z1 - 1, F("spruce_planks"))
    w.fill(x0, top + 1, z0, x1, top + 1, z1, F(SM))
    S.crenellate(w, x0, z0, x1, z1, top + 2, B.wall(CB))
    # octagonal spire with gilded ribs
    for i in range(0, 26):
        r = 3.6 * (1 - i / 26.0) + 0.2
        y = top + 2 + i
        dx, dz, d = S._disk_mask_xz(cxx, czz, r)
        m = d <= r + 0.35
        st = F(ROOF)
        w.set_many(cxx + dx[m], np.full(int(m.sum()), y), czz + dz[m], st)
        if i % 5 == 4:
            w.set_many(cxx + dx[m & (d > r - 0.7)], np.full(int((m & (d > r - 0.7)).sum()), y),
                       czz + dz[m & (d > r - 0.7)], F(GOLD))
    tip = top + 28
    w.fill(cxx, tip - 1, czz, cxx, tip, czz, F(GOLD))
    TK.flagpole(w, cxx, tip, czz, 3, "east", "cathedral", 3.0, 2.0, pole="fence")
    # gargoyles at the corners under the belfry cornice
    for (cx, cz, fac) in ((x0, z0, "west"), (x0, z1, "west"), (x1, z0, "east"), (x1, z1, "east")):
        vx = -1 if fac == "west" else 1
        w.set(cx + vx * 2, 26, cz, B.stairs("stone_bricks", "east" if vx > 0 else "west", "top"))
        w.set(cx + vx * 2, 27, cz, B.wall("stone_bricks"))


# ============================================================================== buttresses, pinnacles, flèche
def buttresses(w):
    xs = [x for x in range(WX + 8, EX + 1, 8) if not (CX0 - 2 <= x <= CX1 + 2)]
    for x in xs:
        for (zw, zp, zn) in ((AZ0, AZ0 - 4, NZ0), (AZ1, AZ1 + 4, NZ1)):
            sgn = 1 if zp > zw else -1
            # pier
            w.fill(x, 0, zp, x, 0, zw + sgn, F("stone_bricks"))
            for z in range(zw + sgn, zp + sgn, sgn):
                k = abs(z - zw)
                top = 15 - (k - 1) * 2 if k > 1 else 15
                w.fill(x, 1, z, x, top, z, F(CB))
                w.set(x, top + 1, z, B.stairs(SM, "north" if sgn > 0 else "south"))
            S.pinnacle(w, x, 16, zw + sgn, height=6)
            # flyer from the pier top to the clerestory
            K.flying_buttress(w, "x", x, zw + sgn, zn, 15, NAVE_H - 2, mat=CB, cap=SM, pinnacle_h=0)
        # gargoyles on the aisle cornice next to each buttress
        for (zc, out) in ((AZ0, -1), (AZ1, 1)):
            f = K.Face(w, "x", zc, out)
            K.gargoyle(f, x + 2, AISLE_H, d=1)
    # pinnacles along the nave roof edge and the transept corners
    for x in range(WX + 4, EX, 8):
        for z in (NZ0, NZ1):
            if not (CX0 <= x <= CX1):
                S.pinnacle(w, x, NAVE_H + 1, z, height=4)
    for (x, z) in ((CX0, TZ0), (CX1, TZ0), (CX0, TZ1), (CX1, TZ1)):
        S.pinnacle3(w, x, NAVE_H + 1, z, height=10, material=CB, roof=ROOF)
        w.fill(x, 1, z, x, NAVE_H, z, F(SM))


def fleche(w, ridge):
    cx, cz = (CX0 + CX1) // 2, MZ
    y0 = ridge - 1
    S.octagon_prism(w, cx, cz, 3, y0, y0 + 6, F(CB), hollow=True)
    for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        w.fill(cx + dx, y0 + 2, cz + dz, cx + dx, y0 + 4, cz + dz, AIR)
    w.set(cx, y0 + 4, cz, B.S("bell", attachment="ceiling", facing="north")) if False else None
    S.octagon_prism(w, cx, cz, 3, y0 + 7, y0 + 7, F(SM))
    for i in range(0, 18):
        r = 2.8 * (1 - i / 18.0)
        dx, dz, d = S._disk_mask_xz(cx, cz, r)
        m = d <= r + 0.35
        w.set_many(cx + dx[m], np.full(int(m.sum()), y0 + 8 + i), cz + dz[m], F(ROOF) if i % 4 else F(GOLD))
    tip = y0 + 26
    w.fill(cx, tip - 1, cz, cx, tip, cz, F(GOLD))
    TK.flagpole(w, cx, tip, cz, 3, "east", "cathedral", 4.0, 2.5, pole="fence")


# ============================================================================== interior
def interior(w):
    # red runner, pews facing east, chandeliers, font, pulpit, altar on a dais
    deco.carpet_runner(w, WX + 2, MZ - 1, EX - 4, MZ + 1, 0, color="red", border="yellow")
    for x in range(WX + 5, CX0 - 1, 2):
        for z in list(range(NZ0 + 1, MZ - 2)) + list(range(MZ + 3, NZ1)):
            w.set(x, 1, z, B.stairs("dark_oak", "west"))
    for x in range(CX1 + 2, EX - 6, 2):
        for z in list(range(NZ0 + 2, MZ - 2)) + list(range(MZ + 3, NZ1 - 1)):
            w.set(x, 1, z, B.stairs("dark_oak", "west"))
    for x in range(WX + 6, EX, 8):
        deco.chandelier(w, x, VAULT_TOP + 1, MZ, drop=6)
    deco.chandelier(w, (CX0 + CX1) // 2, NAVE_H, MZ, drop=8)
    # font near the entrance
    w.set(WX + 3, 1, MZ - 4, F(CARVED))
    w.set(WX + 3, 2, MZ - 4, B.S("water_cauldron", level=3))
    # pulpit at the crossing
    w.fill(CX0 + 1, 1, NZ1 - 2, CX0 + 2, 2, NZ1 - 2, F(SM))
    w.set(CX0 + 1, 3, NZ1 - 2, B.wall(CB))
    w.set(CX0 + 2, 3, NZ1 - 2, B.lectern("north", True))
    w.be(CX0 + 2, 3, NZ1 - 2, B.lectern_nbt("聖典", "Cathedral of Arseria", ["Light of the stars, guide us."]))
    # dais and altar in the chancel
    for i, x in enumerate(range(EX - 6, EX + 1)):
        for z in range(NZ0 + 1, NZ1):
            if i == 0:
                w.set(x, 1, z, B.stairs("smooth_castle_stone", "east"))
            else:
                w.set(x, 1, z, F(SM))
    w.fill(EX - 3, 1, MZ - 1, EX - 4, 1, MZ + 1, F(SM)) if False else None
    w.fill(EX - 2, 2, MZ - 2, EX - 2, 2, MZ + 2, F(GOLD))
    w.fill(EX - 2, 3, MZ - 2, EX - 2, 3, MZ + 2, B.carpet("white"))
    for z in (MZ - 2, MZ + 2):
        w.set(EX - 2, 3, z, B.candle(4, "white"))
    w.set(EX - 2, 3, MZ, B.S("gilded_finial"))
    w.fill(EX + 1, 2, MZ, EX + 1, 7, MZ, F(GOLD))
    w.fill(EX + 1, 5, MZ - 1, EX + 1, 5, MZ + 1, F(GOLD))
    for z in (MZ - 4, MZ + 4):
        w.set(EX - 1, 2, z, B.S("candle", candles=4, lit=True)) if False else None
        w.fill(EX - 1, 2, z, EX - 1, 3, z, B.S("quartz_pillar", axis="y"))
        w.set(EX - 1, 4, z, B.candle(3, "white"))
    # statues of saints along the aisles (armour stands would be too martial: quartz figures)
    for x in (WX + 8, WX + 20):
        for (z, fac) in ((AZ0 + 1, "south"), (AZ1 - 1, "north")):
            S.statue_figure(w, x, 1, z, facing=fac, material="quartz_block", wings=False, gold_crown=True,
                            plinth="smooth_castle_stone")
    for x in range(WX + 4, EX, 4):
        for z in (AZ0 + 1, AZ1 - 1):
            if w.is_empty(x, 6, z):
                w.set(x, 6, z, B.wall(CB))
                w.set(x, 5, z, B.lantern(hanging=True))
    w.spot("respawn", (WX + 3, 1, MZ), -90.0)
    TK.crowd(w, "cathedral", [(WX + 3, 1, MZ - 2), (CX0 + 3, 1, MZ + 4), (CX0 + 3, 1, TZ1 - 5),
                              (CX0 + 3, 1, TZ0 + 5)], need=20)


# ============================================================================== close (grounds)
def grounds(w):
    TK.pave(w, 12, -86, 98, -47, TK.SQUARE_MIX, seed=1301)
    # parvis lamps and cathedral flags
    for z in (MZ - 9, MZ + 9):
        K.lamp(w, WX - 7, 0, z, height=4)
        TK.flagpole(w, WX - 12, 0, z, 9, "east", "cathedral", 3.0, 2.0, pole="wall")
        w.set(WX - 12, 1, z, F(SM))
    # garden behind the apse and along the north side: lawn, cypresses, flower beds
    w.fill(EX + 9, 0, -84, 97, 0, -50, B.S("grass_block"))
    w.fill(32, 0, -86, 56, 0, -80, B.S("grass_block"))
    for (x, z) in ((90, -80), (94, -70), (90, -60), (94, -52), (36, -83), (44, -83), (52, -83)):
        deco.tree_cypress(w, x, 0, z, seed=x + z)
    for (x0, z0) in ((88, -74), (88, -66)):
        deco.flower_bed(w, x0, z0, x0 + 6, z0 + 3, 0, seed=x0 + z0, border="smooth_castle_stone")
    TK.guard(w, "cathedral", 1, WX - 2, 1, MZ - 4, 90.0)
    TK.guard(w, "cathedral", 2, WX - 2, 1, MZ + 4, 90.0)
    TK.guard(w, "cathedral", 3, (CX0 + CX1) // 2 - 3, 1, TZ1 + 2, 0.0)


def build(w, district, terrain):
    grounds(w)
    body(w)
    apse(w)
    r, rt = roofs(w)
    windows(w)
    buttresses(w)
    west_front(w)
    fleche(w, r.ridge_y)
    interior(w)
