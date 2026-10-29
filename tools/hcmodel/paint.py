"""Procedural texture painter for hcmodels (pixel-art 1x mode and high-detail HD mode).

Every cube face is painted texel by texel from:
  * a Material: colour ramp (dark -> light) + surface pattern (scales, plates, fur, membrane...)
  * baked light (top faces brighter, lower body darker), crevice / edge ambient occlusion
  * 3D "solid" accents computed in model space (stripes / spots continue across neighbouring cubes)
  * decals: eyes, mouth + teeth, nostrils, claws, horn tips, glowing veins...

HD mode (TEXEL > 1, the default): the PNG has TEXEL x TEXEL texels per model pixel.  Patterns are
evaluated in continuous model-pixel coordinates, so a scale that was 5x4 texels is now a rounded,
overlapping scale of 20x16 texels with its own gradient, rim light and specular hot-spot; plates get
bevels and scratches, fur gets strands, membranes get veins, horns get growth rings, rock gets cracks,
crystals get facets, skin gets pores and wrinkles, metal gets brushed anisotropy, cloth gets a weave.
Colours are interpolated continuously along the ramp (no Bayer dithering), with multi-octave colour
variation, baked crevice AO at face edges / cut-out edges and height-field lighting (subtle: the game
lights the model too).  The model JSON keeps its 1x texture_width/height: Minecraft samples entity
textures with normalised UVs, so the PNG can be any multiple.

LabPBR maps (PBR = True): `<name>_n.png` (RG = tangent normal from the material height field, OpenGL Y+,
B = ambient occlusion, A = height) and `<name>_s.png` (R = perceptual smoothness, G = F0 / metal id,
B = porosity (0-64) or subsurface (65-255), A = emissive (255 = none)) for the shader pack.

Each texel also knows its semantic position in its cube:
  f = front fraction (0 back .. 1 front), d = down fraction (0 top .. 1 bottom),
  r = right fraction (0 left side .. 1 entity's right side)
which decals use for placement.
"""
import math

import numpy as np
from PIL import Image

TEXEL = 4          # HD texel density multiplier (1 = legacy pixel-art painter)
PBR = True         # also produce LabPBR _n / _s maps in HD mode
HD_BLUR = 0.4      # final anti-aliasing blur of the HD colour map (texels); keeps distant mips calmer

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16.0

_CUR_K = 1         # texel factor of the painting in progress (read by _shade when no texel= is given)
_CUR_DETAIL = 1.0  # feature-size multiplier of the painting in progress (small models get finer patterns)
_LAST = {}         # extra outputs of the last _shade call (height, ao, pbr) for the PBR maps
_CUR_N = None      # model-space normal of the face being painted (read by _accent_mask for iso-surface veins)


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def mix(a, b, t):
    return a + (b - a) * t


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


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


# per-pattern HD defaults: gloss (specular strength), shininess, relief (height-field strength in model px),
# and LabPBR values (smoothness 0-255, f0 0-255, sss/porosity byte)
PATTERN_HD = {
    'scales':   dict(gloss=0.55, shine=22, relief=0.55, smooth=150, f0=16, por=90),
    'plates':   dict(gloss=0.45, shine=18, relief=0.60, smooth=140, f0=16, por=80),
    'fur':      dict(gloss=0.08, shine=6, relief=0.35, smooth=60, f0=8, por=120),
    'membrane': dict(gloss=0.30, shine=14, relief=0.30, smooth=120, f0=14, por=200),
    'horn':     dict(gloss=0.60, shine=26, relief=0.40, smooth=165, f0=18, por=70),
    'bone':     dict(gloss=0.25, shine=10, relief=0.40, smooth=110, f0=12, por=30),
    'rock':     dict(gloss=0.06, shine=6, relief=0.80, smooth=45, f0=8, por=40),
    'crystal':  dict(gloss=0.95, shine=40, relief=0.70, smooth=235, f0=24, por=180),
    'skin':     dict(gloss=0.30, shine=12, relief=0.35, smooth=105, f0=12, por=100),
    'veins':    dict(gloss=0.50, shine=20, relief=0.40, smooth=160, f0=14, por=160),
    'metal':    dict(gloss=0.85, shine=34, relief=0.25, smooth=205, f0=230, por=0),
    'cloth':    dict(gloss=0.02, shine=4, relief=0.25, smooth=40, f0=6, por=100),
    'wood':     dict(gloss=0.15, shine=8, relief=0.35, smooth=80, f0=10, por=50),
    'smooth':   dict(gloss=0.40, shine=16, relief=0.20, smooth=150, f0=14, por=90),
}


class Material:
    def __init__(self, name, ramp, pattern='smooth', scale=1.0, noise=0.06, edge=0.10, belly=None,
                 accent=None, accent_mode=None, accent_freq=0.25, accent_thresh=0.55, glow=None, contrast=1.0,
                 bake=1.0, base=0.55, gloss=None, relief=None, hue_var=0.035, micro=1.0, pbr=None):
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
        # HD-only knobs (None = pattern default)
        self.gloss = gloss
        self.relief = relief
        self.hue_var = hue_var        # amplitude of the low-frequency hue/saturation variation
        self.micro = micro            # amplitude multiplier of the fine (sub-texel) detail
        self.pbr = pbr or {}          # overrides: smooth, f0, por (LabPBR bytes)

    def hd(self, key):
        v = getattr(self, key, None)
        if v is not None:
            return v
        return PATTERN_HD.get(self.pattern, PATTERN_HD['smooth'])[key]

    def pbr_byte(self, key):
        if key in self.pbr:
            return self.pbr[key]
        return PATTERN_HD.get(self.pattern, PATTERN_HD['smooth'])[key]


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
M('mouth', ['#1a0606', '#3a0d0f', '#5e1a1c', '#842a2b', '#a84444', '#c86060'], 'skin', gloss=0.6)
M('tongue', ['#3b0a14', '#6e1426', '#9e2438', '#c94256', '#e06b7b', '#f29aa6'], 'skin', gloss=0.7)
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
M('gold', ['#3a2600', '#6b4a00', '#a07400', '#d3a20f', '#f2c94a', '#fff0a8'], 'metal', pbr={'f0': 231})
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


def vnoise2(x, y, seed=0):
    """2D value noise in [0,1]."""
    x0, y0 = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    def h(a, b):
        return hash2(x0 + a, y0 + b, seed)
    return mix(mix(h(0, 0), h(1, 0), fx), mix(h(0, 1), h(1, 1), fx), fy)


def fbm2(x, y, seed=0, octaves=3, gain=0.5):
    v, a, f, tot = 0.0, 1.0, 1.0, 0.0
    for o in range(octaves):
        v = v + vnoise2(x * f, y * f, seed + o * 23) * a
        tot += a
        a *= gain
        f *= 2.0
    return v / tot


def voronoi2(x, y, seed, jitter=1.0):
    """Nearest / second-nearest feature distances and the cell hash for unit cells."""
    ix, iy = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    best = np.full(x.shape, 9.0)
    second = np.full(x.shape, 9.0)
    bestv = np.zeros(x.shape)
    bpx = np.zeros(x.shape)
    bpy = np.zeros(x.shape)
    for ox in (-1, 0, 1):
        for oy in (-1, 0, 1):
            cx = ix + ox
            cy = iy + oy
            px = cx + 0.5 + (hash2(cx, cy, seed) - 0.5) * jitter
            py = cy + 0.5 + (hash2(cx, cy, seed + 9) - 0.5) * jitter
            dist = np.sqrt((x - px) ** 2 + (y - py) ** 2)
            upd = dist < best
            second = np.where(upd, best, np.minimum(second, dist))
            bestv = np.where(upd, hash2(cx, cy, seed + 21), bestv)
            bpx = np.where(upd, px, bpx)
            bpy = np.where(upd, py, bpy)
            best = np.where(upd, dist, best)
    return best, second, bestv, bpx, bpy


# ----------------------------------------------------------------------------- patterns (face-local, 1x legacy)
def pattern_delta(mat, I, J, w, h, seed, P):
    """Returns (delta value, outline mask) for a face grid I (cols), J (rows). 1x pixel-art version."""
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


# ----------------------------------------------------------------------------- patterns (HD, continuous)
def isoline(n, X, Y, width, level=0.5, soft=0.3):
    """Soft line (0..1) of uniform width (model px) along the iso-contour n == level of a noise field."""
    step = max(1e-6, float(X[0, 1] - X[0, 0])) if X.shape[1] > 1 else 1.0
    gy, gx = np.gradient(n)
    grad = np.sqrt(gx * gx + gy * gy) / step + 1e-6
    dist = np.abs(n - level) / grad
    return 1 - smoothstep(width * 0.5, width * 0.5 + soft, dist)


def _hash1(i, seed):
    return float(_hash3(np.int64(i), np.int64(0), np.int64(0), seed))


def _fmax():
    """Highest useful spatial frequency (cycles per pattern px): features finer than ~2.5 texels turn to mush."""
    return max(0.4, _CUR_K * _CUR_DETAIL / 2.5)


def _grain(X, Y, seed, amp, mat):
    f = min(1.2, _fmax())
    return (vnoise2(X * f, Y * f, seed) - 0.5) * amp * 0.6 * mat.micro


def _edge_width(px, K):
    """Width of a 'thin line' in pattern px: about one texel, never thinner than 0.035 of a feature."""
    return max(0.035, 0.9 / max(1.0, px * K))


