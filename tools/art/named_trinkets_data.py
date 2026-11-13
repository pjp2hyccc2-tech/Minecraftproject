"""The named trinkets (名品の装身具, owner's request of 2026-11-13): one for each of the 30 lords (ヌシ) and an accessory
and a charm for each of the 8 dungeons and the final raid, in the eight newer trinket kinds. tools/art/named_trinkets.py
turns these rows into NamedTrinketTable.java, the names and lore (docs/i18n/named_trinkets.json), the icons, the
models and the overrides of the trinket base items. The numbers (stat percentages, the effect's strength) are made in
NamedTrinkets.java from the level, the stats and the effect named here.

Row: id, source (LORD / DUNGEON), origin (lord or dungeon id), field, level, kind, main stat, second stat, effect,
     (metal, gem, cloth) palette ramps, ja name, en name, ja lore, en lore.
The level is the lord's fighting level (its field's top monster level + 2) or the dungeon's recommended level; tests
check both against the game's own numbers.
"""
from __future__ import annotations

from collections import namedtuple

Row = namedtuple("Row", "id source origin field level kind main second effect look ja en ja_lore en_lore")

EFFECTS = ["CRIT", "CRIT_DAMAGE", "DAMAGE", "SPEED", "MOVE", "HEALING", "HEALTH", "DEFENSE", "LIFESTEAL", "THORNS",
           "EVASION", "HEAL_ON_KILL", "LAST_STAND", "SKILL_POWER", "BURN", "SLOW", "POISON", "STUN", "BLEED", "FREEZE"]
ACCESSORIES = ["BROOCH", "CIRCLET", "ANKLET", "MEDAL"]
CHARMS = ["MIRROR", "FEATHER", "BEADS", "DOLL"]
STATS = ["STR", "VIT", "AGI", "INT", "DEX", "LUK"]


def lord(key, field, level, kind, main, second, effect, look, ja, en, ja_lore, en_lore):
    return Row(f"trinket_lord_{key}", "LORD", key, field, level, kind, main, second, effect, look, ja, en, ja_lore,
               en_lore)


def dungeon(key, slot, field, level, kind, main, second, effect, look, ja, en, ja_lore, en_lore):
    return Row(f"trinket_dungeon_{key}_{slot}", "DUNGEON", key, field, level, kind, main, second, effect, look, ja, en,
               ja_lore, en_lore)


