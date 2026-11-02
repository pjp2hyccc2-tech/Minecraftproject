#!/usr/bin/env python3
"""Offline check of the MeikonShaders pack (adapted from the HuntCraft lint tool).

1. glslangValidator compile of every program stub of every dimension folder: the Iris-style
   absolute #include directives are expanded and the macros the loader provides
   (MC_VERSION, MC_RENDER_STAGE_*, ...) are injected after #version. Compiled once with the
   default options and once with every boolean option flipped (+ other extreme values), the
   way Iris applies options (by rewriting the #define lines).
2. The generated stubs match tools/shader/build_pack.py.
3. Option consistency: every option in lib/settings.glsl has a label in lang/en_us.lang and
   lang/ja_jp.lang and is reachable from shaders.properties; screens / sliders only name
   existing options.

Usage: python3 tools/shader/lint.py [--keep DIR] [--quick]
Exit code 1 on any failure (or if glslangValidator is missing).
"""
import os
import re
import shutil
import subprocess
import sys
sys.dont_write_bytecode = True
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "shaderpack", "shaders"))
sys.path.insert(0, HERE)
import build_pack  # noqa: E402

ENV_DEFINES = {
    "MC_VERSION": "12001",
    "MC_GL_VERSION": "450",
    "MC_GLSL_VERSION": "450",
    "MC_OS_LINUX": "",
    "MC_GL_VENDOR_MESA": "",
    "MC_GL_RENDERER_MESA": "",
    "IS_IRIS": "",
    "IS_OCULUS": "",
    "MC_RENDER_QUALITY": "1.0",
    "MC_SHADOW_QUALITY": "1.0",
    "MC_HAND_DEPTH": "0.125",
    "MC_RENDER_STAGE_NONE": "0",
    "MC_RENDER_STAGE_SKY": "1",
    "MC_RENDER_STAGE_SUNSET": "2",
    "MC_RENDER_STAGE_CUSTOM_SKY": "3",
    "MC_RENDER_STAGE_SUN": "4",
    "MC_RENDER_STAGE_MOON": "5",
    "MC_RENDER_STAGE_STARS": "6",
    "MC_RENDER_STAGE_VOID": "7",
    "MC_RENDER_STAGE_TERRAIN_SOLID": "8",
    "MC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED": "9",
    "MC_RENDER_STAGE_TERRAIN_CUTOUT": "10",
    "MC_RENDER_STAGE_ENTITIES": "11",
    "MC_RENDER_STAGE_BLOCK_ENTITIES": "12",
    "MC_RENDER_STAGE_DESTROY": "13",
    "MC_RENDER_STAGE_OUTLINE": "14",
    "MC_RENDER_STAGE_DEBUG": "15",
    "MC_RENDER_STAGE_HAND_SOLID": "16",
    "MC_RENDER_STAGE_TERRAIN_TRANSLUCENT": "17",
    "MC_RENDER_STAGE_TRIPWIRE": "18",
    "MC_RENDER_STAGE_PARTICLES": "19",
    "MC_RENDER_STAGE_CLOUDS": "20",
    "MC_RENDER_STAGE_RAIN_SNOW": "21",
    "MC_RENDER_STAGE_WORLD_BORDER": "22",
    "MC_RENDER_STAGE_HAND_TRANSLUCENT": "23",
}

INCLUDE_RE = re.compile(r'^\s*#include\s+"([^"]+)"')
BOOL_RE = re.compile(r'^(\s*)(//\s*)?#define\s+([A-Z][A-Z0-9_]*)\s*(//.*)?$')
VALUE_RE = re.compile(r'^\s*#define\s+([A-Z][A-Z0-9_]*)\s+(\S+)\s*//\s*\[([^\]]*)\]')
CONST_RE = re.compile(r'^\s*const\s+(?:int|float)\s+(\w+)\s*=\s*([^;]+);\s*//\s*\[([^\]]*)\]')


