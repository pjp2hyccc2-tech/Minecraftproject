package com.huntcraft.gear.armor;

import com.huntcraft.registry.HCItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

import static com.huntcraft.core.SkillType.*;

/**
 * The 8 armor series. Skill levels are summed over equipped pieces (+ set bonus + charm) by {@link ArmorSkills}.
 * Resistances (fire, water, thunder, ice, dragon) are FIRE_RES..DRAGON_RES skill points (negative = weakness).
 * Smith tree (trade-in upgrades): hunter -> bone -> jagraptor -> {ignivald, frostgarm, voltrix, gravadon}; ignivald -> azragos.
 * Defense at level 1 (sum of 4 pieces): hunter 7, bone 13, jagraptor 14, voltrix 17, ignivald/frostgarm 18, gravadon 20, azragos 22.
 */
public final class ArmorSets {
    private ArmorSets() {
    }

    private static final Supplier<Item> LEATHER = () -> Items.LEATHER;
    private static final Supplier<Item> IRON = () -> Items.IRON_INGOT;

    // ------------------------------------------------------------------ starter / early sets
    public static final ArmorSet HUNTER = ArmorSet.builder("hunter").rarity(1).color(0xD2AA7A)
            .defense(1, 3, 2, 1, 0F, 0F).sound(SoundEvents.ARMOR_EQUIP_LEATHER).perLevel(0.15F, 0F).zenny(60).hr(1)
            .piece(0, p -> p.skill(GATHERING, 1).res(0, 1, 0, 0, 0)
                    .cost(LEATHER, 2).cost(IRON, 1).zenny(100).upgrade(HCItems.MONSTER_BONE_S, HCItems.MONSTER_BONE_M))
            .piece(1, p -> p.skill(RECOVERY_UP, 1).res(0, 0, 0, 1, 0)
                    .cost(LEATHER, 4).cost(IRON, 2).zenny(150).upgrade(HCItems.MONSTER_BONE_S, HCItems.MONSTER_BONE_M))
            .piece(2, p -> p.skill(CONSTITUTION, 1).res(1, 0, 0, 0, 0)
                    .cost(LEATHER, 3).cost(IRON, 1).zenny(120).upgrade(HCItems.MONSTER_BONE_S, HCItems.MONSTER_BONE_M))
            .piece(3, p -> p.skill(STAMINA_SURGE, 1).res(0, 0, 1, 0, 0)
                    .cost(LEATHER, 2).cost(IRON, 1).zenny(100).upgrade(HCItems.MONSTER_BONE_S, HCItems.MONSTER_BONE_M))
            .bonus(4, GATHERING, 1)
            .build();

    public static final ArmorSet BONE = ArmorSet.builder("bone").rarity(1).color(0xEADFC4).from(HUNTER)
            .defense(2, 5, 4, 2, 0.5F, 0F).sound(SoundEvents.ARMOR_EQUIP_GENERIC).perLevel(0.2F, 0.03F).zenny(80).hr(1)
            .piece(0, p -> p.skill(ATTACK_BOOST, 1).res(1, 0, -1, 0, 0)
                    .cost(HCItems.MONSTER_BONE_S, 3).cost(HCItems.BOARFANG_TUSK, 1).zenny(200).upgrade(HCItems.MONSTER_BONE_M, HCItems.MONSTER_BONE_L))
            .piece(1, p -> p.skill(ATTACK_BOOST, 1).skill(STUN_RES, 1).res(0, 0, 0, 1, 0)
                    .cost(HCItems.MONSTER_BONE_S, 4).cost(HCItems.MONSTER_BONE_M, 1).zenny(300).upgrade(HCItems.MONSTER_BONE_M, HCItems.MONSTER_BONE_L))
            .piece(2, p -> p.skill(HEALTH_BOOST, 1).res(0, 1, -1, 0, 0)
                    .cost(HCItems.MONSTER_BONE_S, 3).cost(HCItems.BOARFANG_HIDE, 2).zenny(250).upgrade(HCItems.MONSTER_BONE_M, HCItems.MONSTER_BONE_L))
            .piece(3, p -> p.skill(CARVING, 1).res(0, 0, 0, 1, 0)
                    .cost(HCItems.MONSTER_BONE_S, 2).cost(HCItems.MONSTER_BONE_M, 1).zenny(200).upgrade(HCItems.MONSTER_BONE_M, HCItems.MONSTER_BONE_L))
            .bonus(3, HEROICS, 1)
            .build();

