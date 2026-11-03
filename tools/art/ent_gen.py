#!/usr/bin/env python3
"""Builds the HITORI entities from their specs (tools/art/ent_<id>.py):

  assets/hitori/textures/entity/<id>.png + <id>_eyes.png   (box UVs packed automatically, px texels per pixel)
  src/main/java/com/hitori/client/model/<Name>Model.java    (HierarchicalModel, DESIGN §5/§12 contract)
  tools/art/preview/ent_<id>.png                              (every pose: front / 3-4 / side / back / night / IR camera)

The setupAnim body of each spec is written in a strict Java subset and translated to Python (ent_java.py), so the
previews run the same animation code that ships (pose blending and per-entity memory are mirrored by
ent_model.Animator).
usage: python3 tools/art/ent_gen.py [id ...] [--no-preview] [--rows=a,b]
"""
import importlib
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image

import render3d as R  # noqa: E402
import ent_java  # noqa: E402
import ent_render as ER  # noqa: E402
from ent_model import java_source, entity_world, Animator, HELPERS, PROLOGUE, level, advance, chase, flop  # noqa: E402
from ent_paint import finalize_eyes, finalize_base  # noqa: E402
from common import TEX_ROOT, JAVA_ROOT, PREVIEW  # noqa: E402

IDS = ['oni_doll', 'woman', 'camera']


def load(eid):
    mod = importlib.import_module(f'ent_{eid}')
    return mod, mod.SPEC()


class Built:
    def __init__(self, spec, mod):
        self.spec = spec
        self.mod = mod
        img, eyes = spec.paint()
        self.base = finalize_base(img, cutout=False)
        self.eye = finalize_eyes(eyes)
        self.tex = self.base.astype(np.float64)
        self.eyes = self.eye.astype(np.float64)
        self.java = java_source(spec)
        names = [p.jname for p in spec.root.walk() if p is not spec.root]
        body = ((spec.prologue or PROLOGUE) + spec.anim).replace('this.', '')
        post = spec.post.replace('this.', '')
        consts = {n: v for n, v in spec.poses}
        fn, self.py = ent_java.compile_anim(body, post, HELPERS + spec.helpers, ['root'] + names, consts,
                                            extra_globals={'follow': follow, 'level': level, 'advance': advance,
                                                           'chase': chase, 'flop': flop})
        self.anim = Animator(spec, fn)

    def save(self):
        spec = self.spec
        d = os.path.join(TEX_ROOT, 'entity')
        os.makedirs(d, exist_ok=True)
        Image.fromarray(self.base, 'RGBA').save(os.path.join(d, f'{spec.id}.png'), optimize=True)
        Image.fromarray(self.eye, 'RGBA').save(os.path.join(d, f'{spec.id}_eyes.png'), optimize=True)
        jd = os.path.join(JAVA_ROOT, 'client', 'model')
        os.makedirs(jd, exist_ok=True)
        with open(os.path.join(jd, f'{spec.java_name}.java'), 'w', encoding='utf-8') as f:
            f.write(self.java)

    # ------------------------------------------------------------------------------------------------ views
    def view(self, events, t=None, *, yaw_view=0.0, pitch_view=8.0, size=(190, 270), ortho=None, dist=6.0,
             limb=None, head=None, glitch=None, scene=None, look='lit', alpha=1.0, eye=None, target=None, fov=60.0,
             entity_yaw=0.0, center_y=None, eyes_gain=None, bg=None, pos=(0.0, 0.0, 0.0), tv=(-2.6, 0.55, 1.4),
             label='CAM1  浴室', stamp='2026/11/03  03:21:07', pose_fn=None, light=1.0):
        """look: 'lit' (plain), 'night' (the dark apartment, only the TV's glow), 'ir' (a fixed camera's IR feed).
        pose_fn(parts) may adjust the posed parts after the animation (preview staging only)."""
        spec = self.spec
        if isinstance(events, int):
            pose = events
            pt = 0.0 if t is None else t
            events = [(1000.0 - pt, pose)]
            t = 1000.0
        self.anim.timeline(events, t, limb=limb, head=head, glitch=glitch)
        if pose_fn:
            pose_fn({p.name: p for p in spec.root.walk()})
        hgt = spec.height
        cy = center_y if center_y is not None else getattr(self.mod, 'VIEW_CENTER', hgt * 0.5)
        c = np.array([pos[0], cy, pos[2]])
        ya, pa = math.radians(yaw_view), math.radians(pitch_view)
        if eye is None:
            eye_p = c + dist * np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
            tgt = c
            ort = ortho if ortho is not None else getattr(self.mod, 'VIEW_ORTHO', hgt * 0.62)
        else:
            eye_p = np.array(eye, float)
            tgt = np.array(target, float)
            ort = None
        w, h = size
        if bg is None:
            bg = (50, 52, 58) if look == 'lit' else (6, 7, 9)
        cv = ER.Canvas(w, h, R.look_at(eye_p, tgt), ortho=ort, fov=fov, bg=bg)
        if scene:
            scene(cv)
        M = entity_world(entity_yaw, pos)
        if look == 'night':
            cv.color *= 0.16
            ER.draw_entity(cv, spec, self.tex, None, M, eye_p, alpha=alpha, sky=ER.tv_light(tv))
            ER.tv_post(cv, side=-1.0 if tv[0] < 0 else 1.0)
            g = 0.9 if eyes_gain is None else eyes_gain
            ER.draw_entity_eyes(cv, spec, self.eyes, M, g)
            return np.clip(cv.color, 0, 255)
        ER.draw_entity(cv, spec, self.tex, None, M, eye_p, alpha=alpha, light=light)
        if look == 'ir':
            ER.ir_light(cv)
            g = 2.4 if eyes_gain is None else eyes_gain
            ER.draw_entity_eyes(cv, spec, self.eyes, M, g)
            return ER.ir_post(np.clip(cv.color, 0, 400), label=label, stamp=stamp)
        g = 0.55 if eyes_gain is None else eyes_gain
        ER.draw_entity_eyes(cv, spec, self.eyes, M, g)
        return np.clip(cv.color, 0, 255)


