#!/usr/bin/env python3
"""Validate generated textures against docs/DESIGN.md section 4.

Parses the block table, item list, paintings, GUI and environment entries, then checks every
expected PNG exists with the right size and RGBA mode, cutout textures have binary alpha,
animated strips have their .mcmeta, and nothing unexpected sits in the texture folders.
"""
import json
import os
import re
import sys
sys.dont_write_bytecode = True

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from common import ROOT, TEX_ROOT  # noqa: E402

DESIGN = os.path.join(ROOT, 'docs', 'DESIGN.md')
TOKEN = re.compile(r'`([a-z0-9_]+)`|(\d+)\s*[×x]\s*(\d+)|(\d+)²')


def section(text, start, stop):
    i = text.index(start)
    j = text.index(stop, i + len(start)) if stop else len(text)
    return text[i:j]


def sized_tokens(chunk):
    """Backticked names followed by a size spec ('32×32' or '256²'); pending names share a size."""
    out, pending = {}, []
    for m in TOKEN.finditer(chunk):
        if m.group(1):
            pending.append(m.group(1))
        else:
            if m.group(4):
                w = h = int(m.group(4))
            else:
                w, h = int(m.group(2)), int(m.group(3))
            for n in pending:
                out[n] = (w, h)
            pending = []
    return out


def expected():
    text = open(DESIGN, encoding='utf-8').read()
    sec4 = section(text, '## 4. Registry ids', '## 5.')
    exp = {}   # relpath -> dict(size=(w,h), anim=bool)
    blocks = section(sec4, '### Blocks', '### Items')
    for line in blocks.splitlines():
        if not line.startswith('|') or '---' in line:
            continue
        cols = [c.strip() for c in line.strip('|').split('|')]
        if len(cols) < 2 or cols[0] == 'id':
            continue
        tex = cols[1]
        for m in re.finditer(r'(item\s+)?`([a-z0-9_]+)`', tex):
            name = m.group(2)
            if m.group(1):
                exp[f'item/{name}'] = dict(size=(16, 16), anim=False)
            else:
                anim = name.startswith('torii_gate')
                exp[f'block/{name}'] = dict(size=(16, 256) if anim else (16, 16), anim=anim)
    items = section(sec4, '### Items', '### Paintings')
    for name in re.findall(r'`([a-z0-9_]+)`', items):
        exp[f'item/{name}'] = dict(size=(16, 16), anim=False)
    for name, size in sized_tokens(section(sec4, '### Paintings', '### GUI')).items():
        exp[f'painting/{name}'] = dict(size=size, anim=False)
    for name, size in sized_tokens(section(sec4, '### GUI', '### Entity')).items():
        exp[f'gui/{name}'] = dict(size=size, anim=False)
    env = section(sec4, '### Environment', None)
    for m in re.finditer(r'environment/([a-z0-9_]+)\.png`?\s*(\d+)\s*[×x]\s*(\d+)', env):
        exp[f'environment/{m.group(1)}'] = dict(size=(int(m.group(2)), int(m.group(3))), anim=False)
    return exp


def main():
    exp = expected()
    try:
        from common import REGISTRY
        import t_build, t_props, t_items, t_paint, t_gui  # noqa: F401
        cutouts = {n for n, s in REGISTRY.items() if s['cutout']}
        registered = set(REGISTRY)
    except Exception:  # pragma: no cover - checker usable standalone
        cutouts, registered = set(), set()
    problems = []
    for rel, spec in sorted(exp.items()):
        path = os.path.join(TEX_ROOT, rel + '.png')
        if not os.path.isfile(path):
            problems.append(f'MISSING  {rel}.png')
            continue
        im = Image.open(path)
        if im.mode != 'RGBA':
            problems.append(f'MODE     {rel}.png is {im.mode}, expected RGBA')
        if im.size != spec['size']:
            problems.append(f'SIZE     {rel}.png is {im.size[0]}x{im.size[1]}, expected '
                            f'{spec["size"][0]}x{spec["size"][1]}')
        if rel in cutouts:
            a = np.asarray(im.convert('RGBA'))[..., 3]
            bad = np.setdiff1d(np.unique(a), [0, 255])
            if bad.size:
                problems.append(f'ALPHA    {rel}.png (cutout) has partial alpha values {bad[:6]}')
        if spec['anim']:
            meta = path + '.mcmeta'
            if not os.path.isfile(meta):
                problems.append(f'MCMETA   {rel}.png.mcmeta missing')
            else:
                data = json.load(open(meta, encoding='utf-8'))
                if data != {"animation": {"frametime": 3, "interpolate": True}}:
                    problems.append(f'MCMETA   {rel}.png.mcmeta unexpected content {data}')
    # unexpected files in the folders this tool owns
    for cat in ('block', 'item', 'painting', 'gui', 'environment'):
        d = os.path.join(TEX_ROOT, cat)
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if f.endswith('.png') and f'{cat}/{f[:-4]}' not in exp:
                problems.append(f'EXTRA    {cat}/{f} is not listed in DESIGN.md')
    missing_gen = sorted(set(exp) - registered) if registered else []
    for rel in missing_gen:
        problems.append(f'NOGEN    {rel} has no generator')
    print(f'checked {len(exp)} expected textures '
          f'({sum(1 for r in exp if r.startswith("block/"))} block, '
          f'{sum(1 for r in exp if r.startswith("item/"))} item, '
          f'{sum(1 for r in exp if r.startswith("painting/"))} painting, '
          f'{sum(1 for r in exp if r.startswith("gui/"))} gui, '
          f'{sum(1 for r in exp if r.startswith("environment/"))} environment)')
    if problems:
        print('\n'.join(problems))
        print(f'FAILED: {len(problems)} problem(s)')
        return False
    print('OK: all textures present, sized, RGBA; cutouts binary; animations have mcmeta')
    return True


if __name__ == '__main__':
    sys.exit(0 if main() else 1)
