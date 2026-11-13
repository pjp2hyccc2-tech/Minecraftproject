package com.eternalrealm.social.phone;

import java.util.HexFormat;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Photos on the 魔導スマホ (pure): the client sends a screenshot made small ({@link #WIDTH} x {@link #HEIGHT} JPEG, at
 * most {@link #MAX_BYTES}) in chunks of {@link #CHUNK} bytes; the server checks it, keeps it under a random id and
 * deletes the oldest once more than {@link #KEEP} are kept.
 */
public final class PhotoRules {
    public static final int WIDTH = 384;
    public static final int HEIGHT = 216;
    public static final int MAX_WIDTH = 640;
    public static final int MAX_HEIGHT = 480;
    public static final int MAX_BYTES = 96 * 1024;
    public static final int CHUNK = 16 * 1024;
    public static final int MAX_CHUNKS = (MAX_BYTES + CHUNK - 1) / CHUNK;
    /** Photos kept on the server at most (the oldest go first). */
    public static final int KEEP = 400;
    private static final Pattern ID = Pattern.compile("[0-9a-f]{16}");

    private PhotoRules() {
    }

    public static String newId(RandomGenerator random) {
        return HexFormat.of().toHexDigits(random.nextLong());
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /** JPEG data starts with the SOI marker and a segment marker. */
    public static boolean looksLikeJpeg(byte[] data) {
        return data != null && data.length > 4 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    public static boolean sizeOk(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_WIDTH && height <= MAX_HEIGHT;
    }

    /**
     * One photo arriving in chunks, in order: {@code total} chunks, each but the last exactly {@link #CHUNK} bytes.
     * Anything else (a chunk out of order, too many, too large) ruins it.
     */
    public static final class Upload {
        private final int id;
        private final int total;
        private final byte[] data;
        private int received;
        private int length;
        private boolean broken;

        public Upload(int id, int total) {
            this.id = id;
            this.total = total;
            this.broken = total < 1 || total > MAX_CHUNKS;
            this.data = new byte[broken ? 0 : total * CHUNK];
        }

        public int id() {
            return id;
        }

        /** @return false when the chunk ruins the upload */
        public boolean accept(int index, byte[] chunk) {
            if (broken || chunk == null || index != received || index >= total) {
                broken = true;
                return false;
            }
            boolean last = index == total - 1;
            if (chunk.length == 0 || chunk.length > CHUNK || (!last && chunk.length != CHUNK)
                    || length + chunk.length > MAX_BYTES) {
                broken = true;
                return false;
            }
            System.arraycopy(chunk, 0, data, length, chunk.length);
            length += chunk.length;
            received++;
            return true;
        }

        public boolean complete() {
            return !broken && received == total;
        }

        public boolean broken() {
            return broken;
        }

        /** The whole photo (once complete). */
        public byte[] bytes() {
            byte[] out = new byte[length];
            System.arraycopy(data, 0, out, 0, length);
            return out;
        }
    }
}
