#!/usr/bin/env python3
"""遭難 ―SOUNAN― procedural texture generator.

Generates every block / item / GUI texture listed in docs/DESIGN.md section 5 (entity textures are
made by gen_entities.py together with the Java models; gui/trail_map comes from tools/terrain) as
deterministic pixel art, then writes contact sheets / tiling previews to tools/art/preview/ and runs
check.py.

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
import legacy  # noqa: E402

# textures inherited from MEIKON (exact reproductions), registered first so SOUNAN modules may override
REGISTRY.update(legacy.load())

import s_nature  # noqa: E402,F401  cedar, forest floor, rock, stones, leaves
import s_town  # noqa: E402,F401  village / road / tunnel materials, doors, decals
import s_props  # noqa: E402,F401  prop block model textures
import s_items  # noqa: E402,F401  16x16 item sprites + held-model skins
import s_gui  # noqa: E402,F401  title logo, faces overlay, phone frame / wallpapers / icons
import s_jumpscare  # noqa: E402,F401  painted full-screen jumpscare faces
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
        made.append(preview.contact_sheet(blocks, os.path.join(PREVIEW, 'sheet_blocks.png'),
                                          cell=112, cols=12, title='SOUNAN blocks (x7)'))
        made.append(preview.contact_sheet(blocks, os.path.join(PREVIEW, 'sheet_blocks_night.png'),
                                          cell=112, cols=12, night=True, title='SOUNAN blocks (x7, night tint)'))
    items = groups.get('item', [])
    small_items = [(l, a) for l, a in items if a.shape[0] <= 16]
    if small_items:
        made.append(preview.contact_sheet(small_items, os.path.join(PREVIEW, 'sheet_items.png'),
                                          cell=128, cols=8, title='SOUNAN items (x8)'))
    skins = [(l, a) for l, a in items if a.shape[0] > 16]
    if skins:
        made.append(preview.contact_sheet(skins, os.path.join(PREVIEW, 'sheet_item_skins.png'),
                                          cell=192, cols=6, title='held-model skins'))
    gui = groups.get('gui', [])
    if gui:
        made.append(preview.contact_sheet(gui, os.path.join(PREVIEW, 'sheet_gui.png'),
                                          cell=256, cols=4, title='SOUNAN gui'))
    if tiles:
        made.append(preview.tile_preview(tiles, os.path.join(PREVIEW, 'tiles.png'), cols=5))
        made.append(preview.tile_preview(tiles, os.path.join(PREVIEW, 'tiles_night.png'), cols=5, night=True))
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
