"""死神 (Reaper): the assassin who walks with death. 刈取 reaps — wide sweeping cuts that drink life and finish the
weak; 冥府 opens the underworld — wraiths, chains of souls, fear and doom that falls a moment later."""

TITLE_JA = "死神"
TITLE_EN = "Reaper"
SUMMARY = "Sweeping reaps that drink life, executions, wraiths and doom."
DAMAGE = "PHYSICAL"
BRANCHES = [("刈取", "Harvest"), ("冥府", "Underworld")]

NODES = [
    # ------------------------------------------------------------------ 0: 刈取
    ("a", "soul_reap", 0, 0, 1,
     '.cd(8).theme(SHADOW).act(18, 14, 15, hit(7, arc(4.6, 110), 2.4, 0.6))'
     '.fx(siphon(0, 0.15, 0), vis(0, "slash"), vis(0, "shadow"))',
     "scythe arc heal SHADOW",
     "魂刈り", "Soul Reap",
     "大きく弧を描いて前方を薙ぎ払う。与えたダメージの一部でHPを回復する。",
     "Reap a wide arc in front of you; part of the damage you deal flows back as health."),
    ("a", "deaths_footsteps", 0, 1, 1,
     '.cd(10).theme(SHADOW).act(16, 12, 14, hit(6, circle(3.0), 1.8, 0.6))'
     '.fx(dash(START, 5), afflict(0, WEAKEN, 100, 0.15), vis(START, "blink"), vis(0, "impact"))',
     "boot mist weaken SHADOW",
     "死神の足音", "Death's Footsteps",
     "音もなく敵の懐へ滑り込み、周りを斬りつけて弱らせる。",
     "Glide soundlessly into the foe's midst and cut all around you, leaving them weakened."),
    ("p", "reapers_instinct", 0, 2, 1, '.bonus(lifesteal(0.01))',
     "drop - heal BLOOD",
     "刈り手の本能", "Reaper's Instinct", "与えたダメージの一部でHPを回復する。", "Part of the damage you deal heals you."),
    ("a", "mark_of_doom", 0, 3, 1,
     '.cd(12).theme(SHADOW).act(16, 12, 12, hit(6, ahead(2.0, 3.0), 1.2, 0.2))'
     '.fx(afflict(0, VULNERABLE, 200, 0.25), vis(START, "cast"), vis(0, "sigil"))',
     "skull halo vuln SHADOW",
     "終焉の印", "Mark of Doom",
     "前方の敵に終わりの印を刻む。印を刻まれた敵は長い間、受けるダメージが増える。",
     "Brand the foe ahead with the mark of the end; for a long while it takes more damage from everything."),

    ("a", "crescent_reap", 0, 0, 2,
     '.req("soul_reap", 3).cd(12).theme(SHADOW).act(20, 16, 18, hit(8, circle(4.2), 2.8, 1.0))'
     '.fx(afflict(0, BLEED, 80, 0.4), vis(0, "spin"), vis(0, "slash"))',
     "crescent spiral bleed SHADOW",
     "三日月刈り", "Crescent Reap",
     "身を翻して周りを一回転に刈り取る。刈られた敵は血を流す。",
     "Whirl once and reap everything around you; what you cut bleeds."),
    ("p", "cold_hands", 0, 1, 2, '.req("reapers_instinct", 1).bonus(stats(mods().physical(0.02).crit(0.01)))',
     "hand - crit FROST",
     "冷たき手", "Cold Hands", "物理攻撃力と会心率が上がる。", "Raises physical power and critical chance."),
    ("a", "execution", 0, 2, 2,
     '.req("mark_of_doom", 1).cd(16).theme(BLOOD).act(22, 18, 18, hit(10, ahead(2.2, 2.5), 3.0, 0.8))'
     '.fx(execute(0, 0.35, 1.0), vis(START, "shadow"), vis(0, "thrust"))',
     "scythe beamd skull BLOOD",
     "処刑", "Execution",
     "刃を振り下ろして裁きを下す。HPの少ない敵には大きなダメージを与える。",
     "Bring the blade down as a sentence; foes low on health take far more damage."),
    ("p", "reapers_due", 0, 3, 2, '.req("mark_of_doom", 1).bonus(healOnKill(0.02))',
     "skull glow heal BLOOD",
     "死神の取り分", "The Reaper's Due", "敵を倒すとHPが回復する。", "Defeating a foe restores your health."),

    ("a", "soul_harvest", 0, 0, 3,
     '.req("crescent_reap", 1).cd(20).theme(SHADOW).act(22, 18, 20, hit(10, circle(5.0), 2.2, 0.4))'
     '.fx(siphon(0, 0.25, 0), vis(0, "vortex"), vis(0, "shadow"))',
     "scythe spiral heal SHADOW",
     "魂の収穫", "Soul Harvest",
     "周りの敵から魂を刈り取る。与えたダメージの多くがHPとなって返ってくる。",
     "Harvest the souls of the foes around you; much of the damage returns to you as health."),
    ("a", "scythe_storm", 0, 1, 3,
     '.req("cold_hands", 1).cd(18).theme(SHADOW).act(28, 24, 22, hits(4, 4, 5, circle(3.8), 0.8, 0.3))'
     '.fx(vis(0, "blade_storm"), vis(4, "spin"))',
     "scythe claws x5 SHADOW",
     "鎌嵐", "Scythe Storm",
     "刃を嵐のように振り回し、周りの敵を五度切り刻む。",
     "Spin your blades like a storm, cutting every foe around you five times."),
    ("p", "deathly_focus", 0, 2, 3, '.req("reapers_due", 1).bonus(stats(mods().critDamage(0.08)))',
     "eye glow crit SHADOW",
     "死の集中", "Deathly Focus", "会心ダメージが大きく上がる。", "Greatly raises critical damage."),
    ("a", "death_knell", 0, 3, 3,
     '.req("execution", 1).cd(22).theme(SHADOW).act(20, 16, 18, hit(8, circle(6.0), 1.6, 0.4))'
     '.fx(afflict(0, WEAKEN, 120, 0.2), afflict(0, SLOW, 120, 2), vis(0, "roar"), vis(0, "impact"))',
     "bell ring weaken SHADOW",
     "弔鐘", "Death Knell",
     "弔いの鐘を鳴らす。響きを聞いた周りの敵は力が抜け、足取りが重くなる。",
     "Toll the bell of mourning: the foes around who hear it grow weak and heavy-footed."),

    ("a", "reapers_leap", 0, 0, 4,
     '.req("soul_harvest", 3).cd(20).theme(SHADOW).act(26, 22, 22, hit(18, circle(4.0), 3.6, 1.2))'
     '.fx(leap(START, 8, 3.5), afflict(0, VULNERABLE, 100, 0.2), vis(START, "blink"), vis(0, "smite"))',
     "wings smite vuln SHADOW",
     "死神の跳躍", "Reaper's Descent",
     "高く跳び上がり、敵の真ん中へ鎌を振り下ろしながら降り立つ。",
     "Leap high and come down in the midst of the foe with your blades falling first."),
    ("p", "undying_hunger", 0, 1, 4, '.req("deathly_focus", 1).bonus(lastStand(0.07, 0.0))',
     "fangs glow - BLOOD",
     "尽きぬ飢え", "Undying Hunger", "HPが減るほど与えるダメージが大きく増える。",
     "The lower your health, the harder you strike."),
    ("a", "severance", 0, 2, 4,
     '.req("death_knell", 1).cd(22).theme(BLOOD).act(24, 20, 24, hit(10, beam(9.0, 1.4), 3.8, 0.6))'
     '.fx(afflict(0, BLEED, 100, 0.5), vis(START, "shadow"), vis(0, "slash"), vis(0, "ray"))',
     "scythe beam bleed BLOOD",
     "断絶", "Severance",
     "空間ごと断ち切る一閃を放ち、一直線上の敵を深く切り裂く。",
     "Loose a cut that severs the very air, carving deep into every foe along its line."),
    ("p", "grave_strength", 0, 3, 4, '.req("death_knell", 1).bonus(stats(mods().physical(0.03)))',
     "skull - up EARTH",
     "冥き膂力", "Grave Strength", "物理攻撃力が上がる。", "Raises physical power."),

    ("u", "crimson_harvest", 0, 0, 5,
     '.req("reapers_leap", 3).cd(120).theme(BLOOD)'
     '.act(44, 40, 40, pause(6), hit(16, circle(7.0), 2.0, 0.4), hit(28, circle(7.5), 4.5, 1.6))'
     '.fx(afflict(1, SLOW, 80, 3), execute(2, 0.3, 1.0), siphon(2, 0.3, 0), vis(START, "shadow"), vis(0, "vortex"),'
     ' vis(1, "spin"), vis(2, "nova"), vis(2, "slash"))',
     "moon spiral skull BLOOD",
     "奥義・紅月の大刈取", "Secret Art: Crimson Moon Harvest",
     "【必殺技】紅い月を背に、周りの敵を二度大きく刈り取る。一度目で足を止め、二度目は弱った敵ほど深く斬り、与えたダメージでHPを大きく回復する。",
     "[Ultimate] Under a crimson moon, reap all around you twice: the first sweep holds them, the second cuts deeper "
     "the weaker they are and heals you greatly."),
    ("p", "lord_of_harvest", 0, 1, 5, '.req("undying_hunger", 1).bonus(stats(mods().crit(0.02).critDamage(0.05)))',
     "crown glow crit SHADOW",
     "刈取の王", "Lord of the Harvest", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "guillotine", 0, 2, 5,
     '.req("severance", 3).cd(40).theme(BLOOD).act(30, 26, 30, hit(16, ahead(2.5, 2.5), 5.0, 1.0))'
     '.fx(leap(START, 4, 4.0), execute(0, 0.4, 1.2), vis(START, "blink"), vis(0, "judgment"), vis(0, "impact"))',
     "greatsword smite skull BLOOD",
     "断頭", "Guillotine",
     "宙へ跳び、落下の勢いで一太刀を振り下ろす。HPの少ない敵ほど大きなダメージを与える。",
     "Spring into the air and bring one cut down with all your weight; the weaker the foe, the more it takes."),
    ("p", "deaths_embrace", 0, 3, 5, '.req("grave_strength", 3).bonus(healOnKill(0.02), lifesteal(0.01))',
     "heart halo heal BLOOD",
     "死の抱擁", "Death's Embrace", "敵を倒すとHPが回復し、与えたダメージの一部でもHPを回復する。",
     "Defeating a foe heals you, and part of the damage you deal does too."),

    # ------------------------------------------------------------------ 1: 冥府
    ("a", "wraith_bolt", 1, 0, 1,
     '.cd(8).theme(SHADOW).act(18, 14, 14, shot(7, orb("shadow_bolt", 2.0, 30), 2.2, 0.4))'
     '.fx(afflict(0, WEAKEN, 100, 0.15), vis(START, "cast"))',
     "orb mist weaken SHADOW",
     "怨霊弾", "Wraith Bolt",
     "怨霊を弾にして放つ。当たった敵は力を吸われて弱る。",
     "Fire a wraith like a bolt; the foe it strikes has its strength drained away."),
    ("a", "terror", 1, 1, 1,
     '.cd(16).theme(SHADOW).act(16, 12, 14, hit(6, circle(5.0), 1.0, 0.2))'
     '.fx(afflict(0, SLOW, 100, 3), afflict(0, WEAKEN, 100, 0.2), vis(0, "roar"), vis(0, "shadow"))',
     "mask ring slow SHADOW",
     "恐怖", "Terror",
     "死の気配を放って周りの敵をすくませる。敵は足が止まり、力が抜ける。",
     "Let the presence of death wash over the foes around you: they freeze in place and their strength fails."),
    ("p", "underworld_pact", 1, 2, 1, '.bonus(stats(mods().stamina(4).healthPercent(0.02)))',
     "chains - heart SHADOW",
     "冥府の契約", "Underworld Pact", "最大スタミナと最大HPが上がる。", "Raises maximum stamina and maximum health."),
    ("a", "soul_chain", 1, 3, 1,
     '.cd(12).theme(SHADOW).act(18, 14, 14, hit(7, line(8.0, 1.0), 1.4, 0.0))'
     '.fx(afflict(0, PULL, 1, 1.2), afflict(0, STUN, 20, 0), vis(0, "chains"))',
     "chains beamd pull SHADOW",
     "魂鎖", "Soul Chain",
     "魂を縛る鎖を投げ、捕らえた敵を引き寄せて一瞬動きを止める。",
     "Cast a chain that binds the soul: the foe it catches is dragged to you and briefly stunned."),

    ("a", "spectral_blades", 1, 0, 2,
     '.req("wraith_bolt", 3).cd(12).theme(SHADOW)'
     '.act(20, 16, 16, shots(4, 3, 3, orb("shadow_bolt", 2.6, 24).withPierce(3), 0.9, 0.1))'
     '.fx(vis(START, "shadow"), vis(2, "ray"))',
     "daggers ring x3 SHADOW",
     "亡霊の刃", "Spectral Blades",
     "亡霊の刃を三本続けて放つ。刃は敵を貫いて飛んでいく。",
     "Send three spectral blades flying one after another; each passes through the foes it strikes."),
    ("p", "dread_presence", 1, 1, 2, '.req("underworld_pact", 1).bonus(proc(WEAKEN, 0.03, 80, 0.15, true))',
     "eye mist weaken SHADOW",
     "畏怖の気配", "Dread Presence", "近接攻撃が一定確率で敵を弱らせる。", "Your melee hits may weaken the foe."),
    ("a", "grave_mist", 1, 2, 2,
     '.req("terror", 1).cd(18).theme(VENOM).act(16, 12, 16, pause(4))'
     '.fx(zone(0, CASTER, 0, 5.0, 0, 8, 10, 0.5, POISON, 60, 0.3), vis(0, "mist"), vis(0, "smoke"))',
     "skull cloud poison VENOM",
     "墓霧", "Grave Mist",
     "墓場の瘴気を足元に広げる。しばらくの間、霧の中の敵は毒に冒され続ける。",
     "Spread the miasma of the grave at your feet; for a while every foe in the mist is poisoned."),
    ("p", "shade_skin", 1, 3, 2, '.req("soul_chain", 1).bonus(evasion(0.015))',
     "hood - - SHADOW",
     "影の衣", "Shade Skin", "攻撃を回避しやすくなる。", "You dodge attacks more often."),

    ("a", "legion_of_the_lost", 1, 0, 3,
     '.req("spectral_blades", 1).cd(22).theme(SHADOW).act(30, 26, 22, hits(6, 5, 4, circle(4.5), 1.0, 0.2))'
     '.fx(afflict(3, WEAKEN, 100, 0.2), vis(START, "shadow"), vis(0, "storm"), vis(3, "nova"))',
     "skull orbit x4 SHADOW",
     "亡者の軍勢", "Legion of the Lost",
     "亡者の群れを呼び起こし、周りの敵へ四度襲いかからせる。",
     "Rouse a host of the dead to fall upon the foes around you four times."),
    ("a", "underworld_gate", 1, 1, 3,
     '.req("dread_presence", 1).cd(26).theme(SHADOW).act(18, 14, 18, pause(6))'
     '.fx(zone(0, LOOK, 10.0, 4.0, 6, 8, 8, 0.8, SLOW, 40, 3), vis(START, "cast"), vis(0, "rift"))',
     "portal spiral slow SHADOW",
     "冥府の門", "Gate of the Underworld",
     "狙った場所に冥府の門を開く。門に引かれた敵は動きが鈍り、しばらく傷つけられ続ける。",
     "Open a gate to the underworld where you look; foes near it are dragged slow and keep taking harm."),
    ("p", "necrotic_touch", 1, 2, 3, '.req("dread_presence", 1).bonus(proc(POISON, 0.03, 80, 0.25, true))',
     "hand cloud poison VENOM",
     "腐蝕の手", "Necrotic Touch", "近接攻撃が一定確率で敵を毒に冒す。", "Your melee hits may poison the foe."),
    ("a", "spirit_shroud", 1, 3, 3,
     '.req("shade_skin", 1).cd(36).theme(SHADOW).act(12, 8, 12, pause(2))'
     '.fx(self(0, DEFENSE_UP, 0.3, 200), self(0, EVASION, 0.2, 200), self(0, LIFESTEAL, 0.1, 200), vis(0, "aura"),'
     ' vis(0, "shadow"))',
     "hood halo shield SHADOW",
     "霊衣", "Spirit Shroud",
     "亡霊の衣をまとう。しばらくの間、受けるダメージが減り、攻撃をかわしやすくなり、攻撃でHPを吸収する。",
     "Wrap yourself in a shroud of ghosts: for a while you take less damage, dodge more, and every blow heals you."),

    ("a", "requiem_chains", 1, 0, 4,
     '.req("legion_of_the_lost", 1).cd(24).theme(SHADOW).act(22, 18, 22, hit(10, circle(6.0), 2.0, 0.0))'
     '.fx(afflict(0, STUN, 40, 0), afflict(0, VULNERABLE, 100, 0.2), vis(0, "chains"), vis(0, "nova"))',
     "chains nova stun SHADOW",
     "鎮魂の鎖", "Chains of Requiem",
     "周りの地面から鎖が伸び、敵を縛り上げて動きを止め、守りを崩す。",
     "Chains burst from the ground around you, binding every foe fast and laying their guard open."),
    ("p", "dark_might", 1, 1, 4, '.req("necrotic_touch", 1).bonus(power(0.03))',
     "gem glow star SHADOW",
     "冥き力", "Dark Might", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "soul_burst", 1, 2, 4,
     '.req("underworld_gate", 1).cd(22).theme(SHADOW).act(20, 16, 22, hit(9, circle(5.0), 3.4, 1.6))'
     '.fx(afflict(0, BLIND, 60, 0), vis(0, "burst"), vis(0, "nova"))',
     "orb burst blind SHADOW",
     "魂爆", "Soul Burst",
     "集めた魂を一気に解き放ち、周りの敵を吹き飛ばして目をくらませる。",
     "Release the souls you have gathered all at once, blasting the foes around you back and blinding them."),
    ("p", "death_ward", 1, 3, 4, '.req("spirit_shroud", 1).bonus(lastStand(0.0, 0.05))',
     "shield_small mist shield SHADOW",
     "死の守り", "Death Ward", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),

    ("u", "sending_to_yomi", 1, 0, 5,
     '.req("requiem_chains", 3).cd(130).theme(SHADOW)'
     '.act(46, 42, 40, pause(6), hit(14, circle(8.0), 1.2, 0.0), hit(30, circle(8.0), 4.8, 1.4))'
     '.fx(afflict(1, PULL, 1, 1.0), afflict(2, STUN, 40, 0), afflict(2, VULNERABLE, 160, 0.3), vis(START, "cast"),'
     ' vis(0, "rift"), vis(1, "vortex"), vis(2, "nova"), vis(2, "shadow"))',
     "portal nova pull SHADOW",
     "奥義・黄泉送り", "Secret Art: Sending to Yomi",
     "【必殺技】足元に黄泉への裂け目を開き、周りの敵をすべて引きずり込む。裂け目が閉じる時の衝撃で敵を気絶させ、守りを砕く。",
     "[Ultimate] Open a rift to Yomi at your feet and drag every foe around you in; as it closes, the shock stuns "
     "them and shatters their guard."),
    ("p", "ferryman", 1, 1, 5, '.req("dark_might", 1).bonus(cooldown(0.02), healOnKill(0.01))',
     "lantern glow clock SHADOW",
     "渡し守", "The Ferryman", "スキルの再使用時間が短くなり、敵を倒すとHPが回復する。",
     "Skills recover faster; defeating a foe heals you."),
    ("a", "sentence_of_death", 1, 2, 5,
     '.req("soul_burst", 3).cd(34).theme(SHADOW).act(20, 16, 24, hit(6, ahead(2.0, 4.0), 1.0, 0.0))'
     '.fx(afflict(0, VULNERABLE, 120, 0.25), zone(0, LOOK, 10.0, 3.0, 40, 1, 1, 6.0), vis(0, "sigil"),'
     ' vis(0, "judgment"))',
     "hourglass smite skull SHADOW",
     "死の宣告", "Sentence of Death",
     "前方の敵に死を宣告して守りを崩す。二秒後、狙った場所に死が落ち、大きなダメージを与える。",
     "Pronounce death on the foe ahead and lay its guard open; two seconds later death falls where you aimed."),
    ("p", "eternal_night", 1, 3, 5, '.req("death_ward", 3).bonus(stats(mods().crit(0.02).physical(0.02)))',
     "moon halo star SHADOW",
     "常夜", "Eternal Night", "会心率と物理攻撃力が上がる。", "Raises critical chance and physical power."),
]
