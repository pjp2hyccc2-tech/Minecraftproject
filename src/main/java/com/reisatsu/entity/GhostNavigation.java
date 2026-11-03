package com.reisatsu.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paths for ghosts: every door of the restaurant (staff doors, the automatic doors, the kitchen swing doors, stall
 * doors, the walk-in fridge) counts as open, walls do not. The spirits follow the computed nodes themselves
 * ({@link Spirit#walkTo}); this navigation only plans.
 */
public class GhostNavigation extends GroundPathNavigation {
    private static final Map<Block, Boolean> PASSABLE = new ConcurrentHashMap<>();

    public GhostNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new GhostNodes();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    /** Doors and gates a ghost walks through. */
    public static boolean ghostPassable(BlockState state) {
        return PASSABLE.computeIfAbsent(state.getBlock(), b -> {
            if (b instanceof DoorBlock || b instanceof FenceGateBlock) return true;
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            return id != null && id.getNamespace().equals("reisatsu") && id.getPath().contains("door") && !id.getPath().equals("door_switch");
        });
    }

    static class GhostNodes extends WalkNodeEvaluator {
        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && ghostPassable(s)) return BlockPathTypes.DOOR_OPEN;
            return super.getBlockPathType(level, x, y, z);
        }
    }
}
