#!/usr/bin/env python3
"""ETERNAL REALM NPC art pipeline (deterministic; needs Pillow and numpy). Companion of generate.py for the NPC rig.

Single source of truth for the town and story NPC bodies and their dialogue portraits:
  rig_npc.py         geometry and animations of the NPC rig
  npc_cast.py        shared palette, builds and costume helpers; appearances() lists every appearance
  npc_cast_town.py   the ten service NPCs
  npc_cast_story.py  the story characters and the quest givers
  npc_cast_folk.py   townsfolk, guards, clergy, the dungeon warden and the default citizen
  npc_paint.py       painting helpers (faces, hair, clothing)
Run from anywhere:  python3 tools/art_entities/generate_npc.py [--no-preview] [appearance ids: preview only those]

Writes
  src/main/resources/assets/eternalrealm/textures/entity/npc/<appearance>.png (+ <appearance>_glow.png)
  src/main/resources/assets/eternalrealm/textures/gui/portrait/<id>.png   (48x48 busts on a themed backdrop) for every
      appearance that is not a colour variant (ids ending in a digit): the speakers' ids and the townsfolk types
  src/main/java/com/eternalrealm/client/entity/model/gen/NpcGeometry.java / NpcAnimations.java
  src/main/java/com/eternalrealm/npc/NpcBodies.java   (appearance ids, scales and talk lengths; pure, for the server)
  tools/art_entities/uv_map_npc.txt, preview_npc.png (front, three-quarter and back of every appearance),
  preview_npc_portraits.png (all portraits, 2x) and preview_npc_anim.png (frames of every animation)
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import ermodel  # noqa: E402
import generate  # noqa: E402
import rig_npc  # noqa: E402
from erpaint import rgb  # noqa: E402

ROOT = generate.ROOT
PORTRAIT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'eternalrealm', 'textures', 'gui', 'portrait')
BODIES = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'eternalrealm', 'npc', 'NpcBodies.java')
PORTRAIT = 48
PORTRAIT_PPU = 3.0


def build():
    rig = rig_npc.rig()
    ermodel.pack(rig)
    painted = generate.paint_rig(rig)
    return rig, painted


# ------------------------------------------------------------------------------------------------ portraits

def has_portrait(vid):
    return not vid[-1].isdigit()


def backdrop(top, bottom, size=PORTRAIT):
    """Vertical gradient with a soft light behind the head and a darker rim."""
    t, b = np.array(rgb(top), float), np.array(rgb(bottom), float)
    y, x = np.mgrid[0:size, 0:size].astype(float)
    k = (y / (size - 1))[..., None]
    img = t * (1 - k) + b * k
    d = np.hypot((x - size * 0.5) / size, (y - size * 0.36) / size)
    glow = np.clip(1.0 - d / 0.42, 0, 1)[..., None] ** 1.6
    img = img + (255 - img) * glow * 0.22
    edge = np.clip((d - 0.38) / 0.4, 0, 1)[..., None]
    img = img * (1 - 0.45 * edge)
    # quantise softly so the backdrop matches the pixel art (4-level dither of the gradient)
    bayer = np.array([[0, 2], [3, 1]], float) / 4.0 - 0.375
    img = img + bayer[(y % 2).astype(int), (x % 2).astype(int)][..., None] * 6.0
    out = np.zeros((size, size, 4), np.uint8)
    out[..., :3] = np.clip(img, 0, 255)
    out[..., 3] = 255
    return out


def portrait(rig, v, tex):
    """The bust of an appearance, turned a little towards the dialogue text. Portrait spec (Variant.extra):
    bg (top, bottom colours), dy (frame centre shift in model units; negative shows more above the head, e.g.
    hats), yaw, zoom, hide (parts left out of the portrait, e.g. a prop in front of the face)."""
    spec = v.extra.get('portrait') or {}
    yaw = spec.get('yaw', -22.0)
    ppu = PORTRAIT_PPU * spec.get('zoom', 1.0)
    dy = spec.get('dy', 0.0)
    hidden = set(spec.get('hide', ())) - v.hide
    v.hide |= hidden
    try:
        model = ermodel.render(rig, v, tex, yaw=yaw, pitch=4.0, ppu=ppu, size=(PORTRAIT, PORTRAIT),
                               center=(0.6, -4.5 + dy))
    finally:
        v.hide -= hidden
    bg = spec.get('bg', ('#5a6a8a', '#161c2a'))
    img = backdrop(bg[0], bg[1]).astype(np.float64)
    alpha = model[..., 3] > 0
    # a dark outline around the silhouette separates the figure from the backdrop
    pad = np.pad(alpha, 1)
    ring = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~alpha
    img[ring, :3] *= 0.45
    img[alpha, :3] = model[alpha, :3]
    return Image.fromarray(img.astype(np.uint8), 'RGBA')


def write_portraits(rig, painted):
    os.makedirs(PORTRAIT_DIR, exist_ok=True)
    out = {}
    for v in rig.variants:
        if not has_portrait(v.id):
            continue
        im = portrait(rig, v, painted[v.id].img)
        im.save(os.path.join(PORTRAIT_DIR, v.id + '.png'), optimize=True)
        out[v.id] = im
    return out


# ------------------------------------------------------------------------------------------------ java

def write_java(rig):
    generate.write_java(rig)
    for name in ('Geometry', 'Animations'):
        path = os.path.join(generate.GEN_DIR, rig.java_name + name + '.java')
        with open(path) as f:
            text = f.read()
        with open(path, 'w') as f:
            f.write(text.replace('tools/art_entities/generate.py', 'tools/art_entities/generate_npc.py', 1))


def write_bodies(rig):
    jf = ermodel.jf
    L = []
    w = L.append
    ids = [v.id for v in rig.variants]
    w('// GENERATED by tools/art_entities/generate_npc.py. Do not edit: change the script and re-run.')
    w('package com.eternalrealm.npc;')
    w('')
    w('import java.util.List;')
    w('')
    w('/**')
    w(' * The NPC bodies shared by server and client (pure data): appearance ids, model scales and the length of every')
    w(' * appearance\'s talk gesture. Textures are {@code textures/entity/npc/<id>.png}; portraits')
    w(' * {@code textures/gui/portrait/<id>.png} exist for every id that does not end in a digit (colour variants).')
    w(' */')
    w('public final class NpcBodies {')
    w('    /** The appearance used for unknown ids. */')
    w('    public static final String DEFAULT = "%s";' % ids[0])
    w('    /** Every appearance id, {@link #DEFAULT} first. */')
    w('    public static final List<String> APPEARANCES = List.of(')
    line = '            '
    rows = []
    for i, vid in enumerate(ids):
        piece = '"%s"%s' % (vid, ', ' if i < len(ids) - 1 else '')
        if len(line) + len(piece) > 118:
            rows.append(line.rstrip())
            line = '            '
        line += piece
    rows.append(line.rstrip() + ');')
    L.extend(rows)
    w('')
    w('    private NpcBodies() {')
    w('    }')
    w('')
    w('    public static boolean exists(String id) {')
    w('        return APPEARANCES.contains(id);')
    w('    }')
    w('')
    w('    /** {@code id} if it is an appearance, otherwise {@link #DEFAULT}. */')
    w('    public static String orDefault(String id) {')
    w('        return id != null && exists(id) ? id : DEFAULT;')
    w('    }')
    w('')
    w('    /** Model (and hitbox) scale of an appearance (1 if unknown). */')
    w('    public static float scale(String id) {')
    w('        return switch (id == null ? "" : id) {')
    by_scale = {}
    for v in rig.variants:
        s = round(float(v.extra.get('scale', 1.0)), 4)
        if s != 1.0:
            by_scale.setdefault(s, []).append(v.id)
    for s, keys in by_scale.items():
        w('            case %s -> %s;' % (', '.join('"%s"' % k for k in keys), jf(s)))
    w('            default -> 1.0F;')
    w('        };')
    w('    }')
    w('')
    w('    /** Seconds of the talk gesture of an appearance (3 if unknown). */')
    w('    public static float talkSeconds(String id) {')
    w('        return switch (id == null ? "" : id) {')
    by_len = {}
    for v in rig.variants:
        by_len.setdefault(round(rig.slot_anim(v, 'talk').length, 4), []).append(v.id)
    for length, keys in sorted(by_len.items()):
        if length == 3.0:
            continue
        w('            case %s -> %s;' % (', '.join('"%s"' % k for k in keys), jf(length)))
    w('            default -> 3.0F;')
    w('        };')
    w('    }')
    w('}')
    os.makedirs(os.path.dirname(BODIES), exist_ok=True)
    with open(BODIES, 'w') as f:
        f.write('\n'.join(L) + '\n')


def uv_table(rig):
    lines = ['UV map of the NPC rig (part, box index, material, texOffs u v, size w h d)', '== npc %dx%d' % (
        rig.tex_w, rig.tex_h)]
    for p in rig.parts.values():
        for i, c in enumerate(p.cubes):
            lines.append('%-16s %d %-22s %4d %4d   %2d %2d %2d' % (p.name, i, c.mat, c.u, c.v, c.w, c.h, c.d))
    with open(os.path.join(HERE, 'uv_map_npc.txt'), 'w') as f:
        f.write('\n'.join(lines) + '\n')


# ------------------------------------------------------------------------------------------------ previews

def views(rig, v, tex, ppu=4.0):
    out = []
    for yaw, label in ((0, v.id), (35, ''), (200, '')):
        im = Image.fromarray(ermodel.render(rig, v, tex, yaw=yaw, pitch=8, ppu=ppu, size=(int(ppu * 24), int(ppu * 44)),
                                            center=(0, 6)))
        out.append((im, label))
    return out


def preview(rig, painted, path, ppu=4.0, ids=None, per_row=15):
    rows = []
    row = []
    for v in rig.variants:
        if ids and v.id not in ids:
            continue
        row.extend(views(rig, v, painted[v.id].img, ppu))
        if len(row) >= per_row:
            rows.append(row)
            row = []
    if row:
        rows.append(row)
    sheet = generate.compose_rows(rows)
    sheet.save(path)
    return sheet


def portrait_sheet(portraits, path, per_row=12):
    rows = []
    row = []
    for vid, im in portraits.items():
        row.append((im.resize((PORTRAIT * 2, PORTRAIT * 2), Image.NEAREST), vid))
        if len(row) >= per_row:
            rows.append(row)
            row = []
    if row:
        rows.append(row)
    generate.compose_rows(rows).save(path)


def anim_sheet(rig, painted, path, ppu=2.6, frames=6):
    """One strip per animation, shown on the first appearance that uses it (walk on the citizen)."""
    rows = []
    seen = set()
    for v in rig.variants:
        for slot in rig.slots:
            an = rig.slot_anim(v, slot)
            if an.name in seen:
                continue
            seen.add(an.name)
            row = []
            for k in range(frames):
                t = an.length * k / frames if an.loop else an.length * k / (frames - 1)
                pose = ermodel.evaluate([(an, t)])
                im = Image.fromarray(ermodel.render(rig, v, painted[v.id].img, pose=pose, yaw=30, pitch=8, ppu=ppu,
                                                    size=(int(ppu * 34), int(ppu * 46)), center=(0, 5)))
                row.append((im, '%s/%s' % (v.id, an.name) if k == 0 else 't=%.2f' % t))
            rows.append(row)
    # pair the strips two per row to keep the sheet compact
    paired = []
    for i in range(0, len(rows), 2):
        paired.append(rows[i] + (rows[i + 1] if i + 1 < len(rows) else []))
    generate.compose_rows(paired).save(path)


def main(argv):
    rig, painted = build()
    ids = [a for a in argv if not a.startswith('--')]
    print('npc: %d parts %d boxes tex %dx%d, %d appearances, %d animations' % (
        len(rig.parts), len(rig.cubes), rig.tex_w, rig.tex_h, len(rig.variants), len(rig.anims)))
    if ids:
        os.makedirs('/tmp/npcwork', exist_ok=True)
        preview(rig, painted, '/tmp/npcwork/preview_sel.png', ppu=float(os.environ.get('NPC_PPU', '4')), ids=ids)
        return
    generate.write_textures(rig, painted)
    write_java(rig)
    write_bodies(rig)
    uv_table(rig)
    portraits = write_portraits(rig, painted)
    if '--no-preview' in argv:
        return
    preview(rig, painted, os.path.join(HERE, 'preview_npc.png'))
    portrait_sheet(portraits, os.path.join(HERE, 'preview_npc_portraits.png'))
    anim_sheet(rig, painted, os.path.join(HERE, 'preview_npc_anim.png'))


if __name__ == '__main__':
    main(sys.argv[1:])
