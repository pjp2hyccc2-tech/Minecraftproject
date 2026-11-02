"""Tiny software rasterizer for offline previews (not shipped).

Draws textured quads with a z-buffer: block-model JSON elements (Minecraft block model rules),
entity box models (exact ModelPart.Cube box-UV mapping) and simple test geometry. Supports
orthographic and perspective cameras, cutout / translucent / additive ("eyes") passes, Minecraft-like
face shading, and a night + flashlight post effect to judge how things read in the dark.
"""
import math

import numpy as np

# ----------------------------------------------------------------------------------------------- math


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], np.float64)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float64)


def affine(R=None, t=None):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    if t is not None:
        M[:3, 3] = t
    return M


def scale_m(sx, sy=None, sz=None):
    sy = sx if sy is None else sy
    sz = sx if sz is None else sz
    return np.diag([sx, sy, sz, 1.0])


def apply(M, pts):
    pts = np.asarray(pts, np.float64)
    return pts @ M[:3, :3].T + M[:3, 3]


def look_at(eye, target, up=(0, 1, 0)):
    eye, target, up = np.asarray(eye, float), np.asarray(target, float), np.asarray(up, float)
    f = target - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, up)
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    V = np.eye(4)
    V[0, :3], V[1, :3], V[2, :3] = r, u, -f
    V[:3, 3] = -V[:3, :3] @ eye
    return V


_MIPS = {}


