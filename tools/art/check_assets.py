#!/usr/bin/env python3
"""Lists missing client assets for every item / block / effect / projectile registered by core + the armor module.

    python3 tools/art/check_assets.py        # exit code 1 if anything is missing

Checks: item model JSON + every texture it references (16x16), block states + block models + textures,
MonsterProjectile kind textures, literal texture paths in core client code, trap textures, armor layer textures
(+ geometry keys), mob effect icons, and ja_jp / en_us names of items, effects and skills.
"""
import glob
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'huntcraft')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'huntcraft')
LANG = os.path.join(ROOT, 'src', 'main', 'lang')

problems = []
warnings = []


def read(rel):
    with open(os.path.join(JAVA, rel), encoding='utf-8') as f:
        return f.read()


def missing(kind, what):
    problems.append('%-14s %s' % (kind, what))


# ----------------------------------------------------------------------------- registered ids
def core_items():
    src = read('registry/HCItems.java')
    ids = re.findall(r'\b(?:mat|tool)\("([a-z0-9_]+)"', src)
    for m in re.finditer(r'new MonsterMats\("(\w+)", "(\w+)", "(\w+)", "(\w+)", "(\w+)", "(\w+)"', src):
        p, scale, shell, special, claw, gem = m.groups()
        for n in (scale, shell, special, claw, gem):
            ids += ['%s_%s' % (p, n), '%s_%s_plus' % (p, n)]
        ids.append(p + '_tail')
    return ids


def block_items():
    return re.findall(r'\btool\("([a-z0-9_]+)", \(\) -> new BlockItem', read('registry/HCItems.java'))


def armor_items():
    ids = re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', read('gear/armor/ArmorModule.java'))
    pieces = re.search(r'PIECE_NAMES = \{([^}]*)\}', read('gear/armor/ArmorSet.java')).group(1)
    pieces = re.findall(r'"(\w+)"', pieces)
    for sid in re.findall(r'ArmorSet\.builder\("(\w+)"\)', read('gear/armor/ArmorSets.java')):
        ids += ['%s_%s' % (sid, p) for p in pieces]
    return ids


def armor_sets():
    return re.findall(r'ArmorSet\.builder\("(\w+)"\)', read('gear/armor/ArmorSets.java'))


def core_blocks():
    return re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', read('registry/HCBlocks.java'))


def core_effects():
    return re.findall(r'EFFECTS\.register\("([a-z0-9_]+)"', read('registry/HCEffects.java'))


def projectile_kinds():
    src = read('entity/misc/MonsterProjectile.java')
    body = re.search(r'enum Kind \{([^}]*)\}', src).group(1)
    return [k.strip().lower() for k in body.split(',') if k.strip()]


def skills():
    body = re.search(r'enum SkillType \{(.*?);', read('core/SkillType.java'), re.S).group(1)
    return [m.lower() for m in re.findall(r'^\s*([A-Z_]+)\(', body, re.M)]


