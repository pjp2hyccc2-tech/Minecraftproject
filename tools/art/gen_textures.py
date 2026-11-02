#!/usr/bin/env python3
"""冥婚 ―MEIKON― procedural texture generator.

Generates every block / item / painting / GUI / environment texture listed in
docs/DESIGN.md section 4 (entity textures excluded) as deterministic pixel art.

usage:  python3 tools/art/gen_textures.py [--only GLOB[,GLOB..]] [--no-preview] [--no-check]
"""
import argparse
import fnmatch
import json
import os
import sys
sys.dont_write_bytecode = True

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import REGISTRY, TEX_ROOT, PREVIEW, finalize, to_array  # noqa: E402
import t_build  # noqa: E402,F401  floors, walls, panels, doors, torii, decals
import t_props  # noqa: E402,F401  furniture / block-model surfaces
import t_items  # noqa: E402,F401  items
import t_paint  # noqa: E402,F401  paintings
import t_gui  # noqa: E402,F401  gui + environment
import preview  # noqa: E402

MCMETA = {"animation": {"frametime": 3, "interpolate": True}}


def render(name):
    spec = REGISTRY[name]
    out = spec['fn']()
    if spec['frames'] > 1:
        frames = [to_array(f) for f in out]
        arr = np.concatenate(frames, 0)
    else:
        arr = to_array(out)
    return finalize(arr, cutout=spec['cutout'], opaque=spec['opaque'])


def save(name, arr):
    path = os.path.join(TEX_ROOT, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(arr, 'RGBA').save(path, optimize=True)
    spec = REGISTRY[name]
    if spec['frames'] > 1:
        with open(path + '.mcmeta', 'w', encoding='utf-8') as f:
            json.dump(MCMETA, f, indent=2)
            f.write('\n')
    return path


def build_previews(results):
    os.makedirs(PREVIEW, exist_ok=True)
    groups = {'block': [], 'item': [], 'painting': [], 'gui': [], 'environment': []}
    tiles, anim = [], []
    for name, arr in sorted(results.items()):
        cat = name.split('/')[0]
        spec = REGISTRY[name]
        label = name.split('/', 1)[1]
        if spec['frames'] > 1:
            anim.append((label, arr))
            groups[cat].append((label, arr[:16]))
        else:
            groups[cat].append((label, arr))
        if spec['tile']:
            tiles.append((label, arr))
    made = []
    if groups['block']:
        made.append(preview.contact_sheet(groups['block'], os.path.join(PREVIEW, 'sheet_blocks.png'),
                                          cell=128, cols=10, title='MEIKON blocks (x8)'))
        made.append(preview.contact_sheet(groups['block'], os.path.join(PREVIEW, 'sheet_blocks_night.png'),
                                          cell=128, cols=10, night=True, title='MEIKON blocks (x8, dimmed)'))
    small = groups['item'] + [(l, a) for l, a in groups['gui'] if a.shape[0] <= 16]
    if small:
        made.append(preview.contact_sheet(small, os.path.join(PREVIEW, 'sheet_items.png'),
                                          cell=128, cols=8, title='MEIKON items + hud (x8)'))
    if groups['painting']:
        made.append(preview.contact_sheet(groups['painting'], os.path.join(PREVIEW, 'sheet_paintings.png'),
                                          cell=256, cols=4, title='MEIKON paintings'))
    big = [(l, a) for l, a in groups['gui'] if a.shape[0] > 16] + groups['environment']
    if big:
        made.append(preview.contact_sheet(big, os.path.join(PREVIEW, 'sheet_gui.png'),
                                          cell=256, cols=4, title='MEIKON gui + environment'))
    if tiles:
        made.append(preview.tile_preview(tiles, os.path.join(PREVIEW, 'tiles.png'), cols=5))
    for label, arr in anim:
        frames = preview.strip_frames(arr)
        made.append(preview.contact_sheet([(f'{label} {i}', f) for i, f in enumerate(frames)],
                                          os.path.join(PREVIEW, f'anim_{label}.png'), cell=96, cols=8))
    # combined overview sheet: every texture in one image
    over = groups['block'] + groups['item'] + [(l, a) for l, a in groups['gui'] if a.shape[0] <= 16]
    if over and len(results) == len(REGISTRY):
        top = preview.contact_sheet(over, os.path.join(PREVIEW, '_overview_small.png'),
                                    cell=96, cols=14, title='冥婚 ―MEIKON― textures: blocks / items / hud (x6)')
        large = groups['painting'] + big
        frames = []
        for label, arr in anim:
            frames += [(f'{label} {i}', f) for i, f in enumerate(preview.strip_frames(arr)) if i % 4 == 0]
        bot = preview.contact_sheet(large + frames, os.path.join(PREVIEW, '_overview_large.png'),
                                    cell=192, cols=7, title='paintings / gui / environment / portal frames')
        a, b = Image.open(top), Image.open(bot)
        sheet = Image.new('RGB', (max(a.width, b.width), a.height + b.height), (18, 16, 18))
        sheet.paste(a, (0, 0))
        sheet.paste(b, (0, a.height))
        out = os.path.join(PREVIEW, 'contact_sheet.png')
        sheet.save(out)
        os.remove(top)
        os.remove(bot)
        made.append(out)
    return made


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='')
    ap.add_argument('--no-preview', action='store_true')
    ap.add_argument('--no-check', action='store_true')
    args = ap.parse_args()
    pats = [p for p in args.only.split(',') if p]
    names = sorted(n for n in REGISTRY if not pats or any(fnmatch.fnmatch(n, p) for p in pats))
    results = {}
    for n in names:
        arr = render(n)
        save(n, arr)
        results[n] = arr
    print(f'wrote {len(results)} textures')
    if not args.no_preview:
        for p in build_previews(results):
            print('preview:', p)
    if not args.no_check and not pats:
        import check
        ok = check.main()
        sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
