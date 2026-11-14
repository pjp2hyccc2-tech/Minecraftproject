"""The trees of the twelve second classes (二次職, owner 2026-11-14: 「職業をレベル40でニ次職に進化できるようにして
2つ枝分かれをつくって…新しいスキルを高クオリティで40個ずつ増やして」).

One spec per class in tools/jobs/specs/<class>.py holds everything about its 40 nodes in one place: where the node sits
(branch, column, tier), what it does (the Java skill DSL of com.eternalrealm.job: cooldown, theme, action frames,
effects, prerequisites, passive bonuses), its icon recipe (tools/art/skill_tree.py) and its name and description in
Japanese and English. This script writes from them

* src/main/java/com/eternalrealm/job/<Class>Skills.java   (generated: edit the spec, then run this script)
* docs/i18n/class_skills.json                             (merge with scripts/add_lang.py)
* tools/jobs/class_icons.json                             (read by tools/art/class_skill_icons.py)
* docs/modules/classes.md                                 (the trees as tables)

and checks the shape: 40 nodes filling two branches of four columns and five tiers, unique names, an ultimate per
branch, prerequisites on earlier tiers of the same class.

    python3 tools/jobs/class_trees.py
"""
from __future__ import annotations

import importlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

# class id -> (Java class name, enum constant, base job)
CLASSES = {
    "guardian": ("GuardianSkills", "GUARDIAN", "knight"),
    "paladin": ("PaladinSkills", "PALADIN", "knight"),
    "shinobi": ("ShinobiSkills", "SHINOBI", "assassin"),
    "reaper": ("ReaperSkills", "REAPER", "assassin"),
    "sage": ("SageSkills", "SAGE", "mage"),
    "necromancer": ("NecromancerSkills", "NECROMANCER", "mage"),
    "sniper": ("SniperSkills", "SNIPER", "ranger"),
    "spirit_archer": ("SpiritArcherSkills", "SPIRIT_ARCHER", "ranger"),
    "bishop": ("BishopSkills", "BISHOP", "priest"),
    "inquisitor": ("InquisitorSkills", "INQUISITOR", "priest"),
    "sword_saint": ("SwordSaintSkills", "SWORD_SAINT", "spellblade"),
    "hexblade": ("HexbladeSkills", "HEXBLADE", "spellblade"),
}
KINDS = {"a": "active", "p": "passive", "u": "ultimate"}
WEAPONS = ["ONE_HANDED_SWORD", "TWO_HANDED_SWORD", "SPEAR", "DUAL_BLADES", "BOW", "STAFF"]


def load(cls: str):
    return importlib.import_module(f"specs.{cls}")


def check(cls: str, spec) -> list[str]:
    bad = []
    nodes = spec.NODES
    if len(nodes) != 40:
        bad.append(f"{cls}: {len(nodes)} nodes")
    names = set()
    cells = set()
    tiers = {}
    for n in nodes:
        kind, name, branch, col, tier = n[:5]
        if kind not in KINDS:
            bad.append(f"{cls}_{name}: kind {kind}")
        if name in names:
            bad.append(f"{cls}_{name}: twice")
        names.add(name)
        if not (0 <= branch <= 1 and 0 <= col <= 3 and 1 <= tier <= 5):
            bad.append(f"{cls}_{name}: cell {branch} {col} {tier}")
        if (branch, col, tier) in cells:
            bad.append(f"{cls}_{name}: cell taken")
        cells.add((branch, col, tier))
        tiers[name] = tier
        if not n[6].split()[0] in ("-",) and len(n[6].split()) < 4:
            bad.append(f"{cls}_{name}: icon recipe '{n[6]}'")
        for text in n[7:11]:
            if not text or text != text.strip():
                bad.append(f"{cls}_{name}: empty or padded text")
    for b in (0, 1):
        if not any(n[0] == "u" and n[2] == b for n in nodes):
            bad.append(f"{cls}: branch {b} has no ultimate")
    for n in nodes:
        java = n[5]
        i = 0
        while True:
            i = java.find('.req("', i)
            if i < 0:
                break
            j = java.index('"', i + 6)
            parent = java[i + 6:j]
            if parent not in tiers:
                bad.append(f"{cls}_{n[1]}: unknown prerequisite {parent}")
            elif tiers[parent] >= n[4]:
                bad.append(f"{cls}_{n[1]}: prerequisite {parent} not on an earlier tier")
            i = j
        if n[4] > 1 and '.req("' not in java:
            bad.append(f"{cls}_{n[1]}: tier {n[4]} without a prerequisite")
        if n[0] == "p" and ".bonus(" not in java:
            bad.append(f"{cls}_{n[1]}: a passive without bonuses")
        if n[0] != "p" and (".act(" not in java or ".fx(" not in java or ".cd(" not in java):
            bad.append(f"{cls}_{n[1]}: an active needs .cd, .act and .fx")
    if len(spec.BRANCHES) != 2:
        bad.append(f"{cls}: {len(spec.BRANCHES)} branch names")
    return bad


