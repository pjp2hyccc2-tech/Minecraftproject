"""Full-screen jumpscare faces (gui/jumpscare_<monster>.png, 256x256, opaque, black background).

Each face is painted as a relief: a height field (skull, sockets, nose, mouth...) with albedo layers, lit by a harsh
light (the player's flashlight / camera flash) with cast shadows, wet specular highlights and blood-tinted
subsurface in the shadows; then photographic post: grain, chromatic aberration, vignette, crushed blacks.
Rendered at 2x and downsampled."""
import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from common import Tex, texture, rng, fnoise, smoothstep

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


def to_tex(c):
    img = Image.fromarray((np.clip(c, 0, 1) * 255).astype(np.uint8), 'RGB').resize((256, 256), Image.LANCZOS)
    arr = np.asarray(img, np.float32)
    t = Tex(256, 256)
    t.a[..., :3] = arr
    t.a[..., 3] = 255
    return t


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


# ------------------------------------------------------------------------------------------------ common pieces
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


# ================================================================================================ shared anatomy
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


def bark_layer(R, mask, seed, base=(0.12, 0.08, 0.06), height=0.55):
    fib = fnoise(N, N, seed, beta=1.0, sx=1.0, sy=9.0)
    bn = noise(seed + 1, 0.8)
    c = np.stack([base[0] + 0.05 * fib, base[1] + 0.035 * fib, base[2] + 0.025 * fib], -1) * (1 + 0.35 * bn[..., None])
    R.A = np.where(mask[..., None], c, R.A)
    R.H = np.where(mask, height + 0.03 * fib + 0.02 * bn, R.H)
    R.M = np.where(mask, 1.0, R.M)
    R.WET = np.where(mask, 0.08, R.WET)
    R.ROUGH = np.where(mask, 20.0, R.ROUGH)
    R.SSS = np.where(mask, 0.0, R.SSS)


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


