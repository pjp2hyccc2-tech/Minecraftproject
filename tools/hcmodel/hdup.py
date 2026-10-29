"""HD helpers for pixel-art designs: smart upscaling and "replay" of 1x pixel operations onto an HD texture.

Many generators (village NPC faces, companion faces, small-monster eye stamps, armor trims) draw pixel art at
model-pixel (1x) resolution.  In HD mode the textures are TEXEL x bigger, so those operations are replayed:
the HD image is box-filtered down to a 1x view, the pixel function runs on that view, and every texel it
changed is re-applied on the HD image:

  * colour changes become per-colour layers with anti-aliased, rounded shapes (a 1x dot becomes a round
    rivet / pupil, a 1x line a soft-edged stroke; shapes of one colour keep their identity)
  * uniform multiplicative changes (e.g. `a[...] *= 0.72` pleats) scale the HD texels instead
  * alpha cut-outs are applied with rounded, anti-aliased edges
"""
import numpy as np

try:
    from scipy import ndimage
except Exception:  # pragma: no cover
    ndimage = None


def downsample(img, K):
    """HD RGBA (float or uint8) -> 1x float RGBA: alpha-weighted colour average, alpha = coverage * 255."""
    a = np.asarray(img, float)
    H, W = a.shape[:2]
    h, w = H // K, W // K
    blk = a[:h * K, :w * K].reshape(h, K, w, K, 4)
    alpha = blk[..., 3] > 0
    cnt = alpha.sum(axis=(1, 3))
    rgb = (blk[..., :3] * alpha[..., None]).sum(axis=(1, 3)) / np.maximum(cnt, 1)[..., None]
    out = np.zeros((h, w, 4))
    out[..., :3] = rgb
    out[..., 3] = cnt / float(K * K) * 255.0
    return out


def coverage(mask, K, radius=0.36, aa=0.14):
    """1x boolean mask -> HD soft coverage (0..1) with rounded corners and anti-aliased edges."""
    m = np.kron(mask.astype(float), np.ones((K, K)))
    if ndimage is None or K < 2:
        return m
    b = ndimage.gaussian_filter(m, radius * K)
    return np.clip((b - (0.5 - aa)) / (2 * aa), 0, 1)


def layers_to_hd(colours, masks, K):
    """Composites per-colour 1x masks into an HD colour field + coverage: (rgb (H,W,3), cov (H,W))."""
    h, w = masks[0].shape
    rgb = np.zeros((h * K, w * K, 3))
    tot = np.zeros((h * K, w * K))
    for c, m in zip(colours, masks):
        cv = coverage(m, K)
        rgb += cv[..., None] * np.asarray(c, float)[None, None, :]
        tot += cv
    rgb = rgb / np.maximum(tot, 1e-6)[..., None]
    return rgb, np.clip(tot, 0, 1)


