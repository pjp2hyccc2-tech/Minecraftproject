"""Compile + link every program of the TabiShaders pack with Mesa (EGL surfaceless, GL 3.3 compatibility profile).

Emulates the parts of the Iris preprocessor the pack relies on: #include "/lib/..." and the MC_* macros.
usage: python3 check_glsl.py [path/to/shaders]"""
import ctypes as C
import os
import re
import sys

os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'TabiShaders', 'shaders')

egl = C.CDLL('libEGL.so.1')
egl.eglGetProcAddress.restype = C.c_void_p
egl.eglGetProcAddress.argtypes = [C.c_char_p]
egl.eglGetDisplay.restype = C.c_void_p
egl.eglCreateContext.restype = C.c_void_p
egl.eglCreateContext.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.POINTER(C.c_int)]
egl.eglInitialize.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_int)]
egl.eglChooseConfig.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_void_p), C.c_int, C.POINTER(C.c_int)]
egl.eglMakeCurrent.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p]

dpy = egl.eglGetDisplay(None)
maj, mnr = C.c_int(), C.c_int()
assert egl.eglInitialize(dpy, C.byref(maj), C.byref(mnr)), 'eglInitialize failed'
assert egl.eglBindAPI(0x30A2)   # EGL_OPENGL_API
attrs = (C.c_int * 5)(0x3040, 0x0008, 0x3033, 0, 0x3038)   # RENDERABLE_TYPE=OPENGL_BIT, SURFACE_TYPE=0
cfg = C.c_void_p()
n = C.c_int()
assert egl.eglChooseConfig(dpy, attrs, C.byref(cfg), 1, C.byref(n)) and n.value, 'no EGL config'
cattrs = (C.c_int * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 0x2, 0x3038)   # 3.3 compatibility profile
ctx = egl.eglCreateContext(dpy, cfg, None, cattrs)
assert ctx, 'context creation failed'
assert egl.eglMakeCurrent(dpy, None, None, ctx), 'make current failed'


def gl(name, res, *args):
    f = C.CFUNCTYPE(res, *args)(egl.eglGetProcAddress(name.encode()))
    return f


glGetString = gl('glGetString', C.c_char_p, C.c_uint)
glCreateShader = gl('glCreateShader', C.c_uint, C.c_uint)
glShaderSource = gl('glShaderSource', None, C.c_uint, C.c_int, C.POINTER(C.c_char_p), C.c_void_p)
glCompileShader = gl('glCompileShader', None, C.c_uint)
glGetShaderiv = gl('glGetShaderiv', None, C.c_uint, C.c_uint, C.POINTER(C.c_int))
glGetShaderInfoLog = gl('glGetShaderInfoLog', None, C.c_uint, C.c_int, C.c_void_p, C.c_char_p)
glCreateProgram = gl('glCreateProgram', C.c_uint)
glAttachShader = gl('glAttachShader', None, C.c_uint, C.c_uint)
glLinkProgram = gl('glLinkProgram', None, C.c_uint)
glGetProgramiv = gl('glGetProgramiv', None, C.c_uint, C.c_uint, C.POINTER(C.c_int))
glGetProgramInfoLog = gl('glGetProgramInfoLog', None, C.c_uint, C.c_int, C.c_void_p, C.c_char_p)
print('GL:', glGetString(0x1F02).decode(), '|', glGetString(0x1F01).decode())

MACROS = '\n'.join(['#define MC_VERSION 12001', '#define MC_GL_VERSION 330', '#define IRIS_VERSION 10700',
                    '#define MC_RENDER_STAGE_SKY 1', '#define MC_RENDER_STAGE_SUNSET 2', '#define MC_RENDER_STAGE_STARS 4',
                    '#define MC_RENDER_STAGE_SUN 5', '#define MC_RENDER_STAGE_MOON 6']) + '\n'


def preprocess(path, seen=None):
    seen = seen if seen is not None else set()
    out = []
    for line in open(path, encoding='utf-8').read().split('\n'):
        m = re.match(r'\s*#include\s+"([^"]+)"', line)
        if m:
            inc = os.path.join(ROOT, m.group(1).lstrip('/'))
            out.append(preprocess(inc, seen))
            continue
        out.append(line)
    src = '\n'.join(out)
    return src


def compile_stage(src, kind):
    lines = src.split('\n')
    assert lines[0].startswith('#version'), 'first line must be #version'
    full = lines[0] + '\n' + MACROS + '\n'.join(lines[1:])
    sh = glCreateShader(kind)
    arr = (C.c_char_p * 1)(full.encode())
    glShaderSource(sh, 1, arr, None)
    glCompileShader(sh)
    ok = C.c_int()
    glGetShaderiv(sh, 0x8B81, C.byref(ok))
    buf = C.create_string_buffer(8192)
    glGetShaderInfoLog(sh, 8192, None, buf)
    return sh, ok.value, buf.value.decode(errors='replace')


fails = 0
progs = sorted({f.rsplit('.', 1)[0] for f in os.listdir(ROOT) if f.endswith(('.vsh', '.fsh'))})
for p in progs:
    shaders = []
    msgs = []
    for ext, kind in (('vsh', 0x8B31), ('fsh', 0x8B30)):
        f = os.path.join(ROOT, f'{p}.{ext}')
        if not os.path.exists(f):
            continue
        sh, ok, log = compile_stage(preprocess(f), kind)
        shaders.append(sh)
        if not ok:
            msgs.append(f'  {ext} COMPILE ERROR:\n' + log)
        elif log.strip():
            msgs.append(f'  {ext} warnings: ' + log.strip()[:300])
    prog = glCreateProgram()
    for s in shaders:
        glAttachShader(prog, s)
    glLinkProgram(prog)
    ok = C.c_int()
    glGetProgramiv(prog, 0x8B82, C.byref(ok))
    if not ok.value:
        buf = C.create_string_buffer(8192)
        glGetProgramInfoLog(prog, 8192, None, buf)
        msgs.append('  LINK ERROR:\n' + buf.value.decode(errors='replace'))
    bad = any('ERROR' in m for m in msgs)
    fails += bad
    print(('FAIL ' if bad else 'ok   ') + p)
    for m in msgs:
        print(m)
print('programs:', len(progs), 'failed:', fails)
sys.exit(1 if fails else 0)
