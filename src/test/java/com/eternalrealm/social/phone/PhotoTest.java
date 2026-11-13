package com.eternalrealm.social.phone;

import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhotoTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    public void idsAreSixteenHexDigits() {
        String id = PhotoRules.newId(new Random(1));
        assertTrue(id, PhotoRules.validId(id));
        assertFalse(PhotoRules.validId("../../etc/passwd"));
        assertFalse(PhotoRules.validId("0123456789ABCDEF"));
        assertFalse(PhotoRules.validId(null));
    }

    @Test
    public void jpegAndSizeChecks() {
        assertTrue(PhotoRules.looksLikeJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}));
        assertFalse(PhotoRules.looksLikeJpeg(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0}));
        assertTrue(PhotoRules.sizeOk(PhotoRules.WIDTH, PhotoRules.HEIGHT));
        assertFalse(PhotoRules.sizeOk(4096, 4096));
        assertFalse(PhotoRules.sizeOk(0, 10));
    }

    @Test
    public void anUploadArrivesInOrderedFullChunks() {
        byte[] photo = new byte[PhotoRules.CHUNK * 2 + 100];
        new Random(2).nextBytes(photo);
        PhotoRules.Upload upload = new PhotoRules.Upload(7, 3);
        assertTrue(upload.accept(0, java.util.Arrays.copyOfRange(photo, 0, PhotoRules.CHUNK)));
        assertFalse(upload.complete());
        assertTrue(upload.accept(1, java.util.Arrays.copyOfRange(photo, PhotoRules.CHUNK, 2 * PhotoRules.CHUNK)));
        assertTrue(upload.accept(2, java.util.Arrays.copyOfRange(photo, 2 * PhotoRules.CHUNK, photo.length)));
        assertTrue(upload.complete());
        assertArrayEquals(photo, upload.bytes());
    }

    @Test
    public void aBadChunkRuinsTheUpload() {
        PhotoRules.Upload skipped = new PhotoRules.Upload(1, 2);
        assertFalse(skipped.accept(1, new byte[10]));
        assertTrue(skipped.broken());
        PhotoRules.Upload shortChunk = new PhotoRules.Upload(1, 2);
        assertFalse("only the last chunk may be short", shortChunk.accept(0, new byte[10]));
        PhotoRules.Upload tooMany = new PhotoRules.Upload(1, PhotoRules.MAX_CHUNKS + 1);
        assertTrue(tooMany.broken());
        PhotoRules.Upload oversized = new PhotoRules.Upload(1, 1);
        assertFalse(oversized.accept(0, new byte[PhotoRules.CHUNK + 1]));
    }

    @Test
    public void theShelfKeepsUsedPhotosAndDropsTheRest() {
        PhotoShelf shelf = new PhotoShelf();
        shelf.add("000000000000000a", A, 0);
        shelf.add("000000000000000b", A, 0);
        shelf.add("000000000000000c", B, 0);
        assertTrue(shelf.usableBy("000000000000000a", A));
        assertFalse(shelf.usableBy("000000000000000a", B));
        shelf.use("000000000000000a");
        shelf.use("000000000000000c");
        assertFalse("used once", shelf.usableBy("000000000000000a", A));
        List<String> gone = shelf.sweep(PhotoShelf.PENDING_MILLIS + 1, Set.of("000000000000000a"));
        assertEquals("never used, and used but deleted since", Set.of("000000000000000b", "000000000000000c"),
                Set.copyOf(gone));
        assertTrue(shelf.has("000000000000000a"));
        assertEquals(1, PhotoShelf.fromJson(shelf.toJson()).size());
    }

    @Test
    public void theShelfMakesRoomByDroppingTheOldest() {
        PhotoShelf shelf = new PhotoShelf();
        for (int i = 0; i < PhotoRules.KEEP; i++) {
            assertTrue(shelf.add(String.format("%016x", i), A, i).isEmpty());
        }
        assertEquals(List.of(String.format("%016x", 0)), shelf.add("ffffffffffffffff", A, 9999));
        assertEquals(PhotoRules.KEEP, shelf.size());
    }
}