def _scales_hd(mat, X, Y, fw, fh, seed, out):
    """Overlapping reptile scales: staggered rows of rounded (U-shaped) scales, each a soft dome with a lit
    shoulder, a glossy hot-spot, faint concentric growth arcs, a thin lit rim along its free edge and the
    shadow it casts onto the row below.  Rows undulate slightly so the lattice is not ruler-straight."""
    s = mat.scale
    sw, sh = 5.0 * s, 4.0 * s
    Yw = Y + np.sin(X * 0.22 / s + vnoise2(X * 0.05 / s, Y * 0.05 / s, seed + 9) * 3.0) * 0.16 * s

    def edge_of(cx, coli, rowi):
        a = np.clip(2 * cx - 1, -1, 1)
        jit = (hash2(coli, rowi, seed + 7) - 0.5) * 0.08
        return 0.58 + 0.42 * np.sqrt(np.clip(1 - a * a, 0, 1)) + jit     # round U: 1 at the centre, 0.58 at the sides

    rowf = Yw / sh
    row = np.floor(rowf)
    cy = rowf - row
    off = (row.astype(np.int64) % 2) * 0.5
    colf = X / sw + off
    col = np.floor(colf)
    cx = colf - col
    yedge = edge_of(cx, col.astype(np.int64), row.astype(np.int64))
    below = cy > yedge
    row2 = row + 1
    off2 = (row2.astype(np.int64) % 2) * 0.5
    colf2 = X / sw + off2
    col2 = np.floor(colf2)
    cx2 = colf2 - col2
    row = np.where(below, row2, row)
    col = np.where(below, col2, col)
    cx = np.where(below, cx2, cx)
    cy = np.where(below, cy - 1, cy)
    coli, rowi = col.astype(np.int64), row.astype(np.int64)
    yedge = edge_of(cx, coli, rowi)
    t = (cy + 0.42) / (yedge + 0.42)                      # 0 hidden top .. 1 free edge
    a = np.clip(2 * cx - 1, -1, 1)
    dome = np.sqrt(np.clip(1 - (a * 0.94) ** 2, 0, 1))
    jit = (hash2(coli, rowi, seed) - 0.5) * 0.12
    overlap = 1 - smoothstep(0.0, 0.32, t)                # shadow of the scales above
    ew = _edge_width(sh, _CUR_K * _CUR_DETAIL)
    rim = smoothstep(0.90 - 3 * ew, 0.93 - ew, t) * (1 - smoothstep(0.95 - ew, 1.0 - ew * 0.5, t))
    edge = smoothstep(1.0 - ew, 1.0, t)
    spec = np.exp(-(((cx - 0.44) / 0.22) ** 2 + ((t - 0.40) / 0.22) ** 2))
    growth = np.sin(t * 19.0 + a * a * 2.0) * 0.012 * smoothstep(0.3, 0.8, t) * mat.micro
    d = jit + 0.10 - t * 0.24 + dome * 0.15 - overlap * 0.24 + rim * 0.10 + spec * 0.10 - edge * 0.2 + growth
    d += np.exp(-(a / 0.14) ** 2) * 0.02                  # faint keel
    d += _grain(X, Y, seed + 4, 0.03, mat)
    out['height'] = np.clip(dome * 0.45 + (1 - t) * 0.45 - overlap * 0.35 - edge * 0.3 + 0.1, 0, 1)
    out['outline'] = t > 1.0 - ew
    out['ao'] = overlap * 0.55 + edge * 0.5
    hv = (hash2(coli, rowi, seed + 3) - 0.5) * 0.08
    out['tint'] = np.stack([1 + hv, 1 + hv * 0.3, 1 - hv], -1)
    return d


def _plates_hd(mat, X, Y, fw, fh, seed, out):
    """Overlapping armour scutes: rows of broad rounded plates with a random stagger per row; every plate
    overlaps the row below (shadow under its lower edge, a lit bevel just inside it) and its right-hand
    neighbour.  Lower edges are shallow arcs with the odd chip; the surface has small pits and scratches."""
    s = mat.scale
    pw, ph = 7.0 * s, 5.0 * s
    K = _CUR_K * _CUR_DETAIL
    # organic warp: rows undulate, column seams wobble (no straight brick courses)
    Yw = Y + np.sin(X * 0.3 / s + vnoise2(X * 0.07 / s, Y * 0.07 / s, seed + 9) * 4.0) * 0.24 * s
    Xw = X + (vnoise2(X * 0.2 / s + 3.0, Y * 0.2 / s, seed + 8) - 0.5) * 1.5 * s

    def plate(rowf, X):
        row = np.floor(rowf)
        rowi = row.astype(np.int64)
        cy = rowf - row
        off = hash2(rowi, rowi * 0, seed + 1)
        colf = X / pw + off
        col = np.floor(colf)
        coli = col.astype(np.int64)
        cx = colf - col
        a = np.abs(2 * cx - 1)
        arc = 0.06 + hash2(coli, rowi, seed + 2) * 0.10
        yedge = 1 - arc * a * a - 2.2 * np.clip(a - 0.7, 0, 1) ** 2      # shallow arc with rounded corners
        chip_pos = 0.15 + hash2(coli, rowi, seed + 3) * 0.7
        has_chip = hash2(coli, rowi, seed + 4) > 0.55
        chip_d = 0.06 + hash2(coli, rowi, seed + 5) * 0.08
        yedge = yedge - chip_d * np.exp(-((cx - chip_pos) / 0.045) ** 2) * has_chip
        return rowi, coli, cx, cy, yedge

    rowf = Yw / ph
    rowi, coli, cx, cy, yedge = plate(rowf, Xw)
    below = cy > yedge
    rowi2, coli2, cx2, cy2, yedge2 = plate(rowf + 1, Xw)
    rowi = np.where(below, rowi2, rowi)
    coli = np.where(below, coli2, coli)
    cx = np.where(below, cx2, cx)
    cy = np.where(below, cy2 - 1, cy)
    yedge = np.where(below, yedge2, yedge)
    t = (cy + 0.32) / (yedge + 0.32)                       # 0 hidden top .. 1 lower edge
    a = np.abs(2 * cx - 1)
    ew = _edge_width(ph, K)
    overlap = (1 - smoothstep(0.0, 0.28, t)) * 0.20        # shadow cast by the plate above
    edge = smoothstep(1 - 0.9 * ew, 1.0, t)
    # distances (model px) to the lower edge and to the side seams -> bevelled rim all around the plate
    sl = cx * pw
    sr = (1 - cx) * pw
    db = np.clip((yedge - cy) * ph, 0, None)
    inside = np.minimum(np.minimum(sl, sr), db)
    bevel = smoothstep(0.0, 1.0 * s, inside)
    seam = 1 - smoothstep(0.0, 0.14 * s, np.minimum(sl, sr))
    lshadow = (1 - smoothstep(0.0, 0.6 * s, sl)) * 0.10    # the left neighbour lies over this plate's edge
    dome = 1 - a * a * 0.22 - ((t - 0.5) * 2) ** 2 * 0.14
    keel = np.exp(-((cx - 0.5) / 0.10) ** 2) * smoothstep(0.12, 0.4, t) * (1 - smoothstep(0.8, 0.97, t)) * (hash2(coli, rowi, seed + 13) > 0.35)
    jit = (hash2(coli, rowi, seed + 6) - 0.5) * 0.11
    # sparse pits and hairline scratches + micro grain
    pc = max(2.4 * s, 2.0 / _fmax())
    pb, _, _, _, _ = voronoi2(X / pc, Y / pc, seed + 7)
    pit_gate = hash2(np.floor(X / pc).astype(np.int64), np.floor(Y / pc).astype(np.int64), seed + 8) > 0.92
    pits = (1 - smoothstep(0.06, 0.15, pb)) * pit_gate
    scr = np.abs(fbm2(X * 0.9 + rowi * 3.1, Y * 0.14 + coli * 1.7, seed + 10, 2) - 0.5)
    scratch = (1 - smoothstep(0.0, 0.014, scr)) * smoothstep(0.1, 0.4, t) * bevel
    d = jit + dome * 0.12 - overlap - edge * 0.22 - seam * 0.18 - lshadow + (bevel - 0.8) * 0.06 + keel * 0.04
    d += -pits * 0.12 * mat.micro + scratch * 0.04 * mat.micro + _grain(X, Y, seed + 11, 0.03, mat)
    out['height'] = np.clip(0.2 + bevel * 0.6 * dome - overlap * 0.8 + keel * 0.18 - pits * 0.3, 0, 1)
    out['outline'] = edge > 0.5
    out['ao'] = overlap * 1.8 + edge * 0.5 + seam * 0.6 + lshadow + pits * 0.4
    hv = (hash2(coli, rowi, seed + 12) - 0.5) * 0.06
    out['tint'] = np.stack([1 + hv, 1 + hv * 0.4, 1 - hv], -1)
    return d


