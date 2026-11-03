"""Shared helpers for the HITORI prop modules: flat / emissive materials, round shapes from box strips, the water and
rain textures (animated), and HITORI palettes for wood, plastics and fabrics."""
import math

import numpy as np

from common import C, ramp, pal, rng, grid, texture, fnoise, REGISTRY
from hk_kit import Atlas, Model, Mat, child, MODELS, NS
from hk_lib import (blank, from_idx, noise, dl, tint, over, rect, px, hline, vline, line, frame, bevel, wear, specks,
                    grime, disc_mask, ring_mask, m_wood, m_plastic, m_fabric, m_brushed)
import hk_mats as M

# ================================================================================================ palettes
TEAK = ramp(['#2a170c', '#3a2112', '#4a2c18', '#5b371f', '#6c4327', '#7d4f2f', '#8e5c38'], 7)
LACQUER = ramp(['#1a0c08', '#28130c', '#361a10', '#452215', '#552b1a', '#66351f'], 6)
KEYAKI = ramp(['#2e1a0e', '#3e2414', '#4f2f1a', '#603a21', '#714628', '#82522f', '#935f37'], 7)
PALEWOOD = ramp(['#6a5032', '#806040', '#95714c', '#a88258', '#b89264', '#c6a172', '#d2af80'], 7)
DARKWOOD = ramp(['#140c07', '#1e120a', '#29190e', '#352113', '#422a18', '#50331e'], 6)
CREAMPL = ramp(['#8c8470', '#a29a84', '#b6ae97', '#c7bfa7', '#d5ceb6', '#e1dac3', '#ebe5cf'], 7)   # yellowed plastic
GREYPL = ramp(['#2e3032', '#3d4042', '#4d5053', '#5e6164', '#6f7275', '#818487', '#93969a'], 7)
CHARCOAL = ramp(['#0f0f10', '#18181a', '#212124', '#2b2b2f', '#36363a', '#424246'], 6)
STEELW = ramp(['#5a5e60', '#6e7274', '#83878a', '#989c9e', '#adb1b2', '#c1c4c5', '#d4d6d7'], 7)
STAINLESS = ramp(['#4a4e50', '#606466', '#777b7d', '#8e9294', '#a5a9aa', '#babdbe', '#cdd0d0'], 7)
RUST = ramp(['#2a140a', '#3e1d0e', '#542713', '#6a3218', '#7f3e1e', '#934c26'], 6)
PAPER = ramp(['#9d968a', '#b2ab9e', '#c5bfb1', '#d4cfc1', '#e0dccf', '#eae7db', '#f3f1e8'], 7)
FUSUMA_PAPER = ramp(['#a39a84', '#b5ad97', '#c4bca6', '#d0c9b3', '#dad4bf', '#e2dcc8', '#e9e4d2'], 7)

# ================================================================================================ materials


def solid(key, color, bev=False):
    return M.solid(key, color, bev)


def lit(key, color):
    """Flat colour meant for emissive faces (pass glow=... on the box)."""
    return M.solid(key, color, False)


def mat(key, fn, per_face=False):
    """Mat from fn(w, h, face) -> array."""
    return M.custom(key, fn, per_face)


def painted(key, palette, base=None, amp=0.3, beta=1.6, bev=True, wear_amt=0.0, seed=0):
    """Smooth painted / plastic surface with soft noise and pixel bevel."""
    def fn(w, h, face):
        n = noise(w, h, M._seed(key, w, h, face) + seed, beta=beta, wrap=False)
        b = (len(palette) - 1) * 0.6 if base is None else base
        a = from_idx(b + n * amp, palette)
        return M._finish(a, w, h, face, bev, wear_amt, M._seed(key, w, h, face))
    return Mat(key, fn)


def woodmat(key, palette, base=None, amp=0.5, along=None, bev=True, wear_amt=0.03):
    return M.wood(key, palette, base=base, amp=amp, bev=bev, wear_amt=wear_amt, along=along)


# ================================================================================================ geometry helpers
def disc_strips(m, cx, cz, r, y0, y1, side, top_key=None, bottom=None, step=1.0, atlas=None, glow=(), uniq=None,
                top_spec=None):
    """A disc (cylinder) of radius r centred at (cx, cz) built from strips along z of width `step`.
    top_key: an atlas region (2r x 2r) whose sub-rects map onto each strip's up face (one picture across strips)."""
    x = cx - r
    while x < cx + r - 1e-6:
        xb = min(cx + r, x + step)
        xm = (x + xb) / 2
        half = math.sqrt(max(0.0, r * r - (xm - cx) ** 2))
        half = max(0.5, round(half * 2) / 2)
        za, zb = cz - half, cz + half
        faces = {}
        if top_key is not None:
            faces['up'] = {'r': top_key, 'sub': (x - (cx - r), za - (cz - r), xb - (cx - r), zb - (cz - r))}
        elif top_spec is not None:
            faces['up'] = top_spec
        if bottom is not None:
            faces['down'] = bottom
        m.box([x, y0, za], [xb, y1, zb], side, faces=faces, glow=glow, uniq=uniq)
        x = xb
    return m


def cord(m, x, z, y0, y1, material, w=0.4):
    """A thin vertical cord / wire."""
    h = w / 2
    m.box([x - h, y0, z - h], [x + h, y1, z + h], material)
    return m


# ================================================================================================ water / rain textures
def water_frame(w, h, i, n_frames, seed, base='#1c2a30', alpha=200, bright='#5a7a86', depth=0.0):
    """Still dark water: a slow ripple pattern (animated), a faint reflection band. Periodic in time."""
    x, y = grid(w, h)
    t = i / n_frames * 2 * math.pi
    n1 = fnoise(w, h, seed, beta=1.6)
    n2 = fnoise(w, h, seed + 1, beta=1.6)
    f = n1 * math.cos(t) + n2 * math.sin(t)
    a = blank(w, h, C(base, alpha))
    a[..., :3] += f[..., None] * 6
    rip = np.abs(np.sin((x + y * 0.6) * 0.9 + f * 1.6 + t)) < 0.12
    a[rip, :3] = a[rip, :3] * 0.6 + C(bright)[:3] * 0.4
    a[rip, 3] = np.minimum(255, a[rip, 3] + 20)
    return a


def stream_frame(w, h, i, n_frames, seed=0):
    """Falling tap water: bright streaks moving down."""
    x, y = grid(w, h)
    a = blank(w, h, C('#9ab8c4', 150))
    ph = (y - i * h / n_frames * 2) % h
    hl = (np.abs(np.sin(ph * 1.3 + x * 2.0)) < 0.35)
    a[hl] = C('#e4f0f4', 210)
    return a


def register_anim(path, size, frames, painter, frametime=2, interpolate=False, frame_list=None, cutout=False):
    """An animated texture: painter(i) -> (h, w, 4) for i in 0..frames-1 (concatenated vertically)."""
    def fn():
        return [painter(i) for i in range(frames)]
    texture(path, size=size, frames=frames, cutout=cutout)(fn)
    meta = {'animation': {'frametime': frametime, 'interpolate': interpolate}}
    if frame_list is not None:
        meta['animation']['frames'] = frame_list
    if size[0] != size[1]:
        meta['animation']['width'] = size[0]
        meta['animation']['height'] = size[1]
    REGISTRY[path]['mcmeta'] = meta
    return path


def ref(path):
    return f'{NS}:{path}'
