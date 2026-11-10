package com.eternalrealm.item;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.PassiveEffect;
import com.eternalrealm.job.SkillEffect.AfflictionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 武具庫: the named designs of the fields (docs/modules/armory.md). Per field and weapon type three named weapons (180),
 * one boss weapon per boss with its own weapon art (9), and three armor sets per field (30 sets of four pieces).
 *
 * <p><b>Levels.</b> A field's designs come in three steps (the "bands" of {@link #levelOf}); a design's base item is
 * the highest tier its level may equip, its own level is the level needed to equip it.</p>
 *
 * <p><b>Profiles.</b> Weapons are built from a feel and an element:</p>
 * <pre>
 * feel       attack  crit   speed  reach   (staves and bows have no reach)
 * BALANCED   1.00    +3 %   +3 %   ×1.00
 * SWIFT      0.92    +4 %   +12 %  ×0.95
 * HEAVY      1.12     0     0      ×1.08
 * KEEN       0.95    +10 %  +4 %   ×1.00
 * LONG       0.98    +2 %   +2 %   ×1.22
 * ELEMENTAL  0.94    +2 %   +3 %   ×1.00   element proc 22 % (others 10 %, lord/calamity/boss +4 %)
 * source     field/monster ×1.02, craft ×1.05, dungeon ×1.08, lord ×1.10, calamity and boss ×1.12 (best source counts)
 * bonus      damage of the weapon's type +1..6 % (by source) and an element trait, scaled by 0.5 + level / 50
 * </pre>
 * Lord and calamity weapons add a perk by feel (and calamities heal on kills); boss weapons have their own perks and
 * a weapon art ({@link WeaponArts}).
 *
 * <p><b>Ids and models are stable</b>: weapon models are {@code 3000 + (field − 1) × 18 + type × 3 + step}, boss
 * weapons 3180 + boss index, armor {@code 4000 + set index × 4 + slot}; the unit tests pin them.</p>
 */
public final class Armory {
    /** Levels of the three steps of each field (index field − 1). */
    private static final int[][] LEVELS = {
            {5, 10, 15}, {10, 15, 20}, {15, 20, 25}, {20, 25, 30}, {30, 35, 40},
            {40, 45, 50}, {50, 50, 50}, {10, 15, 20}, {15, 20, 25}, {20, 25, 30}};
    /** Weapon attack bonus anchors (level → bonus): the tiers' bonuses at their levels, extended to level 50. */
    private static final int[] CURVE_LEVELS = {1, 8, 15, 22, 30, 37, 44, 50};
    private static final double[] WEAPON_BONUS = {0.0, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.686};
    private static final double[] SET_DEFENSE = {12, 30, 52, 78, 106, 136, 168, 194};
    private static final double[] SET_HEALTH = {5, 12, 21, 30, 40, 52, 64, 74};

    private static final int BOSS_MODELS = 3180;

    /** How a weapon handles. */
    enum Feel {
        BALANCED(1.00, 0.03, 0.03, 1.00),
        SWIFT(0.92, 0.04, 0.12, 0.95),
        HEAVY(1.12, 0.0, 0.0, 1.08),
        KEEN(0.95, 0.10, 0.04, 1.00),
        LONG(0.98, 0.02, 0.02, 1.22),
        ELEMENTAL(0.94, 0.02, 0.03, 1.00);

        final double attack;
        final double crit;
        final double speed;
        final double reach;

        Feel(double attack, double crit, double speed, double reach) {
            this.attack = attack;
            this.crit = crit;
            this.speed = speed;
            this.reach = reach;
        }
    }

    private static final WeaponType SWORD = WeaponType.ONE_HANDED_SWORD;
    private static final WeaponType GREAT = WeaponType.TWO_HANDED_SWORD;
    private static final WeaponType TWIN = WeaponType.DUAL_BLADES;
    private static final WeaponType SPEAR = WeaponType.SPEAR;
    private static final WeaponType STAFF = WeaponType.STAFF;
    private static final WeaponType BOW = WeaponType.BOW;

    private static final Set<DesignSource> F = EnumSet.of(DesignSource.FIELD);
    private static final Set<DesignSource> C = EnumSet.of(DesignSource.CRAFT);
    private static final Set<DesignSource> CF = EnumSet.of(DesignSource.CRAFT, DesignSource.FIELD);
    private static final Set<DesignSource> FM = EnumSet.of(DesignSource.FIELD, DesignSource.MONSTER);
    private static final Set<DesignSource> D = EnumSet.of(DesignSource.DUNGEON);
    private static final Set<DesignSource> DM = EnumSet.of(DesignSource.DUNGEON, DesignSource.MONSTER);
    private static final Set<DesignSource> LORD = EnumSet.of(DesignSource.LORD);
    private static final Set<DesignSource> CAL = EnumSet.of(DesignSource.CALAMITY);

    private static final Set<DesignSource> BOSS = EnumSet.of(DesignSource.BOSS, DesignSource.CRAFT);

    private static final List<WeaponDesign> WEAPONS;
    private static final List<ArmorSet> SETS;
    private static final List<ArmorPiece> PIECES;
    private static final List<Design> DESIGNS;
    private static final Map<String, Design> BY_ID;
    private static final Map<Integer, Design> BY_MODEL;
    private static final Map<String, ArmorSet> SETS_BY_ID;

    static {
        List<WeaponDesign> weapons = new ArrayList<>(189);
        fieldWeapons(weapons);
        bossWeapons(weapons);
        WEAPONS = Collections.unmodifiableList(weapons);
        List<ArmorSet> sets = new ArrayList<>(30);
        armorSets(sets);
        SETS = Collections.unmodifiableList(sets);
        List<ArmorPiece> pieces = new ArrayList<>(120);
        for (ArmorSet set : SETS) {
            pieces.addAll(set.pieces());
        }
        PIECES = Collections.unmodifiableList(pieces);
        List<Design> designs = new ArrayList<>(WEAPONS);
        designs.addAll(PIECES);
        DESIGNS = Collections.unmodifiableList(designs);
        Map<String, Design> byId = new LinkedHashMap<>();
        Map<Integer, Design> byModel = new LinkedHashMap<>();
        for (Design design : DESIGNS) {
            if (byId.put(design.id(), design) != null) {
                throw new ExceptionInInitializerError("Duplicate design id " + design.id());
            }
            if (byModel.put(design.model(), design) != null) {
                throw new ExceptionInInitializerError("Duplicate design model " + design.model() + " (" + design.id() + ")");
            }
        }
        Map<String, ArmorSet> setsById = new LinkedHashMap<>();
        for (ArmorSet set : SETS) {
            if (setsById.put(set.id(), set) != null || byId.containsKey(set.id())) {
                throw new ExceptionInInitializerError("Duplicate set id " + set.id());
            }
        }
        BY_ID = Collections.unmodifiableMap(byId);
        BY_MODEL = Collections.unmodifiableMap(byModel);
        SETS_BY_ID = Collections.unmodifiableMap(setsById);
    }

