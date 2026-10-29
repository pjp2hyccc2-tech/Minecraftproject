"""Lowest point (ground contact) of a model in given poses: python3 small/posecheck.py <id> anim:t [anim:t ...]
Prints max Y of all cube corners (ground is y = 24; >24 sinks into the ground, <24 floats)."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from look import load, STATE  # noqa: E402
from hcmodel.render import pose_matrices  # noqa: E402


def lowest(m, layers):
    mats = pose_matrices(m, layers)
    ys = []
    for root in m.roots:
        for b in root.walk():
            M = mats[b.name]
            for c in b.cubes:
                ox, oy, oz = c.origin
                dx, dy, dz = c.size
                for x in (ox, ox + dx):
                    for y in (oy, oy + dy):
                        for z in (oz, oz + dz):
                            ys.append((M @ np.array([x, y, z, 1.0]))[1])
    return max(ys)


if __name__ == '__main__':
    m = load(sys.argv[1])
    for spec in sys.argv[2:]:
        an, t = spec.split(':')
        t = float(t)
        layers = []
        if an not in STATE and an not in getattr(m, 'state_poses', ()) and 'idle' in m.animations:
            layers.append((m.animations['idle'], t, 1.0))
        layers.append((m.animations[an], t, 1.0))
        print('%-10s t=%.2f lowest y=%.2f' % (an, t, lowest(m, layers)))


def per_bone(m, layers, top=6):
    mats = pose_matrices(m, layers)
    out = []
    for root in m.roots:
        for b in root.walk():
            M = mats[b.name]
            for c in b.cubes:
                ox, oy, oz = c.origin
                dx, dy, dz = c.size
                ys = [(M @ np.array([x, y, z, 1.0]))[1] for x in (ox, ox + dx) for y in (oy, oy + dy) for z in (oz, oz + dz)]
                out.append((max(ys), b.name))
    out.sort(reverse=True)
    return out[:top]
