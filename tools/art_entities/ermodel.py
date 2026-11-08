"""Core of the ETERNAL REALM entity art pipeline: rig data model, UV packing, Java code generation,
keyframe evaluation (same math as vanilla KeyframeAnimations) and a small software renderer for previews.

Conventions (Mojang model space): +Y is down, the model faces -Z, -X is the entity's right side, y=24 is the
ground. Parts and cubes are authored in ABSOLUTE model coordinates of the rest pose (as if no ancestor were
rotated); the generator converts them to the parent-relative offsets that PartPose expects.
Animation values use vanilla semantics: rotations in degrees (added to the part's rotation), positions via
KeyframeAnimations.posVec (y positive = UP), scales as absolute factors (scaleVec subtracts 1).
"""
import math

import numpy as np

DEG = math.pi / 180.0


class Cube:
    def __init__(self, x, y, z, w, h, d, mat, inflate=0.0, mirror=False, share=None, tag=None):
        self.x, self.y, self.z = float(x), float(y), float(z)
        self.w, self.h, self.d = int(w), int(h), int(d)
        if (self.w, self.h, self.d) != (w, h, d):
            raise ValueError("cube sizes must be integers: %s" % ((w, h, d),))
        self.mat = mat
        self.inflate = inflate
        self.mirror = mirror
        self.share = share          # tag of another cube whose UV rectangle is reused (same size)
        self.tag = tag
        self.u = self.v = None
        self.part = None

    def uv_size(self):
        return 2 * (self.w + self.d), self.h + self.d

    def faces(self):
        """Texture rectangles (u, v, width, height) per face, named from the entity's point of view."""
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            'top': (u + d, v, w, d),
            'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h),          # model -X side
            'front': (u + d, v + d, w, h),      # model -Z side
            'left': (u + d + w, v + d, d, h),   # model +X side
            'back': (u + 2 * d + w, v + d, w, h),
        }


class Part:
    def __init__(self, name, pivot, rot=(0, 0, 0), cubes=(), children=(), opt=False):
        self.name = name
        self.pivot = tuple(float(c) for c in pivot)
        self.rot = tuple(float(r) for r in rot)      # rest rotation, degrees
        self.cubes = list(cubes)
        self.children = list(children)
        self.opt = opt                                # hidden unless a variant shows it
        self.parent = None
        for c in self.cubes:
            c.part = self
        for ch in self.children:
            ch.parent = self

    def walk(self):
        yield self
        for ch in self.children:
            yield from ch.walk()


def P(name, pivot, *items, rot=(0, 0, 0), opt=False):
    cubes = [i for i in items if isinstance(i, Cube)]
    kids = [i for i in items if isinstance(i, Part)]
    return Part(name, pivot, rot, cubes, kids, opt)


def C(x, y, z, w, h, d, mat, **kw):
    return Cube(x, y, z, w, h, d, mat, **kw)


# ----------------------------------------------------------------------------------------------- animations

class Channel:
    def __init__(self, bone, target, keys, interp='smooth'):
        self.bone, self.target, self.interp = bone, target, interp
        self.keys = [(float(k[0]), float(k[1]), float(k[2]), float(k[3])) for k in keys]
        times = [k[0] for k in self.keys]
        if times != sorted(times):
            raise ValueError("keyframes out of order for %s %s" % (bone, target))


class Anim:
    def __init__(self, name, length, loop=False, impact=None, channels=()):
        self.name, self.length, self.loop, self.impact = name, float(length), loop, impact
        self.channels = list(channels)

    def bones(self):
        return {c.bone for c in self.channels}


def anim(name, length, loop=False, impact=None, **bones):
    """bones: bone=dict(rot=[(t,x,y,z)...], pos=[...], scale=[...], lin=bool)"""
    chans = []
    for bone, spec in bones.items():
        interp = 'linear' if spec.get('lin') else 'smooth'
        for target in ('rot', 'pos', 'scale'):
            if target in spec:
                chans.append(Channel(bone, target, spec[target], spec.get(target + '_interp', interp)))
    return Anim(name, length, loop, impact, chans)


def catmull(t, p0, p1, p2, p3):
    return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                  + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t * t * t)


