package com.eternalrealm.world.forge.feature;

import com.eternalrealm.world.forge.DarkTowerShape;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Draws the part of the dark tower ({@link DarkTowerShape}) that lies in the chunk being decorated, so the spire
 * towers over the wasteland as soon as its chunks exist. Placed once per chunk (chunk corner) in every area 7 biome;
 * chunks away from the tower return immediately. Only writes inside its own chunk.
 */
public final class DarkTowerFeature extends Feature<NoneFeatureConfiguration> {
    public DarkTowerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int chunkX = context.origin().getX() & ~15;
        int chunkZ = context.origin().getZ() & ~15;
        int reach = DarkTowerShape.REACH;
        int cx = DarkTowerShape.CENTER_X;
        int cz = DarkTowerShape.CENTER_Z;
        if (chunkX + 15 < cx - reach || chunkX > cx + reach || chunkZ + 15 < cz - reach || chunkZ > cz + reach) {
            return false;
        }
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        int groundY = DarkTowerShape.groundY(context.chunkGenerator(), level, server.getChunkSource().randomState());
        int body = DarkTowerShape.bodyHeight(groundY, level.getMaxBuildHeight());
        int top = Math.min(level.getMaxBuildHeight() - 1, groundY + body + DarkTowerShape.SPIRE + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(chunkX, cx - reach); x <= Math.min(chunkX + 15, cx + reach); x++) {
            for (int z = Math.max(chunkZ, cz - reach); z <= Math.min(chunkZ + 15, cz + reach); z++) {
                int dx = x - cx;
                int dz = z - cz;
                double oct = DarkTowerShape.octagon(dx, dz);
                if (oct > reach) {
                    continue;
                }
                if (oct <= DarkTowerShape.PLINTH_RADIUS) {
                    int terrain = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                    for (int y = Math.max(terrain + 1, groundY - DarkTowerShape.FOUNDATION_DEPTH); y < groundY; y++) {
                        level.setBlock(pos.set(x, y, z), DarkTowerShape.foundation(dx, dz, groundY - y), Block.UPDATE_CLIENTS);
                    }
                }
                for (int y = groundY; y <= top; y++) {
                    BlockState state = DarkTowerShape.blockAt(dx, y - groundY, dz, body);
                    if (state != null) {
                        level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }
}
