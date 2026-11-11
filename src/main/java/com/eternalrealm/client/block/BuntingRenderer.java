package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.BuntingBlock;
import com.eternalrealm.block.forge.BuntingBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.BuntingLayout;
import com.eternalrealm.world.town.BuntingLine;
import com.eternalrealm.world.town.BuntingStyle;
import com.eternalrealm.world.town.BuntingSway;
import com.eternalrealm.world.town.TownWind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Strings of bunting. The cord is a catenary ({@link BuntingLine}) drawn as a thin ribbon turned towards the camera; the
 * whole string swings about its chord and the pieces flutter about the cord in waves that run along it
 * ({@link BuntingSway}, in the town wind). Pennants, flags and laundry are cutout cloths bent in two rows, paper
 * lanterns are small ribbed lanterns that light up after dusk and under roofs (with a soft additive halo), garlands are
 * leaf clusters wound round the cord with small flowers. Iron eyes mark both ends, each fixed to the wall, post or eave
 * it finds.
 *
 * <p>Built for ~200 strings in one town: everything is written straight into one cutout buffer (block atlas, so the
 * cloths are mipmapped) without allocating, light is sampled along the string twice a second, the detail drops with
 * distance, and the render bounds of {@link BuntingBlockEntity} cover the whole string so it is culled correctly.
 */
public class BuntingRenderer implements BlockEntityRenderer<BuntingBlockEntity> {
    private static final RenderType CLOTH = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/bunting/glow.png");
    private static final RenderType GLOW = RenderType.eyes(GLOW_TEXTURE);
    private static final int VIEW_DISTANCE = 128;
    private static final int MAX_SEGMENTS = 64;
    private static final int MAX_GLOWS = 64;
    private static final int LIGHT_EVERY = 10;
    private static final int MOUNT_EVERY = 100;

    private final QuadWriter quads = new QuadWriter();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final double[] p = new double[6];
    private final double[] cord = new double[(MAX_SEGMENTS + 1) * 6];
    private final double[] side = new double[(MAX_SEGMENTS + 1) * 3];
    private final double[] glows = new double[MAX_GLOWS * 7];
    private final double[] axes = new double[6];
    private int glowCount;
    // state of the string being drawn
    private double camX;
    private double camY;
    private double camZ;
    private double wind;
    private double flutter;
    private int lod;

    public BuntingRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BuntingBlockEntity bunting, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = bunting.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = bunting.getBlockPos();
        long tick = level.getGameTime();
        BuntingLine line = bunting.line();
        BuntingLayout layout = bunting.layout();
        refreshLight(level, bunting, line, tick);
        refreshMounts(level, bunting, line, tick);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        camX = eye.x - pos.getX();
        camY = eye.y - pos.getY();
        camZ = eye.z - pos.getZ();
        quads.begin(pose.last(), buffers.getBuffer(CLOTH), packedLight);

        int[] light = bunting.clientLight;
        double dirX = line == null ? 1.0 : bunting.targetX();
        double dirZ = line == null ? 0.0 : bunting.targetZ();
        hook(0.5, 0.5, 0.5, bunting.clientAnchorMount, light[0], dirX, dirZ);
        if (line == null || layout == null || line.length() < 0.1) {
            return;
        }
        hook(0.5 + bunting.targetX(), 0.5 + bunting.targetY(), 0.5 + bunting.targetZ(), bunting.clientFarMount,
                light[light.length - 1], dirX, dirZ);

        double seconds = (tick + partialTick) / 20.0;
        long seed = pos.asLong();
        wind = TownWind.strength(seconds, seed, level.getRainLevel(partialTick), level.getThunderLevel(partialTick))
                * bunting.wind();
        advanceFlutter(bunting, seconds, BuntingSway.flutterSpeed(wind));
        flutter = bunting.clientPhase;
        double cross = line.crossWind(BuntingSway.WIND_X, BuntingSway.WIND_Z);
        double swing = BuntingSway.swingAngle(seconds, seed, wind, cross, line.sag());
        double cos = Math.cos(swing);
        double sin = Math.sin(swing);

        line.at(line.length() * 0.5, p, 0);
        double mx = p[0] + 0.5 - camX;
        double my = p[1] + 0.5 - camY;
        double mz = p[2] + 0.5 - camZ;
        double distance = Math.max(0.0, Math.sqrt(mx * mx + my * my + mz * mz) - line.length() * 0.5);
        lod = distance < 20.0 ? 2 : (distance < 48.0 ? 1 : 0);

