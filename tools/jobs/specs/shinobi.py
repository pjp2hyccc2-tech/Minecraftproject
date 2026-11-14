"""忍者 (Shinobi): the assassin who became a ninja. 忍術 commands the five elements, clones and substitution;
影刃 is the craft of kunai, shuriken and the cut nobody sees."""

TITLE_JA = "忍者"
TITLE_EN = "Shinobi"
SUMMARY = "Elemental ninjutsu, clones and thrown steel; never where the enemy strikes."
DAMAGE = "PHYSICAL"
BRANCHES = [("忍術", "Ninjutsu"), ("影刃", "Shadow Blade")]

NODES = [
    # ------------------------------------------------------------------ 0: 忍術
    ("a", "flame_orb", 0, 0, 1,
     '.cd(9).theme(FIRE).act(18, 14, 15, shot(7, orb("fireball", 1.5, 30).withExplosion(2.6), 2.4, 0.6))'
     '.fx(afflict(0, BURN, 60, 0), vis(START, "cast"), vis(0, "burst"))',
     "scroll flamewall burn FIRE",
     "火遁・焔玉", "Fire Arts: Flame Orb",
     "印を結んで口から火の玉を吐く。着弾すると炸裂し、周りの敵を燃やす。",
     "Weave the seals and breathe out a ball of fire; it bursts where it lands and sets the foes around ablaze."),
    ("a", "substitution", 0, 1, 1,
     '.cd(14).theme(WIND).act(10, 6, 12, pause(2))'
     '.fx(retreat(START, 6), self(0, EVASION, 0.6, 30), vanish(0, 8), vis(START, "smoke"), vis(0, "blink"))',
     "log mist - WIND",
     "変わり身の術", "Art of Substitution",
     "丸太と入れ替わって後ろへ跳ぶ。敵は見失い、しばらく攻撃をかわしやすくなる。",
     "Swap places with a log and spring back: the enemy loses you, and for a moment you dodge almost anything."),
    ("p", "inner_breath", 0, 2, 1, '.bonus(saver(0.02), stats(mods().stamina(4)))',
     "hand - stamina WIND",
     "気脈", "Inner Breath", "スキルのスタミナ消費が減り、最大スタミナが上がる。",
     "Skills cost less stamina and your stamina grows."),
    ("a", "torrent", 0, 3, 1,
     '.cd(10).theme(FROST).act(18, 14, 15, hit(7, line(7.0, 1.6), 2.2, 1.4))'
     '.fx(afflict(0, SLOW, 80, 2), vis(START, "cast"), vis(0, "breath"))',
     "drop waves slow FROST",
     "水遁・激流", "Water Arts: Torrent",
     "前方へ激流を放ち、一直線上の敵を押し流して足を鈍らせる。",
     "Loose a torrent ahead: every foe in its line is swept back and slowed."),

    ("a", "duplication", 0, 0, 2,
     '.req("substitution", 3).cd(20).theme(SHADOW).act(24, 20, 18, hits(4, 4, 4, circle(3.2), 0.8, 0.2))'
     '.fx(self(0, EVASION, 0.25, 200), vis(START, "shadow"), vis(1, "spin"), vis(3, "blink"))',
     "hood - x4 SHADOW ghost",
     "分身の術", "Art of Duplication",
     "分身を生み出して周りを四度斬りつける。しばらくの間、攻撃をかわしやすくなる。",
     "Split into shadows that cut all around you four times; for a while attacks find you less often."),
    ("p", "hand_seals", 0, 1, 2, '.req("inner_breath", 1).bonus(cooldown(0.02), stats(mods().crit(0.01)))',
     "hand glow clock ARCANE",
     "印結び", "Hand Seals", "スキルの再使用時間が短くなり、会心率が上がる。",
     "Skills recover faster and your critical chance rises."),
    ("a", "purple_lightning", 0, 2, 2,
     '.req("flame_orb", 3).cd(14).theme(THUNDER).act(20, 16, 18, hit(8, line(6.0, 1.4), 3.0, 0.8))'
     '.fx(dash(START, 6), afflict(0, STUN, 20, 0), vis(START, "lightning"), vis(0, "thrust"))',
     "hand speed stun THUNDER",
     "雷遁・紫電", "Thunder Arts: Violet Lightning",
     "手に雷をまとって一直線に駆け抜け、貫いた敵をしびれさせる。",
     "Wrap your hand in lightning and dart straight through the foe; whatever you pierce is stunned."),
    ("p", "mist_walker", 0, 3, 2, '.req("torrent", 1).bonus(evasion(0.01), stats(mods().moveSpeed(0.01)))',
     "boot mist speed FROST",
     "霧歩き", "Mist Walker", "攻撃を回避しやすくなり、移動速度が上がる。", "You dodge more often and move faster."),

    ("a", "sickle_wind", 0, 0, 3,
     '.req("duplication", 1).cd(14).theme(WIND).act(20, 16, 18, hit(8, beam(8.0, 1.2), 2.6, 0.6))'
     '.fx(afflict(0, VULNERABLE, 100, 0.15), vis(START, "cast"), vis(0, "slash"))',
     "crescent beam vuln WIND",
     "風遁・鎌鼬", "Wind Arts: Sickle Weasel",
     "真空の刃を飛ばし、一直線上の敵を切り裂いて守りを崩す。",
     "Send a blade of empty air down a line, slicing every foe in it and laying their guard open."),
    ("a", "hidden_mist", 0, 1, 3,
     '.req("mist_walker", 1).cd(30).theme(FROST).act(14, 10, 16, pause(4))'
     '.fx(vanish(0, 10), self(0, INVISIBILITY, 0, 80), self(0, SPEED, 1, 80), vis(0, "mist"), vis(0, "smoke"))',
     "moon mist - FROST",
     "霧隠れの術", "Art of the Hidden Mist",
     "濃い霧に紛れて姿を消す。敵は狙いを失い、しばらく素早く動ける。",
     "Vanish into thick mist: enemies lose their target and for a while you move swiftly."),
    ("p", "shinobi_secrets", 0, 2, 3, '.req("hand_seals", 1).bonus(power(0.03))',
     "scroll glow star ARCANE",
     "忍の極意", "Secrets of the Shinobi", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "stone_spears", 0, 3, 3,
     '.req("torrent", 3).cd(16).theme(EARTH).act(26, 22, 20, hits(8, 5, 3, ahead(2.2, 3.0), 1.4, 0.6))'
     '.fx(afflict(2, LAUNCH, 1, 0.7), vis(0, "quake"), vis(2, "pillar"))',
     "spear crack launch EARTH",
     "土遁・岩槍", "Earth Arts: Stone Spears",
     "前方の地面から岩の槍を三度突き上げる。最後の槍が敵を打ち上げる。",
     "Drive spears of rock up from the ground ahead three times; the last one throws the foe into the air."),

    ("a", "fire_dragon", 0, 0, 4,
     '.req("sickle_wind", 1).cd(22).theme(FIRE).act(26, 22, 24, hits(6, 4, 4, arc(6.0, 35), 1.0, 0.3))'
     '.fx(afflict(3, BURN, 80, 0), vis(START, "cast"), vis(0, "breath"), vis(3, "burst"))',
     "snake flamewall x4 FIRE",
     "火遁・火龍の舞", "Fire Arts: Dragon Dance",
     "炎の龍を吐き出し、前方を四度焼き払う。最後の炎が敵を燃え上がらせる。",
     "Breathe out a dragon of flame that sweeps the cone ahead four times; the last flame sets the foe alight."),
    ("p", "phantom_body", 0, 1, 4, '.req("shinobi_secrets", 1).bonus(evasion(0.015), stats(mods().crit(0.01)))',
     "hood glow - SHADOW ghost",
     "幻身", "Phantom Body", "攻撃を回避しやすくなり、会心率が上がる。",
     "You dodge more often and land more critical hits."),
    ("a", "hundred_thunders", 0, 2, 4,
     '.req("purple_lightning", 3).cd(24).theme(THUNDER).act(22, 18, 22, hit(10, circle(4.5), 2.6, 0.8))'
     '.fx(afflict(0, STUN, 30, 0), self(0, SPEED, 1, 100), vis(0, "lightning"), vis(0, "nova"))',
     "bolt nova stun THUNDER",
     "雷遁・百雷", "Thunder Arts: Hundred Thunders",
     "全身から雷を放ち、周りの敵をしびれさせる。雷の勢いでしばらく素早くなる。",
     "Lightning bursts from your whole body: foes around are stunned and the charge leaves you quicker."),
    ("p", "five_phases", 0, 3, 4, '.req("stone_spears", 1).bonus(stats(mods().physical(0.02).critDamage(0.05)))',
     "orbs3 orbit - ARCANE",
     "五行の理", "Law of the Five Phases", "物理攻撃力と会心ダメージが上がる。",
     "Raises physical power and critical damage."),

    ("u", "all_creation", 0, 0, 5,
     '.req("fire_dragon", 3).cd(120).theme(ARCANE)'
     '.act(48, 44, 40, hit(8, circle(6.0), 1.4, 0.3), hit(14, circle(6.0), 1.4, 0.3), hit(20, circle(6.0), 1.4, 0.3),'
     ' hit(26, circle(6.0), 1.4, 0.3), hit(34, circle(7.0), 2.6, 1.5))'
     '.fx(afflict(0, BURN, 80, 0), afflict(1, SLOW, 80, 3), afflict(2, STUN, 30, 0), afflict(3, LAUNCH, 1, 0.6),'
     ' afflict(4, VULNERABLE, 160, 0.3), vis(START, "sigil"), vis(0, "burst"), vis(1, "storm"), vis(2, "lightning"),'
     ' vis(3, "vortex"), vis(4, "quake"))',
     "scroll nova star ARCANE",
     "五遁・森羅万象", "Five Arts: All Creation",
     "【必殺技】火・水・雷・風・土の五つの忍術を立て続けに放つ。燃やし、鈍らせ、しびれさせ、打ち上げ、最後の大地の一撃で守りを砕く。",
     "[Ultimate] Unleash fire, water, thunder, wind and earth in a single breath: foes burn, slow, are stunned, thrown "
     "up, and the final blow of earth shatters their guard."),
    ("p", "shinobi_spirit", 0, 1, 5, '.req("phantom_body", 1).bonus(stats(mods().physical(0.03).moveSpeed(0.02)))',
     "mask glow up SHADOW",
     "忍魂", "Shinobi Spirit", "物理攻撃力と移動速度が上がる。", "Raises physical power and movement speed."),
    ("a", "great_falls", 0, 2, 5,
     '.req("hundred_thunders", 1).cd(32).theme(FROST).act(30, 26, 30, hit(14, ahead(4.0, 5.0), 3.6, 2.0))'
     '.fx(afflict(0, SLOW, 100, 3), vis(START, "cast"), vis(0, "rain"), vis(0, "quake"))',
     "drop rain slow FROST",
     "水遁・大瀑布", "Water Arts: Great Falls",
     "前方に滝のような大水を叩き落とし、敵を押し潰して足を止める。",
     "Bring a waterfall crashing down ahead, crushing the foes beneath it and bogging them down."),
    ("p", "village_master", 0, 3, 5, '.req("five_phases", 3).bonus(cooldown(0.02), saver(0.02))',
     "scroll halo clock EARTH",
     "隠れ里の長", "Master of the Hidden Village", "スキルの再使用時間が短くなり、スタミナ消費が減る。",
     "Skills recover faster and cost less stamina."),

    # ------------------------------------------------------------------ 1: 影刃
    ("a", "shuriken", 1, 0, 1,
     '.cd(7).theme(STEEL).act(18, 14, 14, shots(2, 3, 4, orb("knife", 3.4, 18), 0.6, 0.0))'
     '.fx(afflict(3, BLEED, 60, 0.3), vis(START, "cast"))',
     "shuriken speed x4 STEEL",
     "手裏剣連打", "Shuriken Barrage",
     "手裏剣を四枚続けざまに投げる。最後の一枚が深く刺さり、出血させる。",
     "Throw four shuriken in quick succession; the last one bites deep and leaves the foe bleeding."),
    ("a", "kunai_lunge", 1, 1, 1,
     '.cd(9).theme(SHADOW).act(16, 12, 14, hit(6, line(6.0, 1.2), 2.2, 0.6))'
     '.fx(dash(START, 6), afflict(0, VULNERABLE, 80, 0.12), vis(START, "blink"), vis(0, "thrust"))',
     "kunai speed vuln SHADOW",
     "苦無突き", "Kunai Lunge",
     "苦無を構えて一気に間合いを詰め、貫いた敵の守りを崩す。",
     "Close the distance in one bound with your kunai; the foes you pierce lose their guard."),
    ("p", "silent_steps", 1, 2, 1, '.bonus(stats(mods().moveSpeed(0.015).dodgeCost(0.97)))',
     "boot - speed SHADOW",
     "忍び足", "Silent Steps", "移動速度が上がり、回避のスタミナ消費が減る。",
     "You move faster and dodging costs less stamina."),
    ("a", "chain_sickle", 1, 3, 1,
     '.cd(12).theme(STEEL).act(18, 14, 14, hit(7, line(7.0, 1.0), 1.6, 0.0))'
     '.fx(afflict(0, PULL, 1, 1.0), afflict(0, SLOW, 60, 2), vis(0, "chains"))',
     "chains arc pull STEEL",
     "鎖鎌", "Chain Sickle",
     "鎖鎌の分銅を投げつけ、絡め取った敵を手元へ引き寄せて足を鈍らせる。",
     "Hurl the weighted chain of your sickle: whatever it catches is dragged to you and slowed."),

    ("a", "shadow_draw", 1, 0, 2,
     '.req("kunai_lunge", 1).cd(12).theme(SHADOW).act(22, 18, 16, pause(4), hit(12, arc(4.0, 80), 3.2, 0.6))'
     '.fx(self(START, CRIT_UP, 0.3, 20), vis(START, "shadow"), vis(1, "slash"))',
     "dagger arc crit SHADOW",
     "影居合", "Shadow Draw",
     "影に身を沈めて刃を抜き放つ。この一太刀は会心が出やすい。",
     "Sink into the shadow and draw: this one cut lands critically far more often."),
    ("p", "honed_edge", 1, 1, 2, '.req("silent_steps", 1).bonus(stats(mods().critDamage(0.06)))',
     "kunai glow crit STEEL",
     "研ぎ澄まし", "Honed Edge", "会心ダメージが上がる。", "Raises critical damage."),
    ("a", "kunai_rain", 1, 2, 2,
     '.req("shuriken", 3).cd(16).theme(STEEL).act(22, 18, 18,'
     ' shots(4, 4, 3, orb("knife", 2.0, 30).withExplosion(2.4).fallingFromAbove(), 1.2, 0.2))'
     '.fx(vis(START, "cast"), vis(0, "rain"))',
     "knives_rain rain x3 STEEL",
     "苦無の雨", "Rain of Kunai",
     "空高く苦無を放ち、狙った場所へ三度降り注がせる。",
     "Throw kunai high into the sky; they come down three times where you aim."),
    ("p", "endurance", 1, 3, 2, '.req("chain_sickle", 1).bonus(stats(mods().crit(0.015)), healOnKill(0.01))',
     "heart - crit SHADOW",
     "忍耐", "Endurance", "会心率が上がり、敵を倒すとHPが回復する。",
     "Raises critical chance; defeating a foe restores your health."),

    ("a", "haze_cut", 1, 0, 3,
     '.req("shadow_draw", 3).cd(14).theme(SHADOW).act(20, 16, 18, hit(8, arc(4.0, 80), 2.6, 0.6))'
     '.fx(dash(START, 4), afflict(0, BLIND, 60, 0), vis(START, "smoke"), vis(0, "slash"))',
     "dagger mist blind SHADOW",
     "朧斬り", "Haze Cut",
     "煙の中から踏み込んで斬る。斬られた敵は目がくらむ。",
     "Step out of the smoke and cut; the foe you strike is left blinded."),
    ("a", "shadow_bind", 1, 1, 3,
     '.req("honed_edge", 1).cd(22).theme(SHADOW).act(18, 14, 18, hit(8, circle(4.0), 1.6, 0.0))'
     '.fx(afflict(0, STUN, 40, 0), afflict(0, SLOW, 80, 2), vis(0, "chains"), vis(0, "shadow"))',
     "chains glow stun SHADOW",
     "影縛り", "Shadow Snare",
     "自分の影を周りへ伸ばし、触れた敵を縛りつけて動きを止める。",
     "Spread your shadow around you; every foe it touches is bound fast and cannot move."),
    ("p", "killing_distance", 1, 2, 3, '.req("endurance", 1).bonus(stats(mods().crit(0.02)))',
     "target - crit STEEL",
     "必殺の間合い", "Killing Distance", "会心率が上がる。", "Raises critical chance."),
    ("a", "fuma_shuriken", 1, 3, 3,
     '.req("kunai_rain", 1).cd(16).theme(WIND).act(20, 16, 20, shot(8, orb("crescent", 1.6, 40).withPierce(10), 3.0, 0.8))'
     '.fx(vis(START, "spin"), vis(0, "ray"))',
     "shuriken spiral x2 WIND",
     "風魔手裏剣", "Great Wind Shuriken",
     "巨大な手裏剣を投げ放つ。唸りを上げて飛び、一直線上の敵をすべて切り裂く。",
     "Hurl a giant shuriken; it whirls through every foe in its path."),

    ("a", "shadow_strike", 1, 0, 4,
     '.req("haze_cut", 1).cd(22).theme(SHADOW).act(20, 16, 22, hit(8, ahead(2.2, 1.5), 4.0, 0.6))'
     '.fx(dash(START, 8), execute(0, 0.3, 0.8), vis(START, "blink"), vis(0, "thrust"))',
     "kunai beamd skull SHADOW",
     "影討ち", "Shadow Strike",
     "影から影へ跳んで敵の背に回り、急所を突く。弱った敵ほど大きなダメージ。",
     "Leap from shadow to shadow to the foe's back and stab the vital spot; the weaker it is, the deeper it cuts."),
    ("p", "night_hunter", 1, 1, 4, '.req("killing_distance", 1).bonus(stats(mods().physical(0.03)))',
     "moon - star SHADOW",
     "夜狩り", "Night Hunter", "物理攻撃力が上がる。", "Raises physical power."),
    ("a", "thousand_kunai", 1, 2, 4,
     '.req("fuma_shuriken", 1).cd(24).theme(STEEL).act(24, 20, 24, shots(4, 2, 8, orb("knife", 3.6, 16), 0.45, 0.0))'
     '.fx(afflict(7, VULNERABLE, 100, 0.15), vis(START, "cast"), vis(7, "burst"))',
     "knives7 speed x7 STEEL",
     "千本苦無", "Thousand Kunai",
     "目にも止まらぬ速さで苦無を八本投げる。最後の一本が刺さった敵は脆弱になる。",
     "Throw eight kunai faster than the eye can follow; the foe hit by the last one is left vulnerable."),
    ("p", "rending_edge", 1, 3, 4, '.req("killing_distance", 1).bonus(proc(BLEED, 0.03, 80, 0.3, true))',
     "daggers - bleed BLOOD",
     "裂刃", "Rending Edge", "近接攻撃が一定確率で敵を出血させる。", "Your melee hits may leave the foe bleeding."),

    ("u", "shadow_moon_dance", 1, 0, 5,
     '.req("shadow_strike", 3).cd(120).theme(SHADOW)'
     '.act(48, 44, 40, seq(pause(4), hits(8, 3, 8, circle(5.0), 0.7, 0.1), hit(36, circle(6.0), 3.0, 1.4)))'
     '.fx(self(0, EVASION, 0.5, 60), afflict(4, BLEED, 80, 0.4), afflict(9, VULNERABLE, 160, 0.3), vis(START, "shadow"),'
     ' vis(0, "smoke"), vis(2, "blade_storm"), vis(9, "slash"), vis(9, "burst"))',
     "moon claws x7 SHADOW",
     "奥義・影月乱舞", "Secret Art: Shadow Moon Dance",
     "【必殺技】月影に溶け、周りの敵を八度斬り刻んだあと、渾身の一太刀で薙ぎ払う。舞の間は攻撃をほとんど受けない。",
     "[Ultimate] Melt into the moonlit shadow, cut every foe around you eight times and finish with one sweeping "
     "blow. Attacks barely touch you while you dance."),
    ("p", "jonin", 1, 1, 5, '.req("night_hunter", 1).bonus(stats(mods().crit(0.015).critDamage(0.05)))',
     "mask halo crit STEEL",
     "上忍", "Jonin", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "cut_and_gone", 1, 2, 5,
     '.req("thousand_kunai", 3).cd(30).theme(SHADOW).act(24, 20, 26, hit(10, line(10.0, 1.6), 4.4, 0.4))'
     '.fx(dash(START, 10), afflict(0, BLEED, 100, 0.5), vis(START, "blink"), vis(0, "slash"))',
     "katana speed bleed SHADOW",
     "斬り捨て御免", "Cut and Gone",
     "一瞬で十歩先まで駆け抜け、通り道の敵すべてを斬り捨てる。斬られた敵は深く出血する。",
     "Flash ten paces ahead in an instant, cutting down everything in your way; they bleed deeply."),
    ("p", "shadowless", 1, 3, 5, '.req("rending_edge", 3).bonus(evasion(0.02))',
     "hood glow - SHADOW",
     "無影", "Shadowless", "攻撃を回避しやすくなる。", "You dodge attacks more often."),
]
