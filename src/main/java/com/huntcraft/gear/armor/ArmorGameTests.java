package com.huntcraft.gear.armor;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SkillType;
import com.huntcraft.core.SmithingRegistry;
import com.huntcraft.data.HunterData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side checks of the armor module: skill sums, set bonus, charms, level-scaled defense. */
@GameTestHolder(HuntCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ArmorGameTests {
    private ArmorGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void armorSkillsAndCharms(GameTestHelper helper) {
        Player p = new Player(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 0.0F, new GameProfile(UUID.randomUUID(), "hc_armor_test")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (ArmorSet set : ArmorSets.ALL) {
            for (int i = 0; i < 4; i++) {
                if (!(set.item(i).get() instanceof HCArmorItem)) {
                    helper.fail("not an armor item: " + set.itemId(i));
                    return;
                }
            }
        }
        // full Ignivald set: fire res 4 points -> clamped to 3, set bonus attack +1 (3 pieces) and crit boost (4 pieces)
        for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new ItemStack(ArmorSets.IGNIVALD.item(i).get()));
        p.tickCount++;
        int fire = HunterSkills.level(p, SkillType.FIRE_RES);
        int atk = HunterSkills.level(p, SkillType.ATTACK_BOOST);
        int crit = HunterSkills.level(p, SkillType.CRITICAL_BOOST);
        if (fire != 3) helper.fail("expected fire res 3, got " + fire);
        if (atk != 4) helper.fail("expected attack boost 4, got " + atk);
        if (crit != 1) helper.fail("expected critical boost 1 from set bonus, got " + crit);
        // charm in the offhand
        Map<SkillType, Integer> cs = new EnumMap<>(SkillType.class);
        cs.put(SkillType.HEALTH_BOOST, 2);
        p.setItemSlot(EquipmentSlot.OFFHAND, CharmItem.create(ArmorModule.CHARM.get(), 2, cs, new int[]{2, 1}));
        p.tickCount++;
        int hb = HunterSkills.level(p, SkillType.HEALTH_BOOST);
        if (hb != 3) helper.fail("expected health boost 3 (coil 1 + charm 2), got " + hb);
        ArmorEvents.applySkillAttributes(p);
        double maxHp = p.getAttributeValue(Attributes.MAX_HEALTH);
        if (Math.abs(maxHp - 32.0) > 0.01) helper.fail("expected 32 max health, got " + maxHp);
        // random charms always carry at least one skill
        for (int i = 0; i < 50; i++) {
            ItemStack c = CharmItem.roll(ArmorModule.CHARM.get(), helper.getLevel().getRandom(), i % 2 == 0 ? Rank.HIGH : null, 1 + i % 10);
            if (CharmItem.getSkills(c).isEmpty()) {
                helper.fail("charm without skills");
                return;
            }
        }
        // level scaling of defense
        HCArmorItem mail = (HCArmorItem) ArmorSets.AZRAGOS.item(1).get();
        ItemStack lv1 = new ItemStack(mail);
        ItemStack lv8 = new ItemStack(mail);
        mail.setLevel(lv8, 8);
        double a1 = sumArmor(lv1);
        double a8 = sumArmor(lv8);
        if (!(a8 > a1)) helper.fail("level 8 armor (" + a8 + ") must exceed level 1 (" + a1 + ")");
        if (mail.getUpgradeCost(lv1, 2).isEmpty() || mail.getRequiredHR(lv1, 6) < 8) helper.fail("bad upgrade data");
        helper.succeed();
    }

    /**
     * Blacksmith data: every piece has a forge recipe, every series with a predecessor has trade-in upgrade recipes,
     * all costs are real items, level-ups 2..8 cost materials, and defense / rarity grow along the upgrade tree.
     * Then forges, upgrades and levels up a piece with a fake player through SmithingRegistry.
     */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void armorSmithing(GameTestHelper helper) {
        for (ArmorSet set : ArmorSets.ALL) {
            for (int i = 0; i < 4; i++) {
                SmithingRegistry.Recipe forge = SmithingRegistry.get(ArmorModule.forgeRecipeId(set, i));
                if (forge == null || forge.result.get() != set.item(i).get() || forge.upgradeFrom != null) {
                    helper.fail("bad forge recipe for " + set.itemId(i));
                    return;
                }
                if (!validCosts(forge.costs)) {
                    helper.fail("bad forge costs for " + set.itemId(i));
                    return;
                }
                SmithingRegistry.Recipe up = SmithingRegistry.get(ArmorModule.upgradeRecipeId(set, i));
                if (set.upgradeFrom != null) {
                    if (up == null || up.upgradeFrom == null || up.upgradeFrom.get() != set.upgradeFrom.item(i).get()
                            || up.result.get() != set.item(i).get() || !validCosts(up.costs) || up.zenny >= forge.zenny) {
                        helper.fail("bad upgrade recipe for " + set.itemId(i));
                        return;
                    }
                } else if (up != null) {
                    helper.fail("unexpected upgrade recipe for " + set.itemId(i));
                    return;
                }
                HCArmorItem item = (HCArmorItem) set.item(i).get();
                ItemStack st = new ItemStack(item);
                for (int lv = 2; lv <= ArmorSet.MAX_LEVEL; lv++) {
                    if (!validCosts(item.getUpgradeCost(st, lv)) || item.getUpgradeZenny(st, lv) <= 0) {
                        helper.fail("bad level-up cost " + set.itemId(i) + " lv" + lv);
                        return;
                    }
                }
            }
            if (set.upgradeFrom != null) {
                if (totalArmor(set) <= totalArmor(set.upgradeFrom) || set.rarity < set.upgradeFrom.rarity) {
                    helper.fail(set.id + " must be stronger than " + set.upgradeFrom.id);
                    return;
                }
            }
        }
        for (ArmorSet set : ArmorSets.ALL) {
            if (set != ArmorSets.AZRAGOS && totalArmor(set) >= totalArmor(ArmorSets.AZRAGOS)) {
                helper.fail("azragos must have the highest defense, " + set.id + " is not lower");
                return;
            }
        }
        // forge -> trade-in upgrade -> level up with a fake player
        FakePlayer fp = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hc_smith_test"));
        fp.getInventory().clearContent();
        HunterData d = HunterData.get(fp);
        d.zenny = 1_000_000;
        d.hunterRank = 10;
        ArmorSet bone = ArmorSets.BONE;
        SmithingRegistry.Recipe forge = SmithingRegistry.get(ArmorModule.forgeRecipeId(bone, 0));
        give(fp, forge.costs);
        if (!SmithingRegistry.tryForge(fp, forge.id) || countOf(fp, bone.item(0).get()) != 1) {
            helper.fail("forging " + bone.itemId(0) + " failed");
            return;
        }
        if (countOf(fp, forge.costs.get(0).item().get()) != 0) helper.fail("forge materials were not consumed");
        if (d.zenny != 1_000_000 - forge.zenny) helper.fail("forge zenny not paid");
        SmithingRegistry.Recipe up = SmithingRegistry.get(ArmorModule.upgradeRecipeId(ArmorSets.JAGRAPTOR, 0));
        give(fp, up.costs);
        if (!SmithingRegistry.tryForge(fp, up.id) || countOf(fp, ArmorSets.JAGRAPTOR.item(0).get()) != 1 || countOf(fp, bone.item(0).get()) != 0) {
            helper.fail("upgrading " + bone.itemId(0) + " -> " + ArmorSets.JAGRAPTOR.itemId(0) + " failed");
            return;
        }
        int slot = slotOf(fp, ArmorSets.JAGRAPTOR.item(0).get());
        ItemStack piece = fp.getInventory().getItem(slot);
        HCArmorItem armor = (HCArmorItem) piece.getItem();
        give(fp, armor.getUpgradeCost(piece, 2));
        if (!SmithingRegistry.tryLevelUp(fp, slot) || armor.getLevel(fp.getInventory().getItem(slot)) != 2) {
            helper.fail("level-up of " + ArmorSets.JAGRAPTOR.itemId(0) + " failed");
            return;
        }
        helper.succeed();
    }

    private static boolean validCosts(List<SmithingRegistry.Cost> costs) {
        if (costs.isEmpty()) return false;
        for (SmithingRegistry.Cost c : costs) {
            Item it = c.item().get();
            if (it == null || it == Items.AIR || c.count() <= 0) return false;
        }
        return true;
    }

    private static double totalArmor(ArmorSet set) {
        double v = 0;
        for (int i = 0; i < 4; i++) v += ((HCArmorItem) set.item(i).get()).armorAt(1);
        return v;
    }

    private static void give(Player p, List<SmithingRegistry.Cost> costs) {
        for (SmithingRegistry.Cost c : costs) p.getInventory().add(new ItemStack(c.item().get(), c.count()));
    }

    private static int countOf(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static int slotOf(Player p, Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static double sumArmor(ItemStack st) {
        double v = 0;
        for (AttributeModifier m : st.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR)) v += m.getAmount();
        return v;
    }
}
