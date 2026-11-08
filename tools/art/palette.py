"""Shared material ramps. Index 0 = outline, then dark -> light, last = highlight."""
from px import Ramp, hexc

# metals
IRON = Ramp("1d1e24", "45484f", "6b6f78", "8f949d", "b5b9c0", "dfe2e6")
IRON_DARK = Ramp("16171b", "2c2e33", "43464d", "5d6169", "7c8089", "a3a7ae")
STEEL = Ramp("141a26", "3d4a5e", "62738c", "8fa1b9", "bfcde0", "f2f7ff")
STEEL_DARK = Ramp("10141c", "262e3b", "3a4556", "56627a", "7a879e", "a9b5c8")
GOLD = Ramp("3a2206", "7f4f0f", "b37a17", "dba42a", "f4cf4f", "fff3a6")
GOLD_PALE = Ramp("40300e", "8f6a24", "c39a3c", "e6c25e", "f8e292", "fffbe0")
BRONZE = Ramp("2b1a0c", "5e3a1a", "8a5a2a", "b07d3e", "d3a564", "f0d49a")
SILVER = Ramp("1a1f2c", "59647a", "8893aa", "b3bccf", "dbe1ec", "ffffff")

# organics
WOOD = Ramp("24150c", "4a2c1a", "6d4426", "8e5d34", "b07c4a", "cf9f6c")
WOOD_PALE = Ramp("2e2416", "6b5436", "917450", "b3946a", "d1b58b", "eddcb6")
LEATHER = Ramp("1d110a", "3a2114", "56331f", "74482c", "92613d", "b07f55")
LEATHER_DARK = Ramp("120b08", "241712", "36231a", "4a3124", "604233", "7b5a47")
LINEN = Ramp("3a3226", "8f8270", "b8aa92", "d8ccb4", "eee6d4", "fffaf0")
CLOTH_RED = Ramp("240608", "5c1216", "8a1d20", "b52c2a", "d9503c", "f08a62")
STRING = Ramp("2a2620", "8a8170", "b8ae98", "dcd3bd", "f2ecdc", "ffffff")
BONE = Ramp("2a2318", "7a6a4e", "a8966f", "cdbd95", "e8dcbb", "fffbea")

# desert
SAND = Ramp("3a2812", "8d6a3a", "b48c52", "d4b073", "ead096", "fbefc8")
SANDSTONE = Ramp("3b2614", "8a5e34", "b07d47", "cf9d5e", "e6bf80", "f7e2b4")
TURQUOISE = Ramp("06302f", "0d5e5a", "16928a", "27c2b0", "6ee8d4", "d4fff6")

# frost
ICE = Ramp("0f2a45", "2f6491", "4f94c4", "7cc2e6", "b4e6f7", "f0fdff")
ICE_DEEP = Ramp("0b1d36", "1f4b7a", "336fa6", "4f94c4", "7cc2e6", "c8f2ff")
FROST_STEEL = Ramp("142233", "4f6a85", "7896b2", "a3c0d8", "cfe3f2", "ffffff")
FUR_WHITE = Ramp("2b3240", "8c97a8", "b7c2d1", "d7dfe9", "eef3f8", "ffffff")

# flame dragon
DRAGON = Ramp("2a0605", "641210", "961f17", "c2321f", "e2582b", "ff9246")
DRAGON_DARK = Ramp("1c0404", "3e0b0a", "5f1410", "821c15", "a6281b", "c8432a")
FLAME = Ramp("4a0d04", "b8340a", "e8631a", "ff9a2a", "ffd04a", "fff7b0")
EMBER = Ramp("3a0a04", "8a1e08", "c43c0c", "f07818", "ffb030", "fff070")
OBSIDIAN = Ramp("0b0709", "1e1416", "2f2023", "432d30", "5c3e40", "7d5a5a")

# celestial
HOLY_WHITE = Ramp("2a2c48", "9aa2c6", "c3cae6", "e2e7f8", "f5f7ff", "ffffff")
HOLY_GOLD = Ramp("3d2a08", "93651a", "c9952c", "ecc24a", "ffe27a", "fffbd2")
SKY = Ramp("12244a", "2d5aa0", "4a86cf", "79b2ee", "b4d8ff", "eef7ff")

# abyss
ABYSS = Ramp("07040b", "150d20", "231633", "34224a", "4a3366", "6a4c8c")
VOID = Ramp("1b0633", "4c138a", "7b2bd0", "a45cf2", "cf9bff", "f4e2ff")
VOID_GLOW = Ramp("2b0a4a", "6a1fc0", "9b45f0", "c47dff", "e6c2ff", "ffffff")
BLOOD = Ramp("1e0207", "4a0712", "770d1e", "a1162a", "c92a3a", "ee5a64")

# misc
SLIME = Ramp("0c3312", "1f6b25", "33a03a", "5ccc52", "98ee84", "e2ffd8")
LEAF = Ramp("0f2610", "1f4d1f", "2f7030", "45954a", "6dbb5e", "a8e08c")
MOSS = Ramp("10220e", "28461c", "3c6626", "56862f", "7aa845", "a8cc6e")
SPORE = Ramp("1f2a08", "4b6a12", "7a9a1c", "a9c837", "d6ef6a", "f8ffc4")
SCARAB = Ramp("061e1c", "0f4440", "17716a", "21a08c", "53cdb0", "c2ffe8")
CYAN_RUNE = Ramp("042a33", "0b6273", "13a0b8", "2fd6ea", "8af4ff", "e6ffff")
HEAL_GREEN = Ramp("0a2e10", "1b6a24", "2fa23a", "52d257", "9cf28c", "eaffdc")
POTION_RED = Ramp("2e0408", "6b0b14", "a3121e", "d2232c", "f2565a", "ffb4b0")
POTION_GREEN = Ramp("1a2a06", "3f6b0d", "68a314", "96d22a", "c8f064", "f4ffc4")
POTION_VIOLET = Ramp("16062a", "3a0f66", "5f1fa0", "8a3fd6", "b77cf2", "ecd6ff")
GLASS = Ramp("1f2a38", "6a7f95", "9fb4c8", "c9d8e6", "e8f1f8", "ffffff")
PARCHMENT = Ramp("3a2a16", "8a6a40", "b8955e", "d9bd86", "efdcae", "fff6dc")
STONE = Ramp("1c1e24", "454953", "646a77", "868d9b", "aab1bd", "d4d9e0")
SHADOW = Ramp("050308", "120c18", "1f1728", "2e2338", "40324c", "5a4866")

# job colours (frames, emblems)
JOB_KNIGHT = Ramp("0d1b3d", "1d3a7a", "2d58b0", "4a7fd6", "8fb4f0", "e0ecff")
JOB_ASSASSIN = Ramp("14040a", "3d0912", "6b0f1e", "a01b2c", "d0343f", "f27a72")
JOB_MAGE = Ramp("18082e", "3a1470", "5a24a8", "7d43d6", "a978f0", "e2ccff")
JOB_RANGER = Ramp("0c2410", "1c4a20", "2d6e2c", "45923c", "73b85a", "b8e49a")
JOB_PRIEST = Ramp("3a2a0a", "8f6a1c", "c9a032", "ecd068", "fbefb4", "ffffff")
JOB_SPELLBLADE = Ramp("042630", "0a5466", "11849c", "22b6cc", "6ee6f2", "dcffff")
JOB_WEAPON = Ramp("221a14", "4d3b2a", "7a6146", "a3876a", "cdb497", "f3e6d0")

WHITE = hexc("ffffff")
BLACK = hexc("000000")
