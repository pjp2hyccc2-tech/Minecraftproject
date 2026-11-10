package com.eternalrealm.world.map;

import com.eternalrealm.world.field.FieldLayout;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FieldMaskPainterTest {
    @Test
    public void theFieldIsClearItsNeighboursVeiledAndTheBorderLit() {
        int size = 96;
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            int[] box = FieldMaskPainter.box(field);
            int[] pixels = FieldMaskPainter.paint(field, size, 0x123456);
            int clear = 0;
            int veil = 0;
            int rim = 0;
            for (int p : pixels) {
                if (p == 0) {
                    clear++;
                } else if (p == FieldMaskPainter.VEIL) {
                    veil++;
                } else if ((p & 0xFFFFFF) == 0x123456) {
                    rim++;
                }
            }
            assertEquals("field " + field + ": only clear, veil and rim pixels", pixels.length, clear + veil + rim);
            assertTrue("field " + field + " has a clear inside", clear > size * size / 40);
            assertTrue("field " + field + " has neighbours", veil > size * size / 10);
            assertTrue("field " + field + " has a rim", rim > size);
            double[] label = FieldLayout.labelPoint(field);
            int lx = (int) ((label[0] - box[0]) / (box[2] - box[0]) * size);
            int ly = (int) ((label[1] - box[1]) / (box[3] - box[1]) * size);
            assertTrue("label of " + field + " is not veiled", pixels[ly * size + lx] != FieldMaskPainter.VEIL);
        }
    }
}
