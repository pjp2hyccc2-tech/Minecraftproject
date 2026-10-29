"""Simulates the 2D surface height of the three quest maps (same formulas as gen_worldgen.py) and prints
statistics (water/lava fraction, height range, slopes, biome shares) and writes preview PNGs to tools/preview/."""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from mcnoise import Normal
import gen_worldgen as G

N = 448
xs, zs = np.meshgrid(np.arange(N, dtype=np.float64) + 1024 - 224, np.arange(N, dtype=np.float64) + 1024 - 224)


def ev(expr, noises, y=0.0):
    t = expr['type'] if isinstance(expr, dict) else None
    if not isinstance(expr, dict):
        return np.full(xs.shape, float(expr))
    if t == 'minecraft:noise':
        n = noises[expr['noise']]
        return n.value(xs * expr['xz_scale'], np.full(xs.shape, y * expr['y_scale']), zs * expr['xz_scale'])
    if t == 'minecraft:add':
        return ev(expr['argument1'], noises, y) + ev(expr['argument2'], noises, y)
    if t == 'minecraft:mul':
        return ev(expr['argument1'], noises, y) * ev(expr['argument2'], noises, y)
    if t == 'minecraft:clamp':
        return np.clip(ev(expr['input'], noises, y), expr['min'], expr['max'])
    if t == 'minecraft:abs':
        return np.abs(ev(expr['argument'], noises, y))
    if t == 'minecraft:square':
        a = ev(expr['argument'], noises, y); return a * a
    if t in ('minecraft:interpolated', 'minecraft:cache_2d', 'minecraft:flat_cache'):
        return ev(expr['argument'], noises, y)
    if t == 'minecraft:y_clamped_gradient':
        fy, ty, fv, tv = expr['from_y'], expr['to_y'], expr['from_value'], expr['to_value']
        tt = np.clip((y - fy) / (ty - fy), 0, 1)
        return np.full(xs.shape, fv + (tv - fv) * tt)
    raise ValueError(t)


def main():
    os.makedirs(os.path.join(G.ROOT, 'tools', 'preview'), exist_ok=True)
    for m in G.MAPS:
        for seed in (1, 2, 3, 4, 5):
            noises = {k: Normal(hash((seed, k)) & 0xffffffff, v['firstOctave'], v['amplitudes']) for k, v in G.NOISES.items()}
            h = ev(m['h'], noises)
            H = m['B'] + m['S'] * h
            sea = m['sea']
            gy, gx = np.gradient(H)
            slope = np.hypot(gx, gy)
            temp = ev(m['router']['temperature'], noises) if isinstance(m['router']['temperature'], dict) else np.zeros_like(H)
            print(f"{m['id']:16s} seed{seed}: H {H.min():6.1f}..{H.max():6.1f} mean {H.mean():6.1f}  below sea {100*(H < sea).mean():5.1f}%  "
                  f"slope p50 {np.percentile(slope,50):.2f} p90 {np.percentile(slope,90):.2f} p99 {np.percentile(slope,99):.2f} >1.5: {100*(slope>1.5).mean():.1f}%  "
                  f"temp {temp.min():.2f}..{temp.max():.2f} std {temp.std():.2f}")
            if seed == 1:
                img = np.zeros((N, N, 3), np.uint8)
                t = np.clip((H - 40) / 170, 0, 1)
                for c in range(3):
                    img[..., c] = (np.array(m['colors'][0])[c] * (1 - t) + np.array(m['colors'][1])[c] * t).astype(np.uint8)
                img[H < sea] = m['fluid']
                img[slope > 1.5] = (img[slope > 1.5] * 0.55).astype(np.uint8)
                Image.fromarray(img).save(os.path.join(G.ROOT, 'tools', 'preview', f"terrain_{m['id']}.png"))


if __name__ == '__main__':
    main()
