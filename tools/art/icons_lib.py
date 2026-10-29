"""Icon archetypes (16x16) built on pix.Canvas. Each function draws onto a fresh canvas and returns it.
Palette dicts carry 5-colour ramps: main, dark, acc, tip (+ 'glow' hex, 'gem' ramp...)."""
import math

import numpy as np

from pix import Canvas, ramp, hx, mix, to_hex, lighten_ramp

GOLD = ramp('#d9a630', sat=1.1)
IVORY = ['#4a3f2e', '#a8977a', '#d2c4a4', '#ece2c8', '#fffaf0']
IVORY_DK = ['#3a3024', '#857456', '#a8977a', '#c9bb9c', '#e6dcc4']
BONE = ['#4a4033', '#a4957a', '#cfc2a4', '#e6dcc2', '#fbf6ea']
WOOD = ramp('#8a5a2e')
LEATHER = ramp('#7b4f2e')
IRON = ['#1e2126', '#4f5761', '#76808b', '#a2acb7', '#dce3ea']
GLASS = ['#2a3640', '#7f98a8', '#b8d0dc', '#e2f0f6', '#ffffff']
CORK = ramp('#a07040')
WHITE = '#ffffff'


def rot(pts, ang, cx, cy):
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def shield_pts(cx, cy, w, h, ang=0, point=1.0):
    """Scale / shield outline: rounded top, pointed bottom."""
    pts = []
    for k in range(25):
        t = math.pi + k / 24 * math.pi  # 180..360 deg: left over the top to the right
        pts.append((cx + math.cos(t) * w / 2, cy - h * 0.12 + math.sin(t) * h * 0.42))
    pts.append((cx + w * 0.38, cy + h * 0.12))
    pts.append((cx, cy + h * 0.5 * point))
    pts.append((cx - w * 0.38, cy + h * 0.12))
    return rot(pts, ang, cx, cy)


def star_pts(cx, cy, r0, r1, n=5, ang=-90):
    pts = []
    for k in range(n * 2):
        r = r0 if k % 2 == 0 else r1
        a = math.radians(ang + k * 180.0 / n)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def plus_mark(c, x=13, y=2, col='#fff4b0', arm='#f6c84a'):
    """Gold sparkle marking high-rank (+) materials."""
    c.px(x, y, col)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        c.px(x + dx, y + dy, arm)


def plus_trim(c, rp_gold=None):
    """Golden rim on the top-left silhouette edge (upgraded look)."""
    g = rp_gold or GOLD
    a = c.img[..., 3] > 0
    n = c.n
    for y in range(n):
        for x in range(n):
            if not a[y, x]:
                continue
            up = y == 0 or not a[y - 1, x]
            lf = x == 0 or not a[y, x - 1]
            if up or lf:
                c.img[y, x, :3] = hx(g[3] if (up and lf) else g[2])


# ----------------------------------------------------------------------------- monster material archetypes
def bez(p0, p1, p2, steps=14):
    ts = np.linspace(0, 1, steps)
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in ts]


def crescent(c, base_out, ctrl_out, tip, ctrl_in, base_in):
    """Filled crescent (claw / horn / tusk) between an outer and an inner quadratic curve."""
    pts = bez(base_out, ctrl_out, tip) + bez(tip, ctrl_in, base_in)[1:]
    return c.poly(pts)


def scale(P, variant=0):
    c = Canvas()
    pts = shield_pts(8.0, 8.2, 12.4, 15.2, 12, point=0.95)
    front = c.poly(pts)
    c.layer(front, P['main'], bulge=1.3)
    inner = c.cells(c.poly(shield_pts(8.6, 8.8, 7.6, 10.0, 12, point=0.9)), 0.6)
    c.shift_level(inner & (c.img[..., 3] > 0) & (c.last_lev > 0), P['main'], -1)
    a = math.radians(12)
    for k in range(-5, 6):
        x = 8.0 - math.sin(a) * k * 1.0
        y = 8.2 + math.cos(a) * k * 1.0 - 0.6
        c.px_on(int(round(x - 0.4)), int(round(y)), P['main'][4] if k < 1 else P['main'][3])
        c.px_on(int(round(x + 0.8)), int(round(y)), P['main'][1])
    c.px_on(4, 4, P['main'][4])
    c.px_on(5, 3, P['main'][4])
    return c

def fur(P):
    c = Canvas()
    back = [((4.5, 15.4), (1.6, 9), (1.0, 2.4), 5.2), ((11.5, 15.4), (14.4, 9), (15.0, 2.8), 5.2)]
    front = [((6.4, 15.6), (4.4, 8), (6.0, 0.8), 6.0), ((9.6, 15.6), (11.8, 8), (10.6, 1.0), 6.0), ((8.0, 15.6), (8.4, 9), (8.6, 3.0), 5.0)]
    for (p0, p1, p2, w) in back:
        c.layer(c.curve(p0, p1, p2, w, 1.4), P['dark'], shade='cyl', outline='auto')
    for k, (p0, p1, p2, w) in enumerate(front):
        c.layer(c.curve(p0, p1, p2, w, 1.4), P['main'] if k < 2 else P['acc'], shade='cyl', outline='auto' if k < 2 else 'soft')
    return c

def rock_plate(P):
    c = Canvas()
    pts = [(1.5, 7), (3.5, 2.5), (9, 1.5), (14, 3.5), (14.8, 9), (12.5, 14), (6, 14.8), (2, 12)]
    hm = c.poly(pts)
    c.layer(hm, P['main'], shade='flat', bulge=1.4)
    for (x0, y0, x1, y1) in ((5, 3, 7, 8), (7, 8, 6, 14), (7, 8, 13, 7), (7, 8, 2, 10), (10, 3, 11, 7)):
        c.line(x0, y0, x1, y1, P['main'][1], only_on=True)
    c.pxs([(4, 5), (9, 4), (12, 9), (4, 11), (9, 11)], P['main'][4])
    for x, y in ((3, 3), (4, 2), (5, 2), (9, 2), (10, 2), (12, 3), (13, 3)):
        c.px_on(x, y, P['acc'][3])
    for x, y in ((4, 3), (10, 3)):
        c.px_on(x, y, P['acc'][2])
    return c