def channel_value(ch, t):
    keys = ch.keys
    first = len(keys)
    for i, k in enumerate(keys):
        if t <= k[0]:
            first = i
            break
    i = max(0, first - 1)
    j = min(len(keys) - 1, i + 1)
    a = 0.0
    if j != i:
        a = min(1.0, max(0.0, (t - keys[i][0]) / (keys[j][0] - keys[i][0])))
    if ch.interp == 'linear':
        return tuple(keys[i][c] + (keys[j][c] - keys[i][c]) * a for c in (1, 2, 3))
    p0 = keys[max(0, i - 1)]
    p3 = keys[min(len(keys) - 1, j + 1)]
    return tuple(catmull(a, p0[c], keys[i][c], keys[j][c], p3[c]) for c in (1, 2, 3))


def evaluate(anims_with_time, scale=1.0):
    """Returns {bone: {'rot': [x,y,z] deg, 'pos': [x,y,z] posVec units, 'scale': [x,y,z] offsets}} summed."""
    out = {}
    for an, t in anims_with_time:
        if an is None:
            continue
        tt = (t % an.length) if an.loop else t
        for ch in an.channels:
            v = channel_value(ch, tt)
            slot = out.setdefault(ch.bone, {'rot': [0.0, 0.0, 0.0], 'pos': [0.0, 0.0, 0.0], 'scale': [0.0, 0.0, 0.0]})
            if ch.target == 'scale':
                v = tuple(c - 1.0 for c in v)
            for c in range(3):
                slot[ch.target][c] += v[c] * scale
    return out


# ----------------------------------------------------------------------------------------------------- rigs

class Variant:
    def __init__(self, vid, show=(), hide=(), poses=None, palette=None, paint=None, glow=None, overrides=None,
                 extra=None):
        self.id = vid
        self.show = set(show)
        self.hide = set(hide)
        self.poses = poses or {}       # part -> dict(pos=(x,y,z) posVec, rot=(x,y,z) deg, scale=(x,y,z))
        self.palette = palette or {}
        self.paint = paint             # callable(img, rig, variant, painter) for decals
        self.glow = glow               # callable(glow_img, rig, variant, painter) or None
        self.overrides = overrides or {}   # slot -> animation name
        self.extra = extra or {}

    def palette_rgb(self, key):
        from erpaint import palette_entry
        return palette_entry(self.palette, key)['base']


class Rig:
    def __init__(self, name, java_name, root_parts, anims, variants, slots, anim_sets=None, tex_size=None,
                 head='head'):
        self.name = name
        self.java_name = java_name
        self.roots = root_parts
        self.anims = {a.name: a for a in anims}
        if len(self.anims) != len(anims):
            raise ValueError("duplicate animation names in " + name)
        self.variants = variants
        self.slots = slots                      # slot names in Java order, e.g. ['idle', 'walk', ...]
        self.anim_sets = anim_sets or {}        # optional: variant id -> {slot: anim name} base mapping
        self.tex_size = tex_size
        self.head = head
        self.parts = {}
        for r in self.roots:
            for p in r.walk():
                if p.name in self.parts:
                    raise ValueError("duplicate part %s in %s" % (p.name, name))
                if p.name == 'root':
                    raise ValueError("'root' is reserved by HierarchicalModel")
                self.parts[p.name] = p
        self.cubes = [c for p in self.parts.values() for c in p.cubes]
        for an in anims:
            for b in an.bones():
                if b not in self.parts:
                    raise ValueError("%s: animation %s uses unknown bone %s" % (name, an.name, b))
        for v in variants:
            for pname in list(v.show) + list(v.hide) + list(v.poses):
                if pname not in self.parts:
                    raise ValueError("%s/%s: unknown part %s" % (name, v.id, pname))

    def slot_anim(self, variant, slot):
        name = variant.overrides.get(slot) or self.anim_sets.get(variant.id, {}).get(slot) or slot
        if name not in self.anims:
            raise ValueError("%s/%s: no animation %s for slot %s" % (self.name, variant.id, name, slot))
        return self.anims[name]

    def visible(self, variant, part):
        if part.name in variant.hide:
            return False
        if part.opt and part.name not in variant.show:
            return False
        return True

    def toggled_parts(self):
        names = []
        for p in self.parts.values():
            if p.opt or any(p.name in v.hide for v in self.variants):
                names.append(p.name)
        return names

    def posed_parts(self):
        names = []
        for p in self.parts.values():
            if any(p.name in v.poses for v in self.variants):
                names.append(p.name)
        return names


# ------------------------------------------------------------------------------------------------- UV packing

