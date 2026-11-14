"""剣聖 (Sword Saint): the spellblade who became one with the sword. 剣技 is flawless technique — the draw, the
returning cut, the counter, a thousand cuts; 剣気 sends the blade's spirit out — flying slashes, floating swords,
a rain and a prison of blades."""

TITLE_JA = "剣聖"
TITLE_EN = "Sword Saint"
SUMMARY = "Flawless sword technique and the sword's spirit cast far beyond the blade."
DAMAGE = "MAGIC"
BRANCHES = [("剣技", "Swordcraft"), ("剣気", "Sword Spirit")]

NODES = [
    # ------------------------------------------------------------------ 0: 剣技
    ("a", "iai_draw", 0, 0, 1,
     '.cd(8).theme(STEEL).act(18, 14, 14, pause(2), hit(8, arc(4.2, 60), 2.8, 0.4))'
     '.fx(self(START, CRIT_UP, 0.3, 18), vis(START, "cast"), vis(1, "slash"), vis(1, "impact"))',
     "katana arc crit STEEL",
     "居合抜き", "Iai Draw",
     "鞘から抜き放つ一瞬の一太刀。この一撃は会心が出やすい。",
     "One cut in the instant the blade leaves its sheath; it lands critically far more often."),
    ("a", "flowing_cut", 0, 1, 1,
     '.cd(9).theme(WIND).act(20, 16, 15, hits(4, 4, 3, arc(3.6, 70), 0.9, 0.2))'
     '.fx(dash(START, 3), vis(0, "slash"), vis(2, "slash"))',
     "katana waves x3 WIND",
     "流水斬り", "Flowing Cut",
     "水が流れるように踏み込み、途切れなく三度斬りつける。",
     "Step in like flowing water and cut three times without a pause."),
    ("p", "sword_heart", 0, 2, 1, '.bonus(stats(mods().crit(0.015).magic(0.01)))',
     "heart - crit STEEL",
     "剣心", "Sword Heart", "会心率と魔法攻撃力が上がる。", "Raises critical chance and magic power."),
    ("a", "go_no_sen", 0, 3, 1,
     '.cd(14).theme(STEEL).act(22, 18, 14, pause(3), hit(14, circle(3.6), 2.6, 1.0))'
     '.fx(self(START, DEFENSE_UP, 0.7, 14), vis(START, "shield"), vis(1, "spin"))',
     "katana ring shield STEEL",
     "後の先", "Go no Sen",
     "相手の攻撃を受けきってから返す構え。構えの間は受けるダメージが大きく減り、そのあと周りを斬り払う。",
     "A stance that answers the enemy's blow: while it holds you take far less damage, then you cut down all "
     "around you."),

    ("a", "swallow_return", 0, 0, 2,
     '.req("iai_draw", 3).cd(12).theme(WIND).act(18, 14, 16, hit(4, arc(3.8, 70), 1.8, 0.2), hit(9, arc(3.8, 70), 1.8, 0.6))'
     '.fx(afflict(1, VULNERABLE, 80, 0.15), vis(0, "slash"), vis(1, "slash"))',
     "crescent arc2 x2 WIND",
     "燕返し", "Swallow's Return",
     "振り下ろした刃を燕のように返して斬り上げる。二の太刀を受けた敵は守りが崩れる。",
     "Bring the blade down, then turn it back like a swallow and cut up again; the second cut breaks the guard."),
    ("p", "still_water", 0, 1, 2, '.req("sword_heart", 1).bonus(stats(mods().crit(0.01)), cooldown(0.01))',
     "drop waves crit FROST",
     "明鏡止水", "Still Water", "会心率が上がり、スキルの再使用時間が短くなる。", "Raises critical chance; skills recover faster."),
    ("a", "piercing_flash", 0, 2, 2,
     '.req("flowing_cut", 1).cd(12).theme(STEEL).act(18, 14, 16, hit(7, line(6.0, 1.0), 3.0, 0.8))'
     '.fx(dash(START, 5), afflict(0, VULNERABLE, 80, 0.12), vis(START, "blink"), vis(0, "thrust"))',
     "sword speed vuln STEEL",
     "刺突一閃", "Piercing Flash",
     "一瞬で間合いを詰めて鋭く突き通す。貫かれた敵は守りが崩れる。",
     "Close in an instant and drive the point clean through; whoever it pierces loses their guard."),
    ("p", "blade_discipline", 0, 3, 2, '.req("go_no_sen", 1).bonus(stats(mods().critDamage(0.06)))',
     "greatsword - crit STEEL",
     "剣の鍛錬", "Blade Discipline", "会心ダメージが上がる。", "Raises critical damage."),

    ("a", "eight_directions", 0, 0, 3,
     '.req("swallow_return", 1).cd(16).theme(STEEL).act(24, 20, 20, hits(4, 2, 8, circle(4.0), 0.5, 0.1))'
     '.fx(vis(0, "spin"), vis(4, "slash"), vis(7, "impact"))',
     "katana x x7 STEEL",
     "八方斬り", "Eight Directions",
     "八つの方角すべてへ瞬く間に斬りつけ、周りの敵を刻む。",
     "Cut toward all eight directions in the blink of an eye, carving every foe around you."),
    ("a", "moon_shadow_cut", 0, 1, 3,
     '.req("still_water", 1).cd(16).theme(SHADOW).act(22, 18, 20, hit(10, beam(8.0, 1.6), 3.2, 0.6))'
     '.fx(afflict(0, BLIND, 60, 0), vis(START, "cast"), vis(0, "arcane_slash"))',
     "moon beam blind SHADOW",
     "月影斬", "Moon Shadow Cut",
     "月の影のような斬撃を放ち、一直線上の敵を斬り裂いて目をくらませる。",
     "Loose a cut like the shadow of the moon, slicing every foe in its line and leaving them blinded."),
    ("p", "economy_of_motion", 0, 2, 3, '.req("blade_discipline", 1).bonus(saver(0.02), stats(mods().dodgeCost(0.97)))',
     "boot glow stamina WIND",
     "無駄なき型", "Economy of Motion", "スキルと回避のスタミナ消費が減る。", "Skills and dodges cost less stamina."),
    ("a", "zanshin", 0, 3, 3,
     '.req("piercing_flash", 1).cd(16).theme(STEEL).act(20, 16, 18, hit(10, arc(4.0, 80), 2.4, 0.6))'
     '.fx(retreat(START, 4), self(START, EVASION, 0.5, 20), vis(START, "blink"), vis(0, "slash"))',
     "eye arc - STEEL",
     "残心", "Zanshin",
     "一歩退いて攻撃をかわし、すぐさま斬り返す。退いている間は攻撃をかわしやすい。",
     "Step back from the blow and cut back at once; while you step away, attacks rarely find you."),

    ("a", "heaven_splitter", 0, 0, 4,
     '.req("eight_directions", 1).cd(22).theme(STEEL).act(26, 22, 24, hit(16, ahead(2.0, 3.0), 4.2, 1.2))'
     '.fx(leap(START, 3, 3.0), afflict(0, LAUNCH, 1, 0.6), vis(START, "blink"), vis(0, "smite"), vis(0, "slash"))',
     "greatsword smite launch STEEL",
     "天割り", "Heaven Splitter",
     "跳び上がり、天を割るような一太刀を振り下ろして敵を打ち上げる。",
     "Leap and bring down a cut that seems to split the sky, throwing the foe into the air."),
    ("p", "way_of_the_sword", 0, 1, 4, '.req("economy_of_motion", 1).bonus(power(0.03))',
     "katana halo star STEEL",
     "剣の道", "Way of the Sword", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "thousand_cuts", 0, 2, 4,
     '.req("moon_shadow_cut", 1).cd(24).theme(WIND).act(28, 24, 24, hits(4, 2, 10, arc(3.8, 90), 0.36, 0.05))'
     '.fx(afflict(9, BLEED, 80, 0.4), vis(0, "blade_storm"), vis(9, "slash"))',
     "katana claws bleed WIND",
     "千本斬り", "A Thousand Cuts",
     "見えない速さで前方を十度斬り刻む。最後の一太刀で深い傷を負わせる。",
     "Carve the space ahead ten times faster than the eye can follow; the last cut leaves a deep wound."),
    ("p", "no_mind", 0, 3, 4, '.req("zanshin", 1).bonus(stats(mods().crit(0.02)))',
     "eye halo crit ARCANE",
     "無念無想", "No Mind", "会心率が上がる。", "Raises critical chance."),

    ("u", "infinite_stroke", 0, 0, 5,
     '.req("heaven_splitter", 3).cd(120).theme(STEEL)'
     '.act(44, 40, 40, seq(pause(8), hits(14, 2, 6, circle(6.0), 0.6, 0.0), hit(32, circle(8.0), 5.0, 1.6)))'
     '.fx(self(0, DEFENSE_UP, 0.5, 30), afflict(7, VULNERABLE, 160, 0.3), vis(START, "cast"), vis(1, "blade_storm"),'
     ' vis(4, "slash"), vis(7, "slash"), vis(7, "nova"))',
     "katana nova star LIGHT",
     "奥義・無限一刀", "Secret Art: Infinite Stroke",
     "【必殺技】心を無にして刀を納め、周りの敵を六度瞬く間に斬り、最後にただ一刀で斬り払う。守りを砕かれた敵は大きなダメージを受け続ける。",
     "[Ultimate] Empty the mind and sheathe the blade, then cut every foe around you six times in a blink and "
     "finish with a single stroke that shatters their guard."),
    ("p", "pinnacle_of_the_sword", 0, 1, 5, '.req("way_of_the_sword", 1).bonus(stats(mods().critDamage(0.08)), power(0.01))',
     "crown glow crit STEEL",
     "剣の極み", "Pinnacle of the Sword", "会心ダメージとスキルの威力が上がる。", "Raises critical damage and skill power."),
    ("a", "dragon_cleaver", 0, 2, 5,
     '.req("thousand_cuts", 3).cd(30).theme(STEEL).act(26, 22, 26, hit(10, line(8.0, 2.0), 4.6, 1.0))'
     '.fx(dash(START, 8), vis(START, "blink"), vis(0, "slash"), vis(0, "impact"))',
     "greatsword speed x2 STEEL",
     "竜断ち", "Dragon Cleaver",
     "竜をも断つと伝わる一太刀。大きく踏み込み、通り道の敵を両断する。",
     "The cut said to fell dragons: one great stride forward, and everything in your path is cleft in two."),
    ("p", "serene_blade", 0, 3, 5, '.req("no_mind", 3).bonus(evasion(0.015))',
     "katana mist - FROST",
     "静寂の刃", "Serene Blade", "攻撃を回避しやすくなる。", "You dodge attacks more often."),

    # ------------------------------------------------------------------ 1: 剣気
    ("a", "flying_slash", 1, 0, 1,
     '.cd(8).theme(WIND).act(18, 14, 14, shot(7, orb("crescent", 2.4, 24).withPierce(4), 2.0, 0.4))'
     '.fx(vis(START, "cast"), vis(0, "arcane_slash"))',
     "crescent speed - WIND",
     "飛ぶ斬撃", "Flying Slash",
     "剣気を刃にして飛ばし、一直線上の敵を斬り裂く。",
     "Shape your sword spirit into a blade and send it flying through the foes in its line."),
    ("a", "sword_spirit_release", 1, 1, 1,
     '.cd(26).theme(ARCANE).act(12, 8, 12, pause(3))'
     '.fx(self(0, ATTACK_UP, 0.2, 200), self(0, CRIT_UP, 0.1, 200), vis(0, "aura"), vis(0, "flame_blade"))',
     "katana glow up ARCANE",
     "剣気解放", "Release the Sword Spirit",
     "内に秘めた剣気を解き放つ。しばらくの間、与えるダメージと会心率が上がる。",
     "Release the sword spirit held within: for a while you deal more damage and crit more often."),
    ("p", "spirit_edge", 1, 2, 1, '.bonus(stats(mods().magic(0.03)))',
     "sword - - ARCANE",
     "気の刃", "Spirit Edge", "魔法攻撃力が上がる。", "Raises magic power."),
    ("a", "floating_swords", 1, 3, 1,
     '.cd(12).theme(ARCANE).act(22, 18, 16, hits(4, 6, 3, circle(3.0), 0.9, 0.2))'
     '.fx(vis(0, "blade_storm"), vis(2, "spin"))',
     "sword orbit x3 ARCANE",
     "浮遊剣", "Floating Swords",
     "剣気で三本の剣を浮かべて周りを回らせ、近くの敵を三度斬りつける。",
     "Raise three swords of spirit that circle you, cutting the nearby foes three times."),

    ("a", "cross_wave", 1, 0, 2,
     '.req("flying_slash", 3).cd(12).theme(WIND).act(20, 16, 16, shot(8, orb("crescent", 2.0, 30).withFan(2, 12), 1.6, 0.4))'
     '.fx(afflict(0, VULNERABLE, 80, 0.12), vis(START, "cast"), vis(0, "arcane_slash"))',
     "crescent x vuln WIND",
     "十字剣波", "Cross Wave",
     "十字に重ねた二つの剣波を放つ。当たった敵は守りが崩れる。",
     "Send two waves of the sword out crossed; the foes they strike lose their guard."),
    ("p", "ki_circulation", 1, 1, 2, '.req("spirit_edge", 1).bonus(stats(mods().stamina(5)), saver(0.02))',
     "orb waves stamina ARCANE",
     "気の巡り", "Ki Circulation", "最大スタミナが上がり、スキルのスタミナ消費が減る。",
     "Raises maximum stamina; skills cost less stamina."),
    ("a", "rain_of_swords", 1, 2, 2,
     '.req("floating_swords", 1).cd(16).theme(ARCANE)'
     '.act(24, 20, 18, shots(6, 3, 5, orb("crescent", 1.6, 40).fallingFromAbove(), 0.9, 0.2))'
     '.fx(vis(START, "cast"), vis(0, "rain"))',
     "sword rain x5 ARCANE",
     "剣の雨", "Rain of Swords",
     "剣気の剣を空に浮かべ、狙った場所へ五本降らせる。",
     "Raise swords of spirit into the sky and let five fall where you aim."),
    ("p", "blade_ward", 1, 3, 2, '.req("floating_swords", 1).bonus(stats(mods().guard(0.02).defense(3)))',
     "shield_small orbit shield ARCANE",
     "剣の結界", "Blade Ward", "ガード性能と防御力が上がる。", "Raises guard and defense."),

    ("a", "phantom_swords", 1, 0, 3,
     '.req("cross_wave", 1).cd(18).theme(SHADOW).act(24, 20, 20, shots(4, 3, 5, orb("crescent", 3.0, 20), 0.7, 0.1))'
     '.fx(afflict(4, VULNERABLE, 100, 0.15), vis(START, "shadow"), vis(4, "arcane_slash"))',
     "sword speed x5 SHADOW ghost",
     "幻影剣", "Phantom Swords",
     "幻の剣を五本続けて撃ち出す。最後の一本を受けた敵は守りが崩れる。",
     "Fire five phantom swords one after another; the foe struck by the last loses its guard."),
    ("a", "spirit_burst", 1, 1, 3,
     '.req("ki_circulation", 1).cd(20).theme(ARCANE).act(18, 14, 18, hit(8, circle(5.0), 2.8, 1.6))'
     '.fx(vis(0, "nova"), vis(0, "burst"))',
     "orb burst - ARCANE",
     "剣気爆発", "Spirit Burst",
     "溜めた剣気を一気に爆発させ、周りの敵を吹き飛ばす。",
     "Burst the sword spirit you have gathered all at once, blasting the foes around you back."),
    ("p", "ringing_steel", 1, 2, 3, '.req("ki_circulation", 1).bonus(proc(BLEED, 0.03, 60, 0.3, true))',
     "note glow bleed STEEL",
     "斬鉄の響き", "Ringing Steel", "近接攻撃が一定確率で敵を出血させる。", "Your melee hits may leave the foe bleeding."),
    ("a", "heaven_piercing_blade", 1, 3, 3,
     '.req("rain_of_swords", 1).cd(18).theme(WIND).act(20, 16, 20, shot(8, orb("crescent", 3.4, 24).withPierce(16), 3.0, 0.6))'
     '.fx(vis(START, "cast"), vis(0, "ray"))',
     "lance beam - WIND",
     "衝天剣", "Heaven-Piercing Blade",
     "天を衝くほど鋭い剣気を放ち、一直線上の敵すべてを貫く。",
     "Loose a sword spirit sharp enough to pierce the heavens; it runs through every foe in its line."),

    ("a", "rising_dragon", 1, 0, 4,
     '.req("phantom_swords", 1).cd(22).theme(WIND).act(22, 18, 22, hit(9, circle(4.0), 3.0, 0.6))'
     '.fx(afflict(0, LAUNCH, 1, 0.9), self(0, ATTACK_UP, 0.1, 100), vis(0, "pillar"), vis(0, "slash"))',
     "snake pillar launch WIND",
     "昇竜剣", "Rising Dragon",
     "昇る竜のように剣気を巻き上げ、周りの敵を高く打ち上げる。勢いでしばらく力が増す。",
     "Spiral the sword spirit up like a rising dragon, throwing the foes around you high; the momentum stays "
     "with you a while."),
    ("p", "spirit_body", 1, 1, 4, '.req("ringing_steel", 1).bonus(stats(mods().healthPercent(0.03)))',
     "heart halo heart ARCANE",
     "気の体", "Spirit Body", "最大HPが上がる。", "Raises maximum health."),
    ("a", "prison_of_blades", 1, 2, 4,
     '.req("spirit_burst", 1).cd(26).theme(ARCANE).act(18, 14, 22, pause(5))'
     '.fx(zone(0, LOOK, 10.0, 4.0, 0, 8, 8, 0.7, BLEED, 60, 0.3), vis(START, "cast"), vis(0, "blade_storm"))',
     "sword zone bleed ARCANE",
     "剣牢", "Prison of Blades",
     "狙った場所に剣気の牢を作る。しばらくの間、中の敵は無数の刃に斬られて出血し続ける。",
     "Build a prison of sword spirit where you look; for a while foes inside are cut by countless blades and bleed."),
    ("p", "fighting_spirit", 1, 3, 4, '.req("heaven_piercing_blade", 1).bonus(lastStand(0.06, 0.0))',
     "flame glow up FIRE",
     "闘気", "Fighting Spirit", "HPが減るほど与えるダメージが増える。", "The lower your health, the harder you strike."),

    ("u", "myriad_swords", 1, 0, 5,
     '.req("rising_dragon", 3).cd(120).theme(ARCANE)'
     '.act(50, 46, 40, seq(pause(8), shots(14, 2, 8, orb("crescent", 2.8, 30), 0.6, 0.1), hit(40, circle(8.0), 4.4, 1.4)))'
     '.fx(afflict(8, BLEED, 100, 0.4), afflict(9, VULNERABLE, 160, 0.3), vis(START, "sigil"), vis(0, "blade_storm"),'
     ' vis(1, "arcane_slash"), vis(9, "nova"), vis(9, "slash"))',
     "sword nova x7 ARCANE",
     "奥義・万剣帰一", "Secret Art: Myriad Swords as One",
     "【必殺技】無数の剣気の剣を呼び出して次々と撃ち出し、最後にすべての剣を一つに束ねて周りを薙ぎ払う。敵は深く傷つき、守りを砕かれる。",
     "[Ultimate] Summon countless swords of spirit and fire them one after another, then bind them all into one "
     "and sweep everything around you; the foes bleed deeply and their guard shatters."),
    ("p", "sword_spirit_master", 1, 1, 5, '.req("spirit_body", 1).bonus(stats(mods().magic(0.03).crit(0.01)))',
     "crown halo up ARCANE",
     "剣気の達人", "Master of the Sword Spirit", "魔法攻撃力と会心率が上がる。", "Raises magic power and critical chance."),
    ("a", "heaven_and_earth_flash", 1, 2, 5,
     '.req("prison_of_blades", 3).cd(32).theme(ARCANE).act(28, 24, 28, hit(12, beam(12.0, 2.4), 4.4, 1.2))'
     '.fx(vis(START, "cast"), vis(0, "arcane_slash"), vis(0, "ray"))',
     "greatsword beam x2 ARCANE",
     "天地一閃", "Flash of Heaven and Earth",
     "天と地を一つに結ぶような巨大な剣気を放ち、はるか先まで一直線上の敵を斬り裂く。",
     "Loose a vast sword spirit that seems to join heaven and earth, cleaving every foe far down its line."),
    ("p", "unbroken_soul", 1, 3, 5, '.req("fighting_spirit", 3).bonus(lastStand(0.0, 0.04))',
     "sword halo shield STEEL",
     "折れぬ剣魂", "Unbroken Soul", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),
]
