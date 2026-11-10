#!/usr/bin/env python3
"""Writes the game data of the bestiary (tools/art_entities/bestiary.py) into the mod.

  src/main/java/com/eternalrealm/monster/Monsters.java        templates and table entries   (generated block)
  src/main/java/com/eternalrealm/monster/DropTables.java      one drop table per template   (generated block)
  src/main/java/com/eternalrealm/monster/forge/MonsterBodies.java   template -> look       (generated block)
  src/main/java/com/eternalrealm/entity/EnemyVariant.java     behaviour of every look       (generated blocks)
  src/main/java/com/eternalrealm/world/field/FieldThreats.java    the 30 field lords        (generated block)
  docs/i18n/world.json, entity.json, field_threats.json       names (Japanese and English)
  docs/modules/bestiary.md                                    the list for people

Run from anywhere after editing bestiary.py:  python3 tools/art_entities/bestiary_gen.py
(then python3 tools/art_entities/generate.py for the textures and models, and merge the names into the lang files).
"""
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))

import bestiary  # noqa: E402

JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'eternalrealm')
BEGIN = '// GENERATED BESTIARY %s BEGIN (tools/art_entities/bestiary_gen.py)'
END = '// GENERATED BESTIARY %s END'

BODY = {'humanoid': 'HUSK', 'beast': 'SPIDER', 'golem': 'RAVAGER', 'flyer': 'BLAZE'}
RIG = {'humanoid': 'HUMANOID', 'beast': 'BEAST', 'golem': 'GOLEM', 'flyer': 'FLYER'}
SOUND = {'bandit': 'BANDIT', 'ghoul': 'GHOUL', 'mummy': 'MUMMY', 'cultist': 'CULTIST', 'prisoner': 'PRISONER',
         'jailer': 'JAILER', 'ice_knight': 'ICE_KNIGHT', 'frost_mage': 'FROST_MAGE', 'fire_imp': 'FIRE_IMP',
         'dark_knight': 'DARK_KNIGHT', 'void_mage': 'VOID_MAGE', 'shade': 'SHADE', 'dryad': 'DRYAD', 'wolf': 'WOLF',
         'cave_rat': 'CAVE_RAT', 'salamander': 'SALAMANDER', 'scorpion': 'SCORPION', 'treant': 'TREANT',
         'sand_golem': 'SAND_GOLEM', 'ice_golem': 'ICE_GOLEM', 'lava_golem': 'LAVA_GOLEM',
         'ruin_construct': 'RUIN_CONSTRUCT', 'harpy': 'HARPY', 'ice_wraith': 'ICE_WRAITH',
         'storm_spirit': 'STORM_SPIRIT', 'sky_sentinel': 'SKY_SENTINEL', 'fire_spirit': 'FIRE_SPIRIT'}


def jd(x):
    s = repr(float(x))
    return s


def jf(x):
    return repr(float(x)) + 'F'


def replace_block(path, tag, body, indent):
    """Replaces the lines between the BEGIN/END markers of {@code tag} (markers kept) with {@code body}."""
    with open(path, encoding='utf-8') as f:
        src = f.read()
    begin = BEGIN % tag
    end = END % tag
    i = src.find(begin)
    j = src.find(end)
    if i < 0 or j < 0:
        raise SystemExit('%s: markers %s missing' % (path, tag))
    line_end = src.index('\n', i) + 1
    line_start = src.rindex('\n', 0, j) + 1
    text = ''.join(indent + line + '\n' if line else '\n' for line in body)
    src = src[:line_end] + text + src[line_start:]
    with open(path, 'w', encoding='utf-8') as f:
        f.write(src)


def translucent(m):
    a = m['art']
    return bool(a.get('translucent')) or (m['rig'] == 'flyer' and a.get('kind') == 'wraith')


# ------------------------------------------------------------------------------------------------ the Java side

