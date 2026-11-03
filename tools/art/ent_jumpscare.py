"""Full-screen jump scare faces of the spirits (gui/jumpscare_<id>.png, 256x256, opaque, on black) and the medium's
dialogue portrait (gui/medium_portrait.png, 64x64).

Each face is painted as a relief: a height field (skull, sockets, mouth...) with albedo, wetness and emission layers,
lit by the camera flash with cast shadows, wet specular and subsurface in the shadows, then put through a photographic
post (grain, chromatic aberration, vignette, crushed blacks). Designed to match the models' features and palettes.
The relief helpers are copied from SOUNAN's s_jumpscare.py (the art worker owns that file).
usage: python3 tools/art/ent_jumpscare.py [id ...]
"""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from common import rng, fnoise, smoothstep, TEX_ROOT, PREVIEW, font, FONT_LABEL

N = 512                      # working resolution

def grid_uv(n=N):
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    return (x / (n - 1)) * 2 - 1, (y / (n - 1)) * 2 - 1

def g2(u, v, cx, cy, sx, sy, ang=0.0):
    c, s = math.cos(ang), math.sin(ang)
    du, dv = u - cx, v - cy
    a = (du * c + dv * s) / sx
    b = (-du * s + dv * c) / sy
    return np.exp(-(a * a + b * b))

def ell(u, v, cx, cy, sx, sy, ang=0.0):
    """Signed ellipse field: >0 inside (1 at centre)."""
    c, s = math.cos(ang), math.sin(ang)
    du, dv = u - cx, v - cy
    a = (du * c + dv * s) / sx
    b = (-du * s + dv * c) / sy
    return 1.0 - np.sqrt(a * a + b * b)

def blur(a, s):
    return ndimage.gaussian_filter(a, s, mode='nearest')

def noise(seed, beta=1.0, n=N):
    return fnoise(n, n, seed, beta=beta)

def rot_uv(u, v, ang, cx=0.0, cy=0.0):
    c, s = math.cos(ang), math.sin(ang)
    du, dv = u - cx, v - cy
    return cx + du * c + dv * s, cy - du * s + dv * c

class Relief:
    def __init__(self, seed):
        self.u, self.v = grid_uv()
        self.H = np.zeros((N, N), np.float32)         # height (towards the viewer)
        self.A = np.zeros((N, N, 3), np.float32)      # albedo 0..1
        self.M = np.zeros((N, N), np.float32)         # coverage (0 = background)
        self.WET = np.zeros((N, N), np.float32)       # specular strength
        self.ROUGH = np.full((N, N), 40.0, np.float32)  # specular exponent
        self.SSS = np.zeros((N, N), np.float32)       # how much shadows bleed red (skin)
        self.E = np.zeros((N, N, 3), np.float32)      # emission
        self.r = rng(seed)

    def paint(self, mask, color, alpha=1.0):
        m = np.clip(mask, 0, 1)[..., None] * alpha
        self.A = self.A * (1 - m) + np.asarray(color, np.float32)[None, None, :] / 255.0 * m

    def render(self, lights, ambient=0.06, amb_col=(0.55, 0.6, 0.7), shadow=True, bump=1.0, view_spec=True):
        H = self.H * bump
        gy, gx = np.gradient(H, 2.0 / N)
        n = np.stack([-gx, -gy, np.ones_like(H)], -1)
        n /= np.linalg.norm(n, axis=-1, keepdims=True)
        col = self.A * ambient * np.asarray(amb_col)[None, None, :]
        # cavity / ambient occlusion
        ao = np.clip(1.0 - np.clip(blur(H, 10) - H, 0, None) * 3.0, 0.25, 1.0)
        col *= ao[..., None]
        for (L, lc, power, spread) in lights:
            L = np.asarray(L, np.float32)
            L = L / np.linalg.norm(L)
            ndl = (n * L[None, None, :]).sum(-1)
            wrap = 0.18
            diff = np.clip((ndl + wrap) / (1 + wrap), 0, 1)
            sh = self.shadow(H, L) if shadow else 1.0
            # spotlight falloff from the image centre (the beam)
            k = power * spread
            lit = diff * sh * k
            sss = np.clip((1 - sh) * 0.35 + (1 - diff) * 0.25, 0, 1) * self.SSS * k
            lcv = np.asarray(lc, np.float32)[None, None, :]
            col += self.A * lit[..., None] * lcv + sss[..., None] * np.array([0.35, 0.04, 0.03]) * self.A.mean(-1, keepdims=True) * 2
            h = L + np.array([0, 0, 1.0])
            h /= np.linalg.norm(h)
            nh = np.clip((n * h[None, None, :]).sum(-1), 0, 1)
            spec = nh ** self.ROUGH * self.WET * sh * k
            col += spec[..., None] * lcv
        col += self.E
        col *= self.M[..., None]
        return col

    def shadow(self, H, L, steps=28, reach=0.35):
        """Height-field cast shadows along the light direction."""
        if abs(L[2]) > 0.995:
            return np.ones_like(H)
        dirxy = np.array([L[0], L[1]], np.float32)
        ln = np.linalg.norm(dirxy)
        dirxy /= max(ln, 1e-6)
        slope = L[2] / max(ln, 1e-6)          # height gained per unit of distance towards the light
        y, x = np.mgrid[0:N, 0:N].astype(np.float32)
        occl = np.zeros_like(H)
        for i in range(1, steps + 1):
            d = reach * i / steps
            px = x + dirxy[0] * d * N / 2
            py = y + dirxy[1] * d * N / 2
            hs = ndimage.map_coordinates(H, [py, px], order=1, mode='nearest')
            occl = np.maximum(occl, (hs - (H + d * slope)) * 18.0)
        return np.clip(1.0 - occl, 0.0, 1.0)

def post(col, seed, grain=0.06, ca=1.5, vign=0.9, gamma=1.0, tint=(1.0, 1.0, 1.0), crush=0.03, gain=1.0):
    r = rng(seed)
    c = np.clip(col * gain, 0, None)
    c = c / (1 + c * 0.25)                     # soft highlight roll-off
    c = np.clip((c - crush) / (1 - crush), 0, 1) ** gamma
    c *= np.asarray(tint)[None, None, :]
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    rad = np.hypot((xx - N / 2) / (N / 2), (yy - N / 2) / (N / 2))
    c *= np.clip(1 - (rad ** 2) * vign * 0.6, 0, 1)[..., None]
    if ca:
        shift = (rad * ca)
        out = c.copy()
        for ch, s in ((0, 1.0), (2, -1.0)):
            px = N / 2 + (xx - N / 2) * (1 + s * 0.004 * ca)
            py = N / 2 + (yy - N / 2) * (1 + s * 0.004 * ca)
            out[..., ch] = ndimage.map_coordinates(c[..., ch], [py, px], order=1, mode='nearest')
        c = out
    g = r.normal(0, grain, (N, N))
    g = blur(g, 0.7)
    c = c + g[..., None] * (0.35 + c * 0.65)
    return np.clip(c, 0, 1)

def strands(R, seed, n, region, color=(8, 8, 10), width=(1.0, 2.6), length=(0.5, 1.4), droop=0.5, wet=0.6,
            start_fn=None, angle=1.57, curl=0.5, under=None):
    """Draw hair strands as long thin wavy ribbons into albedo + height (they sit on top)."""
    r = rng(seed)
    img = Image.new('F', (N, N), 0.0)
    d = ImageDraw.Draw(img)
    hl = Image.new('F', (N, N), 0.0)
    dh = ImageDraw.Draw(hl)
    for i in range(n):
        if start_fn:
            x0, y0, a0 = start_fn(r)
        else:
            x0 = r.uniform(region[0], region[1])
            y0 = r.uniform(region[2], region[3])
            a0 = angle + r.normal(0, 0.25)
        ln = r.uniform(*length)
        w = r.uniform(*width)
        pts = []
        x, y, a = (x0 + 1) * N / 2, (y0 + 1) * N / 2, a0
        k = r.uniform(-curl, curl)
        steps = 40
        for s in range(steps):
            pts.append((x, y))
            a += k * 0.08 + r.normal(0, 0.04)
            a = a * (1 - droop * 0.02) + 1.5708 * droop * 0.02
            x += math.cos(a) * ln * N / 2 / steps
            y += math.sin(a) * ln * N / 2 / steps
        d.line(pts, fill=1.0, width=max(1, int(w * N / 256)))
        if r.random() < wet:
            dh.line([(p[0] - w * 0.6, p[1]) for p in pts[2:-6]], fill=1.0, width=1)
    m = np.asarray(img, np.float32)
    m = np.clip(blur(m, 0.6), 0, 1)
    h = np.asarray(hl, np.float32)
    R.paint(m, color)
    R.H = R.H + m * 0.04
    R.M = np.maximum(R.M, m)
    R.WET = np.maximum(R.WET * (1 - m), m * 0.25 + h * 0.6)
    R.ROUGH = np.where(m > 0.3, 60.0, R.ROUGH)
    R.SSS *= (1 - m)
    return m