def pack(rig):
    sized = []
    by_tag = {}
    for c in rig.cubes:
        if c.tag:
            by_tag[c.tag] = c
    for c in rig.cubes:
        if c.share is None:
            sized.append(c)
    sized.sort(key=lambda c: (-(c.uv_size()[1]), -(c.uv_size()[0])))
    candidates = [rig.tex_size] if rig.tex_size else [(64, 32), (64, 64), (128, 64), (128, 128), (256, 128),
                                                      (256, 256), (512, 256), (512, 512)]
    for tw, th in candidates:
        if try_pack(sized, tw, th):
            break
    else:
        raise ValueError("%s: textures do not fit" % rig.name)
    for c in rig.cubes:
        if c.share is not None:
            src = by_tag[c.share]
            if (src.w, src.h, src.d) != (c.w, c.h, c.d):
                raise ValueError("%s: shared UV of different size: %s" % (rig.name, c.share))
            c.u, c.v = src.u, src.v
    rig.tex_w, rig.tex_h = tw, th
    return tw, th


def try_pack(cubes, tw, th):
    """Skyline packing with a 0-pixel gap (faces of one box are contiguous, boxes never overlap)."""
    sky = [0] * tw
    for c in cubes:
        w, h = c.uv_size()
        w, h = max(w, 1), max(h, 1)
        if w > tw:
            return False
        best = None
        for x in range(0, tw - w + 1):
            top = max(sky[x:x + w])
            if top + h <= th and (best is None or top < best[1] or (top == best[1] and x < best[0])):
                best = (x, top)
        if best is None:
            return False
        x, top = best
        c.u, c.v = x, top
        for i in range(x, x + w):
            sky[i] = top + h
    return True


# --------------------------------------------------------------------------------------------- Java emitter

def jf(v):
    s = ('%.4f' % v).rstrip('0')
    if s.endswith('.'):
        s += '0'
    if s == '-0.0':
        s = '0.0'
    return s + 'F'


