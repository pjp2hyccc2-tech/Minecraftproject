#!/usr/bin/env python3
"""Renders every REISATSU prop block model to contact sheets (two views each) -> tools/art/preview/props_<group>.png.

Also the texture lookup used by other preview tools (gen_entities.py): tex_lookup('reisatsu:block/dining_floor').
usage: python3 tools/art/model_preview.py [glob ...]
"""
import fnmatch
import json
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from common import PREVIEW, MODEL_ROOT  # noqa: E402
import rs_render  # noqa: E402

tex_lookup = rs_render.tex


def load_model(kind, name):
    with open(os.path.join(MODEL_ROOT, kind, name + '.json'), encoding='utf-8') as f:
        return json.load(f)


GROUPS = {
    'dining': ['booth_*', 'restaurant_*', 'call_button*', 'menu_stand', 'menu_scattered_*', 'water_glass_*',
               'broken_plate', 'pendant_lamp*', 'exit_sign*', 'window_blind*', 'planter'],
    'waiting': ['counter', 'register*', 'service_bell', 'cake_case', 'business_phone*', 'reservation_book',
                'capsule_machine', 'waiting_bench', 'magazine_rack', 'umbrella_stand', 'notice_board', 'call_display_*',
                'drink_bar*', 'coffee_machine', 'ice_machine', 'cup_shelf', 'soup_warmer', 'server_station', 'kids_*',
                'toy_shelf', 'stuffed_bear', 'high_chair'],
    'kitchen': ['gas_range*', 'fryer*', 'griddle', 'kitchen_*', 'exhaust_hood', 'pot_shelf', 'dish_rack', 'prep_table',
                'cutting_board', 'dishwasher*', 'pass_*', 'walkin_shelf', 'frost*'],
    'back': ['locker_*', 'time_recorder', 'staff_table', 'folding_chair', 'bulletin_board', 'cardboard_boxes',
             'cleaning_cart', 'filing_cabinet', 'safe', 'crt_tv*', 'office_*', 'pc', 'security_*', 'breaker_panel*'],
    'restroom': ['restroom_*', 'mirror*', 'toilet', 'urinal', 'stall_wall', 'hand_dryer*', 'paper_towel'],
    'exterior': ['vending_machine_*', 'pole_sign_pole*', 'pole_sign_tile_5*', 'roof_sign_tile_3*', 'menu_board_*',
                 'bike_rack', 'trash_station', 'ac_unit', 'gas_cylinder', 'dumpster', 'wheel_stop', 'outdoor_planter',
                 'street_lamp_*', 'utility_pole_*', 'guardrail', 'chevron_sign', 'curve_mirror', 'traffic_light*',
                 'flower_offering', 'note_paper', 'door_switch*', 'auto_door_rail', 'swing_door_frame'],
}


def main(args):
    import rs_modules  # noqa: F401
    import rs_kit
    names = sorted(rs_kit.MODELS)
    for n in ('register', 'register_open', 'service_bell', 'business_phone', 'business_phone_ringing',
              'reservation_book', 'drink_bar', 'drink_bar_pouring', 'coffee_machine', 'ice_machine', 'cup_shelf',
              'soup_warmer'):
        rs_render.SUPPORT[n] = 'counter'
    rs_render.SUPPORT['cutting_board'] = 'prep_table'
    rs_render.SUPPORT['pc'] = 'office_desk'
    out = []
    for g, pats in GROUPS.items():
        if args and not any(fnmatch.fnmatch(g, a) for a in args):
            continue
        sel = [n for n in names if any(fnmatch.fnmatch(n, p) for p in pats)]
        out.append(rs_render.props_sheet(sel, os.path.join(PREVIEW, f'props_{g}.png'), views=((-35, 26), (145, 22)),
                                         cols=8, cell=200, title=f'REISATSU props: {g} (front 3/4, back 3/4)'))
    return out


if __name__ == '__main__':
    for p in main(sys.argv[1:]):
        print(p)
