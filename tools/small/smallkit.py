"""Shared helpers for the HuntCraft small monster model scripts (tools/models/<id>.py).

* run(model, argv, variants)  - export hcmodel JSON + texture(s) and render previews
      --preview [anim ...]  contact sheet  tools/preview/<id>.png
      --big                 four large rest views  tools/preview/<id>_big.png
* gait helpers for bipeds / quadrupeds / insects, generic pose helpers
* fit_to_ground(model, root)  - moves the root bone so the lowest point of the rest pose touches y = 24
"""
import math
import os
import sys

TOOLS = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
if TOOLS not in sys.path:
    sys.path.insert(0, TOOLS)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from hcmodel import Model, anim as A  # noqa: E402,F401
from hcmodel import paint as painter  # noqa: E402
from hcmodel.paint import M  # noqa: E402,F401
from hcmodel.render import preview, render, label  # noqa: E402

ASSETS = os.path.abspath(os.path.join(TOOLS, '..', 'src', 'main', 'resources', 'assets', 'huntcraft'))
PREVIEW_DIR = os.path.join(TOOLS, 'preview')


# ----------------------------------------------------------------------------- geometry
def rest_extent(model):
    """(min, max) of all cube corners in the rest pose (model pixels)."""
    pts = []
    for c in model.all_cubes():
        m = c.bone.rest_matrix()
        ox, oy, oz = c.origin
        dx, dy, dz = c.size
        for x in (ox, ox + dx):
            for y in (oy, oy + dy):
                for z in (oz, oz + dz):
                    pts.append((m @ np.array([x, y, z, 1.0]))[:3])
    pts = np.array(pts)
    return pts.min(0), pts.max(0)


def fit_to_ground(model, root, ground=24.0):
    """Shifts `root`'s pivot vertically so the lowest rest-pose point sits on the ground."""
    _, mx = rest_extent(model)
    dy = ground - mx[1]
    px, py, pz = root.pivot
    root.pivot = (px, py + dy, pz)
    return dy


def size_report(model):
    mn, mx = rest_extent(model)
    w, h, l = (mx[0] - mn[0]) / 16, (24 - mn[1]) / 16, (mx[2] - mn[2]) / 16
    return 'size %.2f wide x %.2f tall x %.2f long (blocks)' % (w, h, l)


# ----------------------------------------------------------------------------- export / preview
def _remap_materials(model, mapping):
    model._remap = mapping
    saved = []
    for c in model.all_cubes():
        saved.append((c, c.mat, dict(c.faces), [dict(d) for d in c.decals]))
        c.mat = mapping.get(c.mat, c.mat)
        c.faces = {k: mapping.get(v, v) for k, v in c.faces.items()}
        for d in c.decals:
            if 'mat' in d:
                d['mat'] = mapping.get(d['mat'], d['mat'])
    return saved


def _restore_materials(saved):
    for c, mat, faces, decals in saved:
        c.bone.model._remap = {}
        c.mat = mat
        c.faces = faces
        c.decals = decals


