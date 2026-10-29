package com.huntcraft.gear.armor;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SkillType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
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

    private static double sumArmor(ItemStack st) {
        double v = 0;
        for (AttributeModifier m : st.getAttributeModifiers(EquipmentSlot.CHEST).get(Attributes.ARMOR)) v += m.getAmount();
        return v;
    }
}
