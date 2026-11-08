package com.eternalrealm.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The generated audio (tools/audio/generate_all.py) and its metadata must agree: every sound event of ERSounds has a
 * sounds.json entry whose OGG files exist (and no file is unused), subtitles have Japanese and English text, music is
 * streamed stereo, sound effects are mono (Minecraft only positions mono sounds) and the size budgets hold.
 */
public class SoundAssetsTest {
    private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "eternalrealm");
    private static final Path SOUNDS = ASSETS.resolve("sounds");
    private static final Path SOUNDS_JSON = ASSETS.resolve("sounds.json");
    private static final Path I18N = Paths.get("docs", "i18n", "audio.json");
    private static final Path ERSOUNDS = Paths.get("src", "main", "java", "com", "eternalrealm", "sound", "ERSounds.java");
    private static final long MUSIC_BUDGET = 12_000_000L;
    private static final long SFX_BUDGET = 2_000_000L;

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** sound file name ("eternalrealm:sfx/ui/click") per entry of an event. */
    private static List<String> files(JsonObject event) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : event.getAsJsonArray("sounds")) {
            names.add(element.isJsonObject() ? element.getAsJsonObject().get("name").getAsString() : element.getAsString());
        }
        return names;
    }

    private static Path fileOf(String name) {
        assertTrue(name, name.startsWith("eternalrealm:"));
        return SOUNDS.resolve(name.substring("eternalrealm:".length()) + ".ogg");
    }

    @Test
    public void everyRegisteredSoundHasAnEntryAndNothingIsStale() throws IOException {
        Matcher matcher = Pattern.compile("\\bsound\\(\"([a-z0-9_.]+)\"\\)").matcher(Files.readString(ERSOUNDS, StandardCharsets.UTF_8));
        Set<String> registered = new TreeSet<>();
        while (matcher.find()) {
            assertTrue("registered twice: " + matcher.group(1), registered.add(matcher.group(1)));
        }
        assertFalse(registered.isEmpty());
        assertEquals(registered, new TreeSet<>(read(SOUNDS_JSON).keySet()));
    }

    @Test
    public void everyFileExistsAndIsUsed() throws IOException {
        Set<Path> referenced = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : read(SOUNDS_JSON).entrySet()) {
            for (String name : files(entry.getValue().getAsJsonObject())) {
                Path file = fileOf(name);
                assertTrue(entry.getKey() + ": missing " + file, Files.isRegularFile(file));
                referenced.add(file.normalize());
            }
        }
        Set<Path> present = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(SOUNDS)) {
            walk.filter(p -> p.toString().endsWith(".ogg")).forEach(p -> present.add(p.normalize()));
        }
        assertEquals(referenced, present);
    }

    @Test
    public void musicIsStreamedStereoAndEffectsAreMono() throws IOException {
        for (Map.Entry<String, JsonElement> entry : read(SOUNDS_JSON).entrySet()) {
            String id = entry.getKey();
            boolean music = id.startsWith("music.") || id.startsWith("jingle.");
            for (JsonElement element : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                boolean stream = element.isJsonObject() && element.getAsJsonObject().has("stream")
                        && element.getAsJsonObject().get("stream").getAsBoolean();
                assertEquals(id + " stream", music, stream);
                String name = element.isJsonObject() ? element.getAsJsonObject().get("name").getAsString() : element.getAsString();
                int[] header = vorbisHeader(fileOf(name));
                assertEquals(id + " channels", music ? 2 : 1, header[0]);
                assertEquals(id + " sample rate", 44100, header[1]);
            }
        }
    }

    /** {channels, sample rate} from the Vorbis identification header in the first Ogg page. */
    private static int[] vorbisHeader(Path file) throws IOException {
        byte[] head = new byte[128];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            assertTrue(file + " too short", read >= 64);
        }
        assertEquals(file + " is not Ogg", "OggS", new String(head, 0, 4, StandardCharsets.US_ASCII));
        int packet = 27 + (head[26] & 0xFF);
        assertEquals(file + " first packet is not the identification header", 1, head[packet]);
        assertEquals("vorbis", new String(head, packet + 1, 6, StandardCharsets.US_ASCII));
        int channels = head[packet + 11] & 0xFF;
        int rate = (head[packet + 12] & 0xFF) | (head[packet + 13] & 0xFF) << 8 | (head[packet + 14] & 0xFF) << 16
                | (head[packet + 15] & 0xFF) << 24;
        return new int[]{channels, rate};
    }

    @Test
    public void sizesStayWithinBudget() throws IOException {
        Map<String, Long> totals = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(SOUNDS)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".ogg"))::iterator) {
                String top = SOUNDS.relativize(p).getName(0).toString();
                totals.merge(top.equals("sfx") ? "sfx" : "music", Files.size(p), Long::sum);
            }
        }
        assertTrue("music and jingles: " + totals.get("music") + " bytes", totals.getOrDefault("music", 0L) <= MUSIC_BUDGET);
        assertTrue("sound effects: " + totals.get("sfx") + " bytes", totals.getOrDefault("sfx", 0L) <= SFX_BUDGET);
    }

    @Test
    public void subtitlesHaveJapaneseAndEnglish() throws IOException {
        JsonObject texts = read(I18N);
        Set<String> used = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : read(SOUNDS_JSON).entrySet()) {
            JsonObject event = entry.getValue().getAsJsonObject();
            if (entry.getKey().startsWith("music.")) {
                assertFalse(entry.getKey() + ": music has no subtitle", event.has("subtitle"));
                continue;
            }
            if (event.has("subtitle")) {
                String key = event.get("subtitle").getAsString();
                assertEquals("subtitles.eternalrealm." + entry.getKey(), key);
                used.add(key);
            }
        }
        assertEquals(used, new TreeSet<>(texts.keySet()));
        for (Map.Entry<String, JsonElement> entry : texts.entrySet()) {
            JsonObject text = entry.getValue().getAsJsonObject();
            assertEquals(entry.getKey(), Set.of("ja", "en"), text.keySet());
            for (String language : Set.of("ja", "en")) {
                assertFalse(entry.getKey() + " " + language, text.get(language).getAsString().isBlank());
            }
        }
    }
}