def big_views(model, tex, out_path, size=440, layers=()):
    views = [('front-left', 35, 16), ('left side', 90, 4), ('front', 0, 6), ('back-right', 215, 18)]
    tiles = []
    for name, yaw, pitch in views:
        im, _ = render(model, tex, layers, size, yaw, pitch)
        tiles.append(label(im, name))
    sheet = Image.new('RGB', (size * 2, size * 2), (20, 22, 26))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % 2) * size, (i // 2) * size))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


def pose_sheet(model, tex, out_path, poses, size=300, yaw=35, pitch=16):
    """poses: list of (label, anim_name, time). One tile each (idle layered under actions)."""
    tiles = []
    fit = None
    for lab, an, t in poses:
        layers = []
        a = model.animations[an]
        if an not in ('idle', 'walk', 'run', 'fly', 'death', 'sleep', 'stun', 'trapped') and 'idle' in model.animations \
                and an not in getattr(model, 'state_poses', ()):
            layers.append((model.animations['idle'], t, 1.0))
        layers.append((a, t, 1.0))
        im, f = render(model, tex, layers, size, yaw, pitch, fit)
        if fit is None:
            c, s = f
            fit = (c, s * 1.25)
            im, _ = render(model, tex, layers, size, yaw, pitch, fit)
        tiles.append(label(im, lab))
    cols = min(6, len(tiles))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * size, rows * size), (20, 22, 26))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % cols) * size, (i // cols) * size))
    sheet.save(out_path)
    return sheet


# ----------------------------------------------------------------------------- post-paint passes
def _passes(model):
    if not hasattr(model, 'post_passes'):
        model.post_passes = []
    return model.post_passes


def _hex(c):
    c = c.lstrip('#')
    return [int(c[i:i + 2], 16) for i in (0, 2, 4)]


def stamp(model, cube, face, x, y, rows, palette, mirror=False):
    """Pixel art on one cube face. rows: list of strings, '.' = keep; palette: char -> '#rrggbb' or
    ('#rrggbb', '#glow'). (x, y) = face-local texel of the first char (from the face's left / top)."""
    def fn(img, glow):
        u0, v0, w, h = cube.face_rects()[face]
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '.' or ch == ' ':
                    continue
                col = palette[ch]
                gcol = None
                if isinstance(col, tuple):
                    col, gcol = col
                xi = (w - 1 - (x + i)) if mirror else (x + i)
                u, v = u0 + xi, v0 + y + j
                if not (0 <= xi < w and 0 <= y + j < h):
                    continue
                img[v, u, :3] = _hex(col)
                img[v, u, 3] = 255
                if gcol is not None:
                    glow[v, u, :3] = _hex(gcol)
                    glow[v, u, 3] = 255
    _passes(model).append(fn)


def stamp_eyes(model, cube, rows, palette, front=1, top=1):
    """Symmetric eyes on both side faces of a head cube; rows are drawn with column 0 = toward the snout."""
    # east face (+X, entity left): column 0 is the front; west face (-X, right): last column is the front
    stamp(model, cube, 'east', front, top, rows, palette)
    stamp(model, cube, 'west', front, top, rows, palette, mirror=True)


def accent_pass(model, cubes, mat_name, mask_fn, seed=7):
    """Repaints texels of `cubes` with material `mat_name` where mask_fn(P, L, N, f, d, r, face) is True.
    P / L = (h, w, 3) model-space / bone-local texel positions (rest pose), N = face normal (model space),
    f/d/r semantic fractions (front / down / right)."""
    def fn(img, glow):
        mat = painter.MATERIALS[getattr(model, '_remap', {}).get(mat_name, mat_name)]
        ys = [((c.bone.rest_matrix() @ np.array([*c.origin, 1.0]))[1]) for c in model.all_cubes()]
        ymin, yspan = min(ys) - 4, max(8.0, max(ys) - min(ys) + 8)
        for cube in cubes:
            mrest = cube.bone.rest_matrix()
            rotm = mrest[:3, :3]
            for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
                g = painter.face_grid(cube, face)
                if g is None:
                    continue
                u0, v0, w, h, I, J, pts, n, f, d, r = g
                P = (np.concatenate([pts, np.ones(pts.shape[:2] + (1,))], axis=-1) @ mrest.T)[..., :3]
                N = rotm @ n
                N = N / (np.linalg.norm(N) + 1e-9)
                mask = mask_fn(P, pts, N, f, d, r, face)
                if mask is None or not np.any(mask):
                    continue
                rgb, gl = painter._shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed + hash(face) % 97, cube)
                region = img[v0:v0 + h, u0:u0 + w]
                keep = region[..., 3] > 0
                sel = mask & keep
                region[sel, :3] = rgb[sel]
                if gl is not None:
                    gr = glow[v0:v0 + h, u0:u0 + w]
                    gs = sel & (gl[..., 3] > 0)
                    gr[gs] = gl[gs]
    _passes(model).append(fn)


def paint(model):
    img, glow = painter.paint_model(model)
    passes = getattr(model, 'post_passes', [])
    if not passes:
        return img, glow
    a = np.array(img).copy()
    g = np.array(glow).copy() if glow is not None else np.zeros_like(a)
    for fn in passes:
        fn(a, g)
    gimg = Image.fromarray(g.astype(np.uint8), 'RGBA') if g[..., 3].any() else None
    return Image.fromarray(a.astype(np.uint8), 'RGBA'), gimg


def export(model):
    """Like Model.export but with post-paint passes (stamps / accents)."""
    import json
    if any(c.uv is None for c in model.all_cubes()):
        model.pack()
    os.makedirs(os.path.join(ASSETS, 'hcmodels'), exist_ok=True)
    with open(os.path.join(ASSETS, 'hcmodels', model.name + '.json'), 'w') as f:
        json.dump(model.to_json(), f, separators=(',', ':'))
    img, glow = paint(model)
    tex_dir = os.path.join(ASSETS, 'textures', 'entity')
    os.makedirs(tex_dir, exist_ok=True)
    img.save(os.path.join(tex_dir, model.name + '.png'))
    glow_path = os.path.join(tex_dir, model.name + '_glow.png')
    if glow is not None:
        glow.save(glow_path)
    elif os.path.exists(glow_path):
        os.remove(glow_path)
    return img, glow


