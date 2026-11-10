package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.Dungeons;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a dungeon sounds and feels like around the player, played by the client only (no network traffic, so it costs
 * a full party nothing on the server): a few particles drifting near each player and, now and then, a quiet sound.
 *
 * @param motes          particles, picked by weight
 * @param motesPerSecond particles spawned per second around the player (at full particle settings)
 * @param sounds         sound event ids, one picked at random
 * @param soundEvery     average ticks between two sounds (the client varies it by up to half either way)
 * @param volume         sound volume
 */
public record DungeonAmbience(List<Mote> motes, int motesPerSecond, List<String> sounds, int soundEvery,
                              float volume) {
    /**
     * A particle: {@code particle} is a particle type id, or {@code minecraft:falling_dust/<block id>} for falling dust
     * of a block; {@code drift} is its velocity: -1 sinking, 0 floating, 1 rising.
     */
    public record Mote(String particle, int weight, int drift) {
        public Mote {
            Objects.requireNonNull(particle, "particle");
        }
    }

    public DungeonAmbience {
        motes = List.copyOf(motes);
        sounds = List.copyOf(sounds);
    }

    /** The ambience of {@code theme}. */
    public static DungeonAmbience of(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> new DungeonAmbience(List.of(new Mote("minecraft:white_ash", 6, 0),
                    new Mote("minecraft:dripping_water", 1, -1)), 10,
                    List.of("minecraft:ambient.cave", "minecraft:block.pointed_dripstone.drip_water",
                            "minecraft:block.chain.step"), 240, 0.35F);
            case FOREST -> new DungeonAmbience(List.of(new Mote("minecraft:spore_blossom_air", 5, 0),
                    new Mote("minecraft:glow", 2, 0), new Mote("minecraft:falling_spore_blossom", 1, -1)), 10,
                    List.of("minecraft:block.azalea_leaves.step", "minecraft:block.hanging_roots.step",
                            "minecraft:ambient.cave"), 200, 0.4F);
            case DESERT_TEMPLE -> new DungeonAmbience(List.of(new Mote("minecraft:falling_dust/minecraft:sand", 4, -1),
                    new Mote("minecraft:white_ash", 3, 0)), 8,
                    List.of("minecraft:block.sand.fall", "minecraft:block.suspicious_sand.step",
                            "minecraft:ambient.cave"), 260, 0.35F);
            case ICE_PRISON -> new DungeonAmbience(List.of(new Mote("minecraft:snowflake", 5, -1),
                    new Mote("minecraft:white_ash", 2, 0)), 10,
                    List.of("minecraft:block.chain.step", "minecraft:block.chain.place", "minecraft:ambient.cave"),
                    200, 0.35F);
            case ICE_CASTLE -> new DungeonAmbience(List.of(new Mote("minecraft:snowflake", 6, -1),
                    new Mote("minecraft:end_rod", 1, 0)), 12,
                    List.of("minecraft:block.amethyst_block.chime", "minecraft:block.amethyst_cluster.step"), 160,
                    0.45F);
            case VOLCANO -> new DungeonAmbience(List.of(new Mote("minecraft:small_flame", 3, 1),
                    new Mote("minecraft:ash", 4, -1), new Mote("minecraft:lava", 1, 1)), 12,
                    List.of("minecraft:block.lava.pop", "minecraft:block.lava.ambient",
                            "minecraft:ambient.basalt_deltas.additions"), 160, 0.4F);
            case SKY_RUINS -> new DungeonAmbience(List.of(new Mote("minecraft:end_rod", 3, 0),
                    new Mote("minecraft:white_ash", 4, 0), new Mote("minecraft:electric_spark", 1, 0)), 10,
                    List.of("minecraft:block.beacon.ambient", "minecraft:block.conduit.ambient.short",
                            "minecraft:block.piston.contract"), 220, 0.3F);
            case DARK_TOWER -> new DungeonAmbience(List.of(new Mote("minecraft:reverse_portal", 4, 1),
                    new Mote("minecraft:portal", 3, 0), new Mote("minecraft:witch", 1, 1)), 12,
                    List.of("minecraft:ambient.soul_sand_valley.mood", "minecraft:block.respawn_anchor.ambient",
                            "minecraft:ambient.soul_sand_valley.additions"), 220, 0.35F);
            case END_THRONE -> new DungeonAmbience(List.of(new Mote("minecraft:reverse_portal", 4, 1),
                    new Mote("minecraft:end_rod", 2, 0), new Mote("minecraft:dragon_breath", 1, 1)), 14,
                    List.of("minecraft:block.beacon.ambient", "minecraft:block.respawn_anchor.ambient",
                            "minecraft:block.end_portal_frame.fill"), 260, 0.3F);
        };
    }

    /** The theme of an instance's content id (a dungeon, or the final raid). */
    public static Optional<DungeonTheme> themeOf(String contentId) {
        if (Dungeons.FINAL_RAID.id().equals(contentId)) {
            return Optional.of(Dungeons.FINAL_RAID.theme());
        }
        return Dungeons.byId(contentId).map(DungeonDefinition::theme);
    }
}
