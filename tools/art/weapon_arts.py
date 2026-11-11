"""The weapon arts of every named field weapon and every tier weapon: the single definition.

Writes
  src/main/java/com/eternalrealm/item/ArtTable.java   the rows the game builds its arts from (ArtForge)
  textures/gui/skill/<art>.png                        the skill icon of every art (weapon_art_icons.py)
  docs/i18n/weapon_arts.json                          names and descriptions (Japanese and English)
  docs/modules/armory.md                              the art table (between the ART TABLE markers)

Each row chooses the attack form (by default from the weapon type and feel), the extras (areas, buffs, heals,
dashes, pulls, executes ...), the look (palette: particles and colours; the primary shape every hit draws) and the
names. Descriptions are composed from the same choices, so they always say what the art does.

    python3 tools/art/weapon_arts.py
"""
from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import armory_data  # noqa: E402

JAVA = os.path.join(ROOT, "src/main/java/com/eternalrealm/item/ArtTable.java")
I18N = os.path.join(ROOT, "docs/i18n/weapon_arts.json")
DOC = os.path.join(ROOT, "docs/modules/armory.md")

TYPES = {"longsword": "SWORD", "greatsword": "GREAT", "twin_blades": "TWIN", "spear": "SPEAR", "staff": "STAFF",
         "bow": "BOW"}
JAVA_TYPES = {"longsword": "ONE_HANDED_SWORD", "greatsword": "TWO_HANDED_SWORD", "twin_blades": "DUAL_BLADES",
              "spear": "SPEAR", "staff": "STAFF", "bow": "BOW"}
FEEL_FORM = {
    "SWORD": {"BALANCED": "COMBO", "SWIFT": "DASH", "HEAVY": "CLEAVE", "KEEN": "PRECISE", "LONG": "WAVE",
              "ELEMENTAL": "BURST"},
    "GREAT": {"BALANCED": "REND", "SWIFT": "SPIN", "HEAVY": "SLAM", "KEEN": "GUILLOTINE", "LONG": "SWEEP",
              "ELEMENTAL": "ERUPTION"},
    "TWIN": {"SWIFT": "FLURRY", "KEEN": "ASSASSINATE", "BALANCED": "CROSS", "ELEMENTAL": "FAN", "HEAVY": "MAUL",
             "LONG": "CHAIN"},
    "SPEAR": {"LONG": "LANCE", "BALANCED": "THRUSTS", "SWIFT": "CHARGE", "HEAVY": "IMPALE", "KEEN": "PIERCE",
              "ELEMENTAL": "JAVELIN"},
    "STAFF": {"ELEMENTAL": "BURST", "BALANCED": "ORBS", "KEEN": "LANCE", "SWIFT": "VOLLEY", "HEAVY": "METEOR",
              "LONG": "BEAM"},
    "BOW": {"SWIFT": "VOLLEY", "KEEN": "SNIPE", "BALANCED": "FAN", "LONG": "PIERCE", "ELEMENTAL": "RAIN",
            "HEAVY": "IMPACT"},
}
FORM_SHAPE = {
    "SWORD_COMBO": "CRESCENT", "SWORD_DASH": "ARC", "SWORD_CLEAVE": "CRESCENT", "SWORD_PRECISE": "LINE",
    "SWORD_WAVE": "WAVE", "SWORD_BURST": "BURST", "GREAT_REND": "CRESCENT", "GREAT_SPIN": "RING",
    "GREAT_SLAM": "SPIKES", "GREAT_GUILLOTINE": "PILLAR", "GREAT_SWEEP": "FAN", "GREAT_ERUPTION": "GEYSER",
    "TWIN_FLURRY": "SCATTER", "TWIN_ASSASSINATE": "LINE", "TWIN_CROSS": "CROSS", "TWIN_FAN": "FAN",
    "TWIN_MAUL": "BURST", "TWIN_CHAIN": "CHAIN", "SPEAR_LANCE": "LINE", "SPEAR_THRUSTS": "HELIX",
    "SPEAR_CHARGE": "COMET", "SPEAR_IMPALE": "SPIKES", "SPEAR_PIERCE": "LINE", "SPEAR_JAVELIN": "COMET",
    "STAFF_BURST": "BURST", "STAFF_ORBS": "ORBS", "STAFF_LANCE": "LINE", "STAFF_VOLLEY": "FAN",
    "STAFF_METEOR": "RAIN", "STAFF_BEAM": "LINE", "BOW_VOLLEY": "COMET", "BOW_SNIPE": "LINE", "BOW_FAN": "FAN",
    "BOW_PIERCE": "LINE", "BOW_RAIN": "RAIN", "BOW_IMPACT": "BURST",
}

