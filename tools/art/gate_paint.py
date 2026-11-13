"""A small numpy painter for the gatekeeper screen's pictures (tools/art/gate_screen.py).

Colours are floats 0..1; shapes are rasterised by PIL at SS times the size and averaged down, so every edge is
anti-aliased. Deterministic: every random choice comes from the canvas' seeded generator.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw

SS = 4


def rgb(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


def mix(a, b, t):
    return a + (b - a) * t


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


class Canvas:
    def __init__(self, w, h, seed):
        self.w = w
        self.h = h
        self.img = np.zeros((h, w, 3), np.float32)
        self.rng = np.random.default_rng(seed)
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
        self.X = xs + 0.5
        self.Y = ys + 0.5

    # ------------------------------------------------------------------ masks

    def _layer(self):
        return Image.new("L", (self.w * SS, self.h * SS), 0)

    def _down(self, im):
        return np.asarray(im.resize((self.w, self.h), Image.BOX), np.float32) / 255.0

    def poly(self, pts):
        im = self._layer()
        ImageDraw.Draw(im).polygon([(x * SS, y * SS) for x, y in pts], fill=255)
        return self._down(im)

    def polys(self, many):
        im = self._layer()
        draw = ImageDraw.Draw(im)
        for pts in many:
            draw.polygon([(x * SS, y * SS) for x, y in pts], fill=255)
        return self._down(im)

    def ellipse(self, cx, cy, rx, ry=None):
        ry = rx if ry is None else ry
        im = self._layer()
        ImageDraw.Draw(im).ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=255)
        return self._down(im)

    def rect(self, x0, y0, x1, y1):
        im = self._layer()
        ImageDraw.Draw(im).rectangle([x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1], fill=255)
        return self._down(im)

    def line(self, pts, width):
        im = self._layer()
        draw = ImageDraw.Draw(im)
        scaled = [(x * SS, y * SS) for x, y in pts]
        draw.line(scaled, fill=255, width=max(1, int(round(width * SS))), joint="curve")
        r = width * SS / 2.0
        for x, y in (scaled[0], scaled[-1]):
            draw.ellipse([x - r, y - r, x + r, y + r], fill=255)
        return self._down(im)

    def lines(self, many, width):
        im = self._layer()
        draw = ImageDraw.Draw(im)
        for pts in many:
            draw.line([(x * SS, y * SS) for x, y in pts], fill=255, width=max(1, int(round(width * SS))),
                      joint="curve")
        return self._down(im)

    def arc_band(self, cx, cy, r0, r1, a0=0.0, a1=360.0):
        """The ring between radii r0 and r1, between angles a0..a1 (degrees, 0 = +x, clockwise on screen)."""
        im = self._layer()
        draw = ImageDraw.Draw(im)
        draw.pieslice([(cx - r1) * SS, (cy - r1) * SS, (cx + r1) * SS, (cy + r1) * SS], a0, a1, fill=255)
        draw.pieslice([(cx - r0) * SS, (cy - r0) * SS, (cx + r0) * SS, (cy + r0) * SS], a0, a1, fill=0)
        return self._down(im)

    # ------------------------------------------------------------------ noise

    def value_noise(self, cell, seed=None):
        rng = self.rng if seed is None else np.random.default_rng(seed)
        cell = max(1.0, cell)
        gw = int(self.w / cell) + 3
        gh = int(self.h / cell) + 3
        g = rng.random((gh, gw)).astype(np.float32)
        xs = np.arange(self.w, dtype=np.float32) / cell
        ys = np.arange(self.h, dtype=np.float32) / cell
        x0 = np.floor(xs).astype(int)
        y0 = np.floor(ys).astype(int)
        fx = xs - x0
        fy = ys - y0
        sx = fx * fx * (3 - 2 * fx)
        sy = fy * fy * (3 - 2 * fy)
        a = g[y0][:, x0]
        b = g[y0][:, x0 + 1]
        c = g[y0 + 1][:, x0]
        d = g[y0 + 1][:, x0 + 1]
        top = a + (b - a) * sx[None, :]
        bottom = c + (d - c) * sx[None, :]
        return top + (bottom - top) * sy[:, None]

    def fbm(self, cell, octaves=4, persistence=0.5, seed=None):
        rng = self.rng if seed is None else np.random.default_rng(seed)
        total = np.zeros((self.h, self.w), np.float32)
        amp = 1.0
        norm = 0.0
        for o in range(octaves):
            total += amp * self.value_noise(cell / (2 ** o), seed=int(rng.integers(1 << 30)))
            norm += amp
            amp *= persistence
        return total / norm

    # ------------------------------------------------------------------ paint

    def put(self, mask, color, alpha=1.0):
        m = np.clip(mask * alpha, 0.0, 1.0)[..., None]
        col = color if np.ndim(color) == 3 else np.asarray(color, np.float32)[None, None, :]
        self.img = self.img * (1.0 - m) + col * m

    def add(self, mask, color, k=1.0):
        col = color if np.ndim(color) == 3 else np.asarray(color, np.float32)[None, None, :]
        self.img = self.img + mask[..., None] * col * k

    def screen(self, mask, color, k=1.0):
        col = color if np.ndim(color) == 3 else np.asarray(color, np.float32)[None, None, :]
        c = np.clip(mask[..., None] * col * k, 0.0, 1.0)
        self.img = 1.0 - (1.0 - np.clip(self.img, 0.0, 1.0)) * (1.0 - c)

    def multiply(self, mask, color, k=1.0):
        col = color if np.ndim(color) == 3 else np.asarray(color, np.float32)[None, None, :]
        m = np.clip(mask * k, 0.0, 1.0)[..., None]
        self.img = self.img * (1.0 - m + m * col)

    def vgrad(self, stops, y0=0.0, y1=None):
        """A colour array graded top to bottom through (t, colour) stops between rows y0 and y1."""
        y1 = self.h if y1 is None else y1
        t = np.clip((self.Y - y0) / max(1e-6, (y1 - y0)), 0.0, 1.0)
        out = np.zeros((self.h, self.w, 3), np.float32)
        out[:] = stops[0][1]
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            k = np.clip((t - t0) / max(1e-6, (t1 - t0)), 0.0, 1.0)[..., None]
            inside = ((t >= t0) & (t <= t1))[..., None]
            out = np.where(inside, c0 + (c1 - c0) * k, out)
        out = np.where((t > stops[-1][0])[..., None], stops[-1][1], out)
        return out

    def hgrad(self, stops, x0=0.0, x1=None):
        x1 = self.w if x1 is None else x1
        t = np.clip((self.X - x0) / max(1e-6, (x1 - x0)), 0.0, 1.0)
        out = np.zeros((self.h, self.w, 3), np.float32)
        out[:] = stops[0][1]
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            k = np.clip((t - t0) / max(1e-6, (t1 - t0)), 0.0, 1.0)[..., None]
            inside = ((t >= t0) & (t <= t1))[..., None]
            out = np.where(inside, c0 + (c1 - c0) * k, out)
        return out

    def radial(self, cx, cy, r, ry=None, power=2.0):
        """1 at the centre falling to 0 at radius r (an ellipse when ry is given)."""
        ry = r if ry is None else ry
        d = np.sqrt(((self.X - cx) / r) ** 2 + ((self.Y - cy) / ry) ** 2)
        return np.clip(1.0 - d, 0.0, 1.0) ** power

    def glow(self, cx, cy, r, color, k=1.0, power=2.0, ry=None):
        self.add(self.radial(cx, cy, r, ry, power), color, k)

    def dots(self, pts, color, k=1.0, glow_r=0.0, glow_k=0.0):
        """Single bright points (sub-pixel positions spread over their neighbours) with an optional halo."""
        acc = np.zeros((self.h, self.w), np.float32)
        for x, y, s in pts:
            ix = int(math.floor(x - 0.5))
            iy = int(math.floor(y - 0.5))
            fx = x - 0.5 - ix
            fy = y - 0.5 - iy
            for dx, dy, wgt in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy),
                                (1, 1, fx * fy)):
                px = ix + dx
                py = iy + dy
                if 0 <= px < self.w and 0 <= py < self.h:
                    acc[py, px] += wgt * s
        self.add(np.clip(acc, 0, 1.5), color, k)
        if glow_r > 0 and glow_k > 0:
            for x, y, s in pts:
                self.glow(x, y, glow_r, color, glow_k * s, power=2.0)

    def blur_mask(self, mask, radius):
        from PIL import ImageFilter
        im = Image.fromarray(np.clip(mask * 255, 0, 255).astype(np.uint8), "L")
        return np.asarray(im.filter(ImageFilter.GaussianBlur(radius)), np.float32) / 255.0

    def blurred(self, radius):
        from PIL import ImageFilter
        im = Image.fromarray(np.clip(self.img * 255, 0, 255).astype(np.uint8), "RGB")
        return np.asarray(im.filter(ImageFilter.GaussianBlur(radius)), np.float32) / 255.0

    # ------------------------------------------------------------------ finishing

    def bloom(self, threshold=0.7, radius=6.0, k=0.6):
        bright = np.clip(self.img - threshold, 0.0, None)
        from PIL import ImageFilter
        im = Image.fromarray(np.clip(bright / max(1e-6, bright.max()) * 255, 0, 255).astype(np.uint8), "RGB")
        blurred = np.asarray(im.filter(ImageFilter.GaussianBlur(radius)), np.float32) / 255.0 * bright.max()
        self.img = self.img + blurred * k

    def finish(self, exposure=1.0, saturation=1.05, vignette=0.35, grain=0.012, lift=0.0):
        img = self.img * exposure
        # soft shoulder instead of a hard clip, so bright glows keep their colour
        img = 1.0 - np.exp(-img * 1.25)
        img = img / (1.0 - math.exp(-1.25))
        lum = (img * np.array([0.299, 0.587, 0.114], np.float32)).sum(axis=2, keepdims=True)
        img = lum + (img - lum) * saturation
        if vignette > 0:
            d = np.sqrt(((self.X - self.w / 2) / (self.w / 2)) ** 2 * 0.8 + ((self.Y - self.h / 2) / (self.h / 2)) ** 2
                        * 0.6)
            img = img * (1.0 - vignette * smoothstep(0.55, 1.35, d))[..., None]
        if grain > 0:
            img = img + (self.rng.random((self.h, self.w, 1)).astype(np.float32) - 0.5) * grain
        img = lift + img * (1.0 - lift)
        return Image.fromarray(np.clip(img * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGB")


def jitter_line(rng, x0, y0, x1, y1, steps, amount):
    """A wobbling polyline from (x0, y0) to (x1, y1)."""
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        if 0 < i < steps:
            x += rng.normal(0, amount)
            y += rng.normal(0, amount)
        pts.append((x, y))
    return pts
