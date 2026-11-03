#!/usr/bin/env python3
"""Contract checker for the HITORI art (docs/DESIGN.md §7, model / state names per REISATSU DESIGN §7.1).

Verifies that
  * every texture the world's plain block models need exists (the Surfaces of §7, the door halves <id>_bottom/_top),
  * every prop / door art model and state-variant name exists (boolean p -> <id> / <id>_<p>; int -> <id>_<n>;
    half / part first), with a particle texture, valid elements, rotations, render types and texture references,
    and that every prop id of §7 is covered by an expectation below,
  * the item sprites, item models and the 3D held models (forge:separate_transforms) exist,
  * the GUI textures exist with their sizes (gui/jumpscare_* belong to the models worker),
  * the particle definitions of §7 exist and point at existing textures,
  * block / item textures are multiples of 16 (mip levels), animated textures have consistent .mcmeta frames,
    cutout textures have binary alpha, and nothing unexpected / orphaned sits in our texture folders.
usage: python3 tools/art/check.py
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
from common import ROOT, TEX_ROOT, MODEL_ROOT  # noqa: E402

DESIGN = os.path.join(ROOT, 'docs', 'DESIGN.md')
ASSETS = os.path.dirname(TEX_ROOT)
NOT_OURS_GUI = re.compile(r'^gui/jumpscare_.*$')
PROPERTY_WORDS = {'on', 'channel', 'lit', 'swing', 'closed', 'water', 'lid', 'running', 'open', 'has_knife',
                  'present', 'ringing', 'unit', 'half', 'part', 'axis'}

# ---------------------------------------------------------------------------------------------- expectations
VANILLA_DOORS = ['front_door', 'interior_door', 'toilet_door', 'bath_door', 'balcony_door']
WORLD_EXTRA_TEXTURES = []
DOOR_ITEM_SPRITES = VANILLA_DOORS + ['fusuma', 'oshiire_fusuma']


def bools(id_, *props):
    """<id>, <id>_<p1>, <id>_<p1>_<p2>, ... for the true ones (in §7 order)."""
    names = [id_]
    for p in props:
        names = names + [n + '_' + p for n in names]
    return set(names)


def expected_models():
    E = {}
    plain = ['chabudai', 'zabuton', 'tv_stand', 'pull_switch', 'futon_folded', 'cardboard_box', 'altar_shelf',
             'shower', 'bath_stool', 'bucket', 'mirror_small', 'toilet_seat', 'rice_bag', 'rice_cooker',
             'salt_shaker', 'cup_glass', 'microwave', 'dining_table', 'dining_chair', 'desk', 'desk_chair',
             'printout', 'shelf', 'camera_bag', 'poster', 'shoe_cabinet', 'shoes', 'umbrella', 'calendar',
             'wall_clock', 'laundry_pole', 'ac_outdoor_unit', 'vending_machine_small', 'mailboxes', 'bicycle',
             'stairs_rail', 'vacancy_sign', 'swing_set', 'puddle', 'rain_window']
    for i in plain:
        E[i] = {i}
    E['crt_tv'] = {'crt_tv'} | {f'crt_tv_on_{c}' for c in range(3)} | {'crt_tv_on'} | {f'crt_tv_{c}' for c in range(3)}
    E['hanging_lamp'] = bools('hanging_lamp', 'lit', 'swing')
    E['fluorescent_ring'] = bools('fluorescent_ring', 'lit')
    E['light_switch'] = bools('light_switch', 'on')
    E['curtain'] = bools('curtain', 'closed')
    E['bathtub'] = {f'bathtub_{p}_{w}' for p in ('head', 'foot') for w in range(3)} | \
        {f'bathtub_{p}_lid' for p in ('head', 'foot')} | \
        {f'bathtub_{p}_lid_{w}' for p in ('head', 'foot') for w in range(3)}
    E['bath_tap'] = bools('bath_tap', 'running')
    E['washing_machine'] = bools('washing_machine', 'running')
    E['washbasin'] = bools('washbasin', 'running')
    E['kitchen_sink'] = bools('kitchen_sink', 'running')
    E['gas_stove'] = bools('gas_stove', 'lit')
    E['fridge_old'] = bools('fridge_old', 'open')
    E['knife_rack'] = bools('knife_rack', 'has_knife')
    E['drawer'] = bools('drawer', 'open')
    E['laptop_block'] = bools('laptop_block', 'open', 'on')
    E['sewing_box'] = bools('sewing_box', 'open')
    E['bed'] = {'bed', 'bed_head', 'bed_foot'}
    E['plush_doll'] = bools('plush_doll', 'present')
    E['desk_lamp'] = bools('desk_lamp', 'lit')
    E['breaker_panel'] = bools('breaker_panel', 'open')
    E['intercom'] = bools('intercom', 'ringing')
    E['street_lamp_old'] = bools('street_lamp_old', 'lit')
    E['utility_pole'] = {'utility_pole', 'utility_pole_top'}
    E['nameplate'] = {f'nameplate_{u}' for u in range(8)}
    for d in ('fusuma', 'oshiire_fusuma'):
        E[d] = {f'{d}_{h}_{o}' for h in ('lower', 'upper') for o in range(3)}
    return E


ITEMS = ['plush', 'plush_open', 'plush_rice', 'ritual_doll', 'scissors', 'rice_scoop', 'red_thread', 'nail_clipper',
         'knife', 'salt', 'cup_water', 'salt_water', 'fixed_camera', 'laptop', 'phone', 'lighter', 'printout',
         'phone_light']
HELD = ['knife', 'laptop', 'phone', 'salt_water', 'ritual_doll', 'lighter', 'fixed_camera', 'phone_light']
PARTICLES = ['rice_grain', 'stuffing', 'water_drip', 'salt_spray', 'ember', 'smoke', 'tv_static_mote']
GUI_SIZES = {'title_logo': (512, 256), 'cctv': (512, 288), 'laptop_frame': (512, 288), 'phone_frame': (512, 288),
             'fusuma_slit': (512, 288), 'closet_dark': (512, 288), 'printout': (256, 320), 'icons': (128, 32),
             'name_field': (256, 48), 'vignette': (256, 256), 'grain': (256, 256)}


# ---------------------------------------------------------------------------------------------- DESIGN parsing
def section(text, start, stop):
    i = text.index(start)
    j = text.index(stop, i + len(start)) if stop else len(text)
    return text[i:j]


def ids_in(par):
    out = []
    for i in re.findall(r'`([a-z0-9_]+)`', par):
        if i.startswith('_') or i in PROPERTY_WORDS:
            continue
        out.append(i)
    return out


def design_ids():
    text = open(DESIGN, encoding='utf-8').read()
    s7 = section(text, '## 7. Registry ids', '## 8.')
    blocks = section(s7, '### Blocks', '### Items')
    surf = section(blocks, 'Surfaces (', 'Doors (')
    doors = section(blocks, 'Doors (', 'Props (')
    props = section(blocks, 'Props (', None)
    items = section(s7, '### Items', '### Entities')
    gui = section(s7, '### GUI', '### Particles')
    parts = section(s7, '### Particles', None)
    door_ids = [i for i in ids_in(doors) if not i.endswith('_bottom') and not i.endswith('_top') and i != 'id']
    gui_sizes = {}
    for m in re.finditer(r'`([a-z_]+)`\s*(\d+)\s*[×x]\s*(\d+)', gui):
        gui_sizes[m.group(1)] = (int(m.group(2)), int(m.group(3)))
    for m in re.finditer(r'`([a-z_]+)`\s*(\d+)²', gui):
        gui_sizes[m.group(1)] = (int(m.group(2)), int(m.group(2)))
    gui_ids = ids_in(gui)
    item_par = items.split('3D held models')[0]
    held_par = items.split('3D held models')[1] if '3D held models' in items else ''
    return dict(surfaces=ids_in(surf), doors=door_ids, props=ids_in(props), items=ids_in(item_par),
                held=[i for i in ids_in(held_par) if i not in ('forge',)], gui=gui_ids, gui_sizes=gui_sizes,
                particles=ids_in(parts))


# ---------------------------------------------------------------------------------------------- model checks
def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def resolve_model(name, kind='block', depth=0):
    p = os.path.join(MODEL_ROOT, kind, name + '.json')
    if not os.path.isfile(p):
        return None
    m = load(p)
    par = m.get('parent', '')
    if par.startswith('hitori:block/') and depth < 5:
        base = resolve_model(par.split('/', 1)[1], 'block', depth + 1)
        if base:
            out = dict(base)
            t = dict(base.get('textures', {}))
            t.update(m.get('textures', {}))
            out['textures'] = t
            for k, v in m.items():
                if k not in ('parent', 'textures'):
                    out[k] = v
            return out
    return m


def tex_path(ref):
    ns, rel = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    return ns, os.path.join(TEX_ROOT, rel + '.png')


_ALPHA = {}


def tex_alpha(path):
    if path not in _ALPHA:
        a = np.asarray(Image.open(path).convert('RGBA'))[..., 3]
        meta = path + '.mcmeta'
        if os.path.isfile(meta):
            an = load(meta).get('animation', {})
            fh = an.get('height', min(a.shape))
            a = a[:fh]
        _ALPHA[path] = a
    return _ALPHA[path]


def check_alpha(label, body, problems):
    """A solid model (no render_type) must not sample transparent texels: they would render opaque."""
    rt = body.get('render_type', '')
    if rt in ('minecraft:cutout', 'minecraft:cutout_mipped', 'minecraft:translucent'):
        return
    texs = body.get('textures', {})
    for el in body.get('elements', []):
        for face, fd in el['faces'].items():
            ref = fd['texture']
            seen = 0
            while ref.startswith('#') and seen < 5:
                ref = texs.get(ref[1:], '')
                seen += 1
            ns, p = tex_path(ref)
            if ns != 'hitori' or not os.path.isfile(p):
                continue
            a = tex_alpha(p)
            h, w = a.shape
            uv = fd.get('uv') or [0, 0, 16, 16]
            u0, u1 = sorted((uv[0], uv[2]))
            v0, v1 = sorted((uv[1], uv[3]))
            x0 = int(np.floor(u0 / 16 * w + 0.02))
            y0 = int(np.floor(v0 / 16 * h + 0.02))
            x1 = max(x0 + 1, int(np.ceil(u1 / 16 * w - 0.02)))
            y1 = max(y0 + 1, int(np.ceil(v1 / 16 * h - 0.02)))
            if (a[y0:y1, x0:x1] < 250).any():
                problems.append(f'SOLIDA   {label}: face {face} samples transparent texels but the model is solid')
                return


def check_body(label, body, problems, used):
    check_alpha(label, body, problems)
    texs = body.get('textures', {})
    for k, v in texs.items():
        if v.startswith('#'):
            continue
        ns, p = tex_path(v)
        if ns == 'hitori':
            used.add(v.split(':', 1)[1])
            if not os.path.isfile(p):
                problems.append(f'TEXREF   {label}: {k} -> {v} does not exist')
    if 'particle' not in texs:
        problems.append(f'PARTICLE {label} has no particle texture')
    rt = body.get('render_type')
    if rt and rt not in ('minecraft:solid', 'minecraft:cutout', 'minecraft:cutout_mipped', 'minecraft:translucent'):
        problems.append(f'RENDER   {label}: render_type {rt}')
    if not body.get('elements'):
        if 'elements' not in body:
            problems.append(f'ELEMENTS {label}: no elements')
    for el in body.get('elements', []):
        for c in el['from'] + el['to']:
            if not -16 <= c <= 32:
                problems.append(f'BOUNDS   {label}: coordinate {c} outside -16..32')
        rot = el.get('rotation')
        if rot and rot['angle'] not in (-45, -22.5, 0, 22.5, 45):
            problems.append(f'ROTATION {label}: angle {rot["angle"]}')
        if rot and rot.get('axis') not in ('x', 'y', 'z'):
            problems.append(f'ROTATION {label}: axis {rot.get("axis")}')
        for face, fd in el['faces'].items():
            ref = fd['texture']
            if ref.startswith('#') and ref[1:] not in texs:
                problems.append(f'TEXREF   {label}: face {face} uses undefined {ref}')
            uv = fd.get('uv')
            if uv and any(not -0.001 <= c <= 16.001 for c in uv):
                problems.append(f'UV       {label}: face {face} uv {uv} outside 0..16')


def main():
    problems = []
    used = set()
    D = design_ids()
    E = expected_models()
    # ---- DESIGN coverage: every prop / door id is covered, and our lists match §7
    for id_ in D['props']:
        if id_ not in E:
            problems.append(f'UNCOVERED §7 prop {id_} has no model expectation in check.py')
    for id_ in D['doors']:
        if id_ not in E and id_ not in VANILLA_DOORS:
            problems.append(f'UNCOVERED §7 door {id_}')
    for id_ in VANILLA_DOORS:
        if id_ not in D['doors']:
            problems.append(f'DESIGN   door {id_} no longer listed in §7')
    for it in D['items']:
        if it not in ITEMS:
            problems.append(f'UNCOVERED §7 item {it}')
    for it in D['held']:
        if it not in HELD:
            problems.append(f'UNCOVERED §7 held model {it}')
    for p in D['particles']:
        if p not in PARTICLES:
            problems.append(f'UNCOVERED §7 particle {p}')
    for g in D['gui']:
        if not g.startswith('jumpscare') and g not in GUI_SIZES:
            problems.append(f'UNCOVERED §7 gui {g}')
    for g, sz in D['gui_sizes'].items():
        if g in GUI_SIZES and GUI_SIZES[g] != sz:
            problems.append(f'DESIGN   gui {g} is {sz} in §7 but {GUI_SIZES[g]} here')
    # ---- textures for the world's plain models
    tex_exp = {}
    for id_ in D['surfaces']:
        tex_exp[f'block/{id_}'] = (16, 16)
    for d in VANILLA_DOORS:
        tex_exp[f'block/{d}_bottom'] = (16, 16)
        tex_exp[f'block/{d}_top'] = (16, 16)
    for t in WORLD_EXTRA_TEXTURES:
        tex_exp[f'block/{t}'] = (16, 16)
    world_textures = set(k.split('/', 1)[1] for k in tex_exp)
    for it in ITEMS + DOOR_ITEM_SPRITES:
        tex_exp[f'item/{it}'] = (16, 16)
    for g, sz in GUI_SIZES.items():
        tex_exp[f'gui/{g}'] = sz
    for rel, size in sorted(tex_exp.items()):
        p = os.path.join(TEX_ROOT, rel + '.png')
        if not os.path.isfile(p):
            problems.append(f'MISSING  textures/{rel}.png')
            continue
        im = Image.open(p)
        if im.mode != 'RGBA':
            problems.append(f'MODE     {rel}.png is {im.mode}')
        if size and im.size != tuple(size) and not os.path.isfile(p + '.mcmeta'):
            problems.append(f'SIZE     {rel}.png is {im.size[0]}x{im.size[1]}, expected {size[0]}x{size[1]}')
    # ---- prop block models
    nmodels = 0
    for id_, names in sorted(E.items()):
        for n in sorted(names):
            m = resolve_model(n)
            if m is None:
                problems.append(f'MISSING  models/block/{n}.json ({id_})')
                continue
            nmodels += 1
            check_body(f'models/block/{n}', m, problems, used)
    expected_names = set().union(*E.values())
    try:
        import hk_modules  # noqa: F401
        import hk_kit
        ours = set(hk_kit.MODELS)
    except Exception as e:  # pragma: no cover
        print('warning: generators not importable:', e)
        ours = set()
    for n in sorted(ours - expected_names):
        problems.append(f'EXTRA    models/block/{n}.json is not a §7 state model')
    bdir = os.path.join(MODEL_ROOT, 'block')
    if os.path.isdir(bdir):
        for f in sorted(os.listdir(bdir)):
            n = f[:-5]
            if f.endswith('.json') and n in ours and n not in expected_names:
                pass
    # ---- items
    for it in ITEMS:
        p = os.path.join(MODEL_ROOT, 'item', it + '.json')
        if not os.path.isfile(p):
            problems.append(f'MISSING  models/item/{it}.json')
            continue
        m = load(p)
        if it in HELD:
            if m.get('loader') != 'forge:separate_transforms':
                problems.append(f'LOADER   models/item/{it}.json is not forge:separate_transforms')
                continue
            check_body(f'models/item/{it} (base)', m['base'], problems, used)
            gui_ = m.get('perspectives', {}).get('gui', {})
            lay = gui_.get('textures', {}).get('layer0', '')
            if not lay or not os.path.isfile(tex_path(lay)[1]):
                problems.append(f'TEXREF   models/item/{it}.json gui sprite {lay} missing')
            if 'display' not in m['base'] or 'firstperson_righthand' not in m['base']['display']:
                problems.append(f'DISPLAY  models/item/{it}.json base has no first person transform')
        else:
            lay = m.get('textures', {}).get('layer0', '')
            if not os.path.isfile(tex_path(lay)[1]):
                problems.append(f'TEXREF   models/item/{it}.json -> {lay} missing')
    # ---- particles
    for pid in PARTICLES:
        p = os.path.join(ASSETS, 'particles', pid + '.json')
        if not os.path.isfile(p):
            problems.append(f'MISSING  particles/{pid}.json')
            continue
        for t in load(p).get('textures', []):
            rel = t.split(':', 1)[1]
            if not os.path.isfile(os.path.join(TEX_ROOT, 'particle', rel + '.png')):
                problems.append(f'TEXREF   particles/{pid}.json -> particle/{rel}.png missing')
            used.add('particle/' + rel)
    # ---- texture hygiene: sizes, animation, cutouts, orphans
    try:
        from common import REGISTRY
        cutouts = {n for n, s in REGISTRY.items() if s['cutout']}
    except Exception:
        cutouts = set()
    counts = {}
    for cat in ('block', 'item', 'gui', 'particle'):
        d = os.path.join(TEX_ROOT, cat)
        for dirpath, _, files in os.walk(d):
            for f in sorted(files):
                if not f.endswith('.png'):
                    continue
                rel = os.path.relpath(os.path.join(dirpath, f), TEX_ROOT)[:-4].replace(os.sep, '/')
                counts[cat] = counts.get(cat, 0) + 1
                im = Image.open(os.path.join(dirpath, f))
                w, h = im.size
                meta = os.path.join(dirpath, f + '.mcmeta')
                if cat in ('block', 'item'):
                    if os.path.isfile(meta):
                        an = load(meta).get('animation', {})
                        fw, fh = an.get('width', min(w, h)), an.get('height', min(w, h))
                        if w % fw or h % fh:
                            problems.append(f'ANIM     {rel}.png {w}x{h} not a multiple of the frame {fw}x{fh}')
                        if fw % 16 or fh % 16:
                            problems.append(f'MIPS     {rel}.png frame {fw}x{fh} is not a multiple of 16')
                        nfr = (w // fw) * (h // fh)
                        for fr in an.get('frames', []):
                            idx = fr['index'] if isinstance(fr, dict) else fr
                            if idx >= nfr:
                                problems.append(f'ANIM     {rel}.png frame index {idx} >= {nfr}')
                    elif w % 16 or h % 16:
                        problems.append(f'MIPS     {rel}.png is {w}x{h} (not a multiple of 16: lowers the atlas mip level)')
                if rel in cutouts:
                    a = np.asarray(im.convert('RGBA'))[..., 3]
                    bad = np.setdiff1d(np.unique(a), [0, 255])
                    if bad.size:
                        problems.append(f'ALPHA    {rel}.png (cutout) has partial alpha')
                if cat == 'gui':
                    if NOT_OURS_GUI.match(rel):
                        continue
                    if rel not in tex_exp:
                        problems.append(f'EXTRA    textures/{rel}.png is not a §7 GUI texture')
                elif cat == 'item':
                    if rel not in tex_exp and not rel.startswith('item/held/'):
                        problems.append(f'EXTRA    textures/{rel}.png is not an item sprite / held skin')
                    if rel.startswith('item/held/') and rel not in used:
                        problems.append(f'ORPHAN   textures/{rel}.png is not used by a held model')
                elif cat == 'block':
                    name = rel.split('/', 1)[1]
                    if name not in world_textures and rel not in used:
                        problems.append(f'ORPHAN   textures/{rel}.png is used by no model and is not a world texture')
                elif cat == 'particle':
                    if rel not in used:
                        problems.append(f'ORPHAN   textures/{rel}.png is not listed by a particle definition')
    nm = sum(len(v) for v in E.values())
    print(f'checked {len(tex_exp)} contract textures, {nm} art block models ({nmodels} present) for '
          f'{len(E)} §7 prop/door ids, {len(ITEMS)} item models ({len(HELD)} held 3D), {len(PARTICLES)} particles; '
          f'texture files: ' + ', '.join(f'{k} {v}' for k, v in sorted(counts.items())))
    if problems:
        print('\n'.join(problems))
        print(f'FAILED: {len(problems)} problem(s)')
        return False
    print('OK: every §7 texture and model exists, models are consistent, textures are mip-safe and none is orphaned')
    return True


if __name__ == '__main__':
    sys.exit(0 if main() else 1)
