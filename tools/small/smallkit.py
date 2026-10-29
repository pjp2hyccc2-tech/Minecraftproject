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
from hcmodel.render import preview, render, label, pose_matrices, model_quads, to_world, view_matrix, L0, L1  # noqa: E402

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


# ----------------------------------------------------------------------------- glow-aware rendering
def _raster(quads, tex, size, yaw, pitch, fit, bg=(46, 50, 58), lit=True):
    """hcmodel.render.rasterize with an optional unlit mode (for the full-bright glow pass)."""
    import math as _m
    W = H = size
    img = np.zeros((H, W, 3), float)
    img[:] = bg
    zbuf = np.full((H, W), np.inf)
    V = view_matrix(yaw, pitch)
    tex = np.asarray(tex.convert('RGBA'), dtype=float)
    TH, TW = tex.shape[:2]
    tris = []
    for P, uv, n in quads:
        nw = np.array([n[0], -n[1], -n[2]])
        nl = np.linalg.norm(nw)
        nw = nw / nl if nl > 0 else nw
        bright = min(1.0, 0.4 + 0.6 * (max(0, nw @ L0) + max(0, nw @ L1))) if lit else 1.0
        tris.append((to_world(P) @ V.T, uv, bright))
    if not tris:
        return Image.fromarray(img.astype(np.uint8)), fit
    if fit is None:
        pts = np.concatenate([t[0] for t in tris])
        mn, mx = pts[:, :2].min(0), pts[:, :2].max(0)
        fit = ((mn + mx) / 2, max(mx - mn) * 1.08 + 1e-6)
    center, span = fit
    sc = W / span
    for S, uv, bright in tris:
        Q = np.stack([(S[:, 0] - center[0]) * sc + W / 2, -(S[:, 1] - center[1]) * sc + H / 2, S[:, 2]], axis=-1)
        for (a, b, c) in ((0, 1, 2), (0, 2, 3)):
            p0, p1, p2 = Q[a], Q[b], Q[c]
            area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p1[1] - p0[1]) * (p2[0] - p0[0])
            if abs(area) < 1e-9:
                continue
            xmin = max(0, int(_m.floor(min(p0[0], p1[0], p2[0]))))
            xmax = min(W - 1, int(_m.ceil(max(p0[0], p1[0], p2[0]))))
            ymin = max(0, int(_m.floor(min(p0[1], p1[1], p2[1]))))
            ymax = min(H - 1, int(_m.ceil(max(p0[1], p1[1], p2[1]))))
            if xmin > xmax or ymin > ymax:
                continue
            xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            w0 = ((p1[0] - xs) * (p2[1] - ys) - (p1[1] - ys) * (p2[0] - xs)) / area
            w1 = ((p2[0] - xs) * (p0[1] - ys) - (p2[1] - ys) * (p0[0] - xs)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
            u = w0 * uv[a][0] + w1 * uv[b][0] + w2 * uv[c][0]
            v = w0 * uv[a][1] + w1 * uv[b][1] + w2 * uv[c][1]
            col = tex[np.clip((v * TH).astype(int), 0, TH - 1), np.clip((u * TW).astype(int), 0, TW - 1)]
            ok = inside & (col[..., 3] > 25)
            sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
            ok &= z < sub
            if not ok.any():
                continue
            sub[ok] = z[ok]
            img[ymin:ymax + 1, xmin:xmax + 1][ok] = col[ok][:, :3] * bright
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), fit


