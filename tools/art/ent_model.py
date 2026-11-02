"""Entity model framework (offline): one Python spec per monster -> packed box UVs, painted textures, the Java
model class (com.sounan.client.model.<Name>Model) and posed previews that run the translated setupAnim.

Coordinate rules are vanilla 1.20.1: model units are pixels, y points down, feet at y = 24, the model faces -z.
ModelPart.translateAndRotate = translate(x/16, y/16, z/16) * rotationZYX(zRot, yRot, xRot) * scale.
"""
import math
import re

import numpy as np

import render3d as R


class Cube:
    def __init__(self, origin, size, mat=None, grow=0.0, mirror=False, tag=None):
        self.origin = tuple(float(v) for v in origin)
        self.size = tuple(int(v) for v in size)
        assert all(abs(a - b) < 1e-9 for a, b in zip(size, self.size)), f'cube sizes must be integers: {size}'
        self.mat = mat
        self.grow = grow
        self.mirror = mirror
        self.tag = tag
        self.uv = None

    @property
    def footprint(self):
        w, h, d = self.size
        return 2 * d + 2 * w, d + h

    def regions(self):
        """face -> (u0, v0, w, h) in the box-UV layout (vanilla names: right = -x side, left = +x side)."""
        u, v = self.uv
        w, h, d = self.size
        return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rot = tuple(float(v) for v in rot)
        self.cubes = list(cubes)
        self.children = list(children)
        self.parent = None
        for c in self.children:
            c.parent = self
        self.reset()

    def add(self, *children):
        for c in children:
            c.parent = self
            self.children.append(c)
        return self

    def reset(self):
        self.x, self.y, self.z = self.pivot
        self.xRot, self.yRot, self.zRot = self.rot
        self.xScale = self.yScale = self.zScale = 1.0
        self.visible = True

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    @property
    def jname(self):
        parts = self.name.split('_')
        return parts[0] + ''.join(p.capitalize() for p in parts[1:])

    def matrix(self):
        M = R.affine(None, [self.x / 16.0, self.y / 16.0, self.z / 16.0])
        M = M @ R.affine(R.rot_z(self.zRot) @ R.rot_y(self.yRot) @ R.rot_x(self.xRot))
        if (self.xScale, self.yScale, self.zScale) != (1.0, 1.0, 1.0):
            M = M @ R.scale_m(self.xScale, self.yScale, self.zScale)
        return M


# ------------------------------------------------------------------------------------------------ UV packing
def pack(root, tex_w, tex_h=None, pad=0):
    cubes = [c for p in root.walk() for c in p.cubes]
    order = sorted(cubes, key=lambda c: (-c.footprint[1], -c.footprint[0]))
    W = tex_w
    for H in ([tex_h] if tex_h else [32, 64, 128, 256]):
        occ = np.zeros((H, W), bool)
        ok = True
        for c in order:
            fw, fh = c.footprint
            fw, fh = max(fw, 1) + pad, max(fh, 1) + pad
            placed = False
            for v in range(0, H - fh + 1):
                for u in range(0, W - fw + 1):
                    if not occ[v:v + fh, u:u + fw].any():
                        occ[v:v + fh, u:u + fw] = True
                        c.uv = (u, v)
                        placed = True
                        break
                if placed:
                    break
            if not placed:
                ok = False
                break
        if ok:
            return W, H
    raise ValueError('textures do not fit')


# ------------------------------------------------------------------------------------------------ Java
def jf(v):
    s = f'{float(v):.4f}'.rstrip('0')
    if s.endswith('.'):
        s += '0'
    return s + 'F'


