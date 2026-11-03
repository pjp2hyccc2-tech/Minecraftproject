"""Entity model framework (offline) for the HITORI spirits: one Python spec per spirit -> packed box UVs,
painted hi-res textures, the Java model class (com.hitori.client.model.<Name>Model) and posed previews that run
the translated setupAnim.

Coordinate rules are vanilla 1.20.1: model units are pixels, y points down, feet at y = 24, the model faces -z
(its right hand is at -x). ModelPart.translateAndRotate = translate(x/16, y/16, z/16) * rotZYX(zRot, yRot, xRot) *
scale.

Texture density: the UV layout is in model pixels (LayerDefinition.create(mesh, W, H)) but the PNG is S times
larger (S = Spec.px, usually 2): every model pixel carries S x S texels. Box sizes may therefore be multiples of
1/S (0.5 px at S = 2) and still map onto whole texels.

Translucency (the spirits render with entityTranslucent, depth writes on, no sorting inside the model): cubes can
drop faces (CubeListBuilder.addBox(..., Set<Direction>)) so that flat cards are single-sided and internal faces
that would show through the translucent skin are never drawn.
"""
import math

import numpy as np

import render3d as R

# box-UV face name -> net.minecraft.core.Direction of the polygon that uses it
FACE_DIR = {'top': 'DOWN', 'bottom': 'UP', 'right': 'WEST', 'front': 'NORTH', 'left': 'EAST', 'back': 'SOUTH'}
DIR_FACE = {v.lower(): k for k, v in FACE_DIR.items()}
ALL_FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


class Cube:
    def __init__(self, origin, size, mat=None, grow=0.0, mirror=False, tag=None, faces=None, uv_of=None):
        self.origin = tuple(float(v) for v in origin)
        self.size = tuple(float(v) for v in size)
        for v in self.size:
            assert abs(v * 4 - round(v * 4)) < 1e-9, f'cube sizes must be multiples of 0.25: {size}'
        self.uv_of = uv_of      # tag of another cube of the same size whose texture region this one reuses
        self.mat = mat
        self.grow = grow
        self.mirror = mirror
        self.tag = tag
        self.faces = tuple(ALL_FACES if faces is None else faces)
        for f in self.faces:
            assert f in ALL_FACES, f
        assert not (grow and faces is not None), 'face sets cannot be combined with a CubeDeformation'
        self.uv = None
        self.part = None

    @property
    def footprint(self):
        w, h, d = self.size
        return 2 * d + 2 * w, d + h

    def regions(self):
        """face -> (u0, v0, w, h) in layout units (vanilla names: right = -x side, left = +x side)."""
        u, v = self.uv
        w, h, d = self.size
        return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.cubes = list(cubes)
        for c in self.cubes:
            c.part = self
        self.children = list(children)
        self.parent = None
        for c in self.children:
            c.parent = self
        self.reset()

    def add(self, *children):
        for c in children:
            c.parent = self
            self.children.append(c)
        return self

    def cube(self, *cubes):
        for c in cubes:
            c.part = self
            self.cubes.append(c)
        return self

    def reset(self):
        self.x, self.y, self.z = self.pivot
        self.xRot, self.yRot, self.zRot = self.rot
        self.xScale = self.yScale = self.zScale = 1.0
        self.visible = True
        self.skipDraw = False

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    @property
    def jname(self):
        parts = self.name.split('_')
        return parts[0] + ''.join(p.capitalize() for p in parts[1:])

    def matrix(self):
        M = R.affine(None, [self.x / 16.0, self.y / 16.0, self.z / 16.0])
        M = M @ R.affine(R.rot_z(self.zRot) @ R.rot_y(self.yRot) @ R.rot_x(self.xRot))
        if (self.xScale, self.yScale, self.zScale) != (1.0, 1.0, 1.0):
            M = M @ R.scale_m(self.xScale, self.yScale, self.zScale)
        return M

    def rest_matrix(self):
        """Model-space matrix of this part in the rest pose (pivots + initial rotations), in pixels."""
        M = np.eye(4)
        chain = []
        p = self
        while p is not None:
            chain.append(p)
            p = p.parent
        for q in reversed(chain):
            T = R.affine(None, list(q.pivot))
            Rm = R.affine(R.rot_z(q.rot[2]) @ R.rot_y(q.rot[1]) @ R.rot_x(q.rot[0]))
            M = M @ T @ Rm
        return M


# ------------------------------------------------------------------------------------------------ Java HashMap order
def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def hashmap_order(names):
    """Iteration order of a java.util.HashMap<String, ?> filled in this insertion order (PartDefinition children:
    the order ModelPart renders its children in)."""
    n = len(names)
    cap = 16
    while n > cap * 0.75:
        cap *= 2

    def bucket(s):
        h = java_hash(s)
        h ^= (h >> 16)
        return h & (cap - 1)
    return [nm for _, _, nm in sorted((bucket(nm), i, nm) for i, nm in enumerate(names))]


def render_children(part):
    order = hashmap_order([c.name for c in part.children])
    by = {c.name: c for c in part.children}
    return [by[n] for n in order]