def expand(path, stack=None):
    stack = stack or []
    if path in stack:
        raise RuntimeError("include cycle: " + " -> ".join(stack + [path]))
    out = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = INCLUDE_RE.match(line)
            if m:
                inc = m.group(1)
                target = os.path.join(ROOT, inc[1:]) if inc.startswith("/") else os.path.join(os.path.dirname(path), inc)
                out.append("// ---- begin include %s\n" % inc)
                out.extend(expand(os.path.normpath(target), stack + [path]))
                out.append("// ---- end include %s\n" % inc)
            else:
                out.append(line)
    return out


def parse_options():
    """-> (bools {name: default_on}, values {name: (default, [choices])})"""
    bools, values = {}, {}
    with open(os.path.join(ROOT, "lib", "settings.glsl"), encoding="utf-8") as fh:
        for line in fh:
            m = VALUE_RE.match(line)
            if m:
                values[m.group(1)] = (m.group(2), m.group(3).split())
                continue
            m = CONST_RE.match(line)
            if m:
                values[m.group(1)] = (m.group(2).strip(), m.group(3).split())
                continue
            m = BOOL_RE.match(line)
            if m and "[" not in (m.group(4) or ""):
                bools[m.group(3)] = m.group(2) is None
    return bools, values


def apply_variant(lines, bools, values, flip):
    """rewrite option lines like the loader does. flip: invert booleans, pick other values"""
    if not flip:
        return lines
    out = []
    for line in lines:
        m = VALUE_RE.match(line)
        if m and m.group(1) in values:
            default, choices = values[m.group(1)]
            pick = choices[-1] if choices and choices[-1] != default else choices[0]
            line = re.sub(r'(#define\s+%s\s+)\S+' % m.group(1), lambda mm: mm.group(1) + pick, line, count=1)
            out.append(line)
            continue
        m = CONST_RE.match(line)
        if m and m.group(1) in values:
            default, choices = values[m.group(1)]
            pick = choices[0] if choices[0] != default else choices[-1]
            line = re.sub(r'(=\s*)[^;]+;', lambda mm: mm.group(1) + pick + ";", line, count=1)
            out.append(line)
            continue
        m = BOOL_RE.match(line)
        if m and m.group(3) in bools and "[" not in (m.group(4) or ""):
            name = m.group(3)
            on = m.group(2) is None
            out.append(("//#define %s\n" % name) if on else ("#define %s\n" % name))
            continue
        out.append(line)
    return out


def compile_all(exe, tmp, quick):
    bools, values = parse_options()
    failures = 0
    total = 0
    variants = [False] if quick else [False, True]
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in ("lib", "program", "lang")]
        for f in sorted(files):
            if not f.endswith((".vsh", ".fsh")):
                continue
            src = os.path.join(base, f)
            rel = os.path.relpath(src, ROOT)
            raw = expand(src)
            for flip in variants:
                total += 1
                lines = apply_variant(raw, bools, values, flip)
                header = ["#define %s %s\n" % (k, v) for k, v in ENV_DEFINES.items()]
                if lines and lines[0].startswith("#version"):
                    lines = lines[:1] + header + lines[1:]
                name = rel.replace(os.sep, "_") + ("_flipped" if flip else "")
                tmp_file = os.path.join(tmp, name + (".vert" if f.endswith(".vsh") else ".frag"))
                with open(tmp_file, "w", encoding="utf-8") as fh:
                    fh.writelines(lines)
                res = subprocess.run([exe, tmp_file], capture_output=True, text=True)
                if res.returncode != 0:
                    failures += 1
                    print("==== FAIL %s%s" % (rel, " (options flipped)" if flip else ""))
                    msgs = [l for l in (res.stdout + res.stderr).splitlines() if "ERROR" in l or "error" in l.lower()]
                    for l in msgs[:12]:
                        print("   ", l)
                        m = re.search(r":(\d+): ", l)
                        if m:
                            ln = int(m.group(1))
                            if 0 < ln <= len(lines):
                                print("       > " + lines[ln - 1].rstrip())
    print("glslang: checked %d program variants, %d failed" % (total, failures))
    return failures


