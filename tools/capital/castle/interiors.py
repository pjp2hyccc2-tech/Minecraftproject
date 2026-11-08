"""Castle interiors: great hall (ballroom), Hall of the Star Gate, throne room, royal library and Nox's
observatory, chapel, knights' barracks and the cloister corridors. Light-shaft emitters sit just inside the
important east/west windows and under the skylights."""
from __future__ import annotations

import math

import numpy as np

from .. import blocks as B
from .. import deco
from .. import shapes as S
from ..noise import hash2, hash3
from . import parts as Pt
from . import plan as P

AIR = "minecraft:air"
CB, SM, GOLD, ROOF, PALE = Pt.CB, Pt.SM, Pt.GOLD, Pt.ROOF, Pt.PALE
CARVED = "carved_castle_stone"


def _sign(w, x, y, z, facing, lines, wall=True, wood="dark_oak", color="black", glowing=False):
    st = B.wall_sign(wood, facing) if wall else B.sign(wood)
    deco.sign_text(w, x, y, z, st, lines, color=color, glowing=glowing)


def spiral_stair(w, cx, cz, r_in, r_out, y0, y1, material="smooth_castle_stone", spt=16, ccw=True, core=None):
    """Helical stair in the annulus r_in..r_out around (cx, cz), from y0 (first step) up to y1."""
    R = int(math.ceil(r_out)) + 1
    core = core or B.pillar()
    out = []
    for y in range(y0, y1 + 1):
        w.set(cx, y, cz, core)
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if not (r_in - 0.2 <= d <= r_out + 0.3):
                continue
            a = (math.atan2(dz, dx) / (2 * math.pi)) % 1.0
            if not ccw:
                a = (1.0 - a) % 1.0
            i0 = int(a * spt)
            ta = (-math.sin(math.atan2(dz, dx)), math.cos(math.atan2(dz, dx)))
            if not ccw:
                ta = (-ta[0], -ta[1])
            face = ("east" if ta[0] > 0 else "west") if abs(ta[0]) >= abs(ta[1]) else ("south" if ta[1] > 0 else "north")
            k = 0
            while True:
                y = y0 + i0 + k * spt
                if y > y1:
                    break
                w.set(cx + dx, y, cz + dz, B.stairs(material, face))
                w.fill(cx + dx, y + 1, cz + dz, cx + dx, min(y + 4, y1 + 3), cz + dz, AIR)
                out.append((dx, dz, y))
                k += 1
    return out


