"""The relics (金枠の遺宝): one source per dungeon boss, calamity, lord (ヌシ) and wanderer, each with its own weapon,
armor set, accessory and charm in the source's motif. Single source of truth for tools/art/relics.py (the Java table
RelicTable.java, the texts, the art) and tools/art/weapon_arts.py (the relic weapons' arts).

Each entry:
    key        stable source key (b1..b8, final, c_<calamity>, l_<lord>, w_<wanderer>); ids derive from it
    kind       BOSS / CALAMITY / LORD / WANDERER, target = the boss / calamity / lord / wanderer id
    field      1..10, level = the source's level (relics ask for level + 5)
    motif      (ja, en) the source's short name used in the item names
    weapon     (type, form, element, ja, en)   the feel follows from the art's form
    style      armor style HEAVY / LIGHT / ROBE
    acc        (kind, main stat, second stat, focus)   accessory: RING AMULET EARRING BRACELET
    charm      (kind, focus)                         charm: TALISMAN BELL STONE POUCH
    lore       (ja, en)
    art        (ja, en, extras, primary shape, accent shape, wind-up, particle, accent particle, rgb, accent rgb)
    sprite     weapon sprite spec (named_weapon_specs vocabulary)
    look       armor (pattern, A, B, C, E, G, emblem, extra theme kwargs) as ramp names
    trinket    (metal, gem, cloth) ramp names of the accessory and charm icons
"""
from __future__ import annotations

from dataclasses import dataclass, field as dfield

TYPES = {"SWORD": "longsword", "GREAT": "greatsword", "TWIN": "twin_blades", "SPEAR": "spear", "STAFF": "staff",
         "BOW": "bow"}
JAVA_TYPES = {"SWORD": "ONE_HANDED_SWORD", "GREAT": "TWO_HANDED_SWORD", "TWIN": "DUAL_BLADES", "SPEAR": "SPEAR",
              "STAFF": "STAFF", "BOW": "BOW"}
FORM_FEEL = {
    "SWORD": {"COMBO": "BALANCED", "DASH": "SWIFT", "CLEAVE": "HEAVY", "PRECISE": "KEEN", "WAVE": "LONG",
              "BURST": "ELEMENTAL"},
    "GREAT": {"REND": "BALANCED", "SPIN": "SWIFT", "SLAM": "HEAVY", "GUILLOTINE": "KEEN", "SWEEP": "LONG",
              "ERUPTION": "ELEMENTAL"},
    "TWIN": {"FLURRY": "SWIFT", "ASSASSINATE": "KEEN", "CROSS": "BALANCED", "FAN": "ELEMENTAL", "MAUL": "HEAVY",
             "CHAIN": "LONG"},
    "SPEAR": {"LANCE": "LONG", "THRUSTS": "BALANCED", "CHARGE": "SWIFT", "IMPALE": "HEAVY", "PIERCE": "KEEN",
              "JAVELIN": "ELEMENTAL"},
    "STAFF": {"BURST": "ELEMENTAL", "ORBS": "BALANCED", "LANCE": "KEEN", "VOLLEY": "SWIFT", "METEOR": "HEAVY",
              "BEAM": "LONG"},
    "BOW": {"VOLLEY": "SWIFT", "SNIPE": "KEEN", "FAN": "BALANCED", "PIERCE": "LONG", "RAIN": "ELEMENTAL",
            "IMPACT": "HEAVY"},
}
STYLE_PIECES = {
    # slot: (ja noun, en noun)
    "HEAVY": [("兜", "Helm"), ("鎧", "Cuirass"), ("脚甲", "Greaves"), ("鉄靴", "Sabatons")],
    "LIGHT": [("頭巾", "Hood"), ("胴衣", "Jerkin"), ("脚衣", "Leggings"), ("長靴", "Boots")],
    "ROBE": [("冠", "Circlet"), ("法衣", "Robe"), ("裳", "Skirt"), ("履", "Sandals")],
}
STYLE_SET = {"HEAVY": ("遺鎧", "Relic Plate"), "LIGHT": ("遺装", "Relic Garb"), "ROBE": ("遺衣", "Relic Vestments")}
TRINKET_NOUN = {"RING": ("指輪", "Ring"), "AMULET": ("首飾り", "Amulet"), "EARRING": ("耳飾り", "Earring"),
                "BRACELET": ("腕輪", "Bracelet"), "TALISMAN": ("護符", "Talisman"), "BELL": ("守り鈴", "Ward Bell"),
                "STONE": ("守り石", "Ward Stone"), "POUCH": ("お守り袋", "Charm Pouch")}
SLOTS = ["head", "chest", "legs", "feet"]


@dataclass(frozen=True)
class Relic:
    index: int
    key: str
    kind: str
    target: str
    field: int
    level: int
    motif: tuple
    weapon: tuple
    style: str
    acc: tuple
    charm: tuple
    lore: tuple
    art: tuple
    sprite: dict
    look: tuple
    trinket: tuple
    extra: dict = dfield(default_factory=dict)

    # ---------------------------------------------------------------- ids and numbers (RelicTable / Relics.java)
    @property
    def wtype(self) -> str:
        return self.weapon[0]

    @property
    def form(self) -> str:
        return f"{self.wtype}_{self.weapon[1]}"

    @property
    def feel(self) -> str:
        return FORM_FEEL[self.wtype][self.weapon[1]]

    @property
    def element(self) -> str:
        return self.weapon[2]

    @property
    def relic_level(self) -> int:
        return min(100, self.level + 5)

    @property
    def weapon_id(self) -> str:
        return f"relic_{self.key}_weapon"

    @property
    def set_id(self) -> str:
        return f"relic_{self.key}"

    @property
    def accessory_id(self) -> str:
        return f"relic_{self.key}_{self.acc[0].lower()}"

    @property
    def charm_id(self) -> str:
        return f"relic_{self.key}_{self.charm[0].lower()}"

    @property
    def art_id(self) -> str:
        return f"art_relic_{self.key}"

    @property
    def weapon_model(self) -> int:
        return 3500 + self.index

    @property
    def set_model(self) -> int:
        return 4500 + self.index * 4

    @property
    def accessory_model(self) -> int:
        return 5000 + self.index * 2

    @property
    def charm_model(self) -> int:
        return 5001 + self.index * 2

    @property
    def tier(self) -> int:
        best = 1
        for i, lv in enumerate([1, 8, 15, 22, 30, 37, 44], start=1):
            if lv <= self.relic_level:
                best = i
        return best

    def piece_ids(self):
        return [f"{self.set_id}_{slot}" for slot in SLOTS]

    # ---------------------------------------------------------------- names
    def names(self):
        """translation key -> (ja, en) of the relic's items, set and art."""
        mja, men = self.motif
        out = {}
        out[f"design.eternalrealm.{self.weapon_id}"] = (self.weapon[3], self.weapon[4])
        sja, sen = STYLE_SET[self.style]
        out[f"armor_set.eternalrealm.{self.set_id}"] = (f"{mja}の{sja}", f"{men} {sen}")
        for slot, (pja, pen) in zip(SLOTS, STYLE_PIECES[self.style]):
            out[f"design.eternalrealm.{self.set_id}_{slot}"] = (f"{mja}の{pja}", f"{men} {pen}")
        aja, aen = TRINKET_NOUN[self.acc[0]]
        out[f"design.eternalrealm.{self.accessory_id}"] = (f"{mja}の{aja}", f"{men} {aen}")
        cja, cen = TRINKET_NOUN[self.charm[0]]
        out[f"design.eternalrealm.{self.charm_id}"] = (f"{mja}の{cja}", f"{men} {cen}")
        for key in (f"design.eternalrealm.{self.weapon_id}.lore", f"armor_set.eternalrealm.{self.set_id}.lore",
                    f"design.eternalrealm.{self.accessory_id}.lore", f"design.eternalrealm.{self.charm_id}.lore"):
            out[key] = self.lore
        return out


def _r(key, kind, target, field, level, motif, weapon, style, acc, charm, lore, art, sprite, look, trinket):
    return (key, kind, target, field, level, motif, weapon, style, acc, charm, lore, art, sprite, look, trinket)


SW = lambda blade, metal, **kw: dict(kind="sword", blade=blade, metal=metal, **kw)  # noqa: E731
GS = lambda blade, metal, **kw: dict(kind="great", blade=blade, metal=metal, **kw)  # noqa: E731
TW = lambda blade, metal, **kw: dict(kind="twin", blade=blade, metal=metal, **kw)  # noqa: E731
SPR = lambda head, metal, **kw: dict(kind="spear", head=head, metal=metal, **kw)  # noqa: E731
STF = lambda head, **kw: dict(kind="staff", head=head, **kw)  # noqa: E731
BW = lambda metal, **kw: dict(kind="bow", metal=metal, **kw)  # noqa: E731