def shell(P):
    c = Canvas()
    pts = [(1.0, 9.0), (2.6, 4.0), (6.0, 1.4), (11.0, 1.8), (14.6, 5.0), (15.0, 10.0), (12.0, 13.6), (7.0, 15.0), (3.0, 13.4)]
    hm = c.poly(pts)
    c.layer(hm, P['main'], shade='flat', bulge=1.3)
    # layered plates: two darker seams following the curve + bright ridge
    for k, off in enumerate((4.6, 9.4)):
        for x in range(1, 16):
            y = off + 0.18 * (x - 8) + 1.4 * math.sin((x - 1) / 14 * math.pi)
            c.px_on(x, int(round(y)), P['main'][1])
            c.px_on(x, int(round(y)) - 1, P['main'][3])
    c.line(3, 4, 12, 2, P['main'][4], only_on=True)
    for (bx, by, tx, ty) in ((5.0, 3.0, 3.6, 0.2), (9.0, 2.0, 9.6, -0.6), (13.0, 3.6, 15.4, 1.2)):
        c.layer(c.stroke([(bx, by), (tx, ty + 0.8)], 2.6, 0.6), P['tip'], shade='cyl')
    return c

def hide(P, stripes=None, furry=False):
    c = Canvas()
    body = c.rect(3.2, 3.6, 12.8, 12.6, rad=2.6)
    legs = c.stroke([(4, 4.5), (1.4, 1.6)], 3.4, 2.2) | c.stroke([(12, 4.5), (14.6, 1.6)], 3.4, 2.2) | \
        c.stroke([(4, 11.5), (1.4, 14.6)], 3.4, 2.2) | c.stroke([(12, 11.5), (14.6, 14.6)], 3.4, 2.2)
    neck = c.stroke([(8, 4.5), (8, 1.2)], 3.8, 2.6) | c.stroke([(8, 12), (8, 15)], 2.4, 1.0)
    hm = body | legs | neck
    c.layer(hm, P['main'], shade='flat', bulge=1.0)
    inner = c.cells(c.rect(5.2, 5.6, 10.8, 10.6, rad=1.6))
    c.shift_level(inner & (c.img[..., 3] > 0), P['main'], -1)
    if stripes:
        for k in (0, 1, 2):
            y = 4.8 + k * 3.0
            c.line(4, y, 12, y + 1, stripes[2], only_on=True)
            c.line(5, y + 1, 11, y + 2, stripes[1], only_on=True)
    if furry:
        for x in range(3, 14, 2):
            c.px_on(x, 3 + (x % 4 == 1), P['main'][4])
    c.px_on(5, 5, P['main'][4])
    return c


def claw(P, curl=1.0):
    c = Canvas()
    hm = crescent(c, (2.0, 14.6), (1.0, 2.0), (14.6, 2.2), (6.2, 5.4), (8.4, 14.6))
    c.layer(hm, P['tip'], shade='cyl', bulge=1.1)
    base = c.rect(1.5, 11.6, 9.0, 15.0, rad=1.2)
    c.layer(base, P['dark'], shade='flat')
    for x, y in ((4, 6), (5, 5), (6, 4)):
        c.px_on(x, y, P['tip'][4])
    return c


def fang(P, ice=None):
    c = Canvas()
    hm = crescent(c, (3.0, 3.2), (2.4, 12.0), (12.6, 14.6), (7.0, 9.6), (9.6, 3.2))
    c.layer(hm, P['tip'], shade='cyl', bulge=1.1)
    root = c.rect(2.0, 1.0, 10.6, 4.4, rad=1.2)
    c.layer(root, P['dark'], shade='flat')
    for x, y in ((4, 6), (4, 7), (5, 9)):
        c.px_on(x, y, P['tip'][4])
    if ice:
        for x, y in ((11, 13), (10, 12), (10, 13), (9, 12), (12, 14)):
            c.px_on(x, y, ice[3])
        c.px_on(11, 14, ice[4])
    return c


def tusk(P):
    c = Canvas()
    hm = crescent(c, (1.2, 13.6), (2.6, 1.4), (15.0, 1.4), (6.0, 6.2), (6.4, 15.0))
    c.layer(hm, P['tip'], shade='cyl', bulge=1.1)
    band = c.poly([(0.8, 12.4), (5.8, 11.0), (7.0, 15.4), (1.2, 15.4)])
    c.layer(band, P['dark'], shade='flat')
    for x, y in ((3, 8), (4, 6), (6, 4)):
        c.px_on(x, y, P['tip'][4])
    return c


def horn(P, glow=None):
    c = Canvas()
    hm = crescent(c, (1.4, 15.0), (1.6, 2.0), (14.8, 1.6), (6.2, 6.8), (7.6, 15.0))
    c.layer(hm, P['tip'], shade='cyl', bulge=1.1)
    for k, (x, y) in enumerate(((2, 11), (3, 8), (5, 5), (8, 3))):
        c.line(x, y, x + 3, y + 2 - k * 0.3, P['tip'][1], only_on=True)
    if glow:
        for x, y in ((13, 2), (12, 2), (14, 2), (12, 3)):
            c.px_on(x, y, glow[3])
        c.px_on(14, 1, glow[4])
        for x, y in ((3, 10), (4, 7), (6, 5), (9, 3)):
            c.px_on(x, y, glow[2])
    return c


def membrane(P):
    c = Canvas()
    joint = (2.6, 2.6)
    tips = [(15.0, 3.4), (14.0, 10.4), (9.4, 15.0), (2.6, 15.0)]
    mem = c.poly([joint] + tips)
    for k in range(3):
        a, b = tips[k], tips[k + 1]
        mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        mem &= ~c.circle(mx + (mx - joint[0]) * 0.16, my + (my - joint[1]) * 0.16, 2.2)
    c.layer(mem, P['acc'], shade='flat', bulge=0.7)
    for t in tips:
        c.layer(c.stroke([joint, t], 1.8, 0.9), P['dark'], shade='cyl', outline='soft')
    c.layer(c.circle(joint[0], joint[1], 2.2), P['tip'], shade='dome')
    return c


