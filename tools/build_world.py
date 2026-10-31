#!/usr/bin/env python3
"""Builds the hand-made Japan dimension and writes src/main/resources/data/tabi/tabi_world/{japan.bin, meta.json}.
Run from anywhere:  python3 tools/build_world.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import numpy as np  # noqa: E402

from wb import World  # noqa: E402
import b_village  # noqa: E402
import b_town  # noqa: E402
import b_rail  # noqa: E402
import b_kobe  # noqa: E402
from bcommon import fillet_path, road  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'src/main/resources/data/tabi/tabi_world')


def honami_bus(w):
    from b_rail import route_meta
    pts = fillet_path([(40, 24), (633, 24)], 8)
    p3 = [(x + 0.5, 67.0, z + 0.5) for (x, z) in pts[::2]]
    if p3[-1][0] < pts[-1][0]:
        p3.append((pts[-1][0] + 0.5, 67.0, pts[-1][1] + 0.5))
    stations = [{'id': 'honami', 'name': 'ほなみ', 'kana': 'ほなみ', 'en': 'Honami', 's': 20.0, 'exit': [60.5, 67, 20.5]}]
    meta, L = route_meta('honami_bus', 'ほなみ線', 'bus', 'bus_honami', 1, 11.0, 0.6, 200, 0, '#2F6FB3', '大川駅前ゆき', 'ほなみゆき', p3, stations)
    meta['stations'].append({'id': 'okawa_ekimae', 'name': '大川駅前', 'kana': 'おおかわえきまえ', 'en': 'Okawa Sta.', 's': round(L - 8.0, 2),
                             'exit': [630.5, 67, 29.5]})
    w.meta['routes'].append(meta)


def main():
    t0 = time.time()
    w = World()
    # 1. terrain modifiers (order matters: later ones override)
    b_village.terrain(w)
    b_town.terrain(w)
    b_kobe.terrain(w)
    b_rail.terrain(w)
    # 2. designed regions
    b_village.regions(w)
    b_town.regions(w)
    b_kobe.regions(w)
    b_rail.regions(w)
    print(f'regions allocated: {len(w.chunks)} chunks ({time.time() - t0:.0f}s)')
    # 3. structures
    b_village.build(w)
    print(f'village done ({time.time() - t0:.0f}s)')
    b_town.build(w)
    print(f'town done ({time.time() - t0:.0f}s)')
    line = b_rail.build(w)
    b_rail.gorge_river(w)
    print(f'railway done, length {line["length"]:.0f} ({time.time() - t0:.0f}s), tunnels {line["tunnels"]}, bridges {line["bridges"]}')
    b_kobe.build(w)
    print(f'kobe done ({time.time() - t0:.0f}s)')
    honami_bus(w)
    import b_deco
    b_deco.build(w, line)
    print(f'deco done ({time.time() - t0:.0f}s)')
    # 4. a few views along the line
    sz = line['shiomi_z']
    w.viewpoint('shiomi_bridge', 2118, line['shiomi_ytop'] + 2.6, sz - 3, -40, -6, time=12650, mode='spectator')
    w.viewpoint('akashi_bridge_night', 2110, 80, sz + 26, -32, -8, time=15000, mode='spectator')
    w.viewpoint('kirigaya', 1245, line['kiri_ytop'] + 4, 90, 0, 18, time=3000, mode='spectator')
    w.viewpoint('train_ride', 622.5, 69, 46.5, -90, 5, time=6000, mode='creative', ride='kawagiri', look=90, interval=500)
    os.makedirs(OUT, exist_ok=True)
    n, size = w.export(os.path.join(OUT, 'japan.bin'), os.path.join(OUT, 'meta.json'))
    print(f'exported {n} chunks, {size / 1e6:.1f} MB raw data, palette {len(w.pal)}, '
          f'npcs {len(w.meta["npcs"])}, signs {len(w.meta["signs"])}, areas {len(w.meta["areas"])} ({time.time() - t0:.0f}s)')
    print('bin size', os.path.getsize(os.path.join(OUT, 'japan.bin')) / 1e6, 'MB')
    return w


if __name__ == '__main__':
    main()