# ================================================================================================ 覗き nozoki
@texture('gui/jumpscare_nozoki', size=(256, 256), opaque=True)
def jumpscare_nozoki():
    """It peeks round the trunk a hand's breadth from the lens, head tipped over sideways: huge wet black eyes with
    pinprick pupils, a lipless slit stretched to the cheeks, wet hair; long grey fingers wrapped on the bark."""
    R = Relief(3001)
    U, V = R.u, R.v
    cx, cy, tilt = 0.36, 0.06, -0.62
    u, v = rot_uv(U, V, tilt, cx, cy)
    sc = 1.22
    u, v = (u - cx) / sc, (v - cy) / sc
    H, M = face_relief(R, u, v, w=0.56, h=0.86, brow=0.07, sockets=0.22, cheek=0.1, hollow=0.09, nose=0.03, eye_x=0.25,
                       chin=0.05)
    R.H, R.M = H, M
    R.A = skin_albedo(R, (150, 154, 150), 0.14, 3002, veins=0.7, vein_col=(80, 92, 120), blotch_col=(118, 112, 100), blotch=0.4)
    R.SSS = M * 0.7
    R.WET = M * 0.5
    R.ROUGH[:] = 24.0
    for ex in (-0.25, 0.25):
        R.paint(g2(u, v, ex, -0.12, 0.27, 0.21) * 0.55, (70, 64, 70))           # bruised hollows round the eyes
        R.paint(g2(u, v, ex * 1.5, 0.12, 0.12, 0.05, 0.4 if ex > 0 else -0.4) * 0.25, (190, 192, 186))
    for k in range(3):
        R.paint(np.clip(1 - np.abs(v + 0.42 + k * 0.05 - 0.04 * np.sin(u * 6 + k)) / 0.006, 0, 1) * (np.abs(u) < 0.35) * 0.35,
                (110, 110, 106))
    for ex in (-0.25, 0.25):
        orb = ell(u, v, ex, -0.13, 0.19, 0.135, -0.12 if ex < 0 else 0.12)
        inside = orb > 0
        R.H = np.where(inside, R.H + np.sqrt(np.clip(orb, 0, 1)) * 0.06 - 0.04, R.H)
        R.A = np.where(inside[..., None], np.array([0.008, 0.008, 0.011]), R.A)
        R.WET = np.where(inside, 3.2, R.WET)
        R.ROUGH = np.where(inside, 200.0, R.ROUGH)
        R.SSS = np.where(inside, 0.0, R.SSS)
        lid = smoothstep(0.0, 0.05, orb + 0.06) * (1 - smoothstep(0.06, 0.12, orb + 0.06))
        R.paint(lid * 0.7, (66, 30, 34))
        pin = ((u - ex + (0.03 if ex > 0 else -0.01)) ** 2 + (v + 0.12) ** 2) < 0.012 ** 2
        R.E[pin] = [1.2, 1.15, 1.05]
    curve = v - 0.4 - 0.16 * u ** 2
    wmax = 0.52
    open_ = 0.018 + 0.06 * np.clip(1 - (u / wmax) ** 2, 0, 1) ** 1.5
    slit = np.clip(1 - np.abs(curve) / open_, 0, 1) * (np.abs(u) < wmax)
    R.H -= slit * 0.14
    R.A = R.A * (1 - slit[..., None]) + np.array([0.03, 0.0, 0.0]) * slit[..., None]
    R.WET += slit * 0.9
    gum = np.clip(1 - np.abs(curve + open_ * 0.7) / 0.012, 0, 1) * (np.abs(u) < wmax - 0.03) * (slit > 0)
    R.paint(gum * 0.8, (96, 40, 40))
    rt = rng(3010)
    tx = -wmax + 0.06
    while tx < wmax - 0.06:
        tw = rt.uniform(0.012, 0.024)
        ty = 0.4 + 0.16 * tx ** 2
        if rt.random() > 0.15 and abs(tx) < wmax * 0.7:
            ln = rt.uniform(0.25, 0.6)
            shade_ = rt.uniform(0.75, 1.05)
            top = (np.abs(u - tx) < tw * 0.8) & (v > ty - open_ * ln) & (v < ty - 0.002)
            R.A[top] = np.array([0.47, 0.44, 0.35]) * shade_
            R.H[top] += 0.015
        if rt.random() > 0.3 and abs(tx) < wmax * 0.55:
            bot = (np.abs(u - tx - tw) < tw * 0.7) & (v > ty + open_ * 0.45) & (v < ty + open_ * rt.uniform(0.6, 0.85))
            R.A[bot] = np.array([0.4, 0.37, 0.3]) * rt.uniform(0.7, 1.0)
        tx += tw * 2 + rt.uniform(0.0, 0.012)
    for sx in (-1, 1):
        R.paint(g2(u, v, sx * 0.55, 0.43, 0.07, 0.012, sx * -0.6) * 0.6, (90, 88, 84))
        R.paint(g2(u, v, sx * 0.45, 0.52, 0.09, 0.01, sx * -0.3) * 0.4, (100, 98, 92))
    # hair: a wet mass plastered over the skull and hanging (towards the image bottom), strands across the face
    ragged = blur(rng(3011).normal(0, 1, (N, N)), 4) * 0.25 + 0.05 * np.sin(u * 23)
    top_of_head = smoothstep(0.03, -0.03, v + 0.3 - 0.35 * u ** 2 + ragged)
    beyond = smoothstep(0.0, -0.04, ell(u, v, 0, 0, 0.56, 0.86)) * smoothstep(0.35, 0.0, -ell(u, v, 0, 0, 0.56, 0.86))
    hang = np.clip((U - cx) * 0 + 1, 0, 1) * smoothstep(-0.2, 0.2, V - cy + 0.3) * beyond
    mask = np.clip(top_of_head + beyond * smoothstep(0.4, -0.2, v) + hang, 0, 1)
    hair_mass(R, mask, 3003, flow_angle=1.5708 + 0.25 * np.tanh(u * 2) * (v < 0) + tilt * 0.6, col=(9, 9, 11))
    hair_clumps(R, 3004, 9, lambda r: (*rot_uv(r.uniform(-0.25, 0.35) * sc + cx, -0.42 * sc + cy, -tilt, cx, cy),
                                       1.7 + r.normal(0, 0.25)), length=(0.8, 1.3), width=(1.5, 3.2), droop=0.55, curl=0.4)
    # the trunk in front on the left, three long grey fingers wrapped round its edge
    edge = -0.08 + 0.025 * np.sin(V * 11) + 0.012 * np.sin(V * 37)
    trunk = U < edge
    bark_layer(R, trunk, 3005, base=(0.2, 0.13, 0.095), height=0.75)
    R.H = np.where(trunk, R.H - 0.5 * np.clip((U - edge + 0.3) * 1.8, 0, 1) ** 2, R.H)
    for i, (fy, L) in enumerate(((0.52, 0.5), (0.63, 0.56), (0.74, 0.48))):
        x0 = -0.06
        path = [(x0, fy - 0.05), (x0 - 0.1, fy - 0.01), (x0 - 0.25, fy + 0.01), (x0 - L * 0.75, fy + 0.04), (x0 - L, fy + 0.09)]
        finger(R, path, 0.036, 0.74, (104, 110, 106), nail_col=(112, 98, 66), seed=3007 + i)
    key = ((0.32, 0.62, 0.72), (1.0, 0.96, 0.88), 1.3, 1.0)
    fill = ((-0.7, -0.4, 0.6), (0.45, 0.55, 0.7), 0.18, 1.0)
    col = R.render([key, fill], ambient=0.04)
    yy, xx = np.mgrid[0:N, 0:N]
    spot = np.exp(-(((xx - N * 0.55) / (N * 0.55)) ** 2 + ((yy - N * 0.5) / (N * 0.6)) ** 2))
    col *= (0.22 + 0.95 * spot)[..., None]
    return to_tex(post(col, 3009, grain=0.06, ca=2.2, vign=1.0, gamma=1.15, tint=(0.94, 1.0, 1.06), crush=0.03))