def run(model, argv=None, variants=None, size=240, frames=6):
    """Export JSON + main texture (+ variant textures: {suffix: {mat: mat2}}) and render previews."""
    argv = sys.argv[1:] if argv is None else argv
    bad = ['%s %s' % (c.bone.name, c.size) for c in model.all_cubes() if any(abs(v - round(v)) > 1e-6 for v in c.size)]
    if bad:
        raise ValueError('non-integer cube sizes (box UV needs integers, use inflate): ' + ', '.join(bad))
    img, glow = export(model)
    tex_dir = os.path.join(ASSETS, 'textures', 'entity')
    var_imgs = {}
    for suffix, mapping in (variants or {}).items():
        saved = _remap_materials(model, mapping)
        vimg, vglow = paint(model)
        _restore_materials(saved)
        vimg.save(os.path.join(tex_dir, '%s_%s.png' % (model.name, suffix)))
        var_imgs[suffix] = vimg
    ncubes = sum(1 for _ in model.all_cubes())
    print('%s: texture %dx%d, %d cubes, %d bones, %d anims, %s' % (
        model.name, model.tex_w, model.tex_h, ncubes, len(model.bones), len(model.animations), size_report(model)))
    sel = [x for x in argv if not x.startswith('--')]
    if '--big' in argv:
        idle = [(model.animations['idle'], 0.0, 1.0)] if 'idle' in model.animations else []
        big_views(model, img, os.path.join(PREVIEW_DIR, model.name + '_big.png'), layers=idle)
        for suffix, vimg in var_imgs.items():
            big_views(model, vimg, os.path.join(PREVIEW_DIR, '%s_%s_big.png' % (model.name, suffix)), layers=idle)
    if '--preview' in argv:
        preview(model, img, os.path.join(PREVIEW_DIR, model.name + '.png'), anims=sel or None, frames=frames, size=size)
    return img, glow


# ----------------------------------------------------------------------------- animation helpers
def cyc(t, period, phase=0.0):
    return math.sin(2 * math.pi * t / period + phase)


def leg_cycle(anim, bones, period, phase, thigh_amp, knee_amp, foot_amp, offset=(0, 0, 0), steps=16, lift=None):
    """2/3-segment leg swing. bones = (upper, lower[, foot]). Positive thigh = foot backward.
    The lower joint flexes during the forward swing (phase-shifted), the foot counter-rotates."""
    upper = bones[0]
    A.sample(anim, upper, 'rotation', lambda t: (offset[0] + thigh_amp * cyc(t, period, phase), 0, 0), steps)
    if len(bones) > 1 and bones[1]:
        # knee flexes (positive) mostly while the leg swings forward
        A.sample(anim, bones[1], 'rotation',
                 lambda t: (offset[1] + knee_amp * max(0.0, cyc(t, period, phase - math.pi / 2)), 0, 0), steps)
    if len(bones) > 2 and bones[2]:
        A.sample(anim, bones[2], 'rotation',
                 lambda t: (offset[2] + foot_amp * cyc(t, period, phase - 1.2), 0, 0), steps)
    if lift:
        bone, amount = lift
        A.sample(anim, bone, 'position', lambda t: (0, -amount * max(0.0, cyc(t, period, phase - math.pi / 2)), 0), steps)
    return anim


def quad_walk(anim, legs, period, amp, knee, phases=None, steps=16):
    """legs: dict fl/fr/bl/br -> (upper, lower) bones. Default 4-beat walk phases."""
    ph = phases or {'fl': 0.0, 'br': math.pi * 0.5, 'fr': math.pi, 'bl': math.pi * 1.5}
    for k, bones in legs.items():
        leg_cycle(anim, bones, period, ph[k], amp, knee, 0, steps=steps)
    return anim


def bob(anim, bone, period, amp_y, phase=0.0, roll=0.0, pitch=0.0, steps=16, freq=2):
    """Vertical bob (freq per period) + optional roll/pitch sway (once per period)."""
    A.sample(anim, bone, 'position', lambda t: (0, amp_y * cyc(t, period / freq, phase), 0), steps)
    if roll or pitch:
        A.sample(anim, bone, 'rotation', lambda t: (pitch * cyc(t, period / freq, phase + 0.8), 0, roll * cyc(t, period, phase)), steps)
    return anim


def keys(anim, bone, chan, pts, interp='c'):
    """pts: [(t, (x, y, z)), ...]."""
    getattr(anim, {'rotation': 'rot', 'position': 'pos', 'scale': 'scale'}[chan])(bone, [(t, v, interp) for t, v in pts])
    return anim


def wobble(anim, bone, amp, period, phase=0.0, offset=(0, 0, 0), steps=None):
    return A.sine(anim, bone, 'rotation', amp, period, phase, offset, steps)
