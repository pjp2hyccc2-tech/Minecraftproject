package com.eternalrealm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the mod resources that only fail at runtime inside Minecraft.
 *
 * <p>Found by the CI GameTest server: without {@code pack.mcmeta} Forge 47.x rejects the mod's pack ("Missing metadata
 * in pack mod:eternalrealm"), so translations, the GameTest structure and all future data silently disappear.</p>
 */
public class ResourcesTest {
    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    @Test
    public void packMetadataExistsWithTheFormatOfMinecraft1201() throws IOException {
        Path packMeta = RESOURCES.resolve("pack.mcmeta");
        assertTrue("pack.mcmeta is required by Forge 47.x", Files.isRegularFile(packMeta));
        JsonObject pack = JsonParser.parseString(Files.readString(packMeta, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("pack");
        assertEquals("1.20.1 uses pack format 15 for resource and data packs", 15, pack.get("pack_format").getAsInt());
        assertTrue(pack.has("description"));
    }

    @Test
    public void gameTestStructureIsPresent() {
        assertTrue(Files.isRegularFile(RESOURCES.resolve("data/eternalrealm/structures/empty.nbt")));
    }

    @Test
    public void englishAndJapaneseDefineTheSameKeys() throws IOException {
        Set<String> english = keys(RESOURCES.resolve("assets/eternalrealm/lang/en_us.json"));
        Set<String> japanese = keys(RESOURCES.resolve("assets/eternalrealm/lang/ja_jp.json"));
        assertTrue("no translations found", english.size() > 20);
        assertEquals(english, japanese);
    }

    private static Set<String> keys(Path langFile) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(langFile, StandardCharsets.UTF_8));
        return new TreeSet<>(root.getAsJsonObject().keySet());
    }
}
