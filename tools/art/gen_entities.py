#!/usr/bin/env python3
"""Builds the SOUNAN monsters from their specs (tools/art/ent_<id>.py):

  textures/entity/<id>.png + <id>_eyes.png     (box UVs packed automatically, eyes layer: RGB 0 where alpha 0)
  src/main/java/com/sounan/client/model/<Name>Model.java   (HierarchicalModel, §6 contract)
  src/main/java/com/sounan/entity/Animated.java
  tools/art/preview/entity_<id>.png             (every pose over time, front / 3-4 / side, plus night views)

The setupAnim body of each spec is written in a strict Java subset and translated to Python (ent_java.py), so
the previews run the same animation code that ships.
usage: python3 tools/art/gen_entities.py [id ...] [--no-preview]
"""
import importlib
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image, ImageDraw

import render3d as R  # noqa: E402
import ent_java  # noqa: E402
from ent_model import java_source, pose_model, draw_entity, entity_world  # noqa: E402
from ent_paint import finalize_eyes, finalize_base  # noqa: E402
from common import TEX_ROOT, JAVA_ROOT, PREVIEW, font, FONT_LABEL  # noqa: E402

IDS = ['nozoki', 'yobigoe', 'ushiro', 'koyanushi', 'oyamasama', 'utsurumono']

ANIMATED = '''package com.sounan.entity;

/** Animation state shared by all SOUNAN monsters (read by the client models). */
public interface Animated {
    int pose();

    float poseTime(float partialTick);
}
'''


def load(eid):
    mod = importlib.import_module(f'ent_{eid}')
    return mod.SPEC()


def build(spec):
    img, eyes = spec.paint()
    base = finalize_base(img, cutout=not spec.translucent)
    eye = finalize_eyes(eyes)
    d = os.path.join(TEX_ROOT, 'entity')
    os.makedirs(d, exist_ok=True)
    Image.fromarray(base, 'RGBA').save(os.path.join(d, f'{spec.id}.png'), optimize=True)
    Image.fromarray(eye, 'RGBA').save(os.path.join(d, f'{spec.id}_eyes.png'), optimize=True)
    src = java_source(spec, spec.tex_w, spec.tex_h)
    jd = os.path.join(JAVA_ROOT, 'client', 'model')
    os.makedirs(jd, exist_ok=True)
    with open(os.path.join(jd, f'{spec.java_name}.java'), 'w', encoding='utf-8') as f:
        f.write(src)
    names = [p.jname for p in spec.root.walk() if p is not spec.root]
    body = 'this.root.visible = !entity.isInvisible();\n' + spec.anim
    body = body.replace('this.', '')
    fn, code = ent_java.compile_anim(body, spec.helpers, ['root'] + names)
    pose_consts = {n: v for n, v in spec.poses}
    return base.astype(np.float64), eye.astype(np.float64), fn, pose_consts


def make_anim(fn, consts):
    def run(parts, entity, *args):
        g = fn.__globals__
        for k, v in consts.items():
            g[k] = v
        return fn(parts, entity, *args)
    return run


def view(spec, tex, eyes, run, pose, pt, age, yaw_view=0.0, pitch_view=8.0, size=(220, 300), dist=6.0, ortho=1.6,
         head_yaw=0.0, head_pitch=0.0, limb=(0.0, 0.0), left=False, scene=None, night=False, center_y=None,
         entity_yaw=0.0, eye_override=None, target=None):
    pose_model(spec, run, pose, pt, age, limb[0], limb[1], head_yaw, head_pitch, left)
    hgt = spec.height
    cy = center_y if center_y is not None else hgt * 0.5
    c = np.array([0.0, cy, 0.0])
    ya, pa = math.radians(yaw_view), math.radians(pitch_view)
    eye = c + dist * np.array([math.sin(ya) * math.cos(pa), math.sin(pa), math.cos(ya) * math.cos(pa)])
    if eye_override is not None:
        eye = np.array(eye_override, float)
    w, h = size
    cv = R.Canvas(w, h, R.look_at(eye, c if eye_override is None else np.array(target, float)),
                  ortho=ortho if eye_override is None else None, fov=60, bg=(52, 55, 62) if not night else (16, 18, 24))
    if scene:
        scene(cv)
    M = entity_world(entity_yaw)
    if night:
        draw_entity(cv, spec, tex, None, M, eye)
        cv.finish()
        cv.night(flashlight=True, strength=1.2, fog_dist=18.0, cone=0.5, amb=0.12)
        draw_entity(cv, spec, tex, eyes, M, eye, eyes_only=True)
        cv.finish()
    else:
        draw_entity(cv, spec, tex, eyes * 0.6, M, eye)
        cv.finish()
    return np.clip(cv.color, 0, 255)


def ground(cv, size=2, y=0.0, tex_name='block/forest_floor_top', dim=0.6):
    from model_preview import tex_lookup
    g = tex_lookup('sounan:' + tex_name) * np.array([dim, dim, dim, 1.0])
    for gx in range(-size, size):
        for gz in range(-size, size):
            pts = np.array([(gx, y, gz), (gx + 1, y, gz), (gx + 1, y, gz + 1), (gx, y, gz + 1)], float)
            cv.quad(pts, R._uv_corners([0, 0, 16, 16]), g)


def trunk(cv, x, z, h=4, tex_name='block/cedar_log', dim=0.75):
    from model_preview import tex_lookup
    b = tex_lookup('sounan:' + tex_name) * np.array([dim, dim, dim, 1.0])
    for y in range(h):
        R.cube(cv, (x, y, z), lambda f: b)


def sheet(rows, out, title=None):
    f = font(FONT_LABEL, 12)
    W = max(sum(im.shape[1] + 4 for _, im in r[1]) for r in rows) + 8
    H = sum(max(im.shape[0] for _, im in r[1]) + 34 for r in rows) + 8
    img = Image.new('RGB', (W, H), (18, 16, 18))
    d = ImageDraw.Draw(img)
    y = 6
    for label, ims in rows:
        d.text((6, y), label, fill=(230, 220, 205), font=f)
        y += 16
        x = 6
        rh = 0
        for sub, im in ims:
            img.paste(Image.fromarray(im.astype(np.uint8)), (x, y))
            d.text((x + 2, y + im.shape[0] + 1), sub, fill=(170, 165, 155), font=f)
            x += im.shape[1] + 4
            rh = max(rh, im.shape[0])
        y += rh + 18
    img.save(out)
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    ids = args or IDS
    jd = os.path.join(JAVA_ROOT, 'entity')
    os.makedirs(jd, exist_ok=True)
    with open(os.path.join(jd, 'Animated.java'), 'w', encoding='utf-8') as f:
        f.write(ANIMATED)
    for eid in ids:
        spec = load(eid)
        tex, eyes, fn, consts = build(spec)
        run = make_anim(fn, consts)
        print(f'{eid}: texture {spec.tex_w}x{spec.tex_h}, {sum(1 for _ in spec.root.walk()) - 1} parts')
        if '--no-preview' not in sys.argv:
            mod = importlib.import_module(f'ent_{eid}')
            rows = mod.previews(spec, tex, eyes, run, view, ground, trunk)
            p = sheet(rows, os.path.join(PREVIEW, f'entity_{eid}.png'))
            print('preview:', p)


if __name__ == '__main__':
    main()
