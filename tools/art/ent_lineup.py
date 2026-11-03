#!/usr/bin/env python3
"""All the spirits, the medium and her car side by side at true scale (tools/art/preview/ent_lineup.png):
a lit row and the same row through the camera finder at night."""
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np

import render3d as R
import ent_gen as G
import ent_render as ER
from ent_model import entity_world
from common import PREVIEW

LINE = [('visitor', 2, 60), ('waitress', 3, 60), ('chef', 6, 60), ('child', 5, 60), ('mirror_woman', 4, 60),
        ('manager', 1, 60), ('medium', 0, 60), ('medium_car', 0, 40)]


def main():
    built = []
    for eid, pose, t in LINE:
        mod, spec = G.load(eid)
        built.append((G.Built(spec, mod), pose, t))
    rows = []
    for mode in ('lit', 'finder'):
        W, H = 1500, 420
        eye = np.array([0.0, 1.4, 11.0])
        cv = ER.Canvas(W, H, R.look_at(eye, np.array([0.0, 1.05, 0.0])), ortho=1.7, bg=(50, 52, 58) if mode == 'lit' else (8, 9, 12))
        ER.floor(cv, 7)
        x = -5.7
        for b, pose, t in built:
            b.anim.timeline([(1000.0 - t, pose)], 1000.0)
            yaw = -25.0 if b.spec.id != 'medium_car' else -60.0
            M = entity_world(yaw, (x + (0.9 if b.spec.id == 'medium_car' else 0.0), 0.0, 0.0))
            ER.draw_entity(cv, b.spec, b.tex, None, M, eye, alpha=1.0)
            x += 1.18
        if mode == 'finder':
            ER.night_post(cv, amb=0.35, flash=False, fog=(8, 9, 12), fog_dist=40.0)
        x = -5.7
        for b, pose, t in built:
            b.anim.timeline([(1000.0 - t, pose)], 1000.0)
            yaw = -25.0 if b.spec.id != 'medium_car' else -60.0
            M = entity_world(yaw, (x + (0.9 if b.spec.id == 'medium_car' else 0.0), 0.0, 0.0))
            ER.draw_entity_eyes(cv, b.spec, b.eyes, M, 0.6 if mode == 'lit' else 1.0)
            x += 1.18
        img = np.clip(cv.color, 0, 255)
        if mode == 'finder':
            img = ER.finder_post(img)
        rows.append((('lineup at true scale: ' + ', '.join(e for e, _, _ in LINE)) if mode == 'lit' else 'through the finder',
                     [('', img)]))
    out = ER.sheet(rows, os.path.join(PREVIEW, 'ent_lineup.png'))
    print(out)


if __name__ == '__main__':
    main()
