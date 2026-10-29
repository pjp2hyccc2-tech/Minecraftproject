package com.huntcraft.village;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.Rank;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.companion.MofuType;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.menu.ItemBoxContainer;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.world.VillageBuilder;
import com.huntcraft.village.world.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** CI smoke tests of the village module: structure placement, NPC / companion / dummy ticking, data helpers. */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private VillageGameTests() {
    }

    /** All village tiles load and place; NPCs and dummies spawn at their spots. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void villageTemplatesPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(3000, 60, 3000);
        int tiles = VillageBuilder.placeTemplates(level, origin);
        if (tiles < VillageLayout.TILES_X * VillageLayout.TILES_Z) {
            helper.fail("only " + tiles + " village tiles placed");
            return;
        }
        double[] ib = VillageBuilder.at(origin, VillageLayout.ITEM_BOX[0], VillageLayout.ITEM_BOX[1], VillageLayout.ITEM_BOX[2]);
        BlockState box = level.getBlockState(BlockPos.containing(ib[0], ib[1], ib[2]));
        if (!box.is(VillageRegistry.ITEM_BOX.get())) {
            helper.fail("item box missing in the house, found " + box);
            return;
        }
        List<Entity> spawned = VillageBuilder.spawnAll(level, origin, null);
        int npcs = 0, dummies = 0;
        for (Entity e : spawned) {
            if (e instanceof VillageNpc) npcs++;
            else if (e instanceof TrainingDummy) dummies++;
        }
        if (npcs != VillageLayout.NPC_ROLES.length || dummies != VillageLayout.DUMMY_POS.length) {
            helper.fail("spawned " + npcs + " npcs / " + dummies + " dummies");
            return;
        }
        helper.runAfterDelay(40, () -> {
            for (Entity e : spawned) {
                if (e.isRemoved()) {
                    helper.fail(e.getType() + " vanished");
                    return;
                }
            }
            spawned.forEach(Entity::discard);
            helper.succeed();
        });
    }

    /** NPCs, the training dummy and companions of every type tick and fight without throwing. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void villageEntitiesTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Entity> all = new ArrayList<>();
        List<VillageNpc> npcs = new ArrayList<>();
        for (NpcRole r : NpcRole.values()) {
            VillageNpc npc = helper.spawn(r.mofu ? VillageRegistry.MOFU_NPC.get() : VillageRegistry.HUMAN_NPC.get(), new BlockPos(4 + r.ordinal() * 2, 1, 4));
            npc.setRole(r);
            npc.setHome(npc.blockPosition(), 0.0F);
            npcs.add(npc);
            all.add(npc);
        }
        TrainingDummy dummy = helper.spawn(VillageRegistry.TRAINING_DUMMY.get(), new BlockPos(30, 1, 6));
        all.add(dummy);
        LivingEntity target = dummy;
        HuntMonster monster = null;
        if (!MonsterRegistry.all().isEmpty()) {
            monster = MonsterRegistry.spawn(level, MonsterRegistry.all().get(0).id(), helper.absolutePos(new BlockPos(24, 1, 34)), Rank.LOW, "test");
            if (monster != null) target = monster;
        }
        List<MofuEntity> mofus = new ArrayList<>();
        for (MofuType t : MofuType.values()) {
            MofuEntity m = helper.spawn(VillageRegistry.MOFU.get(), new BlockPos(16 + t.ordinal() * 5, 1, 24));
            m.setTestMode(true);
            m.setVisual(t.ordinal(), t, 10, t.ordinal() % 3, t.ordinal() % 3);
            m.refreshStats();
            m.setHealth(m.getMaxHealth());
            m.setCombatTarget(target);
            mofus.add(m);
            all.add(m);
        }
        final HuntMonster mon = monster;
        helper.runAfterDelay(20, () -> {
            dummy.hurt(level.damageSources().mobAttack(mofus.get(0)), 6.0F);
            mofus.get(0).meleeAttack(dummy);
            mofus.get(2).throwBomb(dummy);
            mofus.get(1).playHorn();
        });
        helper.runAfterDelay(300, () -> {
            if (dummy.isRemoved() || !dummy.isAlive()) {
                helper.fail("training dummy died");
                return;
            }
            for (VillageNpc n : npcs) {
                if (n.isRemoved()) {
                    helper.fail("npc " + n.getRole() + " vanished");
                    return;
                }
            }
            all.forEach(Entity::discard);
            if (mon != null) mon.discard();
            helper.succeed();
        });
    }

    /** Item box insert/sort, companion XP and meal hooks. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void villageDataLogic(GameTestHelper helper) {
        Player p = helper.makeMockPlayer();
        HunterData d = HunterData.get(p);
        ItemStack rest = ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.STONE, 64));
        ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.STONE, 30));
        ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.APPLE, 3));
        ItemBoxContainer.sort(p);
        int stone = 0;
        for (ItemStack st : d.itemBox) if (st.is(Items.STONE)) stone += st.getCount();
        if (!rest.isEmpty() || stone != 94) {
            helper.fail("item box insert/sort broken: " + stone);
            return;
        }
        CompoundTag v = MofuData.village(p);
        CompoundTag rec = MofuData.create(p.getRandom(), MofuData.nextUid(v), 1);
        MofuData.list(v).add(rec);
        if (MofuData.addXp(rec, 2000) <= 0 || MofuData.find(v, rec.getInt("uid")) == null) {
            helper.fail("companion xp / lookup broken");
            return;
        }
        CompoundTag meal = new CompoundTag();
        meal.putString("id", "chanko");
        meal.putLong("until", helper.getLevel().getGameTime() + 1000);
        v.put("meal", meal);
        if (MealSystem.attackMultiplier(p) <= 1.0 || MealSystem.extraCarves(p) != 1) {
            helper.fail("meal hooks broken");
            return;
        }
        helper.succeed();
    }
}
