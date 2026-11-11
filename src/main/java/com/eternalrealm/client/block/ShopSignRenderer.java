package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.ShopSignBlock;
import com.eternalrealm.block.forge.ShopSignBlockEntity;
import com.eternalrealm.world.town.BuntingSway;
import com.eternalrealm.world.town.SignSwing;
import com.eternalrealm.world.town.TownWind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The swinging board of a shop sign: a wooden board in an iron frame with the trade's picture painted on both faces
 * (each face reads the right way round), hanging from two iron rings under the bracket arm ({@code wall}) or from two
 * short chains under the ceiling ({@code ceiling}), and swinging gently about its top edge in the town wind (more when
 * the wind meets its face). The bracket itself is the block model.
 */
public class ShopSignRenderer implements BlockEntityRenderer<ShopSignBlockEntity> {
    private static final RenderType TYPE = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    private static final double PX = 1.0 / 16.0;
    /** Board size: 16 x 12 pixels, a pixel thick (the board sprites are 64 x 48: four texels a pixel). */
    private static final double WIDTH = 16.0 * PX;
    private static final double HEIGHT = 12.0 * PX;
    private static final double THICK = 1.0 * PX;

    private final QuadWriter quads = new QuadWriter();

    public ShopSignRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShopSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = sign.getLevel();
        BlockState state = sign.getBlockState();
        if (level == null || !(state.getBlock() instanceof ShopSignBlock)) {
            return;
        }
        Direction facing = state.getValue(ShopSignBlock.FACING);
        boolean wall = state.getValue(ShopSignBlock.ATTACH) == ShopSignBlock.Attach.WALL;
        ShopSignBlock.Icon icon = state.getValue(ShopSignBlock.ICON);
        BlockPos pos = sign.getBlockPos();

        // m: the board's face normal at rest; e = up x m runs along the board (left to right seen from the front)
        double fx = facing.getStepX();
        double fz = facing.getStepZ();
        double mx = wall ? -fz : fx;
        double mz = wall ? fx : fz;
        double ex = mz;
        double ez = -mx;

