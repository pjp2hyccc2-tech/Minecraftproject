package com.eternalrealm.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Lights the glowing outline of an entity on this client only (the outline the game draws for glowing entities, white
 * for entities without a team). Reaches {@link Entity}'s protected shared flags as a subclass; never instantiated.
 */
abstract class GlowAccess extends Entity {
    private static final int GLOWING_FLAG = 6;

    private GlowAccess(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Whether the flags say the entity glows (as the server last sent them, unless this client changed them). */
    static boolean isGlowing(Entity entity) {
        return (entity.getEntityData().get(DATA_SHARED_FLAGS_ID) & 1 << GLOWING_FLAG) != 0;
    }

    /** Sets the glowing flag on this client; the server's next update of the entity's flags overrides it. */
    static void setGlowing(Entity entity, boolean glowing) {
        byte flags = entity.getEntityData().get(DATA_SHARED_FLAGS_ID);
        byte next = (byte) (glowing ? flags | 1 << GLOWING_FLAG : flags & ~(1 << GLOWING_FLAG));
        if (next != flags) {
            entity.getEntityData().set(DATA_SHARED_FLAGS_ID, next);
        }
    }
}
