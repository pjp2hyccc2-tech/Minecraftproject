package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.FloatingLanternBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.FloatingLanternMotion;
import com.eternalrealm.world.town.TownWind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * A candle lantern floating on the canal: a wooden float with a small paper lantern on it, bobbing, rocking, turning
 * and drifting on the ripples ({@link FloatingLanternMotion}). After dusk (and always in the dark of a covered or
 * underground waterway) the candle lights the paper from inside, a soft halo hangs round it and its light lies on the
 * water below, shimmering.
 */
public class FloatingLanternRenderer implements BlockEntityRenderer<FloatingLanternBlockEntity> {
    private static final RenderType BODY = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    private static final RenderType GLOW = RenderType.eyes(
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/bunting/glow.png"));
    /** Warm paper tints, chosen per lantern. */
    private static final float[][] PAPER = {
            {1.0F, 0.94F, 0.8F}, {1.0F, 0.83F, 0.72F}, {1.0F, 0.9F, 0.62F}, {0.99F, 0.78F, 0.82F}, {0.96F, 0.96F, 0.9F}};

    private final QuadWriter quads = new QuadWriter();
    private final double[] drift = new double[2];
    private final double[] tilt = new double[2];
    private final double[] axes = new double[6];
    // the float's frame: right, up, forward
    private double rx;
    private double ry;
    private double rz;
    private double ux;
    private double uy;
    private double uz;
    private double fx;
    private double fy;
    private double fz;

    public FloatingLanternRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FloatingLanternBlockEntity lantern, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = lantern.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = lantern.getBlockPos();
        long tick = level.getGameTime();
        if (Double.isNaN(lantern.clientWater) || tick - lantern.clientWaterAt > 40L || tick < lantern.clientWaterAt) {
            lantern.clientWaterAt = tick;
            FluidState below = level.getFluidState(pos.below());
            lantern.clientWater = below.isEmpty() ? 0.0 : below.getHeight(level, pos.below()) - 1.0;
        }
        double seconds = (tick + partialTick) / 20.0;
        long seed = pos.asLong();
        FloatingLanternMotion.drift(seconds, seed, drift);
        FloatingLanternMotion.tilt(seconds, seed, tilt);
        double yaw = FloatingLanternMotion.yaw(seconds, seed);
        double flicker = FloatingLanternMotion.flicker(seconds, seed);
        double baseX = 0.5 + drift[0];
        double baseY = lantern.clientWater + FloatingLanternMotion.bob(seconds, seed);
        double baseZ = 0.5 + drift[1];
        frame(yaw, tilt[0], tilt[1]);

        int sky = LightTexture.sky(packedLight);
        float night = 1.0F - LightShaftRenderer.daylight(level, partialTick);
        float glow = Math.max(night, Mth.clamp((11.0F - sky) / 8.0F, 0.0F, 1.0F));
        float[] tint = PAPER[Math.floorMod((int) (TownWind.phase(seed) * 1000.0), PAPER.length)];

        quads.begin(pose.last(), buffers.getBuffer(BODY), packedLight);
        // the float: a shallow wooden square, top and sides
        TextureAtlasSprite raft = GimmickSprites.get(GimmickSprites.FLOAT_RAFT);
        quads.color(1.0F, 1.0F, 1.0F, 1.0F);
        box(baseX + ux * 0.025, baseY + uy * 0.025, baseZ + uz * 0.025, 0.17, 0.035, 0.17, raft);
        // the paper lantern on it, open at the top, lit from inside after dusk
        TextureAtlasSprite paper = GimmickSprites.get(GimmickSprites.FLOAT_PAPER);
        int lit = packedLight;
        float shine = 1.0F;
        if (glow > 0.02F) {
            int block = Math.min(15, Math.round((7.0F + 8.0F * glow) * (float) flicker));
            lit = LightTexture.pack(Math.max(LightTexture.block(packedLight), block), sky);
            shine = 1.0F + 0.1F * glow;
        }
        quads.light(lit).color(Math.min(1.0F, tint[0] * shine), Math.min(1.0F, tint[1] * shine),
                Math.min(1.0F, tint[2] * shine), 1.0F);
        // the lantern's centre stands 0.19 up the float's (rocking) axis
        double cx = baseX + ux * 0.19;
        double cy = baseY + uy * 0.19;
        double cz = baseZ + uz * 0.19;
        double half = 0.115;
        boolean evenly = glow > 0.3F;
        side(cx + rx * half, cy + ry * half, cz + rz * half, fx, fy, fz, half, 0.13, paper,
                evenly ? 0.0 : rx, evenly ? 1.0 : ry, evenly ? 0.0 : rz);
        side(cx - rx * half, cy - ry * half, cz - rz * half, fx, fy, fz, half, 0.13, paper,
                evenly ? 0.0 : -rx, evenly ? 1.0 : -ry, evenly ? 0.0 : -rz);
        side(cx + fx * half, cy + fy * half, cz + fz * half, rx, ry, rz, half, 0.13, paper,
                evenly ? 0.0 : fx, evenly ? 1.0 : fy, evenly ? 0.0 : fz);
        side(cx - fx * half, cy - fy * half, cz - fz * half, rx, ry, rz, half, 0.13, paper,
                evenly ? 0.0 : -fx, evenly ? 1.0 : -fy, evenly ? 0.0 : -fz);
        if (glow <= 0.02F) {
            return;
        }
        // the flame, a halo round the lantern and its light on the water
        QuadWriter.cameraAxes(Minecraft.getInstance().gameRenderer.getMainCamera(), axes);
        quads.target(buffers.getBuffer(GLOW)).light(LightTexture.FULL_BRIGHT);
        float f = (float) flicker;
        quads.color(0.9F * f, 0.62F * f, 0.25F * f, 1.0F);
        quads.billboard(baseX + ux * 0.16, baseY + uy * 0.16, baseZ + uz * 0.16, 0.045, axes);
        quads.color(tint[0] * 0.42F * glow * f, tint[1] * 0.3F * glow * f, tint[2] * 0.16F * glow * f, 1.0F);
        quads.billboard(baseX, baseY + 0.2, baseZ, 0.62, axes);
        double shimmer = 1.0 + 0.12 * Math.sin(seconds * 2.3 + seed % 7);
        double wx = 0.55 * shimmer;
        double wz = 0.55 / shimmer;
        double wy = lantern.clientWater + 0.01;
        quads.color(tint[0] * 0.3F * glow * f, tint[1] * 0.22F * glow * f, tint[2] * 0.12F * glow * f, 1.0F);
        quads.vertex(baseX - wx, wy, baseZ - wz, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
        quads.vertex(baseX - wx, wy, baseZ + wz, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        quads.vertex(baseX + wx, wy, baseZ + wz, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        quads.vertex(baseX + wx, wy, baseZ - wz, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
    }

    /** The float's axes after turning by {@code yaw} and rocking by small angles about x and z. */
    private void frame(double yaw, double aboutX, double aboutZ) {
        double c = Math.cos(yaw);
        double s = Math.sin(yaw);
        rx = c;
        ry = 0.0;
        rz = s;
        fx = -s;
        fy = 0.0;
        fz = c;
        // tip the up vector a little towards right and forward, then square the frame again
        ux = rx * aboutZ + fx * aboutX;
        uy = 1.0;
        uz = rz * aboutZ + fz * aboutX;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double d = rx * ux + ry * uy + rz * uz;
        rx -= ux * d;
        ry -= uy * d;
        rz -= uz * d;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl;
        ry /= rl;
        rz /= rl;
        // forward = right x up
        fx = ry * uz - rz * uy;
        fy = rz * ux - rx * uz;
        fz = rx * uy - ry * ux;
    }

    /** A box in the float's frame (top and four sides). */
    private void box(double cx, double cy, double cz, double hx, double hy, double hz, TextureAtlasSprite sprite) {
        side(cx + ux * hy, cy + uy * hy, cz + uz * hy, rx, ry, rz, hx, hz, sprite, fx, fy, fz, ux, uy, uz);
        side(cx + rx * hx, cy + ry * hx, cz + rz * hx, fx, fy, fz, hz, hy, sprite, rx, ry, rz);
        side(cx - rx * hx, cy - ry * hx, cz - rz * hx, fx, fy, fz, hz, hy, sprite, -rx, -ry, -rz);
        side(cx + fx * hz, cy + fy * hz, cz + fz * hz, rx, ry, rz, hx, hy, sprite, fx, fy, fz);
        side(cx - fx * hz, cy - fy * hz, cz - fz * hz, rx, ry, rz, hx, hy, sprite, -fx, -fy, -fz);
    }

    /** An upright face centred at c: +-ha along a, +-hb along the float's up (the sprite's up). */
    private void side(double cx, double cy, double cz, double ax, double ay, double az, double ha, double hb,
                      TextureAtlasSprite sprite, double nx, double ny, double nz) {
        side(cx, cy, cz, ax, ay, az, ha, hb, sprite, ux, uy, uz, nx, ny, nz);
    }

    private void side(double cx, double cy, double cz, double ax, double ay, double az, double ha, double hb,
                      TextureAtlasSprite sprite, double bx, double by, double bz, double nx, double ny, double nz) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float nX = (float) nx;
        float nY = (float) ny;
        float nZ = (float) nz;
        quads.vertex(cx - ax * ha + bx * hb, cy - ay * ha + by * hb, cz - az * ha + bz * hb, u0, v0, nX, nY, nZ);
        quads.vertex(cx - ax * ha - bx * hb, cy - ay * ha - by * hb, cz - az * ha - bz * hb, u0, v1, nX, nY, nZ);
        quads.vertex(cx + ax * ha - bx * hb, cy + ay * ha - by * hb, cz + az * ha - bz * hb, u1, v1, nX, nY, nZ);
        quads.vertex(cx + ax * ha + bx * hb, cy + ay * ha + by * hb, cz + az * ha + bz * hb, u1, v0, nX, nY, nZ);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