def render_glow(model, tex, glow, layers=(), size=420, yaw=35, pitch=-20, fit=None):
    """Lit base render + the emissive layer added full-bright on visible surfaces (like RenderType.eyes)."""
    quads = model_quads(model, pose_matrices(model, layers))
    im, fit = _raster(quads, tex, size, yaw, pitch, fit)
    if glow is None:
        return im, fit
    t = np.asarray(tex.convert('RGBA'))
    g = np.asarray(glow.convert('RGBA'))
    gt = np.zeros_like(t)
    gt[..., 3] = t[..., 3]                    # keep occlusion of the base model
    lit = (g[..., 3] > 0) & (g[..., :3].sum(-1) > 0)
    gt[lit, :3] = g[lit, :3]
    gim, _ = _raster(quads, Image.fromarray(gt, 'RGBA'), size, yaw, pitch, fit, bg=(0, 0, 0), lit=False)
    out = np.clip(np.asarray(im).astype(int) + np.asarray(gim).astype(int), 0, 255).astype(np.uint8)
    return Image.fromarray(out), fit


def glow_ratio(img, glow):
    """Lit emissive texels / opaque base texels (keep well under ~0.15)."""
    if glow is None:
        return 0.0
    g = np.asarray(glow.convert('RGBA'))
    b = np.asarray(img.convert('RGBA'))
    lit = ((g[..., 3] > 0) & (g[..., :3].sum(-1) > 0)).sum()
    return lit / max(1, (b[..., 3] > 0).sum())


# NOTE: in hcmodel.render a NEGATIVE pitch looks from above (positive pitch looks from below).
VIEWS_ABOVE = [('front-right (above)', 35, -20), ('right side', 90, -4), ('front', 0, -8), ('back-left (above)', 215, -22)]