def bloom(col, thr=0.85, radius=18, strength=0.9, streak=0.0):
    hi = np.clip(col - thr, 0, None)
    b = np.stack([blur(hi[..., c], radius) for c in range(3)], -1) * strength
    b += np.stack([blur(hi[..., c], radius * 0.3) for c in range(3)], -1) * strength * 0.6
    if streak:
        st = np.stack([ndimage.gaussian_filter(hi[..., c], (1.5, radius * 6), mode='nearest') for c in range(3)], -1)
        b += st * streak
    return col + b


# ================================================================================================ 呼び声 yobigoe
@texture('gui/jumpscare_yobigoe', size=(256, 256), opaque=True)
def jumpscare_yobigoe():
    """Right beside you with the lamp lit again: the rescuer's hood, the headlamp burning into your eyes - and under
    it skin. No eyes, no nose, no mouth. Faint dimples where they should be, a glossy waxy membrane."""
    R = Relief(3101)
    U, V = R.u, R.v
    u, v = rot_uv(U, V, 0.12, 0.0, 0.1)
    u, v = u / 0.92, (v - 0.14) / 0.92
    H, M = face_relief(R, u, v, w=0.55, h=0.78, brow=0.05, sockets=0.035, cheek=0.04, hollow=0.02, nose=0.03, chin=0.03)
    R.H, R.M = H, M
    R.A = skin_albedo(R, (196, 164, 146), 0.05, 3102, veins=0.25, vein_col=(160, 120, 130), blotch_col=(200, 150, 140), blotch=0.3)
    R.SSS = M * 0.9
    R.WET = M * 1.2
    R.ROUGH[:] = 55.0
    for ex in (-0.24, 0.24):                          # where eyes should be: shallow dimples of skin
        R.H -= g2(u, v, ex, -0.1, 0.1, 0.05) * 0.025
        R.paint(g2(u, v, ex, -0.1, 0.11, 0.05) * 0.18, (170, 128, 120))
    R.H -= g2(u, v, 0, 0.36, 0.22, 0.012) * 0.02          # a crease where a mouth should be
    R.paint(g2(u, v, 0, 0.36, 0.2, 0.012) * 0.25, (160, 112, 104))
    # hood: wet faded orange fabric around the face, heavy folds, a reflective strip catching the flash
    hood_in = ell(u, v, 0, -0.05, 0.62, 0.92)
    hood = (hood_in < 0.04) & (ell(U, V, 0, 0.1, 1.25, 1.3) > -0.4)
    fold = np.sin(np.arctan2(v + 0.05, u) * 9 + noise(3103, 1.2) * 2.5)
    R.H = np.where(hood, 0.25 + 0.06 * fold - 0.2 * np.clip(-hood_in, 0, 1), R.H)
    oc = np.array([0.78, 0.32, 0.11]) * (0.75 + 0.25 * fold[..., None]) * (0.85 + 0.15 * noise(3104, 0.5)[..., None])
    R.A = np.where(hood[..., None], oc, R.A)
    R.M = np.where(hood, 1.0, R.M)
    R.WET = np.where(hood, 0.7, R.WET)
    R.ROUGH = np.where(hood, 35.0, R.ROUGH)
    R.SSS = np.where(hood, 0.0, R.SSS)
    tape = hood & (np.abs(np.arctan2(v + 0.05, u) - 2.4) < 0.08)
    tape |= hood & (np.abs(np.arctan2(v + 0.05, u) - 0.74) < 0.08)
    R.A[tape] = [0.75, 0.76, 0.76]
    R.E[tape] = [0.5, 0.5, 0.5]
    rim = (hood_in > 0.0) & (hood_in < 0.05)
    R.paint(rim.astype(np.float32), (40, 14, 6))
    # headlamp strap across the brow and the lamp itself, burning
    strap = (np.abs(v + 0.5 - 0.06 * u ** 2) < 0.05) & ((M > 0.5) | hood)
    R.A[strap] = [0.05, 0.05, 0.06]
    R.H[strap] += 0.03
    lamp = ell(u, v, 0, -0.52, 0.16, 0.1) > 0
    R.A[lamp] = [0.12, 0.12, 0.13]
    R.H[lamp] += 0.08
    lens = ell(u, v, 0, -0.52, 0.1, 0.065) > 0
    R.E[lens] = [9.0, 8.6, 7.4]
    R.M = np.maximum(R.M, lamp.astype(np.float32))
    flash = ((0.0, 0.15, 1.0), (1.0, 0.97, 0.92), 1.05, 1.0)
    lampkey = ((0.0, -0.9, 0.45), (1.0, 0.95, 0.85), 0.9, 1.0)
    col = R.render([flash, lampkey], ambient=0.05)
    col = bloom(col, thr=0.95, radius=26, strength=1.3, streak=0.8)
    yy, xx = np.mgrid[0:N, 0:N]
    lx, ly = N / 2 + 0.06 * N, N * 0.255
    glare = np.exp(-(((xx - lx) / (N * 0.22)) ** 2 + ((yy - ly) / (N * 0.2)) ** 2))
    col += glare[..., None] * np.array([1.0, 0.95, 0.82]) * 0.6
    return to_tex(post(col, 3109, grain=0.07, ca=2.6, vign=1.1, gamma=1.05, tint=(1.0, 0.98, 0.95), crush=0.04))