def _fur_hd(mat, X, Y, fw, fh, seed, out, face):
    """Fur: thin strands hang along the flow (down the flanks, swept slightly backward; head-to-tail on top
    faces) and are bundled into locks; every strand ends in a pale tip with the shaded root of the next strand
    beneath it, the ends being loosely staggered so the coat breaks into soft ragged tufts."""
    s = mat.scale
    # small models get finer patterns (X is already divided by the detail factor); fur locks only shrink by
    # the square root of it so tufts stay readable on a 1-block monster
    k = math.sqrt(_CUR_DETAIL)
    X, Y, fw, fh = X * k, Y * k, fw * k, fh * k
    if face in ('top', 'bottom'):
        Y = fh - Y                                        # flow from the head toward the tail
    slant = 0.2 if face == 'west' else (-0.2 if face == 'east' else 0.0)
    sway = (fbm2(X * 0.04 / s, Y * 0.03 / s, seed, 2) - 0.5) * 5.0 * s
    Xs = X + Y * slant + sway + np.sin(Y * 0.25 / s + X * 0.05 / s) * 0.25 * s
    fm = _fmax()
    lw, lh = 2.7 * s, 5.0 * s                            # lock (tuft) width / nominal length

    def lock(rowf, Xs, shift=0):
        row = np.floor(rowf) - shift
        rowi = row.astype(np.int64)
        off = (rowi % 2) * 0.5
        colf = Xs / lw + off
        col = np.floor(colf)
        coli = col.astype(np.int64)
        cx = colf - col
        jx = (hash2(coli, rowi, seed + 1) - 0.5) * 0.5
        ln = 0.85 + hash2(coli, rowi, seed + 2) * 0.7      # lock length: tips reach into the next row
        u = (rowf - row) / ln                              # 0 root .. 1 tip
        a = (cx - 0.5 - jx) * 2.0                          # -1..1 across the lock
        half = 1.3 - 1.2 * np.clip(u, 0, 1) ** 1.3         # wide at the root (locks overlap), tapers to a point
        half = half * (1 + 0.22 * (vnoise2(u * 3.0 + coli * 0.37, rowi * 0.61 + 2.0, seed + 12) - 0.5))
        return u, a, half, coli, rowi

    # rows drift up and down along the flow so the tips never line up in horizontal courses
    rowf = Y / lh + (fbm2(Xs * 0.12 / s + 4.0, Y * 0.02 / s, seed + 9, 2) - 0.5) * 1.4
    u0, a0, h0, c0, r0 = lock(rowf, Xs)
    u1, a1, h1, c1, r1 = lock(rowf, Xs, 1)                 # lock of the row above (its tip hangs over this row)
    rel1 = np.where(u1 <= 1.0, np.abs(a1) / np.maximum(h1, 1e-3), 9.0)
    rel0 = np.abs(a0) / np.maximum(h0, 1e-3)
    cov1 = 1 - smoothstep(0.8, 1.0, rel1)                  # upper lock lies over everything
    cov0 = (1 - smoothstep(0.8, 1.0, rel0)) * (1 - cov1)   # own lock
    under = 1 - cov0 - cov1                                # undercoat showing between lock tips
    u = np.where(cov1 > 0.5, u1, u0)
    rel = np.where(cov1 > 0.5, rel1, rel0)
    coli = np.where(cov1 > 0.5, c1, c0)
    rowi = np.where(cov1 > 0.5, r1, r0)
    edge = smoothstep(0.6, 1.0, np.minimum(rel, 1.0)) * (cov0 + cov1)   # soft crevice along the lock edges
    tip = smoothstep(0.4, 1.0, u) * (cov0 + cov1)          # pale tip
    root = (1 - smoothstep(0.0, 0.5, u)) * cov0            # shaded root under the tip of the lock above
    # strands: fine streaks running along the lock (the main visual), two frequencies
    f1, f2 = min(2.4 / s, fm), min(4.2 / s, fm * 1.4)
    st = vnoise2(Xs * f1 + 7.0, Y * 0.2 / s, seed + 3)
    st2 = vnoise2(Xs * f2 + 11.0, Y * 0.4 / s, seed + 4)
    clump = vnoise2(Xs * 0.3 / s, Y * 0.05 / s, seed + 5)
    jit = (hash2(coli, rowi, seed + 6) - 0.5) * 0.08 * (1 - under)
    d = jit + (clump - 0.5) * 0.14 + (st - 0.5) * 0.22 * mat.micro + (st2 - 0.5) * 0.08 * mat.micro
    d += tip * 0.10 - root * 0.12 - edge * 0.07 - under * 0.15
    out['height'] = np.clip(0.4 + (st - 0.5) * 0.3 + (st2 - 0.5) * 0.12 - edge * 0.12 - under * 0.08 + tip * 0.04, 0, 1)
    out['ao'] = np.clip(root * 0.4 + edge * 0.25 + under * 0.4, 0, 1)
    out['rag'] = np.clip(0.5 + (st - 0.5) * 1.2 + (clump - 0.5) * 0.6 + (u - 0.5) * 0.35, 0, 1)
    hv = (clump - 0.5) * 0.10 + (hash2(coli, rowi, seed + 8) - 0.5) * 0.05
    out['tint'] = np.stack([1 + hv, 1 + hv * 0.4, 1 - hv * 0.9], -1)
    return d


def _membrane_hd(mat, X, Y, fw, fh, seed, out):
    """Wing / fin membrane: a few primary veins fanning out from the root edge to the free edge, short
    secondary veins branching off them, a faint capillary network, and translucency that increases toward
    the free edge and between the veins."""
    s = mat.scale
    vertical = fh >= fw
    along, across = (Y, X) if vertical else (X, Y)
    L, Wd = (fh, fw) if vertical else (fw, fh)
    L = max(L, 1e-6)
    u = along / L
    n = max(2, int(round(Wd / (8.0 * s))))
    dmin = np.full(X.shape, 99.0)
    sec = np.zeros(X.shape)

    aa = 0.25 / max(1.0, _CUR_K * _CUR_DETAIL / 4.0)     # anti-aliasing width (model px)

    def primary(k, uu):
        c0 = (k + 0.5 + (_hash1(k, seed) - 0.5) * 0.5) / n * Wd
        return Wd * 0.5 + (c0 - Wd * 0.5) * (0.80 + 0.42 * uu) + (fbm2(uu * 3.0 + k * 3.1, uu * 0 + k * 1.7, seed + 2, 2) - 0.5) * 1.2 * s

    for k in range(n):
        c = primary(k, u)
        dmin = np.minimum(dmin, np.abs(across - c))
        for j in range(5):
            u0 = (j + 0.1 + _hash1(k * 7 + j, seed + 3) * 0.8) / 5.0
            a0, b0 = u0 * L, float(primary(k, np.asarray(u0)))
            side = 1.0 if _hash1(k * 11 + j, seed + 4) > 0.5 else -1.0
            ln = (3.0 + _hash1(k * 13 + j, seed + 5) * 5.0) * s
            bend = (_hash1(k * 17 + j, seed + 6) - 0.5) * 1.0
            # curved branch: leaves the primary at ~40 deg and bends toward the free edge, tapering to nothing
            pa = np.clip((along - a0) / ln, 0, 1)
            cb = b0 + side * (0.8 * pa * ln + bend * pa * pa * ln) + 0.35 * s * np.sin(pa * 9.0 + k)
            dist = np.abs(across - cb) * 0.8 + np.abs(along - a0 - pa * ln) * 0.6
            dist = np.where((along < a0 - 0.3 * s) | (along > a0 + ln + 0.3 * s), 99.0, dist)
            w2 = (0.15 - 0.10 * pa) * s
            sec = np.maximum(sec, (1 - smoothstep(w2, w2 + aa, dist)) * (1 - smoothstep(0.8, 1.0, pa)))
    wmain = (0.27 - 0.11 * u) * s
    vein = 1 - smoothstep(wmain, wmain + aa, dmin)
    halo = (1 - smoothstep(wmain + 0.1, wmain + 1.4 * s, dmin)) * (1 - vein) * 0.06      # pale translucent rim
    cap = isoline(fbm2(X * 0.26 / s, Y * 0.26 / s, seed + 8, 2), X, Y, 0.14 * s, soft=aa) * (1 - vein) * (1 - sec)
    # translucency: the membrane thins toward the free edge and between the veins (backlit, paler)
    trans = u * 0.14 + smoothstep(0.0, 3.5 * s, dmin) * 0.08 + (fbm2(X * 0.06, Y * 0.06, seed + 9, 2) - 0.5) * 0.08
    wrinkle = (vnoise2(X * 0.5 / s, Y * 0.5 / s, seed + 10) - 0.5) * 0.03
    d = trans - vein * 0.26 - sec * 0.18 - cap * 0.07 * mat.micro + halo + 0.02 + wrinkle + _grain(X, Y, seed + 6, 0.012, mat)
    out['height'] = np.clip(0.35 + vein * 0.6 + sec * 0.45 + cap * 0.12, 0, 1)
    out['outline'] = (vein + sec) > 0.5
    out['ao'] = vein * 0.2
    return d


def _horn_hd(mat, X, Y, fw, fh, seed, out):
    """Keratin horn / tusk / claw: a rounded cross-section with a long glossy highlight, growth rings across
    the axis (irregular spacing, slightly curved, crisp crease on one side), broad colour bands and a very
    faint lengthwise fibre."""
    s = mat.scale
    vertical = fh >= fw
    along, across = (Y, X) if vertical else (X, Y)
    L, Wd = (fh, fw) if vertical else (fw, fh)
    a = across / max(Wd, 1e-6)
    cyl = np.sqrt(np.clip(1 - (2 * a - 1) ** 2, 0, 1))
    hl = np.exp(-((a - 0.36) / 0.13) ** 2)
    ph = along / (1.7 * s) + (fbm2(along * 0.06 / s, across * 0.2 / s, seed, 2) - 0.5) * 1.6 + (2 * a - 1) ** 2 * 0.5
    ring_i = np.floor(ph).astype(np.int64)
    rt = ph - ring_i
    amp = 0.45 + hash2(ring_i, ring_i * 0, seed + 1) * 0.8
    crease = (np.exp(-(rt / 0.06) ** 2) + np.exp(-((1 - rt) / 0.06) ** 2)) * 0.09 * amp
    ridge = (rt - 0.5) * 0.05 * amp
    band = (vnoise2(ph * 0.22, a * 0.3 + 2.0, seed + 2) - 0.5) * 0.14
    fib = (vnoise2(across * min(1.6 / s, _fmax()), along * 0.04 / s, seed + 4) - 0.5) * 0.04 * mat.micro
    d = cyl * 0.14 - 0.07 + hl * 0.09 * (0.5 + mat.hd('gloss')) + ridge - crease + band + fib + _grain(X, Y, seed + 7, 0.02, mat)
    out['height'] = np.clip(0.3 + cyl * 0.45 + ridge * 2.0 - crease * 2.0, 0, 1)
    out['ao'] = crease * 2.0
    return d


def _bone_hd(mat, X, Y, fw, fh, seed, out):
    s = mat.scale
    und = fbm2(X * 0.3 / s, Y * 0.3 / s, seed, 3)
    pc = max(1.3 * s, 1.6 / _fmax())
    pb, _, _, _, _ = voronoi2(X / pc, Y / pc, seed + 5)
    pits = 1 - smoothstep(0.10, 0.24, pb)
    gate = (hash2(np.floor(X / pc).astype(np.int64), np.floor(Y / pc).astype(np.int64), seed + 6) > 0.88)
    pits = pits * gate
    # hairline cracks
    crack = isoline(fbm2(X * 0.1 / s + 4, Y * 0.1 / s, seed + 7, 2), X, Y, 0.16) * (fbm2(X * 0.05, Y * 0.05, seed + 8, 2) > 0.55)
    d = (und - 0.5) * 0.16 - pits * 0.12 - crack * 0.14 + _grain(X, Y, seed + 2, 0.04, mat)
    out['height'] = np.clip(0.6 + (und - 0.5) * 0.4 - pits * 0.6 - crack * 0.4, 0, 1)
    out['ao'] = pits * 0.5 + crack * 0.4
    return d