def big_views(model, tex, out_path, size=440, layers=(), glow=None):
    views = VIEWS_ABOVE
    tiles = []
    for name, yaw, pitch in views:
        im, _ = render_glow(model, tex, glow, layers, size, yaw, pitch)
        tiles.append(label(im, name))
    sheet = Image.new('RGB', (size * 2, size * 2), (20, 22, 26))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % 2) * size, (i // 2) * size))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


def contact_sheet(model, tex, out_path, anims=None, frames=6, size=240, glow=None):
    """Like hcmodel.render.preview, but every view looks from above (as players see small monsters)."""
    rest = [('front-right', 35, -18), ('right side', 90, -4), ('front', 0, -8), ('top', 0, -80), ('back-left', 215, -20)]
    idle = [(model.animations['idle'], 0.0, 1.0)] if 'idle' in model.animations else []
    rows = [[label(render_glow(model, tex, glow, idle, size, y, p)[0], n) for n, y, p in rest]]
    _, fit = render(model, tex, (), size, 35, -18)
    fit = (fit[0], fit[1] * 1.25)
    for an in (anims if anims is not None else list(model.animations.keys())):
        a = model.animations[an]
        row = []
        for k in range(frames):
            t = a.length * k / (frames - 1 if not a.loop else frames)
            layers = [(a, t, 1.0)]
            if an not in ('idle', 'walk', 'run', 'fly', 'death', 'sleep', 'stun', 'trapped') + tuple(getattr(model, 'state_poses', ())) \
                    and 'idle' in model.animations:
                layers.insert(0, (model.animations['idle'], t, 1.0))
            im, _ = render_glow(model, tex, glow, layers, size, 35, -18, fit)
            row.append(label(im, '%s t=%.2f' % (an, t)))
        rows.append(row)
    cols = max(len(r) for r in rows)
    sheet = Image.new('RGB', (cols * size, len(rows) * size), (20, 22, 26))
    for ri, r in enumerate(rows):
        for ci, im in enumerate(r):
            sheet.paste(im, (ci * size, ri * size))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


def pose_sheet(model, tex, out_path, poses, size=300, yaw=35, pitch=-16, glow=None):
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
        im, f = render_glow(model, tex, glow, layers, size, yaw, pitch, fit)
        if fit is None:
            c, s = f
            fit = (c, s * 1.25)
            im, _ = render_glow(model, tex, glow, layers, size, yaw, pitch, fit)
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


def _texel(model, img):
    """HD texel factor of the image being painted (1 for legacy textures)."""
    return max(1, img.shape[0] // model.tex_h)


def pixel_pass(model, fn1x, round_shapes=True):
    """Registers a post-paint pass written against 1x pixels: fn1x(img1x, glow1x) edits RGBA float arrays at
    model-pixel resolution; in HD mode the edits are replayed on the HD texture (rounded, anti-aliased)."""
    def fn(img, glow):
        K = _texel(model, img)
        if K == 1:
            fn1x(img, glow)
            return
        from hcmodel.hdup import replay
        replay(img, glow, fn1x, K, round_shapes=round_shapes)
    _passes(model).append(fn)


def stamp(model, cube, face, x, y, rows, palette, mirror=False):
    """Pixel art on one cube face. rows: list of strings, '.' = keep; palette: char -> '#rrggbb' or
    ('#rrggbb', '#glow'). (x, y) = face-local texel of the first char (from the face's left / top).
    In HD mode the stamp is replayed with rounded, anti-aliased shapes (pupils become round, lines soft)."""
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
                else:
                    glow[v, u] = 0          # a plain stamped pixel (pupil, outline...) must not glow
    pixel_pass(model, fn)


def stamp_eyes(model, cube, rows, palette, front=1, top=1):
    """Symmetric eyes on both side faces of a head cube; rows are drawn with column 0 = toward the snout.
    HD: a proper eye (dark ring, iris gradient, slit pupil, highlight) is drawn over the stamp's box instead."""
    # east face (+X, entity left): column 0 is the front; west face (-X, right): last column is the front
    def hd(img, glow):
        K = _texel(model, img)
        if K == 1:
            return
        from hcmodel.paint import _eye_hd, hexrgb
        cols = [(ch, (_hex(v[0]) if isinstance(v, tuple) else _hex(v)), (v[1] if isinstance(v, tuple) else None)) for ch, v in palette.items()]
        used = set(''.join(rows)) - {'.', ' '}
        cols = [c for c in cols if c[0] in used]
        def sat(rgb):
            return (max(rgb) - min(rgb)) + max(rgb) * 0.2
        iris = max(cols, key=lambda c: sat(c[1]))
        dark = min(cols, key=lambda c: sum(c[1]))
        gcol = hexrgb(iris[2]) if iris[2] else None
        ys = [j for j, r in enumerate(rows) if any(ch not in '. ' for ch in r)]
        xs = [i for r in rows for i, ch in enumerate(r) if ch not in '. ']
        y0, y1, x0, x1 = min(ys), max(ys), min(xs), max(xs)
        ew, eh = (x1 - x0 + 1), (y1 - y0 + 1)
        for face, mirror in (('east', False), ('west', True)):
            u0, v0, w, h = cube.face_rects()[face]
            cx = (front + x0 + ew / 2.0)
            if mirror:
                cx = w - cx
            cy = top + y0 + eh / 2.0
            _eye_hd(img, glow, u0 * K, v0 * K, w * K, h * K, cx * K, cy * K, ew * K * 0.95, eh * K * 0.9,
                    np.array(iris[1], float), gcol, np.array(dark[1], float) * 0.6, True, K)
    stamp(model, cube, 'east', front, top, rows, palette)
    stamp(model, cube, 'west', front, top, rows, palette, mirror=True)
    _passes(model).append(hd)


def accent_pass(model, cubes, mat_name, mask_fn, seed=7):
    """Repaints texels of `cubes` with material `mat_name` where mask_fn(P, L, N, f, d, r, face) is True.
    P / L = (h, w, 3) model-space / bone-local texel positions (rest pose), N = face normal (model space),
    f/d/r semantic fractions (front / down / right).  HD: the mask edge is anti-aliased (~half a model px)."""
    def fn(img, glow):
        K = _texel(model, img)
        mat = painter.MATERIALS[getattr(model, '_remap', {}).get(mat_name, mat_name)]
        ys = [((c.bone.rest_matrix() @ np.array([*c.origin, 1.0]))[1]) for c in model.all_cubes()]
        ymin, yspan = min(ys) - 4, max(8.0, max(ys) - min(ys) + 8)
        for cube in cubes:
            mrest = cube.bone.rest_matrix()
            rotm = mrest[:3, :3]
            for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
                g = painter.face_grid(cube, face, K)
                if g is None:
                    continue
                u0, v0, w, h, I, J, pts, n, f, d, r = g
                P = (np.concatenate([pts, np.ones(pts.shape[:2] + (1,))], axis=-1) @ mrest.T)[..., :3]
                N = rotm @ n
                N = N / (np.linalg.norm(N) + 1e-9)
                mask = mask_fn(P, pts, N, f, d, r, face)
                if mask is None or not np.any(mask):
                    continue
                rgb, gl = painter._shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed + hash(face) % 97, cube, texel=K)
                region = img[v0:v0 + h, u0:u0 + w]
                keep = region[..., 3] > 0
                if K > 1:
                    cov = painter._soften_mask(np.asarray(mask, bool), K) * keep
                    region[..., :3] = region[..., :3] * (1 - cov[..., None]) + rgb * cov[..., None]
                    sel = (cov > 0.5)
                else:
                    sel = np.asarray(mask, bool) & keep
                    region[sel, :3] = rgb[sel]
                if gl is not None:
                    gr = glow[v0:v0 + h, u0:u0 + w]
                    gs = sel & (gl[..., 3] > 0)
                    gr[gs] = gl[gs]
    _passes(model).append(fn)


def dim_under_glow(model, factor=0.35):
    """Final pass: darkens base texels that glow. The emissive layer is ADDED full-bright in game, so a bright base
    under a bright glow clips to yellow-white in daylight; a dark base keeps the glow's own colour."""
    def fn(img, glow):
        lit = (glow[..., 3] > 0) & (glow[..., :3].sum(-1) > 0)
        img[lit, :3] = (img[lit, :3] * factor).astype(img.dtype)
    model.final_passes = getattr(model, 'final_passes', []) + [fn]


def paint(model):
    img, glow = painter.paint_model(model)
    passes = getattr(model, 'post_passes', []) + getattr(model, 'final_passes', [])
    if not passes:
        return img, glow
    a = np.array(img).astype(float)
    g = np.array(glow).astype(float) if glow is not None else np.zeros_like(a)
    for fn in passes:
        fn(a, g)
    a = np.clip(a, 0, 255)
    g = np.clip(g, 0, 255)
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
    painter.save_pbr(model, os.path.join(tex_dir, model.name))
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
        var_imgs[suffix] = (vimg, vglow)
    ncubes = sum(1 for _ in model.all_cubes())
    print('%s: texture %dx%d, %d cubes, %d bones, %d anims, %s, glow ratio %.3f' % (
        model.name, model.tex_w, model.tex_h, ncubes, len(model.bones), len(model.animations), size_report(model),
        glow_ratio(img, glow)))
    sel = [x for x in argv if not x.startswith('--')]
    if '--big' in argv:
        idle = [(model.animations['idle'], 0.0, 1.0)] if 'idle' in model.animations else []
        big_views(model, img, os.path.join(PREVIEW_DIR, model.name + '_big.png'), layers=idle, glow=glow)
        for suffix, (vimg, vglow) in var_imgs.items():
            big_views(model, vimg, os.path.join(PREVIEW_DIR, '%s_%s_big.png' % (model.name, suffix)), layers=idle, glow=vglow)
    if '--preview' in argv:
        contact_sheet(model, img, os.path.join(PREVIEW_DIR, model.name + '.png'), anims=sel or None, frames=frames, size=size,
                      glow=glow)
    if '--closeup' in argv:
        from hcmodel.render import closeup
        closeup(model, img, os.path.join(PREVIEW_DIR, model.name + '_hd.png'), size=560, glow=glow)
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