def skin_albedo(R, base, var, seed, veins=0.0, vein_col=(70, 80, 110), blotch_col=None, blotch=0.0):
    nlo = noise(seed, 1.6)
    nmid = noise(seed + 1, 0.9)
    nhi = noise(seed + 2, 0.15)
    c = np.asarray(base, np.float32)[None, None, :] / 255.0
    c = c * (1 + nlo[..., None] * var * 0.6 + nmid[..., None] * var * 0.35 + nhi[..., None] * var * 0.25)
    if veins:
        rid = 1 - np.abs(noise(seed + 3, 1.3))
        rid = np.clip((rid - 0.86) * 9, 0, 1) * veins
        c = c * (1 - rid[..., None]) + np.asarray(vein_col)[None, None, :] / 255.0 * rid[..., None]
    if blotch and blotch_col is not None:
        b = np.clip((noise(seed + 4, 1.8) - 0.6) * 2, 0, 1) * blotch
        c = c * (1 - b[..., None]) + np.asarray(blotch_col)[None, None, :] / 255.0 * b[..., None]
    return np.clip(c, 0, 1)

def eyeball(R, u, v, cx, cy, rad, iris_r, pupil_r, sclera, iris, look=(0.0, 0.0), wet=1.6, depth=0.06):
    """A sphere with sclera / iris / pupil, set into the relief."""
    d2 = ((u - cx) ** 2 + (v - cy) ** 2) / rad ** 2
    inside = d2 < 1
    hb = np.sqrt(np.clip(1 - d2, 0, 1)) * rad * 0.9
    R.H = np.where(inside, np.maximum(R.H, R.H * 0.0 + hb + depth), R.H)
    du, dv = u - cx - look[0] * rad * 0.35, v - cy - look[1] * rad * 0.35
    rr = np.sqrt(du * du + dv * dv)
    col = np.where((rr < iris_r)[..., None], np.asarray(iris)[None, None, :] / 255.0, np.asarray(sclera)[None, None, :] / 255.0)
    col = np.where((rr < pupil_r)[..., None], np.array([0.0, 0.0, 0.0]), col)
    m = inside.astype(np.float32)
    R.A = np.where(inside[..., None], col, R.A)
    R.WET = np.where(inside, wet, R.WET)
    R.ROUGH = np.where(inside, 140.0, R.ROUGH)
    R.SSS = np.where(inside, 0.0, R.SSS)
    R.M = np.maximum(R.M, m)
    return inside

def face_relief(R, u, v, w=0.62, h=0.9, brow=0.06, sockets=0.14, cheek=0.06, hollow=0.05, nose=0.07, chin=0.04,
                eye_x=0.28, eye_y=-0.14):
    """A long, gaunt face as a height field in face coordinates (u right, v down, face spans |u|<w, |v|<h)."""
    head = np.clip(ell(u, v, 0.0, 0.0, w, h), 0, 1)
    H = np.sqrt(head) * 0.5
    H += g2(u, v, 0, eye_y - 0.2, w * 0.85, 0.09) * brow                         # brow ridge
    for sx in (-1, 1):
        H -= g2(u, v, sx * eye_x, eye_y, 0.19, 0.15) * sockets                    # eye sockets
        H += g2(u, v, sx * (eye_x + 0.12), eye_y + 0.24, 0.17, 0.08, sx * 0.4) * cheek   # cheekbones
        H -= g2(u, v, sx * (eye_x + 0.08), eye_y + 0.46, 0.15, 0.16) * hollow          # hollow cheeks
        H -= g2(u, v, sx * (w * 0.92), eye_y - 0.05, 0.1, 0.28) * 0.06                 # temples
    H += g2(u, v, 0, eye_y + 0.14, 0.055, 0.2) * nose                              # nose ridge
    H += g2(u, v, 0, eye_y + 0.33, 0.09, 0.06) * nose * 0.8                        # nose tip
    H += g2(u, v, 0, h * 0.78, 0.2, 0.1) * chin
    M = smoothstep(-0.015, 0.03, ell(u, v, 0.0, 0.0, w, h))
    return H, M

def finger(R, path, width, base_h, color, nail_col=(150, 140, 100), seed=0, nail_len=0.12, knuckles=(0.35, 0.68)):
    """A long finger along a polyline of (u, v) points (from the hand to the tip), shaded as a cylinder."""
    U, V = R.u, R.v
    pts = np.asarray(path, np.float32)
    seglen = np.hypot(*(pts[1:] - pts[:-1]).T)
    total = seglen.sum()
    best = np.full(U.shape, 9.0, np.float32)
    tpos = np.zeros(U.shape, np.float32)
    acc = 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        ab = b - a
        L2 = float(ab @ ab)
        t = np.clip(((U - a[0]) * ab[0] + (V - a[1]) * ab[1]) / L2, 0, 1)
        px, py = a[0] + ab[0] * t, a[1] + ab[1] * t
        dd = np.hypot(U - px, V - py)
        closer = dd < best
        best = np.where(closer, dd, best)
        tpos = np.where(closer, (acc + t * seglen[i]) / total, tpos)
        acc += seglen[i]
    wl = width * (1.0 - 0.25 * tpos)
    inside = best < wl
    prof = np.sqrt(np.clip(1 - (best / wl) ** 2, 0, 1))
    R.H = np.where(inside, base_h + prof * width * 0.9, R.H)
    col = np.asarray(color, np.float32) / 255.0
    nz = noise(seed, 0.5)
    c = col[None, None, :] * (0.9 + 0.12 * nz[..., None])
    for k in knuckles:
        kn = np.exp(-((tpos - k) / 0.025) ** 2)
        c = c * (1 - 0.3 * kn[..., None])
    nail = tpos > 1 - nail_len
    c = np.where(nail[..., None], np.asarray(nail_col, np.float32)[None, None, :] / 255.0, c)
    R.A = np.where(inside[..., None], c, R.A)
    R.M = np.where(inside, 1.0, R.M)
    R.WET = np.where(inside, np.where(nail, 0.9, 0.3), R.WET)
    R.ROUGH = np.where(inside, 50.0, R.ROUGH)
    R.SSS = np.where(inside, 0.55, R.SSS)
    return inside

def hair_clumps(R, seed, n, start_fn, length=(0.6, 1.4), width=(2.0, 5.0), droop=0.9, curl=0.3, col=(9, 9, 11), clip=None):
    """Wet hair: clumps of 3-6 nearly parallel strands with specular streaks."""
    r = rng(seed)
    img = Image.new('F', (N, N), 0.0)
    d = ImageDraw.Draw(img)
    hl = Image.new('F', (N, N), 0.0)
    dh = ImageDraw.Draw(hl)
    for i in range(n):
        x0, y0, a0 = start_fn(r)
        ln = r.uniform(*length)
        k = r.uniform(-curl, curl)
        for j in range(int(r.integers(3, 7))):
            ox, oy = r.normal(0, 0.012), r.normal(0, 0.012)
            w = r.uniform(*width) * (N / 512)
            x, y, a = (x0 + ox + 1) * N / 2, (y0 + oy + 1) * N / 2, a0 + r.normal(0, 0.05)
            pts = []
            for s_ in range(48):
                pts.append((x, y))
                a += k * 0.05 + r.normal(0, 0.02)
                a = a * (1 - droop * 0.03) + 1.5708 * droop * 0.03
                x += math.cos(a) * ln * N / 2 / 48
                y += math.sin(a) * ln * N / 2 / 48
            d.line(pts, fill=1.0, width=max(1, int(w)))
            if r.random() < 0.5:
                dh.line([(p[0] - w * 0.3, p[1]) for p in pts[3:-8]], fill=1.0, width=1)
    m = np.clip(blur(np.asarray(img, np.float32), 0.7), 0, 1)
    if clip is not None:
        m *= clip
    h = np.asarray(hl, np.float32) * (m > 0.3)
    R.paint(m, col)
    R.H = R.H + m * 0.035
    R.M = np.maximum(R.M, m)
    R.WET = R.WET * (1 - m) + m * (0.15 + h * 1.4)
    R.ROUGH = np.where(m > 0.3, 70.0, R.ROUGH)
    R.SSS *= (1 - m)
    return m

