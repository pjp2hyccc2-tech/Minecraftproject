"""Sets the damage of the second classes' attack skills in tools/jobs/specs/*.py (owner, 2026-11-15:
「二次職の攻撃スキルの＋のパーセンテージを最低でも450とかにして攻撃特化の職業なら最高で1600％くらいにして」).

The skill tree shows a skill's power at level 1 as 総威力 (its hits' multipliers × 100; a damaging zone shows its
pulses). Per class, the attack skills keep their order and are spread linearly over 450 % .. the class's top for
actives; ultimates whose damage is their point reach the class's highest (1600 % for the attack classes). Heal zones
keep their side damage. Nothing becomes weaker. Running it again on the result only moves the last digit
of a few ultimates (the per-hit numbers are rounded to two decimals).

    python3 tools/jobs/class_power.py .            # the table of old -> new totals
    python3 tools/jobs/class_power.py . --apply    # rewrites the specs; then run tools/jobs/class_trees.py
"""
import ast
import importlib.util
import io
import os
import re
import sys
import tokenize

ROOT = sys.argv[1]
APPLY = "--apply" in sys.argv
SPECS = os.path.join(ROOT, "tools", "jobs", "specs")

MIN = 4.5
# class -> (actives' top, ultimates' floor, ultimates' top): attack classes reach 1600 %
TIERS = {
    "attack": (11.0, 13.0, 16.0),
    "hybrid": (9.0, 10.0, 12.0),
    "support": (8.0, 9.0, 10.0),
}
# an ultimate whose damage is its point (the others heal or protect with a blow on the side)
OFFENSIVE_ULTIMATE = 4.0
CLASS_TIER = {
    "shinobi": "attack", "reaper": "attack", "sword_saint": "attack", "hexblade": "attack",
    "sage": "attack", "necromancer": "attack", "sniper": "attack", "spirit_archer": "attack",
    "paladin": "hybrid", "inquisitor": "hybrid",
    "guardian": "support", "bishop": "support",
}
ZONE_MAX = 10.0  # SkillEffect.Zone's limit per pulse


def split_args(text, open_at):
    """Arguments of the call whose '(' is at open_at: list of (start, end) spans, and the index of ')'."""
    depth = 0
    quoted = False
    spans = []
    begin = open_at + 1
    for i in range(open_at, len(text)):
        ch = text[i]
        if ch == '"':
            quoted = not quoted
        elif quoted:
            continue
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                spans.append((begin, i))
                return spans, i
        elif ch == "," and depth == 1:
            spans.append((begin, i))
            begin = i + 1
    raise ValueError("unbalanced call")


CALL = re.compile(r'(?<![A-Za-z_])(hit|hits|shot|shots|zone|healZone)\(')


def damage_parts(chain):
    """[(kind, multiplier span, count)] of the chain: hit frames (count = frames x projectiles) and zones (pulses)."""
    parts = []
    for m in CALL.finditer(chain):
        name = m.group(1)
        open_at = m.end() - 1
        spans, _ = split_args(chain, open_at)
        args = [chain[a:b].strip() for a, b in spans]
        if name == "hit":
            parts.append(("hit", spans[2], 1))
        elif name == "hits":
            parts.append(("hit", spans[4], int(args[2])))
        elif name in ("shot", "shots"):
            proj = args[1] if name == "shot" else args[3]
            fan = re.search(r"withFan\((\d+)", proj)
            count = int(fan.group(1)) if fan else 1
            if name == "shots":
                count *= int(args[2])
            parts.append(("hit", spans[2] if name == "shot" else spans[4], count))
        elif name == "zone":
            if float(args[7]) > 0:
                parts.append(("zone", spans[7], int(args[5])))
        # healZone: support, left as it is
    return parts


def value(chain, span):
    return float(chain[span[0]:span[1]].strip())


def power(chain):
    hits = sum(value(chain, s) * n for k, s, n in damage_parts(chain) if k == "hit")
    zones = sum(value(chain, s) * n for k, s, n in damage_parts(chain) if k == "zone")
    return hits, zones


def fmt(x):
    s = f"{x:.2f}".rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def rescale(chain, hit_factor, zone_factor):
    """The chain with every hit multiplier and zone damage multiplied; returns (new chain, old->new index map)."""
    edits = []
    for kind, span, _ in damage_parts(chain):
        old = value(chain, span)
        new = old * (hit_factor if kind == "hit" else zone_factor)
        if kind == "zone":
            new = min(ZONE_MAX, new)
        lead = len(chain[span[0]:span[1]]) - len(chain[span[0]:span[1]].lstrip())
        edits.append((span[0] + lead, span[1], fmt(new)))
    edits.sort()
    out = []
    pos = 0
    for a, b, text in edits:
        out.append(chain[pos:a])
        out.append(text)
        pos = b
    out.append(chain[pos:])
    return "".join(out), edits


