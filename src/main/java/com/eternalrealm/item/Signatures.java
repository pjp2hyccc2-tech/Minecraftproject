package com.eternalrealm.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The drop hooks of the named designs: what a field lord (ヌシ), a calamity (災厄), a dungeon and a boss leave
 * behind, and which monsters visibly carry a named weapon (and may drop it). Pure lookups on {@link Armory}; the
 * chances are {@link ArmoryDrops}'.
 *
 * <p>Every lord of {@code FieldThreats} has exactly one signature weapon, every calamity one weapon and one armor set,
 * every boss ({@code b1..b8}, {@code final}) one boss weapon; the unit tests check that the ids resolve.</p>
 */
public final class Signatures {
    /**
     * Monster templates that carry a named weapon in their hand (their body can show it), and which. Lords carry their
     * own signature weapon instead ({@link #lordWeapon}).
     */
    private static final Map<String, String> WIELDED = Map.ofEntries(
            Map.entry("bandit_chief", "pasture_cleaver"),
            Map.entry("d1_bandit", "thistle_fangs"),
            Map.entry("forest_witch", "mosslight_staff"),
            Map.entry("d2_grove_priestess", "mosslight_staff"),
            Map.entry("sand_husk", "pickaxe_breaker"),
            Map.entry("d3_sun_priest", "geode_staff"),
            Map.entry("frozen_dead", "icicle_pair"),
            Map.entry("d4_jailer", "jailers_verdict"),
            Map.entry("d5_ice_knight", "frostcastle_rapier"),
            Map.entry("dragonkin_berserker", "ruin_guardian_slab"),
            Map.entry("crater_blaze", "kiln_staff"),
            Map.entry("celestial_paladin", "thunderhorn_greatsword"),
            Map.entry("knight_of_the_end", "nightmare_greatsword"),
            Map.entry("d8_doom_guard", "doomguard_shivs"),
            Map.entry("void_wanderer", "corrupt_bark_spear"),
            Map.entry("doom_cultist", "cultist_bonestaff"),
            Map.entry("herald_of_ruin", "cultist_bonestaff"),
            Map.entry("scarecrow_dead", "pitchfork_spear"),
            Map.entry("pirate_cutthroat", "deckhand_cutlass"),
            Map.entry("pirate_captain", "anchor_greatsword"));

    /**
     * Lord templates whose body shows a held weapon: the lord then carries its signature weapon. Lords on beast, golem
     * or archer bodies (whose AI needs their own bow or crossbow) keep their natural weapons.
     */
    private static final Map<String, Boolean> LORD_BODY_SHOWS_WEAPON = Map.of(
            "garm", true, "valka", true, "scorch_eye", true, "alvin", true, "xenos", true,
            "black_knight", true, "drowned_fisher", true, "barbas", true, "dock_brawler", true);

    private Signatures() {
    }

    /** The signature weapon of a field lord (ヌシ), e.g. {@code garm} → ガルムの大顎. */
    public static Optional<WeaponDesign> lordWeapon(String lordId) {
        return signature(DesignSource.LORD, lordId);
    }

    /** Whether the lord's body visibly carries its signature weapon. */
    public static boolean lordWieldsWeapon(String lordId) {
        return LORD_BODY_SHOWS_WEAPON.getOrDefault(lordId, false) && lordWeapon(lordId).isPresent();
    }

    /** The weapon of a calamity (災厄), by calamity id ({@code meadow} .. {@code harbour}). */
    public static Optional<WeaponDesign> calamityWeapon(String calamityId) {
        return signature(DesignSource.CALAMITY, calamityId);
    }

    /** The armor set of a calamity; it drops one piece at a time. */
    public static Optional<ArmorSet> calamityArmor(String calamityId) {
        for (ArmorSet set : Armory.sets()) {
            if (set.has(DesignSource.CALAMITY) && set.origin().equals(calamityId)) {
                return Optional.of(set);
            }
        }
        return Optional.empty();
    }

    /** The boss weapon of a boss ({@code b1} .. {@code b8}, {@code final}). */
    public static Optional<WeaponDesign> bossWeapon(String bossId) {
        return signature(DesignSource.BOSS, bossId);
    }

    /** The designs a dungeon's boss may reward ({@code d1} .. {@code d8}): weapons and the pieces of its sets. */
    public static List<Design> dungeonDesigns(String dungeonId) {
        List<Design> found = new ArrayList<>();
        for (Design design : Armory.designs()) {
            if (design.sources().contains(DesignSource.DUNGEON) && design.origin().equals(dungeonId)) {
                found.add(design);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /** The named weapon a monster template carries in its hand (and may drop), if any. */
    public static Optional<WeaponDesign> wielded(String templateId) {
        String id = templateId == null ? null : WIELDED.get(templateId);
        return id == null ? Optional.empty() : Armory.weapon(id);
    }

    /** Every template that carries a named weapon. */
    public static Map<String, String> wielders() {
        return WIELDED;
    }

    private static Optional<WeaponDesign> signature(DesignSource source, String origin) {
        if (origin == null || origin.isEmpty()) {
            return Optional.empty();
        }
        for (WeaponDesign weapon : Armory.weapons()) {
            if (weapon.has(source) && weapon.origin().equals(origin)) {
                return Optional.of(weapon);
            }
        }
        return Optional.empty();
    }
}
