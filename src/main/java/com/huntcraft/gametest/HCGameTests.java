package com.huntcraft.gametest;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.Rank;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side smoke tests run in CI (runGameTestServer): every registered monster must spawn,
 * tick, perform each of its attacks, take damage, die (carcass for large) without throwing.
 */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HCGameTests {
    private HCGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void monstersSpawnAndTick(GameTestHelper helper) {
        List<HuntMonster> spawned = new ArrayList<>();
        int i = 0;
        for (MonsterRegistry.Entry e : MonsterRegistry.all()) {
            BlockPos rel = new BlockPos(6 + (i % 4) * 10, 1, 6 + (i / 4) * 10);
            HuntMonster m = MonsterRegistry.spawn(helper.getLevel(), e.id(), helper.absolutePos(rel), i % 2 == 0 ? Rank.LOW : Rank.HIGH, "test");
            if (m == null) {
                helper.fail("Could not spawn " + e.id());
                return;
            }
            spawned.add(m);
            i++;
        }
        helper.runAfterDelay(100, () -> {
            for (HuntMonster m : spawned) {
                if (m.isRemoved() && !m.isCarcass()) helper.fail(m.profile().id + " vanished");
            }
            spawned.forEach(HuntMonster::discard);
            helper.succeed();
        });
    }

    /** Runs every attack of every monster (all monsters in parallel) against an armor stand target. */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void monstersPerformAllAttacks(GameTestHelper helper) {
        List<MonsterRegistry.Entry> entries = MonsterRegistry.all();
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(24, 1, 24));
        target.setInvulnerable(true);
        int n = entries.size();
        HuntMonster[] mons = new HuntMonster[n];
        int[] attackIdx = new int[n];
        int[] wait = new int[n];
        for (int i = 0; i < n; i++) {
            BlockPos rel = new BlockPos(5 + (i % 4) * 12, 1, 5 + (i / 4) * 12);
            mons[i] = MonsterRegistry.spawn(helper.getLevel(), entries.get(i).id(), helper.absolutePos(rel), Rank.LOW, "test");
            if (mons[i] == null) {
                helper.fail("Could not spawn " + entries.get(i).id());
                return;
            }
        }
        helper.onEachTick(() -> {
            boolean allDone = true;
            for (int i = 0; i < n; i++) {
                HuntMonster m = mons[i];
                if (m == null) continue;
                List<MonsterAttack> attacks = m.getAttacks();
                if (attackIdx[i] > attacks.size()) continue;
                allDone = false;
                if (wait[i] > 0) {
                    wait[i]--;
                    continue;
                }
                try {
                    if (attackIdx[i] == attacks.size()) {
                        m.hurt(helper.getLevel().damageSources().generic(), 5.0F);
                        m.addStatus(4, 9999, null);
                        m.hurt(helper.getLevel().damageSources().generic(), 99999.0F);
                        attackIdx[i]++;
                        continue;
                    }
                    MonsterAttack a = attacks.get(attackIdx[i]++);
                    m.stopAction();
                    m.setFlying(a.airOnly);
                    m.startAttack(a, target);
                    wait[i] = a.duration + 2;
                } catch (Exception ex) {
                    helper.fail(entries.get(i).id() + " attack exception: " + ex);
                    return;
                }
            }
            if (allDone) {
                for (HuntMonster m : mons) if (m != null) m.discard();
                helper.succeed();
            }
        });
    }

    /** Large monsters become carcasses instead of dying and can be carved. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void largeMonsterCarcass(GameTestHelper helper) {
        List<MonsterRegistry.Entry> large = MonsterRegistry.large();
        if (large.isEmpty()) {
            helper.succeed();
            return;
        }
        HuntMonster m = MonsterRegistry.spawn(helper.getLevel(), large.get(0).id(), helper.absolutePos(new BlockPos(24, 1, 24)), Rank.LOW, "test");
        if (m == null) {
            helper.fail("spawn failed");
            return;
        }
        m.hurt(helper.getLevel().damageSources().generic(), 2000.0F);
        helper.runAfterDelay(20, () -> {
            if (!m.isCarcass()) helper.fail("not a carcass after lethal damage, health=" + m.getHealth());
            else {
                m.discard();
                helper.succeed();
            }
        });
    }
}
