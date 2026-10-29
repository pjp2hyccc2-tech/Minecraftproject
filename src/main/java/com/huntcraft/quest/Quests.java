package com.huntcraft.quest;

import com.huntcraft.registry.HCItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.huntcraft.quest.QuestDef.Type.CAPTURE;
import static com.huntcraft.quest.QuestDef.Type.GATHER;
import static com.huntcraft.quest.QuestDef.Type.HUNT;
import static com.huntcraft.quest.QuestDef.Type.SLAY;
import static com.huntcraft.quest.QuestMap.EMBER_CALDERA;
import static com.huntcraft.quest.QuestMap.FROSTPEAK_RANGE;
import static com.huntcraft.quest.QuestMap.VERDANT_HILLS;

/**
 * All quest definitions + progression rules.
 * <ul>
 *   <li>★1 is always open; ★N opens when every key quest of ★(N-1) is cleared (★4 instead needs the ★3 urgent quest).</li>
 *   <li>Urgent quests appear once every key quest of their star level is cleared.</li>
 *   <li>Hunter rank is capped at 7 until the low rank urgent quest is cleared (clearing it promotes to HR 8).</li>
 * </ul>
 */
public final class Quests {
    public static final String LR_URGENT = "lr3_urgent_azragos";
    public static final String HR_URGENT = "hr6_urgent_azragos";
    public static final int LOW_RANK_HR_CAP = 7;
    public static final int HIGH_RANK_HR = 8;

    private static final Map<String, QuestDef> BY_ID = new LinkedHashMap<>();

    public enum Status { LOCKED, NEED_HR, AVAILABLE }

    private Quests() {
    }

    private static QuestDef.Builder q(String id, QuestDef.Type type, int stars, QuestMap map) {
        QuestDef.Builder b = QuestDef.builder(id, type, stars, map);
        // standard sub rewards by star level
        switch (stars) {
            case 1 -> b.item(HCItems.POTION, 2);
            case 2 -> b.item(HCItems.POTION, 3).item(HCItems.ARMOR_SPHERE, 1);
            case 3 -> b.item(HCItems.MEGA_POTION, 2).item(HCItems.ARMOR_SPHERE, 2);
            case 4 -> b.item(HCItems.MEGA_POTION, 2).item(HCItems.ARMOR_SPHERE_PLUS, 1);
            case 5 -> b.item(HCItems.MEGA_POTION, 3).item(HCItems.ARMOR_SPHERE_PLUS, 2);
            default -> b.item(HCItems.MAX_POTION, 1).item(HCItems.HARD_ARMOR_SPHERE, 1);
        }
        return b;
    }

    private static void add(QuestDef.Builder b) {
        QuestDef d = b.build();
        BY_ID.put(d.id, d);
    }

