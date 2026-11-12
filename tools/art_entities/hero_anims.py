"""Every animation of the four travellers (rig_hero.py). Authored pose to pose (rig_hero.posed): each key pose is a
full pose of the bones that move, eased per segment (vanilla Catmull-Rom 'c' by default; 'O' quartic-out snaps a
blow home, 'I' quartic-in winds up, 'b' overshoots and settles, 'l' linear, 's' smooth in-out).

Conventions (vanilla model space, see ermodel): an arm's x rotation below zero swings it forward and up (-90 points
it ahead, -180 straight up); z rotation moves the right arm out to the side when positive, the left arm when
negative; a forearm's x rotation below zero bends the elbow; a shin's x rotation above zero bends the knee back; the
chest's x rotation above zero leans the body forward; base 'pos' y above zero lifts the whole body.
Held props point ahead of the fist (swords) or stand along the forearm (staves): an arm pitched by -90 holds a
sword upright.

Returns the animations and HITS: the moments (seconds) the blows of each one-shot land; the server lands its hits
then (HeroBodies.hits).
"""
from rig_hero import blink, flaps, merge, posed, shout, shut, spin, wave

C = 'c'
O = 'O'
I_ = 'I'
B = 'b'
L_ = 'l'
S = 's'


def mirror(pose):
    """Swap left and right (the right arm's rotations become the left's: y and z flip)."""
    out = {}
    swap = {'right_arm': 'left_arm', 'left_arm': 'right_arm', 'right_forearm': 'left_forearm',
            'left_forearm': 'right_forearm', 'right_leg': 'left_leg', 'left_leg': 'right_leg',
            'right_shin': 'left_shin', 'left_shin': 'right_shin'}
    for bone, v in pose.items():
        rot = v if not isinstance(v, dict) else v.get('rot', (0, 0, 0))
        out[swap.get(bone, bone)] = (rot[0], -rot[1], -rot[2])
    return out


def merge_poses(*poses):
    out = {}
    for pose in poses:
        out.update(pose)
    return out


def shift(pose, dy=0.0, dz=0.0):
    """The pose with its whole body moved (base pos added: up, forward when dz < 0)."""
    out = dict(pose)
    base = out.get('base', {})
    pos = base.get('pos', (0.0, 0.0, 0.0)) if isinstance(base, dict) else (0.0, 0.0, 0.0)
    out['base'] = dict(pos=(pos[0], pos[1] + dy, pos[2] + dz))
    return out


def breathing(name, length, pose, frames_extra=None, **extra):
    """A held pose that breathes (the chest rises and falls once per loop)."""
    chest = pose.get('chest', (0, 0, 0))
    chest = chest if not isinstance(chest, dict) else chest.get('rot', (0, 0, 0))
    inhale = merge_poses(pose, {'chest': (chest[0] - 2.0, chest[1], chest[2])})
    return posed(name, length, [(0.0, pose, C), (length / 2.0, inhale, C), (length, pose, C)], loop=True, **extra)


def crouch(depth):
    """Both knees bent by `depth` (0..1): thighs forward, shins back, the body lowered so the feet stay down."""
    import math
    a = -50.0 * depth
    b = 95.0 * depth
    drop = 6.0 * (1 - math.cos(math.radians(a))) + 6.0 * (1 - math.cos(math.radians(a + b)))
    return {'right_leg': (a, 0, 0), 'left_leg': (a, 0, 0), 'right_shin': (b, 0, 0), 'left_shin': (b, 0, 0),
            'base': dict(pos=(0, -drop, 0))}


def stance(front='left', depth=0.5, spread=10.0):
    """A fighting stance: one foot forward, knees bent, the body lowered."""
    import math
    fa, fb = -38.0 * depth - 8.0, 52.0 * depth
    ba, bb = 26.0 * depth, 34.0 * depth
    drop = 6.0 * (1 - math.cos(math.radians(fa))) + 6.0 * (1 - math.cos(math.radians(fa + fb)))
    drop = min(drop, 6.0 * (1 - math.cos(math.radians(ba))) + 6.0 * (1 - math.cos(math.radians(ba + bb)))) + 0.3
    f, b = ('left', 'right') if front == 'left' else ('right', 'left')
    sz = -spread if f == 'left' else spread
    return {'%s_leg' % f: (fa, 0, sz * 0.4), '%s_shin' % f: (fb, 0, 0), '%s_leg' % b: (ba, 0, -sz * 0.4),
            '%s_shin' % b: (bb, 0, 0), 'base': dict(pos=(0, -drop, 0))}


def kneel(knee='left'):
    """Down on one knee (the other foot ahead)."""
    up, down = ('right', 'left') if knee == 'left' else ('left', 'right')
    return {'%s_leg' % up: (-82, 0, 0), '%s_shin' % up: (84, 0, 0), '%s_leg' % down: (6, 0, 0),
            '%s_shin' % down: (84, 0, 0), 'base': dict(pos=(0, -6.6, 0))}


def breathe(length, amp=1.2):
    return dict(rot=merge(wave(length, -amp, length, axis=0, offset=-0.4), wave(length, 0.5, length / 2.0, phase=1.0,
                                                                                axis=2)))


def cloth(length, period, amp, offset=0.0, axis=0, phase=0.0):
    return dict(rot=wave(length, amp, period, phase=phase, axis=axis, offset=offset))


def hair_sway(length, period=3.0, amp=1.5):
    return dict(hair_long=cloth(length, period, amp, offset=0.5, phase=1.2),
                hair_long2=cloth(length, period, amp * 1.6, offset=1.0, phase=0.6),
                lock_r=dict(rot=wave(length, amp, period, phase=0.4, axis=2)),
                lock_l=dict(rot=wave(length, -amp, period, phase=0.4, axis=2)),
                lock_r2=dict(rot=wave(length, amp * 1.5, period, phase=0.1, axis=2)),
                lock_l2=dict(rot=wave(length, -amp * 1.5, period, phase=0.1, axis=2)),
                tail=cloth(length, period, amp, phase=0.9),
                tail2=dict(rot=merge(wave(length, amp * 2.0, period, phase=0.3, axis=0, offset=1.0),
                                     wave(length, amp * 2.5, period * 2, phase=0.7, axis=2))))


def skirt_sway(length, period=3.0, amp=1.0):
    return dict(dress2=dict(rot=merge(wave(length, amp, period * 2, phase=1.0, axis=2),
                                      wave(length, amp * 0.6, period, phase=0.5, axis=0))),
                robe2=dict(rot=merge(wave(length, amp, period * 2, phase=1.0, axis=2),
                                     wave(length, amp * 0.6, period, phase=0.5, axis=0))),
                cape=cloth(length, period, amp * 2.0, offset=1.0, phase=0.8),
                cape2=cloth(length, period, amp * 3.0, offset=2.0, phase=0.3),
                veil=cloth(length, period, amp * 1.2, offset=0.5, phase=0.8),
                veil2=cloth(length, period, amp * 2.0, offset=1.0, phase=0.3),
                tabard_f=cloth(length, period, amp * 1.5, offset=-0.5, phase=0.5),
                tabard_b=cloth(length, period, amp * 2.0, offset=1.0, phase=0.5),
                coat_tail_r=dict(rot=merge(wave(length, amp * 2.2, period, phase=0.3, axis=0, offset=1.5),
                                           wave(length, amp * 1.5, period * 2, phase=1.1, axis=2))),
                coat_tail_l=dict(rot=merge(wave(length, amp * 2.0, period, phase=0.9, axis=0, offset=1.0),
                                           wave(length, -amp * 1.5, period * 2, phase=0.2, axis=2))),
                coat_flap_r=cloth(length, period, amp * 1.4, offset=-0.5, phase=0.6),
                coat_flap_l=cloth(length, period, amp * 1.4, offset=-0.5, phase=1.4),
                hat_cone2=cloth(length, period, amp * 1.5, axis=2, phase=0.2),
                hat_tip=dict(rot=merge(wave(length, amp * 3.0, period, phase=0.6, axis=2),
                                       wave(length, amp * 2.0, period, phase=1.1, axis=0))),
                hat_tip2=dict(rot=merge(wave(length, amp * 5.0, period, phase=0.9, axis=2),
                                        wave(length, amp * 3.0, period, phase=1.5, axis=0))),
                hat_charm=dict(rot=wave(length, amp * 6.0, period / 2.0, phase=0.3, axis=0)),
                staff_ribbon=dict(rot=merge(wave(length, amp * 6.0, period, phase=0.2, axis=2),
                                            wave(length, amp * 4.0, period / 2.0, axis=0, offset=4.0))))


