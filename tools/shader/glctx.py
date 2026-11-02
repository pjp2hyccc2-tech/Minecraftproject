"""Minimal headless OpenGL (Mesa, EGL surfaceless, 4.5 compatibility profile) via ctypes.

Used by tools/shader/preview.py to compile, link and run the SounanShaders programs with the
same compiler the CI run uses (Mesa llvmpipe). No window, no X server.
"""
import ctypes as C
import os

os.environ.setdefault("EGL_PLATFORM", "surfaceless")

# ---- constants -----------------------------------------------------------------------
GL_VERTEX_SHADER = 0x8B31
GL_FRAGMENT_SHADER = 0x8B30
GL_COMPILE_STATUS = 0x8B81
GL_LINK_STATUS = 0x8B82
GL_TEXTURE_2D = 0x0DE1
GL_RGBA = 0x1908
GL_RGBA8 = 0x8058
GL_RGBA16F = 0x881A
GL_FLOAT = 0x1406
GL_UNSIGNED_BYTE = 0x1401
GL_DEPTH_COMPONENT = 0x1902
GL_DEPTH_COMPONENT32F = 0x8CAC
GL_TEXTURE_MIN_FILTER = 0x2801
GL_TEXTURE_MAG_FILTER = 0x2800
GL_LINEAR = 0x2601
GL_NEAREST = 0x2600
GL_LINEAR_MIPMAP_LINEAR = 0x2703
GL_TEXTURE_WRAP_S = 0x2802
GL_TEXTURE_WRAP_T = 0x2803
GL_CLAMP_TO_EDGE = 0x812F
GL_REPEAT = 0x2901
GL_TEXTURE_COMPARE_MODE = 0x884C
GL_TEXTURE_COMPARE_FUNC = 0x884D
GL_COMPARE_REF_TO_TEXTURE = 0x884E
GL_LEQUAL = 0x0203
GL_LESS = 0x0201
GL_FRAMEBUFFER = 0x8D40
GL_COLOR_ATTACHMENT0 = 0x8CE0
GL_DEPTH_ATTACHMENT = 0x8D00
GL_FRAMEBUFFER_COMPLETE = 0x8CD5
GL_DEPTH_TEST = 0x0B71
GL_BLEND = 0x0BE2
GL_CULL_FACE = 0x0B44
GL_SRC_ALPHA = 0x0302
GL_ONE_MINUS_SRC_ALPHA = 0x0303
GL_SRC_COLOR = 0x0300
GL_DST_COLOR = 0x0306
GL_ONE = 1
GL_ZERO = 0
GL_COLOR = 0x1800
GL_DEPTH = 0x1801
GL_COLOR_BUFFER_BIT = 0x4000
GL_DEPTH_BUFFER_BIT = 0x0100
GL_TRIANGLES = 0x0004
GL_MODELVIEW = 0x1700
GL_PROJECTION = 0x1701
GL_TEXTURE = 0x1702
GL_VERTEX_ARRAY = 0x8074
GL_NORMAL_ARRAY = 0x8075
GL_COLOR_ARRAY = 0x8076
GL_TEXTURE_COORD_ARRAY = 0x8078
GL_TEXTURE0 = 0x84C0
GL_NONE = 0
GL_PACK_ALIGNMENT = 0x0D05
GL_UNPACK_ALIGNMENT = 0x0CF5
GL_VENDOR = 0x1F00
GL_RENDERER = 0x1F01
GL_VERSION = 0x1F02

_egl = None
_ctx = None
_funcs = {}

P = C.c_void_p
I = C.c_int
U = C.c_uint
F = C.c_float

SIGS = {
    "glGetString": (C.c_char_p, [U]),
    "glGetError": (U, []),
    "glCreateShader": (U, [U]),
    "glShaderSource": (None, [U, I, C.POINTER(C.c_char_p), P]),
    "glCompileShader": (None, [U]),
    "glGetShaderiv": (None, [U, U, C.POINTER(I)]),
    "glGetShaderInfoLog": (None, [U, I, P, C.c_char_p]),
    "glDeleteShader": (None, [U]),
    "glCreateProgram": (U, []),
    "glAttachShader": (None, [U, U]),
    "glBindAttribLocation": (None, [U, U, C.c_char_p]),
    "glLinkProgram": (None, [U]),
    "glGetProgramiv": (None, [U, U, C.POINTER(I)]),
    "glGetProgramInfoLog": (None, [U, I, P, C.c_char_p]),
    "glDeleteProgram": (None, [U]),
    "glUseProgram": (None, [U]),
    "glGetUniformLocation": (I, [U, C.c_char_p]),
    "glGetAttribLocation": (I, [U, C.c_char_p]),
    "glUniform1i": (None, [I, I]),
    "glUniform1f": (None, [I, F]),
    "glUniform2i": (None, [I, I, I]),
    "glUniform3f": (None, [I, F, F, F]),
    "glUniform4f": (None, [I, F, F, F, F]),
    "glUniformMatrix4fv": (None, [I, I, U, C.POINTER(F)]),
    "glVertexAttribPointer": (None, [U, I, U, U, I, P]),
    "glEnableVertexAttribArray": (None, [U]),
    "glDisableVertexAttribArray": (None, [U]),
    "glVertexAttrib4f": (None, [U, F, F, F, F]),
    "glGenTextures": (None, [I, C.POINTER(U)]),
    "glBindTexture": (None, [U, U]),
    "glTexImage2D": (None, [U, I, I, I, I, I, U, U, P]),
    "glTexParameteri": (None, [U, U, I]),
    "glGenerateMipmap": (None, [U]),
    "glActiveTexture": (None, [U]),
    "glGenFramebuffers": (None, [I, C.POINTER(U)]),
    "glBindFramebuffer": (None, [U, U]),
    "glFramebufferTexture2D": (None, [U, U, U, U, I]),
    "glCheckFramebufferStatus": (U, [U]),
    "glDrawBuffers": (None, [I, C.POINTER(U)]),
    "glDrawBuffer": (None, [U]),
    "glReadBuffer": (None, [U]),
    "glReadPixels": (None, [I, I, I, I, U, U, P]),
    "glGetTexImage": (None, [U, I, U, U, P]),
    "glViewport": (None, [I, I, I, I]),
    "glClearColor": (None, [F, F, F, F]),
    "glClear": (None, [U]),
    "glClearBufferfv": (None, [U, I, C.POINTER(F)]),
    "glEnable": (None, [U]),
    "glDisable": (None, [U]),
    "glEnablei": (None, [U, U]),
    "glDisablei": (None, [U, U]),
    "glBlendFunc": (None, [U, U]),
    "glBlendFuncSeparate": (None, [U, U, U, U]),
    "glDepthMask": (None, [U]),
    "glDepthFunc": (None, [U]),
    "glMatrixMode": (None, [U]),
    "glLoadMatrixf": (None, [C.POINTER(F)]),
    "glLoadIdentity": (None, []),
    "glClientActiveTexture": (None, [U]),
    "glEnableClientState": (None, [U]),
    "glDisableClientState": (None, [U]),
    "glVertexPointer": (None, [I, U, I, P]),
    "glColorPointer": (None, [I, U, I, P]),
    "glNormalPointer": (None, [U, I, P]),
    "glTexCoordPointer": (None, [I, U, I, P]),
    "glDrawArrays": (None, [U, I, I]),
    "glFinish": (None, []),
    "glPixelStorei": (None, [U, I]),
}