def java(cls: str, spec) -> str:
    name, constant, base = CLASSES[cls]
    used = [w for w in WEAPONS if any(f"weapons({w}" in n[5] or f", {w}" in n[5] and "weapons(" in n[5]
                                       for n in spec.NODES)]
    weapon_imports = "".join(f"import static com.eternalrealm.combat.WeaponType.{w};\n" for w in used)
    lines = [
        "package com.eternalrealm.job;",
        "",
        "import com.eternalrealm.combat.DamageType;",
        "",
        "import java.util.List;",
        "",
        weapon_imports.rstrip("\n") if weapon_imports else None,
        "import static com.eternalrealm.job.Dsl.*;",
        "import static com.eternalrealm.job.SkillEffect.AfflictionKind.*;",
        "import static com.eternalrealm.job.SkillEffect.BuffKind.*;",
        "import static com.eternalrealm.job.SkillEffect.Placement.*;",
        "import static com.eternalrealm.job.SkillTheme.*;",
        "",
        "/**",
        f" * {spec.TITLE_JA} ({spec.TITLE_EN}), the second class of the {base} at level 40: {spec.BRANCHES[0][0]} "
        f"({spec.BRANCHES[0][1]}) /",
        f" * {spec.BRANCHES[1][0]} ({spec.BRANCHES[1][1]}). {spec.SUMMARY}",
        " *",
        " * <p>Generated by tools/jobs/class_trees.py from tools/jobs/specs/" + cls + ".py (names, descriptions and",
        " * icons live there too): edit the spec and run the script, not this file.</p>",
        " */",
        f"final class {name} {{",
        f"    private {name}() {{",
        "    }",
        "",
        "    static List<SkillDefinition> tree() {",
        f"        Tree t = new Tree(JobClass.{constant}, DamageType.{spec.DAMAGE});",
    ]
    lines = [line for line in lines if line is not None]
    branch = -1
    for n in sorted(spec.NODES, key=lambda n: (n[2], n[4], n[3])):
        kind, sname, b, col, tier, chain = n[:6]
        if b != branch:
            branch = b
            lines.append("")
            lines.append(f"        // ---------------------------------------------------------------- {b}: "
                         f"{spec.BRANCHES[b][0]}")
        method = KINDS[kind]
        head = f'        t.{method}("{sname}", {b}, {col}, {tier})'
        chain = " ".join(chain.split())
        lines.extend(wrap(head + chain + ";"))
    lines += ["", "        return t.build();", "    }", "}", ""]
    return "\n".join(lines)