# ============================================================================== great hall
def great_hall(w, T):
    h = P.HALL
    x0, x1 = h["x0"] + 2, h["x1"] - 2          # interior -11..11
    zs, zn = h["z0"] - 2, h["z1"] + 2          # interior -116 .. -148
    fy = P.TY
    # floor: quartz field, deepslate grid on the bay lines, diorite diamonds, gilded medallion
    w.fill(x0, fy, zn, x1, fy, zs, B.S("smooth_quartz"))
    bays_lines = [-117, -123, -129, -135, -141, -147]
    for z in range(zn, zs + 1):
        for x in range(x0, x1 + 1):
            zz = (z + 117) % 6
            if x in (-6, 6) or zz == 0 or x in (x0, x1):
                w.set(x, fy, z, B.S("polished_deepslate"))
            elif abs(abs(x) - 3) + abs(zz - 3) <= 1 and abs(x) < 6:
                w.set(x, fy, z, B.S("polished_diorite"))
            elif abs(x) > 6 and abs(abs(x) - 9) + abs(zz - 3) <= 1:
                w.set(x, fy, z, B.S("polished_diorite"))
    cz = -132
    for x in range(-6, 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x, z - cz)
            if d <= 5.5:
                a = math.degrees(math.atan2(z - cz, x)) % 45
                if d > 4.6:
                    st = B.full(GOLD)
                elif d < 1.2:
                    st = B.S("chiseled_quartz_block")
                elif min(a, 45 - a) * d * math.pi / 180 < 0.6:
                    st = B.full(GOLD) if d < 3.5 else B.S("lapis_block")
                else:
                    st = B.S("smooth_quartz") if d < 3 else B.S("lapis_block")
                w.set(x, fy, z, st)
    # vault + ribs, wall shafts (engaged pillars) with gilded capitals
    spring = h["spring"]
    hs = Pt.pointed_vault(w, zn, zs, x0, x1, spring, material=SM, axis="z", shell=2, k=h["vault_k"],
                          ribs=bays_lines, rib_state=B.full(CARVED))
    apex = max(hs)
    w.fill(0, apex - 1, zn, 0, apex - 1, zs, B.full(GOLD))
    for zb in bays_lines:
        for x in (x0, x1):
            w.fill(x, fy + 1, zb, x, spring - 1, zb, B.pillar())
            w.set(x, fy + 1, zb, B.full(CARVED))
            w.set(x, spring - 1, zb, B.full(GOLD))
            inward = "west" if x > 0 else "east"
            w.set(x - (1 if x > 0 else -1), spring - 1, zb, B.stairs(GOLD, "east" if x > 0 else "west", "top"))
            # kingdom banner on the pillar, sconce below
            deco.kingdom_wall_banner(w, x - (1 if x > 0 else -1), 50, zb, facing=inward)
            deco.kingdom_wall_banner(w, x - (1 if x > 0 else -1), 48, zb, facing=inward)
            deco.sconce(w, x - (1 if x > 0 else -1), 42, zb, inward)
    # cornice (gilded) along the walls at the spring line, dado band
    for x in (x0, x1):
        for z in range(zn, zs + 1):
            if z not in bays_lines and w.get(x, spring - 1, z).endswith("air"):
                w.set(x, spring - 1, z, B.stairs(GOLD, "east" if x > 0 else "west", "top"))
            if w.get(x, fy + 2, z) == "minecraft:air" and z not in bays_lines:
                pass
    # oculus through the vault into the lantern
    for x in range(-2, 3):
        for z in range(cz - 2, cz + 3):
            if math.hypot(x, z - cz) <= 2.3:
                w.fill(x, apex - 3, z, x, apex + 9, z, AIR)
    gx, gz = S.ring_positions(0, cz, 3.0)
    for xx, zz in zip(gx, gz):
        w.set(int(xx), hs[int(xx) - x0], int(zz), B.full(GOLD))
    deco.light_shaft(w, 0, apex - 1, cz, "south", length=34.0, width=3.0, height=3.0, pitch=89.0)
    # chandeliers along the nave and over the aisles
    for z in (-120, -126, -138, -144):
        deco.chandelier(w, 0, apex - 1, z, drop=apex - 1 - 47)
    for z in (-123, -141):
        for x in (-6, 6):
            i = x - x0
            deco.chandelier(w, x, hs[i] - 1, z, drop=max(3, hs[i] - 1 - 49))
    # light shafts inside the tall east/west windows
    for z in (-120, -126, -132, -138, -144):
        deco.light_shaft(w, x1, 48, z, "west", length=24.0, width=4.0, height=14.0, pitch=34.0)
        deco.light_shaft(w, x0, 48, z, "east", length=24.0, width=4.0, height=14.0, pitch=34.0)
    # red carpet runner (skips the medallion)
    for z in range(zn + 4, zs + 1):
        if cz - 6 <= z <= cz + 6:
            continue
        for x in range(-2, 3):
            w.set(x, fy + 1, z, B.carpet("yellow" if abs(x) == 2 else "red"))
    # banquet tables in the aisles
    for s in (-1, 1):
        runs = ((-119, 9), (-137, 9)) if s > 0 else ((-117, 5), (-137, 9))
        for (za, n) in runs:
            deco.long_table(w, 7 * s, fy, za - n + 1, n, axis="z", top="dark_oak", cloth="white", chair_wood="dark_oak")
        for z in ((-121, -125, -139, -143) if s > 0 else (-119, -139, -143)):
            w.set(7 * s, fy + 2, z, B.S("cake"))
    # dais with the high table at the north end
    w.fill(x0, fy + 1, zn, x1, fy + 1, zn + 3, B.full(SM))
    w.fill(x0, fy + 1, zn + 4, x1, fy + 1, zn + 4, B.stairs(SM, "north"))
    w.fill(-2, fy + 1, zn, 2, fy + 1, zn + 4, B.full(SM))
    w.fill(-2, fy + 2, zn, 2, fy + 2, zn + 3, B.carpet("red"))
    for s in (-1, 1):
        for x in range(3 * s, 9 * s + s, s):
            w.set(x, fy + 2, zn + 2, B.slab("dark_oak", "top"))
            w.set(x, fy + 3, zn + 2, B.carpet("blue"))
            w.set(x, fy + 2, zn + 1, B.stairs("dark_oak", "north"))
        w.set(5 * s, fy + 3, zn + 2, B.candle(4, "white"))
        w.set(8 * s, fy + 3, zn + 2, B.candle(3, "white"))
        w.set(6 * s, fy + 2, zn + 1, B.stairs(GOLD, "north"))
        for by in (54, 52, 50):
            deco.kingdom_wall_banner(w, 4 * s, by, zn, facing="south")
    # stairwell down to the Hall of the Star Gate (west aisle, descending northwards)
    sx0, sx1 = -11, -9
    z_top = -126
    for k in range(24):
        z = z_top - k
        y = fy - 1 - k
        sg = P.STARGATE
        in_hall = math.hypot(sx0 + 1 - sg["cx"], z - sg["cz"]) < sg["r"] - 0.4
        w.fill(sx0, y, z, sx1, y, z, B.stairs("polished_deepslate", "north"))
        w.fill(sx0, y + 1, z, sx1, y + 4, z, AIR)
        if in_hall:
            w.fill(sx0, sg["floor"], z, sx1, y - 1, z, B.S("polished_deepslate"))
            w.set(sx1 + 1, y + 1, z, B.wall("polished_blackstone"))
            continue
        w.fill(sx0 - 1, y - 1, z, sx0 - 1, y + 5, z, B.S("polished_deepslate"))
        w.fill(sx1 + 1, y - 1, z, sx1 + 1, y + 5, z, B.S("polished_deepslate"))
        w.fill(sx0, y + 5, z, sx1, y + 5, z, B.S("deepslate_tiles"))
        if k % 4 == 2:
            w.set(sx1, y + 4, z, B.S("chain"))
            w.set(sx1, y + 3, z, B.lantern(hanging=True, soul=True))
    w.fill(sx0, fy, z_top - 2, sx1, fy, z_top, AIR)
    w.fill(sx0, fy + 1, z_top - 2, sx1, fy + 3, z_top, AIR)
    for z in range(z_top - 3, z_top + 2):
        w.set(sx1 + 1, fy + 1, z, B.wall(CB))
    for x in range(sx0, sx1 + 2):
        w.set(x, fy + 1, z_top + 1, B.wall(CB))
    for z in (z_top + 1, z_top - 3):
        w.set(sx1 + 1, fy + 1, z, B.full(SM))
        w.set(sx1 + 1, fy + 2, z, B.lantern(soul=True))
    deco.sign_text(w, sx1 + 2, fy + 1, z_top - 1, B.sign("dark_oak", 12), ["星門の間", "Hall of the", "Star Gate", "↓"],
                   color="blue")
    w.spot("castle.great_hall", (0, fy + 1, -118), 180.0)