# palette: (particle, accent particle, colour, accent colour, accent shape, wind-up)
PAL = {
    "lark": ("item:minecraft:feather", "dust:f6e7b0", 0xE6D49A, 0xFFF6D8, "SCATTER", "GATHER"),
    "oath": ("dust:f2d27a", "v:end_rod", 0xF2D27A, 0xFFFFFF, "SIGIL", "GLOW"),
    "gale": ("v:electric_spark", "v:cloud", 0xE8E37A, 0xF4FBFF, "SPIRAL", "SPIN_UP"),
    "hay": ("item:minecraft:wheat", "v:poof", 0xD9B24C, 0xF0E2B0, "BURST", "RISING"),
    "hill": ("block:minecraft:grass_block", "v:poof", 0x6FA04A, 0xC8A060, "WAVE", "GROUND_SIGIL"),
    "garm": ("dust:9a1c22", "item:minecraft:bone", 0x9A1C22, 0xEDE3CC, "SPIKES", "RISING"),
    "undercity": ("v:squid_ink", "v:dripping_water", 0x24304A, 0x6A9AC8, "VORTEX", "GATHER"),
    "thistle": ("dust:8a4aa8", "item:minecraft:sweet_berries", 0x8A4AA8, 0x4C8A2A, "SPIKES", "RISING"),
    "hook": ("item:minecraft:chain", "dust:b02020", 0x9C9CA6, 0xB02020, "CHAIN", "GATHER"),
    "herd": ("block:minecraft:white_wool", "v:crit", 0xF0EEE6, 0xC8B89A, "WAVE", "SPIN_UP"),
    "canal": ("v:splash", "v:bubble", 0x4A9AD0, 0xCFF0FF, "WAVE", "GATHER"),
    "tusk": ("item:minecraft:bone", "block:minecraft:coarse_dirt", 0xF2E8D2, 0x6A4A30, "SPIKES", "RISING"),
    "sprout": ("v:happy_villager", "dust:7ad04a", 0x7AD04A, 0xDFF7B0, "PETALS", "RISING"),
    "wisp": ("v:small_flame", "v:end_rod", 0xFFC86A, 0xFFF4D0, "ORBS", "AIR_SIGIL"),
    "dandelion": ("v:white_ash", "dust:f4e05a", 0xF7F4E8, 0xF4E05A, "SCATTER", "RISING"),
    "watch": ("v:crit", "dust:c8b48a", 0xC8B48A, 0xFFFFFF, "FAN", "GLOW"),
    "viper": ("v:item_slime", "dust:4a9a2a", 0x4A9A2A, 0xB8F070, "LINE", "GATHER"),
    "skylark": ("v:note", "v:electric_spark", 0xF2EA7A, 0xFFFFFF, "SPIRAL", "SPIN_UP"),
    "moonleaf": ("dust:c8d4e8", "v:spore_blossom_air", 0xC8D4E8, 0x9AD08A, "PETALS", "GATHER"),
    "heartwood": ("block:minecraft:oak_log", "dust:d8a040", 0x7A5230, 0xD8A040, "RING", "GROUND_SIGIL"),
    "eclipse": ("dust:1a1022", "v:end_rod", 0x1A1022, 0xE8E0FF, "RING", "GLOW"),
    "bramble": ("dust:3a6a24", "item:minecraft:sweet_berries", 0x3A6A24, 0x8A2A2A, "SPIKES", "RISING"),
    "root": ("block:minecraft:rooted_dirt", "v:poof", 0x7A5A3A, 0xB89A70, "WAVE", "GROUND_SIGIL"),
    "nightbough": ("v:large_smoke", "dust:5a3a8a", 0x3A2A5A, 0x8A6ACA, "FAN", "GATHER"),
    "silk": ("item:minecraft:string", "dust:ffffff", 0xEEEEF4, 0xFFFFFF, "CHAIN", "SPIN_UP"),
    "moth": ("v:white_ash", "dust:d8c8a0", 0xD8C8A0, 0xF4EEDC, "DOME", "GATHER"),
    "lunar": ("dust:bfe8ff", "v:end_rod", 0xBFE8FF, 0xFFFFFF, "CRESCENT", "GLOW"),
    "vine": ("item:minecraft:vine", "dust:3a8a2a", 0x3A8A2A, 0x9AD070, "CHAIN", "GATHER"),
    "owl": ("item:minecraft:feather", "dust:7a5a3a", 0x8A6A4A, 0xF0E8D8, "FAN", "GLOW"),
    "grove": ("block:minecraft:oak_leaves", "block:minecraft:oak_log", 0x4A8A3A, 0x7A5230, "WAVE", "SPIN_UP"),
    "mossglow": ("v:glow", "dust:6aa04a", 0x6AA04A, 0xBFF6E0, "ORBS", "AIR_SIGIL"),
    "crescentfrost": ("v:snowflake", "v:end_rod", 0x9ED8FF, 0xFFFFFF, "CRESCENT", "AIR_SIGIL"),
    "nebula": ("v:firework", "dust:9a6ae0", 0x9A6AE0, 0xF0E0FF, "STAR", "AIR_SIGIL"),
    "willow": ("item:minecraft:vine", "v:cloud", 0x7AAA5A, 0xEAF6E0, "ARC", "SPIN_UP"),
    "silverbark": ("dust:d8dce6", "v:end_rod", 0xD8DCE6, 0xFFFFFF, "FAN", "GLOW"),
    "nightshade": ("dust:6a2a8a", "v:item_slime", 0x6A2A8A, 0x7ACA3A, "RAIN", "AIR_SIGIL"),
    "ore": ("block:minecraft:iron_ore", "v:crit", 0xC0A080, 0xF0F0F0, "BURST", "GLOW"),
    "sunscarab": ("v:flame", "dust:f0c040", 0xF0C040, 0xFFE8A0, "SIGIL", "GLOW"),
    "centipede": ("dust:8a3a20", "v:item_slime", 0x8A3A20, 0xD8A040, "CHAIN", "RISING"),
    "oreshatter": ("block:minecraft:stone", "block:minecraft:iron_ore", 0x8A8A8A, 0xC0A080, "BURST", "RISING"),
    "coal": ("block:minecraft:coal_block", "v:flame", 0x2A2420, 0xFF8A2A, "GEYSER", "GLOW"),
    "ironspike": ("block:minecraft:iron_block", "v:crit", 0xC8C8D0, 0xFFFFFF, "SPIKES", "GROUND_SIGIL"),
    "ember": ("v:small_flame", "dust:ff8a2a", 0xFF8A2A, 0xFFD080, "SCATTER", "GATHER"),
    "tombsand": ("block:minecraft:sand", "v:item_slime", 0xD8C08A, 0x8AC04A, "VORTEX", "GROUND_SIGIL"),
    "gear": ("item:minecraft:iron_nugget", "v:wax_off", 0xB8B8C0, 0xFFE8A0, "RING", "SPIN_UP"),
    "rail": ("item:minecraft:rail", "v:crit", 0x9A8A70, 0xFFFFFF, "LINE", "GLOW"),
    "bluevein": ("dust:5a8ad0", "v:crit", 0x5A8AD0, 0xD8E8FF, "HELIX", "GLOW"),
    "grave": ("v:soul", "dust:8a8a90", 0x6A6A74, 0xA8E8F0, "FAN", "RISING"),
    "geode": ("item:minecraft:amethyst_shard", "v:end_rod", 0xA87AE0, 0xF0E0FF, "SCATTER", "AIR_SIGIL"),
    "sunpillar": ("v:flame", "dust:ffd860", 0xFFD860, 0xFFF4C0, "PILLAR", "AIR_SIGIL"),
    "golemcore": ("v:electric_spark", "item:minecraft:redstone", 0xF0E060, 0xE03030, "LINE", "AIR_SIGIL"),
    "wren": ("item:minecraft:feather", "v:poof", 0x9A7A5A, 0xE8D8C0, "ARC", "GATHER"),
    "firedamp": ("v:large_smoke", "v:flame", 0x5A5048, 0xFF9A3A, "BURST", "GATHER"),
    "rattlebone": ("item:minecraft:bone", "dust:9a5a3a", 0xE8DCC0, 0x9A5A3A, "LINE", "GLOW"),
    "rivermist": ("v:cloud", "v:snowflake", 0xD8EEF6, 0xFFFFFF, "WAVE", "GATHER"),
    "pearl": ("item:minecraft:prismarine_crystals", "v:end_rod", 0xE8F4F0, 0xFFFFFF, "RING", "GLOW"),
    "frostcastle": ("block:minecraft:packed_ice", "v:snowflake", 0x9AC8F0, 0xFFFFFF, "SPIKES", "GLOW"),
    "cascade": ("v:falling_water", "v:splash", 0x5AA8E8, 0xE0F6FF, "PILLAR", "RISING"),
    "jail": ("item:minecraft:chain", "dust:2a2a34", 0x5A5A68, 0x9AB8D8, "CAGE", "GROUND_SIGIL"),
    "avalanche": ("item:minecraft:snowball", "v:snowflake", 0xF4FAFF, 0xBFE0FF, "WAVE", "RISING"),
    "icicle": ("block:minecraft:ice", "v:snowflake", 0xA8D8FF, 0xFFFFFF, "SCATTER", "GATHER"),
    "teardrop": ("v:dripping_water", "v:end_rod", 0x8AC8FF, 0xFFFFFF, "RAIN", "GLOW"),
    "web": ("item:minecraft:cobweb", "dust:e8e8e8", 0xE8E8E8, 0x8ACA4A, "CAGE", "SPIN_UP"),
    "whitewater": ("v:splash", "v:bubble", 0xE8F6FF, 0x5AA8E8, "WAVE", "SPIN_UP"),
    "pilgrim": ("dust:f6e8b0", "v:end_rod", 0xF6E8B0, 0xFFFFFF, "SIGIL", "GROUND_SIGIL"),
    "sovereign": ("v:falling_water", "v:snowflake", 0x3A8AD0, 0xE0F4FF, "VORTEX", "RISING"),
    "holydew": ("v:dripping_water", "v:glow", 0xBFE8FF, 0xFFF4C0, "RAIN", "AIR_SIGIL"),
    "bell": ("v:note", "v:snowflake", 0xE8F0FF, 0xFFFFFF, "RING", "AIR_SIGIL"),
    "shell": ("block:minecraft:mossy_cobblestone", "v:poof", 0x6A8A5A, 0xD8C880, "DOME", "GROUND_SIGIL"),
    "heron": ("item:minecraft:feather", "v:cloud", 0xF8F8F8, 0xD8E8F0, "SPIRAL", "SPIN_UP"),
    "frostchain": ("item:minecraft:chain", "v:snowflake", 0x9ABAD8, 0xFFFFFF, "CHAIN", "GLOW"),
    "rainbow": ("v:glow", "dust:ff8ad8", 0x8AE8FF, 0xFF8AD8, "FAN", "AIR_SIGIL"),
    "cinderrune": ("er:rune", "v:flame", 0xFF7A30, 0xFFD080, "SIGIL", "GROUND_SIGIL"),
    "magma": ("v:lava", "dust:ff6a1a", 0xFF6A1A, 0xFFC060, "CRESCENT", "RISING"),
    "idolfire": ("v:flame", "dust:e8b860", 0xE8B860, 0xFF5A2A, "PILLAR", "GLOW"),
    "ruinstone": ("block:minecraft:stone_bricks", "v:poof", 0xA8A090, 0xE8D8B0, "DOME", "GROUND_SIGIL"),
    "amber": ("v:dripping_honey", "dust:e8a030", 0xE8A030, 0xFFE0A0, "FAN", "GLOW"),
    "ashdragon": ("v:large_smoke", "v:flame", 0x4A4440, 0xFF6A1A, "GEYSER", "RISING"),
    "embermoth": ("v:small_flame", "v:white_ash", 0xFFA040, 0xF4E8D8, "PETALS", "GATHER"),
    "salamander": ("v:flame", "dust:d03a1a", 0xD03A1A, 0xFFB040, "CHAIN", "GATHER"),
    "glyph": ("er:rune", "block:minecraft:chiseled_stone_bricks", 0x8AD8E8, 0xB8B0A0, "SIGIL", "GLOW"),
    "scorch": ("v:flame", "v:smoke", 0xFF7A20, 0x5A5048, "HELIX", "GATHER"),
    "guardian": ("block:minecraft:stone_bricks", "v:crit", 0xB8B0A0, 0xFFFFFF, "LINE", "GROUND_SIGIL"),
    "frenzy": ("dust:c01a2a", "v:damage_indicator", 0xC01A2A, 0xFF8A8A, "COMET", "RISING"),
    "kiln": ("v:flame", "v:lava", 0xFF9A3A, 0xFFE080, "ORBS", "AIR_SIGIL"),
    "oracle": ("v:falling_lava", "dust:2a1a2a", 0xFF6A1A, 0x3A2A3A, "RAIN", "GROUND_SIGIL"),
    "scorcheye": ("v:flame", "v:end_rod", 0xFF5A1A, 0xFFF0C0, "SIGIL", "AIR_SIGIL"),
    "sunstone": ("dust:ffe060", "v:end_rod", 0xFFE060, 0xFFFFFF, "STAR", "GLOW"),
    "flamewing": ("v:flame", "v:small_flame", 0xFF7A2A, 0xFFD060, "COMET", "GATHER"),
    "runethunder": ("er:rune", "v:electric_spark", 0x8AD8FF, 0xF6F28A, "SIGIL", "AIR_SIGIL"),
    "cloud": ("v:cloud", "dust:ffffff", 0xF4F8FF, 0xD8E8F8, "ARC", "SPIN_UP"),
    "skyforge": ("v:electric_spark", "dust:8ac8ff", 0x8AC8FF, 0xF6F28A, "LINE", "GLOW"),
    "skyoath": ("dust:ffe8a0", "v:end_rod", 0xFFE8A0, 0xFFFFFF, "CROSS", "GROUND_SIGIL"),
    "glacier": ("block:minecraft:blue_ice", "v:snowflake", 0x7AB8F0, 0xFFFFFF, "FAN", "RISING"),
    "horn": ("block:minecraft:dripstone_block", "v:crit", 0xB89A80, 0xFFFFFF, "SPIKES", "RISING"),
    "thunderhead": ("v:electric_spark", "v:cloud", 0xF6F28A, 0xD0D8E8, "PILLAR", "AIR_SIGIL"),
    "talon": ("dust:b8202a", "v:crit", 0xB8202A, 0xFFE0E0, "CRESCENT", "GATHER"),
    "skyshard": ("item:minecraft:quartz", "v:end_rod", 0xF4F0E8, 0xFFF0B0, "SCATTER", "GLOW"),
    "plumestorm": ("item:minecraft:feather", "v:electric_spark", 0x3A4A9A, 0xF6F28A, "SPIRAL", "SPIN_UP"),
    "summit": ("dust:f0f4ff", "v:cloud", 0xF0F4FF, 0xC0D8F0, "LINE", "GLOW"),
    "skypillar": ("v:end_rod", "dust:ffe8a0", 0xFFF4D0, 0xFFE8A0, "CRESCENT", "AIR_SIGIL"),
    "rod": ("v:electric_spark", "item:minecraft:copper_ingot", 0xF6F28A, 0xD87A4A, "PILLAR", "GLOW"),
    "stormcloud": ("v:electric_spark", "v:cloud", 0xC8D0E0, 0xF6F28A, "VORTEX", "AIR_SIGIL"),
    "cirrus": ("v:snowflake", "item:minecraft:feather", 0xE0F0FF, 0xFFFFFF, "FAN", "SPIN_UP"),
    "starwell": ("v:firework", "v:end_rod", 0xD8E0FF, 0xFFFFFF, "STAR", "AIR_SIGIL"),
    "tailwind": ("v:cloud", "v:white_ash", 0xE8F4F0, 0xFFFFFF, "ARC", "SPIN_UP"),
    "tempest": ("v:electric_spark", "v:rain", 0xE8E070, 0x8AB0D8, "FAN", "GATHER"),
    "horizon": ("v:end_rod", "dust:9ad0ff", 0xFFFFFF, 0x9AD0FF, "LINE", "GLOW"),
    "hollowroot": ("block:minecraft:rooted_dirt", "v:item_slime", 0x5A4A3A, 0x8ACA4A, "CRESCENT", "GATHER"),
    "oblivion": ("v:large_smoke", "v:reverse_portal", 0x2A2234, 0xB070FF, "LINE", "GLOW"),
    "dusk": ("dust:c86a4a", "v:large_smoke", 0xC86A4A, 0x5A3A6A, "RING", "SPIN_UP"),
    "nightmare": ("v:sculk_soul", "dust:6a2a9a", 0x6A2A9A, 0x40E0D0, "PILLAR", "GLOW"),
    "rubble": ("block:minecraft:deepslate_bricks", "v:poof", 0x4A4A50, 0xA0A0A8, "WAVE", "RISING"),
    "requiem": ("dust:5a1018", "v:soul", 0x5A1018, 0xA8E8F0, "RING", "SPIN_UP"),
    "shadowsilk": ("item:minecraft:string", "v:large_smoke", 0x3A3448, 0xD8D0E8, "CHAIN", "GATHER"),
    "doomguard": ("dust:8a1020", "v:crit", 0x8A1020, 0xFFD0D0, "LINE", "GATHER"),
    "voidwhisper": ("v:reverse_portal", "v:portal", 0x7A3AC8, 0xE0C0FF, "FAN", "GATHER"),
    "rotbark": ("v:item_slime", "block:minecraft:spruce_log", 0x5A8A2A, 0x4A3A28, "SPIKES", "RISING"),
    "gate": ("block:minecraft:iron_bars", "v:crit", 0x8A8A90, 0xFFFFFF, "DOME", "GROUND_SIGIL"),
    "voiddrake": ("v:reverse_portal", "dust:9a4ae0", 0x9A4AE0, 0xF0D8FF, "VORTEX", "GATHER"),
    "bonehex": ("item:minecraft:bone", "v:soul", 0xE8DCC0, 0x5ADCE0, "ORBS", "AIR_SIGIL"),
    "beacon": ("v:soul_fire_flame", "v:end_rod", 0x5ADCE0, 0xFFFFFF, "PILLAR", "AIR_SIGIL"),
    "ruinfire": ("v:flame", "v:reverse_portal", 0xFF5A2A, 0xA060E0, "RAIN", "AIR_SIGIL"),
    "deadwood": ("item:minecraft:dead_bush", "v:item_slime", 0x8A6A40, 0x7ACA3A, "SPIKES", "GATHER"),
    "endhowl": ("v:sculk_soul", "v:large_smoke", 0x2AB8B0, 0x3A3448, "WAVE", "GLOW"),
    "shardfall": ("item:minecraft:amethyst_shard", "v:snowflake", 0xB89AF0, 0xE0F4FF, "RAIN", "AIR_SIGIL"),
    "wheat": ("item:minecraft:wheat", "v:composter", 0xE0C060, 0xA8D060, "CRESCENT", "SPIN_UP"),
    "millstone": ("block:minecraft:smooth_stone", "v:poof", 0xB0B0B0, 0xE8E0D0, "RING", "SPIN_UP"),
    "harvestmoon": ("v:end_rod", "dust:ffd860", 0xFFF4D0, 0xFFD860, "CRESCENT", "GLOW"),
    "plow": ("block:minecraft:farmland", "block:minecraft:dirt", 0x6A4A30, 0x9A7A50, "WAVE", "GROUND_SIGIL"),
    "granary": ("block:minecraft:hay_block", "v:crit", 0xD8B040, 0xFFFFFF, "FAN", "GLOW"),
    "bull": ("dust:a02020", "v:poof", 0xA02020, 0xE8D8C0, "SPIKES", "RISING"),
    "sickle": ("item:minecraft:wheat", "v:crit", 0xD8C060, 0xFFFFFF, "CRESCENT", "SPIN_UP"),
    "strawdoll": ("item:minecraft:wheat", "v:item_slime", 0xC8A050, 0x6ACA3A, "SCATTER", "GATHER"),
    "blight": ("v:ash", "dust:7a8a3a", 0x7A8A3A, 0x3A3A2A, "VORTEX", "RISING"),
    "hayfork": ("block:minecraft:hay_block", "v:poof", 0xE0C060, 0xF0E8D0, "LINE", "GATHER"),
    "goldenear": ("item:minecraft:wheat", "v:end_rod", 0xF0D060, 0xFFFFFF, "HELIX", "GLOW"),
    "reaper": ("dust:8a1a1a", "v:crit", 0x8A1A1A, 0xE0E0E0, "FAN", "SPIN_UP"),
    "scarecrow": ("item:minecraft:wheat", "v:large_smoke", 0xC8A050, 0x2A2420, "ORBS", "RISING"),
    "sunflower": ("item:minecraft:sunflower", "dust:ffe040", 0xFFE040, 0x6A4A20, "PETALS", "AIR_SIGIL"),
    "strawfire": ("v:flame", "item:minecraft:wheat", 0xFF8A2A, 0xE0C060, "GEYSER", "AIR_SIGIL"),
    "crow": ("dust:1a1a22", "item:minecraft:feather", 0x1A1A22, 0x8A8A9A, "SCATTER", "GATHER"),
    "cornsilk": ("item:minecraft:string", "dust:f0e070", 0xF0E070, 0xFFFFFF, "FAN", "GLOW"),
    "bullhorn": ("item:minecraft:bone", "v:poof", 0xE8DCC0, 0xA07A50, "BURST", "GLOW"),
    "reed": ("item:minecraft:sugar_cane", "v:bubble", 0x7AB050, 0xD0F0FF, "ARC", "SPIN_UP"),
    "ripple": ("v:splash", "dust:3ab0a8", 0x3AB0A8, 0xE0FFFF, "RING", "GROUND_SIGIL"),
    "mirror": ("v:end_rod", "dust:c8f0ff", 0xC8F0FF, 0xFFFFFF, "CROSS", "GLOW"),
    "driftwood": ("item:minecraft:stick", "v:splash", 0xA88A60, 0x8AC8F0, "WAVE", "RISING"),
    "mire": ("block:minecraft:mud", "v:item_slime", 0x4A3A30, 0x7ACA3A, "GEYSER", "GROUND_SIGIL"),
    "fin": ("dust:2a8a9a", "v:bubble", 0x2A8A9A, 0x9AF0E8, "WAVE", "SPIN_UP"),
    "minnow": ("dust:d8e0e8", "v:bubble", 0xD8E0E8, 0x8AC8E0, "SCATTER", "SPIN_UP"),
    "gel": ("v:item_slime", "dust:6ad090", 0x6AD090, 0xD0FFE0, "ORBS", "GATHER"),
    "kingfisher": ("v:electric_spark", "dust:2ab0c0", 0x2AB0C0, 0xF09A3A, "COMET", "GATHER"),
    "rustharpoon": ("dust:9a5a30", "item:minecraft:chain", 0x9A5A30, 0xC0C0C8, "CHAIN", "GATHER"),
    "pearltide": ("item:minecraft:prismarine_crystals", "v:splash", 0xE0F0F0, 0x6AB8E8, "WAVE", "GLOW"),
    "drowned": ("v:dripping_water", "item:minecraft:kelp", 0x3A6A6A, 0x5A8A3A, "HELIX", "RISING"),
    "lotus": ("v:cherry_leaves", "v:glow", 0xF0A8C8, 0xFFFFFF, "PETALS", "AIR_SIGIL"),
    "prophecy": ("v:bubble", "v:end_rod", 0x8AD8F0, 0xFFFFFF, "SIGIL", "AIR_SIGIL"),
    "frozenmirror": ("v:snowflake", "v:end_rod", 0xC0E8FF, 0xFFFFFF, "CAGE", "AIR_SIGIL"),
    "reedpipe": ("v:note", "item:minecraft:sugar_cane", 0x9AD070, 0xFFFFFF, "ARC", "GATHER"),
    "dragonfly": ("v:electric_spark", "dust:3ac8b0", 0x3AC8B0, 0xF6F28A, "SPIRAL", "GLOW"),
    "spiritpearl": ("v:end_rod", "v:bubble", 0xFFFFFF, 0x8AD8F0, "RAIN", "AIR_SIGIL"),
    "deck": ("block:minecraft:oak_planks", "v:splash", 0xB08A5A, 0x8AC8F0, "ARC", "GATHER"),
    "coin": ("item:minecraft:gold_nugget", "v:wax_on", 0xF0C040, 0xFFF0A0, "SCATTER", "GLOW"),
    "tide": ("v:splash", "dust:2a7ab0", 0x2A7AB0, 0xD8F4FF, "WAVE", "RISING"),
    "anchor": ("item:minecraft:chain", "block:minecraft:iron_block", 0x6A6A74, 0xC8C8D0, "CHAIN", "GROUND_SIGIL"),
    "stormsail": ("v:cloud", "v:electric_spark", 0xE8EEF4, 0xF6F28A, "VORTEX", "SPIN_UP"),
    "pincer": ("dust:b0281a", "block:minecraft:iron_block", 0xB0281A, 0xC8C8D0, "CROSS", "GATHER"),
    "saltspray": ("v:splash", "v:white_ash", 0xD8EEF4, 0xFFFFFF, "SCATTER", "SPIN_UP"),
    "ink": ("v:squid_ink", "v:large_smoke", 0x14101C, 0x6A5A8A, "VORTEX", "GATHER"),
    "brawl": ("v:crit", "dust:c03030", 0xE8E0D0, 0xC03030, "BURST", "RISING"),
    "boarding": ("v:crit", "v:splash", 0xD8D0C0, 0x8AC8F0, "COMET", "GATHER"),
    "windlass": ("item:minecraft:chain", "item:minecraft:copper_ingot", 0x9A9AA4, 0xD87A4A, "CHAIN", "SPIN_UP"),
    "maelstrom": ("v:bubble", "v:splash", 0x2A6AA0, 0xC0F0FF, "VORTEX", "SPIN_UP"),
    "buoy": ("v:end_rod", "dust:ff5040", 0xFFF4D0, 0xFF5040, "RING", "GLOW"),
    "compass": ("dust:e8c060", "v:cloud", 0xE8C060, 0xF4F8FF, "CROSS", "AIR_SIGIL"),
    "krakenink": ("v:squid_ink", "dust:3a2a4a", 0x2A1A3A, 0x7A5AA0, "CHAIN", "GATHER"),
    "broadside": ("v:large_smoke", "v:crit", 0x5A5048, 0xFFE0A0, "FAN", "GATHER"),
    "gull": ("item:minecraft:feather", "v:cloud", 0xFFFFFF, 0xD0E0F0, "SPIRAL", "SPIN_UP"),
    "tradewind": ("v:cloud", "v:electric_spark", 0xE0F0F8, 0xF6F28A, "LINE", "SPIN_UP"),
    # tier materials
    "iron": ("v:crit", "dust:c8ccd4", 0xC8CCD4, 0xFFFFFF, "CRESCENT", "GLOW"),
    "steel": ("er:slash", "v:crit", 0xA8B8D0, 0xFFFFFF, "SCATTER", "SPIN_UP"),
    "sandstone": ("block:minecraft:sand", "v:poof", 0xD8C08A, 0xF0E4C0, "VORTEX", "GROUND_SIGIL"),
    "frost": ("v:snowflake", "block:minecraft:ice", 0x9AD8FF, 0xFFFFFF, "SPIKES", "GATHER"),
    "flame": ("v:flame", "v:lava", 0xFF7A2A, 0xFFD060, "WAVE", "RISING"),
    "celestial": ("v:end_rod", "v:firework", 0xFFF6D8, 0xD8E0FF, "STAR", "AIR_SIGIL"),
    "abyss": ("v:reverse_portal", "v:squid_ink", 0x6A2AB0, 0x14101C, "VORTEX", "GATHER"),
}

