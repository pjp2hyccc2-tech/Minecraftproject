#!/usr/bin/env python3
"""The HITORI entities side by side at true scale (tools/art/preview/ent_lineup.png): the doll in each of its poses,
the woman, the camera - lit, then the same row as a fixed camera's IR feed sees it."""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np

import render3d as R
import ent_gen as G
import ent_render as ER
import ent_scene as SC
from ent_model import entity_world
from common import PREVIEW

# (id, pose, pose time, x position, yaw)
LINE = [('woman', 1, 140, -3.1, -15.0), ('oni_doll', 4, 140, -2.15, -20.0), ('oni_doll', 1, 140, -1.45, -25.0),
        ('oni_doll', 3, 136, -0.75, -60.0), ('oni_doll', 2, 136, 0.05, -70.0), ('oni_doll', 6, 60, 0.85, -10.0),
        ('oni_doll', 8, 60, 1.55, -45.0), ('camera', 0, 40, 2.2, -30.0), ('woman', 0, 140, 3.05, 160.0)]


def main():
    cache = {}
    built = []
    for eid, pose, t, x, yaw in LINE:
        if eid not in cache:
            mod, spec = G.load(eid)
            cache[eid] = G.Built(spec, mod)
        built.append((cache[eid], pose, t, x, yaw))
    rows = []
    for mode in ('lit', 'ir'):
        W, H = 1500, 460
        eye = np.array([0.0, 1.3, 9.0]) if mode == 'lit' else np.array([0.0, 3.4, 6.5])
        cv = ER.Canvas(W, H, R.look_at(eye, np.array([0.0, 0.8, 0.0])), ortho=1.25 if mode == 'lit' else None, fov=40,
                       bg=(50, 52, 58) if mode == 'lit' else (6, 7, 9))
        ER.floor(cv, 5, tex=SC.TATAMI)
        for b, pose, t, x, yaw in built:
            b.anim.timeline([(1000.0 - t, pose)], 1000.0)
            M = entity_world(yaw, (x, 0.0, 0.0))
            ER.draw_entity(cv, b.spec, b.tex, None, M, eye, alpha=1.0)
            if mode == 'ir':
                continue
            ER.draw_entity_eyes(cv, b.spec, b.eyes, M, 0.55)
        if mode == 'ir':
            ER.ir_light(cv, power=3.2, reach=4.5)
            for b, pose, t, x, yaw in built:
                b.anim.timeline([(1000.0 - t, pose)], 1000.0)
                ER.draw_entity_eyes(cv, b.spec, b.eyes, entity_world(yaw, (x, 0.0, 0.0)), 2.4)
            img = ER.ir_post(np.clip(cv.color, 0, 400), label='CAM2  和室', stamp='2026/11/03  03:47:19')
        else:
            img = np.clip(cv.color, 0, 255)
        rows.append(('true scale: 女 FACE, 鬼 STAND / SIT / WALK / CRAWL / PEEK / LIMP, 定点カメラ, 女 STAND' if mode == 'lit'
                     else 'the same, as a fixed camera sees them in the dark (IR)', [('', img)]))
    out = ER.sheet(rows, os.path.join(PREVIEW, 'ent_lineup.png'))
    print(out)


if __name__ == '__main__':
    main()
