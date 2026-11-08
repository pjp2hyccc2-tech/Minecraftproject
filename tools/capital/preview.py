"""Numpy voxel ray-casting previews of the capital.

Features: perspective cameras, per-block colours (colors.py), partial boxes (slabs, carpets, lanterns...),
transparent glass/water, sun shading with shadow rays (sunlight passes through glass, tinted by stained
glass), Minecraft-like sky light and block light (flood fill), ambient occlusion, fog, clouds, section
(cut-away) planes and optional volumetric light shafts for interiors. Also a top-down map with districts.

    python3 tools/capital/build.py --views plaza_north,great_hall
"""
from __future__ import annotations

import math
import os
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from . import layout as L
from .colors import material
from .world import NX, NY, NZ, X0, Y0, Z0

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "previews")
K_AIR, K_SOLID, K_GLASS, K_BOX, K_WATER = 0, 1, 2, 3, 4
CS = 4  # coarse cell size for empty-space skipping


def _lin(c):
    return (np.asarray(c, np.float32) / 255.0) ** 2.2


class Scene:
    def __init__(self, w, light=True):
        t0 = time.time()
        pal = list(w.palette)
        grid = w.a
        # banners hang one block below their block: add virtual "cloth" entries
        extra = []
        n0 = len(pal)
        mats = [material(s) for s in pal]
        banner_ids = [i for i, m in enumerate(mats) if m["kind"] == "banner"]
        if banner_ids:
            grid = grid.copy()
            for bi in banner_ids:
                m = mats[bi]
                xs, zs, ys = np.nonzero(grid == bi)
                ok = ys > 0
                xs, zs, ys = xs[ok], zs[ok], ys[ok] - 1
                below = grid[xs, zs, ys]
                free = below <= 1
                vid = n0 + len(extra)
                b = m["box"]
                low = dict(m)
                low["box"] = (b[0], 0.0, b[2], b[3], 1.0, b[5])
                low["kind"] = "box"
                extra.append(low)
                grid[xs[free], zs[free], ys[free]] = vid
                hi = dict(m)
                hi["box"] = (b[0], 0.0, b[2], b[3], b[4], b[5])
                hi["kind"] = "box"
                mats[bi] = hi
        mats = mats + extra
        n = len(mats)
        kmap = {"air": K_AIR, "solid": K_SOLID, "stairs": K_SOLID, "glass": K_GLASS, "box": K_BOX,
                "water": K_WATER, "banner": K_BOX}
        self.kind = np.array([kmap[m["kind"]] for m in mats], np.uint8)
        self.top = np.array([_lin(m["top"]) for m in mats], np.float32)
        self.side = np.array([_lin(m["side"]) for m in mats], np.float32)
        self.alpha = np.array([m["alpha"] for m in mats], np.float32)
        self.box = np.array([m["box"] if m["box"] is not None else (0, 0, 0, 1, 1, 1) for m in mats], np.float32)
        self.emit = np.array([m["emit"] for m in mats], np.uint8)
        self.names = [m["name"] for m in mats]
        self.grid = grid
        self.dims = np.array([NX, NY, NZ])
        k = self.kind[grid]
        self.occ = k != K_AIR
        # coarse occupancy (any non-air in a 4^3 cell)
        cx, cz, cy = -(-NX // CS), -(-NZ // CS), -(-NY // CS)
        pad = np.zeros((cx * CS, cz * CS, cy * CS), bool)
        pad[:NX, :NZ, :NY] = self.occ
        self.coarse = pad.reshape(cx, CS, cz, CS, cy, CS).any(axis=(1, 3, 5))
        self.opaque = (k == K_SOLID)
        self.sky = None
        self.blk = None
        if light:
            self._light()
        print(f"scene: {time.time() - t0:.1f}s")

    # ------------------------------------------------------------------ light fields
    def _light(self):
        t0 = time.time()
        opaque = self.opaque
        transp = (~opaque).astype(np.uint8)
        # sky light: 15 above the highest opaque block of each column, then flood fill
        top = np.where(opaque.any(axis=2), NY - 1 - np.argmax(opaque[:, :, ::-1], axis=2), -1)
        ys = np.arange(NY)[None, None, :]
        sky = np.where(ys > top[:, :, None], 15, 0).astype(np.uint8)
        self.sky = self._flood(sky, transp, 15)
        blk = self.emit[self.grid].astype(np.uint8)
        self.blk = self._flood(blk, np.maximum(transp, (blk > 0).astype(np.uint8)), 15)
        print(f"light: {time.time() - t0:.1f}s")

    @staticmethod
    def _flood(Lf, transp, iters):
        D = np.empty_like(Lf)
        for _ in range(iters):
            np.subtract(Lf, 1, out=D, where=Lf > 0)
            D[Lf == 0] = 0
            np.maximum(Lf[1:], D[:-1], out=Lf[1:])
            np.maximum(Lf[:-1], D[1:], out=Lf[:-1])
            np.maximum(Lf[:, 1:], D[:, :-1], out=Lf[:, 1:])
            np.maximum(Lf[:, :-1], D[:, 1:], out=Lf[:, :-1])
            np.maximum(Lf[:, :, 1:], D[:, :, :-1], out=Lf[:, :, 1:])
            np.maximum(Lf[:, :, :-1], D[:, :, 1:], out=Lf[:, :, :-1])
            Lf *= transp
        return Lf

    # ------------------------------------------------------------------ tracing
    def trace(self, O, Dr, max_t=2000.0, shadow=False, clip=None, max_iter=4000):
        """Trace rays (world coords). Returns dict with hit, t, vox(id), cell (gx,gy,gz), axis, sign,
        and the first transparent layer (tt, tvox, taxis, tsign) for primary rays, or transmittance (rgb)
        for shadow rays. ``clip``: list of (axis, value, keep_below) planes in world coords; voxels on the
        cut side are ignored (primary rays only)."""
        N = Dr.shape[0]
        O = np.asarray(O, np.float64)
        if O.ndim == 1:
            O = np.broadcast_to(O, (N, 3))
        G = O - np.array([X0, Y0, Z0], np.float64)   # grid coords (x, y, z)
        Dr = np.asarray(Dr, np.float64)
        with np.errstate(divide="ignore", invalid="ignore"):
            inv = np.where(Dr != 0, 1.0 / Dr, np.inf)
        dims = self.dims.astype(np.float64)
        t0 = np.where(Dr != 0, (np.where(Dr > 0, 0.0, dims) - G) * inv, -np.inf)
        t1 = np.where(Dr != 0, (np.where(Dr > 0, dims, 0.0) - G) * inv, np.inf)
        inside0 = (G >= 0) & (G < dims)
        t0 = np.where((Dr == 0) & ~inside0, np.inf, t0)
        t1 = np.where((Dr == 0) & ~inside0, -np.inf, t1)
        tenter = np.maximum(t0.max(axis=1), 0.0)
        texit = np.minimum(t1.min(axis=1), max_t)
        enter_axis = np.argmax(t0, axis=1)
        res = {
            "hit": np.zeros(N, bool), "t": np.full(N, np.inf), "vox": np.zeros(N, np.int32),
            "cell": np.zeros((N, 3), np.int32), "axis": np.zeros(N, np.int8), "sign": np.zeros(N, np.int8),
        }
        if shadow:
            res["trans"] = np.ones((N, 3), np.float32)
        else:
            res.update({"tt": np.full(N, np.inf), "tvox": np.zeros(N, np.int32), "taxis": np.zeros(N, np.int8),
                        "tsign": np.zeros(N, np.int8), "tcell": np.zeros((N, 3), np.int32)})
        act = np.nonzero(tenter < texit)[0]
        if act.size == 0:
            return res
        g = G[act]
        d = Dr[act]
        iv = inv[act]
        t = tenter[act] + 1e-6
        tex = texit[act]
        p = g + d * t[:, None]
        cell = np.clip(np.floor(p).astype(np.int64), 0, (self.dims - 1)[None, :])
        step = np.where(d > 0, 1, -1).astype(np.int64)
        tdelta = np.abs(iv)
        tnext = np.where(d != 0, (cell + (step > 0) - g) * iv, np.inf)
        last_axis = enter_axis[act].astype(np.int8)
        idx = act
        if shadow:
            trans = np.ones((idx.size, 3), np.float32)
        grid, kind, coarse = self.grid, self.kind, self.coarse
        clip_planes = clip or []
        for it in range(max_iter):
            if idx.size == 0:
                break
            vox = grid[cell[:, 0], cell[:, 2], cell[:, 1]]
            k = kind[vox]
            if clip_planes and not shadow:
                cut = np.zeros(idx.size, bool)
                for (ax, val, keep_below) in clip_planes:
                    off = (X0, Y0, Z0)[ax]
                    wc = cell[:, ax] + off
                    cut |= (wc > val) if keep_below else (wc < val)
                k = np.where(cut, K_AIR, k)
            done = np.zeros(idx.size, bool)
            # ---- solids
            hs = k == K_SOLID
            # ---- partial boxes
            hb = k == K_BOX
            if hb.any():
                bi = np.nonzero(hb)[0]
                bx = self.box[vox[bi]].astype(np.float64)
                lo = cell[bi] + bx[:, 0:3]
                hi = cell[bi] + bx[:, 3:6]
                ta = (lo - g[bi]) * iv[bi]
                tb = (hi - g[bi]) * iv[bi]
                tmin = np.minimum(ta, tb)
                tmax = np.maximum(ta, tb)
                # rays parallel to an axis: inside slab -> (-inf, inf), outside -> miss
                par = d[bi] == 0
                if par.any():
                    insl = (g[bi] >= lo) & (g[bi] <= hi)
                    tmin = np.where(par, np.where(insl, -np.inf, np.inf), tmin)
                    tmax = np.where(par, np.where(insl, np.inf, -np.inf), tmax)
                tn = tmin.max(axis=1)
                tf = tmax.min(axis=1)
                tcell_exit = tnext[bi].min(axis=1)
                ok = (tn <= tf) & (tf >= t[bi] - 1e-9) & (tn <= tcell_exit + 1e-9)
                bh = bi[ok]
                if bh.size:
                    hs[bh] = True
                    ax_b = np.argmax(tmin[ok], axis=1).astype(np.int8)
                    tt_b = np.maximum(tn[ok], t[bh])
                    entered_inside = tn[ok] < t[bh]
                    ax_b = np.where(entered_inside, last_axis[bh], ax_b)
                    t[bh] = tt_b
                    last_axis[bh] = ax_b
            if hs.any():
                hi_ = np.nonzero(hs)[0]
                gi = idx[hi_]
                if shadow:
                    res["hit"][gi] = True
                    res["trans"][gi] = 0.0
                else:
                    res["hit"][gi] = True
                    res["t"][gi] = t[hi_]
                    res["vox"][gi] = vox[hi_]
                    res["cell"][gi] = cell[hi_]
                    res["axis"][gi] = last_axis[hi_]
                    res["sign"][gi] = -step[hi_, last_axis[hi_]]
                done |= hs
            # ---- transparent layers
            ht = ((k == K_GLASS) | (k == K_WATER)) & ~done
            if ht.any():
                ti = np.nonzero(ht)[0]
                if shadow:
                    a = self.alpha[vox[ti]][:, None]
                    col = self.top[vox[ti]]
                    trans[ti] *= (1.0 - a * 0.85) + a * 0.85 * np.clip(col * 1.6, 0, 1)
                else:
                    gi = idx[ti]
                    first = ~np.isfinite(res["tt"][gi])
                    if first.any():
                        f = ti[first]
                        gf = gi[first]
                        res["tt"][gf] = t[f]
                        res["tvox"][gf] = vox[f]
                        res["taxis"][gf] = last_axis[f]
                        res["tsign"][gf] = -step[f, last_axis[f]]
                        res["tcell"][gf] = cell[f]
                    # water is opaque-ish below the surface: stop after 6 blocks of water
            # ---- advance
            axis = np.argmin(tnext, axis=1)
            ar = np.arange(idx.size)
            t = tnext[ar, axis]
            cell[ar, axis] += step[ar, axis]
            tnext[ar, axis] += tdelta[ar, axis]
            last_axis = axis.astype(np.int8)
            out = (t > tex) | (cell < 0).any(axis=1) | (cell >= self.dims[None, :]).any(axis=1)
            done |= out
            if shadow:
                dim = trans.max(axis=1) < 0.02
                done |= dim
            keep = ~done
            if shadow and (~keep).any():
                fin = np.nonzero(~keep & ~hs)[0]
                res["trans"][idx[fin]] = trans[fin]
            idx, g, d, iv, t, tex, cell, step, tdelta, tnext, last_axis = (
                idx[keep], g[keep], d[keep], iv[keep], t[keep], tex[keep], cell[keep], step[keep],
                tdelta[keep], tnext[keep], last_axis[keep])
            if shadow:
                trans = trans[keep]
            # ---- empty-space skipping through coarse cells
            if idx.size and it % 2 == 1:
                cc = cell // CS
                emp = ~coarse[cc[:, 0], cc[:, 2], cc[:, 1]]
                if emp.any():
                    ei = np.nonzero(emp)[0]
                    lo = cc[ei] * CS
                    hi = lo + CS
                    de = d[ei]
                    te = np.where(de > 0, (hi - g[ei]) * iv[ei], np.where(de < 0, (lo - g[ei]) * iv[ei], np.inf))
                    tc = te.min(axis=1) + 1e-6
                    adv = tc > t[ei]
                    ei = ei[adv]
                    tc = tc[adv]
                    if ei.size:
                        pe = g[ei] + d[ei] * tc[:, None]
                        nc = np.floor(pe).astype(np.int64)
                        t[ei] = tc
                        cell[ei] = nc
                        tnext[ei] = np.where(d[ei] != 0, (nc + (step[ei] > 0) - g[ei]) * iv[ei], np.inf)
                        last_axis[ei] = np.argmin(np.abs(pe - np.round(pe)), axis=1).astype(np.int8)
                        out = (t > tex) | (cell < 0).any(axis=1) | (cell >= self.dims[None, :]).any(axis=1)
                        if out.any():
                            keep = ~out
                            if shadow:
                                fin = np.nonzero(out)[0]
                                res["trans"][idx[fin]] = trans[fin]
                                trans = trans[keep]
                            idx, g, d, iv, t, tex, cell, step, tdelta, tnext, last_axis = (
                                idx[keep], g[keep], d[keep], iv[keep], t[keep], tex[keep], cell[keep], step[keep],
                                tdelta[keep], tnext[keep], last_axis[keep])
        if shadow and idx.size:
            res["trans"][idx] = trans
        return res


# ====================================================================================== camera & shading
SKY_TOP = np.array([0.20, 0.38, 0.82])
SKY_HOR = np.array([0.72, 0.80, 0.92])
SUN_COL = np.array([1.00, 0.93, 0.80])


def sun_dir(elev=38.0, azim=-70.0):
    """Direction towards the sun. azim: degrees from +x (east) towards +z (south); -90 would be north."""
    e, a = math.radians(elev), math.radians(azim)
    return np.array([math.cos(e) * math.cos(a), math.sin(e), math.cos(e) * math.sin(a)])


def camera_rays(pos, look, width, height, fov=60.0, up=(0, 1, 0)):
    pos = np.asarray(pos, np.float64)
    fwd = np.asarray(look, np.float64) - pos
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, up)
    right /= np.linalg.norm(right)
    upv = np.cross(right, fwd)
    aspect = width / height
    th = math.tan(math.radians(fov) / 2)
    xs = (np.arange(width) + 0.5) / width * 2 - 1
    ys = 1 - (np.arange(height) + 0.5) / height * 2
    X, Y = np.meshgrid(xs * th * aspect, ys * th)
    d = fwd[None, None, :] + X[..., None] * right[None, None, :] + Y[..., None] * upv[None, None, :]
    d /= np.linalg.norm(d, axis=2, keepdims=True)
    return pos, d.reshape(-1, 3)