# ------------------------------------------------------------------------------------------------ UV packing
def pack(root, tex_w, tex_h=None):
    allc = [c for p in root.walk() for c in p.cubes]
    cubes = [c for c in allc if c.uv_of is None]
    order = sorted(cubes, key=lambda c: (-math.ceil(c.footprint[1]), -math.ceil(c.footprint[0])))
    W = tex_w
    for H in ([tex_h] if tex_h else [32, 64, 128, 256]):
        occ = np.zeros((H, W), bool)
        ok = True
        for c in order:
            fw, fh = c.footprint
            fw, fh = max(1, math.ceil(fw)), max(1, math.ceil(fh))
            placed = False
            for v in range(0, H - fh + 1):
                row = occ[v:v + fh]
                for u in range(0, W - fw + 1):
                    if not row[:, u:u + fw].any():
                        occ[v:v + fh, u:u + fw] = True
                        c.uv = (u, v)
                        placed = True
                        break
                if placed:
                    break
            if not placed:
                ok = False
                break
        if ok:
            by = {c.tag: c for c in cubes if c.tag}
            for c in allc:
                if c.uv_of is not None:
                    src = by[c.uv_of]
                    assert src.size == c.size, f'shared uv {c.tag} -> {c.uv_of}: sizes differ'
                    c.uv = src.uv
            return W, H
    raise ValueError(f'textures do not fit in {W} wide (used {sum(math.ceil(c.footprint[0]) * math.ceil(c.footprint[1]) for c in cubes)} of {W * (tex_h or 256)})')


def advance(st, pose, pt):
    """Python twin of the Java advance(): the clock that holds while the pose time holds."""
    if st.clockPose == -2:
        st.clock = pt
        st.clockPt = pt
    elif pose != st.clockPose:
        st.clock += min(40.0, max(0.0, pt))
        st.clockPt = pt
    elif pt > st.clockPt:
        st.clock += min(40.0, pt - st.clockPt)
        st.clockPt = pt
    st.clockPose = pose
    return st.clock


def chase(st, slot, target, w, z, t):
    """Python twin of the Java chase()."""
    last = st.st[slot]
    if last < 0.0 or t < last or t - last > 40.0:
        st.sx[slot] = target
        st.sv[slot] = 0.0
    elif t > last:
        dt = t - last
        n = min(160, max(1, int(math.ceil(dt * 4.0))))
        h = dt / n
        x, v = st.sx[slot], st.sv[slot]
        for _ in range(n):
            v += (w * w * (target - x) - 2.0 * z * w * v) * h
            x += v * h
        st.sx[slot], st.sv[slot] = x, v
    st.st[slot] = t
    return st.sx[slot]


def flop(st, slot, part, fl, gx, gy, gz, w, z, t, *chain):
    """Python twin of the Java flop()."""
    P = R.rot_z(part.zRot) @ R.rot_y(part.yRot) @ R.rot_x(part.xRot)
    r = P[:, 1]
    M = np.eye(3)
    for p in chain:
        M = M @ (R.rot_z(p.zRot) @ R.rot_y(p.yRot) @ R.rot_x(p.xRot))
    h = M.T @ np.array([gx, gy, gz], float)
    h = h / max(1e-4, float(np.linalg.norm(h)))
    d = r + (h - r) * fl
    d = d / max(1e-4, float(np.linalg.norm(d)))
    e = np.array(st.sx[slot:slot + 3], float)
    last = st.st[slot]
    if last < 0.0 or t < last or t - last > 40.0:
        e = d.copy()
        st.sv[slot:slot + 3] = [0.0, 0.0, 0.0]
    elif t > last:
        dt = t - last
        n = min(160, max(1, int(math.ceil(dt * 4.0))))
        h = dt / n
        u = np.array(st.sv[slot:slot + 3], float)
        for _ in range(n):
            u += (w * w * (d - e) - 2.0 * z * w * u) * h
            u -= float(u @ e) * e
            e = e + u * h
            e /= max(1e-4, float(np.linalg.norm(e)))
        st.sv[slot:slot + 3] = list(u)
    st.sx[slot:slot + 3] = list(e)
    st.st[slot] = t
    v = np.cross(r, e)
    k = 1.0 / max(1e-3, 1.0 + float(r @ e))
    Vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    D = np.eye(3) + Vx + (np.outer(v, v) - float(v @ v) * np.eye(3)) * k
    N = D @ P
    part.yRot = math.asin(max(-1.0, min(1.0, -N[2, 0])))
    part.xRot = math.atan2(N[2, 1], N[2, 2])
    part.zRot = math.atan2(N[1, 0], N[0, 0])


def level(target, *chain):
    """Python twin of the Java level(): undo the ancestors' rotations (ZYX) so the target stays level."""
    M = np.eye(3)
    for p in chain:
        M = M @ (R.rot_z(p.zRot) @ R.rot_y(p.yRot) @ R.rot_x(p.xRot))
    target.yRot = math.asin(max(-1.0, min(1.0, -M[0, 2])))
    target.xRot = math.atan2(M[1, 2], M[2, 2])
    target.zRot = math.atan2(M[0, 1], M[0, 0])


# ------------------------------------------------------------------------------------------------ Java
def jf(v):
    s = f'{float(v):.4f}'.rstrip('0')
    if s.endswith('.'):
        s += '0'
    return s + 'F'


