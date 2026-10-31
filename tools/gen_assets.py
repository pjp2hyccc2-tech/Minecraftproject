#!/usr/bin/env python3
"""Generates Java registries, models, blockstates, textures, lang, loot tables, sounds, skins, shaders and data files.
Run from the repository root:  python3 tools/gen_assets.py [--no-sounds]
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from specs_blocks import BLOCKS  # noqa: E402
from specs_items import ITEM_SPRITES  # noqa: E402
from skins import SKINS, make_skin  # noqa: E402
import sounds  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
RES = os.path.join(ROOT, 'src/main/resources')
ASSETS = os.path.join(RES, 'assets/tabi')
DATA = os.path.join(RES, 'data/tabi')
JAVA = os.path.join(ROOT, 'src/main/java/com/nihonisshu/tabi/registry')
CONTENT = os.path.join(ROOT, 'tools/content')
MANIFEST = json.load(open(os.path.join(CONTENT, 'manifest.json'), encoding='utf-8'))


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def clean(path):
    if os.path.isdir(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


for sub in ('blockstates', 'models/block', 'models/item', 'textures/block', 'textures/item', 'textures/entity/npc', 'lang', 'shaders/core'):
    clean(os.path.join(ASSETS, sub))
clean(os.path.join(DATA, 'loot_tables/blocks'))

lang_ja, lang_en = {}, {}
FACE = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {
        'down': [x1, 16 - z2, x2, 16 - z1], 'up': [x1, z1, x2, z2],
        'north': [16 - x2, 16 - y2, 16 - x1, 16 - y1], 'south': [x1, 16 - y2, x2, 16 - y1],
        'west': [z1, 16 - y2, z2, 16 - y1], 'east': [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]
    uv = [max(0.0, min(16.0, v)) for v in uv]
    if uv[2] - uv[0] < 0.5:
        uv[2] = min(16, uv[0] + 1) if uv[0] < 15 else uv[2]
        uv[0] = uv[2] - 1
    if uv[3] - uv[1] < 0.5:
        uv[3] = min(16, uv[1] + 1) if uv[1] < 15 else uv[3]
        uv[1] = uv[3] - 1
    return uv


def elements_json(elements, panel=False):
    out = []
    for e in elements:
        f, t, spec = e[0:3], e[3:6], e[6]
        faces = {}
        for k, face in FACE.items():
            tex = spec if isinstance(spec, str) else spec.get(k)
            if tex is None:
                continue
            full = tex.endswith('!') or (panel and k in ('n', 's'))
            tex = tex.rstrip('!')
            faces[face] = {'texture': '#' + tex, 'uv': [0, 0, 16, 16] if full else default_uv(face, f, t)}
        el = {'from': list(f), 'to': list(t), 'faces': faces}
        if len(e) > 7:
            el['rotation'] = e[7]
        out.append(el)
    return out


def shape_boxes(b):
    if b.get('shape'):
        boxes = [b['shape']]
    else:
        boxes = [e[0:6] for e in b.get('elements', [])]
    flat = []
    for bx in boxes:
        x1, y1, z1, x2, y2, z2 = [max(0.0, min(16.0, float(v))) for v in bx]
        if x2 - x1 < 0.01 or y2 - y1 < 0.01 or z2 - z1 < 0.01:
            continue
        flat += [x1, y1, z1, x2, y2, z2]
    return flat


def jnum(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return s if s else '0'


def facing_variants(model, extra=None):
    v = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        d = {'model': model}
        if y:
            d['y'] = y
        v[f'facing={f}'] = d
    return {'variants': v}


def save_tex(name, painter, folder='block'):
    img = painter()
    img.save(os.path.join(ASSETS, 'textures', folder, name + '.png'))


def stairs_blockstate(model):
    v = {}
    for facing, y0 in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                m = model + ('_inner' if 'inner' in shape else '_outer' if 'outer' in shape else '')
                y = y0
                if shape in ('inner_left', 'outer_left'):
                    y = (y0 + 270) % 360 if half == 'bottom' else y0
                elif shape in ('inner_right', 'outer_right'):
                    y = y0 if half == 'bottom' else (y0 + 90) % 360
                d = {'model': m}
                if half == 'top':
                    d['x'] = 180
                if y:
                    d['y'] = y
                if half == 'top' or y:
                    d['uvlock'] = True
                v[f'facing={facing},half={half},shape={shape}'] = d
    return {'variants': v}


def pane_blockstate(m):
    return {'multipart': [
        {'apply': {'model': m + '_post'}},
        {'when': {'north': 'true'}, 'apply': {'model': m + '_side'}},
        {'when': {'east': 'true'}, 'apply': {'model': m + '_side', 'y': 90}},
        {'when': {'south': 'true'}, 'apply': {'model': m + '_side_alt'}},
        {'when': {'west': 'true'}, 'apply': {'model': m + '_side_alt', 'y': 90}},
        {'when': {'north': 'false'}, 'apply': {'model': m + '_noside'}},
        {'when': {'east': 'false'}, 'apply': {'model': m + '_noside_alt'}},
        {'when': {'south': 'false'}, 'apply': {'model': m + '_noside_alt', 'y': 90}},
        {'when': {'west': 'false'}, 'apply': {'model': m + '_noside', 'y': 270}},
    ]}


# ===================================================================== blocks
java_blocks = []
panels = []
block_items = []
BS = os.path.join(ASSETS, 'blockstates')
MB = os.path.join(ASSETS, 'models/block')
MI = os.path.join(ASSETS, 'models/item')

for b in BLOCKS:
    bid, kind = b['id'], b['kind']
    M = f'tabi:block/{bid}'
    lang_ja[f'block.tabi.{bid}'] = b['ja']
    lang_en[f'block.tabi.{bid}'] = b['en']
    texmap = {}
    for tname, painter in b['tex'].items():
        fname = bid if (tname == 'all') else f'{bid}_{tname}'
        save_tex(fname, painter)
        texmap[tname] = f'tabi:block/{fname}'
    render = b.get('render')
    p = b['props']
    props = f'BlockBehaviour.Properties.of().mapColor(MapColor.{p["map"]}).strength({p["strength"][0]}f, {p["strength"][1]}f).sound(SoundType.{p["sound"]})'
    if b.get('light'):
        props += f'.lightLevel(s -> {b["light"]})'
    if b['id'] in ('window_lit', 'window_lit_cool', 'neon_sign', 'vending_machine_top', 'gas_lamp', 'street_lamp', 'chochin', 'kenbaiki_top', 'red_steel_lit', 'train_lcd', 'white_steel_lit'):
        props += '.emissiveRendering((s, l, q) -> true)'
    solid_cube = kind in ('cube', 'cube_bt', 'facing_cube', 'pillar') and not render
    if not solid_cube and kind not in ('stairs', 'slab'):
        props += '.noOcclusion()'
    if b.get('nocollide'):
        props += '.noCollission()'
    if kind in ('crop', 'rice'):
        props += '.noCollission().randomTicks().instabreak().pushReaction(PushReaction.DESTROY)'
    if kind == 'cross':
        props += '.noCollission().instabreak()'
    const = bid.upper()
    model_json = None
    item_model = {'parent': M}

    if kind == 'cube':
        model_json = {'parent': 'minecraft:block/cube_all', 'textures': {'all': texmap['all']}}
        if render:
            model_json['render_type'] = 'minecraft:' + render
        wjson(f'{BS}/{bid}.json', {'variants': {'': {'model': M}}})
        ctor = f'new Block({props})'
    elif kind == 'cube_bt':
        model_json = {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': texmap['top'], 'side': texmap['side'], 'bottom': texmap.get('bottom', texmap['side'])}}
        wjson(f'{BS}/{bid}.json', {'variants': {'': {'model': M}}})
        ctor = f'new Block({props})'
    elif kind == 'facing_cube':
        tx = lambda k: texmap[b.get(k, k)] if b.get(k, k) in texmap else texmap['side']
        model_json = {'parent': 'minecraft:block/orientable_with_bottom',
                      'textures': {'top': tx('top'), 'bottom': tx('bottom'), 'front': tx('front'), 'side': texmap['side']}}
        wjson(f'{BS}/{bid}.json', facing_variants(M))
        ctor = f'new FacingBlock({props})'
    elif kind == 'pillar':
        model_json = {'parent': 'minecraft:block/cube_column', 'textures': {'end': texmap['end'], 'side': texmap['side']}}
        wjson(f'{BS}/{bid}.json', {'variants': {'axis=y': {'model': M}, 'axis=z': {'model': M, 'x': 90}, 'axis=x': {'model': M, 'x': 90, 'y': 90}}})
        ctor = f'new RotatedPillarBlock({props})'
    elif kind == 'stairs':
        base = f'tabi:block/{b["of"]}'
        for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
            wjson(f'{MB}/{bid}{suffix}.json', {'parent': f'minecraft:block/{parent}', 'textures': {'bottom': base, 'top': base, 'side': base}})
        wjson(f'{BS}/{bid}.json', stairs_blockstate(M))
        ctor = f'new StairBlock(() -> {b["of"].upper()}.get().defaultBlockState(), {props})'
    elif kind == 'slab':
        base = f'tabi:block/{b["of"]}'
        wjson(f'{MB}/{bid}.json', {'parent': 'minecraft:block/slab', 'textures': {'bottom': base, 'top': base, 'side': base}})
        wjson(f'{MB}/{bid}_top.json', {'parent': 'minecraft:block/slab_top', 'textures': {'bottom': base, 'top': base, 'side': base}})
        wjson(f'{BS}/{bid}.json', {'variants': {'type=bottom': {'model': M}, 'type=top': {'model': M + '_top'}, 'type=double': {'model': base}}})
        ctor = f'new SlabBlock({props})'
    elif kind == 'pane':
        for part in ('post', 'side', 'side_alt', 'noside', 'noside_alt'):
            mj = {'parent': f'minecraft:block/template_glass_pane_{part}', 'textures': {'pane': texmap['pane'], 'edge': texmap['edge']}}
            mj['render_type'] = 'minecraft:' + (render or 'cutout')
            wjson(f'{MB}/{bid}_{part}.json', mj)
        wjson(f'{BS}/{bid}.json', pane_blockstate(M))
        item_model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': texmap['pane']}}
        ctor = f'new PaneBlock({props})'
    elif kind == 'cross':
        model_json = {'parent': 'minecraft:block/cross', 'textures': {'cross': texmap['cross']}, 'render_type': 'minecraft:' + (render or 'cutout')}
        wjson(f'{BS}/{bid}.json', {'variants': {'': {'model': M}}})
        item_model = {'parent': 'minecraft:item/generated', 'textures': {'layer0': texmap['cross']}}
        ctor = f'new ShapedBlock({props}, TabiShapes.of(4, 0, 4, 12, 16, 12), true)'
    elif kind == 'crop':
        crop = bid.replace('_crop', '')
        for i in range(4):
            wjson(f'{MB}/{bid}_stage{i}.json', {'parent': 'minecraft:block/crop', 'textures': {'crop': texmap[f'{crop}_{i}']}, 'render_type': 'minecraft:cutout'})
        wjson(f'{BS}/{bid}.json', {'variants': {f'age={a}': {'model': f'{M}_stage{min(3, a // 2)}'} for a in range(8)}})
        ctor = f'new TabiCropBlock({props}, () -> TabiItems.{b["seed"].upper()}.get())'
        item_model = None
    elif kind == 'rice':
        for i in range(4):
            hgt = [12, 20, 24, 24][i]
            els = [{'from': [0.8, 0, 8], 'to': [15.2, hgt, 8], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}, 'shade': False,
                    'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#cross'}, 'south': {'uv': [0, 0, 16, 16], 'texture': '#cross'}}},
                   {'from': [8, 0, 0.8], 'to': [8, hgt, 15.2], 'rotation': {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}, 'shade': False,
                    'faces': {'west': {'uv': [0, 0, 16, 16], 'texture': '#cross'}, 'east': {'uv': [0, 0, 16, 16], 'texture': '#cross'}}}]
            wjson(f'{MB}/{bid}_stage{i}.json', {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'minecraft:cutout',
                                                'textures': {'particle': texmap[f'rice_{i}'], 'cross': texmap[f'rice_{i}']}, 'elements': els})
        wjson(f'{BS}/{bid}.json', {'variants': {f'age={a}': {'model': f'{M}_stage{a}'} for a in range(4)}})
        ctor = f'new RicePlantBlock({props})'
        item_model = None
    else:  # element models: shaped / seat / futon / action / panel
        texs = dict(texmap)
        texs['particle'] = list(texmap.values())[0]
        model_json = {'parent': 'minecraft:block/block', 'textures': {k: v for k, v in texs.items()},
                      'elements': elements_json(b['elements'], panel=(kind == 'panel'))}
        model_json['render_type'] = 'minecraft:' + (render or 'cutout')
        facing = b.get('facing', True)
        boxes = ', '.join(jnum(v) for v in shape_boxes(b))
        nocol = 'true' if b.get('nocollide') else 'false'
        if kind == 'shaped' and not facing:
            wjson(f'{BS}/{bid}.json', {'variants': {'': {'model': M}}})
            ctor = f'new ShapedBlock({props}, TabiShapes.of({boxes}), {nocol})'
        else:
            wjson(f'{BS}/{bid}.json', facing_variants(M))
            shapes = f'TabiShapes.horizontal({boxes})'
            if kind == 'shaped':
                ctor = f'new FacingBlock({props}, {shapes}, {nocol})'
            elif kind == 'seat':
                ctor = f'new SeatBlock({props}, {shapes}, {b["seat"]})'
            elif kind == 'futon':
                ctor = f'new FutonBlock({props}, {shapes})'
            elif kind == 'action':
                ctor = f'new ActionBlock({props}, {shapes}, "{b["action"]}")'
            elif kind == 'panel':
                fd, bd, cy, th = b['panel']
                ctor = f'new TextPanelBlock({props}, {shapes}, {nocol}, {fd}, {bd}, {cy}f, {th}f)'
                panels.append(const)
            else:
                raise ValueError(kind)
    if model_json is not None:
        wjson(f'{MB}/{bid}.json', model_json)
    if b.get('item', True) and item_model is not None:
        wjson(f'{MI}/{bid}.json', item_model)
        block_items.append(bid)
    java_blocks.append(f'    public static final RegistryObject<Block> {const} = BLOCKS.register("{bid}", () -> {ctor});')
    # loot: drop itself (crops/rice handled by their own logic)
    if kind == 'crop':
        crop = bid.replace('_crop', '')
        loot = {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': f'tabi:{crop}', 'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'tabi:{bid}', 'properties': {'age': '7'}}],
                 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}}]},
                {'type': 'minecraft:item', 'name': f'tabi:{crop}_seeds'}]}]},
            {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'tabi:{crop}_seeds'}],
             'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'tabi:{bid}', 'properties': {'age': '7'}}]}]}
    elif kind == 'rice':
        loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'tabi:rice_seedling'}]}]}
    elif kind == 'slab':
        loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'tabi:{bid}', 'functions': [
            {'function': 'minecraft:set_count', 'count': 2, 'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'tabi:{bid}', 'properties': {'type': 'double'}}]}]}]}]}
    else:
        loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'tabi:{bid}'}],
                                                      'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    wjson(os.path.join(DATA, f'loot_tables/blocks/{bid}.json'), loot)

java = ['package com.nihonisshu.tabi.registry;', '',
        '// GENERATED by tools/gen_assets.py - do not edit by hand.', '',
        'import com.nihonisshu.tabi.TabiMod;',
        'import com.nihonisshu.tabi.block.*;',
        'import net.minecraft.world.level.block.*;',
        'import net.minecraft.world.level.block.state.BlockBehaviour;',
        'import net.minecraft.world.level.material.MapColor;',
        'import net.minecraft.world.level.material.PushReaction;',
        'import net.minecraftforge.registries.DeferredRegister;',
        'import net.minecraftforge.registries.ForgeRegistries;',
        'import net.minecraftforge.registries.RegistryObject;', '',
        'public final class TabiBlocks {',
        '    private TabiBlocks() {}', '',
        '    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TabiMod.MODID);', '']
java += java_blocks
java += ['', '    public static Block[] textPanels() {',
         '        return new Block[]{' + ', '.join(f'{c}.get()' for c in panels) + '};', '    }', '}', '']
open(os.path.join(JAVA, 'TabiBlocks.java'), 'w', encoding='utf-8').write('\n'.join(java))

# ===================================================================== items
item_text = {}
p_it = os.path.join(CONTENT, 'item_text.json')
if os.path.exists(p_it):
    item_text = json.load(open(p_it, encoding='utf-8'))
java_items = []
for it in MANIFEST['items']:
    iid, kind = it['id'], it['kind']
    lang_ja[f'item.tabi.{iid}'] = it['ja']
    lang_en[f'item.tabi.{iid}'] = it['en']
    tx = item_text.get(iid, {})
    if tx.get('desc_ja'):
        lang_ja[f'item.tabi.{iid}.desc'] = tx['desc_ja']
    if tx.get('desc_en'):
        lang_en[f'item.tabi.{iid}.desc'] = tx['desc_en']
    img = ITEM_SPRITES[iid]()
    img.save(os.path.join(ASSETS, 'textures/item', iid + '.png'))
    wjson(f'{MI}/{iid}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'tabi:item/{iid}'}})
    const = iid.upper()
    if kind == 'journal':
        ctor = 'new JournalItem(new Item.Properties().stacksTo(1))'
    elif kind == 'ticket':
        ctor = 'new TicketItem(new Item.Properties().stacksTo(1))'
    elif kind == 'plant':
        ctor = 'new ItemNameBlockItem(TabiBlocks.RICE_PLANT.get(), new Item.Properties())'
    elif kind == 'seed':
        ctor = f'new ItemNameBlockItem(TabiBlocks.{iid.replace("_seeds", "").upper()}_CROP.get(), new Item.Properties())'
    elif kind in ('food', 'drink'):
        nut = {'tomato': 2, 'cucumber': 2, 'eggplant': 2}.get(iid)
        src = next((x for x in MANIFEST['items'] if x['id'] == iid), None)
        # nutrition is encoded in the manifest builder; fall back to 4
        nutrition = {'onigiri': 6, 'mugicha': 1, 'suika': 3, 'kakigori': 2, 'ramune': 1, 'ice_candy': 2, 'dagashi': 1, 'cider': 1,
                     'ayu_shioyaki': 6, 'cream_soda': 3, 'coffee': 1, 'kocha': 1, 'akashiyaki': 6, 'butaman': 6, 'xiaolongbao': 5,
                     'goma_dango': 3, 'sobameshi': 8, 'wagyu_kushi': 8, 'baumkuchen': 4, 'kobe_pudding': 3, 'cheesecake': 4,
                     'croissant': 4, 'curry_pan': 5, 'crepe': 4, 'kawara_senbei': 2, 'kobe_cookies': 3, 'ikanago_kugini': 4,
                     'amazake': 2, 'arima_senbei': 2, 'ekiben_okawa': 10, 'ekiben_shiomi': 10, 'ekiben_kobe': 12}.get(iid, nut or 2)
        sat = 0.6 if nutrition >= 5 else 0.3
        food = f'new FoodProperties.Builder().nutrition({nutrition}).saturationMod({sat}f).alwaysEat().build()'
        ctor = f'new TabiFoodItem(new Item.Properties().food({food}), {"true" if kind == "drink" else "false"}, "{it["journal"] or ""}")'
    else:
        ctor = 'new TabiItem(new Item.Properties())'
    java_items.append(f'    public static final RegistryObject<Item> {const} = ITEMS.register("{iid}", () -> {ctor});')
for bid in block_items:
    java_items.append(f'    public static final RegistryObject<Item> {bid.upper()}_BLOCK = ITEMS.register("{bid}", () -> new BlockItem(TabiBlocks.{bid.upper()}.get(), new Item.Properties()));')
java = ['package com.nihonisshu.tabi.registry;', '',
        '// GENERATED by tools/gen_assets.py - do not edit by hand.', '',
        'import com.nihonisshu.tabi.TabiMod;',
        'import com.nihonisshu.tabi.item.*;',
        'import net.minecraft.world.food.FoodProperties;',
        'import net.minecraft.world.item.BlockItem;',
        'import net.minecraft.world.item.Item;',
        'import net.minecraft.world.item.ItemNameBlockItem;',
        'import net.minecraftforge.registries.DeferredRegister;',
        'import net.minecraftforge.registries.ForgeRegistries;',
        'import net.minecraftforge.registries.RegistryObject;', '',
        'public final class TabiItems {',
        '    private TabiItems() {}', '',
        '    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TabiMod.MODID);', '']
java += java_items + ['}', '']
open(os.path.join(JAVA, 'TabiItems.java'), 'w', encoding='utf-8').write('\n'.join(java))

# ===================================================================== skins
for name, params in SKINS.items():
    make_skin(params, seed=hash(name) % 1000).save(os.path.join(ASSETS, 'textures/entity/npc', name + '.png'))

# ===================================================================== sounds
sj = {}
for sid, (fn, ja, en) in sounds.SOUNDS.items():
    sj[sid] = {'sounds': [{'name': f'tabi:{sid}', 'stream': sid in ('melody', 'evening_chime')}], 'subtitle': f'subtitles.tabi.{sid}'}
    lang_ja[f'subtitles.tabi.{sid}'] = ja
    lang_en[f'subtitles.tabi.{sid}'] = en
wjson(os.path.join(ASSETS, 'sounds.json'), sj)
if '--no-sounds' not in sys.argv:
    os.makedirs(os.path.join(ASSETS, 'sounds'), exist_ok=True)
    for sid, (fn, ja, en) in sounds.SOUNDS.items():
        sounds.write_ogg(fn(), os.path.join(ASSETS, 'sounds', sid + '.ogg'))

# ===================================================================== misc lang
lang_ja.update({
    'itemGroup.tabi': '日本一周旅', 'key.categories.tabi': '日本一周旅', 'key.tabi.journal': '旅の手帳を開く', 'key.tabi.grade': '色調モード切替（自動／暖／涼／オフ）',
    'entity.tabi.npc': '住人', 'entity.tabi.vehicle': '乗りもの', 'entity.tabi.seat': '座席',
    'message.tabi.futon_day': 'まだ明るいよ。夜になったら寝よう。',
})
lang_en.update({
    'itemGroup.tabi': 'Nihon Isshu Tabi', 'key.categories.tabi': 'Nihon Isshu Tabi', 'key.tabi.journal': 'Open travel journal', 'key.tabi.grade': 'Colour grading (auto/warm/cool/off)',
    'entity.tabi.npc': 'Resident', 'entity.tabi.vehicle': 'Vehicle', 'entity.tabi.seat': 'Seat',
    'message.tabi.futon_day': "It's still light out. Sleep when night comes.",
})
for k in lang_ja:
    lang_en.setdefault(k, lang_ja[k])
wjson(os.path.join(ASSETS, 'lang/ja_jp.json'), lang_ja)
wjson(os.path.join(ASSETS, 'lang/en_us.json'), lang_en)

# ===================================================================== shader (colour grading)
wjson(os.path.join(ASSETS, 'shaders/core/grade.json'), {
    'vertex': 'tabi:grade', 'fragment': 'tabi:grade', 'attributes': ['Position', 'UV0'],
    'samplers': [{'name': 'Sampler0'}],
    'uniforms': [{'name': n, 'type': 'float', 'count': 1, 'values': [v]} for n, v in (('Warmth', 0.0), ('Saturation', 1.0), ('Vignette', 0.0), ('Contrast', 0.0))],
})
open(os.path.join(ASSETS, 'shaders/core/grade.vsh'), 'w').write('''#version 150

in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
}
''')
open(os.path.join(ASSETS, 'shaders/core/grade.fsh'), 'w').write('''#version 150

uniform sampler2D Sampler0;
uniform float Warmth;
uniform float Saturation;
uniform float Vignette;
uniform float Contrast;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, Saturation);
    // warm: golden highlights, soft amber shadows / cool: blue shadows, clean highlights
    vec3 warmHi = vec3(1.10, 1.01, 0.84);
    vec3 warmLo = vec3(1.06, 0.98, 0.90);
    vec3 coolHi = vec3(0.95, 1.00, 1.06);
    vec3 coolLo = vec3(0.86, 0.95, 1.14);
    vec3 tint;
    if (Warmth >= 0.0) {
        tint = mix(vec3(1.0), mix(warmLo, warmHi, l), Warmth);
    } else {
        tint = mix(vec3(1.0), mix(coolLo, coolHi, l), -Warmth);
    }
    c *= tint;
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = mix(c, s, Contrast);
    vec2 d = texCoord - 0.5;
    float v = 1.0 - Vignette * smoothstep(0.30, 0.80, length(d * vec2(1.25, 1.0)));
    fragColor = vec4(clamp(c * v, 0.0, 1.0), 1.0);
}
''')

# ===================================================================== dimension + biomes
wjson(os.path.join(DATA, 'dimension_type/japan.json'), {
    'ultrawarm': False, 'natural': True, 'piglin_safe': False, 'respawn_anchor_works': False, 'bed_works': True, 'has_raids': False,
    'has_skylight': True, 'has_ceiling': False, 'coordinate_scale': 1.0, 'ambient_light': 0.0, 'logical_height': 384,
    'effects': 'minecraft:overworld', 'infiniburn': '#minecraft:infiniburn_overworld', 'min_y': -64, 'height': 384,
    'monster_spawn_light_level': 0, 'monster_spawn_block_light_limit': 0})
wjson(os.path.join(DATA, 'dimension/japan.json'), {'type': 'tabi:japan', 'generator': {'type': 'tabi:japan', 'biome_source': {'type': 'tabi:japan'}}})


def biome(grass, foliage, water, water_fog, sky=0x78A7FF, fog=0xC0D8FF, temp=0.85, downfall=0.7):
    return {'has_precipitation': True, 'temperature': temp, 'downfall': downfall,
            'effects': {'sky_color': sky, 'fog_color': fog, 'water_color': water, 'water_fog_color': water_fog,
                        'grass_color': grass, 'foliage_color': foliage,
                        'mood_sound': {'sound': 'minecraft:ambient.cave', 'tick_delay': 6000, 'block_search_extent': 8, 'offset': 2.0}},
            'spawners': {}, 'spawn_costs': {}, 'carvers': {}, 'features': []}


wjson(os.path.join(DATA, 'worldgen/biome/satoyama.json'), biome(0x6DB33F, 0x4E9A2C, 0x3F8FC8, 0x0A3A5A))
wjson(os.path.join(DATA, 'worldgen/biome/seto_sea.json'), biome(0x6DB33F, 0x4E9A2C, 0x2E86B8, 0x06304A))
wjson(os.path.join(DATA, 'worldgen/biome/kobe.json'), biome(0x72B048, 0x55A030, 0x2A78A8, 0x062A40))

# ===================================================================== content data (dialogue, shops, bubbles, journal)
dd = os.path.join(DATA, 'dialogue')
clean(dd)
cd = os.path.join(CONTENT, 'dialogue')
if os.path.isdir(cd):
    for fn in sorted(os.listdir(cd)):
        if fn.endswith('.json'):
            shutil.copy(os.path.join(cd, fn), os.path.join(dd, fn))
shops_text = {}
p_st = os.path.join(CONTENT, 'shops_text.json')
if os.path.exists(p_st):
    shops_text = json.load(open(p_st, encoding='utf-8'))
shops = {}
for sid, s in MANIFEST['shops'].items():
    tx = shops_text.get(sid, {})
    items = []
    for row in s['items']:
        e = {'item': f'tabi:{row[0]}', 'price': row[1], 'note': tx.get('notes', {}).get(row[0], '')}
        if len(row) > 2:
            e['journal'] = row[2]
        items.append(e)
    o = {'name': s['name'], 'greet': tx.get('greet', 'いらっしゃいませ'), 'mode': s['mode'], 'items': items}
    if s.get('journal'):
        o['journal'] = s['journal']
    shops[sid] = o
wjson(os.path.join(DATA, 'shops.json'), {'shops': shops})
for name in ('bubbles.json', 'journal.json'):
    src = os.path.join(CONTENT, name)
    dst = os.path.join(ASSETS, 'tabi', name)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(src):
        shutil.copy(src, dst)
    else:
        wjson(dst, {'pools': {}} if name == 'bubbles.json' else {'entries': {}})

print(f'blocks: {len(BLOCKS)}  block items: {len(block_items)}  items: {len(MANIFEST["items"])}  skins: {len(SKINS)}  panels: {len(panels)}')
