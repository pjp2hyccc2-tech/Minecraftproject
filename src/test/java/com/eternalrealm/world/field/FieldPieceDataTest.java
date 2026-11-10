package com.eternalrealm.world.field;

import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FieldPieceDataTest {
    private static final Path EXPORT = Paths.get("src", "main", "resources", "data", "eternalrealm", "fieldpieces");

    // ------------------------------------------------------------------------------------------- the format

    /** A tiny piece: chunk (2, -1), two columns written (a stone pillar with a foundation, an air pocket). */
    private static byte[] tinyPiece() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(new byte[]{'E', 'R', 'P', '1'});
        varint(raw, 1);
        int32(raw, 2);
        int32(raw, -1);
        int32(raw, 60);
        varint(raw, 4);
        for (int column = 0; column < 256; column++) {
            if (column == 0) {                   // local (0, 0): keep, stone x3 -> foundation stone
                varint(raw, 2);
                varint(raw, 2);
                varint(raw, 1);
                varint(raw, 0);
                varint(raw, 3);
                varint(raw, 2);
            } else if (column == 17) {           // local (1, 1): air x4
                varint(raw, 1);
                varint(raw, 0);
                varint(raw, 4);
                varint(raw, 1);
            } else if (column == 34) {           // local (2, 2): only "keep": touched but writes nothing
                varint(raw, 1);
                varint(raw, 0);
                varint(raw, 4);
                varint(raw, 0);
            } else {
                varint(raw, 0);
            }
        }
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(zipped)) {
            gz.write(raw.toByteArray());
        }
        return zipped.toByteArray();
    }

    private static FieldPieceData tinyData() throws IOException {
        JsonObject manifest = JsonParser.parseString("{\"format\":1,\"build\":\"t\",\"pieces\":[{\"id\":\"tiny\","
                + "\"field\":1,\"box\":[32,60,-16,33,63,-15],\"file\":\"pieces/tiny.bin\",\"palette\":"
                + "[\"eternalrealm:void\",\"minecraft:air\",\"minecraft:stone\"],\"blockEntities\":[],"
                + "\"probes\":[{\"pos\":[32,61,-16],\"state\":\"minecraft:stone\"}],\"samples\":[[32,-16,59]]}],"
                + "\"waystones\":[{\"id\":\"w\",\"pos\":[40,64,-10],\"facing\":\"south\"}]}").getAsJsonObject();
        byte[] piece = tinyPiece();
        return FieldPieceData.read(manifest, name -> {
            assertEquals("pieces/tiny.bin", name);
            return new ByteArrayInputStream(piece);
        });
    }

    @Test
    public void tilesAreIndexedByChunkAndDecodeColumnByColumn() throws IOException {
        FieldPieceData data = tinyData();
        assertEquals(1, data.pieces().size());
        assertEquals(1, data.chunkCount());
        assertTrue(data.tilesAt(0, 0).isEmpty());
        List<FieldPieceData.TileRef> refs = data.tilesAt(2, -1);
        assertEquals(1, refs.size());
        FieldPieceData.Tile tile = refs.get(0).decode();
        assertEquals(60, tile.minY());
        assertEquals(4, tile.height());
        assertEquals(0, tile.at(0, 60));
        assertEquals(2, tile.at(0, 61));
        assertEquals(2, tile.at(0, 63));
        assertEquals(0, tile.at(0, 64));
        assertEquals(2, tile.foundation()[0]);
        assertEquals(1, tile.at(17, 60));
        assertTrue(tile.touched()[34]);
        assertFalse(tile.touched()[1]);
    }

    @Test
    public void coversAndNearFollowTheWrittenColumns() throws IOException {
        FieldPieceData data = tinyData();
        assertTrue(data.covers(32, -16));
        assertTrue(data.covers(33, -15));
        assertFalse("a column of only 'keep' is not covered", data.covers(34, -14));
        assertFalse(data.covers(35, -16));
        assertFalse(data.covers(32, -32));
        assertTrue(data.near(40, -16, 8));
        assertFalse(data.near(41, -16, 7));
        assertTrue(data.near(20, -20, 12));
        assertFalse(data.near(0, 0, 8));
        assertNotNull(data.waystoneNear(42, -8, 16));
        assertNull(data.waystoneNear(80, -8, 16));
    }

    @Test
    public void malformedDataIsRejected() throws IOException {
        JsonObject manifest = JsonParser.parseString("{\"format\":1,\"pieces\":[{\"id\":\"bad\",\"box\":[0,0,0,0,0,0],"
                + "\"file\":\"x\",\"palette\":[\"minecraft:air\"]}]}").getAsJsonObject();
        try {
            FieldPieceData.read(manifest, name -> new ByteArrayInputStream(tinyPiece()));
            fail("a palette without void/air must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        assertTrue(FieldPieceData.empty().isEmpty());
        assertFalse(FieldPieceData.empty().covers(0, 0));
    }

    // ------------------------------------------------------------------------------------------- the real export

    private static FieldPieceData export() throws IOException {
        Path manifest = EXPORT.resolve("manifest.json");
        assertTrue("no field piece export at " + manifest.toAbsolutePath(), Files.exists(manifest));
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return FieldPieceData.read(json, name -> {
                InputStream in = Files.newInputStream(EXPORT.resolve(name));
                return in;
            });
        }
    }

    @Test
    public void theExportDecodesCompletely() throws IOException {
        FieldPieceData data = export();
        assertTrue(data.pieces().size() >= 11);
        Set<String> ids = new HashSet<>();
        for (FieldPieceData.Piece piece : data.pieces()) {
            assertTrue("duplicate piece " + piece.id(), ids.add(piece.id()));
            assertTrue(piece.id() + " has no tiles", piece.tileCount() > 0);
            for (int t = 0; t < piece.tileCount(); t++) {
                FieldPieceData.Tile tile = piece.tile(t);
                assertTrue(tile.minY() >= -64 && tile.minY() + tile.height() <= 320);
            }
        }
    }

    @Test
    public void everyWaystoneHasItsShrine() throws IOException {
        FieldPieceData data = export();
        for (FieldLayout.Waystone stone : FieldLayout.waystones()) {
            FieldPieceData.Waystone placed = data.waystoneNear(stone.x(), stone.z(), 3);
            assertNotNull("no waystone placed within 3 blocks of " + stone.id(), placed);
            assertEquals(stone.id(), placed.id());
            assertTrue(stone.id() + " shrine does not cover its stone", data.covers(placed.x(), placed.z()));
        }
        assertEquals(FieldLayout.waystones().size(), data.waystones().size());
    }

    /** The pieces were fitted to the terrain: a changed FieldTerrain needs {@code python3 tools/fields/build.py}. */
    @Test
    public void theTerrainIsTheOneThePiecesWereBuiltOn() throws IOException {
        FieldPieceData data = export();
        StringBuilder drift = new StringBuilder();
        for (FieldPieceData.Piece piece : data.pieces()) {
            for (FieldPieceData.Sample sample : piece.samples()) {
                int now = (int) Math.floor(FieldTerrain.heightAt(sample.x(), sample.z()));
                if (now != sample.top()) {
                    drift.append("\n  ").append(piece.id()).append(" at ").append(sample.x()).append(", ")
                            .append(sample.z()).append(": built on ").append(sample.top()).append(", terrain now ")
                            .append(now);
                }
            }
        }
        assertEquals("FieldTerrain changed under the field set pieces; rebuild them with tools/fields/build.py:"
                + drift, 0, drift.length());
    }

    @Test
    public void piecesStayInsideTheWorldAndOutOfTheCapitalAndTheDungeonGates() throws IOException {
        FieldPieceData data = export();
        for (FieldPieceData.Piece piece : data.pieces()) {
            int[] b = piece.box();
            assertTrue(piece.id() + " outside the world", FieldLayout.inBounds(b[0], b[2]) && FieldLayout.inBounds(b[3], b[5]));
            for (int x = b[0]; x <= b[3]; x++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    if (!data.covers(x, z)) {
                        continue;
                    }
                    assertFalse(piece.id() + " builds inside the capital at " + x + ", " + z,
                            FieldLayout.capitalDistance(x, z) < 8.0);
                    for (LandmarkSite site : LandmarkSites.all()) {
                        double keepOut = site.dungeonId().equals("d8") ? 30.0 : 20.0;
                        assertTrue(piece.id() + " builds at " + x + ", " + z + " next to the gate of " + site.dungeonId(),
                                Math.hypot(x - site.x(), z - site.z()) > keepOut);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------- encoding helpers

    private static void varint(ByteArrayOutputStream out, int value) {
        while (true) {
            int b = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                return;
            }
        }
    }

    private static void int32(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
