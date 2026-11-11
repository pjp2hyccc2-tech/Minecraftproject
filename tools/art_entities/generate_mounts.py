#!/usr/bin/env python3
"""ETERNAL REALM mount art pipeline (deterministic; needs Pillow and numpy). Companion of generate.py for the mount
rigs of rig_mounts.py: the Moonmane Charger, the Azurewing Griffin, the Starwheel Arcbike, and the five horse and five
griffin breeds (rig_horse_breeds.py, rig_griffin_breeds.py).

Run from anywhere:  python3 tools/art_entities/generate_mounts.py [--no-preview] [--compare DIR] [--only RIG,...]

Writes
  src/main/resources/assets/eternalrealm/textures/entity/mount_<kind>/<coat>.png (+ <coat>_glow.png)
  src/main/java/com/eternalrealm/client/entity/model/gen/<Kind>MountGeometry.java / <Kind>MountAnimations.java
  src/main/java/com/eternalrealm/client/entity/model/gen/MountRigs.java   (every kind's rig tables, layer and the
      stable's preview scale: client)
  src/main/java/com/eternalrealm/mount/MountBodies.java   (model scale, hitbox, seat height and position, wheel
      radius: pure, for the server's riding code)
  tools/art_entities/previews/mounts.png (the first three from four sides), previews/mounts_anim.png (their animation
  frames), previews/mount_breeds.png (every breed in every coat, by day and by night) and
  previews/mount_breeds_anim.png (the breeds' animation frames)
  with --compare DIR: DIR/compare_<kind>.png, each mount rendered next to DIR/ref_<kind>.png if that picture exists
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import ermodel  # noqa: E402
import generate  # noqa: E402
import rig_mounts  # noqa: E402

ROOT = generate.ROOT
BODIES = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'eternalrealm', 'mount', 'MountBodies.java')
RIGS_JAVA = os.path.join(generate.GEN_DIR, 'MountRigs.java')
KINDS = {'mount_horse': 'HORSE', 'mount_griffin': 'GRIFFIN', 'mount_bike': 'BIKE',
         'mount_horse_bloom': 'HORSE_BLOOM', 'mount_horse_tide': 'HORSE_TIDE', 'mount_horse_gale': 'HORSE_GALE',
         'mount_horse_blaze': 'HORSE_BLAZE', 'mount_horse_halo': 'HORSE_HALO',
         'mount_griffin_gold': 'GRIFFIN_GOLD', 'mount_griffin_owl': 'GRIFFIN_OWL', 'mount_griffin_storm': 'GRIFFIN_STORM',
         'mount_griffin_frost': 'GRIFFIN_FROST', 'mount_griffin_void': 'GRIFFIN_VOID'}
FIRST = ('mount_horse', 'mount_griffin', 'mount_bike')
REFS = {'mount_horse': 'ref_horse.png', 'mount_griffin': 'ref_bird.png', 'mount_bike': 'ref_bike.png'}
# the stable's cards show a horse at 27 and a griffin (wings spread) at 21 pixels per block
PREVIEW_SCALE = {'horse': 27.0, 'griffin': 21.0, 'bike': 50.0}


def build(only=None):
    out = []
    for rig in rig_mounts.rigs():
        if only and rig.name not in only:
            continue
        ermodel.pack(rig)
        out.append((rig, generate.paint_rig(rig)))
    return out


def family(rig):
    return rig.name.split('_')[1]


def preview_extent(rig):
    """How big the model looks in the stable's turning preview (blocks): the larger of its height and the diagonal
    of its footprint, in the pose the stable shows (a griffin with its wings spread, as in a glide)."""
    v = rig.variants[0]
    pose = ermodel.evaluate([(rig.anims['glide'], 0.0)]) if family(rig) == 'griffin' else \
        ermodel.evaluate([(rig.anims['idle'], 0.0)])
    mats = ermodel.part_transforms(rig, v, pose)
    pts = []
    for part in rig.parts.values():
        if not ermodel.visible_chain(rig, v, part):
            continue
        m = mats[part.name]
        for c in part.cubes:
            for cx in (c.x, c.x + c.w):
                for cy in (c.y, c.y + c.h):
                    for cz in (c.z, c.z + c.d):
                        pts.append((m @ np.array([cx - part.pivot[0], cy - part.pivot[1], cz - part.pivot[2], 1.0]))[:3])
    a = np.array(pts)
    height = (24.0 - a[:, 1].min()) / 16.0
    footprint = math.hypot(a[:, 0].max() - a[:, 0].min(), a[:, 2].max() - a[:, 2].min()) / 16.0
    return max(height, footprint * 0.62)


def preview_scales(rigs):
    """Each kind's pixels per block in the stable: its family's, smaller for a breed that is bigger than the first of
    its family (antlers, a halo, longer wings), never larger."""
    extents = {rig.name: preview_extent(rig) for rig, _ in rigs}
    out = {}
    for rig, _ in rigs:
        fam = family(rig)
        first = extents.get('mount_' + fam, extents[rig.name])
        out[rig.name] = round(float(PREVIEW_SCALE[fam] * min(1.0, first / extents[rig.name])), 1)
    return out


def measure(rig):
    """Seat height and position, hitbox and wheel radius in blocks (scaled), from the rest pose."""
    v = rig.variants[0]
    scale = float(v.extra.get('scale', 1.0))
    seat = v.extra['seat']
    if isinstance(seat, tuple):
        part, tag = seat
        cube = next(c for c in rig.parts[part].cubes if c.tag == tag)
    else:
        cube = rig.parts[seat].cubes[0]
    width, height = v.extra['hitbox']
    return dict(scale=scale, width=width, height=height,
                seat_height=round((24.0 - cube.y) / 16.0 * scale, 4),
                seat_forward=round(-(cube.z + cube.d / 2.0) / 16.0 * scale, 4),
                wheel_radius=round(float(v.extra.get('wheel_radius', 0.0)) / 16.0 * scale, 4))


def write_bodies(rigs):
    jf = ermodel.jf
    values = {rig.name: measure(rig) for rig, _ in rigs}
    L = []
    w = L.append
    w('// GENERATED by tools/art_entities/generate_mounts.py. Do not edit: change the script and re-run.')
    w('package com.eternalrealm.mount;')
    w('')
    w('/**')
    w(' * Body measures of the mount models (pure, for the server): model scale, hitbox, the seat (height above the')
    w(' * ground and how far ahead of the centre; the rider sits on it) and the bike\'s wheel radius, in blocks.')
    w(' */')
    w('public final class MountBodies {')
    w('    private MountBodies() {')
    w('    }')
    for method, key, typ in (('scale', 'scale', 'float'), ('width', 'width', 'float'), ('height', 'height', 'float'),
                             ('seatHeight', 'seat_height', 'double'), ('seatForward', 'seat_forward', 'double'),
                             ('wheelRadius', 'wheel_radius', 'double')):
        w('')
        w('    public static %s %s(MountKind kind) {' % (typ, method))
        w('        return switch (kind) {')
        for name, kind in KINDS.items():
            value = values[name][key]
            w('            case %s -> %s;' % (kind, jf(value) if typ == 'float' else repr(float(value))))
        w('        };')
        w('    }')
    w('}')
    with open(BODIES, 'w') as f:
        f.write('\n'.join(L) + '\n')
    return values


# ------------------------------------------------------------------------------------------------ previews

def lit(painter):
    """The body texture with the emissive layer added on top, as the game shows it at night (previews only)."""
    body = painter.img.astype(np.int32)
    glow = painter.glow.astype(np.int32)
    mask = glow[..., 3:4] > 0
    out = body.copy()
    out[..., :3] = np.where(mask, np.minimum(255, body[..., :3] * 0.35 + glow[..., :3] * 0.9), body[..., :3])
    return out.astype(np.uint8)


def view(rig, painted, yaw, pitch=14, ppu=6.0, pose=None, size=None, center=None, glow=False):
    v = rig.variants[0]
    texture = lit(painted[v.id]) if glow else painted[v.id].img
    return Image.fromarray(ermodel.render(rig, v, texture, pose=pose, yaw=yaw, pitch=pitch, ppu=ppu,
                                          size=size, center=center))


def turntable(rigs, path, ppu=5.0):
    rows = []
    for rig, painted in rigs:
        row = []
        for yaw in (-35, 0, 90, 145, 215):
            row.append((view(rig, painted, yaw, ppu=ppu), '%s yaw %d' % (rig.name, yaw) if yaw == -35 else ''))
        rows.append(row)
    generate.compose_rows(rows).save(path)


def anim_sheet(rigs, path, ppu=3.0, frames=6):
    rows = []
    for rig, painted in rigs:
        v = rig.variants[0]
        for slot in rig.slots:
            an = rig.slot_anim(v, slot)
            row = []
            for k in range(frames):
                t = an.length * k / frames if an.loop else an.length * k / (frames - 1)
                pose = ermodel.evaluate([(an, t)])
                row.append((view(rig, painted, -35, ppu=ppu, pose=pose, size=(int(ppu * 70), int(ppu * 50)),
                                 center=(0, 6)), '%s/%s' % (rig.name, an.name) if k == 0 else 't=%.2f' % t))
            rows.append(row)
    generate.compose_rows(rows).save(path)


def compare(rigs, directory):
    """Each mount at the reference picture's angle, next to the picture, on a dusk backdrop."""
    for rig, painted in rigs:
        if rig.name not in REFS:
            continue
        ref_path = os.path.join(directory, REFS[rig.name])
        kind = rig.name.split('_', 1)[1]
        shots = []
        if rig.name == 'mount_griffin':
            shots.append(view(rig, painted, -35, pitch=-4, ppu=7.0, glow=True,
                              pose=ermodel.evaluate([(rig.anims['flap'], 0.0)])))
            shots.append(view(rig, painted, 125, pitch=12, ppu=8.0, glow=True,
                              pose=ermodel.evaluate([(rig.anims['fold'], 0.0)])))
        elif rig.name == 'mount_horse':
            shots.append(view(rig, painted, 55, pitch=8, ppu=9.0, glow=True))
            shots.append(view(rig, painted, 235, pitch=12, ppu=7.0, glow=True,
                              pose=ermodel.evaluate([(rig.anims['gallop'], 0.1)])))
        else:
            shots.append(view(rig, painted, 60, pitch=8, ppu=9.0, glow=True))
            shots.append(view(rig, painted, 215, pitch=14, ppu=7.0, glow=True))
        height = max(s.height for s in shots)
        panel = []
        if os.path.exists(ref_path):
            ref = Image.open(ref_path).convert('RGBA')
            scale = height / ref.height
            panel.append(ref.resize((int(ref.width * scale), height), Image.LANCZOS))
        panel.extend(shots)
        width = sum(p.width for p in panel) + 12 * (len(panel) + 1)
        sheet = Image.new('RGBA', (width, height + 24), (34, 30, 52, 255))
        grad = np.linspace(0, 1, height + 24)[:, None]
        bg = (np.array([58, 50, 92]) * (1 - grad) + np.array([24, 22, 38]) * grad).astype(np.uint8)
        sheet.paste(Image.fromarray(np.repeat(bg[:, None, :], width, axis=1).reshape(height + 24, width, 3)), (0, 0))
        x = 12
        for p in panel:
            sheet.alpha_composite(p, (x, 12 + height - p.height))
            x += p.width + 12
        sheet.convert('RGB').save(os.path.join(directory, 'compare_%s.png' % kind))


