"""Full-screen jump scares of HITORI (gui/jumpscare_doll.png, gui/jumpscare_woman.png: 256x256, opaque, on black).

Each face is painted as a relief: a height field (skull, sockets, mouth...) with albedo, wetness and emission layers,
lit by the camera flash with cast shadows, wet specular and subsurface in the shadows, then put through a photographic
post (grain, chromatic aberration, vignette, crushed blacks). Designed to match the models' features and palettes.
The relief helpers are copied from SOUNAN's s_jumpscare.py (the art worker owns that file).
usage: python3 tools/art/ent_jumpscare.py [doll|woman ...]
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


# ================================================================================================ shared
def polyline_dist(u, v, pts):
    """Distance from every pixel to a polyline (u, v coordinates) and the position along it (0..1)."""
    pts = np.asarray(pts, np.float32)
    seg = np.hypot(*(pts[1:] - pts[:-1]).T)
    total = float(seg.sum())
    best = np.full(u.shape, 9.0, np.float32)
    along = np.zeros(u.shape, np.float32)
    acc = 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        ab = b - a
        L2 = float(ab @ ab)
        t = np.clip(((u - a[0]) * ab[0] + (v - a[1]) * ab[1]) / L2, 0, 1)
        d = np.hypot(u - (a[0] + ab[0] * t), v - (a[1] + ab[1] * t))
        closer = d < best
        best = np.where(closer, d, best)
        along = np.where(closer, (acc + t * seg[i]) / total, along)
        acc += seg[i]
    return best, along


def button(R, u, v, cx, cy, rx, ry, ang, lift, seed, thread=(0.16, 0.08, 0.07), glint=(-0.35, -0.38)):
    """A glossy black four-hole button set into (or hanging in front of) the relief: a domed disc with a raised rim,
    four sunken holes, a dark thread through them and a painted wet glint (specular alone is too fickle here)."""
    e = ell(u, v, cx, cy, rx, ry, ang)
    inside = e > 0
    base = np.where(inside, lift + 0.025 * np.sqrt(np.clip(e, 0, 1)), 0)
    rim = inside & (e < 0.2)
    base = base + rim * 0.01
    R.H = np.where(inside, base, R.H)
    c, s = math.cos(ang), math.sin(ang)
    col = np.zeros(u.shape + (3,), np.float32) + np.array([0.03, 0.03, 0.036])
    col = np.where(rim[..., None], np.array([0.07, 0.07, 0.08]), col)
    holes = np.zeros(u.shape, bool)
    for (hx, hy) in ((-0.28, -0.28), (0.28, -0.28), (-0.28, 0.28), (0.28, 0.28)):
        px, py = cx + (hx * rx) * c - (hy * ry) * s, cy + (hx * rx) * s + (hy * ry) * c
        holes |= ((u - px) ** 2 + (v - py) ** 2) < (0.12 * rx) ** 2
    R.H = np.where(holes & inside, R.H - 0.012, R.H)
    col[holes & inside] = [0.004, 0.004, 0.005]
    for (a, b) in (((-0.28, -0.28), (0.28, 0.28)), ((0.28, -0.28), (-0.28, 0.28))):
        ax_, ay_ = cx + (a[0] * rx) * c - (a[1] * ry) * s, cy + (a[0] * rx) * s + (a[1] * ry) * c
        bx_, by_ = cx + (b[0] * rx) * c - (b[1] * ry) * s, cy + (b[0] * rx) * s + (b[1] * ry) * c
        d, _ = polyline_dist(u, v, [(ax_, ay_), (bx_, by_)])
        th = (d < 0.045 * rx) & inside
        col[th] = thread
        R.H = np.where(th, R.H + 0.004, R.H)
    R.A = np.where(inside[..., None], col, R.A)
    R.M = np.maximum(R.M, inside.astype(np.float32))
    R.WET = np.where(inside, 1.6, R.WET)
    R.ROUGH = np.where(inside, 160.0, R.ROUGH)
    R.SSS = np.where(inside, 0.0, R.SSS)
    gx, gy = cx + glint[0] * rx, cy + glint[1] * ry
    gl = ell(u, v, gx, gy, rx * 0.32, ry * 0.16, -0.7) > 0
    R.E = np.where((gl & inside)[..., None], np.array([0.75, 0.8, 0.88]), R.E)
    gl2 = (((u - (cx + 0.42 * rx)) ** 2 + (v - (cy + 0.45 * ry)) ** 2) < (0.07 * rx) ** 2) & inside
    R.E = np.where(gl2[..., None], np.array([0.25, 0.27, 0.3]), R.E)
    return inside


def motion_blur(img, mask, dx, dy, steps=14):
    """Smears the masked layer along (dx, dy) pixels (a quick moving object)."""
    acc = np.zeros_like(img)
    macc = np.zeros(mask.shape, np.float32)
    for k in range(steps):
        f = k / (steps - 1) - 0.5
        sh = (int(round(dy * f)), int(round(dx * f)))
        acc += np.roll(img * mask[..., None], sh, (0, 1))
        macc += np.roll(mask, sh, (0, 1))
    return acc / steps, macc / steps


# ================================================================================================ 鬼（人形）the doll
def js_doll():
    """'みつけた' - the fusuma slides open and it is right there, inches from your face: the soaked plush face filling
    the view, the sewn-on button eye shining, the torn socket with its dirty stuffing, the other button swinging on
    its thread across the cheek, the mouth seam ripped open over rice grains like teeth, red thread biting into it,
    and the kitchen knife coming down out of the top of the frame. Lit from below by the laptop on your knees, rimmed
    by the TV's static behind it."""
    R = Relief(7101)
    r = rng(7101)
    U, V = rot_uv(R.u, R.v, 0.22, 0.0, 0.0)
    zoom = 1.08
    u, v = (U + 0.06) / zoom, (V - 0.1) / zoom
    # ---------------------------------------------------------------- the head: a soaked pillow of plush
    shape = ell(u, v, 0.0, 0.06, 1.02, 0.98)
    dome = np.clip(shape, 0, 1)
    M = smoothstep(-0.02, 0.03, shape)
    H = np.sqrt(dome) * 0.4
    for sx in (-1, 1):
        H += g2(u, v, sx * 0.45, 0.26, 0.34, 0.3) * 0.05
    H -= g2(u, v, 0.0, -0.55, 0.02, 0.42) * 0.02                         # the centre seam
    wn = r.normal(0, 1, (N, N)).astype(np.float32)
    fib = ndimage.gaussian_filter(wn, (9.0, 1.4), mode='wrap')            # wet fibres hanging down
    fib = np.tanh(fib / (fib.std() + 1e-6))
    clump = np.clip(blur(noise(7102, 1.5), 3.0), -1.5, 1.5)
    H += fib * 0.004 + clump * 0.022
    R.H, R.M = H, M
    base = np.array([0.6, 0.61, 0.585], np.float32)
    A = base[None, None, :] * (0.86 + 0.07 * fib[..., None] + 0.11 * clump[..., None])
    A = np.where((clump < -0.95)[..., None], A * 0.72, A)                 # crevices between matted clumps
    stain = np.clip((blur(noise(7104, 1.8), 4.0) - 0.45) * 1.1, 0, 0.5)
    A = A * (1 - stain[..., None]) + np.array([0.4, 0.32, 0.2]) * stain[..., None]
    for ex in (-0.36, 0.36):                                              # dirty water run down from both eyes
        tear = g2(u, v, ex + 0.03, 0.24, 0.035, 0.28) * (v > 0.0)
        A = A * (1 - 0.5 * tear[..., None]) + np.array([0.25, 0.21, 0.15]) * 0.5 * tear[..., None]
    R.A = A
    R.WET = M * (0.08 + 0.16 * np.clip(fib, 0, 1) + 0.1 * np.clip(clump, 0, 1))
    R.ROUGH[:] = 9.0
    R.SSS[:] = 0.0
    # ---------------------------------------------------------------- the muzzle, the stitched nose
    for sx in (-1, 1):
        puff = g2(u, v, sx * 0.14, 0.37, 0.17, 0.12)
        R.H = R.H + puff * 0.08
        R.paint(puff * 0.5, (196, 194, 184))
    nose = (v > 0.21) & (v < 0.29) & (np.abs(u) < 0.075 * (0.29 - v) / 0.08)
    R.paint(nose.astype(np.float32), (118, 58, 60))
    R.H = np.where(nose, R.H + 0.012, R.H)
    d, _ = polyline_dist(u, v, [(0.0, 0.28), (0.005, 0.41)])
    R.paint((d < 0.007).astype(np.float32), (84, 40, 42))
    # ---------------------------------------------------------------- the mouth seam torn open over rice teeth
    jag = 0.05 * np.sin(u * 47.0) + 0.04 * noise(7105, 0.6)
    mo = ell(u, v, 0.04, 0.5, 0.28, 0.08, 0.14) + jag
    inside = mo > 0
    R.H = np.where(inside, R.H - 0.13 * np.sqrt(np.clip(mo, 0, 1)) - 0.02, R.H)
    R.A = np.where(inside[..., None], np.array([0.03, 0.012, 0.012]), R.A)
    R.WET = np.where(inside, 0.9, R.WET)
    for row, (vy, k) in enumerate(((0.448, 13), (0.556, 11))):
        for i in range(k):
            gx = -0.21 + i * 0.46 / (k - 1) + r.normal(0, 0.008)
            gy = vy + 0.013 * math.sin(gx * 9.0) + r.normal(0, 0.005) + 0.04 * gx
            gr = ell(u, v, gx, gy, 0.02, 0.012, r.normal(1.57, 0.5))
            g = gr > 0
            R.H = np.where(g, R.H + 0.04 * np.sqrt(np.clip(gr, 0, 1)) + 0.01, R.H)
            R.A = np.where(g[..., None], np.array([0.9, 0.87, 0.78]) * r.uniform(0.85, 1.0), R.A)
            R.WET = np.where(g, 1.8, R.WET)
            R.ROUGH = np.where(g, 120.0, R.ROUGH)
    for i in range(6):                                                   # loose grains spilling down the chin
        gx, gy = 0.24 + r.normal(0, 0.05), 0.62 + i * 0.05 + r.normal(0, 0.02)
        gr = ell(u, v, gx, gy, 0.018, 0.011, r.uniform(0, 3))
        g = gr > 0
        R.H = np.where(g, R.H + 0.03, R.H)
        R.A = np.where(g[..., None], np.array([0.88, 0.84, 0.74]), R.A)
        R.WET = np.where(g, 1.6, R.WET)
    for (a, b) in (((-0.25, 0.46), (-0.31, 0.55)), ((0.31, 0.53), (0.37, 0.6)), ((0.32, 0.52), (0.35, 0.64))):
        d, _ = polyline_dist(u, v, [a, b])                               # frayed ends of the old mouth stitch
        R.paint((d < 0.006).astype(np.float32), (70, 34, 34))
    # ---------------------------------------------------------------- the sewn-on button, the torn socket
    R.H = R.H - np.clip(1.0 - np.abs(ell(u, v, -0.36, -0.1, 0.17, 0.17)) / 0.2, 0, 1) * 0.02
    surf = float(R.H[N // 2, N // 2])
    button(R, u, v, -0.36, -0.1, 0.15, 0.15, 0.2, surf + 0.02, 7106)
    so = ell(u, v, 0.36, -0.1, 0.15, 0.135, -0.2) + 0.14 * noise(7107, 0.8)
    hole = so > 0
    R.H = np.where(hole, R.H - 0.18 * np.sqrt(np.clip(so, 0, 1)) - 0.03, R.H)
    R.A = np.where(hole[..., None], np.array([0.015, 0.012, 0.012]), R.A)
    R.WET = np.where(hole, 0.3, R.WET)
    ring = (so < 0) & (so > -0.55)
    fl = blur(noise(7108, 1.2), 4.0)
    tuftw = np.clip((fl + 0.2) * 2.0, 0, 1) * ring * np.clip(1.0 + so / 0.55, 0, 1) ** 0.5
    fluff = blur(np.clip(noise(7109, 0.6), -1, 1), 1.2)
    R.H = R.H + tuftw * (0.045 + 0.015 * fluff)
    tc = np.array([0.78, 0.76, 0.7])[None, None, :] * (0.85 + 0.15 * fluff[..., None])
    R.A = R.A * (1 - tuftw[..., None]) + tc * tuftw[..., None]
    R.WET = R.WET * (1 - tuftw) + 0.05 * tuftw
    for k in range(10):                                                 # frayed threads round the hole
        a = k / 10 * 6.283 + r.normal(0, 0.3)
        pts = [(0.36 + math.cos(a) * 0.14, -0.1 + math.sin(a) * 0.125)]
        for s_ in range(3):
            a += r.normal(0, 0.5)
            pts.append((pts[-1][0] + math.cos(a) * 0.035, pts[-1][1] + math.sin(a) * 0.035))
        d, _ = polyline_dist(u, v, pts)
        R.paint((d < 0.0045).astype(np.float32), (36, 30, 28))
    # ---------------------------------------------------------------- the other button, hanging on its thread
    path = [(0.37, 0.02), (0.4, 0.15), (0.43, 0.3), (0.465, 0.41)]
    d, al = polyline_dist(u, v, path)
    th = d < 0.006
    R.H = np.where(th, np.maximum(R.H, surf + 0.02 + 0.08 * al), R.H)
    R.A = np.where(th[..., None], np.array([0.12, 0.1, 0.09]), R.A)
    R.WET = np.where(th, 1.0, R.WET)
    R.M = np.maximum(R.M, th.astype(np.float32))
    button(R, u, v, 0.48, 0.5, 0.12, 0.09, 0.55, surf + 0.1, 7110, glint=(-0.3, -0.45))
    # ---------------------------------------------------------------- red thread wound tight below the mouth
    for (a, b) in (((-1.2, 0.68), (1.2, 0.88)), ((-1.2, 0.97), (1.2, 0.74))):
        d, _ = polyline_dist(u, v, [a, ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + 0.02), b])
        pinch = np.clip(1 - d / 0.05, 0, 1) * M
        R.H = R.H - pinch * 0.04
        t_ = (d < 0.012) & (M > 0.5)
        R.H = np.where(t_, R.H + 0.03, R.H)
        tw = 0.8 + 0.2 * np.sin((u + v) * 140.0)
        R.A = np.where(t_[..., None], np.array([0.72, 0.08, 0.06]) * tw[..., None], R.A)
        R.WET = np.where(t_, 0.9, R.WET)
        R.ROUGH = np.where(t_, 60.0, R.ROUGH)
    # ---------------------------------------------------------------- drops of bath water
    for i in range(46):
        dx, dy = r.uniform(-0.85, 0.85), r.uniform(-0.75, 0.9)
        rad = r.uniform(0.006, 0.015)
        e = ell(u, v, dx, dy, rad, rad * 1.25)
        dm = (e > 0) & (M > 0.5)
        R.H = np.where(dm, R.H + 0.02 * np.sqrt(np.clip(e, 0, 1)), R.H)
        R.WET = np.where(dm, 2.6, R.WET)
        R.ROUGH = np.where(dm, 300.0, R.ROUGH)
        R.E = np.where((dm & (e > 0.6))[..., None], np.array([0.25, 0.27, 0.3]), R.E)
    # ---------------------------------------------------------------- light: the laptop from below, the TV behind
    key = ((-0.2, 0.62, 0.76), (0.85, 0.92, 1.0), 1.55, 1.0)
    rim = ((0.8, -0.5, 0.25), (0.45, 0.6, 1.0), 0.6, 1.0)
    col = lit(R, key, rim, amb=0.06, amb_col=(0.5, 0.58, 0.75))
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    col = col * (0.55 + 0.6 * np.clip((yy / N) * 1.15, 0, 1))[..., None]   # the screen light falls off upward
    # ---------------------------------------------------------------- the knife coming down out of the frame
    ku, kv = R.u, R.v
    tip = np.array([0.26, -0.3])
    heel = np.array([0.98, -1.12])
    axis = heel - tip
    L = float(np.linalg.norm(axis))
    ax = axis / L
    nrm = np.array([ax[1], -ax[0]])                                       # toward the edge side (lower left)
    rel_u, rel_v = ku - tip[0], kv - tip[1]
    along = (rel_u * ax[0] + rel_v * ax[1]) / L
    across = rel_u * nrm[0] + rel_v * nrm[1]
    bw = 0.2                                                              # santoku: straight spine, edge curving up
    edge_w = bw * np.sqrt(np.clip(along / 0.35, 0, 1))
    blade = (along > 0) & (along < 1.0) & (across > -0.012) & (across < edge_w)
    hd = (along >= 0.96) & (along < 1.45) & (across > 0.02) & (across < 0.12)
    sh = np.clip(across / np.maximum(edge_w, 1e-3), 0, 1)
    steel = np.array([0.58, 0.63, 0.68])[None, None, :] * (0.5 + 0.7 * (1 - sh[..., None]) ** 1.5)
    bevel = blade & (sh > 0.78)
    steel = np.where(bevel[..., None], np.array([1.2, 1.25, 1.32]), steel)
    spine = blade & (across < 0.008)
    steel = np.where(spine[..., None], np.array([0.85, 0.9, 0.95]), steel)
    for k_ in range(5):                                                   # water beads on the steel
        bu, bv = 0.3 + 0.12 * k_ + r.normal(0, 0.03), 0.06 + r.normal(0, 0.03)
        bead = blade & (((along - bu) / 0.02) ** 2 + ((across - bv) / 0.012) ** 2 < 1)
        steel = np.where(bead[..., None], np.array([1.1, 1.15, 1.2]), steel)
    hcol = np.array([0.05, 0.045, 0.045])[None, None, :] * np.ones((N, N, 1))
    for rv in (1.08, 1.2, 1.32):
        rivet = hd & (((along - rv) / 0.02) ** 2 + ((across - 0.07) / 0.012) ** 2 < 1)
        hcol = np.where(rivet[..., None], np.array([0.7, 0.72, 0.75]), hcol)
    kimg = np.where(blade[..., None], steel, np.where(hd[..., None], hcol, 0.0))
    kmask = (blade | hd).astype(np.float32)
    kimg, kmask2 = motion_blur(kimg, kmask, -22.0, 20.0, steps=12)
    col = col * (1 - kmask2[..., None]) + kimg
    col = bloom(col, thr=0.85, radius=9, strength=0.45)
    out = post(col, 7119, grain=0.055, ca=2.2, vign=1.0, gamma=1.0, tint=(0.92, 0.98, 1.06), crush=0.03, gain=1.1)
    return to_rgb(out)


# ================================================================================================ 女 the woman
def js_woman():
    """On the camera feed, right at the lens: her grey face pressed close behind the wet black hair, the one eye in the
    parting blazing white in the infrared, the mouth a little open, the long grey fingers reaching in from below to
    cover the lens, out of focus. Already in the feed's IR monochrome (grey-green, noise, interlace)."""
    R = Relief(7201)
    r = rng(7201)
    U, V = rot_uv(R.u, R.v, -0.14, 0.0, 0.0)
    rr = U * U + V * V
    U, V = U * (1 - 0.2 * rr), V * (1 - 0.2 * rr)                         # pressed against a wide lens
    zoom = 1.2
    u, v = (U + 0.02) / zoom, (V + 0.04) / zoom
    f = Face(w=0.56, h=0.9, eye_x=0.21, eye_y=-0.16, socket=0.24, brow=0.06, nose_len=0.25, nose=0.16, mouth_y=0.36,
             mouth_w=0.17, lips=0.02, cheek=0.08, hollow=0.13, chin=0.05, jaw_drop=0.05)
    H, M, ft = face_height(u, v, f)
    R.H, R.M = H, M
    R.A = skin(R, (200, 205, 208), 0.08, 7202, veins=0.7, vein_col=(110, 120, 142), blotch_col=(168, 172, 178), blotch=0.4)
    R.SSS = M * 0.1
    R.WET = M * 0.7
    R.ROUGH[:] = 45.0
    R.paint(ft['nostril'] * 0.9, (60, 64, 70))
    for sx in (-1, 1):
        R.paint(g2(u, v, sx * 0.21, -0.16, 0.16, 0.12) * 0.55, (88, 90, 98))      # dark sunken rings
    ex, ey = 0.21, -0.16
    eyeball(R, u, v, ex, ey, 0.08, 0.045, 0.016, (210, 206, 200), (160, 166, 170), depth=0.015)
    almond = ell(u, v, ex, ey, 0.08, 0.056)
    lids = (ell(u, v, ex, ey, 0.1, 0.1) > 0) & (almond < 0)
    R.A = np.where(lids[..., None], R.A * 0.7, R.A)
    R.H = np.where(lids, R.H + 0.02, R.H)
    eye_core = ((u - ex) ** 2 + (v - ey) ** 2) < 0.034 ** 2
    R.E[eye_core] = [3.0, 3.1, 3.0]                                     # the eyeshine in the infrared
    mouth_hole(R, u, v, 0.02, 0.41, 0.1, 0.055, lip=(90, 96, 110), lipw=0.018, depth=0.16)
    # the hair: two heavy curtains with a ragged parting over her left eye, clumps and loose strands across
    edge_l = -0.1 + 0.05 * np.sin(v * 12) + 0.03 * np.sin(v * 31 + 1.0) + 0.06 * np.clip(-v, 0, 1)
    edge_r = 0.42 + 0.04 * np.sin(v * 10 + 2.0) + 0.03 * np.sin(v * 27)
    curtains = ((u < edge_l) | (u > edge_r) | (v < -0.36 + 0.08 * u)).astype(np.float32)
    clump = strand_mask(7203, 8, lambda r_: (r_.uniform(-0.6, -0.12), -1.1), 1.5708, (1.7, 2.3), width=(5.0, 9.0),
                        droop=0.9, curl=0.06, clump=(4, 7))
    loose = strand_mask(7204, 8, lambda r_: (r_.uniform(0.0, 0.5), -1.1), 1.6, (1.5, 2.3), width=(1.4, 2.6), droop=0.85,
                        curl=0.12, clump=(2, 3))
    hm = np.clip(blur(curtains, 2.5) + clump + loose * 0.85, 0, 1)
    wet_hair(R, hm, col=(10, 11, 13), sheen=0.9)
    keep = ((np.abs(u - ex) < 0.055) & (np.abs(v - ey) < 0.05)).astype(np.float32)
    R.E = R.E * (1 - np.clip(hm, 0, 1) * (1 - keep))[..., None]
    # the fingers reaching for the lens from below, too long, out of focus
    fingers = np.zeros((N, N), bool)
    for i, (x0, ang, ln) in enumerate(((-0.92, -1.05, 1.0), (-0.7, -1.15, 1.18), (-0.47, -1.28, 1.22), (-0.24, -1.4, 1.1),
                                        (-1.05, -0.78, 0.7))):
        pts = [(x0, 1.2)]
        a = ang
        for s_ in range(6):
            a += 0.05 * (1 if i % 2 else -1)
            pts.append((pts[-1][0] + math.cos(a) * ln / 6, pts[-1][1] + math.sin(a) * ln / 6))
        fingers |= finger(R, pts, 0.06, 0.7, (186, 190, 194), nail_col=(36, 34, 40), seed=7205 + i, nail_len=0.13,
                          knuckles=(0.42, 0.72))
    key = ((0.42, -0.5, 0.76), (1.0, 1.0, 1.0), 1.45, 1.0)                 # the camera's IR illuminator, above and aside
    col = lit(R, key, ((-0.6, 0.2, 0.7), (0.6, 0.6, 0.6), 0.12, 1.0), amb=0.04)
    col = flash_spot(col, 0.55, 0.42, 0.48, 0.58, floor=0.07)
    fing = blur(fingers.astype(np.float32), 4.0)
    col = col * (1 - fing[..., None]) + blur_rgb(col, 7.0) * fing[..., None]   # the near fingers out of focus
    col = bloom(col, thr=1.4, radius=16, strength=1.2)
    lum = col.mean(-1)
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    lum = np.clip(lum, 0, 2.0)
    lum = lum / (1 + lum * 0.3) * 1.25
    lum = lum * (0.92 + 0.08 * (yy % 4 < 2))                              # interlace
    lum = lum + r.normal(0, 0.055, (N, N)) * (0.35 + lum)
    tear = (np.abs(yy - 300) < 5) * 0.12                                  # a torn line in the feed
    lum = np.roll(lum, 0, 1) + tear
    rgb = np.clip(lum, 0, 1.3)[..., None] * np.array([0.8, 1.0, 0.85])
    out = post(rgb, 7209, grain=0.045, ca=1.4, vign=1.2, gamma=1.05, tint=(1.0, 1.0, 1.0), crush=0.05)
    return to_rgb(out)


FACES = {'doll': js_doll, 'woman': js_woman}


def main():
    ids = [a for a in sys.argv[1:] if not a.startswith('-')] or list(FACES)
    sheet = []
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
        d.text((10 + i * 264, 268), 'jumpscare_' + eid + '.png', fill=(220, 210, 200), font=font(FONT_LABEL, 12))
    os.makedirs(PREVIEW, exist_ok=True)
    out = os.path.join(PREVIEW, 'ent_jumpscares.png')
    img.save(out)
    print(out)


if __name__ == '__main__':
    main()
