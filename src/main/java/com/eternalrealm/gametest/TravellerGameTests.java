package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.npc.NpcBodies;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.npc.forge.NpcEntities;
import com.eternalrealm.npc.forge.TravellerDirector;
import com.eternalrealm.npc.forge.TravellerEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The travelling adventurers (docs/modules/travellers.md): only in a single-player world; in a fight they wear down a
 * monster with their attacks and skills, and nothing hurts them.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TravellerGameTests {
    private static final String ARENA = "arena";
    private static final String EMPTY = "empty";

    private TravellerGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** A dedicated or test server is no single-player world: the travellers stay away. */
    @GameTest(template = EMPTY)
    public static void travellersOnlyLiveInASinglePlayerWorld(GameTestHelper helper) {
        check(!TravellerDirector.allowed(helper.getLevel().getServer()), "not a single-player world");
        helper.succeed();
    }

    /**
     * Graus, in the field with a monster in front of him: he closes in and wears it down with his halberd and his
     * bell; nothing hurts him; his body is the NPC body of his appearance.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void aTravellerFightsAMonsterAndCannotBeHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TravellerEntity graus = NpcEntities.TRAVELLER.get().create(level);
        check(graus != null, "a traveller's body");
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
        graus.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        Vec3 ground = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        graus.setup(Travellers.Traveller.GRAUS, Travellers.Place.FIELD, ground, 4.0);
        level.addFreshEntity(graus);
        check(graus.getAppearance().equals(Travellers.Traveller.GRAUS.appearance()), "Graus wears his own body");
        check(NpcBodies.exists(graus.getAppearance()), "a generated body");
        check(!graus.hurt(level.damageSources().generic(), 5.0F), "nothing hurts a traveller");

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