# Java helpers shared by every model (static, pure; translated to Python for the previews).
HELPERS = '''
/** Smoothstep 0..1. */
private static float ease(float x) {
    float c = Mth.clamp(x, 0.0F, 1.0F);
    return c * c * (3.0F - 2.0F * c);
}

/** Fast in, slow out (0..1). */
private static float easeOut(float x) {
    float c = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
    return 1.0F - c * c * c;
}

/** Slow in, fast out (0..1). */
private static float easeIn(float x) {
    float c = Mth.clamp(x, 0.0F, 1.0F);
    return c * c * c;
}

/** 0 before a, rises to 1 at b (smooth). */
private static float span(float x, float a, float b) {
    return ease((x - a) / (b - a));
}

/** Pseudo random 0..1 for an integer-valued x (bounded argument: Mth.sin is a lookup table). */
private static float hash(float x) {
    float k = x - 1024.0F * (float) Mth.floor(x / 1024.0F);
    float s = Mth.sin(k * 12.9898F + 4.1414F) * 43758.547F;
    return s - (float) Mth.floor(s);
}

/** Smooth value noise -1..1. */
private static float noise(float x) {
    float i = (float) Mth.floor(x);
    float f = x - i;
    float u = f * f * (3.0F - 2.0F * f);
    return Mth.lerp(u, hash(i), hash(i + 1.0F)) * 2.0F - 1.0F;
}

/** Missing frames: time holds still for most of every 3-tick window, then jumps to catch up (g = 0..1). */
private static float stutter(float x, float g) {
    float p = 3.0F;
    float i = (float) Mth.floor(x / p);
    float f = x / p - i;
    float hold = Mth.clamp(g, 0.0F, 1.0F) * (0.55F + 0.4F * hash(i + 17.0F));
    float s = f < hold ? 0.0F : (f - hold) / (1.0F - hold);
    return (i + s) * p;
}

/** 1 -> 0 over 3 ticks once every period ticks (phase shifted): sudden twitches. t must be >= 0. */
private static float twitch(float t, float period, float phase) {
    float m = (t + phase) % period;
    return m < 3.0F ? 1.0F - m / 3.0F : 0.0F;
}

/** Stepped progress: holds still, then lurches to the next step (jerky motion), x in 0..1. */
private static float jerky(float x, float steps) {
    float c = Mth.clamp(x, 0.0F, 1.0F) * steps;
    float i = (float) Mth.floor(c);
    float f = c - i;
    float s = f < 0.55F ? 0.0F : ease((f - 0.55F) / 0.45F);
    return Math.min(1.0F, (i + s) / steps);
}

/** Damped spring response 0..1 with overshoot (x in ticks since the step). */
private static float spring(float x, float freq, float damp) {
    if (x <= 0.0F) {
        return 0.0F;
    }
    return 1.0F - (float) Math.exp(-damp * x) * Mth.cos(freq * x);
}
'''