# weapon id: (art id, 日本語名, English name, palette, primary shape or None, form or None, extras)
F = {
    # ---------------------------------------------------------------- 1 はじまりの草原
    "meadowlark_blade": ("art_larkdance_cut", "ヒバリ舞いの三連斬", "Larkdance Cut", "lark", None, None, ["STAMINA"]),
    "wayfarers_oath": ("art_oathpoint", "誓いの一刺し", "Oathpoint", "oath", None, None, ["EXECUTE", "CRIT_UP"]),
    "galewild_saber": ("art_galewild_thunderdash", "野分の迅雷斬", "Galewild Thunderdash", "gale", "SPIRAL", None,
                       ["SPEED"]),
    "pasture_cleaver": ("art_pasture_reaping", "牧草刈りの大振り", "Pasture Reaping", "hay", "WAVE", None,
                        ["KNOCKBACK"]),
    "hillwarden_greatsword": ("art_hillwarden_earthcrawl", "丘守りの地這い", "Hillwarden's Earthcrawl", "hill", "WAVE",
                              None, ["SLOW_FIELD"]),
    "garms_maw": ("art_garms_crushing_bite", "ガルムの噛み砕き", "Garm's Crushing Bite", "garm", "SPIKES", None,
                  ["LIFESTEAL", "KNOCKBACK"]),
    "undercity_kris": ("art_undercanal_shadowstab", "地下水路の影刺し", "Undercanal Shadowstab", "undercity", None,
                       None, ["VANISH"]),
    "thistle_fangs": ("art_thistle_burst", "薊棘の乱れ咲き", "Thistle Burst", "thistle", "SPIKES", None, ["ZONE"]),
    "hooks_reapers": ("art_hooks_rending_pull", "フックの鉤裂き", "Hook's Rending Pull", "hook", "CHAIN", None,
                      ["PULL", "EXECUTE"]),
    "herdsman_pike": ("art_herdsmans_drive", "牧人の追い突き", "Herdsman's Drive", "herd", None, None, ["KNOCKBACK"]),
    "canal_partisan": ("art_sluicegate_torrent", "水門開きの奔流突き", "Sluicegate Torrent", "canal", "WAVE", None,
                       ["SLOW_FIELD"]),
    "durns_tuskspear": ("art_durns_tusk_charge", "ドルンの猪突", "Durn's Tusk Charge", "tusk", None, "SPEAR_CHARGE",
                        ["LAUNCH"]),
    "sprout_wand": ("art_sprouting_seeds", "若芽の三つ弾", "Sprouting Seeds", "sprout", None, None, ["REGEN"]),
    "lantern_of_the_lost": ("art_guiding_wisplights", "迷い灯の導き", "Guiding Wisplights", "wisp", "ORBS",
                            "STAFF_ORBS", ["HEAL"]),
    "dandelion_staff": ("art_dandelion_drift", "綿毛の旅立ち", "Dandelion Drift", "dandelion", "SCATTER", None,
                        ["SPEED"]),
    "shepherds_bow": ("art_watchtower_spread", "見張り台の扇射ち", "Watchtower Spread", "watch", None, None,
                      ["EVASION"]),
    "rogues_recurve": ("art_viper_shot", "毒蛇の一矢", "Viper Shot", "viper", None, None, ["EXECUTE"]),
    "skylark_longbow": ("art_skylark_chorus", "揚げ雲雀の囀り矢", "Skylark Chorus", "skylark", "SPIRAL", None,
                        ["SPEED"]),
    # ---------------------------------------------------------------- 2 月影の森
    "moonleaf_blade": ("art_moonleaf_waltz", "月葉の舞斬り", "Moonleaf Waltz", "moonleaf", "PETALS", None,
                       ["EVASION"]),
    "heartwood_edge": ("art_heartwood_rings", "芯木の年輪斬り", "Heartwood Rings", "heartwood", "RING", None,
                       ["DEFENSE_UP"]),
    "eclipse_moonblade": ("art_eclipse_severance", "月蝕の断ち", "Eclipse Severance", "eclipse", "CRESCENT", None,
                          ["MARK", "EXECUTE"]),
    "thornwall_greatsword": ("art_bramblewall_rise", "茨垣の隆起", "Bramblewall Rise", "bramble", None, None,
                             ["ZONE", "THORNS"]),
    "rootbreaker": ("art_rootbreaker_fissure", "根砕きの地割れ", "Rootbreaker Fissure", "root", "WAVE", None, ["STUN"]),
    "nightbough_claymore": ("art_nightbough_sweep", "夜枝の大薙ぎ", "Nightbough Sweep", "nightbough", None, None,
                            ["SIPHON"]),
    "silkcutter_pair": ("art_silkcutter_gale", "絹断ちの糸疾風", "Silkcutter Gale", "silk", "ARC", None, ["SPEED"]),
    "moth_wing_daggers": ("art_scale_dust_daze", "鱗粉の幻惑", "Scale-Dust Daze", "moth", "DOME", "TWIN_CROSS",
                          ["SLOW_FIELD", "BLIND"]),
    "lunars_moonclaws": ("art_lunars_crescent_fangs", "ルナールの月牙", "Lunar's Crescent Fangs", "lunar", "CRESCENT",
                         None, ["EVASION"]),
    "vinewhip_spear": ("art_vinelash_snare", "蔓鞭の絡め突き", "Vinelash Snare", "vine", "CHAIN", None, ["PULL"]),
    "owl_feather_lance": ("art_silent_owlfeathers", "梟羽の静かな連矢", "Silent Owlfeathers", "owl", "FAN", "TWIN_FAN",
                          ["VANISH"]),
    "grovewarden_glaive": ("art_grovewardens_clearing", "森番の倒木払い", "Grovewarden's Clearing", "grove", "WAVE",
                           None, ["KNOCKBACK"]),
    "mosslight_staff": ("art_mosslight_motes", "苔灯りの癒し玉", "Mosslight Motes", "mossglow", None, None,
                        ["FIELD_HEAL"]),
    "crescent_druid_staff": ("art_crescent_frostlance", "三日月の冷光槍", "Crescent Frostlance", "crescentfrost",
                             None, None, ["EXECUTE"]),
    "nebulas_starbranch": ("art_nebulas_starfall", "ネブラの星降らし", "Nebula's Starfall", "nebula", "RAIN",
                           "STAFF_METEOR", ["CLEANSE"]),
    "willow_shortbow": ("art_willowwind_rapid", "柳風の速射", "Willowwind Rapid", "willow", "ARC", None, ["EVASION"]),
    "silverbark_bow": ("art_silverbark_moonshot", "銀樹皮の月光矢", "Silverbark Moonshot", "silverbark", None, None,
                       ["CRIT_UP"]),
    "belladonnas_nightshade": ("art_nightshade_rain", "宵闇の毒花雨", "Nightshade Rain", "nightshade", None, None,
                               ["ZONE"]),
    # ---------------------------------------------------------------- 3 鉄鉱の鉱山
    "ore_vein_sword": ("art_orevein_trace", "鉱脈なぞりの連斬", "Orevein Trace", "ore", None, None, ["DEFENSE_UP"]),
    "sunscarab_khopesh": ("art_sunscarab_hook", "日輪甲虫の鉤刃", "Sunscarab Hook", "sunscarab", "SIGIL", None,
                          ["PULL", "EXECUTE"]),
    "vein_eaters_jointblade": ("art_vein_eaters_segment_run", "鉱脈喰らいの節走り", "Vein-Eater's Segment Run",
                               "centipede", "CHAIN", "SWORD_DASH", ["EXECUTE"]),
    "pickaxe_breaker": ("art_oreshatter_quake", "鉱石砕きの地揺れ", "Oreshatter Quake", "oreshatter", "WAVE", None,
                        ["STUN"]),
    "coalheart_greatsword": ("art_coalheart_emberstrike", "炭芯の熾火打ち", "Coalheart Emberstrike", "coal", None,
                             "GREAT_ERUPTION", ["IMBUE"]),
    "colossal_ironvein": ("art_ironvein_spikes", "鉄脈の大棘", "Ironvein Spikes", "ironspike", "SPIKES", None,
                          ["ZONE", "KNOCKBACK"]),
    "ember_shivs": ("art_ember_patter", "熾火の連打ち", "Ember Patter", "ember", None, None, ["IMBUE"]),
    "tombsand_fangs": ("art_tombsand_bite", "墓砂の牙刺し", "Tombsand Bite", "tombsand", "VORTEX", None,
                       ["SLOW_FIELD"]),
    "cogwheel_daggers": ("art_clockwork_whirl", "歯車仕掛けの回転刃", "Clockwork Whirl", "gear", "RING", "TWIN_CROSS",
                         ["STAMINA"]),
    "minecart_pike": ("art_railrun_thrust", "線路走りの一直突き", "Railrun Thrust", "rail", None, None, ["DASH"]),
    "veined_steel_lance": ("art_bluevein_triple", "青筋鋼の三段突き", "Bluevein Triple", "bluevein", None, None,
                           ["CRIT_UP"]),
    "dolgas_tomb_halberd": ("art_dolgas_gravesweep", "ドルガの墓守薙ぎ", "Dolga's Gravesweep", "grave", None,
                            "GREAT_SWEEP", ["TAUNT"]),
    "geode_staff": ("art_geode_burst", "晶洞の結晶弾", "Geode Burst", "geode", "SCATTER", None, ["SHIELD"]),
    "sunpriest_sceptre": ("art_sunpriests_pillar", "陽神官の日輪柱", "Sunpriest's Pillar", "sunpillar", "PILLAR",
                          "STAFF_BEAM", ["HEAL"]),
    "golem_core_rod": ("art_corelight_lance", "核心の雷光槍", "Corelight Lance", "golemcore", None, None, ["STUN"]),
    "cave_wren_bow": ("art_cave_wren_hop", "坑道ミソサザイの跳ね矢", "Cave Wren Hop", "wren", "ARC", None,
                      ["BACKSTEP"]),
    "firedamp_longbow": ("art_firedamp_blast", "坑気の爆ぜ矢", "Firedamp Blast", "firedamp", None, "BOW_IMPACT",
                         ["ZONE"]),
    "rustbones_rattle": ("art_rattlebone_shot", "錆骨の鳴り矢", "Rattlebone Shot", "rattlebone", None, None,
                         ["SIPHON"]),
    # ---------------------------------------------------------------- 4 聖なる滝
    "rivermist_sword": ("art_rivermist_flow", "川霧の流れ斬り", "Rivermist Flow", "rivermist", "WAVE", None,
                        ["EVASION"]),
    "pearlguard_blade": ("art_pearlguard_halo", "真珠守りの光輪", "Pearlguard Halo", "pearl", "RING", "SWORD_BURST",
                         ["SHIELD"]),
    "frostcastle_rapier": ("art_frostcastle_point", "氷城の一点突き", "Frostcastle Point", "frostcastle", "SPIKES",
                           None, ["EXECUTE"]),
    "cascade_greatsword": ("art_cascade_drop", "瀑布落とし", "Cascade Drop", "cascade", "PILLAR", None, ["KNOCKBACK"]),
    "jailers_verdict": ("art_jailers_sentence", "看守の判決", "Jailer's Sentence", "jail", "CAGE", "GREAT_GUILLOTINE",
                        ["MARK", "EXECUTE"]),
    "gouras_avalanche": ("art_gouras_avalanche", "ゴウラの雪崩落とし", "Goura's Avalanche", "avalanche", "WAVE", None,
                         ["ZONE", "STUN"]),
    "icicle_pair": ("art_icicle_volley", "氷柱の連投げ", "Icicle Volley", "icicle", "SCATTER", "TWIN_FAN", []),
    "spirit_tear_daggers": ("art_spirit_teardrops", "滝霊の涙雨", "Spirit Teardrops", "teardrop", "RAIN", None,
                            ["HEAL"]),
    "mistweavers_fangs": ("art_mistweavers_venom_web", "霧糸の毒網", "Mistweaver's Venom Web", "web", "CAGE", None,
                          ["SLOW_FIELD"]),
    "whitewater_spear": ("art_whitewater_crossing", "白波の渡し突き", "Whitewater Crossing", "whitewater", "WAVE",
                         None, ["KNOCKBACK"]),
    "pilgrim_glaive": ("art_pilgrims_prayer", "巡礼の祈り突き", "Pilgrim's Prayer", "pilgrim", "SIGIL", None,
                       ["CLEANSE"]),
    "falls_sovereign_trident": ("art_sovereign_torrent", "滝主の三叉奔流", "Sovereign Torrent", "sovereign", "FAN",
                                None, ["ZONE", "PULL"]),
    "holy_dew_staff": ("art_holy_dew_shower", "聖露の慈雨", "Holy Dew Shower", "holydew", "RAIN", "STAFF_ORBS",
                       ["HEAL"]),
    "handmaidens_snowbell": ("art_snowbell_chime", "雪鈴の澄んだ響き", "Snowbell Chime", "bell", "RING",
                             "STAFF_BURST", ["CLEANSE", "SHIELD"]),
    "ords_shellstaff": ("art_ords_shellguard", "オルドの甲羅の守り", "Ord's Shellguard", "shell", "DOME",
                        "STAFF_METEOR", ["SHIELD"]),
    "heron_bow": ("art_heron_glide", "白鷺の舞い矢", "Heron Glide", "heron", "SPIRAL", None, ["SPEED"]),
    "frozen_chain_bow": ("art_frostchain_bind", "凍鎖縛りの矢", "Frostchain Bind", "frostchain", "CHAIN", None,
                         ["STUN"]),
    "rainbow_spray_bow": ("art_rainbow_spray", "虹飛沫の扇", "Rainbow Spray", "rainbow", "FAN", "BOW_FAN", ["HEAL"]),
    # ---------------------------------------------------------------- 5 古代遺跡
    "cinder_rune_sword": ("art_cinder_glyph", "燠文字の刻印", "Cinder Glyph", "cinderrune", "SIGIL", None, ["MARK"]),
    "magma_vein_falchion": ("art_magma_vein_triple", "溶岩脈の三段斬り", "Magma Vein Triple", "magma", None, None,
                            ["ZONE"]),
    "agnis_idol_blade": ("art_agnis_sacred_flame", "アグニスの神火落とし", "Agnis's Sacred Flame", "idolfire", "PILLAR",
                         None, ["MARK", "ATTACK_UP"]),
    "ruin_guardian_slab": ("art_tablet_crush", "石板落とし", "Tablet Crush", "ruinstone", "DOME", None, ["DEFENSE_UP"]),
    "amber_sun_greatsword": ("art_ambersun_arc", "琥珀陽の大薙ぎ", "Ambersun Arc", "amber", None, None,
                             ["PARTY_ATTACK"]),
    "ashen_dragonfang": ("art_ashen_dragon_roar", "灰竜の咆炎", "Ashen Dragon Roar", "ashdragon", "GEYSER",
                         "GREAT_ERUPTION", ["ZONE", "KNOCKBACK"]),
    "ember_moth_blades": ("art_embermoth_flutter", "火蛾の羽ばたき", "Embermoth Flutter", "embermoth", "PETALS",
                          "TWIN_CROSS", ["EVASION"]),
    "salamander_tongues": ("art_salamander_lash", "火蜥蜴の舌打ち", "Salamander Lash", "salamander", "CHAIN",
                           "TWIN_CHAIN", ["IMBUE"]),
    "glyphcut_daggers": ("art_glyphcut_engraving", "碑文刻みの連刻", "Glyphcut Engraving", "glyph", "SIGIL", None,
                         ["CRIT_UP"]),
    "scorchpoint_spear": ("art_scorchpoint_triple", "焦尖の三連突き", "Scorchpoint Triple", "scorch", None, None,
                          ["IMBUE"]),
    "guardian_plate_halberd": ("art_bulwark_thrust", "守護板の城壁突き", "Bulwark Thrust", "guardian", None, None,
                               ["SHIELD"]),
    "valkas_berserk_lance": ("art_valkas_frenzy_charge", "ヴァルカの狂乱突撃", "Valka's Frenzy Charge", "frenzy",
                             None, "SPEAR_CHARGE", ["ATTACK_UP", "LIFESTEAL"]),
    "kiln_staff": ("art_kilnfire_orbs", "窯火の焼き玉", "Kilnfire Orbs", "kiln", None, None, ["ZONE"]),
    "lava_oracle_staff": ("art_lava_oracle", "溶岩巫女の託宣", "Lava Oracle", "oracle", "RAIN", "STAFF_METEOR",
                          ["MARK"]),
    "scorch_eyes_gaze": ("art_scorching_gaze", "灼眼の睨み", "Scorching Gaze", "scorcheye", "LINE", "STAFF_BEAM",
                         ["STUN"]),
    "sunstone_bow": ("art_sunstone_ray", "陽石の光矢", "Sunstone Ray", "sunstone", "STAR", None, ["CRIT_UP"]),
    "flamewing_bow": ("art_flamewing_trio", "炎翼の三矢", "Flamewing Trio", "flamewing", None, None, ["IMBUE"]),
    "rune_tablet_longbow": ("art_tablet_thunderscript", "石版の雷文字", "Tablet Thunderscript", "runethunder", "SIGIL",
                            None, ["MARK"]),
    # ---------------------------------------------------------------- 6 天翔の峰
    "cloudcutter": ("art_cloudcutter_flash", "雲裂き一閃", "Cloudcutter Flash", "cloud", None, None, ["EVASION"]),
    "skyforge_saber": ("art_skyforge_thunderblade", "天工の雷刃", "Skyforge Thunderblade", "skyforge", None, None,
                       ["STUN"]),
    "alvins_oathblade": ("art_alvins_sky_oath", "アルヴィンの天誓", "Alvin's Sky Oath", "skyoath", "CROSS", None,
                         ["SHIELD", "PARTY_ATTACK"]),
    "glacier_crest_claymore": ("art_glacier_crest_sweep", "氷嶺の大薙ぎ", "Glacier Crest Sweep", "glacier", None,
                               None, ["SLOW_FIELD"]),
    "thunderhorn_greatsword": ("art_thunderhorn_uppercut", "雷角の突き上げ", "Thunderhorn Uppercut", "horn", "SPIKES",
                               "GREAT_REND", ["LAUNCH"]),
    "thunderhead_greatblade": ("art_thunderhead_strike", "雷雲の落雷斬", "Thunderhead Strike", "thunderhead", "PILLAR",
                               "GREAT_GUILLOTINE", ["ZONE", "STUN"]),
    "wyvern_talon_pair": ("art_wyvern_talonrake", "飛竜の爪裂き", "Wyvern Talonrake", "talon", "CRESCENT", None,
                          ["LIFESTEAL"]),
    "sky_ruin_shards": ("art_skyruin_shardlight", "天遺跡の光片", "Skyruin Shardlight", "skyshard", "SCATTER",
                        "TWIN_FAN", ["CRIT_UP"]),
    "zephyrs_thunderplumes": ("art_zephyrs_plumestorm", "ゼファーの雷羽嵐", "Zephyr's Plumestorm", "plumestorm",
                              "SPIRAL", None, ["SPEED", "ZONE"]),
    "summit_spear": ("art_summit_banner", "頂の旗突き", "Summit Banner", "summit", None, None, ["PARTY_ATTACK"]),
    "celestial_pillar_glaive": ("art_celestial_crescent", "天柱の月弧", "Celestial Crescent", "skypillar", "CRESCENT",
                                "GREAT_SWEEP", ["MARK"]),
    "lightning_rod_lance": ("art_rodcall_lightning", "避雷針の誘雷", "Rodcall Lightning", "rod", "PILLAR", None,
                            ["MARK"]),
    "stormcaller_staff": ("art_stormcall_cloud", "嵐呼びの雷雲", "Stormcall Cloud", "stormcloud", "VORTEX", None,
                          ["ZONE"]),
    "cirrus_rod": ("art_cirrus_featherfrost", "巻雲の氷羽", "Cirrus Featherfrost", "cirrus", None, "STAFF_VOLLEY",
                   ["SPEED"]),
    "starwell_staff": ("art_starwell_draw", "星井戸の汲み光", "Starwell Draw", "starwell", "STAR", None, ["STAMINA"]),
    "windrider_bow": ("art_tailwind_arrows", "風乗りの追い風矢", "Tailwind Arrows", "tailwind", "ARC", None, ["SPEED"]),
    "tempest_recurve": ("art_tempest_fan", "大嵐の雷扇", "Tempest Fan", "tempest", None, "BOW_FAN", ["STUN"]),
    "peak_snipers_horizon": ("art_horizon_shot", "地平射ち", "Horizon Shot", "horizon", None, None,
                             ["EXECUTE", "CRIT_UP"]),
    # ---------------------------------------------------------------- 7 終焉の森
    "hollowroot_blade": ("art_hollowroot_drain", "虚根の吸い上げ", "Hollowroot Drain", "hollowroot", None, None,
                         ["SIPHON"]),
    "oblivion_edge": ("art_oblivion_stroke", "忘却の一太刀", "Oblivion Stroke", "oblivion", None, None, ["VANISH"]),
    "last_dusk": ("art_last_dusk_rend", "終の黄昏斬り", "Last Dusk Rend", "dusk", "RING", "SWORD_BURST",
                  ["ZONE", "SIPHON"]),
    "nightmare_greatsword": ("art_nightmare_descent", "悪夢の振り下ろし", "Nightmare Descent", "nightmare", None,
                             "GREAT_GUILLOTINE", ["EXECUTE"]),
    "towerfall_greatsword": ("art_towerfall_rubble", "塔崩しの瓦礫薙ぎ", "Towerfall Rubble", "rubble", "WAVE", None,
                             ["STUN"]),
    "black_knights_requiem": ("art_black_knights_requiem", "黒騎士の鎮魂斬", "Black Knight's Requiem", "requiem", "RING",
                              "GREAT_SPIN", ["LIFESTEAL", "TAUNT"]),
    "shadowsilk_fangs": ("art_shadowsilk_stitch", "影絹の縫い留め", "Shadowsilk Stitch", "shadowsilk", "CHAIN", None,
                         ["SLOW_FIELD"]),
    "doomguard_shivs": ("art_doomguard_throatcut", "滅衛の喉裂き", "Doomguard Throatcut", "doomguard", None, None,
                        ["EXECUTE"]),
    "voidwhisper_daggers": ("art_voidwhisper_fan", "虚囁の刃投げ", "Voidwhisper Fan", "voidwhisper", None, None,
                            ["VANISH"]),
    "corrupt_bark_spear": ("art_rotbark_lance", "腐皮の毒槍", "Rotbark Lance", "rotbark", "SPIKES", None, ["ZONE"]),
    "gatebreaker_halberd": ("art_gatebreaker_smash", "門破りの叩き割り", "Gatebreaker Smash", "gate", "DOME",
                            "SPEAR_IMPALE", ["KNOCKBACK", "STUN"]),
    "nihils_voidfang": ("art_nihils_void_devour", "ニヒルの虚喰み", "Nihil's Void Devour", "voiddrake", "VORTEX",
                        "SPEAR_PIERCE", ["PULL", "SIPHON"]),
    "cultist_bonestaff": ("art_bonehex", "信徒の骨呪", "Bonehex", "bonehex", None, None, ["SIPHON"]),
    "abyssal_lantern": ("art_abyssal_beacon", "深淵灯の導き火", "Abyssal Beacon", "beacon", "PILLAR", None,
                        ["HEAL", "CLEANSE"]),
    "xenos_ruinsceptre": ("art_xenos_doom_decree", "ゼノスの破滅宣告", "Xenos's Doom Decree", "ruinfire", "RAIN",
                          "STAFF_METEOR", ["MARK", "EXECUTE"]),
    "deadwood_bow": ("art_deadwood_thornshot", "枯木の毒棘矢", "Deadwood Thornshot", "deadwood", "SPIKES", None,
                     ["SLOW_FIELD"]),
    "end_howl_longbow": ("art_endhowl_shot", "終吠えの一矢", "Endhowl Shot", "endhowl", "WAVE", "BOW_PIERCE", ["STUN"]),
    "oblivion_shard_bow": ("art_oblivion_shardfall", "忘却片の降り矢", "Oblivion Shardfall", "shardfall", None, None,
                           ["SLOW_FIELD"]),
    # ---------------------------------------------------------------- 8 豊穣の農場
    "wheatstalk_sword": ("art_wheatcut_triple", "麦刈りの三振り", "Wheatcut Triple", "wheat", None, None, ["STAMINA"]),
    "millstone_blade": ("art_millstone_turn", "石臼回し", "Millstone Turn", "millstone", "RING", "SWORD_BURST",
                        ["DEFENSE_UP"]),
    "harvest_moon_sabre": ("art_harvest_moon_flash", "収穫月の一閃", "Harvest Moon Flash", "harvestmoon", "CRESCENT",
                           None, ["HEAL"]),
    "plowshare_greatsword": ("art_plowshare_upturn", "鋤起こし", "Plowshare Upturn", "plow", "WAVE",
                             "GREAT_ERUPTION", ["LAUNCH"]),
    "granary_gate_blade": ("art_granary_bar_sweep", "穀倉門の閂薙ぎ", "Granary Bar Sweep", "granary", None, None,
                           ["KNOCKBACK"]),
    "brutus_horncleaver": ("art_brutus_bull_rush", "ブルータスの猛牛突進", "Brutus's Bull Rush", "bull", "SPIKES",
                           "GREAT_REND", ["DASH", "KNOCKBACK"]),
    "twin_sickles": ("art_twin_sickle_harvest", "双鎌の刈り取り", "Twin Sickle Harvest", "sickle", "CRESCENT",
                     "TWIN_CROSS", ["STAMINA"]),
    "strawman_needles": ("art_strawdoll_curse", "藁人形の呪い針", "Strawdoll Curse", "strawdoll", "SCATTER",
                         "TWIN_FAN", ["STUN"]),
    "blight_harvest_scythes": ("art_blight_reaping", "枯穂の死神刈り", "Blight Reaping", "blight", "VORTEX", None,
                               ["ZONE", "SIPHON"]),
    "pitchfork_spear": ("art_pitchfork_toss", "熊手の放り上げ", "Pitchfork Toss", "hayfork", None, None, ["LAUNCH"]),
    "golden_ear_lance": ("art_golden_ear_blessing", "黄金穂の祝い突き", "Golden Ear Blessing", "goldenear", None, None,
                         ["PARTY_ATTACK"]),
    "harvest_boss_reaper": ("art_boss_great_reap", "頭目の大刈り", "Harvest Boss's Great Reap", "reaper", None,
                            "GREAT_SWEEP", ["LIFESTEAL"]),
    "scarecrow_post": ("art_scarecrow_stare", "かかしの睨み", "Scarecrow Stare", "scarecrow", None, None, ["STUN"]),
    "sunflower_staff": ("art_sunflower_glow", "向日葵の陽だまり", "Sunflower Glow", "sunflower", "PETALS", None,
                        ["FIELD_HEAL"]),
    "stroms_strawcrown": ("art_stroms_strawfire_crown", "ストロームの藁火の冠", "Strom's Strawfire Crown", "strawfire",
                          "GEYSER", "STAFF_METEOR", ["ZONE"]),
    "crow_scaring_bow": ("art_crowscatter", "鴉追いの散らし矢", "Crowscatter", "crow", "SCATTER", None, ["BLIND"]),
    "cornsilk_bow": ("art_cornsilk_fan", "とうもろこし糸の扇射ち", "Cornsilk Fan", "cornsilk", None, None, ["STAMINA"]),
    "bullhorn_recurve": ("art_bullhorn_heavy_shot", "牛角の重矢", "Bullhorn Heavy Shot", "bullhorn", None, None,
                         ["EXECUTE"]),
    # ---------------------------------------------------------------- 9 湖畔のほとり
    "reedcutter": ("art_reedcutter_rush", "葦刈りの疾走斬", "Reedcutter Rush", "reed", None, None, ["SPEED"]),
    "lake_scale_sword": ("art_lakescale_ripple", "湖鱗の波紋斬り", "Lakescale Ripple", "ripple", "RING", "SWORD_BURST",
                         ["SLOW_FIELD"]),
    "mirror_surface_blade": ("art_mirrorwater_reflection", "水鏡の写し斬り", "Mirrorwater Reflection", "mirror",
                             "CROSS", None, ["MARK", "EVASION"]),
    "driftwood_greatsword": ("art_driftwood_heave", "流木の打ち上げ", "Driftwood Heave", "driftwood", "WAVE", None,
                             ["LAUNCH"]),
    "marshking_greatsword": ("art_marshkings_mire", "沼王の泥沼", "Marshking's Mire", "mire", "GEYSER",
                             "GREAT_ERUPTION", ["SLOW_FIELD"]),
    "numels_greatfin": ("art_numels_finsweep", "ヌメルの大鰭薙ぎ", "Numel's Finsweep", "fin", "WAVE", None, ["PULL"]),
    "minnow_knives": ("art_minnow_school", "小魚群れの投げ刃", "Minnow School", "minnow", "SCATTER", "TWIN_FAN",
                      ["SPEED"]),
    "gel_glass_daggers": ("art_gelglass_dissolve", "沼玻璃の溶け刺し", "Gelglass Dissolve", "gel", "ORBS", None,
                          ["SIPHON"]),
    "kingfisher_pair": ("art_kingfisher_dive", "翡翠鳥の急降下", "Kingfisher Dive", "kingfisher", "COMET", None,
                        ["LEAP"]),
    "rusted_harpoon": ("art_rusted_harpoon_haul", "錆銛の引き寄せ", "Rusted Harpoon Haul", "rustharpoon", "CHAIN",
                       None, ["PULL"]),
    "pearl_tide_spear": ("art_pearltide_ebb", "真珠潮の満ち引き", "Pearltide Ebb", "pearltide", "WAVE", None,
                         ["BACKSTEP"]),
    "drowned_fishers_gaff": ("art_drowned_fishers_catch", "沈んだ漁師の釣り上げ", "Drowned Fisher's Catch", "drowned",
                             None, None, ["PULL", "LAUNCH"]),
    "lotus_staff": ("art_lotus_unfolding", "蓮華の開花", "Lotus Unfolding", "lotus", "PETALS", None, ["FIELD_HEAL"]),
    "lakebed_oracle": ("art_lakebed_prophecy", "湖底の予言光", "Lakebed Prophecy", "prophecy", "SIGIL", None, ["MARK"]),
    "melusines_mirror": ("art_melus_frozen_mirror", "メリューの凍て鏡", "Melu's Frozen Mirror", "frozenmirror", "CAGE",
                         None, ["STUN", "SLOW_FIELD"]),
    "reed_bow": ("art_reedpipe_trill", "葦笛の連矢", "Reedpipe Trill", "reedpipe", None, None, ["STAMINA"]),
    "dragonfly_bow": ("art_dragonfly_return", "蜻蛉返しの矢", "Dragonfly Return", "dragonfly", "SPIRAL", None,
                      ["BACKSTEP"]),
    "spirit_pearl_bow": ("art_spiritpearl_rain", "精霊真珠の光雨", "Spiritpearl Rain", "spiritpearl", None, None,
                         ["HEAL"]),
    # ---------------------------------------------------------------- 10 風の港
    "deckhand_cutlass": ("art_deckrun_slash", "甲板走りの切り込み", "Deckrun Slash", "deck", None, None, ["STAMINA"]),
    "doubloon_rapier": ("art_doubloon_flick", "金貨弾きの突き", "Doubloon Flick", "coin", "SCATTER", None, ["CRIT_UP"]),
    "barbas_tidecleaver": ("art_barbas_tidesplitter", "バルバスの潮断ち", "Barbas's Tidesplitter", "tide", "WAVE",
                           "SWORD_CLEAVE", ["ZONE"]),
    "anchor_greatsword": ("art_anchor_drop", "錨落とし", "Anchor Drop", "anchor", "CHAIN", "GREAT_GUILLOTINE",
                          ["PULL"]),
    "stormsail_greatsword": ("art_stormsail_turn", "嵐帆の大旋回", "Stormsail Turn", "stormsail", "VORTEX",
                             "GREAT_SPIN", ["SPEED"]),
    "ankaras_great_pincer": ("art_ankaras_crushing_clamp", "アンカラの挟み潰し", "Ankara's Crushing Clamp", "pincer",
                             "CROSS", "GREAT_REND", ["PULL", "STUN"]),
    "saltspray_daggers": ("art_saltspray_flurry", "潮飛沫の連斬", "Saltspray Flurry", "saltspray", None, None,
                          ["SLOW_FIELD"]),
    "inkstorm_blades": ("art_inkstorm_whirl", "墨嵐の渦", "Inkstorm Whirl", "ink", "VORTEX", None, ["ZONE"]),
    "dock_brawlers_knuckleknives": ("art_brawlers_knuckle_barrage", "喧嘩屋の拳打ち", "Brawler's Knuckle Barrage",
                                    "brawl", None, "TWIN_MAUL", ["STUN", "ATTACK_UP"]),
    "boarding_pike": ("art_boarding_charge", "斬り込み突撃", "Boarding Charge", "boarding", None, "SPEAR_CHARGE",
                      ["KNOCKBACK"]),
    "windlass_harpoon": ("art_windlass_reel", "巻き上げ銛の引き戻し", "Windlass Reel", "windlass", "CHAIN",
                         "SPEAR_JAVELIN", ["PULL"]),
    "maelstrom_trident": ("art_maelstrom_trident", "大渦の三叉", "Maelstrom Trident", "maelstrom", "VORTEX",
                          "SPEAR_IMPALE", ["ZONE", "PULL"]),
    "buoy_lantern_staff": ("art_buoylight", "浮標灯の灯り", "Buoylight", "buoy", "PILLAR", None, ["SHIELD"]),
    "compass_rose_staff": ("art_fourfold_winds", "羅針の四方風", "Fourfold Winds", "compass", "CROSS", None,
                           ["SPEED"]),
    "kraken_ink_rod": ("art_krakenink_tentacles", "海魔墨の触腕", "Krakenink Tentacles", "krakenink", "CHAIN",
                       "STAFF_BEAM", ["STUN", "PULL"]),
    "gunwale_bow": ("art_broadside_volley", "舷側の一斉射", "Broadside Volley", "broadside", None, None,
                    ["STAMINA"]),
    "seagull_bow": ("art_gullwheel_arrows", "海鳥の旋回矢", "Gullwheel Arrows", "gull", "SPIRAL", None, ["EVASION"]),
    "trade_wind_longbow": ("art_tradewind_longshot", "貿易風の遠矢", "Tradewind Longshot", "tradewind", None,
                           "BOW_PIERCE", ["SPEED"]),
}

