"""HUD icons (16x16): the bag and the equipment, drawn pixel by pixel.

python3 tools/art/hud_icons.py  ->  src/main/resources/assets/eternalrealm/textures/gui/hud/{bag,equipment}.png
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "eternalrealm",
                   "textures", "gui", "hud")


def hexc(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


BAG = [
    "................",
    "......oooo......",
    ".....oLLLLo.....",
    "....oL.oo.Lo....",
    "....oL....Lo....",
    "..oooooooooooo..",
    ".oDMMMMMMMMMMDo.",
    ".oMHMMMgGgMMMMo.",
    ".oDDDDDgYgDDDDo.",
    ".oMLLLLLgLLLLMo.",
    ".oMLHLLLLLLLLMo.",
    ".oMLLLLLLLLLLMo.",
    ".oMLLLLLLLLLLDo.",
    ".oDMLLLLLLLLMDo.",
    "..oDDDDDDDDDDo..",
    "...oooooooooo...",
]
BAG_COLORS = {"o": hexc("1d110a"), "D": hexc("56331f"), "M": hexc("74482c"), "L": hexc("92613d"),
              "H": hexc("c49a6c"), "g": hexc("b37a17"), "G": hexc("f4cf4f"), "Y": hexc("fff3a6")}

EQUIPMENT = [
    "................",
    "..ooo......ooo..",
    ".oHSSo....oSSDo.",
    ".oHSSSooooSSSDo.",
    ".oSSSSGGGGSSSDo.",
    "..oSSSSSSSSSDo..",
    "..oHSSSGgSSSDo..",
    "..oHSSSGgSSSDo..",
    "..oHSSSGgSSSDo..",
    "..oSSSSGgSSSDo..",
    "..oSSSSSSSSSDo..",
    "..oGGGGYGGGGgo..",
    "..oSSSSSSSSSDo..",
    "...oSSSSSSSDo...",
    "....ooooooooo...",
    "................",
]
EQUIPMENT_COLORS = {"o": hexc("141a26"), "D": hexc("3d4a5e"), "S": hexc("8fa1b9"), "H": hexc("dfe6f0"),
                    "G": hexc("f4cf4f"), "g": hexc("b37a17"), "Y": hexc("fff3a6")}


def draw(rows, colors):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch])
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    draw(BAG, BAG_COLORS).save(os.path.join(OUT, "bag.png"))
    draw(EQUIPMENT, EQUIPMENT_COLORS).save(os.path.join(OUT, "equipment.png"))


if __name__ == "__main__":
    main()
