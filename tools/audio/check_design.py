#!/usr/bin/env python3
"""Checks tools/audio/catalog.py against docs/DESIGN.md section 8: the same event ids, variant counts, channel
(M/S) and attenuation distances.  Prints every mismatch; exit code 1 if there is any."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from catalog import CATALOG  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sec = open(os.path.join(ROOT, "docs", "DESIGN.md"), encoding="utf-8").read().split("## 8. Sounds", 1)[1].split("## 9.", 1)[0]

want = {}  # event -> dict(count, ch, att)


def put(ev, **kw):
    want.setdefault(ev, {}).update({k: v for k, v in kw.items() if v is not None})


section = None
prose = {}
for line in sec.splitlines():
    s = line.strip()
    if s.startswith("Ambient beds"):
        section = "beds"
    elif s.startswith("Positional loops"):
        section = "ploops"
    elif s.startswith("Music"):
        section = "music"
    elif s.startswith("Restaurant one-shots"):
        section = "rest"
    elif s.startswith("Camera and items"):
        section = "cam"
    elif s.startswith("Player (M)"):
        section = "player"
    elif s.startswith("Spirits (M)"):
        section = "spirits"
    elif s.startswith("Game (S)"):
        section = "game"
    if s.startswith("|") and "`" in s and not s.startswith("| event"):
        cells = [c.strip() for c in s.strip("|").split("|")]
        evs = re.findall(r"`([a-z_]+\.[a-z_0-9]+)`", cells[0])
        if section in ("beds", "music"):
            for ev in evs:
                put(ev, count=1, ch=2)
            continue
        counts = [int(x) for x in re.findall(r"\d+", cells[1])]
        third = cells[2]
        for k, ev in enumerate(evs):
            c = counts[k] if k < len(counts) else counts[-1]
            if section == "cam":
                ch = 2 if third.startswith("S") else 1
                att = int(re.findall(r"att (\d+)", third)[0]) if re.findall(r"att (\d+)", third) else None
                put(ev, count=c, ch=ch, att=att)
            elif third == "S":
                put(ev, count=c, ch=2)
            else:
                put(ev, count=c, ch=1, att=int(third))
    elif section in ("ploops", "player", "game") and not s.startswith("|"):
        prose[section] = prose.get(section, "") + " " + s

for section, text in prose.items():
    for m in re.finditer(r"`([a-z_]+\.[a-z_0-9]+)`\s*(\(([^)]*)\)|×(\d+))?", text):
        ev, inner, times = m.group(1), m.group(3), m.group(4)
        if section == "ploops":
            if inner and inner.startswith("S"):
                put(ev, count=1, ch=2)
            elif inner and "one-shots" in inner:
                put(ev, count=int(re.findall(r"×(\d+)", inner)[0]), ch=1, att=int(re.findall(r", (\d+)", inner)[0]))
            elif inner:
                put(ev, count=1, ch=1, att=int(re.findall(r"^(\d+)", inner)[0]))
        elif section == "player":
            put(ev, count=int(times) if times else 1, ch=1)
        else:
            put(ev, count=int(times) if times else 1, ch=2)

have = {e.event: e for e in CATALOG}
bad = 0
for ev, w in want.items():
    e = have.get(ev)
    if e is None:
        print(f"MISSING in catalog: {ev}")
        bad += 1
        continue
    if w.get("count") is not None and e.count != w["count"]:
        print(f"{ev}: count {e.count} != design {w['count']}")
        bad += 1
    if w.get("ch") is not None and e.ch != w["ch"]:
        print(f"{ev}: channels {e.ch} != design {w['ch']}")
        bad += 1
    if w.get("att") is not None and e.att != w["att"]:
        print(f"{ev}: attenuation {e.att} != design {w['att']}")
        bad += 1
for ev in have:
    if ev not in want:
        print(f"EXTRA in catalog (not in DESIGN section 8): {ev}")
        bad += 1
print(f"design: {len(want)} events; catalog: {len(have)} events, {sum(e.count for e in CATALOG)} files; "
      f"{'OK' if not bad else str(bad) + ' problem(s)'}")
sys.exit(1 if bad else 0)
