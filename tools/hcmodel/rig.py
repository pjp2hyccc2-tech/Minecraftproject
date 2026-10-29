"""Pose based authoring helpers for character rigs (used by tools/models/hunter.py).

A ``Pose`` is an ABSOLUTE pose: bone -> rotation (deg, x/y/z) and bone -> position offset (px) on top of the rest
pose. ``key(anim, t, pose)`` writes one keyframe for every bone of the rig so that every animation is fully
defined (the mod plays character actions on their own, not layered on top of an idle like the monsters).

Also provides forward kinematics of a posed model (``fk``) and an analytic two-bone IK (``ik_arm``) so that
the second hand of two-handed weapons can be placed on the grip automatically, and ``euler_zyx`` to turn
rotation matrices into Minecraft bone angles (ModelPart applies Rz * Ry * Rx).
"""
import math

import numpy as np

from .model import part_matrix, rot_x, rot_y, rot_z


# ----------------------------------------------------------------------------- pose
class Pose:
    def __init__(self, base=None):
        self.rot = dict(base.rot) if base else {}
        self.pos = dict(base.pos) if base else {}

    def copy(self):
        return Pose(self)

    def r(self, bone, x=0.0, y=0.0, z=0.0):
        self.rot[bone] = (float(x), float(y), float(z))
        return self

    def p(self, bone, x=0.0, y=0.0, z=0.0):
        self.pos[bone] = (float(x), float(y), float(z))
        return self

    def add_r(self, bone, x=0.0, y=0.0, z=0.0):
        cx, cy, cz = self.rot.get(bone, (0, 0, 0))
        self.rot[bone] = (cx + x, cy + y, cz + z)
        return self

    def add_p(self, bone, x=0.0, y=0.0, z=0.0):
        cx, cy, cz = self.pos.get(bone, (0, 0, 0))
        self.pos[bone] = (cx + x, cy + y, cz + z)
        return self

    def get_r(self, bone):
        return self.rot.get(bone, (0.0, 0.0, 0.0))

    def get_p(self, bone):
        return self.pos.get(bone, (0.0, 0.0, 0.0))

    def mix(self, other, f):
        """Linear blend towards `other` (f = 0 -> self, 1 -> other)."""
        out = Pose()
        bones = set(self.rot) | set(other.rot)
        for b in bones:
            a, c = self.get_r(b), other.get_r(b)
            out.rot[b] = tuple(a[i] + (c[i] - a[i]) * f for i in range(3))
        bones = set(self.pos) | set(other.pos)
        for b in bones:
            a, c = self.get_p(b), other.get_p(b)
            out.pos[b] = tuple(a[i] + (c[i] - a[i]) * f for i in range(3))
        return out

    def mirror(self, pairs):
        """Mirror left/right: pairs = [(bone_l, bone_r), ...]; y/z rotations and x positions flip."""
        out = Pose()
        swap = {}
        for a, b in pairs:
            swap[a] = b
            swap[b] = a
        for b, (x, y, z) in self.rot.items():
            out.rot[swap.get(b, b)] = (x, -y, -z)
        for b, (x, y, z) in self.pos.items():
            out.pos[swap.get(b, b)] = (-x, y, z)
        return out


def unwrap(prev, cur):
    """Shift `cur` by multiples of 360 so it is the closest representation to `prev`."""
    return cur + 360.0 * round((prev - cur) / 360.0)


# Two-bone limbs (upper, lower, end-effector or None) that ``key`` canonicalises: a limb orientation has two Euler
# representations that differ by a 180 degree twist about the limb axis (the IK solver picks one depending on the
# pole side). Both place the end effector identically but only one keeps the bone's front facing forward and
# interpolates sanely; ``key`` picks the one closest to the previous key (or to the rest pose for the first key).
LIMBS = []
_RY180 = None


def _ry180():
    global _RY180
    if _RY180 is None:
        _RY180 = rot_y(math.pi)[:3, :3]
    return _RY180


def _dist(a, b):
    return sum(abs(unwrap(b[i], a[i]) - b[i]) for i in range(3))


def canonical_limbs(pose, prev_rot):
    """Returns a copy of `pose` where every limb in LIMBS uses the Euler representation closest to prev_rot
    (bone -> last keyed rotation; missing = rest)."""
    out = pose.copy()
    for upper, lower, end in LIMBS:
        if upper not in pose.rot and lower not in pose.rot:
            continue
        ru, rl = pose.get_r(upper), pose.get_r(lower)
        mu, ml = matrix_zyx(*ru), matrix_zyx(*rl)
        alt_u = euler_zyx(mu @ _ry180())
        alt_l = euler_zyx(_ry180() @ ml @ _ry180())
        pu, pl = prev_rot.get(upper, (0.0, 0.0, 0.0)), prev_rot.get(lower, (0.0, 0.0, 0.0))
        if _dist(alt_u, pu) + _dist(alt_l, pl) < _dist(ru, pu) + _dist(rl, pl) - 1e-6:
            out.rot[upper] = alt_u
            out.rot[lower] = alt_l
            if end is not None:
                out.rot[end] = euler_zyx(_ry180() @ matrix_zyx(*pose.get_r(end)))
    return out


