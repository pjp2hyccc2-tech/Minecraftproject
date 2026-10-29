#!/usr/bin/env python3
"""Offline GLSL check of the HuntCraft shader pack with glslangValidator.

Expands the Iris-style absolute #include directives, defines the macros the shader loader
provides (MC_VERSION, MC_RENDER_STAGE_*, ...), and compiles every program stub of every
dimension folder in the OpenGL 3.30 compatibility profile.  Iris-specific transformations
(gl_FragData -> outputs, mc_Entity attributes, ...) are all valid compatibility-profile GLSL,
so a clean run here catches the vast majority of errors before the 20-minute client run.

Usage: python3 tools/shader/lint.py [--keep DIR]
Exit code 1 if any program fails to compile (or glslangValidator is missing).
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "shaderpack", "shaders"))

ENV_DEFINES = {
    "MC_VERSION": "12001",
    "MC_GL_VERSION": "450",
    "MC_GLSL_VERSION": "450",
    "MC_OS_LINUX": "",
    "MC_GL_VENDOR_MESA": "",
    "MC_GL_RENDERER_MESA": "",
    "IS_IRIS": "",
    "IS_OCULUS": "",
    "MC_NORMAL_MAP": "",
    "MC_SPECULAR_MAP": "",
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
                if inc.startswith("/"):
                    target = os.path.join(ROOT, inc[1:])
                else:
                    target = os.path.join(os.path.dirname(path), inc)
                out.append("// ---- begin include %s\n" % inc)
                out.extend(expand(os.path.normpath(target), stack + [path]))
                out.append("// ---- end include %s\n" % inc)
            else:
                out.append(line)
    return out


def main():
    keep = None
    if "--keep" in sys.argv:
        keep = sys.argv[sys.argv.index("--keep") + 1]
        os.makedirs(keep, exist_ok=True)
    exe = shutil.which("glslangValidator") or shutil.which("glslang")
    if not exe:
        print("glslangValidator not found (apt-get install glslang-tools)")
        return 1
    tmp = keep or tempfile.mkdtemp(prefix="hcshader_")
    failures = 0
    total = 0
    for base, _dirs, files in os.walk(ROOT):
        if os.path.basename(base) in ("lib", "program", "lang"):
            continue
        for f in sorted(files):
            if not (f.endswith(".vsh") or f.endswith(".fsh")):
                continue
            total += 1
            src = os.path.join(base, f)
            lines = expand(src)
            # inject the loader environment defines right after #version
            header = ["#define %s %s\n" % (k, v) for k, v in ENV_DEFINES.items()]
            if lines and lines[0].startswith("#version"):
                lines = lines[:1] + header + lines[1:]
            rel = os.path.relpath(src, ROOT).replace(os.sep, "_")
            ext = ".vert" if f.endswith(".vsh") else ".frag"
            tmp_file = os.path.join(tmp, rel + ext)
            with open(tmp_file, "w", encoding="utf-8") as fh:
                fh.writelines(lines)
            res = subprocess.run([exe, tmp_file], capture_output=True, text=True)
            if res.returncode != 0:
                failures += 1
                print("==== FAIL %s" % os.path.relpath(src, ROOT))
                msgs = [l for l in (res.stdout + res.stderr).splitlines() if "ERROR" in l or "error" in l.lower()]
                for l in msgs[:12]:
                    print("   ", l)
                    m = re.search(r":(\d+): ", l)
                    if m:
                        ln = int(m.group(1))
                        if 0 < ln <= len(lines):
                            print("       > " + lines[ln - 1].rstrip())
    print("checked %d programs, %d failed" % (total, failures))
    if not keep:
        shutil.rmtree(tmp, ignore_errors=True)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