    public static final ArmorSet JAGRAPTOR = ArmorSet.builder("jagraptor").rarity(2).color(0x5DA8F0).from(BONE)
            .defense(2, 6, 4, 2, 1.0F, 0F).sound(SoundEvents.ARMOR_EQUIP_LEATHER).perLevel(0.2F, 0.05F).zenny(100).hr(1)
            .piece(0, p -> p.skill(CRITICAL_EYE, 1).res(-1, 1, 0, 0, 0)
                    .cost(HCItems.JAGRAPTOR_HIDE, 2).cost(HCItems.JAGRAPTOR_FANG, 1).zenny(300).upgrade(HCItems.JAGRAPTOR_HIDE, HCItems.JAGRAPTOR_FANG))
            .piece(1, p -> p.skill(EVADE_WINDOW, 1).skill(CONSTITUTION, 1).res(0, 1, 0, -1, 0)
                    .cost(HCItems.JAGRAPTOR_HIDE, 4).cost(HCItems.JAGRAPTOR_FANG, 1).zenny(400).upgrade(HCItems.JAGRAPTOR_HIDE, HCItems.JAGRAPTOR_FANG))
            .piece(2, p -> p.skill(CRITICAL_EYE, 1).res(0, 0, 1, 0, 0)
                    .cost(HCItems.JAGRAPTOR_HIDE, 3).cost(HCItems.MONSTER_BONE_S, 2).zenny(350).upgrade(HCItems.JAGRAPTOR_HIDE, HCItems.JAGRAPTOR_FANG))
            .piece(3, p -> p.skill(EVADE_WINDOW, 1).res(0, 1, 0, 0, 0)
                    .cost(HCItems.JAGRAPTOR_HIDE, 2).cost(HCItems.JAGRAPTOR_FANG, 2).zenny(300).upgrade(HCItems.JAGRAPTOR_HIDE, HCItems.JAGRAPTOR_FANG))
            .bonus(3, STAMINA_SURGE, 1)
            .build();

    // ------------------------------------------------------------------ large monster sets
    public static final ArmorSet IGNIVALD = ArmorSet.builder("ignivald").rarity(4).color(0xFF6A2A).from(JAGRAPTOR)
            .defense(3, 7, 5, 3, 1.5F, 0F).sound(SoundEvents.ARMOR_EQUIP_CHAIN).perLevel(0.3F, 0.1F).zenny(200).hr(2)
            .piece(0, p -> p.skill(WEAKNESS_EXPLOIT, 1).skill(ATTACK_BOOST, 1).res(1, -1, 0, 0, 0)
                    .cost(HCItems.IGNIVALD.scale, 2).cost(HCItems.IGNIVALD.shell, 1).cost(HCItems.MONSTER_BONE_M, 1).zenny(600)
                    .upgrade(HCItems.IGNIVALD.scale, HCItems.IGNIVALD.scalePlus))
            .piece(1, p -> p.skill(ATTACK_BOOST, 1).skill(WINDPROOF, 1).res(1, 0, 0, -1, 1)
                    .cost(HCItems.IGNIVALD.shell, 2).cost(HCItems.IGNIVALD.special, 1).cost(HCItems.IGNIVALD.scale, 1).zenny(800)
                    .upgrade(HCItems.IGNIVALD.shell, HCItems.IGNIVALD.shellPlus))
            .piece(2, p -> p.skill(WEAKNESS_EXPLOIT, 1).skill(HEALTH_BOOST, 1).res(1, -1, 0, 0, 0)
                    .cost(HCItems.IGNIVALD.scale, 2).cost(HCItems.IGNIVALD.claw, 1).cost(HCItems.MONSTER_BONE_M, 1).zenny(700)
                    .upgrade(HCItems.IGNIVALD.shell, HCItems.IGNIVALD.shellPlus))
            .piece(3, p -> p.skill(ATTACK_BOOST, 1).skill(EVADE_WINDOW, 1).res(1, 0, 0, -1, 0)
                    .cost(HCItems.IGNIVALD.claw, 2).cost(HCItems.IGNIVALD.tail, 1).cost(HCItems.FLAME_SAC, 1).zenny(600)
                    .upgrade(HCItems.IGNIVALD.scale, HCItems.IGNIVALD.scalePlus))
            .bonus(3, ATTACK_BOOST, 1).bonus(4, CRITICAL_BOOST, 1)
            .build();