def key(anim, t, pose, bones, interp='c'):
    """Keys every bone in `bones` at time t with the pose values (missing bones = rest). Angles are unwrapped
    against the previous key of that bone so Catmull-Rom never spins the long way round."""
    prev_rot = {}
    for b in bones:
        prev = anim.channels.get(b, {}).get('rotation')
        if prev:
            prev_rot[b] = prev[-1][1]
    if LIMBS:
        pose = canonical_limbs(pose, prev_rot)
    for b in bones:
        r = pose.get_r(b)
        if b in prev_rot:
            pr = prev_rot[b]
            r = tuple(unwrap(pr[i], r[i]) for i in range(3))
        anim.rot(b, [(t, r, interp)])
        p = pose.get_p(b)
        if any(p) or (b in anim.channels and 'position' in anim.channels[b]):
            anim.pos(b, [(t, p, interp)])
    return anim


# ----------------------------------------------------------------------------- forward kinematics
def fk(model, pose, bone_name):
    """4x4 matrix (model pixel space) of a bone under the given absolute pose."""
    b = model.bones[bone_name]
    chain = []
    while b is not None:
        chain.append(b)
        b = b.parent
    m = np.eye(4)
    for bn in reversed(chain):
        px, py, pz = bn.pivot
        ox, oy, oz = pose.get_p(bn.name)
        rx, ry, rz = bn.rot
        ax, ay, az = pose.get_r(bn.name)
        m = m @ part_matrix(px + ox, py + oy, pz + oz, math.radians(rx + ax), math.radians(ry + ay), math.radians(rz + az))
    return m


def xform(m, p):
    v = m @ np.array([p[0], p[1], p[2], 1.0])
    return v[:3]


def euler_zyx(R):
    """Angles (deg) a, b, c such that R = Rz(c) Ry(b) Rx(a) (Minecraft ModelPart order)."""
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    b = math.asin(sy)
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(R[2, 1], R[2, 2])
        c = math.atan2(R[1, 0], R[0, 0])
    else:
        a = math.atan2(-R[1, 2], R[1, 1])
        c = 0.0
    return (math.degrees(a), math.degrees(b), math.degrees(c))


def matrix_zyx(a, b, c):
    return (rot_z(math.radians(c)) @ rot_y(math.radians(b)) @ rot_x(math.radians(a)))[:3, :3]


def normalize(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def rotate_about(v, axis, ang):
    """Rodrigues rotation of v about unit axis by ang (radians)."""
    axis = normalize(axis)
    return v * math.cos(ang) + np.cross(axis, v) * math.sin(ang) + axis * np.dot(axis, v) * (1 - math.cos(ang))


# ----------------------------------------------------------------------------- IK
def ik_two_bone(pose, model, upper, lower, l1, l2, target_world, pole_world, twist=0.0):
    """Analytic 2-bone IK. upper/lower: bone names (lower is a child of upper, pivot (0, l1, 0)).
    Both bones must have zero rest rotation and the local +Y axis runs down the limb.
    target_world / pole_world: numpy points in model space (px). pole = where the joint (elbow/knee) bends to.
    twist: extra rotation (deg) of the limb plane about the shoulder->target axis."""
    up = model.bones[upper]
    parent_m = fk(model, pose, up.parent.name) if up.parent else np.eye(4)
    inv = np.linalg.inv(parent_m)
    S = np.array(up.pivot, float) + np.array(pose.get_p(upper), float)
    T = xform(inv, target_world)
    P = xform(inv, pole_world)
    d = T - S
    L = np.linalg.norm(d)
    L = max(abs(l1 - l2) + 0.05, min(l1 + l2 - 0.02, L))
    dn = normalize(d)
    # plane normal (hinge axis)
    pv = P - S
    pv = pv - dn * np.dot(pv, dn)
    if np.linalg.norm(pv) < 1e-6:
        pv = np.array([1.0, 0.0, 0.0])
        pv = pv - dn * np.dot(pv, dn)
    pvn = normalize(pv)
    if twist:
        pvn = normalize(rotate_about(pvn, dn, math.radians(twist)))
    n = normalize(np.cross(dn, pvn))
    cos_a = (l1 * l1 + L * L - l2 * l2) / (2 * l1 * L)
    cos_a = max(-1.0, min(1.0, cos_a))
    a = math.acos(cos_a)
    # upper arm direction: rotate dn towards the pole side by angle a
    u = normalize(dn * math.cos(a) + pvn * math.sin(a))
    E = S + u * l1
    v = normalize(T - E)
    x_axis = n
    y_axis = u
    z_axis = np.cross(x_axis, y_axis)
    R1 = np.stack([x_axis, y_axis, z_axis], axis=1)
    rot1 = euler_zyx(R1)
    v_local = R1.T @ v
    phi = math.degrees(math.atan2(v_local[2], v_local[1]))
    pose.r(upper, *rot1)
    pose.r(lower, phi, 0, 0)
    return pose


def look_at(pose, model, bone, target_world, forward=(0, 0, -1), max_deg=60):
    """Rotates `bone` (relative to its parent) so that its local `forward` points at the target (clamped)."""
    b = model.bones[bone]
    parent_m = fk(model, pose, b.parent.name) if b.parent else np.eye(4)
    inv = np.linalg.inv(parent_m)
    T = xform(inv, target_world) - (np.array(b.pivot, float) + np.array(pose.get_p(bone), float))
    T = normalize(T)
    f = normalize(np.array(forward, float))
    yaw = math.degrees(math.atan2(-T[0], -T[2])) if abs(f[2]) > 0.5 else 0.0
    pitch = math.degrees(math.asin(max(-1, min(1, T[1]))))
    yaw = max(-max_deg, min(max_deg, yaw))
    pitch = max(-max_deg, min(max_deg, pitch))
    pose.r(bone, pitch, yaw, 0)
    return pose
