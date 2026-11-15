"""Textures of the finales (秘奥義), both drawn from nothing:

- textures/gui/finale_vignette.png (256x256): white, clear in the middle and opaque at the edges; the cinema overlay
  tints it with the finale's colour (``client.FinaleCinema``).
- textures/entity/bone_king.png (16x16): plain white; the skeleton knight-king is drawn with vertex colours
  (``client.entity.BoneKingRenderer``), the entity pipeline only needs a texture to sample.

Usage: python3 tools/art/finale_textures.py [repo root]
"""
from __future__ import annotations

import math
import os
import sys

from PIL import Image

ASSETS = "src/main/resources/assets/eternalrealm/textures"


def vignette(n: int = 256) -> Image.Image:
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            dx = (x + 0.5) / n * 2.0 - 1.0
            dy = (y + 0.5) / n * 2.0 - 1.0
            # a little wider than tall: the screen is
            d = math.sqrt(dx * dx * 0.9 + dy * dy * 1.1)
            a = max(0.0, min(1.0, (d - 0.45) / 0.75))
            a = a * a * (3.0 - 2.0 * a)
            px[x, y] = (255, 255, 255, int(round(255 * a)))
    return img


def white(n: int = 16) -> Image.Image:
    return Image.new("RGBA", (n, n), (255, 255, 255, 255))


def main(root: str) -> None:
    out = os.path.join(root, ASSETS)
    os.makedirs(os.path.join(out, "gui"), exist_ok=True)
    os.makedirs(os.path.join(out, "entity"), exist_ok=True)
    vignette().save(os.path.join(out, "gui", "finale_vignette.png"))
    white().save(os.path.join(out, "entity", "bone_king.png"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