def hair_mass(R, mask, seed, flow_angle=None, col=(8, 8, 10), sheen=0.9, density=1.0):
    """A mass of wet hair: flow-aligned strand texture (streaks along the hair direction), dark, with specular
    streaks; `mask` 0..1 says where hair is; flow_angle (array or float, radians, 1.5708 = straight down)."""
    U, V = R.u, R.v
    if flow_angle is None:
        flow_angle = 1.5708
    fa = np.broadcast_to(np.asarray(flow_angle, np.float32), U.shape)
    # coordinate across the flow -> strands are stripes of constant cross-coordinate
    across = U * np.sin(fa) - V * np.cos(fa)
    along = U * np.cos(fa) + V * np.sin(fa)
    r = rng(seed)
    ph = blur(r.normal(0, 1, (N, N)), 6) * 3.0
    s1 = np.sin(across * 260 + ph * 4 + np.sin(along * 7) * 2)
    s2 = np.sin(across * 610 + ph * 9)
    strand = np.clip(0.5 + 0.35 * s1 + 0.25 * s2, 0, 1)
    clump = np.clip(0.5 + 0.5 * np.sin(across * 70 + ph * 2), 0, 1)
    m = np.clip(mask, 0, 1)
    tone = 0.6 + 0.4 * strand * clump
    base = np.asarray(col, np.float32)[None, None, :] / 255.0 * tone[..., None]
    R.A = R.A * (1 - m[..., None]) + base * m[..., None]
    R.H = R.H * (1 - m * 0.2) + m * (R.H * 0.2 + 0.03 * strand)
    R.M = np.maximum(R.M, m)
    R.WET = R.WET * (1 - m) + m * sheen * (strand * clump) ** 2
    R.ROUGH = np.where(m > 0.3, 90.0, R.ROUGH)
    R.SSS *= (1 - m)
    return m

def bloom(col, thr=0.85, radius=18, strength=0.9, streak=0.0):
    hi = np.clip(col - thr, 0, None)
    b = np.stack([blur(hi[..., c], radius) for c in range(3)], -1) * strength
    b += np.stack([blur(hi[..., c], radius * 0.3) for c in range(3)], -1) * strength * 0.6
    if streak:
        st = np.stack([ndimage.gaussian_filter(hi[..., c], (1.5, radius * 6), mode='nearest') for c in range(3)], -1)
        b += st * streak
    return col + b


# ================================================================================================ anatomy
class Face:
    """Parameters of a face in face coordinates (u right, v down; the face spans |u| < w, |v| < h)."""

    def __init__(self, **kw):
        self.w, self.h = 0.62, 0.86
        self.eye_x, self.eye_y = 0.25, -0.1
        self.socket = 0.11
        self.brow = 0.05
        self.nose_len, self.nose = 0.3, 0.12
        self.mouth_y, self.mouth_w = 0.4, 0.2
        self.lips = 0.03
        self.cheek, self.hollow = 0.05, 0.035
        self.chin = 0.05
        self.jaw_drop = 0.0
        self.__dict__.update(kw)


def face_height(u, v, f):
    """Height field of a face: skull curvature, brow ridge, sockets, nose (ridge, tip, alae, nostrils), philtrum,
    lips, cheekbones, nasolabial folds, chin. Returns H, mask, and a dict of feature masks."""
    e = ell(u, v, 0, f.jaw_drop * 0.5, f.w, f.h + f.jaw_drop * 0.5)
    H = np.sqrt(np.clip(e, 0, 1)) * 0.55
    ey, ex = f.eye_y, f.eye_x
    H += g2(u, v, 0, ey - 0.16, f.w * 0.72, 0.06) * f.brow
    H += g2(u, v, 0, ey - 0.42, f.w * 0.6, 0.2) * 0.02                     # forehead
    for sx in (-1, 1):
        H -= g2(u, v, sx * ex, ey, 0.16, 0.1) * f.socket
        H += g2(u, v, sx * (ex + 0.14), ey + 0.19, 0.15, 0.07, sx * 0.5) * f.cheek
        H -= g2(u, v, sx * (ex + 0.1), ey + 0.44, 0.13, 0.14) * f.hollow
        H -= g2(u, v, sx * (f.w * 0.94), ey - 0.04, 0.09, 0.26) * 0.05         # temples
        # nasolabial fold: from beside the nose wing down to past the mouth corner
        a = math.atan2(0.26, sx * 0.1)
        H -= g2(u, v, sx * 0.17, ey + f.nose_len + 0.1, 0.02, 0.13, sx * -0.35) * 0.025
    ny = ey + f.nose_len
    H += g2(u, v, 0, ey + f.nose_len * 0.45, 0.042, f.nose_len * 0.5) * f.nose
    H += g2(u, v, 0, ny, 0.06, 0.05) * f.nose * 0.85
    for sx in (-1, 1):
        H += g2(u, v, sx * 0.065, ny + 0.025, 0.04, 0.035) * f.nose * 0.55
    nostril = np.zeros_like(u)
    for sx in (-1, 1):
        nostril = np.maximum(nostril, g2(u, v, sx * 0.035, ny + 0.05, 0.022, 0.012))
    H -= nostril * 0.04
    my = f.mouth_y + f.jaw_drop
    H -= g2(u, v, 0, (ny + my) / 2, 0.025, (my - ny) * 0.35) * 0.012         # philtrum
    upper = g2(u, v, 0, f.mouth_y - 0.022, f.mouth_w, 0.02) * (1 + 0.3 * g2(u, v, 0, f.mouth_y - 0.03, 0.04, 0.02))
    lower = g2(u, v, 0, my + 0.03, f.mouth_w * 0.85, 0.028)
    H += (upper + lower) * f.lips
    H += g2(u, v, 0, f.h * 0.84 + f.jaw_drop, 0.17, 0.09) * f.chin
    M = smoothstep(-0.015, 0.03, e)
    feats = {'nostril': nostril, 'upper_lip': upper, 'lower_lip': lower}
    return H, M, feats


def skin(R, base, var, seed, pores=0.03, blotch_col=None, blotch=0.0, veins=0.0, vein_col=(80, 90, 120)):
    A = skin_albedo(R, base, var, seed, veins=veins, vein_col=vein_col, blotch_col=blotch_col, blotch=blotch)
    por = np.clip(noise(seed + 9, 0.05) - 1.2, 0, None) * pores
    return np.clip(A - por[..., None], 0, 1)


# ================================================================================================ shared pieces
def to_rgb(col, size=256):
    img = Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8), 'RGB').resize((size, size), Image.LANCZOS)
    return np.asarray(img, np.uint8)


def flash_spot(col, cx=0.5, cy=0.45, rx=0.62, ry=0.66, floor=0.16):
    """Flash falloff: the centre of the frame bright, the edges falling into black."""
    yy, xx = np.mgrid[0:N, 0:N]
    spot = np.exp(-(((xx - N * cx) / (N * rx)) ** 2 + ((yy - N * cy) / (N * ry)) ** 2))
    return col * (floor + (1 - floor) * spot)[..., None]


