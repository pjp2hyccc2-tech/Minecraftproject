"""MEIKON texture generators kept only to reproduce the textures REISATSU inherited.

`load()` imports the old modules into a scratch registry and returns {reisatsu name: spec} for the
textures listed in KEEP (old name -> new name). Nothing else from MEIKON is generated.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

KEEP = {
    'block/old_planks': 'block/old_planks',
    'block/rotten_planks': 'block/rotten_planks',
    'block/dark_beam_side': 'block/dark_beam_side',
    'block/dark_beam_top': 'block/dark_beam_top',
    'block/plaster_wall': 'block/plaster_wall',
    'block/shoji_torn': 'block/shoji_torn',
    'block/tatami_top': 'block/tatami_old_top',
    'block/tatami_side': 'block/tatami_old_side',
    'block/futon_top': 'block/futon_top',
    'block/futon_side': 'block/futon_side',
    'block/stone_lantern': 'block/stone_lantern',
    'block/blood_stain': 'block/blood_stain',
    'block/hair_strands': 'block/hair_strands',
    'block/shimenawa': 'block/shimenawa',
    'gui/vignette': 'gui/vignette',
    'gui/grain': 'gui/grain',
    'gui/paper': 'gui/paper',
    'gui/hands': 'gui/hands',
}


_CACHE = {}


def load():
    if _CACHE:
        return dict(_CACHE)
    import common
    saved = dict(common.REGISTRY)
    common.REGISTRY.clear()
    if HERE not in sys.path:
        sys.path.insert(0, HERE)
    import t_build  # noqa: F401
    import t_props  # noqa: F401
    import t_gui  # noqa: F401
    old = dict(common.REGISTRY)
    common.REGISTRY.clear()
    common.REGISTRY.update(saved)
    out = {}
    for o, n in KEEP.items():
        spec = dict(old[o])
        spec['name'] = n
        out[n] = spec
    _CACHE.update(out)
    return out