def write_rig_registry(rigs, scales):
    """MountRigs.java (client): every kind's rig tables, its layer definition and its preview scale."""
    jf = ermodel.jf
    L = []
    w = L.append
    w('// GENERATED by tools/art_entities/generate_mounts.py. Do not edit: change the script and re-run.')
    w('package com.eternalrealm.client.entity.model.gen;')
    w('')
    w('import com.eternalrealm.client.entity.model.RigTables;')
    w('import com.eternalrealm.mount.MountKind;')
    w('import net.minecraft.client.model.geom.builders.LayerDefinition;')
    w('')
    w('/**')
    w(' * The model of every mount kind (client): its generated rig tables, its layer definition and how many pixels per')
    w(' * block the stable\'s turning preview gives it so the whole model fits a card.')
    w(' */')
    w('public final class MountRigs {')
    for rig, _ in rigs:
        g = rig.java_name + 'Geometry'
        w('    private static final RigTables %s = new RigTables("%s", %s.VARIANTS, %s.TOGGLED_PARTS, %s.VISIBLE,'
          % (KINDS[rig.name], rig.name, g, g, g))
        w('            %s.POSED_PARTS, %s.POSES, %s.SCALES, %s.GLOWS, %sAnimations::forVariant);'
          % (g, g, g, g, rig.java_name))
    w('')
    w('    private MountRigs() {')
    w('    }')
    w('')
    w('    public static RigTables tables(MountKind kind) {')
    w('        return switch (kind) {')
    for rig, _ in rigs:
        w('            case %s -> %s;' % (KINDS[rig.name], KINDS[rig.name]))
    w('        };')
    w('    }')
    w('')
    w('    public static LayerDefinition layer(MountKind kind) {')
    w('        return switch (kind) {')
    for rig, _ in rigs:
        w('            case %s -> %sGeometry.create(0.0F);' % (KINDS[rig.name], rig.java_name))
    w('        };')
    w('    }')
    w('')
    w('    /** Pixels per block of the stable\'s preview (the family\'s, less for a bigger breed). */')
    w('    public static float previewScale(MountKind kind) {')
    w('        return switch (kind) {')
    for rig, _ in rigs:
        w('            case %s -> %s;' % (KINDS[rig.name], jf(scales[rig.name])))
    w('        };')
    w('    }')
    w('')
    w('    /** Whether the kind\'s model has a glide pose (its fifth horse slot: the winged breed). */')
    w('    public static boolean glides(MountKind kind) {')
    gliders = [KINDS[rig.name] for rig, _ in rigs if family(rig) == 'horse' and 'glide' in rig.slots]
    w('        return %s;' % (' || '.join('kind == MountKind.%s' % k for k in gliders) or 'false'))
    w('    }')
    w('}')
    with open(RIGS_JAVA, 'w') as f:
        f.write('\n'.join(L) + '\n')


