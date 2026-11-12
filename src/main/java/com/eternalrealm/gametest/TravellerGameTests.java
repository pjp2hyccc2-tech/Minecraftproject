package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.npc.HeroBodies;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.npc.forge.NpcEntities;
import com.eternalrealm.npc.forge.TravellerDirector;
import com.eternalrealm.npc.forge.TravellerEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * The legendary travellers (docs/modules/travellers.md): only in a single-player world; in a fight they bring their
 * moves down on a monster; nothing but monsters can hurt them, and a fall is not a death; the sister raises a fallen
 * companion with her sanctuary.
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

    private static TravellerEntity traveller(GameTestHelper helper, Travellers.Traveller who, Vec3 at) {
        ServerLevel level = helper.getLevel();
        TravellerEntity body = NpcEntities.TRAVELLER.get().create(level);
        check(body != null, "a traveller's body");
        Vec3 position = helper.absoluteVec(at);
        body.moveTo(position.x, position.y, position.z, -90.0F, 0.0F);
        body.setup(who, Travellers.Place.FIELD, position, 4.0);
        level.addFreshEntity(body);
        return body;
    }

    private static Mob monster(GameTestHelper helper, Vec3 at) {
        MonsterTemplate template = Monsters.require("wandering_dead");
        Vec3 there = helper.absoluteVec(at);
        Mob mob = MonsterFactory.spawn(helper.getLevel(), new MonsterSpawn(template, template.levels().min()), there.x,
                there.y, there.z, 90.0F).orElseThrow(() -> new GameTestAssertException("no body for wandering_dead"));
        mob.setNoAi(true);
        return mob;
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }

    /** A dedicated or test server is no single-player world: the travellers stay away. */
    @GameTest(template = EMPTY)
    public static void travellersOnlyLiveInASinglePlayerWorld(GameTestHelper helper) {
        check(!TravellerDirector.allowed(helper.getLevel().getServer()), "not a single-player world");
        helper.succeed();
    }

    /** Kaylen, a monster in front of him: he closes in and cuts it down with his moves; his body is the hero body. */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void aTravellerFightsAMonsterWithHisMoves(GameTestHelper helper) {
        TravellerEntity kaylen = traveller(helper, Travellers.Traveller.KAYLEN, new Vec3(2.5, 2.0, 4.5));
        check(HeroBodies.exists(kaylen.heroAppearance()), "a generated hero body");
        check(kaylen.getMaxHealth() == (float) Travellers.Traveller.KAYLEN.maxHealth(), "a legend's health");
        Mob mob = monster(helper, new Vec3(6.5, 2.0, 4.5));
        float before = mob.getHealth();
        kaylen.fight(mob);
        helper.succeedWhen(() -> {
            check(mob.getHealth() < before || !mob.isAlive(), String.format(Locale.ROOT,
                    "waiting for Kaylen to cut the monster (%.1f blocks away, %.0f/%.0f)", kaylen.distanceTo(mob),
                    mob.getHealth(), before));
            kaylen.discard();
            mob.discard();
        });
    }

    /**
     * Players, falls and the like never touch them; a monster's blow does, through their toughness; a blow that
     * would kill makes them fall instead, and they stand up again.
     */
    @GameTest(template = ARENA)
    public static void onlyMonstersHurtATravellerAndAFallIsNotDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TravellerEntity siegwald = traveller(helper, Travellers.Traveller.SIEGWALD, new Vec3(2.5, 2.0, 4.5));
        FakePlayer someone = player(helper, "er_gt_traveller_hit");
        float full = siegwald.getHealth();
        check(!siegwald.hurt(level.damageSources().generic(), 50.0F), "the world does not hurt a legend");
        check(!siegwald.hurt(level.damageSources().playerAttack(someone), 50.0F), "players cannot hurt a traveller");
        check(siegwald.getHealth() == full, "unhurt");
        Mob mob = monster(helper, new Vec3(4.5, 2.0, 4.5));
        siegwald.invulnerableTime = 0;
        check(siegwald.hurt(level.damageSources().mobAttack(mob), 10.0F), "a monster's blow lands");
        float taken = full - siegwald.getHealth();
        check(taken > 0.0F && taken < 10.0F, "through the knight's toughness: " + taken);
        siegwald.setHealth(1.2F); // a monster's blow, through his toughness, is less than one heart here
        siegwald.invulnerableTime = 0;
        siegwald.hurt(level.damageSources().mobAttack(mob), 50.0F);
        check(siegwald.isAlive() && siegwald.isDown(), "a lethal blow makes him fall, not die");
        check(!siegwald.canBeSeenAsEnemy(), "monsters leave a fallen traveller alone");
        siegwald.invulnerableTime = 0;
        check(!siegwald.hurt(level.damageSources().mobAttack(mob), 50.0F), "nothing hurts the fallen");
        siegwald.standUp(false);
        check(!siegwald.isDown(), "up again");
        check(Math.abs(siegwald.getHealth() - siegwald.getMaxHealth() * Travellers.STAND_UP_HEALTH) < 1.0F,
                "with part of his health");
        siegwald.discard();
        mob.discard();
        helper.succeed();
    }

    /** Mariel and Siegwald in one party: he falls, her sanctuary raises him at full health. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void theSisterRaisesAFallenCompanion(GameTestHelper helper) {
        FakePlayer leader = player(helper, "er_gt_traveller_party");
        TravellerEntity mariel = traveller(helper, Travellers.Traveller.MARIEL, new Vec3(2.5, 2.0, 2.5));
        TravellerEntity siegwald = traveller(helper, Travellers.Traveller.SIEGWALD, new Vec3(4.5, 2.0, 4.5));
        mariel.becomeCompanion(leader, 0);
        siegwald.becomeCompanion(leader, 1);
        siegwald.fall();
        check(siegwald.isDown(), "fallen");
        helper.succeedWhen(() -> {
            check(!siegwald.isDown(), "waiting for Mariel's sanctuary");
            check(siegwald.getHealth() >= siegwald.getMaxHealth() - 1.0F, "raised at full health");
            mariel.discard();
            siegwald.discard();
        });
    }
}
