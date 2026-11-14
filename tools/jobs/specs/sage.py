"""賢者 (Sage): the mage who reached wisdom. 融合 weaves two elements into one — steam, frost-fire, plasma,
gravity and starlight; 叡智 turns knowledge into help for the party — barriers, haste, time and mana."""

TITLE_JA = "賢者"
TITLE_EN = "Sage"
SUMMARY = "Fused elements and the party's mind: barriers, haste and time."
DAMAGE = "MAGIC"
BRANCHES = [("融合", "Fusion"), ("叡智", "Sagecraft")]

NODES = [
    # ------------------------------------------------------------------ 0: 融合
    ("a", "steam_burst", 0, 0, 1,
     '.cd(9).theme(FIRE).act(20, 16, 16, hit(9, ahead(3.0, 4.0), 2.4, 0.8))'
     '.fx(afflict(0, BLIND, 40, 0), afflict(0, BURN, 40, 0), vis(START, "cast"), vis(0, "mist"), vis(0, "burst"))',
     "drop cloud burn FIRE",
     "蒸気爆発", "Steam Burst",
     "炎と水を前方でぶつけ、蒸気の爆発を起こす。巻き込まれた敵は火傷を負い、目がくらむ。",
     "Clash fire and water ahead of you into an explosion of steam; caught foes are scalded and blinded."),
    ("a", "frostfire_bolt", 0, 1, 1,
     '.cd(8).theme(FROST).act(18, 14, 14, shot(7, orb("frost_orb", 1.8, 30).withExplosion(2.4), 2.2, 0.4))'
     '.fx(afflict(0, FREEZE, 40, 0), afflict(0, BURN, 40, 0), vis(START, "cast"))',
     "orb hail freeze FROST",
     "氷炎弾", "Frostfire Bolt",
     "凍てつく炎の弾を放つ。炸裂した場所の敵を凍らせながら燃やす。",
     "Fire a bolt of freezing flame that bursts where it lands, chilling and burning the foes at once."),
    ("p", "elemental_insight", 0, 2, 1, '.bonus(stats(mods().magic(0.03)))',
     "tome - - ARCANE",
     "元素の洞察", "Elemental Insight", "魔法攻撃力が上がる。", "Raises magic power."),
    ("a", "plasma_lance", 0, 3, 1,
     '.cd(10).theme(THUNDER).act(18, 14, 15, shot(7, orb("thunder_orb", 2.6, 24).withPierce(6), 2.6, 0.4))'
     '.fx(afflict(0, STUN, 15, 0), vis(START, "cast"), vis(0, "lightning"))',
     "lance beam stun THUNDER",
     "雷炎槍", "Plasma Lance",
     "雷と炎を一本の槍に束ねて放つ。一直線上の敵を貫いてしびれさせる。",
     "Bind lightning and flame into a single lance; it runs through every foe in its line and stuns them."),

    ("a", "lava_rain", 0, 0, 2,
     '.req("steam_burst", 3).cd(16).theme(FIRE).act(24, 20, 20,'
     ' shots(4, 4, 3, orb("magma", 1.4, 40).withExplosion(2.0).fallingFromAbove(), 1.4, 0.4))'
     '.fx(afflict(2, BURN, 80, 0), vis(START, "cast"), vis(0, "rain"))',
     "meteor rain x3 MAGMA",
     "熔岩雨", "Lava Rain",
     "狙った場所に熔岩の雫を三度降らせる。最後の雫が敵を燃え上がらせる。",
     "Let drops of lava fall three times where you aim; the last one sets the foes ablaze."),
    ("p", "double_casting", 0, 1, 2, '.req("elemental_insight", 1).bonus(cooldown(0.02))',
     "tome x clock ARCANE",
     "二重詠唱", "Double Casting", "スキルの再使用時間が短くなる。", "Skills recover faster."),
    ("a", "glacial_storm", 0, 2, 2,
     '.req("frostfire_bolt", 3).cd(20).theme(FROST).act(18, 14, 20, pause(5))'
     '.fx(zone(0, LOOK, 10.0, 4.5, 6, 6, 10, 0.6, SLOW, 40, 2), vis(START, "cast"), vis(0, "storm"))',
     "snowflake hail slow FROST",
     "氷嵐", "Glacial Storm",
     "狙った場所に氷の嵐を起こす。嵐の中の敵は凍える風に切り刻まれ、足が鈍る。",
     "Raise a storm of ice where you look; foes in it are cut by the freezing wind and slowed."),
    ("p", "mana_resonance", 0, 3, 2, '.req("plasma_lance", 1).bonus(power(0.02), stats(mods().crit(0.01)))',
     "crystal waves star ARCANE",
     "魔力共鳴", "Mana Resonance", "スキルの威力と会心率が上がる。", "Raises skill power and critical chance."),

    ("a", "prism_ray", 0, 0, 3,
     '.req("lava_rain", 1).cd(18).theme(ARCANE).act(24, 20, 22, hit(12, beam(12.0, 1.4), 3.0, 0.6))'
     '.fx(afflict(0, BLIND, 60, 0), afflict(0, VULNERABLE, 100, 0.15), vis(START, "cast"), vis(0, "ray"))',
     "crystal beam blind ARCANE",
     "七色光線", "Prismatic Ray",
     "七つの色に分かれた光線を放ち、一直線上の敵を焼いて目をくらませ、守りを崩す。",
     "Fire a ray split into seven colours, searing every foe in its line, blinding them and opening their guard."),
    ("a", "thunderfrost_circle", 0, 1, 3,
     '.req("double_casting", 1).cd(20).theme(THUNDER).act(22, 18, 20, hit(10, circle(5.0), 2.4, 0.6))'
     '.fx(afflict(0, FREEZE, 30, 0), afflict(0, STUN, 15, 0), vis(START, "sigil"), vis(0, "lightning"))',
     "rune nova freeze THUNDER",
     "雷氷陣", "Circle of Thunder and Frost",
     "足元に魔法陣を描き、雷と氷を同時に解き放つ。周りの敵は凍りつき、しびれる。",
     "Draw a circle at your feet and loose thunder and ice together: the foes around freeze and are stunned."),
    ("p", "arcane_scholar", 0, 2, 3, '.req("mana_resonance", 1).bonus(stats(mods().magic(0.02).critDamage(0.05)))',
     "tome glow crit ARCANE",
     "魔導の学徒", "Arcane Scholar", "魔法攻撃力と会心ダメージが上がる。", "Raises magic power and critical damage."),
    ("a", "gravity_flame", 0, 3, 3,
     '.req("plasma_lance", 3).cd(20).theme(ARCANE).act(22, 18, 20, shot(8, orb("rift", 1.2, 40).withExplosion(3.2), 2.8, 0.0))'
     '.fx(afflict(0, PULL, 1, 0.8), afflict(0, BURN, 60, 0), vis(START, "cast"), vis(0, "vortex"))',
     "portal spiral pull ARCANE",
     "重力炎", "Gravity Flame",
     "重さを持つ炎の球を放つ。炸裂すると周りの敵を中心へ引き寄せて燃やす。",
     "Fire an orb of heavy flame; when it bursts it drags the foes around into its heart and burns them."),

    ("a", "convergence", 0, 0, 4,
     '.req("prism_ray", 1).cd(26).theme(ARCANE)'
     '.act(34, 30, 26, hit(8, circle(4.0), 1.0, 0.2), hit(14, circle(4.0), 1.0, 0.2), hit(20, circle(4.0), 1.0, 0.2),'
     ' hit(26, circle(5.0), 1.8, 1.2))'
     '.fx(afflict(0, BURN, 60, 0), afflict(1, FREEZE, 30, 0), afflict(2, STUN, 20, 0), afflict(3, LAUNCH, 1, 0.6),'
     ' vis(START, "sigil"), vis(0, "burst"), vis(1, "storm"), vis(2, "lightning"), vis(3, "nova"))',
     "orbs3 nova x4 ARCANE",
     "四元収束", "Four-Element Convergence",
     "炎・氷・雷・風を順に周りへ解き放つ。燃やし、凍らせ、しびれさせ、最後に打ち上げる。",
     "Release fire, ice, thunder and wind around you in turn: foes burn, freeze, are stunned and finally thrown "
     "up."),
    ("p", "sages_wisdom", 0, 1, 4, '.req("arcane_scholar", 1).bonus(power(0.03))',
     "tome halo star ARCANE",
     "賢者の叡智", "A Sage's Wisdom", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "starfall", 0, 2, 4,
     '.req("thunderfrost_circle", 1).cd(24).theme(HOLY)'
     '.act(26, 22, 24, shots(6, 3, 5, orb("star_arrow", 1.6, 40).withExplosion(1.8).fallingFromAbove(), 1.0, 0.3))'
     '.fx(vis(START, "cast"), vis(0, "rain"), vis(4, "burst"))',
     "star rain x5 LIGHT",
     "星降り", "Starfall",
     "星を呼び、狙った場所へ五つ降らせる。",
     "Call the stars down: five fall where you aim."),
    ("p", "endless_mana", 0, 3, 4, '.req("gravity_flame", 1).bonus(saver(0.03))',
     "crystal glow stamina ARCANE",
     "無尽の魔力", "Endless Mana", "スキルのスタミナ消費が減る。", "Skills cost less stamina."),

    ("u", "genesis", 0, 0, 5,
     '.req("convergence", 3).cd(140).theme(ARCANE)'
     '.act(52, 48, 40, seq(pause(8), hits(16, 5, 4, circle(7.0), 1.2, 0.3), hit(40, circle(8.0), 5.0, 1.8)))'
     '.fx(afflict(1, BURN, 80, 0), afflict(2, FREEZE, 40, 0), afflict(3, STUN, 30, 0),'
     ' afflict(5, VULNERABLE, 160, 0.3), vis(START, "sigil"), vis(0, "meteor_cast"), vis(1, "burst"), vis(2, "storm"),'
     ' vis(3, "lightning"), vis(5, "nova"))',
     "sun nova star ARCANE",
     "奥義・天地創世", "Secret Art: Genesis",
     "【必殺技】天と地の元素をすべて呼び集める。炎・氷・雷・風が周りの敵を襲い、最後に世界が生まれた時のような光が守りを砕く。",
     "[Ultimate] Gather every element of heaven and earth: fire, ice, thunder and wind fall upon the foes around "
     "you, and a light like the birth of the world shatters their guard."),
    ("p", "archsage", 0, 1, 5, '.req("sages_wisdom", 1).bonus(stats(mods().magic(0.03).crit(0.01)))',
     "hat halo up ARCANE",
     "大賢者", "Archsage", "魔法攻撃力と会心率が上がる。", "Raises magic power and critical chance."),
    ("a", "chaos_orb", 0, 2, 5,
     '.req("starfall", 3).cd(32).theme(ARCANE).act(26, 22, 28, shot(10, orb("arcane", 1.0, 60).withExplosion(4.0), 4.0, 1.4))'
     '.fx(afflict(0, VULNERABLE, 120, 0.2), afflict(0, WEAKEN, 120, 0.2), vis(START, "cast"), vis(0, "vortex"),'
     ' vis(0, "nova"))',
     "orb spiral vuln ARCANE",
     "混沌球", "Orb of Chaos",
     "ゆっくり進む混沌の球を放つ。炸裂すると周りの敵の守りと力をまとめて奪う。",
     "Release a slow orb of chaos; when it bursts it strips the foes around of both guard and strength."),
    ("p", "lord_of_elements", 0, 3, 5, '.req("endless_mana", 3).bonus(proc(FREEZE, 0.02, 40, 0, false), stats(mods().magic(0.02)))',
     "snowflake glow freeze ARCANE",
     "元素の主", "Lord of the Elements", "攻撃が一定確率で敵を凍らせ、魔法攻撃力が上がる。",
     "Your attacks may freeze the foe, and your magic power rises."),

    # ------------------------------------------------------------------ 1: 叡智
    ("a", "mana_shield", 1, 0, 1,
     '.cd(20).theme(ARCANE).act(14, 10, 14, pause(4)).fx(shieldAllies(0, 0.2, 200, 8), vis(0, "shield"), vis(0, "sigil"))',
     "crystal halo shield ARCANE",
     "魔力の盾", "Mana Shield", "自分と周囲の仲間に魔力のシールドを張る。", "Shield yourself and the allies around you with mana."),
    ("a", "haste", 1, 1, 1,
     '.cd(26).theme(WIND).act(12, 8, 12, pause(3))'
     '.fx(self(0, SPEED, 1, 200), allies(0, SPEED, 1, 200, 10), vis(0, "aura"), vis(0, "storm"))',
     "boot speed speed WIND",
     "加速の呪文", "Spell of Haste", "自分と周囲の仲間の移動速度を大きく上げる。", "You and the allies around you move much faster."),
    ("p", "scholars_mind", 1, 2, 1, '.bonus(stats(mods().stamina(5)), cooldown(0.01))',
     "tome - stamina ARCANE",
     "学者の知性", "Scholar's Mind", "最大スタミナが上がり、スキルの再使用時間が短くなる。",
     "Raises maximum stamina; skills recover faster."),
    ("a", "rune_trap", 1, 3, 1,
     '.cd(16).theme(ARCANE).act(14, 10, 14, pause(4))'
     '.fx(zone(0, LOOK, 8.0, 3.0, 20, 1, 1, 3.0, STUN, 40, 0), vis(START, "cast"), vis(0, "sigil"))',
     "rune zone stun ARCANE",
     "魔法陣罠", "Rune Trap",
     "狙った場所に魔法陣を描く。一秒後に陣が炸裂し、上にいる敵を気絶させる。",
     "Draw a rune where you look; a second later it erupts and stuns the foes standing on it."),

    ("a", "arcane_ward", 1, 0, 2,
     '.req("mana_shield", 3).cd(30).theme(ARCANE).act(14, 10, 16, pause(4))'
     '.fx(allies(0, DEFENSE_UP, 0.2, 240, 10), self(0, DEFENSE_UP, 0.2, 240), cleanse(0, 10), vis(0, "aura"),'
     ' vis(0, "shield"))',
     "shield_cross ring shield ARCANE",
     "秘術の守護", "Arcane Ward",
     "秘術の守りで自分と周囲の仲間を包む。状態異常を払い、しばらく受けるダメージを減らす。",
     "Wrap yourself and the allies around you in arcane protection: ailments are cleansed and damage softened."),
    ("p", "deep_thought", 1, 1, 2, '.req("scholars_mind", 1).bonus(stats(mods().crit(0.015).magic(0.01)))',
     "eye glow crit ARCANE",
     "沈思", "Deep Thought", "会心率と魔法攻撃力が上がる。", "Raises critical chance and magic power."),
    ("a", "time_warp", 1, 2, 2,
     '.req("haste", 1).cd(40).theme(ARCANE).act(14, 10, 16, pause(4))'
     '.fx(cdcut(0, 60), self(0, SPEED, 0, 100), vis(START, "cast"), vis(0, "vortex"))',
     "clock spiral clock ARCANE",
     "時の歪み", "Time Warp",
     "自分の周りの時を早める。他のスキルの再使用時間が三秒縮み、しばらく素早く動ける。",
     "Speed up time around you: your other skills come back three seconds sooner and you move swiftly for a while."),
    ("p", "mana_spring", 1, 3, 2, '.req("rune_trap", 1).bonus(saver(0.02), stats(mods().stamina(3)))',
     "drop waves stamina ARCANE",
     "魔力の泉", "Mana Spring", "スキルのスタミナ消費が減り、最大スタミナが上がる。",
     "Skills cost less stamina and your stamina grows."),

    ("a", "empower", 1, 0, 3,
     '.req("arcane_ward", 1).cd(34).theme(ARCANE).act(14, 10, 16, pause(4))'
     '.fx(self(0, ATTACK_UP, 0.15, 240), allies(0, ATTACK_UP, 0.15, 240, 10), vis(0, "aura"), vis(0, "blessing"))',
     "staff ring up ARCANE",
     "魔力付与", "Empower",
     "自分と周囲の仲間に魔力を分け与え、しばらく与えるダメージを上げる。",
     "Share your mana with yourself and the allies around you: for a while everyone deals more damage."),
    ("a", "slowing_field", 1, 1, 3,
     '.req("deep_thought", 1).cd(26).theme(FROST).act(14, 10, 16, pause(4))'
     '.fx(zone(0, CASTER, 0, 6.0, 0, 10, 10, 0.2, SLOW, 40, 3), vis(0, "sigil"), vis(0, "mist"))',
     "hourglass zone slow FROST",
     "鈍化結界", "Field of Slowness",
     "足元に結界を広げる。しばらくの間、結界の中の敵はひどく鈍くなる。",
     "Spread a field at your feet; for a while foes inside it are badly slowed."),
    ("p", "sages_guidance", 1, 2, 3, '.req("mana_spring", 1).bonus(stats(mods().healing(0.05).magic(0.01)))',
     "hand halo heal LIFE",
     "賢者の導き", "Sage's Guidance", "回復量と魔法攻撃力が上がる。", "Raises healing and magic power."),
    ("a", "mirror_barrier", 1, 3, 3,
     '.req("time_warp", 1).cd(34).theme(ARCANE).act(14, 10, 16, pause(4))'
     '.fx(shieldAllies(0, 0.15, 160, 8), allies(0, THORNS, 0.2, 160, 8), self(0, THORNS, 0.2, 160), vis(0, "shield"),'
     ' vis(0, "iron_wall"))',
     "shield_spikes halo thorns ARCANE",
     "反射障壁", "Mirror Barrier",
     "鏡のような障壁を張る。自分と仲間はシールドを得て、受けた攻撃の一部を跳ね返す。",
     "Raise a mirrored barrier: you and your allies gain shields and send part of every blow back."),

    ("a", "rewind", 1, 0, 4,
     '.req("empower", 1).cd(60).theme(ARCANE).act(18, 14, 20, pause(6))'
     '.fx(heal(0, 10, 0.2, 4), cleanse(0, 10), cdcut(0, 40), vis(START, "cast"), vis(0, "heal"), vis(0, "vortex"))',
     "clock waves heal LIFE",
     "時間逆行", "Turn Back Time",
     "少しだけ時を巻き戻す。自分と仲間の傷が癒え、状態異常が消え、他のスキルの再使用時間が二秒縮む。",
     "Turn time back a little: wounds close, ailments vanish, and your other skills return two seconds sooner."),
    ("p", "insight", 1, 1, 4, '.req("sages_guidance", 1).bonus(cooldown(0.02))',
     "eye halo clock LIGHT",
     "洞察", "Insight", "スキルの再使用時間が短くなる。", "Skills recover faster."),
    ("a", "repulsion", 1, 2, 4,
     '.req("slowing_field", 1).cd(22).theme(ARCANE).act(16, 12, 18, hit(6, circle(5.0), 1.6, 3.0))'
     '.fx(afflict(0, SLOW, 60, 2), vis(0, "nova"), vis(0, "shield"))',
     "orb nova slow ARCANE",
     "斥力場", "Repulsion Field",
     "強い斥力を放ち、周りの敵を大きく吹き飛ばして足を鈍らせる。",
     "Push out with a powerful repelling force, hurling the foes around you far back and slowing them."),
    ("p", "arcane_aegis", 1, 3, 4, '.req("mirror_barrier", 1).bonus(stats(mods().defense(4).healthPercent(0.02)))',
     "shield halo - ARCANE",
     "秘術の加護", "Arcane Aegis", "防御力と最大HPが上がる。", "Raises defense and maximum health."),

    ("u", "eternal_wisdom", 1, 0, 5,
     '.req("rewind", 3).cd(150).theme(ARCANE).act(36, 32, 36, pause(8), hit(18, circle(6.0), 3.0, 1.0))'
     '.fx(shieldAllies(0, 0.4, 240, 12), allies(0, ATTACK_UP, 0.25, 240, 12), self(0, ATTACK_UP, 0.25, 240),'
     ' heal(0, 12, 0.2, 4), cdcut(0, 100), vis(START, "cast"), vis(0, "sigil"), vis(0, "aura"), vis(1, "pillar"),'
     ' vis(1, "nova"))',
     "tome pillar shield ARCANE",
     "奥義・永遠の叡智", "Secret Art: Eternal Wisdom",
     "【必殺技】すべての知恵を開く。仲間を癒して厚いシールドと力の加護で包み、他のスキルの再使用時間を五秒縮める。溢れた魔力の波が周りの敵を打つ。",
     "[Ultimate] Open all you know: allies are healed, wrapped in thick shields and strength, your other skills "
     "return five seconds sooner, and the overflowing mana strikes the foes around you."),
    ("p", "fount_of_knowledge", 1, 1, 5, '.req("insight", 1).bonus(power(0.02), cooldown(0.01))',
     "tome waves star ARCANE",
     "叡智の泉", "Fount of Knowledge", "スキルの威力が上がり、再使用時間が短くなる。", "Raises skill power; skills recover faster."),
    ("a", "mass_haste", 1, 2, 5,
     '.req("repulsion", 3).cd(60).theme(WIND).act(16, 12, 20, pause(5))'
     '.fx(self(0, SPEED, 1, 300), allies(0, SPEED, 1, 300, 14), allies(0, CRIT_UP, 0.1, 300, 14), stamina(0, 40),'
     ' vis(0, "storm"), vis(0, "aura"))',
     "feather speed crit WIND",
     "疾風の陣", "Formation of the Gale",
     "広い範囲の仲間に疾風の加護を与える。長い間素早く動け、会心が出やすくなる。自分のスタミナも取り戻す。",
     "Bless allies in a wide circle with the gale: they move swiftly and strike critically more often for a long "
     "while, and your stamina returns."),
    ("p", "sages_body", 1, 3, 5, '.req("arcane_aegis", 3).bonus(stats(mods().healthPercent(0.03)), lastStand(0.0, 0.03))',
     "heart halo shield ARCANE",
     "賢者の体", "Sage's Body", "最大HPが上がり、HPが減るほど受けるダメージが減る。",
     "Raises maximum health; the lower your health, the less damage you take."),
]