# ============================================================================== star gate hall
def star_gate_hall(w, T):
    g = P.STARGATE
    cx, cz, r, fl, wt, ry = g["cx"], g["cz"], g["r"], g["floor"], g["wall_top"], g["dome_ry"]
    R = r + 2
    # carve the drum and the dome
    S.cylinder(w, cx, cz, r + 1.2, fl - 1, wt, B.S("polished_deepslate"))
    S.dome(w, cx, wt, cz, r + 1.2, B.S("deepslate_tiles"), ry=ry + 1.2, hollow=False)
    S.cylinder(w, cx, cz, r, fl + 1, wt, AIR)
    S.dome(w, cx, wt, cz, r, AIR, ry=ry, hollow=False)
    # floor: deepslate tiles with a star map, summoning circle in the centre
    S.cylinder(w, cx, cz, r, fl, fl, B.S("deepslate_tiles"))
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d > r:
                continue
            u = hash2(dx, dz, 404)
            if d > 6.5 and u < 0.035:
                w.set(cx + dx, fl, cz + dz, B.S("chiseled_quartz_block") if u < 0.012 else B.full(GOLD))
            elif 4.6 < d <= 5.5:
                w.set(cx + dx, fl, cz + dz, B.full(GOLD))
            elif d <= 4.6:
                a = math.degrees(math.atan2(dz, dx)) % 60
                if d < 1.5:
                    w.set(cx + dx, fl, cz + dz, B.S("chiseled_quartz_block"))
                elif min(a, 60 - a) * d * math.pi / 180 < 0.55:
                    w.set(cx + dx, fl, cz + dz, B.S("smooth_quartz"))
                else:
                    w.set(cx + dx, fl, cz + dz, B.S("lapis_block"))
            elif abs(d - 9.5) < 0.5:
                w.set(cx + dx, fl, cz + dz, B.S("polished_blackstone_bricks"))
    # pilasters with gilded capitals and continuous dome ribs
    for k in range(12):
        a = k * math.pi / 6
        px, pz = cx + int(round((r - 0.4) * math.cos(a))), cz + int(round((r - 0.4) * math.sin(a)))
        w.fill(px, fl + 1, pz, px, wt, pz, B.S("quartz_pillar", axis="y"))
        w.set(px, fl + 1, pz, B.S("chiseled_quartz_block"))
        w.set(px, wt, pz, B.full(GOLD))
        seen = set()
        for i in range(0, 160):
            ang = i / 160.0 * math.pi / 2
            rr = (r - 0.55) * math.cos(ang)
            yy = wt + int(round((ry - 0.55) * math.sin(ang)))
            cell = (cx + int(round(rr * math.cos(a))), yy, cz + int(round(rr * math.sin(a))))
            if cell in seen or math.hypot(cell[0] - cx, cell[2] - cz) < 3.2:
                continue
            seen.add(cell)
            w.set(*cell, B.S("smooth_quartz") if len(seen) % 6 else B.full(GOLD))
        # banners between pilasters
        a2 = a + math.pi / 12
        bx, bz = cx + int(round((r - 0.8) * math.cos(a2))), cz + int(round((r - 0.8) * math.sin(a2)))
        f = ("west" if math.cos(a2) > 0 else "east") if abs(math.cos(a2)) > abs(math.sin(a2)) else \
            ("north" if math.sin(a2) > 0 else "south")
        if k % 3 != 1:
            deco.kingdom_wall_banner(w, bx, fl + 6, bz, facing=f)
    # stars in the dome (soft amethyst glow)
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for y in range(wt + 1, wt + ry + 1):
                d = math.sqrt((dx / r) ** 2 + (dz / r) ** 2 + ((y - wt) / ry) ** 2)
                if 0.9 < d <= 1.0 and hash3(dx, y, dz, 77) < 0.012:
                    w.set(cx + dx, y, cz + dz, B.S("amethyst_cluster", facing="down"))
    # oculus to the Star Court (the light falls on the summoning circle at noon)
    S.cylinder(w, cx, cz, 2.3, wt + ry - 2, P.TY - 1, AIR)
    gx, gz = S.ring_positions(cx, cz, 2.9)
    for xx, zz in zip(gx, gz):
        w.set(int(xx), wt + ry - 1, int(zz), B.full(GOLD))
    deco.light_shaft(w, cx, P.TY - 2, cz, "south", length=24.0, width=3.0, height=3.0, pitch=89.0)
    # the Star Gate: a vertical ring on a stepped dais at the north side
    rz, rcy, rr = g["ring_z"], g["ring_cy"], g["ring_r"]
    w.fill(-7, fl + 1, rz - 2, 7, fl + 1, rz + 3, B.S("polished_blackstone_bricks"))
    w.fill(-7, fl + 1, rz + 3, 7, fl + 1, rz + 3, B.stairs("polished_blackstone_brick", "north"))
    w.fill(-5, fl + 2, rz - 2, 5, fl + 2, rz + 1, B.S("chiseled_quartz_block"))
    w.fill(-5, fl + 2, rz + 2, 5, fl + 2, rz + 2, B.stairs("quartz", "north"))
    for x in range(-rr - 2, rr + 3):
        for y in range(rcy - rr - 2, rcy + rr + 3):
            d = math.hypot(x, y - rcy)
            if rr - 0.6 <= d <= rr + 0.6:
                a = math.degrees(math.atan2(y - rcy, x)) % 45
                st = B.full(GOLD) if min(a, 45 - a) < 6 else B.S("smooth_quartz")
                w.set(x, y, rz, st)
                w.set(x, y, rz - 1, B.S("chiseled_quartz_block"))
            elif rr + 0.6 < d <= rr + 1.4 and y >= rcy - rr:
                w.set(x, y, rz - 1, B.full(GOLD))
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x = int(round((rr - 1.2) * math.cos(a)))
        y = rcy + int(round((rr - 1.2) * math.sin(a)))
        w.set(x, y, rz, B.S("amethyst_cluster", facing="up" if math.sin(a) < 0 else "down"))
    w.set(0, rcy + rr + 1, rz, B.S("castle_crest_stone"))
    # candle stands around the summoning circle and soul lanterns on chains
    for k in range(8):
        a = k * math.pi / 4
        x, z = cx + int(round(6.6 * math.cos(a))), cz + int(round(6.6 * math.sin(a)))
        if z < rz + 4:
            continue
        w.set(x, fl + 1, z, B.wall("polished_blackstone"))
        w.set(x, fl + 2, z, B.candle(4, "white"))
    for k in range(6):
        a = k * math.pi / 3 + math.pi / 6
        x, z = cx + int(round(9.5 * math.cos(a))), cz + int(round(9.5 * math.sin(a)))
        top = wt + int(ry * math.sqrt(max(0.0, 1 - (9.5 / r) ** 2)))
        for y in range(fl + 7, top):
            w.set(x, y, z, B.chain())
        w.set(x, fl + 6, z, B.lantern(hanging=True, soul=True))
    w.spot("story.lilia", (0, fl + 1, rz + 5), 0.0)
    w.spot("castle.summon", (0, fl + 1, cz), 180.0)


