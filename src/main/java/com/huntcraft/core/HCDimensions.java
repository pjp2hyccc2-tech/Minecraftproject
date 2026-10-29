package com.huntcraft.core;

import com.huntcraft.HuntCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * Dimension keys. The JSON for these dimensions lives in data/huntcraft/dimension/ (quest + village modules).
 */
public final class HCDimensions {
    /** Hub: village + gathering hall. Monsters can never exist here. */
    public static final ResourceKey<Level> HUNTER_VILLAGE = key("hunter_village");
    /** Quest map 1: grassy hills / forest (low rank start). */
    public static final ResourceKey<Level> VERDANT_HILLS = key("verdant_hills");
    /** Quest map 2: snowy mountain range. */
    public static final ResourceKey<Level> FROSTPEAK_RANGE = key("frostpeak_range");
    /** Quest map 3: volcanic caldera. */
    public static final ResourceKey<Level> EMBER_CALDERA = key("ember_caldera");

    public static final Set<ResourceKey<Level>> QUEST_MAPS = Set.of(VERDANT_HILLS, FROSTPEAK_RANGE, EMBER_CALDERA);

    private HCDimensions() {
    }

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, HuntCraft.id(path));
    }

    public static boolean isVillage(Level level) {
        return level != null && level.dimension() == HUNTER_VILLAGE;
    }

    public static boolean isQuestMap(Level level) {
        return level != null && QUEST_MAPS.contains(level.dimension());
    }

    public static boolean isHuntDimension(Level level) {
        return isVillage(level) || isQuestMap(level);
    }
}
