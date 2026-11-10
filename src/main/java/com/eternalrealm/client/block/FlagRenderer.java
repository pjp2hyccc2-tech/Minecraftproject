package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.FlagBlock;
import com.eternalrealm.block.forge.FlagBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.FlagCloth;
import com.eternalrealm.world.town.TownWind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Waving cloth flags. The cloth is a grid (finer up close) shaped every frame by {@link FlagCloth} in the town's wind
 * ({@link TownWind}: gusts, stronger in rain and storms), lit with per-vertex normals so every ripple catches the
 * light, darkened in its folds, and drawn on both sides (the back shows the mirrored design, like real cloth).
 */
public class FlagRenderer implements BlockEntityRenderer<FlagBlockEntity> {
    private static final Map<FlagBlockEntity.Design, ResourceLocation> TEXTURES =
            new EnumMap<>(FlagBlockEntity.Design.class);
    private static final int MAX_COLS = 48;
    private static final int MAX_ROWS = 24;

    static {
        for (FlagBlockEntity.Design design : FlagBlockEntity.Design.values()) {
            TEXTURES.put(design, new ResourceLocation(ModInfo.MOD_ID, "textures/entity/flag/" + design.id() + ".png"));
        }
    }

    private final double[] grid = new double[(MAX_COLS + 1) * (MAX_ROWS + 1) * 3];
    private final float[] normals = new float[(MAX_COLS + 1) * (MAX_ROWS + 1) * 3];
    private final float[] shade = new float[(MAX_COLS + 1) * (MAX_ROWS + 1)];