ROWS = [
    # ================================================================ dungeon bosses
    _r("b1", "BOSS", "b1", 1, 6, ("盗賊王", "Bandit King"),
       ("SWORD", "PRECISE", "SHADOW", "簒奪者の細剣", "Usurper's Rapier"), "LIGHT",
       ("RING", "AGI", "LUK", "CRIT"), ("POUCH", "EVASION"),
       ("地下都市の王が奪った宝の中で、最後まで手放さなかった品々。",
        "Of all the undercity king's spoils, these he never parted with."),
       ("簒奪の三閃", "Usurper's Triple Flash", ["VANISH", "EXECUTE"], "LINE", "CROSS", "GATHER", "v:squid_ink",
        "dust:f0c040", 0x2E2240, 0xF0C040),
       SW("slim", "STEEL_DARK", trim="GOLD", grip="LEATHER_DARK", guard="basket", pommel="gem", gem="VOID_GLOW"),
       ("assassin", "INK", "GOLD", "INK", "VIOLET", "VOID_GLOW", "crown", {"S": "GOLD", "D": "DARK_VIOLET"}),
       ("GOLD", "VOID_GLOW", "INK")),
    _r("b2", "BOSS", "b2", 2, 13, ("古代樹", "Ancient Tree"),
       ("GREAT", "SLAM", "EARTH", "千年樹の剛剣", "Thousandyear Greatsword"), "HEAVY",
       ("BRACELET", "VIT", "STR", "DAMAGE"), ("STONE", "WARD"),
       ("古代樹の守護者が根に抱いていた、年輪の刻まれた宝。",
        "Treasures the ancient tree's guardian held in its roots, scored with growth rings."),
       ("千年輪の大震", "Thousand-Ring Quake", ["STUN", "ZONE"], "WAVE", "RING", "GROUND_SIGIL",
        "block:minecraft:oak_log", "v:composter", 0x6A4A2A, 0x8ACB5A),
       GS("broad", "DARKWOOD", trim="MOSS", grip="LEATHER", guard="claw", gem="AMBER", runes="LEAF"),
       (5, "DARKWOOD", "MOSS", "DARKWOOD", "LEAF", "AMBER", "tree", {"S": "WOOD", "F": "LEAF"}),
       ("BRONZE", "AMBER", "MOSS")),
    _r("b3", "BOSS", "b3", 3, 20, ("砂塵巨神", "Sand Colossus"),
       ("BOW", "IMPACT", "EARTH", "巨神の砂嵐弓", "Colossus Stormbow"), "HEAVY",
       ("AMULET", "STR", "VIT", "CRIT_DAMAGE"), ("TALISMAN", "THORNS"),
       ("砂嵐とともに歩んだ巨神の胸から零れた、砂金の遺宝。",
        "Gold-sand relics that spilled from the colossus that walked with the sandstorm."),
       ("砂嵐の巨弾", "Sandstorm Titanshot", ["KNOCKBACK", "SLOW_FIELD"], "BURST", "VORTEX", "GROUND_SIGIL",
        "block:minecraft:sand", "dust:e0b050", 0xD8B060, 0x8A5A2A),
       BW("SANDSTONE", recurve=3.0, trim="GOLD", bands=(8, 9), grip="LINEN", gem="AMBER", tips="curl"),
       (6, "SANDSTONE", "GOLD", "SAND", "SUNSTONE", "AMBER", "sun", {"F": "AMBER"}),
       ("GOLD", "AMBER", "SAND")),
    _r("b4", "BOSS", "b4", 4, 26, ("凍獄の処刑人", "Frozen Headsman"),
       ("SPEAR", "IMPALE", "FROST", "氷獄の断頭斧槍", "Icebound Headsman's Halberd"), "HEAVY",
       ("EARRING", "STR", "VIT", "CRIT_DAMAGE"), ("BELL", "WARD"),
       ("凍獄で刑を執り続けた処刑人の、霜の降りた装具。",
        "The frosted gear of the headsman who carried out the frozen prison's sentences."),
       ("凍獄の断罪", "Frozen Verdict", ["EXECUTE", "MARK"], "PILLAR", "SPIKES", "GLOW", "v:snowflake",
        "block:minecraft:packed_ice", 0x8FC8F0, 0x2A3A5A),
       SPR("halberd", "FROST_STEEL", shaft="DARKWOOD", trim="IRON_DARK", edge="ICE", bands=(-12, -11), gem="ICE"),
       ("knight", "FROST_STEEL", "IRON_DARK", "ICE_DEEP", "NIGHT", "ICE", "flake", {"D": "DARK_BLUE"}),
       ("FROST_STEEL", "ICE", "NIGHT")),
    _r("b5", "BOSS", "b5", 4, 29, ("氷獄の女王", "Frost Queen"),
       ("TWIN", "FLURRY", "FROST", "氷后の双晶刃", "Ice Queen's Twin Crystals"), "ROBE",
       ("AMULET", "INT", "AGI", "CRIT"), ("STONE", "HEALTH"),
       ("氷獄の女王の宝物庫に眠っていた、溶けることのない氷の宝飾。",
        "Never-melting ice jewels from the frozen queen's vault."),
       ("氷晶の舞踏", "Crystal Ballroom", ["SLOW_FIELD", "EVASION"], "SCATTER", "STAR", "SPIN_UP", "v:snowflake",
        "dust:dff4ff", 0xBFE8FF, 0x6A9AE0),
       TW("crystal", "ICE", trim="SILVER", grip="SKY", guard="wing", pommel="gem", gem="ICE", glow="ICE"),
       ("mage", "ICE_DEEP", "SILVER", "FUR_WHITE", "ICE", "ICE", "flake", {"D": "DARK_BLUE"}),
       ("SILVER", "ICE", "FUR_WHITE")),
    _r("b6", "BOSS", "b6", 5, 35, ("竜王", "Dragon King"),
       ("SPEAR", "CHARGE", "FIRE", "竜王の焔牙槍", "Dragon King's Flamefang Lance"), "HEAVY",
       ("RING", "STR", "VIT", "DAMAGE"), ("TALISMAN", "LIFESTEAL"),
       ("灼炎の竜王の鱗と牙から鍛えられた、今も熱を帯びる遺宝。",
        "Relics forged from the searing dragon king's scales and fangs, still warm to the touch."),
       ("竜王の焔穿", "Dragonking Flamepierce", ["IMBUE", "LAUNCH"], "COMET", "GEYSER", "RISING", "v:flame",
        "v:lava", 0xE2582B, 0xFFD060),
       SPR("flamelance", "DRAGON", shaft="OBSIDIAN", trim="GOLD", edge="FLAME", bands=(-12, -11), gem="EMBER",
           veins="FLAME"),
       (2, "DRAGON", "GOLD", "DRAGON_DARK", "EMBER", "FLAME", "flame", {"R": "FLAME", "S": "OBSIDIAN", "D": "DARK_RED"}),
       ("GOLD", "FLAME", "DRAGON_DARK")),
    _r("b7", "BOSS", "b7", 6, 42, ("機械神", "Machine God"),
       ("GREAT", "GUILLOTINE", "THUNDER", "機神の雷断剣", "Machine God's Thundercleaver"), "HEAVY",
       ("BRACELET", "DEX", "INT", "SPEED"), ("BELL", "EVASION"),
       ("天空の機械神の歯車と回路から外れ落ちた、いまだ脈打つ部品。",
        "Parts that fell from the sky machine god's gears and circuits, still pulsing."),
       ("機神雷断", "Machina Thundercut", ["STUN", "MARK"], "PILLAR", "SIGIL", "AIR_SIGIL", "v:electric_spark",
        "dust:7af0ff", 0xEEDC3A, 0x7AF0FF),
       GS("executioner", "STEEL", trim="THUNDER", grip="NAVY", guard="wing", gem="CYAN_RUNE", runes="CYAN_RUNE",
          glow="THUNDER"),
       ("spellblade", "STEEL", "THUNDER", "NAVY", "CYAN_RUNE", "CYAN_RUNE", "gear", {"R": "THUNDER", "D": "DARK_BLUE"}),
       ("STEEL", "CYAN_RUNE", "NAVY")),
    _r("b8", "BOSS", "b8", 7, 47, ("終焉の門番", "End Gatekeeper"),
       ("STAFF", "METEOR", "SHADOW", "門番の封鍵杖", "Gatekeeper's Sealkey Staff"), "HEAVY",
       ("EARRING", "INT", "VIT", "DAMAGE"), ("STONE", "WARD"),
       ("終焉の門を守り続けた番人が、最後に託した鍵と装具。",
        "The keys and gear the gatekeeper of the end entrusted at the last."),
       ("封印の大門", "Sealing Gate", ["PULL", "ZONE"], "RAIN", "CAGE", "AIR_SIGIL", "v:reverse_portal",
        "v:squid_ink", 0x7B2BD0, 0x14101C),
       STF("cage", core="VOID_GLOW", shaft="OBSIDIAN", trim="VOID"),
       (7, "NIGHT", "VOID", "INK", "VOID_GLOW", "VOID_GLOW", "key", {"R": "VOID_GLOW", "S": "SHADOW", "D": "DARK_VIOLET"}),
       ("NIGHT", "VOID_GLOW", "INK")),
    _r("final", "BOSS", "final", 7, 50, ("終焉王", "End King"),
       ("BOW", "RAIN", "BLOOD", "終焉王の黙示弓", "End King's Apocalypse Bow"), "HEAVY",
       ("AMULET", "LUK", "VIT", "CRIT_DAMAGE"), ("TALISMAN", "LIFESTEAL"),
       ("世界の終わりを告げた王の玉座の間に残された、黒と紅の遺宝。",
        "Black and crimson relics left in the throne hall of the king who proclaimed the end."),
       ("終焉の黙示", "Apocalypse of the End", ["ZONE", "LIFESTEAL"], "RAIN", "CROSS", "AIR_SIGIL", "dust:a1162a",
        "v:soul", 0xA1162A, 0x1A1022),
       BW("OBSIDIAN", recurve=4.5, trim="BLOOD", bands=(8, 9), grip="CRIMSON_STEEL", gem="BLOOD", tips="curl",
          glow="BLOOD"),
       ("knight", "NIGHT", "BLOOD", "NIGHT", "BLOOD", "BLOOD", "crown", {"R": "BLOOD", "S": "OBSIDIAN", "D": "DARK_RED"}),
       ("OBSIDIAN", "BLOOD", "NIGHT")),
    # ================================================================ calamities
    _r("c_meadow", "CALAMITY", "meadow", 1, 15, ("呑影", "Devouring Shade"),
       ("TWIN", "ASSASSINATE", "SHADOW", "呑影の双爪", "Devouring Shade Twinclaws"), "LIGHT",
       ("EARRING", "AGI", "DEX", "CRIT"), ("POUCH", "EVASION"),
       ("草原を呑み込もうとした影が残した、昼でも暗い遺宝。",
        "Relics left by the shadow that tried to swallow the meadow; dark even at noon."),
       ("影呑みの顎", "Shadow-Swallow Jaws", ["VANISH", "SIPHON"], "LINE", "VORTEX", "GATHER", "v:squid_ink",
        "dust:4a8a3a", 0x1E2A1A, 0x6AB04A),
       TW("claw", "SHADOW", trim="MOSS", grip="LEATHER_DARK", guard="claw", pommel="spike", edge="VOID_GLOW"),
       ("assassin", "SHADOW", "MOSS", "INK", "LEAF", "VOID_GLOW", "eye", {"S": "SHADOW", "D": "DARK_GREEN"}),
       ("SHADOW", "VOID_GLOW", "MOSS")),
    _r("c_moon_forest", "CALAMITY", "moon_forest", 2, 20, ("月蝕樹", "Eclipse Tree"),
       ("STAFF", "BURST", "HOLY", "月蝕樹の枝杖", "Eclipse-Tree Branchstaff"), "ROBE",
       ("AMULET", "INT", "VIT", "HEALING"), ("BELL", "RENEWAL"),
       ("月が欠けるたびに枝を伸ばした巨樹の、白く光る遺宝。",
        "Pale-glowing relics of the colossal tree that grew a branch with every eclipse."),
       ("月蝕の輪光", "Eclipse Halo", ["HEAL", "CLEANSE"], "BURST", "RING", "AIR_SIGIL", "v:end_rod",
        "dust:2a2040", 0xD4DCF4, 0x2A2040),
       STF("moon", core="MOONSILVER", shaft="DARKWOOD", leaf="LEAF", trim="SILVER"),
       ("priest", "MOONSILVER", "SILVER", "MOONSILVER", "INK", "SKY", "moon", {"D": "DARK_VIOLET"}),
       ("MOONSILVER", "SKY", "INK")),
    _r("c_iron_mine", "CALAMITY", "iron_mine", 3, 25, ("坑底獣", "Pit Wrecker"),
       ("GREAT", "ERUPTION", "EARTH", "坑底の破砕大剣", "Pitbottom Crusher"), "HEAVY",
       ("BRACELET", "STR", "VIT", "DAMAGE"), ("STONE", "THORNS"),
       ("坑道の底で暴れた破壊獣の外殻から削り出した、鈍く光る鉄塊。",
        "Dull-gleaming iron carved from the shell of the beast that raged at the bottom of the mine."),
       ("坑底崩落", "Pit Collapse", ["STUN", "KNOCKBACK"], "GEYSER", "SPIKES", "GROUND_SIGIL",
        "block:minecraft:deepslate", "v:lava", 0x5A5048, 0xF07818),
       GS("slab", "IRON_DARK", trim="COPPER", grip="LEATHER", guard="short", gem="EMBER", veins="EMBER"),
       (2, "IRON_DARK", "COPPER", "LEATHER_DARK", "RUST", "EMBER", "mountain", {"R": "EMBER", "F": "AMBER"}),
       ("COPPER", "EMBER", "LEATHER_DARK")),
    _r("c_sacred_falls", "CALAMITY", "sacred_falls", 4, 30, ("堕ちた滝守", "Fallen Falls-Keeper"),
       ("SPEAR", "LANCE", "FROST", "堕滝の水鏡槍", "Fallen Cascade Mirrorspear"), "LIGHT",
       ("RING", "DEX", "INT", "CRIT"), ("BELL", "HEALTH"),
       ("聖なる滝を守り、やがて堕ちた番人の、濁りを知らぬ水の遺宝。",
        "Relics of water that never clouds, from the keeper of the sacred falls who fell."),
       ("堕滝の逆流", "Fallen Backflow", ["PULL", "SLOW_FIELD"], "WAVE", "SPIRAL", "GATHER", "v:falling_water",
        "v:bubble", 0x5AB8DA, 0x2A3A6A),
       SPR("trident", "TEAL_STEEL", shaft="NAVY", trim="PEARL", edge="ICE", gem="SEA"),
       ("ranger", "TEAL_STEEL", "PEARL", "SEA", "NAVY", "ICE", "drop_water", {"F": "PEARL"}),
       ("PEARL", "SEA", "NAVY")),
    _r("c_ancient_ruins", "CALAMITY", "ancient_ruins", 5, 40, ("焦遺の焔", "Ruin-Scorching Flame"),
       ("SWORD", "BURST", "FIRE", "焦遺焔の祭剣", "Ruinflame Rite-Sword"), "ROBE",
       ("EARRING", "INT", "STR", "DAMAGE"), ("TALISMAN", "RENEWAL"),
       ("古代遺跡を焦がし続けた焔が冷えて固まった、赤熱の遺宝。",
        "Red-hot relics, the cooled remains of the flame that kept scorching the ancient ruins."),
       ("焦遺の祭火", "Ruinflame Rite", ["IMBUE", "ZONE"], "BURST", "PILLAR", "RISING", "v:flame", "dust:ffc866",
        0xF09A34, 0x6A2A10),
       SW("flame", "SUNSTONE", trim="GOLD", grip="CLOTH_RED", guard="crescent_up", pommel="gem", gem="FLAME",
          runes="FLAME", glow="FLAME"),
       ("mage", "SANDSTONE", "GOLD", "DRAGON_DARK", "FLAME", "FLAME", "flame", {"D": "DARK_RED"}),
       ("GOLD", "FLAME", "DRAGON_DARK")),
    _r("c_soaring_peaks", "CALAMITY", "soaring_peaks", 6, 50, ("峰砕き", "Peakbreaker"),
       ("BOW", "PIERCE", "THUNDER", "峰砕きの貫天弓", "Peakbreaker Skypiercer"), "HEAVY",
       ("BRACELET", "DEX", "STR", "CRIT_DAMAGE"), ("STONE", "WARD"),
       ("峰を砕いた巨像の核から削り出された、雷を帯びた石の遺宝。",
        "Thunder-charged stone relics cut from the core of the colossus that broke the peaks."),
       ("貫天の雷矢", "Skypiercing Bolt", ["STUN", "CRIT_UP"], "LINE", "PILLAR", "GLOW", "v:electric_spark",
        "block:minecraft:stone", 0xFFF27A, 0x646A77),
       BW("STONE", trim="THUNDER", bands=(8, 9), grip="LEATHER_DARK", gem="THUNDER", glow="THUNDER"),
       (6, "STONE", "THUNDER", "STONE", "SKY", "THUNDER", "bolt", {"R": "THUNDER"}),
       ("STONE", "THUNDER", "SKY")),
    _r("c_end_forest", "CALAMITY", "end_forest", 7, 55, ("終焉の来訪者", "Visitor from the End"),
       ("STAFF", "LANCE", "SHADOW", "来訪者の虚空杖", "Visitor's Voidstaff"), "ROBE",
       ("AMULET", "INT", "LUK", "CRIT_DAMAGE"), ("TALISMAN", "LIFESTEAL"),
       ("終焉の向こうから来たものが落としていった、この世の理に従わない遺宝。",
        "Relics dropped by that which came from beyond the end; they obey no law of this world."),
       ("虚空の招来", "Void Summons", ["SIPHON", "MARK"], "LINE", "VORTEX", "AIR_SIGIL", "v:portal", "v:witch",
        0x9B45F0, 0x06040C),
       STF("crystal", core="VOID_GLOW", shaft="ABYSS", trim="VOID"),
       ("mage", "INK", "VOID", "INK", "ABYSS", "VOID_GLOW", "eye", {"D": "DARK_VIOLET"}),
       ("VOID", "VOID_GLOW", "ABYSS")),
    _r("c_farm", "CALAMITY", "farm", 8, 20, ("枯れ根", "Withering Root"),
       ("TWIN", "MAUL", "VENOM", "枯れ根の双鎌", "Witherroot Twin Sickles"), "LIGHT",
       ("RING", "STR", "AGI", "DAMAGE"), ("POUCH", "THORNS"),
       ("実りを枯らした根の芯から削り出した、毒を秘めた遺宝。",
        "Venom-laced relics carved from the heart of the root that withered the harvest."),
       ("枯死の刈り取り", "Withering Reap", ["ZONE", "LIFESTEAL"], "BURST", "SPIKES", "RISING", "v:item_slime",
        "block:minecraft:rooted_dirt", 0x7A9A1C, 0x5A3A1A),
       TW("sickle", "RUST", half=1.6, trim="STRAW", grip="LEATHER", guard="short", pommel="ring", edge="SLIME"),
       ("ranger", "STRAW", "RUST", "LEATHER", "SPORE", "SLIME", "wheat", {"F": "SLIME"}),
       ("RUST", "SLIME", "STRAW")),
    _r("c_lakeside", "CALAMITY", "lakeside", 9, 25, ("湖底獣", "Lakebed Wrecker"),
       ("SWORD", "CLEAVE", "FROST", "湖底獣の殻断剣", "Lakebed Shellsplitter"), "HEAVY",
       ("BRACELET", "VIT", "STR", "DAMAGE"), ("STONE", "HEALTH"),
       ("湖の底で眠っていた破壊獣の殻から作られた、冷たく重い遺宝。",
        "Cold, heavy relics made from the shell of the wrecker that slept on the lake bed."),
       ("湖底の大顎", "Lakebed Maw", ["KNOCKBACK", "SLOW_FIELD"], "CRESCENT", "WAVE", "GATHER", "v:bubble",
        "v:splash", 0x236C74, 0xC8F4F2),
       SW("cleaver", "TEAL_STEEL", trim="PEARL", grip="SEA", guard="short", gem="SEA"),
       (5, "TEAL_STEEL", "PEARL", "SEA", "SEA", "ICE", "wave", {"S": "PEARL", "F": "PEARL"}),
       ("TEAL_STEEL", "SEA", "PEARL")),
    _r("c_harbour", "CALAMITY", "harbour", 10, 30, ("嵐呼び", "Stormcaller"),
       ("STAFF", "VOLLEY", "THUNDER", "嵐呼びの呪笛杖", "Stormcaller's Hexpipe"), "ROBE",
       ("EARRING", "INT", "DEX", "SPEED"), ("BELL", "EVASION"),
       ("港に嵐を呼び寄せた呪術師の、潮と雷の匂いが残る遺宝。",
        "Relics of the hexer who called storms onto the harbour; they still smell of brine and lightning."),
       ("嵐呼びの雷雨", "Stormcall Downpour", ["STUN", "SPEED"], "FAN", "RAIN", "SPIN_UP", "v:electric_spark",
        "v:rain", 0x6A8AC8, 0xFFF27A),
       STF("fork", core="THUNDER", shaft="DARKWOOD", trim="COPPER"),
       ("mage", "SEA", "CORAL", "SEA", "TEAL_STEEL", "THUNDER", "bolt", {}),
       ("COPPER", "THUNDER", "SEA")),
    # ================================================================ lords (ヌシ)
    _r("l_garm", "LORD", "garm", 1, 15, ("ガルム", "Garm"),
       ("SPEAR", "CHARGE", "BLOOD", "ガルムの街道裂き", "Garm's Roadripper"), "LIGHT",
       ("EARRING", "STR", "AGI", "CRIT_DAMAGE"), ("POUCH", "LIFESTEAL"),
       ("街道を荒らしたガルムの牙と毛皮から作られた、血の匂いの消えない遺宝。",
        "Relics of Garm's fangs and hide; the smell of blood never fades."),
       ("街道裂きの突進", "Roadripper Rush", ["LIFESTEAL", "KNOCKBACK"], "COMET", "SPIKES", "RISING", "dust:8a1a1e",
        "item:minecraft:bone", 0x8A1A1E, 0xD8CCA8),
       SPR("fang", "BONE", shaft="LEATHER_DARK", trim="CRIMSON_STEEL", ribbon="BLOOD", bands=(-12, -11),
           edge="BLOOD"),
       ("ranger", "LEATHER_DARK", "BONE", "CLOTH_RED", "FUR_WHITE", "BLOOD", "fang", {"S": "BONE", "F": "BLOOD"}),
       ("BONE", "BLOOD", "LEATHER_DARK")),
    _r("l_hook", "LORD", "hook", 1, 15, ("赤目のフック", "Red-Eyed Hook"),
       ("BOW", "SNIPE", "SHADOW", "赤目の狙い鉤弓", "Red-Eye Hookbow"), "LIGHT",
       ("RING", "DEX", "LUK", "CRIT"), ("TALISMAN", "EVASION"),
       ("赤目のフックが獲物を狙う時に握った、赤い石の嵌まった遺宝。",
        "Red-stoned relics Hook gripped whenever he picked out his prey."),
       ("赤目の一射", "Red-Eye Shot", ["EXECUTE", "MARK"], "LINE", "CHAIN", "GLOW", "dust:d0343f",
        "item:minecraft:chain", 0xD0343F, 0x2A2E33),
       BW("IRON_DARK", recurve=3.5, trim="CRIMSON_STEEL", bands=(8, 9), grip="LEATHER_DARK", gem="BLOOD",
          tips="curl"),
       ("assassin", "IRON_DARK", "CRIMSON_STEEL", "LEATHER_DARK", "CLOTH_RED", "BLOOD", "eye",
        {"S": "IRON", "D": "DARK_RED"}),
       ("IRON_DARK", "BLOOD", "CLOTH_RED")),
    _r("l_durn", "LORD", "durn", 1, 15, ("大猪ドルン", "Great Boar Durn"),
       ("SWORD", "COMBO", "EARTH", "大猪の牙剣", "Great Boar Tuskblade"), "HEAVY",
       ("BRACELET", "STR", "VIT", "CRIT_DAMAGE"), ("STONE", "THORNS"),
       ("大猪ドルンが突き崩した岩に残っていた牙と、その牙を飾った遺宝。",
        "The tusk Durn left in the rocks it rammed apart, and the relics made to honour it."),
       ("大猪の地響き", "Boar Rumble", ["KNOCKBACK", "DEFENSE_UP"], "CRESCENT", "BURST", "RISING",
        "block:minecraft:coarse_dirt", "item:minecraft:bone", 0x7A5A3A, 0xF2E8D2),
       SW("broad", "BONE", trim="BRONZE", grip="LEATHER", guard="crescent_down", pommel="fang"),
       (1, "LEATHER", "BONE", "LEATHER_DARK", "FUR_WHITE", "AMBER", "mountain", {"S": "BONE"}),
       ("BONE", "AMBER", "LEATHER")),
    _r("l_nebula", "LORD", "nebula", 2, 20, ("古老ネブラ", "Elder Nebula"),
       ("BOW", "FAN", "HOLY", "古老の星読み弓", "Elder's Stargazer Bow"), "ROBE",
       ("AMULET", "INT", "LUK", "HEALING"), ("BELL", "RENEWAL"),
       ("月影の古老ネブラが星を数えるのに使った、淡く光る遺宝。",
        "Softly glowing relics the moonshade elder Nebula used to count the stars."),
       ("星読みの扇射", "Stargazer's Fan", ["HEAL", "CRIT_UP"], "FAN", "STAR", "AIR_SIGIL", "v:firework",
        "dust:c8b4ff", 0xA978F0, 0xF4F2FF),
       BW("MOONSILVER", recurve=3.0, trim="SILVER", bands=(8, 9), grip="VIOLET", gem="SKY", glow="SKY"),
       ("priest", "MOONSILVER", "SILVER", "VIOLET", "SKY", "SKY", "star", {"D": "DARK_VIOLET"}),
       ("SILVER", "SKY", "VIOLET")),
    _r("l_belladonna", "LORD", "belladonna", 2, 20, ("宵闇の魔女", "Dusk Witch"),
       ("STAFF", "LANCE", "VENOM", "宵闇の毒華杖", "Duskbloom Venomstaff"), "ROBE",
       ("EARRING", "INT", "AGI", "CRIT"), ("POUCH", "EVASION"),
       ("宵闇の魔女が夜ごと摘んだ毒花を封じた、甘い香りの遺宝。",
        "Sweet-scented relics sealing the poison flowers the dusk witch picked each night."),
       ("毒華の一刺", "Poisonbloom Lance", ["SLOW_FIELD", "EXECUTE"], "LINE", "PETALS", "GATHER", "v:witch",
        "dust:7aca3a", 0x7A3AA0, 0x7ACA3A),
       STF("lotus", core="POTION_VIOLET", shaft="NIGHT", leaf="LEAF"),
       ("mage", "VIOLET", "SLIME", "INK", "POTION_VIOLET", "SLIME", "flower", {"D": "DARK_VIOLET"}),
       ("VIOLET", "SLIME", "INK")),
    _r("l_lunar", "LORD", "lunar", 2, 20, ("銀狼王", "Silver Wolf King"),
       ("GREAT", "SPIN", "FROST", "狼王の銀月大剣", "Wolf King's Silvermoon"), "LIGHT",
       ("RING", "AGI", "STR", "SPEED"), ("STONE", "EVASION"),
       ("白銀の狼王が月夜に遠吠えした丘で見つかった、銀の遺宝。",
        "Silver relics found on the hill where the silver wolf king howled on moonlit nights."),
       ("銀月の旋風", "Silvermoon Whirl", ["SPEED", "SLOW_FIELD"], "RING", "CRESCENT", "SPIN_UP", "dust:dfe8ff",
        "v:snowflake", 0xC8D8F0, 0x8AA0C8),
       GS("fang", "MOONSILVER", trim="SILVER", grip="FUR_WHITE", guard="wing", gem="ICE"),
       ("ranger", "FUR_WHITE", "MOONSILVER", "LEATHER_DARK", "FUR_WHITE", "ICE", "moon", {"S": "SILVER"}),
       ("MOONSILVER", "ICE", "FUR_WHITE")),
    _r("l_dolga", "LORD", "dolga", 3, 25, ("墓守ドルガ", "Warden Dolga"),
       ("SPEAR", "IMPALE", "EARTH", "墓守の鶴嘴槍", "Warden's Pickspear"), "HEAVY",
       ("BRACELET", "VIT", "STR", "CRIT_DAMAGE"), ("STONE", "WARD"),
       ("坑道の墓守ドルガが、眠る者たちを守るために振るった遺宝。",
        "Relics Dolga swung to guard those who sleep in the shafts."),
       ("墓守の地穿ち", "Warden's Groundbreaker", ["STUN", "DEFENSE_UP"], "SPIKES", "PILLAR", "GROUND_SIGIL",
        "block:minecraft:gravel", "v:ash", 0x6A6460, 0xC0A080),
       SPR("hook", "IRON", shaft="DARKWOOD", trim="BRONZE", bands=(-12, -11), gem="AMBER"),
       (2, "IRON", "BRONZE", "LEATHER_DARK", "ASH", "AMBER", "skull", {}),
       ("BRONZE", "AMBER", "ASH")),
    _r("l_rustbone", "LORD", "rustbone", 3, 25, ("錆骨", "Rustbone"),
       ("TWIN", "CROSS", "VENOM", "錆骨の双刺", "Rustbone Twinstings"), "LIGHT",
       ("EARRING", "DEX", "AGI", "CRIT"), ("TALISMAN", "THORNS"),
       ("錆骨の射手の骨と矢じりを繋ぎ合わせた、錆の浮いた遺宝。",
        "Rust-flecked relics pieced together from the rustbone archer's bones and arrowheads."),
       ("錆骨の連刺", "Rustbone Sting", ["SLOW_FIELD", "EXECUTE"], "CROSS", "SCATTER", "GATHER", "dust:b05a28",
        "v:item_slime", 0xB05A28, 0x5CCC52),
       TW("needle", "RUST", trim="BONE", grip="LEATHER_DARK", guard="short", pommel="spike", edge="SLIME"),
       ("assassin", "RUST", "BONE", "LEATHER_DARK", "SLIME", "SLIME", "fang", {"S": "BONE"}),
       ("RUST", "SLIME", "BONE")),
    _r("l_vein_eater", "LORD", "vein_eater", 3, 25, ("鉱脈喰らい", "Vein-Eater"),
       ("BOW", "VOLLEY", "EARTH", "鉱脈喰らいの節弓", "Vein-Eater Segment Bow"), "LIGHT",
       ("RING", "DEX", "STR", "DAMAGE"), ("POUCH", "HEALTH"),
       ("鉱脈を喰らい続けた大ムカデの節から作られた、鉱石の光を宿す遺宝。",
        "Relics made from the segments of the giant centipede that ate the ore veins, glinting with ore."),
       ("鉱脈喰いの連射", "Veineater Volley", ["SPEED", "LIFESTEAL"], "COMET", "CHAIN", "GATHER",
        "block:minecraft:copper_ore", "dust:d8a040", 0x9A4A22, 0xD8A040),
       BW("COPPER", recurve=4.0, trim="BRONZE", bands=(8, 9), grip="LEATHER", gem="AMBER"),
       ("ranger", "COPPER", "BRONZE", "LEATHER", "RUST", "AMBER", "fang", {}),
       ("COPPER", "AMBER", "LEATHER")),
    _r("l_goura", "LORD", "goura", 4, 30, ("暴君ゴウラ", "Tyrant Goura"),
       ("GREAT", "SWEEP", "FROST", "暴君の瀑布大剣", "Tyrant's Cascade Greatblade"), "HEAVY",
       ("BRACELET", "STR", "VIT", "DAMAGE"), ("STONE", "THORNS"),
       ("滝壺に君臨したゴウラの、水圧で磨かれた遺宝。",
        "Relics of Goura, polished by the crushing water of the plunge pool it ruled."),
       ("瀑布の大断", "Cascade Cleave", ["KNOCKBACK", "SLOW_FIELD"], "WAVE", "GEYSER", "RISING", "v:splash",
        "v:falling_water", 0x2A8AB4, 0xDCF4FF),
       GS("broad", "SEA", trim="PEARL", grip="NAVY", guard="claw", gem="ICE", runes="ICE"),
       (5, "SEA", "PEARL", "NAVY", "TEAL_STEEL", "ICE", "wave", {}),
       ("PEARL", "ICE", "NAVY")),
    _r("l_mistweaver", "LORD", "mistweaver", 4, 30, ("霧糸", "Mistweaver"),
       ("TWIN", "CHAIN", "VENOM", "霧糸の毒牙双刃", "Mistweaver Fangpair"), "LIGHT",
       ("AMULET", "AGI", "DEX", "CRIT"), ("POUCH", "EVASION"),
       ("霧の中に糸を張り巡らせた大蜘蛛の、湿った糸と牙の遺宝。",
        "Relics of damp silk and fang from the giant spider that strung the mist with threads."),
       ("霧糸縫い", "Mistweave Stitch", ["PULL", "SLOW_FIELD"], "CHAIN", "DOME", "GATHER", "v:white_ash",
        "item:minecraft:string", 0xDCDCEC, 0x5A8A3A),
       TW("fang", "PEARL", trim="SLIME", grip="STRING", guard="claw", edge="SLIME"),
       ("assassin", "PEARL", "SLIME", "LINEN", "ASH", "SLIME", "web", {"S": "SILVER"}),
       ("SILVER", "SLIME", "LINEN")),
    _r("l_ord", "LORD", "ord", 4, 30, ("大亀オルド", "Great Tortoise Ord"),
       ("STAFF", "ORBS", "EARTH", "大亀の甲羅杖", "Great Tortoise Shellstaff"), "HEAVY",
       ("AMULET", "VIT", "INT", "HEALING"), ("STONE", "WARD"),
       ("滝守の大亀オルドが百年背負った甲羅から削り出した遺宝。",
        "Relics carved from the shell Ord the falls tortoise carried for a hundred years."),
       ("甲羅の守り珠", "Shellguard Orbs", ["SHIELD", "DEFENSE_UP"], "ORBS", "DOME", "GROUND_SIGIL",
        "dust:3a969c", "block:minecraft:mossy_cobblestone", 0x3A969C, 0x7A5A3A),
       STF("shell", core="TURQUOISE", shaft="DARKWOOD", trim="MOSS"),
       (5, "TEAL_STEEL", "MOSS", "STONE", "MOSS", "TURQUOISE", "mountain", {"S": "STONE"}),
       ("MOSS", "TURQUOISE", "STONE")),
    _r("l_valka", "LORD", "valka", 5, 40, ("狂戦士ヴァルカ", "Berserker Valka"),
       ("GREAT", "REND", "BLOOD", "狂戦士の血風大剣", "Berserker's Bloodgale"), "HEAVY",
       ("RING", "STR", "VIT", "CRIT_DAMAGE"), ("TALISMAN", "LIFESTEAL"),
       ("遺跡で戦い続けた狂戦士ヴァルカの、血を吸って赤黒く染まった遺宝。",
        "Relics of Valka the ruin berserker, stained dark red with all the blood they drank."),
       ("血風の乱撃", "Bloodgale Frenzy", ["LIFESTEAL", "ATTACK_UP"], "CRESCENT", "SPIKES", "RISING",
        "dust:770d1e", "v:damage_indicator", 0x770D1E, 0xEE5A64),
       GS("jagged", "CRIMSON_STEEL", trim="BRONZE", grip="CLOTH_RED", guard="claw", pommel="fang", edge="BLOOD",
          veins="BLOOD"),
       (3, "CRIMSON_STEEL", "BRONZE", "LEATHER_DARK", "FUR_WHITE", "BLOOD", "drop", {"S": "BONE", "D": "DARK_RED"}),
       ("BRONZE", "BLOOD", "LEATHER_DARK")),
    _r("l_scorch_eye", "LORD", "scorch_eye", 5, 40, ("灼眼", "Scorch-Eye"),
       ("STAFF", "BURST", "FIRE", "灼眼の焔瞳杖", "Scorch-Eye Pupilstaff"), "ROBE",
       ("EARRING", "INT", "DEX", "DAMAGE"), ("BELL", "RENEWAL"),
       ("灼眼のブレイズが見つめた先を焼き焦がした、燃える瞳の遺宝。",
        "Relics of the burning eye: whatever Scorch-Eye stared at was set ablaze."),
       ("灼眼の焦視", "Searing Stare", ["IMBUE", "MARK"], "BURST", "SIGIL", "GLOW", "v:small_flame",
        "dust:ffd04a", 0xFF9A2A, 0x3A0A04),
       STF("orb_claw", core="FLAME", shaft="OBSIDIAN", trim="GOLD"),
       ("mage", "DRAGON_DARK", "GOLD", "DRAGON_DARK", "EMBER", "FLAME", "eye", {"D": "DARK_RED"}),
       ("GOLD", "FLAME", "OBSIDIAN")),
    _r("l_agnis", "LORD", "agnis", 5, 40, ("神像アグニス", "Idol Agnis"),
       ("SPEAR", "THRUSTS", "HOLY", "神像の聖槍", "Idol's Holy Spear"), "HEAVY",
       ("BRACELET", "STR", "VIT", "SPEED"), ("STONE", "HEALTH"),
       ("遺跡の神像アグニスの台座の下から見つかった、祈りの込められた遺宝。",
        "Relics found beneath the pedestal of Agnis the ruin idol, steeped in prayer."),
       ("神像の聖連突", "Idol's Sacred Thrusts", ["HEAL", "DEFENSE_UP"], "HELIX", "SIGIL", "GLOW", "v:end_rod",
        "block:minecraft:sandstone", 0xECC24A, 0xF7E2B4),
       SPR("winged", "SUNSTONE", shaft="SANDSTONE", trim="HOLY_GOLD", bands=(-12, -11), gem="HOLY_GOLD"),
       (6, "SANDSTONE", "HOLY_GOLD", "SANDSTONE", "SUNSTONE", "HOLY_GOLD", "sun", {"F": "HOLY_GOLD"}),
       ("HOLY_GOLD", "SUNSTONE", "SANDSTONE")),
    _r("l_alvin", "LORD", "alvin", 6, 50, ("聖騎士アルヴィン", "Paladin Alvin"),
       ("SWORD", "COMBO", "HOLY", "聖騎士の天誓剣", "Paladin's Skyoath"), "HEAVY",
       ("RING", "STR", "INT", "CRIT"), ("BELL", "WARD"),
       ("天翔の聖騎士アルヴィンが峰の頂で誓いを立てた時の、光を帯びた遺宝。",
        "Light-touched relics from the oath Alvin the paladin swore on the highest peak."),
       ("天誓の聖斬", "Skyoath Strike", ["SHIELD", "PARTY_ATTACK"], "CRESCENT", "CROSS", "GLOW", "v:end_rod",
        "dust:ffe27a", 0xF5F7FF, 0xECC24A),
       SW("straight", "HOLY_WHITE", trim="HOLY_GOLD", grip="SKY", guard="wing", pommel="gem", gem="HOLY_GOLD",
          runes="HOLY_GOLD", glow="HOLY_GOLD"),
       ("knight", "HOLY_WHITE", "HOLY_GOLD", "SKY", "SKY", "HOLY_GOLD", "wing", {"F": "HOLY_GOLD", "D": "DARK_BLUE"}),
       ("HOLY_GOLD", "SKY", "HOLY_WHITE")),
    _r("l_peak_sniper", "LORD", "peak_sniper", 6, 50, ("峰の狙撃手", "Peak Sniper"),
       ("BOW", "SNIPE", "THUNDER", "狙撃手の天穿弓", "Peak Sniper's Heavenbore"), "LIGHT",
       ("EARRING", "DEX", "LUK", "CRIT_DAMAGE"), ("TALISMAN", "EVASION"),
       ("峰の狙撃手が雲の上から獲物を射抜いた、軽く強い遺宝。",
        "Light, strong relics of the sniper who shot prey from above the clouds."),
       ("天穿の一矢", "Heavenbore Arrow", ["CRIT_UP", "EXECUTE"], "LINE", "COMET", "GLOW", "v:cloud",
        "dust:79b2ee", 0x79B2EE, 0xFFFFFF),
       BW("SKY", recurve=4.5, trim="SILVER", bands=(8, 9), grip="LEATHER_DARK", gem="SKY", tips="curl"),
       ("ranger", "SKY", "SILVER", "LEATHER_DARK", "HOLY_WHITE", "SKY", "wing", {"F": "SILVER"}),
       ("SILVER", "SKY", "LEATHER_DARK")),
    _r("l_zephyr", "LORD", "zephyr", 6, 50, ("雷翼ゼファー", "Thunderwing Zephyr"),
       ("TWIN", "FLURRY", "THUNDER", "雷翼の疾風双刃", "Thunderwing Galepair"), "LIGHT",
       ("BRACELET", "AGI", "DEX", "SPEED"), ("BELL", "EVASION"),
       ("雷翼のゼファーの羽根を鍛え込んだ、雷鳴の速さを持つ遺宝。",
        "Relics forged with Zephyr's feathers; they move at the speed of thunder."),
       ("雷翼の乱舞", "Thunderwing Flurry", ["SPEED", "STUN"], "SCATTER", "SPIRAL", "SPIN_UP", "v:electric_spark",
        "item:minecraft:feather", 0xEEDC3A, 0xEAF6FF),
       TW("feather", "THUNDER", trim="SILVER", grip="SKY", guard="wing", pommel="tassel", glow="THUNDER"),
       ("assassin", "HOLY_WHITE", "THUNDER", "SKY", "SKY", "THUNDER", "bolt", {"R": "THUNDER"}),
       ("SILVER", "THUNDER", "SKY")),
    _r("l_xenos", "LORD", "xenos", 7, 50, ("先触れゼノス", "Herald Xenos"),
       ("STAFF", "METEOR", "SHADOW", "破滅の先触れの終杖", "Herald's Ruinstaff"), "ROBE",
       ("AMULET", "INT", "LUK", "DAMAGE"), ("TALISMAN", "WARD"),
       ("破滅を告げて回ったゼノスの、終わりの予兆を宿した遺宝。",
        "Relics of Xenos, who went about proclaiming ruin; they carry an omen of the end."),
       ("破滅の予兆", "Omen of Ruin", ["MARK", "SIPHON"], "RAIN", "STAR", "AIR_SIGIL", "v:dragon_breath",
        "v:reverse_portal", 0xC47DFF, 0x1B0633),
       STF("skull", core="VOID_GLOW", shaft="NIGHT", trim="VOID"),
       ("mage", "INK", "VOID", "NIGHT", "VOID_GLOW", "VOID_GLOW", "skull", {"D": "DARK_VIOLET"}),
       ("VOID", "VOID_GLOW", "NIGHT")),
    _r("l_black_knight", "LORD", "black_knight", 7, 50, ("黒騎士", "Black Knight"),
       ("SWORD", "CLEAVE", "SHADOW", "黒騎士の終剣", "Black Knight's Endblade"), "HEAVY",
       ("RING", "STR", "VIT", "CRIT_DAMAGE"), ("STONE", "THORNS"),
       ("終焉の黒騎士が仕えた主の名を刻んだ、黒鉄の遺宝。",
        "Black-iron relics engraved with the name of the master the black knight served."),
       ("黒騎士の断罪", "Black Verdict", ["EXECUTE", "DEFENSE_UP"], "CRESCENT", "PILLAR", "GLOW", "v:large_smoke",
        "dust:6a4c8c", 0x1C2232, 0x8A6ACA),
       SW("executioner", "NIGHT", trim="VOID", grip="CLOTH_RED", guard="crescent_down", pommel="gem",
          gem="VOID_GLOW"),
       (7, "NIGHT", "VOID", "NIGHT", "CLOTH_RED", "VOID_GLOW", "cross", {"S": "NIGHT", "D": "DARK_VIOLET"}),
       ("NIGHT", "VOID_GLOW", "CLOTH_RED")),
    _r("l_nihil", "LORD", "nihil", 7, 50, ("虚無竜ニヒル", "Void Drake Nihil"),
       ("SPEAR", "PIERCE", "SHADOW", "虚無竜の喰らい槍", "Voiddrake Gnawspear"), "LIGHT",
       ("EARRING", "AGI", "STR", "CRIT"), ("POUCH", "LIFESTEAL"),
       ("虚無を喰む竜ニヒルの牙から削り出した、空虚な輝きの遺宝。",
        "Relics cut from the fangs of Nihil the void-gnawing drake, shining with emptiness."),
       ("虚無の喰らい牙", "Void Gnaw", ["SIPHON", "VANISH"], "LINE", "VORTEX", "GATHER", "v:portal",
        "dust:2b0a4a", 0x4C138A, 0xE6C2FF),
       SPR("fang", "VOID", shaft="ABYSS", trim="NIGHT", edge="VOID_GLOW", gem="VOID_GLOW"),
       ("assassin", "ABYSS", "VOID", "INK", "VOID_GLOW", "VOID_GLOW", "fang", {"S": "SHADOW", "D": "DARK_VIOLET"}),
       ("ABYSS", "VOID_GLOW", "INK")),
    _r("l_brutus", "LORD", "brutus", 8, 20, ("暴れ牛ブルータス", "Rampaging Brutus"),
       ("GREAT", "SLAM", "EARTH", "暴れ牛の角断剣", "Rampaging Hornbreaker"), "HEAVY",
       ("BRACELET", "STR", "VIT", "DAMAGE"), ("STONE", "THORNS"),
       ("暴れ牛ブルータスの角を鍛え込んだ、突進の勢いを宿す遺宝。",
        "Relics forged with Brutus's horns; they hold all the force of its charge."),
       ("暴れ牛の踏み砕き", "Bull Stomp", ["KNOCKBACK", "LAUNCH"], "SPIKES", "BURST", "RISING",
        "block:minecraft:dirt", "item:minecraft:wheat", 0x6D4426, 0xD2B44E),
       GS("cleaver", "IRON", trim="WOOD", grip="LEATHER", guard="crescent_up", pommel="spike", gem="AMBER"),
       (1, "RUST", "WOOD", "LEATHER", "STRAW", "AMBER", "fang", {"F": "STRAW"}),
       ("IRON", "AMBER", "LEATHER")),
    _r("l_harvest_boss", "LORD", "harvest_boss", 8, 20, ("収穫泥棒", "Harvest Thief"),
       ("BOW", "VOLLEY", "VENOM", "収穫泥棒の早取り弓", "Harvest Thief's Quickpick Bow"), "LIGHT",
       ("RING", "DEX", "LUK", "CRIT"), ("POUCH", "EVASION"),
       ("収穫泥棒の頭目が盗んだ実りを数えるのに使った、くすねた宝の数々。",
        "Pilfered treasures the harvest thief chief used to count his stolen crops."),
       ("早取りの連射", "Quickpick Volley", ["SPEED", "EVASION"], "COMET", "SCATTER", "GATHER",
        "item:minecraft:wheat", "dust:96d22a", 0xD2B44E, 0x96D22A),
       BW("WOOD", recurve=3.0, trim="STRAW", bands=(8, 9), grip="CLOTH_RED", gem="SLIME"),
       ("ranger", "STRAW", "LEATHER_DARK", "LEATHER", "CLOTH_RED", "SLIME", "wheat", {"F": "CLOTH_RED"}),
       ("STRAW", "SLIME", "CLOTH_RED")),
    _r("l_strom", "LORD", "strom", 8, 20, ("案山子王ストローム", "Scarecrow King Strom"),
       ("STAFF", "BEAM", "EARTH", "案山子王の見張り杖", "Strom's Watchstaff"), "ROBE",
       ("AMULET", "INT", "VIT", "HEALING"), ("TALISMAN", "RENEWAL"),
       ("案山子の王ストロームが畑を見張り続けた杖と、身につけた藁の遺宝。",
        "The staff Strom the scarecrow king watched the fields with, and the straw relics it wore."),
       ("見張りの光線", "Watchman's Beam", ["HEAL", "SLOW_FIELD"], "LINE", "SIGIL", "GROUND_SIGIL",
        "item:minecraft:hay_block", "dust:eedc7e", 0xA88A34, 0x3A2C0C),
       STF("crook", core="STRAW", shaft="WOOD", trim="RUST"),
       ("priest", "STRAW", "WOOD", "LINEN", "CLOTH_RED", "AMBER", "sun", {"F": "CLOTH_RED"}),
       ("RUST", "AMBER", "STRAW")),
    _r("l_melusine", "LORD", "melusine", 9, 25, ("湖の魔女メリュー", "Lake Witch Melu"),
       ("SWORD", "DASH", "FROST", "湖の魔女の水鏡剣", "Lake Witch's Mirrorblade"), "ROBE",
       ("EARRING", "INT", "DEX", "CRIT"), ("BELL", "HEALTH"),
       ("湖の魔女メリューが水面に映した呪いを封じた、澄んだ水の遺宝。",
        "Clear-water relics sealing the curses the lake witch Melu reflected on the water."),
       ("水鏡の疾走", "Mirrorwater Dash", ["SLOW_FIELD", "VANISH"], "ARC", "WAVE", "GATHER", "v:bubble",
        "dust:f082b4", 0x6EE0A8, 0xF082B4),
       SW("saber", "PEARL", trim="JADE", grip="ROSE", guard="tsuba", pommel="tassel", pgem="JADE", edge="ICE"),
       ("priest", "ROSE", "JADE", "ROSE", "SEA", "JADE", "drop_water", {"F": "JADE"}),
       ("JADE", "ROSE", "SEA")),
    _r("l_drowned_fisher", "LORD", "drowned_fisher", 9, 25, ("溺れ漁師", "Drowned Fisher"),
       ("SPEAR", "LANCE", "FROST", "溺れ漁師の銛", "Drowned Fisher's Harpoon"), "LIGHT",
       ("BRACELET", "DEX", "STR", "DAMAGE"), ("POUCH", "LIFESTEAL"),
       ("湖に沈んだ漁師が最後まで握りしめていた銛と、網に絡まった遺宝。",
        "The harpoon the drowned fisher clutched to the end, and the relics tangled in his net."),
       ("溺れ漁師の網打ち", "Drowning Net", ["PULL", "SLOW_FIELD"], "LINE", "CAGE", "GATHER", "v:dripping_water",
        "item:minecraft:string", 0x3A6A8A, 0xB8B8D0),
       SPR("harpoon", "TEAL_STEEL", shaft="DARKWOOD", trim="STRING", gem="SEA"),
       ("ranger", "SEA", "STRING", "LEATHER_DARK", "TEAL_STEEL", "ICE", "wave", {}),
       ("TEAL_STEEL", "SEA", "STRING")),
    _r("l_numel", "LORD", "numel", 9, 25, ("湖底の主ヌメル", "Lakebed Lord Numel"),
       ("STAFF", "BURST", "VENOM", "湖底の主のぬめり杖", "Lakebed Lord's Slickstaff"), "ROBE",
       ("AMULET", "INT", "VIT", "DAMAGE"), ("STONE", "RENEWAL"),
       ("湖底の主ヌメルの体から採れた、決して乾かない粘りを持つ遺宝。",
        "Relics from the body of Numel, lord of the lakebed; their slime never dries."),
       ("ぬめりの大泡", "Slick Bubble Burst", ["SLOW_FIELD", "REGEN"], "BURST", "ORBS", "RISING", "v:item_slime",
        "v:bubble", 0x52D257, 0x1A3A2A),
       STF("ring", core="SLIME", shaft="MOSS", trim="JADE"),
       ("mage", "JADE", "SLIME", "MOSS", "SLIME", "SLIME", "drop_water", {"D": "DARK_GREEN"}),
       ("JADE", "SLIME", "MOSS")),
    _r("l_barbas", "LORD", "barbas", 10, 30, ("隻眼の船長", "One-Eyed Captain"),
       ("SWORD", "PRECISE", "BLOOD", "隻眼船長の海賊刀", "One-Eye's Cutlass"), "LIGHT",
       ("EARRING", "STR", "LUK", "CRIT_DAMAGE"), ("TALISMAN", "LIFESTEAL"),
       ("隻眼の船長バルバスが七つの海から奪った、金貨の匂いの遺宝。",
        "Relics Barbas the one-eyed captain plundered from seven seas; they smell of gold coin."),
       ("隻眼の一閃", "One-Eyed Flash", ["EXECUTE", "CRIT_UP"], "LINE", "STAR", "GLOW", "dust:c92a3a",
        "item:minecraft:gold_nugget", 0xC92A3A, 0xF4CF4F),
       SW("scimitar", "STEEL", trim="GOLD", grip="CLOTH_RED", guard="basket", pommel="gem", gem="BLOOD"),
       ("assassin", "LEATHER_DARK", "GOLD", "CLOTH_RED", "LINEN", "BLOOD", "anchor", {"S": "STEEL_DARK"}),
       ("GOLD", "BLOOD", "CLOTH_RED")),
    _r("l_dock_brawler", "LORD", "dock_brawler", 10, 30, ("港の喧嘩屋", "Dock Brawler"),
       ("TWIN", "CROSS", "EARTH", "喧嘩屋の拳刃", "Brawler's Knucklers"), "HEAVY",
       ("BRACELET", "STR", "AGI", "SPEED"), ("STONE", "WARD"),
       ("港の喧嘩屋が一度も負けなかった頃に身につけていた、傷だらけの遺宝。",
        "Scarred relics the dock brawler wore back when he never lost a fight."),
       ("喧嘩屋の乱打", "Brawler's Barrage", ["KNOCKBACK", "STAMINA"], "CROSS", "BURST", "RISING", "v:crit",
        "dust:c46c34", 0x9A4A23, 0xF0F0F0),
       TW("kukri", "IRON", trim="COPPER", grip="LEATHER", guard="ring", pommel="ring"),
       (1, "LEATHER", "COPPER", "LEATHER_DARK", "CLOTH_RED", "AMBER", "mountain", {}),
       ("COPPER", "AMBER", "LEATHER")),
    _r("l_ankara", "LORD", "ankara", 10, 30, ("錨喰らいの大ガニ", "Anchor-Eater"),
       ("GREAT", "GUILLOTINE", "FROST", "錨喰らいの鋏大剣", "Anchor-Eater Pincerblade"), "HEAVY",
       ("RING", "VIT", "STR", "CRIT_DAMAGE"), ("STONE", "THORNS"),
       ("錨を喰らう大ガニアンカラの鋏と甲羅から作られた、潮に強い遺宝。",
        "Salt-proof relics made from the pincers and shell of Ankara the anchor-eating crab."),
       ("錨喰らいの大鋏", "Anchor-Eater Snap", ["EXECUTE", "DEFENSE_UP"], "PILLAR", "CRESCENT", "GATHER",
        "dust:e8786a", "v:splash", 0xC24A48, 0x5AB8DA),
       GS("pincer", "CORAL", trim="RUST", grip="SEA", guard="claw", gem="SEA"),
       (5, "CORAL", "RUST", "SEA", "SEA", "ICE", "anchor", {"S": "CORAL"}),
       ("CORAL", "SEA", "RUST")),
    # ================================================================ wanderers
    _r("w_bramble_knight", "WANDERER", "bramble_knight", 1, 12, ("野茨騎士", "Bramble Knight"),
       ("SPEAR", "LANCE", "VENOM", "野茨騎士の茨槍", "Bramble Knight's Thornlance"), "HEAVY",
       ("RING", "STR", "VIT", "DAMAGE"), ("STONE", "THORNS"),
       ("野茨の遍歴騎士が旅の途中で手放さなかった、棘の絡んだ遺宝。",
        "Thorn-wrapped relics the bramble errant knight never parted with on his travels."),
       ("茨槍の突撃", "Thornlance Charge", ["ZONE", "THORNS"], "COMET", "PETALS", "RISING",
        "item:minecraft:sweet_berries", "dust:3a6a24", 0x8A2A2A, 0x3A6A24),
       SPR("pike", "STEEL", shaft="WOOD", trim="MOSS", ribbon="CLOTH_RED", bands=(-12, -11), gem="ROSE"),
       ("knight", "STEEL", "MOSS", "LEATHER", "ROSE", "ROSE", "flower", {"F": "LEAF"}),
       ("STEEL", "ROSE", "MOSS")),
    _r("w_moonmoth_witch", "WANDERER", "moonmoth_witch", 2, 18, ("月光蛾", "Moonmoth"),
       ("BOW", "RAIN", "HOLY", "月光蛾の鱗粉弓", "Moonmoth Dustbow"), "ROBE",
       ("EARRING", "DEX", "INT", "CRIT"), ("BELL", "EVASION"),
       ("月光蛾の魔女の羽から落ちた鱗粉を固めた、夜に光る遺宝。",
        "Night-glowing relics hardened from the scale-dust that fell from the moonmoth witch's wings."),
       ("鱗粉の月雨", "Moondust Rain", ["ZONE", "EVASION"], "RAIN", "PETALS", "AIR_SIGIL", "v:white_ash",
        "dust:e6e0b0", 0xD8D0A0, 0x7A86B0),
       BW("MOONSILVER", trim="PEARL", bands=(8, 9), grip="VIOLET", gem="HOLY_GOLD"),
       ("priest", "MOONSILVER", "PEARL", "VIOLET", "LINEN", "HOLY_GOLD", "moon", {"D": "DARK_VIOLET"}),
       ("PEARL", "HOLY_GOLD", "VIOLET")),
    _r("w_slag_titan", "WANDERER", "slag_titan", 3, 23, ("鉱滓巨人", "Slag Titan"),
       ("GREAT", "ERUPTION", "FIRE", "鉱滓巨人の熔鉄剣", "Slag Titan's Moltenblade"), "HEAVY",
       ("BRACELET", "STR", "VIT", "CRIT_DAMAGE"), ("STONE", "WARD"),
       ("鉱滓の巨人の体内で溶けては固まりを繰り返した、熱い鉄の遺宝。",
        "Hot iron relics that melted and set again and again inside the slag titan."),
       ("熔鉄の噴出", "Molten Upheaval", ["IMBUE", "ZONE"], "GEYSER", "BURST", "GROUND_SIGIL", "v:lava",
        "block:minecraft:magma_block", 0xE8631A, 0x432D30),
       GS("slab", "OBSIDIAN", trim="EMBER", grip="LEATHER_DARK", guard="short", veins="FLAME", glow="FLAME"),
       (2, "OBSIDIAN", "EMBER", "IRON_DARK", "ASH", "FLAME", "flame", {"R": "FLAME"}),
       ("OBSIDIAN", "FLAME", "ASH")),
    _r("w_cascade_wyvern", "WANDERER", "cascade_wyvern", 4, 28, ("瀑流飛竜", "Cascade Wyvern"),
       ("SPEAR", "CHARGE", "FROST", "瀑流飛竜の翼槍", "Cascade Wyvern Wingspear"), "LIGHT",
       ("EARRING", "AGI", "STR", "MOVE"), ("BELL", "EVASION"),
       ("瀑流の飛竜が滝を昇る時に落とした鱗と翼膜の遺宝。",
        "Relics of scale and wing membrane the cascade wyvern shed while climbing the falls."),
       ("瀑流の急降下", "Cascade Dive", ["LEAP", "SLOW_FIELD"], "COMET", "WAVE", "SPIN_UP", "v:falling_water",
        "dust:7cc2e6", 0x4F94C4, 0xF0FDFF),
       SPR("winged", "ICE", shaft="NAVY", trim="SILVER", edge="ICE", gem="SKY"),
       ("ranger", "ICE_DEEP", "SILVER", "NAVY", "ICE", "SKY", "wing", {"F": "ICE"}),
       ("SILVER", "SKY", "NAVY")),
    _r("w_mist_hermit", "WANDERER", "mist_hermit", 4, 30, ("霧仙", "Mist Hermit"),
       ("STAFF", "ORBS", "HOLY", "霧仙の霞杖", "Mist Hermit's Hazestaff"), "ROBE",
       ("AMULET", "INT", "VIT", "HEALING"), ("TALISMAN", "RENEWAL"),
       ("霧に籠もった仙人が百年かけて霞を練り上げた、ほのかに温かい遺宝。",
        "Faintly warm relics the hermit kneaded out of haze over a hundred years in the mist."),
       ("霞練りの珠", "Haze-Kneaded Pearls", ["HEAL", "REGEN"], "ORBS", "DOME", "AIR_SIGIL", "v:cloud",
        "dust:d2ffe8", 0xC8F2E0, 0x6A9A8A),
       STF("crystal", core="PEARL", shaft="WOOD_PALE", trim="JADE"),
       ("priest", "LINEN", "JADE", "PEARL", "FUR_WHITE", "JADE", "drop_water", {}),
       ("JADE", "PEARL", "LINEN")),
    _r("w_walking_glyph", "WANDERER", "walking_glyph", 5, 36, ("歩く碑文", "Walking Glyph"),
       ("SWORD", "WAVE", "EARTH", "碑文の刻文剣", "Glyph-Carved Blade"), "HEAVY",
       ("AMULET", "INT", "STR", "CRIT"), ("STONE", "WARD"),
       ("歩く碑文の表面から剥がれ落ちた、意味を失わない古代文字の遺宝。",
        "Relics flaked from the walking inscription; their ancient letters never lose their meaning."),
       ("碑文の波動", "Inscription Wave", ["MARK", "STUN"], "WAVE", "SIGIL", "GROUND_SIGIL", "v:enchant",
        "block:minecraft:chiseled_stone_bricks", 0x2FD6EA, 0x868D9B),
       SW("broad", "STONE", trim="CYAN_RUNE", grip="LEATHER_DARK", guard="bar", runes="CYAN_RUNE", glow="CYAN_RUNE"),
       (6, "STONE", "CYAN_RUNE", "STONE", "CYAN_RUNE", "CYAN_RUNE", "bolt", {"R": "CYAN_RUNE"}),
       ("STONE", "CYAN_RUNE", "STONE")),
    _r("w_ash_herald", "WANDERER", "ash_herald", 5, 38, ("灰の先触れ", "Ash Herald"),
       ("BOW", "FAN", "FIRE", "灰の先触れの燼弓", "Ash Herald's Cinderbow"), "LIGHT",
       ("RING", "DEX", "INT", "DAMAGE"), ("TALISMAN", "THORNS"),
       ("灰の先触れが通った後に残る燼を集めて作られた、触れると温かい遺宝。",
        "Relics made from the cinders left wherever the herald of ash passed; warm to the touch."),
       ("燼の扇", "Cinder Spread", ["IMBUE", "SLOW_FIELD"], "FAN", "RAIN", "RISING", "v:ash", "v:small_flame",
        0x7C7682, 0xFF9A2A),
       BW("ASH", recurve=3.5, trim="EMBER", bands=(8, 9), grip="LEATHER_DARK", gem="FLAME"),
       ("ranger", "ASH", "EMBER", "LEATHER_DARK", "ASH", "FLAME", "flame", {}),
       ("ASH", "FLAME", "LEATHER_DARK")),
    _r("w_thunder_roc", "WANDERER", "thunder_roc", 6, 45, ("雷鳴の巨鳥", "Thunder Roc"),
       ("SPEAR", "JAVELIN", "THUNDER", "雷鳥の天雷槍", "Thunder Roc Skybolt Javelin"), "LIGHT",
       ("BRACELET", "DEX", "AGI", "SPEED"), ("BELL", "EVASION"),
       ("雷鳴の巨鳥が嵐の中心で落とした羽根を束ねた、雷を呼ぶ遺宝。",
        "Thunder-calling relics bundled from feathers the thunder roc shed in the eye of the storm."),
       ("天雷の投槍", "Skybolt Javelin", ["STUN", "MARK"], "COMET", "PILLAR", "AIR_SIGIL", "v:electric_spark",
        "v:cloud", 0xFFF27B, 0x2A3A7A),
       SPR("leaf", "THUNDER", shaft="NAVY", trim="SILVER", ribbon="SKY", gem="THUNDER", glow="THUNDER"),
       ("assassin", "NAVY", "THUNDER", "SKY", "HOLY_WHITE", "THUNDER", "wing", {"R": "THUNDER", "D": "DARK_BLUE"}),
       ("SILVER", "THUNDER", "NAVY")),
    _r("w_glacier_bearer", "WANDERER", "glacier_bearer", 6, 48, ("氷河負い", "Glacier Bearer"),
       ("GREAT", "SLAM", "FROST", "氷河負いの氷塊剣", "Glacier Bearer's Iceberg Blade"), "HEAVY",
       ("RING", "VIT", "STR", "DAMAGE"), ("STONE", "HEALTH"),
       ("氷河を背負って歩く者の背から削り出した、千年溶けない氷の遺宝。",
        "Relics cut from the back of the one who carries a glacier: ice that has not melted in a thousand years."),
       ("氷河の崩落", "Glacier Calving", ["STUN", "SLOW_FIELD"], "SPIKES", "PILLAR", "GROUND_SIGIL",
        "block:minecraft:blue_ice", "v:snowflake", 0x7CC2E6, 0x0F2A45),
       GS("crystal", "ICE_DEEP", trim="FROST_STEEL", grip="FUR_WHITE", guard="short", gem="ICE", glow="ICE"),
       (5, "FROST_STEEL", "ICE", "FUR_WHITE", "FUR_WHITE", "ICE", "flake", {"D": "DARK_BLUE"}),
       ("FROST_STEEL", "ICE", "FUR_WHITE")),
    _r("w_hollow_walker", "WANDERER", "hollow_walker", 7, 52, ("虚ろな巡礼者", "Hollow Pilgrim"),
       ("SWORD", "DASH", "SHADOW", "虚ろ巡礼の行脚剣", "Hollow Pilgrim's Wayblade"), "ROBE",
       ("AMULET", "AGI", "INT", "CRIT"), ("BELL", "RENEWAL"),
       ("終わらない巡礼を続ける者が祈りの代わりに携えた、空っぽの遺宝。",
        "Hollow relics the endless pilgrim carries in place of prayer."),
       ("虚ろの行脚", "Hollow Pilgrimage", ["VANISH", "SIPHON"], "ARC", "RING", "GATHER", "v:soul",
        "v:sculk_soul", 0x5A4866, 0x9AF0F0),
       SW("slim", "ASH", trim="VOID", grip="LINEN", guard="ring", pommel="tassel", pgem="VOID_GLOW"),
       ("priest", "ASH", "VOID", "LINEN", "INK", "VOID_GLOW", "eye", {"D": "DARK_VIOLET"}),
       ("ASH", "VOID_GLOW", "LINEN")),
    _r("w_gravebloom", "WANDERER", "gravebloom", 7, 54, ("墓花", "Gravebloom"),
       ("TWIN", "FAN", "VENOM", "墓花の散華双刃", "Gravebloom Petalfall Pair"), "LIGHT",
       ("EARRING", "AGI", "INT", "DAMAGE"), ("POUCH", "LIFESTEAL"),
       ("墓花の大樹が咲かせる、死者の夢を吸って色づいた花の遺宝。",
        "Relics of the gravebloom's flowers, coloured by the dreams of the dead they drink."),
       ("散華の舞", "Petalfall Dance", ["ZONE", "SIPHON"], "FAN", "PETALS", "RISING", "v:spore_blossom_air",
        "dust:6a1640", 0xD04A8A, 0x221838),
       TW("leaf", "ROSE", trim="INK", grip="LEAF", guard="crescent_up", pommel="gem", gem="ROSE"),
       ("assassin", "ROSE", "INK", "LEAF", "ROSE", "ROSE", "flower", {"D": "DARK_VIOLET"}),
       ("INK", "ROSE", "LEAF")),
    _r("w_scarecrow_king", "WANDERER", "scarecrow_king", 8, 17, ("渡りの案山子王", "Roaming Scarecrow King"),
       ("SPEAR", "THRUSTS", "FIRE", "案山子王の干し草熊手", "Scarecrow King's Hayfork"), "LIGHT",
       ("RING", "STR", "LUK", "CRIT_DAMAGE"), ("TALISMAN", "THORNS"),
       ("畑を渡り歩く案山子の王が手にした熊手と、藁に隠した遺宝。",
        "The fork the wandering scarecrow king carried, and the relics hidden in its straw."),
       ("熊手の連突き", "Hayfork Flurry", ["IMBUE", "KNOCKBACK"], "HELIX", "SCATTER", "RISING",
        "item:minecraft:wheat", "v:small_flame", 0xE0B040, 0xC43C0C),
       SPR("pitchfork", "IRON_DARK", shaft="WOOD_PALE", trim="STRAW", ribbon="CLOTH_RED"),
       ("ranger", "STRAW", "LEATHER", "LINEN", "EMBER", "FLAME", "wheat", {"F": "CLOTH_RED"}),
       ("IRON_DARK", "FLAME", "STRAW")),
    _r("w_reed_serpent", "WANDERER", "reed_serpent", 9, 22, ("葦原の大蛇", "Reed Serpent"),
       ("BOW", "SNIPE", "VENOM", "葦原大蛇の毒牙弓", "Reed Serpent Fangbow"), "LIGHT",
       ("EARRING", "DEX", "AGI", "CRIT"), ("POUCH", "EVASION"),
       ("葦原に潜む大蛇の牙と鱗から作られた、しなやかで毒を帯びた遺宝。",
        "Supple, venom-tinged relics made from the fangs and scales of the serpent that hides in the reeds."),
       ("大蛇の一咬み", "Serpent's Bite", ["EXECUTE", "SLOW_FIELD"], "LINE", "SPIRAL", "GATHER", "dust:4a8a14",
        "v:item_slime", 0x6EBA22, 0xF2E8C8),
       BW("MOSS", recurve=4.0, trim="JADE", bands=(8, 9), grip="LEATHER", gem="SLIME", tips="curl"),
       ("ranger", "MOSS", "JADE", "LINEN", "SLIME", "SLIME", "fang", {"S": "WOOD"}),
       ("JADE", "SLIME", "MOSS")),
    _r("w_sunken_captain", "WANDERER", "sunken_captain", 10, 27, ("沈まぬ船長", "Unsinking Captain"),
       ("GREAT", "REND", "THUNDER", "沈まぬ船長の錨大剣", "Unsinking Anchorblade"), "HEAVY",
       ("AMULET", "STR", "LUK", "CRIT_DAMAGE"), ("STONE", "WARD"),
       ("何度沈められても浮かび上がった船長の、錨と羅針盤の遺宝。",
        "Anchor and compass relics of the captain who rose again however often he was sunk."),
       ("錨落としの大旋回", "Anchorfall Swing", ["KNOCKBACK", "PULL"], "CRESCENT", "WAVE", "GATHER",
        "item:minecraft:chain", "v:splash", 0x56627A, 0x5AB8DA),
       GS("cleaver", "STEEL_DARK", trim="BRONZE", grip="SEA", guard="crescent_down", gem="SEA"),
       (2, "IRON_DARK", "BRONZE", "SEA", "SEA", "SEA", "anchor", {"F": "CORAL"}),
       ("BRONZE", "SEA", "IRON_DARK")),
    _r("w_tide_singer", "WANDERER", "tide_singer", 10, 29, ("潮呼びの巫女", "Tide Singer"),
       ("STAFF", "VOLLEY", "HOLY", "潮呼びの潮騒杖", "Tide Singer's Surfstaff"), "ROBE",
       ("AMULET", "INT", "VIT", "HEALING"), ("BELL", "RENEWAL"),
       ("潮を呼ぶ巫女が歌に合わせて鳴らした、潮騒の響く遺宝。",
        "Relics the tide-calling priestess rang in time with her songs; they echo with the surf."),
       ("潮騒の歌", "Song of the Surf", ["HEAL", "SLOW_FIELD"], "FAN", "WAVE", "SPIN_UP", "v:nautilus",
        "dust:b4ecff", 0x2A8AB5, 0xFFE27A),
       STF("bell", core="SEA", shaft="PEARL", trim="HOLY_GOLD"),
       ("priest", "SEA", "HOLY_GOLD", "PEARL", "TEAL_STEEL", "SKY", "wave", {"F": "HOLY_GOLD"}),
       ("HOLY_GOLD", "SEA", "PEARL")),
]