def mapped_index(edits, i):
    """Where index i of the old chain lands in the new one (i never falls inside an edited number)."""
    shift = 0
    for a, b, text in edits:
        if b <= i:
            shift += len(text) - (b - a)
        elif a < i < b:
            raise ValueError("a piece boundary inside a number")
    return i + shift


def load(cls):
    spec = importlib.util.spec_from_file_location(f"spec_{cls}", os.path.join(SPECS, f"{cls}.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def targets(cls, nodes):
    """skill name -> (target total, hits, zones) for the attack skills of a class."""
    top_active, ult_floor, ult_top = TIERS[CLASS_TIER[cls]]
    attack = {}
    for n in nodes:
        if n[0] in ("a", "u"):
            h, z = power(n[5])
            if h + z > 0:
                attack[n[1]] = (n[0], h, z)
    def offensive(kind, h, z):
        return kind == "u" and h + z >= OFFENSIVE_ULTIMATE

    actives = {k: v for k, v in attack.items() if not offensive(*v)}
    ults = {k: v for k, v in attack.items() if offensive(*v)}
    lo = min(h + z for _, h, z in actives.values())
    hi = max(h + z for _, h, z in actives.values())
    out = {}
    for name, (_, h, z) in actives.items():
        t = MIN + (h + z - lo) / (hi - lo) * (top_active - MIN)
        out[name] = (max(t, h + z), h, z)  # never weaker than before
    if ults:
        best = max(h + z for _, h, z in ults.values())
        for name, (_, h, z) in ults.items():
            out[name] = (max(h + z, ult_floor, ult_top * (h + z) / best), h, z)
    return out


def factors(t, h, z):
    """Hit and zone factors for a target total t: the hits alone show at least MIN when the skill has hits."""
    if z == 0:
        return t / h, 1.0
    if h == 0:
        return 1.0, t / z
    hits = max(MIN, t * h / (h + z))
    return hits / h, max(0.0, t - hits) / z


def main():
    report = []
    for cls in CLASS_TIER:
        module = load(cls)
        plan = targets(cls, module.NODES)
        path = os.path.join(SPECS, f"{cls}.py")
        source = open(path, encoding="utf-8").read()
        tree = ast.parse(source)
        lines = source.splitlines(keepends=True)
        starts = [0]
        for line in lines:
            starts.append(starts[-1] + len(line))
        replacements = []
        nodes_list = next(n.value for n in tree.body if isinstance(n, ast.Assign)
                          and getattr(n.targets[0], "id", "") == "NODES")
        for elt in nodes_list.elts:
            kind = elt.elts[0].value
            name = elt.elts[1].value
            if name not in plan:
                continue
            chain_node = elt.elts[5]
            t, h, z = plan[name]
            hf, zf = factors(t, h, z)
            chain = chain_node.value
            new_chain, edits = rescale(chain, hf, zf)
            nh, nz = power(new_chain)
            # if a zone hit its limit, the rest goes to the hits
            if nz + nh < t - 0.05 and nh > 0:
                extra = (t - nz) / nh
                new_chain, edits2 = rescale(new_chain, extra, 1.0)
                # combine edits: recompute from the original with the final numbers
                final_h, final_z = power(new_chain)
                new_chain, edits = rescale(chain, final_h / h, final_z / z if z else 1.0)
            nh, nz = power(new_chain)
            report.append(f"{cls:14} {kind} {name:28} {h + z:6.2f} -> {nh + nz:6.2f} (hits {h:5.2f}->{nh:5.2f}, zone {z:5.2f}->{nz:5.2f})")
            a = starts[chain_node.lineno - 1] + chain_node.col_offset
            b = starts[chain_node.end_lineno - 1] + chain_node.end_col_offset
            segment = source[a:b]
            # the literal's pieces: their contents' spans in the runtime string
            pieces = []
            offset = 0
            for tok in tokenize.generate_tokens(io.StringIO(segment).readline):
                if tok.type == tokenize.STRING:
                    text = ast.literal_eval(tok.string)
                    pieces.append((tok, offset, offset + len(text)))
                    offset += len(text)
            assert offset == len(chain), (name, offset, len(chain))
            seg_lines = segment.splitlines(keepends=True)
            seg_starts = [0]
            for line in seg_lines:
                seg_starts.append(seg_starts[-1] + len(line))
            new_segment = segment
            for tok, s0, s1 in reversed(pieces):
                n0, n1 = mapped_index(edits, s0), mapped_index(edits, s1)
                quote = tok.string[0]
                content = new_chain[n0:n1]
                assert quote not in content
                p0 = seg_starts[tok.start[0] - 1] + tok.start[1]
                p1 = seg_starts[tok.end[0] - 1] + tok.end[1]
                new_segment = new_segment[:p0] + quote + content + quote + new_segment[p1:]
            replacements.append((a, b, new_segment))
        for a, b, text in sorted(replacements, reverse=True):
            source = source[:a] + text + source[b:]
        if APPLY:
            open(path, "w", encoding="utf-8").write(source)
    print("\n".join(report))


main()
