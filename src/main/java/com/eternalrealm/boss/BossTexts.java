package com.eternalrealm.boss;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Translation keys the platform shows around boss fights besides the names (texts in docs/i18n/bosses_v2.json):
 * hints for mechanics, the transformation and defeat titles, and the death messages of the boss damage types.
 */
public final class BossTexts {
    private static final String PREFIX = "message." + ModInfo.MOD_ID + ".boss.";

    /** Action bar when a safe-zone attack starts. No arguments. */
    public static final String SAFE_ZONES = PREFIX + "safe_zones";
    /** Action bar when a stack (soak) marker appears, argument 1: the marked player. */
    public static final String STACK = PREFIX + "stack";
    /** Title shown while the boss transforms, argument 1: boss name. */
    public static final String TRANSFORM_TITLE = "title." + ModInfo.MOD_ID + ".boss.transform";
    /** Subtitle under {@link #TRANSFORM_TITLE}, argument 1: the form number (2, 3, 4). */
    public static final String TRANSFORM_SUBTITLE = "title." + ModInfo.MOD_ID + ".boss.transform.form";
    /** Title when the boss collapses. No arguments. */
    public static final String DEFEATED_TITLE = "title." + ModInfo.MOD_ID + ".boss.defeated";
    /** Subtitle under {@link #DEFEATED_TITLE}, argument 1: boss name. */
    public static final String DEFEATED_SUBTITLE = "title." + ModInfo.MOD_ID + ".boss.defeated.subtitle";

    /** Damage type (data/eternalrealm/damage_type) of hits that can be dodged but not guarded. */
    public static final String UNGUARDABLE = "boss_unguardable";
    /** Damage type of hits that can be neither dodged nor guarded (soaks, safe zones). */
    public static final String UNAVOIDABLE = "boss_unavoidable";
    /** Damage type of lasting hazards. */
    public static final String HAZARD = "boss_hazard";
    public static final List<String> DAMAGE_TYPES = List.of(UNGUARDABLE, UNAVOIDABLE, HAZARD);

    private BossTexts() {
    }

    /**
     * Death message key of a boss damage type: {@code death.attack.eternalrealm.<type>} (argument 1 the victim,
     * argument 2 the boss), or with {@code player = true} the variant used when the hit had no attacker.
     */
    public static String deathMessage(String damageType, boolean player) {
        return "death.attack." + ModInfo.MOD_ID + "." + damageType + (player ? ".player" : "");
    }

    /** Every key above. */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>(List.of(SAFE_ZONES, STACK, TRANSFORM_TITLE, TRANSFORM_SUBTITLE,
                DEFEATED_TITLE, DEFEATED_SUBTITLE));
        for (String type : DAMAGE_TYPES) {
            keys.add(deathMessage(type, false));
            keys.add(deathMessage(type, true));
        }
        return List.copyOf(keys);
    }
}