def java_geometry(rig, package, cls):
    lines = []
    w = lines.append
    w('// GENERATED by tools/art_entities/generate.py from the rig "%s". Do not edit: change the script and re-run.' % rig.name)
    w('package %s;' % package)
    w('')
    w('import net.minecraft.client.model.geom.PartPose;')
    w('import net.minecraft.client.model.geom.builders.CubeDeformation;')
    w('import net.minecraft.client.model.geom.builders.CubeListBuilder;')
    w('import net.minecraft.client.model.geom.builders.LayerDefinition;')
    w('import net.minecraft.client.model.geom.builders.MeshDefinition;')
    w('import net.minecraft.client.model.geom.builders.PartDefinition;')
    w('')
    w('/** Geometry, UV layout and per-variant looks of the %s rig (%d parts, %d boxes, texture %dx%d). */'
      % (rig.name, len(rig.parts), len(rig.cubes), rig.tex_w, rig.tex_h))
    w('public final class %s {' % cls)
    w('    public static final int TEXTURE_WIDTH = %d;' % rig.tex_w)
    w('    public static final int TEXTURE_HEIGHT = %d;' % rig.tex_h)
    w('')
    w('    private %s() {' % cls)
    w('    }')
    w('')
    w('    /** @param inflate extra size on every box (0 for the body, more for overlay layers such as shields) */')
    w('    public static LayerDefinition create(float inflate) {')
    w('        MeshDefinition mesh = new MeshDefinition();')
    w('        PartDefinition root = mesh.getRoot();')
    counter = [0]

    def emit(part, parent_var, parent_pivot):
        counter[0] += 1
        var = 'p%d' % counter[0]
        rel = [part.pivot[i] - parent_pivot[i] for i in range(3)]
        builder = 'CubeListBuilder.create()'
        mirror_state = False
        for c in part.cubes:
            if c.mirror != mirror_state:
                builder += '.mirror(%s)' % ('true' if c.mirror else 'false')
                mirror_state = c.mirror
            ox, oy, oz = c.x - part.pivot[0], c.y - part.pivot[1], c.z - part.pivot[2]
            infl = 'new CubeDeformation(inflate + %s)' % jf(c.inflate) if c.inflate else 'new CubeDeformation(inflate)'
            builder += '\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, %s)' % (
                c.u, c.v, jf(ox), jf(oy), jf(oz), jf(c.w), jf(c.h), jf(c.d), infl)
        if any(part.rot):
            pose = 'PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)' % (
                jf(rel[0]), jf(rel[1]), jf(rel[2]), jf(part.rot[0] * DEG), jf(part.rot[1] * DEG), jf(part.rot[2] * DEG))
        else:
            pose = 'PartPose.offset(%s, %s, %s)' % (jf(rel[0]), jf(rel[1]), jf(rel[2]))
        prefix = ('PartDefinition %s = ' % var) if part.children else ''
        w('        %s%s.addOrReplaceChild("%s", %s,\n                %s);' % (prefix, parent_var, part.name, builder, pose))
        for ch in part.children:
            emit(ch, var, part.pivot)

    for r in rig.roots:
        emit(r, 'root', (0.0, 0.0, 0.0))
    w('        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);')
    w('    }')
    w('')
    toggled = rig.toggled_parts()
    posed = rig.posed_parts()
    w('    /** Variant ids, in the order of the tables below. */')
    w('    public static final String[] VARIANTS = {%s};' % ', '.join('"%s"' % v.id for v in rig.variants))
    w('    /** Parts whose visibility depends on the variant. */')
    w('    public static final String[] TOGGLED_PARTS = {%s};' % ', '.join('"%s"' % n for n in toggled))
    w('    /** VISIBLE[variant][toggled part]. */')
    w('    public static final boolean[][] VISIBLE = {')
    for v in rig.variants:
        flags = ', '.join('true' if rig.visible(v, rig.parts[n]) else 'false' for n in toggled)
        w('            {%s}, // %s' % (flags, v.id))
    w('    };')
    w('    /** Parts with a per-variant rest adjustment. */')
    w('    public static final String[] POSED_PARTS = {%s};' % ', '.join('"%s"' % n for n in posed))
    w('    /** POSES[variant][posed part] = {posX, posY(up), posZ, rotX, rotY, rotZ (degrees), scaleX, scaleY, scaleZ} or null. */')
    w('    public static final float[][][] POSES = {')
    for v in rig.variants:
        rows = []
        for n in posed:
            p = v.poses.get(n)
            if p is None:
                rows.append('null')
            else:
                vals = list(p.get('pos', (0, 0, 0))) + list(p.get('rot', (0, 0, 0))) + list(p.get('scale', (1, 1, 1)))
                rows.append('{' + ', '.join(jf(x) for x in vals) + '}')
        w('            {%s}, // %s' % (', '.join(rows), v.id))
    w('    };')
    w('    /** Model scale of each variant (applied by the renderer). */')
    w('    public static final float[] SCALES = {%s};' % ', '.join(jf(v.extra.get('scale', 1.0)) for v in rig.variants))
    w('    /** Variants that have an emissive texture (&lt;variant&gt;_glow.png). */')
    w('    public static final boolean[] GLOWS = {%s};' % ', '.join('true' if v.glow else 'false' for v in rig.variants))
    w('}')
    return '\n'.join(lines) + '\n'


def java_animations(rig, package, cls):
    lines = []
    w = lines.append
    w('// GENERATED by tools/art_entities/generate.py from the rig "%s". Do not edit: change the script and re-run.' % rig.name)
    w('package %s;' % package)
    w('')
    w('import net.minecraft.client.animation.AnimationDefinition;')
    w('')
    w('import static com.eternalrealm.client.entity.model.AnimKit.pos;')
    w('import static com.eternalrealm.client.entity.model.AnimKit.rot;')
    w('import static com.eternalrealm.client.entity.model.AnimKit.scale;')
    w('')
    w('/** Keyframe animations of the %s rig. Slot order: %s. */' % (rig.name, ', '.join(rig.slots)))
    w('public final class %s {' % cls)
    names = sorted(rig.anims)
    for n in names:
        w('    public static final AnimationDefinition %s = %s();' % (n.upper(), camel(n)))
    w('')
    w('    private %s() {' % cls)
    w('    }')
    w('')
    w('    /** The animation of every slot for a variant (variant ids as in the geometry class). */')
    w('    public static AnimationDefinition[] forVariant(String variant) {')
    w('        return switch (variant) {')
    groups = {}
    for v in rig.variants:
        key = tuple(rig.slot_anim(v, s).name for s in rig.slots)
        groups.setdefault(key, []).append(v.id)
    default_key = tuple(rig.slot_anim(rig.variants[0], s).name for s in rig.slots)
    for key, ids in groups.items():
        if key == default_key:
            continue
        w('            case %s -> new AnimationDefinition[]{%s};' % (', '.join('"%s"' % i for i in ids),
                                                                     ', '.join(k.upper() for k in key)))
    w('            default -> new AnimationDefinition[]{%s};' % ', '.join(k.upper() for k in default_key))
    w('        };')
    w('    }')
    for n in names:
        an = rig.anims[n]
        w('')
        w('    private static AnimationDefinition %s() {' % camel(n))
        w('        return AnimationDefinition.Builder.withLength(%s)%s' % (jf(an.length), '.looping()' if an.loop else ''))
        for ch in an.channels:
            flat = ', '.join('%s, %s, %s, %s' % (jf(k[0]), jf(k[1]), jf(k[2]), jf(k[3])) for k in ch.keys)
            fn = {'rot': 'rot', 'pos': 'pos', 'scale': 'scale'}[ch.target]
            w('                .addAnimation("%s", %s(%s, %s))' % (ch.bone, fn, 'true' if ch.interp == 'linear' else 'false', flat))
        w('                .build();')
        w('    }')
    w('}')
    return '\n'.join(lines) + '\n'