    public static final ArmorSet FROSTGARM = ArmorSet.builder("frostgarm").rarity(4).color(0x9BE7FF).from(JAGRAPTOR)
            .defense(3, 7, 5, 3, 2.0F, 0.05F).sound(SoundEvents.ARMOR_EQUIP_LEATHER).perLevel(0.3F, 0.1F).zenny(200).hr(2)
            .piece(0, p -> p.skill(EARPLUGS, 1).skill(HEROICS, 1).res(-1, 0, 0, 1, 0)
                    .cost(HCItems.FROSTGARM.scale, 2).cost(HCItems.FROSTGARM.special, 1).cost(HCItems.MONSTER_BONE_M, 1).zenny(600)
                    .upgrade(HCItems.FROSTGARM.scale, HCItems.FROSTGARM.scalePlus))
            .piece(1, p -> p.skill(HEALTH_BOOST, 1).skill(CONSTITUTION, 1).res(0, 1, 0, 1, 0)
                    .cost(HCItems.FROSTGARM.shell, 2).cost(HCItems.FROSTGARM.scale, 2).cost(HCItems.FROST_SAC, 1).zenny(800)
                    .upgrade(HCItems.FROSTGARM.shell, HCItems.FROSTGARM.shellPlus))
            .piece(2, p -> p.skill(STAMINA_SURGE, 1).skill(ATTACK_BOOST, 1).res(-1, 0, 0, 1, 0)
                    .cost(HCItems.FROSTGARM.scale, 2).cost(HCItems.FROSTGARM.claw, 1).cost(HCItems.FROSTGARM.shell, 1).zenny(700)
                    .upgrade(HCItems.FROSTGARM.shell, HCItems.FROSTGARM.shellPlus))
            .piece(3, p -> p.skill(CONSTITUTION, 1).skill(TREMOR_RES, 1).res(0, 0, 0, 1, 0)
                    .cost(HCItems.FROSTGARM.claw, 2).cost(HCItems.FROSTGARM.tail, 1).cost(HCItems.ICE_CRYSTAL, 2).zenny(600)
                    .upgrade(HCItems.FROSTGARM.scale, HCItems.FROSTGARM.scalePlus))
            .bonus(3, HEAT_COLD_RES, 1).bonus(4, AGITATOR, 1)
            .build();

    public static final ArmorSet VOLTRIX = ArmorSet.builder("voltrix").rarity(4).color(0xFFE23D).from(JAGRAPTOR)
            .defense(3, 6, 5, 3, 1.5F, 0F).sound(SoundEvents.ARMOR_EQUIP_CHAIN).perLevel(0.3F, 0.1F).zenny(200).hr(2)
            .piece(0, p -> p.skill(CRITICAL_EYE, 1).skill(SPEED_SHARPENING, 1).res(0, -1, 1, 0, 0)
                    .cost(HCItems.VOLTRIX.scale, 2).cost(HCItems.VOLTRIX.special, 1).cost(HCItems.THUNDERBUG, 2).zenny(600)
                    .upgrade(HCItems.VOLTRIX.scale, HCItems.VOLTRIX.scalePlus))
            .piece(1, p -> p.skill(CRITICAL_EYE, 1).skill(FOCUS, 1).res(0, 0, 1, 0, -1)
                    .cost(HCItems.VOLTRIX.shell, 2).cost(HCItems.VOLTRIX.special, 1).cost(HCItems.VOLTRIX.scale, 1).zenny(800)
                    .upgrade(HCItems.VOLTRIX.shell, HCItems.VOLTRIX.shellPlus))
            .piece(2, p -> p.skill(EVADE_WINDOW, 1).skill(RAZOR_SHARP, 1).res(0, -1, 1, 0, 0)
                    .cost(HCItems.VOLTRIX.scale, 2).cost(HCItems.VOLTRIX.claw, 1).cost(HCItems.THUNDER_SAC, 1).zenny(700)
                    .upgrade(HCItems.VOLTRIX.shell, HCItems.VOLTRIX.shellPlus))
            .piece(3, p -> p.skill(CRITICAL_EYE, 1).skill(EVADE_WINDOW, 1).res(1, 0, 1, 0, 0)
                    .cost(HCItems.VOLTRIX.shell, 1).cost(HCItems.VOLTRIX.claw, 1).cost(HCItems.VOLTRIX.tail, 1).zenny(600)
                    .upgrade(HCItems.VOLTRIX.scale, HCItems.VOLTRIX.scalePlus))
            .bonus(3, EVADE_WINDOW, 1).bonus(4, CRITICAL_EYE, 1)
            .build();