# ----------------------------------------------------------------------------- resource helpers
def tex_path(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns != 'huntcraft':
        return None
    return os.path.join(ASSETS, 'textures', path + '.png')


def model_path(ref):
    ns, path = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    if ns != 'huntcraft':
        return None
    return os.path.join(ASSETS, 'models', path + '.json')


def check_texture(ref, owner, size=None):
    p = tex_path(ref)
    if p is None:
        return
    if not os.path.exists(p):
        missing('texture', '%s (for %s)' % (os.path.relpath(p, ROOT), owner))
        return
    if size:
        # HD art: any square multiple of the design size up to 4x is fine (icons 16 -> 32 / 64, effects 18 -> 36)
        w, h = Image.open(p).size
        k = w / float(size[0])
        ok = k == h / float(size[1]) and k == int(k) and 1 <= k <= 4
        if not ok:
            warnings.append('size %s is %dx%d, expected %dx%d (or an HD multiple)' % (os.path.relpath(p, ROOT), w, h, size[0], size[1]))


def check_model(ref, owner, depth=0, item_icon=False):
    p = model_path(ref)
    if p is None:
        return
    if not os.path.exists(p):
        missing('model', '%s (for %s)' % (os.path.relpath(p, ROOT), owner))
        return
    try:
        with open(p, encoding='utf-8') as f:
            m = json.load(f)
    except ValueError as e:
        missing('bad json', '%s: %s' % (os.path.relpath(p, ROOT), e))
        return
    for k, t in m.get('textures', {}).items():
        if not t.startswith('#'):
            check_texture(t, owner, (16, 16) if item_icon and '/item/' in '/' + t.split(':')[-1] else None)
    if 'parent' in m and depth < 6:
        check_model(m['parent'], owner, depth + 1)
    for ov in m.get('overrides', []):
        check_model(ov['model'], owner, depth + 1, item_icon)


def lang():
    out = {}
    for lg in ('ja_jp', 'en_us'):
        d = {}
        for f in sorted(glob.glob(os.path.join(LANG, lg, '*.json'))):
            with open(f, encoding='utf-8') as fh:
                d.update(json.load(fh))
        out[lg] = d
    return out


# ----------------------------------------------------------------------------- checks
def main():
    L = lang()

    def need_lang(key):
        for lg, d in L.items():
            if key not in d:
                missing('lang ' + lg, key)

    items = list(dict.fromkeys(core_items()))
    mine = armor_items()
    blocks_as_items = set(block_items())
    for iid in items + mine:
        check_model('huntcraft:item/' + iid, 'item ' + iid, item_icon=True)
        need_lang(('block.huntcraft.' if iid in blocks_as_items else 'item.huntcraft.') + iid)
    for iid in mine:
        need_lang('item.huntcraft.%s.desc' % iid)
    for b in core_blocks():
        p = os.path.join(ASSETS, 'blockstates', b + '.json')
        if not os.path.exists(p):
            missing('blockstate', os.path.relpath(p, ROOT))
            continue
        with open(p, encoding='utf-8') as f:
            st = json.load(f)
        for v in st.get('variants', {}).values():
            for vv in (v if isinstance(v, list) else [v]):
                check_model(vv['model'], 'block ' + b)
        need_lang('block.huntcraft.' + b)
    for k in projectile_kinds():
        check_texture('huntcraft:entity/projectile/' + k, 'MonsterProjectile.' + k.upper())
    for f in glob.glob(os.path.join(JAVA, 'client', '**', '*.java'), recursive=True):
        with open(f, encoding='utf-8') as fh:
            for ref in re.findall(r'HuntCraft\.id\("textures/([a-z0-9_/]+)\.png"\)', fh.read()):
                check_texture('huntcraft:' + ref, os.path.basename(f))
    geo = read('gear/armor/client/ArmorGeometry.java')
    for sid in armor_sets():
        for layer in ('1', '2'):
            check_texture('huntcraft:models/armor/%s_layer_%s' % (sid, layer), 'armor ' + sid, (128, 64))
        for part in ('outer', 'inner'):
            if 'JSON.put("%s_%s"' % (sid, part) not in geo:
                missing('geometry', '%s_%s in ArmorGeometry.java' % (sid, part))
        need_lang('armorset.huntcraft.' + sid)
    for e in core_effects():
        check_texture('huntcraft:mob_effect/' + e, 'effect ' + e, (18, 18))
        need_lang('effect.huntcraft.' + e)
    for s in skills():
        need_lang('skill.huntcraft.' + s)
    for w in warnings:
        print('WARN', w)
    for p in problems:
        print('MISSING', p)
    print('checked %d items (%d core, %d armor module), %d blocks, %d projectile kinds, %d effects, %d skills: %d missing'
          % (len(items) + len(mine), len(items), len(mine), len(core_blocks()), len(projectile_kinds()), len(core_effects()),
             len(skills()), len(problems)))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