STATE_JAVA = '''
    /** Per-entity animation memory: the previous pose, the values to blend from, smoothed values. */
    private static final class State {
        int pose = -1;
        int prev = -1;
        boolean blending;
        float lastAge = -1000.0F;
        float[] from;
        float[] last;
        final float[] fv = new float[8];
        final float[] ft = new float[] {-1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F};
        float clock;
        int clockPose = -2;
        float clockPt;
        final float[] sx = new float[48];
        final float[] sv = new float[48];
        final float[] st = new float[] {-1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F};
    }

    private State state(Entity entity, int pose, float age) {
        State s = this.states.get(entity);
        if (s == null) {
            s = new State();
            s.from = new float[this.all.length * 9];
            s.last = new float[this.all.length * 9];
            s.pose = pose;
            this.states.put(entity, s);
        } else if (s.pose != pose) {
            System.arraycopy(s.last, 0, s.from, 0, s.last.length);
            // blend only from a frame that was actually seen a moment ago
            s.blending = age - s.lastAge < 10.0F;
            s.prev = s.pose;
            s.pose = pose;
        }
        return s;
    }

    /** Eases every part from the values shown when the pose changed to the new pose over `ticks`. */
    private void blend(State s, float pt, float ticks, float age) {
        boolean on = s.blending && ticks > 0.0F && pt < ticks;
        if (!on) {
            s.blending = false;
        }
        float k = on ? ease(pt / ticks) : 1.0F;
        float[] f = s.from;
        float[] l = s.last;
        for (int i = 0; i < this.all.length; i++) {
            ModelPart p = this.all[i];
            int o = i * 9;
            if (on) {
                p.x = Mth.lerp(k, f[o], p.x);
                p.y = Mth.lerp(k, f[o + 1], p.y);
                p.z = Mth.lerp(k, f[o + 2], p.z);
                p.xRot = Mth.lerp(k, f[o + 3], p.xRot);
                p.yRot = Mth.lerp(k, f[o + 4], p.yRot);
                p.zRot = Mth.lerp(k, f[o + 5], p.zRot);
                p.xScale = Mth.lerp(k, f[o + 6], p.xScale);
                p.yScale = Mth.lerp(k, f[o + 7], p.yScale);
                p.zScale = Mth.lerp(k, f[o + 8], p.zScale);
            }
            l[o] = p.x;
            l[o + 1] = p.y;
            l[o + 2] = p.z;
            l[o + 3] = p.xRot;
            l[o + 4] = p.yRot;
            l[o + 5] = p.zRot;
            l[o + 6] = p.xScale;
            l[o + 7] = p.yScale;
            l[o + 8] = p.zScale;
        }
        s.lastAge = age;
    }

    /** Undoes the rotations of `chain` (the target's ancestors, root side first) so that `target` stays level / hangs
     *  straight down in model space (trays, glasses, strands of hair, keys); add any sway afterwards. */
    private static void level(ModelPart target, ModelPart... chain) {
        float m00 = 1.0F, m01 = 0.0F, m02 = 0.0F, m10 = 0.0F, m11 = 1.0F, m12 = 0.0F, m20 = 0.0F, m21 = 0.0F, m22 = 1.0F;
        for (ModelPart p : chain) {
            float cz = Mth.cos(p.zRot), sz = Mth.sin(p.zRot), cy = Mth.cos(p.yRot), sy = Mth.sin(p.yRot);
            float cx = Mth.cos(p.xRot), sx = Mth.sin(p.xRot);
            float r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
            float r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
            float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
            float n00 = m00 * r00 + m01 * r10 + m02 * r20, n01 = m00 * r01 + m01 * r11 + m02 * r21, n02 = m00 * r02 + m01 * r12 + m02 * r22;
            float n10 = m10 * r00 + m11 * r10 + m12 * r20, n11 = m10 * r01 + m11 * r11 + m12 * r21, n12 = m10 * r02 + m11 * r12 + m12 * r22;
            float n20 = m20 * r00 + m21 * r10 + m22 * r20, n21 = m20 * r01 + m21 * r11 + m22 * r21, n22 = m20 * r02 + m21 * r12 + m22 * r22;
            m00 = n00; m01 = n01; m02 = n02; m10 = n10; m11 = n11; m12 = n12; m20 = n20; m21 = n21; m22 = n22;
        }
        // local rotation = transpose(M), decomposed as Rz * Ry * Rx
        target.yRot = (float) Math.asin(Mth.clamp(-m02, -1.0F, 1.0F));
        target.xRot = (float) Math.atan2(m12, m22);
        target.zRot = (float) Math.atan2(m01, m00);
    }

    /** A value that follows its target with a lag (exponential, `speed` per tick), remembered per entity. */
    private static float follow(State s, int slot, float target, float speed, float t) {
        float last = s.ft[slot];
        if (last < 0.0F || t < last || t - last > 40.0F) {
            s.fv[slot] = target;
        } else {
            float k = 1.0F - (float) Math.exp(-speed * (t - last));
            s.fv[slot] = s.fv[slot] + (target - s.fv[slot]) * k;
        }
        s.ft[slot] = t;
        return s.fv[slot];
    }

    /** The animation clock: pose time accumulated across pose changes, so it stands still while the game holds the
     *  pose time (frozen under the player's eyes) and steps when the pose time steps (stop-motion). */
    private static float advance(State s, int pose, float pt) {
        if (s.clockPose == -2) {
            s.clock = pt;
            s.clockPt = pt;
        } else if (pose != s.clockPose) {
            s.clock += Mth.clamp(pt, 0.0F, 40.0F);
            s.clockPt = pt;
        } else if (pt > s.clockPt) {
            s.clock += Math.min(40.0F, pt - s.clockPt);
            s.clockPt = pt;
        }
        // (a pose time that steps back within a pose - a held camera frame drawn after a live one - passes no time)
        s.clockPose = pose;
        return s.clock;
    }

    /** A damped spring per entity (slot 0..47): chases `target` with inertia and overshoot (w: angular frequency per
     *  tick, z: damping ratio), integrated in at most 160 sub-steps of 1/4 tick on the clock `t`; it restarts at the
     *  target after a gap of more than 40 ticks or when time runs backwards, and holds while `t` holds. */
    private static float chase(State s, int slot, float target, float w, float z, float t) {
        float last = s.st[slot];
        if (last < 0.0F || t < last || t - last > 40.0F) {
            s.sx[slot] = target;
            s.sv[slot] = 0.0F;
        } else if (t > last) {
            float dt = t - last;
            int n = Math.min(160, Math.max(1, (int) Math.ceil(dt * 4.0F)));
            float h = dt / (float) n;
            float x = s.sx[slot];
            float v = s.sv[slot];
            for (int i = 0; i < n; i++) {
                v += (w * w * (target - x) - 2.0F * z * w * v) * h;
                x += v * h;
            }
            s.sx[slot] = x;
            s.sv[slot] = v;
        }
        s.st[slot] = t;
        return s.sx[slot];
    }

    /** Plush physics: a part extending along its +y leans from its posed direction toward the model-space direction
     *  (gx, gy, gz) (gravity = 0, 1, 0) by `flop` (0 stiff .. 1 hanging) and follows with inertia: the direction (in its
     *  parent's frame) is a spherical pendulum (slots `slot` .. `slot + 2`), so it lags, swings and settles
     *  whenever its ancestors (`chain`, root side first) move. The correction is the smallest rotation onto the posed
     *  rotation, so the part keeps its twist: author rest poses within ~120 degrees of where the part will hang. */
    private static void flop(State s, int slot, ModelPart part, float flop, float gx, float gy, float gz, float w, float z, float t,
                             ModelPart... chain) {
        float pz = Mth.cos(part.zRot), qz = Mth.sin(part.zRot), py = Mth.cos(part.yRot), qy = Mth.sin(part.yRot);
        float px = Mth.cos(part.xRot), qx = Mth.sin(part.xRot);
        float p00 = pz * py, p01 = pz * qy * qx - qz * px, p02 = pz * qy * px + qz * qx;
        float p10 = qz * py, p11 = qz * qy * qx + pz * px, p12 = qz * qy * px - pz * qx;
        float p20 = -qy, p21 = py * qx, p22 = py * px;
        float rx = p01, ry = p11, rz = p21;
        float m00 = 1.0F, m01 = 0.0F, m02 = 0.0F, m10 = 0.0F, m11 = 1.0F, m12 = 0.0F, m20 = 0.0F, m21 = 0.0F, m22 = 1.0F;
        for (ModelPart p : chain) {
            float cz = Mth.cos(p.zRot), sz = Mth.sin(p.zRot), cy = Mth.cos(p.yRot), sy = Mth.sin(p.yRot);
            float cx = Mth.cos(p.xRot), sx = Mth.sin(p.xRot);
            float r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
            float r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
            float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
            float n00 = m00 * r00 + m01 * r10 + m02 * r20, n01 = m00 * r01 + m01 * r11 + m02 * r21, n02 = m00 * r02 + m01 * r12 + m02 * r22;
            float n10 = m10 * r00 + m11 * r10 + m12 * r20, n11 = m10 * r01 + m11 * r11 + m12 * r21, n12 = m10 * r02 + m11 * r12 + m12 * r22;
            float n20 = m20 * r00 + m21 * r10 + m22 * r20, n21 = m20 * r01 + m21 * r11 + m22 * r21, n22 = m20 * r02 + m21 * r12 + m22 * r22;
            m00 = n00; m01 = n01; m02 = n02; m10 = n10; m11 = n11; m12 = n12; m20 = n20; m21 = n21; m22 = n22;
        }
        // the hang direction in the parent frame (transpose(M) * g), mixed with the posed direction
        float hx = m00 * gx + m10 * gy + m20 * gz, hy = m01 * gx + m11 * gy + m21 * gz, hz = m02 * gx + m12 * gy + m22 * gz;
        float hl = Math.max(1.0E-4F, Mth.sqrt(hx * hx + hy * hy + hz * hz));
        float dx = Mth.lerp(flop, rx, hx / hl), dy = Mth.lerp(flop, ry, hy / hl), dz = Mth.lerp(flop, rz, hz / hl);
        float dl = Math.max(1.0E-4F, Mth.sqrt(dx * dx + dy * dy + dz * dz));
        // a spherical pendulum toward that direction: a damped spring whose state stays a unit vector (no angles, so
        // no singular direction, and it can never whip through the centre); slots slot .. slot + 2
        dx /= dl;
        dy /= dl;
        dz /= dl;
        float ex = s.sx[slot], ey = s.sx[slot + 1], ez = s.sx[slot + 2];
        float last = s.st[slot];
        if (last < 0.0F || t < last || t - last > 40.0F) {
            ex = dx;
            ey = dy;
            ez = dz;
            s.sv[slot] = 0.0F;
            s.sv[slot + 1] = 0.0F;
            s.sv[slot + 2] = 0.0F;
        } else if (t > last) {
            float dt = t - last;
            int n = Math.min(160, Math.max(1, (int) Math.ceil(dt * 4.0F)));
            float h = dt / (float) n;
            float ux = s.sv[slot], uy = s.sv[slot + 1], uz = s.sv[slot + 2];
            for (int i = 0; i < n; i++) {
                ux += (w * w * (dx - ex) - 2.0F * z * w * ux) * h;
                uy += (w * w * (dy - ey) - 2.0F * z * w * uy) * h;
                uz += (w * w * (dz - ez) - 2.0F * z * w * uz) * h;
                float rad = ux * ex + uy * ey + uz * ez;
                ux -= rad * ex;
                uy -= rad * ey;
                uz -= rad * ez;
                ex += ux * h;
                ey += uy * h;
                ez += uz * h;
                float el = Math.max(1.0E-4F, Mth.sqrt(ex * ex + ey * ey + ez * ez));
                ex /= el;
                ey /= el;
                ez /= el;
            }
            s.sv[slot] = ux;
            s.sv[slot + 1] = uy;
            s.sv[slot + 2] = uz;
        }
        s.sx[slot] = ex;
        s.sx[slot + 1] = ey;
        s.sx[slot + 2] = ez;
        s.st[slot] = t;
        // D = the smallest rotation taking r onto e: I + [v]x + [v]x^2 / (1 + r.e), v = r x e; then N = D * P
        float vx = ry * ez - rz * ey, vy = rz * ex - rx * ez, vz = rx * ey - ry * ex;
        float k = 1.0F / Math.max(1.0E-3F, 1.0F + rx * ex + ry * ey + rz * ez);
        float d00 = 1.0F - (vy * vy + vz * vz) * k, d01 = -vz + vx * vy * k, d02 = vy + vx * vz * k;
        float d10 = vz + vx * vy * k, d11 = 1.0F - (vx * vx + vz * vz) * k, d12 = -vx + vy * vz * k;
        float d20 = -vy + vx * vz * k, d21 = vx + vy * vz * k, d22 = 1.0F - (vx * vx + vy * vy) * k;
        float n00 = d00 * p00 + d01 * p10 + d02 * p20, n10 = d10 * p00 + d11 * p10 + d12 * p20;
        float n20 = d20 * p00 + d21 * p10 + d22 * p20, n21 = d20 * p01 + d21 * p11 + d22 * p21, n22 = d20 * p02 + d21 * p12 + d22 * p22;
        part.yRot = (float) Math.asin(Mth.clamp(-n20, -1.0F, 1.0F));
        part.xRot = (float) Math.atan2(n21, n22);
        part.zRot = (float) Math.atan2(n10, n00);
    }
'''

