"""審問官 (Inquisitor): the priest who judges. 断罪 accuses, binds and sentences — chains, seals, verdicts and the
execution of the condemned; 聖火 is the holy fire that purges — censers, pyres, blessed flame for the party."""

TITLE_JA = "審問官"
TITLE_EN = "Inquisitor"
SUMMARY = "Accusations, chains and verdicts; the purging holy fire."
DAMAGE = "HOLY"
BRANCHES = [("断罪", "Condemnation"), ("聖火", "Holy Fire")]

NODES = [
    # ------------------------------------------------------------------ 0: 断罪
    ("a", "accusation", 0, 0, 1,
     '.cd(9).theme(HOLY).act(18, 14, 14, shot(7, orb("holy_light", 2.6, 24), 1.6, 0.2))'
     '.fx(afflict(0, VULNERABLE, 200, 0.2), vis(START, "cast"), vis(0, "impact"))',
     "hand beamd vuln HOLY",
     "告発", "Accusation",
     "罪を告発する光を放つ。撃たれた敵は長い間、受けるダメージが増える。",
     "Fire a light that names the guilty; the foe it strikes takes more damage for a long while."),
    ("a", "chains_of_penance", 0, 1, 1,
     '.cd(12).theme(HOLY).act(18, 14, 14, hit(7, line(8.0, 1.2), 1.2, 0.0))'
     '.fx(afflict(0, PULL, 1, 1.2), afflict(0, SLOW, 80, 2), vis(0, "chains"))',
     "chains beamd pull HOLY",
     "贖罪の鎖", "Chains of Penance",
     "贖罪の鎖を放ち、捕らえた敵を目の前まで引きずり出して足を鈍らせる。",
     "Cast out the chains of penance: the foes they catch are dragged before you and slowed."),
    ("p", "zealotry", 0, 2, 1, '.bonus(stats(mods().magic(0.03)))',
     "flame - up HOLY",
     "狂信", "Zealotry", "魔法攻撃力が上がる。", "Raises magic power."),
    ("a", "verdict", 0, 3, 1,
     '.cd(10).theme(HOLY).act(20, 16, 16, hit(9, ahead(2.5, 3.0), 2.4, 0.8))'
     '.fx(afflict(0, STUN, 30, 0), vis(START, "judgment_charge"), vis(0, "smite"))',
     "scales smite stun HOLY",
     "判決", "Verdict",
     "判決を言い渡し、前方の敵へ光の槌を打ち下ろして一瞬気絶させる。",
     "Pronounce the verdict and bring a hammer of light down on the foes ahead, stunning them for a moment."),

    ("a", "interrogation", 0, 0, 2,
     '.req("accusation", 3).cd(16).theme(HOLY).act(18, 14, 16, hit(8, ahead(2.0, 3.0), 1.6, 0.0))'
     '.fx(afflict(0, WEAKEN, 160, 0.25), afflict(0, BLIND, 40, 0), vis(0, "sigil"), vis(0, "impact"))',
     "eye glow weaken HOLY",
     "審問", "Interrogation",
     "厳しく問い詰め、前方の敵の力を長い間奪い、目をくらませる。",
     "Question the foe without mercy: for a long while its strength is sapped, and it is dazzled."),
    ("p", "merciless_judge", 0, 1, 2, '.req("zealotry", 1).bonus(stats(mods().crit(0.015).critDamage(0.04)))',
     "scales - crit HOLY",
     "仮借なき裁き", "Merciless Judge", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "brand_of_heresy", 0, 2, 2,
     '.req("verdict", 1).cd(18).theme(FIRE).act(18, 14, 18, hit(8, circle(4.5), 1.6, 0.4))'
     '.fx(afflict(0, BURN, 100, 0), afflict(0, VULNERABLE, 100, 0.15), vis(0, "sigil"), vis(0, "flame_blade"))',
     "rune flamewall burn FIRE",
     "異端の烙印", "Brand of Heresy",
     "周りの敵に燃える異端の烙印を押す。敵は焼かれ、受けるダメージが増える。",
     "Burn the brand of heresy into the foes around you: they burn and take more damage."),
    ("p", "iron_conviction", 0, 3, 2, '.req("verdict", 1).bonus(lastStand(0.05, 0.0))',
     "helmet glow - STEEL",
     "鉄の信念", "Iron Conviction", "HPが減るほど与えるダメージが増える。", "The lower your health, the harder you strike."),

    ("a", "condemnation", 0, 0, 3,
     '.req("interrogation", 1).cd(20).theme(HOLY).act(24, 20, 20, hit(12, ahead(2.5, 3.0), 3.4, 1.0))'
     '.fx(execute(0, 0.3, 0.8), vis(START, "judgment_charge"), vis(0, "judgment"), vis(0, "smite"))',
     "scales pillar skull LIGHT",
     "断罪", "Condemnation",
     "罪ある者に裁きを下す。HPの少ない敵ほど大きなダメージを与える。",
     "Pass judgment on the guilty; the weaker the foe, the more it takes."),
    ("a", "shackles", 0, 1, 3,
     '.req("merciless_judge", 1).cd(24).theme(HOLY).act(18, 14, 18, hit(8, circle(5.0), 1.2, 0.0))'
     '.fx(afflict(0, STUN, 50, 0), vis(0, "chains"), vis(0, "nova"))',
     "chains ring stun HOLY",
     "枷", "Shackles",
     "光の枷を周りへ放ち、触れた敵の手足を縛って動きを止める。",
     "Throw shackles of light all around you; every foe they touch is bound hand and foot."),
    ("p", "heretic_hunter", 0, 2, 3, '.req("iron_conviction", 1).bonus(power(0.02), stats(mods().crit(0.01)))',
     "target glow star HOLY",
     "異端狩り", "Heretic Hunter", "スキルの威力と会心率が上がる。", "Raises skill power and critical chance."),
    ("a", "sentence_of_light", 0, 3, 3,
     '.req("brand_of_heresy", 1).cd(22).theme(HOLY).act(16, 12, 18, pause(4))'
     '.fx(zone(0, LOOK, 10.0, 3.5, 30, 1, 1, 4.0, STUN, 40, 0), vis(START, "cast"), vis(0, "sigil"), vis(0, "pillar"))',
     "sun zone stun LIGHT",
     "光の宣告", "Sentence of Light",
     "狙った場所に光の宣告を刻む。一瞬ののち、天から光の柱が落ちて大きなダメージを与え、敵を気絶させる。",
     "Inscribe a sentence of light where you look; a moment later a pillar of light falls on it, dealing heavy "
     "damage and stunning the foes there."),

    ("a", "inquisition", 0, 0, 4,
     '.req("condemnation", 1).cd(24).theme(HOLY).act(26, 22, 24, hits(6, 5, 3, ahead(2.5, 3.0), 1.4, 0.4))'
     '.fx(afflict(2, VULNERABLE, 120, 0.2), vis(START, "cast"), vis(0, "judgment"), vis(2, "slash"))',
     "cross x x3 HOLY",
     "異端審問", "Inquisition",
     "前方の敵へ三度、裁きの光を打ち込む。最後の一撃で守りを崩す。",
     "Strike the foes ahead three times with the light of judgment; the last blow breaks their guard."),
    ("p", "arbiters_eye", 0, 1, 4, '.req("heretic_hunter", 1).bonus(stats(mods().crit(0.02)))',
     "eye halo crit LIGHT",
     "裁定者の眼", "Arbiter's Eye", "会心率が上がる。", "Raises critical chance."),
    ("a", "cross_of_light", 0, 2, 4,
     '.req("shackles", 1).cd(22).theme(HOLY).act(22, 18, 22, hit(10, ahead(2.0, 5.0), 3.0, 0.4))'
     '.fx(afflict(0, STUN, 40, 0), afflict(0, LAUNCH, 1, 0.6), vis(START, "cast"), vis(0, "pillar"), vis(0, "judgment"))',
     "cross pillar launch LIGHT",
     "光の十字架", "Cross of Light",
     "前方に巨大な光の十字架を突き立て、敵を打ち上げて気絶させる。",
     "Drive a towering cross of light into the ground ahead, hurling the foes into the air and stunning them."),
    ("p", "unforgiving", 0, 3, 4, '.req("sentence_of_light", 1).bonus(proc(VULNERABLE, 0.02, 100, 0.15, false))',
     "scales glow vuln BLOOD",
     "赦さぬ者", "The Unforgiving", "攻撃が一定確率で敵を脆弱にする。", "Your attacks may leave the foe vulnerable."),

    ("u", "divine_execution", 0, 0, 5,
     '.req("inquisition", 3).cd(130).theme(HOLY)'
     '.act(46, 42, 40, pause(8), hit(20, circle(7.0), 1.2, 0.0), hit(36, circle(8.0), 5.2, 1.6))'
     '.fx(afflict(1, PULL, 1, 1.0), afflict(2, STUN, 40, 0), afflict(2, VULNERABLE, 160, 0.3), execute(2, 0.3, 1.0),'
     ' vis(START, "judgment_charge"), vis(0, "sigil"), vis(1, "chains"), vis(2, "judgment"), vis(2, "pillar"))',
     "scales nova skull LIGHT",
     "奥義・神罰執行", "Secret Art: Divine Execution",
     "【必殺技】神の罰を執行する。光の鎖で周りの敵を引き寄せ、続く裁きの一撃で気絶させて守りを砕く。弱った敵ほど深く裁かれる。",
     "[Ultimate] Carry out heaven's punishment: chains of light drag the foes around you in, then the blow of "
     "judgment stuns them and shatters their guard. The weaker they are, the harder they are judged."),
    ("p", "grand_inquisitor", 0, 1, 5, '.req("arbiters_eye", 1).bonus(stats(mods().magic(0.03).critDamage(0.05)))',
     "crown halo crit HOLY",
     "大審問官", "Grand Inquisitor", "魔法攻撃力と会心ダメージが上がる。", "Raises magic power and critical damage."),
    ("a", "chain_of_judgment", 0, 2, 5,
     '.req("cross_of_light", 3).cd(30).theme(HOLY).act(28, 24, 28, hits(6, 4, 4, circle(5.0), 0.8, 0.2))'
     '.fx(afflict(3, STUN, 40, 0), vis(0, "chains"), vis(1, "lightning"), vis(3, "nova"))',
     "chains nova x4 LIGHT",
     "裁きの連鎖", "Chain of Judgment",
     "裁きの光が鎖のように連なって周りの敵を四度打つ。最後の一撃で気絶させる。",
     "The light of judgment links like a chain and strikes the foes around you four times; the last stuns them."),
    ("p", "absolute_law", 0, 3, 5, '.req("unforgiving", 3).bonus(power(0.03))',
     "scales halo star LIGHT",
     "絶対律", "Absolute Law", "スキルの威力が上がる。", "Raises the power of your skills."),

    # ------------------------------------------------------------------ 1: 聖火
    ("a", "sacred_flame", 1, 0, 1,
     '.cd(8).theme(FIRE).act(18, 14, 14, shot(7, orb("fireball", 2.0, 30).withExplosion(2.0), 1.8, 0.4))'
     '.fx(afflict(0, BURN, 80, 0), vis(START, "cast"))',
     "flame burst burn FIRE",
     "聖火弾", "Sacred Flame",
     "聖なる炎の弾を放つ。炸裂した場所の周りの敵を燃やす。",
     "Fire a bolt of holy flame that bursts where it lands and sets the foes around ablaze."),
    ("a", "censer_swing", 1, 1, 1,
     '.cd(10).theme(FIRE).act(18, 14, 14, hit(7, circle(3.5), 1.4, 0.4))'
     '.fx(afflict(0, BURN, 60, 0), heal(0, 6, 0.04, 1), vis(0, "spin"), vis(0, "aura"))',
     "lantern spiral heal FIRE",
     "香炉の炎", "Censer Swing",
     "聖なる炎の香炉を振り回す。周りの敵を焼き、立ち上る煙が近くの仲間を少し癒す。",
     "Swing a censer of holy fire around you: it burns the foes around, and its rising smoke heals nearby allies "
     "a little."),
    ("p", "purging_fire", 1, 2, 1, '.bonus(proc(BURN, 0.03, 60, 0, false))',
     "flame glow burn FIRE",
     "浄化の火", "Purging Fire", "攻撃が一定確率で敵を燃やす。", "Your attacks may set the foe ablaze."),
    ("a", "flame_of_truth", 1, 3, 1,
     '.cd(12).theme(FIRE).act(20, 16, 16, hits(4, 3, 4, arc(5.5, 35), 0.7, 0.1))'
     '.fx(afflict(3, BURN, 80, 0), vis(START, "cast"), vis(0, "breath"))',
     "eye flamewall x4 FIRE",
     "真実の炎", "Flame of Truth",
     "真実を暴く炎を前方へ吹きつけ、四度焼き払う。",
     "Breathe out the flame that lays the truth bare, scorching the cone ahead four times."),

    ("a", "pillar_of_judgment", 1, 0, 2,
     '.req("sacred_flame", 3).cd(16).theme(FIRE).act(20, 16, 18, hit(9, ahead(2.0, 5.0), 2.6, 0.6))'
     '.fx(afflict(0, BURN, 80, 0), afflict(0, LAUNCH, 1, 0.6), vis(START, "cast"), vis(0, "pillar"))',
     "flame pillar launch FIRE",
     "裁きの火柱", "Pillar of Judgment",
     "前方の地面から聖なる火柱を噴き上げ、敵を打ち上げて燃やす。",
     "A pillar of holy fire erupts from the ground ahead, hurling the foes up and setting them alight."),
    ("p", "ember_soul", 1, 1, 2, '.req("purging_fire", 1).bonus(stats(mods().magic(0.02).critDamage(0.04)))',
     "heart glow crit FIRE",
     "燠火の魂", "Ember Soul", "魔法攻撃力と会心ダメージが上がる。", "Raises magic power and critical damage."),
    ("a", "flame_ward", 1, 2, 2,
     '.req("censer_swing", 1).cd(30).theme(FIRE).act(12, 8, 12, pause(3))'
     '.fx(self(0, FLAME_WEAPON, 0, 240), shield(0, 0.2, 240), self(0, THORNS, 0.2, 240), vis(0, "aura"),'
     ' vis(0, "flame_blade"))',
     "shield flamewall shield FIRE",
     "炎の守り", "Flame Ward",
     "聖なる炎をまとう。シールドを得て、攻撃が敵を燃やし、受けた攻撃の一部を跳ね返す。",
     "Clothe yourself in holy flame: you gain a shield, your blows set foes alight and part of every hit you take "
     "is returned."),
    ("p", "smoldering_faith", 1, 3, 2, '.req("flame_of_truth", 1).bonus(stats(mods().healthPercent(0.02).defense(3)))',
     "cross - shield FIRE",
     "燻る信仰", "Smoldering Faith", "最大HPと防御力が上がる。", "Raises maximum health and defense."),

    ("a", "ring_of_cleansing_fire", 1, 0, 3,
     '.req("pillar_of_judgment", 1).cd(20).theme(FIRE).act(20, 16, 20, hit(9, circle(5.5), 2.4, 0.8))'
     '.fx(afflict(0, BURN, 80, 0), cleanse(0, 8), vis(0, "nova"), vis(0, "burst"))',
     "sun nova burn FIRE",
     "浄炎の輪", "Ring of Cleansing Fire",
     "清めの炎を輪のように広げる。周りの敵を焼き、近くの仲間の状態異常を焼き払う。",
     "Spread a ring of cleansing fire: the foes around you burn and the ailments of nearby allies burn away."),
    ("a", "holy_pyre", 1, 1, 3,
     '.req("ember_soul", 1).cd(24).theme(FIRE).act(18, 14, 20, pause(5))'
     '.fx(zone(0, LOOK, 10.0, 4.0, 0, 8, 10, 0.8, BURN, 60, 0), vis(START, "cast"), vis(0, "pillar"), vis(0, "storm"))',
     "flame zone burn MAGMA",
     "聖なる業火", "Holy Pyre",
     "狙った場所を聖なる業火で包む。しばらくの間、炎の中の敵は焼かれ続ける。",
     "Wrap the place you look at in holy fire; for a while foes inside it keep burning."),
    ("p", "sacred_heat", 1, 2, 3, '.req("ember_soul", 1).bonus(power(0.02), stats(mods().crit(0.01)))',
     "sun glow star FIRE",
     "聖熱", "Sacred Heat", "スキルの威力と会心率が上がる。", "Raises skill power and critical chance."),
    ("a", "lash_of_flame", 1, 3, 3,
     '.req("smoldering_faith", 1).cd(18).theme(FIRE).act(18, 14, 18, hit(8, line(8.0, 1.2), 2.2, 0.0))'
     '.fx(afflict(0, PULL, 1, 0.8), afflict(0, BURN, 60, 0), vis(0, "chains"), vis(0, "flame_blade"))',
     "snake beamd pull FIRE",
     "炎の鞭", "Lash of Flame",
     "炎の鞭を振るい、一直線上の敵を打って手元へ引き寄せ、燃やす。",
     "Crack a whip of fire down a line, striking the foes in it, dragging them to you and setting them alight."),

    ("a", "seraphic_fire", 1, 0, 4,
     '.req("ring_of_cleansing_fire", 1).cd(24).theme(FIRE)'
     '.act(24, 20, 24, shot(10, orb("fireball", 1.6, 40).withPierce(16).withExplosion(2.0), 3.2, 0.8))'
     '.fx(afflict(0, BURN, 100, 0), vis(START, "cast"), vis(0, "ray"), vis(0, "burst"))',
     "wings flamewall burn FIRE",
     "熾天の炎", "Seraphic Fire",
     "熾天使の炎を放つ。炎は一直線上の敵すべてを貫いて燃やし、終点で炸裂する。",
     "Loose the fire of the seraphim; it passes through every foe in its line, burning them, and bursts at the end."),
    ("p", "keeper_of_the_flame", 1, 1, 4, '.req("sacred_heat", 1).bonus(stats(mods().healing(0.04).magic(0.02)))',
     "lantern halo heal FIRE",
     "聖火の守り手", "Keeper of the Flame", "回復量と魔法攻撃力が上がる。", "Raises healing and magic power."),
    ("a", "baptism_of_fire", 1, 2, 4,
     '.req("flame_ward", 1).cd(40).theme(FIRE).act(16, 12, 18, pause(5))'
     '.fx(allies(0, FLAME_WEAPON, 0, 240, 10), self(0, FLAME_WEAPON, 0, 240), allies(0, ATTACK_UP, 0.12, 240, 10),'
     ' self(0, ATTACK_UP, 0.12, 240), vis(0, "aura"), vis(0, "flame_blade"))',
     "sword flamewall up FIRE",
     "炎の洗礼", "Baptism of Fire",
     "自分と周囲の仲間の武器に聖なる炎を授ける。しばらくの間、攻撃が敵を燃やし、与えるダメージが増える。",
     "Anoint your weapon and those of the allies around you with holy fire: for a while every blow burns and hits "
     "harder."),
    ("p", "from_the_ashes", 1, 3, 4, '.req("lash_of_flame", 1).bonus(lastStand(0.05, 0.02))',
     "feather glow heart FIRE",
     "灰の中から", "From the Ashes", "HPが減るほど与えるダメージが増え、受けるダメージが減る。",
     "The lower your health, the harder you strike and the less damage you take."),

    ("u", "heavenly_wildfire", 1, 0, 5,
     '.req("seraphic_fire", 3).cd(130).theme(FIRE)'
     '.act(48, 44, 40, seq(pause(6), hits(12, 4, 5, circle(7.0), 1.0, 0.2), hit(36, circle(8.0), 4.4, 1.4)))'
     '.fx(afflict(1, BURN, 120, 0), afflict(6, LAUNCH, 1, 0.8), afflict(6, VULNERABLE, 160, 0.3), vis(START, "sigil"),'
     ' vis(0, "meteor_cast"), vis(1, "storm"), vis(3, "pillar"), vis(6, "nova"))',
     "sun flamewall x5 MAGMA",
     "奥義・天火燎原", "Secret Art: Heavenly Wildfire",
     "【必殺技】天の火を地に放つ。燃え広がる炎が周りの敵を五度焼き、最後の大炎が敵を打ち上げて守りを砕く。",
     "[Ultimate] Let heaven's fire loose upon the earth: the spreading flames burn the foes around you five times, "
     "and a final blaze hurls them up and shatters their guard."),
    ("p", "saint_of_flames", 1, 1, 5, '.req("keeper_of_the_flame", 1).bonus(stats(mods().magic(0.03).healing(0.03)))',
     "crown flamewall up FIRE",
     "炎の聖人", "Saint of Flames", "魔法攻撃力と回復量が上がる。", "Raises magic power and healing."),
    ("a", "hammer_of_the_sun", 1, 2, 5,
     '.req("baptism_of_fire", 1).cd(34).theme(FIRE)'
     '.act(28, 24, 28, shot(10, orb("smite", 1.4, 40).withExplosion(4.0).fallingFromAbove(), 4.0, 1.6))'
     '.fx(afflict(0, BURN, 80, 0), afflict(0, STUN, 30, 0), vis(START, "cast"), vis(0, "smite"), vis(0, "nova"))',
     "hammer smite burn FIRE",
     "太陽の槌", "Hammer of the Sun",
     "太陽のように燃える槌を天から落とす。狙った場所で炸裂し、周りの敵を焼いて気絶させる。",
     "Drop a hammer burning like the sun from the sky; it bursts where you aim, burning and stunning the foes "
     "around."),
    ("p", "everburning", 1, 3, 5, '.req("from_the_ashes", 3).bonus(cooldown(0.02))',
     "flame halo clock FIRE",
     "不滅の火", "Everburning", "スキルの再使用時間が短くなる。", "Skills recover faster."),
]
