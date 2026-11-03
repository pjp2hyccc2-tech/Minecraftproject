#!/usr/bin/env python3
"""Contract checker for the HITORI art (docs/DESIGN.md §7 / §7.1).

Verifies that
  * every texture the world's plain block models need exists (Surfaces and structure, door halves, lit panels),
  * every prop block model / state-variant name of §7.1 exists, with a particle texture, valid elements, rotations,
    render types and texture references, and that every furniture id of §7 is covered by the expectations below,
  * the item sprites, item models and the four 3D held models (forge:separate_transforms) exist,
  * the GUI textures exist with the sizes §7 gives (the spirits' jumpscare_* / medium_portrait are not ours),
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
NOT_OURS_GUI = re.compile(r'^gui/(jumpscare_.*|medium_portrait)$')

# ---------------------------------------------------------------------------------------------- expectations
# Surfaces and structure: textures used by the world's plain models (id -> extra textures beyond block/<id>)
SURFACE_EXTRA = {
    'ceiling_light': ['ceiling_light_lit'],
    'downlight': ['downlight_lit'],
    'brick_sill': ['brick_sill_top'],
    'curb': ['curb_top'],
    'pillar': ['pillar_top'],
    'rice_paddy': ['rice_paddy_side'],
    'dining_floor': ['dining_floor_worn'],
}
SURFACE_MODELS = {'pendant_lamp': ['pendant_lamp', 'pendant_lamp_lit'],
                  'exit_sign': ['exit_sign', 'exit_sign_hanging'],
                  'window_blind': ['window_blind', 'window_blind_closed']}
VANILLA_DOORS = ['staff_door', 'office_door', 'restroom_door_m', 'restroom_door_w', 'back_door', 'fridge_door',
                 'stall_door']


def bools(id_, *props):
    """Model names for boolean properties (in §7 order): <id>, <id>_<p1>, <id>_<p1>_<p2>, ... (true ones)."""
    out = {id_}
    names = [id_]
    for p in props:
        names = names + [n + '_' + p for n in names]
    out |= set(names)
    return out


def expected_models():
    E = {}
    E['auto_door'] = {'auto_door_rail'}
    E['door_switch'] = bools('door_switch', 'on')
    E['swing_door'] = {'swing_door_frame'}
    E['booth_seat'] = {'booth_seat'}
    E['booth_partition'] = {'booth_partition'}
    E['restaurant_table'] = {'restaurant_table'}
    E['restaurant_chair'] = bools('restaurant_chair', 'fallen')
    E['call_button'] = bools('call_button', 'lit')
    E['menu_stand'] = {'menu_stand'}
    E['menu_scattered'] = {f'menu_scattered_{v}' for v in range(4)}
    E['water_glass'] = {f'water_glass_{v}' for v in range(1, 5)}
    E['broken_plate'] = {'broken_plate'}
    E['register'] = bools('register', 'open')
    E['service_bell'] = {'service_bell'}
    E['counter'] = {'counter'}
    E['cake_case'] = {'cake_case'}
    E['business_phone'] = bools('business_phone', 'ringing')
    E['reservation_book'] = {'reservation_book'}
    E['capsule_machine'] = {'capsule_machine'}
    E['waiting_bench'] = {'waiting_bench'}
    for i in ('magazine_rack', 'umbrella_stand', 'notice_board', 'coffee_machine', 'ice_machine', 'cup_shelf',
              'soup_warmer', 'server_station', 'planter', 'kids_mat', 'toy_shelf', 'stuffed_bear', 'kids_fence',
              'high_chair', 'griddle', 'kitchen_counter', 'exhaust_hood', 'pot_shelf', 'dish_rack', 'kitchen_trash',
              'prep_table', 'cutting_board', 'pass_bell', 'walkin_shelf', 'time_recorder', 'staff_table',
              'folding_chair', 'bulletin_board', 'cardboard_boxes', 'cleaning_cart', 'filing_cabinet', 'safe',
              'office_desk', 'office_chair', 'pc', 'restroom_mirror', 'toilet', 'urinal', 'stall_wall',
              'paper_towel', 'bike_rack', 'trash_station', 'ac_unit', 'gas_cylinder', 'dumpster', 'wheel_stop',
              'outdoor_planter', 'guardrail', 'chevron_sign', 'curve_mirror', 'flower_offering', 'note_paper'):
        E[i] = {i}
    E['frost'] = {'frost', 'frost_wall'}
    E['drink_bar'] = bools('drink_bar', 'pouring')
    E['call_display'] = {f'call_display_{n}' for n in range(18)} | {'call_display_closed'}
    E['gas_range'] = bools('gas_range', 'lit')
    E['fryer'] = bools('fryer', 'on')
    E['kitchen_sink'] = bools('kitchen_sink', 'running')
    E['dishwasher'] = bools('dishwasher', 'running')
    E['pass_shelf'] = bools('pass_shelf', 'lit')
    E['locker'] = {'locker_lower', 'locker_lower_open', 'locker_upper', 'locker_upper_open'}
    E['crt_tv'] = bools('crt_tv', 'on')
    E['security_monitors'] = bools('security_monitors', 'on', 'alert')
    E['security_camera'] = bools('security_camera', 'on', 'seen')
    E['breaker_panel'] = bools('breaker_panel', 'open')
    E['restroom_sink'] = bools('restroom_sink', 'running')
    E['mirror'] = bools('mirror', 'cracked')
    E['hand_dryer'] = bools('hand_dryer', 'running')
    E['vending_machine'] = {'vending_machine_lower', 'vending_machine_upper'}
    E['pole_sign'] = {'pole_sign_pole', 'pole_sign_pole_lit'} | {f'pole_sign_tile_{t}' for t in range(16)} | \
        {f'pole_sign_tile_{t}_lit' for t in range(16)}
    E['roof_sign'] = {f'roof_sign_tile_{t}' for t in range(8)} | {f'roof_sign_tile_{t}_lit' for t in range(8)}
    E['menu_board'] = {'menu_board_0', 'menu_board_1'}
    E['street_lamp'] = {'street_lamp_pole', 'street_lamp_pole_lit', 'street_lamp_head', 'street_lamp_head_lit'}
    E['utility_pole'] = {'utility_pole_pole', 'utility_pole_top'}
    E['traffic_light'] = bools('traffic_light', 'lit')
    for k, v in SURFACE_MODELS.items():
        E[k] = set(v)
    return E


ITEMS = ['reisha_camera', 'reisha_camera_finder', 'film_pack', 'evidence_photo', 'flashlight', 'flashlight_on', 'ofuda',
         'shop_key', 'office_key']
HELD = ['reisha_camera', 'reisha_camera_finder', 'flashlight', 'flashlight_on']
DOOR_ITEM_SPRITES = VANILLA_DOORS + ['auto_door', 'swing_door']
SPIRITS = ['visitor', 'waitress', 'chef', 'child', 'mirror_woman', 'manager']
PARTICLES = ['menu_flutter', 'water_drop', 'wet_print', 'ember', 'spirit_mote', 'frost_breath', 'paper_ash']


# ---------------------------------------------------------------------------------------------- DESIGN parsing
def section(text, start, stop):
    i = text.index(start)
    j = text.index(stop, i + len(start)) if stop else len(text)
    return text[i:j]


def design_ids():
    text = open(DESIGN, encoding='utf-8').read()
    s7 = section(text, '## 7. Registry ids', '## 8.')
    blocks = section(s7, '### Blocks', '### 7.1')
    groups = {}
    cur = None
    for line in blocks.splitlines():
        for head in ('Surfaces and structure', 'Doors', 'Furniture and fixtures'):
            if line.startswith(head):
                cur = head
        if cur and line.startswith('| `'):
            cols = [c.strip() for c in line.strip('|').split('|')]
            first = re.sub(r'\([^)]*\)', '', cols[0])
            for i in re.findall(r'`([a-z0-9_]+)`', first):
                groups.setdefault(cur, []).append(i)
    gui = section(s7, '### GUI', '### Particles')
    sizes = {}
    for m in re.finditer(r'`([a-z0-9_<>.]+)`\s*(\d+)\s*[×x]\s*(\d+)|`([a-z0-9_<>.]+)`\s*(\d+)²', gui):
        if m.group(1):
            sizes[m.group(1)] = (int(m.group(2)), int(m.group(3)))
        else:
            sizes[m.group(4)] = (int(m.group(5)), int(m.group(5)))
    for m in re.finditer(r'`([a-z_]+)`, `([a-z_]+)` (\d+)²', gui):
        sizes[m.group(1)] = sizes[m.group(2)] = (int(m.group(3)), int(m.group(3)))
    parts = section(s7, '### Particles', None)
    pids = re.findall(r'`([a-z_]+)` \(', parts)
    return groups, sizes, pids


def gui_expectations(sizes):
    exp = {}
    for k, v in sizes.items():
        if k.startswith('jumpscare') or k == 'medium_portrait':
            continue
        if k == 'photo_name_<spirit>':
            for s in SPIRITS:
                exp[f'gui/photo_name_{s}'] = v
        elif k == 'photo_ghost_1..3':
            for i in (1, 2, 3):
                exp[f'gui/photo_ghost_{i}'] = v
        else:
            exp[f'gui/{k}'] = v
    for n in ('vignette', 'grain', 'paper'):
        exp.setdefault(f'gui/{n}', (256, 256))
    return exp


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
    for el in body.get('elements', []):
        for c in el['from'] + el['to']:
            if not -16 <= c <= 32:
                problems.append(f'BOUNDS   {label}: coordinate {c} outside -16..32')
        rot = el.get('rotation')
        if rot and rot['angle'] not in (-45, -22.5, 0, 22.5, 45):
            problems.append(f'ROTATION {label}: angle {rot["angle"]}')
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
    groups, gui_sizes, pids = design_ids()
    E = expected_models()
    # every id of the furniture / doors tables is covered by an expectation (or is a vanilla door)
    for id_ in groups.get('Furniture and fixtures', []) + groups.get('Doors', []):
        if id_ not in E and id_ not in VANILLA_DOORS:
            problems.append(f'UNCOVERED §7 id {id_} has no model expectation in check.py')
    for id_ in SURFACE_MODELS:
        if id_ not in groups.get('Surfaces and structure', []):
            problems.append(f'DESIGN   {id_} no longer listed in Surfaces and structure')
    # ---- textures for the world's plain models
    tex_exp = {}
    for id_ in groups.get('Surfaces and structure', []):
        if id_ in SURFACE_MODELS:
            continue
        tex_exp[f'block/{id_}'] = None
        for e in SURFACE_EXTRA.get(id_, []):
            tex_exp[f'block/{e}'] = None
    for d in VANILLA_DOORS:
        tex_exp[f'block/{d}_bottom'] = None
        tex_exp[f'block/{d}_top'] = None
    tex_exp['block/stall_door_occupied_bottom'] = None
    tex_exp['block/stall_door_occupied_top'] = None
    tex_exp['block/auto_door_panel'] = (16, 48)
    tex_exp['block/swing_door'] = (16, 32)
    world_textures = set(k.split('/', 1)[1] for k in tex_exp)
    for it in ITEMS + DOOR_ITEM_SPRITES:
        tex_exp[f'item/{it}'] = (16, 16)
    tex_exp.update(gui_expectations(gui_sizes))
    for rel, size in sorted(tex_exp.items()):
        p = os.path.join(TEX_ROOT, rel + '.png')
        if not os.path.isfile(p):
            problems.append(f'MISSING  textures/{rel}.png')
            continue
        im = Image.open(p)
        if im.mode != 'RGBA':
            problems.append(f'MODE     {rel}.png is {im.mode}')
        if size and im.size != tuple(size):
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
    # models we wrote that nobody expects (typos / stale names)
    expected_names = set().union(*E.values())
    bdir = os.path.join(MODEL_ROOT, 'block')
    try:
        import hk_modules  # noqa: F401
        import hk_kit
        ours = set(hk_kit.MODELS)
    except Exception as e:  # pragma: no cover
        print('warning: generators not importable:', e)
        ours = set()
    for n in sorted(ours - expected_names):
        problems.append(f'EXTRA    models/block/{n}.json is not a §7.1 state model')
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
    if sorted(pids) != sorted(PARTICLES):
        problems.append(f'DESIGN   particle ids changed: {pids}')
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
    print(f'checked {len(tex_exp)} contract textures, {nm} §7.1 block models ({nmodels} present), {len(ITEMS)} item models '
          f'({len(HELD)} held 3D), {len(PARTICLES)} particles; texture files: ' +
          ', '.join(f'{k} {v}' for k, v in sorted(counts.items())))
    if problems:
        print('\n'.join(problems))
        print(f'FAILED: {len(problems)} problem(s)')
        return False
    print('OK: every §7/§7.1 texture and model exists, models are consistent, textures are mip-safe and none is orphaned')
    return True


if __name__ == '__main__':
    sys.exit(0 if main() else 1)
