"""HuntCraft model toolkit.

Build Minecraft (vanilla box-UV) entity models in Python, paint their textures procedurally,
author keyframe animations and render preview images -- then export an "hcmodel" JSON that the
mod loads at runtime (client/model/HcModelLoader.java).

Coordinate conventions (identical to Minecraft entity models):
  * units are model pixels (1/16 block)
  * +Y is DOWN, ground level is y = 24, the model faces -Z (north)
  * a bone's pivot is relative to its parent's pivot; cube origins are relative to the bone pivot
  * rotations are degrees, applied Z*Y*X like ModelPart (rotationZYX)
  * entity's RIGHT side is -X, LEFT side is +X
Animations: rotation offsets in degrees, position offsets in model pixels (+Y down), scale multipliers.
"""
from .model import Model, Bone, Cube, Animation
from .paint import Material, MATERIALS, register_material
from . import anim