def sky_color(d, sun):
    up = np.clip(d[:, 1], -0.2, 1.0)
    f = np.clip(up, 0, 1) ** 0.6
    col = SKY_HOR[None, :] * (1 - f[:, None]) + SKY_TOP[None, :] * f[:, None]
    s = np.clip(d @ sun, 0, 1)
    col += SUN_COL[None, :] * (s ** 64)[:, None] * 2.0 + np.array([0.25, 0.18, 0.08])[None, :] * (s ** 6)[:, None]
    # clouds on a plane far above
    from .noise import fbm2
    with np.errstate(divide="ignore", invalid="ignore"):
        tcl = np.where(d[:, 1] > 0.02, (420.0) / d[:, 1], np.inf)
    ok = np.isfinite(tcl)
    if ok.any():
        px = d[ok, 0] * tcl[ok]
        pz = d[ok, 2] * tcl[ok]
        n = fbm2(px, pz, 260.0, 4, 9917)
        c = np.clip((n - 0.05) * 2.4, 0, 1) * np.clip(d[ok, 1] * 3, 0, 1)
        col[ok] = col[ok] * (1 - c[:, None] * 0.85) + np.array([0.97, 0.97, 1.0])[None, :] * c[:, None] * 0.85
    return col


def shade(scene, O, D, sun, clip=None, ambient_boost=1.0, fog=900.0, exposure=1.0, volumetric=0, vol_res=3,
          width=None, height=None, warm=1.0):
    t0 = time.time()
    N = D.shape[0]
    r = scene.trace(O, D, clip=clip)
    print(f"  primary: {time.time() - t0:.1f}s ({int(r['hit'].sum())} hits)")
    col = sky_color(D, sun)

    def surface(hit, tt, vox, cell, axis, sign):
        idx = np.nonzero(hit)[0]
        if idx.size == 0:
            return idx, None
        P = (np.asarray(O, np.float64) if np.ndim(O) == 1 else O[idx]) + D[idx] * tt[idx][:, None]
        v = vox[idx]
        ax = axis[idx].astype(int)
        sg = sign[idx].astype(int)
        base = np.where((ax == 1)[:, None] & (sg == 1)[:, None], scene.top[v], scene.side[v])
        c = cell[idx]
        from .noise import hash3
        jit = 0.93 + 0.14 * hash3(c[:, 0], c[:, 1], c[:, 2], 5).astype(np.float32)
        base = base * jit[:, None]
        # normal
        n = np.zeros((idx.size, 3))
        n[np.arange(idx.size), ax] = sg
        # face-adjacent cell for light lookup
        fc = c.copy()
        fc[np.arange(idx.size), ax] += sg
        fc = np.clip(fc, 0, scene.dims - 1)
        if scene.sky is not None:
            sk = scene.sky[fc[:, 0], fc[:, 2], fc[:, 1]].astype(np.float32) / 15.0
            bl = scene.blk[fc[:, 0], fc[:, 2], fc[:, 1]].astype(np.float32) / 15.0
            # partial blocks use their own cell's light too
            sk = np.maximum(sk, scene.sky[c[:, 0], c[:, 2], c[:, 1]].astype(np.float32) / 15.0)
            bl = np.maximum(bl, scene.blk[c[:, 0], c[:, 2], c[:, 1]].astype(np.float32) / 15.0)
        else:
            sk = np.ones(idx.size, np.float32)
            bl = np.zeros(idx.size, np.float32)
        # ambient occlusion: solid neighbours around the face cell
        occ = np.zeros(idx.size, np.float32)
        oa = [(a, b) for a in (-1, 0, 1) for b in (-1, 0, 1) if (a, b) != (0, 0)]
        for (a, b) in oa:
            q = fc.copy()
            ax1 = (ax + 1) % 3
            ax2 = (ax + 2) % 3
            q[np.arange(idx.size), ax1] += a
            q[np.arange(idx.size), ax2] += b
            q = np.clip(q, 0, scene.dims - 1)
            w_ = 1.0 if (a == 0 or b == 0) else 0.6
            occ += scene.opaque[q[:, 0], q[:, 2], q[:, 1]] * w_
        ao = 1.0 - np.clip(occ / 6.4, 0, 1) * 0.55
        # edge darkening for a blocky look
        fr = P - (c + np.array([X0, Y0, Z0]))
        e = np.minimum(fr, 1 - fr)
        e[np.arange(idx.size), ax] = 1.0
        edge = np.where(e.min(axis=1) < 0.045, 0.82, 1.0)
        # sun
        ndl = np.clip(n @ sun, 0, 1)
        lit = np.zeros((idx.size, 3), np.float32)
        cand = np.nonzero(ndl > 0)[0]
        if cand.size:
            So = P[cand] + n[cand] * 1e-3
            sr = scene.trace(So, np.broadcast_to(sun, (cand.size, 3)).copy(), shadow=True, max_t=700.0)
            lit[cand] = sr["trans"] * ndl[cand][:, None]
        amb = (0.035 + 0.66 * sk ** 2.4)[:, None] * (SKY_TOP * 0.55 + SKY_HOR * 0.45)[None, :] * ambient_boost
        blk = (bl ** 2.2)[:, None] * np.array([1.0, 0.70, 0.40])[None, :] * 1.7 * warm
        light = amb * ao[:, None] + lit * SUN_COL[None, :] * 1.35 + blk * (0.6 + 0.4 * ao[:, None])
        emit = scene.emit[v].astype(np.float32)[:, None] / 15.0
        cc = base * light * edge[:, None] + base * emit * 1.2
        dist = tt[idx]
        fogf = 1.0 - np.exp(-dist / fog)
        cc = cc * (1 - fogf[:, None]) + (SKY_HOR * 0.95)[None, :] * fogf[:, None]
        return idx, cc

    idx, cc = surface(r["hit"], r["t"], r["vox"], r["cell"], r["axis"], r["sign"])
    if idx.size:
        col[idx] = cc
    # transparent first layer (glass / water): blend in front of what is behind
    tr = np.isfinite(r["tt"]) & (r["tt"] < np.where(r["hit"], r["t"], np.inf))
    tidx, tc = surface(tr, r["tt"], r["tvox"], r["tcell"], r["taxis"], r["tsign"])
    if tidx.size:
        a = scene.alpha[r["tvox"][tidx]][:, None]
        is_water = scene.kind[r["tvox"][tidx]] == K_WATER
        # water: fresnel-ish reflection of the sky
        if is_water.any():
            refl = sky_color(D[tidx] * np.array([1, -1, 1]), sun)
            a = np.where(is_water[:, None], np.clip(a + 0.1, 0, 0.9), a)
            tc = np.where(is_water[:, None], tc * 0.55 + refl * 0.45, tc)
        col[tidx] = col[tidx] * (1 - a) + tc * a
    if volumetric:
        col += _volumetric(scene, O, D, r, sun, volumetric, vol_res, width, height)
    col = col * exposure
    col = col / (1.0 + col * 0.18)
    rgb = np.clip(col, 0, 1) ** (1 / 2.2)
    print(f"  shaded: {time.time() - t0:.1f}s")
    return rgb


