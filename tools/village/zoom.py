"""Render a sub-region of the generated village at high zoom: python3 zoom.py x0 z0 x1 z1 [rot] [scale] [out]"""
import os
import sys
sys.argv, args = sys.argv[:1], sys.argv[1:]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_village as gv  # noqa: E402
import vrender  # noqa: E402
from vworld import World  # noqa: E402

gv.build()
x0, z0, x1, z1 = map(int, args[:4])
rot = int(args[4]) if len(args) > 4 else 0
scale = int(args[5]) if len(args) > 5 else 10
out = args[6] if len(args) > 6 else os.path.join(gv.ROOT, 'tools', 'preview', 'village_zoom.png')
sub = World(x1 - x0 + 1, gv.SY, z1 - z0 + 1)
for (x, y, z), b in gv.W.blocks.items():
    if x0 <= x <= x1 and z0 <= z <= z1:
        sub.blocks[(x - x0, y, z - z0)] = b
vrender.iso(sub, out, rot=rot, scale=scale)
print(out)
