package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    /** A void dimension that holds every player's clock repair workshop. */
    public static final ResourceKey<Level> WORKSHOP = ResourceKey.create(Registries.DIMENSION, ClockworkDiver.id("workshop"));
    /** A void dimension where the giant clock interiors are built while a player is shrunk down. */
    public static final ResourceKey<Level> INTERIOR = ResourceKey.create(Registries.DIMENSION, ClockworkDiver.id("clock_interior"));

    private ModDimensions() {}

    public static boolean isWorkshop(Level level) {
        return level != null && level.dimension() == WORKSHOP;
    }

    public static boolean isInterior(Level level) {
        return level != null && level.dimension() == INTERIOR;
    }

    public static boolean isModDimension(Level level) {
        return isWorkshop(level) || isInterior(level);
    }
}
