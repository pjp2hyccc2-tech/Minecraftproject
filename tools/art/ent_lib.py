"""Shared part builders and Java animation snippets for the monster specs."""
from ent_model import Part, Cube

# Java helpers available to every model (static, pure functions; translated for the previews)
HELPERS = '''
/** Smoothstep 0..1. */
private static float ease(float x) {
    float c = Mth.clamp(x, 0.0F, 1.0F);
    return c * c * (3.0F - 2.0F * c);
}

/** 1 -> 0 over 3 ticks once every period ticks (phase shifted): sudden twitches. t must be >= 0. */
private static float twitch(float t, float period, float phase) {
    float m = (t + phase) % period;
    return m < 3.0F ? 1.0F - m / 3.0F : 0.0F;
}

/** Stepped progress: holds still, then lurches to the next step (jerky motion), x in 0..1. */
private static float jerky(float x, float steps) {
    float c = Mth.clamp(x, 0.0F, 1.0F) * steps;
    float i = (float) Mth.floor(c);
    float f = c - i;
    float s = f < 0.55F ? 0.0F : ease((f - 0.55F) / 0.45F);
    return Math.min(1.0F, (i + s) / steps);
}

/** Curls a two-segment finger. */
private static void curl(ModelPart a, ModelPart b, float c1, float c2) {
    a.xRot = c1;
    b.xRot = c2;
}
'''


def hand(prefix, side, palm=(3, 3, 1), seg=(3, 3), fingers=3, thumb=True, mat='skin', nail='nail', spread=1.0):
    """A palm with `fingers` two-segment fingers hanging along +y and an optional thumb.
    side = -1 for the right hand (-x), +1 for the left."""
    pw, ph, pd = palm
    h = Part(f'{prefix}_hand', (0, 0, 0), cubes=[Cube((-pw / 2, 0, -pd / 2), palm, mat, tag=f'{prefix}_palm')])
    xs = [(-(fingers - 1) / 2 + i) * spread for i in range(fingers)]
    for i, fx in enumerate(xs):
        f1 = Part(f'{prefix}_finger{i + 1}', (fx, ph, 0), cubes=[Cube((-0.5, 0, -0.5), (1, seg[0], 1), mat, tag=f'{prefix}_f{i + 1}a')])
        f2 = Part(f'{prefix}_finger{i + 1}b', (0, seg[0], 0), cubes=[Cube((-0.5, 0, -0.5), (1, seg[1], 1), nail, tag=f'{prefix}_f{i + 1}b')])
        f1.add(f2)
        h.add(f1)
    if thumb:
        t = Part(f'{prefix}_thumb', (-side * pw / 2, 1, -0.3), (0, 0, side * 0.5),
                 cubes=[Cube((-0.5, 0, -0.5), (1, max(2, seg[0]), 1), nail, tag=f'{prefix}_thumb')])
        h.add(t)
    return h