def split_top(text: str, sep: str) -> list[str]:
    """Splits at {@code sep} characters outside brackets and strings (the separator stays with the next part)."""
    parts = []
    depth = 0
    quoted = False
    begin = 0
    for i, ch in enumerate(text):
        if ch == '"':
            quoted = not quoted
        elif not quoted and ch in "([":
            depth += 1
        elif not quoted and ch in ")]":
            depth -= 1
        elif not quoted and depth == 0 and ch == sep and i > begin:
            parts.append(text[begin:i])
            begin = i
    parts.append(text[begin:])
    return parts


def split_args(segment: str) -> list[str]:
    """Splits a call segment ({@code .fx(a, b(c, d), e)}) after the commas between its own arguments."""
    pieces = []
    depth = 0
    quoted = False
    begin = 0
    for i, ch in enumerate(segment):
        if ch == '"':
            quoted = not quoted
        elif not quoted and ch in "([":
            depth += 1
        elif not quoted and ch in ")]":
            depth -= 1
        elif not quoted and ch == "," and depth == 1:
            pieces.append(segment[begin:i + 1])
            begin = i + 1
    pieces.append(segment[begin:])
    return pieces


def wrap(text: str, width: int = 120, indent: str = "                ") -> list[str]:
    """Lays a DSL chain out within the width: method calls on new lines, long argument lists broken between
    arguments (never inside one)."""
    head_end = text.index(")") + 1
    head, rest = text[:head_end], text[head_end:]
    segments = split_top(rest, ".")
    lines = []
    line = head
    for seg in segments:
        if len(line) + len(seg) <= width:
            line += seg
            continue
        if line.strip():
            lines.append(line)
        line = indent
        for piece in split_args(seg):
            if len(line) + len(piece) > width and line.strip():
                lines.append(line.rstrip())
                line = indent + "        " + piece.lstrip()
            else:
                line += piece
    lines.append(line)
    return lines


def texts(cls: str, spec) -> dict:
    out = {}
    for n in spec.NODES:
        key = f"skill.eternalrealm.{cls}_{n[1]}"
        out[key] = {"ja": n[7], "en": n[8]}
        out[key + ".desc"] = {"ja": n[9], "en": n[10]}
    for b, (ja, en) in enumerate(spec.BRANCHES):
        out[f"skill_branch.eternalrealm.{cls}.{b}"] = {"ja": ja, "en": en}
    return out


BASE_JA = {"knight": "騎士", "assassin": "暗殺者", "mage": "魔導士", "ranger": "狩人", "priest": "神官",
           "spellblade": "魔剣士"}


def points(n) -> int:
    """Points to max a node: actives have five levels, a class's passives and ultimates three."""
    return 5 if n[0] == "a" else 3


def doc(specs: dict) -> str:
    """docs/modules/classes.md: the twelve class trees as tables (generated with the Java, so they always agree)."""
    out = [
        "# 二次職のスキルツリー（生成）",
        "",
        "`python3 tools/jobs/class_trees.py` が `tools/jobs/specs/` から書き出す一覧（手で編集しない）。仕組みは",
        "[skills.md](skills.md) の「二次職」。〔P〕= パッシブ、〔必〕= 必殺技、← = 前提。列は系統ごとに左から。",
        "",
    ]
    with open(os.path.join(ROOT, "docs/i18n/second_classes.json"), encoding="utf-8") as f:
        classes = json.load(f)
    for cls, spec in specs.items():
        _, _, base = CLASSES[cls]
        names = {n[1]: n[7] for n in spec.NODES}
        kinds = {k: sum(1 for n in spec.NODES if n[0] == k) for k in "apu"}
        total = sum(points(n) for n in spec.NODES)
        out += [f"## {spec.TITLE_JA}（{cls}）— {BASE_JA[base]}の二次職", "",
                classes[f"job.eternalrealm.{cls}.desc"]["ja"], "",
                f"系統: {spec.BRANCHES[0][0]}（{spec.BRANCHES[0][1]}）／ {spec.BRANCHES[1][0]}（{spec.BRANCHES[1][1]}）"
                f" ／ アクティブ {kinds['a']}・パッシブ {kinds['p']}・必殺技 {kinds['u']} ／ 全習得 {total} pt", "",
                f"| Lv | {spec.BRANCHES[0][0]} | | | | {spec.BRANCHES[1][0]} | | | |",
                "|---|---|---|---|---|---|---|---|---|"]
        cells = {(n[2], n[3], n[4]): n for n in spec.NODES}
        for tier, level in enumerate((40, 44, 48, 54, 60), start=1):
            row = [str(level)]
            for b in (0, 1):
                for col in range(4):
                    n = cells[(b, col, tier)]
                    text = n[7] + ("〔P〕" if n[0] == "p" else "〔必〕" if n[0] == "u" else "")
                    chain = n[5]
                    i = 0
                    while True:
                        i = chain.find('.req("', i)
                        if i < 0:
                            break
                        j = chain.index('"', i + 6)
                        lv = chain[j + 2:chain.index(")", j)].strip()
                        text += f"<br>←{names[chain[i + 6:j]]} Lv{lv}"
                        i = j
                    row.append(text)
            out.append("| " + " | ".join(row) + " |")
        out.append("")
    return "\n".join(out)


