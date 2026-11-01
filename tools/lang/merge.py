"""Builds src/main/resources/assets/frostbound/lang/{en_us,ja_jp}.json from the core texts and ui_texts.py,
and checks that every key used in the Java sources has a translation.

Usage: python3 -I tools/lang/merge.py <repo root>
"""
import json
import os
import re
import sys

root = sys.argv[1] if len(sys.argv) > 1 else "."
here = os.path.join(root, "tools", "lang")
sys.path.insert(0, here)
import ui_texts  # noqa: E402

out_dir = os.path.join(root, "src", "main", "resources", "assets", "frostbound", "lang")
os.makedirs(out_dir, exist_ok=True)

dynamic = []
for t in ["overview", "build", "resource", "population", "work", "law", "research", "expedition", "weather", "history"]:
    dynamic.append("frostbound.ui.tab." + t)
for s in ["running", "off", "broken", "repairing", "fuel_out"]:
    dynamic.append("frostbound.ui.gen_state." + s)
for s in ["construction", "active", "paused", "upgrading"]:
    dynamic.append("frostbound.ui.bstate." + s)
dynamic += ["frostbound.ui.hunger_band.%d" % i for i in range(4)]
dynamic += ["frostbound.ui.temp_band.%d" % i for i in range(5)]
dynamic += ["frostbound.ui.law_short." + s for s in ["requires", "cooldown", "conflict", "enacted"]]
dynamic += ["frostbound.tutorial.build_food"]

result = {}
for lang in ["en_us", "ja_jp"]:
    with open(os.path.join(here, "core.%s.json" % lang), encoding="utf-8") as f:
        merged = json.load(f)
    ui = ui_texts.texts(lang)
    clash = set(merged) & set(ui)
    if clash:
        print("duplicate keys between core and ui:", sorted(clash))
    merged.update(ui)
    result[lang] = merged
    with open(os.path.join(out_dir, lang + ".json"), "w", encoding="utf-8") as f:
        json.dump(dict(sorted(merged.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")

# every literal key in the Java sources and every dynamic key must exist
used = set(dynamic)
pat = re.compile(r'"((?:frostbound|subtitles|block|entity|itemGroup|key)\.[a-z0-9_.]+[a-z0-9_])"')
for d in [os.path.join(root, "src", "main", "java"), os.path.join(root, "core", "src", "main", "java")]:
    for dirpath, _, files in os.walk(d):
        for fn in files:
            if fn.endswith(".java"):
                with open(os.path.join(dirpath, fn), encoding="utf-8") as f:
                    used.update(pat.findall(f.read()))
with open(os.path.join(here, "keys_core.txt"), encoding="utf-8") as f:
    used.update(l.strip() for l in f if l.strip())
ignore = {"frostbound.selftest", "frostbound.selftest.phase", "frostbound.autoshots"}
missing = 0
for lang, texts in result.items():
    for k in sorted(used - ignore):
        if k not in texts:
            print("%s missing %s" % (lang, k))
            missing += 1
print("keys: en_us=%d ja_jp=%d missing=%d" % (len(result["en_us"]), len(result["ja_jp"]), missing))
sys.exit(1 if missing else 0)
