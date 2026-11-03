#!/usr/bin/env python3
"""霊撮 ―REISATSU― procedural texture generator.

Generates every block / item / GUI / particle texture of docs/DESIGN.md §7 (entity textures, gui/jumpscare_* and
gui/medium_portrait belong to the spirits worker) as deterministic pixel art, writes contact sheets / tiling previews
to tools/art/preview/ and runs check.py.

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

import common  # noqa: E402
from common import REGISTRY, TEX_ROOT, PREVIEW, finalize, to_array  # noqa: E402

import rs_modules  # noqa: E402,F401  (imports every texture / model module)
import preview  # noqa: E402

MCMETA = {"animation": {"frametime": 3, "interpolate": True}}
ANIM = {}   # texture name -> mcmeta dict (set by modules for animated textures)


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
    meta = spec.get('mcmeta')
    if spec['frames'] > 1 or meta:
        with open(path + '.mcmeta', 'w', encoding='utf-8') as f:
            json.dump(meta or MCMETA, f, indent=2)
            f.write('\n')
    return path


def build_previews(results):
    os.makedirs(PREVIEW, exist_ok=True)
    groups = {}
    tiles = []
    for name, arr in sorted(results.items()):
        cat = name.split('/')[0]
        label = name.split('/', 1)[1]
        groups.setdefault(cat, []).append((label, arr))
        if REGISTRY[name]['tile']:
            tiles.append((label, arr))
    made = []
    blocks = groups.get('block', [])
    if blocks:
        surf = [(l, a) for l, a in blocks if REGISTRY['block/' + l]['tile']]
        props = [(l, a) for l, a in blocks if not REGISTRY['block/' + l]['tile']]
        if surf:
            made.append(preview.contact_sheet(surf, os.path.join(PREVIEW, 'sheet_surfaces.png'), cell=112, cols=10,
                                              title='REISATSU surfaces / doors (x7)'))
            made.append(preview.contact_sheet(surf, os.path.join(PREVIEW, 'sheet_surfaces_night.png'), cell=112,
                                              cols=10, night=True, title='REISATSU surfaces (night tint)'))
        if props:
            made.append(preview.contact_sheet(props, os.path.join(PREVIEW, 'sheet_prop_atlases.png'), cell=192,
                                              cols=8, title='REISATSU prop atlases'))
    items = groups.get('item', [])
    small_items = [(l, a) for l, a in items if a.shape[0] <= 16]
    if small_items:
        made.append(preview.contact_sheet(small_items, os.path.join(PREVIEW, 'sheet_items.png'),
                                          cell=128, cols=8, title='REISATSU items (x8)'))
    skins = [(l, a) for l, a in items if a.shape[0] > 16]
    if skins:
        made.append(preview.contact_sheet(skins, os.path.join(PREVIEW, 'sheet_item_skins.png'),
                                          cell=192, cols=6, title='held-model skins'))
    gui = groups.get('gui', [])
    if gui:
        made.append(preview.contact_sheet(gui, os.path.join(PREVIEW, 'sheet_gui.png'),
                                          cell=256, cols=4, title='REISATSU gui'))
    parts = groups.get('particle', [])
    if parts:
        made.append(preview.contact_sheet(parts, os.path.join(PREVIEW, 'sheet_particles.png'),
                                          cell=96, cols=10, title='REISATSU particles'))
    if tiles:
        tl = [(l, a) for l, a in tiles if a.shape[0] == 16 and a.shape[1] == 16]
        made.append(preview.tile_preview(tl, os.path.join(PREVIEW, 'tiles.png'), cols=6))
        made.append(preview.tile_preview(tl, os.path.join(PREVIEW, 'tiles_night.png'), cols=6, night=True))
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
