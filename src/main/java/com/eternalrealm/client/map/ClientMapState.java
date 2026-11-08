package com.eternalrealm.client.map;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.world.map.MapView;
import com.google.gson.JsonObject;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * The latest {@link MapView} from the server ({@code map} UI channel), decoded once per document. Client thread only.
 */
public final class ClientMapState {
    /** The dungeon instance dimension ({@code dungeon.forge.DungeonLevels#DUNGEONS}). */
    public static final String DUNGEON_DIMENSION = "eternalrealm:dungeons";
    private static JsonObject decoded;
    private static MapView view = MapView.EMPTY;

    private ClientMapState() {
    }

    /** The current map data (decoded again only when a new document arrived). */
    public static MapView view() {
        Optional<JsonObject> json = ClientUiState.get(MapView.CHANNEL);
        if (json.isEmpty()) {
            decoded = null;
            view = MapView.EMPTY;
        } else if (json.get() != decoded) {
            decoded = json.get();
            view = MapView.fromJson(decoded);
        }
        return view;
    }

    public static void clear() {
        decoded = null;
        view = MapView.EMPTY;
    }

    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }

    /** The level is the realm the areas are in (アストリア, or the overworld on servers without it). */
    public static boolean inRealm(Level level) {
        return dimensionOf(level).equals(view().realm().dimension());
    }

    /** The level is アストリア with the pure layout's biomes and dungeon entrances. */
    public static boolean inAstoria(Level level) {
        return inRealm(level) && view().realm().astoria();
    }

    public static boolean inDungeonDimension(Level level) {
        return DUNGEON_DIMENSION.equals(dimensionOf(level));
    }
}