# translated prologue shared by all models: missing frames (glitch) shift time, pose time and the walk cycle
PROLOGUE = '''
float t = ageInTicks;
if (glitch > 0.0F) {
    float held = stutter(ageInTicks, glitch);
    float lost = ageInTicks - held;
    pt = Math.max(0.0F, pt - lost);
    limbSwing = limbSwing - lost * limbSwingAmount;
    t = held;
}
float yaw = netHeadYaw * 0.017453292F;
float pitch = headPitch * 0.017453292F;
'''


def java_source(spec):
    root = spec.root
    name = spec.java_name
    parts = [p for p in root.walk() if p is not root]
    uses_faces = any(set(c.faces) != set(ALL_FACES) for p in parts for c in p.cubes)
    lines = []
    A = lines.append
    A('package com.hitori.client.model;')
    A('')
    A('import com.hitori.entity.Animated;')
    if uses_faces:
        A('import java.util.EnumSet;')
    A('import java.util.Map;')
    A('import java.util.WeakHashMap;')
    A('import net.minecraft.client.model.HierarchicalModel;')
    A('import net.minecraft.client.model.geom.ModelLayerLocation;')
    A('import net.minecraft.client.model.geom.ModelPart;')
    A('import net.minecraft.client.model.geom.PartPose;')
    if any(c.grow for p in parts for c in p.cubes):
        A('import net.minecraft.client.model.geom.builders.CubeDeformation;')
    A('import net.minecraft.client.model.geom.builders.CubeListBuilder;')
    A('import net.minecraft.client.model.geom.builders.LayerDefinition;')
    A('import net.minecraft.client.model.geom.builders.MeshDefinition;')
    A('import net.minecraft.client.model.geom.builders.PartDefinition;')
    if spec.translucent:
        A('import net.minecraft.client.renderer.RenderType;')
    if uses_faces:
        A('import net.minecraft.core.Direction;')
    A('import net.minecraft.resources.ResourceLocation;')
    A('import net.minecraft.util.Mth;')
    A('import net.minecraft.world.entity.Entity;')
    if not spec.plain_entity:
        A('import net.minecraft.world.entity.Mob;')
    A('')
    A('/**')
    for l in spec.doc.strip().splitlines():
        A((' * ' + l.strip()).rstrip())
    A(' *')
    A(f' * <p>Texture {spec.tex_w * spec.px}x{spec.tex_h * spec.px} ({spec.px} texels per model pixel, UV layout '
      f'{spec.tex_w}x{spec.tex_h}): textures/entity/{spec.id}.png + {spec.id}_eyes.png.')
    A(' * <p>Generated by tools/art/ent_gen.py (spec tools/art/' + spec.module + '.py) - edit the spec, not this file.')
    A(' */')
    if spec.plain_entity:
        A(f'public class {name}<T extends Entity> extends HierarchicalModel<T> {{')
    else:
        A(f'public class {name}<T extends Mob & Animated> extends HierarchicalModel<T> {{')
    A(f'    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("hitori", "{spec.id}"), "main");')
    for pname, pid in spec.poses:
        A(f'    public static final int {pname} = {pid};')
    A('')
    A('    private final ModelPart root;')
    for p in parts:
        A(f'    private final ModelPart {p.jname};')
    A('    private final ModelPart[] all;')
    A('    private final Map<Entity, State> states = new WeakHashMap<>();')
    A('')
    A(f'    public {name}(ModelPart root) {{')
    if spec.translucent:
        A('        super(RenderType::entityTranslucent);')
    A('        this.root = root;')
    for p in parts:
        par = 'root' if p.parent is root else f'this.{p.parent.jname}'
        A(f'        this.{p.jname} = {par}.getChild("{p.name}");')
    A('        this.all = new ModelPart[] {' + ', '.join(f'this.{p.jname}' for p in parts) + '};')
    A('    }')
    A('')
    A('    public static LayerDefinition createBodyLayer() {')
    A('        MeshDefinition mesh = new MeshDefinition();')
    A('        PartDefinition root = mesh.getRoot();')
    for p in parts:
        par = 'root' if p.parent is root else 'p' + p.parent.jname[0].upper() + p.parent.jname[1:]
        cl = 'CubeListBuilder.create()'
        for c in p.cubes:
            u, v = c.uv
            ox, oy, oz = c.origin
            w, h, d = c.size
            cl += f'.texOffs({u}, {v})'
            if c.mirror:
                cl += '.mirror()'
            if c.grow:
                cl += f'.addBox({jf(ox)}, {jf(oy)}, {jf(oz)}, {jf(w)}, {jf(h)}, {jf(d)}, new CubeDeformation({jf(c.grow)}))'
            elif set(c.faces) != set(ALL_FACES):
                dirs = ', '.join('Direction.' + FACE_DIR[f] for f in ALL_FACES if f in c.faces)
                cl += f'.addBox({jf(ox)}, {jf(oy)}, {jf(oz)}, {jf(w)}, {jf(h)}, {jf(d)}, EnumSet.of({dirs}))'
            else:
                cl += f'.addBox({jf(ox)}, {jf(oy)}, {jf(oz)}, {jf(w)}, {jf(h)}, {jf(d)})'
            if c.mirror:
                cl += '.mirror(false)'
        px_, py_, pz_ = p.pivot
        rx, ry, rz = p.rot
        if (rx, ry, rz) == (0, 0, 0):
            pose = 'PartPose.ZERO' if (px_, py_, pz_) == (0, 0, 0) else f'PartPose.offset({jf(px_)}, {jf(py_)}, {jf(pz_)})'
        else:
            pose = f'PartPose.offsetAndRotation({jf(px_)}, {jf(py_)}, {jf(pz_)}, {jf(rx)}, {jf(ry)}, {jf(rz)})'
        call = f'{par}.addOrReplaceChild("{p.name}", {cl}, {pose});'
        if p.children:
            var = 'p' + p.jname[0].upper() + p.jname[1:]
            A(f'        PartDefinition {var} = {call}')
        else:
            A(f'        {call}')
    A(f'        return LayerDefinition.create(mesh, {spec.tex_w}, {spec.tex_h});')
    A('    }')
    A('')
    A('    @Override')
    A('    public ModelPart root() {')
    A('        return this.root;')
    A('    }')
    A('')
    A('    @Override')
    A('    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {')
    A('        this.root().getAllParts().forEach(ModelPart::resetPose);')
    A('        // resetPose restores only positions and rotations: scales and visibility are reset here')
    A('        for (ModelPart p : this.all) {')
    A('            p.xScale = 1.0F;')
    A('            p.yScale = 1.0F;')
    A('            p.zScale = 1.0F;')
    A('            p.visible = true;')
    A('            p.skipDraw = false;')
    A('        }')
    A('        // hidden while the entity is invisible (the eyes layer draws this model too, so it hides as well)')
    A('        this.root.visible = !entity.isInvisible();')
    A('        if (!this.root.visible) {')
    A('            return;')
    A('        }')
    A('        float partial = ageInTicks - (float) Mth.floor(ageInTicks);')
    if spec.plain_entity:
        A('        int pose = entity instanceof Animated a ? a.pose() : 0;')
        A('        float pt = entity instanceof Animated a ? Math.max(0.0F, a.poseTime(partial)) : ageInTicks;')
        A('        float glitch = entity instanceof Animated a ? Mth.clamp(a.glitch(partial), 0.0F, 1.0F) : 0.0F;')
    else:
        A('        int pose = entity.pose();')
        A('        float pt = Math.max(0.0F, entity.poseTime(partial));')
        A('        float glitch = Mth.clamp(entity.glitch(partial), 0.0F, 1.0F);')
    A('        final float poseTicks = pt;')
    A('        State mem = this.state(entity, pose, ageInTicks);')
    A('        int prev = mem.prev;')
    A('        float blend = 8.0F;')
    for l in ((spec.prologue or PROLOGUE) + spec.anim).strip('\n').splitlines():
        A(('        ' + l) if l.strip() else '')
    A('        this.blend(mem, poseTicks, blend, ageInTicks);')
    for l in spec.post.strip('\n').splitlines():
        A(('        ' + l) if l.strip() else '')
    A('    }')
    A('')
    for l in (HELPERS + spec.helpers).strip('\n').splitlines():
        A(('    ' + l) if l.strip() else '')
    for l in STATE_JAVA.strip('\n').splitlines():
        A(l)
    A('}')
    return '\n'.join(lines) + '\n'


