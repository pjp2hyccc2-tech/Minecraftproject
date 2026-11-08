"""Every sound of ETERNAL REALM: ids, files, subtitles and the metadata docs/AUDIO.md is written from.

The ids must match com.eternalrealm.sound.ERSounds (checked by generate_all.py and SoundAssetsTest).
File names are relative to assets/eternalrealm/sounds/ without the .ogg extension.
"""
from dataclasses import dataclass, field

NAMESPACE = "eternalrealm"


@dataclass(frozen=True)
class Music:
    id: str
    file: str
    title_ja: str
    title_en: str
    loop: bool = True


@dataclass(frozen=True)
class Jingle:
    id: str
    file: str
    ja: str
    en: str


@dataclass(frozen=True)
class Sfx:
    id: str
    files: tuple
    ja: str
    en: str
    source: str
    attenuation: int = 0          # 0 = vanilla default (16 blocks)
    subtitle: bool = True
    description: str = ""


MUSIC = (
    Music("music.title", "music/title", "アルセリア王都（タイトル・拠点）", "Royal Capital of Alseria (title, hub)"),
    Music("music.area_1", "music/area_1", "アルセリア王国", "Kingdom of Alseria"),
    Music("music.area_2", "music/area_2", "エルダーフォレスト", "Elder Forest"),
    Music("music.area_3", "music/area_3", "サンドリア砂漠", "Sandria Desert"),
    Music("music.area_4", "music/area_4", "ノルディア雪原", "Nordia Snowfield"),
    Music("music.area_5", "music/area_5", "ドラゴニア火山", "Dragonia Volcano"),
    Music("music.area_6", "music/area_6", "セレスティア天空大陸", "Celestia, the Sky Continent"),
    Music("music.area_7", "music/area_7", "終焉の塔", "Tower of the End"),
    Music("music.dungeon", "music/dungeon", "ダンジョン", "Dungeon"),
    Music("music.boss", "music/boss", "ボスバトル", "Boss Battle"),
    Music("music.final_boss", "music/final_boss", "終焉の王", "King of the End"),
    Music("music.ending", "music/ending", "エンディング（スタッフロール）", "Ending (staff roll)", loop=False),
)

JINGLES = (
    Jingle("jingle.victory", "jingle/victory", "勝利のファンファーレ", "Victory fanfare"),
    Jingle("jingle.level_up", "jingle/level_up", "レベルアップ", "Level up"),
    Jingle("jingle.quest_complete", "jingle/quest_complete", "クエスト達成", "Quest complete"),
    Jingle("jingle.discovery", "jingle/discovery", "新たな地を発見", "New land discovered"),
)


def _v(base, count):
    return tuple(f"{base}_{i}" for i in range(1, count + 1)) if count > 1 else (base,)