def grin(R, u, v, cy=0.36, width=0.47, rise=1.25, open_=0.14, teeth=(0.75, 0.72, 0.62), lip=(120, 92, 96), seed=1,
         lower_teeth=True, interior=(0.025, 0.0, 0.003)):
    """A smile stretched too wide (corners pulled up into the cheeks), split open in the middle."""
    curve = v - (cy - rise * u ** 2)
    across = np.clip(1 - (u / width) ** 2, 0, 1)
    gap = 0.012 + open_ * across ** 1.2
    inside = (curve > 0) & (curve < gap) & (np.abs(u) < width)
    R.H = np.where(inside, R.H - 0.12 * across, R.H)
    R.A = np.where(inside[..., None], np.asarray(interior, np.float32)[None, None, :], R.A)
    R.WET = np.where(inside, 0.8, R.WET)
    R.SSS = np.where(inside, 0.0, R.SSS)
    # upper teeth hanging from the lip line, separated
    rt = rng(seed)
    tx = -width + 0.04
    while tx < width - 0.04:
        tw = rt.uniform(0.02, 0.034)
        ln = rt.uniform(0.026, 0.05) * (0.55 + 0.45 * (1 - (tx / width) ** 2))
        tooth = inside & ((((u - tx) / (tw * 0.92)) ** 2 + ((curve - ln * 0.35) / (ln * 0.75)) ** 2) < 1.0)
        tint = np.asarray(teeth) * rt.uniform(0.72, 1.02) * np.array([1.0, rt.uniform(0.95, 1.0), rt.uniform(0.82, 0.95)])
        R.A[tooth] = tint
        R.H[tooth] += 0.1
        R.WET[tooth] = 1.2
        gumline = inside & (np.abs(u - tx) < tw * 0.95) & (curve < 0.006)
        R.A[gumline] = [0.32, 0.18, 0.18]
        if lower_teeth and abs(tx) < width * 0.6:
            low = inside & (np.abs(u - tx - tw) < tw * 0.7) & (curve > gap - rt.uniform(0.02, 0.035))
            R.A[low] = np.asarray(teeth) * rt.uniform(0.6, 0.85)
        tx += tw * 2 + rt.uniform(0.002, 0.008)
    # lips: thin, pressed into a line along both edges
    up = np.clip(1 - np.abs(curve + 0.006) / 0.012, 0, 1) * (np.abs(u) < width + 0.02)
    lo = np.clip(1 - np.abs(curve - gap - 0.01) / 0.016, 0, 1) * (np.abs(u) < width)
    R.paint(np.maximum(up, lo) * 0.85, lip)
    for sx in (-1, 1):                                       # creases at the corners, dimples of the fixed smile
        R.paint(g2(u, v, sx * (width + 0.02), cy - rise * width ** 2 - 0.01, 0.03, 0.012, sx * -1.0) * 0.7, (90, 90, 92))
        R.H -= g2(u, v, sx * (width - 0.05), cy - rise * width ** 2 + 0.06, 0.05, 0.03) * 0.03
    return inside


def hollow_eye(R, u, v, ex, ey, rx, ry, depth=0.09, pin=0.5, ang=0.0):
    """An eye that is only a black hollow, a pinpoint of light deep inside."""
    orb = ell(u, v, ex, ey, rx, ry, ang)
    inside = orb > 0
    R.H = np.where(inside, R.H - depth * np.sqrt(np.clip(orb, 0, 1)), R.H)
    R.A = np.where(inside[..., None], np.array([0.006, 0.006, 0.008]), R.A)
    R.WET = np.where(inside, 0.05, R.WET)
    R.SSS = np.where(inside, 0.0, R.SSS)
    rim = (orb > -0.25) & ~inside
    R.paint(np.clip(1 + orb / 0.25, 0, 1) * rim * 0.6, (60, 60, 66))
    if pin:
        p = ((u - ex) ** 2 + (v - ey) ** 2) < (0.011) ** 2
        R.E[p] = [pin, pin * 0.97, pin * 0.95]
    return inside


def save_gui(name, rgb):
    d = os.path.join(TEX_ROOT, 'gui')
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, name + '.png')
    Image.fromarray(rgb, 'RGB').convert('RGBA').save(p, optimize=True)
    return p


# ================================================================================================ 給仕 the waitress
def lit(R, key, fill=None, amb=0.05, amb_col=(0.55, 0.6, 0.7)):
    lights = [key] + ([fill] if fill else [])
    return R.render(lights, ambient=amb, amb_col=amb_col)


def mouth_hole(R, u, v, cx, cy, rx, ry, lip=(150, 120, 122), lipw=0.025, teeth=None, inner=(0.02, 0.0, 0.004), ang=0.0,
               depth=0.16):
    """An open mouth: a dark hole sinking into the face, lips round its rim, optional upper teeth."""
    e = ell(u, v, cx, cy, rx, ry, ang)
    inside = e > 0
    R.H = np.where(inside, R.H - depth * np.sqrt(np.clip(e, 0, 1)), R.H)
    shade = np.clip(e * 3, 0, 1)
    col = np.asarray(inner, np.float32)[None, None, :] * (0.4 + 0.6 * (1 - shade[..., None])) + \
        np.array([0.12, 0.02, 0.03]) * (1 - shade[..., None]) * 0.6
    R.A = np.where(inside[..., None], col, R.A)
    R.WET = np.where(inside, 0.6, R.WET)
    R.SSS = np.where(inside, 0.0, R.SSS)
    rim = np.clip(1 - np.abs(e) / (lipw / max(rx, ry) * 1.0), 0, 1)
    R.paint(rim * 0.85, lip)
    R.H = R.H + rim * 0.025
    if teeth:
        top = inside & (v < cy - ry * 0.55) & (np.abs(u - cx) < rx * 0.6)
        k = np.floor((u - cx) / (rx * 0.18))
        tooth = top & (np.abs(((u - cx) / (rx * 0.18)) - k - 0.5) < 0.38)
        R.A[tooth] = np.asarray(teeth, np.float32)
        R.H[tooth] += 0.06
        R.WET[tooth] = 1.0
    return inside


def js_child_p():
    """'Mi-tsu-ke-ta' - right in front of you and a little below: the hat brim, the black fringe over the eyes with one
    eye looking out between two strands, the mouth a wide black O."""
    R = Relief(5101)
    U0, V0 = R.u, R.v
    U, V = rot_uv(U0, V0, 0.2, 0.0, 0.0)
    zoom = 1.9
    u, v = (U + 0.1) / zoom, (V - 0.05) / zoom
    f = Face(w=0.66, h=0.76, eye_x=0.24, eye_y=-0.08, socket=0.07, brow=0.02, nose_len=0.17, nose=0.05, mouth_y=0.33,
             mouth_w=0.12, lips=0.02, cheek=0.06, hollow=0.0, chin=0.04)
    H, M, ft = face_height(u, v, f)
    for sx in (-1, 1):                                       # round child cheeks
        H += g2(u, v, sx * 0.32, 0.14, 0.2, 0.18) * 0.07
    R.H, R.M = H, M
    R.A = skin(R, (208, 207, 200), 0.07, 5102, blotch_col=(188, 182, 182), blotch=0.3, veins=0.25, vein_col=(130, 140, 162))
    R.paint(g2(u, v, 0, 0.62, 0.4, 0.18) * 0.25, (150, 150, 155))         # under the jaw
    R.SSS = M * 0.55
    R.WET = M * 0.3
    R.ROUGH[:] = 24.0
    R.paint(ft['nostril'] * 0.75, (110, 100, 104))
    # the mouth: a wide black O, gums and four small teeth at the top
    inside = mouth_hole(R, u, v, 0.0, 0.36, 0.165, 0.19, lip=(178, 150, 154), lipw=0.022, depth=0.22)
    for tx in (-0.075, -0.025, 0.025, 0.075):
        tooth = inside & (np.abs(u - tx) < 0.019) & (v < 0.215 - 0.6 * (u - tx) ** 2 + 0.01 * abs(tx) * 10) & (v > 0.16)
        R.A[tooth] = [0.74, 0.72, 0.66]
        R.H[tooth] += 0.05
        R.WET[tooth] = 1.2
    gum = inside & (v < 0.2) & (v > 0.17)
    R.paint(gum * 0.6, (120, 60, 66))
    tongue = inside & (ell(u, v, 0.0, 0.5, 0.11, 0.06) > 0)
    R.paint(tongue * 0.35, (90, 40, 46))
    # the black bob: a straight fringe ending in strand tips over the eyes, the sides down past the jaw
    rr = rng(5104)
    tips = np.zeros_like(u)
    for k in range(60):
        x0 = rr.uniform(-0.75, 0.75)
        ln = rr.uniform(0.0, 0.07)
        w_ = rr.uniform(0.006, 0.014)
        tips = np.maximum(tips, ((np.abs(u - x0) < w_ * (1 - np.clip((v - 0.0) / (ln + 1e-3), 0, 1))) & (v > -0.02) & (v < ln)).astype(np.float32))
    gap = np.clip(1 - np.abs(u - 0.2) / (0.035 + 0.03 * np.clip((v + 0.25) * 2.5, 0, 1)), 0, 1) * (v > -0.32)
    fringe = np.clip((v < 0.0).astype(np.float32) + tips, 0, 1) * (1 - np.clip(gap * 2.0, 0, 1))
    side_w = 0.5 + 0.03 * np.sin(v * 20)
    sides = ((np.abs(u) > side_w) & (v < 0.64)).astype(np.float32)
    mask = blur(np.clip(fringe + sides, 0, 1), 0.8)
    flow = 1.5708 + 0.25 * np.tanh(u * 2.0) * (v < -0.2)
    hair_mass(R, mask, 5103, flow_angle=flow, col=(10, 10, 12), sheen=1.4)
    R.H = R.H + mask * (0.06 + 0.03 * np.sin(u * 140))
    # the one eye in the gap of the fringe: dark, wet, a single point of light; strands still over it
    e = ell(u, v, 0.2, -0.085, 0.05, 0.036)
    inside_e = (e > 0) & (gap > 0.2)
    R.A = np.where(inside_e[..., None], np.array([0.05, 0.04, 0.04]), R.A)
    R.A = np.where((inside_e & (np.abs(u - 0.2) > 0.032))[..., None], np.array([0.55, 0.52, 0.48]), R.A)
    R.WET = np.where(inside_e, 2.5, R.WET)
    R.ROUGH = np.where(inside_e, 180.0, R.ROUGH)
    R.SSS = np.where(inside_e, 0.0, R.SSS)
    pin = ((u - 0.212) ** 2 + (v + 0.097) ** 2) < 0.009 ** 2
    R.E[pin] = [1.8, 1.8, 1.8]
    strand = (np.abs(u - 0.2 + 0.4 * (v + 0.1)) < 0.006) & (v > -0.2) & (v < 0.0)
    R.A[strand] = [0.03, 0.03, 0.035]
    # the hat brim: yellow, at the lens, out of focus
    brim = (V < -0.62 + 0.12 * U ** 2)
    R.A[brim] = [0.8, 0.62, 0.13]
    R.H[brim] = 0.9
    R.M[brim] = 1.0
    R.WET[brim] = 0.1
    R.SSS[brim] = 0.0
    under = brim & (V > -0.74 + 0.12 * U ** 2)
    R.A[under] = [0.42, 0.32, 0.07]
    key = ((0.35, 0.5, 1.0), (1.0, 0.97, 0.92), 1.25, 1.0)            # the flash held low and to the right
    fill = ((-0.9, -0.2, 0.4), (0.4, 0.5, 0.7), 0.1, 1.0)
    col = lit(R, key, fill)
    bl = blur_rgb(col, 8.0)
    bm = blur((V < -0.62 + 0.12 * U ** 2).astype(np.float32), 6)[..., None]
    col = col * (1 - bm) + bl * bm
    col = flash_spot(col, 0.56, 0.5, 0.62, 0.66, floor=0.12)
    return to_rgb(post(col, 5109, grain=0.075, ca=2.4, vign=1.1, gamma=1.15, tint=(0.95, 1.0, 1.05), crush=0.045))


