package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.core.city.BuildingType;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.server.ForgeWorldAccess;
import com.frostbound.world.Terrain;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * World-space overlays of the management mode: heat map on the ground, heat range ring, selected building
 * outline and the placement ghost. Also remembers the frame's matrices for mouse picking.
 */
@Mod.EventBusSubscriber(modid = FrostboundMod.MOD_ID, value = Dist.CLIENT)
public final class WorldOverlay {
    public static boolean showHeat;
    public static String placing;
    public static int rotation;
    public static int selected = -1;
    /** Ground point under the mouse (management mode). */
    public static Vec3 hover;
    /** Why the ghost cannot be placed (language key), or null. */
    public static String problem;

    private static final Matrix4f proj = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean haveMatrices;

    private WorldOverlay() {
    }

    public static void reset() {
        placing = null;
        selected = -1;
        hover = null;
        problem = null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        proj.set(event.getProjectionMatrix());
        view.set(event.getPoseStack().last().pose());
        camPos = event.getCamera().getPosition();
        haveMatrices = true;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ManagementScreen) || !ClientCity.hasCity()) {
            return;
        }
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (showHeat) {
            heat(m);
        }
        lines(m);
        if (placing != null && hover != null) {
            ghostFill(m);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        ps.popPose();
    }

    private static void heat(Matrix4f m) {
        byte[] grid = ClientCity.heatGrid();
        if (grid == null) {
            return;
        }
        int cell = ClientCity.heatCell();
        int size = ClientCity.heatHalf() * 2 / cell;
        if (grid.length < size * size) {
            return;
        }
        int x0 = ClientCity.heatCx() - ClientCity.heatHalf();
        int z0 = ClientCity.heatCz() - ClientCity.heatHalf();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int iz = 0; iz < size; iz++) {
            for (int ix = 0; ix < size; ix++) {
                int v = grid[iz * size + ix] & 0xFF;
                if (v == 0) {
                    continue;
                }
                double t = v - 100;
                int c = Theme.tempColor(t);
                float r = ((c >> 16) & 0xFF) / 255f;
                float g = ((c >> 8) & 0xFF) / 255f;
                float b = (c & 0xFF) / 255f;
                float x = x0 + ix * cell;
                float z = z0 + iz * cell;
                float y = Terrain.height(x0 + ix * cell + cell / 2, z0 + iz * cell + cell / 2) + 1.06f;
                quad(bb, m, x + 0.1f, z + 0.1f, x + cell - 0.1f, z + cell - 0.1f, y, r, g, b, 0.38f);
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float x0, float z0, float x1, float z1, float y, float r, float g,
            float b, float a) {
        bb.vertex(m, x0, y, z0).color(r, g, b, a).endVertex();
        bb.vertex(m, x0, y, z1).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y, z1).color(r, g, b, a).endVertex();
        bb.vertex(m, x1, y, z0).color(r, g, b, a).endVertex();
    }

    private static void line(BufferBuilder bb, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        bb.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
    }

    private static void box(BufferBuilder bb, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
        line(bb, m, x0, y0, z0, x1, y0, z0, c);
        line(bb, m, x1, y0, z0, x1, y0, z1, c);
        line(bb, m, x1, y0, z1, x0, y0, z1, c);
        line(bb, m, x0, y0, z1, x0, y0, z0, c);
        line(bb, m, x0, y1, z0, x1, y1, z0, c);
        line(bb, m, x1, y1, z0, x1, y1, z1, c);
        line(bb, m, x1, y1, z1, x0, y1, z1, c);
        line(bb, m, x0, y1, z1, x0, y1, z0, c);
        line(bb, m, x0, y0, z0, x0, y1, z0, c);
        line(bb, m, x1, y0, z0, x1, y1, z0, c);
        line(bb, m, x1, y0, z1, x1, y1, z1, c);
        line(bb, m, x0, y0, z1, x0, y1, z1, c);
    }

    private static void lines(Matrix4f m) {
        RenderSystem.lineWidth(2.0f);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        CompoundTag g = ClientCity.generator();
        double gx = g.getInt("x") + 0.5;
        double gz = g.getInt("z") + 0.5;
        double range = g.getDouble("range") > 0 ? g.getDouble("range") : g.getDouble("nominalRange");
        int ringColor = g.getDouble("range") > 0 ? 0xC0FFA040 : 0x80808080;
        ring(bb, m, gx, gz, range, ringColor);
        ring(bb, m, gx, gz, City.CITY_RADIUS, 0x6080B0E0);
        if (selected >= 0) {
            CompoundTag b = ClientCity.building(selected);
            if (b != null) {
                BuildingType t = BuildingTypes.get(b.getString("type"));
                int h = t == null ? 4 : t.height;
                int x = b.getInt("x");
                int z = b.getInt("z");
                int y = b.getInt("y");
                box(bb, m, x - 0.05, y - 0.02, z - 0.05, x + b.getInt("sx") + 0.05, y + h + 0.5, z + b.getInt("sz") + 0.05, 0xFFFFE080);
            }
        }
        if (placing != null && hover != null) {
            BuildingType t = BuildingTypes.get(placing);
            if (t != null) {
                int[] fp = footprint(t, hover);
                int y = ForgeWorldAccess.floorHeight(fp[0] + fp[2] / 2, fp[1] + fp[3] / 2);
                int c = problem == null ? 0xFF7CFF7C : 0xFFFF6050;
                box(bb, m, fp[0], y, fp[1], fp[0] + fp[2], y + t.height, fp[1] + fp[3], c);
                double fx = fp[0] + fp[2] / 2.0;
                double fz = fp[1] + fp[3] / 2.0;
                double[] dir = switch (rotation) {
                    case 1 -> new double[] {-1, 0};
                    case 2 -> new double[] {0, -1};
                    case 3 -> new double[] {1, 0};
                    default -> new double[] {0, 1};
                };
                double ex = fx + dir[0] * (fp[2] / 2.0 + 1.5);
                double ez = fz + dir[1] * (fp[3] / 2.0 + 1.5);
                line(bb, m, fx, y + 0.1, fz, ex, y + 0.1, ez, 0xFFFFC040);
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.lineWidth(1.0f);
    }

    private static void ring(BufferBuilder bb, Matrix4f m, double cx, double cz, double r, int c) {
        if (r <= 0) {
            return;
        }
        int seg = Math.max(24, (int) (r * 1.2));
        double px = cx + r;
        double pz = cz;
        double py = Terrain.height((int) Math.floor(px), (int) Math.floor(pz)) + 1.15;
        for (int i = 1; i <= seg; i++) {
            double a = i * Math.PI * 2 / seg;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            double y = Terrain.height((int) Math.floor(x), (int) Math.floor(z)) + 1.15;
            line(bb, m, px, py, pz, x, y, z, c);
            px = x;
            py = y;
            pz = z;
        }
    }

    private static void ghostFill(Matrix4f m) {
        BuildingType t = BuildingTypes.get(placing);
        if (t == null) {
            return;
        }
        int[] fp = footprint(t, hover);
        int y = ForgeWorldAccess.floorHeight(fp[0] + fp[2] / 2, fp[1] + fp[3] / 2);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (problem == null) {
            quad(bb, m, fp[0], fp[1], fp[0] + fp[2], fp[1] + fp[3], y + 0.04f, 0.45f, 1f, 0.45f, 0.35f);
        } else {
            quad(bb, m, fp[0], fp[1], fp[0] + fp[2], fp[1] + fp[3], y + 0.04f, 1f, 0.35f, 0.3f, 0.35f);
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** {x, z, sizeX, sizeZ} of the ghost centred on the hovered point. */
    public static int[] footprint(BuildingType t, Vec3 at) {
        int sx = rotation % 2 == 0 ? t.width : t.depth;
        int sz = rotation % 2 == 0 ? t.depth : t.width;
        int x = (int) Math.floor(at.x) - sx / 2;
        int z = (int) Math.floor(at.z) - sz / 2;
        return new int[] {x, z, sx, sz};
    }

    /** Client-side prediction of the server's placement check (the server decides). */
    public static String predict(BuildingType t, Vec3 at) {
        if (!ClientCity.unlocked(t.id)) {
            return "frostbound.reason.locked";
        }
        int[] fp = footprint(t, at);
        CompoundTag g = ClientCity.generator();
        double cx = fp[0] + fp[2] / 2.0;
        double cz = fp[1] + fp[3] / 2.0;
        if (Math.hypot(cx - (g.getInt("x") + 0.5), cz - (g.getInt("z") + 0.5)) > City.CITY_RADIUS) {
            return "frostbound.reason.too_far";
        }
        ListTag list = ClientCity.buildings();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int bx0 = b.getInt("x");
            int bz0 = b.getInt("z");
            int bx1 = bx0 + b.getInt("sx") - 1;
            int bz1 = bz0 + b.getInt("sz") - 1;
            if (fp[0] <= bx1 + 1 && fp[0] + fp[2] - 1 >= bx0 - 1 && fp[1] <= bz1 + 1 && fp[1] + fp[3] - 1 >= bz0 - 1) {
                return "frostbound.reason.overlap";
            }
        }
        String terrain = ForgeWorldAccess.checkTerrain(fp[0], fp[1], fp[2], fp[3]);
        if (terrain != null) {
            return terrain;
        }
        for (ResourceType r : ResourceType.values()) {
            int need = t.cost.get(r);
            if (need > 0 && ClientCity.amount(r.id()) + 1e-6 < need) {
                return "frostbound.reason.resources";
            }
        }
        return null;
    }

    /** The ground point under the mouse, using the last frame's camera. */
    public static Vec3 pick(double mouseX, double mouseY, int screenW, int screenH) {
        if (!haveMatrices || screenW <= 0 || screenH <= 0) {
            return null;
        }
        float ndcX = (float) (mouseX / screenW * 2.0 - 1.0);
        float ndcY = (float) (1.0 - mouseY / screenH * 2.0);
        Matrix4f inv = new Matrix4f(proj).mul(view).invert();
        Vector4f n = new Vector4f(ndcX, ndcY, -1f, 1f);
        inv.transform(n);
        n.div(n.w);
        Vector4f f = new Vector4f(ndcX, ndcY, 1f, 1f);
        inv.transform(f);
        f.div(f.w);
        Vec3 origin = camPos.add(n.x, n.y, n.z);
        Vec3 dir = new Vec3(f.x - n.x, f.y - n.y, f.z - n.z).normalize();
        double step = 0.5;
        for (double t = 0; t < 500; t += step) {
            Vec3 p = origin.add(dir.scale(t));
            int ground = Terrain.height((int) Math.floor(p.x), (int) Math.floor(p.z)) + 1;
            if (p.y <= ground) {
                return new Vec3(p.x, ground, p.z);
            }
        }
        return null;
    }

    /** Ensures a used Cost import (kept for clarity of the prediction). */
    static int costOf(Cost c, ResourceType r) {
        return c.get(r);
    }
}
