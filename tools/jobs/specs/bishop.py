"""司教 (Bishop): the priest who shepherds the whole flock. 救済 is salvation — great heals, litanies, wells of
light and the raising of many at once; 結界 is the ward — barriers, seals, sanctums and the banishing of evil."""

TITLE_JA = "司教"
TITLE_EN = "Bishop"
SUMMARY = "Great heals and mass revival, barriers and sanctums that hold the line."
DAMAGE = "HOLY"
BRANCHES = [("救済", "Salvation"), ("結界", "Wards")]

NODES = [
    # ------------------------------------------------------------------ 0: 救済
    ("a", "greater_heal", 0, 0, 1,
     '.cd(16).theme(HOLY).act(18, 14, 18, pause(6)).fx(heal(0, 12, 0.22, 4), vis(START, "cast"), vis(0, "heal"))',
     "cross halo heal LIGHT",
     "大いなる癒し", "Grand Heal", "自分と広い範囲の仲間のHPを大きく回復する。",
     "Greatly heal yourself and the allies in a wide circle around you."),
    ("a", "prayer_of_life", 0, 1, 1,
     '.cd(24).theme(LIFE).act(14, 10, 14, pause(4))'
     '.fx(allies(0, REGENERATION, 1, 300, 12), self(0, REGENERATION, 1, 300), vis(0, "blessing"), vis(0, "aura"))',
     "flower ring heal LIFE",
     "生命の祈り", "Prayer of Life", "自分と周囲の仲間のHPが長い間、回復し続ける。",
     "You and the allies around you keep regenerating health for a long while."),
    ("p", "bishops_mercy", 0, 2, 1, '.bonus(stats(mods().healing(0.06)))',
     "heart - heal LIGHT",
     "司教の慈悲", "Bishop's Mercy", "回復量が上がる。", "Raises healing."),
    ("a", "holy_word", 0, 3, 1,
     '.cd(10).theme(HOLY).act(16, 12, 16, hit(7, circle(4.0), 1.8, 0.6))'
     '.fx(heal(0, 8, 0.06, 2), afflict(0, BLIND, 40, 0), vis(0, "nova"), vis(0, "heal"))',
     "tome nova blind LIGHT",
     "聖言", "Holy Word",
     "聖なる言葉を唱える。言葉の光が周りの敵を打って目をくらませ、仲間を癒す。",
     "Speak a holy word: its light strikes and dazzles the foes around you and heals your allies."),

    ("a", "litany_of_mending", 0, 0, 2,
     '.req("greater_heal", 3).cd(20).theme(HOLY).act(18, 14, 20, pause(5))'
     '.fx(heal(0, 12, 0.08, 2), healZone(0, CASTER, 0, 6.0, 8, 10, 0.02, 0), vis(0, "heal"), vis(0, "sigil"))',
     "tome waves heal LIGHT",
     "癒しの連祷", "Litany of Mending",
     "癒しの祈りを連ねて唱える。仲間をすぐに回復し、足元の祈りの陣がしばらく癒し続ける。",
     "Chant a litany of healing: allies mend at once, and the circle of prayer at your feet keeps healing them."),
    ("p", "devout", 0, 1, 2, '.req("bishops_mercy", 1).bonus(cooldown(0.02))',
     "cross glow clock HOLY",
     "篤信", "Devout", "スキルの再使用時間が短くなる。", "Skills recover faster."),
    ("a", "lightwell", 0, 2, 2,
     '.req("prayer_of_life", 1).cd(30).theme(HOLY).act(16, 12, 18, pause(5))'
     '.fx(healZone(0, LOOK, 8.0, 4.0, 12, 15, 0.04, 0), vis(START, "cast"), vis(0, "pillar"), vis(0, "heal"))',
     "drop pillar heal LIGHT",
     "光の泉", "Lightwell",
     "狙った場所に光の泉を湧かせる。長い間、泉に入った仲間は癒され続ける。",
     "Raise a well of light where you look; for a long while every ally in it keeps healing."),
    ("p", "serenity", 0, 3, 2, '.req("holy_word", 1).bonus(stats(mods().healthPercent(0.02).stamina(4)))',
     "flower - stamina HOLY",
     "平穏", "Tranquility", "最大HPと最大スタミナが上がる。", "Raises maximum health and maximum stamina."),

    ("a", "mass_resurrection", 0, 0, 3,
     '.req("litany_of_mending", 1).cd(120).theme(HOLY).act(32, 28, 32, pause(14))'
     '.fx(revive(0, 14, 0.35), vis(START, "cast"), vis(0, "pillar"), vis(0, "heal"))',
     "feather pillar heart LIGHT",
     "一斉蘇生", "Mass Resurrection",
     "長い祈りを捧げ、広い範囲で倒れているすべての仲間をその場で蘇らせる。",
     "Offer a long prayer and raise every fallen ally in a wide circle where they lie."),
    ("a", "rite_of_purification", 0, 1, 3,
     '.req("devout", 1).cd(24).theme(HOLY).act(16, 12, 18, pause(5))'
     '.fx(cleanse(0, 14), heal(0, 14, 0.06, 2), allies(0, SPEED, 0, 120, 14), self(0, SPEED, 0, 120),'
     ' vis(0, "sanctuary"), vis(0, "heal"))',
     "drop halo heal HOLY",
     "祓いの儀", "Rite of Purification",
     "祓いの儀を行う。広い範囲の仲間の状態異常を払い、傷を癒し、足取りを軽くする。",
     "Perform the rite: allies in a wide circle are cleansed of ailments, mended and lightened on their feet."),
    ("p", "saintly_halo", 0, 2, 3, '.req("serenity", 1).bonus(stats(mods().healing(0.04)), healOnKill(0.01))',
     "sun halo heal HOLY",
     "聖者の後光", "Saintly Halo", "回復量が上がり、敵を倒すとHPが回復する。", "Raises healing; defeating a foe heals you."),
    ("a", "sacred_hymn", 0, 3, 3,
     '.req("lightwell", 1).cd(30).theme(HOLY).act(18, 14, 20, pause(6))'
     '.fx(heal(0, 14, 0.15, 3), allies(0, DEFENSE_UP, 0.15, 200, 14), self(0, DEFENSE_UP, 0.15, 200),'
     ' vis(0, "blessing"), vis(0, "aura"))',
     "note ring heal HOLY",
     "神聖讃歌", "Sacred Hymn",
     "讃歌を歌い上げる。広い範囲の仲間を癒し、しばらく受けるダメージを減らす。",
     "Raise a hymn: allies in a wide circle are healed and take less damage for a while."),

    ("a", "hands_of_grace", 0, 0, 4,
     '.req("mass_resurrection", 1).cd(40).theme(LIFE).act(16, 12, 20, pause(5))'
     '.fx(heal(0, 4, 0.5, 6), cleanse(0, 4), vis(0, "heal"), vis(0, "pillar"))',
     "hand pillar heal LIFE",
     "神癒の手", "Hands of Grace",
     "神の癒しを手に宿し、すぐそばの仲間と自分のHPを一気に大きく回復する。",
     "Lay on hands full of divine healing: you and the allies right beside you are restored in one great surge."),
    ("p", "faithful_heart", 0, 1, 4, '.req("saintly_halo", 1).bonus(power(0.02), stats(mods().healing(0.03)))',
     "heart halo star HOLY",
     "信仰篤き心", "Faithful Heart", "スキルの威力と回復量が上がる。", "Raises skill power and healing."),
    ("a", "gentle_rain", 0, 2, 4,
     '.req("rite_of_purification", 1).cd(36).theme(HOLY).act(18, 14, 22, pause(6))'
     '.fx(healZone(0, CASTER, 0, 8.0, 10, 10, 0.03, 0.5), vis(0, "rain"), vis(0, "heal"))',
     "drop rain heal LIGHT",
     "光の慈雨", "Gentle Rain of Light",
     "光の雨を降らせる。しばらくの間、広い範囲の仲間は癒され、敵は光に焼かれる。",
     "Let a rain of light fall: for a while allies in a wide circle heal and foes are seared."),
    ("p", "patron_saint", 0, 3, 4, '.req("sacred_hymn", 1).bonus(lastStand(0.0, 0.04))',
     "wings glow shield HOLY",
     "守護聖人", "Patron Saint", "HPが減るほど受けるダメージが減る。", "The lower your health, the less damage you take."),

    ("u", "grand_benediction", 0, 0, 5,
     '.req("hands_of_grace", 3).cd(150).theme(HOLY).act(40, 36, 40, pause(10), hit(18, circle(8.0), 2.0, 1.0))'
     '.fx(revive(0, 16, 0.5), heal(0, 16, 0.4, 8), cleanse(0, 16), allies(0, ATTACK_UP, 0.2, 240, 16),'
     ' self(0, ATTACK_UP, 0.2, 240), shieldAllies(0, 0.3, 240, 16), afflict(1, BLIND, 60, 0), vis(START, "cast"),'
     ' vis(0, "sigil"), vis(0, "heal"), vis(1, "pillar"), vis(1, "nova"))',
     "sun pillar heal LIGHT",
     "奥義・大祝福", "Secret Art: Grand Benediction",
     "【必殺技】天に大いなる祝福を願う。広い範囲で倒れた仲間を蘇らせ、全員を大きく癒して状態異常を払い、シールドと力の加護で包む。降り注ぐ光が周りの敵の目をくらませる。",
     "[Ultimate] Ask heaven for the grand benediction: the fallen in a wide circle rise, everyone is greatly healed, "
     "cleansed and wrapped in shields and strength, and the falling light dazzles the foes around you."),
    ("p", "cardinal", 0, 1, 5, '.req("faithful_heart", 1).bonus(stats(mods().healing(0.08).magic(0.02)))',
     "crown halo heal LIGHT",
     "枢機卿", "Cardinal", "回復量と魔法攻撃力が大きく上がる。", "Greatly raises healing and magic power."),
    ("a", "light_of_salvation", 0, 2, 5,
     '.req("gentle_rain", 1).cd(45).theme(HOLY).act(20, 16, 24, pause(6))'
     '.fx(heal(0, 14, 0.25, 4), shieldAllies(0, 0.2, 160, 14), vis(0, "heal"), vis(0, "shield"))',
     "cross nova shield LIGHT",
     "救済の光", "Light of Salvation",
     "救いの光で広い範囲の仲間を包み、大きく回復してシールドを張る。",
     "Wrap the allies in a wide circle in the light of salvation: they are greatly healed and shielded."),
    ("p", "unceasing_prayer", 0, 3, 5, '.req("patron_saint", 3).bonus(saver(0.02), cooldown(0.01))',
     "tome glow stamina HOLY",
     "絶えぬ祈り", "Unceasing Prayer", "スキルのスタミナ消費が減り、再使用時間が短くなる。",
     "Skills cost less stamina and recover faster."),

    # ------------------------------------------------------------------ 1: 結界
    ("a", "barrier", 1, 0, 1,
     '.cd(18).theme(HOLY).act(14, 10, 14, pause(4)).fx(shieldAllies(0, 0.25, 200, 8), vis(0, "shield"), vis(0, "sigil"))',
     "shield_cross halo shield HOLY",
     "結界", "Barrier", "自分と周囲の仲間に光の結界を張り、シールドを与える。",
     "Raise a barrier of light that shields you and the allies around you."),
    ("a", "exorcism_ward", 1, 1, 1,
     '.cd(20).theme(HOLY).act(14, 10, 16, pause(4))'
     '.fx(zone(0, CASTER, 0, 5.0, 0, 8, 12, 0.6, BLIND, 30, 0), vis(0, "sigil"), vis(0, "pillar"))',
     "rune zone blind HOLY",
     "破魔の結界", "Exorcism Ward",
     "足元に破魔の結界を広げる。しばらくの間、結界の中の敵は光に焼かれて目がくらむ。",
     "Spread a ward against evil at your feet; for a while foes inside it burn in the light and are dazzled."),
    ("p", "sacred_vestments", 1, 2, 1, '.bonus(stats(mods().defense(4).healthPercent(0.01)))',
     "chest - shield HOLY",
     "聖衣", "Sacred Vestments", "防御力と最大HPが上がる。", "Raises defense and maximum health."),
    ("a", "holy_seal", 1, 3, 1,
     '.cd(16).theme(HOLY).act(16, 12, 14, hit(7, ahead(2.5, 4.0), 1.4, 0.0))'
     '.fx(afflict(0, STUN, 60, 0), afflict(0, WEAKEN, 100, 0.15), vis(START, "cast"), vis(0, "chains"), vis(0, "sigil"))',
     "chains halo stun HOLY",
     "封印", "Holy Seal",
     "前方の敵を聖なる封印で縛り、しばらく動きを止めて力を奪う。",
     "Bind the foes ahead with a holy seal: they cannot move for a while and their strength fades."),

    ("a", "dome_of_protection", 1, 0, 2,
     '.req("barrier", 3).cd(30).theme(HOLY).act(16, 12, 18, pause(5))'
     '.fx(shieldAllies(0, 0.35, 160, 8), allies(0, DEFENSE_UP, 0.15, 160, 8), self(0, DEFENSE_UP, 0.15, 160),'
     ' vis(0, "sanctuary"), vis(0, "shield"))',
     "tower halo shield HOLY",
     "守護の円蓋", "Dome of Protection",
     "光の円蓋で仲間を覆う。厚いシールドを与え、しばらく受けるダメージを減らす。",
     "Cover your allies with a dome of light: thick shields, and less damage taken for a while."),
    ("p", "warding_lore", 1, 1, 2, '.req("sacred_vestments", 1).bonus(stats(mods().guard(0.02).defense(3)))',
     "rune - shield HOLY",
     "結界術", "Warding Lore", "ガード性能と防御力が上がる。", "Raises guard and defense."),
    ("a", "repel_evil", 1, 2, 2,
     '.req("exorcism_ward", 1).cd(18).theme(HOLY).act(16, 12, 16, hit(6, circle(5.0), 1.6, 2.6))'
     '.fx(afflict(0, WEAKEN, 100, 0.15), vis(0, "nova"), vis(0, "roar"))',
     "sun nova weaken HOLY",
     "退魔の波動", "Wave of Banishment",
     "退魔の波動を放ち、周りの敵を大きく吹き飛ばして弱らせる。",
     "Release a wave that banishes evil: the foes around you are thrown far back and weakened."),
    ("p", "holy_thorns", 1, 3, 2, '.req("holy_seal", 1).bonus(thorns(0.04))',
     "shield_spikes halo thorns HOLY",
     "聖なる棘", "Holy Thorns", "受けたダメージの一部を相手に跳ね返す。", "Part of the damage you take is returned."),

    ("a", "cathedral_ward", 1, 0, 3,
     '.req("dome_of_protection", 1).cd(40).theme(HOLY).act(18, 14, 20, pause(6))'
     '.fx(healZone(0, CASTER, 0, 6.0, 10, 15, 0.03, 0), allies(0, DEFENSE_UP, 0.25, 200, 8),'
     ' self(0, DEFENSE_UP, 0.25, 200), vis(0, "sanctuary"), vis(0, "pillar"))',
     "tower pillar heal LIGHT",
     "聖堂結界", "Cathedral Ward",
     "大聖堂のような結界を築く。仲間の守りを大きく固め、結界の中にいる間は癒し続ける。",
     "Raise a ward like a cathedral: your allies are greatly hardened and keep healing while they stay inside it."),
    ("a", "binding_circle", 1, 1, 3,
     '.req("warding_lore", 1).cd(26).theme(HOLY).act(16, 12, 18, pause(5))'
     '.fx(zone(0, LOOK, 10.0, 4.5, 0, 6, 10, 0.4, SLOW, 40, 4), vis(START, "cast"), vis(0, "sigil"), vis(0, "chains"))',
     "rune ring slow HOLY",
     "縛魔陣", "Circle of Binding",
     "狙った場所に魔を縛る陣を描く。しばらくの間、陣の中の敵はほとんど動けなくなる。",
     "Draw a circle that binds evil where you look; for a while foes inside it can barely move."),
    ("p", "iron_piety", 1, 2, 3, '.req("holy_thorns", 1).bonus(stats(mods().defense(5)))',
     "shield_small glow - STEEL",
     "鉄の信心", "Iron Piety", "防御力が上がる。", "Raises defense."),
    ("a", "prison_of_light", 1, 3, 3,
     '.req("repel_evil", 1).cd(22).theme(HOLY).act(20, 16, 18, hit(9, ahead(3.0, 5.0), 2.0, 0.0))'
     '.fx(afflict(0, STUN, 60, 0), vis(START, "cast"), vis(0, "pillar"), vis(0, "chains"))',
     "tower beamd stun LIGHT",
     "光の牢獄", "Prison of Light",
     "前方に光の牢獄を落とし、中の敵を打って閉じ込める。",
     "Drop a prison of light on the ground ahead, striking the foes inside and holding them fast."),

    ("a", "divine_mirror", 1, 0, 4,
     '.req("cathedral_ward", 1).cd(40).theme(HOLY).act(16, 12, 20, pause(5))'
     '.fx(allies(0, THORNS, 0.3, 200, 10), self(0, THORNS, 0.3, 200), shieldAllies(0, 0.2, 200, 10),'
     ' vis(0, "shield"), vis(0, "blessing"))',
     "gem halo thorns LIGHT",
     "神鏡", "Divine Mirror",
     "神の鏡で仲間を映し、シールドを与えて受けた攻撃の多くを跳ね返させる。",
     "Reflect your allies in the divine mirror: they gain shields and send much of every blow back."),
    ("p", "ward_master", 1, 1, 4, '.req("iron_piety", 1).bonus(cooldown(0.02), stats(mods().guard(0.01)))',
     "rune halo clock HOLY",
     "結界師", "Ward Master", "スキルの再使用時間が短くなり、ガード性能が上がる。", "Skills recover faster; raises guard."),
    ("a", "rain_of_holy_lances", 1, 2, 4,
     '.req("binding_circle", 1).cd(24).theme(HOLY)'
     '.act(26, 22, 24, shots(6, 3, 5, orb("holy_lance", 1.8, 40).withExplosion(1.6).fallingFromAbove(), 1.2, 0.3))'
     '.fx(vis(START, "cast"), vis(0, "rain"), vis(4, "burst"))',
     "lance rain x5 LIGHT",
     "聖槍の雨", "Rain of Holy Lances",
     "天から聖なる槍を呼び、狙った場所へ五本降らせる。",
     "Call holy lances down from the sky: five fall where you aim."),
    ("p", "bastion_of_faith", 1, 3, 4, '.req("iron_piety", 1).bonus(stats(mods().healthPercent(0.03)))',
     "heart halo shield LIGHT",
     "信仰の砦", "Bastion of Faith", "最大HPが上がる。", "Raises maximum health."),

    ("u", "heavenly_ward", 1, 0, 5,
     '.req("divine_mirror", 3).cd(150).theme(HOLY).act(40, 36, 40, pause(8), hit(16, circle(8.0), 2.4, 2.0))'
     '.fx(shieldAllies(0, 0.5, 300, 14), allies(0, DEFENSE_UP, 0.35, 300, 14), self(0, DEFENSE_UP, 0.35, 300),'
     ' healZone(0, CASTER, 0, 9.0, 14, 20, 0.03, 1.0), afflict(1, WEAKEN, 120, 0.2), vis(START, "cast"),'
     ' vis(0, "sigil"), vis(0, "sanctuary"), vis(1, "pillar"), vis(1, "nova"))',
     "shield_cross pillar shield LIGHT",
     "奥義・天上結界", "Secret Art: Heavenly Ward",
     "【必殺技】天上の結界を地上に降ろす。仲間を厚いシールドで包んで守りを大きく固め、敵を押しのけて弱らせる。結界は長く仲間を癒し、敵を焼き続ける。",
     "[Ultimate] Bring heaven's ward down to earth: allies are wrapped in thick shields and greatly hardened, foes "
     "are driven back and weakened, and the ward keeps healing friends and searing foes for a long while."),
    ("p", "grand_warden", 1, 1, 5, '.req("ward_master", 1).bonus(stats(mods().guard(0.02).defense(5)))',
     "crown halo shield HOLY",
     "大結界師", "Grand Warden", "ガード性能と防御力が大きく上がる。", "Greatly raises guard and defense."),
    ("a", "great_purification", 1, 2, 5,
     '.req("rain_of_holy_lances", 3).cd(36).theme(HOLY).act(24, 20, 26, hit(10, circle(7.0), 3.4, 1.2))'
     '.fx(afflict(0, BLIND, 80, 0), cleanse(0, 10), vis(START, "cast"), vis(0, "nova"), vis(0, "judgment"))',
     "sun burst blind LIGHT",
     "大祓", "Great Purification",
     "大いなる祓いを行う。広い範囲の敵を光で打って目をくらませ、近くの仲間の状態異常を払う。",
     "Perform the great purification: the light strikes and dazzles foes in a wide circle and cleanses the "
     "allies near you."),
    ("p", "holy_fortitude", 1, 3, 5, '.req("bastion_of_faith", 3).bonus(lastStand(0.03, 0.03))',
     "cross nova shield HOLY",
     "聖なる不屈", "Holy Fortitude", "HPが減るほど与えるダメージが増え、受けるダメージが減る。",
     "The lower your health, the harder you strike and the less damage you take."),
]