def tail(P, tip='spike'):
    c = Canvas()
    body = c.curve((1.2, 15.0), (3.0, 7.0), (10.6, 5.6), 8.0, 3.4, steps=20)
    c.layer(body, P['main'], shade='cyl', bulge=1.3)
    for t in (0.25, 0.45, 0.65):
        x = (1 - t) ** 2 * 1.2 + 2 * (1 - t) * t * 3.0 + t * t * 10.6
        y = (1 - t) ** 2 * 15.0 + 2 * (1 - t) * t * 7.0 + t * t * 5.6
        dx, dy = 2 * (1 - t) * (3.0 - 1.2) + 2 * t * (10.6 - 3.0), 2 * (1 - t) * (7.0 - 15.0) + 2 * t * (5.6 - 7.0)
        nl = math.hypot(dx, dy)
        nx, ny = -dy / nl, dx / nl
        w = 3.6 - t * 2.0
        c.line(x - nx * w, y - ny * w, x + nx * w, y + ny * w, P['main'][1], only_on=True)
    g = P['glow'] if 'glow' in P else P['tip'][4]
    if tip == 'spike':
        hm = c.poly([(8.8, 3.4), (15.6, 0.4), (13.2, 5.6), (15.8, 8.0), (10.2, 8.6)])
        c.layer(hm, P['tip'], shade='flat')
        c.px_on(14, 2, g)
        c.px_on(14, 7, g)
    elif tip == 'fluff':
        for (p0, p1, p2) in (((9.4, 5.8), (13.4, 5.4), (15.6, 1.4)), ((9.0, 5.4), (11.2, 2.4), (12.6, 0.4)), ((9.8, 6.4), (14.4, 8.2), (15.8, 5.4))):
            c.layer(c.curve(p0, p1, p2, 4.4, 1.2), P['acc'], shade='cyl')
    elif tip == 'fin':
        hm = c.poly([(8.6, 4.4), (15.8, 0.4), (14.0, 5.8), (15.8, 10.4), (9.6, 7.6)])
        c.layer(hm, P['acc'], shade='flat')
        c.line(10, 5, 15, 1, P['acc'][4], only_on=True)
    elif tip == 'club':
        c.layer(c.ellipse(12.0, 5.0, 3.9, 3.7), P['tip'], shade='dome')
        c.pxs([(11, 3), (13, 6), (14, 4)], P['tip'][1])
    elif tip == 'blade':
        hm = c.poly([(8.6, 4.0), (15.8, 0.2), (12.4, 8.0)])
        c.layer(hm, P['tip'], shade='flat')
        c.line(10, 5, 15, 1, g, only_on=True)
    return c

def gem_faceted(P):
    c = Canvas()
    G = P['gem']
    outline = [(1.6, 6.0), (4.6, 2.0), (11.4, 2.0), (14.4, 6.0), (8.0, 15.0)]
    hm = c.poly(outline)
    c.layer(hm, G, shade='flat', bulge=0.3, flat=0.5)
    inside = c.img[..., 3] > 0
    edge = c.last_lev == 0
    core = inside & ~edge
    table = c.cells(c.poly([(5.2, 2.6), (10.8, 2.6), (12.4, 5.6), (3.6, 5.6)]), 0.5) & core
    left = c.cells(c.poly([(2.4, 6.2), (8, 6.2), (8, 14.4)]), 0.5) & core
    right = c.cells(c.poly([(8, 6.2), (13.6, 6.2), (8, 14.4)]), 0.5) & core
    c.recolor(right, G[1])
    c.recolor(left, G[3])
    c.recolor(table, G[3])
    c.recolor(c.cells(c.poly([(6.2, 3.0), (9.8, 3.0), (10.8, 5.2), (5.2, 5.2)]), 0.5) & core, G[4])
    for x in range(2, 15):
        c.px_on(x, 6, G[2]) if core[6, x] else None
    c.line(8, 6, 8, 13, G[2], only_on=True)
    c.px(5, 3, WHITE)
    c.px(4, 7, G[4])
    return c


def gem_orb(P, swirl=None, motif=None):
    c = Canvas()
    G = P['gem']
    hm = c.circle(8, 8, 6.9)
    c.layer(hm, G, shade='dome', bulge=1.4)
    inner = c.cells(c.circle(8.6, 8.8, 3.4), 0.5)
    c.shift_level(inner & (c.img[..., 3] > 0), G, 1)
    if motif == 'bolt':
        pts = [(10, 3), (7, 7), (9, 8), (6, 13)]
        for a, b in zip(pts, pts[1:]):
            c.line(a[0], a[1], b[0], b[1], WHITE, only_on=True)
    elif motif == 'flame':
        fl = c.cells(c.poly([(8.5, 4.6), (10.8, 8.4), (10.2, 11.4), (8.4, 12.4), (6.6, 11.2), (6.4, 8.6), (7.6, 9.4)]), 0.5)
        c.recolor(fl & (c.img[..., 3] > 0), G[1])
        c.recolor(c.cells(c.poly([(8.6, 7.6), (9.6, 9.8), (8.6, 11.4), (7.6, 10.4)]), 0.5), G[4])
    elif motif == 'core':
        c.recolor(c.cells(c.circle(8.8, 9.0, 1.6), 0.5), G[4])
    c.px(4, 4, WHITE)
    c.px(5, 4, G[4])
    c.px(4, 5, G[4])
    c.px(5, 5, G[4])
    return c

def crystal_cluster(P, n=3):
    c = Canvas()
    G = P['gem']
    shards = [((8, 15), (8, 0.6), 5.4), ((5.4, 15), (1.4, 4.6), 4.0), ((10.6, 15), (14.6, 5.6), 4.0)][:n]
    for (b, t, w) in shards[::-1]:
        hm = c.stroke([b, t], w, 0.5)
        c.layer(hm, G, shade='flat', bulge=0.5)
        c.line(b[0], b[1] - 1, t[0], t[1] + 1, G[4], only_on=True)
    return c