TIERS = ["iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"]
# (tier, type): (art id, 日本語名, English name, form, extras)
T = {
    ("iron", "longsword"): ("art_iron_clean_cut", "鉄刃一閃", "Iron Clean Cut", "SWORD_COMBO", []),
    ("iron", "greatsword"): ("art_iron_mass_strike", "鉄塊打ち", "Iron Mass Strike", "GREAT_SLAM", []),
    ("iron", "twin_blades"): ("art_iron_crosscut", "鉄双刃の交差", "Iron Crosscut", "TWIN_CROSS", []),
    ("iron", "spear"): ("art_iron_triple_thrust", "鉄槍の三段突き", "Iron Triple Thrust", "SPEAR_THRUSTS", []),
    ("iron", "staff"): ("art_iron_rod_bolt", "鉄杖の魔弾", "Iron Rod Bolt", "STAFF_BURST", []),
    ("iron", "bow"): ("art_iron_arrowhead_fan", "鉄鏃の扇射ち", "Iron Arrowhead Fan", "BOW_FAN", []),
    ("steel", "longsword"): ("art_honed_flurry", "鋼の連閃", "Honed Flurry", "SWORD_DASH", []),
    ("steel", "greatsword"): ("art_steel_whirl", "鋼の旋回斬", "Steel Whirl", "GREAT_SPIN", []),
    ("steel", "twin_blades"): ("art_steel_blade_storm", "鋼の乱れ刃", "Steel Blade Storm", "TWIN_FLURRY", []),
    ("steel", "spear"): ("art_steel_point_pierce", "鋼穂の貫き", "Steel Point Pierce", "SPEAR_PIERCE", []),
    ("steel", "staff"): ("art_steel_triple_bolt", "鋼杖の三連弾", "Steel Triple Bolt", "STAFF_ORBS", []),
    ("steel", "bow"): ("art_steel_rapid_fire", "鋼鏃の速射", "Steel Rapid Fire", "BOW_VOLLEY", []),
    ("sandstone", "longsword"): ("art_sandstone_dust_ring", "砂岩の砂塵輪", "Sandstone Dust Ring", "SWORD_BURST",
                                 ["SLOW_FIELD"]),
    ("sandstone", "greatsword"): ("art_quicksand_upheaval", "流砂起こし", "Quicksand Upheaval", "GREAT_ERUPTION",
                                  ["SLOW_FIELD"]),
    ("sandstone", "twin_blades"): ("art_grit_throw", "砂礫の投げ刃", "Grit Throw", "TWIN_FAN", ["SLOW_FIELD"]),
    ("sandstone", "spear"): ("art_sandpillar_thrust", "砂柱突き", "Sandpillar Thrust", "SPEAR_IMPALE",
                             ["SLOW_FIELD"]),
    ("sandstone", "staff"): ("art_sandstorm_fall", "砂嵐落とし", "Sandstorm Fall", "STAFF_METEOR", ["SLOW_FIELD"]),
    ("sandstone", "bow"): ("art_sandrain_arrows", "砂降らしの矢", "Sandrain Arrows", "BOW_RAIN", ["SLOW_FIELD"]),
    ("frost", "longsword"): ("art_icecrystal_cleave", "氷晶の斬り下ろし", "Icecrystal Cleave", "SWORD_CLEAVE", []),
    ("frost", "greatsword"): ("art_icicle_forest", "氷柱林立", "Icicle Forest", "GREAT_SLAM", ["ZONE"]),
    ("frost", "twin_blades"): ("art_icechain_strikes", "氷鎖の連撃", "Icechain Strikes", "TWIN_CHAIN", []),
    ("frost", "spear"): ("art_icelance_line", "氷槍の一直線", "Icelance Line", "SPEAR_LANCE", []),
    ("frost", "staff"): ("art_icecrystal_spear", "氷晶の魔槍", "Icecrystal Spear", "STAFF_LANCE", []),
    ("frost", "bow"): ("art_freezing_arrow", "凍て矢", "Freezing Arrow", "BOW_SNIPE", []),
    ("flame", "longsword"): ("art_flamewave_slash", "炎の波斬り", "Flamewave Slash", "SWORD_WAVE", []),
    ("flame", "greatsword"): ("art_flame_swirl_sweep", "炎渦の大薙ぎ", "Flame Swirl Sweep", "GREAT_SWEEP", []),
    ("flame", "twin_blades"): ("art_firewheel_twinstrike", "火輪の双撃", "Firewheel Twinstrike", "TWIN_MAUL", []),
    ("flame", "spear"): ("art_firespear_throw", "火槍投げ", "Firespear Throw", "SPEAR_JAVELIN", []),
    ("flame", "staff"): ("art_flameburst_bolt", "炎爆の魔弾", "Flameburst Bolt", "STAFF_BURST", ["ZONE"]),
    ("flame", "bow"): ("art_blazing_arrow", "火炎矢", "Blazing Arrow", "BOW_IMPACT", []),
    ("celestial", "longsword"): ("art_skylight_triple", "天光の三連斬", "Skylight Triple", "SWORD_COMBO", []),
    ("celestial", "greatsword"): ("art_starfall_strike", "星落とし", "Starfall Strike", "GREAT_GUILLOTINE", ["MARK"]),
    ("celestial", "twin_blades"): ("art_stardust_throw", "星屑の投げ刃", "Stardust Throw", "TWIN_FAN", []),
    ("celestial", "spear"): ("art_skylight_lance", "天光槍", "Skylight Lance", "SPEAR_PIERCE", []),
    ("celestial", "staff"): ("art_meteor_call", "流星招き", "Meteor Call", "STAFF_METEOR", []),
    ("celestial", "bow"): ("art_star_shower", "星の雨", "Star Shower", "BOW_RAIN", []),
    ("abyss", "longsword"): ("art_abyss_drain_cut", "深淵の吸い斬り", "Abyss Drain Cut", "SWORD_PRECISE", ["SIPHON"]),
    ("abyss", "greatsword"): ("art_abyss_rift", "深淵の裂け目", "Abyss Rift", "GREAT_ERUPTION", ["ZONE", "SIPHON"]),
    ("abyss", "twin_blades"): ("art_void_shade_stab", "虚影の刺突", "Void Shade Stab", "TWIN_ASSASSINATE", ["VANISH"]),
    ("abyss", "spear"): ("art_void_pull_lance", "虚無の引き槍", "Void Pull Lance", "SPEAR_LANCE", ["PULL"]),
    ("abyss", "staff"): ("art_abyss_ray", "深淵の光線", "Abyss Ray", "STAFF_BEAM", ["SIPHON"]),
    ("abyss", "bow"): ("art_void_arrow", "虚無矢", "Void Arrow", "BOW_PIERCE", ["SIPHON"]),
}
# material: (affliction, ticks, power, theme)
MATERIAL = {
    "iron": (None, 0, 0.0, "STEEL"), "steel": ("BLEED", 60, 0.15, "STEEL"), "sandstone": ("SLOW", 40, 1.0, "EARTH"),
    "frost": ("FREEZE", 40, 0.0, "FROST"), "flame": ("BURN", 60, 0.0, "FIRE"), "celestial": ("BLIND", 40, 0.0, "HOLY"),
    "abyss": ("WEAKEN", 60, 0.15, "SHADOW"),
}
ELEMENT_AFFLICTION = {"NONE": None, "FIRE": "BURN", "FROST": "SLOW", "THUNDER": "STUN", "VENOM": "POISON",
                      "HOLY": "BLIND", "SHADOW": "WEAKEN", "BLOOD": "BLEED", "EARTH": "VULNERABLE"}
