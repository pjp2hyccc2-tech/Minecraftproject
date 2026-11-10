package com.eternalrealm.monster.forge;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

/**
 * Minecraft's own mobs do not appear: every spawn that vanilla starts by itself (natural spawning, chunk generation,
 * structures, patrols, wandering traders, phantoms, zombie reinforcements, jockeys) is cancelled, in every dimension.
 * The mod's monsters never go through vanilla's spawn finalisation ({@link MonsterFactory}), and spawn eggs, spawners
 * and commands still work for operators.
 */
public final class VanillaSpawnBlocker {
    private VanillaSpawnBlocker() {
    }

    /** Whether vanilla starting a spawn of this kind is blocked. */
    public static boolean blocks(MobSpawnType type) {
        return switch (type) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL, EVENT, REINFORCEMENT, JOCKEY -> true;
            default -> false;
        };
    }

    /** Registered on the Forge bus by {@code EternalRealm}. */
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (MonsterTags.isMonster(event.getEntity())) {
            return;
        }
        if (blocks(event.getSpawnType())) {
            event.setSpawnCancelled(true);
        }
    }
}
