package com.eternalrealm.world.field;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The threats of the fields (フィールドの脅威): in every field three ヌシ (lords) — named, much tougher monsters of
 * the field (each a look and template of its own) that roam their territory and come back some minutes after they fell — and one 災厄
 * (calamity), a boss that manifests at its lair when the field's 瘴気 (miasma, {@link Miasma}) overflows. Places are
 * flat, dry spots away from roads, waystones and set pieces (found with the field terrain functions); a calamity's
 * lair is levelled into a scar (災厄の爪痕) during its omen.
 */
public final class FieldThreats {
    /** A player this close to a lord's home wakes it (spawns the body when it is due). */
    public static final double WAKE_RADIUS = 96.0;
    /** Nobody within this distance for {@link #SLEEP_TICKS}: the lord's body goes away (it stays alive). */
    public static final double SLEEP_RADIUS = 160.0;
    public static final int SLEEP_TICKS = 60 * 20;
    /** Radius of a calamity's lair (levelled, the arena of the fight). */
    public static final int LAIR_RADIUS = 20;
    /** Players this close to a calamity join the fight. */
    public static final double JOIN_RADIUS = 56.0;
    /** A lord fights at this many levels above the top level of its field's monsters. */
    public static final int LORD_LEVEL_BONUS = 2;
    /** A share of the damage a contributor needs for the lord's or calamity's rewards. */
    public static final double REWARD_SHARE = 0.03;

    /** One ヌシ: a named monster of the field with its home and territory. */
    public record Lord(String id, int field, String template, int x, int z, int territory, double healthMultiplier,
                       int respawnMinTicks, int respawnMaxTicks) {
        public String translationKey() {
            return "threat." + ModInfo.MOD_ID + ".lord." + id;
        }
    }

    /** One 災厄: the boss that manifests at the lair of its field. */
    public record Calamity(String id, int field, String boss, int x, int z) {
        public String translationKey() {
            return "threat." + ModInfo.MOD_ID + ".calamity." + id;
        }
    }

    private static final int MIN = 60 * 20;
    /** The ヌシ: three per field, each its own ELITE template {@code lord_<id>} of the bestiary. */
    // GENERATED BESTIARY LORDS BEGIN (tools/art_entities/bestiary_gen.py)
    private static final List<Lord> LORDS = List.of(
            lord("garm", 1, "lord_garm", -468, 564, 28, 6.0, 9, 13),
            lord("hook", 1, "lord_hook", -676, 916, 32, 6.0, 7, 11),
            lord("durn", 1, "lord_durn", -652, 772, 34, 6.0, 10, 14),
            lord("nebula", 2, "lord_nebula", -908, -284, 26, 6.0, 9, 13),
            lord("belladonna", 2, "lord_belladonna", -588, -4, 30, 6.0, 7, 11),
            lord("lunar", 2, "lord_lunar", -804, -396, 30, 6.0, 10, 14),
            lord("dolga", 3, "lord_dolga", -1212, 524, 24, 6.0, 10, 14),
            lord("rustbone", 3, "lord_rustbone", -1196, 804, 30, 6.0, 8, 12),
            lord("vein_eater", 3, "lord_vein_eater", -1372, 660, 26, 6.0, 10, 14),
            lord("goura", 4, "lord_goura", 556, -148, 28, 6.0, 10, 14),
            lord("mistweaver", 4, "lord_mistweaver", 412, -644, 26, 6.0, 8, 12),
            lord("ord", 4, "lord_ord", 380, -492, 26, 6.0, 10, 14),
            lord("valka", 5, "lord_valka", 1052, -44, 28, 6.0, 11, 15),
            lord("scorch_eye", 5, "lord_scorch_eye", 948, -196, 26, 6.0, 9, 13),
            lord("agnis", 5, "lord_agnis", 1228, -252, 24, 6.0, 10, 14),
            lord("alvin", 6, "lord_alvin", 300, -676, 24, 6.0, 11, 15),
            lord("peak_sniper", 6, "lord_peak_sniper", 28, -748, 26, 6.0, 9, 13),
            lord("zephyr", 6, "lord_zephyr", 404, -796, 30, 6.0, 10, 14),
            lord("xenos", 7, "lord_xenos", -892, -556, 28, 6.0, 12, 16),
            lord("black_knight", 7, "lord_black_knight", -956, -388, 28, 6.0, 10, 14),
            lord("nihil", 7, "lord_nihil", -1044, -500, 30, 6.0, 10, 14),
            lord("brutus", 8, "lord_brutus", 556, 180, 34, 6.0, 9, 13),
            lord("harvest_boss", 8, "lord_harvest_boss", 940, 612, 30, 6.0, 7, 11),
            lord("strom", 8, "lord_strom", 500, -12, 30, 6.0, 10, 14),
            lord("melusine", 9, "lord_melusine", 484, 660, 26, 6.0, 10, 14),
            lord("drowned_fisher", 9, "lord_drowned_fisher", 716, 732, 30, 6.0, 8, 12),
            lord("numel", 9, "lord_numel", 804, 844, 30, 6.0, 10, 14),
            lord("barbas", 10, "lord_barbas", 892, 772, 26, 6.0, 10, 14),
            lord("dock_brawler", 10, "lord_dock_brawler", 1020, 884, 24, 6.0, 8, 12),
            lord("ankara", 10, "lord_ankara", 1284, 804, 28, 6.0, 10, 14));
    // GENERATED BESTIARY LORDS END