def monsters_block():
    lines = []
    for field in range(1, 11):
        slug = bestiary.FIELDS[field][0]
        lines.append('// ---- field %d (%s)' % (field, slug))
        for m in [x for x in bestiary.SPEC if x['field'] == field]:
            h, d, ar, sp = m['tune']
            kind = 'elite' if m['lord'] else 'normal'
            lines.append('%s("%s", %s, %d, %d, %d, t(%s, %s, %s, %s), NONE),' % (
                kind, m['id'], BODY[m['rig']], m['lv'][0], m['lv'][1], field, h, d, ar, sp))
    lines[-1] = lines[-1].rstrip(',')
    out = ['private static List<MonsterTemplate> bestiary() {', '    return List.of(']
    out += ['            ' + line for line in lines]
    out[-1] += ');'
    out.append('}')
    out.append('')
    out.append('/** The bestiary monsters of field {@code areaId} that the field spawner picks (not the lords). */')
    out.append('private static List<MonsterTable.Entry> bestiaryEntries(int areaId) {')
    out.append('    return switch (areaId) {')
    for field in range(1, 11):
        entries = []
        for m in [x for x in bestiary.normals() if x['field'] == field]:
            offset = 1 if m['tune'][0] >= 1.3 else 0
            entries.append('entry("%s", %d, %d)' % (m['id'], m['weight'], offset))
        out.append('        case %d -> List.of(%s);' % (field, (',\n                ').join(entries)))
    out.append('        default -> List.of();')
    out.append('    };')
    out.append('}')
    return out


def drops_block():
    lines = []
    for field in range(1, 11):
        slug, mats, tier, _ = bestiary.FIELDS[field]
        lines.append('// field %d (%s), tier %d' % (field, slug, tier))
        for i, m in enumerate([x for x in bestiary.SPEC if x['field'] == field]):
            sig, other = (mats[0], mats[1]) if i % 2 == 0 else (mats[1], mats[0])
            kind = 'fieldElite' if m['lord'] else 'field'
            lines.append('%s("%s", %d, %d, %s, %s),' % (kind, m['id'], tier, field, sig, other))
    lines[-1] = lines[-1].rstrip(',')
    out = ['private static final List<DropTable> BESTIARY = List.of(']
    out += ['        ' + line for line in lines]
    out[-1] += ');'
    return out


def bodies_block():
    out = []
    for m in bestiary.SPEC:
        out.append('Map.entry("%s", "%s"),' % (m['id'], m['id']))
    out[-1] = out[-1].rstrip(',')
    return out


def variant_block():
    out = []
    for rig in ('humanoid', 'beast', 'golem', 'flyer'):
        out.append('// %s (bestiary)' % rig)
        for m in bestiary.by_rig(rig):
            bolt = 'BoltKind.' + m['bolt'] if m['bolt'] else 'null'
            particle = 'ParticleTypes.' + m['particle'] if m['particle'] else 'null'
            out.append('%s(EnemyRig.%s, %s, %s, %s, %s, %s, %s),' % (
                m['id'].upper(), RIG[rig], bolt, particle, jf(m['chance']), SOUND[m['sound']],
                'true' if m['fire'] else 'false', 'true' if translucent(m) else 'false'))
    out[-1] = out[-1][:-1] + ';'
    return out


def variant_cases():
    ids = [m['id'].upper() for m in bestiary.SPEC]
    lines = []
    row = []
    for i in ids:
        row.append(i)
        if len(', '.join(row)) > 76:
            lines.append(', '.join(row) + ',')
            row = []
    if row:
        lines.append(', '.join(row))
    out = ['case ' + lines[0]] + ['        ' + line for line in lines[1:]]
    out[-1] += ' -> this.soundsLike.sounds();'
    return out


def lords_block():
    out = ['private static final List<Lord> LORDS = List.of(']
    for m in bestiary.lords():
        lid, x, z, territory = m['lord']
        out.append('        lord("%s", %d, "%s", %d, %d, %d, 6.0, %d, %d),' % (
            lid, m['field'], m['id'], x, z, territory, m['respawn'][0], m['respawn'][1]))
    out[-1] = out[-1].rstrip(',') + ');'
    return out


# --------------------------------------------------------------------------------------- the special moves