# ================================================================================================ 後ろ ushiro
@texture('gui/jumpscare_ushiro', size=(256, 256), opaque=True)
def jumpscare_ushiro():
    """You turned round. It hangs over you upside down, chalk-white, eyes nearly touching, and the grin runs from
    ear to ear, far too many small teeth."""
    R = Relief(3201)
    U, V = R.u, R.v
    u, v = rot_uv(U, V, math.pi + 0.18, 0.0, -0.05)       # upside down, a little tilted
    u, v = u / 1.18, (v + 0.02) / 1.18
    H, M = face_relief(R, u, v, w=0.58, h=0.86, brow=0.05, sockets=0.1, cheek=0.06, hollow=0.04, nose=0.05, eye_x=0.13)
    R.H, R.M = H, M
    R.A = skin_albedo(R, (226, 222, 214), 0.06, 3202, veins=0.35, vein_col=(150, 160, 180), blotch_col=(200, 196, 190), blotch=0.3)
    R.SSS = M * 0.5
    R.WET = M * 0.3
    R.ROUGH[:] = 22.0
    for ex in (-0.092, 0.092):                            # eyes far too close together
        R.paint(g2(u, v, ex, -0.15, 0.12, 0.09) * 0.8, (110, 60, 66))
        inside = eyeball(R, u, v, ex, -0.15, 0.08, 0.034, 0.009, (196, 178, 158), (30, 16, 12), look=(-0.5 if ex > 0 else 0.5, 0.3), depth=0.0)
        vein = np.clip((np.abs(noise(3206 + int(ex * 100), 1.0)) < 0.08) * 1.0, 0, 1) * inside
        R.paint(vein * 0.6, (150, 40, 40))
        # heavy lids: only a slit of the eyeball shows, rims raw red
        lid_top = inside & (v < -0.15 - 0.03 + 0.5 * (u - ex) ** 2 * 6)
        lid_bot = inside & (v > -0.15 + 0.035)
        for lm in (lid_top, lid_bot):
            R.A[lm] = [0.8, 0.76, 0.72]
            R.WET[lm] = 0.3
            R.ROUGH[lm] = 22.0
            R.H[lm] += 0.01
        rim = inside & ~lid_top & ~lid_bot & ((np.abs(v + 0.15 + 0.03 - 0.5 * (u - ex) ** 2 * 6) < 0.008) | (np.abs(v + 0.15 - 0.035) < 0.008))
        R.A[rim] = [0.55, 0.12, 0.12]
    # the grin: ear to ear, corners hooked up towards the ears, lips thin and grey, rows of small teeth
    curve = v - 0.33 + 0.33 * (u / 0.62) ** 2 * (1 + 0.6 * (np.abs(u) > 0.45))
    wmax = 0.64
    open_ = 0.025 + 0.11 * np.clip(1 - (u / wmax) ** 2, 0, 1) ** 1.2
    gap = np.clip(1 - np.abs(curve) / open_, 0, 1) * (np.abs(u) < wmax)
    gap = np.clip(gap * 2.5, 0, 1)
    R.H -= gap * 0.12
    R.A = R.A * (1 - gap[..., None]) + np.array([0.06, 0.0, 0.0]) * gap[..., None]
    lips = np.clip(1 - np.abs(np.abs(curve) - open_) / 0.012, 0, 1) * (np.abs(u) < wmax)
    R.paint(lips * 0.8, (118, 92, 96))
    rt = rng(3203)
    tx = -wmax + 0.05
    while tx < wmax - 0.05:
        tw = rt.uniform(0.01, 0.017)
        for side in (-1, 1):
            ty = 0.33 - 0.33 * (tx / 0.62) ** 2 * (1 + 0.6 * (abs(tx) > 0.45))
            o = 0.025 + 0.11 * max(0.0, 1 - (tx / wmax) ** 2) ** 1.2
            band = (np.abs(u - tx) < tw * 0.8) & (side * (v - ty) > o * 0.55) & (side * (v - ty) < o * rt.uniform(0.85, 1.0))
            R.A[band] = np.array([0.82, 0.8, 0.72]) * rt.uniform(0.8, 1.0)
            R.H[band] += 0.012
        tx += tw * 2.1
    deep = np.clip(1 - np.abs(curve) / (open_ * 0.55), 0, 1) * (np.abs(u) < wmax)
    R.A = R.A * (1 - deep[..., None]) + np.array([0.015, 0.0, 0.0]) * deep[..., None]
    R.WET = R.WET * (1 - deep) + lips * 0.5
    R.H = R.H - deep * 0.15
    for sx in (-1, 1):
        R.paint(g2(u, v, sx * 0.62, 0.12, 0.05, 0.03) * 0.5, (150, 120, 120))
    # hair: from her (inverted) scalp it falls past her face towards you: dark curtains on both sides and strands
    scalp = smoothstep(0.03, -0.03, v + 0.55 - 0.3 * u ** 2)
    out_ = smoothstep(0.0, -0.05, ell(u, v, 0, 0, 0.58, 0.86))
    curtain = np.clip(out_ * smoothstep(0.75, 0.35, np.abs(U)) + out_ * (np.abs(U) > 0.5), 0, 1)
    mask = np.clip(scalp * M + curtain, 0, 1)
    hair_mass(R, mask, 3204, flow_angle=1.5708, col=(7, 7, 9))
    hair_clumps(R, 3205, 5, lambda r: (r.choice([-1, 1]) * r.uniform(0.35, 0.6), r.uniform(0.5, 0.9), -1.5708 + r.normal(0, 0.15)),
                length=(0.5, 1.0), width=(1.5, 3.0), droop=-0.2, curl=0.3)
    under = ((0.1, -0.75, 0.65), (0.95, 0.97, 1.0), 1.25, 1.0)
    col = R.render([under], ambient=0.05)
    yy, xx = np.mgrid[0:N, 0:N]
    spot = np.exp(-(((xx - N * 0.5) / (N * 0.55)) ** 2 + ((yy - N * 0.45) / (N * 0.55)) ** 2))
    col *= (0.25 + 0.9 * spot)[..., None]
    return to_tex(post(col, 3209, grain=0.06, ca=2.2, vign=1.1, gamma=1.1, tint=(0.96, 1.0, 1.05), crush=0.03))


