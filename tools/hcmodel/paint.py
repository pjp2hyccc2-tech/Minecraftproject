"""Procedural pixel-art texture painter for hcmodels.

Every cube face is painted texel by texel from:
  * a Material: colour ramp (dark -> light) + surface pattern (scales, plates, fur, membrane...)
  * baked light (top faces brighter, lower body darker) quantised to the ramp with ordered dithering
  * 3D "solid" accents computed in model space (stripes / spots continue across neighbouring cubes)
  * decals: eyes, mouth + teeth, nostrils, claws, horn tips, glowing veins...

Each texel also knows its semantic position in its cube:
  f = front fraction (0 back .. 1 front), d = down fraction (0 top .. 1 bottom),
  r = right fraction (0 left side .. 1 entity's right side)
which decals use for placement.
"""
import math

import numpy as np
from PIL import Image

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def mix(a, b, t):
    return a + (b - a) * t


def ramp_from(base, n=6, dark=0.32, light=1.35, hue_shift=0.0):
    """Builds a ramp from one base colour (shadows cooler/darker, lights warmer/brighter)."""
    b = hexrgb(base) if isinstance(base, str) else np.array(base, float)
    out = []
    for i in range(n):
        t = i / (n - 1)
        k = dark + (light - dark) * t
        c = b * k
        # hue shift: shadows toward blue/purple, lights toward yellow
        c = c + np.array([-12, -6, 14]) * (1 - t) * (0.6 + hue_shift) + np.array([14, 10, -8]) * t * (0.4 + hue_shift)
        out.append('#%02x%02x%02x' % tuple(int(max(0, min(255, v))) for v in c))
    return out


class Material:
    def __init__(self, name, ramp, pattern='smooth', scale=1.0, noise=0.06, edge=0.10, belly=None,
                 accent=None, accent_mode=None, accent_freq=0.25, accent_thresh=0.55, glow=None, contrast=1.0,
                 bake=1.0, base=0.55):
        self.name = name
        self.ramp = [hexrgb(c) for c in (ramp if isinstance(ramp, (list, tuple)) else ramp_from(ramp))]
        self.pattern = pattern
        self.scale = scale
        self.noise = noise
        self.edge = edge
        self.belly = belly            # material name for downward / underside texels
        self.accent = accent          # material name used where the accent mask is set
        self.accent_mode = accent_mode  # 'stripes_z', 'stripes_y', 'spots', 'bands_x', 'tips'
        self.accent_freq = accent_freq
        self.accent_thresh = accent_thresh
        self.glow = hexrgb(glow) if glow else None  # emissive colour for pattern "cracks"/"veins"
        self.contrast = contrast
        self.bake = bake
        self.base = base


MATERIALS = {}


def register_material(mat):
    MATERIALS[mat.name] = mat
    return mat


def M(*a, **k):
    return register_material(Material(*a, **k))