def electrode(P):
    c = Canvas()
    body = c.poly(rot([(2.4, 5.0), (13.6, 5.0), (13.6, 11.0), (2.4, 11.0)], -40, 8, 8))
    cap1 = c.poly(rot([(0.8, 4.2), (4.0, 4.2), (4.0, 11.8), (0.8, 11.8)], -40, 8, 8))
    cap2 = c.poly(rot([(12.0, 4.2), (15.2, 4.2), (15.2, 11.8), (12.0, 11.8)], -40, 8, 8))
    c.layer(body, P['acc'], shade='cyl')
    c.layer(cap1 | cap2, P['main'], shade='cyl')
    core = c.poly(rot([(5.0, 6.8), (11.0, 6.8), (11.0, 9.2), (5.0, 9.2)], -40, 8, 8))
    c.layer(core, ramp(P['glow'], light=1.1), shade='flat', outline=None)
    for x, y in ((13, 2), (14, 3), (2, 13), (1, 12)):
        c.px(x, y, P['glow'])
    return c


def skull_plate(P):
    c = Canvas()
    dome = c.ellipse(8, 8.5, 6.6, 6.0)
    jaw = c.rect(4, 9, 12, 14, rad=1.2)
    c.layer(dome | jaw, P['main'], shade='dome')
    eye = c.ellipse(5.6, 9.0, 1.4, 1.2) | c.ellipse(10.4, 9.0, 1.4, 1.2)
    c.recolor(c.cells(eye, 0.4), P['main'][0])
    c.pxs([(7, 12), (9, 12)], P['main'][0])
    for x in range(4, 12, 2):
        c.px_on(x, 14, P['main'][1])
    brow = c.stroke([(3.5, 6.5), (8, 5.0), (12.5, 6.5)], 1.6)
    c.layer(brow, P['acc'], shade='cyl', outline='soft')
    return c


def bone_icon(size='m'):
    c = Canvas()
    if size == 's':
        a, b, w, r = (5, 11), (11, 5), 2.2, 1.6
    elif size == 'm':
        a, b, w, r = (4, 12), (12, 4), 2.8, 2.0
    else:
        a, b, w, r = (3, 13), (13, 3), 3.6, 2.4
    shaft = c.stroke([a, b], w)
    d = r * 0.75
    knobs = c.circle(a[0] - d, a[1], r) | c.circle(a[0], a[1] + d, r) | c.circle(b[0] + d, b[1], r) | c.circle(b[0], b[1] - d, r)
    c.layer(shaft | knobs, BONE, shade='cyl')
    return c


def sac(P, inner):
    c = Canvas()
    body = c.ellipse(8, 9.2, 5.6, 5.4)
    neck = c.rect(6.4, 2.4, 9.6, 5.5, rad=1.2)
    c.layer(body | neck, P['main'], shade='dome')
    knot = c.rect(5.6, 4.2, 10.4, 5.6, rad=0.6)
    c.layer(knot, P['dark'], shade='flat')
    core = c.ellipse(8.4, 10.0, 2.6, 2.4)
    c.layer(core, inner, shade='dome', outline=None)
    c.px(6, 7, P['main'][4])
    return c


def meat(rp_meat, rp_fat, bone=True, char=False):
    c = Canvas()
    if bone:
        bn = c.stroke([(2.5, 13.5), (13.5, 2.5)], 2.0)
        knob = c.circle(1.9, 12.6, 1.5) | c.circle(3.4, 14.1, 1.5) | c.circle(12.6, 1.9, 1.5) | c.circle(14.1, 3.4, 1.5)
        c.layer(bn | knob, BONE, shade='cyl')
    m = c.ellipse(8, 8, 5.4, 4.0, -45)
    c.layer(m, rp_meat, shade='dome', bulge=1.2)
    for k in range(-2, 3):
        c.px_on(7 + k, 8 - k + 1, rp_fat[3] if not char else rp_meat[1])
    c.px_on(6, 6, rp_meat[4])
    return c


def flask(liquid, kind='round', cork=True, label=None):
    c = Canvas()
    if kind == 'round':
        glass = c.circle(8, 10, 5.3) | c.rect(6.3, 2.5, 9.7, 7, rad=0.6)
        liq = c.circle(8, 10, 4.2) & (c.Y > 7.6)
    elif kind == 'tall':
        glass = c.rect(4.2, 6, 11.8, 14.5, rad=2.0) | c.rect(6.4, 2.5, 9.6, 6.5, rad=0.6)
        liq = c.rect(5.2, 8.0, 10.8, 13.6, rad=1.4)
    elif kind == 'vial':
        glass = c.rect(5.5, 3.5, 10.5, 14.5, rad=2.2)
        liq = c.rect(6.4, 7.0, 9.6, 13.6, rad=1.5)
    elif kind == 'gourd':
        glass = c.circle(8, 11, 4.4) | c.circle(8, 6.0, 2.6) | c.rect(7, 2, 9, 4.5)
        liq = (c.circle(8, 11, 3.4) | c.circle(8, 6.4, 1.6)) & (c.Y > 5.6)
    else:  # bottle
        glass = c.rect(4.8, 6.5, 11.2, 14.6, rad=1.2) | c.poly([(4.8, 7.2), (6.8, 4.8), (9.2, 4.8), (11.2, 7.2)]) | c.rect(6.8, 2.2, 9.2, 5.2)
        liq = c.rect(5.6, 8.0, 10.4, 13.8, rad=0.8)
    c.layer(glass, GLASS, shade='dome', bulge=0.6, flat=0.35)
    c.layer(liq, liquid, shade='dome', outline=None)
    if label:
        lb = c.rect(5.5, 10.2, 10.5, 12.2)
        c.layer(lb, label, shade='flat', outline=None)
    if cork:
        top = {'round': (6.0, 1.0, 10.0, 3.6), 'tall': (6.0, 1.0, 10.0, 3.6), 'vial': (6.0, 1.8, 10.0, 4.2),
               'gourd': (6.5, 0.8, 9.5, 3.0), 'bottle': (6.4, 0.8, 9.6, 3.2)}[kind]
        ck = c.rect(*top, rad=0.6)
        c.layer(ck, CORK, shade='flat')
    # glass glint
    for x, y in {'round': ((5, 9), (5, 10)), 'tall': ((5, 8), (5, 9), (5, 10)), 'vial': ((6, 5), (6, 6)),
                 'gourd': ((6, 9), (6, 10)), 'bottle': ((5, 8), (5, 9))}[kind]:
        c.px_on(x, y, WHITE)
    return c