def java_source(spec, tex_w, tex_h):
    root = spec.root
    name = spec.java_name
    parts = [p for p in root.walk() if p is not root]
    lines = []
    A = lines.append
    A('package com.sounan.client.model;')
    A('')
    A('import com.sounan.entity.Animated;')
    A('import net.minecraft.client.model.HierarchicalModel;')
    A('import net.minecraft.client.model.geom.ModelLayerLocation;')
    A('import net.minecraft.client.model.geom.ModelPart;')
    A('import net.minecraft.client.model.geom.PartPose;')
    if any(c.grow for p in parts for c in p.cubes):
        A('import net.minecraft.client.model.geom.builders.CubeDeformation;')
    A('import net.minecraft.client.model.geom.builders.CubeListBuilder;')
    A('import net.minecraft.client.model.geom.builders.LayerDefinition;')
    A('import net.minecraft.client.model.geom.builders.MeshDefinition;')
    A('import net.minecraft.client.model.geom.builders.PartDefinition;')
    if spec.translucent:
        A('import net.minecraft.client.renderer.RenderType;')
    A('import net.minecraft.resources.ResourceLocation;')
    A('import net.minecraft.util.Mth;')
    A('import net.minecraft.world.entity.Mob;')
    A('')
    A('/**')
    for l in spec.doc.strip().splitlines():
        A((' * ' + l.strip()).rstrip())
    A(' *')
    A(' * <p>Generated by tools/art/gen_entities.py (spec tools/art/' + spec.module + '.py) - edit the spec, not this file.')
    A(' */')
    A(f'public class {name}<T extends Mob & Animated> extends HierarchicalModel<T> {{')
    A(f'    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("sounan", "{spec.id}"), "main");')
    for pname, pid in spec.poses:
        A(f'    public static final int {pname} = {pid};')
    A('')
    A('    private final ModelPart root;')
    for p in parts:
        A(f'    private final ModelPart {p.jname};')
    A('')
    A(f'    public {name}(ModelPart root) {{')
    if spec.translucent:
        A('        super(RenderType::entityTranslucent);')
    A('        this.root = root;')
    for p in parts:
        par = 'root' if p.parent is root else f'this.{p.parent.jname}'
        A(f'        this.{p.jname} = {par}.getChild("{p.name}");')
    A('    }')
    A('')
    A('    public static LayerDefinition createBodyLayer() {')
    A('        MeshDefinition mesh = new MeshDefinition();')
    A('        PartDefinition root = mesh.getRoot();')
    for p in parts:
        par = 'root' if p.parent is root else 'p' + p.parent.jname[0].upper() + p.parent.jname[1:]
        cl = 'CubeListBuilder.create()'
        for c in p.cubes:
            u, v = c.uv
            ox, oy, oz = c.origin
            w, h, d = c.size
            cl += f'.texOffs({u}, {v})'
            if c.mirror:
                cl += '.mirror()'
            if c.grow:
                cl += f'.addBox({jf(ox)}, {jf(oy)}, {jf(oz)}, {jf(w)}, {jf(h)}, {jf(d)}, new CubeDeformation({jf(c.grow)}))'
            else:
                cl += f'.addBox({jf(ox)}, {jf(oy)}, {jf(oz)}, {jf(w)}, {jf(h)}, {jf(d)})'
            if c.mirror:
                cl += '.mirror(false)'
        px_, py_, pz_ = p.pivot
        rx, ry, rz = p.rot
        if (rx, ry, rz) == (0, 0, 0):
            pose = 'PartPose.ZERO' if (px_, py_, pz_) == (0, 0, 0) else f'PartPose.offset({jf(px_)}, {jf(py_)}, {jf(pz_)})'
        else:
            pose = f'PartPose.offsetAndRotation({jf(px_)}, {jf(py_)}, {jf(pz_)}, {jf(rx)}, {jf(ry)}, {jf(rz)})'
        call = f'{par}.addOrReplaceChild("{p.name}", {cl}, {pose});'
        if p.children:
            var = 'p' + p.jname[0].upper() + p.jname[1:]
            A(f'        PartDefinition {var} = {call}')
        else:
            A(f'        {call}')
    A(f'        return LayerDefinition.create(mesh, {tex_w}, {tex_h});')
    A('    }')
    A('')
    A('    @Override')
    A('    public ModelPart root() {')
    A('        return this.root;')
    A('    }')
    A('')
    A('    @Override')
    A('    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {')
    A('        this.root().getAllParts().forEach(ModelPart::resetPose);')
    for l in spec.anim.strip('\n').splitlines():
        A(('        ' + l) if l.strip() else '')
    A('    }')
    if spec.helpers.strip():
        A('')
        for l in spec.helpers.strip('\n').splitlines():
            A(('    ' + l) if l.strip() else '')
    A('}')
    return '\n'.join(lines) + '\n'


