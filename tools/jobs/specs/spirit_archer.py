"""精霊弓士 (Spirit Archer): the ranger who shoots with the spirits. 精霊矢 lends each arrow a spirit — wind, snow,
thunder, flame, earth and light; 霊樹 is the grove's blessing — roots, blossoms, healing and the world tree."""

TITLE_JA = "精霊弓士"
TITLE_EN = "Spirit Archer"
SUMMARY = "Arrows of the elemental spirits and the healing blessing of the grove."
DAMAGE = "RANGED"
BRANCHES = [("精霊矢", "Spirit Arrows"), ("霊樹", "Spirit Grove")]

NODES = [
    # ------------------------------------------------------------------ 0: 精霊矢
    ("a", "sylph_arrow", 0, 0, 1,
     '.weapons(BOW).cd(8).theme(WIND).act(18, 14, 14, shot(7, arrow(4.4).withPierce(3), 1.8, 1.6))'
     '.fx(vis(START, "cast"), vis(0, "ray"))',
     "arrow1 speed - WIND",
     "風精の矢", "Sylph Arrow",
     "風の精霊を宿した矢を放つ。敵を貫いて飛び、当たった敵を大きく吹き飛ばす。",
     "Loose an arrow carrying a wind spirit; it flies through the foes it strikes and blows them far back."),
    ("a", "snow_spirit_arrow", 0, 1, 1,
     '.weapons(BOW).cd(10).theme(FROST).act(18, 14, 14, shot(7, arrow(3.8).withExplosion(2.2), 1.6, 0.3))'
     '.fx(afflict(0, FREEZE, 40, 0), vis(START, "cast"), vis(0, "mist"))',
     "snowflake hail freeze FROST",
     "雪精の矢", "Snow Spirit Arrow",
     "雪の精霊を宿した矢を放つ。着弾すると冷気が広がり、周りの敵を凍らせる。",
     "Loose an arrow carrying a snow spirit; where it lands the cold spreads out and freezes the foes around."),
    ("p", "spirit_bond", 0, 2, 1, '.bonus(stats(mods().ranged(0.02).moveSpeed(0.01)))',
     "feather - up WIND",
     "精霊の絆", "Spirit Bond", "遠距離攻撃力と移動速度が上がる。", "Raises ranged power and movement speed."),
    ("a", "thunder_spirit_arrow", 0, 3, 1,
     '.weapons(BOW).cd(10).theme(THUNDER).act(18, 14, 15, shot(7, arrow(4.6), 1.8, 0.3))'
     '.fx(afflict(0, STUN, 20, 0), vis(START, "cast"), vis(0, "lightning"))',
     "bolt beamd stun THUNDER",
     "雷精の矢", "Thunder Spirit Arrow",
     "雷の精霊を宿した矢を放つ。射抜かれた敵の上に雷が落ち、しびれさせる。",
     "Loose an arrow carrying a thunder spirit; lightning falls on the foe it strikes and stuns it."),

    ("a", "gale_volley", 0, 0, 2,
     '.req("sylph_arrow", 3).weapons(BOW).cd(12).theme(WIND).act(20, 16, 16, shots(4, 3, 4, arrow(4.4), 0.7, 0.2))'
     '.fx(self(0, SPEED, 1, 80), vis(START, "cast"), vis(3, "storm"))',
     "arrows3fan speed x4 WIND",
     "疾風連矢", "Volley of the Gale",
     "風に乗せて四本の矢を立て続けに放つ。風の勢いでしばらく素早く動ける。",
     "Send four arrows flying on the wind in quick succession; the wind carries you swiftly for a while."),
    ("p", "spirit_whisper", 0, 1, 2, '.req("spirit_bond", 1).bonus(proc(SLOW, 0.03, 60, 1, false))',
     "feather mist slow FROST",
     "精霊のささやき", "Whisper of Spirits", "攻撃が一定確率で敵の足を鈍らせる。", "Your attacks may slow the foe."),
    ("a", "salamander_arrow", 0, 2, 2,
     '.req("thunder_spirit_arrow", 1).weapons(BOW).cd(14).theme(FIRE).act(20, 16, 18, shot(8, arrow(3.6).withExplosion(2.8), 2.2, 0.6))'
     '.fx(afflict(0, BURN, 80, 0), vis(START, "cast"), vis(0, "burst"))',
     "flame burst burn FIRE",
     "炎精の矢", "Salamander Arrow",
     "炎の精霊を宿した矢を放つ。着弾すると燃え上がり、周りの敵を焼く。",
     "Loose an arrow carrying a fire spirit; it bursts into flame where it lands and burns the foes around."),
    ("p", "sylph_grace", 0, 3, 2, '.req("thunder_spirit_arrow", 1).bonus(evasion(0.015))',
     "wings - - WIND",
     "風精の加護", "Sylph's Grace", "攻撃を回避しやすくなる。", "You dodge attacks more often."),

    ("a", "tempest_arrow", 0, 0, 3,
     '.req("gale_volley", 1).weapons(BOW).cd(18).theme(WIND).act(22, 18, 20, shot(9, arrow(3.0).withExplosion(4.0), 2.4, 0.0))'
     '.fx(afflict(0, PULL, 1, 0.8), vis(START, "cast"), vis(0, "vortex"))',
     "arrow2 spiral pull WIND",
     "嵐精の矢", "Tempest Arrow",
     "嵐の精霊を宿した矢を放つ。着弾すると竜巻が起こり、周りの敵を中心へ巻き込む。",
     "Loose an arrow carrying a storm spirit; a whirlwind rises where it lands and drags the foes around into it."),
    ("a", "aurora_arrows", 0, 1, 3,
     '.req("spirit_whisper", 1).weapons(BOW).cd(16).theme(HOLY).act(20, 16, 18, shot(8, arrow(4.0).withFan(5, 40), 1.2, 0.2))'
     '.fx(afflict(0, BLIND, 60, 0), vis(START, "cast"), vis(0, "ray"))',
     "arrows5fan halo blind LIGHT",
     "極光の矢", "Aurora Arrows",
     "光の精霊の力で五本の矢を扇状に放つ。射られた敵はまばゆい光に目がくらむ。",
     "Loose five arrows in a fan with the power of the light spirits; the foes they strike are dazzled."),
    ("p", "spirit_sight", 0, 2, 3, '.req("spirit_whisper", 1).bonus(stats(mods().crit(0.02)))',
     "eye halo crit ARCANE",
     "精霊の眼", "Spirit Sight", "会心率が上がる。", "Raises critical chance."),
    ("a", "gnome_arrow", 0, 3, 3,
     '.req("salamander_arrow", 1).weapons(BOW).cd(18).theme(EARTH).act(22, 18, 20, shot(9, arrow(3.4).withExplosion(3.0), 2.0, 0.6))'
     '.fx(afflict(0, LAUNCH, 1, 0.6), afflict(0, SLOW, 80, 2), vis(START, "cast"), vis(0, "quake"))',
     "gem crack launch EARTH",
     "地精の矢", "Gnome Arrow",
     "大地の精霊を宿した矢を放つ。着弾すると地面が隆起し、周りの敵を打ち上げて足を鈍らせる。",
     "Loose an arrow carrying an earth spirit; the ground heaves where it lands, throwing foes up and slowing them."),

    ("a", "wind_dragon_arrow", 0, 0, 4,
     '.req("tempest_arrow", 1).weapons(BOW).cd(22).theme(WIND).act(24, 20, 24, shot(10, arrow(3.2).withPierce(16), 3.6, 1.8))'
     '.fx(afflict(0, LAUNCH, 1, 0.7), vis(START, "cast"), vis(0, "ray"), vis(0, "storm"))',
     "snake beam launch WIND",
     "風龍の矢", "Wind Dragon Arrow",
     "風の龍となって飛ぶ矢を放つ。一直線上の敵すべてを貫いて打ち上げる。",
     "Loose an arrow that flies as a dragon of wind, piercing every foe in its line and throwing them up."),
    ("p", "spirit_archery", 0, 1, 4, '.req("spirit_sight", 1).bonus(power(0.03))',
     "bow halo star ARCANE",
     "精霊射術", "Spirit Archery", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "glacier_arrow", 0, 2, 4,
     '.req("aurora_arrows", 1).weapons(BOW).cd(22).theme(FROST).act(24, 20, 24, shot(10, arrow(3.4).withExplosion(3.6), 2.6, 0.4))'
     '.fx(afflict(0, FREEZE, 60, 0), vis(START, "cast"), vis(0, "burst"), vis(0, "storm"))',
     "crystal hail freeze FROST",
     "氷河の矢", "Glacier Arrow",
     "氷河の精霊を宿した矢を放つ。着弾すると氷が一面に広がり、周りの敵を長く凍りつかせる。",
     "Loose an arrow carrying a glacier spirit; ice spreads out where it lands and freezes the foes around for long."),
    ("p", "storm_caller", 0, 3, 4, '.req("gnome_arrow", 1).bonus(proc(STUN, 0.02, 20, 0, false))',
     "bolt glow stun THUNDER",
     "嵐を呼ぶ者", "Storm Caller", "攻撃が一定確率で敵をしびれさせる。", "Your attacks may stun the foe."),

    ("u", "arrow_of_the_spirit_king", 0, 0, 5,
     '.req("wind_dragon_arrow", 3).weapons(BOW).cd(120).theme(ARCANE)'
     '.act(48, 44, 40, pause(6), shot(30, arrow(4.0).withPierce(16).withExplosion(5.0), 7.0, 2.0))'
     '.fx(afflict(1, BURN, 80, 0), afflict(1, FREEZE, 40, 0), afflict(1, STUN, 30, 0), vis(START, "sigil"),'
     ' vis(0, "judgment_charge"), vis(1, "ray"), vis(1, "nova"))',
     "bow nova star ARCANE",
     "奥義・精霊王の矢", "Secret Art: Arrow of the Spirit King",
     "【必殺技】すべての精霊の力を一本の矢に集めて放つ。一直線上の敵を貫いて大爆発を起こし、燃やし、凍らせ、しびれさせる。",
     "[Ultimate] Gather the power of every spirit into one arrow: it pierces every foe in its line and explodes, "
     "burning, freezing and stunning them."),
    ("p", "lord_of_spirits", 0, 1, 5, '.req("spirit_archery", 1).bonus(stats(mods().ranged(0.03).crit(0.01)))',
     "crown halo up ARCANE",
     "精霊の主", "Lord of Spirits", "遠距離攻撃力と会心率が上がる。", "Raises ranged power and critical chance."),
    ("a", "four_spirits", 0, 2, 5,
     '.req("glacier_arrow", 3).weapons(BOW).cd(30).theme(ARCANE)'
     '.act(28, 24, 28, shots(6, 4, 4, arrow(4.2).withExplosion(1.8), 1.2, 0.3))'
     '.fx(afflict(0, BURN, 60, 0), afflict(1, FREEZE, 30, 0), afflict(2, STUN, 20, 0), afflict(3, LAUNCH, 1, 0.6),'
     ' vis(START, "cast"), vis(3, "burst"))',
     "orbs3 burst x4 ARCANE",
     "四精連矢", "Arrows of the Four Spirits",
     "炎・氷・雷・地の精霊の矢を順に放つ。燃やし、凍らせ、しびれさせ、最後に打ち上げる。",
     "Loose the arrows of fire, ice, thunder and earth in turn: foes burn, freeze, are stunned and thrown up."),
    ("p", "spirit_quiver", 0, 3, 5, '.req("storm_caller", 3).bonus(saver(0.03))',
     "quiver glow stamina ARCANE",
     "精霊の矢筒", "Spirit Quiver", "スキルのスタミナ消費が減る。", "Skills cost less stamina."),

    # ------------------------------------------------------------------ 1: 霊樹
    ("a", "forest_balm", 1, 0, 1,
     '.cd(16).theme(LIFE).act(16, 12, 16, pause(5))'
     '.fx(heal(0, 8, 0.08, 2), healZone(0, CASTER, 0, 5.0, 6, 15, 0.03, 0), vis(0, "heal"), vis(0, "aura"))',
     "leaf glow heal LIFE",
     "森の癒し", "Forest Balm",
     "森の精霊に祈り、自分と周囲の仲間を癒す。足元にしばらく癒しの光が残る。",
     "Pray to the forest spirits to heal yourself and the allies around you; a healing glow lingers at your feet."),
    ("a", "binding_vine_arrow", 1, 1, 1,
     '.weapons(BOW).cd(12).theme(LIFE).act(18, 14, 14, shot(7, arrow(4.0), 1.2, 0.0))'
     '.fx(afflict(0, STUN, 40, 0), afflict(0, SLOW, 100, 2), vis(START, "cast"), vis(0, "chains"))',
     "flower glow stun LIFE",
     "蔦絡みの矢", "Binding Vine Arrow",
     "蔦の精霊を宿した矢を放つ。射られた敵に蔦が絡みつき、動きを止める。",
     "Loose an arrow carrying a vine spirit; vines wrap the foe it strikes and hold it in place."),
    ("p", "forest_kin", 1, 2, 1, '.bonus(stats(mods().healthPercent(0.02).healing(0.04)))',
     "leaf - heart LIFE",
     "森の同胞", "Forest Kin", "最大HPと回復量が上がる。", "Raises maximum health and healing."),
    ("a", "wind_walk", 1, 3, 1,
     '.cd(14).theme(WIND).act(10, 6, 12, pause(2))'
     '.fx(dash(START, 7), self(0, SPEED, 1, 80), self(0, EVASION, 0.25, 60), vis(START, "blink"), vis(0, "storm"))',
     "feather speed speed WIND",
     "風渡り", "Wind Walk",
     "風に乗って一気に前へ渡る。しばらく素早く動け、攻撃をかわしやすくなる。",
     "Ride the wind far forward in one bound; for a while you move swiftly and dodge more easily."),

    ("a", "blossom_arrow", 1, 0, 2,
     '.req("forest_balm", 1).weapons(BOW).cd(16).theme(LIFE).act(18, 14, 16, shot(7, arrow(4.0).withExplosion(3.0), 1.4, 0.2))'
     '.fx(heal(0, 8, 0.06, 2), vis(START, "cast"), vis(0, "heal"))',
     "flower burst heal LIFE",
     "花咲く矢", "Blossom Arrow",
     "花の精霊を宿した矢を放つ。着弾すると花が咲き乱れて敵を打ち、自分と近くの仲間を癒す。",
     "Loose an arrow carrying a flower spirit; it bursts into bloom, striking foes and healing you and the allies "
     "near you."),
    ("p", "verdant_soul", 1, 1, 2, '.req("forest_kin", 1).bonus(stats(mods().healing(0.06)))',
     "flower halo heal LIFE",
     "新緑の魂", "Verdant Soul", "回復量が上がる。", "Raises healing."),
    ("a", "needle_rain", 1, 2, 2,
     '.req("binding_vine_arrow", 1).weapons(BOW).cd(16).theme(LIFE)'
     '.act(24, 20, 18, shots(6, 3, 5, arrow(2.8).fallingFromAbove(), 0.8, 0.1))'
     '.fx(afflict(4, BLEED, 80, 0.3), vis(START, "cast"), vis(0, "rain"))',
     "arrows_rain rain bleed LIFE",
     "針葉の矢雨", "Needle Rain",
     "針葉樹の精霊に願い、狙った場所へ鋭い矢を五本降らせる。最後の矢が敵を出血させる。",
     "Ask the spirits of the pines to rain five sharp arrows where you aim; the last leaves the foe bleeding."),
    ("p", "dryad_blessing", 1, 3, 2, '.req("wind_walk", 1).bonus(healOnKill(0.015))',
     "leaf halo heal LIFE",
     "樹精の祝福", "Dryad's Blessing", "敵を倒すとHPが回復する。", "Defeating a foe restores your health."),

    ("a", "grove_sanctuary", 1, 0, 3,
     '.req("blossom_arrow", 1).cd(34).theme(LIFE).act(18, 14, 20, pause(5))'
     '.fx(healZone(0, CASTER, 0, 6.0, 10, 15, 0.03, 0.4), allies(0, DEFENSE_UP, 0.15, 160, 8),'
     ' self(0, DEFENSE_UP, 0.15, 160), vis(0, "sanctuary"), vis(0, "heal"))',
     "leaf zone shield LIFE",
     "霊樹の聖域", "Sanctuary of the Grove",
     "足元に霊樹の聖域を開く。しばらくの間、仲間は癒されて守りが固まり、敵は傷つけられる。",
     "Open a sanctuary of the grove at your feet: for a while allies heal and harden, and foes are hurt."),
    ("a", "beast_spirit_arrow", 1, 1, 3,
     '.req("verdant_soul", 1).weapons(BOW).cd(18).theme(BLOOD).act(20, 16, 18, shot(8, arrow(4.6), 2.4, 0.8))'
     '.fx(afflict(0, BLEED, 100, 0.4), self(0, ATTACK_UP, 0.15, 160), vis(START, "roar"), vis(0, "thrust"))',
     "paw beamd bleed BLOOD",
     "獣霊の矢", "Beast Spirit Arrow",
     "獣の霊を宿した矢を放つ。牙のように敵に食らいついて出血させ、獣の力でしばらく自分の攻撃力が上がる。",
     "Loose an arrow carrying a beast spirit: it bites into the foe like fangs and makes it bleed, and the beast's "
     "strength stays with you for a while."),
    ("p", "spirit_breath", 1, 2, 3, '.req("dryad_blessing", 1).bonus(cooldown(0.02))',
     "leaf waves clock WIND",
     "精霊の息吹", "Breath of Spirits", "スキルの再使用時間が短くなる。", "Skills recover faster."),
    ("a", "earths_wrath", 1, 3, 3,
     '.req("needle_rain", 1).cd(22).theme(EARTH).act(22, 18, 20, hit(10, circle(5.0), 2.4, 0.6))'
     '.fx(afflict(0, LAUNCH, 1, 0.7), afflict(0, SLOW, 80, 2), vis(0, "quake"), vis(0, "pillar"))',
     "hand crack launch EARTH",
     "大地の怒り", "Wrath of the Earth",
     "大地の精霊を怒らせ、周りの地面を突き上げて敵を打ち上げ、足を鈍らせる。",
     "Rouse the anger of the earth spirits: the ground around you heaves up, hurling foes and slowing them."),

    ("a", "tree_of_life", 1, 0, 4,
     '.req("grove_sanctuary", 1).cd(40).theme(LIFE).act(20, 16, 24, pause(6))'
     '.fx(heal(0, 12, 0.2, 4), allies(0, REGENERATION, 0, 200, 12), self(0, REGENERATION, 0, 200), cleanse(0, 12),'
     ' vis(0, "heal"), vis(0, "pillar"))',
     "flower pillar heal LIFE",
     "生命樹の加護", "Blessing of the Tree of Life",
     "生命樹の力を呼び、自分と周囲の仲間を大きく癒す。状態異常を払い、しばらくHPが回復し続ける。",
     "Call on the tree of life: you and the allies around you are greatly healed, cleansed, and keep "
     "regenerating for a while."),
    ("p", "ancient_bark", 1, 1, 4, '.req("spirit_breath", 1).bonus(stats(mods().defense(5).healthPercent(0.02)))',
     "tower glow shield EARTH",
     "古樹の皮", "Ancient Bark", "防御力と最大HPが上がる。", "Raises defense and maximum health."),
    ("a", "spirits_blessing", 1, 2, 4,
     '.req("beast_spirit_arrow", 1).cd(40).theme(WIND).act(16, 12, 18, pause(5))'
     '.fx(self(0, ATTACK_UP, 0.15, 240), allies(0, ATTACK_UP, 0.15, 240, 12), allies(0, CRIT_UP, 0.08, 240, 12),'
     ' allies(0, SPEED, 0, 240, 12), vis(0, "aura"), vis(0, "blessing"))',
     "wings ring up WIND",
     "精霊の祝福", "Blessing of the Spirits",
     "精霊たちに祝福を願う。自分と周囲の仲間の攻撃力と会心率が上がり、足取りが軽くなる。",
     "Ask the spirits for their blessing: you and the allies around you strike harder, crit more and move faster."),
    ("p", "deep_roots", 1, 3, 4, '.req("earths_wrath", 1).bonus(lastStand(0.0, 0.05))',
     "leaf crack shield EARTH",
     "深き根", "Deep Roots", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),

    ("u", "world_tree_awakening", 1, 0, 5,
     '.req("tree_of_life", 3).cd(140).theme(LIFE).act(40, 36, 40, pause(8), hit(20, circle(8.0), 3.0, 1.2))'
     '.fx(healZone(0, CASTER, 0, 9.0, 12, 20, 0.05, 1.2), revive(0, 12, 0.3), shieldAllies(0, 0.3, 240, 12),'
     ' allies(0, ATTACK_UP, 0.2, 240, 12), afflict(1, LAUNCH, 1, 0.8), vis(START, "cast"), vis(0, "sigil"),'
     ' vis(0, "heal"), vis(1, "pillar"), vis(1, "quake"))',
     "leaf pillar heal LIFE",
     "奥義・世界樹の目覚め", "Secret Art: Awakening of the World Tree",
     "【必殺技】世界樹を目覚めさせる。倒れた仲間を蘇らせ、仲間をシールドと力の加護で包む。大地を割って伸びる根が周りの敵を打ち上げ、霊樹の光が長く仲間を癒し敵を焼く。",
     "[Ultimate] Awaken the world tree: fallen allies rise, the living are wrapped in shields and strength, roots "
     "burst from the earth and hurl foes up, and the grove's light heals friends and sears foes for a long while."),
    ("p", "grove_keeper", 1, 1, 5, '.req("ancient_bark", 1).bonus(stats(mods().healing(0.06).healthPercent(0.03)))',
     "leaf halo heart LIFE",
     "森の守り人", "Keeper of the Grove", "回復量と最大HPが上がる。", "Raises healing and maximum health."),
    ("a", "spirit_storm", 1, 2, 5,
     '.req("spirits_blessing", 1).cd(30).theme(WIND).act(18, 14, 22, pause(5))'
     '.fx(zone(0, LOOK, 12.0, 5.0, 4, 10, 8, 0.8, SLOW, 40, 2), vis(START, "cast"), vis(0, "storm"), vis(0, "vortex"))',
     "feather spiral slow WIND",
     "精霊嵐", "Spirit Storm",
     "狙った場所に精霊たちの嵐を起こす。しばらくの間、嵐の中の敵は切り刻まれ、足が鈍る。",
     "Raise a storm of spirits where you look; for a while foes inside are cut again and again and slowed."),
    ("p", "eternal_spring", 1, 3, 5, '.req("deep_roots", 3).bonus(stats(mods().healing(0.05)), healOnKill(0.01))',
     "flower glow heal LIFE",
     "常春", "Eternal Spring", "回復量が上がり、敵を倒すとHPが回復する。", "Raises healing; defeating a foe heals you."),
]