BOLT_STYLE = {'FIRE': 'FIRE', 'FROST': 'FROST', 'VOID': 'DARK', 'CURSE': 'DARK', 'STORM': 'MAGIC', 'NATURE': 'MAGIC'}


def element_of(m):
    """Colour and after-effect of a monster's move markers."""
    if m['element']:
        return m['element']
    for element, ids in bestiary.ELEMENTS.items():
        if m['id'] in ids:
            return element
    if m['bolt'] in BOLT_STYLE:
        return BOLT_STYLE[m['bolt']]
    if m['fire']:
        return 'FIRE'
    return 'DANGER'


def moves_of(m):
    """The three special moves of a body, signature first: veterans use the first, elites two, lords all three."""
    if m['moves']:
        return list(m['moves'])
    a = m['art']
    rig = m['rig']
    bolt = m['bolt']
    elemental = element_of(m) in ('FIRE', 'FROST', 'DARK', 'MAGIC')
    if rig == 'humanoid':
        show = set(a.get('show', ()))
        if bolt == 'ARROW':
            return ['VOLLEY', 'BEAM', 'CLEAVE']
        if bolt == 'STORM':
            return ['STORM_CROSS', 'CIRCLE', 'NOVA']
        if bolt in ('VOID', 'CURSE'):
            return ['CIRCLE', 'BEAM', 'NOVA']
        if bolt:
            return ['CIRCLE', 'NOVA', 'BEAM']
        if 'greatblade' in show:
            return ['CLEAVE', 'WAVE', 'SLAM']
        if 'blade' in show:
            return ['WAVE', 'CLEAVE', 'SWEEP'] if 'shield_l' in show else ['CLEAVE', 'SWEEP', 'POUNCE']
        if 'club' in show:
            return ['SLAM', 'SWEEP', 'STOMP']
        if 'dagger' in show:
            return ['POUNCE', 'CLEAVE', 'SWEEP']
        if 'claws_r' in show or 'claws_l' in show:
            return ['SWEEP', 'POUNCE', 'CLEAVE']
        return ['CLEAVE', 'SLAM', 'SWEEP']
    if rig == 'beast':
        kind = a['kind']
        if kind in ('wolf', 'fox', 'hound'):
            return ['BREATH', 'POUNCE', 'CHARGE'] if elemental and kind == 'hound' else \
                ['POUNCE', 'CHARGE', 'BREATH' if elemental else 'SWEEP']
        if kind in ('boar', 'bull', 'ram', 'beetle'):
            return ['CHARGE', 'STOMP', 'SWEEP']
        if kind in ('rat', 'hare', 'mole'):
            return ['POUNCE', 'SWEEP', 'CHARGE']
        if kind == 'lizard':
            return ['BREATH', 'SWEEP', 'POUNCE'] if elemental else ['POUNCE', 'SWEEP', 'CHARGE']
        if kind == 'serpent':
            return ['SWEEP', 'POUNCE', 'BREATH' if elemental else 'CHARGE']
        if kind == 'toad':
            return ['POUNCE', 'SWEEP', 'STOMP']
        if kind == 'tortoise':
            return ['STOMP', 'SLAM', 'BREATH' if elemental else 'SWEEP']
        if kind in ('crab', 'crayfish'):
            return ['SWEEP', 'SLAM', 'CHARGE']
        if kind in ('scorpion', 'centipede'):
            return ['POUNCE', 'SWEEP', 'CHARGE']
        if kind == 'spider':
            return ['POUNCE', 'CIRCLE', 'SWEEP']
        return ['CHARGE', 'POUNCE', 'SWEEP']
    if rig == 'golem':
        if a.get('ape'):
            return ['POUNCE', 'SLAM', 'STOMP']
        if a.get('runes') or a.get('core') or 'crystal_r' in a.get('show', ()) or 'ice_crown' in a.get('show', ()):
            return ['SLAM', 'STOMP', 'NOVA']
        if 'canopy' in a.get('show', ()):
            return ['SLAM', 'SWEEP', 'STOMP']
        return ['SLAM', 'STOMP', 'CHARGE']
    kind = a['kind']
    if kind in ('bird', 'harpy', 'bat'):
        return ['POUNCE', 'SWEEP', 'BEAM' if bolt == 'STORM' else 'CHARGE']
    if kind == 'bug':
        return ['POUNCE', 'CHARGE', 'SWEEP']
    if kind == 'moth':
        return ['CIRCLE', 'NOVA', 'POUNCE']
    if kind in ('spirit', 'wisp'):
        return ['STORM_CROSS', 'CIRCLE', 'NOVA'] if bolt == 'STORM' else ['CIRCLE', 'NOVA', 'BEAM']
    if kind == 'wraith':
        return ['NOVA', 'CIRCLE', 'BEAM']
    if kind == 'eye':
        return ['BEAM', 'CIRCLE', 'NOVA']
    if kind == 'sentinel':
        return ['BEAM', 'CIRCLE', 'STORM_CROSS']
    if kind == 'flame':
        return ['NOVA', 'CIRCLE', 'BREATH']
    return ['CIRCLE', 'NOVA', 'POUNCE']