def breed_sheet(rigs, path, ppu=3.6):
    """Every breed in every coat (three quarters from the front) and its first coat at night and from behind."""
    rows = []
    for rig, painted in rigs:
        if rig.name in FIRST:
            continue
        pose = ermodel.evaluate([(rig.anims['fold'], 0.0)] if family(rig) == 'griffin' else [(rig.anims['idle'], 0.0)])
        row = []
        for v in rig.variants:
            row.append((Image.fromarray(ermodel.render(rig, v, painted[v.id].img, pose=pose, yaw=-35, pitch=12,
                                                       ppu=ppu)), '%s/%s' % (rig.name[6:], v.id)))
        v = rig.variants[0]
        row.append((Image.fromarray(ermodel.render(rig, v, lit(painted[v.id]), pose=pose, yaw=-35, pitch=12, ppu=ppu)),
                    'night'))
        row.append((Image.fromarray(ermodel.render(rig, v, painted[v.id].img, pose=pose, yaw=145, pitch=16, ppu=ppu)),
                    'behind'))
        rows.append(row)
    generate.compose_rows(rows).save(path)


def breed_anim_sheet(rigs, path, ppu=2.4, frames=4):
    rows = []
    for rig, painted in rigs:
        if rig.name in FIRST:
            continue
        v = rig.variants[0]
        row = []
        for slot in rig.slots:
            an = rig.slot_anim(v, slot)
            layers = [(rig.anims['idle'], 0.0)] if family(rig) == 'horse' and slot == 'glide' else []
            if family(rig) == 'griffin' and slot in ('flap', 'glide'):
                layers = []
            for k in range(frames):
                t = an.length * k / frames if an.loop else an.length * k / (frames - 1)
                pose = ermodel.evaluate(layers + [(an, t)])
                row.append((view(rig, painted, -35, ppu=ppu, pose=pose, size=(int(ppu * 74), int(ppu * 54)),
                                 center=(0, 6)), '%s/%s' % (rig.name[6:], slot) if k == 0 else ''))
        rows.append(row)
    generate.compose_rows(rows).save(path)