# ============================================================================== throne room
def throne_room(w, T):
    k = P.KEEP
    x0, x1 = k["x0"] + 3, k["x1"] - 3          # -14..14
    zs, zn = k["z0"] - 3, k["z1"] + 3          # -163 .. -185
    fy = P.TY
    ceil = 59
    # floor
    w.fill(x0, fy, zn, x1, fy, zs, B.S("polished_diorite"))
    S.rect_ring(w, x0, zn, x1, zs, fy, B.S("polished_deepslate"))
    for z in range(zn + 1, zs):
        for x in range(x0 + 1, x1):
            if (x + z) % 4 == 0 and abs(x) > 3:
                w.set(x, fy, z, B.S("smooth_quartz"))
    # coffered ceiling: beams every 4 blocks, blue panels with gilded edges
    w.fill(x0, ceil, zn, x1, ceil, zs, B.full(ROOF))
    for x in range(x0, x1 + 1):
        for z in range(zn, zs + 1):
            if x % 4 == 0 or (z + 163) % 4 == 0:
                w.set(x, ceil - 1, z, B.log("dark_oak", "x" if (z + 163) % 4 == 0 else "z", stripped=True))
            elif x % 4 in (1, 3) or (z + 163) % 4 in (1, 3):
                w.set(x, ceil, z, B.full(GOLD))
    # pillars with gilded capitals
    for s in (-1, 1):
        for z in (-166, -170, -174, -178, -182):
            x = 8 * s
            w.fill(x, fy + 1, z, x, ceil - 2, z, B.pillar())
            w.set(x, fy + 1, z, B.full(CARVED))
            w.set(x, ceil - 2, z, B.full(GOLD))
            for f, (dx, dz) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1))):
                w.set(x + dx, ceil - 2, z + dz, B.stairs(GOLD, {"east": "west", "west": "east", "south": "north",
                                                                  "north": "south"}[f], "top"))
        # wall banners between the windows and on the pillars
        for z in (-170, -178):
            deco.kingdom_wall_banner(w, x1 if s > 0 else x0, 50, z, facing="west" if s > 0 else "east")
            deco.kingdom_wall_banner(w, x1 if s > 0 else x0, 48, z, facing="west" if s > 0 else "east")
    # carpet runner from the door to the dais
    for z in range(zn + 6, zs + 1):
        for x in range(-2, 3):
            w.set(x, fy + 1, z, B.carpet("yellow" if abs(x) == 2 else "blue"))
    # dais (three steps) and the throne with a canopy
    for i, (za, y) in enumerate(((zn + 5, fy + 1), (zn + 4, fy + 2), (zn + 3, fy + 3))):
        w.fill(-7, y, zn, 7, y, za - 1, B.full(SM))
        w.fill(-7, y, za, 7, y, za, B.stairs(SM, "north"))
    w.fill(-7, fy + 4, zn, 7, fy + 4, zn + 2, B.carpet("blue"))
    ty = fy + 4
    # the throne: gilded seat with a tall back, crest, finials and armrests
    w.set(0, ty, zn + 1, B.stairs(GOLD, "north"))
    w.fill(0, ty, zn, 0, ty + 4, zn, B.full(GOLD))
    w.set(0, ty + 1, zn, B.S("blue_wool"))
    w.set(0, ty + 2, zn, B.S("blue_wool"))
    w.set(0, ty + 5, zn, B.S("castle_crest_stone"))
    w.set(0, ty + 6, zn, B.S("gilded_finial"))
    for s in (-1, 1):
        w.set(s, ty, zn + 1, B.stairs(GOLD, "east" if s < 0 else "west"))
        w.fill(s, ty, zn, s, ty + 3, zn, B.full(GOLD))
        w.set(s, ty + 4, zn, B.stairs(GOLD, "east" if s < 0 else "west"))
        w.set(s, ty + 5, zn, B.S("gilded_finial"))
        w.set(2 * s, ty, zn, B.full(GOLD))
        w.set(2 * s, ty + 1, zn, B.S("gilded_finial"))
        # the princess's seat (west) and the queen's empty seat (east)
        w.set(4 * s, ty, zn + 1, B.stairs("gilded_trim", "north"))
        w.set(4 * s, ty, zn, B.full(GOLD))
        w.set(4 * s, ty + 1, zn, B.S("white_wool" if s < 0 else "blue_wool"))
        w.set(4 * s, ty + 2, zn, B.slab(GOLD))
    w.fill(-3, fy + 13, zn, 3, fy + 13, zn + 3, B.S("blue_wool"))
    S.rect_ring(w, -3, zn, 3, zn + 3, fy + 13, B.full(GOLD))
    for x in (-3, 3):
        w.fill(x, fy + 4, zn + 3, x, fy + 12, zn + 3, B.pillar())
    for x in (-2, 2):
        deco.kingdom_wall_banner(w, x, fy + 12, zn + 4, facing="south")
    for x in (-6, 6):
        deco.kingdom_wall_banner(w, x, fy + 16, zn, facing="south")
        deco.kingdom_wall_banner(w, x, fy + 14, zn, facing="south")
    # the king's battle armour, ready for the next sortie (7th this month)
    deco.armor_stand(w, 7, ty - 1, zn + 2, yaw=0.0, armor="golden", weapon="minecraft:golden_sword",
                     offhand=deco.blue_shield())
    _sign(w, 7, fy + 1, zn + 6, "south", ["国王陛下の鎧", "※出陣禁止", "── 騎士団長アデル", ""], wall=False)
    # suits of armour of the royal guard between the windows
    for z in (-170, -178):
        for s in (-1, 1):
            deco.armor_stand(w, 12 * s, fy, z, yaw=-90.0 * s, armor="iron", weapon="minecraft:iron_sword",
                             offhand=deco.blue_shield())
    # candelabra and chandeliers
    for s in (-1, 1):
        for z in (zn + 6, zs - 2):
            w.set(4 * s, fy + 1, z, B.S("polished_blackstone_wall"))
            w.set(4 * s, fy + 2, z, B.candle(4, "white"))
    for z in (-168, -174, -180):
        deco.chandelier(w, 0, ceil - 1, z, drop=7)
        for s in (-1, 1):
            deco.chandelier(w, 11 * s, ceil - 1, z, drop=5)
    # war table (a map table) on the east side
    w.fill(10, fy + 1, -166, 12, fy + 1, -164, B.S("dark_oak_planks"))
    w.set(11, fy + 2, -165, B.S("cartography_table"))
    for x in (10, 12):
        w.set(x, fy + 2, -166, B.S("candle", candles=2, lit=True))
    w.set(13, fy + 1, -165, B.stairs("dark_oak", "east"))
    # light shafts inside the east/west windows
    for z in (-167, -174, -181):
        deco.light_shaft(w, x1, 44, z, "west", length=20.0, width=4.0, height=14.0, pitch=33.0)
        deco.light_shaft(w, x0, 44, z, "east", length=20.0, width=4.0, height=14.0, pitch=33.0)
    w.spot("castle.throne", (0, ty + 1, zn + 2), 0.0)
    w.spot("castle.king", (-2, ty, zn + 2), 0.0)
    # doorway between the great hall and the star court
    w.fill(-2, fy + 1, -150, 2, fy + 5, -149, AIR)