# ----------------------------------------------------------------------------- material library
# neutrals / bodies
M('default', '#8a8a8a')
M('bone', ['#4a4033', '#7d6e57', '#a8987c', '#cbbd9f', '#e6dcc4', '#f7f1e2'], 'bone')
M('bone_dark', ['#241d16', '#3e3328', '#5c4c3b', '#7d6a54', '#9c8770', '#b9a68e'], 'bone')
M('horn_dark', ['#120d0b', '#231a15', '#3a2c23', '#574436', '#7a614e', '#a08470'], 'horn')
M('horn_ivory', ['#5a4c3a', '#8a7759', '#b7a37e', '#d8c7a2', '#efe3c6', '#fffaf0'], 'horn')
M('claw', ['#0f0d0c', '#1f1b18', '#35302b', '#514942', '#706760', '#958b83'], 'horn')
M('teeth', ['#6b6455', '#a29a86', '#cfc8b4', '#e8e2d0', '#f7f3e8', '#ffffff'], 'smooth')
M('mouth', ['#1a0606', '#3a0d0f', '#5e1a1c', '#842a2b', '#a84444', '#c86060'], 'skin')
M('tongue', ['#3b0a14', '#6e1426', '#9e2438', '#c94256', '#e06b7b', '#f29aa6'], 'skin')
M('eye_dark', ['#000000', '#0b0b0d', '#18181c', '#26262c', '#36363e', '#4a4a54'], 'smooth')
# fire wyvern (Ignivald)
M('ign_scale', ['#2a0806', '#561008', '#8a1d0c', '#bd3312', '#e05a20', '#f59a48'], 'scales', scale=1.0, belly='ign_belly', accent='ign_dark', accent_mode='stripes_z', accent_freq=0.16, accent_thresh=0.78)
M('ign_dark', ['#120504', '#230806', '#3d0d08', '#5c140b', '#7a1e10', '#9a2a16'], 'scales')
M('ign_plate', ['#2b0a07', '#5a140b', '#8f2611', '#c4401b', '#e66b2c', '#ffa65e'], 'plates', belly='ign_belly')
M('ign_belly', ['#4a2b14', '#7a4d24', '#a87538', '#cf9d55', '#e8c47e', '#fbe4ad'], 'plates', scale=0.7)
M('ign_membrane', ['#1c0505', '#3d0b09', '#661410', '#8f2318', '#b53a24', '#d65d38'], 'membrane', glow=None)
M('ign_spike', ['#140d0a', '#2a1c14', '#46301f', '#6b4a2e', '#946a42', '#c0925d'], 'horn')
# ice fanged beast (Frostgarm)
M('frost_fur', ['#2c3f5c', '#4a6a8f', '#7499bd', '#a3c4e0', '#d0e6f5', '#f4fbff'], 'fur', belly='frost_fur_light')
M('frost_fur_light', ['#55708c', '#7f9db8', '#a9c6dc', '#cde2ef', '#e9f4fa', '#ffffff'], 'fur')
M('frost_hide', ['#1d2533', '#334054', '#4d5d76', '#6c7f99', '#8fa3bb', '#b5c6d9'], 'skin')
M('ice_crystal', ['#1b4d6b', '#2e7aa3', '#4fa9d3', '#86d0ef', '#c2ecfb', '#f5fdff'], 'crystal', glow='#9fe8ff')
# thunder leviathan (Voltrix)
M('volt_scale', ['#0d1c2a', '#173149', '#24496b', '#336590', '#4a86b5', '#6fadd8'], 'scales', belly='volt_belly', accent='volt_stripe', accent_mode='stripes_z', accent_freq=0.22, accent_thresh=0.7)
M('volt_stripe', ['#2c2508', '#5c4c0e', '#9a8016', '#d6b324', '#f2d74a', '#fff08a'], 'scales')
M('volt_belly', ['#3b3a2c', '#5e5c45', '#86836a', '#aba98e', '#cfcdb4', '#ebe9d6'], 'plates', scale=0.7)
M('volt_organ', ['#3a3000', '#7a6400', '#c4a000', '#f2d100', '#fff04a', '#ffffb0'], 'veins', glow='#fff36a')
# brute wyvern (Gravadon)
M('grav_rock', ['#1e1a17', '#36302a', '#524840', '#71655a', '#938677', '#b5a898'], 'rock', belly='grav_hide')
M('grav_hide', ['#2a1f19', '#45342a', '#634c3d', '#836654', '#a3826d', '#c3a08a'], 'skin')
M('grav_moss', ['#14200f', '#22361a', '#355228', '#4d703a', '#6b8f50', '#8fb06c'], 'fur')
M('mud', ['#1f150d', '#3a2717', '#573c24', '#755434', '#946d47', '#b38b60'], 'rock')
# elder dragon (Azragos)
M('azr_scale', ['#0a0612', '#170d26', '#25153d', '#382057', '#4e2f73', '#6a4593'], 'scales', belly='azr_belly', accent='azr_rune', accent_mode='veins3d', accent_freq=0.18, accent_thresh=0.86)
M('azr_plate', ['#07040c', '#140b1f', '#231434', '#341f4c', '#4a2d69', '#64418a'], 'plates', belly='azr_belly')
M('azr_belly', ['#2a1214', '#4a2023', '#6e3134', '#934649', '#b6605f', '#d28480'], 'plates', scale=0.7)
M('azr_rune', ['#4a0c3a', '#86125e', '#c21f7d', '#e9409c', '#ff78c0', '#ffb3df'], 'smooth', glow='#ff5fbf')
M('azr_membrane', ['#0b0510', '#1b0d24', '#2e163b', '#432155', '#5c2f72', '#7a4593'], 'membrane')
M('azr_horn', ['#0c0a0e', '#1c181f', '#302a34', '#4a414f', '#6a5f70', '#8f8396'], 'horn')
M('lava_rock', ['#120c0a', '#221714', '#36241f', '#4d342c', '#664539', '#82594a'], 'rock', glow='#ff8a1f')
# small monster bodies
M('raptor_blue', ['#0c1b2e', '#16304d', '#21486f', '#2f6493', '#4682b3', '#68a6d1'], 'scales', belly='raptor_belly', accent='raptor_stripe', accent_mode='stripes_z', accent_freq=0.35, accent_thresh=0.72)
M('raptor_stripe', ['#2a0d05', '#4f1a0a', '#7d2c12', '#ae451d', '#d6672e', '#f29350'], 'scales')
M('raptor_belly', ['#3d382c', '#615a47', '#8a8267', '#b1aa8c', '#d3cdb2', '#eeead6'], 'plates', scale=0.6)
M('raptor_white', ['#3c4a59', '#5f7286', '#8ba0b4', '#b7c9d8', '#dde9f2', '#ffffff'], 'scales', belly='raptor_belly', accent='raptor_ice', accent_mode='stripes_z', accent_freq=0.35, accent_thresh=0.72)
M('raptor_ice', ['#0d2d48', '#174a72', '#226a9c', '#3a8fc4', '#62b5e2', '#9ad8f7'], 'scales')
M('boar_fur', ['#1e130c', '#352315', '#523620', '#704c2d', '#8f653e', '#ad7f53'], 'fur', belly='boar_fur_light')
M('boar_fur_light', ['#3b2a1d', '#5c4430', '#7f5f45', '#a17d5e', '#bf9b7b', '#d9b99a'], 'fur')
M('deer_fur', ['#2b1d10', '#4b331c', '#6f4c2a', '#93683b', '#b4864f', '#d1a668'], 'fur', belly='deer_belly', accent='deer_spot', accent_mode='spots', accent_freq=0.45, accent_thresh=0.74)
M('deer_belly', ['#5a4c3a', '#857259', '#ad9a7c', '#cfbe9f', '#e8dbc0', '#faf2df'], 'fur')
M('deer_spot', ['#8a7a60', '#b0a080', '#d0c2a2', '#e8dcc0', '#f6eedc', '#ffffff'], 'smooth')
M('turtle_shell', ['#142018', '#21352a', '#30503d', '#436b52', '#5b886a', '#7aa587'], 'plates', scale=1.6)
M('turtle_skin', ['#2a2a1c', '#46452e', '#646244', '#84815c', '#a5a178', '#c6c299'], 'skin')
M('insect_body', ['#0e0d08', '#201d0f', '#3a3414', '#5c5118', '#86761c', '#b39d2a'], 'plates', scale=0.6, accent='insect_stripe', accent_mode='bands_z', accent_freq=0.6, accent_thresh=0.5)
M('insect_stripe', ['#050403', '#0f0d09', '#1c1912', '#2b271d', '#3b362a', '#4c4637'], 'plates', scale=0.6)
M('insect_wing', ['#3d4a52', '#5e7079', '#8299a3', '#a8c0c8', '#cfe2e8', '#f2fbfd'], 'membrane')
M('mammoth_fur', ['#3a2c22', '#5a463a', '#7d6656', '#a08876', '#c2ab99', '#e1cebf'], 'fur', belly='mammoth_fur')
M('gecko_scale', ['#2b0c02', '#521a05', '#80300b', '#b04c13', '#d9701f', '#f8a040'], 'scales', belly='gecko_belly', accent='lava_glow', accent_mode='spots', accent_freq=0.5, accent_thresh=0.8)
M('gecko_belly', ['#4a3418', '#755228', '#9e743c', '#c39a55', '#e0bf77', '#f7e0a6'], 'plates', scale=0.6)
M('lava_glow', ['#5c1600', '#a32c00', '#e04d00', '#ff7a12', '#ffab3d', '#ffd98a'], 'smooth', glow='#ff8a1f')
M('crab_shell', ['#2a0f08', '#4a1c10', '#71301c', '#99492c', '#bf6741', '#de8d63'], 'plates', scale=1.2, belly='crab_belly')
M('crab_belly', ['#5a4436', '#80644f', '#a5886d', '#c6aa8e', '#e2cbb1', '#f7e8d6'], 'plates', scale=0.8)
M('crab_rock', ['#1e1b18', '#37322d', '#534c45', '#736a61', '#958b81', '#b8aea4'], 'rock')
# mofu (cat-folk) furs
M('mofu_white', ['#7a7066', '#a59a8e', '#cbc1b5', '#e5ddd2', '#f5f0ea', '#ffffff'], 'fur', belly='mofu_white')
M('mofu_ginger', ['#4a2410', '#7a3d1a', '#a85a28', '#d07d3c', '#eba35c', '#fbc98a'], 'fur', belly='mofu_white', accent='mofu_ginger_dark', accent_mode='stripes_y', accent_freq=0.6, accent_thresh=0.7)
M('mofu_ginger_dark', ['#2a1208', '#4a200e', '#6e3216', '#924620', '#b45e2e', '#d07d3c'], 'fur')
M('mofu_grey', ['#25262b', '#3c3e45', '#585b64', '#777b86', '#999ea9', '#bcc1cb'], 'fur', belly='mofu_white')
M('mofu_black', ['#0a0a0c', '#16161a', '#24242a', '#34343c', '#46464f', '#5c5c66'], 'fur', belly='mofu_white')
M('mofu_tabby', ['#3a2a1c', '#5c4430', '#806048', '#a27e62', '#c29f82', '#ddc0a4'], 'fur', belly='mofu_white', accent='mofu_ginger_dark', accent_mode='stripes_y', accent_freq=0.7, accent_thresh=0.62)
M('nose_pink', ['#5e2a32', '#8c3f4b', '#b65a67', '#d97d89', '#efa3ad', '#ffccd2'], 'smooth')
# props / gear
M('leather', ['#1f140c', '#3a2616', '#573a22', '#76512f', '#966b40', '#b58955'], 'skin')
M('cloth_red', ['#2a0808', '#4e0f10', '#7a1a1a', '#a62a27', '#cc4a3e', '#e8765f'], 'cloth')
M('cloth_blue', ['#0a1428', '#13254a', '#1f3a70', '#2f5596', '#4775b8', '#6e9bd6'], 'cloth')
M('cloth_cream', ['#5a5040', '#857960', '#ada184', '#cfc4a8', '#e9e0c8', '#fbf6e8'], 'cloth')
M('metal', ['#1c1e22', '#33373e', '#50565f', '#727a85', '#99a2ad', '#c6ced8'], 'metal')
M('gold', ['#3a2600', '#6b4a00', '#a07400', '#d3a20f', '#f2c94a', '#fff0a8'], 'metal')
M('wood', ['#22160c', '#3d2816', '#5c3d22', '#7c5530', '#9d6f40', '#bd8b55'], 'wood')


