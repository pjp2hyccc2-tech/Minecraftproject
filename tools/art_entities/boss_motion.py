"""Shared helpers of the boss rigs' clips: biped legs by IK, muzzle export and the build step that runs the
secondary-motion pass. Every boss rig module defines its own geometry, chains and clips."""
import eranim
from eranim import Leg


class Ctx:
    """The rig and look a set of clips is authored on, with its two IK legs (None for a rig without feet)."""

    def __init__(self, rig, variant, legs=None):
        self.rig, self.v = rig, variant
        self.R = Leg(rig, *legs[0]) if legs else None
        self.L = Leg(rig, *legs[1]) if legs else None


def feet(c, x, right_plan=(), left_plan=(), right_holds=(), left_holds=(), y_off=0.0, samples=None):
    """Both legs by IK: each foot follows its step plan (planted otherwise)."""
    for leg, plan, holds in ((x.R, right_plan, right_holds), (x.L, left_plan, left_holds)):
        eranim.plant(c, x.rig, x.v, leg, eranim.steps(leg, list(plan), y_off=y_off),
                     eranim.step_pitch(list(plan), holds=list(holds)), samples=samples)


def gait(c, x, gait_def, phases=(0.0, 0.5), samples=24):
    T = gait_def.length
    for leg, phase in zip((x.R, x.L), phases):
        eranim.solve_leg(c, x.rig, x.v, leg, [T * i / samples for i in range(samples + 1)], gait_def.ankle(leg, phase),
                         gait_def.foot_pitch(phase))
    c.footsteps = [(round(T * (phase % 1.0), 4), leg.side, -gait_def.stride / 2.0) for leg, phase in
                   zip((x.R, x.L), phases)]


def muzzle(rig_obj, v, an, part, point):
    """{left, up, forward} in blocks at scale 1 of a point at the clip's impact frame."""
    p = eranim.world_point(rig_obj, v, an, an.impact, part, point)
    return (p[0] / 16.0, (24.0 - p[1]) / 16.0, -p[2] / 16.0)


def build(clip, x, chains):
    """Runs the secondary pass and carries the clip's extra data (footsteps, muzzle) over to the Anim."""
    an = clip.build(x.rig, x.v, chains)
    if hasattr(clip, 'footsteps'):
        an.footsteps = clip.footsteps
    if hasattr(clip, 'muzzle_part'):
        an.muzzle = muzzle(x.rig, x.v, an, clip.muzzle_part, clip.muzzle_point)
    return an


def local(r, bone, point):
    pv = r.parts[bone].pivot
    return tuple(round(point[i] - pv[i], 3) for i in range(3))


def finish_rig(r, walk_speed, trails):
    r.walk_speed = walk_speed
    r.trails = trails
    r.trails_for = lambda v: [(b, local(r, b, base), local(r, b, tip)) for b, base, tip in trails]
    return r
