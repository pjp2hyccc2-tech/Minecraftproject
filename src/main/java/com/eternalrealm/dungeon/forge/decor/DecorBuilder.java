package com.eternalrealm.dungeon.forge.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.decor.DecorBlock;
import com.eternalrealm.dungeon.decor.DecorPlan;
import com.eternalrealm.dungeon.decor.DungeonDecor;
import com.eternalrealm.dungeon.forge.DungeonPalette;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Builds an instance's decoration ({@link DungeonDecor}) after its build plan: planned off the server thread while the
 * plan is being built, then placed a slice per tick like the plan. Blocks are set without shape updates (the plan
 * says how fences join and where lanterns hang), so nothing pops off while the rest is still going up.
 *
 * <p>If planning fails the instance simply stays undecorated (logged): the decoration never blocks a run.</p>
 */
public final class DecorBuilder {
    /** Decoration blocks placed per tick. */
    public static final int BLOCKS_PER_TICK = 4000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private final ServerLevel level;
    private final BlockPos origin;
    private final DungeonLayout layout;
    private final CompletableFuture<DecorPlan> planning;
    private DecorPlan plan;
    private boolean failed;
    private int index;

    public DecorBuilder(ServerLevel level, BlockPos origin, DungeonLayout layout) {
        this.level = level;
        this.origin = origin;
        this.layout = layout;
        this.planning = CompletableFuture.supplyAsync(() -> DungeonDecor.plan(layout));
    }

    /** The plan once it is ready (empty while planning, or if planning failed). */
    public Optional<DecorPlan> plan() {
        if (plan == null && !failed && planning.isDone()) {
            try {
                plan = planning.join();
            } catch (RuntimeException e) {
                failed = true;
                LOGGER.error("Decoration of dungeon {} (seed {}) failed; building it plain", layout.id(),
                        layout.seed(), e);
            }
        }
        return Optional.ofNullable(plan);
    }

    /** Places the next slice. @return whether the decoration is complete (or given up) */
    public boolean buildSlice() {
        Optional<DecorPlan> ready = plan();
        if (ready.isEmpty()) {
            return failed;
        }
        List<DecorBlock> blocks = ready.get().blocks();
        int budget = BLOCKS_PER_TICK;
        while (index < blocks.size() && budget-- > 0) {
            place(blocks.get(index++));
        }
        return index >= blocks.size();
    }

    private void place(DecorBlock block) {
        BlockPos pos = origin.offset(block.x(), block.y(), block.z());
        BlockState state;
        if (block.isRole()) {
            BlockRole role = BlockRole.valueOf(block.state().substring(1).toUpperCase(Locale.ROOT));
            state = DungeonPalette.block(layout.theme(), role, pos.getX(), pos.getY(), pos.getZ());
        } else {
            state = state(block.state());
            if (state == null) {
                return;
            }
        }
        level.setBlock(pos, state, FLAGS);
        if (block.nbt() != null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                try {
                    CompoundTag tag = entity.saveWithoutMetadata();
                    tag.merge(TagParser.parseTag(block.nbt()));
                    entity.load(tag);
                    entity.setChanged();
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                } catch (CommandSyntaxException | RuntimeException e) {
                    LOGGER.warn("Bad block entity data {} at {}: {}", block.nbt(), pos, e.getMessage());
                }
            }
        }
    }

    /** The block state of a state string, parsed once (null, logged once, if the game does not know it). */
    public static synchronized BlockState state(String text) {
        if (STATES.containsKey(text)) {
            return STATES.get(text);
        }
        BlockState state = null;
        try {
            state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Unknown decoration block state {}: {}", text, e.getMessage());
        }
        STATES.put(text, state);
        return state;
    }

    /**
     * Removes the decoration's water and lava without block updates, so nothing can flow while the walls around it
     * are cleared. Call before clearing the boxes.
     */
    public void drainFluids() {
        plan().ifPresent(ready -> {
            for (BlockBox box : ready.fluidBoxes()) {
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(box.minX(), box.minY(), box.minZ()),
                        origin.offset(box.maxX(), box.maxY(), box.maxZ()))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        });
    }

    /** Boxes outside the layout the decoration built into (to clear with the layout's boxes). */
    public List<BlockBox> extraBoxes() {
        return plan().map(DecorPlan::extraBoxes).orElse(List.of());
    }
}