# ------------------------------------------------------------------------------------------------ posing / render
class FakeEntity:
    def __init__(self, pose, pose_time, left=False, ident=7):
        self._pose = pose
        self._t = pose_time
        self._left = left
        self._id = ident

    def pose(self):
        return self._pose

    def poseTime(self, partial):
        return self._t

    def isLeftHanded(self):
        return self._left

    def getId(self):
        return self._id


def pose_model(spec, anim_fn, pose, pose_time, age, limb_swing=0.0, limb_amount=0.0, head_yaw=0.0, head_pitch=0.0, left=False):
    parts = {}
    for p in spec.root.walk():
        p.reset()
        if p is not spec.root:
            parts[p.jname] = p
    anim_fn(parts, FakeEntity(pose, pose_time, left), limb_swing, limb_amount, age, head_yaw, head_pitch)


def draw_entity(cv, spec, tex, eyes, M_world, eye_pos, alpha=1.0, eyes_only=False):
    def rec(p, M):
        Mp = M @ p.matrix()
        if not p.visible:
            return
        for c in p.cubes:
            ox, oy, oz = c.origin
            w, h, d = c.size
            for (dname, verts, uvs) in R.box_polys(ox, oy, oz, w, h, d, c.uv[0], c.uv[1], c.mirror, c.grow):
                pts = R.apply(Mp, np.array(verts, float) / 16.0)
                th, tw = tex.shape[:2]
                uv = np.array(uvs, float) / np.array([spec.tex_w, spec.tex_h])
                n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
                if np.linalg.norm(n) < 1e-12:
                    n = np.cross(pts[2] - pts[1], pts[3] - pts[1])
                # orientation-free shading: light the side facing the camera
                nn = n if np.dot(n, eye_pos - pts.mean(0)) > 0 else -n
                shade = R.entity_shade(nn)
                if not eyes_only:
                    cv.quad(pts, uv, tex, shade=shade, mode='translucent' if spec.translucent else 'cutout', alpha=alpha)
                if eyes is not None:
                    cv.quad(pts, uv, eyes, shade=1.0, mode='additive')
        for ch in p.children:
            rec(ch, Mp)
    rec(spec.root, M_world)


def entity_world(yaw_deg=0.0, pos=(0, 0, 0)):
    """LivingEntityRenderer: Y(180 - bodyYaw) . scale(-1,-1,1) . translate(0,-1.501,0)."""
    return (R.affine(None, pos) @ R.affine(R.rot_y(math.radians(180.0 - yaw_deg))) @ R.scale_m(-1, -1, 1)
            @ R.affine(None, [0, -1.501, 0]))


class Spec:
    """Base class for monster specs (subclass sets id, java_name, poses, doc, anim, helpers, build(), paint())."""
    id = 'x'
    java_name = 'XModel'
    module = 'ent_x'
    translucent = False
    tex_w = 64
    tex_h = None
    poses = []
    doc = ''
    anim = ''
    helpers = ''

    def __init__(self):
        self.root = Part('root')
        self.build()
        self.tex_w, self.tex_h = pack(self.root, self.tex_w, self.tex_h)

    def build(self):
        raise NotImplementedError

    def part(self, name):
        for p in self.root.walk():
            if p.name == name:
                return p
        raise KeyError(name)

    def cube(self, tag):
        for p in self.root.walk():
            for c in p.cubes:
                if c.tag == tag:
                    return c
        raise KeyError(tag)