def write_moves():
    L = []
    w = L.append
    w('// GENERATED by tools/art_entities/bestiary_gen.py from tools/art_entities/bestiary.py. Do not edit.')
    w('package com.eternalrealm.monster;')
    w('')
    w('import com.eternalrealm.combat.Telegraph;')
    w('')
    w('import java.util.List;')
    w('')
    w('import static com.eternalrealm.monster.SpecialMove.*;')
    w('')
    w('/**')
    w(' * The special moves of the bestiary bodies (three each, signature first: veterans use the first, elites two,')
    w(' * the ヌシ all three) and the element of their markers. {@link SpecialMoves} asks here first.')
    w(' */')
    w('final class BestiaryMoves {')
    w('    private BestiaryMoves() {')
    w('    }')
    w('')
    w('    /** The moves of a bestiary body, or {@code null} for other bodies. */')
    w('    static List<SpecialMove> movesOf(String variant) {')
    w('        return switch (variant) {')
    groups = collections.OrderedDict()
    for m in bestiary.SPEC:
        groups.setdefault(tuple(moves_of(m)), []).append(m['id'])
    for moves, ids in groups.items():
        assert len(moves) == 3 and len(set(moves)) == 3, (ids, moves)
        w('            case %s ->' % ', '.join('"%s"' % i for i in ids))
        w('                    List.of(%s);' % ', '.join(moves))
    w('            default -> null;')
    w('        };')
    w('    }')
    w('')
    w('    /** The element of a bestiary body\'s markers, or {@code null} for other bodies. */')
    w('    static Telegraph.Style styleOf(String variant) {')
    w('        return switch (variant) {')
    styles = collections.OrderedDict()
    for m in bestiary.SPEC:
        styles.setdefault(element_of(m), []).append(m['id'])
    for style, ids in styles.items():
        w('            case %s ->' % ', '.join('"%s"' % i for i in ids))
        w('                    Telegraph.Style.%s;' % style)
    w('            default -> null;')
    w('        };')
    w('    }')
    w('}')
    path = os.path.join(JAVA, 'monster', 'BestiaryMoves.java')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(wrap_java(L)) + '\n')


def wrap_java(lines, width=120):
    """Breaks the long case lists of the generated switches at commas."""
    out = []
    for line in lines:
        if len(line) <= width or not line.lstrip().startswith('case '):
            out.append(line)
            continue
        indent = ' ' * (len(line) - len(line.lstrip()))
        rest = line.strip()
        first = True
        while len(rest) + len(indent) + (0 if first else 8) > width:
            cut = rest.rfind(', ', 0, width - len(indent) - (0 if first else 8))
            out.append(indent + ('' if first else '        ') + rest[:cut + 1])
            rest = rest[cut + 2:]
            first = False
        out.append(indent + ('' if first else '        ') + rest)
    return out


# ------------------------------------------------------------------------------------------------- the names

