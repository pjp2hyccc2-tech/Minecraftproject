package com.huntcraft.gear.armor;

import com.huntcraft.core.SkillType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Skill provider installed into {@link com.huntcraft.core.HunterSkills}: sums the skill points of equipped
 * armor pieces, set bonuses and the active charm. Results are cached per player per tick (both logical sides).
 */
public final class ArmorSkills {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Map<Player, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private record Cached(int stamp, long time, Map<SkillType, Integer> skills) {
    }

    private ArmorSkills() {
    }

    /** Provider function (see HunterSkills.setProvider). */
    public static Map<SkillType, Integer> compute(Player p) {
        if (p == null) return Collections.emptyMap();
        long time = p.level() != null ? p.level().getGameTime() : 0L;
        Cached c = CACHE.get(p);
        if (c != null && c.stamp() == p.tickCount && c.time() == time) return c.skills();
        Map<SkillType, Integer> skills = Collections.unmodifiableMap(calculate(p));
        CACHE.put(p, new Cached(p.tickCount, time, skills));
        return skills;
    }

    /** Uncached calculation. */
    public static EnumMap<SkillType, Integer> calculate(Player p) {
        EnumMap<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        Map<ArmorSet, Integer> counts = new HashMap<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack st = p.getItemBySlot(slot);
            if (st.getItem() instanceof HCArmorItem a && a.getType().getSlot() == slot) {
                a.addSkills(out);
                counts.merge(a.set, 1, Integer::sum);
            }
        }
        counts.forEach((set, n) -> set.addBonus(out, n));
        ItemStack charm = CharmItem.findActive(p);
        if (!charm.isEmpty()) CharmItem.addSkills(charm, out);
        return out;
    }

    /** Number of pieces of {@code set} the player wears. */
    public static int piecesWorn(Player p, ArmorSet set) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (p.getItemBySlot(slot).getItem() instanceof HCArmorItem a && a.set == set) n++;
        }
        return n;
    }

    public static void invalidate(Player p) {
        CACHE.remove(p);
    }
}
