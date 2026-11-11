package com.eternalrealm.world.forge.capital;

import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The royal capital as exported by {@code tools/capital} into {@code data/eternalrealm/capital}: a {@link Blueprint}
 * whose local x/z are relative to the capital centre and y to the plaza paving (players stand at y = 1). The format,
 * the loader and the placer ({@link com.eternalrealm.world.forge.blueprint.BlueprintPlacer}) are shared with the
 * estates.
 */
public final class CapitalBlueprint extends Blueprint {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String FOLDER = "capital";
    public static final ResourceLocation MANIFEST = Blueprint.manifest(FOLDER);

    private CapitalBlueprint(Blueprint parsed) {
        super(parsed);
    }

    /** The blueprint shipped with the mod, if any (malformed data is logged and treated as absent). */
    public static Optional<CapitalBlueprint> load(ResourceManager resources) {
        Optional<Resource> manifest = resources.getResource(MANIFEST);
        if (manifest.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = new InputStreamReader(manifest.get().open(), StandardCharsets.UTF_8)) {
            return Optional.of(parse(JsonParser.parseReader(reader).getAsJsonObject()));
        } catch (IOException | RuntimeException e) {
            LOG.error("The capital blueprint {} could not be read; the classic town is used instead", MANIFEST, e);
            return Optional.empty();
        }
    }

    static CapitalBlueprint parse(JsonObject json) {
        return new CapitalBlueprint(Blueprint.parse(json, FOLDER));
    }
}
