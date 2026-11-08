package com.eternalrealm.gametest;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.combat.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration of the game content in a real server: templated monsters get their body, level stats and tags; the
 * boss fight spawns its (animated) body, runs its brain every tick and finishes when its health pool is emptied;
 * equipment data survives the item stack.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GameplayGameTests {
    private static final String ARENA = "arena";

    private GameplayGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    @GameTest(template = ARENA)
    public static void everyMonsterTemplateGetsABody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int built = 0;
        for (var template : Monsters.all()) {
            Optional<Mob> mob = MonsterFactory.create(level, new MonsterSpawn(template, template.levels().min()), true);
            check(mob.isPresent(), "no body for " + template.id());
            Mob body = mob.get();
            check(MonsterTags.isMonster(body), template.id() + " is not tagged");
            check(Math.abs(body.getMaxHealth() - Math.min(1024.0, new MonsterSpawn(template, template.levels().min())
                    .stats().health())) < 0.01, template.id() + " has the wrong health " + body.getMaxHealth());
            body.discard();
            built++;
        }
        check(built == Monsters.all().size(), "built " + built);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void bossFightRunsAndEndsWhenItsHealthIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 2, 4));
        AtomicBoolean defeated = new AtomicBoolean();
        BossFight fight = BossFight.start(level, Bosses.B1, Difficulty.NORMAL, spawn, Set.of(UUID.randomUUID()),
                done -> defeated.set(true)).orElseThrow(() -> new GameTestAssertException("no boss body"));
        check(fight.body().isAlive(), "the boss body did not spawn");
        AtomicReference<Integer> ticks = new AtomicReference<>(0);
        helper.onEachTick(() -> {
            fight.tick(level.getGameTime());
            ticks.set(ticks.get() + 1);
            if (ticks.get() == 40) {
                check(!fight.isOver(), "the fight ended by itself");
                fight.absorb(null, fight.brain().maxHealth() * 2.0);
            }
        });
        helper.succeedWhen(() -> {
            check(defeated.get(), "waiting for the defeat");
            check(fight.isDefeated(), "the fight is not defeated");
        });
    }

    @GameTest(template = ARENA)
    public static void equipmentDataSurvivesTheStack(GameTestHelper helper) {
        EquipmentData data = com.eternalrealm.item.EquipmentGenerator.roll(
                EquipmentBase.weapon(WeaponType.STAFF, ItemTier.FROST), ItemRarity.EPIC, new java.util.SplittableRandom(7));
        ItemStack stack = EquipmentStacks.create(data);
        ItemStack copy = ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
        EquipmentData read = EquipmentStacks.read(copy).orElseThrow(() -> new GameTestAssertException("no data"));
        check(read.rarity() == ItemRarity.EPIC && read.tier() == ItemTier.FROST, "data changed: " + read);
        check(read.uniqueId() != null, "no unique id");
        helper.succeed();
    }
}
