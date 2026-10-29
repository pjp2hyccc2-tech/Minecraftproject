"""Hunter combat animation library + action timelines (imported by hunter.py).

Authoring model
---------------
Every animation is a list of absolute keyframe poses (see hcmodel/rig.py). Weapon attacks are described by
"weapon keys": where the main hand is (model px), where the blade points (unit vector) and what the torso /
legs do; the arms are then solved with IK (second hand on the grip for two-handed weapons, wrist bone aims
the blade). Model space: +X = entity left, +Y = down (0 = shoulders, 12 = waist, 24 = ground), -Z = front.

Action timelines (ACTIONS) are exported to hunter_actions.json and drive the server side combat
(gear/weapon/action): tick counts, hit frames + hit shapes, cancel windows, root motion, i-frames, sounds.
Animation length == ticks / 20 (the client stretches the animation to the action length anyway).
"""
import math

import numpy as np

from hcmodel import rig as R
from hcmodel.rig import Pose, key
import hunter as H

ALL = list(H.BONES)
R.LIMBS = [('arm_r', 'arm_r_lower', 'hand_r'), ('arm_l', 'arm_l_lower', 'hand_l'), ('leg_r', 'leg_r_lower', None), ('leg_l', 'leg_l_lower', None)]
TICK = 0.05
ACTIONS = {}
MODEL = None


def weapon_for(anim_name):
    for short, cls in H.SHORT.items():
        if anim_name.startswith(short + '_') or anim_name.endswith('_' + short):
            return cls
    if anim_name.startswith('bow') or anim_name.endswith('bow'):
        return 'bow'
    return None


# ------------------------------------------------------------------------------------------------ vector helpers
def D(yaw, pitch):
    """Unit direction: yaw 0 = forward (-Z), +90 = entity left (+X); pitch +90 = up (-Y)."""
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([math.sin(y) * math.cos(p), -math.sin(p), -math.cos(y) * math.cos(p)])


def V(x, y, z):
    return np.array([float(x), float(y), float(z)])


def lerp(a, b, f):
    return a + (b - a) * f


# ------------------------------------------------------------------------------------------------ arm / weapon posing
def arm_ik(pose, side, target, pole=None, twist=0.0):
    sg = 1.0 if side == 'l' else -1.0
    if pole is None:
        pole = target + V(sg * 10, 6, 10)
    R.ik_two_bone(pose, MODEL, 'arm_' + side, 'arm_%s_lower' % side, H.L_UPPER_ARM, H.L_LOWER_ARM, target, pole, twist)
    return pose


def leg_ik(pose, side, target, pole=None):
    if pole is None:
        pole = target + V(0, -8, -12)
    R.ik_two_bone(pose, MODEL, 'leg_' + side, 'leg_%s_lower' % side, H.L_UPPER_LEG, H.L_LOWER_LEG, target, pole)
    return pose


def hand_point(pose, side):
    return R.xform(R.fk(MODEL, pose, 'hand_' + side), (0, 0, 0))


_BLADE_LOCAL = {}


def blade_local(cls, left=False):
    """Blade direction (unit) and grip point in the HAND bone frame (px)."""
    k = (cls, left)
    if k not in _BLADE_LOCAL:
        g, t = H.weapon_points_px(cls, left)
        d = t - g
        _BLADE_LOCAL[k] = (d / max(1e-6, np.linalg.norm(d)), g, t)
    return _BLADE_LOCAL[k]


def aim(pose, side, cls, blade_dir, roll=0.0, face=None):
    """Rotates the wrist (hand bone) so the weapon held in that hand points along blade_dir (model space).
    roll: rotation (deg) about the blade axis; face: optional direction the sprite's flat face should turn to."""
    left = side == 'l'
    bl, g, t = blade_local(cls, left)
    low = R.fk(MODEL, pose, 'arm_%s_lower' % side)
    hand_rest = low @ H.part_matrix(0, 6, 0, 0, 0, 0)
    Rp = hand_rest[:3, :3]
    target_local = R.normalize(Rp.T @ R.normalize(np.array(blade_dir, float)))
    # rotation taking bl -> target_local (minimal), then roll about target
    v = np.cross(bl, target_local)
    s = np.linalg.norm(v)
    c = float(np.dot(bl, target_local))
    if s < 1e-6:
        Rm = np.eye(3) if c > 0 else R.matrix_zyx(180, 0, 0)
    else:
        vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
        Rm = np.eye(3) + vx + vx @ vx * ((1 - c) / (s * s))
    if face is not None:
        # sprite normal in hand frame is roughly +Z of the item matrix: choose roll so it faces `face`
        m = H.item_matrix(cls, left)
        n_local = R.normalize(m[:3, :3] @ np.array([0, 0, 1.0]))
        n_now = Rm @ n_local
        f_local = R.normalize(Rp.T @ np.array(face, float))
        f_perp = f_local - target_local * np.dot(f_local, target_local)
        n_perp = n_now - target_local * np.dot(n_now, target_local)
        if np.linalg.norm(f_perp) > 1e-4 and np.linalg.norm(n_perp) > 1e-4:
            f_perp, n_perp = R.normalize(f_perp), R.normalize(n_perp)
            ang = math.atan2(np.dot(np.cross(n_perp, f_perp), target_local), np.dot(n_perp, f_perp))
            # flat sprite: either side is fine -> pick the smaller rotation
            if ang > math.pi / 2:
                ang -= math.pi
            if ang < -math.pi / 2:
                ang += math.pi
            roll += math.degrees(ang)
    if roll:
        ax = target_local
        a = math.radians(roll)
        K = np.array([[0, -ax[2], ax[1]], [ax[2], 0, -ax[0]], [-ax[1], ax[0], 0]])
        Rr = np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * (K @ K)
        Rm = Rr @ Rm
    pose.r('hand_' + side, *R.euler_zyx(Rm))
    return pose


def weapon_point(pose, side, cls, frac):
    """Model-space point on the weapon axis: frac 0 = grip, 1 = tip."""
    bl, g, t = blade_local(cls, side == 'l')
    hm = R.fk(MODEL, pose, 'hand_' + side)
    return R.xform(hm, g + (t - g) * frac)


def hand2_target(pose, cls):
    """Where the off hand should hold a two-handed weapon (model space)."""
    bl, g, t = blade_local(cls)
    w = H.WEAPONS[cls]
    hm = R.fk(MODEL, pose, 'hand_r')
    return R.xform(hm, g + bl * (w['hand2'] * w['scale'] * 16.0))


def two_hand(pose, cls, pole=None, twist=0.0):
    """Left hand on the grip of the weapon in the right hand (after the right arm is posed)."""
    t = hand2_target(pose, cls)
    arm_ik(pose, 'l', t, pole, twist)
    # wrist: copy the right wrist orientation roughly (not rendered, keeps off-hand items sane)
    pose.r('hand_l', *pose.get_r('hand_r'))
    return pose


def wk(pose, cls, hand, blade, roll=0.0, face=None, pole=None, twist=0.0, two=True, off_hand=None, off_pole=None):
    """Weapon key: right hand at `hand` (px), weapon pointing along `blade`. two: left hand joins the grip.
    off_hand: explicit left hand target when not two-handed."""
    arm_ik(pose, 'r', V(*hand), pole, twist)
    aim(pose, 'r', cls, blade, roll, face)
    if two and H.WEAPONS[cls]['hand2'] > 0:
        two_hand(pose, cls, off_pole)
    elif off_hand is not None:
        arm_ik(pose, 'l', V(*off_hand), off_pole)
    return pose


# ------------------------------------------------------------------------------------------------ body helpers
def body(pose, lean=0.0, twist=0.0, tilt=0.0, hips_rot=(0, 0, 0), drop=0.0, head=(0, 0, 0), root=(0, 0, 0)):
    """Torso: lean (+ forward), twist (+ turns chest to the left), tilt (side). drop: root sink (px, + down)."""
    pose.r('chest', lean, twist, tilt)
    pose.r('hips', *hips_rot)
    pose.r('head', *head)
    pose.p('root', root[0], root[1] + drop, root[2])
    return pose


def legs(pose, r=(0, 0, 0), l=(0, 0, 0), rk=0.0, lk=0.0):
    """Explicit leg rotations + knee bends (deg)."""
    pose.r('leg_r', *r)
    pose.r('leg_l', *l)
    pose.r('leg_r_lower', rk, 0, 0)
    pose.r('leg_l_lower', lk, 0, 0)
    return pose


def plant(pose, r=(-2.5, 1.0), l=(2.5, -1.0), y=24.0):
    """Feet planted on the ground at (x, z) with IK (hips/root already posed)."""
    leg_ik(pose, 'r', V(r[0], y, r[1]))
    leg_ik(pose, 'l', V(l[0], y, l[1]))
    return pose


HIPS_FOLLOW = 0.35      # pelvis yaw as a fraction of the chest twist (same direction)


def stance_base(drop=1.0, feet=((-3.0, 1.5), (3.0, -2.5)), lean=4, twist=0, head=(0, 0, 0)):
    p = Pose()
    head = (head[0], head[1] - twist * HIPS_FOLLOW, head[2])
    body(p, lean=lean, twist=twist, drop=drop, head=head, hips_rot=(0, twist * HIPS_FOLLOW, 0))
    plant(p, feet[0], feet[1])
    return p


def relax_arms(pose, swing=0.0, out=3.0):
    pose.r('arm_r', swing, 0, out)
    pose.r('arm_l', -swing, 0, -out)
    pose.r('arm_r_lower', -6, 0, 0)
    pose.r('arm_l_lower', -6, 0, 0)
    pose.r('hand_r', 0, 0, 0)
    pose.r('hand_l', 0, 0, 0)
    return pose


# ------------------------------------------------------------------------------------------------ keyframing
def anim(model, name, ticks, loop=False):
    return model.anim(name, ticks * TICK, loop)


def keys(a, frames, interp='c'):
    """frames: list of (tick, pose[, interp])."""
    for fr in frames:
        t, p = fr[0], fr[1]
        it = fr[2] if len(fr) > 2 else interp
        key(a, t * TICK, p, ALL, it)
    return a


def breathe(a, base, period_ticks, chest=1.2, head=1.0, arms=0.8, steps=8):
    """Loop: subtle breathing / sway added on top of a base pose."""
    n = steps
    for i in range(n + 1):
        t = period_ticks * i / n
        ph = 2 * math.pi * i / n
        p = base.copy()
        p.add_r('chest', chest * math.sin(ph), 0, 0)
        p.add_r('head', -head * math.sin(ph), 0, 0)
        p.add_r('arm_r', 0, 0, -arms * math.sin(ph))
        p.add_r('arm_l', 0, 0, arms * math.sin(ph))
        p.add_p('root', 0, 0.3 - 0.3 * math.cos(ph), 0)
        key(a, t * TICK, p, ALL)
    return a


# ------------------------------------------------------------------------------------------------ stances
STANCE = {}