def mip_level(tex, lvl):
    """Box-filtered mip level (alpha-weighted colour, like a cutout_mipped atlas), cached per array."""
    key = (id(tex), lvl)
    if key in _MIPS and _MIPS[key][0] is tex:
        return _MIPS[key][1]
    cur = tex
    for _ in range(lvl):
        h, w = cur.shape[:2]
        if h < 2 or w < 2:
            break
        c = cur[:h // 2 * 2, :w // 2 * 2].reshape(h // 2, 2, w // 2, 2, 4)
        a = c[..., 3:4]
        rgb = (c[..., :3] * a).sum((1, 3)) / np.maximum(a.sum((1, 3)), 1e-6)
        al = a.mean((1, 3))
        cur = np.concatenate([rgb, al], -1)
    _MIPS[key] = (tex, cur)
    return cur


# ----------------------------------------------------------------------------------------------- canvas
class Canvas:
    """Camera + framebuffer. World units are blocks; camera space looks down -z."""

    def __init__(self, w, h, view, ortho=None, fov=70.0, bg=(24, 26, 30), near=0.01):
        self.w, self.h = w, h
        self.V = view
        self.ortho = ortho      # half-height of the view volume in world units, or None = perspective
        self.fov = fov
        self.near = near
        self.color = np.zeros((h, w, 3), np.float64)
        self.color[:] = bg
        self.depth = np.full((h, w), np.inf)
        self.trans = []         # deferred translucent quads
        self.adds = []          # deferred additive quads
        self.light_dirs = None
        self.mip = False

    # camera space -> screen
    def project(self, pc):
        z = -pc[:, 2]
        if self.ortho:
            s = self.h / 2 / self.ortho
            sx = self.w / 2 + pc[:, 0] * s
            sy = self.h / 2 - pc[:, 1] * s
            return np.stack([sx, sy, z], 1)
        f = (self.h / 2) / math.tan(math.radians(self.fov) / 2)
        zz = np.maximum(z, 1e-6)
        sx = self.w / 2 + pc[:, 0] * f / zz
        sy = self.h / 2 - pc[:, 1] * f / zz
        return np.stack([sx, sy, z], 1)

    def quad(self, world_pts, uvs, tex, shade=1.0, mode='cutout', tint=None, alpha=1.0, double=True):
        """world_pts: 4x3 (counter-clockwise seen from the front), uvs: 4x2 in 0..1, tex: HxWx4 float 0..255."""
        pc = apply(self.V, world_pts)
        if (-pc[:, 2] < self.near).all():
            return
        if not double:
            # corners are TL, TR, BR, BL seen from outside (clockwise) -> this cross product points inwards
            n = np.cross(pc[1] - pc[0], pc[2] - pc[0])
            if self.ortho:
                if n[2] >= 0:
                    return
            elif np.dot(n, pc[0]) <= 0:
                return
        item = (pc, np.asarray(uvs, np.float64), tex, shade, tint, alpha)
        if mode == 'translucent':
            self.trans.append(item)
        elif mode == 'additive':
            self.adds.append(item)
        else:
            self._raster(item, 'cutout')

    def _clip_near(self, pc, uvs):
        """Sutherland-Hodgman clip of a camera-space polygon against z = -near."""
        zc = -self.near
        out_p, out_u = [], []
        n = len(pc)
        for i in range(n):
            a, b = pc[i], pc[(i + 1) % n]
            ua, ub = uvs[i], uvs[(i + 1) % n]
            ina, inb = a[2] <= zc, b[2] <= zc
            if ina:
                out_p.append(a)
                out_u.append(ua)
            if ina != inb:
                t = (zc - a[2]) / (b[2] - a[2])
                out_p.append(a + (b - a) * t)
                out_u.append(ua + (ub - ua) * t)
        return np.array(out_p), np.array(out_u)

    def _raster(self, item, mode):
        pc, uvs, tex, shade, tint, alpha = item
        if not self.ortho and (-pc[:, 2] < self.near).any():
            pc, uvs = self._clip_near(pc, uvs)
            if len(pc) < 3:
                return
        sp = self.project(pc)
        for k in range(1, len(pc) - 1):
            tri = [0, k, k + 1]
            self._tri(sp[tri], pc[tri], uvs[tri], tex, shade, tint, alpha, mode)

    def _tri(self, s, pc, uv, tex, shade, tint, alpha, mode):
        x0, x1 = int(max(0, math.floor(s[:, 0].min()))), int(min(self.w - 1, math.ceil(s[:, 0].max())))
        y0, y1 = int(max(0, math.floor(s[:, 1].min()))), int(min(self.h - 1, math.ceil(s[:, 1].max())))
        if x0 > x1 or y0 > y1:
            return
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xs + 0.5, ys + 0.5
        (ax, ay), (bx, by), (cx, cy) = s[0, :2], s[1, :2], s[2, :2]
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        l0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
        l1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
        l2 = 1 - l0 - l1
        eps = -1e-6
        inside = (l0 >= eps) & (l1 >= eps) & (l2 >= eps)
        if not inside.any():
            return
        z = s[:, 2]
        if self.ortho:
            depth = l0 * z[0] + l1 * z[1] + l2 * z[2]
            u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]
            v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
        else:
            iz = np.array([1 / z[0], 1 / z[1], 1 / z[2]])
            w_ = l0 * iz[0] + l1 * iz[1] + l2 * iz[2]
            depth = 1 / w_
            u = (l0 * uv[0, 0] * iz[0] + l1 * uv[1, 0] * iz[1] + l2 * uv[2, 0] * iz[2]) / w_
            v = (l0 * uv[0, 1] * iz[0] + l1 * uv[1, 1] * iz[1] + l2 * uv[2, 1] * iz[2]) / w_
        th, tw = tex.shape[:2]
        if self.mip and mode == 'cutout':
            # pick a mip level from the texel / pixel density of this triangle
            area_px = abs(den) / 2
            area_tx = abs((uv[1, 0] - uv[0, 0]) * (uv[2, 1] - uv[0, 1]) - (uv[2, 0] - uv[0, 0]) * (uv[1, 1] - uv[0, 1])) / 2 * tw * th
            if area_px > 1e-6 and area_tx > 0:
                lvl = int(max(0, math.floor(0.5 * math.log2(max(area_tx / area_px, 1e-9)))))
                if lvl > 0:
                    tex = mip_level(tex, lvl)
                    th, tw = tex.shape[:2]
        tu = np.clip((u * tw).astype(int), 0, tw - 1)
        tv = np.clip((v * th).astype(int), 0, th - 1)
        texel = tex[tv, tu]
        a = texel[..., 3] / 255.0 * alpha
        zb = self.depth[y0:y1 + 1, x0:x1 + 1]
        cb = self.color[y0:y1 + 1, x0:x1 + 1]
        rgb = texel[..., :3] * shade
        if tint is not None:
            rgb = rgb * np.asarray(tint)[None, None, :]
        if mode == 'cutout':
            m = inside & (a >= 0.1) & (depth < zb)
            cb[m] = rgb[m]
            zb[m] = depth[m]
        elif mode == 'translucent':
            m = inside & (a > 0.003) & (depth < zb)
            cb[m] = cb[m] * (1 - a[m, None]) + rgb[m] * a[m, None]
        else:  # additive, no depth write
            m = inside & (depth < zb + 1e-4)
            cb[m] = cb[m] + texel[..., :3][m] * alpha

    def finish(self):
        def dist(item):
            return -item[0][:, 2].mean()
        for it in sorted(self.trans, key=dist, reverse=True):
            self._raster(it, 'translucent')
        for it in self.adds:
            self._raster(it, 'additive')
        self.trans, self.adds = [], []
        return self

    def night(self, flashlight=True, strength=1.0, fog=(30, 34, 42), fog_dist=14.0, cone=0.35, amb=0.10):
        """Cheap in-game-ish night look: dark cold ambient, flashlight cone from the camera, fog."""
        h, w = self.h, self.w
        ys, xs = np.mgrid[0:h, 0:w]
        r = np.hypot((xs - w / 2) / (h / 2), (ys - h / 2) / (h / 2))
        d = np.where(np.isfinite(self.depth), self.depth, 1e3)
        lit = np.full((h, w), amb)
        if flashlight:
            spot = np.exp(-(r / cone) ** 2) * 1.5 + np.exp(-(r / (cone * 2.2)) ** 2) * 0.5
            fall = 1.0 / (1.0 + (d / 9.0) ** 2)
            lit = lit + spot * fall * strength
        col = self.color * lit[..., None] * np.array([0.92, 0.95, 1.05])
        fogk = 1 - np.exp(-(d / fog_dist) ** 2)
        fogk = np.where(np.isfinite(self.depth), fogk, 1.0)
        col = col * (1 - fogk[..., None]) + np.asarray(fog) * fogk[..., None] * 0.6
        self.color = col
        return self

    def image(self, down=1):
        from PIL import Image
        img = Image.fromarray(np.clip(self.color, 0, 255).astype(np.uint8), 'RGB')
        if down > 1:
            img = img.resize((self.w // down, self.h // down), Image.LANCZOS)
        return img


# ----------------------------------------------------------------------------------------------- block models
FACE_SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def _face_corners(fr, to, face):
    """4 corners (px) of a box face, ordered top-left, top-right, bottom-right, bottom-left as seen from outside,
    matching the default UV layout of block models."""
    x0, y0, z0 = fr
    x1, y1, z1 = to
    if face == 'north':
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if face == 'south':
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if face == 'west':
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)]
    if face == 'east':
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
    if face == 'up':
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    if face == 'down':
        return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]
    raise ValueError(face)