def blur_rgb(c, s):
    return np.stack([blur(c[..., k], s) for k in range(3)], -1)


def crack_mask(seed, cx, cy, n_rad=12, rings=(0.12, 0.26, 0.45), reach=(0.3, 0.9), width=2, ring_w=1, size=N):
    """A spider web of cracks (0..1 mask) around (cx, cy) in -1..1 coordinates."""
    img = Image.new('L', (size, size), 0)
    d = ImageDraw.Draw(img)
    r = rng(seed)
    px, py = (cx + 1) * size / 2, (cy + 1) * size / 2
    for k in range(n_rad):
        a = k / n_rad * 2 * math.pi + r.normal(0, 0.2)
        x, y = px, py
        pts = [(x, y)]
        ln = r.uniform(*reach) * size / 2
        steps = int(r.integers(5, 10))
        for s_ in range(steps):
            a += r.normal(0, 0.18)
            x += math.cos(a) * ln / steps
            y += math.sin(a) * ln / steps
            pts.append((x, y))
            if r.random() < 0.25:                                  # branches
                b = a + r.choice([-1, 1]) * r.uniform(0.4, 0.9)
                d.line([(x, y), (x + math.cos(b) * ln * 0.25, y + math.sin(b) * ln * 0.25)], fill=200, width=max(1, width - 1))
        d.line(pts, fill=255, width=width)
    for rr in rings:
        R_ = rr * size / 2
        k_n = 22
        for k in range(k_n):
            if r.random() < 0.35:
                continue
            a0 = k / k_n * 2 * math.pi
            a1 = a0 + 2 * math.pi / k_n * r.uniform(0.7, 1.0)
            j0, j1 = r.uniform(0.85, 1.12), r.uniform(0.85, 1.12)
            d.line([(px + math.cos(a0) * R_ * j0, py + math.sin(a0) * R_ * j0),
                    (px + math.cos(a1) * R_ * j1, py + math.sin(a1) * R_ * j1)], fill=180, width=ring_w)
    return np.asarray(img, np.float32) / 255.0


def strand_mask(seed, n, start, angle, length, width=(1.0, 2.5), droop=0.3, curl=0.3, size=N, clump=(3, 6)):
    """Wet hair as clumps of thin strands (0..1 mask); start(r) -> (x, y) in -1..1."""
    img = Image.new('F', (size, size), 0.0)
    d = ImageDraw.Draw(img)
    r = rng(seed)
    for i in range(n):
        x0, y0 = start(r)
        a0 = angle + r.normal(0, 0.12)
        ln = r.uniform(*length)
        k = r.uniform(-curl, curl)
        for j in range(int(r.integers(*clump))):
            x, y, a = (x0 + r.normal(0, 0.01) + 1) * size / 2, (y0 + r.normal(0, 0.01) + 1) * size / 2, a0 + r.normal(0, 0.04)
            pts = []
            for s_ in range(40):
                pts.append((x, y))
                a += k * 0.05 + r.normal(0, 0.02)
                a = a * (1 - droop * 0.03) + 1.5708 * droop * 0.03
                x += math.cos(a) * ln * size / 2 / 40
                y += math.sin(a) * ln * size / 2 / 40
            d.line(pts, fill=1.0, width=max(1, int(r.uniform(*width) * size / 512)))
    return np.clip(blur(np.asarray(img, np.float32), 0.6), 0, 1)


def wet_hair(R, m, col=(8, 8, 10), sheen=1.1, seed=1):
    hl = np.clip(m - blur(m, 2.0), 0, 1)
    R.paint(m, col)
    R.H = R.H + m * 0.05
    R.M = np.maximum(R.M, m)
    R.WET = R.WET * (1 - m) + m * (0.12 + hl * 0.9) * sheen
    R.ROUGH = np.where(m > 0.3, 90.0, R.ROUGH)
    R.SSS = R.SSS * (1 - m)


def js_waitress_p():
    """Her head rolled over onto her shoulder at the lens: the hollows for eyes with a pinpoint deep inside, the polite
    smile stretched past the cheeks and split open; her hair pulled back into the net; a loose strand across."""
    R = Relief(4101)
    U0, V0 = R.u, R.v
    roll = -1.4
    U, V = rot_uv(U0, V0, roll, 0.0, 0.0)
    zoom = 1.9
    u, v = (U + 0.02) / zoom, (V + 0.33) / zoom
    f = Face(w=0.58, h=0.8, eye_x=0.23, eye_y=-0.12, socket=0.16, brow=0.03, nose_len=0.24, nose=0.08, mouth_y=0.4,
             mouth_w=0.36, lips=0.012, cheek=0.08, hollow=0.02, chin=0.04)
    H, M, ft = face_height(u, v, f)
    R.H, R.M = H, M
    R.A = skin(R, (196, 196, 188), 0.08, 4102, veins=0.4, vein_col=(120, 132, 156), blotch_col=(172, 168, 162), blotch=0.3)
    R.SSS = M * 0.6
    R.WET = M * 0.35
    R.ROUGH[:] = 30.0
    R.paint(ft['nostril'] * 0.7, (110, 106, 108))
    for ex in (-0.23, 0.23):
        R.paint(g2(u, v, ex, -0.12, 0.17, 0.12) * 0.5, (96, 96, 104))
        R.paint(g2(u, v, ex, -0.06, 0.12, 0.05) * 0.5, (70, 70, 80))
        hollow_eye(R, u, v, ex, -0.12, 0.105, 0.075, depth=0.14, pin=0.7, ang=0.1 if ex > 0 else -0.1)
    grin(R, u, v, cy=0.47, width=0.4, rise=0.95, open_=0.17, seed=4103, lip=(122, 96, 102))
    # hair: dark, pulled back hard from the hairline into the net
    hairline = smoothstep(0.03, -0.03, v + 0.5 - 0.42 * u ** 2 + 0.02 * np.sin(u * 40))
    outside = smoothstep(-0.01, 0.04, -ell(u, v, 0, 0, 0.58, 0.8)) * (v < 0.3)
    hm = np.clip(hairline + outside, 0, 1)
    hair_mass(R, hm, 4104, flow_angle=-1.5708 + 1.1 * np.tanh(u * 2.0), col=(18, 13, 12), sheen=0.7)
    strand = strand_mask(4105, 1, lambda r: (0.42, -0.8), 2.25, (1.0, 1.1), width=(2.5, 3.5), droop=0.25,
                         curl=0.3, clump=(3, 4))
    wet_hair(R, strand * 0.9, col=(14, 11, 10), sheen=0.3)
    key = ((0.95, -0.55, 0.55), (1.0, 0.97, 0.93), 1.45, 1.0)          # the flash held off to one side
    fill = ((-0.8, 0.4, 0.4), (0.45, 0.55, 0.72), 0.08, 1.0)
    col = lit(R, key, fill, amb=0.03)
    col = flash_spot(col, 0.6, 0.45, 0.58, 0.6, floor=0.06)
    return to_rgb(post(col, 4109, grain=0.08, ca=2.6, vign=1.15, gamma=1.18, tint=(0.95, 1.0, 1.05), crush=0.05))