    private Armory() {
    }

    // ------------------------------------------------------------------ lookups

    /** Every named weapon: the 180 field weapons (field by field, type by type), then the 9 boss weapons. */
    public static List<WeaponDesign> weapons() {
        return WEAPONS;
    }

    public static List<ArmorSet> sets() {
        return SETS;
    }

    /** The 120 armor pieces, set by set, head to feet. */
    public static List<ArmorPiece> armorPieces() {
        return PIECES;
    }

    /** Every design (weapons, then armor pieces). */
    public static List<Design> designs() {
        return DESIGNS;
    }

    public static Optional<Design> design(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    public static Optional<WeaponDesign> weapon(String id) {
        return design(id).filter(WeaponDesign.class::isInstance).map(WeaponDesign.class::cast);
    }

    public static Optional<ArmorSet> set(String id) {
        return Optional.ofNullable(id == null ? null : SETS_BY_ID.get(id));
    }

    public static Optional<Design> byModel(int model) {
        return Optional.ofNullable(BY_MODEL.get(model));
    }

    /** The named weapons of a field (boss weapons included), in catalogue order. */
    public static List<WeaponDesign> weaponsOf(int field) {
        List<WeaponDesign> found = new ArrayList<>();
        for (WeaponDesign weapon : WEAPONS) {
            if (weapon.field() == field) {
                found.add(weapon);
            }
        }
        return Collections.unmodifiableList(found);
    }

    public static List<ArmorSet> setsOf(int field) {
        List<ArmorSet> found = new ArrayList<>();
        for (ArmorSet set : SETS) {
            if (set.field() == field) {
                found.add(set);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Every design of a field that {@code source} yields (weapons, then armor pieces). */
    public static List<Design> fromSource(int field, DesignSource source) {
        List<Design> found = new ArrayList<>();
        for (Design design : DESIGNS) {
            if (design.field() == field && design.sources().contains(source)) {
                found.add(design);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** Level of step {@code step} (0..2) of a field. */
    public static int levelOf(int field, int step) {
        return LEVELS[Checks.range("field", field, 1, 10) - 1][Checks.range("step", step, 0, 2)];
    }

    // ------------------------------------------------------------------ curves

    /** Weapon attack bonus at a design level: the tiers' bonuses at their levels, linear between them. */
    public static double weaponBonusAt(int level) {
        return interpolate(WEAPON_BONUS, level);
    }

    /** Defense of a full set at a design level (before style, set and rarity). */
    public static double setDefenseAt(int level) {
        return interpolate(SET_DEFENSE, level);
    }

    /** Max health of a full set at a design level (before style, set and rarity). */
    public static double setHealthAt(int level) {
        return interpolate(SET_HEALTH, level);
    }

    private static double interpolate(double[] values, int level) {
        int clamped = Math.max(1, Math.min(50, level));
        for (int i = 1; i < CURVE_LEVELS.length; i++) {
            if (clamped <= CURVE_LEVELS[i]) {
                double t = (clamped - CURVE_LEVELS[i - 1]) / (double) (CURVE_LEVELS[i] - CURVE_LEVELS[i - 1]);
                return values[i - 1] + (values[i] - values[i - 1]) * t;
            }
        }
        return values[values.length - 1];
    }

    /** {@code 0.5 + level / 50}: how strongly a design's secondary bonuses apply at its level. */
    static double bonusScale(int level) {
        return 0.5 + level / 50.0;
    }

    // ------------------------------------------------------------------ the field weapons

    private static void fieldWeapons(List<WeaponDesign> out) {
        // 1 アルセリア王国 (Lv5/10/15): meadows, bandits, the lost underground city (d1)
        w(out, "meadowlark_blade", SWORD, 1, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "wayfarers_oath", SWORD, 1, 1, C, "", Feel.KEEN, Element.NONE);
        w(out, "galewild_saber", SWORD, 1, 2, CAL, "meadow", Feel.SWIFT, Element.THUNDER);
        w(out, "pasture_cleaver", GREAT, 1, 0, FM, "", Feel.HEAVY, Element.NONE);
        w(out, "hillwarden_greatsword", GREAT, 1, 1, C, "", Feel.LONG, Element.EARTH);
        w(out, "garms_maw", GREAT, 1, 2, LORD, "garm", Feel.HEAVY, Element.BLOOD);
        w(out, "undercity_kris", TWIN, 1, 0, D, "d1", Feel.KEEN, Element.SHADOW);
        w(out, "thistle_fangs", TWIN, 1, 1, FM, "", Feel.SWIFT, Element.VENOM);
        w(out, "hooks_reapers", TWIN, 1, 2, LORD, "hook", Feel.KEEN, Element.BLOOD);
        w(out, "herdsman_pike", SPEAR, 1, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "canal_partisan", SPEAR, 1, 1, D, "d1", Feel.LONG, Element.FROST);
        w(out, "sunfield_lance", SPEAR, 1, 2, C, "", Feel.ELEMENTAL, Element.HOLY);
        w(out, "sprout_wand", STAFF, 1, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "lantern_of_the_lost", STAFF, 1, 1, D, "d1", Feel.ELEMENTAL, Element.HOLY);
        w(out, "dandelion_staff", STAFF, 1, 2, C, "", Feel.SWIFT, Element.EARTH);
        w(out, "shepherds_bow", BOW, 1, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "rogues_recurve", BOW, 1, 1, D, "d1", Feel.KEEN, Element.VENOM);
        w(out, "skylark_longbow", BOW, 1, 2, C, "", Feel.SWIFT, Element.THUNDER);
        // 2 エルダーフォレスト (Lv10/15/20): moonlit woods, the ancient tree maze (d2)
        w(out, "moonleaf_blade", SWORD, 2, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "heartwood_edge", SWORD, 2, 1, D, "d2", Feel.BALANCED, Element.EARTH);
        w(out, "eclipse_moonblade", SWORD, 2, 2, CAL, "moon_forest", Feel.KEEN, Element.SHADOW);
        w(out, "thornwall_greatsword", GREAT, 2, 0, F, "", Feel.HEAVY, Element.VENOM);
        w(out, "rootbreaker", GREAT, 2, 1, D, "d2", Feel.HEAVY, Element.EARTH);
        w(out, "nightbough_claymore", GREAT, 2, 2, C, "", Feel.LONG, Element.SHADOW);
        w(out, "silkcutter_pair", TWIN, 2, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "moth_wing_daggers", TWIN, 2, 1, D, "d2", Feel.KEEN, Element.VENOM);
        w(out, "glowcap_stilettos", TWIN, 2, 2, C, "", Feel.ELEMENTAL, Element.HOLY);
        w(out, "vinewhip_spear", SPEAR, 2, 0, F, "", Feel.LONG, Element.VENOM);
        w(out, "owl_feather_lance", SPEAR, 2, 1, C, "", Feel.SWIFT, Element.NONE);
        w(out, "grovewarden_glaive", SPEAR, 2, 2, C, "", Feel.BALANCED, Element.EARTH);
        w(out, "mosslight_staff", STAFF, 2, 0, FM, "", Feel.BALANCED, Element.HOLY);
        w(out, "crescent_druid_staff", STAFF, 2, 1, C, "", Feel.KEEN, Element.FROST);
        w(out, "nebulas_starbranch", STAFF, 2, 2, LORD, "nebula", Feel.ELEMENTAL, Element.HOLY);
        w(out, "willow_shortbow", BOW, 2, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "silverbark_bow", BOW, 2, 1, C, "", Feel.BALANCED, Element.NONE);
        w(out, "belladonnas_nightshade", BOW, 2, 2, LORD, "belladonna", Feel.ELEMENTAL, Element.VENOM);
        // 3 サンドリア砂漠 (Lv15/20/25): the iron mine, the sand temple (d3)
        w(out, "ore_vein_sword", SWORD, 3, 0, F, "", Feel.BALANCED, Element.EARTH);
        w(out, "sunscarab_khopesh", SWORD, 3, 1, D, "d3", Feel.KEEN, Element.FIRE);
        w(out, "forgefire_blade", SWORD, 3, 2, C, "", Feel.ELEMENTAL, Element.FIRE);
        w(out, "pickaxe_breaker", GREAT, 3, 0, FM, "", Feel.HEAVY, Element.EARTH);
        w(out, "coalheart_greatsword", GREAT, 3, 1, C, "", Feel.HEAVY, Element.FIRE);
        w(out, "colossal_ironvein", GREAT, 3, 2, CAL, "iron_mine", Feel.HEAVY, Element.EARTH);
        w(out, "ember_shivs", TWIN, 3, 0, F, "", Feel.SWIFT, Element.FIRE);
        w(out, "tombsand_fangs", TWIN, 3, 1, D, "d3", Feel.KEEN, Element.VENOM);
        w(out, "cogwheel_daggers", TWIN, 3, 2, C, "", Feel.SWIFT, Element.NONE);
        w(out, "minecart_pike", SPEAR, 3, 0, F, "", Feel.LONG, Element.NONE);
        w(out, "veined_steel_lance", SPEAR, 3, 1, C, "", Feel.BALANCED, Element.NONE);
        w(out, "dolgas_tomb_halberd", SPEAR, 3, 2, LORD, "dolga", Feel.LONG, Element.SHADOW);
        w(out, "geode_staff", STAFF, 3, 0, FM, "", Feel.ELEMENTAL, Element.EARTH);
        w(out, "sunpriest_sceptre", STAFF, 3, 1, D, "d3", Feel.ELEMENTAL, Element.HOLY);
        w(out, "golem_core_rod", STAFF, 3, 2, C, "", Feel.KEEN, Element.THUNDER);
        w(out, "cave_wren_bow", BOW, 3, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "firedamp_longbow", BOW, 3, 1, C, "", Feel.ELEMENTAL, Element.FIRE);
        w(out, "rustbones_rattle", BOW, 3, 2, LORD, "rustbone", Feel.KEEN, Element.BLOOD);
        // 4 ノルディア雪原 (Lv20/25/30): the sacred falls, the frozen prison (d4), the ice castle (d5)
        w(out, "rivermist_sword", SWORD, 4, 0, F, "", Feel.BALANCED, Element.FROST);
        w(out, "pearlguard_blade", SWORD, 4, 1, C, "", Feel.BALANCED, Element.HOLY);
        w(out, "frostcastle_rapier", SWORD, 4, 2, DM, "d5", Feel.KEEN, Element.FROST);
        w(out, "cascade_greatsword", GREAT, 4, 0, F, "", Feel.HEAVY, Element.FROST);
        w(out, "jailers_verdict", GREAT, 4, 1, DM, "d4", Feel.HEAVY, Element.SHADOW);
        w(out, "gouras_avalanche", GREAT, 4, 2, LORD, "goura", Feel.HEAVY, Element.FROST);
        w(out, "icicle_pair", TWIN, 4, 0, FM, "", Feel.SWIFT, Element.FROST);
        w(out, "spirit_tear_daggers", TWIN, 4, 1, C, "", Feel.KEEN, Element.HOLY);
        w(out, "mistweavers_fangs", TWIN, 4, 2, LORD, "mistweaver", Feel.SWIFT, Element.VENOM);
        w(out, "whitewater_spear", SPEAR, 4, 0, F, "", Feel.LONG, Element.NONE);
        w(out, "pilgrim_glaive", SPEAR, 4, 1, C, "", Feel.BALANCED, Element.HOLY);
        w(out, "falls_sovereign_trident", SPEAR, 4, 2, CAL, "sacred_falls", Feel.LONG, Element.FROST);
        w(out, "holy_dew_staff", STAFF, 4, 0, F, "", Feel.BALANCED, Element.HOLY);
        w(out, "mistcrystal_rod", STAFF, 4, 1, C, "", Feel.ELEMENTAL, Element.FROST);
        w(out, "handmaidens_snowbell", STAFF, 4, 2, D, "d5", Feel.BALANCED, Element.HOLY);
        w(out, "heron_bow", BOW, 4, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "frozen_chain_bow", BOW, 4, 1, D, "d4", Feel.KEEN, Element.FROST);
        w(out, "rainbow_spray_bow", BOW, 4, 2, C, "", Feel.ELEMENTAL, Element.HOLY);
        // 5 ドラゴニア火山 (Lv30/35/40): ancient ruins, the volcano cave (d6)
        w(out, "cinder_rune_sword", SWORD, 5, 0, F, "", Feel.ELEMENTAL, Element.FIRE);
        w(out, "magma_vein_falchion", SWORD, 5, 1, D, "d6", Feel.BALANCED, Element.FIRE);
        w(out, "gearwork_blade", SWORD, 5, 2, C, "", Feel.SWIFT, Element.THUNDER);
        w(out, "ruin_guardian_slab", GREAT, 5, 0, FM, "", Feel.HEAVY, Element.EARTH);
        w(out, "amber_sun_greatsword", GREAT, 5, 1, C, "", Feel.LONG, Element.HOLY);
        w(out, "ashen_dragonfang", GREAT, 5, 2, CAL, "ancient_ruins", Feel.HEAVY, Element.FIRE);
        w(out, "ember_moth_blades", TWIN, 5, 0, F, "", Feel.SWIFT, Element.FIRE);
        w(out, "salamander_tongues", TWIN, 5, 1, D, "d6", Feel.ELEMENTAL, Element.FIRE);
        w(out, "glyphcut_daggers", TWIN, 5, 2, C, "", Feel.KEEN, Element.NONE);
        w(out, "scorchpoint_spear", SPEAR, 5, 0, F, "", Feel.BALANCED, Element.FIRE);
        w(out, "guardian_plate_halberd", SPEAR, 5, 1, C, "", Feel.LONG, Element.EARTH);
        w(out, "valkas_berserk_lance", SPEAR, 5, 2, LORD, "valka", Feel.HEAVY, Element.BLOOD);
        w(out, "kiln_staff", STAFF, 5, 0, FM, "", Feel.BALANCED, Element.FIRE);
        w(out, "lava_oracle_staff", STAFF, 5, 1, D, "d6", Feel.KEEN, Element.EARTH);
        w(out, "scorch_eyes_gaze", STAFF, 5, 2, LORD, "scorch_eye", Feel.ELEMENTAL, Element.FIRE);
        w(out, "sunstone_bow", BOW, 5, 0, F, "", Feel.BALANCED, Element.HOLY);
        w(out, "flamewing_bow", BOW, 5, 1, D, "d6", Feel.SWIFT, Element.FIRE);
        w(out, "rune_tablet_longbow", BOW, 5, 2, C, "", Feel.KEEN, Element.THUNDER);
        // 6 セレスティア天空大陸 (Lv40/45/50): soaring peaks, the sky ruins (d7)
        w(out, "cloudcutter", SWORD, 6, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "skyforge_saber", SWORD, 6, 1, D, "d7", Feel.KEEN, Element.THUNDER);
        w(out, "alvins_oathblade", SWORD, 6, 2, LORD, "alvin", Feel.BALANCED, Element.HOLY);
        w(out, "glacier_crest_claymore", GREAT, 6, 0, C, "", Feel.LONG, Element.FROST);
        w(out, "thunderhorn_greatsword", GREAT, 6, 1, FM, "", Feel.HEAVY, Element.NONE);
        w(out, "thunderhead_greatblade", GREAT, 6, 2, CAL, "soaring_peaks", Feel.HEAVY, Element.THUNDER);
        w(out, "wyvern_talon_pair", TWIN, 6, 0, F, "", Feel.SWIFT, Element.BLOOD);
        w(out, "sky_ruin_shards", TWIN, 6, 1, D, "d7", Feel.KEEN, Element.HOLY);
        w(out, "gale_feather_blades", TWIN, 6, 2, C, "", Feel.SWIFT, Element.THUNDER);
        w(out, "summit_spear", SPEAR, 6, 0, F, "", Feel.LONG, Element.NONE);
        w(out, "celestial_pillar_glaive", SPEAR, 6, 1, D, "d7", Feel.LONG, Element.HOLY);
        w(out, "lightning_rod_lance", SPEAR, 6, 2, C, "", Feel.ELEMENTAL, Element.THUNDER);
        w(out, "stormcaller_staff", STAFF, 6, 0, F, "", Feel.ELEMENTAL, Element.THUNDER);
        w(out, "cirrus_rod", STAFF, 6, 1, C, "", Feel.SWIFT, Element.FROST);
        w(out, "starwell_staff", STAFF, 6, 2, D, "d7", Feel.KEEN, Element.HOLY);
        w(out, "windrider_bow", BOW, 6, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "tempest_recurve", BOW, 6, 1, C, "", Feel.BALANCED, Element.THUNDER);
        w(out, "peak_snipers_horizon", BOW, 6, 2, LORD, "peak_sniper", Feel.KEEN, Element.NONE);
        // 7 終焉の塔 (Lv50): the forest of the end, the tower (d8)
        w(out, "hollowroot_blade", SWORD, 7, 0, F, "", Feel.BALANCED, Element.VENOM);
        w(out, "oblivion_edge", SWORD, 7, 1, C, "", Feel.KEEN, Element.SHADOW);
        w(out, "last_dusk", SWORD, 7, 2, CAL, "end_forest", Feel.BALANCED, Element.SHADOW);
        w(out, "nightmare_greatsword", GREAT, 7, 0, FM, "", Feel.HEAVY, Element.SHADOW);
        w(out, "towerfall_greatsword", GREAT, 7, 1, D, "d8", Feel.LONG, Element.EARTH);
        w(out, "black_knights_requiem", GREAT, 7, 2, LORD, "black_knight", Feel.HEAVY, Element.BLOOD);
        w(out, "shadowsilk_fangs", TWIN, 7, 0, F, "", Feel.SWIFT, Element.SHADOW);
        w(out, "doomguard_shivs", TWIN, 7, 1, DM, "d8", Feel.KEEN, Element.BLOOD);
        w(out, "voidwhisper_daggers", TWIN, 7, 2, C, "", Feel.ELEMENTAL, Element.SHADOW);
        w(out, "corrupt_bark_spear", SPEAR, 7, 0, FM, "", Feel.LONG, Element.VENOM);
        w(out, "gatebreaker_halberd", SPEAR, 7, 1, D, "d8", Feel.HEAVY, Element.EARTH);
        w(out, "nightmare_eye_lance", SPEAR, 7, 2, C, "", Feel.KEEN, Element.SHADOW);
        w(out, "cultist_bonestaff", STAFF, 7, 0, FM, "", Feel.BALANCED, Element.SHADOW);
        w(out, "abyssal_lantern", STAFF, 7, 1, D, "d8", Feel.ELEMENTAL, Element.HOLY);
        w(out, "xenos_ruinsceptre", STAFF, 7, 2, LORD, "xenos", Feel.KEEN, Element.FIRE);
        w(out, "deadwood_bow", BOW, 7, 0, F, "", Feel.BALANCED, Element.VENOM);
        w(out, "end_howl_longbow", BOW, 7, 1, D, "d8", Feel.KEEN, Element.SHADOW);
        w(out, "oblivion_shard_bow", BOW, 7, 2, C, "", Feel.ELEMENTAL, Element.FROST);
        // 8 黄金の農村 (Lv10/15/20): fields of wheat, scarecrows, the blight
        w(out, "wheatstalk_sword", SWORD, 8, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "millstone_blade", SWORD, 8, 1, C, "", Feel.BALANCED, Element.EARTH);
        w(out, "harvest_moon_sabre", SWORD, 8, 2, CF, "", Feel.KEEN, Element.HOLY);
        w(out, "plowshare_greatsword", GREAT, 8, 0, F, "", Feel.HEAVY, Element.EARTH);
        w(out, "granary_gate_blade", GREAT, 8, 1, C, "", Feel.LONG, Element.NONE);
        w(out, "brutus_horncleaver", GREAT, 8, 2, LORD, "brutus", Feel.HEAVY, Element.BLOOD);
        w(out, "twin_sickles", TWIN, 8, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "strawman_needles", TWIN, 8, 1, C, "", Feel.KEEN, Element.VENOM);
        w(out, "blight_harvest_scythes", TWIN, 8, 2, CAL, "farm", Feel.SWIFT, Element.VENOM);
        w(out, "pitchfork_spear", SPEAR, 8, 0, FM, "", Feel.LONG, Element.NONE);
        w(out, "golden_ear_lance", SPEAR, 8, 1, C, "", Feel.BALANCED, Element.HOLY);
        w(out, "harvest_boss_reaper", SPEAR, 8, 2, LORD, "harvest_boss", Feel.LONG, Element.BLOOD);
        w(out, "scarecrow_post", STAFF, 8, 0, F, "", Feel.BALANCED, Element.SHADOW);
        w(out, "sunflower_staff", STAFF, 8, 1, C, "", Feel.ELEMENTAL, Element.HOLY);
        w(out, "harvest_gem_rod", STAFF, 8, 2, CF, "", Feel.KEEN, Element.EARTH);
        w(out, "crow_scaring_bow", BOW, 8, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "cornsilk_bow", BOW, 8, 1, C, "", Feel.BALANCED, Element.NONE);
        w(out, "bullhorn_recurve", BOW, 8, 2, CF, "", Feel.HEAVY, Element.EARTH);
        // 9 静寂の湖畔 (Lv15/20/25): reeds, the drowned, the lake witch
        w(out, "reedcutter", SWORD, 9, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "lake_scale_sword", SWORD, 9, 1, C, "", Feel.BALANCED, Element.FROST);
        w(out, "mirror_surface_blade", SWORD, 9, 2, CAL, "lakeside", Feel.KEEN, Element.FROST);
        w(out, "driftwood_greatsword", GREAT, 9, 0, F, "", Feel.HEAVY, Element.NONE);
        w(out, "marshking_greatsword", GREAT, 9, 1, C, "", Feel.HEAVY, Element.VENOM);
        w(out, "deep_lake_slab", GREAT, 9, 2, CF, "", Feel.LONG, Element.FROST);
        w(out, "minnow_knives", TWIN, 9, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "gel_glass_daggers", TWIN, 9, 1, C, "", Feel.KEEN, Element.VENOM);
        w(out, "kingfisher_pair", TWIN, 9, 2, CF, "", Feel.SWIFT, Element.THUNDER);
        w(out, "rusted_harpoon", SPEAR, 9, 0, F, "", Feel.LONG, Element.BLOOD);
        w(out, "pearl_tide_spear", SPEAR, 9, 1, C, "", Feel.BALANCED, Element.FROST);
        w(out, "drowned_fishers_gaff", SPEAR, 9, 2, LORD, "drowned_fisher", Feel.LONG, Element.VENOM);
        w(out, "lotus_staff", STAFF, 9, 0, F, "", Feel.BALANCED, Element.HOLY);
        w(out, "lakebed_oracle", STAFF, 9, 1, C, "", Feel.KEEN, Element.HOLY);
        w(out, "melusines_mirror", STAFF, 9, 2, LORD, "melusine", Feel.ELEMENTAL, Element.FROST);
        w(out, "reed_bow", BOW, 9, 0, F, "", Feel.SWIFT, Element.NONE);
        w(out, "dragonfly_bow", BOW, 9, 1, C, "", Feel.KEEN, Element.THUNDER);
        w(out, "spirit_pearl_bow", BOW, 9, 2, CF, "", Feel.ELEMENTAL, Element.HOLY);
        // 10 潮騒の港町 (Lv20/25/30): docks, pirates, the maelstrom
        w(out, "deckhand_cutlass", SWORD, 10, 0, FM, "", Feel.SWIFT, Element.NONE);
        w(out, "doubloon_rapier", SWORD, 10, 1, C, "", Feel.KEEN, Element.NONE);
        w(out, "barbas_tidecleaver", SWORD, 10, 2, LORD, "barbas", Feel.BALANCED, Element.FROST);
        w(out, "anchor_greatsword", GREAT, 10, 0, FM, "", Feel.HEAVY, Element.EARTH);
        w(out, "stormsail_greatsword", GREAT, 10, 1, C, "", Feel.LONG, Element.THUNDER);
        w(out, "krakenbone_slab", GREAT, 10, 2, CF, "", Feel.HEAVY, Element.SHADOW);
        w(out, "saltspray_daggers", TWIN, 10, 0, F, "", Feel.SWIFT, Element.FROST);
        w(out, "inkstorm_blades", TWIN, 10, 1, C, "", Feel.ELEMENTAL, Element.SHADOW);
        w(out, "dock_brawlers_knuckleknives", TWIN, 10, 2, LORD, "dock_brawler", Feel.KEEN, Element.BLOOD);
        w(out, "boarding_pike", SPEAR, 10, 0, F, "", Feel.LONG, Element.NONE);
        w(out, "windlass_harpoon", SPEAR, 10, 1, C, "", Feel.BALANCED, Element.EARTH);
        w(out, "maelstrom_trident", SPEAR, 10, 2, CAL, "harbour", Feel.LONG, Element.FROST);
        w(out, "buoy_lantern_staff", STAFF, 10, 0, F, "", Feel.BALANCED, Element.HOLY);
        w(out, "compass_rose_staff", STAFF, 10, 1, C, "", Feel.SWIFT, Element.THUNDER);
        w(out, "kraken_ink_rod", STAFF, 10, 2, CF, "", Feel.ELEMENTAL, Element.SHADOW);
        w(out, "gunwale_bow", BOW, 10, 0, F, "", Feel.BALANCED, Element.NONE);
        w(out, "seagull_bow", BOW, 10, 1, C, "", Feel.SWIFT, Element.NONE);
        w(out, "trade_wind_longbow", BOW, 10, 2, CF, "", Feel.KEEN, Element.THUNDER);
    }

    // ------------------------------------------------------------------ boss weapons

    private static void bossWeapons(List<WeaponDesign> out) {
        boss(out, 0, "bandit_king_twinshade", TWIN, 1, 15, "b1", Feel.KEEN, Element.SHADOW,
                CombatModifiers.builder().crit(0.05).build(), WeaponArts.SHADOW_LEGION,
                List.of(new PassiveEffect.Evasion(0.05), new PassiveEffect.Lifesteal(0.02)));
        boss(out, 1, "ancient_tree_heartstaff", STAFF, 2, 20, "b2", Feel.BALANCED, Element.EARTH,
                CombatModifiers.builder().healing(0.08).healthFlat(15).build(), WeaponArts.THORNROOT_BLOOM,
                List.of(new PassiveEffect.HealOnKill(0.05)));
        boss(out, 2, "colossus_dunespear", SPEAR, 3, 25, "b3", Feel.HEAVY, Element.EARTH,
                CombatModifiers.builder().healthFlat(30).build(), WeaponArts.SAND_MAELSTROM,
                List.of(new PassiveEffect.Thorns(0.15), new PassiveEffect.LastStand(0.10, 0.05)));
        boss(out, 3, "frozen_executioner_blade", GREAT, 4, 30, "b4", Feel.HEAVY, Element.FROST,
                CombatModifiers.builder().critDamage(0.2).build(), WeaponArts.DOOM_GUILLOTINE,
                List.of(new PassiveEffect.LastStand(0.15, 0.08)));
        boss(out, 4, "frozen_queen_sceptre", STAFF, 4, 30, "b5", Feel.KEEN, Element.FROST,
                CombatModifiers.builder().magic(0.06).build(), WeaponArts.FROZEN_PRISON,
                List.of(new PassiveEffect.HealOnKill(0.04), new PassiveEffect.Evasion(0.03)));
        boss(out, 5, "dragon_king_flamebrand", SWORD, 5, 40, "b6", Feel.BALANCED, Element.FIRE,
                CombatModifiers.builder().physical(0.08).build(), WeaponArts.METEOR_SLASH,
                List.of(new PassiveEffect.Lifesteal(0.04)));
        boss(out, 6, "machine_god_skybow", BOW, 6, 50, "b7", Feel.KEEN, Element.THUNDER,
                CombatModifiers.builder().ranged(0.08).attackSpeed(0.04).build(), WeaponArts.HEAVENS_CHAIN,
                List.of(new PassiveEffect.Evasion(0.04), new PassiveEffect.HealOnKill(0.03)));
        boss(out, 7, "gatekeeper_end_halberd", SPEAR, 7, 50, "b8", Feel.LONG, Element.SHADOW,
                CombatModifiers.builder().defense(20).build(), WeaponArts.GATE_OF_END,
                List.of(new PassiveEffect.Lifesteal(0.03), new PassiveEffect.Thorns(0.10)));
        boss(out, 8, "king_of_end_greatsword", GREAT, 7, 50, "final", Feel.HEAVY, Element.SHADOW,
                CombatModifiers.builder().critDamage(0.3).crit(0.05).build(), WeaponArts.BLACK_SWORD_RAIN,
                List.of(new PassiveEffect.Lifesteal(0.05), new PassiveEffect.LastStand(0.20, 0.10)));
    }

    // ------------------------------------------------------------------ armor sets

    private static void armorSets(List<ArmorSet> out) {
        set(out, "meadow_runner", ArmorStyle.LIGHT, 1, 0, CF, "", Element.NONE);
        set(out, "homestead_guard", ArmorStyle.HEAVY, 1, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON),
                "d1", Element.EARTH);
        set(out, "wildflower_vestment", ArmorStyle.ROBE, 1, 2, CAL, "meadow", Element.HOLY);
        set(out, "moonleaf_robe", ArmorStyle.ROBE, 2, 0, CF, "", Element.HOLY);
        set(out, "silkshadow", ArmorStyle.LIGHT, 2, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON), "d2",
                Element.SHADOW);
        set(out, "heartwood_bulwark", ArmorStyle.HEAVY, 2, 2, CAL, "moon_forest", Element.EARTH);
        set(out, "ironvein_plate", ArmorStyle.HEAVY, 3, 0, CF, "", Element.EARTH);
        set(out, "ember_scholar", ArmorStyle.ROBE, 3, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON), "d3",
                Element.FIRE);
        set(out, "dune_stalker", ArmorStyle.LIGHT, 3, 2, CAL, "iron_mine", Element.VENOM);
        set(out, "rivermist_garb", ArmorStyle.LIGHT, 4, 0, CF, "", Element.FROST);
        set(out, "glacier_warden", ArmorStyle.HEAVY, 4, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON), "d4",
                Element.FROST);
        set(out, "holy_spring_vestment", ArmorStyle.ROBE, 4, 2, CAL, "sacred_falls", Element.HOLY);
        set(out, "rune_sage", ArmorStyle.ROBE, 5, 0, CF, "", Element.THUNDER);
        set(out, "cinder_dancer", ArmorStyle.LIGHT, 5, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON), "d6",
                Element.FIRE);
        set(out, "ruin_guardian_plate", ArmorStyle.HEAVY, 5, 2, CAL, "ancient_ruins", Element.EARTH);
        set(out, "thunderpeak_armor", ArmorStyle.HEAVY, 6, 0, CF, "", Element.THUNDER);
        set(out, "cloudweaver", ArmorStyle.ROBE, 6, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON), "d7",
                Element.HOLY);
        set(out, "skystrider", ArmorStyle.LIGHT, 6, 2, CAL, "soaring_peaks", Element.THUNDER);
        set(out, "voidstep", ArmorStyle.LIGHT, 7, 0, CF, "", Element.SHADOW);
        set(out, "endless_night_plate", ArmorStyle.HEAVY, 7, 1, EnumSet.of(DesignSource.CRAFT, DesignSource.DUNGEON),
                "d8", Element.SHADOW);
        set(out, "oblivion_vestment", ArmorStyle.ROBE, 7, 2, CAL, "end_forest", Element.SHADOW);
        set(out, "farmstead_bulwark", ArmorStyle.HEAVY, 8, 0, CF, "", Element.EARTH);
        set(out, "scarecrow_garb", ArmorStyle.LIGHT, 8, 1, C, "", Element.VENOM);
        set(out, "harvest_rite", ArmorStyle.ROBE, 8, 2, CAL, "farm", Element.HOLY);
        set(out, "lotus_vestment", ArmorStyle.ROBE, 9, 0, CF, "", Element.HOLY);
        set(out, "lakescale_mail", ArmorStyle.HEAVY, 9, 1, C, "", Element.FROST);
        set(out, "reedwalker", ArmorStyle.LIGHT, 9, 2, CAL, "lakeside", Element.VENOM);
        set(out, "privateer_garb", ArmorStyle.LIGHT, 10, 0, CF, "", Element.NONE);
        set(out, "tidecaller_robe", ArmorStyle.ROBE, 10, 1, C, "", Element.FROST);
        set(out, "anchorguard_plate", ArmorStyle.HEAVY, 10, 2, CAL, "harbour", Element.EARTH);
    }

    // ------------------------------------------------------------------ builders

    private static void w(List<WeaponDesign> out, String id, WeaponType type, int field, int step,
                          Set<DesignSource> sources, String origin, Feel feel, Element element) {
        int level = levelOf(field, step);
        int model = Design.WEAPON_MODELS + (field - 1) * 18 + type.ordinal() * 3 + step;
        double source = sourceFactor(sources);
        boolean ranged = type == WeaponType.STAFF || type == WeaponType.BOW;
        boolean signature = sources.contains(DesignSource.LORD) || sources.contains(DesignSource.CALAMITY);
        double proc = element == Element.NONE ? 0.0
                : (feel == Feel.ELEMENTAL ? 0.22 : 0.10) + (signature ? 0.04 : 0.0);
        List<PassiveEffect> perks = new ArrayList<>();
        if (signature) {
            perks.add(switch (feel) {
                case HEAVY -> new PassiveEffect.LastStand(0.12, 0.06);
                case SWIFT -> new PassiveEffect.Evasion(0.04);
                case KEEN -> new PassiveEffect.HealOnKill(0.03);
                case ELEMENTAL -> new PassiveEffect.Lifesteal(0.015);
                case BALANCED, LONG -> new PassiveEffect.Lifesteal(0.02);
            });
            if (sources.contains(DesignSource.CALAMITY)) {
                perks.add(new PassiveEffect.HealOnKill(0.04));
            }
        }
        out.add(new WeaponDesign(id, model, type, field, level, sources, origin, round(feel.attack * source),
                feel.crit, feel.speed, ranged ? 1.0 : feel.reach, element, round(proc),
                weaponBonus(type, level, sources, feel, element), bias(type, feel, element), null, perks));
    }

    private static void boss(List<WeaponDesign> out, int index, String id, WeaponType type, int field, int level,
                             String origin, Feel feel, Element element, CombatModifiers extra, WeaponArt art,
                             List<PassiveEffect> perks) {
        if (art.type() != type) {
            throw new ExceptionInInitializerError(id + ": its art belongs to another weapon type");
        }
        boolean ranged = type == WeaponType.STAFF || type == WeaponType.BOW;
        double proc = element == Element.NONE ? 0.0 : (feel == Feel.ELEMENTAL ? 0.26 : 0.14);
        CombatModifiers bonus = weaponBonus(type, level, EnumSet.of(DesignSource.BOSS), feel, element).plus(extra);
        out.add(new WeaponDesign(id, BOSS_MODELS + index, type, field, level, BOSS, origin,
                round(feel.attack * sourceFactor(EnumSet.of(DesignSource.BOSS))), feel.crit, feel.speed,
                ranged ? 1.0 : feel.reach, element, round(proc), bonus, bias(type, feel, element), art.id(), perks));
    }

    private static void set(List<ArmorSet> out, String id, ArmorStyle style, int field, int step,
                            Set<DesignSource> sources, String origin, Element theme) {
        int level = levelOf(field, step);
        int model = Design.ARMOR_MODELS + out.size() * ArmorSet.PIECES;
        double quality = sources.contains(DesignSource.CALAMITY) ? 1.08
                : sources.contains(DesignSource.DUNGEON) ? 1.04 : 1.0;
        double k = 0.6 + 0.8 * level / 50.0;
        CombatModifiers piece;
        ArmorSet.SetBonus two;
        List<PassiveEffect> fourPerks = new ArrayList<>();
        switch (style) {
            case HEAVY -> {
                piece = CombatModifiers.builder().guard(0.005).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().defense(r1(10 * k)).healthFlat(r1(12 * k))
                        .build(), List.of());
                fourPerks.add(new PassiveEffect.LastStand(0.0, 0.06));
            }
            case LIGHT -> {
                piece = CombatModifiers.builder().moveSpeed(0.005).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().crit(round(0.02 * k)).moveSpeed(0.02).build(),
                        List.of());
                fourPerks.add(new PassiveEffect.Evasion(0.03));
            }
            case ROBE -> {
                piece = CombatModifiers.builder().magic(0.005).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().magic(round(0.04 * k)).healing(round(0.04 * k))
                        .build(), List.of());
                fourPerks.add(new PassiveEffect.HealOnKill(0.03));
            }
            default -> throw new IllegalStateException("unreachable");
        }
        CombatModifiers fourMods = switch (style) {
            case HEAVY -> CombatModifiers.builder().guard(0.05).healthPercent(round(0.04 * k)).build();
            case LIGHT -> CombatModifiers.builder().attackSpeed(round(0.04 * k)).critDamage(round(0.08 * k)).build();
            case ROBE -> CombatModifiers.builder().magic(round(0.06 * k)).stamina(r1(15 * k)).build();
        };
        switch (theme) {
            case FIRE -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.BURN, 0.05, 60, 0.0, false));
            case FROST -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.SLOW, 0.06, 40, 1.0, false));
            case THUNDER -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.STUN, 0.03, 10, 0.0, false));
            case VENOM -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.POISON, 0.05, 80, 0.15, false));
            case HOLY -> fourMods = fourMods.plus(CombatModifiers.builder().healing(round(0.05 * k)).build());
            case SHADOW -> fourPerks.add(new PassiveEffect.Evasion(0.02));
            case BLOOD -> fourPerks.add(new PassiveEffect.Lifesteal(0.02));
            case EARTH -> fourPerks.add(new PassiveEffect.Thorns(0.10));
            case NONE -> fourMods = fourMods.plus(CombatModifiers.builder().healthFlat(r1(8 * k)).build());
        }
        ArmorSet.SetBonus four = new ArmorSet.SetBonus(fourMods, fourPerks);
        out.add(new ArmorSet(id, model, style, field, level, sources, origin, theme, quality, quality, piece, two,
                four));
    }

    private static double sourceFactor(Set<DesignSource> sources) {
        double best = 0.0;
        for (DesignSource source : sources) {
            best = Math.max(best, switch (source) {
                case FIELD, MONSTER -> 1.02;
                case CRAFT -> 1.05;
                case DUNGEON -> 1.08;
                case LORD -> 1.10;
                case CALAMITY, BOSS -> 1.12;
            });
        }
        return best;
    }

    /** Damage of the weapon's type by source, an element trait and a feel trait, scaled by the level. */
    private static CombatModifiers weaponBonus(WeaponType type, int level, Set<DesignSource> sources, Feel feel,
                                               Element element) {
        double k = bonusScale(level);
        double damage = 0.0;
        for (DesignSource source : sources) {
            damage = Math.max(damage, switch (source) {
                case FIELD, MONSTER -> 0.01;
                case CRAFT -> 0.02;
                case DUNGEON -> 0.03;
                case LORD -> 0.04;
                case CALAMITY -> 0.05;
                case BOSS -> 0.06;
            });
        }
        CombatModifiers.Builder b = CombatModifiers.builder();
        double percent = round(damage * k);
        DamageType damageType = type.damageType();
        switch (damageType) {
            case MAGIC -> b.magic(percent);
            case RANGED -> b.ranged(percent);
            default -> b.physical(percent);
        }
        switch (element) {
            case FIRE -> b.critDamage(round(0.04 * k));
            case FROST -> b.defense(r1(4 * k));
            case THUNDER -> b.attackSpeed(round(0.02 * k));
            case VENOM -> b.moveSpeed(round(0.02 * k));
            case HOLY -> b.healing(round(0.03 * k));
            case SHADOW -> b.crit(round(0.01 * k));
            case BLOOD -> b.critDamage(round(0.06 * k));
            case EARTH -> b.healthFlat(r1(8 * k));
            case NONE -> {
            }
        }
        switch (feel) {
            case SWIFT -> b.moveSpeed(round(0.01 * k));
            case HEAVY -> b.guard(0.03);
            case KEEN -> b.critDamage(round(0.05 * k));
            default -> {
            }
        }
        return b.build();
    }

    /** Affix tendencies: the weapon's attack stat, plus the feel's and the element's. */
    private static Map<AffixKind, Integer> bias(WeaponType type, Feel feel, Element element) {
        AffixPool pool = AffixPool.of(EquipmentSlot.WEAPON, type);
        Map<AffixKind, Integer> bias = new EnumMap<>(AffixKind.class);
        AffixKind main = switch (type) {
            case STAFF -> AffixKind.INT;
            case BOW -> AffixKind.DEX;
            default -> AffixKind.STR;
        };
        AffixKind damage = switch (type) {
            case STAFF -> AffixKind.MAGIC_PERCENT;
            case BOW -> AffixKind.RANGED_PERCENT;
            default -> AffixKind.PHYSICAL_PERCENT;
        };
        bias.put(main, 2);
        switch (feel) {
            case SWIFT -> bias.put(AffixKind.AGI, 3);
            case HEAVY -> {
                bias.put(main, 4);
                bias.put(damage, 2);
            }
            case KEEN -> {
                bias.put(AffixKind.CRIT_CHANCE, 3);
                bias.put(AffixKind.CRIT_DAMAGE, 2);
                bias.put(AffixKind.LUK, 2);
            }
            case LONG -> bias.put(damage, 2);
            case ELEMENTAL -> bias.put(damage, 3);
            case BALANCED -> {
            }
        }
        if (element == Element.HOLY && pool.allows(AffixKind.HEALING_PERCENT)) {
            bias.put(AffixKind.HEALING_PERCENT, 3);
        }
        bias.keySet().removeIf(kind -> !pool.allows(kind));
        return bias;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double r1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