def props_idle(length):
    """The ever-moving props: the witch's rings and grimoire, the sister's halo and tear."""
    return dict(ring_a=dict(rot=spin(length, 2, turns=length / 3.0), lin=True),
                ring_b=dict(rot=spin(length, 0, turns=-length / 4.0), lin=True),
                ring_c=dict(rot=spin(length, 1, turns=length / 6.0), lin=True),
                staff_core=dict(scale=[(t, s, s, s) for t, s in ((0, 1.0), (length * 0.25, 1.12), (length * 0.5, 1.0),
                                                                    (length * 0.75, 1.12), (length, 1.0))]),
                grimoire=dict(pos=wave(length, 0.7, length / 2.0, axis=1), rot=merge(
                    wave(length, 4.0, length, phase=1.0, axis=1), wave(length, 2.0, length / 2.0, phase=0.4, axis=2))),
                halo=dict(rot=spin(length, 2, turns=length / 12.0), lin=True),
                staff_tear=dict(pos=wave(length, 0.35, length / 2.0, axis=1), rot=spin(length, 1, turns=length / 4.0)),
                phantoms=dict(pos=wave(length, 0.4, length / 2.0, axis=1)))


def page_flip(t0, length, dur=0.5):
    return dict(grimoire_page=dict(rot=[(0, 0, 0, 0), (t0, 0, 0, 0), (t0 + dur, 0, 0, -170), (t0 + dur + 0.05, 0, 0, 0),
                                        (length, 0, 0, 0)], lin=True))


def merge_extra(*dicts):
    out = {}
    for d in dicts:
        for bone, spec in d.items():
            if bone in out:
                raise ValueError('channel for %s twice' % bone)
            out[bone] = spec
    return out


def ambient(length, hair=1.5, cloth_amp=1.0, period=3.0, props=True):
    """Hair, cloth and props in motion: added to every loop."""
    d = merge_extra(hair_sway(length, period, hair), skirt_sway(length, period, cloth_amp))
    if props:
        d = merge_extra(d, props_idle(length))
    return d


# ===================================================================================================== walk/run

def walk_cycle(name, length=1.0, arm_r=16.0, arm_l=16.0, lean=2.0, bob=0.55, stride=24.0, extra_pose=None,
               ambient_extra=None):
    """A walk (or, faster and leaning, a run). Arms swing against the legs; the prop arm swings less."""
    h = length / 2.0
    q = length / 4.0
    knee = 38.0 if stride < 30 else 70.0

    def at(phase):
        s = 1.0 if phase == 0 else -1.0
        pose = {
            'right_leg': (stride * s, 0, 0), 'left_leg': (-stride * s, 0, 0),
            'right_arm': (-arm_r * s, 0, 0), 'left_arm': (arm_l * s, 0, 0),
            'right_forearm': (-8 - (6 if s > 0 else 0), 0, 0), 'left_forearm': (-8 - (6 if s < 0 else 0), 0, 0),
            'hips': (0, 4 * s, 0), 'chest': (lean, -5 * s, 0), 'head': (-lean * 0.5, 4 * s, 0),
        }
        if extra_pose:
            for bone, v in extra_pose.items():
                r = pose.get(bone, (0, 0, 0))
                pose[bone] = (r[0] + v[0], r[1] + v[1], r[2] + v[2])
        return pose

    a = at(0)
    b = at(1)
    frames = [(0.0, a, C), (h, b, C), (length, a, C)]
    extra = {
        'right_shin': dict(rot=[(0, 10, 0, 0), (q, knee, 0, 0), (h, 4, 0, 0), (h + q, 6, 0, 0), (length, 10, 0, 0)]),
        'left_shin': dict(rot=[(0, 4, 0, 0), (q, 6, 0, 0), (h, 10, 0, 0), (h + q, knee, 0, 0), (length, 4, 0, 0)]),
        'base': dict(pos=[(0, 0, 0, 0), (q, 0, bob, 0), (h, 0, 0, 0), (h + q, 0, bob, 0), (length, 0, 0, 0)]),
        'cape': dict(rot=[(0, 10 + lean * 3, 0, 0), (h, 15 + lean * 3, 0, 0), (length, 10 + lean * 3, 0, 0)]),
        'cape2': dict(rot=[(0, 8 + lean * 2, 0, 0), (q, 14 + lean * 2, 0, 0), (h, 8 + lean * 2, 0, 0),
                           (h + q, 14 + lean * 2, 0, 0), (length, 8 + lean * 2, 0, 0)]),
        'hair_long': dict(rot=[(0, 4 + lean, 0, 0), (h, 8 + lean, 0, 0), (length, 4 + lean, 0, 0)]),
        'hair_long2': dict(rot=[(0, 3 + lean, 0, 0), (q, 9 + lean, 0, 0), (h, 3 + lean, 0, 0), (h + q, 9 + lean, 0, 0),
                                (length, 3 + lean, 0, 0)]),
        'tail2': dict(rot=[(0, 12 + lean * 2, 0, 8), (h, 18 + lean * 2, 0, -8), (length, 12 + lean * 2, 0, 8)]),
        'veil': dict(rot=[(0, 6, 0, 0), (h, 10, 0, 0), (length, 6, 0, 0)]),
        'veil2': dict(rot=[(0, 6, 0, 0), (q, 12, 0, 0), (h, 6, 0, 0), (h + q, 12, 0, 0), (length, 6, 0, 0)]),
        'coat_tail_r': dict(rot=[(0, 10 + lean * 3, 0, 4), (h, 16 + lean * 3, 0, -2), (length, 10 + lean * 3, 0, 4)]),
        'coat_tail_l': dict(rot=[(0, 14 + lean * 3, 0, 2), (h, 9 + lean * 3, 0, -4), (length, 14 + lean * 3, 0, 2)]),
        'tabard_f': dict(rot=[(0, -10, 0, 0), (h, 6, 0, 0), (length, -10, 0, 0)]),
        'tabard_b': dict(rot=[(0, 8, 0, 0), (h, 14, 0, 0), (length, 8, 0, 0)]),
        'dress2': dict(rot=[(0, 3, 0, 3), (h, 3, 0, -3), (length, 3, 0, 3)]),
        'robe2': dict(rot=[(0, 3, 0, 3), (h, 3, 0, -3), (length, 3, 0, 3)]),
        'hat_tip': dict(rot=[(0, -6, 0, 3), (h, -10, 0, -3), (length, -6, 0, 3)]),
        'hat_tip2': dict(rot=[(0, -8, 0, 6), (h, -14, 0, -6), (length, -8, 0, 6)]),
    }
    if ambient_extra:
        extra.update(ambient_extra)
    return posed(name, length, frames, loop=True, **extra)


# ===================================================================================================== Rosalind

ROS_STAFF_REST = {'right_arm': (-10, 0, 8), 'right_forearm': (-24, 0, 0)}
ROS_HIP = {'left_arm': (8, 0, -34), 'left_forearm': (-30, 0, 66)}