# ============================================================================== library + observatory
def library(w, T):
    c = P.LIBRARY
    x0, x1 = c["x0"] + 2, c["x1"] - 2          # -56..-32
    zs, zn = c["z0"] - 2, c["z1"] + 2          # -118 .. -144
    fy = P.TY
    w.fill(x0, fy, zn, x1, fy, zs, B.S("spruce_planks"))
    for z in range(zn, zs + 1):
        for x in range(x0, x1 + 1):
            if (x + z) % 2 == 0 and x0 + 2 < x < x1 - 2:
                w.set(x, fy, z, B.S("dark_oak_planks"))
    hs = Pt.pointed_vault(w, zn, zs, x0, x1, 46, material="dark_oak_planks", axis="z", shell=1, k=0.62,
                          ribs=[-119, -125, -131, -137, -143], rib_state=B.log("dark_oak", "x", stripped=True))
    # bookcases along the walls between windows (two storeys), gallery at y=40 on the long walls
    bays = [-122, -128, -134, -140]
    for x, f in ((x0, "east"), (x1, "west")):
        for z in range(zn, zs + 1):
            if any(abs(z - b) <= 2 for b in bays):
                continue
            deco.bookshelf_wall(w, x, z, x, z, fy + 1, fy + 7, seed=7, facing=f)
            deco.bookshelf_wall(w, x, z, x, z, fy + 9, fy + 13, seed=8, facing=f)
        gx = x + (1 if x == x0 else -1)
        for z in range(zn, zs + 1):
            w.set(gx, fy + 8, z, B.slab("dark_oak", "top"))
            w.set(gx + (1 if x == x0 else -1), fy + 8, z, B.slab("dark_oak", "top"))
            w.set(gx + (2 if x == x0 else -2), fy + 9, z, B.fence("dark_oak"))
    w.fill(x0 + 3, fy + 9, zs - 10, x0 + 3, fy + 9, zs - 3, AIR)
    # end walls: bookcases + the gallery stairs at the south end
    for z, f in ((zn, "south"), (zs, "north")):
        for x in range(x0, x1 + 1):
            if z == zs and -47 <= x <= -41:
                continue
            if z == zn and -52 <= x <= -48:
                continue
            deco.bookshelf_wall(w, x, z, x, z, fy + 1, fy + 7, seed=9, facing=f)
    for i in range(8):
        w.set(x0 + 3, fy + 1 + i, zs - 3 - i, B.stairs("dark_oak", "north"))
        w.fill(x0 + 3, fy + 2 + i, zs - 3 - i, x0 + 3, fy + 5 + i, zs - 3 - i, AIR)
    # free-standing bookcase rows and reading tables down the middle
    for zr in (-121, -127, -133, -139):
        for s in (-1, 1):
            xa = -44 + s * 5
            for x in range(min(xa, xa + 3 * s), max(xa, xa + 3 * s) + 1):
                deco.bookshelf_wall(w, x, zr, x, zr, fy + 1, fy + 3, seed=11)
            w.set(-44 + s * 11, fy + 1, zr, B.lectern("south"))
    for zt in (-124, -130, -136, -142):
        deco.long_table(w, -44, fy, zt - 1, 3, axis="z", top="dark_oak", cloth=None, chair_wood="spruce", candles=False)
        w.set(-44, fy + 2, zt, B.candle(3, "white"))
    # Nox's desk at the north end (by the passage to the observatory): books, a cake, a lectern
    w.fill(-55, fy + 1, -143, -53, fy + 1, -142, B.slab("dark_oak", "top"))
    w.set(-54, fy + 2, -143, B.S("cake"))
    w.set(-55, fy + 2, -142, B.candle(2, "white"))
    w.set(-53, fy + 2, -142, B.S("brewing_stand", has_bottle_0=True, has_bottle_2=True))
    w.set(-54, fy + 1, -141, B.stairs("spruce", "north"))
    w.set(-52, fy + 1, -143, B.lectern("east", has_book=True))
    w.be(-52, fy + 1, -143, B.lectern_nbt("星詠みの覚え書き", "ノクス", [
        "黒き月、新月。星門は開いた。\n……また会えたな。",
        "菓子の在庫：残り三つ。\n至急補充のこと。",
        "異界の者は、この世界の『終わり』に縛られない。\nだが十四日目の夜には、必ず帰すこと。"]))
    # passage to the observatory tower
    w.fill(-51, fy + 1, -149, -49, fy + 4, -144, AIR)
    w.fill(-51, fy, -149, -49, fy, -144, B.S("spruce_planks"))
    # chandeliers
    for z in (-124, -132, -140):
        top = max(hs)
        deco.chandelier(w, -44, top - 1, z, drop=12)
    # light shafts in the east/west windows (reading light)
    for z in bays:
        deco.light_shaft(w, x1 - 1, 41, z, "west", length=16.0, width=4.0, height=10.0, pitch=35.0)
        deco.light_shaft(w, x0 + 1, 41, z, "east", length=16.0, width=4.0, height=10.0, pitch=35.0)
    w.spot("story.nox", (-54, fy + 1, -140), 180.0)
    w.spot("castle.library", (-44, fy + 1, -120), 180.0)