def ball(rp, mark=None, rays=None, flies=False):
    c = Canvas()
    hm = c.circle(8, 8.5, 5.6)
    c.layer(hm, rp, shade='dome', bulge=1.2)
    band = c.cells(c.rect(2, 8.0, 14, 9.0))
    c.recolor(band & (c.img[..., 3] > 0) & ~c.cells(c.circle(8, 8.5, 4.6), 0.99), rp[1])
    if mark:
        for (x, y) in mark[1]:
            c.px_on(x, y, mark[0])
    if rays:
        for (x, y) in ((8, 0), (1, 2), (15, 2), (0, 9), (15, 9), (3, 15), (13, 15)):
            c.px(x, y, rays)
    if flies:
        for (x, y) in ((2, 2), (13, 1), (14, 4)):
            c.px(x, y, '#20201a')
        for (x, y) in ((4, 1), (11, 0), (12, 2)):
            c.px(x, y, '#b8b48a')
    c.px(5, 5, WHITE)
    return c


# ----------------------------------------------------------------------------- gathering / nature
def leaves(rp, stem=None, n=3, broad=1.0, tint=None):
    c = Canvas()
    st = stem or ramp('#4a6a20')
    c.layer(c.curve((8, 15.2), (8.4, 11), (7.6, 7), 1.6, 1.0), st, shade='cyl')
    lv = [((8, 9), (2.0, 6.0), (1.4, 1.6), -1), ((8, 8), (14.0, 5.4), (14.6, 1.4), 1), ((8, 11), (8.5, 4.0), (8.0, 0.6), 0)][:n]
    for (b, m, t, side) in lv:
        pts = bez(b, (m[0] - 2.6 * broad * (1 if side >= 0 else -1) * 0.4, m[1] + 2.2 * broad), t) + \
            bez(t, (m[0] + 2.6 * broad * (1 if side >= 0 else -1) * 0.6, m[1] - 1.6 * broad), b)[1:]
        hm = c.poly(pts)
        c.layer(hm, rp, shade='dome', bulge=1.0)
        c.line(b[0], b[1], (b[0] + t[0]) / 2, (b[1] + t[1]) / 2, rp[1], only_on=True)
    if tint:
        for (x, y) in tint[1]:
            c.px_on(x, y, tint[0])
    return c


def mushroom(cap, stem=None, spots=None):
    c = Canvas()
    st = stem or ['#4a4033', '#b8a88a', '#ddd0b4', '#f0e8d6', '#ffffff']
    c.layer(c.rect(6.2, 7.5, 9.8, 15.0, rad=1.4), st, shade='cyl')
    capm = c.ellipse(8, 7.6, 7.0, 5.6) & (c.Y < 9.4)
    c.layer(capm, cap, shade='dome', bulge=1.2)
    for x in range(2, 15):
        c.px_on(x, 9, cap[1])
    if spots:
        for (x, y) in ((5, 5), (9, 4), (11, 7), (7, 7), (3, 7)):
            c.px_on(x, y, spots)
    return c


def bug(body, wing=None, glow=None, legs='#1a140c'):
    c = Canvas()
    for (x0, y0, x1, y1) in ((5, 8, 2, 6), (5, 10, 2, 11), (11, 8, 14, 6), (11, 10, 14, 11), (6, 12, 4, 14), (10, 12, 12, 14)):
        c.line(x0, y0, x1, y1, legs)
    if wing:
        c.layer(c.ellipse(4.6, 6.2, 3.6, 2.0, -35) | c.ellipse(11.4, 6.2, 3.6, 2.0, 35), wing, shade='flat', bulge=0.4)
    abd = c.ellipse(8, 10.4, 3.6, 4.4)
    c.layer(abd, glow if glow else body, shade='dome', bulge=1.2)
    head = c.ellipse(8, 4.6, 2.6, 2.2)
    c.layer(head, body, shade='dome')
    c.line(7, 3, 5, 0, legs)
    c.line(9, 3, 11, 0, legs)
    if glow:
        c.px(8, 10, '#ffffff')
    return c


def seed(rp, spot=None):
    c = Canvas()
    hm = c.ellipse(8, 8.6, 5.2, 6.4, 25)
    c.layer(hm, rp, shade='dome', bulge=1.3)
    c.line(6, 4, 10, 13, rp[1], only_on=True)
    c.px_on(5, 6, rp[4])
    c.px_on(6, 5, rp[4])
    if spot:
        c.pxs([(10, 6), (9, 10)], spot)
    return c


def web():
    c = Canvas()
    col = ['#3a3a40', '#9aa0aa', '#d8dce4', '#f2f4f8', '#ffffff']
    cx, cy = 8, 8
    for k in range(8):
        a = math.radians(k * 45 + 10)
        c.line(cx, cy, cx + math.cos(a) * 7.4, cy + math.sin(a) * 7.4, col[2])
    for r in (2.4, 4.6, 6.8):
        pts = []
        for k in range(8):
            a = math.radians(k * 45 + 10)
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        for a_, b_ in zip(pts, pts[1:] + pts[:1]):
            c.line(a_[0], a_[1], b_[0], b_[1], col[3])
    c.px(8, 8, col[4])
    return c


def vine(rp, dew='#cfe8ff'):
    c = Canvas()
    hm = c.curve((3, 15), (1.0, 6.0), (8.0, 4.0), 2.4, 1.6) | c.curve((8.0, 4.0), (14.5, 3.0), (12.6, 10.0), 1.6, 1.0)
    c.layer(hm, rp, shade='cyl')
    for (p, s) in (((5, 9), 1), ((11, 5), 1)):
        lf = c.ellipse(p[0] + 1.5, p[1] - 1.2, 2.4, 1.3, -30)
        c.layer(lf, rp, shade='dome')
    for (x, y) in ((4, 12), (9, 3), (13, 8), (6, 6)):
        c.px(x, y, dew)
    return c