def camel(name):
    parts = name.split('_')
    return parts[0] + ''.join(p.capitalize() for p in parts[1:])


# -------------------------------------------------------------------------------------------- software render

def rot_matrix(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def part_transforms(rig, variant, pose):
    """World (model space) 4x4 matrices of every part, including variant poses and animation offsets."""
    mats = {}

    def visit(part, parent_m, parent_pivot):
        rel = np.array(part.pivot) - np.array(parent_pivot)
        rx, ry, rz = [part.rot[i] * DEG for i in range(3)]
        sx = sy = sz = 1.0
        vp = variant.poses.get(part.name) if variant else None
        if vp:
            px, py, pz = vp.get('pos', (0, 0, 0))
            rel = rel + np.array([px, -py, pz])
            vr = vp.get('rot', (0, 0, 0))
            rx, ry, rz = rx + vr[0] * DEG, ry + vr[1] * DEG, rz + vr[2] * DEG
            sx, sy, sz = vp.get('scale', (1, 1, 1))
        a = pose.get(part.name)
        if a:
            rel = rel + np.array([a['pos'][0], -a['pos'][1], a['pos'][2]])
            rx, ry, rz = rx + a['rot'][0] * DEG, ry + a['rot'][1] * DEG, rz + a['rot'][2] * DEG
            sx, sy, sz = sx + a['scale'][0], sy + a['scale'][1], sz + a['scale'][2]
        m = np.eye(4)
        m[:3, :3] = rot_matrix(rx, ry, rz) @ np.diag([sx, sy, sz])
        m[:3, 3] = rel
        world = parent_m @ m
        mats[part.name] = world
        for ch in part.children:
            visit(ch, world, part.pivot)

    for r in rig.roots:
        visit(r, np.eye(4), (0.0, 0.0, 0.0))
    return mats


FACE_CORNERS = {
    # face: (corner order matching texture rect: top-left, top-right, bottom-right, bottom-left), viewed from outside
    'front': [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],
    'back': [(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)],
    'right': [(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)],
    'left': [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],
    'top': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
    'bottom': [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],
}


FACE_NORMALS = {'front': (0, 0, -1), 'back': (0, 0, 1), 'right': (-1, 0, 0), 'left': (1, 0, 0),
                'top': (0, -1, 0), 'bottom': (0, 1, 0)}


def render(rig, variant, texture, pose=None, yaw=30.0, pitch=12.0, ppu=4.0, size=None, center=None, shade=True):
    """Orthographic render. Returns RGBA image array. Mirror flags are honoured for UV orientation."""
    pose = pose or {}
    mats = part_transforms(rig, variant, pose)
    view = rot_matrix(pitch * DEG, 0, 0) @ rot_matrix(0, yaw * DEG, 0)
    tris = []
    tex = texture
    th, tw = tex.shape[0], tex.shape[1]
    light_dirs = [np.array([0.2, -1.0, -0.7]), np.array([-0.2, -1.0, 0.7])]
    light_dirs = [l / np.linalg.norm(l) for l in light_dirs]
    pts_all = []
    for part in rig.parts.values():
        if variant is not None and not visible_chain(rig, variant, part):
            continue
        m = mats[part.name]
        for c in part.cubes:
            g = c.inflate
            x0, y0, z0 = c.x - g, c.y - g, c.z - g
            x1, y1, z1 = c.x + c.w + g, c.y + c.h + g, c.z + c.d + g
            rects = c.faces()
            for fname, corners in FACE_CORNERS.items():
                u, v, fw, fh = rects[fname]
                if fw == 0 or fh == 0:
                    continue
                if fname in ('front', 'back') and (c.w == 0 or c.h == 0):
                    continue
                # cube coords are absolute; transform expects part-local: subtract pivot before the matrix
                pts = []
                for (cx, cy, cz) in corners:
                    if c.mirror:
                        cx = 1 - cx
                    local = np.array([x0 + (x1 - x0) * cx - part.pivot[0], y0 + (y1 - y0) * cy - part.pivot[1],
                                      z0 + (z1 - z0) * cz - part.pivot[2], 1.0])
                    pts.append((m @ local)[:3])
                ln = np.array(FACE_NORMALS[fname], dtype=float)
                if c.mirror and fname in ('right', 'left'):
                    ln = -ln
                normal = m[:3, :3] @ ln
                n = np.linalg.norm(normal)
                if n < 1e-9:
                    continue
                normal = normal / n
                bright = 1.0
                if shade:
                    # model space normal -> world: model y is down, so flip y for lighting
                    nw = np.array([normal[0], -normal[1], normal[2]])
                    dif = sum(max(0.0, float(np.dot(nw, -l))) for l in light_dirs)
                    bright = min(1.0, 0.4 + 0.6 * dif)
                uvq = [(u, v), (u + fw, v), (u + fw, v + fh), (u, v + fh)]
                vp = [view @ q for q in pts]
                tris.append((vp, uvq, bright))
                pts_all.extend(vp)
    if not pts_all:
        return np.zeros((8, 8, 4), np.uint8)
    arr = np.array(pts_all)
    if center is None:
        cx_, cy_ = (arr[:, 0].min() + arr[:, 0].max()) / 2, (arr[:, 1].min() + arr[:, 1].max()) / 2
    else:
        cx_, cy_ = center
    if size is None:
        span_x = (arr[:, 0].max() - arr[:, 0].min()) * ppu + 8
        span_y = (arr[:, 1].max() - arr[:, 1].min()) * ppu + 8
        size = (int(span_x), int(span_y))
    W, H = size
    img = np.zeros((H, W, 4), np.float64)
    zbuf = np.full((H, W), np.inf)
    for vp, uvq, bright in tris:
        sp = [((q[0] - cx_) * ppu + W / 2, (q[1] - cy_) * ppu + H / 2, q[2]) for q in vp]
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(img, zbuf, [sp[i] for i in tri], [uvq[i] for i in tri], tex, tw, th, bright)
    return np.clip(img, 0, 255).astype(np.uint8)


def visible_chain(rig, variant, part):
    p = part
    while p is not None:
        if not rig.visible(variant, p):
            return False
        p = p.parent
    return True


def raster(img, zbuf, sp, uv, tex, tw, th, bright):
    H, W = zbuf.shape
    xs = [p[0] for p in sp]
    ys = [p[1] for p in sp]
    x0, x1 = max(0, int(math.floor(min(xs)))), min(W - 1, int(math.ceil(max(xs))))
    y0, y1 = max(0, int(math.floor(min(ys)))), min(H - 1, int(math.ceil(max(ys))))
    if x1 < x0 or y1 < y0:
        return
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = sp
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
    l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
    l3 = 1 - l1 - l2
    eps = -1e-4
    inside = (l1 >= eps) & (l2 >= eps) & (l3 >= eps)
    if not inside.any():
        return
    z = l1 * az + l2 * bz + l3 * cz
    u = l1 * uv[0][0] + l2 * uv[1][0] + l3 * uv[2][0]
    v = l1 * uv[0][1] + l2 * uv[1][1] + l3 * uv[2][1]
    ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
    vi = np.clip(np.floor(v).astype(int), 0, th - 1)
    texel = tex[vi, ui]
    alpha_ok = texel[..., 3] > 25
    sub_z = zbuf[y0:y1 + 1, x0:x1 + 1]
    draw = inside & alpha_ok & (z < sub_z)
    sub_img = img[y0:y1 + 1, x0:x1 + 1]
    col = texel[..., :3].astype(np.float64) * bright
    sub_img[draw, :3] = col[draw]
    sub_img[draw, 3] = 255
    sub_z[draw] = z[draw]
