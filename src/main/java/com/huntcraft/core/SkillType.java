package com.huntcraft.core;

/**
 * All hunter skills. Levels come from armor pieces and charms (gear.armor module provides
 * the level calculation through {@link HunterSkills#setProvider}).
 * Effects are implemented where they apply (core damage calc, stamina, weapons, quests).
 */
public enum SkillType {
    ATTACK_BOOST(5),        // 攻撃: +3 raw per level, lv4+ affinity +5%
    CRITICAL_EYE(5),        // 見切り: +5% affinity per level
    WEAKNESS_EXPLOIT(3),    // 弱点特効: +15/30/50% affinity on weak spots (hitzone >= 45)
    CRITICAL_BOOST(3),      // 超会心: crit multiplier 1.30/1.35/1.40
    ELEMENT_ATTACK(3),      // 属性攻撃強化: element +10% per level
    HEALTH_BOOST(3),        // 体力増強: +4 max HP per level
    DEFENSE_BOOST(5),       // 防御: +1 armor per level
    CONSTITUTION(5),        // 体術: stamina cost -10% per level
    STAMINA_SURGE(3),       // スタミナ急速回復: stamina regen +25% per level
    EVADE_WINDOW(3),        // 回避性能: +2 i-frame ticks per level
    GUARD(3),               // ガード性能: guard stamina cost & knockback reduced
    EARPLUGS(2),            // 耳栓: lv1 small roars, lv2 all roars
    FIRE_RES(3),            // 火耐性 (lv3: fireblight immunity)
    WATER_RES(3),
    THUNDER_RES(3),
    ICE_RES(3),
    DRAGON_RES(3),
    HANDICRAFT(3),          // 匠: +sharpness capacity
    RAZOR_SHARP(3),         // 業物: chance to not consume sharpness
    SPEED_SHARPENING(2),    // 砥石高速化
    FOCUS(3),               // 集中: faster charge (GS/Hammer/Bow), gauges fill faster
    HEROICS(3),             // 火事場力: +attack/def when HP < 35%
    AGITATOR(3),            // 挑戦者: +attack/affinity while monster is enraged
    RECOVERY_UP(2),         // 回復量UP: potions heal +25% per level
    GATHERING(2),           // 採取+: extra gather yields
    CARVING(2),             // 剥ぎ取り+: extra carves
    STUN_RES(2),            // 気絶耐性
    POISON_RES(2),          // 毒耐性
    TREMOR_RES(1),          // 耐震
    WINDPROOF(2),           // 風圧耐性
    HEAT_COLD_RES(1);       // 暑さ寒さ無効

    public final int maxLevel;

    SkillType(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public String key() {
        return "skill.huntcraft." + name().toLowerCase();
    }

    public static SkillType byName(String s) {
        for (SkillType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        return null;
    }
}
