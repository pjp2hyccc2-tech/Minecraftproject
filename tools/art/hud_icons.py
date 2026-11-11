"""HUD icons (16x16): the bag, the equipment and the three mounts, drawn pixel by pixel.

python3 tools/art/hud_icons.py  ->  src/main/resources/assets/eternalrealm/textures/gui/hud/{bag,equipment}.png and
                                    mount_{horse,griffin,bike}.png
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


# the warhorse's head (black, white mane, gold chanfron with a blue gem, glowing eye, blue barding at the neck)
MOUNT_HORSE = [
    "........oo.oo...",
    ".......oKoKKo...",
    "......oKKKKKWo..",
    ".....oGGKKKKWWo.",
    "....oGBGKEKWWWo.",
    "...oGGGKKKKWWWWo",
    "..oKGGKKKKKWWwWo",
    ".oKKGKKKKKKKWWWo",
    "oKKKKKKoKKKKWwWo",
    "oKkKKKo.oKKKWWWo",
    ".oooooo..oKKKWWo",
    ".........oKKKWWo",
    ".........oBBBBBo",
    "........oBbGbBBo",
    "........oGGGGGGo",
    "........oooooooo",
]
MOUNT_HORSE_COLORS = {"o": hexc("0d0d14"), "K": hexc("2e2e3c"), "k": hexc("15151d"), "W": hexc("eef2fa"),
                      "w": hexc("a9b4cc"), "G": hexc("f4cf4f"), "B": hexc("2c4fc8"), "b": hexc("6f9cff"),
                      "E": hexc("8fe0ff")}

# the griffin's head (white feathers, gold crown with a blue gem, hooked gold beak, glowing eye, blue collar)
MOUNT_GRIFFIN = [
    ".....o.o.o......",
    "....oGoGoGo.....",
    "...oGGGBGGGo....",
    "..oWWWWWWWWWo...",
    ".oWWWWWWWWWWWo..",
    "oYYoWWEWWWWWWWo.",
    "oYYYoWWWWWWWWWWo",
    "oYYYYoWWWWWWwWWo",
    ".oyYYoWWWWWWWwWo",
    "..oyoWWWWWWwWWWo",
    "...oWWWWWWwWWWwo",
    "...oWWWWWwWWWWWo",
    "....oWWWBBBBWWWo",
    "....oBBBGGBBBBBo",
    "....oGGGGGGGGGGo",
    "....oooooooooooo",
]
MOUNT_GRIFFIN_COLORS = {"o": hexc("101420"), "W": hexc("eef1f8"), "w": hexc("b8c2dc"), "G": hexc("f4cf4f"),
                        "Y": hexc("f0c050"), "y": hexc("a8761c"), "B": hexc("2c4fc8"), "E": hexc("8fe0ff")}

# the arcbike from the side: glowing blue wheels with gold hubs, gold frame and fork, stone tank, brown seat, a lamp
MOUNT_BIKE = [
    "................",
    "...........oo...",
    "..........oGo...",
    "..........oGo...",
    "...oo....oGoAo..",
    "..oLLoooooGoAAo.",
    "..oLLLSSSSSGoo..",
    "..ooGGSSSSSGGo..",
    ".oooooGGGGGoGooo",
    "oDBBBDoooooGDBBo",
    "DBbbbBD...oDBbbB",
    "DBbGbBD...DBbGbB",
    "DBbbbBD...DBbbbB",
    "oDBBBDo...oDBBBo",
    ".oDDDo.....oDDDo",
    "................",
]
MOUNT_BIKE_COLORS = {"o": hexc("101018"), "D": hexc("2a2832"), "B": hexc("2c4fc8"), "b": hexc("6fb4ff"),
                     "G": hexc("f4cf4f"), "S": hexc("d9d4ca"), "L": hexc("74482c"), "A": hexc("ffc45c")}


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
    draw(MOUNT_HORSE, MOUNT_HORSE_COLORS).save(os.path.join(OUT, "mount_horse.png"))
    draw(MOUNT_GRIFFIN, MOUNT_GRIFFIN_COLORS).save(os.path.join(OUT, "mount_griffin.png"))
    draw(MOUNT_BIKE, MOUNT_BIKE_COLORS).save(os.path.join(OUT, "mount_bike.png"))


if __name__ == "__main__":
    main()