def observatory(w, T):
    o = P.OBSERVATORY
    cx, cz, r, top = o["x"], o["z"], o["r"], o["top"]
    fy = P.TY
    inner = r - 1.6
    # clear the stacked floors, then a spiral stair to the observation room
    S.cylinder(w, cx, cz, inner, fy + 1, top - 10, AIR)
    S.cylinder(w, cx, cz, inner, fy, fy, B.S("spruce_planks"))
    steps = spiral_stair(w, cx, cz, 1.5, inner, fy + 1, top - 11, material="spruce", spt=14)
    last_steps = [s_ for s_ in steps if s_[2] >= top - 11 - 4]
    room = top - 10
    S.cylinder(w, cx, cz, inner, room, room, B.S("dark_oak_planks"))
    for (dx, dz, y) in last_steps:
        w.fill(cx + dx, room, cz + dz, cx + dx, room, cz + dz, AIR)
    S.cylinder(w, cx, cz, inner, room + 1, top + 8, AIR)
    # telescope (brass) pointing at the south slit
    w.set(cx, room + 1, cz, B.S("polished_blackstone_wall"))
    for i in range(5):
        w.set(cx, room + 2 + i, cz + i, B.S("waxed_cut_copper"))
    w.set(cx, room + 7, cz + 5, B.S("lightning_rod", facing="south"))
    w.set(cx - 2, room + 1, cz + 1, B.stairs("spruce", "east"))
    w.fill(cx - 3, room + 1, cz - 2, cx - 2, room + 1, cz - 1, B.slab("dark_oak", "top"))
    w.set(cx - 3, room + 2, cz - 2, B.S("cake", bites=3))
    w.set(cx - 2, room + 2, cz - 1, B.candle(3, "white"))
    deco.kingdom_banner(w, cx + 2, room + 1, cz - 3, rotation=0,
                        nbt=B.banner_nbt([("glb", "yellow"), ("flo", "light_blue"), ("bo", "yellow")]))
    deco.chandelier(w, cx, top + 6, cz, drop=3)
    w.spot("castle.observatory", (cx - 1, room + 1, cz - 1), 180.0)


