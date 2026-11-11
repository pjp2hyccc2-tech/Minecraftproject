"""Helpers for estate designs: storage pieces that keep their contents through a redesign, the way in and out.

A design is a capital-format blueprint (``capital.world.World`` + ``capital.export.export``) built around the
origin. Required spots: ``entry`` (feet position + yaw where players arrive) and ``exit`` (the door that takes them
out; the game handles a ``room_door`` there). Every storage piece carries its name in its persistent data
(``ForgeData:{er_slot:"chest.3"}``): when an estate is redesigned the contents of each piece move into the piece of
the same name in the new design, slot by slot, so a bigger room must keep every name of the smaller one.

All helpers take a target ``t`` (``World`` or ``Frame``) and local positions/directions like the capital kit.
"""
from __future__ import annotations

from capital import blocks as B
from capital.deco import FACING_BYTE, _wdir

SLOT = "er_slot"


def slot_nbt(name: str) -> str:
    return f'ForgeData:{{{SLOT}:{B.snbt_str(name)}}}'


class Storage:
    """Counts the storage pieces of a design and hands out their names (chest.1, chest.2, barrel.1 ...)."""

    def __init__(self):
        self.count = {}
        self.names = []

    def name(self, kind: str) -> str:
        n = self.count.get(kind, 0) + 1
        self.count[kind] = n
        name = f"{kind}.{n}"
        self.names.append(name)
        return name

    def summary(self) -> dict:
        return dict(self.count)


def chest(t, s: Storage, x, y, z, facing="north", kind="chest"):
    """A single chest (27 slots) at (x, y, z); ``facing`` = the side its lid opens towards."""
    name = s.name(kind)
    t.set(x, y, z, B.chest(facing))
    t.be(x, y, z, "{" + slot_nbt(name) + "}")
    return name


def double_chest(t, s: Storage, x, y, z, facing="north", along="east"):
    """A double chest: (x, y, z) and the block towards ``along`` (two pieces of 27 slots, one name each)."""
    from capital.world import DIR_VEC
    dx, _, dz = DIR_VEC[along]
    # a type=left half joins the block clockwise of its facing (ChestBlock.getConnectedDirection)
    clockwise = {"north": "east", "east": "south", "south": "west", "west": "north"}[facing]
    if along not in (clockwise, {"north": "west", "west": "south", "south": "east", "east": "north"}[facing]):
        raise ValueError(f"a double chest facing {facing} cannot extend {along}")
    first_type = "left" if along == clockwise else "right"
    second_type = "left" if first_type == "right" else "right"
    names = []
    for (px, pz, kind) in ((x, z, first_type), (x + dx, z + dz, second_type)):
        name = s.name("chest")
        t.set(px, y, pz, B.chest(facing, kind))
        t.be(px, y, pz, "{" + slot_nbt(name) + "}")
        names.append(name)
    return names


def barrel(t, s: Storage, x, y, z, facing="up"):
    name = s.name("barrel")
    t.set(x, y, z, B.barrel(facing))
    t.be(x, y, z, "{" + slot_nbt(name) + "}")
    return name


def shelf(t, s: Storage, x, y, z, facing="south"):
    """A chiseled bookshelf (six book slots) facing ``facing``."""
    name = s.name("shelf")
    t.set(x, y, z, B.S("chiseled_bookshelf", facing=facing))
    t.be(x, y, z, "{" + slot_nbt(name) + "}")
    return name


def stand(t, s: Storage, x, y, z, yaw=0.0, small=False):
    """An empty armour stand on the floor block at y (feet at y+1), facing ``yaw``: players dress it and take
    the pieces back; it never breaks."""
    name = s.name("stand")
    nbt = (f"{{ShowArms:1b,NoBasePlate:0b,Invulnerable:1b,DisabledSlots:0"
           f"{',Small:1b' if small else ''},{slot_nbt(name)}}}")
    t.entity("minecraft:armor_stand", (x + 0.5, y + 1.0, z + 0.5), yaw, nbt)
    return name


def frame(t, s: Storage, x, y, z, facing, glow=False):
    """An empty item frame occupying block (x, y, z), hanging on the wall behind it, facing ``facing``: a hit
    hands its item back, it never breaks."""
    name = s.name("frame")
    wf = _wdir(t, facing)
    nbt = (f"{{Facing:{FACING_BYTE[wf]}b,Fixed:0b,Invulnerable:1b,Invisible:0b,{slot_nbt(name)}}}")
    t.entity("minecraft:glow_item_frame" if glow else "minecraft:item_frame", (x + 0.5, y + 0.5, z + 0.5), 0.0,
             nbt)
    return name


def room_door(t, x, y, z, facing, hinge="left"):
    """The ornate inn room door (two blocks, y and y+1); ``facing`` like a vanilla door (the direction a player
    walking in faces). It never opens: using it is the game's business."""
    t.set(x, y, z, B.S("room_door", facing=facing, half="lower", hinge=hinge))
    t.set(x, y + 1, z, B.S("room_door", facing=facing, half="upper", hinge=hinge))


def exit_door(t, x, y, z, facing, hinge="left"):
    """The way out: a room door at (x, y, z) (lower half) and the ``exit`` spot in front of it, inside."""
    room_door(t, x, y, z, facing, hinge)
    from capital.world import DIR_VEC
    dx, _, dz = DIR_VEC[facing]
    # the player stands inside, in front of the door (opposite to facing, i.e. against the walking direction)
    t.spot("exit", (x - dx, y, z - dz), _yaw_towards(facing))


def entry(t, x, y, z, yaw):
    """Where players arrive: feet at (x, y, z)."""
    t.spot("entry", (x, y, z), yaw)


def _yaw_towards(direction):
    return {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}[direction]