THEME_OF_ELEMENT = {"NONE": "STEEL", "FIRE": "FIRE", "FROST": "FROST", "THUNDER": "THUNDER", "VENOM": "VENOM",
                    "HOLY": "HOLY", "SHADOW": "SHADOW", "BLOOD": "BLOOD", "EARTH": "EARTH"}

# ------------------------------------------------------------------------------------------------ descriptions
FORM_TEXT = {
    "SWORD_COMBO": ("流れるような二連斬りから、回転斬りで締める。", "Two flowing cuts, finished with a spinning slash."),
    "SWORD_DASH": ("前へ踏み込みながら、素早く四度斬りつける。", "Dashes in and cuts four times in quick succession."),
    "SWORD_CLEAVE": ("力を溜め、前方を大きく斬り下ろす。", "Gathers strength and cleaves down in a wide arc."),
    "SWORD_PRECISE": ("狙い澄ました一突きで、前方を鋭く貫く。", "A carefully aimed thrust that pierces straight ahead."),
    "SWORD_WAVE": ("斬撃を飛ばし、前方の敵をまとめて切り裂く。", "Sends the slash flying through every foe ahead."),
    "SWORD_BURST": ("自分の周りを一気に薙ぎ払う。", "Sweeps everything around you in one burst."),
    "GREAT_REND": ("横薙ぎから、前方へ叩きつける二段斬り。", "A sideways cut, then a crushing blow ahead."),
    "GREAT_SPIN": ("大剣ごと回転し、周囲を二度斬り払う。", "Spins with the greatsword, cutting all around twice."),
    "GREAT_SLAM": ("地面を叩き割り、周囲の敵を打ち据える。", "Smashes the ground, striking every foe around you."),
    "GREAT_GUILLOTINE": ("頭上から前方へ、渾身の一撃を振り下ろす。", "Brings an all-out blow down from overhead."),
    "GREAT_SWEEP": ("長い間合いで、前方を大きく薙ぎ払う。", "A long sweep across a wide arc ahead."),
    "GREAT_ERUPTION": ("斬りつけてから、前方の地面を噴き上げる。", "Cuts, then makes the ground ahead burst upward."),
    "TWIN_FLURRY": ("飛び込みながら、双刃で六連撃を浴びせる。", "Leaps in and rains six blows with both blades."),
    "TWIN_ASSASSINATE": ("一瞬で間合いを詰め、急所を深く刺す。", "Closes in at once and stabs a weak point deep."),
    "TWIN_CROSS": ("十字に斬り結び、回転斬りで締める。", "Crossing cuts, finished with a spinning slash."),
    "TWIN_FAN": ("刃を扇状に投げ放つ。", "Throws blades out in a fan."),
    "TWIN_MAUL": ("双刃を叩きつけ、周囲を二度打つ。", "Slams both blades down, striking all around twice."),
    "TWIN_CHAIN": ("刃が伸びるように、前方を二度貫く。", "Lashes ahead twice as if the blades stretched out."),
    "SPEAR_LANCE": ("長い間合いの一突きで、一直線に貫く。", "One long-reaching thrust through everything in a line."),
    "SPEAR_THRUSTS": ("三段突きで、前方を続けて貫く。", "Three thrusts in a row through the line ahead."),
    "SPEAR_CHARGE": ("勢いよく突進し、そのまま貫く。", "Charges forward and runs foes through."),
    "SPEAR_IMPALE": ("突いてから、前方の地面ごと叩き伏せる。", "Thrusts, then slams the ground ahead."),
    "SPEAR_PIERCE": ("細く鋭い突きで、遠くまで貫く。", "A thin, sharp thrust that reaches far."),
    "SPEAR_JAVELIN": ("穂先の力を投げ放ち、当たった場所で炸裂させる。", "Hurls the spear's power, bursting where it lands."),
    "STAFF_BURST": ("魔力の塊を放ち、当たった場所で炸裂させる。", "Fires a mass of magic that bursts where it lands."),
    "STAFF_ORBS": ("魔弾を三つ続けて放ち、最後の一発が弾ける。", "Three bolts in a row; the last one bursts."),
    "STAFF_LANCE": ("速く鋭い魔弾が、敵を何体も貫く。", "A fast, sharp bolt that pierces several foes."),
    "STAFF_VOLLEY": ("魔弾を扇状に五つ放つ。", "Five bolts fly out in a fan."),
    "STAFF_METEOR": ("狙った場所へ頭上から魔弾を落とし、炸裂させる。", "Drops a bolt from above onto the target, where it bursts."),
    "STAFF_BEAM": ("杖の先から、前方へ一直線の光を放つ。", "A straight ray from the staff's tip through everything ahead."),
    "BOW_VOLLEY": ("三本の矢を立て続けに射る。", "Three arrows in quick succession."),
    "BOW_SNIPE": ("引き絞った一矢で、遠くの敵を何体も貫く。", "One fully drawn arrow that pierces far through several foes."),
    "BOW_FAN": ("五本の矢を扇状に放つ。", "Five arrows in a fan."),
    "BOW_PIERCE": ("どこまでも突き抜ける矢を放つ。", "An arrow that keeps going through everything in its path."),
    "BOW_RAIN": ("狙った場所へ矢の雨を降らせる。", "Arrows rain down on the target."),
    "BOW_IMPACT": ("当たった場所で炸裂する重い矢を放つ。", "A heavy arrow that bursts where it hits."),
}
AFFLICTION_TEXT = {
    "BURN": ("敵を燃やす", "sets foes on fire"), "SLOW": ("敵の足を鈍らせる", "slows foes"),
    "FREEZE": ("敵を凍りつかせる", "freezes foes"), "STUN": ("敵をしびれさせる", "stuns foes"),
    "POISON": ("敵を毒に侵す", "poisons foes"), "BLIND": ("敵の目をくらませる", "blinds foes"),
    "WEAKEN": ("敵の力を奪う", "weakens foes"), "BLEED": ("敵を出血させる", "makes foes bleed"),
    "VULNERABLE": ("敵の守りを崩す", "leaves foes vulnerable"),
}
ELEMENT_WORD = {"FIRE": ("炎", "fire"), "FROST": ("冷気", "frost"), "THUNDER": ("雷", "lightning"),
                "VENOM": ("毒", "venom"), "HOLY": ("聖なる光", "holy light"), "SHADOW": ("闇", "darkness"),
                "BLOOD": ("血", "blood"), "EARTH": ("大地の力", "earth"), "STEEL": ("衝撃", "force")}