def main(argv):
    only = None
    if '--only' in argv:
        only = set(argv[argv.index('--only') + 1].split(','))
    rigs = build(only)
    for rig, _ in rigs:
        print('%s: %d parts %d boxes tex %dx%d (x%d), %d animations' % (
            rig.name, len(rig.parts), len(rig.cubes), rig.tex_w, rig.tex_h, rig.texel, len(rig.anims)))
    for rig, painted in rigs:
        generate.write_textures(rig, painted)
        generate.write_java(rig)
    if only:
        return
    values = write_bodies(rigs)
    for name, v in values.items():
        print('  %s %s' % (name, v))
    scales = preview_scales(rigs)
    print('  preview scales %s' % scales)
    write_rig_registry(rigs, scales)
    if '--compare' in argv:
        directory = argv[argv.index('--compare') + 1]
        os.makedirs(directory, exist_ok=True)
        compare(rigs, directory)
    if '--no-preview' in argv:
        return
    os.makedirs(generate.PREVIEWS, exist_ok=True)
    first = [(rig, painted) for rig, painted in rigs if rig.name in FIRST]
    turntable(first, os.path.join(generate.PREVIEWS, 'mounts.png'))
    anim_sheet(first, os.path.join(generate.PREVIEWS, 'mounts_anim.png'))
    breed_sheet(rigs, os.path.join(generate.PREVIEWS, 'mount_breeds.png'))
    breed_anim_sheet(rigs, os.path.join(generate.PREVIEWS, 'mount_breeds_anim.png'))


if __name__ == '__main__':
    main(sys.argv[1:])