def load(path):
    with open(path, encoding='utf-8') as f:
        return json.load(f, object_pairs_hook=collections.OrderedDict)


def dump_inline(path, data):
    lines = ['{']
    items = list(data.items())
    for i, (k, v) in enumerate(items):
        lines.append('  %s: {"ja": %s, "en": %s}%s' % (json.dumps(k, ensure_ascii=False),
                                                      json.dumps(v['ja'], ensure_ascii=False),
                                                      json.dumps(v['en'], ensure_ascii=False),
                                                      ',' if i < len(items) - 1 else ''))
    lines.append('}')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def dump_pretty(path, data):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def replace_section(data, belongs, entries, after_prefix):
    """A copy of {@code data} where the keys {@code belongs(key)} are replaced by {@code entries}, placed where the
    first of them was, or (none yet) after the last key starting with {@code after_prefix}, or at the end."""
    keys = list(data)
    at = next((i for i, k in enumerate(keys) if belongs(k)), None)
    kept = [k for k in keys if not belongs(k)]
    if at is None:
        last = max((i for i, k in enumerate(kept) if k.startswith(after_prefix)), default=len(kept) - 1)
        at = last + 1
    else:
        at = len([k for k in keys[:at] if not belongs(k)])
    out = collections.OrderedDict()
    for k in kept[:at]:
        out[k] = data[k]
    for k, v in entries.items():
        out[k] = v
    for k in kept[at:]:
        out[k] = data[k]
    return out


def names():
    i18n = os.path.join(ROOT, 'docs', 'i18n')
    ours = re.compile(r'(monster|entity\.eternalrealm\.enemy)\.?(eternalrealm\.)?(f\d+_|lord_)')

    world = load(os.path.join(i18n, 'world.json'))
    entries = collections.OrderedDict(('monster.eternalrealm.' + m['id'], {'ja': m['ja'], 'en': m['en']})
                                      for m in bestiary.SPEC)
    world = replace_section(world, lambda k: k.startswith('monster.eternalrealm.') and bool(
        re.match(r'monster\.eternalrealm\.(f\d+_|lord_)', k)), entries, 'monster.eternalrealm.')
    dump_inline(os.path.join(i18n, 'world.json'), world)

    entity = load(os.path.join(i18n, 'entity.json'))
    entries = collections.OrderedDict(('entity.eternalrealm.enemy.' + m['id'], {'ja': m['ja'], 'en': m['en']})
                                      for m in bestiary.SPEC)
    entity = replace_section(entity, lambda k: bool(re.match(r'entity\.eternalrealm\.enemy\.(f\d+_|lord_)', k)),
                             entries, 'entity.eternalrealm.enemy.')
    dump_pretty(os.path.join(i18n, 'entity.json'), entity)

    threats = load(os.path.join(i18n, 'field_threats.json'))
    lord_ids = {m['lord'][0] for m in bestiary.lords()}
    entries = collections.OrderedDict()
    for m in bestiary.lords():
        entries['threat.eternalrealm.lord.' + m['lord'][0]] = {'ja': m['ja'], 'en': m['en']}
    threats = replace_section(threats, lambda k: k.startswith('threat.eternalrealm.lord.') and k.split('.')[-1] in lord_ids,
                              entries, 'threat.eternalrealm.lord.')
    dump_inline(os.path.join(i18n, 'field_threats.json'), threats)


# --------------------------------------------------------------------------------------------------- the doc

KIND_JA = {'humanoid': '人型', 'beast': '獣', 'golem': '巨像', 'flyer': '飛行'}
BOLT_JA = {None: '近接', 'ARROW': '射撃', 'FIRE': '炎', 'FROST': '氷', 'VOID': '虚無', 'CURSE': '呪い',
           'STORM': '雷', 'NATURE': '自然'}


