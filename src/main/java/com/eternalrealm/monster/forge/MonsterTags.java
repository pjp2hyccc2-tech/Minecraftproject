package com.eternalrealm.monster.forge;

import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/** What makes a mob an ETERNAL REALM monster: an entity tag plus its template, level and damage (saved with it). */
public final class MonsterTags {
    public static final String TAG = "eternalrealm_monster";
    static final String TEMPLATE = "er_template";
    static final String LEVEL = "er_level";
    static final String DAMAGE = "er_damage";
    /** Summons (vexes of an evoker) fight with their owner's tag but give no rewards. */
    static final String NO_REWARD = "er_no_reward";

    private MonsterTags() {
    }

    public static boolean isMonster(Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    public static Optional<MonsterTemplate> template(Entity entity) {
        if (!isMonster(entity)) {
            return Optional.empty();
        }
        return Monsters.byId(entity.getPersistentData().getString(TEMPLATE));
    }

    public static int level(Entity entity) {
        return Math.max(1, entity.getPersistentData().getInt(LEVEL));
    }

    /** Damage of one of its hits before the victim's defense. */
    public static double damage(Entity entity) {
        return entity.getPersistentData().getDouble(DAMAGE);
    }

    public static boolean givesRewards(Entity entity) {
        return isMonster(entity) && !entity.getPersistentData().getBoolean(NO_REWARD);
    }

    static void mark(Entity entity, String templateId, int level, double damage, boolean rewards) {
        entity.addTag(TAG);
        CompoundTag data = entity.getPersistentData();
        data.putString(TEMPLATE, templateId);
        data.putInt(LEVEL, level);
        data.putDouble(DAMAGE, damage);
        if (!rewards) {
            data.putBoolean(NO_REWARD, true);
        }
    }
}
