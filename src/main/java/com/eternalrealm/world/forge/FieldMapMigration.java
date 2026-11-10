package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.FieldLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Moves worlds onto the current field map. アストリア's land is fixed by {@link FieldLayout}; when a world was created
 * with an older map (the concentric rings were version 1), its アストリア folder is cleared before the levels load so the
 * realm generates afresh (characters, items, quests and season progress live elsewhere and are kept). Players who
 * logged out inside the old land are sent to the capital when they come back.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID)
public final class FieldMapMigration {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "eternalrealm_field_map.txt";
    private static final String PLAYER_TAG = "eternalrealm_field_map";
    private static final String UPGRADED = "upgraded";
    /** This world was moved from an older map: players saved in the old land must be relocated. */
    private static volatile boolean upgradedWorld;

    private FieldMapMigration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path marker = root.resolve(MARKER);
        String recorded = readMarker(marker);
        int version = parseVersion(recorded);
        upgradedWorld = recorded.contains(UPGRADED);
        if (version >= FieldLayout.VERSION) {
            return;
        }
        Path astoria = DimensionType.getStorageFolder(AstoriaDimension.LEVEL, root);
        if (Files.isDirectory(astoria)) {
            upgradedWorld = true;
            LOG.warn("アストリア was generated with field map version {}; regenerating it with version {} (characters "
                    + "and progress are kept)", version, FieldLayout.VERSION);
            for (String folder : new String[]{"region", "entities", "poi", "data"}) {
                deleteTree(astoria.resolve(folder));
            }
        }
        try {
            Files.writeString(marker, FieldLayout.VERSION + (upgradedWorld ? " " + UPGRADED : ""),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Could not record the field map version in {}", marker, e);
        }
    }

    /** Players who were standing in the old land come back at the capital. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getPersistentData().getInt(PLAYER_TAG) >= FieldLayout.VERSION) {
            return;
        }
        player.getPersistentData().putInt(PLAYER_TAG, FieldLayout.VERSION);
        if (upgradedWorld && player.level().dimension().equals(AstoriaDimension.LEVEL)) {
            AstoriaTravel.sendToHub(player);
        }
    }

    private static String readMarker(Path marker) {
        try {
            return Files.exists(marker) ? Files.readString(marker, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static int parseVersion(String recorded) {
        try {
            return recorded.isEmpty() ? 0 : Integer.parseInt(recorded.split(" ")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteTree(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    LOG.warn("Could not delete {}", p, e);
                }
            });
        } catch (IOException e) {
            LOG.warn("Could not clear {}", path, e);
        }
    }
}