# ------------------------------------------------------------------------------------------------ posing (python)
class FakeEntity:
    def __init__(self, pose=0, pose_time=0.0, glitch=0.0, invisible=False, ident=7):
        self._pose = pose
        self._t = pose_time
        self._g = glitch
        self._inv = invisible
        self._id = ident

    def isInvisible(self):
        return self._inv

    def pose(self):
        return self._pose

    def poseTime(self, partial):
        return self._t

    def glitch(self, partial):
        return self._g

    def getId(self):
        return self._id


class State:
    def __init__(self):
        self.pose = -1
        self.prev = -1
        self.blending = False
        self.lastAge = -1000.0
        self.frm = None
        self.last = None
        self.fv = [0.0] * 8
        self.ft = [-1.0] * 8
        self.clock = 0.0
        self.clockPose = -2
        self.clockPt = 0.0
        self.sx = [0.0] * 48
        self.sv = [0.0] * 48
        self.st = [-1.0] * 48


def ease(x):
    c = min(1.0, max(0.0, x))
    return c * c * (3 - 2 * c)


ATTRS = ('x', 'y', 'z', 'xRot', 'yRot', 'zRot', 'xScale', 'yScale', 'zScale')


class Animator:
    """Runs a spec's translated setupAnim with the same per-entity state machine as the Java template."""

    def __init__(self, spec, fn):
        self.spec = spec
        self.fn = fn
        self.parts = [p for p in spec.root.walk() if p is not spec.root]
        self.states = {}

    def state(self, ent, pose, age):
        s = self.states.get(ent._id)
        n = len(self.parts) * 9
        if s is None:
            s = State()
            s.frm = [0.0] * n
            s.last = [0.0] * n
            s.pose = pose
            self.states[ent._id] = s
        elif s.pose != pose:
            s.frm = list(s.last)
            s.blending = age - s.lastAge < 10.0
            s.prev = s.pose
            s.pose = pose
        return s

    def blend(self, s, pt, ticks, age):
        on = s.blending and ticks > 0 and pt < ticks
        if not on:
            s.blending = False
        k = ease(pt / ticks) if on else 1.0
        for i, p in enumerate(self.parts):
            o = i * 9
            for j, a in enumerate(ATTRS):
                if on:
                    setattr(p, a, s.frm[o + j] + k * (getattr(p, a) - s.frm[o + j]))
                s.last[o + j] = getattr(p, a)
        s.lastAge = age

    def run(self, ent, limbSwing=0.0, limbAmount=0.0, age=0.0, head_yaw=0.0, head_pitch=0.0):
        spec = self.spec
        parts = {'root': spec.root}
        for p in spec.root.walk():
            p.reset()
            if p is not spec.root:
                parts[p.jname] = p
        spec.root.visible = not ent.isInvisible()
        if not spec.root.visible:
            return
        partial = age - math.floor(age)
        pose = ent.pose()
        pt = max(0.0, ent.poseTime(partial))
        glitch = min(1.0, max(0.0, ent.glitch(partial)))
        st = self.state(ent, pose, age)
        self.fn(parts, ent, limbSwing, limbAmount, age, head_yaw, head_pitch, pose, pt, glitch, st, st.prev,
                partial, lambda b: self.blend(st, pt, b, age))

    def timeline(self, events, t_end, limb=None, head=None, glitch=None, ident=7, step=1.0):
        """Simulates an entity: events = [(tick, pose), ...] (sorted). Runs every tick (so pose blends and lagged
        values evolve as in game) and leaves the parts posed at t_end. limb(t) -> (limbSwing, amount),
        head(t) -> (yaw deg, pitch deg), glitch(t) -> 0..1."""
        self.states.pop(ident, None)
        t = events[0][0]
        while True:
            tt = min(t, t_end)
            pose, start = events[0][1], events[0][0]
            for (et, ep) in events:
                if et <= tt:
                    pose, start = ep, et
            ls, la = limb(tt) if limb else (0.0, 0.0)
            hy, hp = head(tt) if head else (0.0, 0.0)
            g = glitch(tt) if glitch else 0.0
            self.run(FakeEntity(pose, tt - start, g, ident=ident), ls, la, tt, hy, hp)
            if tt >= t_end:
                break
            t += step


