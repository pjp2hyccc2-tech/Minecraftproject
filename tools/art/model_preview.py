#!/usr/bin/env python3
"""Renders every HITORI prop block model to contact sheets (two views each) -> tools/art/preview/props_<group>.png.

Also the texture lookup used by other preview tools (gen_entities.py): tex_lookup('hitori:block/dining_floor').
usage: python3 tools/art/model_preview.py [glob ...]
"""
import fnmatch
import json
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import PREVIEW, MODEL_ROOT  # noqa: E402
import hk_render  # noqa: E402

tex_lookup = hk_render.tex


def load_model(kind, name):
    with open(os.path.join(MODEL_ROOT, kind, name + '.json'), encoding='utf-8') as f:
        return json.load(f)


GROUPS = {
    'washitsu': ['chabudai', 'zabuton', 'crt_tv', 'crt_tv_on_*', 'tv_stand', 'hanging_lamp*', 'fluorescent_ring*',
                 'pull_switch', 'light_switch*', 'curtain*', 'futon_folded', 'cardboard_box', 'altar_shelf',
                 'fusuma_*', 'oshiire_fusuma_*'],
    'bath': ['bathtub_*_0', 'bathtub_*_1', 'bathtub_*_2', 'bathtub_*_lid', 'bath_tap*', 'shower', 'bath_stool', 'bucket',
             'washing_machine*', 'washbasin*', 'mirror_small', 'toilet_seat'],
    'kitchen': ['kitchen_sink*', 'gas_stove*', 'fridge_old*', 'rice_bag', 'rice_cooker', 'salt_shaker', 'cup_glass',
                'knife_rack*', 'microwave', 'dining_table', 'dining_chair', 'drawer*'],
    'western': ['desk', 'desk_chair', 'laptop_block*', 'sewing_box*', 'printout', 'bed_*', 'shelf', 'plush_doll*',
                'camera_bag', 'poster', 'desk_lamp*', 'shoe_cabinet', 'shoes', 'umbrella', 'breaker_panel*',
                'intercom*', 'calendar', 'wall_clock'],
    'exterior': ['laundry_pole', 'ac_outdoor_unit', 'vending_machine_small', 'street_lamp_old*', 'utility_pole*',
                 'mailboxes', 'bicycle', 'stairs_rail', 'nameplate_*', 'vacancy_sign', 'swing_set', 'puddle',
                 'rain_window'],
}


def main(args):
    import hk_modules  # noqa: F401
    import hk_kit
    names = sorted(hk_kit.MODELS)
    out = []
    for g, pats in GROUPS.items():
        if args and not any(fnmatch.fnmatch(g, a) for a in args):
            continue
        sel = [n for n in names if any(fnmatch.fnmatch(n, p) for p in pats)]
        out.append(hk_render.props_sheet(sel, os.path.join(PREVIEW, f'props_{g}.png'), views=((-35, 26), (145, 22)),
                                         cols=8, cell=200, title=f'HITORI props: {g} (front 3/4, back 3/4)'))
    return out


if __name__ == '__main__':
    for p in main(sys.argv[1:]):
        print(p)
