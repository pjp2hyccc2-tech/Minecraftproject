package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockFill;
import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonLayoutGenerator;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.InstanceSlot;
import com.eternalrealm.dungeon.decor.DecorBlock;
import com.eternalrealm.dungeon.decor.DecorPlan;
import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.dungeon.forge.DungeonPalette;
import com.eternalrealm.dungeon.forge.DungeonSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A dungeon site (docs/modules/boss.md §4) built and cleared the light way — a slice per tick within a time limit,
 * blocks set without neighbour updates and the bars and fences fitted afterwards — ends up exactly as planned: every
 * planned block in place (the decoration's own blocks aside), the sealed doors' bars and fences joined to their
 * neighbours, the forest's leafy floor marks kept from decaying, and afterwards every box empty again.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonSiteGameTests {
    private static final String EMPTY = "empty";
    /** Far from the slots real runs take (the lowest free one first). */
    private static final int SLOT = 4000;
    private static final long SLICE_NANOS = 25_000_000L;

    private DungeonSiteGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 3000)
    public static void aSiteIsBuiltAsPlannedAndClearedAgain(GameTestHelper helper) {
        ServerLevel level = DungeonLevels.level(helper.getLevel().getServer());
        DungeonLayout layout = DungeonLayoutGenerator.generate(Dungeons.byId("d2").orElseThrow(), 11L);
        DungeonSite site = new DungeonSite("d2", layout, new InstanceSlot(SLOT), level);
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null || stage.get() >= 2) {
                return;
            }
            try {
                long now = level.getServer().getTickCount();
                if (stage.get() == 0 && site.build(System.nanoTime() + SLICE_NANOS, now)) {
                    checkBuilt(level, site);
                    site.startClearing();
                    stage.set(1);
                } else if (stage.get() == 1 && site.clear(System.nanoTime() + SLICE_NANOS, now)) {
                    checkCleared(level, site);
                    stage.set(2);
                }
            } catch (GameTestAssertException e) {
                site.releaseChunks();
                failure.set(e);
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            check(stage.get() == 2, "stage " + stage.get() + " (" + site.phase() + ")");
        });
    }

    private static void checkBuilt(ServerLevel level, DungeonSite site) {
        DungeonLayout layout = site.layout;
        BlockPos origin = site.origin;
        Map<Long, BlockRole> planned = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockFill fill : layout.buildPlan()) {
            BlockBox box = fill.box();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        planned.put(cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z).asLong(),
                                fill.role()); // later fills win, as when building
                    }
                }
            }
        }
        Set<Long> decorated = new HashSet<>();
        DecorPlan decor = site.decor.plan().orElse(null);
        check(decor != null, "the decoration was planned");
        for (DecorBlock block : decor.blocks()) {
            decorated.add(cursor.set(origin.getX() + block.x(), origin.getY() + block.y(), origin.getZ() + block.z())
                    .asLong());
        }
        List<String> problems = new ArrayList<>();
        int doors = 0;
        int leaves = 0;
        for (Map.Entry<Long, BlockRole> entry : planned.entrySet()) {
            if (decorated.contains(entry.getKey())) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getKey());
            BlockState want = DungeonPalette.block(layout.theme(), entry.getValue(), pos.getX(), pos.getY(), pos.getZ());
            BlockState got = level.getBlockState(pos);
            boolean joins = want.getBlock() instanceof IronBarsBlock || want.getBlock() instanceof FenceBlock;
            if (joins ? got.getBlock() != want.getBlock() : got != want) {
                if (problems.size() < 8) {
                    problems.add(pos.toShortString() + " " + entry.getValue() + ": " + got + " instead of " + want);
                }
                continue;
            }
            if (joins) {
                doors++;
                boolean nextToDecor = decorated.contains(pos.east().asLong()) || decorated.contains(pos.west().asLong())
                        || decorated.contains(pos.north().asLong()) || decorated.contains(pos.south().asLong());
                if (!nextToDecor && Block.updateFromNeighbourShapes(got, level, pos) != got) {
                    problems.add(pos.toShortString() + ": " + got + " is not joined to its neighbours");
                }
            }
            if (got.hasProperty(LeavesBlock.PERSISTENT)) {
                leaves++;
                if (!got.getValue(LeavesBlock.PERSISTENT)) {
                    problems.add(pos.toShortString() + ": leaves that would decay");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new GameTestAssertException(problems.size() + " problems in " + planned.size() + " planned blocks: "
                    + String.join("; ", problems));
        }
        check(doors > 0, "the forest dungeon has fenced doors");
        check(leaves > 0, "the forest dungeon has leafy floor marks");
    }

    private static void checkCleared(ServerLevel level, DungeonSite site) {
        List<BlockBox> boxes = new ArrayList<>(site.layout.occupiedBoxes());
        boxes.addAll(site.decor.extraBoxes());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = site.origin;
        for (BlockBox box : boxes) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!level.getBlockState(cursor).isAir()) {
                            throw new GameTestAssertException("left behind at " + cursor.toShortString() + ": "
                                    + level.getBlockState(cursor));
                        }
                    }
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