def rosalind():
    A = []
    H = {}
    idle_pose = merge_poses(ROS_STAFF_REST, ROS_HIP, {'head': (2, -8, 5), 'hips': (0, 0, 2.5), 'chest': (0, 4, -2),
                                                      'right_leg': (-4, 0, 3), 'left_leg': (3, 0, -1)})
    L = 6.0
    breath = dict(chest=breathe(L))
    idle2 = merge_poses(idle_pose, {'head': (0, -4, 8)})
    A.append(posed('ros_idle', L, [(0.0, idle_pose, C), (2.6, idle_pose, C), (3.2, idle2, C), (4.6, idle2, C),
                                   (5.4, idle_pose, C), (L, idle_pose, C)], loop=True,
                   eyelids=blink((1.4, 4.0), L), **merge_extra(ambient(L, period=3.0), page_flip(2.2, L))))
    A.append(walk_cycle('ros_walk', 1.0, arm_r=6.0, arm_l=14.0, lean=1.0, stride=20.0,
                        extra_pose={'right_arm': (-10, 0, 6), 'right_forearm': (-20, 0, 0)},
                        ambient_extra=props_idle(1.0)))
    A.append(walk_cycle('ros_run', 0.7, arm_r=10.0, arm_l=30.0, lean=8.0, stride=40.0, bob=1.0,
                        extra_pose={'right_arm': (-18, 0, 8), 'right_forearm': (-24, 0, 0)},
                        ambient_extra=props_idle(0.7)))
    # ready: the staff held across, the free hand open towards the enemy, floating a little
    ready = {'right_arm': (-40, 18, 18), 'right_forearm': (-50, 0, 0), 'staff_witch': (90, 0, -16),
             'left_arm': (-62, -12, -12), 'left_forearm': (-18, 0, 0), 'chest': (4, 16, 0), 'head': (-2, -14, 0),
             'hips': (0, 8, 0), 'right_leg': (-4, 0, 6), 'left_leg': (8, 0, -6), 'right_shin': (8, 0, 0),
             'left_shin': (12, 0, 0)}
    LR = 3.0
    A.append(posed('ros_ready', LR, [(0.0, ready, C), (LR, ready, C)], loop=True,
                   base=dict(pos=wave(LR, 0.5, LR, axis=1, offset=1.2)),
                   eyelids=blink((1.7,), LR), **merge_extra(ambient(LR, period=1.5, hair=2.0), page_flip(0.8, LR))))
    # talk: a hand to the cheek, then open towards the listener; a tilt of the head
    LT = 2.8
    t1 = merge_poses(ROS_STAFF_REST, {'left_arm': (-52, 0, -8), 'left_forearm': (-96, -20, 0), 'head': (4, -6, 10),
                                      'chest': (0, 6, 0)})
    t2 = merge_poses(ROS_STAFF_REST, {'left_arm': (-40, -26, -22), 'left_forearm': (-36, 0, 0), 'head': (-2, -10, -4),
                                      'chest': (0, 8, 0)})
    A.append(posed('ros_talk', LT, [(0.0, idle_pose, C), (0.4, t1, S), (1.3, t1, C), (1.7, t2, C), (2.4, t2, C),
                                    (LT, idle_pose, S)], mouth=flaps(0.4, 2.4, LT, period=0.2),
                   eyelids=blink((1.5,), LT)))
    # attack: the staff swept forward from overhead, a star leaves the tip at 0.38
    LA = 0.9
    a0 = merge_poses(ready, {})
    a1 = {'right_arm': (-150, 10, 20), 'right_forearm': (-20, 0, 0), 'staff_witch': (80, 0, 0),
          'left_arm': (-50, -10, -20), 'left_forearm': (-30, 0, 0), 'chest': (-6, 22, 0), 'head': (-4, -16, 0),
          'right_leg': (-4, 0, 6), 'left_leg': (8, 0, -6)}
    a2 = {'right_arm': (-82, -12, 6), 'right_forearm': (-6, 0, 0), 'staff_witch': (128, 0, 0),
          'left_arm': (-30, -10, -26), 'left_forearm': (-20, 0, 0), 'chest': (10, -10, 0), 'head': (2, 8, 0),
          'right_leg': (-12, 0, 6), 'left_leg': (10, 0, -6), 'right_shin': (12, 0, 0)}
    A.append(posed('ros_attack', LA, [(0.0, a0, C), (0.22, a1, I_), (0.38, a2, O), (0.6, a2, C), (LA, a0, S)],
                   base=dict(pos=[(0, 0, 1.2, 0), (0.38, 0, 0.8, 0), (LA, 0, 1.2, 0)]),
                   eyelids=blink((0.7,), LA), ring_a=dict(rot=spin(LA, 2, turns=1.0), lin=True),
                   ring_b=dict(rot=spin(LA, 0, turns=-0.8), lin=True)))
    H['ros_attack'] = [0.38]
    # attack_b: the free hand flicks a spray of stars, the staff twirls
    LB = 1.0
    b1 = merge_poses(ready, {'left_arm': (-30, 30, -50), 'left_forearm': (-100, 0, 0), 'chest': (0, 30, 0),
                             'staff_witch': (90, 180, -16)})
    b2 = merge_poses(ready, {'left_arm': (-90, -30, -10), 'left_forearm': (0, 0, 0), 'chest': (6, -14, 0),
                             'head': (0, 10, 0), 'staff_witch': (90, 360, -16)})
    b3 = merge_poses(ready, {'staff_witch': (90, 360, -16)})
    A.append(posed('ros_attack_b', LB, [(0.0, ready, C), (0.28, b1, I_), (0.45, b2, O), (0.7, b2, C), (LB, b3, S)],
                   base=dict(pos=[(0, 0, 1.2, 0), (LB, 0, 1.2, 0)]), eyelids=blink((0.8,), LB)))
    H['ros_attack_b'] = [0.45]
    # skill_a "流星雨": the staff raised high, a turn of the wrist, swept down towards the enemy: stars fall
    LS = 1.6
    s1 = {'right_arm': (-170, 0, 10), 'right_forearm': (-4, 0, 0), 'staff_witch': (144, 0, 0),
          'left_arm': (-150, 0, -24), 'left_forearm': (-20, 0, 0), 'chest': (-12, 0, 0), 'head': (-24, 0, 0),
          'right_leg': (-2, 0, 4), 'left_leg': (4, 0, -4)}
    s2 = merge_poses(s1, {'right_arm': (-175, 0, 20), 'staff_witch': (145, 40, 0), 'chest': (-14, 6, 0)})
    s3 = {'right_arm': (-70, -20, 4), 'right_forearm': (-10, 0, 0), 'staff_witch': (160, 0, 0),
          'left_arm': (-20, 0, -40), 'left_forearm': (-10, 0, 0), 'chest': (14, -6, 0), 'head': (8, 0, 0),
          'right_leg': (-14, 0, 6), 'left_leg': (12, 0, -6), 'right_shin': (14, 0, 0), 'left_shin': (6, 0, 0)}
    A.append(posed('ros_skill_a', LS, [(0.0, ready, C), (0.35, s1, S), (0.65, s2, C), (0.8, s3, O), (1.3, s3, C),
                                       (LS, ready, S)],
                   base=dict(pos=[(0, 0, 1.2, 0), (0.6, 0, 3.0, 0), (0.8, 0, 1.5, 0), (LS, 0, 1.2, 0)]),
                   mouth=shout(0.7, 0.95, LS), eyelids=blink((1.35,), LS),
                   ring_a=dict(rot=spin(LS, 2, turns=2.0), lin=True), ring_b=dict(rot=spin(LS, 0, turns=-1.6), lin=True),
                   ring_c=dict(rot=spin(LS, 1, turns=1.2), lin=True),
                   staff_core=dict(scale=[(0, 1, 1, 1), (0.6, 1.6, 1.6, 1.6), (0.8, 1.9, 1.9, 1.9), (1.0, 1, 1, 1),
                                          (LS, 1, 1, 1)])))
    H['ros_skill_a'] = [0.8, 1.0, 1.2, 1.4]
    # skill_b "星縛りの鎖": the staff tip draws a circle in the air, then thrusts: chains of stars bind
    LK = 1.3
    k1 = {'right_arm': (-100, 0, 0), 'right_forearm': (-10, 0, 0), 'staff_witch': (170, 0, 0),
          'left_arm': (-40, 0, -30), 'left_forearm': (-40, 0, 0), 'chest': (6, 10, 0), 'head': (0, -8, 0)}
    k = [(0.0, ready, C), (0.15, k1, S)]
    for i, ang in enumerate((0, 90, 180, 270, 360)):
        import math
        dx = 18 * math.cos(math.radians(ang))
        dy = 18 * math.sin(math.radians(ang))
        k.append((0.15 + 0.1 * (i + 1), merge_poses(k1, {'right_arm': (-100 + dy, dx, 0)}), C))
    k3 = {'right_arm': (-92, 0, 0), 'right_forearm': (0, 0, 0), 'staff_witch': (152, 0, 0),
          'left_arm': (-80, 20, -20), 'left_forearm': (-10, 0, 0), 'chest': (14, 0, 0), 'head': (6, 0, 0),
          'right_leg': (-16, 0, 6), 'left_leg': (12, 0, -6), 'right_shin': (16, 0, 0)}
    k += [(0.75, k3, O), (1.05, k3, C), (LK, ready, S)]
    A.append(posed('ros_skill_b', LK, k, base=dict(pos=[(0, 0, 1.2, 0), (0.75, 0, 0.6, 0), (LK, 0, 1.2, 0)]),
                   mouth=shout(0.7, 0.9, LK), eyelids=blink((1.1,), LK)))
    H['ros_skill_b'] = [0.75]
    # ultimate "千夜の天蓋": she rises, the grimoire flies up and opens over her head, the staff lifted in both hands;
    # the sky's circle forms; she brings the staff down and the stars of a thousand nights fall; she floats back down
    LU = 3.4
    u1 = {'right_arm': (-176, 8, 8), 'right_forearm': (-6, 0, 0), 'staff_witch': (152, 0, 0),
          'left_arm': (-168, -10, -12), 'left_forearm': (-30, 0, 0), 'chest': (-14, 0, 0), 'head': (-28, 0, 0),
          'right_leg': (6, 0, 4), 'left_leg': (-18, 0, -4), 'right_shin': (10, 0, 0), 'left_shin': (40, 0, 0),
          'hips': (-4, 0, 0)}
    u2 = merge_poses(u1, {'chest': (-18, 0, 0), 'head': (-32, 0, 0), 'right_arm': (-180, 14, 14),
                          'left_arm': (-176, -14, -16)})
    u3 = {'right_arm': (-60, -10, 4), 'right_forearm': (-20, 0, 0), 'staff_witch': (185, 0, 0),
          'left_arm': (-60, 30, -30), 'left_forearm': (-30, 0, 0), 'chest': (22, 0, 0), 'head': (12, 0, 0),
          'right_leg': (-30, 0, 4), 'left_leg': (10, 0, -4), 'right_shin': (40, 0, 0), 'left_shin': (20, 0, 0)}
    u4 = merge_poses(u3, {'chest': (16, 0, 0), 'head': (6, 0, 0)})
    A.append(posed('ros_ultimate', LU, [(0.0, ready, C), (0.8, u1, S), (1.6, u2, C), (2.1, u2, C), (2.4, u3, O),
                                        (2.9, u4, C), (LU, ready, S)],
                   base=dict(pos=[(0, 0, 1.2, 0), (0.8, 0, 7.0, 0, S), (2.1, 0, 8.0, 0), (2.4, 0, 6.0, 0, O),
                                  (2.9, 0, 5.0, 0), (LU, 0, 1.2, 0, S)]),
                   grimoire=dict(pos=[(0, 0, 0, 0), (0.6, 0, 0, 0), (1.2, -9, 22, 6, S), (2.2, -9, 23, 6),
                                      (2.6, -9, 21, 6), (3.0, 0, 0, 0, S), (LU, 0, 0, 0)],
                                 rot=[(0, 0, 0, 0), (0.6, 0, 0, 0), (1.2, 55, 0, -12, S), (2.2, 55, 360, -12),
                                      (3.0, 0, 360, 0, S), (LU, 0, 360, 0)]),
                   grimoire_page=dict(rot=[(0, 0, 0, 0), (1.2, 0, 0, 0), (1.4, 0, 0, -170), (1.45, 0, 0, 0),
                                           (1.65, 0, 0, -170), (1.7, 0, 0, 0), (1.9, 0, 0, -170), (1.95, 0, 0, 0),
                                           (LU, 0, 0, 0)], lin=True),
                   ring_a=dict(rot=spin(LU, 2, turns=4.0), lin=True), ring_b=dict(rot=spin(LU, 0, turns=-3.0), lin=True),
                   ring_c=dict(rot=spin(LU, 1, turns=2.5), lin=True),
                   staff_core=dict(scale=[(0, 1, 1, 1), (1.6, 1.8, 1.8, 1.8), (2.1, 2.2, 2.2, 2.2), (2.4, 2.6, 2.6, 2.6),
                                          (2.6, 1, 1, 1), (LU, 1, 1, 1)]),
                   hair_long=dict(rot=[(0, 2, 0, 0), (0.8, 18, 0, 0), (2.1, 22, 0, 0), (2.4, -8, 0, 0), (2.9, 4, 0, 0),
                                       (LU, 2, 0, 0)]),
                   hair_long2=dict(rot=[(0, 2, 0, 0), (0.8, 16, 0, 0), (2.1, 24, 0, 0), (2.4, -10, 0, 0),
                                        (2.9, 6, 0, 0), (LU, 2, 0, 0)]),
                   dress2=dict(rot=[(0, 0, 0, 0), (0.8, -8, 0, 0), (2.1, -10, 0, 0), (2.4, 10, 0, 0), (2.9, 2, 0, 0),
                                    (LU, 0, 0, 0)]),
                   hat_tip=dict(rot=[(0, 0, 0, 0), (0.8, 14, 0, 0), (2.4, -16, 0, 6), (2.9, 4, 0, 0), (LU, 0, 0, 0)]),
                   hat_tip2=dict(rot=[(0, 0, 0, 0), (0.8, 20, 0, 0), (2.4, -24, 0, 10), (2.9, 6, 0, 0),
                                      (LU, 0, 0, 0)]),
                   mouth=shout(2.2, 2.6, LU), eyelids=shut(1.0, 1.9, LU)))
    H['ros_ultimate'] = [2.4, 2.6, 2.8, 3.0]
    # down: kneeling, leaning on the staff, the hat low
    LD = 4.0
    down = merge_poses(kneel('left'), {'right_arm': (-30, 0, 10), 'right_forearm': (-40, 0, 0),
                                       'staff_witch': (50, 0, 0), 'left_arm': (-20, 0, -10),
                                       'left_forearm': (-30, 0, 20), 'chest': (24, 0, 0), 'head': (22, 0, 0)})
    A.append(breathing('ros_down', LD, down, eyelids=shut(0.0, LD, LD),
                       **merge_extra(ambient(LD, props=False), props_idle(LD))))
    # join: a tip of the hat with a wink, a little bow
    LJ = 2.0
    j1 = merge_poses(ROS_STAFF_REST, {'left_arm': (-150, -10, -20), 'left_forearm': (-60, 0, 0), 'head': (8, -6, 8),
                                      'chest': (14, 0, 0), 'right_leg': (6, 0, 4), 'left_leg': (-10, 0, -2),
                                      'left_shin': (14, 0, 0)})
    A.append(posed('ros_join', LJ, [(0.0, idle_pose, C), (0.4, j1, S), (1.3, j1, C), (LJ, idle_pose, S)],
                   hat_brim=dict(rot=[(0, 0, 0, 0), (0.4, -10, 0, 8), (1.3, -12, 0, 8), (LJ, 0, 0, 0)]),
                   eyelids=shut(0.6, 0.85, LJ), mouth=flaps(0.5, 1.4, LJ, period=0.2)))
    return A, H


