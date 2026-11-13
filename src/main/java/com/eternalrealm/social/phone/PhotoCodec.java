package com.eternalrealm.social.phone;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Photos as JPEG (the JDK's ImageIO, no Minecraft): the client makes a screenshot small and encodes it; the server
 * reads what arrives with checked limits (the size is read from the header before any pixel is decoded) and encodes it
 * again, so what it keeps and hands out is a plain JPEG of its own making (no metadata, nothing smuggled along).
 */
public final class PhotoCodec {
    private static final float[] QUALITIES = {0.85F, 0.72F, 0.6F, 0.45F};

    private PhotoCodec() {
    }

    /** Scales {@code source} to fill {@code width} x {@code height} (cropping the middle) with smooth filtering. */
    public static BufferedImage fit(BufferedImage source, int width, int height) {
        double scale = Math.max(width / (double) source.getWidth(), height / (double) source.getHeight());
        int scaledW = (int) Math.ceil(source.getWidth() * scale);
        int scaledH = (int) Math.ceil(source.getHeight() * scale);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            BufferedImage step = source;
            // halve first while far too large: a single bilinear step from 1920 to 384 would skip pixels and shimmer
            while (step.getWidth() / 2 >= scaledW && step.getHeight() / 2 >= scaledH) {
                BufferedImage half = new BufferedImage(step.getWidth() / 2, step.getHeight() / 2,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D h = half.createGraphics();
                try {
                    h.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    h.drawImage(step, 0, 0, half.getWidth(), half.getHeight(), null);
                } finally {
                    h.dispose();
                }
                step = half;
            }
            g.drawImage(step, (width - scaledW) / 2, (height - scaledH) / 2, scaledW, scaledH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** JPEG of at most {@link PhotoRules#MAX_BYTES} (the quality drops until it fits). */
    public static byte[] encode(BufferedImage image) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        for (float quality : QUALITIES) {
            byte[] bytes = jpeg(rgb, quality);
            if (bytes.length <= PhotoRules.MAX_BYTES) {
                return bytes;
            }
        }
        throw new IOException("photo too large even at low quality");
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("no JPEG writer");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(32 * 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /**
     * Reads a JPEG that arrived from a client: no larger than {@link PhotoRules#MAX_BYTES}, no larger in pixels than
     * {@link PhotoRules#sizeOk} allows (checked from the header first).
     */
    public static BufferedImage read(byte[] data) throws IOException {
        if (!PhotoRules.looksLikeJpeg(data) || data.length > PhotoRules.MAX_BYTES) {
            throw new IOException("not a small JPEG");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) {
                throw new IOException("unreadable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("no reader");
            }
            ImageReader reader = readers.next();
            try {
                if (!"jpeg".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("not a JPEG");
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!PhotoRules.sizeOk(width, height)) {
                    throw new IOException("photo of " + width + "x" + height);
                }
                BufferedImage image = reader.read(0);
                if (image == null || !PhotoRules.sizeOk(image.getWidth(), image.getHeight())) {
                    throw new IOException("unreadable");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException e) {
            throw new IOException("unreadable: " + e.getMessage(), e);
        }
    }

    /** What the server keeps for what a client sent: read with limits, encoded again. */
    public static byte[] normalize(byte[] data) throws IOException {
        return encode(read(data));
    }
}
