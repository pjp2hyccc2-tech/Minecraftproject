"""Renders, masters, encodes and verifies every sound."""
import importlib
import json
import os
import time
from multiprocessing import get_context

import numpy as np

from . import analysis, catalog, dsp, encode

# file stem -> (module, function, loudness target LUFS, vorbis quality)
MUSIC = {
    "music/title": ("tracks.title", "render", -14.0, 2),
    "music/area_1": ("tracks.area1", "render", -14.0, 2),
    "music/area_2": ("tracks.area2", "render", -14.0, 2),
    "music/area_3": ("tracks.area3", "render", -14.0, 2),
    "music/area_4": ("tracks.area4", "render", -14.0, 2),
    "music/area_5": ("tracks.area5", "render", -14.0, 2),
    "music/area_6": ("tracks.area6", "render", -14.0, 2),
    "music/area_7": ("tracks.area7", "render", -14.0, 2),
    "music/dungeon": ("tracks.dungeon", "render", -15.0, 2),
    "music/boss": ("tracks.boss", "render", -14.0, 2),
    "music/final_boss": ("tracks.final_boss", "render", -14.0, 2),
    "music/ending": ("tracks.ending", "render", -14.0, 2),
    "jingle/victory": ("tracks.jingles", "victory", -14.0, 3),
    "jingle/level_up": ("tracks.jingles", "level_up", -14.0, 3),
    "jingle/quest_complete": ("tracks.jingles", "quest_complete", -14.0, 3),
    "jingle/discovery": ("tracks.jingles", "discovery", -14.0, 3),
}
SFX_QUALITY = 4


def _matches(stem, only):
    if not only:
        return True
    ids = {m.id: m.file for m in catalog.MUSIC}
    ids.update({j.id: j.file for j in catalog.JINGLES})
    for s in catalog.SFX:
        for f in s.files:
            ids.setdefault(s.id, f)
    for o in only:
        if o == stem or stem.endswith("/" + o) or o in stem:
            return True
        if o in ids and (ids[o] == stem or any(stem == f for s in catalog.SFX if s.id == o for f in s.files)):
            return True
    return False


def _render_music(stem):
    mod, fn, target, quality = MUSIC[stem]
    module = importlib.import_module("eraudio." + mod)
    t0 = time.time()
    result = getattr(module, fn)()
    audio, loop = result["audio"], result["loop"]
    mastered = dsp.master(audio, target_lufs=result.get("lufs", target), loop=loop)
    return mastered, loop, quality, result.get("warnings", []), time.time() - t0


def _render_sfx(stem):
    from . import sfx
    t0 = time.time()
    audio, peak_db = sfx.render(stem)
    audio = audio - np.mean(audio)
    audio = dsp.highpass(audio, 25.0)
    tp = dsp.true_peak(audio)
    audio = audio * (dsp.db_to_gain(peak_db) / max(tp, 1e-9))
    k = min(audio.size, int(0.003 * dsp.SR))
    audio[-k:] *= np.linspace(1, 0, k)
    return audio, False, SFX_QUALITY, [], time.time() - t0


def build_one(args):
    stem, out_dir, analyze_dir = args
    if stem in MUSIC:
        audio, loop, quality, warnings, secs = _render_music(stem)
    else:
        audio, loop, quality, warnings, secs = _render_sfx(stem)
    path = os.path.join(out_dir, stem + ".ogg")
    size = encode.encode_ogg(audio, path, quality)
    channels = 2 if audio.ndim == 2 else 1
    decoded = encode.decode_ogg(path, channels)
    info = analysis.measure(audio, loop)
    info["render_s"] = round(secs, 1)
    info["bytes"] = size
    info["decoded_samples"] = int(decoded.shape[-1])
    info["samples"] = int(audio.shape[-1])
    info["decoded_peak_db"] = round(float(dsp.gain_to_db(np.max(np.abs(decoded)))), 2)
    if loop:
        info["decoded_seam"] = analysis.seam_report(decoded)
        info["codec_edges"] = analysis.codec_edges(audio, decoded)
    info["warnings"] = warnings
    if analyze_dir:
        analysis.plot(stem, audio, analyze_dir, loop)
    return stem, info