# ============================================================================== chapel
def chapel(w, T):
    c = P.CHAPEL
    x0, x1 = c["x0"] + 2, c["x1"] - 2          # 30..60
    zs, zn = c["z0"] - 2, c["z1"] + 2          # -122 .. -134
    fy = P.TY
    w.fill(x0, fy, zn, x1, fy, zs, B.S("polished_andesite"))
    for x in range(x0, x1 + 1):
        if x % 2 == 0:
            w.fill(x, fy, zn + 1, x, fy, zs - 1, B.S("polished_diorite"))
    Pt.pointed_vault(w, x0, x1, zn, zs, 43, material=SM, axis="x", shell=1, k=0.62,
                     ribs=list(range(x0 + 2, x1, 6)), rib_state=B.full(GOLD))
    # pews facing the altar (east)
    for x in range(x0 + 4, x1 - 8, 2):
        for z in list(range(zn + 1, -129)) + list(range(-126, zs)):
            w.set(x, fy + 1, z, B.stairs("spruce", "west"))
    for x in range(x0, x1 - 5):
        for z in range(-128, -127 + 1):
            w.set(x, fy + 1, z, B.carpet("red"))
        w.set(x, fy + 1, -129, B.carpet("yellow"))
        w.set(x, fy + 1, -126, B.carpet("yellow"))
    # altar platform and altar
    w.fill(x1 - 5, fy + 1, zn, x1, fy + 1, zs, B.full(SM))
    w.fill(x1 - 5, fy + 1, zn, x1 - 5, fy + 1, zs, B.stairs(SM, "east"))
    w.fill(x1 - 2, fy + 2, -130, x1 - 2, fy + 2, -126, B.S("smooth_quartz"))
    w.fill(x1 - 2, fy + 3, -130, x1 - 2, fy + 3, -126, B.slab(GOLD, "bottom"))
    for z in (-130, -128, -126):
        w.set(x1 - 2, fy + 3, z, B.candle(4, "white"))
    w.set(x1, fy + 2, -128, B.full(GOLD))
    w.set(x1, fy + 3, -128, B.S("castle_crest_stone"))
    w.set(x1, fy + 4, -128, B.S("gilded_finial"))
    for z in (-133, -123):
        w.set(x1 - 1, fy + 2, z, B.S("polished_blackstone_wall"))
        w.set(x1 - 1, fy + 3, z, B.candle(4, "white"))
        deco.kingdom_wall_banner(w, x1, fy + 9, z, facing="west")
    for x in (x0 + 6, x0 + 16):
        deco.chandelier(w, x, 50, -128, drop=6)
    # light: morning through the east window, afternoon through the west rose window
    deco.light_shaft(w, x1 - 1, 40, -128, "west", length=24.0, width=5.0, height=10.0, pitch=30.0)
    deco.light_shaft(w, x0 + 1, 41, -128, "east", length=22.0, width=6.0, height=6.0, pitch=26.0)
    w.spot("castle.chapel", (x0 + 3, fy + 1, -128), -90.0)


