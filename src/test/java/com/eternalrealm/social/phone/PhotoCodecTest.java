package com.eternalrealm.social.phone;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PhotoCodecTest {
    /** A busy picture (worst case for JPEG): noise over gradients. */
    private static BufferedImage scene(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.util.Random random = new java.util.Random(3);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255 / width + random.nextInt(60)) & 0xFF;
                int g = (y * 255 / height + random.nextInt(60)) & 0xFF;
                int b = random.nextInt(256);
                image.setRGB(x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    @Test
    public void aScreenshotBecomesASmallJpeg() throws IOException {
        BufferedImage small = PhotoCodec.fit(scene(1920, 1080), PhotoRules.WIDTH, PhotoRules.HEIGHT);
        assertEquals(PhotoRules.WIDTH, small.getWidth());
        assertEquals(PhotoRules.HEIGHT, small.getHeight());
        byte[] jpeg = PhotoCodec.encode(small);
        assertTrue(jpeg.length + " bytes", jpeg.length <= PhotoRules.MAX_BYTES);
        assertTrue(PhotoRules.looksLikeJpeg(jpeg));
        BufferedImage back = PhotoCodec.read(jpeg);
        assertEquals(PhotoRules.WIDTH, back.getWidth());
        byte[] kept = PhotoCodec.normalize(jpeg);
        assertTrue(PhotoRules.looksLikeJpeg(kept) && kept.length <= PhotoRules.MAX_BYTES);
    }

    @Test
    public void anOddShapeIsCroppedToFill() {
        BufferedImage tall = PhotoCodec.fit(scene(300, 900), PhotoRules.WIDTH, PhotoRules.HEIGHT);
        assertEquals(PhotoRules.WIDTH, tall.getWidth());
        assertEquals(PhotoRules.HEIGHT, tall.getHeight());
    }

    @Test
    public void whatIsNotASmallJpegIsRefused() throws IOException {
        for (byte[] bad : new byte[][]{new byte[0], {(byte) 0x89, 'P', 'N', 'G', 13, 10}, new byte[]{(byte) 0xFF,
                (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0}}) {
            try {
                PhotoCodec.read(bad);
                fail("read " + Arrays.toString(bad));
            } catch (IOException expected) {
                // refused
            }
        }
        BufferedImage smooth = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 768; y++) {
            for (int x = 0; x < 1024; x++) {
                smooth.setRGB(x, y, (x / 4) << 16 | (y / 3) << 8 | 90);
            }
        }
        byte[] huge = PhotoCodec.encode(smooth);
        try {
            PhotoCodec.read(huge);
            fail("a photo larger than allowed was read");
        } catch (IOException expected) {
            // refused by its size (header) or its bytes
        }
    }
}