def nut(rp, crack='#ffb347'):
    c = Canvas()
    hm = c.ellipse(8, 8.4, 6.0, 6.4)
    c.layer(hm, rp, shade='dome', bulge=1.2)
    for x in range(4, 13):
        y = 8 + int(round(math.sin(x * 1.3) * 1.2))
        c.px_on(x, y, crack)
    c.line(8, 2, 8, 5, rp[1], only_on=True)
    c.px_on(5, 5, rp[4])
    return c


def ore(rock, crystal, n=3):
    c = Canvas()
    hm = c.poly([(1.5, 9), (3.5, 4.5), (8.5, 2.5), (13.5, 4.5), (14.8, 10), (11, 14.5), (5, 14.5)])
    c.layer(hm, rock, shade='flat', bulge=1.2)
    gems = [((6.0, 7.0), 2.0), ((10.4, 9.6), 1.8), ((7.2, 11.6), 1.4)][:n]
    for (x, y), r in gems:
        g = c.poly([(x, y - r * 1.4), (x + r, y), (x, y + r * 1.4), (x - r, y)])
        c.layer(g, crystal, shade='flat', bulge=0.5, outline='soft')
        c.px_on(int(x - 0.5), int(y - r * 0.6), crystal[4])
    return c


def sphere(rp, band=None, glint=WHITE):
    c = Canvas()
    hm = c.circle(8, 8.2, 6.4)
    c.layer(hm, rp, shade='dome', bulge=1.4)
    if band:
        for x in range(1, 16):
            y = 8 + int(round(0.6 * math.sin((x - 2) / 12 * math.pi)))
            c.px_on(x, y, band[2])
            c.px_on(x, y + 1, band[1])
    for (x, y) in ((6, 11), (10, 6), (11, 11), (5, 8)):
        c.px_on(x, y, rp[1])
    c.px(5, 4, glint)
    c.px(4, 5, rp[4])
    return c


def coin(rp):
    c = Canvas()
    hm = c.ellipse(8, 8, 5.2, 7.0)
    c.layer(hm, rp, shade='dome', bulge=0.9)
    for y in range(4, 13, 2):
        c.line(6, y, 10, y, rp[2], only_on=True)
    c.layer(c.ellipse(8, 8, 1.4, 1.4), rp, shade='flat', outline='soft')
    c.px(6, 3, WHITE)
    return c


def antler(rp):
    c = Canvas()
    hm = c.curve((4, 15), (3, 7), (9, 2), 3.0, 1.4) | c.stroke([(4.2, 10), (9.4, 8.4), (12.6, 4.6)], 2.0, 1.0) | \
        c.stroke([(5.6, 5.8), (6.6, 1.2)], 1.8, 0.8) | c.stroke([(9.6, 8.4), (13.6, 10.0)], 1.8, 0.8)
    c.layer(hm, rp, shade='cyl')
    return c


def turtle_shell(rp, edge):
    c = Canvas()
    hm = c.ellipse(8, 8.6, 7.0, 6.0)
    c.layer(hm, edge, shade='dome')
    inner = c.ellipse(8, 8.2, 5.4, 4.6)
    c.layer(inner, rp, shade='dome', outline='soft')
    for (x0, y0, x1, y1) in ((8, 4, 8, 12), (4, 6, 12, 10), (4, 10, 12, 6)):
        c.line(x0, y0, x1, y1, rp[1], only_on=True)
    return c


def stinger(rp, venom):
    c = Canvas()
    hm = c.curve((3, 3), (7, 6), (14, 14), 5.0, 0.4)
    c.layer(hm, rp, shade='cyl')
    for t in (0.2, 0.4):
        x, y = 3 + t * 9, 3 + t * 9
        c.line(x - 1, y + 1, x + 1, y - 1, '#e8c020', only_on=True)
    c.px(14, 15, venom)
    c.px(13, 15, venom)
    return c


def insect_wing(rp):
    c = Canvas()
    hm = c.ellipse(9, 7, 6.8, 3.6, -35) | c.ellipse(8, 10.6, 5.0, 2.6, -15)
    c.layer(hm, rp, shade='flat', bulge=0.4)
    for (x0, y0, x1, y1) in ((2, 13, 13, 3), (2, 13, 12, 10), (6, 9, 9, 3)):
        c.line(x0, y0, x1, y1, rp[1], only_on=True)
    c.px(9, 5, WHITE)
    return c


def feather_crest(rp, tip):
    c = Canvas()
    for (b, t, w) in (((8, 15), (3.0, 2.0), 3.2), ((8, 15), (13.0, 2.0), 3.2), ((8, 15), (8.0, 0.8), 3.8)):
        hm = c.stroke([b, t], w * 0.6, w)
        c.layer(hm, rp, shade='cyl')
        c.px_on(int(t[0]), int(t[1]) + 1, tip)
    return c


def pepper(rp):
    c = Canvas()
    body = c.curve((5.0, 4.6), (4.0, 12.0), (12.6, 14.6), 5.4, 1.2)
    c.layer(body, rp, shade='cyl', bulge=1.2)
    stem = c.stroke([(5, 5), (7.6, 1.6)], 1.8, 1.2) | c.ellipse(5.4, 4.6, 2.6, 1.4, 20)
    c.layer(stem, ramp('#4a8a20'), shade='cyl')
    c.px_on(4, 8, rp[4])
    return c


def honey_drop(rp):
    c = Canvas()
    hm = c.poly([(8, 1.5), (12.6, 9), (12.4, 11.8), (10, 14.4), (6, 14.4), (3.6, 11.8), (3.4, 9)]) | c.circle(8, 10.8, 4.6)
    c.layer(hm, rp, shade='dome', bulge=1.3)
    c.px_on(6, 9, WHITE)
    c.px_on(6, 10, rp[4])
    return c


