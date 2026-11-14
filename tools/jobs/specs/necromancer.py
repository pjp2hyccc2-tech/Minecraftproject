"""冥術士 (Necromancer): the mage who studied death. 呪詛 lays curses — frailty, plague, withering, ruin; 死霊
commands bone and the life of others — spears and prisons of bone, drained life, and the fallen raised again."""

TITLE_JA = "冥術士"
TITLE_EN = "Necromancer"
SUMMARY = "Curses that rot the foe, bone, drained life and the raising of the fallen."
DAMAGE = "MAGIC"
BRANCHES = [("呪詛", "Curses"), ("死霊", "Undeath")]

NODES = [
    # ------------------------------------------------------------------ 0: 呪詛
    ("a", "curse_of_frailty", 0, 0, 1,
     '.cd(10).theme(SHADOW).act(18, 14, 14, shot(7, orb("shadow_bolt", 2.2, 30), 1.4, 0.2))'
     '.fx(afflict(0, WEAKEN, 200, 0.2), afflict(0, VULNERABLE, 200, 0.15), vis(START, "cast"))',
     "skull mist weaken SHADOW",
     "衰弱の呪い", "Curse of Frailty",
     "呪いの弾を放つ。当たった敵は長い間、力が衰え、守りも脆くなる。",
     "Fire a bolt of curse: the foe it strikes is left weak and frail for a long while."),
    ("a", "rot", 0, 1, 1,
     '.cd(14).theme(VENOM).act(16, 12, 15, pause(4))'
     '.fx(zone(0, LOOK, 10.0, 3.5, 0, 6, 10, 0.5, POISON, 60, 0.3), vis(START, "cast"), vis(0, "mist"))',
     "flower cloud poison VENOM",
     "腐朽", "Rot",
     "狙った場所の大地を腐らせる。しばらくの間、そこにいる敵は毒に冒され続ける。",
     "Rot the ground where you look; for a while the foes on it are poisoned again and again."),
    ("p", "dark_learning", 0, 2, 1, '.bonus(stats(mods().magic(0.03)))',
     "tome - - SHADOW",
     "冥き学識", "Dark Learning", "魔法攻撃力が上がる。", "Raises magic power."),
    ("a", "hex_volley", 0, 3, 1,
     '.cd(8).theme(SHADOW).act(18, 14, 14, shots(3, 4, 3, orb("shadow_bolt", 2.8, 24), 0.9, 0.1))'
     '.fx(vis(START, "cast"), vis(2, "shadow"))',
     "orbs3 speed x3 SHADOW",
     "呪弾連射", "Hex Volley",
     "呪いの弾を三発続けて撃ち出す。",
     "Fire three bolts of curse in quick succession."),

    ("a", "agony", 0, 0, 2,
     '.req("curse_of_frailty", 3).cd(16).theme(BLOOD).act(18, 14, 18, hit(8, circle(4.5), 1.8, 0.2))'
     '.fx(afflict(0, BLEED, 100, 0.4), afflict(0, SLOW, 100, 2), vis(0, "chains"), vis(0, "shadow"))',
     "heart crack bleed BLOOD",
     "苦悶", "Agony",
     "周りの敵に耐えがたい苦しみを与える。敵は血を流し、のたうって動きが鈍る。",
     "Inflict unbearable agony on the foes around you: they bleed, writhe and slow."),
    ("p", "malice", 0, 1, 2, '.req("dark_learning", 1).bonus(proc(WEAKEN, 0.03, 80, 0.15, false))',
     "eye - weaken SHADOW",
     "悪意", "Malice", "攻撃が一定確率で敵を弱らせる。", "Your attacks may weaken the foe."),
    ("a", "plague_cloud", 0, 2, 2,
     '.req("rot", 1).cd(22).theme(VENOM).act(18, 14, 18, pause(5))'
     '.fx(zone(0, CASTER, 0, 6.0, 0, 10, 10, 0.4, POISON, 80, 0.4), vis(0, "mist"), vis(0, "storm"))',
     "skull cloud x3 VENOM",
     "疫病の雲", "Plague Cloud",
     "自分の周りに疫病の雲を呼ぶ。長い間、雲の中の敵は重い毒に冒され続ける。",
     "Call a cloud of plague around you; for a long while every foe inside it suffers a heavy poison."),
    ("p", "lingering_curse", 0, 3, 2, '.req("hex_volley", 1).bonus(power(0.02))',
     "hourglass mist star SHADOW",
     "長き呪い", "Lingering Curse", "スキルの威力が上がる。", "Raises the power of your skills."),

    ("a", "soul_rend", 0, 0, 3,
     '.req("agony", 1).cd(18).theme(SHADOW).act(22, 18, 20, hit(10, beam(10.0, 1.2), 2.6, 0.4))'
     '.fx(siphon(0, 0.2, 0), vis(START, "cast"), vis(0, "ray"), vis(0, "shadow"))',
     "skull beam heal SHADOW",
     "魂裂き", "Soul Rend",
     "魂を裂く暗い光線を放つ。一直線上の敵を傷つけ、奪った生命でHPを回復する。",
     "Fire a dark beam that tears at the soul; it wounds every foe in its line and the stolen life heals you."),
    ("a", "blinding_hex", 0, 1, 3,
     '.req("malice", 1).cd(20).theme(SHADOW).act(16, 12, 16, hit(7, circle(5.0), 1.2, 0.2))'
     '.fx(afflict(0, BLIND, 80, 0), afflict(0, WEAKEN, 100, 0.15), vis(0, "smoke"), vis(0, "nova"))',
     "eye cloud blind SHADOW",
     "盲目の呪い", "Hex of Blindness",
     "周りの敵の目に闇を流し込み、目をくらませて力を奪う。",
     "Pour darkness into the eyes of the foes around you, blinding them and sapping their strength."),
    ("p", "hexmaster", 0, 2, 3, '.req("lingering_curse", 1).bonus(stats(mods().crit(0.015).critDamage(0.04)))',
     "hand glow crit SHADOW",
     "呪術の達人", "Hexmaster", "会心率と会心ダメージが上がる。", "Raises critical chance and critical damage."),
    ("a", "wither", 0, 3, 3,
     '.req("plague_cloud", 1).cd(18).theme(VENOM).act(18, 14, 18, hit(8, ahead(2.5, 4.0), 1.4, 0.0))'
     '.fx(afflict(0, POISON, 120, 0.6), vis(START, "cast"), vis(0, "sigil"))',
     "flower crack poison VENOM",
     "枯死", "Wither",
     "前方の敵の生命を枯らす。重い毒が長く続く。",
     "Wither the life of the foes ahead; a heavy poison lingers in them for a long time."),

    ("a", "curse_storm", 0, 0, 4,
     '.req("soul_rend", 1).cd(26).theme(SHADOW).act(20, 16, 22, pause(5))'
     '.fx(zone(0, CASTER, 0, 6.0, 0, 10, 8, 0.6, WEAKEN, 60, 0.2), vis(0, "storm"), vis(0, "vortex"))',
     "skull spiral weaken SHADOW",
     "呪詛の嵐", "Storm of Curses",
     "呪いの嵐を身にまとう。しばらくの間、嵐に巻かれた敵は傷つき、力を奪われ続ける。",
     "Wrap yourself in a storm of curses; for a while foes caught in it keep taking harm and losing strength."),
    ("p", "black_grimoire", 0, 1, 4, '.req("hexmaster", 1).bonus(power(0.03))',
     "tome halo star SHADOW",
     "黒の魔導書", "Black Grimoire", "スキルの威力が上がる。", "Raises the power of your skills."),
    ("a", "death_coil", 0, 2, 4,
     '.req("blinding_hex", 1).cd(22).theme(SHADOW).act(22, 18, 22, shot(9, orb("shadow_bolt", 1.8, 40).withPierce(4), 3.2, 0.6))'
     '.fx(siphon(0, 0.3, 0), vis(START, "cast"), vis(0, "rift"))',
     "snake spiral heal SHADOW",
     "死の螺旋", "Death Coil",
     "とぐろを巻く死の気を放つ。貫いた敵から生命を奪い、HPを大きく回復する。",
     "Loose a coiling stream of death; it pierces the foes in its way and their stolen life heals you greatly."),
    ("p", "spite", 0, 3, 4, '.req("wither", 1).bonus(lastStand(0.06, 0.0))',
     "fangs mist - SHADOW",
     "怨念", "Spite", "HPが減るほど与えるダメージが増える。", "The lower your health, the harder you strike."),

    ("u", "curse_of_myriad_demons", 0, 0, 5,
     '.req("curse_storm", 3).cd(130).theme(SHADOW)'
     '.act(46, 42, 40, pause(8), hit(18, circle(8.0), 1.6, 0.0), hit(34, circle(8.0), 4.6, 1.2))'
     '.fx(afflict(1, WEAKEN, 160, 0.3), afflict(1, VULNERABLE, 160, 0.3), afflict(2, POISON, 120, 0.6),'
     ' afflict(2, SLOW, 100, 3), vis(START, "sigil"), vis(0, "vortex"), vis(1, "chains"), vis(2, "nova"),'
     ' vis(2, "shadow"))',
     "skull spiral skull SHADOW",
     "奥義・万魔の呪", "Secret Art: Curse of Myriad Demons",
     "【必殺技】万の魔の呪いを解き放つ。周りの敵の力と守りを奪い、続く大きな一撃で重い毒に冒して足を止める。",
     "[Ultimate] Unleash the curse of a myriad demons: the foes around lose strength and guard, then a great blow "
     "poisons them deeply and roots them in place."),
    ("p", "lich_mind", 0, 1, 5, '.req("black_grimoire", 1).bonus(stats(mods().magic(0.03)), cooldown(0.01))',
     "skull halo clock ARCANE",
     "不死者の叡智", "Mind of the Undying", "魔法攻撃力が上がり、スキルの再使用時間が短くなる。",
     "Raises magic power; skills recover faster."),
    ("a", "corpse_blast", 0, 2, 5,
     '.req("death_coil", 3).cd(30).theme(BLOOD).act(24, 20, 26, hit(10, circle(5.5), 3.6, 1.6))'
     '.fx(afflict(0, BLEED, 100, 0.4), vis(START, "cast"), vis(0, "burst"), vis(0, "nova"))',
     "bone burst bleed BLOOD",
     "屍爆", "Corpse Blast",
     "周りに満ちる死の気を一気に破裂させ、敵を吹き飛ばして深い傷を負わせる。",
     "Burst the death that fills the air around you all at once: foes are blown back and bleed deeply."),
    ("p", "eternal_curse", 0, 3, 5, '.req("spite", 3).bonus(proc(VULNERABLE, 0.02, 100, 0.15, false))',
     "chains glow vuln SHADOW",
     "永劫の呪縛", "Eternal Curse", "攻撃が一定確率で敵を脆弱にする。", "Your attacks may leave the foe vulnerable."),

    # ------------------------------------------------------------------ 1: 死霊
    ("a", "bone_spear", 1, 0, 1,
     '.cd(8).theme(SHADOW).act(18, 14, 14, shot(7, orb("bone", 3.0, 24).withPierce(4), 2.2, 0.6))'
     '.fx(vis(START, "cast"), vis(0, "thrust"))',
     "bone beamd - SHADOW",
     "骨槍", "Bone Spear",
     "鋭い骨の槍を撃ち出し、一直線上の敵を貫く。",
     "Shoot a sharp spear of bone that runs through the foes in its line."),
    ("a", "life_tap", 1, 1, 1,
     '.cd(12).theme(BLOOD).act(16, 12, 14, hit(7, ahead(2.5, 4.0), 1.6, 0.0))'
     '.fx(siphon(0, 0.4, 0), vis(START, "cast"), vis(0, "chains"))',
     "drop waves heal BLOOD",
     "生命吸収", "Life Tap",
     "前方の敵から生命を吸い上げる。与えたダメージの多くでHPを回復する。",
     "Draw the life out of the foes ahead; much of the damage dealt returns to you as health."),
    ("p", "grave_vitality", 1, 2, 1, '.bonus(stats(mods().healthPercent(0.03)))',
     "heart - heart SHADOW",
     "墓所の生命力", "Grave Vitality", "最大HPが上がる。", "Raises maximum health."),
    ("a", "bone_armor", 1, 3, 1,
     '.cd(26).theme(SHADOW).act(12, 8, 12, pause(3)).fx(shield(0, 0.3, 240), self(0, DEFENSE_UP, 0.2, 240),'
     ' vis(0, "shield"), vis(0, "iron_wall"))',
     "chest glow shield SHADOW",
     "骨の鎧", "Bone Armor", "骨の鎧をまとい、シールドを得て受けるダメージを減らす。",
     "Clad yourself in bone: you gain a shield and take less damage."),

    ("a", "bone_prison", 1, 0, 2,
     '.req("bone_spear", 3).cd(18).theme(EARTH).act(20, 16, 18, hit(9, ahead(3.0, 5.0), 1.4, 0.0))'
     '.fx(afflict(0, STUN, 50, 0), afflict(0, SLOW, 100, 2), vis(START, "cast"), vis(0, "pillar"), vis(0, "quake"))',
     "bone zone stun EARTH",
     "骨牢", "Bone Prison",
     "狙った先の地面から骨の柵を生やし、中の敵を閉じ込めて動きを止める。",
     "Raise a cage of bone from the ground ahead; the foes caught inside cannot move."),
    ("p", "blood_pact", 1, 1, 2, '.req("grave_vitality", 1).bonus(lifesteal(0.01))',
     "drop glow heal BLOOD",
     "血の盟約", "Blood Pact", "与えたダメージの一部でHPを回復する。", "Part of the damage you deal heals you."),
    ("a", "draining_circle", 1, 2, 2,
     '.req("life_tap", 1).cd(24).theme(BLOOD).act(16, 12, 18, pause(4))'
     '.fx(healZone(0, CASTER, 0, 5.0, 8, 10, 0.02, 0.5), vis(0, "sigil"), vis(0, "mist"))',
     "rune zone heal BLOOD",
     "吸魂陣", "Draining Circle",
     "足元に陣を描く。しばらくの間、陣の中の敵から生気を奪い、陣の中の仲間に分け与える。",
     "Draw a circle at your feet; for a while it drains the foes inside and gives their life to the allies inside."),
    ("p", "undying_will", 1, 3, 2, '.req("bone_armor", 1).bonus(lastStand(0.0, 0.05))',
     "skull halo shield SHADOW",
     "不死の意志", "Undying Will", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),

    ("a", "bone_storm", 1, 0, 3,
     '.req("bone_prison", 1).cd(20).theme(SHADOW).act(28, 24, 22, hits(4, 4, 5, circle(4.0), 0.8, 0.2))'
     '.fx(afflict(4, BLEED, 80, 0.3), vis(0, "storm"), vis(2, "spin"))',
     "bone spiral x5 SHADOW",
     "骨嵐", "Bone Storm",
     "砕けた骨の嵐を身にまとい、周りの敵を五度切り刻む。最後の一撃で出血させる。",
     "Whirl a storm of shattered bone around you, cutting the foes around five times; the last leaves them bleeding."),
    ("a", "soul_siphon", 1, 1, 3,
     '.req("blood_pact", 1).cd(22).theme(SHADOW).act(18, 14, 18, hit(8, circle(5.0), 1.8, 0.0))'
     '.fx(siphon(0, 0.3, 6.0), stamina(0, 20), vis(0, "vortex"), vis(0, "heal"))',
     "orb spiral heal SHADOW",
     "魂吸い", "Soul Siphon",
     "周りの敵から魂を吸い上げる。奪った生命は自分と近くの仲間のHPになり、スタミナも戻る。",
     "Pull the souls out of the foes around you: the stolen life heals you and nearby allies, and stamina returns."),
    ("p", "deathless", 1, 2, 3, '.req("undying_will", 1).bonus(healOnKill(0.02))',
     "skull glow heal LIFE",
     "死なずの体", "Deathless", "敵を倒すとHPが回復する。", "Defeating a foe restores your health."),
    ("a", "grave_guard", 1, 3, 3,
     '.req("undying_will", 1).cd(36).theme(SHADOW).act(14, 10, 16, pause(4))'
     '.fx(shieldAllies(0, 0.25, 200, 8), allies(0, DEFENSE_UP, 0.15, 200, 8), vis(0, "shield"), vis(0, "sanctuary"))',
     "tower halo shield SHADOW",
     "墓守の加護", "Gravekeeper's Ward",
     "墓守の霊を呼び、自分と周囲の仲間をシールドで包んで守りを固める。",
     "Call the spirits of the gravekeepers: you and the allies around you are shielded and hardened."),

    ("a", "bone_spikes", 1, 0, 4,
     '.req("bone_storm", 1).cd(24).theme(EARTH).act(24, 20, 24, hit(10, ahead(3.0, 4.0), 2.6, 0.4))'
     '.fx(zone(0, AHEAD, 4.0, 4.0, 6, 3, 8, 1.4, BLEED, 60, 0.4), afflict(0, LAUNCH, 1, 0.6), vis(0, "quake"),'
     ' vis(0, "pillar"))',
     "bone crack launch EARTH",
     "骨の剣山", "Field of Bone Spikes",
     "前方の地面から骨の刃を突き上げて敵を打ち上げる。刃はしばらく残り、近づく敵を切り裂く。",
     "Drive blades of bone up from the ground ahead, throwing foes into the air; the blades stay a while and cut "
     "whatever comes near."),
    ("p", "dark_renewal", 1, 1, 4, '.req("deathless", 1).bonus(stats(mods().healing(0.05).healthPercent(0.02)))',
     "flower halo heal SHADOW",
     "冥き再生", "Dark Renewal", "回復量と最大HPが上がる。", "Raises healing and maximum health."),
    ("a", "death_and_rebirth", 1, 2, 4,
     '.req("soul_siphon", 1).cd(100).theme(SHADOW).act(26, 22, 28, pause(10))'
     '.fx(revive(0, 10, 0.3), heal(0, 10, 0.1, 2), vis(START, "cast"), vis(0, "pillar"), vis(0, "shadow"))',
     "skull pillar heart SHADOW",
     "死と再生", "Death and Rebirth",
     "死の理をねじ曲げ、周りで倒れた仲間をその場で蘇らせる。生きている仲間も少し癒す。",
     "Bend the law of death: fallen allies around you rise where they lie, and the living are healed a little."),
    ("p", "bone_fortress", 1, 3, 4, '.req("grave_guard", 1).bonus(stats(mods().defense(6)))',
     "tower - shield EARTH",
     "骨の砦", "Bone Fortress", "防御力が上がる。", "Raises defense."),

    ("u", "march_of_the_dead", 1, 0, 5,
     '.req("bone_spikes", 3).cd(130).theme(SHADOW)'
     '.act(52, 48, 40, seq(pause(6), hits(12, 4, 6, circle(6.5), 1.0, 0.3), hit(40, circle(8.0), 4.0, 1.6)))'
     '.fx(siphon(7, 0.3, 8.0), afflict(3, BLEED, 100, 0.4), afflict(7, LAUNCH, 1, 0.7), vis(START, "sigil"),'
     ' vis(0, "shadow"), vis(1, "quake"), vis(4, "pillar"), vis(7, "nova"))',
     "skull crack x6 SHADOW",
     "奥義・死者の行進", "Secret Art: March of the Dead",
     "【必殺技】大地から死者の軍勢が這い出し、周りの敵へ六度襲いかかる。最後に骨の大波が敵を打ち上げ、奪った生命で自分と仲間を癒す。",
     "[Ultimate] The dead claw their way out of the ground and fall upon the foes around you six times; a final "
     "wave of bone hurls them up, and the stolen life heals you and your allies."),
    ("p", "vessel_of_undeath", 1, 1, 5, '.req("dark_renewal", 1).bonus(stats(mods().healthPercent(0.03).magic(0.02)))',
     "crown halo heart SHADOW",
     "不死王の器", "Vessel of Undeath", "最大HPと魔法攻撃力が上がる。", "Raises maximum health and magic power."),
    ("a", "soul_cage", 1, 2, 5,
     '.req("death_and_rebirth", 1).cd(32).theme(SHADOW).act(22, 18, 24, hit(10, circle(6.0), 2.4, 0.0))'
     '.fx(afflict(0, PULL, 1, 1.0), afflict(0, STUN, 40, 0), vis(0, "chains"), vis(0, "vortex"))',
     "chains spiral pull SHADOW",
     "魂の檻", "Soul Cage",
     "魂を捕らえる檻を広げ、周りの敵を自分のもとへ引き寄せて動きを止める。",
     "Spread a cage that catches souls: the foes around you are dragged to you and held fast."),
    ("p", "eternal_unlife", 1, 3, 5, '.req("bone_fortress", 1).bonus(lastStand(0.04, 0.03))',
     "skull nova shield SHADOW",
     "永遠なる死生", "Eternal Unlife", "HPが減るほど与えるダメージが増え、受けるダメージが減る。",
     "The lower your health, the harder you strike and the less damage you take."),
]