def make_stances():
    # unarmed idle (vanilla-like, arms relaxed)
    p = Pose()
    body(p, lean=1.5, drop=0.0)
    legs(p, (0, 0, 1.0), (0, 0, -1.0), 2, 2)
    relax_arms(p, 0, 2.5)
    STANCE['idle'] = p

    # great sword: resting over the right shoulder, both hands on the long grip, wide stance
    p = stance_base(drop=1.5, feet=((-3.5, 2.0), (3.0, -3.0)), lean=5, twist=18, head=(0, -12, 0))
    wk(p, 'great_sword', (-6, 9, -3), D(-25, 60), face=V(1, 0, 0), pole=V(-10, 22, 8), off_pole=V(8, 18, -2))
    STANCE['idle_gs'] = p

    # long sword: blade low at the right side pointing forward-down (iai-like ready pose)
    p = stance_base(drop=1.2, feet=((-3.0, 2.5), (3.5, -3.0)), lean=6, twist=22, head=(2, -16, 0))
    wk(p, 'long_sword', (-7, 13, -3), D(-30, -25), face=V(1, 0, 0), pole=V(-14, 14, 8), off_pole=V(4, 18, -4))
    STANCE['idle_ls'] = p

    # sword & shield: sword forward-up, shield arm raised in front
    p = stance_base(drop=1.0, feet=((-3.0, 2.0), (3.0, -3.5)), lean=5, twist=10, head=(0, -8, 0))
    wk(p, 'sword_and_shield', (-6, 10, -6), D(-15, 35), face=V(1, 0, 0), two=False, off_hand=(5, 9, -7), off_pole=V(14, 12, 2))
    p.r('hand_l', 0, 0, 0)
    STANCE['idle_sns'] = p

    # dual blades: both blades held low and out, slight crouch
    p = stance_base(drop=1.8, feet=((-4.0, 2.0), (4.0, -2.0)), lean=10, twist=0, head=(-4, 0, 0))
    wk(p, 'dual_blades', (-8, 12, -4), D(-35, -20), face=V(1, 0, 0), two=False, pole=V(-16, 12, 8))
    arm_ik(p, 'l', V(8, 12, -4), V(16, 12, 8))
    aim(p, 'l', 'dual_blades', D(35, -20), face=V(1, 0, 0))
    STANCE['idle_db'] = p

    # dual blades demon mode: blades raised, aggressive forward lean
    p = stance_base(drop=2.2, feet=((-4.0, 3.0), (4.0, -4.0)), lean=16, twist=0, head=(-8, 0, 0))
    wk(p, 'dual_blades', (-9, 9, -6), D(-45, 10), face=V(1, 0, 0), two=False, pole=V(-18, 8, 8))
    arm_ik(p, 'l', V(9, 9, -6), V(18, 8, 8))
    aim(p, 'l', 'dual_blades', D(45, 10), face=V(1, 0, 0))
    STANCE['idle_db_demon'] = p

    # hammer: shouldered, the head up behind the right shoulder, both hands on the shaft in front of the chest
    p = stance_base(drop=1.5, feet=((-3.5, 2.5), (3.0, -2.5)), lean=4, twist=16, head=(0, -10, 0))
    wk(p, 'hammer', (-6, 8, -3), D(-30, 62), face=V(1, 0, 0), pole=V(-12, 20, 8), off_pole=V(8, 16, -2))
    STANCE['idle_hm'] = p

    # hunting horn: held like a staff, bell up over the right shoulder
    p = stance_base(drop=1.2, feet=((-3.5, 2.0), (3.0, -3.0)), lean=4, twist=12, head=(0, -8, 0))
    wk(p, 'hunting_horn', (-6, 11, -1), D(-25, 70), face=V(1, 0, 0), pole=V(-14, 12, 8), off_pole=V(6, 14, -6))
    STANCE['idle_hh'] = p

    # lance: couched forward at hip height, shield up on the left
    p = stance_base(drop=1.5, feet=((-3.5, 3.0), (3.0, -3.5)), lean=8, twist=14, head=(0, -10, 0))
    wk(p, 'lance', (-6, 13, -4), D(-6, 4), face=V(1, 0, 0), two=False, off_hand=(5, 8, -7), off_pole=V(14, 10, 2), pole=V(-14, 16, 8))
    p.r('hand_l', 0, 0, 0)
    STANCE['idle_ln'] = p

    # bow: held in the right hand pointing down-forward, left hand free
    p = stance_base(drop=0.8, feet=((-3.0, 2.0), (3.0, -2.5)), lean=3, twist=8, head=(0, -6, 0))
    wk(p, 'bow', (-6, 13, -4), D(0, -60), face=V(1, 0, 0), two=False, off_hand=(6, 13, -2), pole=V(-14, 16, 8))
    STANCE['idle_bow'] = p
    return STANCE


def make_loops(m):
    """Idle loops from the stances + walk/run cycles."""
    for name, base in STANCE.items():
        a = anim(m, name, 60, loop=True)
        breathe(a, base, 60, chest=1.0 if name == 'idle' else 1.5, arms=0.8 if name == 'idle' else 0.4)

    # walk cycle (legs/hips/head; arms swing only in the unarmed version)
    for armed in (False, True):
        for run in (False, True):
            name = ('run' if run else 'walk') + ('_armed' if armed else '')
            a = anim(m, name, 20, loop=True)
            n = 12
            amp = 38 if run else 26
            knee = 55 if run else 35
            for i in range(n + 1):
                ph = 2 * math.pi * i / n
                p = Pose()
                s = math.sin(ph)
                c = math.cos(ph)
                # legs: thigh swings, knee bends when the leg is behind / lifting
                def kb(x):
                    return max(0.0, math.sin(x)) * knee
                p.r('leg_r', amp * s, 0, 0)
                p.r('leg_l', -amp * s, 0, 0)
                p.r('leg_r_lower', kb(ph - 2.0) * (1 if True else 0), 0, 0)
                p.r('leg_l_lower', kb(ph + math.pi - 2.0), 0, 0)
                p.p('root', 0, (1.6 if run else 0.8) * (0.5 - 0.5 * math.cos(2 * ph)), 0)
                p.r('hips', (10 if run else 3), 0, -3.5 * s)
                p.r('chest', (6 if run else 2), 7 * s, 2 * s)
                p.r('head', -(8 if run else 3), -5 * s, 0)
                if not armed:
                    p.r('arm_r', -amp * 0.9 * s, 0, 3)
                    p.r('arm_l', amp * 0.9 * s, 0, -3)
                    p.r('arm_r_lower', -14 + 14 * max(0, -s), 0, 0)
                    p.r('arm_l_lower', -14 + 14 * max(0, s), 0, 0)
                key(a, 20 * TICK * i / n, p, ['leg_r', 'leg_l', 'leg_r_lower', 'leg_l_lower', 'root', 'hips', 'chest', 'head']
                    + ([] if armed else ['arm_r', 'arm_l', 'arm_r_lower', 'arm_l_lower']))

    # crouch: additive delta (applied on top of anything)
    a = anim(m, 'crouch', 2, loop=True)
    p = Pose()
    p.p('root', 0, 3.0, 0)
    p.r('hips', -6, 0, 0)
    p.r('chest', 24, 0, 0)
    p.r('head', -18, 0, 0)
    p.r('leg_r', -30, 0, 0)
    p.r('leg_l', -30, 0, 0)
    p.r('leg_r_lower', 30, 0, 0)
    p.r('leg_l_lower', 30, 0, 0)
    bones = ['root', 'hips', 'chest', 'head', 'leg_r', 'leg_l', 'leg_r_lower', 'leg_l_lower']
    key(a, 0, p, bones)
    key(a, 2 * TICK, p, bones)


# ------------------------------------------------------------------------------------------------ attack builder
class Attack:
    """Builds an attack animation from weapon keys and registers its action timeline."""

    def __init__(self, m, name, cls, ticks, two=True):
        self.m = m
        self.name = name
        self.cls = cls
        self.ticks = ticks
        self.two = two
        self.frames = []
        special = 0 if ('_combo' in name or name in ('sns_guard_slash', 'ln_poke', 'bow_kick')) else 1
        self.action = {'ticks': ticks, 'anim': name, 'hits': [], 'move': [], 'sounds': [], 'cancel': ticks,
                       'lock': True, 'iframes': [0, 0], 'trail': [0, 0], 'stamina': 0, 'next': None, 'tags': {'special': special}}
        ACTIONS[name] = self.action

    def key(self, tick, hand, blade, lean=0, twist=0, tilt=0, drop=1.0, head=None, feet=None, roll=0.0, face=None,
            pole=None, off_hand=None, off_pole=None, root=(0, 0, 0), interp='c', hips=None, legs_=None, twist_arm=0.0, two=None):
        p = Pose()
        # the pelvis turns WITH the chest (a swing is driven from the hips); the head stays on the target
        hd = head if head is not None else (lean * -0.5, -twist * 1.0, 0)
        if head is not None and hips is None:
            hd = (head[0], head[1] - twist * HIPS_FOLLOW, head[2])
        body(p, lean=lean, twist=twist, tilt=tilt, drop=drop, head=hd, root=root,
             hips_rot=hips if hips is not None else (lean * 0.3, twist * HIPS_FOLLOW, tilt * 0.3))
        if legs_ is not None:
            legs(p, *legs_)
        else:
            ft = feet or ((-3.5, 2.0), (3.5, -3.0))
            plant(p, ft[0], ft[1])
        face = face if face is not None else V(1, 0, 0)
        two_ = self.two if two is None else two
        wk(p, self.cls, hand, blade, roll, face, pole, twist_arm, two_, off_hand, off_pole)
        self.frames.append((tick, p, interp))
        return p

    def pose(self, tick, p, interp='c'):
        self.frames.append((tick, p, interp))
        return p

    def hit(self, tick, mv, range_=3.6, cone=100, kind=None, sharp=1, ko=(0, 0), elem=1.0, status=1.0, area=None,
            bounce=True, use_sharp=True, hitstop=2, shake=0.0, sound=None, fx='sweep', forward=1.4, up=1.0):
        h = {'t': tick, 'mv': mv, 'range': range_, 'cone': cone, 'sharp': sharp, 'ko': list(ko), 'elem': elem,
             'status': status, 'bounce': bounce, 'use_sharp': use_sharp, 'hitstop': hitstop, 'shake': shake,
             'fx': fx, 'fwd': forward, 'up': up}
        if kind:
            h['kind'] = kind
        if area is not None:
            h['area'] = area
        if sound:
            h['sound'] = sound
        self.action['hits'].append(h)
        return self

    def move(self, t0, t1, speed, up=0.0):
        self.action['move'].append([t0, t1, speed, up])
        return self

    def sound(self, tick, name, vol=1.0, pitch=1.0):
        self.action['sounds'].append([tick, name, vol, pitch])
        return self

    def cancel(self, tick):
        self.action['cancel'] = tick
        return self

    def next(self, name):
        self.action['next'] = name
        return self

    def trail(self, t0, t1):
        self.action['trail'] = [t0, t1]
        return self

    def iframes(self, t0, t1):
        self.action['iframes'] = [t0, t1]
        return self

    def lock(self, v):
        self.action['lock'] = v
        return self

    def stamina(self, v):
        self.action['stamina'] = v
        return self

    def tag(self, k, v):
        self.action['tags'][k] = v
        return self

    def build(self, loop=False):
        a = anim(self.m, self.name, self.ticks, loop)
        keys(a, self.frames)
        return a


def swing_sound(cls):
    return 'weapon.swing_heavy' if cls in ('great_sword', 'hammer', 'hunting_horn') else 'weapon.swing_light'