IMBUE_WORD = {"FIRE": ("炎", "fire"), "FROST": ("冷気", "frost"), "VENOM": ("毒", "venom"),
              "THUNDER": ("雷", "lightning")}
EXTRA_TEXT = {
    "ZONE": ("当たった場所に、しばらく{e}の場が残る。", "A lingering field of {e} remains where it lands."),
    "SLOW_FIELD": ("敵の足を鈍らせる場が残る。", "Leaves a field that slows foes."),
    "MARK": ("先に印を刻み、少し遅れてそこへ追撃が落ちる。", "Marks the spot first; a second strike falls there a moment later."),
    "FIELD_HEAL": ("足元に仲間を癒やす場を作る。", "Creates a field underfoot that heals allies."),
    "HEAL": ("自分と周りの仲間の傷を癒やす。", "Heals you and allies nearby."),
    "SHIELD": ("自分に守りの盾を張る。", "Shields you."),
    "ATTACK_UP": ("自分の攻撃力が上がる。", "Raises your attack."),
    "CRIT_UP": ("自分の会心率が上がる。", "Raises your critical rate."),
    "SPEED": ("その後しばらく足が速くなる。", "You move faster for a while afterwards."),
    "EVASION": ("技の間、敵の攻撃をかわしやすくなる。", "You dodge more easily during the art."),
    "DEFENSE_UP": ("自分の防御力が上がる。", "Raises your defense."),
    "PARTY_ATTACK": ("周りの仲間の攻撃力を上げる。", "Raises the attack of allies nearby."),
    "REGEN": ("その後しばらく体力が回復し続ける。", "You regenerate health for a while afterwards."),
    "LIFESTEAL": ("しばらく攻撃で体力を吸い取る。", "For a while your hits drain health."),
    "THORNS": ("しばらく受けたダメージの一部を跳ね返す。", "For a while you reflect part of the damage you take."),
    "IMBUE": ("その後しばらく武器に{i}が宿る。", "Your weapon carries {i} for a while afterwards."),
    "DASH": ("前へ踏み込んでから放つ。", "Steps in before striking."),
    "LEAP": ("跳び上がって間合いを詰める。", "Leaps in to close the distance."),
    "BACKSTEP": ("放った後に後ろへ跳び退く。", "Springs back afterwards."),
    "PULL": ("敵を引き寄せる。", "Pulls foes in."),
    "LAUNCH": ("敵を宙へ打ち上げる。", "Launches foes into the air."),
    "KNOCKBACK": ("敵を大きく吹き飛ばす。", "Knocks foes far back."),
    "EXECUTE": ("弱った敵ほど深く傷つける。", "Wounds the weakened deeper."),
    "SIPHON": ("与えたダメージの一部を体力として吸い取る。", "Drains part of the damage as health."),
    "TAUNT": ("周りの敵の注意を引きつける。", "Draws the attention of foes nearby."),
    "CLEANSE": ("周りの仲間の状態異常を払う。", "Cleanses allies nearby."),
    "STAMINA": ("スタミナを取り戻す。", "Restores stamina."),
    "VANISH": ("姿を消し、敵に見失わせる。", "You vanish and foes lose track of you."),
    "STUN": ("当たった敵を一瞬しびれさせる。", "Briefly stuns the foes it hits."),
    "BLIND": ("当たった敵の目をくらませる。", "Blinds the foes it hits."),
}