# ===================================================================================================== Siegwald

SIEG_SHOULDER = {'right_arm': (-58, 0, 14), 'right_forearm': (-52, 0, 0), 'sword_iron': (-44, 0, 0)}


def siegwald():
    A = []
    H = {}
    L = 6.0
    idle_pose = merge_poses(SIEG_SHOULDER, {'left_arm': (0, 0, -8), 'left_forearm': (-10, 0, 0), 'head': (0, 0, 0),
                                            'chest': (0, 0, 0), 'right_leg': (-2, 0, 5), 'left_leg': (2, 0, -5)})
    look = merge_poses(idle_pose, {'head': (0, -26, 0), 'chest': (0, -4, 0)})
    A.append(posed('sieg_idle', L, [(0.0, idle_pose, C), (2.2, idle_pose, C), (2.8, look, C), (4.0, look, C),
                                    (4.6, idle_pose, C), (L, idle_pose, C)], loop=True,
                   eyelids=blink((1.8, 5.2), L), **ambient(L, cloth_amp=0.8)))
    A.append(walk_cycle('sieg_walk', 1.0, arm_r=4.0, arm_l=16.0, lean=1.0, stride=22.0,
                        extra_pose=dict(SIEG_SHOULDER)))
    A.append(walk_cycle('sieg_run', 0.7, arm_r=8.0, arm_l=32.0, lean=10.0, stride=40.0, bob=1.0,
                        extra_pose={'right_arm': (-40, 0, 12), 'right_forearm': (-40, 0, 0), 'sword_iron': (-30, 0, 0)}))
    # ready: both hands on the grip, the blade raised before him, a wide stance
    ready = merge_poses(stance('left', 0.55), {'right_arm': (-40, 0, -18), 'right_forearm': (-40, 0, 0),
                                               'sword_iron': (40, 0, 0), 'left_arm': (-50, 0, 22),
                                               'left_forearm': (-40, 0, 0), 'chest': (6, -20, 0), 'head': (0, 20, 0),
                                               'hips': (0, -6, 0)})
    LR = 3.0
    A.append(posed('sieg_ready', LR, [(0.0, ready, C), (LR, ready, C)], loop=True, eyelids=blink((1.2,), LR),
                   **ambient(LR, period=1.5)))
    # talk: a nod, the free hand opens, a few words
    LT = 2.6
    t1 = merge_poses(idle_pose, {'left_arm': (-36, -20, -14), 'left_forearm': (-40, 0, 0), 'head': (6, -4, 0)})
    A.append(posed('sieg_talk', LT, [(0.0, idle_pose, C), (0.5, t1, S), (1.0, merge_poses(t1, {'head': (-2, -6, 0)}), C),
                                     (1.9, t1, C), (LT, idle_pose, S)],
                   mouth=flaps(0.5, 2.1, LT, period=0.19, rest_every=4), eyelids=blink((1.4,), LT)))
    # attack: a two-handed sweep from his right to his left
    LA = 0.85
    a1 = merge_poses(stance('left', 0.6), {'right_arm': (-30, 70, 30), 'right_forearm': (-30, 0, 0),
                                           'sword_iron': (10, 60, 0), 'left_arm': (-40, 80, 20),
                                           'left_forearm': (-40, 0, 0), 'chest': (0, 50, 0), 'head': (0, -40, 0)})
    a2 = merge_poses(stance('left', 0.7), {'right_arm': (-80, -60, 0), 'right_forearm': (-10, 0, 0),
                                           'sword_iron': (80, -60, 0), 'left_arm': (-76, -40, -10),
                                           'left_forearm': (-20, 0, 0), 'chest': (8, -50, 0), 'head': (0, 40, 0)})
    A.append(posed('sieg_attack', LA, [(0.0, ready, C), (0.25, a1, I_), (0.4, a2, O), (0.6, a2, C), (LA, ready, S)],
                   mouth=shout(0.3, 0.45, LA)))
    H['sieg_attack'] = [0.4]
    # attack_b: a rising cut, then the blade comes down overhead
    LB = 1.1
    b1 = merge_poses(stance('left', 0.7), {'right_arm': (-10, -30, 20), 'right_forearm': (-20, 0, 0),
                                           'sword_iron': (70, -20, 0), 'left_arm': (-20, -20, 10),
                                           'left_forearm': (-30, 0, 0), 'chest': (16, -20, 0)})
    b2 = merge_poses(stance('left', 0.5), {'right_arm': (-170, 10, 10), 'right_forearm': (-10, 0, 0),
                                           'sword_iron': (-20, 0, 0), 'left_arm': (-170, -10, -10),
                                           'left_forearm': (-10, 0, 0), 'chest': (-12, 10, 0), 'head': (-10, 0, 0)})
    b3 = merge_poses(stance('left', 0.8), {'right_arm': (-70, 0, 0), 'right_forearm': (-6, 0, 0),
                                           'sword_iron': (70, 0, 0), 'left_arm': (-70, 0, 0),
                                           'left_forearm': (-6, 0, 0), 'chest': (20, 0, 0), 'head': (6, 0, 0)})
    A.append(posed('sieg_attack_b', LB, [(0.0, ready, C), (0.18, b1, I_), (0.35, b2, O), (0.6, b2, I_), (0.75, b3, O),
                                         (0.95, b3, C), (LB, ready, S)], mouth=shout(0.65, 0.8, LB)))
    H['sieg_attack_b'] = [0.35, 0.75]
    # skill_a "不動の誓い": the sword driven point-down into the ground before him, both hands on the pommel
    LS = 1.4
    s1 = merge_poses(stance('left', 0.3), {'right_arm': (-160, 0, 14), 'right_forearm': (-20, 0, 0),
                                           'sword_iron': (150, 0, 0), 'left_arm': (-160, 0, -14),
                                           'left_forearm': (-20, 0, 0), 'chest': (-8, 0, 0), 'head': (-10, 0, 0)})
    s2 = merge_poses(crouch(0.25), {'right_arm': (-46, 0, -14), 'right_forearm': (-28, 0, 0),
                                    'sword_iron': (172, 0, 0), 'left_arm': (-46, 0, 14), 'left_forearm': (-28, 0, 0),
                                    'chest': (14, 0, 0), 'head': (10, 0, 0)})
    s3 = merge_poses(s2, {'chest': (2, 0, 0), 'head': (-6, 0, 0)})
    A.append(posed('sieg_skill_a', LS, [(0.0, ready, C), (0.35, s1, S), (0.6, s2, O), (0.9, s3, C), (1.15, s3, C),
                                        (LS, ready, S)], mouth=shout(0.55, 0.9, LS),
                   cape=dict(rot=[(0, 5, 0, 0), (0.6, -8, 0, 0), (0.8, 28, 0, 0), (1.15, 12, 0, 0), (LS, 5, 0, 0)]),
                   cape2=dict(rot=[(0, 4, 0, 0), (0.6, -10, 0, 0), (0.85, 34, 0, 0), (1.15, 10, 0, 0),
                                   (LS, 4, 0, 0)])))
    H['sieg_skill_a'] = [0.6]
    # skill_b "旋風斬": two full turns with the blade held out
    LW = 1.3
    w0 = merge_poses(crouch(0.35), {'right_arm': (-80, 20, 50), 'right_forearm': (-6, 0, 0), 'sword_iron': (80, 70, 0),
                                    'left_arm': (-70, 60, 30), 'left_forearm': (-20, 0, 0), 'chest': (8, 40, 0)})
    w1 = merge_poses(crouch(0.3), {'right_arm': (-86, -10, 60), 'right_forearm': (0, 0, 0), 'sword_iron': (86, 90, 0),
                                   'left_arm': (-40, 0, -40), 'left_forearm': (-10, 0, 0), 'chest': (6, -10, 0)})
    A.append(posed('sieg_skill_b', LW, [(0.0, ready, C), (0.2, w0, I_), (0.3, w1, C), (1.0, w1, C), (LW, ready, S)],
                   base=dict(rot=[(0, 0, 0, 0), (0.25, 0, 0, 0), (0.62, 0, -360, 0), (1.0, 0, -720, 0), (1.01, 0, 0, 0),
                                  (LW, 0, 0, 0)], lin=True),
                   cape=dict(rot=[(0, 5, 0, 0), (0.3, 40, 0, 20), (1.0, 40, 0, 20), (LW, 5, 0, 0)]),
                   cape2=dict(rot=[(0, 4, 0, 0), (0.3, 30, 0, 0), (1.0, 30, 0, 0), (LW, 4, 0, 0)]),
                   mouth=shout(0.3, 1.0, LW)))
    H['sieg_skill_b'] = [0.45, 0.85]
    # ultimate "竜退けの一閃": the sword raised, dragon fire gathers along the blade; a crouch, a leap, one cut
    LU = 3.0
    u1 = merge_poses(stance('left', 0.4), {'right_arm': (-176, 10, 6), 'right_forearm': (-4, 0, 0),
                                           'sword_iron': (88, 0, 0), 'left_arm': (-176, -10, -6),
                                           'left_forearm': (-4, 0, 0), 'chest': (-14, 0, 0), 'head': (-20, 0, 0)})
    u2 = merge_poses(stance('left', 1.0), {'right_arm': (-176, 10, 6), 'right_forearm': (-30, 0, 0),
                                           'sword_iron': (90, 0, 0), 'left_arm': (-176, -10, -6),
                                           'left_forearm': (-30, 0, 0), 'chest': (10, 0, 0), 'head': (4, 0, 0)})
    u3 = merge_poses(stance('left', 1.2), {'right_arm': (-40, 0, 6), 'right_forearm': (0, 0, 0),
                                           'sword_iron': (110, 0, 0), 'left_arm': (-40, 0, -6),
                                           'left_forearm': (0, 0, 0), 'chest': (40, 0, 0), 'head': (20, 0, 0)})
    u4 = merge_poses(stance('left', 1.0), {'right_arm': (-30, 0, 10), 'right_forearm': (-10, 0, 0),
                                           'sword_iron': (110, 0, 0), 'left_arm': (-30, 0, -10),
                                           'left_forearm': (-10, 0, 0), 'chest': (30, 0, 0), 'head': (10, 0, 0)})
    A.append(posed('sieg_ultimate', LU, [(0.0, ready, C), (0.5, u1, S), (1.3, u1, C), (1.6, u2, S),
                                         (1.75, shift(u2, 3.0, -2.0), C), (1.9, shift(u3, 0.0, -6.0), O),
                                         (2.4, shift(u4, 0.0, -6.0), C), (LU, ready, S)],
                   cape=dict(rot=[(0, 5, 0, 0), (1.6, 10, 0, 0), (1.9, 60, 0, 0), (2.4, 30, 0, 0), (LU, 5, 0, 0)]),
                   cape2=dict(rot=[(0, 4, 0, 0), (1.6, 10, 0, 0), (1.95, 50, 0, 0), (2.4, 20, 0, 0), (LU, 4, 0, 0)]),
                   mouth=shout(1.75, 2.1, LU), eyelids=shut(0.6, 1.3, LU)))
    H['sieg_ultimate'] = [1.9]
    LD = 4.0
    down = merge_poses(kneel('left'), {'right_arm': (-40, 0, 6), 'right_forearm': (-44, 0, 0), 'sword_iron': (150, 0, 0),
                                       'left_arm': (-30, 0, -6), 'left_forearm': (-50, 0, 0), 'chest': (20, 0, 0),
                                       'head': (26, 0, 0)})
    A.append(breathing('sieg_down', LD, down, eyelids=shut(0.0, LD, LD), **ambient(LD, props=False)))
    # join: the knight's salute, the blade upright before his face
    LJ = 2.0
    j1 = {'right_arm': (-60, -20, -14), 'right_forearm': (-100, 0, 0), 'sword_iron': (70, 0, 0),
          'left_arm': (0, 0, -8), 'head': (6, 0, 0)}
    A.append(posed('sieg_join', LJ, [(0.0, idle_pose, C), (0.5, j1, S), (1.3, j1, C), (LJ, idle_pose, S)],
                   eyelids=shut(0.7, 1.1, LJ), mouth=flaps(1.2, 1.7, LJ, period=0.2)))
    return A, H