def unique_names(all_texts: dict) -> list[str]:
    """Every skill name, Japanese and English, is used once: the new ones among themselves and against the six
    jobs' trees in the language files (a class skill called like a skill of another tree would read as the same)."""
    bad = []
    lang = os.path.join(ROOT, "src/main/resources/assets/eternalrealm/lang")
    for code, locale in (("ja", "ja_jp"), ("en", "en_us")):
        with open(os.path.join(lang, f"{locale}.json"), encoding="utf-8") as f:
            existing = json.load(f)
        seen = {}
        for key, value in existing.items():
            if not key.startswith("skill.eternalrealm.") or key.endswith(".desc"):
                continue
            sid = key[len("skill.eternalrealm."):]
            if any(sid.startswith(cls + "_") for cls in CLASSES):
                continue  # an earlier run of this script: compared below
            seen[value] = sid
        for key, value in all_texts.items():
            if not key.startswith("skill.eternalrealm.") or key.endswith(".desc"):
                continue
            sid = key[len("skill.eternalrealm."):]
            other = seen.get(value[code])
            if other is not None:
                bad.append(f"{sid}: the {code} name '{value[code]}' is also {other}")
            seen[value[code]] = sid
    return bad


def main() -> int:
    problems = []
    all_texts = {}
    icons = {}
    specs = {}
    for cls, (name, _, _) in CLASSES.items():
        try:
            spec = load(cls)
        except ModuleNotFoundError:
            problems.append(f"{cls}: no spec")
            continue
        specs[cls] = spec
        problems += check(cls, spec)
        path = os.path.join(ROOT, "src/main/java/com/eternalrealm/job", f"{name}.java")
        with open(path, "w", encoding="utf-8") as f:
            f.write(java(cls, spec))
        all_texts.update(texts(cls, spec))
        icons[cls] = [[f"{cls}_{n[1]}", n[0]] + n[6].split() for n in sorted(spec.NODES, key=lambda n: (n[2], n[4],
                                                                                                         n[3]))]
    problems += unique_names(all_texts)
    for p in problems:
        print("CHECK FAILED:", p)
    with open(os.path.join(ROOT, "docs/i18n/class_skills.json"), "w", encoding="utf-8") as f:
        json.dump(all_texts, f, ensure_ascii=False, indent=1)
        f.write("\n")
    with open(os.path.join(ROOT, "docs/modules/classes.md"), "w", encoding="utf-8") as f:
        f.write(doc(specs))
    with open(os.path.join(HERE, "class_icons.json"), "w", encoding="utf-8") as f:
        json.dump(icons, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"class trees: {sum(len(v) for v in icons.values())} nodes of {len(icons)} classes, "
          f"{len(all_texts)} texts")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
