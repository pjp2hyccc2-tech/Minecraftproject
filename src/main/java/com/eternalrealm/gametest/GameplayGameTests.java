package com.eternalrealm.gametest;

import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.DefeatSequence;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
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
        AtomicBoolean killed = new AtomicBoolean();
        AtomicBoolean defeated = new AtomicBoolean();
        AtomicReference<Integer> ticks = new AtomicReference<>(0);
        AtomicReference<Integer> killedAt = new AtomicReference<>(-1);
        BossFight fight = BossFight.start(level, Bosses.B1, Difficulty.NORMAL, spawn, Vec3.atBottomCenterOf(spawn),
                BossFight.DEFAULT_ARENA_HALF_SIZE, Set.of(UUID.randomUUID()), done -> {
                    killed.set(true);
                    killedAt.set(ticks.get());
                }, done -> {
                    check(killed.get(), "the killing blow is reported before the defeat");
                    check(ticks.get() - killedAt.get() >= DefeatSequence.REGULAR.lengthTicks(),
                            "the defeat waits for the collapse (" + (ticks.get() - killedAt.get()) + " ticks)");
                    defeated.set(true);
                }).orElseThrow(() -> new GameTestAssertException("no boss body"));
        check(fight.body().isAlive(), "the boss body did not spawn");
        helper.onEachTick(() -> {
            fight.tick(level.getGameTime());
            ticks.set(ticks.get() + 1);
            if (ticks.get() == 40) {
                check(!fight.isOver(), "the fight ended by itself");
                fight.absorb(null, fight.brain().maxHealth() * 2.0);
            }
            if (ticks.get() == 70) {
                check(fight.isDying() && fight.isDefeated(), "the boss collapses");
                check(fight.body().isAlive() && !fight.body().isRemoved(), "the body stays while it collapses");
                check(fight.isInvulnerable(), "a collapsing boss cannot be hit");
                check(!defeated.get(), "the rewards wait for the collapse");
                check(fight.body() instanceof com.eternalrealm.entity.ERBoss boss && boss.isDefeatPlaying(),
                        "the body plays its defeat");
                fight.absorb(null, 10.0); // ignored
            }
        });
        helper.succeedWhen(() -> {
            check(defeated.get(), "waiting for the defeat");
            check(fight.isDefeated() && !fight.isDying(), "the fight is not defeated");
            check(fight.body().isRemoved(), "the body is gone after the collapse");
        });
    }

    /**
     * A whole fight against fake players (they cannot be hurt, everything else runs for real): the azure dragon king
     * goes through all four forms with its patterns, props, hazards, charges and safe zones, collapses and leaves
     * nothing behind.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void aBossGoesThroughEveryFormAndCollapsesCleanly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = helper.absolutePos(new BlockPos(4, 2, 4));
        List<net.minecraft.server.level.ServerPlayer> fighters = List.of(fake(helper, "er_gt_boss_a", 1.5, 1.5),
                fake(helper, "er_gt_boss_b", 7.5, 6.5), fake(helper, "er_gt_boss_c", 4.5, 8.0));
        Set<UUID> ids = new java.util.HashSet<>();
        fighters.forEach(player -> ids.add(player.getUUID()));
        AtomicBoolean killed = new AtomicBoolean();
        AtomicBoolean defeated = new AtomicBoolean();
        BossFight fight = BossFight.start(level, Bosses.B6, Difficulty.NORMAL, spawn, Vec3.atBottomCenterOf(spawn),
                6.0, ids, done -> killed.set(true), done -> defeated.set(true))
                .orElseThrow(() -> new GameTestAssertException("no boss body"));
        fight.overrideTargets(() -> fighters);
        AtomicReference<Integer> ticks = new AtomicReference<>(0);
        Set<Integer> forms = new java.util.HashSet<>();
        AtomicReference<Integer> mostProps = new AtomicReference<>(0);
        AtomicBoolean enraged = new AtomicBoolean();
        helper.onEachTick(() -> {
            int t = ticks.get() + 1;
            ticks.set(t);
            fight.tick(level.getGameTime());
            forms.add(fight.brain().phase());
            enraged.set(enraged.get() || fight.brain().isEnraged());
            mostProps.set(Math.max(mostProps.get(), fight.propCount()));
            if (t % 45 == 0 && !fight.isInvulnerable() && !fight.isDefeated()) {
                fight.absorb(null, fight.brain().maxHealth() * 0.075);
            }
        });
        helper.succeedWhen(() -> {
            check(defeated.get() && killed.get(), "waiting for the defeat (form " + (fight.brain().phase() + 1)
                    + ", tick " + ticks.get() + ")");
            check(forms.equals(Set.of(0, 1, 2, 3)), "every form was played: " + forms);
            check(enraged.get(), "the last form is enraged");
            check(mostProps.get() > 0, "attacks showed props");
            check(fight.body().isRemoved(), "the body is gone");
            check(fight.propCount() == 0, "props left behind by the fight");
            List<net.minecraft.world.entity.Entity> leftovers = level.getEntities((net.minecraft.world.entity.Entity) null,
                    new net.minecraft.world.phys.AABB(spawn).inflate(48.0), entity -> !entity.isRemoved()
                            && (entity.getTags().contains(com.eternalrealm.boss.forge.BossProps.TAG)
                            || entity.getPersistentData().hasUUID("er_fight")));
            check(leftovers.isEmpty(), "left in the arena: " + leftovers);
        });
    }

    private static net.minecraft.server.level.ServerPlayer fake(GameTestHelper helper, String name, double x, double z) {
        net.minecraftforge.common.util.FakePlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(
                helper.getLevel(), new com.mojang.authlib.GameProfile(
                        UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(x, 2.0, z));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        return player;
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