    public FlagRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FlagBlockEntity flag, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = flag.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = flag.getBlockPos();
        double seconds = (level.getGameTime() + partialTick) / 20.0;
        long seed = pos.asLong();
        double wind = TownWind.strength(seconds, seed, level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick)) * flag.wind();
        wind = Mth.clamp(wind, 0.05, 1.6);
        double width = flag.width();
        double height = flag.height();
        advancePhase(flag, seconds, FlagCloth.angularSpeed(width, wind));

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(camera.distanceToSqr(Vec3.atCenterOf(pos)));
        double detail = distance < 40.0 ? 1.0 : distance < 80.0 ? 0.5 : 0.3;
        int cols = Mth.clamp((int) Math.round(width * 6.0 * detail), 4, MAX_COLS);
        int rows = Mth.clamp((int) Math.round(height * 4.0 * detail), 2, MAX_ROWS);
        FlagCloth.Shape shape = flag.design().shape();
        FlagCloth.compute(width, height, cols, rows, shape, flag.clientPhase, wind, grid);
        computeNormals(cols, rows);

        Direction facing = flag.getBlockState().getValue(FlagBlock.FACING);
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double lx = -fz;                       // sideways: the cloth's flat normal (front side)
        double lz = fx;
        double ox = 0.5 + fx * 0.08;
        double oy = 0.74;
        double oz = 0.5 + fz * 0.08;

        int light = clothLight(level, pos, facing, width, height, packedLight);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutout(TEXTURES.get(flag.design())));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normalMatrix = pose.last().normal();
        int stride = cols + 1;
        for (int row = 0; row < rows; row++) {
            float v0 = (float) row / rows;
            float v1 = (float) (row + 1) / rows;
            for (int col = 0; col < cols; col++) {
                float u0 = (float) col / cols;
                float u1 = (float) (col + 1) / cols;
                int a = row * stride + col;            // top, pole side
                int b = (row + 1) * stride + col;      // bottom, pole side
                int c = (row + 1) * stride + col + 1;  // bottom, fly side
                int d = row * stride + col + 1;        // top, fly side
                // front (seen from +sideways), then back with flipped normals
                put(out, matrix, normalMatrix, a, u0, v0, ox, oy, oz, fx, fz, lx, lz, light, 1.0F);
                put(out, matrix, normalMatrix, b, u0, v1, ox, oy, oz, fx, fz, lx, lz, light, 1.0F);
                put(out, matrix, normalMatrix, c, u1, v1, ox, oy, oz, fx, fz, lx, lz, light, 1.0F);
                put(out, matrix, normalMatrix, d, u1, v0, ox, oy, oz, fx, fz, lx, lz, light, 1.0F);
                put(out, matrix, normalMatrix, a, u0, v0, ox, oy, oz, fx, fz, lx, lz, light, -1.0F);
                put(out, matrix, normalMatrix, d, u1, v0, ox, oy, oz, fx, fz, lx, lz, light, -1.0F);
                put(out, matrix, normalMatrix, c, u1, v1, ox, oy, oz, fx, fz, lx, lz, light, -1.0F);
                put(out, matrix, normalMatrix, b, u0, v1, ox, oy, oz, fx, fz, lx, lz, light, -1.0F);
            }
        }
    }

    private void put(VertexConsumer out, Matrix4f matrix, Matrix3f normalMatrix, int index, float u, float v,
                     double ox, double oy, double oz, double fx, double fz, double lx, double lz, int light,
                     float side) {
        int i = index * 3;
        double x = grid[i];
        double y = grid[i + 1];
        double z = grid[i + 2];
        float px = (float) (ox + fx * x + lx * z);
        float py = (float) (oy + y);
        float pz = (float) (oz + fz * x + lz * z);
        float nx = normals[i] * side;
        float ny = normals[i + 1] * side;
        float nz = normals[i + 2] * side;
        // flag space -> world: x along facing, z along sideways
        float wx = (float) (fx * nx + lx * nz);
        float wz = (float) (fz * nx + lz * nz);
        float s = shade[index];
        out.vertex(matrix, px, py, pz).color(s, s, s, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normalMatrix, wx, ny, wz).endVertex();
    }

    /** Per-vertex normals in flag space (front side = +z) and a fold shade from how far each tilts. */
    private void computeNormals(int cols, int rows) {
        int stride = cols + 1;
        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= cols; col++) {
                int left = row * stride + Math.max(0, col - 1);
                int right = row * stride + Math.min(cols, col + 1);
                int up = Math.max(0, row - 1) * stride + col;
                int down = Math.min(rows, row + 1) * stride + col;
                double ux = grid[right * 3] - grid[left * 3];
                double uy = grid[right * 3 + 1] - grid[left * 3 + 1];
                double uz = grid[right * 3 + 2] - grid[left * 3 + 2];
                double vx = grid[down * 3] - grid[up * 3];
                double vy = grid[down * 3 + 1] - grid[up * 3 + 1];
                double vz = grid[down * 3 + 2] - grid[up * 3 + 2];
                // (down - up) x (right - left) points to +z for a flat flag
                double nx = vy * uz - vz * uy;
                double ny = vz * ux - vx * uz;
                double nz = vx * uy - vy * ux;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                int i = (row * stride + col) * 3;
                if (len < 1e-9) {
                    normals[i] = 0.0F;
                    normals[i + 1] = 0.0F;
                    normals[i + 2] = 1.0F;
                } else {
                    normals[i] = (float) (nx / len);
                    normals[i + 1] = (float) (ny / len);
                    normals[i + 2] = (float) (nz / len);
                }
                shade[row * stride + col] = 0.74F + 0.26F * Math.abs(normals[i + 2]);
            }
        }
    }

    private static void advancePhase(FlagBlockEntity flag, double seconds, double angularSpeed) {
        double last = flag.clientLastSeconds;
        double dt = Double.isNaN(last) ? 0.0 : seconds - last;
        if (Double.isNaN(last)) {
            flag.clientPhase = TownWind.phase(flag.getBlockPos().asLong()) * 4.0;
        }
        if (dt > 0.0 && dt < 1.0) {
            flag.clientPhase += angularSpeed * dt;
            if (flag.clientPhase > 1.0e6) {
                flag.clientPhase -= Math.PI * 2.0 * 100000.0;
            }
        }
        flag.clientLastSeconds = seconds;
    }

    /** Light at the middle of the cloth (it can reach out of the pole's shadow), never darker than at the pole. */
    private static int clothLight(Level level, BlockPos pos, Direction facing, double width, double height,
                                  int poleLight) {
        BlockPos middle = BlockPos.containing(pos.getX() + 0.5 + facing.getStepX() * width * 0.5,
                pos.getY() + 0.7 - height * 0.5, pos.getZ() + 0.5 + facing.getStepZ() * width * 0.5);
        int cloth = LevelRenderer.getLightColor(level, middle);
        int block = Math.max(LightTexture.block(cloth), LightTexture.block(poleLight));
        int sky = Math.max(LightTexture.sky(cloth), LightTexture.sky(poleLight));
        return LightTexture.pack(block, sky);
    }

    @Override
    public boolean shouldRenderOffScreen(FlagBlockEntity flag) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