        drawCord(bunting, line, cos, sin, distance);
        glowCount = 0;
        float glow = 0.0F;
        if (bunting.style() == BuntingStyle.LANTERNS) {
            int middle = light[light.length / 2];
            float covered = Mth.clamp((11.0F - LightTexture.sky(middle)) / 8.0F, 0.0F, 1.0F);
            glow = Math.max(1.0F - LightShaftRenderer.daylight(level, partialTick), covered);
        }
        drawPieces(bunting, line, layout, cos, sin, glow, seconds);
        if (glowCount > 0) {
            drawGlows(buffers, camera);
        }
    }

    // ------------------------------------------------------------------------------------------ cord and hooks

    private void drawCord(BuntingBlockEntity bunting, BuntingLine line, double cos, double sin, double distance) {
        int n = 1;
        if (!line.straight()) {
            double perBlock = lod == 2 ? 2.2 : (lod == 1 ? 1.2 : 0.6);
            n = Mth.clamp((int) Math.ceil(line.length() * perBlock), 3, MAX_SEGMENTS);
        }
        double length = line.length();
        for (int k = 0; k <= n; k++) {
            int o = k * 6;
            line.at(length * k / n, cord, o);
            line.swing(cos, sin, cord, o);
            line.swing(cos, sin, cord, o + 3);
            cord[o] += 0.5;
            cord[o + 1] += 0.5;
            cord[o + 2] += 0.5;
        }
        double half = 0.016 + distance * 0.0004;
        for (int k = 0; k <= n; k++) {
            int o = k * 6;
            double vx = cord[o] - camX;
            double vy = cord[o + 1] - camY;
            double vz = cord[o + 2] - camZ;
            double sx = cord[o + 4] * vz - cord[o + 5] * vy;
            double sy = cord[o + 5] * vx - cord[o + 3] * vz;
            double sz = cord[o + 3] * vy - cord[o + 4] * vx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-6) {
                sx = 0.0;
                sy = 1.0;
                sz = 0.0;
                len = 1.0;
            }
            side[k * 3] = sx / len * half;
            side[k * 3 + 1] = sy / len * half;
            side[k * 3 + 2] = sz / len * half;
        }
        TextureAtlasSprite sprite = GimmickSprites.get(GimmickSprites.CORD);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        quads.color(0.95F, 0.95F, 0.95F, 1.0F);
        for (int k = 0; k < n; k++) {
            int a = k * 6;
            int b = a + 6;
            int sa = k * 3;
            int sb = sa + 3;
            quads.light(lightAt(bunting, length * (k + 0.5) / n, length));
            quads.vertex(cord[a] - side[sa], cord[a + 1] - side[sa + 1], cord[a + 2] - side[sa + 2], u0, v0, 0, 1, 0);
            quads.vertex(cord[a] + side[sa], cord[a + 1] + side[sa + 1], cord[a + 2] + side[sa + 2], u1, v0, 0, 1, 0);
            quads.vertex(cord[b] + side[sb], cord[b + 1] + side[sb + 1], cord[b + 2] + side[sb + 2], u1, v1, 0, 1, 0);
            quads.vertex(cord[b] - side[sb], cord[b + 1] - side[sb + 1], cord[b + 2] - side[sb + 2], u0, v1, 0, 1, 0);
        }
    }

    /** An iron eye at (x, y, z), with a stem and a plate to what it is fixed to ({@code mount}, -1 for nothing). */
    private void hook(double x, double y, double z, int mount, int light, double dirX, double dirZ) {
        quads.light(light).color(0.9F, 0.9F, 0.92F, 1.0F);
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double ax = len > 1e-6 ? dirX / len : 1.0;
        double az = len > 1e-6 ? dirZ / len : 0.0;
        TextureAtlasSprite ring = GimmickSprites.get(GimmickSprites.RING);
        quad(x, y, z, ax, 0, az, 0, 1, 0, 0.1, 0.1, ring, 0.0F, 1.0F, 0.0F);
        quad(x, y, z, -az, 0, ax, 0, 1, 0, 0.1, 0.1, ring, 0.0F, 1.0F, 0.0F);
        if (mount < 0) {
            return;
        }
        Direction d = Direction.from3DDataValue(mount);
        double dx = d.getStepX();
        double dy = d.getStepY();
        double dz = d.getStepZ();
        // two axes square to the stem
        double px = dy != 0 ? 1.0 : 0.0;
        double py = dy != 0 ? 0.0 : 1.0;
        double qx = dy != 0 ? 0.0 : -dz;
        double qy = 0.0;
        double qz = dy != 0 ? 1.0 : dx;
        TextureAtlasSprite iron = GimmickSprites.get(GimmickSprites.IRON);
        double from = 0.075;
        double to = 0.5;
        double h = 0.024;
        double cx = x + dx * (from + to) * 0.5;
        double cy = y + dy * (from + to) * 0.5;
        double cz = z + dz * (from + to) * 0.5;
        double half = (to - from) * 0.5;
        // four sides of the stem
        quad(cx + px * h, cy + py * h, cz, dx, dy, dz, qx, qy, qz, half, h, iron, (float) px, (float) py, 0.0F);
        quad(cx - px * h, cy - py * h, cz, dx, dy, dz, qx, qy, qz, half, h, iron, (float) -px, (float) -py, 0.0F);
        quad(cx + qx * h, cy, cz + qz * h, dx, dy, dz, px, py, 0, half, h, iron, (float) qx, 0.0F, (float) qz);
        quad(cx - qx * h, cy, cz - qz * h, dx, dy, dz, px, py, 0, half, h, iron, (float) -qx, 0.0F, (float) -qz);
        // the plate on the wall
        quad(x + dx * 0.496, y + dy * 0.496, z + dz * 0.496, px, py, 0, qx, qy, qz, 0.085, 0.085, iron,
                (float) -dx, (float) -dy, (float) -dz);
    }

    // ------------------------------------------------------------------------------------------ pieces

    private void drawPieces(BuntingBlockEntity bunting, BuntingLine line, BuntingLayout layout, double cos,
                            double sin, float glow, double seconds) {
        double length = line.length();
        for (int i = 0; i < layout.count(); i++) {
            int kind = layout.kind(i);
            double s = layout.at(i);
            line.at(s, p, 0);
            line.swing(cos, sin, p, 0);
            line.swing(cos, sin, p, 3);
            double px = p[0] + 0.5;
            double py = p[1] + 0.5;
            double pz = p[2] + 0.5;
            double tx = p[3];
            double ty = p[4];
            double tz = p[5];
            // gravity's down, square to the cord, and the side the cloth swings out to
            double dx = ty * tx;
            double dy = ty * ty - 1.0;
            double dz = ty * tz;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl < 1e-4) {
                dx = -tz;
                dy = 0.0;
                dz = tx;
                dl = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
            }
            dx /= dl;
            dy /= dl;
            dz /= dl;
            double bx = ty * dz - tz * dy;
            double by = tz * dx - tx * dz;
            double bz = tx * dy - ty * dx;
            double cross = bx * BuntingSway.WIND_X + bz * BuntingSway.WIND_Z;
            int light = lightAt(bunting, s, length);
            int slot = layout.colorSlot(i);
            int rgb = slot >= 0 ? BuntingStyle.dyeRgb(bunting.color(slot)) : 0xFFFFFF;
            float r = ((rgb >> 16) & 255) / 255.0F;
            float g = ((rgb >> 8) & 255) / 255.0F;
            float b = (rgb & 255) / 255.0F;
            double w = layout.width(i);
            double h = layout.height(i);
            switch (kind) {
                case BuntingLayout.KIND_PENNANT, BuntingLayout.KIND_FLAG -> {
                    double psi = BuntingSway.clothAngle(flutter, s, wind, cross, 1.0);
                    double tip = psi * 1.35 + 0.12 * Math.sin(flutter * 1.3 + s * 2.0);
                    cloth(px, py, pz, tx, ty, tz, dx, dy, dz, bx, by, bz, w, h, psi, tip,
                            GimmickSprites.get(kind == BuntingLayout.KIND_PENNANT ? GimmickSprites.PENNANT
                                    : GimmickSprites.FLAG), r, g, b, light, false);
                }
                case BuntingLayout.KIND_LANTERN -> lantern(px, py, pz, tx, ty, tz, dx, dy, dz, bx, by, bz, s, cross, r,
                        g, b, light, glow, i, seconds);
                case BuntingLayout.KIND_LEAVES -> leaves(px, py, pz, tx, ty, tz, dx, dy, dz, bx, by, bz, s, cross, i,
                        light);
                case BuntingLayout.KIND_FLOWER -> flower(px, py, pz, tx, ty, tz, dx, dy, dz, bx, by, bz, s, r, g, b,
                        light);
                default -> {
                    // laundry: heavier, so it moves less
                    double psi = BuntingSway.clothAngle(flutter, s, wind, cross, 0.55);
                    double tip = psi * 1.2 + 0.06 * Math.sin(flutter * 1.1 + s * 1.7);
                    cloth(px, py, pz, tx, ty, tz, dx, dy, dz, bx, by, bz, w, h, psi, tip,
                            GimmickSprites.get(laundrySprite(kind)), r, g, b, light, lod == 2);
                }
            }
        }
    }

    private static int laundrySprite(int kind) {
        return switch (kind) {
            case BuntingLayout.KIND_SHIRT -> GimmickSprites.SHIRT;
            case BuntingLayout.KIND_TOWEL -> GimmickSprites.TOWEL;
            case BuntingLayout.KIND_SOCKS -> GimmickSprites.SOCKS;
            case BuntingLayout.KIND_TROUSERS -> GimmickSprites.TROUSERS;
            default -> GimmickSprites.DRESS;
        };
    }

    /**
     * A cloth hanging from the cord at P: top edge along the cord, bent in two rows (the lower half swings further
     * than the upper), or one flat quad far away.
     */
    private void cloth(double px, double py, double pz, double tx, double ty, double tz, double dx, double dy,
                       double dz, double bx, double by, double bz, double w, double h, double psi, double tip,
                       TextureAtlasSprite sprite, float r, float g, float b, int light, boolean pegs) {
        double c1 = Math.cos(psi);
        double s1 = Math.sin(psi);
        double d1x = dx * c1 + bx * s1;
        double d1y = dy * c1 + by * s1;
        double d1z = dz * c1 + bz * s1;
        double c2 = Math.cos(tip);
        double s2 = Math.sin(tip);
        double d2x = dx * c2 + bx * s2;
        double d2y = dy * c2 + by * s2;
        double d2z = dz * c2 + bz * s2;
        double hw = w * 0.5;
        double lx = px - tx * hw;
        double ly = py - ty * hw;
        double lz = pz - tz * hw;
        double rx = px + tx * hw;
        double ry = py + ty * hw;
        double rz = pz + tz * hw;
        // the cloth faces T x D: tilt the normal up so both sides read evenly
        float nx = (float) ((ty * d1z - tz * d1y) * 0.4);
        float nz = (float) ((tx * d1y - ty * d1x) * 0.4);
        float ny = 0.85F;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        quads.light(light);
        if (lod == 0) {
            quads.color(r, g, b, 1.0F);
            quads.vertex(lx, ly, lz, u0, v0, nx, ny, nz);
            quads.vertex(rx, ry, rz, u1, v0, nx, ny, nz);
            quads.color(r * 0.88F, g * 0.88F, b * 0.88F, 1.0F);
            quads.vertex(rx + d1x * h, ry + d1y * h, rz + d1z * h, u1, v1, nx, ny, nz);
            quads.vertex(lx + d1x * h, ly + d1y * h, lz + d1z * h, u0, v1, nx, ny, nz);
            return;
        }
        double hh = h * 0.5;
        double mlx = lx + d1x * hh;
        double mly = ly + d1y * hh;
        double mlz = lz + d1z * hh;
        double mrx = rx + d1x * hh;
        double mry = ry + d1y * hh;
        double mrz = rz + d1z * hh;
        float vm = (v0 + v1) * 0.5F;
        // a fold between the rows darkens with the bend
        float fold = (float) (0.94 - 0.18 * Math.min(1.0, Math.abs(tip - psi) * 2.5));
        quads.color(r, g, b, 1.0F);
        quads.vertex(lx, ly, lz, u0, v0, nx, ny, nz);
        quads.vertex(rx, ry, rz, u1, v0, nx, ny, nz);
        quads.color(r * fold, g * fold, b * fold, 1.0F);
        quads.vertex(mrx, mry, mrz, u1, vm, nx, ny, nz);
        quads.vertex(mlx, mly, mlz, u0, vm, nx, ny, nz);
        quads.vertex(mlx, mly, mlz, u0, vm, nx, ny, nz);
        quads.vertex(mrx, mry, mrz, u1, vm, nx, ny, nz);
        quads.color(r * 0.86F, g * 0.86F, b * 0.86F, 1.0F);
        quads.vertex(mrx + d2x * hh, mry + d2y * hh, mrz + d2z * hh, u1, v1, nx, ny, nz);
        quads.vertex(mlx + d2x * hh, mly + d2y * hh, mlz + d2z * hh, u0, v1, nx, ny, nz);
        if (pegs) {
            // two wooden pegs over the cord at the top corners, on both faces
            TextureAtlasSprite peg = GimmickSprites.get(GimmickSprites.PEG);
            double ox = (ty * d1z - tz * d1y) * 0.012;
            double oy = (tz * d1x - tx * d1z) * 0.012;
            double oz = (tx * d1y - ty * d1x) * 0.012;
            quads.color(1.0F, 1.0F, 1.0F, 1.0F);
            for (int k = 0; k < 2; k++) {
                double cx = (k == 0 ? lx + tx * 0.07 : rx - tx * 0.07) + d1x * 0.025;
                double cy = (k == 0 ? ly + ty * 0.07 : ry - ty * 0.07) + d1y * 0.025;
                double cz = (k == 0 ? lz + tz * 0.07 : rz - tz * 0.07) + d1z * 0.025;
                for (int f = -1; f <= 1; f += 2) {
                    quad(cx + ox * f, cy + oy * f, cz + oz * f, tx, ty, tz, -d1x, -d1y, -d1z, 0.024, 0.055, peg, nx, ny,
                            nz);
                }
            }
        }
    }

    /** A paper lantern on a short cord: a ribbed body (six sides near, four further, two crossed quads far away). */
    private void lantern(double px, double py, double pz, double tx, double ty, double tz, double dx, double dy,
                         double dz, double bx, double by, double bz, double s, double cross, float r, float g, float b,
                         int light, float glow, int index, double seconds) {
        double psi = BuntingSway.clothAngle(flutter * 0.6, s, wind, cross, 0.45);
        double c = Math.cos(psi);
        double sn = Math.sin(psi);
        double ax = dx * c + bx * sn;
        double ay = dy * c + by * sn;
        double az = dz * c + bz * sn;
        // e2 = axis x tangent completes the frame round the lantern's axis
        double ex = ay * tz - az * ty;
        double ey = az * tx - ax * tz;
        double ez = ax * ty - ay * tx;
        double el = Math.max(1e-6, Math.sqrt(ex * ex + ey * ey + ez * ez));
        ex /= el;
        ey /= el;
        ez /= el;
        double drop = 0.08;
        double height = 0.4;
        double topX = px + ax * drop;
        double topY = py + ay * drop;
        double topZ = pz + az * drop;
        double cX = topX + ax * height * 0.5;
        double cY = topY + ay * height * 0.5;
        double cZ = topZ + az * height * 0.5;
        float flicker = (float) (0.9 + 0.06 * Math.sin(seconds * 7.3 + index * 2.1) + 0.04 * Math.sin(seconds * 13.1
                + index));
        int paper = light;
        float shine = 1.0F;
        if (glow > 0.02F) {
            int lit = Math.round((6.0F + 9.0F * glow) * flicker);
            paper = LightTexture.pack(Math.max(LightTexture.block(light), Math.min(15, lit)), LightTexture.sky(light));
            shine = 1.0F + 0.12F * glow;
            if (glowCount < MAX_GLOWS) {
                int o = glowCount++ * 7;
                glows[o] = cX;
                glows[o + 1] = cY;
                glows[o + 2] = cZ;
                glows[o + 3] = r * glow * 0.55F * flicker + 0.12F * glow;
                glows[o + 4] = g * glow * 0.5F * flicker + 0.08F * glow;
                glows[o + 5] = b * glow * 0.4F * flicker;
                glows[o + 6] = 0.42 + 0.06 * flicker;
            }
        }
        // the short cord from the string to the lantern's cap
        TextureAtlasSprite cordSprite = GimmickSprites.get(GimmickSprites.CORD);
        quads.light(light).color(0.9F, 0.9F, 0.9F, 1.0F);
        quad(px + ax * drop * 0.5, py + ay * drop * 0.5, pz + az * drop * 0.5, tx, ty, tz, ax, ay, az, 0.012,
                drop * 0.5, cordSprite, 0.0F, 1.0F, 0.0F);
        TextureAtlasSprite sprite = GimmickSprites.get(GimmickSprites.LANTERN);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float vm = (v0 + v1) * 0.5F;
        quads.light(paper).color(Math.min(1.0F, r * shine), Math.min(1.0F, g * shine), Math.min(1.0F, b * shine), 1.0F);
        if (lod == 0) {
            quad(cX, cY, cZ, tx, ty, tz, -ax, -ay, -az, 0.16, height * 0.5, sprite, 0.0F, 1.0F, 0.0F);
            quad(cX, cY, cZ, ex, ey, ez, -ax, -ay, -az, 0.16, height * 0.5, sprite, 0.0F, 1.0F, 0.0F);
            return;
        }
        int sides = lod == 2 ? 6 : 4;
        double rEnd = 0.1;
        double rMid = 0.165;
        double botX = topX + ax * height;
        double botY = topY + ay * height;
        double botZ = topZ + az * height;
        for (int j = 0; j < sides; j++) {
            double a0 = Math.PI * 2.0 * j / sides;
            double a1 = Math.PI * 2.0 * (j + 1) / sides;
            double am = (a0 + a1) * 0.5;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            // lit from inside after dusk the paper glows evenly all round; by day it is shaded like a body
            float nx = glow > 0.3F ? 0.0F : (float) (tx * Math.cos(am) + ex * Math.sin(am));
            float ny = glow > 0.3F ? 1.0F : (float) (ty * Math.cos(am) + ey * Math.sin(am));
            float nz = glow > 0.3F ? 0.0F : (float) (tz * Math.cos(am) + ez * Math.sin(am));
            float ua = u0 + (u1 - u0) * j / sides;
            float ub = u0 + (u1 - u0) * (j + 1) / sides;
            // upper row: cap ring to the belly
            quads.vertex(topX + (tx * c0 + ex * s0) * rEnd, topY + (ty * c0 + ey * s0) * rEnd,
                    topZ + (tz * c0 + ez * s0) * rEnd, ua, v0, nx, ny, nz);
            quads.vertex(topX + (tx * c1 + ex * s1) * rEnd, topY + (ty * c1 + ey * s1) * rEnd,
                    topZ + (tz * c1 + ez * s1) * rEnd, ub, v0, nx, ny, nz);
            quads.vertex(cX + (tx * c1 + ex * s1) * rMid, cY + (ty * c1 + ey * s1) * rMid,
                    cZ + (tz * c1 + ez * s1) * rMid, ub, vm, nx, ny, nz);
            quads.vertex(cX + (tx * c0 + ex * s0) * rMid, cY + (ty * c0 + ey * s0) * rMid,
                    cZ + (tz * c0 + ez * s0) * rMid, ua, vm, nx, ny, nz);
            // lower row: belly to the bottom ring
            quads.vertex(cX + (tx * c0 + ex * s0) * rMid, cY + (ty * c0 + ey * s0) * rMid,
                    cZ + (tz * c0 + ez * s0) * rMid, ua, vm, nx, ny, nz);
            quads.vertex(cX + (tx * c1 + ex * s1) * rMid, cY + (ty * c1 + ey * s1) * rMid,
                    cZ + (tz * c1 + ez * s1) * rMid, ub, vm, nx, ny, nz);
            quads.vertex(botX + (tx * c1 + ex * s1) * rEnd, botY + (ty * c1 + ey * s1) * rEnd,
                    botZ + (tz * c1 + ez * s1) * rEnd, ub, v1, nx, ny, nz);
            quads.vertex(botX + (tx * c0 + ex * s0) * rEnd, botY + (ty * c0 + ey * s0) * rEnd,
                    botZ + (tz * c0 + ez * s0) * rEnd, ua, v1, nx, ny, nz);
        }
        // dark wooden caps top and bottom
        TextureAtlasSprite cap = GimmickSprites.get(GimmickSprites.LANTERN_CAP);
        quads.light(light).color(1.0F, 1.0F, 1.0F, 1.0F);
        double rc = rEnd * 1.12;
        quad(topX, topY, topZ, tx, ty, tz, ex, ey, ez, rc, rc, cap, (float) -ax, (float) -ay, (float) -az);
        quad(botX, botY, botZ, tx, ty, tz, ex, ey, ez, rc, rc, cap, (float) ax, (float) ay, (float) az);
    }

    /** Two crossed leaf clusters wound round the cord, jiggling a little in the wind. */
    private void leaves(double px, double py, double pz, double tx, double ty, double tz, double dx, double dy,
                        double dz, double bx, double by, double bz, double s, double cross, int index, int light) {
        double alpha = s * 4.7 + 0.25 * BuntingSway.clothAngle(flutter, s, wind, cross, 0.6);
        double c = Math.cos(alpha);
        double sn = Math.sin(alpha);
        double r1x = dx * c + bx * sn;
        double r1y = dy * c + by * sn;
        double r1z = dz * c + bz * sn;
        double r2x = -dx * sn + bx * c;
        double r2y = -dy * sn + by * c;
        double r2z = -dz * sn + bz * c;
        float shade = 0.84F + 0.16F * (((index * 37) & 15) / 15.0F);
        TextureAtlasSprite sprite = GimmickSprites.get(GimmickSprites.LEAVES);
        quads.light(light).color(shade, shade, shade, 1.0F);
        quad(px, py, pz, tx, ty, tz, r1x, r1y, r1z, 0.14, 0.11, sprite, 0.0F, 1.0F, 0.0F);
        if (lod > 0) {
            quad(px, py, pz, tx, ty, tz, r2x, r2y, r2z, 0.14, 0.11, sprite, 0.0F, 1.0F, 0.0F);
        }
    }

    /** A small flower among the leaves, turned outwards and a little down so it is seen from the street. */
    private void flower(double px, double py, double pz, double tx, double ty, double tz, double dx, double dy,
                        double dz, double bx, double by, double bz, double s, float r, float g, float b, int light) {
        double alpha = s * 4.7 + 1.1;
        double c = Math.cos(alpha) * 0.55 + 0.45;
        double sn = Math.sin(alpha);
        double ox = dx * c + bx * sn;
        double oy = dy * c + by * sn;
        double oz = dz * c + bz * sn;
        double ol = Math.max(1e-6, Math.sqrt(ox * ox + oy * oy + oz * oz));
        ox /= ol;
        oy /= ol;
        oz /= ol;
        // the flower's face is square to the outward direction: span it with the tangent and outward x tangent
        double wx = oy * tz - oz * ty;
        double wy = oz * tx - ox * tz;
        double wz = ox * ty - oy * tx;
        TextureAtlasSprite sprite = GimmickSprites.get(GimmickSprites.FLOWER);
        quads.light(light).color(r, g, b, 1.0F);
        quad(px + ox * 0.075, py + oy * 0.075, pz + oz * 0.075, tx, ty, tz, wx, wy, wz, 0.07, 0.07, sprite,
                (float) ox, (float) oy, (float) oz);
    }

    /** Soft additive halos round the lit lanterns, turned to the camera. */
    private void drawGlows(MultiBufferSource buffers, Camera camera) {
        QuadWriter.cameraAxes(camera, axes);
        quads.target(buffers.getBuffer(GLOW)).light(LightTexture.FULL_BRIGHT);
        for (int i = 0; i < glowCount; i++) {
            int o = i * 7;
            quads.color((float) glows[o + 3], (float) glows[o + 4], (float) glows[o + 5], 1.0F);
            quads.billboard(glows[o], glows[o + 1], glows[o + 2], glows[o + 6], axes);
        }
    }

    /**
     * A quad centred at c spanning +-ha along unit vector a (the sprite's u) and +-hb along unit vector b (the sprite's
     * v runs from +b to -b, i.e. b is the sprite's up).
     */
    private void quad(double cx, double cy, double cz, double ax, double ay, double az, double bx, double by, double bz,
                      double ha, double hb, TextureAtlasSprite sprite, float nx, float ny, float nz) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        double aX = ax * ha;
        double aY = ay * ha;
        double aZ = az * ha;
        double bX = bx * hb;
        double bY = by * hb;
        double bZ = bz * hb;
        quads.vertex(cx - aX + bX, cy - aY + bY, cz - aZ + bZ, u0, v0, nx, ny, nz);
        quads.vertex(cx - aX - bX, cy - aY - bY, cz - aZ - bZ, u0, v1, nx, ny, nz);
        quads.vertex(cx + aX - bX, cy + aY - bY, cz + aZ - bZ, u1, v1, nx, ny, nz);
        quads.vertex(cx + aX + bX, cy + aY + bY, cz + aZ + bZ, u1, v0, nx, ny, nz);
    }

    // ------------------------------------------------------------------------------------------ caches

    private static void advanceFlutter(BuntingBlockEntity bunting, double seconds, double speed) {
        double last = bunting.clientLastSeconds;
        if (Double.isNaN(last)) {
            bunting.clientPhase = TownWind.phase(bunting.getBlockPos().asLong()) * 3.0;
        } else {
            double dt = seconds - last;
            if (dt > 0.0 && dt < 1.0) {
                bunting.clientPhase += speed * dt;
                if (bunting.clientPhase > 1.0e5) {
                    bunting.clientPhase -= Math.PI * 2.0 * 10000.0;
                }
            }
        }
        bunting.clientLastSeconds = seconds;
    }

    private static int lightAt(BuntingBlockEntity bunting, double s, double length) {
        int[] light = bunting.clientLight;
        int i = (int) Math.round(s / Math.max(1e-6, length) * (light.length - 1));
        return light[Mth.clamp(i, 0, light.length - 1)];
    }

    /** Light along the string (the cell of each sample and the one above, whichever is brighter), every half second. */
    private void refreshLight(Level level, BuntingBlockEntity bunting, BuntingLine line, long tick) {
        if (bunting.clientLightAt != Long.MIN_VALUE && tick >= bunting.clientLightAt
                && tick - bunting.clientLightAt < LIGHT_EVERY) {
            return;
        }
        bunting.clientLightAt = tick;
        BlockPos pos = bunting.getBlockPos();
        int[] light = bunting.clientLight;
        for (int i = 0; i < light.length; i++) {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            if (line != null) {
                line.at(line.length() * i / (light.length - 1), p, 0);
                x = p[0];
                y = p[1];
                z = p[2];
            }
            cursor.set(Mth.floor(pos.getX() + 0.5 + x), Mth.floor(pos.getY() + 0.2 + y),
                    Mth.floor(pos.getZ() + 0.5 + z));
            int a = LevelRenderer.getLightColor(level, cursor);
            cursor.move(0, 1, 0);
            int b = LevelRenderer.getLightColor(level, cursor);
            light[i] = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)),
                    Math.max(LightTexture.sky(a), LightTexture.sky(b)));
        }
    }

    /** What the anchor and the far hook are fixed to, every five seconds. */
    private void refreshMounts(Level level, BuntingBlockEntity bunting, BuntingLine line, long tick) {
        if (bunting.clientMountAt != Long.MIN_VALUE && tick >= bunting.clientMountAt
                && tick - bunting.clientMountAt < MOUNT_EVERY) {
            return;
        }
        bunting.clientMountAt = tick;
        BlockPos pos = bunting.getBlockPos();
        BlockState state = bunting.getBlockState();
        Direction facing = state.hasProperty(BuntingBlock.FACING) ? state.getValue(BuntingBlock.FACING) : Direction.NORTH;
        bunting.clientAnchorMount = mount(level, pos.getX(), pos.getY(), pos.getZ(), facing.getOpposite());
        if (line != null) {
            int tx = bunting.targetX();
            int tz = bunting.targetZ();
            Direction along = Math.abs(tx) >= Math.abs(tz) ? (tx >= 0 ? Direction.EAST : Direction.WEST)
                    : (tz >= 0 ? Direction.SOUTH : Direction.NORTH);
            bunting.clientFarMount = mount(level, pos.getX() + tx, pos.getY() + bunting.targetY(), pos.getZ() + tz, along);
        }
    }

    /** The side of the cell at (x, y, z) to fix a hook to: the preferred wall, a post below, an eave above, any wall. */
    private int mount(Level level, int x, int y, int z, Direction preferred) {
        if (sturdy(level, x, y, z, preferred)) {
            return preferred.get3DDataValue();
        }
        if (sturdy(level, x, y, z, Direction.DOWN)) {
            return Direction.DOWN.get3DDataValue();
        }
        if (sturdy(level, x, y, z, Direction.UP)) {
            return Direction.UP.get3DDataValue();
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != preferred && sturdy(level, x, y, z, d)) {
                return d.get3DDataValue();
            }
        }
        return -1;
    }

    private boolean sturdy(Level level, int x, int y, int z, Direction towards) {
        cursor.set(x + towards.getStepX(), y + towards.getStepY(), z + towards.getStepZ());
        BlockState state = level.getBlockState(cursor);
        return !state.isAir() && state.isFaceSturdy(level, cursor, towards.getOpposite(), SupportType.CENTER);
    }

    // ------------------------------------------------------------------------------------------ culling

    @Override
    public boolean shouldRender(BuntingBlockEntity bunting, Vec3 camera) {
        BlockPos pos = bunting.getBlockPos();
        double hx = bunting.targetX() * 0.5;
        double hy = bunting.targetY() * 0.5;
        double hz = bunting.targetZ() * 0.5;
        double dx = pos.getX() + 0.5 + hx - camera.x;
        double dy = pos.getY() + 0.5 + hy - camera.y;
        double dz = pos.getZ() + 0.5 + hz - camera.z;
        double reach = VIEW_DISTANCE + Math.sqrt(hx * hx + hy * hy + hz * hz);
        return dx * dx + dy * dy + dz * dz < reach * reach;
    }

    @Override
    public boolean shouldRenderOffScreen(BuntingBlockEntity bunting) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
