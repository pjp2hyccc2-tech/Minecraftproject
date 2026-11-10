package com.eternalrealm.revival;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "強くなるには" — the advice on the revival screen, chosen from what the fallen player could actually improve (a
 * "become stronger" panel): level below the place's recommendation, unspent points, no potions, gear
 * that was never enhanced, fighting a dungeon alone. Ordered by how much each would help; at most {@link #MAX} tips,
 * the last slot falls back to a combat hint.
 */
public final class RevivalTips {
    public static final int MAX = 3;

    /** What the server knows about the fallen player. */
    public record Situation(int level, int recommendedLevel, int unspentStatPoints, int unspentSkillPoints,
                            int potions, int unenhancedPieces, int partySize, RevivalRules.Place place,
                            boolean bossFight) {
    }

    public enum Kind {
        LEVEL,
        STAT_POINTS,
        SKILL_POINTS,
        POTIONS,
        ENHANCE,
        PARTY,
        DODGE,
        BOSS_PATTERNS;

        public String translationKey() {
            return "revival." + ModInfo.MOD_ID + ".tip." + name().toLowerCase(Locale.ROOT);
        }

        /** Icon hint for the client (an item id path of the mod or vanilla). */
        public String icon() {
            return switch (this) {
                case LEVEL -> "minecraft:experience_bottle";
                case STAT_POINTS -> "minecraft:nether_star";
                case SKILL_POINTS -> "minecraft:enchanted_book";
                case POTIONS -> ModInfo.MOD_ID + ":health_potion_medium";
                case ENHANCE -> "minecraft:anvil";
                case PARTY -> "minecraft:bell";
                case DODGE -> "minecraft:feather";
                case BOSS_PATTERNS -> "minecraft:spyglass";
            };
        }
    }

    /** One tip with the numbers its text needs. */
    public record Tip(Kind kind, List<String> args) {
        public Tip {
            args = List.copyOf(args);
        }
    }

    private RevivalTips() {
    }

    public static List<Tip> choose(Situation s) {
        List<Tip> tips = new ArrayList<>();
        if (s.recommendedLevel() > 0 && s.level() < s.recommendedLevel()) {
            tips.add(new Tip(Kind.LEVEL, List.of(Integer.toString(s.recommendedLevel()), Integer.toString(s.level()))));
        }
        if (s.unspentStatPoints() > 0) {
            tips.add(new Tip(Kind.STAT_POINTS, List.of(Integer.toString(s.unspentStatPoints()))));
        }
        if (s.unspentSkillPoints() > 0) {
            tips.add(new Tip(Kind.SKILL_POINTS, List.of(Integer.toString(s.unspentSkillPoints()))));
        }
        if (s.potions() <= 0) {
            tips.add(new Tip(Kind.POTIONS, List.of()));
        }
        if (s.unenhancedPieces() >= 2) {
            tips.add(new Tip(Kind.ENHANCE, List.of(Integer.toString(s.unenhancedPieces()))));
        }
        if (s.partySize() <= 1 && (s.place() == RevivalRules.Place.DUNGEON || s.bossFight())) {
            tips.add(new Tip(Kind.PARTY, List.of()));
        }
        if (tips.size() >= MAX) {
            return List.copyOf(tips.subList(0, MAX));
        }
        tips.add(new Tip(s.bossFight() ? Kind.BOSS_PATTERNS : Kind.DODGE, List.of()));
        if (tips.size() < MAX && s.bossFight()) {
            tips.add(new Tip(Kind.DODGE, List.of()));
        }
        return List.copyOf(tips);
    }
}