def flower(petal, center='#f6e05a', stem=None):
    c = Canvas()
    st = stem or ramp('#4a7a24')
    c.layer(c.curve((8, 15), (7.0, 11), (8, 7), 1.6, 1.2), st, shade='cyl')
    c.layer(c.ellipse(5, 12, 2.4, 1.2, -30), st, shade='dome')
    for k in range(5):
        a = math.radians(-90 + k * 72)
        c.layer(c.ellipse(8 + math.cos(a) * 3.0, 5.4 + math.sin(a) * 3.0, 2.4, 1.8, math.degrees(a)), petal, shade='dome')
    c.layer(c.circle(8, 5.4, 1.6), ramp(center), shade='dome', outline='soft')
    return c


# ----------------------------------------------------------------------------- tools / consumables extra
def ration(wrap, tie):
    c = Canvas()
    hm = c.rect(2.0, 4.6, 14.0, 12.4, rad=2.6)
    c.layer(hm, wrap, shade='dome', bulge=0.9)
    c.layer(c.rect(7.0, 3.6, 9.0, 13.4), tie, shade='flat', outline='soft')
    for x in (3, 13):
        for y in (6, 8, 10):
            c.px_on(x, y, wrap[1])
    return c


def bbq_spit():
    c = Canvas()
    for (a, b) in (((2, 15), (5, 5)), ((14, 15), (11, 5))):
        c.layer(c.stroke([a, b], 1.6), WOOD, shade='cyl')
    c.layer(c.stroke([(0.6, 6.4), (15.4, 6.4)], 1.4), IRON, shade='cyl')
    m = c.ellipse(8, 7.4, 4.0, 3.2)
    c.layer(m, ramp('#a8422a'), shade='dome')
    c.px_on(6, 6, '#f0c8a0')
    c.layer(c.ellipse(1.6, 6.4, 1.2, 1.6), WOOD, shade='dome')
    return c


def trap_pitfall():
    c = Canvas()
    frame = c.rect(1.5, 3.5, 14.5, 12.5, rad=1.5)
    c.layer(frame, ramp('#6a5a3a'), shade='flat')
    inner = c.cells(c.rect(3.2, 5.2, 12.8, 10.8))
    c.recolor(inner & (c.img[..., 3] > 0), '#3a3022')
    for k in range(4, 13, 2):
        c.line(k, 5, k - 2, 11, '#c8b890', only_on=True)
        c.line(k - 2, 5, k, 11, '#a89870', only_on=True)
    for (x, y) in ((1, 2), (14, 2), (1, 13), (14, 13)):
        c.layer(c.stroke([(x + 0.5, y + 0.5), (x + 0.5, y + 2.8)], 1.6), IRON, shade='cyl')
    c.layer(c.rect(6, 12, 10, 15, rad=0.8), ramp('#8a6a3a'), shade='flat')
    return c


def trap_shock():
    c = Canvas()
    base = c.ellipse(8, 10.6, 7.0, 3.6)
    c.layer(base, IRON, shade='dome')
    top = c.ellipse(8, 8.8, 5.4, 2.6)
    c.layer(top, ramp('#3a4048'), shade='dome', outline='soft')
    coil = c.rect(6.4, 2.4, 9.6, 8.6, rad=1.0)
    c.layer(coil, ramp('#c87a2a'), shade='cyl')
    for y in (3, 5, 7):
        c.line(6, y, 9, y, '#6a3a10', only_on=True)
    for (x, y) in ((3, 2), (4, 3), (12, 2), (11, 3), (13, 5), (2, 5)):
        c.px(x, y, '#ffe55a')
    c.px(8, 1, '#ffffa0')
    return c


def badge():
    c = Canvas()
    rib = c.poly([(4.5, 0.5), (7.5, 0.5), (8, 7), (5.5, 9)]) | c.poly([(8.5, 0.5), (11.5, 0.5), (10.5, 9), (8, 7)])
    c.layer(rib, ramp('#b82a2a'), shade='flat')
    med = c.circle(8, 10, 5.4)
    c.layer(med, GOLD, shade='dome', bulge=1.2)
    c.layer(c.poly(star_pts(8, 10, 3.4, 1.5, 5)), ramp('#f0e0a0'), shade='flat', outline='soft')
    c.px(6, 7, WHITE)
    return c


def book(cover, pages='#efe6cf', strap=None):
    c = Canvas()
    c.layer(c.rect(3.0, 2.0, 13.4, 14.4, rad=0.8), ['#3a3024', '#c8bb9c', '#e6dcc4', '#f4eedf', '#ffffff'], shade='flat')
    c.layer(c.rect(2.0, 1.4, 12.4, 14.0, rad=1.0), cover, shade='flat', bulge=0.8)
    c.layer(c.rect(2.0, 1.4, 3.8, 14.0), ramp('#3a2414'), shade='flat', outline='soft')
    if strap:
        c.layer(c.rect(8.0, 6.4, 13.6, 8.6), strap, shade='flat', outline='soft')
    c.layer(c.poly(star_pts(7.6, 7.6, 2.6, 1.1, 4, -90)), GOLD, shade='flat', outline='soft')
    c.layer(c.rect(10.0, 12.0, 11.4, 15.6), ramp('#c82a2a'), shade='flat', outline='soft')
    return c


def smoke_ball(rp, smoke):
    c = ball(rp)
    for (x, y) in ((12, 2), (13, 1), (14, 3), (11, 1), (3, 1), (2, 2)):
        c.px(x, y, smoke)
    return c


