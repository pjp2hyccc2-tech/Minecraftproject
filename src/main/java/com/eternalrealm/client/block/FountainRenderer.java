package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.FountainSprayBlock;
import com.eternalrealm.world.town.WaterJet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

/**
 * The water of the fountains: a glassy column (or a spout's arc) made of the animated flowing-water texture, tinted
 * with the biome's water and whitened with air, thinning as it climbs and spreading into a crown at the top. The
 * drops and splashes around it are particles from the block's client ticker.
 */
public class FountainRenderer implements BlockEntityRenderer<FountainSprayBlock.Entity> {
    private static final ResourceLocation WATER_FLOW = new ResourceLocation("minecraft", "block/water_flow");
    private static final int SIDES = 8;

    public FountainRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FountainSprayBlock.Entity jet, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = jet.getLevel();
        if (level == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_FLOW);
        int water = BiomeColors.getAverageWaterColor(level, jet.getBlockPos());
        float r = mix((water >> 16 & 255) / 255.0F, 0.5F);
        float g = mix((water >> 8 & 255) / 255.0F, 0.5F);
        float b = mix((water & 255) / 255.0F, 0.5F);
        Mesh mesh = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)),
                packedLight).color(r, g, b, 0.72F);

        int power = jet.getBlockState().getValue(FountainSprayBlock.POWER);
        Direction facing = jet.getBlockState().getValue(FountainSprayBlock.FACING);
        double[] nozzle = FountainSprayBlock.nozzle(facing);
        double time = (level.getGameTime() + partialTick) / 20.0;
        double wobble = 0.015 * Math.sin(time * 9.0 + jet.getBlockPos().asLong() % 7);
        if (facing == Direction.UP) {
            column(mesh, sprite, nozzle, power, time, wobble);
        } else {
            spout(mesh, sprite, nozzle, facing, power, jet.landingDrop(), wobble);
        }
    }

    /** A vertical jet: a tapering tube to the apex, then a short umbrella of water falling away. */
    private static void column(Mesh mesh, TextureAtlasSprite sprite, double[] nozzle, int power, double time,
                               double wobble) {
        double apex = WaterJet.apex(power);
        double base = 0.05 + 0.022 * power;
        int segments = 6 + power * 2;
        double[][] ring = null;
        double[][] previous = null;
        for (int s = 0; s <= segments; s++) {
            double f = (double) s / segments;
            double y = nozzle[1] + apex * f;
            double radius = base * (1.0 - 0.55 * f);
            double sway = wobble * f * (1.0 + power * 0.4);
            ring = ring(nozzle[0] + sway, y, nozzle[2] + sway * 0.6, radius, time * 0.5);
            if (previous != null) {
                tube(mesh, sprite, previous, ring, f - 1.0 / segments, f);
            }
            previous = ring;
        }
        // the crown: the water turns over and falls back in a widening skirt
        double crown = 0.18 + 0.12 * power;
        double[][] lip = ring(nozzle[0] + wobble, nozzle[1] + apex + 0.06, nozzle[2], base * 0.6, time * 0.5);
        double[][] skirt = ring(nozzle[0] + wobble, nozzle[1] + apex - 0.25 - 0.08 * power, nozzle[2], crown,
                time * 0.5);
        tube(mesh, sprite, ring, lip, 0.0, 0.2);
        tube(mesh, sprite, lip, skirt, 0.2, 1.0);                // underside of the falling skirt
        tube(mesh, sprite, skirt, lip, 1.0, 0.2);                // and its top, seen from outside
    }

    /** A spout: a thinning tube along the ballistic arc down to where the water lands. */
    private static void spout(Mesh mesh, TextureAtlasSprite sprite, double[] nozzle, Direction facing, int power,
                              double landingDrop, double wobble) {
        double end = WaterJet.spoutTimeToDrop(power, landingDrop);
        int segments = Mth.clamp((int) Math.round(end / 2.5), 8, 24);
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double base = 0.045 + 0.012 * power;
        double[][] previous = null;
        for (int s = 0; s <= segments; s++) {
            double t = end * s / segments;
            double[] p = WaterJet.spoutPoint(power, t);
            double f = (double) s / segments;
            double radius = base * (1.0 - 0.35 * f) + 0.01 * f;
            double[][] ring = ring(nozzle[0] + fx * p[0] + wobble * f, nozzle[1] + p[1], nozzle[2] + fz * p[0], radius,
                    0.0);
            if (previous != null) {
                tube(mesh, sprite, previous, ring, f - 1.0 / segments, f);
            }
            previous = ring;
        }
    }

    /** A horizontal ring of SIDES points (good enough for a tube that bends gently). */
    private static double[][] ring(double x, double y, double z, double radius, double twist) {
        double[][] points = new double[SIDES][];
        for (int i = 0; i < SIDES; i++) {
            double a = twist + Math.PI * 2.0 * i / SIDES;
            points[i] = new double[]{x + Math.cos(a) * radius, y, z + Math.sin(a) * radius};
        }
        return points;
    }

    /**
     * Joins two rings with outward-facing quads. The texture runs along the tube from {@code v0} to {@code v1} (0..1
     * of the sprite), so the flowing-water animation moves the way the water goes.
     */
    private static void tube(Mesh mesh, TextureAtlasSprite sprite, double[][] from, double[][] to, double v0,
                             double v1) {
        float vA = sprite.getV(Mth.clamp(v0, 0.0, 1.0) * 16.0);
        float vB = sprite.getV(Mth.clamp(v1, 0.0, 1.0) * 16.0);
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            float uA = sprite.getU(8.0 * i / SIDES);
            float uB = sprite.getU(8.0 * (i + 1) / SIDES);
            double[] n = Mesh.normalOf(from[i], to[i], to[j]);
            mesh.vertex(from[i], uA, vA, n);
            mesh.vertex(to[i], uA, vB, n);
            mesh.vertex(to[j], uB, vB, n);
            mesh.vertex(from[j], uB, vA, n);
        }
    }

    private static float mix(float channel, float white) {
        return channel + (1.0F - channel) * white;
    }

    @Override
    public boolean shouldRenderOffScreen(FountainSprayBlock.Entity jet) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