def js_visitor_p():
    """The cracked visor a hand's breadth away: the flash blooming on the smoked glass, a web of cracks, and through
    the missing shard one wide eye in wet skin, staring."""
    R = Relief(4201)
    U, V = rot_uv(R.u, R.v, -0.18, 0.0, 0.0)
    # visor: a gently curved dark glass filling the frame; the white shell at the corners
    visor = ell(U, V, 0.0, 0.0, 1.35, 0.92) > 0
    R.H = np.where(visor, 0.6 + 0.25 * np.clip(ell(U, V, 0.0, 0.0, 1.35, 0.92), 0, 1), 0.3)
    R.M[:] = 1.0
    R.A[:] = [0.75, 0.73, 0.68]
    R.A[visor] = [0.03, 0.035, 0.045]
    seal = (ell(U, V, 0.0, 0.0, 1.35, 0.92) > -0.06) & ~visor
    R.A[seal] = [0.02, 0.02, 0.02]
    R.WET[:] = 0.6
    R.WET[visor] = 3.0
    R.ROUGH[:] = 30.0
    R.ROUGH[visor] = 220.0
    R.SSS[:] = 0.0
    # the missing shard: an irregular hole, the face behind it
    hole_f = ell(U, V, -0.18, 0.1, 0.46, 0.33, 0.3) + 0.08 * noise(4202, 0.9)
    hole = hole_f > 0
    f = Face(w=0.62, h=0.86, eye_x=0.25, eye_y=-0.06)
    fu, fv = (U - 0.1) / 1.6, (V - 0.18) / 1.6
    Hf, Mf, ftf = face_height(fu, fv, f)
    R.H = np.where(hole, Hf * 0.6, R.H)
    skin_a = skin(R, (176, 168, 160), 0.08, 4203, blotch_col=(160, 150, 150), blotch=0.3, veins=0.3, vein_col=(110, 120, 150))
    R.A = np.where(hole[..., None], skin_a, R.A)
    R.WET = np.where(hole, 1.4, R.WET)
    R.ROUGH = np.where(hole, 50.0, R.ROUGH)
    R.SSS = np.where(hole, 0.05, R.SSS)
    ex = -0.25 * 1.6 + 0.1
    eyeball(R, U, V, ex, -0.06 * 1.6 + 0.18, 0.16, 0.06, 0.018, (214, 204, 194), (52, 38, 30), depth=0.02)
    eyem = (ell(U, V, ex, -0.06 * 1.6 + 0.18, 0.14, 0.14) > 0) & hole
    rid = 1 - np.abs(noise(4204, 1.3))
    veins = np.clip((rid - 0.9) * 10, 0, 1) * eyem * (ell(U, V, ex, -0.06 * 1.6 + 0.18, 0.15, 0.15) < 0.75)
    R.paint(veins * 0.7, (150, 46, 44))
    R.paint(np.clip(ell(U, V, ex, 0.12, 0.22, 0.12), 0, 1) * hole * 0.35, (150, 130, 126))   # the lower lid
    edge = np.clip(1 - np.abs(hole_f) / 0.025, 0, 1)
    R.A = R.A * (1 - edge[..., None]) + np.array([0.75, 0.82, 0.86]) * edge[..., None]
    R.H = R.H + edge * 0.1
    # cracks from the impact point, catching the flash
    cm = crack_mask(4205, 0.42, -0.32, n_rad=16, rings=(0.1, 0.22, 0.4, 0.62, 0.85), reach=(0.6, 1.5), width=4)
    cm = cm * visor * (1 - hole)
    R.A = R.A * (1 - cm[..., None] * 0.9) + np.array([0.7, 0.78, 0.82]) * cm[..., None] * 0.9
    R.H = R.H + cm * 0.02
    R.WET = R.WET * (1 - cm) + cm * 0.3
    frost = blur((ell(U, V, 0.42, -0.32, 0.12, 0.1) > 0).astype(np.float32), 3) * visor
    R.paint(frost * 0.7, (150, 160, 166))
    # water beading on the visor
    rr = rng(4206)
    for k in range(70):
        x, y = rr.uniform(-1, 1), rr.uniform(-1, 1)
        rad = rr.uniform(0.008, 0.022)
        dm = (((U - x) ** 2 + (V - y) ** 2) < rad ** 2) & visor
        R.H[dm] += 0.05
        R.WET[dm] = 4.0
        R.A[dm] = R.A[dm] * 0.7 + 0.08
    key = ((0.25, -0.35, 1.0), (1.0, 0.98, 0.95), 1.2, 1.0)
    col = lit(R, key, ((-0.6, 0.5, 0.6), (0.4, 0.5, 0.7), 0.1, 1.0), amb=0.04)
    col = col + R.E
    yy, xx = np.mgrid[0:N, 0:N]
    glare = np.exp(-(((xx - N * 0.62) / (N * 0.07)) ** 2 + ((yy - N * 0.3) / (N * 0.05)) ** 2)) * visor
    col = col + glare[..., None] * np.array([1.6, 1.6, 1.7])
    col = bloom(col, thr=0.9, radius=16, strength=0.9)
    col = flash_spot(col, 0.5, 0.48, 0.7, 0.7, floor=0.18)
    return to_rgb(post(col, 4209, grain=0.07, ca=2.6, vign=1.1, gamma=1.12, tint=(0.93, 0.99, 1.07), crush=0.04))


