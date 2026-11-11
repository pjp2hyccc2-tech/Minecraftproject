"""Registry of the twenty guild-house designs and their preview cameras (docs/modules/guildhouse.md).

``DESIGNS[("guild", id)] = builder(world) -> storage`` for tools/estates/build.py; ``VIEWS`` adds the cameras to
tools/estates/views.py. ``check(results)`` checks the set as a whole: one design per catalogue id, the same ids as
the capital's portal block, storage names that grow with the tiers.
"""
from __future__ import annotations

import json
import os

from capital import blocks as B

from . import t1, t2, t3, t4, t5

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CATALOGUE = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "guildhouse", "designs.json")

MODULES = (t1, t2, t3, t4, t5)
BUILDERS = {}
for _module in MODULES:
    BUILDERS.update(_module.DESIGNS)
DESIGNS = {("guild", theme): fn for theme, fn in BUILDERS.items()}


def catalogue():
    with open(CATALOGUE, encoding="utf-8") as f:
        return json.load(f)["designs"]


# preview cameras: from the arrival (behind the entry spot), from the air, the thumbnail angle, and the interior
# cameras the design modules list (INTERIORS: name -> (pos, look))
def _views():
    from .hkit import HALF
    interiors = {}
    for _module in MODULES:
        interiors.update(getattr(_module, "INTERIORS", {}))
    v = {}
    for d in catalogue():
        th = d["id"]
        half = HALF[d["tier"]]
        tall = d["size"][2]
        gx = -half + 6
        cams = {
            f"{th}_arrive": dict(pos=(gx + 2.5, 2.62, 0.5), look=(half * 0.7, tall * 0.3, 0.5), fov=84,
                                 label=f"{th}: from the return gate"),
            f"{th}_aerial": dict(pos=(-half * 1.25, tall + 16, half * 1.15), look=(2.0, tall * 0.2, 0.0), fov=62,
                                 label=f"{th}: from the air"),
            f"{th}_thumb": dict(pos=(-half * 1.3, tall * 0.5 + 10, -half * 1.0), look=(2.0, tall * 0.28, 0.0),
                                fov=60),
        }
        for name, (pos, look) in interiors.get(th, {}).items():
            cams[f"{th}_{name}"] = dict(pos=pos, look=look, fov=80, exposure=1.3, label=f"{th}: {name}")
        v[("guild", th)] = cams
    return v


VIEWS = _views()


def check(results) -> list:
    """Problems of the set: every catalogue design built, the capital's portal ids in step."""
    problems = []
    ids = [d["id"] for d in catalogue()]
    if tuple(ids) != tuple(B.GUILD_THEMES):
        problems.append(f"tools/capital/blocks.py GUILD_THEMES {B.GUILD_THEMES} != catalogue {ids}")
    built = {design for (kind, design) in results if kind == "guild"}
    if built and built != set(ids) and len(built) == len(BUILDERS) and set(BUILDERS) != set(ids):
        problems.append(f"designs without a house: {sorted(set(ids) - set(BUILDERS))}")
    return problems
