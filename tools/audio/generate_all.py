#!/usr/bin/env python3
"""Regenerates every ETERNAL REALM sound (music, jingles, SFX) and the metadata files.

    python3 tools/audio/generate_all.py              # everything
    python3 tools/audio/generate_all.py --only music.boss skill.slash   # some sounds (ids or file stems)
    python3 tools/audio/generate_all.py --metadata   # only sounds.json and docs/i18n/audio.json
    python3 tools/audio/generate_all.py --analyze    # also writes spectrograms/level plots to build/audio-analysis

Deterministic: the same sources give byte-identical OGG files (fixed seeds, bit-exact ffmpeg muxing).
Needs numpy, scipy and ffmpeg with libvorbis. No samples or downloads: everything is synthesised here.
"""
import argparse
import json
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from eraudio import catalog  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
SOUNDS_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
I18N_JSON = os.path.join(ROOT, "docs", "i18n", "audio.json")
ERSOUNDS_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "eternalrealm", "sound", "ERSounds.java")
ANALYSIS_DIR = os.path.join(ROOT, "build", "audio-analysis")

MUSIC_BUDGET = 12_000_000
SFX_BUDGET = 2_000_000


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def java_ids():
    with open(ERSOUNDS_JAVA, encoding="utf-8") as f:
        return re.findall(r'\bsound\("([a-z0-9_.]+)"\)', f.read())


def write_metadata():
    write_json(SOUNDS_JSON, catalog.sounds_json())
    write_json(I18N_JSON, catalog.i18n())
    ids = catalog.all_ids()
    jids = java_ids()
    if sorted(ids) != sorted(jids) or len(set(ids)) != len(ids):
        missing = sorted(set(ids) - set(jids))
        extra = sorted(set(jids) - set(ids))
        raise SystemExit(f"ERSounds.java and the catalog differ: missing in Java {missing}, only in Java {extra}")
    print(f"metadata: {len(ids)} sound events, sounds.json and docs/i18n/audio.json written")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", nargs="*", default=None)
    parser.add_argument("--metadata", action="store_true")
    parser.add_argument("--analyze", action="store_true")
    args = parser.parse_args()

    write_metadata()
    if args.metadata:
        return
    from eraudio import build  # noqa: E402  (heavy imports only when rendering)
    started = time.time()
    build.build_all(SOUNDS_DIR, only=args.only, analyze_dir=ANALYSIS_DIR if args.analyze else None)
    build.report_sizes(SOUNDS_DIR, MUSIC_BUDGET, SFX_BUDGET)
    print(f"done in {time.time() - started:.0f} s")


if __name__ == "__main__":
    main()
