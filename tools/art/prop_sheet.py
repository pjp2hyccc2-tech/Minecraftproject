#!/usr/bin/env python3
"""Prop preview sheets: python3 tools/art/prop_sheet.py out.png name [name ...] (globs allowed)."""
import fnmatch
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rs_modules  # noqa
import rs_kit
import rs_render as RR

out = sys.argv[1]
names = []
for a in sys.argv[2:]:
    if any(c in a for c in '*?['):
        names += sorted(n for n in rs_kit.MODELS if fnmatch.fnmatch(n, a))
    else:
        names.append(a)
views = ((-35, 26), (145, 22)) if os.environ.get('BACK') else ((-35, 26),)
cell = int(os.environ.get('CELL', '224'))
cols = int(os.environ.get('COLS', '6'))
print(RR.props_sheet(names, out, views=views, cols=cols, cell=cell))
