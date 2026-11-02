#!/usr/bin/env python3
"""冥婚 ―MEIKON― sound generator.

Synthesises every sound effect and music cue of the mod from scratch
(numpy / scipy, no samples), encodes them to OGG Vorbis with ffmpeg and writes
``sounds.json``, the subtitle tables and a level report.

    python3 tools/audio/gen_sounds.py               # everything
    python3 tools/audio/gen_sounds.py --only 'bride/*' --spec /tmp/spec
    python3 tools/audio/gen_sounds.py --report-only # re-measure existing files

Output is deterministic: every file uses its own fixed seed (crc32 of its path).
"""
import argparse
import fnmatch
import json
import os
import subprocess
import sys
import time
from multiprocessing import Pool

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import analyze  # noqa: E402
import dsp  # noqa: E402
from catalog import CATALOG, files  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "meikon")
SOUND_DIR = os.path.join(ASSETS, "sounds")
FFMPEG = os.environ.get("FFMPEG", "/usr/local/bin/ffmpeg")
STREAM_MIN_SEC = 8.0


def jobs(pattern=None):
    out = []
    for ent in CATALOG:
        for path, idx in files(ent):
            if pattern and not (fnmatch.fnmatch(path, pattern) or fnmatch.fnmatch(ent.event, pattern)):
                continue
            out.append((ent.event, path, idx))
    return out


def entry_for(event):
    for ent in CATALOG:
        if ent.event == event:
            return ent
    raise KeyError(event)


def postprocess(x, ent):
    x = np.asarray(x, dtype=np.float64)
    # channel layout
    if ent.ch == 1 and x.ndim == 2:
        x = x.mean(axis=0)
    elif ent.ch == 2 and x.ndim == 1:
        x = np.vstack([x, x])
    # DC
    x = dsp.dc_block(x, f=12.0, circular=ent.loop)
    # level
    kind = ent.norm[0]
    if kind == "peak":
        if len(ent.norm) > 2:  # tame isolated crackle spikes (crest factor cap)
            x = dsp.norm_peak(x, 0.0)
            r = dsp.rms_db(x)
            if -r > ent.norm[2]:
                x = dsp.limiter(x, r + ent.norm[2], 0.002)
        x = dsp.norm_peak(x, ent.norm[1])
    elif kind == "rms":
        x = dsp.norm_rms(x, ent.norm[1])
        if dsp.peak_db(x) > ent.norm[2]:
            x = dsp.limiter(x, ent.norm[2], 0.01)
    # click safety for one-shots (never touches loops: they must stay circular)
    if not ent.loop:
        x = dsp.fade(x, 0.0005, 0.004)
    # absolute safety: no sample may clip
    pk = dsp.peak(x)
    if pk > 0.999:
        x = x * (0.999 / pk)
    return x


def encode(x, out_path, q):
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    ch = 1 if x.ndim == 1 else 2
    data = (x if x.ndim == 1 else x.T).astype(np.float32).tobytes()
    cmd = [FFMPEG, "-y", "-loglevel", "error", "-f", "f32le", "-ar", str(dsp.SR), "-ac", str(ch), "-i", "pipe:0",
           "-c:a", "libvorbis", "-q:a", str(q), "-map_metadata", "-1",
           "-fflags", "+bitexact", "-flags:a", "+bitexact", out_path]  # byte-identical reruns
    subprocess.run(cmd, input=data, check=True)


def dense_master(x, rms_db, ceiling_db):
    """Maximise, band-limit (less codec overshoot), brickwall to the ceiling;
    the internal RMS target is re-aimed so the *final* RMS lands on rms_db."""
    aim = rms_db
    for _ in range(4):
        y = dsp.maximize(x, aim, ceiling_db=ceiling_db)
        y = dsp.lowpass(y, 15500, 4)
        y = y - y.mean(axis=-1, keepdims=True)
        y = dsp.limiter(y, ceiling_db, 0.0015)
        y = y - y.mean(axis=-1, keepdims=True)
        y = np.clip(y, -dsp.db(ceiling_db), dsp.db(ceiling_db))
        err = rms_db - dsp.rms_db(y)
        if abs(err) < 0.05:
            break
        aim = min(aim + err, ceiling_db - 0.3)
    return y


def render(job):
    event, path, idx = job
    ent = entry_for(event)
    t0 = time.time()
    rng = dsp.rng_for(path)
    x = ent.fn(rng, idx)
    out = os.path.join(SOUND_DIR, path + ".ogg")
    if ent.norm[0] == "dense":
        # the decoded (true) peak of a saturated signal overshoots after Vorbis
        # encoding: lower the internal ceiling until the decoded file is clean
        _, rms_t, peak_t = ent.norm
        x = postprocess(x, ent._replace(norm=("none",)))
        ceil = peak_t
        for _ in range(6):
            y = dense_master(x, rms_t, ceil)
            encode(y, out, ent.q)
            pk = analyze.stats(analyze.decode(out, channels=ent.ch))["peak_db"]
            if pk <= peak_t + 0.05:
                break
            ceil -= (pk - peak_t) + 0.05
        x = y
    else:
        x = postprocess(x, ent)
        encode(x, out, ent.q)
    return path, time.time() - t0, x.shape[-1] / dsp.SR


