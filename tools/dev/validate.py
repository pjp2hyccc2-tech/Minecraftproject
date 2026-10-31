"""Static checks of generated resources and the world palette."""
import gzip, json, os, re, struct, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
A = os.path.join(ROOT, 'src/main/resources/assets/tabi')
sys.path.insert(0, os.path.join(ROOT, 'tools'))
from specs_blocks import BLOCKS
import specs_deco  # noqa
errors = []
def tex_exists(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    if ns != 'tabi':
        return True
    return os.path.exists(os.path.join(A, 'textures', path + '.png'))
def model_exists(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    if ns != 'tabi':
        return True
    return os.path.exists(os.path.join(A, 'models', path + '.json'))
for fn in os.listdir(os.path.join(A, 'blockstates')):
    d = json.load(open(os.path.join(A, 'blockstates', fn)))
    refs = []
    if 'variants' in d:
        for v in d['variants'].values():
            refs += [x['model'] for x in (v if isinstance(v, list) else [v])]
    for part in d.get('multipart', []):
        refs.append(part['apply']['model'])
    for r in refs:
        if not model_exists(r):
            errors.append(f'blockstate {fn}: missing model {r}')
for root, _, files in os.walk(os.path.join(A, 'models')):
    for fn in files:
        d = json.load(open(os.path.join(root, fn)))
        for k, v in d.get('textures', {}).items():
            if not v.startswith('#') and not tex_exists(v):
                errors.append(f'model {fn}: missing texture {v}')
        if 'parent' in d and not model_exists(d['parent']):
            errors.append(f'model {fn}: missing parent {d["parent"]}')
        for el in d.get('elements', []):
            for face, f in el['faces'].items():
                t = f['texture'][1:]
                if t not in d.get('textures', {}):
                    errors.append(f'model {fn}: face texture #{t} undefined')
# items
for fn in os.listdir(os.path.join(A, 'models/item')):
    d = json.load(open(os.path.join(A, 'models/item', fn)))
    p = d.get('parent')
    if p and not model_exists(p):
        errors.append(f'item model {fn}: missing parent {p}')
    for v in d.get('textures', {}).values():
        if not tex_exists(v):
            errors.append(f'item model {fn}: missing texture {v}')
# palette of the world
kinds = {b['id']: b['kind'] for b in BLOCKS}
facing_kinds = {'facing_cube', 'shaped', 'seat', 'futon', 'action', 'panel'}
with gzip.open(os.path.join(ROOT, 'src/main/resources/data/tabi/tabi_world/japan.bin')) as f:
    data = f.read()
assert data[:4] == b'TABI'
n = struct.unpack('>i', data[8:12])[0]
pos = 12
pal = []
for _ in range(n):
    L = struct.unpack('>H', data[pos:pos + 2])[0]
    pal.append(data[pos + 2:pos + 2 + L].decode())
    pos += 2 + L
for s in pal:
    name = s.split('[')[0]
    props = dict(kv.split('=') for kv in s[len(name) + 1:-1].split(',')) if '[' in s else {}
    if name.startswith('tabi:'):
        bid = name[5:]
        k = kinds.get(bid)
        if k is None:
            errors.append(f'palette: unknown tabi block {s}')
            continue
        shaped_nofacing = k == 'shaped' and not next(b for b in BLOCKS if b['id'] == bid).get('facing', True)
        allowed = set()
        if k in facing_kinds and not shaped_nofacing:
            allowed = {'facing'}
        if k == 'facing_cube':
            allowed = {'facing'}
        if k == 'pillar':
            allowed = {'axis'}
        if k == 'slab':
            allowed = {'type', 'waterlogged'}
        if k == 'stairs':
            allowed = {'facing', 'half', 'shape', 'waterlogged'}
        if k == 'pane':
            allowed = {'north', 'south', 'east', 'west', 'waterlogged'}
        if k == 'crop':
            allowed = {'age'}
        if k == 'rice':
            allowed = {'age', 'waterlogged'}
        if k == 'crossing':
            allowed = {'facing', 'flash'}
        bad = set(props) - allowed
        if bad:
            errors.append(f'palette: {s} has invalid props {bad}')
        if k in ('cube', 'cube_bt', 'cross') and props:
            errors.append(f'palette: {s} should have no props')
meta = json.load(open(os.path.join(ROOT, 'src/main/resources/data/tabi/tabi_world/meta.json')))
prof = set(f[:-5] for f in os.listdir(os.path.join(ROOT, 'src/main/resources/data/tabi/dialogue')))
skins = set(f[:-4] for f in os.listdir(os.path.join(A, 'textures/entity/npc')))
for npc in meta['npcs']:
    if npc['profile'] not in prof:
        errors.append(f'npc profile without dialogue: {npc["profile"]}')
    if npc['skin'] not in skins:
        errors.append(f'npc skin missing: {npc["skin"]}')
lang = json.load(open(os.path.join(A, 'lang/ja_jp.json')))
for b in BLOCKS:
    if f'block.tabi.{b["id"]}' not in lang:
        errors.append(f'lang missing block {b["id"]}')
print(f'palette {len(pal)} states, {len(meta["npcs"])} npcs, {len(meta["signs"])} signs, {len(meta["routes"])} routes')
for r in meta['routes']:
    print(' route', r['id'], len(r['points']), 'pts', [(s['id'], s['s']) for s in r['stations']])
print('\n'.join(errors) if errors else 'OK: no errors')
