"""Software preview renderer that reproduces Minecraft's entity model rendering
(ModelPart transforms, box UV mapping incl. mirror, keyframe animations, entity lighting).
Used to visually check models/textures/animations without running the game."""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from .model import part_matrix

L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


def pose_matrices(model, layers=()):
    """layers: list of (Animation, time_seconds, weight). Returns bone name -> 4x4 matrix (pixel units)."""
    mats = {}

    def visit(bone, parent_m):
        px, py, pz = bone.pivot
        rx, ry, rz = (math.radians(a) for a in bone.rot)
        sx = sy = sz = 1.0
        for anim, t, wgt in layers:
            r = anim.sample(bone.name, 'rotation', t)
            if r:
                rx += math.radians(r[0]) * wgt
                ry += math.radians(r[1]) * wgt
                rz += math.radians(r[2]) * wgt
            p = anim.sample(bone.name, 'position', t)
            if p:
                px += p[0] * wgt
                py += p[1] * wgt
                pz += p[2] * wgt
            s = anim.sample(bone.name, 'scale', t)
            if s:
                sx += (s[0] - 1) * wgt
                sy += (s[1] - 1) * wgt
                sz += (s[2] - 1) * wgt
        m = parent_m @ part_matrix(px, py, pz, rx, ry, rz, sx, sy, sz)
        mats[bone.name] = m
        for c in bone.children:
            visit(c, m)

    for root in model.roots:
        visit(root, np.eye(4))
    return mats


def cube_quads(cube, m, tw, th):
    """Returns list of (verts4 (4,3) pixel-space after bone matrix, uv4 (4,2) normalized, normal (3,))."""
    ox, oy, oz = cube.origin
    dx, dy, dz = cube.size
    g = cube.inflate
    x0, y0, z0 = ox - g, oy - g, oz - g
    x1, y1, z1 = ox + dx + g, oy + dy + g, oz + dz + g
    if cube.mirror:
        x0, x1 = x1, x0
    v7 = (x0, y0, z0); v = (x1, y0, z0); v1 = (x1, y1, z0); v2 = (x0, y1, z0)
    v3 = (x0, y0, z1); v4 = (x1, y0, z1); v5 = (x1, y1, z1); v6 = (x0, y1, z1)
    u, vv = cube.uv
    f4 = u; f5 = u + dz; f6 = u + dz + dx; f7 = u + dz + dx + dx; f8 = u + dz + dx + dz; f9 = u + dz + dx + dz + dx
    f10 = vv; f11 = vv + dz; f12 = vv + dz + dy
    polys = [
        ((v4, v3, v7, v), f5, f10, f6, f11, (0, -1, 0)),   # DOWN  (minY = visual top)
        ((v1, v2, v6, v5), f6, f11, f7, f10, (0, 1, 0)),   # UP    (maxY = visual bottom)
        ((v7, v3, v6, v2), f4, f11, f5, f12, (-1, 0, 0)),  # WEST
        ((v, v7, v2, v1), f5, f11, f6, f12, (0, 0, -1)),   # NORTH
        ((v4, v, v1, v5), f6, f11, f8, f12, (1, 0, 0)),    # EAST
        ((v3, v4, v5, v6), f8, f11, f9, f12, (0, 0, 1)),   # SOUTH
    ]
    out = []
    rot = m[:3, :3]
    for verts, ua, va, ub, vb, n in polys:
        uvs = [(ub, va), (ua, va), (ua, vb), (ub, vb)]
        nn = np.array(n, float)
        if cube.mirror:
            nn[0] *= -1
        P = np.array([(m @ np.array([*p, 1.0]))[:3] for p in verts])
        out.append((P, np.array(uvs, float) / np.array([tw, th]), rot @ nn))
    return out


def model_quads(model, mats):
    quads = []
    for root in model.roots:
        for bone in root.walk():
            m = mats[bone.name]
            for c in bone.cubes:
                quads.extend(cube_quads(c, m, model.tex_w, model.tex_h))
    return quads


def to_world(P):
    """Model pixels -> world blocks (entity yaw 0, i.e. facing +Z)."""
    return np.stack([P[..., 0] / 16.0, 1.501 - P[..., 1] / 16.0, -P[..., 2] / 16.0], axis=-1)