def _volumetric(scene, O, D, r, sun, samples, res, width, height):
    """In-scattered sunlight along the primary rays (low resolution, upsampled)."""
    H, Wd = height, width
    sub = np.zeros((H, Wd), bool)
    sub[res // 2::res, res // 2::res] = True
    ids = np.nonzero(sub.ravel())[0]
    tend = np.where(r["hit"][ids], r["t"][ids], 80.0)
    tend = np.minimum(tend, 80.0)
    acc = np.zeros(ids.size, np.float32)
    jitter = ((np.arange(ids.size) * 0.618) % 1.0)
    Ob = np.asarray(O, np.float64)
    for s in range(samples):
        ts = tend * (s + jitter) / samples
        P = (Ob if Ob.ndim == 1 else Ob[ids]) + D[ids] * ts[:, None]
        sr = scene.trace(P, np.broadcast_to(sun, (ids.size, 3)).copy(), shadow=True, max_t=300.0)
        acc += sr["trans"].mean(axis=1) * (tend / samples) * 0.012
    hh, ww = len(range(res // 2, H, res)), len(range(res // 2, Wd, res))
    img = acc.reshape(hh, ww)
    im = Image.fromarray(np.clip(img * 255 * 4, 0, 255).astype(np.uint8)).resize((Wd, H), Image.BILINEAR)
    up = np.asarray(im, np.float32).ravel() / 255.0 / 4
    phase = np.clip(D @ sun, 0, 1) ** 4 * 1.5 + 0.35
    return (up * phase)[:, None] * SUN_COL[None, :] * 1.6


def render(scene, name, pos, look, size=(1280, 720), fov=60.0, sun=None, clip=None, scale=1.0, label=None, **kw):
    w_, h_ = int(size[0] * scale), int(size[1] * scale)
    t0 = time.time()
    O, D = camera_rays(pos, look, w_, h_, fov)
    if sun is None:
        sun = sun_dir()
    elif isinstance(sun, tuple):
        sun = sun_dir(*sun)
    rgb = shade(scene, O, D, sun, clip=clip, width=w_, height=h_, **kw)
    img = Image.fromarray((rgb.reshape(h_, w_, 3) * 255).astype(np.uint8))
    if label:
        d = ImageDraw.Draw(img)
        d.rectangle([0, h_ - 22, w_, h_], fill=(0, 0, 0))
        d.text((8, h_ - 18), label, fill=(240, 240, 240), font=_font(14))
    os.makedirs(OUT_DIR, exist_ok=True)
    p = os.path.join(OUT_DIR, f"{name}.png")
    img.save(p, optimize=True)
    print(f"preview {name}: {time.time() - t0:.1f}s -> {p}")
    return p


def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


# ====================================================================================== top-down map
def topdown(w, name="map_top", scale=3, districts=True):
    """Top-down map: top block colour with hillshade + district outlines and names."""
    pal = w.palette
    mats = [material(s) for s in pal]
    vis = np.array([m["kind"] not in ("air",) for m in mats])
    col = np.array([m["top"] for m in mats], np.float32)
    a = w.a
    v = vis[a]
    top = np.where(v.any(axis=2), NY - 1 - np.argmax(v[:, :, ::-1], axis=2), 0)
    ids = np.take_along_axis(a, top[:, :, None], axis=2)[:, :, 0]
    c = col[ids]
    hgt = top.astype(np.float32)
    gx = np.gradient(hgt, axis=0)
    gz = np.gradient(hgt, axis=1)
    shade_ = np.clip(1.0 - (gx * 0.6 + gz * 0.9) * 0.08, 0.55, 1.3)
    c = c * shade_[:, :, None] * (0.75 + 0.25 * np.clip((hgt - 30) / 120 + 0.5, 0, 1))[:, :, None]
    c[~w.footprint] = (40, 40, 48)
    img = Image.fromarray(np.clip(c.transpose(1, 0, 2), 0, 255).astype(np.uint8)).resize(
        (NX * scale, NZ * scale), Image.NEAREST)
    if districts:
        d = ImageDraw.Draw(img)
        f = _font(12)
        for dist in L.DISTRICTS.values():
            colr = (255, 240, 120) if dist.owner == "free" else (255, 120, 120)
            for (x0, z0, x1, z1) in dist.boxes:
                d.rectangle([(x0 - X0) * scale, (z0 - Z0) * scale, (x1 - X0 + 1) * scale - 1, (z1 - Z0 + 1) * scale - 1],
                            outline=colr, width=1)
            x0, z0, x1, z1 = dist.boxes[0]
            d.text(((x0 - X0) * scale + 3, (z0 - Z0) * scale + 2), dist.name, fill=colr, font=f)
        for nm, s in w.spots.items():
            x, y, z = s["pos"]
            px, pz = (x - X0) * scale, (z - Z0) * scale
            d.ellipse([px - 3, pz - 3, px + 3, pz + 3], fill=(255, 60, 200))
    os.makedirs(OUT_DIR, exist_ok=True)
    p = os.path.join(OUT_DIR, f"{name}.png")
    img.save(p, optimize=True)
    print(f"map -> {p}")
    return p


# ====================================================================================== named views
def views():
    """Named preview cameras: name -> kwargs for render()."""
    from .castle import plan as P
    return {
        "plaza_north": dict(pos=(-5, 6, 30), look=(3, 56, -120), fov=64, sun=sun_dir(34, -160), fog=2400,
                            label="From the plaza looking north at the castle (cf. concept art 1)"),
        "aerial_south": dict(pos=(-120, 118, 150), look=(10, 36, -110), fov=58, sun=sun_dir(40, -150), fog=2600,
                             label="Aerial view from over the lake (cf. design board main view)"),
        "aerial_castle": dict(pos=(-95, 105, -10), look=(0, 50, -140), fov=60, sun=sun_dir(42, -150), fog=2400,
                              label="The castle from the south-west"),
        "castle_east": dict(pos=(160, 64, -30), look=(40, 50, -140), fov=62, sun=sun_dir(36, -165), fog=2400,
                            label="The castle from the east (river, waterfall cirque, gardens)"),
        "waterfall": dict(pos=(96, 30, -40), look=(126, 34, -108), fov=66, sun=sun_dir(40, -160), fog=2400,
                          label="The great waterfall north-east of the castle"),
        "great_hall": dict(**P.VIEW_GREAT_HALL),
        "throne_room": dict(**P.VIEW_THRONE),
        "star_gate": dict(**P.VIEW_STAR_GATE),
        "library": dict(**P.VIEW_LIBRARY),
        "chapel": dict(**P.VIEW_CHAPEL),
        "castle_section": dict(**P.VIEW_SECTION),
    }


def render_all(w, names=None, scale=1.0):
    os.makedirs(OUT_DIR, exist_ok=True)
    if names is None or "map_top" in names:
        topdown(w)
    vs = views()
    sel = [n for n in (names or vs.keys()) if n in vs]
    if not sel:
        return
    scene = Scene(w)
    for n in sel:
        kw = dict(vs[n])
        render(scene, n, scale=scale, **kw)


def plan_slice(w, y, box, name="slice", scale=6):
    """Top-down plan of the blocks at height y (and the first non-air block below, darkened) in a box
    (x0, z0, x1, z1): a quick check of interior layouts."""
    x0, z0, x1, z1 = box
    pal = w.palette
    mats = [material(s) for s in pal]
    col = np.array([m["top"] for m in mats], np.float32)
    kind = np.array([m["kind"] != "air" for m in mats])
    sub = w.a[x0 - X0:x1 - X0 + 1, z0 - Z0:z1 - Z0 + 1, :y - Y0 + 1]
    vis = kind[sub]
    top = np.where(vis.any(axis=2), sub.shape[2] - 1 - np.argmax(vis[:, :, ::-1], axis=2), 0)
    ids = np.take_along_axis(sub, top[:, :, None], axis=2)[:, :, 0]
    c = col[ids]
    depth = (sub.shape[2] - 1 - top).astype(np.float32)
    c = c * np.clip(1.0 - depth * 0.08, 0.35, 1.0)[:, :, None]
    img = Image.fromarray(np.clip(c.transpose(1, 0, 2), 0, 255).astype(np.uint8)).resize(
        ((x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    for gx in range(x0, x1 + 1):
        if gx % 10 == 0:
            d.line([((gx - x0) * scale, 0), ((gx - x0) * scale, img.size[1])], fill=(255, 0, 0), width=1)
            d.text(((gx - x0) * scale + 2, 2), str(gx), fill=(255, 60, 60), font=_font(10))
    for gz in range(z0, z1 + 1):
        if gz % 10 == 0:
            d.line([(0, (gz - z0) * scale), (img.size[0], (gz - z0) * scale)], fill=(255, 0, 0), width=1)
            d.text((2, (gz - z0) * scale + 2), str(gz), fill=(255, 60, 60), font=_font(10))
    p = os.path.join(OUT_DIR, f"{name}.png")
    img.save(p)
    return p
