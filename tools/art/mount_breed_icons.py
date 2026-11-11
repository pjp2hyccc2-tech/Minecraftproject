"""HUD icons (16x16) of the ten mount breeds, drawn pixel by pixel in the style of hud_icons.py (a dark outline, the
breed's head with what marks it out): antlers in bloom, a fin crest and a pearl, a wing behind the head, a mane of fire,
a spiral horn under a halo; a lion's mane, an owl's face, a storm crest with a bolt, an ice crown, crystal horns and
stars.

python3 tools/art/mount_breed_icons.py  ->  src/main/resources/assets/eternalrealm/textures/gui/hud/mount_<breed>.png
(and tools/art/mount_breed_icons_preview.png, every icon enlarged next to the first three)
"""
import os

from PIL import Image

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "hud")


def hexc(value):
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), 255)


ICONS = {
    # a fawn's head under branching antlers in blossom, a mane of leaves, a garland at the throat
    "horse_bloom": ([
        ".P.b.....b.P....",
        "..bPb...bPb.....",
        "...bb...bb......",
        "....bb.bb.......",
        ".....oKKo.......",
        "....oKKKKLLo....",
        "...oKKEKKLlLo...",
        "..oKKKKKKLLlLo..",
        ".oKKKKKKKKLLLLo.",
        "oKWKKKKoKKKLlLLo",
        "oKkKKKo.oKKKLLLo",
        ".oooooo..oKKKLlo",
        ".........oKKKLLo",
        ".........oGPGPGo",
        "........oGPGPGPo",
        "........oooooooo",
    ], {"o": "1e140c", "K": "b07a48", "k": "7e5230", "W": "f2e6d0", "E": "1a0e06", "L": "6aa83e", "l": "3f6a2a",
        "b": "6b4a2e", "P": "f4a6c0", "G": "86b84a"}),
    # a sea-blue head with a fin ear and a fin crest, foam in the mane, a pearl on the brow, shells at the throat
    "horse_tide": ([
        ".......F..FF....",
        "......oFoFFFo...",
        "......oTTTFFWo..",
        ".....oSSTTTFWWo.",
        "....oSPSTETWWBWo",
        "...oSSSTTTTWBWWo",
        "..oTSSTTTTTWWBWo",
        ".oTTSTTTTTTTWWWo",
        "oTTTTTToTTTTWBWo",
        "oTtTTTo.oTTTWWWo",
        ".oooooo..oTTTWWo",
        ".........oTTTWBo",
        ".........oCCCCCo",
        "........oCcScCCo",
        "........oSSSSSSo",
        "........oooooooo",
    ], {"o": "0a1a22", "F": "6fe0e8", "T": "2f7f8a", "t": "1d5662", "S": "f0d8c8", "P": "ffffff", "E": "9ff4ff",
        "W": "eefcff", "B": "3aa6c0", "C": "e8706a", "c": "b84a46"}),
    # a dapple-grey head with a white wind-swept mane and a feathered wing rising behind
    "horse_gale": ([
        "........oo.oo..f",
        ".......oKoKKo.fF",
        "......oKKKKKWfFF",
        ".....oSSKKKKWFFo",
        "....oSBSKEKWWFFo",
        "...oSSSKKKKWWFo.",
        "..oKSSKKKKKWWWFo",
        ".oKKSKKKKKKKWWWo",
        "oKKKKKKoKKKKWwWo",
        "oKkKKKo.oKKKWWWo",
        ".oooooo..oKKKWWo",
        ".........oKKKWWo",
        ".........oSSSSSo",
        "........oSbBbSSo",
        "........oSSSSSSo",
        "........oooooooo",
    ], {"o": "1a1c24", "K": "a9adb6", "k": "7e838e", "S": "d8dee8", "B": "5ab0ff", "b": "8fd0ff", "E": "9fe0ff",
        "W": "f2f5fa", "w": "b9c8de", "F": "ffffff", "f": "c9d6ea"}),
    # a charcoal head veined with fire, a burning mane, a horned iron chanfron with an ember gem
    "horse_blaze": ([
        "...h....oo.Y....",
        "....h..oKoKYR...",
        ".....hoKKKKYRR..",
        ".....oIIKKKRYRo.",
        "....oIGIKEKRYRRo",
        "...oIIIKKKKRRYRo",
        "..oKIIKKKKKYRRYo",
        ".oKKIKvKKKKKRYRo",
        "oKKKKKKoKKKKYRRo",
        "oKkKvKo.oKKKRYRo",
        ".oooooo..oKKKRYo",
        ".........oKKvKRo",
        ".........oIIIIIo",
        "........oIiGiIIo",
        "........oCCCCCCo",
        "........oooooooo",
    ], {"o": "0c0808", "K": "2e2826", "k": "181514", "I": "4a4448", "i": "c08a3a", "G": "ff6a2a", "E": "ffb43c",
        "h": "c08a3a", "Y": "ffe08a", "R": "ff7a1e", "v": "ff8a2a", "C": "c08a3a"}),
    # a pearl-white head with a gold spiral horn and a floating halo, a mane of pastel light
    "horse_halo": ([
        ".......HHHHHH...",
        ".gN...H......H..",
        "..gN...HHHHHH...",
        "...gN....oo.oo..",
        "....gNooKoKKoA..",
        "....oNKKKKKKAB..",
        "...oKKKKKEKAABo.",
        "..oKKKKKKKKKACAo",
        ".oKKKKKKKKKAABAo",
        "oKKKKKKoKKKKACAo",
        "oKkKKKo.oKKKABAo",
        ".oooooo..oKKKACo",
        ".........oFFFFFo",
        "........oFfgfFFo",
        "........oggggggo",
        "........oooooooo",
    ], {"o": "4a4458", "K": "eceaf2", "k": "c4c0d0", "N": "f4f0e8", "g": "e8c050", "H": "ffd870", "E": "8fd8ff",
        "A": "fff0c8", "B": "ffd0e0", "C": "d8e8ff", "F": "f6f4fa", "f": "7ab8ff"}),
    # an eagle's head in a great shaggy lion's mane, a turquoise circlet, a dark hooked beak
    "griffin_gold": ([
        "......MMMMM.....",
        "....MMoTtToMM...",
        "...MoHHHHHHHoMM.",
        "..MoHHHHHHHHHoM.",
        ".MoHHHHHHHHHHHoM",
        "oDDoHHEHHHHHHHHo",
        "oDDDoHHHHHHHHHMo",
        "oDDDDoHHHHHHHMMo",
        ".oddDoHHHHHHMmMo",
        "..odoHHHHHHMMMMo",
        "...oHHHHHHMmMMmo",
        "...oCCHHHMMMMMMo",
        "....oCCCMMMmMMMo",
        "....oRRRRRRRRRRo",
        "....oTRTRTRTRTRo",
        "....oooooooooooo",
    ], {"o": "1c1208", "M": "7a4a1e", "m": "4a2a10", "H": "c8964a", "C": "f0e2c4", "D": "4a3a2c", "d": "1e1814",
        "E": "ffb43a", "T": "3ac8b8", "t": "1e8a80", "R": "b03a2a"}),
    # an owl's face: a pale heart-shaped disc with two great glowing eyes, ear tufts, a small beak
    "griffin_owl": ([
        "..oB........Bo..",
        "..oBBo....oBBo..",
        "...oBBooooBBo...",
        "..oBBBBBBBBBBo..",
        ".oRRRRRRRRRRRRo.",
        ".oRDDDDRRDDDDRo.",
        ".oRDYYDRRDYYDRo.",
        ".oRDYkYDDYkYDRo.",
        ".oRDDYDbbDYDDRo.",
        ".oRRDDDbbDDDRRo.",
        "..oRRDDDDDDRRo..",
        "..oBBRRDDRRBBo..",
        "..oBwBBRRBBwBo..",
        "..oBBwBBBBwBBo..",
        "...oBBBBBBBBo...",
        "....oooooooo....",
    ], {"o": "1a1208", "B": "c8a06a", "w": "f8f0e0", "R": "8a6440", "D": "fbf4ea", "Y": "ffb03a", "k": "0a0806",
        "b": "5a4a3a"}),
    # a slate hawk's head with a jagged crest tipped in light, a lightning bolt on the cheek, a storm gem
    "griffin_storm": ([
        "....L.L.L.......",
        "...oSLSLSLo.....",
        "..oGGGBGGGo.....",
        "..oSSSSSSSSo....",
        ".oSSSSSSSSSSo...",
        "oYYoSSEWWSSSSo..",
        "oYYYoSSSSSSSSSo.",
        "oYYYYoSSSSSSLSSo",
        ".oyYYoSSSSSSLSSo",
        "..oyoSSSSSLLLSSo",
        "...oSSSSSSSLSSSo",
        "...oSSSSSSLSSSSo",
        "....oSSSGGGGSSSo",
        "....oGGGGBBGGGGo",
        "....oGGGGGGGGGGo",
        "....oooooooooooo",
    ], {"o": "10141c", "S": "56606e", "G": "c8d0dc", "B": "4ad8ff", "L": "bff6ff", "E": "9ff4ff", "W": "eef2f8",
        "Y": "9aa4b4", "y": "4a5260"}),
    # a frost-white head crowned with ice crystals, a pale beak, glowing ice-blue eyes
    "griffin_frost": ([
        "...I..I..I......",
        "..oIi.Ii.Ii.....",
        "..oIIiIIiIIo....",
        "..oWWWCWWWWo....",
        ".oWWWWWWWWWWo...",
        "oPPoWWEWWWWWWo..",
        "oPPPoWWWWWWWWWo.",
        "oPPPPoWWWWWWwWWo",
        ".oqPPoWWWWWWWwWo",
        "..oqoWWWWWWwWWWo",
        "...oWWWWWWwWWWwo",
        "...oWWWWWwWWWWWo",
        "....oWWWIIIIWWWo",
        "....oIIIiiIIIIIo",
        "....oSSSSSSSSSSo",
        "....oooooooooooo",
    ], {"o": "142434", "I": "8ad8ff", "i": "e8faff", "W": "eef6fc", "w": "b4cede", "C": "8ad8ff", "E": "aef0ff",
        "P": "e0f0fa", "q": "5a7a94", "S": "d8e8f4"}),
    # a night-violet head with crystal horns and a crown gem, stars in the plumage, glowing eyes
    "griffin_void": ([
        "..V.......V.....",
        "..vV.....Vv.....",
        "...vV...Vv......",
        "...ovVVVVvo.....",
        "..oNNNNNNNNo....",
        ".oNNNNsNNNNNo...",
        "oBBoNNENNNNNso..",
        "oBBBoNNNNNsNNNo.",
        "oBBBBoNNNNNNNsNo",
        ".obBBoNNsNNNNNNo",
        "..oboNNNNNNNsNNo",
        "...oNNsNNNNNNNNo",
        "...oNNNNNNsNNNNo",
        "....oMMMMVVMMMMo",
        "....oMMMMMMMMMMo",
        "....oooooooooooo",
    ], {"o": "08060e", "V": "c88aff", "v": "7a40d0", "N": "2a1e46", "s": "f0e0ff", "E": "f0d8ff", "B": "7a6a9a",
        "b": "3a2e50", "M": "3a3050"}),
}


def draw(rows, colors):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), hexc(colors[ch]))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    icons = []
    for name, (rows, colors) in ICONS.items():
        img = draw(rows, colors)
        img.save(os.path.join(OUT, "mount_%s.png" % name))
        icons.append(img)
    firsts = [Image.open(os.path.join(OUT, "mount_%s.png" % n)).convert("RGBA") for n in ("horse", "griffin", "bike")]
    scale = 8
    sheet = Image.new("RGBA", ((len(icons) + 3) * (16 * scale + 8) + 8, 16 * scale + 16), (40, 36, 48, 255))
    for i, img in enumerate(firsts + icons):
        sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST), (8 + i * (16 * scale + 8), 8))
    sheet.save(os.path.join(HERE, "mount_breed_icons_preview.png"))


if __name__ == "__main__":
    main()