def all_stems():
    return list(MUSIC.keys()) + catalog.sfx_files()


def build_all(out_dir, report_dir, only=None, plots=False, jobs=2):
    stems = [s for s in all_stems() if _matches(s, only)]
    stems.sort(key=lambda s: (s not in MUSIC, s))   # long renders first
    results = {}
    args = [(s, out_dir, report_dir if plots else None) for s in stems]
    if jobs > 1 and len(args) > 1:
        with get_context("fork").Pool(jobs, maxtasksperchild=4) as pool:
            for stem, info in pool.imap_unordered(build_one, args):
                results[stem] = info
                _print(stem, info)
    else:
        for a in args:
            stem, info = build_one(a)
            results[stem] = info
            _print(stem, info)
    problems = check(results)
    os.makedirs(report_dir, exist_ok=True)
    old = {}
    rp = os.path.join(report_dir, "report.json")
    if os.path.exists(rp):
        with open(rp) as f:
            old = json.load(f)
    old.update(results)
    with open(rp, "w") as f:
        json.dump(old, f, indent=1, sort_keys=True)
    if problems:
        raise SystemExit("PROBLEMS:\n  " + "\n  ".join(problems))
    return results


def _print(stem, info):
    seam = info.get("decoded_seam")
    edges = info.get("codec_edges")
    extra = (f" seam jump {seam['jump']}, codec edges {edges['start']}/{edges['end']}" if seam else "")
    print(f"{stem:38s} {info['seconds']:7.2f}s {info['lufs']:6.1f} LUFS  TP {info['true_peak_db']:5.1f}  "
          f"{info['bytes'] / 1024:7.1f} KiB  {info['render_s']:5.1f}s{extra}", flush=True)
    for w in info.get("warnings", []):
        print("    warning:", w)


def check(results):
    problems = []
    for stem, info in results.items():
        if info["true_peak_db"] > -0.9:
            problems.append(f"{stem}: true peak {info['true_peak_db']} dBTP")
        if info["decoded_peak_db"] >= 0.0:
            problems.append(f"{stem}: decoded file clips ({info['decoded_peak_db']} dBFS)")
        if "decoded_seam" in info:
            if info["decoded_samples"] != info["samples"]:
                problems.append(f"{stem}: decoded length {info['decoded_samples']} != {info['samples']} (loop gap)")
            for key in ("seam", "decoded_seam"):
                # a transient may legitimately sit on the loop point; gross errors (a missing wrap) are far larger
                if info[key]["jump"] > 2.0:
                    problems.append(f"{stem}: sample step at the loop point stands out ({key} {info[key]})")
            e = info["codec_edges"]
            # the error scales with the signal level at the edges; allow for louder edges
            if max(e["start"], e["end"]) > 3.0 * max(1.0, e["edge_level"]):
                problems.append(f"{stem}: codec error at the file edges {e}")
    return problems


def report_sizes(out_dir, music_budget, sfx_budget):
    def total(files):
        return sum(os.path.getsize(os.path.join(out_dir, f + ".ogg")) for f in files
                   if os.path.exists(os.path.join(out_dir, f + ".ogg")))
    music = total(catalog.music_files())
    sfx = total(catalog.sfx_files())
    missing = [f for f in catalog.music_files() + catalog.sfx_files()
               if not os.path.exists(os.path.join(out_dir, f + ".ogg"))]
    print(f"music+jingles: {music / 1e6:.2f} MB (budget {music_budget / 1e6:.0f} MB), "
          f"sfx: {sfx / 1e6:.3f} MB (budget {sfx_budget / 1e6:.0f} MB)")
    if missing:
        print(f"missing files: {len(missing)}: {missing[:6]}{' ...' if len(missing) > 6 else ''}")
    if music > music_budget or sfx > sfx_budget:
        raise SystemExit("over budget")
