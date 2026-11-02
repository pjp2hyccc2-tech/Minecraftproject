#!/usr/bin/env python3
"""Temporary stand-in textures (only written where no texture exists yet) until the art pass lands."""
import os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "sounan", "textures")
rng = np.random.default_rng(7)


def save(rel, arr):
    p = os.path.join(ROOT, rel + ".png")
    if os.path.exists(p):
        return
    os.makedirs(os.path.dirname(p), exist_ok=True)
    a = np.clip(arr, 0, 255).astype(np.uint8)
    if a.shape[-1] == 3:
        a = np.concatenate([a, np.full(a.shape[:2] + (1,), 255, np.uint8)], -1)
    Image.fromarray(a, "RGBA").save(p)


def noise(base, amp, size=16):
    n = rng.normal(0, 1, (size, size, 1))
    return np.array(base, float)[None, None, :] + n * amp


def streaks(base, amp):
    col = rng.normal(0, 1, (1, 16, 1)) * amp
    return np.array(base, float)[None, None, :] + col + rng.normal(0, amp * 0.4, (16, 16, 1))


def planks(base):
    a = noise(base, 8)
    for y in (3, 7, 11, 15):
        a[y] *= 0.6
    return a


blocks = {
    "asphalt": noise((40, 40, 44), 7), "forest_dirt": noise((52, 38, 28), 9), "moss_floor": noise((52, 62, 34), 10),
    "mountain_rock": noise((88, 90, 92), 12), "mossy_rock": noise((74, 86, 62), 12), "river_stones": noise((96, 94, 88), 18),
    "concrete_old": noise((120, 120, 116), 9), "concrete_moss": noise((96, 108, 84), 12), "old_plaster": noise((176, 168, 150), 10),
    "trail_top": noise((104, 84, 60), 10), "forest_floor_top": noise((82, 52, 32), 14), "dark_planks": planks((54, 40, 30)),
    "weathered_planks": planks((110, 104, 96)), "cedar_log": streaks((84, 50, 36), 14), "tin_roof": streaks((112, 66, 44), 18),
}
for k, v in blocks.items():
    save("block/" + k, v)
side = noise((52, 38, 28), 9)
side[:4] = noise((82, 52, 32), 14)[:4]
save("block/forest_floor_side", side)
top = noise((120, 90, 66), 8)
yy, xx = np.mgrid[0:16, 0:16]
r = np.hypot(xx - 7.5, yy - 7.5)
top[(r.astype(int) % 3) == 0] *= 0.7
save("block/cedar_log_top", top)
rib = streaks((84, 50, 36), 14)
rib[6:9] = (226, 92, 160)
save("block/cedar_log_ribbon", rib)
shim = streaks((84, 50, 36), 14)
shim[4:8] = (196, 170, 104)
shim[8:12, ::3] = (236, 236, 228)
save("block/cedar_log_shimenawa", shim)
leaves = np.zeros((16, 16, 4))
leaves[..., :3] = noise((30, 52, 40), 10)
leaves[..., 3] = (rng.random((16, 16)) > 0.25) * 255
save("block/cedar_leaves", leaves)
lat = np.zeros((16, 16, 4))
for i in range(16):
    for p in ((i, i), (i, 15 - i), (0, i), (15, i), (i, 0), (i, 15)):
        lat[p[0], p[1]] = (128, 132, 136, 255)
save("block/steel_lattice", lat)
rope = np.zeros((16, 16, 4))
rope[:, 7:9] = (150, 120, 80, 255)
save("block/climbing_rope", rope)
for d, base in (("hut_door", (96, 80, 62)), ("house_door", (70, 52, 40)), ("iron_gate", (90, 70, 60))):
    for h in ("bottom", "top"):
        a = np.zeros((16, 16, 4))
        a[..., :3] = planks(base)
        a[..., 3] = 255
        if d == "iron_gate":
            a[..., 3] = 0
            a[:, ::3, :] = (96, 74, 62, 255)
            a[::5, :, :] = (96, 74, 62, 255)
        save(f"block/{d}_{h}", a)
items = {"battery": (60, 60, 64), "bear_bell": (200, 160, 60), "hut_key": (150, 120, 70), "memo": (220, 214, 196),
         "omamori": (180, 40, 50), "omiki": (230, 230, 220), "power_bank": (40, 40, 44), "rope": (160, 130, 90),
         "trail_map": (210, 200, 170), "house_door": (70, 52, 40), "hut_door": (96, 80, 62), "iron_gate": (90, 70, 60),
         "flashlight": (50, 50, 54), "flashlight_on": (60, 60, 64), "flashlight_low": (60, 60, 64), "phone": (20, 20, 22),
         "phone_light": (20, 20, 22), "phone_camera": (20, 20, 22), "flare": (180, 30, 30), "flare_lit": (220, 60, 30)}
for k, c in items.items():
    a = np.zeros((16, 16, 4))
    a[3:13, 4:12, :3] = noise(c, 6)[3:13, 4:12]
    a[3:13, 4:12, 3] = 255
    save("item/" + k, a)
gui = {"gui/faces_overlay": (256, 256, 0), "gui/title_logo": (512, 256, 0)}
for k, (w, h, a) in gui.items():
    p = os.path.join(ROOT, k + ".png")
    if not os.path.exists(p):
        Image.new("RGBA", (w, h), (0, 0, 0, a)).save(p)
for i, name in enumerate(["nozoki", "yobigoe", "ushiro", "koyanushi", "oyamasama"]):
    p = os.path.join(ROOT, f"gui/jumpscare_{name}.png")
    if not os.path.exists(p):
        a = noise((60 + i * 10, 50, 50), 30, 256)
        yy, xx = np.mgrid[0:256, 0:256]
        for ex in (96, 160):
            a[np.hypot(xx - ex, yy - 110) < 14] = (0, 0, 0)
        save(f"gui/jumpscare_{name}", a)
os.makedirs(os.path.join(ROOT, "gui/phone"), exist_ok=True)
fp = os.path.join(ROOT, "gui/phone/frame.png")
if not os.path.exists(fp):
    f = np.zeros((320, 160, 4), np.uint8)
    f[..., :3] = 16
    f[..., 3] = 255
    f[24:296, 8:152, 3] = 0
    Image.fromarray(f, "RGBA").save(fp)
for i in range(3):
    p = os.path.join(ROOT, f"gui/phone/wallpaper_{i}.png")
    if not os.path.exists(p):
        g = np.zeros((272, 144, 3))
        g[:150] = np.linspace(60, 160, 150)[:, None, None] * np.array([0.6, 0.75, 1.0])
        g[150:] = (40, 52, 44)
        save(f"gui/phone/wallpaper_{i}", g)
ip = os.path.join(ROOT, "gui/phone/icons.png")
if not os.path.exists(ip):
    ic = np.zeros((32, 192, 4))
    cols = [(60, 140, 80), (60, 60, 70), (230, 200, 80), (80, 200, 100), (60, 170, 80), (230, 200, 120)]
    for j, c in enumerate(cols):
        ic[2:30, j * 32 + 2:j * 32 + 30, :3] = c
        ic[2:30, j * 32 + 2:j * 32 + 30, 3] = 255
    save("gui/phone/icons", ic)
print("placeholders written where missing")
