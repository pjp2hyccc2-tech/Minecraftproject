#!/usr/bin/env python3
"""ひとりかくれんぼ ―HITORI KAKURENBO― sound generator.

Synthesises every sound effect and music cue of the mod from scratch
(numpy / scipy, no samples), encodes them to OGG Vorbis with ffmpeg and writes
``sounds.json``, the subtitle tables and a measurement report.

    python3 tools/audio/gen_sounds.py               # everything
    python3 tools/audio/gen_sounds.py --only 'door/*' --spec /tmp/spec
    python3 tools/audio/gen_sounds.py --report-only # re-measure existing files

Output is deterministic: every file uses its own fixed seed (crc32 of its
path).  Every decoded file is verified to stay at or below -1 dBTP (true
peak, 4x oversampled); jumpscares are maximised right up to that ceiling.
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
import voice  # noqa: E402
from catalog import CATALOG, EXTRAS, files, resolve  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hitori")
SOUND_DIR = os.path.join(ASSETS, "sounds")
FFMPEG = os.environ.get("FFMPEG", "/usr/local/bin/ffmpeg")
STREAM_MIN_SEC = 10.0  # beds and music; positional one-shots stay static (Minecraft has ~8 streaming channels)
TP_CEILING = -1.0


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
    if ent.ch == 1 and x.ndim == 2:
        x = x.mean(axis=0)
    elif ent.ch == 2 and x.ndim == 1:
        x = np.vstack([x, x])
    x = dsp.dc_block(x, f=12.0, circular=ent.loop)
    if ent.loop and ent.extra.get("seam", "quiet") == "quiet":
        x = np.roll(x, -dsp.quiet_rotation(x), axis=-1)
    kind = ent.norm[0]
    if kind == "peak":
        if len(ent.norm) > 2:  # tame isolated crackle spikes (crest factor cap)
            x = dsp.norm_peak(x, 0.0)
            r = dsp.rms_db(x)
            if -r > ent.norm[2]:
                x = dsp.limiter(x, r + ent.norm[2], 0.002)
        x = dsp.norm_peak(x, ent.norm[1])
    elif kind == "lufs":
        x = dsp.norm_lufs(x, ent.norm[1], ent.norm[2])
    elif kind == "rms":
        x = dsp.norm_rms(x, ent.norm[1])
        if dsp.peak_db(x) > ent.norm[2]:
            x = dsp.limiter(x, ent.norm[2], 0.01)
    if not ent.loop:  # click safety for one-shots (loops must stay circular)
        x = dsp.fade(x, 0.0005, 0.004)
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


def decoded_tp(path, ch):
    return dsp.true_peak_db(analyze.decode(path, channels=ch))


def dense_master(x, rms_db, ceiling_db):
    """Maximise, band-limit (less codec overshoot), brickwall to the ceiling;
    the internal RMS target is re-aimed so the *final* RMS lands on rms_db."""
    aim = rms_db
    y = x
    for _ in range(4):
        y = dsp.maximize(x, aim, ceiling_db=ceiling_db)
        y = dsp.lowpass(y, 15000, 4)
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
    try:
        return _render(job)
    except Exception as ex:  # report and keep going
        import traceback
        return job[1], -1.0, f"ERROR {ex!r}\n{traceback.format_exc()}"


def _render(job):
    event, path, idx = job
    ent = entry_for(event)
    t0 = time.time()
    rng = dsp.rng_for(path)
    x = resolve(ent)(rng, idx)
    out = os.path.join(SOUND_DIR, path + ".ogg")
    if ent.norm[0] == "dense":
        # the decoded (true) peak of a saturated signal overshoots after Vorbis
        # encoding: lower the internal ceiling until the decoded file is clean
        _, rms_t, peak_t = ent.norm
        x = postprocess(x, ent._replace(norm=("none",)))
        ceil = peak_t - 0.6
        y = x
        post = ent.extra.get("post_rough")

        def master(c):
            y = dense_master(x, rms_t + (2.0 if post else 0.0), c)
            if post:  # 30-150 Hz roughness AM survives only after maximising
                y = np.vstack([voice.rough_mod(ch, dsp.rng_for(path, 7), *post) for ch in y])
                y = y - y.mean(axis=-1, keepdims=True)  # (a high-pass here would overshoot by 4 dB)
            encode(y, out, ent.q)
            return y, decoded_tp(out, ent.ch)

        lo, hi = peak_t - 4.0, peak_t  # bisection on the internal ceiling: land just under the true-peak limit
        best = None
        for _ in range(7):
            mid = 0.5 * (lo + hi)
            y, tp = master(mid)
            if tp <= peak_t:
                best = (mid, y)
                lo = mid
                if tp > peak_t - 0.15:
                    break
            else:
                hi = mid
        if best is None:
            best = (lo, master(lo)[0])
        x = best[1]
        encode(x, out, ent.q)
    else:
        x = postprocess(x, ent)
        encode(x, out, ent.q)
        for _ in range(4):  # true-peak safety on the decoded file
            tp = decoded_tp(out, ent.ch)
            if tp <= TP_CEILING:
                break
            x = x * dsp.db(TP_CEILING - tp - 0.1)
            encode(x, out, ent.q)
    return path, time.time() - t0, x.shape[-1] / dsp.SR


def write_sounds_json(durations):
    """sounds.json for every event that has its files (positional sounds get their attenuation distance; files
    longer than 10 s stream unless the entry says stream=False; diegetic events carry their subtitle)."""
    data = {}
    for ent in CATALOG:
        sounds = []
        for path, _ in files(ent):
            if not os.path.exists(os.path.join(SOUND_DIR, path + ".ogg")):
                continue
            s = {"name": f"hitori:{path}"}
            if durations.get(path, 0.0) > STREAM_MIN_SEC and ent.extra.get("stream", True):
                s["stream"] = True
            if ent.att is not None and ent.ch == 1:
                s["attenuation_distance"] = ent.att
            if ent.extra.get("preload"):
                s["preload"] = True
            sounds.append(s)
        if not sounds:
            continue
        item = {}
        if ent.ja is not None and ent.extra.get("sub", True):
            item["subtitle"] = f"subtitles.hitori.{ent.event}"
        item["sounds"] = sounds
        data[ent.event] = item
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def write_subtitles():
    ja = {f"subtitles.hitori.{e.event}": e.ja for e in CATALOG if e.ja is not None}
    en = {f"subtitles.hitori.{e.event}": e.en for e in CATALOG if e.en is not None}
    for name, d in [("subtitles_ja.json", ja), ("subtitles_en.json", en)]:
        with open(os.path.join(HERE, name), "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=2)
            f.write("\n")


def _group(ent):
    if ent.event.startswith("music."):
        return "music"
    if ent.loop and ent.ch == 2:
        return "beds"
    if ent.loop:
        return "pos. loops"
    if ent.event == "game.jumpscare":
        return "jumpscare"
    if ent.ja and ent.ja.startswith("「"):
        return "voice lines"
    if ent.ch == 2:
        return "game / UI"
    return "foley"


CACHE = os.path.join(HERE, ".stats_cache.json")


def _stats_cached(cache, path, fp, ent):
    st_ = os.stat(fp)
    key = f"{st_.st_size}:{int(st_.st_mtime)}"
    c = cache.get(path)
    if c and c.get("key") == key:
        return c["st"], c["notes"]
    x = analyze.decode(fp, channels=ent.ch)
    st = analyze.full_stats(x)
    st = {k: float(v) for k, v in st.items()}
    notes = []
    if ent.loop:
        hf, step = analyze.seam_local(x)
        n = x.shape[-1]
        inner = [analyze.seam_local(np.concatenate([x[:, p:], x[:, :p]], axis=1))[0]
                 for p in np.linspace(n // 20, n - n // 20, 40).astype(int)]
        pct = 100.0 * np.mean(np.array(inner) < hf)
        w = 32

        def hfe(seg):
            return float(np.sum(np.diff(seg, n=2, axis=1) ** 2))
        seam_abs = hfe(np.concatenate([x[:, -w:], x[:, :w]], axis=1))
        typical = np.median([hfe(x[:, p - w:p + w]) for p in np.linspace(w, n - w, 400).astype(int)])
        abs_db = 10 * np.log10((seam_abs + 1e-20) / (typical + 1e-20))
        notes.append(f"loop seam hf x{hf:.2f} (interior median x{np.median(inner):.2f}, seam rank {pct:.0f}%, "
                     f"{abs_db:+.0f} dB vs the file's typical HF)" + (" CLICK?" if pct >= 98 and hf > 3 and abs_db > 0 else ""))
    if ent.ch == 2 and (ent.event.startswith("music.") or ent.loop):
        bp = analyze.binaural_pairs(x)
        if bp:
            notes.append("binaural " + ", ".join(f"{a:.1f}/{b:.1f}Hz(d{d:.1f})" for a, b, d, _ in bp[:2]))
    cache[path] = {"key": key, "st": st, "notes": notes}
    return st, notes


def report(spec_dir=None, pattern=None):
    cache = {}
    if os.path.exists(CACHE):
        try:
            with open(CACHE, encoding="utf-8") as f:
                cache = json.load(f)
        except (OSError, ValueError):
            cache = {}
    lines = []
    total = 0
    hdr = (f"{'file':32s} {'dur s':>6s} {'ch':>2s} {'peak':>6s} {'TP':>6s} {'RMS':>6s} {'LUFS':>6s} {'Mmax':>6s} "
           f"{'crest':>5s} {'cent':>5s} {'rough':>5s} {'KB':>6s}  notes")
    lines.append(hdr)
    lines.append("-" * len(hdr))
    durations = {}
    missing = []
    groups = {}
    worst_tp = -100.0
    for ent in CATALOG:
        for path, _ in files(ent):
            fp = os.path.join(SOUND_DIR, path + ".ogg")
            if not os.path.exists(fp):
                lines.append(f"{path:32s} MISSING")
                missing.append(path)
                continue
            st, notes = _stats_cached(cache, path, fp, ent)
            notes = list(notes)
            durations[path] = st["dur"]
            kb = os.path.getsize(fp) / 1024
            total += os.path.getsize(fp)
            worst_tp = max(worst_tp, st["tp_db"])
            groups.setdefault(_group(ent), []).append((path, st))
            if st["dc"] > 1e-3:
                notes.append(f"DC {st['dc']:.4f}")
            if st["tp_db"] > TP_CEILING + 0.05:
                notes.append("TP>-1!")
            lines.append(f"{path:32s} {st['dur']:6.2f} {int(st['ch']):2d} {st['peak_db']:6.1f} {st['tp_db']:6.1f} "
                         f"{st['rms_db']:6.1f} {st['lufs']:6.1f} {st['mmax']:6.1f} {st['crest']:5.1f} "
                         f"{st['centroid']:5.0f} {st['rough']:5.2f} {kb:6.1f}  {'; '.join(notes)}")
            if spec_dir and (pattern is None or fnmatch.fnmatch(path, pattern) or fnmatch.fnmatch(ent.event, pattern)):
                os.makedirs(spec_dir, exist_ok=True)
                x = analyze.decode(fp, channels=ent.ch)
                analyze.spectrogram_png(x, os.path.join(spec_dir, path.replace("/", "__") + ".png"),
                                        f"{path}  dur={st['dur']:.2f}s TP={st['tp_db']:.1f} LUFS={st['lufs']:.1f} rough={st['rough']:.2f}",
                                        fmax=12000 if ent.ch == 1 else 8000)
    lines.append("-" * len(hdr))
    lines.append(f"{len(durations)} files, total {total / 1024 / 1024:.2f} MB, worst true peak {worst_tp:.2f} dBTP"
                 + (f", {len(missing)} MISSING" if missing else ""))
    lines.append("")
    lines.append("Level summary (decoded files)")
    for g in ("beds", "pos. loops", "music", "foley", "voice lines", "game / UI", "jumpscare"):
        items = groups.get(g, [])
        if not items:
            continue
        lu = [s["lufs"] for _, s in items]
        mm = [s["mmax"] for _, s in items]
        rm = [s["rms_db"] for _, s in items]
        lines.append(f"  {g:11s} {len(items):3d} files  LUFS-I {min(lu):6.1f} .. {max(lu):6.1f}   max momentary "
                     f"{min(mm):6.1f} .. {max(mm):6.1f}   RMS {min(rm):6.1f} .. {max(rm):6.1f} dBFS")
    lines.append("")
    lines.append("Loop seams: the click measure at the wrap point compared with the same measure at 40 interior points")
    lines.append("of the file; a seam ranked above 98% of them with hf > 3 whose HF energy is also above the file's typical")
    lines.append("level is flagged CLICK? (in a silent gap the local ratio only measures codec noise).")
    lines.append("Columns: peak = sample peak dBFS, TP = true peak dBTP (4x oversampled), LUFS = BS.1770 integrated,")
    lines.append("Mmax = max momentary (400 ms) loudness, crest = peak - RMS, cent = spectral centroid Hz,")
    lines.append("rough = RMS amplitude-modulation depth in the 30-150 Hz roughness band (noise ~0.07-0.1,")
    lines.append("clean tones ~0, screams/AM 0.2+).  Loop seam (decoded file, wrap end->start): hf = 2nd-difference")
    lines.append("energy across the seam / median of the windows just before the end and just after the start;")
    lines.append("step = |x[0]-x[-1]| / median local sample step.  ~1 = as smooth as the surrounding material, a click")
    lines.append("would show as >> 3.  binaural = detected carrier pairs (a tone in one ear only, its partner 2-10 Hz")
    lines.append("away in the other ear); faint pairs buried in the wind / tunnel noise are not always detected.")
    lines.append("")
    lines.append("Extra events (not in DESIGN.md section 8): " + (", ".join(EXTRAS) if EXTRAS else "none"))
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump(cache, f)
    with open(os.path.join(HERE, "report.txt"), "w", encoding="utf-8") as f:
        f.write("HITORI sound report (measured on the decoded OGG files)\n\n")
        f.write("\n".join(lines) + "\n")
    return durations, total


def _show(results):
    errors = []
    for path, dt, dur in results:
        if dt < 0:
            errors.append(path)
            print(f"  {path:34s} {dur}", flush=True)
        else:
            print(f"  {path:34s} {dur:6.2f}s  ({dt:.1f}s)", flush=True)
    return errors


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--only", help="glob on file path or event id (e.g. 'ritual/*', 'music.chase')")
    ap.add_argument("--jobs", type=int, default=min(2, os.cpu_count() or 1))
    ap.add_argument("--spec", help="also write spectrogram PNGs of the decoded files to this directory")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    t0 = time.time()
    if not args.report_only:
        todo = jobs(args.only)
        weight = {"music/": 9, "amb/apartment": 7, "amb/outside": 7, "amb/": 4, "voice/": 3, "game/jumpscare": 4,
                  "doll/voice": 2, "woman/": 2}
        todo.sort(key=lambda j: -max([w for k, w in weight.items() if j[1].startswith(k)] + [1]))
        print(f"rendering {len(todo)} files with {args.jobs} worker(s)...")
        if args.jobs > 1:
            with Pool(args.jobs) as pool:
                results = pool.imap_unordered(render, todo)
                errors = _show(results)
        else:
            errors = _show(render(j) for j in todo)
        if errors:
            print(f"{len(errors)} file(s) FAILED: " + ", ".join(errors))
    durations, total = report(args.spec, args.only)
    write_sounds_json(durations)
    write_subtitles()
    print(f"done in {time.time() - t0:.1f}s - {len(durations)} files, {total / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