def js_chef_p():
    """The burnt face looming over you under the sooty toque: charcoal skin split by cracks that glow, ember eyes,
    teeth bared with no lips left, the throat glowing as he roars; smoke; the cleaver's edge."""
    R = Relief(4301)
    U, V = rot_uv(R.u, R.v, 0.08, 0.0, 0.0)
    zoom = 1.6
    u, v = U / zoom, (V - 0.2) / zoom
    f = Face(w=0.66, h=0.84, eye_x=0.25, eye_y=-0.12, socket=0.17, brow=0.09, nose_len=0.22, nose=0.06, mouth_y=0.36,
             mouth_w=0.22, lips=0.0, cheek=0.08, hollow=0.05, chin=0.06, jaw_drop=0.1)
    H, M, ft = face_height(u, v, f)
    pts = np.stack([u * 5.5, v * 5.5, np.zeros_like(u)], -1)
    import ent_chef
    cr = ent_chef.voronoi_cracks(pts, 101, 1.0)
    crack = np.clip(1 - cr / 0.05, 0, 1)
    cr2 = ent_chef.voronoi_cracks(pts * 2.3 + 5.0, 7, 1.0)
    fine = np.clip(1 - cr2 / 0.05, 0, 1)
    hot = np.clip(noise(4302, 1.2) * 0.9, 0, 1)
    R.H = H - crack * 0.04 - fine * 0.015 + noise(4303, 0.6) * 0.012
    R.M = M
    char = 0.06 + 0.04 * np.clip(noise(4304, 0.8), -1, 1) - fine * 0.03
    R.A = np.stack([char * 1.15, char, char * 0.9], -1)
    R.WET = M * 0.35
    R.ROUGH[:] = 22.0
    R.SSS[:] = 0.0
    em = crack * np.clip((hot - 0.15) * 2.0, 0, 1) * M
    R.E = np.stack([em * 1.4, em * 0.45, em * 0.08], -1)
    # sockets with ember eyes
    for ex in (-0.25, 0.25):
        e = ell(u, v, ex, -0.12, 0.1, 0.07)
        ins = e > 0
        R.H = np.where(ins, R.H - 0.1 * np.sqrt(np.clip(e, 0, 1)), R.H)
        R.A[ins] = [0.01, 0.005, 0.0]
        core = np.exp(-(((u - ex) / 0.03) ** 2 + ((v + 0.12) / 0.025) ** 2))
        glow = np.exp(-(((u - ex) / 0.08) ** 2 + ((v + 0.12) / 0.06) ** 2))
        R.E += np.stack([core * 3.0 + glow * 0.9, core * 2.4 + glow * 0.35, core * 1.0 + glow * 0.05], -1)
    # the nose burnt to a stub; the mouth open with the teeth bared, the throat glowing
    R.paint(ft['nostril'] * 1.0, (4, 2, 2))
    inside = mouth_hole(R, u, v, 0.0, 0.43, 0.26, 0.17, lip=(30, 18, 14), lipw=0.02, depth=0.2)
    rt = rng(4305)
    tx = -0.21
    while tx < 0.21:
        tw = rt.uniform(0.018, 0.026)
        if rt.random() > 0.12:
            top = inside & (np.abs(u - tx) < tw * 0.85) & (v < 0.42 - 0.14 * 0.55 + rt.uniform(0.0, 0.03))
            R.A[top] = np.array([0.42, 0.36, 0.22]) * rt.uniform(0.6, 1.0)
            R.H[top] += 0.05
            bot = inside & (np.abs(u - tx - 0.01) < tw * 0.75) & (v > 0.42 + 0.14 * 0.5 - rt.uniform(0.0, 0.03))
            R.A[bot] = np.array([0.36, 0.3, 0.18]) * rt.uniform(0.5, 0.9)
        tx += tw * 2 + rt.uniform(0.0, 0.006)
    throat = np.exp(-((u / 0.12) ** 2 + ((v - 0.44) / 0.07) ** 2)) * inside
    R.E += np.stack([throat * 1.6, throat * 0.5, throat * 0.05], -1)
    # the toque's sooty band across the top
    band = V < -0.6 + 0.05 * np.sin(U * 3)
    pleat = 0.5 + 0.5 * np.sin(U * 40)
    R.A[band] = (np.array([0.62, 0.6, 0.56])[None, :] * (0.65 + 0.35 * pleat[band][:, None]))
    R.H[band] = 0.85
    R.M[band] = 1.0
    R.E[band] = 0.0
    soot = band & (V > -0.72)
    R.A[soot] *= 0.35
    # the cleaver's edge entering bottom right
    blade = (U + V * 0.55 > 1.05) & (V > 0.25)
    R.A[blade] = [0.3, 0.32, 0.34]
    R.H[blade] = 0.95
    R.M[blade] = 1.0
    R.E[blade] = 0.0
    R.WET[blade] = 2.0
    R.ROUGH[blade] = 120.0
    edge_l = blade & (U + V * 0.55 < 1.1)
    R.A[edge_l] = [0.85, 0.86, 0.86]
    key = ((-0.45, -0.35, 1.0), (0.75, 0.8, 0.9), 1.1, 1.0)
    col = lit(R, key, None, amb=0.04)
    col = col + R.E * M[..., None]
    col = bloom(col, thr=0.55, radius=12, strength=0.9)
    smoke = np.clip(noise(4306, 1.6) * 0.5 + 0.3, 0, 1) * np.clip(-V, 0, 1) * 0.25
    col = col * (1 - smoke[..., None]) + smoke[..., None] * np.array([0.25, 0.24, 0.23])
    col = flash_spot(col, 0.5, 0.5, 0.7, 0.72, floor=0.25)
    return to_rgb(post(col, 4309, grain=0.07, ca=2.0, vign=1.0, gamma=1.08, tint=(1.05, 0.98, 0.9), crush=0.04))


def js_mirror_woman_p():
    """Right behind the glass: two heavy curtains of wet hair, the grey face between them, one eye wide under a heavy
    lid (the other lost under a clump), the mouth stretched far too long in a scream; the glass cracking."""
    R = Relief(4501)
    U, V = rot_uv(R.u, R.v, -0.05, 0.0, 0.0)
    zoom = 1.45
    u, v = (U + 0.02) / zoom, (V - 0.04) / zoom
    f = Face(w=0.56, h=0.86, eye_x=0.21, eye_y=-0.2, socket=0.13, brow=0.03, nose_len=0.22, nose=0.07, mouth_y=0.22,
             mouth_w=0.2, lips=0.01, cheek=0.04, hollow=0.07, chin=0.03, jaw_drop=0.2)
    H, M, ft = face_height(u, v, f)
    R.H, R.M = H, M
    R.A = skin(R, (148, 158, 166), 0.1, 4502, veins=0.7, vein_col=(66, 80, 118), blotch_col=(118, 128, 140), blotch=0.45)
    R.SSS = M * 0.15
    R.WET = M * 1.3
    R.ROUGH[:] = 60.0
    R.paint(ft['nostril'] * 0.8, (54, 60, 72))
    ex = 0.21
    R.paint(g2(u, v, ex, -0.2, 0.15, 0.11) * 0.55, (64, 68, 82))
    eyeball(R, u, v, ex, -0.2, 0.085, 0.04, 0.012, (200, 196, 188), (34, 30, 28), depth=0.015)
    em = ell(u, v, ex, -0.2, 0.085, 0.085) > 0
    rid = 1 - np.abs(noise(4503, 1.3))
    R.paint(np.clip((rid - 0.9) * 10, 0, 1) * em * 0.8, (160, 56, 56))
    almond = ell(u, v, ex, -0.2, 0.085, 0.05)                 # lids close over the ball: an almond, not a disc
    lids = (ell(u, v, ex, -0.2, 0.1, 0.1) > 0) & (almond < 0)
    skin_here = skin(R, (148, 158, 166), 0.1, 4502)
    R.A = np.where(lids[..., None], skin_here * 0.82, R.A)
    R.H = np.where(lids, R.H + 0.03, R.H)
    R.WET = np.where(lids, 1.0, R.WET)
    R.ROUGH = np.where(lids, 60.0, R.ROUGH)
    upper = (almond > -0.12) & (almond < 0.04) & (v < -0.2)
    R.paint(upper * 0.9, (20, 22, 28))
    mouth_hole(R, u, v, 0.02, 0.4, 0.13, 0.3, lip=(62, 76, 116), lipw=0.02, depth=0.26)
    # the hair: two heavy wet curtains, ragged inner edges, a clump over her right eye, loose strands across
    edge_l = -0.3 + 0.05 * np.sin(v * 13) + 0.03 * np.sin(v * 37 + 1.0)
    edge_r = 0.4 + 0.04 * np.sin(v * 11 + 2.0) + 0.03 * np.sin(v * 31)
    curtains = ((u < edge_l) | (u > edge_r) | (v < -0.48 + 0.1 * u)).astype(np.float32)
    clump = strand_mask(4504, 9, lambda r: (r.uniform(-0.55, -0.15), -1.1), 1.5708, (1.6, 2.2), width=(4.0, 8.0),
                        droop=0.85, curl=0.06, clump=(4, 7))
    loose = strand_mask(4507, 5, lambda r: (r.uniform(-0.1, 0.5), -1.1), 1.6, (1.3, 2.1), width=(1.5, 2.5), droop=0.8,
                        curl=0.15, clump=(2, 3))
    hm = np.clip(blur(curtains, 2.0) + clump + loose * 0.85, 0, 1)
    wet_hair(R, hm, col=(5, 6, 8), sheen=0.5)
    key = ((0.35, -0.35, 1.0), (0.82, 0.9, 1.0), 1.2, 1.0)
    col = lit(R, key, ((-0.7, 0.3, 0.5), (0.35, 0.45, 0.6), 0.1, 1.0), amb=0.05)
    cm = crack_mask(4505, -0.6, -0.5, n_rad=8, rings=(0.12, 0.3), reach=(0.6, 1.3), width=2)
    col = col * (1 - cm[..., None] * 0.2) + cm[..., None] * np.array([0.6, 0.68, 0.75]) * 0.35
    col = flash_spot(col, 0.55, 0.45, 0.62, 0.7, floor=0.12)
    return to_rgb(post(col, 4509, grain=0.075, ca=2.6, vign=1.1, gamma=1.15, tint=(0.88, 0.97, 1.1), crush=0.045))