def _rock_hd(mat, X, Y, fw, fh, seed, out):
    """Rock: wandering crack network (domain-warped voronoi), sub-cracks in some chunks, rough grain and
    specks.  `crack` carries the heat field for glowing (lava) rock: cracks glow only inside sparse hot
    zones, with a hot core fading through the glow colour into dark crust."""
    s = mat.scale
    cell = 5.0 * s
    wx = (fbm2(X * 0.12 / s, Y * 0.12 / s, seed + 1, 2) - 0.5) * 1.6 * s
    wy = (fbm2(X * 0.12 / s + 9, Y * 0.12 / s, seed + 2, 2) - 0.5) * 1.6 * s
    best, second, bestv, _, _ = voronoi2((X + wx) / cell, (Y + wy) / cell, seed, jitter=1.0)
    gap = second - best
    b2, s2, _, _, _ = voronoi2((X + wy) / (cell * 0.45), (Y - wx) / (cell * 0.45), seed + 5)
    sub = (1 - smoothstep(0.0, 0.10, s2 - b2)) * smoothstep(0.55, 0.7, bestv) * smoothstep(0.08, 0.2, gap)
    crack = 1 - smoothstep(0.0, 0.14, gap)
    rough = fbm2(X * 0.4 / s, Y * 0.4 / s, seed + 3, 3)
    grain = fbm2(X * 1.5, Y * 1.5, seed + 6, 2)
    speck = hash2(np.floor(X * 2).astype(np.int64), np.floor(Y * 2).astype(np.int64), seed + 4) > 0.985
    d = (bestv - 0.5) * 0.22 + (0.5 - best) * 0.10 - crack * 0.32 - sub * 0.16 + (rough - 0.5) * 0.14 * mat.micro
    d += (grain - 0.5) * 0.05 * mat.micro + speck * 0.08
    out['height'] = np.clip((1 - crack) * (0.55 + bestv * 0.45) - sub * 0.3 + (rough - 0.5) * 0.25, 0, 1)
    if mat.glow is not None:
        # hot zones (about a third of the crack network); gap is in cell units -> width in model px = gap * cell
        heat = smoothstep(0.42, 0.60, fbm2(X * 0.07 / s + 5, Y * 0.07 / s, seed + 11, 2))
        gpx = gap * cell
        out['crack'] = {'core': np.exp(-(gpx / 0.22) ** 2) * heat,                 # white-hot centre line
                        'ember': (1 - smoothstep(0.0, 0.9, gpx)) * heat,             # glowing crack
                        'halo': (1 - smoothstep(0.3, 2.6, gpx)) * heat}              # heat-darkened crust
        out['outline'] = (gpx < 0.5) & (heat > 0.3)
    out['ao'] = crack * 0.7 + sub * 0.3
    hv = (bestv - 0.5) * 0.06
    out['tint'] = np.stack([1 + hv, np.ones_like(hv), 1 - hv], -1)
    return d


def _crystal_hd(mat, X, Y, fw, fh, seed, out):
    """Gem / ice: large flat facets, each a plane with its own gradient, thin dark facet edges with a light
    fringe on the lit side, a few bright 'lit' facets (these also carry the emissive layer) and sparkles."""
    s = mat.scale
    cell = 5.5 * s
    best, second, bestv, bpx, bpy = voronoi2(X / cell, Y / cell, seed, jitter=0.8)
    gap = second - best
    ix, iy = np.floor(bpx * 13).astype(np.int64), np.floor(bpy * 13).astype(np.int64)
    gx = (hash2(ix, iy, seed + 1) - 0.5) * 1.1
    gy = (hash2(ix, iy, seed + 2) - 0.5) * 1.1
    plane = (X / cell - bpx) * gx + (Y / cell - bpy) * gy
    lit = hash2(ix, iy, seed + 3) > 0.84
    lit_s = smoothstep(0.02, 0.14, gap) * lit
    ew = max(0.05, 1.1 / (cell * _CUR_K * _CUR_DETAIL))
    edge = 1 - smoothstep(0.0, ew, gap)
    fringe = smoothstep(ew * 0.8, ew * 1.8, gap) * (1 - smoothstep(ew * 1.8, ew * 4.0, gap))
    depth = (fbm2(X * 0.12 / s, Y * 0.12 / s, seed + 4, 2) - 0.5) * 0.10
    # faint internal fractures / inclusions inside some facets
    frac = isoline(fbm2(X * 0.2 / s + 2, Y * 0.2 / s, seed + 6, 2), X, Y, 0.12 * s, soft=0.2) * (hash2(ix, iy, seed + 7) > 0.6) * smoothstep(ew, ew * 3, gap)
    d = (bestv - 0.5) * 0.16 + plane * 0.40 - edge * 0.12 + fringe * 0.06 * (gx > 0) - fringe * 0.03 * (gx <= 0)
    d += lit_s * (0.10 + plane * 0.30) + depth + frac * 0.06
    glint = np.exp(-(((X / cell - bpx) - 0.12) ** 2 + ((Y / cell - bpy) - 0.1) ** 2) / 0.004) * lit * 0.22
    d += glint + _grain(X, Y, seed + 8, 0.015, mat)
    out['height'] = np.clip(0.5 + plane * 1.2 - edge * 0.3, 0, 1)
    out['outline'] = lit_s > 0.5
    out['lit'] = lit_s
    out['ao'] = edge * 0.3
    return d


def _skin_hd(mat, X, Y, fw, fh, seed, out):
    s = mat.scale
    warp = fbm2(X * 0.14 / s, Y * 0.14 / s, seed, 2)
    wr = np.sin(Y * 1.9 / s + warp * 7.0) * 0.5 + 0.5
    crease = smoothstep(0.86, 1.0, wr)
    pc = max(1.1 * s, 1.6 / _fmax())
    pb, _, _, _, _ = voronoi2(X / pc, Y / pc, seed + 5)
    pores = (1 - smoothstep(0.08, 0.2, pb)) * (hash2(np.floor(X / pc).astype(np.int64), np.floor(Y / pc).astype(np.int64), seed + 6) > 0.8)
    d = (vnoise2(X * 0.25 / s, Y * 0.25 / s, seed + 1) - 0.5) * 0.12 - crease * 0.10 - pores * 0.07 * mat.micro
    d += np.sin(Y * 1.9 / s + warp * 7.0) * 0.025
    out['height'] = np.clip(0.6 - crease * 0.5 - pores * 0.5 + (warp - 0.5) * 0.2, 0, 1)
    out['ao'] = crease * 0.4 + pores * 0.4
    return d


def _veins_hd(mat, X, Y, fw, fh, seed, out):
    """Electric organ / glowing veins: thin bright branching lines (two scales) with a soft halo."""
    s = mat.scale
    n1 = fbm2(X * 0.09 / s, Y * 0.09 / s, seed, 3)
    n2 = fbm2(X * 0.24 / s + 5.0, Y * 0.24 / s, seed + 2, 2)
    v = isoline(n1, X, Y, 0.3 * s, soft=0.15)
    v2 = isoline(n2, X, Y, 0.16 * s, soft=0.12) * 0.5
    veins = np.clip(v + v2, 0, 1)
    step = max(1e-6, float(X[0, 1] - X[0, 0]))
    gy, gx = np.gradient(n1)
    dist = np.abs(n1 - 0.5) / (np.sqrt(gx * gx + gy * gy) / step + 1e-6)
    halo = (1 - smoothstep(0.2, 1.4 * s, dist)) * 0.07
    d = (vnoise2(X * 0.3 / s, Y * 0.3 / s, seed + 1) - 0.5) * 0.14 + veins * 0.34 + halo - 0.14
    out['height'] = np.clip(0.3 + veins * 0.7, 0, 1)
    out['outline'] = veins > 0.5
    return d


def _metal_hd(mat, X, Y, fw, fh, seed, out):
    vertical = fh >= fw
    along, across = (Y, X) if vertical else (X, Y)
    fm = _fmax()
    brush = vnoise2(across * min(4.5, fm), along * 0.12, seed) - 0.5
    brush2 = vnoise2(across * min(9.0, fm * 1.5) + 3.0, along * 0.3, seed + 1) - 0.5
    band = np.exp(-((Y - fh * 0.35) ** 2) / max(1.0, (fh * 0.14) ** 2)) * 0.22
    scr = np.abs(fbm2(along * 0.9, across * 0.12, seed + 7, 2) - 0.5)
    scratch = (1 - smoothstep(0.0, 0.02, scr)) * 0.07
    d = band + brush * 0.07 * mat.micro + brush2 * 0.03 * mat.micro + scratch + (fbm2(X * 0.2, Y * 0.2, seed + 3, 2) - 0.5) * 0.05
    out['height'] = np.clip(0.5 + brush * 0.3, 0, 1)
    return d


def _cloth_hd(mat, X, Y, fw, fh, seed, out):
    s = mat.scale
    wp = max(0.8 * s, 1.0 / _fmax())
    # plain weave: warp and weft threads alternate over / under (checker of two thread directions)
    tx = 0.5 + 0.5 * np.cos(X * 2 * math.pi / wp)
    ty = 0.5 + 0.5 * np.cos(Y * 2 * math.pi / wp)
    chk = (np.floor(X / wp + 0.5).astype(np.int64) + np.floor(Y / wp + 0.5).astype(np.int64)) % 2 == 0
    weave = np.where(chk, tx * 0.6 + 0.4 * ty, ty * 0.6 + 0.4 * tx) - 0.5
    folds = fbm2(X * 0.12 / s, Y * 0.08 / s, seed, 3)
    fuzz = (vnoise2(X * 1.7, Y * 1.7, seed + 2) - 0.5) * 0.03
    d = weave * 0.045 * mat.micro + (folds - 0.5) * 0.15 + np.sin(Y * 0.8 / s + folds * 3) * 0.03 + fuzz * mat.micro
    out['height'] = np.clip(0.5 + weave * 0.35 + (folds - 0.5) * 0.4, 0, 1)
    return d