    private static final List<Calamity> CALAMITIES = List.of(
            new Calamity("meadow", 1, "b1", -212, 420),
            new Calamity("moon_forest", 2, "b2", -412, -36),
            new Calamity("iron_mine", 3, "b3", -1428, 804),
            new Calamity("sacred_falls", 4, "b4", 468, -372),
            new Calamity("ancient_ruins", 5, "b6", 1372, -172),
            new Calamity("soaring_peaks", 6, "b7", 156, -580),
            new Calamity("end_forest", 7, "b8", -1092, -708),
            new Calamity("farm", 8, "b2", 860, 196),
            new Calamity("lakeside", 9, "b3", 284, 564),
            new Calamity("harbour", 10, "b5", 1148, 764));

    private FieldThreats() {
    }

    private static Lord lord(String id, int field, String template, int x, int z, int territory, double health,
                             int respawnMinMinutes, int respawnMaxMinutes) {
        return new Lord(id, field, template, x, z, territory, health, respawnMinMinutes * MIN, respawnMaxMinutes * MIN);
    }

    public static List<Lord> lords() {
        return LORDS;
    }

    public static List<Calamity> calamities() {
        return CALAMITIES;
    }

    public static List<Lord> lordsOf(int field) {
        List<Lord> found = new ArrayList<>();
        for (Lord lord : LORDS) {
            if (lord.field() == field) {
                found.add(lord);
            }
        }
        return found;
    }

    public static Optional<Lord> lord(String id) {
        return LORDS.stream().filter(lord -> lord.id().equals(id)).findFirst();
    }

    public static Optional<Calamity> calamityOf(int field) {
        return CALAMITIES.stream().filter(calamity -> calamity.field() == field).findFirst();
    }

    /** Ticks until a fallen lord comes back. */
    public static int respawnTicks(Lord lord, RandomGenerator random) {
        int span = Math.max(0, lord.respawnMaxTicks() - lord.respawnMinTicks());
        return lord.respawnMinTicks() + (span == 0 ? 0 : random.nextInt(span + 1));
    }

    /** Ticks until a lord first appears after the server started (staggered, so not all at once). */
    public static int firstAppearanceTicks(Lord lord, RandomGenerator random) {
        return 20 * 20 + random.nextInt(Math.max(1, lord.respawnMinTicks() / 3));
    }
}