ROWS = [
    # ---- the lords (ヌシ): even rows an accessory, odd rows a charm
    lord("garm", 1, 17, "MEDAL", "STR", "AGI", "LIFESTEAL", ("BRONZE", "BLOOD", "CLOTH_RED"),
         "餓狼の勲章", "Medal of the Hungry Wolf",
         "街道を荒らす獣を討った者に、宿場の人々が贈った粗い勲章。牙の跡が残る。",
         "A rough medal the waystation folk gave whoever brought down the highway beast. It still bears tooth marks."),
    lord("hook", 1, 17, "MIRROR", "DEX", "LUK", "CRIT", ("IRON_DARK", "POTION_RED", "LEATHER_DARK"),
         "赤目の護り鏡", "Red-Eye Ward Mirror",
         "射手の赤い眼光を映し返したという小鏡。覗くと狙いが澄む。",
         "A little mirror said to have thrown back the archer's red glare. Looking into it steadies the aim."),
    lord("durn", 1, 17, "ANKLET", "VIT", "STR", "LAST_STAND", ("BONE", "MOSS", "LEATHER"),
         "猪突の足環", "Anklet of the Charging Boar",
         "大猪の牙を削って編んだ足環。追い詰められるほど足が前へ出る。",
         "An anklet of carved boar tusk. The more you are cornered, the harder your feet push forward."),
    lord("nebula", 2, 22, "FEATHER", "INT", "LUK", "HEAL_ON_KILL", ("SILVER", "ICE", "FUR_WHITE"),
         "月影の守り羽", "Moonshade Ward Feather",
         "月夜にだけ落ちる古老の羽。倒した命の名残をそっと持ち主に渡す。",
         "A feather the elder sheds only on moonlit nights. It passes a little of each fallen life to its bearer."),
    lord("belladonna", 2, 22, "BROOCH", "INT", "AGI", "POISON", ("GOLD_PALE", "POTION_VIOLET", "SHADOW"),
         "宵闇のブローチ", "Dusk Brooch",
         "魔女の胸元を飾っていた紫水晶。触れた刃に宵の毒が宿る。",
         "The violet stone that once sat at the witch's breast. Any blade it touches carries the dusk's venom."),
    lord("lunar", 2, 22, "BEADS", "AGI", "STR", "EVASION", ("SILVER", "ICE", "FUR_WHITE"),
         "白銀の牙の数珠", "Silver Fang Prayer Beads",
         "狼王の抜け落ちた牙を連ねた数珠。身を翻す一瞬が見えるようになる。",
         "Beads strung from the wolf king's shed fangs. You begin to see the instant to twist aside."),
    lord("dolga", 3, 27, "CIRCLET", "VIT", "INT", "THORNS", ("IRON_DARK", "SPORE", "STONE"),
         "墓守の額冠", "Gravewarden's Circlet",
         "坑道の死者を守り続けた墓守の冠。触れる者には痛みを返す。",
         "The crown of the warden who kept watch over the dead of the shafts. It returns pain to those who touch it."),
    lord("rustbone", 3, 27, "DOLL", "DEX", "VIT", "SLOW", ("BRONZE", "EMBER", "BONE"),
         "錆骨の身代わり人形", "Rustbone Ward Doll",
         "錆びた骨で組まれた人形。矢を受けた敵の足を重くする。",
         "A doll pieced together from rusted bones. Foes it marks walk as if their feet were lead."),
    lord("vein_eater", 3, 27, "BROOCH", "STR", "VIT", "CRIT_DAMAGE", ("STEEL", "TURQUOISE", "STONE"),
         "鉱晶のブローチ", "Ore-Crystal Brooch",
         "大ムカデの腹から出た青い鉱晶。急所を穿つ一撃が深くなる。",
         "A blue ore crystal found in the great centipede's belly. Blows that find a weak spot bite deeper."),
    lord("goura", 4, 32, "MIRROR", "VIT", "STR", "HEALTH", ("STEEL_DARK", "CYAN_RUNE", "SCARAB"),
         "滝壺の護り鏡", "Plunge Pool Ward Mirror",
         "滝壺の底で磨かれた鏡。水の重みが身体を太くする。",
         "A mirror polished at the bottom of the plunge pool. The weight of the water thickens the body."),
    lord("mistweaver", 4, 32, "CIRCLET", "AGI", "DEX", "SPEED", ("SILVER", "GLASS", "LINEN"),
         "霧糸の額冠", "Mistsilk Circlet",
         "霧の糸を編んだ額冠。腕が糸のように軽くなる。",
         "A circlet woven from threads of mist. Your arms grow as light as silk."),
    lord("ord", 4, 32, "BEADS", "VIT", "LUK", "DEFENSE", ("WOOD", "SCARAB", "MOSS"),
         "甲羅の数珠", "Shellback Prayer Beads",
         "大亀の甲羅を削った数珠。身にまとうと背に甲羅を負うようだ。",
         "Beads carved from the great tortoise's shell. Wearing them is like carrying its shell on your back."),
    lord("valka", 5, 42, "MEDAL", "STR", "VIT", "LAST_STAND", ("IRON", "DRAGON", "CLOTH_RED"),
         "狂戦士の勲章", "Berserker's Medal",
         "倒れるまで戦い続けた狂戦士の勲章。傷が深いほど腕が冴える。",
         "The medal of a berserker who fought until she fell. The deeper the wound, the keener the arm."),
    lord("scorch_eye", 5, 42, "FEATHER", "INT", "DEX", "BURN", ("GOLD", "FLAME", "EMBER"),
         "灼眼の守り羽", "Scorch-Eye Ward Feather",
         "燃える眼の主が残した火の羽。放つ一撃に火が移る。",
         "A burning feather left by the scorching eye. Every strike you loose catches its flame."),
    lord("agnis", 5, 42, "CIRCLET", "STR", "INT", "SKILL_POWER", ("SANDSTONE", "HOLY_GOLD", "SAND"),
         "神像の額冠", "Idol's Circlet",
         "遺跡の神像の額にはめられていた冠。技に古い神の力が宿る。",
         "The circlet set on the ruin idol's brow. An old god's strength flows into your arts."),
    lord("alvin", 6, 52, "BEADS", "STR", "VIT", "HEALING", ("HOLY_GOLD", "HOLY_WHITE", "SKY"),
         "聖騎士の数珠", "Paladin's Prayer Beads",
         "天翔ける聖騎士が祈りに使った数珠。癒しの手が温かくなる。",
         "The beads the paladin of the peaks prayed with. Healing hands grow warm."),
    lord("peak_sniper", 6, 52, "MEDAL", "DEX", "AGI", "CRIT_DAMAGE", ("STEEL", "SKY", "LEATHER"),
         "狙撃手の勲章", "Marksman's Medal",
         "峰の狙撃手が誇りにしていた勲章。外さない一射が致命になる。",
         "The medal the peak sniper took pride in. The shot that does not miss becomes the fatal one."),
    lord("zephyr", 6, 52, "FEATHER", "AGI", "DEX", "STUN", ("GOLD_PALE", "CYAN_RUNE", "SKY"),
         "雷翼の守り羽", "Thunderwing Ward Feather",
         "雷を纏う翼から抜けた羽。打たれた敵が一瞬しびれる。",
         "A feather torn from the thunder-clad wing. Foes it strikes freeze for a heartbeat."),
    lord("xenos", 7, 52, "CIRCLET", "INT", "VIT", "SKILL_POWER", ("OBSIDIAN", "VOID", "ABYSS"),
         "先触れの額冠", "Herald's Circlet",
         "破滅を告げに来た者の冠。唱える言葉に終わりの響きが混じる。",
         "The crown of the one who came to announce ruin. Your words carry an echo of the end."),
    lord("black_knight", 7, 52, "MIRROR", "STR", "VIT", "THORNS", ("IRON_DARK", "BLOOD", "SHADOW"),
         "黒鉄の護り鏡", "Black Iron Ward Mirror",
         "黒騎士の盾から削り出された鏡。刃を向けた者が自らを斬る。",
         "A mirror cut from the black knight's shield. Whoever raises a blade against you cuts themselves."),
    lord("nihil", 7, 52, "BROOCH", "AGI", "STR", "LIFESTEAL", ("ABYSS", "VOID_GLOW", "SHADOW"),
         "虚無竜のブローチ", "Void Drake Brooch",
         "虚無を喰む竜の鱗を留めたブローチ。斬った傷から命を吸う。",
         "A brooch holding a scale of the void-gnawing drake. It drinks life from every cut."),
    lord("brutus", 8, 22, "BEADS", "STR", "VIT", "STUN", ("WOOD", "POTION_RED", "LEATHER"),
         "牛角の数珠", "Bullhorn Prayer Beads",
         "暴れ牛の角を連ねた数珠。重い一撃が相手を揺さぶる。",
         "Beads strung from the rampaging bull's horns. Heavy blows leave the foe reeling."),
    lord("harvest_boss", 8, 22, "ANKLET", "DEX", "LUK", "MOVE", ("BRONZE", "SPORE", "WOOD_PALE"),
         "盗人の足環", "Thief's Anklet",
         "収穫泥棒の頭目が逃げ足自慢に着けていた足環。足取りが軽くなる。",
         "The anklet the harvest thieves' chief boasted his quick feet with. Your step grows light."),
    lord("strom", 8, 22, "DOLL", "INT", "VIT", "SLOW", ("WOOD_PALE", "FLAME", "SAND")
         , "案山子の身代わり人形", "Scarecrow Ward Doll",
         "案山子の王が畑に立てていた人形。近づく者の足を止める。",
         "One of the dolls the scarecrow king set up in his fields. It stops whoever comes near."),
    lord("melusine", 9, 27, "BROOCH", "INT", "DEX", "DAMAGE", ("SILVER", "TURQUOISE", "ICE"),
         "湖光のブローチ", "Lakelight Brooch",
         "湖の魔女が水面の光を閉じ込めた宝石。魔法が澄んで鋭くなる。",
         "A gem in which the lake witch caught the light on the water. Spells grow clear and sharp."),
    lord("drowned_fisher", 9, 27, "MIRROR", "DEX", "STR", "BLEED", ("IRON", "CYAN_RUNE", "LEATHER_DARK"),
         "水底の護り鏡", "Depths Ward Mirror",
         "沈んだ漁師の網に絡んでいた鏡。銛のような傷を残す。",
         "A mirror tangled in the drowned fisher's net. It leaves wounds like a harpoon's."),
    lord("numel", 9, 27, "ANKLET", "VIT", "INT", "EVASION", ("STEEL_DARK", "SLIME", "MOSS"),
         "湖底の足環", "Lakebed Anklet",
         "湖底の主のぬめりを帯びた足環。攻撃がつるりと滑っていく。",
         "An anklet slick with the lakebed lord's slime. Attacks slide right off you."),
    lord("barbas", 10, 32, "FEATHER", "STR", "LUK", "CRIT", ("GOLD", "BLOOD", "LINEN"),
         "海鷗の守り羽", "Seagull Ward Feather",
         "隻眼の船長の帽子に挿してあった羽。運のいい一撃が増える。",
         "The feather from the one-eyed captain's hat. Lucky blows come more often."),
    lord("dock_brawler", 10, 32, "MEDAL", "STR", "AGI", "SPEED", ("IRON", "TURQUOISE", "CLOTH_RED"),
         "喧嘩屋の勲章", "Brawler's Medal",
         "港の喧嘩で負け知らずだった男の勲章。拳が速くなる。",
         "The medal of a man who never lost a fight on the docks. Your fists move faster."),
    lord("ankara", 10, 32, "DOLL", "VIT", "STR", "DEFENSE", ("DRAGON_DARK", "GOLD", "SAND"),
         "甲殻の身代わり人形", "Carapace Ward Doll",
         "大ガニの甲殻で作られた人形。代わりに打撃を受け止める。",
         "A doll made from the great crab's shell. It takes the blows meant for you."),
    # ---- the dungeons and the final raid: an accessory and a charm each
    dungeon("d1", "accessory", 1, 6, "BROOCH", "AGI", "LUK", "CRIT", ("SILVER", "GOLD", "LEATHER_DARK"),
            "古都の銀ブローチ", "Old City Silver Brooch",
            "失われた地下都市の貴族が着けていた銀細工。盗賊たちも手を出さなかった。",
            "Silverwork worn by a noble of the lost undercity. Even the bandits left it alone."),
    dungeon("d1", "charm", 1, 6, "DOLL", "VIT", "LUK", "EVASION", ("BRONZE", "CYAN_RUNE", "LINEN"),
            "水路の身代わり人形", "Canal Ward Doll",
            "地下水路に流された人形。拾った者の身代わりに危機をかわす。",
            "A doll set adrift in the undercity canals. It dodges danger in its finder's place."),
    dungeon("d2", "accessory", 2, 13, "CIRCLET", "INT", "VIT", "HEALING", ("WOOD", "LEAF", "MOSS"),
            "若葉の額冠", "Sprout Circlet",
            "古代樹の若枝で編まれた冠。癒しの力が芽吹く。",
            "A circlet woven from the ancient tree's young twigs. Healing power sprouts within."),
    dungeon("d2", "charm", 2, 13, "FEATHER", "VIT", "INT", "HEAL_ON_KILL", ("WOOD_PALE", "HEAL_GREEN", "LEAF"),
            "樹精の守り羽", "Dryad Ward Feather",
            "迷宮の樹精が落とした緑の羽。命が巡るのを感じる。",
            "A green feather dropped by the labyrinth's dryad. You feel life flowing around you."),
    dungeon("d3", "accessory", 3, 20, "MEDAL", "STR", "DEX", "DAMAGE", ("GOLD", "TURQUOISE", "SAND"),
            "太陽神の勲章", "Sun God's Medal",
            "砂塵の神殿の神官が授けられた勲章。太陽の熱が腕に宿る。",
            "A medal granted to the priests of the sandstorm temple. The sun's heat dwells in your arms."),
    dungeon("d3", "charm", 3, 20, "BEADS", "LUK", "VIT", "LAST_STAND", ("SANDSTONE", "HOLY_GOLD", "SAND"),
            "砂金の数珠", "Gold-Dust Prayer Beads",
            "神殿の砂金を固めた数珠。窮地でこそ輝く。",
            "Beads pressed from the temple's gold dust. They shine brightest in a tight spot."),
    dungeon("d4", "accessory", 4, 26, "ANKLET", "STR", "AGI", "BLEED", ("IRON_DARK", "ICE_DEEP", "STONE"),
            "鎖の足環", "Chain Anklet",
            "凍てつく地下牢の囚人を繋いでいた鎖。その恨みが刃を研ぐ。",
            "The chain that bound the prisoners of the frozen dungeon. Their grudge hones your blade."),
    dungeon("d4", "charm", 4, 26, "MIRROR", "VIT", "STR", "THORNS", ("FROST_STEEL", "ICE", "ICE_DEEP"),
            "氷牢の護り鏡", "Ice Cell Ward Mirror",
            "氷の牢の壁から切り出した鏡。打ちかかる者を凍える痛みが襲う。",
            "A mirror cut from the wall of an ice cell. Freezing pain answers anyone who strikes."),
    dungeon("d5", "accessory", 4, 29, "CIRCLET", "INT", "AGI", "FREEZE", ("FROST_STEEL", "ICE", "FUR_WHITE"),
            "氷華の額冠", "Frostflower Circlet",
            "氷結の城の庭に咲く氷の花の冠。触れたものを凍らせる。",
            "A crown of the ice flowers in the frozen castle's garden. Whatever it touches freezes."),
    dungeon("d5", "charm", 4, 29, "FEATHER", "AGI", "VIT", "EVASION", ("SILVER", "SKY", "FUR_WHITE"),
            "白鳥の守り羽", "Swan Ward Feather",
            "女王の白鳥が残した羽。舞うように攻撃をかわす。",
            "A feather the queen's swan left behind. You slip past attacks as if dancing."),
    dungeon("d6", "accessory", 5, 35, "BROOCH", "STR", "INT", "BURN", ("OBSIDIAN", "FLAME", "EMBER"),
            "溶岩石のブローチ", "Lavastone Brooch",
            "火山洞窟の溶岩が冷え固まった石。今もかすかに熱を放つ。",
            "Stone formed where the volcano's lava cooled. It still gives off a faint heat."),
    dungeon("d6", "charm", 5, 35, "DOLL", "VIT", "STR", "HEALTH", ("DRAGON", "EMBER", "DRAGON_DARK"),
            "竜鱗の身代わり人形", "Dragonscale Ward Doll",
            "竜王の鱗を縫い付けた人形。持ち主の身体を頑丈にする。",
            "A doll stitched with the dragon king's scales. It makes its owner sturdy."),
    dungeon("d7", "accessory", 6, 42, "MEDAL", "DEX", "INT", "SKILL_POWER", ("BRONZE", "CYAN_RUNE", "SKY"),
            "天空機関の勲章", "Sky Engine Medal",
            "古代遺跡の機関から外された歯車の勲章。技の回転が上がる。",
            "A medal made from a gear taken out of the ancient ruins' engine. Your arts spin faster."),
    dungeon("d7", "charm", 6, 42, "MIRROR", "INT", "VIT", "STUN", ("HOLY_WHITE", "SKY", "GLASS"),
            "雲海の護り鏡", "Cloud Sea Ward Mirror",
            "雲海を映す鏡。放つ雷光が敵を縫い止める。",
            "A mirror that shows the sea of clouds. Your lightning pins foes in place."),
    dungeon("d8", "accessory", 7, 47, "CIRCLET", "INT", "STR", "CRIT_DAMAGE", ("OBSIDIAN", "VOID", "SHADOW"),
            "黒曜の額冠", "Obsidian Circlet",
            "終焉の塔の門番が戴いていた黒い冠。致命の一撃を研ぎ澄ます。",
            "The black crown the tower's gatekeeper wore. It sharpens the killing blow."),
    dungeon("d8", "charm", 7, 47, "BEADS", "VIT", "LUK", "LIFESTEAL", ("ABYSS", "VOID_GLOW", "SHADOW"),
            "虚ろの数珠", "Hollow Prayer Beads",
            "塔の虚ろから拾い集めた珠。斬るたびに命が戻る。",
            "Pearls gathered from the tower's hollows. Life returns with every cut."),
    dungeon("final", "accessory", 7, 50, "BROOCH", "STR", "DEX", "CRIT_DAMAGE", ("HOLY_GOLD", "BLOOD", "ABYSS"),
            "玉座のブローチ", "Throne Brooch",
            "終焉の王の外套を留めていた紅玉。王の一撃の重さを知る。",
            "The ruby that fastened the King of the End's mantle. It knows the weight of a king's blow."),
    dungeon("final", "charm", 7, 50, "MIRROR", "VIT", "INT", "LAST_STAND", ("GOLD", "VOID_GLOW", "SHADOW"),
            "黄昏の護り鏡", "Twilight Ward Mirror",
            "玉座の間に掛けられていた鏡。追い詰められた者に最後の力を貸す。",
            "The mirror that hung in the throne room. It lends one last strength to the cornered."),
]


def load():
    ids = set()
    for row in ROWS:
        assert row.id not in ids, row.id
        ids.add(row.id)
        assert row.source in ("LORD", "DUNGEON"), row.id
        assert row.kind in ACCESSORIES + CHARMS, row.id
        assert row.main in STATS and row.second in STATS and row.main != row.second, row.id
        assert row.effect in EFFECTS, row.id
        assert 1 <= row.field <= 10 and 1 <= row.level <= 100, row.id
    return ROWS