def describe(form, affliction, theme, extras):
    ja, en = [FORM_TEXT[form][0]], [FORM_TEXT[form][1]]
    if affliction:
        a_ja, a_en = AFFLICTION_TEXT[affliction]
        ja.append(f"とどめの一撃は{a_ja}。")
        en.append(f"The final hit {a_en}.")
    for extra in extras:
        t_ja, t_en = EXTRA_TEXT[extra]
        e_ja, e_en = ELEMENT_WORD.get(theme, ("衝撃", "force"))
        i_ja, i_en = IMBUE_WORD.get(theme, ("炎", "fire"))
        ja.append(t_ja.format(e=e_ja, i=i_ja))
        en.append(t_en.format(e=e_en, i=i_en))
    return "".join(ja), " ".join(en)


def rows():
    """(key, art id, ja, en, form, palette, primary, extras, affliction, theme) for every art."""
    weapons, _ = armory_data.load()
    out = []
    for w in weapons:
        if "BOSS" in w.sources:
            continue
        art, ja, en, pal, primary, form, extras = F[w.id]
        t = TYPES[w.type]
        form = form or f"{t}_{FEEL_FORM[t][w.feel]}"
        out.append((f"design:{w.id}", art, ja, en, form, pal, primary or FORM_SHAPE[form], extras,
                    ELEMENT_AFFLICTION[w.element], THEME_OF_ELEMENT[w.element], w))
    # the relics (金枠): their weapons' arts, in the relic's own colours (tools/art/relics_data.py)
    import relics_data
    for r in relics_data.load():
        ja, en, extras, primary, accent, windup, particle, accent_particle, rgb, accent_rgb = r.art
        pal = f"relic_{r.key}"
        PAL[pal] = (particle, accent_particle, rgb, accent_rgb, accent, windup)
        out.append((f"relic:{r.weapon_id}", r.art_id, ja, en, r.form, pal, primary, list(extras),
                    ELEMENT_AFFLICTION[r.element], THEME_OF_ELEMENT[r.element], r))
    for tier in TIERS:
        for index, wtype in enumerate(TYPES):
            art, ja, en, form, extras = T[(tier, wtype)]
            aff = MATERIAL[tier][0]
            # every weapon type of a material in its own shade of the material's colour
            pal = f"{tier}_{wtype}"
            base = PAL[tier]
            PAL[pal] = (base[0], base[1], shade(base[2], index), base[3], TIER_ACCENT[index], base[5])
            out.append((f"tier:{tier}:{JAVA_TYPES[wtype]}", art, ja, en, form, pal, FORM_SHAPE[form], extras, aff,
                        MATERIAL[tier][3], None))
    return out