# ============================================================================== barracks
def barracks(w, T):
    b = P.BARRACKS
    x0, x1, zs, zn = b["x0"] + 1, b["x1"] - 1, b["z0"] - 1, b["z1"] + 1   # interior -88..-70, -129..-157
    py = P.PY
    w.fill(x0, py, zn, x1, py, zs, B.S("spruce_planks"))
    # partition walls: office (south) | mess hall | armoury (north)
    for z in (-135, -149):
        w.fill(x0, py + 1, z, x1, 32, z, B.full("stone_bricks"))
        w.fill(-80, py + 1, z, -78, py + 3, z, AIR)
    # captain Adel's office
    w.fill(-86, py + 1, -132, -82, py + 1, -132, B.slab("dark_oak", "top"))
    w.set(-84, py + 1, -131, B.stairs("dark_oak", "south"))
    w.set(-85, py + 2, -132, B.candle(2, "white"))
    w.set(-83, py + 2, -132, B.lectern("south", has_book=True))
    w.be(-83, py + 2, -132, B.lectern_nbt("騎士団日誌", "アデル", [
        "盗賊王ラズロ、十三度目の脱獄。\n陛下の宝冠も持ち去られた。",
        "陛下、本日も出陣を希望。\nお止めした。今月七回目。",
        "差し入れの焼き菓子は\n団員には内密に保管のこと。"]))
    w.set(-88, py + 1, -130, B.S("barrel", facing="up"))
    w.set(-88, py + 2, -130, B.S("cake"))
    deco.kingdom_wall_banner(w, -84, py + 5, -134, facing="south")
    _sign(w, -88, py + 3, -133, "east", ["ラズロ捕縛：正正丅", "脱獄　　：正正丅", "（次こそ）", ""])
    w.set(-72, py + 1, -130, B.S("chest", facing="west"))
    w.set(-72, py + 1, -131, B.S("chest", facing="west"))
    # mess hall
    for zt in (-138, -144):
        deco.long_table(w, -83, py, zt - 2, 5, axis="z", top="spruce", cloth=None, chair_wood="spruce", candles=False)
        deco.long_table(w, -75, py, zt - 2, 5, axis="z", top="spruce", cloth=None, chair_wood="spruce", candles=False)
        w.set(-83, py + 2, zt, B.candle(2, "white"))
        w.set(-75, py + 2, zt, B.candle(2, "white"))
    for z in (-138, -144):
        deco.hanging_lantern(w, -79, 33, z, drop=2)
    # armoury: armour stands and weapon frames
    for i, z in enumerate((-151, -153, -155)):
        deco.armor_stand(w, -87, py, z, yaw=-90.0, armor="iron", weapon="minecraft:iron_sword",
                         offhand=deco.blue_shield())
        deco.armor_stand(w, -71, py, z, yaw=90.0, armor="chainmail", weapon="minecraft:iron_axe")
    for x in (-84, -81, -78, -75):
        deco.item_frame(w, x, py + 3, -157, "south", "minecraft:iron_sword", rotation=1)
        deco.item_frame(w, x, py + 2, -157, "south", deco.blue_shield())
    deco.hanging_lantern(w, -79, 33, -153, drop=2)
    # stairs to the dormitory along the east wall
    for i in range(7):
        w.set(-71, py + 1 + i, -137 - i, B.stairs("spruce", "north"))
        w.fill(-71, py + 2 + i, -137 - i, -71, py + 6 + i, -137 - i, AIR)
    w.fill(-71, 33, -142, -71, 33, -140, AIR)
    # dormitory: two rows of beds with chests
    for z in range(-155, -131, 3):
        for x, f in ((-87, "west"), (-72, "east")):
            if x == -72 and z > -147:
                continue
            foot, head = B.bed_states("blue", f)
            w.set(x, 34, z, head)
            w.set(x + (1 if f == "west" else -1), 34, z, foot)
            w.set(x + (2 if f == "west" else -2), 34, z, B.S("chest", facing="east" if f == "west" else "west"))
    for z in (-136, -146, -154):
        deco.hanging_lantern(w, -79, 39, z, drop=1)
    w.spot("castle.barracks", (-79, py + 1, -132), 0.0)


# ============================================================================== corridors
def corridors(w, T):
    ty = P.TY
    for (xa, xb) in ((-29, -14), (14, 27)):
        for x in range(xa + 2, xb - 1, 4):
            deco.armor_stand(w, x, ty, -133, yaw=0.0, armor="iron", weapon="minecraft:iron_sword",
                             offhand=deco.blue_shield())
        for x in range(xa, xb + 1):
            w.set(x, ty + 1, -132, B.carpet("red"))
            w.set(x, ty + 1, -131, B.carpet("red"))
        deco.kingdom_wall_banner(w, (xa + xb) // 2, ty + 4, -133, facing="south")


def build(w, T):
    star_gate_hall(w, T)
    great_hall(w, T)
    throne_room(w, T)
    library(w, T)
    observatory(w, T)
    chapel(w, T)
    barracks(w, T)
    corridors(w, T)