# ----------------------------------------------------------------------------- noise
def _hash3(ix, iy, iz, seed):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, z, seed=0):
    """Smooth 3D value noise in [0,1] for numpy arrays."""
    x0, y0, z0 = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    def h(a, b, c):
        return _hash3(x0 + a, y0 + b, z0 + c, seed)
    c00 = mix(h(0, 0, 0), h(1, 0, 0), fx)
    c10 = mix(h(0, 1, 0), h(1, 1, 0), fx)
    c01 = mix(h(0, 0, 1), h(1, 0, 1), fx)
    c11 = mix(h(0, 1, 1), h(1, 1, 1), fx)
    return mix(mix(c00, c10, fy), mix(c01, c11, fy), fz)


def fbm(x, y, z, seed=0, octaves=3):
    v, a, f, tot = 0.0, 1.0, 1.0, 0.0
    for o in range(octaves):
        v = v + vnoise(x * f, y * f, z * f, seed + o * 17) * a
        tot += a
        a *= 0.5
        f *= 2.0
    return v / tot


def hash2(i, j, seed=0):
    return _hash3(i, j, np.zeros_like(i), seed)


# ----------------------------------------------------------------------------- patterns (face-local)
def pattern_delta(mat, I, J, w, h, seed, P):
    """Returns (delta value, outline mask) for a face grid I (cols), J (rows)."""
    s = mat.scale
    zero = np.zeros_like(I, dtype=float)
    pat = mat.pattern
    if pat == 'scales':
        sw, sh = max(3, int(round(5 * s))), max(3, int(round(4 * s)))
        row = J // sh
        off = (row % 2) * (sw // 2)
        col = (I + off) // sw
        cx = ((I + off) % sw + 0.5) / sw          # 0..1 across the scale
        cy = ((J % sh) + 0.5) / sh                # 0..1 down the scale
        jit = (hash2(col, row, seed) - 0.5) * 0.16
        # rounded scale: bright upper-centre, dark crescent rim along the lower edge
        rr = (cx - 0.5) ** 2 * 2.2 + (cy - 0.15) ** 2 * 1.2
        d = jit + 0.16 - rr * 0.42
        rim = (cy > 0.72) & (np.abs(cx - 0.5) < 0.42)
        side = (cy > 0.45) & ((cx < 0.12) | (cx > 0.88))
        outline = rim | side
        d = np.where(rim, d - 0.20, d)
        d = np.where(side, d - 0.10, d)
        hl = (cy < 0.3) & (np.abs(cx - 0.4) < 0.18)
        d = np.where(hl, d + 0.10, d)
        return d, outline
    if pat == 'plates':
        pw, ph = max(4, int(round(8 * s))), max(3, int(round(5 * s)))
        row = J // ph
        off = (row % 2) * (pw // 2)
        col = (I + off) // pw
        lx, ly = (I + off) % pw, J % ph
        jit = (hash2(col, row, seed + 3) - 0.5) * 0.12
        d = jit + (0.5 - ly / ph) * 0.16
        d = np.where(ly == 0, d + 0.12, d)
        d = np.where(lx == 0, d + 0.05, d)
        seam = (ly == ph - 1) | (lx == pw - 1)
        d = np.where(seam, d - 0.22, d)
        return d, seam
    if pat == 'fur':
        streak = (hash2(I, I * 0 + 7, seed) - 0.5) * 0.22
        wave = np.sin(J * 0.9 + hash2(I, I * 0 + 3, seed) * 6.28) * 0.07
        clump = (vnoise(I * 0.35, J * 0.12, zero + seed % 97, seed) - 0.5) * 0.25
        d = streak + wave + clump
        tips = ((J + (hash2(I, I * 0 + 11, seed) * 3).astype(int)) % 5 == 0)
        d = np.where(tips, d + 0.08, d)
        return d, np.zeros_like(I, dtype=bool)
    if pat == 'membrane':
        v1 = np.abs(((I * 0.55 + J * 0.25 + hash2(I // 7, I * 0, seed) * 2) % 6) - 3) < 0.45
        v2 = np.abs(((J * 0.6 - I * 0.15) % 9) - 4.5) < 0.35
        veins = v1 | v2
        d = (vnoise(I * 0.15, J * 0.15, zero, seed) - 0.5) * 0.18
        d = np.where(veins, d - 0.2, d + 0.03)
        return d, veins
    if pat == 'horn':
        along = J if h >= w else I
        bands = np.sin(along * 1.3) * 0.08 + (hash2(along, along * 0, seed) - 0.5) * 0.06
        groove = (I % 3 == 0) * -0.05
        return bands + groove, np.zeros_like(I, dtype=bool)
    if pat == 'bone':
        d = (vnoise(I * 0.3, J * 0.3, zero, seed) - 0.5) * 0.16 + (hash2(I, J, seed) - 0.5) * 0.05
        pits = hash2(I, J, seed + 5) > 0.93
        return np.where(pits, d - 0.18, d), np.zeros_like(I, dtype=bool)
    if pat in ('rock',):
        cell = 5.0 * s
        gx, gy = I / cell, J / cell
        ix, iy = np.floor(gx).astype(np.int64), np.floor(gy).astype(np.int64)
        best = np.full(I.shape, 9.0)
        second = np.full(I.shape, 9.0)
        bestv = np.zeros(I.shape)
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                cx = ix + ox
                cy = iy + oy
                px = cx + hash2(cx, cy, seed)
                py = cy + hash2(cx, cy, seed + 9)
                dist = np.sqrt((gx - px) ** 2 + (gy - py) ** 2)
                upd = dist < best
                second = np.where(upd, best, np.minimum(second, dist))
                bestv = np.where(upd, hash2(cx, cy, seed + 21), bestv)
                best = np.where(upd, dist, best)
        crack = (second - best) < 0.12
        d = (bestv - 0.5) * 0.24 + (0.5 - best) * 0.1
        return np.where(crack, d - 0.3, d), crack
    if pat == 'crystal':
        facet = ((I + J) % 7 < 2)
        facet2 = ((I - J) % 9 == 0)
        d = (hash2(I // 3, J // 3, seed) - 0.5) * 0.2
        d = np.where(facet, d + 0.22, d)
        d = np.where(facet2, d - 0.18, d)
        return d, facet
    if pat == 'skin':
        wr = np.sin(J * 1.7 + vnoise(I * 0.2, J * 0.2, zero, seed) * 4) * 0.06
        sp = hash2(I, J, seed + 2) > 0.94
        return np.where(sp, wr - 0.12, wr) + (vnoise(I * 0.25, J * 0.25, zero, seed) - 0.5) * 0.12, np.zeros_like(I, dtype=bool)
    if pat == 'veins':
        v = np.abs(np.sin(I * 0.7 + np.sin(J * 0.5) * 2)) < 0.25
        d = (vnoise(I * 0.3, J * 0.3, zero, seed) - 0.5) * 0.2
        return np.where(v, d + 0.25, d - 0.05), v
    if pat == 'metal':
        band = np.exp(-((J - h * 0.35) ** 2) / max(1.0, (h * 0.12) ** 2)) * 0.25
        return band + (hash2(I, J, seed) - 0.5) * 0.04, np.zeros_like(I, dtype=bool)
    if pat == 'cloth':
        weave = ((I + J) % 2) * 0.05 + (np.sin(J * 0.8) * 0.04)
        return weave + (vnoise(I * 0.2, J * 0.2, zero, seed) - 0.5) * 0.12, np.zeros_like(I, dtype=bool)
    if pat == 'wood':
        grain = np.sin(I * 0.9 + vnoise(I * 0.1, J * 0.05, zero, seed) * 6) * 0.1
        return grain, np.zeros_like(I, dtype=bool)
    return (vnoise(I * 0.2, J * 0.2, zero, seed) - 0.5) * 0.1, np.zeros_like(I, dtype=bool)


# ----------------------------------------------------------------------------- face geometry
def face_grid(cube, face):
    """Returns per-texel bone-local points (N,3), normal, and semantic f/d/r arrays for a face."""
    u0, v0, w, h = cube.face_rects()[face]
    if w <= 0 or h <= 0:
        return None
    ox, oy, oz = cube.origin
    dx, dy, dz = cube.size
    I, J = np.meshgrid(np.arange(w), np.arange(h))
    s = (I + 0.5) / w
    t = (J + 0.5) / h
    minX, minY, minZ = ox, oy, oz
    maxX, maxY, maxZ = ox + dx, oy + dy, oz + dz
    one = np.ones_like(s)
    if face == 'top':
        x, y, z, n = minX + s * dx, minY * one, maxZ - t * dz, (0, -1, 0)
        f, d, r = t, 0 * one, 1 - s
    elif face == 'bottom':
        x, y, z, n = minX + s * dx, maxY * one, maxZ - t * dz, (0, 1, 0)
        f, d, r = t, one, 1 - s
    elif face == 'west':
        x, y, z, n = minX * one, minY + t * dy, maxZ - s * dz, (-1, 0, 0)
        f, d, r = s, t, one
    elif face == 'east':
        x, y, z, n = maxX * one, minY + t * dy, minZ + s * dz, (1, 0, 0)
        f, d, r = 1 - s, t, 0 * one
    elif face == 'north':
        x, y, z, n = minX + s * dx, minY + t * dy, minZ * one, (0, 0, -1)
        f, d, r = one, t, 1 - s
    else:  # south
        x, y, z, n = maxX - s * dx, minY + t * dy, maxZ * one, (0, 0, 1)
        f, d, r = 0 * one, t, s
    pts = np.stack([x, y, z], axis=-1)
    return (u0, v0, w, h, I, J, pts, np.array(n, float), f, d, r)


# ----------------------------------------------------------------------------- decals
def _paint_px(img, glow, u, v, col, glow_col=None):
    H, W = img.shape[:2]
    if 0 <= u < W and 0 <= v < H:
        img[v, u, :3] = col
        img[v, u, 3] = 255
        if glow is not None and glow_col is not None:
            glow[v, u, :3] = glow_col
            glow[v, u, 3] = 255


def apply_decals(cube, img, glow):
    rects = cube.face_rects()
    for dec in cube.decals:
        t = dec.get('type')
        col = hexrgb(dec.get('color', '#ffcc22'))
        gcol = hexrgb(dec['glow']) if isinstance(dec.get('glow'), str) else (col if dec.get('glow') else None)
        dark = hexrgb(dec.get('dark', '#100808'))
        if t == 'eye':
            faces = ['west', 'east'] if dec.get('face', 'side') == 'side' else [dec['face']]
            fx, dy = dec.get('at', (0.8, 0.35))
            size = int(dec.get('size', 3))
            for fc in faces:
                u0, v0, w, h = rects[fc]
                if w < 2 or h < 2:
                    continue
                cx = fx if fc == 'west' else 1 - fx
                cu = u0 + int(round(cx * (w - 1)))
                cv = v0 + int(round(dy * (h - 1)))
                ew, eh = size, max(1, size // 2 + (1 if size >= 3 else 0))
                for a in range(-1, ew + 1):
                    for b in range(-1, eh + 1):
                        uu = cu - ew // 2 + a
                        vv = cv + b
                        if a in (-1, ew) or b in (-1, eh):
                            _paint_px(img, None, uu, vv, dark)
                        else:
                            _paint_px(img, glow, uu, vv, col, gcol)
                # slit pupil + highlight
                pu = cu - ew // 2 + ew // 2
                for b in range(eh):
                    _paint_px(img, glow, pu, cv + b, dark * 0.5, None)
                    if glow is not None:
                        glow[cv + b, pu] = 0
                _paint_px(img, glow, cu - ew // 2, cv, np.minimum(col + 90, 255), gcol)
        elif t == 'eyes_front':
            u0, v0, w, h = rects['north']
            ex, dy = dec.get('at', (0.25, 0.35))
            size = int(dec.get('size', 2))
            for side in (ex, 1 - ex):
                cu = u0 + int(round(side * (w - 1)))
                cv = v0 + int(round(dy * (h - 1)))
                for a in range(size):
                    for b in range(max(1, size - 1)):
                        _paint_px(img, glow, cu - size // 2 + a, cv + b, col, gcol)
                _paint_px(img, None, cu - size // 2 - 1, cv, dark)
                _paint_px(img, None, cu + (size - size // 2), cv, dark)
        elif t == 'mouth':
            faces = ['west', 'east']
            dy = dec.get('at', 0.7)
            reach = dec.get('reach', 0.75)
            teeth = dec.get('teeth', True)
            tooth_col = hexrgb(dec.get('tooth', '#ece6d2'))
            for fc in faces:
                u0, v0, w, h = rects[fc]
                if w < 3 or h < 2:
                    continue
                vv = v0 + int(round(dy * (h - 1)))
                for a in range(w):
                    fr = (a + 0.5) / w
                    front = fr if fc == 'west' else 1 - fr
                    if front < 1 - reach:
                        continue
                    _paint_px(img, None, u0 + a, vv, dark)
                    if teeth and a % 2 == 0:
                        _paint_px(img, None, u0 + a, vv - 1, tooth_col)
                        if vv + 1 < v0 + h:
                            _paint_px(img, None, u0 + a + 1 if a + 1 < w else u0 + a, vv + 1, tooth_col * 0.92)
            # front face mouth line
            u0, v0, w, h = rects['north']
            vv = v0 + int(round(dy * (h - 1)))
            for a in range(w):
                _paint_px(img, None, u0 + a, vv, dark)
                if teeth and a % 2 == 1:
                    _paint_px(img, None, u0 + a, vv - 1, tooth_col)
        elif t == 'teeth':
            fc = dec.get('face', 'north')
            u0, v0, w, h = rects[fc]
            edge = dec.get('edge', 'bottom')
            tooth_col = hexrgb(dec.get('color', '#ece6d2'))
            for a in range(w):
                if a % 2 == 0:
                    vv = v0 + h - 1 if edge == 'bottom' else v0
                    _paint_px(img, None, u0 + a, vv, tooth_col)
                    vv2 = vv - 1 if edge == 'bottom' else vv + 1
                    if a % 4 == 0:
                        _paint_px(img, None, u0 + a, vv2, tooth_col * 0.9)
        elif t == 'nostrils':
            u0, v0, w, h = rects['north']
            dy = dec.get('at', 0.25)
            vv = v0 + int(round(dy * (h - 1)))
            for side in (0.3, 0.7):
                _paint_px(img, None, u0 + int(round(side * (w - 1))), vv, dark)
        elif t in ('claws', 'tip', 'band'):
            pass  # handled per-texel in paint_face
        elif t == 'glow_spot':
            fc = dec.get('face', 'north')
            u0, v0, w, h = rects[fc]
            fx, dy = dec.get('at', (0.5, 0.5))
            rad = dec.get('radius', 1.5)
            cu, cv = u0 + fx * (w - 1), v0 + dy * (h - 1)
            for a in range(w):
                for b in range(h):
                    if (u0 + a - cu) ** 2 + (v0 + b - cv) ** 2 <= rad * rad:
                        _paint_px(img, glow, u0 + a, v0 + b, col, gcol if gcol is not None else col)


# ----------------------------------------------------------------------------- main painter
def paint_model(model, seed=1):
    W, H = model.tex_w, model.tex_h
    img = np.zeros((H, W, 4), dtype=float)
    glow = np.zeros((H, W, 4), dtype=float)
    has_glow = False
    # model height range for AO
    ys = []
    for c in model.all_cubes():
        m = c.bone.rest_matrix()
        for corner in ((c.origin[0], c.origin[1], c.origin[2]), (c.origin[0] + c.size[0], c.origin[1] + c.size[1], c.origin[2] + c.size[2])):
            ys.append((m @ np.array([*corner, 1.0]))[1])
    ymin, ymax = (min(ys), max(ys)) if ys else (0, 24)
    yspan = max(1.0, ymax - ymin)

    for cube in model.all_cubes():
        if cube.mirror_of is not None:
            continue
        mrest = cube.bone.rest_matrix()
        rotm = mrest[:3, :3]
        for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
            g = face_grid(cube, face)
            if g is None:
                continue
            u0, v0, w, h, I, J, pts, n, f, d, r = g
            P = (np.concatenate([pts, np.ones(pts.shape[:2] + (1,))], axis=-1) @ mrest.T)[..., :3]
            N = rotm @ n
            N = N / (np.linalg.norm(N) + 1e-9)
            mat_name = cube.faces.get(face, cube.mat)
            mat = MATERIALS.get(mat_name, MATERIALS['default'])
            # belly: downward-facing faces or the lower part of side faces of belly-flagged materials
            use_belly = mat.belly is not None and (N[1] > 0.55)
            sd = cube.seed * 7 + hash(face) % 101
            rgb, gl = _shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, sd, cube)
            if mat.belly is not None and not use_belly and abs(N[1]) < 0.5:
                # lower 30% of side faces blend into belly colour (with a jagged edge)
                bm = MATERIALS.get(mat.belly)
                if bm is not None and cube.decals is not None and any(dd.get('type') == 'belly_band' for dd in cube.decals):
                    edge = 0.62 + (hash2(I, I * 0, sd) - 0.5) * 0.12
                    mask = d > edge
                    brgb, _ = _shade(bm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 1, cube)
                    rgb = np.where(mask[..., None], brgb, rgb)
            if use_belly:
                rgb, gl = _shade(MATERIALS[mat.belly], I, J, w, h, P, N, f, d, r, ymin, yspan, sd, cube)
            # accent (solid 3D masks)
            if mat.accent and mat.accent_mode:
                am = _accent_mask(mat, P, f, d, r, sd)
                if am is not None and am.any():
                    argb, agl = _shade(MATERIALS[mat.accent], I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 5, cube)
                    rgb = np.where(am[..., None], argb, rgb)
                    if agl is not None:
                        gl = np.where(am[..., None], agl, 0 if gl is None else gl)
            # per-texel decals (claws / tips / bands)
            for dec in cube.decals:
                tp = dec.get('type')
                if tp == 'claws':
                    frac = dec.get('frac', 0.4)
                    cm = MATERIALS[dec.get('mat', 'claw')]
                    crgb, _ = _shade(cm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 9, cube)
                    axis = dec.get('axis', 'front')
                    sel = (f > 1 - frac) if axis == 'front' else (d > 1 - frac)
                    rgb = np.where(sel[..., None], crgb, rgb)
                elif tp == 'tip':
                    frac = dec.get('frac', 0.35)
                    tm = MATERIALS[dec.get('mat', 'horn_ivory')]
                    trgb, _ = _shade(tm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 13, cube)
                    axis = dec.get('axis', 'front')
                    coord = f if axis == 'front' else (1 - f if axis == 'back' else (d if axis == 'down' else 1 - d))
                    noise = (hash2(I, J, sd) - 0.5) * 0.12
                    sel = coord + noise > 1 - frac
                    rgb = np.where(sel[..., None], trgb, rgb)
                elif tp == 'band':
                    lo, hi = dec.get('range', (0.4, 0.6))
                    bm = MATERIALS[dec.get('mat', 'ign_dark')]
                    brgb, bgl = _shade(bm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 17, cube)
                    axis = dec.get('axis', 'front')
                    coord = f if axis == 'front' else d
                    sel = (coord >= lo) & (coord <= hi)
                    rgb = np.where(sel[..., None], brgb, rgb)
                    if bgl is not None:
                        gl = np.where(sel[..., None], bgl, 0 if gl is None else gl)
            if cube.tint is not None:
                rgb = np.clip(rgb * np.array(cube.tint, float), 0, 255)
            keep = np.ones(I.shape, dtype=bool)
            for dec in cube.decals:
                if dec.get('type') == 'cutout':
                    keep &= _cutout(dec, face, f, d, r, I, J, sd)
            img[v0:v0 + h, u0:u0 + w, :3] = rgb
            img[v0:v0 + h, u0:u0 + w, 3] = np.where(keep, 255, 0)
            if cube.glow:
                glow[v0:v0 + h, u0:u0 + w, :3] = rgb
                glow[v0:v0 + h, u0:u0 + w, 3] = 255
                has_glow = True
            elif gl is not None:
                m = (gl[..., 3] > 0) & keep  # no glow on cut-out texels
                region = glow[v0:v0 + h, u0:u0 + w]
                region[m] = gl[m]
                if m.any():
                    has_glow = True
        before = glow[..., 3].sum()
        apply_decals(cube, img, glow)
        if glow[..., 3].sum() != before:
            has_glow = True
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
    gout = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), 'RGBA') if has_glow else None
    return out, gout


def _cutout(dec, face, f, d, r, I, J, seed):
    """Alpha cut-outs for planes / crossed planes. Returns a keep mask.

    shapes:
      spike        triangle pointing up on side faces (apex at top centre)
      spike_back   triangle pointing backward on top/bottom faces (apex at the back)
      spike_front  triangle pointing forward on top/bottom faces
      spike_out    triangle pointing to the cube's +X/-X tip on top/bottom faces (wing fingers, fins)
      scallop      wing trailing edge: arcs cut into the back edge of top/bottom faces
      round        ellipse inscribed in the face
      ragged       random ragged bottom edge (fur fringe / torn membrane)
    """
    shape = dec.get('shape', 'spike')
    side_faces = face in ('west', 'east', 'north', 'south')
    horiz = f if face in ('west', 'east') else r
    if shape == 'spike':
        if not side_faces:
            return np.ones_like(f, dtype=bool)
        return np.abs(horiz - 0.5) * 2 <= d + 0.08
    if shape == 'spike_back':
        if side_faces:
            return np.ones_like(f, dtype=bool)
        return np.abs(r - 0.5) * 2 <= f + 0.06
    if shape == 'spike_front':
        if side_faces:
            return np.ones_like(f, dtype=bool)
        return np.abs(r - 0.5) * 2 <= (1 - f) + 0.06
    if shape == 'spike_out':
        if side_faces:
            return np.ones_like(f, dtype=bool)
        x = 1 - r if dec.get('dir', 'pos') == 'pos' else r
        return np.abs(f - 0.5) * 2 <= (1 - x) + 0.06
    if shape == 'scallop':
        if side_faces:
            return np.ones_like(f, dtype=bool)
        n = dec.get('count', 3)
        depth = dec.get('depth', 0.3)
        span = 1 - r if dec.get('dir', 'pos') == 'pos' else r
        arc = depth * np.abs(np.sin(math.pi * n * span)) + dec.get('taper', 0.0) * span
        back = 1 - f
        return back > arc
    if shape == 'round':
        a = horiz if side_faces else r
        b = d if side_faces else f
        return (a - 0.5) ** 2 + (b - 0.5) ** 2 <= 0.25
    if shape == 'ragged':
        lim = 1 - dec.get('depth', 0.25) * hash2(I, I * 0, seed)
        return d <= lim if side_faces else np.ones_like(f, dtype=bool)
    return np.ones_like(f, dtype=bool)


def _accent_mask(mat, P, f, d, r, seed):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    fq = mat.accent_freq
    th = mat.accent_thresh
    if mat.accent_mode == 'stripes_z':
        v = 0.5 + 0.5 * np.sin(z * fq * 2.2 + vnoise(x * 0.1, y * 0.1, z * 0.1, seed) * 2.5)
        return v > th
    if mat.accent_mode == 'stripes_y':
        v = 0.5 + 0.5 * np.sin(y * fq * 2.2 + x * 0.2 + vnoise(x * 0.15, y * 0.15, z * 0.15, seed) * 2.0)
        return (v > th) & (d < 0.75)
    if mat.accent_mode == 'bands_z':
        v = 0.5 + 0.5 * np.sin(z * fq * 2.0)
        return v > th
    if mat.accent_mode == 'spots':
        v = vnoise(x * fq, y * fq, z * fq, seed)
        return v > th
    if mat.accent_mode == 'veins3d':
        v = np.abs(fbm(x * fq * 0.5, y * fq * 0.5, z * fq * 0.5, seed) - 0.5)
        return v < (1 - th) * 0.25
    if mat.accent_mode == 'tips':
        return f > th
    return None


def _shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed, cube):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    value = np.full(I.shape, mat.base, dtype=float)
    # baked light: up-facing brighter (+Y is down)
    value += -N[1] * 0.10 * mat.bake
    if abs(N[1]) < 0.5:
        value += (0.5 - d) * 0.12 * mat.bake
    # ambient occlusion toward the ground / underside
    value -= np.clip((y - ymin) / yspan, 0, 1) * 0.10 * mat.bake
    delta, outline = pattern_delta(mat, I, J, w, h, seed, P)
    value += delta * mat.contrast
    value += (vnoise(x * 0.35, y * 0.35, z * 0.35, seed) - 0.5) * 2 * mat.noise
    # face edge outline (keeps chunky readability)
    if w >= 3 and h >= 3 and mat.edge > 0:
        edge = (I == 0) | (J == 0) | (I == w - 1) | (J == h - 1)
        value = np.where(edge, value - mat.edge, value)
    n = len(mat.ramp)
    bay = BAYER4[J % 4, I % 4]
    idx = np.clip(np.floor(value * (n - 1) + bay * 0.9), 0, n - 1).astype(int)
    ramp = np.stack(mat.ramp)
    rgb = ramp[idx]
    gl = None
    if mat.glow is not None:
        sel = None
        if mat.pattern in ('rock', 'veins', 'crystal'):
            sel = outline
        elif mat.pattern == 'smooth':
            sel = np.ones_like(I, dtype=bool)
        if sel is not None and sel.any():
            gl = np.zeros(I.shape + (4,))
            gcol = mat.glow
            if mat.pattern == 'rock':
                rgb = np.where(sel[..., None], gcol, rgb)
            gl[sel, :3] = np.where(sel[..., None], gcol if mat.pattern == 'rock' else rgb, 0)[sel]
            gl[sel, 3] = 255
    return rgb, gl