TIER_ACCENT = ["CRESCENT", "SPIKES", "SCATTER", "HELIX", "SIGIL", "FAN"]


def shade(rgb, index):
    """The colour lightened (even index) or darkened (odd index) a little per weapon type."""
    factor = 1.0 + (0.06 * (index // 2 + 1)) * (1 if index % 2 == 0 else -1)
    channels = [(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255]
    return sum(max(0, min(255, round(c * factor))) << (16 - 8 * i) for i, c in enumerate(channels))


def check(all_rows):
    problems = []
    ids, sigs, names_ja, names_en = {}, {}, {}, {}
    for key, art, ja, en, form, pal, primary, extras, aff, theme, w in all_rows:
        for table, value in ((ids, art), (names_ja, ja), (names_en, en)):
            if value in table:
                problems.append(f"duplicate {value}: {key} and {table[value]}")
            table[value] = key
        p = PAL[pal]
        sig = (primary, p[4], p[0], p[1], p[2])
        if sig in sigs:
            problems.append(f"{key} looks like {sigs[sig]}: {sig}")
        sigs[sig] = key
        if not art.startswith("art_"):
            problems.append(f"{key}: bad id {art}")
        # magic and bow attacks push nothing in the game (D-RANGEDKB): a knockback extra there would promise nothing
        if "KNOCKBACK" in extras and _wields(key, w) in ("bow", "staff"):
            problems.append(f"{key}: a bow or staff art cannot knock back (D-RANGEDKB)")
    return problems


def _wields(key, w):
    """The weapon type (bow, staff, ...) of an art row: named weapons, relics and tier weapons."""
    if key.startswith("tier:"):
        return {"BOW": "bow", "STAFF": "staff"}.get(key.split(":")[2], "melee")
    kind = getattr(w, "type", None) or getattr(w, "wtype", "")
    return {"BOW": "bow", "STAFF": "staff"}.get(str(kind).upper(), "melee")


def java(all_rows):
    lines = []
    for key, art, ja, en, form, pal, primary, extras, aff, theme, w in all_rows:
        p = PAL[pal]
        material = ("", "", 0, 0.0)
        if w is None:
            tier = key.split(":")[1]
            aff_name, ticks, power, theme_name = MATERIAL[tier]
            material = (theme_name, aff_name or "", ticks, power)
        lines.append(f'            row("{key}", "{art}", "{form}", "{",".join(extras)}", "{pal}", "{primary}", '
                     f'"{p[4]}", "{p[0]}", "{p[1]}", 0x{p[2]:06X}, 0x{p[3]:06X}, "{p[5]}", "{material[0]}", '
                     f'"{material[1]}", {material[2]}, {material[3]})')
    body = ",\n".join(lines)
    return f'''package com.eternalrealm.item;

import java.util.List;

/**
 * GENERATED by tools/art/weapon_arts.py (do not edit): the weapon art of every named field weapon
 * ({{@code design:<id>}}), every relic weapon ({{@code relic:<id>}}) and every tier weapon
 * ({{@code tier:<tier>:<weapon type>}}): the art's id, its attack form, its extras and its look. {{@link GeneratedArts}}
 * builds the arts from these rows ({{@link ArtForge}}).
 */
final class ArtTable {{
    /**
     * One row; {{@code extras}} is a comma separated list of {{@link ArtForge.Extra}} names. Tier rows name their
     * material's theme and affliction; named and relic weapons take them from their element (empty here).
     */
    record Row(String key, String id, String form, String extras, String family, String primary, String accent,
               String particle, String accentParticle, int rgb, int accentRgb, String windup, String theme,
               String affliction, int afflictionTicks, double afflictionPower) {{
    }}

    static final List<Row> ROWS = List.of(
{body});

    private ArtTable() {{
    }}

    private static Row row(String key, String id, String form, String extras, String family, String primary,
                           String accent, String particle, String accentParticle, int rgb, int accentRgb,
                           String windup, String theme, String affliction, int afflictionTicks,
                           double afflictionPower) {{
        return new Row(key, id, form, extras, family, primary, accent, particle, accentParticle, rgb, accentRgb,
                windup, theme, affliction, afflictionTicks, afflictionPower);
    }}
}}
'''


def i18n(all_rows):
    out = {}
    for key, art, ja, en, form, pal, primary, extras, aff, theme, w in all_rows:
        d_ja, d_en = describe(form, aff, theme, extras)
        out[f"action.eternalrealm.{art}"] = {"ja": ja, "en": en}
        out[f"art.eternalrealm.{art}.desc"] = {"ja": d_ja, "en": d_en}
    return out


FEEL_JA = {"BALANCED": "万能", "SWIFT": "速", "HEAVY": "重", "KEEN": "鋭", "LONG": "長", "ELEMENTAL": "属性"}
TYPE_JA = {"longsword": "片手剣", "greatsword": "両手剣", "twin_blades": "双剣", "spear": "槍", "staff": "杖", "bow": "弓"}


def doc_table(all_rows):
    head = ["<!-- ART TABLE BEGIN (tools/art/weapon_arts.py) -->", "",
            "| 武器 | 武技 | 形 | 追加効果 | 見た目 |", "|---|---|---|---|---|"]
    body = []
    for key, art, ja, en, form, pal, primary, extras, aff, theme, w in all_rows:
        weapon = key.split(":", 1)[1]
        body.append(f"| `{weapon}` | {ja}（{en}） | `{form.lower()}` | {', '.join(e.lower() for e in extras) or '—'}"
                    f" | {pal} / {primary.lower()} |")
    return "\n".join(head + body + ["", "<!-- ART TABLE END -->"])


def main():
    all_rows = rows()
    problems = check(all_rows)
    if problems:
        for problem in problems:
            print("ERROR", problem)
        return 1
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java(all_rows))
    data = i18n(all_rows)
    with open(I18N, "w", encoding="utf-8", newline="\n") as f:
        f.write("{\n" + ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}"
                                   for k, v in data.items()) + "\n}\n")
    with open(DOC, encoding="utf-8") as f:
        doc = f.read()
    table = doc_table(all_rows)
    begin, end = "<!-- ART TABLE BEGIN", "<!-- ART TABLE END -->"
    if begin in doc:
        doc = doc[:doc.index(begin)] + table + doc[doc.index(end) + len(end):]
    else:
        doc = doc.rstrip("\n") + "\n\n## 武技の一覧（Zキー）\n\n" + table + "\n"
    with open(DOC, "w", encoding="utf-8", newline="\n") as f:
        f.write(doc)
    import weapon_art_icons
    icons = weapon_art_icons.write([(r[1], r[6], PAL[r[5]][2], PAL[r[5]][3]) for r in all_rows],
                                   os.path.join(ROOT, "src/main/resources/assets/eternalrealm"))
    print(f"icons: {icons}")
    print(f"weapon arts: {len(all_rows)} rows ({sum(1 for r in all_rows if r[0].startswith('design'))} named, "
          f"{sum(1 for r in all_rows if r[0].startswith('relic'))} relic, "
          f"{sum(1 for r in all_rows if r[0].startswith('tier'))} tier), {len(data)} texts")
    return 0


if __name__ == "__main__":
    sys.exit(main())
