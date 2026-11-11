"""The estates' own blocks in the capital kit's block table (imported by kit.py; the capital's output is untouched):
the labelled storage chest (a block entity with 27 slots and a ``label`` state) and the sorting desk."""
from __future__ import annotations

from capital import blocks as B
from capital import colors as C

LABELS = ("none", "gear", "potion", "material", "food", "book", "treasure", "misc")

if "storage_chest" not in B.SCHEMAS:
    B.SCHEMAS["storage_chest"] = {"facing": B.H4, "label": LABELS}
    B.DEFAULTS["storage_chest"] = {"label": "none"}
B._reg(["storage_chest"], "storage_chest", B.CUSTOM_NS)
B._reg(["sorting_desk"], "facing4", B.CUSTOM_NS)
if "storage_chest" not in B.BE_FAMILIES:
    B.BE_FAMILIES = tuple(B.BE_FAMILIES) + ("storage_chest",)
B.canon.cache_clear()

C.BASE.setdefault("storage_chest", (112, 72, 40))
C.BASE.setdefault("sorting_desk", (96, 62, 38))
C.BASE.setdefault("room_door", (122, 70, 42))
