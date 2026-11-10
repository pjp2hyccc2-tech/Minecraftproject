package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.FacadeMechanismBlock;
import com.eternalrealm.block.forge.WindmillSailsBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.town.TownWind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Windmill sails: an axle out of the mill cap, an iron-banded hub and four stocks carrying lattice sails with canvas,
 * set at a slight twist. They turn counter-clockwise (seen from the front) with the town's wind: lazily in a calm,
 * briskly in a storm.
 */
public class WindmillRenderer implements BlockEntityRenderer<WindmillSailsBlockEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/windmill/sails.png");
    private static final float TEX = 128.0F;
    /** Distance of the sail plane in front of the mill's face. */
    private static final double SAIL_PLANE = 1.05;

    public WindmillRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WindmillSailsBlockEntity mill, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = mill.getLevel();
        if (level == null) {
            return;
        }
        double seconds = (level.getGameTime() + partialTick) / 20.0;
        double wind = TownWind.strength(seconds, mill.getBlockPos().asLong() / 7L, level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick));
        double radius = mill.radius();
        // the sail tips move at roughly 3..10 blocks per second
        double angularSpeed = (1.8 + 6.0 * wind) / radius;
        double last = mill.clientLastSeconds;
        if (!Double.isNaN(last)) {
            double dt = seconds - last;
            if (dt > 0.0 && dt < 1.0) {
                mill.clientAngle += angularSpeed * dt;                     // counter-clockwise from the front
                if (mill.clientAngle > 1.0e5) {
                    mill.clientAngle -= Math.PI * 2.0 * 10000.0;
                }
            }
        }
        mill.clientLastSeconds = seconds;

        Direction facing = mill.getBlockState().getValue(FacadeMechanismBlock.FACING);
        double[] n = {facing.getStepX(), 0.0, facing.getStepZ()};
        double[] up = {0.0, 1.0, 0.0};
        double[] right = Mesh.cross(up, n);
        double[] centre = {0.5 + n[0] * 0.5, 0.5, 0.5 + n[2] * 0.5};
        int light = LevelRenderer.getLightColor(level, mill.getBlockPos().relative(facing));
        Mesh mesh = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light);

        double angle = mill.clientAngle;
        double[] spin = {Math.cos(angle), Math.sin(angle)};
        // axle and hub turn with the sails
        double[] ax = rotate(right, up, spin, 1.0, 0.0);
        double[] ay = rotate(right, up, spin, 0.0, 1.0);
        mesh.box(Mesh.add(centre, n, 0.6), ax, ay, n, 0.17, 0.17, 0.6, 64.0F / TEX, 32.0F / TEX, 1.0F,
                48.0F / TEX);
        mesh.box(Mesh.add(centre, n, SAIL_PLANE), ax, ay, n, 0.42, 0.42, 0.24, 64.0F / TEX, 0.0F, 96.0F / TEX,
                32.0F / TEX);
        for (int k = 0; k < 4; k++) {
            double beta = angle + k * Math.PI / 2.0;
            double[] along = rotate(right, up, new double[]{Math.cos(beta), Math.sin(beta)}, 0.0, 1.0);
            double[] across = rotate(right, up, new double[]{Math.cos(beta), Math.sin(beta)}, 1.0, 0.0);
            sail(mesh, centre, n, along, across, radius);
        }
    }

    /**
     * One sail: the stock (a long beam from the hub to the tip) and the lattice with canvas on its trailing side,
     * twisted so its outer edge stands a little proud of the stock.
     */
    private static void sail(Mesh mesh, double[] centre, double[] n, double[] along, double[] across, double radius) {
        double inner = 0.3;
        double length = radius - inner;
        double[] stockCentre = Mesh.add(Mesh.add(centre, n, SAIL_PLANE + 0.05), along, inner + length / 2.0);
        mesh.box(stockCentre, across, along, n, 0.11, length / 2.0, 0.11, 64.0F / TEX, 64.0F / TEX, 1.0F,
                72.0F / TEX);

        // trailing side: the sails turn towards -across, so the canvas sits on +across
        double r0 = radius * 0.2;
        double r1 = radius;
        double w0 = 0.12;
        double w1 = 0.12 + radius * 0.3;
        double z0 = SAIL_PLANE + 0.02;
        double z1 = SAIL_PLANE + 0.02 + radius * 0.045;
        double[] hubNear = point(centre, n, along, across, r0, w0, z0);
        double[] hubFar = point(centre, n, along, across, r0, w1, z1);
        double[] tipFar = point(centre, n, along, across, r1, w1, z1);
        double[] tipNear = point(centre, n, along, across, r1, w0, z0);
        // texture: u across (0 at the stock), v along (0 at the tip)
        mesh.quadTwoSided(tipNear, hubNear, hubFar, tipFar, 0.0F, 0.0F, 64.0F / TEX, 1.0F);
        // a narrow leading board on the other side of the stock
        double[] boardCentre = point(centre, n, along, across, (r0 + r1) / 2.0, -0.2, SAIL_PLANE + 0.04);
        mesh.box(boardCentre, across, along, n, 0.09, (r1 - r0) / 2.0, 0.03, 64.0F / TEX, 64.0F / TEX, 1.0F,
                72.0F / TEX);
    }

    private static double[] point(double[] centre, double[] n, double[] along, double[] across, double r, double w,
                                  double z) {
        return new double[]{centre[0] + along[0] * r + across[0] * w + n[0] * z,
                centre[1] + along[1] * r + across[1] * w + n[1] * z,
                centre[2] + along[2] * r + across[2] * w + n[2] * z};
    }

    /** The vector (x, y) of the face plane rotated by the angle whose cos/sin are {@code spin}. */
    private static double[] rotate(double[] right, double[] up, double[] spin, double x, double y) {
        double rx = x * spin[0] - y * spin[1];
        double ry = x * spin[1] + y * spin[0];
        return new double[]{right[0] * rx + up[0] * ry, right[1] * rx + up[1] * ry, right[2] * rx + up[2] * ry};
    }

    @Override
    public boolean shouldRenderOffScreen(WindmillSailsBlockEntity mill) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