def load() -> list[Relic]:
    out = []
    for i, row in enumerate(ROWS):
        (key, kind, target, field, level, motif, weapon, style, acc, charm, lore, art, sprite, look, trinket) = row
        out.append(Relic(i, key, kind, target, field, level, motif, weapon, style, acc, charm, lore, art, sprite,
                         look, trinket))
    return out


def check(relics) -> list[str]:
    problems = []
    seen = {}
    for r in relics:
        for value in (r.key, r.weapon_id, r.set_id, r.accessory_id, r.charm_id, r.art_id, r.weapon[3], r.weapon[4],
                      r.art[0], r.art[1]):
            if value in seen:
                problems.append(f"{r.key}: '{value}' also used by {seen[value]}")
            seen[value] = r.key
        if r.wtype not in TYPES or r.weapon[1] not in FORM_FEEL[r.wtype]:
            problems.append(f"{r.key}: bad weapon {r.weapon}")
        if r.style not in STYLE_SET:
            problems.append(f"{r.key}: bad style {r.style}")
        if r.acc[0] not in ("RING", "AMULET", "EARRING", "BRACELET") or r.charm[0] not in (
                "TALISMAN", "BELL", "STONE", "POUCH"):
            problems.append(f"{r.key}: bad trinket kinds")
    if len(relics) != 64:
        problems.append(f"{len(relics)} sources, expected 64")
    return problems


if __name__ == "__main__":
    rs = load()
    print(len(rs), "relic sources", check(rs))
    from collections import Counter
    print(Counter(r.wtype for r in rs), Counter(r.element for r in rs), Counter(r.style for r in rs))