def entity_world(yaw_deg=0.0, pos=(0, 0, 0)):
    """LivingEntityRenderer: Y(180 - bodyYaw) . scale(-1,-1,1) . translate(0,-1.501,0)."""
    return (R.affine(None, pos) @ R.affine(R.rot_y(math.radians(180.0 - yaw_deg))) @ R.scale_m(-1, -1, 1)
            @ R.affine(None, [0, -1.501, 0]))


class Spec:
    """Base class for the spirit specs (subclass sets id, java_name, poses, doc, anim, helpers, build(), paint())."""
    id = 'x'
    java_name = 'XModel'
    module = 'ent_x'
    translucent = True
    plain_entity = False
    px = 2               # texels per model pixel
    tex_w = 64           # UV layout size in model pixels
    tex_h = None
    height = 1.8
    poses = []
    doc = ''
    anim = ''
    post = ''
    helpers = ''
    prologue = None      # Java run before anim (default PROLOGUE: ageInTicks as the clock, glitch = missing frames)

    consts = {}

    def __init__(self):
        self.root = Part('root')
        self.build()
        self.tex_w, self.tex_h = pack(self.root, self.tex_w, self.tex_h)
        self.anim = self.subst(type(self).anim)
        self.post = self.subst(type(self).post)

    def subst(self, code):
        """$NAME$ placeholders -> the solved constants (IK angles etc.) as Java float literals."""
        import re
        def rep(mt):
            k = mt.group(1)
            if k not in self.consts:
                raise KeyError(f'{self.id}: constant {k} not solved (run its solve_ik)')
            return f'{self.consts[k]:.3f}F'
        return re.sub(r'\$([A-Z0-9_]+)\$', rep, code)

    def build(self):
        raise NotImplementedError

    def part(self, name):
        for p in self.root.walk():
            if p.name == name:
                return p
        raise KeyError(name)

    def cube(self, tag):
        for p in self.root.walk():
            for c in p.cubes:
                if c.tag == tag:
                    return c
        raise KeyError(tag)

    def cubes(self):
        return [c for p in self.root.walk() for c in p.cubes]
