package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.npc.NpcBodies;
import com.eternalrealm.npc.Wanderers;
import com.eternalrealm.npc.forge.NpcEntities;
import com.eternalrealm.npc.forge.WandererDirector;
import com.eternalrealm.npc.forge.WandererEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The wandering adventurers (docs/modules/wanderers.md): only in a single-player world; in a fight they wear down a
 * monster with their attacks and skills, and nothing hurts them.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WandererGameTests {
    private static final String ARENA = "arena";
    private static final String EMPTY = "empty";

    private WandererGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A dedicated or test server is no single-player world: the wanderers stay away. */
    @GameTest(template = EMPTY)
    public static void wanderersOnlyLiveInASinglePlayerWorld(GameTestHelper helper) {
        check(!WandererDirector.allowed(helper.getLevel().getServer()), "not a single-player world");
        helper.succeed();
    }

    /**
     * Graus, in the field with a monster in front of him: he closes in and wears it down with his halberd and his
     * bell; nothing hurts him; his body is the NPC body of his appearance.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void aWandererFightsAMonsterAndCannotBeHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WandererEntity graus = NpcEntities.WANDERER.get().create(level);
        check(graus != null, "a wanderer's body");
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
        graus.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        graus.setup(Wanderers.Wanderer.GRAUS, Wanderers.Place.FIELD, helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)), 4.0);
        level.addFreshEntity(graus);
        check(graus.getAppearance().equals(Wanderers.Wanderer.GRAUS.appearance()), "Graus wears his own body");
        check(NpcBodies.exists(graus.getAppearance()), "a generated body");
        check(!graus.hurt(level.damageSources().generic(), 5.0F), "nothing hurts a wanderer");

        MonsterTemplate template = Monsters.require("wandering_dead");
        Vec3 there = helper.absoluteVec(new Vec3(6.5, 2.0, 4.5));
        Mob mob = MonsterFactory.spawn(level, new MonsterSpawn(template, template.levels().min()), there.x, there.y,
                there.z, 90.0F).orElseThrow(() -> new GameTestAssertException("no body for wandering_dead"));
        mob.setNoAi(true);
        float before = mob.getHealth();
        graus.fight(mob);
        helper.succeedWhen(() -> {
            check(mob.getHealth() < before || !mob.isAlive(), String.format(java.util.Locale.ROOT,
                    "waiting for Graus to hurt the monster (%.1f blocks away, %.0f/%.0f)", graus.distanceTo(mob),
                    mob.getHealth(), before));
            check(graus.isAlive() && graus.getHealth() == graus.getMaxHealth(), "Graus took no damage");
            graus.discard();
            mob.discard();
        });
    }
}
