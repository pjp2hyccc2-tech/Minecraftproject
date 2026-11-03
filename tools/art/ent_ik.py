"""Offline IK for authoring poses: finds joint angles that put a point of a part (e.g. a fingertip) at a target
position in model space (pixels, y down, model faces -z). The angles are then written into the Java anim code.

usage (from a spec module or a shell):
    from ent_ik import solve
    solve(spec, end='left_hand', end_offset=(0, 2.5, 0), target=(2.5, 11, -4),
          vary=[('left_arm', 'xRot'), ('left_arm', 'zRot'), ('left_forearm', 'xRot')],
          fixed={'body': {'xRot': 0.0}})
"""
import math

import numpy as np
from scipy.optimize import minimize

import render3d as R


def model_matrix(part):
    """Model-space matrix (pixels) of a posed part (its current x, y, z, rotations)."""
    chain = []
    p = part
    while p is not None:
        chain.append(p)
        p = p.parent
    M = np.eye(4)
    for q in reversed(chain):
        T = R.affine(None, [q.x, q.y, q.z])
        Rm = R.affine(R.rot_z(q.zRot) @ R.rot_y(q.yRot) @ R.rot_x(q.xRot))
        M = M @ T @ Rm
    return M


def point(part, offset):
    M = model_matrix(part)
    return (M @ np.array([offset[0], offset[1], offset[2], 1.0]))[:3]


def solve(spec, end, end_offset, target, vary, fixed=None, bounds=None, init=None, reg=0.02, verbose=True,
          also=None):
    """vary: [(part, attr)], fixed: {part: {attr: value}} applied first. also: extra [(part, offset, target, w)]
    goals. Returns {part.attr: value}."""
    parts = {p.name: p for p in spec.root.walk()}
    for p in parts.values():
        p.reset()
    for pn, attrs in (fixed or {}).items():
        for a, v in attrs.items():
            setattr(parts[pn], a, v)
    tgt = np.array(target, float)
    x0 = np.array(init if init is not None else [0.0] * len(vary), float)

    def f(x):
        for (pn, a), v in zip(vary, x):
            setattr(parts[pn], a, v)
        e = point(parts[end], end_offset) - tgt
        cost = float(e @ e) + reg * float(x @ x)
        for (pn, off, tg, w) in (also or []):
            e2 = point(parts[pn], off) - np.array(tg, float)
            cost += w * float(e2 @ e2)
        return cost
    best = None
    rng = np.random.default_rng(1)
    for k in range(12):
        start = x0 if k == 0 else x0 + rng.normal(0, 0.6, len(x0))
        res = minimize(f, start, method='L-BFGS-B', bounds=bounds)
        if best is None or res.fun < best.fun:
            best = res
    f(best.x)
    out = {f'{pn}.{a}': float(v) for (pn, a), v in zip(vary, best.x)}
    if verbose:
        err = point(parts[end], end_offset) - tgt
        print('IK', end, 'err %.3f px' % float(np.linalg.norm(err)), {k: round(v, 3) for k, v in out.items()})
    for p in parts.values():
        p.reset()
    return out
