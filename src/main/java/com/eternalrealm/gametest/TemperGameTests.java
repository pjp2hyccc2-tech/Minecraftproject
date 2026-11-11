package com.eternalrealm.gametest;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.monster.forge.MonsterSpawner;
import com.eternalrealm.monster.forge.MonsterTempers;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The calm field monsters (owner, 2026-11-12: they attack when a player comes within 5 blocks, or when attacked) and
 * the spacing of habitat spawns (5 blocks).
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TemperGameTests {
    private static final String ARENA = "arena";

    private TemperGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A calm habitat monster at (4.5, 2, 6.5) without AI, so only the code under test picks its target. */
    private static Mob calmMonster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MonsterTemplate template = Monsters.require("wandering_dead");
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 6.5));
        Mob mob = MonsterFactory.spawn(level, new MonsterSpawn(template, template.levels().min()), at.x, at.y, at.z, 180.0F)
                .orElseThrow(() -> new GameTestAssertException("no body for wandering_dead"));
        mob.setNoAi(true);
        MonsterTempers.makeCalm(mob);
        return mob;
    }

    private static FakePlayer player(GameTestHelper helper, String name, double z) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, z));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        return player;
    }

    @GameTest(template = ARENA)
    public static void calmMonstersLeavePlayersAloneUntilHurt(GameTestHelper helper) {
        check(ERServerConfig.CALM_HABITATS.get(), "calm habitats are on by default");
        Mob mob = calmMonster(helper);
        FakePlayer hunter = player(helper, "er_gt_calm_a", 4.5);
        FakePlayer bystander = player(helper, "er_gt_calm_b", 3.5);

        mob.setTarget(hunter);
        check(mob.getTarget() == null, "a calm monster must not pick a player");
        // fake players are invulnerable, so the attack events are checked directly
        LivingAttackEvent touch = new LivingAttackEvent(hunter, mob.damageSources().mobAttack(mob), 4.0F);
        MonsterTempers.onLivingAttack(touch);
        check(touch.isCanceled(), "a calm monster must not deal damage");

        LivingAttackEvent hit = new LivingAttackEvent(mob, hunter.damageSources().playerAttack(hunter), 1.0F);
        MonsterTempers.onLivingAttack(hit);
        check(!hit.isCanceled(), "hitting a calm monster is allowed");
        check(MonsterTempers.mayFight(mob, hunter), "a hit must provoke it");
        check(mob.getTarget() == hunter, "it must turn on the one who hit it, target " + mob.getTarget());
        mob.setTarget(bystander);
        check(mob.getTarget() == hunter, "it must not switch to a bystander who did nothing");
        check(!MonsterTempers.mayFight(mob, bystander), "the bystander did not provoke it");

        LivingAttackEvent back = new LivingAttackEvent(hunter, mob.damageSources().mobAttack(mob), 4.0F);
        MonsterTempers.onLivingAttack(back);
        check(!back.isCanceled(), "a provoked monster hits back");
        mob.discard();
        helper.succeed();
    }

    /** The monster stands at z = 6.5: a player at z = 0.5 is 6 blocks away, at z = 2.0 is 4.5 blocks away. */
    @GameTest(template = ARENA)
    public static void comingWithinFiveBlocksProvokesACalmMonster(GameTestHelper helper) {
        check(ERServerConfig.AGGRO_RADIUS.get() == 5.0, "5 blocks by default, not " + ERServerConfig.AGGRO_RADIUS.get());
        Mob mob = calmMonster(helper);
        FakePlayer walker = player(helper, "er_gt_aggro", 0.5);
        for (int i = 0; i < 30; i++) {
            MonsterTempers.look(helper.getLevel(), List.of(mob), List.of(walker));
        }
        check(!MonsterTempers.mayFight(mob, walker), "6 blocks away, however long, must not provoke it");
        check(mob.getTarget() == null, "attacked from 6 blocks, target " + mob.getTarget());
        Vec3 closer = helper.absoluteVec(new Vec3(4.5, 2.0, 2.0));
        walker.moveTo(closer.x, closer.y, closer.z, 0.0F, 0.0F);
        MonsterTempers.look(helper.getLevel(), List.of(mob), List.of(walker));
        check(MonsterTempers.mayFight(mob, walker), "4.5 blocks away must provoke it at the next look");
        check(mob.getTarget() == walker, "it must attack the one who came close, target " + mob.getTarget());
        mob.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void habitatSpawnsKeepFiveBlocksApart(GameTestHelper helper) {
        Mob mob = calmMonster(helper);
        ServerLevel level = helper.getLevel();
        check(MonsterSpawner.crowded(level, mob.getX() + 2.0, mob.getY(), mob.getZ()), "2 blocks away is too close");
        check(MonsterSpawner.crowded(level, mob.getX() + 4.5, mob.getY(), mob.getZ()), "4.5 blocks is too close");
        check(MonsterSpawner.crowded(level, mob.getX() + 4.0, mob.getY(), mob.getZ() + 2.5), "4.7 blocks is too close");
        check(!MonsterSpawner.crowded(level, mob.getX() + 5.5, mob.getY(), mob.getZ()), "5.5 blocks is far enough");
        mob.discard();
        helper.succeed();
    }
}