# ================================================================================================ 小屋主 koyanushi
@texture('gui/jumpscare_koyanushi', size=(256, 256), opaque=True)
def jumpscare_koyanushi():
    """It woke up. The old thing lunges screaming: a toothless gaping mouth, sockets so sunken only two pinpoints of
    light show, skin like wet paper over the skull, a few long grey hairs; long yellow nails come at your face."""
    R = Relief(3301)
    U, V = R.u, R.v
    u, v = rot_uv(U, V, -0.2, 0.1, -0.05)
    u, v = (u - 0.08) / 1.15, (v + 0.05) / 1.15
    H, M = face_relief(R, u, v, w=0.55, h=0.9, brow=0.11, sockets=0.3, cheek=0.12, hollow=0.14, nose=0.06, chin=0.04, eye_x=0.24)
    R.H, R.M = H, M
    R.A = skin_albedo(R, (132, 126, 112), 0.2, 3302, veins=0.4, vein_col=(84, 88, 96), blotch_col=(88, 72, 54), blotch=0.7)
    R.SSS = M * 0.5
    R.WET = M * 0.35
    R.ROUGH[:] = 18.0
    wr = noise(3303, 0.6)
    wrinkles = np.clip((np.abs(np.sin(v * 70 + wr * 3 + u * 4)) - 0.85) * 6, 0, 1) * M
    R.H -= wrinkles * 0.008
    R.paint(wrinkles * 0.35, (70, 64, 54))
    for ex in (-0.24, 0.24):
        R.paint(g2(u, v, ex, -0.12, 0.17, 0.13) * 0.9, (18, 14, 12))
        pin = ((u - ex) ** 2 + (v + 0.1) ** 2) < 0.014 ** 2
        R.E[pin] = [1.3, 1.25, 0.95]
    # screaming: the jaw hangs open, a black toothless hole, two rotten pegs
    mouth = ell(u, v, 0.02, 0.48, 0.24, 0.3)
    hole = smoothstep(-0.02, 0.06, mouth)
    R.H -= hole * 0.3
    R.A = R.A * (1 - hole[..., None]) + np.array([0.025, 0.008, 0.006]) * hole[..., None]
    lip = np.clip(1 - np.abs(mouth) / 0.035, 0, 1)
    R.paint(lip * 0.7, (86, 62, 56))
    for (tx, ty, tw, th) in ((-0.08, 0.2, 0.035, 0.06), (0.11, 0.21, 0.03, 0.045)):
        peg = (np.abs(u - tx) < tw) & (v > ty) & (v < ty + th)
        R.A[peg] = [0.42, 0.36, 0.2]
        R.H[peg] += 0.03
    R.WET = R.WET * (1 - hole)
    R.H = R.H * (1 - hole) + (R.H.min() - 0.1) * hole
    # bald, liver-spotted scalp and a few long grey hairs
    spots_ = np.clip((noise(3304, 1.4) - 0.9) * 4, 0, 1) * (v < -0.35)
    R.paint(spots_ * 0.6, (74, 58, 40))
    strands(R, 3305, 9, None, color=(150, 146, 136), start_fn=lambda r: (r.uniform(-0.6, 0.5), r.uniform(-0.95, -0.7), 1.3 + r.normal(0, 0.6)),
            length=(0.8, 1.6), droop=0.7, width=(0.6, 1.2), wet=0.2)
    # the hand coming at the lens, out of focus: palm at the lower left, fingers with long yellow nails
    hand = Relief(3306)
    for i, (a0, L) in enumerate(((-0.55, 0.9), (-0.35, 1.05), (-0.15, 1.0), (0.05, 0.85))):
        x0, y0 = -0.95 + i * 0.13, 1.05
        pts = [(x0, y0), (x0 + 0.1 + i * 0.02, y0 - L * 0.4), (x0 + 0.22 + i * 0.05, y0 - L * 0.75), (x0 + 0.3 + i * 0.08, y0 - L * 0.9)]
        pts.append((pts[-1][0] + 0.06 + 0.02 * i, pts[-1][1] - 0.02))
        finger(hand, pts, 0.062, 0.9, (92, 84, 72), nail_col=(184, 154, 64), seed=3307 + i, nail_len=0.3)
    key = ((0.45, 0.3, 0.84), (1.0, 0.97, 0.9), 1.3, 1.0)
    col = R.render([key], ambient=0.05)
    hcol = hand.render([key], ambient=0.06)
    hm = blur(hand.M, 2.0)[..., None]
    hcol = np.stack([blur(hcol[..., c], 2.0) for c in range(3)], -1) / np.maximum(hm, 1e-3)
    col = col * (1 - hm) + hcol * hm
    yy, xx = np.mgrid[0:N, 0:N]
    spot = np.exp(-(((xx - N * 0.55) / (N * 0.55)) ** 2 + ((yy - N * 0.45) / (N * 0.55)) ** 2))
    col *= (0.25 + 0.9 * spot)[..., None]
    return to_tex(post(col, 3309, grain=0.07, ca=2.4, vign=1.0, gamma=1.1, tint=(1.0, 0.98, 0.92), crush=0.03))


