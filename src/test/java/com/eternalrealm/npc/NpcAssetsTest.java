package com.eternalrealm.npc;

import com.eternalrealm.story.StoryNpc;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The generated NPC art (tools/art_entities/generate_npc.py) matches the generated tables without Minecraft: every
 * appearance has its texture at the size of the UV map, glow textures pair with bodies, every speaker of a town NPC
 * and every townsfolk type has a 48x48 dialogue portrait.
 */
public class NpcAssetsTest {
    private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures");
    private static final Path TEXTURES = ASSETS.resolve("entity").resolve("npc");
    private static final Path PORTRAITS = ASSETS.resolve("gui").resolve("portrait");
    private static final Path UV_MAP = Paths.get("tools", "art_entities", "uv_map_npc.txt");

    private static int[] uvSize() throws IOException {
        for (String line : Files.readAllLines(UV_MAP, StandardCharsets.UTF_8)) {
            if (line.startsWith("== npc ")) {
                String[] wh = line.substring("== npc ".length()).trim().split("x");
                return new int[]{Integer.parseInt(wh[0]), Integer.parseInt(wh[1])};
            }
        }
        throw new AssertionError("no texture size in " + UV_MAP);
    }

    private static int[] pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            byte[] signature = new byte[8];
            data.readFully(signature);
            assertEquals("not a PNG: " + png, 'P', signature[1]);
            data.readInt();
            byte[] type = new byte[4];
            data.readFully(type);
            assertEquals("IHDR", new String(type, StandardCharsets.US_ASCII));
            return new int[]{data.readInt(), data.readInt()};
        }
    }

    private static List<String> pngNames(Path dir) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".png"))
                    .forEach(name -> names.add(name.substring(0, name.length() - 4)));
        }
        return names;
    }

    @Test
    public void everyAppearanceHasATextureOfTheGeneratedSize() throws IOException {
        int[] expected = uvSize();
        assertTrue("the default is an appearance", NpcBodies.exists(NpcBodies.DEFAULT));
        assertEquals("the default comes first", NpcBodies.DEFAULT, NpcBodies.APPEARANCES.get(0));
        assertEquals("ids are unique", NpcBodies.APPEARANCES.size(), new HashSet<>(NpcBodies.APPEARANCES).size());
        for (String id : NpcBodies.APPEARANCES) {
            Path body = TEXTURES.resolve(id + ".png");
            assertTrue("missing texture " + body, Files.isRegularFile(body));
            int[] size = pngSize(body);
            assertEquals(id + " width", expected[0], size[0]);
            assertEquals(id + " height", expected[1], size[1]);
        }
    }

    @Test
    public void everyTextureBelongsToAnAppearance() throws IOException {
        for (String name : pngNames(TEXTURES)) {
            String id = name.endsWith("_glow") ? name.substring(0, name.length() - "_glow".length()) : name;
            assertTrue("stale texture " + name, NpcBodies.exists(id));
            if (name.endsWith("_glow")) {
                assertTrue("glow without body: " + name, Files.isRegularFile(TEXTURES.resolve(id + ".png")));
            }
        }
    }

    @Test
    public void everySpeakerOfATownNpcHasAPortrait() throws IOException {
        for (StoryNpc npc : StoryNpc.values()) {
            Path portrait = PORTRAITS.resolve(npc.speaker().id() + ".png");
            assertTrue("no portrait for " + npc.speaker().id() + " (" + npc.id() + ")", Files.isRegularFile(portrait));
            int[] size = pngSize(portrait);
            assertEquals(portrait + " width", 48, size[0]);
            assertEquals(portrait + " height", 48, size[1]);
        }
    }

    @Test
    public void everyAppearanceThatIsNotAColourVariantHasAPortrait() throws IOException {
        Set<String> portraits = new HashSet<>(pngNames(PORTRAITS));
        for (String id : NpcBodies.APPEARANCES) {
            boolean variant = Character.isDigit(id.charAt(id.length() - 1));
            assertEquals("portrait of " + id, !variant, portraits.contains(id));
        }
    }

    @Test
    public void scalesAndTalkGesturesAreSane() {
        for (String id : NpcBodies.APPEARANCES) {
            float scale = NpcBodies.scale(id);
            assertTrue(id + " scale " + scale, scale >= 0.6F && scale <= 1.2F);
            float talk = NpcBodies.talkSeconds(id);
            assertTrue(id + " talk " + talk, talk >= 1.0F && talk <= 5.0F);
        }
        assertEquals(1.0F, NpcBodies.scale("nobody"), 0.0F);
        assertEquals(1.0F, NpcBodies.scale(null), 0.0F);
        assertEquals(3.0F, NpcBodies.talkSeconds(null), 0.0F);
        assertEquals(NpcBodies.DEFAULT, NpcBodies.orDefault("nobody"));
        assertEquals(NpcBodies.DEFAULT, NpcBodies.orDefault(null));
        assertEquals("mina", NpcBodies.orDefault("mina"));
        assertFalse(NpcBodies.exists(""));
    }
}