    public static final ArmorSet GRAVADON = ArmorSet.builder("gravadon").rarity(4).color(0xC8AA80).from(JAGRAPTOR)
            .defense(3, 8, 6, 3, 2.5F, 0.1F).sound(SoundEvents.ARMOR_EQUIP_IRON).perLevel(0.3F, 0.12F).zenny(220).hr(2)
            .piece(0, p -> p.skill(GUARD, 1).skill(STUN_RES, 1).res(1, 1, -1, 0, 0)
                    .cost(HCItems.GRAVADON.special, 1).cost(HCItems.GRAVADON.scale, 2).cost(HCItems.MONSTER_BONE_M, 1).zenny(650)
                    .upgrade(HCItems.GRAVADON.scale, HCItems.GRAVADON.scalePlus))
            .piece(1, p -> p.skill(DEFENSE_BOOST, 2).skill(GUARD, 1).res(1, 1, 0, 0, 0)
                    .cost(HCItems.GRAVADON.scale, 3).cost(HCItems.GRAVADON.shell, 1).cost(HCItems.EARTH_CRYSTAL, 2).zenny(850)
                    .upgrade(HCItems.GRAVADON.shell, HCItems.GRAVADON.shellPlus))
            .piece(2, p -> p.skill(HEALTH_BOOST, 1).skill(TREMOR_RES, 1).res(0, 1, -1, 0, 0)
                    .cost(HCItems.GRAVADON.shell, 2).cost(HCItems.GRAVADON.claw, 1).cost(HCItems.MONSTER_BONE_M, 1).zenny(750)
                    .upgrade(HCItems.GRAVADON.shell, HCItems.GRAVADON.shellPlus))
            .piece(3, p -> p.skill(DEFENSE_BOOST, 1).skill(WINDPROOF, 1).res(0, 1, 0, 0, 0)
                    .cost(HCItems.GRAVADON.scale, 2).cost(HCItems.GRAVADON.tail, 1).cost(HCItems.AQUA_SAC, 1).zenny(650)
                    .upgrade(HCItems.GRAVADON.scale, HCItems.GRAVADON.scalePlus))
            .bonus(3, DEFENSE_BOOST, 1).bonus(4, GUARD, 1)
            .build();

    public static final ArmorSet AZRAGOS = ArmorSet.builder("azragos").rarity(6).color(0xC77DFF).from(IGNIVALD)
            .defense(3, 9, 7, 3, 3.0F, 0.1F).sound(SoundEvents.ARMOR_EQUIP_NETHERITE).perLevel(0.3F, 0.15F).zenny(300).hr(3)
            .piece(0, p -> p.skill(ATTACK_BOOST, 1).skill(CRITICAL_EYE, 1).res(1, -1, 0, 0, 1)
                    .cost(HCItems.AZRAGOS.special, 1).cost(HCItems.AZRAGOS.scale, 2).cost(HCItems.MONSTER_BONE_L, 1).zenny(1000)
                    .upgrade(HCItems.AZRAGOS.scale, HCItems.AZRAGOS.scalePlus))
            .piece(1, p -> p.skill(ELEMENT_ATTACK, 1).skill(AGITATOR, 1).res(1, 0, 0, 0, 1)
                    .cost(HCItems.AZRAGOS.shell, 2).cost(HCItems.AZRAGOS.gem, 1).cost(HCItems.MONSTER_BONE_L, 1).zenny(1300)
                    .upgrade(HCItems.AZRAGOS.shell, HCItems.AZRAGOS.shellPlus))
            .piece(2, p -> p.skill(CRITICAL_BOOST, 1).skill(HEALTH_BOOST, 1).res(0, 0, 0, -1, 1)
                    .cost(HCItems.AZRAGOS.scale, 2).cost(HCItems.AZRAGOS.claw, 1).cost(HCItems.AZRAGOS.shell, 1).zenny(1150)
                    .upgrade(HCItems.AZRAGOS.shell, HCItems.AZRAGOS.shellPlus))
            .piece(3, p -> p.skill(AGITATOR, 1).skill(WEAKNESS_EXPLOIT, 1).res(0, 0, 0, 0, 1)
                    .cost(HCItems.AZRAGOS.claw, 2).cost(HCItems.AZRAGOS.tail, 1).cost(HCItems.NOVACRYSTAL, 1).zenny(1000)
                    .upgrade(HCItems.AZRAGOS.scale, HCItems.AZRAGOS.scalePlus))
            .bonus(3, AGITATOR, 1).bonus(4, ATTACK_BOOST, 1)
            .build();

    public static final List<ArmorSet> ALL = List.of(HUNTER, BONE, JAGRAPTOR, IGNIVALD, FROSTGARM, VOLTRIX, GRAVADON, AZRAGOS);

    /** Registers the 4 pieces of every set. */
    static void registerItems(DeferredRegister<Item> items) {
        for (ArmorSet set : ALL) {
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                set.items[i] = items.register(set.itemId(i), () -> new HCArmorItem(set, ArmorSet.TYPES[idx], new Item.Properties()));
            }
        }
    }

    public static ArmorSet byId(String id) {
        for (ArmorSet s : ALL) if (s.id.equals(id)) return s;
        return null;
    }
}
