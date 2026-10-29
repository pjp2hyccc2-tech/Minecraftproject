"""Generic HD upscaler for hand-made pixel-art PNGs (block textures, projectile sprites, effect icons).

The pixel art stays the design: it is upscaled K x, neighbouring ramp steps are turned into gradients with an
edge-aware (bilateral) filter so outlines and colour borders stay crisp, the silhouette gets rounded,
anti-aliased edges, and a fine two-octave grain adds surface texture.  Alpha stays binary (cut-out safe).

    from hdpost import hd_upscale
    hd_upscale(path, K=4)                     # in place
    python3 tools/art/hdpost.py a.png b.png   # in place, K=4
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')))
from hcmodel.hdup import smooth_steps, grain, coverage  # noqa: E402


def hd_upscale_array(a, K=4, sigma_c=30.0, grain_amp=0.03, round_alpha=True, seed=0, blur=0.15):
    """uint8 / float RGBA (h, w, 4) at 1x -> float RGBA (h*K, w*K, 4)."""
    a = np.asarray(a, float)
    h, w = a.shape[:2]
    alpha1 = a[..., 3] > 0
    # colour: nearest upscale, then edge-aware smoothing (alpha-weighted so transparent texels do not bleed)
    rgb = np.kron(a[..., :3], np.ones((K, K, 1)))
    alpha = np.kron(alpha1, np.ones((K, K), bool))
    rgb = smooth_steps(rgb, alpha, K, sigma_c=sigma_c)
    try:
        from scipy import ndimage
        if blur > 0:
            af = alpha.astype(float)
            for c in range(3):
                num = ndimage.gaussian_filter(rgb[..., c] * af, blur * K / 4.0 * 2)
                den = ndimage.gaussian_filter(af, blur * K / 4.0 * 2)
                rgb[..., c] = np.where(alpha, num / np.maximum(den, 1e-6), rgb[..., c])
    except Exception:
        pass
    rgb = rgb * grain(rgb.shape[:2], K, seed=seed, amp=grain_amp)[..., None]
    out = np.zeros((h * K, w * K, 4), float)
    out[..., :3] = np.clip(rgb, 0, 255)
    if round_alpha and not alpha1.all():
        cov = coverage(alpha1, K, radius=0.2, aa=0.15)
        out[..., 3] = np.where(cov >= 0.5, 255, 0)
    else:
        # translucent sprites keep their alpha (smoothed between neighbouring alpha steps)
        al = np.kron(a[..., 3], np.ones((K, K)))
        try:
            from scipy import ndimage
            al = ndimage.gaussian_filter(al, 0.3 * K)
        except Exception:
            pass
        out[..., 3] = np.clip(al, 0, 255)
    out[~(out[..., 3] > 0), :3] = 0
    return out


def hd_upscale(path, K=4, **kw):
    """Upscales a 1x PNG in place (skips files that are already larger than 1x, judged by a size hint file)."""
    im = Image.open(path).convert('RGBA')
    out = hd_upscale_array(np.asarray(im), K, **kw)
    Image.fromarray(np.clip(np.round(out), 0, 255).astype(np.uint8), 'RGBA').save(path)
    return out


if __name__ == '__main__':
    for p in sys.argv[1:]:
        hd_upscale(p, 4)
        print('upscaled', p)
