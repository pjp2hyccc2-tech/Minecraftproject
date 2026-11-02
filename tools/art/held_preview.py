#!/usr/bin/env python3
"""Simulates how the held item models look in game (vanilla 1.20.1 transforms) -> preview/held_items.png.

First person: ItemInHandRenderer.applyItemArmTransform (translate(+-0.56, -0.52, -0.72)) -> ItemTransform.apply
(translation, rotationXYZ, scale; y/z rotation and x translation mirrored for the left hand) -> translate(-0.5)
-> model, seen by a 70 deg FOV camera at the origin looking down -z.
Third person: a player model holding the item (arm xRot = -pi/10), ItemInHandLayer.renderArmWithItem.
"""
import json
import math
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from PIL import Image, ImageDraw

import render3d as R  # noqa: E402
from model_preview import tex_lookup, load_model  # noqa: E402
from common import PREVIEW, font, FONT_LABEL  # noqa: E402


def quat_xyz(rx, ry, rz):
    """Matrix of new Quaternionf().rotationXYZ(rx, ry, rz) (JOML): R = Rx * Ry * Rz."""
    return R.rot_x(rx) @ R.rot_y(ry) @ R.rot_z(rz)


def item_transform(t, left):
    if not t:
        return np.eye(4)
    tr = np.array(t.get('translation', [0, 0, 0]), float) / 16.0
    rot = np.radians(np.array(t.get('rotation', [0, 0, 0]), float))
    sc = np.array(t.get('scale', [1, 1, 1]), float)
    if left:
        rot[1], rot[2] = -rot[1], -rot[2]
        tr[0] = -tr[0]
    M = R.affine(None, tr) @ R.affine(quat_xyz(*rot)) @ R.scale_m(*sc)
    return M


def base_of(item_model):
    return item_model['base'] if 'base' in item_model else item_model


def draw_item(cv, item_model, ctx, left, M_pre):
    base = base_of(item_model)
    disp = base.get('display', {})
    key = ctx + ('_lefthand' if left else '_righthand')
    t = disp.get(key) or disp.get(ctx + '_righthand')
    M = M_pre @ item_transform(t, left) @ R.affine(None, [-0.5, -0.5, -0.5])
    R.draw_block_model(cv, base, tex_lookup, M=M)


