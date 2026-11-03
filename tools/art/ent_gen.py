#!/usr/bin/env python3
"""Builds the HITORI spirits from their specs (tools/art/ent_<id>.py):

  assets/hitori/textures/entity/<id>.png + <id>_eyes.png   (box UVs packed automatically, px texels per pixel)
  src/main/java/com/hitori/client/model/<Name>Model.java    (HierarchicalModel, DESIGN §5/§12 contract)
  tools/art/preview/ent_<id>.png                              (every pose: front / side / 3-4 / night / finder)

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
from ent_model import java_source, entity_world, Animator, HELPERS, PROLOGUE, level  # noqa: E402
from ent_paint import finalize_eyes, finalize_base  # noqa: E402
from common import TEX_ROOT, JAVA_ROOT, PREVIEW  # noqa: E402

IDS = ['waitress', 'visitor', 'chef', 'child', 'mirror_woman', 'manager', 'medium', 'medium_car']


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
        body = (PROLOGUE + spec.anim).replace('this.', '')
        post = spec.post.replace('this.', '')
        consts = {n: v for n, v in spec.poses}
        fn, self.py = ent_java.compile_anim(body, post, HELPERS + spec.helpers, ['root'] + names, consts,
                                            extra_globals={'follow': follow, 'level': level})
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
             limb=None, head=None, glitch=None, scene=None, night=False, alpha=1.0, finder=False, eye=None,
             target=None, fov=60.0, entity_yaw=0.0, center_y=None, light=1.0, eyes_gain=None, amb=0.13,
             flash=True, bg=None):
        spec = self.spec
        if isinstance(events, int):
            pose = events
            pt = 0.0 if t is None else t
            events = [(1000.0 - pt, pose)]
            t = 1000.0
        self.anim.timeline(events, t, limb=limb, head=head, glitch=glitch)
        hgt = spec.height
        cy = center_y if center_y is not None else hgt * 0.5
        c = np.array([0.0, cy, 0.0])
        ya, pa = math.radians(yaw_view), math.radians(pitch_view)
        if eye is None:
            eye_p = c + dist * np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
            tgt = c
            ort = ortho if ortho is not None else hgt * 0.62
        else:
            eye_p = np.array(eye, float)
            tgt = np.array(target, float)
            ort = None
        w, h = size
        if bg is None:
            bg = (50, 52, 58) if not (night or finder) else (8, 9, 12)
        cv = ER.Canvas(w, h, R.look_at(eye_p, tgt), ortho=ort, fov=fov, bg=bg)
        if scene:
            scene(cv)
        M = entity_world(entity_yaw)
        if finder:
            alpha = 1.0
        ER.draw_entity(cv, spec, self.tex, None, M, eye_p, alpha=alpha, light=light)
        if night:
            ER.night_post(cv, amb=amb, flash=flash)
        g = eyes_gain if eyes_gain is not None else (1.0 if (night or finder) else 0.55)
        if night and not finder:
            g *= 1.0
        ER.draw_entity_eyes(cv, spec, self.eyes, M, g)
        img = np.clip(cv.color, 0, 255)
        if finder:
            img = ER.finder_post(img)
        return img


def turnarounds(b, mod):
    """One row per pose: front, 3/4, side, back and a dim night view (as the naked eye sees it, or lit for the
    living), at a representative pose time; walking poses get a walk cycle."""
    import ent_scene as SC
    spec = b.spec
    if not getattr(mod, 'TURNAROUND', True):
        return []
    scenes = getattr(mod, 'POSE_SCENES', {})
    walking = getattr(mod, 'WALKING', ())
    alpha = getattr(mod, 'NAKED_ALPHA', 1.0)
    rows = []
    for name, pid in spec.poses:
        sc = scenes.get(pid, SC.dining())
        limb = (lambda tt: (tt * 0.6, 0.9)) if pid in walking else None
        ev = [(0, pid)]
        t = 140.0
        ims = []
        for (yv, lab) in ((0, 'front'), (-40, '3/4'), (-90, 'side'), (180, 'back')):
            ims.append((lab, b.view(ev, t, yaw_view=yv, pitch_view=8, scene=sc, limb=limb, size=(170, 230))))
        hgt = spec.height
        ims.append(('night (naked eye)' if alpha < 1 else 'night', b.view(ev, t, size=(230, 230), scene=sc, limb=limb,
                    eye=(0.9, 1.62, 3.4 + hgt), target=(0.0, hgt * 0.55, 0.0), night=True, alpha=alpha, fov=50,
                    eyes_gain=min(1.0, alpha * 2.0))))
        ims.append(('finder', b.view(ev, t, size=(230, 230), scene=sc, limb=limb, eye=(0.9, 1.62, 3.4 + hgt),
                    target=(0.0, hgt * 0.55, 0.0), finder=True, fov=50)))
        rows.append((f'{name} ({pid}) turnaround', ims))
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
