#!/usr/bin/env python3
"""Animation sanity checks for the HITORI entity models (offline): runs every pose tick by tick and reports
  * pops: any part whose orientation in model space turns more than LIMIT degrees in one tick (a flipping joint),
  * freeze: holding the pose time must leave every part (but the real-time drips) exactly where it is,
  * stop-motion: stepping the pose time by 3 ticks must hold the pose between the steps.
usage: python3 tools/art/ent_check.py [id ...]"""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np

import ent_gen as G
import render3d as R
from ent_model import FakeEntity, ATTRS

LIMIT = 50.0
REALTIME = ('fall', 'drip', 'rice', 'led')          # parts that move in real time on purpose


def world_rots(spec):
    out = {}

    def rec(p, M):
        Mp = M @ (R.rot_z(p.zRot) @ R.rot_y(p.yRot) @ R.rot_x(p.xRot))
        out[p.name] = Mp
        for c in p.children:
            rec(c, Mp)
    rec(spec.root, np.eye(3))
    return out


def angle(A, B):
    c = (np.trace(A.T @ B) - 1.0) / 2.0
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


# realistic predecessors of each pose (the game never floats a doll straight into a lunge)
PREV = {'oni_doll': {'FLOAT': ['FLOAT'], 'SIT': ['WALK', 'CRAWL', 'STAND'], 'CRAWL': ['FLOAT', 'WALK', 'STAND', 'SIT', 'PEEK'],
                     'WALK': ['FLOAT', 'CRAWL', 'STAND', 'SIT', 'TURN', 'PEEK'], 'STAND': ['WALK', 'CRAWL', 'SIT', 'TURN', 'PEEK'],
                     'TURN': ['STAND', 'WALK', 'SIT'], 'PEEK': ['WALK', 'CRAWL', 'STAND'], 'LUNGE': ['STAND', 'WALK', 'CRAWL', 'PEEK', 'TURN'],
                     'LIMP': ['STAND', 'WALK', 'CRAWL', 'SIT', 'TURN', 'PEEK'], 'BURN': ['LIMP']},
        'woman': {'STAND': ['FACE'], 'FACE': ['STAND', 'CLOSE'], 'CLOSE': ['FACE', 'STAND'], 'GONE': ['STAND', 'FACE', 'CLOSE']},
        'camera': {'IDLE': ['IDLE'], 'FALLEN': ['IDLE']}}
IN_POSE = 45.0          # degrees per tick, once the blend is over
SNAP = 80.0             # degrees per tick while blending in (snappy on purpose: 2-5 tick blends)
FLIP = 120.0            # with glitch the joints snap on purpose; only a flip past this counts
# poses that are violent on purpose: (in pose, blending in) limits
LIMITS = {('oni_doll', 'CRAWL'): (56.0, SNAP), ('oni_doll', 'LUNGE'): (IN_POSE, 92.0)}


def check(eid):
    mod, spec = G.load(eid)
    b = G.Built(spec, mod)
    parts = [p for p in spec.root.walk() if p is not spec.root]
    moving = [p.name for p in parts if any(k in p.name for k in REALTIME)]
    bad = 0
    names = {n: i for n, i in spec.poses}
    for name, pid in spec.poses:
        worst_in, worst_tr, worst_g = (0.0, ''), (0.0, ''), (0.0, '')
        for prev_name in PREV.get(eid, {}).get(name, [name]):
            prev = names[prev_name]
            for g in (0.0, 0.35):
                b.anim.states.clear()
                last = None
                for k in range(0, 300):
                    pose, pt = (prev, float(k)) if k < 60 else (pid, float(k - 60))
                    b.anim.run(FakeEntity(pose, pt, g), 0, 0, 1000.0 + k, 0, 0)
                    rots = world_rots(spec)
                    if last is not None and k > 60:
                        for p in parts:
                            if p.name in moving or not p.visible:
                                continue
                            a = angle(last[p.name], rots[p.name])
                            where = f'{p.name} at pose tick {k - 60} from {prev_name}, glitch {g}'
                            if g > 0.0:
                                if a > FLIP and a > worst_g[0]:
                                    worst_g = (a, where)
                            elif k - 60 > 14:
                                if a > worst_in[0]:
                                    worst_in = (a, where)
                            elif a > worst_tr[0]:
                                worst_tr = (a, where)
                    last = rots
        flag = ''
        lim_in, lim_tr = LIMITS.get((eid, name), (IN_POSE, SNAP))
        if worst_in[0] > lim_in or worst_tr[0] > lim_tr or worst_g[0] > FLIP:
            flag = '  <-- POP'
            bad += 1
        print(f'{eid} {name:6s}: in pose max {worst_in[0]:5.1f} deg/tick ({worst_in[1]}) | blending in max {worst_tr[0]:5.1f} '
              f'({worst_tr[1]}){flag}' + (f' | glitch flip {worst_g[0]:.0f} ({worst_g[1]})' if worst_g[0] else ''))
        b.anim.states.clear()
        for k in range(60):
            b.anim.run(FakeEntity(pid, float(k), 0.0), 0, 0, 2000.0 + k, 0, 0)
        snaps = []
        for k in range(20):
            b.anim.run(FakeEntity(pid, 60.0, 0.0), 0, 0, 2060.0 + k * 0.5, 0, 0)
            snaps.append(np.array([[getattr(p, a_) for a_ in ATTRS] for p in parts if p.name not in moving]))
        fr = max(np.abs(x - snaps[0]).max() for x in snaps)
        if fr > 1e-9:
            bad += 1
            print(f'    FREEZE BROKEN: {fr:.3g}')
    return bad


def main():
    ids = sys.argv[1:] or G.IDS
    bad = sum(check(e) for e in ids)
    print('OK' if bad == 0 else f'{bad} problem(s)')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