# ------------------------------------------------------------------------------------------------ great sword
def gs(m):
    cls = 'great_sword'
    S = STANCE['idle_gs']
    # combo 1: overhead slash. anticipation: weapon raised high behind, then crashes down in front
    A = Attack(m, 'gs_combo1', cls, 30)
    A.pose(0, S)
    A.key(6, (-5, -2, 2), D(-10, 80), lean=-10, twist=26, drop=1.0, head=(-6, -10, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -4, 10), off_pole=V(8, 2, 8), face=V(1, 0, 0))
    A.key(10, (-4, -6, -1), D(0, 88), lean=-16, twist=20, drop=0.5, head=(-10, -6, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -8, 8), off_pole=V(8, -2, 8), face=V(1, 0, 0))
    A.key(13, (-1, 10, -10), D(0, -18), lean=30, twist=-6, drop=2.5, head=(-20, 4, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 12, 4), off_pole=V(10, 12, 2), face=V(1, 0, 0), interp='l')
    A.key(16, (0, 15, -8), D(0, -52), lean=36, twist=-10, drop=3.5, head=(-24, 6, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 16, 4), off_pole=V(10, 16, 2), face=V(1, 0, 0))
    A.key(24, (-3, 13, -6), D(-15, -40), lean=22, twist=4, drop=2.5, head=(-14, 0, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 16, 6), off_pole=V(8, 16, 2), face=V(1, 0, 0))
    A.pose(30, S)
    A.hit(13, 0.48, 3.9, 110, sharp=1, hitstop=3, shake=0.25).move(9, 13, 0.07).cancel(19).next('gs_combo2').trail(9, 17)
    A.sound(8, 'weapon.swing_heavy', 1.0, 0.85)
    A.build()

    # combo 2: wide horizontal slash from right to left
    A = Attack(m, 'gs_combo2', cls, 28)
    A.pose(0, A.frames[0][1] if False else S)
    A.key(5, (-9, 11, 5), D(-120, 10), lean=6, twist=38, drop=1.5, head=(0, -20, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 12, 10), off_pole=V(4, 16, 6), face=V(0, 1, 0))
    A.key(8, (-10, 11, 4), D(-140, 12), lean=4, twist=44, drop=1.5, head=(0, -22, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 12, 10), off_pole=V(4, 16, 6), face=V(0, 1, 0))
    A.key(11, (-2, 11, -9), D(-20, 4), lean=14, twist=-10, drop=2.0, head=(-6, 6, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 4), off_pole=V(8, 14, 2), face=V(0, 1, 0), interp='l')
    A.key(14, (5, 11, -6), D(55, 0), lean=14, twist=-32, drop=2.5, head=(-6, 16, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-10, 14, -2), off_pole=V(12, 12, 0), face=V(0, 1, 0))
    A.key(21, (3, 12, -4), D(40, -10), lean=12, twist=-20, drop=2.0, head=(-4, 10, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-10, 14, 0), off_pole=V(12, 12, 0), face=V(0, 1, 0))
    A.pose(28, S)
    A.hit(11, 0.36, 3.9, 150, sharp=1, hitstop=2, shake=0.15).move(8, 12, 0.05).cancel(18).next('gs_combo3').trail(8, 15)
    A.sound(7, 'weapon.swing_heavy', 1.0, 0.95)
    A.build()

    # combo 3: rising slash from low left to high right, hop forward
    A = Attack(m, 'gs_combo3', cls, 32)
    A.pose(0, S)
    A.key(6, (6, 17, 2), D(70, -50), lean=22, twist=-30, drop=3.0, head=(-10, 14, 0), feet=((-3.5, 2.0), (4.0, -4.0)),
          pole=V(14, 18, 4), off_pole=V(-4, 20, 6), face=V(0, 1, 0))
    A.key(9, (7, 18, 3), D(80, -60), lean=26, twist=-36, drop=3.5, head=(-10, 16, 0), feet=((-3.5, 2.0), (4.0, -4.0)),
          pole=V(14, 18, 4), off_pole=V(-4, 20, 6), face=V(0, 1, 0))
    A.key(12, (-2, 9, -9), D(-10, 20), lean=4, twist=6, drop=1.5, head=(-2, -4, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 10, 4), off_pole=V(8, 12, 0), face=V(0, 1, 0), interp='l')
    A.key(15, (-7, 1, -4), D(-40, 75), lean=-12, twist=28, drop=0.0, head=(4, -14, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 0, 6), off_pole=V(6, 4, 4), face=V(1, 0, 0))
    A.key(22, (-6, 4, -2), D(-30, 70), lean=-6, twist=22, drop=0.8, head=(2, -12, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, 2, 8), off_pole=V(6, 6, 4), face=V(1, 0, 0))
    A.pose(32, S)
    A.hit(12, 0.52, 3.9, 100, sharp=1, hitstop=3, shake=0.3).move(9, 13, 0.08, 0.05).cancel(22).trail(9, 17)
    A.sound(8, 'weapon.swing_heavy', 1.0, 0.8)
    A.build()

    # charge hold (loop): sword raised high over the right shoulder, legs braced
    a = anim(m, 'charge_gs', 40, loop=True)
    p = Pose()
    body(p, lean=-8, twist=30, drop=2.0, head=(0, -14, 0), hips_rot=(0, -12, 0))
    plant(p, (-4.0, 3.5), (3.5, -5.0))
    wk(p, cls, (-6, -4, 3), D(-30, 70), face=V(1, 0, 0), pole=V(-18, -6, 10), off_pole=V(6, -2, 10))
    breathe(a, p, 40, chest=0.8, head=0.5, arms=0.6)

    # charged slash (shared by the 3 levels): huge overhead crash with a step in
    A = Attack(m, 'gs_charge_slash', cls, 26)
    base = Pose()
    body(base, lean=-8, twist=30, drop=2.0, head=(0, -14, 0), hips_rot=(0, -12, 0))
    plant(base, (-4.0, 3.5), (3.5, -5.0))
    wk(base, cls, (-6, -4, 3), D(-30, 70), face=V(1, 0, 0), pole=V(-18, -6, 10), off_pole=V(6, -2, 10))
    A.pose(0, base)
    A.key(3, (-3, -8, -2), D(0, 90), lean=-22, twist=18, drop=0.0, head=(-8, -8, 0), feet=((-4.0, 3.5), (3.5, -5.0)),
          pole=V(-16, -10, 8), off_pole=V(8, -4, 8), face=V(1, 0, 0))
    A.key(7, (0, 10, -11), D(0, -8), lean=34, twist=-8, drop=3.0, head=(-24, 4, 0), feet=((-3.5, 2.0), (3.5, -7.0)),
          pole=V(-14, 10, 4), off_pole=V(10, 10, 2), face=V(1, 0, 0), interp='l')
    A.key(11, (0, 15, -9), D(0, -40), lean=40, twist=-10, drop=4.5, head=(-26, 6, 0), feet=((-3.5, 2.0), (3.5, -7.0)),
          pole=V(-14, 16, 4), off_pole=V(10, 16, 2), face=V(1, 0, 0))
    A.key(18, (-2, 13, -7), D(-10, -34), lean=28, twist=0, drop=3.0, head=(-18, 2, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 16, 6), off_pole=V(8, 16, 2), face=V(1, 0, 0))
    A.pose(26, S)
    A.move(4, 8, 0.12).trail(3, 12).cancel(20)
    A.sound(2, 'weapon.swing_heavy', 1.0, 0.7)
    A.build()
    for lv, mult in ((0, 0.55), (1, 0.75), (2, 1.0), (3, 1.35)):
        name = 'gs_charge%d' % lv
        ACTIONS[name] = dict(ACTIONS['gs_charge_slash'])
        ACTIONS[name]['hits'] = []
        ACTIONS[name]['anim'] = 'gs_charge_slash'
        ACTIONS[name]['ticks'] = 26 + lv * 2
        h = {'t': 7, 'mv': round(1.10 * mult, 4), 'range': 4.0 + 0.2 * lv, 'cone': 110 + 10 * lv, 'sharp': 2, 'ko': [0, 0],
             'elem': 1.0 + 0.15 * lv, 'status': 1.0 + 0.15 * lv, 'bounce': True, 'use_sharp': True, 'hitstop': 3 + lv,
             'shake': 0.3 + 0.25 * lv, 'fx': 'slam', 'fwd': 2.2, 'up': 0.6, 'sound': 'weapon.slam'}
        ACTIONS[name]['hits'] = [h]
        ACTIONS[name]['tags'] = {'charge': lv}

    # guard hold: blade held flat in front like a wall
    a = anim(m, 'guard_gs', 40, loop=True)
    p = Pose()
    body(p, lean=10, twist=14, drop=2.5, head=(-6, -4, 0))
    plant(p, (-4.0, 3.0), (3.5, -5.0))
    wk(p, cls, (-3, 6, -8), D(70, 10), face=V(0, 0, -1), pole=V(-14, 8, 4), off_pole=V(8, 8, 0))
    breathe(a, p, 40, chest=0.6, head=0.4, arms=0.3)