def _default_uv(fr, to, face):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    if face in ('north', 'south'):
        u0, u1 = (16 - x1, 16 - x0) if face == 'north' else (x0, x1)
        return [u0, 16 - y1, u1, 16 - y0]
    if face in ('east', 'west'):
        u0, u1 = (16 - z1, 16 - z0) if face == 'east' else (z0, z1)
        return [u0, 16 - y1, u1, 16 - y0]
    if face == 'up':
        return [x0, z0, x1, z1]
    return [x0, 16 - z1, x1, 16 - z0]


def _uv_corners(uv, rotation=0):
    u0, v0, u1, v1 = uv
    c = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]      # face corners TL, TR, BR, BL
    k = (rotation // 90) % 4                            # texture turned clockwise (FaceBakery semantics)
    if k:
        c = c[-k:] + c[:-k]
    return np.array(c, np.float64) / 16.0


def resolve_tex(model, ref, textures_lookup):
    t = ref
    seen = 0
    while t.startswith('#') and seen < 10:
        t = model['textures'][t[1:]]
        seen += 1
    return textures_lookup(t)


def element_quads(model, textures_lookup, M=None):
    """Yield (world_pts 4x3 in blocks, uvs 4x2, tex, face, shade_flag) for every face of a block model."""
    M = np.eye(4) if M is None else M
    for el in model.get('elements', []):
        fr, to = el['from'], el['to']
        rot = el.get('rotation')
        for face, fd in el['faces'].items():
            pts = np.array(_face_corners(fr, to, face), np.float64)
            if rot:
                o = np.array(rot['origin'], np.float64)
                ang = math.radians(rot['angle'])
                R = {'x': rot_x, 'y': rot_y, 'z': rot_z}[rot['axis']](ang)
                p = (pts - o) @ R.T
                if rot.get('rescale'):
                    k = 1 / math.cos(abs(ang))
                    sc = np.ones(3)
                    for i, ax in enumerate('xyz'):
                        if ax != rot['axis']:
                            sc[i] = k
                    p = p * sc
                pts = p + o
            uv = fd.get('uv') or _default_uv(fr, to, face)
            uvs = _uv_corners(uv, fd.get('rotation', 0))
            tex = resolve_tex(model, fd['texture'], textures_lookup)
            yield apply(M, pts / 16.0), uvs, tex, face, el.get('shade', True), fd


def draw_block_model(cv, model, textures_lookup, M=None, mode='cutout', light=1.0):
    for pts, uvs, tex, face, shade, fd in element_quads(model, textures_lookup, M):
        s = FACE_SHADE.get(face, 1.0) if shade else 1.0
        glow = 'forge_data' in fd
        cv.quad(pts, uvs, tex, shade=1.0 if glow else s * light, mode=mode)


def cube(cv, pos, tex_for_face, light=1.0, mode='cutout'):
    """A full block at integer position pos (world), tex_for_face(face) -> tex array."""
    x, y, z = pos
    for face in ('north', 'south', 'east', 'west', 'up', 'down'):
        pts = np.array(_face_corners((0, 0, 0), (16, 16, 16), face), np.float64) / 16.0 + np.array([x, y, z])
        uvs = _uv_corners(_default_uv((0, 0, 0), (16, 16, 16), face))
        cv.quad(pts, uvs, tex_for_face(face), shade=FACE_SHADE[face] * light, mode=mode, double=False)


# ----------------------------------------------------------------------------------------------- entity boxes
def box_polys(ox, oy, oz, dx, dy, dz, u, v, mirror=False, grow=0.0):
    """Minecraft 1.20.1 ModelPart.Cube: returns list of (direction, 4 vertices (px, model space), 4 uvs (texels))."""
    x0, y0, z0 = ox - grow, oy - grow, oz - grow
    x1, y1, z1 = ox + dx + grow, oy + dy + grow, oz + dz + grow
    if mirror:
        x0, x1 = x1, x0
    v7 = (x0, y0, z0)
    v0 = (x1, y0, z0)
    v1 = (x1, y1, z0)
    v2 = (x0, y1, z0)
    v3 = (x0, y0, z1)
    v4 = (x1, y0, z1)
    v5 = (x1, y1, z1)
    v6 = (x0, y1, z1)
    f4 = u
    f5 = u + dz
    f6 = u + dz + dx
    f7 = u + dz + dx + dx
    f8 = u + dz + dx + dz
    f9 = u + dz + dx + dz + dx
    f10 = v
    f11 = v + dz
    f12 = v + dz + dy
    polys = [
        ('down', [v4, v3, v7, v0], (f5, f10, f6, f11)),
        ('up', [v1, v2, v6, v5], (f6, f11, f7, f10)),
        ('west', [v7, v3, v6, v2], (f4, f11, f5, f12)),
        ('north', [v0, v7, v2, v1], (f5, f11, f6, f12)),
        ('east', [v4, v0, v1, v5], (f6, f11, f8, f12)),
        ('south', [v3, v4, v5, v6], (f8, f11, f9, f12)),
    ]
    out = []
    for d, verts, (u1, v1_, u2, v2_) in polys:
        uvs = [(u2, v1_), (u1, v1_), (u1, v2_), (u2, v2_)]
        if mirror:
            verts = verts[::-1]
            uvs = uvs[::-1]
        out.append((d, verts, uvs))
    return out


# entity lighting (Lighting.setupFor3DItems-ish): two lights, ambient 0.4
_L0 = np.array([0.2, 1.0, -0.7])
_L0 /= np.linalg.norm(_L0)
_L1 = np.array([-0.2, 1.0, 0.7])
_L1 /= np.linalg.norm(_L1)


def entity_shade(n):
    n = n / (np.linalg.norm(n) + 1e-12)
    return min(1.0, 0.4 + 0.6 * (max(0.0, float(n @ _L0)) + max(0.0, float(n @ _L1))))


ENTITY_TO_WORLD = affine(np.diag([-1.0, -1.0, 1.0]) / 16.0, [0, 1.501, 0])