    static {
        // ======================================================== LOW RANK ★1
        add(q("lr1_jagraptor", SLAY, 1, VERDANT_HILLS).target("jagraptor", 8).reward(400, 50).hr(1).key()
                .item(HCItems.JAGRAPTOR_HIDE, 2));
        add(q("lr1_herb", GATHER, 1, VERDANT_HILLS).deliver(HCItems.HERB, 10).reward(300, 40).hr(1).key()
                .item(HCItems.BLUE_MUSHROOM, 2));
        add(q("lr1_mushroom", GATHER, 1, VERDANT_HILLS).deliver(HCItems.BLUE_MUSHROOM, 6).reward(350, 40).hr(1)
                .item(HCItems.HONEY, 2));
        add(q("lr1_grasica", SLAY, 1, VERDANT_HILLS).target("grasica", 5).reward(350, 40).hr(1)
                .item(HCItems.RAW_MEAT, 3));
        add(q("lr1_boarfang", SLAY, 1, VERDANT_HILLS).target("boarfang", 4).reward(450, 50).hr(1)
                .item(HCItems.BOARFANG_HIDE, 1));
        // ======================================================== LOW RANK ★2
        add(q("lr2_ignivald", HUNT, 2, VERDANT_HILLS).target("ignivald", 1).reward(2400, 120).hr(2).key());
        add(q("lr2_voltrix", HUNT, 2, VERDANT_HILLS).target("voltrix", 1).reward(2400, 120).hr(2).key());
        add(q("lr2_buzzling", SLAY, 2, VERDANT_HILLS).target("buzzling", 10).reward(800, 70).hr(2)
                .item(HCItems.HONEY, 5));
        add(q("lr2_blizraptor", SLAY, 2, FROSTPEAK_RANGE).target("blizraptor", 8).reward(900, 80).hr(2).key()
                .item(HCItems.HOT_DRINK, 2));
        add(q("lr2_ice_crystal", GATHER, 2, FROSTPEAK_RANGE).deliver(HCItems.ICE_CRYSTAL, 6).reward(800, 70).hr(2)
                .item(HCItems.HOT_DRINK, 2));
        add(q("lr2_hot_pepper", GATHER, 2, FROSTPEAK_RANGE).deliver(HCItems.HOT_PEPPER, 8).reward(700, 60).hr(2)
                .item(HCItems.HOT_DRINK, 3));
        // ======================================================== LOW RANK ★3
        add(q("lr3_frostgarm", HUNT, 3, FROSTPEAK_RANGE).target("frostgarm", 1).reward(3600, 180).hr(3).key());
        add(q("lr3_gravadon", HUNT, 3, EMBER_CALDERA).target("gravadon", 1).reward(3600, 180).hr(3).key()
                .item(HCItems.COOL_DRINK, 2));
        add(q("lr3_ignivald_capture", CAPTURE, 3, VERDANT_HILLS).target("ignivald", 1).reward(3900, 190).hr(3).key());
        add(q("lr3_heatgecko", SLAY, 3, EMBER_CALDERA).target("heatgecko", 10).reward(1200, 100).hr(3)
                .item(HCItems.COOL_DRINK, 2));
        add(q("lr3_earth_crystal", GATHER, 3, EMBER_CALDERA).deliver(HCItems.EARTH_CRYSTAL, 8).reward(1100, 90).hr(3)
                .item(HCItems.AZURITE_ORE, 2));
        add(q("lr3_yukimammo", SLAY, 3, FROSTPEAK_RANGE).target("yukimammo", 3).reward(1300, 110).hr(3)
                .item(HCItems.YUKIMAMMO_FUR, 1));
        add(q(LR_URGENT, HUNT, 3, EMBER_CALDERA).target("azragos", 1).reward(9000, 400).hr(4).urgent().rolls(7)
                .item(HCItems.ARMOR_SPHERE_PLUS, 2).item(HCItems.MAX_POTION, 1));
        // ======================================================== HIGH RANK ★4
        add(q("hr4_ignivald", HUNT, 4, EMBER_CALDERA).target("ignivald", 1).reward(7200, 360).hr(8).key());
        add(q("hr4_voltrix", HUNT, 4, FROSTPEAK_RANGE).target("voltrix", 1).reward(7200, 360).hr(8).key());
        add(q("hr4_jagraptor", SLAY, 4, VERDANT_HILLS).target("jagraptor", 15).reward(2400, 180).hr(8)
                .item(HCItems.JAGRAPTOR_FANG, 3));
        add(q("hr4_lightcrystal", GATHER, 4, VERDANT_HILLS).deliver(HCItems.LIGHTCRYSTAL, 3).reward(2800, 200).hr(8)
                .item(HCItems.CRIMSONITE_ORE, 2));
        add(q("hr4_gansekigani", SLAY, 4, EMBER_CALDERA).target("gansekigani", 8).reward(2600, 190).hr(8)
                .item(HCItems.GANSEKI_SHELL, 2));
        // ======================================================== HIGH RANK ★5
        add(q("hr5_frostgarm", HUNT, 5, FROSTPEAK_RANGE).target("frostgarm", 1).reward(9000, 450).hr(9).key());
        add(q("hr5_gravadon_capture", CAPTURE, 5, VERDANT_HILLS).target("gravadon", 1).reward(9600, 470).hr(9).key());
        add(q("hr5_blizraptor", SLAY, 5, FROSTPEAK_RANGE).target("blizraptor", 15).reward(3400, 230).hr(9)
                .item(HCItems.BLIZRAPTOR_CREST, 2));
        add(q("hr5_novacrystal", GATHER, 5, EMBER_CALDERA).deliver(HCItems.NOVACRYSTAL, 3).reward(3800, 250).hr(9)
                .item(HCItems.LIGHTCRYSTAL, 1));
        // ======================================================== HIGH RANK ★6
        add(q("hr6_double", HUNT, 6, VERDANT_HILLS).target("ignivald", 1).target("voltrix", 1).reward(14000, 650).hr(11).key());
        add(q("hr6_gravadon", HUNT, 6, EMBER_CALDERA).target("gravadon", 1).reward(11000, 550).hr(11).key());
        add(q("hr6_frostgarm_capture", CAPTURE, 6, FROSTPEAK_RANGE).target("frostgarm", 1).reward(11500, 560).hr(11));
        add(q(HR_URGENT, HUNT, 6, EMBER_CALDERA).target("azragos", 1).reward(24000, 1200).hr(12).urgent().rolls(9)
                .item(HCItems.HARD_ARMOR_SPHERE, 3).item(HCItems.MAX_POTION, 2));
    }

    @Nullable
    public static QuestDef get(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<QuestDef> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static List<String> ids() {
        return new ArrayList<>(BY_ID.keySet());
    }

    // ------------------------------------------------------------------ progression
    public static boolean highRankUnlocked(Set<String> completed) {
        return completed.contains(LR_URGENT);
    }

    public static int hrCap(Set<String> completed) {
        return highRankUnlocked(completed) ? 999 : LOW_RANK_HR_CAP;
    }

    private static boolean keysCleared(int star, Set<String> completed) {
        for (QuestDef d : BY_ID.values()) {
            if (d.stars == star && d.key && !completed.contains(d.id)) return false;
        }
        return true;
    }

    /** Is the star level (or the urgent quest of that star) open for this hunter? */
    public static boolean starUnlocked(int star, boolean urgent, Set<String> completed) {
        if (urgent) return keysCleared(star, completed) && (star != 6 || highRankUnlocked(completed));
        if (star <= 1) return true;
        if (star == 4) return highRankUnlocked(completed);
        return keysCleared(star - 1, completed) && (star < 4 || highRankUnlocked(completed));
    }

    public static Status status(QuestDef q, int hr, Set<String> completed) {
        if (!starUnlocked(q.stars, q.urgent, completed)) return Status.LOCKED;
        if (hr < q.hrReq) return Status.NEED_HR;
        return Status.AVAILABLE;
    }
}
