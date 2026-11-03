"""Shared builders for the spirit specs (humanoid skeleton pieces, hands, decal cards)."""
from ent_model import Part, Cube


def card(name, pivot, origin, size, mat, tag=None, faces=('front',), rot=(0, 0, 0)):
    """A flat single-sided card (depth 0): mouths, cracks, glasses, hair curtains. Only `faces` are drawn, so it never
    doubles up over itself when the model is translucent."""
    return Part(name, pivot, rot, cubes=[Cube(origin, size, mat, tag=tag or name, faces=faces)])