def doc():
    L = ['# 魔物図鑑（フィールドの魔物100種＋ヌシ30体）', '',
         '`tools/art_entities/bestiary.py` が唯一の定義元です。見た目（4種のリグのパーツ・配色・姿勢・模様）と',
         'ゲームデータ（テンプレート・出現表・ドロップ・行動・名前）を `bestiary_gen.py` / `generate.py` が生成します。',
         '', '- 通常の魔物: 各フィールドに10種。既存の3種と合わせてフィールドの出現表から湧く（重み・レベル補正つき）。',
         '- ヌシ: 各フィールドに3体（ELITE）。出現表からは湧かず、フィールドの脅威（`FieldThreatService`）が縄張りに出す。',
         '- 攻撃: 「近接」以外は距離を取って属性弾を撃つ（炎=燃焼、氷=鈍足、虚無=弱体、呪い=衰弱、自然=毒、雷/射撃=追加効果なし）。',
         '- 体力・攻撃・防御・速度は役割の標準値（NORMAL / ELITE）に倍率を掛けたもの。', '']
    for field in range(1, 11):
        slug, mats, tier, lv = bestiary.FIELDS[field]
        L.append('## フィールド %d（%s, Lv%d-%d, 装備ティア %d）' % (field, slug, lv[0], lv[1], tier))
        L.append('')
        L.append('| id | 名前 | 種別 | Lv | 攻撃 | 体力/攻撃/防御/速度 | 重み |')
        L.append('|---|---|---|---|---|---|---|')
        for m in [x for x in bestiary.SPEC if x['field'] == field]:
            name = m['ja'] + ('（ヌシ）' if m['lord'] else '')
            pos = ' @%d,%d' % (m['lord'][1], m['lord'][2]) if m['lord'] else ''
            L.append('| `%s` | %s | %s | %d-%d | %s | %s | %s |' % (
                m['id'], name, KIND_JA[m['rig']], m['lv'][0], m['lv'][1], BOLT_JA[m['bolt']],
                '/'.join('%g' % t for t in m['tune']), ('ヌシ' + pos) if m['lord'] else m['weight']))
        L.append('')
    with open(os.path.join(ROOT, 'docs', 'modules', 'bestiary.md'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(L))


def check():
    ids = [m['id'] for m in bestiary.SPEC]
    assert len(ids) == len(set(ids)), 'duplicate ids'
    assert len(bestiary.normals()) == 100, len(bestiary.normals())
    assert len(bestiary.lords()) == 30, len(bestiary.lords())
    for field in range(1, 11):
        assert len([m for m in bestiary.normals() if m['field'] == field]) == 10, field
        assert len([m for m in bestiary.lords() if m['field'] == field]) == 3, field
    for m in bestiary.SPEC:
        lo, hi = bestiary.FIELDS[m['field']][3]
        assert lo <= m['lv'][0] <= m['lv'][1] <= hi, (m['id'], m['lv'])
        assert m['sound'] in SOUND, m['id']
        assert 0.8 <= m['tune'][3] <= 1.25, (m['id'], 'speed')
        assert m['tune'][0] <= 1.45, (m['id'], 'a NORMAL monster takes 3-6 reference hits')
        assert re.fullmatch(r'[a-z0-9_]+', m['id']), m['id']


def main():
    check()
    replace_block(os.path.join(JAVA, 'monster', 'Monsters.java'), 'TEMPLATES', monsters_block(), '    ')
    replace_block(os.path.join(JAVA, 'monster', 'DropTables.java'), 'DROPS', drops_block(), '    ')
    replace_block(os.path.join(JAVA, 'monster', 'forge', 'MonsterBodies.java'), 'BODIES', bodies_block(),
                  '            ')
    replace_block(os.path.join(JAVA, 'entity', 'EnemyVariant.java'), 'VARIANTS', variant_block(), '    ')
    replace_block(os.path.join(JAVA, 'entity', 'EnemyVariant.java'), 'SOUNDS', variant_cases(), '            ')
    replace_block(os.path.join(JAVA, 'world', 'field', 'FieldThreats.java'), 'LORDS', lords_block(), '    ')
    write_moves()
    names()
    doc()
    print('bestiary: %d monsters (%d lords) written' % (len(bestiary.SPEC), len(bestiary.lords())))


if __name__ == '__main__':
    main()
