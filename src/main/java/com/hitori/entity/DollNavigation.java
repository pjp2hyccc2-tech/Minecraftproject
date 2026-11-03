package com.hitori.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paths for the doll: every door, fusuma and sliding door of the apartment counts as open (it gets through anything
 * that is not a wall — it was in the bath a moment ago and now it is in the kitchen). The doll follows the nodes
 * itself ({@link OniDoll}); this navigation only plans.
 */
public class DollNavigation extends GroundPathNavigation {
    private static final Map<Block, Boolean> PASSABLE = new ConcurrentHashMap<>();

    public DollNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new DollNodes();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    /** Doors, fusuma, sliding doors: the doll passes. */
    public static boolean passable(BlockState state) {
        return PASSABLE.computeIfAbsent(state.getBlock(), b -> {
            if (b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof TrapDoorBlock) return true;
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id == null || !id.getNamespace().equals("hitori")) return false;
            String p = id.getPath();
            return p.contains("door") || p.contains("fusuma") || p.equals("curtain");
        });
    }

    static class DollNodes extends WalkNodeEvaluator {
        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && passable(s)) return BlockPathTypes.DOOR_OPEN;
            return super.getBlockPathType(level, x, y, z);
        }
    }
}