def first_person(item_model, left=False, w=480, h=270, equip=0.0):
    cv = R.Canvas(w, h, np.eye(4), fov=70, bg=(28, 31, 38))
    # a ground plane + a few trunks for scale (camera at eye height 1.62)
    floor = tex_lookup('sounan:block/forest_floor_top') * np.array([0.5, 0.5, 0.5, 1])
    for gx in range(-6, 6):
        for gz in range(-12, 0):
            pts = np.array([(gx, -1.62, gz), (gx + 1, -1.62, gz), (gx + 1, -1.62, gz + 1), (gx, -1.62, gz + 1)], float)
            cv.quad(pts, R._uv_corners([0, 0, 16, 16]), floor)
    bark = tex_lookup('sounan:block/cedar_log') * np.array([0.6, 0.6, 0.6, 1])
    for (tx, tz) in ((-3, -7), (2, -9), (5, -5)):
        for ty in range(0, 4):
            R.cube(cv, (tx, -1.62 + ty, tz), lambda f: bark)
    side = -1 if left else 1
    M_pre = R.affine(None, [side * 0.56, -0.52 + equip * -0.6, -0.72])
    draw_item(cv, item_model, 'firstperson', left, M_pre)
    cv.finish()
    img = cv.color
    img[h // 2, w // 2 - 4:w // 2 + 5] = 230
    img[h // 2 - 4:h // 2 + 5, w // 2] = 230
    return np.clip(img, 0, 255)


# --- player model (vanilla PlayerModel geometry, px, y down) for third person
PLAYER = {
    'head': ((0, 0, 0), (-4, -8, -4, 8, 8, 8)),
    'body': ((0, 0, 0), (-4, 0, -2, 8, 12, 4)),
    'right_arm': ((-5, 2, 0), (-3, -2, -2, 4, 12, 4)),
    'left_arm': ((5, 2, 0), (-1, -2, -2, 4, 12, 4)),
    'right_leg': ((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4)),
    'left_leg': ((1.9, 12, 0), (-2, 0, -2, 4, 12, 4)),
}
SKIN = {'head': (140, 120, 100), 'body': (60, 70, 90), 'right_arm': (70, 80, 100), 'left_arm': (70, 80, 100),
        'right_leg': (50, 50, 60), 'left_leg': (50, 50, 60)}


def part_matrix(pivot, rot):
    """ModelPart.translateAndRotate in block units: translate(pivot/16) then rotationZYX(z, y, x)."""
    xr, yr, zr = rot
    return R.affine(None, np.array(pivot, float) / 16.0) @ R.affine(R.rot_z(zr) @ R.rot_y(yr) @ R.rot_x(xr))


def third_person(item_model, left=False, w=320, h=320, yaw=-30, pitch=15):
    c = np.array([0, 1.0, 0])
    d = 3.2
    eye = c + d * np.array([math.sin(math.radians(yaw)) * math.cos(math.radians(pitch)), math.sin(math.radians(pitch)),
                            math.cos(math.radians(yaw)) * math.cos(math.radians(pitch))])
    cv = R.Canvas(w, h, R.look_at(eye, c), ortho=1.25, bg=(36, 38, 44))
    # entity root: Y(180 - bodyRot) with bodyRot = 180 -> faces +z (towards the camera), then scale(-1,-1,1), translate(0,-1.501,0)
    root = R.affine(R.rot_y(math.pi)) @ R.scale_m(-1, -1, 1) @ R.affine(None, [0, -1.501, 0])
    arm_rot = -math.pi / 10
    rots = {'right_arm': (arm_rot if not left else 0, 0, 0), 'left_arm': (arm_rot if left else 0, 0, 0)}
    for name, (pivot, (ox, oy, oz, dx, dy, dz)) in PLAYER.items():
        M = root @ part_matrix(pivot, rots.get(name, (0, 0, 0)))
        col = np.zeros((1, 1, 4))
        col[..., :3] = SKIN[name]
        col[..., 3] = 255
        for (dname, verts, uvs) in R.box_polys(ox, oy, oz, dx, dy, dz, 0, 0):
            pts = R.apply(M, np.array(verts, float) / 16.0)
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            shade = R.entity_shade(-n if np.dot(-n, eye - pts.mean(0)) > 0 else n)
            cv.quad(pts, np.zeros((4, 2)), col, shade=shade)
    arm = 'left_arm' if left else 'right_arm'
    M = root @ part_matrix(PLAYER[arm][0], rots[arm])
    M = M @ R.affine(R.rot_x(math.radians(-90))) @ R.affine(R.rot_y(math.radians(180)))
    M = M @ R.affine(None, [(-1 if left else 1) / 16.0, 0.125, -0.625])
    draw_item(cv, item_model, 'thirdperson', left, M)
    cv.finish()
    return np.clip(cv.color, 0, 255)


ITEMS = ['flashlight', 'flashlight_on', 'flashlight_low', 'phone', 'phone_light', 'phone_camera', 'flare', 'flare_lit']


def main(names=None):
    names = names or ITEMS
    rows = []
    for n in names:
        m = load_model('item', n)
        rows.append((n, [first_person(m), first_person(m, left=True), third_person(m), third_person(m, yaw=60)]))
    f = font(FONT_LABEL, 12)
    W = 6 + 480 * 2 + 320 * 2 + 6 * 4
    H = len(rows) * (320 + 20) + 6
    img = Image.new('RGB', (W, H), (18, 16, 18))
    d = ImageDraw.Draw(img)
    for i, (n, ims) in enumerate(rows):
        x, y = 6, 6 + i * 340
        for im in ims:
            img.paste(Image.fromarray(im.astype(np.uint8)), (x, y))
            x += im.shape[1] + 6
        d.text((6, y + 322), n + '   (first person R / L, third person)', fill=(220, 210, 200), font=f)
    out = os.path.join(PREVIEW, 'held_items.png')
    img.save(out)
    return out


if __name__ == '__main__':
    print(main(sys.argv[1:] or None))
