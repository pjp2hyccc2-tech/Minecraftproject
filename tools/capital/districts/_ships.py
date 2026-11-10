"""Sailing ships for the harbour: a hull with sheer, tumblehome and a raised stern castle, decks, masts with
square sails on yards, bowsprit, rigging, stern lanterns, figurehead and flags."""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

AIR = K.AIR
F = K.F


def ship(w, cx, z_stern, length=30, beam=9, masts=3, deck_y=1, keel_y=-6, seed=0, hull="dark_oak",
         upper="spruce", stripe="gilded_trim", sail="white", flags=("harbour", "kingdom", "pennant")):
    """Ship lying along +z (stern at ``z_stern``, bow at z_stern + length - 1), centred on x = cx."""
    L = length
    half = beam / 2.0
    stern_castle = int(L * 0.24)
    fore_castle = int(L * 0.86)
    hull_st, upper_st = F(f"{hull}_planks"), F(f"{upper}_planks")
    decks = []
    for u in range(L):
        z = z_stern + u
        t = u / (L - 1)
        # plan: blunt stern, fine bow
        if t < 0.12:
            hw = half * (0.78 + 0.22 * t / 0.12)
        elif t > 0.62:
            q = (t - 0.62) / 0.38
            hw = half * math.sqrt(max(0.0, 1 - q ** 1.7))
        else:
            hw = half
        sheer = int(round(1.5 * (abs(t - 0.5) * 2) ** 2))           # deck rises towards bow/stern
        dk = deck_y + (sheer if t > 0.5 else min(sheer, 1))
        decks.append(dk)
        kl = keel_y + int(round(3 * max(0.0, abs(t - 0.45) * 2.2 - 0.5)))
        rail = dk + (3 if u < stern_castle else (2 if u >= fore_castle else 1))
        for y in range(kl, rail + 1):
            f = max(0.0, (y - kl) / max(1.0, (dk - kl)))
            w_y = hw * (min(1.0, f) ** 0.55) if y <= dk else hw - (0.3 if y > dk + 1 else 0.0)
            r = int(math.floor(w_y + 0.25))
            if r < 0:
                continue
            for dx in range(-r, r + 1):
                x = cx + dx
                edge = abs(dx) == r or y == kl
                if y > dk:                                      # bulwarks / castles
                    if edge:
                        st = upper_st if y < rail else B.slab(upper, "bottom")
                        if u < stern_castle and y <= dk + 3 and y < rail:
                            st = upper_st
                        w.set(x, y, z, st)
                    elif u < stern_castle and y == dk + 3 - 0:
                        w.set(x, y, z, upper_st)                # quarterdeck floor
                    elif u < stern_castle and y < dk + 3:
                        w.set(x, y, z, AIR)
                    else:
                        w.set(x, y, z, AIR)
                elif y == dk:
                    w.set(x, y, z, upper_st if not edge else hull_st)
                else:
                    if edge:
                        w.set(x, y, z, hull_st if y != dk - 2 else F(stripe))
                    else:
                        w.set(x, y, z, AIR if y > kl else hull_st)
        # stern castle windows and the transom
        if u == 0:
            for dx in range(-int(hw) + 1, int(hw)):
                w.set(cx + dx, dk + 1, z, B.pane("yellow") if dx % 2 == 0 else upper_st)
                w.set(cx + dx, dk + 2, z, F(stripe) if dx % 3 == 0 else upper_st)
            w.set(cx, dk + 4, z, B.lantern())
        if u < stern_castle and u > 0:
            for s in (-1, 1):
                if u % 2 == 0:
                    w.set(cx + s * int(math.floor(hw + 0.25)), dk + 2, z, B.pane("yellow"))
    # stern castle door and stairs to the quarterdeck
    zc = z_stern + stern_castle
    dd = decks[stern_castle - 1]
    w.set(cx, dd + 1, zc - 1, B.door_states("spruce", "north", "left")[0])
    w.set(cx, dd + 2, zc - 1, B.door_states("spruce", "north", "left")[1])
    for i in range(3):
        w.set(cx + 2, deck_y + 2 + i, zc + 2 - i, B.stairs(upper, "north"))
    w.set(cx, deck_y + 5, z_stern + 1, B.S("gilded_finial"))
    # rudder and figurehead / bowsprit
    w.fill(cx, keel_y + 1, z_stern - 1, cx, deck_y, z_stern - 1, F(f"{hull}_planks"))
    zb = z_stern + L - 1
    for i in range(1, 9):
        w.set(cx, deck_y + 2 + i // 2, zb + i - 1, B.log(upper, "z") if i < 6 else B.fence(upper))
    w.set(cx, deck_y + 1, zb, F(stripe))
    w.set(cx, deck_y + 2, zb + 1, B.S("gilded_finial"))
    # masts with yards and sails
    pos = {3: (0.36, 0.56, 0.76), 2: (0.40, 0.68), 1: (0.5,)}[masts]
    hts = {3: (15, 20, 16), 2: (17, 15), 1: (12,)}[masts]
    tops = []
    for k, (p, h) in enumerate(zip(pos, hts)):
        zm = z_stern + int(L * p)
        y0 = deck_y + 1
        w.fill(cx, keel_y + 1, zm, cx, y0 + h, zm, B.log(upper, "y"))
        tops.append((zm, y0 + h))
        # two (or three) square sails on yards, the lower ones wider
        n_sails = 3 if h >= 18 else 2
        for j in range(n_sails):
            ys = y0 + 3 + j * (h - 3) // n_sails
            ye = ys + (h - 3) // n_sails - 2
            spread = int(round(half + 1.5 - j * 1.2))
            w.fill(cx - spread - 1, ye + 1, zm, cx + spread + 1, ye + 1, zm, B.log(upper, "x"))
            for x in range(cx - spread, cx + spread + 1):
                for y in range(ys, ye + 1):
                    belly = 1 if abs(x - cx) < spread - 1 and ys < y < ye else 0
                    w.set(x, y, zm + belly, B.wool(sail))
                    if belly:
                        w.set(x, y, zm, AIR) if x != cx else None
            w.set(cx, ye + 1, zm, B.log(upper, "y"))
        if h >= 18:                                            # crow's nest
            yn = y0 + h - 3
            for (dx, dz) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
                w.set(cx + dx, yn, zm + dz, B.slab(upper, "top"))
                w.set(cx + dx, yn + 1, zm + dz, B.fence(upper))
        # shrouds: fence lines from the hull sides up to the mast, one block aft of the sails
        for s in (-1, 1):
            xs = cx + s * int(half)
            for i in range(0, int(half)):
                x = xs - s * i
                y = deck_y + 2 + int(i * (h - 4) / max(1, half))
                w.set(x, y, zm - 1, B.fence(upper), only_empty=True)
    # stays between the mast tops (chains along z) and flags on the tops
    for i, (zm, top) in enumerate(tops):
        design = flags[min(i, len(flags) - 1)] if i != len(tops) // 2 else flags[0]
        TK.flagpole(w, cx, top, zm, 2, "east", design, 3.0 if design != "pennant" else 5.0,
                    2.0 if design != "pennant" else 1.0, pole="fence", wood=upper)
    TK.flagpole(w, cx, deck_y + 4, z_stern, 3, "east", flags[1] if len(flags) > 1 else "kingdom", 2.5, 1.5,
                pole="fence", wood=upper)
    # deck furniture: barrels, a hatch, the ship's bell, lanterns on the rails
    zc = z_stern + int(L * 0.46)
    w.set(cx - 1, deck_y + 1, zc, B.barrel("up"))
    w.set(cx + 1, deck_y + 1, zc + 1, B.barrel("up"))
    w.set(cx, deck_y, zc + 3, B.trapdoor("spruce", "north", "top", False))
    w.set(cx - 2, deck_y + 1, z_stern + stern_castle + 1, B.S("bell", attachment="floor", facing="east"))
    return tops


def rowboat(w, cx, z0, length=5, water_y=-2, wood="oak"):
    """Small open rowing boat along +z floating at the water line."""
    for u in range(length):
        z = z0 + u
        hw = 1 if 0 < u < length - 1 else 0
        for dx in range(-hw, hw + 1):
            edge = abs(dx) == hw
            w.set(cx + dx, water_y, z, F(f"{wood}_planks") if (edge or u in (0, length - 1)) else
                  B.slab(wood, "top"))
            if edge or u in (0, length - 1):
                w.set(cx + dx, water_y + 1, z, B.slab(wood, "bottom"))
            else:
                w.set(cx + dx, water_y + 1, z, AIR)
    w.set(cx, water_y + 1, z0 + length // 2, B.slab(wood, "bottom"))