def turnarounds(b, mod):
    """One row per pose: front, 3/4, side, back (lit), the dim apartment lit only by the TV (as the naked eye sees
    it) and a fixed camera's IR feed, at a representative pose time; cyclic poses are shown mid-cycle."""
    spec = b.spec
    if not getattr(mod, 'TURNAROUND', True):
        return []
    scenes = getattr(mod, 'POSE_SCENES', {})
    times = getattr(mod, 'POSE_TIMES', {})
    night_eye = getattr(mod, 'NIGHT_EYE', ((0.9, 1.1, 2.6), (0.0, 0.3, 0.0)))
    ir_eye = getattr(mod, 'IR_EYE', ((1.5, 1.9, 2.2), (0.0, 0.25, 0.0)))
    tsz = getattr(mod, 'TURN_SIZE', (150, 170))
    rows = []
    for name, pid in spec.poses:
        sc = scenes.get(pid)
        ev = [(0, pid)]
        t = times.get(pid, 140.0)
        ims = []
        for (yv, lab) in ((0, 'front'), (-40, '3/4'), (-90, 'side'), (180, 'back')):
            ims.append((lab, b.view(ev, t, yaw_view=yv, pitch_view=10, scene=sc, size=tsz)))
        ims.append(('night (TV light)', b.view(ev, t, size=(200, 170), scene=sc, eye=night_eye[0], target=night_eye[1],
                                               look='night', fov=55)))
        ims.append(('IR camera', b.view(ev, t, size=(200, 170), scene=sc, eye=ir_eye[0], target=ir_eye[1], look='ir',
                                        fov=62)))
        rows.append((f'{name} ({pid}) at pose tick {int(t)}', ims))
    return rows


def follow(st, slot, target, speed, t):
    last = st.ft[slot]
    if last < 0 or t < last or t - last > 40.0:
        st.fv[slot] = target
    else:
        k = 1.0 - math.exp(-speed * (t - last))
        st.fv[slot] = st.fv[slot] + (target - st.fv[slot]) * k
    st.ft[slot] = t
    return st.fv[slot]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    rows_sel = None
    for a in sys.argv[1:]:
        if a.startswith('--rows='):
            rows_sel = a.split('=', 1)[1].split(',')
    ids = args or IDS
    for eid in ids:
        mod, spec = load(eid)
        b = Built(spec, mod)
        b.save()
        nparts = sum(1 for _ in spec.root.walk()) - 1
        print(f'{eid}: layout {spec.tex_w}x{spec.tex_h} -> texture {spec.tex_w * spec.px}x{spec.tex_h * spec.px}, '
              f'{nparts} parts, {len(spec.cubes())} cubes')
        if '--no-preview' not in sys.argv:
            os.makedirs(PREVIEW, exist_ok=True)
            rows = mod.previews(b, ER, rows_sel) + turnarounds(b, mod)
            p = ER.sheet(rows, os.path.join(PREVIEW, f'ent_{eid}.png'))
            print('preview:', p)


if __name__ == '__main__':
    main()