def view_matrix(yaw_deg, pitch_deg):
    y = math.radians(yaw_deg + 180.0)
    p = math.radians(pitch_deg)
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return rx @ ry


def rasterize(quads, tex, size, yaw, pitch, fit=None, bg=(46, 50, 58)):
    W = H = size
    img = np.zeros((H, W, 3), float)
    img[:] = bg
    # simple floor gradient
    zbuf = np.full((H, W), np.inf)
    V = view_matrix(yaw, pitch)
    tex = np.asarray(tex.convert('RGBA'), dtype=float)
    TH, TW = tex.shape[:2]
    allp = []
    tris = []
    for P, uv, n in quads:
        Wp = to_world(P)
        nw = np.array([n[0], -n[1], -n[2]])
        nl = np.linalg.norm(nw)
        nw = nw / nl if nl > 0 else nw
        bright = min(1.0, 0.4 + 0.6 * (max(0, nw @ L0) + max(0, nw @ L1)))
        S = Wp @ V.T
        allp.append(S)
        tris.append((S, uv, bright))
    if not allp:
        return Image.fromarray(img.astype(np.uint8)), fit
    pts = np.concatenate(allp)
    if fit is None:
        mn, mx = pts[:, :2].min(0), pts[:, :2].max(0)
        span = max(mx - mn) * 1.08 + 1e-6
        center = (mn + mx) / 2
        fit = (center, span)
    center, span = fit
    sc = W / span

    def scr(S):
        return np.stack([(S[:, 0] - center[0]) * sc + W / 2, -(S[:, 1] - center[1]) * sc + H / 2, S[:, 2]], axis=-1)

    for S, uv, bright in tris:
        Q = scr(S)
        for (a, b, c) in ((0, 1, 2), (0, 2, 3)):
            p0, p1, p2 = Q[a], Q[b], Q[c]
            area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p1[1] - p0[1]) * (p2[0] - p0[0])
            if abs(area) < 1e-9:
                continue
            xmin = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
            xmax = min(W - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
            ymin = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
            ymax = min(H - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
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
            tx = np.clip((u * TW).astype(int), 0, TW - 1)
            ty = np.clip((v * TH).astype(int), 0, TH - 1)
            col = tex[ty, tx]
            ok = inside & (col[..., 3] > 25)
            sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
            ok &= z < sub
            if not ok.any():
                continue
            sub[ok] = z[ok]
            region = img[ymin:ymax + 1, xmin:xmax + 1]
            region[ok] = col[ok][:, :3] * bright
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), fit


def render(model, tex, layers=(), size=420, yaw=35, pitch=22, fit=None):
    mats = pose_matrices(model, layers)
    return rasterize(model_quads(model, mats), tex, size, yaw, pitch, fit)


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, len(text) * 7 + 8, 14], fill=(0, 0, 0))
    d.text((4, 1), text, fill=(255, 255, 255))
    return img


def preview(model, tex, out_path, anims=None, frames=6, size=300):
    """Writes a contact sheet: 4 rest views + one row per animation (frames sampled over its length)."""
    rest_views = [('front-left', 35, 18), ('left side', 90, 5), ('front', 0, 8), ('top', 0, 80), ('back-right', 215, 20)]
    tiles = []
    base_fit = None
    idle_layer = [(model.animations['idle'], 0.0, 1.0)] if 'idle' in model.animations else []
    for name, yaw, pitch in rest_views:
        im, fit = render(model, tex, idle_layer, size, yaw, pitch)
        tiles.append(label(im, name))
    anims = anims if anims is not None else list(model.animations.keys())
    rows = [tiles]
    # common fit for animation rows: compute from front-left rest
    _, afit = render(model, tex, (), size, 35, 18)
    c, s = afit
    afit = (c, s * 1.25)
    for an in anims:
        a = model.animations[an]
        row = []
        for k in range(frames):
            t = a.length * k / (frames - 1 if not a.loop else frames)
            layers = [(a, t, 1.0)]
            if an not in ('idle', 'death', 'sleep', 'trapped', 'stun') and 'idle' in model.animations and an not in ('walk', 'run', 'fly'):
                layers.insert(0, (model.animations['idle'], t, 1.0))
            im, _ = render(model, tex, layers, size, 35, 18, afit)
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
