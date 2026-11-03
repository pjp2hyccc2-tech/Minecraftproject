#!/usr/bin/env python3
"""Checks tools/audio/catalog.py against docs/DESIGN.md section 8: the same event ids, variant counts, channels
(M/S), attenuation distances and loop flags.  Prints every mismatch; exit code 1 if there is any.

Section 8 is prose: "Beds (S, loops): `amb.apartment` (60 s: ...), ...", "Positional loops (M): `fixture.fridge` (8)",
"Music (S): ...", "One-shots (M unless noted; att): `ritual.cut` (scissors through fabric, 8), `door.fusuma` ×3
(paper door sliding, 8), `door.front_open` / `door.front_close` (steel apartment door, chain, 16), ...".  Each
paragraph sets the default channel layout; a parenthesis may override it (S / M), carry an attenuation distance (the
last bare number; "N s" is a duration, "×N" inside the parenthesis describes the content) and say "loop" /
"no loop"."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from catalog import CATALOG  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sec = open(os.path.join(ROOT, "docs", "DESIGN.md"), encoding="utf-8").read().split("## 8. Sounds", 1)[1].split("## 9.", 1)[0]

PARAS = [("Beds", dict(ch=2, loop=True)), ("Positional loops", dict(ch=1, loop=True)),
         ("Music", dict(ch=2, loop=True)), ("One-shots", dict(ch=1, loop=False))]
GROUP = re.compile(r"((?:`[a-z_]+\.[a-z_0-9]+`\s*(?:/\s*)?)+)\s*(?:×(\d+))?\s*(?:\(([^()]*)\))?")


def paragraphs(text):
    """{heading: paragraph text} for the four lists (each runs until the next heading or a blank line)."""
    out = {}
    cur = None
    for line in text.splitlines():
        s = line.strip()
        hit = [h for h, _ in PARAS if s.startswith(h + " (")]
        if hit:
            cur = hit[0]
            out[cur] = s.split(":", 1)[1] if ":" in s else ""
            continue
        if not s:
            cur = None
            continue
        if cur:
            out[cur] += " " + s
    return out


def parse_spec(spec, default):
    """(channels, attenuation, loop) from the text inside a parenthesis."""
    ch, loop = default["ch"], default["loop"]
    att = None
    if spec is None:
        return ch, att, loop
    head = spec.split(":", 1)[0]
    first = head.split(",")[0].strip()
    if re.match(r"^S\b", first):
        ch = 2
    elif re.match(r"^M\b", first):
        ch = 1
    if "no loop" in head:
        loop = False
    elif re.search(r"\bloop\b", head):
        loop = True
    if ch == 1:
        m = re.search(r"\batt (\d+)", head)
        if m:
            att = int(m.group(1))
        else:
            nums = [int(x.group(1)) for x in re.finditer(r"(?<![×\d.])(\d+)(?!\s*s\b)(?![\d.])", head)]
            if nums:
                att = nums[-1]
    return ch, att, loop


want = {}
for head, default in PARAS:
    text = paragraphs(sec).get(head, "")
    for m in GROUP.finditer(text):
        evs = re.findall(r"`([a-z_]+\.[a-z_0-9]+)`", m.group(1))
        count = int(m.group(2)) if m.group(2) else 1
        ch, att, loop = parse_spec(m.group(3), default)
        for ev in evs:
            lp = loop or ev.endswith("_loop")
            want[ev] = dict(count=count, ch=ch, att=att, loop=lp)

have = {e.event: e for e in CATALOG}
bad = 0
for ev, w in want.items():
    e = have.get(ev)
    if e is None:
        print(f"MISSING in catalog: {ev}")
        bad += 1
        continue
    if e.count != w["count"]:
        print(f"{ev}: count {e.count} != design {w['count']}")
        bad += 1
    if e.ch != w["ch"]:
        print(f"{ev}: channels {e.ch} != design {w['ch']}")
        bad += 1
    if w["att"] is not None and e.att != w["att"]:
        print(f"{ev}: attenuation {e.att} != design {w['att']}")
        bad += 1
    if e.loop != w["loop"]:
        print(f"{ev}: loop {e.loop} != design {w['loop']}")
        bad += 1
for ev in have:
    if ev not in want:
        print(f"EXTRA in catalog (not in DESIGN section 8): {ev}")
        bad += 1
print(f"design: {len(want)} events; catalog: {len(have)} events, {sum(e.count for e in CATALOG)} files; "
      f"{'OK' if not bad else str(bad) + ' problem(s)'}")
sys.exit(1 if bad else 0)
