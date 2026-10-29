package com.huntcraft.core;

import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Skill level lookup. The armor module installs a provider that sums armor + charm skill points.
 * Every system that needs a skill calls {@link #level(Player, SkillType)}.
 */
public final class HunterSkills {
    private static Function<Player, Map<SkillType, Integer>> provider = p -> Collections.emptyMap();

    private HunterSkills() {
    }

    /** Called by the armor module during init. */
    public static void setProvider(Function<Player, Map<SkillType, Integer>> p) {
        provider = p;
    }

    /** All active skills (clamped to max level). */
    public static Map<SkillType, Integer> all(Player player) {
        if (player == null) return Collections.emptyMap();
        Map<SkillType, Integer> raw = provider.apply(player);
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<SkillType, Integer> out = new EnumMap<>(SkillType.class);
        raw.forEach((k, v) -> {
            int lv = Math.min(k.maxLevel, v);
            if (lv > 0) out.put(k, lv);
        });
        return out;
    }

    public static int level(Player player, SkillType type) {
        if (player == null) return 0;
        Map<SkillType, Integer> raw = provider.apply(player);
        if (raw == null) return 0;
        Integer v = raw.get(type);
        return v == null ? 0 : Math.max(0, Math.min(type.maxLevel, v));
    }
}