def js_manager_p():
    """'O-kyaku-sama...': his grey face too close, the glasses' glare blanking one eye, the other bloodshot behind the
    cracked lens, black rings, a dry open mouth; the fluorescent light flickering green."""
    R = Relief(4601)
    U, V = rot_uv(R.u, R.v, 0.14, 0.0, 0.0)
    zoom = 1.8
    u, v = (U - 0.04) / zoom, (V - 0.02) / zoom
    f = Face(w=0.62, h=0.88, eye_x=0.25, eye_y=-0.14, socket=0.15, brow=0.06, nose_len=0.27, nose=0.11, mouth_y=0.42,
             mouth_w=0.2, lips=0.02, cheek=0.03, hollow=0.08, chin=0.05, jaw_drop=0.03)
    H, M, ft = face_height(u, v, f)
    R.H, R.M = H, M
    R.A = skin(R, (150, 152, 140), 0.14, 4602, blotch_col=(118, 112, 104), blotch=0.55, veins=0.35, pores=0.08)
    stub = np.clip(noise(4603, 0.05) - 0.3, 0, 1) * smoothstep(0.22, 0.32, v + 0.1 * np.abs(u)) * (np.abs(u) < 0.55)
    R.paint(stub * 0.6, (60, 64, 66))
    R.paint(np.clip(noise(4610, 0.3) - 0.8, 0, 1) * 0.8, (190, 196, 196))      # sweat beads
    R.SSS = M * 0.5
    R.WET = M * 0.9
    R.ROUGH[:] = 40.0
    R.paint(ft['nostril'] * 0.9, (60, 50, 50))
    for ex in (-0.25, 0.25):
        R.paint(np.clip(g2(u, v, ex, -0.05, 0.16, 0.09) * 1.4, 0, 1), (22, 16, 26))   # black rings under the eyes
        R.H -= g2(u, v, ex, -0.05, 0.14, 0.07) * 0.03
        eyeball(R, u, v, ex, -0.14, 0.075, 0.035, 0.014, (190, 176, 160), (50, 40, 34), depth=0.01)
        em = ell(u, v, ex, -0.14, 0.075, 0.075) > 0
        R.paint(np.clip(noise(4604 + int(ex * 10), 1.3) - 0.45, 0, 1) * em, (170, 70, 66))
        R.paint((ell(u, v, ex, -0.19, 0.09, 0.03) > 0) * 0.7, (110, 100, 96))  # heavy lids
    mouth_hole(R, u, v, 0.0, 0.44, 0.15, 0.022, lip=(96, 76, 74), lipw=0.012, depth=0.08)
    for sx in (-1, 1):
        R.paint(g2(u, v, sx * 0.2, 0.34, 0.03, 0.14, sx * -0.3) * 0.5, (80, 82, 78))  # deep folds
    # thinning hair combed over the top
    scalp = smoothstep(0.02, -0.02, v + 0.62 - 0.25 * u ** 2)
    R.paint(scalp * 0.5, (120, 116, 106))
    comb = np.clip(np.sin((v + u * 0.25) * 160) * 0.5 + 0.2, 0, 1) * scalp * (np.abs(u) > 0.1)
    R.paint(comb * 0.85, (36, 33, 30))
    # glasses: thick dark frames, the left lens (image right) cracked, the right lens blanked by the glare
    frames = np.zeros_like(u)
    for ex in (-0.25, 0.25):
        e = ell(u, v, ex, -0.14, 0.2, 0.13)
        frames = np.maximum(frames, np.clip(1 - np.abs(e) / 0.12, 0, 1))
    bridge = (np.abs(v + 0.16) < 0.02) & (np.abs(u) < 0.08)
    frames = np.maximum(frames, bridge.astype(np.float32))
    temple = (np.abs(v + 0.18) < 0.018) & (np.abs(u) > 0.43)
    frames = np.maximum(frames, temple.astype(np.float32))
    R.A = R.A * (1 - frames[..., None]) + np.array([0.03, 0.03, 0.035]) * frames[..., None]
    R.H = R.H + frames * 0.12
    R.WET = np.where(frames > 0.5, 1.5, R.WET)
    lens_r = ell(u, v, 0.25, -0.14, 0.185, 0.115) > 0
    cm = crack_mask(4606, 0.25 * zoom + 0.04, -0.14 * zoom + 0.05, n_rad=9, rings=(0.06, 0.13), reach=(0.15, 0.3), width=2)
    cm = cm * lens_r
    lens_l = ell(u, v, -0.25, -0.14, 0.185, 0.115) > 0
    key = ((0.55, -0.8, 0.6), (0.82, 1.0, 0.86), 1.2, 1.0)              # the fluorescent tube above, to one side
    fill = ((0.2, 0.2, 1.0), (0.5, 0.65, 0.85), 0.3, 1.0)               # the monitors' glow in front
    col = lit(R, key, fill, amb=0.05)
    col = col * (1 - cm[..., None] * 0.4) + cm[..., None] * np.array([0.85, 0.92, 0.95])
    yy, xx = np.mgrid[0:N, 0:N]
    glare = np.clip(1 - np.abs((u + 0.25) * 0.8 + (v + 0.14) * 0.6 + 0.02) / 0.09, 0, 1) * lens_l
    glare = np.maximum(glare, lens_l * 0.55)
    col = col * (1 - glare[..., None] * 0.7) + glare[..., None] * np.array([1.2, 1.35, 1.5])
    col = bloom(col, thr=0.9, radius=12, strength=0.8)
    col = flash_spot(col, 0.5, 0.45, 0.68, 0.72, floor=0.15)
    return to_rgb(post(col, 4609, grain=0.075, ca=2.2, vign=1.1, gamma=1.12, tint=(0.9, 1.04, 0.95), crush=0.045))

FACES = {'waitress': js_waitress_p, 'visitor': js_visitor_p, 'chef': js_chef_p, 'child': js_child_p,
         'mirror_woman': js_mirror_woman_p, 'manager': js_manager_p}


def medium_portrait(size=64):
    """The medium's dialogue portrait: her own model, head and shoulders, warm low light, a dark plum ground."""
    import ent_gen as G
    import render3d as R3
    import ent_render as ER
    from ent_model import entity_world
    mod, spec = G.load('medium')
    b = G.Built(spec, mod)
    b.anim.timeline([(0, 0)], 30.0, head=lambda tt: (-12.0, 4.0))
    eye = np.array([-0.55, 1.42, 1.6])
    tgt = np.array([0.0, 1.22, 0.0])
    k = 4
    cv = ER.Canvas(size * k, size * k, R3.look_at(eye, tgt), ortho=0.36, bg=(0, 0, 0))
    yy, xx = np.mgrid[0:size * k, 0:size * k]
    rad = np.hypot((xx - size * k * 0.45) / (size * k * 0.7), (yy - size * k * 0.38) / (size * k * 0.7))
    ground = np.clip(1.0 - rad, 0, 1)
    cv.color[:] = (np.array([22, 14, 24])[None, None, :] + ground[..., None] * np.array([46, 30, 40]))
    M = entity_world(0.0)
    ER.draw_entity(cv, b.spec, b.tex, None, M, eye, alpha=1.0, light=1.05,
                   sky=lambda n, p: 0.85 + 0.25 * max(0.0, float(n @ np.array([0.5, 0.3, 0.8]) / 1.0)))
    ER.draw_entity_eyes(cv, b.spec, b.eyes, M, 0.4)
    col = np.clip(cv.color, 0, 255)
    warm = col * np.array([1.06, 0.98, 0.9])
    img = Image.fromarray(np.clip(warm, 0, 255).astype(np.uint8)).resize((size, size), Image.BOX)
    a = np.asarray(img).astype(np.float32)
    a[0, :] = a[-1, :] = [12, 9, 12]
    a[:, 0] = a[:, -1] = [12, 9, 12]
    a[1, 1:-1] = a[1, 1:-1] * 0.7 + np.array([90, 70, 60]) * 0.3
    return np.clip(a, 0, 255).astype(np.uint8)


def main():
    ids = [a for a in sys.argv[1:] if not a.startswith('-')] or list(FACES) + ['portrait']
    sheet = []
    if 'portrait' in ids:
        ids.remove('portrait')
        pr = medium_portrait()
        print(save_gui('medium_portrait', pr))
        big = np.asarray(Image.fromarray(pr).resize((256, 256), Image.NEAREST))
        sheet.append(('medium_portrait (64x64, shown 4x)', big))
    for eid in ids:
        rgb = FACES[eid]()
        p = save_gui('jumpscare_' + eid, rgb)
        print(p)
        sheet.append((eid, rgb))
    W = 8 + len(sheet) * 264
    img = Image.new('RGB', (W, 290), (14, 12, 14))
    d = ImageDraw.Draw(img)
    for i, (eid, rgb) in enumerate(sheet):
        img.paste(Image.fromarray(rgb), (8 + i * 264, 8))
        d.text((10 + i * 264, 268), eid, fill=(220, 210, 200), font=font(FONT_LABEL, 12))
    os.makedirs(PREVIEW, exist_ok=True)
    out = os.path.join(PREVIEW, 'ent_jumpscares_wip.png' if len(sheet) < len(FACES) + 1 else 'ent_jumpscares.png')
    img.save(out)
    print(out)


if __name__ == '__main__':
    main()
