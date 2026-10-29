package com.huntcraft.village.companion;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Companion skills unlocked by level (type-specific skills plus shared ones). */
public enum MofuSkill {
    // fighter
    COMBO(MofuType.FIGHTER, 5),
    RAMPAGE(MofuType.FIGHTER, 15),
    BLADE_DANCE(MofuType.FIGHTER, 30),
    // healer
    SOOTHING(MofuType.HEALER, 5),
    ENCORE(MofuType.HEALER, 15),
    REVIVAL(MofuType.HEALER, 30),
    // bomber
    BIG_BARREL(MofuType.BOMBER, 5),
    FLASH_BARREL(MofuType.BOMBER, 15),
    CARPET_BOMB(MofuType.BOMBER, 30),
    // shared
    GUTS(null, 10),
    GATHERER(null, 20),
    VETERAN(null, 40);

    /** null = every type. */
    @Nullable
    public final MofuType type;
    public final int level;

    MofuSkill(@Nullable MofuType type, int level) {
        this.type = type;
        this.level = level;
    }

    public String key() {
        return "mofu.huntcraft.skill." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable(key());
    }

    public Component description() {
        return Component.translatable(key() + ".desc");
    }

    public boolean appliesTo(MofuType t) {
        return type == null || type == t;
    }

    public boolean has(MofuType t, int lv) {
        return appliesTo(t) && lv >= level;
    }

    /** Every skill a companion of this type can learn, in unlock order. */
    public static List<MofuSkill> forType(MofuType t) {
        List<MofuSkill> out = new ArrayList<>();
        for (MofuSkill s : values()) if (s.appliesTo(t)) out.add(s);
        out.sort((a, b) -> Integer.compare(a.level, b.level));
        return out;
    }
}