# ----------------------------------------------------------------------------- armor piece icons
def helm_icon(A):
    c = Canvas()
    dome = c.ellipse(8, 8.2, 6.0, 6.4) | c.rect(2.0, 8.0, 14.0, 14.4, rad=1.2)
    for fn in A.get('helm_back', []):
        fn(c, A)
    c.layer(dome, A['main'], shade='dome', bulge=1.0)
    if A.get('brow'):
        band = c.cells(c.rect(2.0, 6.6, 14.0, 8.4))
        c.recolor(band & (c.img[..., 3] > 0), A['brow'][2])
        for x in range(2, 14):
            c.px_on(x, 6, A['brow'][3])
    visor = A.get('visor', 'open')
    if visor == 'open':
        hole = c.cells(c.rect(4.6, 9.0, 11.4, 15.0, rad=0.8), 0.5)
        c.img[hole] = 0
        c.owner[hole] = -1
        for y in range(9, 15):
            for x in (4, 11):
                if c.opaque(x, y):
                    c.px(x, y, A['main'][0])
        c.line(5, 8, 10, 8, A['main'][0], only_on=True)
    elif visor == 'slit':
        c.line(4, 9, 11, 9, '#0c0808')
        c.line(5, 10, 10, 10, '#0c0808')
        if A.get('eye'):
            c.px(5, 9, A['eye'])
            c.px(10, 9, A['eye'])
        for x in (6, 8, 10):
            c.px_on(x, 12, A['main'][1])
    elif visor == 'mask':
        for x0 in (4, 9):
            c.img[9:11, x0:x0 + 3, :3] = hx('#140e0a')
        c.px(7, 11, '#140e0a')
        c.px(8, 11, '#140e0a')
        for x in range(4, 12):
            c.px(x, 13, '#140e0a' if x % 2 else A['main'][3])
    for fn in A.get('helm', []):
        fn(c, A)
    return c


def mail_icon(A):
    c = Canvas()
    body = c.poly([(4.0, 3.0), (6.4, 2.0), (9.6, 2.0), (12.0, 3.0), (12.6, 14.6), (3.4, 14.6)])
    arms = c.rect(0.8, 2.6, 5.2, 10.6, rad=1.6) | c.rect(10.8, 2.6, 15.2, 10.6, rad=1.6)
    c.layer(arms, A['dark'], shade='cyl')
    c.layer(body, A['main'], shade='dome', bulge=0.8)
    neck = c.cells(c.ellipse(8, 1.6, 2.4, 1.8), 0.4)
    c.img[neck] = 0
    pads = c.ellipse(3.0, 3.6, 3.0, 2.4) | c.ellipse(13.0, 3.6, 3.0, 2.4)
    c.layer(pads, A.get('pad', A['main']), shade='dome')
    if A.get('belt'):
        c.layer(c.rect(3.4, 11.4, 12.6, 13.0), A['belt'], shade='flat', outline='soft')
    for fn in A.get('mail', []):
        fn(c, A)
    return c


def coil_icon(A):
    c = Canvas()
    legs = c.rect(3.2, 5.0, 7.6, 15.0, rad=1.0) | c.rect(8.4, 5.0, 12.8, 15.0, rad=1.0)
    c.layer(legs, A.get('legs', A['dark']), shade='cyl')
    skirt = c.poly([(3.0, 4.0), (13.0, 4.0), (14.2, 10.4), (1.8, 10.4)])
    c.layer(skirt, A['main'], shade='flat', bulge=0.8)
    c.line(8, 5, 8, 10, A['main'][0], only_on=True)
    belt = c.rect(2.6, 2.0, 13.4, 4.6, rad=0.6)
    c.layer(belt, A.get('belt', A['dark']), shade='flat')
    c.layer(c.rect(7.0, 2.2, 9.0, 4.4), A.get('buckle', GOLD), shade='flat', outline='soft')
    for fn in A.get('coil', []):
        fn(c, A)
    return c


def greaves_icon(A):
    c = Canvas()
    for x0 in (1.2, 8.6):
        shin = c.rect(x0 + 0.6, 1.6, x0 + 5.0, 12.0, rad=1.0)
        foot = c.rect(x0 - 0.4, 9.6, x0 + 6.0, 14.6, rad=1.6)
        c.layer(shin | foot, A['main'], shade='cyl')
        cuff = c.rect(x0 + 0.2, 1.2, x0 + 5.4, 3.6, rad=0.6)
        c.layer(cuff, A.get('cuff', A['dark']), shade='flat')
        c.layer(c.rect(x0 - 0.4, 13.0, x0 + 6.0, 15.0, rad=0.8), A.get('sole', ramp('#2a2420')), shade='flat', outline='soft')
    for fn in A.get('greaves', []):
        fn(c, A)
    return c


# decoration helpers for armor icons
def deco_horns(col, spread=1.0):
    def fn(c, A):
        for side in (-1, 1):
            hm = crescent(c, (8 + side * 4.6, 6.4), (8 + side * 7.6, 4.8), (8 + side * 6.6 * spread, 0.4),
                          (8 + side * 6.0, 4.0), (8 + side * 3.2, 5.0))
            c.layer(hm, col, shade='cyl')
    return fn


def deco_crest(col, h=4.5):
    def fn(c, A):
        hm = c.poly([(6.0, 3.0), (8.0, 3.0 - h), (10.0, 3.0)])
        c.layer(hm, col, shade='flat')
    return fn


def deco_ears(col):
    def fn(c, A):
        for side in (-1, 1):
            hm = c.poly([(8 + side * 2.0, 3.2), (8 + side * 5.6, -0.4), (8 + side * 5.8, 4.4)])
            c.layer(hm, col, shade='flat')
    return fn


def deco_px(pts_cols):
    def fn(c, A):
        for (x, y, col) in pts_cols:
            c.px(x, y, col)
    return fn


def deco_px_on(pts_cols):
    def fn(c, A):
        for (x, y, col) in pts_cols:
            c.px_on(x, y, col)
    return fn


def deco_layer(builder, col, shade='flat', outline='auto'):
    def fn(c, A):
        c.layer(builder(c), col, shade=shade, outline=outline)
    return fn


def charm_icon(stone, cord, rune, glow=False):
    c = Canvas()
    c.layer(c.curve((4.0, 3.0), (8.0, -1.6), (12.0, 3.0), 1.4, 1.4), cord, shade='cyl')
    hm = c.ellipse(8, 9.4, 5.6, 5.8)
    c.layer(hm, stone, shade='dome', bulge=1.2)
    c.layer(c.rect(6.0, 2.6, 10.0, 4.8, rad=0.8), cord, shade='flat')
    for (x, y) in ((8, 6), (8, 7), (8, 8), (8, 9), (8, 10), (8, 11), (6, 8), (10, 8), (7, 12), (9, 12), (6, 10), (10, 10)):
        if (x, y) in ((6, 10), (10, 10)) and not glow:
            continue
        c.px_on(x, y, rune)
    c.px(5, 6, WHITE)
    return c