# ===================================================================================================== Kaylen

def kaylen():
    A = []
    H = {}
    L = 6.0
    # idle: weight on one leg, the left hand resting on the sheathed sword
    idle_pose = {'left_arm': (-14, -10, 6), 'left_forearm': (-36, 0, 14), 'right_arm': (2, 0, 8),
                 'right_forearm': (-8, 0, 0), 'hips': (0, -6, -3), 'chest': (0, 8, 2), 'head': (6, -10, -4),
                 'right_leg': (2, 0, 6), 'left_leg': (-6, 0, -2), 'left_shin': (8, 0, 0)}
    glance = merge_poses(idle_pose, {'head': (0, 20, 0)})
    A.append(posed('kay_idle', L, [(0.0, idle_pose, C), (3.0, idle_pose, C), (3.5, glance, C), (4.4, glance, C),
                                   (5.0, idle_pose, C), (L, idle_pose, C)], loop=True,
                   eyelids=blink((1.1, 4.8), L), **ambient(L, hair=1.2, cloth_amp=0.8)))
    A.append(walk_cycle('kay_walk', 1.0, arm_r=14.0, arm_l=6.0, lean=2.0, stride=22.0,
                        extra_pose={'left_arm': (-12, -10, 6), 'left_forearm': (-30, 0, 14)}))
    A.append(walk_cycle('kay_run', 0.6, arm_r=40.0, arm_l=8.0, lean=14.0, stride=44.0, bob=1.0,
                        extra_pose={'left_arm': (-20, -10, 6), 'left_forearm': (-30, 0, 14)}))
    # ready: low stance, the blade held low and back, the left hand forward
    ready = merge_poses(stance('left', 0.8, spread=14), {'right_arm': (-20, 0, 34), 'right_forearm': (-20, 0, 0),
                                                         'sword_rune': (150, 30, 0), 'left_arm': (-70, -30, -10),
                                                         'left_forearm': (-20, 0, 0), 'chest': (14, -30, 0),
                                                         'head': (-6, 26, 0), 'hips': (0, -10, 0)})
    LR = 2.0
    A.append(posed('kay_ready', LR, [(0.0, ready, C), (LR, ready, C)], loop=True, eyelids=blink((1.4,), LR),
                   **ambient(LR, period=1.0, hair=2.0)))
    LT = 2.4
    t1 = merge_poses(idle_pose, {'right_arm': (-26, -20, 14), 'right_forearm': (-50, 0, 0), 'head': (2, -2, 4)})
    A.append(posed('kay_talk', LT, [(0.0, idle_pose, C), (0.4, t1, S), (1.6, t1, C), (LT, idle_pose, S)],
                   mouth=flaps(0.4, 1.8, LT, period=0.18, rest_every=3), eyelids=blink((1.0,), LT)))
    # attack: a quick cut, right to left
    LA = 0.6
    a1 = merge_poses(stance('left', 0.8, 14), {'right_arm': (-70, 50, 50), 'right_forearm': (-40, 0, 0),
                                               'sword_rune': (40, 70, 0), 'left_arm': (-40, -20, -30),
                                               'chest': (10, 30, 0), 'head': (-4, -20, 0)})
    a2 = merge_poses(stance('left', 0.9, 14), {'right_arm': (-80, -60, -10), 'right_forearm': (-6, 0, 0),
                                               'sword_rune': (80, -80, 0), 'left_arm': (-20, 0, -40),
                                               'chest': (14, -40, 0), 'head': (0, 30, 0)})
    A.append(posed('kay_attack', LA, [(0.0, ready, C), (0.14, a1, I_), (0.24, a2, O), (0.4, a2, C), (LA, ready, S)]))
    H['kay_attack'] = [0.24]
    # attack_b: back-hand cut, then a thrust
    LB = 0.9
    b1 = merge_poses(stance('left', 0.9, 14), {'right_arm': (-90, -40, -20), 'right_forearm': (-50, 0, 0),
                                               'sword_rune': (60, -90, 0), 'chest': (10, -40, 0), 'head': (0, 30, 0)})
    b2 = merge_poses(stance('left', 0.8, 14), {'right_arm': (-80, 50, 40), 'right_forearm': (-10, 0, 0),
                                               'sword_rune': (80, 90, 0), 'chest': (10, 40, 0), 'head': (0, -30, 0)})
    b3 = merge_poses(stance('right', 1.0, 10), {'right_arm': (-88, 0, 0), 'right_forearm': (0, 0, 0),
                                                'sword_rune': (90, 0, 0), 'left_arm': (10, 0, -20),
                                                'chest': (18, 30, 0), 'head': (0, -26, 0)})
    A.append(posed('kay_attack_b', LB, [(0.0, ready, C), (0.12, b1, I_), (0.22, b2, O), (0.42, b1, I_),
                                        (0.55, shift(b3, 0, -3), O), (0.7, shift(b3, 0, -3), C), (LB, ready, S)]))
    H['kay_attack_b'] = [0.22, 0.55]
    # skill_a "瞬閃": a crouch, gone (the body flashes behind the enemy), the cut lands
    LS = 1.0
    s1 = merge_poses(stance('left', 1.2, 14), {'right_arm': (-10, 0, 40), 'right_forearm': (-10, 0, 0),
                                               'sword_rune': (170, 20, 0), 'left_arm': (-60, -40, -20),
                                               'chest': (30, -40, 0), 'head': (-20, 30, 0)})
    s2 = merge_poses(stance('right', 1.2, 14), {'right_arm': (-100, -50, -30), 'right_forearm': (0, 0, 0),
                                                'sword_rune': (90, -90, 0), 'left_arm': (10, 0, -50),
                                                'chest': (24, -60, 0), 'head': (0, 50, 0)})
    A.append(posed('kay_skill_a', LS, [(0.0, ready, C), (0.25, s1, I_), (0.32, s2, O), (0.75, s2, C), (LS, ready, S)],
                   coat_tail_r=dict(rot=[(0, 10, 0, 0), (0.3, 60, 0, 10), (0.6, 30, 0, 0), (LS, 10, 0, 0)]),
                   coat_tail_l=dict(rot=[(0, 10, 0, 0), (0.3, 56, 0, -10), (0.6, 26, 0, 0), (LS, 10, 0, 0)]),
                   tail2=dict(rot=[(0, 10, 0, 0), (0.3, 70, 0, 20), (0.6, 30, 0, 0), (LS, 10, 0, 0)]),
                   eyelids=shut(0.6, 0.75, LS)))
    H['kay_skill_a'] = [0.3, 0.5]
    # skill_b "幻刃陣": two fingers raised, the phantom blades leave his back and fall on the enemy
    LP = 1.5
    p1 = merge_poses(ready, {'left_arm': (-170, 0, -10), 'left_forearm': (0, 0, 0), 'chest': (-6, -10, 0),
                             'head': (-10, 10, 0)})
    p2 = merge_poses(ready, {'left_arm': (-80, -10, -10), 'left_forearm': (0, 0, 0), 'chest': (16, -20, 0),
                             'head': (6, 20, 0)})
    phantom_keys = {}
    for i in range(6):
        t0 = 0.55 + i * 0.12
        phantom_keys['phantom_%d' % (i + 1)] = dict(
            pos=[(0, 0, 0, 0), (0.3, 0, 4, 0), (t0, 0, 6, 0), (t0 + 0.1, 0, 30, -20), (t0 + 0.11, 0, 0, 0),
                 (LP, 0, 0, 0)],
            scale=[(0, 1, 1, 1), (t0 + 0.1, 1, 1, 1), (t0 + 0.11, 0.01, 0.01, 0.01), (LP - 0.2, 0.01, 0.01, 0.01),
                   (LP, 1, 1, 1)])
    A.append(posed('kay_skill_b', LP, [(0.0, ready, C), (0.35, p1, S), (0.6, p1, C), (0.75, p2, O), (1.25, p2, C),
                                       (LP, ready, S)], mouth=shout(0.6, 0.8, LP), **phantom_keys))
    H['kay_skill_b'] = [0.7, 0.82, 0.94, 1.06, 1.18, 1.3]
    # ultimate "無尽剣界": drawn and gone: cut after cut from every side, then, his back to them, he sheathes the
    # blade slowly; at the click everything he cut falls apart
    LU = 3.6
    seq = [(0.0, ready, C), (0.35, s1, I_)]
    cuts = [(-100, -50, -30, 90, -90, -50), (-60, 60, 50, 60, 90, 40), (-150, 0, 20, -40, 0, 0),
            (-90, -70, -10, 90, -100, -60), (-40, 40, 60, 120, 60, 30), (-110, 30, 10, 70, 50, 20)]
    t = 0.5
    for i, (ax, ay, az, sx, sy, ch) in enumerate(cuts):
        pose = merge_poses(stance('right' if i % 2 else 'left', 1.1, 14),
                           {'right_arm': (ax, ay, az), 'right_forearm': (-6, 0, 0), 'sword_rune': (sx, sy, 0),
                            'left_arm': (-20, 0, -40 if i % 2 else -20), 'chest': (20, ch, 0), 'head': (0, -ch, 0)})
        seq.append((round(t, 3), pose, O))
        t += 0.3
    sheath = merge_poses(stance('left', 0.5, 10), {'right_arm': (-40, 0, -30), 'right_forearm': (-50, 30, 0),
                                                   'sword_rune': (100, 160, 0), 'left_arm': (-40, -10, 6),
                                                   'left_forearm': (-40, 0, 14), 'chest': (4, 30, 0), 'head': (10, -20, 0),
                                                   'hips': (0, 20, 0)})
    done = merge_poses(sheath, {'right_arm': (-10, 0, 10), 'right_forearm': (-20, 0, 0), 'sword_rune': (100, 160, 0),
                                'head': (14, -30, 0)})
    seq += [(2.4, sheath, S), (3.15, merge_poses(sheath, {'right_arm': (-30, 0, -26)}), L_), (3.2, done, O),
            (3.45, done, C), (LU, ready, S)]
    A.append(posed('kay_ultimate', LU, seq,
                   base=dict(rot=[(0, 0, 0, 0), (0.5, 0, 0, 0), (0.8, 0, 120, 0), (1.1, 0, -60, 0), (1.4, 0, 200, 0),
                                  (1.7, 0, 20, 0), (2.0, 0, -150, 0), (2.3, 0, 180, 0), (2.4, 0, 180, 0),
                                  (3.45, 0, 180, 0), (LU, 0, 0, 0)], lin=True),
                   coat_tail_r=dict(rot=[(0, 10, 0, 0), (0.5, 60, 0, 10), (2.3, 60, 0, -10), (2.6, 14, 0, 0),
                                         (LU, 10, 0, 0)]),
                   coat_tail_l=dict(rot=[(0, 10, 0, 0), (0.5, 56, 0, -10), (2.3, 56, 0, 10), (2.6, 12, 0, 0),
                                         (LU, 10, 0, 0)]),
                   tail2=dict(rot=[(0, 10, 0, 0), (0.5, 70, 0, 20), (2.3, 70, 0, -20), (2.7, 14, 0, 0),
                                   (LU, 10, 0, 0)]),
                   eyelids=shut(2.5, 3.15, LU), mouth=shout(3.2, 3.35, LU)))
    H['kay_ultimate'] = [0.5, 0.8, 1.1, 1.4, 1.7, 2.0, 3.2]
    LD = 4.0
    down = merge_poses(kneel('right'), {'right_arm': (-50, 0, 10), 'right_forearm': (-30, 0, 0),
                                        'sword_rune': (170, 0, 0), 'left_arm': (-10, 0, -10),
                                        'left_forearm': (-50, 0, 30), 'chest': (20, 0, 0), 'head': (24, 0, 0)})
    A.append(breathing('kay_down', LD, down, eyelids=shut(0.0, LD, LD), **ambient(LD, props=False)))
    # join: draws, the blade spins in his hand, points ahead, sheathes again
    LJ = 1.8
    j1 = {'right_arm': (-90, 0, 20), 'right_forearm': (-6, 0, 0), 'sword_rune': (90, 0, 720),
          'left_arm': (-20, 0, -10), 'chest': (0, 10, 0), 'head': (0, -10, 0)}
    j2 = {'right_arm': (-30, 0, 30), 'right_forearm': (-50, 0, 0), 'sword_rune': (10, 0, 0),
          'left_arm': (-14, -10, 6), 'left_forearm': (-36, 0, 14)}
    j3 = merge_poses(j2, {'sword_rune': (10, 0, 720)})
    j4 = merge_poses(idle_pose, {'sword_rune': (0, 0, 720)})
    A.append(posed('kay_join', LJ, [(0.0, idle_pose, C), (0.35, j2, S), (0.7, j3, L_), (0.9, j1, O), (1.3, j1, C),
                                    (LJ, j4, S)], mouth=flaps(1.0, 1.5, LJ, period=0.2)))
    return A, H