class _GL:
    def __getattr__(self, name):
        if name not in _funcs:
            res, args = SIGS[name]
            addr = _egl.eglGetProcAddress(name.encode())
            if not addr:
                raise RuntimeError("GL function not found: " + name)
            _funcs[name] = C.CFUNCTYPE(res, *args)(addr)
        return _funcs[name]


gl = _GL()


def init():
    """create a surfaceless 4.5 compatibility context; returns (version, renderer)"""
    global _egl, _ctx
    if _ctx:
        return
    _egl = C.CDLL("libEGL.so.1")
    _egl.eglGetProcAddress.restype = C.c_void_p
    _egl.eglGetProcAddress.argtypes = [C.c_char_p]
    _egl.eglGetDisplay.restype = C.c_void_p
    _egl.eglCreateContext.restype = C.c_void_p
    _egl.eglCreateContext.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.POINTER(C.c_int)]
    _egl.eglInitialize.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_int)]
    _egl.eglChooseConfig.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_void_p), C.c_int, C.POINTER(C.c_int)]
    _egl.eglMakeCurrent.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p]
    dpy = _egl.eglGetDisplay(None)
    maj, mnr = C.c_int(), C.c_int()
    if not _egl.eglInitialize(dpy, C.byref(maj), C.byref(mnr)):
        raise RuntimeError("eglInitialize failed")
    if not _egl.eglBindAPI(0x30A2):  # EGL_OPENGL_API
        raise RuntimeError("eglBindAPI failed")
    attrs = (C.c_int * 5)(0x3040, 0x0008, 0x3033, 0, 0x3038)
    cfg = C.c_void_p()
    n = C.c_int()
    if not _egl.eglChooseConfig(dpy, attrs, C.byref(cfg), 1, C.byref(n)) or not n.value:
        raise RuntimeError("no EGL config")
    cattrs = (C.c_int * 7)(0x3098, 4, 0x30FB, 5, 0x30FD, 0x2, 0x3038)  # 4.5 compatibility
    ctx = _egl.eglCreateContext(dpy, cfg, None, cattrs)
    if not ctx:
        cattrs = (C.c_int * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 0x2, 0x3038)
        ctx = _egl.eglCreateContext(dpy, cfg, None, cattrs)
    if not ctx or not _egl.eglMakeCurrent(dpy, None, None, ctx):
        raise RuntimeError("could not create / bind a GL context")
    _ctx = ctx
    return gl.glGetString(GL_VERSION).decode(), gl.glGetString(GL_RENDERER).decode()


def compile_shader(src, kind):
    sh = gl.glCreateShader(kind)
    arr = (C.c_char_p * 1)(src.encode("utf-8"))
    gl.glShaderSource(sh, 1, arr, None)
    gl.glCompileShader(sh)
    ok = C.c_int()
    gl.glGetShaderiv(sh, GL_COMPILE_STATUS, C.byref(ok))
    buf = C.create_string_buffer(16384)
    gl.glGetShaderInfoLog(sh, 16384, None, buf)
    return sh, bool(ok.value), buf.value.decode(errors="replace")


def link_program(vs, fs, attribs=None):
    prog = gl.glCreateProgram()
    gl.glAttachShader(prog, vs)
    gl.glAttachShader(prog, fs)
    for name, loc in (attribs or {}).items():
        gl.glBindAttribLocation(prog, loc, name.encode())
    gl.glLinkProgram(prog)
    ok = C.c_int()
    gl.glGetProgramiv(prog, GL_LINK_STATUS, C.byref(ok))
    buf = C.create_string_buffer(16384)
    gl.glGetProgramInfoLog(prog, 16384, None, buf)
    return prog, bool(ok.value), buf.value.decode(errors="replace")


def fptr(arr):
    return arr.ctypes.data_as(C.POINTER(C.c_float))


def gen_texture():
    t = C.c_uint()
    gl.glGenTextures(1, C.byref(t))
    return t.value


def gen_framebuffer():
    f = C.c_uint()
    gl.glGenFramebuffers(1, C.byref(f))
    return f.value