SFX = (
    # Skills: casts and impacts per element
    Sfx("skill.slash", _v("sfx/skill/slash", 2), "斬撃", "Slash", "PLAYERS",
        description="鋭い風切り＋刃の響き"),
    Sfx("skill.heavy_slash", _v("sfx/skill/heavy_slash", 1), "重い斬撃", "Heavy slash", "PLAYERS",
        description="低い風切り＋重い衝撃"),
    Sfx("skill.fire_cast", _v("sfx/skill/fire_cast", 1), "炎の詠唱", "Fire spell cast", "PLAYERS",
        description="炎が巻き上がる音とはぜる火の粉"),
    Sfx("skill.fire_explosion", _v("sfx/skill/fire_explosion", 1), "爆発", "Fiery explosion", "PLAYERS", 32,
        description="低い爆発音＋炎の余韻"),
    Sfx("skill.ice_cast", _v("sfx/skill/ice_cast", 1), "氷の詠唱", "Ice spell cast", "PLAYERS",
        description="結晶のきらめきと凍りつく空気"),
    Sfx("skill.ice_shatter", _v("sfx/skill/ice_shatter", 1), "氷が砕ける", "Ice shatters", "PLAYERS",
        description="ガラス質の破砕音"),
    Sfx("skill.thunder", _v("sfx/skill/thunder", 1), "雷鳴", "Thunder strikes", "PLAYERS", 48,
        description="鋭い雷撃音と遠雷のうなり"),
    Sfx("skill.holy_chime", _v("sfx/skill/holy_chime", 1), "聖なる鐘の音", "Holy chime", "PLAYERS",
        description="明るい鐘の和音"),
    Sfx("skill.heal", _v("sfx/skill/heal", 1), "回復", "Healing", "PLAYERS",
        description="上昇するハープとやわらかな和音"),
    Sfx("skill.buff", _v("sfx/skill/buff", 1), "強化", "Buff", "PLAYERS",
        description="上昇スイープと和音"),
    Sfx("skill.shadow_whoosh", _v("sfx/skill/shadow_whoosh", 1), "闇の揺らめき", "Shadow whoosh", "PLAYERS",
        description="低く暗い風切りとうめくような響き"),
    Sfx("skill.arrow_release", _v("sfx/skill/arrow_release", 1), "矢を放つ", "Arrow released", "PLAYERS",
        description="強い弦音と矢の飛翔"),
    Sfx("skill.arrow_volley", _v("sfx/skill/arrow_volley", 1), "矢の雨", "Arrow volley", "PLAYERS",
        description="複数の弦音と矢の群れ"),
    Sfx("skill.magic_bolt", _v("sfx/skill/magic_bolt", 1), "魔法弾", "Magic bolt", "PLAYERS",
        description="下降するFMのザップと光の粒"),
    # Basic attack per weapon family
    Sfx("weapon.swing.one_handed_sword", _v("sfx/weapon/one_handed_sword", 2), "剣を振る", "Sword swings", "PLAYERS",
        description="軽く速い風切り"),
    Sfx("weapon.swing.two_handed_sword", _v("sfx/weapon/two_handed_sword", 2), "大剣を振る", "Greatsword swings",
        "PLAYERS", description="遅く重い風切り"),
    Sfx("weapon.swing.dual_blades", _v("sfx/weapon/dual_blades", 2), "双剣を振る", "Dual blades swing", "PLAYERS",
        description="二連の短い風切り"),
    Sfx("weapon.swing.spear", _v("sfx/weapon/spear", 2), "槍で突く", "Spear thrusts", "PLAYERS",
        description="鋭い突きと柄の鳴り"),
    Sfx("weapon.swing.staff", _v("sfx/weapon/staff", 2), "杖を振る", "Staff swings", "PLAYERS",
        description="木の風切り＋魔力のうなり"),
    Sfx("weapon.swing.bow", _v("sfx/weapon/bow", 2), "弓を射る", "Bow fires", "PLAYERS",
        description="軽い弦音と矢の風切り"),
    # Defense
    Sfx("combat.guard", _v("sfx/combat/guard", 1), "防御", "Attack blocked", "PLAYERS",
        description="鈍い金属と木の衝撃"),
    Sfx("combat.parry", _v("sfx/combat/parry", 1), "パリィ", "Parry", "PLAYERS",
        description="高く響く金属音と火花"),
    Sfx("combat.dodge", _v("sfx/combat/dodge", 1), "回避", "Dodge", "PLAYERS",
        description="素早い布と空気の音"),
    # Bosses
    Sfx("boss.telegraph", _v("sfx/boss/telegraph", 1), "危険な予兆", "Danger warning", "HOSTILE", 48,
        description="逆再生のうねり＋不協和の警告音"),
    Sfx("boss.roar", _v("sfx/boss/roar", 1), "ボスの咆哮", "Boss roars", "HOSTILE", 64,
        description="フォルマント合成の咆哮"),
    Sfx("boss.phase_change", _v("sfx/boss/phase_change", 1), "ボスの形態変化", "Boss changes phase", "HOSTILE", 64,
        description="重い衝撃＋不協和の鐘と合唱のうねり"),
    Sfx("boss.form_sting", _v("sfx/boss/form_sting", 1), "ボスが姿を変える", "Boss transforms", "HOSTILE", 64,
        description="逆再生のうねり→低音金管のスタブ（ニ短調）＋ティンパニとクラッシュ"),
    Sfx("boss.collapse", _v("sfx/boss/collapse", 1), "ボスが崩れ落ちる", "Boss collapses", "HOSTILE", 64,
        description="落ちていく地鳴りと弔鐘、暗い合唱が長調に解決し光のきらめきで終わる"),
    # Dungeons
    Sfx("dungeon.door_open", _v("sfx/dungeon/door_open", 1), "扉が開く", "Dungeon door opens", "BLOCKS", 32,
        description="石扉がこすれる音と着地音"),
    Sfx("dungeon.wave_start", _v("sfx/dungeon/wave_start", 1), "角笛", "War horn sounds", "HOSTILE", 64,
        description="低い角笛（5度上行）"),
    # Loot and economy
    Sfx("loot.rare_drop", _v("sfx/loot/rare_drop", 1), "レアドロップ", "Rare item dropped", "PLAYERS",
        description="きらめく鐘のアルペジオ"),
    Sfx("loot.legendary_drop", _v("sfx/loot/legendary_drop", 1), "レジェンダリードロップ", "Legendary item dropped",
        "PLAYERS", 32, description="金管と鐘と合唱の小さなファンファーレ"),
    Sfx("loot.gold_pickup", _v("sfx/loot/gold_pickup", 3), "ゴールド獲得", "Gold picked up", "PLAYERS",
        description="硬貨の触れ合う音"),
    Sfx("enhance.success", _v("sfx/enhance/success", 1), "強化成功", "Enhancement succeeded", "PLAYERS",
        description="金床の一撃＋上昇するきらめき"),
    Sfx("enhance.fail", _v("sfx/enhance/fail", 1), "強化失敗", "Enhancement failed", "PLAYERS",
        description="金床の一撃＋ひび割れと下降音"),
    # UI
    Sfx("ui.click", _v("sfx/ui/click", 1), "クリック", "Click", "MASTER", subtitle=False,
        description="短く柔らかいクリック"),
    Sfx("ui.journal_open", _v("sfx/ui/journal_open", 1), "ページをめくる", "Page turns", "MASTER",
        description="紙のこすれと本の音"),
    Sfx("ui.npc_talk", _v("sfx/ui/npc_talk", 3), "話し声", "Someone talks", "MASTER",
        description="短い会話音（3種の高さ）"),
)


