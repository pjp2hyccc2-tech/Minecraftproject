"""呪剣士 (Hexblade): the spellblade who took up a cursed sword. 血呪 is blood magic — bleeding cuts, drained life,
brands and curses that grow stronger as the wielder bleeds; 魔剣 is the demon blade — poison, darkness, the void
and the sword of the netherlord."""

TITLE_JA = "呪剣士"
TITLE_EN = "Hexblade"
SUMMARY = "Blood magic that feeds on wounds, and a demon blade of curses and darkness."
DAMAGE = "MAGIC"
BRANCHES = [("血呪", "Blood Hex"), ("魔剣", "Demon Blade")]

NODES = [
    # ------------------------------------------------------------------ 0: 血呪
    ("a", "blood_edge", 0, 0, 1,
     '.cd(8).theme(BLOOD).act(18, 14, 14, hit(7, arc(4.0, 70), 2.2, 0.4))'
     '.fx(afflict(0, BLEED, 80, 0.4), siphon(0, 0.1, 0), vis(0, "slash"), vis(0, "impact"))',
     "sword arc bleed BLOOD",
     "血刃", "Blood Edge",
     "血の呪いを帯びた刃で斬る。斬られた敵は血を流し、流れた血の一部が自分のHPとなる。",
     "Cut with a blade steeped in a blood curse: the foe bleeds, and some of what it loses becomes your health."),
    ("a", "hex_brand", 0, 1, 1,
     '.cd(12).theme(SHADOW).act(16, 12, 12, hit(6, ahead(2.0, 3.0), 1.2, 0.2))'
     '.fx(afflict(0, VULNERABLE, 200, 0.2), afflict(0, WEAKEN, 200, 0.12), vis(START, "cast"), vis(0, "sigil"))',
     "rune glow vuln SHADOW",
     "呪印", "Hex Brand",
     "前方の敵に呪いの印を刻む。印を刻まれた敵は長い間、力が衰え、受けるダメージが増える。",
     "Brand the foe ahead with a hex: for a long while it grows weaker and takes more damage."),
    ("p", "price_of_blood", 0, 2, 1, '.bonus(lastStand(0.05, 0.0))',
     "drop - - BLOOD",
     "血の代償", "Price of Blood", "HPが減るほど与えるダメージが増える。", "The lower your health, the harder you strike."),
    ("a", "crimson_dash", 0, 3, 1,
     '.cd(10).theme(BLOOD).act(16, 12, 14, hit(6, line(7.0, 1.4), 2.0, 0.6))'
     '.fx(dash(START, 7), afflict(0, BLEED, 60, 0.3), vis(START, "blink"), vis(0, "thrust"))',
     "boot speed bleed BLOOD",
     "紅の疾走", "Crimson Dash",
     "紅い残光を引いて駆け抜け、通り道の敵を斬って出血させる。",
     "Dash through in a crimson streak, cutting the foes in your path and leaving them bleeding."),

    ("a", "sanguine_rite", 0, 0, 2,
     '.req("blood_edge", 3).cd(30).theme(BLOOD).act(12, 8, 12, pause(3))'
     '.fx(self(0, ATTACK_UP, 0.3, 160), self(0, LIFESTEAL, 0.12, 160), vis(0, "aura"), vis(0, "flame_blade"))',
     "heart flamewall up BLOOD",
     "血の儀式", "Sanguine Rite",
     "血の儀式で呪いの力を呼び覚ます。しばらくの間、与えるダメージが大きく増え、攻撃でHPを吸収する。",
     "Wake the curse with a rite of blood: for a while you deal far more damage and every blow heals you."),
    ("p", "thirst", 0, 1, 2, '.req("price_of_blood", 1).bonus(lifesteal(0.01))',
     "fangs - heal BLOOD",
     "渇き", "Thirst", "与えたダメージの一部でHPを回復する。", "Part of the damage you deal heals you."),
    ("a", "tide_of_blood", 0, 2, 2,
     '.req("hex_brand", 1).cd(16).theme(BLOOD).act(18, 14, 18, hit(8, circle(4.5), 2.0, 0.6))'
     '.fx(afflict(0, BLEED, 100, 0.5), vis(0, "nova"), vis(0, "burst"))',
     "drop waves bleed BLOOD",
     "血潮の波", "Tide of Blood",
     "血の波を周りへ広げ、飲み込まれた敵に深い傷を負わせる。",
     "Spread a wave of blood around you; the foes it swallows are left with deep, bleeding wounds."),
    ("p", "cursed_vigor", 0, 3, 2, '.req("crimson_dash", 1).bonus(stats(mods().healthPercent(0.02)), healOnKill(0.01))',
     "heart glow heart SHADOW",
     "呪われし活力", "Cursed Vigor", "最大HPが上がり、敵を倒すとHPが回復する。",
     "Raises maximum health; defeating a foe heals you."),

    ("a", "blood_moon_cut", 0, 0, 3,
     '.req("sanguine_rite", 1).cd(18).theme(BLOOD).act(22, 18, 20, hit(10, beam(9.0, 1.6), 3.0, 0.6))'
     '.fx(afflict(0, BLEED, 80, 0.4), siphon(0, 0.15, 0), vis(START, "cast"), vis(0, "arcane_slash"))',
     "moon beam bleed BLOOD",
     "血月斬", "Blood Moon Cut",
     "紅い月のような斬撃を飛ばし、一直線上の敵を切り裂いて出血させる。流れた血の一部がHPとなる。",
     "Loose a cut like a blood-red moon down a line, slicing the foes in it open; some of their blood heals you."),
    ("a", "curse_chain", 0, 1, 3,
     '.req("thirst", 1).cd(18).theme(SHADOW).act(18, 14, 16, hit(7, line(8.0, 1.2), 1.4, 0.0))'
     '.fx(afflict(0, PULL, 1, 1.0), afflict(0, WEAKEN, 120, 0.2), vis(0, "chains"))',
     "chains beamd weaken SHADOW",
     "呪鎖", "Chain of Hexes",
     "呪いの鎖を放って敵を引き寄せ、長い間その力を縛る。",
     "Throw out a chain of hexes that drags the foe to you and binds its strength for a long while."),
    ("p", "dark_pact", 0, 2, 3, '.req("thirst", 1).bonus(power(0.02), stats(mods().crit(0.01)))',
     "hand glow star SHADOW",
     "闇の契約", "Dark Pact", "スキルの威力と会心率が上がる。", "Raises skill power and critical chance."),
    ("a", "vein_burst", 0, 3, 3,
     '.req("tide_of_blood", 1).cd(20).theme(BLOOD).act(18, 14, 20, hit(8, circle(5.0), 2.6, 1.0))'
     '.fx(afflict(0, VULNERABLE, 100, 0.2), vis(0, "burst"), vis(0, "impact"))',
     "heart burst vuln BLOOD",
     "血脈爆ぜ", "Vein Burst",
     "周りの敵の血を沸き立たせて弾けさせ、守りを崩す。",
     "Make the blood of the foes around you boil and burst, breaking their guard."),

    ("a", "crimson_tempest", 0, 0, 4,
     '.req("blood_moon_cut", 1).cd(24).theme(BLOOD).act(26, 22, 24, hits(4, 3, 6, circle(4.5), 0.6, 0.2))'
     '.fx(afflict(2, BLEED, 80, 0.4), siphon(5, 0.2, 0), vis(0, "blade_storm"), vis(5, "storm"))',
     "sword spiral x6 BLOOD",
     "紅蓮の嵐", "Crimson Tempest",
     "紅い刃の嵐となって周りの敵を六度斬り刻む。最後の一太刀で流れた血がHPとなる。",
     "Become a storm of crimson blades and cut the foes around you six times; the last cut's blood heals you."),
    ("p", "bloodlust", 0, 1, 4, '.req("dark_pact", 1).bonus(stats(mods().critDamage(0.08)))',
     "fangs glow crit BLOOD",
     "血の渇望", "Bloodlust", "会心ダメージが大きく上がる。", "Greatly raises critical damage."),
    ("a", "hex_detonation", 0, 2, 4,
     '.req("curse_chain", 1).cd(24).theme(SHADOW).act(16, 12, 20, pause(4))'
     '.fx(zone(0, LOOK, 10.0, 3.5, 20, 1, 1, 4.0, WEAKEN, 100, 0.3), vis(START, "cast"), vis(0, "sigil"), vis(0, "rift"))',
     "rune nova weaken SHADOW",
     "呪爆", "Hex Detonation",
     "狙った場所に呪いを仕込む。一秒後に呪いが炸裂して大きなダメージを与え、敵の力を奪う。",
     "Plant a hex where you look; a second later it erupts, dealing heavy damage and sapping the foes' strength."),
    ("p", "undying_blood", 0, 3, 4, '.req("vein_burst", 1).bonus(lastStand(0.0, 0.05))',
     "drop halo shield BLOOD",
     "不死の血", "Undying Blood", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),

    ("u", "curse_of_the_red_moon", 0, 0, 5,
     '.req("crimson_tempest", 3).cd(120).theme(BLOOD)'
     '.act(44, 40, 40, pause(8), hit(18, circle(7.0), 1.6, 0.2), hit(32, circle(8.0), 4.8, 1.6))'
     '.fx(self(0, LIFESTEAL, 0.15, 200), afflict(1, BLEED, 120, 0.6), siphon(2, 0.35, 0),'
     ' afflict(2, VULNERABLE, 160, 0.3), vis(START, "sigil"), vis(0, "vortex"), vis(1, "shadow"), vis(2, "nova"),'
     ' vis(2, "slash"))',
     "moon spiral bleed BLOOD",
     "奥義・紅き月の呪い", "Secret Art: Curse of the Red Moon",
     "【必殺技】紅い月の呪いを呼び降ろす。周りの敵に深い傷を刻み、続く大きな一撃で守りを砕いて、流れた血でHPを大きく回復する。しばらく攻撃でHPを吸収する。",
     "[Ultimate] Call down the curse of the red moon: the foes around you are cut deep, then a great blow shatters "
     "their guard and their blood heals you greatly. For a while every blow keeps healing you."),
    ("p", "blood_sovereign", 0, 1, 5, '.req("bloodlust", 1).bonus(lifesteal(0.01), stats(mods().magic(0.02)))',
     "crown glow heal BLOOD",
     "血の君主", "Blood Sovereign", "与えたダメージの一部でHPを回復し、魔法攻撃力が上がる。",
     "Part of the damage you deal heals you, and your magic power rises."),
    ("a", "sacrificial_strike", 0, 2, 5,
     '.req("hex_detonation", 3).cd(36).theme(BLOOD).act(26, 22, 26, hit(14, ahead(2.5, 2.5), 5.0, 1.2))'
     '.fx(execute(0, 0.3, 1.0), vis(START, "cast"), vis(0, "smite"), vis(0, "impact"))',
     "greatsword pillar skull BLOOD",
     "贄の一撃", "Sacrificial Strike",
     "呪いに贄を捧げるように渾身の一撃を叩きつける。HPの少ない敵ほど大きなダメージを与える。",
     "Strike with everything, as if offering the foe to the curse; the weaker it is, the more it takes."),
    ("p", "crimson_soul", 0, 3, 5, '.req("undying_blood", 3).bonus(stats(mods().magic(0.02).crit(0.02)))',
     "gem halo crit BLOOD",
     "紅の魂", "Crimson Soul", "魔法攻撃力と会心率が上がる。", "Raises magic power and critical chance."),

    # ------------------------------------------------------------------ 1: 魔剣
    ("a", "venom_edge", 1, 0, 1,
     '.cd(16).theme(VENOM).act(18, 14, 14, hit(8, arc(3.8, 60), 1.8, 0.4))'
     '.fx(self(0, VENOM_WEAPON, 0, 240), afflict(0, POISON, 80, 0.4), vis(0, "flame_blade"), vis(0, "slash"))',
     "sword cloud poison VENOM",
     "呪毒の刃", "Venom Edge",
     "刃に呪いの毒をまとわせて斬る。斬った敵は毒に冒され、しばらくの間、攻撃が敵を毒に冒す。",
     "Coat your blade in a cursed venom and cut: the foe is poisoned, and for a while every blow poisons."),
    ("a", "shadow_cleave", 1, 1, 1,
     '.cd(9).theme(SHADOW).act(18, 14, 15, hit(7, arc(4.6, 100), 2.2, 0.6))'
     '.fx(afflict(0, BLIND, 40, 0), vis(0, "slash"), vis(0, "shadow"))',
     "greatsword mist blind SHADOW",
     "影薙ぎ", "Shadow Cleave",
     "闇をまとった刃で前方を大きく薙ぎ払い、斬った敵の目をくらませる。",
     "Sweep a wide arc ahead with a blade wrapped in darkness, blinding the foes you cut."),
    ("p", "dark_affinity", 1, 2, 1, '.bonus(stats(mods().magic(0.03)))',
     "orb - - SHADOW",
     "闇の親和", "Dark Affinity", "魔法攻撃力が上がる。", "Raises magic power."),
    ("a", "void_lunge", 1, 3, 1,
     '.cd(10).theme(SHADOW).act(16, 12, 14, hit(6, line(8.0, 1.2), 2.4, 0.6))'
     '.fx(dash(START, 8), afflict(0, VULNERABLE, 80, 0.12), vis(START, "blink"), vis(0, "thrust"))',
     "portal speed vuln SHADOW",
     "虚空突き", "Void Lunge",
     "虚空を渡るように一気に間合いを詰めて突く。貫かれた敵は守りが崩れる。",
     "Cross the distance as if through the void and thrust; whoever you pierce loses their guard."),

    ("a", "demon_blade_unbound", 1, 0, 2,
     '.req("venom_edge", 1).cd(30).theme(SHADOW).act(12, 8, 12, pause(3))'
     '.fx(self(0, THUNDER_WEAPON, 0, 240), self(0, ATTACK_UP, 0.15, 240), vis(0, "lightning"), vis(0, "flame_blade"))',
     "bolt glow up SHADOW",
     "魔剣解放", "Demon Blade Unbound",
     "魔剣の封を解く。しばらくの間、闇の雷が刃に宿って攻撃が近くの別の敵へ走り、与えるダメージも増える。",
     "Break the demon blade's seal: for a while dark lightning lives in the edge, every blow arcs to another foe "
     "nearby, and you deal more damage."),
    ("p", "malevolence", 1, 1, 2, '.req("dark_affinity", 1).bonus(proc(WEAKEN, 0.03, 80, 0.15, true))',
     "eye glow weaken SHADOW",
     "悪意の刃", "Malevolence", "近接攻撃が一定確率で敵を弱らせる。", "Your melee hits may weaken the foe."),
    ("a", "night_reaver", 1, 2, 2,
     '.req("shadow_cleave", 1).cd(14).theme(SHADOW).act(22, 18, 18, hits(4, 4, 3, arc(4.0, 80), 1.0, 0.3))'
     '.fx(afflict(2, BLIND, 60, 0), vis(0, "slash"), vis(2, "shadow"))',
     "crescent claws x3 SHADOW",
     "夜裂き", "Night Reaver",
     "夜を裂くように三度斬りつける。最後の一太刀で敵の目をくらませる。",
     "Cut three times as if tearing the night apart; the last cut leaves the foe blinded."),
    ("p", "shadow_guard", 1, 3, 2, '.req("void_lunge", 1).bonus(evasion(0.015))',
     "hood - shield SHADOW",
     "影の守り", "Shadow Guard", "攻撃を回避しやすくなる。", "You dodge attacks more often."),

    ("a", "abyss_slash", 1, 0, 3,
     '.req("demon_blade_unbound", 1).cd(18).theme(SHADOW).act(22, 18, 20, hit(10, beam(10.0, 2.0), 3.2, 0.6))'
     '.fx(afflict(0, WEAKEN, 100, 0.2), vis(START, "cast"), vis(0, "arcane_slash"), vis(0, "rift"))',
     "greatsword beamd weaken SHADOW",
     "深淵斬り", "Abyss Slash",
     "深淵の闇を刃に乗せて飛ばし、一直線上の敵を斬って力を奪う。",
     "Send the darkness of the abyss flying on your blade, cutting every foe in its line and sapping its strength."),
    ("a", "curse_engraving", 1, 1, 3,
     '.req("malevolence", 1).cd(20).theme(VENOM).act(18, 14, 18, hit(8, circle(4.5), 1.6, 0.2))'
     '.fx(afflict(0, POISON, 120, 0.5), afflict(0, VULNERABLE, 100, 0.15), vis(0, "sigil"), vis(0, "mist"))',
     "rune cloud poison VENOM",
     "呪刻", "Curse Engraving",
     "周りの敵に呪いを刻み込む。刻まれた敵は重い毒に冒され、受けるダメージが増える。",
     "Engrave a curse into the foes around you: a heavy poison takes hold and they take more damage."),
    ("p", "grim_resolve", 1, 2, 3, '.req("malevolence", 1).bonus(power(0.03))',
     "skull glow star SHADOW",
     "冥き決意", "Grim Resolve", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "shadow_walk", 1, 3, 3,
     '.req("shadow_guard", 1).cd(18).theme(SHADOW).act(16, 12, 16, hit(6, circle(3.0), 1.6, 0.4))'
     '.fx(dash(START, 9), self(0, EVASION, 0.4, 60), vis(START, "shadow"), vis(0, "blink"))',
     "hood speed - SHADOW ghost",
     "影渡り", "Shadow Walk",
     "影から影へ渡って大きく移動し、着いた先で周りを斬りつける。しばらく攻撃をかわしやすくなる。",
     "Walk from shadow to shadow across a great distance and cut all around where you arrive; for a while "
     "attacks rarely find you."),

    ("a", "black_crescent", 1, 0, 4,
     '.req("abyss_slash", 1).cd(22).theme(SHADOW)'
     '.act(22, 18, 22, shot(9, orb("crescent", 2.2, 30).withPierce(8).withExplosion(2.0), 3.0, 0.6))'
     '.fx(afflict(0, BLIND, 60, 0), vis(START, "cast"), vis(0, "arcane_slash"), vis(0, "burst"))',
     "crescent burst blind SHADOW",
     "黒月閃", "Black Crescent",
     "黒い三日月の斬撃を放つ。敵を貫いて飛び、終点で弾けて周りの目をくらませる。",
     "Loose a black crescent cut that flies through the foes in its way and bursts at the end, blinding those "
     "around."),
    ("p", "cursed_armor", 1, 1, 4, '.req("grim_resolve", 1).bonus(stats(mods().defense(5)), thorns(0.03))',
     "chest mist thorns SHADOW",
     "呪いの鎧", "Cursed Armor", "防御力が上がり、受けたダメージの一部を跳ね返す。",
     "Raises defense; part of the damage you take is returned."),
    ("a", "soul_devour", 1, 2, 4,
     '.req("night_reaver", 3).cd(24).theme(SHADOW).act(20, 16, 22, hit(9, ahead(3.0, 3.0), 3.2, 0.4))'
     '.fx(siphon(0, 0.4, 0), stamina(0, 30), vis(0, "vortex"), vis(0, "shadow"))',
     "skull spiral heal SHADOW",
     "魂喰い", "Soul Devourer",
     "魔剣に前方の敵の魂を喰らわせる。与えたダメージの多くがHPとなり、スタミナも戻る。",
     "Let the demon blade devour the souls of the foes ahead: much of the damage becomes your health, and stamina "
     "returns."),
    ("p", "demon_pact", 1, 3, 4, '.req("shadow_walk", 1).bonus(stats(mods().crit(0.02).critDamage(0.04)))',
     "hand halo crit SHADOW",
     "魔との契約", "Demon Pact", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),

    ("u", "sword_of_the_netherlord", 1, 0, 5,
     '.req("black_crescent", 3).cd(120).theme(SHADOW)'
     '.act(46, 42, 40, pause(10), hit(24, beam(14.0, 3.0), 3.0, 0.6), hit(36, circle(8.0), 4.4, 1.8))'
     '.fx(afflict(1, VULNERABLE, 160, 0.3), afflict(2, WEAKEN, 160, 0.3), afflict(2, BLIND, 60, 0),'
     ' vis(START, "judgment_charge"), vis(0, "rift"), vis(1, "arcane_slash"), vis(2, "nova"), vis(2, "shadow"))',
     "greatsword nova vuln SHADOW",
     "奥義・冥王剣", "Secret Art: Sword of the Netherlord",
     "【必殺技】冥王の力を魔剣に宿し、巨大な闇の刃で前方を一直線に斬り裂いてから、周りすべてを薙ぎ払う。敵は守りも力も奪われる。",
     "[Ultimate] Fill the demon blade with the netherlord's might: a vast blade of darkness cleaves the line ahead, "
     "then sweeps everything around you. The foes lose both guard and strength."),
    ("p", "vessel_of_the_netherlord", 1, 1, 5, '.req("cursed_armor", 1).bonus(stats(mods().magic(0.03).healthPercent(0.02)))',
     "crown halo up SHADOW",
     "冥王の器", "Vessel of the Netherlord", "魔法攻撃力と最大HPが上がる。", "Raises magic power and maximum health."),
    ("a", "midnight_flash", 1, 2, 5,
     '.req("soul_devour", 1).cd(30).theme(SHADOW).act(24, 20, 26, hit(10, circle(6.0), 3.4, 1.0))'
     '.fx(afflict(0, BLIND, 80, 0), afflict(0, SLOW, 80, 2), vis(START, "shadow"), vis(0, "nova"), vis(0, "spin"))',
     "moon nova slow SHADOW",
     "闇夜の一閃", "Midnight Flash",
     "辺りを一瞬闇夜に変え、その闇の中で周りの敵をまとめて斬り払う。斬られた敵は目がくらみ、足が鈍る。",
     "Turn the world around you to midnight for an instant and cut down every foe in the dark; they are blinded "
     "and slowed."),
    ("p", "endless_curse", 1, 3, 5, '.req("demon_pact", 3).bonus(proc(POISON, 0.03, 100, 0.3, false))',
     "snake glow poison VENOM",
     "終わらぬ呪い", "Endless Curse", "攻撃が一定確率で敵を毒に冒す。", "Your attacks may poison the foe."),
]