def _wood_hd(mat, X, Y, fw, fh, seed, out):
    s = mat.scale
    warp = fbm2(X * 0.08 / s, Y * 0.03 / s, seed, 2)
    g = np.sin(X * 0.9 / s + warp * 7.0)
    lines = smoothstep(0.75, 1.0, g)
    fine = np.sin(X * min(3.1 / s, _fmax() * 6.0) + warp * 9.0) * 0.5 + 0.5
    d = g * 0.06 - lines * 0.10 + (fine - 0.5) * 0.04 * mat.micro + (fbm2(X * 0.3, Y * 0.1, seed + 3, 2) - 0.5) * 0.06
    out['height'] = np.clip(0.6 - lines * 0.5 + (fine - 0.5) * 0.2, 0, 1)
    out['ao'] = lines * 0.3
    return d


def _smooth_hd(mat, X, Y, fw, fh, seed, out):
    nz = fbm2(X * 0.2, Y * 0.2, seed, 3)
    d = (nz - 0.5) * 0.10 + _grain(X, Y, seed + 1, 0.03, mat)
    out['height'] = np.clip(0.5 + (nz - 0.5) * 0.3, 0, 1)
    return d


_PATTERNS_HD = {
    'scales': _scales_hd, 'plates': _plates_hd, 'membrane': _membrane_hd, 'horn': _horn_hd, 'bone': _bone_hd,
    'rock': _rock_hd, 'crystal': _crystal_hd, 'skin': _skin_hd, 'fur': _fur_hd, 'veins': _veins_hd, 'metal': _metal_hd,
    'cloth': _cloth_hd, 'wood': _wood_hd, 'smooth': _smooth_hd,
}


def pattern_hd(mat, X, Y, fw, fh, seed, face=None):
    """HD pattern in continuous face-local model-pixel coordinates X (right), Y (down); fw/fh = face size.

    Returns dict: delta (value offset), height (0..1 relief), outline (bool, emissive selection for
    rock/veins/crystal), tint (per-element colour multiplier, (h,w,3) or None), ao (0..1 extra occlusion),
    plus pattern-specific fields (rock: crack = {core, ember}; crystal: lit)."""
    out = {'delta': None, 'height': None, 'outline': np.zeros(X.shape, bool), 'tint': None, 'ao': None}
    fn = _PATTERNS_HD.get(mat.pattern, _smooth_hd)
    if fn is _fur_hd:
        d = fn(mat, X, Y, fw, fh, seed, out, face)
    else:
        d = fn(mat, X, Y, fw, fh, seed, out)
    out['delta'] = d
    if out['height'] is None:
        out['height'] = np.full(X.shape, 0.5)
    return out



# ----------------------------------------------------------------------------- face geometry
def face_grid(cube, face, texel=1):
    """Returns per-texel bone-local points (N,3), normal, and semantic f/d/r arrays for a face.

    texel > 1: the grid has texel x texel samples per model pixel (HD painting); u0/v0/w/h are in HD texels."""
    K = int(texel)
    u0, v0, w, h = cube.face_rects()[face]
    if w <= 0 or h <= 0:
        return None
    u0, v0, w, h = u0 * K, v0 * K, w * K, h * K
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


def apply_decals(cube, img, glow, texel=1):
    if texel > 1:
        return apply_decals_hd(cube, img, glow, texel)
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


def _blend(img, glow, u0, v0, w, h, rgb, cov, gcol=None, keep_alpha=True):
    """Alpha-blends colour rgb (3,) or (h,w,3) with coverage cov (h,w) into the image region."""
    region = img[v0:v0 + h, u0:u0 + w]
    c = cov[..., None]
    region[..., :3] = region[..., :3] * (1 - c) + np.asarray(rgb, float) * c
    if not keep_alpha:
        region[..., 3] = np.maximum(region[..., 3], cov * 255)
    if glow is not None:
        g = glow[v0:v0 + h, u0:u0 + w]
        if gcol is not None:
            g[..., :3] = g[..., :3] * (1 - c) + np.asarray(gcol, float) * c
            g[..., 3] = np.maximum(g[..., 3], cov * 255)
        else:
            # a non-glowing decal painted over a glowing texel removes the glow there
            g[..., :3] *= (1 - c)
            g[..., 3] *= (1 - cov)