def read_props(path):
    """key=value with backslash continuations"""
    out = {}
    if not os.path.isfile(path):
        return out
    with open(path, encoding="utf-8") as fh:
        text = fh.read().replace("\\\n", " ")
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def check_consistency():
    problems = []
    bools, values = parse_options()
    options = set(bools) | set(values)
    props = read_props(os.path.join(ROOT, "shaders.properties"))
    langs = {l: read_props(os.path.join(ROOT, "lang", l + ".lang")) for l in ("en_us", "ja_jp")}

    screens = {k: v.split() for k, v in props.items() if k == "screen" or k.startswith("screen.")}
    placed = set()
    for key, entries in screens.items():
        for e in entries:
            if e in ("<empty>", "<profile>", "*"):
                continue
            if e.startswith("[") and e.endswith("]"):
                sub = e[1:-1]
                if "screen." + sub not in screens:
                    problems.append("%s references undefined screen %s" % (key, e))
                for lang, entries_l in langs.items():
                    if "screen." + sub not in entries_l:
                        problems.append("lang/%s.lang: missing screen.%s" % (lang, sub))
                continue
            if e not in options:
                problems.append("%s references unknown option %s" % (key, e))
            placed.add(e)
    for o in sorted(options - placed):
        problems.append("option %s is not on any screen" % o)
    for s in props.get("sliders", "").split():
        if s not in values:
            problems.append("sliders: %s is not a value option" % s)
    for lang, entries in langs.items():
        for o in sorted(options):
            if "option." + o not in entries:
                problems.append("lang/%s.lang: missing option.%s" % (lang, o))
    for k, v in props.items():
        if k.startswith("profile."):
            for tok in v.split():
                name = tok.lstrip("!").split("=")[0]
                if name and name not in options and not name.startswith("profile."):
                    problems.append("%s sets unknown option %s" % (k, name))
    # every boolean option must appear in a plain #ifdef / #ifndef line somewhere: that is what
    # Iris / Oculus / OptiFine scan for ("#if defined X" does not count)
    src = ""
    for base, _d, files in os.walk(ROOT):
        for f in files:
            if f.endswith((".glsl", ".vsh", ".fsh")):
                with open(os.path.join(base, f), encoding="utf-8") as fh:
                    src += fh.read()
    for b in bools:
        if not re.search(r"^\s*#\s*(ifdef|ifndef)\s+%s\b" % b, src, re.M):
            problems.append("boolean option %s is never tested with #ifdef" % b)
    # GLSL and .properties stay pure ASCII (OptiFine and some drivers pass comments through)
    for base, _d, files in os.walk(ROOT):
        for f in files:
            if f.endswith((".glsl", ".vsh", ".fsh", ".properties")):
                path = os.path.join(base, f)
                with open(path, "rb") as fh:
                    for n, line in enumerate(fh, 1):
                        if any(b > 127 for b in line):
                            problems.append("%s:%d: non-ASCII character" % (os.path.relpath(path, ROOT), n))
                            break
    for p in problems:
        print("consistency:", p)
    print("consistency: %s" % ("OK" if not problems else "%d problem(s)" % len(problems)))
    return len(problems)


def main():
    keep = None
    if "--keep" in sys.argv:
        keep = sys.argv[sys.argv.index("--keep") + 1]
        os.makedirs(keep, exist_ok=True)
    exe = shutil.which("glslangValidator") or shutil.which("glslang")
    if not exe:
        print("glslangValidator not found (apt-get install glslang-tools)")
        return 1
    stale = build_pack.check()
    for s in stale:
        print("stubs:", s)
    print("stubs: %s" % ("OK" if not stale else "%d problem(s) - run tools/shader/build_pack.py" % len(stale)))
    tmp = keep or tempfile.mkdtemp(prefix="meikonshader_")
    try:
        failures = compile_all(exe, tmp, "--quick" in sys.argv)
    finally:
        if not keep:
            shutil.rmtree(tmp, ignore_errors=True)
    inconsistent = check_consistency()
    return 1 if (failures or stale or inconsistent) else 0


if __name__ == "__main__":
    sys.exit(main())
