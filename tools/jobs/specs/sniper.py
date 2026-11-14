"""狙撃手 (Sniper): the ranger who never misses. 狙撃 is the one perfect shot — still, charged, armor-piercing, aimed at
the weak point; 戦術 wins the fight before it starts — flash and smoke, nets, marks, signals and fields of fire."""

TITLE_JA = "狙撃手"
TITLE_EN = "Sniper"
SUMMARY = "Charged, piercing, precise shots and the tactics of the battlefield."
DAMAGE = "RANGED"
BRANCHES = [("狙撃", "Marksmanship"), ("戦術", "Tactics")]

NODES = [
    # ------------------------------------------------------------------ 0: 狙撃
    ("a", "still_shot", 0, 0, 1,
     '.weapons(BOW).cd(8).theme(WIND).act(26, 22, 14, pause(4), shot(18, arrow(5.0), 3.0, 0.6))'
     '.fx(self(START, CRIT_UP, 0.25, 26), vis(START, "cast"), vis(1, "thrust"))',
     "bow glow crit WIND",
     "静止射撃", "Still Shot",
     "息を止めて狙いを定め、渾身の一矢を放つ。この一矢は会心が出やすい。",
     "Hold your breath, settle your aim and loose one perfect arrow; it lands critically far more often."),
    ("a", "armor_piercer", 0, 1, 1,
     '.weapons(BOW).cd(10).theme(STEEL).act(20, 16, 15, shot(9, arrow(4.6).withPierce(8), 2.2, 0.4))'
     '.fx(afflict(0, VULNERABLE, 100, 0.15), vis(START, "cast"), vis(0, "ray"))',
     "arrow1 beamd vuln STEEL",
     "徹甲矢", "Armor Piercer",
     "鎧を貫く鋼の矢を放つ。一直線上の敵を貫き、守りを崩す。",
     "Loose a steel arrow made to pierce armor; it runs through every foe in its line and breaks their guard."),
    ("p", "marksmans_breath", 0, 2, 1, '.bonus(stats(mods().crit(0.015).critDamage(0.04)))',
     "eye - crit WIND",
     "狙撃の呼吸", "Marksman's Breath", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "leg_shot", 0, 3, 1,
     '.weapons(BOW).cd(10).theme(EARTH).act(16, 12, 14, shot(6, arrow(4.4), 1.6, 0.2))'
     '.fx(afflict(0, SLOW, 120, 3), afflict(0, WEAKEN, 100, 0.12), vis(START, "cast"))',
     "arrow2 - slow EARTH",
     "脚撃ち", "Leg Shot",
     "敵の脚を正確に射抜き、長い間足を鈍らせて力を削ぐ。",
     "Put an arrow precisely through the foe's leg: it is slowed for a long while and its strength falters."),

    ("a", "weak_point", 0, 0, 2,
     '.req("still_shot", 3).weapons(BOW).cd(14).theme(BLOOD).act(22, 18, 16, shot(12, arrow(4.8), 2.6, 0.4))'
     '.fx(afflict(0, VULNERABLE, 160, 0.3), vis(START, "cast"), vis(0, "sigil"))',
     "target beamd vuln BLOOD",
     "弱点射抜き", "Weak Point",
     "敵の弱点を見抜いて射抜く。射抜かれた敵は長い間、受けるダメージが大きく増える。",
     "Find the foe's weak point and shoot it: for a long while it takes far more damage from everything."),
    ("p", "long_shot", 0, 1, 2, '.req("marksmans_breath", 1).bonus(stats(mods().ranged(0.03)))',
     "quiver - up WIND",
     "遠矢の心得", "Long Shot", "遠距離攻撃力が上がる。", "Raises ranged power."),
    ("a", "double_tap", 0, 2, 2,
     '.req("armor_piercer", 1).weapons(BOW).cd(12).theme(WIND).act(22, 18, 18, shots(8, 6, 2, arrow(4.6), 1.6, 0.3))'
     '.fx(vis(START, "cast"), vis(1, "ray"))',
     "arrow2 speed x2 WIND",
     "二連射", "Double Tap",
     "寸分違わぬ位置へ二本の矢を続けて撃ち込む。",
     "Put two arrows into the very same spot, one right after the other."),
    ("p", "lying_in_wait", 0, 3, 2, '.req("leg_shot", 1).bonus(stats(mods().critDamage(0.06)))',
     "hood - crit EARTH",
     "待ち伏せ", "Lying in Wait", "会心ダメージが上がる。", "Raises critical damage."),

    ("a", "charged_shot", 0, 0, 3,
     '.req("weak_point", 1).weapons(BOW).cd(18).theme(WIND).act(40, 36, 20, pause(4), shot(30, arrow(5.6).withPierce(4), 4.4, 1.6))'
     '.fx(self(START, DEFENSE_UP, 0.3, 34), vis(START, "judgment_charge"), vis(1, "ray"), vis(1, "impact"))',
     "bow beam x2 WIND",
     "溜め撃ち", "Charged Shot",
     "弓を限界まで引き絞り、力を溜めてから放つ。溜めている間は受けるダメージが減る。",
     "Draw the bow to its limit and hold, then loose; while you hold, you take less damage."),
    ("a", "ricochet", 0, 1, 3,
     '.req("long_shot", 1).weapons(BOW).cd(14).theme(STEEL).act(20, 16, 18, shot(8, arrow(4.0).withFan(3, 30).withPierce(2), 1.6, 0.3))'
     '.fx(vis(START, "cast"), vis(0, "burst"))',
     "arrows3fan ring x3 STEEL",
     "跳弾射撃", "Ricochet",
     "跳ね返る矢を放つ。矢は三方に分かれ、それぞれ敵を貫いて飛ぶ。",
     "Loose a ricocheting arrow that splits three ways, each piercing the foes in its path."),
    ("p", "far_sight", 0, 2, 3, '.req("lying_in_wait", 1).bonus(stats(mods().crit(0.02)))',
     "eye halo star WIND",
     "千里眼", "Far Sight", "会心率が上がる。", "Raises critical chance."),
    ("a", "headhunter", 0, 3, 3,
     '.req("double_tap", 3).weapons(BOW).cd(18).theme(BLOOD).act(24, 20, 20, shot(14, arrow(5.0), 3.0, 0.6))'
     '.fx(execute(0, 0.35, 0.9), vis(START, "cast"), vis(0, "thrust"))',
     "skull beamd skull BLOOD",
     "首狩り", "Headhunter",
     "とどめの一矢を放つ。HPの少ない敵ほど大きなダメージを与える。",
     "Loose the finishing arrow; the weaker the foe, the more it takes."),

    ("a", "flash_arrow", 0, 0, 4,
     '.req("charged_shot", 1).weapons(BOW).cd(22).theme(WIND).act(24, 20, 24, hit(10, beam(16.0, 1.0), 4.0, 0.8))'
     '.fx(vis(START, "cast"), vis(0, "ray"), vis(0, "lightning"))',
     "arrow1 beam - WIND",
     "一閃の矢", "Flash Arrow",
     "目にも見えない速さの一矢。放った瞬間、はるか先まで一直線上の敵を射抜く。",
     "An arrow too fast to see: the instant it leaves the string it strikes every foe far down its line."),
    ("p", "master_marksman", 0, 1, 4, '.req("far_sight", 1).bonus(power(0.03))',
     "target halo star WIND",
     "名射手", "Master Marksman", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "suppressing_fire", 0, 2, 4,
     '.req("ricochet", 1).weapons(BOW).cd(20).theme(STEEL).act(26, 22, 24, shots(4, 3, 6, arrow(4.0), 0.7, 0.1))'
     '.fx(afflict(5, SLOW, 80, 2), vis(START, "cast"), vis(5, "burst"))',
     "arrows5fan speed x6 STEEL",
     "制圧射撃", "Suppressing Fire",
     "矢を六本立て続けに撃ち込んで敵を押さえ込む。最後の一本で足を鈍らせる。",
     "Pin the foe down with six arrows in quick succession; the last one slows it."),
    ("p", "cold_blood", 0, 3, 4, '.req("headhunter", 1).bonus(stats(mods().critDamage(0.08)))',
     "drop glow crit FROST",
     "冷徹", "Ice in the Veins", "会心ダメージが大きく上がる。", "Greatly raises critical damage."),

    ("u", "one_shot_one_kill", 0, 0, 5,
     '.req("flash_arrow", 3).weapons(BOW).cd(120).theme(WIND)'
     '.act(52, 48, 40, pause(6), shot(38, arrow(6.0).withPierce(16), 9.0, 3.0))'
     '.fx(self(START, DEFENSE_UP, 0.5, 44), execute(1, 0.4, 1.5), afflict(1, VULNERABLE, 160, 0.3),'
     ' vis(START, "judgment_charge"), vis(0, "sigil"), vis(1, "ray"), vis(1, "impact"))',
     "target beam skull LIGHT",
     "奥義・一射必殺", "Secret Art: One Shot, One Kill",
     "【必殺技】長く狙いを定め、ただ一本の矢にすべてを込める。一直線上の敵を貫き、弱った敵は一撃で仕留める。構えている間は受けるダメージが大きく減る。",
     "[Ultimate] Take long, careful aim and put everything into a single arrow: it pierces every foe in its line "
     "and finishes the weakened outright. You take far less damage while you aim."),
    ("p", "perfect_aim", 0, 1, 5, '.req("master_marksman", 1).bonus(stats(mods().crit(0.02).ranged(0.02)))',
     "target glow up LIGHT",
     "必中", "Perfect Aim", "会心率と遠距離攻撃力が上がる。", "Raises critical chance and ranged power."),
    ("a", "skyfall_arrow", 0, 2, 5,
     '.req("suppressing_fire", 3).weapons(BOW).cd(32).theme(FIRE)'
     '.act(30, 26, 28, shot(12, arrow(3.0).withExplosion(4.0).fallingFromAbove(), 4.2, 1.6))'
     '.fx(afflict(0, BURN, 60, 0), vis(START, "meteor_cast"), vis(0, "smite"), vis(0, "nova"))',
     "meteor smite burn FIRE",
     "天墜の矢", "Skyfall Arrow",
     "空高く放った一矢が燃えながら落ちてくる。狙った場所で大爆発を起こし、周りの敵を燃やす。",
     "An arrow loosed high into the sky comes down burning and explodes where you aimed, setting the foes "
     "around alight."),
    ("p", "hawk_king", 0, 3, 5, '.req("cold_blood", 3).bonus(stats(mods().ranged(0.03).critDamage(0.05)))',
     "feather halo crit WIND",
     "鷹の王", "King of Hawks", "遠距離攻撃力と会心ダメージが上がる。", "Raises ranged power and critical damage."),

    # ------------------------------------------------------------------ 1: 戦術
    ("a", "flashbang_arrow", 1, 0, 1,
     '.weapons(BOW).cd(12).theme(HOLY).act(18, 14, 14, shot(7, arrow(4.0).withExplosion(3.0), 1.0, 0.2))'
     '.fx(afflict(0, BLIND, 80, 0), vis(START, "cast"), vis(0, "burst"))',
     "arrow1 nova blind LIGHT",
     "閃光矢", "Flashbang Arrow",
     "閃光を放つ矢を射る。炸裂した場所の周りの敵は目がくらむ。",
     "Shoot an arrow that bursts in a blinding flash; the foes around where it lands are blinded."),
    ("a", "tactical_roll", 1, 1, 1,
     '.weapons(BOW).cd(12).theme(WIND).act(16, 12, 14, shot(6, arrow(4.2), 1.6, 0.6))'
     '.fx(retreat(START, 6), self(0, SPEED, 1, 60), vis(START, "blink"), vis(0, "cast"))',
     "boot speed speed WIND",
     "戦術転回", "Tactical Roll",
     "後ろへ転がって距離を取りながら一矢を放ち、しばらく素早く動ける。",
     "Roll back out of reach, loosing an arrow as you go; you stay quick on your feet for a while."),
    ("p", "battlefield_eye", 1, 2, 1, '.bonus(stats(mods().stamina(5).dodgeCost(0.97)))',
     "eye - stamina STEEL",
     "戦術眼", "Battlefield Eye", "最大スタミナが上がり、回避のスタミナ消費が減る。",
     "Raises maximum stamina; dodging costs less stamina."),
    ("a", "smoke_arrow", 1, 3, 1,
     '.weapons(BOW).cd(16).theme(SHADOW).act(16, 12, 14, shot(6, arrow(3.6).withExplosion(3.5), 1.0, 0.0))'
     '.fx(afflict(0, SLOW, 80, 2), vanish(0, 10), vis(START, "cast"), vis(0, "smoke"))',
     "arrow2 cloud slow SHADOW",
     "煙幕矢", "Smoke Arrow",
     "煙を噴く矢を射る。煙に巻かれた敵は足が鈍り、周りの敵は自分を見失う。",
     "Shoot an arrow trailing smoke: foes caught in it slow down, and the enemies near you lose track of you."),

    ("a", "net_arrow", 1, 0, 2,
     '.req("flashbang_arrow", 1).weapons(BOW).cd(16).theme(EARTH).act(18, 14, 14, shot(7, arrow(3.6).withExplosion(2.0), 1.0, 0.0))'
     '.fx(afflict(0, STUN, 50, 0), vis(START, "cast"), vis(0, "chains"))',
     "chains glow stun EARTH",
     "捕縛網", "Net Arrow",
     "網を仕込んだ矢を射る。広がった網が敵を絡め取り、動きを止める。",
     "Shoot an arrow carrying a net; it spreads over the foes where it lands and holds them fast."),
    ("p", "ghillie_cloak", 1, 1, 2, '.req("battlefield_eye", 1).bonus(evasion(0.015))',
     "hood glow - LIFE",
     "隠密外套", "Ghillie Cloak", "攻撃を回避しやすくなる。", "You dodge attacks more often."),
    ("a", "concussive_arrow", 1, 2, 2,
     '.req("tactical_roll", 1).weapons(BOW).cd(14).theme(EARTH).act(18, 14, 16, shot(7, arrow(4.2).withExplosion(2.6), 1.8, 2.4))'
     '.fx(afflict(0, LAUNCH, 1, 0.6), vis(START, "cast"), vis(0, "impact"), vis(0, "burst"))',
     "bomb burst launch EARTH",
     "衝撃矢", "Concussive Arrow",
     "炸裂する矢を射る。爆風が周りの敵を大きく吹き飛ばして打ち上げる。",
     "Shoot an exploding arrow; the blast throws the foes around it far back and up into the air."),
    ("p", "spotter", 1, 3, 2, '.req("smoke_arrow", 1).bonus(proc(VULNERABLE, 0.02, 100, 0.15, false))',
     "target ring vuln STEEL",
     "観測手", "Spotter", "攻撃が一定確率で敵を脆弱にする。", "Your attacks may leave the foe vulnerable."),

    ("a", "mark_target", 1, 0, 3,
     '.req("net_arrow", 1).weapons(BOW).cd(24).theme(BLOOD).act(18, 14, 16, shot(7, arrow(4.4), 1.0, 0.2))'
     '.fx(afflict(0, VULNERABLE, 240, 0.3), allies(0, ATTACK_UP, 0.1, 200, 12), vis(START, "cast"), vis(0, "sigil"))',
     "banner glow vuln BLOOD",
     "標的指示", "Mark the Target",
     "攻撃目標を矢で指し示す。射られた敵は長い間、受けるダメージが大きく増え、近くの仲間は攻撃力が上がる。",
     "Point out the target with an arrow: it takes far more damage for a long while and the allies near you strike "
     "harder."),
    ("a", "incendiary_volley", 1, 1, 3,
     '.req("ghillie_cloak", 1).weapons(BOW).cd(18).theme(FIRE).act(22, 18, 20, shots(6, 4, 3, arrow(3.2).withExplosion(2.0), 1.0, 0.3))'
     '.fx(afflict(2, BURN, 80, 0), vis(START, "cast"), vis(2, "burst"))',
     "arrows3fan flamewall burn FIRE",
     "焼夷弾幕", "Incendiary Volley",
     "火薬を仕込んだ矢を三本続けて撃ち込む。最後の爆発が敵を燃え上がらせる。",
     "Fire three arrows packed with powder; the last explosion sets the foes ablaze."),
    ("p", "battle_plan", 1, 2, 3, '.req("spotter", 1).bonus(cooldown(0.02))',
     "banner - clock STEEL",
     "作戦立案", "Battle Plan", "スキルの再使用時間が短くなる。", "Skills recover faster."),
    ("a", "overwatch", 1, 3, 3,
     '.req("concussive_arrow", 1).weapons(BOW).cd(28).theme(WIND).act(18, 14, 20, pause(5))'
     '.fx(zone(0, LOOK, 14.0, 5.0, 0, 8, 10, 0.7, SLOW, 40, 1), vis(START, "cast"), vis(0, "arrow_rain"))',
     "arrows_rain zone slow WIND",
     "監視射撃", "Overwatch",
     "狙った場所を見張り、しばらくの間そこへ矢を降らせ続ける。中の敵は傷つき、足が鈍る。",
     "Watch over the spot you aim at and keep arrows falling on it for a while; foes inside are hurt and slowed."),

    ("a", "shrapnel_arrow", 1, 0, 4,
     '.req("mark_target", 1).weapons(BOW).cd(22).theme(STEEL).act(20, 16, 22, shot(8, arrow(4.0).withExplosion(3.5), 2.6, 1.0))'
     '.fx(afflict(0, BLEED, 100, 0.4), vis(START, "cast"), vis(0, "burst"), vis(0, "nova"))',
     "knives7 burst bleed STEEL",
     "破片矢", "Shrapnel Arrow",
     "破片を詰めた矢を射る。炸裂した破片が周りの敵に突き刺さり、出血させる。",
     "Shoot an arrow packed with shrapnel; the burst drives splinters into the foes around it and they bleed."),
    ("p", "veteran_scout", 1, 1, 4, '.req("battle_plan", 1).bonus(stats(mods().moveSpeed(0.02).crit(0.01)))',
     "boot halo speed WIND",
     "歴戦の斥候", "Veteran Scout", "移動速度と会心率が上がる。", "Raises movement speed and critical chance."),
    ("a", "signal_fire", 1, 2, 4,
     '.req("incendiary_volley", 1).cd(40).theme(FIRE).act(16, 12, 18, pause(5))'
     '.fx(allies(0, ATTACK_UP, 0.15, 240, 14), self(0, ATTACK_UP, 0.15, 240), allies(0, SPEED, 0, 240, 14),'
     ' stamina(0, 30), vis(0, "roar"), vis(0, "pillar"))',
     "flame pillar up FIRE",
     "狼煙", "Signal Fire",
     "狼煙を上げて総攻撃の合図を送る。広い範囲の仲間の攻撃力と移動速度が上がり、自分のスタミナも戻る。",
     "Raise a signal fire to call the attack: allies in a wide circle strike harder and move faster, and your "
     "stamina returns."),
    ("p", "ambusher", 1, 3, 4, '.req("overwatch", 1).bonus(lastStand(0.05, 0.0))',
     "trap glow - EARTH",
     "伏兵", "Ambusher", "HPが減るほど与えるダメージが増える。", "The lower your health, the harder you strike."),

    ("u", "annihilation_field", 1, 0, 5,
     '.req("shrapnel_arrow", 3).weapons(BOW).cd(120).theme(FIRE)'
     '.act(44, 40, 40, seq(pause(6), shots(10, 3, 8, arrow(2.6).withExplosion(2.2).fallingFromAbove(), 1.0, 0.3)))'
     '.fx(afflict(1, SLOW, 100, 3), afflict(8, VULNERABLE, 160, 0.3), afflict(8, BURN, 80, 0), vis(START, "sigil"),'
     ' vis(0, "cast"), vis(1, "arrow_rain"), vis(5, "rain"), vis(8, "nova"))',
     "arrows_rain flamewall x7 FIRE",
     "奥義・殲滅射界", "Secret Art: Field of Annihilation",
     "【必殺技】狙った一帯を射界に定め、炸裂する矢を八本続けて降らせる。敵の足を止め、最後の爆発が守りを砕いて燃え上がらせる。",
     "[Ultimate] Mark out a killing field where you aim and rain eight exploding arrows on it: the foes are held in "
     "place, and the last blast shatters their guard and sets them alight."),
    ("p", "tactician", 1, 1, 5, '.req("veteran_scout", 1).bonus(power(0.02), cooldown(0.01))',
     "crown glow clock STEEL",
     "軍師", "Tactician", "スキルの威力が上がり、再使用時間が短くなる。", "Raises skill power; skills recover faster."),
    ("a", "parting_volley", 1, 2, 5,
     '.req("signal_fire", 1).weapons(BOW).cd(26).theme(WIND).act(20, 16, 22, shot(8, arrow(4.6).withFan(5, 30), 1.2, 0.4))'
     '.fx(retreat(START, 10), self(0, SPEED, 1, 80), vis(START, "blink"), vis(0, "cast"))',
     "arrows5fan speed speed WIND",
     "離脱射撃", "Parting Volley",
     "大きく後ろへ跳んで距離を取りながら、五本の矢を扇状に放つ。しばらく素早く動ける。",
     "Spring far back out of reach while loosing five arrows in a fan; you stay quick on your feet for a while."),
    ("p", "iron_discipline", 1, 3, 5, '.req("ambusher", 1).bonus(stats(mods().defense(4).healthPercent(0.02)))',
     "helmet halo shield STEEL",
     "鉄の規律", "Iron Discipline", "防御力と最大HPが上がる。", "Raises defense and maximum health."),
]
