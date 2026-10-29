package com.huntcraft.quest;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.HCDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The three quest maps (each one is a dimension; quests run in 448x448 regions of it). */
public enum QuestMap {
    VERDANT_HILLS("verdant_hills", "verdant", HCDimensions.VERDANT_HILLS, Climate.TEMPERATE, 0xFF6FBF4A,
            List.of(new Spawn("jagraptor", 30, 3, 5), new Spawn("grasica", 25, 2, 4), new Spawn("boarfang", 15, 1, 3),
                    new Spawn("buzzling", 15, 2, 4), new Spawn("gamenos", 8, 1, 2), new Spawn("nussutto_mofu", 5, 1, 1))),
    FROSTPEAK_RANGE("frostpeak_range", "frostpeak", HCDimensions.FROSTPEAK_RANGE, Climate.COLD, 0xFF9FD8F0,
            List.of(new Spawn("blizraptor", 35, 3, 5), new Spawn("yukimammo", 22, 1, 2), new Spawn("boarfang", 15, 1, 3),
                    new Spawn("nussutto_mofu", 5, 1, 1))),
    EMBER_CALDERA("ember_caldera", "ember", HCDimensions.EMBER_CALDERA, Climate.HOT, 0xFFE0602A,
            List.of(new Spawn("heatgecko", 35, 2, 4), new Spawn("gansekigani", 28, 1, 3), new Spawn("gamenos", 12, 1, 2),
                    new Spawn("nussutto_mofu", 5, 1, 1)));

    public enum Climate { TEMPERATE, COLD, HOT }

    /** Small monster group spawn entry (monster registry id, weight, group size). */
    public record Spawn(String id, int weight, int min, int max) {
    }

    public final String id;
    public final String shortName;
    public final ResourceKey<Level> dimension;
    public final Climate climate;
    public final int color;
    public final List<Spawn> spawns;

    QuestMap(String id, String shortName, ResourceKey<Level> dimension, Climate climate, int color, List<Spawn> spawns) {
        this.id = id;
        this.shortName = shortName;
        this.dimension = dimension;
        this.climate = climate;
        this.color = color;
        this.spawns = spawns;
    }

    public String nameKey() {
        return "questmap.huntcraft." + id;
    }

    public Component displayName() {
        return Component.translatable(nameKey());
    }

    public ResourceLocation campTemplate() {
        return HuntCraft.id("camp_" + shortName);
    }

    /** Top block of the flattened camp pad. */
    public BlockState padTop() {
        return switch (this) {
            case VERDANT_HILLS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case FROSTPEAK_RANGE -> Blocks.SNOW_BLOCK.defaultBlockState();
            case EMBER_CALDERA -> Blocks.SMOOTH_BASALT.defaultBlockState();
        };
    }

    /** Fill block below the camp pad. */
    public BlockState padFill() {
        return switch (this) {
            case VERDANT_HILLS -> Blocks.DIRT.defaultBlockState();
            case FROSTPEAK_RANGE -> Blocks.STONE.defaultBlockState();
            case EMBER_CALDERA -> Blocks.BLACKSTONE.defaultBlockState();
        };
    }

    @Nullable
    public static QuestMap byId(String id) {
        for (QuestMap m : values()) if (m.id.equals(id)) return m;
        return null;
    }

    @Nullable
    public static QuestMap of(@Nullable Level level) {
        return level == null ? null : of(level.dimension());
    }

    @Nullable
    public static QuestMap of(ResourceKey<Level> dim) {
        for (QuestMap m : values()) if (m.dimension == dim) return m;
        return null;
    }
}