# ================================================================================================ 御山様 oyamasama
@texture('gui/jumpscare_oyamasama', size=(256, 256), opaque=True)
def jumpscare_oyamasama():
    """Looking up into it as it bends down: the paper face cloth with its one painted red eye, torn at the bottom,
    and behind it a mouth split open in bark-black skin, splinters for teeth; dead branches spread overhead; the
    shimenawa and its white shide hang below. Lit red by the flare."""
    R = Relief(3401)
    U, V = R.u, R.v
    u, v = U / 1.05, (V - 0.05) / 1.05
    # bark skin face behind the cloth (fills the frame)
    skin = ell(u, v, 0, 0.05, 0.85, 1.05) > -0.05
    bark_layer(R, skin, 3402, base=(0.1, 0.07, 0.055), height=0.3)
    R.H = R.H + np.sqrt(np.clip(ell(u, v, 0, 0.05, 0.85, 1.05), 0, 1)) * 0.3
    # the mouth: a vertical split far too tall, ringed with splinter teeth, wet
    mouth = ell(u, v, 0.02, 0.6, 0.27, 0.42)
    hole = smoothstep(-0.01, 0.05, mouth)
    R.H -= hole * 0.35
    R.A = R.A * (1 - hole[..., None]) + np.array([0.02, 0.0, 0.0]) * hole[..., None]
    R.WET += hole * 1.0
    rt = rng(3403)
    for k in range(34):
        a = rt.uniform(0, 2 * math.pi)
        bx, by = 0.02 + math.cos(a) * 0.27, 0.6 + math.sin(a) * 0.42
        L = rt.uniform(0.05, 0.12)
        pts = [(bx, by), (bx - math.cos(a) * L, by - math.sin(a) * L)]
        finger(R, pts, rt.uniform(0.01, 0.02), 0.3, (150, 128, 96), nail_col=(170, 150, 112), seed=3404 + k, nail_len=0.3, knuckles=())
    # the paper cloth over the upper face, torn along the bottom edge, the red painted eye
    cl_bottom = 0.08 + 0.07 * np.sin(U * 13) + 0.05 * blur(rt.normal(0, 1, (N, N)), 3) * 4 + 0.12 * (U > 0.15) * (U < 0.45)
    cloth = (np.abs(U) < 0.62 + 0.05 * V) & (V < cl_bottom) & (V > -1.2)
    fold = np.sin(U * 9 + V * 2) * 0.5 + noise(3405, 1.3) * 0.5
    R.H = np.where(cloth, 0.62 + 0.03 * fold - 0.08 * (U / 0.6) ** 2, R.H)
    paper = np.array([0.8, 0.77, 0.69]) * (0.9 + 0.1 * fold[..., None]) * (1 - 0.15 * np.clip(noise(3406, 1.5), 0, 1)[..., None])
    R.A = np.where(cloth[..., None], paper, R.A)
    R.M = np.where(cloth, 1.0, R.M)
    R.WET = np.where(cloth, 0.08, R.WET)
    R.SSS = np.where(cloth, 0.0, R.SSS)
    ex, ey = 0.02, -0.32
    ring = np.hypot((U - ex) / 0.24, (V - ey) / 0.15)
    eye_ink = cloth & (np.abs(ring - 0.85) < 0.18 + 0.05 * noise(3407, 0.7))
    R.A[eye_ink] = [0.55, 0.06, 0.04]
    iris = cloth & (np.hypot((U - ex) / 0.075, (V - ey) / 0.075) < 1.0)
    R.A[iris] = [0.45, 0.03, 0.02]
    pup = cloth & (np.hypot((U - ex) / 0.03, (V - ey) / 0.04) < 1.0)
    R.A[pup] = [0.04, 0.0, 0.0]
    for k, dx in enumerate((-0.05, 0.03, 0.09)):              # red ink ran down the paper
        run = cloth & (np.abs(U - ex - dx) < 0.008 + 0.004 * k) & (V > ey + 0.12) & (V < ey + 0.3 + 0.08 * k)
        R.A[run] = [0.5, 0.05, 0.04]
    # dead branches overhead against the dark
    for k in range(9):
        x0 = rt.uniform(-0.8, 0.8)
        pts = [(x0, -0.85), (x0 + rt.normal(0, 0.15), -1.05), (x0 + rt.normal(0, 0.3), -1.3)]
        finger(R, pts, rt.uniform(0.012, 0.025), 0.5, (40, 30, 24), nail_col=(40, 30, 24), seed=3420 + k, knuckles=())
    # shimenawa across the bottom with white shide
    rope = (V > 0.9 + 0.04 * np.sin(U * 3)) & (V < 1.02 + 0.04 * np.sin(U * 3))
    tw = np.sin((U * 30 + V * 30))
    R.A = np.where(rope[..., None], np.array([0.55, 0.45, 0.25]) * (0.75 + 0.25 * tw[..., None]), R.A)
    R.H = np.where(rope, 0.75 + 0.03 * tw, R.H)
    R.M = np.where(rope, 1.0, R.M)
    for sx in (-0.5, 0.15, 0.6):
        for j in range(4):
            seg = (np.abs(U - sx - (0.03 if j % 2 else -0.03)) < 0.05) & (V > 0.98 + j * 0.06) & (V < 1.04 + j * 0.06)
            R.A[seg] = [0.85, 0.83, 0.78]
            R.H[seg] = 0.8
            R.M[seg] = 1.0
    flare = ((0.15, 0.85, 0.5), (1.0, 0.22, 0.12), 1.6, 1.0)
    torch = ((-0.3, 0.4, 0.86), (0.95, 0.95, 1.0), 0.55, 1.0)
    col = R.render([flare, torch], ambient=0.04, amb_col=(0.6, 0.2, 0.2))
    yy, xx = np.mgrid[0:N, 0:N]
    spot = np.exp(-(((xx - N * 0.5) / (N * 0.6)) ** 2 + ((yy - N * 0.55) / (N * 0.6)) ** 2))
    col *= (0.25 + 0.9 * spot)[..., None]
    return to_tex(post(col, 3409, grain=0.07, ca=2.4, vign=1.1, gamma=1.1, tint=(1.05, 0.92, 0.9), crush=0.03))