# ===================================================================================================== Mariel

MAR_REST = {'right_arm': (-16, 0, -6), 'right_forearm': (-30, 0, -8), 'staff_holy': (16, 0, 0), 'left_arm': (-22, 0, 10),
            'left_forearm': (-50, 0, -34)}


def mariel():
    A = []
    H = {}
    L = 6.0
    idle_pose = merge_poses(MAR_REST, {'head': (6, 6, -4), 'chest': (0, -3, 0), 'hips': (0, 0, -1.5),
                                       'right_leg': (-1, 0, 2), 'left_leg': (2, 0, -2)})
    look = merge_poses(idle_pose, {'head': (2, -10, 4)})
    A.append(posed('mar_idle', L, [(0.0, idle_pose, C), (2.4, idle_pose, C), (3.0, look, C), (4.2, look, C),
                                   (4.8, idle_pose, C), (L, idle_pose, C)], loop=True,
                   eyelids=blink((1.6, 4.4), L), **ambient(L, cloth_amp=0.8)))
    A.append(walk_cycle('mar_walk', 1.0, arm_r=6.0, arm_l=6.0, lean=1.0, stride=18.0,
                        extra_pose={'right_arm': (-12, 0, -4), 'right_forearm': (-26, 0, -6),
                                    'left_arm': (-14, 0, 6), 'left_forearm': (-40, 0, -20)},
                        ambient_extra=props_idle(1.0)))
    A.append(walk_cycle('mar_run', 0.7, arm_r=10.0, arm_l=20.0, lean=8.0, stride=36.0, bob=1.0,
                        extra_pose={'right_arm': (-20, 0, 0), 'right_forearm': (-30, 0, 0)},
                        ambient_extra=props_idle(0.7)))
    # ready: the staff held upright before her in both hands
    ready = {'right_arm': (-40, 0, -14), 'right_forearm': (-40, 0, -10), 'staff_holy': (50, 0, 0),
             'left_arm': (-44, 0, 16), 'left_forearm': (-44, 0, -30), 'chest': (2, 0, 0), 'head': (-2, 0, 0),
             'right_leg': (-6, 0, 4), 'left_leg': (6, 0, -4), 'right_shin': (6, 0, 0)}
    LR = 3.0
    A.append(posed('mar_ready', LR, [(0.0, ready, C), (LR, ready, C)], loop=True, eyelids=blink((1.3,), LR),
                   **ambient(LR, period=1.5)))
    LT = 2.8
    t1 = merge_poses(MAR_REST, {'left_arm': (-50, 0, 0), 'left_forearm': (-80, 20, -20), 'head': (8, 4, -8),
                                'chest': (4, 0, 0)})
    t2 = merge_poses(MAR_REST, {'left_arm': (-40, -30, -20), 'left_forearm': (-30, 0, 0), 'head': (2, -6, 6)})
    A.append(posed('mar_talk', LT, [(0.0, idle_pose, C), (0.45, t1, S), (1.3, t1, C), (1.7, t2, C), (2.3, t2, C),
                                    (LT, idle_pose, S)], mouth=flaps(0.4, 2.4, LT, period=0.2),
                   eyelids=blink((1.2,), LT)))
    # attack: the staff thrust forward, a bolt of light
    LA = 0.85
    a1 = merge_poses(ready, {'right_arm': (-30, 0, 10), 'right_forearm': (-60, 0, 0), 'staff_holy': (-20, 0, 0),
                             'chest': (-6, 10, 0)})
    a2 = {'right_arm': (-86, 0, 0), 'right_forearm': (0, 0, 0), 'staff_holy': (146, 0, 0), 'left_arm': (-30, 0, -20),
          'left_forearm': (-20, 0, 0), 'chest': (10, -6, 0), 'right_leg': (-12, 0, 4), 'left_leg': (8, 0, -4),
          'right_shin': (10, 0, 0)}
    A.append(posed('mar_attack', LA, [(0.0, ready, C), (0.2, a1, I_), (0.38, a2, O), (0.6, a2, C), (LA, ready, S)],
                   staff_tear=dict(scale=[(0, 1, 1, 1), (0.3, 1.5, 1.5, 1.5), (0.4, 1, 1, 1), (LA, 1, 1, 1)])))
    H['mar_attack'] = [0.38]
    # attack_b: the free hand raised: a lance of light falls from above
    LB = 0.95
    b1 = merge_poses(ready, {'left_arm': (-170, 0, -10), 'left_forearm': (0, 0, 0), 'head': (-14, 0, 0)})
    b2 = merge_poses(ready, {'left_arm': (-80, -10, -10), 'left_forearm': (0, 0, 0), 'head': (4, 0, 0),
                             'chest': (6, 0, 0)})
    A.append(posed('mar_attack_b', LB, [(0.0, ready, C), (0.28, b1, S), (0.45, b2, O), (0.7, b2, C), (LB, ready, S)]))
    H['mar_attack_b'] = [0.45]
    # skill_a "癒しの聖域": the staff planted, both hands on it, eyes closed in prayer: the circle of healing
    LS = 1.5
    s1 = merge_poses(crouch(0.15), {'right_arm': (-50, 0, -16), 'right_forearm': (-50, 0, -16),
                                    'staff_holy': (60, 0, 10), 'left_arm': (-50, 0, 16), 'left_forearm': (-50, 0, 10),
                                    'chest': (10, 0, 0), 'head': (16, 0, 0)})
    A.append(posed('mar_skill_a', LS, [(0.0, ready, C), (0.35, s1, S), (0.6, s1, C), (1.25, s1, C), (LS, ready, S)],
                   eyelids=shut(0.4, 1.25, LS), mouth=flaps(0.5, 1.2, LS, period=0.22)))
    H['mar_skill_a'] = [0.6]
    # skill_b "浄化の光柱": the staff swept across in an arc and raised: pillars of light rise under the enemies
    LP = 1.3
    p1 = merge_poses(ready, {'right_arm': (-70, 50, 30), 'right_forearm': (-10, 0, 0), 'staff_holy': (60, 0, 0),
                             'chest': (0, 30, 0)})
    p2 = merge_poses(ready, {'right_arm': (-80, -50, -10), 'right_forearm': (-6, 0, 0), 'staff_holy': (60, 0, 0),
                             'chest': (6, -30, 0)})
    p3 = merge_poses(ready, {'right_arm': (-160, -10, 10), 'right_forearm': (0, 0, 0), 'staff_holy': (130, 0, 0),
                             'chest': (-8, -10, 0), 'head': (-12, 0, 0)})
    A.append(posed('mar_skill_b', LP, [(0.0, ready, C), (0.2, p1, S), (0.45, p2, C), (0.65, p3, O), (1.0, p3, C),
                                       (LP, ready, S)], mouth=shout(0.6, 0.8, LP)))
    H['mar_skill_b'] = [0.65]
    # ultimate "王国再誕の祈り": she kneels and prays; wings of light open; she rises, arms spread to the sky
    LU = 3.8
    u1 = merge_poses(kneel('left'), {'right_arm': (-60, 0, -22), 'right_forearm': (-60, 0, 0),
                                     'left_arm': (-60, 0, 22), 'left_forearm': (-60, 0, 30), 'chest': (10, 0, 0),
                                     'head': (18, 0, 0), 'staff_holy': (90, 0, 22)})
    u2 = {'right_arm': (-150, 0, 40), 'right_forearm': (-6, 0, 0), 'staff_holy': (126, 0, 0),
          'left_arm': (-150, 0, -40), 'left_forearm': (-6, 0, 0), 'chest': (-14, 0, 0), 'head': (-24, 0, 0),
          'right_leg': (4, 0, 2), 'left_leg': (4, 0, -2)}
    u3 = merge_poses(u2, {'right_arm': (-120, 0, 60), 'left_arm': (-120, 0, -60), 'chest': (-10, 0, 0),
                          'head': (-16, 0, 0), 'staff_holy': (96, 0, 0)})
    A.append(posed('mar_ultimate', LU, [(0.0, ready, C), (0.5, u1, S), (1.4, u1, C), (2.1, shift(u2, 2.0), S),
                                        (2.5, shift(u3, 3.0), B), (3.2, shift(u3, 2.6), C), (LU, ready, S)],
                   halo=dict(scale=[(0, 1, 1, 1), (2.0, 1, 1, 1), (2.5, 1.8, 1.8, 1.8), (3.2, 1.6, 1.6, 1.6),
                                    (LU, 1, 1, 1)], rot=spin(LU, 2, turns=1.5), lin=True),
                   veil=dict(rot=[(0, 0, 0, 0), (2.1, 6, 0, 0), (2.5, 30, 0, 0), (3.2, 20, 0, 0), (LU, 0, 0, 0)]),
                   veil2=dict(rot=[(0, 0, 0, 0), (2.1, 6, 0, 0), (2.5, 34, 0, 0), (3.2, 24, 0, 0), (LU, 0, 0, 0)]),
                   robe2=dict(rot=[(0, 0, 0, 0), (2.1, -4, 0, 0), (2.5, -10, 0, 0), (3.2, -6, 0, 0), (LU, 0, 0, 0)]),
                   eyelids=shut(0.5, 2.3, LU), mouth=flaps(0.6, 1.4, LU, period=0.24)))
    H['mar_ultimate'] = [2.5]
    LD = 4.0
    down = merge_poses(kneel('left'), {'right_arm': (-50, 0, -30), 'right_forearm': (-60, 0, -30),
                                       'left_arm': (-50, 0, 30), 'left_forearm': (-60, 0, 30), 'chest': (16, 0, 0),
                                       'head': (24, 0, 0), 'staff_holy': (100, 0, 0)})
    A.append(breathing('mar_down', LD, down, eyelids=shut(0.0, LD, LD),
                       **merge_extra(ambient(LD, props=False), props_idle(LD))))
    # join: a graceful bow with a smile
    LJ = 2.0
    j1 = merge_poses(MAR_REST, {'chest': (26, 0, 0), 'head': (14, 0, 0), 'right_leg': (8, 0, 2), 'left_leg': (-4, 0, -2),
                                'right_shin': (14, 0, 0), 'left_shin': (10, 0, 0)})
    A.append(posed('mar_join', LJ, [(0.0, idle_pose, C), (0.5, j1, S), (1.2, j1, C), (LJ, idle_pose, S)],
                   base=dict(pos=[(0, 0, 0, 0), (0.5, 0, -1.0, 0), (1.2, 0, -1.0, 0), (LJ, 0, 0, 0)]),
                   eyelids=shut(0.5, 1.2, LJ), mouth=flaps(1.2, 1.8, LJ, period=0.2)))
    return A, H


def animations():
    A = []
    H = {}
    for build in (rosalind, siegwald, kaylen, mariel):
        anims, hits = build()
        A.extend(anims)
        H.update(hits)
    return A, H
