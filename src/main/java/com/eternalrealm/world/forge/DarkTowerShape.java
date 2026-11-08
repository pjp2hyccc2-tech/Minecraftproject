package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import javax.annotation.Nullable;

/**
 * Geometry of the dark tower (終焉の塔) at {@link AstoriaLayout#TOWER_X}, {@link AstoriaLayout#TOWER_Z}: an octagonal
 * plaza, a hollow octagonal body of blackstone with obsidian ribs, purple windows and inner floors that narrows towards
 * the top, a crown and an obsidian spire. World generation draws it chunk by chunk (so it is visible from afar), the
 * landmark builder later cuts the great gate into its south wall. Both derive the same ground level from the noise.
 */
public final class DarkTowerShape {
    public static final int CENTER_X = AstoriaLayout.TOWER_X;
    public static final int CENTER_Z = AstoriaLayout.TOWER_Z;
    public static final int BODY_RADIUS = 13;
    public static final int PLINTH_RADIUS = 22;
    /** Horizontal reach of everything the tower touches (plaza and cleared ground). */
    public static final int REACH = 24;
    public static final int CLEAR_HEIGHT = 40;
    public static final int FOUNDATION_DEPTH = 40;
    public static final int SPIRE = 20;
    private static final int MAX_BODY = 200;
    private static final double VERTEX_STEP = Math.PI / 4.0;

    private DarkTowerShape() {
    }

    /** y of the plaza floor (the tower stands on it). */
    public static int groundY(ChunkGenerator generator, LevelHeightAccessor level, RandomState random) {
        int base = generator.getBaseHeight(CENTER_X, CENTER_Z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
        return Mth.clamp(base, generator.getSeaLevel() + 1, 140);
    }

    /** Height of the body above the plaza floor (the spire adds {@link #SPIRE}). */
    public static int bodyHeight(int groundY, int maxBuildHeight) {
        return Math.max(60, Math.min(MAX_BODY, maxBuildHeight - 4 - SPIRE - groundY));
    }

    /** "Radius" of an octagon with its flat sides facing the axes. */
    public static double octagon(int dx, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        return Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2.0));
    }

    /** Outer radius of the body at height {@code dy} (1..body). */
    public static double radius(int dy, int body) {
        if (dy <= 40) {
            return BODY_RADIUS;
        }
        double t = (dy - 40) / (double) Math.max(1, body - 40);
        return BODY_RADIUS - 6.0 * Math.pow(t, 1.4);
    }

    /**
     * The tower block at an offset from the plaza centre ({@code dy} = 0 is the plaza floor): AIR for the hollow
     * inside and the cleared space above the plaza, null where the world is left as it is.
     */
    @Nullable
    public static BlockState blockAt(int dx, int dy, int dz, int body) {
        double oct = octagon(dx, dz);
        if (dy < 0 || oct > REACH) {
            return null;
        }
        if (dy == 0) {
            return oct <= PLINTH_RADIUS ? floor(dx, dz, oct) : null;
        }
        if (dy > body) {
            return crown(dx, dy - body, dz, oct, radius(body, body));
        }
        double r = radius(dy, body);
        if (oct <= r + 1.0 && oct > r - 2.0 && onRib(dx, dz, oct)) {
            return (dy % 7 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState();
        }
        if (oct <= r) {
            if (oct > r - 2.0) {
                return wall(dx, dy, dz, r);
            }
            return (dy % 20 == 0 ? Blocks.DEEPSLATE_TILES : Blocks.AIR).defaultBlockState();
        }
        if (dy == 1 && oct <= PLINTH_RADIUS && oct > PLINTH_RADIUS - 1) {
            return Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                    SlabType.BOTTOM);
        }
        return dy <= CLEAR_HEIGHT && oct <= PLINTH_RADIUS ? Blocks.AIR.defaultBlockState() : null;
    }

    /** Foundation block below the plaza at depth {@code below} (1 = just under the floor). */
    public static BlockState foundation(int dx, int dz, int below) {
        double oct = octagon(dx, dz);
        if (oct > PLINTH_RADIUS - 1 && below <= 3) {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
        return (GenNoise.hash(AstoriaLayout.SEED, dx, -below, dz) < 0.3 ? Blocks.DEEPSLATE : Blocks.COBBLED_DEEPSLATE)
                .defaultBlockState();
    }

    private static boolean onRib(int dx, int dz, double oct) {
        double angle = Math.atan2(dz, dx) - Math.PI / 8.0;
        double m = angle - Math.floor(angle / VERTEX_STEP) * VERTEX_STEP;
        double arc = Math.min(m, VERTEX_STEP - m) * Math.max(oct, 1.0);
        return arc < 0.9;
    }

    private static BlockState floor(int dx, int dz, double oct) {
        double hash = GenNoise.hash(AstoriaLayout.SEED, dx, 0, dz);
        Block block;
        if (oct <= BODY_RADIUS - 2) {
            block = ((Math.abs(dx) + Math.abs(dz)) % 6 == 0) ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_DEEPSLATE;
        } else if (((int) oct) % 5 == 0) {
            block = Blocks.POLISHED_BLACKSTONE_BRICKS;
        } else {
            block = hash < 0.12 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES;
        }
        return block.defaultBlockState();
    }

    private static BlockState wall(int dx, int dy, int dz, double r) {
        int phase = Math.floorMod(dy, 16);
        boolean window = phase >= 6 && phase <= 10 && !(dz > 0 && dy < 26)
                && ((Math.abs(dx) <= 1 && Math.abs(dz) > r - 2.5) || (Math.abs(dz) <= 1 && Math.abs(dx) > r - 2.5));
        if (window) {
            return Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
        }
        if (dy % 12 == 0) {
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        double hash = GenNoise.hash(AstoriaLayout.SEED, dx, dy, dz);
        Block block = hash < 0.14 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : hash < 0.2 ? Blocks.BLACKSTONE
                : hash < 0.21 ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
        return block.defaultBlockState();
    }

    @Nullable
    private static BlockState crown(int dx, int above, int dz, double oct, double topRadius) {
        if (above == 1 && oct <= topRadius && oct > topRadius - 1.0) {
            int segment = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 12.0));
            return segment % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : null;
        }
        if (above > SPIRE) {
            return null;
        }
        double spire = 4.5 * (1.0 - above / (double) (SPIRE + 1));
        if (oct <= spire) {
            boolean vein = GenNoise.hash(AstoriaLayout.SEED, dx, above, dz) < 0.25 || above == SPIRE;
            return (vein ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState();
        }
        return null;
    }
}