def write_sounds_json(durations):
    data = {}
    for ent in CATALOG:
        sounds = []
        for path, _ in files(ent):
            s = {"name": f"meikon:{path}"}
            dur = durations.get(path, 0.0)
            if dur > STREAM_MIN_SEC:
                s["stream"] = True
            if ent.att is not None and ent.ch == 1:
                s["attenuation_distance"] = ent.att
            if ent.extra.get("preload"):
                s["preload"] = True
            sounds.append(s)
        item = {}
        if ent.ja is not None:
            item["subtitle"] = f"subtitles.meikon.{ent.event}"
        item["sounds"] = sounds
        data[ent.event] = item
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def write_subtitles():
    ja = {f"subtitles.meikon.{e.event}": e.ja for e in CATALOG if e.ja is not None}
    en = {f"subtitles.meikon.{e.event}": e.en for e in CATALOG if e.en is not None}
    for name, d in [("subtitles_ja.json", ja), ("subtitles_en.json", en)]:
        with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=2)
            f.write("\n")


def seam_rank(x, w=64, positions=400):
    """Percentile rank (0-100) of the 2nd-difference energy around the loop
    seam among the same measure at evenly spaced interior positions."""
    x = np.atleast_2d(x)
    n = x.shape[1]

    def hf(seg):
        return float(np.sum(np.diff(seg, n=2, axis=1) ** 2))

    seam = hf(np.concatenate([x[:, -w:], x[:, :w]], axis=1))
    ref = np.array([hf(x[:, p - w:p + w]) for p in np.linspace(w, n - w, positions).astype(int)])
    return 100.0 * np.mean(ref < seam), seam / (np.median(ref) + 1e-12)


def report(spec_dir=None, pattern=None):
    lines = []
    total = 0
    hdr = f"{'file':38s} {'dur s':>7s} {'ch':>3s} {'peak dBFS':>10s} {'RMS dBFS':>9s} {'KB':>7s}  notes"
    lines.append(hdr)
    lines.append("-" * len(hdr))
    durations = {}
    for ent in CATALOG:
        for path, _ in files(ent):
            fp = os.path.join(SOUND_DIR, path + ".ogg")
            if not os.path.exists(fp):
                lines.append(f"{path:38s} MISSING")
                continue
            x = analyze.decode(fp, channels=ent.ch)
            st = analyze.stats(x)
            durations[path] = st["dur"]
            kb = os.path.getsize(fp) / 1024
            total += os.path.getsize(fp)
            notes = []
            if ent.loop:
                rank, ratio = seam_rank(x)
                jump, _ = analyze.seam_score(x)
                notes.append(f"loop seam: rank {rank:.0f}%, hf x{ratio:.2f}, step x{jump:.2f}")
            if st["dc"] > 1e-3:
                notes.append(f"DC {st['dc']:.4f}")
            if st["peak_db"] > -0.1:
                notes.append("CLIP?")
            lines.append(f"{path:38s} {st['dur']:7.2f} {st['ch']:3d} {st['peak_db']:10.2f} {st['rms_db']:9.2f} {kb:7.1f}  {'; '.join(notes)}")
            if spec_dir and (pattern is None or fnmatch.fnmatch(path, pattern)):
                os.makedirs(spec_dir, exist_ok=True)
                analyze.spectrogram_png(x, os.path.join(spec_dir, path.replace("/", "__") + ".png"),
                                        f"{path}  dur={st['dur']:.2f}s peak={st['peak_db']:.1f} rms={st['rms_db']:.1f}",
                                        fmax=12000 if ent.ch == 1 else 8000)
    lines.append("-" * len(hdr))
    lines.append(f"{len(durations)} files, total {total / 1024 / 1024:.2f} MB")
    lines.append("")
    lines.append("Loop seam columns: 2nd-difference (click) energy in a 3 ms window around the wrap point,")
    lines.append("as percentile rank among 400 interior positions and relative to their median; step = |x[0]-x[-1]|")
    lines.append("relative to the median sample step.  Loops are built circularly (periodic noise/oscillators,")
    lines.append("circular reverb), so a high value only means an event sits on the seam: game/chase starts on")
    lines.append("its bar-1 downbeat drum hit by design.")
    with open(os.path.join(HERE, "report.txt"), "w", encoding="utf-8") as f:
        f.write("MEIKON sound report (measured on the decoded OGG files)\n\n")
        f.write("\n".join(lines) + "\n")
    return durations, total


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", help="glob on file path or event id (e.g. 'bride/*', 'game.chase')")
    ap.add_argument("--jobs", type=int, default=min(2, os.cpu_count() or 1))
    ap.add_argument("--spec", help="also write spectrogram PNGs of the decoded files to this directory")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    t0 = time.time()
    if not args.report_only:
        todo = jobs(args.only)
        # longest first for better load balancing
        weight = {"music/": 9, "ambient/drone": 7, "ambient/wind": 5, "game/chase": 4, "bride/hum": 3}
        todo.sort(key=lambda j: -max([w for k, w in weight.items() if j[1].startswith(k)] + [1]))
        print(f"rendering {len(todo)} files with {args.jobs} worker(s)...")
        if args.jobs > 1:
            with Pool(args.jobs) as pool:
                for path, dt, dur in pool.imap_unordered(render, todo):
                    print(f"  {path:38s} {dur:6.2f}s  ({dt:.1f}s)")
        else:
            for j in todo:
                path, dt, dur = render(j)
                print(f"  {path:38s} {dur:6.2f}s  ({dt:.1f}s)")
    durations, total = report(args.spec, args.only)
    write_sounds_json(durations)
    write_subtitles()
    print(f"done in {time.time() - t0:.1f}s - {len(durations)} files, {total / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