        double seconds = (level.getGameTime() + partialTick) / 20.0;
        double wind = TownWind.strength(seconds, pos.asLong(), level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick));
        double push = mx * BuntingSway.WIND_X + mz * BuntingSway.WIND_Z;
        double angle = SignSwing.angle(seconds, pos.asLong(), wind, push);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // the board's down and face normal after the swing about the top edge (the e axis)
        double dx = mx * sin;
        double dy = -cos;
        double dz = mz * sin;
        double nx = mx * cos;
        double ny = sin;
        double nz = mz * cos;

        // pivot: the bracket arm (wall) or the ceiling hooks; where the board's top edge hangs below it
        double pivotX = 0.5;
        double pivotY = wall ? 12.5 * PX : 1.0;
        double pivotZ = 0.5;
        double hang = wall ? 2.0 * PX : 4.5 * PX;
        double along = wall ? 1.0 * PX : 0.0;     // wall boards keep a pixel clear of the wall
        double cx = pivotX + ex * along;
        double cz = pivotZ + ez * along;

        quads.begin(pose.last(), buffers.getBuffer(TYPE), packedLight);
        double topX = cx + dx * hang;
        double topY = pivotY + dy * hang;
        double topZ = cz + dz * hang;
        double midX = topX + dx * HEIGHT * 0.5;
        double midY = topY + dy * HEIGHT * 0.5;
        double midZ = topZ + dz * HEIGHT * 0.5;
        board(midX, midY, midZ, ex, ez, dx, dy, dz, nx, ny, nz, GimmickSprites.get(GimmickSprites.SIGN_BOARD
                + icon.ordinal()));

        // hangers: two rings under the arm, or two chains up to the ceiling, swinging with the board
        double offset = wall ? 5.0 * PX : 6.5 * PX;
        TextureAtlasSprite hanger = GimmickSprites.get(wall ? GimmickSprites.SIGN_RING : GimmickSprites.SIGN_CHAIN);
        double half = (hang + 0.6 * PX) * 0.5;
        double width = wall ? 1.25 * PX : 1.6 * PX;
        for (int k = -1; k <= 1; k += 2) {
            double hx = cx + ex * offset * k + dx * half;
            double hy = pivotY + dy * half;
            double hz = cz + ez * offset * k + dz * half;
            quad(hx, hy, hz, ex, 0.0, ez, -dx, -dy, -dz, width, half, hanger, (float) nx, (float) ny, (float) nz);
            quad(hx, hy, hz, nx, ny, nz, -dx, -dy, -dz, width, half, hanger, (float) ex, 0.0F, (float) ez);
        }
    }

    /** The board: both painted faces (each reading the right way round) and the iron-bound edges. */
    private void board(double cx, double cy, double cz, double ex, double ez, double dx, double dy, double dz,
                       double nx, double ny, double nz, TextureAtlasSprite face) {
        double hw = WIDTH * 0.5;
        double hh = HEIGHT * 0.5;
        double ht = THICK * 0.5;
        float u0 = face.getU0();
        float u1 = face.getU1();
        float v0 = face.getV0();
        float v1 = face.getV1();
        quads.color(1.0F, 1.0F, 1.0F, 1.0F);
        // front (+n): left = -e
        double fx = cx + nx * ht;
        double fy = cy + ny * ht;
        double fz = cz + nz * ht;
        quads.vertex(fx - ex * hw + dx * hh, fy + dy * hh, fz - ez * hw + dz * hh, u0, v1, (float) nx, (float) ny,
                (float) nz);
        quads.vertex(fx + ex * hw + dx * hh, fy + dy * hh, fz + ez * hw + dz * hh, u1, v1, (float) nx, (float) ny,
                (float) nz);
        quads.vertex(fx + ex * hw - dx * hh, fy - dy * hh, fz + ez * hw - dz * hh, u1, v0, (float) nx, (float) ny,
                (float) nz);
        quads.vertex(fx - ex * hw - dx * hh, fy - dy * hh, fz - ez * hw - dz * hh, u0, v0, (float) nx, (float) ny,
                (float) nz);
        // back (-n): seen from behind its left is +e, painted the right way round
        double bx = cx - nx * ht;
        double by = cy - ny * ht;
        double bz = cz - nz * ht;
        quads.vertex(bx + ex * hw + dx * hh, by + dy * hh, bz + ez * hw + dz * hh, u0, v1, (float) -nx, (float) -ny,
                (float) -nz);
        quads.vertex(bx - ex * hw + dx * hh, by + dy * hh, bz - ez * hw + dz * hh, u1, v1, (float) -nx, (float) -ny,
                (float) -nz);
        quads.vertex(bx - ex * hw - dx * hh, by - dy * hh, bz - ez * hw - dz * hh, u1, v0, (float) -nx, (float) -ny,
                (float) -nz);
        quads.vertex(bx + ex * hw - dx * hh, by - dy * hh, bz + ez * hw - dz * hh, u0, v0, (float) -nx, (float) -ny,
                (float) -nz);
        // edges: top, bottom, both ends
        TextureAtlasSprite edge = GimmickSprites.get(GimmickSprites.SIGN_EDGE);
        quads.color(0.92F, 0.92F, 0.92F, 1.0F);
        quad(cx - dx * hh, cy - dy * hh, cz - dz * hh, ex, 0.0, ez, nx, ny, nz, hw, ht, edge, (float) -dx, (float) -dy,
                (float) -dz);
        quad(cx + dx * hh, cy + dy * hh, cz + dz * hh, -ex, 0.0, -ez, nx, ny, nz, hw, ht, edge, (float) dx, (float) dy,
                (float) dz);
        quad(cx + ex * hw, cy, cz + ez * hw, -dx, -dy, -dz, nx, ny, nz, hh, ht, edge, (float) ex, 0.0F, (float) ez);
        quad(cx - ex * hw, cy, cz - ez * hw, dx, dy, dz, nx, ny, nz, hh, ht, edge, (float) -ex, 0.0F, (float) -ez);
    }

    /**
     * A quad centred at c, +-ha along a (sprite u) and +-hb along b (the sprite's up), facing a x b. Drawn both ways
     * round, so thin hangers show from either side.
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
        quads.vertex(cx - aX - bX, cy - aY - bY, cz - aZ - bZ, u0, v1, nx, ny, nz);
        quads.vertex(cx + aX - bX, cy + aY - bY, cz + aZ - bZ, u1, v1, nx, ny, nz);
        quads.vertex(cx + aX + bX, cy + aY + bY, cz + aZ + bZ, u1, v0, nx, ny, nz);
        quads.vertex(cx - aX + bX, cy - aY + bY, cz - aZ + bZ, u0, v0, nx, ny, nz);
        quads.vertex(cx - aX + bX, cy - aY + bY, cz - aZ + bZ, u0, v0, -nx, -ny, -nz);
        quads.vertex(cx + aX + bX, cy + aY + bY, cz + aZ + bZ, u1, v0, -nx, -ny, -nz);
        quads.vertex(cx + aX - bX, cy + aY - bY, cz + aZ - bZ, u1, v1, -nx, -ny, -nz);
        quads.vertex(cx - aX - bX, cy - aY - bY, cz - aZ - bZ, u0, v1, -nx, -ny, -nz);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
