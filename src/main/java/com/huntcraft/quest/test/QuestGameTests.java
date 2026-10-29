package com.huntcraft.quest.test;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.SafeZones;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.quest.QuestCamps;
import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.QuestInstance;
import com.huntcraft.quest.QuestManager;
import com.huntcraft.quest.QuestMap;
import com.huntcraft.quest.QuestRegions;
import com.huntcraft.quest.QuestRegistry;
import com.huntcraft.quest.QuestRewards;
import com.huntcraft.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Quest module smoke tests: definitions, quest-map worldgen + base camp, full quest lifecycle without players. */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private QuestGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void questDefinitionsValid(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        int keys = 0;
        for (QuestDef q : Quests.all()) {
            if (!ids.add(q.id)) helper.fail("duplicate quest id " + q.id);
            if (q.stars < 1 || q.stars > 6) helper.fail("bad stars " + q.id);
            if (q.targets.isEmpty() || q.timeLimitMinutes <= 0 || q.zenny <= 0 || q.hrp <= 0) helper.fail("bad quest " + q.id);
            if ((q.type == QuestDef.Type.GATHER) != q.targets.get(0).isItem()) helper.fail("target kind mismatch " + q.id);
            if (q.key) keys++;
            q.title();
        }
        if (ids.size() < 30) helper.fail("expected ~30 quests, got " + ids.size());
        if (keys < 10 || Quests.get(Quests.LR_URGENT) == null || Quests.get(Quests.HR_URGENT) == null) helper.fail("progression broken");
        Set<String> none = new HashSet<>();
        if (!Quests.starUnlocked(1, false, none) || Quests.starUnlocked(2, false, none) || Quests.starUnlocked(4, false, none)) helper.fail("unlock rules");
        Set<String> all = new HashSet<>(ids);
        for (QuestDef q : Quests.all()) if (!Quests.starUnlocked(q.stars, q.urgent, all)) helper.fail("not unlockable " + q.id);
        helper.succeed();
    }

    /** Generates terrain in every quest map (runs the custom noise router, surface rules, biomes and features). */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void questMapsGenerate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StringBuilder info = new StringBuilder();
        for (QuestMap map : QuestMap.values()) {
            ServerLevel level = server.getLevel(map.dimension);
            if (level == null) {
                helper.fail("quest map dimension missing: " + map.id);
                return;
            }
            BlockPos c = QuestRegions.center(QuestRegions.MAX_SLOTS - 1);
            int found = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LevelChunk ch = level.getChunk((c.getX() >> 4) + dx * 3, (c.getZ() >> 4) + dz * 3);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = level.getMinBuildHeight(); y < 260; y++) {
                                BlockState s = ch.getBlockState(new BlockPos(x, y, z));
                                Block b = s.getBlock();
                                if (b == QuestRegistry.GATHER_HERB.get() || b == QuestRegistry.GATHER_MINING.get() || b == QuestRegistry.GATHER_BONE.get()
                                        || b == QuestRegistry.GATHER_BUG.get() || b == QuestRegistry.GATHER_MUSHROOM.get()) found++;
                            }
                        }
                    }
                }
            }
            int y = QuestCamps.surfaceY(level, c.getX(), c.getZ());
            if (y <= level.getMinBuildHeight() + 4) helper.fail(map.id + ": no terrain generated (surface y=" + y + ")");
            info.append(map.id).append(" surface=").append(y).append(" gatherPoints=").append(found).append("; ");
        }
        HuntCraft.LOGGER.info("[QuestGameTests] {}", info);
        helper.succeed();
    }

    /** Starts a hunt quest without players, kills the target, waits for the return countdown and checks cleanup. */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void questLifecycle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestDef q = Quests.get("lr2_ignivald");
        if (q == null) {
            helper.fail("quest missing");
            return;
        }
        QuestInstance inst = QuestManager.start(server, q, List.of());
        if (inst == null) {
            helper.fail("quest instance could not start");
            return;
        }
        ServerLevel level = server.getLevel(q.map.dimension);
        if (level == null) {
            helper.fail("no level");
            return;
        }
        BlockState supply = level.getBlockState(inst.camp.offset(-5, 0, 0));
        if (supply.getBlock() != QuestRegistry.SUPPLY_BOX.get()) helper.fail("supply box not found at camp, found " + supply);
        if (!SafeZones.isInSafeZone(level, Vec3.atBottomCenterOf(inst.camp))) helper.fail("camp is not a safe zone");
        if (inst.supplies.isEmpty()) helper.fail("supply box empty");
        boolean registered = MonsterRegistry.get("ignivald") != null;
        if (registered && inst.targets.isEmpty()) helper.fail("target monster not spawned");
        CompoundTag preview = QuestRewards.rollSuccess(inst, q, level.random);
        if (preview.getList("items", 10).isEmpty()) helper.fail("no reward items rolled");
        helper.runAfterDelay(20, () -> {
            if (registered) {
                Entity e = level.getEntity(inst.targets.get(0));
                if (!(e instanceof HuntMonster m)) {
                    helper.fail("target entity not loaded");
                    return;
                }
                m.hurt(level.damageSources().generic(), 99999.0F);
            } else {
                QuestManager.complete(server, inst);
            }
        });
        helper.runAfterDelay(60, () -> {
            if (inst.state != QuestInstance.State.COMPLETE) helper.fail("quest not complete after target died: " + inst.state);
        });
        helper.runAfterDelay(60 + QuestManager.RETURN_DELAY_CLEAR + 40, () -> {
            if (QuestManager.data(server).instances.containsKey(inst.id)) helper.fail("instance not cleaned up");
            else if (SafeZones.isInSafeZone(level, Vec3.atBottomCenterOf(inst.camp))) helper.fail("camp safe zone not removed");
            else helper.succeed();
        });
    }
}
