package com.frostbound.apiprobe;

import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Compile-only canary for the BUILD 0 design.
 *
 * <p>Every Minecraft 1.20.1 / Forge 47 API the design document relies on is referenced here, so CI fails
 * if one of them does not exist with the expected signature. This source set is never loaded at runtime
 * and is not packaged into the mod jar.
 */
final class Forge1201ApiProbe {
    private Forge1201ApiProbe() {
    }

    /** Dimension, terrain shaping and world type (design: dedicated dimension). */
    static Object[] worldgenRegistries() {
        return new Object[] {
            Registries.DENSITY_FUNCTION_TYPE, Registries.BIOME_SOURCE, Registries.CHUNK_GENERATOR,
            Registries.WORLD_PRESET, Registries.DIMENSION_TYPE, Registries.LEVEL_STEM
        };
    }

    static Class<?> densityFunctionCodecType() {
        return KeyDispatchDataCodec.class;
    }

    /** Vanilla rules the game takes over in FROSTBOUND worlds. */
    static Object[] gameRules() {
        return new Object[] {
            GameRules.RULE_DAYLIGHT, GameRules.RULE_WEATHER_CYCLE, GameRules.RULE_DOMOBSPAWNING,
            GameRules.RULE_SNOW_ACCUMULATION_HEIGHT
        };
    }

    /** Time and weather are written into the overworld; derived dimensions follow it. */
    static void timeAndWeather(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        overworld.setDayTime(0L);
        overworld.setWeatherParameters(0, 6000, true, false);
    }

    /** Smooth sky on the client between server time packets. */
    static void smoothClientTime(ClientLevel level) {
        level.setDayTime(0L);
    }

    /** Moving players into the dimension and pinning their respawn to the plaza. */
    static void placePlayer(ServerPlayer player, ServerLevel level, ResourceKey<Level> dimension, BlockPos plaza) {
        player.teleportTo(level, plaza.getX() + 0.5, plaza.getY(), plaza.getZ() + 0.5, 0f, 0f);
        player.setRespawnPosition(dimension, plaza, 0f, true, false);
        player.setTicksFrozen(0);
    }

    /** Citizens are never written to chunks (design: lightweight citizens). */
    static void citizenType(EntityType.Builder<?> builder) {
        builder.noSave();
    }

    /** Management camera (design: first person / top-down switching). */
    static void camera(Minecraft minecraft, Entity cameraEntity) {
        minecraft.setCameraEntity(cameraEntity);
    }

    static Class<?>[] clientEvents() {
        return new Class<?>[] {
            RenderHandEvent.class, RenderHighlightEvent.Block.class, ViewportEvent.ComputeFov.class,
            ViewportEvent.RenderFog.class, ViewportEvent.ComputeFogColor.class, RenderGuiOverlayEvent.Pre.class,
            RegisterGuiOverlaysEvent.class, RenderLevelStageEvent.class, RegisterDimensionSpecialEffectsEvent.class
        };
    }

    static Object hotbarOverlayId() {
        return VanillaGuiOverlay.HOTBAR.id();
    }

    /** Nine-slice metal panels for the HUD and screens. */
    static void panel(GuiGraphics graphics, ResourceLocation texture) {
        graphics.blitNineSliced(texture, 0, 0, 100, 40, 4, 4, 32, 32, 0, 0);
    }

    static Class<?>[] serverEvents() {
        return new Class<?>[] {BlockEvent.PortalSpawnEvent.class, BlockEvent.BreakEvent.class, AddReloadListenerEvent.class};
    }

    /** Crash-safe saving overrides SavedData#save(File) (design: save data). */
    static final class ProbeData extends SavedData {
        @Override
        public CompoundTag save(CompoundTag tag) {
            return tag;
        }

        @Override
        public void save(File file) {
            super.save(file);
        }
    }

    static <T extends SavedData> T storage(ServerLevel level, Function<CompoundTag, T> load, Supplier<T> create) {
        return level.getDataStorage().computeIfAbsent(load, create, "frostbound_probe");
    }
}