def _disc(X, Y, cx, cy, rx, ry, soft=0.6):
    """Anti-aliased ellipse coverage in texel units (soft = edge width in texels)."""
    q = np.sqrt(((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2)
    return 1 - smoothstep(1.0 - soft / max(rx, ry), 1.0 + soft / max(rx, ry) * 0.3, q)


def _eye_hd(img, glow, u0, v0, w, h, cx, cy, ew, eh, col, gcol, dark, slit=True, K=4):
    """Reptile / beast eye: dark socket ring, iris with radial gradient, slit pupil, specular highlight."""
    X, Y = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)
    rx, ry = ew / 2.0, eh / 2.0
    ring = _disc(X, Y, cx, cy, rx + 0.9 * K / 4 + 0.6, ry + 0.9 * K / 4 + 0.6)
    _blend(img, glow, u0, v0, w, h, dark, ring, None)
    ball = _disc(X, Y, cx, cy, rx, ry)
    # iris gradient: brighter around the pupil, darker toward the rim, warmer lower half
    q = np.sqrt(((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2)
    shade = 1.0 - 0.45 * smoothstep(0.35, 1.0, q)
    shade += 0.08 * np.clip((Y - cy) / max(ry, 1e-6), -1, 1)
    iris = np.clip(np.asarray(col, float)[None, None, :] * shade[..., None], 0, 255)
    # fine radial fibres
    ang = np.arctan2(Y - cy, (X - cx) + 1e-6)
    fib = np.sin(ang * 14.0 + q * 3.0) * 0.06 + 1.0
    iris = np.clip(iris * fib[..., None], 0, 255)
    _blend(img, glow, u0, v0, w, h, iris, ball, np.asarray(gcol, float) if gcol is not None else None)
    if gcol is not None:
        g = glow[v0:v0 + h, u0:u0 + w]
        g[..., :3] = np.where(ball[..., None] > 0.02, np.clip(np.asarray(gcol, float)[None, None, :] * shade[..., None] * 1.05, 0, 255), g[..., :3])
    # pupil: vertical slit (or round) with soft edge
    if slit:
        pw = max(0.35 * K / 4 * 2.0, rx * 0.22)
        ph = ry * 0.86
        pup = _disc(X, Y, cx, cy, pw, ph, soft=0.8)
    else:
        pup = _disc(X, Y, cx, cy, rx * 0.42, ry * 0.42, soft=0.8)
    pup = pup * ball
    _blend(img, glow, u0, v0, w, h, dark * 0.5, pup, None)
    # highlight (upper-left) + small secondary
    hl = _disc(X, Y, cx - rx * 0.42, cy - ry * 0.38, max(0.7, rx * 0.20), max(0.6, ry * 0.26), soft=0.9) * ball
    hl2 = _disc(X, Y, cx + rx * 0.35, cy + ry * 0.35, max(0.5, rx * 0.10), max(0.4, ry * 0.12), soft=0.9) * ball * 0.6
    _blend(img, glow, u0, v0, w, h, np.array([255, 255, 255.0]), np.clip(hl * 0.9 + hl2, 0, 1), np.array([255, 255, 255.0]) if gcol is not None else None)


def apply_decals_hd(cube, img, glow, K):
    rects = {k: (u * K, v * K, w * K, h * K) for k, (u, v, w, h) in cube.face_rects().items()}
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
                if w < 2 * K or h < 2 * K:
                    continue
                cx = fx if fc == 'west' else 1 - fx
                # 1x eye: ew = size, eh = size//2 (+1) texels, top-left at (cu - ew//2, cv)
                ew1, eh1 = size, max(1, size // 2 + (1 if size >= 3 else 0))
                cu1 = int(round(cx * (w // K - 1)))
                cv1 = int(round(dy * (h // K - 1)))
                ccx = (cu1 - ew1 // 2 + ew1 / 2.0) * K
                ccy = (cv1 + eh1 / 2.0) * K
                _eye_hd(img, glow, u0, v0, w, h, ccx, ccy, ew1 * K * 1.05, eh1 * K * 1.05, col, gcol, dark, True, K)
        elif t == 'eyes_front':
            u0, v0, w, h = rects['north']
            ex, dy = dec.get('at', (0.25, 0.35))
            size = int(dec.get('size', 2))
            for side in (ex, 1 - ex):
                cu1 = int(round(side * (w // K - 1)))
                cv1 = int(round(dy * (h // K - 1)))
                ccx = (cu1 - size // 2 + size / 2.0) * K
                ccy = (cv1 + max(1, size - 1) / 2.0) * K
                _eye_hd(img, glow, u0, v0, w, h, ccx, ccy, size * K * 1.1, max(1, size - 1) * K * 1.15, col, gcol, dark, False, K)
        elif t == 'mouth':
            dy = dec.get('at', 0.7)
            reach = dec.get('reach', 0.75)
            teeth = dec.get('teeth', True)
            tooth_col = hexrgb(dec.get('tooth', '#ece6d2'))
            for fc in ('west', 'east', 'north'):
                u0, v0, w, h = rects[fc]
                if w < 3 * K or h < 2 * K:
                    continue
                X, Y = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)
                vv = (int(round(dy * (h // K - 1))) + 0.5) * K
                fr = X / w
                front = fr if fc == 'west' else (1 - fr if fc == 'east' else np.ones_like(fr))
                lip = np.exp(-((Y - vv) / (0.45 * K)) ** 2)
                gate = smoothstep(1 - reach - 0.03, 1 - reach + 0.03, front) if fc != 'north' else np.ones_like(front)
                wob = 1 + 0.15 * np.sin(X / K * 2.1)
                lip = np.clip(lip * wob, 0, 1) * gate
                _blend(img, glow, u0, v0, w, h, dark, lip, None)
                if teeth:
                    # triangular teeth hanging from the upper lip every 2 model px, smaller ones rising from below
                    tx = (X / K) % 2.0
                    tri = np.clip(1 - np.abs(tx - 1.0) / 0.55, 0, 1)
                    up = smoothstep(0.0, 0.25, (vv - Y) / K) * (1 - smoothstep(0.9, 1.35, (vv - Y) / K)) * tri
                    tooth_h = (vv - Y) / K
                    up = np.where(np.abs(tx - 1.0) <= 0.55 * (1 - tooth_h / 1.35), up, 0)
                    lo = smoothstep(0.0, 0.2, (Y - vv) / K) * (1 - smoothstep(0.55, 0.9, (Y - vv) / K)) * np.clip(1 - np.abs(((X / K + 1) % 2.0) - 1.0) / 0.4, 0, 1)
                    cov = np.clip(up + lo * 0.9, 0, 1) * gate
                    shade = 1 - 0.25 * smoothstep(0.2, 1.3, np.abs(Y - vv) / K)
                    _blend(img, glow, u0, v0, w, h, np.clip(tooth_col[None, None, :] * shade[..., None], 0, 255), cov, None)
        elif t == 'teeth':
            fc = dec.get('face', 'north')
            u0, v0, w, h = rects[fc]
            edge = dec.get('edge', 'bottom')
            tooth_col = hexrgb(dec.get('color', '#ece6d2'))
            X, Y = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)
            dist = (h - Y) / K if edge == 'bottom' else Y / K     # distance from the edge in model px
            tx = (X / K) % 2.0
            big = ((X / K) % 4.0) < 2.0
            length = np.where(big, 1.8, 1.1)
            width = 0.75 * np.clip(1 - dist / length, 0, 1)
            cov = smoothstep(0.0, 0.18, width - np.abs(tx - 1.0)) * (dist < length)
            shade = 1 - 0.3 * np.clip(dist / length, 0, 1) - 0.25 * smoothstep(0.3, 0.75, np.abs(tx - 1.0) / np.maximum(width, 1e-3))
            _blend(img, glow, u0, v0, w, h, np.clip(tooth_col[None, None, :] * shade[..., None], 0, 255), cov, None)
            # dark gum line at the root
            root = np.exp(-(dist / 0.25) ** 2) * 0.6
            _blend(img, glow, u0, v0, w, h, dark, root, None)
        elif t == 'nostrils':
            u0, v0, w, h = rects['north']
            dy = dec.get('at', 0.25)
            X, Y = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)
            vv = (int(round(dy * (h // K - 1))) + 0.5) * K
            for side in (0.3, 0.7):
                cu = (int(round(side * (w // K - 1))) + 0.5) * K
                cov = _disc(X, Y, cu, vv, 0.7 * K, 0.5 * K, soft=0.8)
                rim = _disc(X, Y, cu, vv - 0.15 * K, 0.95 * K, 0.7 * K, soft=0.8) - cov
                _blend(img, glow, u0, v0, w, h, dark * 1.8, np.clip(rim, 0, 1) * 0.5, None)
                _blend(img, glow, u0, v0, w, h, dark * 0.6, cov, None)
        elif t in ('claws', 'tip', 'band'):
            pass  # handled per-texel in paint_model
        elif t == 'glow_spot':
            fc = dec.get('face', 'north')
            u0, v0, w, h = rects[fc]
            fx, dy = dec.get('at', (0.5, 0.5))
            rad = dec.get('radius', 1.5) * K
            X, Y = np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)
            cu, cv = fx * (w - K) + K / 2.0, dy * (h - K) + K / 2.0
            q = np.sqrt((X - cu) ** 2 + (Y - cv) ** 2) / max(rad, 1e-6)
            core = 1 - smoothstep(0.6, 1.05, q)
            halo = (1 - smoothstep(0.9, 1.9, q)) * 0.35
            gc = gcol if gcol is not None else col
            _blend(img, glow, u0, v0, w, h, col, np.clip(core + halo, 0, 1), gc)
            if glow is not None:
                g = glow[v0:v0 + h, u0:u0 + w]
                # soft emissive falloff instead of a hard-edged disc
                g[..., 3] = np.where(core > 0.02, 255, g[..., 3])
                g[..., :3] = np.where((core > 0.02)[..., None], np.asarray(gc, float) * (0.6 + 0.4 * core)[..., None], g[..., :3])


# ----------------------------------------------------------------------------- main painter
def _edge_distance(I, J, w, h):
    """Distance (in grid cells) to the nearest face border."""
    return np.minimum(np.minimum(I + 0.5, w - I - 0.5), np.minimum(J + 0.5, h - J - 0.5))


def _mask_distance(mask, cap=6.0):
    """Approximate distance (cells) from each True cell to the nearest False cell (capped)."""
    try:
        from scipy import ndimage
        return np.minimum(ndimage.distance_transform_edt(mask), cap)
    except Exception:
        d = np.full(mask.shape, cap)
        cur = ~mask
        for k in range(int(cap)):
            d = np.where(mask & cur, min(float(k), cap), d)
            nxt = cur.copy()
            nxt[1:, :] |= cur[:-1, :]
            nxt[:-1, :] |= cur[1:, :]
            nxt[:, 1:] |= cur[:, :-1]
            nxt[:, :-1] |= cur[:, 1:]
            cur = nxt
        return np.where(mask, d, 0)


def detail_for(model):
    """Pattern feature-size multiplier: large monsters 1.0, ~2-block models 0.7, ~1-block models 0.5
    (so a small raptor shows rows of small scales instead of one scale per face). Override: model.hd_detail."""
    if getattr(model, 'hd_detail', None):
        return float(model.hd_detail)
    ys = []
    for c in model.all_cubes():
        m = c.bone.rest_matrix()
        for corner in ((c.origin[0], c.origin[1], c.origin[2]), (c.origin[0] + c.size[0], c.origin[1] + c.size[1], c.origin[2] + c.size[2])):
            ys.append((m @ np.array([*corner, 1.0]))[1])
    span = (max(ys) - min(ys)) if ys else 48
    if span < 30:
        return 0.5
    if span < 56:
        return 0.7
    return 1.0


def texel_for(model):
    """HD texel multiplier of a model: TEXEL for large monsters; small models (finer patterns, see detail_for)
    get proportionally more texels so every pattern keeps ~TEXEL texels per feature pixel (at most 8)."""
    if TEXEL <= 1:
        return 1
    return int(min(6, max(1, round(TEXEL / detail_for(model)))))


def paint_model(model, seed=1, texel=None):
    global _CUR_K, _CUR_DETAIL
    K = int(texel_for(model) if texel is None else texel)
    _CUR_K = K
    _CUR_DETAIL = detail_for(model)
    W, H = model.tex_w * K, model.tex_h * K
    img = np.zeros((H, W, 4), dtype=float)
    glow = np.zeros((H, W, 4), dtype=float)
    pbr = None
    if K > 1 and PBR:
        pbr = {'height': np.full((H, W), 0.5), 'ao': np.zeros((H, W)), 'smooth': np.zeros((H, W)),
               'f0': np.zeros((H, W)), 'por': np.zeros((H, W)), 'face': np.zeros((H, W), np.int32)}
    has_glow = False
    # model height range for AO
    ys = []
    for c in model.all_cubes():
        m = c.bone.rest_matrix()
        for corner in ((c.origin[0], c.origin[1], c.origin[2]), (c.origin[0] + c.size[0], c.origin[1] + c.size[1], c.origin[2] + c.size[2])):
            ys.append((m @ np.array([*corner, 1.0]))[1])
    ymin, ymax = (min(ys), max(ys)) if ys else (0, 24)
    yspan = max(1.0, ymax - ymin)
    face_id = 0

    for cube in model.all_cubes():
        if cube.mirror_of is not None:
            continue
        mrest = cube.bone.rest_matrix()
        rotm = mrest[:3, :3]
        for face in ('top', 'bottom', 'west', 'north', 'east', 'south'):
            g = face_grid(cube, face, K)
            if g is None:
                continue
            face_id += 1
            u0, v0, w, h, I, J, pts, n, f, d, r = g
            P = (np.concatenate([pts, np.ones(pts.shape[:2] + (1,))], axis=-1) @ mrest.T)[..., :3]
            N = rotm @ n
            N = N / (np.linalg.norm(N) + 1e-9)
            mat_name = cube.faces.get(face, cube.mat)
            mat = MATERIALS.get(mat_name, MATERIALS['default'])
            # belly: downward-facing faces or the lower part of side faces of belly-flagged materials
            use_belly = mat.belly is not None and (N[1] > 0.55)
            sd = cube.seed * 7 + hash(face) % 101
            rgb, gl = _shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, sd, cube, texel=K, face=face)
            px = _take_pbr()
            rag = _LAST.get('rag')
            if mat.belly is not None and not use_belly and abs(N[1]) < 0.5:
                # lower 30% of side faces blend into belly colour (with a jagged edge)
                bm = MATERIALS.get(mat.belly)
                if bm is not None and cube.decals is not None and any(dd.get('type') == 'belly_band' for dd in cube.decals):
                    brgb, _ = _shade(bm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 1, cube, texel=K, face=face)
                    bpx = _take_pbr()
                    if K > 1:
                        edge = 0.62 + (vnoise2(I / K * 0.6, I * 0 + sd, sd) - 0.5) * 0.14 + (vnoise2(I / K * 2.5, I * 0 + 3, sd + 1) - 0.5) * 0.04
                        tt = smoothstep(edge - 0.015, edge + 0.015, d)
                        rgb = rgb * (1 - tt[..., None]) + brgb * tt[..., None]
                        px = _lerp_pbr(px, bpx, tt)
                    else:
                        edge = 0.62 + (hash2(I, I * 0, sd) - 0.5) * 0.12
                        mask = d > edge
                        rgb = np.where(mask[..., None], brgb, rgb)
            if use_belly:
                rgb, gl = _shade(MATERIALS[mat.belly], I, J, w, h, P, N, f, d, r, ymin, yspan, sd, cube, texel=K, face=face)
                px = _take_pbr()
            # accent (solid 3D masks)
            if mat.accent and mat.accent_mode:
                global _CUR_N
                _CUR_N = N
                am = _accent_mask(mat, P, f, d, r, sd)
                _CUR_N = None
                if am is not None and am.any():
                    argb, agl = _shade(MATERIALS[mat.accent], I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 5, cube, texel=K, face=face)
                    apx = _take_pbr()
                    if K > 1:
                        if am.dtype == bool:
                            amf = _soften_mask(am, K)
                            if rag is not None:
                                # fur: the stripe / spot edge follows the strands (ragged) instead of a soft blur
                                amf = smoothstep(0.3, 0.7, amf + (rag - 0.5) * 0.45)
                        else:
                            amf = np.clip(am, 0, 1)          # already anti-aliased float coverage (thin veins)
                            am = amf > 0.5
                        rgb = rgb * (1 - amf[..., None]) + argb * amf[..., None]
                        px = _lerp_pbr(px, apx, amf)
                    else:
                        am = am > 0.5 if am.dtype != bool else am
                        rgb = np.where(am[..., None], argb, rgb)
                    if agl is not None:
                        gl = np.where(am[..., None], agl, 0 if gl is None else gl)
            # per-texel decals (claws / tips / bands)
            for dec in cube.decals:
                tp = dec.get('type')
                if tp == 'claws':
                    frac = dec.get('frac', 0.4)
                    cm = MATERIALS[dec.get('mat', 'claw')]
                    crgb, _ = _shade(cm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 9, cube, texel=K)
                    cpx = _take_pbr()
                    axis = dec.get('axis', 'front')
                    coord = f if axis == 'front' else d
                    if K > 1:
                        tt = smoothstep(1 - frac - 0.02, 1 - frac + 0.02, coord)
                        rgb = rgb * (1 - tt[..., None]) + crgb * tt[..., None]
                        px = _lerp_pbr(px, cpx, tt)
                    else:
                        sel = coord > 1 - frac
                        rgb = np.where(sel[..., None], crgb, rgb)
                elif tp == 'tip':
                    frac = dec.get('frac', 0.35)
                    tm = MATERIALS[dec.get('mat', 'horn_ivory')]
                    trgb, _ = _shade(tm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 13, cube, texel=K)
                    tpx = _take_pbr()
                    axis = dec.get('axis', 'front')
                    coord = f if axis == 'front' else (1 - f if axis == 'back' else (d if axis == 'down' else 1 - d))
                    if K > 1:
                        # soft, slightly ragged transition (a gradient over ~0.6 model px plus noise)
                        span = 0.5 / max(1.0, (h if axis in ('down', 'up') else w) / K)
                        noise = (vnoise2(I / K * 1.1, J / K * 1.1, sd) - 0.5) * 0.10
                        tt = smoothstep(1 - frac - span, 1 - frac + span, coord + noise)
                        rgb = rgb * (1 - tt[..., None]) + trgb * tt[..., None]
                        px = _lerp_pbr(px, tpx, tt)
                    else:
                        noise = (hash2(I, J, sd) - 0.5) * 0.12
                        sel = coord + noise > 1 - frac
                        rgb = np.where(sel[..., None], trgb, rgb)
                elif tp == 'band':
                    lo, hi = dec.get('range', (0.4, 0.6))
                    bm = MATERIALS[dec.get('mat', 'ign_dark')]
                    brgb, bgl = _shade(bm, I, J, w, h, P, N, f, d, r, ymin, yspan, sd + 17, cube, texel=K)
                    bpx = _take_pbr()
                    axis = dec.get('axis', 'front')
                    coord = f if axis == 'front' else d
                    sel = (coord >= lo) & (coord <= hi)
                    if K > 1:
                        tt = _soften_mask(sel, K)
                        rgb = rgb * (1 - tt[..., None]) + brgb * tt[..., None]
                        px = _lerp_pbr(px, bpx, tt)
                    else:
                        rgb = np.where(sel[..., None], brgb, rgb)
                    if bgl is not None:
                        gl = np.where(sel[..., None], bgl, 0 if gl is None else gl)
            if cube.tint is not None:
                rgb = np.clip(rgb * np.array(cube.tint, float), 0, 255)
            keep = np.ones(I.shape, dtype=bool)
            for dec in cube.decals:
                if dec.get('type') == 'cutout':
                    keep &= _cutout(dec, face, f, d, r, I, J, sd)
            if K > 1 and not keep.all() and keep.any():
                # crevice AO + slight darkening along the cut-out silhouette (membrane / fin edges read as edges)
                dist = _mask_distance(keep, 2.5 * K / 4 + 1.0)
                rim = 1 - smoothstep(0.0, 1.6 * K / 4 + 0.5, dist)
                rgb = rgb * (1 - 0.22 * rim)[..., None]
                if px is not None:
                    px['ao'] = np.clip(px['ao'] + rim * 0.4, 0, 1)
            img[v0:v0 + h, u0:u0 + w, :3] = rgb
            img[v0:v0 + h, u0:u0 + w, 3] = np.where(keep, 255, 0)
            if pbr is not None and px is not None:
                for key in ('height', 'ao', 'smooth', 'f0', 'por'):
                    pbr[key][v0:v0 + h, u0:u0 + w] = px[key]
                pbr['face'][v0:v0 + h, u0:u0 + w] = face_id
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
        apply_decals(cube, img, glow, K)
        if glow[..., 3].sum() != before:
            has_glow = True
    if K > 1:
        _finish_hd(img, glow, K)
        img[..., :3] = np.round(img[..., :3] / 2) * 2          # 128 levels per channel: invisible, ~20% smaller PNG
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
    gout = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), 'RGBA') if has_glow else None
    model._pbr_maps = _pbr_images(pbr, img, glow, K) if pbr is not None else None
    return out, gout


def _finish_hd(img, glow, K):
    """Anti-aliasing blur of the HD colour (alpha-weighted so cut-outs do not bleed), glow masked to opaque texels."""
    try:
        from scipy import ndimage
    except Exception:
        return
    if HD_BLUR > 0:
        a = (img[..., 3] > 0).astype(float)
        for c in range(3):
            num = ndimage.gaussian_filter(img[..., c] * a, HD_BLUR)
            den = ndimage.gaussian_filter(a, HD_BLUR)
            img[..., c] = np.where(a > 0, num / np.maximum(den, 1e-6), img[..., c])
    hole = img[..., 3] < 26
    glow[hole] = 0


def _pbr_images(pbr, img, glow, K):
    """LabPBR _n (RG normal OpenGL Y+, B ao, A height) and _s (R smooth, G f0, B porosity/sss, A emissive)."""
    try:
        from scipy import ndimage
    except Exception:
        ndimage = None
    H, W = img.shape[:2]
    hgt = pbr['height']
    if ndimage is not None:
        hgt = ndimage.gaussian_filter(hgt, 0.6)
    # gradients only within the same face (faces are packed next to each other in the atlas)
    fid = pbr['face']
    gx = np.zeros_like(hgt)
    gy = np.zeros_like(hgt)
    gx[:, 1:-1] = (hgt[:, 2:] - hgt[:, :-2]) * 0.5
    gy[1:-1, :] = (hgt[2:, :] - hgt[:-2, :]) * 0.5
    samex = np.zeros(hgt.shape, bool)
    samex[:, 1:-1] = (fid[:, 2:] == fid[:, 1:-1]) & (fid[:, :-2] == fid[:, 1:-1])
    samey = np.zeros(hgt.shape, bool)
    samey[1:-1, :] = (fid[2:, :] == fid[1:-1, :]) & (fid[:-2, :] == fid[1:-1, :])
    gx = np.where(samex, gx, 0)
    gy = np.where(samey, gy, 0)
    strength = 2.2 * K
    nx, ny, nz = -gx * strength, gy * strength, np.ones_like(gx)   # OpenGL convention: +Y = up in the image
    nl = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny = nx / nl, ny / nl
    opaque = img[..., 3] > 0
    # mild quantisation (normal 64 levels, ao / height 32 levels) keeps the PNGs about half the size
    n_img = np.zeros((H, W, 4), np.uint8)
    n_img[..., 0] = np.clip(np.round((nx * 0.5 + 0.5) * 255 / 4) * 4, 0, 255)
    n_img[..., 1] = np.clip(np.round((ny * 0.5 + 0.5) * 255 / 4) * 4, 0, 255)
    n_img[..., 2] = np.clip(np.round((1 - np.clip(pbr['ao'], 0, 1) * 0.65) * 255 / 8) * 8, 0, 255)
    n_img[..., 3] = np.clip(np.round(hgt * 255 / 8) * 8, 0, 255)
    n_img[~opaque] = (128, 128, 255, 255)
    s_img = np.zeros((H, W, 4), np.uint8)
    s_img[..., 0] = np.clip(pbr['smooth'], 0, 255)
    s_img[..., 1] = np.clip(pbr['f0'], 0, 255)
    s_img[..., 2] = np.clip(pbr['por'], 0, 255)
    lit = (glow[..., 3] > 0) & (glow[..., :3].sum(-1) > 30)
    lum = glow[..., :3].max(-1) / 255.0
    s_img[..., 3] = np.where(lit, np.clip(120 + lum * 130, 0, 254), 255).astype(np.uint8)
    s_img[~opaque] = (0, 0, 0, 255)
    return Image.fromarray(n_img, 'RGBA'), Image.fromarray(s_img, 'RGBA')


def save_pbr(model, path_base):
    """Writes <path_base>_n.png / _s.png if the last paint produced them (HD + PBR)."""
    maps = getattr(model, '_pbr_maps', None)
    import os
    for suffix in ('_n', '_s'):
        p = path_base + suffix + '.png'
        if maps is None:
            if os.path.exists(p):
                os.remove(p)
        else:
            maps[0 if suffix == '_n' else 1].save(p)


def _soften_mask(mask, K):
    """Boolean mask -> anti-aliased coverage: the edge is smoothed over ~1 texel (crisp but not stair-stepped)."""
    try:
        from scipy import ndimage
        b = ndimage.gaussian_filter(mask.astype(float), 0.3 * K)
        return smoothstep(0.25, 0.75, b)
    except Exception:
        return mask.astype(float)


def _take_pbr():
    if not _LAST:
        return None
    return {k: _LAST[k] for k in ('height', 'ao', 'smooth', 'f0', 'por')}


def _lerp_pbr(a, b, t):
    if a is None or b is None:
        return a if b is None else b
    return {k: a[k] * (1 - t) + b[k] * t for k in a}


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
        if _CUR_K > 1:
            lim = 1 - dec.get('depth', 0.25) * vnoise2(I / _CUR_K * 1.0, I * 0 + seed, seed)
        else:
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
        if _CUR_K > 1:
            # uniform-width veins (model px) along the iso-surface of a 3D noise field: the band |n-0.5| < c
            # is normalised by the gradient so the lines do not swell where the field is flat
            k = fq * 0.5
            n = fbm(x * k, y * k, z * k, seed)
            eps = 0.35
            gx = (fbm((x + eps) * k, y * k, z * k, seed) - fbm((x - eps) * k, y * k, z * k, seed)) / (2 * eps)
            gy = (fbm(x * k, (y + eps) * k, z * k, seed) - fbm(x * k, (y - eps) * k, z * k, seed)) / (2 * eps)
            gz = (fbm(x * k, y * k, (z + eps) * k, seed) - fbm(x * k, y * k, (z - eps) * k, seed)) / (2 * eps)
            if _CUR_N is not None:
                # only the gradient component tangential to the face matters for the width of the slice
                N = np.asarray(_CUR_N, float)
                dn = gx * N[0] + gy * N[1] + gz * N[2]
                gx, gy, gz = gx - dn * N[0], gy - dn * N[1], gz - dn * N[2]
            grad = np.sqrt(gx * gx + gy * gy + gz * gz) + 1e-4
            width = (1 - th) * 24.0            # thresh 0.975 -> 0.6 px wide veins
            dist = np.abs(n - 0.5) / grad
            aa = 0.6 / _CUR_K                  # anti-aliased edge of ~0.6 texel (float coverage, not softened again)
            return 1 - smoothstep(width * 0.5 - aa * 0.5, width * 0.5 + aa * 0.5, dist)
        v = np.abs(fbm(x * fq * 0.5, y * fq * 0.5, z * fq * 0.5, seed) - 0.5)
        return v < (1 - th) * 0.25
    if mat.accent_mode == 'tips':
        return f > th
    return None


def _shade(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed, cube, texel=None, face=None):
    K = int(_CUR_K if texel is None else texel)
    if K > 1:
        return _shade_hd(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed, cube, K, face)
    _LAST.clear()
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


def ramp_lookup(ramp, value):
    """Continuous lookup along a colour ramp (list of (3,) arrays); value 0..1 -> (…,3)."""
    cols = np.stack(ramp)
    n = len(ramp)
    v = np.clip(value, 0, 1) * (n - 1)
    i0 = np.clip(np.floor(v).astype(int), 0, n - 2)
    t = (v - i0)[..., None]
    return cols[i0] * (1 - t) + cols[i0 + 1] * t


# light direction in face space (x right, y down, z out of the surface) for the height-field relief
_L = np.array([-0.42, -0.62, 0.66])
_L = _L / np.linalg.norm(_L)
_HV = np.array([0.0, 0.0, 1.0]) + _L
_HV = _HV / np.linalg.norm(_HV)


def _shade_hd(mat, I, J, w, h, P, N, f, d, r, ymin, yspan, seed, cube, K, face=None):
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    X = (I + 0.5) / K
    Y = (J + 0.5) / K
    fw, fh = w / K, h / K
    value = np.full(I.shape, mat.base, dtype=float)
    # baked light: up-facing brighter (+Y is down); subtle, the game lights the model too
    value += -N[1] * 0.10 * mat.bake
    if abs(N[1]) < 0.5:
        value += (0.5 - d) * 0.12 * mat.bake
    value -= np.clip((y - ymin) / yspan, 0, 1) * 0.10 * mat.bake
    dt = _CUR_DETAIL
    pt = pattern_hd(mat, X / dt, Y / dt, fw / dt, fh / dt, seed, face)
    surf = pt['delta'] * (mat.contrast ** 0.5)         # lift for low-contrast materials (HD detail reads better)
    # height-field relief lighting (diffuse) + specular hot spots
    hgt = pt['height'] * mat.hd('relief') * K * dt
    gx = np.zeros_like(hgt)
    gy = np.zeros_like(hgt)
    gx[:, 1:-1] = (hgt[:, 2:] - hgt[:, :-2]) * 0.5
    gy[1:-1, :] = (hgt[2:, :] - hgt[:-2, :]) * 0.5
    nx, ny, nz = -gx, -gy, np.ones_like(gx)
    nl = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / nl, ny / nl, nz / nl
    diff = nx * _L[0] + ny * _L[1] + nz * _L[2]
    surf = surf + (diff - _L[2]) * 0.55
    gloss = mat.hd('gloss')
    if gloss > 0:
        ndh = np.clip(nx * _HV[0] + ny * _HV[1] + nz * _HV[2], 0, 1)
        surf = surf + ndh ** mat.hd('shine') * 0.22 * gloss
    # crevice AO at the face border (soft, ~0.8 model px) instead of the 1-texel outline
    ao_edge = np.zeros_like(value)
    if fw >= 2.5 and fh >= 2.5 and mat.edge > 0:
        dist = _edge_distance(I, J, w, h) / K
        ao_edge = 1 - smoothstep(0.0, 0.85, dist)
        surf = surf - ao_edge * mat.edge * 1.3
    # tone calibration: the face keeps the mean value the 1x pixel-art painter gave it (same pattern, outline
    # and contrast), so the HD texture box-filtered to 1x matches the original colour identity
    w1, h1 = max(1, int(round(fw))), max(1, int(round(fh)))
    I1, J1 = np.meshgrid(np.arange(w1), np.arange(h1))
    d1, _ = pattern_delta(mat, I1, J1, w1, h1, seed, None)
    m1 = float(np.mean(d1)) * mat.contrast
    if w1 >= 3 and h1 >= 3 and mat.edge > 0:
        m1 -= mat.edge * float(np.mean((I1 == 0) | (J1 == 0) | (I1 == w1 - 1) | (J1 == h1 - 1)))
    value += surf + float(np.clip(m1 - np.mean(surf), -0.2, 0.2))
    # colour variation: 3D model-space noise (continuous across cubes) at three octaves
    value += (vnoise(x * 0.35, y * 0.35, z * 0.35, seed) - 0.5) * 2 * mat.noise
    value += (vnoise(x * 1.1, y * 1.1, z * 1.1, seed + 31) - 0.5) * 0.9 * mat.noise
    value += (vnoise(x * 3.3, y * 3.3, z * 3.3, seed + 57) - 0.5) * 0.45 * mat.noise * mat.micro * dt
    # continuous ramp lookup + per-element tint + low-frequency hue variation
    rgb = ramp_lookup(mat.ramp, value)
    if pt['tint'] is not None:
        rgb = rgb * pt['tint']
    if mat.hue_var > 0:
        hn = vnoise(x * 0.18 + 11, y * 0.18, z * 0.18, seed + 71) - 0.5
        hn2 = vnoise(x * 0.6 + 5, y * 0.6, z * 0.6, seed + 83) - 0.5
        hv = (hn * 1.4 + hn2 * 0.6) * mat.hue_var
        hv = hv * np.clip(1.25 - rgb.max(-1) / 255.0, 0.15, 1.0)   # whites / pale colours barely shift
        rgb = rgb * np.stack([1 + hv * 1.6, 1 + hv * 0.3, 1 - hv * 1.6], -1)
    rgb = np.clip(rgb, 0, 255)
    ao = np.clip(ao_edge * 0.8 + (pt['ao'] if pt['ao'] is not None else 0), 0, 1)
    _LAST.clear()
    _LAST['rag'] = pt.get('rag')
    _LAST['height'] = np.clip(pt['height'] * 0.85 + 0.15 * (1 - ao_edge), 0, 1)
    _LAST['ao'] = ao
    _LAST['smooth'] = np.full(I.shape, float(mat.pbr_byte('smooth')))
    _LAST['f0'] = np.full(I.shape, float(mat.pbr_byte('f0')))
    _LAST['por'] = np.full(I.shape, float(mat.pbr_byte('por')))
    gl = None
    if mat.glow is not None:
        gcol = mat.glow
        sel = None
        if mat.pattern == 'rock':
            # ember cracks: only inside the sparse hot zones; white-hot core -> glow colour -> dull red -> crust
            ck = pt['crack']
            core, ember, halo = ck['core'], ck['ember'], ck['halo']
            hot = np.clip(gcol * 1.1 + np.array([60, 70, 50.0]), 0, 255)
            dull = gcol * 0.38 + np.array([20, 0, 0.0])
            h = np.clip(halo, 0, 1)
            rgb = rgb * (1 - h[..., None] * 0.4) + dull * h[..., None] * 0.4       # scorched crust around the crack
            e = smoothstep(0.0, 0.6, ember)
            rgb = rgb * (1 - e[..., None]) + dull * e[..., None]
            e2 = smoothstep(0.45, 0.9, ember)
            rgb = rgb * (1 - e2[..., None]) + gcol * e2[..., None]
            rgb = rgb * (1 - core[..., None]) + hot * core[..., None]
            sel = ember > 0.55
            if sel.any():
                gl = np.zeros(I.shape + (4,))
                lum = smoothstep(0.55, 1.0, ember) * 0.6 + core * 0.4
                gl[sel, :3] = (gcol * (0.3 + 0.7 * lum[..., None]))[sel]
                gl[sel, 3] = 255
        elif mat.pattern == 'crystal':
            lit = pt['lit']
            sel = lit > 0.5
            if sel.any():
                gl = np.zeros(I.shape + (4,))
                gl[sel, :3] = (rgb * 0.55 + gcol * 0.45 * lit[..., None])[sel]
                gl[sel, 3] = 255
        else:
            if mat.pattern == 'veins':
                sel = pt['outline']
            elif mat.pattern == 'smooth':
                sel = np.ones_like(I, dtype=bool)
            if sel is not None and sel.any():
                gl = np.zeros(I.shape + (4,))
                gl[sel, :3] = np.where(sel[..., None], rgb, 0)[sel]
                gl[sel, 3] = 255
        if sel is not None and sel.any():
            _LAST['smooth'] = np.where(sel, 200.0, _LAST['smooth'])
    return rgb, gl