def _unique_colours(arr, sel):
    cols = np.round(arr[sel][:, :3]).astype(int)
    keys = cols[:, 0] * 65536 + cols[:, 1] * 256 + cols[:, 2]
    uniq, inv = np.unique(keys, return_inverse=True)
    full = np.full(arr.shape[:2], -1, int)
    full[sel] = inv
    return [(int(k // 65536), int((k // 256) % 256), int(k % 256)) for k in uniq], full


def replay(img, glow, fn, K, round_shapes=True):
    """Runs fn(a1x, g1x) (RGBA float arrays at 1x; g1x may be ignored) and applies its changes to the HD
    arrays img / glow in place.  Returns the number of changed 1x texels."""
    a1 = downsample(img, K)
    g1 = downsample(glow, K) if glow is not None else np.zeros_like(a1)
    a1c, g1c = a1.copy(), g1.copy()
    fn(a1c, g1c)
    H, W = img.shape[:2]
    h, w = a1.shape[:2]
    hd_alpha = img[..., 3] > 0
    # --- alpha cut-outs
    cut = (a1c[..., 3] <= 0) & (a1[..., 3] > 0)
    if cut.any():
        keep = coverage(~cut, K) if round_shapes else np.kron((~cut).astype(float), np.ones((K, K)))
        img[:h * K, :w * K, 3] = np.minimum(img[:h * K, :w * K, 3], keep * 255)
        if glow is not None:
            glow[:h * K, :w * K, 3] = np.minimum(glow[:h * K, :w * K, 3], keep * 255)
    # --- colour changes
    diff = np.abs(a1c[..., :3] - a1[..., :3]).max(-1)
    changed = (diff > 0.75) & (a1c[..., 3] > 0) & ~cut
    n = int(changed.sum())
    if n:
        # multiplicative changes (same ratio on all channels) scale the HD texels
        ratio = a1c[..., :3] / np.maximum(a1[..., :3], 1.0)
        rmean = ratio.mean(-1)
        mult = changed & (np.abs(ratio - rmean[..., None]).max(-1) < 0.04) & (rmean > 0.25) & (rmean < 1.6) & (a1[..., :3].min(-1) > 8)
        if mult.any():
            cv = coverage(mult, K, radius=0.25, aa=0.2)
            scale = np.kron(np.where(mult, rmean, 1.0), np.ones((K, K)))
            k = 1 + (scale - 1) * cv
            img[:h * K, :w * K, :3] *= k[..., None]
        absm = changed & ~mult
        if absm.any():
            cols, idx = _unique_colours(a1c, absm)
            masks = [idx == i for i in range(len(cols))]
            rgb, cov = layers_to_hd(cols, masks, K) if round_shapes else (
                np.kron(a1c[..., :3], np.ones((K, K, 1))), np.kron(absm.astype(float), np.ones((K, K))))
            cov = cov * hd_alpha[:h * K, :w * K]
            # a subtle 1-texel crevice shadow around the overlay keeps it readable against the HD surface
            if ndimage is not None and K >= 2:
                ring = np.clip(ndimage.gaussian_filter(cov, 0.45 * K) * 1.6 - cov, 0, 1) * 0.25
                img[:h * K, :w * K, :3] *= (1 - ring)[..., None]
            region = img[:h * K, :w * K, :3]
            region[:] = region * (1 - cov[..., None]) + rgb * cov[..., None]
            img[:h * K, :w * K, 3] = np.where(cov > 0.5, 255, img[:h * K, :w * K, 3])
            if glow is not None:
                # a plain (non-glowing) overlay removes the glow under it; glowing overlays are handled below
                gchanged = np.abs(g1c - g1).max(-1) > 0.75
                plain = absm & ~gchanged & (g1[..., 3] > 0)
                if plain.any():
                    pc = coverage(plain, K)
                    glow[:h * K, :w * K] *= (1 - pc)[..., None]
    # --- glow changes (absolute colours only)
    if glow is not None:
        gdiff = np.abs(g1c - g1).max(-1)
        gset = (gdiff > 0.75) & (g1c[..., 3] > 0)
        gclear = (gdiff > 0.75) & (g1c[..., 3] <= 0) & (g1[..., 3] > 0)
        if gclear.any():
            pc = coverage(gclear, K)
            glow[:h * K, :w * K] *= (1 - pc)[..., None]
        if gset.any():
            cols, idx = _unique_colours(g1c, gset)
            masks = [idx == i for i in range(len(cols))]
            rgb, cov = layers_to_hd(cols, masks, K)
            cov = cov * hd_alpha[:h * K, :w * K]
            region = glow[:h * K, :w * K, :3]
            region[:] = region * (1 - cov[..., None]) + rgb * cov[..., None]
            glow[:h * K, :w * K, 3] = np.maximum(glow[:h * K, :w * K, 3], cov * 255)
    return n


def smooth_steps(rgb, alpha, K, sigma_c=26.0, radius=None):
    """Edge-aware (bilateral) smoothing of an HD image made of K x K blocks: small colour steps become
    gradients, large steps (outlines) stay crisp.  rgb (H,W,3) float, alpha (H,W) bool."""
    radius = int(round(0.9 * K)) if radius is None else radius
    H, W = rgb.shape[:2]
    acc = np.zeros_like(rgb)
    wsum = np.zeros((H, W))
    pad = radius
    rp = np.pad(rgb, ((pad, pad), (pad, pad), (0, 0)), mode='edge')
    ap = np.pad(alpha.astype(float), pad, mode='constant')
    ss = 0.55 * K
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            sh = rp[pad + dy:pad + dy + H, pad + dx:pad + dx + W]
            sa = ap[pad + dy:pad + dy + H, pad + dx:pad + dx + W]
            dc = np.abs(sh - rgb).max(-1)
            wgt = np.exp(-(dx * dx + dy * dy) / (2 * ss * ss)) * np.exp(-(dc / sigma_c) ** 2) * sa
            acc += sh * wgt[..., None]
            wsum += wgt
    out = acc / np.maximum(wsum, 1e-6)[..., None]
    return np.where(alpha[..., None], out, rgb)


def grain(shape, K, seed=0, amp=0.03):
    """Fine multiplicative grain (two octaves) for flat HD areas; returns (H,W) multiplier around 1."""
    from .paint import vnoise2
    H, W = shape
    J, I = np.mgrid[0:H, 0:W]
    X, Y = (I + 0.5) / K, (J + 0.5) / K
    n = (vnoise2(X * 1.3, Y * 1.3, seed) - 0.5) * 0.6 + (vnoise2(X * 3.1, Y * 3.1, seed + 5) - 0.5) * 0.4
    return 1 + n * amp * 2
