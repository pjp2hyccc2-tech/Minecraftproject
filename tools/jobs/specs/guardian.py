"""守護士 (Guardian): the knight who does not fall. 鉄壁 holds the line with shields, counters and an iron body;
統率 commands the field — taunts that drag enemies in, banners and vows that harden the whole party."""

TITLE_JA = "守護士"
TITLE_EN = "Guardian"
SUMMARY = "Shields, counters and provocation; the party's wall."
DAMAGE = "PHYSICAL"
BRANCHES = [("鉄壁", "Bulwark"), ("統率", "Command")]

# (kind, name, branch, column, tier, DSL, icon recipe, 名前, name, 説明, description)
NODES = [
    # ------------------------------------------------------------------ 0: 鉄壁
    ("a", "titan_stance", 0, 0, 1,
     '.cd(24).theme(STEEL).act(14, 10, 12, pause(4))'
     '.fx(self(0, DEFENSE_UP, 0.35, 200), shield(0, 0.25, 200), self(0, THORNS, 0.2, 200), vis(0, "shield"),'
     ' vis(0, "iron_wall"))',
     "shield_spikes ring shield STEEL",
     "巨人の構え", "Titan Stance",
     "大地に根を張るように構え、受けるダメージを大きく減らす。シールドをまとい、攻撃の一部を跳ね返す。",
     "Plant yourself like a mountain: take far less damage, wear a shield and send part of every blow back."),
    ("p", "stone_skin", 0, 1, 1, '.bonus(stats(mods().defense(6).healthPercent(0.02)))',
     "helmet - - EARTH",
     "岩肌", "Stone Skin", "防御力と最大HPが上がる。", "Raises defense and maximum health."),
    ("a", "shield_crush", 0, 2, 1,
     '.cd(9).theme(STEEL).act(18, 14, 16, hit(7, arc(3.6, 60), 2.6, 1.2))'
     '.fx(afflict(0, STUN, 30, 0), afflict(0, VULNERABLE, 100, 0.15), vis(0, "impact"), vis(0, "slam_small"))',
     "shield_broken burst stun STEEL",
     "盾砕き", "Shield Crush",
     "盾ごと叩きつけて敵をよろめかせ、守りを崩して脆弱にする。",
     "Smash with your whole shield: the foe reels, stunned, its guard broken (vulnerable)."),
    ("a", "riposte_stance", 0, 3, 1,
     '.cd(14).theme(STEEL).act(22, 18, 14, pause(3), hit(14, circle(3.4), 2.4, 1.0))'
     '.fx(self(START, DEFENSE_UP, 0.6, 20), self(START, EVASION, 0.3, 20), vis(START, "shield"), vis(1, "spin"))',
     "sword ring x2 STEEL",
     "反撃の構え", "Riposte Stance",
     "一瞬だけ鉄壁の構えで攻撃を受け流し、周囲をなぎ払って反撃する。",
     "For a heartbeat nothing gets through; then you sweep everything around you in reply."),

    ("a", "bastion", 0, 0, 2,
     '.req("titan_stance", 3).cd(30).theme(EARTH).act(16, 12, 15, pause(5))'
     '.fx(shield(0, 0.45, 160), self(0, DEFENSE_UP, 0.25, 160), taunt(0, 8, 100), vis(0, "iron_wall"),'
     ' vis(0, "sigil"))',
     "tower halo shield EARTH",
     "要塞化", "Bastion",
     "自らを砦に変える。大きなシールドと防御強化をまとい、近くの敵を引きつける。",
     "Become a fortress: a heavy shield, hardened armor, and every nearby foe turns on you."),
    ("p", "iron_resolve", 0, 1, 2, '.req("stone_skin", 1).bonus(lastStand(0.0, 0.05))',
     "heart glow - STEEL",
     "鉄の決意", "Iron Resolve", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),
    ("a", "rampart_slam", 0, 2, 2,
     '.req("shield_crush", 3).cd(12).theme(EARTH).act(22, 18, 20, hit(9, circle(3.8), 2.8, 1.4))'
     '.fx(afflict(0, SLOW, 80, 2), shield(0, 0.1, 100), vis(0, "quake"), vis(0, "impact"))',
     "hammer crack slow EARTH",
     "城壁打ち", "Rampart Slam",
     "盾で大地を打ち、周囲の敵を吹き飛ばして足を鈍らせる。打った衝撃で薄いシールドを得る。",
     "Drive your shield into the ground: foes around are thrown back and slowed, and the shock shields you."),
    ("p", "reprisal", 0, 3, 2, '.req("riposte_stance", 1).bonus(thorns(0.06))',
     "shield_small burst thorns BLOOD",
     "報復", "Reprisal", "受けたダメージの一部を相手に跳ね返す。", "Part of the damage you take is returned to the attacker."),

    ("a", "immovable", 0, 0, 3,
     '.req("bastion", 1).cd(40).theme(STEEL).act(12, 8, 10, pause(2))'
     '.fx(self(0, DEFENSE_UP, 0.6, 100), cleanse(0, 0), stamina(0, 40), vis(0, "roar"), vis(0, "shield"))',
     "knight glow up STEEL",
     "不動", "Immovable",
     "どんな力にも動じない。状態異常を振り払い、スタミナを取り戻し、しばらく鉄壁となる。",
     "Nothing moves you: shake off every ailment, recover stamina and stand like iron for a while."),
    ("p", "fortified_heart", 0, 1, 3, '.req("iron_resolve", 1).bonus(stats(mods().healthPercent(0.04)))',
     "heart halo heart BLOOD",
     "堅き心臓", "Fortified Heart", "最大HPが大きく上がる。", "Greatly raises maximum health."),
    ("a", "earthwall", 0, 2, 3,
     '.req("rampart_slam", 1).cd(18).theme(EARTH).act(26, 22, 22, hits(6, 6, 3, line(5.5, 2.0), 1.3, 0.8))'
     '.fx(dash(START, 3), afflict(2, LAUNCH, 1, 0.7), vis(0, "quake"), vis(2, "pillar"))',
     "chest crack x3 EARTH",
     "大地の壁", "Earthwall",
     "前へ踏み出し、地面から三度岩壁を突き上げる。最後の一撃で敵を打ち上げる。",
     "Step in and raise three walls of rock from the ground ahead; the last one hurls foes into the air."),
    ("p", "spiked_bulwark", 0, 3, 3, '.req("reprisal", 3).bonus(stats(mods().guard(0.03)), thorns(0.03))',
     "shield_spikes - thorns STEEL",
     "棘の城壁", "Spiked Bulwark", "ガード性能が上がり、反射ダメージも増える。",
     "Your guard holds better and returns more damage."),

    ("a", "wall_of_aegis", 0, 0, 4,
     '.req("immovable", 1).cd(45).theme(HOLY).act(18, 14, 20, pause(6))'
     '.fx(shield(0, 0.6, 200), shieldAllies(0, 0.25, 200, 8), allies(0, DEFENSE_UP, 0.2, 200, 8),'
     ' vis(START, "cast"), vis(0, "shield"), vis(0, "aura"))',
     "shield_cross nova shield HOLY",
     "聖盾の壁", "Wall of Aegis",
     "聖なる盾を広げ、自分と周囲の仲間を厚いシールドと防御の加護で包む。",
     "Spread a holy shield: you and every ally nearby are wrapped in thick shields and harder armor."),
    ("p", "unyielding", 0, 1, 4, '.req("fortified_heart", 1).bonus(lastStand(0.05, 0.04))',
     "banner glow heart STEEL",
     "不屈", "Unyielding", "HPが減るほど与えるダメージが増え、受けるダメージが減る。",
     "The lower your health, the harder you hit and the less you take."),
    ("a", "colossus_strike", 0, 2, 4,
     '.req("earthwall", 1).cd(22).theme(EARTH).act(30, 26, 28, hit(18, circle(5.0), 4.2, 1.6))'
     '.fx(leap(START, 3, 2.0), afflict(0, STUN, 40, 0), vis(START, "cast"), vis(0, "quake"), vis(0, "nova"))',
     "greatsword smite stun EARTH",
     "巨像撃", "Colossus Strike",
     "高く跳び、巨像のように全体重で叩き落とす。広い範囲の敵を気絶させる。",
     "Leap high and fall like a colossus: a wide blast that stuns everything it lands on."),
    ("p", "retribution_aura", 0, 3, 4, '.req("spiked_bulwark", 1).bonus(proc(WEAKEN, 0.05, 80, 0.15, true))',
     "eye ring weaken BLOOD",
     "報復の気", "Aura of Retribution", "近接攻撃が確率で敵を弱体化させる。", "Your melee hits may weaken the foe."),

    ("u", "eternal_fortress", 0, 0, 5,
     '.req("wall_of_aegis", 3).cd(130).theme(EARTH).act(32, 28, 35, pause(8), hit(22, circle(6.5), 4.5, 1.5))'
     '.fx(shield(0, 1.2, 240), self(0, DEFENSE_UP, 0.7, 240), taunt(0, 18, 240), allies(0, DEFENSE_UP, 0.3, 240, 12),'
     ' afflict(1, STUN, 50, 0), vis(START, "cast"), vis(0, "iron_wall"), vis(1, "quake"), vis(1, "pillar"))',
     "tower nova shield EARTH",
     "永劫の城塞", "Eternal Fortress",
     "【必殺技】永遠に落ちぬ城塞となる。巨大なシールドと鉄壁の守りをまとい、すべての敵を引きつけ、大地を割って周囲を気絶させる。仲間の守りも固める。",
     "[Ultimate] Become the fortress that never falls: a colossal shield, iron armor, every foe drawn to you, the ground "
     "split to stun all around — and your allies hardened too."),
    ("p", "living_citadel", 0, 1, 5, '.req("unyielding", 3).bonus(stats(mods().healthPercent(0.05).defense(6)))',
     "tower glow up EARTH",
     "生ける城塞", "Living Citadel", "最大HPと防御力が大きく上がる。", "Greatly raises maximum health and defense."),
    ("a", "mountain_breaker", 0, 2, 5,
     '.req("colossus_strike", 3).cd(30).theme(EARTH)'
     '.act(36, 32, 32, hit(10, arc(4.5, 90), 2.4, 0.8), hit(24, ahead(3.5, 4.0), 4.0, 1.6))'
     '.fx(afflict(1, LAUNCH, 1, 0.8), afflict(1, VULNERABLE, 120, 0.25), vis(0, "slash"), vis(1, "quake"),'
     ' vis(1, "burst"))',
     "hammer crack launch EARTH",
     "山崩し", "Mountain Breaker",
     "大きく薙いでから、前方の大地ごと叩き割る。打ち上げた敵の守りを砕く。",
     "A wide sweep, then a blow that splits the very ground ahead: foes fly up with their guard shattered."),
    ("p", "soul_of_the_aegis", 0, 3, 5, '.req("retribution_aura", 1).bonus(cooldown(0.03), saver(0.03))',
     "shield_cross halo star HOLY",
     "神盾の魂", "Soul of the Aegis", "スキルの再使用時間とスタミナ消費が減る。",
     "Skills recover faster and cost less stamina."),

    # ------------------------------------------------------------------ 1: 統率
    ("a", "challengers_roar", 1, 0, 1,
     '.cd(12).theme(STEEL).act(16, 12, 12, hit(6, circle(6.0), 1.2, 0.4))'
     '.fx(taunt(0, 14, 160), afflict(0, WEAKEN, 120, 0.2), vis(0, "roar"), vis(0, "war_cry"))',
     "banner waves taunt STEEL",
     "挑戦の咆哮", "Challenger's Roar",
     "戦場に響く咆哮で広い範囲の敵を引きつけ、怯ませて弱体化させる。",
     "A roar across the battlefield: every foe in a wide circle turns on you, weakened by fear."),
    ("a", "shield_throw", 1, 1, 1,
     '.cd(8).theme(STEEL).act(16, 12, 14, shot(5, orb("crescent", 2.0, 20).withPierce(4), 2.2, 0.8))'
     '.fx(afflict(0, STUN, 20, 0), vis(START, "cast"), vis(0, "slash"))',
     "buckler speed stun STEEL",
     "盾投げ", "Shield Throw",
     "盾を投げつけ、一直線に並んだ敵を貫いて気絶させる。",
     "Hurl your shield: it cuts through a line of foes and stuns them."),
    ("p", "commanders_presence", 1, 2, 1, '.bonus(stats(mods().healthPercent(0.02).guard(0.01)))',
     "crown - - STEEL",
     "将の威風", "Commander's Presence", "最大HPとガード性能が上がる。", "Raises maximum health and guard."),
    ("a", "guardians_leap", 1, 3, 1,
     '.cd(14).theme(WIND).act(24, 20, 16, hit(16, circle(3.6), 2.0, 1.0))'
     '.fx(leap(START, 8, 2.5), shieldAllies(0, 0.12, 120, 5), vis(START, "blink"), vis(0, "impact"))',
     "wings speed shield WIND",
     "守護の跳躍", "Guardian's Leap",
     "仲間のもとへ大きく跳び、着地の衝撃で敵を吹き飛ばす。周りの仲間にシールドを張る。",
     "Leap far to your allies' side: the landing scatters foes and shields everyone near you."),

    ("a", "iron_vow", 1, 0, 2,
     '.req("challengers_roar", 3).cd(28).theme(STEEL).act(16, 12, 15, pause(5))'
     '.fx(allies(0, DEFENSE_UP, 0.25, 200, 10), allies(0, THORNS, 0.1, 200, 10), taunt(0, 10, 120),'
     ' vis(0, "aura"), vis(0, "sigil"))',
     "hand halo thorns STEEL",
     "鉄の誓い", "Iron Vow",
     "誓いを立て、周囲の仲間の守りを固めて棘をまとわせる。敵の目を自分に向けさせる。",
     "Swear the vow: allies around you harden and grow thorns, and the enemy looks only at you."),
    ("p", "rallying_spirit", 1, 1, 2, '.req("commanders_presence", 1).bonus(healOnKill(0.015))',
     "banner glow heal LIFE",
     "鼓舞", "Rallying Spirit", "敵を倒すとHPが回復する。", "Defeating a foe restores your health."),
    ("a", "chain_hook", 1, 2, 2,
     '.req("shield_throw", 1).cd(12).theme(STEEL).act(18, 14, 14, hit(8, line(7.0, 1.2), 1.8, 0.0))'
     '.fx(afflict(0, PULL, 1, 0.5), afflict(0, SLOW, 60, 2), vis(0, "chains"))',
     "chains beam pull STEEL",
     "鎖鉤", "Chain Hook",
     "鎖を放って一直線上の敵を絡め取り、手元へ引き寄せて足を鈍らせる。",
     "Cast a hooked chain: foes in its line are caught, dragged to you and slowed."),
    ("p", "vanguard", 1, 3, 2, '.req("guardians_leap", 1).bonus(stats(mods().moveSpeed(0.02).stamina(5)))',
     "boot speed - WIND",
     "先陣", "Vanguard", "移動速度と最大スタミナが上がる。", "Raises movement speed and maximum stamina."),

    ("a", "rallying_banner", 1, 0, 3,
     '.req("iron_vow", 1).cd(36).theme(HOLY).act(18, 14, 18, pause(6))'
     '.fx(allies(0, ATTACK_UP, 0.15, 240, 10), allies(0, REGENERATION, 0, 240, 10),'
     ' healZone(0, CASTER, 0, 5.0, 8, 20, 0.02, 0), vis(0, "aura"), vis(0, "pillar"))',
     "banner pillar up HOLY",
     "結束の旗", "Rallying Banner",
     "旗を掲げて仲間を奮い立たせる。周囲の仲間の攻撃力が上がり、旗のもとでHPが少しずつ回復する。",
     "Raise the banner: allies around you strike harder and slowly heal while they stand beneath it."),
    ("a", "taunting_blow", 1, 1, 3,
     '.req("chain_hook", 1).cd(10).theme(STEEL).act(16, 12, 14, hit(6, arc(3.8, 80), 2.6, 0.6))'
     '.fx(taunt(0, 8, 120), afflict(0, VULNERABLE, 80, 0.12), vis(0, "slash"), vis(0, "roar"))',
     "sword arc taunt STEEL",
     "挑発撃", "Taunting Blow",
     "わざと隙を見せるような一撃。打った敵は脆弱になり、周りの敵も引きつける。",
     "A blow that mocks the foe: those it hits grow vulnerable and the ones around turn on you."),
    ("p", "warlords_grip", 1, 2, 3, '.req("rallying_spirit", 3).bonus(proc(STUN, 0.03, 20, 0, true))',
     "hand burst stun STEEL",
     "覇者の握力", "Warlord's Grip", "近接攻撃が確率で敵を気絶させる。", "Your melee hits may stun the foe."),
    ("a", "sentinel_charge", 1, 3, 3,
     '.req("guardians_leap", 3).cd(16).theme(WIND).act(22, 18, 20, hits(4, 5, 3, line(4.0, 1.6), 1.2, 1.0))'
     '.fx(dash(START, 8), allies(2, SPEED, 1, 100, 6), vis(START, "blink"), vis(0, "thrust"), vis(2, "impact"))',
     "lance speed x3 WIND",
     "哨兵突撃", "Sentinel Charge",
     "盾を構えて突進し、進路の敵を三度押し込む。駆け抜けた先の仲間を加速させる。",
     "Charge behind your shield, ramming everything in the way three times; allies where you end up speed up."),

    ("a", "line_of_sanctuary", 1, 0, 4,
     '.req("rallying_banner", 1).cd(40).theme(HOLY).act(20, 16, 22, pause(6))'
     '.fx(shieldAllies(0, 0.3, 200, 10), cleanse(0, 10), allies(0, DEFENSE_UP, 0.2, 200, 10), vis(START, "cast"),'
     ' vis(0, "shield"), vis(0, "sigil"))',
     "shield waves shield HOLY",
     "守りの戦線", "Line of Sanctuary",
     "戦線を敷いて仲間を守る。周囲の仲間にシールドと防御の加護を与え、状態異常を払う。",
     "Draw the line: allies around you gain shields and harder armor, and their ailments are cleansed."),
    ("p", "born_leader", 1, 1, 4, '.req("warlords_grip", 1).bonus(power(0.03))',
     "crown halo star HOLY",
     "生まれながらの将", "Born Leader", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "gravity_bash", 1, 2, 4,
     '.req("taunting_blow", 3).cd(20).theme(EARTH).act(24, 20, 22, hit(10, circle(5.5), 2.8, 0.0))'
     '.fx(afflict(0, PULL, 1, 0.6), afflict(0, STUN, 30, 0), taunt(0, 12, 140), vis(0, "vortex"), vis(0, "quake"))',
     "gem spiral pull EARTH",
     "重圧打", "Gravity Bash",
     "大地を打って重圧の渦を起こす。周囲の敵を引き寄せて気絶させ、すべての敵の注意を奪う。",
     "Strike the ground and raise a vortex of weight: foes around are dragged in, stunned and fixed on you."),
    ("p", "steadfast_pace", 1, 3, 4, '.req("vanguard", 3).bonus(stats(mods().guard(0.02)), saver(0.02))',
     "boot glow stamina STEEL",
     "泰然", "Steadfast", "ガード性能が上がり、スタミナ消費が減る。", "Your guard holds better and skills cost less stamina."),

    ("u", "oath_of_the_wall", 1, 0, 5,
     '.req("line_of_sanctuary", 3).cd(140).theme(HOLY).act(28, 24, 30, pause(8))'
     '.fx(shieldAllies(0, 0.6, 260, 14), allies(0, DEFENSE_UP, 0.35, 260, 14), allies(0, REGENERATION, 1, 260, 14),'
     ' taunt(0, 20, 260), cleanse(0, 14), vis(START, "cast"), vis(0, "pillar"), vis(0, "aura"), vis(0, "iron_wall"))',
     "shield_cross pillar heal HOLY",
     "城壁の誓約", "Oath of the Wall",
     "【必殺技】「誰一人倒れさせはしない」。広い範囲の仲間を厚いシールドと守りの加護、再生で包み、状態異常を払う。すべての敵を自分に引きつける。",
     "[Ultimate] \"No one falls today.\" Allies in a wide circle are wrapped in thick shields, hardened armor and "
     "regeneration, their ailments cleansed — and every foe turns on you."),
    ("p", "banner_of_legends", 1, 1, 5,
     '.req("born_leader", 3).bonus(stats(mods().healthPercent(0.03).physical(0.02)), healOnKill(0.01))',
     "banner nova up HOLY",
     "伝説の軍旗", "Banner of Legends", "最大HPと物理攻撃力が上がり、敵を倒すとHPが回復する。",
     "Raises maximum health and physical power; defeating a foe restores health."),
    ("a", "judgment_of_steel", 1, 2, 5,
     '.req("gravity_bash", 1).cd(26).theme(STEEL)'
     '.act(34, 30, 30, hit(8, arc(4.2, 100), 2.2, 0.6), hit(16, arc(4.2, 100), 2.2, 0.6), hit(26, circle(5.0), 3.6, 1.8))'
     '.fx(afflict(2, LAUNCH, 1, 0.9), taunt(2, 12, 160), vis(0, "slash"), vis(1, "slash"), vis(2, "nova"))',
     "greatsword arc2 x3 STEEL",
     "鋼鉄の裁き", "Judgment of Steel",
     "左右に大きく二度薙ぎ、最後に周囲すべてを打ち上げる三連撃。",
     "Two great sweeps, left and right, then a blow that hurls everything around you skyward."),
    ("p", "eternal_guardian", 1, 3, 5, '.req("steadfast_pace", 1).bonus(lastStand(0.04, 0.05))',
     "knight halo shield HOLY",
     "永遠の守護者", "Eternal Guardian", "HPが減るほど与えるダメージが増え、受けるダメージが大きく減る。",
     "The lower your health, the harder you hit and the far less you take."),
]