# ------------------------------------------------------------------------------------------------ long sword
def ls(m):
    cls = 'long_sword'
    S = STANCE['idle_ls']
    # combo 1: rising diagonal slash from the low right guard
    A = Attack(m, 'ls_combo1', cls, 18)
    A.pose(0, S)
    A.key(4, (-8, 15, 1), D(-60, -40), lean=8, twist=30, drop=1.5, head=(0, -18, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 18, 8), off_pole=V(4, 20, 0), face=V(1, 0, 0))
    A.key(7, (-2, 8, -9), D(-5, 25), lean=16, twist=-8, drop=2.0, head=(-8, 4, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 10, 4), off_pole=V(8, 12, 0), face=V(1, 0, 0), interp='l')
    A.key(10, (4, 3, -7), D(50, 60), lean=10, twist=-26, drop=1.5, head=(-4, 12, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-8, 6, -2), off_pole=V(12, 6, 0), face=V(1, 0, 0))
    A.key(14, (2, 6, -6), D(30, 50), lean=8, twist=-16, drop=1.5, head=(-2, 8, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-10, 8, 0), off_pole=V(12, 8, 0), face=V(1, 0, 0))
    A.pose(18, S)
    A.hit(7, 0.26, 3.8, 110, hitstop=2).move(5, 8, 0.07).cancel(11).next('ls_combo2').trail(5, 11)
    A.sound(4, 'weapon.swing_light', 1.0, 0.95)
    A.build()
    # combo 2: thrust
    A = Attack(m, 'ls_combo2', cls, 16)
    A.pose(0, S)
    A.key(4, (-8, 10, 3), D(-10, 5), lean=-4, twist=34, drop=1.0, head=(0, -16, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, 14, 10), off_pole=V(2, 16, 4), face=V(0, 1, 0))
    A.key(7, (-3, 9, -12), D(0, 2), lean=22, twist=-14, drop=2.5, head=(-10, 6, 0), feet=((-3.5, 1.0), (3.5, -7.0)),
          pole=V(-12, 12, -4), off_pole=V(8, 14, -2), face=V(0, 1, 0), interp='l')
    A.key(10, (-3, 9, -13), D(0, 0), lean=24, twist=-16, drop=2.5, head=(-10, 6, 0), feet=((-3.5, 1.0), (3.5, -7.0)),
          pole=V(-12, 12, -4), off_pole=V(8, 14, -2), face=V(0, 1, 0))
    A.pose(16, S)
    A.hit(7, 0.22, 4.2, 50, hitstop=2).move(5, 8, 0.09).cancel(10).next('ls_combo3').trail(5, 9)
    A.sound(4, 'weapon.swing_light', 0.9, 1.1)
    A.build()
    # combo 3: wide horizontal slash left -> right
    A = Attack(m, 'ls_combo3', cls, 18)
    A.pose(0, S)
    A.key(4, (6, 9, 0), D(80, 20), lean=4, twist=-30, drop=1.5, head=(0, 16, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(10, 10, 6), off_pole=V(14, 10, 2), face=V(0, 1, 0))
    A.key(8, (-2, 9, -9), D(-10, 4), lean=12, twist=10, drop=2.0, head=(-6, -4, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 10, 4), off_pole=V(8, 12, 0), face=V(0, 1, 0), interp='l')
    A.key(11, (-9, 10, 3), D(-110, -5), lean=8, twist=34, drop=2.0, head=(-2, -18, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-16, 12, 10), off_pole=V(4, 14, 6), face=V(0, 1, 0))
    A.pose(18, S)
    A.hit(8, 0.24, 3.8, 150, hitstop=2).move(6, 9, 0.06).cancel(12).next('ls_combo4').trail(6, 12)
    A.sound(5, 'weapon.swing_light', 1.0, 0.9)
    A.build()
    # combo 4: overhead finisher
    A = Attack(m, 'ls_combo4', cls, 22)
    A.pose(0, S)
    A.key(5, (-4, -4, 2), D(-10, 85), lean=-12, twist=16, drop=0.5, head=(-6, -8, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -6, 10), off_pole=V(8, -2, 8), face=V(1, 0, 0))
    A.key(9, (-1, 9, -10), D(0, -20), lean=30, twist=-6, drop=2.5, head=(-20, 4, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 12, 4), off_pole=V(10, 12, 2), face=V(1, 0, 0), interp='l')
    A.key(13, (0, 14, -8), D(0, -55), lean=34, twist=-8, drop=3.0, head=(-22, 4, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 16, 4), off_pole=V(10, 16, 2), face=V(1, 0, 0))
    A.pose(22, S)
    A.hit(9, 0.20, 3.8, 90, hitstop=2).move(6, 10, 0.07).cancel(14).trail(6, 13)
    A.sound(5, 'weapon.swing_light', 1.0, 0.85)
    A.build()
    # spirit blade I / II / roundslash
    A = Attack(m, 'ls_spirit1', cls, 24)
    A.pose(0, S)
    A.key(5, (-7, 1, 3), D(-30, 80), lean=-10, twist=30, drop=0.5, head=(-2, -16, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 0, 10), off_pole=V(6, 2, 8), face=V(1, 0, 0))
    A.key(10, (3, 10, -9), D(40, -30), lean=28, twist=-28, drop=3.0, head=(-16, 12, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-6, 12, 0), off_pole=V(12, 12, 0), face=V(1, 0, 0), interp='l')
    A.key(14, (5, 13, -6), D(60, -45), lean=24, twist=-32, drop=3.0, head=(-14, 14, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-4, 14, 0), off_pole=V(12, 14, 0), face=V(1, 0, 0))
    A.pose(24, S)
    A.hit(10, 0.30, 3.6, 90, hitstop=2, fx='spirit', sound='weapon.spirit_slash').move(7, 11, 0.08).cancel(16).next('ls_spirit2').trail(7, 14)
    A.build()
    A = Attack(m, 'ls_spirit2', cls, 24)
    A.pose(0, S)
    A.key(5, (7, 4, 1), D(70, 55), lean=-6, twist=-34, drop=0.5, head=(-2, 18, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(14, 4, 6), off_pole=V(16, 6, 2), face=V(1, 0, 0))
    A.key(10, (-4, 11, -9), D(-45, -30), lean=26, twist=30, drop=3.0, head=(-14, -14, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-16, 12, 4), off_pole=V(6, 14, 2), face=V(1, 0, 0), interp='l')
    A.key(14, (-7, 14, -4), D(-70, -45), lean=22, twist=34, drop=3.0, head=(-12, -16, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-16, 16, 6), off_pole=V(4, 16, 4), face=V(1, 0, 0))
    A.pose(24, S)
    A.hit(10, 0.34, 3.8, 130, hitstop=2, fx='spirit', sound='weapon.spirit_slash').move(7, 11, 0.08).cancel(16).next('ls_spirit3').trail(7, 14)
    A.build()
    # roundslash: full 360 spin with the blade horizontal
    A = Attack(m, 'ls_spirit3', cls, 30)
    A.pose(0, S)
    A.key(5, (-8, 9, 4), D(-130, 5), lean=4, twist=40, drop=2.0, head=(0, -24, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 12, 10), off_pole=V(2, 14, 6), face=V(0, 1, 0))
    for i, (tk, yaw) in enumerate(((9, -70), (12, 0), (15, 70), (18, 140), (21, 200), (24, 260))):
        hand = V(0, 9, -10)
        yr = math.radians(yaw)
        hp = (math.sin(yr) * 10, 9, -math.cos(yr) * 10)
        p = Pose()
        body(p, lean=14, twist=0, tilt=0, drop=2.5, head=(-6, 0, 0), root=(0, 0, 0))
        p.r('root', 0, yaw + 70, 0)
        plant(p, (-3.5, 1.5), (3.5, -3.5))
        wk(p, cls, (-2, 9, -11), D(-5, 5), 0.0, V(0, 1, 0), V(-14, 10, 4), 0.0, True, None, V(8, 12, 0))
        A.pose(tk, p, 'l' if i < 5 else 'c')
    A.pose(30, S)
    A.hit(13, 0.42, 3.4, 360, area=3.4, hitstop=3, shake=0.2, fx='spirit', sound='weapon.spirit_slash').cancel(24).trail(8, 24)
    A.sound(6, 'weapon.swing_light', 1.0, 0.8)
    A.build()
    # foresight slash: hop back with the blade raised, then a quick slash forward
    A = Attack(m, 'ls_foresight', cls, 26)
    A.pose(0, S)
    A.key(3, (-6, 4, 4), D(-40, 70), lean=-16, twist=24, drop=0.0, head=(-6, -10, 0), feet=((-3.5, 4.0), (3.5, -2.0)),
          pole=V(-16, 2, 10), off_pole=V(6, 4, 8), face=V(1, 0, 0), root=(0, -2, 0))
    A.key(7, (-6, 2, 4), D(-40, 75), lean=-14, twist=22, drop=1.0, head=(-6, -10, 0), feet=((-3.5, 4.0), (3.5, -2.0)),
          pole=V(-16, 0, 10), off_pole=V(6, 2, 8), face=V(1, 0, 0))
    A.key(11, (0, 9, -10), D(0, 5), lean=26, twist=-10, drop=2.5, head=(-16, 6, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 10, 4), off_pole=V(10, 12, 2), face=V(1, 0, 0), interp='l')
    A.key(15, (3, 13, -7), D(30, -40), lean=28, twist=-20, drop=3.0, head=(-16, 10, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-8, 14, 0), off_pole=V(12, 14, 0), face=V(1, 0, 0))
    A.pose(26, S)
    A.hit(11, 0.32, 3.8, 110, hitstop=2, fx='spirit', sound='weapon.spirit_slash').iframes(0, 12).cancel(18).trail(8, 15)
    A.build()


# ------------------------------------------------------------------------------------------------ sword & shield
def sns(m):
    cls = 'sword_and_shield'
    S = STANCE['idle_sns']
    SH = dict(two=False, off_hand=(5, 9, -7), off_pole=V(14, 12, 2))

    def shield(p):
        p.r('hand_l', 0, 0, 0)
        return p

    A = Attack(m, 'sns_combo1', cls, 12)
    A.pose(0, S)
    shield(A.key(3, (-8, 6, 1), D(-70, 40), lean=2, twist=22, drop=1.0, head=(0, -12, 0), pole=V(-16, 6, 8), **SH))
    shield(A.key(5, (-2, 9, -9), D(10, -10), lean=12, twist=-6, drop=1.5, head=(-6, 2, 0), pole=V(-14, 10, 2), interp='l', **SH))
    shield(A.key(8, (3, 12, -6), D(50, -35), lean=10, twist=-14, drop=1.5, head=(-4, 8, 0), pole=V(-10, 12, 0), **SH))
    A.pose(12, S)
    A.hit(5, 0.14, 3.2, 90, hitstop=1).move(3, 6, 0.06).cancel(7).next('sns_combo2').trail(3, 8)
    A.sound(2, 'weapon.swing_light', 0.8, 1.2)
    A.build()
    A = Attack(m, 'sns_combo2', cls, 12)
    A.pose(0, S)
    shield(A.key(3, (2, 5, -4), D(60, 50), lean=4, twist=-18, drop=1.0, head=(0, 10, 0), pole=V(-8, 4, -2), **SH))
    shield(A.key(5, (-3, 9, -9), D(-15, -5), lean=12, twist=10, drop=1.5, head=(-6, -4, 0), pole=V(-14, 10, 2), interp='l', **SH))
    shield(A.key(8, (-8, 12, -3), D(-70, -30), lean=10, twist=22, drop=1.5, head=(-4, -12, 0), pole=V(-16, 12, 6), **SH))
    A.pose(12, S)
    A.hit(5, 0.14, 3.2, 90, hitstop=1).move(3, 6, 0.06).cancel(7).next('sns_combo3').trail(3, 8)
    A.sound(2, 'weapon.swing_light', 0.8, 1.3)
    A.build()
    A = Attack(m, 'sns_combo3', cls, 14)
    A.pose(0, S)
    shield(A.key(3, (-5, 0, 0), D(-20, 85), lean=-6, twist=14, drop=0.5, head=(-4, -8, 0), pole=V(-16, -2, 8), **SH))
    shield(A.key(6, (-1, 9, -10), D(0, -15), lean=20, twist=-4, drop=2.0, head=(-12, 2, 0), pole=V(-14, 10, 2), interp='l', **SH))
    shield(A.key(9, (0, 13, -8), D(0, -50), lean=22, twist=-6, drop=2.5, head=(-14, 2, 0), pole=V(-14, 14, 2), **SH))
    A.pose(14, S)
    A.hit(6, 0.17, 3.2, 80, hitstop=2).move(4, 7, 0.07).cancel(9).next('sns_combo4').trail(4, 9)
    A.sound(3, 'weapon.swing_light', 0.9, 1.1)
    A.build()
    # combo 4: shield bash + slash finisher
    A = Attack(m, 'sns_combo4', cls, 16)
    A.pose(0, S)
    p = A.key(4, (-7, 9, 2), D(-40, 10), lean=-4, twist=26, drop=1.0, head=(0, -14, 0), pole=V(-16, 10, 8), two=False, off_hand=(6, 8, -4), off_pole=V(16, 10, 4))
    shield(p)
    p = A.key(7, (-6, 10, -1), D(-30, 15), lean=18, twist=-16, drop=2.5, head=(-8, 10, 0), pole=V(-16, 12, 6), two=False, off_hand=(2, 7, -11), off_pole=V(12, 8, -4), interp='l')
    shield(p)
    shield(A.key(11, (0, 9, -11), D(5, -10), lean=22, twist=4, drop=2.5, head=(-12, 0, 0), pole=V(-14, 10, 2), interp='l', **SH))
    A.pose(16, S)
    A.hit(7, 0.10, 2.8, 70, kind='blunt', ko=(8, 2), hitstop=2, fx='bash', sound='weapon.bash', use_sharp=False, bounce=False)
    A.hit(11, 0.20, 3.2, 90, hitstop=2).move(5, 8, 0.08).cancel(12).trail(9, 13)
    A.build()
    # shield bash (special)
    A = Attack(m, 'sns_bash', cls, 18)
    A.pose(0, S)
    p = A.key(5, (-7, 10, 2), D(-40, 10), lean=-6, twist=30, drop=1.0, head=(0, -16, 0), pole=V(-16, 10, 8), two=False, off_hand=(7, 8, -2), off_pole=V(18, 10, 6))
    shield(p)
    p = A.key(8, (-7, 11, 0), D(-35, 10), lean=24, twist=-20, drop=3.0, head=(-10, 12, 0), pole=V(-16, 12, 6), two=False, off_hand=(1, 7, -12), off_pole=V(12, 8, -4), interp='l')
    shield(p)
    p = A.key(12, (-7, 11, 0), D(-35, 10), lean=20, twist=-16, drop=2.5, head=(-8, 10, 0), pole=V(-16, 12, 6), two=False, off_hand=(1, 8, -11), off_pole=V(12, 8, -4))
    shield(p)
    A.pose(18, S)
    A.hit(8, 0.24, 3.0, 70, kind='blunt', ko=(14, 4), hitstop=3, shake=0.15, fx='bash', sound='weapon.bash', use_sharp=False, bounce=False)
    A.move(5, 9, 0.1).cancel(13)
    A.build()
    # guard slash: quick slash while the shield stays up
    A = Attack(m, 'sns_guard_slash', cls, 14)
    G = Pose()
    body(G, lean=8, twist=-10, drop=2.0, head=(-4, 6, 0))
    plant(G, (-3.5, 3.0), (3.5, -4.0))
    wk(G, cls, (-7, 11, -2), D(-30, 20), 0.0, V(1, 0, 0), V(-16, 12, 6), 0.0, False, (2, 5, -9), V(12, 6, -2))
    shield(G)
    A.pose(0, G)
    p = A.key(3, (-8, 6, 2), D(-60, 50), lean=6, twist=-6, drop=2.0, head=(-4, 4, 0), pole=V(-16, 6, 8), two=False, off_hand=(2, 5, -9), off_pole=V(12, 6, -2))
    shield(p)
    p = A.key(6, (-2, 9, -10), D(10, -10), lean=14, twist=-14, drop=2.5, head=(-8, 8, 0), pole=V(-14, 10, 2), two=False, off_hand=(2, 5, -9), off_pole=V(12, 6, -2), interp='l')
    shield(p)
    A.pose(14, G)
    A.hit(6, 0.14, 3.2, 90, hitstop=1).cancel(9).trail(4, 9).lock(False)
    A.sound(3, 'weapon.swing_light', 0.8, 1.2)
    A.build()
    # guard hold
    a = anim(m, 'guard_sns', 40, loop=True)
    breathe(a, G, 40, chest=0.6, head=0.4, arms=0.3)


# ------------------------------------------------------------------------------------------------ dual blades
def db(m):
    cls = 'dual_blades'
    S = STANCE['idle_db']
    DEM = STANCE['idle_db_demon']

    def both(A, tk, rh, rd, lh, ld, lean=10, twist=0, drop=1.8, head=None, feet=None, rpole=None, lpole=None, interp='c', root=(0, 0, 0)):
        p = A.key(tk, rh, rd, lean=lean, twist=twist, drop=drop, head=head, feet=feet, pole=rpole, two=False, interp=interp, root=root)
        arm_ik(p, 'l', V(*lh), lpole if lpole is not None else V(lh[0] + 10, lh[1] + 6, lh[2] + 10))
        aim(p, 'l', cls, ld, 0.0, V(1, 0, 0))
        return p

    # 1: right slash, 2: left slash, 3: cross slash (both), 4/5: quick alternating, 6: spin
    A = Attack(m, 'db_combo1', cls, 10)
    A.pose(0, S)
    both(A, 3, (-9, 5, 2), D(-70, 50), (8, 12, -4), D(35, -20), lean=6, twist=24, head=(0, -14, 0), rpole=V(-18, 4, 8))
    both(A, 5, (-1, 9, -10), D(10, -10), (7, 11, -5), D(30, -20), lean=16, twist=-8, head=(-8, 4, 0), rpole=V(-14, 10, 2), interp='l')
    A.pose(10, S)
    A.hit(5, 0.12, 3.2, 100, hitstop=1).move(3, 5, 0.06).cancel(6).next('db_combo2').trail(3, 7)
    A.sound(2, 'weapon.swing_light', 0.7, 1.3)
    A.build()
    A = Attack(m, 'db_combo2', cls, 10)
    A.pose(0, S)
    both(A, 3, (-7, 12, -4), D(-35, -20), (9, 5, 2), D(70, 50), lean=6, twist=-24, head=(0, 14, 0), lpole=V(18, 4, 8))
    both(A, 5, (-7, 11, -5), D(-30, -20), (1, 9, -10), D(-10, -10), lean=16, twist=8, head=(-8, -4, 0), lpole=V(14, 10, 2), interp='l')
    A.pose(10, S)
    A.hit(5, 0.12, 3.2, 100, hitstop=1).move(3, 5, 0.06).cancel(6).next('db_combo3').trail(3, 7)
    A.sound(2, 'weapon.swing_light', 0.7, 1.35)
    A.build()
    A = Attack(m, 'db_combo3', cls, 12)
    A.pose(0, S)
    both(A, 3, (-9, 3, 1), D(-60, 60), (9, 3, 1), D(60, 60), lean=-6, twist=0, head=(-4, 0, 0), rpole=V(-18, 2, 8), lpole=V(18, 2, 8))
    both(A, 6, (-2, 10, -10), D(30, -25), (2, 10, -10), D(-30, -25), lean=22, twist=0, drop=2.5, head=(-14, 0, 0), rpole=V(-14, 10, 0), lpole=V(14, 10, 0), interp='l')
    both(A, 9, (2, 13, -8), D(50, -40), (-2, 13, -8), D(-50, -40), lean=24, twist=0, drop=3.0, head=(-14, 0, 0), rpole=V(-12, 14, 0), lpole=V(12, 14, 0))
    A.pose(12, S)
    A.hit(6, 0.16, 3.2, 100, hitstop=2).move(4, 7, 0.08).cancel(8).next('db_combo4').trail(4, 9)
    A.sound(3, 'weapon.swing_light', 0.8, 1.1)
    A.build()
    A = Attack(m, 'db_combo4', cls, 8)
    A.pose(0, S)
    both(A, 2, (-8, 8, 2), D(-80, 20), (7, 12, -4), D(35, -20), lean=8, twist=20, head=(0, -12, 0), rpole=V(-18, 8, 8))
    both(A, 4, (0, 8, -10), D(20, 0), (7, 12, -4), D(35, -20), lean=14, twist=-10, head=(-8, 6, 0), rpole=V(-14, 8, 0), interp='l')
    A.pose(8, S)
    A.hit(4, 0.10, 3.2, 100, hitstop=1).move(2, 4, 0.06).cancel(5).next('db_combo5').trail(2, 6)
    A.sound(1, 'weapon.swing_light', 0.7, 1.4)
    A.build()
    A = Attack(m, 'db_combo5', cls, 8)
    A.pose(0, S)
    both(A, 2, (-7, 12, -4), D(-35, -20), (8, 8, 2), D(80, 20), lean=8, twist=-20, head=(0, 12, 0), lpole=V(18, 8, 8))
    both(A, 4, (-7, 12, -4), D(-35, -20), (0, 8, -10), D(-20, 0), lean=14, twist=10, head=(-8, -6, 0), lpole=V(14, 8, 0), interp='l')
    A.pose(8, S)
    A.hit(4, 0.10, 3.2, 100, hitstop=1).move(2, 4, 0.06).cancel(5).next('db_combo6').trail(2, 6)
    A.sound(1, 'weapon.swing_light', 0.7, 1.45)
    A.build()
    # 6: spinning finisher
    A = Attack(m, 'db_combo6', cls, 16)
    A.pose(0, S)
    for i, (tk, yaw) in enumerate(((3, -60), (6, 30), (9, 130), (12, 230))):
        p = both(A, tk, (-8, 9, -3), D(-80, 5), (8, 9, -3), D(80, 5), lean=12, twist=0, drop=2.5, head=(-6, 0, 0),
                 rpole=V(-18, 10, 6), lpole=V(18, 10, 6), interp='l' if i < 3 else 'c')
        p.r('root', 0, yaw + 60, 0)
        plant(p, (-3.5, 1.5), (3.5, -3.5))
    A.pose(16, S)
    A.hit(7, 0.18, 3.0, 360, area=3.0, hitstop=2, shake=0.1).move(3, 10, 0.05).cancel(12).trail(3, 12)
    A.sound(3, 'weapon.swing_light', 0.9, 1.0)
    A.build()
    # blade dance: frantic alternating slashes then a double finisher (7 hits)
    A = Attack(m, 'db_dance', cls, 30)
    A.pose(0, DEM)
    for i in range(6):
        tk = 2 + i * 3
        sgn = 1 if i % 2 == 0 else -1
        rh = (-2 + 3 * sgn, 6 + 2 * (i % 3), -10) if sgn > 0 else (-9, 8, -2)
        lh = (9, 8, -2) if sgn > 0 else (2 - 3 * sgn, 6 + 2 * (i % 3), -10)
        both(A, tk, rh, D(20 * sgn, -20 + 15 * (i % 3)), lh, D(-20 * sgn, -20 + 15 * (i % 3)), lean=22, twist=-8 * sgn,
             drop=3.0, head=(-12, 0, 0), rpole=V(-16, 8, 2), lpole=V(16, 8, 2), interp='l')
    both(A, 21, (-1, 9, -12), D(15, -10), (1, 9, -12), D(-15, -10), lean=28, twist=0, drop=3.5, head=(-16, 0, 0), rpole=V(-14, 10, -2), lpole=V(14, 10, -2), interp='l')
    both(A, 25, (2, 14, -8), D(40, -45), (-2, 14, -8), D(-40, -45), lean=26, twist=0, drop=3.5, head=(-14, 0, 0), rpole=V(-12, 14, 0), lpole=V(12, 14, 0))
    A.pose(30, DEM)
    for i in range(6):
        A.hit(2 + i * 3, 0.15, 2.8, 360, area=2.8, hitstop=1, forward=1.0, up=1.0)
    A.hit(21, 0.28, 2.8, 360, area=2.8, hitstop=3, shake=0.2, forward=1.0)
    A.stamina(25).cancel(26).trail(1, 23).sound(0, 'weapon.blade_dance', 1.0, 1.0)
    A.build()
    # demon flurry: 3 fast cone slashes
    A = Attack(m, 'db_flurry', cls, 16)
    A.pose(0, S)
    both(A, 3, (-1, 8, -11), D(15, -5), (8, 10, -3), D(40, -20), lean=20, twist=-8, drop=2.5, head=(-10, 4, 0), rpole=V(-14, 8, 0), interp='l')
    both(A, 7, (-8, 10, -3), D(-40, -20), (1, 8, -11), D(-15, -5), lean=20, twist=8, drop=2.5, head=(-10, -4, 0), lpole=V(14, 8, 0), interp='l')
    both(A, 11, (-2, 7, -11), D(25, 10), (2, 7, -11), D(-25, 10), lean=24, twist=0, drop=3.0, head=(-12, 0, 0), rpole=V(-14, 8, -2), lpole=V(14, 8, -2), interp='l')
    A.pose(16, S)
    for tk in (3, 7, 11):
        A.hit(tk, 0.18, 3.4, 110, hitstop=1)
    A.move(2, 12, 0.07).cancel(12).trail(2, 12)
    A.build()


# ------------------------------------------------------------------------------------------------ hammer
def hammer(m):
    cls = 'hammer'
    S = STANCE['idle_hm']
    # combo 1: big side swing right -> left
    A = Attack(m, 'hm_combo1', cls, 24)
    A.pose(0, S)
    A.key(5, (-9, 12, 5), D(-160, 20), lean=0, twist=36, drop=1.5, head=(0, -20, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 14, 10), off_pole=V(2, 18, 6), face=V(0, 1, 0))
    A.key(10, (-1, 11, -10), D(-10, 0), lean=14, twist=-10, drop=2.5, head=(-8, 6, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(14, (6, 11, -4), D(70, -10), lean=12, twist=-34, drop=2.5, head=(-6, 16, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-8, 14, -2), off_pole=V(14, 12, 0), face=V(0, 1, 0))
    A.pose(24, S)
    A.hit(10, 0.36, 3.6, 120, kind='blunt', ko=(0, 0), hitstop=3, shake=0.2, fx='bash').move(7, 11, 0.07).cancel(16).next('hm_combo2').trail(7, 14)
    A.sound(6, 'weapon.swing_heavy', 1.0, 0.9)
    A.build()
    # combo 2: return swing left -> right over the head
    A = Attack(m, 'hm_combo2', cls, 24)
    A.pose(0, S)
    A.key(5, (7, 4, 2), D(90, 50), lean=-6, twist=-36, drop=1.0, head=(0, 18, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(14, 4, 8), off_pole=V(16, 6, 4), face=V(0, 1, 0))
    A.key(10, (-2, 10, -10), D(-20, -10), lean=18, twist=14, drop=2.5, head=(-10, -8, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(14, (-8, 12, 1), D(-100, -25), lean=14, twist=34, drop=2.5, head=(-6, -18, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-18, 14, 8), off_pole=V(4, 16, 4), face=V(0, 1, 0))
    A.pose(24, S)
    A.hit(10, 0.42, 3.6, 120, kind='blunt', hitstop=3, shake=0.2, fx='bash').move(7, 11, 0.07).cancel(16).next('hm_combo3').trail(7, 14)
    A.sound(6, 'weapon.swing_heavy', 1.0, 0.85)
    A.build()
    # combo 3: golf swing (upswing from behind-right, hop)
    A = Attack(m, 'hm_combo3', cls, 30)
    A.pose(0, S)
    A.key(6, (-6, 18, 6), D(-150, -40), lean=24, twist=34, drop=3.5, head=(-10, -16, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 20, 10), off_pole=V(4, 22, 6), face=V(0, 1, 0))
    A.key(9, (-7, 19, 7), D(-160, -45), lean=28, twist=38, drop=4.0, head=(-10, -18, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 20, 10), off_pole=V(4, 22, 6), face=V(0, 1, 0))
    A.key(13, (-1, 12, -10), D(0, -10), lean=12, twist=0, drop=1.5, head=(-4, 0, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(16, (3, 2, -6), D(30, 70), lean=-12, twist=-20, drop=-1.0, head=(4, 10, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-10, 0, 0), off_pole=V(12, 4, 0), face=V(1, 0, 0))
    A.key(22, (0, 5, -4), D(10, 75), lean=-6, twist=-10, drop=1.0, head=(2, 6, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-12, 4, 2), off_pole=V(12, 6, 2), face=V(1, 0, 0))
    A.pose(30, S)
    A.hit(13, 0.56, 3.6, 90, kind='blunt', ko=(6, 0), hitstop=4, shake=0.35, fx='bash').move(10, 14, 0.09, 0.08).cancel(22).trail(10, 17)
    A.sound(8, 'weapon.swing_heavy', 1.0, 0.75)
    A.build()
    # charge hold: hammer dragged behind, knees bent, ready to swing
    C = Pose()
    body(C, lean=12, twist=36, drop=3.0, head=(-4, -22, 0), hips_rot=(4, -14, 0))
    plant(C, (-4.0, 3.5), (3.5, -5.0))
    wk(C, cls, (-8, 15, 6), D(-165, 10), 0.0, V(0, 1, 0), V(-18, 18, 10), 0.0, True, None, V(2, 20, 6))
    a = anim(m, 'charge_hm', 40, loop=True)
    breathe(a, C, 40, chest=0.8, head=0.5, arms=0.4)
    # charge 1: upswing
    A = Attack(m, 'hm_charge1', cls, 22)
    A.pose(0, C)
    A.key(5, (-7, 19, 6), D(-160, -40), lean=26, twist=36, drop=4.0, head=(-10, -18, 0), feet=((-4.0, 3.5), (3.5, -5.0)),
          pole=V(-14, 20, 10), off_pole=V(4, 22, 6), face=V(0, 1, 0))
    A.key(9, (-1, 11, -10), D(0, -5), lean=10, twist=0, drop=1.5, head=(-4, 0, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(13, (2, 1, -6), D(20, 75), lean=-14, twist=-16, drop=-1.0, head=(4, 8, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-10, 0, 0), off_pole=V(12, 2, 0), face=V(1, 0, 0))
    A.pose(22, S)
    A.hit(9, 0.60, 3.4, 80, kind='blunt', ko=(18, 3), sharp=1, hitstop=4, shake=0.3, fx='bash').move(6, 10, 0.1, 0.1).cancel(15).trail(6, 13)
    A.sound(4, 'weapon.swing_heavy', 1.0, 0.9)
    A.build()
    # charge 2: side smash
    A = Attack(m, 'hm_charge2', cls, 26)
    A.pose(0, C)
    A.key(4, (-9, 13, 7), D(-170, 15), lean=6, twist=40, drop=2.5, head=(0, -22, 0), feet=((-4.0, 3.5), (3.5, -5.0)),
          pole=V(-18, 14, 10), off_pole=V(2, 18, 6), face=V(0, 1, 0))
    A.key(11, (-1, 11, -11), D(-5, 0), lean=18, twist=-12, drop=3.0, head=(-10, 6, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(15, (6, 12, -4), D(75, -15), lean=16, twist=-36, drop=3.0, head=(-8, 18, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-8, 14, -2), off_pole=V(14, 12, 0), face=V(0, 1, 0))
    A.pose(26, S)
    A.hit(11, 0.85, 3.6, 130, kind='blunt', ko=(22, 4), sharp=2, hitstop=5, shake=0.4, fx='bash', sound='weapon.bash').move(8, 12, 0.1).cancel(18).trail(8, 16)
    A.sound(6, 'weapon.swing_heavy', 1.0, 0.75)
    A.build()
    # charge 3: spinning big bang: 3 spins then a ground slam
    A = Attack(m, 'hm_charge3', cls, 32)
    A.pose(0, C)
    for i, (tk, yaw) in enumerate(((3, -80), (6, 20), (9, 120), (12, 220), (15, 320), (18, 400))):
        p = A.key(tk, (-3, 11, -10), D(-15, 0), lean=12, twist=0, drop=2.5, head=(-6, 0, 0), feet=((-3.5, 1.5), (3.5, -3.5)),
                  pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
        p.r('root', 0, yaw + 80, 0)
    A.key(21, (-4, -6, -1), D(0, 88), lean=-14, twist=14, drop=0.0, head=(-8, -6, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -8, 8), off_pole=V(8, -2, 8), face=V(1, 0, 0))
    p = A.key(24, (0, 16, -9), D(0, -55), lean=40, twist=-6, drop=5.0, head=(-24, 4, 0), feet=((-3.5, 2.0), (3.5, -7.0)),
              pole=V(-14, 16, 2), off_pole=V(10, 16, 0), face=V(1, 0, 0), interp='l')
    p.r('root', 0, 480, 0)
    A.key(27, (0, 16, -9), D(0, -58), lean=40, twist=-6, drop=5.0, head=(-24, 4, 0), feet=((-3.5, 2.0), (3.5, -7.0)),
          pole=V(-14, 16, 2), off_pole=V(10, 16, 0), face=V(1, 0, 0)).r('root', 0, 480, 0)
    end = S.copy()
    end.r('root', 0, 480, 0)
    A.pose(32, end)
    for tk in (4, 8, 12):
        A.hit(tk, 0.22, 3.0, 360, area=3.0, kind='blunt', ko=(6, 1), hitstop=1, fx='sweep', forward=0.0)
    A.hit(24, 1.25, 3.8, 100, kind='blunt', ko=(40, 8), sharp=3, elem=1.5, status=1.5, hitstop=6, shake=0.8, fx='slam', sound='weapon.slam', forward=2.0, up=0.1)
    A.move(2, 14, 0.04).move(21, 24, 0.08).cancel(28).trail(2, 26)
    A.sound(2, 'weapon.swing_heavy', 0.8, 1.1).sound(8, 'weapon.swing_heavy', 0.8, 1.1).sound(20, 'weapon.swing_heavy', 1.0, 0.7)
    A.build()


# ------------------------------------------------------------------------------------------------ hunting horn
def horn(m):
    cls = 'hunting_horn'
    S = STANCE['idle_hh']
    A = Attack(m, 'hh_combo1', cls, 26)
    A.pose(0, S)
    A.key(6, (-7, 0, 2), D(-30, 85), lean=-10, twist=24, drop=0.5, head=(-4, -12, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, -2, 10), off_pole=V(6, 2, 8), face=V(1, 0, 0))
    A.key(11, (-1, 10, -10), D(0, -10), lean=28, twist=-6, drop=2.5, head=(-18, 4, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 12, 2), off_pole=V(10, 12, 0), face=V(1, 0, 0), interp='l')
    A.key(15, (0, 15, -8), D(0, -48), lean=32, twist=-8, drop=3.5, head=(-20, 4, 0), feet=((-3.5, 2.0), (3.5, -6.0)),
          pole=V(-14, 16, 2), off_pole=V(10, 16, 0), face=V(1, 0, 0))
    A.pose(26, S)
    A.hit(11, 0.33, 3.6, 100, kind='blunt', hitstop=3, shake=0.15, fx='bash').move(8, 12, 0.07).cancel(17).next('hh_combo2').trail(8, 15).tag('note', 1)
    A.sound(7, 'weapon.swing_heavy', 1.0, 0.95)
    A.build()
    A = Attack(m, 'hh_combo2', cls, 24)
    A.pose(0, S)
    A.key(5, (7, 6, 1), D(85, 40), lean=0, twist=-34, drop=1.0, head=(0, 18, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(14, 6, 8), off_pole=V(16, 8, 4), face=V(0, 1, 0))
    A.key(10, (-2, 10, -10), D(-15, -5), lean=14, twist=12, drop=2.5, head=(-8, -8, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(14, (-8, 12, 2), D(-110, -20), lean=12, twist=34, drop=2.5, head=(-6, -18, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-18, 14, 8), off_pole=V(4, 16, 4), face=V(0, 1, 0))
    A.pose(24, S)
    A.hit(10, 0.30, 3.6, 130, kind='blunt', hitstop=3, shake=0.15, fx='bash').move(7, 11, 0.06).cancel(16).next('hh_combo3').trail(7, 14).tag('note', 1)
    A.sound(6, 'weapon.swing_heavy', 1.0, 1.0)
    A.build()
    A = Attack(m, 'hh_combo3', cls, 30)
    A.pose(0, S)
    A.key(6, (-6, 18, 6), D(-150, -40), lean=22, twist=34, drop=3.5, head=(-10, -16, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 20, 10), off_pole=V(4, 22, 6), face=V(0, 1, 0))
    A.key(12, (-1, 11, -10), D(0, -5), lean=10, twist=0, drop=1.5, head=(-4, 0, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(16, (3, 2, -6), D(25, 72), lean=-12, twist=-18, drop=-1.0, head=(4, 10, 0), feet=((-3.5, 3.0), (3.5, -5.0)),
          pole=V(-10, 0, 0), off_pole=V(12, 4, 0), face=V(1, 0, 0))
    A.pose(30, S)
    A.hit(12, 0.38, 3.6, 90, kind='blunt', ko=(4, 0), hitstop=4, shake=0.3, fx='bash').move(9, 13, 0.09, 0.06).cancel(22).trail(9, 16).tag('note', 1)
    A.sound(8, 'weapon.swing_heavy', 1.0, 0.8)
    A.build()
    # note 2 (right click): wide left swing; note 3 (sneak + left): backswing
    A = Attack(m, 'hh_swing2', cls, 24)
    A.pose(0, S)
    A.key(5, (-9, 11, 5), D(-150, 15), lean=2, twist=36, drop=1.5, head=(0, -20, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-18, 12, 10), off_pole=V(2, 16, 6), face=V(0, 1, 0))
    A.key(10, (-1, 10, -10), D(-5, 0), lean=14, twist=-10, drop=2.5, head=(-8, 6, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(8, 14, 0), face=V(0, 1, 0), interp='l')
    A.key(14, (6, 11, -4), D(70, -10), lean=12, twist=-34, drop=2.5, head=(-6, 16, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-8, 14, -2), off_pole=V(14, 12, 0), face=V(0, 1, 0))
    A.pose(24, S)
    A.hit(10, 0.38, 3.8, 110, kind='blunt', hitstop=3, shake=0.15, fx='bash').move(7, 11, 0.07).cancel(16).trail(7, 14).tag('note', 2)
    A.sound(6, 'weapon.swing_heavy', 1.0, 0.9)
    A.build()
    A = Attack(m, 'hh_swing3', cls, 26)
    A.pose(0, S)
    A.key(5, (-3, 4, -8), D(10, 60), lean=10, twist=-10, drop=2.0, head=(-6, 4, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-14, 4, 0), off_pole=V(10, 6, 0), face=V(1, 0, 0))
    A.key(11, (-6, 12, 2), D(-140, -20), lean=4, twist=30, drop=2.5, head=(0, -22, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-18, 14, 8), off_pole=V(2, 16, 4), face=V(0, 1, 0), interp='l')
    A.key(15, (-8, 10, 6), D(-170, 0), lean=0, twist=36, drop=2.0, head=(0, -24, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-18, 12, 10), off_pole=V(2, 16, 6), face=V(0, 1, 0))
    A.pose(26, S)
    A.hit(11, 0.33, 3.4, 200, kind='blunt', hitstop=3, shake=0.15, fx='bash').cancel(17).trail(7, 15).tag('note', 3)
    A.sound(6, 'weapon.swing_heavy', 1.0, 1.05)
    A.build()
    # perform: raise the horn, blow, swing it around (hit at 10)
    A = Attack(m, 'hh_perform', cls, 40)
    A.pose(0, S)
    A.key(5, (-6, 8, -2), D(-30, 50), lean=-4, twist=16, drop=1.0, head=(-6, -8, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, 8, 8), off_pole=V(8, 10, 2), face=V(1, 0, 0))
    A.key(10, (0, 10, -9), D(0, 10), lean=16, twist=0, drop=2.5, head=(-10, 0, 0), feet=((-3.5, 2.0), (3.5, -5.0)),
          pole=V(-14, 12, 2), off_pole=V(10, 12, 0), face=V(1, 0, 0), interp='l')
    A.key(16, (-4, -2, -6), D(-15, 80), lean=-16, twist=10, drop=0.0, head=(-24, -4, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -4, 6), off_pole=V(8, 0, 4), face=V(1, 0, 0))
    A.key(30, (-4, -2, -6), D(-15, 82), lean=-18, twist=8, drop=0.0, head=(-26, -4, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
          pole=V(-16, -4, 6), off_pole=V(8, 0, 4), face=V(1, 0, 0))
    A.pose(40, S)
    A.hit(10, 0.28, 2.8, 360, area=2.8, kind='blunt', hitstop=2, fx='note', forward=0.0).cancel(32).tag('perform', 10)
    A.sound(14, 'weapon.horn_song', 1.2, 1.0)
    A.build()


# ------------------------------------------------------------------------------------------------ lance
def lance(m):
    cls = 'lance'
    S = STANCE['idle_ln']
    SH = dict(two=False, off_hand=(5, 8, -7), off_pole=V(14, 10, 2))

    def shield(p):
        p.r('hand_l', 0, 0, 0)
        return p

    for i, (name, nxt, mv, high) in enumerate((('ln_combo1', 'ln_combo2', 0.24, 0), ('ln_combo2', 'ln_combo3', 0.24, 0), ('ln_combo3', None, 0.30, 1))):
        ticks = 20 if high else 16
        A = Attack(m, name, cls, ticks)
        A.pose(0, S)
        shield(A.key(3, (-8, 13 - 3 * high, 4), D(-8, 6 + 14 * high), lean=2, twist=28, drop=1.5, head=(0, -14, 0),
                     feet=((-3.5, 3.0), (3.5, -4.0)), pole=V(-16, 16, 10), face=V(1, 0, 0), **SH))
        shield(A.key(7 + high, (-3, 11 - 3 * high, -13), D(0, 2 + 16 * high), lean=22, twist=-8, drop=3.0, head=(-10, 4, 0),
                     feet=((-3.5, 1.0), (3.5, -8.0)), pole=V(-14, 14, -4), face=V(1, 0, 0), interp='l', **SH))
        shield(A.key(10 + 2 * high, (-3, 11 - 3 * high, -13), D(0, 2 + 16 * high), lean=22, twist=-8, drop=3.0, head=(-10, 4, 0),
                     feet=((-3.5, 1.0), (3.5, -8.0)), pole=V(-14, 14, -4), face=V(1, 0, 0), **SH))
        A.pose(ticks, S)
        A.hit(7 + high, mv, 4.4, 36, hitstop=2).move(4, 8, 0.07).cancel(10 + high).trail(5, 10 + high)
        if nxt:
            A.next(nxt)
        A.sound(4, 'weapon.swing_light', 0.8, 0.8)
        A.build()
    # guard hold and counter poke
    G = Pose()
    body(G, lean=10, twist=-12, drop=2.5, head=(-6, 8, 0))
    plant(G, (-4.0, 3.0), (3.5, -5.0))
    wk(G, cls, (-7, 14, -2), D(-15, -10), 0.0, V(1, 0, 0), V(-16, 16, 6), 0.0, False, (1, 5, -9), V(12, 6, -2))
    shield(G)
    a = anim(m, 'guard_ln', 40, loop=True)
    breathe(a, G, 40, chest=0.6, head=0.4, arms=0.3)
    A = Attack(m, 'ln_counter', cls, 16)
    A.pose(0, G)
    shield(A.key(3, (-9, 13, 3), D(-10, 0), lean=4, twist=10, drop=2.0, head=(-4, 0, 0), feet=((-4.0, 3.0), (3.5, -5.0)),
                 pole=V(-18, 16, 10), face=V(1, 0, 0), two=False, off_hand=(1, 5, -9), off_pole=V(12, 6, -2)))
    shield(A.key(6, (-3, 11, -13), D(0, 0), lean=20, twist=-14, drop=3.0, head=(-8, 6, 0), feet=((-4.0, 2.0), (3.5, -7.0)),
                 pole=V(-14, 14, -4), face=V(1, 0, 0), interp='l', two=False, off_hand=(2, 5, -8), off_pole=V(12, 6, -2)))
    shield(A.key(9, (-3, 11, -13), D(0, 0), lean=20, twist=-14, drop=3.0, head=(-8, 6, 0), feet=((-4.0, 2.0), (3.5, -7.0)),
                 pole=V(-14, 14, -4), face=V(1, 0, 0), two=False, off_hand=(2, 5, -8), off_pole=V(12, 6, -2)))
    A.pose(16, G)
    A.hit(6, 0.55, 4.4, 36, sharp=2, hitstop=3, shake=0.2, fx='counter', sound='weapon.counter').cancel(10).trail(4, 9).lock(False)
    A.build()
    ACTIONS['ln_poke'] = dict(ACTIONS['ln_counter'])
    ACTIONS['ln_poke']['anim'] = 'ln_counter'
    ACTIONS['ln_poke']['hits'] = [dict(ACTIONS['ln_counter']['hits'][0], mv=0.36, sharp=1, hitstop=2, shake=0.0, fx='sweep', sound='weapon.swing_light')]
    # dash loop + finisher
    a = anim(m, 'ln_dash', 12, loop=True)
    n = 6
    for i in range(n + 1):
        ph = 2 * math.pi * i / n
        p = Pose()
        body(p, lean=26, twist=-6, drop=2.0 + 0.8 * (0.5 - 0.5 * math.cos(2 * ph)), head=(-14, 4, 0), hips_rot=(8, 0, -3 * math.sin(ph)))
        p.r('leg_r', 40 * math.sin(ph), 0, 0)
        p.r('leg_l', -40 * math.sin(ph), 0, 0)
        p.r('leg_r_lower', max(0.0, math.sin(ph - 2.0)) * 60, 0, 0)
        p.r('leg_l_lower', max(0.0, math.sin(ph + math.pi - 2.0)) * 60, 0, 0)
        wk(p, cls, (-6, 11, -8), D(0, 4), 0.0, V(1, 0, 0), V(-16, 14, 0), 0.0, False, (2, 6, -9), V(12, 6, -2))
        shield(p)
        key(a, 12 * TICK * i / n, p, ALL)
    A = Attack(m, 'ln_dash_finish', cls, 16)
    A.pose(0, G)
    shield(A.key(2, (-8, 12, 3), D(-8, 6), lean=10, twist=20, drop=2.0, head=(-4, -8, 0), feet=((-3.5, 3.0), (3.5, -4.0)),
                 pole=V(-16, 14, 10), face=V(1, 0, 0), **SH))
    shield(A.key(6, (-2, 10, -14), D(0, 4), lean=26, twist=-12, drop=3.5, head=(-12, 6, 0), feet=((-3.5, 1.0), (3.5, -8.0)),
                 pole=V(-14, 12, -4), face=V(1, 0, 0), interp='l', **SH))
    shield(A.key(10, (-2, 10, -14), D(0, 4), lean=26, twist=-12, drop=3.5, head=(-12, 6, 0), feet=((-3.5, 1.0), (3.5, -8.0)),
                 pole=V(-14, 12, -4), face=V(1, 0, 0), **SH))
    A.pose(16, S)
    A.hit(6, 0.60, 4.0, 50, sharp=2, hitstop=3, shake=0.25, fx='counter', sound='weapon.counter').move(2, 6, 0.12).cancel(11).trail(3, 9)
    A.build()


# ------------------------------------------------------------------------------------------------ bow
def bow(m):
    cls = 'bow'
    S = STANCE['idle_bow']
    # draw hold: bow raised in the right hand, left hand pulls the string to the cheek
    C = Pose()
    body(C, lean=4, twist=34, drop=1.2, head=(0, -34, 0), hips_rot=(0, -10, 0))
    plant(C, (-3.0, 4.0), (3.0, -4.5))
    wk(C, cls, (-4, 6, -11), D(0, 90), 0.0, V(0, 0, -1), V(-16, 8, -2), 0.0, False, (-1, 4, -3), V(12, 4, 8))
    C.r('hand_l', 0, 0, 0)
    a = anim(m, 'charge_bow', 40, loop=True)
    breathe(a, C, 40, chest=0.5, head=0.3, arms=0.2)
    # shoot: release, string hand snaps back, small recoil
    A = Attack(m, 'bow_shoot', cls, 10)
    A.pose(0, C)
    p = A.key(2, (-4, 6, -11), D(0, 90), lean=2, twist=34, drop=1.2, head=(0, -34, 0), feet=((-3.0, 4.0), (3.0, -4.5)),
              pole=V(-16, 8, -2), face=V(0, 0, -1), two=False, off_hand=(4, 5, 0), off_pole=V(14, 6, 8), interp='l')
    p.r('hand_l', 0, 0, 0)
    A.pose(10, C)
    A.lock(False).cancel(4)
    A.build()
    del ACTIONS['bow_shoot']['hits']
    # kick: melee for the bow
    A = Attack(m, 'bow_kick', cls, 16)
    A.pose(0, S)
    p = Pose()
    body(p, lean=-8, twist=-16, drop=1.0, head=(-2, 12, 0))
    legs(p, (10, 0, 2), (-60, 0, -4), 0, 90)
    wk(p, cls, (-7, 12, -2), D(0, -60), 0.0, V(1, 0, 0), V(-16, 14, 6), 0.0, False, (6, 8, 2), V(16, 10, 8))
    A.pose(4, p)
    p2 = Pose()
    body(p2, lean=14, twist=-24, drop=2.0, head=(-10, 16, 0))
    legs(p2, (16, 0, 2), (-95, 0, -4), 0, 8)
    wk(p2, cls, (-8, 12, 0), D(-10, -60), 0.0, V(1, 0, 0), V(-16, 14, 8), 0.0, False, (8, 6, 4), V(18, 8, 8))
    A.pose(7, p2, 'l')
    A.pose(10, p2)
    A.pose(16, S)
    A.hit(7, 0.08, 2.6, 60, kind='blunt', ko=(3, 1), use_sharp=False, bounce=False, hitstop=2, fx='bash', sound='combat.kick').cancel(11)
    A.build()


# ------------------------------------------------------------------------------------------------ dodge / reactions
def reactions(m):
    # dodge rolls: 10 ticks, a full forward tumble over the shoulder
    for name, axis in (('dodge_f', 'f'), ('dodge_b', 'b'), ('dodge_l', 'l'), ('dodge_r', 'r')):
        a = anim(m, name, 10)
        for i in range(11):
            f = i / 10.0
            p = Pose()
            tuck = math.sin(math.pi * min(1.0, f * 1.15))
            ang = 360.0 * (0.5 - 0.5 * math.cos(math.pi * f)) if f < 1 else 360.0
            rx = ry = rz = 0.0
            if axis == 'f':
                rx = ang
            elif axis == 'b':
                rx = -ang
            elif axis == 'l':
                rz = ang
            else:
                rz = -ang
            # rotate about the body centre (11px above the feet): counter-translate the root along the circle
            th = math.radians(ang)
            cy = -11.0
            if axis in ('f', 'b'):
                sg = 1 if axis == 'f' else -1
                px_, py_, pz_ = 0.0, cy - cy * math.cos(th), -cy * math.sin(th) * sg
            else:
                sg = 1 if axis == 'l' else -1
                px_, py_, pz_ = cy * math.sin(th) * sg, cy - cy * math.cos(th), 0.0
            p.r('root', rx, ry, rz)
            p.p('root', px_, py_ - 2.0 * math.sin(math.pi * f), pz_)
            p.r('hips', 20 * tuck, 0, 0)
            p.r('chest', 45 * tuck, 0, 0)
            p.r('head', 10 * tuck, 0, 0)
            p.r('leg_r', -95 * tuck, 0, 4)
            p.r('leg_l', -90 * tuck, 0, -4)
            p.r('leg_r_lower', 100 * tuck, 0, 0)
            p.r('leg_l_lower', 95 * tuck, 0, 0)
            p.r('arm_r', -60 * tuck, 0, 20 * tuck)
            p.r('arm_l', -70 * tuck, 0, -20 * tuck)
            p.r('arm_r_lower', -70 * tuck, 0, 0)
            p.r('arm_l_lower', -70 * tuck, 0, 0)
            key(a, i * TICK, p, ALL, 'l')
    # flinch: hit from the front, stagger back
    A = Attack(m, 'flinch', 'great_sword', 12, two=False)
    p = Pose()
    body(p, lean=-14, twist=10, tilt=6, drop=1.0, head=(-14, 10, 0))
    plant(p, (-3.5, 1.0), (3.5, -2.0))
    p.r('arm_r', -30, 0, 30)
    p.r('arm_l', -20, 0, -40)
    p.r('arm_r_lower', -40, 0, 0)
    p.r('arm_l_lower', -30, 0, 0)
    A.pose(0, Pose())
    A.pose(2, p, 'l')
    p2 = p.copy()
    body(p2, lean=-6, twist=4, tilt=2, drop=2.0, head=(-4, 4, 0))
    plant(p2, (-3.5, 1.0), (3.5, -2.0))
    A.pose(7, p2)
    A.pose(12, Pose())
    A.lock(False).cancel(6)
    A.build()
    del ACTIONS['flinch']['hits']
    # knockdown: thrown back, lands on the back, pushes up, stands
    A = Attack(m, 'knockdown', 'great_sword', 44, two=False)
    A.pose(0, Pose())
    p = Pose()
    body(p, lean=-30, drop=-4.0, head=(-20, 0, 0))
    p.r('arm_r', -120, 0, 40)
    p.r('arm_l', -120, 0, -40)
    p.r('leg_r', -40, 0, 6)
    p.r('leg_l', -20, 0, -6)
    p.r('leg_r_lower', 40, 0, 0)
    p.r('leg_l_lower', 20, 0, 0)
    A.pose(4, p)
    lie = Pose()
    lie.r('root', -84, 0, 0)
    lie.p('root', 0, -3.0, 4)
    lie.r('chest', 4, 0, 0)
    lie.r('head', 14, 0, 0)
    lie.r('arm_r', -40, 0, 50)
    lie.r('arm_l', -30, 0, -60)
    lie.r('arm_r_lower', -30, 0, 0)
    lie.r('arm_l_lower', -40, 0, 0)
    lie.r('leg_r', -16, 0, 6)
    lie.r('leg_l', -6, 0, -6)
    lie.r('leg_r_lower', 30, 0, 0)
    lie.r('leg_l_lower', 10, 0, 0)
    A.pose(10, lie, 'l')
    A.pose(24, lie)
    up = Pose()
    up.r('root', -30, 0, 0)
    up.p('root', 0, 1.0, 2)
    up.r('chest', 30, 0, 0)
    up.r('head', -10, 0, 0)
    up.r('arm_r', 40, 0, 30)
    up.r('arm_l', 40, 0, -30)
    up.r('arm_r_lower', -20, 0, 0)
    up.r('arm_l_lower', -20, 0, 0)
    up.r('leg_r', -70, 0, 6)
    up.r('leg_l', -60, 0, -6)
    up.r('leg_r_lower', 80, 0, 0)
    up.r('leg_l_lower', 70, 0, 0)
    A.pose(32, up)
    crouch = Pose()
    crouch.p('root', 0, 4.0, 0)
    crouch.r('chest', 20, 0, 0)
    crouch.r('head', -14, 0, 0)
    crouch.r('leg_r', -50, 0, 4)
    crouch.r('leg_l', -50, 0, -4)
    crouch.r('leg_r_lower', 60, 0, 0)
    crouch.r('leg_l_lower', 60, 0, 0)
    crouch.r('arm_r', 10, 0, 10)
    crouch.r('arm_l', 10, 0, -10)
    A.pose(38, crouch)
    A.pose(44, Pose())
    A.iframes(0, 40).cancel(40).tag('nocancel', 1).sound(8, 'combat.knockdown', 1.0, 1.0)
    A.build()
    del ACTIONS['knockdown']['hits']


# ------------------------------------------------------------------------------------------------ misc actions
def misc(m):
    # sheathe / unsheathe (right hand reaches over the shoulder)
    for name, fwd in (('unsheathe', True), ('sheathe', False)):
        A = Attack(m, name, 'great_sword', 14, two=False)
        reach = Pose()
        body(reach, lean=-4, twist=14, drop=1.0, head=(-4, -10, 0))
        plant(reach, (-3.5, 2.0), (3.5, -3.0))
        arm_ik(reach, 'r', V(-2, -6, 4), V(-14, -8, 10))
        reach.r('hand_r', 0, 0, 0)
        reach.r('arm_l', 10, 0, -8)
        reach.r('arm_l_lower', -20, 0, 0)
        down = Pose()
        body(down, lean=4, twist=8, drop=1.5, head=(0, -6, 0))
        plant(down, (-3.5, 2.0), (3.5, -3.0))
        arm_ik(down, 'r', V(-7, 12, -4), V(-16, 12, 8))
        down.r('hand_r', 0, 0, 0)
        down.r('arm_l', 10, 0, -8)
        down.r('arm_l_lower', -20, 0, 0)
        # guide key: the hand travels up through the front (not sideways) on its way to the shoulder
        mid = Pose()
        body(mid, lean=-2, twist=8, drop=1.0, head=(-2, -6, 0))
        plant(mid, (-3.5, 2.0), (3.5, -3.0))
        mid.r('arm_r', -95, -20, 0)
        mid.r('arm_r_lower', -80, 0, 0)
        mid.r('arm_l', 8, 0, -8)
        mid.r('arm_l_lower', -16, 0, 0)
        if fwd:
            A.pose(0, STANCE['idle'])
            A.pose(2.5, mid)
            A.pose(5, reach)
            A.pose(6, reach, 'l')
            A.pose(10, down)
            A.pose(14, STANCE['idle_gs'])
        else:
            A.pose(0, STANCE['idle_gs'])
            A.pose(4, down)
            A.pose(8, reach)
            A.pose(9, reach, 'l')
            A.pose(11.5, mid)
            A.pose(14, STANCE['idle'])
        A.lock(False).cancel(8).tag('swap', 6 if fwd else 9)
        A.sound(6 if fwd else 9, 'combat.' + name, 0.8, 1.0)
        A.build()
        del ACTIONS[name]['hits']

    # sharpen (loop): kneeling, the weapon lies across the lap in the LEFT hand (HeldItems swaps the hands and puts
    # the whetstone in the right hand), the right hand rubs the stone along the blade towards the tip
    a = anim(m, 'sharpen', 16, loop=True)
    for i in range(9):
        f = i / 8.0
        p = Pose()
        body(p, lean=22, twist=-8, drop=5.0, head=(-24, -6, 0), hips_rot=(10, 0, 0))
        plant(p, (-4.0, 1.0), (4.0, -5.0))
        p.r('leg_r', -70, 0, 0)
        p.r('leg_r_lower', 110, 0, 0)
        p.r('leg_l', -30, 0, 0)
        p.r('leg_l_lower', 95, 0, 0)
        arm_ik(p, 'l', V(5, 13, -8), V(16, 12, 0))
        aim(p, 'l', 'long_sword', D(-100, 8), face=V(0, 1, 0))
        s = math.sin(2 * math.pi * f)
        c = math.cos(2 * math.pi * f)
        arm_ik(p, 'r', V(-4 - 5 * s, 11.5 - 1.2 * s, -8.5 + 0.5 * c), V(-16, 8, -2))
        p.r('hand_r', -70, 0, 0)
        key(a, 16 * TICK * f, p, ALL)

    # carve: kneel, cut with the right hand
    A = Attack(m, 'carve', 'great_sword', 24, two=False)
    A.pose(0, Pose())
    kneel = Pose()
    body(kneel, lean=22, twist=-4, drop=6.0, head=(-18, 0, 0), hips_rot=(8, 0, 0))
    kneel.r('leg_r', -80, 0, 0)
    kneel.r('leg_r_lower', 120, 0, 0)
    kneel.r('leg_l', 10, 0, 0)
    kneel.r('leg_l_lower', 100, 0, 0)
    arm_ik(kneel, 'l', V(5, 16, -8), V(14, 14, 0))
    arm_ik(kneel, 'r', V(-5, 17, -9), V(-14, 14, 0))
    A.pose(5, kneel)
    k2 = kneel.copy()
    arm_ik(k2, 'r', V(-2, 15, -11), V(-14, 12, 0))
    A.pose(10, k2)
    k3 = kneel.copy()
    arm_ik(k3, 'r', V(-8, 18, -7), V(-14, 16, 0))
    A.pose(15, k3)
    A.pose(19, kneel)
    A.pose(24, Pose())
    A.lock(True).cancel(18).tag('onback', 1)
    A.build()
    del ACTIONS['carve']['hits']

    # victory: crouch, then thrust the weapon to the sky (blade straight up), left fist on the hip, chest out
    A = Attack(m, 'victory', 'great_sword', 44, two=False)
    A.pose(0, Pose())
    crouch_ = Pose()
    body(crouch_, lean=14, twist=6, drop=3.5, head=(-10, -4, 0))
    plant(crouch_, (-4.0, 2.0), (4.0, -2.0))
    arm_ik(crouch_, 'r', V(-7, 12, -3), V(-16, 12, 6))
    aim(crouch_, 'r', 'great_sword', D(-20, 30), face=V(1, 0, 0))
    crouch_.r('arm_l', 16, 0, -14)
    crouch_.r('arm_l_lower', -30, 0, 0)
    A.pose(5, crouch_)
    p = Pose()
    body(p, lean=-12, twist=-8, drop=0.0, head=(-22, 6, 0), hips_rot=(-3, -3, 0))
    plant(p, (-4.0, 2.0), (4.5, -2.0))
    p.r('arm_r', -176, 0, 8)
    p.r('arm_r_lower', -6, 0, 0)
    aim(p, 'r', 'great_sword', D(0, 88), face=V(1, 0, 0))
    p.r('arm_l', 24, 0, -36)
    p.r('arm_l_lower', -92, 0, 0)
    p.r('hand_l', 0, 0, 0)
    A.pose(9, p, 'l')
    p2 = p.copy()
    body(p2, lean=-10, twist=-8, drop=0.6, head=(-20, 6, 0), hips_rot=(-3, -3, 0))
    plant(p2, (-4.0, 2.0), (4.5, -2.0))
    A.pose(12, p2)
    A.pose(30, p2)
    A.pose(44, Pose())
    A.lock(True).cancel(30)
    A.build()
    del ACTIONS['victory']['hits']

    # drink / eat (main hand) and off-hand variants
    for name, hand in (('drink', 'r'), ('eat', 'r'), ('drink_off', 'l'), ('eat_off', 'l')):
        a = anim(m, name, 32, loop=True)
        sg = -1 if hand == 'r' else 1
        other = 'l' if hand == 'r' else 'r'
        for i in range(9):
            f = i / 8.0
            p = Pose()
            tilt_back = -16 if name.startswith('drink') else -4
            body(p, lean=2, twist=0, drop=1.0, head=(tilt_back + 3 * math.sin(2 * math.pi * f), sg * 6, 0))
            plant(p, (-3.0, 1.0), (3.0, -1.5))
            bob = 1.2 * math.sin(4 * math.pi * f) if name.startswith('eat') else 0.4 * math.sin(2 * math.pi * f)
            arm_ik(p, hand, V(sg * 2.5, -1.5 + bob, -7.0), V(sg * 16, 2, 2))
            p.r('hand_' + hand, 0, 0, 0)
            p.r('arm_' + other, 6, 0, -sg * 6)
            p.r('arm_%s_lower' % other, -18, 0, 0)
            key(a, 32 * TICK * f, p, ALL)


# ------------------------------------------------------------------------------------------------ build all
def build_animations(m):
    global MODEL
    MODEL = m
    ACTIONS.clear()
    make_stances()
    make_loops(m)
    gs(m)
    ls(m)
    sns(m)
    db(m)
    hammer(m)
    horn(m)
    lance(m)
    bow(m)
    reactions(m)
    misc(m)
    return m


def export_actions():
    out = {'actions': ACTIONS, 'weapons': {}}
    for cls, w in H.WEAPONS.items():
        out['weapons'][cls] = {'grip': list(w['grip']), 'tip': list(w['tip']), 'hand2': w['hand2']}
    return out
