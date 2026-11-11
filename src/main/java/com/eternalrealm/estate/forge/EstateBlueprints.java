package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The estate designs shipped with the mod: {@code data/eternalrealm/estates/<kind>/<design>/manifest.json} and its
 * tiles, made with the capital's pipeline ({@code tools/estates}). Loaded once per resource reload. Server thread.
 */
public final class EstateBlueprints {
    private static final String ROOT = "estates";
    private static ResourceManager cachedFor;
    private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();

    private EstateBlueprints() {
    }

    /** The blueprint of a design, if the mod has it. */
    public static Optional<Blueprint> get(MinecraftServer server, String kind, String design) {
        if (!EstateKey.isName(kind) || !EstateKey.isName(design)) {
            return Optional.empty();
        }
        ResourceManager resources = server.getResourceManager();
        if (cachedFor != resources) {
            cachedFor = resources;
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(kind + "/" + design,
                id -> Blueprint.load(resources, EstateKey.blueprintFolder(kind, design)));
    }

    public static Optional<Blueprint> get(MinecraftServer server, EstateKey key) {
        return get(server, key.kind(), key.design());
    }

    /** Every design the mod ships, by kind (both sorted by name). */
    public static Map<String, List<String>> designs(ResourceManager resources) {
        Map<String, List<String>> found = new TreeMap<>();
        for (ResourceLocation location : resources.listResources(ROOT,
                path -> path.getPath().endsWith("/manifest.json")).keySet()) {
            if (!ModInfo.MOD_ID.equals(location.getNamespace())) {
                continue;
            }
            String[] parts = location.getPath().split("/");
            if (parts.length == 4 && EstateKey.isName(parts[1]) && EstateKey.isName(parts[2])) {
                found.computeIfAbsent(parts[1], kind -> new ArrayList<>()).add(parts[2]);
            }
        }
        found.values().forEach(list -> list.sort(String::compareTo));
        return found;
    }
}
