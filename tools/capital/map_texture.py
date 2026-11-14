"""The in-game map of the capital (王都の地図, 2026-11-14): a top-down painting of the exported blueprint and the places
on it, for ``ui.map.CapitalMapScreen``.

Reads what the game ships (``data/eternalrealm/capital``: the manifest's palette, tiles and named spots) so the map is
always the city that is built, and writes

* ``assets/eternalrealm/textures/gui/capital_map.png``: two pixels per block, the top block's colour (tools/capital/
  colors.py) with hill shading, roof edges and a warm map tint; outside the footprint a parchment margin;
* ``assets/eternalrealm/capital_map.json``: the picture's bounds in world blocks, the places (an id, a kind, the town
  NPC who works there; ``com.eternalrealm.world.map.CapitalMap`` names and draws them) and the districts with the
  point their names are written at.

    python3 tools/capital/map_texture.py
"""
from __future__ import annotations

import json
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, ROOT)

from tools.capital.colors import material  # noqa: E402
from tools.capital import layout as L  # noqa: E402

DATA = os.path.join(ROOT, "src/main/resources/data/eternalrealm/capital")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/eternalrealm")
SCALE = 2

# places on the map: blueprint spot -> (place id, kind); the kinds choose the icon and colour in the game
PLACES = {
    "spawn": ("arrival", "arrival"),
    "respawn": ("goddess", "revival"),
    "npc.quest_board": ("quest_board", "quest"),
    "npc.item_shop": ("item_shop", "shop"),
    "npc.blacksmith": ("blacksmith", "smith"),
    "npc.job_master": ("job_master", "job"),
    "npc.auction": ("auction", "market"),
    "npc.mailbox": ("mailbox", "mail"),
    "npc.guild_registrar": ("guild_registrar", "guild"),
    "npc.guild_estates": ("guild_estates", "guild"),
    "npc.gatekeeper": ("gatekeeper", "gate"),
    "npc.arena_master": ("arena_master", "arena"),
    "npc.hall_of_fame": ("hall_of_fame", "fame"),
    "board.ranking": ("ranking_board", "fame"),
    "npc.innkeeper": ("inn", "inn"),
    "castle.gate": ("castle_gate", "castle"),
    "castle.great_hall": ("great_hall", "castle"),
    "castle.throne": ("throne", "castle"),
    "castle.library": ("library", "castle"),
    "castle.observatory": ("observatory", "castle"),
    "castle.chapel": ("chapel", "castle"),
}
# places the blueprint has no spot for: (place id, kind, npc role, x, z) where the game puts them; the stablemaster
# stands at RealmSetup.STABLE_X/STABLE_Z (keep in step) unless the blueprint names an npc.stable_master spot
EXTRA = [("stable", "stable", "stable_master", -20, 69, "npc.stable_master")]
# district names drawn on the map (others are streets, walls and canals; the arena's name is its place's)
DISTRICTS = ["castle", "plaza", "guild", "civic", "shops", "market", "residential_east", "residential_west",
             "guild_gates", "cathedral", "south_west", "south_east", "harbour", "west_fields", "east_riverside"]


def decode(path: str, tiles: int, height: int):
    data = open(path, "rb").read()
    assert data[:4] == b"ERT1", path
    tx, tz = struct.unpack(">ii", data[4:12])
    p = 12
    out = np.zeros((tiles * tiles, height), np.uint16)

    def varint():
        nonlocal p
        value = 0
        shift = 0
        while True:
            b = data[p]
            p += 1
            value |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                return value
    for column in range(tiles * tiles):
        y = 0
        for _ in range(varint()):
            length = varint()
            index = varint()
            out[column, y:y + length] = index
            y += length
    return tx, tz, out.reshape(tiles, tiles, height)


