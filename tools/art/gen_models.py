#!/usr/bin/env python3
"""Writes the HITORI prop block models (models/block/<name>.json, facing north) and the item models
(models/item/<id>.json; 3D held models use forge:separate_transforms). UVs come from the rs_kit atlases, so models and
textures always match: run gen_textures.py as well after any change.

usage: python3 tools/art/gen_models.py
"""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import rs_modules  # noqa: E402,F401
import rs_kit  # noqa: E402


def main():
    out = rs_kit.write_models()
    nb = len(rs_kit.MODELS)
    ni = len(rs_kit.ITEM_MODELS)
    import rs_particles
    print(f'wrote {nb} block models, {ni} item models, {len(rs_particles.PARTICLES)} particle definitions')
    return out


if __name__ == '__main__':
    main()
