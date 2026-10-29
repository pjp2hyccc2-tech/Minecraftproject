package com.huntcraft.quest.test;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.SafeZones;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.quest.QuestBoardService;
import com.huntcraft.quest.QuestCamps;
import com.huntcraft.quest.QuestDef;
import com.huntcraft.quest.QuestInstance;
import com.huntcraft.quest.QuestManager;
import com.huntcraft.quest.QuestMap;
import com.huntcraft.quest.QuestRegions;
import com.huntcraft.quest.QuestRegistry;
import com.huntcraft.quest.QuestRewards;
import com.huntcraft.quest.Quests;
import com.huntcraft.quest.world.QuestMapData;
import com.huntcraft.registry.HCItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quest module tests: definitions, designed map data + worldgen camp, and the full quest loop with mock players
 * (accept -> depart -> base camp -> target spawned -> kill -> complete -> rewards; gather delivery; 3 faints = failure).
 */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private QuestGameTests() {
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new GameTestAssertException(msg);
    }

    /** Mock player connected like a real one (in the player list, own connection) - same approach as vanilla tests. */
    private static ServerPlayer mockPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer p = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name));
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel ch = new EmbeddedChannel(conn);
        ch.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        server.getPlayerList().placeNewPlayer(conn, p);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void removePlayer(ServerPlayer p) {
        try {
            QuestManager.abandon(p);
            p.server.getPlayerList().remove(p);
        } catch (RuntimeException e) {
            HuntCraft.LOGGER.warn("mock player cleanup failed", e);
        }
    }

    private static void unlockFor(ServerPlayer p, QuestDef q) {
        HunterData d = HunterData.get(p);
        for (QuestDef o : Quests.all()) if (o.stars < q.stars && o.key) d.completedQuests.add(o.id);
        d.hunterRank = Math.max(d.hunterRank, q.hrReq);
        d.addZenny(5000);
    }

    // ------------------------------------------------------------------ static checks
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

    /** Every designed map loads (no fallback), has 9 areas, a camp and gathering points; worldgen builds the camp. */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void questMapsGenerate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StringBuilder info = new StringBuilder();
        for (QuestMap map : QuestMap.values()) {
            QuestMapData d = map.data();
            check(!d.fallback, map.id + ": map data missing (fallback used)");
            check(d.areas.size() == 9, map.id + ": expected 9 areas, got " + d.areas.size());
            check(d.gatherCount() >= 40, map.id + ": too few gathering points " + d.gatherCount());
            check(!QuestCamps.largeAreas(map).isEmpty(), map.id + ": no large monster areas");
            ServerLevel level = server.getLevel(map.dimension);
            check(level != null, "quest map dimension missing: " + map.id);
            BlockPos center = QuestRegions.center(QuestRegions.MAX_SLOTS - 1 - map.ordinal());
            BlockPos supply = QuestCamps.fixture(map, center, d.camp.supply());
            BlockPos delivery = QuestCamps.fixture(map, center, d.camp.delivery());
            BlockPos flag = QuestCamps.fixture(map, center, d.camp.flag());
            level.getChunk(supply.getX() >> 4, supply.getZ() >> 4);
            level.getChunk(delivery.getX() >> 4, delivery.getZ() >> 4);
            level.getChunk(flag.getX() >> 4, flag.getZ() >> 4);
            BlockState s = level.getBlockState(supply);
            check(s.is(QuestRegistry.SUPPLY_BOX.get()), map.id + ": supply box missing at " + supply + " found " + s);
            check(level.getBlockState(delivery).is(QuestRegistry.DELIVERY_BOX.get()), map.id + ": delivery box missing at " + delivery);
            check(level.getBlockState(flag).is(QuestRegistry.CAMP_FLAG.get()), map.id + ": guild flag missing at " + flag);
            // a gathering point placed by worldgen
            BlockPos g = QuestRegions.toWorld(center, QuestMapData.unpack(d.gatherPos[0]));
            level.getChunk(g.getX() >> 4, g.getZ() >> 4);
            Block gb = level.getBlockState(g).getBlock();
            check(gb == QuestRegistry.GATHER_HERB.get() || gb == QuestRegistry.GATHER_MUSHROOM.get() || gb == QuestRegistry.GATHER_BUG.get()
                    || gb == QuestRegistry.GATHER_BONE.get() || gb == QuestRegistry.GATHER_MINING.get(), map.id + ": gathering point missing at " + g + " found " + gb);
            // terrain matches the data
            BlockPos camp = QuestCamps.campPos(map, center);
            int y = QuestCamps.surfaceY(level, camp.getX(), camp.getZ());
            check(Math.abs(y - camp.getY()) <= 3, map.id + ": camp surface " + y + " expected ~" + camp.getY());
            check(QuestRegions.area(map, camp.getX(), camp.getZ()) == 1, map.id + ": camp not in area 1");
            info.append(map.id).append(" areas=").append(d.areas.size()).append(" gather=").append(d.gatherCount()).append("; ");
        }
        HuntCraft.LOGGER.info("[QuestGameTests] {}", info);
        helper.succeed();
    }

    // ------------------------------------------------------------------ full loop
    /** Board accept -> depart -> base camp -> target spawned (kept loaded) -> slain -> complete -> rewards + return. */
    @GameTest(template = "arena", timeoutTicks = 1800)
    public static void questHuntLoop(GameTestHelper helper) {
        QuestDef q = Quests.get("lr2_ignivald");
        check(q != null, "quest lr2_ignivald missing");
        ServerPlayer p = mockPlayer(helper, "hc_hunt_test");
        unlockFor(p, q);
        HunterData d = HunterData.get(p);
        int zenny0 = d.zenny;
        int hrp0 = d.hrPoints + d.hunterRank * 100000;
        QuestBoardService.accept(p, q.id);
        int fee = q.contractFee();
        check(d.zenny == zenny0 - fee, "contract fee not charged: " + d.zenny + " vs " + (zenny0 - fee));
        QuestBoardService.depart(p);
        QuestInstance inst = QuestManager.instanceOf(p);
        if (inst == null) {
            removePlayer(p);
            helper.fail("quest did not start");
            return;
        }
        check(p.level().dimension() == q.map.dimension, "player not teleported to the quest map: " + p.level().dimension());
        check(p.position().distanceTo(Vec3.atBottomCenterOf(inst.camp)) < 14, "player not at the base camp: " + p.position() + " camp " + inst.camp);
        ServerLevel ql = p.serverLevel();
        BlockPos supply = QuestCamps.fixture(q.map, inst.center, q.map.data().camp.supply());
        check(ql.getBlockState(supply).is(QuestRegistry.SUPPLY_BOX.get()), "supply box missing at the camp");
        check(!inst.supplies.isEmpty(), "supply box empty");
        check(SafeZones.isInSafeZone(ql, Vec3.atBottomCenterOf(inst.camp)), "camp is not a safe zone");
        boolean registered = MonsterRegistry.get("ignivald") != null;
        check(!registered || !inst.targets.isEmpty(), "target monster not spawned");
        CompoundTag preview = QuestRewards.rollSuccess(inst, q, ql.random);
        check(!preview.getList("items", 10).isEmpty(), "no reward items rolled");
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    if (registered) {
                        Entity e = ql.getEntity(inst.targets.get(0));
                        if (!(e instanceof HuntMonster m)) throw new GameTestAssertException("target not loaded (chunk ticket)");
                        check(QuestCamps.areaAt(inst, m.getX(), m.getZ()) > 1, "target not in a hunting area");
                        m.hurt(ql.damageSources().generic(), 99999.0F);
                    } else {
                        QuestManager.complete(ql.getServer(), inst);
                    }
                })
                .thenWaitUntil(() -> check(inst.state == QuestInstance.State.COMPLETE, "quest not complete after the target was slain: " + inst.state))
                .thenWaitUntil(() -> check(!QuestManager.data(ql.getServer()).instances.containsKey(inst.id), "instance not finished yet"))
                .thenExecute(() -> {
                    check(!HCDimensions.isQuestMap(p.level()), "player not returned from the quest map");
                    check(d.completedQuests.contains(q.id), "quest not recorded as cleared");
                    check(d.zenny > zenny0 - fee, "no zenny reward: " + d.zenny);
                    check(d.hrPoints + d.hunterRank * 100000 > hrp0, "no HRP reward");
                    int boxItems = 0;
                    for (ItemStack st : d.itemBox) boxItems += st.getCount();
                    check(boxItems > 0, "no reward materials in the item box");
                    check(!SafeZones.isInSafeZone(ql, Vec3.atBottomCenterOf(inst.camp)), "camp safe zone not removed");
                    removePlayer(p);
                })
                .thenSucceed();
    }

    /** Gather quest: deliver the items at the delivery box, then return early with the guild flag. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void questGatherDelivery(GameTestHelper helper) {
        QuestDef q = Quests.get("lr1_herb");
        check(q != null, "quest lr1_herb missing");
        ServerPlayer p = mockPlayer(helper, "hc_gather_test");
        unlockFor(p, q);
        HunterData d = HunterData.get(p);
        QuestBoardService.accept(p, q.id);
        QuestBoardService.depart(p);
        QuestInstance inst = QuestManager.instanceOf(p);
        if (inst == null) {
            removePlayer(p);
            helper.fail("gather quest did not start");
            return;
        }
        BlockPos delivery = QuestCamps.fixture(q.map, inst.center, q.map.data().camp.delivery());
        check(p.serverLevel().getBlockState(delivery).is(QuestRegistry.DELIVERY_BOX.get()), "delivery box missing");
        p.getInventory().add(new ItemStack(HCItems.HERB.get(), 4));
        QuestManager.deliver(p, delivery);
        check(inst.state == QuestInstance.State.ACTIVE && inst.progress[0] == 4, "partial delivery not counted: " + inst.progress[0]);
        p.getInventory().add(new ItemStack(HCItems.HERB.get(), 10));
        QuestManager.deliver(p, delivery);
        check(inst.state == QuestInstance.State.COMPLETE, "gather quest not complete after delivery: " + inst.state);
        QuestManager.useCampFlag(p);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    check(!QuestManager.data(p.server).instances.containsKey(inst.id), "flag did not finish the quest");
                    check(!HCDimensions.isQuestMap(p.level()), "player not returned by the guild flag");
                    check(d.completedQuests.contains(q.id), "gather quest not recorded");
                    removePlayer(p);
                })
                .thenSucceed();
    }

    /** Three faints fail the quest: the hunter is sent back without rewards (contract fee lost). */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void questFailsAfterThreeFaints(GameTestHelper helper) {
        QuestDef q = Quests.get("lr1_mushroom");
        check(q != null, "quest lr1_mushroom missing");
        ServerPlayer p = mockPlayer(helper, "hc_faint_test");
        unlockFor(p, q);
        HunterData d = HunterData.get(p);
        QuestBoardService.accept(p, q.id);
        int zennyAfterFee = d.zenny;
        QuestBoardService.depart(p);
        QuestInstance inst = QuestManager.instanceOf(p);
        if (inst == null) {
            removePlayer(p);
            helper.fail("quest did not start");
            return;
        }
        for (int i = 0; i < 3; i++) {
            p.invulnerableTime = 0;
            p.kill();
            check(p.isAlive() && p.getHealth() > 0, "faint " + (i + 1) + " killed the player");
            check(HCDimensions.isQuestMap(p.level()), "fainted player left the quest map");
            check(p.position().distanceTo(Vec3.atBottomCenterOf(inst.camp)) < 14, "fainted player not carried back to camp");
        }
        check(inst.faints == 3, "faints not counted: " + inst.faints);
        check(inst.state == QuestInstance.State.FAILED, "quest did not fail after 3 faints: " + inst.state);
        helper.startSequence()
                .thenWaitUntil(() -> check(!QuestManager.data(p.server).instances.containsKey(inst.id), "failed quest not closed yet"))
                .thenExecute(() -> {
                    check(!HCDimensions.isQuestMap(p.level()), "player not returned after failure");
                    check(!d.completedQuests.contains(q.id), "failed quest recorded as cleared");
                    check(d.zenny == zennyAfterFee, "zenny changed after a failed quest: " + d.zenny + " vs " + zennyAfterFee);
                    removePlayer(p);
                })
                .thenSucceed();
    }
}
