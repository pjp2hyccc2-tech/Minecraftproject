#!/usr/bin/env python3
"""Writes the HITORI prop block models (models/block/<name>.json, facing north) and the item models
(models/item/<id>.json; 3D held models use forge:separate_transforms). UVs come from the hk_kit atlases, so models and
textures always match: run gen_textures.py as well after any change.

usage: python3 tools/art/gen_models.py
"""
import os
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import hk_modules  # noqa: E402,F401
import hk_kit  # noqa: E402


def main():
    out = hk_kit.write_models()
    nb = len(hk_kit.MODELS)
    ni = len(hk_kit.ITEM_MODELS)
    import hk_particles
    print(f'wrote {nb} block models, {ni} item models, {len(hk_particles.PARTICLES)} particle definitions')
    return out


if __name__ == '__main__':
    main()
