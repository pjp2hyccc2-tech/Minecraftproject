"""Motion toolkit for the boss rigs: eased keyframe authoring, sampled curves, two-bone leg IK for planted feet, and a
spring pass that adds overlapping secondary motion (capes, robes, hair, chains, tails, wing tips, vines) to every clip.

Conventions are those of ermodel (Mojang model space: +Y down, the model faces -Z, ground at y = 24; rotations in
degrees added to the rest rotation; positions in posVec units with y UP). Easing codes: see ermodel.EASES.

Workflow of one clip:
    c = Clip('melee_slam', 2.0, impact=1.15)
    c.k('chest', 0.0, rot=(0, 0, 0)); c.k('chest', 0.6, rot=(-20, 0, 0), e='s'); ...
    c.fn('core', 'scale', lambda t: (...), samples)      # procedural channels
    legs: Gait / plant_feet (IK) after the body keys are set
    anim = c.build(rig, variant, chains)                  # runs the secondary pass, returns an ermodel.Anim
"""
import math

import numpy as np

import ermodel

DEG = math.pi / 180.0


def smooth(a):
    a = max(0.0, min(1.0, a))
    return a * a * (3.0 - 2.0 * a)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp3(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


# ------------------------------------------------------------------------------------------------- clips

class Clip:
    """Keys per (bone, target): {time: (x, y, z, ease)}; later keys at the same time replace earlier ones."""

    def __init__(self, name, length, loop=False, impact=None):
        self.name, self.length, self.loop, self.impact = name, float(length), loop, impact
        self.tracks = {}

    def k(self, bone, t, rot=None, pos=None, scale=None, e='s'):
        for target, v in (('rot', rot), ('pos', pos), ('scale', scale)):
            if v is not None:
                self.tracks.setdefault((bone, target), {})[round(float(t), 4)] = (float(v[0]), float(v[1]),
                                                                                   float(v[2]), e)
        return self

    def seq(self, bone, target, keys, e='s'):
        """keys: (t, x, y, z) or (t, x, y, z, ease)."""
        for key in keys:
            code = key[4] if len(key) > 4 else e
            self.tracks.setdefault((bone, target), {})[round(float(key[0]), 4)] = (float(key[1]), float(key[2]),
                                                                                    float(key[3]), code)
        return self

    def rot(self, bone, keys, e='s'):
        return self.seq(bone, 'rot', keys, e)

    def pos(self, bone, keys, e='s'):
        return self.seq(bone, 'pos', keys, e)

    def scl(self, bone, keys, e='s'):
        return self.seq(bone, 'scale', keys, e)

    def fn(self, bone, target, f, samples, e='c', t0=0.0, t1=None):
        """A procedural channel: f(t) -> (x, y, z) sampled at {@code samples + 1} times (Catmull-Rom by default)."""
        t1 = self.length if t1 is None else t1
        track = self.tracks.setdefault((bone, target), {})
        for i in range(samples + 1):
            t = t0 + (t1 - t0) * i / samples
            v = f(t)
            track[round(t, 4)] = (float(v[0]), float(v[1]), float(v[2]), e)
        return self

    def add(self, bone, target, f, samples, e='c'):
        """Adds f(t) to the channel: the existing curve (eased keys included) is resampled densely (30 per second at
        least) so fast strikes keep their shape under the added layer (tremble, breathing)."""
        existing = self.channel(bone, target)
        base = (lambda t: ermodel.channel_value(existing, t)) if existing else (
            lambda t: (1.0, 1.0, 1.0) if target == 'scale' else (0.0, 0.0, 0.0))
        n = max(samples, int(math.ceil(self.length * 30)))
        self.tracks.pop((bone, target), None)           # every old key is replaced by the resampled curve
        return self.fn(bone, target, lambda t: tuple(a + b for a, b in zip(base(t), f(t))), n, e)

    def has(self, bone, target='rot'):
        return (bone, target) in self.tracks

    def channel(self, bone, target):
        track = self.tracks.get((bone, target))
        if not track:
            return None
        keys = [(t, v[0], v[1], v[2], v[3]) for t, v in sorted(track.items())]
        return ermodel.Channel(bone, target, keys)

    def value(self, bone, target, t):
        ch = self.channel(bone, target)
        if ch is None:
            return (1.0, 1.0, 1.0) if target == 'scale' else (0.0, 0.0, 0.0)
        tt = (t % self.length) if self.loop else min(max(t, 0.0), self.length)
        return ermodel.channel_value(ch, tt)

    def anim(self):
        """The clip as it stands, as an ermodel.Anim (no secondary pass)."""
        channels = []
        for (bone, target) in sorted(self.tracks):
            channels.append(self.channel(bone, target))
        return ermodel.Anim(self.name, self.length, self.loop, self.impact, channels)

    def pose_at(self, t, anim=None):
        anim = anim or self.anim()
        tt = (t % self.length) if self.loop else min(max(t, 0.0), self.length)
        return ermodel.evaluate([(anim, tt)])

    def finish(self):
        """Every one-shot channel starts and ends where it is keyed; loops get their closing key."""
        for (bone, target), track in self.tracks.items():
            times = sorted(track)
            if self.loop:
                first = track[times[0]]
                if times[0] > 0.0:
                    track[0.0] = track[times[0]]
                    times = sorted(track)
                    first = track[0.0]
                if round(self.length, 4) not in track:
                    track[round(self.length, 4)] = (first[0], first[1], first[2], 'c' if first[3] == 'c' else 's')
            else:
                if times[0] > 0.0:
                    rest = (1.0, 1.0, 1.0) if target == 'scale' else (0.0, 0.0, 0.0)
                    track[0.0] = (rest[0], rest[1], rest[2], 's')
        return self

    def build(self, rig, variant, chains=(), sample_rate=60):
        """Finishes the clip, runs the secondary-motion pass for {@code chains} and returns an ermodel.Anim."""
        self.finish()
        if chains:
            simulate(self, rig, variant, chains, sample_rate)
            floor_clamp(self, rig, variant, chains)
        return self.anim()


def retime(clip, impact):
    """Moves the impact frame of a one-shot clip to {@code impact} seconds: the wind-up before it is compressed (or
    stretched) evenly, everything after it keeps its timing relative to the impact (the clip gets shorter or longer
    by the same amount). Poses, IK and floor contacts are unchanged: only key times move. Used to fit an action into
    the telegraphs of the boss that plays it."""
    old = clip.impact
    if old is None or abs(old - impact) < 1e-9:
        return clip
    shift = impact - old
    k = impact / old

    def warp(t):
        return t * k if t <= old + 1e-9 else t + shift
    for key, track in list(clip.tracks.items()):
        clip.tracks[key] = {round(warp(t), 4): v for t, v in track.items()}
    clip.length = round(clip.length + shift, 4)
    clip.impact = impact
    return clip


# ---------------------------------------------------------------------------------------------- oscillators

def breath(period, amp, phase=0.0):
    """Smooth periodic 0-centred wave (sum of two harmonics for a less mechanical rhythm)."""
    def f(t):
        x = 2.0 * math.pi * (t / period) + phase
        return amp * (0.82 * math.sin(x) + 0.18 * math.sin(2.0 * x + 0.6))
    return f


def shake(freq, amp, seed=0):
    """Deterministic tremble: a few incommensurate sines (for roars and staggers)."""
    def f(t):
        s = seed * 1.7
        return amp * (0.55 * math.sin(2 * math.pi * freq * t + s) + 0.3 * math.sin(2 * math.pi * freq * 1.73 * t + 2.1 + s)
                      + 0.15 * math.sin(2 * math.pi * freq * 2.61 * t + 4.2 + s))
    return f


def envelope(t, t0, t1, fade=0.15):
    """1 inside [t0, t1] with smooth fades of {@code fade} seconds, 0 outside."""
    if t < t0 - fade or t > t1 + fade:
        return 0.0
    if t < t0:
        return smooth((t - (t0 - fade)) / fade)
    if t > t1:
        return 1.0 - smooth((t - t1) / fade)
    return 1.0


# ------------------------------------------------------------------------------------------- rotations

def euler_zyx(m):
    """Angles (x, y, z) in degrees with m = Rz @ Ry @ Rx (ermodel.rot_matrix order)."""
    sy = -m[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    return x / DEG, y / DEG, z / DEG


# -------------------------------------------------------------------------------------------- leg IK

class Leg:
    """A leg of three bones (thigh, shin, foot); the ankle is the foot's pivot. {@code knee} 'front' (biped, hind
    legs) or 'back' (front legs of quadrupeds, whose elbow points back)."""

    def __init__(self, rig, thigh, shin, foot, knee='front'):
        self.thigh, self.shin, self.foot, self.knee = thigh, shin, foot, knee
        hp = np.array(rig.parts[thigh].pivot)
        kp = np.array(rig.parts[shin].pivot)
        ap = np.array(rig.parts[foot].pivot)
        self.l1 = float(np.linalg.norm((kp - hp)[1:]))
        self.l2 = float(np.linalg.norm((ap - kp)[1:]))
        self.rest_ankle = ap
        self.side = float(hp[0])
        self.sole = max(c.y + c.h for c in rig.parts[foot].cubes)    # y of the sole (24 = ground at rest)
        self.rest_thigh = math.atan2(kp[2] - hp[2], kp[1] - hp[1]) / DEG
        self.rest_shin = math.atan2(ap[2] - kp[2], ap[1] - kp[1]) / DEG - self.rest_thigh


def solve_leg(clip, rig, variant, leg, times, ankle_at, foot_pitch_at=None, e='c', floor=True):
    """Keys thigh, shin and foot rotations (x) so that the ankle reaches ankle_at(t) (model space, y down) with the
    sole at the world pitch foot_pitch_at(t) degrees (0 = flat). The body keys must be set first: the hip follows
    them. Existing x rotations of the leg bones are replaced, y/z are kept. With {@code floor}, a foot whose toe or
    heel would dip into the floor (a pitched foot, a kneel on the toes) is raised just enough to rest on it."""
    anim = clip.anim()
    for t in times:
        lift = 0.0
        for _ in range(3 if floor else 1):
            _solve_leg_at(clip, rig, variant, leg, t, anim, ankle_at, foot_pitch_at, e, lift)
            if not floor:
                break
            sink = _box_low(rig, variant, clip.pose_at(t), rig.parts[leg.foot]) - 24.0
            if sink <= 0.05:
                break
            lift += sink
    # drop keys of the leg bones that are not on the IK grid (authored placeholders)
    grid = {round(t, 4) for t in times}
    for bone in (leg.thigh, leg.shin, leg.foot):
        track = clip.tracks.get((bone, 'rot'), {})
        for key in [k for k in track if k not in grid]:
            del track[key]


def _solve_leg_at(clip, rig, variant, leg, t, anim, ankle_at, foot_pitch_at, e, lift):
    parent = rig.parts[leg.thigh].parent.name
    rest_parent = ermodel.part_matrix(rig, variant, {}, parent)
    if True:
        pose = clip.pose_at(t, anim)
        pm = ermodel.part_matrix(rig, variant, pose, parent)
        inv = np.linalg.inv(pm)
        target = np.array(list(ankle_at(t)) + [1.0])
        target[1] -= lift
        local = inv @ target                                  # in the parent's frame, relative to the parent pivot
        hip_local = np.array(rig.parts[leg.thigh].pivot) - np.array(rig.parts[parent].pivot)
        d = local[:3] - hip_local
        dy, dz = d[1], d[2]
        dist = math.hypot(dy, dz)
        reach = (leg.l1 + leg.l2) * 0.999
        dist = min(dist, reach)
        dist = max(dist, abs(leg.l1 - leg.l2) + 1e-3)
        phi = math.atan2(dz, dy)
        cos_b = (leg.l1 ** 2 + dist ** 2 - leg.l2 ** 2) / (2 * leg.l1 * dist)
        cos_g = (leg.l1 ** 2 + leg.l2 ** 2 - dist ** 2) / (2 * leg.l1 * leg.l2)
        beta = math.acos(max(-1.0, min(1.0, cos_b)))
        gamma = math.acos(max(-1.0, min(1.0, cos_g)))
        if leg.knee == 'front':
            th1, th2 = phi - beta, math.pi - gamma
        else:
            th1, th2 = phi + beta, -(math.pi - gamma)
        a1 = th1 / DEG - leg.rest_thigh
        a2 = th2 / DEG - leg.rest_shin
        # parent's world pitch (relative to its rest) so the foot can be kept level in the world
        rel = pm[:3, :3] @ rest_parent[:3, :3].T
        parent_pitch = euler_zyx(rel)[0]
        want = foot_pitch_at(t) if foot_pitch_at else 0.0
        a3 = want - (parent_pitch + a1 + a2)
        for bone, ang in ((leg.thigh, a1), (leg.shin, a2), (leg.foot, a3)):
            old = clip.value(bone, 'rot', t) if clip.has(bone) else (0.0, 0.0, 0.0)
            clip.tracks.setdefault((bone, 'rot'), {})[round(t, 4)] = (ang, old[1], old[2], e)


class Gait:
    """A looping walk: every foot is planted for {@code duty} of the cycle and slides back by {@code stride} model
    units at constant speed (so it does not skate when the game moves the body at the matching speed, see
    {@link #walk_speed}), then swings forward with a lift arc. {@code phases}: the cycle fraction at which each leg's
    foot strikes the ground."""

    def __init__(self, length, stride, duty=0.62, lift=4.0, heel=12.0, toe=18.0):
        self.length, self.stride, self.duty, self.lift, self.heel, self.toe = length, stride, duty, lift, heel, toe

    def walk_speed(self):
        """Value for the walk animation's speed in BossModel: 80 / (model units per second of a planted foot)."""
        ground = self.stride / (self.duty * self.length)
        return 80.0 / ground

    def ankle(self, leg, phase, forward=0.0, lift=None, out=0.0):
        """Ankle path (model space) of a leg whose foot strikes at cycle fraction {@code phase}; {@code forward}
        shifts the whole path (model z), {@code out} spreads the feet sideways."""
        lift = self.lift if lift is None else lift
        z0 = leg.rest_ankle[2] + forward
        y0 = leg.rest_ankle[1]
        x0 = leg.rest_ankle[0] + out * (1 if leg.side > 0 else -1)
        half = self.stride / 2.0

        def at(t):
            u = ((t / self.length) - phase) % 1.0
            if u < self.duty:                                   # planted: slides back at ground speed
                s = u / self.duty
                return (x0, y0, z0 - half + self.stride * s)
            s = (u - self.duty) / (1.0 - self.duty)             # swing: forward with an arc
            z = z0 + half - self.stride * smooth(s)
            y = y0 - lift * (math.sin(math.pi * s) ** 0.85)
            return (x0, y, z)
        return at

    def foot_pitch(self, phase):
        """Heel strike with the toes up, flat while planted, heel off before the toe leaves, toes trail in swing."""
        def at(t):
            u = ((t / self.length) - phase) % 1.0
            if u < 0.08:
                return -self.heel * (1.0 - u / 0.08)
            if u < self.duty - 0.12:
                return 0.0
            if u < self.duty:
                return self.toe * smooth((u - (self.duty - 0.12)) / 0.12)
            s = (u - self.duty) / (1.0 - self.duty)
            return lerp(self.toe, -self.heel * 0.6, smooth(s))
        return at

    def strikes(self, phases):
        return [round((p % 1.0) * self.length, 4) for p in phases]


def plant(clip, rig, variant, leg, ankle_at, pitch_at=None, samples=None, t0=0.0, t1=None, e='c'):
    """Keys a leg by IK over [t0, t1] (one-shots: stances, kneels, lunges). Besides a regular grid, the leg is solved
    at every key time of the body's own channels, so a foot stays planted through a sudden drop (an impact frame)."""
    t1 = clip.length if t1 is None else t1
    samples = samples or max(8, int((t1 - t0) * 12))
    times = [t0 + (t1 - t0) * i / samples for i in range(samples + 1)]
    legs = {leg.thigh, leg.shin, leg.foot}
    events = sorted({k for (bone, target), track in clip.tracks.items() if bone not in legs and target != 'scale'
                     for k in track if t0 < k < t1 and len(track) <= 16})
    grid = (t1 - t0) / samples
    for k in events:
        if min(abs(k - t) for t in times) > 0.25 * grid:
            times.append(k)
    solve_leg(clip, rig, variant, leg, sorted(times), ankle_at, pitch_at, e)


def planted(leg, forward=0.0, out=0.0, lift=0.0):
    """Ankle target of a foot that stays where it stands (relative to its rest place)."""
    p = leg.rest_ankle
    return lambda t: (p[0] + out * (1 if leg.side > 0 else -1), p[1] - lift, p[2] + forward)


def path(points, e='s'):
    """Ankle path through (t, (x, y, z)) points with eased segments (for steps in one-shot clips)."""
    points = sorted(points, key=lambda p: p[0])

    def at(t):
        if t <= points[0][0]:
            return points[0][1]
        for (ta, pa), (tb, pb) in zip(points, points[1:]):
            if t <= tb:
                a = (t - ta) / max(1e-6, tb - ta)
                f = ermodel.ease(e, a) if e != 'c' else smooth(a)
                return lerp3(pa, pb, f)
        return points[-1][1]
    return at


def steps(leg, plan, start=(0.0, 0.0), y_off=0.0):
    """Ankle path of a foot that takes the steps of {@code plan} one after another and stays planted in between:
    entries (t0, t1, (forward, out), lift) where forward is a model z offset from the rest place (negative = to the
    front) and out a sideways offset away from the body. {@code y_off}: extra height (negative = up, e.g. kneeling
    feet resting on their toes)."""
    p = leg.rest_ankle
    side = 1 if leg.side > 0 else -1

    def at(t):
        cur = start
        for (t0, t1, to, lift) in plan:
            if t < t0:
                break
            if t <= t1:
                s = (t - t0) / max(1e-6, t1 - t0)
                f = smooth(s)
                z = lerp(cur[0], to[0], f)
                o = lerp(cur[1], to[1], f)
                return (p[0] + o * side, p[1] + y_off - lift * math.sin(math.pi * s), p[2] + z)
            cur = to
        return (p[0] + cur[1] * side, p[1] + y_off, p[2] + cur[0])
    return at


def step_pitch(plan, lift_pitch=18.0, rest=0.0, holds=()):
    """Foot pitch for {@code steps}: toes dip while the foot is in the air, flat when planted; {@code holds}:
    (t0, t1, pitch) ranges with a held pitch (toes on the floor when kneeling)."""
    def at(t):
        for (t0, t1, pitch) in holds:
            if t0 <= t <= t1:
                return pitch
        for (t0, t1, to, lift) in plan:
            if t0 <= t <= t1 and lift > 0:
                return rest + lift_pitch * math.sin(math.pi * (t - t0) / max(1e-6, t1 - t0))
        return rest
    return at


def eased_value(keys, t):
    """Value of a scalar through (t, value, ease) keys (helper for procedural channels)."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (ta, va, _), (tb, vb, e) in zip(keys, keys[1:]):
        if t <= tb:
            a = (t - ta) / max(1e-6, tb - ta)
            return va + (vb - va) * ermodel.ease(e if e != 'c' else 's', a)
    return keys[-1][1]


def lifted_step(leg, t0, t1, d_from, d_to, lift, out_from=0.0, out_to=0.0):
    """A single step: the ankle travels from rest+d_from to rest+d_to (model z offsets) between t0 and t1 with an
    arc of {@code lift} units; planted before and after."""
    p = leg.rest_ankle
    sx = 1 if leg.side > 0 else -1

    def at(t):
        if t <= t0:
            return (p[0] + out_from * sx, p[1], p[2] + d_from)
        if t >= t1:
            return (p[0] + out_to * sx, p[1], p[2] + d_to)
        s = (t - t0) / (t1 - t0)
        f = smooth(s)
        return (p[0] + lerp(out_from, out_to, f) * sx, p[1] - lift * math.sin(math.pi * s), p[2] + lerp(d_from, d_to, f))
    return at


# ------------------------------------------------------------------------------------------ secondary motion

class Chain:
    """Bones hanging or trailing from a primary bone, each the child of the previous one.

    rest:    'down' (cloth, hair, vines: gravity pulls them straight), 'back' (tails), 'side' (wing tips), 'up'
    axes:    rotation axes that are simulated ('x' pitch, 'y' yaw, 'z' roll)
    hang:    0..1, how strongly gravity keeps each segment at its rest orientation in the world (1 for cloth)
    freq:    natural frequency (Hz) of the first segment; later segments get {@code falloff} times it
    damp:    damping ratio (0.2 floppy .. 0.8 heavy)
    inertia: how far (degrees per 1000 units/s^2) the attachment's acceleration throws the segments back
    limits:  per axis (min, max) degrees of the simulated offset (keeps cloth out of the body)
    """

    def __init__(self, bones, rest='down', axes='xz', hang=0.9, freq=1.6, damp=0.35, inertia=6.0, falloff=1.18,
                 limits=None, gain=1.0, drag=None):
        self.bones = list(bones)
        self.rest, self.axes, self.hang, self.freq, self.damp = rest, axes, hang, freq, damp
        self.inertia, self.falloff, self.gain = inertia, falloff, gain
        self.limits = limits or {}
        self.drag = drag or {}       # clip name -> (x, y, z) constant world offset (wind, running drag)


def _rest_dir(rest):
    return {'down': np.array([0.0, 1.0, 0.0]), 'up': np.array([0.0, -1.0, 0.0]),
            'back': np.array([0.0, 0.0, 1.0]), 'side': np.array([1.0, 0.0, 0.0])}[rest]


def _axis_gain(d, axis):
    """Tip displacement direction per unit rotation about {@code axis} of a segment pointing along d."""
    if axis == 'x':
        return np.array([0.0, -d[2], d[1]])
    if axis == 'y':
        return np.array([d[2], 0.0, -d[0]])
    return np.array([-d[1], d[0], 0.0])


def simulate(clip, rig, variant, chains, rate=60):
    """Replaces the rotation channels of every chain bone by the spring response to the clip's primary motion."""
    anim = clip.anim()
    length = clip.length
    dt = 1.0 / rate
    cycles = 4 if clip.loop else 1
    lead = 0.0 if clip.loop else 0.0
    n = int(round(length * rate))
    total = n * cycles + 1
    times = [i * dt for i in range(total)]
    chain_bones = {b for ch in chains for b in ch.bones}
    # primary pose without the chain bones' own keys (those become the authored offsets)
    primary = ermodel.Anim(anim.name, anim.length, anim.loop, anim.impact,
                           [c for c in anim.channels if not (c.bone in chain_bones and c.target == 'rot')])
    authored = {c.bone: c for c in anim.channels if c.bone in chain_bones and c.target == 'rot'}
    poses = []
    for t in times:
        tt = (t % length) if clip.loop else min(t, length)
        poses.append(ermodel.evaluate([(primary, tt)]))
    for ch in chains:
        attach = rig.parts[ch.bones[0]].parent.name
        rest_m = ermodel.part_matrix(rig, variant, {}, attach)
        att_pt = rig.parts[ch.bones[0]].pivot
        angles = np.zeros((total, 3))
        points = np.zeros((total, 3))
        for i, pose in enumerate(poses):
            m = ermodel.part_matrix(rig, variant, pose, attach)
            rel = m[:3, :3] @ rest_m[:3, :3].T
            angles[i] = euler_zyx(rel)
            local = np.array([att_pt[0] - rig.parts[attach].pivot[0], att_pt[1] - rig.parts[attach].pivot[1],
                              att_pt[2] - rig.parts[attach].pivot[2], 1.0])
            points[i] = (m @ local)[:3]
        # unwrap angles (euler jumps)
        for a in range(3):
            angles[:, a] = np.degrees(np.unwrap(np.radians(angles[:, a])))
        vel = np.gradient(points, dt, axis=0)
        acc = np.gradient(vel, dt, axis=0)
        k = max(1, int(rate * 0.05))
        kernel = np.ones(2 * k + 1) / (2 * k + 1)
        for a in range(3):
            acc[:, a] = np.convolve(np.pad(acc[:, a], k, mode='edge'), kernel, mode='valid')
        d = _rest_dir(ch.rest)
        parent_off = {ax: angles[:, 'xyz'.index(ax)].copy() for ax in 'xyz'}
        drag = ch.drag.get(clip.name, (0.0, 0.0, 0.0))
        outputs = {}
        for s, bone in enumerate(ch.bones):
            freq = ch.freq * (ch.falloff ** s)
            w = 2.0 * math.pi * freq
            out = {}
            for ax in 'xyz':
                idx = 'xyz'.index(ax)
                auth = np.array([(ermodel.channel_value(authored[bone], (t % length) if clip.loop else min(t, length))[idx]
                                  if bone in authored else 0.0) for t in times])
                if ax not in ch.axes:
                    out[ax] = auth
                    continue
                hang = ch.hang if (ax != 'y' and ch.rest in ('down', 'up')) else ch.hang * 0.3
                P = parent_off[ax]
                W = (1.0 - hang) * P + auth + drag[idx]
                g = _axis_gain(d, ax)
                W = W + ch.inertia * 1e-3 * (-(acc @ g)) * (1.0 + 0.25 * s)
                x = W[0]
                v = 0.0
                res = np.zeros(total)
                for i in range(total):
                    v += (w * w * (W[i] - x) - 2.0 * ch.damp * w * v) * dt
                    x += v * dt
                    res[i] = x
                o = (res - P) * ch.gain + auth * (1.0 - ch.gain)
                lo, hi = ch.limits.get(ax, (-170.0, 170.0))
                o = np.clip(o, lo, hi)
                out[ax] = o
            outputs[bone] = out
            for ax in 'xyz':
                parent_off[ax] = parent_off[ax] + out[ax]
        # write keys
        for bone, out in outputs.items():
            if clip.loop:
                start = (cycles - 1) * n
                seg = {ax: out[ax][start:start + n + 1].copy() for ax in 'xyz'}
                for ax in 'xyz':                       # close the loop: spread the seam error over the cycle
                    err = seg[ax][-1] - seg[ax][0]
                    seg[ax] -= err * np.linspace(0.0, 1.0, n + 1)
                ts = np.linspace(0.0, length, n + 1)
            else:
                seg = {ax: out[ax][:n + 1].copy() for ax in 'xyz'}
                ts = np.array(times[:n + 1])
                # settle on the authored end pose over the last part of the clip
                tail = max(0.12, min(0.5, length * 0.18))
                for ax in 'xyz':
                    end = (ermodel.channel_value(authored[bone], length)['xyz'.index(ax)]
                           if bone in authored else 0.0)
                    blend = np.array([smooth((t - (length - tail)) / tail) for t in ts])
                    seg[ax] = seg[ax] * (1.0 - blend) + end * blend
            step = key_step(length)
            m = max(2, int(round(length / step)))
            track = {}
            for j in range(m + 1):
                t = length * j / m
                i = min(n, int(round(t * rate)))
                track[round(t, 4)] = (float(seg['x'][i]), float(seg['y'][i]), float(seg['z'][i]), 'c')
            clip.tracks[(bone, 'rot')] = track


def _box_low(rig, variant, pose, part):
    """Lowest model y of the boxes of one part in a pose."""
    m = ermodel.part_matrix(rig, variant, pose, part.name)
    low = -1e9
    for c in part.cubes:
        for dx in (0, c.w):
            for dy in (0, c.h):
                for dz in (0, c.d):
                    q = m @ np.array([c.x + dx - part.pivot[0], c.y + dy - part.pivot[1], c.z + dz - part.pivot[2], 1.0])
                    low = max(low, q[1])
    return low


def floor_clamp(clip, rig, variant, chains, floor=24.0, clearance=1.0):
    """Keeps chain segments (tails, capes, trains, vines) out of the floor: at every key, a segment whose boxes sink
    into it is turned about its main axis just enough to lie on it, from the chain's root outwards (so a tail that
    sweeps down lies along the ground instead of through it)."""
    anim = clip.anim()
    for ch in chains:
        axis = 'z' if ch.rest == 'side' else 'x'
        idx = 'xyz'.index(axis)
        tracks = [clip.tracks.get((b, 'rot')) for b in ch.bones]
        if not all(tracks):
            continue
        for t in sorted(tracks[0].keys()):
            pose = ermodel.evaluate([(anim, min(t, clip.length - 1e-6) if clip.loop else t)])
            changed = False
            for bone, track in zip(ch.bones, tracks):
                part = rig.parts[bone]
                if not part.cubes or not ermodel.visible_chain(rig, variant, part) or t not in track:
                    continue
                slot = pose.setdefault(bone, {'rot': [0.0, 0.0, 0.0], 'pos': [0.0, 0.0, 0.0], 'scale': [0.0, 0.0, 0.0]})
                base = slot['rot'][idx]
                limit = floor - clearance

                def sink(a):
                    slot['rot'][idx] = a
                    return _box_low(rig, variant, pose, part) - limit
                if sink(base) <= 0.0:
                    slot['rot'][idx] = base
                    continue
                # nearest angle (either way) at which the segment clears the floor
                best = None
                for step in range(1, 61):
                    for sign in (1.0, -1.0):
                        a = base + sign * 2.0 * step
                        if sink(a) <= 0.0:
                            best = a
                            break
                    if best is not None:
                        break
                if best is None:
                    slot['rot'][idx] = base
                    continue
                lo, hi = (best - 2.0, best) if best > base else (best, best + 2.0)
                for _ in range(14):
                    mid = (lo + hi) / 2.0
                    inside = sink(mid) > 0.0
                    if best > base:
                        lo, hi = (mid, hi) if inside else (lo, mid)
                    else:
                        lo, hi = (lo, mid) if inside else (mid, hi)
                a = hi if best > base else lo
                slot['rot'][idx] = a
                key = list(track[t])
                key[idx] = key[idx] + (a - base)
                track[t] = tuple(key)
                changed = True
            if changed and clip.loop and t == 0.0 and clip.length in tracks[0]:
                for track in tracks:
                    if 0.0 in track and clip.length in track:
                        track[clip.length] = track[0.0]


def key_step(length):
    return 1.0 / 12.0 if length <= 3.0 else 1.0 / 10.0


# ------------------------------------------------------------------------------------------- inspection

def lowest(rig, variant, pose, skip=()):
    """Largest model y (lowest point) of the visible boxes of a pose."""
    mats = ermodel.part_transforms(rig, variant, pose)
    low = -1e9
    for part in rig.parts.values():
        if part.name in skip or not ermodel.visible_chain(rig, variant, part):
            continue
        m = mats[part.name]
        for c in part.cubes:
            for dx in (0, c.w):
                for dy in (0, c.h):
                    for dz in (0, c.d):
                        q = m @ np.array([c.x + dx - part.pivot[0], c.y + dy - part.pivot[1], c.z + dz - part.pivot[2],
                                          1.0])
                        low = max(low, q[1])
    return low


def ground(clip, rig, variant, t, skip=(), e=None, bone='base', clearance=0.0):
    """Re-keys the root's height at {@code t} so that the lowest box just touches the floor (y = 24): falls, kneels."""
    old = clip.value(bone, 'pos', t)
    code = e or clip.tracks.get((bone, 'pos'), {}).get(round(t, 4), (0, 0, 0, 's'))[3]
    clip.k(bone, t, pos=(old[0], old[1], old[2]), e=code)
    pose = clip.pose_at(t)
    low = lowest(rig, variant, pose, skip)
    clip.k(bone, t, pos=(old[0], old[1] + (low - 24.0) - clearance, old[2]), e=code)


def rest_on_floor(clip, rig, variant, times, bone, axis='x', clearance=0.5, span=90.0):
    """Turns {@code bone} about {@code axis} at each of {@code times} (keys are added) just enough that its boxes
    and those of its children stay out of the floor: a dropped weapon lies on the ground instead of in it."""
    idx = 'xyz'.index(axis)
    parts = [p for p in rig.parts[bone].walk() if p.cubes and ermodel.visible_chain(rig, variant, p)]
    for t in times:
        old = list(clip.value(bone, 'rot', t))
        track = clip.tracks.setdefault((bone, 'rot'), {})
        had = track.get(round(t, 4))
        code = had[3] if had else 'c'

        def sink(a):
            v = list(old)
            v[idx] = a
            clip.k(bone, t, rot=tuple(v), e=code)
            pose = clip.pose_at(t)
            return max(_box_low(rig, variant, pose, p) for p in parts) - (24.0 - clearance)
        base = old[idx]
        if sink(base) <= 0.0:
            if had:
                track[round(t, 4)] = had
            else:
                del track[round(t, 4)]
                if not track:
                    del clip.tracks[(bone, 'rot')]
            continue
        best = None
        for step in range(1, int(span / 2) + 1):
            for sign in (1.0, -1.0):
                if sink(base + sign * 2.0 * step) <= 0.0:
                    best = base + sign * 2.0 * step
                    break
            if best is not None:
                break
        if best is None:
            sink(base)
            continue
        inside, outside = best - (2.0 if best > base else -2.0), best
        for _ in range(14):
            mid = (inside + outside) / 2.0
            if sink(mid) > 0.0:
                inside = mid
            else:
                outside = mid
        sink(outside)


def unsink(clip, rig, variant, t0, t1, skip=(), bone='base', rate=30):
    """Raises the root over [t0, t1] wherever the body would sink into the floor (falls, kneels, crashes): the
    correction is the penetration itself (moving the root moves everything), eased in and out of the range."""
    n = max(2, int(math.ceil((t1 - t0) * rate)))
    times = [t0 + (t1 - t0) * i / n for i in range(n + 1)]
    anim = clip.anim()
    sink = [max(0.0, lowest(rig, variant, ermodel.evaluate([(anim, t)]), skip) - 24.0) for t in times]
    # widen each correction a little so the interpolated curve between samples stays out of the floor too
    wide = [max(sink[max(0, i - 1):i + 2]) for i in range(len(sink))]

    def corr(t):
        if t <= t0 or t >= t1:
            return 0.0
        i = min(n - 1, int((t - t0) / (t1 - t0) * n))
        a = (t - times[i]) / max(1e-6, times[i + 1] - times[i])
        return wide[i] + (wide[i + 1] - wide[i]) * a
    if max(sink) > 0.0:
        clip.add(bone, 'pos', lambda t: (0.0, corr(t), 0.0), int(clip.length * rate))


def _solve(f, lo, hi, iterations=28, scan=72):
    """Root of f in [lo, hi] (or the closest approach): coarse scan, then bisection inside the best bracket."""
    xs = [lo + (hi - lo) * i / scan for i in range(scan + 1)]
    fs = [f(a) for a in xs]
    best = min(range(len(xs)), key=lambda i: abs(fs[i]))
    bracket = None
    for i in range(scan):
        if fs[i] == 0 or fs[i] * fs[i + 1] < 0:
            if bracket is None or abs(i - best) < abs(bracket - best):
                bracket = i
    if bracket is None:
        f(xs[best])
        return xs[best]
    a, b, fa = xs[bracket], xs[bracket + 1], fs[bracket]
    for _ in range(iterations):
        m = (a + b) / 2.0
        fm = f(m)
        if fa * fm <= 0:
            b = m
        else:
            a, fa = m, fm
    f((a + b) / 2.0)
    return (a + b) / 2.0


def aim(clip, rig, variant, t, bone, axis, part, base, tip, elevation, lo, hi, iterations=28, forward=False):
    """Sets the {@code axis} rotation of {@code bone} at key {@code t} so that the segment base->tip of {@code part}
    points {@code elevation} degrees above the horizon (0 = a level blade); the closest reachable angle otherwise.
    With {@code forward} the segment must point to the front (a levelled scepter, a thrust), not back over the
    shoulder: the angle is measured in the side view from the facing direction."""
    idx = 'xyz'.index(axis)
    old = list(clip.value(bone, 'rot', t))
    code = clip.tracks.get((bone, 'rot'), {}).get(round(t, 4), (0, 0, 0, 's'))[3]

    def angle(a):
        v = list(old)
        v[idx] = a
        clip.k(bone, t, rot=tuple(v), e=code)
        pose = clip.pose_at(t)
        d = ermodel.part_point(rig, variant, pose, part, tip) - ermodel.part_point(rig, variant, pose, part, base)
        if forward:
            return math.degrees(math.atan2(-d[1], -d[2])) - elevation
        return math.degrees(math.atan2(-d[1], math.hypot(d[0], d[2]))) - elevation
    return _solve(angle, lo, hi, iterations)


def reach_y(clip, rig, variant, t, bone, axis, part, point, target_y, lo, hi, iterations=28):
    """Sets the {@code axis} rotation of {@code bone} at key {@code t} so that {@code point} of {@code part} reaches
    model height {@code target_y} (a blade touching the floor at the impact frame); the closest angle otherwise."""
    idx = 'xyz'.index(axis)
    old = list(clip.value(bone, 'rot', t))
    code = clip.tracks.get((bone, 'rot'), {}).get(round(t, 4), (0, 0, 0, 's'))[3]

    def height(a):
        v = list(old)
        v[idx] = a
        clip.k(bone, t, rot=tuple(v), e=code)
        return ermodel.part_point(rig, variant, clip.pose_at(t), part, point)[1] - target_y
    return _solve(height, lo, hi, iterations)


def world_point(rig, variant, clip_or_anim, t, part, point):
    anim = clip_or_anim.anim() if isinstance(clip_or_anim, Clip) else clip_or_anim
    tt = (t % anim.length) if anim.loop else min(max(t, 0.0), anim.length)
    pose = ermodel.evaluate([(anim, tt)])
    return ermodel.part_point(rig, variant, pose, part, point)
