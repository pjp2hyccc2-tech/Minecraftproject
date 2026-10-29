package com.huntcraft.village;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.IUpgradeable;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SafeZones;
import com.huntcraft.core.ShopRegistry;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.registry.HCItems;
import com.huntcraft.village.companion.CompanionManager;
import com.huntcraft.village.companion.Expeditions;
import com.huntcraft.village.companion.MofuData;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.companion.MofuGear;
import com.huntcraft.village.companion.MofuType;
import com.huntcraft.village.meal.Ingredients;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.menu.ItemBoxContainer;
import com.huntcraft.village.npc.NpcRole;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.shop.VillageShop;
import com.huntcraft.village.story.ElderStory;
import com.huntcraft.village.world.VillageBuilder;
import com.huntcraft.village.world.VillageLayout;
import com.huntcraft.village.world.VillageSavedData;
import com.huntcraft.village.world.VillageTravel;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CI tests of the village module: the village builds in its dimension with every NPC, safe zones keep monsters out,
 * the blacksmith forges every registered recipe (consuming materials / zenny), the shop adjusts zenny, the mofu
 * companion spawns and follows, entities tick without throwing, and the data helpers work.
 */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    private VillageGameTests() {
    }

    private static FakePlayer hunter(ServerLevel level, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        HunterData d = HunterData.get(p);
        d.zenny = 0;
        d.hunterRank = 1;
        for (int i = 0; i < d.itemBox.size(); i++) d.itemBox.set(i, ItemStack.EMPTY);
        p.getInventory().clearContent();
        return p;
    }

    private static int count(FakePlayer p, Item item) {
        return SmithingRegistry.count(p, item);
    }

    /** The village is built in the hunter village dimension: tiles, item boxes, quest board, every NPC and dummy. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void villageBuildsInDimension(GameTestHelper helper) {
        ServerLevel village = helper.getLevel().getServer().getLevel(HCDimensions.HUNTER_VILLAGE);
        if (village == null) {
            helper.fail("dimension huntcraft:hunter_village is missing");
            return;
        }
        VillageBuilder.ensureBuilt(village);
        helper.assertTrue(VillageBuilder.isBuilt(village), "village not marked as built");
        int[] ib = VillageLayout.ITEM_BOX;
        helper.assertTrue(village.getBlockState(new BlockPos(ib[0], ib[1], ib[2])).is(VillageRegistry.ITEM_BOX.get()), "house item box missing");
        int[] ib2 = VillageLayout.ITEM_BOX_HALL;
        helper.assertTrue(village.getBlockState(new BlockPos(ib2[0], ib2[1], ib2[2])).is(VillageRegistry.ITEM_BOX.get()), "gathering hall item box missing");
        int[] qb = VillageLayout.QUEST_BOARD;
        helper.assertTrue(village.getBlockState(new BlockPos(qb[0], qb[1], qb[2])).is(VillageRegistry.QUEST_BOARD.get()), "quest board missing");
        VillageSavedData data = VillageSavedData.get(village);
        for (String roleId : VillageLayout.NPC_ROLES) {
            UUID id = data.npcUuid(roleId);
            Entity e = id == null ? null : village.getEntity(id);
            if (!(e instanceof VillageNpc npc) || !npc.isAlive()) {
                helper.fail("npc " + roleId + " not present in the village");
                return;
            }
            helper.assertTrue(npc.getRole() == NpcRole.byName(roleId), "npc " + roleId + " has role " + npc.getRole());
        }
        helper.assertTrue(data.dummies.size() == VillageLayout.DUMMY_POS.length, "training dummies missing");
        for (UUID id : data.dummies) helper.assertTrue(village.getEntity(id) instanceof TrainingDummy, "training dummy not present");
        Vec3 spawn = VillageTravel.spawnPoint();
        helper.assertTrue(village.getBlockState(BlockPos.containing(spawn.x, spawn.y, spawn.z)).isAir(), "spawn point is obstructed");
        helper.assertTrue(!village.getBlockState(BlockPos.containing(spawn.x, spawn.y - 1, spawn.z)).isAir(), "spawn point has no floor");
        helper.succeed();
    }

    /** Monsters cannot spawn in the village / gathering hall, and are pushed out of registered safe zones elsewhere. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void safeZoneKeepsMonstersOut(GameTestHelper helper) {
        ServerLevel village = helper.getLevel().getServer().getLevel(HCDimensions.HUNTER_VILLAGE);
        if (village == null) {
            helper.fail("dimension huntcraft:hunter_village is missing");
            return;
        }
        VillageTravel.registerSafeZones();
        double[] hall = VillageLayout.SAFE_ZONES[1];
        Vec3 hallCenter = new Vec3(hall[0], hall[1], hall[2]);
        helper.assertTrue(SafeZones.isInSafeZone(village, hallCenter), "gathering hall is not a safe zone");
        BlockPos hallPos = BlockPos.containing(hallCenter);
        village.getChunk(hallPos);
        Zombie z = EntityType.ZOMBIE.create(village);
        if (z != null) {
            z.moveTo(hallCenter.x, hallCenter.y, hallCenter.z, 0, 0);
            helper.assertTrue(!village.addFreshEntity(z), "a zombie could spawn in the gathering hall");
        }
        List<MonsterRegistry.Entry> monsters = MonsterRegistry.all();
        for (MonsterRegistry.Entry e : monsters) {
            HuntMonster m = MonsterRegistry.spawn(village, e.id(), hallPos, Rank.LOW, null);
            if (m != null && m.isAddedToWorld()) {
                m.discard();
                helper.fail(e.id() + " could spawn in the village");
                return;
            }
        }
        // outside the village: a registered zone pushes an intruding monster out and makes it drop hunters inside
        if (monsters.isEmpty()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Vec3 center = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(24, 1, 24)));
        String zone = "village/gametest_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SafeZones.register(zone, level.dimension(), center, 7.0);
        String id = MonsterRegistry.small().isEmpty() ? monsters.get(0).id() : MonsterRegistry.small().get(0).id();
        HuntMonster m = MonsterRegistry.spawn(level, id, helper.absolutePos(new BlockPos(40, 1, 24)), Rank.LOW, "test");
        if (m == null || !m.isAddedToWorld()) {
            SafeZones.unregister(zone);
            helper.fail("could not spawn " + id + " outside the safe zone");
            return;
        }
        m.teleportTo(center.x, center.y, center.z);
        FakePlayer p = hunter(level, "zone_hunter");
        p.moveTo(center.x + 1, center.y, center.z, 0, 0);
        m.setTarget(p);
        helper.assertTrue(m.getTarget() == null, id + " targeted a hunter inside a safe zone");
        helper.runAfterDelay(300, () -> {
            double dist = Math.hypot(m.getX() - center.x, m.getZ() - center.z);
            SafeZones.unregister(zone);
            boolean inside = !m.isRemoved() && dist < 7.0;
            m.discard();
            if (inside) helper.fail(id + " stayed inside the safe zone (" + String.format("%.1f", dist) + " blocks from the centre)");
            else helper.succeed();
        });
    }

    /** Every smithing recipe (weapons, armor, companion gear) forges: materials, zenny and the base item are consumed. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void smithForgeConsumesMaterials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer p = hunter(level, "smith_tester");
        HunterData d = HunterData.get(p);
        // companion gear recipe with one material taken from the item box
        SmithingRegistry.Recipe bamboo = SmithingRegistry.get(MofuGear.itemId(MofuGear.Slot.WEAPON, MofuGear.Tier.BAMBOO));
        if (bamboo == null) {
            helper.fail("mofu gear recipes are not registered");
            return;
        }
        d.zenny = 1000;
        p.getInventory().add(new ItemStack(Items.BAMBOO, 6));
        ItemBoxContainer.insert(d.itemBox, new ItemStack(HCItems.HERB.get(), 2));
        helper.assertTrue(SmithingRegistry.tryForge(p, bamboo.id), "forging " + bamboo.id + " failed");
        helper.assertTrue(count(p, bamboo.result.get()) == 1, "forged gear not received");
        helper.assertTrue(count(p, Items.BAMBOO) == 0 && count(p, HCItems.HERB.get()) == 0, "materials were not consumed");
        helper.assertTrue(d.zenny == 1000 - bamboo.zenny, "zenny not charged: " + d.zenny);
        // without materials it must fail and charge nothing
        int before = d.zenny;
        helper.assertTrue(!SmithingRegistry.tryForge(p, bamboo.id), "forged without materials");
        helper.assertTrue(d.zenny == before, "zenny charged on a failed forge");
        // every registered recipe
        int forged = 0;
        for (SmithingRegistry.Recipe r : SmithingRegistry.all()) {
            p.getInventory().clearContent();
            for (int i = 0; i < d.itemBox.size(); i++) d.itemBox.set(i, ItemStack.EMPTY);
            d.zenny = r.zenny + 100;
            d.hunterRank = Math.max(1, r.requiredHR);
            for (SmithingRegistry.Cost c : r.costs) p.getInventory().add(new ItemStack(c.item().get(), c.count()));
            if (r.upgradeFrom != null) p.getInventory().add(new ItemStack(r.upgradeFrom.get()));
            if (!SmithingRegistry.tryForge(p, r.id)) {
                helper.fail("recipe " + r.id + " could not be forged with its materials");
                return;
            }
            Item out = r.result.get();
            if (count(p, out) < 1 || d.zenny != 100) {
                helper.fail("recipe " + r.id + " did not give its result / charge " + r.zenny + "z");
                return;
            }
            for (SmithingRegistry.Cost c : r.costs) {
                if (c.item().get() != out && (r.upgradeFrom == null || c.item().get() != r.upgradeFrom.get()) && count(p, c.item().get()) != 0) {
                    helper.fail("recipe " + r.id + " did not consume " + c.item().get());
                    return;
                }
            }
            forged++;
        }
        // level up the first upgradeable result
        for (SmithingRegistry.Recipe r : SmithingRegistry.all()) {
            ItemStack probe = new ItemStack(r.result.get());
            if (!(probe.getItem() instanceof IUpgradeable up) || up.getMaxLevel(probe) < 2) continue;
            p.getInventory().clearContent();
            p.getInventory().setItem(0, probe);
            for (SmithingRegistry.Cost c : up.getUpgradeCost(probe, 2)) p.getInventory().add(new ItemStack(c.item().get(), c.count()));
            d.zenny = up.getUpgradeZenny(probe, 2);
            d.hunterRank = Math.max(1, up.getRequiredHR(probe, 2));
            helper.assertTrue(SmithingRegistry.tryLevelUp(p, 0), "level up of " + r.id + " failed");
            helper.assertTrue(up.getLevel(p.getInventory().getItem(0)) == 2, "level up of " + r.id + " did not raise the level");
            break;
        }
        HuntCraft.LOGGER.info("[village gametest] forged {} smithing recipes", forged);
        helper.succeed();
    }

    /** Buying and selling adjusts zenny (core shop, village bulk buy, selling from the item box). */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void shopBuySellAdjustsZenny(GameTestHelper helper) {
        FakePlayer p = hunter(helper.getLevel(), "shop_tester");
        HunterData d = HunterData.get(p);
        ShopRegistry.Entry potion = ShopRegistry.get("potion");
        if (potion == null) {
            helper.fail("potion is not sold");
            return;
        }
        d.zenny = 1000;
        helper.assertTrue(ShopRegistry.buy(p, "potion", 2), "core buy failed");
        helper.assertTrue(d.zenny == 1000 - potion.price() * 2, "zenny after buy: " + d.zenny);
        helper.assertTrue(count(p, HCItems.POTION.get()) == 2, "bought potions not received");
        int slot = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(HCItems.POTION.get())) slot = i;
        int sell = ShopRegistry.sellPrice(new ItemStack(HCItems.POTION.get()));
        int before = d.zenny;
        helper.assertTrue(ShopRegistry.sell(p, slot, 2), "core sell failed");
        helper.assertTrue(d.zenny == before + sell * 2 && count(p, HCItems.POTION.get()) == 0, "zenny after sell: " + d.zenny);
        // village bulk buy (price may be a daily deal)
        boolean deal = VillageShop.deals(VillageShop.day(helper.getLevel()), d.hunterRank).contains("potion");
        int unit = VillageShop.unitPrice(potion, deal);
        before = d.zenny;
        helper.assertTrue(VillageShop.buy(p, "potion", 3), "village buy failed");
        helper.assertTrue(d.zenny == before - unit * 3 && count(p, HCItems.POTION.get()) == 3, "village buy zenny: " + d.zenny);
        // HR-locked goods cannot be bought
        d.zenny = 99999;
        helper.assertTrue(!VillageShop.buy(p, "village_max_potion", 1), "bought an HR locked item");
        helper.assertTrue(d.zenny == 99999, "charged for an HR locked item");
        // selling from the item box
        ItemBoxContainer.insert(d.itemBox, new ItemStack(HCItems.IGNIVALD.scale.get(), 4));
        int idx = -1;
        for (int i = 0; i < d.itemBox.size(); i++) if (d.itemBox.get(i).is(HCItems.IGNIVALD.scale.get())) idx = i;
        int matPrice = ShopRegistry.sellPrice(new ItemStack(HCItems.IGNIVALD.scale.get()));
        before = d.zenny;
        helper.assertTrue(VillageShop.sellFromBox(p, idx, 3), "sell from box failed");
        helper.assertTrue(d.zenny == before + matPrice * 3 && count(p, HCItems.IGNIVALD.scale.get()) == 1, "sell from box zenny: " + d.zenny);
        helper.succeed();
    }

    /** The active mofu spawns next to its hunter (CompanionManager) and follows when the hunter moves away. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void mofuSpawnsAndFollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer p = hunter(level, "mofu_owner");
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        CompoundTag v = MofuData.village(p);
        CompoundTag rec = MofuData.createFirst(MofuData.nextUid(v), MofuType.FIGHTER, 1, "Tester");
        MofuData.list(v).add(rec);
        v.putInt("active", rec.getInt("uid"));
        CompanionManager.tick(p);
        MofuEntity mofu = CompanionManager.current(p.getUUID());
        if (mofu == null || !mofu.isAddedToWorld()) {
            helper.fail("the active mofu did not spawn");
            return;
        }
        helper.assertTrue(mofu.distanceTo(p) < 4.0F, "mofu spawned far from its hunter");
        helper.assertTrue(mofu.getGear(MofuGear.Slot.WEAPON) == MofuGear.Tier.BAMBOO.ordinal(), "starter weapon not applied");
        BlockPos far = helper.absolutePos(new BlockPos(30, 1, 30));
        p.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (++ticks[0] % 10 == 0) CompanionManager.tick(p);
            MofuEntity m = CompanionManager.current(p.getUUID());
            if (m == null || m.isRemoved()) {
                helper.fail("mofu vanished while following");
                return;
            }
            if (m.distanceTo(p) < 5.6F && ticks[0] > 20) {
                CompanionManager.despawn(p.getUUID());
                helper.succeed();
            } else if (ticks[0] > 360) {
                CompanionManager.despawn(p.getUUID());
                helper.fail("mofu did not follow its hunter (distance " + String.format("%.1f", m.distanceTo(p)) + ")");
            }
        });
    }

    /** NPCs of every role, the training dummy and companions of every type tick and fight without throwing. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void villageEntitiesTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Entity> all = new ArrayList<>();
        List<VillageNpc> npcs = new ArrayList<>();
        for (NpcRole r : NpcRole.values()) {
            VillageNpc npc = helper.spawn(VillageBuilder.typeFor(r), new BlockPos(4 + r.ordinal() * 2, 1, 4));
            npc.setRole(r);
            npc.setHome(npc.blockPosition(), 0.0F);
            npc.playAction(VillageNpc.WORK);
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
            // level 40: every skill unlocked; azragos weapon for element damage
            m.setVisual(t.ordinal(), t, 40, MofuGear.Tier.AZRAGOS.ordinal(), MofuGear.Tier.BONE.ordinal(), MofuGear.Tier.IGNIVALD.ordinal());
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
            mofus.get(1).playHorn(true);
            if (mon != null) {
                mofus.get(0).meleeAttack(mon);
                mofus.get(2).throwBomb(mon);
            }
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

    /** Item box insert/sort, companion XP, meals (combos, hooks, daily special), expeditions and elder rewards. */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void villageDataLogic(GameTestHelper helper) {
        FakePlayer p = hunter(helper.getLevel(), "data_tester");
        HunterData d = HunterData.get(p);
        ItemStack rest = ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.STONE, 64));
        ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.STONE, 30));
        ItemBoxContainer.insert(d.itemBox, new ItemStack(Items.APPLE, 3));
        ItemBoxContainer.sort(p);
        int stone = 0;
        for (ItemStack st : d.itemBox) if (st.is(Items.STONE)) stone += st.getCount();
        helper.assertTrue(rest.isEmpty() && stone == 94, "item box insert/sort broken: " + stone);
        // companions
        CompoundTag v = MofuData.village(p);
        CompoundTag rec = MofuData.create(p.getRandom(), MofuData.nextUid(v), 1);
        MofuData.list(v).add(rec);
        helper.assertTrue(MofuData.addXp(rec, 2000) > 0 && MofuData.find(v, rec.getInt("uid")) != null, "companion xp / lookup broken");
        // meals: every category pair has a dish, the meal hooks see the active meal
        for (Ingredients.Ingredient a : Ingredients.ALL) {
            for (Ingredients.Ingredient b : Ingredients.ALL) {
                Ingredients.Meal m = Ingredients.cook(a, b, false);
                helper.assertTrue((m != null) == (a.category() != b.category()), "dish table broken for " + a.id() + "+" + b.id());
            }
        }
        d.zenny = 5000;
        helper.assertTrue(MealSystem.eat(p, "boar_meat", "kazami_rice"), "eating a meal failed");
        helper.assertTrue(MealSystem.attackMultiplier(p) > 1.0, "meal attack hook broken");
        helper.assertTrue(MealSystem.active(p) != null && MealSystem.active(p).dish().id().equals("donburi"), "wrong dish for meat + rice");
        Ingredients.Ingredient[] s1 = Ingredients.dailySpecial(42), s2 = Ingredients.dailySpecial(42);
        helper.assertTrue(s1[0] == s2[0] && s1[1] == s2[1] && s1[0].category() != s1[1].category(), "daily special broken");
        // expeditions
        List<ItemStack> loot = Expeditions.roll(Expeditions.ALL.get(0), 10, Expeditions.Length.LONG, p.getRandom());
        helper.assertTrue(!loot.isEmpty(), "expedition gave no loot");
        // elder milestone reward
        d.hunterRank = 2;
        int z = d.zenny;
        CompoundTag claim = new CompoundTag();
        claim.putString("op", "claim");
        claim.putString("id", "hr2");
        ElderStory.handle(p, claim);
        helper.assertTrue(d.zenny > z && ElderStory.claimed(p, ElderStory.REWARDS.get(1)), "elder reward not granted");
        z = d.zenny;
        ElderStory.handle(p, claim);
        helper.assertTrue(d.zenny == z, "elder reward granted twice");
        helper.succeed();
    }
}
