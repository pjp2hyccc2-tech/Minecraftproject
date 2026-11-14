"""聖騎士 (Paladin): the holy knight. 光刃 lays light on the blade — smites, holy lances, wings of the seraph;
加護 blesses the line — auras, healing, the raising of the fallen."""

TITLE_JA = "聖騎士"
TITLE_EN = "Paladin"
SUMMARY = "Holy blows that heal, auras and blessings for the party."
DAMAGE = "PHYSICAL"
BRANCHES = [("光刃", "Radiant Blade"), ("加護", "Benediction")]

NODES = [
    # ------------------------------------------------------------------ 0: 光刃
    ("a", "radiant_edge", 0, 0, 1,
     '.cd(8).theme(HOLY).act(16, 12, 14, hit(6, arc(3.8, 70), 2.6, 0.5))'
     '.fx(siphon(0, 0.2, 0), afflict(0, BLIND, 40, 0), vis(0, "slash"), vis(0, "impact"))',
     "sword arc heal HOLY",
     "光輝の刃", "Radiant Edge",
     "光をまとった斬撃。斬った敵の目をくらませ、与えたダメージの一部でHPを回復する。",
     "A blade wreathed in light: it dazzles the foe and part of the damage heals you."),
    ("a", "smite_down", 0, 1, 1,
     '.cd(10).theme(HOLY).act(20, 16, 16, shot(8, orb("smite", 1.6, 40).withExplosion(2.8).fallingFromAbove(), 2.8,'
     ' 0.8)).fx(afflict(0, STUN, 20, 0), vis(START, "cast"), vis(0, "smite"))',
     "hammer smite stun HOLY",
     "天槌落とし", "Skyfall Smite",
     "狙った場所へ天から光の槌を落とし、周りの敵を一瞬気絶させる。",
     "Call a hammer of light down from the sky where you aim; foes around it are briefly stunned."),
    ("p", "devotion", 0, 2, 1, '.bonus(stats(mods().physical(0.03).healing(0.04)))',
     "cross - - HOLY",
     "騎士の献身", "Knight's Devotion", "物理攻撃力と回復量が上がる。", "Raises physical power and healing."),
    ("a", "crusader_dash", 0, 3, 1,
     '.cd(12).theme(HOLY).act(18, 14, 16, hit(6, line(6.0, 1.4), 2.2, 1.0))'
     '.fx(dash(START, 7), afflict(0, VULNERABLE, 80, 0.12), vis(START, "blink"), vis(0, "thrust"))',
     "lance speed vuln HOLY",
     "十字軍突撃", "Crusader's Dash",
     "光の尾を引いて突進し、貫いた敵の守りを崩す。",
     "Charge in a trail of light; the foes you pierce lose their guard."),

    ("a", "blessed_blade", 0, 0, 2,
     '.req("radiant_edge", 3).cd(24).theme(HOLY).act(12, 8, 10, pause(2))'
     '.fx(self(0, ATTACK_UP, 0.2, 200), self(0, LIFESTEAL, 0.08, 200), vis(0, "aura"), vis(0, "blessing"))',
     "sword glow up HOLY",
     "祝福の剣", "Blessed Blade",
     "剣に祝福を授ける。しばらく与えるダメージが増え、攻撃でHPを吸収する。",
     "Bless your sword: for a while you deal more damage and every blow heals you."),
    ("p", "zeal", 0, 1, 2, '.req("devotion", 1).bonus(stats(mods().crit(0.02).critDamage(0.06)))',
     "flame - crit HOLY",
     "熱情", "Fervor", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "hammer_of_light", 0, 2, 2,
     '.req("smite_down", 3).cd(14).theme(HOLY).act(24, 20, 20, hit(10, ahead(2.8, 3.0), 3.4, 1.2))'
     '.fx(afflict(0, STUN, 30, 0), afflict(0, WEAKEN, 100, 0.15), vis(0, "smite"), vis(0, "quake"))',
     "hammer crack weaken HOLY",
     "光の鉄槌", "Hammer of Light",
     "前方の地面へ光の鉄槌を振り下ろし、敵を気絶させて弱体化させる。",
     "Bring a hammer of light down on the ground ahead: foes are stunned and weakened."),
    ("p", "holy_charge", 0, 3, 2, '.req("crusader_dash", 1).bonus(stats(mods().moveSpeed(0.02).physical(0.02)))',
     "boot speed - HOLY",
     "聖なる突進", "Holy Charge", "移動速度と物理攻撃力が上がる。", "Raises movement speed and physical power."),

    ("a", "cross_slash", 0, 0, 3,
     '.req("blessed_blade", 1).cd(14).theme(HOLY)'
     '.act(22, 18, 20, hit(6, line(5.0, 1.0), 1.9, 0.4), hit(12, arc(4.0, 90), 1.9, 0.4))'
     '.fx(afflict(1, VULNERABLE, 100, 0.15), vis(0, "thrust"), vis(1, "slash"))',
     "sword x x2 HOLY",
     "聖十字斬", "Holy Cross",
     "縦と横に斬り、光の十字を刻む。刻まれた敵は脆弱になる。",
     "Cut down, then across, carving a cross of light; the foes it marks grow vulnerable."),
    ("a", "radiant_burst", 0, 1, 3,
     '.req("hammer_of_light", 1).cd(18).theme(HOLY).act(20, 16, 22, hit(8, circle(4.8), 2.8, 1.0))'
     '.fx(afflict(0, BLIND, 60, 0), heal(0, 6, 0.06, 2), vis(0, "nova"), vis(0, "burst"))',
     "sun nova heal LIGHT",
     "光爆", "Burst of Radiance",
     "身にまとった光を爆発させる。周囲の敵の目をくらませ、近くの仲間を癒す。",
     "Let the light you carry burst out: foes around are blinded and allies nearby are healed."),
    ("p", "light_infusion", 0, 2, 3, '.req("zeal", 1).bonus(lifesteal(0.01))',
     "drop glow heal HOLY",
     "光の浸透", "Light Infusion", "与えたダメージの一部でHPを回復する。", "Part of the damage you deal heals you."),
    ("a", "judgment_lance", 0, 3, 3,
     '.req("crusader_dash", 3).cd(16).theme(HOLY).act(22, 18, 22, shot(8, orb("holy_lance", 2.8, 24).withPierce(8),'
     ' 3.0, 0.9)).fx(afflict(0, LAUNCH, 1, 0.6), vis(START, "cast"), vis(0, "ray"))',
     "spear beam launch HOLY",
     "裁きの槍", "Lance of Judgment",
     "光の槍を投げ放ち、一直線上の敵をすべて貫いて打ち上げる。",
     "Hurl a lance of light: it runs through every foe in its line and throws them up."),

    ("a", "seraph_wings", 0, 0, 4,
     '.req("cross_slash", 1).cd(20).theme(HOLY).act(26, 22, 22, hit(18, circle(4.0), 3.4, 1.2))'
     '.fx(leap(START, 7, 3.0), self(0, ATTACK_UP, 0.15, 120), vis(START, "blink"), vis(0, "pillar"), vis(0, "nova"))',
     "wings pillar up HOLY",
     "熾天の翼", "Seraph's Wings",
     "光の翼で舞い上がり、敵の中心へ降り立つ。着地の光が周囲を焼き、しばらく力がみなぎる。",
     "Rise on wings of light and land in the thick of the foe; the light of the landing burns them and fills you "
     "with strength."),
    ("p", "sacred_fury", 0, 1, 4, '.req("light_infusion", 3).bonus(lastStand(0.06, 0.02))',
     "flame halo heart HOLY",
     "聖なる憤怒", "Sacred Fury", "HPが減るほど与えるダメージが大きく増える。",
     "The lower your health, the harder you strike."),
    ("a", "consecrated_ground", 0, 2, 4,
     '.req("radiant_burst", 3).cd(26).theme(HOLY).act(16, 12, 20, pause(5))'
     '.fx(healZone(0, CASTER, 0, 5.0, 10, 10, 0.02, 0.7), vis(0, "sigil"), vis(0, "pillar"))',
     "rune zone heal HOLY",
     "聖別の地", "Consecrated Ground",
     "足元を聖別する。しばらくの間、聖域の上の敵は焼かれ、仲間は癒される。",
     "Hallow the ground at your feet: for a while the foes on it burn and your allies on it heal."),
    ("p", "paragon", 0, 3, 4, '.req("judgment_lance", 1).bonus(power(0.03))',
     "crown glow star HOLY",
     "模範騎士", "Paragon", "スキルの威力が上がる。", "Raises the power of your skills."),

    ("u", "divine_judgment", 0, 0, 5,
     '.req("seraph_wings", 3).cd(120).theme(HOLY).act(40, 36, 40, pause(6), hit(26, circle(7.0), 5.5, 1.6))'
     '.fx(self(0, DEFENSE_UP, 0.5, 40), afflict(1, STUN, 50, 0), afflict(1, VULNERABLE, 160, 0.3),'
     ' heal(1, 10, 0.15, 4), vis(START, "cast"), vis(0, "sigil"), vis(1, "pillar"), vis(1, "nova"))',
     "sun smite stun LIGHT",
     "天上の審判", "Judgment from on High",
     "【必殺技】天に祈りを捧げ、巨大な光の柱を呼び降ろす。周囲の敵を気絶させて守りを砕き、仲間を大きく癒す。",
     "[Ultimate] Pray to the heavens and call down a vast pillar of light: foes around are stunned with their guard "
     "shattered, allies greatly healed."),
    ("p", "champion_of_light", 0, 1, 5, '.req("sacred_fury", 1).bonus(stats(mods().physical(0.03).healthPercent(0.03)))',
     "knight nova up HOLY",
     "光の勇者", "Champion of Light", "物理攻撃力と最大HPが上がる。", "Raises physical power and maximum health."),
    ("a", "heavens_cleave", 0, 2, 5,
     '.req("consecrated_ground", 1).cd(30).theme(HOLY).act(34, 30, 32, hit(14, beam(10.0, 2.0), 4.2, 1.4))'
     '.fx(afflict(0, BLIND, 80, 0), vis(START, "cast"), vis(0, "ray"), vis(0, "slash"))',
     "greatsword beam blind LIGHT",
     "天裂き", "Heaven's Cleave",
     "剣を天へ掲げ、光の刃を遥か前方まで振り下ろす。一直線上の敵すべてを斬り裂く。",
     "Raise your sword to the sky and bring a blade of light down far ahead, cleaving every foe in its line."),
    ("p", "radiant_armor", 0, 3, 5, '.req("paragon", 3).bonus(stats(mods().defense(6)), thorns(0.03))',
     "helmet halo thorns HOLY",
     "光輝の鎧", "Radiant Armor", "防御力が上がり、受けたダメージの一部を跳ね返す。",
     "Raises defense and returns part of the damage you take."),

    # ------------------------------------------------------------------ 1: 加護
    ("a", "healing_light", 1, 0, 1,
     '.cd(14).theme(HOLY).act(16, 12, 16, pause(5)).fx(heal(0, 8, 0.14, 3), vis(START, "cast"), vis(0, "heal"))',
     "cross glow heal LIFE",
     "癒しの光", "Healing Light", "自分と周囲の仲間のHPを回復する。", "Heal yourself and the allies around you."),
    ("a", "aura_of_courage", 1, 1, 1,
     '.cd(30).theme(HOLY).act(14, 10, 14, pause(4))'
     '.fx(allies(0, ATTACK_UP, 0.12, 240, 10), allies(0, CRIT_UP, 0.06, 240, 10), vis(0, "aura"), vis(0, "roar"))',
     "banner ring up HOLY",
     "鼓舞の光", "Rallying Light",
     "鼓舞の光を放ち、周囲の仲間を奮い立たせて攻撃力と会心率を上げる。",
     "Shine a light that rallies the allies around you: they strike harder and land more critical hits."),
    ("p", "grace", 1, 2, 1, '.bonus(stats(mods().healing(0.06).healthPercent(0.02)))',
     "flower - heal LIFE",
     "天恵", "Heaven's Gift", "回復量と最大HPが上がる。", "Raises healing and maximum health."),
    ("a", "shield_of_faith", 1, 3, 1,
     '.cd(20).theme(HOLY).act(14, 10, 14, pause(4)).fx(shieldAllies(0, 0.18, 160, 8), vis(0, "shield"), vis(0, "sigil"))',
     "shield halo shield HOLY",
     "信心の守り", "Ward of Faith", "自分と周囲の仲間にシールドを張る。", "Shield yourself and the allies around you."),

    ("a", "mending_wave", 1, 0, 2,
     '.req("healing_light", 3).cd(18).theme(LIFE).act(18, 14, 18, pause(5))'
     '.fx(heal(0, 8, 0.08, 2), healZone(0, CASTER, 0, 6.0, 6, 15, 0.04, 0), vis(0, "heal"), vis(0, "aura"))',
     "drop waves heal LIFE",
     "癒しの波", "Mending Wave",
     "癒しの波を広げる。仲間をすぐに回復し、その場にしばらく癒しの光を残す。",
     "Spread a wave of healing: allies mend at once and a healing glow stays on the ground for a while."),
    ("p", "benevolence", 1, 1, 2, '.req("grace", 1).bonus(healOnKill(0.02))',
     "heart glow heal LIFE",
     "慈愛", "Benevolence", "敵を倒すとHPが回復する。", "Defeating a foe restores your health."),
    ("a", "aura_of_warding", 1, 2, 2,
     '.req("aura_of_courage", 1).cd(30).theme(HOLY).act(14, 10, 14, pause(4))'
     '.fx(allies(0, DEFENSE_UP, 0.18, 240, 10), allies(0, REGENERATION, 0, 240, 10), vis(0, "aura"), vis(0, "shield"))',
     "buckler ring heal HOLY",
     "守護の気", "Aura of Warding",
     "守りの気を放ち、周囲の仲間の守りを固めて少しずつ回復させる。",
     "Radiate protection: allies around you take less damage and slowly heal."),
    ("p", "steadfast_faith", 1, 3, 2, '.req("shield_of_faith", 1).bonus(stats(mods().guard(0.02).defense(4)))',
     "shield_small - up HOLY",
     "揺るがぬ信仰", "Steadfast Faith", "ガード性能と防御力が上がる。", "Raises guard and defense."),

    ("a", "purifying_light", 1, 0, 3,
     '.req("mending_wave", 1).cd(24).theme(HOLY).act(16, 12, 16, pause(5))'
     '.fx(cleanse(0, 12), heal(0, 12, 0.1, 2), allies(0, SPEED, 0, 120, 12), vis(0, "pillar"), vis(0, "heal"))',
     "star pillar heal LIGHT",
     "清めの光", "Cleansing Radiance",
     "清めの光で仲間の状態異常を払い、回復して足取りを軽くする。",
     "A cleansing radiance: allies' ailments are washed away, their wounds mended and their steps quickened."),
    ("a", "lionheart", 1, 1, 3,
     '.req("aura_of_warding", 1).cd(34).theme(HOLY).act(16, 12, 16, pause(5))'
     '.fx(self(0, ATTACK_UP, 0.25, 160), allies(0, ATTACK_UP, 0.12, 160, 10), stamina(0, 30), vis(0, "roar"),'
     ' vis(0, "aura"))',
     "crown nova up FIRE",
     "獅子の心", "Lionheart",
     "獅子の如く吼え、自分の力を大きく高め、仲間を奮い立たせる。スタミナも取り戻す。",
     "Roar like a lion: your own strength surges, your allies take heart, and your stamina returns."),
    ("p", "sanctified", 1, 2, 3, '.req("benevolence", 1).bonus(stats(mods().healing(0.06)), cooldown(0.02))',
     "flask halo heal HOLY",
     "聖油の祝福", "Anointed", "回復量が上がり、スキルの再使用時間が短くなる。", "Raises healing; skills recover faster."),
    ("a", "guardian_angel", 1, 3, 3,
     '.req("shield_of_faith", 3).cd(40).theme(HOLY).act(18, 14, 18, pause(6))'
     '.fx(shieldAllies(0, 0.35, 120, 10), allies(0, DEFENSE_UP, 0.25, 120, 10), vis(START, "cast"), vis(0, "shield"),'
     ' vis(0, "pillar"))',
     "wings halo shield HOLY",
     "守護天使", "Guardian Angel",
     "守護天使を呼び、仲間を厚いシールドで包んで守りを固める。危機の瞬間に。",
     "Call a guardian angel: allies are wrapped in thick shields and hardened armor. For the moment of need."),

    ("a", "resurrection_hymn", 1, 0, 4,
     '.req("purifying_light", 3).cd(90).theme(HOLY).act(30, 26, 30, pause(12))'
     '.fx(revive(0, 10, 0.4), heal(0, 10, 0.15, 3), vis(START, "cast"), vis(0, "pillar"), vis(0, "heal"))',
     "feather pillar heart LIGHT",
     "蘇生の賛歌", "Hymn of Rising",
     "賛歌を捧げ、周囲で倒れた仲間をその場で蘇らせる。生きている仲間も癒す。",
     "Sing the hymn: fallen allies around you rise again where they lie, and the living are healed."),
    ("p", "beacon", 1, 1, 4, '.req("sanctified", 1).bonus(stats(mods().healthPercent(0.03)), healOnKill(0.01))',
     "star glow heal LIGHT",
     "灯火", "Beacon", "最大HPが上がり、敵を倒すとHPが回復する。", "Raises maximum health; defeating a foe heals you."),
    ("a", "holy_bulwark", 1, 2, 4,
     '.req("lionheart", 1).cd(30).theme(HOLY).act(18, 14, 20, hit(8, circle(5.0), 2.4, 1.2))'
     '.fx(taunt(0, 10, 120), shield(0, 0.3, 160), allies(0, DEFENSE_UP, 0.15, 160, 8), vis(0, "shield"), vis(0, "nova"))',
     "tower nova taunt HOLY",
     "光の城壁", "Wall of Radiance",
     "光の城壁を広げて周囲の敵を吹き飛ばし、引きつける。自分にシールドを張り、仲間の守りを固める。",
     "Spread a wall of light: foes around are thrown back and drawn to you; you gain a shield and allies harden."),
    ("p", "divine_favor", 1, 3, 4, '.req("guardian_angel", 1).bonus(lastStand(0.0, 0.05), saver(0.02))',
     "gem halo shield HOLY",
     "天使の寵愛", "Angel's Favor", "HPが減るほど受けるダメージが減り、スタミナ消費も減る。",
     "The lower your health, the less damage you take; skills cost less stamina."),

    ("u", "sanctum_of_light", 1, 0, 5,
     '.req("resurrection_hymn", 1).cd(140).theme(HOLY).act(30, 26, 36, pause(8))'
     '.fx(healZone(0, CASTER, 0, 8.0, 12, 20, 0.05, 1.0), shieldAllies(0, 0.4, 240, 12),'
     ' allies(0, ATTACK_UP, 0.2, 240, 12), cleanse(0, 12), revive(0, 12, 0.3), vis(START, "cast"), vis(0, "sigil"),'
     ' vis(0, "pillar"), vis(0, "aura"))',
     "sun pillar heal LIGHT",
     "光の聖域", "Sanctum of Light",
     "【必殺技】大地に光の聖域を開く。倒れた仲間を蘇らせ、仲間を厚いシールドと力の加護で包み、状態異常を払う。聖域は長く敵を焼き、仲間を癒し続ける。",
     "[Ultimate] Open a sanctum of light: the fallen rise, allies are wrapped in shields and strength and cleansed, "
     "and the sanctum keeps burning foes and healing friends for a long while."),
    ("p", "saints_heart", 1, 1, 5, '.req("beacon", 3).bonus(stats(mods().healing(0.1).healthPercent(0.03)))',
     "heart nova heal LIGHT",
     "聖者の心", "Saint's Heart", "回復量と最大HPが大きく上がる。", "Greatly raises healing and maximum health."),
    ("a", "crusade", 1, 2, 5,
     '.req("holy_bulwark", 3).cd(60).theme(HOLY).act(20, 16, 24, pause(6))'
     '.fx(allies(0, ATTACK_UP, 0.25, 240, 12), allies(0, CRIT_UP, 0.1, 240, 12), allies(0, SPEED, 0, 240, 12),'
     ' vis(0, "roar"), vis(0, "aura"))',
     "banner rain up HOLY",
     "聖戦", "Crusade",
     "聖戦を宣言する。広い範囲の仲間の攻撃力・会心率・移動速度を大きく上げる。",
     "Declare the crusade: allies in a wide circle strike harder, crit more often and move faster."),
    ("p", "aegis_of_heaven", 1, 3, 5, '.req("divine_favor", 3).bonus(stats(mods().defense(8).guard(0.02)))',
     "shield_cross glow up LIGHT",
     "天の神盾", "Aegis of Heaven", "防御力とガード性能が大きく上がる。", "Greatly raises defense and guard."),
]