def subtitle_key(sound_id):
    return f"subtitles.{NAMESPACE}.{sound_id}"


def all_ids():
    return [m.id for m in MUSIC] + [j.id for j in JINGLES] + [s.id for s in SFX]


def sounds_json():
    """The content of assets/eternalrealm/sounds.json (insertion ordered)."""
    out = {}
    for m in MUSIC:
        out[m.id] = {"sounds": [{"name": f"{NAMESPACE}:{m.file}", "stream": True}]}
    for j in JINGLES:
        out[j.id] = {"subtitle": subtitle_key(j.id),
                     "sounds": [{"name": f"{NAMESPACE}:{j.file}", "stream": True}]}
    for s in SFX:
        entry = {}
        if s.subtitle:
            entry["subtitle"] = subtitle_key(s.id)
        sounds = []
        for f in s.files:
            if s.attenuation:
                sounds.append({"name": f"{NAMESPACE}:{f}", "attenuation_distance": s.attenuation})
            else:
                sounds.append(f"{NAMESPACE}:{f}")
        entry["sounds"] = sounds
        out[s.id] = entry
    return out


def i18n():
    """docs/i18n/audio.json: subtitle texts (music has no subtitles, like vanilla music)."""
    out = {}
    for j in JINGLES:
        out[subtitle_key(j.id)] = {"ja": j.ja, "en": j.en}
    for s in SFX:
        if s.subtitle:
            out[subtitle_key(s.id)] = {"ja": s.ja, "en": s.en}
    return dict(sorted(out.items()))


def music_files():
    return [m.file for m in MUSIC] + [j.file for j in JINGLES]


def sfx_files():
    return [f for s in SFX for f in s.files]