def main() -> int:
    manifest = json.load(open(os.path.join(DATA, "manifest.json"), encoding="utf-8"))
    size = manifest["tileSize"]
    min_y, max_y = manifest["minY"], manifest["maxY"]
    height = max_y - min_y + 1
    palette = manifest["palette"]
    mats = [material(s) if not s.startswith("eternalrealm:void") else {"kind": "air", "top": (0, 0, 0)}
            for s in palette]
    visible = np.array([m["kind"] != "air" for m in mats])
    colours = np.array([m.get("top", (0, 0, 0)) for m in mats], np.float32)
    water = np.array([m["kind"] == "water" for m in mats])
    xs = [t["x"] for t in manifest["tiles"]]
    zs = [t["z"] for t in manifest["tiles"]]
    x0, z0 = min(xs) * size, min(zs) * size
    nx, nz = (max(xs) - min(xs) + 1) * size, (max(zs) - min(zs) + 1) * size
    top = np.zeros((nx, nz), np.int32)
    ids = np.zeros((nx, nz), np.int32)
    built = np.zeros((nx, nz), bool)
    for tile in manifest["tiles"]:
        tx, tz, vox = decode(os.path.join(DATA, tile["file"]), size, height)
        v = visible[vox]
        has = v.any(axis=2)
        t = np.where(has, height - 1 - np.argmax(v[:, :, ::-1], axis=2), 0)
        i = np.take_along_axis(vox, t[:, :, None], axis=2)[:, :, 0]
        ox, oz = tx * size - x0, tz * size - z0
        top[ox:ox + size, oz:oz + size] = t
        ids[ox:ox + size, oz:oz + size] = i
        built[ox:ox + size, oz:oz + size] = has
    c = colours[ids]
    h = top.astype(np.float32)
    gx = np.gradient(h, axis=0)
    gz = np.gradient(h, axis=1)
    shade = np.clip(1.0 - (gx * 0.55 + gz * 0.85) * 0.09, 0.55, 1.3)
    c = c * shade[:, :, None] * (0.8 + 0.2 * np.clip((h - 30) / 110 + 0.5, 0, 1))[:, :, None]
    # roof and wall edges: a dark line where the ground steps three blocks or more
    step = np.maximum(np.abs(np.diff(h, axis=0, prepend=h[:1])), np.abs(np.diff(h, axis=1, prepend=h[:, :1])))
    c[step >= 3] *= 0.62
    # water a little deeper and calmer
    wet = water[ids]
    c[wet] = c[wet] * 0.55 + np.array((40, 92, 170), np.float32) * 0.45
    # a warm map tint: a little less saturation, towards parchment
    grey = c.mean(axis=2, keepdims=True)
    c = c * 0.86 + grey * 0.14
    c = c * 0.9 + np.array((236, 214, 170), np.float32) * 0.1
    parchment = np.array((222, 205, 164), np.float32)
    c[~built] = parchment
    img = Image.fromarray(np.clip(c.transpose(1, 0, 2), 0, 255).astype(np.uint8))
    img = img.resize((nx * SCALE, nz * SCALE), Image.NEAREST)
    # a soft shadow around the city on the parchment
    mask = Image.fromarray((built.T * 255).astype(np.uint8)).resize((nx * SCALE, nz * SCALE), Image.NEAREST)
    halo = mask.filter(ImageFilter.GaussianBlur(6))
    base = Image.new("RGB", img.size, tuple(int(v) for v in parchment))
    dark = Image.new("RGB", img.size, (150, 128, 92))
    base = Image.composite(dark, base, halo.point(lambda v: int(v * 0.55)))
    img = Image.composite(img, base, mask)
    out = os.path.join(ASSETS, "textures/gui/capital_map.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out, optimize=True)

    spots = manifest["spots"]
    places = []
    for spot, (pid, kind) in PLACES.items():
        if spot in spots:
            x, _, z = spots[spot]["pos"]
            place = {"id": pid, "kind": kind}
            if spot.startswith("npc."):
                place["npc"] = "innkeeper" if pid == "inn" else spot[4:]
            place.update({"x": x, "z": z})
            places.append(place)
    for pid, kind, npc, x, z, spot in EXTRA:
        if spot in spots:
            x, _, z = spots[spot]["pos"]
        places.append({"id": pid, "kind": kind, "npc": npc, "x": x, "z": z})
    districts = []
    for did in DISTRICTS:
        d = L.DISTRICTS.get(did)
        if d is None:
            continue
        # the name goes in the middle of the district's largest box
        bx0, bz0, bx1, bz1 = max(d.boxes, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
        districts.append({"id": did, "x": (bx0 + bx1) / 2.0, "z": (bz0 + bz1) / 2.0})
    doc = {"x0": x0, "z0": z0, "width": nx, "height": nz, "scale": SCALE,
           "places": places, "districts": districts}
    with open(os.path.join(ASSETS, "capital_map.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"capital map: {img.size[0]}x{img.size[1]} px, {len(places)} places, {len(districts)} districts -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
